# AbstractApplicationContext

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

            // 2. 创建beanFactory
            ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

            prepareBeanFactory(beanFactory);

            try {
                postProcessBeanFactory(beanFactory);

                invokeBeanFactoryPostProcessors(beanFactory);

                registerBeanPostProcessors(beanFactory);

                initMessageSource();

                initApplicationEventMulticaster();

                onRefresh();

                registerListeners();

                finishBeanFactoryInitialization(beanFactory);

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

1. prepareRefresh()

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

2. obtainFreshBeanFactory()

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
        // 2.1 重置beanFactory：如果已经有了beanFactory，则进行销毁和关闭
        if (hasBeanFactory()) {
            destroyBeans();
            closeBeanFactory();
        }
        try {
            // 2.2 创建beanFactory
            DefaultListableBeanFactory beanFactory = createBeanFactory();
            // 2.3 为beanFactory设置一个可序列化的id
            beanFactory.setSerializationId(getId());
            // 2.4 按照当前上下文（AbstractRefreshableApplicationContext）的allowBeanDefinitionOverriding和allowCircularReferences属性，为beanFactory对应的两个相同字段进行设置
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
- `GenericApplicationContext`（AnotationConfigApplicationContext）下的refreshBeanFactory
    ```java
    protected final void refreshBeanFactory() throws IllegalStateException {
        if (!this.refreshed.compareAndSet(false, true)) {
            throw new IllegalStateException("...");
        }
        this.beanFactory.setSerializationId(getId());
    }
    ```
    > 注意：注解配置Bean的上下午，在此刻并没有加载beanDefinition