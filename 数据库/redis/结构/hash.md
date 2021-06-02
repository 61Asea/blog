# Redis结构：Hash/dict

> [哈希表](https://asea-cch.life/achrives/hash)

> [hashMap](https://asea-cch.life/achrives/hashmap)

    具体的哈希表数据结构，以及JDK中的HashMap实现内容，可以查看上文

Redis的哈希与JDK7的HashMap大部分有异曲同工之妙，但是Redis有其自身的着重点：

1. Redis是内存型数据库，需要考虑内存的开销
2. Redis在4.0版本之前，是单线程运行的；在4.0版本之后，逐步开始支持多线程，如异步新增、删除

通过Redis以上两点考究点，我们来分析Redis的hash与其他hash结构实现的不同

# **1. 基础结构**

为了减少内存的开销，Redis通过两个阈值适时调整Hash结构的底层存储结构策略。当以下两个条件都满足时，使用压缩列表；若有一个条件不满足，则使用dict结构进行存储

- hash_max_ziplist_entries

    hash从ziplist向dict转换的**最大键值对数量**阈值，默认为512个

- hash_max_ziplist_value

    hash从ziplist向dict转换的**单个键值大小长度**阈值，默认为64字节

## **1.1 压缩列表**

> [Redis结构：ziplist](https://asea-cch.life/achrives/ziplist)

对底层存储结构是压缩列表的Hash**执行新增、删除操作**时，会造成数组元素的移动。当存储量级较大时，元素的移动成本将无法忽视

但是如果存储量级较小时，使用ziplist无疑可以发挥更好的内存利用率，这符合Redis的第一个需求

## **1.2 dict/dictht/dictEntry**

    dictht也是通过拉链法解决哈希冲突，所以底层结构采用：数组 + 链表

紧接着1.1关注的问题：当使用数组时，总是要考虑增删操作时，元素移动的巨大成本；所以使用链表，可以解决这个问题（虽然失去了随机访问性）

### **dict**

最外层结构体，类比于JDK的HashMap

```c++
typedef struct dict {
    dictType *type;
    void *privdata;
    // 注意，是一个dictht数组，存放着两个底层数组结构，这跟其扩容机制相关
    dictht ht[2];
    // 单线程Redis的扩容标识，该标识表明redis的扩容机制是渐进式扩容
    long rehashidx;
    unsigned long iterators;
} dict;
```

### **dictht**

存储的底层结构，类比于HashMap的Node数组

```c++
typedef struct dictht {
    // dictEntry数组
    dictEntry **table;
    // 数组的总大小
    unsigned long size;
    // 计算key对应数组的索引掩码，值是size - 1
    unsigned long sizemask;
    // 数组已存放的元素个数
    unsigned long used;
} dictht;
```

    注意sizeMask的值为size - 1，证明redis与HashMap的哈希函数相同

### **dictEntry**

键值对桶结点，类比于HashMap的Node类。因为采取拉链法，所以两者都有对桶的下一个节点的引用。

```c++
typedef struct dictEntry {
    void *key;
    union {
        void *val;
        uint64_t u64;
        int64_t s64;
        double d;
    } v;
    // 指向下一个桶节点
    struct dictEntry *next;
} dictEntry;
```

# **2. 关键操作**

下面的介绍，都以dict结构为原型进行介绍，详情源码和结构可自行参考redis源码：

> [Redis官网，点击稳定版的Download按钮，即可查看c++源码](http://www.redis.cn/download.html)

## **2.1 get**

哈希值计算：

```c++
#define dictHashKey(d, key) (d) ->type->hashFunction(key)
```

JDK的HashMap对hashCode进行了扰动，防止低位特征不明显而造成分布不均

哈希函数：

    h & d->ht[table].sizemask，其中sizeMask与HashMap一致，都是数组长度 - 1

```c++
dictEntry *dictFind(dict *d, const void *key)
{
    dictEntry *he;
    uint64_t h, idx, table;

    if (dictSize(d) == 0) return NULL; /* dict is empty */
    if (dictIsRehashing(d)) _dictRehashStep(d);
    // 计算key的哈希值
    h = dictHashKey(d, key);
    // 查找ht[0]和ht[1]共两个表
    for (table = 0; table <= 1; table++) {
        // 通过哈希函数获得数组的下标
        idx = h & d->ht[table].sizemask;
        he = d->ht[table].table[idx];
        while(he) {
            if (key==he->key || dictCompareKeys(d, key, he->key))
                return he;
            he = he->next;
        }
        // 如果没有在扩容，则第二个表没有启用，直接返回NULL
        if (!dictIsRehashing(d)) return NULL;
    }
    return NULL;
}
```

读操作大致与HashMap思路相同，主要是扩容时，会先在ht[0]查找，找不到再去ht[1]查找

## **2.2 put**

```c++
int dictAdd(dict *d, void *key, void *val) {
    // 先构造键值对节点
    dictEntry *entry = dictAddRaw(d, key, NULL);
    if (!entry) return DICT_ERR;
    // 加入到哈希表后，再设置值
    dictSetVal(d, entry, val);
    return DICT_OK;
}
```

redis会先设置节点到结构中，再对节点的value字段进行赋值

```c++
dictEntry *dictAddRaw(dict *d, void *key, dictEntry **existing)
{
    long index;
    dictEntry *entry;
    dictht *ht;

    if (dictIsRehashing(d)) _dictRehashStep(d);

    // 先判断是否需要扩容，需要的话，接下来的put操作应该往第二个table去put 
    if ((index = _dictKeyIndex(d, key, dictHashKey(d,key), existing)) == -1)
        return NULL;

    ht = dictIsRehashing(d) ? &d->ht[1] : &d->ht[0];
    entry = zmalloc(sizeof(*entry));
    // put操作
    entry->next = ht->table[index];
    ht->table[index] = entry;
    ht->used++;

    dictSetKey(d, entry, key);
    return entry;
}
```

与HashMap不同，redis会先扩容，再执行put操作，因为redis是渐进式扩容，它需要在put操作前先感知当前已经开始扩容了

## **2.3 扩容机制**

redis采用渐进式扩容，这是redis对于第二个需求（单线程）的考量，这种扩容机制是一种分而治之的方式，将rehash的操作分摊在每一次增删查改操作中，避免一次性的集中式rehash

    一次性集中式rehash，意味着有庞大的计算量，对于单线程的redis而言，可能会处于很长的一段时间不可用，这是不允许的

**所以dict结构维护有两个数组，第一个数组为存放数据的table，作为非扩容时的容器；第二个数只有在扩容时才会被启用，并在该期间直到扩容结束，作为主存放数据的容器**

当扩容结束时，会将ht[1]覆盖到ht[0]，并将rehashIndex置为-1，表示扩容结束；反之会将rehashIndex设置为0，表示扩容正在开始

```c++
int dictExpand(dict *d, unsigned long size)
{
    if (dictIsRehashing(d) || d->ht[0].used > size)
        return DICT_ERR;

    dictht n;
    // 计算新的hashtable长度
    unsigned long realsize = _dictNextPower(size);

    /* Rehashing to the same table size is not useful. */
    if (realsize == d->ht[0].size) return DICT_ERR;

    /* Allocate the new hash table and initialize all pointers to NULL */
    n.size = realsize;
    n.sizemask = realsize-1;
    n.table = zcalloc(realsize*sizeof(dictEntry*));
    n.used = 0;

    /* Is this the first initialization? If so it's not really a rehashing
     * we just set the first hash table so that it can accept keys. */
    if (d->ht[0].table == NULL) {
        d->ht[0] = n;
        return DICT_OK;
    }

    /* Prepare a second hash table for incremental rehashing */
    d->ht[1] = n;
    d->rehashidx = 0;
    return DICT_OK;
}
```

## **2.1 扩容后的长度**

网上有人说redis的扩容后的长度与hashMap机制一致，也有人说是两倍于当前已有元素个数的最小2n次方幂长度

在add操作引发的是否rehash判断中，我们可以看到传入的参数为：**第一个数组长度的两倍**
```c++
static int _dictExpandIfNeeded(dict *d)
{
    // 初始化操作（略）

    if (d->ht[0].used >= d->ht[0].size &&
        (dict_can_resize ||
         d->ht[0].used/d->ht[0].size > dict_force_resize_ratio))
    {
        // 传入了ht[0].used * 2
        return dictExpand(d, d->ht[0].used*2);
    }
    return DICT_OK;
}
```

但是在dictExpand方法中，又调用了静态方法_dictNextPower进行计算，

```c++
static unsigned long _dictNextPower(unsigned long size)
{
    unsigned long i = DICT_HT_INITIAL_SIZE;

    if (size >= LONG_MAX) return LONG_MAX + 1LU;
    while(1) {
        if (i >= size)
            return i;
        // 从这里可以看出，返回的是：大于 ht[0].used * 2 的最小2的n次方
        i *= 2;
    }
}
```

关于底层数组长度的最佳实践为2的n次方，redis初始化容量为4，可以得出以下结论：

    一般情况下，旧数组的长度就是2的n次方，扩容后的长度就是扩容后的2倍

    如果旧数组的长度不是2的n次方，需要特殊处理，以保证哈希函数的分布性能

## **2.2 渐进式扩容时，进行访问操作**

    分而治之的方法：_dictRehashStep，执行一次表示进行一次单步的rehash

- 在写操作时

    如果正在扩容，则执行一次_dictRehashStep，并将新增的键值对加入到ht[1]中；否则，不执行_dictRehashStep，加入到ht[0]中

- 在读操作时

    如果正在扩容，则执行一次_dictRehashStep。接着再分别去ht[0]和ht[1]中查找元素；否则，直接去ht[0]查找

- 在删除操作时

    如果正在扩容，则执行一次_dictRehashStep。接着再去ht[0]和ht[1]中查找元素，进行删除；否则，直接去ht[0]查找元素并删除

- 在修改操作时

    与写操作一致，因为底层也是调用写操作的方法

# **3. 与ConcurrentHashMap的对比**

ConcurrentHashMap8是多线程协同扩容，而Redis因其单线程模型，采取渐进式扩容

在扩容效率上，ConcurrentHashMap上更胜一筹

在读效率上，考虑到redis可能没有做哈希扰动，可能会出现底层数组长度较小时分布不均的问题，ConcurrentHashMap更胜一筹

在写效率上，ConcurrentHashMap需要帮助扩容，而redis只需要写到第二个数组即可，Redis更胜一筹

# 参考
- [Redis Hash数据结构的底层实现](https://www.cnblogs.com/ourroad/p/4891648.html)
- [HashMap和Redis的hash的对比](https://blog.csdn.net/wangmaohong0717/article/details/84611426)