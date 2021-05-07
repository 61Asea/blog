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
            // 初始容量传入0，在添加元素时，不会进行默认扩容，而是根据第一次添加的元素长度进行扩容
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

add/addAll方法会根据加入元素的个数与当前size的总和作为**最小容量**，扩容后的底层数组长度应尽量超过它，否则会造成多次扩容

正常扩容后的长度为之前的1.5倍

    newCapacity = oldCapacity(elementData.length) + oldCapacity >>> 1;

当使用addAll一次性添加过多元素，超过了之前的1.5倍，下次操作必定触发扩容

扩容不可避免地涉及到了内存复制，需要调用**Array.of()把原数组复制到新数组中**，这个操作代价很高，因此应**尽可能地减少扩容的次数**

    如阿里开发规范手册，常见的做法有，在可控可知的元素个数的情况下，创建ArrayList对象时就尽可能指定较大的容量大小，以此来减少扩容的次数

# **3. 删除**

可根据索引下标删除元素的方法remove(index)，以及根据对象是否相等进行删除的方法remove(Object)

```java
public E remove(int index) {
    rangeCheck(index);

    modCount++;
    // 根据索引下标获得响应元素
    E oldValue = elementData(index);

    int numMoved = size - index - 1;
    if (numMoved > 0)
        // 将index后的所有元素都往前挪
        System.arrayCopy(elementData, index + 1, elementData, index, numMoved)
    return oldValue;
}
```

需要调用System.arrayCopy将index后面的所有元素都往前挪一位，即复制到index的位置上，该操作的时间复杂度为O(N)，开销较大

```java
public boolean remove(Object o) {
    if (o == null) {
        for (int index = 0; index < size; index++) {
            if (elementData[index] == null) {
                fastRemove(index);
                return true;
            }
        }
    } else {
        for (int index = 0; index < size; index++) {
            if (o.equals(elementData[index])) {
                fastRemove(index);
                return true;
            }
        }
    }
    return false;
}
```

remove(Object)方法将从头开始查找与指定对象相等的元素，并将其删除，时间复杂度为O(n^2)

# **4. 序列化**

elementData属于transient类型，在序列化的时候会被忽略，因为它是一个可变长度的数组，可能有其他数组项还未被填充

所以ArrayList实现了writeObject和readObject方法，来**控制只序列化被填充部分的内容**

```java
private void writeObject(java.io.ObjectOutputStream s)
throws java.io.IOException{
    int expectedModCount = modCount;
    s.defaultWriteObject();

    s.writeInt(size);

    // 这个地方使用了size，只序列化已填充的项
    for (int i=0; i<size; i++) {
        s.writeObject(elementData[i]);
    }

    if (modCount != expectedModCount) {
        throw new ConcurrentModificationException();
    }
}
```

# **5. 集合框架的快速失败，fail-fast机制（modCount）**

    集合框架的快速失败机制，是一种保证集合在多线程下不能被并发修改的机制

modCount用于记录ArrayList结构发生变化的次数，结构变化指的是添加/删除一个元素，导致底层的内部数组的调整

在进行序列化或者迭代操作时，**会对modCount进行对比**，如果发现有变化，则抛出ConcurrentModificationException异常，来保证迭代的安全

在多线程并发访问容器时，该机制可以有效地提前结束，避免可能发生的错误会造成程序往下执行异常

    在大部分情况下，ConcurrentModificationException的出现不是因为并发，而是在单线程下，使用增强for循环遍历集合，但是又在遍历过程中调整了内部数组
    
    Iterator使用了保护机制，只要它发现有某一次修改不是经过它自己进行的，那么就会抛出异常

解决单线程遍历集合过程中修改内部数组可能导致ConcurrentModificationException的方法，有以下几种：
- 使用普通for循环
- 使用iterator迭代器进行遍历
- 使用Stream的filter（未试过）

# 参考
- [cyc2018-Java容器](https://www.cyc2018.xyz/Java/Java%20%E5%AE%B9%E5%99%A8.html#arraylist)
- [Arrays.asList()的集合不能add()和remove()](https://blog.csdn.net/javaboyweng/article/details/114631958)
- [fail-fast](https://www.cnblogs.com/54chensongxia/p/12470446.html)
- [fail-fast是什么鬼](https://blog.csdn.net/yjn1995/article/details/99471191)