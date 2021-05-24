# ConcurrentSkipListMap

> [跳表](https://asea-cch.life/achrives/跳表)

在上面文章中，简单地讲述了跳表的底层结构和加速遍历思想，本文将讲述JDK的跳表实现：ConcurrentSkipListMap，它是一个并发安全的Map容器

> [Map](https://asea-cch.life/achrives/java容器#1-2-Map容器接口)

```java
/**
    一个可伸缩的并发{@link ConcurrentNavigableMap}实现。映射根据其键的{@linkplain Comparable natural ordering}进行排序，或者根据使用的构造函数，通过在映射创建时提供的{@link Comparator}进行排序。
*
    这个类实现了skiplist的一个并发变体，它为{@code containsKey}、{@code get}、{@code put}和{@code remove}操作及其变体提供预期的平均日志（n）时间开销。插入、删除、更新和访问操作由多个线程同时安全地执行。
*
    迭代器和拆分器是弱一致的。
*
    *升序键排序视图及其迭代器比降序视图快。
*
    此类及其视图中的方法返回的所有{@code Map.Entry}对表示生成映射时映射的快照。它们不支持{@code Entry.setValue}方法(但是请注意，可以使用{@code put}、{@code putIfAbsent}或{@code replace}更改关联映射中的映射，具体取决于所需的效果。）
*
    注意，与大多数集合不同，{@code size}方法是<em>而不是</em>常量时间操作。由于这些映射的异步特性，确定当前元素数需要遍历元素，因此如果在遍历期间修改此集合，则可能会报告不准确的结果。此外，批量操作{@code putAll}、{@code equals}、{@code toArray}、{@code containsValue}和{@code clear}不能保证以原子方式执行。例如，与{@code putAll}操作并发操作的迭代器可能只查看一些添加的元素。
*
    这个类及其视图和迭代器实现了{@link Map}和{@link Iterator}接口的所有可选方法。与大多数其他并发集合一样，此类不允许使用{@code null}键或值，因为某些null返回值无法可靠地与缺少元素区分开来。
*
    此类是Java集合框架的成员。
*/
```



# **基础结构**

## **1. Node**

## **2. Index**

# 参考
- [SkipList的基本原理](https://www.cnblogs.com/lfri/p/9991925.html)