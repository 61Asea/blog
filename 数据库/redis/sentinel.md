# Redis高可用：sentinel

Sentinel，又称为**哨岗/哨兵**，是Redis的高可用性的解决方案

作用：可以监视**多个主**服务器，以及这些主服务器**属下的所有从**服务器，并在被监视的**主服务器进行下线**状态时，自动将下线主服务器**属下的某个从**服务器升级为新的主服务器，然后由新的主服务器代替旧主服务器继续处理命令请求

> 主服务器下线时，从服务器会中止对主服务器的复制操作

Sentinel系统：指的是由一个或多个Sentinel共同组成的系统

涉及命令：`PUBLISH __sentinel__:hello "<s_ip>,<s_port>,<s_runid>,<s_epoch>,<m_name>,<m_ip>,<m_port>,<m_epoch>"`、`SUBSCRIBE __sentinel__:hello`、`INFO`、`PING`、`SLAVEOF`、`SENTINEL master-is-down-by-addr`

# **1. Sentinel启动**

> Sentinel本质上只是一个运行在特殊模式下的Redis服务器，执行的工作和普通Redis服务不同，所以初始化过程和普通Redis服务器的初始化过程并不完全相同

启动单个Sentinel命令：
`redis-sentinel <config>`或`redis-server <config> --sentinel`

Sentinel启动过程中，需要执行以下步骤：
- 初始化服务器

    Sentinel不使用数据库，所以不会载入RDB文件或AOF文件

- 将普通Redis服务器使用的代码替换为Sentinel专用代码

    替换命令表，以及对配置的参数读取不同，所以Sentinel不能执行诸如SET、DBSIZE等等指令

    > Sentinel七个可执行指令：PING、SENTINEL、INFO、SUBSCRIBE、UNSUBSCRIBE、PSUBSCRIBE、PUNSUNSCRIBE

- 初始化Sentinel状态

    取而代之正常服务器的RedisServer结构，sentinel服务器会初始化一个`sentinel状态`的结构，保存所有和sentinel功能相关的功能

    ```c++
    struct sentienlState {
        // 当前纪元，用于实现故障转移
        uint64_t current_epoch;

        // 保存sentinel监视的主服务器字典，键为主服务器名字，值为指向sentinelRedisInstance结构的指针
        dict *masters;

        // 是否进入了TILT模式
        int tilt;

        // 目前正在执行的脚本数量
        int running_scripts;

        // 进入TILT模式的时间
        mstime_t tilt_strat_time;

        // 最后一次执行时间处理器的时间
        mstime_t previoust_time;

        // 一个FIFO队列，包含了所有需要执行的用户脚本
        list *scripts_queue;
    } sentinel;
    ```

- 根据给定的配置文件，初始化Sentinel的监视主服务器列表

    ```c++
    typedef struct sentinelRedisInstance {
        // 主服务器实例的地址
        sentinelAddr *addr;

        // SENTINEL down-after—milliseconds选项设定的值
        // 实例无响应多少毫秒后，才会被判断为主观下线
        mstime_t down_after_period;

        // SENTINEL monitor <master-name> <IP> <port> <quorum>选项中的quorum参数
        // 判断该实例为客观下线所需的支持投票数量
        int quorum;

        // SENTINEL parallel-syncs <master-name> <number>选项的值
        // 在执行故障转移时，可以同时对新的主服务器进行同步的从服务器数量
        int parallel_syncs;

        // SENTINEL failover-timeout <master-name> <ms>选项的值
        // 刷新故障迁移状态的最大时限
        mstime_t failover_timeout;

        // 自身标记，有SRI_MASTER、SRI_SENTINEL、SRI_S_DOWN
        int flags;

        // 哨兵字典，键为ip:port，值为哨兵的sentinelRedisInstance
        dict *sentinels;
        
        dict *slaves;
    } sentinelRedisInstance;
    ```

    sentinelRedisInstance.addr指向的结构，保存着实例的IP地址和端口号：
    ```c++
    typedef struct sentinelAddr {
        char *ip;
        int port;
    } sentinelAddr;
    ```

    以上的结构的最上层为sentinalState.master引用，当sentinel状态初始化时，也会引发对master字段的初始化，master字典的初始化则是**根据被载入的Sentinel配置**进行

- 创建连向主服务器的网络连接

    运行代码/监视列表等一系列状态初始化后，将会创建连向主服务器的网络连接，成为主服务器的客户端，可以向主服务器发送命令，并从命令回复中获取相关信息

    连接有两个：
    - 一个是`命令连接`，专门用于向主服务器发送命令，并接收命令回复
    - 另一个是`订阅连接`，专门用于订阅主服务器的`__sentile__:hello`频道

    > 

# **2. 监视过程**

`命令连接`和`订阅连接`是sentinel系统中各sentinel交互的基础

服务器的__sentinel__:hello频道作为交互媒介，每个sentinel都会订阅服务器的该频道，并定期向频道发送信息，实现与其他sentinel的信息同步

## **2.1 sentinel系统初始化**

### **2.1.1 获取主服务器信息**

频率：默认`每十秒一次`

方式：通过`命令连接`向被监视的主服务器发送`INFO`命令

```shell
# Server
...
run_id: xxxxxxxx
...

# Replication
role:master
...
slave0:ip=xxx,port=xxx,state=online/offline,offset=43,lag=0
slave1:...
slave2:...

# Other sections
...
```

信息将更新到对应sentinelRedisInstance结构，至于返回的从服务器信息，则被用于更新sentinelRedisInstance.`slaves`字段

通过分析INFO的**回复**获取主服务器的当前信息：
- 获取主服务器本身信息，包括run_id域记录的服务器运行ID、role域记录的服务器角色

- INFO会返回关于主服务器属下的所有从服务器信息，Sentinel通过这些信息**自动**发现从服务器

![sentinel的主服务器结构](https://asea-cch.life/upload/2021/08/sentinel%E7%9A%84%E4%B8%BB%E6%9C%8D%E5%8A%A1%E5%99%A8%E7%BB%93%E6%9E%84-95fe979132b64d4095fc3b9459a5c400.png)

### **2.1.2 获取从服务器信息**

每当sentinel发现主服务器有新从服务器出现，Sentinel除了会为该新的从服务器创建相应实例结构外，还会创建两个连接到从服务器的`命令连接`和`订阅连接`

> 新的从服务器加入到主从架构时，会向主服务器发起`SLAVE OF`指令加入。随后，sentinel通过主服务器的INFO返回感知到新从服务器的加入

频率：默认`每十秒一次`

方式：通过`命令连接`向被监视的**从服务器**发送`INFO`命令

```shell
# Server
...
run_id:xxxx
...

# Replication
role:slave
master_host:xxx
master_port:xxx
master_link_status:up
slave_repl_offset:11887 # 从服务器的复制偏移量
slave_priority:100
```

### **2.1.3 向主服务器和从服务器发送信息**

<!-- 疑问1：为什么要两个连接，发布订阅机制是什么？

疑问2：为什么都能通过主服务器信息获取从服务器信息，还需要再跟从服务器建立连接？ -->

频率：`每两秒一次`

方式：

通过**命令连接**，向所有被监视的服务器（主+从）发送以下格式命令

`PUBLISH __sentinel__:hello "<s_ip>,<s_port>,<s_runid>,<s_epoch>,<m_name>,<m_ip>,<m_port>,<m_epoch>"`

这条命令向各个服务器的__sentinel__:hello频道发送了一条信息，参数意义如下：

| 参数 | 意义 |
| ---- | ---- |
| s_ip | Sentinel的IP地址 |
| s_port | Sentinel的端口号 |
| s_runid | Sentinel的运行ID |
| s_epoch | Sentinel当前的配置纪元 |
| m_name | 主服务器的名字 |
| m_ip | 主服务器的IP地址 |
| m_port | 主服务器的端口号 |
| m_epoch | 主服务器当前的配置纪元 |

### **2.1.4 接收来自主服务器和从服务器的频道信息**

方式：`订阅连接`在建立起后，Sentinel就会通过它向服务器发送命令`SUBSCRIBE __sentinel__:hello`

> 每个与sentinel连接的服务器，sentinel既通过命令连接向服务器的__sentinel__:hello频道发送信息，又通过订阅连接从服务器的该频道接收信息

频率：同seninel的发送频率一致，`默认每两秒一次`

作用：主要用于**多个sentinel监视同一个服务器**，一个sentinel发送的信息会被其他sentinel接收到，这些信息会被用于更新其他sentinel对发送信息sentinel的认知

![sentinel两个连接实现发布和订阅](https://asea-cch.life/upload/2021/08/sentinel%E4%B8%A4%E4%B8%AA%E8%BF%9E%E6%8E%A5%E5%AE%9E%E7%8E%B0%E5%8F%91%E5%B8%83%E5%92%8C%E8%AE%A2%E9%98%85-2b986d6aafdb4dab99af478d2d90eb80.png)

具体过程：

当sentinel（127.0.0.1:26379）从频道收到信息
```shell
1) "message"
2) "__sentinel__:hello"
3) "127.0.0.1,26379,xxxxxxxxxx,0,mymaster,127.0.0.1,6379,0"

1) "message"
2) "__sentinel__:hello"
3) "127.0.0.1,26381,xxxxxxxxxx,0,mymaster,127.0.0.1,6379,0"

1) "message"
2) "__sentinel__:hello"
3) "127.0.0.1,26380,xxxxxxxxxx,0,mymaster,127.0.0.1,6379,0"
```

忽略掉发送者为自己的信息（上述第一条），并提取出其它信息中的八个参数（2.2.3发送步骤的信息）后，进行以下操作：

> 目标sentinel为接收订阅信息的sentinel，源sentinel为发送信息的sentinel

- 更新sentinels字典

    目标sentinel会在sentinelState.masters字典中，查找相对应的主机127.0.0.1:6379，并提取出sentinel参数，检查sentinelRedisInstance.sentinels中是否已存在该sentinel，存在则更新；不存在则创建一个新的sentinelRedisInstance实例，添加到字典中

    ![sentinel对哨兵其他节点的结构](https://asea-cch.life/upload/2021/08/sentinel%E5%AF%B9%E5%93%A8%E5%85%B5%E5%85%B6%E4%BB%96%E8%8A%82%E7%82%B9%E7%9A%84%E7%BB%93%E6%9E%84-31861ec3d50c4838a8739b7068eebd09.png)

    > 因为sentinel可以通过订阅连接感知其他sentinel，所以在使用sentinel无需提供各个sentinel的地址信息，监视**同一个服务器**的多个sentinel可以自动发现对方

- 创建连向其他Sentinel的**命令连接**

    ![监视同一服务器的sentinel系统](https://asea-cch.life/upload/2021/08/%E7%9B%91%E8%A7%86%E5%90%8C%E4%B8%80%E6%9C%8D%E5%8A%A1%E5%99%A8%E7%9A%84sentinel%E7%B3%BB%E7%BB%9F-56c183a6da3240c2a64e169e665e625b.png)

    目标sentinel会和源sentinel互相建立命令连接，最终监视同一主服务器的多个sentinel将组成相互连接的网络

    使用命令连接相连的各个sentinel可以通过向其他sentinel发送命令请求来进行信息交换，是实现`主观下线检测`和`客观下线检测`的实现基础

## **2.2 检测主观下线状态**

定义：某发送方认为某方（主、从服务器）没有按照一定的规定进行响应，则自认为某方进入下线状态

频率：`每秒一次`

发送方：某Sentinel

接收方：主服务器、从服务器、其他Sentinel

发送内容：`PING`

回复内容：
- 有效回复：实例返回`+PONG`、`-LOADING`、`-MASTERDOWN`三种回复的其中一种
- 无效回复：实例返回除以上三种回复的其他回复，或没有在指定实现内返回任何回复

相关配置：`down-after-milliseconds`，选项指定了sentinel判断实例进入主观下线所需的时间长度

发送方后续做法：在指定的down-after-milliseconds毫秒内，接收方连续向Sentinel返回无效回复，那么发送方Sentinel将会修改该实例对应结构，在sentinelRedisInstance.flags属性中打开`SRI_S_DOWN`标识，以此认为该实例进入了`主观下线`状态

## **2.3 检测客观下线状态**

定义：在某发送方认定主服务器进入**主观下线**后，会询问其他监视该服务器的其他sentinel是否也同样认定服务器进入主观下线状态，当获得足够数量的**主观下线判断**后，sentinel就会将**从服务器判定为客观下线**，并对主服务器执行`故障转移`

频率：发送方认定主服务器进入**主观下线**后

发送方：认定服务器下线的sentinel

接收方：sentinel系统中的其他sentinel

发送内容：`SENTINEL is-master-down-by-addr <ip> <port> <current_epoch> <runid>`

回复内容：接收到上述命令后，会根据命令的参数去检查指定主服务器是否已下线，并返回一条包含三个参数的Multi Bulk回复
- down_state：检查结果，`1`表示已下线，`0`表示未下线
- leader_runid：`*`或`局部Sentinel领头的运行ID`，前者表示回复仅仅用于检测下线状态，后者表示回复leader的runid以选举领头sentinel
- leader_epoch：领头Sentinel的配置纪元，用于选举领头sentinel，如果leader_runid为*，则该值为0

相关配置：`quorum`，参数指定了认定主服务器客观下线的数量

发送方后续做法：根据其他sentinel返回的回复内容，统计同意主服务器已下线的数量，当数量达到配置`quorum`值时，sentinel会将该服务器的sentinelRedisInstance.flags改为SRI_O_DOWN，表示主服务器已经进行客观下线状态

## **2.4 故障转移**

当主服务器被判断为客观下线后，sentinel系统中的各个sentinel会进行协商来`选举领头sentinel`，并由领头sentinel对下线主服务器执行`故障转移`操作

### **2.4.1 选举领头Sentinel**

本质：每个sentinel都会被选为leader的资格，选举过程中以谁的**设置命令**更快到达其他sentinel为胜负条件

`配置纪元`：本质上就是一个计数器，每次进行领头sentinel选举之后，无论是否成功，都会将所有sentinel的配置纪元值自增一次

> 在一个配置纪元里面，所有sentinel都有一次将某个sentinel设置为局部领头sentinel的机会，并且一旦设置局部领头，在这个配置纪元里面就不能再修改

做法：每个发现主服务器进入客观下线的sentinel都会要求其他sentinel将自己设置为局部leader，当某个sentinel得到半数以上其他sentinel的头牌，则成为sentinel leader

> 目标sentinel以先到先得的方式接收，设置之后将拒绝其他sentinel的要求

命令：`SENTINEL is-master-down-by-addr ... <runid>`

> 命令中的runid就是源sentinel的运行id，这表明源sentinel要求目标sentinel将前者设置为后者的**局部leader**

回复：返回三项参数，其中leader_epoch返回自己的配置纪元，leader_runid返回自己设置的领头leader

### **2.4.2 故障转移**

产生leader sentinel之后，对已下线的主服务器执行故障转移操作，包含三个步骤：

1. 在已下线的主服务器的所有从服务器中，挑选出一个新主服务器

    - 将所有属性的从服务器保存到一个列表中，筛选出正常在线的从服务器

        > 快速排除掉处于下线或断线状态的从服务器

    - 删除最近5秒内没有回复过领头sentinel的`INFO`命令的从服务器

        > 保证剩余的从服务器都是最近成功进行过通信的

    - 删除所有与已下线服务器连接断开超过`down-after-milliseconds * 10`的从服务器

        > 尽量保证剩余从服务器保存的数据都是比较新的

    - 根据`priority`优先级属性进行排序，选出其中优先级最高的从服务器

        > 相同优先级的，则选取复制偏移量更大的从服务器，如果复制偏移量相同，则选取运行ID最小的

    - 每秒一次的频率，向新主发送`INFO`命令，观察回复中的role信息，确认其从slave转换为master

2. 让已下线的主服务器属下的其他从服务器，`SLAVEOF`新主服务器


3. 将已下线的主服务器设置为新服务器的从服务器，当旧主重新上线后，将称为新主的从服务器

    将已下线的主服务器设置为新的主服务器的从服务器，并在旧主重新上线后，向它发送SLAVEOF命令，让它成为server2的从服务器

    > 涉及到主从复制的一致性

# **3. 总结流程**

1. 以每10秒一次的频率，发送`INFO`命令获取主服务器的信息

2. 以每10秒一次的频率，发送`INFO`命令获取从服务器的信息

3. 每当获取到一个新服务器的信息，则对其建立命令连接和订阅连接

4. 以每2秒一次的频率，发送`PUBLISH __sentinel__:hello "<s_ip>,<s_port>,<s_runid>,<s_epoch>,<m_name>,<m_ip>,<m_port>,<m_epoch>"`到服务器中，感知其他sentinel的存在，并组建成sentinel系统

5. 以每秒一次的频率，发送`PING`命令到各个服务器中，当主服务器进行了无效回复时，认为其主观下线

6. 认定某服务器主观下线后，发送`SENTINEL is-master-down-by-addr <ip> <port> <current_epoch> <runid>`询问其他sentinel（其他sentinel同样也会执行该操作），在获得`quorum`数量的认可后，认定该服务器进入客观下线状态

7. sentinel向集群中的其它sentinel发送`SENTINEL is-master-down-by-addr ... <runid>`要求它们将其设置为局部leader，其它sentinel接收命令以先到先得，后到拒绝的方式。最终选举出leader sentinel

8. leader sentinel根据规则选出新的主服务器，并将旧的主服务器设置新主的从服务器

# 参考
- [Redis设计与实现]()
