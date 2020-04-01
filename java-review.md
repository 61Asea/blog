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
1. 一个类的初始化顺序

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

2. 两个具有继承关系类的初始化顺序

**父类的（静态变量、静态初始化块）=> 子类的（静态变量、静态初始化块）=> 父类的（变量、初始化块、构造器）=> 子类的（变量、初始化块、构造器）**

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

# 参考：
- [2021Java实习必看面试两百题解析](https://blog.csdn.net/qq_41112238/article/details/105074636)
- [集合初始化时应指定初始值大小](https://blog.csdn.net/zhuolou1208/article/details/81252090)
- [initialCapaCity存疑](https://zhuanlan.zhihu.com/p/39924972)
- [static静态变量和静态代码块的执行顺序](https://blog.csdn.net/sinat_34089391/article/details/80439852)