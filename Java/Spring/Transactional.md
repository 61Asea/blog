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

与AOP的`@EnableAspectJAutoProxy`注解类似，在`ConfigurationClassPostProcessor`中读取配置：

> ConfigurationClassPostProcessor是一个**BeanFactoryPostProcessor**，可以看调用栈：`AbstractApplicationContext`#`refresh()`#`invokeBeanFactoryPostProcess()`方法

```java
```

今晚目标：总结AutoProxyRegistrar和ProxyTransactionManagementConfiguration这两个Registrar是如何被加载到的

重新归纳一下@Import和@Configuration注解的流程

# 参考
- [Spring事务源码解析（一）@EnableTransactionManagement注解](https://mp.weixin.qq.com/s/FU3hznLFspCcHYJs-x8h2Q)