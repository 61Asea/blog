# interview ten：tomcat + netty

# **1. 同步/异步、阻塞和非阻塞**

> 如何理解同步和阻塞

以`进程`视角分为两个步骤：

1. 发起I/O请求

2. 实际的I/O读写操作

以`操作系统`视角分为两个步骤：

1. kernel数据准备（DMA）

2. 将数据从内核空间拷贝到进程空间中（CPU）

- 同步、异步：指是否由**进程自身占用CPU**，将数据从内核拷贝到进程空间中，是的话为同步，否则为异步

    - 异步：由**内核占用CPU**将数据拷贝到进程中，完成后通知进程，进程在此期间可以处理自己的任务

- 阻塞、非阻塞：指进程在**发起I/O请求**后，是否处于阻塞状态

    - 阻塞I/O模型：同步阻塞I/O

    - 非阻塞I/O模型：同步非阻塞I/O（包括I/O多路复用）、异步I/O

    BIO会阻塞，NIO不会阻塞，`对于NIO而言通过channel进行IO操作请求后，其实就返回了`

# **2. I/O模型**

1. 同步阻塞I/O

    进程在发起I/O请求后**直接阻塞**，等待kernel数据完毕后，再**进程占用cpu拷贝**

    数据由DMA从磁盘/网卡搬运到内核空间中，再由中断信号唤醒进程，进程占用CPU将数据从内核空间拷贝到用户空间

2. 同步非阻塞I/O：可直接代指I/O多路复用

    进程在发起I/O请求后不会直接阻塞，当kernel数据准备完毕后，需**进程占用cpu拷贝**

    `I/O多路复用`：进程发起I/O请求后可以立即返回，由一个I/O线程（selector）检测多个句柄的就绪状态：

    - select、poll：持续轮询句柄的就绪情况

    - epoll：添加事件到硬件驱动中，实现句柄就绪后的通知

3. 异步I/O

    进程在发起I/O请求后不会进入阻塞状态，且kernel数据准备完毕后，由**内核占用cpu拷贝**到进程空间，完成后通知进程

    - CPU密集型系统：表现不佳，因为内核占用cpu势必导致进程的cpu可分配资源降低，会增加该类型系统计算延时

    - I/O密集型系统：表现佳（如node.js、aio）

# **3. select()与epoll()的区别**

两者都用于监视多个socket句柄的状态，epoll相比select更加高效

- select()：

    1. 传入socket列表FD_SET，遍历FD_SET并将进程加入到各fd的等待队列中，阻塞进程

        第一次遍历FD_SET，且涉及FD_SET到内核的内存拷贝

    2. 某socket接收到数据，发出cpu中断信号，cpu执行中断程序进行数据搬运，并唤醒进程

        - 数据搬运：数据从网卡缓冲区搬运到kernel socket buffer

            > DMA：进行 I/O 设备和内存的数据传输的时候，数据搬运的工作全部交给 DMA 控制器，而 CPU 不再参与任何与数据搬运相关的事情

            优化后步骤：某socket接收到数据后，向DMA发起中断信号，由DMA负责数据搬运而不占用CPU，DMA读完数据后再向CPU发起中断信号，`CPU执行中断程序后直接唤醒进程即可`

        - 唤醒进程：将进程从所有socket fd的等待队列中移除

        第二次遍历FD_SET

    3. 进程只知道至少有一个socket接收了数据，需要遍历FD_SET获取就绪的fd

        第三次遍历FD_SET

        NIO：通过mmap() + 堆外内存直接打通了kernel socket buffer到用户空间，无需内存拷贝

- epoll()：

    1. 通过epoll()的函数对fd进行注册

    - epoll_create()：创建epfd（eventpoll）
    - epoll_ctl()：添加需要监视的socket fd到epfd中，内核会将eventpoll加入到这些socket的等待队列
    - epoll_wait()：进程进入epfd的等待队列中，进入阻塞状态

        监视的socket fd基本不会变动，后续无需每次都进行注册，进一步减少了第一次遍历

    2. socket接收到数据后，向DMA发起中断信号。DMA读取数据完毕后，向CPU发起中断信号执行中断程序，cpu将执行**设备驱动回调**，将就绪的文件描述符加入到epfd的rblist就绪队列中

        epfd作为进程与socket的中介者，socket的等待队列将一直持有eventpoll引用，无需移出队列减少了select第二次遍历

    3. 驱动将fd加入rblist后，唤醒在epfd等待队列上的进程

        进程可直接在rblist中获取到就绪fd，减少了select的第三次遍历

总结：

1. 句柄数增多导致的性能问题

    - select()/poll()：需要对FD_SET进行三次线性遍历，在句柄数越多时表现越差，select最多监视1024个，poll使用链表结构可以监视更多句柄，但依旧没有改善效率低的问题

    - epoll()：没有影响，因为是通过注册设备回调实现就绪列表rblist，只有活跃的socket才会主动调用，进程可以直接从就绪队列获取到就绪

2. 消息传递方式

    - select()：将FD_SET从用户空间中拷贝到内核空间

    - epoll()：内核和用户进程共享内存（mmap()）

3. 监视方式：都在阻塞状态中由中断信号唤醒，select()由socket直接唤醒，epoll()由socket间接通过eventpoll唤醒

# **4. BIO、NIO、AIO**

- BIO：同步阻塞I/O，进程在发起I/O请求后进入阻塞状态等待I/O完成

    网络结构：one connection per thread，每个客户端连接都对应一个处理线程，没有分配到线程的连接会阻塞等待或拒绝

- NIO：同步非阻塞I/O，进程发起I/O请求后不会进入阻塞状态，基于Reactor模型下对channel进行读写操作会立即返回，由多路复用器selector来监视注册的fd并进行数据读写操作

    网络结构：多个socket绑定同一I/O线程

- AIO：异步I/O，进程发起I/O请求后不会进入阻塞状态，由内核占用cpu完成对I/O请求的处理，并通知进程进行处理

    网络结构：nodejs

# **5. Reactor**

Reactor模型分为：reactor和handler，前者负责I/O事件的监听与**分发**，后者负责I/O事件**绑定与处理**

- 单Reactor单线程模型

# **6. netty的线程模型**

多Reactor多线程模型

# **7. tomcat的线程模型**

# **8. 什么是粘包、半包，如何解决**

# **9. 零拷贝**

# 参考

- [网络篇夺命连环12问](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247488227&idx=1&sn=36587eab67d87824179dd5edda3533db&chksm=c2b25a1ef5c5d308ae02ba5a2e5922738fd43305faf74c41320272acecc77d6a155eb50ad33a&token=982147105&lang=zh_CN&scene=21#wechat_redirect)

# 重点参考
- [从底层介绍epoll（相当全面）](https://www.toutiao.com/i6683264188661367309/)