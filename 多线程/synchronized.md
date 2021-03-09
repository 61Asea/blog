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

    当字节码解释器执行monitorenter字节码轻度锁住一个对象时（从偏向锁膨胀为轻量锁），就会在获取锁的线程的栈上显式或隐式分配一个lock record

    为什么需要分配lock record：

    是因为原来的偏向锁资源（Mark Word）中没有锁（重量锁）资源，因为后续可能要膨胀为重量锁的需要，所以需要有Lock Record去关联重量锁资源

Lock Record用于存储锁对象的Mark Word的拷贝（Displaced Mark Word）

每一个被锁的对象，其Mark Word都会与一个Lock Record相关联

Lock Record：存储了markword的拷贝，Owner字段记录了锁对象的markword

Mark Word：锁记录指针指向获得锁的线程id

#### 4.3 Monitor


# 参考
- [synchronized与volatile原理-内存屏障的重要实践](https://www.cnblogs.com/lemos/p/9252342.html)
- [深入分析Synchronized原理(阿里面试题)](https://www.cnblogs.com/aspirant/p/11470858.html)
- [](https://blog.csdn.net/qq_36268025/article/details/106137960)
- [就是要你懂Java中volatile关键字实现原理](https://www.cnblogs.com/xrq730/p/7048693.html)
- [lock指令前缀与mutex锁](https://blog.csdn.net/saintyyu/article/details/94493694)
- [线程Lock Record与对象Mark Word的关联](https://blog.csdn.net/slslslyxz/article/details/106363990)
- [JVM 的Lock Record简介](https://blog.csdn.net/qq_33589510/article/details/105317593)