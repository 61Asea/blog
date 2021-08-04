# Redis类型键：string

字符串对象的编码方式有：`int`，`embstr`和`raw`，在存储时，会根据存储的类型进行相应编码处理

# **底层实现**

```c++
typedef struct redisObj {
    // 指向指针
    void* ptr;

    // ...
}robj;
```

void*会**根据字符串对象存储的值类型**，转换为相对应的c类型结构/对象类型，并将对象的编码设置为结构对应Redis编码

- REDIS_ENCODING_INT：整数值

    使用long编码方式保存

- REDIS_ENCODING_EMBSTR：长度小于等于32字节的字符串

    使用embstr编码方式保存这个字符串值

- REDIS_ENCODING_RAW：长度大于32字节的字符串

    使用一个简单动态字符串（SDS）保存这个字符串值，并设置编码为raw

## **raw和embstr的区别**

embstr编码是专门用于保存`短字符串`的一种优化编码方式，和`raw`一样，都使用redisObject和sdshdr结构来表示字符串对象

> Redis并没有为embstr编码的字符串对象编写任何相应的修改程序，所以embstr编码的字符串实际上是只读的，当对embstr字符串进行修改时，会先将编码从embstr转换为raw，再执行raw编码的修改命令，这使得**embstr在修改后，总会变成一个raw对象**

- 将内存分配次数从两次降为一次

    raw编码会调用**两次内存分配**函数分别创建redisObject结构和sdshdr结构；而embstr则通过调用**一次内存分配**函数来分配一块连续空间，空间一次包含redisObject和sdshdr结构

- 释放embstr编码的字符串对象只需要调用**一次**内存释放函数

- 保存在连续内存块中，比起raw编码而言，embstr能更好地被加载到缓存中，利用缓存带来的优势

## **保存小数**

long double类型表示的浮点数在Redis中也是**作为字符串来保存**，使用`embstr`或`raw`编码方式进行编码

在有需要时，程序会：

1. 先将保存在字符串对象的字符串**值转换为浮点数值**，执行某些数值操作
2. 然后再将执行所得浮点数**结果转换回字符串**

# **编码转换**

> 在上面讲到了保存小数时的类型转换，然而它不会最终修改字符串值的类型，只是在操作过程中临时转换

`APPEND`：将对象转换为raw编码，然后按raw编码的方式执行操作

当`int`编码或`embstr`编码的字符串在条件满足的情况下，会被转换为raw编码的字符串对象

- int -> int

    可以，通过APPEND命令拼接另外一个整数，得到的仍然是一个int编码的字符串

- int -> embstr

    不行，embstr只有在`set`时确定，属于只读量

- int -> raw

    通过APPEND命令，将本身保存整数值的字符串，追加**字符串值**，这样操作执行的结果就是获得一个`raw`编码方式的字符串对象

- embstr -> raw

    embstr编码的字符串对象实际上是只读的，当操作通过embstr编码的字符串值时，会先将其转换为raw编码，再执行修改命令

    所以embstr执行修改命令后，最终会变成raw编码

- embstr -> int

    不行，只有通过重新set进行编码转换

- raw -> int

    不行，只有通过重新set进行编码转换

- raw -> embstr

    不行

# 参考
- [Redis设计与实现]()