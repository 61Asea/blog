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
若发现是voaltile，则调用release_int_field_put()做赋值操作，否则直接调用int_field_put()

3. 赋值操作包了一层在OrderAccess::release_store方法，读取操作同理在OrderAccess::load_acquire中

```c++
// load操作调用的方法
inline jbyte oopDesc::byte_field_acquire(int offset const {
    return OrderAccess::load_acquire(byte_field_addr(offset));     
}

// store操作调用的方法
inline void oopDesc::release_byte_field_put(int offset, jbyte contents) { 
    OrderAccess::release_store(byte_field_addr(offset), contents); 
}
```

赋值操作包了一层在OrderAccess::release_store方法，读取操作同理在OrderAccess::load_acquire中


# 3. 硬件语义

# 参考
- [硬件模型](https://github.com/61Asea/blog/blob/master/%E5%A4%9A%E7%BA%BF%E7%A8%8B/%E7%A1%AC%E4%BB%B6%E6%A8%A1%E5%9E%8B.md)
- [内存屏障在CPU、JVM、JDK中的实现](https://www.cnblogs.com/Courage129/p/14360186.html)
- [volatile底层原理详解](https://zhuanlan.zhihu.com/p/133851347)

- [从汇编语言看Java volatile关键字](https://blog.csdn.net/a7980718/article/details/83932123)
- [Volatile, LOCK与MESIF](https://zhuanlan.zhihu.com/p/258534023)
- [volatile与lock前缀指令](https://www.cnblogs.com/xrq730/p/7048693.html)