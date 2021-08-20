# LRU置换算法

LRU（Least recently used，最近最少使用），属于缓存淘汰算法中的一种，根据数据的历史访问记录来进行淘汰数据

    如果数据最近被访问过，那么将来被访问的几率也更高

实现思路：

通过维护一个**固定长度的双向链表**，假设使用尾插法，队头表示最久未访问数据，队尾表示最近访问数据。当缓存超过固定长度阈值时，则队头的过期数据删除，并将最近访问的数据添加到队尾（若已存在于链表，则先移出链表）

## **1. LinkedHashMap**

> [LinkedHashMap](https://asea-cch.life/archievs/linkedhashmap)

LinkedHashMap有两种实现，分别为：根据插入顺序实现和根据**访问顺序**实现

我们可以通过LinkedHashMap的第二种实现来达到LRU的效果，LinkedHashMap在内部维护了一个双向链表

```java
public class LinkedLRUCache<K, V> extends LinkedHashMap<K, V> {
    protected static float DEFAULT_FACTOR = 0.75f;

    protected int cacheMaxNum;

    public LinkedLRUCache(int size) {
        super((int) (size / DEFAULT_FACTOR + 1.0f), DEFAULT_FACTOR, true);

        cacheMaxNum = size;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > cacheMaxNum;
    }

    public static void main(String[] args) {
        LinkedLRUCache<Integer, String> lru = new LinkedLRUCache<>(5);
        for (int i = 0; i < 10; i++) {
            lru.put(i, "data" + i);
        }

        for (int i = 10; i > 5; i--) {
            lru.get(i);
        }

        System.out.println(lru);
    }
}
```

## **2. 自定义双向链表 + HashMap**

```java
public class LRUCache<K, V> {
    static class CacheNode<K, V> {
        protected CacheNode before;

        protected CacheNode after;

        protected K key;

        protected V value;

        public CacheNode(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    protected Map<K, CacheNode> cachedMap;

    protected CacheNode head;

    protected CacheNode tail;

    protected int cacheMaxNum;

    private void enterTail(CacheNode node) {
        if (node == tail) {
            return;
        }

        if (head == null || tail == null) {
            head = tail = node;
            return;
        }

        CacheNode tl = tail;
        tl.after = node;
        node.before = tl;
        node.after = null;
        tail = node;
    }

    private void removeFromLink(CacheNode node) {
        CacheNode after = node.after, before = node.before;
        node.before = node.after = null;

        if (before == null) {
            after.before = null;
            head = after;
        } else {
            before.after = after;
        }

        if (after == null) {
            before.after = null;
            tail = before;
        } else {
            after.before = before;
        }

    }

    public LRUCache(int size) {
        cachedMap = new HashMap<>((int) (size / 0.75f + 1.0f));
        this.cacheMaxNum = size;
    }

    public CacheNode get(K key) {
        CacheNode node = cachedMap.get(key);
        if (node != null) {
            removeFromLink(node);
            enterTail(node);
        }
        return node;
    }

    public void put(K key, V value) {
        CacheNode node = cachedMap.get(key);
        if (node == null) {
            if (cachedMap.size() >= cacheMaxNum) {
                Object headK = head.key;
                removeFromLink(head);
                cachedMap.remove(headK);
            }
            node = new CacheNode(key, value);
        } else {
            removeFromLink(node);
        }
        cachedMap.put(key, node);
        enterTail(node);
    }

    public void remove(K key) {
        removeFromLink(cachedMap.remove(key));
    }

    public static void main(String[] args) {
        // 5个缓存块
        LRUCache<Integer, String> cache = new LRUCache<>(5);
        for (int i = 0; i < 10; i++) {
            cache.put(i, "data" + i);
        }

        for (int i = 10; i > 5; i--) {
            cache.get(i);
        }

        System.out.println(cache);
    }
}
```

## **3. lfu算法**

全称：Least frequently used，即最不经常使用

做法：LFU缓存策略是在LRU策略基础上，为每个数据增加一个计数器，来统计这个数据的**访问次数**，以**访问次数**为判断的依据
> 根据key的最近被访问频率进行淘汰，很少被访问的有限被淘汰，被访问多的则被留下来

使用lfu策略筛选淘汰数据时，首先会根据数据的访问次数进行筛选，把**访问次数最低**的数据淘汰出缓存。如果两个数据的**访问次数相同**，则选择**访问时间最久远**（idle_time最长的）的数据进行淘汰

## **4. 综合对比**

lru：
- 优点：维护的时间复杂度低，只要O(1)
- 缺点：可能会误淘汰热度高的key

    > 假设一个key很久没有被访问到，只是偶尔被访问了一次，就被认为是热点数据，不会被淘汰；取而代之被置换的队尾，则是某个在将来很有可能被访问到的数据

lfu：
- 优点：能够避免**周期性**或**偶发性**的操作导致缓存命中率下降的问题
- 缺点：
    - 维护的时间复杂度高，可达到O(N)
    - 只是简单的增加计数器方式并不完美，用户访问的模式是会频繁改变的
    
    > 如果在一段时间内频繁访问的key一段时候后很少被访问到，那么会造成在后面的访问过程中，因为之前的过热而无法被立即清除，只有等待后续的数据计数器到达前热缓存的计数步数，缓存才能被加载进来，在这段时间内缓存miss情况将可能严重

# 参考
- [缓存淘汰算法--LRU算法(java代码实现)](https://blog.csdn.net/wangxilong1991/article/details/70172302)
- [redis缓存淘汰策略LRU和LFU对比与分析](https://blog.csdn.net/raoxiaoya/article/details/103141022)
- [缓存淘汰算法LRU和LFU](https://www.isolves.com/it/cxkf/sf/2020-11-04/32744.html)