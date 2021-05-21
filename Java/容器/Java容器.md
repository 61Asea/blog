# Java容器

## **1. 容器分类**

    容器主要包括Collection和Map两种，Colletion存储着对象的集合，而Map存储着键值对（两个对象）的映射表

## **1.1 Collection接口**

![Collection](http://8.135.101.145/upload/2021/05/Collection-cfcec07fec08420685a21e19c3d49338.png)

Collection接口提供集合框架基本的入口，包括了：
- 增：add(E)、addAll(Collection<? extends E>)
- 删：remove(Object)、removeAll(Colleciton<?>)
- 查：contains(Object)、containsAll(Collection<?>)、iterator()、size()、isEmpty()

```java
public interface Collection<E> extends Iterable<E> {
    // 返回集合中元素的数量，如果元素长度超过Integer.MAX_VALUE，则返回Integer.MAX_VALUE
    int size();

    // 集合元素长度是否为0
    boolean isEmpty();

    // 当且仅当此集合包含至少一个元素e，使得 o == null ? e == null : o.equals(e) 返回true
    // 注意，必须时重写equals和hashcode方法
    boolean contains(Object o);

    // 如果集合包含指定集合中的所有元素，返回true
    boolean containsAll(Colletion<?> c);

    // 返回该集合的迭代器，对于元素的返回顺序没有保证
    Iterator<E> iterator();

    // 返回集合对应类型的数组，如果集合对元素顺序有保证，此方法必须返回同样的顺序
    // toArray会产生内存复制(System.arraycopy)，效率低
    Object[] toArray();

    // 返回集合指定T类型转换的数组
    // 如果数组a的空闲元素元素多于此集合，则紧跟在集合结束之后的数组中的元素将设置为null
    <T> T[] toArray(T[] a);

    // 如果添加成功，返回true
    // 如果集合不允许重复的元素，返回false
    // 如果集合拒绝添加某个特定元素，而不是因为它已经包含该元素，则必须抛出异常
    boolean add(E e);

    // 添加指定集合的全部元素到本集合中
    boolean addAll(Collection<?> c);

    // 从集合中删除指定元素的单个实例e，o == null ? e == null : o.equals(e)
    boolean remove(Object o);

    // 删除集合中满足给定集合的所有元素，注意，是所有元素
    boolean removeAll(Collection<?> c);

    // 保留此集合中包含在指定集合中的元素，换句话说，从本集合中删除指定集合不包含的所有元素
    boolean retainAll(Collection<?> c);

    // 删除集合中的所有元素
    void clear();
}
```

在集合框架中，集合主要有三大类，分别为：Set、List和Queue

### **1.1.1 Set**

### **1.1.2 List**

List，又称为列表，根据内存的分布情况，底层实现可以分为数组和链表

数组在物理内存分布上为连续的，每个元素占用的内存相同，可以根据索引下标快速定位到具体的元素，即**支持随机访问**。但是添加和删除的效率低。

链表在物理内存分布上是不连续的，通过每个元素中的指针联系在一起，可以**快速删除链表头和尾元素**。但是不支持随机访问，只能从第一个元素往下遍历。

1. ArrayList（数组）

    基于动态数组实现，支持随机访问

    > [Java容器：ArrayList](http://8.135.101.145/arraylist)

2. Vector

    和ArrayList类似，但它是线程安全的

    > [](http://8.135.101.145/)

3. LinkedList（链表）

    基于双向链表实现，只能顺序访问，但是可以快速地在链表中间插入和删除元素
    
    不仅如此，LinkedList还可以用作栈、队列和双向队列

    > [](http://8.135.101.145/linkedlist)

4. Stack（基于数组实现的栈）

5. CopyOnWriteArrayList（写时复制数组）

### **1.1.3 Queue**

1. PriorityQueue

2. PriorityBlockingQueue

3. ScheduledThreadPool.DelayedWorkQueue

4. ArrayBlockingQueue

5. LinkedBlockingQueue

6. SynchronousQueue

## **1.2 Map接口**

Map，字面意思上为映射，也可称为字典，指的是存放**一组键值对**的结构，通过给定的键，可以根据**某些规则**找到键所对应的键值对，得到值

    所以从思想上来讲，数组就是一种Map，它的规则为：Entry<整型，值类型>，K与数组下标相等
    
Map接口上定义了映射的常见的put/get/remove/compute等行为，最重要的是定义了映射应有的四种结构：
- 键值对Entry，类型为：Entry<K, V>
- 键集合keySet，**键不可重复**，类型为：Set<K>
- 值集合values，**值可重复**，类型为：Collection<V>
- 键值对集合entrySet，类型为：Set<Entry<K, V>>

```java
public interface Map<K, V> {
    // Map.Entry
    interface Entry<K, V> {
        K getKey();

        V getValue();

        V setValue();
    }

    Set<K> keySet();

    Collection<V> values();

    Set<Entry <K, V>> entrySet();
}
```

总结：

Map规定的是键与值映射关系，并以键值对集合的方式进行存储。如何**通过某种规则**，用指定键从键值对集合中查找到合适的键值对，才是Map子类实现上需要关注的

Map与Hash息息相关，但是他们不是相等于的关系，Hash只是Map的一种实现方式，具体的实现有HashMap。从AbstractMap中我们可以更确定这个思想，AbstractMap的get方法，会将整个键值对集合从头到尾进行遍历，当发现遍历到某个键值对的键与给定的键相等，则返回该键值对的值

HashMap则对应的制定了映射规则：
    
    K的哈希值，与数组下标相等

Map可以通过多种方式/结构来实现，其中一种方式就是哈希表。Hash也可以通过多种方式/结构实现

    其他的方式还有：树（TreeMap）、链表+链表/红黑树实现的哈希表（LinkedHashMap）、数组+链表/红黑树实现的哈希表（HashMap）、跳表（ConcurrentSkipListMap）等等

## **AbstractMap**

AbstractMap实现Map的大部分方法，为各种不同类型的映射实现提供了基本实现

它定义了抽象映射的成员变量：键集合Set<K> keySet、值集合Collection<V> values，并通过AbstractSet和AbstractCollection匿名实现

```java
public abstract class AbstractMap<K, V> implements Map<K, V> {
    protected AbstractMap() {}

    transient Set<K> keySet;

    transient Collection<V> values;

    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            // AbstractMap定义了匿名类，需要调用具体的entrySet
            ks = new AbstractSet<K>() {
                public Iterator<K> iterator() {
                    return new Iterator<K>() {
                        // 具体的entrySet的迭代器，不同的Map子类都有自己的EntrySet类
                        private Iterator<Entry<K, V>> i = entrySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public K next() {
                            return i.next().getKey();
                        }

                        public void remove() {
                            i.remove();
                        }
                    };
                }

                // 通过Map具体实现来返回
                // 在AbstractMap为返回entrySet的长度
                // HashMap/TreeMap都使用了size成员进行缓存
                public int size() {
                    return AbstractMap.this.size();
                }

                public boolean isEmpty() {
                    return AbstractMap.this.isEmpty();
                }

                public void clear() {
                    // 调用了entrySet().clear()方法
                    // 在HashMap中，将clear方法放在了Map接口实现层
                    AbstractMap.this.clear();
                }

                public boolean contains(Object k) {
                    // 这里会通过迭代器，从头往后遍历
                    // HashMap: 通过关键方法getNode(int hash, Object key)，计算hash获得
                    // TreeMap: 从根节点往下遍历
                    return AbstractMap.this.containsKey(k);
                }
            };
            keySet = ks;
        }
        return ks;
    }

    public Collection<V> values() {
        Collection<V> vals = values;
        if (vals == null) {
            vals = new AbstractCollection<V>() {
                public Iterator<V> iterator() {
                    return new Iterator<V>() {
                        private Iterator<K, V> i = entrySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public V next() {
                            // i.next()获得的是Entry<K, V>
                            return i.next().getValue();
                        }

                        public void remove() {
                            i.remove();
                        }
                    };
                }

                public int size() {
                    return AbstractMap.this.size();
                }

                public boolean isEmpty() {
                    return AbstractMap.this.isEmpty();
                }

                public void clear() {
                    AbstractMap.this.clear();
                }

                public boolean contains(Object v) {
                    return AbstractMap.this.containsKey(v);
                }         
            };
            values = vals;
        }
        return vals;
    }

    // 最核心的抽象方法!!!!!
    public abstract Set<Entry<K, V>> entrySet();

    public static class SimpleEntry<K, V> implements Entry<K, V>, java.io.Serializable {
        private final K key;

        private V value;

        public SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        // ..

        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }
    }

    // 子类继承后重写实现
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    // get/containsKey/containsValue，都是通过entrySet的迭代器遍历

    public V get(Object key) {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        if (key == null) {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                if (e.getKey() == null)
                    return e.getValue();
            }
        } else {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                if (key.equals(e.getKey()))
                    return e.getValue();
            }
        }
        return null;
    }

    public boolean containsKey(Object key) {
        // 遍历entrySet
    }

    public boolean containsValue(Object value) {
        // 遍历entrySet
    }
}
```

## **NavigableMap**

SortedMap的子接口，实现该接口的映射容器将拥有SortedMap的有序特性，并额外提供了根据键值区间获取键值对的方法

实现该类的Map容器有：
- TreeMap：通过比较器实现有序特性，并使用二叉搜索树进行存储，兼顾查找性能
- ConcurrentSkipListMap：使用链表存储数据，并在链表上添加索引，形成跳表

```java
public interface NavigableMap<K, V> extends SortedMap<K, V> {
    // 有序排列中，获取给定key的上一个键值对
    Map.Entry<K, V> lowerEntry(K key);

    // 有序排列中，获取给定key的下一个键值对
    Map.Entry<K, V> higherEntry(K key);

    // 根据给定的键（可以不存在，在相邻键区间中），获得相邻键值最大的键值对
    Map.Entry<K, V> ceilingEntry(K key);

    // 根据给定的键（可以不存在，在相邻键区间中），获得相邻键值最小的键值对
    Map.Entry<K, V> floorEntry(K key);
}
```

## **1.2.1 HashMap**

底层实现：数组 + 链表/红黑树

通过Hash算法制定了查询规则，可以通过给定的键的Hash值，快速地定位到键值对处于数组的下标，获得相对于的桶。再对桶进行往下遍历，查找相等键的键值对，得到值

通过该规则，在哈希冲突不严重的情况下，查找效率为O(1)

## **1.2.2 TreeMap**

底层实现：红黑树

通过二叉树的结构制定查询规则，从二叉树root节点开始往下查找，查找相等键的键值对，得到值

TreeMap带来更多的好处，是因为其插入节点时，会根据Comparator比较大小，在合适的位置进行插入。并且其提供了在相邻Key范围之内，向上/下取值的功能，这在某些功能开发上非常实用

查找效率为O(logN)

## **1.2.3 LinkedHashMap**

# 参考
- [Java容器](https://www.cyc2018.xyz/Java/Java%20%E5%AE%B9%E5%99%A8.html)