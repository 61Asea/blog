# Interview twelve：Kafka

- [究竟什么时候该使用MQ？](https://blog.csdn.net/Aria_Miazzy/article/details/103788449/)

# **1. mq的特性**

1. **解耦（依赖倒置）**，隐藏上下游之间的依赖关系，统一以消息规范进行交互可以方便地加入新的生产端/消费端

2. **削峰**，对短时间的高并发请求进行缓冲，防止大量读写压力打在db上造成系统的崩溃（降低系统对瞬时操作量的敏感程度）

3. **异步调用**，解决过长的调用链路，将没必要卡在主链路的业务拆分解耦

# **2. mq缺点**

1. `提升系统复杂度`，每多一个中间组件，就要多考虑一个影响系统稳定性的因素，还得制定相对应的兜底措施

2. 消息丢失风险（如何保证消息必达、消息幂等性）

    在kafka这种at least once的可靠性保证下，势必涉及到**消息重试，需保证各个端幂等**

# **3. 什么时候使用mq**

`不适用场景`：**上游实时关注下游执行结果**，**这种情况下应直接使用rpc**

   > 如果强行使用MQ通讯，调用方不能直接感知到下游执行情况，则要等待另一个MQ通知回调。如果这么玩，不但使得编码复杂，还会**引入消息丢失的风险，中间多加入一层**，多此一举

适用场景： **上游不关注下游执行结果**

1. **数据驱动**的任务依赖

    方式：具有一定依赖关系的任务，且各个任务执行时间长。通过上游完成任务后**发送完成通知到MQ**，下游订阅mq的方式，替代cron工作流组成消息通知工作流。

    - 优势：各个工作执行单元间的**间隙**无需人工预留，执行单元的启动可以做到更加实时准确

2. **上游不关心多个下游执行结果**

    方式：与主业务相关性较小的业务拆分解耦，拆分出的业务通过订阅mq方式进行异步执行
    
    > 如上游帖子系统和下游的积分系统、优惠券系统、会员系统

    - 优势：下游系统的失败不会影响到上游主业务的正确性；新增下游系统无需修改上游系统代码

3. 上游关心执行结果，但异步返回执行时间很长

    方式：上游调用下游api后立即返回（异步接口），并订阅mq获取下游的结果通知

# **4. Kafka介绍理解**

Kafka本身是一个流式数据处理平台，更多偏向于拿它当做消息队列系统使用，从整个kafka体系理解上分为3个层次：

<!-- 1. 注册中心Zookeeper：负责kafka集群元数据的管理，集群协调工作

    - 每个broker在上线后都会尝试创建/controller节点，通过master选举确定kafka集群的controller，每个broker都会注册该节点的Watcher

    - broker还会将自身的信息注册到/brokers节点中 -->


1. Zookeeper作为注册中心：负责集群元数据管理和协调工作

    - /controller节点：每个broker启动后，都尝试在zk建立/controller节点进行master选举，并注册/controller节点的Watcher，监测节点的更新变动

    - /brokers节点：broker还会将自身信息注册到zk的/brokers节点中

2. Kafka核心层：分为producer、consumer、broker，在三者之间为record提供**可靠传输**能力

    - record：消息
    - producer：生产record并发送
    - broker：kafka的服务器节点，一般是broker集群模式。负责消息的接收和拉取，并提供消息持久化、消息清理机制
    - topic：主题，用于区分各个业务
    - `partition`：主题分区，一个主题可以分成多个分区，每个分区具备**分区有序性**（不同的分区无法保证消息有序），**降低单节点的读写压力**，**提高整个kafka系统的伸缩能力**
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

- **点到点模型**：每个消息只会被**单独broker**接收处理

- 发布订阅模型：每个消息会被**全部broker**接收处理

kafka通过consumer group实现两种模型：

- 当所有consumer都属于同一个group时，主题分区只会被group中的某个consumer消费，即每个consumer**只拉取到未被处理过的消息**

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

## producer

底层通过Sender进行发送，分别有三种模式：发送后忘记、同步发送、异步发送，一般采用**异步发送，并指定失败后的回调函数，并设置失败时不断重试**

sender提供消息重试机制、分区有序性（幂等特性、串行发送）、ack=all配置

- 重试机制

    - retries：用来配置sender的重试次数，当发生可重试异常时（网络抖动、leader副本选举），内部将会尝试自动恢复
    - retry.backoff.ms：重试间隔，还需要配置总的重试时间

- 幂等性： Kafka引入`Producer ID（PID）` + `Sequence Number`

    - 每个producer对应一个PID，每个发送的\<Topic, Partition\>都对应一个单调递增的Sequence Number
    - 每个broker也维护一个\<PID, Topic, Partition>\，用来与producer发送的Seq进行相减比对
    
    > 当producer发送的最新seq比broker记录的seq大1时，为正常状态

- 串行发送：通过配置`max.in.flight.requests.per.connection = 1`，保证producer每次发送的数据的请求数为1，**且在当前消息未成功时进行重试，直到成功再发送下一个消息请求**

- acks=all，保证全部副本都确认了消息后，才对客户端返回，且消费者才可以进行消费

## broker

通过pagecache的策略异步写入磁盘，需要等待fsync()调度进行持久化，有消息丢失的可能性

需要**依托多副本机制的同步**，根据配置保证有多个副本写入成功：

- replication.factor=N：N个副本需要写入，应该设置较大的值

- min.insync.replicas=N：表示ISR列表中，要存在N个写入成功的副本

    > [关于kafka的配置acks和min.insync.replicas详解](https://blog.csdn.net/DraGon_HooRay/article/details/123788566)producer的acks和该参数进行配合，当min.insync.replicas配置1（默认），即使producers配置acks=all，只要消息写入leader副本后就算成功了，效果变成了acks=1

## consumer

- 关闭自动位移提交，改为手动提交，并按照**先处理业务再提交位移**的顺序，保证消息在处理完毕后才提交位移

> 多次拉取消息，涉及消息重试，需要保证业务系统的幂等性

- **当消费者找不到记录的消费位移**时，会根据消费者`auto.offset.reset`的配置决定从何处进行消费。所以reset应设置为earliest（从头开始读），防止丢失消息

# **12. 同步机制**

leader副本主写主读，follower副本只负责同步leader副本的内容；leader副本在写入时，需要等待消息经过其他follower副本的确认，才算真正成功

> 得益于kafka的架构设计，可以在多个维度上编排，每个节点都可获得更为合理读写负载。反观主写从读，常常无法分担主节点的写压力

对于每个副本而言，都有两个概念：
- HW：同步的高水位，从低位到高水位都表明是主从已同步的消息，可以消费
- LEO：表示该节点的消息追加偏移量，HW~LEO之间的消息消费者不可见

# **13. 为什么抛弃Zookeeper**

- 提交位移是高频读写操作，zk并不适合

    > zk集群，只有leader写，且zab协议需要过半follower返回ack，效率低下

- 严重依赖zk实现元数据管理和集群协调工作，如果集群规模过大，主题和分区数量很多，会导致zk集群的元数据过多，进而导致watcher的延时

# **14. kafka为什么快？**

1. 顺序I/O：

    写消息到分区时，采用追加文件的方式，这种效率比随机I/O写入快。顺序I/O写入磁盘的速度，甚至能比随机I/O写入内存还要快

2. 生产写入Page Cache、mmap()

    - 通过fsync()的策略，批次写入到磁盘中（fsync()的策略不应为实时调用）

    - 不是立即写入到磁盘，而是写到page cache中，page cache处于内存和磁盘介质之间，可以加速读写速度

3. 消费零拷贝

    - 消费信息：零拷贝sendfile() + SG-DMA

    - 写入信息：**网卡接收数据进行持久化，mmap() + write**

4. epoll

5. 批量处理和压缩

    - 发送：不是一条条消息发送的，而是将消息合并成一个大Partition进行发送

    - 消费：同理，一次拉取一批消息进行消费

    - 压缩：采用压缩算法来减少消息的大小，进而使得消息在网络上节省带宽

# **15. kafka实现延迟队列**


# 参考
- [深度解读：Kafka 放弃 ZooKeeper，消息系统兴起二次革命](http://k.sina.com.cn/article_1746173800_68147f6802701014i.html)
- [1分钟实现“延迟消息”功能](https://mp.weixin.qq.com/s?__biz=MjM5ODYxMDA5OQ==&mid=2651959961&idx=1&sn=afec02c8dc6db9445ce40821b5336736&chksm=bd2d07458a5a8e5314560620c240b1c4cf3bbf801fc0ab524bd5e8aa8b8ef036cf755d7eb0f6&scene=21#wechat_redirect)


# 重点参考
- [kafka11问](https://www.jianshu.com/p/e1af3a703550)
- [为什么使用mq？什么时候使用mq？](https://mp.weixin.qq.com/s/Brd-j3IcljcY7BV01r712Q?)
- [Kafka的特性，以及为什么有这些特性](https://www.jianshu.com/p/449f009bce1d)

- [kafka page cache解读](https://www.sohu.com/a/379932771_198222)

- [mmap() + write / sendfile + ScatterGather-DMA](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247491660&idx=1&sn=a7d79ec4cc3f40e7b9a9018436a7377a&chksm=c2b1a8b1f5c621a7268ca298598a15c4ac575790628651e5651925b5efd96ebc0046796ef5b1&token=931654098&lang=zh_CN#rd)

# 优先级最高的参考资料
- [究竟什么时候该使用MQ？](https://blog.csdn.net/Aria_Miazzy/article/details/103788449/)
- [幂等的8种实现方式](https://blog.csdn.net/sufu1065/article/details/122335349)：select + insert、insert、状态机幂等、token（预先请求下游生成）、悲观锁（for update）、乐观锁（version）、分布式锁结合select