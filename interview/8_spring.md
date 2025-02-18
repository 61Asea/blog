# interview eight：Spring

- `工厂模式`：通过BeanFactory和ApplicationContext来生产Bean对象

- `单例模式`：Bean默认情况下都是单例

- `代理模式`：AOP代理，JDK代理和CGLIB代理

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

做法：Spring通过缓存，以**提前暴露半成品bean**的方式解决循环依赖问题，在填充属性过程中会将当前正在构建为完毕的bean引用缓存起来供依赖对象使用，辅助依赖对象构造完毕后完成自身构造。

**问题1：为什么使用三级缓存解决循环依赖问题？**

三级缓存指的是：singletonObjects，earlySingletonObjects，singletonFactories

主要是为了解决`bean泄漏`和`增强对象引用错误`两个问题：

- 二级缓存：区分初始化完毕的bean和正在构造中的bean

    - 为什么要二级缓存：因为可能存在多bean之间的循环依赖，三级缓存需要配合二级缓存，将需要提前暴露的bean实例初始化并保存起来，否则注入过程会通过ObjectFactory获取到不同的bean实例

- 三级缓存：存储bean或其aop增强的**ObjectFactory**

    - 为什么要三级缓存：配合二级缓存，防止出现注入未完成时出现其它线程并发获取，从而**获取到的未完成bean**

    > 如果需要aop增强的bean遇到了循环依赖，就应该提前暴露引用

    - 为什么要使用ObjectFactory：

    因为AOP增强无法直接对实例进行增强
    
    Spring设计上认为，AOP跟bean生命周期本身就是通过postProcessAfterInitialization方法，对**初始化后的bean**完成aop代理，如果遇到循环依赖的情况只能提前暴露引用
    
    所以统一都使用ObjectFactory进行包装，再根据具体情况触发bpp.getEarlyReference()方法

    <!-- 如果没有用三级缓存来对ObjectFactory进行区分，那么二级缓存不符合单一职责 -->

**问题2：能解决所有循环依赖问题吗？**

无法解决所有的循环依赖问题

首要考虑应是：重新设计bean，考虑到底有没有必要存在循环依赖

- 多例（prototype）setter注入：多例模式下的bean并没有使用缓存机制，自然也无法通过缓存解决循环依赖的问题

- 构造器注入：正常情况下无法解决，因为无法实例化（构造器必须传入对象），所以无法提前暴露自身的半成品对象

    > 可以在构造器中对依赖对象加上@Lazy注解，这样注入的是一个依赖对象的代理对象，从而避免无限循环链的产生。该代理对象的方法会最终指向依赖对象的方法。

    ```java
    @Component
    public class A {
        final B b;

        public A(B b) {
            this.b = b;
        }
    }

    @Component
    public class B {
        final A a;

        public B(@Lazy A a) {
            this.a = a;
        }
    }

    // 注入顺序：先A后B
    // 解决流程：
    // 1. A 初始化发现 B，但是自身没有初始化完成，无法放入缓存
    // 2. B 初始化发现 @Lazy A a，会得到A的代理对象proxy，初始化B成功并放入缓存
    // 3. 从缓存中获取到B，A 初始化成功，并放入缓存
    // 后续使用：B中调用A的方法，本质调用proxy对象，而proxy对象则指向到在缓存中已初始化完毕的a
    ```

# **3. FactoryBean和BeanFactory**

BeanFacotry：bean的工厂，用于生产和管理bean对象

FactoryBean：指的是具备工厂能力的bean，本身不能被当做一个正常的bean实例使用，用于产出其它bean

正常情况下bean无须自己实现工厂模式，Spring已担任了工厂角色，但少数情况下bean本身就应是工厂

# **4. AOP（JDK动态代理和CGLIB动态代理区别）**

JDK动态代理：
- 机制：基于**反射机制**实现，target类**若实现某个`接口`AOP则会使用JDK动态代理**，生成一个实现同样接口的代理类
- 关系：被代理类和代理类是接口实现关系
- 调用方式：委托，在调用具体代码前，调用重写的InvokeHandler方法，来实现对代码的增强

    ```java
    new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable
        {
            // ....添加扩展点

            try {
                return method.invoke(delegate, args) ;
            } catch (InvocationTargetException ex) {
                throw ex.getCause() ;
            }
        }
    } ;
    ```

CGLIB动态代理：
- 机制：基于asm第三方框架，通过**修改字节码生成一个`子类`**，然后重写父类的方法，实现对代码的增强
- 关系：被代理类和代理类是继承关系
- 调用方式：调用methodInterceptor.intercept()

    ```java
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(MainServiceImpl.class);
    enhancer.setCallback(new MethodInterceptor() {
        @Override
        public Object intercept(Object obj, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
            System.out.println("begin time:"+System.currentTimeMillis());
            Object object = methodProxy.invokeSuper(obj, objects);
            System.out.println("end time:"+System.currentTimeMillis());
            return object;
        }
    });
    MainService proxy= (MainService)enhancer.create();
    proxy.doSomeThing();
    ```

```java
// JDK代理生成的class
public void 被代理方法() {
    invocationHandler.invoke(...)
}

// CGLIB代理生成的class
public void 被代理方法() {
    // intercept中包含了cglib，invoke或invokeSuper两种方式，后者可使得this调用到增强对象
    methodInterceptor.intercept(...);
}
```

默认情况下，spring使用jdk动态代理，若被代理对象没有接口，自动切换为cglib动态代理

# **5. AOP实现原理**

通过动态代理的方式产出代理对象，创建内层拦截器链串联增强能力，并在链条执行的最尾部时的默认拦截器，从而触发目标方法的调用

过程：

1. 创建代理对象

    - 传入拦截器数组，目标对象，目标对象接口数组
    - 调用 getProxy 方法的时候，会根据接口数量选择使用JDK proxy还是cglib
        - 大于0用JDK PROXY
        - 等于0用Cglib

2. 调用代理对象方法：对proxy对象的调用，会进入到拦截器链的逻辑中，之中通过维护拦截器数组下标进行递归调用

**问题：AOP使用this导致注解失效**

因为this获得的是被代理对象，而不是增强对象，所以没有增强行为

解决方案：

1. 实现BeanFactoryAware接口获得beanFactory，再通过beanFactory来获取增强对象

2. 如@Transactional注解上，配置expose-proxy = true，将代理对象暴露在AopUtils线程变量里，使用增强对象时通过线程变量获取

3. @Configuration思路，实现cglib的拦截器，具体通过调用invokeSuper()来取代调用invoke()

# **6. 事务传播机制**

开启事务：
通过@EnableTransactionManagement(proxyTargetClass = true)开启事务，注解包括`@Import(TransactionManagementConfigurationSelector.class)`，由ConfigurationClassPostProcessor解析

@Transactional：
对象是否有事务增强由bean是否有@Transactional注解决定，最终会调用`AbstractPlatformTransactionManager`对事务进行管理

事务传播机制（PROPAGATION）：在多个方法的调用中，规定事务传递的方式，共有7种事务传播机制

- REQUIRED：默认，如果当前线程没有事务，则创建一个事务，否则加入该事务

    - 特性：加入当前事务后，如果操作失败，则当前整个事务失败

- REQUIRED_NEW：无论当前线程是否存在事务，都会创建新事务

    - 特性：新的事务与当前事务各自独立，失败互不影响

- NESTED：如果当前线程没有事务，则创建一个事务，否则创建一个当前事务的**子事务（嵌套事务）**

    - 特性：已存在事务的子事务，它是外部事务的一部分，只有外部事务结束后它才会提交。**子事务**的异常抛出，不会直接导致当前事务失败，可通过捕获异常来进行处理

- SUPPORTS：如果当前线程有事务，则加入到事务中，否则以非事务的方式运行

- NOT_SUPPORTS：一直以非事务方式执行，如果当前线程有事务，则阻塞挂起当前事务，并将被调用方的方法以非事务方式运行

- NEVER：一直以非事务方式执行，如果当前线程有事务，则直接抛出异常

- MANDATORY：当前事务必须运行在事务内部，如果没有运行的事务，就抛出异常

# **7. 共有几种装配方式**

注入对象的方式：

- bean扫描发现机制（@ComponentScan + @Component、@Bean、@Service、@Controller） + 自动装配（@Autowired、@Resource）

- 在Java中进行显示配置（@Configuration）

- 在XML中进行显示配置

自动装配：主要根据bean的类型和名称进行注入

- @Autowired：默认先按bean的`type`注入，存在多个bean时再按`name`注入，默认要求依赖对象必须**存在且唯一**

    - 允许不存在，标注@Autowired(required = false)

    - 若确实存在同个类型多种实现，需配合@Primary、@Qualifier，使用@Qualifier后将直接按`name`注入

- @Resource：默认先按bean的`name`注入，如果没有匹配再按bean的`type`装配

    - 不指定，按默认规则（效率差）

    - 同时指定name和type，从上下文中找到唯一匹配的bean进行装配

    - 只指定name：只按`name`注入，不会再通过`type`匹配

    - 只指定type：只按`type`注入，不会再通过`name`匹配

# 参考

- [Cglib invoke以及invokeSuper的一点区别](https://www.cnblogs.com/lvbinbin2yujie/p/10284316.html)

- [cglib动态代理中invokeSuper和invoke的区别](https://blog.csdn.net/weixin_43634610/article/details/103487513)

- [Spring AOP、AspectJ、CGLIB 都是什么鬼？它们有什么关系？](https://zhuanlan.zhihu.com/p/426442535)：曾经以为AspectJ是Spring AOP一部分，是因为Spring AOP使用了AspectJ的Annotation。使用了Aspect来定义切面,使用Pointcut来定义切入点，使用Advice来定义增强处理。虽然使用了Aspect的Annotation，但是并没有使用它的编译器和织入器。其实现原理是JDK 动态代理，在运行时生成代理类

# 重点参考