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

## **1.1 内存区域**

关于ByteBuf的内存区域，涉及到以下关于内存管理专业词汇：

- direct buffer（c heap）：堆外直接内存，同样属于JVM进程用户空间的一部分，与堆内存区分开来

- c stack：堆外栈内存，同样处于堆外直接内存，与c stack区别仅在于大小支持

- `intermediate buffer（c stack、c heap）`：netty文档提及的中间缓冲区概念

    其实就是c stack或者c heap，只在HeapBytebuf中出现，因为需要进行堆内到堆外的内存复制，两者的选取通过`MAX_BUFFER_LEN`

    [Thearas的回答：direct buffer、c heap、c stack](https://www.zhihu.com/question/60892134)

    ```c++
    /*
    * Class:     java_net_SocketOutputStream
    * Method:    socketWrite
    * Signature: (Ljava/io/FileDescriptor;[BII)V
    */
    JNIEXPORT void JNICALL
    Java_java_net_SocketOutputStream_socketWrite0(JNIEnv *env, jobject this,
                                                jobject fdObj, jbyteArray data,
                                                jint off, jint len) {
        char *bufP;               // 指向要发送的字符数组，即 intermediate buffer 
        char BUF[MAX_BUFFER_LEN]; // 这就是在 stack 上分配的数组空间了
        int buflen;
        int fd;

        // ... 省略

        /*
        * Use stack allocate buffer if possible. For large sizes we allocate
        * an intermediate buffer from the heap (up to a maximum). If heap is
        * unavailable just use our stack buffer.
        * bufp 就是你问的 intermediate buffer 啦，可以看到是临时的一个 buffer ，如果在 C HEAP 上分配，那么在本函数末尾就被释放啦
        */
        if (len <= MAX_BUFFER_LEN) {
            bufP = BUF;              // 将 bufp 直接指向先前的 stack 上分配的 BUF
            buflen = MAX_BUFFER_LEN;
        } else {
            buflen = min(MAX_HEAP_BUFFER_LEN, len);
            bufP = (char *)malloc((size_t)buflen);  // 将 bufp 指向在 C Heap 上分配的空间
            if (bufP == NULL) {
                bufP = BUF;
                buflen = MAX_BUFFER_LEN;
            }
        }

        while(len > 0) {
            int loff = 0;
            int chunkLen = min(buflen, len);
            int llen = chunkLen;
            int retry = 0;

            // 注意这里，将原始数组的区域复制到缓冲区中。
            // 缓冲区所在区域据上面所说：
            // 如果原始数组大小小于 MAX_BUFFER_LEN ，那么在 stack 上分配，否则分配到 C Heap 上，即堆外内存上，如果失败，那就分配到 stack 上了
            (*env)->GetByteArrayRegion(env, data, off, chunkLen, (jbyte *)bufP);

            // 循环
            while(llen > 0) { 
                int n = send(fd, bufP + loff, llen, 0); // 调用windows api，发送
                if (n > 0) {
                    llen -= n;
                    loff += n;
                    continue;
                }

        // ... 省略，一些失败处理

        if (bufP != BUF) {
            free(bufP); // 在这里释放 intermediate buffer
        }
    }
    ```

- mmap()：系统调用，可以将kernel buffer的内存与用户空间的内存进行物理映射，避免数据拷贝

- kernel buffer：内核读缓冲区，存放磁盘、网卡等外设DMA拷贝过来的数据

- kernel socket buffer：内核socket缓冲区，用于发送数据

### **常见问题**

**问1：堆内存、堆外内存、用户进程缓冲区（用户空间）、kernel buffer的关系？**

答：JAVA程序运行在JVM上，JVM处于用户态，拥有其独立的用户进程缓冲区（用户空间），JVM将自身内存区域分为了：

- 堆内存（运行时区域）：存放程序的常驻对象，**会频繁受到YOUNG GC影响**

- 堆外内存：又称`direct buffer直接内存`，是处于VM进程中的内存区域，用于与kernel buffer进行数据交互

**问2：netty文档中的intermediate buffer指的是？**

答：调用write、read等系统调用要求传入的buffer数据空间应是是`连续`且`地址稳定不变动`的，而堆内存数据频繁受GC影响，在除了CMS以外的GC算法中会移动内存地址，**导致调用出现异常**

因此，当发送堆内存的数据时，需要会将其拷贝到一个稳定的intermediate buffer（中间缓冲区），该区域即是direct buffer（c heap）

intermediate buffer也可能是`c stack`而不是`c heap`，这取决于输出数据的大小：当数据没有到达足够量级时，使用方法栈分配的内存空间进行传输

**问3：direct buffer使用mmap()吗？**

答：direct buffer在linux中通过`malloc()`函数进行分配，当分配的区域够大时，会使用mmap()将其与kernel buffer的某部分区域进行物理地址映射

netty使用NIO中的`DirectByteBuffer`进行数据操作时，操作结果将直接反馈在kernel buffer上，去除了一次**无意义的堆内到堆外的拷贝**，和一次**堆外到内核缓冲区的拷贝**

## **1.2 ByteBuf模式**

ByteBuf可以从以下两个角度去分类：
- pooled or unpooled

- heap-based or direct

为了降低分配和释放内存的开销，Netty提供`ByteBufAllocator`实现了ByteBuf的池化，最终是否使用取决于系统和应用程序的决定

- Pooled：预先从分配好的内存中取一段封装成ByteBuf，交给应用程序

    池化ByteBuf实例以提高性能，并最大限度地减少内存碎片

    ```java
    // ByteBuf的池化分配器
    public class PooledByteBufAllocator extends AbstractByteBufAllocator implements ByteBufAllocatorMetricProvider {
        // 池化的内存区域由PlatformDependent.directBufferPerferred()参数决定，默认从分配器获得的内存都为直接内存
        // We should always prefer direct buffers by default if we can use a Cleaner to release direct buffers.
        public static final PooledByteBufAllocator DEFAULT = new PooledByteBufAllocator(PlatformDependent.directBufferPerferred());
    }
    ```

- Unpooled：直接调用操作系统API申请内存

    ```java
    // 非池化ByteBuf分配器
    public class UnpooledByteBufAllocator extends AbstractByteBufAllocator implements ByteBufAllocatorMetricProvider {
        public static final UnpooledByteBufAllocator DEFAULT = new UnpooledByteBufAllocator(PlatformDependent.directBufferPerferred());
    }

    // Unpooled对UnpooledByteBufAllocator接口进行包装
    public class Unpooled {
        private static final ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;

        // 提供非池化的内存分配接口
    }
    ```

注意：区分`池/非池化`与`heap/direct buffer`这两个概念，前者是netty作为应用层对于内存的分配管理，后者是netty借助jdk.nio实现对直接内存的分配使用

对于每个Channel，或者绑定到ChannelHandler的ChannelHandlerContext，都能获取到一个ByteBufAllocator的引用：

```java
Channel channel = ...;
// 获取channel绑定的ByteBufAllocator引用
ByteBufAllocator allocator = channel.alloc();

ChannelHandlerContext ctx = ...;
ByteBufAllocator allocator = ctx.alloc();
```

如果在无法获得ByteBufAllocator引用的情况下，可以使用Unpooled来获得非池化ByteBuf，可用于那些并不需要Netty其他组件的非网络项目

> [为什么netty项目压测后系统内存消耗无法恢复到之前的状态？](https://www.zhihu.com/question/369299148/answer/2078134994)：涉及池化的管理

### **1.2.1 HeapByteBuf**

存放区域：JVM堆

回收机制：
- UnpooledHeapByteBuf：由JVM GC机制承担对象生命周期
- PooledHeapByteBuf：不受JVM GC影响，所以**netty分配器必须诱使JVM GC相信该对象永远都不被回收**，并通过`引用计数`机制，以**自驱动**对象移入/移除池

这种模式也被称为`支撑数组（backing array）`，对于需要在**应用业务层使用该数据的byte[]**，则建议直接使用HeapByteBuf

缺点：如果通过套接字发送该模式下的ByteBuf，**JVM将会在内部把缓冲区复制到一个`直接缓冲区`**，再由直接缓冲区将数据通过系统调用传入内核，**性能相对较差**

```java
// 非池化的HeapByteBuf，生命周期由JVM GC管理
ByteBuf buf = Unpooled.heapBuffer(size);
// 如果buf存在支撑数组，则数据存在堆缓冲区
if (buf.hasArray()) {
    byte[] array = buf.array();
    // 计算第一个可读字节的下标位置
    int offset = buf.arrayOffset() + buf.readerIndex();
    // 获得buf可读字节数
    int length = buf.readableBytes();

    handleArray(array, offset, length);
}
```

`buf.arrayOffset()`：返回支撑数组的起始下标，正常都是返回0

### **1.2.2 DirectByteBuf**

存放区域：direct buffer，属于JVM堆外内存

回收机制：由于身处堆外内存，JVM GC在正常情况下无法回收该区域的对象，netty通过`引用计数`机制进行回收
- UnpooledDirectByteBuf：不需要池化，netty通过`PlatformDependent`实现内存的分配与回收
- PooledDirectByteBuf：需要池化，netty通过`Handle`对内存移入/移除池

直接使用direct buffer是**网络数据传输的理想选择**，可以避免将数据从heap拷贝到direct buffer

> 在使用了mmap()调用后，直接缓冲区的内存区域直接物理映射到内核缓冲区，避免了用户空间到内核空间的一次内存拷贝

缺点：
- **分配和释放较为昂贵**，netty引入`池化`来降低操作花销
- **没有支撑数组**，如果业务层仍需要对byte[]进行操作，仍不得不将其转化为HeapByteBuf
- **不恰当使用将存在内存泄漏**，因为基本不受JVM GC的影响，需要牢记`release()`

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

### **1.2.3 池化/非池化**

池化：降低direct buffer和heap buffer对内存分配/销毁的开销，**尤其是direct buffer**

非池化：指不使用池化机制来进行堆/直接内存的分配

回收：

- 池化：对**处在netty池中**的PooledDirectByteBuffer/PooledHeapByteBuf不会被回收，因为netty池中会保存其引用

    注意：**netty并不会一直持有对该区域的引用**，在分配给程序后将暂时失去该引用，由程序持有引用，所以**程序必须在使用完毕后调用`release()`方法将ByteBuf返回到池中，以免发生内存泄漏**
    
    - netty引用时机：netty在已经分配了内存区域但未返回给应用程序时，或者应用在返回给netty池之后，netty池就会对其引用

    - 程序引用场景：通过netty分配器来持有该ByteBuf对象之后

    - `无引用`：**实则上发送内存泄漏**，多出现在程序在使用完毕后没有调用`release()`，并且失去对该ByteBuf引用后导致

        > 无引用的池化Heap或Direct Buffer都会导致内存泄漏！

        - PooledHeapByteBuf：**最终会被JVM GC回收**，但是netty池内部存在该ByteBuf的数据结构（没有引用关系），所以在越来越多的缓冲区**没有正常返回池中**，池的内部结构就会一直增加，最终导致OOM

        - PooledDirectByteBuf：不会被JVM GC回收，且池内部结构也会一直增加，**OOM会来的更快**

    两者的释放代码最终都由`Handle`进行处理：

    ```java
    @Override
    protected final void deallocate() {
        if (handle >= 0) {
            final long handle = this.handle;
            this.handle = -1;
            memory = null;
            chunk.arena.free(chunk, tmpNioBuf, handle, maxLength, cache);
            tmpNioBuf = null;
            chunk = null;
            recycle();
        }
    }
    ```

    > 回收对象时，暂时不会清空bytebuf上的字节内容，而是由分配时再进行重初始化

- 非池化：

    - UnpooledHeapByteBuf：生命周期由JVM GC管理，netty只提供分配的API即可

        ```java
        @Override
        protected void deallocate() {
            // freeArray点进去后是NOOP操作
            freeArray(array);
            array = EmptyArrays.EMPTY_BYTES;
        }
        ```

    - UnpooledDirectHeapByteBuf：不涉及JVM GC，netty需提供分配和释放的API

        ```java
        @Override
        protected void deallocate() {
            ByteBuffer buffer = this.buffer;
            if (buffer == null) {
                return;
            }

            this.buffer = null;

            if (!doNotFree) {
                // 调用PlatformDepent.freeDirectBuffer(buffer)进行释放
                freeDirect(buffer);
            }
        }
        ```

最佳实践：尽量避免使用HeapByteBuf，业务上减少对byte[]的使用，用ByteBuf提供的API对数据进行操作即可

1. 需要使用池化对象时，则使用PooledByteBufAllocator.DEFAULT来分配direct buffer

2. 需要使用非池化对象时，则使用Unpooled.directbuffer()

## **1.3 组合CompositeByteBuf**

如果需要将多个ByteBuf进行合并时，使用CompositeByteBuf可以替代常规**内存拷贝合并**的方式，转而采用**组合合并**的方式

> 前者是将底层字节数组合并成同一个字节数组；后者则是单纯组合，底层仍旧是多个字节数组

```java
public class CompositeByteBuf extends AbstractReferenceCountedByteBuf implements Iterable<ByteBuf> {
    private static final ByteBuffer EMPTY_NIO_BUFFER = Unpooled.EMPTY_BUFFER.nioBuffer();
    private static final Iterator<ByteBuf> EMPTY_ITERATOR = Collections.<ByteBuf>emptyList().iterator();

    private final ByteBufAllocator alloc;
    private final boolean direct;
    private final int maxNumComponents;

    private int componentCount;
    // 组合的容器，存放不同类型的ByteBuf
    private Component[] components; // resized when needed
}
```

具体的API如出一辙，这是包装器（装饰器）模式思想的体现。内部提供对这些组合对象的统一管理，并针对不同类型的ByteBuf进行具体类型的操作

```java
public class CompositeByteBuf extends AbstractReferenceCountedByteBuf implements Iterable<ByteBuf> {
    // 包装类
    interface ByteWrapper<T> {
        ByteBuf wrap(T bytes);
        boolean isEmpty(T bytes);
    }

    private int addComponent0(boolean increaseWriterIndex, int cIndex, ByteBuf buffer) {
        assert buffer != null;
        boolean wasAdded = false;
        try {
            // 添加具体bytebuf到Component数组中
            addComp(cIndex, c);
            
            // ...

            if (increaseWriterIndex) {
                // 相对应增加当前ComponentByteBuf的写索引
                writerIndex += readableBytes;
            }
            return cIndex;
        } finally {
            if (!wasAdded) {
                buffer.release();
            }
        }
    }

    @Override
    public CompositeByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        if (length == 0) {
            return this;
        }

        // 遍历Component数组实现
        int i = toComponentIndex0(index);
        while (length > 0) {
            Component c = components[i];
            int localLength = Math.min(length, c.endOffset - index);
            // 对具体的component进行操作
            c.buf.getBytes(c.idx(index), dst, dstIndex, localLength);

            // 驱动遍历索引i改变的逻辑...
        }
        return this;
    }
}
```

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

# **3. 引用计数（内存泄漏）**

`引用计数`：不同于JVM可达性分析，它是一种通过当某个对象所持有的资源不再被其他对象引用时，释放该对象所持有的资源的回收方式

对于

为了实现更高效的计数修改，ByteBuf内部的计数采用`位运算`的方式，并提供给用户的`refCnt()`接口返回**符合正常视角的计数值**

ByteBuf.refCnt值对应的含义：
- 1：表示当前buffer没有引用，调用`refCnt()`返回0
- 2：表示当前buffer有1个引用，调用`refCnt()`返回1
- N（大于2的偶数）：表示当前buffer有(n / 2)个引用，调用`refCnt()`返回(n / 2)

```java
// 假设buf的refCnt属性为2
Bytebuf buf = Unpooled.buffer();
```

# 参考
- [Netty源码解析](https://zhuanlan.zhihu.com/p/269847729)
- [mmap和directBuffer简单总结](https://blog.csdn.net/scying1/article/details/90755438)
- [netty零拷贝的理解](https://www.cnblogs.com/xys1228/p/6088805.html)
- [池化的细节](https://blog.csdn.net/qq_41594698/article/details/100509043)

# 重点参考

- [Thearas用户回答：direct buffer（C heap）/C stack/intermediate buffer的关系](https://www.zhihu.com/question/60892134)：c stack/direct buffer（c heap）都处于JVM的堆外内存，正常socket操作会**将JVM堆内数据复制到intermediate buffer中（内存复制）**，再调用系统API将intermediate buffer的数据发送至网卡。而intermediate buffer可以在direct buffer（c heap）上分配，也可以在c stack上分配，所以如果在代码层面上直接就使用direct buffer，可以省去一次内存拷贝

- [海纳用户回答：direct buffer的好处总结](https://www.zhihu.com/question/60892134)

- [ETIN用户回答：为什么需要intermediate buffer与kernel交互？为什么heap buffer的数据需要复制到intermediate buffer？](https://www.zhihu.com/question/60892134)：底层通过`write()`、`read()`、`sendfile()`等系统调用时，需要传入buffer的起始地址和buffercount作为参数，jvm堆中的buffer往往以buffer数组对象而存在，这些对象受GC影响

- [gavin用户回答：为什么需要intermediate buffer?]()：gc的时候要移动对象，地址会变，不能将gc堆的内存地址直接作为参数传给io系统调用，所以才会采用堆外中介缓冲区来实现

- [RednaxelaFX：NIO中的DirectByteBuffer的运用](https://www.zhihu.com/question/57374068)：DirectByteBuffer本身是在堆内存的，其真正承载数据的buffer则在堆外native memory中，由JVM通过malloc()分配出来，属于用户态。

- [《推特提问翻译：不同ByteBuf的GC是怎么样的？》](https://www.it1352.com/1677134.html)：包括Netty池内存泄漏的解答