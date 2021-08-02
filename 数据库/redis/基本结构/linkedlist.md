# Redis基本结构：linkedlist

链表结构内置在很多高级的编程语言中，但redis使用C语言开发，并没有内置这种数据结构，所以Redis构建了自己的链表实现

使用场景（有但不仅限于）：

- 发布与订阅模式
- 慢查询
- 监视器
- Redis服务器用来保存多个客户端的状态信息
- 构建客户端输出缓冲区
- **`list列表键`的底层实现之一**

    当一个list列表键包含了**数量比较多**的元素，又或者列表中包含的元素都是**比较长的字符串**时，Redis就会使用链表作为list键的底层实现

    > list键的另一种底层实现是ziplist压缩列表，这只在元素较少/元素的字符串长度小于阈值时使用

# **1. 定义**

链表节点同时有前/后置节点的指针引用，组成`双向链表`

```c++
// 链表结点
typedef struct listNode {
    // 前置节点
    struct listNode *prev;
    // 后置节点
    struct listNode *next;
    // 节点的值
    void *value;
}listNode;
```

注意：不是闭环链表，头结点的前置节点与尾节点的后置节点都指向了null

```c++
// 链表主体
typedef struct list {
    // 链表头结点
    listNode *head;
    // 链表尾节点
    listNode *tail;
    // 链表所包含的节点数量
    unsigned long len;
    // 节点值复制函数
    void *(*dup) (void *ptr);
    // 节点值释放函数
    void (*free) (void *ptr);
    // 节点值对比函数
    int (*match) (void *ptr, void *key);
}list;
```

list结构为链表提供了表头指针`head`、表尾head指针`tail`，以及链表长度计数`len`，并且提供`dup`，`free`，`match`函数，方便对节点值进行操作

Redis的链表实现特性如下：
- 结点双端：获取某个结点的前置和后置结点，时间复杂度都是O(1)
- 链表双端：获取链表的表头和表尾，时间复杂度都是O(1)
- 无环：头结点的prev指针，与尾节点的next指针，都指向NULL，对链表的访问以NULL为终点
- 长度计数：对list的计数，时间复杂度为O(1)
- 多态：链表节点使用void*指针来保存节点值，并且可以通过list的操作函数对值设置类型，**所以链表可以用于保存各种不同类型的值**

# **2. 区别（与JDK.LinkedList）**

```java
// JDK内置的链表对象
public class LinkedList<E> extends AbstractSequentialList<E> implements List<E>, Deque<E>, Cloneable, java.io.Serializable {
    // 结点类
    private static class Node<E> {
        // 范型指定值
        E item;
        // 后置结点
        Node<E> next;
        // 前置结点
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }
}
```

LinkedList通过范型指定链表结点的值类型，所以不能很轻松地满足多态（除非值类Object类型，并通过反射操作进行类型转换）

# 参考
- [Redis设计与实现]()