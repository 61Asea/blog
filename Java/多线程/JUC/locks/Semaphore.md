# Semaphore

Semaphore又称信号量，使用AQS的共享模式实现

    如停车场只能容纳一定数量的车子，当停车场停满了车(入场许可发放完了)，其它想进来的车子必须等待有其它车从停车场开出(释放入场许可)

## **1. 信号量的state**

通过**将资源抽象为permits（许可证）**，达到以下的效果：

    permits是共享的，但是permits数量有限，没有拿到permits的需要等待其他线程释放permits

state作为标记共享资源的数量，也被称为permits，state的状态变更如下：
- 线程占用资源时，则state减1；反之，state加1
- 若线程没拿到资源，则挂起等待
- 线程获取/释放资源后，都会唤醒其他等待该资源的线程

![](https://upload-images.jianshu.io/upload_images/19073098-755d8e27ded0ea70.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

## **2. 基础类和接口**

与其他同步器类似，提供统一的Sync父类，以及重写Sync的非公平/公平子类，并通过组合方式，组合sync到Semaphore里

```java
public class Semaphore implements java.io.Serializable {
    private final Sync sync;

    abstract static class Sync extends AQS {
        Sync(permits) {
            setState(permits);
        }
    }

    final class NonfairSync extends Sync {
        NonfairSync(permits) {
            super(permits);
        }
    }

    final class FairSync extends Sync {
        FairSync(permits) {
            super(permits);
        }
    }

    public Semaphore(int permits) {
        // 默认使用非公平模式
        this(permits, false);
    }

    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new UnfairSync(permits);
    }
}
```

## **2.1 Sync extends AQS**

```java
abstract static class Sync extends AbstractQueuedSynchronizer {
    Sync(int permits) {
        // 初始化时，设置状态为当前许可证的数量
        setState(permits);
    }

    final int getPermits() { return getState(); }

    final int nonfairTryAcquireShared(int acquires) {
        for (;;) {
            // 获取可以许可证数量
            int available = getState();
            int remaining = available - acquires;
            // 当前线程不在队列时调用，则会直接与同步队列的线程进行竞争
            if (remaining < 0 || compareAndSetState(available, remaining))
                // r > 0, 在AQS中会唤醒后续节点；r < 0（0 - acquires）且在同步队列中，线程挂起
                return remaining;
        }
    }

    protected final boolean tryReleaseShared(int releases) {
        for (;;) {
            int current = getState();
            int next = current + releases;
            if (next < current)
                // 防止releases传入负数 
                throw new Error("Maxmium permit count exceeded");
            if (compareAndSetState(current, nextc))
                return true;
        }
    }

    // Semaphore没有为这个方法暴露接口，只有重写Semaphore的类才能暴露
    final void reducePermits(int reductions) {
        for (;;) {
            int current = getState();
            int next = current - reductions;
            if (next > current)
                // 防止reductions传入整数
                throw new Error("Permit count underflow");
            if (compareAndSetState(current, next))
                return;
        }
    }

    // 清空许可证的个数，并返回清空前可用的许可证数量
    final int drainPermits() {
        for (;;) {
            int current = getState();
            if (current == 0 || compareAndSetState(current, 0))
                return current;
        }
    }
}
```

### **2.1.1 NonfairSync**

```java
static final class NonfairSync extends Sync {
    // ...

    protected int tryAcquireShared(int acquires) {
        return nonfairTryAcquireShared(acquires);
    }
}
```

### **2.1.2 FairSync**

```java
static final class FairSync extends Sync {
    // ...

    // 公平地获取共享资源
    protected int tryAcquireShared(int acquires) {
        for (;;) {
            if (hasQueuedPredecessors())
                // 当队列有其他等待者时，获取失败
                return -1;
            int available = getState();
            int remaining = available - acquires;
            if (remaining < 0 || compareAndSetState(available, remaining))
                return remaining;
        }
    }
}
```

## **3. Semaphore提供的接口**

获取许可证接口：
- acquire()
- acquire(int)
- acquireUninterruptibly()
- acquireUninterruptibly(int)

释放许可证接口：
- release()
- release(int)

尝试获取许可证接口：
- tryAcquire():boolean
- tryAcquire(long, TimeUnit)
- tryAcquire(int):boolean
- tryAcquire(int, long, TimeUnit):boolean

```java
public class Semaphore implements java.io.Serializable {
    private final Sync sync;

    // ..

    public void acquire() {
        sync.acquireSharedInterrutibly(1);
    }

    public void acquire(int permits) {
        if (permits < 0)
            throw new IllegalArgumentException();
        sync.acquireSharedInterrutibly(permits);
    }

    public void acquireUninterrutibly() {
        sync.acquireShared(1);
    }

    public void acquireUniterruptibly(int permits) {
        if (permits < 0)
            throw new IllegalArgumentException();
        sync.acquireShared(permits);
    }

    public void release() {
        sync.releaseShared(1);
    }

    public void release(int permits) {
        if (permits < 0)
            throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }

    // 默认使用非公平的获取方式
    public boolean tryAcquire() {
        return sync.nonfairTryAcquireShared(1) >= 0;
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    public boolean tryAcquire(int permits) {
        if (permits < 0)
            throw new IllegalArgumentException();
        return sync.nonfairTryAcquireShared(permits) >= 0;
    }

    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
        if (permits < 0)
            throw new IllegalArgumentException();
        return sync.tryAcquireSharedNanos(permits, unit.toNanos(unit));
    }
}
```

## **4. DEMO**

```java
public class SemaphoreDemo {
    public static void main(String[] args) {
        // 给2个许可证
        Semaphore semaphore = new Semaphore(2);
        for (int i = 0; i < 4; i++) {
            new Thread(() -> {
                try {
                    System.out.println(Thread.currentThread().getName() + " try to acquire");
                    semaphore.acquire();
                    System.out.println(Thread.currentThread().getName() + " got permit, doing work...");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    System.out.println(Thread.currentThread().getName() + " release permit");
                    semaphore.release();
                }
            }).start();
        }
    }
}
```

输出详情：

    [获取到许可证的线程] [同步队列]

Thread-0 try to acquire

    [] []

Thread-0 got permit, doing work...

    [0] []
    
Thread-1 try to acquire

    [0] []

Thread-1 got permit, doing work...

    [0, 1] []

Thread-3 try to acquire

    [0, 1] [3]

Thread-2 try to acquire

    [0, 1] [3, 2] // 非公平获取失败，如果成功的话，可能2会比3先获取到资源

Thread-1 release permit

    [0] [3, 2]

Thread-0 release permit

    [] [3, 2]

Thread-3 got permit, doing work...

    [3] [2]

Thread-2 got permit, doing work...

    [3, 2] []

Thread-3 release permit

    [2] []

Thread-2 release permit

    [] []

# 参考
- [Java Semaphore/CountDownLatch/CyclicBarrier 深入解析](https://www.jianshu.com/p/4556f0f3b9cb)
