# AbstractApplicationContext.refresh()

> 本篇只做源码的笔记使用

```java
public interface ConfigurableApplicationContext extends ApplicationContext, LifeCycle, Closeable {
    // 关键方法，无论是ClassPathXmlApplicationContext，还是AnnotationConfigApplicationContext(GenericApplicationContext)，都会调用该方法对Spring进行初始化
    void refresh() throws BeanException, IllegalStateException;
}
```

```java
public abstract class AbstractApplication extends DefaultResourceLoader implements ConfigurableApplicationContext {
    public void refresh() throws BeansException, IllegalStateException {
        synchronized (this.startupShutdownMonitor) {
            // 1. 初始化spring上下文（如活跃、启动时间），并进行对必须有的参数进行非空校验
            prepareRefresh();

            // 2. 创建beanFactory，负责Bean定义的加载和注册
            ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

            // 3. 设置beanFactory的类加载器，添加几个BeanPostProcessor，手动注册几个特殊的bean
            prepareBeanFactory(beanFactory);

            try {
                // 4. 扩展点1，可以在此处新增特殊的BeanPostProcessor
                postProcessBeanFactory(beanFactory);

                // 5. 传入beanFactory，调用每一个beanFactoryPostProcessor，可能会添加一些特殊的beanPostProcessor到beanFactory中，也有可能修改beanFactory中一些bean的定义
                invokeBeanFactoryPostProcessors(beanFactory);

                // 6. 从bean定义中，取出BeanPostProcessor类型的，并对它们进行实例化（getBean），并添加到beanPostProcessors列表中
                registerBeanPostProcessors(beanFactory);

                // 7. 初始化当前ApplicationContext的`MessageSource`，这部分涉及Spring国际化功能
                initMessageSource();

                // 8. 初始化当前ApplicationContext的`事件广播器`，默认为`时间事件广播器`
                initApplicationEventMulticaster();

                // 9. 扩展点2
                onRefresh();

                // 10. 往`事件广播器`中注册`事件监听器`
                registerListeners();

                // 11. 正式开始加载其他的业务bean，涉及到bean的生命周期
                finishBeanFactoryInitialization(beanFactory);

                // 12. 
                finishRefresh();
            } catch (BeansException ex) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Exception encountered during context initialization - " + "cancelling refresh attempt: " + ex);
                }

                destroyBeans();

                cancelRefresh(ex);

                throw ex;
            } finally {
                // 
                resetCommonCaches();
            }
        }
    }
}
```

# 1. prepareRefresh()

```java
protected void prepareRefresh() {
    // 初始化上下文的启动时间
    this.startupDate = System.currentTimeMills();
    // 设置当前上下文为active状态
    this.closed.set(false);
    this.active.set(true);

    if (logger.isInfoEnabled()) {
        logger.info("Refreshing " + this);
    }

    // 为environment成员初始化一些web的信息，正常不会使用到
    // environment为ConfigurableWebEnvironment类型的
    initPropertySources();

    // AbstractEnvironment -> AbstractPropertyResolver.validateRequiredProperties
    // requiredProperties用于校验必有的属性，如果propertySources对应requiredProperties字段没有值，则抛出错误
    // TODO1：propertySources在StandardEnvironment初始化的时候会加入SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME（systemProperties，表示系统变量）、SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME（systemEnvironment，表示jvm环境上下文变量）
    // TODO2: 目前没找着spring内部调用setRequireProperties进行添加，大胆猜测是提供给外部调用的
    getEnvironment().validateRequireProperties();

    this.earlyApplicationEvents = new LinkedHashSet<>();
}
```

# 2. obtainFreshBeanFactory()

作用：负责BeanFactory的初始化，Bean的加载和注册

```java
protect ConfigurableListableBeanFactory obtainFreshBeanFactory() {
    // 创建beanFactory【DefaultListableBeanFactory】（如果已有beanFactory，则重置），并加载beanDefinition到beanFactory中
    refreshBeanFactory();
    ConfigurableListableBeanFactory beanFactory = getBeanFactory();
    if (logger.isDebugEnabled()) {
        logger.debug("Bean factory for " + getDisplayName() + ": " + beanFactory)
    }
    return beanFactory;
}
```

refreshBeanFactory方法由AbstractApplicationContext的子类实现：
- `AbstractRefreshableApplicationContext`（ClassXmlPathApplicationContext）下的refreshBeanFactory
    ```java
    protected final void refreshBeanFactory() throws BeanException {
        // 2.1 重置当前上下文的beanFactory：如果已经有了beanFactory，则进行销毁和关闭
        if (hasBeanFactory()) {
            destroyBeans();
            closeBeanFactory();
        }
        try {
            // 2.2 创建beanFactory
            DefaultListableBeanFactory beanFactory = createBeanFactory();
            // 2.3 为beanFactory设置一个可序列化的id
            beanFactory.setSerializationId(getId());
            // 2.4 按照当前上下文的allowBeanDefinitionOverriding和allowCircularReferences属性，为beanFactory对应的两个相同字段进行设置
            // 前者为“是否运行Bean覆盖”，后者为“是否允许循环引用”
            customizeBeanFactory(beanFactory);
            // 2.5 加载beanDefinitions（关键一步）
            loadBeanDefinitions(beanFactory);
            synchronized (this.beanFactoryMonitor) {
                this.beanFactory = beanFactory;
            }
        } catch (IOException ex) {
            throw new ApplicationContextException("...");
        }
    }
    ```
    > 一个应用可以存在多个beanFactory，而一个ApplicationContext只能有一个beanFactory

    PathMatchingResourcePatternResolver.findPathMatchingResources(String locationPattern)

- `GenericApplicationContext`（AnotationConfigApplicationContext）下的refreshBeanFactory
    ```java
    protected final void refreshBeanFactory() throws IllegalStateException {
        if (!this.refreshed.compareAndSet(false, true)) {
            throw new IllegalStateException("...");
        }
        this.beanFactory.setSerializationId(getId());
    }
    ```
    > 注意：注解配置Bean的上下午，在此刻并没有加载beanDefinition，而是调用方通过调用公共方法对bean进行注册

在`ClassPathXmlApplicationContext`中，任务包括：
- IO流读取xml文件的配置，将其转化为Resource类型对象
- 将resource对象转化为beanDefinitionHolder（包含beanDefinition）
- 将beanDefinition注册到beanFactory（extends BeanDefinitionRegistry）中的`beanDefinitionMap`，键为beanName，值为beanDefinition。如果该beanDefinition存在其它的别名，则一同注册

    ```java
    public class DefaultListableBeanFactory {
        @Override
        public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException {
            // ...
            BeanDefinition oldBeanDefinition;

            oldBeanDefinition = this.beanDefinitionMap.get(beanName);
            if (oldBeanDefinition != null) {
                // ...
                this.beanDefinitionMap.put(beanName, beanDefinition)
            } else {
                // 1. 并发调用registryBean，使用synchronized

                // 2. map虽然是线程安全的，但是如果没有保证其与放入list的顺序一致，会出现map已经有了注册，但是updatedDefinitionNames还未添加完毕的不一致情况

                // 3. 在修改容器时，其它线程可能在使用，而出现fast-fail，使用COW思想
                if (hasBeanCreationStarted()) {
                    // 防止在容器初始化以外的场景下，并发注册bean导致的线程问题
                    synchronized(this.beanDefinitionMap) {
                        // 将该map的put操作放到synchronized，保证与以下的操作保持同步
                        this.beanDefinitionMap.put(beanName, beanDefinition);

                        // 写时复制的思路，防止其它线程出现fail-fast
                        List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
                        updatedDefinition.addAll(this.beanDefinitionNames);
                        updatedDefinitionNames.add(beanName);
                        this.beanDefinitionNames = updateDefinitions;
                        
                        // 写时复制的思路，防止其它线程出现fail-fast
                        if (this.manualSingletonsNames.contain(beanName)) {
                            Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
                            updatedSingletons.remove(beanName);
                            this.manualSingletonNames = updatedSingletons;
                        }
                    }
                } else {
                    this.beanDefinition.put(beanName, beanDefinition);
                    this.beanDefinitionNames.add(beanName);
                    this.manualSingletonNames.remove(beanName);
                }
                this.frozenBeanDefinitionNames = null;
            }
        }
    }
    ```

# 3. prepareBeanFactory(beanFactory)

作用：设置beanFactory的类加载器，添加几个BeanPostProcessor，手动注册几个特殊的bean

```java
protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // 设置为加载当前ApplicationContext的类加载器
    beanFactory.setBeanClassLoader(getClassLoader());
    beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
    beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvirment()));

    // 相当重要的一个方法：所有实现了Aware接口的bean，在被初始化时，会被这个beanProcessor进行回调
    // 这个我们很常用，如我们会为了获取 ApplicationContext 而 implement ApplicationContextAware
   // 注意：它不仅仅回调 ApplicationContextAware，还会负责回调 EnvironmentAware、ResourceLoaderAware 等
    beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));

    // 如果bean依赖于以下几个接口实现类，在自动装配的时自动忽略它们，Spring会通过其它方式来处理这些依赖
    beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
    beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
    beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
    beanFactory.ignoreDependencyInterface(MessageSoureceAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

    // 为以下几种类型的bean赋值，如果有bean依赖了以下几个，会注入这边相应的值
    beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
    beanFactory.registerResolvableDependency(ResourceLoader.class, this);
    beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
    beanFactory.registerResolvableDependency(ApplicationContext.class, this);

    // 注册事件监听器
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

    // 如果存在bean名称为“loadTimeWeaver”的bean，则注册一个beanProcessor
    if (beanFactory.containBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));

        beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory));
    }

    // 如果没有定义 "environment" 这个 bean，那么 Spring 会 "手动" 注册一个
   if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
      beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
   }
   // 如果没有定义 "systemProperties" 这个 bean，那么 Spring 会 "手动" 注册一个
   if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
      beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
   }
   // 如果没有定义 "systemEnvironment" 这个 bean，那么 Spring 会 "手动" 注册一个
   if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
      beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
   }
}
```

# 4. postProcessBeanFactory(beanFactory)

```java
/**
 * 在应用上下文标准初始化前，修改内部工厂
 * 全部的bean definition将会被加载，但在此处还未被初始化
 * 这个方法允许我们在确切的ApplictionContext实现类中，去注册一些特别的额外
 * bean postProcessor
 */
protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
}
```

作用：不同的上下文对象可以在这一步中注册一些特殊的beanPostProcessor

# 5. invokeBeanFactoryPostProcessors()

```java
protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
    // 关键一步，调用各个beanFactoryPostProcessor的post
    PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

    // if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
    //     beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
    //     beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    // }
}
```

进入到`invokeBeanFactoryPostProcessors(beanFactory, this.beanFactoryPostProcessors)`方法后，会发现原来项目中的BeanFactoryPostProcessor基本都是在此时才被`getBean()`，即不存在则创建

注意区分：BeanDefinitionRegistryPostProcessor和BeanFactroyPostProcessor，前者作为后者的继承

> 主要处理的是类似像AOP这样的注册逻辑，在ConfigurationClassPostProcessor(@Configuration类)中会读取某些如图`@Import`的类型，调用其`registerBeanDefinitions()`接口进行加载

```java
class PostProcessorRegistrationDelegate {
    // 注意：以下方法中，BDRPP和BFPP都按照实现PriorityOrded、Ordered接口的顺序进行注册处理
	public static void invokeBeanFactoryPostProcessors() {
        // 处理BeanDefinitionRegistryPostProcessor

        // 处理BeanFactoryPostProcessor
    }
```

根据不同的`规则`，会按照`不同的顺序`来调用`invokeBeanFactoryPostProcessors(...)`方法，以实现在bean实例化前做特殊操作的功能

```java
// PostProcessorRegistrationDelegate.java
private static void invokeBeanFactoryPostProcessors(Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBenFactory beanFactory) {
    for (BeanFactoryPostProcessor postProcessor : postProcessors) {
        // 调用传入的postProcessors
        postProcessor.postProcessBeanFactory(beanFactory);
    }
}
```

作用：对beanFactory调用各个`beanFactoryPostProcessor`的`postProcessBeanFactory(beanFactory)`方法

## **BeanFactoryPostProcessor和BeanPostProcessor的区别**

BeanFactoryPostProcessor：由ApplicationContext管理，在bean**实例化**前调用执行

BeanPostProcessor：由BeanFactory管理，在bean**初始化的前与后**调用执行

# 6.** registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory)

```java
protected void registerBeanPostProcessors() {
    PostProcessorRegistrationDelegate.registerbeanPostProcessors(beanFactory, this);
}
```

```java
// PostProcessorRegistrationDelegate.java
public static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
    // ...

    registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);
}

// 将beanPostProcessor注册到beanFactory的一个list
private static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {
    for (BeanPostProcessor postProcessor : postProcessors) {
        beanFactory.addBeanPostProcessor(postProcessor);
    }
}
```

# **7.** initMessageSourece()

作用：初始化当前ApplicationContext的`MessageSource`，这部分涉及`国际化`

# **8.** initApplicationEventMulticaster()

作用：初始化当前ApplicationContext的`事件广播器`，默认为`时间事件广播器`

```java
protected void initApplicationEventMulticaster() {
    ConfigurableListableBeanFactory beanFactory = getBeanFactory();
    if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
        // 如果有用户自定义的广播器，则加载用户
        this.applicationEventMulticaster = beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);

        // ...
    } else {
        // 否则，使用默认的时间广播器
        this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);

        // ...
    }
}
```

# **9.** onRefresh()

# **10.** registerListeners()

作用：往`事件广播器`中注册`事件监听器`

```java
protect void registerListeners() {
    // ...

    // 先添加set的一些监听器
    for (ApplicationListener<?> listener : getApplicationListeners()）{
        getApplicationEventMulticaster().addApplicationListener(listener);
    }

    // ...

    // 通过配置读取一些监听器，并添加到事件广播器中
    String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
    for (String listenerBeanName : listenerBeanNames) {
        getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
    }

    // ...

    // 如果存在早期事件，则先进行发布
    Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
    this.earlyApplicationEvents = null;
    if (earlyEventsToProcess != null) {
        for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
            getApplicationEventMulticaster().multicastEvent(earlyEvent);
        }
    }
}
```

# **11.** finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory)

作用：bean的`初始化`，负责初始化所有没有设置懒加载的`singleton bean`

```java
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
    // 如果用户自行配置了conversionService的bean，则在通过简单校验后，将其设置为beanFactory的conversionService
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) && beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
            beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class);
        );
    }

    if (!beanFactory.hasEmbeddedValueResolver()) {
        beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }

    // 先初始化LoadTimeWeaverAware类型的bean
    String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
        getBean(weaverAwareName);
    }

    // 停止使用用于类型匹配的临时类加载器
    beanFactory.setTempClassLoader(null);

    // 冻结所有的bean的定义，已注册的bean定义将不会被修改或处理
    beanFactory.freezeConfiguration();

    // 重点！初始化
    beanFactory.preInstantiateSingletons();
}
```

- conversionService：bean用于将前端传过来的参数和controller方法上的参数格式转换时使用
- EmbeddedValueResolver：方便读取`配置文件`的属性
- preInstantiateSingletons()：真正的初始化方法

```java
// DefaultListableBeanFactory.java
public void preInstantiateSingletons() throws BeansException {
    // 复制之前“按顺序注册bean定义”的线程安全List
    List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

    // 对所有不懒加载的bean进行初始化单例
    for (String beanName : beanNames) {
        // 合并父bean中的配置，注意<bean id = "" class = "" parent = ""/>中的parent属性
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);

        // 不是抽象类、是单例的且不是懒加载的
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            // 处理Factory Bean
            if (isFactoryBean(beanName)) {
                // &[beanName]，在bean名字前加上“&”
                final FactoryBean<?> factory = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
                // 判断当前FactoryBean是否是SmartFactoryBean的实现
                boolean isEagerInit;
                if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                    isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> (SmartFactoryBean<?> factory).isEagerInit(), getAccessControlContext());
                } else {
                    isEagerInit = (factory instanceof SmartFactoryBean && ((SmartFactoryBean<?>) factory).isEagerInit());
                }
            } else {
                // 重要方法
                getBean(beanName);
            }
        }

        for (String beanName : beanNames) {
            // 经过上面的getBean方法后，bean都将被实例化
            Object singletonInstance = getSingleton(beanName);
            if (singletonInstance instanceof SmartInitializingSingleton) {
                final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
                if (System.getSecurityManager() != null) {
                    AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                        smartSingleton.afterSingletonsInstantiated();
                        return null;
                    }, getAccessControlContext());
                } else {
                    smartSingleton.afterSingletonsInstantiated();
                }
            }
        }
    }
}
```

> [spring bean parent属性详解](https://www.cnblogs.com/caoyc/p/5622986.html)：某些bean会作为其他bean的父类，起模板/父类的作用，子类bean会继承父类的一切bean属性，所以在代码中通过`getMergedLocalBeanDefinition()`方法为子类bean合并上父bean的属性，保证正确性

无论bean是否属于工厂，都会调用`getBean(String beanName)`方法，该方法将对bean进行`实例化`：

```java
public Object getBean(String name) throws BeansException {
    return doGetBean(name, null, null, false);
}

// 返回一个bean的instance，该实例可以是共享的，也可以是独立的
// 该方法是getOrSet
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType, @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
    // 获取beanName，这里处理两种情况：
    // 1. FactoryBean（带“&”的）
    // 2. 根据别名来获取Bean的，在这里会转换最正统的beanName
    // 如果FactoryBean的话，则把&去掉；否则，根据别名获取真实名称
    final String beanName = transformedBeanName(name);
    Object bean;

    // 检查是否已经初始化过
    Object sharedInstance = getSingleton(beanName);
    // 如果已经初始化过，且没有传入arg，则代表纯粹的get，直接取出返回即可
    if (sharedInstance != null && args == null) {
        // ...

        // 如果是普通bean，直接返回；工厂bean，返回它创建的那个对象
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, this);
    } else {
        // 如果已经存在prototype类型的该bean，则失败
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        BeanFactory parentBeanFactory = getParentBeanFactory();
        // 如果该工厂有父工厂，且当前并没有bean的beanDefition，则委托给父工厂进行实例
        if (parentBeanFactory != null && !containsBeanDefition(beanName)) {
            // ...
        }

        if (!typeCheckOnly) {
            // 将当前beanName放入一个alreadyCreated的集合中
            markBeanAsCreated(beanName);
        }

        // 从此处起，开始创建bean的实例
        try {
            final RootBeanDefinition mbd = getMergedLocalBeanDefition(beanName);
            checkMergedBeanDefition(mbd, beanName, args);

            // 先初始化依赖的所有bean，depend-on中定义的依赖
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                for (String dep : dependsOn) {
                    // 检查bean配置中，已经填写了depend-on属性的bean，它们是否有循环依赖
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName, ...);
                    }
                    // 注册依赖关系
                    registerDependentBean(dep, beanName);
                    // 先初始化依赖的bean，再往下初始化本bean
                    getBean(dep);
                }
            }

            // 如果<bean>上配置的是单例
            if (mbd.isSingleton()) {
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        // 创建bean
                        return createBean(beanName, mbd, args);
                    }
                });
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            } else if (mod.isPrototype()) {
                Object prototypeInstance = null;
                try {
                    beforePrototypeCreation(beanName);
                    // 创建bean
                    prototypeInstance = createBean(beanName, mbd, args);
                } finally {
                    afterPrototypeCreation(beanName);
                }
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            } else {
                // 如果不是singleton，也不是prototype，则是自定义的scope，例如web中的session类型，交给自定义scope的应用方法实现
                String scopeName = mbd.getScope();
                final Score scope = this.scopes.get(scopeName);
                if (scope == null) {

                }
                try {
                    Object scopedInstance = scope.get(beanName, () -> {
                        try {
                            // 创建bean
                            return createBean(beanName, mbd, args);
                        } finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                } catch (Exception ex) {
                    // ...
                }
            }
        } catch (BeansException ex) {
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }

    // 如果指定了requiredType，则检查bean的类型
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException();
            }
            return convertedBean;
        }
    }
    return (T) bean;
}
```

以上长方法有两点要注意：
- getOrSet的思想，获取bean前先判断在不在，存在的话直接返回，不在的话就创建一个bean
- Spring本身只定义了两种scope：singleton和prototype，而像springmvc的其它scope的实现也是通过这里而来

```java
protected Object createBean(String beanName, RootBeanDefition mbd) {
    RootBeanDefinition mbdToUse = mbd;

    // 用于确保beanDefinition中的Class被vm加载
    Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
    if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
        mbdToUse = new RootBeanDefinition(mbd);
        mbdToUse.setBeanClass(resolvedClass);
    }

    // 准备方法覆写，如果bean中定义了<lookup-method />和<replaced-method />
    try {
        mbdToUse.prepareMethodOverrides();
    } catch (BeanDefinitionValidationException ex) {
        throw new BeanDefinitionStoreException("....");
    }

    try {
        // 如果有代理的话，直接返回
        Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
        if (bean != null) {
            return bean;
        }
    } catch () {}

    try {
        Object beanInstance = doCreateBean(beanName, mbdToUse, args);
        return beanInstance;
    } catch () {}
}

protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args) {
    
}
```

<!-- # 进度 -->

<!-- 
8月28日进度：今天看了bean的xml解析，以及注册到map中的过程，其中包括了并发问题的；还有初步认识了beanFactoryPostProcessor的概念

后续：BeanFactoryPostProcessor接口实现类是如何被调用的，invokeBeanFactoryPostProcessors？postProcessBeanFactory方法到底是干嘛用的（已完成）
 -->

<!-- 
进度：

1. 平行方向：开始区分ApplicationContext、Environment、PropertyResolver和BeanFactory的关系（ApplictionContext包含Environment和BeanFactory，Environment组合PropertyResolver）；垂直方向：开始了解上述前三者的继承关系和接口关系

2. 持续梳理Environment和PropertyResolver的关系，目前得出简单结论：Environment和PropertyResolver的相同接口方法，具体实现都在PropertyResolver中

3. 平行方向上，加入BeanFactory模块，与ApplicationContext进行联动

4. 继续啃beanDefinition的读取过程（已完成）
 -->


<!-- 周日进度：在找寻beanPostProcessor的方法调用，doCreateBean还没看完 -->

 # 参考
 - [历时三个月，史上最详细的Spring注解驱动开发系列教程终于出炉了，给你全新震撼](https://liayun.blog.csdn.net/article/details/115053350)
 - [IOC](https://mp.weixin.qq.com/s/0zDCy0eQycdM8M9eHGuLEQ)
 - [Environment](https://www.cnblogs.com/liuenyuan1996/p/11133984.html)
 - [从BeanDefinition和PostProcessor来理解IOC和DI](https://www.zhihu.com/question/48427693/answer/723146648)

 # 重点参考
 - [为什么在注册bean的时候要synchronized](https://www.cnblogs.com/daimzh/p/12854414.html)
 - [Spring的BeanFactoryPostProcessor和BeanPostProcessor](https://blog.csdn.net/caihaijiang/article/details/35552859)
 - [方法isDependent分析-初始化bean-Spring源码](https://www.jianshu.com/p/af496b2a9f89)