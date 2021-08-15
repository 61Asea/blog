# Reactor模型

> connection per thread：每一个socket都是阻塞的（第二阶段），所以一个线程同一时间只能处理一个socket，那么每线程对应多个socket的话，会造成其它客户端响应延迟。

基于这种思想，采用多线程的形式来提升服务器的吞吐量，但是缺点显而易见：
- 线程的系统资源开销大，资源有限
- 线程的反复创建、销毁代价极大，虽然可以用线程池进行更深层次的优化

> 在高性能I/O系统设计中，有两个著名的模式：`Reactor`和`Proactor`，前者用于同步I/O，后者用于异步I/O

Reactor模式，也称为反应器模式，是大多数I/O相关的组件如：Redis、Netty、Zookeeper、JavaNIO都在使用的IO模式，它主要用来**提升系统的吞吐量**

> [select&poll&epoll](), [I/O多路复用]()：重点关注调用select/poll/epoll线程、poll返回后如何分配事件、epoll的两种模式

实现思想来源于`I/O多路复用模型`，取代同步阻塞I/O中使用多线程实现connection per thread处理方式，可以显而易见的节省系统资源

Reactor模型的组成成分包括：
- 一或多个并发输入源client
- 两种类型的handler
    - `reactor`：一个service handler，是一种特别的handler
        
    - `handler`：多个request handler，也称为handler

service handler(reactor)通过多路复用的方式，将输入的请求(event)，根据不同的event类型，分发到相应的handler中进行处理

![reactor的朴素模型](https://asea-cch.life/upload/2021/08/reactor%E7%9A%84%E6%9C%B4%E7%B4%A0%E6%A8%A1%E5%9E%8B-8b8f643f899a41ababe4a2f5c88017a5.png)

# **1. 单线程Reactor**

一种简单的Reactor模型，称为**Reactor朴素原型**，单线程指的是：`reactor`与`handler`处于同一个线程上



具体应用案例：Java的NIO模式的Selector网络通讯

```java

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