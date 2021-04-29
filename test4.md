1. global current marking

- initial marking（暂停）

    扫描根集合，将所有根集合可直接到达的对象标记出来压入到扫描栈中等待后续使用。该过程借用ygc的暂停阶段，因此没有额外的暂停阶段

- current marking（并发）

    不断取出扫描栈中的引用，遍历引用对应的对象图，标记存活对象；在这个过程中也会处理全局的logging satb 队列，以减缓第三阶段的暂停时间

- final marking（暂停）

    扫描线程中剩余的SATB write barrier引用。因为在上个阶段会将全局SATB logging write barrier队列进行处理，此时还剩下线程中的未满队列的引用。在这个过程中同时也处理弱引用

- clean up（暂停）

    并没有清理堆内存，而是清点region上存活对象的情况得出统计情况，并将空的region回收到可分配region列表中

2. evacuation

evacuation暂停阶段，会将region上的存活对象拷贝到空的region上，回收掉原本使用的region

evacuation包括ygc和mixed gc，会自由选择若干region进行独立收集，称为CSet，CSet涉及的跨region引用通过RSet进行索引

在纯G1模式下，会根据用户设定的时间选择若干收益高的region

在分代式G1模式下：
- young gc: Eden的region
- mixed gc：Eden的region + 若干old gen的region

接下来的过程与parallel gc的young gc类似，会将region上的存活对象拷贝到空的region上，回收掉原本使用的region；这个过程不依赖于全局并发标记，若有则使用；若无则自行遍历根集合进行拷贝

mixed gc下还会对old gen的region存活对象集中移动到其他region上，做了压缩(compact)处理

3. G1的工作模式：

视情况切换young gc和mixed gc进行收集，背后定期执行全局并发标记

全局并发标记的initial marking阶段搭在young gc上执行，不会有额外的暂停阶段

**由参数IHOP决定，会决定是否执行和并发标记周期：**
1. 分配对象大于region的百分之50
2. **在evacuation（young）后，会判断当前堆占用情况是否大于阈值，若大于阈值则执行一次并发标记周期（全局并发标记 + mixed gc）**

当mixed gc无法跟上内存分配速率，导致old gen被填满无法进行内存分配时，会转换进行一次MSC，这才是真正意义上的Full GC

mutator：
在用户线程方面，G1需要维护：
1. SATB的完整性（写前屏障和TAMS指针）

    因为mutator和collector是并发执行的，可能存在SATB引用被覆盖的情况，需要在值覆盖前，通过写前屏障环切，记录下旧值

    新分配的对象都认为是活的，通过TAMS指针，分配在指针范围内的对象都是新分配的对象

2. 跨region的RSet更新（写后屏障）

    写后屏障来更新跨老年代region的RSet更新，因为g1的gc选择范围永远都会有young gen，所以并不需要维护young region之间的RSet更新

CMS的触发条件：

**1. 当CMSInitiatingOccupancyOnly参数开启，若大于阈值（92）则触发CMS**
2. 为开启1条件的参数，VM会自动触发（不推荐）
3. 元区域进行扩容

Full gc的触发条件

**1. 老年代填满，无法再分配内存（G1和其他GC）**
**2. VM的悲观策略，min(新生代的剩余大小，平均晋升old的大小) > old剩余大小**
**3. CMS GC过程中，出现promotion failed（晋升失败）或current mode failed（直接分配在老年代），会停止掉CMS GC，切换为MSC**
4. 主动调用jmap进行full gc（解决内存碎片问题）
5. Perm方法区空间满了