# **ThreadLocal**

# **线程封闭**

当访问共享数据时，通常需要使用同步，一种**避免使用同步**的方式就是不共享数据，每个线程都有一份独立的变量副本，即线程封闭。
- Ad-hoc线程封闭
- 栈封闭
- ThreadLocal

通过例如局部变量和ThreadLocal类，将变量封闭在某个线程中，但仍需确保封闭在线程中的**对象**不会逸出

> 这里说的是避免使用同步，而不是为了同步而去使用，也就是说ThreadLocal并不是用来解决对象共享访问问题

# **ThreadLocalMap**

ThreadLocal是一个工具类，它旨在维护不同线程的(ThreadLocal.ThreadLocalMap)threadLocals变量

它通过Thread.currentThread()来获得当前运行线程，并提供了get与set方法对当前运行线程的threadLocals变量进行存取，做到不同线程的set和get相对于其他线程是不可见独立的

```java
// 初始化的各个方式

// 1. set
private static ThreadLocal<Integer> local = new ThreadLocal<>();
local.set(0);

// 2. lambda设置默认值
private static ThreadLocal<Integer> local = ThreadLocal.withInitial(() -> 0);

// 3. 匿名内部类重写initialValue设置默认值
private static ThreadLocal<Integer> local = new ThreadLocal<>() {
    @Override
    private Integer initialValue() {
        return 0;
    }
};

Syso(local.get()); // 0
```

ThreadLocal中包含ThreadLocalMap内部类，Thread的threadLocals变量通过ThreadLocal.createMap(t, value)方法创建出map，并将value传递进去

```java
public void set(T value) {
    Thread t = Thread.currentThread(); // 获得当前运行线程
    ThreadLocalMap map = getMap(t); // 
    if (map != null) {
        map.set(this, value);
    } else {
        // 创建threadLocals，将value传入到map中(若value是对象，则不可为共享，否则会出现并发问题)
        createMap(t, value);
    }
}

public T get() {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null) {
        // 可以看出map的底层实现是一个ThreadLocalMap.Entry数组
        ThreadLocalMap.Entry e = map.getEntry(this);
        if (e != null) {
            @SuppressWarnings("unchecked")
            T result = (T)e.value;
            return result;
        }
    }
    // 若map为空，则获取初始值(null)并创建map，并返回初始值
    return setInitialValue();
}
```
****
个人觉得很棒的理解，引用：https://www.iteye.com/topic/103804

当然，说ThreadLocal使得各线程能够保持各自独立的一个对象，并**不是通过ThreadLocal.set()来实现的**，而是通过**每个线程中的new对象的操作来创建的对象，每个线程创建一个，不是什么对象的拷贝或副本**，通过ThreadLocal.set()将这个新创建的对象的引用保存到各线程的自己的一个map中，每个线程都有这样一个map，执行ThreadLocal.get()时，各线程从自己的map中取出放进去的对象，因此取出来的是各自自己线程中的对象，ThreadLocal实例是作为map的key来使用的

也就是说，如果ThreadLocal.set()进去的东西本来就是**多个线程共享的同一个对象**，那么多个线程的ThreadLocal.get()取得的还是这个共享对象本身，还是有并发访问问题

总之，**ThreadLocal不是用来解决对象共享访问问题的**，而主要是提供了**保持对象的方法和避免参数传递的方便的对象访问方式**。归纳了两点：
1. 每个线程中都有一个自己的ThreadLocalMap类对象，可以将线程自己的对象保持到其中，各管各的，线程可以正确的访问到自己的对象。
2. 将一个共用的ThreadLocal静态实例作为key，将不同对象的引用保存到不同线程的ThreadLocalMap中，然后在线程执行的各处通过这个静态ThreadLocal实例的get()方法取得自己线程保存的那个对象，避免了将这个对象作为参数传递的麻烦。
****

#### ThreadLocalMap与ThreadLocalMap.Entry
    // ThreadLocal在没有外部强引用时，发生GC会被回收（得益于弱引用）
    // 如果创建TheadLocal的线程一直运行，那么这个Entry对象的value就有可能一直得不到回收，发生内存泄露

    static class ThreadLocalMap {
        static class Entry extends WeakReference<ThreadLocal<?>> {
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                // key被保存到了WeakReference对象中
                super(k);
                value = v;
            }
        }
    
        private static final int INITIAL_CAPACITY = 16; // 初始长度16

        private Entry[] table; // map的底层存储结构

        ......

        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
            table = new Entry[INITIAL_CAPACITY];
            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
            table[i] = new Entry(firstKey, firstValue);
            size = 1;
            setThreshold(INITIAL_CAPACITY);
        }

        private void set(ThreadLocal<?> key, Object value) {
            Entry[] tab = table;
            int len = tab.length;
            // 将threadLocal作为key，哈希值跟长度做与操作，得到下表
            int i = key.threadLocalHashCode & (len-1);

            // 判断是否为空，若不为空则走逻辑，都不符合则nextIndex()继续往下走
            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                ThreadLocal<?> k = e.get();

                // 若该项的key就是欲设置的key，覆盖值
                if (k == key) {
                    e.value = value;
                    return;
                }

                // 该项key为null(弱引用key为null，代表引用某个ThreadLocal的对象被回收了，但此时value泄露，需要替换掉)
                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            // 若Entry数组的该项为空，则创建新的Entry
            tab[i] = new Entry(key, value);
            int sz = ++size;
            // 如果Entry[]长度到了阈值，则重哈希化(resize进行扩容)
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }
    }

# **内存泄露**

本质：ThreadLocalMap和Thread的生命周期一样长，如果没有手动删除对应key的value就会导致Entry内存泄露，而不是因为弱引用导致的

弱引用是为了解决：即使引用ThreadLocal的对象被回收了，但是ThreadLocalMap还有ThreadLocal的强引用，会导致ThreadLocal无法被回收，即强引用ThreadLocal内存泄露，更加严重

****
引用：https://www.jianshu.com/p/a1cd61fa22da

下面我们分两种情况讨论：

- key 使用强引用：引用的ThreadLocal的对象被回收了，但是ThreadLocalMap还持有ThreadLocal的强引用，如果没有手动删除，ThreadLocal不会被回收，导致Entry内存泄漏

- key 使用弱引用：引用的ThreadLocal的对象被回收了，由于ThreadLocalMap持有ThreadLocal的弱引用，即使没有手动删除，ThreadLocal也会被回收。value在下一次ThreadLocalMap调用set,get，remove的时候会被清除

比较两种情况，我们可以发现：由于ThreadLocalMap的生命周期跟Thread一样长，**如果都没有手动删除对应key，都会导致内存泄漏，但是使用弱引用可以多一层保障**：**弱引用ThreadLocal不会内存泄漏**，对应的value在下一次ThreadLocalMap调用set,get,remove的时候会被清除。
****

ThreadLocal在ThreadLocalMap中以一个弱引用被ThreadLocalMap.Entry中的Key引用，因此**如果ThreadLocal没有外部强引用来引用它，那么在下此次JVM的GC会被回收**

如果Key被回收，则会出现null Key的情况，如果当前线程的生命

## **ThreadLocal作为静态变量**

静态threadlocal变量是类的属性，由类加载器加载，在`进程结束`时卸载

> 这意味着不想泄漏时，必须手动remove，但是如果是为了使用ThreadLocal来复用对象的话，问题不大

由于类在类加载器中唯一，所以该类型的ThreadLocal泄漏一般不会导致OOM等情况发生，个数是可控的

**一般ThreadLocal都会是private static修饰，这是由于其用处是线程封闭，使用者是线程，实例的话会造成大量不必要的浪费**

## **ThreadLocal作为实例变量**

实例threadlocal变量是实例属性，由实例创建时产生，并随着实例失去引用被GC时回收

每个Thread维护一个key为该threadlocal的弱引用，value为强引用的ThreadLocalMap

当实例被GC时，Thread中的Map的对应key引用会变为null，**key不会泄漏**

问题：但造成value泄漏，且该个数可能不可控，虽然threadLocal提供懒修复的方式手段，但依旧不推荐

# **场景**

> ThreadLocal的场景，《Java并发编程实战》和正确理解ThreadLocal

正确理解ThreadLocal中讲解了适合的场景为：按线程多实例（每个线程对应一个实例）的对象的访问，并且这个对象很多地方都要用到

通常用于防止对可变的单实例变量或全局变量进行共享。在单线程应用程序中可能会维持一个**全局的数据库连接**，在启动时初始化该连接对象connection，从而**避免在每个方法都得传递这个connection引用**。由于connection不一定是线程安全的，因此没有同步的情况下，作为共享变量可能会产生竞态条件，不是线程安全的。

```java
public class DBManager {
    private static ThreadLocal<Connection> connectionHolder = ThreadLocal.withInitial(
        () -> DriverManager.getConnection(DB_URL)
    )；

    public static Connection getConnection() {
        return connectionHolder.get();
    }
}

public class Main {
    public static void main(String[] args) {
        Connection mConnection = DBManager.getConnection();
        FutureTask<Connection> f = new FutureTask<>(
            () -> DBManager.getConnection
        );
        new Thread(f1).start();

        Connection fConnection = f1.get();

        Syso(mConnection.hashCode() != fConnection.hashCode()) // true
    }
}
```

# 参考：
- [《Java并发编程实战》]()
- [ThreadLocal终极篇](https://zhuanlan.zhihu.com/p/84456443)
- [ThreadLocal源码分析-set方法](https://www.cnblogs.com/noodleprince/p/8657399.html)
- [正确理解ThreadLocal](https://www.iteye.com/topic/103804)
- [ThreadLocal内存泄露真因探究](https://www.jianshu.com/p/a1cd61fa22da)
- [threadlocalMap的Key是弱引用，用线程池有可能泄露](https://www.cnblogs.com/aspirant/p/8991010.html)