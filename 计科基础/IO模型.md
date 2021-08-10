# IO模型

<!-- 1. kernel准备数据
2. 将kernel缓冲区的数据拷贝到用户进程中

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

# **1. 五大模型**

同步IO：第二步出现阻塞的，就是同步IO
异步IO：两步都不会阻塞的，就是异步IO

阻塞IO：调用后，会一直block到操作结束的IO

非阻塞IO：kernel准备数据过程中不会阻塞，可以立即返回的IO

## **1.1 阻塞IO（Blocking I/O）**

## **1.2 非阻塞IO（NonBlocking I/O）**

## **1.3 I/O多路复用（I/O multiplexing）**

## **1.4 信号驱动I/O（Signal driven I/O）**

## **1.5 异步I/O（asynchronous I/O）**

# **2. BIO & NIO**



# 参考
- [全网最透彻的五种linux IO模型分析](https://zhuanlan.zhihu.com/p/393635611?utm_source=ZHShareTargetIDMore&utm_medium=social&utm_oi=793369254232731648)
- [BIO、NIO 入门（Netty 先导）](https://blog.csdn.net/w903328615/article/details/113914902?spm=1001.2014.3001.5501)
- [epoll](https://blog.csdn.net/qq_31967569/article/details/89678482)

# 重点参考
- [原来 8 张图，就可以搞懂「零拷贝」了](https://zhuanlan.zhihu.com/p/258513662)
- [IO - 同步，异步，阻塞，非阻塞 ](https://blog.csdn.net/historyasamirror/article/details/5778378)