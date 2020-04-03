# **2PC/3PC**

基于模型：协调者（单点或集群），一组参与者，网络可能出现分区

## **1. 2PC**
二阶段提交协议，又称二段式协议，绝大部分关系型数据库都是采用二阶段提交的方式来完成分布式事务。
二段式因为只引入了协调者超时机制，参与者在没有超时机制的情况下，很可能出现协调者宕机导致锁住资源的情况。

一般分为以下两个阶段：
- 提交事务请求
- 执行提交事务

### **1.1. 提交事务请求**
该阶段也被称为投票阶段，三段式也是在该阶段基础上进行分割
1. 协调者向所有参与者发送事务内容，询问是否可以执行事务操作，并等待各个参与者的响应
2. 参与者收到请求后，执行事务，将操作写入到本地Undo和Redo中
3. 执行事务的成功与否，会分别返回Yes或No到协调者中

### **1.2. 执行提交事务**
当上一步中，全部参与者返回Yes，则：
1. 发起事务提交请求
2. 所有参与者提交本地事务
3. 反馈事务提交结果，向协调者发送ACK信息
4. 协调者等待所有参与者的ACK，若全部完成，则完成分布式事务

若有一个参与者返回No，则：
1. 发起事务回滚请求
2. 所有参与者回滚本地事务
3. 反馈事务回滚，向协调者发送ACK，
4. 接受到所有参与者ACK，完成分布式事务中断

### **1.3 优缺点**
优点：
- 简单实现方便

缺点：
- 参与者阻塞问题严重，这个体现参与者在1.1的执行事务后，若其他参与者没有执行事务完成，则需要阻塞等待其他参与者完成，协调者才会进行下一步。
即参与者会被其他参与者的执行快慢所阻塞
- 单点问题，协调者宕了就炸了。在第一阶段宕了，那么参与者不锁资源阻塞；若在第二阶段宕了，参与者直接锁定事务资源并阻塞。
- 脑裂，如果参与者没有全部都收到commit，那么各节点将出现数据不一致


## **2. 3PC**

参与者也引入了超时机制，并且比2PC多了一个canCommit阶段，以保证协调者在重新恢复后的策略选择
也保证了参与者在preCommit等到协调者超时后，是否继续执行事务操作

一般分为以下三个阶段：
1. canCommit阶段
2. preCommit阶段（与二段式的提交事务请求相似）
3. doCommit阶段（与二段式的执行事务请求相似）

canCommit会先问全部参与者是否能commit，如果全部为Yes，那么这次提交的大背景则为commit；如果有一个不为Yes，则事务失败
决定大背景，并让协调者集群化，可以很好的解决协调者宕机后选举新leader时，新leader执行的策略选择

优点：
1. 降低了二段式的阻塞范围，因为当在preCommit中执行事务后。参与者可凭借自身的超时机制，不受其他参与者的影响与协调者的下一阶段doCommit操作，进行事务提交。
2. 解决了单点问题，并且新leader选举后，依旧可以保证一致性

缺点：
也是优点一的缺点，如果在preCommit中，参与者在接收到commit操作后，网络出现分区，那么参与者真的就凭借自身超时机制，并按照canCommit的大背景去做了事务提交操作。
但是此时此刻协调者并没有接收到某些参与者的ACK，会对可用节点发送abort指令，这样就导致数据不一致。