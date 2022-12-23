# interview eleven：zookeeper

# **Zookeeper的理解**

分布式协调的集中式服务，提供**高可用**、**顺序访问控制**的能力，用于解决分布式环境下**一致性**的问题

具有以下特点：

- `顺序一致性` ：leader会根据请求顺序生成**全局唯一的递增编号**（**ZXID**），加入到**队列**中严格保证按照FIFO顺序执行

- `原子性` ：所有事务请求的处理结果在整个集群中所有机器上都是一致的；不存在部分机器应用了该事务，而另一部分没有应用的情况

    > 只要有机器应用（commit）事务，即使leader最终宕机，最终一定会通过master选举和**宕机恢复**恢复

    假设leader在发送了部分commit后宕机，会通过从follower中重新选举，将当前事务ID最大的结点作为新leader，后续再**通过数据同步达到数据一致性**

- 单一视图 ：所有客户端看到的服务端数据模型都是一致的

    依旧会有不一致的情况出现，来源于**过半写入机制**，结点只要有过半写入成功即代表整个集群写入成功，如果恰好有请求打在**未写入完毕**的结点，会出现查询不一致的情况

    > 我们在讨论CAP时，**默认忽略延迟**导致的一致性问题，延迟的过程必然会带来数据的不一致。zk只能保证在一段时间后数据必定进入**最终一致性**

- 可靠性 ：一旦服务端成功应用了一个事务，则其引起的改变会一直保留，直到被另外一个事务所更改

- `实时性` ：一旦一个事务被成功应用后，Zookeeper仅能**保证在一段时间后**，客户端**最终**可以读取到这个事务变更后的最新状态数据

# **zk运用场景**

1. Master选举：利用zk节点的**全局唯一性**，同时只有一个客户端能够创建成功的特性，创建成功则作为master

2. 分布式协调：zk核心使用方式，利用watcher监听机制，当一个系统的某个节点状态发生改变，另外的系统可以得到通知

3. `分布式锁`：利用**临时顺序节点**的特性可实现公平锁

    - 在父节点之下创建临时顺序节点，并判断当前节点是否为最小节点，是则加锁成功；否则获取上一个节点，并添加Watcher进行阻塞等待

    - 天然支持结点宕机的问题，结点在宕机后会自动消失

4. 注册中心：服务提供者将自己提供的服务注册到zk，服务消费者从zk中获得生产者的服务列表信息，再去调用生产者的内容数据

    ![rpc的注册中心](https://asea-cch.life/upload/2022/03/rpc%E7%9A%84%E6%B3%A8%E5%86%8C%E4%B8%AD%E5%BF%83-8887e890c8ab4b3981a167494bb12a33.png)

# **zk集群架构**

分为`zk服务集群`与`客户端`，客户端可以连接到zookeeper集群服务的任意**非leader服务器**（leaderServes配置默认为false）上

> 除非显式配置leaderServes，**否则默认leader不允许客户端连接**

![cs](https://mmbiz.qpic.cn/mmbiz_jpg/ibBMVuDfkZUmqb0t9xOJOXebntahoHMGMCm6fTFSXooaLURMMJJxQfvA9pJqicu1gJGUxUalNTKQHibArrPfOw2HA/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)

1. 服务端：zookeeper server集群

    - 基于Leader的**非对等**部署

        - 单点写一致性：只有leader节点可以写入数据，其他节点收到写入数据的请求，则会将之**转发给leader节点**

        - Leader：`发起投票`、`提议决议`、`更新系统状态`、`写数据`、数据同步、过半写成功提交

        - Follower：接收客户端请求（写请求转发给leader节点，自身只负责读请求）、`参与投票`、状态被同步、写入成功的返回响应

        - Observer：接收客户端请求（写请求转发给leader节点，自身只负责读请求）、状态被同步、`横向扩展`

            > Observer不参与投票过程，只同步leader的集群状态，Observer的目的是**避免太多follower节点参与过半写过程，影响性能**。这样Zookeeper只要使用一个几台机器的小集群就可以实现高性能了，如果要横向扩展的话，只需要增加Observer节点即可
            
    - 过半机制：只要过半机器正常工作，那么整个集群对外就是可用的，用于**解决脑裂问题**

        - 过半选举：选举过程中，某台zkServer获得超过半数选票，称为leader

        - `过半写`：只要过半写入成功，就代表整个集群写成功

        - 脑裂问题：仅出现在没有过半机制下，如果出现网络分区（多机房部署），则分区下可能出现新的leader，整个集群就出现了两个leader

            过半机制的解决方式：**少于一半节点**的分区无法选举出新leader

            - 奇数部署（容忍度）：在过半机制下，宕掉多个zk节点后，剩余节点必须大于`总数 / 2`集群才可正常提供服务，所以`容忍度` = `ceil(N/2) - 1`

                所以3台和4台的容忍度都为1，部署3台可以更节省资源

    - 可靠措施：减少zk集群各节点间假死情况的出现

        - 冗余通信：集群服务间建立**冗余心跳线**，防止单一通信线失效就立即导致集群节点无法通信

        - 仲裁机制：当心跳线完全断开时，使用ICMP检测网络不同是否为自身问题

2. 客户端：业务服务的zk client

    - 会话session：指客户端与zk集群服务之间维护的tcp长连接，通过这个连接进行`心跳检测`、`获取Watcher`、客户端发送信息、接收zk服务结果响应

# **如何保证顺序一致性（ZAB）**

![](https://mmbiz.qpic.cn/mmbiz_jpg/ibBMVuDfkZUmqb0t9xOJOXebntahoHMGMNOMJz5ibicXbq9DbBvotd05SEtuJJMggvxe7NktQyXTFSumDjDyJzHYA/640?wx_fmt=jpeg&wxfrom=5&wx_lazy=1&wx_co=1)

通过支持**崩溃恢复**的ZAB**原子广播**协议，类似2PC两阶段的过程，提交保证集群结点间的**顺序**、**最终**一致性

- 主要流程：

    1. leader收到写请求之后转换为`proposal提议`，并为proposal分配一个`全局唯一递增的事务ID`（ZXID）后，将提议放入到`FIFO队列`，按照FIFO策略将请求**发送给所有的follower**

        > 同一时间的写请求，通过ZXID识别先后顺序

    2. Follower收到提议后，以`事务日志`形式**写入**到本地磁盘（prepare），写入成功后返回ACK到leader

    3. leader收到**过半follower**的ack响应后，即可认为集群数据写入成功，就会发送`commit命令`给所有follower，让它们提交proposal

- 两种模式：崩溃恢复（启动） -> 消息广播（启动完毕） -> 再次进入崩溃恢复（断网、重启） -> 消息广播（恢复完毕）

    1. 崩溃恢复
    
    - 进入：`集群启动`，`leader网络中断，或宕机重启`等异常情况，ZAB协议就会进入到崩溃恢复状态选举出新的leader

    - 退出：过半机器与新leader完成状态同步后，退出恢复模式，剩余未同步完毕的机器继续同步，直到同步完毕加入到集群后，该结点服务才可用

    2. 消息广播

    - 进入：**过半follower**与leader完成状态同步，zk集群进入消息广播模式

        当有新加入的服务器（旧leader、新机器）启动后加入到集群时，会自动进入数据恢复模式，与leader进行数据同步

    - 退出：leader宕机、重启等异常情况

# **zk集群master选举**

三个关键参数：epoch纪元、zxid、serverid

节点状态：LOOKING、FOLLOWING、LEADING

分为两种情况：

- 启动时的master选举：节点发起自身投票、收到其它节点投票、`处理投票`、`统计投票`、状态变更

    初始状态：LOOKING，zk单机不会进入选举状态，两台及以上会进入

    由于存在自身发起投票，和处理投票后的再次发出，所以共有两次投票

    - 发起投票：每个Server发出初始投票，该投票都会**选择自己作为leader**服务器，投票内容包含`（myid，zxid）`
    
    - 收到投票：收到其他结点的投票信息，检查是否是本轮选举`epoch`，是否来源于follower服务器

    - 处理投票：判断收到的投票的（myid，zxid）二元组的大小，选出其中最大的二元组。变更投票信息后**再次发出**

        > 以zxid大的为先，若zxid相同，再以myid为准

    - 统计投票：每次投票后，zk统计投票信息，当发现有**过半**机器接收到相同的投票信息，则认为已选出leader

    - 状态变更：若最终投票信息是自身服务，则从LOOKING -> LEADER，否则从LOOKING -> FOLLOWER

- 运行过程中的master选举：`状态变更`、发起投票、收到投票、处理投票、统计投票

    相比启动过程，多了一开始的状态变更，通常出现在leader崩溃退出等异常情况，follower将从FOLLOWER -> LOOKING

# **数据同步**

leader在收到请求时，会按照收到的顺序为每个proposal分配ZXID，并加入到FIFO队列中

涉及`3种值`：

- lastSyncZxid/peerLastZxid：Observer、Follower中最新的ZXID

- minCommittedLog/minZxid：leader的proposal队列中的zxid最大值

- maxCommittedLog/maxZxid：leader的proposal队列中的zxid最小值

围绕这3个值，共有`4种数据同步方式`：

- 差异化同步：

    - 值：minCommitedLog< lastSyncZxid < maxCommittedLog，且**都处于同一纪元**

    - 场景：旧leader崩溃，新leader选举完成之后

    - 做法：

        1. leader向follower发出`DIFF`命令，代表开始差异化同步，发送**lastSyncZxid到maxCommittedLog之间**的proposal给follower，再发送`NEWLEADER`命令给follower

        2. follower接收到DIFF、提议和NEWLEADER命令后进行同步，同步完成后发送ACK响应

        3. leader收到**半数以上的learner**的ack后，认为同步完成（集群可用），发送`UPTODATE`命令给learner

        4. learner收到UPTODATE命令后，响应ack，并开始提供服务（当前follower可用）

- 回滚同步

    - 值：lastSyncZxid > maxCommittedLog，且**不处于同个epoch**

    - 场景：旧leader崩溃恢复（**已生成proposal，但未发出写入命令**），重新加入集群，且当前集群没有新的proposal产生

    - 做法：
    
    leader发送`TRUNC`命令，旧leader接收后直接回滚到当前纪元的maxCommitedLog，提议丢失

- 回滚同步 + 差异化同步

    - 值：minCommitedLog < lastSyncZxid < maxCommitedLog，后两者**不处于同一纪元**

    - 场景：旧leader崩溃恢复，重新加入集群，且当前集群已经产生新纪元下的新提议

    - 做法：
    
        1. leader发送`TRUNC命令`，旧leader接收后回滚到新leader旧纪元下的最大Zxid提议状态

        2. leader发送`SYNC命令`，旧leader接收后，按照差异化同步进行

- 全量同步

    - 值：
    
        - 新leader不存在提议队列的缓存，并且被同步者的lastSyncZxid != leader的最大Zxid

        - lastSyncZxid < leader的minCommitedLog

    - 场景：

        - 第一种值情况

        - 第二种情况，相当于崩溃后的zk结点过久没有恢复，导致差异数据较大

    - 做法：

        - leader向follower和observer发送`SNAP`命令，进行数据全量同步

# **数据不一致场景**

- `查询不一致`

    过半写成功则代表集群写入成功，但可能有的结点还未同步完毕，这种情况下zk保证在一段时间后能进入到最终一致

- `leader未发送proposal宕机`

    **事务日志丢失**，崩溃恢复重新加入集群后发现不在同一个epoch，会进行回滚

- leader发送proposal，但发送commit时宕机

    **事务日志不回丢失**，发送proposal成功，则重新恢复选举出来的master（zxid最大的节点）一定会有这个日志，会在后续的数据同步过程中再次同步给其它的learner

# **znode数据结构**

四种结点：

- 持久结点：`create /hello/x`，一旦被创建，除非被主动移出，否则将一直存在zk中

- 临时结点：`create -e /hello/x`，生命周期与客户端session一致，当**客户端断开连接后**，zk会自动删除临时结点

- 顺序结点：`create -s /hello/x`，会在结点名称后从小到大顺序，自动添加数字（10位大小）

- 临时顺序结点：`create -s -e /hello/x`，同时具备临时结点和顺序结点的特性

    > 天然适合实现公平模式的zk分布式锁

存储内容：

- acl：节点访问权限，如IP

- data：节点具体的业务数据

- child：节点的子节点

- stat：节点状态信息，包括事务ID，纪元

> curator提供递归删除节点和递归创建节点功能

# **Watcher机制**

client对zk节点注册Zookeeper Watcher，加入到client内的WatcherManager。

推拉结合：当节点发生变动时，zookeeper server通知client节点发生变动（`推`），client再拉取节点最新数据（`拉`）

Watcher的4个特征：

- 一次性：Watcher在zk server通知后会失效，需要重新设置为true

- 轻量级：zk服务的通知是以WatchEvent为单位的，WatchEvent的轻量级体现在其**不包含节点的具体信息**，需要zk client再自行拉取，这种模式也称为推拉结合

- 串行执行：client收到server通知后，从WatcherManager中取出该Watcher的回调逻辑，并对逻辑**串行执行**

    > 需要注意，执行逻辑不要阻塞过长时间，否则会导致一个Wacher对整个zookeeper client阻塞过久

- **异步通知**：zk server发送watcher通知到zk client的过程是异步的，不能保证节点的每个更新都监控完整，只能保证**最终一致**

Watcher的坑：

- 异步通知，不保证每次变化都能监控到，只能保证最终一致的监控，所以不适合对于节点数据变化敏感的场景

- Watcher的回调逻辑不宜阻塞过久，应结合线程池进行分发处理，防止client因某段回调逻辑阻塞过久

# **zk实现分布式锁、信号量、闭锁**

参考`org.apache.curator`实现：

- 公平锁（默认实现）：依托**临时顺序节点**实现，具备可重入性与互斥性
    - 公平锁，类似等待队列的方式实现，可以解决惊群效应

    - 可具备重入性，通过当前持锁节点的信息判断是否是持有锁线程自身

    - cp特性使其可以呈现redis cluster无法解决的锁丢失问题
        - redis：解决单点问题引入cluster，但数据一致性无法得到保障，可能出现锁丢失问题（数据同步）
        - zk：zab消息广播保证数据的最终一致性，不担心锁丢失情况

    - 性能较差，频繁新增、删除节点，对leader而言写压力和同步成本过于庞大
    
    - 没有超时机制，需要进行补偿，防止服务假死导致锁无法释放
- 读写锁
- 联锁
- 信号量/闭锁

1. 公平锁

    思路：锁key作为**持久类型的父节点**进行展开，zk集群根据调用创建接口的顺序分配序列号，在父节点下为对应客户端创建**临时顺序节点**，并将序列号拼接到子节点名称之后

    - 互斥获取：父节点的所有子节点集合，按照`节点名称`（对比序列号）进行排序，并获取上一步返回的节点名称在有序集合中的index

        - 若节点index < maxleases（默认maxleases = 1，即集合首位），则说明当前线程已获取到锁

        - 若节点index >= maxleases，则获取`index - maxleases`（前一个）位置的节点，对其添加Watcher，以监视节点移除，再进入**阻塞状态**

        > 最终形成了一个类似JDK.AQS的同步队列，队列中的每个节点，都会等待上一个节点状态的变更

    - 释放：删除对应的zk节点，触发下一个节点的Watcher返回，以对阻塞的节点进行唤醒

2. 读写锁

    思路：依托公平锁的实现，读、写操作的节点名称会附加不同的标识（序列号的递增与操作类型无关）

    - 重入线程，读写不互斥

    - 读读不互斥：遍历所有子节点，获取`最小写锁子节点的索引`

        - 子节点包含写锁节点索引，判断操作线程节点与最小写索引的大小，若ourIndex < firstWriteIndex，则获取读锁

        - 子节点不包含写锁节点索引，直接获取到读锁

    - 读写互斥：ourIndex > firstWriteIndex，则对前一个节点添加Watcher，并进入阻塞状态等待

3. 联锁：在超时时间内，对多个锁进行统一获取，要么全部成功，要么全部失败

    - 超时时间：
        
        - redis：默认1500ms * 子锁个数

        - zk：默认阻塞直至获取成功

    - 全部成功：采用阻塞获取方式获取锁，只有在获取到全部锁之后才能执行业务

    - 全部失败：一般出现在超时情况下，**对已成功的锁进行解锁操作**，若超时则释放全部锁

4. 信号量/闭锁

    redisson：将计数维护在redis中，结合Publish/Subscribe实现

    zk：维护

# **zookeeper的CP特性指的是什么？**

> CAP关注的应是数据的读写，选举等可以认为不在考虑范围内

同机房不可用：当出现网络分区时，小于半数机器数分区的zk集群将因为过半机制关闭服务提供，即使是同个分区机房下的service也无法使用

zookeeper
- C：
    - 过半提供服务机制，不会产生脑裂现象，从而影响数据的一致性
    - 数据同步采用zab协议，保证各节点间的数据一致性
- A：
    - 过半提供服务虽然保证了数据的一致性，但是也间接降低了系统的可用性
    - zab协议同步数据期间，系统不可用

[ZooKeeper 并不适合做注册中心 - 安能的文章 - 知乎](https://zhuanlan.zhihu.com/p/98591694)：注册中心无需cp特性，因为数据的不一致仅仅会导致负载的短暂不均衡，所以zookeeper并不适合做注册中心

# 参考
- [Zookeeper夺命连环9问](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247488995&idx=1&sn=990d099cd9724931da9a414da549d093&chksm=c2b25d1ef5c5d40821fe69e42fadb96312c02654ffb921cddc9801c8ab3c4f4d3e5cae2b0b9c&token=982147105&lang=zh_CN&scene=21#wechat_redirect)

- [主流微服务注册中心浅析和对比](https://my.oschina.net/yunqi/blog/3040280)

- [12道Zookeeper](https://zhuanlan.zhihu.com/p/418125310)

- [ZooKeeper 分布式锁 Curator 源码 01：可重入锁](https://zhuanlan.zhihu.com/p/390210597)
- [ZooKeeper 分布式锁 Curator 源码 02：可重入锁重复加锁和锁释放](https://zhuanlan.zhihu.com/p/391823831)
- [ZooKeeper 分布式锁 Curator 源码 03：可重入锁并发加锁](https://zhuanlan.zhihu.com/p/392245217)