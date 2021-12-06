# Zookeeper：Znode

zk的类似一个linux树状的小型文件系统，zk的指令在底层都是操作DataTree对象

# **DataTree**

```java
public class DataTree {
    /**
     * This is a pointer to the root of the DataTree. It is the source of truth,
     * but we usually use the nodes hashmap to find nodes in the tree.
     */
    private DataNode root = new DataNode(new byte[0], -1L, new StatPersisted());

    /**
     * create a /zookeeper/quota node for maintaining quota properties for
     * zookeeper
     */
    private final DataNode quotaDataNode = new DataNode(new byte[0], -1L, new StatPersisted());

    /**
     * the path trie that keeps track of the quota nodes in this datatree
     */
    private final PathTrie pTrie = new PathTrie();

    /**
     * This map provides a fast lookup to the datanodes. The tree is the
     * source of truth and is where all the locking occurs
     */
    private final NodeHashMap nodes;

     /**
     * This hashtable lists the paths of the ephemeral nodes of a session.
     */
    private final Map<Long, HashSet<String>> ephemerals = new ConcurrentHashMap<Long, HashSet<String>>();
}
```

- DataNode：数据存储的最小单元，为znode节点的数据内容

- `nodes`：存放所有的DataNode，当对zk中的znode进行操作时，其实底层就是对这个map进行操作，其中path为key，DataNode为value

- ephemerals：存放所有的临时节点，是nodes的一个子集，便于实时的访问和session结束后的集中清理

- Pathtrie：字典树，跟节点的配额相关，如果没有用到配额就用不到

    zookeeper-quota机制温和，即使超出限制，也只会在日志中报告一下，并不会产生实质性的限制行为

## **插入**

```java
public void createNode(final String path, byte[] data, List<ACL> acl, long ephemeralOwner, int parentCVersion, long zxid, long time, Stat outputStat) {
    int lastSlash = path.lastIndexOf("/");
    // 获得最后一个"/"前的内容，也就是新增结点的父节点
    String parentName = path.substring(0, lastSlash);
    // 最后一个"/"后的全部内容
    String childName = path.substring(lastSlash + 1);
    // 结点的属性，zxid为leader为其创建的全局事务id
    StatPersisted stat = createStat(zxid, time, ephemeralOwner);
    // 从nodes中获取父节点信息
    DataNode parent = nodes.get(parentName);
    if (parent == null) {
        // NoNodeException
        throw new KeeperException.NoNodeException();
    }
    // 使用内置锁，防止多个客户端并发添加结点出现问题
    synchronized(parent) {
        // 获取acl
        Long longval = aclCache.convertAcls(acl);

        Set<String> children = parent.getChildren();
        if (children.contains(childName)) {
            // zk结点的唯一性
            throw new KeeperException.NodeExistsException();
        }

        // 将父节点的acl权限取消掉，这也是为什么acl只能给予子节点的原因
        nodes.preChange(parentName, parent);
        if (parentCVersion == -1) {
            parentCVersion = parent.stat.getCversion();
            parentCVersion++;
        }

        if (parentCVersion > parent.stat.getCversion()) {
            parent.stat.setCversion(parentCVersion);
            // zk-stat特性：当结点有新增、删除子节点时，会修改结点的pzxid属性
            parent.stat.setPzxid(zxid);
        }

        // 新节点实例
        DataNode child = new DataNode(data, longval, stat);
        // 父结点的子结点hashset添加新节点
        parent.addChild(childName);
        // 如果命令具备acl，则设置acl权限
        nodes.postChage(parentName, parent);
        // 计算新增结点的数据大小，并添加到当前父节点的属性中
        nodeDataSize.addAndGet(getNodeSize(path, child.data));
        // 将新增结点加入到全局NodeHashMap中，方便操作
        nodes.put(path, child);

        // ...
}
```

## **查找**

```java
public class DataTree {
    public DataNode getNode(String path) {
        return nodes.get(path);
    }
}
```

nodes是一个NodeHashMap接口实现，具体实现类为`NodeHashMapImpl`，通过对ConcurrentHashMap包装构成：

```java
public class NodeHashMapImpl {
    // 内部类，ConcurrentHashMap
    private final ConcurrentHashMap<String, DataNode> nodes;
    private final boolean digestEnabled;
    private final DigestCalculator digestCalculator;

    public DataNode get(String path) {
        // hashMap.get()
        return nodes.get(path);
    }
}
```

# 参考
- [图文翔解HashTree（哈希树）](https://blog.csdn.net/yang_yulei/article/details/46337405)
- [如何从大量数据中找出高频词](https://my.oschina.net/u/4167465/blog/3077142)
- [Zookeeper用到的字典树（Trie树）](https://www.pianshen.com/article/67831805871/)
- [zk权限管理与配额](https://www.cnblogs.com/linuxbug/p/5023677.html)

# 重点参考
- [浅谈Trie树（字典树）](https://www.cnblogs.com/TheRoadToTheGold/p/6290732.html)
- [ZOOKEEPER源码阅读（二）数据存储](https://www.freesion.com/article/30551352525/)
- [数据模型之限额: Quotas以及PathTrie和StatsTrack](https://www.jianshu.com/p/e60e292eff24)