# Mysql存储格式

# 重点参考
- [MySql数据是如何存储在磁盘上存储的](https://mp.weixin.qq.com/s?__biz=MzI3NzE0NjcwMg==&mid=2650152173&idx=1&sn=649e69f288d3d529d3af5282584b97dc&chksm=f36801ccc41f88dae42bf2914ae341aca27ee1284d06b50e8801e261bcb20e0c8cc380194edc&scene=21#wechat_redirect)

# question:

问：myisam和innodb都是用的B+树存数据，为什么myisam可以没有主键

答：其实innodb也可以没有主键，是一般建议有主键，因为聚簇索引存的是所有列数据，一般是查到主键ID，通过聚簇索引回表查到所有数据；
myisam的叶子节点存的是地址好像，树结构存的东西不一样

问：是不是聚簇索引不仅是包含主键索引的那一个B+树，还包含了其他的二级索引对应的B+树

答：不是，聚簇存的是所有行数据 ，普通索引存的是主键索引的值