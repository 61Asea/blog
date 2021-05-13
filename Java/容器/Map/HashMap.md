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

AbstractMap实现Map的大部分方法，为各种不同类型的映射实现提供了基本实现

它定义了抽象映射的成员变量：键集合和值集合，并将键值对集合的获取方法定义为抽象方法，继承AbstractMap的子类都必须对entrySet()进行自定义

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
}
```