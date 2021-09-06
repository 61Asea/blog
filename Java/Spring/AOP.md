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

# **AspectJAwareAdvisorAutoProxyCreator**

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

接下来，加载业务bean时（`refresh()#finishBeanFactoryInitialization(beanFactory)`），将通过该BBP对AOP信息进行**读取**，并**生成增强对象**：

- 通过`AbstractAdvisorAutoProxyCreator`实现读取

- 通过`AbstractAutopProxyCreator`和BBP接口实现生成

![AnnotationAwareAspectJAutoProxyCreator](https://asea-cch.life/upload/2021/09/AnnotationAwareAspectJAutoProxyCreator-32359a3936fc4d18a95e12fd2e487fd9.png)

## **读取AOP信息**

当容器开始加载业务bean时，每个bean的生命周期中，有4个阶段会被该AOP的beanPostProcessor触发，读取AOP信息发生在`实例化bean之前`

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
            // 是基础AOP类（带有@Aspect），或者应该跳过的类
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
    protected boolean shouldSkip(Class<?> beanName, String beanName) {
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

    - `BeanFactoryAspectJadvisorBuilder.buildAspectJAdvisors()`

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

                                    // 获取切面工厂中，从
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


关键方法在于`AspectJAwareAdvisorAutoProxyCreator`.`shouldSkip(beanClass, beanName)`方法，该方法

## **生成增强对象**

# 参考

# 重点参考
- [详解Spring中Bean的this调用导致AOP失效的原因](https://my.oschina.net/guangshan/blog/1807721)
- [Spring @Configuration 和 @Component 区别](https://blog.csdn.net/isea533/article/details/78072133)