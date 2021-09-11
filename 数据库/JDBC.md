# JDBC

JDBC，全称`Java Data Base Connectivity`（Java数据库连接），是JNDI调用SPI的其中一种

思路：讲双亲委派模型，JNDI调用SPI的方式，用线程变量 + 父类加载器直接委派给子类加载器进行类加载的过程

# **1. 传统JDBC**

思路：按照面试题的思路去挑选重点看，如JDBC连接数据库的步骤、Statement和PreparedStatement的区别、数据分页

# **2. Spring JDBC**

思路：

# **3. druid**

思路：
- 数据库连接池工作原理和实现方案，druid就是其中一种，这与JDBC2.0提供的DataSource数据源接口做交互

- 设计一个数据库连接池方案（druid咯）

# 参考
- [双亲委派模型破坏](https://blog.csdn.net/sinat_34976604/article/details/86723663)
- [传统JDBC](https://www.cnblogs.com/erbing/p/5805727.html)

- [JDBC和druid的简单介绍](https://www.cnblogs.com/knowledgesea/p/11202918.html)

- [Spring JDBC源码解析](https://mp.weixin.qq.com/s?__biz=MzU5MDgzOTYzMw==&mid=2247484601&idx=1&sn=3c0e33701105a65e74627bc7a40e545d&scene=21#wechat_redirect)