import java.util.HashMap;
import java.util.Map;

/*
 * @lc app=leetcode.cn id=146 lang=java
 *
 * [146] LRU 缓存
 *
 * https://leetcode-cn.com/problems/lru-cache/description/
 *
 * algorithms
 * Medium (52.53%)
 * Likes:    1953
 * Dislikes: 0
 * Total Accepted:    292.6K
 * Total Submissions: 557.1K
 * Testcase Example:  '["LRUCache","put","put","get","put","get","put","get","get","get"]\n' +
  '[[2],[1,1],[2,2],[1],[3,3],[2],[4,4],[1],[3],[4]]'
 *
 * 请你设计并实现一个满足  LRU (最近最少使用) 缓存 约束的数据结构。
 * 
 * 实现 LRUCache 类：
 * 
 * 
 * 
 * 
 * LRUCache(int capacity) 以 正整数 作为容量 capacity 初始化 LRU 缓存
 * int get(int key) 如果关键字 key 存在于缓存中，则返回关键字的值，否则返回 -1 。
 * void put(int key, int value) 如果关键字 key 已经存在，则变更其数据值 value ；如果不存在，则向缓存中插入该组
 * key-value 。如果插入操作导致关键字数量超过 capacity ，则应该 逐出 最久未使用的关键字。
 * 
 * 
 * 函数 get 和 put 必须以 O(1) 的平均时间复杂度运行。
 * 
 * 
 * 
 * 
 * 
 * 示例：
 * 
 * 输入
 * ["LRUCache", "put", "put", "get", "put", "get", "put", "get", "get", "get"]
 * [[2], [1, 1], [2, 2], [1], [3, 3], [2], [4, 4], [1], [3], [4]]
 * 输出
 * [null, null, null, 1, null, -1, null, -1, 3, 4]
 * 
 * 解释
 * LRUCache lRUCache = new LRUCache(2);
 * lRUCache.put(1, 1); // 缓存是 {1=1}
 * lRUCache.put(2, 2); // 缓存是 {1=1, 2=2}
 * lRUCache.get(1);    // 返回 1
 * lRUCache.put(3, 3); // 该操作会使得关键字 2 作废，缓存是 {1=1, 3=3}
 * lRUCache.get(2);    // 返回 -1 (未找到)
 * lRUCache.put(4, 4); // 该操作会使得关键字 1 作废，缓存是 {4=4, 3=3}
 * lRUCache.get(1);    // 返回 -1 (未找到)
 * lRUCache.get(3);    // 返回 3
 * lRUCache.get(4);    // 返回 4
 * 
 * 
 * 
 * 
 * 提示：
 * 
 * 
 * 1 <= capacity <= 3000
 * 0 <= key <= 10000
 * 0 <= value <= 10^5
 * 最多调用 2 * 10^5 次 get 和 put
 * 
 * 
 */

/**
 * 思路：
 * 1. 声明CacheNode节点，节点应有前、后指针，用于形成一个双向链表
 * 2. 为了保证O(1)时间复杂度，需要引入HashMap，key为int，value为CacheNode
 * 3. 使用哨兵节点减少head和tail为null的边界情况
 * 4. 将队列的行为抽象为三个，注意总是以传入的node优先处理，再处理node的prev，最后处理node的next
 *  - 出队头：将head的后一位移出链表（这样可以防止head为null的边界情况），需要返回该旧值，用于map的删除
 *  - 入队尾：将新节点加入到tail的前一位
 *  - 删除节点：删除传入的节点，无须考虑边界情况
 * 
 * 5. 编码put方法（不要将map的判断复杂化，通过map.get直接做条件分支）
 *  - node不为null，说明本就有该节点，应该先删除节点，再入队尾
 *  - node为null，先统一将新元素入队尾，判断是否达到lru的长度限制，是的话再出队头
 * 
 * 6. 编码get方法（同理map简单化）
 *  - node不为null，说明有该节点，先删除节点，再入队尾
 *  - node为null，说明没有该节点，直接返回-1即可
 * 
 */
class LRUCache {
    static class CacheNode {
        int key;
        int value;
        CacheNode prev;
        CacheNode next;
        
        public CacheNode() {}
        
        public CacheNode(int key, int value) {
            this.key = key;
            this.value = value;
        }
    }
    
    private Map<Integer, CacheNode> cache = new HashMap<>();
    private int capacity;
    private int count;
    private CacheNode head;
    private CacheNode tail;
    
    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.count = 0;
        this.head = new CacheNode();
        this.tail = new CacheNode();
        this.head.next = tail;
        this.tail.prev = head;
    }
    
    public int get(int key) {
        CacheNode node = cache.get(key);
        if (node != null) {
            removeNode(node);
            addTail(node);
            return node.value;
        }
        return -1;
    }
    
    public void put(int key, int value) {
        CacheNode node = cache.get(key);
        if (node != null) {
            node.value = value;
            removeNode(node);
            addTail(node);
        } else {
            node = new CacheNode(key, value);
            cache.put(key, node);
            addTail(node);
            count++;
            if (count > capacity) {
                CacheNode oldHeadNext = removeHead();
                cache.remove(oldHeadNext.key);
                count--;
            }
        }
    }
    
    private void addTail(CacheNode node) {
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }
    
    private CacheNode removeHead() {
        CacheNode oldHeadNext = head.next;
        removeNode(oldHeadNext);
        return oldHeadNext;
    }
    
    private void removeNode(CacheNode node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
}

/**
 * Your LRUCache object will be instantiated and called as such:
 * LRUCache obj = new LRUCache(capacity);
 * int param_1 = obj.get(key);
 * obj.put(key,value);
 */
// @lc code=end

