#! https://zhuanlan.zhihu.com/p/498000036
# NIO：简述netty与tomcat的关联性

**本文已收录至个人博客：** https://github.com/61Asea/blog

<!-- - 同步/异步、阻塞/非阻塞
- I/O模型：同步阻塞模型、同步非阻塞模型、异步模型
- select()/poll()和epoll()的区别 -->
- epoll空轮询bug
    - netty/jetty解决方案
- BIO/NIO/AIO
- NIO buffer双向性
- Reactor模型
- Netty线程模型
- 黏包、粘包问题
- 大/小端、高/低水位
- MappedByteBuffer、DirectByteBuffer与malloc()/mmap()的关系
- 传统I/O/mmap() + write/零拷贝/大文件传输
- Tomcat的reactor线程模型
- actor（**消息传递模型**思想）

# **1. JDK的epoll空轮询bug**

bug表现：在进程while(1)循环中调用select()，select()无法阻塞且返回0事件，导致while(1)一直执行空跑CPU直至100%

操作系统原因：Linux2.6的内核中，poll和epoll对于**突然中断的连接socket**，会对返回的事件集合（eventSet）置为POLLHUP/POLLERR，从而使得eventSet发送变化，导致Selector被唤醒

Jetty/Netty解决方案：

1. 新增select死循环阈值、0事件唤醒计数器

2. 计算从select调用前到被唤醒的时长t1，对比select()函数传入的阻塞时间t2，若t1 < t2 && select返回0，则计数器递增1

3. 当计数器达到死循环阈值，则触发bug修复逻辑

    修复逻辑：**新起一个selector**，复制SelectionKey关注的事件到新的selector中，使用新的selector进行阻塞

# **2. BIO、NIO、AIO**

- BIO：同步阻塞I/O，进程在发起I/O请求后进入阻塞状态等待I/O完成

    网络结构：one connection per thread，每个客户端连接都对应一个处理线程，没有分配到线程的连接会阻塞等待或拒绝

- NIO：同步非阻塞I/O，进程发起I/O请求后不会进入阻塞状态，对channel进行读写操作会立即返回，由多路复用器selector来监视注册的fd并进行数据读写操作

    网络结构：基于Reactor模型，由reactor（selector）提供句柄描述符的注册/回调机制，并根据就绪事件类型分发到不同的handler中进行处理（OP_ACCEPT、OP_READ、OP_WRITE）

- AIO：异步I/O，进程发起I/O请求后不会进入阻塞状态，由内核占用cpu完成对I/O请求的处理，并通知进程进行处理

    网络结构：nodejs

> NIO为什么高效？

需根据场景分析，在低并发I/O下，NIO不一定就比BIO高效

1. 采用selector监控多个fd，替代bio的one connection per thread，后者在过多连接下将导致巨大的线程切换开销

2. 只有处于活动状态的io才会去占用线程，不会使得线程白白因为io而阻塞，提高**线程利用率**

3. 使用NIO提供的直接内存（mmap）时，省去将数据从kernel page cache与用户进程空间之间的拷贝开销

4. 使用NIO.channel.transferTo()/transferFrom()，调用系统内核sendfile()函数，实现文件传输的零拷贝

# **3. NIO的buffer是双向的吗？**

是的，buffer支持数据的读入与写出，nio中需要调用filp()方法进行读/写模式的切换，而netty则通过维护读、写指针简化操作

> bio：读使用in流，写使用out流

- nio.ByteBuffer：读写共用position指针

- netty.ByteBuf：分为readerIndex读指针、writeIndex写指针

# **4. Reactor**

Reactor模型分为：

- `reactor`：负责I/O事件的监听（查询与响应），当检测到I/O事件时，分发给handlers处理

- `handlers`：与I/O事件进行**绑定**，负责I/O事件的处理

1. 单Reactor单线程模型：reactor与handlers共用一个线程，reactor需要监视所有的事件，某个handler的阻塞将直接影响其他handler的执行，**无法充分利用多核的性能**

    - 核心问题：
        
        1. 没有将非I/O部分的操作（如：decode、compute、encode）与handlers进行剥离

        2. reactor监视所有的事件

例子：如果某个handler执行业务时长过久，服务将无法进行其他socket fd的建立、读取工作

2. 单Reactor多线程模型：将业务从handlers中剥离开，使用线程池进行处理，handlers只负责I/O操作

    - 核心问题：reactor与handlers依旧共用主I/O线程，reactor仍需要监视所有的事件

例子：如果突然间服务有大量的连接建立到来，则这种瞬时的连接高并发可能会导致**其他类型的就绪事件的处理不及时**（如OP_READ、OP_WRITE）

3. `多Reactor多线程模型`：**主流方案**，将reactor分为多主多从关系（通常一主多从），每个reactor都有自己的selector，主reactor负责建立连接，从reactor负责客户端fd的I/O读写，具体业务则交由业务线程池进行

    缓解单Reactor的压力，松耦合OP_ACCEPT、OP_READ/OP_WRITE进一步利用多核心优势

例子：netty的bossWorkGroup和workerWorkGroup

# **5. netty的线程模型**

线程模型：`多Reactor多线程`模型，每个reactor都有一个selector以注册句柄的感兴趣事件，通过主Reactor并按某种规则分发client fd到`从Reactor`中

- 主Reactor：boss线程组，负责连接建立、分发连接到从Reactor

    - 角色：Acceptor（OP_ACCEPT事件）

    - 监视句柄：listen socket fd

    - 驱动方式：selector阻塞，当有就绪OP_ACCEPT事件发生时被唤醒，进行acceptor逻辑操作

    - 具体逻辑：非阻塞accept()方法，使用while(1)从全连接队列中取出三次握手完毕的客户端句柄

- 从Reactor：worker线程组，负责client fd的**I/O读写**、业务线程池投递

    - 角色：handlers（OP_READ/OP_WRITE）

    - 监视句柄：client socket fd

    - 驱动方式：selector阻塞，当有就绪的OP_READ、OP_WRITE事件发生时被唤醒

    - 具体方式：非阻塞读/写，使用while(1)进行读写操作直至抛出EWOULDBLOCK异常

        > 非阻塞读/写：被唤醒只是知道有就绪的fd，可以从rdlist（selectedKeys）中取出，但具体能读、写的数据量不确定，可将未完成的读、写放到下次epoll_wait()周期；而如果使用阻塞读/写将直接导致该I/O线程阻塞，影响其他句柄的I/O读写

- 业务线程池：用于剥离`从Reactor`的非I/O操作（如：decode、compute、encode），专注于业务逻辑的处理

# **6. 什么是黏包、半包，如何解决**

发生位置：应用层

原因：TCP基于字节流传输，应用层在读取数据时按**某个长度值**进行读取，读取的流数据中可能包含多个message（黏包），或者不完整的message（半包）

> NIO使用buffer进行读、写数据，长度值为TCP的`SO_RCVBUF`大小值

解决方案：

- 黏包：采用**拆包**方式（length | data的格式进行解码）

    > netty使用`LengthFieldBasedFrameDecoder`读取长度信息

- 半包：采用**累加缓存**方式，出现半包后对之前解析的数据进行缓存，并累加读取未读完的数据后进行**拼接**

# **7. 大/小端**

- 大、小端：指的是字节在内存**低到高地址**的排列顺序，又称为`字节序`

    > 一般默认低到高地址的顺序为**从左到右**

    - `大端`：高位字节放在低地址，低位字节放在高地址

        以127（0x00 0x00 0x00 0x7f）为例：

        | 0x0001 | 0x0002 | 0x0003 | 0x0004 |
        | ---- | ---- | ---- | ---- |
        | 0x00 | 0x00 | 0x00 | 0x7f |

    - 小端：低位字节放在高地址，高位字节放在低地址

        以127（0x00 0x00 0x00 0x7f）为例：

        | 0x0001 | 0x0002 | 0x0003 | 0x0004 |
        | ---- | ---- | ---- | ---- |
        | 0x7f | 0x00 | 0x00 | 0x00 |

    `网络字节序`：TCP/IP各层协议将字节序定义为Big-Endian，因此**大端字节序**也被称为网络字节序

    ```java
    // netty.CompositeByteBuf
    public ByteOrder order() {
        // 默认返回大端字节序（网络字节序）
        return ByteOrder.BIG_ENDIAN;
    }
    ```

# **8. netty发送数据（高/低水位）**

`通信缓冲区`：

- 单缓冲：任一时刻都只能实现单方向的数据传输，决不允许双方**同时向对方**发送数据

- `双缓冲`（tcp）：实现**双工通信**，一个用作`发送缓冲区`（写缓冲区），一个用作`接收缓冲区`（读缓冲区）

    netty设置每个client socket fd的发送缓冲区大小、接收缓冲区大小：

    ```java
    new ServerBootstrap()
        .childOption(ChannelOption.SO_RCVBUF, 8 * 1024)  // 对应内核TCP的SO_RCVBUF参数
        .childOption(ChannelOption.SO_SNDBUF, 8 * 1024); // 对应内核TCP的SO_SNDBUF参数
    ```

    - SO_SNDBUF：操作系统内核的**tcp发送缓冲区**，对应发送窗口大小
    
        所有应用需要发送到对端的消息，都会存放到该缓冲区中，等待发往对端

    - SO_RCVBUF：操作系统内核的**tcp接收缓冲区**，对应接收窗口大小，接收缓冲区与TCP接收窗口大小对应关系：接收窗口大小 <= 接收缓冲区大小

`ChannelOutboundBuffer`：netty等待写入内核通信缓冲区的**消息队列**，将要发送的数据以Entry形式进行缓存，各个Entry组成链表

`水位`：ChannelOutboundBuffer本身是无界的，水位控制消息队列的链表节点数量处于水位范围内，以**限制程序的写操作，避免OOM**

- 高水位：队列高过高水位时，channel的isWritable变成false，无法写入

- 低水位：队列低于低水位时，channel的isWritable变成true，可以继续写入

> 为了避免对端程序接收缓慢，导致netty的buffer队列无止境扩张导致进程OOM，在写数据时**应判断isWritable()**，如果是false就不要再写数据了

```java
public boolean sendMsg(final ByteBuf msg) {
    int msgId = msg.getShort(4);
    int bytes = msg.readableBytes();

    if (channel == null) {
        LOGGER.error("sendMsg {} channel isNull,size:{]", msg.getShort(4), msg.readableBytes());
        ByteBufUtils.release(msg);
        return false;
    }

    ChannelOutboundBuffer outboundBuffer = channel.unsafe().outboundBuffer();
    // 使用channel.isWritable()做判断，高低水位保护channel消息队列的大小
    if (!channel.isActive() || !channel.isWritable()) {
        LOGGER.warn("sendMsg ip:{},session:{} isActive:{},isWritable:{}  size:{}", getAddress(), getSessionId(),
                channel.isActive(), channel.isWritable(), msg.readableBytes());
        ByteBufUtils.release(msg);
        return false;
    }

    channel.writeAndFlush(msg).addListener(new GenericFutureListener() {
        public void operationComplete(Future future) throws Exception {
            if (!future.isSuccess()) {
                LOGGER.warn("sendMsg {} channel 发送失败,size:{}, error:{}", msgId, bytes, future.cause());
                printOutBufInfo();
            }
        }
    });
    return true;
}
```

# **9. MappedByteBuffer、DirectByteBuffer与malloc()/mmap()的关系**

JDK.NIO高性能：

- MappedByteBuffer：对应使用`mmap()`的虚拟内存，由FileChannel.map()实现

    - mmap()：内存映射文件，将**一个文件映射到用户进程的地址空间**，实现外设地址与进程虚拟地址空间的一段虚拟地址对应关系
        
        - 进程视角：采用指针直接读写操作这一段内存，避免调用read()、write()产生内存拷贝开销

        - 内核视角：内核对该区域的修改将直接反映到用户空间，从而实现不同进程间的文件共享

- DirectByteBuffer：堆外直接内存，继承于MappedByteBuffer，由ByteUtils.allocate()或Channel.map()方法获得

    - ByteUtils.allocate()：调用Unsafe.allocateMemory0 => `os:malloc()`申请内存

    - Channel.map()：调用FileChannel.map() => kernel.mmap64得到虚拟内存地址

    > linux下申请内存就是通过`brk()`和`mmap()`两个系统调用，除了这两个之外的内存管理函数（如malloc()），都是上层运行库封装出来的，底层都要走到这两个函数调用上

# **10. Netty的零拷贝实现**

读取磁盘数据，然后发送数据：

- 传统I/O，使用`read()、write()`

    - 上下文切换：4次，每个系统调用会产生两次上下文切换

    - 内存拷贝：4次

        - 第一次：从磁盘内存缓冲区拷贝到kernel page cache中（DMA）
        - 第二次：从kernel page cache中拷贝到用户进程堆空间（CPU）
        - 第三次：从用户进程堆空间拷贝到kernel socket buffer（CPU）
        - 第四次：从kernel socket buffer拷贝到网卡高速缓存（DMA）

    - 优化思路：减少到用户空间的拷贝开销

- 在无须修改数据的情况下，使用`sendfile() + SG-DMA`

    > FileChannel.transferTo()

    - 上下文切换：2次，sendfile()合并了read和write

    - 内存拷贝：2次

        - 第一次：从磁盘内存缓冲区拷贝到kernel page cache（DMA）
        - 第二次：从kernel page cache拷贝到网卡高速缓存（SG-DMA）

    - 效果：由于无须修改数据，完全绕开了用户空间的拷贝；并且通过SG-DMA直接进入到网卡高速缓存中

- **在需要修改数据的情况下，使用`mmap() + write()`**

    > netty的directByteBuf、jdknio的directByteBuffer使用这种方式：mmap()虚拟内存对应映射socket fd（虚拟空间映射kernel socket buffer + kernel socket buffer映射socket fd）

    - 上下文切换：4次

    - 内存拷贝：3次

        - 第一次：从磁盘内存缓冲区拷贝到kernel page cache（DMA）
        - 第二次：从kernel page cache中拷贝到kernel socket buffer（CPU）
        - 第三次：从kernel socket buffer拷贝到网卡高速缓存中（DMA）

    - 优化思路：不可避免需要进程操作，则使用mmap()建立起虚拟内存与文件的映射，避免内核缓冲区到用户进程堆空间的拷贝

- 大文件传输，使用`异步I/O + 直接I/O`

    > 不使用page cache的称为直接I/O，反过来称为缓存I/O

    - 上下文切换：4次，依旧使用read()和write()

    - 内存拷贝：3次

        - 第一次：将数据从磁盘内存缓冲区拷贝到用户空间，通过异步I/O进行通知（DMA）

        - 第二次：用户空间拷贝到kernel socket buffer（CPU）

        - 第三次：kernel socket buffer到网卡高速缓存（DMA）

    - 优化思路：绕开page cache，以免因为填充page cache，导致预读功能无效化影响小文件的传输效率

结论：传输大文件时，使用异步I/O+直接I/O；传输小文件时，使用零拷贝技术

# **11. tomcat的线程模型**

在linux上采用epoll多路复用，线程模型采用`多Reactor多线程`模型

- Acceptor：主Reactor，负责端口监听与调用`ServerSocketChannel.accept()`建立新连接

    - accept()：从全连接队列中取出tcp三次握手**完毕**的socket fd

    - OP_REGISTER：将新连接包装为队列任务，加入到Poller的阻塞队列中

- Poller：从Reactor，负责client socket的**读监听**（读取数据），但不负责数据的read
    
    - synchronizedQueue：用于接收主Reactor加入的任务，当有任务加入队列时，**会唤醒Poller的selector**

    - wakeupCounter：并发程度计数器，当计数器大于>0时，selector将调用`selectNow()`及时返回

    - selector：用于监听client socket的读就绪事件，同时配合socketProcessor对http请求行和请求头的**非阻塞读**
    
        当客户端发送数据后，selector阻塞返回并通过I/O线程池进行处理

- Tomcat I/O线程池（对应SokcetProcessor）:处理read、decode、compute、encode、write全过程

    - SocketProcessor：线程会通过ConnectionHandler获得**绑定的socket**进行轮询操作

    - 读取http首部（请求行、请求头）采用busy waiting进行**非阻塞读**，这意味着不阻塞I/O线程

        如果出现读不完数据，则由Poller线程继续监测下次数据的到来

    - 读取http主体（数据）采用`BlockPoller + CountDownLatch`进行阻塞读

        请求体数据未读完毕且不可读，则注册封装OP_READ事件到BlockPoller的阻塞队列中，并利用`NioSocketWrapper`中的readLatch**阻塞**I/O线程

- BlockPoller：实现SocketProcessor对请求体数据的阻塞读取

    - synchronizedQueue

    - selector

# **12. Servlet**

> servlet线程安全吗？

Servlet在单实例模式下是`线程不安全`的，不同的SocketProcessor通过Context的分发，最终都会调向同一个servlet（StandardWrapper）

多例模式的Servlet已被废弃，会造成大量的servlet对象频繁创建和回收，效率差

> servlet生命周期？

共三个阶段：初始化、service调用、销毁

流程：在请求对应的servlet时初始化，然后以单例形式常驻内存中，并在最终服务器关闭时销毁

> struct2和spring mvc是如何实现的？

前者通过**filter**实现，后者通过新增一个servlet来实现，即struct2在更上个层级实现

```java
public class StandardWrapperValue {
    @Override
    public final void invoke(Request request, Response response) {
        // ...

        // 创建过滤器链
        ApplicationFilterChain filterChain =
                ApplicationFilterFactory.createFilterChain(request, wrapper, servlet);
        // ...

        // 调用过滤器链，最终会传递调用servlet对应的wrapper
        filterChain.doFilter(request.getRequest(),
                                response.getResponse());
    }
}
```

> filter生命周期？

共三个阶段：

- init：在WrapperValue构造该filter链时，进行初始化

- doFilter：请求处理过程调用

- destroy：**请求处理结束**，删除请求链时进行销毁

# **12. Actor模型思想**

# 参考

- [网络篇夺命连环12问](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247488227&idx=1&sn=36587eab67d87824179dd5edda3533db&chksm=c2b25a1ef5c5d308ae02ba5a2e5922738fd43305faf74c41320272acecc77d6a155eb50ad33a&token=982147105&lang=zh_CN&scene=21#wechat_redirect)
- [netty高低水位流控（yet）](https://www.cnblogs.com/silyvin/p/12145700.html)
- [详解大端模式和小端模式](https://www.cnblogs.com/little-white/p/3236548.html)

# 重点参考
- [从底层介绍epoll（相当全面）](https://www.toutiao.com/i6683264188661367309/)
- [Netty系列文章之Netty线程模型](https://juejin.cn/post/6844903974298976270)
- [小林coding-8张图搞懂「零拷贝」](https://zhuanlan.zhihu.com/p/258513662)
- [Netty——发送消息流程&高低水位](https://www.cnblogs.com/caoweixiong/p/14676840.html)

- [Tomcat NIO(5)-整体架构](https://mp.weixin.qq.com/s?__biz=MzI0MDE3MjAzMg==&mid=2648393624&idx=1&sn=ddb2852d26daa1a16a74e183a0d4ac08&chksm=f1310af7c64683e183431a43ab5e4528a1d53d2ba4d7f591d8b5da1a401f3737a378476cc1c8&scene=178&cur_album_id=2123520319532400647#rd)：Acceptor、Poller、I/O线程池、BlockPoller