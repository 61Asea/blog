# Mybatis：SqlSession

`SqlSession`，称为数据库会话，Mybatis以此作为抽象来操作db

SqlSession是线程非安全的，因此不能被共享，**每个线程都应该有它自己的SqlSession实例**

![Mybatis](https://asea-cch.life/upload/2021/09/Mybatis-4a767dc49e944f8fb0f158b0364e0a6a.png)

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

解析xml的工作交由`XMLConfigBuilder`：

- \<properties>

- \<typeAliases>

- \<settings>

- \<environments>：主要解析可能多个的\<environment>（每个\<environment>还包括有：\<transtionManager>和\<dataSource>）

    - transactionManager：源码还称它为`TransactionFactory`，

    - dataSource：Mybatis内置两种数据源，分为池化和非池化，但实际使用上多数人仍以阿里的druid（德鲁伊）为主

- \<mappers>：主要解析多个\<mapper>

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

    // 传入<mappers>标签
    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    // <package>标签
                    String mapperPackage = child.getStringAttribute("name");
                    // 添加Mapper接口类到configuration
                    configuration.addMappers(mapperPackage);
                } else {
                    // <mapper>标签读取属性，属性之间互斥，只能存在一个
                    Sttring resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    if (resource != null && url == null && mapperClass == null) {
                        // <mapper resource="..." />解析，使用io，resource通常是xml配置的statement集合
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getResourceAsStream(resource);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                        // 通过XNode解析后调用configuration.addMapper加入到configuration中
                        mapperParser.parse();
                    } else if (resource == null && url != null && mapperClass == null) {
                        // <mapper url="..." />解析，使用io
                        ErrorContext.instance().resource(resource);
                        InputStream inputStream = Resources.getUrlAsStream(url);
                        XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                        // 与resource类似
                        mapperParser.parse();
                    } else if (resource == null && url == null && mapperClass != null) {
                        // <mapper class="..." />解析，直接用反射
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        // 添加Mapper接口类到configuration
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one");
                    }
                }
            }
        }
    }
}
```

Configuration组合`MapperRegistry`，将Mapper的管理行为委托于后者，而自身存储管理了所有从XML或Mapper注解接口类解析的`Statement`：

- xml：在Mybatis总配置中的\<mapper resource="...">

    根据namespace读取对应xml的全部statement并注册到该结构，并以namespace解析出的Mapper类名注册到MapperRegistry

- SQL注解：在Mybatis总配置中的\<mapper class="...">和\<mapper package="...">，或者与Spring整合后的组件扫描方式

    直接解析注解类，读取各个方法的@Select、@Update、@Update注解，并将解析的Statement注册到该结构，将注解类注册到MapperRegistry

```java
public class Configuration {
    protected Environment environment;

    // statemenet（xml或注解上的sql语句模板），key为statement的id，value为statement
    protected final Map<String, MappedStatement> mappedStatements = new StrictMap<MappedStatement>("Mapped Statements collection");

    protected MapperRegistry mapperRegistry = new MapperRegistry(this);

    // 加载package字符串使用
    public void addMappers(String packageName) {
        // 寄托MapperRegistry
        mapperRegistry.addMappers(packageName);
    }

    // 加载class、url、resource使用
    public <T> void addMapper(Class<T> type) {
        // 寄托MapperRegistry
        mapperRegistry.addMapper(type);
    }

    public boolean hasMapper(Class<?> type) {
        return mapperRegistry.hasMapper(type);
    }
}
```

MapperRegistry具体的解析逻辑在`MapperAnnotationBuilder`中：

```java
public class MapperRegistry {
    // 持有configuration的引用
    private final Configuration config;
    private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<Class<?>, MapperProxyFactory<?>>();

    public MapperRegistry(Configuration config) {
        this.config = config;
    }

    public <T> void addMapper(Class<T> type) {
        // Mapper都为接口类型
        if (type.isInterface()) {
            if (hasMapper(type)) {
                // 不允许覆盖注册
                throw new BindingException("Type is already know to the MapperRegistry");
            }
            boolean loadCompleted = false;
            try {
                knowMappers.put(type, new MapperProxyFactory<T>(type));
                MappperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
                // 这个parser主要是为了解析注解类的；如果是xml调用该方法的话，解析在该方法调用前完成，这款里只会将mapper注册，不会再次解析
                parser.parser();
                loadCompleted = true;
            } finally {
                if (!loadCompleted) {
                    knowMappers.remove(type);
                }
            }
        }
    }
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
    - `mappedStatements`：存储全部的statement
    - `mapperRegistry`：管理全部的mapper
    - environment
        - transactionFactory：会话可忽略，这个只用在SqlSessionFactory创建会话
        - dataSource：数据源可忽略，SqlSessionFactory创建会话时已经分配好了connection
    - `executor`：该对象可以视为`statement`的操作执行器，一般为`SimpleExecutor`
        - transaction：整合spring事务后，基本废弃
            - `connection`（若无指定，由DataSource分配）

```java
public class DefaultSqlSession implements SqlSession {
    private Configuration configuration;

    // 一般为SimpleExecutor
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

通过DefaultSqlSessionFactory创建SqlSession时，传入的Executor类型都为`configuration.getDefaultExecutorType()`：

```java
// Configuration.class
public class Configuration {
    // SimpleExecutor
    protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;

    public ExecutorType getDefaultExecutorType() {
        // 一般情况下返回ExecutorType.SIMPLE
        return defaultExecutorType;
    }

    public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
        executorType = executorType == null ? defaultExecutorType : executorType;
        executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
        Executor executor;
        if (ExecutorType.BATCH == executorType) {
            executor = new BatchExecutor(this, transaction);
        } else if (ExecutorType.REUSE == executorType) {
            executor = new ReuseExecutor(this, transaction);
        } else {
            // 返回SimpleExecutor
            executor = new SimpleExecutor(this, transaction);
        }
        if (cacheEnabled) {
        executor = new CachingExecutor(executor);
        }
        executor = (Executor) interceptorChain.pluginAll(executor);
        return executor;
    }
}
```

所以我们在看sqlSession对象的db操作实现应关注`SimpleExecutor`：

> 因为sqlSession可以由用户直接指定connection，也可以由DataSource分配，后者属于懒汉式加载，**加载时机就在SimpleExecutor执行操作时**

```java
public class SimpleExecutor extends BaseExecutor {
    // 以查询操作的子类模板方法为例
    // ms通过statementId，从Configuration中取出
    @Override
    public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 将ms和stmt进行结合
        Statement stmt = null;
        try {
            Configuration confiration = ms.getConfiguration();
            StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
            // 获取connection，并通过connection来获取预编译statement（默认），并传入参数
            stmt = prepareStatement(handler, ms.getStatementLog());
            // 执行statement的execute()方法，并将结构进行映射
            return handler.<E>query(stmt, resultHandler);
        } finally {
            closeStatemet(stmt);
        }
    }

    private Statement prepareStatement(StatementHandler handler, Log statemenetLog) throws SQLException {
        Statement stmt;
        // 无则由DataSource分配，有（提前指定）则直接获取
        Connection connection = getConnection(statementLog);
        // 通过connection初始化一个PrepareStatement并返回
        stmt = handler.parepare(connection);
        // 往预编译sql的statement模板填充参数
        handler.parameterize(stmt);
    }

    protected Connection getConnection(Log statementLog) throws SQLException {
        // 事务是否有connection对象，决定于外部接口的调用，若调用DefaultSqlSessionFactory#openSessionFromDataSource则无；若调用#openSessionFromConnection则有
        Connection connection = transaction.getConnection();
        if (statementLog.isDebugEnabled()) {
            return ConnectionLogger.newInstance(connection, statementLog, queryStack);
        } else {
            return connection;
        }
  }
}
```

connection的获取可以看一下`JdbcTransaction`的实现：

```java
public class JdbcTransaction {
    // ...

    // 事务是否有connection对象，决定于外部接口的调用，若调用DefaultSqlSessionFactory#openSessionFromDataSource则无；若调用#openSessionFromConnection则有
    @Override
    public Connection getConnection() throws SQLException {
        if (connection == null) {
            openConnection();
        }
        return connection;
    }
}
```

# **SqlSession、Connection、Transaction的关系总结**

> [解析Mybatis之Sqlsession、Connection和Transaction原理与三者间的关系](https://blog.csdn.net/AiMaiShanHuHai/article/details/102984764)

生命周期：
- SqlSession：只存在于一次Http请求
- Connection：由DataSource管理
- Transaction：伴随SqlSession生成，只存在于一次Http请求

相互关系：
- SqlSession和Transaction：一次会话可以提交多个事务操作，所以为1:N的关系
- SqlSession和Connection：Connection不可以同时被多个线程并发使用
    - 不同时刻的SqlSession可以复用同一个Connection，所以为N：1的关系
    - **某一时刻的SqlSession绑定一个Connection，所以为1:1的关系**

![Transaction_SqlSession_Connection](https://asea-cch.life/upload/2021/09/Transaction_SqlSession_Connection-0349e45743d9466ab243717bc8eea922.png)

结论：

1. 业务通过Mybatis执行DB操作，SqlSession和Connection的关系为1:1，那么可以简单认为SqlSession是Connection的包装类

2. org.apache.ibatis.mapping.StatementType中包含了：STATEMENT、PREPARED、CALLABLE，默认为PREPARED，所以生成的Statement为PreparedStatement


# 参考
- [SqlSession、SqlSessionFactory和SqlSessionFactoryBuilder](https://blog.csdn.net/chris_mao/article/details/48803545)

# 重点参考
- [源码org.apache.ibatis.*]()
- [Sqlsession、Connection和Transaction原理与三者间的关系](https://blog.csdn.net/AiMaiShanHuHai/article/details/102984764)
- [spring中的mybatis的sqlSession是如何做到线程隔离的？](https://www.cnblogs.com/yougewe/p/10072740.html)