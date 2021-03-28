# CountDownLatch

## **1. Sync extends AQS**

```java
public CountDownLatch {
    private static final class Sync extends AbstractQueuedSynchronizer {
        Sync(int count) {
            // 设置共享锁的最大资源数
            setState(count);
        }

        int getCount() {
            return getState();
        }

        protected int tryAcquireShared(int acquires) {
            // 资源等于0，说明被countDown到0，可以在doAcquireShared中获取到资源；反之，则阻塞挂起
            return (getState() == 0) ? 1 : -1;
        }

        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c - 1;
                if (compareAndSetState(c, nextc))
                    return true;
            }
        }
    }
}
```

## **2. CountDownLatch**

调用该方法后，会直接进入同步队列，不会去获取资源

随后会调用aqs的tryAcquireShared方法，在未被countDown至0的情况下，线程阻塞并挂起

### **2.1 await()**

```java
public void await() throws InterruptedException {
    // 调用aqs的可中断，共享资源获取方法
    sync.acquiredShareInterrutibly(1);
}

//AQS.java
public void acquiredShareInterruptiby(int acquires) {
    if (Thread.isInterrupted())
        throw new InterruptedException();
    else
        // acquires == 1, 这个个数其实不要紧
        doAcquiredShareInterruptibly(acquires);
}

protected void doAcquiredShareInterruptibly(int arg) throw InterruptedException {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = false;
    for (;;) {
        final Node p = node.processors();
        if (p == head) {
            // 如果latch的state仍不为0，这里必定得到r = -1
            int r = tryAcquiredShared(arg);
            if (r >= 0) {
                // 当latch被countdown到0时，会唤醒头节点的后续线程，同步队列的线程将被一直传递唤醒，并出队

                // 设置头节点，唤醒后续节点等操作
            }
        }

        // 线程挂起
    }
}
```

### **2.2 countDown()**

调用该方法后，AQS的资源数-1，并且会唤醒后续线程

```java
public void countDown() {
    sync.releaseShared(1);
}

// aqs.java
public void releaseShared(int acquires) {
    if (tryReleaseShared(acquires)) {
        doReleaseShared(acquires);
        return true;
    }
    return false;
}

public void doReleaseShared(int arg) {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            // 唤醒同步队列头节点的后续节点
            unparkSuccessors(h);

            if (h == head)
                break;
        }
    }
}
```

```java
// 五个线程需要都计算完任务后，再一起进行下一步操作
public class CountDownLatchDemo {
    protected static CountDownLatch latch = new CountDownLatch(1);

    protected List<Task> taskList = new ArrayList<>();

    static class Task implements Runnable {
        private volatile boolean ready;

        private String taskName;

        public Task(int index) {
            this.taskName = "Thread " + index;
        }

        @Override
        public void run() {
            try {
                System.out.println(taskName + " is " + "ready");
                ready = true;
                latch.await();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

        public boolean isReady() {
            return ready;
        }
    }

    public boolean isAllReady() {
        for (Task task : taskList) {
            if (!task.isReady()) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        CountDemo countDemo = new CountDemo();
        for (int i = 0; i < 5; i++) {
            Task task = new Task(i);
            countDemo.taskList.add(task);
            new Thread(task).start();
        }

        while (!countDemo.isAllReady()) ;

        latch.countDown();
        System.out.println("fuck that shit");
    }
}
```

# 参考
- [Java Semaphore/CountDownLatch/CyclicBarrier 深入解析(原理篇)](https://www.jianshu.com/p/4556f0f3b9cb)