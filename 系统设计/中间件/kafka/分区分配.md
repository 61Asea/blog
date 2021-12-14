# Kafka：分区分配

分区和副本的关系：

- topic：主题，对应业务，是一个**逻辑概念**

- partition：主题的分区，主题应有多个分区，各个分区之间互不干扰，消息不重复

    - replica：主题副本机制，副本会**均匀**的分配在不同broker上，`读写服务只有leader提供，follower只做热备`

        - leader：有且只有leader提供读写服务

            > 好处：写入数据后，消费者就可以马上读取，无需等待XA等分布式协调机制；单调读，避免主从同步的数据延迟，保持强一致性

        - follower：不提供任何读写服务，只专注于热备，与leader保持数据同步

    - AR（Assigned Replicas）：已声明所有分区副本的列表，由配置的副本因子决定，`AR = ISR + OSR`

        - ISR（In-Sync Replicas）：可用列表，与leader数据**同步**的副本，包括leader本身

            > 处于ISR列表的副本，数据与leader相差不会过大，当leader副本对应broker宕机时，优先master选举（**这个过程由kafka controller broker进行协调**）

        - OSR（Outof-Sync Replicas）：不可用列表，与leader数据**不同步**的副本

        初始工作状态下，所有的副本都在ISR中；运行过程中，可能出现网络、磁盘I/O问题，导致部分副本与leader的同步速度慢于`replica.lag.time.max.ms`，降级至OSR

消息幂等、不重复：引入`Producer ID（即PID）`和`Sequence Number`

- PID：每个新的Producer在初始化的时候会被分配一个唯一的PID

- Sequence Number：对于Producer，通过其不重复PID发送数据，在每个\<Topic, Partition\>组中都对应一个从0开始单调递增的Sequence Number

如果消息序号比Broker维护的序号差值**比一大**，说明中间有数据尚未写入，即乱序，此时Broker拒绝该消息，Producer抛出InvalidSequenceNumber

如果消息序号**小于等于**Broker维护的序号，说明该消息已被保存，即为重复消息，Broker直接丢弃该消息，Producer抛出DuplicateSequenceNumber

> Sender发送失败后会重试，这样可以保证每个消息都被发送到broker

# 参考
- [Kafka 保证分区有序](https://blog.csdn.net/q322625/article/details/112911083)
- [Kafka在分布式情况下，如何保证消息的有序](https://www.zhihu.com/question/266390197/answer/772404605)：多partition有序，则broker保存的数据要保持顺序，消费时也要按序消费。那么存在当某分区堵了，为了有序，其他分区都不能被消费，这就退化成了单一队列，毫无并发性可言；单partition有序，不同partition之间不会干扰对方

- [消息分区策略](https://www.cnblogs.com/gyshht/p/14821397.html)

- [生产者幂等性](https://www.jianshu.com/p/b1599f46229b)

- [主题、分区、副本](https://www.cnblogs.com/rexcheny/articles/11627073.html)：图文并茂，不过在讲第一张图的副本leader有误，并且提出了kafka分区副本单调读的特性