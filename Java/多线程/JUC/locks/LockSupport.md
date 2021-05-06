# LockSupport

等待过程中被中断了，需要判断中断标志并作出处理

没有清空中断标志的话，接下来的LockSupport.park将不会生效

```java
public class Main {
    static class Park implements Runnable {
        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName()+"开始阻塞");
            LockSupport.park();

            Thread.interrupted();

            System.out.println(Thread.currentThread().getName()+"第一次结束阻塞");
            for (int i = 0; i < 100; i++) {
                LockSupport.park();
                System.out.println("第"+ i +"次结束阻塞");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Thread t = new Thread(new Park());
        t.start();
        Thread.sleep(100); //①
        System.out.println(Thread.currentThread().getName()+"开始唤醒阻塞线程");
        t.interrupt();
//        LockSupport.unpark(t);
        System.out.println(Thread.currentThread().getName()+"结束唤醒");
    }
}

```