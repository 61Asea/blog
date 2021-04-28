# DelayedWorkQueue优先级阻塞队列

本文涵盖内容：优先级（堆）、互斥阻塞和生产消费对队列的行为

堆是实现优先级队列的最佳选择，而该队列正好是基于堆数据结构的实现。基于任务的sequence和time属性，可以获得具有任务优先级的队列。

DelayedWorkQueue借助ReentrantLock实现了**线程安全的队列容器**

```java
static class DelayedWorkQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {
    // 数组初始化大小
    private static final int INITIAL_CAPACITY = 16;
    // 底层实现是一个数组
    private RunnableScheduleFuture<?>[] queue = new RunnableScheduleFuture<?>[INITIAL_CAPACITY];
    // 显式锁，保证操作的线程安全
    private final ReentrantLock lock = new ReentrantLock();
    // 当前队列中有效任务的个数
    private int size = 0;

    // 是否有线程在等待第一个任务（因为还未到第一个任务的执行时间）
    // 有的话则调用线程直接await()，没有的话，则自己作为leader，并限时挂起，直到第一个任务的执行时间到达
    private Thread leader = null;

    // 实现阻塞和通知
    private final Condition available = lock.newCondition();

    // 在对任务进行移动时，判断其是否为ScheduledFutureTask实例，是的话则维护其在队列的下标
    private void setIndex(RunnableSchduledFuture<?> f, int idx) {
        if (f instanceof ScheduledFutureTask)
            ((ScheduledFutureTask)f).heapIndex = idx;
    }

    // 如果不是属于ScheduledFutureTask类型的，则遍历获得索引
    private int indexOf(Object x) {
        if (x != null) {
            if (x instanceof ScheduledFutureTask) {
                int i = ((ScheduledFutureTask) x).heapIndex;
                if (i >= 0 && i < size && queue[i] == x)
                    return i;
            } else {
                for (int i = 0; i < size; i++)
                    if (x.equals(queue[i]))
                        return i;
            }
        }
        return -1;
    }
    
    // 清空队列
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (int i = 0; i < size; i++) {
                RunnableScheduledFuture<?> t = queue[i];
                if (t != null) {
                    queue[i] = null;
                    setIndex(t, -1);
                }
            }
            size = 0;
        } finally {
            lock.unlock();
        }
    }

    // 获取队头
    public RunnableScheduledFuture<?> peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return queue[0];
        } finally {
            lock.unlock();
        }
    }

    // 其他方法在接下来的内容介绍
}
```

# **1. 堆**

**堆结构是一个二叉树，节点实际存储在数组中**，堆能保证最小/大的在队列头，但不能保证数组队列的每一项都按优先级排序

如果i是节点的索引，那么
- 父节点为：floor((i - 1) / 2))
- 左子节点为：2 * i + 1
- 右子节点为: 2 * i + 2

    题外话，如果n为二叉树的层数：
        
        二叉树总节点数
            2^n - 1
        
        二叉树每一层的节点数
            
            2^(n - 1)
        
        二叉树除子节点以外的所有节点数：
            二叉树子节点数 - 1 -> 2^(n - 1) -1

堆内元素节点位置，取决于节点的某一个属性的大小值。根据父节点是否大于子节点，分为最小堆和最大堆，即二叉树根节点最小则为最小堆，**根节点最大则为最大堆**
```java
// ScheduledFutureTask的比较方法
public int compareTo(Delayed other) {
    // 下次执行时间越早的，优先级越大
    // 相同下次执行时间的，序号越小的，优先级越大
    long diff = time - x.time;
    if (diff < 0) // 当前任务，比other更早执行
        return -1;
    else if (diff > 0) // 当前任务，比other晚执行
        return 1;
    else if (sequenceNumber < x.sequenceNumber) // 当前任务比ohter更早创建，则更早执行
        return -1;
    else
        return 1;
}

public static void main(String[] args) {
    int result = task.compareTo(otherTask);
    if (result == 0)
        System.out.println("两个任务优先级相等");
    else if (result > 0)
        System.out.println("task优先级低于otherTask");
    else
        System.out.println("task优先级高于otherTask");
}
```

判断最大/小堆将对我们的插入和删除操作有不同的理解：
- 最大堆在添加较小属性值的项时，往上挪的次数会较少或不需要挪
<!-- - 最大堆在删除较大属性值的项时，往下挪 -->
- 最小堆在添加较大属性值的项时，往上挪的次数会较少或不需要挪


注意上面的比较方法，我们将在下面结合堆插入和删除时的操作，判断这个堆是什么类型的堆

## **1.1 堆插入**
```java
// 传入的k代表新增的位置，上面的值由最终堆重排的最终结果决定
// while循环中的k会一直变化为当前节点的根节点
private void siftUp(int k, RunnableScheduledFuture<?> key) {
    while (k > 0) {
        // 父节点为：floor((i - 1) / 2))
        int parent = (k - 1) >>> 1;
        RunnableScheduledFuture<?> e = queue[parent];
        if (key.compareTo(e) >= 0)
            // 如果新增的task的优先级，比父task的低，则直接break，由此可见该堆是最大堆
            break;
        queue[k] = e;
        setIndex(e, k);
        k = parent;
    }
    queue[k] = key;
    setIndex(key, k);
}
```
由上可见，该堆是一个**以优先级作为属性值的最大堆**，根节点为优先级最高的任务

假设新增task4（task4的优先级比其他项都高），以当前队列情况为：[task1, task2, task3]，调用siftUp方法为例：

    当前队列有3项，k = 3，新增的任务为task4，执行siftUp(3, task4)

1. 第一轮循环

    查找新增位置k的父节点：int parent = (3 - 1) >>> 1 = 1;
    
    取出父节点的任务：e = queue[parent] = queue[1] = task2;
    
    判断父节点任务和task4的优先级，task4优先级更高
    
    将父节点挪到新增位置k：queue[k] = queue[3] = task2;
    
    重新设置：k = parent = 1;
    
    队列当前情况：[task1, task2, task3, task2]

2. 第二轮循环
    
    查找新的k的父节点：int parent = (1 - 1) >>> 1 = 0;
    
    发现是根节点：e = queue[parent] = queue[0] = task1;
   
    判断根节点任务和task4的优先级，task4优先级更高
    
    将根节点挪到k的位置：queue[k] = queue[1] = task1;
    
    重新设置：k = parent = 0;
    
    队列当前情况：[task1, task1, task3, task2]

3. task4入队
    发现k = 0，退出循环
    
    设置task4：queue[k] = queue[0] = task4
    
    队列最终情况：[task4, task1, task3, task2]

## **1.2 堆移除**

```java
// 此时的k代表要删除的任务的下标，key为替代删除的节点（一般为最后一个节点）
private void siftDown(int k, RunnableScheduledFuture<?> key) {
    // 题外话里面有讲到，这里获取到的是叶子节点的那一层的索引
    int half = size >>> 1;
    // k < half：往下挪的时候，元素的位置在叶子节点层之上
    while (k < half) {
        int child = (k << 1) + 1;
        RunnableScheduledFuture<?> c = queue[child];
        int right = child + 1;
        // right < size：防止没有右节点
        // c.compareTo(queue[right]) > 0: 从两个子节点找出最大的节点，这也证明了队列是最大堆实现；最小堆反之会找出最小的节点
        if (right < size && c.compareTo(queue[right]) > 0)
            c = queue[child = right];
        if (key.compareTo(c) <= 0)
            // 最后再比较一下最后一个节点与他们的大小，比他们大就停下来
            break;
        queue[k] = c;
        setIndex(c, k);
        k = child;
    }
    queue[k] = key;
    setIndex(key, k);
}
```

假设当前队列情况为:[task4, task1, task3, task2]，删除task4节点，调用siftDown方法为例：

1. 在执行siftDown前的准备工作(remove方法)

    设置要删除的节点的位置缓存: setIndex(queue[0], -1);
    
    队列长度先减一：size = size - 1 -> 4 - 1 = 3; 
    
    取出最后一个节点，缓存起来，并将其在队列置为空：
        replacement = queue[0]
        queue[0] = null -> queue[0] = null;
    
    如果队列删除的不是尾节点：调用siftDown(replacement) -> 执行siftDown(0, replacement(task2))

    队列详情为：[task4, task1, task3]

2. 第一轮循环

    获得叶子节点层的索引：int half = size >>> 1 -> 3 >>> 1 = 1;
    
    往下挪的位置，是否在叶子节点之上：while (k < half) -> while (0 < 1);
    
    获得节点的左和右子节点索引：int child = (k << 1) + 1 -> (0 << 1) + 1 = 1，int right = child + 1 = 2;
    
    比较左(task1)跟右(task3)的优先级，取更大的那个，这里假设task3更大：c = queue[child = right] -> c = task3, child = 2;
    
    比较更大的子节点(task3)与替换节点(task2)的大小，更大的那个占住k的位置：
    queue[k] = queue[0] = c;

    设置k的值为更大子节点的索引: k = child = 2;

    队列详情为：[task3, task1, task3]

3. 结束，退出循环

    发现 while(k < half) -> while(2 < 1)，退出
    
    设置k(child)位置为task2：queue[2] = task2;

    队列详情为：[task3, task1, task2]

# **2. 互斥阻塞**

队列在入队/出队等关键行为上锁保证线程安全，在获取队列长度上也使用了锁，所以可以获取到某一时刻上正确的长度值

```java
    // 实现对数组操作的并发安全
    private final ReentrantLock lock = new ReentrantLock();

    // 实现阻塞和通知
    private final Condition available = lock.newCondition();
```

消费者在从队列中取出任务时，可以使用限时等待poll(long, TimeUnit)和阻塞等待take()，当任务为空时，线程阻塞，等待通知唤醒

生产者往队列投递任务时，put/offer方法，都会在结束释放锁之前，唤醒在等待任务的消费者

# **3. 关键行为**

队列，从队尾进，从队头出。即对堆的添加操作，都是添加到最后一个位置；对堆的移除操作，都是将根节点位置移除

## **3.1 投递任务到队列**

DelayWorkQueue提供了以下的投递入口：

- add(Runnable): 调用offer(Runnable)
- put(Runnable)：调用offer(Runnable)
- offer(Runnable)：核心的投递实现
- offer(Runnable, long, TimeUnit)：从接口上看是限时方法，但是本质上也是直接调用了offer(Runnable)，不会阻塞

```java
public boolean offer(Runnable x) {
    if (x == null)
        throw new NullPointerException();
    RunnableScheduleFuture<?> e = (RunnableScheduledFuture<?>)x;
    final ReentrantLock lock = this.lock;
    // 保证线程安全
    lock.lock();
    try {
        int i = size;
        if (i >= queue.length)
            // 扩容
            grow();
        size = i + 1;
        if (i == 0) {
            queue[0] = e;
            setIndex(e, 0);
        } else {
            siftUp(i, e);
        }

        // 新加入的项，被重排序到队头的话，
        if (queue[0] == e) {
            leader = null;
            // 随机唤醒等待的线程
            available.signal();
        }
    } finally {
        lock.unlock();
    }
    return true;
}
```

## **3.2 消费队列任务**

DelayWorkQueue提供了以下的消费入口：

- remove(Object)：严格意义上不是消费入口，删除失败会抛错
- take(): 阻塞消费，没有任务则阻塞
- poll()：非阻塞消费，直接取队头，没有则返回null
- poll(long, TimeUnit)：限时阻塞消费，没有任务的话则在限时期间内阻塞，最后没有则返回null

poll和take的核心方法: finishPoll(RunnableScheduleFuture<?>)，可以理解为简易版的remove方法

```java
// 队头出队，其实就是覆盖掉队头
private RunnableScheduledFuture<?> finishPoll(RunnableScheduledFuture<?> f) {
    int s = --size;
    // 获取replacement，即最后一个节点
    RunnableScheduledFuture<?> x = queue[s];
    // 最后一个节点变null
    queue[s] = null;
    if (s != 0)
        // 将最后一个节点放到要删除的位置上，往下挪进行重排序
        siftDown(0, x);
    // 干掉f的缓存
    setIndex(f, -1);
    return f;
}
```

```java
public RunnableScheduledFuture<?> take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        for (;;) {
            RunnableScheduleFuture<?> first = queue[0];
            if (first == null)
                available.await();
            else {
                // 还有多久才能执行
                long delay = first.getDelay(NANOSECONDS);
                if (delay <= 0)
                    // 可以执行了，则直接出队
                    return finishPoll(first);
                
                first = null; // 等待时不要保留引用？

                if (leader != null)
                    // 已经有人在等了，则直接不限时阻塞
                    available.await();
                else {
                    // 没有人在等，任务又不能执行，把自己设置成leader，让其他线程直接不限时阻塞
                    Thread thisThread = Thread.currentThread();
                    leader = thisThread;
                    try {
                        // 限时阻塞delay的时间
                        available.awaitNanos(delay);
                    } finally {
                        if (leader == thisThread)
                            // 防止醒后还是执行不了，直接进入不限时阻塞
                            leader = null;
                    }
                }
            }
        }
    }
}
```

## **3.3 扩容**

offer的时候调用，因为offer已经上锁了，所以扩容不用加锁，扩容后会增加之前百分之50的长度

```java
private void grow() {
    int oldCapacity = queue.length;
    int newCapacity = oldCapacity + (oldCapacity >> 1); // 扩容50%
    if (newCapacity < 0) // overflow
        newCapacity = Integer.MAX_VALUE;
    queue = Arrays.copyOf(queue, newCapacity);
}
```

# 参考
- [DelayedWorkQueue的堆结构](https://blog.csdn.net/nobody_1/article/details/99684009)