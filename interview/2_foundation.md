# Java：线程安全基础

**本文已收录至个人博客：** https://github.com/61Asea/blog

目录：
- 基础并发：原子性、可见性、有序性、可重入性、非公平
    - synchronized：
        - 结构：waiting queue（collection list、entry list）、blocking queue、ondeck
        - 优化：锁自适应自旋、锁消除、锁粗化、锁膨胀过程（偏向锁、轻量级锁）

    - volatile：mesi、#lock、读写屏障

    - JMM：屏蔽硬件差异、happen-before
        - 原子性：`lock`、`unlock`、read、write、load、assign、use、store八大原子操作
        - 可见性：synchronized或volatile提供
        - 有序性：通过读写屏障，具体由synchornized、volatile、final等提供，遵循happen-before原则

- 线程：状态（NEW、RUNNABLE、BLOCKED、WAITING、TIMED_WAITING、TERMINATED）
- JUC初级：
    - ThreadLocal：ThreadLocalMap、内存泄漏
    - FutureTask：异步执行同步获取、并发无锁栈（get()方法线程进入，并由执行线程完毕执行finishCompletion()唤醒）
    - CAS：ABA问题

# **1. synchronized**

synchronized是java提供的原子性**内置锁**，也称为监视器锁，当进入synchronized原语时，会进入指定监视器的获取锁流程，具备4个特点：

- 原子性（互斥性）：synchronized代码块在编译后，会在同步代码的前后各添加`monitorenter`和`monitorexit`字节码指令，它们依赖底层系统`mutex_lock`，并通过监视器的等待队列Waiting Queue、Blocking Queue、OnDeck实现原子性

    ![synchronized原理](https://asea-cch.life/upload/2022/01/synchronized%E5%8E%9F%E7%90%86-722075f8a2a04632ac044ab2e37a1a13.png)

    - waiting queue：等待队列，存放竞争线程，内部又分为collectionList和`entrySet`

        - collectionList：用于存放每个竞争失败的线程，每个竞争失败的线程会加入到该集合，专门划分该区域是为了**解决大量线程在并发情况失败后对等待队列进行锁竞争**

        - entryList：入口集，存放竞争候选者，候选者们作为准备进入OnDeck的预备线程，由JVM每次从collectionList挪出一部分线程加入
    
    - blocking queue：阻塞队列，主要是监视器对象的`等待集合waitSet`，当获得监视器的owner线程调用obj.wait()方法时，会进入到该集合等待被notify()唤醒
    
        唤醒后的线程无需进入collectionList，直接进入entryList

    - onDeck：`任意时刻，最多只有一个线程正在竞争锁资源，该线程称为OnDeck`

    > 处于waiting queue、ondeck的线程都处于BLOCKED状态，处于blocking queue的线程都处于WAITING状态
    
- 可见性/有序性：操作系统底层使用的互斥锁，最终汇编后也包括volatile使用的#Lock前缀指令，该指令通过读/写屏障确保变量的可见性和有序性

- 可重入性：owner线程每次进入/退出同步块，monitor对象的计数器都会加/减1，重入性避免线程出现自己锁自己造成死锁的现象

- 非公平锁：synchronized在收到新线程的锁请求时，**有可能抢占onDeck线程的锁资源**，不会立即加入到collection list中，而是先自旋获取锁，如果获取成功则直接成功，否则再加入collection list中

> 被唤醒后的线程，会被加入到entryset（入口集）中，而竞争锁资源的只有onDeck线程（等待被唤醒），即任何时刻都只有一个线程正在竞争锁资源，不会出现惊群效应

实现：字节码指令monitorenter + 2个monitorexit，**配合修改对象markword里的锁信息（不包含修改gc、hashcode信息）**

## **统一答复**

synchronized是java内置的原子锁，又称为内置锁，其通过monitorenter/monitorexit指令调用底层操作系统的互斥锁，实现同一时刻只有一个线程持有资源，其他竞争失败线程均阻塞挂起的互斥语义

# 2. synchronized优化

mutex_lock重量级锁：产生用户态 -> 内核态 -> 用户态的系统状态切换开销

优化：通过某些手段，**减少mutex_lock锁的使用频率**

- 锁自旋：竞争线程在获取锁资源失败后，不要立即调用mutex_lock陷入阻塞状态，而是再通过多次CAS操作尝试自旋获取锁资源，直到到达一定次数后仍旧无法获取资源时，才使用mutex_lock进入阻塞状态

    - 缺点：如果没有控制好自旋次数，cpu会陷入较长一段时间的空轮询，极大浪费计算资源

    - 自适应：JVM根据上次CAS成功的次数对次数进行自适应，如果线程的上次CAS操作成功，那么会增加下次CAS的最大自旋次数；如果常出现线程自旋失败，则意味着资源竞争激烈，需要减少锁自旋的次数，甚至会**直接忽视掉锁自旋过程**

- 锁消除：检测到不可能存在竞态条件，JVM会自行消除该监视器锁

- 锁粗化：开发时遵循同步块最小原则以缩小锁持有时间，但是实际上在**频繁使用同一共享资源**的情况下，频繁对资源的加锁和解锁造成另外不必要的性能开销。JVM会将多个加锁、解锁操作合并在一起，粗化为一个更大范围的锁

JDK6后为synchronized引入了`偏向锁`和`轻量级锁`：

- 偏向锁（锁状态位01 + 偏向模式标志位为1）：第一次被线程1获取后，JVM会将monitor对象头的`偏向线程ID`设置为线程1

    - 可偏向标识：由vm参数决定，默认在4秒前创建的对象都是不可偏向对象(001)，后续创建的对象为（101）

        - 匿名偏向类：如果一个对象为101状态，但偏向线程id为0，则代表这个对象可偏向，但当前未偏向

    - 偏向线程：该ID对应线程**每次进入同步块时都无需任何同步操作**

    并发线程场景：
    
    尝试获取锁时，会检测对象是否处于可偏向模式，并检测对象头的偏向信息。若发现不是本线程的偏向信息，则检测线程1是否依旧活跃（持有锁线程是否已退出同步款）
    
    - 若未退出，升级为轻量级锁
    
    - 若已退出，则以Class的维度进行优化，通过偏向锁计数器、阈值，维护`批量重偏向`和`批量锁撤销`
    
        - 计数器小于阈值20：意味着偏向锁遇到轻度竞争，直接将对象的偏向锁升级为轻量级锁，计数器+1

            偏向锁 -> 轻量级锁

        - 批量重偏向（计数器大于阈值20）：遍历vm所有线程栈中该类型对象的LR，将它们的epoch + 1，后续其它线程获取锁时，发现epoch不相等，则尝试CAS操作将Mark Word里的线程ID设置为其它线程ID

            轻量级锁 -> 偏向锁

        - 批量锁撤销（计数器大于阈值40）：认为该Class存在较为严重的竞争，标记该class不可偏向，之后获取该class的锁，都直接进入轻量级锁

            无锁对象 -> 轻量级锁

            偏向锁 -> 轻量级锁

    ```
    0|01 表示不可偏向
    
    1|01表示可偏向，但不确定是否已偏向，得再用threadID是否为0来表示是否偏向中。设置了threadID的偏向后（可能线程未结束同步代码块，也可能已经结束，不确定），其他线程并不能直接通过CAS替换，需要判断epoch（在一些安全点时会判断并设置），只有确定epoch过期的，才可以用CAS来设置新的偏向锁；否则需要升级为轻量级锁。
    ```

- 轻量级锁（锁状态位00）：依据`绝大部分锁，在其同步周期内都是不存在竞争`的假说，如果没有竞争，则轻量级锁使用CAS轻量操作成功避免了互斥量的开销；如果存在竞争，则会造成多次CAS操作对cpu资源的浪费，以及最终可能仍旧转换为重量级锁的开销

    原理：

    1. 每个线程都先在当前栈中分配一个锁记录（Lock Record），用于指向对象头的Mark Word
    2. 并通过CAS操作来将Mark Word的**锁记录指针指向当前线程栈的锁记录**
    
    3. 同一时刻只会有一个线程成功，失败则再次尝试，直至达到一定次数膨胀为重量级锁

- 重量级锁（状态位10）：mutex挂起当前线程，也意味着线程陷入了内核态

## 统一答复

锁膨胀：偏向锁 -> 轻量级锁 -> 重量级锁

偏向锁：首先需要开启偏向锁（设置延迟生效时间，默认4秒），一开始monitor对象的标识位为01，并通过偏向模式的0表示当前对象不存在持有者，当偏向模式标志变为1时，表示锁有偏向线程

1. 若无偏向属性，则将偏向信息改为自身线程信息
2. 若有偏向属性，暂停当前持有资源的线程进行状态检查
    - CAS修改成功：CAS期间没有竞争
    - CAS修改失败：表示CAS期间有竞争，且当前持有者可能还未完成，会暂停当前持有资源的线程进行状态检查

> 状态检查：持有资源者在偏向锁检测过程中，如果已不活跃/退出同步块，则继续偏向模式；否则则进入轻量级锁

轻量级锁：为owner栈和自身栈都分配一个lock record，并拷贝对象头信息到各自的lock record。然后唤醒owner，owner继续执行代码块，竞争线程则一直进行CAS操作。当CAS成功则获取锁资源，并增加该锁的自旋最大次数；否则，则继续自旋，直到达到一定次数仍旧失败，则升级为重量级锁

# **2. volatile**

volatile可以保证共享变量在线程之间的**可见性**与**有序性**，相比synchronized可以提供更轻量级的可见性保证，但对于多个变量的操作无法保证原子性

解决问题：线程工作内存与主存的一致性问题

原理：volatile通过`读写屏障`使得多级缓存对某变量的写会立即刷回主存，对某变量的读会强制缓存失效并重新从主存中读取

hotspot代码：`lock: addl`

读写屏障硬件语义：`#lock`指令，触发cpu`mesi一致性协议`

禁止重排序：

1. 当第二个操作是volatile写，不管第一个操作是什么，禁止重排序

2. 当第一个操作是volatile读，不管第二个操作是什么，禁止重排序

3. 当第一个操作是volatile写，第二个操作是volatile读，禁止重排序

    - 第二个操作若是其他操作，可重排：

        ```java
        volatile boolean flag = false;

        int a = 0;

        public void writer() {
            // a = 1可能重排到flag = true之前
            flag = true;
            a = 1;
        }
        ```

# **3. JMM**

为了弥补CPU与内存之间读写速度的差异，硬件发展过程中引入高速缓存（L1、L2、L3三级缓存）弥补差距，随之产生**缓存一致性/可见性问题**，且编译器与CPU的重排序也引入**有序性**问题，不同的硬件和操作系统内存也**存在访问差异**，因此需要JMM内存模型对多线程操作下的规范约束，屏蔽底层的差异保证**Java进程在不同的平台下仍能达到一致的内存访问效果**

- 原子性：JVM保证了lock、unlock、read、load、assign、use、store、write八个基本操作的原子性

- 可见性：通过volatile、final、synchronized的语义保证可见性

- 有序性：通过读写屏障等方式来禁止cpu对某些指令的重排

    happen-before原则：

    1. 程序顺序规则：单线程每个操作，happen-before于该线程的后续操作

    2. volatile变量规则：对volatile变量的写，happen-before于对该变量的读

    3. 传递性规则：若A先于B，B先于C，则Ahapen-beforeC发生

    4. 监视器锁规则：对一个监视器锁的unlock，happen-before对该锁的lock

    5. final变量规则：对final变量的写，happen-before于对final域对象的读，happen-before后续对final变量的读

## **统一答复**

JMM是一套屏蔽不同硬件和操作内存访问差异的规范机制，在JMM模型下Java进程在不同平台运行都可以获得一致的内存访问语义，它主要对工作内存和主存的一致性、操作原子性和有序性提出规范：

工作内存：L1、L2、L3和寄存器

主存：物理内存

- 原子性：提供8大原子操作语义，其中lock和unlock对应synchronized的monitorenter和monitorexit

- 可见性：通过volatile、synchronized的语义保证共享变量可见性

- 有序性：通过volatile、synchronized的读写屏障禁止编译器、cpu对某些指令的重排


# **4. 线程**

- 进程：程序的一次执行，是**操作系统**进行**资源分配和调度**的独立单位

- 线程：**进程**的实际执行单位，是一个更小的独立实体，**本身不占用系统资源**，只占用一些进程的资源（**虚拟机栈、寄存器、程序计数器**）

线程共有6个状态：NEW、RUNNABLE、BLOCKED、WAITING、TIMED_WAITING、TERMINATED

- NEW：初始化状态，线程对象被创建后的初始状态

- RUNNABLE：可运行状态，可细分为READY就绪状态和RUNNING运行状态

    - READY：等待OS分配时间片

    - RUNNING：获得时间片正在运行的线程，调用Thread.yield()后，在当前时间片使用完毕后会重新回到READY状态

    - 进入：
        - BLOCKED -> READY：竞争到监视器的锁对象
        - TIME_WAITING -> READY：当前线程的sleep(timeout)方法结束
        - READY -> RUNNING：获得时间片
        - RUNNING -> READY：调用Thread.yield()，且线程的时间片用完

    - 退出：
        - RUNNING -> BLOCKED：发现当前监视器已有owner，且为重量级锁，则进入阻塞状态
        - RUNNING -> TIMED_WAITING：
            - 调用sleep(timeout)
            - 调用obj.wait(timeout)
            - 调用threadObj.join(timeout)
        - RUNNING -> WAITING：
            - 调用obj.wait()
            - 调用threadObj.join()

- BLOCKED：阻塞状态，处于Waiting Queue（collection list和entry list）、On Deck的线程，等待竞争监视器锁

    - 进入：
        - RUNNING -> BLOCKED：如上
        - WAITING -> BLOCKED：处于阻塞队列中，被其他线程的notify()/notifyAll()方法唤醒
    - 退出：
        - BLOCKED -> READY：竞争监视器锁成功，进入就绪状态

- WAITING：无限期等待状态，处于Blocking Queue（wait set）中的线程，本质是调用了监视器.wait()方法

    - 进入：
        - RUNNING -> WAITING：
             - 调用obj.wait()
             - 调用某线程对象的join()方法，本质上仍旧是调用obj.wait()
    - 退出：
        - WAITING -> BLOCKED：
            - 当前监视器持有线程，调用obj.notify()/obj.notifyAll()
            - join()等待的线程完成任务后，会自动唤醒等待线程

- TIMED_WAITING：计时等待状态，处于Blocking Queue（wait set）中的线程，唯一不同的是它们会在一段时间后自动转化为其他状态

    - 进入：

        - RUNNABLE -> TIMED_WAITING
            - 调用obj.wait(timeout)
            - 调用Thread.sleep(timeout)
            - 调用某线程对象的join(timeout)，本质上仍旧是调用obj.wait(timeout)

    - 退出：

        - TIMED_WAITING -> BLOCKED：
            - 调用obj.notify()/obj.notifyAll()
            - 通过obj.wait()进入TIMED_WAITING状态的，且已到结束时间，自动转换为BLOCKED
            - join()线程执行完毕，调用obj.notify()/obj.notifyAll()

        - TIMED_WAITING -> READY：
            - 调用Thread.sleep(timeout)进入状态的，且已到达结束时间，则自动转换到Ready中等待调度

# **5. ThreadLocal**

ThreadLocal本身不存储数据，而是在每个线程中创建一个ThreadLocalMap，线程访问变量时，其实访问的是自身Map中的变量值，以此实现**线程与线程之间相互隔离**，达到**线程封闭**效果

ThreadLocalMap哈希冲突：使用**开放地址法**解决哈希冲突，具体采用线性探测

内存泄漏：ThreadLocalMap除非在线程结束，否则始终无法被回收

措施：

- key为弱引用，每次GC时都会回收，从而使得entry数组存在一堆key为null，value有值的entry对象，在用户下次使用时，可以将值直接覆盖在这些key为null的Entry

- **使用完之后调用remove方法删除Entry对象**

# **5. FutureTask**

支持**获取结果**、**同步执行**的异步任务类，实现了Runnable和Future接口，主要实现思路类似AQS：结合volatile、CAS（伴随着状态自旋和阻塞），使用`并发无锁栈`

并发无锁栈：只有一个waiters引用，全部线程都只通过一个CAS操作设置waiters指针（设置成功的同时q.next = waiters也是成功的）

```java
private volatile WaitNode waiters;

queued = UNSAFE.compareAndSwapObject(this, waitersOffset, q.next = waiters, q);
```

- 并发无锁栈waiters：FutureTask保留栈顶元素的引用，每有一个调用`get()`方法等待任务的线程，都将从栈顶入栈，所以出栈则是从栈顶元素开始（先进后出）

- FutureTask#get() -> FutureTask#awaitDone()：调用后判断任务的状态

    - 如果为COMPLETED（已完成），则直接返回结果FutureTask.outcome

    - 如果为COMPLETING（正在完成中），则调用Thread.yield()后短暂停止，再去获取结果

    - 如果为CANCELLED（已取消），则直接抛出任务取消异常

    - 否则，进入并发无锁栈中阻塞，等待唤醒

- FutureTask#finishCompletion：唤醒并发无锁栈的所有线程

# **6. CAS原理**

CAS又名compare and swap（比较并交换），主要是通过**处理器指令**保证该操作的原子性，它包含**三个**操作数：

- 变量内存地址，用V表示
- 旧的预期值，用A表示
- 准备设置的新值，用B表示

操作过程：当执行CAS时，只有V的值为A时，才会用B去更新V的值，否则更新操作失败

使用场景：配合自旋实现busy waiting忙轮询，避免线程进入sleep waiting产生内核态切换的开销

底层实现汇编指令：`lock cmpxchg`，cmpxchg是比较并交换，lock通过锁定北桥信号来保证cmpxchg操作的原子性（不是锁总线）

问题：

1. ABA问题

    当读取到的值是A时，但在**准备赋值前**值可能已被改成过B值，再改回A值，而在**准备赋值时**发现仍旧是A值，这种更新漏洞就叫做ABA问题

    Java可以使用AtomicStampedReference解决该问题，它新加入了`预期标识`和`更新后标识`两个字段，检查时不只检查旧值，还会检查标识值是否也到达预期，只有全部相等的情况下才会更新

    解决思路：新增与值无关，但与操作相关的标志位进行甄别，这个标识可以是**版本号**

    ```
    作者：Curiosity
    链接：https://www.zhihu.com/question/269109328/answer/345774153
    来源：知乎
    著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。

    我觉得java的AtomicStampedReference确实蛮坑爹的，每次cas不管成功失败都会new出一个对象，性能有一定影响。最好的解法是像c++的folly库一样能偷64位指针的高16位做stamp，但是java语言层面规定的太死，玩不了这种hack。不要用Reference，而用数组下标做指针，用Atomic<Long>记录下标，偷高位的bit做stamp，可以绕开AtomicStampedReference，但这样GC又成了问题...反正挺麻烦的。
    
    ABA给你举个例子你就懂了，假如我有个链表W->X->Y->Z，我要删除X，如果我只做W.next.CompareAndSet(X, X.next)的话，如果这个线程CAS前被挂起，另一个线程删掉了X，X被GC了，又另一个线程new了个node，叫nX好了，插入到W和Y中间，而刚好分配给他X原来的地址，链表就变成了 W->nX->Y->Z，这时候第一个线程醒了，CompareAndSet仍然会成功，因为X和nX地址一样，就把nX删掉了，而逻辑上X和nX是不同的node，第一个线程逻辑正确的操作是不去动链表，返回false（没找到X）。如果用了StampedReference每次修改以后stamp+1，就可以避免这种情况。
    ```

2. 自旋次数过大

    自旋搭配CAS实现忙轮询，在小并发程度下有较好的性能表现，但是当竞争过于激烈时，往往会出现到达自旋次数后仍旧无法成功操作，为CPU带来空轮询开销

3. 只能更新单值

    粒度为单个共享变量值，并不能保证其他操作的原子性

7. 哲学家问题（死锁问题）

解决死锁的方法：

- 一次性拿到所有的资源（锁的粒度放大）：通用的解决方案，一定可以解决死锁，但是效率不高

- 

# 参考
- [Java基础篇](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247485644&idx=1&sn=db46ab83196031d8f563585b72a7b511&chksm=c2b24031f5c5c927bd125e219d4c2c810254f49ddc28988591978d27fe10a07eac85247bf89e&token=982147105&lang=zh_CN&scene=21#wechat_redirect)

- [马士兵：synchronized详解](https://www.bilibili.com/video/BV18Z4y1x74U/?spm_id_from=333.999.0.0&vd_source=24d877cb7ef153b8ce2cb035abac58ed)

- [马士兵：volatile详解，讲了内存屏障、DCL](https://www.bilibili.com/video/BV1gt4y1X73J/?spm_id_from=333.337.search-card.all.click&vd_source=24d877cb7ef153b8ce2cb035abac58ed)