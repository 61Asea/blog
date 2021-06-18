# Mysql（四）：运作机制

> [Mysql（一）：innodb存储](https://asea-cch.life/achrives/innodb存储)
> [Mysql（二）：索引](https://asea-cch.life/achrives/索引)
> [Mysql（三）：锁机制](https://asea-cch.life/achrives/锁机制)

上两篇文章主要介绍了innodb的外存存储和索引机制，我们深刻地感受到I/O操作对于系统的性能至关重要，Mysql使用B+树组织数据，并贯彻系统局部性原理，最小操作单位为一个页，以尽可能地减少对库操作时I/O的次数

那么在软件层面上，Mysql运作机制又是什么？

![逻辑架构](https://asea-cch.life/upload/2021/06/%E9%80%BB%E8%BE%91%E6%9E%B6%E6%9E%84-4702c42811ee452fa842577bf56fd56a.png)

Buffer Pool对应图中的**查询缓存**，处于内存区域，是数据库对磁盘读写进行缓存加速的机制。它使得操作不会立即反馈到磁盘上，而是先操作缓存，极大程度提高数据的访问速度

![缓冲池和日志文件结构](https://asea-cch.life/upload/2021/06/%E7%BC%93%E5%86%B2%E6%B1%A0%E5%92%8C%E6%97%A5%E5%BF%97%E6%96%87%E4%BB%B6%E7%BB%93%E6%9E%84-b4d927e2591d465197630da28169e012.jpg)

上图中与磁盘文件平行的，就是数据库的日志系统，它是数据库的重要保护机制，数据库事务通过日志系统与锁机制共同实现原子性、持久性和隔离性，进而保证了业务数据的一致性

# **1. Buffer Pool**

![bufferPool结构](https://asea-cch.life/upload/2021/06/bufferPool%E7%BB%93%E6%9E%84-ab9d2f8d4e194de388ef16c3df61eaf7.jpg)

## **1.1 缓存页和描述信息**

## **1.2 **

# **2. 日志系统**

# 参考
- [理解Mysql中的Buffer pool](https://www.cnblogs.com/wxlevel/p/12995324.html)
- [详解MySQL中的缓冲池](https://blog.csdn.net/weixin_42305622/article/details/113424622)
- [Mysql Buffer Pool](https://blog.csdn.net/qq_27347991/article/details/81052728)

- [MySQL日志系统：redo log、binlog、undo log 区别与作用](https://blog.csdn.net/u010002184/article/details/88526708)

