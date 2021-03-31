# ReentrantReadWriteLock

ReentrantReadWriteLock是JUC包中读写锁接口（ReadWriteLock）的重要实现类，主要实现：读共享，写独占

对比单纯的互斥锁，在共享资源使用场景为**频繁读取及少量修改**的情况下可以较好的提高性能

问题：独占锁，读跟写互斥没毛病，**但是如果全是读，在同一时刻只能有一个线程去访问资源，效率低**

读写锁的优化效果：
1. 读的时候，多个线程可以一起访问资源，并且阻塞其他线程的写操作（获取写资源）
2. 写的时候，阻塞全部线程的读操作（获取读资源）

![读锁/写锁关系](https://upload-images.jianshu.io/upload_images/19073098-6b99bbfec18d7745.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

## **1. 读写锁的state**

state一般只用来表示一种锁，要么独占，要么共享，为了**能让state可以同时协同读、写线程**，state通过位数拆分，表示两种锁资源

    // int类型，32bits，高16位：读锁（共享），低16位：写锁（独占）
    public volatile int state;

    0000 0000 0000 0000 （高16位）| 0000 0000 0000 0001（低16位）

## **2. 基础类和接口**

ReentrantReadWriteLock没有像ReentrantLock一样实现Lock接口，而是在内部组合了ReadLock、WriteLock的成员变量，两者均实现了Lock接口

    共用一个sync(aqs) + readLock(封装了对sync共享模板方法的调用) + writeLock(封装了对sync独占模板方法的调用)

两种lock操作的都是同一个sync，即sync的状态需要做到支持读写两用，读操作是共享模式，写操作是独占模式，所以sync重写了四个模板方法

    读写锁构造之后，将两个Lock实现子类，暴露出来给外部使用

```java
public class ReentrantReadWriteLock implements ReadWriteLock {
    private final ReentrantReadWriteLock.ReaderLock readerLock;
    private final ReentrantReadWriteLock.WriteLock writerLock;

    final Sync sync;

    public ReentrantReadWriteLock() {
        this(false);
    }

    public ReentrantReadWriteLock(boolean fair) {
        // 可以看出，readerLock和writeLock的公平/非公平策略是一致的
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }

    static final class Sync extends AbstractQueuedSynchronizer {

    }

    public static class ReadLock implements Lock {
        private final Sync sync;

        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }
    }

    public static class WriteLock implements Lock {
        private final Sync sync;

        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }
    }

    // 暴露写锁
    public ReentrantReadWriteLock.WriteLock writeLock() {
        return writerLock;
    }

    // 暴露读锁
    public ReentrantReadWriteLock.ReadLock readLock() {
        return readerLock;
    }
}
```

### **2.1 ReadWriteLock接口**

读写锁接口，实现了该接口的子类，通过接口方法暴露出其读锁与写锁

```java
public interface ReadWriteLock {
    Lock writerLock();

    Lock readerLock();
}
```

### **2.2 Sync（extends AbstractQueuedSynchronizer）**

Sync包含了对资源的位数划分，计数管理以及常见的aqs模板方法

- tryAcquire：获取写锁
- tryAcquireShared：获取读锁
- tryRelease：释放写锁
- tryReleaseShared：释放读锁

```java
final class Sync extends AbstractQueuedSynchronizer {
    // 共享资源的位移数
    static final int SHARED_SHIFT = 16;
    // 0000 0000 0000 0001 0000 0000 0000 0000，Shared的起点？
    static final int SHARED_UNIT = (1 << SHARED_SHIFT);
    // 0000 0000 0000 0000 1111 1111 1111 1111，写锁的最大值
    static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;
    // 0000 0000 0000 0000 1111 1111 1111 1111，写锁掩码
    static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

    // 返回以计数表示的共享保留数
    static int sharedCount(int c) {
        return c >>> SHARED_SHIFT;
    }

    // 返回以计数表示的独享保留数
    static int exclusiveCount(int c) {
        return c & EXCLUSIVE_MASK;
    }

    // 每个线程的读资源计数器，使用ThreadLocal进行维护，并缓存在cachedHoldCounter中
    static final class HoldCounter {
        int count = 0;
        final long tid = getThreadId(Thread.currentThread());
    }

    // 每个线程维护自己的计数器，继承ThreadLocal
    static final class ThreadLocalHoldCouter extends ThreadLocal<HoldCounter> {
        public HoldCounter initialValue() {
            return new HoldCounter();
        }
    }

    // 当前线程持有的可重入读取锁数，每当线程的读保持数下降至0，则删除
    private transient ThreadLocalHoldCounter readHolds;
    // 最后一个获取到读锁的线程计数器，每当有新的线程获取到读锁，这个变量都会更新，这个变量的目的是为了：当最后一个获取读锁的线程重复获取读锁，或者释放读锁，则直接使用这个变量，速度更快，相当于缓存
    private transient HoldCounter cachedHoldCounter;
    // 是获取读锁的第一个线程，如果只有一个线程获取读锁，使用这样一个变量速度更快
    private transient Thread firstReader = null;
    // firstReader的计数器
    private transient int firstReaderHoldCount;

    Sync() {
        // 初始化线程封闭的读保持数计数器
        readHolds = new ThreadLocalHoldCounter();
        // 0
        setState(getState());
    }

    // 是否阻塞当前获取读资源的线程，实现非公平/公平读锁获取
    abstract boolean readerShouldBlock();

    // 是否阻塞当前获取写资源的线程，实现非公平/公平写锁获取
    abstract boolean writerShouldBlock();
}
```

### **2.2.1 获取读资源**

#### **2.2.1.1 tryAcquireShared（重点）**

![获取读锁的流程](https://upload-images.jianshu.io/upload_images/4236553-c747934c55844272.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

获取读锁的模板方法，当写锁被其他线程占用（写操作），会阻塞读锁的获取（读操作）

使用了ThreadLocal线程变量，来记录每个线程重入读锁的次数，并且在每次获取时，将当前线程的ThreadLocal缓存到Sync.cachedHoldCounter,减少去线程查询

```java
protected final int tryAcquireShared(int unused) {
    Thread current = Thread.currentThread();
    int c = getState();
    // 查询写锁的占用情况，当写锁被占用（有线程在进行写操作），且该线程不是自身时，获取失败
    // 这也意味着自身占有写锁时，可以申请读锁，防止死锁
    if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
        return -1;
    int r = sharedCount(c);
    if (!readerShouldBlock() && r < MAX_COUNT && compareAndState(c, c + SHARED_UNIT)) {
        // 修改成功，当前线程占有了一个读锁
        if (r == 0) {
            // 记录第一个占有读锁的线程
            firstReader = current;
            firstReaderHoldCount = 1;
        } else if (firstReader == current) {
            //  第一个占有读锁的线程重入了该锁
            firstReaderHoldCount++;
        } else {
            // r > 0，其他线程也占有了读锁
            // 取出缓存的HoldCounter
            HoldCounter rh = cachedHoldCounter;
            if (rh == null || rh.tid != getThreadId(current))
                cachedHoldCounter = rh = readHolds.get();
            else if (rh.count == 0)
                // 在tryReleaseShared中，cacheHoldCounter计数-1。当彻底释放读锁后，还会将线程的threadLocal删除，但此时cacheHoldCounter还是指向被删除前的holdCounter，因此此处再设置一下
                readHolds.set(rh);
                
            // 记录线程重入读锁的次数
            rh.count++;
        }
        return 1;
    }
    return fullTryAcquireShared(current);
}
```

Sync缓存cachedHoldCounter与线程计数器的一致性：
- cachedHoldCounter是该线程的，但是count == 0，则需要重新设置该cachedHoldCounter到readHolds。否则readHolds与缓存对象不一致，操作将无法指向同一个计数对象
- cachedHoldCounter不是当前线程的，直接设置缓存为当前线程的

```java
// 该方法可以实现非公平/公平的读锁获取；当无法获取时，且不满足其他条件，调用fullTryAcquireShared阻塞
abstract boolean readerShouldBlock();
```

导致获取读锁操作失败的原因有以下三个：
- readerShouldBlock == true
- r >= MAX_COUNT
- 中途有其它线程修改了state

当获取锁的操作失败了，会通过CAS自旋，不断获取state值，并尝试修改state，直至符合以下条件：
- 条件1：readerShouldBlock()返回false
- 条件2：r < MAX_COUNT
- 条件3：CAS成功

```java
// 与tryAcquireShared方法很相似，多了自旋获取操作，当readerShouldBlock()没有返回-1/占有写锁线程获取读锁/CAS失败，都会自旋操作，其余操作都会导致线程挂起/获取锁成功
final int fullTryAcquireShared(Thread current) {
    HoldCounter rh = null;
    // 自旋
    for (;;) {
        // 不断获取state值
        int c = getState();
        if (exclusiveCount(c) != 0) {
            if (getExclusiveOwnerThread() != current)
                return -1;
        } 
        // 条件1
        else if (readerShouldBlock()) {
            // 这里处理的是当前线程的读锁重入，出现的情况可能为：当前线程已持有读锁，需要重入，但是在上层readerShouldBlock()阻塞，则进入到这里重新判断（只有新加入的读程需要考虑写线程的饥饿问题）
            if (firstReader == current) {
                // 读锁重入，可以直接走到下面的CAS
            } else {
                if (rh == null) {
                    rh = cachedHoldCounter;
                    if (rh == null || rh.tid != getThreadId(current)) {
                        rh = readHolds.get();
                        if (rh.count == 0)
                            readHolds.remove();
                    }
                }
                if (rh.count == 0)
                    return -1;
                // 如果当前线程 != 0，则说明当前线程已经占有了读锁，可重入
            }
        }

        // 条件2
        if (sharedCount(c) == MAX_COUNT)
            throw new Error("Maximum lock count exceeded");

        // CAS成功
        if (compareAndSetState(c, c + SHARED_UNIT)) {
            if (sharedCount(c) == 0) {
                firstReader = current;
                firstReaderHoldCount = 1;
            } else if (firstReader == current) {
                firstReaderHoldCount++;
            } else {
                if (rh == null)
                    rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                else if (rh.count == 0)
                    readHolds.set(rh);
                rh.count++;
                cachedHoldCounter = rh;
            }
            return 1;
        }
    }
}
```

#### **2.2.1.2 tryReleaseShared**

上述线程通过ThreadLocal记录了对读锁的重入次数，后续每调用tryReleaseShared方法，重入数都会-1。直到变为0为止，该方法才会返回true；否则返回false。

```java
// 释放读锁
protected final boolean tryReleaseShared(int unused) {
    Thread current = Thread.cuurentThread();
    // 当前线程是之前第一个获取读锁的线程
    if (firstReader == current) {
        if (firstReaderHoldCount == 1)
            firstReader = null;
        else
            firstReaderHoldCount--;
    } else {
        HoldCounter rh = cachedHoldCounter;
        if (rh == null || rh.tid != getThreadId(current))
            // 取不到，则从ThreadLocal中取
            rh = readHolds.get();
        int count = rh.count;
        if (count <= 1) {
            // 若当前线程不再占用锁，则清除对应的ThreadLocal变量
            // 该操作与tryAcquireShared联动
            readHolds.remove();
            if (count <= 0)
                throw unmatchUnlockException();
        }
        --rh.count;
    }

    for (;;) {
        int c = getState();
        int nextc = c - SHARED_UNIT;
        if (compareAndSetState(c, nextc))
            return nextc == 0;
    }
}
```

### **2.2.2 获取写资源**

#### **2.2.2.1 tryAcquire()**

相比ReentrantLock，多了操作：同一时刻有读操作时，写锁获取会失败

```java
// 获取写锁（当读锁被占用时（有线程在读），阻塞写操作，同时无法获取到写锁（阻塞写操作））
protected final boolean tryAcquire(int acquires) {
    Thread current = Thread.currentThread();
    int c = getState();
    // 获取当前写锁的个数
    int w = exclusiveCount(c);
    if (c != 0) {
        // 1. c != 0 && w == 0, 则读锁的资源数 != 0，说明有线程占有了读锁，不能再获取写锁了
        // 2. current != getExclusvieOwnerThread(), 若写锁被占用，但不是当前线程，则获取写锁失败
        if (w == 0 || current != getExclusvieOwnerThread())
            return false;

        // MAX_COUNT = 65534个，不能超过这个上限
        if (w + exclusiveCount(acquires) > MAX_COUNT)
            throw new Error("Maximum lock count exceeded");

        // 重入，直接设置，同一时刻只有一个线程能走到这
        setState(c + acquires);
        return true;
    }

    // c == 0, 说明写锁和读锁都没有被占用
    if (writerShouldBlock() || !compareAndSetState(c, c + acquires))
        return false;

    setExclusiveOwnerThread(current);
    return true; 
}
```

```java
// 非公平/公平锁实现
abstract boolean writerShouldBlock();
```

#### **2.2.2.2 tryRelease()**

与ReentrantLock对写锁的释放一致

```java
// 释放写锁
protected final boolean tryRelease(int releases) {
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    int nextc = getState() - releases;
    // 通过独占资源掩码，计算扣除资源数后是否资源为0
    boolean free = exclusiveCount(nextc) == 0;
    if (free)
        // 解锁
        setExclusiveOwnerThread(null);
    setState(nextc);
    return free;
}
```

### **2.2.3 NonfairSync**

非公平Sync，主要重写两个抽象方法，做到公平/非公平锁效果

```java
static final class NonfairSync extends Sync {
    // 非公平获取写锁，不需要阻塞，与队列一起竞争
    final boolean writeShouldBlock() {
        return false;
    }

    // 非公平获取读锁
    final boolean readerShouldBlock() {
        return apparentlyFirstQueuedIsExclusive();
    }
}

// Doug lea注释：
// 该方法避免了写程序在队伍中陷入无限期饥饿的情况，如果队列头的线程是一个等待的写线程，则阻塞它。如果队列头是读锁，写锁后面有一个等待写锁的线程，则新的读锁线程加入不会阻塞；反之队头是写锁，则需要阻塞
final boolean apparentlyFirstQueuedIsExclusive() {
    Node h, s;
    // s代表队列中第一个等待的线程，若该线程是写锁，则
    return (h == head) != null && (s = h.next) != null && !s.isShared() && s.thread != null;
}
```

### **2.2.4 FairSync**

在获取读锁/写锁时，如果队列中有等待的节点了，则需要排队。实现方式是AQS.hasQueuedPredecessors()，在可重入锁和信号量都使用了该方法，实现锁资源的公平竞争

```java
static final class FairSync extends Sync {
    final boolean writerShouldBlock() {
        return hasQueuePredecessors();
    }

    final boolean readerShouldBlock() {
        return hasQueuedPredecessors();
    }
}
```

### **2.3 Lock接口实现类**

ReentrantReadWriteLock实现了2.1 ReadWriteLock的接口，在读写锁内又组合Lock接口的实现类：WriteLock/ReadLock，这两个实现类在接口方法实现上才调用了AQS

### **2.3.1 读锁**

与其他AQS共享模式实现类思路基本一致，值得注意的是读锁没有等待队列

等待队列在独占模式才有意义，因为共享模式下有多个线程在运行，无法保证业务代码的“判断-执行”操作是原子的，即多个线程的操作不是串行化的

假设存在A线程await，等待B线程完成任务后signAll唤醒：
在不能保证原子操作下，可能会导致A线程尚未执行await方法，B线程已经完成了任务，signAll无法唤醒A线程

    同理，Object.wait/Object.notify为什么要在synchronized中使用

```java
public static class ReadLock implements Lock {
    private final Sync sync;

    protected ReadLock(ReentrantReadWriteLock lock) {
        this.sync = lock.sync; // 
    }

    public void lock() {
        sync.acquireShared(1);
    }

    public void lockInterruptibly() {
        sync.acquireSharedInterruptibly(1);
    }

    public void unlock() {
        sync.releaseShared(1);
    }

    public boolean tryLock() {
        return sync.tryReadLock();
    }

    public boolean tryLock(long timeout, TimeUnit unit) {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}

// Sync.class
final boolean tryReadLock() {
    // 该方法与sync.tryAcquireShared(int arg)基本一致，后者是AQS的模板方法，需要返回r的数量
}
```

### **2.3.2 写锁**

基本没差，不赘述

```java
public static class WriteLock implements Lock {
    private final Sync sync;

    protected WriteLock(ReentrantReadWriteLock lock) {
        sync = lock.sync;
    }

    public void lock() {
        sync.acquire(1);
    }

    public void lockInterruptibly() {
        sync.acquireInterruptibly(1);
    }

    public void unlock() {
        sync.release(1);
    }

    public boolean tryLock() {
        return sync.tryWriterLock();
    }

    public boolean tryLock(long timeout, TimeUnit unit) {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    public Condition newCondition() {
        return sync.newCondition();
    }
}

final boolean tryWriterLock() {
    // 与sync.tryAcquire(int arg)基本一致
}
```

## **3. 锁降级**

    锁降级(写锁进程的重入性)：
    如果不支持降级，写锁占用的线程，需要释放写锁后，才能获取读取锁，否则会进入死锁
    
    而为了线程安全性，在释放写锁后，如果为了保证该线程串行化获得读锁，需要保证其他线程不会抢夺写锁；或者没有这个需求，也会引起线程对锁的争夺和线程唤醒上下文切换，这都将引入其他的性能效率问题。
    
    所以为了效率，降级的实现方式如下：
    
        先获取写入锁，然后获取读取锁，最后释放写入锁

    注意：重入允许从写入锁降级为读取锁，但是，从读取锁升级到写入锁是不可能的

![锁降级](https://upload-images.jianshu.io/upload_images/4236553-f545a504abde8c2f.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

总的来说，锁降级就是写锁线程的一种特殊的锁重入机制

## **4. demo**

```java
public class ReadWriteDemo {
    static ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    static ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
    static ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();


    public static void main(String[] args) {
        //读
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    System.out.println("read thread " + threadName + " acquire read lock");
                    readLock.lock();
                    System.out.println("read thread " + threadName + " read locking");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    readLock.unlock();
                    System.out.println("read thread " + threadName + " release read lock remain read count:" + readWriteLock.getReadLockCount());
                }
            }, "" + i).start();
        }

        //写
        for (int i = 4; i < 7; i++) {
            new Thread(() -> {
                String threadName = Thread.currentThread().getName();
                try {
                    System.out.println("write thread " + threadName + " acquire write lock");
                    writeLock.lock();
                    System.out.println("write thread " + threadName + " write locking");
//                    Thread.sleep(1000);
                } finally {
                    writeLock.unlock();
                    System.out.println("write thread " + threadName + " release write lock remain write count:" + readWriteLock.getWriteHoldCount());
                }
            }, "" + i).start();
        }
    }
}
```

**输出分析:**

    读写锁结构详情使用 [读锁使用线程] [写锁使用线程] [同步队列情况] 格式进行描述，thread 0~3是读线程，thread 4~6是写线程

read thread 0 acquire read lock

    [] [] []

read thread 2 acquire read lock

    [] [] []

read thread 0 read locking

    [0] [] [] 当前写锁未被占用，可以读

write thread 5 acquire write lock

    [0] [] [] 输出有延迟：写锁还未调用writeLock.lock()

read thread 2 read locking

    [0, 2] [] [] 队列头为空，直接读

read thread 1 acquire read lock

    [0, 2] [] [5, 1] 刚刚的thread5调用了writeLock.lock，进入了队列，则此时为了防止写程饥饿，阻塞thread 1的获取

write thread 6 acquire write lock

    [0, 2] [] [5, 1, 6]

write thread 4 acquire write lock

    [0, 2] [] [5, 1, 6, 4]

read thread 0 release read lock remain read count:1

    [2] [] [5, 1, 6, 4]

read thread 2 release read lock remain read count:0

    [] [] [5, 1, 6, 4]

write thread 5 write locking

    [] [5] [1, 6, 4] 

write thread 5 release write lock remain write count:0

    [] [] [1, 6, 4]

read thread 1 read locking

    [1] [] [6, 4] 

read thread 1 release read lock remain read count:0

    [] [] [6, 4]

write thread 6 write locking

    [] [6] [4] 

write thread 6 release write lock remain write count:0

    [] [] [4]

write thread 4 write locking

    [] [4] []

write thread 4 release write lock remain write count:0

    [] [] []

# 参考
- [移位、原码、反码和补码](https://blog.csdn.net/qq_41135254/article/details/97750382)
- [Java 并发之 ReentrantReadWriteLock 深入分析](https://www.jianshu.com/p/c4af8c70ff99)
- [并发编程之——读锁源码分析(解释关于锁降级的争议)](https://www.jianshu.com/p/cd485e16456e)