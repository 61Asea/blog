# ReentrantLock

    ReentrantLock：显式锁，可重入锁，独占锁

ReentrantLock(AbstractQueuedSynchronizer)是在JDK1.5中引入的，同时引入的还有Unsafe的CAS语义，ReentrantLock的细节上就是使用Unsafe的CAS，来减少线程阻塞挂起或休眠，相比同期未优化的Synchonzied性能有较为显著的提高

    两者的数据结构都包含：
    volatile + CAS + 同步队列 + 等待队列(等待/通知)

在JDK1.6后，Synchronized也进行了相对应的优化，新增了锁优化与锁膨胀策略，偏向锁（CAS）、轻量级锁（CAS + 自旋）显著提升了synchronized的性能，成为官方的推荐使用

现在使用ReentrantLock，已经没有与synchronized有性能提升一说，更多的是需要其所具备的，但synchronized没有的特性

    ReentrantLock经常与synchronized进行对比，本质上两者的内存语义，线程挂起方式一致，没有明确的数据/理论表明 ReentrantLock 比synchronized 更快

## **1. 基础类与接口**

### **1.1 Lock**

ReentrantLock实现了Lock接口, 提供了锁类一致的接口方法，在接口的具体方法实现上，ReentrantLock组合了AQS的独占模式，实现独占锁

```java
public interface Lock {
    void lock();

    void lockInterruptibly() throws InterruptedException;

    boolean tryLock();

    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    void unlock();

    Condition newCondition();
}

public class ReentrantLock implements Lock, java.io.Serializable {
    private Sync sync;

    void lock() {
        sync.lock(); // fair/nonfair
    }

    void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1); // aqs
    }

    boolean tryLock() {
        sync.nonfairTryAcquire(1); // nonfair
    }

    boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        
    }

    void unlock();
}
```

### **1.2 Sync (extends AbstractQueuedSynchronizer)**

在ReentrantLock，请求资源数都为1（acquires的值都为1），且AQS.state最大值为独占线程的（重入次数 + 1）

| AQS.state | 含义 |
| 0 | 未锁或刚释放资源解锁 |
| 1 | 独占锁定 |
| > 1 | 独占线程重入 |

1. Sync

    ReentrantLock通过组合方式组合Sync，Sync继承于AQS，且使用的是AQS的独占模式

    Sync实现了AQS的模板方法，并提供了与Lock接口的lock方法一致的抽象方法

    ```java
    public abstract class Sync extends AbstractQueuedSynchronizer {
        // 与Lock接口的lock一致
        abstract void lock();

        /** 默认的获取资源模板方法
            1. 非公平Sync的模板方法实现和ReentrantLock的tryLock方法都使用了它
            2. 因为模板方法tryAcquired是受保护, 而Lock接口的tryLock()方法只需要获取一次资源的动作，但又无法直接调用,所以暴露这个接口也可以使得tryLock可以调用到
            3. 该方法会在aqs.acquire方法里被反复调用（当线程被唤醒）
        **/
        final boolean nonfairTryAcquired(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState(); // 获取AQS的资源状态
            if (c == 0) {
                // 未被锁住，尝试CAS
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                // 可重入特性
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }

        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread())
                // 独占锁，其他线程不可释放资源，只有独占线程可以释放
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }
    }
    ```

2. NonfairSync

    非公平锁的实现

    ```java
        static final class NonfairSync extends Sync {
            final void lock() {

            }

            protected final boolean tryAcquire(int acquires) {
                return nonfairTryAcquire(acquires);
            }
        }
    ```

3. FairSync

## **2. 公平/非公平锁**

# 参考
- [AQS.md](https://github.com/61Asea/blog/blob/master/%E5%A4%9A%E7%BA%BF%E7%A8%8B/JUC/locks/AQS.md)
- [Java 并发之 ReentrantLock 深入分析(与Synchronized区别)](https://www.jianshu.com/p/dcabdf695557)