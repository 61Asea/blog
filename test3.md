- global current marking
- evacuation

## 1. global current marking

1. initial marking（暂停阶段）  

扫描根集合，标记所有根集合可直接到达的对象，压入扫描栈等待后续扫描。在分代式G1中，借用YGC的暂停，所以没有额外的暂停阶段

2. current marking（并发阶段）

不断取出扫描栈的引用，进行递归标记存活对象。在该阶段还会扫描SATB write barrier记录的引用，这间接降低了第三阶段的停顿

3. final marking / remarking（暂停阶段）

扫描线程剩余的SATB write barrier引用，并在该过程处理弱引用

**与CMS的remark阶段本质不同，CMS是增量更新，会在该阶段重新扫描mod-union table里的dirty card外加整个根集合，而此时整个young gen(不管对象死活)都会被当做根集合的一部分，因而CMS remark可能会非常慢**

4. clean up（暂停阶段）

没有对堆进行实际上的清理，而是清点region的存活对象情况，若region已没有存活对象，则直接回收到可分配region列表中

<!-- # 2. evacuation（CSet，纯G1，分代式G1）

evacuation是全暂停的，本质上就是Young GC和Mixed GC，负责将存活的对象从region上移动到空的region上，然后回收掉原本的region空间

可以自由选择多个region来独立收集，构成C-Set，靠per-region remembered set实现

在选定CSet后，回收过程与Parallel GC的young gc算法类似，不依赖于global current marking的结果，若有的话则使用；若无则从根集合自行遍历；

纯G1模式下，CSet的选定完全靠统计模型找收益最高、总开销不超过用户指定上限的若干region

分代式G1模式下，CSet的选定根据Young gc或Mixed gc有所不同：
- young gc：全部young gen的region
- mixed gc：全部young gen的region + 若干根据统计模型得出的高收益的old gen region

分代式G1的工作模式就是视情况在young gc和mixed gc之间切换，背后定期做全局并发标记，initial marking搭在young gc上执行。当全局并发标记正在工作时，G1不会选择做mixed gc；反之，不会进行全局并发标记

如果mixed gc无法跟上程序分配内存的速率，导致old gen被填满无法再进行mixed gc，会切换到G1之外的serial old gc来收集整个heap -->

# 2. evacuation


