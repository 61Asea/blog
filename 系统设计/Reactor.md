# Reactor模型

> **connection per thread**：每一个socket都是阻塞的（第二阶段），所以一个线程同一时间只能处理一个socket，那么每线程对应多个socket的话，会造成其它客户端响应延迟。

早期的服务器架构多采用connection per thread模型，通过多线程方式提升服务器的吞吐量，但这种方式的缺点显而易见：
- 线程的系统资源开销大，资源有限
- 线程的反复创建、销毁代价极大，虽然可以用线程池进行更深层次的优化

为了减少系统线程资源的开销，更进一步地提升系统的吞吐量，Reactor模式出现了，并成为目前大多数I/O相关的组件如：Redis、Netty、Zookeeper、JavaNIO都在使用的IO模式

Reactor实现思想来源于`I/O多路复用模型`，使用它来取代同步阻塞I/O中使用多线程实现connection per thread处理方式，可以显而易见的节省系统资源

> 在高性能I/O系统设计中，有两个著名的模式：`Reactor`和`Proactor`，前者用于同步I/O，后者用于异步I/O

# **1. 基础设施**

**基本I/O模型**：

I/O多路复用（事件驱动I/O模型）

> [《select&poll&epoll》]()、 [《I/O多路复用》]()：重点关注调用select/poll/epoll线程、poll返回后如何分配事件、epoll的ET模式

## **1.1 具体成分**：

![Reactor_Structures](https://asea-cch.life/upload/2021/08/Reactor_Structures-52304180f0fc4557bb4c36f2885f045a.png)

- **Handle**

    作用：操作系统中文件句柄（**文件描述符fd**），可以是一个打开的文件、一个socket连接，在这里代指一个网络连接（Connection，在Java NIO中的**Channel**）

    **Channel会注册到Synchronous Event Demultiplexer中，以监听fd就绪情况**，对于`ServerSocketChannel`可以是`CONNECT`事件，对于`SocketChannel`可以是`READ`、`WRITE`、`CLOSE`事件

    > 疑问：ServerSocketChannel是不是对应epfd，SocketChannel对应socketfd？
    
    答：selector对象实例对于epfd，socketchannel对于socket的fd，serversocketchannel也有对应fd，在初始化的时候先行注册

- **Initiaion Dispatcher**

    作用：**Event Handler**的容器，用于注册、移除EventHandler等**管理操作**
    
    它还作为Reactor模式的入口，调用**Synchronous Event Demultiplexer**（内核select/epoll）阻塞以等待事件返回。当阻塞等待返回时，根据event类型将发生的**Handle**分发给对应的Event Handler处理，即调用Event Handler的`handle_event()`方法

- **Synchronous Event Demultiplexer**

    作用：阻塞等待fd数据就绪，如果阻塞等待返回，即表示在返回的**Handle中可以不阻塞的执行**返回的事件类型

    > 该模块一般使用操作系统的**select/poll/epoll**实现，在Java NIO中使用Selector进行封装，当Selector.select()返回时，可以调用Selector的`selectedKeys()`方法获取Set\<SelectionKey\>，一个SelectionKey代表一个有事件发生的Channel以及Channel上的event类型，设置Handle的状态，然后返回

- **Event Handler**

    继承自`Concrete Event Handler`，定义事件处理方法：handle_event()，以供Intiation Dispatcher回调使用

## **1.2 流程**

有**一个或多个并发输入源**，有一个**Service Handler**，有多个**Request Handlers**，Service Handler会**同步**的将输入请求多路复用的分发给相对应的request handler

![reactor简易模型](https://asea-cch.life/upload/2021/08/reactor%E7%9A%84%E6%9C%B4%E7%B4%A0%E6%A8%A1%E5%9E%8B-8b8f643f899a41ababe4a2f5c88017a5.png)

有多个不同的`EventHandler`来处理不同的请求，`Initiation Dispatcher`用于管理EventHandler，EventHandler首先要注册到InitiationDispatcher中，然后Initiation Dispatcher根据输入的Event类型分发给注册的EventHandler；然而Initiation Dispatcher并不监听Event的到来，这个工作交由`Synchronous Event Demultiplexer`处理

- Event Handlers -> Request Handlers
- Initiation Dispatcher和Synchronous Event Demultiplexer -> Service Handler的两种不同职责
    - Initiation Dispatcher -> reactor
    - Synchronous Event Demultiplexer -> acceptor

**具体交互流程如下**：
1. 初始化Initiation Dispatcher，并初始化Handle（ServerSocketChannel）映射Initiation Dispatcher上

    Innitiation Dispatcher主要负责根据事件类型进行dispatch，以及**注册/删除**Event Handler

2. 注册Event Handler到Initiation Dispatcher中，每个Event Handler应包含对相应Handle的引用

    > 第一个注册Event Handler是AcceptorEventHandler，用于接收CONNECT事件来创建Event Handler处理新连接

3. 调用Intiation Dispatcher的handle_events()方法以启动Event Loop，在Event Loop中，调用select()方法（Synchronous Event Demultiplexer）阻塞等待Event发生

4. 当某个Handle的Event发生后，select()方法返回，IntiationDispatcher根据返回的Handle，并回调该Event Handler的回调方法（Java返回的selectedKey就是event handler对象，key.attachment()就是之前注册的回调方法）

# **2. 单线程Reactor**

一种简单的Reactor模型，称为**Reactor朴素原型**，单线程指的是：`reactor`与`handler`处于同一个线程上

具体应用案例：使用main线程实现Java的NIO模式的Selector网络通讯

在Java中，用Selector类封装了Synchronous Event Demultiplexer的功能。不同于使用Intiation Dispatcher来管理Event Handler，Java通过SelectedKey的`attchment对象`来存储对应的`Event Handler`，这样在select()方法返回时可直接调用，无须注册EventHandler这个步骤，或者说设置Attachment就是这里的注册

![单线程Reactor](https://asea-cch.life/upload/2021/08/%E5%8D%95%E7%BA%BF%E7%A8%8BReactor-89f6be31248d486c9fc3ace557b6c8ef.jpg)

```java
package nio.single;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Handler implements Runnable {
    private static int READING = 1;

    private static int SENDING = 2;

    private Selector selector;

    private SocketChannel client;

    private SelectionKey sk;

    private int state = READING;

    private ByteBuffer inputBuffer = ByteBuffer.allocate(1024);

    private ByteBuffer outputBuffer = ByteBuffer.allocate(1024);

    public Handler(Selector selector, SocketChannel socketChannel) throws IOException {
        this.selector = selector;
        this.client = socketChannel;
        this.client.configureBlocking(false);
        this.sk = client.register(selector, SelectionKey.OP_READ, this);
        this.selector.wakeup();
    }

    public boolean inputIsComplete() {
        return false;
    }

    public boolean outputIsComplete() {
        return false;
    }

    public void read() throws IOException {
        client.read(inputBuffer);
        if (inputIsComplete()) {
            // 执行业务逻辑，传入inputBuffer
            state = SENDING;
            sk.interestOps(SelectionKey.OP_WRITE);
        }
    }

    public void write() throws IOException {
        client.write(outputBuffer);
        if (outputIsComplete()) {
            state = READING;
            sk.interestOps(SelectionKey.OP_READ);
        }
    }

    @Override
    public void run() {
        try {
            if (state == READING) {
                read();
            } else if (state == SENDING) {

            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
```

单线程模型下的Reactor，既需要处理监听事件，还要处理分发，甚至在真实业务上还是通过该线程进行处理：

```java
package nio.single;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Reactor implements Runnable {
    protected Selector selector;

    protected ServerSocketChannel ssc;

    public Reactor(int port) {
        init(port);
    }

    public void init(int port) {
        try {
            selector = Selector.open();
            ssc = ServerSocketChannel.open();
            ssc.socket().bind(new InetSocketAddress(port));
            ssc.configureBlocking(false);

            SelectionKey sk = ssc.register(selector, SelectionKey.OP_ACCEPT);
            sk.attach(new Acceptor());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void dispatch(SelectionKey key) {
        Runnable r = (Runnable) key.attachment();
        if (r != null) {
            r.run();
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) ;
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                dispatch(it.next());
            }
            keys.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class Acceptor implements Runnable {
        @Override
        public void run() {
            try {
                SocketChannel socketChannel = ssc.accept();
                new Handler(selector, socketChannel);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new Reactor(10000).run();
    }
}
```

优点：相比传统模型，Reactor可以减少线程的使用，并提供了模块化、解耦功能等优点

缺点：I/O读写数据和接收新连接都是在同一个线程实现的，共享一个Reactor的Channel如果出现某个长时间的数据读写阻塞，会影响其它Channel执行，得不到即使处理导致响应时间变长，且这个期间也不能接收新的连接

# **3. 多线程Reactor**

基于单线程Reactor的缺点，进行以下改进：
1. 将Handler处理器的执行放入线程池中，多线程进行业务处理
2. 将Reactor的职责拆分，演化为一个Main Reactor和多个Sub Reactor，Main Reactor主要处理CONNECT事件，而多个Sub Reactor来处理READ、WRITE事件，这些可以分别在自己的线程中执行

具体应用案例：Netty

![多线程Reactor](https://asea-cch.life/upload/2021/08/%E5%A4%9A%E7%BA%BF%E7%A8%8BReactor-d4b943e8970c43bbbcc50ee6e595d6ae.jpg)

以下代码将Reactor拆分为Main和Sub两种概念，Main Reactor主要负责监听socket连接，Sub Reactor用于负责connection的读/写操作，每个Reactor起一个线程

在EventHandler处理中，将业务逻辑丢入线程池中处理，防止业务阻塞

```java
package nio.current;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

/**
 * 拆分出Main Reactor和Sub Reactor的概念
 *
 */
public class CurrentReactor implements Runnable {
    public Selector sl;

    public ServerSocketChannel ssc;

    public SubReactor[] subReactors;

    public CurrentReactor(int port, int nThread) throws IOException {
        sl = Selector.open();
        ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));
        ssc.configureBlocking(false);
        // ConcurrentAcceptor会将往subSls中注册可读事件的fd
        ssc.register(sl, OP_READ, new ConcurrentAcceptor(this));
        this.subReactors = new SubReactor[nThread];
        for (int i = 0; i < 2; i++) {
            subReactors[i] = new SubReactor(Selector.open());
        }
    }

    public void dispatch(SelectionKey k) {
        Runnable r = (Runnable) k.attachment();
        if (r != null) {
            r.run();
        }
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < subReactors.length; i++) {
                // 启动处理connection的线程
                new Thread(subReactors[i]).start();
            }

            while (!Thread.interrupted());
            sl.select();
            Set<SelectionKey> keySet = sl.selectedKeys();
            Iterator<SelectionKey> it = keySet.iterator();
            while (it.hasNext()) {
                dispatch(it.next());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    class SubReactor implements Runnable {
        private Selector subSelector;

        SubReactor(Selector subSelector) {
            this.subSelector = subSelector;
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted());
                this.subSelector.select();
                Set<SelectionKey> keySet = sl.selectedKeys();
                Iterator<SelectionKey> it = keySet.iterator();
                while (it.hasNext()) {
                    dispatch(it.next());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        CurrentReactor mainCurrentReactor = new CurrentReactor(10000, 2);
        mainCurrentReactor.run();
    }
}
```

Acceptor被单独拆分出来：

```java
package nio.current;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ConcurrentAcceptor implements Runnable {
    public int next = 0;

    public ServerSocketChannel ssc;

    public CurrentReactor.SubReactor[] subReactors;

    public ConcurrentAcceptor(CurrentReactor currentReactor) {
        this.ssc = currentReactor.ssc;
        this.subReactors = currentReactor.subReactors;
    }

    @Override
    public void run() {
        try {
            SocketChannel client = ssc.accept();
            if (client == null) {
                return;
            }

            new CurrentHandler(subReactors[next].subSelector, client);
            if (++next == subReactors.length) {
                next = 0;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

```

EventHandler线程池对真实业务进行处理，防止业务阻塞影响到其他连接的响应

```java
package nio.current;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class CurrentHandler implements Runnable {

    private Selector sl;

    private SocketChannel sc;

    // 对应channel
    private SelectionKey sk;

    private ByteBuffer input = ByteBuffer.allocate(1024);

    private ByteBuffer output = ByteBuffer.allocate(1024);

    private Sender sender;

    private final static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(5);

    public CurrentHandler(Selector sl, SocketChannel sc) throws IOException {
        this.sl = sl;
        this.sc = sc;
        this.sc.configureBlocking(false);
        this.sk = this.sc.register(sl, SelectionKey.OP_READ, this);
    }

    public void handle() {
        // 真正的业务逻辑
        // ...

        if (this.sender == null) {
            this.sender = new Sender(this);
            sk.attach(this.sender);
        }
        sk.interestOps(SelectionKey.OP_WRITE);
        sl.wakeup();
    }

    public void process() throws IOException {
        sc.read(input);
        if (isInputComplete()) {
            executor.execute(() -> handle());
        }
    }

    public boolean isInputComplete() {
        return false;
    }

    class Sender implements Runnable {
        CurrentHandler handler;

        Sender(CurrentHandler handler) {
            this.handler = handler;
        }

        public boolean isOutputComplete() {
            return false;
        }

        public void run() {
            try {
                sc.write(output);
                if (isOutputComplete()) {
                    sk.attach(handler);
                    sk.interestOps(SelectionKey.OP_READ);
                    sl.wakeup();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            process();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

# 参考
- [SelectionKey理解](https://www.cnblogs.com/burgeen/p/3618059.html)

# 重点参考
- [Reactor模式简介](https://www.cnblogs.com/crazymakercircle/p/9833847.html)
- [Reactor模式详解](http://www.blogjava.net/DLevin/archive/2015/09/02/427045.html)
- [Scalable IO in Java - 笔记](https://www.cnblogs.com/luxiaoxun/archive/2015/03/11/4331110.html)