# CopyOnWriteArrayList

本文介绍的是写时复制数组列表，它常被用于需要保证**线程安全**的场合，舒适区是**写少读多**，因为每次写时会产生内存复制，影响应用的性能

与Vector和Colletions包装的线程安全ArrayList做横向对比，介绍了COW优化方案和优缺点，以及分析CopyOnWriteArrayList的实现原理

# **1. COW**

    COW的主要思想是：读读不互斥，写操作也不应该影响到读操作

## **1.1 独占式线程安全**
Vector和Colletions包装的线程安全ArrayList，都是以**独占**整个数组资源的形式来保证并发安全。这也意味着当有线程写操作独占资源时，其他读线程将被阻塞，大大降低了读的效率

web业务场景中，**读多写少**是较为常见的，使用**读读互斥，读写也互斥**的线程安全容器，显然效率很差劲

## **1.2 读读不互斥的线程安全**

> [AQS应用：读写锁](https://asea-cch.life/reentrantreadwritelock)

当说到读读互斥的概念，我们可以快速联想到ReentrantLock的优化版本：AQS读写锁，它通过读锁和写锁分离，做到了读线程读取资源不互斥

    如果我们用ReentrantReadWriteLock封装一层ArrayList，可以获得一个效率更高的线程安全ArrayList

读写锁确实能在一定程度缓解Vector的问题，但是它还是无法解决读/写互斥的问题。当有线程独占写锁进行写操作时（防止脏读），或者队列中有写线程正在排队时（防止饥饿），读线程将被阻塞

如果我们想最大程度地提升读的效率，那么**无论读线程在任何时候都不会被阻塞**，这就是COW能做到的

## **1.3 读永不互斥的线程安全**

写时复制的思想，是计算机程序设计上的一种优化策略

在读的时候共享同一份资源，当有调用者需要**添加/修改**该资源，则单独**复制**一份资源的副本给该调用者。在调用者完成写操作时，将该副本覆盖原先的资源，读线程在读取资源时会通过内存屏障（volatile）刷新内存，读取到最新的值

通过以上方式，防止了读线程**读取到写过程中的中间值**，读线程看到的要么是**修改前**，要么就是**修改后**，即保证了**最终一致性**

更重要的是，我们真正做到了读线程不会因为写线程或其他读线程阻塞，大大提升了读效率

## **1.4 CAP视角分析优缺点**

显而易见，COW为了保证可用性，确保最终一致，牺牲掉实时一致性

    由CAP理论中，我们知道无法同时满足A与P，作为权衡，读写锁牺牲了部分可用性，却能换来实时一致性，我们应该根据实际场景来进行选取

> [CAP理论](https://asea-cch.life/cap)

我们也可以简单地分析下CopyOnWriteArrayList的优缺点：
- 优点

    保证线程安全的同时，大大提升了读资源的效率
    
    相当适合**读多写少**的应用场景

- 缺点

    写时复制 =》 Arrays.copyOf，在内存会有两份相同的数据，如果数组过大，将占用大量的内存
    
    频繁的复制过大的内存，可能导致频繁的GC

    **写多读少**的应用场景效率极差

# **2. 基础设施**

继承自List接口，而不是AbstractList接口

```java
public class CopyOnWriteArrayList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
    // 锁
    final transient ReentrantLock lock = new ReentrantLock();

    // 底层数组，只能通过getArray/setArray访问
    // 使用volatile修饰，在每次获取的时候都会读取到最新的ArrayList
    private transient volatile Object[] array;

    final Object[] getArray() {
        return array;
    }

    final void setArray(Object[] a) {
        array = a;
    }

    public CopyOnWriteArrayList() {
        setArray(new Object[0]);
    }

    private void resetLock() {
        UNSAFE.putObjectVolatile(this, lockOffset, new ReentrantLock());
    }
    private static final sun.misc.Unsafe UNSAFE;
    // 传统艺能，CAS设置锁字段的值
    private static final long lockOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = CopyOnWriteArrayList.class;
            lockOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("lock"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
```

1. array

    存储的结构与ArrayList一致，但是多了volatile关键字，这是因为它在可能会导致数组结构修改的操作（添加/删除/设置）时，都会新建一个新的数组，并将更新后的数据**拷贝**到新的数组中

2. lock

    CopyOnWriteArrayList的线程安全操作是通过**lock互斥锁**实现的，当一个线程对数组进行操作时，都会独占锁资源，防止多个线程并发修改

## **2.1 关键操作**

## **2.1.1 add**

防止其他写线程并发修改导致线程安全问题，并在写时复制一份新数组，对新数组进行操作，操作完毕后用新数组覆盖元数据

```java
public boolean add(E e) {
    final ReentrantLock lock = this.lock;
    // 防止其他写线程并发修改
    lock.lock();
    try {
        Object[] elements = getArray();
        int len = elements.length;
        // 内存复制，耗时操作
        Object[] newElements = Arrays.copyOf(elements, len + 1);
        newElements[len] = e;
        // 关键方法，因为array成员变量是volatile类型的，在设置后，其他线程会刷新工作内存，获得最新的数据
        setArray(newElements);
        return true;
    } finally {
        lock.unlock();
    }
}
```

```java
public void add(int index, E element) {
    // ... 与add(E e)基本类似的操作，主要考虑下面的数组元素位置移动

    int numMoved = len - index;
    if (numMoved == 0)
        // index刚刚好是数组尾，相当于调用add(E e)
        newElements = Arrays.copyOf(elements, len + 1);
    else {
        newElements = new Object[len + 1];
        // 两个内存复制，相当于在新数组的中间留了个空间，给下面设置
        System.arrayCopy(elements, 0, newElements, 0, index);
        System.arrayCopy(elements, index, newElements, index + 1, numMoved);
    }
    newElements[index] = element;
    setArray(newElements);

    // ...
}
```

与ArrayList的add(int index, E element)方法不同，这里还得内存复制两次，因为操作的并不是原有的数组，新数组没有index之前的数据，真的效率巨差

## **2.1.2 remove**

```java
public E remove(int index) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        Object[] elements = getArray();
        int len = elemetns.length;
        E oldValue = get(elements, index);
        int numMoved = len - index - 1;
        if (numMoved == 0)
            setArray(Arrays.copyOf(elements, len - 1));
        else {
            Object[] newElements = new Object[len - 1];
            // 先复制index之前的数据到新数组中
            System.arrayCopy(elements, 0, newElements, 0, index);
            // 再复制index之后的数据到新数组中
            System.arrayCopy(elements, index + 1, newElements, index, numMoved);
            // 不用像ArrayList一样将尾部置空了，因为操作的是新数组而不是之前的旧数组
            setArray(newElements);
        }
        return oldValue;
    } finally {
        lock.unlock();
    }
}
```

# 参考
- [Java容器：CopyOnWriteArrayList](https://www.cyc2018.xyz/Java/Java%20%E5%AE%B9%E5%99%A8.html#vector)

# 重点参考
- [Java并发编程实战——从并发容器CopyOnWriteArrayList看COW与读写锁的对比](https://blog.csdn.net/No_Game_No_Life_/article/details/104711805?utm_term=%E8%AF%BB%E5%86%99%E9%94%81%E5%92%8CCopyOnWriteArrayList&utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~all~sobaiduweb~default-2-104711805&spm=3001.4430)