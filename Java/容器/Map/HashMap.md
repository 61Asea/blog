# **HashMap**

HashMap，哈希表，也可称为散列或字典，是编码中相当重要的一种数据结构

# **1. 基础设施**

Map接口，定义了映射的常见的put/get/remove/compute等行为

最重要的是定义了映射应有的四种结构：
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

## **1.1 AbstractMap**

AbstractMap实现Map接口的大部分方法，为各种不同类型的哈希表实现提供了基础设施

AbstractMap中，定义了抽象映射的成员变量：键集合和值集合

并将键值对集合的获取方法定义为抽象方法，继承AbstractMap的子类都必须对entrySet()进行自定义

```java
public abstract class AbstractMap<K, V> implements Map<K, V> {
    protected AbstractMap() {}

    transient Set<K> keySet;
    transient Collection<V> values;

    public Set<K>
}
```