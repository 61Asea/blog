# interview ten：tomcat + netty

- 同步/异步、阻塞/非阻塞
- I/O模型：同步阻塞模型、同步非阻塞模型、异步模型
- select()/poll()和epoll()的区别
- Reactor模型
- Netty线程模型
- 半包、粘包问题
- MappedByteBuffer、DirectByteBuffer、malloc()
- 大端小端问题
- 零拷贝：sendfile + mmap()
- Tomcat线程模型
- actor

# **1. 同步/异步、阻塞和非阻塞**

> 如何理解同步和阻塞

以`进程`视角分为两个步骤：

1. 发起I/O请求

2. 实际的I/O读写操作

以`操作系统`视角分为两个步骤：

1. kernel数据准备，将数据从**磁盘缓冲区搬运都内核缓冲区**（DMA）

2. 将数据从内核空间拷贝到进程空间中（CPU）

- 同步、异步：指是否由**进程自身占用CPU**，将数据从内核拷贝到进程空间中，是的话为同步，否则为异步

    - 异步：由**内核占用CPU**将数据拷贝到进程中，完成后通知进程，进程在此期间可以处理自己的任务

- 阻塞、非阻塞：指进程在**发起I/O请求**后是否处于阻塞状态

    - 阻塞I/O模型：同步阻塞I/O

    - 非阻塞I/O模型：同步非阻塞I/O（包括I/O多路复用）、异步I/O

    BIO会阻塞，NIO不会阻塞，`对于NIO而言通过channel进行IO操作请求后，其实就返回了`

# **2. I/O模型**

1. 同步阻塞I/O

    进程视角：进程在发起I/O请求后**阻塞至I/O读写操作完毕**
    
    系统视角：等待kernel数据完毕后，**进程自身占用cpu拷贝**

    数据由DMA从磁盘/网卡搬运到内核空间中，再由中断信号唤醒进程，进程占用CPU将数据从内核空间拷贝到用户空间

2. 同步非阻塞I/O：可直接代指I/O多路复用

    进程视角：进程在发起I/O请求后**可以立即返回**
    
    系统视角：当kernel数据准备完毕后，需**进程自身占用cpu拷贝**

    `I/O多路复用`：进程发起I/O请求后可以立即返回，由一个I/O线程（selector）检测多个注册在其上句柄的就绪状态：

    - select、poll：将进程加入到socket的等待队列中，由socket接收完毕数据后唤醒

    - epoll：引入eventpoll epfd作为进程与socket的中介，socket完毕后会调用中断程序使得rdlist持有socket的引用，实现句柄就绪后的通知

3. 异步I/O

    进程视角：进程在发起I/O请求后不会进入阻塞状态
    
    系统视角：kernel数据准备完毕后，由**内核占用cpu拷贝**到进程空间，完成后通知进程

    - CPU密集型系统：表现不佳，因为内核占用cpu势必导致进程的cpu可分配资源降低，会增加该类型系统计算延时

    - I/O密集型系统：表现佳（如node.js、aio）

# **3. select()与epoll()的区别**

都用于**监视多个socket句柄**的状态，epoll相比select更加高效

- select()：

    1. 从进程空间向内核空间传入socket列表FD_SET，遍历FD_SET后将进程加入到各fd的等待队列中，selector进入阻塞状态

        第一次遍历FD_SET，用于将进程加入到对于socket的等待队列中，涉及FD_SET到内核的内存拷贝

    2. 某socket接收到数据，发出cpu中断信号，cpu执行中断程序进行数据搬运，并唤醒进程

        - 数据搬运：数据从网卡缓冲区搬运到kernel socket buffer

            > DMA：进行 I/O 设备和内存的数据传输的时候，数据搬运的工作交给 DMA 控制器，而 CPU 不再参与任何与数据搬运相关的事情

            优化后步骤：某socket接收到数据后，**向DMA发送中断信号**，由DMA负责数据搬运而不占用CPU。DMA完成搬运后，再向CPU发起中断信号，`CPU执行中断程序后，再唤醒进程`

        - 唤醒进程：将进程从所有socket fd的等待队列中移除

        第二次遍历FD_SET，从而得知哪些socket等待队列上有该进程，所以该次遍历**同样需要**将FD_SET传递到内核

    3. 进程只知道至少有一个socket接收了数据，需要遍历FD_SET获取就绪的fd，并根据可读/可写事件调用read()/write()

        第三次遍历FD_SET，这次遍历在进程用户空间进行，主要是获知已就绪的fd

        NIO：通过mmap() 直接打通了kernel socket buffer到用户空间，无需内存拷贝，具体实现是使用DirectByteBuffer（extends `MappedByteBuffer`）

- epoll()：

    1. 通过epoll()的函数对fd进行注册

    - epoll_create()：创建epfd（eventpoll）
    
        - epfd：本身就是一个红黑树，用于存储注册在其上的句柄，从而**避免每次调用都需要传入fd集合产生拷贝**
        
        - epfd.rdlist：epfd包含一个双向链表实现的就绪队列，当有socket的数据就绪后，将由DMA、CPU配合执行中断程序将就绪fd加入到队列中，从而避免每次进程还需重新遍历fd集合获取就绪的fd
    
    - epoll_ctl()：添加需要监视的socket fd到epfd中，**内核会将eventpoll加入到这些socket的等待队列**

    - epoll_wait()：进程进入epfd的等待队列中，进入阻塞状态

        监视的socket fd基本不会变动，后续无需每次都进行注册，进一步减少了第一次遍历

    2. socket接收到数据后，向DMA发起中断信号。DMA读取数据完毕后，向CPU发起中断信号执行中断程序，cpu将执行**设备驱动回调**，将就绪的文件描述符加入到epfd的rblist就绪队列中

        epfd作为进程与socket的中介者，socket的等待队列将一直持有eventpoll引用，无需移出队列减少了select第二次遍历

    3. 驱动将就绪socket fd加入rdlist，并唤醒在epfd等待队列上的进程

        进程可直接在rdlist中获取到就绪fd，减少了select的第三次遍历

总结：

1. 句柄数增多导致的性能问题

    - select()/poll()：需要对FD_SET进行三次线性遍历，在句柄数越多时表现越差，select最多监视1024个，poll使用链表结构可以监视更多句柄，但依旧没有改善效率低的问题

    - epoll()：没有影响，因为是通过注册设备回调实现就绪列表rblist，只有活跃的socket才会主动调用，进程可以直接从就绪队列获取到就绪

2. 消息传递方式

    - select()：每次调用select()都会将FD_SET从用户空间中拷贝到内核空间

    - epoll()：fd存放在内核eventpoll中，在**用户空间和内核空间的copy仅需一次**

    > epoll没有使用mmap！

3. 监视方式：都在阻塞状态中由中断信号唤醒，select()由socket直接唤醒，epoll()由socket间接通过eventpoll唤醒

# **3附加. JDK的epoll空转bug**



# **4. BIO、NIO、AIO**

- BIO：同步阻塞I/O，进程在发起I/O请求后进入阻塞状态等待I/O完成

    网络结构：one connection per thread，每个客户端连接都对应一个处理线程，没有分配到线程的连接会阻塞等待或拒绝

- NIO：同步非阻塞I/O，进程发起I/O请求后不会进入阻塞状态，对channel进行读写操作会立即返回，由多路复用器selector来监视注册的fd并进行数据读写操作

    网络结构：基于Reactor模型，由reactor（selector）提供句柄描述符的注册/回调机制，并根据就绪事件类型分发到不同的handler中进行处理（OP_ACCEPT、OP_READ、OP_WRITE）

- AIO：异步I/O，进程发起I/O请求后不会进入阻塞状态，由内核占用cpu完成对I/O请求的处理，并通知进程进行处理

    网络结构：nodejs

# **4附加：NIO的buffer是双向的吗？**



# **5. Reactor**

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

# **6. netty的线程模型**

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

# **7. 什么是粘包、半包，如何解决**

# **8. MappedByteBuffer、DirectByteBuffer、malloc()**

JDK.NIO高性能：

- MappedByteBuffer：对应使用`mmap()`的虚拟内存，由FileChannel.map()实现

    - mmap()：内存映射文件，将**一个文件映射到用户进程的地址空间**，实现外设地址与进程虚拟地址空间的一段虚拟地址对应关系
        
        - 进程视角：采用指针直接读写操作这一段内存，避免调用read()、write()产生内存拷贝开销

        - 内核视角：内核对该区域的修改将直接反映到用户空间，从而实现不同进程间的文件共享

- DirectByteBuffer：堆外直接内存，继承于MappedByteBuffer

# **9. Netty的零拷贝实现**



# **10. 大/小端**

# **11. tomcat的线程模型**

# **12. Actor模型思想**

# 参考

- [网络篇夺命连环12问](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247488227&idx=1&sn=36587eab67d87824179dd5edda3533db&chksm=c2b25a1ef5c5d308ae02ba5a2e5922738fd43305faf74c41320272acecc77d6a155eb50ad33a&token=982147105&lang=zh_CN&scene=21#wechat_redirect)

# 重点参考
- [从底层介绍epoll（相当全面）](https://www.toutiao.com/i6683264188661367309/)
- [Netty系列文章之Netty线程模型](https://juejin.cn/post/6844903974298976270)