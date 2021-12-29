# Kafka：consumer

**消费者**：负责**订阅**Kafka中的Topic并从中**拉取**消息，consumer一般为某个独立进程实例

订阅API：`public void subscribe(Collection<String> topics, ConsumerRebalanceListener listener)`

# **1. consumer group**

> Kafka的消费理念中，还有一层消费组（Consumer Group）的概念，**每一个消费者都对应的一个消费组**

传统消息系统中，有两种消息模型：点对点模型（`传统消息队列`）、发布/订阅模型：

1. 点对点：消息一旦被消费，就会从队列中删除，而且**只能被下游的一个**consumer消费

    > 伸缩性差：多个consumer从共享队列中取出消费，引入资源竞争可能有锁资源等方式的开销

2. 发布/订阅：允许消息被多个订阅者消费

    > 伸缩性差：所有的订阅者需要订阅主题的**所有分区**

**消费组**：kafka用于实现`单播`和`广播`两种消息模型的手段，同一个topic，每个消费者组都可以拿到相同的全部数据

> 当只有一个消费者组时，实现单播；当有平行多个消费者组，多个组都会收到相同信息，实现广播

消费者组内包含多个consumer实例，每个实例通过分区分配机制，**协调订阅**topic下的所有分区。一个分区只能由同一个消费组中的一个consumer消费，分区可被多个组的固定consumer消费

**示例**：

- 组内consumer数量 > partition数：多余consumer处于空闲状态，**确保每个分区只被同一个组内单个消费者消费**

- 组内consumer数量 <= partition数：当分区数等于消费者数时，每个消费者对应一个分区；当分区数大于消费者数时，有的消费者对应多个分区

- 多个消费者组：相同的数据会被不同组的消费者**消费多次**

# **2. 位移提交**

`offset`：如果是指消费者**消费到的位置**，称为位移；如果是指在**消息所处分区的位置**，称为偏移量

每次调用poll()时，返回的是没消费过的消息集，所以需要记录上一次消费时的位移，并且做持久化保存

> 旧版本的kafka将值保存在zookeeper中，由于zookeeper高并发读写性能较差，新版本的则将唯一存储在kafka内部主题`__consumer_offsets`，但却产生了众多问题

`position`：代指上面的offset，消费者需要提交的消费位移，且如果当前消费到x位置时，提交的值应为**x+1**

`committed offset`：已提交过的消费位移

`lastConsumedOffset（consumed offset）`：某次拉取消息集中，最后一个消息的位移

`LEO（Log End Offset）`：下一条**待写入**消息的位移

提交的方式有两种：

1. 自动提交（高级API）：配置`enable.auto.commit = true`

    > 最佳实践：关闭自动提交，在业务执行完毕后手动提交，一般交由spring-kafka执行提交策略

    - 还没有自动提交位移，且在消费过程宕机，重启消费者后消息会重复消费

    - 拉取到信息后恰好自动提交位移，消费过程宕机，重启消费者后消息会丢失

2. 手动提交（低级API）：配置`enable.auto.commit = false`，并手动调用`KafkaConsumer.commitSync()`等提交位移的方法

    > 消费端**消息可靠**：避免了消费端的消息丢失问题，但要保证消费端幂等性，以应对重复消费问题的引入

## **相关API**

- 获取消费者所分配的分区信息：`KafkaConsumer.assignment()`

    须先调用consumer.poll(Duration.ofMillis(x))，poll()方法内部会对消费者进行分区分配
    
    > x的值必须够大，否则可能还来不及实施分区就执行了assignment()，最终得到不符合预期的空列表

- 指定分区位移：`KafkaConsumer.seek(TopicPartition partition, long offset)`

    搭配assignment()的结果进行指定，后续的poll()会从新的位移位置开始

# **3. 分区分配策略**

消费者客户端分配策略参数：`partition.assignment.strategy`，提供三种策略RangeAssignor、RoundRobinAssignor、StickyAssignor，用户可以自实现分配策略

## **3.1 RangeAssignor（范围策略）**

原理：在主题维度上，按照消费者总数和**主题分区总数**进行整除来获得跨度，再将这些分区按照跨度平均分配给各消费者

跨度：`n = 主题分区总数 / 消费者总数`

> 肯定存在无法整除的不平均分配情况，所以字典序靠前的消费者会被**多分配一个分区**，即n+1

获得跨度数量的前m个消费者：`m = 主题分区总数 % 消费者总数`

**示例**：

- 例子1：存在2个主题0、1，每个主题都有4个分区，共有两个消费者C0、C1

    主题0：
    
    - n = 1 * 4 / 2 = 2，跨度为2，如果最后分配无法平均，则前m个消费者需要分配3个分区

    - m = 4 % 2 = 0，表示可以平均分配

    - 结果：C0 = [t0p0, t0p1]，C1 = [t0p2, t0p3]

    主题1：同理主题0

    - 结果：C0 = [t1p0, t1p1]，C1 = [t1p2, t1p3]

    最后结果：C0 = [t0p0, t0p1, t1p0, t1p1]，C1 = [t0p2, t0p3, t1p2, t1p3]

- 例子2：存在2个主题0、1，每个主题都有3个分区，共有两个消费者C0、C1

    主题0：
    
    - n = 1 * 3 / 2 = 1
    
    - m = 3 % 2 = 1，表示无法平均分配，前1个消费者需要分配2个分区（n + 1 = 2）

    - 结果：C0 = [t0p0, top1]，C1 = \[t0p2\]

    主题1：同理主题0

    - 结果：C0 = [t1p0, t1p1]，C1 = \[t1p2]\

    最后结果：C0 = [t0p0, t0p1, t1p0, t1p1], C1 = [t0p2, t1p2]

缺点：在主题分区数量无法被组内消费者数整除时，会出现不够均匀的情况，且这种不均匀情况还会**随着主题数变多而扩大**

## **3.2 RoundRobinAssignor（轮询策略）**

原理：将消费组内所有消费者，及消费者订阅的**所有主题的分区**按照字典排序，并通过**轮询方式逐个依次分配给每个消费者**

> 主题以及主题内的分区都是逐次遍历

**示例**：

- 例子1：存在2个主题0、1，每个主题有三个分区0、1、2，共有C0、C1两个消费者

    主题0：

    - 遍历顺序：t0p0 -> C0，t0p1 -> C1，t0p2 -> C0

    - 结果：C0 = [t0p0, t0p2]，C1 = \[t0p1\]

    主题1：

    - 遍历顺序：t1p0 -> C1（因为上一个遍历分配的是C0），t1p1 -> C0，t1p2 -> C1

    - 结果：C0 = \[t1p1\]，C1 = [t1p0, t1p2]

    最后结果：C0 = [t0p0, t0p2, t1p1]，C1 = [t0p1, t1p0, t1p2]

- 例子2：存在3个主题0、1、2，三个主题的分区数依次为1、2、3，共有C0、C1、C2三个消费者，其中C0订阅主题0，C1订阅主题0、1，C2订阅主题1、2

    主题0：

    - 遍历顺序：t0p0 -> C0

    - 结果：C0 = \[top0]\，C1 = [], C2 = []

    主题1：

    - 遍历顺序：t1p0 -> C1，t1p1 -> C2

    - 结果：C0 = []，C1 = \[t1p0]\, C2 = \[t1p1]\

    主题2：不会遍历到C0和C1，因为它们都没有订阅主题2

    - 遍历顺序：t2p0 -> C2，t2p1 -> C2, t2p2 -> C2

    - 结果：C0 = [], C1 = [], C2 = [t2p0, t2p1, t2p2]

    最后结果：C0 = \[t0p0\]，C1 = \[t1p0\]，C2 = [t1p1, t2p0, t2p1, t2p2]

缺点：当遇到各个消费者**订阅主题不一致**时，仍会出现分配不均问题

## **3.3 StickyAssignor（粘滞策略）**

原理：在轮询策略的基础上，添加了`分区分配尽可能均匀`和`分区分配尽可能与上一次分配保持相同`两个特性，当两个特性发生冲突时，优先考虑第一特性

> 前者可以一定程度上解决轮询策略中的不平均问题，后者则是对于消费者/broker数量动态变化时的重均衡问题

**示例**：依旧是2.3.2的例子2

主题0：

- 遍历顺序：t0p0 -> C0

- 结果：C0 = \[t0p0]\，C1 = [], C2 = []

主题1：

- 遍历顺序：t1p0 -> C1，t1p1 -> C1（**轮询策略会给C2，但是黏性策略会根据整体均衡度来做决定**）

- 结果：C0 = []，C1 = [t1p0, t1p1], C2 = []

主题2：与2.3.2相同

最后结果：C0 = \[t0p0\]，C1 = [t1p0, t1p1]，C2 = [t1p1, t2p0, t2p1, t2p2]

**再均衡（重均衡）的应对**：

当出现broker/consumer数量变化时，分区与consumer的关系会出现变化，之前的消费者与新指派的消费者可能不会再是同一个，这存在一个问题：

    之前消费者进行一般的处理，会因为突如其来的再均衡，在新指派的消费者中再复现一遍

所以在黏性策略下，会尽量保证前后两次分配相同，进而**减少系统资源损耗**与**减少其他异常情况发生**

# **4. 重均衡**

定义：**分区所属权**从一个消费者**转移**到另一个消费者的行为，为消费组提供高可用性和伸缩性

功能：我们可以通过该机制方便安全地**删除**组里的消费者，或往消费者组里**添加**新的消费者

缺点：

- 再均衡过程中，消费组**不可用**

- 一个分区的所属消费者更换时，消费者当前的状态会丢失，进而导致重复消费

> 一般情况下，应尽量避免不必要的再均衡发生

流程：

1. 第一阶段：确定消费者组所属的coordinator（FIND_COORDINATOR）

    consumer加入消费者组，并与coordinator建立连接

    coordinator选取规则：

    - 计算分区数：partition_num = group_id % groupMetadataTopicPartitionCount

    - 获取_consumers_offsets主题的partition_num分区leader副本所在broker

2. 第二阶段：发送请求给协调者以加入消费者组（JOIN_GROUP），消费者组在处理完毕后返回结果给各个消费者

    - JOIN_GROUP_REQUEST：请求完毕后阻塞等待kafka的返回

        consumer发送JoinGroupRequest请求，请求携带group_protocols域，囊括消费者所配置的多种策略，由`partition.assignment.strategy`配置

        coordinator会通过一个Map维护每个发送JoinGroup请求的consumer

        主要有两个目的：

        - 选举消费者**组leader**：启动先到先得，后续随机

            如果消费组内还未有leader，则第一个加入消费者组的消费者为组leader
            
            如果某一时刻，leader消费者由于某种原因退出组，则使用Map的第一个键值对

        - 投票选取**消费者组的分配策略**：选取各个消费者支持的最多策略

            收集各个消费者支持的所有分配策略，组成去重候选集candidates，并从每个消费者的group_protocols中选出第一个支持的策略，最后选取最多投票的策略

    - JOIN_GROUP_RESPONSE：

        处理完leader选举和最终分区策略后，发送响应给各个consumer，响应包括：leader_id、members_id（leader consumer有值，其他consumer为空）、protocol_metadata

3. 第三阶段（SYNC_GROUP）：组leader通过第二阶段选取的策略实施具体的分区分配，并通过coordinator将方案转发给各个消费者

    每个consumer都会发送SYNC_GROUP_REQUEST到coordinator中，leader consumer的请求携带分配结果，其他consumer请求相当于拉取元数据

4. 第四阶段（HEART_BEAT）：所有consumer进入正常工作状态，并向GroupCoordinator发送心跳来维持它们与消费组的从属关系，以及它们对分区的所有权关系

### **重均衡监听器ConsumerRebalanceListener**

subscribe(...)订阅接口中，提供了重均衡监听器的形参，该监听器用于设定发生再均衡**动作前/后的一些准备或收尾动作**

```java
public interface ConsumerRebalanceListener {
    // 再均衡开始之前，和消费者停止读取消息之后被调用，通过这个方法来处理消费位移的提交，避免不必要的重复消费
    // partitions：该消费者在再均衡前所分配到的分区
    void onPartitionsRevoked(Collection<TopicPartition> partitions);

    // 再均衡完成后，和消费者开始读取消息之前被调用
    // partitions：再均衡后所分配到的新分区
    void onPartitionsAssigned(Collection<TopicPartition> partitions);

    // ConsumerCoordinator协调者发现分区宕机时，会调用该接口
    default void onPartitionsLost(Collection<TopicPartition> partitions) {
        onPartitionsRevoked(partitions);
    }
}
```

示例：

```java
public class KafkaRebalanceDemo {
    private final static KafkaConsumer consumer = new KafkaConsumer(new Properties());

    private final static List<String> topicList = Arrays.asList("topic1");

    private final static Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    public static void main(String[] args) throws Exception {
        consumer.subscribe(topicList, new ConsumerRebalanceListener() {
            @Override
            public void onPartitionRevoked(Collection<TopicPartition> partitions) {
                // 再均衡操作前，先将当前已消费到的位移提交，该接口方法被调用时可以保证消费者已停止拉取消息
                consumer.commitSync(currentOffsets);
                currentOffsets.clear();
            }
        });

        while (true) {
            // 拉模式
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMills(1000));
            // 对所有消息进行遍历，当然也可以根据分区进行遍历
            for (String topic : topicList) {
                // records重写了迭代器，会把各个ConsumerRecord都给遍历出来
                for (ConsumerRecord<String, String> record : records) {
                    // 业务处理

                    // 重写了equals方法和hashcode()方法，暂且认为List<ConsumerRecord<String, String>>是有序的
                    currentOffsets.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset() + 1));
                }
                consumer.commitAsync(currentOffsets, null);
            }
        }
    }
}
```

# **5. 消费者线程模型**

主要从`消费速率`和`可扩展性`来考虑模型设计

> Kafka通过fail-fast机制来规避**线程并发操作**的情况，应当认为consumer是线程不安全的

## **5.1 线程非安全**

KafkaConsumer提供给外部的API中，都会在内部调用`acquire() / release()`方法，具体的方式是通过原子工具类进行`CAS`操作

- 当acquire()有并发实例进行操作，通过该机制能使其及时抛错失效，确保当前执行线程的安全
- 当前持有资源的执行线程在完成任务后，会在内部调用release()将原子类复位

```java
public class KafkaConsumer {
    private final AtomicLong currentThread = new AtomicLong(NO_CURRENT_THREAD);

    private final AtomicInteger refcount = new AtomicInt(0);

    private void acquire() {
        long threadId = Thread.currentThread().getId();
        // 关键在于后面的CAS操作，乐观锁的方式避免阻塞等待
        if (threadId != currentThread.get() && !currentThread.compareAndSet(NO_CURRENT_THREAD, threadId)) {
            throw new CurrentModificationException("...");
        }

        // 可重入
        refcount.incrementAndGet();
    }

    private void release() {
        if (refcount.decrementAndGet() == 0) {
            currentThread.set(NO_CURRENT_THREAD);
        }
    }
}
```

通过对线程操作计数标记进行CAS操作来取代悲观锁，不会造成阻塞等待的同时，确保同一时刻只有一个线程在操作

## **5.2 I/O模型**

由于KafkaConsumer线程不安全的特性，所以**不能通过多线程操作同一KafkaConsumer实例**

这不意味着只能用单线程的方式进行：

1. 每个线程对应一个**KafkaConsumer实例**，我们将这些线程称为消费线程

    > 类似socket编程的`one connection per thread`思想：为每一个TCP连接创建一个线程进行处理

    原理：消费线程可以消费一个或多个分区的消息，不同消费线程间的消费分区**没有交集**，所有消费线程都隶属于同一个消费组

    优点：简单易实现

    缺点：并发度由主题的分区数决定，线程大于分区数时，就有部分线程处于空闲状态

    结论：如果将线程替换为进程也是同样的思路，事实上分布式系统下都是以多个消费者进程独立存在

    ```java
    package cn.shiyujun.kafka;

    import org.apache.kafka.clients.consumer.ConsumerRecord;
    import org.apache.kafka.clients.consumer.ConsumerRecords;
    import org.apache.kafka.clients.consumer.KafkaConsumer;

    import java.time.Duration;
    import java.util.Arrays;
    import java.util.List;
    import java.util.Properties;

    public class FirstMultiThreadKafka {
        private final static List<String> topicList = Arrays.asList("topic-demo");

        static class KafkaConsumerThread implements Runnable {
            private KafkaConsumer<String, String> consumer;

            private String topic;

            public KafkaConsumerThread(Properties properties, String topic) {
                this.consumer = new KafkaConsumer<>(properties);
                this.topic = topic;
            }

            @Override
            public void run() {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord record : records) {
                        // do something
                    }
                }
            }

            public KafkaConsumer<String, String> getConsumer() {
                return consumer;
            }

            public void connectSync() {
                do {
                    consumer.poll(Duration.ofMillis(1000));
                } while (consumer.assignment().isEmpty());
            }

            public int partitionsNumFor(String topic) {
                return getConsumer().partitionsFor(topic).size();
            }
        }

        public static void main(String[] args) {
            Properties properties = new Properties();
            KafkaConsumerThread mainConsumer = new KafkaConsumerThread(properties, topicList.get(0));
            mainConsumer.connectSync();
            int subThreadNum = mainConsumer.partitionsNumFor(topicList.get(0)) - 1;

            for (int i = 0; i < subThreadNum; i++) {
                new Thread(new KafkaConsumerThread(properties, topicList.get(0))).start();
            }

            mainConsumer.run();
        }
    }

    ```

2. 多个线程同时消费一个分区

    优点：突破第一种方式下消费线程不能超过分区数的限制，进一步提高了消费能力

    缺点：使用assign()、seek()等方式实现，但是对于提交位移和串行控制的处理会变得异常复杂，实际使用较少

3. 方案1结合多路复用模型的优化方案

    优点：
    - 将poll()动作与消费动作解耦，poll频率的变化不会对消费速率**过于敏感**
    - 消费动作可以使用线程池方式，增加整体吞吐量

    缺点：依旧是方案1的缺点

    ```java
    @Override
        public void run() {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord record : records) {
                    // 直接将业务都投递到业务线程池中
                    executor.submit(() -> {
                       // do something
                    });
                }
            }
        }
    ```

# 参考
- [Kafka消费者组是什么？](https://blog.csdn.net/qq_36918149/article/details/99656156)
- [消费者组和分区数对应关系测试](https://blog.csdn.net/gdkyxy2013/article/details/86644919)

- [kafka在高并发的情况下,如何避免消息丢失和消息重复?](https://www.cnblogs.com/williamwucoder/p/13347934.html)

# 重点参考
- [队列如何保证消息不丢失？如何处理重复消费？](https://hollis.blog.csdn.net/article/details/104285138?spm=1001.2101.3001.6661.1&utm_medium=distribute.pc_relevant_t0.none-task-blog-2%7Edefault%7ECTRLIST%7Edefault-1.no_search_link&depth_1-utm_source=distribute.pc_relevant_t0.none-task-blog-2%7Edefault%7ECTRLIST%7Edefault-1.no_search_link)：完全避免消息重复发送是很困难的，重复消费的场景在手动提交中仍旧会发生，所以应该保证生产、消费过程保持幂等性；重心放在消息尽量不丢失的方向上，再去处理由此导致加剧的重复消费问题

- [从producer、broker、consumer上分析丢失消息](https://zhuanlan.zhihu.com/p/309518055)