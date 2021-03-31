# CyclicBarrier

又称栅栏，常常被拿来与CountDownLatch闭锁做对比，闭锁一般由其他线程驱动；栅栏则是业务线程根据情况自行驱动

模拟场景：
几个人想去某个地方，约定到了某个地方集合，人都到了再一起出发
- 每个人到达集合点时，就看看人齐了没，没有则等待
- 若最后一个参与者过来发现人也齐了，于是唤醒其他的全部人，一起出发

![](https://img-blog.csdnimg.cn/20201229140729713.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3UwMTMxNjEyNzg=,size_16,color_FFFFFF,t_70)

## **1. 栅栏的state**

与其他的同步器直接组合aqs实现子类相比，栅栏组合了可重入锁，利用其独占性质来协调其他线程进行等待

    没有直接修改state，CyclicBarrier是通过ReentrantLock + Condition来实现线程间同步的

## **2. 基础类和接口**

```java
public class CyclicBarrier {
    private static class Generation {
        boolean broken = false;
    }

    // 用于控制栅栏入口的实体
    private final ReentrantLock lock = new ReentrantLock();
    
    // 等待栅栏打开的等待队列
    private final Condition trip = lock.newCondition();

    // 栅栏的参与者数, 到达这个数量则栅栏打开
    private final int paries;

    // 当栅栏打开时，执行的指令
    private final Runnable barrierCommand;

    private Generation generation = new Gernaration();

    private int count;

    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }
}
```
### **2.1 迭代**

Generation是CyclicBarrier的内部静态类，描述了CyclicBarrier的**更新换代**。在CyclicBarrier中，**同一批线程属于同一代**，当有parties个线程全部到达barrier时，generation就会更新为下一个迭代

其内部属性broken，记录的是当前迭代是否已经出现失效问题

```java
private static class Generation {
    boolean broken = false;
}
```

迭代失效/损坏栅栏(g.broken == true)的四种情况：

1. 在barrier等待中的某个线程被中断，中断的线程将将栅栏损坏(g.broken = true)，其他所有线程被唤醒，并检测到损坏，抛出BrokenBarrierException

2. 外部线程调用了reset()方法重置栅栏，则栅栏的全部线程都将抛出BrokenBarrierException

3. 超时机制下，当加入等待队列的线程限时唤醒后，发现栅栏已经超时，则损坏栅栏

4. 最后一个线程在执行传入栅栏的回调方法执行出现异常，则损坏栅栏，并唤醒其他所有线程

### **2.2 获取独占资源**

每一个调用CyclicBarrier的await方法的线程，都会在一开始获得独占锁，并且判断是否参与者已经到齐，没有则将自身加入到等待队列；有则唤醒其他线程，并重置栅栏到下一个迭代

```java
public int await() {
    try {
        return dowait(false, 0L);
    } catch (TimeoutException toe) {
        throw new Error(toe);
    }
}

private int dowait(boolean timed, long nanos) {
    final ReentrantLock lock = this.lock;
    // 独占锁获取, 锁住资源，同一时刻竞争的线程进入同步队列
    lock.lock();

    try {
        // 获取当前栅栏的迭代
        final Generation g = generation;

        // 当前栅栏处于损坏状态（如果有线程处于等待状态，其他线程调用reset()方法，则在reset中调用breakBarrier打破栅栏）
        if (g.broken)
            throw new BrokenBarrierException();

        // 中断，其他线程抛出BrokenBarrierException
        if (Thread.interrupted()) {
            breakBarrier();
            throw new InterruptedException();
        }

        // 参与者到了一个，则count自减，index为自减前的值
        int index = --count;
        if (index == 0) {
            // 都到齐了，无需等待
            boolean ranAtion = false;
            try {
                final Runnable command = barrierCommand;
                if (command != null)
                    command.run();
                ranAction = true;
                // 开始下一轮, 重置栅栏
                nextGeneration();
                return 0;
            } finally {
                if (!ranAction)
                    // command.run出异常了，则毁掉屏障
                    breakBarrier();
            }
        }

        // 走到这，则意味着栅栏的参与者还未全部到齐，自旋操作
        for (;;) {
            try {
                // 挂起加入到等待队列中
                if (!timed)
                    // 若无超时机制，则无限期挂起
                    trip.await();
                else if (nanos > 0L)
                    // 限期挂起, awaitNanos会返回剩余的nanos
                    nanos = trip.awaitNanos(nanos);
            } catch (InterruptedException ie) {
                // 等待过程中被其他线程中断
                if (g == generation && !g.broken) {
                    // 仍然是同一个栅栏内，并且该迭代未被broken过
                    // 在等待期间被其他线程打断，则毁掉栅栏，并往上抛出异常
                    breakBarrier();
                    throw ie;
                } else {
                    Thread.currentThread().interrupt();
                }
            }

            // 被唤醒后，发现栅栏已经坏了，则抛出异常
            if (g.broken)
                throw new BrokenBarrierException();

            // 被唤醒后，发现栅栏已经迭代，当前线程不是这一批次的了
            if (g != generation)
                return index;

            if (timed && nanos <= 0L) {
                // 若有超时机制，且超时，则毁掉栅栏
                breakBarrier();
                throw new TimeoutException();
            }
        }
    } finally {
        // 以上流程完成，解锁
        lock.unlock();
    }
} 
```

### **2.3 重置**

```java
public void reset() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 立即损坏栅栏，并唤醒在当前迭代等待的所有线程
        breakBarrier();

        // 转入到下一个迭代中
        nextGeneration();
    } finally {
        lock.unlock();
    }
}
```

### **3. DEMO**

# 参考
- [CyclicBarrier原理剖析](https://blog.csdn.net/u013161278/article/details/111881393)
- [Java Semaphore/CountDownLatch/CyclicBarrier 深入解析(原理篇)](https://www.jianshu.com/p/4556f0f3b9cb)