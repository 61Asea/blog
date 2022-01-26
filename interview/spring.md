# interview eight：Spring

- 单例模式：Bean默认情况下都是单例

- 工厂模式：通过BeanFactory和ApplicationContext来生产Bean对象

- 代理模式：AOP代理，JDK代理和CGLIB代理

- 模板方法：Jdbc

# **1. bean生命周期**

主要分为4个阶段：

- `实例化`：创建bean对象实例

- `填充属性`：为属性赋值

- `初始化`：调用bean配置的init-method方法

- `销毁`：容器关闭之后，若有配置destroy-method或实现了DisposableBean接口，则会回调执行destroy方法

最外层是`AbstractAutowireCapableBeanFactory#createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)`，方法内部各个调用分别为：

- resolveBeforeInstantiation(beanName, mbdToUse)：在实例化方法之前

    > bean的AOP信息在此处会由AbstractAutoProxyCreator.postProcessBeforeInstantiation()方法进行读取，若无则证明为普通bean并直接缓存起来，在后续判断是否增强时直接跳过

    调用所有**InstantiationAwareBeanPostProcessor**.`postProcessBeforeInstantiation()`（第一个扩展点） 

- doCreateBean()

    - createBeanInstance()：实例化方法，调用后bean被实例化

    - populateBean()：属性填充

        > 如若出现循环依赖，对于需要增强的对象需要提前暴露引用，防止出现后续增强引用**无法覆盖之前设置的引用**，导致最终注入的不是增强对象

        - 所有**InstantiationAwareBeanPostProcessor**.`postProcessAfterInstantiation()`：实例化方法之后（第二个扩展点）

        - autowireByName()/autowireByType()：根据注解类型进行属性填充，**在这会触发循环依赖**


        - `applyPropertyValues()`：填充属性时扩展点（第三个扩展点）

    - initializeBean()：**BeanNameAware、BeanFactoryAware、BeanPostProcessor、InitializingBean**的扩展点

        - invokeAwareMethods：BeanAware扩展点，包括`setBeanName()`、`setBeanFactory()`等方法（第四、五扩展点）

        - applyBeanPostProcessorsBeforeInitialization()：调用bpp的初始化前置方法，bpp.`postProcessBeforeInitialization()`（第六扩展点）

        - invokeInitMethods()

            - `afterPropertiesSet()`：InitializingBean接口（第七扩展点）

            - invokeCustomInitMethod()：自定义初始化方法

        - applyBeanPostProcessorAfterInitialization()：调用bpp的初始化后置方法，bpp.`postProcessAfterInitialization`（第八扩展点）

            > 如若没有出现循环依赖，则正常在此处获取增强对象

# **2. Spring如何解决循环依赖**

循环依赖：指对象之间出现直接或间接的依赖关系，这种关系恰好构成一个闭环

做法：Spring通过**提前暴露半成品bean**的方式解决循环依赖问题，在填充属性过程中会将当前正在构建为完毕的bean引用缓存起来供依赖对象使用，辅助依赖对象构造完毕后完成自身构造

**问题1：为什么使用三级缓存解决循环依赖问题？**

三级缓存指的是：singletonObjects，earlySingletonObjects，singletonFactories

主要是为了解决bean泄漏和增强对象引用错误两个问题：

- 二级缓存：区分已经初始化完毕的bean和正在构造中的bean，防止其他使用者注入构造未完成的bean

- 三级缓存：存储bean和其要加强的aop处理，如果需要aop增强的bean遇到了循环依赖，就应该提前暴露引用

    Spring的设计原则，认为AOP跟bean的生命周期本身就是通过postProcessAfterInitialization方法对**初始化后的bean**完成aop代理，如果遇到循环依赖的情况只能提前暴露引用
    
    所以统一都使用ObjectFactory进行包装，再根据具体情况触发bpp.getEarlyReference()方法

    如果没有用三级缓存来对ObjectFactory进行区分，那么二级缓存不符合单一职责

**问题2：能解决所有循环依赖问题吗？**

首要考虑应是：重新设计bean，考虑到底有没有必要存在循环依赖

- 多例setter注入：没有使用缓存机制

- 构造器注入：正常情况下无法解决，因为java语言约束下无法提前暴露半成品对象，但是可以配合@Lazy强行暴露依赖链中某个对象的半成品对象（代理）进行解决

# **3. FactoryBean和BeanFactory**

BeanFacotry：bean的工厂，用于生产和管理bean对象

FactoryBean：指的是具备工厂能力的bean，本身不能被当做一个正常的bean实例使用，用于产出其它bean

正常情况下bean无须自己实现工厂模式，Spring已担任了工厂角色，但少数情况下bean本身就应是工厂

# **4. JDK动态代理和CGLIB动态代理区别**

JDK动态代理：基于**反射机制**实现，target类**若实现某个`接口`AOP则会使用JDK动态代理**，生成一个实现同样接口的代理类，然后通过重写方法的方式实现对代码的增强

CGLIB动态代理：基于asm第三方框架，通过**修改字节码生成一个`子类`**，然后重写父类的方法，实现对代码的增强

# **5. 事务传播机制**

通过@EnableTransactionManagement(proxyTargetClass = true)开启事务，注解包括`@Import(TransactionManagementConfigurationSelector.class)`，由ConfigurationClassPostProcessor解析

对象是否有事务增强由bean是否由@Transactional注解决定，最终会调用`AbstractPlatformTransactionManager`对事务进行管理

事务传播机制：在多个方法的调用中，规定事务传递的方式


# **6. 共有几种装配方式**


# 参考

# 重点参考