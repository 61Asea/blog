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

## **Q4:**G1和CMS的全局并发标记有几个阶段？其中哪几个阶段是全暂停的，为什么全暂停**

# 参考
- [copy GC 和 mark & compaction GC的算法异同](https://www.cnblogs.com/chuliang/p/8689418.html)
- [Copy GC(1) : 基本原理](https://zhuanlan.zhihu.com/p/28197216)
- [JVM GC完成对象地址移动后，如何修正指针？](https://www.zhihu.com/question/57732697?sort=created)