# 1. Kafka的理解

- 是什么：消息队列中间件，具备流式数据处理和分析能力，常用于消息队列系统使用

- 有什么

    - 注册中心：zookeeper负责kafka集群元数据的管理，以及集群的协调工作，每个kafka服务器启动后都会将自身注册到zk中

    - kafka核心层

        - producer：消息主体的生产者
        
        - topic：主题，与业务逻辑相关

        - record：消息主体

        - broker：kafka服务器节点

        - partition：主题分区，一个主题可以有多个分区

        - partition leader：又称分区leader replica，kafka默认是主写主读的

            - 数据同步：**只有落盘的数据才能被同步到follower**

        - partition follower：又称分区follower replica，负责热备leader的数据，不提供读/写功能
        
            当producer配置ack策略为1（确保至少一个follower写入pagecache），或-1（确保全部follower写入pagecache）时，leader才会响应producer

        - offset：偏移量，指的是某分区每一条消息都会根据时间先后顺序有一个递增的序号，这个序号就是偏移量

        - consumer：消息主体的消费者

        - consumer group：消费者组，用于实现不同的消息队列模型，一个消费者组中的消费者只会去消费一个分区的消息

        - consumer group leader：消费者组群主，执行消费者分区分配策略，通过cooridinator将结果分发给其他consumer

        - controller：在zk上抢注/controller节点成功的broker，用于负责分区leader选举、主题管理等工作

        - cooridinator：协调者，用于处理消费者组的重平衡，协调同一组内的消费分区分配

    - 存储层：log-file形式写入磁盘中

- 特征：

    - 快：消息顺序I/O追加写入、页缓存、零拷贝、消息精简、消息批量发送

    - 分区有序性：

        - 机制：Sequenece Number（消息序列号） + PID（producer id），broker维护pid、主题分区、序列号三元组，以应对不稳定的网络情况可能导致的乱序情况

            当发现producer发送的pid + sequence number与当前维护的三元组相差值大于1时，说明有乱序情况出现，会拒绝该消息写入

        - 应用：record指定key后（如订单系统的订单id），根据hash取模，相同key的消息总是能落在同一个分区上

    > 为什么不是多分区有序性？

    单分区有序性可以保证不同partition之间互不干扰

    如果要保证全分区有序，则需要在多个broker之间引入消息写入、消费的同步机制以保持顺序，当出现某消息消费过久（堵住），会造成其他分区无法写入/读取，整个kafka集群退化为单一队列

    - 主读主写：可以分担写压力，得益于kafka对主题分区的概念引入，通过多个维度对消息进行切分，将读写压力更均衡得分配到各个broker上，实现更合理的reload

# 2. 消息队列模型

- 点到点模型：指消息只能被一个消费者消费，消费后消息删除

- 发布订阅模型：相当于广播模式，平行的消费者组上的消费者都可以消费掉消息

机制：通过consumer group同时支持这两个模式，配置**所有的consumer都属于同一个consumer group**则属于点对点模型，如果**每个consumer group都只有一个consumer**则属于发布/订阅模型

# 3. Kafka的通信过程

1. broker

- /controller节点的抢注，第一个抢占到节点的broker为kafka controller，用于协调各个分区leader replica和follower replica的崩溃恢复与热备

- /broker/ids信息的注册，每个broker在启动后都会将自己的元信息写入，其他broker节点可根据zk的watcher机制感知到新broker的加入与退出

2. producer

- producer与配置的broker建立tcp连接，获取到所有broker的地址信息后，与所有broker建立tcp连接

- 发送消息过程，如果指定key则按照hash取模将消息发送到固定分区中，如果没有指定key则按照轮询、随机等两种机制进行发送

3. consumer

- consumer与配置的broker进行tcp连接，得到协调者信息后向其发送`JOINGROUP`指令，第一个发送的consumer将成为群主

    后续消费者组中的每个消费者在加入组时，都会发送JOINGROUP获取信息

- 成为群主的consumer执行分区分配策略，并通过`SYNCGROUP`命令将分配结果发送给协调者

    后续其他consumer也会根据SYNCGROUP获取到分区分配方案

- 根据主题分区进行消息消费

# 4. 为什么需要分区

kafka的分区可以更加均衡的分摊读/写压力到不同的broker上，降低单点压力，即可并提供横向扩展能力（在增加broker之后进行consumer的重平衡即可）

# 5. producer发送信息如何选择分区

producer在没有指定key的情况下，消息根据以下两种方式：

1. 轮询：维护全局的原子递增序列号，将消息均匀轮询发送到broker中

2. 随机：随机发送到broker中

在有指定key的情况下，则根据key的hash取模情况，发送消息到某个分区上，这也意味着相同的key信息都会落到同一个分区上，则相同key消息具备有序性（分区有序性）

# 6. consumer的分区分配策略

由conusmer group群主执行，具体有三种策略：

1. range：根据每个主题的分区进行均匀分配，当主题间的分区数量不一致时会出现分配不均匀情况

2. roundRobin：根据主题的所有嗯去进行均匀分配，当consumer订阅的主题不一致时会出现分配不均匀的情况

3. sticky：黏滞策略，根据主题的所有分区，并结合全局consumer对主题的订阅情况进行均匀分配，避免roundrobin造成的不均匀情况，并保证上一次分配对应的consumer，在新的分配下仍旧保持对应关系

# 7. consumer的重平衡

指有`新的consumer`加入到消费者组，或有`新的主题`，或`分区数量`发生变动，则需要重新对消费者与分区关系进行重新分配的机制3t
2. 向协调者发送`JOINGROUP`请求加入到消费者组中，第一个发送该请求的consumer成为群主，协调者会把组成员发送给群主

3. 群主执行分区分配策略，并将结果发送通过`SYNCGROUP`发送给协调者，后续的消费者发送`SYNCGROUP`到协调者后，协调者会响应分区结果

# 8. 如何保证消息可靠性（可靠消息最终一致性）

消息从产生到消费，经历三个部分，需要保证producer、broker、consumer上的可靠性才能保证消息的可靠性

> 消息可靠：消息不能丢失，所以重试机制必然产生消息重复，保证幂等性变得至关重要

1. producer：消息在buffer有`易失性`（批量发送机制）、存在**超时异常**（网络时延或丢失）的情况

- 网络因素：当broker没有对producer的消息发送进行响应（发送过程丢失、响应过程丢失），或发送消息出现**超时异常**

    超时异常：无法确定消息是否真正发送到broker，因为存在producer投递消息失败或broker响应丢失，前者未写入，后者写入
    
    解决方案：

    1. **重试消息发送**

        参数：`retries = N`，设置一个较大值，在发送消息失败后不断重试

        消息重复：每个消息分配一个全局唯一的Producer Id + Sequence Number序号，broker可以根据消息的PID进行判断，防止消息重复写入（**幂等性**）和**有序性**

    2. broker的ISR列表响应个数

        参数：`acks = 0、1、-1/all`，表示无需、需要一个（leader）、需要全部ISR副本的broker写入并返回ack给producer，才算真正完成

        > 该机制需要搭配broker的`min.insync.replicas`，防止当ISR列表只有一个副本时，acks = -1达不到预期效果

- 易失性：存在buffer中的消息，在producer宕机后将消失

    解决方案：
    
    1. 自实现本地消息表，保留可靠消息的凭证（耦合性较强）

    2. 考虑使用RocketMQ，其通过**Half消息**和**回查事务**实现分布式事务，将本地消息表集成到MQ服务中

2. broker：写入到pageCache的消息具有`易失性`，当出现follower replica未同步成功时，leader replica所在broker宕机，则消息丢失

    解决方案：

    - 调整`fsync`频率，由于pagecache是异步写入到磁盘，只有在磁盘的数据才被持久化（不推荐，性能较低）

    - `min.insync.replica`搭配producer的`acks`参数，确保有一个以上的follower同步后才算真正写入

        `min.insync.replica`确保broker的ISR最低数量，否则当ISR = 1（只有leader本身），则producer的acks参数将失效

3. consumer：使用手动提交(`enable.auto.commit = false`)和重读取偏移量策略（`auto.offset.reset=earliest`），并保证幂等性

    自动提交可能导致消费失败但仍旧消息位移被异步提交，导致消息丢失

    而重读取策略也是为了保证在位移量异常（zk异常）的情况下不丢失任何消息，但会导致消息重复消费

# 9. broker的副本机制和同步原理

leader主读主写，follower只做热备冗余容灾

Kafka中将一个分区的所有副本集合称为`AR`，完成数据同步的所有副本集合称为`ISR`，ISR是一个动态集合，由replica.lag.time.max.ms参数配置最大同步延迟时间

通过`HW`和`LEO`来实现主从之间的数据同步，前者表示当前集群的已同步信息，后者表示leader已接收到的信息个数

# 10. kafka时间轮

底层是一个**环形数组**，用于实现延迟功能的算法

# 11. kafka抛弃zk原因

1. **重度依赖**：kafka作为一个分布式系统，重度依赖另一个分布式协调系统，增加运维成本

2. **性能问题**：zk不适合高频写入操作，若出现kafka高并发提交位移操作则会导致效率低下

    > 后来版本的kafka，将消息提交和位移保存也通过消息的方式进行处理，主题为`__consumer_offsets`

3. 严重依赖zk进行元数据管理和broker间的协调工作，如果集群规模过大，会导致zk元数据过多，进而导致zk watcher的延时

# 12. kafka快的原因

1. 通过文件追加的方式顺序I/O写入

2. 消息写入到pageCache中，再由os的`fsync`策略进行异步落盘

3. sendfile + SG-DMA，实现消息零拷贝发送

4. mmap() + write（），减少一次从kernel socket buffer拷贝到用户空间的开销，后续写入拷贝到kernel page cache（cpu），再由`fsync`策略落盘（DMA）

5. producer批量发送

# 参考

- [消息队列解决方案（本地消息表）](https://blog.csdn.net/as4566/article/details/109107766)
- [可靠消息最终一致性](https://www.cnblogs.com/zhengzhaoxiang/p/13976517.html)