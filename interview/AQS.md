# Interview：AQS

AbstractQueuedSynchronizer，为ReentrantLock、Semaphore、CountDownLatch等同步工具提供基础实现，是`CLH队列`的一种变体实现，抽象出**同步器的state（volatile）**提供定义进入CLH队列的规则：

1. 使用**CAS+volatile**方式来管理等待线程的入队出队

2. 每个节点入队后对会**尝试修改前一个节点的状态**

3. 在可以竞争到资源时（当前仅当线程节点处于head之后，且尝试获取资源成功）挂起

4. 竞争到资源的节点，在执行完毕后，会将自身节点的状态修改为0，并唤醒后续的节点

5. 线程被唤醒后，意味着队头线程已经执行完毕，将队头线程出队

> 类比：synchronized的waiting queue，collection list和entry list对应除了队头和第二个节点的CLH队列部分，on deck对应第二个节点

难点：

- 释放和唤醒的并发安全性，由`AQS#acquiredQueued()#tryAcquired()`方法和`AQS#release()#tryRelease()`对state的CAS操作保障

    当前驱线程不唤醒后驱线程，则肯定资源已被释放，后续线程可以获得资源不被阻塞
    
    如果后驱线程阻塞，则队列必定有值，前驱线程在完成任务后必定会唤醒后驱线程

- 唤醒的方式：独占模式唤醒、传递模式唤醒

    前者只会唤醒队头节点的后一个节点，后者会根据**剩余资源数**来唤醒队头节点的后N个节点

## **ReentrantLock**

与synchronized没有本质区别，在竞争激烈的时候线程一样会阻塞，一样支持可重入，额外支持**公平锁模式**和**获取尝试**

公平锁：在竞争资源时，发现资源可持有后不会立即独占资源，而是判断等待队列是否有其他等待的线程，若有则进入队列中排队，有效防止`线程饥饿`问题

## ****