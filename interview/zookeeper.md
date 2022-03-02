# interview eleven：zookeeper

# **1. Zookeeper的理解**

顺序一致性 ：从一个客户端发起的事务请求，最终都会严格按照其发起顺序被应用到Zookeeper中

原子性 ：所有事务请求的处理结果在整个集群中所有机器上都是一致的；不存在部分机器应用了该事务，而另一部分没有应用的情况

单一视图 ：所有客户端看到的服务端数据模型都是一致的

可靠性 ：一旦服务端成功应用了一个事务，则其引起的改变会一直保留，直到被另外一个事务所更改

实时性 ：一旦一个事务被成功应用后，Zookeeper可以保证客户端立即可以读取到这个事务变更后的最新状态的数据

# **zk运用场景**

# **zk集群架构**

- 基于Leader的非对等部署（单点写一致性）

- 过半机制（防脑裂）

# **master选举**

# **数据同步**

proposal

minCommittedLog

maxCommittedLog

# **如何保证顺序一致性（ZAB）**

# **数据不一致场景**

# **znode**

# **watcher**

# **zk实现分布式锁**

# **如何理解CAP**

# 参考
- [Zookeeper夺命连环9问](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247488995&idx=1&sn=990d099cd9724931da9a414da549d093&chksm=c2b25d1ef5c5d40821fe69e42fadb96312c02654ffb921cddc9801c8ab3c4f4d3e5cae2b0b9c&token=982147105&lang=zh_CN&scene=21#wechat_redirect)

- [主流微服务注册中心浅析和对比](https://my.oschina.net/yunqi/blog/3040280)