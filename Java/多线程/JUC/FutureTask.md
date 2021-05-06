# FutureTask

> [线程的生命周期](http://8.135.101.145/archives/thead)

本篇文章涵盖：Runnable、Callable、Thread、Future与FutureTask

# **1. 线程接口和线程类**

## **1.1 Runnable**

重写Runnable接口，就代表着要创建一个线程，当**线程在VM执行时，会执行线程对象的run()方法**

```java
public interface Runnable {
    public abstract void run();
}
```

## **1.2 Thread**

> [线程的生命周期](http://8.135.101.145/archives/thead)

```java
public class Thread implements Runnable {
    public enum State {
        // 初始化 new Thread()
        NEW,
        // 调用start()方法，进入就绪状态；当获得cpu时间片时，进入运行状态
        RUNNABLE,
        // 在锁对象的锁池(入口集/同步队列)中，等待锁竞争
        BLOCKED,
        // 在锁对象的等待集中，等待唤醒
        WAITING,
        // 在锁对象的等待集中，等待唤醒/限时唤醒
        TIMED_WAITING,
        // 消亡
        TERMIANTED
    }

    private Runnable target;

    private ThreadGroup group;

    // 如果是传入Runnable对象，则初始化target
    public Thread(Runnable target) {
        init(null, target, "Thread-" + nextThreadNum(), 0);
    }

    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    public synchronized void start() {
        // 已经运行的线程，不可再次运行
        if (threadStatus != 0)
            throw new IllegalThreadStateException();

        // 线程组在init方法默认传入null，会等同于security的线程组，或是父线程(调用start的当前线程)的线程组
        group.add(this);

        boolean started = false;
        try {
            // C++方法，真正的线程调度
            start0();
            started = true;
        } finally {
            try {
                if (!started) {
                    group.threadStartFailed(this);
                }
            } catch (Throwable ignore) {
                // 这里直接忽视，如果start0方法出错，抛出了异常，它将会被传递到调用栈上
            }
         }
    }

    // c++真正执行线程的本地方法，进入Ready状态，等待cpu分配时间片
    // 当该线程对象获得时间片时，会调用自身的run()方法
    private native void start0();
}
```

从以上start0()和run()方法的关系，可以发现定义任务的最原始方式有两种：
1. 继承Thread类，重写run方法，这样就不会调用到target.run方法
2. 传入一个target对象，通过调用target.run方法来执行任务

## **1.3 Callable**

Callable类型的任务，无法直接传入Thread的构造器，可以通过FutureTask进行包装，再传入Thread中

重写该接口的任务，可以返回**返回结果**

    调用FutureTask.get()方法可以阻塞获取结果

call方法**允许抛出异常**

```java
/**
 * A {@code Runnable}, however, does not return 
 * a result and cannot throw a checked exception.
 * Runnable无法返回结果和抛出受检异常
 */
public interface Callable<V> {
    V call() throws Exception;
}
```

## **1.4 Future<V>**

Future表示**异步计算的结果**，提供了
- isDone: 检查计算是否完成
- get: 阻塞等待其完成，并检索其完成结果的方法
- cancel: 提供取消方法，计算一旦完成，则不可取消任务

如果只是为了给任务拥有可取消的特性，可以不提供结果，直接声明Future<?>类型，并提供null作为结果

```java
public interface Future<V> {
    // 该方法如果返回true，则isDone()肯定也会返回true
    // mayInterruptIfRunning如果为true，则可能会中断正在计算的任务
    boolean cancel(boolean mayInterruptIfRunning);

    // cancel如果成功，则返回true
    boolean isCancelled();

    boolean isDone();

    // 阻塞等待任务结果，异步计算完成才会得到最终结果
    V get() throws InterruptedException, ExecutionException;

    // 限时等待结果获取，如果在给定时间内没计算完，则抛出超时异常
    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
```

## **1.5 RunnableFuture<V>**

FutureTask重写的接口，其继承了Runnable和Future接口，拥有了两者的特性，即可获取异步计算结果的任务

因为继承了Runnable接口，FutureTask可以作为Thread类的构造器参数传入，进行初始化

```java
public interface RunnableFuture<V> extends Runnable, Future<V> {
    // 设置实现该接口的异步计算任务的结果，除非它被取消了
    void run();
}
```

    RunnableFuture的run方法多赋予了新的意义，即设置异步计算的结果

# **2. FutureTask**

FutureTask重写了RunnableFuture接口，拥有Runnable、Future接口的行为特性

一篇总结得很好的博客：

    Doug Lea在设计很多并发工具的时候都使用了相似的思想，即AQS的设计思想，大致分为：
    控制生命周期的状态State，存放等待线程的队列，贯穿整个state的修改和线程的出队入队的CAS，当然还有在用CAS常常伴随着的自旋和volatile

所以我们关注的点主要有：runner的设置、waiters的安全加入栈、FutureTask状态更迭的安全性，以及等待者的等待唤醒策略

```java
public class Future<V> implements RunnableFuture<V> {
    // 任务执行的主体，Callable.call，带有返回结果
    private Callable<V> callable;

    // callable的结果，可能为任务返回值或异常
    // 通过FutureTask.set()/FutureTask.setException()设置结果
    private Object outcome;

    // 执行任务的线程，在run/runAndReset时赋值，主要用于cancel
    private volatile Thread runner;

    // 等待队列的头结点，记录的是等待执行结果的线程，可以多个（使用Treiber Stack线程安全的队列）
    private volatile WaitNode waiters;

    // CAS
    // Doug Lea最喜欢用的，简直传统艺能
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            // 取出state/runner/waiters字段在类信息中的位移地址
            stateOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
```

## **2.1 状态更迭**

```java
public class FutureTask<V> implements RunnableFuture<V> {
    // FutureTask的状态
    private volatile int state;
    private static final int NEW = 0; // 初始化
    private static final int COMPLETING = 1; // 任务执行完成，但正在设置返回值/异常
    private static final int NORMAL = 2; // 返回值设置完毕，任务真正结束
    private static final int EXECPTIONAL = 3; // 异常设置完毕，任务真正结束
    private static final int CANCELLED = 4; // 任务被取消
    private static final int INTERRUPTING = 5; // 任务中断成功，但正在设置中断值
    private static final int INERRUPTED = 6; // 任务中断完毕
}
```
注释翻译：

    FutureTask的运行状态，最初是NEW状态。仅在set、setException和cancel中转换为终端状态（NORMAL/EXCEPTIONAL/CANCELLED）
    
    在计算的期间，状态可能呈现COMPLETING或INTERRUPTING的瞬间值，前者出现在设置结果时，后者出现在cancel(true)中断运行时

    从中间状态变为最终状态，会使用更低开销的顺序或者延迟写入，因为值是唯一的，状态不会再被进一步修改

    可能的状态更迭有：
    NEW -> COMPLETING -> NORMAL
    NEW -> COMPLETING -> EXCEPTIONAL
    NEW -> CANCELLED
    NEW -> INTERRUPTING -> INTERRUPTED

从注释中我们可以将状态分为以下四类：
- 初始状态：NEW
- 中间状态：COMPLETING、INTERRUPTING
- 终端状态：NORMAL、EXCEPTIONAL、CANCELLED
- 中断状态：INTERRUPTED

只有在NEW -> 中间/终端状态的这个过程存在竞态条件，**使用CAS保证其并发安全**

中间状态 -> 终端状态，使用了**更低开销的顺序或者延迟写入**，而不是使用CAS，这是因为这个过程不存在竞态条件，它们的最终态都是可确定的
- 任务到达COMPLETING，其实已经执行完成（不能再被中断和取消），下一个状态可以根据任务的结果进行确定，变为NORMAL/EXCEPTIONAL
- 任务到达INTERRUPTING，其实已经被中断了（不能再被取消和完成），下一个状态确定且唯一

## **2.2 WaitNode**

WaitNode是一个Treiber stack，是一种**无锁并发栈**，其无锁的特性是基于CAS原子操作实现的

它是一个**单向链表**，链表头部即栈顶元素，在入栈和出栈的过程中，需要对栈顶元素进行CAS控制，保证线程安全

> [理解与使用Treiber Stack](https://www.cnblogs.com/mrcharleshu/p/13227895.html)

```java
static final class WaitNode {
    volatile Thread thread;
    volatile WaitNode next;
    WaitNode() {
        threand = Thread.currentThread();
    }
}
```

WaitNode栈用来维护等待任务完成的线程：
- 在调用任务的get()后，若任务尚未完成，则会新增一个WaitNode节点，入栈
- 在任务完成/取消/异常后，会**在finishCompletion钩子方法中唤醒栈中等待的线程**，使得节点出栈

```java
// 出/入栈的核心方法
private int awaitDone(boolean timed, long nanos) throw InterruptedException {
    final long deadline = timed ? System.nanoTime() + nanos : 0L;
    WaitNode q = null;
    boolean queued = false;
    for (;;) {
        // 中断处理，出栈
        if (Thread.interrupted()) {
            removeWaiter(q);
            throw new InterruptedException();
        }

        int s = state;
        if (s > COMPLETING) {
            // 进入终端状态NORMAL/EXCEPTIONAL/CANCELLED
            if (q != null)
                q.thread = null;
            return s;
        }
        else if (s == COMPLETING)
            // 即将完成，已经在设置返回值了
            Thread.yield();
        else if (q == null)
            q = new WaitNode();
        else if (!queued)
            // 当前线程节点入栈，并设置next指针指向之前的栈顶
            queued = UNSAFE.compareAndSwapObject(this, waitersOffset, q.next = waiters, q);
        else if (timed) {
            nanos = System.nanoTime() - deadline;
            if (nanos <= 0L) {
                removeWaiter(q);
                // 超时返回
                return state;
            }
            LockSupport.parkNanos(this, nanos);
        } else
            LockSupport.park();
    }
}
```

## **2.3 关键行为**

结合2.2的awaitDone()对WaitNode的使用，罗列以下关键行为，它们一同串起了整个**FutureTask状态的更迭**和**线程的出栈入栈**

### **2.3.1 run()/runAndReset() : 唤醒等待线程出栈，执行计算**

    NEW -> COMPLETING -> NORMAL/EXCEPTIONAL

线程被分配cpu时间片后，执行start0()方法，转而执行Runnable.run方法，设置结果或异常，再唤醒等待栈中所有线程

在ScheduledThreadPool.ScheduledFutureTask中，重写了run方法，会根据不同需求调用FutureTask.run/FutureTask.runAndReset

```java
public void run() {
    // 任务无法被执行多次
    // 一旦有线程进入了run方法，则其他调用run方法的线程将直接return
    if (state != NEW || !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread()))
        return;
    try {
        Callable<V> c = callable;
        // re-check，任务可能被取消
        // 提前返回而言，计算过程仍可能被取消
        if (c != null && state == NEW) {
            V result;
            boolean ran;
            try {
                result = c.call();
                ran = true;
            } catch (Throwable ex) {
                result = null;
                ran = false;
                // 进入中间状态COMPLETING
                setException(ex);
            } finally {
                if (ran)
                    // 进入中间状态COMPLETING
                    set(result);
            }
        }
    } finally {
        // 上面的流程已经将state修改了，这里置空runner相当于释放锁资源
        runner = null;

        // runner置空后必须重新读取状态，防止泄露的中断
        // 上面的过程可能被中断，在这里等待cancel执行完毕，状态变为INTERRUPTED
        int s = state;
        if (s >= INTERRUPTING)
            handlePossibleCancellationInterrupt(s);
    }
}
```

从上面的逻辑可以看出，除非在未开始之前就被拦截，否则即使成功取消任务，任务仍会进行计算，只是**计算后的结果无法设置到outcome**上：

```java
protected void set(V result) {
    // 这里对应cancel()方法的CAS竞争(NEW, CANCELED/INTERRUPTING)
    if (UNSAFE.compareAndSwapObject(this, stateOffset, NEW, COMPLETING)) {
        // 设置计算的结果
        outcome = result;
        // 上文已经分析过，后续的状态已经确定，且不会产生竞争，可以用串行/延迟设置state
        UNSAFE.putOrderInt(this, stateOffset, NORMAL);
        // 唤醒等待栈的线程
        finishCompletion();
    }
}
```

setException会将异常设置到outcome中，这也意味着执行线程在遇到问题时无法抛出异常，只能通过future.get()获得异常情况，或在执行代码中捕获异常

具体内容可以看：
> [线程的异常捕获](http://8.135.101.145/archives/threadpoolexception)

```java
protected void setException(Throwable t) {
    if (UNSAFE.compareAndSwapObject(this, stateOffset, NEW, COMPLETING)) {
        // 设置异常
        outcome = t;
        UNSAFE.putOrderInt(this, stateOffset, EXCEPTIONAL);
        // 唤醒等待栈的线程
        finishCompletion();
    }
}
```

因为**FutureTask的中间状态一旦确定后，其终端状态就可以进行确定**，这里可以直接唤醒等待栈的线程。所以判断任务是否完成完毕，只要判断状态是否为NEW即可

```java
// 保证只有一个线程去唤醒队列（signal/signalAll/condition.signal只能在独占锁的情况下去唤醒），防止出现并发问题导致有的线程无法被唤醒
private void finishCompletion() {
    // CAS失败了，则再循环一次，直到waiters == null，则意味已经有线程在唤醒了
    for (WaitNode q; (q = waiters) != null) {
        // 出栈CAS，保证只有一个线程操作栈，将栈顶设置为空
        if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
            for (;;) {
                Thread t = q.thread;
                if (t != null) {
                    q.thread = null;
                    LockSupport.unpark(t);
                }
                WaitNode next = q.next;
                if (next = null)
                    break;
                q.next = null; // help GC
                q = next;
            }
            break;
        }
    }
}
```

### **2.3.2 cancel(boolean mayInterruptIfRunning)：取消任务执行，线程出栈**

成功取消任务后，状态只会变为CANCELLED或INTERRUPTED

    cancel(true)：NEW -> CANCELLED
    cancel(false)：NEW -> INTERRUPTING -> INTERRUPTED (状态确定，延迟设置)

```java
public boolean cancel(boolean mayInterruptIfRunning) {
    if (state != NEW || !UNSAFE.compareAndSwapObject(this, stateOffset, NEW, mayInterruptIfRunning ? INTERRUPTING : CANCELLED))
        return false;
    try {
        if (mayInterruptIfRunning) {
            try {
                Thread t = runner;
                if (t != null)
                    t.interrupt();
            } finally {
                UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
            }
        }
    } finally {
        // 唤醒所有等待结果的线程
        finishCompletion();
    }
    return true;
}
```

### **2.3.3 get()：调用线程等待执行结果，线程入栈**

```java
public V get() throws InterruptedException, ExecutionException {
    int s = state;
    // NEW，COMPLETING：正在计算/计算完成，但结果尚未设置
    if (s <= COMPLETING)
        s = awaitDone(false, 0L);
    return report(s);
}

private V report(int s) throws ExecutionException {
    Object x = outcome;
    if (s == NORMAL)
        // NOW -> NORMAL
        // 计算完成，没有出错，返回结果
        return (V)x;
    if (s >= CANCELLED)
        // NOW -> CANCELLED
        // NOW -> INTERRUPTING -> INTERRUPTED
        throw new CancellationException();
    // NOW -> EXCEPTIONAL
    // s == EXCEPTIONAL，处理异常，抛出异常
    throw new ExecutionException((Throwable)x);
}
```

调用get()时，如果任务未执行完毕，会进入任务的等待栈中，等待任务异步唤醒，即调用线程会同步阻塞

## **2.4 获取状态的方法**

成功取消任务后，状态只会变为CANCELLED或INTERRUPTED

```java
public boolean isCancelled() {
    // CANCELLED/INTERRUPTING/INTERRUPTED
    return state >= CANCELLED;
}
```

只要状态不为NEW，则都可以认为任务执行已经结束，**即中间状态也认为是done了**，但是可能此时还未能获取到结果和异常

```java
public boolean isDone() {
    return state != NEW;
}
```

# 参考
- [线程的生命周期](http://8.135.101.145/archives/thead)
- [线程的异常捕获](http://8.135.101.145/archives/threadpoolexception)

# 重点参考
- [深入理解 异步任务执行类FutureTask源码分析](https://blog.csdn.net/wl12346/article/details/103818451)
- [理解与使用Treiber Stack](https://www.cnblogs.com/mrcharleshu/p/13227895.html)
- [java 中断线程的几种方式 interrupt()](https://www.cnblogs.com/myseries/p/10918819.html)