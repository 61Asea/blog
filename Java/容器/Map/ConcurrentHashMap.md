# ConcurrentHashMap

> [HashMap](https://asea-cch.life/achrives/hashmap)

HashMap属于并发不安全的容器，在并发情况下甚至会导致死循环问题，在任何有并发场景的情况下，都不应该使用HashMap，JDK8使用尾插法仍会导致问题（桶是红黑树节点情况下，在调用getRoot()时其他线程修改root，导致问题）

# **1. 分段锁/CAS**

## **1.1 分段锁（JDK8之前）**



## **1.2 CAS（JDK8）**

```java
public class ConcurrentHashMap<K, V> {
    private static final sun.misc.Unsafe U;
    private static final long SIZECTL;
    private static final long TRANSFERINDEX;
    private static final long BASECOUNT;
    private static final long CELLSBUSY;
    private static final long CELLVALUE;
    // 数组的随机访问性，ABASE为数组对象的基础偏移，ASHIFT是每个数组元素的位移地址
    // (n << ASHIFT) + ABASE：数组随机访问性质，在内存中是连续的，只要知道基础位置，再加上(偏移量 * 第N项)即可获得数组元素
    private static final long ABASE;
    private static final int ASHIFT;

    static {
        // 加载字段在类的偏移量，和加载Node[]数组的基础偏移量和元素偏移量
    }
}
```

Unsafe在ConcurrentHashMap包装的三个关键方法，这三个方法用于设置/修改桶头：

```java
// 可见性地获知在底层数组上的某一个桶
static final <K, V> Node<K, V> tabAt(Node<K, V>[] tab, int i) {
    // i的值为hashcode通过哈希函数哈希化后的结果，即桶在数组的下标
    return (Node<K, V>)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
}

// CAS操作某个桶元素
static final <K, V> boolean casTabAt(Node<K, V>[] tab, int i, Node<K, V> c, Node<K, V> v) {
    return U.compareAndSwapObject(tab, ((long) i << ASHIFT) + ABASE, c, v)
}

// 可见性地设置某个桶，设置后其他线程可以感知到该桶的变化
static final <K, V> void setTabAt(Node<K, V>[] tab, int i, Node<K, V> v) {
    U.putObjectVolatile(tab, ((long) i << ASHIFT) + ABASE, v);
}
```

Unsafe使用Unsafe.compareAndSwapLong/Int(object, offset, expect, value)，在ConcurrentHashMap设置一些存在并发安全字段：
- 计数

    addCount()方法：修改baseCount和cellValue，用于并发地统计数量

- 扩容

    addCount()方法：修改sizeCtl，该字段为正数时，与HashMap.threshold一致；为负数时，代表当前正在扩容，-N表示有N - 1个线程正在参与扩容过程

    transfer()方法：修改sizeCtrl，将其减一

- 数据迁移

    transfer()方法：修改transferIndex

- CAS自旋标记

    fullAddCount()方法：修改cellBusy，是调整大小和/或创建计数器单元格时使用的自旋锁（通过CAS锁定）。


# **2. 基础设施**

## **2.1 静态变量**

```java
// JDK7遗留，JDK8已经本身逻辑不使用，但在writeObject时会使用它初始化Segment，以兼容之前版本的ConcurrentHashMap
private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

// 扩容时，每个线程的最少搬运量
// 扩容时使用，如果线程在并发put时，发现map正在扩容，会帮助其扩容
private static final int MIN_TRANSFER_STRIDE = 16;

// 位的个数，用来生成戳，用在sizeCtl里
private static int RESIZE_STAMP_BITS = 16;

// 参与扩容的最大线程数
private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1;

// 用来记录在sizeCtl中，size戳的位偏移数
private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;

// 判断节点类型，-1为移动过的节点，-2为树节点，-3为临时节点
static final int MOVED = -1;
static final int TREEBIN = -2;
static final int RESERVED = -3;
// 31个1，用于将一个负数变成正数，但是不是取绝对值?
static final int HASH_BITS = 0x7fffffff;

// 运行的CPU数
static final int NCPU = Runtime.getRuntime().availableProcessors();
```

## **2.2 成员变量**
```java
// 底层数组，懒加载，不会在刚构造的时候就被初始化
transient volatile Node<K, V>[] table;

// 扩容过程中，会将新的table赋值给nextTable，结束后设置为null
private transient volatile Node<K, V>[] nextTable;

// 基本计数器值，主要在没有竞争时使用，通过CAS将增量累加
private transient volatile long baseCount;

// 该变量的值有不同的含义：
// sizeCtl = -1：表示当前table正在初始化（有线程正在创建table数组），当前线程需要自旋等待

// sizeCtl < -1：表示当前table数组正在扩容，高16位表示扩容的标识戳；低16位表示：（1 + nThread）个线程正在参与并发扩容

// sizeCtl = 0：表示创建table数组时，使用DEFAULT_CAPACITY为默认大小

// sizeCtl > 0：如果table未初始化，则表示初始化大小；如果table已初始化，则代表下次扩容的阈值（类似于HashMap.threshold）
private transient volatile int sizeCtl;

// 扩容过程中，记录当前进度，所有线程都需要从transferIndex中分配区间任务，去执行自己的任务
private transient volatile int transferIndex;

// 当要修改cells数组时加锁，防止多线程修改cells数组，0为无锁，1为有锁
// counterCells初始化；扩容；某个元素为null，给这个位置创建新的cell对象时
private transient volatile int cellsBusy;

// 当baseCount发生竞争后，会创建counterCells数组，线程会通过计算hash值取到自己的cell，将增量累加到指定cell中
// 总数 = sum(cells) + baseCount
private transient volatile CounterCell[] counterCells;
```

## **2.3 内部类**

在2.1中，我们发现桶头的hash的值可以是负数的，分别有MOVED、TREEBIN、RESERVED：
- MOVED = -1

    hashcode = -1时，表示该桶正在进行扩容，该哈希桶已被迁移到了新的临时hash表，此时get操作会去临时表进行查找；而put操作，会帮助扩容

- TREEBIN = -2

    hashcode = -2时，表示该桶已经树化

- RESERVED = -3

以上三种hash，分别对应了四种不同类型的Node（红黑树TreeBin和TreeNode）和最基本的Node类

## **2.3.1 Node**

相较于HashMap，Node在ConcurrentHashMap中更像是一个**视图**：
- 禁止setValue操作，防止并发修改
- val值、next引用为volatile，确保最终一致性
- 提供find(int, Object k)行为，使得在get方法中可以调用不同类型桶的遍历方式

```java
static class Node<K, V> implements Map.Entry<K, V> {
    final int hash;
    final K key;
    volatile V val;
    volatile Node<K, V> next;

    Node(int hash, K key, V val, Node<K, V> next) {
        this.hash = hash;
        this.key = key;
        this.val = val;
        this.next = next;
    }

    // ...

    Node<K, V> find(int h, Object k) {
        Node<K, V> e = this;
        if (k != null) {
            do {
                K ek;
                if (e.hash == h && ((ek = e.key) == k || (ek != null && k.equals(ek))))
                    return e;
            } while ((e = e.next) != null);
        }
        return null;
    }
}
```

## **2.3.2 ForwardingNode**

在扩容期间，会在桶头插入一个ForwardingNode节点，发现是桶头结点的hash值是MOVED：非扩容线程put操作，进入辅助扩容的流程；get操作，会在临时表中寻找元素

该结点本身并不存储键值对，只是作为一个标记插入到桶头

```java
static final class ForwardingNode<K, V> extends Node<K, V> {
    // 临时表
    final Node<K, V>[] nextTable;

    ForwardingNode(Node<K, V>[] tab) {
        // 该结点本身并未有数据
        super(MOVED, null, null, null);
        this.nextTable = tab;
    }

    // 在临时表中寻找元素
    Node<K, V> find(int h, Object k) {
        // 做循环，以避免在forwardingNode上的任意深度递归
        outer: for(Node<K, V>[] tab = nextTable;;) {
            Node<K, V> e; int n;
            // 当扩容完成后，会将nextTable置为空，并将表赋值到table上，其他线程的get操作将返回null
            if (k == null || tab == null || (n = tab.length) == 0 || (e = tabAt(tab, (n - 1) & h) == null))
                return null;
            for (;;) {
                int eh; int ek;
                if ((eh = e.hash) == h && ((ek = e.key) == k || (ek != null && k.equals(ek))))
                    return e;
                if (eh < 0) {
                    if (e instanceof ForwardingNode) {
                        // 重新获取nextTable的情况
                        tab = ((ForwardingNode<K, V>)e).nextTable;
                        continue outer;
                    }
                    else
                        // 树结点的查找
                        return e.find(h, k);
                }
                if ((e = e.next) == null)
                    return null;
            }
        }
    }
}
```

重点在于将本身对table某个桶的遍历 =》 临时表的桶遍历，这个过程需要再做一次哈希化，通过该方式实现**读与扩容不互斥**

## **2.3.3 TreeBin/TreeNode（源码分析未完成）**

与HashMap类似，HashMap的树化过程是将全部节点都先转化为TreeNode，再将桶头作为树的根节点，接下来的元素一个个往树插入，红黑树的**控制平衡**、**插入**和**查找根节点**等方法代码都在TreeNode类中

```java
// ConcurrentHashMap.TreeNode，少掉了很多红黑树的方法
static final class TreeNode<K, V> extends Node<K, V> {
    TreeNode<K, V> parent;
    TreeNode<K, V> left;
    TreeNode<K, V> right;
    TreeNode<K, V> prev;
    boolean red;
    // ....
}
```

ConcurrentHashMap则通过TreeBin对象来抽象红黑树桶这一概念，TreeNode只用于做单纯的红黑树节点结构，并通过TreeBin来管理各个节点

```java
static final class TreeBin<K, V> extends Node<K, V> {
    TreeNode<K, V> root;
    volatile TreeNode<K, V> first;
    volatile Thread waiter;
    volatile int lockState;

    // 写锁占用
    static final int WRITER = 1;
    // 等待获取写锁
    static final int WAITER = 2;
    // 读锁，每多一个读者，数量递增1
    static final int READER = 4;

    TreeBin(TreeNode<K, V> b) {
        // hash值为TREEBIN，即-2
        super(TREEBIN, null, null, null);
        this.first = b;
        TreeNode<K, V> r = null;
        // 通过b和后续的节点，构建红黑树，并将最终的根节点赋值到r上
        this.root = r;
    }

    // 红黑树的各种管理方法...

    // 重写ConcurrentHashMapo.Node的find行为
    final Node<K, V> find(int h, Object k) {
        // 二叉搜索树，从根节点开始搜索
        // 读写锁
        if (k != null) {
            for (Node<K, V> e = first; e != null; ) {
                int s; K ek;
                if (((s = lockState) & (WAITER|WRITER)) != 0) {
                    if (e.hash == h && ((ek = e.key) == k || ek != null && k.equals(ek)))
                        return e;
                    e = e.next;
                }
                else if (U.compareAndSwapInt(this, LOCKSTATE, s, s + READER)) {
                    // ...
                }
            }
        }
    }
}
```

## **2.3.4 ReservationNode**

根据注释的翻译，它是在computeIfAbsent和compute中使用的占位符节点

```java
static final class ReservationNode<K, V> extends Node<K, V> {
    ReservationNode() {
        super(RESERVED, null, null, null);

        Node<K, V> find(int h, Object k) {
            return null;
        }
    }
}
```

在computeIfAbsent和compute过程中，会先生成一个ReservationNode节点，**先对其进行上锁**，再计算val的最终值生成真正的Node，最后再通过CAS将其设置为桶头。**注意，这里的val计算过程为延迟计算，并没有先计算**

不同与compute行为，put行为直接使用桶头将其CAS设置，这是因为compute方法需要互斥掉对相同key的put操作，刚刚提到了val计算为延迟计算，如果此时没有互斥的话，其他put操作很可能重新插入新的，相同key的Node，导致出现两个相同key的Node

    其实也可以先计算val生成Node，然后再进行与put相同的过程，猜测作者希望做到计算有效化，避免无效计算
    
    如果先计算val，然后发现并不能compute，那么就白计算了

```java
public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    // ...

    else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
        Node<K, V> r = new ReservationNode<K, V>();
        synchronized (r) {
            if (casTabAt(tab, i, null, r)) {
                // 设置成功后，其他线程的put操作和cas操作都将失效
                binCount = 1;
                Node<K, V> = null;
                try {
                    if ((val = mappingFunction.apply(key)))
                    node = new Node<K, V>(h, key, val, null);
                } finally {
                    setTabAt(tab, i, node);
                }
            }
        }
        // 重新设置好桶头后，再释放监视器r
        // ...
    }
}
```

## **2.4 构造函数**

ConcurrentHashMap采用了懒加载的方式，构造器中并未初始化底层数组，而是将这个过程放到了第一次putVal中

在HashMap和ConcurrentHashMap中都带有传入初始长度的构造器，HashMap通过threshold实现，而ConcurrentHashMap使用了sizeCtl进行实现（sizeCtl > 0时，当做threshold）

```java
public ConcurrentHashMap() {

}

public ConcurrentHashMap(int initialCapacity) {
    if (initialCapacity < 0)
        throw new IllegalArgumentException();
    int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY : tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
    this.sizeCtl = cap;
}
```

cap的计算与HashMap都使用tableSizeFor方法将传参转化为2^n大小，不过ConcurrentHashMap**比HashMap多做了实际长度计算的优化**：

```java
public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
        throw new IllegalArgumentException();
    if (initialCapacity < concurrencyLevel)   // Use at least as many bins
        initialCapacity = concurrencyLevel;   // as estimated threads
    long size = (long)(1.0 + (long)initialCapacity / loadFactor);
    int cap = (size >= (long)MAXIMUM_CAPACITY) ?
        MAXIMUM_CAPACITY : tableSizeFor((int)size);
    this.sizeCtl = cap;
}
```

    val = 1 + initialCapacity / 0.75f （提升传参大小的4/3倍）

通过上述公式，可以设置合理的初始化容量，防止初始化后的第一次扩容过早触发的问题

但是，concurrentHashMap的单参数构造器并没有使用0.75，而是使用了0.66

    val = 1 + (initialCapacity + initialCapacity >>> 1)
        = 1 + initialCapacity / 0.66f （提升为传参大小的3/2倍）

# **3. 关键行为**

## **3.1 ConcurrentHashMap和HashMap的扰乱函数**

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
    // 0x7fffffff = 0111 1111 1111 1111 1111 1111 1111 1111 1111，即除了最高位为0，其他位为1的二进制数，可以屏蔽掉hash的符号，变为31位的正数
    return (h ^ (h >>> 16)) & HASH_BITS;
}
```

不同于HashMap，spread方法会使用**HASH_BITS将符号位进行屏蔽**，只使用31位，负值的hash在concurrentHashMap有特殊含义，桶的头结点可能为负值
- 当桶头的hash为正数时，表示该hash桶为正常的链表结构
- 当桶头的hash为负数时，见上2.1静态变量

这也意味桶头的hash可能在某一阶段中，会发生结构变化：
- ForwardingNode（-1）：移动过的节点，用于扩容和迁移数据，不会保存真正的内容
- TreeBin（-2）：树化结点，会保存真正的内容
- ReservationNode（-3）：临时节点

因此，通过HASH_BITS屏蔽掉符号位，hash再处理后只会是正数，**以防止key.hashCode()返回-1/-2/-3，这样这三个负数才可以作为桶头的状态**

## **3.2 putVal**

### **HashMap的key和value可以为空吗？**
> [ConcurrentHashMap的key value不能为null，map可以？](https://www.cnblogs.com/fanguangdexiaoyuer/p/12335921.html)
> [Java中的ConcurrentHashMap中为什么不能存储null?](https://www.zhihu.com/question/379146959)

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
    // Retry-Loop与UnSafe.getObjectVolatile结合，刷新桶数据
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

# JDK-BUG
- [ConcurrentHashMap不同构造器使用不同负载因子](https://bugs.openjdk.java.net/browse/JDK-8202422)

# 参考
- [无锁HASHMAP的原理与实现](https://coolshell.cn/articles/9703.html)
- [ConcurrentHashMap1.8 - 扩容详解](https://blog.csdn.net/ZOKEKAI/article/details/90051567)
- [ConcurrentHashMap 1.7和1.8的区别](https://blog.csdn.net/u013374645/article/details/88700927)
- [ConcurrentHashMap的key value不能为null，map可以？](https://www.cnblogs.com/fanguangdexiaoyuer/p/12335921.html)
- [Java中的ConcurrentHashMap中为什么不能存储null?](https://www.zhihu.com/question/379146959)

# 重点参考
- [ConcurrentHashMap，Hash算法优化、位运算揭秘](https://www.cnblogs.com/grey-wolf/p/13069173.html)
- [ConcurrentHashMap的Unsafe操作](https://blog.csdn.net/js_tengzi/article/details/91358268)
- [ConcurrentHashMap(一)：常量，成员变量，静态代码块，内部类，spread函数，tabAt函数等详解](https://blog.csdn.net/lxsxkf/article/details/109161450)