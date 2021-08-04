# Redis类型键：list列表键

Redis3.2之前，list列表键通过ziplist或者linkedlist底层结构，来对不同的长度场景做优化。然而ziplist或者linkedlist都不是最完美的方案：

- 在接近更换为linkedlist的阈值条件时，ziplist有可能**数据量级极大**，这在频繁更新的场景下，造成了极大的性能损耗

    原因：变大导致重新分配数组内存，移动导致内存复制

- 当真正更换为linkedlist时，又面临内存利用率差的问题，这对于内存型数据库而言是不友好的

    原因：链表结点中的`prev`和`next`指针就占据了4字节，附加占用空间显著提升

所以，Redis3.2之后，结合ziplist和linkedlist的优点，使用`quicklist`结构来对列表进行更进一步的优化

## **1. Redis3.2版本前**

### **编码方式**

列表对象的编码可以是`REDIS_ENCODING_ZIPLIST`或者`REDIS_ENCODING_LINKEDLIST`，这句话等价于：列表对象的底层实现可以是`ziplist`或者`linkedlist`

- 当使用ziplist编码作为底层实现，则每个压缩列表的entry保存一个列表元素

- 当使用linkedlist编码作为底层实现，则每个node保存一个**字符串对象**，每个字符串对象都保存了一个列表元素

> linkedlist的链表结点结构中包含了多个字符串对象，字符串对象是Redis五种类型对象中唯一一种会被其他四种类型对象嵌套的对象

### **编码转换**

当列表对象可以同时满足以下两个条件时，使用`ziplist`编码：

1. 列表的元素个数不大于512个

2. 列表的所有字符串元素的长度都小于64字节（元素越大，移动成本也随之变大）

若不能同时满足以上两个条件，则将对象编码转换为linkedlist，entry元素会被转移并保存到双端链表中

## **2. Redis3.2版本后**

### **编码方式**

统一使用quicklist，结合两种编码结构的优势：
- linkedlist：快速插入、删除，没有移动元素成本
- ziplist：提升内存空间利用率，减少内存碎片

### **编码转换**

不存在

## **3. 常用指令**

`LPUSH`：将新数据从列表的左边，推入到双端列表头结点的压缩列表头

```redis
redis> LPUSH numbers 1 "three" 5
OK
```

`RPUSH`：将新数据从列表的右边，推入到双端列表尾结点的压缩列表尾

`LLEN`：返回列表的长度

`LPOP`：将列表的头结点弹出

`RPOP`：将列表的尾节点弹出

`LINDEX`：返回列表对应索引位置的数据

```redis
redis> LINDEX numbers 1
"three"
```

`LINSERT`：往**指定元素**的**BEFORE**或**AFTER**进行插入

```redis
redis> LINSERT numbers BEFORE 1 2
OK

redis> LINDEX numbers 1
"2"
```

`LSET`：设置**指定索引**位置的元素值，不能超出范围

# 参考
- [Redis设计与实现]()
- [Redis基础结构：quicklist](https://asea-cch.life/achrives/redis-quicklist)