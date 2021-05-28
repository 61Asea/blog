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
// 31个1，用于将一个负数变成正数，但不是取绝对值（负数以原码的补码形式表达）
static final int HASH_BITS = 0x7fffffff;

// 运行的CPU数
static final int NCPU = Runtime.getRuntime().availableProcessors();
```

以-1作为例子，**负数在计算机的表达形式为原码的补码**，推导HASH_BITS不是取负数绝对值：

-1原码：

    1000 0000 0000 0000 0000 0000 0000 0001

-1反码：

    1111 1111 1111 1111 1111 1111 1111 1110

-1补码：

    1111 1111 1111 1111 1111 1111 1111 1111

通过HASH_BITS屏蔽符号位：

    0111 1111 1111 1111 1111 1111 1111 1111

最后的结果是(2^31 - 1)，而不是1，所以HASH_BITS只是屏蔽符号位，并不是取绝对值

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

### **2.3.5 CounterCell**

使用@sun.misc.Contended，表示该字段会单独占用一个缓存行，主要适用于频繁写的共享数据上

因为CounterCell属于一个变动频繁的数据，其字段value是volatile的，这意味着cpu之间对于value的变动，都会锁总线，使用MESI协议做到一致性

但如果其他与CounterCell无关的数据也跟它在同一个缓存行，也会因为它的影响而去读取主存的最新数据（注意，不是ROF）

> [硬件模型] (https://asea-cch.life/achrives/硬件模型)

```java
@sun.misc.Contended static final class CounterCell {
    // 关键的volatile，如果有其他数据跟当前对象处于同一缓存行，会因为volatile而频繁地去刷新缓存
    volatile long value;

    CounterCell(long x) {
        value = x;
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

## **3.2 get获值**

大体逻辑与HashMap没差，主要的细节差别体现在：

- ConcurrentHashMap不能get(null)，因为扰乱函数没有对null做特别处理（HashMap在key为null时返回0），会**抛出空指针异常**

- ConcurrentHashMap是线程安全容器，get操作没有上锁（效率操作）

    因为获取操作本质上并未修改数据，所以通过volatile的方式可以兼顾效率，并保证最终一致性（为了实时一致，去与写操作互斥，读性能肯定变差，并导致写性能变差）

    通过getObjectVolatile获取数组的桶元素，这个操作可以获得桶的最新值
    通过Node节点的val和next字段为volatile，做到get返回的最终一致性

- ConcurrentHashMap的桶头节点hash可能会变为负数，分别代表ForwardingNode、TreeBin和临时节点，他们对于桶的遍历方式不同，所以各个节点类重写find方法实现多态

```java
public V get(Object key) {
    Node<K, V>[] tab; Node<K, V> e, p; int n, eh; K ek;
    int h = spread(key.hashCode());
    // tabAt，做volatile获取
    if ((tab = table) != null && (n = tab.length) > 0 && (e = tabAt(tab, (n - 1) & h)) != null)) {
        if ((eh = e.hash) == h) {
            if ((ek = e.key) == key || (ek != null && key.equals(ek)))
                return e.val;
        }
        else if (eh < 0)
            // 桶头节点为负数，三种不同桶节点的find多态
            return (p = e.find(h, key)) != null ? p.val : null;
        while ((e = e.next) != null) {
            if (e.hash == h && ((ek = e.key) == key || (ek != null && key.equals(ek))))
                return e.val;
        }
    }
    return null;
}
```

## **3.3 putVal设值**

### **HashMap的key和value可以为空吗？**
> [ConcurrentHashMap的key value不能为null，map可以？](https://www.cnblogs.com/fanguangdexiaoyuer/p/12335921.html)
> [Java中的ConcurrentHashMap中为什么不能存储null?](https://www.zhihu.com/question/379146959)

### **与HashMap的不同**

与HashMap对比，主要涉及了几个不同：

- ConcurrentHashMap不能放置key/value为null的键值对
- ConcurrentHashMap在第一次putVal时，才初始化底层数组
- ConcurrentHashMap可以感知到桶是否正在扩容，是的化，会参与到扩容过程，扩容完成后，再去设置键值对
- ConcurrentHashMap在初始化桶时，**尝试CAS做替换**，失败了则重新获取tab的数据
- ConcurrentHashMap将节点插入桶时，**使用synchronized将桶锁住**，这里不用CAS的原因，是因为可能出现的并发操作情况太多，如put/remove。CAS通过实现一般只能兼顾单种操作，直接synchronized大法好

### **与JDK7的ConcurrentHashMap的不同**

JDK7采用了Segment分段锁的方式尝试将锁的粒度变小，而JDK8直接以桶作为锁的粒度，粒度更小

### **方法源码分析**

```java
// 实现Map的接口方法
public V put(K key, V value) {
    return putVal(key, value, false);
}
```

```java
final V putVal(K key, V value, boolean onlyIfAbsent) {
    // key或value都不能为空（原因见上）
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
                        // 尾插法，遍历到链表尾，并且记录元素个数，后续做树化判断
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
                        // 设置为2，防止到下面的逻辑再次树化
                        binCount = 2;
                        // 树结点插入 ...
                    }
                }
            }
            if (binCount != 0) {
                if (binCount >= TREEIFY_THRESHOLD)
                    treeifyBin(tab, i);

                if (oldVal != null)
                    return oldValue;
                break;
            }
        }
        // 扩容逻辑！！
        addCount(1L, binCount);
        return null;
    }
}
```

putVal有四种情况:
1. Map的底层数组未初始化，调用initTable方法初始化（懒汉式）
2. 哈希化后得到的桶为null，则尝试CAS进行更新（单操作，CAS可以直接做到）
3. 如果Map正在扩容，则参与扩容，帮助transfer数据
4. 有桶，且未扩容，则获取桶头的监视器锁，进行互斥更新（多操作，CAS不好做）

## **3.4 addCount计数**

```java
private final void addCount(long x, int check)
```

在putVal、replaceNode（remove）、clear、compute、merge方法的收尾，都会调用addCount方法对当前计数

计数是扩容的前置操作，当check的值大于等于0时，会去检测下是否需要扩容。如果需要，再根据当前map的状态（是否在扩容、能否参与扩容），发起或参与扩容

    使用了类似LongAdder的思想
    
    当设置baseCount字段存在竞争时，将会使用counterCells进行分桶，再根据探针哈希定位到某个桶进行CAS设置操作，后续的计数将通过baseCount + 桶的数值进行返回

    这样可以将锁粒度减小，更进一步减少锁竞争

```java
private final void addCount(long x, int check) {
    CounterCell[] as; long b, s;
    // as已经初始化，说明之前已经存在竞争 / 对baseCount做CAS存在竞争
    if ((as = counterCells) != null || !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
        CounterCell a; long v; int m;
        // 对桶的操作是否有竞争
        boolean uncontended = true;
        // as未初始化 或 随机的桶为null 或 对桶做CAS时失败（有竞争）
        if (as == null || (m = as.length - 1) < 0 || (a = as[ThreadLocalRandom.getProbe() & m]) == null || !(uncontended = U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
            fullAddCount(x, uncontended);
            return;
        }
        if (check <= 1)
            return;
        // 计算当前整个容器的长度
        s = sumCount();
    }
    // 检查是否需要扩容
}
```

## **3.5 transfer**

transfer的前置方法是addCount，在上一节中我们介绍了addCount的基本操作，即计数

addCount还有下半部分逻辑，主要是用于检测是否需要扩容，并配合map的sizeCtl和transferIndex，来决定发起还是参与扩容

当sc为正数时，代表Map扩容的阈值；而当sc < -1时，代表Map正在扩容：

    (1111 1111 1111 1111) (0000 0000 0000 0010)

    高16位的数值rs代表本次扩容的标识戳，低16位的数值n代表共有n - 1个线程参与扩容

1. 发起扩容的那个线程，称为第一个线程，他会将当前长度生成的标识戳设置到sc的高16位，且是负数，所以sc会从正数变为负数

    sizeCtl = (rs <<< RESIZE_STAMP_SHIFT) + 2

2. 第一个线程如果结束扩容，会将sc设置为rs + 1

```java
private final void addCount(long x, int check) {
    // ...
    // 检查是否需要扩容
    if (check >= 0) {
        Node<K, V>[] tab, nt; int n, sc;
        // sizeCtl > 0代表扩容阈值
        while (s >= (long)(sc = sizeCtl) && (tab = table) != null && (n = tab.length) < MAXIMUM_CAPACITY) {
            int rs = resizeStamp(n);
            // sc < 0 => sc < -1，高16位表示扩容的标识戳，低16位表示有多少个线程正在参与扩容
            // 当前table数组正在扩容，会参与帮助扩容
            if (sc < 0) {
                // sc >>> RESIZE_STAMP_SHIFT，即sc的高16位，是扩容的标识戳。如果sc的高16位不等于rs，说明sizeCtl被其他线程修改了，当前的过程应该失效
                // sc == rs + 1，表示扩容结束，不再有线程进行扩容
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 || sc == rs + MAX_RESIZERS || (nt = nextTable) == null || transferIndex <= 0)
                    break;
            }
            // 没有在扩容的话，则发起扩容
            else if ()


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