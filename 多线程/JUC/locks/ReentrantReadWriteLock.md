# ReentrantReadWriteLock

ReentrantReadWriteLock是JUC包中读写锁接口（ReadWriteLock）的重要实现类，主要实现：读共享，写独占

对比单纯的互斥锁，在共享资源使用场景为**频繁读取及少量修改**的情况下可以较好的提高性能

问题：独占锁，读跟写互斥没毛病，**但是如果全是读，在同一时刻只能有一个线程去访问资源，效率低**

读写锁的优化效果：
1. 读的时候，多个线程可以一起访问资源，并且阻塞其他线程的写操作（获取写资源）
2. 写的时候，阻塞全部线程的读操作（获取读资源）

## **1. 读写锁的state**

state一般只用来表示一种锁，要么独占，要么共享，为了**能让state可以同时协同读、写线程**，state通过位数拆分，表示两种锁资源

    // int类型，32bits，高16位：读锁（共享），低16位：写锁（独占）
    public volatile int state;

    0000 0000 0000 0000 （高16位）| 0000 0000 0000 0001（低16位）

## **2. 基础类和接口**

ReentrantReadWriteLock没有像ReentrantLock一样实现Lock接口，而是在内部组合了ReadLock、WriteLock的成员变量，两者均实现了Lock接口

读写锁构造之后，将锁暴露出来给外部使用

```java
public class ReentrantReadWriteLock implements ReadWriteLock {
    private final ReentrantReadWriteLock.ReaderLock readerLock;
    private final ReentrantReadWriteLock.WriteLock writerLock;

    final Sync sync;

    public ReentrantReadWriteLock() {
        this(false);
    }

    public ReentrantReadWriteLock(boolean fair) {
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
    // 0000 0000 0000 0000 1111 1111 1111 1111，写锁的最大值？
    static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;
    // 0000 0000 0000 0000 1111 1111 1111 1111，写锁掩码？
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
    private transient HoldCounter cachedHoldCounter;
    private transient Thread firstReader = null;
    private transient int firstReaderHoldCount;

    Sync() {
        // 初始化线程封闭的读保持数计数器
        readHolds = new ThreadLocalHoldCounter();
        // 0
        setState(getState());
    }

    // 是否阻塞当前获取读资源的线程
    abstract boolean readerShouldBlock();

    // 是否阻塞当前获取写资源的线程
    abstract boolean writerShouldBlock();

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

    protected final int tryAcquireShared(int unused) {
        Thread current = Thread.currentThread();
        int c = getState();
        // 获取读锁，当有其他线程在进行写操作时，获取失败
        if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
            return -1;
        // r > 0, 表明有线程占用了读锁
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
                // 其他线程也占有了读锁
                // 取出缓存的HoldCounter
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    cachedHoldCounter = rh = readHolds.get();
                else if (rh.count == 0)
                    // 说明cachedHoldCounter已经被移出threadlocal
                    readHolds.set(rh);
                    
                // 记录线程重入读锁的次数
                rh.count++;
            }
            return 1;
        }
        return fullTryAcquireShared(current);
    }
}
```

# 参考
- [移位、原码、反码和补码](https://blog.csdn.net/qq_41135254/article/details/97750382)