# Netty：Channel

netty将server listen fd和client socket fd进行包装，使得我们可以更加方便地操作底层网络API：
- NioServerSocketChannel
- NioSocketChannel

当然Channel不仅限于句柄，还结合了Reactor线程模型与底层jdk.nio相关内容

# **1. 基础组件**

## **1.1 引导**

ServerBoostrap中提供了以下内容：
- NioServerSocketChannel的TCP选项值、工作组
- NioServerSocketChannel的初始化方法`init(Channel channel)`
- NioServerSocketChannel的ChannelInitializer
    - 提供对NioSocketChannel进行线程组注册的逻辑

```java
public class ServerBoostrap extends AbstractBoostrap<ServerBootstrap, ServerChannel> {
    // 在main()方法中设置的各个属性
    // 客户端channel的tcp选项
    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> childAttrs = new ConcurrentHashMap<AttributeKey<?>, Object>();
    private final ServerBootstrapConfig config = new ServerBootstrapConfig(this);
    // 客户端channel对应的线程组，如果使用group(EventLoopGroup)的话则与服务端线程组共用
    private volatile EventLoopGroup childGroup;
    // 客户端channel对应的ChannelInitializer，在后续注册方法channelRegistered时机触发
    private volatile ChannelHandler childHandler;

    // 初始化NioServerSocketChannel
    @Override
    void init(Channel channel) {
        setChannelOptions(channel, newOptionsArray(), logger);
        setAttributes(channel, attrs0().entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY));

        // 获得listen socket fd的pipeline
        ChannelPipeline p = channel.pipeline();

        final EventLoopGroup currentChildGroup = childGroup;
        final ChannelHandler currentChildHandler = childHandler;
        final Entry<ChannelOption<?>, Object>[] currentChildOptions;
        synchronized (childOptions) {
            currentChildOptions = childOptions.entrySet().toArray(EMPTY_OPTION_ARRAY);
        }
        final Entry<AttributeKey<?>, Object>[] currentChildAttrs = childAttrs.entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY);

        // 为服务器监听Channel的pipeline添加多一个handler
        // headContext -> 该channelInitializer -> tailContext
        p.addLast(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();
                if (handler != null) {
                    pipeline.addLast(handler);
                }

                // 驱动服务端线程的自循环，这里是第一次驱动
                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.addLast(new ServerBootstrapAcceptor(
                                ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                    }
                });
            }
        });
    }

    private static class ServerBoostrapAcceptor extends ChannelInBoundHandlerAdapter {
        // ...

         @Override
        @SuppressWarnings("unchecked")
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            final Channel child = (Channel) msg;

            child.pipeline().addLast(childHandler);

            setChannelOptions(child, childOptions, logger);
            setAttributes(child, childAttrs);

            try {
                // 将监听channel获得的msg（本质上商NioSocketChannel）注册到工作线程组中
                // 工作线程组会根据chooser选择eventloop，来与channel进行绑定

                // 该register方法最终到达Channel.Unsafe层面的注册，在Unsafe层面的注册会再驱动子线程中的eventLoop驱动起来，后续会分析到
                childGroup.register(child).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            forceClose(child, future.cause());
                        }
                    }
                });
            } catch (Throwable t) {
                forceClose(child, t);
            }
        }
    }
}
```

jdk.nio提供了单独的API`bind(int port)`，通过本地方法来系统调用内核`bind(int port)、listen()`方法以启动服务器并进行监听

netty由父类AbstractBoostrap提供`bind(int port)`，与jdk.nio类似也是单独API，

```java
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Clonable {
    public B channel(Class<? extends C> channelClass) {
        // 设置channelFactory到引导中
        return channelFactory(new ReflectiveChannelFactory<C>(ObjectUtil.checkNotNull(channelClass, "channelClass")));
    }

    @Deprecated
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        // ...

        // 设置工厂，一般为反射类工厂，调用其工厂方法newChannel()来反射生成对应的channel实例
        this.channelFactory = channelFactory;
        return self();
    }

    // 
    private ChannelFuture doBind(SocketAddress localAddress) {
        // 初始化NioServerSocketChannel实例对象，为其加入TCP参数和ChannelInitializer，后者会为pipeline的handler链中加入ServerBoostrap.ServerBootstrapAcceptor
        // ServerBootstrapAcceptor：提供channelRead以处理客户端channel的pipeline构造、属性填充等
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        // ....

        if (reg.Future.isDone()) {
            ChannelPromise promise = channel.newPromise();
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener((future) -> {
                // ChannelFuture注册一个doBind0()，在操作完成时触发;
            });
        }
    }

    private static void doBind0(final ChannelFuture regFuture, final Channel channel, final SocketAddress localAddress, final ChannelPromise promise) {
        // 该方法会驱动NioServerSocketChannel的NioEventLoop进行自循环，以不断的处理其队列的任务，兼顾阻塞select()系统调用
        channel.eventLoop().execute(() -> {
            if (regFuture.isSuccess()) {
                channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                promise.setFailure(regFuture.cause());
            }
        });
    }

    // 极其重要的方法
    final ChannelFuture initAndRegister() {
        // 1. 开始初始化服务器listen socket fd对应的channel
        Channel channel = null;
        try {
            channel = channelFactory.newChannel();
            init(channel);
        } catch (Throwable t) {
            // ...
        }

        // 2. 给boss线程组注册channel，为channel注入selector的selectionKeys集合，最后为selector注册channel的OP_ACCEPT兴趣
        ChannelFuture regFuture = config().group().register(channel);
        // ....
    }
}
```

config().group()：返回boss线程组，一般在此处之前已经生成了多个eventLoop，每个eventLoop都会初始化其selector

config.childGroup()：返回worker线程组，如果在引导只传入了一个组，则共用该组，否则则新起一个组

# **2. Channel**

主要介绍两种Channel，它们分别对应jdk.nio中不同的channel，在文件系统上也属于不同的套接字文件句柄范畴：
- NioServerSocketChannel/EpollServerSocketChannel：server listen fd
- NioSocketChannel/EpollSocketChannel：client socket fd

它们的父类AbstractNioMessageChannel/AbstractNioChannel包括以下公共成员：

- pipeline：流水线，为该channel的channel handler链条，初始状态下有headContext和tailContext
- ch：对应底层nio上的channel
- readInterestOp：兴趣事件selectionKey
- unsafe：实际上的处理逻辑

    ```java
    // AbstractChannel.register()，两种channel都会使用的方法，相当重要
    @Override
    public final void register(EventLoop eventLoop, final ChannelPromise promise) {
        // ...

        AbstractChannel.this.eventLoop = eventLoop;

        // 该方法一般由服务器channel进行驱动
        if (eventLoop.inEventLoop()) {
            // 服务器channel的初始化和注册流程
            register0(promise);
        } else {
            // 后续服务器channel对新的客户端channel注册流程，因为不在同一个线程，则将其作为任务投递到worker eventloop中

            // 如果是第一次调用worker eventloop的该方法，还会驱动其自循环逻辑的启动
            try {
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        register0(promise);
                    }
                });
            } catch (Throwable t) {
                // ...
            }
        }
    }
    ```

## **2.1 ServerSocketChannel**

对应server listen fd，用于**监听、接收**客户端**连接**请求

ServerSocketChannel绑定并持有eventloop的引用，eventloop的选择器将会为其注册OP_ACCEPT事件，而OP_ACCEPT兴趣事件的attachment则为channel对象本身

具体下发处理逻辑在`AbstractNioUnsafe.read()`，由selector的select阻塞返回触发，它会调用channel的doReadMessages多态实现，而NioServerSocketChannel实现如下：

```java
// 用于对OP_ACCEPT事件的处理
@Override
protected int doReadMessages(List<Object> buf) throws Exception {
    SocketChannel ch = SocketUtils.accept(javaChannel());

    try {
        if (ch != null) {
            buf.add(new NioSocketChannel(this, ch));
            return 1;
        }
    } catch (Throwable t) {
        logger.warn("Failed to create a new channel from an accepted socket.", t);

        try {
            ch.close();
        } catch (Throwable t2) {
            logger.warn("Failed to close a socket.", t2);
        }
    }

    return 0;
}
```

具体成员详情参考：
- pipeline：head -> serverboostrapAcceptor -> tail
- readInterestOp：OP_ACCEPT
- unsafe：NioMessageUnsafe

## **2.2 SocketChannel**

对应client socket fd，用于与客户端**读入、写出**数据

具体下发处理逻辑在`NioByteUnsafe.read()`，仍旧由selector的select阻塞返回触发，它会调用channel的doReadBytes的多态实现，NioSocketChannel的具体实现如下：

```java
@Override
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        allocHandle.attemptedBytesRead(byteBuf.writableBytes());
        // 传入nio.channel，通过I/O流来读取数据到byteBuf中
        return byteBuf.writeBytes(javaChannel(), allocHandle.attemptedBytesRead());
    }
```

具体成员详情参考：
- pipeline：head -> logging -> engthfielddecoder -> 自定义handler -> tail
- readInterestOp：OP_READ/OP_WRITE
- unsafe：NioByteUnsafe

## **2.3 ChannelPipeLine和ChannelHandlerContext的相互配合**

ChannelPipeline与Channel相互持有对方的引用：

```java
public class AbstractChannel {
    private final DefaultChannelPipeline pipeline;

    public DefaultChannelPipeline pipeline() {
        return this.pipeline;
    }
}
```

ChannelPipeline旨在维护ChannelHandlerContext类型节点的双向队列，提供：
- 节点入队、替换节点等结构管理的API
- 头、尾节点的实现
- 配合ChannelHandlerContext实现队列`从头到尾`的消息流转

```java
public class DefaultChannelPipeline implements ChannelPipeline {
    // 双向队列
    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;

    private final Channel channel;

    protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtils.checkNotNull(channel, "channel");

        // 用于处理出站消息
        head = new HeadContext(this);
        // 用于处理入站消息
        tail = new TailContext(this);

        head.next = tail;
        tail.prev = head;
    }

    @Override
    public final ChannelPipeline addLast(ChannelHander handler) {
        return addLast(null, handler);
    }

    @Override
    public final ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            checkMultiplicity(handler);

            // 将handler包装为一个ChannelHandlerContext
            newCtx = newContext(group, filterName(name, handler), handler);

            // 加在队列的倒数第二位置，即tail节点之前
            addLast0(newCtx);

            // ...
        }
        return this;
    }

    // 各种各样的fireChanellXXXX()方法，传入head进行从头到尾的流转
    @Override
    public final ChannelPipeline fireChannelActive() {
        // 以active时机为例，传入head之后，由AbstractChannelHandlerContext配合进行流转
        AbstractChannelHandlerContext.invokeChannelActive(head);
        return this;
    }
}
```

ChannelHandlerContext可以根据情况来控制数据在**ChannelHandlerContext节点双向队列**的流转：

- 可从某个节点开始进行流转：invokeChannelXXX()系列的静态方法

    > 需要区分：invokeChannelXXX()系列的实例方法用于触发内部handler的某时机的具体方法

- 从当前节点开始进行流程：fireChannelXXX()系列的实例方法

- 可控制流转的方向，inbound则往next方向，outbound则往prev方向

    > findContextOutbound() / findContextInbound()：出站、入站信息流转的关键方法，会筛选出ChannelOutboundHandler和ChannelInboundHandler

```java
abstract class AbstractChannelHandlerContext implements ChannelHandlerContext {
    // 当前节点的下一个节点
    volatile AbstractChannelHandlerContext next;
    // 当前节点的上一个节点
    volatile AbstractChannelHandlerContext prev;
    // 对应channel
    private final DefaultChannelPipeline pipeline;
    // 对应channel所在的eventloop
    final EventExecutor executor;

    AbstractChannelHandlerContext(DefaultPipeline pipeline, EventExecutor executor, String name, Class<? extends ChannelHandler> handlerClass) {
        // ...
        this.pipeline = pipeline;
        this.executor = executor;
    }

    private AbstractChannelHandlerContext findContextInbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        // 获得当前的eventloop
        EventExecutor currentExecutor = executor();
        do {
            ctx = ctx.next;
        } while (skipContext(ctx, currentExecutor, mask, MASK_ONLY_INBOUND));
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound(int mask) {
        // findContextInbound一样的操作，遍历的方向相反而已
        do {
            ctx = ctx.prev;
        } while(skipContext(ctx, currentExecutor, mask, MASK_ONLY_OUTBOUND));
        return ctx;
    }

    // 用于筛除某种类型的Handler，具体由mask的值决定，位运算筛除
    private static boolean skipContext(AbstractChannelHandlerContext ctx, EventExecutor currentExecutor, int mask, int onlyMask) {
    }

    // 当前节点开始，往下一个节点开始流转
    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        // findContextInBound(MASK_CHANNEL_READ)返回下一个入站handler
        // 调用静态系列方法，传入下一个节点，表示从该节点继续往下遍历
        invokeChannelRead(findContextInBound(MASK_CHANNEL_READ), msg);
        return this;
    }

    // 静态方法系列
    static void invokeChannelRead(final AbstractChannelHandlerContext next, Object msg) {
        final Object m = next.pipeline.touch(ObjectUtil.checkNotNull(msg, "msg"), next);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            // 调用invokeChannelRead实例方法，内部会调用真实的handler实现
            next.invokeChannelRead(m);
        } else {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    next.invokeChannelRead(m);
                }
            });
        }
    }

    // 实例方法系列
    private void invokeChannelRead(Object msg) {
        if (invokeHandler()) {
            try {
                // 调用具体handler的channelRead方法
                ((ChannelInboundHandler) handler()).channelRead(this, msg);
            } catch (Throwable t) {
                invokeExceptionCaught(t);
            }
        } else {
            fireChannelRead(msg);
        }
    }
}
```

**ChannelHandlerContext节点的双向队列**，用于在selector阻塞返回处理SelectionKeys的过程，每个节点都有对应时机的处理逻辑：

```java
// 以NioServerSocketChannel的ServerBoostrapAcceptor为例
@Override
@SuppressWarnings("unchecked")
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    final Channel child = (Channel) msg;

    // 为NioSocketChannel添加channelInitializer
    child.pipeline().addLast(childHandler);

    // 设置客户端channel的tcp参数等
    setChannelOptions(child, childOptions, logger);
    setAttributes(child, childAttrs);

    try {
        // 注册channel到worker工作组中，驱动对应eventloop的自循环启动
        childGroup.register(child).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    forceClose(child, future.cause());
                }
            }
        });
    } catch (Throwable t) {
        forceClose(child, t);
    }

    // 没有调用ctx.fireXXX()，pipeline到此流转结束
}
```

# 参考
- [Netty源码分析(一)：客户端操作之channel()](https://blog.csdn.net/qq_41594698/article/details/89738304)
- [Netty源码分析(四)：关于ChannelPipeline和addLast](https://blog.csdn.net/qq_41594698/article/details/89894135)

# 参考
- [线程模型简单总结](https://blog.csdn.net/scying1/article/details/90755438)

# 重点参考
- [Netty中的那些坑](https://www.cnblogs.com/rainy-shurun/p/5213086.html)：讲解netty线程模型下，使用不当导致的各种坑
- [Netty 防止内存泄漏措施](https://www.infoq.cn/article/olLlvGFx*Kr0UV9K7tez)：堆积任务导致问题的分析，包括解决方案