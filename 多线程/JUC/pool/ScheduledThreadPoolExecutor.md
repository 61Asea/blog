# ScheduledThreadPoolExecutor

上篇文章描述了ThreadPoolExecutor的池管理

> 上一篇文章：[ThreadPoolExecutor（三）：池管理](http://8.135.101.145/archives/threadpoolexecutor3)

本篇文章涵盖：调度线程池的成分和具体实现

# **1. 成分**

> [线程池概述](http://8.135.101.145/archives/executor)

在线程池概述中，简单地提及到了线程池模块的各个接口与抽象类，对于ThreadPoolExecutor而言，它通过AbstractExecutorService实现了ExecutorService接口的大部分行为，包括：invoke系列、submit系列

![线程池的接口/实现类结构](https://img2018.cnblogs.com/i-beta/1378444/201911/1378444-20191125143829962-1722847086.png)

从上图可知，本文主角ScheduledThreadPoolExecutor通过继承ThreadPoolExecutor和实现ScheduledExecutorService接口，拥有所有父类/接口的行为

```java
// 通过继承ExecutorService，并扩展以下的四种新行为
public interface ScheduledExecutorService extends ExecutorService {
    public ScheduleFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    public ScheduleFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    public ScheduleFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
```

可以看出，调度执行接口拥有了包含ExecutorService及其之上的所有接口，所以我们可以使用接口类型和Executors工具类初始化调度线程池，而不用去关注接口的具体实现子类

```java
public static void main(String[] args) {
    // 面向接口编程，我们只需要关注接口行为，而无须关注具体实现
    // 因为ScheduledExecutorService接口继承了ExecutorService，我们也可以获得其他的线程池行为
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
}
```

# **2. 调度线程接口**

- schedule：单次执行，在给定时长后再执行
- scheduleAtFixedRate: **以线程池启动的时间**作为时间轴，每隔period时长就执行一次
- scheduleWithFixedDelay：**以任务的执行情况**作为时间轴，上一个任务执行完毕后，等待delay时长，再执行下一次

scheduleAtFixedRate和scheduleWithFixedDelay具备了循环执行的能力，即**定时任务**

## **2.1 schedule(Runnable/Callable, long, TimeUnit unit)**

    /**
    * Creates and executes a one-shot action that becomes enabled
    * after the given delay.
    */

该接口行为表示：创建并执行一个在给定**delay**后启用的一次性动作，这也意味着调用该接口，不会立即执行任务

    /**
     * Creates and executes a ScheduledFuture that becomes 
     * enabled after the given delay.
     */

对应地，方法还可传入Callable对象，它会被包装为Future对象，Future会在给定延迟后变成启用状态(enabled)

## **2.2 scheduleAtFixedRate（Runnable command, long initialDelay, long period, TimeUnit unit）**

- initialDelay：初始化后，多久再执行第一次任务
- period：上一个任务的执行与下一个任务的执行的间隔

     注释中文：

     创建并执行一个周期性动作，该动作在给定的初始延迟之后首先启用，然后在给定的时间段内启用；也就是说，执行将在{@code initialDelay}之后开始，然后是{@code initialDelay+period}，然后是{@code initialDelay+2*period}，依此类推。如果任务的任何执行遇到异常，则禁止后续执行。否则，只能通过取消或终止执行者来终止任务。如果此任务的任何执行时间长于其周期，则后续执行可能会延迟开始，但不会同时执行

假设initialDelay为1s，period为1s，任务执行需要2s，从0s开始，调度线程池只有一个线程

格式：第几秒：[正在执行的任务][等待执行的任务]

0：[][]
1: [1][]
2: [1][2]
3: [2][3]
4：[2][3, 4]
5: [3][4, 5]
6: [3][4, 5, 6]

可以发现，该方法是以period作为**周期**，以线程池启动后的时间作为时间轴，在每到一个周期时，无论如何都会再次调度任务。如果发现当前无法执行任务，则进入等待状态。当一直保持任务执行时间 > period，等待执行的任务将会越积越多

## **2.3 scheduleWithFixedDelay**

- initialDelay：初始化后，多久再执行第一次任务
- delay: 上一个任务执行完毕后，等待delay时长的延迟，再执行下一个任务

    注释中文：

    创建并执行一个周期性操作，该操作在给定的初始延迟之后首先启用，然后在一个执行的终止和下一个执行的开始之间具有给定的延迟。如果任务的任何执行遇到异常，则禁止后续执行。否则，只能通过取消或终止执行者来终止任务

假设initialDelay为1s，delay为1s，任务执行需要2s，从0s开始，调度线程池只有一个线程

格式：第几秒：[正在执行的任务][等待执行的任务]

0: [][]
1: [1][]
2: [1][]
3: [][]
4: [2][]
5: [2][]
6: [][]

# **3 调度线程池**

依旧是生产-消费模型相同，但是在生产端与消费端的实现有所变化

1. 生产端

线程池：用户每调一次execute/submit则产出一次任务，如果wc > corePoolSize，则投递到队列中

调度池：用户提交一次任务后，任务按周期/按延迟执行，执行完毕后设置下次执行任务时间，投递到队列中（一直循环）

2. 消费端：

线程池：根据corePoolSize和maxiumPoolSize，用户每调用execute/submit方法时决定是否增加消费者；消费者每次获取任务时，也会决定是否关闭自身

调度池：用户每调用一次delayedExecute，则增加一个消费者，直到消费者达到corePoolSize个数，并一直维持住这个个数。这也意味着maxiumPoolSize参数没有在调度池中没有效果。


## **3.1 基本设施**

- 使用内部类ScheduleFutureTask对传入的任务进行装饰
- 使用DelayedWorkQueue（无界的优先级队列）作为父类阻塞队列的实现

```java
public class SchduleThreadPoolExecutor extends ThreadPoolExecutor implements ScheduleExecutorService {
    // 无界的优先级队列，BlockingQueue的具体实现
    static class DelayedWorkQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {}

    // 内部类，用于装饰传入的任务，也是队列每一项的类型
    private class ScheduledFutureTask<V> extends FutureTask<V> implements RunnableSchedukedFuture<V> {}

    // 成员属性

    // 当线程池处于SHUTDOWN状态时，是否继续执行存在队列中的周期性定时任务
    private volatile boolean continueExistingPeriodTasksAfterShutdown;

    // 当线程池处于SHUTDOWN状态时，是否继续执行存在队列中的延迟定时任务
    private volatile boolean continueExistingDelayTaskAfterShutdown = true;

    // 任务取消之后，是否从队列中删除
    private volatile boolean removeOnCancel = false;

    // 记录当前任务被创建时是第几个任务的一个序号，用于决定当有多个任务下次执行时间相同时，谁先执行。序号小的先执行
    private static final AtomicLong sequencer = new AtomicLong();
}
```

## **3.1.1 ScheduleFutureTask**

```java
private class ScheduledFutureTask<V> extends FutureTask<V> implements RunnableScheduleFuture<V> {
    // 记录当前实例的序列号，用于确认两个下次执行时间相同的任务的执行次序，序号更小的任务先被执行
    private final long sequenceNumber;

    // 任务的下次执行时间
    private long time;

    // 用于记录任务的间隔
    // = 0，代表一次性任务
    // > 0，代表是周期性任务
    // < 0，代表是延迟性任务
    private final long period;

    // 记录需要周期性执行的任务的实例
    RunnableScheduledFuture<V> outerTask = this;

    // 记录当前任务在队列数组位置的下标
    int heapIndex;

    ScheduledFutureTask(Runnable r, V result, long ns, long period) {
        super(r, result);
        // 第一次初始化时，ns为当前时间 + initialDelay
        this.time = ns;
        // FixedRate任务 > 0; FixedDelay任务 < 0
        this.period = period;
        this.sequenceNumber = sequence.getAndIncrement();
    }

    // 获得当前距离下次执行时间的值，单位为纳秒
    public long getDelay(TimeUnit unit) {
        return unit.convert(time - now(), NANOSECONDS);
    }

    public int compareTo(Delayed other) {
        // 下次执行时间越早的，优先级越大
        // 相同下次执行时间的，序号越小的，优先级越大
    }

    // RunnableFuture的接口方法，判断任务是否是循环任务
    // 该方法用于每次执行任务完毕后，检查是否为周期性任务，是的话，则调用setNextRunTime设置下次运行时间
    public boolean isPeriodic() {
        return period != 0;
    }

    private void setNextRunTime() {
        long p = period;
        if (p > 0)
            // 固定周期型任务，FixedRate
            time += p;
        else
            // 延迟周期性任务，FixedDelay
            time = triggerTime(-p);
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        // 如果removeOnCancel为true，则取消任务后，会将任务从队列中移除
        if (cancelled && removeOnCancel && heapIndex >= 0)
            remove(this);
        return cancelled;
    }

    public void run() {
        // 任务执行时，会先判断是否是周期性任务，再采取不同的执行策略
        boolean periodic = isPeriodic();
        if (!canRunInCurrentRunState(periodic))
            cancel(false);
        else if (!periodic)
            // 一次性任务，则调用FutureTask.run()方法
            SchduledFutureTask.super.run();
        else if (ScheduledFutureTask.super.runAndReset()) {
            // 周期性任务，在运行并重置后，设置任务的下次执行时间
            setNextRunTime();
            // 将当前任务放入任务队列中以便下次执行
            // 该方法就是生产投递
            reExecutePeriodic(outerTask);
        }
    }
}
```


## **3.2 关键行为**

# 参考
- [ScheduledThreadPoolExecutor详解](https://blog.csdn.net/qq_40685275/article/details/99836268)

# 重点参考