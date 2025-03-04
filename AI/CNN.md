# Convolutional Neural Network（CNN）

卷积神经网络，常用于影像辨识（Image Classification）。

在影像辨识中，弹性最大的模型是Fully Connected Layer（全连接Layer），指的是需要通过查看整张图片来侦查出重要的Pattern特征。

但是输入图片参数到所有的神经网络中，如果是按照全连接的方式进行模型计算，会导致过多参数，从而引发overfitting。因此需要通过一些方式简化计算的步骤。

## 卷积简化

为了对参数的数量进行限制，降低模型的弹性，引入了卷积（Convolutional Layer）。

卷积包含：守备区域 + 共享参数

> 其实CNN的model bias会比较大，但在影像上并没有问题。

### 1. Receptive field

> 因为其实可以不通过整张照片,也许只要看图片的一小部份就能侦查出重要的特征，所以引入一个守备区域（Receptive field）的概念

Receptive field：守备区域，指在整个图片三维空间上的限定某一个三维空间子集，里面包含了三维的数据
图片三维：平面上是图片解析度大小，纵轴上是颜色rgb的深度（默认RGB共计3个channel）

> 每一个neural都只关心自己的receptive field里发生的事情就好,这样就可以使得neural无需全连接所有的入惨

最经典的Receptive field安排方式：
1. 默认会看所有的channel，所以只讨论平面上长和宽的大小，也叫做kernel size，大小一般是3*3
2. 会有一个stride参数，来决定下一个receptive field的与上一个receptive field的平移距离，要求必须重叠。
3. receptive field超出影像的范围，使用padding进行0填充

### 2. Neural共享参数

> 每一个receptive field会有一组neural去关照他

每一个Receptive field，对应有一组neural。组里的每一个neural（按序号），都与其他Receptive field的neural（按序号）使用相同参数filter。

