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

    `ImportBeanDefinitionRegistrar`类型，用于向工厂注册`AbstractAutoProxyCreator`，以实现事务对象代理
    
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

## **读取事务相关的AOP信息**

```java

```

## **获取增强**

# 参考
- [Spring事务源码解析（一）@EnableTransactionManagement注解](https://mp.weixin.qq.com/s/FU3hznLFspCcHYJs-x8h2Q)