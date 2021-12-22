# Kafka：日志存储

包括：目录布局、存储格式、索引、清理规则、刷盘规则

# **1. 文件目录布局**

> 如果有一个名为"topic-log"的主题，该主题有4个分区，那么实际物理存储表现为：“topic-log-0”、“topic-log-1”、“topic-log-2”、“topic-log-3”。如果是分区单副本，则这些Log可能均匀地分布在不同的broker上；如果分区多副本，且分布合理的话，每个broker都应有这些分区的副本日志

![日志文件夹和分段日志](https://asea-cch.life/upload/2021/12/%E6%97%A5%E5%BF%97%E6%96%87%E4%BB%B6%E5%A4%B9%E5%92%8C%E5%88%86%E6%AE%B5%E6%97%A5%E5%BF%97-3d1a50bee46e4dc1b6e2916dbbfa0e55.png)

一个主题分区对应一个日志（Log），为了防止Log过大，Kafka引入了`日志分段`（Log Segement），将Log切分为多个Log Segement：

- Log：在物理上以文件夹的形式存储，代表着一个分区副本，也代表着分区（分区多副本）

- Log Segement：对应磁盘上的一个日志文件和两个索引文件，LogSegement存在多个，但**只有最后一个LogSegment才能执行写入操作**，在此之前所有的LogSegment都不能写入数据

    - activeSegment：最后一个LogSegment，表示当前活跃的日志分段，随着消息的不断写入，当activeSegment满足一定条件时，就需要创建的activeSegment，之后追加的消息将写入新的activeSegment

    - baseOffset：基准偏移量，**通过文件名获取**，用来表示当前LogSegment中第一条消息的index

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

- 时间戳索引文件：根据指定时间戳来查找对应**偏移量信息**

    > 类比innodb的聚簇索引、二级索引，kafka时间戳索引相当于偏移量索引的二级索引

    - timestamp：当前日志分段最大的时间戳

    - relativeOffset：时间戳所对应消息的相对偏移量

Kafka中的索引文件以`稀疏索引(sparse index)`构造消息的索引，它并不保证每个消息在索引文件中都有对应的索引项，具体密度由**log.index.interval.bytes**的值决定

稀疏索引通过MappedByteBuffer将索引文件映射到内存中，以加快索引的查询速度，由于偏移量索引文件中的偏移量是单调递增的，当查询指定偏移量时，使用`二分查找`定位偏移量的位置

> 磁盘空间、内存空间、查找时间等多方面的一个折中方案

## **偏移量索引查找**

偏移量查找：ConcurrentSkipListMap跳表 + 索引文件

跳表保存各个Log Segment的baseOffset（文件名），每个Log Segment的.index文件保存消息稀疏索引，通过二分查找找到具体消息

假设查找偏移量为268的消息：

1. 通过跳表查找出小于268的最大基准LogSegmentA

    ![LogSegment跳表](https://asea-cch.life/upload/2021/12/LogSegment%E8%B7%B3%E8%A1%A8-24f8efa88d174d21a9cf64fe78cb80e1.png)


2. 计算消息相对LogSegment A基准的偏移量

    relativeOffset = 268 - 251 = 17

3. 在对应索引文件中找到不大于17的索引项

    ![稀疏索引查找](https://asea-cch.life/upload/2021/12/%E7%A8%80%E7%96%8F%E7%B4%A2%E5%BC%95%E6%9F%A5%E6%89%BE-e32ae51e17b04bdcb2528acd888e6ca2.png)

    通过二分查找，找到不大于17的最大索引项\<14, 459>\二元组（第一项为逻辑偏移量，第二项为对应.log文件的**物理位置**）

4. 根据索引项中的position物理位置，定位到.log文件中开始**顺序查找（遍历）**目标消息

## **时间戳索引查找**

- 遍历各LogSegment最大时间戳，查找出对应日志分段，获得偏移量
- 偏移量索引查找过程（跳表+稀疏索引）

假设查找时间戳为1526384718288消息：

1. 和每个Log Segment中的最大时间戳largestTimeStamp逐一比对，直到找到不小于1526384718288的最大时间戳对应日志分段

    最大时间戳的计算：查找Log Segment所对应的时间戳索引文件，找到最后一条索引项，若最后一条索引项时间戳 > 0，则取其值；否则，取该Log Segement的**lastModifiedTime最近修改时间**

2. 在对应日志分段的时间戳索引文件中使用**二分查找**，获得\<1526384718283, 28>\（第一项索引的稀疏时间戳值，第二项是消息偏移量）

![时间戳索引回表](https://asea-cch.life/upload/2021/12/%E6%97%B6%E9%97%B4%E6%88%B3%E7%B4%A2%E5%BC%95%E5%9B%9E%E8%A1%A8-58ef373655f6493b9957432efcdb9d73.png)

# **4. 日志清理**

Kafka将消息**存储在磁盘中**，Log分为多个Log Segment便于日志的清理操作，有以下两种**日志清理策略**：

- 日志删除（Log Retention）：按照一定的保留策略**直接删除**不符合条件的**Log Segment**

- 日志压缩（Log Compaction）：针对每个消息的key进行整合，对于有相同key的不同value值，**只保留最后一个版本**

## **4.1 日志删除**

日志删除任务：周期性检测和删除不符合保留条件的Log Segment，周期可通过`log.retention.check.interval.ms`进行配置（默认5分钟）

保留策略：

- 基于时间：默认情况下，只配置了log.retention.hours参数，值为168即保留时间为7天

    通过删除任务，检查当前日志文件中是否有**保留时间超过设定的阈值（retentionMs）**，寻找可删除的Log Segment集合（deletableSegments）

    retentionMs：通过broker端的`log.retention.hours`、`log.retention.minutes`和`log.retention.ms`来配置，其中log.retention.ms优先级最高

    Log Segment的时间：查询该分段所对应的时间戳索引文件，查找索引文件中最后一条索引项，若大于0，则取其值；否则，才取分段的最近修改时间

    > 如果全部分段都过期，则kafka会**切分出一个新日志分段作为activeSegment**，保证接收消息的写入不受影响。并从Log对象维护的分段跳表中移除待删除的日志分段，保证没有线程对这些Segment进行读取操作

- 基于日志大小：默认值为-1，表示无穷大

    通过删除任务，检查当前日志**的总大小是否超过设定的阈值（retentionSize）**，寻找可删除的Log Segment集合（deletableSegments）

    retentionSize：通过`log.retention.bytes`配置全部日志文件的总大小的删除阈值

    > `log.segment.bytes`：单个日志分段的大小

- 基于日志起始偏移量：某日志分段的下一个日志分段的起始偏移量baseOffset <= logStartOffset，则可以删除此日志分段

    logStartOffset：通过sh脚本/KafkaAdminClient.deleteRecords()方法，发送DeleteRecordsRequest请求到broker中对logStartOffset进行修改

## **4.2 日志压缩**

原理：对于有相同key的不同value值，只保留最后一个版本

Log Compaction是在默认的日志删除（Log Retention）**规则之外**提供的一种**清理过时**数据的方式

> 如果应用只关心key对于的最新value值，则可以开启Kafka的日志压缩功能，对相同key的消息定期合并

Log Compaction前后，日志分段中的偏移量**保持一致**，而存活的消息放入新的日志分段文件，**物理位置按照新文件组织**

# **5. 磁盘存储**

传统消息件：使用内存作为默认的存储介质，磁盘作为备选介质，以此实现**高吞吐**和**低延迟**

Kafka：通过磁盘存储和缓存信息，以**顺序I/O（线性顺序）**方式写入磁盘

kafka典型顺序写盘操作：`采用文件追加的方式写入消息`，只能在日志尾部追加新的消息，也不允许修改已写入的消息

## **5.1 顺序I/O**

> 磁盘顺序写入速度600MB/s，磁盘随机写入速度100KB/s，两者性能相差6000倍。**磁盘顺序写比内存随机写的速度快**

![写入速度对比](https://asea-cch.life/upload/2021/12/%E5%86%99%E5%85%A5%E9%80%9F%E5%BA%A6%E5%AF%B9%E6%AF%94-8c8ff0e8624f4030a1c2ccc57f23de18.png)

OS会针对线性写入做深层次优化，包括：预读（提前将一个比较大的磁盘块读入内存）、后写（将小逻辑写操作合并组成大的物理写操作）

## **5.2 page cache**

页缓存：磁盘文件系统的缓存，可以减少对磁盘I/O的操作，把磁盘的数据缓存到内存中，把对磁盘的访问变成对内存的访问

kafka读盘：检测待读取数据是否在页缓存（kernel buffer）上，如果命中缓存，则直接拷贝回用户空间，否则对磁盘发起读取操作

kafka写盘：检测数据对应页是否在页缓存中，如果不存在，则先在page cache中添加相应的页，并写入数据到页中（脏页）

linux脏页写入：`vm.dirty_background_ratio`，指脏页数量占系统内存的百分比阈值，当达到阈值后将触发**pbflush/flush/kdmflush**等后台写进程运行处理脏页

## **5.3 zero copy**


# **总结**

Log：对应一个分区副本，是一个文件夹，Kafka将Log分段为多个Log Segment

Log Segment：日志文件、偏移量索引文件、时间戳索引文件等组成，存储多个RecordBatch

    日志分段后，将文件名作为baseOffset基准偏移量，这有利于日志清理策略和索引的设计（二分查找、跳表、稀疏索引）

偏移量索引文件：\<消息偏移量，消息在.log文件的物理位置>\，这种方式类似myisam非聚簇索引

Kafka高性能的三大因素：消息顺序追加、页缓存、零拷贝

# 参考