# 机器学习相关的数学

机器学习擅长的任务包括：

- 回归regression：从连续的数据序列中学习趋势，并预测出今后的趋势，输出是数值
  - 应用：李宏毅课程中的youtube频道观看量人数预测、股票预测、天气温度预测
- 分类classification：对输入进行判断鉴别
  - 应用：影像辨识、垃圾邮件过滤
- 聚类clustering：将数据中权重接近的数据进行聚类
  - 应用：聚类算法

## **1. 回归**

书中对广告费$\theta$ 与 点击量$f(\theta)$，这种**正相关**问题进行**拟合**假设，提出了经典的线形模型(linear model)：

$$
f(\theta)=\theta_0 + \theta_1x
$$

### **1.1 最小二乘法**

本质是一个求解函数最佳未知参数$\theta$的问题，假设：
- $\theta$：model function的最佳未知参数
- i：第几笔训练数据
- $f_\theta(x^{\left(i\right)})$：第i笔数据的预测结果值
- $y^{\left(i\right)}$：第i笔数据的真实结果值

可以通过最小二乘法，找到使得误差$E$（error）最小的$\theta$，这样的问题也称为最优化问题(optimization)：

$$
\Large E(\theta) = \frac{1}{2} \sum_{i=1}^n(y^{\left(i\right)} - f_\theta(x^{\left(i\right)}))^2
$$

解析以上的式子：
1. 计算每i笔数据的误差，由真实值减去预测值获得：$y^{\left(i\right)} - f_\theta(x^{\left(i\right)})$
2. 计算每一笔误差的平方
    > 为什么要计算误差的平方？
    
    答：因为考虑误差为负值的情况，混合正值和负值的运算会使得真实误差被抵消。**而且后续需要对目标函数进行微分，所以不用绝对值运算。相比绝对值，平方的微分更简单。**

3. 求和所有误差的平方，并乘以$\frac{1}{2}$

    > 为什么要乘以一个1/2？

    答：也是与微分有关系，为了使得结果的表达式变得更简单而随便加的常数。在最优化问题里，这个常数可以随便取值。

### **1.1.1 微分**

微分：得知函数在某个点的斜率，计算瞬时变化的快慢程度的方法。

> 通过计算间隔$h$内的平均值，但使得$h$无限趋近于0，从而使得平均值可以代指瞬时值。

$$
\Large\frac{\mathrm{d}}{\mathrm{d} x}f(x)=\lim_{x \to 0} \frac{f(x + h) - f(x)}{h}
$$

其中$\frac{\mathrm{d}}{\mathrm{d}x}$称为微分运算符，在表示$f(x)$的微分时，可以写作：$\frac{\mathrm{d} f(x)}{\mathrm{d} x}$、$\frac{\mathrm{d}}{\mathrm{d} x} f(x)$ 或者 一阶导数${f}'(x)$

**微分特性一：** 当$f(x) = x^n$时，对其微分的结果如下：
$$
\Large\frac{\mathrm{d} f(x)}{\mathrm{d} x}=nx^{n=1}
$$

推导过程：
1. $x^n=e^{lnx^n}=e^{nlnx}$
2. 因为$e^x$的求导仍然是$e^n$，所以对$x^n$求导有：$e^{nlnx}*\frac{n}{x}$
3. 化简：${(x^n)}'=e^{lnx^n}*\frac{n}{x}=x^n*\frac{n}{x}=nx^{n-1}$

**微分特性二：** 微分的结合律和分配律，体现出来的特性被称为线形

$$
\Large\frac{\mathrm{d}}{\mathrm{d} x} (f(x) + g(x))=\frac{\mathrm{d}}{\mathrm{d} x}f(x) + \frac{\mathrm{d}}{\mathrm{d} x}g(x)
$$

$$
\Large\frac{\mathrm{d}}{\mathrm{d}x}(af(x)) = a\cdot \frac{\mathrm{d}}{\mathrm{d}x}f(x)
$$

**微分特性三：** 与x无关的常数a的微分恒定为0（常数是平行于x轴的一条直线）
$$
\Large\frac{\mathrm{d}}{\mathrm{d}x} a = 0
$$

> 根据以上的所有特性，可以延伸出在机器学习中最常用的一些计算

**常用计算：**

1. $\Large\frac{\mathrm{d}}{\mathrm{d}x}5=0$ （根据特性3）

2. $\Large\frac{\mathrm{d}}{\mathrm{d}x}x=\frac{\mathrm{d}}{\mathrm{d}x}x^1=1\cdot x^{1-1} = 1$ （根据特性1）

3. $\Large\frac{\mathrm{d}}{\mathrm{d}x}x^3 = 3x^2$ (根据特性1)

4. $\Large\frac{\mathrm{d}}{\mathrm{d}x}x^{-2}=-2\cdot x^{-3}$ (根据特性1)

5. $\Large\frac{\mathrm{d}}{\mathrm{d}x}10x^4=10\cdot \frac{\mathrm{d}}{\mathrm{d}x}x^4= 10\cdot 4x^3=40x^3$ （根据特性1+特性2）

6. $\Large \frac{\mathrm{d}}{\mathrm{d}x}(x^5 + x^6) = \frac{\mathrm{d}}{\mathrm{d}x}x^5 + \frac{\mathrm{d}}{\mathrm{d}x}x^6 = 5x^4 + 6x^5$（根据特性1 + 特性2）

7. $\Large \frac{\mathrm{d}}{\mathrm{d}x} \sum_{i=0}^{n}x^n = \sum_{i=0}^{n} \frac{\mathrm{d}}{\mathrm{d}x}x^n$ (根据特性2)

### **1.1.2 梯度下降法**

又称最速下降法，英文称为gradient desent，本质是通过微分，得到function model的值上升、下降趋势，从而根据趋势来移动，逼近最小值。

$$
\Large x := x - \eta\frac{\mathrm{d}}{\mathrm{d}x} f(x)
$$

通过计算$f(x)$在$x_0$的微分，可以得到：
- $\Large \frac{\mathrm{d}y}{\mathrm{d}x} < 0$时，model处于下降趋势，$x$需要继续往$x_0$的右方移动
- $\Large \frac{\mathrm{d}y}{\mathrm{d}x} > 0$时，model处于上升趋势，$x$需要继续往$x_0$的左方移动

所以可见微分趋势与移动方向刚好是相反的，在定义中是**减去**$\eta\frac{\mathrm{d}}{\mathrm{d}x} f(x)$

学习率$\large \eta$：也称为learning rate，决定梯度下降的速度。根据学习率的大小，到达最小值的更新次数也会发生变化。
- 发散：常出现在$\eta$较大的情况，可能会使得移动在一段距离内反复横跳，甚至远离最小值
- 收敛：当$\eta$较小时，移动量虽然较小，但是值会慢慢往最小值收敛



## 参考文档

- [白话机器学习的数学]()