# **ThreadPoolExecutor（三）：池管理**

上篇文章描述了线程池是通过哪些关键行为，运转起生产-消费模型

> 上一篇文章：[ThreadPoolExecutor（二）：生产消费](http://8.135.101.145/archives/threadpoolexecutor1)

本篇文章的知识点涵盖了：线程池属性统计、线程池关闭和队列满时的拒绝策略，在开始以上知识点前，我们先引入线程池对于这些操作的独占属性

# **1. mainlock（独占操作）**

Doug Lea使用ReentrantLock使以上的几种行为互斥，在注释中说明了以下原因：

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     */

    锁定Workers Set和相关属性统计的访问
    
    虽然我们可以使用某种类型的并发集，但通常情况下最好使用锁。其中一个原因是它序列化了interruptIdleworkers操作，从而避免了不必要的中断风暴，特别是在关机期间，否则退出的线程将同时中断那些尚未中断的线程
    
    它还简化了一些与最大池大小等相关的统计簿记。我们还对shutdown和shutdownow持有mainLock，以确保workers set是稳定的，同时分别检查是否允许中断和实际中断

个人对注释的理解：

    使用mainLock的最根本原因是它即可以保证workers set的并发安全，又避免了中断风暴，还能够简化线程安全的统计操作

```java
// 伪代码
public class ThreadPoolExecutor {
    private final ReentrantLock mainLock = new ReentrantLock();

    private boolean addWorker(Runnable firstTask, boolean core) {
        // mainlock.lock();
        // 新增工作线程时，将w加入workerSet
        // mainlock.unlock();
    }

    private void addWorkerFailed(Worker w) {
        // mainlock.lock();
        // 新增失败，将w从workerSet删除
        // mainlock.unlock();
    }

    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // mainlock.lock();
        // 统计w已完成的数据，加到线程池的成员变量completedTaskCount
        // 将w从workerSet删除
        // mainlock.unlock();
    }

    private void shutdown() {
        // mainlock.lock();
        // 设置线程池状态为SHUTDOWN
        // 中断空闲线程
        // mainlock.unlock();
    }

    private void shutdownNow() {
        // mainlock.lock();
        // 设置线程池状态为STOP
        // 中断所有线程，包括空闲线程
        // mainlock.unlock();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) {
        // mainlock.lock();
        // 指定时间等待线程池的状态变为TERMINATED
        // mainlock.unlock();
    }

    public int getPoolSize() {
        // mainlock.lock();
        // 获取workers的长度
        // mainlock.unlock();
    }
}
```

## **1.1 线程池属性统计**

getPoolSize()：线程池的工作线程数量
getActiveCount()：线程池正在执行任务的工作线程数量
getCompletedTaskCount()：已完成的任务数量
getTaskCount()：获取当前未执行任务 + 正在执行任务数量，即总任务数
getLargestPoolSize()：获取线程池工作线程数量峰值

以上的属性统计记账，都通过mainLock进行实现，简化了统计的难度

## **1.2 中断风暴**

在线程添加/线程退出等情况，都会对workSet进行并发操作，使用锁可以保证非并发安全容器的线程安全。当然，更多的原因是因为：

    “其中一个原因是它序列化了interruptIdleworkers操作，从而避免了不必要的中断风暴”

具体的场景如下：

    shutdown() 
    -> 
    interruptIdleWorkers(false) 
    -> 
    其中Worker被中断 
    -> 
    getTask()中感知到线程池的SHUTDOWN/STOP状态 
    -> 
    退出runWorker()方法 
    -> 
    processWorkerExit()方法 
    -> 
    tryTerminate()方法 
    -> 
    interruptIdleWorkers(ONLY_ONE)

在最后一步中，如果interruptIdleWorker没有互斥，那么**将会直接进入方法中对线程再进行一次中断**

    注意：此时最外层的shutdown()可能仍在执行interruptIdleWorkers()方法，这就产生了多次预料之外的中断

# **2. 关闭**

关闭方法分为两种有两种：
- 优雅关闭：shutdown() RUNNING -> SHUTDOWN
- 暴力关闭：shutdownNow() RUNNING/SHUTDOWN -> STOP

两种关闭方式的区别，在于遍历workerSet时，对是否应该中断的判断不同。对应的，线程池处于SHUTDOWN下，正在执行任务的线程不应该被中断；STOP下的线程都应该确保被中断

## **2.1 processWorkerExit(Worker w, boolean completedAbruptly)**

该方法用于记录完成任务数，并将自身从线程集合中移除

该方法具有传递性，具体由一个线程的中断并退出，调用tryTerminate方法进行传递，而tryTerminated()方法只会中断一个线程

```java
private void processWorkerExit(Worker w, boolean completedAbruptly) {
    // completedAbruptly为true，表示线程在执行任务中出错导致线程完成生命周期
    if (completedAbruptly)
        decrementWorkerCount();

    final ReentrantLock mainLock = this.lock;
    mainLock.lock();
    try {
        completedCount += w.completedCount;
        workers.remove(w);
    } finally {
        mainLock.unlock();
    }

    // 如果线程池正在关闭，此处调用会传递中断的信号到下一个空闲的线程，使其退出，在退出时调用该方法
    tryTerminate();

    int c = ctl.get();
    if (runStateLess(c, STOP)) {
        if (!completedAbruptly) {
            int min = allowCorePoolTimeout ? 0 : corePoolSize;
            if (min == 0 && !workQueue.isEmpty())
                min = 1;
            if (workCountOf(c) > min)
                return;
        }
        addWorker(null, false);
    }
}
```

## **2.2 优雅关闭**

pool.shutdown() 
-> 优雅关闭的入口
interruptIdleWorkers(false)
-> 中断所有的空闲线程
runWorker
-> 退出runWorker，执行finally的方法
processWorkerExit()
->
tryTerminate()
-> 等待mainlock释放
外部调用shutdown()方，调用mainlock.unlock()释放锁
-> 如果工作线程已经为0，则直接终止线程池
-> 如果线程数不为0，则打断一个线程，使其退出的时候再调用tryTerminated()进行传递

线程不允许被中断的情况有：
1. 刚创建，但还未执行runWorker方法
2. 正在执行任务中，无法被正常的shutdown关闭

```java
final void tryTerminated() {
    for (;;) {
        int c = ctl.get();
        if (isRunning(c) || runStateAtLeast(c, TIDYING) || (runStateOf(c) == SHUTDOWN && !workQueue.isEmpty())
            // 1. 当前线程池处于运行，无需操作
            // 2. 线程池处于TERMINATED，已经终止
            // 3. 线程池处于SHUTDOWN，并且队列不为空，不能终止
            return;
        
        if (workCountOf(c) != 0) {
            // 打断没有工作的线程，只打断一个线程
            interruptIdleWorker(ONLY_ONE);
            return;
        }

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // CAS设置线程池的状态为TIDYING
            if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                try {
                    // 终止线程池，默认空操作
                    terminated();
                } finally {
                    ctl.set(ctlOf(TERMINATED, 0));
                    terminated.signAll();
                }
                return;
            }
        } finally {
            mainLock.unLock();
        }
    }
}
```

```java
public void shutdown() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        // 检查是否有关闭线程池的权限，并确保允许调用者中断每一个线程
        checkShutdownAccess();
        // 将当前线程池的状态设置为SHUTDOWN
        advanceRunState(SHUTDOWN);
        // 中断Worker线程
        interruptIdleWorker(); // interruptIdleWorker(false);
        // 为ScheduledThreadPoolExecutor调用钩子函数
        onShutdown();
    } finally {
        mainLock.unLock();
    }
    tryTerminate();
}
```

## **2.3 直接关闭**

pool.shutdownNow()
->
interruptWorkers()
-> 中断所有的Worker线程
worker.interruptIfStarted()
-> 设置中断
runWorkers()
-> 阻塞过程被中断，直接退出方法
processWorkerExit()
->
tryTerminate()
-> 如果工作线程已经为0，则直接终止线程池
-> 如果线程数不为0，则打断一个线程，使其退出的时候再调用tryTerminated()进行传递

```java
public void shutdownNow() {
    List<Runnable> tasks;
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        checkShutdownAccess();
        // 设置线程池状态为STOP
        advanceRunState(STOP);
        // 中断所有的Worker线程
        interruptWorkers(); 
        // 将任务队列中的任务移动到tasks中
        tasks = drainQueue();
    } finally {
        mainLock.unLock();
    }
    tryTerminate();
    // 返回提交成功，但为完成的任务
    return tasks;
}
```

# **3. 拒绝策略**

当队列已满，且当前工作线程数大于maximumPoolSize时，线程拒绝添加新任务，一共有以下4种拒绝策略

| 策略 | 描述 |
| -----| ----- |
| AbortPolicy | 丢弃任务，会向上层抛出RejectExecutionException异常 |
| CallerRunsPolicy | 调用者线程自行运行任务 |
| DiscardOldestPolicy | 替换最旧的任务，即队头出队，新任务从队尾投入 |
| DiscardPolicy | 空操作，丢弃任务，不会抛出异常 |

**defaultHandler缺省抛弃策是ThreadPoolExecutor.AbortPolicy()**

# 参考
- [ThreadPoolExecutor 优雅关闭线程池的原理.md](https://www.cnblogs.com/xiaoheike/p/11185453.html)
- [Java线程池ThreadPoolExecutor使用和分析(三) - 终止线程池原理](https://www.cnblogs.com/trust-freedom/p/6693601.html)

# 重点参考
- [Java线程池ThreadPoolExecutor使用和分析](https://www.cnblogs.com/trust-freedom/p/6681948.html)