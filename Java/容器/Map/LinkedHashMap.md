# LinkedHashMap

> [HashMap](https://asea-cch.life/archives/hashmap)

在上一篇文章中，介绍了Java对哈希表数据结构的实现HashMap。HashMap具有无序的特点，这对于需要通过**插入顺序**或**访问顺序**遍历的场景，将会十分不友好

LinkedHashMap，通过将HashMap与双向链表进行结合，它将所有的Entry结点链入到一个双向链表中来**保持迭代的顺序**。此外，该迭代的顺序既可以是默认实现的插入顺序，也可以是访问顺序，后者将可以很好地**支持LRU置换算法**

## **双向链表**

链表的结点通过继承HashMap.Node类，增加了前向和后向两个引用

```java
public class LinkedHashMap<K, V> extends HashMap<K, V> implements Map<K, V> {
    // 继承HashMap.Entry，新增了前向和后向引用
    static class Entry<K, V> extends HashMap.Node<K, V> {
        Entry<K, V> before, after;
        Entry(int hash, K key, V value， Node<K, V> next) {
            super(hash, key, value, next);
        }
    }

    // LinkedHashMap额外维护的双向链表
    transient LinkedHashMap.Entry<K, V> head;
    transient LinkedHashMap.Entry<K, V> tail;
}
```

通过重写HashMap的多个方法，保证在增/删/改/查四种类型的操作都能额外维护双向链表

    这也是为什么LinkedHashMap的操作花销要比HashMap大的原因

### **putVal操作(增/改)**

通过HashMap的putVal操作进行驱动，可以调用到LinkedHashMap重写的一些方法：

- newNode

    用于在插入新结点的时候，顺便将其链入到自行维护的双向链表的表尾

    ```java
    Node<K, V> newNode(int hash, K key, V value, Node<K, V> e) {
        LinkedHashMap.Entry<K, V> p = new LinkedHashMap.Entry<K, V>(hash, key, value, e);
        // 将新的结点加入到双向链表的表尾
        linkNodeLast(p);
        return p;
    }
    ```

- afterNodeAccess(Node<K, V> p)

    当LinkedHashMap不是默认实现，而是按照访问顺序进行排序时，该方法会将**已存在，想要覆盖值的键值对，移动到双向链表尾部**

    LRU思想：新增的数据，肯定不是最近访问，直接放到链表最后

    ```java
    void afterNodeAccess(Node<K,V> e) { 
        // move node to last
        LinkedHashMap.Entry<K,V> last;

        // 按照访问顺序的LinkedHashMap
        if (accessOrder && (last = tail) != e) {
            LinkedHashMap.Entry<K,V> p =
                (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            // 将已存在的键值对挪到双向链表的最尾部
            tail = p;
            ++modCount;
        }
    }
    ```

- afterNodeInsertion(boolean evict)

    如果evict为true，并且覆写了LinkedHashMap的removeEldestEntry方法的，可以根据removeEldestEntry的返回值，决定是否要删除最年老的结点，即表头

    ```java
    void afterNodeInsertion(boolean evict) { // possibly remove eldest
        LinkedHashMap.Entry<K,V> first;

        // removeEldestEntry默认返回false
        if (evict && (first = head) != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false, true);
        }
    }
    ```

```java
// HashMap
final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
    // ...

    if ((p = tab[i = (n -1) & hash]) == null)
        // 调用newNode的第一个情况，桶为空
        tab[i] = newNode(hash, key, value, null);
    else {
        // ...

        if ((e = p.next) == null) {
            // 调用newNode的第二个情况，桶没有该值，插入到桶尾，并链入到双向链表的尾部
            p.next = newNode(hash, key, value, null);
        }
    }

    if (e != null) {
        // 之前就存在该关键字的映射
        // ...
        afterNodeAccess(e);
        // ...
    }

    // ...结尾判断是否要扩容
    afterNodeInsertion(evict);
    return null;
}
```

### **removeNode（删）**

在删除结点时，肯定也需要维护双向链表

removeNode方法通过afterNodeRemoval模板方法驱动，调用到LinkedHashMap的覆写实现

```java
void afterNodeRemoval(Node<K, V> e) {
    LinkedHashMap.Entry<K, V> p = (LinkedHashMap.Entry<K, V>) e, b = p.before, a = p.after;
    p.after = p.before = null;

    if (b == null)
        // p是队头，直接设置队头为p.after，即a
        head = a;
    else
        // 不是队头，则将前继结点的next设置为p的后继结点
        b.after = a;
    if (a == null)
        // p是队尾，则直接设置队尾为前继结点
        tail = b;
    else
        // p不是队尾，设置后继结点的before为p的前继结点
        a.before = b;
}
```

### **get（查）**

在默认按插入顺序实现的LinkedHashMap中，get操作并不会对双向链表产生影响

但是如果LinkedHashMap是按访问顺序实现的，则get操作是其顺序变化的重要驱动方法

LinkedHashMap通过重写get方法，在HashMap的get操作上多加了一步维护链表关系

```java
public V get(Object key) {
    Node<K, V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    
    // 重要
    if (accessOrder)
        afterNodeAccess(e);
    return e.value;
}
```

根据上面对afterNodeAccess的分析，get操作过的键值对，将会被放到双向链表的最尾部

    从链表的尾往头走，为最近访问的次序，尾部为最近的访问元素

# 参考
- [彻头彻尾理解 LinkedHashMap](https://blog.csdn.net/justloveyou_/article/details/71713781)