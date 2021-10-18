# socket编程

![socket和tcp](https://asea-cch.life/upload/2021/10/socket%E5%92%8Ctcp-e82fff58fc2243eb8d70393d91137074.png)

整个过程由**服务器程序**和**操作系统内核**完美配合

# 单/多线程原生态

- `socket()`：产生一个监听类型的socket fd，与客户端连接socket fd区分开来
- `bind()`：绑定socket的信息到系统中
- `listen()`：系统调用，监听客户端的连接请求（**第一次握手报文**）
- `accpet()`：从`全连接队列`中接收**已经三次握手完毕**的连接

在listen和accept的中间，包含了I/O模型的知识点，广为人知的select/poll/epoll在之中有所作为

# select

![select_poll](https://asea-cch.life/upload/2021/10/select_poll-89fd91ccea1b4a339e2501008e42839b.jpg)

结论：性能差，共有**3次内存拷贝，1次轮询**的开销，根本原因是fd_set的维护和对fd就绪的轮询耦合在了select()函数里

# epoll

![epoll](https://asea-cch.life/upload/2021/10/epoll-e4b77808a15246a69b7ab32355f494ed.jpg)

结论：并发量大的情况下性能佳，通过内核eventpoll进行维护避免多次内存拷贝，并且通过网卡设备驱动回调来避免轮询fd操作

# 参考
- [accpet()和select()的关系](https://www.cnblogs.com/zhchy89/p/8850048.html)

# 重点参考
- [java BIO/NIO的accept()方法](https://blog.csdn.net/Tom098/article/details/116107072)

- [c++处理客户端请求](https://blog.csdn.net/yang1994/article/details/115717915)：包含one connection per thread的阻塞accpet、非阻塞accpet、结合非阻塞accpet的select

- [epoll深度好文](https://blog.csdn.net/davidsguo008/article/details/73556811/)

- [select效率查的原因（总结到位）](https://blog.csdn.net/shixin_0125/article/details/72597808)

- [epoll的API](https://www.cnblogs.com/xuewangkai/p/11158576.html)