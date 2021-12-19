# Kafka：分区分配

主题作为消息的归类，可以再细分为一个或多个分区，**分区也可以看作对消息的二次归类**

分区提供了**可伸缩性、水平扩展**的能力，并通过多副本机制来为Kafka提供数据冗余以提高**可用性**

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

# **消息幂等**

生产者客户端的Sender线程发送失败后会重试，以保证每个消息都被发送到broker。失败的原因包括不限于：网络延迟、网络出错、宕机介质恢复

broker幂等机制：引入`Producer ID（即PID）`和`Sequence Number`

- PID：每个新的Producer在初始化的时候会被分配一个唯一的PID

- Sequence Number：对于Producer，通过其不重复PID发送数据，在每个\<Topic, Partition\>组中都对应一个从0开始单调递增的Sequence Number

    - 如果消息序号比Broker维护的序号差值**比一大**，说明中间有数据尚未写入，即乱序，此时Broker拒绝该消息，Producer抛出InvalidSequenceNumber

    - 如果消息序号**小于等于**Broker维护的序号，说明该消息已被保存，即为重复消息，Broker直接丢弃该消息，Producer抛出DuplicateSequenceNumber

# **1. 主题管理**

包括：创建主题、查看主题信息、修改主题和删除主题四大操作：
- 通过%KAKFA_HOME%/bin/kafka-topics.sh脚本来执行
- 通过KafkaAdminClient发送CreateTopicsRequest、DeleteTopicsRequest请求来
- 直接操纵日志文件和zookeeper节点实现

## **1.1 创建主题**

自动创建（不推荐）：

```shell
# 自动创建该主题
auto.create.topics.enable=true
# 分区默认值
num.partitions=1
# 副本因子
default.replication.factor=1
```

client向未创建的主题发送/消费消息时，会自动创建主题，自动创建的主题其分区个数和每分区副本个数由配置决定

手动创建（推荐）：

```shell
# 通过脚本预先创建，主题名：“topic-create”，主题有4个分区，每个分区有两个副本
bin/kafka-topics.sh --zookeeper localhost:2081/kafka -- create --topic topic-create --partition 4 --replication-factor 2
```

## **1.2 副本分配**

生产者分区分配：为每条消息指定要发往的分区，通过分区器对key的策略

消费者分区分配：指定消费者可以消费消息的分区，有StickyAssignor、RoundRobinAsignnor、RangeAssignor三种策略

## **1.3 查看主题**

通过zk-cli查看分区副本分配方案：

```shell
[zk: localhost:2181/kafka(CONNECTED) 2] get /brokers/topics/topic-create
{"version": 1, "partitions":{"2":[1,2], "1":[0,1],"3":[2,1],"0":[2,0]}}
```

"2":\[1,2\]：key表示分区2，value数组的长度表示分配了两个副本，数组的内容表示分别在brokerId为1和2的broker节点上

通过`kafka-topics.sh`查看：

```shell
bin/kafka-topics.sh --zookeeper localhost:2181/kafka --describe --topic topic-create
```

![kafka指令](https://asea-cch.life/upload/2021/12/kafka%E6%8C%87%E4%BB%A4-640553c4547c4cda9d4c3cd9cddd9e03.png)

- Leader：该分区的leader副本所在位置
- Isr：与leader进行同步的可用副本所在broker id集合（包括leader）
- Replicas（AR）：副本所在broker的id


> kafka在内部做埋点时，会根据主题的名称命名metrics名称，并将\".\"改成下划线\"\_\"，所以主题不能包含\".\"或\"\_\"

## **1.4 修改主题**

修改分区个数、修改配置，由`kafka-topics.sh`的`alter`指令提供

当主题消息包含key时，根据key计算分区的行为将会遭受影响，建议一开始就设置好分区数量，避免以后对其进行调整

> 目前kafka只支持增加分区，而不支持减少分区，原因是功能收益点过低。考虑删除分区的消息保留问题，若采用分散插入现有分区，当消息量较大时数据复制将会占用高资源，以及复制过程中的可用性问题

# **2. 分区管理**

leader副本选举、分区重分配、复制限流（与生产限速/配额做区分）、修改副本因子

创建主题时，主题分区及其副本会尽可能均匀地分布到Kafka集群的各个broker节点上，对应的leader副本的分配也比较均匀

## **2.1 副本分配**

只要leder副本对外提供读写服务，而follower副本只负责在内部进行消息的同步

如果一个分区的leader不可用，则整个分区变得不可用，此时就需要通过选举出新的leader副本继续对外提供服务，**然而这将打破原本的负载均衡，出现某个节点存在同主题的多个leader分区，导致失衡**

<!-- 针对同一个分区而言，同一个broker节点中不可能出现它的多个副本，即kafka集群中的一个broker最多只能有一个分区的副本 -->

### **2.1.1 优先副本自动选举（不推荐）**

**优先副本（preferred replica）**：用于治理负载失衡的情况，在AR集合列表中的第一个副本为优先副本，理想情况下优先副本就是分区的leader副本。通过一定方式促使优先副本选举为leader副本，以此来促进集群的负载均衡，这行为也可称为分区平衡

`broker节点不平衡率 = 非优先副本的leader个数 / 分区总数`，当不平衡率超过leader.imbalance.per.broker.percentage参数配置的比值（默认10%），会自动执行优先副本的选举动作以求分区平衡

> 不推荐使用分区自动平衡功能，因为分区的平衡并不意味着负载的均衡，还需要考虑主题TPS，且当遇到业务高峰期的关键任务上正好出现自动选举操作，以zk的短暂不可用特性势必导致业务阻塞、频繁超时的风险

### **2.1.2 脚本执行**

在合适的时机**手动执行**分区平衡，使用`kafka-perferred-replica-election.sh`脚本进行优先副本选举，并通过json文件进行分批

合适的时机：业务低峰期

## **2.2 分区重分配**



# 参考
- [Kafka 保证分区有序](https://blog.csdn.net/q322625/article/details/112911083)
- [Kafka在分布式情况下，如何保证消息的有序](https://www.zhihu.com/question/266390197/answer/772404605)：多partition有序，则broker保存的数据要保持顺序，消费时也要按序消费。那么存在当某分区堵了，为了有序，其他分区都不能被消费，这就退化成了单一队列，毫无并发性可言；单partition有序，不同partition之间不会干扰对方

- [消息分区策略](https://www.cnblogs.com/gyshht/p/14821397.html)

- [生产者幂等性](https://www.jianshu.com/p/b1599f46229b)

- [主题、分区、副本](https://www.cnblogs.com/rexcheny/articles/11627073.html)：图文并茂，不过在讲第一张图的副本leader有误，并且提出了kafka分区副本单调读的特性