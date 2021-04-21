# ThreadPoolExecutor

该类是整个线程池实现的核心，Executors通过固定参数填充ThreadPoolExecutor来创建4种线程池技术，开发者也可以直接通过ThreadPoolExecutor自定义线程池

## **1.池状态**

```java
public class ThreadPoolExecutor extends AbstractExecutorService {
    // 高3位表示线程池的运行状态，低29位表示线程有效数量
    // 初始状态位：线程池Running，线程个数0
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    // 高3位表示线程池的运行状态，低29位表示线程有效数量
    private static final int COUNT_BITS = Integer.SIZE - 3;

    // 线程的最大数量
    // 0001 1111 1111 1111 1111 1111 1111 1111
    private static final int CAPACITY = (1 << COUNT_BITS) -1;

    // 运行状态被存储在高阶比特位上
    // 1010 0000....（直到32位）
    private static final int RUNNING = -1 << COUNT_BITS;
    // 0000 0000....
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    // 0010 0000....
    private static final int STOP = 1 << COUNT_BITS;
    // 0100 0000....
    private static final int TIDYING = 2 << COUNT_BITS;
    // 0110 0000....
    private static final int TERMINATED = 3 << COUNT_BITS;

    // 获取线程池状态
    private static final int runStateOf(int c) {
        // 假设runstate是stop
        // 0010 0000 0000 0000 0000 0000 0000 0000
        // 1110 0000 0000 0000 0000 0000 0000 0000
        // 0010 0000 0000 0000 0000 0000 0000 0000
        return c & ~CAPACITY;
    }

    // 获取线程池有效线程的数量
    private static final int workerCountOf(int c) { 
        return c & CAPACITY
    }

    // 获取32位int类型的数值
    // rs表示线程状态，wc表示线程数量，相当于将rs的前三位和wc的后29位拼接在一起
    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }
}
```

### **1.1 状态介绍**

与线程的状态相似，线程池也有自己的状态，被存储在一个整型的高3位上

- RUNNING

    一旦被创建，初始化状态是RUNNING，线程池中的任务数为0
    该状态接受新的任务，处理等待队列中的任务

- SHUTDOWN

    不接受新的任务提交，会继续处理等待队列中的任务

- STOP

    不接受新的任务提交，不再处理等待队列中的任务，中断正在执行任务的线程

- TIDYING

    所有的任务都被销毁了，工作线程为0，线程池的状态在转换为TIDYING时，会执行钩子方法terminated()，该方法在父类是空的，用户可以通过重载terminiated来实现自定义处理TIDYING转化处理

- TERMINATED

    线程池处于TIDYING时，执行完terminated后，就会由TIDYING转换为TERMINATED

### **1.2 状态更迭**

1. RUNNING -> SHUTDOWN

    调用pool.shutdown()方法，线程池会由RUNNING转变为SHUTDOWN，此时仍然会处理任务队列

2. (RUNNING or SHUTDOWN) -> STOP

    调用pool.shutdownNow()方法，池在RUNNING或SHUTDOWN状态下，会转变为STOP。此时不会处理任务队列的任务，并中断正在执行任务的线程，返回未完成的任务

3. SHUTDOWN -> TYDING

    任务队列的任务处理完毕，并且池空（工作线程数量为0）

4. STOP -> TYDING

    池空

5. TYDING -> TERMINATED

    执行完terminated()钩子方法后，池就会由TYDING更迭为TERMINATED状态

## **2. 基本结构**

ThreadPoolExecutor用一个int类型来表示当前线程池的**运行状态**和**线程有效数量**

需要重写:

Executor.execute(Runnable)
ExecutorService.shutdown()
ExecutorService.shutdownNow()
ExecutorService.isShutdown()
ExecutorService.isTerminated()
ExecutorService.awaitTermination(long, TimeUnit)

并另外提供了：
1. 线程池管理（忙时拒绝策略/线程创建（线程工厂））
2. 工作线程
3. 任务队列

线程池类成员如下：
```java
public class ThreadPoolExecutor extends AbstractExecutorService {
    // 线程池独占锁，在关闭线程池/新增工作线程时使用
    private final ReentrantLock mainLock = new ReentrantLock();
    private final Condition termination = mainLock.getCondition();

    // 记录了线程池整个生命周期中曾经出现的最大线程个数，即largetestPoolSize <= maximumPoolSize
    private int largestPoolSize;
    private long completedTaskCount;
    // 
    private volatile long keepAliveTime;
    private volatile boolean allowCoreThreadTimeout;
    
    // 核心线程数
    private volatile int corePoolSize;
    // 线程池允许的最大线程数，线程池中的当前线程数目不会超过该值，如果当前线程小于maximumPoolSize且工作队列已满，才会创建新的线程来执行任务
    private volatile int maximumPoolSize;

    private volatile ThreadFactory threadFactory;

     // 当队列满了时，新提交任务的拒绝策略，默认是放弃
    private final BlockingQueue<Runnable> workQueue;
    private volatile RejectExecutionHandler handler;
    
    // 所有的工作线程，只有当独占mainLock时，才可进行访问
    private final HashSet<Worker> workers = new HashSet<>();

    // 1. 线程池管理之运行时允许关闭线程池的权限线程
    private static final RuntimePermission shutdownPerm = new RuntimePermission("modifyThread");

    private final AccessControlContext acc;

    private static final boolean ONLY_ONE = true;

    // 2. 工作线程集合
    HashSet<Worker> workers = new HashSet<>();
    // 2. 工作线程之控制创建工作线程的详情
    private volatile ThreadFactory threadFactory;

    // 3. 任务队列
    BlockingQueue<Runnable> workQueue;
    // 3. 任务队列之任务的忙时拒绝策略（默认是放弃）
    private static final RejectExecutionHandler defaultHandler = new AbortPolicy();
}
```

### **2.1 关键参数**

**corePoolSize和maxiumPoolSize**

他们的值可以控制池的线程的数目：
1. 当前线程数 < corePoolSize，则新建线程执行
2. 当前线程数 >= corePoolSize，则加入BlockingQueue任务队列
3. 若BlockingQueue队列满了，且当前线程数 < maxiumPoolSize, 则新建线程执行任务；反之，则执行拒绝策略

**threadFactory**

用来创建线程的地方，Executors默认的工厂会将守护线程属性设置为false，线程优先级为NORMAL等级；Executors还提供了权限工厂，可以设置线程的访问权限

**handler**

在任务队列满时，且线程池线程数达到最大值，所执行的拒绝策略，默认为AbortPolicy，即执行丢弃该任务
1. AbortPolicy，丢弃策略，会抛出RejectException异常
2. CallerRunsPolicy，在调用者的线程自己执行策略，除非线程池已经被关闭了
3. DiscardOrdestPolicy，替换最旧的任务，将队列头的任务丢弃，再调用execute方法，将当前任务加入到队列中
4. DiscardPolicy，丢弃策略，不会抛出异常

**workQueue**

任务队列，用来存放等待执行的任务，根据策略的不同，Executors提供了四种不同的阻塞队列
1. 不含容量的同步队列SynchronousQueue，它不保存任务，一直创建新的线程进行执行，直到达到maxiumPoolSize，执行拒绝策略
2. 无界队列LinkedBlockingQueue，可以一直添加任务，直到程序OOM，执行任务顺序为FIFO
3. 有界队列ArrayBlockingQueue，执行任务顺序为FIFO
4. 优先级队列PriorityBlockingQueue，执行任务顺序按照优先级大小来执行任务

**keepAliveTime**

存活时间，指的是当池的线程数量超过corePoolSize时，超过的部分最多存活的时间

**allowCoreThreadTimeOut**

true时，超过core数目的线程在keepAliveTime后将被释放；false时，即使线程空闲也不释放

可以参照Executors工具类，默认都使用忙时拒绝策略，可传入线程工厂

### **2.2 构造函数**

```java
public static ExecutorService newCachedThreadPool() {
    return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
}

// 传入自选的线程工厂版本
public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
    return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>, threadFactory);
}
```

```java
public ThreadPoolExecutor(int corePoolSize, int maxiumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectExecutionHandler handler) {
    if (corePoolSize < 0 || maxiumPoolSize <= 0 || maxiumPoolSize < corePoolSize || keepAliveTime < 0)
        throw new IllegalArgumentException();

    if (workQueue == null || threadFactory == null || handler == null)
        throw new NullPointException();
    // acc
    this.acc = System.getSecurityManager() == null ? null : AccessController.getContext();
    this.corePoolSize = corePoolSize;
    this.maxiumPoolSize = maxiumPoolSize;
    this.workQueue = workQueue;
    this.keepAliveTime = keepAliveTime;

    // 默认使用Executors.defaultThreadFactory(); 
    this.threadFactory = threadFactory;

    // 默认使用静态defaultHandler = new AbortPolicy();
    this.handler = handler;
}
```

### **2.3 Worker**

## **3. 关键行为**

### **3.1 线程池管理**

#### **3.1.1 excute方法**

重写了最上层接口Executor.excute方法，道格李在注释中写出了执行的三个步骤：

1. 如果正在运行的线程少于corePoolSize，则以给定的command作为线程的第一个任务，新起这个线程。并在addWorker的调用时，原子检查runstate和workcount，防止在不应该添加线程时添加线程
2. 如果一个任务可以成功排队，我们仍然需要重新检查是否应该添加一个新线程。可能上次检查后，线程已经死了，或者在进入这个方法之后线程池被关闭了。因此，重新检查状态，如果有必要，在停止回滚时回滚排队；如果没有必要，则新起一个新线程
3. 如果任务不能成功排队，则尝试添加一个新的线程。如果失败了，则我们可以直到线程池已经关闭或者饱和了，默认的策略会拒绝这个任务

```java
public void execute(Runnable command) {
    if (command == null)
        throw new NullPointException();

    int c = ctl.get();
    // 判断是否当前线程数小于核心线程数
    if (workerCountOf(c) < corePoolSize) {
        // 小于核心线程数，新起线程，并执行该任务
        if (addWorker(command, true))
            return;
        // 添加失败，重新获取当前的线程池状态和线程数量
        c = ctl.get();
    }

    // 当前线程数大于核心线程数，且添加队列成功
    if (isRunning(c) && workQueue.offer(command)) {
        int recheck = ctl.get();
        if (!isRunning(recheck) && remove(command))
            // 回滚提交到队列的操作，并拒绝该任务
            reject(command);
        // 重新检查下当前线程是否都没了，没的话就再起一个新的
        else if (workCountOf(recheck) == 0)
            addWorker(null, false);
    }
    // 当前线程数大于核心线程数，且队列已满
    else if (!addWorker(command, false))
        // 添加线程失败，当前线程数已达到maxium
        reject(command);
}
```

### **3.1.2 addWorker方法**

使用该方法来新增工作线程，方法入口提供了isFirstTask参数和core参数，可决定新增的线程是否有第一个执行的command 和 是否新增后加入到core中

按照上述的情况，总结下新增工作线程的情况：
1. 当前线程数 < core
2. 当前线程数 >= core，任务队列未满，且在重新检测后发现当前没有工作线程数
3. 当前线程数 >= core，任务队列已满，且当前线程数 < maxium，则新起线程

```java
public void addWorker(Runnable firstTask, boolean core) {
    // retry用于直接跳出/跳过多层循环
    retry:
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
        w = new Worker(firstTask);
        final Thread t = w.thread;
        if (t != null) {
            // 线程池新增线程使用独占锁，确保新增过程的线程安全性
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 在持有独占锁时，重新检查。防止：1. 锁获取之前线程池关闭 2. ThreadFactory创建的线程没有alive
                int rs = workStateOf(ctl.get());

                if (rs < SHUTDOWN || (rs == SHUTDOWN && firstTask == null)) {
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
```

如果创建了线程，但是线程并没有成功开启，则需要将该线程从池中进行移除

```java
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

### **3.1.3 shutDown和shutDownNow**

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

# 参考
- [线程池的五种状态](https://www.cnblogs.com/jxxblogs/p/11751944.html)
- [Java 线程池 ThreadPoolExecutor 中的位运算操作](https://blog.csdn.net/cnm10050/article/details/105835302)
- [线程池看懂了也很简单](https://blog.csdn.net/bieber007/article/details/108487746)
- [Java线程池ThreadPoolExecutor使用和分析(三) - 终止线程池原理](https://www.cnblogs.com/trust-freedom/p/6693601.html)
- [深入理解Java线程池：ThreadPoolExecutor](https://www.cnblogs.com/liuzhihu/p/8177371.html)
- [ThreadPoolExecutor 优雅关闭线程池的原理.md](https://www.cnblogs.com/xiaoheike/p/11185453.html)