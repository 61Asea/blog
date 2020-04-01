# **Java Review**
## **数据类型/基础语法**
### **Q1：简单说说Java有哪些数据类型**
基本数据类型包括：
1. 数值型：
- byte(8位)
- short(16位)
- int(32位, 有效范围[2^-31, 2^31-1]，补码存取，符号位)
- long(64位)
- float(32位)
- double(64位) 
2. 字符型：char
3. 布尔型：boolean

除了基本类型外，其他数据类型属于引用类型，包括：***类，接口，数组***

普通编程领域，可以缺省使用double，除非需要在运算量大但精度要求低的领域，才需要考虑float。float在十进制下至少有6位数字精度

### **Q2：float number = 3.4的问题**
默认小数为双精度double数，double赋值到float上为向下转型，会造成精度损失，所以必须进行强制类型转换，正确写法为
    
    float number = 3.4f || float number = (float)3.4;

类似的问题还有：

    float a = 1.1f + 0.2f;
    float b = 1.3f;
    syso(a == c); // false
    syso(Math.abs(a - b) < 0.00000001) // true

### **Q3：字符串拼接方式与效率**
1. “+”，但是考虑到String是final对象，不会被修改，所以在拼接的时候生成的"hello world"为**新的对象**，***效率低，线程安全***

        String hello = "hello";
        String world = " world";
        String str = hello + world; // 三个final字符串
        String str1 = "hello" + "world" // 这种方式效率不比StringBuffer慢甚至更快，因为jvm做了优化

2. StringBuffer可变字符串, 效率较高，线程安全，底层用了悲观锁synchronized独占

        StringBuffer str = new StringBuffer("hello");
        str.append(" world");

3. StringBuilder，效率最高，但线程不安全

### **Q4：final, finally和finalize区别**
1. final用于修饰类，方法和变量。若使用它在某种程度上保证对象，变量不可变，可以形成线程安全
- final修饰的类**不可继承**
- final修饰的变量**引用不可变，值可变**
- final修饰的方法**不可重写**
2. finally是try-catch块的最后必须执行的代码块，可以用来释放资源等等
3. finalize是Object类的方法，在对象被GC回收前会调用一次，用于资源回收

### **Q5：==和equals的区别, equals和hashCode的联系**
1. ==，对比两个基本类型的数值是否相同，或对比两个对象的引用是否完全相同
2. 若没重写，equals默认按照==进行比较，如果重写了对象的equals方法，按照定制规则进行比较
3. 两个对象如果相等，那么它们的hashCode必须相等，但如果两个对象的hashCode值相等，它们不一定相等

hashCode：JVM每new一个对象，都会将这个Object丢到一个哈希表里，众所周知，哈希表的内部实现是数组+链表。相同hashCode的对象都会放在链表上，所以如果这个object确实equal，必要前提就是它的hashCode要等于object的hashCode

### **Q6：Array和ArrayList的区别**
1. Array长度在定义之后就不允许改变了，而ArrayList是长度可变，可以自动扩容
2. Array只能存储相同类型的数据，ArrayList可以存储不同类型数据
3. ArrayList有更多操作数据的方法

### **Q6 extra: ArrayList和HashMap的扩容问题**
ArrayList默认容量为10，HashMap默认容量为16，因此到相同size时，ArrayList需要更多的扩容操作

阿里规范有提到关于ArrayList和HashMap的声明与初始化策略，HashMap在长度临界点时，会自动扩容，而ArrayList是在数组长度不够时再做扩容
- ArrayList的oldCapacity右移一位，新增原来长度的一半，即每次扩容是原来的1.5倍。并把原来的数组复制到这个更大的数组中
        
        // 不要直接初始化, 通过返回去构造比较好
        List<String> list;

- HashMap扩容为两倍当前容量

        /** 
            空间换时间的思路，减少扩容的概率，负载因子(即loader, 默认0.75)
            initialCapacity = (需要存储的元素个数 / 负载因子) + 1；
            底层的tableForSize(initialCapacity)方法会将参数变为2的倍数
        **/
        new HashMap<>(int initialCapacity);

### **Q7：&和&&的区别**
&具有**按位与**和**逻辑与**两个功能
&&具有短路的特点，当前面false时，不会进行后面表达式判断，可以用来避免空指针异常

### **Q8：JDK8新特性**
1. stream流，扩展了集合框架的不足
2. lambda
3. 函数式接口(实现自定义lambda)，函数式接口有且只有一个抽象方法
4. 接口中可以添加default修饰的非抽象方法，可以有方法体和内容

### **Q9：Stream流**
#### Arrays.stream/Stream.of

  Arrays.stream是为了弥补Stream.of无法使用基本类型数组参数。

    int[] arrays = {1, 2, 3};
    // 会把arrays当成一个对象
    Stream.of(arrays).foreach(System.out :: println); // [I@27d6c5e0

    // 弥补Stream.of的策略
    Arrays.stream(arrays).foreach(System.out :: println); // 1, 2, 3

#### IntStream/DoubleStream/LongStream数值流

聚合方法：
- rangeClosed/range（左右开闭不同）: 返回子序列[a, b]或[a, b)
- sum: 计算元素综合
- sorted: 排序元素

迭代器：

        IntStream.generator(new IntSupplier() {
            @Override
            public int getAsInt() {}
        }).limit(n).toArray();

### **Q10: 代码块（2019京东秋招）**
#### 一个类的初始化顺序

**类内容（静态变量、静态初始化块） => 实例内容（变量、初始化块、构造器）**

        public class Handler {
            // 静态代码块，只执行一次
            static {
                System.out.println("static");
            }

            // 构造代码块，每次调用构造方法前执行
            {
                System.out.println("construct");
            }

            // 构造函数
            public Handler() {
                System.out.println("handler");
            }
        }

        public static void main (String[] args) {
            new Handler();
            new Handler();
        }

        // "static construct handler construct handler"

#### 两个具有继承关系类的初始化顺序

父类的（静态变量、静态初始化块）=> 子类的（静态变量、静态初始化块）=> 父类的（变量、初始化块、构造器）=> 子类的（变量、初始化块、构造器）

        public class CodeBlock {
            public CodeBlock(int index) {
                System.out.println("index:" + index);
            }

            static {
                System.out.println("Static");
            }

            {
                System.out.println("init");
            }
        }

        public class Another extends CodeBlock{
            static {
                System.out.println("Another static");
            }

            {
                System.out.println("Another init");
            }

            public Another(int index) {
                super(index);

                System.out.println("Another constructor");
            }

            public static void main(String args[]) {
                Another another1 = new Another(1);
                Another another2 = new Another(2);

                /**
                    执行结果为：
                        Static
                        Another init
                        Construct
                        index: 1
                        Another init
                        Another constructor
                        Construct
                        index: 2
                        Another init
                        Another constructor
                **/
            }
        }

## **面向对象**
### **Q1：简述面向对象特性**
#### 1. 封装
建议成员变量私有，然后提供公有的getter/setter方法来获取值/赋值，封装的核心思想是合理隐藏，合理暴露，可以提高安全性，实现代码的组件化

eg: modelBean

#### 2. 继承
一种子类到父类的关系，是“is a”关系，可以提高代码的复用性，相同代码可写到父类，子类的功能更加强大，不仅得到了父类的功能，还有自己的功能

tip：使用组合的方式会更好

#### 3. 多态
同一个类型的对象执行相同的行为，在不同的状态下表现出不同的特征。多态可以降低类之间的耦合度，右边对象可以实现组件化切换，业务功能随之改变，便于扩展和维护

eg： 接口，工厂

### **Q2：类和对象**
类是一个抽象的概念，是具有相同特征的事物的描述，***对象的模板***。

对象是一个个具体的存在，是类的实例

### **Q3：列举Object类的方法**
#### equals
判断其他对象是否与当前对象的引用是否相等，实战中无意义

应该重写该方法，需要检测两个对象状态的相等性，如果两个对象的状态相等，就认为这两个对象是相等的

#### toString
打印当前对象的字符串表示

#### wait
导致当前线程等待，等待其他线程唤醒，会释放锁

#### notify/notifyAll
随机唤醒一个/全部线程

#### hashCode
返回当前对象的hashCode值

#### finalize
当垃圾回收器要回收对象前调用

#### clone
创建并返回对象的一个副本, 属于浅拷贝

- 可以implements Cloneable接口，重写clone方法来自实现深拷贝(不推荐，写到手酸)
- 可以implements Serializable, 利用序列化来实现深拷贝，这种方式会先把源转为二进制流，再将二进制流反序列化为一个全新的java对象（本来java对象们都待在虚拟机堆中，通过序列化，将源对象的信息以另外一种形式存放在了堆外，再将堆外的这份信息通过反序列化的方式再放回到堆中）

        Class Person implements Serializable {
            private String name;

            // getter and setter...

            private Person cloneBySerializable() {
                BufferArrayOutputStream output = new BufferArrayOutputStream();
                ObjectOutputStream cor = new ObjectOutputStream(output);
                // 通过对象流，将源转为二进制流存在内存中
                cor.write(this);

                // 读取输出流二进制源，传入到字符输入流中
                BufferArrayInputStream input = new BufferArrayInputStream(
                    output.toByteArray()
                );
                ObjectInputStream corInput = new ObjectInputStream(input);
                // 用对象输入流去装饰，直接readObject读出来
                return (Person) corInput.readObject();
            }
        }

### **Q4: 方法重载和方法重写的区别**
方法重载是同一个类中具有不同参数列表的同名方法（无关返回值类型）
eg: 一个类有多个构造器

方法重写是子类中具有和父类相同参数列表的同名方法，会覆盖父类原有的方法

子类重写父类方法需要遵循：
- 返回值类型小于等于父类被重写方法的返回值类型，即必须是父类方法返回值的子类或不修改（用父类对象引用来调用子类重写的方法，也就是说要把A的子类对象引用赋给A的对象引用，如果此时返回值类型不是A类或A的子类, 其他类的对象引用是不能赋给A的对象引用的）
- 修饰符权限大于等于父类被重写方法权限修饰符（在父类中是public的方法，如果子类中将其降低访问权限为private，那么子类中重写以后的方法对于外部对象就不可访问了，这个就破坏了继承的含义）
- 抛出的异常类型小于等于父类被重写方法抛出的异常类型

### **Q5: 接口与抽象类的区别**
- 接口中只能定义public staic final修饰的常量，抽象类中可以定义普通变量
- 接口和抽象类**都不能实例化**，但接口没有构造器，**抽象类有构造器**
- 接口可以多实现，抽象类只能单继承
- 接口在JDK1.8之前只能定义public abstract修饰的方法，JDK1.8开始可以定义默认方法(default)和静态方法，JDK1.9开始可以定义私有方法，抽象类中的方法没有限制

### **Q6：什么时候用接口，什么时候用抽象类**
- 如果知道某个类应该成为基类，那么第一选择应该是让它成为一个接口，只有在必须要有方法定义和成员变量的时候，才应该选择抽象类
- 在接口和抽象类的选择上，必须遵守这样一个原则：行为模型应该总是通过接口而不是抽象类定义。通过抽象类建立行为模型会出现的问题：如果有一个抽象类Moblie，有两个继承它的类Mobile1和Moblie2，分别有自己的功能1和功能2，如果出现一个既有功能1又有功能2的新产品需求，由于Java不允许多继承就出现了问题，而如果是接口的话只需要同时实现两个接口即可
- 组合与继承的关联，可以将本身想通过继承复用的代码写到一个类中，再将该类组合到具体场景下

### **Q7： 内部类有什么作用？有哪些分类？**
内部类有更好的封装性，有更多的权限修饰符，封装性可以得到更多的控制
- 需要用一个类来解决一个复杂的问题，但是又不希望这个类是公共的
- 需要实现一个接口，但不需要持有它的引用

#### 4种内部类
- 静态内部类：由static修饰，属于类本身，只加载一次。类可以定义的成分静态内部类都可以定义，可以访问外部类的静态变量和方法，通过new 外部类.静态内部类构造器来创建对象

#### 借助JVM保证一个类的构造方法在多线程环境下被正确地加锁、同步来初始化，来实现线程安全
        
        // 内部类单例模式，保证了线程安全，饿汉式
        public class Singleton {
            public static class Inner {
                private static final Singleton instance = new Singleton();
            }

            public static Singleton getInstance() {
                return Inner.instance;
            }
        }


- 成员内部类：属于外部类的每个对象，随对象一起加载。不可以定义静态成员和方法，可以访问外部类的所有内容

        public class MemberInner {
            private int a = 1;

            class Inner {
                private int a = 2;
                public void innerMethod() {
                    int a = 3;
                    System.out.println(a); // 3
                    System.out.println(this.a); // 2
                    System.out.println(MemberInner.this.a); // 1
                }
            }

            public static void main(String[] args) {
                MemberInner memberInner = new MemberInner();
                // 内部类依附于外部类存在，需要外部类实例来进行new
                MemberInner.Inner inner = memberInner.new Inner();
                inner.innerMethod();
            }
        }

- 局部内部类：定义在方法、构造器、代码块、循环中。只能定义实例成员变量和实例方法，作用范围仅在局部代码块中
- 匿名内部类：没有名字的局部内部类，可以简化代码，匿名内部类会立即创建一个匿名内部类的对象返回，对象类型相当于当前new的类的子类类型

thinking in java 上说内部类是为了解决多继承问题（the inner class is as the rest of the solution of the multiple-inheritance problem）

那么，为什么我们需要多继承？为什么将一个类嵌套在另一个类中来实现另外的类呢？为什么不将内部类移出来作为一个平常的类呢？

最深层次的原因是内部类的特性：内部类可以访问它所在的外部类的所有元素，包括私有成员和私有方法。也就是说实现了某一接口或类的内部类需要访问外部类的元素来实现自身功能，或者说内部类与外部类结合来实现某种功能，我认为这才是内部类的最终用法

### **Q7 EXTRA: 为什么内部类调用的外部变量必须是final修饰的**
为了解决：局部变量的生命周期与局部内部类的对象的生命周期的不一致性问题

因为生命周期的原因。方法中的局部变量，方法结束后这个变量就要释放掉，final保证这个变量始终指向一个对象。首先，内部类和外部类其实是处于同一个级别，内部类不会因为定义在方法中就会随着方法的执行完毕而跟随者被销毁。问题就来了，如果外部类的方法中的变量不定义final，那么当外部类方法执行完毕的时候，这个局部变量肯定也就被GC了，然而内部类的某个方法还没有执行完，这个时候他所引用的外部变量已经找不到了。如果定义为final，java会将这个变量复制一份作为成员变量内置于内部类中，这样的话，由于final所修饰的值始终无法改变，所以这个变量所指向的内存区域就不会变

### **Q8: 泛型和泛型擦除是什么**

## **I/O流**
#### 如何理解input和output
为了在程序结束后某些数据得以保存,IO可以帮我们将数据存储到持久化设备中(硬盘,U盘)

程序运行时的数据时在内存中,使用IO流可以帮我们把内存数据存储到持久化设备中,
- 内存数据存储到持久化设备--输出(Output)操作:我给你东西,对我来说东西是出去了(写)
- 持久化设备读取到内存中--输入(Input)操作:别人给我东西,对我来说就是进来(读)

#### 1. 字节流和字符流
字符流的由来： 因为数据编码的不同，而有了对字符进行高效操作的流对象。本质其实就是基于字节流读取时，去查了指定的码表。 字节流和字符流的区别：
- 读写单位不同：字节流以字节（8bit）为单位，字符流以字符为单位，根据码表映射字符，一次可能读多个字节。
- 处理对象不同：字节流能处理**所有类型的数据（如图片、avi等）**，而字符流只能处理**字符类型==的数据。

***结论：只要是处理纯文本数据，就优先考虑使用字符流。 除此之外都使用字节流***

#### 2. 输入流和输出流
OutputStream是所有的输出字节流的父类：
- 它是个抽象类
- ByteArrayOutputStream/FileOutputStream是两种基本的介质流，它们分别向Byte数组(内存缓存)、文件(外存)写入数据
- PipedOutputStream是向与起塔线程共用的管道中写入数据
- ObjectOutputStream和所有FilterOutputStream的子类都是装饰流（BufferedOutputStream、DataOutputStream、PrintStream）
- BufferedOutputStream和DataOutputStream是并行关系，谁套谁都行，区别就是代码中先加入谁的方法而已，BufferedOutputStream是将OutputStream的内容先发到内存缓冲区，再从缓冲区发到文件或其他主机；DataOutputStream主要是用于将基本Java数据类型写入输出流中，简单的byte类型用原始的outputStream就够了

        // 这个demo会将write的东西，写入到内存中
        ByteArrayOutputStream basicOutputStream = new ByteArrayOutputStream();
        BufferedOutputStream bufferCorOutput = new BufferedOutputStream(basicOutPutStream);
        DataOutputStream dataBufferCorOutput = new DataOutputStream(bufferDCorOutput);
        dataBufferCorOutput.write(...);

        // 输出流存放的内存的字节数组
        System.out.println(basicOutputStream.toByteArray());

InputStream是所有输入字节类的父类：
- ByteArrayInputStream、StringBufferInputStream、FileInputStream 是三种基本的介质流，它们分别从Byte 数组、StringBuffer、和本地文件中读取数据。PipedInputStream 是从与其它线程共用的管道中读取数据

# 参考：
- [2021Java实习必看面试两百题解析](https://blog.csdn.net/qq_41112238/article/details/105074636)
- [集合初始化时应指定初始值大小](https://blog.csdn.net/zhuolou1208/article/details/81252090)
- [initialCapaCity存疑](https://zhuanlan.zhihu.com/p/39924972)
- [static静态变量和静态代码块的执行顺序](https://blog.csdn.net/sinat_34089391/article/details/80439852)
- [关于Java的Object.clone()方法与深浅拷贝](https://www.cnblogs.com/nickhan/p/8569329.html)
- [Java IO流学习总结](https://zhuanlan.zhihu.com/p/28757397)
- [内部类用处的思考](https://www.iteye.com/blog/sunxg-557846)
- [谈谈对4种内部类的理解，和使用场景分析](https://blog.csdn.net/xinzhou201/article/details/81950188)