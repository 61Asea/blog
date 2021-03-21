# AbstractQueuedSychronizer

AQS，抽象队列同步器，是JDK为”线程同步“提供了一套通用的机制，来**管理同步状态（synchronization state）、阻塞/唤醒线程、管理等待队列**

![](https://upload-images.jianshu.io/upload_images/19073098-523c9713fd239283.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

基于它可以写出JAVA中的很多同步器类，如ReentrantLock、CountDownLatch、Semaphore、CyclicBarrier，这些同步器的主要区别就是对**同步状态（synchronization state）的定义不同**

![](https://upload-images.jianshu.io/upload_images/19073098-5277b2a012368215.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

AQS是一种典型的模板方法设计模式，父类定义好骨架和内部操作细节，具体规则由子类去实现

## **1. 模板**

### **1.1 synchronization state（资源）**

在模板方法设计模式下，AQS高度抽象出同步器的关注点：

    如何定义资源？资源是否可以被访问？

所以在子类同步器实现中，都围绕资源（synchronization state）来展开：

```java
    public abstract class AbstractQueuedSynchronizer {
        /**
        * The synchronization state.
        */
        private volatile int state;
    }
```

- 资源的访问方式

    同时访问/同一时间只能被一个线程访问（共享/独占）

    ```java
        static final class AbstractQueuedSynchronier.Node {
            // 共享
            static final Node SHARE = new Node();

            // 独占
            static final Node EXCLUSIVE = null;
        }
    ```
- 访问资源的线程如何进行并发管理（等待队列）
- 线程如何退出（超时/中断）

常见同步器对资源的定义：
| 同步器 | synchronization state的定义 |
| ------ | ------ |
| ReentrantLock | 资源表示独占锁，0表示锁可用；1表示被占用；N表示重入的次数 |
| CountDownLatch | 资源表示倒数计数器，0表示计数器为零，所有线程都可用访问资源；N表示计数器未归零，所有线程都需要阻塞 |
| Semaphore | 资源表示信号量或令牌，0表示没有令牌可用，所有线程都得阻塞；大于0表示有令牌可用，线程每获得一个令牌，state减1，反之则加1 |
| ReentrantReadWriteLock | 表示共享的读锁和独占的写锁，state在逻辑上被分成两个16位unsigned short，分别记录读锁被多少线程使用和写锁被重入的次数 |

### **1.2 模板方法**

    AQS暴露以下API来让用户解决1.1中提出的第二个问题，资源是否可以被访问

| 钩子方法 | 描述 |
| -----| ----- |
| tryAcquire | 排它获取（资源数）|
| tryRelease | 排它释放（资源数）|
| tryAcquireShared | 共享获取（资源数）|
| tryReleaseShared | 共享释放（资源数）|
| isHeldExclusively | 是否排它状态 |

以ReentranLock为例：

ReentrantLock是通过组合NonfairSync/FairSync的方式，并重写Lock接口方法的方式，做到统一暴露Lock接口方法tryLock()和lock()的效果

而在ReentrantLock的tryLock()和lock()的具体实现中，调用的是AQS暴露的接口acquire(), acquire()的实现中使用了具体的tryAcquire模板方法，该模板方法具体实现在NonfairSync和FairSync中

lock/tryLock(Lock接口) -> Sync的lock模板方法（NonfairSync/FairSync对lock模板实现） -> AQS的父类方法acquire -> AQS的Acquire模板方法（NonfairSync/FairSync对tryAcquire模板的实现）

    不同的AQS实现对于arg的传入含义不同，这也解决第一个问题：如何定义资源

### **1.3 暴露方法**

模板方法设计模式下，模板方法并不是使用者的可用api，对于AQS而言，提供了以下的API

- acquire(int arg)
- tryAcquireNanos(int arg, long nanosTimeout)
- release(int arg)
- ...

这些API的使用，可以在模板子类中再包一层暴露出来（ReentrantLock通过Lock接口暴露具体用户API，方法的实现又通过调用Sync模板子类新增的API：lock，这个Sync的新增API，具体使用的就是AQS的暴露方法）

### **1.4 中断、超时、Condition条件等待**

1. 可选的超时设置

    ```java
    private boolean doAcquireNanos(int arg, long nanosTimeout) {
        final long deadline = System.nanoTime() + nanosTimeout;
        for(;;) {
            nanosTimeOut = deadline - System.nanoTime();
            if (nanosTimeout <= 0L) {
                // 超过超时时间
                return false;
            }

            if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                // 每次自旋都会休眠一小段时间，防止CPU疯狂轮询空操作
                LockSupport.parkNanos(this, nanosTimeout);
            }

            if (Thread.interrupted()) {
                // 被取消
                throw new InterruptedException();
            }
        }
    }
    ```

2. 可中断的阻塞操作

    ```java
    private void cancelAcquire(Node node) {
        // ...
        unparkSuccessor(node);
    }
    ```

3. Condition条件等待

    Condition接口，可以**看做是Obejct类的wait()、notify()、notifyAll()方法的替代品，与Lock配合使用**
    AQS框架内部通过一个内部类ConditionObject，实现了Condition接口，以此来为子类提供条件等待的功能

**具体的等待队列，Condition和超时机制，在第二点中分析**

### **1.5 手撕模板方法：ReentrantLock与AQS组合**

```java
    public class ReentrantLock implements Lock {
        private final Sync sync;

        abstract static class Sync extends AbstractQueuedSynchronizer {
            abstract void lock();

            final boolean nonfairTryAcquire(int acquires) {
                // 非公平的尝试，尝试一次CAS操作，成功则将当前线程设置为独占线程

                // 该方法是NonfairSync对AQS.tryAcquire的模板实现
            }
        }

        static class NonfairSync extends Sync {
            final void lock() {
                if (cas(0, 1))
                    setExclusiveOwnerThread(Thread.currentThread());
                else
                    // 调用AQS的统一暴露API: acquire(int arg)
                    acquire(1);
            }

            protected final boolean tryAcquire(int acquires) {
                return nonfairTryAcquire(acquires);
            }
        }

        static class FairSync extends Sync {
            final void lock() {
                // 调用AQS的统一暴露API: acquire(int arg), AQS再调用实现过的模板tryAcquire方法
                super.acquire(1);
            }

            protected final boolean tryAcquire(int acquires) {
                // 相比较nonfairTryAcquire，多了一步从队列中获取的步骤，满足FIFO的公平
            }
        }

        public boolean tryLock() {
            // 相当于调用NonfairLock对AQS的模板实现，因为tryAcquire并不暴露，所以无法调用
            return sync.nonfairTryAcquire(1);
        }

        public boolean tryLock(long timeout, TimeUnit unit) {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        public void lock() {
            sync.lock(); // 调用sync.acquire -> sync.tryAcquire
        }

        public static void main(String[] args) {
            // 内部Sync对象为NonfairSync对象
            ReentrantLock nonfairLock = new ReentrantLock();
            
            // 调用链：sync.nonfairTryAcquire() 非公平
            nonfairLock.tryLock();
            // 调用链: aqs.tryAcquireNanos -> nonfairLock.tryAcquire(1) -> sync.nonfairTryAcquire(1) 非公平
            fairLock.tryLock(1000, TimeUnit.SECOND);

            // 调用链：sync.lock -> aqs.acquire(1) -> nonfairLock.tryAcquire(1) 非公平
            nonfairLock.lock(); 

            // 内部Sync对象为FairSync对象
            ReentrantLock fairLock = new ReentrantLock(true);

            // 调用链: sync.nonfairTryAcquire() 非公平
            fairLock.tryLock();
            // 调用链: aqs.tryAcquireNanos -> fairLock.tryAcquire(1) 公平
            fairLock.tryLock(1000, TimeUnit.SECOND);

            // 调用链: sync.lock -> aqs.acquire(1) -> fairLock.tryAcquire(1) 公平
            fairLock.lock();
        }
    }
```

## **2. AQS关键数据结构**

### **2.1 Node**

通过Unsafe类的objectFieldOffset()方法用于**获取某个字段相对Java对象的"起始地址"的偏移量**
```java
// 对obj对象的某个引用类型字段进行CAS（offset代表该字段的起始位置，相对于obj对象的偏移量）
public native compareAndSwapObject(Object obj, long offset, Object expect, Object update)
```

```java
public abstract class AbstractQueuedSynchronizer {
    private transient volatile Node head;
    private transient volatile Node tail;
    private volatile int state;

    // AQS同步队列的偏移量
    private static final long headOffset;
    private static final long tailOffset;
    private static final long stateOffset;

    // Node关键字段的偏移量
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static final class Node {
        // 共享模式（CountDownLatch等）
        static final Node SHARED = new Node();

        // 独占模式（各种锁）
        static final Node EXCLUSIVE = null;

        static final int CANCELLED = 1; // 节点被取消，不再参与竞争锁
        static final int SIGNAL = -1; // 表示该节点的next节点需要被唤醒
        static final int CONDITION = -2; // 节点在等待队列里的状态
        static final int PROPAGATE = -3; // 表示头结点将唤醒的动作传播下去

        volatile int waitStatus;
        volatile Node prev;
        volatile Node next;
        Node nextwaiter;

        volatile Thread thread;
    }
}
```

### **2.2 同步队列**

![同步队列](https://upload-images.jianshu.io/upload_iages/19073098-803c32a55e7816e3.png?imageMogr2/auto-orient/strip|imageView2/2/w/938/format/webp)

头节点只起到索引作用，不关联任何线程，只有后驱节点

### **2.3 等待队列**



## **3. AQS的独占锁实现(EXCLUSIVE)**

### **3.1 获取资源的操作**

1. acquire(int arg)

```java
// AQS.java
public final void acquire(int arg) {
    if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

- arg：在不同的同步器实现有不同含义，具体可见上文表格
- tryAcquire：实现子类定义具体的获取同步状态操作
- addWaiter: 将当前线程封装到Node中，并**加入同步队列中**
- acquireQueued：符合条件下，竞争失败，则挂起线程
- selfInterrupt: 防止线程循环空操作，增加CPU压力

2. addWaiter(Node node)

**新增加节点到同步队列**，同步队列的头节点不关联任何线程，仅作为索引使用，在enq(Node node)方法中初始化

```java
public final Node addWaiter(Node node) {
    Node node = new Node(Thread.currentThread(), mode);
    Node pred = tail;
    if (pred != null) {
        node.prev = tail;
        if (compareAndSetTail(pred, node)) {
            // CAS操作，替换掉tail位置的node为当前node
            pred.next = node;
            return node;
        }
    }
    // 失败了则进入死循环，必须成功为止
    enq(node);
    return node;
}

// 进入同步队列（自旋 + CAS）
private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) {
            // 如果列表为空，则初始化，再重新进入for循环
            if (compareAndSwapHead(new Node()))
                tail = head;
        } else {
            node.prev = t;
            // CAS操作替换尾节点，失败则重新进入for循环
            if (compareAndSwapTail(t, node)) {
                // 成功，设置之前的尾节点的next为node
                t.next = node;
                return t;
            }
        }
    }
}
```

3. acquireQueued(final Node node, int arg)

synchronized加入同步队列后就使得线程挂起，而aqs会找到最前驱的节点，CAS设置其值为SIGNAL后，并最后尝试一次获取资源

**若依旧获取不到资源，且最前驱节点的状态已经为SIGNAL（SIGNAL表示前驱节点的后续节点需要被唤醒），则线程可以安全地挂起**

    从效果上看，AQS本身并没有提供自旋机制，且挂起线程的内存语义与Synchronized相同

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            // 获得当前线程节点的前驱节点
            final Node p = node.predecessor();
            // 如果前驱是头节点，则尝试获取资源
            if (p == head && tryAcquire(arg)) {
                // 成功获得同步状态，将头节点指向新的节点，并且新的节点的prev为空，表示先前的头节点出队了
                setHead(node);
                p.next = null;
                failed = false;
                return interrupted;
            }

            // 判断获取同步状态失败后是否需要挂起，走到这里说明获取同步状态失败了，可能需要挂起
            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

```java
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    // 拿出前驱节点的状态
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL) {
        // SIGNAL，一般意味着有其他的线程，或本线程在上次轮询中，设置了前驱节点为SIGNAL
        // 并且竞争失败
        return true;
    }

    if (ws > 0) {
        // 一直往链表的前面查找，直到找到不为取消状态的前驱节点为止
        do {
            node.prev = pred = pred.prev
        } while (pred.waitStatus > 0);

        // 链表跳过前驱节点和当前线程节点中间那些失效的（取消状态）的节点
        pred.next = node;
    } else {
        compareAndSwapWaitStatus(pred, ws, Node.SIGNAL);
    }
    return false;
}
```

4. cancelAcquire(Node node)

当获取同步状态发生异常时，需要取消线程竞争同步状态的操作
当到达获取同步状态的超时时间，还无法获得同步状态，则调用该方法

```java
private void cancelAcquire(Node node) {
    if (node == null)
        return;

    node.thread = null;

    // 找到当前需要取消节点的前驱节点（状态不为取消）
    Node pred = node.prev;
    while (pred.waitStatus > 0)
        node.prev = pred = pred.prev;
    // 上面的while循环已经得到的是node之前，第一个不为CANCELLED的节点，所以predNext肯定为CANCELLED
    Node predNext = pred.next;

    node.waitStatus = Node.CANCELLED;
    if (node == tail && compareAndSwapTail(node, pred)) {
        // predNext为CANCELLED，直接CAS置换掉
        compareAndSetNext(pred, predNext, null);
    } else {
        int ws;
        // 前驱节点不为队列头 && ( (前驱节点的等待状态为SIGNAL) || (前驱状态等待状态不为取消 && CAS设置前驱的状态为SIGNAL成功) ) && 前驱节点有绑定线程
        if (pred != head && ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && pred.thread != null) {
            Node next = node.next;
            // next为取消状态的，就不要设置了
            if (next != null && next.waitStatus <= 0) {
                compareAndSetNext(pred, predNext, next);
            }
        } else {
            // 前驱节点是头节点，则唤醒node之后的一个不为取消状态的节点
            unparkSuccessor(node);
        }

        node.next = node; // help GC
    }
}

// 唤醒后续节点
private void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0) {
        compareAndSetWaitStatus(node, ws, 0);
    }

    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        for (Node t = tail; t != null && t != node; t = t.prev) {
            if (t.waitStatus <= 0) {
                s = t;
            }
        }
    }

    if (s != null) {
        // 唤醒node节点之后，状态不为CANCELLED的节点线程
        LockSupport.unpark(s.thread);
    }
}
```

### **3.2 释放资源的操作**

```java
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0) {
            // 唤醒头节点的后续节点线程
            unparkSuccessor(h);
        }
        return true;
    }
    return false;
}
```

### **3.3 公平锁和非公平锁的区别**

1. 加锁的方式不同，非公平锁会直接先一个CAS设置独占线程，失败了再去acquire，而公平锁直接acquire，没有一次CAS

2. 两者的tryAcquire方式略有不同，非公平锁使用了父类的nonfairTryAcquire方法，而公平锁则比nonfairTryAcquire方法多了一步从队列中取的操作，做到FIFO的效果

## **4. AQS的共享实现（SHARED）**

# 参考
- [ReentrantLock.java]()
- [AbstractQueuedSynchronizer.java]()
- [Java多线程 J.U.C之locks框架：AQS综述](https://blog.csdn.net/wuxiaolongah/article/details/114435974)
- [Java并发之 AQS 深入解析(上)](https://www.jianshu.com/p/62ed0767471e)
- [Java并发之 AQS 深入解析(下)](https://www.jianshu.com/p/7a3033143802)