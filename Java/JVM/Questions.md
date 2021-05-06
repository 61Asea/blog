# **钻牛角尖问题合集**

## **Q1: JVM GC完成对象地址移动后，如何修正指针?**

### **1. scavenge（复制算法）**

假设在From Survivor区，存在A、B、C三个对象（左边为From，右边为To）

![引用关系：A -> C, B -> C](https://pic3.zhimg.com/80/v2-74bb529adaaf9125561db111328a2bb6_720w.png)

1) 将A从Eden拷贝到To Survivor(A'), A'仍然指向C

2) A’会继续遍历复制下去(**深度优先遍历**)，即将其引用的C从Eden拷贝到To Survivor(C'), 拷贝过程中会获得C'的新地址，并把新地址赋值到A对C的引用上

    ![](https://pic1.zhimg.com/80/v2-e8681708ba0f0476e609be5fb85c9484_720w.png)

3) C增加一个指针，叫做forwarding，让它指向C'，虽然B在From无法感知到C已经变化，但是会在拷贝到To区域遍历复制时，发现C有一个forwarding指针

4) 发现forwarding时，修改对C的引用为C'的地址

    ![](https://pic1.zhimg.com/80/v2-a80071e48435399e057298c99e868b6e_720w.jpg?source=1940ef5c)

### **2. markSweep（标记清除）**

标记清除**无需移动对象**

    至于MarkSweep的adjust pointer，倒是要简单一点，它不用再像mark阶段那样遍历。就是说，它不必再使用Klass的OopMapBlock中的信息去做一次深度优先遍历。因为在mark阶段，就已经把存活的对象找出来了，所以只要遍历space中的存活对象，去看它们所引用的对象是否forwarded，如果是，就把引用更新为forwarding就可以了

### **3. markCompact（标记整理）**

    尤其，在CompactibleSpace中，如果有连续不存活的对象，那么第一个不存活的对象的markOop里会存着下一个存活对象的地址，这样就大大加快了遍历存活对象的速度

## **Q2: 为什么Survivor:Eden大部分是2:8，Young:Old大部分是1:3?**

处于新生代的对象具有朝生夕灭的特点，大部分对象都是不可存活的，所以在此处使用copying算法，只需要复制少量的存活对象

如果新生代的内存过大，那么copy的花销就更大，所以新生代在堆的比例较小

根据数据，98%的对象都熬不过第一轮垃圾收集，为了保险默认使用8 + 1 = 90%的内存空间进行存放

在分配时，平常对象分配到Eden区域
在young GC后：
- 将Eden存活的对象复制到To Survivor中
- From Survivor区域的存活对象根据GC年龄判断进入老年代还是复制到To Survivor（默认值年龄为15）
- 之前的From Survivor区域和Eden区域清除，并将之前的To Survivor区域作为新的From Survivor区域，被清空的旧的From Survivor区域作为新的To Survivor区域

这样时间内只有10%的堆内存是浪费的，不至于浪费50%的内存

## **Q3: G1收集器聊一下?**

从顶层来看，collector一层可以分为两个部分：
- 全局并发标记 Global Concurrent Marking
- 拷贝存活对象 Evacuation

### 1. 全局并发标记

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

### 2.拷贝存活对象

Evacuation将region上的存活对象，全部拷贝新的region中，并回收掉旧的region

G1的YGC和MixedGC本质上就是该阶段

Evacuation并不完全依赖全局并发标记的结果，若有，则使用；若无，则自己遍历根集合

### 3. CSet的选取

- ygc： 新生代所有region
- mixedgc：新生代所有region + 根据全局并发标记计算出的若干收益最高的region

可以看出young gen永远都在CSet的选取范围内，所以G1是不会维护从新生代出发引用涉及到RSet的更新

### 4. 运作模式

G1视实际情况在YGC和MixedGC中切换，只有当MixedGC无法满足内存分配速率的需求时，才会触发Serial Old GC，进行一次Full GC

背后定期做全局并发标记，该过程借助YGC的暂停，也就是说当MixedGC进行时，不会有全局并发标记；反之，不会有MixedGC

### 5. STAB

GC开始前，会将当前的对象图作为快照保存下来，在GC进行的过程中，将新分配的对象都当做活的

每个region都有top-at-mark-start指针，分别为：prevTAMS和nextTAMS，在TAMS上的都是新分配的对象

随着mutator的运行，某些引用的值被覆盖，会使得快照变得不完整。G1在mutator端采用写前屏障的方式，记录下旧值的变化

这个过程也可能使得本来在并发GC中已经死了的对象，因为SATB，活过了GC

从mutator一侧来看，需要write-barrier来实现：
- SATB的完整性 pre-write-barrier
- 跨region的引用记录到RSet里 post-write-barrier

### 6. 并发的过程有哪些？凭什么叫低延迟收集器？

- 并发标记
- logging write barrier

虽然只有这两个并发过程，但是G1的核心在于虽然会mark整个堆，但是在evacuation时有CSet选定范围，通过只选择收益高的region进行收集，这种暂停的开销是可控的，每次evacuate的暂停时间应该与一般GC的ygc类似

与CMS相比，虽然G1在拷贝对象过程中是暂停的，但是CMS在并发标记过程中会因为对象分配，那么在remark过程中会产生较大时间的暂停（因为会将整个Eden加入到根集合一起遍历）

### 6.logging-write-barrier

为了尽量减少write-barrier对mutator的性能消耗，通过队列的形式，将barrier本身要做的事，转移到其他线程中去处理，只需要将barrier的记录到log队列里

SATB write barrier中，每个Java线程都有一个独立的SATB MarkQueue，mutator在barrier只把旧值加入到队列中，满了之后直接加到全局的SATB中等待处理，并给线程分配一个新的MarkQueue

并发标记会定期检查全局SATB队列集合大小，当超过阈值时，就会处理所有队列，把队列中的每个oop都标记到bitmap中

###


## **Q4:**G1和CMS的全局并发标记有几个阶段？其中哪几个阶段是全暂停的，为什么全暂停**

## **Q5: G1是不是分代，怎么划分内存区域**

有**采用分代的思想**，并**使用多个固定大小Region**的内存布局

每块region都有**唯一的分代标志(eden/survivor/old)**，eden region集构成Eden空间，survivor region集合构成Survivor空间，old region集合构成Old空间

Region的大小可以通过参数-XX:G1HeapRegionSize设定，取值范围是：1M - 32M，且是2的指数

自动决定逻辑：
**size = （堆最大值 + 堆最小值） / 2 / TARGET_REGION_NUMBER(2048)**

然后size取最靠近2的幂数值，将size控制在[1M, 32M]之间

## **Q6: 双亲委派模型的优势**

1. 避免核心类库被篡改
2. 避免类重复加载，父加载器加载后，子加载器没必要再加载一次

# 参考
- [copy GC 和 mark & compaction GC的算法异同](https://www.cnblogs.com/chuliang/p/8689418.html)
- [Copy GC(1) : 基本原理](https://zhuanlan.zhihu.com/p/28197216)
- [JVM GC完成对象地址移动后，如何修正指针？](https://www.zhihu.com/question/57732697?sort=created)