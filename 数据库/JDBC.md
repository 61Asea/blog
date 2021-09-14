# JDBC

JDBC，全称`Java Data Base Connectivity`（Java数据库连接），是一种用于执行SQL语句的JAVA API，为多种**关系型数据库**提供`统一访问`

## JDBC破坏双亲委派

> [《双亲委派模式破坏-JDBC》](https://blog.csdn.net/sinat_34976604/article/details/86723663)：JDBC加载是JNDI调用SPI的其中一种

JDBC的核心在rt.jar中，由启动类加载器（Bootstrap ClassLoader）加载，按照`双亲委派机制`，当业务代码中需要加载厂商DB类时，会从应用类加载器委派到启动类加载器，调用rt.jar的JDBC相应类进行加载

然而后续需要加载厂商的实现时，它们都在其它jar包中，启动类加载器只负责加载`$JAVA_HOME中jre/lib/rt.jar`中的类，并无法加载到这些`Driver实现类`

所以启动类加载器通过`Thread.currentThread().getContextLoader()`线程上下文，**直接往下指派给应用类加载器去加载**，这种类加载方式`破坏双亲委派`

# **1. 传统JDBC**

<!-- 思路：按照面试题的思路去挑选重点看，如JDBC连接数据库的步骤、Statement和PreparedStatement的区别、数据分页 -->

## **1.1 基本设施**

### **1.1.1 Driver接口**

> 注意在rt.jar/java/sql/Driver，跟db厂商重新声明的Driver类不同

作用：作为各个数据库厂商驱动的父类，每个厂商的实现类必须继承该接口，具体实现包括不限于连接、校验URL、配置信息

使用方式：用户可以通过CLass.forName("....")加载指定驱动类，在创建出一个驱动实例后将它注册到DriverManager中

接口代码：

```java
package java.sql;

// Java的SQL架构允许加载多种不同的数据库驱动
public interface Driver {
    // 连接
    Connection connect(String url, java.utils.Properties info) throws SQLException;

    // 重试给定URL是否可以建立数据库连接
    boolean acceptsURL(String url) throws SQLException;

    // 获取驱动子类的配置信息
    DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException;

    // ...
}
```

Mysql的Driver实现子类：

```java
package com.mysql.jdbc;

// 实现父类接口
public class NonRegisteringDriver implements java.sql.Driver {
    @Override
    public java.sql.Connection connect(String url, Properties info) throws SQLException {
        if (url == null) {
            throw SQLError.createSQLException("...");
        }

        if (StringUtils.startWithIgnoreCase(url, LOADBALANCE_URL_PREFIX)) {
            // 返回负载均衡连接实例
            return connectLoadBalanced(url, info);
        } else if (StringUtils.startWithIgnoreCase(url, REPLICATION_URL_PREFIX)) {
            // 返回从连接实例
            return connectReplicationConnection(url, info);
        }

        Properties props = null;

        // 根据用户传入的url和info进行校验，如果得不出props，说明用户填写有误
        if ((props = parseURL(url, info)) == null) {
            return null;
        }

        // ...

        try {
            Connection newConn = com.mysql.jdbc.ConnectionImpl.getInstance(host(props), port(props), props, database(props), url);
            return newConn;
        } catch (...) {
            // ...
        }
    }
}
```

#### DriverManager

用户在使用厂商驱动时，只需要通过Class.forName("...")加载即可，无须关注加载了多少db的驱动，也无须关注Driver类的接口

当需要获取连接时，通过`DriverManager.getConnecton(...)`即可，DriverManager会根据**连接请求**加载它能找到的驱动，对目标数据库URL进行连接再返回连接对象

```java
public class DriverManager {
    private static Connection getConnection(String url, java.util.Properties info, Class<?> caller) throws SQLException {
        // ...

        // 遍历注册过的驱动，用户无须关注是否使用了具体驱动，即降低了不同db驱动与代码的耦合
        for (DriverInfo aDriver : registeredDrivers) {
            if (isDriverAllowed(aDriver.driver, callerCL)) {
                // ....
                Connection con = aDriver.driver.connect(url, info);
            }
        }
    }
}
```

> 这里不推荐使用DriverManager.registerDriver的方式进行注册，因为会产生两个相同实例，并且使得代码与对应厂商驱动耦合

```java
package com.mysql.jdbc;

public class Driver extends NonRegisteringDriver implements java.sql.Driver {
    static {
        try {
            // 当加载该类时，静态代码块会执行，将该驱动自动注册到DriverManager中
            java.sql.DriverManager.registerDriver(new Driver());
        } catch (SQLException E) {
            // ...
        }
    }
}
```

如果使用`DriverManager.registerDriver()`的方式，在业务代码中注册某个DB的驱动时：

```java
public class ConnectDbDemo {
    static {
        DriverManager.registerDriver(new java.mysql.jdbc.Driver());
    }
}
```

- 加载java.mysql.jdbc.Driver类时注册一次，然后本身语义也注册了一次
- registerDriver方法的参数被限定为某个驱动类，如果是`Class.forName(“...”)`的方式，可以将字符串作为动态配置，在程序运行时进行修改，降低程序和DB驱动的耦合

### **1.1.2 Connection接口**

作用：与特定数据库的连接会话，在连接上下文中执行sql语句并返回结果

使用方式：DriverManager.getConnection(...)

接口代码：

```java
public interface Connection {
    // 创建向数据库发送sql的Statement对象
    Statement createStatement() throws SQLException;

    // 创建向数据库发送预编译sql的PreparedStatement对象
    PreparedStatement prepareStatement(String sql) throws SQLException;

    // 创建执行存储过程的callableStatement对象
    CallableStatement prepareCall(String sql) throws SQLException;

    // 设置是否自动起开事务
    void setAutoCommit(boolean autoCommit) throws SQLException;

    // 提交事务
    void commit() throws SQLException;

    // 回滚事务
    void rollback() throws SQLException;
}
```

Mysql的连接类代码：

```java
// MysqlConnection继承了Connection接口
public class ConnectionImpl implements MysqlConnection {
    ResultSetInternalMethods execSQL(StatementImpl callingStatement, String sql, int maxRows, Buffer packet, int resultSetType, int resultSetConcurrency, boolean streamResults, String catalog, Field[] cachedMetadata) throws SQLException {
        // ...
    };

    ResultSetInternalMethods execSQL(StatementImpl callingStatement,String sql, int maxRows, Buffer packet, int resultSetType, int resultSetConcurrency, boolean streamResults, String catalog, Field[] cachedMetadata, boolean isBatch) throws SQLException {
        // ...
    }
}
```

> 提供了execSQL方法，commit、rollback、mysql的statement对象都调用`execSql(...)`执行SQL

### **1.1.3 Statement对象**

作用：用于执行静态SQL语句并返回它所生成结果的对象

使用方式：`Statement st = conn.createStatement();`

共有三种Statement类：
- Statement：`动态占位符`，在处理SQL的程序中执行变量绑定操作，然后再将SQL发送到数据库引擎处理

- PreparedStatement：`静态占位符`，将含有占位符的SQL语句直接发送到数据库引擎，执行`预编译`操作等准备工作后`确定SQL语句`，随后绑定值也被发送到数据库引擎，数据库引擎将收到的值填充进SQL语句后将其执行

- CallableStatement：调用存储过程

接口代码：

```java
public interface Statement {
    execute(String sql);

    executeQuery(String sql);

    executeUpdate(String sql);

    addBatch(String sql);

    executeBatch();
}
```

### **PrepareStatement和Statement的区别**

![Statement和PreparedStatement](https://asea-cch.life/upload/2021/09/Statement%E5%92%8CPreparedStatement-204bd3fd5ed9404ea9474a20835d9a41.jpg)

> [数据库预编译为何能防止SQL注入？](https://www.zhihu.com/question/43581628)

PrepareStatement同样实现Statement接口，主要提供了`预编译`功能：

> 适用场景：一条SQL语句需要反复执行多次，并且只有字段的值可能存在改变

- 分析SQL静态占位符的关键词汇，之后将不会再重复编译，每次执行都省去了解析优化等过程，提升**执行效率**

- SQL的功能确定，所以在后续传入什么"AND"，"OR"这些都关键词到占位符中，都只会被当做普通字符串进行处理（转义处理），**可以防止SQL注入**

```java
// ServerPreparedStatement的构造函数
protected ServerPreparedStatement(MySQLConnection conn, String sql, String catalog, int resultSetType, int resultSetConcurrency) throws SQLException {
    // ...
    try {
        // 将含有静态占位符的SQL语句模板提前发送到Mysql引擎进行预编译
        serverPrepare(sql);
    }
    // ...
}
```

#### **配置参数**

关于PreparedStatement，有两个重要的配置参数：

- useServerPrepStmts：使用服务端预编译

    > 如果没有开启，则PreparedStatement实质上是一个假的预编译Statement，并不会真正产生`PREPARE`、`EXECUTE`命令

- cachePrepStmts：在客户端上使用预编译缓存机制，缓存是`对应于每一个连接`，而不是共享的

    在mysql-connection-java.jar包中存有预编译语句的缓存，缓存的key是完整的sql语句，value是preparedStatement对象

    当开启cachePrepStmts后，`preparedStatement.close()`将不会真正地向服务器发送`closeStmt`，而是将PreparedStatment的状态置为关闭，并**放入缓存**中，在下一次使用时直接从缓存中取出使用


> 所以，只有结合`useServerPrepStmts = true`和`cachePrepStmts = true`，才能最大程度的提升执行效率

#### **执行效率**

频繁使用的语句上，使用预编译+缓存确实能够得到可观的提升；然而对于不频繁使用的语句，服务端编译会增加额外开销，要视情况而定

### **1.1.4 ResultSet**

作用：存放查询结果集合

使用：`ResultSet rs = st.executeQuery()`

类型：在创建Statement时指定生成的类型`conn.createStatement(int resultSetType, int resultSetConcurrency)`
- resultSetType：结果集的遍历类型

    - ResultSet.TYPE_FORWARD_ONLY：只能向前滚动，默认值
    - ResultSet.TYPE_SCROLL_INSENSITIVE：任意的前后滚动，对于修改不敏感

        一旦创建后，如果数据库的数据再发生修改，对它而言无法感知

    - ResultSet.TYPE_SCROLL_SENSITIVE：任意的前后滚动，对于修改敏感

        一旦创建后，如果数据库的数据发生修改，可感知

- resultSetConcurrency：结果集访问的并发级别

    - ResultSet.CONCUR_READ_ONLY：设置为只读类型
    - ResultSet.CONCUR_UPDATABLE：设置为可修改类型
    > 可以使用ResultSet的更新方法来更新里面的数据

- 大致种类的ResultSet

    - 基本

        `conn.createStatement()`获得，不支持滚动读取，只能使用next()方法逐个读取

    - 可滚动

        支持前后滚动操作：next()、previous()、回到第一行first()、取set中的第几行absolute()

        `conn.createStatement(Result.TYPE_SCROLL_INSENITIVE, ResultSet.CONCUR_READ_ONLY)`

## **1.2 JDBC连接数据库步骤**

```java
static {
    // 1. 加载厂商的驱动类
    Class.forName("com.mysql.jdbc.Driver");

    // 2. 建立连接
    Connection conn = DriverManager.getConnection(url, user, password);

    // 3. 获取执行SQL语句的Statement对象
    PreparedStatement psmt = conn.preparedStatement(sql);
    psmt.setInt(index1, ...);
    psmt.setString(index2, "...");

    // 4. 执行SQL语句
    ResultSet rs = psmt.executeQuery();

    // 5. 处理结果集
    while (rs.next()) {
        rs.getString("col_name");
        rs.getInt(1);
    }

    // 6. 释放资源
    try {
        if (rs != null) {
            rs.close();
        }
    } catch(SQLException e) {
        e.printStackTrace();
    } finally {
        try {
            if (psmt != null) {
                psmt.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
```

#### **释放资源**

关闭顺序：ResultSet -> Statement -> Connection

# **2. 数据源**

<!-- 思路：

- 数据库连接池工作原理和实现方案，druid就是其中一种，这与JDBC2.0提供的DataSource数据源接口做交互

- 设计一个数据库连接池方案（druid） -->



# 参考
- [双亲委派模型破坏](https://blog.csdn.net/sinat_34976604/article/details/86723663)
- [传统JDBC](https://www.cnblogs.com/erbing/p/5805727.html)

- [你不知道的PreparedStatement预编译](https://blog.csdn.net/alex_xfboy/article/details/83901351)

- [JDBC和druid的简单介绍](https://www.cnblogs.com/knowledgesea/p/11202918.html)

- [Spring JDBC源码解析](https://mp.weixin.qq.com/s?__biz=MzU5MDgzOTYzMw==&mid=2247484601&idx=1&sn=3c0e33701105a65e74627bc7a40e545d&scene=21#wechat_redirect)

# 重点参考
- [数据库预编译为何能防止SQL注入？](https://www.zhihu.com/question/43581628)
- [JDBC与数据库连接池](https://blog.csdn.net/lonelymanontheway/article/details/83339837)