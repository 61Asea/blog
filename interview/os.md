# interview thirteen：os

# **1. malloc()**

> 不同进程中malloc()函数返回的值会是相同的吗？（会，因为有虚拟内存）

# **2. mmap()**

![mmap](https://asea-cch.life/upload/2022/02/mmap-5f8b3ca0955844efb19e27e7fb5733b2.png)

![虚拟内存与内存映射](https://asea-cch.life/upload/2022/02/%E8%99%9A%E6%8B%9F%E5%86%85%E5%AD%98%E4%B8%8E%E5%86%85%E5%AD%98%E6%98%A0%E5%B0%84-4ddd478a39d5448894d7ec40e17b88e8.png)

- mmap()：内核函数调用，用于向**进程空间结构task_struct.mm_sturct**的vma中分配一段**虚拟空间**

    虚拟空间通过MMU单位和内核页表进行映射到真实物理内存中，该**物理内存会再与文件建立映射**，这段物理空间也可以认为是kernel page cache的一部分

- page cache：又称kernel buffer，用于弥补磁盘与内存之间的读写速度差异提升性能，注意要跟磁盘自带的高速cache作区分

结论：mmap()调用后，内核分配某一块真实物理内存page cache和进程虚拟空间映射，并将该page cache与文件进行映射，后续文件数据到达page cache后，进程可直接感知并通过指针操作

流程：



# **3. 为什么用协程不用线程（线程一定比协程好吗？）**

# **4. 进程调度有哪些算法**

> Linux调度用了什么算法？

# **5. Linux里进程通信有几种方式**

# **6. 进程同步有几种方式**

# **7. 管程**

# **8. 操作系统的栈和队列有哪些应用场景**

# 参考
- [腾讯WXG | 一二+面委+HR|已拿offer](https://leetcode-cn.com/circle/discuss/ON7r4A/)

# 重点参考
- [Linux虚拟内存和内存映射（mmap）](https://www.cnblogs.com/linguoguo/p/15807313.html)
- [共享内存和文件内存映射的区别](https://zhuanlan.zhihu.com/p/149277008)
- [关于共享内存shm和内存映射mmap的区别是什么？ - 妄想者的回答 - 知乎](https://www.zhihu.com/question/401612303/answer/1428608073)
- [Linux中的mmap映射 [一] - 兰新宇的文章 - 知乎](https://zhuanlan.zhihu.com/p/67894878)