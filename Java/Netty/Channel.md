# Netty：Channel

当netty应用程序（应用层）从**全连接队列**（TCP层接口）中`accpet()`得到`client socket fd`，会通过以下引导代码进行包装：

- 服务器Channel：指定类以初始化服务器channel对象，根据系统来选择

- ChannelHanlder：此处引导先只添加一个初始化childHandler模板，模板用于后续channel的构造，调用`initChannel()`方法来初始化对应的pipeline及其handler链

```java
public static void main(String[] args) {
    // 注意：eventGroup对象生成的同时，其对应的selector也对应生成，至于有多少个selector，则看有多少个eventLoop（此处为1）
    NioEventLoopGroup eventGroup = new NioEventLoopGroup(1);

    ServerBootstrap serverBootstrap = new ServerBootstrap();
    // 1. 设置引导的channel工厂，传入的NioServerSocketChannel作为listen fd的对应，其内部指定NioSocketChannel作为client socket包装初始化时的反射生成类
    serverBoostrap.channel(NioSocketChannel.class)
    // 2. 一般还会添加：TCP选项、netty的worker/boss线程组
    .group(...).option(...)
    // 3. 添加childHandler
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();
            // 3.1 新增LoggingChannelHandler
            p.addLast("logging", new LoggingHandler(LogLevel.DEBUG));
            if (isProxy) {
                // 3.2 新增HaProxy对应的解码器，同样也是ChannelHandler
                p.addLast(new HAProxyMessageDecoderNew());
            }
            // 3.3 长度解码器
            p.addLast(new LengthFieldBasedFrameDecoder(...));
            // 3.4 根据线程组情况添加自定义的handler
            if (eventGroups.businessGroup != null) {
                p.addLast(eventGroups.businessGroup, handlers);
            } else {
                p.addLast(handlers);
            }
        }
    });
}
```

结合**selector多路复用模型**来分析netty如何构造一个**底层基于jdk.nio**的`朴素Reactor`模型：
- Handle（listen socket fd、client socket fd）：文件句柄，直接对应`netty.ServerSocketChannel`和`netty.SocketChannel`

- Event Handler：兴趣事件处理器，服务器句柄OP_ACCEPT，客户端句柄OP_READ/OP_WRITE，可直接对应`jdk.nio.Selector#selectionKeys()`中每个key的attachment

    这些事件处理逻辑会以`attachment`的形式包装在一个个`jdk.nio.SelectionKey`中
    
    每当selector阻塞返回时，其对应的SelectionKey集合对应全部就绪的事件，可以很轻松的取出它们连结的attchment直接进行操作

- Initiation Dispatcher：兴趣事件管理容器，直接对应`jdk.nio.Selector`

    netty同时运用继承和组合`jdk.nio.Selector`的方式实现`netty.SelectedSelectionKeySetSelector`类
    
    在线程模型上，通过`EventLoopGroup`来隔离每个selector，每个线程组对应一个selector，共有boss和worker两种线程组，可以有多个不同的线程组

    - boss的selector：主要管理listen socket fd的OP_ACCEPT兴趣事件，在bind()、listen()方法调用后就将该事件注册到selector中

    - worker的selector：主要管理每个client socket fd的OP_READ（数据就绪），通过OP_ACCEPT事件的event handler把客户端fd注册到worker的selector中

- Synchronous Event Demultiplexer：阻塞句柄实现器，直接对应`netty.SelectedSelectionKeySetSelector#select(long timeout)`方法，底层仍旧是jdk.nio实现

# **1. 线程模型**

通过AbstractBoostrap来对netty的线程模型进行一个总览：

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

    // 及其重要的方法
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

## **2.1 ServerSocketChannel**

NioServerSocketChannel/EpollServerSocketChannel对应server listen fd，用于**监听、接收**客户端请求

- 初始化该channel对应的nio.selectorProvider，对应单个boss线程组
- 为listen fd向其selector注册`SelectionKey.OP_ACCEPT`事件
- 提供listen fd对selector阻塞返回的实现：对client socket fd包装为channel对象，后者可直接对应client socket fd、其绑定eventloop的selector


## **1.2 SocketChannel**

- NioSocketChannel/EpollSocketChannel：对应client socket fd，一般listen fd接收(accept)到三次握手完成的客户端后，会对其client socket fd进行初始化/包装


# **3. ChannelPipeLine**



# 参考
- [Netty源码分析(一)：客户端操作之channel()](https://blog.csdn.net/qq_41594698/article/details/89738304)
- [Netty源码分析(四)：关于ChannelPipeline和addLast](https://blog.csdn.net/qq_41594698/article/details/89894135)