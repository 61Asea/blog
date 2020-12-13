## ** Parallel GC悲观策略 **

PSScavenge（新生代的并行），PSMarkSweep（套了一层皮的SerialOld，串行的标记整理算法），PSCompact（后来真正意义上的并行老年代标记整理）

-XX:UseParallelGC: PSScavenge + PSMarkSweep（并行化young gen GC，串行化old gen GC）
-XX:UseParallelOldGC: PSScavenge + PSCompact(不止并行化了youngGC，也并行化了Old GC)

从java se 7u4版本后：使用-XX:+UseParallelGC，会默认开启+UseParallelOldGC

### **1. PGC的YGC/FGC策略**

在YGC执行前：

    min(young已使用大小， 之前晋升到old平均大小) > old剩余大小 ? 不执行YGC，直接执行Full GC : 执行young GC;

    理解：
    1. min(young已使用大小， 之前晋升到old平均大小)：
    代表本次YGC中晋升到老年代的大小，前一个值假设young全部晋升，后一个值假设young按照之前部分晋升率。
    2. min > old剩余大小：
    如果晋升到old不够，那么需要直接FGC回收掉old的部分内存，以支持新的晋升，否则直接YGC会导致old出现内存溢出

在YGC执行后：

    最新的晋升到old平均大小 > 老年代剩余空间大小 ? 触发Full GC : 什么都不做;

触发以上判断的条件：

    在eden上分配内存失败 (Eden空间不足时触发minor gc)

### **2. Demo**

```java
    import java.util.ArrayList;
    import java.util.List;
    
    /**
    * -Xms30m -Xmx30m -Xmn10m -XX:+UseParallelGC -XX:+PrintHeapAtGC
    * @author liuxiao
    */
    public class Test1 {
        public static void main(String[] args) throws InterruptedException {
            List<Object> caches = new ArrayList<Object>(); 
            for (int i = 0; i < 7; i++) {
                caches.add(new byte[1024 * 1024 * 3]);
            }
            caches.clear();

            for (int i = 0; i < 2; i++) {
                caches.add(new byte[1024 * 1024 * 3]);
            }  
            Thread.sleep(10000);
        }
    }
```

按照默认young : old = 1 : 2，eden : From Survivor : To Survivor = 8 : 1 : 1规则
- young大小：10M
- old大小：20M
- Eden大小：8M
- From Survivor：1M
- To Survivor：1M

1. 迭代中的GC情况如下：
    1) young: 3 / 10, old: 0 / 20, YGC: 0, FGC: 0
    2) young: 6 / 10, old: 0 / 20, YGC: 0, FGC: 0
    3) YGC

        触发：eden上分配内存3M失败
        
        =》 
        
        YGC执行前条件：min(6, 0) > 20, 执行young gc

        =》

        Survivor不足以容纳全部Eden的对象，直接通过老年代担保分配（逃生门设计）

        =》

        **young: 3 / 10, old: 6 / 20, YGC: 1, FGC: 0**

        =》

        YGC执行后条件：6 > 14，什么都不做

    4) young: 6 / 10, old: 6 / 20, YGC: 1, FGC: 0
    5) YGC

        触发：eden上分配内存3M失败
        
        =》

        YGC执行前条件：min(6, 6) > 14, 执行young gc

        **young: 0 / 10, old: 12 / 20, YGC: 2, FGC: 0**

        =》
        
        YGC执行后条件：6 > 8，什么都不做

        **young: 3 / 10, old: 12 / 20, YGC: 2, FGC: 0**

    6) young: 6 / 10, old: 12 / 20, YGC: 2, FGC: 0
    7) YGC + FGC

        触发：eden上分配内存3M失败
        
        =》 

        YGC执行前条件：min(6, 6) > 8, 执行young gc
        
        **young: 0 / 10, old: 18 / 20, YGC: 3, FGC: 0**
        
        =》

        YGC执行后条件：6 > 2，执行FGC,但回收率为0

        **young: 3 / 10, old: 18 / 20, YGC: 3, FGC: 1**

2. caches.clear()之后的情况



# 参考
- [GC悲观策略之Parallel GC篇](https://blog.csdn.net/liuxiao723846/article/details/72808495/)
- [你的“对象”啥时候会进入老年代？](https://www.cnblogs.com/xwgblog/p/11703099.html)