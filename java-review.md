# **Java Review**
## **1. 数据类型/基础语法**
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

## **2. 面向对象**
### **Q1：简述面向对象特性**
#### 1. 封装
建议成员变量私有，然后提供公有的getter/setter方法来获取值/赋值，封装的核心思想是合理隐藏，合理暴露，可以提高安全性，实现代码的组件化

eg: modelBean

#### 2. 继承
一种子类到父类的关系，是“is a”关系，可以提高代码的复用性，相同代码可写到父类，子类的功能更加强大，不仅得到了父类的功能，还有自己的功能

tip：使用组合的方式会更好

#### 3. 多态
同一个类型的对象执行相同的行为，在不同的状态下表现出不同的特征。多态可以降低类之间的耦合度，右边对象可以实现组件化切换，业务功能随之改变，便于扩展和维护

- 本质为对引用的虚函数表在运行时进行覆盖，即编译时类型的引用表现出运行时类型的行为
- 向上兼容，所以多态是无需强制类型转换的（即运行时类型是编译时类型的子类时），上面无需：Father f = (Father)new Son()
- 

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
- 静态内部类：HashMap的静态内部-Node实现类

thinking in java 上说内部类是为了解决多继承问题（the inner class is as the rest of the solution of the multiple-inheritance problem）

那么，为什么我们需要多继承？为什么将一个类嵌套在另一个类中来实现另外的类呢？为什么不将内部类移出来作为一个平常的类呢？

最深层次的原因是内部类的特性：内部类可以访问它所在的外部类的所有元素，包括私有成员和私有方法。也就是说实现了某一接口或类的内部类需要访问外部类的元素来实现自身功能，或者说内部类与外部类结合来实现某种功能，我认为这才是内部类的最终用法

### **Q7 EXTRA: 为什么内部类调用的外部变量必须是final修饰的**
为了解决：局部变量的生命周期与局部内部类的对象的生命周期的不一致性问题

因为生命周期的原因。方法中的局部变量，方法结束后这个变量就要释放掉，final保证这个变量始终指向一个对象。首先，内部类和外部类其实是处于同一个级别，内部类不会因为定义在方法中就会随着方法的执行完毕而跟随者被销毁。问题就来了，如果外部类的方法中的变量不定义final，那么当外部类方法执行完毕的时候，这个局部变量肯定也就被GC了，然而内部类的某个方法还没有执行完，这个时候他所引用的外部变量已经找不到了。如果定义为final，java会将这个变量复制一份作为成员变量内置于内部类中，这样的话，由于final所修饰的值始终无法改变，所以这个变量所指向的内存区域就不会变

### **Q8: 泛型和泛型擦除是什么**

#### **泛型**
宽泛的数据类型，接受任意类型的数据，也即是将数据类型参数化，允许强类型程序设计语言在编写代码时定义一些可变部分，把**类型明确的工作推迟到创建对象或调用方法的时候才去明确**的特殊的类型

#### **泛型擦除**
1. raw type

    1.5向后兼容，为了满足以下的代码在旧版本的JVM照样可以运行,引入了raw type概念

        ArrayList<Integer> iist1 = new ArrayList<Integer>();
        ArrayList<String> slist = new ArrayList<String>();
        ArrayList list;
        list = ilist;
        list = slist;

    泛型实例化的ArrayList<Integer>和ArrayList<String>而言，ArrayList必须是他们的共同超类。若要实现这种思想，以C++或C#的就编译时模板展开会相当困难，而要支持这种raw type，最直接的办法就是通过擦除法来实现泛型

    ***即编译时确实是泛型的，但是编译结束后泛型类型信息被擦除（已经指定具体类型的泛型信息除外），变成了Object，这也意味着泛型具体类型不能为int、long这些基本数据类型***

2. 原始类型

    如：List<String>最后编译后会被擦除变成List, JVM看到的只是List，其中的泛型变量都将变为原始类型
    - 无限定的类型变量，如T，将用Object替换
    - 有限定的则为限定类型替换

        // 证明1：
        List<String> slist = new ArrayList<String>();
        slist.add("hello");
        List<Integer> ilist = new ArrayList<Integer>();
        ilist.add(1);
        Syso(slist.getClass() == ilist.getClass()); // true

        // 证明2, 利用反射调用add方法适，可以存储整型，说明String泛型实例在编译之后被擦除掉了，只保留了原始类型

        slist.getClass().getMethod("add", Object.class).invoke(slist, 1);

    无限定的类型变量用Object替换

3. 类型擦除引起的问题与解决方法

    - 编译时被擦除，那么按正常逻辑是可以在ArrayList<String>中add一个数值的，所以为了防止这种情况，会在编译之前检查。类型检查是针对引用而言，谁是一个引用，用这个引用调用泛型方法，就会对这个引用调用的方法进行类型检测，而无关它真正引用的对象
    - 因为擦除会导致所有的泛型变量最后都变成原始类型，所以虽然泛型信息会被擦除掉，但是会将(E) elementDate[index]编译为(xxx)elemenetData[index]，就不用我们自己进行强转。（不用写是因为编译时类型还没有擦除，编译器可以识别出你返回的是string，你也可以多此一举的强制转换，类型擦除是运行时）


#### 泛型接口
通过类去实现这个泛型接口的时候指定泛型T的具体类型，见过的运用常见有mybatis的逆向工厂，生产一个通用的BaseMapper接口，接口方法可以通过T来实现方法参数或返回类型的参数化

#### 泛型类
在编译器，是无法知道K和V具体是什么类型，只有在运行时才会真正根据类型来构造和分配内存，同时

#### **demo**

    public interface GenericInterface<T> {
        public T test();
    }

    public class CodeBlock {
        public CodeBlock(int index) {
            System.out.println("index:" + index);
        }

        static {
            System.out.println("Static");
        }

        {
            System.out.println("Construct");
        }
    }

    public class Generic<K, V> implements GenericInterface<String> {
        private K key;

        private V value;

        public K getKey() {
            return key;
        }

        public void setKey(K key) {
            this.key = key;
        }

        public V getValue() {
            return value;
        }

        public void setValue(V value) {
            this.value = value;
        }

        // test方法的返回类型变成了实现泛型接口传入的具体类型
        @Override
        public String test() {
            return "hello world";
        }

        @Override
        public String toString() {
            return "Generic{" +
                    "key=" + key +
                    ", value=" + value +
                    '}';
        }

        // <T>来表明该方法是一个泛型方法, 返回类型为T
        public <T> T getObject(Constructor<T> constructor) throws IllegalAccessException, InstantiationException, InvocationTargetException {
            T t = constructor.newInstance(1);
            return t;
        }

        public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
            Generic<String, Integer> generic = new Generic<String, Integer>();
            // 参数化了接口方法的返回类型
            System.out.println(generic.test());

            // 实现了成员key、value的类型参数化
            generic.setKey("score");
            generic.setValue(100);
            System.out.println(generic);

            Class clazz = Class.forName("CodeBlock.CodeBlock");
            Object obj = generic.getObject(
                clazz.getDeclaredConstructor(int.class)
            );
        }
    }

### **Q9：泛型标记***
- E：集合elemenet，在集合中使用，表示在集合中存放的元素
- T：指Type，表示Java类，包括基本的类以及自定义类
- K：指Key，表示键，例如Map集合中的Key
- V：指value，表示值，例如Map集合中的Value
- N：指Number，表示数值类型
- ?: 不确定的Java类型

### **Q10：泛型限定是什么**
1. 类型通配符使用？表示所有具体的参数类型，在使用泛型的时候，如果希望将类的继承关系加入泛型应用中就需要对泛型做限定，具体的泛型限定有对泛型上限的限定以及对泛型下限的限定

2. 对泛型上限的限定使用<? extends T>，它表示该通配符所代表的类型是T类的子类型或T接口的子接口，**即[T, 正无穷)，不允许添加除null的元素，获取的元素类型是C**

3. 对泛型下限的限定使用<? super T>，它表示该通配符所代表的类型是T类的父类型或T接口的父接口，**即(负无穷, T]，允许添加C以及C的子类类型的元素，获取的元素类型是Object**

4. PECS原则
    - 频繁往外读取内容的，适合用上界Extends
    - 经常往里插入的，适合用下界Super

## **3. 集合**
### **Q1: 简述一下集合主要有哪些类和接口，各自有什么特点**
需要同步的话可以用Collections类的同步

    // Collections.synchronizedXXX(c);
    Collections.synchronizedList(list);

1. 主要有两个接口Collection和Map，其中Collection又包括List、Set和Quene
2. List是有序的，主要包括ArrayList/LinkedList/Vector,
    - ArrayList底层是Object[]数组，线程不安全，查询快增删慢
    - LinkedList底层是使用双向链表实现，查询慢增删快
    - Vector效率较低，线程安全，被弃用（Vector会在你不需要进行线程安全的时候，强制给你加锁，导致了额外开销，所以慢慢被弃用）
3. Set是唯一且无序的，主要包括了HashSet、LinkedHashSet和TreeSet
- HashSet的底层是HashMap，利用了HashMap的key来保证元素的唯一性，涉及到了HashMap的hash方法，并用一个虚拟的PRESENT对象作为Map的value
- LinkedHashSet可以按照key的操作顺序排序，双向链表，存储的元素是有序的，内部使用LinkedHashMap
- TreeSet支持按照默认或指定的排序规则排序
4. Quene队列，主要有ArrayBlockingQuene基于数组的阻塞队列、LinkedBlockingQuene基于链表的阻塞队列
5. Map以k-v键值对存储元素，包括HashMap, LinkedHashMap和TreeMap，HashMap底层是数组+链表/红黑树实现,LinkedHashMap是HashMap的子类，通过维护一个LinkedList，来维护插入顺序与table中的顺序

### **Q2：HashMap是线程安全的吗？**
不安全，在高并发的情况下，若扩容(transfer方法)时因为线程调度所分配的时间片用完暂停，多线程可能会导致死循环，原因是因为头插法，在并发情况下，会导致某一头结点的next又是其头结点的next，链表成环，造成死循环

可以使用ConcurrentHashMap来保证线程安全
#### JDK7版本的ConcurrentHashMap
***JDK1.7中ConcurrentHashMap采用了数组+Segment+分段锁的方式实现***
- ConcurrentHashMap基于减小锁粒度，通过分段锁来实现线程安全，默认情况下内部Segement数组有16个
- Segement的结构与HashMap类似，继承了ReentrantLock，本身就是一个锁
- Segement内部是数组+链表的结构，每个Segement都包含了一个HashEntry数组，每个HashEntry都是一个Node链表结构；若要对其进行修改，则必须获得相对应的Segement锁
- 多线程下只要加入的数据hashCode映射的数据段不一样，就可以做到并行的线程安全
- ConcurrentHashMap定位一个元素的过程需要进行两次Hash操作，第一次Hash定位到Segment，第二次Hash定位到元素所在的链表的头部

#### JDK8版本的ConcurrentHashMap
***JDK8中ConcurrentHashMap参考了JDK8 HashMap的实现，采用了数组+链表+红黑树的实现方式来设计，内部大量采用CAS操作***

- Node：保存key，value及key的hash值的数据结构。其中value和next都用volatile修饰，保证并发的可见性
- synchronized+CAS+HashEntry+红黑树
- 原来是对需要进行数据操作的Segment加锁，现调整为对每个数组元素加锁(Node)
- 从原来的遍历链表O(n)，变成遍历红黑树O(logN)

### **Q2 extra1: HashMap的indexFor和hash方法**
    // 扰动函数
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    static int indexFor(int h, int length) {
        // 对数组的长度取模，得到的余数则为数组的下表
        return h & (length - 1);
    }

key.hashCode()函数调用的是key键值类型自带的哈希函数，返回int型散列值

理论上散列值是一个int型，如果直接拿散列值作为下标访问HashMap主数组的话，考虑到2进制32位带符号的int表值范围从-2147483648到2147483648。前后加起来大概40亿的映射空间。只要哈希函数映射得比较均匀松散，一般应用是很难出现碰撞的

问题是一个40亿长度的数组，内存是放不下的，用之前还要先做对数组的长度取模运算，得到的余数才能用来访问数组下标。源码中模运算是在这个indexFor( )函数里完成的

这也正好解释了为什么HashMap的数组长度要取2的整数幂。因为这样（数组长度-1）正好相当于一个“低位掩码”。“与”操作的结果就是散列值的高位全部归零，只保留低位值，用来做数组下标访问

右位移16位，正好是32bit的一半，自己的高半区和低半区做异或，就是为了混合原始哈希码的高位和低位，以此来加大低位的随机性。而且混合后的低位掺杂了高位的部分特征，这样高位的信息也被变相保留下来

### **Q3：List、Set和Map有什么区别？**
1. List是有序的，可重复和有索引的集合，继承了Collection集合全部功能，除了Collection的三种遍历方式外，可用索引遍历
2. Set是无序，不可重复的集合，Set的实现类LinkedHashSet和TreeSet是有序的，LinkedHashSet可以按照插入的时间先后和元素操作时间排序，TreeSet可以按照默认的比较规则或者自定义规则排序
3. Map是无序的，以k-v键值对形式存储元素，键不可重复，值无要求，重复键值会覆盖

### **Q3 extra：Collection的三种遍历方式**
- 迭代器
- 普通for循环
- foreach循环

        public static void main(String[] args) {
            List<String> list = new ArrayList<>();
            list.add(1);
            list.add(2);

            // 1. 迭代器
            Iterator<String> it = list.iterator();
            while(it.hasNext()) {
                String s = it.next();
                Syso(s);
            }

            // 2. 普通遍历
            Object[] objs = list.toArray(new Object[list.size()]);
            for (int i = 0; i < objs.length; i++) {
                Syso(objs[i]);
            }

            // 3. foreach遍历
            for (String s : list) {
                Syso(s);
            }
        }

- 上述List的索引遍历：

        String s = list.get(index);

### **Q4：HashSet是如何去重的**
底层其实用了HashMap的不允许重复键特性
1. 基本类型，可直接按值进行比较
2. 引用类型，会先比较hashCode()是否相同(即定位到Map内部数组的某一项)，再通过遍历链表，以K.eqauls()方法判断是否相同，都相同则返回，都不相同则插入
3. 如果希望内容相同的对象就代表对象相同，那么除了重写equals方法外，还要重写hashCode方法（以确保能定位到相同的Map内部数组某一项），只有hashCode和equals方法返回都为相同的才能说明是同一个对象

### **Q4：HashMap的put方法**
1. 根据put方法传入k的hashCode()得到初步hashCode，再与高16位(k >>> 16)进行异或，得到hashCode
2. hashCode与（数组长度 - 1）通过与操作进行取模，得到数组下标
3. 遍历下表对应链表/树，通过equals()方法对K进行相等判断，若相等则覆盖掉K对应V

    **第一和第二点的详情可以看Q2 extra**

### **Q5：HashMap和HashSet的底层是怎么实现的？**
1. JDK8前，HashMap的底层实现是数组+链表
2. 每个元素都是一个单链表，链表中的每个元素都是内部类Node(实现了Map.Entry<K, V>)的实现,Node包括4个属性：key、value、hash和next
3. JDK8后采用了数组+链表/红黑树的做法，当链表元素超过8个后，会转换为红黑树提升效率，时间复杂度为O(logn)
4. HashSet基于HashMap实现，其元素其实是存放在了内部一个HashMap的Key上，value是一个默认对象

### **Q6：Collection和Collections的区别**
Collection是一个集合接口，Collections是一个工具类，为Collection接口实现类提供很多方法，如addAll批量添加，shuffle打乱，sort排序

### **Q7：迭代器是什么？**
实现Iterator接口，是一个遍历Collection集合的一个游标,初始化时迭代器位于第一个元素之前，通过next()获取到下一个元素，并往后移一位

    List<String> list = new ArrayList<>();
    Iterator<String> it = list.iterator();
    while(it.hasNext()) {
        it.next();
    }

### **Q8：foreach遍历时是否可以添加或删除元素**
**foreach的底层是迭代器Iterator实现的**，如果进行添加或删除元素会产生Fast-fail问题，抛出ConcurrentModificationException异常，因为增删会影响modCount的值，当modCount与预期的exceptedModCount不一致时，会抛错

### **Q9：Queue接口中的add()/offer()、remove()/poll()、element()/peek()方法有什么区别**
1. add()和offer()都是向队列尾部插入一个元素，区别在于当超过队列界限，add会抛出异常，offer返回false
2. remove()和poll()都是出队，区别在于队列为空时，remove会抛出异常，poll将返回null值
3. element和peek都是查看队列头，区别在于队列为空时，element会抛出异常，peek将返回null值

### **Q10：线程安全的集合类**
JUC中的concurrentHashMap, Vector(锁粒度太大被弃用), HashTable(锁粒度太大被弃用), Collections.synchronizedXXX()

其中ArrayList -> Collections.synchronizedList/CopyOnWriteArrayList
CopyOnWriteArrayList和Collections.synchronizedList是实现线程安全的列表的两种方式, 两种实现方式分别针对不同情况有不同的性能表现
- Collections.synchronized的写性能较为优秀，读是通过synchronized悲观锁实现，效率不如CopyOnWriteArrayList
- CopyOnWriteArrayList在多线程下写操作性能较差，而多线程的读操作性能较好

### **Q10 extra: copyOnWriteArrayList**
其每次写操作都会进行一次数组复制操作，然后对新复制的数组进行些操作，不可能存在在同时又读写操作在同一个数组上，而读操作并没有对数组修改，不会产生线程安全问题。

其中setArray()操作仅仅是对array进行引用赋值。Java中“=”操作只是将引用和某个对象关联，**假如同时有一个线程将引用指向另外一个对象，一个线程获取这个引用指向的对象，那么他们之间不会发生ConcurrentModificationException，他们是在虚拟机层面阻塞的**，而且速度非常快，是一个**原子操作**，几乎不需要CPU时间

在列表有更新时直接将原有的列表复制一份，并再新的列表上进行更新操作，完成后再将引用移到新的列表上。旧列表如果仍在使用中(比如遍历)则继续有效。如此一来就不会出现修改了正在使用的对象的情况(读和写分别发生在两个对象上)，同时读操作也不必等待写操作的完成，免去了锁的使用加快了读取速度。

## **4. 多线程**
### **Q1：创建线程有哪几种实现方式？分别有什么优缺点**
1. 继承Thread类，重写run()方法即可，功能单一，不能继承其他类

        // 单继承，意味着该类如果已经继承于某超类，则不能实现多线程
        public MyThread extends Thread {
            @Override
            public void run() {}

            public static void main(String[] args) {}
        }

    由于线程资源与Thread实例捆绑在一起，所以不同线程的资源不会进行共享

2. 实现Runnable接口，重写run()方法，并将该实现类作为参数传入Thread构造器，优点是可以继承其他类，避免了单继承的局限性

        public MyThread implements Runnable {
            @Override
            public void run() {}

            public static void main(String[] args) {
                Thread thread = new Thread(new MyThread());
                thread.start();
            }
        }

    静态代理，线程资源与MyThread实例绑定在一起，Thread只是作为一个代理类，所以资源可以共享

3. 相比Thread/Runnable可以获取返回值，具体步骤为：实现Callable接口，重写call()方法，并包装成FutureTask对象作为参数传入Thread构造器

        // 不使用lambda-1
        public class MyThread implements Callable<String> {
            @Override
            public String call() {
                return 10086;
            }

            public static void main(String[] args) {
                Future<String> future = new FutureTask<>(new MyThread());
                new Thread(future).start();
            }
        }

        // 不使用lambda-2, 匿名内部类
        public class MyThread {
            Callable<String> cb = new Callable<>() {
                @Override
                public String call() throws Exception {
                    Syso("Callable");
                    return "inner";
                }
            };

            FutureTask<String> task = new FutureTask<>(cb);
            new Thread(task).start();
        }

        // 使用lambda
        public class MyThread {
            public static void main(String[] args) {
                Future<Integer> future = new FutureTask<>(
                    () -> 10086
                );
                new Thread(future, "").start();

                try {
                    Syso(future.isDone()); // 不阻塞
                    Syso(future.get()); // 获得task的返回值，会阻塞
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

    - 创建Callable接口实现类，并实现call()方法，创建该实现类的实例(JDK8后可以直接使用lambda创建Callable对象)
    - 使用FutureTask去包装Callable对象，该FutureTask对象封装了Callable对象的call()方法返回值
    - 使用FutureTask对象作为Thread的target参数（FutureTask实现了RunnableFuture接口），调用start启动线程
    - 调用futureTask.get方法阻塞获得子线程的执行返回值

#### Callable<T>
    // 泛型决定返回的类型，也可以抛出异常
    public interface Callable<V> {
        V call() throws Exception;
    }

#### Future<V> 
为Future<V>接口的具体实现用于封装Callable，用来获取异步计算结果的，说白了就是对**具体的Runnable(用Executor.callable封装成callable)或者Callable**对象任务执行的结果进行获取get(),取消cancel(),判断是否完成等操作：

- 若任务还未开始，cancel(...)则返回false；
- 若任务已经启动，cancel(true)将试图中断该任务线程来停止任务，如果停止成功，返回true；- 若任务已经启动，cancel(false)不会对正在执行的任务线程产生影响；
- 若任务已经完成，cancel(...)则返回false；

        public interface Future<V> {
            V get(); // 获取异步执行的结果，如果没结果可用，该方法则阻塞到计算完成

            V get(long timeout, TimeUnit unit); // 同上，但是阻塞有时间限制timeout

            boolean isDone(); // 如果任务执行结束，无论是正常结束还是中途取消还是发生异常，都返回true

            boolean isCancelled(); // 任务在被完成前取消，则返回true

            boolean cancel(boolean mayInterruptRunning); 
        }

#### FutureTask<V>
FutureTask<V>为Future<V>接口的具体实现

FutureTask实现了Runnable接口，所以可以通过Thread传入做静态代理来运行线程

    // FutureTask实现Runnable接口，意味着它可以直接提交给Executor执行
    public interface RunnableFuture<V> extends Future<V>, Runnable {
        void run();
    };

    public class FutureTask<V> implements RunnableFuture<V> {
        public FutureTask(Runnable runnable, V result) {
            // 将runnable封装成callable(底层运用了适配器模式)
            this.callable = Executors.callable(runnable, result); 
            this.state = NEW;
        }

        public FutureTask(Callable<V> callable) {
            this.callable = callable;
            this.state = NEW;
        }

        @Override
        public void run() {
            try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call(); // 在此处调用包装在内部的callable
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    setException(ex);
                }
                if (ran)
                    set(result); // CAS操作
            }
        }
    };

4. 通过线程池创建(Executor框架)

        public interface ExecutorService extends Executor {
            <T> Future<T> submit(Callable<T> task);

            <T> Future<T> submit(Runnable task);
        }

第一个方法：submit提交一个实现Callable接口的任务，并且返回封装了异步计算结果的Future
第二个方法：submit提交一个实现Runnable接口的任务，并且返回封装了异步计算结果的Future

因此我们只要创建好我们的线程对象（实现Callable接口或者Runnable接口），然后通过上面3个方法提交给线程池去执行即可。还有点要注意的是，除了我们自己实现Callable对象外，我们还可以使用工厂类Executors来把一个Runnable对象包装成Callable对象

ExecutorService的子接口AbstractExecutorService，submit()返回的类型为泛型方法<T> FutureTask<T> newTaskFor()的返回类型，具体为FutureTask

### **Q2： 线程有哪些状态？**

1. New：初始化状态，new操作创建一个线程，此时程序还未开始运行线程里的任务
2. Runnable：可运行状态，调用线程的start方法变更，进入可运行线程池中，等待线程调度选中，包括系统的**Running和Ready状态**，也就是此状态线程可能在执行，也可能在等待CPU为其分配执行时间；
3. Blocked：阻塞状态，内部锁(不是juc的锁)获取失败时，进入阻塞状态，在锁池中等待排他锁
4. Waiting: 无限等待状态，等待其他线程唤醒时，释放锁更不会分配cpu时间，唤醒时线程将从等待池进入锁池
5. TimedWaiting: 计时等待状态，带超时参数的方法，例如Thread.sleep(long time)/Object.wait(time)，无须被其他线程显式唤醒
6. Terminated：终止状态，线程正常运行完毕或被未捕获异常终止

#### 各种情况下的状态流程
1. sleep()或wait(), 唤醒后竞争不到锁

    New -> Runnable -> Waiting -> Runnable -> Blocked -> Runnable -> Terminated

2. 竞争不到内部锁，无sleep()或wait()

    New -> Runnable -> Blocked(没有竞争到内部锁) -> Runnable -> Terminated

### **Q2 extra: 阻塞状态关于sleep()、yield()、notify()/notifyAll()和wait()**
#### 线程对象与锁对象的概念
线程对象是Thread的实力对象，多个线程之间合作需要同步，而锁是实现线程同步的机制之一，锁也是一个对象，**Java中所有的对象都可以当做锁来使用**

#### 锁池和等待池的概念

锁池与等待池分别是不同线程状态的两种集合，Java虚拟机会为每个对象维护两个“队列”（姑且称之为“队列”，尽管它不一定符合数据结构上队列的“先进先出”原则）

锁池(EntrySet入口集)：某对象锁被当前线程A所持有，其他想持有该锁的线程则会进入到锁池中，待线程A释放锁，锁池中的线程具备竞争锁的资格

等待池(WaitSet等待集)：当锁对象调用wait方法时，持有该对象的线程进入等待池中，进入等待池的线程对象不具备持有锁的资格

#### Object类：wait、notify、notifyAll
对象锁，也被称为监视器，synchronized(xxx)

- wait()

    - wait()方法是Object类的方法，无须重写，可以认为该方法属于锁对象，调用者为锁对象，意思为**持有该锁的线程进入wait状态，释放锁，并进入等待池中**

    - 若没有其他线程调用对象锁的notifyAll/notify，调用了同一个对象锁的Object.wait的线程会一直等待

    - 调用wait()与wait(0)效果一样

    - 调用wait()方法必须先获得对象锁，有一种说法认为，执行wait()方法之后，可能会有中断或者虚假的唤醒，所以wait()方法一般要放在一个循环中（一般wait()方法的调用是伴随一个条件的，条件发生时才调用wait()

- notify()/notifyAll()

    - 会在等待池中随机选择一个或全部线程对象放入锁池中，即不保证立即执行，除非线程优先级更高

    - 调用后，无限期等待/计时等待线程将从Wait状态，进入到Blocked状态

#### Thread类：sleep、join、yield
- sleep()

    sleep是Thread的静态方法，调用者是线程对象，sleep的作用是**将当前线程暂停一定的时间，但不释放锁**

- join()

    等待调用join()方法的线程死亡，当前线程才继续往下运行,比如在线程t1代码中调用了t2.join()，则t1要等t2死亡之后才能继续执行

### **Q3：什么是线程安全问题，如何解决？**
多个线程对同一个共享变量进行操作时，若有竞态条件，则可能会产生问题
- 使用内部锁synchronized，可以使用同步代码块，如果是实例方法则用this作为锁对象，如果是静态方法，可以用类.class作为锁
- 使用java.util.concurrent包中的锁，如显示锁ReentrantLock

**解决方法本质：在单个原子操作中，更新所有相关的状态变量**

若一个对象没有状态变量，我们认为他是线程安全的；
若一个对象只有一个状态变量，当该状态变量是线程安全的，那我们认为他是线程安全的（此时原子操作中更新了所有相关的状态变量）
但当一个对象有多个状态变量，即使全部状态变量都是安全的，他也可能不是线程安全的（原子操作没有更新所有相关的状态变量）

### **Q3 extra：竞态条件**
1. Checkt then Act，先检查再执行
2. Read-Update-Write, 读取-修改-写入

### **Q4：多线程不可见的原因和解决方法**
1. 不可见的原因是栈中不同线程有自己的工作内存，线程都是从主内存拷贝共享变量的副本，在自己的工作内存中操作共享变量
2. 内部锁：获得锁后，线程清空工作内存，从主内存拷贝共享变量的最新副本，修改后刷新回主内存，再释放锁
3. 使用volatile关键字，被volatile修饰的变量会通知其他线程之前读取到的值已失效，线程会加载最新值到自己的工作内存中
4. 显式锁
5. 原子变量

        @ThreadSafe
        public class Main {
            // 该类的状态变量只有一个，通过原子变量来保证该状态的线程安全，则可以认为该类是线程安全的了
            private AtomicInteger value = new AtomicInteger(0);

            public Integer nextValue() {
                  // 内部锁/监视锁/可重入锁(解决子类父类方法死锁)
        //        synchronized (this) {
                    for (int i = 0; i < 100000; i++) {
                        value.incrementAndGet();
                    }
        //        }
                return this.value.intValue();
            }

            public static void main(String[] args) throws Exception {
                Main main = new Main();

                FutureTask<Integer> f1 = new FutureTask<>(() -> main.nextValue());
                FutureTask<Integer> f2 = new FutureTask<>(() -> main.nextValue());
                new Thread(f1).start();
                new Thread(f2).start();

                f2.get();
                System.out.println(main.value); // 20000
            }
        }
### **Q5：说一说volatile关键字的作用**


### **Q6：说一说synchronized关键字的作用**
可重入

## **5. I/O**
#### 如何理解input和output
为了在程序结束后某些数据得以保存,IO可以帮我们将数据存储到持久化设备中(硬盘,U盘)

程序运行时的数据时在内存中,使用IO流可以帮我们把内存数据存储到持久化设备中,
- 内存数据存储到持久化设备--输出(Output)操作:我给你东西,对我来说东西是出去了(写)
- 持久化设备读取到内存中--输入(Input)操作:别人给我东西,对我来说就是进来(读)

#### 1. 字节流和字符流
字符流的由来： 因为数据编码的不同，而有了对字符进行高效操作的流对象。本质其实就是基于字节流读取时，去查了指定的码表。 字节流和字符流的区别：
- 读写单位不同：字节流以字节（8bit）为单位，字符流以字符为单位，根据码表映射字符，一次可能读多个字节
- 处理对象不同：字节流能处理**所有类型的数据（如图片、avi等）**，而字符流只能处理**字符类型==的数据

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
- [面向对象：多态、编译时运行时、向上兼容](https://blog.csdn.net/qq_38962004/article/details/79690605)
- [关于Java的Object.clone()方法与深浅拷贝](https://www.cnblogs.com/nickhan/p/8569329.html)
- [Java IO流学习总结](https://zhuanlan.zhihu.com/p/28757397)
- [内部类用处的思考](https://www.iteye.com/blog/sunxg-557846)
- [谈谈对4种内部类的理解，和使用场景分析](https://blog.csdn.net/xinzhou201/article/details/81950188)
- [Java 不能实现真正泛型的原因是什么？-RednaxelaFX回答](https://www.zhihu.com/question/28665443/answer/118148143)
- [Java中的泛型会被类型擦除，那为什么在运行期仍然可以使用反射获取到具体的泛型类型？-陆萌萌回答](https://www.zhihu.com/question/346911525/answer/830285753)
- [Java泛型类型擦除以及类型擦除带来的问题](https://www.cnblogs.com/wuqinglong/p/9456193.html)
- [<? extends T>和<? super T>](https://www.jianshu.com/p/520104cfd0ff)
- [HashMap中的hash函数](https://www.cnblogs.com/zhengwang/p/8136164.html)
- [ArrayList线程安全](https://blog.csdn.net/xiangcaoyihan/article/details/78228962)
- [java并发之sleep与wait、notify与notifyAll的区别](https://blog.csdn.net/u012719153/article/details/78915034)
- [Java中对象的锁池和等待池](https://www.baidu.com/link?url=KMwXaB2naXAACR9dJUrtr4t1NbITiLBBaJXknBk-NLNYDMGLwRZ6J2E-pyEDY8-FpLfZcCFANeWF29PaTT8ESDsIk6DaV9ymwckHq8Hswty&wd=&eqid=d8e3e57700010cf7000000065e8da632)