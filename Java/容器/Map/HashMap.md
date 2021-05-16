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

内容太多，专门开了一节

```java
// 桶链表变成红黑树结点的长度
static final int TREEIFY_THRESHOLD = 8;

// 红黑树退化成链表的长度
static final int UNTREEIFY_THRESHOLD = 6；

// 转变红黑树前有个判断：在容量小于64前，直接扩容数组
static final int MIN_TREEIFY_CAPACITY = 64;
```

> [红黑树](https://asea-cch.life/archives/)

# **2. 源码分析**

## **2.1 基本类**

### **2.1.1 Entry**

HashMap中对于键值对的定义为：
- Node（链表节点）
- TreeNode(红黑树节点)

### **Node链表节点**

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

### **TreeNode红黑树节点**

```java

```

需要注意Entry的equals方法，会调用key的equals方法和value的equals方法

```java
// Objects.equals方法
public static boolean equals(Object a, Object b) {
    return (a == b) || (a != null && a.equals(b));
}
```

如果使用自定义对象作为key时，且认为对象的某些成员属性相等则为相同对象，必须重写Key对象的hashCode和equals方法


<!-- 
挪到getNode方法讲

在阿里开发规范中提及到HashMap使用对象作为Key的一些约定：

    如果自定义对象作为作为Map的键，那么必须重写hashCode和eqauls方法 -->

## **2.1.2 迭代器模式**

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

## **2.1.3 EntrySet**

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

## **2.1.4 KeySet**

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

## **2.1.5 Values**

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

## **2.2 默认参数和静态方法**

```java
public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, Serializable {
    // 数组默认初始容量16
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;

    // 数组最大容量2的30次方
    static final int MAXIMUM_CAPACITY = 1 <<< 30;

    // 默认的最大负载因子
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // 红黑树的一些静态参数
}
```

涉及到了

# 参考
- [hashCode()简单分析](https://blog.csdn.net/changrj6/article/details/100043822)
- [Java Object.hashCode()返回的是对象内存地址？](https://www.jianshu.com/p/be943b4958f4)

- [对象的==和equals](https://www.cnblogs.com/pu20065226/p/8530010.html)
- [cyc2018-HashMap](https://www.cyc2018.xyz/Java/Java%20%E5%AE%B9%E5%99%A8.html#hashmap)

# 重点参考
- [JDK1.8以后的hashmap为什么在链表长度为8的时候变为红黑树](https://blog.csdn.net/danxiaodeshitou/article/details/108175535)