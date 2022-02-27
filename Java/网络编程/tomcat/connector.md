# Tomcat：Connector

> 本章着重介绍connector，后面介绍的Container，其职责是将请求路由到对应的servlet来处理`业务逻辑`

Connector职责：

- EndPoint（Acceptor、Poller、Processor）：**网络I/O模型**和**应用层I/O模型框架**的选型，解析处理应用层协议，封装为一个Request对象

    > netty：以JDK NIO作为网络I/O模型（同步非阻塞I/O），在应用层面上自实现**异步事件驱动框架**以反馈在所有的I/O操作上，用户I/O操作调用都是立即返回（异步I/O）

- Adapter：将Request转换为ServletRequest，将Response转换为ServletResponse

EndPoint可以再具体分为`EndPoint`、`SocketProcessor`、`Adapter`，这三个组件协作完成以下功能：

- NioEndPoint：与底层socket打交道，处理selector的服务器监听、客户端读事件，并提供HttpSocketProcessor同步处理I/O操作

    - Acceptor：对应listen socket fd，**默认使用阻塞式I/O**取出client socket fd对应的SocketChannel，包装成**PollEvent**放入到Poller的队列中

    - Poller：内置selector监听注册的client socket fd集合，当selector阻塞返回时，遍历SelectionKeys将就绪的socket包装成**SocketProcessor**，并提交该任务到Worker线程池中

    - SocketProcessor：由Worker线程池中的某个线程**阻塞处理**，负责读入字节流，解析为**Tomcat定义的Request对象**后传递给Adapter

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

NioEndPoint组合了独立的Poller与Acceptor两个异步线程，Poller先启动，Acceptor再启动，详情见`NioEndPoint#startInternal()`

> startInternal()方法的调用与Boostrap、ProtocolHandler、AbstractProtocol相关

Poller对selector阻塞返回就绪key集合遍历处理，通过`createSocketProcesor()`方法，为每一个就绪socket**包装成SocketProcessor**，SocketProcessor将会进行**阻塞I/O操作**和**阻塞serlvet业务处理**

```java
public class NioEndPoint extends AbstractJsseEndPoint<NioChannel, SocketChannel> {
    // jdk.nio的ServerSocketChannel，对应server listen socket fd
    private volatile ServerSocketChannel serverSocket = null;

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

        // 创建worker线程池
        if (getExecutor == null) {
            createExecutor();
        }

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

tomcat：服务器监听到新连接到来，同样以**投递任务**的方式进行，任务的具体内容是将新连接socket对应的fd注册到poller的选择器

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
    protected volatile AcceptorState state = AcceptorState.NEW;
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
            }
        } catch (Throwable t) {
            // ...
        } finally {
            // 与stop方法配套使用，只有run这边执行完毕后，acceptor的stop逻辑才能真正开始运作
            stopLatch.countDown();
        }
        
        state = AcceptorState.ENDED;
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
            // 向poller的队列投递任务，任务的类型为OP_REGISTER
            // 任务内容：selector注册client socket fd的OP_READ事件
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

最后看一下，processKey方法如何将客户端的可读事件传递到SocketProcessor：

```java
protected void processKey(SelectonKey sk, NioSocketWrapper socketWrapper) {
    try {
        if (close) {
            cancelledKey(sk, socketWrapper);
        } else if (sk.isValid() && socketWrapper != null) {
            if (socketWrapper.getSendfileData() != null) {
                processSendfile(sk, socketWrapper, false);
            } else {
                // 取消当前socket在selector的读事件注册，防止多个线程同时读一个socket，保证同一时刻只有一个SocketProcessor处理socket的数据
                unreg(sk, socketWrapper, sk.readyOps());
                boolean closeSocket = false;
                if (sk.isReadable()) {
                    // 处理socket的读
                    if (socketWrapper.readOperation != null) {
                        if (!socketWrapper.readOperation.process()) {
                            closeSocket = true;
                        }
                    } else if (!processSocket(socketWrapper, SocketEvent.OPEN_READ, true)) {
                        // 数据入站操作交由SocketProcessor在worker线程池中异步执行
                        closeSocket = true;
                    }
                }

                if (!closeSocket && sk.isWritable()) {
                    // 处理socket的写
                    if (socketWrapper.writeOperation != null) {
                        if (!socketWrapper.writeOperation.process()) {
                            closeSocket = true;
                        }
                    } else if (!processSocket(socketWrapper, SocketEvent.OPEN_WRITE, true)) {
                        // 数据出站操作依旧交由SocketProcessor异步执行
                        closeSocket = true;
                    }
                }

                if (closeSocket) {
                    cancelledKey(sk, socketWrapper);
                }
            }
        } else {
            cancelledKey(sk, socketWrapper);
        }
    } catch (...) {
        // ...
    }
}

public boolean processSocket(SocketWrapperBase<S> socketWrapper, SocketEvent event, boolean dispatch) {
    try {
        if (socketWrapper == null) {
            return false;
        }
        SocketProcessorBase<S> sc = null;
        if (processorCache != null) {
            // 取出SocketProcessor的缓存
            sc = processorCache.pop();
        }
        if (sc == null) {
            // 没有则新建，在后续SocketWrapper关闭时一同返还缓存池中
            sc = createSocketProcess(socketWrapper, event);
        } else {
            sc.reset(socketWrapper. event);
        }
        Executor executor = getExecutor();
        if (dispatch && executor != null) {
            // 异步分发，委托给Worker线程池执行
            executor.executor(sc);
        } else {
            // 直接在poller线程中执行SocketProcessor的任务
            sc.run();
        }
    } catch (...) {
        // ...
        return false;
    }
    return true;
}
```

`同步读`：poller线程不负责具体的读http message，当有可读事件时分配给SocketProcessor来处理，而后者是在tomcat的工作线程池来执行，即一个连接的http请求数据读取和poller是**异步**的，所以为了保证同一时刻只有一个SocketProcessor在处理socket的数据，会取消该socket在poller selector的可读事件

最后是通过抽象方法`createSocketProcessor(SocketWrapperBase<S>, SocketEvent)`来创建SocketProcess：

```java
// NioEndpoint的实现
@Override
protected SocketProcessorBase<NioChannel> createSocketProcessor(SocketWrapperBase<NioChannel> socketWrapper, SocketEvent event) {
    return new SocketProcessor(socketWrapper, event);
}
```

# **2. Processor**

SocketProcessor的执行方式有两种，一种是直接在poller线程中**同步执行**，一种是当worker线程池不为空时提交到池中进行**异步执行**

nio-http11/bio模式下，都不应该在poller线程中同步执行，因为poller线程只有一个，这会造成其他用户的请求阻塞

## **2.1 Worker线程池**

同样在startInternal()方法中初始化池，调用`createExecutor()`方法

```java
public void createExecutor() {
    internalExecutor = true;
    // 线程池内部队列
    TaskQueue taskqueue = new TaskQueue();
    TaskThreadFactory tf = new TaskThreadFactory(getName() + "-exec-", daemon, getThreadPriority());
    // tomcat继承jdk线程池的实现
    executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), 60, TimeUnit.SECONDS, taskqueue, tf);
}
```

> [tomcat线程池](https://zhuanlan.zhihu.com/p/86955374)

tomcat.TaskQueue继承自jdk.LinkedBlockingQueue\<Runnable\>，是一个无界队列，重写了offer(Runnable)/take()/poll()等方法来支持自定义线程池的新特性

正常的线程池使用无界队列作为任务队列后，将会面临以下问题：

- 任务可以无限制的投递，当消费者无法处理过来时，程序面临OOM的危险

- corePoolSize ～ maximumPoolSize之间数目的临时线程数无法被利用到，默认无界队列的线程池**线程数最多只能到达corePoolSize个**

```java
public class TaskQueue extends LinkedBlockingQueue<Runnable> {
    // 该方法被调用的前提：池中线程数已到达corePoolSize
    @Override
    public boolean offer(Runnable o) {
        if (parent == null) {
            return super.offer(o);
        }

        if (parent.getPoolSize() == parent.getMaximumPoolSize()) {
            // 如果当前池的线程数到达了最大数，这个时候就没了可工作的线程了，将任务投递到阻塞队列中
            return super.offer(o);
        }

        // 到达此处，池中线程数：[corePoolSize, maximumPoolSize)，有可能有非核心空闲线程

        if (parent.getSubmiitedCount() <= parent.getPoolSize()) {
            // 正在执行的任务线程数 <= 当前池的线程数，说明有空闲线程阻塞队列等待任务，丢入队列中会被立即执行
            // 这些空闲线程其实就是非核心临时线程
            return super.offer(o);
        }

        // 到达此处时，池中线程数：[corePoolSize, maximumPoolSize)，且没有空闲的非核心线程（非核心线程都在处理任务）

        if (parent.getPoolSize() < parent.getMaximumPoolSize()) {
            // 没有到达最大线程数，可以继续创建非核心线程来执行，真实的利用这部分core到max的区域
            return false;
        }

        // 到达此处，是因为以上判断并不是互斥串行的，并不准确
        return super.offer(o);
    }
}
```

```java
public class ThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {
    @Override
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        // 记录正在执行的任务数，可以推算出接下来会执行任务的线程数
        submittedCount.incrementAndGet();
        try {
            super.execute(command);
        } catch (RejectedExecution rx) {
            if (super.getQueue() instanceof TaskQueue) {
                final TaskQueue queue = (TaskQueue) super.getQueue();
                try {
                    if (!queue.force(command, timeout, unit)) {
                        submittedCount.decrementAndGet();
                        throw new RejectedExecutionException(sm..getString("threadPoolExecutor.queueFull"));
                    }
                }
            }
        }
    }
}
```

结论：
- tomcat只解决了无界队列下线程数突破corePoolSize的限制，当遇到线程消费不过来时，仍会出现OOM风险

- 提供reject重试机制来挽救任务：

    主要出现在TaskQueue的offer返回false，调用ThreadPoolExecutor#addWorker前的场景，若恰好有其他调用offer的线程先行添加完毕，**且刚好工作线程数到达maximumPoolSize**，则会出现reject情况

    offer返回false的情况：

    - 还没有到达maximumPoolSize，且当前没有空闲的线程

    - 如果队列设置了最大界限，由父类方法返回了false（默认设置Integer.MAX_VALUE）

最终流程：

1. 如果当前运行的线程，少于corePoolSize，则创建一个新的线程来执行任务

2. 如果线程数大于corePoolSize了，Tomcat的线程不会直接把线程加入到无界的阻塞队列中，而是去判断poolSize是否等于 maximumPoolSize

3. 如果等于，表示线程池已经满线程数运行，不能再创建线程了，直接把线程提交到队列，
如果不等于，则需要判断，是否有空闲线程可以消费

4. 如果有空闲线程（submittedCount < poolSize）则加入到阻塞队列中，空闲线程消费会立即返回阻塞进行消费

5. **如果没有空闲线程，且当前还能创建线程，则尝试创建新的线程，在offer方法中返回false。**（这一步保证了使用无界队列，仍然可以利用线程的 maximumPoolSize）

    如果创建新线程时失败，意味着出现了offer并发调用，前面的if条件并不准确，这种情况下也会出现拒绝重试

6. 如果总线程数达到maximumPoolSize，则继续尝试把线程加入BlockingQueue中，这种情况也是并发调用offer下，if条件判断不准确导致

7. 如果BlockingQueue达到上限（假如设置了上限），被默认线程池启动拒绝策略，tomcat线程池会catch住拒绝策略抛出的异常，再次把尝试任务加入中BlockingQueue中

8. 再次加入失败，启动拒绝策略

## **2.2 SocketProcessor**

```java
protected class SocketProcessor extends SocketProcessorBase<NioChannel> {
    public SocketProcessor(SocketWrapperBase<NioChannel> socketWrapper, SocketEvent event) {
        super(socketWrapper, event);
    }

    // 父类继承了Runnable，run()实现中加入了对socketWrapper对象的互斥锁
    @Override
    protected void doRun() {
        NioChannel socket = socketWrapper.getSocket();
        Poller poller = NioEndpoint.this.poller;
        if (poller == null) {
            socketWrapper.close();
            return;
        }

        try {
            // handshake用于处理https的握手过程
            int handshake = -1;
            try {
                if (socket.isHandshakeComplete()) {
                    handshake = 0;
                } else if (event == SocketEvent.STOP || event == SocketEvent.DISCONNECT || event == SocketEvent.ERROR) {
                    handshake = -1;
                } else {
                    // 如果是http，则不需要该握手阶段，直接将标志置为0表示握手已经完成，详情见NioChannel的默认实现
                    // 如果是https，则进行握手阶段，详情见SecureNioChannel的方法实现
                    handshake = socket.handshake(event == SocketEvent.OPEN_READ, event == SocketEvent.OPEN_WRITE);
                    event = SocketEvent.OPEN_READ;
                }
            } catch (...) {
                // ...
            }

            if (handshake == 0) {
                // TLS握手完成或无需握手
                SocketState state = SocketState.OPEN;
                if (event == null) {
                    // 默认是读事件处理，调用AbstractProtocol#ConnectionHandler#process()方法
                    state = getHandler().process(socketWrapper, SocketEvent.OP_READ);
                } else {
                    // 响应指定event处理
                    state = getHandler().process(socketWrapper, event);
                }
                if (state == SocketState.CLOSED) {
                    poller.cancelledKey(socket.getIOChannel().keyFor(poller.getSelector()), socketWrapper);
                }
            } else if (handshake == -1) {
                // 握手失败，连接失败
                getHandler().process(socketWrapper.SocketEvent.CONNECT_FAIL);
                poller.cancelledKey(socket.getIOChannel().keyFor(poller.getSelector()), socketWrapper);
            } else if (handshake == SelectionKey.OP_READ) {
                // 向poller的selector注册OP_READ
                socketWrapper.registerReadInterest();
            } else if (handshake == Selection.OP_WRITE) {
                // 向poller的selector注册OP_WRITE
                socketWrapper.registerWriteInterest();
            }
        } catch (...) {
            // ...
        } finally {
            socketWrapper = null;
            event = null;
            if (running && !paused && processorCache != null) {
                processorCache.push(this);
            }
        }
    }
}
```

# **3. Adapter**

SocketProcessor在最后调用了`getHandler().process(socketWrapper, SocketEvent.OP_READ)`来处理请求，getHandler()返回的是Http11Protocol，其process将交互的逻辑又转移到了`CoyoteAdapater`处理：

> 注意区分Protocol下processor和Endpoint下socketProcessor，两者名字相近，但是职责和顶层父类接口完全不同

```java
public abstract class AbstractProtocol {
    // 抽象方法，用于返回AbstractProcessorLight类型的processor，用以处理请求
    protected abstract Processor createProcessor();

    protected static class ConnectionHandler<S> implements AbstractEndpoint.Handler<S> {
        @Override
        public SocketState process(SocketWrapperBase<S> wrapper, SocketEvent status) {
            // 取出读就绪的socket
            S socket = wrapper.getSocket();

            Processor processor = (Processor) wrapper.getCurrentProcessor();
            // 省略一部分代码

            try {
                if (processor == null) {
                    // ...
                }
                if (processor == null) {
                    // 先尝试从processor的回收区取
                    processor = recycledProcessors.pop();
                }
                if (processor == null) {
                    processor = getProtocol().createProcessor();
                }

                // ...
                do {
                    // Http11Processor#process方法，即父类AbstractProcessorLight方法
                    state = processor.process(wrapper, status);
                    // ...
                } while (state == SocketState.UPGRADING);

            } catch (...) {
                // ...
            }
        }
    }
}

public abstract class AbstractHttp11Protocol extends AbstractProtocol {
    @Override
    protected Processor createProcessor() {
        // 该对象在创建的同时，会初始化Request对象和Response对象
        Http11Processor processor = new Http11Processor(this, adapter);
        return processor;
    }
}
```

`AbstractProcessLight#process(SocketWrapperBase<?>, SocketEvent)`将会处理OPEN_READ类型事件，调用service(SocketWrapperBase<?>)方法，从此处开始逐渐与Servlet标准接口接近：

```java
public abstract class AbstractProcessLight implements Processor {
    public AbstractProcessor(Adapter adapter) {
        // 初始化时，构造新的request对象和response对象，这两个对象都属于tomcat包下的
        this(adapter, new Request(), new Response());
    }

    // 与CoyoteAdapter交互的抽象方法
    protected abstract SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException;

    @Override
    public SocketState process(SocketWrapperBase<?> socketWrapper, SocketEvent status) {
        // ...
        do {
            // ...
            else if (status == SocketEvent.OPEN_READ) {
                // 调用Http11Processor的实现
                state = service(socketWrapper);
            }
            // ...
        }
    }
}

public class Http11Processor extends AbstractProcessLight {
    // 入站缓存
    private final Http11InputBuffer inputBuffer;
    // 出站缓存
    private final Http11OutputBuffer outputBuffer;
    private final HttpParser httpParser;

    public Http11Processor(AbstractHttp11Protocol<?> protocol, Adapter adapter) {
        super(adapter);
        this.protocol = protocol;

        httpParser = new HttpParser(protocol.getRelaxedPathChars(), protocol.getRelaxedQueryChars());

        inputBuffer = new Http11InputBuffer(request, protocol.getMaxHttpHeaderSize(), protocol.getRejectIllegalHeader(), httpParser);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new Http11OutputBuffer(response, protocol.getMaxHttpHeaderSize());
        response.setOutputBuffer(outputBuffer);
    }

    @Override
    public SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException {
        // 省略一些代码

        if (getErrorState().isIoAllowed()) {
            rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
            try {
                // 读取数据，为request设置属性
                prepareRequest();
            } catch (...) {
                // ...
            }
        }

        // 省略一些代码

        if (getErrorState().isIoAllowed()) {
            try {
                rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                // 关键代码，CoyoteAdapter通过装饰器模式将在后续把tomcat接收到的request、response转化为servlet标准下的HttpRequest、HttpResponse对象
                getAdapter().service(request, response);

                // ...
            } catch (...) {
                // ...
            }
        }
    }
}
```

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

最后看一下CoyoteAdapter是如何进行转化为以上的Servlet标准的：

```java
public class CoyoteAdapter implements Adapter {
    @Override
    public void service(org.apache.coyote.Requset req, org.apache.coyote.Response res) {
        // Servlet规范下的HttpServletRequest子类
        Request request = (Request) req.getNote(ADAPTER_NOTES);
        // Servlet规范下的HttpServletResponse子类
        Response response = (Response) res.getNot(ADAPTER_NOTES);

        if (request == null) {
            // ...
        }

        // ...

        try {
            // 通过对request对象的inputBuffer进行解析，并设置到Request对象中
            postParseSuccess = postParseRequest(req, request, res, response);
            if (postParseSuccess) {
                request.setAsyncSupported(connector.getService().getContainer().getPipeline().isAsyncSupported());
            }
            // 正式地调用到container中的业务servlet，该操作在当前线程中阻塞完成，container进行路由
            connector.getService().getContainer().getPipeline().getFirst().invoke(request, response);
        }
    }
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

StandardService -> StandardEngine 
->

StandardPipeline.getFirst() -> StandardEngineValue.invoke(request, response)

->

StandardContextValue.invoke(request, response)：校验url的合法性

->

StandardHostValue.invoke(request, response)

->

StandardWrapperValue（servlet的包装类）.invoke(request, response)

ApplicationFilterFactory.createFilterChain(request, wrapper, servlet)

-> 

filterChain.doFilter(request.getRequest(), response.getResponse);

->

servlet.service(request, response) --> HttpServlet.service(ServletRequest, ServletResponse res)

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

- [有点萌的Tomcat线程池](https://zhuanlan.zhihu.com/p/86955374)