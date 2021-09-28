# Mybatis：Spring整合

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

# **SqlSessionFactoryBean**

> [IOC#FactoryBean]()：`FactoryBean`通过工厂思想去产生新的object（bean），如果一个bean实现了该接口，那么它将被当作一个`对象的工厂`，FactoryBean本身是一个bean，但它不能被当作一个正常bean实例来使用，所以也不能直接作为一个bean实例来公开自己（无法直接获取）

`SqlSessionFactoryBean`就使用了FactoryBean方式，用户根据容器获得时会获得对应的SqlSessionFactory对象实例

```java
// 着重阅读FactoryBean和InitializingBean的接口方法
public class SqlSessionFactoryBean implements FactoryBean<SqlSessionFactory>, InitializingBean, ApplicationListener<ApplicationEvent> {
    // config文件的位置，建议用户设置在WEB-INF/mybatis-configuration.xml
    private Resource configLocation;

    // 运行时合并到SqlSessionFactory配置中的Mybatis Mapper文件的位置
    private Resource[] mapperLocations;

    // 返回sqlSessionFactory实例
    @Override
    public SqlSessionFactory getObject() throws Exception {
        if (this.sqlSessionFactory == null) {
            afterPropertiesSet();
        }
        return this.sqlSessionFactory;
    }

    @Override
    public Class<? extends SqlSessionFactory> getObjectType() {
        return this.sqlSessionFactory == null ? SqlSessionFactory.class : this.sqlSessionFactory.getClass();
    }

    // 该bean是一个单例，这也意味着它会被缓存到FactoryBeanRegistrySupport的factoryBeanObjectCache结构中
    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // ..

        // 根据配置文件、其他配置来创建sqlSessionFactory单例
        this.sqlSessionFactory = buildSqlSessionFactory();
    }


    // 默认实现使用了标准的Mybatis XMLConfigBuilder API去创建工厂实例，即读取XML文件方式
    protected SqlSessionFactory buildSqlSessionFactory() throws IOException {
        Configuration configuration;
        XMLConfigBuilder xmlConfigBuilder = null;
        if (this.configLocation != null) {
            // 如果配置文件的位置存在，则使用xml读取
            xmlConfigBuilder = new XMLConfigBuilder(this.configuration.getInputStream(), null, this.configurationProperties);
            configuration = xmlConfigBuilder.getConfiguration();
        } else {
            // 
            configuration = new Configuration();
            configuration.setVariables(this.configurationProperties);
        }

        // 读取TypeAlias、TypeHandler、plugins到configuration中

        // 读取xml配置，获取environment和mapper
        if (xmlConfigBuilder != null) {
            try {
                xmlConfigBuilder.parse();
                // ...
            } catch() {
                // ...
            }
        }

        // 设置transactionFactory到Bean中，后续设置到Environment中
        if (this.transactionFactory == null) {
            this.transactionFactory = new SpringManagedTransactionFactory();
        }
        configuration.setEnvironment(new Environment(this.environment, this.transactionFactory, this.dataSrouce));

        // 以数据库的产品名作为id
        if (this.databaseIdProvider != null) {
            try {
                configuration.setDatabaseId(this.databaseIdProvider.getDatabaseId(this.dataSource));
            } catch () {
                // ...
            }
        }

        // 额外的mapper，在上面的XMLConfigBuilder中已经先通过XMLMapperBuilder解析了很多
        if (!isEmpty(this.mapperLocations)) {
            for (Resource mapperLocation : this.mapperLocations) {
                if (mapperLocation == null) {
                    continue;
                }

                try {
                    XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(mapperLocation.getInputStream(), configuration, mapperLocation.toString(), configration.getSqlFragments());
                    xmlMapperBuilder.parse();
                } catch (...) {
                    // ...
                }
                // ...
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Property 'mapperLocations' was not specified or no matching resources found");
            }
        }

        return this.sqlSessionFactoryBuilder.build(configuration);
    }
}
```

# **SqlSessionTemplate**

以上配置中，SqlSessionTemplate在容器中为标识符为sqlSession的单例bean

> 按照正常认知，一个请求一个SqlSession，理应SqlSession是多例才对

```java
public class SqlSessionTemplate {

}
```

明天目标：

1. 从正常的ssm项目中，梳理业务mapper的注入和使用，以此来推断SqlSession的调用

2. 继续学习SqlSessionTemplate的代理思想，思考spring mvc线程模型，业务与DAO的线程模型

# 重点参考
- [spring中的mybatis的sqlSession是如何做到线程隔离的？](https://www.cnblogs.com/yougewe/p/10072740.html)