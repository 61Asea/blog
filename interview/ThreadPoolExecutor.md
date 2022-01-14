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

使用原子整型类（AtomicInteger）来维护**线程池的状态**、**池中线程个数**，具体通过高3位记录状态，后29位记录线程个数

## **workQueue**

常使用的阻塞队列分为以下四种：

1. 同步队列：SynchronusQueue

    - 效果：同步队列**本身没有含量**不保存任务，当消费线程不够时会创建新的线程进行执行，直到达到maximumPoolSize后执行拒绝策略

    - 使用场景：Executors.newCachedThreadPool

2. 无界队列：不带参数构造器的LinkedBlockingQueue

    - 效果：队列永远不会满，可以一直添加任务，`有助于消除瞬时的请求突发`

        使用无界队列意味着不会出现线程超过corePoolSize的情况，当业务繁忙时新任务都会在队列中等待，**不会触发额外的线程创建**

    - 使用场景：适用于每个任务完全独立于其他的任务，任务互相之间不会有影响，例如网页服务器中

    - 缺点：无限制增长会出现OOM

3. 有界队列：ArrayBlockingQueue(int)、LinkedBlockingQueue(int)

    - 效果：搭配maximumPoolSize和队列大小，可以灵活获得不同特性的线程池，并**有效防止资源耗尽**

        - 固定消费速率：大队列 + 较小maximumPoolSize，降低CPU使用、操作系统分配的线程资源和线程之间切换的开销

            **大队列更容易形成任务堆积**，从而降低新起额外线程的可能性

        - 加速消费速率：小队列 + 较大maximumPoolSize，提高cpu使用率

            **小队列更容易触发队列已满的条件**，从而增大新起额外线程的可能性

    - 缺点：需要根据实际情况调整和控制，大队列小池可能人为导致**低吞吐量**，小队列大池可能创建过多线程导致更严重的**线程上下文切换开销**

4. 优先级队列：BlockingQueue（PriorityBlockingQueue）

    - 效果：一般用最大堆实现，即**队首必定是优先级最高的任务**

    - 场景：ScheduledThreadPoolExecutor

5. tomcat队列：TaskQueue（extends LinkedBlockingQueue）

    - 效果：将超过corePoolSize数的线程视作**空闲线程**，修改线程数满corePoolSize则直接投递到队列的规则，改为当前若有空闲线程，则投递任务，否则新起线程

        该规则下的tomcat线程池会更**提前创建线程资源**来应对突发流量，而不是等待队列满后才开始创建线程。`根据空闲线程数量来决定是投递队列还是新起线程，对于流量变化有更强的适应性`
        
        普通池在队列满后才开始新增线程数，对于流量增长不够敏感
        
        若使用无界LinkedBlockingQueue，则还能消除无法使用超过corePoolSize线程的限制（大部分情况为有界）
        
## **worker**



## **拒绝策略**

- AbortPolicy：拒绝策略，抛出拒绝异常

- DiscardPolicy：丢弃策略，空操作

- DiscardOldestPolicy：替换最久的任务策略，将队头任务替换成新任务

- CallerRunsPolicy：调用者执行策略，直接让调用者线程执行

# **Executors**

- SingleThreadExecutor

- FixedThreadPool

- CachedThreadPool

# **ScheduledThreadPoolExecutor**

# 参考

- [线程池拒绝策略分别使用在什么场景](http://www.javashuo.com/article/p-xzcmklmk-nd.html)