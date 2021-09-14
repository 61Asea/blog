# **ThreadPoolExecutor（二）：生产消费**

上篇文章从生产者-消费者模型切入，描述了线程池的基础设施

> 上一篇文章：[ThreadPoolExecutor(一) - 基础设施](http://8.135.101.145/archives/threadpoolexecutor1)

本篇文章将从ThreadPoolExecutor对execute()、shutdown()和shutdownNow()的实现切入，描述线程池是如何将这个模型玩起来的

我们先简单地分析下线程池应该会做的事情：

1. 调节生产端（execute、addWorker）

- 在workQueue满时，执行拒绝策略，最低限度地保证了队列在固定长度下，过于繁忙的问题

2. 调节消费端（runWorkers、getTask）

- 当前线程大于核心线程数目，则会根据线程从队列取任务的超时情况，调节workSet的长度

3. 实时根据线程池的状态，对工作线程做相对应的调整(processWorkerExit、shutdown、shutdownNow以及其他操作时会实时获取ctl的状态)

# **1 excute(Runnable command) （提交）**

重写了最上层接口Executor.excute方法，道格李在注释中写出了执行的三个步骤：

- 如果正在运行的线程少于corePoolSize，则以给定的command作为线程的第一个任务，新起这个线程。并在addWorker的调用时，原子检查runstate和workcount，防止在不应该添加线程时添加线程
- 如果一个任务可以成功排队，我们仍然需要重新检查是否应该添加一个新线程。可能上次检查后，线程已经死了，或者在进入这个方法之后线程池被关闭了。因此，重新检查状态，如果有必要，在停止回滚时回滚排队；如果没有必要，则新起一个新线程
- 如果任务不能成功排队，则尝试添加一个新的线程。如果失败了，则我们可以直到线程池已经关闭或者饱和了，默认的策略会拒绝这个任务

![execute()](https://images2015.cnblogs.com/blog/677054/201704/677054-20170408210905472-1864459025.png)

```java
public void execute(Runnable command) {
    if (command == null)
        throw new NullPointException();

    int c = ctl.get();
    // 1. 当前线程数小于核心数
    if (workCountOf(c) < corePoolSize) {
        if (addWorker(command, true))
            return;
        // 一直CAS没有成功，且工作线程已经添加到 >= corePoolSize
        c = ctl.get();
    }

    if (isRunning(c) && workQueue.offer(command)) {
        // 重新检测的原因：判断刚加入阻塞队列的任务是否能被执行
        int recheck = ctl.get();
        // 重新检测，如果线程池被关闭，则将提交的任务删除
        if (! isRunning(recheck) && workQueue.remove(command))
            reject(command);
        // 重新检测，如果其他线程add的worker死亡了，则重新再起一个
        else if (workCountOf(recheck) == 0)
            addWorker(null, false);

        // 2. 当前线程数大于等于核心数
    } 
    // 3. 队列已满，且当前线程还小于maxium，则用新起一个线程去执行
    else if (!addWorker(command, false))
        // 添加线程失败，已达到maxium，执行拒绝策略
        reject(command);
}
```

# **2 addWorker(Runnable command, boolean core) （新增工作线程）**

方法入口提供了isFirstTask参数和core参数，可决定新增的线程是否有第一个执行的command 和 是否新增后加入到core中

按照上述的情况，总结下在execute方法新增工作线程的情况：
1. 当前线程数 < core，新增核心线程
2. 当前线程数 >= core，任务队列未满，且在重新检测后发现当前没有工作线程数，新增非核心线程
3. 当前线程数 >= core，任务队列已满，且当前线程数 < maxium，则新起线程，新增非核心线程

![addWorker](https://images2015.cnblogs.com/blog/677054/201704/677054-20170408211358816-1277836615.png)

addWorker方法不止在运行状态可以新增工作线程，在SHUTDOWN并符合某些条件下也可以新增工作线程

在创建Worker时，独占mainlock，防止其他改变线程池行为的并发操作

```java
// 通过两个for(;;)和CAS结合原子类，可以认为是对某个状态去锁的经典案例了
public void addWorker(Runnable firstTask, boolean core) {
    // retry用于直接跳出/跳过多层循环
    retry:
    // 外层循环，负责判断线程池状态
    for (;;) {
        int c = ctl.get();
        int rs = runStateOf(c);

        // 在线程池不在运行状态时，检查队列内是否空
        // rs >= SHUTDOWN && (rs != SHUTDOWN || firstTask != null || workQueue.isEmpty())

        // 1. rs > SHUTDOWN
        // 线程池处于STOP，TIDYING，TERMINATED时，添加线程失败，不接收新任务（SHUTDOWN处于一个中间过程，该过程线程池并没有完全关闭）
        // 2. rs == SHUTDOWN && firstTask != null
        // 线程池处于SHUTDOWN，且worker有首个任务，添加线程失败（线程池要关闭了，不接受新任务）
        // 3. rs == SHUTDOWN && firstTask == null && workQueue.isEmpty()
        // 线程池处于SHUTDOWN，worker没有首个任务，且队列是空的，添加线程失败（线程池要关闭了，队列也全部执行完了，不再新起线程；若队列还有任务，则考虑下要不要新起个线程去一起干活）
        if (rs >= SHUTDOWN && !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty()))
            return false;

        for (;;) {
            int wc = workCountOf(c);
            if (wc >= CAPACITY || wc >= (core ? corePoolSize : maxiumPoolSize))
                // 1. 线程数量达到理论上的最大值
                // 2. 这个线程要新增为核心线程，但是当前线程数已经到达了核心数量
                // 3. 这个线程不需要变为核心线程，但是已经达到了设定的maxium
                return false;
            if (compareAndIncrementWorkCount(c))
                // CAS新增线程成功，直接跳出最外层for
                break retry;
            c = ctl.get(); // re-read
            if (runStateOf(c) != rs)
                // 线程池状态发生了改变，外层重新循环来获取最新的线程池状态
                continue retry;
            // else : CAS失败了，在内层继续循环
        }
    }

    // 上面的for循环出来了，说明需要新增线程，且线程数+1的CAS操作已经成功，往下执行创建该新增的线程操作
    boolean workerStarted = false;
    boolean workerAdded = false;
    Worker w = null;
    try {
        // w的aqs状态为-1
        w = new Worker(firstTask);
        final Thread t = w.thread;
        if (t != null) {
            // 独占线程池自己的锁，竞态条件是：关闭/获取/新增等会影响线程池的行为
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 在持有独占锁时，重新检查
                int rs = workStateOf(ctl.get());

                // 1. 重新检测锁获取之前，线程池是否关闭 
                if (rs < SHUTDOWN || (rs == SHUTDOWN && firstTask == null)) {
                    // 2. 重新检测，ThreadFactory创建的线程没有alive
                    if (t.isAlive())
                        throw new IllegalThreadStateException();
                    workers.add(w);
                    int s = workers.size();
                    if (s > largestPoolSize)
                        largetPoolSise = s;
                    workerAdded = true;
                }
            } finally {
                // 尽早释放锁
                mainLock.unlock();
            }
            if (workerAdded) {
                // 启动后，调用Worker的run() -> pool.runWorkers()
                t.start();
                workerStarted = true;
            }
        }
    } finally {
        if (!workerStarted)
            addWorkerFailed(w);
    }
    return workerStarted;
}

public void addWorkerFailed(Worker w) {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        if (w != null)
            // 将其从工作集合中删除
            workers.remove(w);
        // ctl关于线程数的记录-1
        decrementWorkerCount();
        // 重新检测线程池是否终止，以防该worker阻止终止
        tryTerminate();
    } finally {
        mainLock.unlock();
    }
}
```

     总结：
        1. 线程池处于运行，如果core，且当前线程数 < corePoolSize；如果!core，且当前线程 < maxium，这两种情况可以新起线程
        2. 线程池处于SHUTDOWN，firstTask == null(不接收新任务)，workQueue还有任务，则可以新起一个线程
        3. 线程池的其他状态都不可以新起线程

如果创建了线程，但是线程并没有成功开启，则需要将该线程从池中进行移除

# **3 Worker轮询/消费任务 （执行）**

在2.3中，Worker的run方法实现，会调用线程池runWorkers(Worker w)方法，以启动线程池中的线程

## **3.1 runWorker(Worker w)**

    当线程池处于STOP（shutdownNow）时，线程应该被中断；当线程池处于RUNNING或SHUTDOWN时，线程不应该中断（只中断空闲的线程）

```java
final void runWorker(Worker w) {
    Thread wt = Thread.currentThread();
    Runnable task = w .firstTask;
    w.firstTask = null;
    w.unlock(); // 初始化后为-1，在这里变为0，即空闲状态
    boolean completedAbruptly = true; // 出错标记
    try {
        // 取任务，会在getTask()方法中阻塞住
        while (task != null || (task = getTask() != null)) {
            // 线程加锁，处于执行任务状态
            w.lock();

            // runAtLeast(ctl.get(), STOP): STOP，TIDYING，TERMINATED
            // Thread.interrupted() && runAtLeast(ctl.get(), STOP): 上一个判断认为线程池未STOP，此时获取并清除中断标记，如果发现被中断了，很大可能是并发调用了shutdownNow，，再次双重检测当前线程池状态
            // !wt.isInterrupted：这个条件用来组合前面的判断，即处于STOP时，线程没被中断，则应该中断

            // 这里为了确保两件事：当线程池处于STOP（shutdownNow）时，线程应该被中断；当线程池处于RUNNING或SHUTDOWN时，线程不应该中断（只中断空闲的线程）
            // 1. runAtLeast(ctl.get(), STOP) && !wt.isInterrupted()：第一次检测就是STOP了，则确保线程被中断
            // 2. !runAtLeast(ctl.get(), STOP) || !Thread.interrupted()：处于RUNNING状态，且线程没有被中断
            // 3. !runAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runAtLeast(ctl.get(), STOP)) && !wt.isInterrupted()：在Thread.interrupted发现被中断，可能并发调用了shutdownNow，再次检测，并且确保线程中断
            if ((runAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runAtLeast(ctl.get(), STOP)) && !wt.isInterrupted())
                wt.interrupt();
            try {
                beforeExecute(wt, task);
                Throwable thrown = null;
                try {
                    // 真正的执行任务
                    task.run();
                } catch (RuntimeException x) {
                    thrown = x; throw x;
                } catch (Error x) {
                    thrown = x; throw x;
                } catch (Throwable x) {
                    thrown = x; throw new Error(x);
                } finally {
                    afterExecute(task, thrown);
                }
            } finally {
                task = null;
                w.completedTask++;
                // 线程解锁，处于空闲状态
                w.unlock();
            }
            completedAbruptly = false;
        }
    } finally {
        // 线程退出，条件为getTask()返回null
        processWorkExit(w, completedAbruptly);
    }
}
```

## **3.2 getTask()**

![getTask](https://images2015.cnblogs.com/blog/677054/201704/677054-20170408211632300-254189763.png)

```java
private Runnable getTask() {
    // 记录上次获取任务是否超时
    boolean timedOut= false;

    for (;;) {
        int c = ctl.get();
        int rs = runStateOf(c);

        // 线程池处于关闭状态，且任务队列空，则减少ctl线程数并返回
        if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
            decrementWorkerCount();
            return null;
        }

        int wc = workerCountOf(c);

        // allowCoreThreadTimeOut默认为false
        // 当前线程超过核心线程数时，需要定时从workQueue中获取
        // 对于超过核心线程数量的线程，需要进行超时控制
        boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

        // wc > maxiumPoolSize: 调用了setMaxiumPoolSize方法，需要调整线程的数量
        // timed && timeOut: 为真表示上次操作已经超时（超时，即总是取不到，可以认为消费速度大于生产速度）且当前wc大于corePoolSize
        // wc > 1: 大于1则表示线程池的有效线程不止有当前的线程
        // workQueue.isEmpty(): 消费速度快
        if ((wc > maxiumPoolSize || (timed && timeOut)) && (wc > 1 || workQueue.isEmpty())) {
            if (compareAndDecrementWorkerCount(c))
                return null;
            continue;
        }

        try {
            Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();

            if (r != null) {
                return r;
            }
            // r 只有可能在poll中返回null，也意味着超时了
            timeout = true;
        } catch (InterruptedException retry) {
            // workQueue使用显式锁，并调用了ConditionObject.wait可中断阻塞方法，在被其他线程或本线程中断时，会抛出InterruptedException
            timeout = false;
        }
    }
}
```

getTask()是线程池控制核心线程数量的重要代码，关键点在于第二个if。通过是否对阻塞队列的poll的超时情况，来反映出当前环境下生产与消费的情况。当wc大于corePoolSize时，结合上次操作的超时情况与当前任务队列是否为空，可以看出消费者的是否有足够的消费速率

通过以上代码，在CAS设置了wc的数量后，该调用栈返回到`ThreadPoolExecutor#runWorker(Worker w)`方法中

```java
final void runWorker(Worker w) {
    // ...
    try {
        // ...
    } finally {
        processWorkerExit(w, completedAbruptly);
    }
    }
```

最终在`processWorkerExit`方法会将`w`线程关闭，可以认为这个消费端的调节由**任务驱动**：

> 执行任务 -> 唤醒等待线程并分配 -> 任务执行完毕 -> 检查线程从阻塞到被唤醒的时间是否超时 -> 超时代表消费速率快，可以关闭该线程 -> 调用线程池的关闭线程方法进行关闭

```java
private void processWorkerExit(Worker w, boolean completedAbruptly) {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        completedTaskCount += w.completedTasks;
        // 将线程从hashset中移除
        workers.remove(w);
    } finally {
        mainLock.unlock();
    }
}
```

# 参考
- [Java线程池ThreadPoolExecutor使用和分析(二) - execute原理](https://www.cnblogs.com/trust-freedom/p/6681948.html)
- [深入理解Java线程池：ThreadPoolExecutor](https://www.cnblogs.com/liuzhihu/p/8177371.html)

# 重点参考
- [Java线程池ThreadPoolExecutor使用和分析](https://www.cnblogs.com/trust-freedom/p/6681948.html)