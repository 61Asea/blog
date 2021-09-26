# Mybatis：SqlSession

`SqlSession`，称为数据库会话，Mybatis以此作为抽象来操作db

SqlSession是线程非安全的，因此不能被共享，**每个线程都应该有它自己的SqlSession实例**

```java
// 简单的伪代码
public class App {
    public SqlSessionFactory sqlSessionFactory;

    public static void main(String[] args) {
        Reader reader;
        try {
            reader = Resources.getResourceAsReader("mybatis.xml");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // 通过builder来创建应用运行期间一直存在的SqlSessionFactory
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);

        SqlSession sqlSession = sqlSessionFactory.openSession();
        try {
            // db operation...
        } finally {
            sqlSession.close();
        }
    }
}
```

以上获取SqlSession的过程中，还涉及了两个新概念：`SqlSessionFactory`、`SqlSessionFactoryBuilder`

# **SqlSessionFactoryBuilder**

SqlSession工厂的建造者类，用于创建全局的SqlSessionFactory

最佳实践生命范围：方法范围，创建完应被回收

可以使Builder一直存在，从而重用创建多个SqlSessionFactory，但这种方式并不推荐：
- 解析xml配置对应的Resource时会占用io输入对象，如果Builder一直存在会占用XML解析资源
- 除非有动态加载新SqlSessionFactory的需求，否则在应用启动后，SqlSessionFactory就应被创建且一直存在

Builder代码简单，返回`DefaultSqlSessionFactory`，这个在后续讲

```java
package org.apache.ibatis.session;

public class SqlSessionFactoryBuilder {
    public SqlSessionFactory build(Reader reader) {
        return builder(reader, null, null);
    }

    public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
        try {
            // parser对象会读取xml配置文件的environments等标签
            XMLConfigBuilder parser = new XMLConfigBuilder(reader, environment, properties);
            // 生成对应的Configuration对象，并以此作为SqlSessionFactory的构造参数，返回生成的会话工厂
            return build(parser.parse());
        }
        // ...
    }

    public SqlSessionFactory build(Configuration configuration) {
        return new DefaultSqlSessionFactory(configuration);
    }
}
```

解析xml的工作交由`XMLConfigBuilder`，\<environments>主要解析可能多个的\<environment>，每个\<environment>还包括有：\<transtionManager>和\<dataSource>

- transactionManager：源码还称它为`TransactionFactory`，

- dataSource：Mybatis内置两种数据源，分为池化和非池化，但实际使用上多数人仍以阿里的druid（德鲁伊）为主

```java
public class XMLConfigBuilder extends BaseBuilder {
    // ...
    private String environment;

    public Configuration parse() {
        if (parsed) {
            // 已经被解析过
            throw new BuilderException("...");
        }
        parsed = true;
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    // 重要方法，着重看解析environments和mappers的部分
    private void parseConfiguration(XNode root) {
        try {
            propertiesElement(root.evalNode("properties"));
            typeAliasesElement(root.evalNode("typeAliases"));
            pluginElement(root.evalNode("plugins"));
            objectFactoryElement(root.evalNode("objectFactory"));
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            reflectionFactoryElement(root.evalNode("reflectionFactory"));
            settingsElement(root.evalNode("settings"));
            // 解析environment参数，并设置DataSource等
            environmentsElement(root.evalNode("environments"));
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            typeHandlerElement(root.evalNode("typeHandlers"));
            // 注册Mapper到Mybatis中
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    private void environmentElement(XNode context) throws Exception {
        if (context != null) {
            if (environment == null) {
                // 设置解析器认为的特定environment
                environment = context.getStringAttribute("default");
            }
        }
        for (Xnode child : context.getChildren()) {
            String id = child.getStringAttribute("id");
            // 将environment的id属性与刚刚设置的特定environment字符串做对比，如果相等则返回true
            if (isSpecifiedEnvironment(id)) {
                // 读取特定的environment标签，这也说明配置多个environment时，只会读取environments上default指定的环境
                TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                DataSource dataSource = dsFactory.getDataSource();
                // 数据源对象、事务工厂为environment的成员
                Environment.Builder environmentBuilder = new Environment.Builder(id).transactionFactory(txFactory).dataSource(dataSource);
                // environment为configuration的一个成员
                configuration.setEnvironment(environmenBuilder.build());
            }
        }
    }    
}
```

```java
public class Configuration {
    protected Environment environment;

    // statemenet（xml或注解上的sql语句模板），key为statement的id，value为statement
    protected final Map<String, MappedStatement> mappedStatements = new StrictMap<MappedStatement>("Mapped Statements collection");
}
```

# **SqlSessionFactory**

会话工厂，用于返回用户`SqlSession`，工厂提供不同的会话开启接口，可以自行决定是由用户还是由DataSource指定返回的SqlSession与JDBC Connection的关系

最佳实践生命范围：应用运行期间

> 重复创建多次SqlSessionFactory被视为代码“坏味道”，没有任何理由对它进行清除或重建

经过SqlSessionFactoryBuilder的构造，我们获得了`DefaultSqlSessionFactory`，在介绍会话工厂前，先简单总结一下组合关系：

- configuration
    - environment
        - transactionFactory
        - dataSource
    - 具备根据类型返回不同Executor的能力，默认为`ExecutorType.SIMPLE`类型，即SimpleExecutor

```java
// 由Factory提供统一会话获取接口，可以由DataSource分配Connection，也可以直接指定Connection
public class DefaultSqlSessionFactory implements SqlSessionFactory {
    private final Configuration configuration;

    public DefaultSqlSessionFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    // 由DataSource分配connection，在返回的该SqlSession第一次操作数据库时进行分配
    private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
        Transaction tx = null;
        try {
            final Environment environment = configuration.getEnironment();
            final TransactionFactory transactionFactory = environment.getTransactionFactory();
            // 调用空参数方法创建的transaction没有connection
            tx = transactionFactory.newTransaction();
            // executor：封装了SqlSession对db的操作，默认SimpleExecutor
            final Executor executor = configuration.newExecutor(tx, execType);
            return new DefaultSqlSession(configuration, executor, autoCommit);
        } catch (Exception e) {
            // ...
        } finally {
            ErrorContext.instance().reset();
        }
    }

    // 用户直接传入connection，指定返回的SqlSession和Connection的关系
    private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
        try {
            boolean autoCommit;
            try {
                // 是否自动提交，由指定的connection决定
                autoCommit = connection.getAutoCommit();
            } catch (SQLException e) {
                autoCommit = true;
            }
            final Environment environment = configuration.getEnvironment();
            final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
            // 关键，transaction持有connection
            final Transaction tx = transactionFactory.newTransaction(connection);
            // SimpleExecutor
            final Executor executor = configuration.newExecutor(tx, execType);
            return new DefaultSqlSession(configuration, executor, autoCommit);
        } catch (Exception e) {
            // ...
        } finally {
            ErrorContext.instance().reset();
        }
    }
}
```

# **SqlSession**

最佳实践生命范围是：请求（线程绑定）、方法范围内（栈封闭）

> 在Web框架中，SqlSession放在与HTTP请求对象相似的范围中。换句话说，当收到HTTP请求时，通过SqlSessionFactory打开一个SqlSession；当返回HTTP响应时，就关闭掉SqlSession

通过SqlSessionFactory返回的SqlSession，组合了以下模块：
- configuration
    - environment
        - transactionFactory
        - dataSource
    - executor
        - transaction
            - connection（若无指定，由DataSource分配）

```java
public class DefaultSqlSession implements SqlSession {
    private Configuration configuration;

    private Executor executor;

    // ...

    public DefaultSqlSession(Configuration configuration, Executor executor, boolean autoCommit) {
        this.configuration = configuration;
        this.executor = executor;
        this.dirty = false;
        this.autoCommit = autoCommit;
    }

    @Override
    public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
        try {
            // 取出xml配置的sql模板，或mapper类sql注解的sql模板，这些模板会在XMLConfigBuilder中被加载为configuration中的statement
            MappedStatement ms = configuration.getMappedStatement(statement);
            // 模板方法模式，调用BaseExecutor父类query()方法，父类会调用子类SimpleExecutor的doQuery模板方法
            executor.query(ms, wrapCollection(parameter), rowBounds, handler);
        } catch (Exception e) {
            // ...
        } finally {
            ErrorContext.instance().reset();
        }
    }   
}
```

## **Executor**

明天目标：
1. statement如何加载到configuration
2. Executor在有/无connection时的不同处理

# 参考
- [SqlSession、SqlSessionFactory和SqlSessionFactoryBuilder](https://blog.csdn.net/chris_mao/article/details/48803545)
- [Sqlsession、Connection和Transaction原理与三者间的关系](https://blog.csdn.net/AiMaiShanHuHai/article/details/102984764)