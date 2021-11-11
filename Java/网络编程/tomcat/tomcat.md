# tomcat

核心组件：Connector连接器、Container容器

Connector职责：

- EndPoint（Acceptor、Processor）：**网络I/O模型**和**应用层I/O模型框架**的选型，解析处理应用层协议，封装为一个Request对象

    > netty：以JDK NIO作为网络I/O模型（同步非阻塞I/O），在应用层面上自实现**异步事件驱动框架**以反馈在所有的I/O操作上，用户I/O操作调用都是立即返回（异步I/O）

- Adapter：将Request转换为ServletRequest，将Response转换为ServletResponse

Container职责：路由到对应的servlet，处理`业务逻辑`

# **1. Connector**

具体分为`EndPoint`、`SocketProcessor`、`Adapter`三个组件完成功能：

- NioEndPoint：与底层socket打交道，处理selector的服务器监听、客户端读事件，并提供HttpSocketProcessor同步处理I/O操作

    - Acceptor：对应listen socket fd，使用系统内核提供的selector阻塞监听OP_ACCEPT事件，取出client socket fd对应的SocketChannel，包装成**PollEvent**放入到Poller的队列中

    - Poller：内置selector监听注册的client socket fd集合，当selector阻塞返回时，遍历SelectionKeys将就绪的socket包装成**SocketProcessor**，并提交该任务到Worker线程池中

    - SocketProcessor：由Worker线程池中的某个线程阻塞处理，负责读入字节流，解析为**Tomcat定义的Request对象**后传递给Adapter

- Adapter：负责将Request对象转换为J2EE Servlet标准下的ServletRequest，提供对container**service()**方法的同名包装方法，作为connector和container之间的**交互接口**

![tomcat流程图](https://asea-cch.life/upload/2021/11/tomcat%E6%B5%81%E7%A8%8B%E5%9B%BE-09a80a9394b243f6900706ac7265c583.png)

## **1.1 EndPoint**

EndPoint超类持有acceptor的引用，并提供了异步启动Acceptor线程的方法：

```java
public abstract class AbstractEndPoint<S, U> {
    // Acceptor引用，泛型为SocketChannel
    protected Acceptor<U> acceptor;

    protected void startAcceptorThread() {
        acceptor = new Acceptor<>(this);
        String threadName = getName() + "-Acceptor";
        acceptor.setThreadName(threadName);
        Thread t = new Thread(acceptor, threadName);
        t.setPriority(getAcceptorThreadPriority());
        t.setDaemon(getDaemon());
        t.start();
    }
}
```

NioEndPoint只有独立的Poller异步线程，关于Poller线程和Acceptor线程的初始化顺序见`startInternal()`

> startInternal()方法的调用，与Boostrap、ProtocolHandler、AbstractProtocol相关

Poller对selector阻塞返回的key集合进行遍历处理，每一个就绪的socket都会被包装为一个SocketProcessor，后者将会进行全阻塞的读操作和serlvet操作

```java
public class NioEndPoint extends AbstractJsseEndPoint<NioChannel, SocketChannel> {
    // jdk.nio的ServerSocketChannel，对应server listen socket fd
    private volatile ServerSocketChannel serverSock = null;

    private Poller poller = null;

    // 作为Acceptor和Poller的交互方法
    protected boolean setSocketOptions() {
        // 将取出的socketChannel注册到poller的selector中，以OP_READ事件注册
    }

    public class Poller implements Runnable {
        // Poller的selector，用于阻塞监听客户端套接字的OP_READ事件
        private Selector selector;

        public Poller() throws IOException {
            this.selector = Selector.open();
        }

        // 与SocketProcessor进行交互
        @Override
        public void run() {
            while (true) {
                // ...
                Iterator<SelectionKey> iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;
                while (iterator != null && iterator.hasNext()) {
                    // ...

                    // 处理读就绪的client socket fd
                    processKey(sk, socketWrapper);
                }
            }
        }

        public boolean processSocket(SocketWrapperBase<S> sockerWrapper, SocketEvent event, boolean dispatch) {
            // ...

            // 调用EndPoint的父类方法，创建SocketProcessor
            createSocketProcessor(socketWrapper, event);
        }
    }

    protected class SocketProcessor extends SocketProcessorBase<NioChannel> {
        // 包装了一层，具体实现逻辑在doRun
        @Override
        protected void doRun() {
            NioChannel socket = socketWrapper.getSocket();
        }
    }

    @Override
    public void startInternal() throws Exception {
        // ...

        // 先启动Poller异步线程
        poller = new Poller();
        Thread pollerThread = new Thread(poller, getName());
        pollThread.setPriority(threadPriority);
        pollThread.setDaemon(true);
        pollThread.start();

        // 后启动Acceptor异步线程
        startAcceptorThread();
    }
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