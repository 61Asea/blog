# IO模型

<!-- 
5大IO模型：
- 同步阻塞IO
- 同步非阻塞IO（同步非阻塞I/O，第二个过程会阻塞）
- IO多路复用（基于选择器的同步非阻塞I/O，同样第二个过程会阻塞）
- 信号驱动IO（基于信号驱动，第二个过程同样会阻塞，属于同步IO）
- 异步IO（异步非阻塞I/O，两个过程都不阻塞）

- 同步IO：IO调用期间会被BLOCK住的，都算同步IO。所以阻塞IO一定是同步IO
    - 同步阻塞IO：在调用IO会一直block住
    - 同步非阻塞IO：在kernel准备数据的阶段中，可以返回，但是在kernel将数据复制到用户进程时会阻塞
- 异步IO：一定也是非阻塞IO

NIO使用的也是I/O 多路复用的思想，并基于reactor模型将调度线程抽象为选择器，所以NIO/IO复用模型为：同步非阻塞IO
epoll rbr和rdlist -->

> [《文件传输》](https://asea-cch.life/achrives/文件传输)：介绍了现代文件传输的流程，其中包括DMA、零拷贝、kernel pagecache等概念。得出结论，大文件传输使用异步I/O（直接I/O），少量数据则使用零拷贝（缓存I/O）技术

本文以**网络传输I/O为上下文**，继续围绕`内核空间`和`进程空间`，在`进程的视角`上总结5大模型：
- 阻塞I/O（Blocking I/O）
- 非阻塞I/O（NonBlocking I/O）
- IO多路复用（I/O multiplexing）
- 信号驱动I/O（Signal driven I/O）
- 异步I/O（asynchronous I/O）

同样的，I/O发生与等待的步骤，一定是基于以下两个立足点：
1. kernel数据准备（DMA拷贝）
2. 将数据从内核拷贝到进程中（CPU拷贝）

# **1. 五大模型**

- 同步IO：I/O**发生期间**，进程会被阻塞

    > A synchronous I/O operation causes the requesting process to be blocked until that **I/O operation** completes

- 异步IO：I/O**发生期间**，进程不会被阻塞住

    > An asynchronous I/O operation does not cause the requesting process to be blocked

## **1.1 阻塞IO（Blocking I/O）**

![阻塞IO](https://asea-cch.life/upload/2021/08/%E9%98%BB%E5%A1%9EIO-3e0f2ba38005420785772ad37b2c7a7d.gif)

在linux中，默认情况下所有的socket都是blocking的。在基础的socket编程中，同样也是使用阻塞IO

> 在这种前提下，催生了一个线程对应一个socket的模型，并通过起多线程或用线程池的方式来实现程序与客户端的网络交互架构

`阻塞`：当用户进程调用recvfrom()，会从用户态陷入内核态，并阻塞至**用户进程空间数据拷贝完毕**为止

> 在`数据准备阶段`和`kernel拷贝用户空间`两个阶段，都是阻塞的

按照上述同步IO的定义，阻塞IO模型也可以称为`同步阻塞I/O`

## **1.2 非阻塞IO（NonBlocking I/O）**

![非阻塞IO](https://asea-cch.life/upload/2021/08/%E9%9D%9E%E9%98%BB%E5%A1%9EIO-f6a826fe48914c9daee7b1b70a94a67b.gif)

`非阻塞`：当用户进程调用recvfrom()，如果数据并未准备好，则进程将不会被阻塞，而是返回-1状态码

> 在这种I/O模型下，进程需要自行**轮询监测**，以单线程对应单socket的模型来看，当服务器需要维护多个socket时，轮询监测的CPU开销是巨大的

按照上述同步IO定义，非阻塞IO模型也可以称为`同步非阻塞I/O`

> 注意：因为强调的是IO发生期间，即kernel拷贝数据到用户空间这个过程，非阻塞I/O的进程仍需陷入内核态占用CPU，自行进行数据拷贝

## **1.3 I/O多路复用（I/O multiplexing）**

> [select&poll&epoll](https://asea-cch.life/achrives/select&poll&epoll)：总结了I/O多路复用的三种模型

![IO多路复用](https://asea-cch.life/upload/2021/08/IO%E5%A4%9A%E8%B7%AF%E5%A4%8D%E7%94%A8-07df85c4276b48ed8abec21febe777bb.gif)

## **1.4 信号驱动I/O（Signal driven I/O）**



## **1.5 异步I/O（asynchronous I/O）**

![异步IO](https://asea-cch.life/upload/2021/08/%E5%BC%82%E6%AD%A5IO-5214de95080e4c35a195b4b4e082755f.gif)

# **2. BIO & NIO**


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