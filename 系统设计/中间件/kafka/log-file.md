# Kafka：日志存储

包括：目录布局、存储格式、索引、清理规则、刷盘规则

# **1. 文件目录布局**

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

# **2. 消息格式**

每个分区由多条消息日志组成，消息格式特点：

- 精炼（考虑必要字段）

- 占空间尽量小（解析快、磁盘利用率更大、传输更快）

## **2.1 V0版本消息**

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

## **2.2 v1版本消息**

v0版本缺少了时间戳timestamp字段，在很多功能实现上收到了影响

v1版本：

- 新增timestamp字段（8B）
- magic：值为1，表示v1版本
- attributes：第4个bit位开始使用，0表示timestamp类型为create time（producer创建该消息的时间），1表示timestamp为log append time（broker写入到文件的时间），其他位保留

    类型由broker端的参数`log.message.timestamp.type`进行配置，默认类型为创建时间类型，即采用生产者创建消息时的时间戳

> v1一个消息的最小长度为22B

## **2.3 v2版本消息**

### **2.3.1 长度缩短**

**变长整型（Varints）**：一个或多个字节序列化整数的一个方法，**数值越小则占用字节数越小**，每个字节都有一个位于最高位的msb位，剩余7位存储数据本身，这种类型也称为**Base 128（一个字节表示2^7 = 128个值）**

**msb（most significant bit）**：最高有效位，最后一个字节的msb位为0，其余msb位都设置为1，用来表示其后字节是否与当前字节一起来表示同一个整数

Varints采用**小端字节序**、**ZigZag编码方式**：

    300二进制表示：0000 0001 0010 1100 = 256 + 32 + 8 + 4 = 300

    大端增加msb位：0000 0010 1010 1100（第8位msb位，原二进制表示的第九位需要挪动到第十位）

    大端转小端：1010 1100 1000 0010

    ZigZag编码：sin32 (n << 1) ^ (n >> 31) / sin64 (n << 1) ^ (n >> 63)

优势：与**字段长度**相关的字段会节省空间

- key length：v0和v1版本，即使消息没有key，key length为-1，仍旧需要4个字节进行保存，如果采用Varints编码只需要1个字节

### **2.3.2 格式结构**

使用Record Batch取代先前的Message Set，Record取代先前的Message结构

> Record Batch对应Producer端的`org.apache.kafka.clients.producer.internals.ProducerBatch`，而之中的每个Record对应`org.apache.kafka.clients.producer.ProducerRecord`

Record Batch：

- first offset：当前RecordBatch的起始位移

- length：计算从partition leader epoch字段开始到末尾的长度

- partition leader epoch：分区leader纪元，可以看做分区leader的版本号或更新次数

- magic：v2对应2

- attributes：消息属性，改为占用**两个字节**，低3位表示压缩格式，第4位表示时间戳类型，第5位表示此RecordBatch是否处于事务中，第6位表示是否为控制消息（用于支持事务功能）

- last offset delta：Record Batch中最后一个Record的offset与first offset的差值

- first timestamp：RecordBatch中第一条Record的时间戳

- max timestamp：RecordBatch中最大的时间戳，一般情况下指最后一个Record的时间戳

- producer id：PID，用以支持幂等和事务

- producer epoch：如上

- records count：Record Batch中Record的个数

- `records`：消息集合

    - record：消息结构

        - length：消息总长度

        - attribute：弃用，相关属性放到Batch中的attribute字段
        
        - offset delta：位移增量，保存与Batch first offset的差值

        - headers：支持应用级别的扩展

# **3. 索引**

每个Log Segment对应`.index`、`.timeindex`两个索引文件，用于**提高查找消息的效率**：

- 偏移量索引文件：建立消息偏移量到物理地址之间的映射关系

    偏移量索引项的格式：

    - relativeOffset：4B，相对偏移量，指相对于baseOffset的偏移量，当前索引文件名即为baseOffset的值

    - position：4B，物理地址，指消息在日志分段中对应的物理位置

- 时间戳索引文件：根据指定时间戳来查找对应偏移量信息

    - timestamp：当前日志分段最大的时间戳

    - relativeOffset：时间戳所对应消息的相对偏移量

Kafka中的索引文件以`稀疏索引(sparse index)`构造消息的索引，它并不保证每个消息在索引文件中都有对应的索引项，具体密度由**log.index.interval.bytes**的值决定

稀疏索引通过MappedByteBuffer将索引文件映射到内存中，以加快索引的查询速度，由于偏移量索引文件中的偏移量是单调递增的，当查询指定偏移量时，使用`二分查找`定位偏移量的位置

> 磁盘空间、内存空间、查找时间等多方面的一个折中方案

# **4. 日志清理**

# **5. 磁盘存储**

# 参考