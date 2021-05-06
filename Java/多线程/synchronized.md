# synchronzied

## 1. 特性（作用）

1. 原子性/互斥性

    字节码指令：MonitorEnter、MonitorExit

    要么全部执行并且执行过程不会被任何因素打断，要么就都不执行，确保线程互斥的访问同步代码，同一时刻只有获得监视器锁的代码可以访问代码

2. 可见性

    字节码指令：load（加载屏障）、store（存储屏障）

    保证共享变量的修改能够及时可见

        JMM：
        对一个变量unlock操作之前，必须要同步到主内存中；如果对一个变量进行lock操作，则将会清空工作内存中此变量的值，在执行引擎使用此变量前，需要重新从主内存中load操作或assign操作初始化变量值

3. 有序性

    解决重排序问题，即“一个unlock操作先行发生于后面对一个锁的lock操作”

4. 可重入性

    最大的作用是避免线程死锁，避免自己锁自己的情况发生

        method1同步方法内调用了另一个同步方法，如没有可重入的特性，则会尝试再去获取monitor，但是因为计数器在进入method1的时候已经不为0，会造成线程死锁

## 2. 字节码文件层面

### 2.1 同步块（显式同步）
```java
public class Demo {
    public static void main(String[] args) {
        synchronized(Demo.class) {
            System.out.println("...");
        }
    }
}
```

反编译为类二进制文件：
```java
public void method();
    Code:
    0: aload_0
    // ...
    3: moniterenter
    // ...
    13: moniterexit
```

1. moniterenter：每个对象都是一个监视器锁，当moniter被占用时会处于锁定状态，线程执行moniterenter指令时会尝试获得monitor的所有权

    1. 如果monitor的进入数为0，则该线程进入monitor，然后将进入数置为1，该线程即为monitor的所有者；
    2. 如果线程已经占有了monitor，只是重入，则进入数+1
    3. 如果其他线程占用了monitor，则线程等待monitor的进入数为0，再重写尝试获得monitor的所有权

2. moniterexit：执行monitorexit的线程必须是monitor的所有者，指令执行时，monitor的进入数-1，如果减1后monitor的进入数为0，那线程退出monitor，不再是该monitor的所有者，其他被这个monitor阻塞的线程可以尝试下获取这个monitor的所有权

### 2.2 同步方法（隐式同步）

```java
public class Demo {
    public synchronzied void method() {
        System.out.println("...");
    }
}
```

反编译的二进制文件：

```java
public synchronzied void method();
    flag: ACC_PUBLIC,ACC_SYNCHRONIZED
    Code: 
        // 没有monitor指令了
```

通过ACC_SYNCHRONIZED助记符，指示JVM来实现同步

### 2.3 ObjectMonitor.hpp

```c++
ObjectMonitor() {
    _header       = NULL;
    _count        = 0; // 记录个数
    _waiters      = 0,
    _recursions   = 0;
    _object       = NULL;
    _owner        = NULL;
    _WaitSet      = NULL; // 处于wait状态的线程，会被加入到_WaitSet
    _WaitSetLock  = 0 ;
    _Responsible  = NULL ;
    _succ         = NULL ;
    _cxq          = NULL ;
    FreeNext      = NULL ;
    _EntryList    = NULL ; // 处于等待锁block状态的线程，会被加入到该列表
    _SpinFreq     = 0 ;
    _SpinClock    = 0 ;
    OwnerIsThread = 0 ;
  }
```

Monitor是一个同步工具，通常被描述为一个对象，Synchronized在JVM的实现都是基于**进入和退出Monitor对象**来实现方法同步和代码块同步，通过上述的显式同步（MonitorEnter和MonitorExit）或隐式同步（助记符）的方式实现

所以Java对象都可以成为Monitor，从分配就带了Monitor锁/内部锁

## **3. 操作系统层面**

上述第二点的两种方式本质上都没有区别，都是JVM通过调用操作系统的mutex互斥原语来实现的，被阻塞的线程会被挂起、等待重新调度

重量级锁通过监视器对象monitor实现，其中monitor的本质是依赖于底层系统的Mutex Lock实现，这会导致线程从用户态向内核态转换（挂起，重新调度），会导致“用户态和内核态”两个态之间进行切换，对性能有较大影响

    线程1执行同步块，发现monitor已被其他线程占有，会进行阻塞，此时调用内核指令，切换到内核态，执行阻塞

    重新调度锁池的线程，也会切换到内核态中进行唤醒恢复，并重新切换到用户态继续执行

在这里需要与volatile的lock#前缀锁定做一个区分，mutex锁是操作系统层面的互斥原语，而lock#前缀属于更深层次的汇编指令

即**mutex的实现的底层也是用到lock#前缀的指令，保证作为互斥锁提供的原子性操作**

## **4. 对象头/Lock Record/Monitor**

32位VM中，一个机器码等于4字节；64位VM中，一个机器码等于8字节

对象头由标记字和类型指针组成，长2个机器码；若对象为数组类型，则长3个机器码（需要多一个机器码来记录数组长度，VM只能通过元数据确定对象大小，而无法确定长度）

![标记字64位比特位图](https://img-blog.csdnimg.cn/20190111092408622.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpdWR1bl9jb29s,size_16,color_FFFFFF,t_70)

### **4.1 对象头中的Mark Word**

Synchronized用的锁存在于对象的对象头中，其中对象头中的Mark Word标记字存储着对象运行时的数据，它是实现偏向锁和轻量锁的关键（锁优化）

存储的信息包括：
- 哈希码identity_hashcode
- GC年龄age
- 是否偏向锁biased_lock
- 锁标志位lock
- 指向线程栈的锁记录起始地址指针ptr_to_lock_record
- 指向操作系统mutex锁指针ptr_to_heayweight_monitor

**设计为根据不同的运行状态，复用自己的存储空间**，以便在极小的空间下存储更多的数据，而对象的状态由状态位进行表现

未锁定对象：

    位数：
    unused:25 | hashcode: 31 | unused: 1 | age: 4 | biased_lock: 1 | lock: 2

    具体比特位详情：
    unused:1010110101101011010110101 | hashcode: 1010110101101011010110101 | unused: 0 | age: 0001 | biased_lock: 0 | lock: 01

    其中biased_lock: 0和lock：01，表示该对象处于正常状态
    存储的其他信息包括：哈希码，GC年龄

偏向锁对象:

    位数：
    thread: 54 | epoch: 2 | unused: 1 | age: 4 | biased_lock: 1 | lock: 2

    具体比特位详情：
    thread: 101011010110101101011010110101101011010110101101011010110101101011010110101101011010110101101011010110101101011010110101101011010110101101011010 | epoch: 01 | unused: 0 | age: 0001 | biased_lock: 1 | lock: 01

    其中biased_lock：1和lock：01，表示当前对象的管程处于偏向锁状态
    存储的其他信息动态变化为：偏向锁偏向的线程ID（以及通过该ID记录重入次数），偏向锁的时间戳，GC年龄

轻量级锁对象：

    位数：ptr_to_lock_record:62 | lock: 2

    具体比特位详情:

    lock: 00

    对象状态膨胀为轻量级锁，存储信息动态变化为ptr_to_lock_record，即指向线程的栈中关于该对象的Lock Record起始地址的一个指针

重量级锁对象：

    位数：ptr_to_heavyweight_monitor: 62 | lock: 2

    具体比特位详情:
    
    lock: 10

    对象状态膨胀为重量级锁，存储信息动态变化为ptr_to_heavyweight_monitor，指向重量级锁（碰撞）锁记录的指针，指向监视器Monitor

    可以理解为标记字的状态位变化，基本与对象和线程锁定情况来进行变化

![标志位对应的对象状态（锁定状态）](https://upload-images.jianshu.io/upload_images/2062729-36035cd1936bd2c6.png)    

### **4.2 Lock Record(Displaced Mark Word)**

Lock Record是线程的私有数据结构，每一个线程都有一个可用Lock Record列表，同时还有一个全局的可用Lock Record列表

    Lock Record的产生：
    
    1. 当被偏向的线程再次进入同步块时，发现锁对象偏向的就是当前线程，会在当前线程的栈帧中分配一个Displaced Mark Word为空的Lock Record，并将Lock Record的obj字段指向锁对象

    2. 从偏向锁膨胀为轻量锁时，会给当前线程的栈帧中分配一个用于存储Lock Record的空间，将对象头的Mark Word拷贝到Displaced Mark Word中

    为什么需要分配lock record：

    是因为原来的偏向锁资源（Mark Word）中没有锁（重量锁）资源，因为后续可能要膨胀为重量锁的需要，所以需要有Lock Record去关联重量锁资源

Lock Record在VM中使用BasicObjectLock对象实现，该对象内部由一个BasicLock对象和一个持有该锁的Java对象指针组成，BasicObjectLock对象放置在Java栈帧中

整个Mark Word变为指向BasicObjectLock的指针，该指针必然指向持有该锁的线程栈空间。当需要一个线程是否持有该对象时，只需判断对象头的指针是否在线程的栈地址范围即可

BasicObjectLock的displaced_header备份了原对象的Mark Word内容， obj字段指向持有锁的对象头部

### **4.3 Monitor**

![Monitor中线程的竞争情况（第四步存疑）](https://upload-images.jianshu.io/upload_images/2062729-d1cc81ebcf0e912b.png)

MarkWord的锁标识位为10，则指针指向的就是Monitor对象的起始地址

- _WaitSet：对应等待集，存放在处于Wait状态的线程，处于该状态的线程都是在获取监视器称为ownner后，调用obj.wait()进入等待状态

- _EntryList：对应入口集，存放处于Blocked状态的线程

- _owner：监视器的拥有者，每个监视器在同一时刻只会有一个拥有者

## **5. 锁的膨胀与优化**

Synchronized在JDK6开始，对实现机制进行提升，包括使用JDK5引进的CAS自旋之外，还增加了自适应的CAS自旋、锁消除、锁粗化、偏向锁、轻量级锁等优化策略

对应Mark Word的锁标识位上，锁主要存在四种状态：01（无锁、偏向锁）、00（轻量级锁）、10（重量级锁）

### **5.1 锁优化**

#### **5.1.1 锁自旋**

    自旋和CAS应该做一个明确的概念区分，CAS是比较并置换（Compare And Swap），自旋是忙循环尝试

锁自旋，指的是当一个线程尝试获得某个锁时，如果该锁已经被其他线程占用，则做多次忙检测循环尝试，而不是被挂起或睡眠，进入阻塞状态

    在多数应用上，对象锁的锁状态只会持续很短一段时间，为了很短的时间而频繁使得CPU从用户态切换到内核态是得不偿失的

在后续的偏向锁（批量重偏向）和轻量锁中，都会运用到锁自旋，做多次忙循环尝试，避免线程在粒度较小的同步块中，也需要阻塞挂起和唤醒

锁自旋并不能替代掉阻塞，因为其在竞争激烈/锁持有时间长的环境下并不高效，会出现一直操作失败的情况下，**线程会一直进行空操作自旋，占用CPU处理器的时间，增加CPU的压力的情况**，反而造成的性能浪费

**总结：锁自旋的适用于竞争较小，临界区较小的情况，当锁占用的时间较短，可以减少自旋失败的次数，避免CPU空转**

#### **5.1.2 自适应自旋锁**

基于5.1.1自旋锁的缺点，JDK1.6引入了自适应重试次数的自旋锁，自旋的次数会根据**上一次自旋时间**和**锁Owner**状态来决定自旋次数时间

JVM的自适应策略：

线程如果自旋成功了，那么下次自旋的次数会更多，因为上次既然都成功了，往往下次的自旋也可以成功

反之，如果对于某个锁，很少有自旋能够成功，那么这个锁的竞争情况激烈/临界区过大，自旋会导致CPU性能亏损，就需要降低自旋的次数，甚至忽略掉锁自旋的过程

#### **5.1.3 锁消除**

检测到不可能存在共享数据竞争，则会对这些不可能发生的同步锁进行锁消除

    锁消除的依据是逃逸分析的数据支持，当发现在某些情况下处于线程安全情况下，会消除某些机制的加锁操作

显式：程序员乱加同步机制

隐式：Vector/StringBuffer/HashTable等内置API，在处于状态不可变的情况，可以消除掉内部的锁机制，增加性能

```java
// vec处于该方法的局部变量中，不存在竞态条件
public void test() {
    Vector<Integer> vec = new Vector<>();
    vec.add(1);
}
```

#### **5.1.4 锁粗化**

在使用同步块时，很多人会遵循尽可能小的原则，仅在共享数据的实际作用操作时才进行同步，缩小同步操作数量和持有锁的时间

但是一系列的连续加锁和解锁的操作，可能会导致另外的不必要的性能损耗

锁粗化会将多个连续的加锁、解锁操作连接在一起，扩展成一个范围更大的锁

```java
// 多线程操作vec，vec处于竞态情况下
public void test(Vector<Integer> vec) {
    for (int i = 0; i < 100; i++) {
        // 每次vec.add都会加锁/解锁
        vec.add(i);
    }
}
```

锁粗化优化 =>

```java
public void test(Vector<Integer> vec) {
    // 锁粗化
    synchronized(vec) {
        for (int i = 0; i < 100; i++) {
            vec.add(i);
        }
    }
}
```

#### **5.1.5 偏向锁（01）**

    大多数情况下，锁不仅不存在多线程竞争，而且总是由同一线程多次获得，为了让线程获得锁的代价降低，引进了偏向锁

1. 竞争机制

        直接检查对象头的Mark Word是否为当前线程ID

    一旦线程第一次获得了监视器对象，之后让监视器对象偏向这个线程，之后该线程的多次调用可以直接避免CAS操作（本地延迟），无需再走各种加锁/解锁流程

    CAS操作的本地延迟，指的是Cache一致性协议的开销（mesi加上读写屏障）

2. 撤销机制

        线程A（持有偏向锁）-> 被线程B暂停 -> 若已完成同步块，则退出 / 若未完成同步块，获得升级后的轻量级锁 -> 被线程B唤醒

        线程B -> 发现锁状态为偏向锁状态 -> Mark Word记录的是否为当前线程ID ->尝试一次CAS操作 -> 失败，暂停原持有锁的线程 -> 若原持有未退出同步块，则发起偏向锁撤销和膨胀升迁 / 若已完成，则重新检查Mark Word记录的线程ID -> 唤醒原持有锁的线程

    偏向锁是在单线程执行代码时使用的机制，当发送线程竞争的情况时，会撤销偏向锁，并膨胀为轻量级锁或重量级锁（线程A尚未执行完同步代码块，线程B发起锁升级申请）

3. 目的

    引入偏向锁的目的：为了在没有多线程竞争情况下，减少不必要的轻量级锁执行路径，轻量级锁的加锁解锁操作需要**依赖多次**CAS操作（必然发生）
    
    偏向锁在单线程情况下，可以避免CAS操作，就算出现竞争情况，在尝试置换ThreadId时**依赖一次**CAS操作

#### **5.1.6 轻量级锁（00）**

1. 竞争机制

在偏向锁开启的情况下，会由偏向锁膨胀而来，原持有偏向锁的线程将会获得该轻量级锁，从安全点继续执行


原持有偏向锁线程：
暂停 -> 在线程栈帧分配Lock Record空间，拷贝Mark Word到displaced_word中 -> Mark Word变为记录指向Lock Record的地址指针 -> 被唤醒继续执行，直到解锁

竞争线程：
暂停原持有锁线程 -> 发起膨胀申请 -> 在线程栈帧分配Lock Record空间，拷贝Mark Word到displaced_word中 -> CAS操作（自适应，CAS一定发生无法避免）

2. 升级/撤销机制

原持有偏向所线程在执行完同步块后，会进行一次CAS操作（Mark Word锁记录指针是否仍然指向当前Lock Record && 拷贝栈帧的Mark Word信息是否与对象头的一致）：
-> 不一致，失败，释放锁，唤醒被挂起的线程（说明锁已经升级为重量级锁，对象头的信息已经不一致，Monitor的等待集可能已经有很多CAS失败的线程）
-> 成功，释放锁（说明没有竞争）

竞争线程在接下来CAS操作：
-> 失败，则重试，直到自旋到一定次数仍未成功 -> 升级重量级锁 -> 挂起当前线程，等待原持有锁释放锁，被其唤醒，开始传统的锁竞争
-> 成功，获得轻量级锁，成为原持有偏向线程

3. 目的

在没有多线程竞争的前提下，减少传统的重量级锁使用操作系统互斥量（mutex lock）产生的，CPU从用户态和内核态之间切换的性能消耗

### **5.2 锁膨胀**

![锁膨胀全过程](https://pic2.zhimg.com/v2-8f405804cd55a26b34d59fefc002dc08_r.jpg?source=1940ef5c)

具体详情可参考轻量级锁和偏向锁的升级/撤销机制和竞争机制

# 参考
-[synchronized—深入总结](https://blog.csdn.net/yellowfarf/article/details/103755570)

- [synchronized与volatile原理-内存屏障的重要实践](https://www.cnblogs.com/lemos/p/9252342.html)
- [深入分析Synchronized原理(阿里面试题)](https://www.cnblogs.com/aspirant/p/11470858.html)
- [](https://blog.csdn.net/qq_36268025/article/details/106137960)
- [就是要你懂Java中volatile关键字实现原理](https://www.cnblogs.com/xrq730/p/7048693.html)
- [lock指令前缀与mutex锁](https://blog.csdn.net/saintyyu/article/details/94493694)
- [线程Lock Record与对象Mark Word的关联](https://blog.csdn.net/slslslyxz/article/details/106363990)
- [JVM 的Lock Record简介](https://blog.csdn.net/qq_33589510/article/details/105317593)

- [能否完整的讲述一下java synchronized关键字的锁升级的过程](https://www.zhihu.com/question/267980537/answer/1267465466)
- [偏向锁膨胀到轻量级锁的时候lock-record记录的是什么？](https://www.zhihu.com/question/402591325)