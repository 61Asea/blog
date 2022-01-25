# interview eight：Spring

- 单例模式：Bean默认情况下都是单例

- 工厂模式：通过BeanFactory和ApplicationContext来生产Bean对象

- 代理模式：AOP代理，JDK代理和CGLIB代理

- 模板方法：Jdbc

# **1. bean生命周期**

最外层的关键方法：`AbstractAutowireCapableBeanFactory#createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)`

- resolveBeforeInstantiation(beanName, mbdToUse)：在实例化方法之前，第一个扩展点

- doCreateBean

    - createBeanInstance：实例化方法

    - populateBean：

        - 所有InstantiationAwareBeanPostProcessor.postProcessAfterInstantiation：实例化方法之后，第二个扩展点

        - applyPropertyValues：实例化方法的第三个扩展点

    - initializeBean：


主要分为4个阶段：

- 实例化：创建bean对象实例，可通过实现InstantiationAwareBeanPostProcessor获得**实例化前后**共三个扩展点

    扩展点：

    - postProcessBeforeInstantiation：例如AOP自动代理类`AbstractAutoProxyCreator`会在bean实例化前读取该类是否有@Aspect注解

    - postProcessAfterInstantiation：作为bean实例化后的扩展点

    - postProcessPropertyValues：作为bean填充属性前的扩展点

- 填充属性：为属性赋值，

- 初始化

- 销毁

# **2. Spring如何解决循环依赖**

# **3. JDK动态代理和CGLIB动态代理区别**

# **4. 事务传播机制**

# 参考

# 重点参考