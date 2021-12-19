# Kafka：Producer

# **1. 整体架构**

![生产者架构](https://asea-cch.life/upload/2021/12/%E7%94%9F%E4%BA%A7%E8%80%85%E6%9E%B6%E6%9E%84-d1918ba171c3410287e75196ff42740a.png)

- KafkaProducer：生产方调用组件，将具体消息包装为ProducerRecord对象进行发送

- ProducerInterceptors：生产者拦截器，用来在消息发送前做一些准备工作

- Serilizer：序列化器，用于将消息对象转化为二进制数据

    > [二进制协议和文本协议的区别](https://blog.csdn.net/qq_40771473/article/details/106003171)：无论是文本协议、二进制协议，数据以二进制字节流的形式从a传输到b
    
    它们之间不同在于：

    - 二进制协议：传输双方指定结构规则，数据中不包含结构信息

        > 调试方可读性差，需要通过结构规则进行解析，无法简单解析为字符串

    - 文本协议：常见json、xml，数据中带有结构信息，双方无需制定强结构规则
    
        > 调试方的可读性强，文本协议的二进制数据可直接解析为字符串，无需顾虑结构解析规则

- Partitioner：分区器，用于给序列化后的消息分配发送的分区，以消息的key值作为分区规则

    - 没有key：轮询分区方式发送

    - 有key：hash(key) % 分区数

- `RecordAccumulator`：消息累加器，也称为发送方缓存

    - ProducerBatch：发送消息批

- Sender：发送者线程

思路：当主线程调用KafkaProducer发送消息时，消息**不会立即发出**，而是存储在缓存中，**交由Sender线程**进行发送和确认

## **1.1 消息累加器**

RecordAccumulator的内部**为每个分区维护了一个双端队列**，队列的内容是ProducerBatch

```java
public final class RecordAccumulator {
    private final int batchSize;
    // 实现ByteBuffer复用，避免频繁创建和释放
    private final BufferPool free;

    // key：分区，value：ProducerBatch的双端队列
    private final ConcurrentMap<TopicPartition, Deque<ProducerBatch>> batches;
}
```

- 消息写入缓存：取出队尾的ProducerBatch，判断Batch的剩余大小能否容纳消息，不行则创建新的ProducerBatch进行存储，并追加到队列尾部

    将多个较小的ProducerRecord拼凑成较大的ProducerBatch，**减少网络请求次数提升整体吞吐量**

- Sender读取消息：从双端队列的头部读取出

> 缓存相关的容灾/容量满导致的消息不可靠问题分析，见第三节

## **1.2 Sender**

![IO发送](https://asea-cch.life/upload/2021/12/IO%E5%8F%91%E9%80%81-c4a74ec3052342d78d48359b4f26c115.png)

Sender从RecordAccumulator中获取缓存消息，并进一步将原本ConcurrentHashMap\<TopicPartition, Deque\<ProducerBatch\>\>的保存形式转变为\<Node, List\<ProducerBatch\>\>，Node表示Kafka集群中的broker节点：

```java
private long sendProducerData(long now) {
    Cluster cluster = metadata.fetch();
    // ready内部逻辑：遍历分区map的键值对（key分区，value双向队列）集合，通过cluster找出分区对应的Node信息
    // ReadyCheckResult.readyNodes：分区对应的broker节点
    RecordAccumulator.ReadyCheckResult result = this.accumulator.ready(cluster, now);

    // ...
    // create produce requests
    Map<Integer, List<ProducerBatch>> batches = this.accumulator.drain(cluster, result.readyNodes, this.maxRequestSize, now);
    addToInflightBatches(batches);
    if (guaranteeMessageOrder) {
        // Mute all the partitions drained
        for (List<ProducerBatch> batchList : batches.values()) {
            for (ProducerBatch batch : batchList)
                this.accumulator.mutePartition(batch.topicPartition);
        }
    }

    // ...

    // 方法内部调用KafkaClient进行发送
    sendProduceRequests(batches, now);
    return pollTimeout;
}
```

KafkaClient的具体子类实现：

```java
public class NetWorkClient {
    private void doSend(ClientRequest clientRequest, boolean isInternalRequest, long now) {
        // ...
        doSend(clientRequest, isInternalRequest, now, builder.build(version));
    }

    private void doSend(ClientRequest clientRequest, boolean isInternalRequest, long now, AbstractRequeset request) {
        String destination = clientRequest.destination();
        RequestHeader header = clientRequest.makeHeader(request.version());
        if (log.isDebugEnabled()) {
            log.debug("Sending {} request with header {} and timeout {} to node {}: {}",
                clientRequest.apiKey(), header, clientRequest.requestTimeoutMs(), destination, request);
        }
        Send send = request.toSend(header);
        InFlightRequest inFlightRequest = new InFlightRequest(
                clientRequest,
                header,
                isInternalRequest,
                request,
                send,
                now);
        // inFlightRequests的底层存储结构是一个Map，key：node
        this.inFlightRequests.add(inFlightRequest);

        // 最终通过selector发送
        selector.send(new NetworkSend(clientRequest.destination(), send));
    }
}
```

最终使用selector进行发送，在发送之前会将该消息对应的ack信息注册到`this.inFlightRequests`中，提供后续重试机制的依据

### **Sender重试机制**

```shell
retries = 10
retry.backoff.ms = 100
```

- retries：用来配置sender的重试次数，当发生可重试异常时（网络抖动、leader副本选举），内部将会尝试自动恢复

- retry.backoff.ms：重试间隔，还需要配置总的重试时间

做法：

```java
public class NetWorkClient {
    private void doSend(ClientRequest clientRequest, boolean isInternalRequest, long now) {
        try{
            // ...
        }
        catch (UnsupportedVersionException unsupportedVersionException) {
            ClientResponse clientResponse = new ClientResponse(clientRequest.makeHeader(builder.latestAllowedVersion()),
                    clientRequest.callback(), clientRequest.destination(), now, now,
                    false, unsupportedVersionException, null, null);

            if (!isInternalRequest)
                abortedSends.add(clientResponse);
            else if (clientRequest.apiKey() == ApiKeys.METADATA)
                metadataUpdater.handleFailedRequest(now, Optional.of(unsupportedVersionException));
        }
    }
}
```

具体的回调逻辑为`Sender.handleProduceResponse`方法

## **1.3 分区有序性**

partition：write ahead log，保证FIFO顺序写入

乱序问题往往由网络情况不佳导致，kafka解决方案为：

1. 通过配置`max.in.flight.requests.per.connection = 1`

    该配置代表producer往broker发送数据的请求数，配置为1则表示一次只能发送一个请求

    原理：某个消息发送失败则继续重试，**直到成功才会进行下一个请求的发送**

2. 生产者幂等特性

    Kafka引入`Producer ID（PID）` + `Sequence Number`

    PID：对于每个PID，该Producer发送消息的每个\<Topic, Partition\>都对应一个单调递增的Sequence Number
    
    broker端也会维护一个\<PID, Topic, Partition>\，与producer发送的消息序号进行比对：

    - 消息序号 - broker序号 == 1：正常情况

    - 消息序号 - broker序号 > 1：**中间有数据未写入，即出现乱序**
    
        broker拒绝该消息，producer抛出InvalidSequenceNumerException

    - 消息序号 < broker序号：**重复消息，一般为网络异常**
    
        broker直接丢弃，producer抛出DuplicateSequenceNumberException

# **2. 消息发送**

生产者消息结构：

```java
public class ProducerRecord<K, V> {
    private final String topic; // 主题
    private final Integer partition; // 分区号
    private final Headers headers; // 消息头部
    private final K key; // 键，用于分区
    private final V value; // 消息内容
    private final Long timestamp; // 消息时间戳
}
```

配置：

```java
public static Properties initConfig() {
    Properties props = new Properties();
    // broker列表，生产者启动后会进行连接
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
    // key的序列化器
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    // 消息结构中的value序列化器
    props.put(ProducerConfig.VALUES_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
    // `client.id`，表示该生产者客户端的PID？
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "producer.client.id.demo");
    // 设置自行恢复重试次数，如果到达次数后仍旧没有恢复，那么会抛出异常
    props.put(ProducerConfig.RETRIES_CONFIG, 10);
    return props;
}
```

## **发送方式**

```java
public class KafkaProducer {
    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
        return send(record, null);
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
        ProducerRecord<K, V> interceptedRecord = this.interceptorss.onSend(record);
        return doSend(interceptedRecord, callback);
    }
}
```

send()方法本身就是异步，返回的对象为Future\<RecordMetadata\>类型，调用方可以对该对象使用不同方式**获取/等待**结果

- 发后即忘：

    - 效率：最高
    - 可靠性：最差
    
        **完全依赖自动重试机制**，当重试次数达到上限，或遇到不可重试异常，**消息将直接丢失**

    ```java
    KafkaProducer.send(record);
    ```

- 同步发送：

    - 效率：最差

        阻塞等待一条消息发送完后，才能发送下一条

    - 可靠性：最高

        要么发送成功，要么抛出异常且能被调用线程捕获

    ```java
    KafkaProducer.send(record).get();
    ```

- 异步发送：一般使用send(ProducerRecord, Callback)方式注册回调，因为很难控制**Future.get()**的调用时机和方式

    - 效率：高

    - 可靠性：高，通过指定Callback，在kafka返回响应时调用该回调函数，以确认发送的情况

    metadata和exception互斥，当发送成功则exception为null，反之metadata为null

    ```java
        KafkaProducer.send(record, new Callback() {
            @Override
            public void onCompletion(RecordMetadata metadata, Exception exception) {
                if (exception != null) {
                    logger.error("ex: {}", exception);
                } else {
                    // do something
                }
            }
        });
    ```

对于异步发送方式而言，如果存在发送多个消息record1和record2，且前者先于后者发送：

```java
producer.send(record1, callback1);
producer.send(record2, callback2);
```

KafkaProducer可以保证对应的callback1在callback2之前调用，即回调函数的调用**也可以保证分区有序**

# **3. 生产端消息可靠**

消息可靠：`有序`和`不丢失`

## **3.1 消息丢失**

消息不是立即送出，而是存于消息累加器中，即**发送者的缓存**

- 宕机

    内存具有易失性，处于缓存的数据是危险的，一旦出现非法停止/宕机的情况，buffer的数据将被丢弃，从而造成消息丢失

- 内存不足

    - 丢弃策略：当内存不足以容纳新的消息写入时，消息也会丢失

    - `阻塞策略`：生产线程阻塞等待，当挂起线程过多导致内存不足时会进而程序崩溃，消息仍会丢失

        ```shell
        queue.buffering.max.ms=5000 // 异步发送模式下，缓存数据的最长时间，之后便会被发送到broker
        queue.buffering.max.messages=10000 // 异步模式下最多缓存的消息条数
        queue.enqueue.timeout.ms = -1 // 0代表队列没满的时候直接入队，满了立即扔弃，-1代表无条件阻塞且不丢弃
        batch.num.messages=200 // 一次批量发送需要达到的消息条数，当然如果queue.buffering.max.ms达到的时候也会被发送
        ```

解决思路：

> 以下思路都建立在采取`阻塞策略`的情况

1. 控制消息的生产速率

    - 将异步发送消息改为同步阻塞发送

    - 使用阻塞线程池，并限制池内的线程数

2. 适当扩大buffer容量配置

3. 不直接发送到消息累加器，转而写入其他介质，再由某个异步线程读取发送到内存

> Quotas配额限速机制：`producer_byte_rate = xxx`

## **3.2 有序**

- 网络延迟：broker先后发送record1和record2，前者由于网络延迟比后者更迟到达，导致乱序

- 网络丢包：broker已经处理了record，但响应丢失，导致producer重新发送record

解决思路：生产者幂等机制 + 重试机制（retried次数不能过小）

# **4. 总结**

线程模型：主线程 + Sender

I/O模型：生产者缓存 + Sender epoll模式

消息问题：
- 丢失：
    - 缓存区满：采用阻塞策略，防止缓存清除（默认清除策略，会将丢失消息）
    - 网络问题：Sender提供消息ack + 重试机制
- 重复：由重试导致，通过幂等机制（PID + Sequence Number）解决
- 有序：由网络延迟导致，通过幂等机制（PID + Sequence Number）解决

# 参考

- [Producer端如何保证消息不丢失不乱序](https://blog.csdn.net/weixin_43750952/article/details/85834385)

# 重点参考
- [队列如何保证消息不丢失？如何处理重复消费？](https://hollis.blog.csdn.net/article/details/104285138?spm=1001.2101.3001.6661.1&utm_medium=distribute.pc_relevant_t0.none-task-blog-2%7Edefault%7ECTRLIST%7Edefault-1.no_search_link&depth_1-utm_source=distribute.pc_relevant_t0.none-task-blog-2%7Edefault%7ECTRLIST%7Edefault-1.no_search_link)：完全避免消息重复发送是很困难的，重复消费的场景在手动提交中仍旧会发生，所以应该保证生产、消费过程保持幂等性；重心放在消息尽量不丢失的方向上，再去处理由此导致加剧的重复消费问题

- [从producer、broker、consumer上分析丢失消息](https://zhuanlan.zhihu.com/p/309518055)

- [二进制协议和文本协议的区别](https://blog.csdn.net/qq_40771473/article/details/106003171)