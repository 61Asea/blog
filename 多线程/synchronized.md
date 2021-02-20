# synchronzied

# 1. 特性（作用）

1. 原子性/互斥性

    字节码指令：MonitorEnter、MonitorExit

    要么全部执行并且执行过程不会被任何因素打断，要么就都不执行，确保线程互斥的访问同步代码，同一时刻只有获得监视器锁的代码可以访问代码

2. 可见性

    字节码指令：load（加载屏障）、store（存储屏障）

    保证共享变量的修改能够及时可见

        对一个变量unlock操作之前，必须要同步到主内存中；如果对一个变量进行lock操作，则将会清空工作内存中此变量的值，在执行引擎使用此变量前，需要重新从主内存中load操作或assign操作初始化变量值

3. 有序性



4. 可重入性


互斥/原子性： 确保线程互斥的访问同步代码

可见性：保证共享变量能及时可见，其实是通过Java的内存模型中的，对一个变量unlock操作之前，必须要同步到主内存中；如果对一个变量进行lock操作，则会清空工作内存中此变量的值，在执行引擎使用该变量前，需要重新从主内存中load或assign操作来初始化变量值

有序性：解决重排序问题，即“一个unlock操作先行发生于（happen-before）后面对同一个锁的lock操作

synchronized内置锁是一个对象锁，作用粒度是对象，可以用来实现对临界资源的同步互斥访问，是可重入的，其重入最大的作用是避免死锁


# 参考
- [synchronized与volatile原理-内存屏障的重要实践](https://www.cnblogs.com/lemos/p/9252342.html)
- [深入分析Synchronized原理(阿里面试题)](https://www.cnblogs.com/aspirant/p/11470858.html)
- [](https://blog.csdn.net/qq_36268025/article/details/106137960)
- [就是要你懂Java中volatile关键字实现原理](https://www.cnblogs.com/xrq730/p/7048693.html)