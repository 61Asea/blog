# Netty：ChannelHandler

引导代码需要设置客户端与服务器建立连接后的childHandler，当netty应用为client socket生成`Channel`时，将调用childhandler.`initChannel()`，为每个channel初始化职责链：

```java
public static void main(String[] args) {
    ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBoostrap.childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();
            // 1. 新增LoggingChannelHandler
            p.addLast("logging", new LoggingHandler(LogLevel.DEBUG));
            if (isProxy) {
                // 2. 新增HaProxy对应的解码器，同样也是ChannelHandler
                p.addLast(new HAProxyMessageDecoderNew());
            }
            // 3. 长度解码器
            p.addLast(new LengthFieldBasedFrameDecoder(...));
            // 4. 根据线程组情况添加自定义的handler
            if (eventGroups.businessGroup != null) {
                p.addLast(eventGroups.businessGroup, handlers);
            } else {
                p.addLast(handlers);
            }
        }
    });
}
```

每个channel的职责链可在initChannel方法中根据场景进行组织，一般认为socket对应`多例职责链`，职责链中的各个handler通过`ChannelHandlerContext`进行包装

因为每个client socket对应一个channel，以此具体分析以下对象：

- ChannelPipeLine：对应socket的pipeline，拥有`多例职责链（head、tail）`和`执行线程池`
- ChannelHandler：生命周期中对应每个不同的方法，`ChannelHandlerContext`作为方法的入参
    - decoder/encoder：解/编码器，一般处于职责链中的前部分位置
    - 自定义handler：业务代码，一般处于职责链的倒数位置
- ChannelHandlerContext：包装handler实例，并提供对职责链的执行管理

> ChannelHandler和ChannelHandlerContext的相互运作，构筑了netty网络职责链

# **1. 生命周期**



# **2. ChannelPipeLine**

# 参考

# 重点参考