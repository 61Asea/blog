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

        - controller：在zk上抢注/controller节点成功的broker，用于负责分区leader选举、主题管理等工作

        - cooridinator：协调者，用于处理消费者组的重平衡，协调同一组内的消费分区分配

    - 存储层：log-file形式写入磁盘中

- 特征：

    - 快：消息顺序I/O追加写入、页缓存、零拷贝、消息精简、消息批量发送

    - 分区有序性：Sequenece Number + PID

    - 主读主写：可以分担写压力，得益于kafka对主题分区的概念引入，通过多个维度对消息进行切分，将读写压力更均衡得分配到各个broker上，实现更合理的reload

# 2. 消息队列模型

- 点到点模型

- 发布订阅模型

# 3. Kafka的通信过程

# 4. 为什么需要分区

# 5. producer发送信息如何选择分区

# 6. consumer的分区分配策略

# 7. consumer的重平衡

# 8. 如何保证消息可靠性

# 9. broker的副本机制和同步原理

# 10. kafka时间轮

# 11. kafka抛弃zk原因

# 12. kafka快的原因
