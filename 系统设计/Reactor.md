# Reactor模型

> **connection per thread**：每一个socket都是阻塞的（第二阶段），所以一个线程同一时间只能处理一个socket，那么每线程对应多个socket的话，会造成其它客户端响应延迟。

早期的服务器架构多采用connection per thread模型，通过多线程方式提升服务器的吞吐量，但这种方式的缺点显而易见：
- 线程的系统资源开销大，资源有限
- 线程的反复创建、销毁代价极大，虽然可以用线程池进行更深层次的优化

为了减少系统线程资源的开销，更进一步地提升系统的吞吐量，Reactor模式出现了，并成为目前大多数I/O相关的组件如：Redis、Netty、Zookeeper、JavaNIO都在使用的IO模式

Reactor实现思想来源于`I/O多路复用模型`，使用它来取代同步阻塞I/O中使用多线程实现connection per thread处理方式，可以显而易见的节省系统资源

> 在高性能I/O系统设计中，有两个著名的模式：`Reactor`和`Proactor`，前者用于同步I/O，后者用于异步I/O

# **1. 基础设施**

**基本I/O模型**：

I/O多路复用（事件驱动I/O模型）

> [《select&poll&epoll》]()、 [《I/O多路复用》]()：重点关注调用select/poll/epoll线程、poll返回后如何分配事件、epoll的ET模式

## **1.1 具体成分**：

![Reactor_Structures](https://asea-cch.life/upload/2021/08/Reactor_Structures-52304180f0fc4557bb4c36f2885f045a.png)

- **Handle**

    作用：操作系统中文件句柄（**文件描述符fd**），可以是一个打开的文件、一个socket连接，在这里代指一个网络连接（Connection，在Java NIO中的**Channel**）

    **Channel会注册到Synchronous Event Demultiplexer中，以监听fd就绪情况**，对于`ServerSocketChannel`可以是`CONNECT`事件，对于`SocketChannel`可以是`READ`、`WRITE`、`CLOSE`事件

    > 疑问：ServerSocketChannel是不是对于epfd，SocketChannel对应socketfd？

- **Initiaion Dispatcher**

    作用：**Event Handler**的容器，用于注册、移除EventHandler等**管理操作**
    
    它还作为Reactor模式的入口，调用**Synchronous Event Demultiplexer**（内核select/epoll）阻塞以等待事件返回。当阻塞等待返回时，根据event类型将发生的**Handle**分发给对应的Event Handler处理，即调用Event Handler的`handle_event()`方法

- **Synchronous Event Demultiplexer**

    作用：阻塞等待fd数据就绪，如果阻塞等待返回，即表示在返回的**Handle中可以不阻塞的执行**返回的事件类型

    > 该模块一般使用操作系统的**select/poll/epoll**实现，在Java NIO中使用Selector进行封装，当Selector.select()返回时，可以调用Selector的`selectedKeys()`方法获取Set\<SelectionKey\>，一个SelectionKey代表一个有事件发生的Channel以及Channel上的event类型，设置Handle的状态，然后返回

- **Event Handler**

    继承自`Concrete Event Handler`，定义事件处理方法：handle_event()，以供Intiation Dispatcher回调使用

## **1.2 流程**

有**一个或多个并发输入源**，有一个**Service Handler**，有多个**Request Handlers**，Service Handler会**同步**的将输入请求多路复用的分发给相对应的request handler

![reactor简易模型](https://asea-cch.life/upload/2021/08/reactor%E7%9A%84%E6%9C%B4%E7%B4%A0%E6%A8%A1%E5%9E%8B-8b8f643f899a41ababe4a2f5c88017a5.png)

有多个不同的`EventHandler`来处理不同的请求，`Initiation Dispatcher`用于管理EventHandler，EventHandler首先要注册到InitiationDispatcher中，然后Initiation Dispatcher根据输入的Event类型分发给注册的EventHandler；然而Initiation Dispatcher并不监听Event的到来，这个工作交由`Synchronous Event Demultiplexer`处理

- Event Handlers -> Request Handlers
- Initiation Dispatcher和Synchronous Event Demultiplexer -> Service Handler的两种不同职责
    - Initiation Dispatcher -> reactor
    - Synchronous Event Demultiplexer -> acceptor

**具体交互流程如下**：
1. 初始化Initiation Dispatcher，它是一种特殊的Event Handler，初始化一个Handle映射Initiation Dispatcher上

    往往会初始化AcceptorEventHandler，

2. 注册Initiation Dispatcher

# **1. 单线程Reactor**

一种简单的Reactor模型，称为**Reactor朴素原型**，单线程指的是：`reactor`与`handler`处于同一个线程上

具体应用案例：使用main线程实现Java的NIO模式的Selector网络通讯

```java
public class NIOSingleDemo {

}
```

<!-- 
今日总结：学习了reactor模型，简单接触了单线程和多线程的reactor

后续：
1. 拆分以下reactor和acceptor的概念，然后看清楚5个组成部分，由重点参考的文章入手学习

2. 看Java的NIO，它是个单线程reactor模型，看第一篇文章的demo

 -->

# 参考
- [Reactor模式简介](https://www.cnblogs.com/crazymakercircle/p/9833847.html)

# 重点参考
- [Reactor模式详解](http://www.blogjava.net/DLevin/archive/2015/09/02/427045.html)