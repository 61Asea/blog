# Spring：AOP

> 在区分`@Configuration`和`@Component`分别取代了xml配置文件哪些部分时，了解到@Configuration会返回增强后的的bean实例。并且在看IOC时也对`Spring BBP`没有详细的学习，这些都与Spring的另一个特性-`AOP`息息相关

# **开启AOP**

以JavaConfig为例，开启AOP功能只需加上注释`@EnableAspectJAutoProxy`：

```java
@EnableAspectJAutoProxy
@Configuration
public class JavaConfig {
    // ...
}
```

简单看下注解类EnableAspectJAutoProxy的实现，重点在于`@Import(AspectJAutoProxyRegistar.class)`：

> [《Spring中expose-proxy的作用》](https://www.cnblogs.com/mzcx/p/11430846.html)：使用`AopContext.currentProxy()`代替this，以获得代理对象

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AspectJAutoProxyRegistar.class)
public @interface EnableAspectJAutoProxy {
    // 代表使用CGLIB还是JDK默认的PROXY，默认false为使用JDK-PROXY
    boolean proxyTargetClass default false;

    // 是否暴露出代理对象的引用到ThreadLocal中，默认为false不暴露
    boolean exposeProxy default false;
}
```

# **注解与bean生命周期的结合（@Import注解如何加载到bean容器中）**

在加入了开启注解后，后续@Import引入了AspectJAutoProxyRegistar，后者将会在bean的生命周期中，通过`refresh()#invokeBeanFactoryPostProcess()`被加载：

```java
// invokeBeanFactoryPostProcess() -> PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors()
class PostProcessorRegistrationDelegate {

	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
        // 按照次序，处理BeanDefinitionRegistryPostProcessors

        // 先处理传进来的beanFactoryPostProcessors列表，一般为空，即不处理
        for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
            if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                BeanDefinitionRegistryPostProcessor registryPostProcessor =
                        (BeanDefinitionRegistryPostProcessor) postProcessor;
                registryPostProcessor.postProcessBeanDefinitionRegistry(registry);
                // ...
            }
            else {
                // ...
            }
        }

        // ...

        // 进而处理ConfigurationClassPostProcessor类
        // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
        boolean reiterate = true;
        while (reiterate) {
            reiterate = false;
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                if (!processedBeans.contains(ppName)) {
                    // ConfigurationClassPostProcessor在这里通过getBean从定义中被加载到容器中
                    BeanDefinitionRegistryPostProcessor pp = beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class);
                    registryPostProcessors.add(pp);
                    processedBeans.add(ppName);
                    // 这里被处理到
                    pp.postProcessBeanDefinitionRegistry(registry);
                    reiterate = true;
                }
            }
        }

        // 处理beanFactoryPostProcessors
    }
```

ConfigurationClassPostProcessor被getBean加载后，调用postProcessBeanDefinitionRegistry()方法，该方法会进而转到该类的`processConfigBeanDefinitions(BeanDefinitionRegistry registry)`接口：

```java
public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
    // ...

    this.reader.loadBeanDefinitions(configClasses);
}
```

在最后调用`loadBeanDefinitionsFromRegistrars()`方法，读取注解中引入的类：

```java
public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
		TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
		for (ConfigurationClass configClass : configurationModel) {
			loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
		}
	}

private void loadBeanDefinitionsForConfigurationClass(ConfigurationClass configClass,
        TrackedConditionEvaluator trackedConditionEvaluator) {

    // ...

    // 在这里读取@Import注解，并调用
    loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
}

private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
    // 转而调用AspectJAutoProxyRegistrar.registerBeanDefinitions()
    registrars.forEach((registrar, metadata) ->
            registrar.registerBeanDefinitions(metadata, this.registry));
}
```

加载后并调用其接口方法`registerBeanDefinitions()`：

```java
class AspectJAutoProxyRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 最重要的一段代码，他会触发后续的内容
        AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry);

        // 设置proxy-target-class属性和expose-proxy属性
    }
}
```

经过@Import的加载后（BeanDefinitionRegistryPostProcessor调用阶段），beanFactory将`AspectJAwareAdvisorAutoProxyCreator`的bean定义加载到工厂中，后续BBP在`refresh()#registerBeanPostProcessors()`进行实例/初始化：

```java
@Nullable
public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, @Nullable Object source) {
    // 传入AspectJAwareAdvisorAutoProxyCreator进行beanDefnition加载
    return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
}

@Nullable
private static BeanDefinition registerOrEscalateApcAsRequired(Class<?> cls, BeanDefinitionRegistry registry,
        @Nullable Object source) {
    // ...

    RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
    beanDefinition.setSource(source);
    beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
    beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    // 将AspectJAwareAdvisorAutoProxyCreator的bean定义加载到工厂中
    registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
    return beanDefinition;
}
```

# **AspectJAwareAdvisorAutoProxyCreator**

接下来，加载业务bean时（`refresh()#finishBeanFactoryInitialization(beanFactory)`），将通过该BBP对AOP信息进行**读取**，并**生成增强对象**：

- 通过`AbstractAdvisorAutoProxyCreator`实现读取

- 通过`AbstractAutopProxyCreator`和BBP接口实现生成

![AnnotationAwareAspectJAutoProxyCreator](https://asea-cch.life/upload/2021/09/AnnotationAwareAspectJAutoProxyCreator-32359a3936fc4d18a95e12fd2e487fd9.png)

当容器开始加载业务bean时，每个bean的生命周期中，有4个阶段会被该AOP的beanPostProcessor触发：
- 读取AOP信息：**实例化**bean之前
- 获得增强对象
    - **初始化**bean之后
    - 如果存在循环依赖，则在**初始化之后**，通过注入对象调用`getEarlyBeanReference(Object, String)`提前获得

## **读取AOP信息**

时机：读取AOP信息发生在`实例化bean之前`

```java
public abstract class AbstractAutoProxyCreator {
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
        Object cacheKey = getCacheKey(beanClass, beanName);

        // this.targetSourcedBeans：代理缓存？
		if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
			// this.advisedBeans代表无需代理的bean
            if (this.advisedBeans.containsKey(cacheKey)) {
				return null;
			}
            
            // 是基础AOP类（带有@Aspect），或是切面bean
            // 主要通过这个方法加载所有的AOP信息
			if (isInfrastructureClass(beanClass) || shouldSkip(beanClass, beanName)) {
				this.advisedBeans.put(cacheKey, Boolean.FALSE);
				return null;
			}
		}

		TargetSource targetSource = getCustomTargetSource(beanClass, beanName);
		if (targetSource != null) {
			if (StringUtils.hasLength(beanName)) {
				this.targetSourcedBeans.add(beanName);
			}
			Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
			Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
			this.proxyTypes.put(cacheKey, proxy.getClass());
			return proxy;
		}

		return null;
    }
}
```

- isInfrastructureClass(Class<?> beanClass)

    - AnnotationAwareAspectJAutoProxyCreator的实现：

    > 调用父类的判断，或者如果类上面有@Aspect注解，就认为是基础建设类，返回true

    ```java
    public class AnnotationAwareAspectJAutoProxyCreator extends AspectJAwareAdvisorAutoProxyCreator {
        @Override
        protected boolean isInfrastructureClass(Class<?> beanClass) {
            // this.aspectJAdvisorFactory.isAspect会判断beanClass是否有@Aspect注解
            return (super.isInfrastructureClass(beanClass) ||
                    (this.aspectJAdvisorFactory != null && this.aspectJAdvisorFactory.isAspect(beanClass)));
        }
    }
    ```

    - super的实现（AbstractAutoProxyCreator）：

    ```java
    public class AbstractAutoProxyCreator {
        protected boolean isInfrastructureClass(Class<?> beanClass) {
            // 判断传入的beanClass是否是Advice/PointCut/Advisor/AopInfrastructureBean的类或子类
            boolean retVal = Advice.class.isAssignableFrom(beanClass) ||
                    Pointcut.class.isAssignableFrom(beanClass) ||
                    Advisor.class.isAssignableFrom(beanClass) ||
                    AopInfrastructureBean.class.isAssignableFrom(beanClass);
            if (retVal && logger.isTraceEnabled()) {
                logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.getName() + "]");
            }
            return retVal;
        }
    }
    ```

- shouldSkip(Class<?> beanClass, String beanName)

    ```java
    // AspectAwareAdvisorAutoProxyCreator.java
    protected boolean shouldSkip(Class<?> beannCLass, String beanName) {
        // 获取所有候选的Advisor
        List<Advisor> candidateAdvisors = findCandidateAdvisors();
        for (Advisor advisor : candidateAdvisors) {
            // 如果beanName为AspectJPointcutAdvisor，并且该advisor的切面与beanName相同，直接返回true？
            if (advisor instanceof AspectJPointcutAdvisor) {
                if (((AbstractAspectJAdvice) advisor.getAdvice()).getAspectName().equals(beanName)) {
                    return true;
                }
            }
        }
        return super.shouldSkip(beanClass, beanName);a
    }
    ```

    - AbstractAdvisorAutoProxyCreator.findCandidateAdvisors()

        > 看不懂，为啥只缓存advisor的name集合，然后又要重new一个List去一个个加进去，直接缓存list不好吗？

        ```java
        protected List<Advisor> findCandidateAdvisors() {
            return this.advisorRetrievalHelper.findAdvisorBeans();
        }

        // BeanFactroyAdvisorRetrievalHelper.class
        // 在当前bean工厂中，查找所有符合条件的Advisor bean，忽略FactoryBeans并排除当前正在创建的bean
        public List<Advisor> findAdvisorBeans() {
            String[] advisorNames = null;
            synchronized (this) {
                advisorNames = this.cachedAdvisorBeanNames;
                if (advisorNames == null) {
                    // 该方法会读取工厂中，所有类型为Advisor的bean
                    advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, Advisor.class, true, false);
                    // 设置缓存（线程安全），容易加快下次的判断
                    this.cachedAdvisorBeanNames = advisorNames;
                } 
            }

            // ...

            List<Advisor> advisors = new LinkedList<>();
            // 遍历工厂中所有类型为Advisor的bean
            for (String name : advisorNames) {
                // 默认返回true，没有重写
                if (isEligibleBean(name)) {
                    try {
                        advisors.add(this.beanFactory.getBean(name, Advisor.class));
                    } catch (...) {

                    }
                }
            }
            return advisors;
        }
        ```

    - AnniotationAwareAspectJAutoProxyCreator.findCandidateAdvisors()

        ```java
        protected List<Advisor> findCandidateAdvisors() {
            // 返回了bean工厂中，全部类型为Advisor的bean
            List<Advisor> advisors = super.findCandidateAdvisors();
            if (this.aspectJAdvisorsBuilder != null) {
                // 在bean工厂中，查找AspectJ注释的bean，并返回代表它们的增强对象，为每个AspectJ advice method建立一个Spring Advisor
                advisors.addAll(this.aspectJAdvisorsBuilder.buildAspectJAdvisors());
            }
            return advisors;
        }
        ```

    - `BeanFactoryAspectJAdvisorBuilder.buildAspectJAdvisors()`：build出来的Advisor属于`PointcutAdvisor`类型的，这个后续在判断是否对bean进行增强时会出现

        > 一样的操作，缓存aspect的名字到集合中，advisors的缓存会放入

        ```java
        public List<Advisor> buildAspectJAdvisors() {
            List<String> aspectNames = this.aspectBeanNames;

            if (aspectNames == null) {
                synchronized (this) {
                    aspectNames = this.aspectBeanNames;
                    if (aspectNames == null) {
                        List<Advisor> advisors = new LinkedList<>();
                        aspectNames = new LinkedList<>();
                        // 取出所有的bean的名字
                        String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, Object.class, true, false);
                        for (String beanName : beanNames) {
                            if (!isEligibleBean(beanName)) {
                                continue;
                            }

                            Class<?> beanType = this.beanFactory.getType(beanName);
                            if (beanType == null) {
                                continue;
                            }

                            // 关键判断，判断该bean是否是一个切面bean，即该类是否有@Aspect注解
                            if (this.advisorFactory.isAspect(beanType)) {
                                aspectNames.add(beanName);
                                AspectMetadata amd = new AspectMetadata(beanType, beanName);
                                if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
                                    // 为该bean创建一个切面工厂
                                    MetadataAwareAspectInstanceFactory factory = new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);

                                    // 通过切面实例工厂中，将@Before、@After、@Around等注解对应的方法进行包装，每一个注解都包含了切面的表达式，返回的每一个Advisor都对应一个method
                                    List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);

                                    if (this.beanFactory.isSingleton(beanName)) {
                                        this.advisorsCache.put(beanName, classAdvisors);
                                    }
                                }
                            }
                        }
                        this.aspectBeanNames = aspectNames;
                        return advisors;
                    }
                }
            }
        }
        ```

        注意一下`this.advisorFactory.getAdvisors(beanFactory)`调用链的最后一层，它生成了`AspectJExpressionPointcut`类型的表达式，并传入`PointcutAdvisor`类型的Advisor后返回

        ```java
        public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrderInAspect, String aspectName) {
            // 注意，生成的表达式对象是AspectJExpressionPointcut
            AspectJExpressionPointcut expressionPointcut = getPointcut(
				candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());
            if (expressionPointcut == null) {
                return null;
            }
            // InstantiationModelAwarePointcutAdvisorImpl implements PointcutAdvisor
            return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod,
				this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
        }
        ```

## **生成增强对象**

时机：初始化bean后，或循环依赖时提前获得引用

```java
public abstract class AbstractAutoProxyCreator {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeanException {
        if (bean != null) {
            Object cacheKey = getCacheKey(bean.getClass(), beanName);
            // 如果没有循环依赖的话，则需要进行包装
            if (!this.earlyProxyReferences.contains(cacheKey)) {
                return wrapIfNecessary(bean, beanName, cacheKey);
            }
        }
        // 循环依赖下，已经在前期getEarlyBeanReference(Object, String)方法中进行包装
        return bean;
    }
}
```

关键方法：AbstractAutoProxyCreator.`wrapIfNecessary(Object bean, String beanName, Object cacheKey)`

>  重点：this.advisedBeans包括了无法匹配到切面的bean，如果bean为原型的话，当第一次判断不匹配后，后续都将直接返回

```java
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    // 已经被处理过，无需再处理，targetSourcedBeans代表被处理过的bean
    if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
        return bean;
    }
    // 如果bean是切面bean，无需处理
    // this.advisedBeans包括了无法匹配到切面的bean，如果bean为原型的话，当第一次判断不匹配后，后续都将直接返回
    if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
        return bean;
    }
    // 查看类是否是基础bean，或者属于切面bean
    if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass())) {
        this.advisedBeans.put(cacheKey, Boolean.FALSE);
        return bean;
    }

    // 获取该bean的增强
    Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
    // DO_NOT_PROXY == null
    // 如果获取到了增强，则针对增强创建代理
    if (specificInterceptors != DO_NOT_PROXY) {
        this.advisedBeans.put(cacheKey, Boolean.TRUE);
        Object proxy = createProxy(bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    }

    // 
    this.advisedBeans.put(cacheKey, Boolean.FALSE);
    return bean;
}
```

- 匹配增强对象：getAdviceAndAdvisorsForBean(Class<?> beanClass, String beanName, TargetSource customTargetSource)：获取bean的所有增强

    > 用于返回Advice（增强对象），这些advisors通过对有@Aspect的类加载而获得，最重要的的是：根据是否有返回Advice来判断该bean是否需要被代理

    ```java
    // AbstractAdvisorAutoProxyCreator.java
    @Override
    @Nullable
    protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
        List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
        if (advisors.isEmpty()) {
            return DO_NOT_PROXY;
        }
        // 为啥又内存复制了???
        return advisors.toArray();
    }

    protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
        // 获取工厂中的所有增强
        List<Advisor> candidateAdvisors = findCandidateAdvisors();
        // 验证beanClass是否该被代理，如果应该，则返回该bean的增强
        List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
        extendAdvisors(eligibleAdvisors);
        if (!eligibleAdvisors.isEmpty()) {
            eligibleAdvisors = sortAdvisors(eligibleAdvisors);
        }
        return eligibleAdvisors;
    }

    protected List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {
        // 将当前beanName设置到线程变量currentProxiedBeanName中
        ProxyCreationContext.setCurrentProxiedBeanName(beanName);
        try {
            // 查找当前bean是否匹配某些增强，有的话则返回该增强对象，供上一层流程createProxy
            return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
        }
        finally {
            // 清空线程变量
            ProxyCreationContext.setCurrentProxiedBeanName(null);
        }
    }
    ```

    findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz)：查找对于给定的class的增强对象列表

    ```java
    // AopUtils.class
    // 传入的candidateAdvisors：上层调用findCandidateAdvisors()传入的，即全部的advisor
    public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
        if (candidateAdvisors.isEmpty()) {
            return candidateAdvisors;
        }
        List<Advisor> eligibleAdvisors = new LinkedList<>();
        for (Advisor candidate : candidateAdvisors) {
            if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
                eligibleAdvisors.add(candidate);
            }
        }
        boolean hasIntroductions = !eligibleAdvisors.isEmpty();
        // 这里处理非IntroductionAdvisor类型的Advice，即注解注册进来的哪些
        for (Advisor candidate : candidateAdvisors) {
            if (candidate instanceof IntroductionAdvisor) {
                // 在上面已经处理了
                continue;
            }
            if (canApply(candidate, clazz, hasIntroductions)) {
                eligibleAdvisors.add(candidate);
            }
        }
        return eligibleAdvisors;
    }

    public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
        if (advisor instanceof IntroductionAdvisor) {
            return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
        }
        else if (advisor instanceof PointcutAdvisor) {
            PointcutAdvisor pca = (PointcutAdvisor) advisor;
            // 详情可以看AOP的表达式用法
            return canApply(pca.getPointcut(), targetClass, hasIntroductions);
        } else {
            // 因为没有切入点，所以默认都适用
            return true;
        }
    }
    ```

- `创建代理`：createProxy(Class<?> beanClass, String beanName, Object[] specificInterceptors, TargetSource targetSource)

    ```java
    // AbstractAutoProxyCreator.class
    protected Object createProxy(Class<?> beanClass, @Nullable String beanName, @Nullable Object[] specificInterceptors, TargetSource targetSource) {
        if (this.beanFactory instanceof ConfigurableListableBeanFactory) {

            AutoProxyUtils.exposeTargetClass((COnfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
        }

        ProxyFactory proxyFactory = new ProxyFactory();
        // 读取AOP的配置到工厂中，包括expose-proxy参数和proxy-target-class参数
        proxyFactory.copyFrom(this);

        // 判断当前bean是使用proxyTargetClass代理，还是接口代理
        if (!proxyFactory.isProxytargetClass()) {
            // 检查reserve-target-class属性，为true说明使用cglib
            if (shouldProxyTargetClass(beanClass, beanName)) {
                proxyFactory.setProxyTargetClass(true);
            } else {
                // 判断bean是否有合适的接口可以使用JDK动态代理，有则将合适的接口添加到proxyFactory中，否则设置proxy-target-class为true
                evaluateProxyInterfaces(beanClass, proxyFactory);
            }
        }

        // 将符合该bean的增强，转化为Advisor类型（Advice会通过适配器转化为Advisor类型）
        Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
        proxyFactory.addAdvisors(advisors);
        // 将上层的代理引用设置到工厂中
        proxyFactory.setTargetSource(targetSource);
        customizeProxyFactory(proxyFactory);

        proxyFactory.setFrozen(this.freezeProxy);
		if (advisorsPreFiltered()) {
			proxyFactory.setPreFiltered(true);
		}

        // 使用代理工厂获取代理对象
		return proxyFactory.getProxy(getProxyClassLoader());
    }

    protected void evaluateProxyInterfaces(Class<?> beanClass, ProxyFactory proxyFactory) {
		Class<?>[] targetInterfaces = ClassUtils.getAllInterfacesForClass(beanClass, getProxyClassLoader());
        // 判断是否存在会导致不能代理的接口，false表示有问题，true表示有可用的顶层接口
		boolean hasReasonableProxyInterface = false;
		for (Class<?> ifc : targetInterfaces) {
            // !isConfigurationCallbackInterface：不能存在配置类型的接口
            // !isInternalLanguageInterface：不能存在里面认为的接口
			if (!isConfigurationCallbackInterface(ifc) && !isInternalLanguageInterface(ifc) &&
					ifc.getMethods().length > 0) {
				hasReasonableProxyInterface = true;
				break;
			}
		}
		if (hasReasonableProxyInterface) {
            // 将顶层接口都加入到proxyFactory中
			for (Class<?> ifc : targetInterfaces) {
				proxyFactory.addInterface(ifc);
			}
		}
		else {
			proxyFactory.setProxyTargetClass(true);
		}
	}
    ```

    接下来到最后的`ProxyFactory`父类和`DefaultAopProxyFactory`子类：

    ```java
    // ProxyFactory.class
    public Object getProxy(@Nullable ClassLoader classLoader) {
        return createAopProxy().getProxy(classLoader);
    }

    protected final synchronized AopProxy createAopProxy() {
        if (!this.active) {
            activate();
        }
        return getAopProxyFactory().creatAopProxy(this);
    }
    ```

    ```java
    // DefaultAopProxyFactory.class
    public AopProxy createAopProxy(AdvisedSupport config) throws AppConfigException {
        // 注意，此处的config.isProxyTargetClass()在上面的逻辑会进行合适性修改，可能不是配置的值了
        if (config.isOptmize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
            Class<?> targetClass = config.getTargetClass();
            if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
                // 即使设置了proxy-target-class为true，但如果targetClass是一个接口，仍然会使用JDK动态代理
                return new JdkDynamicAopProxy(config);
            }
            return new ObjenesisCglibAopProxy(config);
        } else {
            return new JdkDynamicAopProxy(config);
        }
    }
    ```

# **调用代理对象**

在以上的一波操作后，现在工厂的bean缓存里已经不是bean本身了，而是bean的增强对象

![jdk-proxy和aop](https://asea-cch.life/upload/2021/09/jdk-proxy%E5%92%8Caop-8ec4650d874d451ab0d596ec88200af2.jpg)

> [《java-jdk动态代理生成的代理类源码》](https://www.cnblogs.com/dengrong/p/8533242.html)

```java
package com.lvwan;

public interface Service {
    void helo();
}

public class XXXService {
    // 获取的是proxy增强对象
    @Autowired
    private Service proxy;

    public void helo() {
        // proxy的helo()方法如下
        proxy.helo();
    }
}

// 动态反编译生成的代理class获得
public class Proxy动态代理类 extends Proxy implements XXXService {
    Method m1, m2, m3, m4;

    static {
        m0 = Class.forName("java.lang.Object").getMethod("hashCode", ...);
        m1 = Class.forName("java.lang.Object").getMethod("equals", new Class[] { Class.forName("java.lang.Object") });
        m2 = Class.forName("java.lang.Object").getMethod("toString", ...);
        // 加载顶层接口的方法
        m3 = Class.forName("com.lvwan.Service").getMethod("helo", ...);
    }

    public Proxy动态代理类(InvocationHandler invocationHandler) {
        // 将JdkDymaticAopProxy实例组合到代理类中
        super(invocationHandler);
    }

    @Override
    public void helo() {
        // 调用JdkDymaticAopProxy实例的invoke方法, 因为helo()方法无餐，所以第三个参数为null
        // 如果helo方法有参数的话，会将其包装成Object[]传入到jdkDymaticAopProxy的invoke方法中
        try {
            return (this.h.invoke(this, m3, null));
        } catch () {
            ...
        }
    }
}
```

JdkDymaticAopProxy，重点注意`@Before`、`@After`、`@Around`是如何在里面体现的：

```java
// JdkDynamicAopProxy.java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // ...

    // 获取对于该方法的chain，链的各个处理器先后顺序，由Advice和Advisor根据注解的时机进行设置
    List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

    if (chain.isEmpty()) {
        // 没有切面，直接调用目标
        Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
        retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
    }
    else {
        // proxy：传入的Proxy动态代理类
        // target：被代理的对象
        // method：被代理的方法
        // args：调用代理对象方法时，传入的参数
        // targetClass：被代理的对象类
        // chain：切面链
        invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
        retVal = invocation.proceed();
    }

    // ...
}
```

`proceed()`方法使用递归调用，通过维护一个链的执行下标，根据职责链的每一个环节进行调用

```java
// ReflectiveMethodInvocation.class
public Object proceed() throws Throwable {
    // 递归的基础条件
    if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
        // 真正调用target的方法
        return invokeJoinpoint();
    }

    //获取下一个要执行的拦截器
    Object interceptorOrInterceptionAdvice =
            this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
    
    if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher) {
        // Evaluate dynamic method matcher here: static part will already have
        // been evaluated and found to match.
        InterceptorAndDynamicMethodMatcher dm =
                (InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
        if (dm.methodMatcher.matches(this.method, this.targetClass, this.arguments)) {
            // 符合该环，调用其invoke方法，方法内部会再递归调用proceed()方法
            return dm.interceptor.invoke(this);
        }
        else {
            // 不匹配该切面，往下一环走
            return proceed();
        }
    }
    else {
        // It's an interceptor, so we just invoke it: The pointcut will have
        // been evaluated statically before this object was constructed.
        return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
    }
}
```

# 参考
- [基于注解的SpringAOP源码解析（二）](https://mp.weixin.qq.com/s?__biz=MzU5MDgzOTYzMw==&mid=2247484595&idx=1&sn=6395c62a309422bd25d039e8bde505bd&chksm=fe396e8dc94ee79b494e0d05434a856e91e03d42b7d64a383e007cc563c277a8f0924997661c&scene=178&cur_album_id=1344425436323037184#rd)

- [基于注解的SpringAOP源码解析（三）](https://mp.weixin.qq.com/s?__biz=MzU5MDgzOTYzMw==&mid=2247484599&idx=1&sn=312ab31f9449281cf77c3abb7337c9b1&chksm=fe396e89c94ee79f3d6d27bef7d17e496d57c99fb3ef96d7bfeb0a0fea74592a703b63ac2d56&scene=178&cur_album_id=1344425436323037184#rd)

- [isInfrastructureClass()](https://blog.csdn.net/yinbucheng/article/details/81079841)

- [Java中的代理模式](https://mp.weixin.qq.com/s/1DRmvuky5_NMRcH-toTLqQs)

# 重点参考
- [详解Spring中Bean的this调用导致AOP失效的原因](https://my.oschina.net/guangshan/blog/1807721)
- [Spring @Configuration 和 @Component 区别](https://blog.csdn.net/isea533/article/details/78072133)