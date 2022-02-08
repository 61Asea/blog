# Interview Five：集合框架

- Map
    - HashMap
        - 哈希函数
        - 解决哈希冲突（底层结构）：数组、链表、红黑树
        - put/resize()，尾插法和头插法
        - 特性：无序，空间复杂度换时间复杂度
    - LinkedHashMap：LRU
    - ConcurrentHashMap

- ArrayList和LinkedList

    - 数据结构：Object[]数组连续空间，双向链表不连续空间

    - 随机访问性：ArrayList具备，LinkedList不具备

    - 插入/删除开销：ArrayList的add方法默认往数组尾部添加，这是因为在数组中间位置添加需要挪动后续元素的位置；链表直接修改引用就行

    - 内存占用情况：链表需要保存前/后指针

    - 都是线程不安全的

# **1. HashMap**

**底层结构**：数组 + 链表/红黑树存储键值对，通过映射关系链接起**键和数组索引的关系**，使用拉链表解决哈希冲突问题

**哈希函数**：计算出键值在数组的对应位置

- 计算公式：`hash & (size - 1)`

    - hash：key的hashcode
    - size：数组长度

- 公式思路：取key的hash值特征来得出索引，”与“运算比“取模”运算有更低的计算开销，但是要注意：

    - 数组长度应为**2的幂次方**

    - 对于低位特征不明显（基本全是1或者0）的key，在基本数组长度都较小的情况下，分布将不够均匀，所以HashMap还会通过将hash值的**高16位和低16位**进行”异或“运算

- `哈希冲突`：指数组长度有限，即使key的hash不同，通过公式计算仍可能处于同一个数组索引位导致冲突

    - 解决思路：采用拉链法解决该问题，具体采用长度小于8使用链表，**长度大于等于8且桶数组长度达到64时**转换链表为红黑树

**put过程**：

1. 调用key的hashCode()方法获得哈希值，将其高16位和低16位异或操作得到更具特征的哈希值

2. 通过哈希函数key & (size - 1)得出哈希值对应数组的下标值

3. 判断当前数组位置是否有元素，无则直接存入，有则遍历该桶，有相等key则替换，否则将新元素插入到链表的尾部（尾插法）

4. 最后判断**键值对个数**是否超过阈值，阈值通过`size数组长度 * factor负载因子`计算得出，若超过则执行resize()扩容

**get过程**：

1. 同样根据put过程得到最终数组下标索引

2. 根据桶的数据结构进行查询

**resize过程**：

1. 创建一个当前数组**两倍长度**的新数组

2. key重新计算hash，采用头插法的方式将数据拷贝到新数组上

    元素rehash后的结构，只会在当前位置，或当前位置+旧数组长度，这是由于哈希函数使用“与”运算导致的

    - 当前位置公式：oldCap & hash == 0
    
        (2oldCap - 1) & hash = (oldCap - 1) & hash

        => 2oldCap & hash - 1 & hash = oldCap & hash - 1 & hash

        => 2(oldCap & hash) = oldCap & hash

        => oldCap & hash = 0

**扩容先后问题**：因为HashMap是集中式扩容，所以扩容和插入的先后顺序没有很大关系，实现上采用**先插入后扩容**；而redis是渐进式扩容，所以需要**先扩容再插入**

**头插法死循环**：有两个因素，一个是迭代旧桶和插入新桶的顺序不一致，另外一个是并发环境下触发了两次resize()

    两个线程各自resize()的过程，对某个桶的链表的迁移过程出现了闭环
    
    顺序不一致：线程获取的结点顺序是正序，而头插法插入到新桶的顺序是倒序的

    并发触发两次resize：A线程可能获取到旧桶的顺序后还未开始迁移到新桶时被暂停，而B线程完成了迁移过程，当A线程重新获得cpu时间片完成后续resize的迁移过程后产生闭环

- 正序遍历：从桶头到桶尾遍历

- 正序插入（头插法）：将新结点直接插入到桶头位置，旧桶头作为其下一个节点

- 倒序插入（尾插法）：新结点直接插入到桶尾

# **2. LinkedHashMap**

# **3. ConcurrentHashMap**

HashTable、Collections.synchronizedMap同步加锁可以解决线程安全问题，但是锁粒度过大性能过差，ConcurrentHashMap更适合高并发场景使用

- JDK7：Segment + HashEntry的分段锁实现，底层结构的最外层是一个Segment数组，每个Segment元素中包含一个HashEntry数组

    - 结构：锁粒度是Segment

        - Segment：Segment继承自ReentrantLock

        - HashEntry：是一个链表结构，具有保存key、value的能力和指向桶下一个结点的指针（final指针）

        相当于每个Segment都是一个HashTable，默认的Segment长度是16，**并发写的程度是16个线程**

        > 所以每个键值对都需要经过**两次hash**来找到最终的数组位置，一次是定位Segment，一次是定位Segment对应位置

    - 操作

        - put：两次hash计算、自旋尝试（默认2次）、操作时加锁

            1. 第一次hash函数计算，定位到hash对应具体的Segment，如果Segment为空则初始化

            2. 使用ReentrantLock尝试加锁，失败则**尝试自旋**（默认2次），自旋超过一定次数则阻塞挂起

            3. 获取到锁后，第二次hash函数计算，计算HashEntry数组对应的HashEntry，再遍历HashEntry链表

        - remove：操作时加锁、删除结点前可能会有复制开销
        
            复制删除结点之前的结点（next指针final），final的next指针意味着无需加锁就可以遍历，这种做法默认加锁成本大于每次删除的复制开销，不适用于频繁删除场景

        - get：最终一致性，类似读快照的方式
        
            整个结构的域基本都是volatile，通过类似count的结构变量来感知集合的变化，以尽量做到无锁化

        - resize：在put过程中扩容（先插入再扩容），跟put操作的锁是同一个

- JDK8：改为使用CAS + synchronized实现，包括了一个Node数组，synchronized的**监视器为桶头结点**

    - 结构：并发粒度为Node

        - Node：包含key、value、next属性，都为**volatile类型**的，以保证在线程之间的可见性

        - sizeCtl：用于协调容器的某些过程（初始化、扩容）和记录当前键值对元素的数量

            - -1：表示当前的Node桶正在初始化

            - \>0：表示当前容器的扩容阈值

            - < -1：表示当前容器正在扩容

    - 操作：

        - put：CAS+自旋初始化桶，桶头采用CAS写入、扩容辅助、桶其他结点使用synchronized写入

            1. 计算hash，方式与HashMap类似，不过会将符号位采用HASH_BITS去除掉最高位，因为桶头元素的负hash值有其他含义（-1/-2/-3）

            2. 如果桶没有初始化，则通过对sizeCtl进行CAS设置-1 + 自旋，保证只有一个线程执行初始化逻辑，其他线程会自旋等待

            3. 如果桶已初始化，则计算出数组下标位置，如果位置为空直接使用CAS设置，成功则后续**进入addCount扩容逻辑**；失败则重新进入自旋逻辑

            4. 如果桶不为空，且桶头hash值不为-1，则直接使用监视器锁锁定当前桶，并**进入判断addCount扩容判断**

            5. 如果桶不为空，且桶头hash值为-1，表示当前容器正在执行扩容逻辑，调用`helpTransfer()`方法辅助扩容

        - remove：扩容辅助、使用synchronized写入

        - transfer：当前数量超过sizeCtl触发 / 有链表的长度到达8，且当前数组长度还未超过64，`ForwardingNode标识当前桶已处于迁移状态`
        
        1. 扩容会将数组分成多个分段，每个分段大小通过当前进程的cpu个数进行计算，最小为16
        
        2. 每个线程通过transferIndex和分段大小来对自己的区域进行迁移，迁移线程之间互不影响通过**sizeCtl**来进行协调
        
        3. 最后一个完成的线程会做容器的扩容完善工作，如重新设置sizeCtl为扩容后的新阈值

        4. 扩容的算法：计算lastRun，将lastRun作为新桶（新旧位置不确定）的头结点，并重新遍历当前旧桶的数据，与lastRun高位不同的依次放入另一个链表中

        ![ConcurrentHashMap扩容算法](https://asea-cch.life/upload/2022/01/ConcurrentHashMap%E6%89%A9%E5%AE%B9%E7%AE%97%E6%B3%95-e664284acb5b47689bc06f79849d5aae.png)

        > get操作不会触发扩容

> JDK8的concurrentHashMap特征：`粒度缩小`（桶头的状态），`扩容新增辅助`的概念（需要考虑扩容过程中出现其他操作的情况），`扩容粒度缩小`（可以在某种程度上边操作容器边扩容），`get()新增转发结点概念`

问题1：扩容期间可以插入数据吗？

可以，只要插入的位置还没有被扩容线程迁移到，就可以插入，与此同时同时迁移线程到达插入位置准备迁移时，会阻塞等待插入数据完成后再进行迁移

以上两个过程都通过CAS / synchronized进行互斥，保证线程安全

问题2：扩容期间可以查询数据吗？

JDK7的方式是直接查询旧数组

JDK8更为实时，分为两个情况：

- 当前桶结点处于正在迁移的过程中，桶头还未设置为ForwardingNode

    新形成的hn和ln链最后通过CAS设置到**新数组**上，即没有修改原本的数组，所以可以读到之前的hash桶上的链表

- 当前桶结点已经迁移完毕，桶头为ForwardingNode

    get方法会调用`结点的find()`，而ForwardingNode的find方法则是**转发到新的数组上**进行搜索

问题3：为什么桶为null用CAS，桶不为空是synchronized

桶为null意味着竞争不激烈

# **4. ArrayList和LinkedList的区别**

- 底层数据结构

    ArrayList底层是一个`Object数组`，是连续的内存空间
    
    LinkedList底层是一个`双向链表`，不具备连续内存空间

- 随机访问性（内存分布情况）

    ArrayList由于是`连续的内存空间`，具备随机访问性，可以直接通过`索引下标`计算出Base + Offset的位置访问内存

    LinkedList的底层结构是`不连续的`，无法提供随机访问性

    > 遍历LinkedList要用迭代器而不是for循环指定i，后者会产生n * (n - 1) * ... * 1的性能开销

- 插入和删除开销（对于已经找定某个位置下元素）：

    ArrayList：采用数组存储，所以插入和删除元素的时间复杂度受`元素位置`的影响：
    
    - 如果在指定位置添加和删除元素的话，其之后的元素都需要往前/后挪动，所以`add()操作是直接往数组末尾添加`
    
    - 当底层数组不够存储时扩容，ArrayList会计算添加个数与当前元素个数之和，并与当前底层数组大小的进行对比，不够时则扩张1.5倍

    LinkedList：采用链表，插入，删除元素时间复杂度不受元素位置影响，直接将指针改一下就好了，时间复杂度为O(1)

- `内存占用空间`：链表的元素要存储prev和next指针，数组的结尾会预留一定的容量空间

- 两者都是线程不安全的

LinkedList使用场景：

1. 往list中add一个数据，且list已经有几十万个数据时，如果触发扩容则需要复制几十万的数据速度极慢

2. 当需要删除list中的某个数据时（不是指定索引删除，是指定元素），linkedlist也优于arraylist，因为双方都需要遍历获取到元素位置，但arraylist需要挪动后面元素的位置

# **5. CopyOnWriteArrayList**



# 参考
- [红黑树和AVL树的比较](https://blog.csdn.net/zzt4326/article/details/88590731)

- [ConcurrentHashMap7](https://www.cnblogs.com/ITtangtang/p/3948786.html)：HashEntry的next指针域为final类型，每次删除某个元素e时，会将e之前的结点全都克隆一遍（e之后的结点可以复用），并将e之前结点链的队尾结点链接到e之后结点链的第一个结点上。这种做法的思路是：`不变性访问不需要同步从而节省时间`，但是会额外带来删除的复制开销

- [ArrayList和LinkedList区别（蚂蚁金服面试题）](https://cnblogs.com/ysyy/p/10891079.html)

# 重点参考
- [ConcurrentHashMap8](https://blog.csdn.net/ZOKEKAI/article/details/90051567)
