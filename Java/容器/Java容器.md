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

### **1.1.3 Queue**

## **1.2 Map接口**

# 参考
- [Java容器](https://www.cyc2018.xyz/Java/Java%20%E5%AE%B9%E5%99%A8.html)