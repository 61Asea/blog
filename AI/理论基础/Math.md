# 机器学习相关的数学

机器学习擅长的任务包括：

- 回归regression：从连续的数据序列中学习趋势，并预测出今后的趋势，输出是数值
  - 应用：李宏毅课程中的youtube频道观看量人数预测、股票预测、天气温度预测
- 分类classification：对输入进行判断鉴别
  - 应用：影像辨识、垃圾邮件过滤
- 聚类clustering：将数据中权重接近的数据进行聚类
  - 应用：聚类算法

### **数学基础1：微分**
得知函数在某个点的斜率，计算瞬时变化的快慢程度的方法。

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

### **数学基础2：偏微分**

考虑多变量的情况，在对多变量函数进行微分的时候，只需关注要微分的变量，把其他变量都当作常数来处理，这种方法就叫做偏微分。

假设有：$\large h(x_1,x_2)=x_1^2+x_2^3$，则对$x_1$, $x_2$分别进行偏微分计算

- 计算$x_1$，将$x_2$当作常数，则$\large \frac{\partial h(x_0, x_1)}{\partial x_1}=2x_1$

- 计算$x_2$，将$x_1$当作常数，则$\large \frac{\partial h(x_0, x_1)}{\partial x_2}=3x_2^2$

### **数学基础3：复合函数**

考虑两个函数进行嵌套出现的情况，这种由多个函数组合而成的称为复合函数。

在计算复合函数的微分时，可以**按步骤进行微分，再做相乘操作**。假设有$f(x)$、$g(x)$，计算$f(g(x))$的微分与偏微分。

设$u=f(v)$,$v=g(x)$：
$$
\frac{\mathrm{d}u}{\mathrm{d}x}= \frac{\mathrm{d}u}{\mathrm{d}v} \cdot \frac{\mathrm{d}v}{\mathrm{d}x}
$$

$$
\frac{\partial u}{\partial x}= \frac{\partial u}{\partial v} \cdot \frac{\partial v}{\partial x}
$$

### **数学基础4: 向量和矩阵**

### **数学基础5：几何向量**

### **数学基础6：指数和对数**

## **1. 回归**

书中对广告费$\theta$ 与 点击量$f(\theta)$，这种**正相关**问题进行**拟合**假设，提出了经典的线形模型(linear model)：

$$
f(\theta)=\theta_0 + \theta_1x
$$

### **1.1 最小二乘法**

本质是一个求解函数最佳未知参数$\theta$的问题，以达到**曲线拟合**的最佳情况，假设：
- $\theta$：model function的最佳未知参数
- i：第几笔训练数据
- $f_\theta(x^{\left(i\right)})$：第i笔数据的预测结果值
- $y^{\left(i\right)}$：第i笔数据的真实结果值

可以通过最小二乘法，找到使得误差$E$（error）最小的$\theta$，这样的问题也称为最优化问题(optimization)。

MSE Mean Square Error 均方误差：

$$
\Large E(\theta) = \frac{1}{2} \sum_{i=1}^n(y^{\left(i\right)} - f_\theta(x^{\left(i\right)}))^2
$$

> 除了MSE之外，更常用的还有Cross-Encropy方法。

均方误差的计算流程：
1. 计算每i笔数据的误差，由真实值减去预测值获得：$y^{\left(i\right)} - f_\theta(x^{\left(i\right)})$
2. 计算每一笔误差的平方
    > 为什么要计算误差的平方？
    
    答：因为考虑误差为负值的情况，混合正值和负值的运算会使得真实误差被抵消。**而且后续需要对目标函数进行微分，所以不用绝对值运算。相比绝对值，平方的微分更简单。**

3. 求和所有误差的平方，并乘以$\frac{1}{2}$

    > 为什么要乘以一个1/2？

    答：也是与微分有关系，为了使得结果的表达式变得更简单而随便加的常数。在最优化问题里，这个常数可以随便取值。

### **1.1.1 梯度下降法**

又称最速下降法，英文称为gradient desent，本质是通过微分，得到function model的值上升、下降趋势，从而根据趋势来移动，最终逼近最小值。

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

### **1.1.2 多项式回归**

考虑$f_\theta(x)$模型下，$\theta$的个数有n个的情况。所以在计算这种情况下的模型最小值，则只需要考虑关注的$\theta_j$参数，其他的$\theta$参数通通视为常数。

套入1.1.1梯度下降法，对目标函数$E(\theta)$进行偏微分计算。

计算当$\theta_j$为$\theta_0$、$\theta_1$的情况，此处因为涉及到复合函数，设$u=E(\theta)$, $f_\theta(x) = v$，则有$\large\frac{\partial u}{\partial x}=\frac{\partial u}{\partial v} \cdot\frac{\partial v}{\partial x}$。

- $\Large \theta_0 := \theta_0-\eta\cdot\frac{\partial}{\partial \theta_0}E(\theta)$

  分步计算：

  $\frac{\partial u}{\partial v}=\frac{1}{2}\sum_{i=1}^n(\frac{\partial}{\partial v}(y^{\left(i\right)^2} - 2y^{\left(i\right)}v + v^2))=\sum_{i=1}^n(v-y^{\left(i\right)})$

  $\frac{\partial v}{\partial \theta_0}=\frac{\partial}{\partial \theta_0}(\theta_0 + \theta_1x)$ = 1

  $\frac{\partial u}{\partial \theta_0}=\sum_{i=1}^n(f_\theta(x)-y^{\left(i\right)})$

- $\Large \theta_1 := \theta_1-\eta\cdot\frac{\partial}{\partial \theta_1}E(\theta)$

  分步计算：

  $\frac{\partial u}{\partial v}=\frac{1}{2}\sum_{i=1}^n(\frac{\partial}{\partial v}(y^{\left(i\right)^2} - 2y^{\left(i\right)}v + v^2))=\sum_{i=1}^n(v-y^{\left(i\right)})$

  $\frac{\partial v}{\partial \theta_1}=\frac{\partial}{\partial \theta_1}(\theta_0 + \theta_1x) = x$

  $\frac{\partial u}{\partial \theta_1}=\sum_{i=1}^n(f_\theta(x)-y^{\left(i\right)})x^{\left(i\right)}$

### **1.1.3 多重回归**

考虑$f_\theta(x)$模型下，$x$参数有n个的情况，即$f_\theta(x_0, x_1, ... ,x_n)=\theta_0 + \theta_1x_1 + \theta_2x_2 + ... + \theta_nx_n$

由于多重回归下，$\theta$和$x$的个数过多，此时通过矩阵向量进行管理：
$
\theta = \begin{bmatrix}
 \theta_0\\
 \theta_1\\
 \theta_2\\
 ...\\
 \theta_n
\end{bmatrix}
$,
$
x = \begin{bmatrix}
 x_0\\
 x_1\\
 x_2\\
 ...\\
 x_n
\end{bmatrix}
$, $\large f_\theta(x)=\theta^Tx$

> $T$代表矩阵转置

对于$n=j$的情况下，依旧可以通过偏微分的方式计算得到

$$
\large \frac{\partial v}{\partial \theta_j}=\frac{\partial v}{\partial \theta_j}(\theta^Tx)=\frac{\partial v}{\partial \theta_j}(\theta_0x_0+...+\theta_nx_n) = x_j
$$

最终得到多重回归下，一般的梯度下降公式：

$$
\theta_j := \theta_j - \eta\sum_{i=1}^n(f_\theta(x^{\left(i\right)}) - y^{\left(i\right)}) x_j^{\left(i\right)}
$$

### **1.1.4 梯度下降法的优化方案**

- 随机梯度下降法（batch size = 1）：随机取一个数据进行计算

  $\theta_j := \theta_j - \eta(f_\theta(x^{\left(k\right)}) - y^{\left(k\right)}) x_j^{\left(k\right)}$
- 最速梯度下降法 (batch size = all)：取完所有数据后再进行计算

  $\theta_j := \theta_j - \eta\sum_{i=1}^n(f_\theta(x^{\left(i\right)}) - y^{\left(i\right)}) x_j^{\left(i\right)}$

- 小批次梯度下降法（mini-batch）：设定一个合理的batch size = K，在批次内取完所有数据后再进行计算

  $\theta_j := \theta_j - \eta\sum_{k\ni K}(f_\theta(x^{\left(k\right)}) - y^{\left(k\right)}) x_j^{\left(k\right)}$

优点：小批次可以更好地感知当前误差计算的变化，从而调整梯度移动的步伐，避免过早陷入局部最优解(local minima)。

当前误差的变化：每一个批次的数据对应的E都是不同的，如果按批次进行划分，则会有（sum / batch size）个E

## **2. 分类**



## 参考文档

- [白话机器学习的数学]()
- [李宏毅2021机器学习课程]()