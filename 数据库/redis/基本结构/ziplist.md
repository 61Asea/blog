# Redis结构：ZipList

压缩空间的结构：`ziplist`、`intset`

ZipList，又称压缩列表，是Redis中一种为了节省内存空间而设计的数据结构，压缩内存空间，可以提升内存利用率，这是Redis作为**内存**数据库的综合考量

以下类型键在元素数量不多，且单元素大小满足阈值时，会采用ziplist**压缩空间**：
- list键
- sorted set键
- hash键

# **1. 存储结构**

压缩列表是由一系列特殊编码的**连续**内存块组成的顺序型数据结构，在操作上分为编码、解码两部分

一个压缩列表可以包含任意多个节点（entry），每个entry可以保存一个字节数组或者一个整数值

## **1.1 编码结构**

编码结构分为：`zipHeader`、`zipEntry集合`和`zipEnd`三个部分

`ziplist`编码结构：
![压缩列表的编码结构](https://asea-cch.life/upload/2021/06/%E5%8E%8B%E7%BC%A9%E5%88%97%E8%A1%A8%E7%9A%84%E7%BC%96%E7%A0%81%E7%BB%93%E6%9E%84-b2572bf610374f829e337c7ac2060840.png)

- zipHeader：存储了压缩列表的数组信息，不包含真实数据

    - zlbytes：列表的字节长度，占**4个字节**，所以压缩列表大小最大为（2^32 - 1）字节

        表示整个压缩列表总长
        
        例如：假设列表zlbytes值为0x50（十进制80），则总厂就是80字节

    - zltail：zipEntry中尾元素相对压缩列表起始地址的偏移量，占**4个字节**

        **可以计算表尾节点的地址**
        
        例如：假设我们有一个指向压缩列表起始位置的指针p，列表zltail值为0x3c（十进制60），则表尾节点地址为p+60

    - zllen：列表的元素数目，占**2个字节**，可记录（2^16 - 1）数目，但真实数目不仅限于该记录数目

        **方便我们快速获得列表的元素个数**，时间复杂度O(1)

        当列表的元素数目超过（2^16 - 1）时，无法再通过zllen获取到数目，只能**通过遍历整个压缩列表才能获取到元素数目**

- zipEnd：压缩列表的结尾，一般指代zlend结构

    - zlend：结尾占位符，占一个字节，值恒为0xFF，**用于标记压缩列表的末端**

`ziplistEntry`编码结构：

![压缩列表Entry的编码结构](https://asea-cch.life/upload/2021/06/%E5%8E%8B%E7%BC%A9%E5%88%97%E8%A1%A8Entry%E7%9A%84%E7%BC%96%E7%A0%81%E7%BB%93%E6%9E%84-3fe7a7a8afb2420ca50c64572a431e04.png)

    
- zipEntry：存储了压缩列表的真实数据，由**若干个Entry组成**

    - previous_entry_length：表示前一个Entry的字节长度，占1个或5个字节，即可变长度字段

        `previous_entry_length`通过可变长度，对整体的大小进行**更细致**的压缩，这类似于动态结构思想

        > 动态结构思想：JVM在设计对象头结构时，采用了动态结构的设计，在对象的不同状态，对象头可以表示不同的含义

        做法：如果前一个元素的长度小于254字节时，previous_entry_length字段用1字节表示；反之，使用5字节表示，而其中这5个字节中的第1个字节恒为0xFE，后面4个字节才真正表示前一个元素的长度

    - encoding：表示当前元素的编码，可以为**字节数组，整数或者自由数**，同样为可变长度字段

    - content：真实存储的数据

```c++
// uint32_t占4个字节

// zlbytes：列表的首地址，就是zlbytes字段的首地址
#define ZIPLIST_BYTES(zl) (*((uint32_t*)(zl)))

// zltail：zlbytes占4个字节，所以zltail首地址为zl首地址偏移uint32_t长度
#define ZIPLIST_TAIL_OFFSET(zl) (*((uint32_t*)((zl)+sizeof(uint32_t))))

// zllen：zlbytes + zltail占8个字节，偏移2个uint32_t
#define ZIPLIST_LENGTH(zl)   (*((uint16_t*)((zl)+sizeof(uint32_t)*2)))

// 最后一个entry：ZIPLIST_TAIL_OFFSET(zl)的值是尾地址的偏移地址，所以尾元素首地址为zl偏移尾地址偏移地址
#define ZIPLIST_ENTRY_TAIL(zl) ((zl)+intrev32ifbe(ZIPLIST_TAIL_OFFSET(zl)))

// zlend：列表首地址位置 + 列表的地址总字节数 - 1
#define ZIPLIST_ENTRY_END(zl) ((zl)+intrev32ifbe(ZIPLIST_BYTES(zl))-1)
```

## **1.2 解码结构**

每次获取数据内容，如：获取当前Entry的类型和内容、上一个Entry的位置等，都需要经过解码运算，如果每次取值都需要解码，则效率较低，应**考虑将解码结果进行缓存**

```c++
typedef struct zlentry {
    // 前一个元素的长度
    unsigned int prevrawlensize;
    // 前一个元素存储的内容
    unsigned int prerawlen;

    // encoding的长度
    unsigned int lensize;
    // 真实数据的长度
    unsigned int len;

    // 当前元素的首部长度，即当前元素的previous_entry_length + encoding的长度
    unsigned int headersize;

    // encoding表示的具体数据类型
    unsigned char encoding;

    // 当前元素的首地址
    unsigned char* p;
} zlentry;
```

通过解码方法zipEntry，解码出压缩列表的某个元素，并存储于上述的结构体中

# **2. 基础操作**

## **2.1 创建压缩列表**

创建空的压缩列表，只需要分配初始存储空间11个字节，这11个字节列表的第一部分zipHeader和zipEnd

4 + 4 + 2 + 1 = 11：
- zlbytes: 4字节
- zltail：4字节
- zllen：2字节
- zlend：1字节

## **2.2 插入元素**

> 假设插入位置为P

### **2.2.1. 编码**

在1.1编码章节中，讲述了元素在压缩列表中的结构为zipEntry，它包含了三个重要字段：previous_entry_length、encoding和content，插入时需要构造前两个字段的值

- previous_entry_length的构造，分为在空长度数组插入、数组中间插入和数组尾插入三种情况：
    - 空长度数组插入：值为0
    - 数组中间插入：需要获取前一个元素的长度，即插入位置的后一个元素的previous_entry_length值
    - 数组尾插入：需要获取当前数组尾entry的元素长度，尾元素长度为其三个字段长度总和

- encoding的构造，分为整数、字节数组类型
    - 整数：先尝试将数据内容解析为整数，成功则按照整数类型编码存储
    - 字节数组：解析为整数失败，则按字节数组类型编码存储

### **2.2.2 重新分配空间**

将第一步编码的数据插入前，需要将列表的空间先变大（假设第一次插入，初始化时只有11字节），需要重新分配内存空间

由于提到了**previous_entry_length是可变字段长度**，新分配的内存长度不是简单的（旧长度+元素编码长度），应根据插入元素的大小来决定

由于重新分配空间，新元素插入的位置指针P会失效，因此需要预算计算好指针P相对于压缩列表首地址的偏移量，待分配空间之后再偏移

### **2.2.3 内存复制（数据拷贝）**

内存复制是否发生，需要根据P的位置决定，跟JDK的ArrayList一样，如果P在数组中间，那么P之后的元素都需要往后移动N位，这就涉及到了内存复制

在将位置P后的元素移动到新的位置，再将新元素插入到插入位置P（这一步不需要内存复制）

## **2.3 删除元素**

与2.2插入元素类似，删除元素的位置如果在中间，会涉及到内存复制

思想也与JDK的ArrayList删除一样：

1. 根据删除元素的个数计算移动步数

2. 直接内存复制，将P位置之后的全部元素都往前移动第一步计算出的步数

3. 将最后一项元素置空

## **2.4 遍历元素**

从头到尾遍历：

    需要解码当前元素，计算当前元素的长度，才能获取到后一个元素首地址

从尾到头遍历：

    通过previous_entry_length和当前元素的首地址p，我们可以很轻易地推算出前一个元素的首地址为p - previous_entry_length，从而实现压缩列表的从尾到头遍历

## **2.5 增删改导致的连锁更新问题**

上述2.3、2.4以及修改元素大小，都可能会导致后一个元素的previous_entry_length发生变化。后一个元素的变大/变小，会再而影响其后一个元素的previous_entry_length长度，引发连锁反应

这种情况在某一段节点都是253节点（previous_entry_length可变长度的阈值）时发生，连锁反应下的元素都会进行扩展/收缩，这将导致他们都**重新分配内存和内存拷贝**

但是这种情况概率较低，Redis并没有为了避免连锁更新而采取措施，比如说：**对三五个节点进行连锁更新是绝对不会影响性能的**

# **3. 应用分析**

Redis的有序集合zSet、哈希hash以及列表list，都直接或者间接使用了压缩列表

    使用压缩列表的最大好处显而易见，可以节省内存，并减少内存碎片的产生

| 数据结构类型 | 描述 |
| -| -|
| list | 由list-max-ziplist-size参数来调整应用压缩列表的策略 |
| hash | 由hash-max-ziplist-entries和hash-max-ziplist-value参数配合调整 |
| zSet | 由zset-max-ziplist-entries和zset-max-ziplist-value参数配置 |

是否运用压缩列表作为以上三种结构的底层存储数据，最主要的依据是：
- entry的个数不超过某个设定的阈值
- 单个entry的大小不超过某个设定的阈值

以上的依据来源于对**空间复杂度和时间复杂度的权衡**，Redis是**内存型**数据库，更加注重内存的使用情况，压缩列表可以一定程度上减少内存开销，并将数据存储于连续的内存区域

但是，压缩列表执行插入、删除时的时间复杂度为O(N)。如果当前超过阈值限定的存储量级，会导致执行效率不佳，因而需要转换策略，弃用压缩列表，改用真实的数据结构类型

    应考虑Redis本身的第一着重点（即内存型数据库），再兼顾操作的效率

# 参考
- [Redis中压缩列表的使用](https://zhuanlan.zhihu.com/p/71880195)

# 重点参考
- [Redis的压缩列表ZipList](https://segmentfault.com/a/1190000017328042)
- [Redis设计与实现]()