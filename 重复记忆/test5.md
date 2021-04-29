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
