# volatile

# 1. 作用

1. 可见性

2. 禁止JIT编译期或Java解释器的重排序优化

3. 禁止CPU对指令进行重排序优化

# 2. 字节码层面语义

volatile修饰的变量，在javac编译器编译成字节码后生成ACC_VOLATILE助记符，帮助JVM执行引擎（Java解释器）解释字节码指令为机器平台相关指令时，对volatile变量进行特殊处理

```java
public class VolatileDemo {
    public static volatile int counter = 1;

    public static void main(String[] args) {
        System.out.println(counter);
    }
}
```

使用[javap -v]指令对VolatileDemo类的二进制**字节码文件**进行反编译：

```java
    public static volatile int counter;
    descriptor: I
    flags: ACC_PUBLIC, ACC_STATIC, ACC_VOLATILE //ACC_VOLATILE标识
    // ...
```

ACC_VOLATILE作为访问标志，供后续Java解释器（C++）解释指令为机器码指令遵循volatile语义

# 3. JVM源码层面语义

Java解释器（C++）在读取到putstatic和putfield指令时，会执行cache.is_volatile()方法对操作的变量进行判断

C++关键代码（/hotspot/src/share/vm/interpreter/bytecodeInterperter.cpp）：
```c++
CASE(_putfield):
CASE(_putstatic):
{
    // ConstantPoolCacheEntry* cache; -- cache是常量池缓存实例
    if (cache -> is_volatile()) {
    // 对volatile修饰的Java基本类型进行赋值
        if (tos_type == itos) {
            obj -> release_int_field_put(field_offset, STACK_INT(-1));
        }

        // 写完值后的storeload屏障
        OrderAccess::storeload();
    } else {
        // 非volatile修饰赋值
        if (tos_type == itos) {
            obj->int_field_put(field_offset, STACK_INT(-1));
        }
    }
}
```

1. 调用cache.is_volatile()方法

```c++
// 1. is_volatile()方法
bool is_volatile() const {
    return (_flags & JVM_ACC_VOLATILE) != 0;
}
```
该方法会读取是否字节码有助记符ACC_VOLATILE

2. 处理putstatic和putfield指令

```c++
CASE(_putfield):
CASE(_putstatic):
{
    if (cache -> is_volatile()) {
        if (tos_type == itos) {
            obj -> release_int_field_put(field_offset, STACK_INT(-1));
        }
    } else {
        if (tos_type == itos) {
            obj->int_field_put(field_offset, STACK_INT(-1));
        }
    }
}
```
若发现是voaltile，则调用release_int_field_put()做赋值操作，否则直接调用int_field_put()

3. 赋值操作OrderAcce4ss::release_store，读取操作OrderAccess::load_acquire

```c++
// load操作调用的方法
inline jbyte oopDesc::byte_field_acquire(int offset const {
    return OrderAccess::load_acquire(byte_field_addr(offset));     
}

// store操作调用的方法
inline void oopDesc::release_byte_field_put(int offset, jbyte contents) {
    // 此处byte_field_addr方法返回的是c++的volatile变量指针
    OrderAccess::release_store(byte_field_addr(offset), contents); 
}
```

赋值操作包了一层在OrderAccess::release_store方法，读取操作包装在OrderAccess::load_acquire中

4. c++的volatile

```c++
// 以byte类型为例

inline void OrderAccess::load_acquire(volatile jbyte* p) {
    return *p;
}

inline void OrderAccess::release_store(volatile jbyte* p, jbyte v) {
    // 将v的数据填充到volatile变量的地址里
    *p = v;
}
```

从以上看，到JVM的c++实现层面，又**使用到了c++中的volatile关键字，用于建立语言级别的内存屏障**

c++的volatile修饰词作用：
- volatile修饰的类型变量表示可以被某些编译期未知的因素更改
- 使用volatile变量时，避免激进的优化（系统总是重新从内存中读取数据，即使它前面的指令刚从内存中读取缓存，防止出现未知更改和主内存中不一致）

5. OrderAccess::storeLoad()

对volatile变量赋值完成后，会返回到bytecodeInterperter.cpp，执行OrderAccess::storeLoad()逻辑

该方法在不同操作系统或cpu架构下有不同的实现，在linux系统下x86架构实现为：

    lock； addl $0,0(%%rsp)

其中addl $0,0(%%rsp)是将当前的寄存器加0，相当于是一个空操作，但又不同于nop空操作。因为lock前缀不允许配合nop指令使用

# 3. 硬件语义

上述在c++层面上，在赋值操作最后会通过OrderAccess::storeload()方式，在汇编层面上在变量赋值前新增**lock; addl $0,0(rsp)**指令

lock前缀，会保证某个处理器对共享内存（缓存·行）的独占使用，它将本处理器的缓存写入内存，该写入操作会使得其他处理器或内核对应的缓存行失效

    在硬件层面，以共享同一缓存行的写处理器，共享读处理器，其他处理器三个视角进行分析：
    
    写处理器：
    会向其他共享该缓存行的处理器发送失效请求，并将后续的赋值操作继续写入到写缓冲区中，等待其他共享核心的invalid ack，再一同同步到其他核心，并写回主内存中
    
    共享读处理器：
    当通过“嗅探”获得失效请求，直接异步加入到无效化队列中，并在后续的读屏障中，将无效化队列清空
    
    写缓冲区同步到其他共享缓存行的处理器，

通过独占内存，使得其他处理器缓存失效，达到了“指令重排序无法越过内存屏障”的作用

# 参考
- [硬件模型](https://github.com/61Asea/blog/blob/master/%E5%A4%9A%E7%BA%BF%E7%A8%8B/%E7%A1%AC%E4%BB%B6%E6%A8%A1%E5%9E%8B.md)
- [内存屏障在CPU、JVM、JDK中的实现](https://www.cnblogs.com/Courage129/p/14360186.html)
- [volatile底层原理详解](https://zhuanlan.zhihu.com/p/133851347)

- [从汇编语言看Java volatile关键字](https://blog.csdn.net/a7980718/article/details/83932123)
- [Volatile, LOCK与MESIF](https://zhuanlan.zhihu.com/p/258534023)
- [volatile与lock前缀指令](https://www.cnblogs.com/xrq730/p/7048693.html)