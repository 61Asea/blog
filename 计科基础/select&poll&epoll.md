# I/O多路复用

> [IO模型]()：讲述了五大IO模型，并归纳了同步IO、异步IO的区别，其中I/O多路复用通过选择器的思想，实现one socket thread per与监测数据准备的解耦，下文将详细讲解I/O多路复用的`三大模型`和`reactor模式`

`就绪状态`：指的是kernel的数据已准备就绪状态，在该状态下会使用某种方式通知进程

![IO多路复用](https://asea-cch.life/upload/2021/08/IO%E5%A4%9A%E8%B7%AF%E5%A4%8D%E7%94%A8-07df85c4276b48ed8abec21febe777bb.gif)

I/O多路复用，可以理解为在阻塞IO和非阻塞IO之前多添加了一步：使用select、poll和epoll函数中的其中一种，来**监测指定文件描述符的数据就绪状态**

以上三种函数分别对应了I/O multiplexing的三种模型，它们仅仅处理了**数据是否准备好**以及**如何通知进程**的问题，仍需要结合阻塞I/O或者非阻塞I/O模式使用，通常结合`非阻塞I/O`使用：

> I/O多路复用模型的整个过程有**两个阻塞**，第一个阻塞指的是进程调用select/poll/epoll后的阻塞，这个阻塞是可设置修改的（NULL，0，>0），通常结合`阻塞IO模式`；第二个阻塞指的是，函数监测到有就绪的数据时通知进程，进程再调用read()/recefrom()系统调用进行读取，这个过程通常结合`非阻塞I/O模式`

- select和poll：因为返回的是已就绪的数量，并不知道是哪些fd数据就绪，会通过线性扫描（轮询）的方式进行read()调用。如果结合阻塞I/O使用，就可能直接在轮询过程中，因为未就绪的fd上阻塞

- epoll：返回的是就绪链表，链表中的描述符一定都是处于就绪状态的，那么结合`阻塞I/O`或`非阻塞I/O`差距不大

以下分别对各个模式进行详细过程分析：

# **1. select()**

```c
#define FD_SET(n, p)    ((p)->fds_bits[(n)/NFDBITS] |= _fdset_mask(n))
#define FD_CLR(n, p)    ((p)->fds_bits[(n)/NFDBITS] &= ~_fdset_mask(n))
#define FD_ISSET(n, p)  ((p)->fds_bits[(n)/NFDBITS] & _fdset_mask(n))
#define FD_COPY(f, t)   bcopy(f, t, sizeof(*(f)))
#define FD_ZERO(p)      bzero(p, sizeof(*(p)))
```

1. 调用宏FD_ZERO()，将指定的fd_set（描述符集合）清空
2. 调用宏FD_SET()，将需要监测的fd加入到fd_set中
3. 调用select()函数监控所有的描述符集合，select()返回就绪文件数量通知进程
4. 调用宏FD_ISSET()，监测传入的fd是否就绪，并根据就绪情况执行相应操作
<!-- 
昨晚结束的地方：在思考，select()函数返回就绪文件数量时，有没有通知进程。第二次阻塞，到底是调用read()阻塞，还是调用FD_ISSET阻塞

即FD_ISSET()干了什么？它在文章中描述“遍历整个描述符集合，并将满足就绪条件的描述符发送给进程”，这个时候是已经将数据从内核拷贝到进程内存了吗？ 

-->



## **1.1 通过FD_ZERO清空描述符集合**

```c++
typedef struct {
    // long int = int
    // fd_set的长度为1024：32 * 32 = 1024
    long int fds_bits[32];
} fd_set;
```

`fd_set`：是一个数组的宏定义，实际上是一个long int类型的数组，数组元素的`每个比特位`对应一个打开的文件句柄（socket、文件、管道或设备等）

## **1.2 通过FD_SET创建描述符集合**

## **1.3 select()监控描述符集合**

```c++
// select系统调用
int select(int nfds, fd_set* readset, fd_set* writeset, fe_set* exceptset, struct timeval* timeout);
```

- 参数：
    - nfds：需要检查的文件描述符个数
    - `readset`；用来检查可读性的一组文件描述字
    - `writeset`：用来检查可写性的一组文件描述字
    - exceptset：用来检查是否有出现异常条件的文件描述子
    - `timeout`：select阻塞的超时时间，填NULL为阻塞；填0为非阻塞；大于0的指表示超时时间
        当设置为NULL时，表示进程被select()调用”阻塞“；当设置为0时，表示不会被阻塞，此时就需要用轮询监测的方式（不建议，效率低）
- 返回值：返回fd的总数

第一步：调用select()系统调用后，进程将进入“阻塞”状态，阻塞可以通过`timeout`参数进行修改

# **2. poll**

# **3. epoll**

# 参考
- [select函数及fd_set介绍](https://www.cnblogs.com/wuyepeng/p/9745573.html)
= [Java Socket与Linux Socket底层调用分析](https://www.cnblogs.com/fiveFish/p/12005960.html)
- [select、poll、epoll之间的区别(搜狗面试)](https://www.cnblogs.com/aspirant/p/9166944.html)
- [fd_set具体是怎样实现的](http://blog.chinaunix.net/uid-20680966-id-1896524.html)

# 重点参考
- [五种IO模型透彻分析](https://www.cnblogs.com/f-ck-need-u/p/7624733.html#3-1-select-poll-)
- [FD_ISSET()](https://blog.csdn.net/baidu_35381300/article/details/51736431)