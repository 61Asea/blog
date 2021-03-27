# ReentrantLock

    ReentrantLock：显式锁，可重入锁，独占锁

ReentrantLock(AbstractQueuedSynchronizer)是在JDK1.5中引入的，同时引入的还有Unsafe的CAS语义，ReentrantLock的细节上就是使用Unsafe的CAS，来减少线程阻塞挂起或休眠，相比同期未优化的Synchonzied性能有较为显著的提高

    两者的数据结构都包含：
    volatile + CAS + 同步队列 + 等待队列(等待/通知)

在JDK1.6后，Synchronized也进行了相对应的优化，新增了锁优化与锁膨胀策略，偏向锁（CAS）、轻量级锁（CAS + 自旋）显著提升了synchronized的性能，成为官方的推荐使用

现在使用ReentrantLock，已经没有与synchronized有性能提升一说，更多的是需要其所具备的，但synchronized没有的特性

    ReentrantLock经常与synchronized进行对比，本质上两者的内存语义，线程挂起方式一致，没有明确的数据/理论表明 ReentrantLock 比synchronized 更快

独占锁锁定的标识：当前ExclusiveThread为自身，状态为1

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
        sync.tryAcquireNanos
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

    非公平锁的实现，非公平在于，调用该模块lock方法的线程，可能会抢先于同步队列队头的线程获得资源，对于同步队列队头的线程而言不公平

    ```java
        static final class NonfairSync extends Sync {
            final void lock() {
                // CAS获取锁，此时可能锁刚释放，调用该方法的线程就取到了锁，而同步队列里的线程取不到锁继续等待
                if (compareAndSetState(0, 1))
                    // 成功则将自己设置为独占线程
                    setExclusiveOwnerThread(Thread.currentThread());
                else
                    // 失败了，再加入到队列里面排队
                    acquire(1);
            }

            protected final boolean tryAcquire(int acquires) {
                return nonfairTryAcquire(acquires);
            }
        }
    ```

    一上来就开始抢占锁，失败后才开始判断是否有线程占有锁，没有人占有的话又开始抢占，这些抢占操作不成功才会进入同步队列阻塞等待别的线程释放锁
    这也是非公平的特点：**不管是否有线程在排队等候锁，我就不排队，直接插队，实在不行才乖乖排队**

3. FairSync

    公平锁是相当于在同步队列中排队的第一个线程，调用该lock模块的线程，会先判断是否同步队列有排队的线程，如果没有的话才去获取资源，有的话则直接入队

    ```java
        static final class FairSync extends Sync {
            final void lock() {
                // 比非公平锁少了直接CAS的操作
                acquire(1);
            }

            protected final boolean tryAcquire(int acquires) {
                final Thread current = Thread.currentThread();
                int c = getState();
                if (c == 0) {
                    // 在CAS之前先判断hasQueuedPredecessors，如果同步队列有其他线程排队的话，则加入到同步队列尾进行排队
                    if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                        setExclusiveOwnerThread(current);
                        return true;
                    }
                }
                // ...
            }
        }
    ```

### **2. 公平/非公平**

公平与非公平体现在获取锁时策略的不同：
- 公平锁每次都会检查队列是否有节点等待，若没有则抢占锁，否则就去排队等候。
- 非公平锁每次都会先去抢占锁，实在不行才排队
- 公平锁、非公平锁在释放锁的逻辑上是一致的

**非公平锁的重要特征**
```java
    public void lock() {
        if (compareAndSetState(0, 1))
            setExclusiveThread(Thread.currentThread());
        else
            acuiqre(1);
    }
```

**公平锁的重要特征：**
```java
// AbstractQueuedSynchronizer.java
// true则需要排队，false则不需要排队，具体断言可看参考的文档
public final boolean hasQueuedPredecessors() {
    Node t = tail; // Read fields in reverse 
    Node h = head;
    Node s;
    return h != t &&
        ((s = h.next) == null || s.thread != Thread.currentThread());
}
```

### **3. 可中断/不可中断**

借助AQS完成该功能

    若是线程在同步队列中等待，外界调用了该线程的Thread.interrupt()方法，结果就是被中断的线程被唤醒，放弃获取锁，并抛出中断异常

### **4. 可限时/不可限时**

在synchronized和ReentrantLock.lock()中，万一锁被别的线程占有了，当前线程就会阻塞住。tryLock()可尝试一次获取锁，若不成功，不去排队

```java
// ReentrantLock.java
public boolean tryLock() {
    return sync.nonfairTryAcquire(1);
}
```

排队也可接受，但是需要限时，则通过AQS的tryAcquireNanos方法，当时间到了还未获取到锁，则直接退出争取流程
```java
// ReentrantLock.java
public boolean tryLock(long timeout, TimeUnit unit) {
    return sync.tryAcquireNanos(1, unit.toNanos(timeout));
}
```

### **5. 等待/通知**

等待与通知机制，基于AQS的等待队列ConditionObject，该队列是**单向链表，节点在调用await()从队尾加入，被其他线程signal或中断时从队头出队**

```java
    // ReentrantLock.java
    public Condition newCondition() {
        return sync.newCondition();
    }

    // Sync.java
    final ConditionObject newConition() {
        return new ContionObject();
    }
```

```java
public class ConditionDemo {
    public static ReentrantLock lock = new ReentrantLock();

    public static Condition condition = lock.newCondition();

    public static Runnable asyncTask = new Thread(() -> {
        try {
            // 获取不到锁时，阻塞住
            lock.lock();
            // 主线程调用await方法，释放资源，异步线程获得资源并执行唤醒，将主线程加入到同步队列中
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    });

    public static void main(String[] args) {
        try {
            lock.lock();
            asyncTask.run();
            condition.await();
            System.out.println("main thread was signed by async Thread");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
```

## **6. ReentrantLock与Synchronized的对比（转载参考的第二篇文章）**

### **6.1 基本结构**

    都包含了CAS操作，数据结构：同步队列 + 等待队列（等待/通知）

### **6.2 功能差异**

1. 相同功能

    - 都能实现独占
    - 都能实现非公平锁
    - 都能实现可重入性
    - 都能实现不可中断锁
    - 都是悲观锁（Synchronized在1.6优化后在重量级锁下为悲观锁）

2 不同功能

    1. 实现方式

        - synchronized是关键字，具体实现在JVM层面上；ReentrantLock是JDK实现上的锁
        - synchronized保护的方法/代码块发生异常会自动释放锁，ReentrantLock保护的代码块发生异常不会释放，**必须在finally中主动释放锁**

    2. ReentrantLock提供的更多功能

        - 公平锁
        - 可中断锁
        - 限时等待锁，超时则放弃锁竞争
        - 单次获取，检测当前锁是否被占用
        - 绑定多个等待队列，而synchronized只有monitor的一个等待队列
        - 能够获取更多同步队列、等待队列的详情

### **6.3 性能区别**

    很多文章说："synchronized 使用了mutex，陷入内核态，而ReentrantLock 使用CAS，是CPU的特殊指令云云"，由此证明synchronized 更耗性能

### **6.3.1. 线程模式**

1. synchronized

    Synchronized在偏向锁/轻量锁会通过CAS或CAS自旋来确保线程不会立即挂起，当膨胀为重量级锁时，才会将线程加入到同步队列中等待锁竞争

    重量级锁挂起方式：
        
        ParkEvent.park() -> NPTL.pthread_cond_wait(xx)

        NPTL是Linux glibc下实现的，用的是futex

    CAS/CAS自旋方式：
    
        Atomic::cmpxchg_ptr(xx) （C++实现的语义）
        
        因为synchronized本身就是C++实现的，直接调用了Atomic

2. ReentrantLock

    ReentrantLock的大部分操作都通过Unsafe.CAS来确定线程是否要挂起与唤醒（整个过程没有使用到自旋，自旋只在enq方法加入同步队列时使用），当竞争失败时，也会将线程挂起

        ReentrantLock挂起方式:

            AQS -> LockSupport.park() -> unsafe.park() -> Parker.park(xx) -> NPTL.pthread_cond_wait(xx)

        CAS方式：

            unsafe.compareAndSwapInt(xx) -> Atomic::cmpxchg_ptr(xx)


可以看出，两种锁在一直竞争不到资源的情况下：

- 线程都会在最后进入到阻塞态挂起
- CAS与线程挂起的内存语义一致
- 唯一两者的不同点在于synchronized优化，膨胀到轻量级锁时会使用自旋CAS的方式尽量避免线程挂起，但是内置锁膨胀有开销，比如：锁记录的变化修改

# 参考
- [AQS.md](https://github.com/61Asea/blog/blob/master/%E5%A4%9A%E7%BA%BF%E7%A8%8B/JUC/locks/AQS.md)
- [Java 并发之 ReentrantLock 深入分析(与Synchronized区别)](https://www.jianshu.com/p/dcabdf695557)
- [AQS-hasQueuedPredecessors()解析](https://blog.csdn.net/weixin_38106322/article/details/107154961)