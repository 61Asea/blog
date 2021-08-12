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

同步IO：I/O发生期间，进程会被阻塞住的，就算是同步IO

> A synchronous I/O operation causes the requesting process to be blocked until that **I/O operation** completes

> An asynchronous I/O operation does not cause the requesting process to be blocked

## **1.1 阻塞IO（Blocking I/O）**

![阻塞IO](https://asea-cch.life/upload/2021/08/%E9%98%BB%E5%A1%9EIO-3e0f2ba38005420785772ad37b2c7a7d.gif)

在linux中，默认情况下所有的socket都是blocking的

当用户进程调用recvfrom()，会从用户态陷入内核态，

## **1.2 非阻塞IO（NonBlocking I/O）**

## **1.3 I/O多路复用（I/O multiplexing）**

## **1.4 信号驱动I/O（Signal driven I/O）**

## **1.5 异步I/O（asynchronous I/O）**

# **2. BIO & NIO**


# 参考
- [BIO、NIO 入门（Netty 先导）](https://blog.csdn.net/w903328615/article/details/113914902?spm=1001.2014.3001.5501)
- [epoll](https://blog.csdn.net/qq_31967569/article/details/89678482)

- [linux文件描述符限制及使用](https://blog.csdn.net/guotianqing/article/details/82313996)
- [fd_set文字解释](https://www.cnblogs.com/wuyepeng/p/9745573.html)
- [fd_set结构](https://www.freesion.com/article/42831060952/)

# 重点参考
- [原来 8 张图，就可以搞懂「零拷贝」了](https://zhuanlan.zhihu.com/p/258513662)
- [IO - 同步，异步，阻塞，非阻塞（亡羊补牢篇）](https://blog.csdn.net/historyasamirror/article/details/5778378)
- [五种IO模型透彻分析](https://www.cnblogs.com/f-ck-need-u/p/7624733.html)