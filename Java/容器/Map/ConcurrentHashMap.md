# ConcurrentHashMap

> [HashMap](https://asea-cch.life/achrives/hashmap)

HashMap属于并发不安全的容器，在并发情况下甚至会导致死循环问题，在任何有并发场景的情况下，都不应该使用HashMap，JDK8使用尾插法仍会导致问题（桶是红黑树节点情况下，在调用getRoot()时其他线程修改root，导致问题）

# **1. 分段锁，无锁化**

## **1.1 分段锁（JDK8之前）**



## **1.2 无锁化（JDK8）**

```java
public class ConcurrentHashMap<K, V> {
    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    // 
    private static final long SIZECTL;
    private static final long TRANSFERINDEX;
    private static final long BASECOUNT;
    private static final long CELLSBUSY;
    private static final long CELLVALUE;
    
    // 底层数组的
    private static final long ABASE;
    private static final int ASHIFT;


}
```

```java
static final <K, V> Node<K, V> tabAt(Node<K, V>[] tab, int i) {
    // i的值为hashcode通过哈希函数哈希化后的结果，即桶在数组的下标
    return (Node<K, V>)U.getObjectVolatile(tab, ((long)i) << ASHIFT) + ABASE);
}
```

# **2. 关键行为**

## **2.1 ConcurrentHashMap和HashMap的扰乱函数**

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? ) : (h = key.hashCode()) ^ (h >>> 16);
}
```

HashMap和ConcurrentHashMap都使用高16位与原本的低16位进行异或操作，使得低位保持高位的特征，在数组容量较低时（意味着哈希函数将只对关键字的低几位进行哈希化），降低低位特征不明显的关键字的哈希冲突

```java
// ConcurrentHashMap
static final int spread(int h) {
    // HASH_BITS = 0x7fffffff
    // 0x7fffffff = 0111 1111 1111 1111 1111 1111 1111 1111 1111，即除了最高位为0，其他位为1的二进制数，可以屏蔽掉hash的符号，变为32位的正数
    return (h ^ (h >>> 16)) & HASH_BITS;
}
```

注意，spread方法会使用**HASH_BITS将符号位进行屏蔽**，这是因为负值的hash在concurrentHashMap有特殊含义：
- 当hashcode为正数时，表示该hash桶为正常的链表结构
- 当hashcode为负数时，有以下几种情况：
    - static final int MOVED = -1;

        hashcode = -1时，表示该桶正在进行扩容，该哈希桶已被迁移到了新的临时hash表，此时get操作会去临时表进行查找；而put操作，会帮助扩容

    - static final int TREEBIN = -2；

        hashcode = -2时，表示该桶已经树化

### **HashMap的key和value可以为空吗？**

## **2.2 putVal**

与HashMap对比，主要涉及了几个不同：

- ConcurrentHashMap不能放置key/value为null的键值对
- ConcurrentHashMap在第一次putVal时，才初始化底层数组
- ConcurrentHashMap可以感知到桶是否正在扩容，是的化，会参与到扩容过程，扩容完成后，再去设置键值对

```java
final V putVal(K key, V value, boolean onlyIfAbsent) {
    // key或value都不能为空
    if (key == null || value == null)
        throw new NullPointerException();
    // 扰乱函数对key的hashcode进行进一步处理
    int hash = spread(key.hashCode());
    int binCount = 0;
    // Retry-Loop与volatile结合，刷新tab的内存
    for (Node<K, V>[] tab = table;;) {
        // f为key对应的桶；n为数组的长度，i为key哈希化后的数组下标，fh为桶的
        Node<K, V> f; int n, i, fh;
        if (tab == null || (n = tab.length) == 0)
            // 第一次putVal再初始化
            tab = initTable();
        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            // 如果桶为null，说明大概率不会竞争，直接使用CAS尝试设置该桶的头结点
            if (casTabAt(tab, i, null, new Node<K, V>(hash, key, value, null)))
                // CAS成功。对tab的i下标位置，预期为null，设置为新结点
                break;
        }
        else if ((fh = f.hash) == MOVED)
            // 发现桶头结点的hash为-1，说明桶正在扩容，会帮助扩容
            tab = helpTransfer(tab, f);
        else {
            // 正常的并发putVal流程，使用synchronized保证并发安全
            V oldVal = null;
            synchronized (f) {
                if (tabAt(tab, i) == f) {
                    if (fh >= 0) {
                        // 桶的hash大于0，说明是链表结构
                        binCount = 1;
                        for (Node<K, V> e = f;;++binCount) {
                            K ek;
                            if (e.hash == hash && ((ek = e.key) == key || (ek != null && key.equals(ek)))) {
                                // 覆盖旧值
                                oldVal = e.val;
                                if (!onlyIfAbsent)
                                    e.val = value;
                                break;
                            }

                            Node<K, V> pred = e;
                            if ((e = e.next == null)) {
                                // 新结点，使用尾插法
                                pred.next = new Node<K, V>(hash, key, value, null);

                                break;
                            }
                        }
                    }
                    else if (f instanceof TreeBin) {
                        // 树结点
                    }
                }
            }
        }
    }
}
```

# 参考
- [无锁HASHMAP的原理与实现](https://coolshell.cn/articles/9703.html)
- [ConcurrentHashMap1.8 - 扩容详解](https://blog.csdn.net/ZOKEKAI/article/details/90051567)
- [ConcurrentHashMap 1.7和1.8的区别](https://blog.csdn.net/u013374645/article/details/88700927)

# 重点参考
- [ConcurrentHashMap，Hash算法优化、位运算揭秘](https://www.cnblogs.com/grey-wolf/p/13069173.html)