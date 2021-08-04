# Redis基础结构：quicklist

在Redis3.2版本以前，list列表键的底层实现可以是ziplist或者linkedlist

考虑到压缩列表有以下缺点：
- 变更时要**重新分配数组的空间**2
- 如果添加的位置在数组居中，需要将位置后的元素往后挪移1位，造成**内存复制**
2
> 如果在大量级的压缩列表中操作变更，移动数据将使得性能将急剧下降。所以在列表**元素超过512个**或某一元素**长度大于64字节**，会转换为链表结构2

而链表也存在有以下缺点：
- 附加空间相对较高，`prev`和`next`指针就要占去16个字节，相对于ziplist压缩下，**内存利用率低**
- 链表不需要连续，内存单独分配，这加剧**内存的碎片化**

针对ziplist和linkedlist存在的问题，Redis3.2版本结合了ziplist和linkedlist的优点，使用quicklist对list类型键进行优化

# **1. 定义**

![QuickList](https://asea-cch.life/upload/2021/08/QuickList-a6f5d22bbbc649d2ab28497b05d79831.png)

```c++
typedef struct quicklistNode {
    // 前驱结点指针
    struct quicklistNode *prev;
    // 后驱结点指针
    struct quicklistNode *next;
    
    // 不设置压缩数据参数recompress时，指向一个ziplist结构
    // 设置recompress时，指向quicklistLZF结构
    unsigned char *zl;

    // ziplist总长度
    unsigned int sz;

    // ziplist中的节点数，占16bits长度
    unsigned int count : 16;

    // 表示是否采用了LZF压缩算法压缩quicklist节点，1表示压缩过，2表示没压缩，占2 bits长度
    unsigned int encoding : 2;

    //表示一个quicklistNode节点是否采用ziplist结构保存数据，2表示压缩了，1表示没压缩，默认是2，占2bits长度
    unsigned int container : 2;

     //标记quicklist节点的ziplist之前是否被解压缩过，占1bit长度
    //如果recompress为1，则等待被再次压缩
    unsigned int recompress : 1; /* was this node previous compressed? */

    //额外扩展位，占10bits长度
    unsigned int extra : 10; /* more bits to steal for future usage */
}quicklistNode;
```

- prev：指向当前结点的前置结点的指针
- next：指向当前结点的后置结点的指针
- zl：数据指针，指向底层的ziplist/quicklistLZF
- sz：表示zl指向的ziplist的总大小（包括zlbytes、zltail、zllen、zlend和各个数据项）
- count：表示ziplist的entry个数

quicklistNode中存储ziplist，以及前后结点的指针，可以看出quicklist是由`多个ziplist`组成的`双向链表`

```c++
typedef struct quicklist {
    // 指向头部结点的指针
    quicklistNode *head;

    // 指向尾部结点的指针
    quicklistNode *tail;

    // 所有ziplist的entry个数之和
    unsigned long count;

    // quicklist结点的个数
    unsigned long len;

    // 关键参数，决定了ziplist的大小设置
    int fill : QL_FILL_BITS;

    unsigned int compress : QL_COMP_BITS;

    unsigned int bookmark_count : QL_BM_BITS;

    quicklistBookmark bookmarks[];
}quicklist;
```

# **2. API**

## **2.1 push操作**

`quicklistPushHead`：将新的entry加入到quicklist头结点中ziplist的头部

`quicklistPushTail`：将新的entry加入到quicklist尾结点中ziplist的尾部

- 如果头结点/尾结点的ziplist在大小上没有超过限制，那么新数据直接插入
- 如果头结点/尾结点的ziplist太大了（`fill`参数决定），那么新创建一个quicklistNode，数据插入到新的ziplist中，再将该结点插入到quicklist的头/尾部

## **2.2 insert操作**

插入操作代指指定位置（某个entry的前或后）插入，遇到的情况比push操作多且复杂

`quicklistInsertAfter`：在指定位置前插入新的entry

`quicklistInsertBefore`：在指定位置后插入新的entry

下面以一个已存在的entryP作为例子，在其前或后插入新的entryX，共有以下情况：

- P所在结点的ziplist有足够空间插入

    - quicklistInsertBefore：插入在P之前

    - quicklistInsertAfter：插入在P之后

- P所在结点的ziplist空间不足，无法插入

    - P处于所在结点的ziplist的头结点

        - P所处quicklist结点的前驱结点可以插入，插在前驱结点的ziplist的尾部

        - P所处quicklist结点的前驱结点不能插入（为NULL或者大小不满足），在**当前结点和前驱结点之间**新创建一个quicklist结点保存要插入的entry，并插入到quicklist结构中

    - P处于所在的ziplist的尾结点

        - P所处quicklist结点的后驱结点可以插入，插在后驱结点的ziplist的头部

        - P所处quicklist结点的后驱结点不能插入（为NULL或大小不满足），在**当前结点和后驱结点之间新创建一个quicklist结点**，保存要插入的entry，并将结点插入到quicklist结构中

    - P处于所在ziplist的中间位置，将entry插入到ziplist中间的任意位置，并分割当前P所在的quicklistNode结点

## **2.3 remove操作**

`quicklistDelRange`：区间元素删除的函数

做法：quicklist 在区间删除时，会先找到 start 所在的 quicklistNode，计算删除的元素是否小于要删除的 count，如果不满足删除的个数，则会移动至下一个 quicklistNode 继续删除，依次循环直到删除完成为止

# **3. 总结**

通过结合ziplist和linkedlist的方式，quicklist解决了它们各自的缺点，这也可以称为quicklist的优点：

1. 通过多个ziplist作为链表结点的方式，可以减少linkedlist的结点数，更大程序地提升内存利用率

2. 通过fill参数控制ziplist的大小，可以控制每个结点上的ziplist不会过于庞大，从而导致的数据量级过大时，移动数据的操作性能过差问题

# 参考
- [Redis数据结构：快速列表](https://www.cnblogs.com/hunternet/p/12624691.html)

# 重点参考
- [Redis源码剖析和注释（七）--- 快速列表(quicklist)](https://blog.csdn.net/men_wen/article/details/70229375)