# **HashMap**

    入手HashMap，首先必须搞清楚什么是Map，什么是Hash，以及Map和Hash(哈希表)的关系
    
    Map可以通过多种方式/结构来实现，其中一种方式就是哈希表
    
    哈希表也可以通过多种方式/结构实现，其中一种方式就是数组实现

    HashMap，是采用数组实现的哈希表来实现的一种Map

> [Java容器-Map接口](https://asea-cch.life/archives/java容器#12-Map接口)

HashMap，通过哈希实现的映射关系容器，可以在给定Key上通过其hash值，快速定位到Key对应的键值对

# **1. 哈希表**

> [Hash(哈希表)](https://asea-cch.life/archives/hash)

HashMap对应的数据结构就是**哈希表**，哈希表一般基于数组实现，HashMap底层结构就是**Entry数组**

## **1.1 哈希码**

> [hashCode()简单分析](https://blog.csdn.net/changrj6/article/details/100043822)

```java
public class Object {
    /**
     * Returns a hash code value for the object. This method is
     * supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     */
    public native int hashCode();
}
```

原文注释中文翻译：返回一个对象的散列值，哈希表（例如HashMap）使用该方法转化关键字

    JDK8之前，默认返回随机数

    JDK8之后，通过和当前线程相关的一个随机数，加上三个确定值，运用Marsaglia’s xorshift scheme随机数算法得到一个随机数

> [Java Object.hashCode()返回的是对象内存地址？](https://www.jianshu.com/p/be943b4958f4)

## **1.2 哈希函数**

    哈希化：通过哈希函数，将关键字的哈希码转换为数组下标

HashMap的哈希函数为：

    (数组容量 - 1) & hash

不是取模操作，而是通过“与”操作，所以数组的容量最好为2的n次幂，这样在数组容量-1时，可以获得二进制位上全是1的掩码

事实上，当数组容量长度较短时，会发现上述的哈希函数对低位特征不明显的哈希码作用较差（如低几位全是0），可能计算后的结果都为0，容易导致分布不均

HashMap会对hashCode进行再处理：

    (hash >>> 16) ^ hash

具体的案例可以看：

> [哈希表](https://asea-cch.life/archives/hash#1-哈希化)

## **1.3 哈希冲突**

    好的哈希函数可以均匀地将关键字哈希化到各个数组下标，单这个过程本质还是将大范围值转换为小范围值，哈希冲突总是不可避免的

当遇到**哈希冲突**问题时，可以通过两种思路解决：
- 开放地址法
- 链地址法（拉链法）

HashMap在实现上采用了拉链法，在JDK8之后还加入了红黑树，以链表过满时，导致的桶链表**查找效率低下**

    当某个桶的长度大于8时候，将链表结构变为红黑树结构，提升效率

装填因子一定程度上反映了**数组容量的表达意义**。因为采用了拉链法，**装填因子就是平均桶链表的长度**

    装填因子 = 数据项个数 / 数组容量

    平均链表长度 = 数据项个数 / 桶个数

    所以，装填因子 = 平均链表长度

如果装填因子过大，往往说明数组容量已经太小了，能表达的含义太少，所以我们需要关注**数组的扩展**

扩展数组容量可以降低装填因子的大小，我们应该找到一个HashMap的装载因子的舒适工作大小

    装填因子不是越小越好，如果过小，会导致频繁触发扩容，扩容是一个高成本操作；如果过大，会导致链表/红黑树查找效率低下

## **1.4 红黑树还是链表？**

内容太多，专门开了一节，讲一下元素什么时候从桶链表变成红黑树

关于红黑树的数据结构介绍：

> [红黑树](https://asea-cch.life/archives/redblacktree)

```java
// 桶链表变成红黑树结点的长度
static final int TREEIFY_THRESHOLD = 8;

// 红黑树退化成链表的长度
static final int UNTREEIFY_THRESHOLD = 6；

// 转变红黑树前有个判断：在容量小于64前，直接扩容数组
static final int MIN_TREEIFY_CAPACITY = 64;
```

### **1.4.1 TreeNode分析**

详情请见上面的红黑树文章

### **1.4.2 转换为红黑树容器**

```java
// 因为树节点的大小是常规节点的两倍，所以我们仅在容器包含足够的节点的时候再使用他们（TREEIFY_THRESHOLD = 8），当长度变为6时，再转换回普通的桶链表。在使用分布良好的哈希码，转化为红黑树容器的情况将极少，理想情况下遵循泊松分布

/**
* Because TreeNodes are about twice the size of regular nodes, we
* use them only when bins contain enough nodes to warrant use
* (see TREEIFY_THRESHOLD). And when they become too small (due to
* removal or resizing) they are converted back to plain bins.  In
* usages with well-distributed user hashCodes, tree bins are
* rarely used.  Ideally, under random hashCodes, the frequency of
* nodes in bins follows a Poisson distribution
* (http://en.wikipedia.org/wiki/Poisson_distribution) with a
* parameter of about 0.5 on average for the default resizing
* threshold of 0.75, although with a large variance because of
* resizing granularity. Ignoring variance, the expected
* occurrences of list size k are (exp(-0.5) * pow(0.5, k) /
* factorial(k)). The first values are:
*
* 0:    0.60653066
* 1:    0.30326533
* 2:    0.07581633
* 3:    0.01263606
* 4:    0.00157952
* 5:    0.00015795
* 6:    0.00001316
* 7:    0.00000094
* 8:    0.00000006
* more: less than 1 in ten million
*/
```

当桶链表的节点达到8个时，put第九个的时候，**可能会触发**桶链表变成红黑树的过程

```java
final void treeifyBin(Node<K,V>[] tab, int hash) {
    // n为底层数组的容量，index为hash对应的桶下标，e为对应桶头结点
    int n, index; Node<K,V> e;
    if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
        // 优先考虑resize
        resize();
    else if ((e = tab[index = (n - 1) & hash]) != null) {
        TreeNode<K, V> hd = null, tl = null;
    }
}
```

但是这里为什么提的是**可能**呢，因为在转化为红黑树的方法中，还有一段关于MIN_TREEIFY_CAPACITY（能转化桶为红黑树的最小数组容量）的判断，如果当前数组容量并没有达到MIN_TREEIFY_CAPACITY，则**优先考虑扩容**

    因为如果桶的数量过少，又发生了严重的hash碰撞，那么根本问题是数组能表达的意义太少了（桶数量过少），所以此时树化意义不大，会引入更大的内存空间浪费，优先考虑扩容


### **1.4.3 退化为链表**

在扩容操作中，如果某个桶的节点数量少于UNTREEIFY_THRESHOLD时，会退化为链表

# **2. 基础设施**

包括了各种迭代器相关的类，Entry键值对，桶链表结点，桶红黑树结点

HashMap中对于键值对的定义为：
- Node（链表节点）
- TreeNode(红黑树节点)

### **2.1 Node链表节点**

```java
public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {
    static class Node<K, V> implemetns Map.Entry<K, V> {
        // 关键字的哈希码
        final int hash;
        // 键（关键字）
        final K key;
        // 值
        V value;

        // 拉链法，链表下个节点的引用
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K getKey() {
            return key;
        }

        public final V getValue() {
            return value;
        }

        // Entry的哈希值计算，将Key的哈希值和Value的哈希值进行异或操作
        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        // 不能修改key，只能设置value
        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {
            // 引用类型，==默认只比较地址引用
            if (o == this)
                return this;

            if (o instanceof Map.Entry) {
                // key和value的值都相等，才是真的相等
                 Map.Entry<?, ?> e (Map.Entry<?, ?>) o;
                 if (Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue()))
                    return true;
            }
            return false;
        }
    }
}
```

## **2.2 TreeNode红黑树节点**

见1.4.1

需要注意Entry的equals方法，会调用key的equals方法和value的equals方法

```java
// Objects.equals方法
public static boolean equals(Object a, Object b) {
    return (a == b) || (a != null && a.equals(b));
}
```

如果使用自定义对象作为key时，且认为对象的某些成员属性相等则为相同对象，必须重写Key对象的hashCode和equals方法

## **2.3 迭代器模式**

不管是迭代键集合、值集合还是键值对集合，使用的迭代器都继承了同一个抽象父类**HashIterator**

运用了迭代器模式，我们可以根据结构定义entrySet的遍历方式，无需关注容器的底层结构实现

```java
abstract class HashIterator {
    Node<K, V> next;
    Node<K, V> current;
    // 在迭代器初始化时，会记下一开始的modCount，用于“快速失败机制”
    int expectedModCount;
    int index;

    HashIterator() {
        expectedModCount = modCount;
        Node<K, V>[] t = table;
        current = next = null;
        index = 0;
        if (t != null && size > 0) {
            // 将next引用指向第一个不为null的桶
            do {} while (index < t.length && (next = t[index++] == null));
        }
    }

    public final boolean hasNext() {
        return next != null;
    }

    final Node<K, V> nextNode() {
        Node<K, V>[] t;
        Node<K, V> e = next;
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        if (e == null)
            throw new NoSuchElementException();

        // 将current置为next，再判断current的next是否为空，为空的话则用do-while将引用指向下一个不为空的桶
        if ((next = (current = e).next) == null && (t = table) != null) {
            do {} while (index < t.length && (next = t[index++]) == null);
        }
        return e;
    }

    public final void remove() {
        Node<K, V> p = current;
        if (p == null)
            throw new IllegalStateException();
        // 快速失败，可以发现直接调用迭代器的remove不会造成modCount的变动，当单线程需要在一个迭代里操作容器时，可以使用迭代器操作
        if (modCount != expectedModCount)
            throw new ConcurrentModificationException();
        current = null;
        K key = p.key;
        removeNode(hash(key), key, null, false, false);
        // 上面的操作会修改modCount，此处需要修正一下
        expectedModeCount = modCount;
    }
}
```

### **EntryIterator**

```java
final class EntryIterator extends HashIterator implements Iterator<Map.Entry<K, V>> {
    public final Map.Entry<K, V> next() {
        return nextNode();
    }
}
```

### **KeyIterator**

```java
final class KeyIterator extends HashIterator implements Iterator<Map.Entry<K, v>> {
    public final Map.Entry<K, V> next() {
        return nextNode().key;
    }
}
```

### **ValueIterator**

```java
final class ValueIterator extends HashIterator implements Iterator<Map.Entry<K, V>> {
    public final Map.Entry<K, V> next() {
        return nextNode().value;
    }
}
```

### **2.3.1 EntrySet**

实现了Map接口获取entrySet的方法，可以从Set的泛型看出返回的Entry需要满足Map.Entry接口的规范：

```java
public Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> es;
    return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
}
```

在Map接口和AbstractMap抽象类中，我们发现很多对于哈希表数据结构的操作，都是通过对EntrySet的操作进行的。熟悉EntrySet的基本容器操作，就是理解HashMap的容器操作：

```java
final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    // 返回的是HashMap的成员变量
    public final int size() {
        return size;
    }

    // HashMap.clear，会直接将数组的每个元素都置空
    public final void clear() {
        HashMap.this.clear();
    }

    // 迭代器模式，返回Entry的迭代器，后续讲
    public final Iterator<Map.Entry<K, V>> iterator() {
        return new EntryIterator();
    }

    public final boolean contains(Object o) {
        if (!(o instanceof Map.Entry))
            return false;
        Map.Entry<?,?> e = (Map.Entry<?,?>) o;
        Object key = e.getKey();
        // 将o的关键字哈希码进一步处理，并通过哈希函数，从底层数组中查找节点
        // 调用外部类的getNode方法
        Node<K, V> candidate = getNode(hash(key), key);
        // 没有找到该节点，直接false；找到了该节点，对比一下是不是键跟值都相等
        return candidate != null && candidate.eqauls(e);
    }

    public final boolean remove(Object o) {
        if (o instanceof Map.Entry) {
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            Object value = e.getValue();
            // 调用外部类的removeNode方法
            return removeNode(hash(key), key, value, true, true) != null;
        }
        return false;
    }

    // 可分割迭代器，为了并行遍历元素设计的一个迭代器，使用时可以将数据流平分成多份，起多线程去跑，提升效率
    public final Spliterator<Map.Entry<K, V>> spliterator() {
        return new EntrySpliterator<>();
    }

    public final void forEach(Consumer<? super Map.Entry<K, V>> action) {
        Node<K, V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            // 遍历一个个的桶
            for (int i = 0; i < tab.length; ++i) {
                // 从桶的链表头往下遍历
                for (Node<K, V> e = tab[i]; e != null; e = e.next)
                    action.accept(e);
            }
        
            // modCount防止并发修改
            // 疑问：执行完全部accept后才对比，就算抛出异常，全部数据也乱了把？如果有多线程场景下，还是得老老实实使用并发安全的容器
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }
}
```

EntrySet其实本身并没有存储结构，是对底层数组的一层封装，最重要的是定义了entrySet的遍历方式，而更具体的细节，在于外部类的方法，即对**哈希化的运用**

### **2.3.2 KeySet**

基本与EntrySet一致，主要是迭代时，传入的是entry的关键字

```java
final class KeySet extends AbstractSet<K> {
    // ...
    public final Iterator<K> iterator() {
        return new KeyIterator();
    }

    public final void forEach(Consumer<? super K> action) {
        // 一致的循环

        // 注意此处使用的e.key
        action.accept(e.key);
    }
}
```

### **2.3.3 Values**

values可以有重复的，继承的是抽象集合类，主要区别是迭代时，传入entry的值

```java
final class Values extends AbstractCollection<V> {
    public final Iterator<V> iterator() {
        return new ValueIterator();
    }

    public final void forEach(Consumer<? super V> action) {
        // ...
        action.accept(e.value);
    }
}
```

## **2.4 默认参数和静态方法**

```java
public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, Serializable {
    // 数组默认初始容量16
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;

    // 数组最大容量2的30次方
    static final int MAXIMUM_CAPACITY = 1 <<< 30;

    // 默认的最大负载因子
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // 底层数组
    transient Node<K, V> table;

    transient Set<Map.Entry<K, V>> entrySet;

    // 键值对的个数
    transient int size;

    // 快速失效机制
    transient int modCount;

    // 触发下一次扩容的长度
    int threshold;

    final float loadFactor;

    public HashMap() {
        // 设置容器的负载因子默认值，0.75
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }
}
```

以上参数/成员变量涉及到数组的初始化问题，以及扩容的问题，数组的初始容量为16，触发下一次扩容的长度为12

# **3 关键方法**

从Map接口的实现入手，包括：

- get(K)

    哈希函数获取元素getNode

- put(K, V)

    设值putVal、扩容resize、桶链表插入（尾插法）、树化treeifyBin

- resize()

    扩容很重要，会介绍关于节点重哈希化的经典公式，还有头插法导致的扩容死循环问题

- remove(Object)

    删除节点removeNode(int hash, Object key, Object value, boolean matchValue, boolean movable)

## **3.1 get(Object key)**

在接口方法实现上调用了getNode(int hash, key)方法

```java
public V get(Object key) {
    Node<K, V> e;
    // 使用hash方法，将关键字的哈希码进一步处理
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}
``` 

getNode方法根据进一步处理过的哈希码，通过哈希函数哈希化得到数组下标

```java
final Node<K, V> getNode(int hash, Object key) {
    // tab为底层数组，first为桶的头结点，e为桶遍历时的节点，n为数组长度，k为e的键
    Node<K, V>[] tab; Node<K, V> first, e; int n; K k;

    // 哈希函数：(n - 1) & hash，计算关键字所在的桶
    if ((tab = table) != null && (n = tab.length) > 0 && (first = tab[(n - 1) & hash]) != null) {
        if (first.hash == hash && ((k = first.key) == key || (key != null && key.equals(k)) )
            // 如果头节点就是要查找的键值对，直接返回
            return first;
        
        if ((e = first.next) != null) {
            // 如果当前桶树化，则红黑树查找
            if (first instanceof TreeNode)
                return ((TreeNode<K, V>)first).getTreeNode(hash, key);
            do {
                if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
                    return e;
            } while((e = e.next) != null);
        }
    }
    return null;
}
```

如果是红黑树桶，则使用红黑树的查找，效率为O（logN）
如果是链表桶，则从头往下遍历，效率为O（N），平均遍历长度等于负载因子

## **3.2 put(K, V)**

在接口方法实现上调用了putVal方法，传入的hash是经过预处理的

```java
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, false);
}
```

分析putVal的代码，它涉及到了：哈希函数、**链表尾插**、树化操作和**扩容**

```java
final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
    Node<K, V>[] tab;
    // p表示该键值对所对应的桶节点，p从头节点开始往下变化
    Node<K, V> p;
    // n代表底层数组的容量，i代表哈希化后的数组下标
    int n, i;
    if ((tab = table) != null || (n = tab.length) == 0)
        // 未初始化，通过扩容，生成扩容后的底层数组并赋值给table
        n = (tab = resize()).length;
    
    // 哈希函数 (hash &(数组容量 - 1))
    if ((p = tab[i = (n - 1) & hash]) == null)
        // 创建链表头
        tab[i] = newNode(hash, key, value, null);
    else {
        // e若不为空，说明该关键字已有键值对存储，会对其进行覆盖操作
        // k是临时变量，记录桶链表下每个节点的关键字
        Node<K, V> e; K k;
        if (p.hash == hash && ((k = p.key) == key || key != null && key.equals(k)))
            // 桶链表的头节点就是关键字的键值对，则直接进行覆盖操作
            e = p;
        else if (p instanceof TreeNode)
            // 桶已经树化了
            e ((TreeNode<K, V>) p).putTreeVal(this, tab, hash, key, value);
        else {
            // 尾插法，从链表头往下遍历，若有找到关键字的键值对，则覆盖；否则，新创建一个节点，加入到链表尾中
            for (int binCount = 0; ; ++binCount) {
                if ((e = p.next) == null) {
                    // 已经遍历到最尾部，创建新的对象
                    p.next = newNode(hash, key, value, null);
                    if (binCount >= TREEIFY_THRESHOLD - 1)
                        // put完之后发现大于等于8，则尝试将桶树化
                        treeifyBin(tab, hash);
                    break;
                }

                if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
                    // 关键字已经有了键值对，直接赋值
                    break;
                
                // p变成下一个节点
                p = e;
            }
        }

        if (e != null) {
            // 关键字已经有了键值对，直接复制
            V oldValue = e.value;
            if (!onlyIfAbsent || oldValue == null)
                // onlyIfAbsent为true时，不覆盖
                e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
    }

    // 注意扩容的时机，如果是已经有值，进行覆盖的，不会扩容；只有新加入键值对节点，才会考虑扩容
    ++modCount;
    if (++size > threshold)
        // size自增长1后，与下次扩容长度做对比，threshold = 当前数组的容量 * 负载因子
        resize();
    afterNodeInsertion(evict);
    return null;
}
```

分析一下putVal的基本操作：

1. 没有初始化过底层数组，则先进行resize扩容

2. 根据**哈希函数哈希化，得到桶下标**

3. 如果没有桶元素，则当前键值对将作为桶链表的头，并跳转到第八步

4. 如果有桶元素，则先判断桶的头节点是否为给定的关键字，判断关键字是否已经有键值对，有的话则跳转到第八步去覆盖值；没有的话往下一步走

5. 判断**是否当前桶已经树化**，是的话则直接根据红黑树规则进行对比和插入（如果有叶子结点为关键字键值对，一样跳转到第八步进行覆盖操作）；否的话，往下一步走

6. 通过**尾插法**，对桶往下一直遍历，若找到存在已有键值对，则跳转到第八步等待覆盖；若无的话，将新结点插入到链表尾部，往第七步走

7. 如果发现当前的**桶长度大于等于8**时，尝试将桶树化，并往下一步走

8. 如果是已有键值对，则覆盖值，并返回旧值；反之，则判断是否应该扩容，扩容的依据在于**当前的size是否超过了threshold（threshold = size * 0.75）**

其中第二、六、七、八步为重点操作，涉及到了扩容、桶树化和红黑树插入，红黑树相关的内容在本文章的第一节有讲解到，接下来重点看下扩容方法

## **3.3 resize()**

扩容的场景：
- HashMap初始化后，第一次putVal，会通过**扩容进行初始化**。如果使用自定义负载因子和初始容量的构造器，则按照自定义进行扩容
- putVal后，发现当前**size > threshold**，根据装填因子和初始容量**后续扩容**

扩容会将数组容量变大，计算下一次扩容的长度（正常比容量小0.25倍），并将当前数组的数据转移到新的数组

    在JDK8之前，使用头插法插入元素，这可能导致多线程下，扩容和插入同时操作，导致扩容死循环

```java
final Node<K, V>[] resize() {
    Node<K, V>[] oldTab = table;
    // 如果旧容量为0，说明table还没有被初始化
    int oldCap = (oldTab == null) ? 0 : oldTab.length;
    // 一般是oldCap * DEFAULT_LOAD_FACTOR(0.75)
    int oldThr = threshold;
    int newCap, newThr = 0;

    if (oldCap > 0) {
        // 大于0，说明已经初始化过
        if (oldCap >= MAXIMUM_CAPACITY) {
            // 触发扩容长度调到2的31方，意味着不会再扩容
            threshold = Integet.MAX_VALUE;
            // 超过最大长度，不会再扩容
            return oldTab;
        }

        // 数组容量变为之前的两倍，并且旧的容量应大于16，才会将下次扩容的值调大
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY)
            // 16 12
            // 32 24
            // 64 48
            newThr = oldThr << 1;
    }
    // 使用传入初始容量的构造器
    else if (oldThr > 0)
        // oldCap = 0，还没被初始化
        // 新的数组容量为，传入HashMap构造器的(initialCapacity - 1)的二进制位全为1的值
        newCap = oldThr;
    else {
        // 没有被初始化过
        // 默认初始化长度为16
        newCap = DEFAULT_INITIAL_CAPACITY; 
        // 默认下次扩容长度为12
        newThr = (int) (DEFAULT_INITIAL_CAPACITY * DEFAULT_LOAD_FACTOR); 
    }

    if (newThr == 0) {
        // 使用特别的构造器导致
        float ft = (float) newCap * loadFactor;
        newThr = (newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ? (int) ft : Integer.MAX_VALUE);
    }
    threshold = newThr;

    // 转移旧的数组到新数组中
    @SuppressWarnings({"rawtypes", "unchecked"})
    Node<K, V>][] newTab = (Node<K, V>[]) new Node[newCap];
    // 设置当前的table为newTab
    table = newTab;
    if (oldTab != null) {
        // 迭代旧数组的每个桶
        for (int j = 0; j < oldCap; ++j) {
            // j下标元素对应的桶
            Node<K, V> e;
            if ((e = oldTab[j]) != null) {
                oldTab[j] = null;
                if (e.next == null)
                    // 桶只有一个节点
                    newTab[e.hash & (newCap - 1)] = e;
                else if (e instanceof TreeNode)
                    // 桶树化，尝试将树变小/退化为链表
                    (TreeNode<K, V>e).split(this, newTab, j, oldCap);
                else {
                    // 桶链表
                    Node<K, V> loHead = null, loTail = null;
                    Node<K, V> hiHead = null, hiTail = null;
                    Node<K, V> next;
                    do {
                        next = e.next;
                        // 将桶元素划分为两份
                        if ((e.hash & oldCap) == 0) {
                            if (loHead == null)
                                loHead = e;
                            else
                                loTail.next = e;
                            loTail = e;
                        }
                        else {
                            if (hiHead == null)
                                hiHead = e;
                            else
                                hiTail = e;
                            hiTail = e;
                        }
                    } while ((e = next) != null);
                    if (loTail != null) {
                        // 把在旧数组的桶的next引用置空，因为现在处于新数组桶的队尾
                        loTail.next = null;
                        newTab[j] = loHead;
                    }
                    if (hiTail != null) {
                        hiTail.next = null;
                        newTab[j + oldCap] = hiHead;
                    }
                }
            }
        }
    }
    return newTab;
}
```

通过loHead/loTail和hiHead/hiTail来划分桶全部节点在扩容后的重哈希化，重点在于(e.hash & oldCap == 0)的公式推导：

    如果扩容之后，元素仍然在旧索引位置，可以获得公式：
    (2oldCap - 1) & e.hash = (oldCap - 1) & e.hash
    =>
    2 (oldCap & e.hash) = oldCap & e.hash
    => 
    oldCap & e.hash = 0, 上述等式成立

所以使用该方法，可以快速将桶里的节点分成两份，一份是旧位置，一份是旧位置 + oldCap的位置

DEMO：

假设使用默认构造器的HashMap，当前数组情况为：
[{Entry1, Entry2, Entry3, Entry4}, {}, {}, ..., {}]，共13个桶数组

首先扩容为32个桶，并设置下一次扩容的桶数为12 * 2 = 24个桶：

[{}, {}, {}, {}, ..., {}]，共32个空桶的数组

假设3通过(hash & oldCap == 0)， Entry1和Entry3结果为0，Entry2和Entry4结果不为0：

LoHead = Entry1, LoTail = Entry3
HiHead = Entry2, HiTail = Entry4

[{Entry1, Entry3}(第1个桶), {}, {}, {}, ..., {Entry2, Entry4}（第17个桶）, {}]，共32个桶的数组

## **扩容和插入可能会导致的死循环问题**

## **3.4 remove(Object key)**

老规矩，先看一下接口实现

```java
public V remove(Object key) {
    Node<K, V> e;
    return (e = removeNode(hash(key), key, null, false, true)) == null ? null : e.value;
}
```

```java
public boolean remove(Object key, Object value) {
    Node<K, V> e;
    return (e = removeNode(hash(key), key, value, true, true)) != null;
}
```

也是通过一个私有方法removeNode来实现，同样老配方，先对关键字的哈希码进一步处理

    remove对节点的删除，会触发扩容、树退化吗？

```java
final Node<K, V> removeNode(int hash, Object key, Object value, boolean matchValue, boolean movable) {
    // p为最终要删除的节点，index为要删除的节点的桶下标
    Node<K, V>[] tab; Node<K, V> p; int n, index;

    // 哈希化得到下标
    if ((tab = table) != null && (n = tab.length) > 0 && (p = tab[index = (n - 1) & hash]) != null) {
        // node是桶头节点，e用于迭代器使用，可能为null
        Node<K, V> node = null, e; K k; V v;

        // tmd，怎么不复用getNode ...
        if (p.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))
            node = p;
        else if ((e = p.next) != null) {
            if (p instanceof TreeNode)
                node = ((Tree<K, V>)p).getTreeNode(hash, key);
            else {
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key ||
                            (key != null && key.equals(k)))) {
                        node = e;
                        break;
                    }
                    // p为删除节点的上一个节点
                    p = e;
                } while ((e = e.next) != null)
            }
        }

        // 根据不同的外层接口来决定要不要匹配值
        if (node != null && (!matchValue || (v = node.value) == value || (value != null && value.equals(v)))) {
            // 关键来了，调用了树的删除节点，该方法在节点个数处于[2, 6]区间时，可能会触发退化链表，关键还要看树的结构
            if (node instanceof TreeNode)
                // removeTreeNode调用untreeify进行退化
                ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
            else if (node == p)
                // 节点跟上一个节点相同，说明是链表头，直接将链表头设置为下一个节点
                tab[index] = node.next;
            else
                // 上一个节点的next指向当前节点的下一个
                p.next = node.next;
            ++modCount;
            --size;
            afterNodeRemoval(node);
            return node;
        }
    }
    return null;
}
```

# 参考
- [hashCode()简单分析](https://blog.csdn.net/changrj6/article/details/100043822)
- [Java Object.hashCode()返回的是对象内存地址？](https://www.jianshu.com/p/be943b4958f4)

- [对象的==和equals](https://www.cnblogs.com/pu20065226/p/8530010.html)
- [cyc2018-HashMap](https://www.cyc2018.xyz/Java/Java%20%E5%AE%B9%E5%99%A8.html#hashmap)

- [HashMap中插入数据用的头插法还是尾插法](https://blog.csdn.net/u010257931/article/details/103143627)

# 重点参考
- [JDK1.8以后的hashmap为什么在链表长度为8的时候变为红黑树](https://blog.csdn.net/danxiaodeshitou/article/details/108175535)
- [关于HashMap底层实现的一些理解](https://blog.csdn.net/opt1997/article/details/104783005)
- [HashMap扩容时的rehash方法中(e.hash & oldCap) == 0算法推导](https://blog.csdn.net/u010425839/article/details/106620440/)