# Mybatis：springboot整合全过程

```xml
<bean id="dataSource" class="com.alibaba.druid.pool.DruidDataSource">
    <property name="url" value="${jdbc.url}" />
    <property name="driverClassName" value="${jdbc.driver}" />
    <property name="username" value="${jdbc.username}" />
    <property name="password" value="${jdbc.password}" />
</bean>

<!-- FactoryBean，这种类型的bean暴露后，用户获取时会得到工厂产出的bean，而是用于产出其他的bean -->
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource" />
    <property name="configLocation" value="classpath:mybatis-config.xml" />
</bean>

<!-- scope="prototype" 另说,另讨论，我们先以mapper形式看一下 -->
<bean id="sqlSession" class="org.mybatis.spring.SqlSessionTemplate">
    <constructor-arg index="0" ref="sqlSessionFactory" />
</bean>

<!-- 事务 -->
<bean name="transactionManager"
        class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
    <property name="dataSource" ref="dataSource"></property>
</bean>
```

传统Spring项目需要通过xml配置文件或注解配置类来配置Mybatis的基础组件

而在Springboot整合中得益于**自动装配**，我们无需再关注以上的基础配置信息，这些操作都交由`MybatisAutoConfiguration`类根据`spring-autoconfigure-metadata.properties`文件进行操作

# 1. 主程序Main方法入口运行

```java
@ComponentScan("xxx.*")
@MapperScan("xxx.mapper")
@ImportResource(locations = "....")
@SpringBootApplication
public class XXXXAplication {
    public static void main(String[] args) {
        SpringAppliction.run(XXXXAplication.class, args)
    }
}
```

注解含义：

- **@SpringApplication**

    包含@Configuration，入口类将会被当做配置类处理

- @ComponnetScan()

    由`ConfigurationClassPostProcessor`进行处理，会解析该包下的所有bean定义，注册到工厂中

- @MapperScan()

    引入`MapperScannerRegistrar.class`，会解析value对应包路径下的所有接口类，并将它们全部视为mapper以注册它们的bean定义到工厂中

- @ImportResource()


以上操作的前提，都需要拥有一个`ApplicationContext`，需要某些重要的BeanFactoryPostProcessor（诸如ConfigurationClassPostProcessor），并且调用context的`refresh()`方法将这些特性配置加载到容器中，这个过程由`SpringApplication.run()`完成：

> 注意在Spring应用类传入到以下方法的primarySource，SpringBoot的起步装配以该类作为出发点，来进行大规模的展开

```java
public class SpringApplication {
    // 应用启动类调用入口
    public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
        return run(new Class<?>[] {primarySource}, args);
    }

    // 第二层调用栈，初始化了一个SpringApplication类，调用启动方法
    public static ConfigurableApplicationContext run(Class<?>[] primarySource, String[] args) {
        return new SpringApplication(primarySource).run(args);
    }

    // 前方高能，如果应用类型是SERVLET，包括：
    // 1. 创建一个AnnotationConfigServletWebApplicationContext容器，构造内部reader类时会读取6个关键BeanFactoryPostProcessor，其中就包括了ConfigurationClassPostProcessor
    // 2. 容器对象预处理，读取传入的PrimaryClass的bean定义，并注册到工厂中
    // 3. 容器refresh()方法调用，容器实例化和初始化所有的bean
    public ConfigurableApplicationContext run(String... args) {
        // 忽略监听器的注册代码

        try {
            ApplicationArguments applicationArguments = new DefaultApplicaitonArguments(args);
            ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);
            configureIgnoreBeanInfo(environment);
            Banner printedBanner = printBanner(environment);
            // 第一步
            context = createApplicationContext();
            exceptionReporters = getSpringFactoriesInstances(SpringBootExceptionReporter.class, new Class[] { ConfigurableApplicationContext.class }, context);
            // 第二步
			prepareContext(context, environment, listeners, applicationArguments, printedBanner);
            // 第三步
            refreshContext(context);
            // ...
        } catch (Throwable ex) {
            // ...
        }
        // ...
    }
}
```

# **2. ConfigureClassPostProcessor处理配置**

```java
public abstract AbstractApplicationContext extends DefaultResourceLoader implements ConfigurableApplicationContext {
    @Override
    public void refresh() throws BeansException, IllegalStateException {
        synchronized (this.startupShutdownMonitor) {
			prepareRefresh();

			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

            // 加载：一些重要bbp，一些重要的bean单例
            // bbp：ApplicationContextAwareProcessor、ApplicationListenerDetector
            // 重要单例bean：spring环境（environment）的bean、系统变量配置（systemProperties）的bean、系统环境（systemEnvironment）的bean等
			prepareBeanFactory(beanFactory);

			try {
				postProcessBeanFactory(beanFactory);

                // 噩梦开始，无限套娃从这里开始，ConfigureClassPostProcessor作为主犯
				invokeBeanFactoryPostProcessors(beanFactory);

                // ...
        }
    }
}
```

调用类型为`BeanDefinitionRegistryPostProcessor`的`postProcessBeanDefinitionRegistry(registry)`方法

> ConfigurationClassPostProcessor属于PriorityOrdered类型，晚于应用上下文的bfbp，但早于其他bfbp调用

前方调用栈：AbstractApplicationContext # refresh() # invokeBeanFactoryPostProcessors() -> PostProcessorRegistrationDelegate # invokeBeanFactoryPostProcessors() -> ConfigurationClassPostProcessor # postProcessbeanDefinitionRegistry()

```java
// ConfigurationClassPostProcessor.class
public class ConfigurationClassPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered, ... {

    @Override
    public void postProcessbeanDefinitionRegistry(BeanDefinition registry) {
        // ...

        processConfigBeanDefinitions(registry);
    }


    public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
        List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
		String[] candidateNames = registry.getBeanDefinitionNames();

        // 遍历获取的8个组件名称，其中只有用户的启动类XXXApplication会被添加到configCandidates列表中
		for (String beanName : candidateNames) {
			BeanDefinition beanDef = registry.getBeanDefinition(beanName);
            // 判断是否为Configuration配置类，检查@Configuration注解
			if (ConfigurationClassUtils.isFullConfigurationClass(beanDef) ||
					ConfigurationClassUtils.isLiteConfigurationClass(beanDef)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Bean definition has already been processed as a configuration class: " + beanDef);
				}
			}
			else if (ConfigurationClassUtils.checkConfigurationClassCandidate(beanDef, this.metadataReaderFactory)) {
				configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
			}
		}

        // Parse each @Configuration class
		ConfigurationClassParser parser = new ConfigurationClassParser(this.metadataReaderFactory, this.problemReporter, this.environment, this.resourceLoader, this.componentScanBeanNameGenerator, registry);

        do {
            // 这一层只处理入口类
            parser.parse(candidates);
            parser.validate();

            // 获取到所有的配置类，包括：
            // 1. 应用类本身
            // 2. 各个Component（视为lite configuration）
            // 3. springboot自动装配的各个配置，其中包括MybatisAutoConfiguration，后者的读取通过springboot和mybatis的整合包的properties配置
            Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
            configClasses.removeAll(alreadyParsed);

            if (this.reader == null) {
				this.reader = new ConfigurationClassBeanDefinitionReader(
						registry, this.sourceExtractor, this.resourceLoader, this.environment,
						this.importBeanNameGenerator, parser.getImportRegistry());
			}
            // 将这些configClasses在parse过程中获得的beanMethod、registrars进行处理
            // bean-method的则直接将定义注册到bean工厂里，registrars则调用其registerBeanDefinitions方法把涉及到的bean注册进去
            // 最后结果：全部mapper的bean定义，sqlSessionFactory的定义、sqlSessionTemplate的定义
			this.reader.loadBeanDefinitions(configClasses);
			alreadyParsed.addAll(configClasses);
        }

        // ...
    }
}
```

> @MapperScan引入的MapperScannerRegistrar在入口类的配置成员中，在`this.reader.loadBeanDefinitions(configClasses)`中，其registerBeanDefinitions方法被调用

## **2.1 parser对配置信息进行解析**

> 通过ClassPathMapperScanner进行扫描（doScan方法），读取出全部的Mapper组件，而mapper对应的xml配置在随后的bean初始化/实例化过程中，通过sqlSessionFactory的bean进行解析（getObject() -> buildSqlSessionFactory())，后续过程在BBP的注册中触发

`parser.parse(candidates)`的处理流程：
1. 处理@Component注解
2. 处理@PropertySources注解
3. 处理@ComponentScan注解
4. 处理@Import注解
5. 处理@ImportResource注解
6. 处理@Bean注解，先把beanMethod加入到配置类元信息中，而不是直接将bean定义注册到工厂中

因为入口类没有@Component和@PropertySources配置，我们先看看如何处理入口类的@ComponentScan注解：

```java
public class ConfigurationClassParser {
    @Nullable
    @Nullable
	protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
        // 处理@Component

        // 处理@PropertySources

        // 处理@ComponentScan，一般用户需要配置整个项目的包路径，以读取全部的组件
        Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
        if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			for (AnnotationAttributes componentScan : componentScans) {
                // 调用componentScanParser进行扫描，解析代码见下
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
                
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition();
					}
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
                        // 扫描到的bean定义如果是配置类型的，则进行解析方法递归
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

        // getImports方法会递归查找入口类的全部@Import注解类，正常情况下会读取到：AutoConfigurationPackages$Registrar、AutoConfigurationImportSelector
        // 在配置了Mybatis的@MapperScan下，还会读取到：MapperScannerRegistrar，这个注册器会将路径下的所有接口类解析为bean定义，并注册到工厂中
        processImports(configClass, sourceClass, getImports(sourceClass), true);
    }
}
```

`ComponentScanAnnotationParser`的解析过程，该parser组合在ConfigurationClassParser中，最后通过`ClassPathBeanDefinitionScanner`进行扫描：

```java
public class ComponentScanAnnotationParser {
    public Set<BeanDefinitionHolder> parse(AnnotationAttributes componentScan, final String declaringClass) {
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(this.registry, componentScan.getBoolean("useDefaultFilters"), this.environment, this.resourceLoader);
        // ....

        // 只扫描ComponentScan的value值路径，如果项目有整合mybatis，在这里暂时还未解析到mapper的bean定义
        return scanner.doScan(StringUtils.toStringArray(basePackages));
    }
}
```

## **2.2 加载由parser解析出来的配置类**

具体可查看@Import、@Configuration的源码接信息

# **3. 注入mapper、sqlSessionTemplate的时机**

关于注入mapper、sqlSessionTemplate和SqlSessionFactory：

> 如果使用shiro等类似自带bbp的框架，若框架涉及到db操作，与db操作相关mapper的bean将被refresh()方法中的bbp注册环节依赖注入，从而提前初始化和实例化。如：shiro框架配置类中的`ShiroFilterFactoryBean`

mapper通过@MapperScan注册到工厂的beanDefinitionMaps结构中，并且将类型置换为`MapperFactoryBean`

```java
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {
    private Class<T> mapperInterface;

    private boolean addToConfig = true;

    public MapperFactoryBean() {

    }

    public MapperFactoryBean(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    /**
     * 通过Configuration对象获取mapper代理对象
     */
    @Override
    public T getObject() throws Exception {
        return getSqlSession().getMapper(this.mapperInterface);
    }

    /**
     * 单例，所以该mapper代理被创建后，会缓存到factoryBeanObjectCache中
     * 详情见：FactoryBeanRegistrySupport#getObjectFromFactorybean
     */
    @Override
    public boolean isSingleton() {
        return true;
    }
}
```

MapperFactoryBean的初始化和实例化过程使用`setter注入`的方式，其提供了`sqlSessionFactory`和`sqlSessionTemplate`两个成员对象的setter方法，所以在`populateBean()`方法注入FactoryBean对象属性时，也会初始化和实例化`sqlSessionFactory`和`sqlSessionTemplate`（注意有先后顺序）：

```java
public abstract class AbstractAutowireCapableBeanFactory {
    // ...

    protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		// ...

        // mapper在这里获得的pvs暂时只有addToConfig
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME || mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
            // 通过类型自动注入后，会获得sqlSessionFactory和sqlSessionTemplate两个bean属性
			if (mbd.getResolvedAutowireMode() == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}

        // ...

        if (pvs != null) {
            // 调用bean的setter方法，将pvs的值注入到bean中
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
    }
}
```

> 源码中会探索bean是否具备`writeObject`和`readObject`方法，一般代指的就是某个属性的getter和setter，如果都具备的情况下，会将这些属性对应的bean依赖注入到该bean中

# 参考
- [mybatis源码分析(三) mybatis-spring整合源码分析](https://www.cnblogs.com/timfruit/p/11489395.html)
- [详解 @MapperScan 注解和 @Mapper 注解](https://www.cnblogs.com/muxi0407/p/11847794.html)
- [MapperFactoryBean的创建](https://blog.csdn.net/weixin_30871701/article/details/95013149)
- [MybatisAutoConfiguration 分析](https://blog.csdn.net/zhenghuangyu/article/details/90452210)

# 重点参考
- [spring中的mybatis的sqlSession是如何做到线程隔离的？](https://www.cnblogs.com/yougewe/p/10072740.html)