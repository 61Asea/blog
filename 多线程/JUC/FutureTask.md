# FutureTask

> [线程的生命周期](http://)

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

> [线程的生命周期]()

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

# **2. FutureTask**

## **2.1 FutureTask相关接口**

## **2.1.1 Future<V>**

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

## **2.1.2 RunnableFuture<V>**

FutureTask重写的接口，其继承了Runnable和Future接口，拥有了两者的特性，即可获取异步计算结果的任务

因为继承了Runnable接口，FutureTask可以作为Thread类的构造器参数传入，进行初始化

```java
public interface RunnableFuture<V> extends Runnable, Future<V> {
    // 设置实现该接口的异步计算任务的结果，除非它被取消了
    void run();
}
```

    RunnableFuture的run方法多赋予了新的意义，即设置异步计算的结果

## **2.2 基础设施**

## **2.2.1 FutureTask的状态**

```java
public class FutureTask<V> implements RunnableFuture<V> {
    // FutureTask的状态
    private volatile int state;
    private static final int NEW = 0;
}
```