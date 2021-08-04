# Redis类型键：zset

## **编码方式**

有序集合对象的编码可以是`REDIS_ENCODING_INTSET`或者`REDIS_ENCODING_HT`，这句话等价于：列表对象的底层实现可以是`intset`或者`hashtable`

- 当使用ziplist作为zset键的底层结构时，每个集合元素使用**两个紧密相邻**的entry来存储

    第一个entry保存的是元素的成员obj（member），第二个entry保存的是元素的分值（score）

    > 有序集合

- 当使用skiplist作为zset键的底层结构时，skiplist本身还维护了一个hashtable结构

    字典中的每个键都是一个字符串对象，这个字符串对象包含了集合的各个元素值，然后将字典的值全部设置为NULL

## **编码转换**

## **API**

`SPOP`：**随机**取出一个元素，再将其从集合中删除

> 这个方法容易产生误解，会让人以为集合的队尾元素出队，注意：集合是`无序`的，根本没有**队头队尾**的概念
