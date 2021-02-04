# Java常见的字节码指令

反编译查看字节码指令：

    javap -c [类文件] > [反编译后的文本]

    eg: javap -c ./Demo.class > Demo.txt

这个demo包含的字节码指令有：

对象：

1. new 创建对象，执行<cinit>，并生成一个引用值存放于栈顶

2. invokespecial 执行<init>, 会消耗栈顶的栈顶的值，传入到对象实例，作为this引用

3. invokevirtual 调用实例方法

4. getfield 获得对象的属性，需要消耗处于栈顶的一份引用值

5. putfield 设置对象的属性，同需要消耗一份引用值

局部变量表：

1. aload_0/aload_1/...: 加载局部变量表中，指定索引的值到栈中

2. astore_0/astore_1/...: 赋值给局部变量表指定索引的变量

常量：

1. iconst_1/biconst/liconst/siconst: 压入整数常量到操作栈中

基于执行引擎的操作：

1. iadd：弹出操作数栈的前两项，通过执行引擎进行相加，再压入到操作栈中

2. dup：复制一份栈顶的值，再压入到栈中

3. return：查找栈帧中的方法出口，返回

![字节码反编译](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9tbWJpei5xcGljLmNuL3N6X21tYml6X3BuZy9rcmpsNlFiaWE2V2tldld1dlFpYmxYaWJyZm5aWmRPWWxBQ2NYOTQ3RmhXdEFaMjBtMFZReXV1bWRlY05ISXk3Sk5hNlk3VjdIUE90UWZpY0dDRWo2dTlyM1EvNjQw?x-oss-process=image/format,png)

![反编译文件的字节码与详情](https://imgconvert.csdnimg.cn/aHR0cHM6Ly9tbWJpei5xcGljLmNuL3N6X21tYml6X3BuZy9rcmpsNlFiaWE2V2tldld1dlFpYmxYaWJyZm5aWmRPWWxBQ1pFcEY2U2tJSEx5MkR6Rkx1UTVvWVhjamRHZDhNS2JjeHZRaWFhYVJpYnhaU29VSnRDaDBJeWtRLzY0MA?x-oss-process=image/format,png)

```java
public class Demo {
    public int i = 0;

    public void addOne() {
        this.i += 1;
    }
    
    public static void main(String[] args) {
        Demo demo = new Demo();
        demo.addOne();
    }
}

// 反编译后的结果

public class mm.Demo {
    public int i;

    public mm.Demo();
    Code:
        0: aload_0
        1: invokespecial #1                  // Method java/lang/Object."<init>":()V
        4: aload_0
        5: iconst_0
        6: putfield      #2                  // Field i:I
        9: return

    // addOne方法局部变量表，0: this.i引用
    public void addOne();
    Code:
        // 加载局部变量表的第一项的值到操作栈中[this.i引用]
        0: aload_0
        // 复制两份引用[this.i引用， this.i引用]
        1: dup
        // 消耗一份引用，传入getfield中，获得对象的值，并压入栈中[this.i的值, this.i引用]
        2: getfield      #2                  // Field i:I
        // 读取常量1，并压入栈中[1, this.i的值, this.i引用]
        5: iconst_1
        // 弹出栈的最上两个，相加得到结果后，再压入栈[1+this.i的值, this.i引用]
        6: iadd
        // 弹出栈的最上两个，将最先弹出的值，赋值到第二个弹出的引用
        7: putfield      #2                  // Field i:I
        10: return

    // main方法局部变量表，0：args，1：demo引用     
    public static void main(java.lang.String[]);
        Code:
        // 创建对应的实例，对其进行默认初始化<cinit>，并且将指向该实例的一个引用压入操作数栈中
        0: new           #3                  // class mm/Demo
        // 下面的invokespecial会消耗掉栈顶的引用作为传给构造器的this参数，且后续还有demo引用赋值操作，此处就进行一个引用的复制
        3: dup
        // 消耗掉栈顶引用，传入构造器的this参数，执行<init>
        4: invokespecial #4                  // Method "<init>":()V
        // 将栈顶的引用赋值给局部变量表的第二项(index=1)
        7: astore_1
        // 加载局部变量表的第二项到操作栈中
        8: aload_1
        // 调用实例对象的方法
        9: invokevirtual #5                  // Method addOne:()V
        12: return
    }
```

总结：

从jvm对线程的虚拟机栈和栈帧（操作数栈，局部变量表）的概念，可以看出执行引擎（LWP线程）的操作流程为：

    将虚拟机栈的栈顶作为当前栈帧 -> 执行当前栈帧（对应当前方法）字节码指令 -> cpu寄存器执行时，弹出操作数栈 -> 

# 参考
- [Java常用的字节码指令](https://blog.csdn.net/itcats_cn/article/details/81113647)
- [线程栈-当前栈帧](https://www.cnblogs.com/jpxjx/p/12539919.html)
- [iconst、bipush、sipush、ldc指令的区别](https://blog.csdn.net/xiaojin21cen/article/details/106403053)
- [getfield指令硬件操作](https://www.sohu.com/a/169345173_283613)
- [关于JVM字节码中dup指令的问题 - R大回复](https://www.zhihu.com/question/52749416)