# Kafka：Producer

# **线程模型**

# **提交**

# **限速**

# 重点参考
- [队列如何保证消息不丢失？如何处理重复消费？](https://hollis.blog.csdn.net/article/details/104285138?spm=1001.2101.3001.6661.1&utm_medium=distribute.pc_relevant_t0.none-task-blog-2%7Edefault%7ECTRLIST%7Edefault-1.no_search_link&depth_1-utm_source=distribute.pc_relevant_t0.none-task-blog-2%7Edefault%7ECTRLIST%7Edefault-1.no_search_link)：完全避免消息重复发送是很困难的，重复消费的场景在手动提交中仍旧会发生，所以应该保证生产、消费过程保持幂等性；重心放在消息尽量不丢失的方向上，再去处理由此导致加剧的重复消费问题

- [从producer、broker、consumer上分析丢失消息](https://zhuanlan.zhihu.com/p/309518055)