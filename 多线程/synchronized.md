# synchronzied

## 1. 特性（作用）

1. 原子性/互斥性

    字节码指令：MonitorEnter、MonitorExit

    要么全部执行并且执行过程不会被任何因素打断，要么就都不执行，确保线程互斥的访问同步代码，同一时刻只有获得监视器锁的代码可以访问代码

2. 可见性

    字节码指令：load（加载屏障）、store（存储屏障）

    保证共享变量的修改能够及时可见

        对一个变量unlock操作之前，必须要同步到主内存中；如果对一个变量进行lock操作，则将会清空工作内存中此变量的值，在执行引擎使用此变量前，需要重新从主内存中load操作或assign操作初始化变量值

3. 有序性

    解决重排序问题，即“一个unlock操作先行发生于后面对一个锁的lock操作”

4. 可重入性

    最大的作用是避免线程间死锁

        子类同步方法调用了父类同步方法，如没有可重入的特性，则会产生死锁

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

### 3. 操作系统层面

上述第二点的两种方式本质上都没有区别，两个指令的执行都是JVM通过调用操作系统的mutex互斥原语来实现的，被阻塞的线程会被挂起、等待重新调度

挂起，重新调度，会导致“用户态和内核态”两个态之间进行切换，对性能有较大影响

    线程1执行同步块，发现monitor已被其他线程占有，会进行阻塞，此时调用内核指令，切换到内核态，执行阻塞

    重新调度锁池的线程，也会切换到内核态中进行唤醒恢复，并重新切换到用户态继续执行

### 4. 

# 参考
- [synchronized与volatile原理-内存屏障的重要实践](https://www.cnblogs.com/lemos/p/9252342.html)
- [深入分析Synchronized原理(阿里面试题)](https://www.cnblogs.com/aspirant/p/11470858.html)
- [](https://blog.csdn.net/qq_36268025/article/details/106137960)
- [就是要你懂Java中volatile关键字实现原理](https://www.cnblogs.com/xrq730/p/7048693.html)