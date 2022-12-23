# Interview Seven：Redis

核心：redis作为内存数据库，第一考虑点是`内存利用率`，其次是`读写延时`，存在某些降低延时但能提高利用率的操作

主要问题：

- 结合实际运用场景，可以结合排行榜功能模块，介绍zset运用

- 数据结构
- redis快的原因
- redis6.0的多线程
- 过期策略
- 分布式锁
    - value是否要设置，设置为什么？
    - expire的时长设置问题，不设置会导致锁无法释放，设置过小/过大都会有相应的问题
    - 业务上采用的方式：轮询多次尝试 + 悲观锁
        问题：如果业务过久，或竞争压力过大，应如何优化？
        - 悲观锁，假设一定会发生竞争，所以在执行业务前就需要占用资源加锁
        - 粒度过大

# **1. 数据结构**

1. 七种基本数据类型

    - sds：简单**动态**字符串，在大部分使用场景下替代C的字符串类型
    
        1. 新增长度信息，**O(1)时间复杂度获取字符串的长度**
        
        2. 自动扩展底层空间，防止缓冲区溢出
        
        3. 空间预分配、惰性释放（打标记），减少字符串的重分配次数

    - linkedlist：**双向无环**链表，发布订阅、满查询、监视器功能都使用链表实现
    
        Redis3.2之前，在list键元素较多时使用linkedlist作为存储结构，3.2之后则采用quicklist作为存储结构（linkedlist + ziplist）

    - hashtable/dict：字典，每个字典都**带有两个hash表**，供平时使用和resize时使用
    
        1. 使用**链地址法**解决哈希冲突
        
        2. **渐进式扩容**避免一次性扩容降低服务可用性，因为redis是**单线程**，且一次性扩容耗时过久，扩容期间长时间无法操作

        与JDK.HashMap的区别：
        
        - 扩容因子：redis在无后台任务下是1，有后台任务下是5
        
        - 扰动函数：redis没有扰动，在数组长度小时，低位特征不明显的key hash分布不够均匀

        - 缩容：负载因子小于0.1时缩容

        - 扩容后大小：`已存放键值对个数`的两倍的最小2次幂长度

        - 缩容后大小：`已存放键值对个数`的最小2次幂长度

        - 渐进式扩容：分治思想，将一次性扩容的开销分摊到每次操作中，每次对dict的增删改查操作，会将涉及到的桶从ht[0]迁移到ht[1]中

            - 查询：先从ht[0]中查找，找不到再去ht[1]中查找

    - skiplist：跳表，由多个跳表节点（zskiplistNode）组成，通过zskiplist对象保存跳表信息（表头节点、表尾节点、长度、最大节点层数）进行管理

        1. **有序且对象唯一**，每个节点都有score分值，多个节点可以有相同的score分值，但是对象一定是唯一的（对象相同则覆盖）

        2. 三维，节点拥有高度属性，**相同高度的节点**可以串起来更高维度的**链表**，高度在1~32之间随机

        3. 随机数法：每插入一个新节点，其最大层数在`1-32之间的数`随机，以正态分布调整索引，保证越高层的节点生成的概率越低

            > 相比传统的索引调整方式，随机数法可以避免每次增删改造成的高度索引重生成，效率更高

    - intset：整数集合，是set集合键实现之一，底层使用数组存储整数，并保证数组中不会出现重复的元素

    - `ziplist`：压缩列表，是redis用于**节省内存空间，提高内存利用率**而设计的数据结构，list、dict、zset在元素个数不满阈值时，会采用它减少空间占用

        连续的内存块组成，这也意味着它拥有数组的缺点，即在变更时需要重新分配数组空间，或挪动数据

    - quicklist：快速列表，redis3.2之后使用quicklist，协调ziplist与linkedlist各自缺点，具体采用多个ziplist组成双向链表

        - linkedlist：链表的next和prev指针占用空间大，无法提供随机访问性

        - ziplist：数组插入删除代价大

        这种思路减少了指针个数，提高内存利用率，也相当于将ziplist分段，防止量级过大下ziplist删除插入产生极大代价

2. 五种类型键（API）：redis通过7大基础数据结构，进一步封装自身的对象系统，这五大对象数据结构都至少用了1种基础数据结构

    - string：int、embstr、raw三种编码，整数值使用int，小于32字节字符串使用embstr，大于32字节或修改设置后使用raw，后两种编码都使用了sds

    - list：统一使用quicklist

    - dict：ziplist、hashtable，小于512个键值对，或单个键值对大小小于64字节时使用ziplist

    - set：intset、hashtable，前者可以节省内存，当存在字符串类型元素或数量过大时使用hashtable的keyset

    - zset：ziplist、skiplist + hashtable

        - zset的ziplist：用两个entry来存储一个集合元素，第一个entry为分数，第二个entry为对象值。**每个元素在列表中从小到大进行排序，分数越小的元素越靠近表头**

        - zset使用跳表的原因：`跳表有序`，且查找为O(logN)时间复杂度，支持范围操作如`ZRANK`、`ZRANGE`

        - zset维护hashtable的原因：hashtable的key为成员对象，value为分数值，通过这种方式可以实现O(1)时间复杂度查找成员分数值的功能`ZSCORE`

# **2. Redis快的原因**

redis快：单线程处理操作，纯内存，epoll 单线程reactor，6.0版本的多线程reactor，网络io

1. 读写完全基于内存进行操作

2. 单线程处理操作事件，没有多线程切换的开销

3. 使用I/O多路复用模型（epoll同步非阻塞）

# **3. redis6.0**

由于Redis完全在内存上操作，主要瓶颈在**网络I/O**而不是CPU

在4.0之前采用单线程模型，资源没有线程并发问题，可以避免锁竞争开销与多线程上下文切换开销，在一个循环中，主线程按超时时间阻塞就绪I/O事件，再转而进入命令处理

在4.0之后引入多线程，主要用于**持久化**、**lazyFree**、**集群同步**等异步任务

## I/O threads

Redis6.0新特性：使用了I/O多线程（I/O threads）来处理网络请求，本质是对I/O多路复用的应用

I/O线程组与socket进行绑定，对就绪socket进行网络数据**接收、解析、发送**等操作，而**命令执行操作仍旧由主线程进行**，提高cpu利用率进而提升网络I/O接收能力

# **4. 分布式锁**

使用原子操作`SET NX`或`lua代码`实现，当key不存在时我们进行set操作，key存在时不做任何操作

> 注意：redis的set指令没有阻塞超时时间概念，所以业务上常用轮询尝试加锁的方式实现，且若有明确的同步块代码，需要先获取资源再执行业务代码，假定一定会发生锁竞争，属于悲观锁

1. expired参数有什么用？

    为必设项，用于设置key的过期时间
    
    死锁：若没有添加expired参数，当加锁者宕机后，其他竞争者将永远无法获取到锁

2. expired设置多大合适（锁超时问题）？

    不宜过短或过长，过短会导致任务还未执行完毕锁就过期释放，使得其他任务获得锁资源进入同步块，出现并发问题；过长会导致锁持有者宕机后，其他竞争者等待过长时间

    建议先评估业务的预期执行时长，可自实现或直接redisson使用“看门狗机制”，即持有者在后台启动线程执行任务，任务内容为在key快到期时候进行自动续期，当持有者宕机时该续期任务线程也会随之消失，不会进入无限续期

3. value是否要设置（锁误删问题）？

    要设置，且一般设置为加锁者的标识ID，这是为了防止加锁者业务执行时长大于锁过期时长，导致过期释放被其他竞争者获得，而旧加锁者又在后续完成任务后解锁他人资源的问题

4. 使用redis实现有什么问题呢？

    - 锁丢失：可能出现在主宕机，而从机还未来得及同步就被选为新主，这样在旧主服务器加的锁就丢失了

        解决方案：官方在集群模式下提供redlock加锁算法，对多个平等的**master**依次加锁，直到加锁成功数**过半**后，才认为加锁成功

    <!-- - 非公平：无法根据加入顺序获取锁，且每个竞争者都会对锁进行轮询尝试，有不可忽视的网络I/O开销 -->

    - 竞争情况：业务上竞争锁资源时可能使用`尝试+轮询`的方式实现，redisson结合PUBLISH/SUBSCRIBE进行阻塞唤醒

        <!-- 根据业务场景确定，若允许接受业务暂时挂起，则可将自旋次数和频率降低 -->
        
        - 竞争不激烈：转换为乐观锁
        
            业务可采用redis的事务机制，通过multi、watch、exec等指令配合实现，或在db上

            ```shell
            127.0.0.1:6379> watch money ##监听money 
            OK
            127.0.0.1:6379> multi ##开启事务
            OK
            127.0.0.1:6379> decrby money 2 ##money = money - 2
            QUEUED
            127.0.0.1:6379> exec  ##如果有另外的线程在这个事务监听的同时修改过，则不执行这个事务，如果没有修改过 则执行事务
            127.0.0.1:6379> (nil) ## 返回nil代表有其它事务修改了数据
            ```

        > 在冲突概率大的情况下，不适合使用乐观锁，因为很大程度下都会冲突失败，而失败后又需要通过重试来保证业务正确性

        - 竞争激烈：结合采用悲观锁（这里的悲观锁指的是一定会获取到锁），根据业务情况降低自旋周期

            > 当**竞争过大**时应如何处理？

            思路：一定是**锁粒度降低**，甚至无锁化

            方式有：

            - 锁细化（垂直方向）：常见**根据业务**进行分级管理，避免锁的粒度过粗

            - 数据分段（水平方向）：类似jdk7.hashmap的做法，将共享资源均匀分段，**提升并发程度**
        

    当前实现方案：锁分级管理，直接使用`轮询 + 尝试`的方式获取**悲观锁**

   问题：业务方并没有让出线程资源，在某些小锁竞争过大的情况下可能总是会进行最大次数重试后失败，且业务明显允许操作失败让玩家重试

    我的解决方案：参照synchronized轻量级锁机制，将自旋次数从静态改为动态自适应，新增最小自旋次数和最大自旋次数。每次加锁到最后能成功，则每次将自旋次数提高1次，最大直至最大自旋次数，否则直接恢复到最小自旋次数 -->

    考虑点：不希望太重，认为切换线程开销较大，阻塞会造成同一线程下其他socket绑定用户的业务逻辑

    问题：在高并发访问压力下，该机制可能出现cpu空轮询浪费、大量带宽占用

    解决思路：当前已根据业务对锁进行细化，避免对其他业务造成影响
    
    - （待定）如果场景是读多写少，redis分布式锁实现为读写锁

    - 结合对持有锁这的channel的`PUBLISH/SUBSCRIBE`机制，client使用countDownLatch实现获取锁失败后的阻塞挂起，减少**busy waiting**对cpu算力的浪费

        如果使用默认redisson的实现，存在**惊群效应**；使用公平锁既解决了**线程饥饿**，也避免了惊群效应，具体方式是**每个首次获取锁资源失败的client，都会订阅队列前一个client节点的channel**

        redisson具体实现：

        - zset保存超时时间为分数的有序队列

        - list保存按照超时顺序排列的节点

5. 分布式锁的单点问题如何解决？

    一主多从集群部署，主从同步存在延时，当主服务器还未推送锁数据到从服务器时宕机，将面临锁丢失问题
    
    搭建cluster，使用redlock算法，按照顺序依次尝试在多个节点上锁，如果获取锁的时间小于超时时间，且超过半数的节点获取成功，那么加锁成功

    操作耗时大，节点越多越明显，且无法应对服务宕机恢复后锁数据丢失的问题，需要在锁超时时间过后才能重启

# **5. 缓存**

将redis实现缓存层，会有以下问题：

1. 热key：指在某一瞬时，突然有几十万请求访问redis上的某个key，可能会导致redis无法处理进而宕机，导致缓存层崩溃进而雪崩

    解决方案：

    - 将热key数据提前放到cdn上

    - 加入二级缓存，提前将热key存于服务上，当redis挂球时走内存查询

2. 缓存穿透/缓存击穿/缓存雪崩

    - 缓存穿透：指访问实际上db也不存在的数据，每次访问时都会直接打在db上，犹如缓存层不存在一样

        解决方案：采用布隆过滤器，原理是通过多个hash函数生成多个数组下标值，并将位图数组中的这些下标对于元素都置为1，查询时存在的数据一定能成功，不存在的数据大部分都被过滤筛除。由于哈希冲突无法避免，误判率必定存在，可通过扩大数组长度来调整误判率

    - 缓存击穿：指单个key的并发访问较高，当该key过期后全部的访问压力将打到db上

        解决方案：过期时间无限根源解决、控制打到db上的请求只有一个、限制当前请求量

        - 设置热点数据永不过期

        - 接口熔断，限流，降低请求压力

        - 由缓存一致性策略驱动，并且对查询加入**互斥**，保证同一时刻只有一个操作去读取db并写回缓存

    - 缓存雪崩：指大规模key在同一时间段内同时过期，类似于热key最终效果，请求基本都打到db上
    
        解决方案：

        - key的过期时间设置为互不相同，防止同时失效

        - 限流

        - 二级缓存保证热key不会直接搞崩

3. 与db的双写一致性问题

    矛盾点：
    - 双写操作如何保证原子性
        
        补偿措施：
        
        - 第二个写是删缓存：订阅binlog增量，获取到写入db成功，且当前删缓存操作失败，则尝试重试
        - 第二个写是写db：没问题，不会产生

- 读操作的更新缓存和整个写操作如何保证并发安全？
    <!-- 讲述读数据的做法，再讲写数据的做法，总结解决方案，最后结合当前项目的问题 -->

    - 读数据：查询数据时缓存数据为null，则`加锁`去db查询结果，最后并写回缓存
    
        加锁必要性：保证同一时刻只有一个读db操作和写缓存操作，其他读操作在阻塞后可直接读取缓存获取数据

    - 写数据：先写db再删缓存、先删缓存再写db都可能出现问题，问题在于组合操作`非原子`，且可以出现`并发操作`

        >为什么是删除缓存，而不是更新缓存（ `Cache Aside Pattern`）：懒加载思想，假设数据为**写多读少**的场合，缓存则没必要提前计算和浪费内存去缓存该数据，而由在需要使用时再去加载

        - 非原子：第二个操作失败，不能使得第一个操作回滚，无法全部失败

        - 并发操作：该组合操作（写数据）和读数据之间并没有互斥性，不能在保证执行期间两个操作的串行处理，**最容易出现的就是并发查询导致的旧数据覆盖问题**

    - **双写一致**解决方案：

        - 双写顺序：先删cache再写db，即使写db失败了也不会造成影响，减少原子性缺失带来的问题

        - 延迟双删：在执行完组合操作后，隔一段时间再次执行删除缓存的操作，达到**最终一致性**

        - 串行化请求：执行组合操作期间，通过synchronized、分布式锁（根据系统而定）等机制来阻塞住其他的并发操作

    - 当前项目方案：采用google guava（[ˈɡwɑːvə]）作为1级缓存，redis作为2级缓存，相同服务集群部署，db作为服务集群的统一数据源，具体的key为各大功能系统的重置时间

        - 思路：在本机内存（google guava）上直接返回，进一步减少对redis的访问，访问压力打散到不同的服务器上

        - 原子性：先删loadingCache缓存、再删redis缓存、最后写db，同样可以避免原子性缺失导致的问题

        - 读：查询缓存为null，则触发查询redis或db操作，对于同一个key的请求，本机通过**loadingCache**（分段锁）互斥，不同服务间则通过**分布式锁**互斥，保证同一时刻只有一个操作者写redis缓存

        - 写：业务支持一段时间的数据不一致，只要保证最终一致即可
        
            - loadingCache：使用原生缓存过期策略进行10s一次的读取

            - redis：延迟双删，在删除完cache后提交另外一个5秒后执行的任务，保证出现并发查询后依旧redis和db层面上保持一致

# **6. 过期策略**

单纯使用定时删除会造成较大的性能开销，单纯使用惰性删除或定期删除都可能会导致无用内存占用过多，所以使用惰性删除 + 定期删除的方式：

    redis每隔一段时间会随机检查一部分键值对，删除掉里面过期的key，而在查询key时，也会检查key的过期时间进行删除

当惰性+定期删除仍旧无法满足内存需求时，在达到maxmemory阈值后redis会执行内存淘汰策略，新的键将会置换掉一些久的键

策略主要有：
- allkeys-lru: 全量替换掉最近最少使用淘汰的key（幂次访问）
- allkeys-random: 全量随机替换掉key（平等访问）
- volatile-lru: 从设置了ttl的数据中，挑选最近最少使用的key进行置换
- volatile-ttl: 从设置了ttl数据中，挑选ttl最小的key进行置换
- volatile-random: 从设置了ttl中的数据中，随机挑选一个key进行替换
- novacation: 内存满时，拒绝操作

# **7. RDB、AOF**

- RDB：内存快照，记录的是某一时刻的数据，而不是操作，采用RDB方式恢复只需直接加载数据文件即可

    - save：同步阻塞生成RDB快照，在生成期间客户端的操作都将无法处理

    - `bgsave`：后台线程执行RDB生成，在生成期间客户端操作后的数据都会保留一个副本，然后在RDB生成完毕后，将副本的数据更新到生成的RDB中

    缺点：

    - 只能记录某一时刻的数据快照，存在数据丢失的风险

- AOF：记录的是redis命令执行操作，恢复时需要将全量日志都执行一遍

    `写后日志（区别于WAL日志先行）`的方式，即先写数据再记录日志到文件中，这是因为redis没有语法检查，先写日志可能会记录错误操作

    缺点：

    - 日志丢失，数据写完后还未写入日志就宕机

    - 恢复速度慢

采用`RDB+AOF`混合持久化的方式，既降低了数据丢失风险，又提高了宕机恢复的速度


# **8. 主从同步**

同步 + 增量命令传播的方式，存在同步延时

从服务器启动后，发送`PSYNC`给主服务器，一开始**拉取**主服务器的RDB数据，后续则由主服务器推送新的更新操作

 # 参考
 - [zset（跳表+hash）](https://blog.csdn.net/dl674756321/article/details/102501627)
 - [Redis6.0多线程](https://www.cnblogs.com/gz666666/p/12901507.html)

 - [redis实现乐观锁](https://blog.csdn.net/chenrushui/article/details/103637285)

 - [Redis分布式锁](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247491561&idx=1&sn=0a2a2728ab6e3fac2211504dcac73963&chksm=c2b25714f5c5de0239d7796ffedbfc2136cd8f7b64e8bd26a79019013314f4e1406ac4b0a8ec&token=982147105&lang=zh_CN&scene=21#wechat_redirect)

 - [Redisson 分布式锁源码 08：MultiLock 加锁与锁释放](https://zhuanlan.zhihu.com/p/388357443)
 - [Redisson 分布式锁源码 10：读写锁](https://zhuanlan.zhihu.com/p/389165753)
 - [Redisson 分布式锁源码 11：Semaphore 和 CountDownLatch](https://zhuanlan.zhihu.com/p/389976053)

 # 重点参考

 - [红黑树、AVL、跳表选择](https://www.cnblogs.com/charlesblc/p/5987812.html)

 - [为什么是先更新数据库再删除缓存，而不是更新缓存？](https://blog.csdn.net/dreamzuora/article/details/107673150)


- [Redisson 分布式锁源码 02：看门狗](https://zhuanlan.zhihu.com/p/386328633)
- [Redisson 分布式锁源码 03：可重入锁互斥](https://zhuanlan.zhihu.com/p/386651081)
- [Redisson 分布式锁源码 06：公平锁排队加锁](https://zhuanlan.zhihu.com/p/387700327)