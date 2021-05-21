# TreeMap

> [Java容器](https://asea-cch.life/achirves/java容器)

在Java容器中介绍了Map接口的实现，作为键值对映射存储，我们可以通过哈希表作为底层结构进行存储，实现快速查找的功能；也可以通过多在底层数组上，多维护一个双向链表，提供按插入顺序或按访问顺序迭代的功能

> [HashMap](https://asea-cch.life/achirves/hashmap)
> [LinkedHashMap](https://asea-cch.life/achirves/linkedhashmap)

在本文，将介绍以**二叉搜索树**为底层结构实现的Map，它相比HashMap查找速度较慢，但可以按照**键值的比较结果进行排序**。使用**红黑树**来维持二叉树的平衡，保证在最糟的插入情况下也能达到O(logN)时间复杂度

> [红黑树](https://asea-cch.life/achirves/redblacktree)

# **1. 基础结构**

```java
public class TreeMap extends AbstractMap<K, V> implements NavigableMap<K, V>, Cloneable, java.io.Serializable {
    // TreeMap是有序的，通过comparator进行排序，左子节点小于父节点，右子节点大于父节点
    private final Comparator<? super K> comparator;
    // 根节点
    private transient Entry<K, V> root;
    // 节点个数
    private transient int size = 0;
    // fast-fail
    private transient int modCount = 0;
}
```

## **1.1 NabigableMap接口**

> [NabigableMap](https://asea-cch.life/achirves/java容器#NavigableMap)

只要实现了cellingEntry/floorEntry方法，内部通过getCeilingEntry和getFloorEntry方法来实现

getCeilingEntry: 在Map中的每个相邻键区间，符合**给定键值的区间的最大值**所对应的键值对，若找不到则返回null

    唯一能确定的是遍历节点p的左子树，都比p小，所以结果一定是p

```java
final Entry<K, V> getCeilingEntry(K key) {
    Entry<K, V> p = root;
    while (p != null) {
        int cmp = compare(key, p.key);
        if (cmp < 0) {
            // cmp小于0，说明比p小，要往p的左边走
            if (p.left != null)
                p = p.left;
            else
                return p;
        } else if (cmp > 0) {
            if (p.right != null) {
                // cmp大于0，说明比p大，要往p的右边走
                p = p.right;
            } else {
                // 进来这里，说明该树路径已经没有比key还大的节点，需要返回到最近一次变换树路径方向的位置
                Entry<K, V> parent = p.parent;
                Entry<K, V> ch = p;

                // parent == null，则表示找不到，超过了该map的键值范围
                // ch != parent.right，表示原路返回到之前分叉位置，因为上一个if一直在往右走
                while (parent != null && ch == parent.right) {
                    ch = parent;
                    parent = parent.parent;
                }
                return parent;
            }
        } else
            return p;
    }
    return null;
}
```

getFloorEntry(K key)：在Map中的每个相邻键区间中，符合**给定键值的区间的最小值**所对应的键值对，若找不到则返回null

    唯一能确定的是遍历节点p的右子树，都比p大，所以结果一定是p

```java
final Entry<K, V> getFloorEntry(K key) {
    Entry<K, V> p = root;
    while (p != null) {
        int cmp = compare(key, p.key);
        if (cmp > 0) {
            if (p.right != null)
                // cmp大于0，往右走
                p = p.right;
            else
                return p;
        } else if (cmp < 0) {
            if (p.left != null)
                // cmp大于0，往左走
                p = p.left;
            else {
                // 进来这里，说明该树路径已经没有比key还小的节点，需要返回到最近一次变换树路径方向的位置
                Entry<K, V> parent = p.parent;
                Entry<K, V> ch = p;

                // ch != parent.left，表示原路返回到之前的分叉位置，因为上面的if一直在往left走
                while (parent != null && ch == parent.left) {
                    ch = parent;
                    parent = parent.parent;
                }
                return parent;
            }
        } else
            return p;
    }
    return null;
}
```

## **1.2 EntryIterator/EntrySet**

1.1得知，TreeMap按键值比较结果大小进行排序，实现了SortedMap特性，所以其迭代器的迭代顺序是**根据键值大小**

迭代器从值最小的节点作为起点进行迭代，根据二叉搜索树的特性，值最小的节点，应在最左的叶子节点；值最大的节点，应在最右放的叶子节点

```java
public interface SortedMap<K, V> {
    Entry<K, V> getFirstEntry();
}

public class TreeMap extends AbstractMap implements NabigableMap {
    final Entry<K, V> getFirstEntry() {
        Entry<K, V> p = root;
        if (p != null)
            while (p.left != null) {
                p = p.left
            }
        return p;
    }
}
```

验证一下EntrySet的迭代器方法的起点是不是调用getFirstEntry方法：

```java
class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    public Iterator<Map.Entry<K, V>> iterator() {
        return new EntryIterator(getFirstEntry());
    }
}
```

所以根据二叉搜索树从小到大的迭代顺序，每到一个节点，其左子树都被迭代过，无需再次遍历，所以优先到达其右子节点的树左路径的终点，再往上遍历，每一次往上时，都遵循**从左往右**的顺序进行遍历。

```java
final Entry<K, V> nextEntry() {
    Entry<K,V> e = next;
    if (e == null)
        throw new NoSuchElementException();
    if (modCount != expectedModCount)
        throw new ConcurrentModificationException();
    next = successor(e);
    lastReturned = e;
    return e;
}

static <K, V> TreeMap.Entry<K, V> successor(Entry<K, V> t) {
    if (t == null)
        return null;
    // 左子树肯定在之前的迭代被遍历过了
    else if (t.right != null) {
        Entry<K, V> p = t.right;
        // 寻找右子节点的树左路径的终点
        while (p.left != null)
            p = p.left
        return p;
    } else {
        Entry<K, V> p = t.parent;
        Entry<K, V> ch = t;
        // 上一次迭代，可能是在上面的if走完，往当时的节点的右节点走了，所以这里应该返回到当时的节点的上一个节点，否则当时的节点将被遍历两次
        while (p != null && ch == p.right) {
            ch = p;
            p = p.parent;
        }
        return p;
    }
}
```

## **2. 红黑树**

> [红黑树理论](https://asea-cch.life/achrives/redblacktree)

TreeMap的实现是红黑树算法的实现，如果要了解TreeMap，必须对红黑树有一定的了解

```java
private static final boolean RED = false;
private static final boolean BLACK = true;

static final class Entry<K, V> implements Map.Entry<K, V> {
    // ...

    boolean color = BLACK;
}
```

TreeMap通过put/deleteEntry来实现对红黑树的插入和删除，

## **3. 用途**