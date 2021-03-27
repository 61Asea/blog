# CountDownLatch

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