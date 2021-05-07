ArrayList，从名字来看是基于数组实现的列表，所以它具备一切数组应有的特性

- 支持快速随机访问

    可以根据下标快速定位到元素（implements RandomAccess）

- 物理内存分布上连续，每个元素大小相同

    会在物理内存上分配一块区域作为数组的存储空间，元素在空间上连续相邻，有顺序性

# **1. 基础结构**

```java
public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    transient Object[] elementData;

    private static final Object[] EMPTY_ELEMENTDATA = {};
    
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    private static final int DEFAULT_CAPACITY = 10;

    public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
            this.elementData = new Object[initialCapacity];
        } else if (initialCapacity == 0) {
            // 初始容量传入0，在添加元素时，不会进行默认扩容？
            this.elementData = EMPTY_ELEMENTDATA;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public ArrayList() {
        // 默认构造，在添加第一个元素时，会根据DEFAULTCAPACITY_EMPTY_ELEMENTDATA来进行默认扩容，长度为10
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    public ArrayList(Collection<? extends E> c) {
        // 内存复制
        elementData = c.toArray();
        if ((size = elementData.length) != 0) {
            // c.toArray might (incorrectly) not return Object[] (see 6260652)
            if (elementData.getClass() != Object[].class)
                elementData = Arrays.copyOf(elementData, size, Object[].class);
        } else {
            // replace with empty array.
            this.elementData = EMPTY_ELEMENTDATA;
        }
    }
}
```

1. Object[] elementData

    ArrayList元素的存储缓冲区，ArrayList的容量就是该数组的长度

    当ArrayList进行扩容时，elementData会被重新赋值，这也是整个ArrayList应注意的核心问题

2. DEFAULTCAPACITY_EMPTY_ELEMENTDATA

    注释原文：
    当添加第一个元素时，任何elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA的空列表，其长度都会被扩容为DEFAULT_CAPACITY

    它与EMPTY_ELEMENTDATA区分开来，作为默认构造的ArrayList在添加第一个元素时的扩容基准

3. EMPTY_ELEMENTDATA

    当初始化ArrayList时，构造器传入了指定值为0的长度，elementData将该值赋值

4. 初始长度和最大长度

    DEFAULT_CAPACITY：数组的初始长度，10

    MAX_ARRAY_SIZE：数组的最大长度，Integer.MAX_VALUE - 8

    size：数组当前长度

5. 迭代器

    ListItr：列表迭代器，支持双向遍历

底层是一个**可变长度**的数组，其他所以我们关注的难点可以放在**长度的维护（扩容）**，还有内存复制导致的效率问题

# **2. 添加（扩容）**

```java
public boolean add(E e) {
    // 确保底层数组的容量，不足则扩容
    ensureCapacityInternal(size + 1);
    elementData[size++] = e;
    return true;
}
```

    在不考虑扩容的情况下，在尾部追加效率较高，否则在数组中间位置添加，将造成数组元素的移动

将指定的元素追加到此列表的末尾，在添加之前调用ensureCapacityInternal方法对容量进行确认。如果底层数组的容量不足，则先进行**扩容**。

```java
private void ensureCapacityInternal(int minCapacity) {
    // add操作：minCapacity = 当前元素个数 + 1
    // addAll操作：minCapacity = 当前元素个数 + 指定集合元素个数
    ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
}

private static int calculateCapacity(Object[] elementData, int minCapacity) {
    if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
        // 集合第一次add/addAll，如果addAll指定集合超过了10个，则返回集合长度
        return Math.max(DEFAULT_CAPACITY, minCapacity);
    }
    return minCapacity;
}

private void ensureExplicitCapacity(int minCapacity) {
    modCount++;

    // elementData.length会保持一直大于等于集合当前的size
    if (minCapacity - elementData.length > 0)
        grow(minCapacity);
}

// 扩容，确保集合至少可以容纳由minCapacity指定的元素数
private void grow(int minCapacity) {
    int oldCapacity = elementData.length;
    // 扩容为之前的1.5倍
    int newCapacity = oldCapacity + (oldCapacity >> 1);
    if (newCapacity - minCapacity < 0)
        // 扩容后依旧无法满足
        newCapacity = minCapacity;
    if (newCapacity - MAX_ARRAY_SIZE > 0)
        // 扩容后直接超过了最大长度，则按照minCapacity算出最长长度
        newCapacity = hugeCapacity(minCapacity);
    // 内存复制，效率低
    elementData = Arrays.copyOf(elementData, newCapacity);
}

private static int hugeCapacity(int minCapacity) {
    // 添加的数目太多，超过了Integer.MAX_VALUE，OOM
    if (minCapacity < 0)
        throw new OutOfMemoryError();
    return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
}
```


# 参考
- [Arrays.asList()的集合不能add()和remove()](https://blog.csdn.net/javaboyweng/article/details/114631958)