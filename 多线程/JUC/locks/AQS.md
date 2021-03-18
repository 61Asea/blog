# AbstractQueuedSychronizer

AQS，抽象队列同步器，是JDK为”线程同步“提供了一套通用的机制，来**管理同步状态（synchronization state）、阻塞/唤醒线程、管理等待队列**

基于它可以写出JAVA中的很多同步器类，如ReentrantLock显示锁、CountDownLatch栅栏、Semaphore信号量、CyclicBarrier，这些同步器的主要区别就是对**同步状态（synchronization state）的定义不同**

    在ReentrantLock内部，包含了一个Sync对象（extends AQS），这个AQS对象是ReentrantLock实现加锁和释放锁的核心组件

AQS是一种典型的模板方法设计模式，父类定义好骨架和内部操作细节，具体规则由子类去实现

## **1. 模板**

### **1.1 synchronization state（资源）**

在模板方法设计模式下，AQS高度抽象出同步器的关注点：

    如何定义资源？资源是否可以被访问？

所以在子类同步器实现中，都围绕资源（synchronization state）来展开：
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

AQS暴露以下API来让用户解决1.1中提出的第一个问题，资源是否可以被访问

| 钩子方法 | 描述 |
| -----| ----- |
| tryAcquire | 排它获取（资源数）|
| tryRelease | 排它释放（资源数）|
| tryAcquireShared | 共享获取（资源数）|
| tryReleaseShared | 共享释放（资源数）|
| isHeldExclusively | 是否排它状态 |

在ReentrantLock中，通过组合Sync(extends AQS)，并重写Lock接口方法的方式，做到统一暴露Lock接口方法tryLock，但实则运用了AQS提供的tryAcquire模板方法，即Sync的tryAcquire实现


# 参考
- [Java多线程 J.U.C之locks框架：AQS综述](https://blog.csdn.net/wuxiaolongah/article/details/114435974)