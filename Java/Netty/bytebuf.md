# Netty：ByteBuf

TCP基于`字节流传输`，Java NIO提供了ByteBuffer作为它的字节容器，但是这个类使用起来过于复杂繁琐

Netty提供`ByteBuf`作为替代，具备更好的卓越功能性和灵活性，被称为`数据容器`：
- 可以被用户自定义的缓冲区类型扩展
- 通过内置的复合缓冲区类型，实现了透明的`零拷贝`
- 容量按需增长（类似StringBuilder）
- 在读和写这两种模式之间切换，无需调用原生`ByteBuffer.flip()`方法
- **读和写使用不同索引**
- **支持引用计数**
- **支持池化**

数据处理API通过ByteBuf和ByteBufHolder暴露，后者的实现类用于**管理前者实例的分配**，执行各种针对于**数据容器本身（指ByteBuf）**和**它所持有的数据**的操作

# **1. 使用模式**

## **1.1 堆缓冲区**

存放区域：JVM堆空间

这种模式也被称为`支撑数组（backing array）`，如果通过套接字发送该模式下的ByteBuf，JVM将会在内部把缓冲区复制到一个`直接缓冲区`

优点：
- 在没有使用池化的情况下提供快速的分配和释放
- 适合有遗留数据需要处理的情况
- **由VM进行GC，不会产生内存泄漏**

缺点：**存在内存复制，性能相对较差**

```java
ByteBuf buf = Unpooled.directBuffer(size);
// 如果buf存在支撑数组，则数据存在堆缓冲区
if (buf.hasArray()) {
    byte[] array = buf.array();
    // 计算第一个字节的偏移量
    int offset = buf.arrayOffset + buf.readerIndex();
    // 获得buf可读字节数
    int length = buf.readableBytes();

    handleArray(array, offset, length);
}
```

## **1.2 直接缓冲区**

存放区域：中间缓冲区（应该是kernel socket buffer），在常规JVM堆内存之外

优点：`网络数据传输的理想选择`，在使用socket进行I/O操作时，无需将数据从堆内复制到直接缓冲区中

缺点：

- 分配和释放较为昂贵
- 处理遗留代码时，仍不得不进行一次复制，将数据拷贝到堆上
- 存在内存泄漏的风险

访问直接缓冲区的数据，从而不得不进行的内存复制操作：
```java
ByteBuf directBuf = Unpooled.directBuffer(size);
// 如果buf存在支撑数组，则数据存在堆缓冲区；否则，则表明这是一个直接缓冲区
if (!directBuf.hasArray()) {
    // 获取可读字节数
    int length = directBuf.readableBytes();
    // 分配一个新的数组来保存具有该长度的字节数据
    byte[] array = new byte[length];
    // 将字节复制到该数组中
    directBuf.getBytes(directBuf.readerIndex(), array);

    handleArray(array, 0, length);
}
```

## **1.3 复合缓冲区**



# **2. 字节操作**

ByteBuf维护了两个不同的索引，一个用于**读取**，一个用于**写入**

两者索引都是从零开始，第一个字节的索引是0，最后一个字节的索引是`capacity() - 1`

```java
public abstract class ByteBuf implements ReferenceCounted, Comparable<ByteBuf> {
    // 返回容器的长度
    public abstract int capacity();

    // 返回容器的读索引
    public abstract int readerIndex();

    // 返回容器的写索引
    public abstract int writeIndex();
    
    // // 设置容器的读索引位置
    // public abstract ByteBuf readerIndex(int readerIndex);

    // // 设置容器的写索引位置
    // public abstract ByteBuf writeIndex(int writerIndex);

    // // 一次性设置读索引和写索引
    // public abstract ByteBuf setIndex(int readerIndex, int writerIndex);

    // 返回剩余可读字节数：writeIndex - readerIndex
    public abstract int readableBytes();

    // 返回剩余可写字节数：capacity() - writeIndex
    public abstract int writableBytes();

    // 返回容器是否可读：writerIndex > readerIndex
    public abstract boolean isReadable();

    // 返回容器是否可写：capacity() > writerIndex
    public abstract boolean isWritable();

    // ...
}
```

## **2.1 随机访问**

ByteBuf如同Java的字节数组一样提供了随机访问的方法，这些方法往往是那些需要索引值作为参数传入，并且方法名以`getXXXX()`开头

这样访问数据**既不会改变readerIndex，也不会改变writerIndex**

```java
ByteBuf buffer = ...;
// 索引i处作为起始位置，读取一个字节长度数据
byte b = buffer.getByte(i);
System.out.println((char)b);

// 声明一个字节数组
byte[] newBufArray = new byte[buffer.readableBytes()];
// 从0开始读取，包括已读部分都会拷贝到数组中
buffer.getBytes(0, newBufArray);
```

可以结合`readerIndex(index)`、`writerIndex(index)`、`setIndex(readerIndex, writerIndex)`来手动移动这两者

## **2.2 索引移动**

