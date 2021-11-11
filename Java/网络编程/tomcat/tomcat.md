# tomcat

核心组件：Connector连接器、Container容器

Connector职责：

- EndPoint（Acceptor、Processor）：`网络I/O模型`和`应用层I/O模型框架`的选型，解析处理应用层协议，封装为一个Request对象

    > netty：以JDK NIO作为网络I/O模型（同步非阻塞I/O），在应用层面上自实现**异步事件驱动框架**以反馈在所有的I/O操作上，用户I/O操作调用都是立即返回（异步I/O）

- Adapter：将Request转换为ServletRequest，将Response转换为ServletResponse

Container职责：路由到对应的servlet，处理`业务逻辑`

# **1. Connector**

具体分为`EndPoint`、`Processor`、`Adapter`三个组件完成功能，流程为：

1. EndPoint与底层socket打交道，负责**提供字节流**给Processor



2. Processor负责将字节流解析为**Tomcat定义的Request对象**，并传递给Adapter

3. Adapter负责将Request对象转换为J2EE Servlet标准下的ServletRequest，提供对container**service()**方法的同名包装方法，作为connector和container之间的**交互接口**

![tomcat流程图](https://asea-cch.life/upload/2021/11/tomcat%E6%B5%81%E7%A8%8B%E5%9B%BE-09a80a9394b243f6900706ac7265c583.png)

## **1.1 EndPoint**

```java
public class NioEndPoint extends AbstractJsseEndPoint<NioChannel, SocketChannel> {
    // jdk.nio的ServerSocketChannel，对应server listen socket fd
    private volatile ServerSocketChannel serverSock = null;


}
```

## **1.2 Processor**

## **1.3 Adapter**

连接器需要对接的是标准的Servlet容器，Servlet的service方法遵循Servlet规范下，只能接收标准化的`ServletRequest`和`ServletResponse`:

```java
// javax.servlet规范
public interface Servlet {
    // 只能接收ServletRequest和ServletResponse
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException;
}

public interface ServletRequest {
    public Object getAttribute(String name);

    public Enumeration<String> getAttributeNames();

    public String getCharacterEncoding();

    public void setCharacterEncoding(String env) throws UnsupportedEncodingException;

    // ...
}
```

## **1.4 流程总结**

以**http1.1**、**NIO同步阻塞I/O网络模型**为例，将涉及以下模块：

- EndPoint --> NioEndPoint、NioEndPoint#SocketProcessor（选取NIO通信模型）

- Processor --> AbstractProcessorLight、Http11Processor（选取http1.1）

- Adapter --> CoyoteAdapter

整体流程：

NioEndPoint#SocketProcessor#doRun()

->

AbstractProtocol#ConnectionHandler#process(SocketWrapperBase<?>, SocketEvent)

->

AbstractProcessorLigth#process(SocketWrapperBase<?>, SocketEvent)

->

Http11Processor#service(SocketWrapperBase<?>)

-> 

CoyoteAdapter.service(Request req, Response res) 

->

与container做交互（待补充）

在调用栈的第二层出现了`AbstractProtocol`，体现了它使用组合的方式将EndPoint和Processor作为一个整体，提供它们封装和**交互**细节

# **2. Container**



# 参考
- [一万字深度剖析Tomcat源码](https://www.jianshu.com/p/7c9401b85704?utm_campaign=haruki&utm_content=note&utm_medium=seo_notes&utm_source=recommendation)