# ZooKeeper：ZAB协议

Zookeeper Atomic Broadcast（ZAB，Zookeeper原子消息广播协议），为Zookeeper分布式协调服务专门设计的协议，定义了那些会改变Zookeeper服务器数据状态的事务请求的处理方式，模式包括：`崩溃恢复`和`消息广播`

**集群角色：**

- Leader：同一时间内，集群有且只有一个Leader，提供对客户端的读/写功能，并负责将数据同步到各个节点中
- Follower：提供对客户端的读功能，写请求则转发给Leader处理，当Leader宕机失联后参与Leader选举
- Observer：与Follower类似，但不参与Leader选举

**Zookeeper服务状态：**

> 各个Zookeeper节点都可以通过**服务状态**来区分自己所属的角色，并执行自己的任务

```java
// QuorumPeer.java
public enum ServerState {
    LOOKING,
    FOLLOWING,
    LEADING,
    OBSERVING
}
```
- LOOKING：当节点认为集群没有leader（出现在服务刚启动，或是Leader宕机），服务器进入LOOKING状态以**查找或者选举**Leader
- FOLLOWING：当前节点为Follower角色
- LEADING：当前节点为Leader角色
- OBSERVING：当前节点为Observer角色

# **1. 特点**

**zk集群技术特点：**

`高性能`：**单一Leader**接收并处理客户端的所有事务请求，其他follower接收到客户端请求也会转发给leader处理，支持接收客户端大量并发请求

`过半机制`：**集群中只要有过半的机器正常工作，那么整个集群对外就是可用的**

- 脑裂问题：

    在没有过半机制下，那些follower与leader失联（**假死、网络异常**）且follower之间互联的follower会重新选举出一个新leader，这时集群就出现了新、旧两个leader

    > **zk的过半选举机制可以一定程度防止脑裂出现，即使出现脑裂状况，过半提供服务机制会关闭服务提供，避免出现数据不一致**
        
    - 过半选举机制：集群在选举新leader的过程中也必须遵循**过半原则**

    - 过半提供服务：集群脑裂为两个集群时，首先如果某个集群的机器没有过半，则该子集群无法正常对外提供服务

- 奇数部署：由于过半机制下，宕掉多个zk节点后，剩余的节点必须大于`N(总数)/2`，集群才可继续使用，所以容忍度为`ceil(N/2)-1`

    如：3个节点和4个节点容忍度都为1，那么部署4个节点纯属浪费资源

    > 所以在向上取整的情况下，部署奇数个zk节点更节省资源

**CAP**：

`C 一致性`：写入一致性

`A 可用性`：基本可用

`P 分区容忍性`

- 二机房部署：无法实现较好容灾效果，主要机房应部署更多的机器，次要机房部署较少机器，保证次要机房出现异常时，zk集群依旧可对外提供服务

    若有5台机器，3台机器放在主要机房，2台机器放在次要机房，保证第一次选举的leader在主要机房中，当主要机房和次要机房的网络出现故障时，不会出现次要机房选举出新leader的情况，且次要机房无法对外提供服务

- 三机房部署：**zk集群的教科书式部署方式**，在奇数部署的大前提下，结合过半机制将奇数个机器分配到不同机房中，三个机房任意有且只有一个机房出现问题时，都具备容灾能力

    假设N1，N2，N3各自为三个机房的机器数量，**默认向上取整**：

    - N1 = (N - 1) / 2

    - N2：[1, (N - N1) / 2]

    - N3 = (N - N1 - N2)，且N3 < N1 + N2

    若有5台机器，则N1 = 2，N2 = 2，N3 = 1，假设leader是3号机房的单机，当3号机房与1、2号机房的网络出现故障，且1号机房和2号机房仍能互通，则：

    1. 3号机房而言，当前互通机器只有它自身，不满足过半原则所以无法正常对外提供服务

    2. 1、2号机房总和满足过半原则，重新选举leader后，继续对外提供服务

- 预防措施

    - 冗余通信（Redundant communications）：也称冗余心跳线，集群采用多种通信方式，防止一种通信方式失效就立即导致集群中的节点无法通信

    - 仲裁机制：当心跳线完全断开时，使用ICMP（ping）来检测网络不通是否为自身问题，裁定后将让能够ping通的端服务更为妥当

# **2. 内容**

ZAB共有4种状态，反应了zk集群从选举到对外提供服务的过程：

```java
public enum ZabState {
    ELECTION, // 选举准leader阶段
    DISCOVERY, // 发现阶段
    SYNCHRONIZATION, // 同步阶段
    BROADCAST // 广播阶段
}
```

- ELECTION：集群进入选举状态，该过程会选出一个节点作为准leader角色
- DISCOVERY：连接上leader，响应leader心跳，并且检测leader角色是否有更改，通过此步骤后选举出的leader才能执行真正职务
- SYNCHRONIZATION：整个集群都确认leader之后，将会把leader的数据同步到各个节点，保证整个集群的数据一致性
- BROADCAST：广播状态，集群开始对外提供服务

## **2.1 zk集群运行流程**

围绕**崩溃恢复、消息广播**两个模式，使用**leader过半选举、主备状态同步**等手段：

1. 服务框架启动

    服务刚启动时，ZAB协议就会进入**恢复模式**选举产生新的leader
    
    当产生新leader后，且**集群中已经有过半的机器与该leader服务器完成了状态同步**后，ZAB协议就会退出数据恢复模式

    > 状态同步：指主备间的数据同步，用来保证集群中存在过半机器能与leader数据状态保持一致

    ZAB状态：ELECTION -> DISCOVERY -> SYNCHRONIZATION -> BROADCAST
    
    服务状态：LOOKING -> FOLLOWING / LEADING / OBSERVING

2. 进入消息广播模式（数据同步）

    > 简化版2pc + Paxios过半思想，Zookeeper实现了以上的主备模式的系统架构，来保持集群中各个副本的数据一致性

    如果非leader的服务器在接收到客户端的事务请求时，会将该请求首先转发给leader服务器

    - 所有事务请求由全局唯一的Leader服务器协调处理，余下的其他服务器则称为Follower服务器

    - Leader负责将客户端的事务请求转换为一个**事务提议（Proposal）**，并将该Proposal分发给集群中的所有Follower服务器

    - 之后Leader服务器需要等待Follower服务器的反馈，**一旦有过半**的Follower服务器进行了ACK反馈后，Leader将会再次向所有的Follower服务器分发Commit消息，要求它们将Proposal进行提交

    ZAB状态：BROADCAST
    
    服务状态：FOLLOWING / LEADING / OBSERVING

3. 新节点加入

    当一台同样遵守ZAB协议的新节点启动后加入到zk集群时，如果**此时已存在一个leader**在负责消息广播，那么新加入的节点就会自觉的进入到数据恢复模式

    找到leader所在服务器，与其进行数据同步，最后参与到消息广播流程中

    ZAB状态：ELECTION -> DISCOVERY -> SYNCHRONIZATION -> BROADCAST
    
    服务状态：FOLLOWING / OBSERVING

4. **崩溃恢复**

    leader服务器宕机或是机器重启，或者由于网络分区导致不存在过半服务器与leader保持正常通信，那么集群**将暂时不对外提供服务**，并进入崩溃恢复模式，直到重新选举出leader并半数状态同步后，才能提供正常服务

    在正常的2pc过程中，如果协调者中途宕机崩溃，参与者将可能出现以下问题：

    - 参与者无限期等待：出现在**一阶段（提交事务请求）**，当协调者向各个参与者发出事务执行请求后立即宕机，参与者执行事务操作锁定资源，却迟迟无法收到二阶段提交，从而进入无限期等待
    
    - 各个参与者数据不一致：出现在**二阶段（执行事务提交）**，当协调者收到**全部参与者（ZAB过半即可）**的ACK反馈后，应向各个参与者发起提交请求，但在发送过程中宕机，出现某些参与者的无法收到二阶段提交
    
    崩溃恢复机制可以保证，重新选取出新的leader，避免leader崩溃导致以上的两个问题

    ZAB状态：ELECTION -> DISCOVERY -> SYNCHRONIZATION -> BROADCAST
    
    服务状态：LOOKING -> FOLLOWING / LEADING / OBSERVING

## **2.2 zab流程**

```java
// set：quorum集合，表示当前集群一个子集
// n：集群部署总节点数
return (set.size() > n / 2);
```

**zxid**：zab协议下的事务编号，64位，低32位是一个单调递增的计数器，高32位是Leader周期的epoch（纪元）编号

1. **准leader选举阶段**（ELECTION）

    - 服务器启动的leader选举

        1. 每台server服务器发出一个投票，每个服务器都会将自己作为leader服务器进行投票，每次投票都会包含二元组（server_id, zxid）

        2. 接收来自其他服务器的投票，检查是否是本轮投票，是否来自LOOKING状态的服务器

        3. 处理投票：优先检查zxid大小，以zxid较大的为准；zxid相等则检查server_id，以server_id较大的为准，最后修改自己的投票信息

        4. 统计投票，每台服务器都会统计投票信息，当服务器接收到过半的投票信息，则两台服务器事实上已得出最新leader

        5. 改变服务器状态，如果leader是自己，则更改服务状态LOOKING为LEADING，否则更改LOOKING为FOLLOWING

    - 服务器运行过程中的leader选举

        1. 改变服务器状态，Leader挂后，FOLLOWING服务器会将状态变更为LOOKING，进入选举过程

        2. 每个server服务器发出一个投票，依旧先将自己的情况作为第一轮投票的选票情况
        
        3. 接收来自各个服务器的投票

        4. 处理投票，此时已经处于运行期间，zxid可能不会像初始时相同

        5. 统计投票

        6. 改变服务器状态

2. **发现阶段**（DISCOVERY）

    通过ELECTION获得了一个准leader，但此时该leader仍未成为事实上的leader，集群也暂未对外提供服务

    1. 接收follower的连接，包括心跳连接建立

    2. 计算新的epoch值，将epoch值 + 1得到新epoch值

    3. 通知统一epoch值，follower随后将epoch值更新为准leader的epoch值

3. **同步阶段**（SYNCHRONIZATION）

    > 整个同步阶段类似2pc

    discovery阶段后，准leader已经称为了leader，并且向所有follower同步了新的epoch

    本阶段则进行epoch的全量更新

    1. leader将epoch对应的事务集合发送给各个follower

    2. 当follower接收到同步数据

        - 如果当前自身的epoch值 != leader epoch，说明服务器崩溃重启/较慢启动，无法参与本轮同步

        - 否则，则按照新epoch的每个proposal进行检查，缺少的部分进行同步更新，**成功后进行ack反馈**

        > 同步更新不会提交事务，会先锁定相关资源

    3. leader获得过半反馈后，向所有follower发送commit消息，至此leader完成同步阶段

    4. follower收到commit消息，依次提交未提交的事务信息

4. **广播阶段**（BROADCAST）

    1. 接收到客户端的事务请求，leader根据epoch、zxid生成对应proposal，向follower发送提案（放到发送队列中串行发送）

    2. 客户端从接收队列中以依次取出提案，将提案加入到已处理事务集合中，并进行事务操作锁定资源，若成功则反馈leader ack

    3. 接收到过半follower的反馈后，leader发送该proposal的commit消息给所有follower

    4. follower接收到对应proposal的commit消息，提交事务操作
    
    > proposal是依次被操作的，所以保证了写入一致性

# **3. 与Paxos区别**

- [Zookeeper的ZAB协议与Paxos协议区别](http://blog.itpub.net/31509949/viewspace-2218255/)

Paxos分为：读阶段、写阶段

ZAB分为：发现阶段、同步阶段、广播阶段

其中ZAB发现阶段等同于Paxos读阶段，广播阶段等同于Paxos写阶段，同步阶段是ZAB协议新添加的

> ZAB用于构建一个高可用的分布式数据主备系统

通过同步阶段后，leader会确保存在过半follower已经提交了之前leader纪元中的所有事务proposal，**即确定新的广播阶段开始前，所有进程都完成了对之前所有事务的提交**

> Paxos算法用于构建一个分布式的一致性状态机系统



# 参考
- [从Paxios到Zookeeper]()
- [Zookeeper脑裂以及解决办法](https://www.pianshen.com/article/8831768506/)
- [准leader的选举过程](https://www.cnblogs.com/leesf456/p/6107600.html)
- [zookeeper leader、follower同步](https://www.jianshu.com/p/d53fb7d4bfe6)

# 重点参考
- [说一下Zookeeper的ZAB协议](https://zhuanlan.zhihu.com/p/143937967)
- [zk集群的脑裂问题](https://www.cnblogs.com/kevingrace/p/12433503.html)：奇数部署 + 脑裂分析
- [Zookeeper的ZAB协议与Paxos协议区别](http://blog.itpub.net/31509949/viewspace-2218255/)

- [后续Raft算法](https://zhuanlan.zhihu.com/p/147691282)