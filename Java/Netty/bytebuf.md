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
    int offset = buf.arrayOffset() + buf.readerIndex();
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

```java
public static void main(String[] args) {
    ByteBuf buf = new PooledByteBufAllocator(false).heapBuffer();
    if (buf.hasArray()) {
        // 0000 0010 0000 0000 ....
        buf.writeShort(512);
        // 从0位置读取一个字节，所以值为2
        short num = buf.getByte(0);

        // 声明一个字节数组
        byte[] newBufArray = new byte[buf.readableBytes()];
        // 从0开始读取，包括已读部分都会拷贝到数组中
        buf.getBytes(0, newBufArray);
    }
}
```

随机访问数据**既不会改变readerIndex，也不会改变writerIndex**

## **2.2 索引**

可以结合`readerIndex(index)`、`writerIndex(index)`、`setIndex(readerIndex, writerIndex)`来手动移动读索引和写索引的位置

一个ByteBuf理论上可以分为三个部分：`已读部分（可丢弃部分）`、`可读部分`、`可写部分`

![ByteBuf区域](https://asea-cch.life/upload/2021/10/ByteBuf-a0dcb07925694d96a997cf55542a4d20.png)

### **2.2.1 可读部分**

该部分存储了`实际的字节数据`，**新分配、包装、复制的缓冲区默认readerIndex为0**

下面的读取操作都将会检索/跳过位于当前readerIndex的数据，**readerIndex将会增加已读的字节数**：

> 假设buffer和buffer1都为堆内存模式下的ByteBuf

- `buffer.readXXX()`开头的检索方法
- `buffer.skipXXX()`开头的跳过方法
- `buffer1.writeBytes(buffer) / buffer.readBytes(buffer1)`，buffer1在写字节时，会从buffer的readerIndex位置开始读取字节数据，这也间接导致了buffer的readerIndex索引位置增加至writerIndex的位置

    ```java
    public static void main(String[] args) {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false);
        ByteBuf buf = allocator.heapBuffer();
        ByteBuf buf1 = allocator.heapBuffer();
        if (buf.hasArray()) {
            // 0000 0010 0000 0000 ....
            buf.writeShort(512);
            // 从0位置读取一个字节，所以值为2
            short num = buf.getByte(0);
            System.out.println(num);

            // buf的读索引为0，写索引为2，所以将会读取0~2的字节数据写入到buf1中
            buf1.writeBytes(buf);
            // buf读索引为2，buf1的写索引为2
            System.out.println(buf.readerIndex() + ", " + buf1.writeIndex());
        }
    }
    ```

    > buffer.readBytes(buffer1)是和它同义的操作，因为buffer是以buffer1的`length()`长度进行读取的，如果没有注意的话会引发下标越界问题

如果尝试在可读字节数为0的缓冲区中读取数据，将会引发`IndexOutOfBoundsException`

### **2.2.2 可写部分**

该部分存储了`未定义、写入就绪的内容`，**新分配**的缓冲区writeIndex默认为0

`buffer.writeXXX()`开头的写入方法操作都将从当前buffer的writerIndex处开始写数据，**writerIndex**将会增加已经写入的字节数

如果尝试往目标写入超过其目标容量的数据，将同样引发`IndexOutOfBoundsException`

### **2.2.3 已读部分**

当对buffer调用读取操作时，readerIndex将会增加读取字节数长度，而从0~readerIndex的区域，我们称为已读部分/未来可丢弃部分

当对该部分内容进行回收时，会将**可读部分往前挪至索引0的位置**，并将**回收的内存空间加至可写部分**

然而，可读部分的挪动涉及到**内存复制**，所以只有内存非常宝贵时，才可以考虑使用`discardReadBytes()`方法来确保可写分段最大化

### **2.2.4 索引管理**

- resetWriterIndex()/resetReaderIndex()：重置索引到**上次标记位置**

    需要配套markWriterIndex()、markReaderIndex()标记使用，默认为0

- writerIndex(int)/readerIndex(int)：直接移动索引到指定位置

- `clear()`：读/写索引直接置零，**性能佳推荐使用**（并不会真正的回收已读内存，避免了内存复制）

## **`2.3 派生缓冲区`**

派生缓冲区为ByteBuf提供了以专门的方式来呈现其内容的`视图`，这些方法将返回新的独立ByteBuf实例，但是**内部存储的数据和源实例的数据是共享的**

> 修改视图的存储数据，源实例和其他视图都会受到影响，需要格外小心。如果需要的是缓冲区的真实副本，则应该使用`copy()`方法进行内存复制，以获得拥有独立数据副本的ByteBuf

- duplicate()：返回共享源内存的ByteBuf

    ```java
    public static void main(String[] args) {
        ByteBuf buf = allocator.heapBuffer();
        if (buf.hasArray()) {
            ByteBuf newBuf = buf.duplicate();
            // buf的写索引变为2，内存变为0000 0010 0000 0000 ...
            buf.writeShort(512);
            // newBuf的写索引仍为0，因为共享内存变为0000 0010 0000 0000 ...
            System.out.println(newBuf.writerIndex());
        }
    }
    ```

- slice(index, length)：返回共享源内存字节数组中，从index位置开始直到`index + length`位置的新ByteBuf

    > 返回的ByteBuf为SlicedByteBuf包装类，通过`adjustment`来跳过源内存的`adjustment`值大小的索引数，**可以认为新的ByteBuf忽视掉了源前index个字节的数据**

    ```java
    public static void main(String[] args) {
        ByteBuf buf = allocator.heapBuffer();
        if (buf.hasArray()) {
            for (int i = 0; i < 4; i++) {
                // 字节数组中每两个字节（为512），共使用了8个字节
                // readerIndex：0，writerIndex：8
                buf.writeShort(512);
            }

            // readerIndex: 2, writerIndex: 8
            buf.readShort();
            // 相当于调用buf.slice()
            ByteBuf newBuf = buf.slice(buf.readerIndex(), buf.readableBytes());
            // newBuf具备独立的读写索引，写索引值为传入的length
            // 仍然共享buf的字节数组内存区域，通过SlicedByteBuf包装类中的adjustment参数进行调整，读索引值为传入的index
            System.out.println(newBuf.capacity()); // 返回6
        }
    }
    ```

- slice()：返回源剩余可读字节的ByteBuf
- Unpooled.unmodifiableBuffer(...)
- order(ByteOrder)
- readSlice(int)

结论：**尽可能使用slice()方法来避免复制内存的开销**

# **3. ByteBuf分配**

# 参考
- [](https://zhuanlan.zhihu.com/p/269847729)

- [Thearas用户回答：direct buffer（C heap）/C stack/intermediate buffer的关系](https://www.zhihu.com/question/60892134)：c stack/direct buffer（c heap）都处于JVM的堆外内存，正常socket操作会**将JVM堆内数据复制到intermediate buffer中（内存复制）**，再调用系统API将intermediate buffer的数据发送至网卡。而intermediate buffer可以在direct buffer（c heap）上分配，也可以在c stack上分配，所以如果在代码层面上直接就使用direct buffer，可以省去一次内存拷贝

- [海纳用户回答：direct buffer的好处总结](https://www.zhihu.com/question/60892134)

- [ETIN用户回答：为什么需要intermediate buffer与kernel交互？为什么heap buffer的数据需要复制到intermediate buffer？](https://www.zhihu.com/question/60892134)：底层通过`write()`、`read()`、`sendfile()`等系统调用时，需要传入buffer的起始地址和buffercount作为参数，jvm堆中的buffer往往以buffer数组对象而存在，这些对象受GC影响响，