# Spring：事务

# **开启事务**

```java
@EnableTransactionManagement
@Configuration
public class JDBCConfig {
    @Bean
    public DruidDataSource druidDataSource() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUsername("...");
        druidDataSource.setPassword("...");
        druidDataSource.setDriverClassName("...");
        druidDataSource.setUrl("...");
        return druidDataSource;
    }

    @Bean
    public JDBCService jdbcService(DruidDataSource druidDataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(druidDataSource);
        return new JDBCService(jdbcTemplate);
    }

    @Bean
    public DataSourceTransactionManager dataSourceTransactionManager(DruidDataSource druidDataSource) {
        return new DataSourceTransactionManager(druidDataSource);
    }
}
```

看一下@EnableTransactionManagement注解，它与AOP的`@EnableAspectJAutoProxy`注解十分想死

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TransactionManagementConfigurationSelector.class)
public @interface EnableTransactionManagement {

	boolean proxyTargetClass() default false;

	AdviceMode mode() default AdviceMode.PROXY;

	int order() default Ordered.LOWEST_PRECEDENCE;

}
```

在`ConfigurationClassPostProcessor`中读取配置，在Import解析后发现引入一个TransactionManagementConfigurationSelector类型的bean，它是一个`ImportSelector`类型的bean：

> AOP注解引入的`AspectJAutoProxyRegistrar`是ImportBeanDefinitionRegistrar类型的，直接调用其registerBeanDefinitions方法来注册一些定制化的bean定义即可

```java
// ImportSelector一般会在selectImports方法中加入分支（AdviceMode）的概念，以达到更灵活的定制注册
public class TransactionManagementConfigurationSelector extends AdviceModeImportSelector<EnableTransactionManagement> {
	@Override
	protected String[] selectImports(AdviceMode adviceMode) {
        // 默认情况下为AdviceMode.PROXY模式，在ConfigurationClassParser的processImports方法进行分支递归
		switch (adviceMode) {
			case PROXY:
				return new String[] {AutoProxyRegistrar.class.getName(), ProxyTransactionManagementConfiguration.class.getName()};
			case ASPECTJ:
				return new String[] {TransactionManagementConfigUtils.TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME};
			default:
				return null;
		}
	}
}
```

所以在默认情况下，Selector向bean工厂**再次传递**以下两种新的**Import类型**的组件：

- `AutoProxyRegistrar`

    `ImportBeanDefinitionRegistrar`类型，用于向工厂注册`InfrastructureAdvisorAutoProxyCreator`，以实现事务对象代理
    
    代理的方式根据注解的`proxy-target-class`决定，true表示强制使用cglib

    ```java
    // AutoProxyRegistrar.class
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            boolean candidateFound = false;
            Set<String> annoTypes = importingClassMetadata.getAnnotationTypes();
            for (String annoType : annoTypes) {
                // ...
                Object mode = candidate.get("mode");
                Object proxyTargetClass = candidate.get("proxyTargetClass");
                if (mode != null && proxyTargetClass != null && AdviceMode.class == mode.getClass() && Boolean.class == proxyTargetClass.getClass()) {
                    candidateFound = true;
                    if (mode == AdviceMode.PROXY) {
                        // 将AbstractAutoProxyCreator的bean定义加载到工厂中
                        AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
                        if ((Boolean) proxyTargetClass) {
                            // 如果proxyTargetClass == true，则强制使用cglib模式代理
                            AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
                            return;
                        }
                    }
                }
            }

            // ...
        }
    ```

- `ProxyTransactionManagementConfiguration`

    `Configuration`类型，着重注意其注入的`BeanFactoryTransactionAttributeSourceAdvisor`这个bean，该bean是SpringAOP事务的核心

    ```java
    @Configuration
    public class ProxyTransactionManagementConfiguration extends AbstractTransactionManagementConfiguration {
        // bean的标识符为"org.springframework.transaction.config.internalTransactionAdvisor"
        @Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        public BeanFactoryTransactionAtteributeSourceAdvisor transactionAdvisor() {
            // 将下面两个bean都加载到Advisor中
            BeanFactoryTransactionAtteributeSourceAdvisor advisor = new BeanFactoryTransactionAtteributeSourceAdvisor();
            advisor.setTransactionAttributeSource(transactionAttributeSource());
            advisor.setAdvice(transactionInterceptor());
            if (this.enableTx != null) {
                // enableTx为注解属性的map，在父类中加载并转为为map
                // 取注解的order属性，返回值为整型
                advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
            }
            return advisor;
        }

        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        public TransacationAttributeSource transactionAttributeSource() {
            return new AnnotationTransactionAttributeSource();
        }

        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        public TransactionInterceptor transactionInterceptor() {
            TransactionInterceptor interceptor = new TransactionInterceptor();
            // 注入transactionAttributeSource的bean
            interceptor.setTransactionAttributeSource(transactionAttributeSource());
            if (this.txManager != null) {
                // txManager由父类方法setConfigurers进行自动注入，可以为null，因为required属性为false
                interceptor.setTransactionManager(this.txManager);
            }
            return interceptor;
        }
    }
    ```

# **事务注解与AOP结合**

一个传统的JDBC应用事务时，包括以下操作：
1. 开启事务
2. 增删改操作
3. 操作过程出现异常，则事务回滚
4. 操作正常完毕，则事务提交

使用`@Transactional`注解后，我们只需要专注于我们的业务，而不需要关心其它的操作，这是因为Spring事务通过AOP实现

读取Advisor的逻辑仍然在`AbstractAutoProxyCreator`中，然而读取增强信息并不在`postProcessBeforeInstantiation`时间节点，这是因为InfrastructureAdvisorAutoProxyCreator并没有重写shouldSkip方法

流程：**在bean自定义init方法调用后，读取事务相关AOP信息，并返回事务增强**

相关类：InfrastructureAdvisorAutoProxyCreator

```java
public class InfrastructureAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator {
	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	@Override
	protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        super.initBeanFactory(beanFactory);
        this.beanFactory = beanFactory;
	}

	@Override
	protected boolean isEligibleAdvisorBean(String beanName) {
		return (this.beanFactory != null && this.beanFactory.containsBeanDefinition(beanName) && this.beanFactory.getBeanDefinition(beanName).getRole() == BeanDefinition.ROLE_INFRASTRUCTURE);
	}

    // 并没有重写shouldSkip和findCandidates两个方法，则默认都使用父类的
    // shouldSkip()默认返回false，findCandidates()返回全部类型为Advisor的bean
}
```

前面提到，BeanFactoryTransactionAttributeSourceAdvisor是在`postProcessAfterInitialization`节点被加载，并且会被应用到有`@Transactional`的bean上：

```java
// AbstractAdvisorAutoProxyCreator.class
// 该调用栈来自wrapIfNeccessary()中的getAdvicesAndAdvisorsForBean()方法
protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
    // 加载所有Advisor类型的组件，包括BeanFactoryTransactionAttributeSourceAdvisor
    List<Advisor> candidateAdvisors = findCandidateAdvisors();
    // 判断这些增强组件中，有哪些是可以应用到当前bean的，内部调用关键方法canApply
    List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
    // ...
    return eligibleAdvisors;
}

public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
    if (advisor instanceof IntroductionAdvisor) {
        return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
    }
    else if (advisor instanceof PointcutAdvisor) {
        // 事务的增强是PointcutAdvisor类型，走这
        PointcutAdvisor pca = (PointcutAdvisor) advisor;

        // 事务增强的Pointcut，是TransactionAttributeSourcePointcut
        return canApply(pca.getPointcut(), targetClass, hasIntroductions);
    }
    else {
        // It doesn't have a pointcut so we assume it applies.
        return true;
    }
}

public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
    // 筛除filter中没有的对象，默认filter会对全部对象返回true，表示全部都可通过
    if (!pc.getClassFilter().matches(targetClass)) {
        return false;
    }

    // 事务的pointcut调用getMethodMatcher方法后，返回的还是pc自身
    MethodMatcher methodMatcher = pc.getMethodMatcher();
    if (methodMatcher == MethodMatcher.TRUE) {
        return true;
    }

    // 事务并不是该类型的，所以为null
    IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
    if (methodMatcher instanceof IntroductionAwareMethodMatcher) {
        introductionAwareMethodMatcher = (IntroductionAwareMethodMatcher) methodMatcher;
    }

    // 递归获得目标bean实现或继承的所有接口
    Set<Class<?>> classes = new LinkedHashSet<>(ClassUtils.getAllInterfacesForClassAsSet(targetClass));
    classes.add(targetClass);
    // 遍历bean的所有接口
    for (Class<?> clazz : classes) {
        // 获得接口的所有方法
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
        for (Method method : methods) {
            // 走后半段的methodMatcher.matches(method, targetClass)方法，也就是TransactionAttributeSourcePointcut重写的matches方法
            if ((introductionAwareMethodMatcher != null && introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions)) || methodMatcher.matches(method, targetClass)) {
                return true;
            }
        }
    }

    return false;
}
```

是否能应用该增强的逻辑跳转到了`TransactionAttributeSourcePointcut`的matches方法：

> source为`AnnotationTransactionAttributeSource`，在@ImportSelector中引入

```java
public abstract class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut {
    @Override
    public boolean matches(Method method, @Nullable Class<?> targetClass) {
        // 如果是事务代理类，则说明已经被代理过了
        if (targetClass != null && TransactionProxy.class.isAssignableFrom(targetClass)) {
            return false;
        }
        // 该方法调用事务增强中的transactionAttributeSource
        TransactuionAttributeSource tas = getTransactionAttributeSource();
        // 查找method和targetClass是否有事务属性
        return (tas == null || tas.getTransactionAttribute(method, targetClass) != null);
    }
}
```

AnnotationTransactionAttributeSource的父类方法`getTransactionAttribute`会为每个method缓存其是否有事务属性：

```java
// AbstractFallbackTransactionAttributeSource.class
public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
    if (method.getDeclaringClass() == Object.class) {
        return null;
    }

    Object cacheKey = getCacheKey(method, targetClass);
    // 从缓存中获取method是否有事务属性
    Object cached = this.attributeCache.get(cacheKey);
    if (cached != null) {
        if (cached == NULL_TRANSACTION_ATTRIBUTE) {
            return null;
        }
        else {
            return (TransactionAttribute) cached;
        }
    }
    else {
        // 重中之重，该方法会解析当前方法及其类的事务属性
        TransactionAttribute txAttr = computeTransactionAttribute(method, targetClass);
        if (txAttr == null) {
            // 没有事务，则缓存空结果到缓存中
            this.attributeCache.put(cacheKey, NULL_TRANSACTION_ATTRIBUTE);
        }
        else {
            // 获取方法的签名属性
            String methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
            if (txAttr instanceof DefaultTransactionAttribute) {
                // 如果事务属于DefaultTransactionAttribute，则将方法的签名设置到其描述属性（descriptor）中
                ((DefaultTransactionAttribute) txAttr).setDescriptor(methodIdentification);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Adding transactional method '" + methodIdentification + "' with attribute: " + txAttr);
            }
            // 方法具备事务属性，缓存解析结果
            this.attributeCache.put(cacheKey, txAttr);
        }
        return txAttr;
    }
}
```

看一下读取AOP信息的最重要方法`computeTransactionAttribute`，它通过反射来解析方法以及类，查找是否有事务属性：

> 在阅读这部分代码时，要注意区分两个概念，一个是传入方法`形参method`，另一个是目标方法`specificMethod`

```java
// AbstractFallbackTransactionAttributeSource.class
protected TransactionAttribute computeTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
    // 如果设置了只允许public修饰符方法可以应用事务，则非公有的方法都不可以使用事务
    if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
        return null;
    }

    // 忽略cglib代理的子类
    Class<?> userClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
    // 获取最为准确的目标方法，因为method可能只是个接口方法，会寻找有其实现的方法
    Method specificMethod = ClassUtils.getMostSpecificMethod(method, userClass);
    // 如果当前方法是一个泛型方法，则会找Class文件中时机实现的方法
    specificMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);
    
    // 1. 先尝试解析目标方法，获取其事务属性
    TransactionAttribute txAttr = findTransactionAttribute(specificMethod);
    if (txAttr != null) {
        return txAttr;
    }
    
    // 2. 再尝试去解析目标方法所在类，获取类是否有事务属性
    txAttr = findTransactionAttribute(specificMethod.getDeclaringClass());
    // 如果存在事务属性，并且传入方法是用户实现的方法
    if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
        return txAttr;
    }

    // 如果通过解析到的方法无法找到事务属性，则判断解析得到的方法与传入的目标方法是否为同一个方法，
    // 如果不是同一个方法，则尝试对传入的方法及其所在的类进行事务属性解析
    if (specificMethod != method) {
        // 对传入方法解析事务属性，如果存在，则直接返回
        txAttr = findTransactionAttribute(method);
        if (txAttr != null) {
            return txAttr;
        }

        // 对传入方法所在类进行事务属性解析，如果存在，则直接返回
        txAttr = findTransactionAttribute(method.getDeclaringClass());
        if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
            return txAttr;
        }
    }
    return null;
}
```

解析事务属性的方法，是通过**解析目标方法上的@Transactional注解**，若有则封装为TransactionAttribute对象并返回，这个事务属性对象包含了用户在@Transactional注解上填写的属性值

到这里，最上层的InfrastructureAdvisorAutoProxyCreator在`postProcessAfterInitialization`节点的工作已经完成了一半：

```java
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    // ...

    // 这里是上面增强获取源码分析的最上层入口
    Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
    if (specificInterceptors != DO_NOT_PROXY) {
        this.advisedBeans.put(cacheKey, Boolean.TRUE);
        // 这里将获取事务增强，我们在下面继续介绍
        Object proxy = createProxy(
                bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    }

    this.advisedBeans.put(cacheKey, Boolean.FALSE);
    return bean;
}
```

同AOP获取切面创建代理对象一致，createProxy()方法在创建jdk代理/cglib代理时，都会将上面的事务增强器以组合/继承的方式加入到代理对象中，并返回最终的代理对象

# **调用拦截链方法**

cglib为例，当每次调用代理对象的接口方法时，进而会调用到拦截器数组中的每个拦截器的intercept方法来进行**动态增强**，其中就包括了`DynamicAdvisedInterceptor`

```java
// CglibAopProxy#DynamicAdvisedInterceptor
private static class DynamicAdvisedInterceptor implements MethodInterceptor, Serializable {
    // 在创建cglib时传入的ProxyFactory类型对象，里面包含了事务增强组件
    private final AdvisedSupport advised;

    public DynamicAdvisedInterceptor(AdvisedSupport advised) {
        this.advised = advised;
    }

    @Override
    public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) {
        // ...

        // 将ProxyFactory中的全部Advisor组件按照order串成一条职责链
        List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
        Object retVal;
        if (chain.isEmpty() && Modifier.isPublic(method.getModifiers())) {
            Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
            retVal = methodProxy.invoke(target, argsToUse);
        }
        else {
            // chain肯定不为空啦，有事务增强组件的拦截逻辑

            // proceed()递归，将全部动态拦截增强都执行完
            retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
        }
        retVal = processReturnType(proxy, target, method, retVal);
        return retVal;
    }
}
```

上面的getInterceptorsAndDynamicInterceptionAdvice，会将全部Advisor组件的advice对象串成职责链，并缓存起来

这条链上每个Advice对象的`invoke()方法`都会在后续的CglibMethodInvocation类中被依次调用

> 这条职责链就包括了BeanFactoryTransactionAttributeSourceAdvisor的advice，它是TransactionInterceptor，是在Import时注册的另外一个bean

```java
public class TransactionInterceptor extends TransactionAspectSupport implements MethodInterceptor, Serializable {
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);
        // 被代理的方法，被代理的对象，以及被代理方法包装类的proceed方法
        return invokeWithTransaction(invocation.getMethod(), targetClass, invocation::proceed);
    }
}
```

# **声明式事务**

invokeWithTransaction()方法的实现在TransactionInterceptor的父类上，其包含了事务开启的细节：

> 在[《AOP总结一文》]()简单介绍了其是如何开启事务，并且以事务开启为例讲解了AOP代理下this的问题

```java
// TransactionInterceptor.class
protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass, final InvocationCallback invocation) throws Throwale {
    TransactionAttributeSource tas = getTransactionAttributeSource();
    // 获取事务属性
    final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
    // 返回一个PlatformTransactionManager
    final PlatformTransactionManager tm = determineTransactionManager(txAttr);
    // 方法的标识，生成的字符串为："[targetClass的标识符].[method]"，如"userService.testTransaction"
    final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);
    if (txAttr == null || (!tm instanceof CallbackPreferringPlatformTransactionManager)) {
        // 声明式事务，非侵入所以延伸性良好，粒度最小为方法但是有解决方法

        // 1. 创建事务
        TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
        Object retVal = null;
        try {
            // 2. 执行调用链和目标方法，即包装后的MethodInvocation的proceed()方法
            retVal = invocation.proceedWithInvocation();
        } catch (Throwale ex) {
            // 3. 异常，则事务回滚
            compleTransactionAfterThrowing(txInfo, ex);
            throw ex;
        } finally {
            // 4. 清除事务信息
            clearupTransactionInfo(txInfo);
        }
        // 5. 成功则提交事务
        commitTransactionAfterReturning(txInfo);
        return retVal;
    } else {
        // 编程式事务，因为代码侵入性强，业务上不推荐使用
    }
}
```

## **创建事务**

创建事务并没有显式调用`begin`，而是隐式事务

在隐式事务之下，数据库实例第一次执行`update`、`insert`、`alert table`、`create`等语句时，会自动开启事务，开启的事务需要**显式调用**commit或rollback结束。当事务结束时，一旦运行上面的语句，又会开启新的一轮事务，形成一个`事务链`

> 隐式事务在事务结束时一定要记得调用**commit**或**rollback**

入参：

- tm：在上一层返回的PlatformTransactionManager
- txAttr：事务属性，在前置的是否能应用事务增强的判断逻辑中有获取过一遍
- joinpointIdetification：方法标识符

```java
protected TransactionInfo createTransactionIfNeccessary(@Nullable PlatformTransactionManager tm, @Nullable TransactionAttribute txAttr, final String joinpointIdentification) {
    if (txAttr != null && txAttr.getName() == null) {
        txAttr = new DelegatingTransactionAttribute(txAttr) {
            @Override
            public String getName() {
                return joinpointIdentification;
            }
        };
    }

    TransactionStatus status = null;
    if (txAttr != null) {
        if (tm != null) {
            // 1. 创建事务
            status = tm.getTransaction(txAttr);
        } else {
            if (logger.isDebugEnabled()) {
                // ...
            }
        }
    }
    // 2. 构建事务信息
    return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
}
```

tm.getTransaction的具体实现在`AbstractPlatformTransactionManager`中，而TransactionManager真实的DataSource我们在文章一开头的配置已经注册了，为`DruidDataSource`

在这里开始要与JDBC的DataSource接口做交互了：

```java
// AbstractPlatformTransactionManager.class
@Override
public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition) {
    // 1.1 获取事务
    Object transaction = doGetTransaction();

    boolean debugEnabled = logger.isDebugEnabled();

    if (definition == null) {
        // 如果没有事务定义（事务属性）
        definition = new DefaultTransactionDefinition();
    }

    // 1.2 判断当前线程是否存在事务，涉及传播机制
    if (isExistingTransaction(transaction)) {
        // 线程存在事务则使用嵌套事务/当前事务/新起事务处理，具体的做法由传播机制的特性决定
        return handleExistingTransaction(definition, transaction, debugEnabled);
    }

    if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
        // 如果当前没有事务，但是事务的传播行为被定义为PROPAGATION_MANDATORY，则抛出异常
        throw new IllegalTransactionStateException("No existing transaction found for transaction marked with propagation 'mandatory'");
    } else if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED || definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW || definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
        // 当事务的传播行为需要新建事务时的处理
        SuspendedResourcesHolder suspendedResources = suspend(null);
        if (debugEnabled) {
            logger.debug("Creating new transaction with name [" + definition.getName() + "]: " + definition);
        }
        try {
            boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
            DefaultTransactionStatus status = newTransactionStatus(definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);
            // 3. 准备事务
            deBegin(transaction, definition);
            // 4. 记录事务状态
            prepareSynchronization(status, definition);
            return status;
        } catch (RuntimeException | Error ex) {
            resume(null, suspendedResources);
            throw ex;
        }
    } else {
        // 创建空事务
    }
}
```

着重看一下第三步准备事务操作`doBegin(transaction, definition)`：

```java
@Override
protected void doBegin(Object transaction, TransactionDefinition definition) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    Connection con = null;

    try {
        if (!txObject.hasConnectionHolder() || txObject.getConnectionHolder().isSynchronziedWithTransaction()) {
            // 判断传进来的事务对象是否有连接绑定（在1.1获取的时候会有绑定）
            Connection newCon = obtainDataSource().getConnection();
            if (logger.isDebugEnabled()) {
                logger.debug("Acquired Connect [" + newCon + "] for JDBC transaction");
            }
        }

        // 设置事务的同步标识
        txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
        // 获取绑定了的当前数据库连接
        con = txObject.getConnectionHolder().getConnection();
        // 获取设置的事务隔离级别，definition就是事务注解属性的DTO类
        // 方法会将当前连接的隔离级别设置为事务注解定义的级别，并返回之前设置的隔离级别
        Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition)；
        // txObject设置previousIsolationLevel为之前的隔离级别，而且con是当前注解的隔离级别
        txObject.setPreviousIsolationLevel(previosIsolationLevel);
        
        // 设置自动提交
        if (con.getAutoCommit()) {
            txObject.setMustRestoreAutoCommit(true);
            if (logger.isDebugEnabled()) {
                logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
            }
            con.setAutoCommit(false);
        }

        prepareTransactionalConnection(con, definition);
        //设置当前线程存在事务的标识
        txObject.getConnectionHolder().setTransactionActive(true);

        //设置超时时间
        int timeout = determineTimeout(definition);
        if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
            txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
        }

        // 将当前数据源对于的事务连接，绑定到当前线程的线程变量中
        if (txObject.isNewConnectionHolder()) {
            TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
        }
    }
}
```

## **传播机制**

## **事务提交**

## **事务回滚**

保存点，save-point，快照

# **进度记录**

今天任务：看到事务拦截器TransactionInterceptor是如何被代理调用的

中秋两日任务：看完拦截器事务操作的具体细节，包括：DataSourceTransactionManager，事务传播级别

中秋第一天进度：在查找事务的”begin“语句（找到了，目前猜测是隐式事务）


# 参考
- [Spring事务源码解析（一）@EnableTransactionManagement注解](https://mp.weixin.qq.com/s/FU3hznLFspCcHYJs-x8h2Q)
- [Spring事务源码解析（二）获取增强](https://mp.weixin.qq.com/s/5tTrdl5GuD9WAyuHNUvW8w)
- [Spring事务源码解析（三）](https://mp.weixin.qq.com/s?__biz=MzU5MDgzOTYzMw==&mid=2247484625&idx=1&sn=e57f571d353cfb98ac3f6b0df755ea1a&chksm=fe396eefc94ee7f9c84a7a873cfadf2e55ef85eee6efa06a137a6feb472edce45befb7eb1d83&scene=178&cur_album_id=1344425436323037184#rd)