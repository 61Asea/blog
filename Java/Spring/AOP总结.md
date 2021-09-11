# **Spring：使用AOP可能会遇到的问题**

# **1. this引用问题**

> [《详解Spring中Bean的this调用导致AOP失效的原因》](https://my.oschina.net/guangshan/blog/1807721)：在我们使用Spring时，可能有前辈教导过我们，在bean中不要使用this来调用被@Async、@Transactional、@Cacheable等注解标注的方法，this下注解是不生效的

## **1.1 this下注解失效**

JDK动态代理和CGLIB字节码代理会各自生成代理类的class文件，当调用容器bean的接口方法时时，会对应调用到代理类的相同接口：

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

两种代理模式相对于的`调用入口`：

```java
// JdkDymamicAopProxy.class
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // ...
    invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
    retVal = invocation.proceed();
}

// CglibAopProxy#DynamicAdvisedInterceptor.class
public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
    // ...

    retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain).proceed();
}
```

最终都会调用ReflectiveMethodInvocation的`proceed()`方法，对注册的拦截器进行调用，来实现注解的功能：

```java
// ReflectiveMethodInvocation.class
public Object proceed() throws Throwable {
    if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
        // 递归的基础条件
        return invokeJoinpoint();
    }

    // ...
    InterceptorAndDynamicMethodMatcher dm =
            (InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
    if (dm.methodMatcher.matches(this.method, this.targetClass, this.arguments)) {
        // 调用注解对应的拦截器，如：
        // @Async对应AsyncExecutionInterceptor
        // @Transactional对应TransactionInterceptor
        return dm.interceptor.invoke(this);
    }
    else {
        // 递归调用
        return proceed();
    }
}

@Nullable
protected Object invokeJoinpoint() throws Throwable {
    // this.target是真实对象
    return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
}

```

> AOP动态代理（proxy）的方法真实调用，会使用被代理对象（target）实例进行方法调用，故在实例方法中通过this获取的都是被代理的真实对象

## **1.2 可能使得synchronized不符合预期互斥效果**

场景：有以下代码通过启动1000个线程，为id为1的实体对象的属性进行递增操作，假设初始值为0，则希望得到结果值为1000

```java
@RestController
public class EmployController {
    @Service
    private EmployService employService;

    @RequestMapping("/add")
    public void add() {
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> employService.increaseAge()).start();
        }
    }
}

@Service
public class EmployService {
    @Autowired
    private EmployRepository employRepository;

    // @Transactional：L通过Spring AOP将数据库事务操作织入到业务中
    @Transactional
    // synchronized：预期通过互斥量使得多个线程串行执行
    public synchronized void increaseAge() {
        Employee employee = employRepository.findById(1);
        employee.setAge(employee.getAge() + 1);
        employRepository.save(employee);
    }
}
```

做法：为了防止线程并发问题而采取synchronized，以期望`每个线程的事务执行到提交是串行的`

结果：employee的age可能会小于1000

原因：以上的代码并不能符合事务串行执行并提交的预期，synchronized代码块的生效区域并没有覆盖整个事务操作，多个线程可能会读到相同的age值进行操作，**可以理解为synchronized锁的是this引用**

> **该同步方法是增强对象执行的其中一步**，synchronized即并未包含事务的提交操作

```java
// TransactionAspectSupport.class
protected Object invokeWithTransaction(Method method, Class<?> targetClass, final InvocationCallbck invocation) throws Throwable {
    // ...

    // 开启事务
    TranscationInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
    Object retVal = null;
    try {
        // 调用target（被代理对象）的方法
        retVal = invocation.proceedWithInvocation();
    } catch (Throwable ex) {
        completeTransacationAfterThrowing(txInfo, ex);
        throw ex;
    } finally {
        cleanupTransactionInfo(txInfo);
    }
    // 提交事务
    commitTransactionAfterReturning(txInfo);
    return retVal;
}
```

解决方案：将synchronized的生效区域扩大覆盖到增强对象的整个执行周期

```java
@Service
public class EmployService {
    @Autowired
    private EmployRepository employRepository;

    @Transactional
    // 去掉target方法的互斥量，没用
    public void increaseAge() {
        Employee employee = employRepository.findById(1);
        employee.setAge(employee.getAge() + 1);
        employRepository.save(employee);
    }
}

@RestController
public class EmployController {
    @Service
    private EmployService employService;

    private static Object mutexObject;

    @RequestMapping("/add")
    public void add() {
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> {
                // 互斥量覆盖到整个代理对象方法的执行
                synchronized (mutexObject) {
                    employService.increaseAge();
                }
            }).start();
        }
    }
}
```

## **1.3 解决方案**

- 通过ApplicationContext来获得动态代理对象

    ```java
    public class Service implements ApplicationContextAware {
        private ApplicationContext applicationContext;

        public void test() {
            applicationContext.getBean("service").dbOpts();
        }

        @Transactional
        public void dbOpts() {

        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }
    }
    ```

    > BeanFactoryAware可达到同样效果，通过装配的bean工厂来getBean

- 线程变量AopContext.currentProxy();

    两种代理模式都会通过expose-proxy属性来设置代理对象到线程变量中：

    ```java
    // JdkDynamicAopProxy.class
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // ...
        if (this.advised.exposeProxy) {
            oldProxy = AopContext.setCurrentProxy(proxy);
            setProxyContext = true;
        }
    }

    // CglibAopProxy$DynamicAdvisedinterceptor.class
    public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
        // ....

        try {
            if (this.advised.exposeProxy) {
                // Make invocation available if necessary.
                oldProxy = AopContext.setCurrentProxy(proxy);
                setProxyContext = true;
            }
        }
    }
    ```

    通过AopContext来获取动态代理对象：

    ```java
    @Component
    public class AsyncService {
        public void async1() {
            System.out.println("1:" + Thread.currentThread().getName());
            ((AsyncService) AopContext.currentProxy()).async2();
        }

        @Async
        public void async2() {
            System.out.println("2:" + Thread.currentThread().getName());
        }

    }
    ```

    > 然而这种方式对@Async注解不生效，因为它不是使用AspectJ的自动代理（AbstractAutoProxyCreator），而是使用**代码中固定的创建代理方式进行代理创建**

# **2. @Configuration对@bean动态代理**

@Configuration采用cglib模式，它通过重写Interceptor来实现不同的intercept方法

```java
// ConfigurationClassEnhancer#BeanMethodInterceptor
private static class BeanMethodInterceptor implements MethodInterceptor, ConditionalCallback {
    @Override
    @Nullable
    public Object intercept(Object enhancedConfigInstance, Method beanMethod, Object[] beanMethodArgs,
    MethodProxy cglibMethodProxy) throws Throwable {
        // 。。。

        if (isCurrentlyInvokedFactoryMethod(beanMethod)) {
            // 。。。
            return cglibMethodProxy.invokeSuper(enhancedConfigInstance, beanMethodArgs);
        }
    }
}
```

可以发现，它没有像以上的对象调用target对象，而是通过直接调用cglib的父类方法，这样则使得this引用生效，这里的差距是通过：cglib的拦截器对MethodProxy接口方法调用不同导致的

> 调用链：调用代理对象的期望接口方法 -> 调用拦截器的intercptor方法 -> 以下代码

注意以下两种方式调用，this的上下文是不同的，前者是对于父类的上下文，

- MethodProxy.invoke()：上下文只有target，this指代的就是target

    > interceptor方法中会ReflectiveMethodInvocation接口的proceed方法，该方法会再调用到以下代码

    ```java
    // CglibAopProxy#CglibMethodInvocation
    private static class CglibMethodInvocation extends ReflectiveMethodInvocation {
        private final MethodProxy methodProxy;

        protected Object invokeJointpoint() throws Throwable {
            // ...

            // invoke方法，调用传入target的真实方法
            return this.methodProxy.invoke(this.target, this.arguments);
        }
    }
    ```

- MethodProxy.invokeSuper()（@Configuration就是属于这种方式）：上下文是代理对象，super方法中的this指代代理对象

    ```java
    // ConfigurationClassEnhancer # BeanMethodInterceptor
    private static class BeanMethodInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(..., Method cglibMethodProxy) {
            // ...

            // 调用父类方法，this引用生效
            return cglibMethodProxy.invokeSuper(enhancedConfigInstance, beanMethodArgs);
        }
    }
    ```

# 参考
- [SpringAOP会导致的一些问题](https://www.jianshu.com/p/5fd84480c43f)
- [@Configuration 和 @Component 区别](https://blog.csdn.net/isea533/article/details/78072133)

- [Cglib源码分析 invoke和invokeSuper的差别](https://blog.csdn.net/makecontral/article/details/79593732)：从反编译class文件的角度来解释

- [Cglib invoke以及invokeSuper的一点区别](https://www.cnblogs.com/lvbinbin2yujie/p/10284316.html)

# 重点参考
- [详解Spring中Bean的this调用导致AOP失效的原因](https://my.oschina.net/guangshan/blog/1807721)
- [完全读懂Spring框架之AOP实现原理](https://my.oschina.net/guangshan/blog/1797461)