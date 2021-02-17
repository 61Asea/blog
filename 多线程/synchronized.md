# synchronzied

互斥/原子性： 确保线程互斥的访问同步代码

可见性：保证共享变量能及时可见，其实是通过Java的内存模型中的，对一个变量unlock操作之前，必须要同步到主内存中；如果对一个变量进行lock操作，则会清空工作内存中此变量的值，在执行引擎使用该变量前，需要重新从主内存中load或assign操作来初始化变量值

有序性：解决重排序问题，即“一个unlock操作先行发生于（happen-before）后面对同一个锁的lock操作

synchronized内置锁是一个对象锁，作用粒度是对象，可以用来实现对临界资源的同步互斥访问，是可重入的，其重入最大的作用是避免死锁


# 参考
- [synchronized与volatile原理-内存屏障的重要实践](https://www.cnblogs.com/lemos/p/9252342.html)
- [深入分析Synchronized原理(阿里面试题)](https://www.cnblogs.com/aspirant/p/11470858.html)