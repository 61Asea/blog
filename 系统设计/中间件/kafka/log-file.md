# Kafka：日志存储

包括：目录结构、存储格式、刷盘规则、清理规则

# **文件目录布局**

> 如果有一个名为"topic-log"的主题，该主题有4个分区，那么实际物理存储表现为：“topic-log-0”、“topic-log-1”、“topic-log-2”、“topic-log-3”。如果是分区单副本，则这些Log可能均匀地分布在不同的broker上；如果分区多副本，且分布合理的话，每个broker都应有这些分区的副本日志

![日志文件夹和分段日志](https://asea-cch.life/upload/2021/12/%E6%97%A5%E5%BF%97%E6%96%87%E4%BB%B6%E5%A4%B9%E5%92%8C%E5%88%86%E6%AE%B5%E6%97%A5%E5%BF%97-3d1a50bee46e4dc1b6e2916dbbfa0e55.png)

一个主题分区对应一个日志（Log），为了防止Log过大，Kafka引入了`日志分段`（Log Segement），将Log切分为多个Log Segement：

- Log：在物理上以文件夹的形式存储，代表着一个分区副本，也代表着分区（分区多副本）

- Log Segement：对应磁盘上的一个日志文件和两个索引文件，LogSegement存在多个，但**只有最后一个LogSegment才能执行写入操作**，在此之前所有的LogSegment都不能写入数据

    - activeSegment：最后一个LogSegment，表示当前活跃的日志分段，随着消息的不断写入，当activeSegment满足一定条件时，就需要创建的activeSegment，之后追加的消息将写入新的activeSegment

    - baseOffset：基准偏移量，用来表示当前LogSegment中第一条消息的index

    - 索引文件：用于检索消息

        - .index（偏移量索引文件）

        - .timeindex（时间戳索引文件）

    ![LogSegment](https://asea-cch.life/upload/2021/12/LogSegment-fb14e0cc7ee141a8bd191f1d4ad0ab2a.png)

最后看一下《深入理解Kafka实践原理》提供的文件目录布局：

![broker整体的目录结构](https://asea-cch.life/upload/2021/12/broker%E6%95%B4%E4%BD%93%E7%9A%84%E7%9B%AE%E5%BD%95%E7%BB%93%E6%9E%84-3af55d0011e940a89bd7c172831fdd67.png)

# **消息格式**

每个分区由多条消息日志组成，消息格式特点：

- 精炼（考虑必要字段）

- 占空间尽量小（解析快、磁盘利用率更大、传输更快）

## **V0版本消息**

- LOG_OVERHEAD：日志头部
    - offset：8B，表示逻辑偏移量
    - message size：4B，表示消息长度大小
- RECORD：消息主体
    - crc32：4B，校验值
    - magic：1B，魔数
    - attributes：1B，消息属性（低3位表示压缩类型）
    - key length：4B，消息key的长度（-1表示key为null）
    - key：可选字段
    - value length：4B，实际消息体的长度（-1表示消息为空，即**墓碑消息**）
    - value：消息体

> v0一个消息的最小长度为14B，如果一个消息小于该值，则说明该消息有误从而拒收

## **v1版本消息**

v0版本缺少了时间戳timestamp字段，在很多功能实现上收到了影响

v1版本：

- 新增timestamp字段（8B）
- magic：值为1，表示v1版本
- attributes：第4个bit位开始使用，0表示timestamp类型为create time（producer创建该消息的时间），1表示timestamp为log append time（broker写入到文件的时间），其他位保留

    类型由broker端的参数`log.message.timestamp.type`进行配置，默认类型为创建时间类型，即采用生产者创建消息时的时间戳

> v1一个消息的最小长度为22B

## **v2版本消息**

使用Record Batch取代先前的Message Set，其对应了Producer端的`org.apache.kafka.clients.producer.internals.ProducerBatch`，而之中的每个Record对应`org.apache.kafka.clients.producer.ProducerRecord`

# 参考