
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

套娃1：调用类型为`BeanDefinitionRegistryPostProcessor`的`postProcessBeanDefinitionRegistry(registry)`方法

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

> 通过ClassPathMapperScanner进行扫描（doScan方法），读取出全部的Mapper组件，而mapper对应的xml配置在随后的bean初始化/实例化过程中，通过sqlSessionFactory的bean进行解析（getObject() -> buildSqlSessionFactory())，后续过程在BBP的注册中触发

parse的处理流程：
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

关于注入mapper、sqlSessionTemplate和SqlSessionFactory：

> 如果使用shiro，与用户权限相关mapper的bean将被提前初始化

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
			// Add property values based on autowire by name if applicable.
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

所以当我们在业务中注入mapper时，其实是注入一个通过JDK代理的mapper接口类，其具体的接口方法对应statement存于`configuration`对象中，拦截器方法在`MapperProxy`对象中：

```java
public class MapperProxy<T> implements InvocationHandler, Serializable {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            } else if (isDefaultMethod(method)) {
                return invokeDefaultMethod(proxy, method, args);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
        // MapperMethod将传入方法的方法名作为statementId，将具体的statement联动
        final MapperMethod mapperMethod = cachedMapperMethod(method);
        // 这里传入的sqlSession是sqlSessionTemplate
        return mapperMethod.execute(sqlSession, args);
    }
}
```

这样在后续调用mapper的接口时，都会通过mapperMethod来调用sqlSession的接口方法（如：select、selectOne、selectMap、update等方法），而该接口实现已经由`SqlSessionTemplate`替代，最终都会调用到SqlSessionTemplate的代理类`sqlSessionProxy`：

```java
public class SqlSessionTemplate implements SqlSession, DisposableBean {
    private final SqlSession sqlSessionProxy;

    public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {

        notNull(sqlSessionFactory, "Property 'sqlSessionFactory' is required");
        notNull(executorType, "Property 'executorType' is required");

        this.sqlSessionFactory = sqlSessionFactory;
        this.executorType = executorType;
        this.exceptionTranslator = exceptionTranslator;
        // 调用SqlSession接口方法时，都会调用到该代理的方法，先执行拦截器逻辑，再执行方法调用
        this.sqlSessionProxy = (SqlSession) newProxyInstance(
            SqlSessionFactory.class.getClassLoader(),
            new Class[] { SqlSession.class },
            new SqlSessionInterceptor());
  }

    private class SqlSessionInterceptor implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 获取真实的sqlSession对象
            SqlSession sqlSession = getSqlSession(
                SqlSessionTemplate.this.sqlSessionFactory,
                SqlSessionTemplate.this.executorType,
                SqlSessionTemplate.this.exceptionTranslator);
            try {
                // 执行sqlSession对象的真正接口实现，以DefaultSqlSession的实现执行具体statement
                Object result = method.invoke(sqlSession, args);
                if (!isSqlSessionTransactional(sqlSession, SqlSessionTemplate.this.sqlSessionFactory)) {
                    // force commit even on non-dirty sessions because some databases require
                    // a commit/rollback before calling close()
                    sqlSession.commit(true);
                }
                return result;
                } catch (Throwable t) {
                Throwable unwrapped = unwrapThrowable(t);
                if (SqlSessionTemplate.this.exceptionTranslator != null && unwrapped instanceof PersistenceException) {
                    // release the connection to avoid a deadlock if the translator is no loaded. See issue #22
                    closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
                    sqlSession = null;
                    Throwable translated = SqlSessionTemplate.this.exceptionTranslator.translateExceptionIfPossible((PersistenceException) unwrapped);
                    if (translated != null) {
                    unwrapped = translated;
                    }
                }
                throw unwrapped;
            } finally {
            if (sqlSession != null) {
                closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
            }
            }
        }
    }
}
```

以上SqlSessionInterceptor的拦截逻辑在mapper的接口调用时拦截执行，可以看到里面并没有对具体多个statement的事务控制

而Service方法（@Transactional一般标注在Service层的业务方法上）是Mapper的使用方，可以调用一个/多个Mapper方法，所以最后事务的提交是由TransactionInterceptor调用连接对象commit()方法完成的

由此可见，事务最终的提交还是由Spring的TransactionInterceptor进行管理