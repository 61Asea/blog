# ** 线程池具体应用**

通过设置ThreadPoolExecutor的**corePoolSize，maximumPoolSize，keepAliveTime**，我们可以得到不同线程调整策略的线程池，Executors工具类提供了这几个参数的固定组合。

现在已经不建议使用Executors创建线程池，但是它还是为我们提供了以下4种类型线程池的思路：
- SingleThreadExecutor，单线程池
- FixedThreadPool，固定线程数池
- CachedThreadPool，
- ScheduledThreadPool：定时任务线程池

    不建议使用的原因，应该起源于阿里开发手册，手册上有简单介绍，但是当我想深入了解时，发现很多人都是看着手册说不行，就简单起个博客文章，直接说不行。

    我会在下面对手册不建议使用的思路进行分析，并阐述我认为的“不建议”

# **1 线程池的阻塞队列**

## **1.1 LinkedBlockingQueue**

SingleThreadPool和FixedThreadPool使用的阻塞队列，底层是使用链表实现

## **1.2 SynchronousQueue**

> [java并发之SynchronousQueue实现原理](https://blog.csdn.net/yanyan19880509/article/details/52562039)

同步阻塞队列，底层并没有容器，更像是一个管道，TransferQueue队列记录的是生产者/消费者线程的等待情况

```java
// TransferQueue.transfer
E transfer(E e, boolean timed, long nanos) {
    // ...
    if (timed && nanos <= 0)        // can't wait
        return null;
    // ...
}
```

### **SynchronousQueue.put**

一个put一定会匹配一个take，put/take操作没有匹配到时，会在内部TransferQueue队列中挂起等待，该队列有head/tail两个指针

    假设：head -> put1 -> put2(tail)

    take1来了，会直接跟tail做匹配
    
    匹配成功则意味着队头肯定也是put类型的，再唤醒put1出队

    匹配失败则以为着队头肯定也是take类型的，加入到队尾进行等待

```java
// SynchronousQueue.put
public void put(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    // timed传入了false，nanos传入了0
    if (transferer.transfer(e, false, 0) == null) {
        Thread.interrupted();
        throw new InterruptedException();
    }
}
```

transfer不会立刻返回，所以put操作会阻塞住

### **SynchronousQueue.offer**

offer操作则不会遇到阻塞的情况，会直接返回false，具体代码：

```java
// SynchronousQueue.offer
public boolean offer(E e) {
    if (e == null) throw new NullPointerException();
    // 注意，第二个参数timed传入了true
    return transferer.transfer(e, true, 0) != null;
}
```

## **1.3 DelayedWorkQueue**

ScheduledThreadPool使用的阻塞队列，底层使用最大堆实现

> [DelayedWorkQueue优先级阻塞队列](http://8.135.101.145/archives/delayedworkqueue)

## **2 线程池的四种类型**

## **2.1 SingleThreadPool**

    单线程池，因为只有单线程执行，提交任务到SingleThreadPool中，也一定程度上变成了FIFO队列，可以保证任务串行执行

```java
static class FinalizableDelegatedExecutorService extends DelegatedExecutorService {
    FinalizableDelegatedExecutorService(ExecutorService executor) {
        super(executor);
    }
    protected void finalize() {
        super.shutdown();
    }
}

public static ExecutorService newSingleThreadExecutor() {
    return new FinalizableDelegatedExecutorService(
        new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>())
    );
}
```

使用了装饰器，将ThreadPoolExecutor包装了一层，并重写了finalize方法，在GC回收池的时候会调用shutdown方法

包装成FinalizableDelegatedExecutorService之后，将无法类型转换为ThreadPoolExecutor，隐藏了方法，防止调用，比如setCorePoolSize

- corePoolSize: 1
- maximumPoolSize: 1

    corePoolSize和maximumPoolSize = 1：
    那么在第一个用户调用方调用execute/submit时，线程就已经达到顶峰。其他用户调用方都不会addWorker，而是直接将任务丢入到阻塞队列中。

    ```java
    Runnable r = timed ?
        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
        workQueue.take();
    ```
    这也意味着当前工作线程数不会大于corePoolSize，则在getTask()方法中，线工作线程将不会判断是否要削减（timed不会变为true）

- keepAliveTime: 0

    我们默认allowCoreThreadTimeOut不会被调用，即线程一般不允许超时：
    在allowCoreThreadTimeOut(boolean)之后，线程池中的线程个数不大于corePoolSize的时候，keepAliveTime参数也会起作用，直到线程个数为0

    因为getTask()的判断timed不会为true，keepAliveTime就没了实际意义

## **2.2 FixedThreadPool**

    固定线程数的线程池，不会因为线程的长时间无消费而销毁线程，在达到核心线程数后，也不会新开辟新的线程去执行任务

```java
public static ExecutorService newFixedThreadPool(int nThreads) {
    return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
}
```

可以认为，SingleThreadPool就是特殊的FixedThreadPool，即Executors.newFixedThreadPool(1);

- corePoolSize和maxmiumPoolSize相等

    与SingleThreadPool相似，在第三个用户调用方调用execute/submit，线程数达到峰值。其他调用方都不会addWorker，直接将任务怼进队列

- keepAliveTime：0

    与SingleThreadPool一直，没实际意义

## **2.3 CachedThreadPool**

    缓存线程池，该线程池的队列不会无限扩张（没有容器存储），并且可以保证调用方的任务被消费后才会继续往下执行，即阻塞提交

```java
public static ExecutorService newCachedThreadPool() {
    return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
}
```

- corePoolSize = 0，maximumPoolSize = Integer.MAX_VALUE

    execute方法每次被调用时，因为都是wc > corePoolSize，所以优先考虑将任务加入到队列中

    因为SynchoronousQueue并没有存储task，而是记录线程的等待情况。调用offer投递任务时，如果没有匹配到消费者，会另起线程去执行该任务（wc > corePoolSize && !queue.offer(task) && wc < maximumPoolSize）

    getTask方法循环执行时，因为都是wc > corePoolSize，timed属性会一直生效

- keepAliveTime: 60L

    getTask方法中，timed属性一直生效，则如果上一次消费线程等待时间超过60秒，并且本次消费等待时间也超过60秒，则将线程销毁

## **2.4 ScheduleThreadPool**

这个没什么好讲的，有跟没有一样，跟直接使用ScheduleThreadPool没差

> [ScheduledThreadPoolExecutor调度线程池](http://8.135.101.145/archives/scheduledthreadpoolexecutor)

# **3 不建议使用Executors的分析**

阿里手册的提及为：

    线程池不允许使用Executors去创建，而是通过ThreadPoolExecutor方式，这样的处理方式让写的同学更加明确线程池的运行规则，规避资源耗尽的风险。
    说明：Executors返回的线程池对象的弊端如下：
    1）FixedThreadPool和SingleThreadPool
        运行的请求队列长度为Integet.MAX_VALUE，可能会堆积大量的请求，从而导致OOm
    2）CachedThreadPool和ScheduledThreadPool
        允许的创建线程数量为Integer.MAX_VALUE，可能会创建大量的线程，从而导致OOM

我们从该规范可以提取出对线程池基础设施的两个关注点：**队列的长度**和**线程的个数**

以上的两个关注点，由execute方法的三个步骤决定：
- wc < corePoolSize，则新起一个线程
- wc >= corePoolSize，尝试加入队列
- 队列已满，且wc < maximum，则新起一个线程执行

生产消费模型最需要关注的就是**生产消费比**，当一直保持生产速率 > 消费速率的场景，即消费不完，我们可以对Executos产出的池的问题做出分析

## **3.1 Executors.SingleThreadPool**

使用的是**无界**的LinkedBlockingQueue，这也意味着队列永远不会达到边界，队列会一直无限制地扩增

因为**SingleThreadPool不会新起线程**，所以问题往往出现在execute的第二步：

    加入队列永远不会失败（除了线程池关闭），即队列无界不会变满。当长时间消费不完，会堆积任务直至INTEGER_MAX个数，从而可能导致OOM

```java
public static void main(String[] args) {
    // 限定队列长度，规避风险
    ThreadExecutorPool singleThreadPool = new ThreadExecutorPool(1, 1, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1024));
}
```

## **3.2 Executors.FixedThreadPool**

与SingleThreadPool一样

## **3.3 Executors.CachedThreadPool**

设置了MaximumPoolSize的值为INTEGER_MAX，即线程数可能无限制地增长

问题出现execute的第二步和第三步：

    使用同步阻塞队列SynchronousQueue，这意味者如果消费速度过慢（可使用消费者过少），在没有消费者匹配时，第二步的offer操作会直接返回false
    
    每次都执行第三步，去立即启动一个线程去执行任务，线程数又是没有边界的，这可能会堆积大量的线程数，从而导致OOM

```java
public static void main(String[] args) {
    // 限制最大线程长度，规避风险
    ThreadExecutorPool cachedThreadPool = new ThreadExecutorPool(0, 1000, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
}
```

## **3.4 Executors.ScheduledThreadPool**

    阿里手册说线程数会达到INTEGER.MAX_VALUE数，但并不是这个原因

虽然调度线程池的maximumPoolSize的值也为INTEGER_MAX，但是由于调度往往是由程序自发的，且调度池是**自产自销**，所以一般**不会出现消费不完的问题**

而且ScheduledThreadPool提供的构造函数，我们无法去决定maximumPoolSize的大小

使用的优先级阻塞队列会进行自增长（最大到达INTEGER_MAX），如果使用不当，也是可能出现DelayedWorkQueue无限扩展导致内存溢出的情况，比如：

- 重复提交调度任务，使得任务列表直接爆掉

    一般是死循环了，不小心一直提交任务

- 设置间隔过大，且提交的这种间隔的调度任务过多

    由Worker.getTask()方法 -> DelayedWorkQueue的take()方法，如果间隔过大，则任务无法出队，此时提交过大这种任务，会导致DelayedWorkQueue一直增长

# 参考
- [Java使用 Executors 创建四种线程池原理](https://blog.csdn.net/hu2535357585/article/details/105753930)
- [Java中的线程池(3)----SingleThreadExecutor](https://blog.csdn.net/qq_35580883/article/details/78740807)
- [Java中的线程池(2)----FixedThreadPool](https://blog.csdn.net/qq_35580883/article/details/78739241)
- [Java中的线程池(1)----线程池基础知识和CachedThreadPool](https://blog.csdn.net/qq_35580883/article/details/78729606)