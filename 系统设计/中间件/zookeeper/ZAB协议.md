# ZooKeeper：ZAB协议

Zookeeper Atomic Broadcast（ZAB，Zookeeper原子消息广播协议），为Zookeeper分布式协调服务专门设计的协议，定义了那些会改变Zookeeper服务器数据状态的事务请求的处理方式，模式包括：`崩溃恢复`和`消息广播`

**集群角色：**

- Leader：同一时间内，集群有且只有一个Leader，提供对客户端的读/写功能，并负责将数据同步到各个节点中
- Follower：提供对客户端的读功能，写请求则转发给Leader处理，当Leader宕机失联后参与Leader选举
- Observer：与Follower类似，但不参与Leader选举

**Zookeeper服务状态：**

> 各个Zookeeper节点都可以通过**服务状态**来区分自己所属的角色，并执行自己的任务

- LOOKING：当节点认为集群没有leader（出现在服务刚启动，或是Leader宕机），服务器进入LOOKING状态以**查找或者选举**Leader
- FOLLOWING：当前节点为Follower角色
- LEADING：当前节点为Leader角色
- OBSERVING：当前节点为Observer角色

# **1. 特点**

```java
// set：quorum集合，表示当前集群一个子集
// n：集群部署总节点数
return (set.size() > n / 2); 
```

**zk集群技术特点：**

`高性能`：**单一Leader**接收并处理客户端的所有事务请求，支持接收客户端大量并发请求

`过半机制`：**集群中只要有过半的机器正常工作，那么整个集群对外就是可用的**

- 脑裂问题：在没有过半机制时，当leader与follower之间并没有真正

    > zk的过半机制可以防止脑裂出现

- 奇数部署：当宕掉多个zk节点后，剩余的节点必须大于`N(总数)/2`，这样zk集群才可继续使用，所以容忍度为`ceil(N / 2) - 1`

    > 所以在向上取整的情况下，部署奇数个zk节点更节省资源

**CAP**：

`P 分区容忍性`：

- 二机房部署

- 三机房部署

`A 可用性`：

`C 一致性`：CP

# **2. 内容**

## **2.1 Leader选举**

**服务器启动期间：**


## **崩溃恢复**

## **2.2 消息广播**

> 2pc + Paxios过半思想，Zookeeper实现了以上的主备模式的系统架构，来保持集群中各个副本的数据一致性

1. 所有事务请求由全局唯一的Leader服务器协调处理，余下的其他服务器则称为Follower服务器

2. Leader负责将客户端的事务请求转换为一个**事务提议（Proposal）**，并将该Proposal分发给集群中的所有Follower服务器

3. 之后Leader服务器需要等待Follower服务器的反馈，**一旦有过半**的Follower服务器进行了ACK反馈后，Leader将会再次向所有的Follower服务器分发Commit消息，要求它们将Proposal进行提交


# 参考
- [从Paxios到Zookeeper]()
- [Zookeeper脑裂以及解决办法](https://www.pianshen.com/article/8831768506/)

# 重点参考
- [说一下Zookeeper的ZAB协议](https://zhuanlan.zhihu.com/p/143937967)
- [zk集群的脑裂问题](https://www.cnblogs.com/kevingrace/p/12433503.html)：奇数部署 + 脑裂分析