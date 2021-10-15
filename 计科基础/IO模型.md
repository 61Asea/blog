# IO模型

> [《文件传输》](https://asea-cch.life/achrives/文件传输)：介绍了现代文件传输的流程，其中包括DMA、零拷贝、kernel pagecache等概念。得出结论，大文件传输使用异步I/O（直接I/O），少量数据则使用零拷贝（缓存I/O）技术

I/O发生与等待的步骤，一定是基于以下两个立足点：
1. kernel数据准备（DMA拷贝）
2. 将数据从内核拷贝到进程中（CPU拷贝）

![blockingio](https://asea-cch.life/upload/2021/08/blockingio-234da8a48f0c42e39a67011bae148b78.png)

- 同步IO：I/O**发生期间**，进程会被阻塞

    > A synchronous I/O operation causes the requesting process to be blocked until that **I/O operation** completes

- 异步IO：I/O**发生期间**，进程不会被阻塞住

    > An asynchronous I/O operation does not cause the requesting process to be blocked

# **1. 五大模型**

本文以**网络传输I/O为上下文**，继续围绕`内核空间`和`进程空间`，在`进程的视角`上总结5大模型：
- 阻塞I/O（Blocking I/O）

    同步阻塞I/O，两个过程都会阻塞

- 非阻塞I/O（NonBlocking I/O）

    同步非阻塞I/O，第二个过程会阻塞

- IO多路复用（I/O multiplexing）

    基于选择器的同步非阻塞I/O，同样第二个过程会阻塞

- 信号驱动I/O（Signal driven I/O）

    基于信号驱动，第二个过程同样会阻塞，属于同步IO

- 异步I/O（asynchronous I/O）

    异步非阻塞I/O，两个过程都不阻塞

## **1.1 阻塞IO（Blocking I/O）**

![阻塞IO](https://asea-cch.life/upload/2021/08/%E9%98%BB%E5%A1%9EIO-3e0f2ba38005420785772ad37b2c7a7d.gif)

在linux中，默认情况下所有的socket都是blocking的。在基础的socket编程中，同样也是使用阻塞IO，这是最省事、最简单的I/O模型

> 在这种前提下，催生了一个线程对应一个socket的模型，并通过起多线程或用线程池的方式来实现程序与客户端的网络交互架构

`阻塞`：当用户进程调用recvfrom()，会从用户态陷入内核态，并阻塞至**用户进程空间数据拷贝完毕**为止

用途：简单易懂，经常被用于每线程对应单socket的架构模型

> 在`数据准备阶段`和`kernel拷贝用户空间`两个阶段，**进程**都会阻塞，前者阻塞时CPU可以执行其它进程；后者阻塞时进程陷入内核态，占用CPU进行拷贝

按照上述同步IO的定义，阻塞IO模型也可以称为`同步阻塞I/O`

## **1.2 非阻塞IO（NonBlocking I/O）**

![非阻塞IO](https://asea-cch.life/upload/2021/08/%E9%9D%9E%E9%98%BB%E5%A1%9EIO-f6a826fe48914c9daee7b1b70a94a67b.gif)

`非阻塞`：当用户进程调用recvfrom()/read()，如果数据并未准备好，则进程将不会被阻塞，而是返回一个错误值**EWOULDBLOCK**

用途：一般结合I/O多路复用的三大模型，组成完整的I/O多路复用模型

> 在这种I/O模型下，进程需要自行**轮询监测**，以单线程对应单socket的模型来看，当服务器需要维护多个socket时，轮询监测的CPU开销是巨大的

按照上述同步IO定义，非阻塞IO模型也可以称为`同步非阻塞I/O`

> 注意：因为强调的是IO发生期间，即kernel拷贝数据到用户空间这个过程，非阻塞I/O的进程仍需陷入内核态占用CPU，自行进行数据拷贝

## **1.3 I/O多路复用（I/O multiplexing）**

![IO多路复用](https://asea-cch.life/upload/2021/08/IO%E5%A4%9A%E8%B7%AF%E5%A4%8D%E7%94%A8-07df85c4276b48ed8abec21febe777bb.gif)

又被称为事件驱动I/O（event driven I/O）

`多路复用`：使单进程具备监测多个I/O的数据就绪状态的能力，相对connection per thread模型的开销更小

用途：用于提升服务处理的连接个数，适用于具备大量网络连接的场景。它并不能提升单个连接的交互速度，甚至在小并发场景下性能没有阻塞I/O高

按照上述同步IO的定义，I/O多路复用模型属于`同步非阻塞I/O`模型的范畴

> [select&poll&epoll](https://asea-cch.life/achrives/select&poll&epoll)：总结了I/O多路复用的三种模型

## **1.4 信号驱动I/O（Signal driven I/O）**

应用进程使用sigaction调用，不同于I/O多路复用，等待数据准备的过程为**非阻塞**

`信号驱动`：内核会在数据到达后向应用进程发送SIGIO信号，意味进程**无须轮询数据就绪状态**

用途：sigaction适用范围较小，一般的普通fd不支持，适用面较少

<!-- 按照上述同步IO的定义，阻塞IO模型也可以称为`同步阻塞I/O` -->

## **1.5 异步I/O（asynchronous I/O）**

![异步IO](https://asea-cch.life/upload/2021/08/%E5%BC%82%E6%AD%A5IO-5214de95080e4c35a195b4b4e082755f.gif)

`异步`：用户进程在系统调用后整个过程（两个阶段）都不会阻塞，由内核CPU进行kernel数据到用户进程的拷贝，完成后再通知进程

用途：典型的有Node这种类似的并发IO密集型的高性能服务器

> 注意：第二个阶段不会阻塞，进程可以转而执行其它操作，这提升了进程的CPU利用率与服务的吞吐量，但是有利有弊，第二个阶段仍然需要内核去占用CPU拷贝操作。所以，如果在**CPU计算密集**的场景下，进程对CPU的高占用势必会影响内核拷贝操作的速度，两者相互影响，反而会使得进程的吞吐量下降

按照上述异步IO的定义，异步IO模型也可以称为`异步非阻塞I/O`。当然，异步肯定就是非阻塞的，一般场景下无须强调

# **2. BIO & NIO**

> [操作系统中的BIO、NIO和多路复用器（SELECT、POLL、EPOLL）的演进和实现](https://blog.csdn.net/A232222/article/details/111054242)

![socket结合NIO（epoll）](https://asea-cch.life/upload/2021/08/socket%E7%BB%93%E5%90%88NIO%EF%BC%88epoll%EF%BC%89-7e4a7d82e0274c28a89ce3afa1da06b2.png)

# 参考
- [BIO、NIO 入门（Netty 先导）](https://blog.csdn.net/w903328615/article/details/113914902?spm=1001.2014.3001.5501)
- [epoll](https://blog.csdn.net/qq_31967569/article/details/89678482)

- [linux文件描述符限制及使用](https://blog.csdn.net/guotianqing/article/details/82313996)
- [fd_set文字解释](https://www.cnblogs.com/wuyepeng/p/9745573.html)
- [fd_set结构](https://www.freesion.com/article/42831060952/)
- [5种IO，入门篇](https://zhuanlan.zhihu.com/p/115912936)

# 重点参考
- [原来 8 张图，就可以搞懂「零拷贝」了](https://zhuanlan.zhihu.com/p/258513662)
- [IO - 同步，异步，阻塞，非阻塞（亡羊补牢篇）](https://blog.csdn.net/historyasamirror/article/details/5778378)
- [五种IO模型透彻分析](https://www.cnblogs.com/f-ck-need-u/p/7624733.html)
- [操作系统中的BIO、NIO和多路复用器（SELECT、POLL、EPOLL）的演进和实现](https://blog.csdn.net/A232222/article/details/111054242)
- [Linux Socket编程（不限Linux）](https://www.cnblogs.com/skynet/archive/2010/12/12/1903949.html)
- [Java NIO浅析](https://tech.meituan.com/2016/11/04/nio.html)