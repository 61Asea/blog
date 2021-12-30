# Interview：Kafka

# **1. 为什么用mq**

1. 实现分布式系统下的调用解耦，**隐藏上下游之间的依赖关系**，统一以消息规范进行交互可以方便地加入新的生产端/消费端

    遵循依赖倒置原则

2. 可以解决过长的调用链路，将没必要卡在主链路的业务拆分解耦，通过**异步调用**的方式进行执行

3. **削峰**，对短时间的高并发请求进行缓冲，防止大量读写压力打在db上造成系统崩溃（降低系统对操作量的敏感程度）

# **2. mq缺点**

1. 如何确保消息可靠传输（可靠性与重复性互为矛盾）

    在kafka这种at least once的可靠性保证下，势必涉及到消息重试，需保证各个端幂等

2. 传递路径变长，延时增加

3. **上游无法知道下游的执行结果**，需要补充结果获取的机制

    - 下游提供结果查询接口

    - 下游主动通知上游

# **3. 什么时候使用mq**

`不适用场景`：调用方实时依赖执行结果的业务场景（**上游实时关注执行结果**），应使用直接调用

适用场景：

- 数据驱动的任务依赖

    具有一定依赖关系的任务，且各个任务执行时间长，通过mq将它们组成消息通知工作流

    > 例子：使用上游完成任务后**发送完成通知到MQ**，下游订阅mq的方式，替代cron工作流

- 上游不关心多个下游执行结果

    > 例子：将与主业务相关性较小的业务拆分解耦，拆分出的业务通过订阅mq方式进行异步执行

- 上游关心执行结果，但异步返回执行时间很长

    > 例子：下游收到上游调用后立即返回，上游订阅mq获取结果通知

# **4. Kafka介绍理解**

Kafka本身是一个流式数据处理平台，更多偏向于拿它当做消息队列系统使用，从整个kafka体系理解上分为3个层次：

<!-- 1. 注册中心Zookeeper：负责kafka集群元数据的管理，集群协调工作

    - 每个broker在上线后都会尝试创建/controller节点，通过master选举确定kafka集群的controller，每个broker都会注册该节点的Watcher

    - broker还会将自身的信息注册到/brokers节点中 -->


1. Zookeeper作为注册中心：负责集群元数据管理和协调工作

    - 每个broker在启动后都会尝试在Zookeeper建立/controller节点以此进行master选举，并注册该节点的Watcher以监测controller节点的变化

    - broker还会将自身信息注册到/brokers节点中

2. Kafka本身核心层：分为producer、consumer、broker，在三者之间为record提供可靠传输能力

    - record：消息
    - producer：生产消息并发送
    - broker：kafka服务器节点，负责消息的接收和拉取，并提供消息持久化、消息清理机制
    - topic：主题，用于区分各业务
    - partition：主题分区，一个主题可以分成多个分区，每个分区具备分区有序性（不同的分区无法保证消息有序），**降低单节点的读写压力**，**提高整个kafka系统的伸缩能力**
    - replica：分区的副本，大致可分为leader副本和follower副本，保证集群的高可用，leader副本主写主读，follower副本只负责同步leader副本，每一个写入都需要全部副本确认
    - controller：kafka集群控制器，率先抢占到zk的/controller节点的broker当选，负责leader副本选举和集群协调工作（新增broker的分区分配、ISR集合元数据更新通知其它broker）
    - consumer：订阅主题，并拉取消息进行消费
    - consumer group：消费者组，由多个消费者组成，主题下的一个分区只会由同一组内的一个消费者进行消费。可通过消费者组概念形成点到点、发布订阅两种消息模式

        组内的各个位移提交写入到以group_id取模获得的_consumers_offsets分区

    - coordinator：协调者，为消费者组分配分区以及再均衡操作，其处在**consumers_offsets主题下，取模分区的leader副本所在的broker**
    - offset：偏移量，分区中的每个消息根据写入顺序都会有一个递增偏移量

3. broker存储层：保存消息，以日志形式追加写入到磁盘中

    - Log：对应一个主题分区副本的日志

    - Log Segment：日志分段，文件名为基准偏移量，便于进行清理和索引设计

# **5. 消息模型**

主要分为两种：
- 点到点模型
- 发布订阅模型

kafka通过consumer group实现两种模型：

- 当所有consumer都属于同一个group时，主题分区只会被group中的某个consumer消费，即每个consumer只拉取到未被处理过的消息

- 当所有consumer都是单独的一个group时，就变成了发布订阅模式，所有consumer都能拉取到相同的消息

# **6. kafka通信过程**

1. 启动时，先向Zookeeper尝试创建`/controller`临时节点进行master选举（若成功则称为controller，失败则获取controller信息后保存到本地），并将自身信息注册到`/brokers/ids`节点下

2. producer启动时获取`bootstrap.servers`配置指定的broker地址，并与配置的broker进行连接获取元数据、主题数据、主题分区数据、分区副本及其leader副本数据，接着与所有broker创建TCP连接

3. producer发送消息异步，以批次进行组提交，提交的分区由分区选择器指定，如果key为null则通过原子变量进行轮询，否则通过哈希取模得到分区

4. 分区接收到消息后写入到page cache中，再由系统调度fsync()的策略，顺序I/O写入到最后一个activeLogSegment中

5. consumer启动时获取`bootstrap.servers`配置指定的broker地址，选择一台broker创建TCP连接，发送请求获得协调者所在broker，获取消费者组均衡的结果，并对各个分区leader副本所在的broker节点建立连接

6. 当拉取到新消息时，执行业务，再提交位移

# **7. 发送消息如何分区**

1. 轮询，按照顺序消息一次发送到不同的分区，顺序由一个原子变量进行维护

2. 随机，随机发送到某个分区

3. 如果key不为null，则key进行hash，再通过对分区数量取模，所以对于相同key的消息而言，总会落到同一个分区上，消息具备有序性

# **8. 分区好处**

没有分区的话只能都写到同一个节点上，而分区则可以对消息进行分治，提供了生产、消费的负载均衡，解决单节点的读写压力，为系统提供良好的水平扩展能力

且kafka分区引入了副本，这样整个系统从多个维度进行冗余，保证高可用

# **9. 消费者组重平衡过程**

消费者的数量最好与所有主题分区的数量保持一致，多了会导致有消费者没有分区可消费，少了会导致某消费者消费多个分区，消费者与分区的关系**由Rebalance进行分配**

新版本通过coordinator协调者完成重平衡过程，重平衡进行过程中**整个消费者群组停止工作，无法消费消息**

1. FIND_COORDINATOR：使用consumer_group_id取模获得_consumers_offsets主题的分区，查找分区的leader副本所在broker，该broker即coordinator

2. JOIN_GROUP：每个consumer在加入组后，都会发送JoinGroupRequest（包含支持的分配策略）到coordinator中

    coordinator将第一次接收到request的broker设定为组leader，并从候选集选取分配策略，将结果以JoinGroupResponse响应给各个consumer

3. SYNC_GROUP：leader consumer将采用coordinator选定的策略进行分区分配，并将结果以SyncGroupRequest发送到coordinator中，由coordinator转发到其他consumer

4. HEART_BEAT：心跳机制

# **10. 再均衡分配策略**

1. RangeAssignor范围策略：以分区为单位，按照某分区的副本数量 / 消费者数的结果进行分配，会出现分配不均的情况

2. RoundRobinAssignor轮询策略：consumer订阅相同主题，主题具备相同分区副本数量的情况下可以保证分配均匀，最多1个分区的误差；否则，出现订阅主题不同等不一致情况下，依旧存在分配不均问题

3. StickyAssignor粘滞策略：解决后续再均衡情况可能导致consumer绑定分区变化，尽量使得分区依旧遵循以上一次分配consumer优先，避免出现重复消费等问题；并且在出现订阅主题不同等不一致情况下，可以做到比轮询策略更均匀的分配

# **11. 如何保证消息可靠**

生产者：重试机制，需要开启幂等机制

broker：
- ack=all保证全部副本都确认了消息后，才对客户端返回，且消费者才可以进行消费
- 写入页缓存中，等待fsync()调度进行持久化

消费者：
- 关闭自动位移提交，改为手动提交，并按照先处理业务再提交位移的顺序，保证消息不丢失
- **当消费者找不到记录的消费位移**时，会根据消费者`auto.offset.reset`的配置决定从何处进行消费，应设置为earliest从头开始读，防止丢失消息

# **12. 同步机制**

leader副本主写主读，得益于kafka的架构设计，可以在多个维度上编排，每个节点都可获得更为合理读写负载（主写从读无法分担主节点些压力）

leader副本的写入，需要等待消息经过其他follower副本的确认，才算真正成功

对于每个副本而言，都有两个概念：
- HW：同步的高水位，从低位到高水位都表明是主从已同步的消息，可以消费
- LEO：表示该节点的消息追加偏移量，HW~LEO之间的消息消费者不可见

# **13. 为什么抛弃Zookeeper**

- 提交位移是高频读写操作，zk并不适合

- 严重依赖zk实现元数据管理和集群协调工作，如果集群规模过大，主题和分区数量很多，会导致zk集群的元数据过多，进而导致watcher的延时

# **14. kafka为什么快？**

1. 顺序I/O写入比随机I/O写入快，顺序I/O写入磁盘比随机I/O写入内存还要快

2. Page Cache

- 批次写入到磁盘中，而不是实时调用fsync()

- page cache处于内存和磁盘介质之间，加速读写速度

3. 

- 零拷贝发送数据：sendfile() + SG-DMA

- **网卡接收数据进行持久化：mmap + write**



# 参考
- [深度解读：Kafka 放弃 ZooKeeper，消息系统兴起二次革命](http://k.sina.com.cn/article_1746173800_68147f6802701014i.html)
- [1分钟实现“延迟消息”功能](https://mp.weixin.qq.com/s?__biz=MjM5ODYxMDA5OQ==&mid=2651959961&idx=1&sn=afec02c8dc6db9445ce40821b5336736&chksm=bd2d07458a5a8e5314560620c240b1c4cf3bbf801fc0ab524bd5e8aa8b8ef036cf755d7eb0f6&scene=21#wechat_redirect)


# 重点参考
- [kafka11问](https://www.jianshu.com/p/e1af3a703550)
- [为什么使用mq？什么时候使用mq？](https://mp.weixin.qq.com/s/Brd-j3IcljcY7BV01r712Q?)
- [Kafka的特性，以及为什么有这些特性](https://www.jianshu.com/p/449f009bce1d)

- [kafka page cache解读](https://www.sohu.com/a/379932771_198222)

- [mmap() + write / sendfile + ScatterGather-DMA](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247491660&idx=1&sn=a7d79ec4cc3f40e7b9a9018436a7377a&chksm=c2b1a8b1f5c621a7268ca298598a15c4ac575790628651e5651925b5efd96ebc0046796ef5b1&token=931654098&lang=zh_CN#rd)