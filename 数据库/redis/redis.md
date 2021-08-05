# Redis

# **1. 数据库存储与实现**

## **1.1 服务器/客户端结构**

`redis.h/redisServer`的db数组中，每一项都是一个redisDb结构，一个redisDb代表一个数据库

在初始化服务器时，程序会根据`dbnum`属性来决定初始化数组容量 -> 创建对应个数的数据库，默认该选项的值为16个，由服务器配置的database选项决定

```c++
// redis服务器结构
struct redisServer {
    // ...

    // 服务器的数据库数量
    int dbnum;

    // 一个数组，保存着服务器中的所有数据库
    redisDb *db;
};
```

我们在使用shell命令`SELECT [num]`选择数据库后，redis客户端结构中的db指针，将会指向到服务器`db数组`下标的**数据库结构**

```c++
// redis客户端结构
typedef struct redisClient {
    // ...

    // 记录客户端当前正在使用的数据库
    redisDb *db;
} redisClient;
```

![redisServer&redisClient](https://asea-cch.life/upload/2021/08/redisServer&redisClient-f91bebeab4464c29b6e860c8bcbfe69d.png)

## **1.2 数据库结构**

数据库本身使用`dict`字典实现，dict保存了用户存储的所有键值对，我们称这个dict为键空间（`key space`）：

- key space中的各个键，即数据库的键，都是一个字符串对象
- key space中的各个值，即数据库的值，每个值可以是5种对象的任意一种

```c++
// redis数据库结构
typedef struct redisDb {
    // ...

    // 数据库键空间，保存着数据库中的所有键值对
    dict *dict;
} redisDb;
```

图为存有三个键值对的数据库：

![db](https://asea-cch.life/upload/2021/08/db-29644952bceb4f2dbb5226f0092bf18e.png)

所有针对数据库的操作的具体实现可以参照**hashtable操作**

## **1.3 读写操作时的维护操作**

处理用户对redis键空间的读写操作时，还会执行一些额外的操作

1. 计算缓存命中率

    处理读请求时，根据键是否存在来更新服务器的`命中次数(hit)`或`不命中次数(miss)`，记录缓存命中率

2. **更新LRU时间**

    除开`OBJECT IDLETIME`指令外，其他指令对键做操作时都会更新键的LRU时间，这个涉及到缓存淘汰策略或过期策略

3. **过期键的被动触发**

    处理读请求时，如果键已经过期，那么服务器会先删除这个过期键，再执行余下的操作

4. **WATCH监视键**

    与事务相关，如果键被某个客户端使用`WATCH`命令监视，当键值发生变动时会将键标记为脏，从而让事务程序感知到该键已经被修改

5. **脏页计数**

    与**持久化**和**主从复制**相关，当键被修改后，会对脏键计数器递增1，这个计时器会触发服务器的持久化和主从复制

6. 数据库通知功能

    如果服务器开启了数据库通知功能，对键修改后，服务器将按配置发送相应的数据库通知

# **2. 键的生存周期**



# 参考
- [Redis设计与实现]()