# Tomcat：Connector

> 本章着重介绍connector，Container的职责是路由到对应的servlet来处理`业务逻辑`

Connector职责：

- EndPoint（Acceptor、Processor）：**网络I/O模型**和**应用层I/O模型框架**的选型，解析处理应用层协议，封装为一个Request对象

    > netty：以JDK NIO作为网络I/O模型（同步非阻塞I/O），在应用层面上自实现**异步事件驱动框架**以反馈在所有的I/O操作上，用户I/O操作调用都是立即返回（异步I/O）

- Adapter：将Request转换为ServletRequest，将Response转换为ServletResponse

具体分为`EndPoint`、`SocketProcessor`、`Adapter`三个组件完成功能：

- NioEndPoint：与底层socket打交道，处理selector的服务器监听、客户端读事件，并提供HttpSocketProcessor同步处理I/O操作

    - Acceptor：对应listen socket fd，使用系统内核提供的selector阻塞监听OP_ACCEPT事件，取出client socket fd对应的SocketChannel，包装成**PollEvent**放入到Poller的队列中

    - Poller：内置selector监听注册的client socket fd集合，当selector阻塞返回时，遍历SelectionKeys将就绪的socket包装成**SocketProcessor**，并提交该任务到Worker线程池中

    - SocketProcessor：由Worker线程池中的某个线程阻塞处理，负责读入字节流，解析为**Tomcat定义的Request对象**后传递给Adapter

- Adapter：负责将Request对象转换为J2EE Servlet标准下的ServletRequest，提供对container#**service()**方法的同名包装方法，作为connector和container之间的**交互接口**

![tomcat流程图](https://asea-cch.life/upload/2021/11/tomcat%E6%B5%81%E7%A8%8B%E5%9B%BE-09a80a9394b243f6900706ac7265c583.png)

# **1. EndPoint**

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

NioEndPoint组合独立Poller异步线程和Acceptor异步线程，Poller先启动，Acceptor再启动，详情见`NioEndPoint#startInternal()`

> startInternal()方法的调用与Boostrap、ProtocolHandler、AbstractProtocol相关

Poller对selector阻塞返回的key集合进行遍历处理，通过`createSocketProcesor()`方法，每一个就绪的socket都会被包装为一个SocketProcessor，SocketProcessor将会进行**阻塞I/O操作**和**阻塞serlvet业务处理**

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
                Iterator<SelectionKey> iterator = keyCount > 0 ? 
                selector.selectedKeys().iterator() : null;
                while (iterator != null && iterator.hasNext()) {
                    // ...

                    // 处理读就绪的client socket fd，进一步调用processSocket()方法
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

## **1.1 Acceptor**

netty：boss线程组中的NioEventLoop（NioServerSocketChannel）接收新连接，以**投递任务**的形式将新连接业务投递到worker线程组的某个线程队列中，并**驱动该线程的启动**

tomcat：服务器监听到新连接到来，同样以**投递任务**的方式进行，将新连接socket对应的fd注册到poller的选择器

> 唯一不同点在于poller随着tomcat启动而启动，且poller线程为**独立线程**，这在1.2会继续分析

需要注意的是，**默认acceptor使用阻塞式I/O**，并没有像netty一样通过selector多路复用，这意味着，tomcat是通过`while循环`驱动，netty是通过`while循环 + 选择器返回key个数`进行驱动

两者在没有新连接到来时都会进入阻塞，但是当有大量新连接到达时，由于tomcat比netty少了一次select()调用，明显效率会更高

```java
public class Acceptor<U> implements Runnable {
    // 持有所属的endpoint引用
    private final AbstractEndpoint<? , U> endpoint;
    // 关闭标记，调用stop后立即变化，run方法可立即感知并进行停止
    private volatile boolean stopCalled = false;
    // 控制stop方法的执行，只有run方法结束，stop才会阻塞返回
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    // Acceptor的可视状态
    protected voaltile AcceptorState state = AcceptorState.NEW;
    // ...

    @Override
    public void run() {
        int errorDelay = 0;
        try {
            while (!stopCalled) {
                while (endpoint.isPaused() && !stopCalled) {
                    // 跟随endpoint的状态
                    state = AcceptorState.PAUSED;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    if (stopCalled) {
                        break;
                    }
                    state = AcceptorState.RUNNING;

                    try {
                        // 到达最大连接数目则等待，使用到了AQS的共享模式，类似Semaphore信号量
                        endpoint.countUpOrAwaitConnection();

                        if (endpoint.isPaused()) {
                            continue;
                        }

                        U socket = null;
                        try {
                            // 调用accept()获取客户端套接字
                            socket = endpoint.serverSocketAccept();
                        } catch (Exception ioe) {
                            // 失败意味着没有获得socket，应返回AQS资源
                            endpoint.countDownConnection();
                            // ...
                        }

                        if (!stopCalled && !endpoint.isPaused()) {
                            // 客户端的socket注册到poller的selector中
                            if (!endpoint.setSocketOptions(socket)) {
                                // 注册失败，需要掐掉连接
                                endpoint.closeSocket(socket);
                            }
                        } else {
                            // endpoint暂停、停止等情况下，将socket掐掉防止泄漏
                            endpoint.destroySocket(socket);
                        }
                    } catch (Throwable t) {
                        // ...
                    }
            }

            } catch (Throwable t) {
                // ...
            } finally {
                // 与stop方法配套使用，只有run这边执行完毕后，acceptor的stop逻辑才能真正开始运作
                stopLatch.countDown();
            }
            state = AcceptorState.ENDED;
        }
    }

    public void stop() {
        // 配合run方法使用，run方法感知到stopCalled = true后会退出循环
        stopCalled = true;
        try {
            // 阻塞等待run方法结束
            stopLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Ignore
        }
    }
}
```

与Poller的交互方式如下，tomcat会对**客户端套接字缓存**，以减少对象频繁创建销毁的开销:

```java
// NioEndPoint.setSocketOptions(SocketChannel)
@Override
protected boolean setSocketOptions(SocketChannel socket) {
    NioSocketWrapper socketWrapper = null;
    try {
        NioChannel channel = null;
        if (nioChannels != null) {
            // SynchronizedStack<NioChannel>类型的缓存，避免重复创建
            channel = nioChannels.pop();
        }
        if (channel == null) {
            // 缓存取不出，则创建新的NioChannel
            SocketBufferHandler bufhandler = new SocketBuffHandler(socketProperties.getAppReadBufSize(), socketProperties.getAppWriteBufSize(), socketProperties.getDirectBuffer());

            // 持有bufhandler引用，使用完毕后由NioSocketWrapper#close()汇入缓存
            if (isSSLEnabled()) {
                // https
                channel = new SecureNioChannel(bufhandle, selectorPool, this);
            } else {
                channel = new NioChannel(bufhandler);
            }
            NioSocketWrapper newWrapper = new NioSocketWrapper(channel, this);
            // channel回收时没有重置，在被取出时再进行重置，懒加载的思想
            channel.reset(socket, newWrapper);
            connections.put(socket, newWrapper);
            socketWrapper = newWrapper;

            // 设置为非阻塞
            socket.configureBlocking(false);
            socketProperies.setProperties(socket.socket());

            socketWrapper.setReadTimeout(getConnectionTimeout());
            socketWrapper.setWriteTimeout(getConnectionTimeout());
            socketWrapper.setKeepAliveLeft(NioEndPoint.this.getMaxKeepAliveRequests());
            // 向poller的selector注册client socket fd的OP_READ事件
            poller.register(channel, socketWrapper);
            return true;
        }
    } catch (Throwable t) {
        Exceptionutils.handleThrowable(t);
        // ...
    }
}
```

> acceptor传递到poller的过程，是一个典型`生产者-消费者`模型，具体见1.2

## **1.2 Poller**

poller是endpoint的组件之一，其工作线程个数由endpoint的类型决定：

- NioEndPoint#Poller：单线程实现

- AprEndPoint#Poller：线程池实现，支持多个poller线程

生产者-消费者模型具体如下：

- 生产方：Acceptor监听到新连接

- 投递队列：通过poller.register(channel, socketWrapper)来进行投递，任务载体为`PollEvent`，任务类型为OP_REGISTER

    ```java
    // NioEndPoint#PollEvent
    public static class PollerEvent {
        private NioChannel socket;
        private int interestOps;

        public PollerEvent(NioChannel ch, int intOps) {
            reset(ch, intOps);
        }

        // 缓存池的体现
        public void reset(NioChannel ch, int intOps) {
            socket = ch;
            interestOps = intOps;
        }
    }
    ```

- 消费方：Poller调用`event()`方法消费任务，该方法存在于其run方法中，**将OP_REGISTER类型任务的socket注册OP_READ到selector中**

```java
// NioEndPoint#Poller
public class Poller implements Runnable {
    // poller的selector，负责客户端socket的读事件监听，又被称为主selector
    private Selector selector;

    // 自实现全同步线程安全队列，类型为PollEvent
    private final SynchronizedQueue<PollEvent> events = new SynchronizedQueue<>();

    // Poller#register()、Poller#add()都通过该方法进行任务入队
    private void addEvent(PollerEvent event) {
        // 任务入队
        events.offer(event);
        if (wakeupCounter.incrementAndGet() == 0) {
            // 唤醒selector来处理任务，防止任务堆积
            selector.wakeup();
        }
    }

    // 投递方法
    public void register(final NioChannel socket, final NioSocketWrapper socketWrapper) {
        // 设置socketWrapper的OP_READ
        socketWrapper.interestOps(SelectionKey.OP_READ);
        PollerEvent event = null;
        if (eventCache != null) {
            // pollerEvent作为任务载体，同样使用缓存池的方式
            event = eventCache.pop();
        }
        if (event == null) {
            // OP_REGISTER
            event = new PollerEvent(socket, OP_REGISTER);
        } else {
            event.reset(socket, OP_REGISTER);
        }
        // 进入任务队列
        addEvent(event);
    }

    // 消费方法
    public boolean events() {
        boolean result = false;

        PollerEvent pe = null;
        // 每次消费只处理for循环此刻读取到的events长度，在处理过程中新添加的任务将不会被处理到
        for (int i = 0, size = events.size(); i < size && (pe = events.poll()) != null; i++ ) {
            result = true;
            NioChannel channel = pe.getSocket();
            NioSocketWrapper socketWrapper = channel.getSocketWrapper();
            int interestOps = pe.getInterestOps();
            if (interestOps == OP_REGISTER) {
                try {
                    // 处理acceptor投递过来的任务
                    channel.getIOChannel().register(getSelector(), SelectionKey.OP_READ, socketWrapper);
                } catch (Exception x) {
                    log.error(sm.getString("endpoint.nio.registerFail"), x);
                }
            } else {
                final SelectionKey key = channel.getIOChannel().keyFor(getSelector());
                if (key == null) {
                    // 处理由于socket关闭导致的key失效
                    socketWrapper.close();
                } else {
                    final NioSocketWrapper attachment = (NioSocketWrapper) key.attachment();
                    if (attachment != null) {
                        // 处理keep-alive复用连接（重新注册OP_READ），和OP_WRITE
                        try {
                            int ops = key.interestOps() | interestOps;
                            attachment.interestOps(ops);
                            key.interestOps(ops);
                        } catch (CancelledKeyException ckx) {
                            cancelledKey(key, socketWrapper);
                        }
                    } else {
                        cancelledKey(key, attachment);
                    }
                }
            }
            if (running && !paused && eventCache != null) {
                // pollEvent加入缓存池待复用
                pe.reset();
                eventCache.push(pe);
            }
        }
        // for循环有处理到任务，就返回true
        return result;
    }
}
```

消费者的自循环逻辑在run()方法实现上，其中通过**wakeupCounter**来决定poller当前是应该消费队列的任务，还是调用select()阻塞，同时还决定了select()阻塞的方式：

> 结论：优先处理队列任务，再进行selector阻塞，但是若selector被唤醒时恰好监听到了就绪句柄并返回，则优先处理selectionKeys

```java
@Override
public void run() {
    while (true) {
        // 有任务，并且已经处理完
        boolean hasEvents = false;
        try {
            if (!close) {
                // 优先处理队列
                hasEvents = events();
                
                // 配合Poller#addEvents()：wakeupCounter.incrementAndGet() == 0
                // 每次要selector阻塞时，都会将counter设置为-1，这样在队列有任务加入时，都会变为0，触发selector.wakeup()
                if (wakeupCounter.getAndSet(-1) > 0) {
                    // 说明队列有任务，阻塞后快速返回
                    keyCount = selector.selectNow();
                } else {
                    keyCount = selector.select(selectorTimeout);
                }
                wakeupCounter.set(0);
                // 这里存在一种可能：此处设置counter为0，恰好acceptor投递任务过来将counter自增为1
            }

            // 关闭逻辑
            if (close) {
                // 清空任务队列
                events();
                timeout(0, false);
                try {
                    // 关闭selector，防止内存泄漏
                    selector.close();
                } catch (...) {
                    // ...
                }
                break;
            }
        } catch (...) {
            // ....
        }

        // 处理selector返回的就绪句柄，若无则消费队列
        if (keyCount == 0) {
            hasEvents = (hasEvents | events());
        }
        // 遍历selectionKeys集合
        Iterator<SelectionKey> iterator =
            keyCount > 0 ? selector.selectedKeys().iterator() : null;
        while (iterator != null && iterator.hasNext()) {
            SelectionKey sk = iterator.next();
            NioSocketWrapper socketWrapper = (NioSocketWrapper) sk.attachment();
            if (socketWrapper == null) {
                iterator.remove();
            } else {
                iterator.remove();
                // 触发后续的SocketProcessor逻辑
                processKey(sk, socketWrapper);
            }
        }

        timeout(keyCount,hasEvents);
    }
    getStopLatch().countDown();
}
```

最后看一下，processKey是如何将客户端的可读事件传递到Processor：

```java

```

# **2. Processor**

## **2.1 SocketProcessor**

## **2.2 Worker线程池**

# **3. Adapter**

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

# **流程总结**

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


# 参考
- [断网故障时Mtop触发tomcat高并发场景下的BUG排查和修复（已被apache采纳）](https://developer.aliyun.com/article/2889)
- [Tomcat NIO 线程模型分析](https://www.jianshu.com/p/4e239e217ada)

- [【网络编程】Netty采用的NIO为什么是同步非阻塞的？](https://blog.csdn.net/weixin_41954254/article/details/106414746)

- [Tomcat 9.0.26 高并发场景下DeadLock问题排查与修复](http://blog.itpub.net/69912579/viewspace-2673081/)
- [JDK-6403933 : (se) Selector doesn't block on Selector.select(timeout) (lnx)](https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6403933)

# 重点参考
- [一万字深度剖析Tomcat源码](https://www.jianshu.com/p/7c9401b85704?utm_campaign=haruki&utm_content=note&utm_medium=seo_notes&utm_source=recommendation)
- [tomcat 线程模型](https://blog.csdn.net/qq_16681169/article/details/75003640)

- [Acceptor默认是BIO](https://blog.csdn.net/u011385186/article/details/53148702)：tomcat的acceptor并没有使用selector，千万不能印象流
