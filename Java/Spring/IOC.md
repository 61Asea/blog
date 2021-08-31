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

## **1.1 应用上下文**

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

# **1.2 Bean工厂**

从上层的`AbstractBeanFactory`开始看起，其为subclass提供了以下重要功能：

```java
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {
    private final List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap(256);

    private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap(256));

    protected <T> T doGetBean(final String name, @Nullable final Class<T> requireedType, @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

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

        // 运用三级缓存来获取bean实例
        protected Object getSingleton(String beanName, boolean allowEarlyReference) {

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

```java
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

}
```

文章进度:
1. 继续补充AbstractAutowireCapableBeanFactory，DefaultListableBeanFactory的属性讲解

2. 将AbstractApplicationContext文章进行删减，只保留refresh()方法的内容，并着重讲解创建单例的调用链，即尾节点doCreateBean方法

3. 根据第二点的方法，来补充bean的生命周期，这部分内容放在IOC文章中

4. 循环依赖的问题和实现细节，整个流程，以及能ioc能解决的循环依赖问题总结，放在AbstractApplicationContext文章

5. AbstractApplicationContext文章改名为bean加载过程，或者直接嵌入到IOC文章中（待定）

# 参考
- [IOC和DI是什么](https://www.iteye.com/blog/jinnianshilongnian-1413846?page=2#comments)
- [如何理解Spring](https://www.zhihu.com/question/48427693/answer/723146648)