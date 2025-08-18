#! https://zhuanlan.zhihu.com/p/497994914
# AQS：CLH队列

**本文已收录至个人博客：** https://github.com/61Asea/blog

> AbstractQueuedSynchronizer（AQS），为ReentrantLock、Semaphore、CountDownLatch等同步工具提供基础实现，底层是`CLH队列`的sleep-waitting版本。

CLH队列：基于链表的自旋锁队列，每个线程在等待锁时，会自旋地等待它的前驱节点释放锁。
- 核心思想：每个线程只监视前一个节点的状态，而不是竞争共享变量。
- 特点：非阻塞，线程自旋不挂起。

## **朴素CLH自旋锁**

```java
// CLH自旋锁
public class CLH {
    // CAS设置tail，保证tail的正确获取顺序
    private final AtomicReference<Node> tail;

    private final ThreadLocal<Node> current;

    private final ThreadLocal<Node> pred;

    public CLH() {
        tail = new AtomicReference(new Node());
        current = ThreadLocal.withIntial(new Node());
        pred = new ThreadLocal<>();
    }

    private final CLH ins = new CLH();

    static class Node {
        volatile boolean locked = false;
    }

    static class TestTask implements Runnable {
        @Override
        public void run() throws InterruptedException {
            try {
                ins.lock();
            } catch (InterruptedException ex) {
                e.printStackTrace();
            } finally {
                ins.unlock();
            }
        }
    }

    public void lock() throws InterruptedException {
        Node c = current.get();
        c.locked = true;

        Node prev = tail.getAndSet(c);
        pred.set(prev);

        // 自旋判断前一个节点的状态
        while (prev.locked) {
            // 1. 空操作；busy-wait空轮循

            // 2. Thread.sleep(100); sleep-wait线程阻塞刮起
        }
    }

    public void unlock() {
        Node c = current.get();
        c.locked = false;
        current.set(pred.get());
    }

    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            new Thread(new TestTask()).start();
        }
    }
}
```

结论：CLH自旋锁为`busy-waiting`，AQS为`sleep-waiting`，AQS的队列节点由前驱节点唤醒，替代朴素CLH队列的后驱节点自旋判断

**问题：为什么AQS要使用CLH队列，而不用普通链表来实现？**

CLH队列本质是一个双向链表，相比较单向链表，双向链表在AQS的这两个方面需求更具优势：
1. 中断取消节点
2. 唤醒阻塞判断

首先，中断节点需要删除掉该节点，如果是单向链表删除节点的话会相当复杂，涉及从头到尾便利

其次，每个节点在被中断唤醒时，只代表着恢复执行代码逻辑的能力，而不是代表已经获取到锁，这时需要根据必要条件判断是否已经轮到了自己（判断prev节点是否为head节点，是的话尝试获取锁）。此时如果是单向链表，获取prev节点就十分麻烦。

## **AQS的CLH队列实现**

AQS本质是一个同步器的模板，其抽象出了state变量，根据对state变量的定义不同，可以实现出不同的同步工具（如显示锁ReentrantLock、Semaphore、CountDownLatch、CyclicBarrier）

组成：双向链表（**dummyHead**，**tail**）、队列元素Node（waitStatus、prev、next、thread）、AQS#state变量、入队、出队（唤醒后继节点机制）

> 注意点：链表的头节点是一个dummy节点，这个dummy不是一成不变的。根据当前线程释放锁后，dummy后的节点被唤醒并竞争锁，若成功则将该节点变成新的dummy节点。dummy的状态可能会被其后继节点修改为SIGNAL。

1. 入队（以非公平为例）： 

    - 若获取锁失败，则需要入队。AQS通过**CAS**设置队尾的方式，低成本管理线程入队。

        ```java
        // AbstractQueuedSynchronizor.class
        public final boolean acquire(int arg) {
            Node node = new Node(Thread.currentThread(), mode);
            Node pred = tail;
            if (pred != null) {
                node.prev = pred;
                // CAS设置新的队尾
                if (compareAndSetTail(pred, node)) {
                    pred.next = node;
                    return node;
                }
            }
            // 上述设置队尾失败，进入enq()中自旋设置
            enq(node);
            return node;
        }
        ```

    - 每个节点入队后，需要保证**前驱节点状态是SIGNAL**，自己才可以进入阻塞状态。在ownner线程（持锁线程）执行完毕后，双向链表的第一个节点（dummy后的节点）SIGNAL状态作为是否唤醒后驱节点的依据。

        ```java
        // AbstractQueuedSynchronizor.class
        public final boolean release(int arg) {
            if (tryRelease(arg)) {
                Node h = head;
                // h.waitStatus在
                if (h != null && h.waitStatus != 0)
                    unparkSuccessor(h);
                return true;
            }
            return false;
        }
        ```

2. 出队

    - 当前持锁线程解锁，调用release()方法触发唤醒逻辑

        ```java
        public final boolean release(int arg) {
            if (tryRelease(arg)) {
                Node h = head;
                // head是dummy节点，需要唤醒的是dummy后的第一个节点
                // h != null && h.waitStatus != 0，代表了以下三个情况无需唤醒
                // 1. 队列为空（从未产生过并发竞争锁）
                // 2. 队列不为空，但是只有dummy节点（之前发生过竞争，但是现在没有线程在阻塞等待）
                // 3. 队列不为空，也不只有dummy节点，但是后续的节点还没进入阻塞状态，没有设置dummy的状态为SIGNAL。
                if (h != null && h.waitStatus != 0)
                    unparkSuccessor(h);
                return true;
            }
            return false;
        }
        ```

    - 回到之前线程阻塞的代码逻辑（acquireQueued）：

        ```java
        final boolean acquireQueued(final Node node, int arg) {
            boolean failed = true;
            try {
                boolean interrupted = false;
                for (;;) {
                    // 重新被唤醒，判断前驱节点是否为dummy，是的话自己尝试获取锁
                    final Node p = node.predecessor();
                    if (p == head && tryAcquire(arg)) {
                        // 成功获取锁，将自己设置为新的dummy（waitStatus=0）
                        setHead(node);
                        p.next = null; // help GC
                        failed = false;
                        return interrupted;
                    }
                    if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                        interrupted = true;
                }
            } finally {
                if (failed)
                    cancelAcquire(node);
            }
        }
        ```

> 可以类比synchronized的on deck和waiting queue结构，waiting queue（collection list和entry list），on deck对应了队列的第二个节点，对应CLH队列除开第二个节点之后的部份。

难点：

- 入队、出队操作对应的方法入口

- 获取、释放资源两种操作的并发安全保证。由`AQS#acquiredQueued()#tryAcquired()`方法和`AQS#release()#tryRelease()`对state的CAS操作保障

- 理解队列中的节点状态、dummy节点

    - 后驱线程被挂起前，会先检查前驱节点的状态，确保设置成SIGNAL后才阻塞，否则会出现无法唤醒的情况
    
    - 后驱线程阻塞，则队列必定有值，前驱线程在完成任务后根据SIGNAL/PROPAGATE标识，若有则唤醒后驱线程。

- 唤醒的方式：独占模式**只在释放资源时唤醒**，传播模式在**获取资源和释放资源**时都会唤醒后续线程

    传播模式原理：每个线程被唤醒后，都会**根据当前的资源数是否足够**，来决定是否唤醒其后一个节点的线程

## **ReentrantLock**

独占锁，使用AQS`独占模式`实现，state表示**标识位和计数器**：
- = 0：可用
- \>= 1：有线程独占，大于1时表示线程有重入锁范围的逻辑

与synchronized没有本质区别，在有竞争时线程一样会**阻塞**，且一样**支持可重入**，额外支持**公平锁模式**和**获取尝试**

- 公平锁：在竞争资源时，发现资源可持有后不会立即独占资源，而是判断等待队列是否有其他等待的线程，若有则进入队列中排队，有效防止`线程饥饿`问题

- 可重入锁：如果发现同步器**此时的独占线程为当前线程**，则直接将资源数加1，并重新进入代码块

> 若没有可重入特性，当前线程在获得锁资源后，尝试再次获取同个锁资源时，会形成自己锁自己的现象

- 非公平锁：与synchronized一致，当有新来线程竞争资源时，会与队头第二个节点线程进行争夺，如果失败则进入队列尾，成功则意味着第二个节点需要继续排队，造成线程饥饿问题

- 性能（与synchronized）：与synchronized相比有小幅度提升，ReentrantLock在竞争失败后不会立即挂起，而是会转而加入CLH队列，并`在前驱节点为队头的情况`下，再次尝试执行`两次CAS竞争`（进入队列后先执行一次CAS，设置前驱节点状态为-1后再竞争一次），这种做法在竞争不太激烈的情况下会有更高的性能表现

> synchronized最糟的情况莫过于锁最终膨胀为重量级锁，**整个开销包括了锁的升级过程**

## **Semaphore**

信号量，使用AQS`共享模式`实现，state表示**许可证**：

- = 0：不可用，许可证不足
- \>= 1：可用，最多还能有

**入口**：

- Semaphore(int count)：传入许可证的个数，后续许可证的获取和返还操作下，个数在区间\[0, count\]

- acquire(int)：`剩余值 = 当前许可证量 - 需要获取的值`，如果剩余值小于0，则进入同步阻塞队列，否则通过CAS设置当前信号量为剩余值，同时返回剩余值

- release(int)：**返还**当前信号量计数器传入个数个许可证，公平模式下如果没有出现与后驱线程竞争成功的线程，才会唤醒后续线程进行**传播**

**应用：** 

1. 限速，控制应用速率

2. 反向使用也可达到阻塞效果，效果类似于闭锁的第二个情况

## **CountDownLatch**

闭锁，使用AQS`共享模式`实现，state表示**闭合开关（倒数状态）**：

- = 0：打开闭合开关，此时在队列中的所有线程都将被唤醒
- \>= 0：关闭闭合开关，此时获取该闭锁资源的线程都将进入到CLH队列中

**入口**

- CountDownLatch(int count)：传入闭合开关的大小

- await()：`sync.acquireSharedInterrupted(1)`，如果闭合开关处于开启状态，则直接进入阻塞状态，否则直接执行

- countDown()：`sync.releaseShared(1)`，每调用一次闭合开关的数量就减1，当减至0时闭合开关**关闭**

**应用**：

1. 所有参与者一同执行任务

    执行countDown()的作为观察者线程（此时构造函数通常传入1），参与者到达闭锁后执行await()方法阻塞等待，观察者可以在恰当的时间点打开闭锁

2. 某个执行任务需要等待所有参与者

- 执行await()的作为任务执行线程（此时构造函数通常传入参与者个数），参与者到达闭锁后执行countDown()方法并继续执行其任务，直到闭锁开关关闭，任务执行线程才开始执行任务

## **CyclicBarrier**

栅栏，使用内置lock（Reentrant）的trip（lock的condition）实现线程间的阻塞等待，通过`AQS.state`和`CyclicBarrier.count`两个值共同维护：

- state：表示栅栏内置的显式锁占用情况

    - = 0：当前锁未被独占，线程可以获取该锁后

    - \>= 0：当前锁已被独占

- CyclicBarrier.count：表示当前已参与的个数，配合parties（代表参与的总个数）使用

    - 0 < count <= parties：栅栏的参与个数未到达，所有线程都会进入lock的等待队列中等待

    - count == 0：最后一个参与者到达，执行栅栏回调（若有）后，唤醒当前等待队列中的所有线程，并重置当前迭代

**入口**：

- CyclicBarrier(int parties, Runnable barrierAction)：设置参与者个数和栅栏即将打开前的执行回调

- **await()**：如果栅栏的count未到达0，则调用`trip.await()`进入等待队列等待唤醒，否则执行回调，并调用`trip.signAll()`唤醒所有线程

> condition.await()对应obj.wait()，condition.signal()/signalAll()对应obj.notify()/notifyAll()，线程被唤醒后会加入到同步队列（AQS为WaitNode，Synchronized为waiting queue）中，等待调用notify()的线程执行完毕，并与其他线程竞争（排队）

**应用：** 无需第三方介入的多线程同时进行任务工具，每个参与者不止有阻塞等待逻辑，而且都会参与到栅栏的迭代逻辑中

# **交替执行打印的demo**

```java
package collection;

import java.util.concurrent.Semaphore;

public class A1B2 {

    private Semaphore[] semaphores;

    private volatile int pos;

    private int raceCount;

    private ThreadLocal<Integer> sumCount = ThreadLocal.withInitial(() -> 0);

    public A1B2(char[] output) throws InterruptedException {
        this.semaphores = new Semaphore[output.length];
        for (int i = 0; i < output.length; i++) {
            semaphores[i] = new Semaphore(1);
            semaphores[i].acquire();
        }
    }

    // (0, A) (1, B) (2, C) (3, D)
    public void run(int count, char output) throws InterruptedException {
        int sum = sumCount.get();
        while (sum < 2) {
            // 停顿
            semaphores[count].acquire();

            if (count > 0) {
                Thread.sleep(1000);
            }

            if (count == pos) {
                pos = (count + 1) % semaphores.length;
                System.out.println(output);
                sumCount.set(sum++);
            }

            // 释放下一序列的线程
            semaphores[pos].release();
        }
    }

    public void start() {
        semaphores[0].release();
    }

    public static void main(String[] args) throws InterruptedException {
        char[] output = {'A', 'B', 'C', 'D'};
        A1B2 a1B2 = new A1B2(output);
        for (int i = 0; i < output.length; i++) {
            int j = i;
            new Thread(() -> {
                try {
                    a1B2.run(j, output[j]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        a1B2.start();
    }
}
```

# **面试 问题**

1. AQS是什么？

    - 双向链表组成的waiting queue
    - 队列节点Node的状态（SIGNAL、CANCEL、PROGRATE）
    - AQS的CLH状态state变量（定制ReentrantLock、Semaphore等）
    - 唤醒线程的机制（由持有锁线程在release资源时，访问头节点进行唤醒）

2. 非公平可重入锁和公平可重入锁

    指通过实现AQS的RenntrantLock的两种模式，两者的区别在于竞争锁资源的线程是否直接进入队列排队
    
    具体代码：ReentrantLock#FairSync对tryAcquire(int)的实现，调用了AQS#hasQueuedProcessors()方法，判断是否有排队的线程

3. AQS的设计模式：模板方法设计模式

    **模板方法必须实现，在模板方法默认返回throws Exception；钩子方法非必须实现，更加体现可加入性的回调**

4. AQS（LockSupport/cpu）是否存在虚假唤醒

    - 多线程虚假唤醒
        
        - 描述：指唤醒机制是唤醒很多处在阻塞态下的线程，但只有部分（甚至只有一个）线程是有效的，其它线程是无效的通知

        - 解决方案：
        
            - 唤醒后的线程会在park()/wait()处继续执行，所以要保证等待函数处于while/for(;;)的判断中，保证可以按照代码的流程**重新走到判断逻辑**

            - 判断逻辑应保证多线程的安全性（获取操作应具备原子性）

    - nio 空轮询：指的是selector.select()可能会无缘无故被wakeUp，跟虚假唤醒是两个概念，后者算是bug

    存在虚假唤醒问题，虽然AQS采用了排队机制，并由持有资源线程唤醒后续线程。但是在非公平模式下，仍旧存在新竞争线程（未处于队列）与队头第一个线程的竞争关系，需要通过for(;;)保证代码流程能走到原子性竞争判断逻辑中

5. 为什么唤醒线程的unparkSuccesor()方法，是从后往前遍历的？

    cas设置尾节点的代码，只能保证新的tail结点的prev一定是安全的，但不能保证旧的tail的next指针已经更新指向到新的tail

6. 怎么理解Node的SIGNAL状态？

    SIGNAL（信物），是持有锁线程与队列线程交互的信物，AQS的唤醒机制是由当前持有锁线程主动唤醒后续线程

    唤醒交互：当有线程加入到等待队列时，往队头节点设置信物标志位，而后续持有锁的线程释放资源时，会根据队头结点的信号标志位，决定是否唤醒时后续节点

# 参考
- [AQS基础——多图详解CLH锁的原理与实现](https://zhuanlan.zhihu.com/p/197840259)

- [为什么AQS唤醒线程要从尾部往前遍历](https://blog.csdn.net/qq_37699336/article/details/124294697)

- [视频：黄俊AQS](https://www.bilibili.com/video/BV19B4y1w7pD?p=4&vd_source=24d877cb7ef153b8ce2cb035abac58ed)