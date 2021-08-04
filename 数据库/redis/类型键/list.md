# Redis类型键：list列表键

# **1. 底层实现和编码**

列表对象的编码可以是`REDIS_ENCODING_ZIPLIST`或者`REDIS_ENCODING_LINKEDLIST`，这句话等价于：列表对象的底层实现可以是`ziplist`或者`linkedlist`

当使用ziplist编码的列表对象作为底层实现时，每个压缩列表节点保存了一个列表元素