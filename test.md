- 全局并发标记 Global Current Marking
- 拷贝存活对象 Evacuation

# 初始标记(initial marking)

    暂停阶段,从根集合出发，扫描根集合可直接到达的对象，并压入扫描栈中等待后续的三色标记


    该阶段借用G1的YGC的暂停，个人理解这两个过程的信息将会同步化

# 并发标记(concurrent marking)

    并发阶段
    
    不断从扫描栈中取出引用进行递归扫描，过程中还会扫描STAB记录下的引用，这个扫描可以缩短最终标记的停顿时间

# 最终标记(final marking)

    暂停阶段

    完成并发标记后，处理掉剩余的STAB引用，并处理弱引用

    这个阶段与CMS的区别是，CMS在这个阶段会扫描整个根集合，Eden也会作为根集合的一部分被扫描，因此耗时可能会很长

# 清理(clean up)

    暂停阶段

    G1使用外部的bitmap记录mark信息，bitmap记录每个region对象的生存情况，这个阶段并不会实际上做垃圾收集，而是根据停顿模型预测出CSet，等待Evacuation阶段来回收