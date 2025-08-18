# Interview Four ：线程池

- 核心参数和成员：corePoolSize核心线程数、maximumPoolSize最大线程数、keepAliveTime空闲线程存活时间、Policy拒绝策略、Queue工作队列

- 执行流程、线程回收流程、拒绝策略

- 使用5大参数定制的各种Executor

    - cachedThreadPool：设置core为0，max为整型最大值，通过SynchronousQueue来复用空闲线程

    - TaskQueue：tomcat的队列，通过改写队列的offer方法，重新定制ThreadPoolExecutor的投递行为

    - 无界队列：在默认的队列offer方法规则下，线程池的线程数将不会超过core数量

    - 小池大队列/大池小队列：前者期望更充分利用cpu，后者期望降低对cpu的使用

- ScheduledThreadThreadPoolExecutor定时调度器

线程池用于消除任务和线程之间的耦合关系，重复利用线程可以降低线程的创建销毁开销，并提供动态调整机制以适应实时变化的系统情况

# **核心参数**

- maximumPoolSize：池最大线程数目，线程池的最大线程数不能超过该值

- corePoolSize：核心线程数，即线程池的常驻线程个数，池内线程数会经历从0到corePoolSize的过程

- `keepAliveTime`：线程存活时间，指的是在**达到keepAliveTime时间后，且timeout为true**（默认allowCoreThreadTimeout为false），超出corePoolSize的那部分线程将被回收

    - 是否为核心的判断，只在addWorker时生效，Worker中并没有core标志。所以在收敛线程个数时，不区分核心，每个线程都可能被回收。

- workQueue：任务队列。线程池属于生产-消费模型，生产方指调用submit方，消费方指线程池中的线程，而workQueue则充当两者之间的任务队列，解耦生产者和消费者之间的强对应关系

- RejectedExecutionHandler：当池内无法再容纳更多任务时，会对新来的任务执行拒绝策略

5大核心参数可以搭配出拥有不同特性的线程池，不推荐使用Executor

# **执行流程**

![ThreadPoolExecutor#execute()](https://images2015.cnblogs.com/blog/677054/201704/677054-20170408210905472-1864459025.png)

execute()流程如下：

1. 当前线程数 < corePoolSize，则新建core线程并执行该提交任务

2. 当前线程数 == corePoolSize，任务进入workQueue中等待线程消费

    - 如果添加队列成功，但当前没有工作线程，则需要添加一个**无firstTask的非核心线程**

3. workQueue已满，则继续创建（maximumPoolSize - corePoolSize）数量个线程执行任务，如果任务执行完成，则额外创建的（maximumPoolSize - corePoolSize）个线程将**限时阻塞在队列上等待keepAliveTime时长**后被回收

## **ctl**

使用原子整型类（AtomicInteger）来维护**线程池的状态**、**池中线程个数**
- 高3位：线程池状态
- 低29位：工作线程数

## **workQueue**

常使用的阻塞队列分为以下四种：

1. 同步队列：SynchronusQueue

    - 效果：同步队列**本身没有含量**不保存任务，当消费线程不够时会创建新的线程进行执行，直到达到maximumPoolSize后执行拒绝策略

    - 使用场景：类似Executors.newCachedThreadPool

2. 无界队列：不带参数构造器的LinkedBlockingQueue

    - 效果：队列永远不会满，可以一直添加任务，`有助于消除瞬时的请求突发`

        使用无界队列意味着不会出现线程超过corePoolSize的情况，当业务繁忙时新任务都会在队列中等待，**不会触发额外的线程创建**

    - 使用场景：适用于每个任务完全独立于其他的任务，任务互相之间不会有影响，例如业务web服务器中

    - 缺点：一直投递会无限制增长，可能会出现`OOM`

    - Executors：SingleThreadExecutor

3. 有界队列：ArrayBlockingQueue(int)、LinkedBlockingQueue(int)

    - 效果：搭配maximumPoolSize和队列大小，可以灵活获得不同特性的线程池，并**有效防止资源耗尽**

        - 固定消费速率：大容量队列 + 较小maximumPoolSize（小池），降低CPU使用、操作系统分配的线程资源和线程之间切换的开销

            大队列**更容易形成任务滞留在队列中**，从而**降低**新起额外线程的可能性

        - 提高消费速率：小容量队列 + 较大maximumPoolSize（大池），提高cpu使用率

            小队列**更容易触发工作队列满的条件**，从而**增大**新起额外线程的可能性

    - 缺点：需要根据实际情况调整和控制，大队列小池可能人为导致**低吞吐量**，小队列大池可能创建过多线程导致更严重的**线程上下文切换开销**

4. 优先级队列：PriorityBlockingQueue

    - 效果：一般用最大堆实现，即**队首必定是优先级最高的任务**

    - 场景：ScheduledThreadPoolExecutor

5. **tomcat队列**：TaskQueue（extends LinkedBlockingQueue）

    - 规则：将超过corePoolSize数的线程视作**空闲线程**，当前若有空闲线程（任务计数值 <= 线程池线程数），才能投递任务到队列中；否则新起工作线程，并将该任务作为新起线程的第一个任务。

        > 修改线程数到达corePoolSize才投递队列的规则，普通池在队列满后才开始新增线程数，**对于流量增长不够敏感**
        
        即使tomcat使用无界LinkedBlockingQueue，也能根据新规则消除无法使用超过corePoolSize线程的限制（大部分情况为有界，防止OOM）

    - 效果：该规则下的tomcat线程池会更**快速地创建线程资源**来应对突发大流量，而不是等待队列满后才开始创建线程。`根据空闲线程数量来决定是投递队列还是新起线程，对于流量变化有更强的适应性`

        ```java
        public boolean offer(Runnable o) {
            //we can't do any checks
            if (parent==null) return super.offer(o);

            // 到达最大线程数时，直接投递到队列中（存在OOM的可能）
            if (parent.getPoolSize() == parent.getMaximumPoolSize()) 
                return super.offer(o);

            // 正在处理的任务数，如果小于等于线程池线程数，则认为总有空闲的线程可以处理，投递队列
            if (parent.getSubmittedCount()<=(parent.getPoolSize())) 
                return super.offer(o);
            
            // 如果没有空闲线程，且还没到达最大线程数，直接返回false，并由线程池的execute机制创建新线程
            if (parent.getPoolSize()<parent.getMaximumPoolSize()) 
                return false;

            // 不会走到这来
            return super.offer(o);
        }
        ```
        
## **Worker**

继承自AQS，实现类似ReentrantLock的同步资源管理，通过资源的占用来**表示当前工作线程的工作状态**：

- -1：刚初始化状态，不可以被中断

- 0：idle状态，说明正在等待任务，此时可以中断线程

- 1：工作状态，`正在执行任务时则不应该被中断`

中断线程：调用`interruptIdleWorker（）`（shutdown()或tryTerminate()都会触发）中断空闲的线程，空闲的依据是**tryLock()方法是否成功**

不可重入：主要应对当前正在执行任务的线程，若其任务内容是中断该线程所在线程池，由于正在执行任务的线程不应被中断，可以**防止其不受该中断逻辑的影响**

> 任务内容是调用该线程所在线程池的interruptIdleWorker()方法，那么假设线程是可重入的，tryLock()将会对一个可重入锁返回true（计数器加1），并中断自身线程的任务，这不符合语义：正在执行任务时不应该被中断

## **拒绝策略**

- AbortPolicy：拒绝策略，抛出拒绝异常

- DiscardPolicy：丢弃策略，空操作

- DiscardOldestPolicy：替换最久的任务策略，将队头任务替换成新任务

- CallerRunsPolicy：**调用者执行策略，直接让调用者线程执行**

# **Executors**

- SingleThreadExecutor

- FixedThreadPool：固定线程数线程池

    - 队列：无界LinkedBlockingQueue

    - 线程数：固定线程数corePoolSize，maximumPoolSize设置不生效

    - 思想：用于在已知并发压力情况下，对线程数做限制

    - 缺点：队列长度没有边界，如果有大量的任务请求到达，会出现队列过长导致程序OOM

- CachedThreadPool：缓存线程池

    - 队列：SynchronousQueue（公平模式TransferQueue、非公平模式TransferStack）

    - 线程数：没有固定线程（corePoolSize为0），最大线程数能到maximumPoolSize

    - 思想：当没有新线程时会创建新线程，由空闲线程时直接复用，线程数会根据系统实时业务情况动态调整，适合**耗时较短的任务**

    - 缺点：线程数目没有边界，如果任务耗时较长且并发量过大，会启动大量线程从而导致OOM

    > 队列没有长度不会OOM

# **ScheduledThreadPoolExecutor**

- 队列：DelayedWorkQueue（最大堆优先级队列，使用**ReentrantLock保证线程安全**）

- 线程数：固定线程数corePoolSize，maximumPoolSize设置不生效

- 思想：定时调度，一般由程序自发行为添加定时任务，自产自销情况下一般不会出现消费不完的问题

- 缺点：队列默认值为Integer.MAX_VALUE，当使用不当时仍会出现OOM情况，如重复提交调度任务

## 美团动态线程池方案（DynamicTp）

为解决传统线程池参数静态配置的缺陷，美团推出 DynamicTp，支持运行时动态调整与实时监控2610：

核心功能：

- **动态调参**：
无需重启服务，实时修改 corePoolSize、maxPoolSize、队列容量等参数。例如电商大促时自动扩容线程池。

- 实时监控：
采集活跃线程数、队列堆积、拒绝任务数等指标，集成 Grafana 可视化看板。

- 多级报警：
支持钉钉/企业微信通知，例如队列使用率 >80% 或拒绝任务数突增时触发报警。

- 零侵入接入：
通过注解或配置文件集成，兼容 Spring Boot、Dubbo 等框架。

## 实践
1. 通过压测确定基准值，并且区分CPU/I/O密集型任务（前者需要减少线程开销，后者需要更多线程提升吞吐），并根据队列和池线程的关系来达到预期效果（大池小队列适合I/O密集，小池大队列适合cpu密集。tomcat为了使得线程的新增能堆流量更敏感，甚至还重写了队列方法，引入空闲线程和当前任务数的概念）
2. 拒绝策略至少使用CallerRunPolicy，队列尽量用有界队列防止OOM
3. 使用美团的DynamicTip，实现动态扩容/缩容功能，可以根据流量动态设置线程池核心参数
4. 集成Sentinel，在过载时进行熔断，防止雪崩

# 参考

- [线程池拒绝策略分别使用在什么场景](http://www.javashuo.com/article/p-xzcmklmk-nd.html)

- [马士兵教育郑金维 - ThreadPoolExecutor](https://www.bilibili.com/video/BV1mK411U78X?p=50&vd_source=24d877cb7ef153b8ce2cb035abac58ed)