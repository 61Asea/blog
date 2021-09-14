# TreeMap

> [Java容器](https://asea-cch.life/archives/java容器)

在Java容器中介绍了Map接口的实现，作为键值对映射存储，我们可以通过哈希表作为底层结构进行存储，实现快速查找的功能；也可以通过多在底层数组上，多维护一个双向链表，提供按插入顺序或按访问顺序迭代的功能

> [HashMap](https://asea-cch.life/archives/hashmap)
> [LinkedHashMap](https://asea-cch.life/archives/linkedhashmap)

在本文，将介绍以**二叉搜索树**为底层结构实现的Map，它相比HashMap查找速度较慢，但可以按照**键值的比较结果进行排序**。使用**红黑树**来维持二叉树的平衡，保证在最糟的插入情况下也能达到O(logN)时间复杂度

> [红黑树](https://asea-cch.life/archives/redblacktree)

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

> [NabigableMap](https://asea-cch.life/archives/java容器#NavigableMap)

只要实现了cellingEntry/floorEntry方法，内部通过getCeilingEntry和getFloorEntry方法来实现

两个方法的核心步骤：

1. 从根开始往下遍历
2. 如果比当前遍历结点p小，则往p.left遍历；反之亦然
3. 与下一层的结点对比时，会根据与其对比的结果，和下一层结点是否有叶子结点方向，作出不同的结果

根据floor和ceiling特性不同，对于第三点的叶子结点方向和对比结果，会有不同做法：

- 如果给定K小于p
    - 如果为floor，则需要判断p是否为p的父节点的左方向，是的话，说明K肯定也小于p的父节点，所以得再往上进行遍历，直到方向为右为止（这个地方也是刚刚K往下遍历时，转换方向的地方）
    - 如果是ceiling，且没有比p再小的结点的话（p.left == null），则无需判断，答案为p

- 如果给定K大于p
    - 如果是floor，且没有比p再大的结点的话(p.right == null)，答案为p
    - 如果是ceiling，需要判断p是否为p的父节点的右方向，是的话，说明K肯定也大于p的父节点，所以得往上遍历，直到p为p的父节点的左子结点为止（这个地方也是K往下遍历时，转换方向的地方）

getCeilingEntry: 在Map中的每个相邻键区间，符合**给定键值的区间的最大值**所对应的键值对，若找不到则返回null

```java
final Entry<K, V> getCeilingEntry(K key) {
    Entry<K, V> p = root;
    while (p != null) {
        int cmp = compare(key, p.key);
        if (cmp < 0) {
            if (p.left != null)
                // cmp小于0，说明比p小，要往p的左边走
                p = p.left;
            else
                // 比p小，且没有比p再小的了，答案就是p
                return p;
        } else if (cmp > 0) {
            if (p.right != null) {
                // cmp大于0，说明比p大，要往p的右边走
                p = p.right;
            } else {
                Entry<K, V> parent = p.parent;
                Entry<K, V> ch = p;

                // parent == null，则表示找不到，超过了该map的键值范围
                // 比p大，且没有比p再大的结点，需要判断下p的父节点是不是p的右结点，是的话，说明p的父节点肯定比p小，需要往上遍历到比p大的结点为止
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
                // 比p大，往p的右边走
                p = p.right;
            else
                // 比p大，且没有比p再大的结点，则答案就是p
                return p;
        } else if (cmp < 0) {
            if (p.left != null)
                p = p.left;
            else {
                Entry<K, V> parent = p.parent;
                Entry<K, V> ch = p;

                // K比p小，且没有比p再小的结点了，需要判断p是否为p的父节点的左子结点，是的话，说明p的父节点比p还大，那么往上遍历，直到找到p为p父节点右子结点的地方，这时候的K才会比p大
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

> [红黑树理论](https://asea-cch.life/archives/redblacktree)

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

    TreeMap为键值有序，LinkedHashMap为插入有序或访问有序

在基于对键值对有排序需求的场合，增删改查为O(logn)级的时间复杂度，但是提供了根据键值区间范围取值，根据键值大小迭代的功能

# 参考
- []()