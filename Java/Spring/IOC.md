# Spring：IOC容器

`IOC`：即`控制反转`，全称inversion of control
- 控制：某程序对依赖对象的控制权
- 反转：从以前的应用程序主动创建依赖对象，反转为`通过IOC容器统一创建与获取依赖的对象`

`DI`：即`依赖注入`，全称dependency injection
- 依赖：应用程序依赖于IOC容器
- 注入：IOC容器向应用程序注入某个其依赖的资源（对象、资源、常量数据）

> IOC和DI是同一个概念的不同角度描述，前者以`IOC容器控制对象`层面出发，后者以`IOC维护被注入对象的依赖对象关系`层面出发

# **1. 容器组成**

![应用上下文与bean工厂的关系](https://asea-cch.life/upload/2021/08/%E5%BA%94%E7%94%A8%E4%B8%8A%E4%B8%8B%E6%96%87%E4%B8%8Ebean%E5%B7%A5%E5%8E%82%E7%9A%84%E5%85%B3%E7%B3%BB-bd33916c406d465892bc8d242fd8d7d5.png)

## **1.1 容器（应用上下文）**

`ApplicationContext`，又称为应用上下文，作为IOC容器，其关键容器抽象类`AbstractApplicationContext`提供容器加载最重要的方法`refresh()`与以下重要组件：
- 父容器
- 环境对象
- **bean定义加载后置处理器**

```java
public abstract class AbstractApplicationContext extends DefaultResourceLoader implements ConfigurableApplicationContext {
    // 父容器
    private ApplicationContext parent;

    // 运行环境，包括系统参数和vm参数
    private ConfigurableEnvironment environment;

    // 用于容器加载bean的定义文件之后，在bean实例化之前使用，可以获取到bean的元数据并进行自定义修改操作（如：修改scope属性或property值）
    private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors;

    // ...

    public void refresh() {
        // 加载容器、加载bean定义文件、初始化单例bean、解决循环依赖问题
    }
}
```

在AbstractApplication的类关系分支下，有两大重要的实现类，它们各自都包含了默认的bean工厂`DefaultListableBeanFactory`：

- AbstractRefreshableApplicationContext：后续subclass有`ClassPathXmlApplicationContext`和`FileSystemXmlApplicationContext`，是基于xml配置的应用上下文

    ```java
    public abstract class AbstractRefreshableApplicationContext extends AbstractApplicationContext {
        // 组合bean工厂，接口实现由beanFactory提供
        private DefaultListableBeanFactory beanFactory;
    }
    ```

- GenericApplicationContext：是基于注解的应用上下文

    ```java
    public abstract class GenericApplicationContext extends AbstractApplicationContext {
        // 组合bean工厂，接口实现由beanFactory提供
        private DefaultListableBeanFactory beanFactory;

        // 资源加载器
        private ResourceLoader resourceLoader;
    }
    ```

> ApplicationContext容器面向用户，其中beanFactory面向Spring应用内部，用户正常使用下不需要调用beanFactory接口

## **1.2 Bean工厂**

### **1.2.1 AbstractBeanFactory**

从上层的`AbstractBeanFactory`开始看起，其为subclass提供了以下重要功能：

```java
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap(256);

    private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap(256));

    protected <T> T doGetBean(final String name, @Nullable final Class<T> requireedType, @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
        final String beanName = transformedBeanName(name);
        Object bean;

        Object sharedInstance = getSingleton(beanName);
        if (sharedInstance != null && args == null) {
            // ...
            bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
        } else {
            // 在这里进入更里层的创建，createBean完成后，会将完成品bean放到第一级缓存中，并从第二、三级缓存移除
            sharedInstance = getSingleton(beanName, () -> {
                try {
                    return createBean(beanName, mbd, args);
                } catch (...) {
                    destroySingleton(beanName);
                }
            });
            // 注意ObjectFactory和FactoryBean的区别，前者结合二级缓存用于解决循环依赖AOP代理对象的问题，后者是一种bean的思想
            bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
        }
    }

    protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking.
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null) {
			return mbd;
		}
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}
}
```

- `FactoryBeanRegistrySupport`：

    AbstractBeanFactory继承于`DefaultSingletonBeanRegistry`，获得Registry模块提供的三级缓存结构与获取bean单例的实现，用于解决**单例循环依赖**的问题

    ```java
    public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {
        // 第一级缓存：存放的是初始化完成的bean
        private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

        // 第三级缓存：存放的是ObjectFactory的bean，用于解决循环依赖下，依赖的最上层对象在返回AOP代理对象时，可能导致其下层对其依赖与最终最上层对象不一致问题，通过() -> getEarlyBeanRefeference()在早期先将对象替换为代理对象
        private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

        // 第二级缓存：存放的是半成品的bean，即实例化完成，但还未初始化完毕
        private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

        private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

        protected Object getSingleton(String beanName, boolean allowEarlyReference) {
            // 运用三级缓存来获取bean实例
            // 在第三级缓存获得工厂bean对象后，会调用其getObject()方法，并将生成的对象上升到第二级缓存后返回
        }
    }
    ```
- `beanPostProcessors`

    用于在bean实例化之后，**调用初始化方法的前与后**两个时机对bean进行修改，AOP就是在此刻进行

- mergedBeanDefinitions

    管理加载的BeanDefinition，这里的定义是通过自身的`getMergedBeanDefinition()`方法对parent bean定义进行合并后的结果
    > [MergedBeanDefinition()](https://blog.csdn.net/andy_zhang2007/article/details/86514320)

- alreadyCreated：在注册bean，或加载bean的定义时进行是否重复判断
- doGetBean()

    gerOrSet的思想，有的话则直接调用`DefaultSingletonBeanRegistry.getSingleton(beanName)`方法进行返回，没有的话则创建一个单例

    > 创建单例的调用链：AbstractBeanFactory.doGetBean() -> AbstractAutowireCapableBeanFactory.createBean() -> `AbstractAutowireCapableBeanFactory.doCreateBean()`

### **1.2.2 AbstractAutowireCapableBeanFactory**

`AbstractAutowireCapableBeanFactory`提供了`createBean`和`doCreateBean`方法，后者涉及到了bean的三个重要阶段：
- 实例化：createBeanInstance(beanName, mbd, args)
- 注入属性：populateBean(beanName, mbd, instanceWrapper)
- 初始化：initializeBean(beanName, exposedObject, mbd)

```java
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory implements AutowireCapableBeanFactory {
    protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) throws BeanCreationException {
        // ...
        try {
            Object beanInstance = doCreateBean(beanName, mbdToUse, args);
            if (logger.isDebugEnabled()) {
                logger.debug("Finished creating instance of bean '" + beanName + "'");
            }
            return beanInstance;
        } catch (...) {
            // ...
        }
        // ...
    }

    protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args) throws BeanCreationException {
        BeanWrapper instanceWrapper = null;
        if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
        // 1. 实例化bean
        if (instanceWrapper == null) {
            instanceWrapper = creatBeanInstance(beanName, mbd, args);
        }

        // ...

        boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences && isSingletonCurrentlyInCreation(beanName));
        if (earlySingletonExposure) {
            // 将半成品的bean提前暴露，添加到第三级缓存中，以解决循环依赖的问题
            addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
        }

        Object exposedObject = bean;
        try {
            // 2. 注入属性，这个过程可能进行循环依赖的注入
            populateBean(beanName, mbd, instanceWrapper);

            // 3. 初始化bean，这个过程可能因为BeanPostProcessor返回新引用，而替换掉旧的引用（旧引用已经存在缓存中）
            exposedObject = initializeBean(beanName, exposedObject, mbd)l
        } catch (Throwable ex) {
            // ...
        }

        if (earlySingletonExposure) {
            Object earlySingletonReference = getSingleton(beanName, false);
            if (earlySingletonReference != null) {
                // 判断是否在initializeBean中返回的引用已被替换，是的话则出现问题，因为代理对象应该提前替换，否则会导致其他被依赖注入的对象出现对象不一致情况

                // 所以，beanPostProcessor应该覆写getEarlySingletonReference()方法
                if (exposedObject == bean) {
                    // 代理对象已经在早期被替换为代理对象并存入二级缓存，这里要从二级缓存中取出
                    exposedObject = earlySingletonReference;
                } else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                    // 出现了多例循环依赖问题
                }
            }
        }

        // ...
        return exposedObject;
    }
}
```

### **1.2.3 DefaultListableBeanFactory**

`DefaultListableBeanFactory`是AbstractAutowireCapatableBeanFactory的子类，提供具体实现给到AbstractApplicationContext#Refresh#`finishBeanFactoryInitialization()`上，作为一个桥接

```java
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {
    public void preInstantiateSingletons() throws BeansException {
        // ...
        getBean(beanName);
    }
}
```

至此，创建bean的调用链变成：

AbstractApplication.refresh()#finishBeanFactoryInitialization() 
-> 
DefaultListableBeanFactory.preInstantiateSingletons()#getBean()
-> 
AbstractBeanFactory.doGetBean() 
-> 
AbstractAutowireCapableBeanFactory.createBean() 
-> 
AbstractAutowireCapableBeanFactory.doCreateBean()

## **1.3 容器生命周期**

`Lifecycle`接口和`LifeCycleProcessor`接口：

```java
public interface Lifecycle {
    void start();
    
    void stop();

    boolean isRunning();
}

public interface LifeCycleProcessor extends LifeCycle {
    // 通知上下文进行refresh操作
    void onRefresh();

    // 通知上下文关闭
    void onClose();
}
```

`LifecycleProcessor`：负责管理ApplicationContext的生命周期，包括：开启/刷新/是否运行中/关闭

### **1.3.1 生命周期管理器的初始化**

在refresh()#finishRefresh()方法中初始化，默认使用`DefaultLifecycleProcessor`，如果用户有自定义则使用用户注入的管理器bean：
```java
public abstract class AbstractApplicationContext {
    // 管理容器的生命周期，主要处理容器关闭时对bean的管理
    private LifeCycleProcessor lifeCycleProcessor;

    protected void finishRefresh() {
        // ...

        // 初始化容器生命周期管理器
        initLifecycleProcessor();

        // 优先传播refresh到lifeCycleProcessor
        getLifeCycleProcessor().onRefresh();

        // ...
    }

    protected void initLifecycleProcessor() {
        ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        // LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor"
        if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
            // 加载用户自定义lifecycleProcessor的bean实例
            this.lifecycleProcessor = beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
        } else {
            DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
            defaultProcessor.setBeanFactory(beanFactory);
            // 需要通过调用beanFactory的注册接口进行注册
            beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
        }
    }
}
```

### **1.3.2 默认生命管理器**

`DefaultLifecycleProcessor`：负责所有的LifecycleProcessor实现执行，是所有的LifeCycleProcessor的代理对象

```java
public class DefaultLifecycleProcessor implements LifecycleProcessor {
    @Override
    public void onRefresh() {
        startBeans(true);
        this.running = true;
    }

    @Override
    public void onClose() {
        stopBeans();
        this.running = false;
    }
}
```

#### **onRefresh()**

onRefresh调用的是`startBeans(boolean autoStartupOnly)`方法，参数代表是否只加载SmartLifecycle类型的管理器则：

```java
private void startBeans(boolean autoStartupOnly) {
    // 通过beanFactory获取全部类型为LifeCycle的bean
    Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
    // 相同phase的lifeProcessor处于同一组中
    Map<Integer, LifecycleGroup> phases = new HashMap<>();
    lifecycleBeans.forEach((beanName, bean) -> {
        if (!autoStartupOnly || (bean instanceof SmartLifecycle && ((SmartLifecycle) bean).isAutoStartup())) {
            // 将相同phase的放到同一个LifecycleGroup中的逻辑
        }
    });

    if (!phases.isEmpty()) {
        List<Integer> keys = new ArrayList<>(phases.keySet());
        Collections.sort(keys);
        for (Integer key : keys) {
            // 升序调用某个LifecycleGroup的全部lifeProcessor的doStart()方法
            phases.get(key).start();
        }
    }
}
```

> 到最后会发现调用的是所有的LifecycleProcessor的start()实现，这应该算是一个扩展点

#### **onClose()**

```java
private void stopBeans() {
    Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
    Map<Integer, LifecycleGroup> phases = new HashMap<>();
    lifecycleBeans.forEach((beanName, bean) -> {
        int shutdownOrder = getPhase(bean);
        LifecycleGroup group = phases.get(shutdownOrder);
        if (group == null) {
            group = new LifecycleGroup(shutdownOrder, this.timeoutPerShutdownPhase, lifecycleBeans, false);
            phases.put(shutdownOrder, group);
        }
        group.add(beanName, bean);
    });
    if (!phases.isEmpty()) {
        List<Integer> keys = new ArrayList<>(phases.keySet());
        Collections.sort(keys, Collections.reverseOrder());
        for (Integer key : keys) {
            phases.get(key).stop();
        }
    }
}
```

与onRefresh一样的套路，根据phase分组，并按照phase升序调用所有lifecycleProcessor的stop方法

### **1.3.3 调用管理方法的入口**

onRefresh：AbstractBeanFactory#refresh()#finishRefresh()

    ```java
    protected void finishRefresh {
        // ...
        getLifecycleProcessor().onRefresh();
    }
    ```

onClose：

# **2. Bean**

## **2.1 Bean的类型**

> [Spring BeanFactory和FactoryBean的区别](https://www.jianshu.com/p/05c909c9beb0)

Spring为我们提供了两种类型的bean，一种是`普通bean`，我们通过`getBean(id)`方法获得该bean的实际类型；另一种是`FactoryBean`，我们通过`getBean(id)`方法获得该工厂生产的bean，而不是该FactoryBean的实例

`FactoryBean`：通过工厂思想去产生新的object（bean），如果一个bean实现了该接口，那么它将被当作一个`对象的工厂`。FactoryBean本身是一个bean，但它不能被当作一个正常bean实例来使用，获取FactoryBean时候得到的并不是工厂本身，而是工厂产出的对象

> 通常情况下，bean无须自己实现工厂模式，Spring容器担任工厂角色；但少数情况下，容器中的bean本身就是工厂，作用是产生其它bean实例。由FactoryBean产生的其它bean实例，不再由Spring容器产生，因此与普通bean的配置不同，无须配置class属性

```java
public interface FactoryBean<T> {
    @Nullable
    T getObject() throws Exception;

    @Nullable
    Class<?> getObjectType();

    default boolean isSingleton() {
        return true;
    }
}
```

结合`doGetBean#getObjectForBeanInstance()`方法，创建出来的bean实例可能是工厂Bean，需要由其再进一步进行生产：

```java
// AbstractBeanFactory.class
protected <T> T doGetBean() {
    sharedInstance = getSingleton(beanName, () -> {
        try {
            return createBean(beanName, mbd, args);
        } catch (...) {
            destroySingleton(beanName);
        }
    });

    // 注意ObjectFactory和FactoryBean的区别，前者结合二级缓存用于解决循环依赖AOP代理对象的问题，后者是一种bean的思想
    bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
}

// FactoryBeanRegistrySupport.class
protected Object getObjectFromFactoryBean(Factory<?> factory, String beanName, boolean shouldPostProcess) {
    // ...
    object = doGetObjectFromFactoryBean(factory, beanName);
}

// FactoryBeanRegistrySupport.class
private Object doGetObjectFromFactoryBean(final FactoryBean<?> factory, final String beanName) {
    // ...

    // 调用FactoryBean的接口，由工厂bean生成最后的bean
    object = factory.getObject();
}
```

从这里可以看出一个bean在容器中的缓存是如何存储的：

1. 从一级缓存`singletonObjects`中查找，返回beanName对于的bean实例
2. 判断该bean实例是否为工厂Bean，是的话跳转到第三步，否则直接返回
3. 判断该工厂Bean是否为单例
    - 是，直接从工厂bean实例缓存-`factoryBeanObjectCache`中查找
        - 找得到，直接返回
        - 找不到，跳转到第4步
    - 否，跳转到第4步
4. 通过该工厂Bean创建bean实例（如果是单例bean则加入到`factoryBeanObjectCache`），最后返回生成的bean实例

## **2.2 Bean生命周期**

### **BeanDefinition加载**

调用栈：在`AbstractApplicationContext`#`refresh()`#`obtainBeanFactory()`方法中加载bean的定义，不同类型的上下文的加载方式不同
- AbstractRefreshableApplicationContext：对应Xml中的配置，读取全部bean标签属性来获取definition

- GenericApplication：对应Scanner，通过扫描某个包路径下的class文件，以反射的方式来获取definition

调用时机：初始化beanFactory的过程

### **BeanFactoryPostProcessor**

调用栈：AbstractApplicationContext#refresh()#`invokeBeanFactoryPostProcessors()`

调用时机：beanFactory初始化完成，即bean定义加载之后

### **getBean()**

加上最重要的三个扩展接口：
- InstantationAwareBeanPostProcessor：用于bean的实例化前后
    - postProcessBeforeInstantiation
    - postProcessAfterInstantiation
    - postProcessPropertyValues
- BeanNameAware：用于bean的init-method调用之前
- BeanFactoryAware：用于bean的init-method调用之前
- BeanPostProcessor：用于bean的初始化前后
    - postProcessBeforeInitialization
    - postProcessAfterInitialization
- DisposableBean：用于销毁

共有4个阶段（加上beanDefinition的加载算5个阶段）
- 实例化：instantiation
- 属性注入：populate
- 初始化：initialization
- 销毁：Destruction

更详细的版本可看：
> [bean生命周期](https://asea-cch.life/achrives/beanlifecycle)

<!-- 文章进度:
1. 继续补充AbstractAutowireCapableBeanFactory，DefaultListableBeanFactory的属性讲解（已完成）

2. 将AbstractApplicationContext文章进行删减，只保留refresh()方法的内容，并着重讲解创建单例的调用链，即尾节点doCreateBean方法

3. 根据第二点的方法，来补充bean的生命周期，这部分内容放在IOC文章中

4. 循环依赖的问题和实现细节，整个流程，以及能ioc能解决的循环依赖问题总结，放在AbstractApplicationContext文章

5. AbstractApplicationContext文章改名为bean加载过程，或者直接嵌入到IOC文章中（待定） -->

# 参考
- [IOC和DI是什么](https://www.iteye.com/blog/jinnianshilongnian-1413846?page=2#comments)
- [如何理解Spring](https://www.zhihu.com/question/48427693/answer/723146648)