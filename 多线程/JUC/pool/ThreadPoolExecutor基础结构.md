# ThreadPoolExecutor（一） - 基础设施

ThreadPoolExecutor是整个线程池实现的核心，了解线程池，应该先了解其基本的数据结构

首先我们应该知道线程池是一个优秀的**生产-消费模式**典例。其对线程的创建/回收管理（消费速率管理），对线程与任务的模型构建（生产者-消费者模型），对任务投递的管理（生产速率管理），都是我们学习时的第一切入点

其次，线程池作为一个模块，肯定是需要有对自身进行管理的行为，这是第二切入点

本篇文章，将会通过生产-消费模型以及线程池管理的角度，对ThreadPoolExecutor的基础设施进行介绍：
- ThreadPoolExecutor.ctl：线程池状态记录/消费者记录
- ThreadPoolExecutor的关键参数：控制生产速率和消费速率的关键
- ThreadPoolExecutor.Worker：消费者实体

# **1. ctl**

通过单独一个原子整型ctl，来维护**线程池的状态**与**工作线程个数**两种记录

内部提供了一系列对ctl高3位/低29位操作的方法，可以方便地获取两种记录的对应情况

自己通过位运算算出了各个状态的具体位情况，感兴趣可以通过参考文章了解下
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
    // 0000 0000....（直到32位）
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    // 0010 0000....（直到32位）
    private static final int STOP = 1 << COUNT_BITS;
    // 0100 0000....（直到32位）
    private static final int TIDYING = 2 << COUNT_BITS;
    // 0110 0000....（直到32位）
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

## **1.1 状态介绍**

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

## **1.2 状态更迭**

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

# **2. 其他的基本结构**

ThreadPoolExecutor需要实现Executor、AbstractExecutorService等接口/父类为实现行为，包括：
Executor.execute(Runnable)：生产任务、消费速率修改
ExecutorService.shutdown()：关闭线程池
ExecutorService.shutdownNow()：立即关闭线程池
ExecutorService.isShutdown()：获取线程池状态
ExecutorService.isTerminated()：获取线程池状态
ExecutorService.awaitTermination(long, TimeUnit)

先简单描述一下上述的效果，它们定义了生产者与消费者：
- 生产者：用户线程的投递，生产速率控制（**拒绝策略**）
- 消费者：工作线程的定义初始化（**Worker**），消费速率控制（**控制消费者个数**）

除了生产者和消费者，模型还需要**任务队列**，供生产投递与阻塞消费

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

## **2.1 关键参数**

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

## **2.2 构造函数**

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

## **2.3 Worker**

线程池中的每一个线程被封装为一个Worker对象，ThreadPool维护的其实就是一组Worker对象

```java
private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
    // worker对应的线程，工厂出错时候为null
    final Thread thread;

    // 初始化时执行的第一个任务，可能为null
    Runnable firstTask;

    // 线程任务计数器
    volatile long completedTasks;

    Worker(Runnable firstTask) {
        setState(-1); // -1代表worker还未启动，与独占锁不同
        this.firstTask = firstTask;
        this.thread = getThreadFactory().newThread(this);
    }

    public void run() {
        // runWorker方法会在一开始unlock一次，该操作相当于启动Worker
        runWorker(this);
    }

    protected boolean isHeldExclusively() {
        return getState() != 0;
    }

    protected boolean tryAcquire(int unused) {
        if (compareAndSetState(0, 1)) {
            setExclusiveOwnerThread(Thread.currentThread());
            return true;
        }
        // 在此处没有判断Thread.currentThread()是否是占用的线程，也表明了Worker是不可重入的
        return false;
    }

    protected boolean tryRelease(int unused) {
        setExclusiveOwnerThread(null);
        setState(0);
        return true;
    }

    public void lock() {acquire(1);}
    public boolean tryLock() {return tryAcquire(1);}
    public void unlock() {release(1);}
    public boolean isLocked() {return isHeldExclusively();}

    // shutdownNow() -> interruptWorkers() -> interruptIfStarted()
    void interruptIfStarted() {
        Thread t;
        // 无论是否正在执行任务，都将被中断
        if (getState() >= 0 && (t == thread) != null && !t.isInterrupted()) {
            try {
                t.interrput();
            } catch(SecurityException ignore)
        }
    }
}
```

### **2.3.1 独占**

    实现独占特性去反应线程现在的执行状态，有锁标识worker空闲态与忙碌态，从而方便控制worker的销毁操作

Worker直接继承了AQS，重写模板方法来实现**独占功能**

1. lock方法一旦获取独占锁，则表示当前线程正在执行任务
2. 如果正在执行任务，则不应该中断线程（除了shutdownNow()）
3. 如果该线程现在不是独占锁状态，即idle状态，说明正在等待任务，此时可以中断线程（shutdown()）
4. 线程池在使用shutdown()方法或tryTerminate()时，会调用interruptIdleWorkers方法来中断空闲的线程，里面又会使用tryLock方法来判断线程是否处于空闲状态，是空闲状态的话可以安全回收

### **2.3.2 不可重入**

    实现不可重入特性，防止在任务内运行修改线程池行为的接口

没有使用Reentrantlock来实现独占锁的功能，原因在于Worker**是不可重入的**，而ReentrantLock是可重入的

之所以设计为不可重入，是如果使用ReentrantLock，它因为可重入，如果在任务中调用了setCorePoolSize这类线程池的控制方法，在interruptIdleWorkers中会中断当前正在运行的线程，即执行任务的线程被认为是空闲的线程

以下是关于Worker可重入的伪代码，可能会涉及到后续文章讲解的内容：
```java
public class ThreadPoolExecutor {
    private void setCorePoolSize() {
        // ...
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
    }

    public void interruptIdleWorkers(boolean onlyOne) {
        // ...
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                // w.tryLock()如果是可重入的，且当前线程就是独占w的线程，则会对当前正在进行的任务进行中断
                if (!t.isInterrupted() && w.tryLock()) {
                    // 可重入的话，当前运行任务的线程会被自身中断
                }
                // ...
            }
        } finally {
            // ...
        }
    }
}

// 在interruptIdleWorkers，会导致当前尝试中断线程就是独占w的线程的情况
public class Demo {
    public static void main(String[] args) {
        ExecutorService exec = Executors.newSingleThreadPool();
        // 在提交的任务里面修改线程池的属性
        exec.submit(() -> {
            exec.setCorePoolSize(10);
        });
    }
}
```

# 参考
- [线程池的五种状态](https://www.cnblogs.com/jxxblogs/p/11751944.html)
- [Java 线程池 ThreadPoolExecutor 中的位运算操作](https://blog.csdn.net/cnm10050/article/details/105835302)

# 重点参考
- [Java线程池ThreadPoolExecutor使用和分析](https://www.cnblogs.com/trust-freedom/p/6681948.html)