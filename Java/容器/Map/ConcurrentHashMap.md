# ConcurrentHashMap

> [HashMap](https://asea-cch.life/achrives/hashmap)

HashMap属于并发不安全的容器，在并发情况下甚至会导致死循环问题，在任何有并发场景的情况下，都不应该使用HashMap，JDK8使用尾插法仍会导致问题（桶是红黑树节点情况下，在调用getRoot()时其他线程修改root，导致问题）




# 参考
- [无锁HASHMAP的原理与实现](https://coolshell.cn/articles/9703.html)
- [ConcurrentHashMap1.8 - 扩容详解](https://blog.csdn.net/ZOKEKAI/article/details/90051567)
- [ConcurrentHashMap 1.7和1.8的区别](https://blog.csdn.net/u013374645/article/details/88700927)