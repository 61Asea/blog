# Kafka：日志存储

包括：存储介质（硬件）、存储格式、清理规则

# **文件目录布局**

一个主题分区对应一个日志（Log），为了防止Log过大，Kafka引入了`日志分段`（Log Segement），将Log切分为多个Log Segement

Log在物理上以文件夹的形式存储，每个LogSegement对应磁盘上的一个日志文件和两个索引文件 