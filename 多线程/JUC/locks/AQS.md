# AbstractQueuedSynchronizer

AQS，抽象队列同步器，是JDK为”线程同步“提供了一套通用的机制，来**管理同步状态（synchronization state）、阻塞/唤醒线程、管理等待队列**

![AQS功能](http://8.135.101.145/upload/2021/05/19073098-523c9713fd239283-6e1bcb02c5df434bbc650019e9e7d11c.webp)

基于它可以写出JAVA中的很多同步器类，如ReentrantLock、CountDownLatch、Semaphore、CyclicBarrier，这些同步器的主要区别就是对**同步状态（synchronization state）的定义不同**

![AQS衍生同步器](http://8.135.101.145/upload/2021/05/19073098-f290953d9b6df0f2-f05809a233e641448d26509a8e3b5be3.webp)

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

![AQS衍生锁](http://8.135.101.145/upload/2021/05/19073098-5277b2a012368215-913c2805b9ea49fea42a69d5b6eba939.webp)

常见同步器对资源的定义：
| 同步器 | synchronization state的定义 |
| ------ | ------ |
| ReentrantLock | 资源表示独占锁，0表示锁可用；1表示被占用；N表示重入的次数 |
| CountDownLatch | 资源表示倒数计数器，0表示计数器为零，所有线程都可用访问资源；N表示计数器未归零，所有线程都需要阻塞 |
| Semaphore | 资源表示信号量或令牌，0表示没有令牌可用，所有线程都得阻塞；大于0表示有令牌可用，线程每获得一个令牌，state减1，反之则加1 |
| ReentrantReadWriteLock | 表示共享的读锁和独占的写锁，state在逻辑上被分成两个16位unsigned short，分别记录读锁被多少线程使用和写锁被重入的次数 |
| ThreadPoolExecutor.Worker | 类似ReentrantLock，但是是不可重入的 |

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

- 获取资源

    acquire(int)、acquireInterruptibly(int)、acquireShared(int)、acquireSharedInterruptibly(int)

    tryAcquireNanos(int, long)、tryAcquireSharedNanos(int, long)

- 释放资源

    release(int)、releaseShared(int)

这些API的使用，可以在模板子类中再包一层暴露出来

    ReentrantLock在内部重写了AQS子类，并新增一个lock方法，该lock方法的实现就是调用AQS暴露的这些API

### **1.4 手撕模板方法：ReentrantLock与AQS组合**

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

AQS的同步机制与Synchronized的机制类似，Synchronized的monitor拥有等待队列和同步队列，AQS也类似

![](https://img-blog.csdnimg.cn/20190329234207496.jpg?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2dzX2FsYmI=,size_16,color_FFFFFF,t_70)

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

1. 队列头

头节点只起到索引作用，不关联任何线程，只有后驱节点

    当node.prev == head时，相当于当前node处于队列头

2. node.next == null

node.next很可能为空，原因在于：
- 在cancel的时候，如果发现下一个节点为取消状态，则不设置前驱节点的下一个节点
- 在addWaiter的时候，在CAS设置前驱的next，可能出现并发问题，即其他线程遍历线程，得到pred.next == null的结果

这也是使用unparkSuccessor，从队尾往队头遍历的方式的原因，目的是为了解决next指针不可靠的问题

### **2.3 等待队列**

    AQS的等待队列，只在独占模式下使用，等待队列的接口方法只能在获得资源的时候使用

在Object的监视器模型上，一个对象拥有一个同步队列与一个等待队列，而AQS拥有一个同步队列和**多个等待队列**

条件队列都共用了Node内部类作为了节点，其中同步队列中用了Node类中的prev和next,等待队列中使用了nextWaiter

AQS中的等待队列是由内部类ConditionObject(implements Condition)维护, 子类中实现了await开头的方法，signal开头的方法，其中以await开头都是将线程设置成等待状态

```java
/** First node of condition queue. */
private transient Node firstWaiter;
/** Last node of condition queue. */
private transient Node lastWaiter;
```

    具体的实现细节可见第6点等待/通知

## **3. AQS的独占(EXCLUSIVE)/共享(SHARED)**

### **3.1 独占实现EXCLUSIVE**

#### **3.1.1 获取资源的操作**

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
private void setHead(Node node) {
    head = node;
    // head节点只是索引，不关联任何线程
    node.thread = null;
    node.prev = null;
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

- 当获取同步状态发生异常时(被中断)，需要取消线程竞争同步状态的操作
- 当到达获取同步状态的超时时间(超过传入的nanosTimeout)，还无法获得同步状态，则调用该方法

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
            // next为取消状态的，就不要设置了，在unparkSuccessor从队尾重新遍历，找到next进行唤醒(next指针可能指向为空)
            if (next != null && next.waitStatus <= 0) {
                compareAndSetNext(pred, predNext, next);
            }
        } else {
            // 前驱节点是头节点，则唤醒node之后的一个不为取消状态的节点，唤醒后的节点会将自己再设置为头节点
            unparkSuccessor(node);
        }

        node.next = node; // help GC
    }
}

// 唤醒后续节点, 从队尾往前遍历（变相理解为竞争）
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

#### **3.1.2 释放资源的操作**

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

#### **3.1.3 公平锁和非公平锁的区别**

1. 加锁的方式不同，非公平锁会直接先一个CAS设置独占线程，失败了再去acquire，而公平锁直接acquire，没有一次CAS

2. 两者的tryAcquire方式略有不同，非公平锁使用了父类的nonfairTryAcquire方法，而公平锁则比nonfairTryAcquire方法多了一步从队列中取的操作，做到FIFO的效果

### **3.2. AQS的共享实现（SHARED）**

共享实现与独占实现大同小异，共享状态获取的方法tryAcquireShared返回的是可用资源数量，而独占状态因为是独占属性，所以不需要返回可用资源数量（还有可用资源数量一定是0）

对应到实现，共享相比于独占的差异在于以下两点:
- 对tryAcquireShared()返回值的判断
- 共享状态下对资源的成功获取或成功释放，都会唤醒后续的节点
    如果尝试获取资源成功，在设置当前节点为头节点时，**需要顺带唤醒后续的共享节点**

#### **3.2.1 获取共享同步状态的操作**

该方法是核心，不止在释放资源时才会唤醒后续节点，在获取资源的时候也会

所以在拥有足够的资源之下，一个节点的唤醒并成功获取资源会引发对下一个节点的唤醒，直至资源 == 0为止

1. acquireShared(int arg)

```java
public final void acquireShared(int arg) {
    // <0 表示获取同步状态失败
    if (tryAcquireShared(arg) < 0) {
        doAcquireShared(arg);
    }
}
```

2. doAcquireShared(int arg)

```java
// 加入同步队列、挂起线程
private void doAcquireShared(int arg) {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                // 不同于独占锁的tryAcquire方法返回布尔类型,此处表示当前可用的资源
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    // 获取同步状态成功，修改头结点，并传递唤醒状态
                    setHeadAndPropagate(node, r);
                    p.next = null;
                    if (interrupted)
                        selfInterrupt();
                    failed = false;
                    return;
                }
            }

            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                interrupted = true;
            }
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

```java
// 除了将头结点指向当前节点外，还需要唤醒下一个共享节点（而独占锁相当于propagate一直==0，所以无需唤醒）
private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head; // 记录下旧的队列头
    setHead(node); // 跟独占一样的设置队列头为当前node

    // propagate == 0 表示没有资源可用了，所以不需要再唤醒后续的共享节点了
    if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
        Node s = node.next;
        if (s == null || s.isShared())
            // 唤醒后续节点
            doReleaseShared();
    }
}
```

#### **3.2.2 释放共享同步状态的操作**

1. releaseShared(int arg)
```java
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        // 释放同步状态成功后，通知后续节点
        doReleaseShared();
        return true;
    }
    return false;
}
```

线程在获取共享锁和释放共享锁后，都会尝试唤醒后续节点，都调用了doReleaseShared()方法

2. doReleaseShared()
```java
private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;
                // 唤醒后继节点
                unparkSuccessor(h);
            }
            else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;
        }

        // 在上面唤醒了后续节点后，后续节点会尝试将自己变为头节点
        if (h == head)
            break;
    }
}
```

![](https://upload-images.jianshu.io/upload_images/19073098-cfe598718a9ec517.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

## **4. 可中断/不可中断**

![](https://upload-images.jianshu.io/upload_images/19073098-be2bf86aa0226191.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp)

    不可中断：不改初衷，继续尝试获取锁，获取不到继续阻塞。不论C怎么中断，都无动于衷
    可中断：检测中断是否发生了，若是发生了直接抛出异常(老子不干了，不再尝试获取锁)

### **4.1 可中断的独占锁**

- 在准备获取锁之前检测中断是否发生，若是则抛出异常并中断
- 在被唤醒后检查是否中断已经发生，若是则直接抛出异常，中断获取锁的for循环

```java
public final void acquireInterruptibly(int arg) {
    // 在没有处理中断异常时，不应该吞掉中断标识
    if (Thread.interrupted())
        // 将线程的中断标识重置为false，并向上抛出中断异常
        throw new InterruptedException();
    if (!tryAcquire(arg))
        doAcquireInterruptibly(arg);
}

private void doAcquireInterruptibly(int arg) throw InterruptedException {
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = false;
    try {
        for (;;) {
            // ...
            if (shouldParkAfterFailedAcquire(p, node) && parkAndChecktInterrupt())
                // 此处处理了中断异常，所以在parkAndCheckInterrupt中可以清理线程的中断标识
                throw new InterruptException();
        }
    } finally {
        // ...
    }
}

private void parkAndCheckInterrupt() {
    // LockSupport.park的底层，发现当前线程的标识为true，则直接返回不挂起线程，所以线程想通过LockSupport.park进行挂起，标识位必须为false
    LockSupport.park(this);

    // thread.isInterrupted(true);
    // 这里必须清理掉标识位。防止当再次获取资源失败时，使用LockSupport.park挂起线程时失败，导致空轮询浪费CPU
    return Thread.interrupted();
}
```

### **4.2 不可中断的独占锁**

```java
public void acquire(int arg) {
    if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUEDE), arg))
        // 自我中断，因为在acquireQueued中仅仅是记录了中断标记，需要在此处处理中断
        selfInterrupted();
}

final boolean acquireQueued(final Node node, int arg) {
    boolean failed = false;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                // ...
                return interrupted;
            }

            if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                // 在此处并没有抛出异常, 只记录了interrupted（因为内层将中断标识清空了）
                interrupted = true;
        }
    } finally {
        // ...
    }
}
```

### **4.3 可中断的共享锁**

与可中断独占锁逻辑一致

### **4.4 不可中断的共享锁**

共享锁的不可中断与独占锁一致，只是将自我中断的逻辑挪了个位置，不用传递标识位到最上层了

```java
private void doAcquireShared(int arg) {
    final Node node = addWait(Node.SHARED);
    boolean failed = false;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    // ...
                    if (interrupted)
                        // 直接在里层进行自我中断
                        selfInterrupt();
                }
            }
        }
    } finally {
        // ...
    }
}
```

## **5. 可限时/不可限时**

### **5.1 不限时**

- acquire(int arg) 
    -> acquireQueued(Node node, int arg)
    -> doAcquireInterruptibly(int arg)

- acquireShared(int arg)
    -> doAcquireShared(int arg)
    -> doAcquireSharedInterruptibly(int arg)

### **5.2 限时等待**

- tryAcquireNanos(int arg, long nanosTimeout)
- tryAcquireSharedNanos(int arg, long nanosTimeout)

    吐槽下。。。其他模板方法又是以try开头的，结果限时方法也是以try开头，但又不是模板方法。。

限时等待的唤醒方式有两种：
- 上层调用该线程的中断方法
- 挂起限时时间到达

```java
public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
}

private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
    if (nanosTimeout <= 0L)
        return false;
    final long deadline = System.nanoTime() + nanosTimeout;
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = false;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null;
                failed = false;
                return true;
            }

            nanosTimeout = deadline - System.nanoTime();
            if (nanosTimeout <= 0L) {
                // 计算剩余的时间，时间耗尽则直接返回
                return false;
            }

            if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold)
                // 睡眠设定的时间后醒来
                LockSupport.parkNanos(this, nanosTimeout);
            if (Thread.interrupted())
                throw new InterruptedException();
        }
    } finally {
        // ...
    }
}
```

共享锁的限时等待与独占锁基本一致

## **6. 等待/通知**

![等待队列数据结构](https://upload-images.jianshu.io/upload_images/19073098-4c5c484ee688e0bd.png?imageMogr2/auto-orient/strip|imageView2/2/w/968/format/webp)

在单纯地使用锁时，比如ReentrantLock，加锁、释放锁只是涉及到AQS中的同步队列

当需要获得锁的线程需要暂时释放锁，可以使用Condition，此时才涉及到等待队列的概念，Condition的获取一般要与一个锁Lock相关，一个锁上面可以生产多个Condition

### **6.1 Node**

等待队列与同步队列共用Node结构，不同于同步队列的是，等待队列的节点使用nextWaiter指针指向下一个节点，且队列并非双向链表

```java
static final class Node {
    static final Node SHARED = new Node();
    static final Node EXCLUSIVE = null;
    Node nextWaiter;
    Thread thread;

    Node(Thread thread, Node mode) { // Used by addWaiter
        this.nextWaiter = mode;
        this.thread = thread;
    }
}
```

### **6.2 ConditionObject**

一个AQS可以对应多个ConditionObject

```java
public class ConditionObject implements Condition {
    private transient Node firstWaiter;

    private transient Node lastWaiter;

    public void await() {
        // 不限时等待
    }

    public void await(long time, TimeUnit timeUnit) {
        // 限时等待，指定时间单位
    }

    public void awaitNanos(long nanosTimeout) {
        // 限时等待，时间单位限定为纳秒
    }

    public void awaitUninterruptibly() {
        // 不可中断的不限时等待
    }

    public void awaitUntil(Date deadline) {
        // 限时等待,指定超时时间为未来的某个时间点
    }

    public void signal() {
        // 通知等待队列里的节点
    }

    public void signalAll() {
        // 通知等待队列里所有的节点
    }
}
```

获取到锁资源的线程如果需要等待条件满足，可以调用Condition实例的awaitXX(XX)方法进入等待队列中，并且释放锁资源

其他线程在完成等待条件后，在锁内调用signalXX()通知线程A条件满足，线程A进入同步队列重新加入锁竞争

### **6.3 conditionObject.await()**

该方法**在获取到独占锁资源时才可调用**（这也意味着肯定不在同步队列中），当使用await时，会将其独占的资源释放掉，并加入等待队列的队尾

- 释放独占资源
- 加入等待队列的队尾
- 挂起线程，等待中断或被其他线程signal唤醒
- 唤醒后调用acquireQueued进入锁竞争

```java
public final void await() throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    Node node = addConditionWaiter(); // (1)
    int savedState = fullyRelease(node); // (2)
    int interruptMode = 0;
    while (!isOnSyncQueue(node)) { // (3)
        LockSupport.park(this);
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) // (4)
            break;
    }
    if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interrputMode = REINTERRUPT;
    if (node.nextWaiter != null)
        unlinkCancelledWaiters(); // (5)
    if (interruptMode != 0)
        reportInterruptAfterWait(interruptMode); // (6)
}
```

1. addConditionWaiter()

构造一个等待队列节点，**并添加到等待队列的队尾**

```java
private Node addContionWaiter() {
    // 等待队列的队尾
    Node t = lastWaiter;
    if (t != null && t.waitStatus != Node.CONDITION) {
        // 发现队尾节点的等待状态不为Condition，则遍历等待队列移除被取消的节点
        unlinkCancelledWaiters();
        t = lastWaiter;
    }

    // 构造等待队列的节点，使用Node(Thread thread, int waitStatus)构造器
    Node node = new Node(Thread.currentThread(), Node.CONDITION);
    if (t == null)
        // 若队列为空，则头指针指向当前节点
        firstWaiter = node;
    else
        t.nextWaiter = node;
    // 尾指针指向尾节点
    lastWaiter = node;
    return node;
}
```

2. fullyRelease(Node node)

释放独占的所有资源

```java
final int fullyRelease(Node node) {
    boolean failed = true;
    try {
        int savedState = getState(); // 获得当前AQS的资源状态（一定是独占）
        if (release(saveState)) { // 将独占的资源全部释放掉
            failed = false;
            return saveState;
        } else {
            // release返回false，说明这个同步器不是独占模式的（Semaphore）
            throw new IllegalMonitorStateException();
        }
    }
}
```

3. isOnSyncQueue(Node node)

判断是否在同步队列中

```java
final boolean isOnSyncQueue(Node node) {
    // node.prev == null, 一定不在同步队列了
    if (node.waitStatus == Node.CONDITION || node.prev == null)
        return false;

    // If has successor, it must be on queue
    if (node.next != null)
        return true;

    // 第一个if，node.prev不为空，第二个if，node.next为空，此时的node可能正处于addWaiter的CAS操作失败的情况，需要调用findNodeFromTail确保是否真的还在同步队列中（doSignal的enq方法）
    return findNodeFromTail(node);
}
```

4. checkInterruptWhileWaiting(Node node)

检查在await的while期间，线程是否发生了中断，是的话需要记录中断位进行处理

```java
private int checkInterruptWhileWaiting(Node node) {
    return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
}

final boolean transferAfterCancelledWait(Node node) {
    if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
        // CAS失败，说明还没有被signal，需要把异常再往上抛
        enq(node);
        return true;
    }

    // CAS成功，说明已经被其他线程signal了，需要返回中断标记，让外层决定如何处理
    while (!isOnSyncQueue(node))
        Thread.yield();
    return false;
}
```

5. unlinkCancelledWaiters()

在没有signal的情况下，节点可能会因为中断而加入同步队列中，此时并未从等待队列中移除，需要删除一下

6. reportInterruptAfterWait(int interruptMode) 
```java
private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
    if (interruptMode == THROW_IE)
        // 说明在await的while过程中被中断，且未被signal 
        throw new InterruptedException();
    else if (interruptMode == REINTERRUPT)
        // 说明在await的while过程被中断，但已被signal / 在acquireQueued中被中断
        selfInterrupt();
}
```

### **6.4 conditionObject.signal()**

该方法只能在线程获取到**独占资源**时使用，会**唤醒等待队列的头节点**,
并将该节点**加入到同步队列的队尾**

- 等待队列队头出队
- 节点加入到同步队列队尾
- 若节点的前驱节点为取消状态，或修改前驱节点状态失败，则唤醒当前节点

```java
public final void signal() {
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    Node first = firstWaiter;
    if (first != null)
        // 唤醒等待队列的第一个节点
        doSignal(first);
}

private void doSignal(Node first) {
    do {
        // (firstWaiter = first.nextWaiter)从等待队列中出队
        if ((firstWaiter = first.nextWaiter) == null)
            // 如果队列只有一个节点，并且还被出队了，则把尾指针也置空
            lastWaiter = null;
        first.nextWaiter = null;
    } while (!transferForSignal(first) && (first = firstWaiter) != null); // transferForSignal可能会失败，因为first节点可能在fullyRelease失败被取消，但fullyRelease方法的之前又将其加入了等待队列中
}

final boolean transferForSignal(Node node) {
    // 在fullyRelease中失败了
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
        return false;

    // 将该节点加入到同步队列的队尾,并返回前驱节点
    Node p = enq(node);
    int ws = p.waitStatus;
    // 如果前驱节点为取消状态，或者前驱节点的状态设置为SIGNAL失败，将无法唤醒node的线程
    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        // 直接唤醒该节点
        LockSupport.unpark(node.thread);
    return true;
}
```

## **7. 个人认为的重点**

1. shouldParkAfterFailedAcquire(Node pred, Node node, int arg)

    该方法出现的场景在各大获取资源的方法中，最重要的作用就是将前驱节点的状态设置为SIGNAL，使得release的时候，可以调用unparkSuccessor唤醒后续节点

    另一个作用就是将线程挂起，避免空轮询操作

2. unparkSuccessor(Node pred, Node node, int arg)

    唤醒后续状态不为取消的节点：
    - **在共享锁下，获取/释放资源成功时，都会调用该方法**
    - **在独占锁下，只有释放资源成功时，才会调用该方法**
    
    节点在被唤醒后会在acquiredQueued/doAcuqireInterruptibly/doAcquireShared/doAcquireSharedInterruptibly方法中重新获取资源，在成功获取资源后设置自己为head节点，即出队

3. acquiredQueued/doAcuqireInterruptibly/doAcquireShared/doAcquireSharedInterruptibly

    这几个方法就是acquire的核心，最主要的是是否要忽略中断，以及衍生出来的可不可限时方法

4. release

    释放资源，当节点的状态不等于0时（SIGNAL = -1）尝试唤醒后续节点，这个

5. acquiredQueued和conditionObject.await(), enq和conditionObject.signal()方法的联动

    联动来实现等待/通知机制：
    1. await方法会在等待队列的节点出队并加入同步队列后，使用acquiredQueued驱动该节点的锁竞争
    2. 等待队列队头出队，并加入到同步队列的队尾

6. PROPAGATE的作用

PROPAGATE 在共享节点时才用得到，假设现在有4个线程、A、B、C、D，A/B 先尝试获取锁，没有成功则将自己挂起，C/D 释放锁

    注意：共享锁的唤醒和释放，都需要唤醒后续的节点，当被唤醒的节点获取资源后，会接着唤醒下一个节点，做到传播的效果

此时同步队列：

    head(SIGNAL) -> A(SIGNAL) -> B(0)

可以参照Semaphore获取/释放锁流程

1. C 释放锁后state=1，设置head.waitStatus=0，然后将A唤醒，A醒过来后调用tryAcquireShared(xx)，该方法返回r=0(剩余许可证数)，此时aqs.state=0(semaphore的许可证数)

    此时同步队列：

        head(0) -> A(SIGNAL) -> B(0) // A还在队列中，因为还没调用setHeadAndPropagate

2. 在A还没调用setHeadAndPropagate(xx)之前，D 释放锁，此时D调用doReleaseShared()，发现head.waitStatus==0，所以没有唤醒其它节点

    此时同步队列：

        head(0) -> A(SIGNAL) -> B(0)

3. 此时A调用了setHeadAndPropagate(xx)，因为r==0且head.waitStatus== 0，因此不会调用doReleaseShared()，也就没有唤醒其它节点。最后导致的是B节点没有被唤醒，只有等到A释放锁时再进行唤醒了

    此时同步队列：

        headA(SIGNAL) -> B(0)

若是加了PROPAGATE状态，在上面的第2步骤里的D调用doReleaseShared()后，发现head.waitStatus==0，于是设置head.waitStatus=PROPAGATE，在第3步骤里，发现
```java
private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head; // Record old head for check below
    setHead(node);

    // 发现第三个条件h.waitStatus(PROPAGATE) < 0, 这个h是记录的旧h
    if (propagate > 0 || h == null || h.waitStatus < 0 ||
        (h = head) == null || h.waitStatus < 0) {
        Node s = node.next;
        if (s == null || s.isShared())
            doReleaseShared();
    }
}
```
此时同步队列变为：

    head(PROPAGATE) -> A(SIGNAL) -> B(0)
    =>
    headA(SIGNAL) -> B(0)

于是在doReleaseShared中唤醒B

```java
private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            // 此时的h是在setHeadAndPropagate后的新head，他的状态是SIGNAL（在acquireShared方法的时候，被节点B设置为了SIGNAL）
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;            // loop to recheck cases
                unparkSuccessor(h);
            }
            // ...
        }
    }
}
```
最后同步队列变为：

    headA(0) -> B(0)
    =>
    headA(PROPAGATE) -> B(0)

虽然在第2步骤里没有唤醒任何线程，但是设置了PROPAGATE状态，在后续的步骤中发现已经设置了PROPAGATE，于是唤醒，这也是PROPAGATE名字的意义：传播

## **8. WaitNode的状态更迭**

队列入队和出队都通过循环获取方法，并由AQS对线程的阻塞/唤醒进行控制

循环获取方法主要通过setHead/setHeadAndPropagate方法、addWaiter方法（enq）进行实现:
- setHead/setHeadAndPropagate：出队，由于release释放资源而触发
- addWaiter：入队，由acquire而触发

独占模式下，只有在释放资源会唤醒后续线程；共享模式下，在释放资源和新的线程竞争到资源时，都会唤醒后续线程，因为资源不只有一份

    独占模式释放资源时只唤醒后续节点
    共享模式释放资源时不只唤醒后续节点，还会通过设置头节点为PROPAGATE状态，来辅助传播

## **8.1 独占模式**

独占模式下，AQS的state会被占用，其他线程将会以WaitNode的形式加入到同步队列中

下面通过基本场景，描述WaitNode在队列中的状态更迭：

    场景：ReentrantLock资源被占用，共5个线程在队列排队

        -1（SIGNAL） -> -1（SIGNAL） -> -1（SIGNAL） -1（SIGNAL） -> 0（初始化默认值）

### **8.1.1 正常排队并释放**

独占模式下，加入到和移除出同步队列的方法有：
- acquireQueued(Node, int)
- doAcuqireInterruptibly(int)
- doAcquireNanos(int, long)

1. 场景：有新的线程需要获取**公平锁**资源

    Reentrant.FairSync.tryAcquire会根据队列是否有节点，决定是否排队

    -1 -> -1 -> -1 -> -1 -> -1 -> 0(新线程将上一个节点的状态变为SIGNAL，并入队)

2. 场景：有新的线程需要获取**非公平锁**资源

    Reentrant.NonfairSync.tryAcquire，跟队头线程进行竞争

    跟队头线程竞争成功，无须入队：
    -1 -> -1 -> -1 -> -1 -> 0

    竞争失败，入队：
    -1 -> -1 -> -1 -> -1 -> -1 -> 0(新线程，排队)

3. 场景：释放锁(release)

    调用unparkSuccessor方法，唤醒队列的第一个状态不为CANCELLED的线程节点。队头被唤醒后会重新进入acquireQueued或其他循环方法中，将自己设置为head，即出队

    -1 -> -1 -> -1 -> 0

### **8.1.2 独占模式的中断（cancelAcquire）**

1. 场景：只对第一个线程进行中断

    第一个线程节点状态变为CANCELLED：
    1（CANCELLED） -> -1 -> -1 -> -1 -> 0

    发现是队头，直接调用unparkSuccessor方法唤醒第二个节点，第二个节点会在循环方法中将自己设置为队列头：
    -1 -> -1 -> -1 -> 0

2. 场景：只对最后一个线程进行中断

    最后一个线程节点状态变为CANCELLED：
    -1 -> -1 -> -1 -> -1

3. 场景：对队列第三个线程进行中断，第四个（下一个）线程节点不为CANCELLED

    第三个线程节点状态变为CANCELLED:
    -1 -> -1 -> 1（CANCELLED） -> -1 -> 0

    查看上一个节点是否为SIGNAL，不是的话则CAS设置
    上个节点为SIGNAL并且设置成功，则：

        将第二个（上一个）节点的next链接到第四个节点：
        -1 -> -1 -> -1 -> 0
    
    上个节点不为SIGNAL并且设置失败，说明上个节点可能也被中断了，则：
        
        唤醒第四个（下一个）节点，让它再次进入shouldParkAfterFailedAcquire方法，将取消的节点出队，并将第一个线程的next链接到自身：
        -1 -> -1 -> 0

3. 场景：队列的第三个线程已经处于CANCELLED状态，对队列第二个线程进行中断

    -1 -> -1 -> 1（CANCELLED） -> -1 -> 0

    第二个线程先变为CANCELLED：
    -1 -> 1（CANCELLED）-> 1（CANCELLED）-> -1 -> 0

    不操作，等待第三个（最后一个为取消状态）的线程被唤醒，让它再次进入shouldParkAfterFailedAcquire方法，将取消的节点出队，并将第一个线程的next链接到自身：
    -1 -> -1 -> 0

### **8.1.3 独占模式状态总结**

1. 独占

    需要注意**公平/非公平**的问题，ReentrantLock提供了两种方式的实现

    公平锁必须入队，非公平锁可以跟队头进行竞争，失败再入队

2. 中断

    cancelAcquire方法为入口方法，它主要的功能是将线程节点的状态修改为CANCELLED。但是它只在下一个节点不为CANCELLED时，才会将当前节点出队

    当遇到**中断节点是队头节点**或**多个节点同时被中断**时，会借助**unparkSuccessor**方法对后续节点进行唤醒，所以还要重点关注循环方法（acquireQueued/doAcquireInterruptibly/...），因为线程在被唤醒（中断）后，会在这些方法中再走一遍**shouldParkAfterAcquireFailed**，移除掉取消的节点

## **8.2 共享模式**

共享模式下，加入到和移除出同步队列的方法有：
- doAcquireShared(Node, int)
- doAcuqireSharedInterruptibly(int)
- doAcquireSharedNanos(int, long)

它们跟独占模式方法的不同，在于获取资源时，它们也会唤醒后续的线程，通过setHeadAndPropagate中的**doReleaseShared**方法实现

该方法借助head节点的状态，记录了在唤醒线程过程中，可能会丢失的其他释放操作附带的唤醒线程操作。通过在获取资源时的setHeadAndPropagate，进行传播。

下面通过基本场景，描述WaitNode在队列中的状态更迭：

    场景：有A、B、C、D共四个线程，Semaphore总许可证为2，C和D都占用了许可证，剩余为0，A和B因为竞争失败，在队列排队

    A(SIGNAL) -> B(0)

### **8.2.1 正常排队并释放**

1. 场景：新增线程E竞争信号量资源

    A(SIGNAL) -> B(SIGNAL) -> E(0)

2. 场景：C释放资源后，D也释放资源

    C释放资源后，semaphore.state = 1，发现head节点为SIGNAL，设置head节点为0状态，并唤醒A（第一个）线程节点：
    head(0) -> A(SIGNAL) -> B(0)

    A线程被唤醒后，还没调用setHeadAndPropagate方法，此时D也释放资源。注意，D释放资源意味着可以有另一个线程（B）可以获得资源，但是此时正在处理A线程的出队流程，所以需要记录下，让A线程出队（竞争到资源）后传播一下

    将head设置为PROPAGATE进行记录：
    head(PROPAGATE) -> A(SIGNAL) -> B(0)

    A节点获取到资源，将自身设置为head，即出队：
    headA(SIGNAL) -> B(0)

    并读取之前的head(PROPAGATE)的状态，如果是PROPAGATE，则通过doReleaseShared进行传播，将head设置为0：
    headA(0) -> B(0)

    唤醒后续节点,B也获得了Semaphore资源：
    headB(0)


### **8.2.2 共享模式的中断**

与独占模式基本一致，不再阐述

# 参考
- [ReentrantLock.java]()
- [AbstractQueuedSynchronizer.java]()
- [CountDownLatch.java]()
- [Java多线程 J.U.C之locks框架：AQS综述](https://blog.csdn.net/wuxiaolongah/article/details/114435974)
- [Java并发之 AQS 深入解析(上)](https://www.jianshu.com/p/62ed0767471e)
- [Java并发之 AQS 深入解析(下)](https://www.jianshu.com/p/7a3033143802)
- [线程中断 interrupt 和 LockSupport](https://blog.csdn.net/qq_26542493/article/details/104602385)
- [并发系列(十)-----AQS详解等待队列](https://blog.csdn.net/MyPersonalSong/article/details/84386935)
- [同步器AQS中的同步队列与等待队列](https://blog.csdn.net/gs_albb/article/details/88904205)