# CyclicBarrier

又称栅栏，常常被拿来与CountDownLatch闭锁做对比，闭锁一般由其他线程驱动；栅栏则是业务线程根据情况自行驱动

模拟场景：
几个人想去某个地方，约定到了某个地方集合，人都到了再一起出发
- 每个人到达集合点时，就看看人齐了没，没有则等待
- 若最后一个参与者过来发现人也齐了，于是唤醒其他的全部人，一起出发

## **1. 栅栏的state**

与其他的同步器直接组合aqs实现子类相比，栅栏组合了可重入锁的独占性质，来协调其他线程进行等待

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

### **2.1 获取资源**

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
        final Generation g = generation;

        if (g.broken)
            throw new BrokenBarrierException();

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
                if (g == generation && !g.broken) {
                    // 仍然是同一个栅栏内，并且该迭代未被broken过
                    // 在等待期间被其他线程打断，则毁掉栅栏，并往上抛出异常
                    breakBarrier();
                    throw ie;
                } else {
                    Thread.currentThread().interrupt();
                }
            }

            if (g.broken)
                throw new BrokenBarrierException();

            if (g != generation)
                return index;

            if (timed && nanos <= 0L) {
                // 若有超时机制，且超时，则毁掉栅栏
                breakBarrier();
                throw new TimeoutException();
            }
        }
    } finally {
        lock.unlock();
    }
} 
```