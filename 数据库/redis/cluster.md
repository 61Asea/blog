# Redis高性能：cluster

Redis cluster（Redis集群）是分布式数据库方案，集群通过分片来进行数据共享，并提供复制和故障转移等功能

> [集群和主从复制](https://asea-cch.life/achrives/集群和主从复制)

# **1. 节点**

节点可以选择是否开启集群模式，集群模式下会保持大部分单机模式的功能

每个节点都会维护一个`clusterState`结构，用于保存集群中的其他节点信息，以及各自相对于负责的`槽信息`

```c++
typedef struct clusterState {
    // 指向当前节点的指针
    clusterNode *myself;

    // 集群当前的配置纪元，用于实现故障转移
    uint64_t currentEpoch;

    // 集群当前的状态：是在线还是下线
    int state;

    // 集群中至少处理着一个槽的节点数量
    int size;

    // 集群节点名单
    // 字典的键为节点的名字，值为节点对应的clusterNode结构
    dict *node;
} clusterState;
```

```c++
struct clusterNode {
    // 创建节点的时间
    mstime_t ctime;

    // 节点的名字，由40个十六进制字符组成
    char name[REDIS_CLUSTER_NAMELEN];

    // 节点标识：标识节点角色（主节点或从节点）/节点目前所处状态（在线或下线）
    int flags;

    // 节点当前的配置纪元，用于实现故障转移
    uint64_t configEpoch;

    // 节点的IP地址
    char ip[REDIS_IP_STR_LEN];

    // 节点的端口号
    int port;

    // 保存连接节点所需的有关信息
    clusterLink *link;
}
```

```c++
typedef struct clusterLink {
    // 连接的创建时间
    mstime_t ctime;

    // TCP套接字描述符
    int fd;

    // 输出缓冲区，保存着等待发送给其他节点的信息
    sds sndbuf;

    // 输入缓冲区，保存着从其他节点接收到的信息
    sds rcvbuf;

    // 与这个连接相关联的节点，如果没有的话就为NULL
    struct clusterNode *node;
} clusterLink;
```

![clusterState集群](https://asea-cch.life/upload/2021/08/clusterState%E9%9B%86%E7%BE%A4-891727f4d82041739fc0fc1a06d5d560.png)

### **节点加入集群（三次握手）**

命令：`CLUSTER MEET <ip> <port>`

效果：发送命令方将加入到指定的ip:port方的集群中

![ClusterMeet三次握手](https://asea-cch.life/upload/2021/08/ClusterMeet%E4%B8%89%E6%AC%A1%E6%8F%A1%E6%89%8B-7b2c2cd28d8c4ebf84592eb26722ac19.png)

过程：
- 第一次握手：收到命令的节点A，向节点B发送`CLUSTER MEET`命令尝试握手，在发送之前会先为节点B创建一个`clusterNode`结构保存到自己的clusterState.nodes字典中
- 第二次握手：收到A发送的命令，节点B同样为节点A创建`clusterNode`结构加入到自己的nodes字典中，并返回一条`PONG`信息
- 第三次握手：节点A收到节点B的`PONG`，感知到节点B已经成功接收到自己发送的`CLUSTER MEET`命令，将向节点B返回一条`PING`信息
- 连接建立：节点B收到节点A的`PING`信息，确认A已经收到刚刚发送的`PONG`信息，握手完毕

# **2. 槽**

定义：用于对应数据库中的各个键，实现Redis集群的`分片`，槽由集群中的某节点进行管理

Redis分片：整个数据库被分为`16384`个槽，**数据库中的每个键都属于这16384个槽中的其中一个**，每个节点处理0个或最多16384个槽

> 当16384个槽都有节点在处理时，集群处于上线状态；相反的，有任何一个槽没有得到处理，那么集群处于下线状态

## **2.1 节点的槽指派信息**

clusterNode.slots属性和numslots属性负责记录节点处理哪些槽

```c++
struct clusterNode {
    // 每个bit位对应一个编号的槽，一个字符包含8个bit位，共有16384/8个字符
    unsigned char slots[16384/8];

    // 处理的槽个数
    int numslots;
}
```

记录方式：如果槽对应bit位上的值为1，则表示**节点被指派**于管理该槽

复杂度：取出和设置的复杂度均为O(1)

#### **传播节点槽指派信息**

将自己的槽指派信息，传播给集群中的其他节点，以此告知其他节点目前自己负责哪些槽

其他的节点在接收到某节点A的槽指派信息后，更新到自身的集群状态：
`clusterState.dict[节点A].slots = 指派信息`

所以每个节点都知道数据库中的16384个槽被分别指派到集群中的哪些节点

## **2.2 记录集群所有槽信息**

以上的结构，在单节点获取某个槽属于哪个节点时效率低下，时间复杂度达到O(N)，N为节点的个数

所以在接到其他节点的槽指派信息时，还会记录冗余数据到`clusterState.slots`数组中

```c++
typedef struct clusterState {
    clusterNode *slots[16384];
}
```

复杂度：O(1)

## **2.3 CLUSTER ADDSLOTS**

命令：`CLUSTER ADDSLOTS <slot> [slot ...]`

命令伪代码：
```python
def CLUSTER_ADDSLOTS(*all_input_slots):

    for i in all_input_slots:
        # 任意一个槽已经被指派，则终止并返回错误回复
        if clusterState.slots[i] != NULL:
            reply_error()
            return

    for i in all_input_slots:
        # 记录集群所有槽信息
        clusterState.slots[i] = clusterState.myself

        # 设置clusterNode的slots比特位
        setSlotBit(clusterState.myself.slots, i)
```

# **3. 集群中执行命令**

在全部槽都进行了指派后，集群将进入上线状态，客户端向节点发送命令时会根据`分片`进行指定：

- 如果键所在槽正好就是当前节点，那么节点直接执行命令
- 如果键所在槽并没有指派给当前节点，那么节点会向客户端返回一个`MOVED`错误进行重定向，指引客户端转向正确的节点，并再次发送之前想执行的命令

> 客户端向集群发送信息，由LBC根据算法进行路由，而不是直接对某节点进行操作

假设键“date”所在槽为2022，由节点7000负责；键“msg”所在槽为6257，由节点7001负责：

```shell
7000> SET date "2022-02-01"
OK

# 收到MOVED错误，客户端转向7001节点后执行命令
7000> SET msg "another chance"
-> Redirected to slot[6257] located at 127.0.0.1:7001

# 在7001节点执行GET，发现重定向成功
7001> GET msg
"another chance"
```

## **3.1 分片算法**

计算出键的CRC-16校验和，并与16383进行与操作，计算出一个介于0到16383之间的整数作为键key的槽号

```python
def slot_number(key):
    return CRC16(key) & 16383
```

## **3.2 判断槽是否由当前节点负责处理**

通过集群所有槽指派信息来查找，时间效率更高，判断clusterState.slots[i] == clusterState.myself：
- true，由当前节点负责
- false，不由当前节点负责，根据clusterState.slots[i]指定的节点IP和端口号，向客户端返回`MOVED`，指引客户端重定向到正确节点

## **3.3 MOVED**

命令格式：`MOVED <slot> <ip>:<port>`

作用：客户端接到节点返回的MOVED错误时，会根据MOVED错误中提供的正确地址地址，转向该节点，并向该节点重新发送之前向遥执行的命令

> 一个集群客户端通常会与集群中的多个节点创建套接字连接，所谓节点转向就是换一个套接字发命令，如果尚未创建连接，则先根据地址创建连接后再转向

## **3.4 节点数据库**

与单机节点保存键值对和过期时间的方式一致，唯一不同点在于集群模式的节点**只能使用0号数据库**

通过键获得槽可以通过`分片算法`，但通过槽来获得对应的多个键将十分麻烦。所以集群模式的节点还会通过维护一个`skiplist`来方便对属于某个或某些槽的所有数据库键进行批量操作

```c++
typedef struct clusterState {
    // ...

    // 分值为槽，对象为键
    zskiplist *slots_to_keys;
}
```

当节点往数据库中添加/删除一个新的键值对时，会维护在`slots_to_keys`结构中该键和关联的槽号

使用命令`CLUSTER GETKEYSINSLOT <slot> <count>`命令可以返回最多count个属于槽slot的数据库键，而这个命令就是**通过跳表的有序性来实现**

# **4. 复制与故障转移**

cluster的节点包含主节点和从节点：

- 主节点：**处理槽**
- 从节点：复制主节点，并在被复制的主节点下线时进行主从切换，不参与槽的处理工作

> [集群#节点](https://asea-cch.life/achrives/集群和主从复制)

**转移过程：**

> sentinel的故障转移是中心化的：sentinel则通过主观下线 -> 询问其它sentinel以获得客观下线 -> 选举sentinel leader -> sentinel leader执行故障转移的方式，其中sentinel leader为该操作的核心角色（中心化）

相比sentinel机制，cluster的转移过程是`去中心化`的：

1. 集群中的每个节点都会定期地向其他节点发送`PING`信息，以此检测对方是否在线

2. 没有在规定时间内返回的节点，将会把接收`PING`信息的节点标记为**疑似下线状态（PFAIL）**

3. 每个节点都会通过**互相发送消息**的方式，交换集群中的各个节点的状态信息，如：在线，疑似下线状态（PFAIL），下线（FAIL），并根据其他节点认为的状态信息维护一个`下线报告链表(failure report)`

    ```c++
    struct clusterNode {
        //...

        // 存放了其他节点对该节点的下线报告
        list *fail_reports;
    }
    ```

    ```c++
    struct clusterNodeFailReport {
        // 报告目标节点已经下线的节点
        struct clusterNode *node;

        // 最后一次从node节点收到下线报告的时间
        mstime_t time;
    } typedef clusterNodeFailReport;
    ```

4. 如果超过半数以上的负责处理槽的主节点认为某个主节点疑似下线，那么该主节点将被标记为`已下线（FAIL）`

5. 在从节点发现主节点被标记为**FAIL**后，将向集群广播`CLUSTERMSG_TYPE_FAILOVER_AUTU_REQUEST`消息，其它仍在正常运作的**主节点**接收到消息后，将向要求投票的从节点发送`CLUSTERMSG_TYPE_FAILOVER_AUTH_ACK`，表示支持该从节点成为新主节点

6. 超过半数以上投票的从节点将当选新的主节点，而旧主节点重上线后加入到新主的从节点中

# **5. 总结**

反思：

1. cluster和sentinel的区别：前者是为了高性能HPC，后者是为了高可用HA

2. cluster和sentinel的关系：两种不同的模式，cluster拥有sentinel的部分特性，如主备切换等，也具备了HA特性

 # 参考
 - [Redis设计与实现#第十七章集群]()