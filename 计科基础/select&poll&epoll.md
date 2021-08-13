# I/O多路复用

> [IO模型]()：讲述了五大IO模型，并归纳了同步IO、异步IO的区别，其中I/O多路复用通过选择器的思想，实现one socket thread per与监测数据准备的解耦，下文将详细讲解I/O多路复用的`三大模型`和`reactor模式`

`就绪状态`：指的是kernel的数据已准备就绪状态，在该状态下会使用某种方式通知进程

![IO多路复用](https://asea-cch.life/upload/2021/08/IO%E5%A4%9A%E8%B7%AF%E5%A4%8D%E7%94%A8-07df85c4276b48ed8abec21febe777bb.gif)

I/O多路复用，可以理解为在阻塞IO和非阻塞IO之前多添加了一步：使用select、poll和epoll函数中的其中一种，通过单线程来**监测指定文件描述符的数据就绪状态**

以上三种函数分别对应了I/O multiplexing的三种模型，它们仅仅处理了**数据是否准备好**以及**如何通知进程**的问题，仍需要结合阻塞I/O或者非阻塞I/O模式使用，通常结合`非阻塞I/O`使用

> I/O多路复用模型的整个过程有**两个阻塞**，第一个阻塞指的是进程调用select/poll/epoll后的阻塞，这个阻塞是可设置修改的（NULL，0，>0），通常结合`阻塞IO模式`；第二个阻塞指的是，函数监测到有就绪的数据时，调用read()/recefrom()系统调用进行读取，read()/recefrom()通常结合`非阻塞I/O模式`

这是因为数据并不是一次性发送完毕的，**而且多路复用只会告诉你 fd 对应的 socket 可读了，但不会告诉你有多少的数据可读**

对多个就绪的fd进行read()时：
- 使用`阻塞I/O`，那只能read()一次，你无法知道下一次read/accept会不会发生阻塞
- 使用`非阻塞I/O`，可以循环的read和accept，直到读完目前能读的所有数据（抛出EWOULDBLOCK异常）

# **1. select()**

fd_set相关的C语言宏：

```c
// 与fd_set相关的C语言宏
#define FD_SET(n, p)    ((p)->fds_bits[(n)/NFDBITS] |= _fdset_mask(n))
#define FD_CLR(n, p)    ((p)->fds_bits[(n)/NFDBITS] &= ~_fdset_mask(n))
#define FD_ISSET(n, p)  ((p)->fds_bits[(n)/NFDBITS] & _fdset_mask(n))
#define FD_COPY(f, t)   bcopy(f, t, sizeof(*(f)))
#define FD_ZERO(p)      bzero(p, sizeof(*(p)))
```

## **1.1 流程**

1. 调用宏FD_ZERO(&fds)，将指定的fd_set（描述符集合）清空
2. 调用宏FD_SET(fd, &fds)，将需要监测的fd加入到fd_set中
3. 调用select()函数监控所有的描述符集合，select()返回就绪文件数量通知进程
4. 调用宏FD_ISSET(fd, &fds)，监测传入的fd是否就绪，并根据就绪情况执行相应操作

### **1. 调用宏FD_ZERO(&fds)，清空描述符集合**

```c++
typedef struct {
    // long int = int
    // fd_set的长度为1024：32 * 32 = 1024
    long int fds_bits[32];
} fd_set;
```

`fd_set`：是一个数组的宏定义，实际上是一个long int类型的数组，数组元素的`每个比特位`对应一个打开的文件句柄（socket、文件、管道或设备等）

`FD_ZERO(&fds)`：将指定的文件描述符清空，防止下一次使用出现问题
 
### **2. 调用宏FD_SET(fd, &fds)**

`FD_SET(fd, &fds)`：用于在文件描述符集合中增加一个新的文件描述符

在上面伪代码中，共往描述符集合中添加了一个socket和一个文件描述符

### **3. 调用select()函数监控所有的描述符集合**

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

调用select后()，进程将进入阻塞状态，直至select()返回**就绪的描述符集合**

### **4. 调用宏FD_ISSET(fd, &fds)，监测传入的fd是否就绪，并根据就绪情况执行相应操作**

select返回的就绪描述符集合可能并不包含进程关注的fd，所以需要调用FD_ISSET进行判断

`FD_ISSET(fd, &fds)`：位运算，用于判断select返回的就绪fd_set中是否包含自己关注的fd

当发现确实包含在就绪文件集中，则由进程自发地发起recv()系统调用，陷入内核态，在recv()过程中使用非阻塞I/O模型

> 因为数据并不是一次性发送完毕的，所以使用非阻塞I/O模型，以防止某fd数据未发送完毕前可能会导致**其他fd的读取阻塞**

handle_read()具体的伪代码如下：

```python
def handle_read():
    socket.setblocking(False)
    while True:
        try:
            data = socket.recv(1024)
        except socket.err, e:
            # 关键的代码，当发现数据未发送完毕时，直接返回EWOULDBLOCK，让下一个fd读
            if e.args[0] in (errno.EWOULDBLOCK, errno.EAGAIN):
                return
            raise
```

## **1.2 总结**

整个流程的伪代码（应用程序映射到内核流程）：

```c++
main() {
    int sock;
    FILE *fp;
    // 文件描述符集合，一个long int类型的数组，每一个
    struct fd_set fds;
    // select的超时时间设置，NULL为阻塞，0为非阻塞，>0表示超过多少秒后超时
    struct timeval timeout = {3, 0};
    char buffer[256] = {0};

    // 建立TCP
    sock = socket(...);
    bind(...);

    // 打开文件
    fp = fopen(...);

    while(1) {
        FD_ZERO(&fds);
        FD_SET(sock, &fds);
        maxfdp = sock > fp ? sock + 1 : fp + 1;
        // 注意：select()系统调用时，进程开始从用户态陷入内核态，将fd_set传入到kernel中，并阻塞
        switch (select(...)) { 
        // 内核在select过程中会遍历fd_set，判断有没有就绪的fd，fd会返回一个描述读写操作是否就绪的mask掩码，给fd_set赋值进行修改
            // 当进入以下case时，进程从内核态返回用户态
            case -1 : 
                exit(-1);
                break;
            case 0:
                break;
            default:
                if (FD_ISSET(sock, &fds)) {
                    // 处理读取，里面有while(1) + 非阻塞的读取，防止某fd数据未发送完毕前可能会导致其他fd的读取阻塞
                    handle_read();
                    if (FD_ISSET(fp, &fds)) {
                        // 写入文件
                        fwrite(fp, buffer...);
                    }
                }
        }
    }
}
```

优点：

- 遵循POSIX规范的操作系统，都支持select

缺点：

- 使用long int类型的fd_set数组，意味着最多监控**1024个**描述符，如果要监听更多描述符的话，需要修改FD_SETSIZE之后重新编译
- 性能随着监控的文件描述符增多而快速下降，具体体现为：
    - 每次调用select()时，需要将fd集合从用户态拷贝到内核态，开销随fd数量增大
    - select()过程中，需要在内核中遍历所有的fd，开销随fd数量增大

时间复杂度：O(N)

## **1.3 学习过程中的问题思考**

问题1：select()函数返回就绪文件数量时，有没有通知进程？

> 答案：select返回时，返回的是就绪的文件符集合，再调用FD_ISSET传入检测的fd进行遍历，所以不是通知进程，而是进程自己做遍历

问题2：第二次阻塞，到底是调用read()阻塞，还是调用FD_ISSET阻塞？

> 答案：调用read()，因为第一点：select()和read()中间是有窗口期的，select()返回的时候，并不一定保证fd是可读（惊群效应）；第二点：数据的发送不是一次性发送完毕的，select只能知道有数据要读，而不知道有多少数据可读，所以，一般会通过while(1)加遍历fd的方式遍历来进行read()，如果使用阻塞IO模型，则会很可能会导致遍历到某个未发送完毕的fd，而直接阻塞

问题3：FD_ISSET()干了什么？它在文章中描述“遍历整个描述符集合，并将满足就绪条件的描述符发送给进程”，这个时候是已经将数据从内核拷贝到进程内存了吗？ 

> 答案：未拷贝到进程内存，FD_ISSET()只是简单的位操作运算，用于运算fd_set就绪描述符集是否包含监测的fd

# **2. poll**

> [I/O多路复用之poll](https://blog.csdn.net/fengxinlinux/article/details/75303969)

poll()模型处理方式**与select()模型的本质类似**，都是传入一组描述符并轮询监测它们的就绪状态

```c++
# include <poll.h>

int poll(struct pollfd *fds, nfds_t nfds, int timeout);
```

## **2.1 区别**

**区别1：描述fd集合的方式不同，使用pollfd结构**

```c++
struct pollfd {
    int fd; // 文件描述符
    short events; // 注册的事件
    short revents; // 实际发生的事件，由内核填充
} pollfd;
```

> 在使用时，根据需要的事件个数来初始化数组，而不是像fd_set一样固定是长度为32的long int数组，这意味着没有最大连接数量限制

所以从用户空间传递到内核空间进行监测时，只需要**传递被监控的文件描述符**，select()则是传递固定大小的数据结构

**区别2：pollfd提供更多的事件类型，对描述符的重复利用更高**

| 事件类型 | 描述 |
| ---- | ---- |
| POLLIN | 普通或优先级带数据可读 |
| POLLOUT | 普通数据或优先级带数据可写 |
| ... | ... |

在poll()方法返回时，会修改fds数组中的所有pollfd，为它们的revents填充`事件的返回值`，这个`pollfd.revents`返回值，用于与fd关心的事件类型`pollfd.events`进行`&`操作：
    
如果操作的结果值大于0，则代表该事件已就绪，否则认为，该事件未就绪

```c++
if (fds[0].revents & fds[0].events) {
    // 大于0，事件就绪，可以将返回值置空
    fds[0].revents = 0;
    // 执行操作
    handle_read();
}
```

## **2.2 总结**

流程伪代码：
```c++
main() {
    // 数组长度根据进程需要的个数决定，这里开启两个socket，所以长度为2
    struct pollfd fds[2];

    // 开启两个socket：sock1，sock2
    int sock1 = socket(...);
    bind(...);
    int sock2 = socket(...);
    bind(...);

    // sock1注册了可读事件
    fds[0].fd = sock1;
    fds[0].events = POLLIN;

    // sock2注册了可写事件
    fds[1].fd = sock2;
    fds[1].events = POLLOUT;

    switch (poll(&fd, 2, 10000)) {
        case -1:
            exit(-1);
            break;
        case 0:
            break;
        default:
            // 拿填充的返回值，与fds[0]关心的事件做"&"操作
            if (fds[0].revents & fds[0].events) {
                // 大于0，事件就绪，可以将返回值置空
                fds[0].revents = 0;
                // 读操作
                handle_read();
            }

            if (fds[1].revents & fds[1].events) {
                fds[1].revents = 0;
                // 发送操作
                handle_write();
            }
    }
}
```

优点（相比select）：
- 每次调用poll()时，从用户空间传入内核空间的描述符集合变小，也没有个数的限制
- poll()过程中，轮询监测pollfds的效率要比监测fd_set效率高

缺点：
- 仍然会随着需要监测文件描述符变多而导致性能下降，本质上还是与select一致
- poll()返回时，需要遍历位运算revents和events的结果，引入了新的性能问题

时间复杂度：O(N)

# **3. epoll**

> [I/O多路复用之epoll](https://blog.csdn.net/fengxinlinux/article/details/75452740)

epoll()是比select()、poll()更先进的一种模型，是Linux特有的I/O复用函数，在实现和使用上与select()与poll()有巨大差异：
- epoll()使用一组函数来完成任务，而不是单个函数

    ```c++
    int epoll_create(int size);

    int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event);

    int epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout);
    ```

- epoll把用户关心的fd上的事件放在**内核的一个事件表中**

    > 从而避免像select()和poll()一样每次调用，都要重复传入fd_set或pollfds

- epoll()**不通过轮询**的方式处理，可以极大地提升性能

    > select/poll：轮询监测传入的fd集，有就绪时返回

## **3.1 流程**

1. 调用epoll_create(int size)，创建epoll实例
2. 调用epoll_ctl()，修改epoll的兴趣列表
3. 调用epoll_wait()等待事件，获得事件时即可执行相应操作

### **3.1 调用epoll_create(int size)，创建epoll实例**

```c++
#include <sys/epoll.h>
// 创建epoll实例
int epoll_create(int size);
```

`作用`：epoll需要**额外使用一个fd**，来唯一标识内核中的这个事件表。因为epoll()把用户进程关心的fd上的事件，放在**内核的一个事件表**

`返回值`：该函数返回的fd将用作其他所有epoll系统调用的第一个参数，以指定要访问的内核时间表

> 注意：额外使用一个fd的话，在使用完epoll后必须close关闭掉该fd，否则会导致fd耗尽

### **3.2 调用epoll_ctl()，修改epoll的兴趣列表**

```c++
#include <sys/epoll.h>
// 关键操作
int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event);
```
- epfd：epoll_create()返回的文件描述符
- op：指定操作类型，操作类型有以下三种
    - EPOLL_CTL_ADD：注册新的fd到epfd中
    - EPOLL_CTL_MOD：修改已注册fd的事件
    - EPOLL_CTL_DEL：从epfd中删除一个fd
- fd：要操作的文件描述符
- event：指定事件的类型，与poll()相似，但有额外的两个事件类型`EPOLLET`和`EPOLLONESHOT`

其中epoll_event的定义如下：
```c++
struct epoll_event {
    __uint32_t events; // epoll事件
    epoll_data_t data; // 用户数据
} epoll_event;
```
- events，代表感兴趣的事件类型：

    | 事件类型 | 描述 |
    | ---- | ---- |
    | EPOLLIN | 可读取非高优先级数据 |
    | EPOLLOUT | 普通数据可写 |
    | **EPOLLET** | 采用边沿触发事件通知 |
    | **EPOLLONESHOT** | 在完成事件通知后禁用检查 |
    | .... | .... |

- data：用于存储用户进程的数据，其中ptr指向的，应该就是传说中的进程fd管理器？？？
    ```c++
    typedef union epoll_data {
        void *ptr;
        int fd; /*关键成员，指定事件所丛书的目标文件描述符*/
        __uint32_t u32;    
        __uint64_t u64; 
    } epoll_data_t;
    ```
    - [探讨epoll原理（红黑树、rdlist的实现）](https://www.cnblogs.com/zhilong233/p/13410719.html)

    底层实现：红黑树数据结构实现

    > 每个epoll对象都有一个独立的eventpoll结构体，通过eventpoll管理存放后续epoll_ctl添加的事件集合，这些集合以epitem为结点**挂载在红黑树上**

    目的：添加到epoll中的事件，都会与设备驱动**建立回调关系**。当相应事件发生时，可以兼顾查找与增删的效率，避免遍历查找

    > 查找到结点后，会调用回调事件，将epitem加入到**rdlist就绪列表中**

`作用`：根据不同的op操作类型，对epfd描述符进行操作

`返回值`：成功时返回0，失败时返回-1并设置errno

<!-- 疑问：红黑树是在哪个数据结构上使用的 -->

### **3. 调用epoll_wait()等待事件，获得事件时即可执行相应操作**

#### **rdlist就绪事件列表**

> [](https://blog.csdn.net/lqy971966/article/details/111170106)

## **3.2 epoll触发条件**


## **3.3 总结**



# 参考
- [select函数及fd_set介绍](https://www.cnblogs.com/wuyepeng/p/9745573.html)
= [Java Socket与Linux Socket底层调用分析](https://www.cnblogs.com/fiveFish/p/12005960.html)
- [select、poll、epoll之间的区别(搜狗面试)](https://www.cnblogs.com/aspirant/p/9166944.html)
- [fd_set具体是怎样实现的](http://blog.chinaunix.net/uid-20680966-id-1896524.html)
- [select()系统调用的详细代码](https://blog.csdn.net/lizhiguo0532/article/details/6568964#comments)

# 重点参考
- [五种IO模型透彻分析](https://www.cnblogs.com/f-ck-need-u/p/7624733.html#3-1-select-poll-)
- [FD_ISSET()](https://blog.csdn.net/baidu_35381300/article/details/51736431)
- [为什么 IO 多路复用要搭配非阻塞 IO?](https://www.zhihu.com/question/37271342/answer/81607536)
- [操作系统——Select,poll,epoll](https://blog.csdn.net/qq_41963107/article/details/108406010)
- [I/O多路复用之select](https://blog.csdn.net/fengxinlinux/article/details/75268914)
- [I/O多路复用之poll](https://blog.csdn.net/fengxinlinux/article/details/75303969)
- [I/O多路复用之epoll](https://blog.csdn.net/fengxinlinux/article/details/75452740)