# 硬件模型

![计算机存储系统](https://img-blog.csdn.net/20160730162807528)

存在问题：CPU与内存的读写速度存在巨大差异，CPU直接从内存读取数据需要等待一定的时间周期

解决方法：通过在CPU内设置缓存（L1Cache，L2Cache和L3Cache），并尽可能的读取更多的字节，寄存器可以通过缓存更快的获取到数据，减少CPU与内存之间运算速度的差异

    多CPU核心协同工作下，又引入了高速缓存一致性问题，通过MESI协议来保证单变量的读写顺序

**路线：CPU寄存器 -> store buffer（解决I状态通知造成的同步阻塞） -> L1Cache/L2Cache/L3Cache -> invalid queue（进行协同，加速ACK响应）-> Bus -> Memory -> Disk**

# **1. CPU**

主流cpu的各大组件：控制部件寄存器、算术及逻辑部件寄存器、store buffers（写缓冲区）、L1/L2/L3 cache（三级缓存）、invalid queue（无效化队列）

## **1.1 高速缓存**

高速缓存位于寄存器与内存之间的一种容量很小的存储器，集成在cpu中

读写速度较快，弥补了部分CPU与内存直接交互的读写差异

它存储了内存的部分拷贝，暂时存放内存中的数据，如果寄存器要取内存中的一部分数据时，可根据数据的内存地址检索缓存

![L1/L2核心独享，L3所有核心共享](https://upload-images.jianshu.io/upload_images/8124450-d18a6ea3d88249ac.png?imageMogr2/auto-orient/strip|imageView2/2/w/533/format/webp)

### **L1/L2/L3 Cache：**

L1，L2，L3 Cache分别称为一级，二级，三级缓存，L1缓存集成在CPU内部，L2和L3缓存早期焊在主板上，现在也都集成在CPU内部，**L1/L2 Cache为核心独有，L3为所有核心共享**

    容量：
    L1 Cache < L2 Cache < L3 Cache(各大CPU厂商目前更关注，因为不同CPU的L1和L2基本没差别)

    速度：
    L1 Cache > L2 Cache > L3Cache

## **1.2 CacheLine 缓存行：**

> [伪共享，缓存行填充（CacheLine补齐）](https://blog.csdn.net/qq_27428109/article/details/74781774)

CacheLine是L1/L2/L3Cache的基本单位，主流大小为64个字节

缓存行填充机制会尽可能地填充相邻内存，直到缓存行填满，系统用**填充上的所有字的内存地址来对缓存行进行标记**

    为什么以缓存行作为基本单位（为什么字做缓存的基本单位是没有效率的）：

    1. 空间局限性，想要读取的字，其相邻字在接下来访问的可能性极高

    2. 对字做标记，意味着需要额外的空间作为标签，如果以字作为基本单位，需要更多的额外空间

    数据在缓存中不是以独立形式存在的，假设访问一个long数组的某一项，当那一项的值被加载到缓存中，会额外加载另外七项，因此能很快地遍历这个数组

    因此，如果数据结构中的项在内存中不是相邻的，将得不到缓存行填充带来的优势

**当CPU需要访问某个字（word）时，会根据字的内存地址检索缓存来获取字所在的缓存行，此处内存地址作为检索的标签，即缓存地址冲突**

    注意：当字已经被核心1的缓存行填充的情况下，其他核心加载该字时，能通过地址总线获取到地址冲突（即读取到核心1的缓存行已经打了该字内存地址的标签），核心1会将字的值发送给其他核心

引入伪共享问题(MESI协议)

    多核心操作的变量虽然可能不是同一个，但是位处于同一个缓存行，会导致双方的缓存行升迁到Shared状态，触发RFO（请求所有权）操作，导致写回、失效等情况，降低操作性能

    eg：JVM的卡表机制与写屏障的引入，当在卡表同一内存区域的对象在标记脏表时，很容易产生缓存行伪共享问题（因为卡表是一个数组，卡表元素在物理上是相邻的）

## **1.3 寄存器**

寄存器是高速贮存部件，它一般用来暂存指令、数据和地址

中央处理器**控制部件**包含的寄存器有：**指令寄存器（IR）和程序计数器（PC）**

**算术及逻辑部件**包含的寄存器有：累加器（ACC），累减器等

### 工作方式：

- 加载

    当需要读取某个字在内存的值，会让高速缓存从内存加载该字及其相邻的字，直到填充满缓存行（因为空间局限性，会尽可能的读入字到缓存行中）

- 读取

    1. 当store buffers没有该字时，根据字的内存地址，检索字所在的缓存行，读取字的值
    2. 当store buffers有该字时，直接读取，这就是store forwarding

- 写入

    1. 当值处于Shared状态时，将值写入到store buffers中，对其他CPU发出invalid通知后，便立即返回，继续执行任务

    2. 当值处于E独享状态时，直接写入到缓存中

## **1.4 MESI协议**

MESI协议用于解决多核心对单值的读写顺序保证，但不保证**多值的读写顺序**

- M（Modified）：修改状态

    数据对应的Cache Line与内存中的数据不一致，数据只存在于本核心的cache中，通过地址总线，不同核心都能感知到竞态值所在其他核的Cache Line
    
    状态更迭:
    
    - 从S -> M：其他核心都应该将自身的缓存行设置为Invalid失效，等待本核心的通知同步值，这也是就是ROF操作（请求所有权操作），性能低下

    - 从E -> M: 直接修改到缓存中，性能较高

- E（Exclusive）：独享/互斥状态

    数据和内存数据一致，数据只存在于本核心的cache中

    状态更迭：

    第一个加载值的核心，其他核心并没有该值对应的缓存行

- S（Shared）：共享状态

    数据和内存数据一致，但是数据存在于很多的cache中

    状态更迭：
    
    值所在缓存行已经被其他核心加载，当本核心加载时，会根据值的内存地址标签，从地址总线的外部引脚感知到地址冲突。本核心和其他加载的核心会将缓存行的状态更迭为Shared

- I（invalid）：失效状态

    Cache Line失效

    状态更迭：

    处于Shared的缓存行被修改时，事务总线会将其他核心的该缓存行状态改为invalid

总结状态更迭常见场景：

    1. cpu1先读入了值到cache，cpu2后续再读入，最后cpu1修改值, 发送失效请求，并同步值给cpu2
    cpu1: E -> S -> M
    cpu2: S -> I

    2.  cpu1读入值到cache，cpu2直接写入该值
    cpu1：E -> I
    cpu2: 一般直接写入，没有读取，写入的时候能感知到cpu1拥有值的缓存行，所以会发送invalid通知cpu1失效，重新读取

操作竞态值，引入了缓存行一致性问题：

    多线程（多CPU核心）操作竞态值下，若某一CPU核心率先操作成功，并将操作后的值放回自己的缓存行，其他CPU核心若无法感知到，则出现缓存不一致问题

    解决的方案就是MESI协议，MESI能保证单个竞态值的读写安全，但会导致彼此影响（写回、失效、同步），性能下降

当第一个CPU核心修改了共享缓存行的值，会发送invalid请求，通知其他拥有该共享缓存行的CPU核心，其他CPU核心会使自身的缓存行失效，重新获取该缓存行，以保证每个缓存使用的共享变量是一致性的

1. 第一个CPU会将自己的cache line数据自动发送给其他CPU，这种操作是绕过主存储器的

2. 存储控制器也可能直接将第一个CPU中的cacheline数据存储到主存储器中，并使得其他cache line的所有拷贝被标记为无效

因为缓存的一致性消息传递是需要时间的，在经典mesi（同步）做法下，写核心cpu会等待其他读核心cpu的ACK；而读核心cpu也会同步请求远程(写核心cpu)数据，再返回ACK

这个过程上读写线程都有一定程度上的阻塞，**降低了cpu性能**

**MESI协议只能保证单个竞态值的读写顺序安全，保证了基本的单竞态值的安全**
    
    CPU或者编译器（如JIT）会根据性能对指令进行重排优化，在多核心对多个缓存行（多个值）操作下，可能会出现无法预估的结果，这时就需要开发者根据写读屏障进行支持了

    eg：
    
    初始状态下：
    a = 0;
    b = 0;

    void foo() {
        this.a = 1; // 语句1
        this.b = 2; // 语句2
    }

    void block() {
        while (b == 0) {
            continue;
        }

        System.out.println(a == 0);
    }

    语句1和语句2的执行顺序是不确定的，因为没有依赖关系，cpu和编译器可能会进行重排优化，造成block输出true

### **1.5 store buffers（存储缓存）**

**经典MESI的失效请求与ACK回应，会使得写线程处于同步阻塞等待ACK过程，store buffers可以将过程异步化**

CPU写核心将写入的值存入写缓冲器中，发送invalid通知其他线程后就可以返回继续工作，等待其他线程都返回invalid ACK后，核心再冲刷缓冲器的值到其他线程的高速缓存以及内存中

**cpu为了优化经典mesi同步阻塞的问题，引入了store buffers，而又因此导致了指令执行顺序的变化，这种重排序是不符合预期的重排序**

    可能出现某核心欲修改的值在store buffers中，但还未刷入到cache和主存中
    
    本核心读取，其他核心进行ROF读取时，这两种情况都返回了未修改的缓存的旧值，进行同步更新便出现了脏读情况

store forwarding解决方案：

    接下来该核心或其他核心远端ROF该值的读取，都优先从该核心的store buffers中读取，这也称为store forwarding
    
    这种做法只解决了多核心操作单一竞态值可能读到脏值的问题，但本质上并未解决多核心操作多竞态值的问题（这个问题在没有store buffers和invalid queue也存在）

## **1.5 重排序**

重排序大致分为：
1. 编译器（如Java的JIT）优化
2. CPU自身优化
3. store buffers下会导致的看似“重排序”的现象

在有无引入store buffers的情况下，如果没有读写屏障，都会遇到第一、二种情况的重排序

在Java提供的软件层面，在vm层面上，vm会禁止对volatile变量的JIT重排序优化；在cpu层面上，volatile会转换为汇编上cpu支持的Lock#指令，这可以阻止后两种情况的重排（Lock#指令自带屏障的效果，从而解决第三种”重排序“问题）

    Lock#指令：
    1. 会使得总线锁定，其他核心对该变量的读写请求将会得到阻塞，直到锁释放
    2. lock后的写操作会回写已修改的数据，同时让其他cpu的缓存行失效
    3. 阻止屏障两边的指令进行重排序

    第一点个人理解可以防止其他核心在未处理失效请求前，又修改了变量的值

    第二点其实是会将其他变量的变动也写入到store buffers中，阻塞等到其他核心都处理完无效请求，再将最新的值同步到其他核心中并写回到主存

    第一点和第二点，综合起来可以解决掉第三种情况的“重排序”
    第三点可以禁止掉cpu自身的指令优化

但是为了解决经典mesi的写核心同步阻塞问题，引入store buffers和store forwarding策略，这就是第三种“重排序”

第三种重排序并不是真正的对指令排序，而是因为store buffers和invalid queue的延迟性，可能导致实际的读入写入顺序不一致

```java
// a和b的初始值都为0
this.a = 0;
this.b = 0;

void foo() {
    a = 1; // 语句1
    b = 2; // 语句2
}

void block() {
    while (b == 0) {
        continue;
    }

    System.out.println(a == 0);
}
```
    假设不存在cpu与编译器的重排序（即语句1与语句2保证执行顺序），可能会出现以下的事件序列：
    1. b的初始值存在于cpu1的cache中，a的初始值存在于cpu2的cache中，缓存行都属于E独享状态
    2. cpu1开始执行语句1，由于其缓存中没有a，同时发现cpu2已经加载了a的缓存行，cpu1将a的值写入store buffers中，并发送read invalidation通知cpu2（E -> I）
    3. cpu1开始执行语句2，发现b为独占E状态，直接写入缓存与主存（E->M）
    4. cpu2开始执行Read b，因为b刚刚已经通过cpu1写入缓存和主存，得到b的值为1
    5. cpu2开始执行Read a， 但此时cpu1的失效通知还未到/cpu2并未开始处理无效化队列，cpu2的a处于E独占，直接返回了a = 0
    6. 输出true

## **1.6 写屏障**

store buffers搭配store forwarding的方式，可以解决多CPU对单个竞态值的读写顺序问题，但无法保证多CPU对多个竞态值的一致问题

写屏障会将当前store_buffers的数据刷到cache后，再执行屏障之后的“写入操作”

通过写屏障，可以将当前store buffers的条目进行打标，然后将屏障后的写入操作也写入store buffers中，当被打标的条目全部刷到cache后（等待其他线程处理失效请求，写核心在接收到ACK才进行刷新和同步），再刷store buffers中存入的，写屏障后的写入操作

**事务总线会保证同一时刻只有一个核心进行写入操作**

## **1.7 invalid queue（失效/无效化队列）**

在引入store buffers和内存屏障之后，写入操作发生cache missing都会使用store buffers，所以容易出现堆积现象。后续的所有写入操作，都会挤压在store buffers中，当store buffers满了之后，cpu还是会卡在等待对应失效ACK，以处理store buffer中的条目

队列可以使得其他核心瞬间返回响应核心CPU的失效请求，减少写核心的store buffers堆积

## **1.8 读屏障**

清空掉无效化队列，保证失效请求可以立即被处理掉

一般在读操作之前，搭配上写操作之后的写屏障，可以保证变量的可见性

## **1.9 读主存，还是根据写核心的数据进行同步？**

这个问题，取决于当写核心对数据a进行操作时，是否有其他的核心正在读取该共享数据a

如果**有其他线程**共享数据a，那么会采用上述的MESI + store buffers + 写屏障/读屏障，对写核心和读核心进行ROF同步，并**将数据立即刷回主存**

为什么要将数据立即刷回主存呢？

那是因为如果并**没有其他线程**在此时共享数据a，肯定会从主存加载该数据到缓存行中，但是如果并没触发上述的读/写屏障，也没有ROF同步，数据并**无法刷回主存**，那后续的核心加载的值都将不是最新值了。所以其他线程也会触发读/写屏障的效果，将store buffers的数据立即刷回

# **2. 内存**

用于存放数据的单元。其作用是用于**暂时存放CPU中的运算数据**，以及与**硬盘等外部存储器交换**的数据

工作方式：

1. 找到数据的指针。（指针可能存放在寄存器内，所以这一步就已经包括寄存器的全部工作了。）

2. 将指针送往内存管理单元（MMU），由MMU将虚拟的内存地址翻译成实际的物理地址。

3. 将物理地址送往内存控制器（memory controller），由内存控制器找出该地址在哪一根内存插槽（bank）上。

4. 确定数据在哪一个内存块（chunk）上，从该块读取数据。

5. 数据先送回内存控制器，再送回CPU，然后开始使用。

# **3. OS**

> [用户态和内核态的区别](https://www.cnblogs.com/gizing/p/10925286.html)

`内核态`：运行操作系统程序，操作硬件，可以使用**特权指令**

`用户态`：运行用户程序，只能使用**非特权指令**

`特权指令`：由OS使用，用户程序不能使用的指令，如：**I/O启动**，**修改程序状态字PSW**，内存清零，设置时钟，允许/禁止中断，停机

`非特权指令`：用户态可以使用的指令，如：**访管指令（使程序从用户态陷入内核态）**、算数运算、取数指令、控制转移

`特权环`：指的是不同级别下，能够运行不同的指令集合，有：R0、R1、R2、R3

> R0相当于内核态，R3相当于用户态

## **3.1 转换过程**

用户态 -> 内核态：通过中断、异常、**陷入机制（访管指令）**

 - 系统调用：**用户态进程主动要求切换到内核态的一种方式**，对操作系统提供的服务程序**发起申请请求**

    > 用户进程需要获取磁盘数据，则会调用库函数，库函数再调用系统系统调用，从而发起I/O请求（包含了访管指令），将自身陷入为内核态

 - 外围系统的中断信号：当外围设备完成用户请求的操作后，会向CPU/DMA发出相应的中断信号

    > 如磁盘读取足够的数据到自身缓冲区后，会向DMA发送中断信号，DMA转而再向CPU发送中断信号。这时候用户进程就会陷入内核态，切换到硬盘读写的中断处理程序，将数据从磁盘缓冲区拷贝kernel缓冲区，再拷贝到用户进程内存中

 - 异常：程序异常，会触发当前进程切换到处理异常的内核相关程序

内核态 -> 用户态：设置**程序状态字PSW**以执行用户程序

## **3.2 程序运行**

- 当程序运行在R3特权级时，则称为运行在用户态，这是最低特权级，即普通用户进程运行的特权级

    > 大部分用户直接面对的程序都是运行在用户态

- 当程序运行在R0特权级时，可以称为运行在内核态

- 运行在用户态下的程序，不能直接访问OS的内核数据结构和内存。在需要操作硬件时，需要通过陷入到内核态中，通过OS的帮助完成它**没有权限和能力**完成的工作


区别：
- 用户态运行的程序，进程所能访问的内存和对象受限，且处于占有的处理器`可被抢占`
- 内核态运行的程序，进程能访问所有内存空间和对象，且所占有的处理器`不可被抢占`

# **4. 进程与线程**

`进程`：操作系统分配资源和调度的基本单位，是代码在**数据集合上的一次运行活动**

`线程`：CPU资源分配的基本单位（最终占用CPU资源的是线程）

关系：线程是进程中的一个实体，不能独立存在，是进程的一个执行路径。进程至少有一个线程，进程中的线程共享进程的资源

`内核级线程`：直接由kernel支持的线程，程序一般不会直接使用内核级线程，而是用内核级线程的一种轻量级线程接口（LWP），操作系统内核可以感知到线程的存在，可以像调度多个进程一样调度线程，具备**可见性**

- 优点：如果一个LWP线程阻塞了，也不会影响到同一进程上的另一LWP线程，进程不会因此阻塞

- 缺点：
    - 操作特权指令时，都要经历用户态 -> 内核态的状态切换，**转换具备一定开销**
    - 内核级线程数量有限，扩展性比不上用户线程
    
        > 线程表是存放在OS固定的表格空间和堆栈空间

`用户级线程`：通过位于用户空间的线程库创建的线程，操作系统内核以**黑盒视角**认知该线程库，**具有不可见性**

> 因为不可见性，还是按一个内核级线程来进行管理用户空间的线程库，这些线程只能使用一个核

- 优点：
    - 用户级线程完全建立在用户空间的线程库上，这种线程不需要切换内核态

    - 不需要内核支持，可以跨OS运行

- 缺点：

- 若一个用户态线程阻塞，整个进程阻塞，即线程操作由用户解决，阻塞处理十分困难，十分复杂

- 不能利用多核处理器的优势

## **4.1 线程模型**

<!-- 由OS内核支持内核级多线程，由OS程序库支持用户级多线程，线程创建完全在用户空间创建，现成的调度也在应用内部运行，然后把这些用户级多线程映射到一些内核级多线程 -->

可以针对不同的应用特点，调节内核级线程的数据来达到物理并行性和逻辑并行性的最佳方案

以下提供，程序线程与内核级线程映射关系的组合策略：

### **4.1.1 一对一模型**

实现：内核级线程

细节：程序使用内核级线程的**轻量级线程**接口（LWP）创建线程，**每个轻量级线程对应一个内核线程支持**，俗称1:1

典型例子：Java

### **4.1.2 多对一模型**

实现：用户级线程

细节：程序使用用户空间的线程库，创建用户级线程，这些线程创建后都**绑定在一个内核线程上**

典型例子：Node

### **4.1.3 多对多模型**

实现：用户级线程与内核级线程的足额和

细节：为n个用户态线程分配多个m个内核态线程（m通常小于n）

典型例子：Linux

优势：减少内核线程，同时保证多核心并行

## **4.2 线程调度模型**

Java的线程调度模型：抢占式调度模型

每个线程都由系统分配执行时间，线程切换不依赖线程自己决定，若希望系统分配更多时间，则通过设置线程优先级来使得某些线程能在同一时间段内获得更多的操作时间

线程总共有十个优先级Thread.MIN_PRIORITY至Thread.MAX_PRIORITY

# 参考
- [每个程序员都应该了解的CPU高速缓存](http://www.360doc.com/content/18/0313/10/51484742_736583417.shtml)
- [既然CPU有缓存一致性协议（MESI），为什么JMM还需要volatile关键字？](https://www.zhihu.com/question/296949412?sort=created#!)
- [内存屏障今生之Store Buffer, Invalid Queue](https://blog.csdn.net/wll1228/article/details/107775976)
- [内存缓冲和无效队列](https://blog.csdn.net/qq_30055391/article/details/84892936)
- [CPU一致性协议MESI](https://zhuanlan.zhihu.com/p/79777058)
- [编译期重排序、CPU重排序、内存的“重排序”](https://blog.csdn.net/Rinvay_Cui/article/details/111056884)


- [CPU缓存行的问题](https://www.cnblogs.com/jokerjason/p/9584402.html)
- [为什么会这么快？（二）神奇的缓存行填充](http://ifeve.com/disruptor-cacheline-padding/)
- [内存，寄存器和cache的区别与联系](https://www.cnblogs.com/zzdbullet/p/9484040.html)
- [CPU高速缓存行与内存关系 及并发MESI 协议](https://www.cnblogs.com/jokerjason/p/9584402.html)
- [CPU缓存一致性协议MESI](https://zhuanlan.zhihu.com/p/79777058)
- [JVM第四篇 程序计数器(PC寄存器)](https://www.cnblogs.com/niugang0920/p/12424671.html)
- [用户态和内核态的区别](https://www.cnblogs.com/gizing/p/10925286.html)
- [用户态线程和内核态线程的区别](https://www.cnblogs.com/FengZeng666/p/14219477.html)

# 重点参考
- [伪共享Java解决方案](https://blog.csdn.net/z69183787/article/details/108678602)