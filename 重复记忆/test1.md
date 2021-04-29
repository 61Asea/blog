从顶层来看，collector一层可以分为两个部分：
- 全局并发标记 Global Concurrent Marking
- 拷贝存活对象 Evacuation

# 1. 全局并发标记

1. 起始标记 initial marking

    暂停阶段

    从根集合出发，将根集合可直接到达的对象压入扫描栈中，等待后续扫描

    该过程借助了G1-YGC的暂停阶段

2. 并发标记 concurrent marking

    并发阶段

    递归取出扫描栈中的引用，直到对象图遍历为止

    也会遍历SATB记录的引用，这个操作可以缩短最终标记的暂停时间

3. 最终标记 final marking

    暂停阶段

    扫描STAB剩余的引用，并处理弱引用

    这个过程耗时相比CMS缩短很多，因为CMS会遍历整个根集合，并将Eden也加入到根集合中，耗时较长

4. 清理 clean up

    暂停阶段

    这个阶段并不是实际上的垃圾收集，而是清点信息

    G1使用外部的bitmap来记录mark信息，这个过程会记录上每个region上对象的存活情况，根据停顿模型得出CSet，等待后续的Evacuation（拷贝阶段）进行回收

<!-- # 2. 拷贝存活对象

Evacuation阶段并不完全依赖全局并发标记得出的CSet结果，若有则使用；若无则自己遍历对象图

Evacution与Parallel Scavenge的YGC过程相似，会将CSet中Region上的存活对象全部复制到新的Region上，并回收掉旧Region

# 3. CSet的选取

- YGC： 全部Young gen的region
- MixedGC：全部Young gen的region + 根据global concurrent marking得出的收益高的若干region

从该过程中可以看出Young gen永远都处于CSet的选取范围，所以G1从不维护从young gen出发的引用涉及到RSet的更新

# 4. 运作模式

G1会根据实际情况在YGC和MixedGC中不断地切换，YGC和MixedGC本质上就是拷贝存活对象阶段

并在背后定期执行全局并发标记

因为全局并发标记的暂停是借助YGC的暂停，所以当选择MixedGC时，不会进行全局并发标记；反之，不会进行MixedGC

# 5. STAB

STAB是G1维持并发收集安全的一个手段，指的是在GC开始前对对象图进行一个快照，此时活的对象成为了一个对象图，在GC收集的过程中，新分配的对象也认为是活的对象

每个region记录着两个top-at-mark-start指针，分别为prevTAMS和nextTAMS，在TAMS上的对象就是新分配的

并发过程中，mutator可能会覆盖掉某些引用字段的值，通过write-barrier的pre-write-barrier写前屏障，记录下变化的旧值，这样在collector遍历对象图时就不会漏掉在snapshot里的对象

这个过程也会使得本应该在并发GC进行中已经是死了的对象，由于SATB，使其活过这次GC -->

# 2.拷贝存活对象

Evacuation将region上的存活对象，全部拷贝新的region中，并回收掉旧的region

G1的YGC和MixedGC本质上就是该阶段

Evacuation并不完全依赖全局并发标记的结果，若有，则使用；若无，则自己遍历根集合

# 3. CSet的选取

- ygc： 新生代所有region
- mixedgc：新生代所有region + 根据全局并发标记计算出的若干收益最高的region

可以看出young gen永远都在CSet的选取范围内，所以G1是不会维护从新生代出发引用涉及到RSet的更新

# 4. 运作模式

G1视实际情况在YGC和MixedGC中切换，只有当MixedGC无法满足内存分配速率的需求时，才会触发Serial Old GC，进行一次Full GC

背后定期做全局并发标记，该过程借助YGC的暂停，也就是说当MixedGC进行时，不会有全局并发标记；反之，不会有MixedGC

# 5. STAB

GC开始前，会将当前的对象图作为快照保存下来，在GC进行的过程中，将新分配的对象都当做活的

每个region都有top-at-mark-start指针，分别为：prevTAMS和nextTAMS，在TAMS上的都是新分配的对象

随着mutator的运行，某些引用的值被覆盖，会使得快照变得不完整。G1在mutator端采用写前屏障的方式，记录下旧值的变化

这个过程也可能使得本来在并发GC中已经死了的对象，因为SATB，活过了GC

从mutator一侧来看，需要write-barrier来实现：
- SATB的完整性 pre-write-barrier
- 跨region的引用记录到RSet里 post-write-barrier

# 6. 并发的过程有哪些？凭什么叫低延迟收集器？

- 并发标记
- logging write barrier

虽然只有这两个并发过程，但是G1的核心在于虽然会mark整个堆，但是在evacuation时有CSet选定范围，通过只选择收益高的region进行收集，这种暂停的开销是可控的，每次evacuate的暂停时间应该与一般GC的ygc类似

与CMS相比，虽然G1在拷贝对象过程中是暂停的，但是CMS在并发标记过程中会因为对象分配，那么在remark过程中会产生较大时间的暂停（因为会将整个Eden加入到根集合一起遍历）

# 6.logging-write-barrier

为了尽量减少write-barrier对mutator的性能消耗，通过队列的形式，将barrier本身要做的事，转移到其他线程中去处理，只需要将barrier的记录到log队列里

SATB write barrier中，每个Java线程都有一个独立的SATB MarkQueue，mutator在barrier只把旧值加入到队列中，满了之后直接加到全局的SATB中等待处理，并给线程分配一个新的MarkQueue

并发标记会定期检查全局SATB队列集合大小，当超过阈值时，就会处理所有队列，把队列中的每个oop都标记到bitmap中