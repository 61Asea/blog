# Interview Four ：线程池

- 核心参数和成员

- 执行流程、线程回收流程、拒绝策略

- 使用5大参数定制的各种Executor

- ScheduledThreadThreadPoolExecutor定时调度器

线程池用于消除任务和线程之间的耦合关系，重复利用线程可以降低线程的创建销毁开销，并提供动态调整机制以适应实时变化的系统情况

# **核心参数**

- maximumPoolSize：池最大线程数目，线程池的最大线程数不能超过该值

- corePoolSize：核心线程数，即线程池的常驻线程个数，池内线程数都会经历从0到corePoolSize的过程

- `keepAliveTime`：线程存活时间，指的是在**达到keepAliveTime时间后，且allowCoreThreadTimeout为true**，超出corePoolSize的那部分线程将被回收

- workQueue：任务队列。线程池属于生产-消费模型，生产方指调用submit方，消费方指线程池中的线程，而workQueue则充当两者之间的任务队列，解耦生产者和消费者之间的强对应关系

- RejectedExecutionHandler：当池内无法再容纳更多任务时，会对新来的任务执行拒绝策略

5大核心参数可以搭配出拥有不同特性的线程池，具体见`Executors`

# **执行流程**

![ThreadPoolExecutor#execute()](https://images2015.cnblogs.com/blog/677054/201704/677054-20170408210905472-1864459025.png)

execute()流程如下：

1. 当前线程数 < corePoolSize，则新建core线程并执行该提交任务

2. 当前线程数 == corePoolSize，任务进入workQueue中等待线程消费

3. workQueue已满，则继续创建（maximumPoolSize - corePoolSize）数量个线程执行任务，如果任务执行完成，则额外创建的（maximumPoolSize - corePoolSize）个线程将**限时阻塞在队列上等待keepAliveTime时长**后被回收

## **ctl**



## **workQueue**

## **worker**

## **拒绝策略**

# **Executors**

- SingleThreadExecutor

- FixedThreadPool

- CachedThreadPool

# **ScheduledThreadPoolExecutor**

# 参考