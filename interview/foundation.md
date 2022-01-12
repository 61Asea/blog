# Interview：Java基础

- 基础并发：
    - synchronized
    - volatile
    - JMM
- 线程
- JUC初级：
    - ThreadLocal
    - FutureTask

# **1. synchronized**

synchronized是java提供的原子性**内置锁**，也称为监视器锁，当进入synchronized原语时，会进入指定监视器的获取锁流程，具备4个特点：

- 原子性（互斥性）：synchronized代码块在编译后，会在同步代码的前后各添加`monitorenter`和`monitorexit`字节码指令，它们依赖底层系统mutex_lock，并配合监视器的等待队列Waiting Queue、Blocking Queue、OnDeck

    ![synchronized原理](https://asea-cch.life/upload/2022/01/synchronized%E5%8E%9F%E7%90%86-722075f8a2a04632ac044ab2e37a1a13.png)

    - waiting queue：等待队列，存放竞争线程，内部又分为collectionList和`entrySet`

        - collectionList：用于存放每个竞争失败的线程，每个竞争失败的线程会加入到该集合，专门划分该区域是为了**解决大量线程在并发情况失败后对等待队列进行锁竞争**

        - 后者则是用于存放竞争候选者，候选者们作为准备进入OnDeck的预备线程，由JVM每次从collectionList挪出一部分线程加入
    
    - blocking queue：阻塞队列，主要是监视器对象的`等待集合waitSet`，当获得监视器的owner线程调用obj.wait()方法时，会进入到该集合等待被notify()唤醒，唤醒后的线程无需进入collectionList，直接进入entryList

    - onDeck：`任意时刻，**最多只有一个线程正在竞争锁资源**，该线程称为OnDeck`

    > 处于waiting queue、ondeck的线程都处于Blocked状态，处于blocking queue的线程都处于wait状态
    
- 可见性/有序性：操作系统底层使用的互斥锁，最终汇编后也包括volatile使用的#Lock前缀指令，该指令通过读/写屏障确保变量的可见性和有序性

- 可重入性：owner线程每次进入/退出同步块，monitor对象的计数器都会加/减1，重入性避免线程出现自己锁自己造成死锁的现象

## **统一答复**

synchronized是java内置的原子锁，又称为内置锁，其通过monitorenter/monitorexit指令调用底层操作系统的互斥锁，实现同一时刻只有一个线程持有资源，其他竞争失败线程均阻塞挂起的语义

# 2. synchronized优化

mutex_lock重量级锁，产生用户态 -> 内核态 -> 用户态的系统状态切换开销，优化的原理就是通过某些手段，**减少mutex_lock锁的使用频率**

具体的思路包括有：

- 锁自旋：竞争线程在获取锁资源失败后，不要立即调用mutex_lock陷入阻塞状态，而是再通过多次CAS操作尝试自旋获取锁资源，直到到达一定次数后仍旧无法获取资源时，才使用mutex_lock进入阻塞状态

    - 缺点：如果没有控制好自旋次数，cpu会陷入较长一段时间的空轮询，极大浪费计算资源

    - 自适应：JVM根据上次CAS成功的次数对次数进行自适应，如果线程的上次CAS操作成功，那么会增加下次CAS的最大自旋次数；如果常出现线程自旋失败，则意味着资源竞争激烈，需要减少锁自旋的次数，甚至会**直接忽视掉锁自旋过程**

- 锁消除：检测到不可能存在竞态条件，JVM会自行消除该监视器锁

- 锁粗化：开发时遵循同步块最小原则以缩小锁持有时间，但是实际上在频繁使用同一共享资源的情况下，频繁对资源的加锁和解锁造成另外不必要的性能开销。JVM会将多个加锁、解锁操作合并在一起，粗化为一个更大范围的锁

JDK6后为synchronized引入了`偏向锁`和`轻量级锁`，这两个锁主要通过**锁自旋**来实现：

- 偏向锁（状态位01，偏向模式为1）：第一次被线程1获取后，JVM会将monitor对象头的`偏向线程ID`设置为线程1，并将`偏向模式标志位`设置为1，表示进入偏向模式

    - 偏向线程：该ID对应线程**每次进入同步块时都无需任何同步操作**

    - 其他线程：尝试获取锁时，会检测对象是否处于偏向模式，并检测对象头的偏向信息发现不是本线程的偏向，则检测线程1是否依旧活跃，若死亡则用CAS替换线程ID为自己的ID，并撤销偏向模式（`偏向模式标志位`设置为0）；否则，将`状态位01`置为00（随后进入轻量级锁）

- 轻量级锁（状态位00）：依据`绝大部分锁，在其同步周期内都是不存在竞争`的假说，如果没有竞争，则轻量级锁使用CAS轻量操作成功避免了互斥量的开销；如果存在竞争，则会造成多次CAS操作对cpu资源的浪费，以及最终可能仍旧转换为重量级锁的开销

    原理：

    1. 每个线程都先在当前栈中分配一个锁记录（Lock Record），用于指向对象头的Mark Word
    2. 并通过CAS操作来将Mark Word的**锁记录指针指向当前线程栈的锁记录**
    
    3. 同一时刻只会有一个线程成功，失败则再次尝试，直至达到一定次数膨胀为重量级锁

- 重量级锁（状态位10）：mutex挂起当前线程，也意味着线程陷入了内核态

## 统一答复

锁膨胀：偏向锁 -> 轻量级锁 -> 重量级锁

偏向锁：一开始monitor对象的标识位为01，并通过偏向模式的0表示当前对象不存在持有者，1表示有偏向持有者

若无竞争者则将偏向信息改为自身线程信息，若有则尝试CAS修改属性
- 成功：CAS期间没有竞争
- 失败：表示CAS期间有竞争，且当前持有者可能还未完成，会暂停当前持有资源的线程进行状态检查

> 状态检查：持有资源者在偏向锁检测过程中，如果已不活跃/退出同步块，则继续偏向模式；否则则进入轻量级锁

轻量级锁：为owner栈和自身栈都分配一个lock record，并拷贝对象头信息到各自的lock record。唤醒owner，owner继续执行代码块；竞争线程则一直进行CAS操作，成功则获取锁资源并增加下次自旋次数，自旋达到一定次数仍旧失败，则升级为重量级锁

# **2. volatile**

volatile可以保证共享变量在线程之间的**可见性**与**有序性**，相比synchronized可以提供更轻量级的可见性保证，但对于多个变量的操作无法保证原子性

解决问题：线程工作内存与主存的一致性问题

原理：volatile通过读写屏障使得多级缓存对某变量的写会立即刷回主存，对某变量的读会强制缓存失效并重新从主存中读取，读写屏障硬件语义下为`#lock`指令，触发cpu`mesi一致性协议`

# **3. JMM**

为了解决CPU和内存之间的读写速度差异，硬件发展过程中引入了高速缓存（L1、L2、L3三级缓存）弥补之间的差距，但又引入**缓存一致性/可见性问题**，加之编译器与CPU的重排序又引入了**有序性**问题，不同的硬件和操作系统内存也**存在访问差异**，因此需要JMM内存模型对多线程操作下的规范约束，屏蔽底层的差异保证**Java进程在不同的平台下仍能达到一致的内存访问效果**

- 原子性：JVM保证了lock、unlock、read、load、assign、use、store、write八个基本操作的原子性

- 可见性：通过volatile、final、synchronized的语义保证可见性

- 有序性：通过volatile、synchronized的屏障来禁止cpu对某些指令的重排

happen-before原则：

1. 程序顺序规则：单线程每个操作，happen-before于该线程的后续操作

2. volatile变量规则：对volatile变量的写，happen-before于对该变量的读

3. 传递性规则：若A先于B，B先于C，则Ahapen-beforeC发生

4. 监视器锁规则：对一个监视器锁的unlock，happen-before对该锁的lock

5. final变量规则：对final变量的写，happen-before于对final域对象的读，happen-before后续对final变量的读

## **统一答复**

JMM是一套屏蔽不同硬件和操作内存访问差异的规范机制，在JMM模型下Java进程在不同平台运行都可以获得一致的内存访问语义，它主要对工作内存和主存的一致性、操作原子性和有序性提出规范：

- 原子性：提供8大原子操作语义，其中lock和unlock对应synchronized的monitorenter和monitorexit

- 可见性：通过volatile、synchronized的语义保证共享变量可见性

- 有序性：通过volatile、synchronized的读写屏障禁止编译器、cpu对某些指令的重排

工作内存：L1、L2、L3和寄存器

主存：物理内存

# **4. 线程**

- 进程：程序的一次执行，是操作系统进行资源分配和调度的独立单位

- 线程：进程中的实际执行单位，是一个更小的独立实体，本身不占用系统资源，只占用一些线程私有的资源（虚拟机栈、寄存器、程序计数器）

线程共有6个状态：NEW、RUNNABLE、BLOCKED、WAIT、TIMED_WAITING、TERMINATED

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

- BLOCKED：阻塞状态，处于Waiting Queue（collection list和entry list）和On Deck的线程，等待竞争监视器锁

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

ThreadLocalMap哈希冲突：使用开放地址法解决哈希冲突，具体采用线性探测

内存泄漏：ThreadLocalMap除非在线程结束，否则始终无法被回收

措施：

- key为弱引用，每次GC时都会回收，从而使得entry数组存在一堆key为null，value有值的entry对象，在用户下次使用时，可以将值直接覆盖在这些key为null的Entry

- **使用完之后调用remove方法删除Entry对象**

# **5. 线程状态**

# 参考
- [Java基础篇](https://mp.weixin.qq.com/s?__biz=MzkzNTEwOTAxMA==&mid=2247485644&idx=1&sn=db46ab83196031d8f563585b72a7b511&chksm=c2b24031f5c5c927bd125e219d4c2c810254f49ddc28988591978d27fe10a07eac85247bf89e&token=982147105&lang=zh_CN&scene=21#wechat_redirect)