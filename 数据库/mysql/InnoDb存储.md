# Mysql（一）：innodb存储

![文件目录](https://asea-cch.life/upload/2021/06/%E6%96%87%E4%BB%B6%E7%9B%AE%E5%BD%95-6b07a2a908f74508b8f6050cfec0629d.png)

以文件来观察，表空间有以下几种类型：
1. 系统表空间：

    文件名以ibdataN命名
    
    包括元数据数据字典（表、列、索引等），double write buffer、插入缓冲索引页（change buffer）、系统事务信息（sys_trx）、默认包含undo回滚段（rollback segment）
2. **用户表空间：**

    文件名以表名命名，库中的表对应一个同名表文件

    一般同一个库的表文件都放在同一个文件夹中，5.7之后引入了General Tablespace，可以把多个表放到同一个文件里
3. **redo log：**

    文件名以ib_logfile0、ib_logfile1命名，滚动方式写入

    主要满足ACID特性中的Durability持久性，保证数据可靠性
4. 临时表空间文件、undo独立表空间

系统表空间、用户表空间、undo独立表空间、临时表空间的格式基本一致，**spaceid代表空间id**，接下来将以用户表空间作为基准，介绍表空间中的各个页：

![用户表空间物理组织](https://asea-cch.life/upload/2021/06/%E7%94%A8%E6%88%B7%E8%A1%A8%E7%A9%BA%E9%97%B4%E7%89%A9%E7%90%86%E7%BB%84%E7%BB%87-f8d948b89fb040169d27b4748539d9ff.jpg)

## **1. Segment/Extent**

表空间由多个Segment组成，Segment默认由256个Extent组成，Extent默认由64个Page组成

这种多层次的组织关系，实际上是为了更好的分配和管理页的新增/删除/扩容等操作

## **2. 页（数据索引页）**

类似于操作系统的页，涉及到了**系统（空间）局部性原理**，在CPU缓存行中也有相应应用

    想要读取的字（数据），其相邻的字（数据）在接下来被访问的概率极高

每个Page默认大小为16KB，序号是一个32位的uint，最大序号为2^32，所以一个表最多能存储数据为：2^32 * 16K = 64T的数据

根据页的默认大小，我们也可以反推导出**一个Extent默认大小为1MB，一个Segment默认大小为256MB**

页的类型有很多种，上图包含的页有：
- FSP_HDR/XDS页（文件管理页）

    处于Segment的开头，代表一个Segment，存储了随后256个Extent的空间管理情况，所以每隔256MB就要有一个类似的文件管理页
    
    第一个Segment的头页为FSP_HDR页，其他的Segment的头页为XDS页

- IBUF_BITMAP页

    用于优化插入/删除缓存

- Inode页

    用于管理Segment中的叶子节点和非叶子节点，并可以管理每个Segment占用的数据页。并且维护了三个Extent链表，来辅助Segment和Extent对页的分配

- **Index页（数据索引页）**

    存储所有的索引和用户数据，**InnoDB将数据与索引存放在同一个文件结构中，建立聚簇索引**

索引页的结构图如下：

![索引页](https://asea-cch.life/upload/2021/06/%E7%B4%A2%E5%BC%95%E9%A1%B5-06395ddcc2024195817ccc9aa6da77a2.jpg)

B+树的每一个结点，就是一个页，每一个页又划分出多个区域，其中最重要的两个：

Index Header：存放**页目录**，当前页属于B+树中的哪个层次，以及聚簇索引的id

User Records + free space：由多个行组成，非叶子结点存储主键值与相对应的子页的页码，叶子结点存储用户数据

    主键和非主键都是索引，一切数据都存储在INDEX索引页中，索引即数据，数据即索引

## **3 行结构**

我们知道B+树的结构中，叶子结点有非叶子结点的冗余数据，每个叶子结点之间通过双向引用形成一个双向链表

根据这个特性，我们将主键和页码指针放在了非叶子结点里，而将主键和真实数据放在了叶子结点中，这样非叶子节点就可以拥有更多的节点度，**大幅度削弱树的高度，以减少I/O的次数**

而为了支持以上的思路，行结构可以**根据当前所在页的Page Level**，判断自身处于哪种类型节点中，并根据不同类型节点来调整自身的存储存储

![行结构的动态变化](https://asea-cch.life/upload/2021/06/%E8%A1%8C%E7%BB%93%E6%9E%84%E7%9A%84%E5%8A%A8%E6%80%81%E5%8F%98%E5%8C%96-dd81d28502da4bc8be3f44304d1bebda.jpg)
    
    默认行格式：DYNAMIC，是COMPACT升级版，对行溢出的情况有不同做法

在介绍了行格式的动态变化后，我们以叶子节点的行格式为切入点进行分析：

![Compact行格式](https://asea-cch.life/upload/2021/06/Compact%E8%A1%8C%E6%A0%BC%E5%BC%8F-10a4f59c641d4f04815ec50e67e854c9.png)

用户的每一行数据，都会遵循行格式进行存储，每一个行都有对应下一个行的**偏移量指针**，指针会指向到**头信息**和**真实数据**之间，所以为了方便读取额外信息，额外信息的存储是逆序的

存储数据的痛点主要来源于**变长字段VARCHAR**和**列值是否为NULL**两个问题：

### **变长字段VARCHAR**

    前提：Mysql可以获取到列的值类型，并知道固定长度类型的字段长度

当对某VARCHAR类型的值进行更新时，都会将其长度记录到**变长字段长度列表**中，在读取数据时，如果发现变长字段类型，则会逆序读取长度列表中的值，并读取**值对应长度**的数据

### **列值是否为NULL**

    前提：Mysql在读取过程中，可以感知到字段是否有NOT NULL约束

在建表语句执行后，Mysql会为可以为NULL的的每个字段建立一个bit位做标记，存放到NULL值列表中，反之，NOT NULL约束的字段无需建立bit位

当读取数据时，会先读取NULL值列表来判断可以为NULL的列是否为NULL值，若是的话，则直接跳过该列，**带来的好处就是真实数据存放得更紧凑**

### **完全行溢出方式**

off-page：溢出页

Dynamic和Compact的区别：
- Compact会将Varchar、Text或Blob类型的值存放于BTree节点，超过768字节的部分再放到off-page
- Dynamic只在TEXT或BLOB列 <= 40字节时将数据存在于数据页中，否则列数据放到off-page中，这样可以避免Compact把太多的大列值放到BTree节点

    Dynamic数据页中只存放20个字节的指针，实际的数据存放在off-page中，Compact会存放768个前缀字节

# 参考
- [从MySQL InnoDB物理文件格式深入理解索引](https://zhuanlan.zhihu.com/p/103582178)
- [mysql page directory_InnoDB Page结构详解](https://blog.csdn.net/weixin_39639965/article/details/113433163)

# 重点参考
- [MySql数据是如何存储在磁盘上存储的](https://mp.weixin.qq.com/s?__biz=MzI3NzE0NjcwMg==&mid=2650152173&idx=1&sn=649e69f288d3d529d3af5282584b97dc&chksm=f36801ccc41f88dae42bf2914ae341aca27ee1284d06b50e8801e261bcb20e0c8cc380194edc&scene=21#wechat_redirect)
- [MySQL中InnoDB页结构和索引的存储](https://blog.csdn.net/qq_45434246/article/details/103370558)
- [InnoDB-索引页（数据页）](https://www.jianshu.com/p/e13e70b90a45/)
- [页分裂和页合并](https://zhuanlan.zhihu.com/p/98818611)
- [MySQL InnoDB 行记录格式（ROW_FORMAT）](https://www.cnblogs.com/wilburxu/p/9435818.html)