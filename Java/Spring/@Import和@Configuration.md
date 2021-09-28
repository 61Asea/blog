# Spring：@Import和@Configuration

@Configuration注解通过`ConfigurationClassPostProcessor`对内部的属性和方法进行解析，它是一个**BeanFactoryPostProcessor**

在应用上下文加载完BeanFactoryProcessor后，有以下调用栈：`AbstractApplicationContext`#`refresh()`#`invokeBeanFactoryPostProcess()`方法

```java
public class PostProcessorRegistrationDelegate {
    public static void invokeBeanFactoryPostProcessors(
    ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
        // 调用所有BeanDefinitionRegistryPostProcessor的postProcessBeanDefinitionRegistry方法
    }
}
```

ConfigurationClassPostProcessor就是一个`BeanDefinitionRegistryPostProcessor`，它的postProcessBeanDefinitionRegistry方法将会被调用到：

> 在下面方法通过ConfigurationClassEnhancer会创建一个cglib实例，该实例包括了ConfigurationClassEnhancer的拦截器回调，其中`BeanMethodInterceptor`相当重要，其拦截方法实现上调用了`invokeSuper`方法，这也是@Configuration的cglib和其他bean的cglib不同之处

```java
// ConfigurationClassPostProcessor.class

@Override
public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    
    if (!this.registriesPostProcessed.contains(factoryId)) {
        // 从这开始进入主题
        processConfigBeanDefinitions(registry);
    }

    // 另外一个重点：Configuration使用cglib代理，
    enhanceConfigurationClasses(beanFactory);
    beanFactory.addBeanPostProcessor(new ImportAwareBeanPostProccessor(beanFactory));
}

public void processConfigBeanDefinitions(BeanDefinitionRegistry registry) {
    List<BeanDefinitionHolder> configCandidates = new ArrayList<>();
    String[] candidateNames = registry.getBeanDefinitionNames();

    for (String beanName : candidateNames) {
        // 从全部bean定义中，筛选出@Configuration注解的bean

        // ...

        configCandidates.add(new BeanDefinitionHolder(beanDef, beanName));
    }

    // ...

    // Configuration注解的类解析器
    ConfigurationClassParser parser = new ConfigurationClassParser(this.metadataReaderFactory, this.problemReporter, this.environment, this.resourceLoader, this.componentScanBeanNameGenerator, registry);

    Set<BeanDefinitionHolder> candidates = new LinkedHashSet<>(configCandidates);
    Set<ConfigurationClass> alreadyParsed = new HashSet<>(configCandidates.size());
    do {
        // 重要方法：解析器会将全部的Configuration类解析

        // 1. 内层识别ImportSelector（事务）、ImportBeanDefinitionRegistrar（AOP、事务）和Configuration（事务）类共三种的@IMPORT类型，将它们保存到config类中的importBeanDefinitionRegistrars结构中

        // 2. 识别@Bean注解的方法，并将它们加载到config类中的beanMethods结构中
        parser.parse(candidates);
        parser.validate();

        Set<ConfigurationClass> configClasses = new LinkedHashSet<>(parser.getConfigurationClasses());
        configClasses.removeAll(alreadyParsed);

        if (this.reader == null) {
            this.reader = new ConfigurationClassBeanDefinitionReader(
            registry, this.sourceExtractor, this.resourceLoader, this.environment, this.importBeanNameGenerator, parser.getImportRegistry());
        }

        // 重要方法：读取器加载配置类的结构，调用其中附加结构的方法

        // 1. 遍历上面方法产出的beanMethods结构，向工厂注册这些Bean定义（这些bean都会以Autowired的方式注入）

        // 2. 遍历上面方法产出的importBeanDefinitionRegistrars结构，调用它们的registerBeanDefinitions方法来注册一些自定义的Bean定义
        this.reader.loadBeanDefinitions(configClasses);

        // ...
    } while (!configCandidates.isEmpty());

    // ...
}
```

## parser

`parser`的重要方法，调用栈如下：

parse(Set\<BeanDefinitionHolder>) -> 
parse(className, beanName) -> 
processConfigurationClass(new ConfigurationClass(reader, beanName)) ->
doProcessConfigurationClass(configClass, sourceClass)

着重看下上面调用栈的最尾方法，该方法包含了**识别import类型**和**读取@Bean方法**两个重要功能

```java
// ConfigurationClassPareser.class
protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
    // ...

    // 1. 处理@ComponentScan注解

    // 2. getImports()方法读取@Import，并在processImports方法中识别import类型，加载到configClass中
    processImports(configClass, sourceClass, getImports(sourceClass), true);

    // ...

    // 3. 读取@Bean方法，同样加载到configClass中
    Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
    for (MethodMetadata methodMetadata : beanMethods) {
        configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
    }
}
```

`processImports()`是一个递归方法，它相当重要，起到AOP/事务注解与工厂的重要交互作用

```java
// ConfigurationClassParser.class
private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass) {
    if (importCandidates.isEmpty()) {
			return;
		}

    if (checkForCircularImports && isChainedImportOnStack(configClass)) {
        this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
    }
    else {
        this.importStack.push(configClass);
        try {
            for (SourceClass candidate : importCandidates) {
                if (candidate.isAssignable(ImportSelector.class)) {
                    // class是一个ImportSelector的类，这以为着可以根据调用分支进行更丰富的引入功能，在事务Selector中就用了这种方法
                    Class<?> candidateClass = candidate.loadClass();
                    ImportSelector selector = BeanUtils.instantiateClass(candidateClass, ImportSelector.class);
                    
                    // ...

                    String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
                    Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames);
                    // processImports的小递归
                    processImports(configClass, currentSourceClass, importSourceClasses, false);
                }
                else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
                    // class是一个ImportBeanDefinitionRegistrar类型的类，AOP就是通过这种方法来注册自定义的Bean定义，将AnnotationAwareAspectJAutoProxyCreator注册到工厂中
                    Class<?> candidateClass = candidate.loadClass();
                    ImportBeanDefinitionRegistrar registrar =
                            BeanUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class);
                    ParserStrategyUtils.invokeAwareMethods(
                            registrar, this.environment, this.resourceLoader, this.registry);
                    // 加载到configClass的ImportBeanDefinitionRegistrar结构中
                    configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
                }
                else {
                    // 这个配置类不是ImportSelector或ImportBeanDefinitionRegistrar，把它当作@Configuration类，调用processConfigurationClas()进行大递归
                    this.importStack.registerImport(
                            currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
                    processConfigurationClass(candidate.asConfigClass(configClass));
                }
            }
        } catch () {
            // ...
        }
    }
}
```

## reader.loadBeanDefinitions

```java
private void loadBeanDefinitionsForConfigurationClass(ConfigurationClass configClass,
        TrackedConditionEvaluator trackedConditionEvaluator) {

    if (trackedConditionEvaluator.shouldSkip(configClass)) {
        String beanName = configClass.getBeanName();
        if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
            this.registry.removeBeanDefinition(beanName);
        }
        this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
        return;
    }

    if (configClass.isImported()) {
        registerBeanDefinitionForImportedConfigurationClass(configClass);
    }
    // 读取@Bean方法，这个结构在上面的parse中已经存入到configClass中，将这些定义注册到工厂中
    for (BeanMethod beanMethod : configClass.getBeanMethods()) {
        loadBeanDefinitionsForBeanMethod(beanMethod);
    }
    loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
    // 读取@Import进来的组件的注册方法，这些@Import组件在parser中也存入到了configClass中，调用组件的registerBeanDefinitions来注册更为丰富的bean定义
    loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
}
```

所以在最后，TransactionManagementConfigurationSelector的selectImports方法触发`processImports()`的递归调用，向工厂再次传递以下两种新的Import类型：

- `AutoProxyRegistrar`：ImportBeanDefinitionRegistrar类型，用于向工厂注册`AbstractAutoProxyCreator`，以实现事务对象的代理

- `ProxyTransactionManagementConfiguration`：Configuration类型，着重注意其注入的`BeanFactoryTransactionAttributeSourceAdvisor`这个bean，该bean是SpringAOP事务的核心