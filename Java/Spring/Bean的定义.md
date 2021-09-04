# Spring：bean定义

# **1. BeanDefinition**

```java
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {
    // "singleton"
    String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

    // "prototype"
    String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;

    // ********************************************************
    //                          bean role
    // ********************************************************
    
    // 代表该bean定义是应用程序的主要部分，通常对应于用户定义的bean
    int ROLE_APPLICATION = 0

    // ComponentDefinition：组件定义

    // 指示该bean定义是某个较大配置（通常是外部组件定义）的支持部分，在查看特定的组件定义时需要注意这种类型的bean，但在查看应用程序的总体配置时则无需注意
    int ROLE_SUPPORT = 1;

    // 代表该bean定义是组件定义内工作的bean，与用户无关，是基础设施角色
    int ROLE_INFRASTRUCTURE = 2;

    // 设置对于该bean的父定义（getMergedBeanDefinition方法使用它进行对child-bean进行合并）
    void setParentName(@Nullable String parentName);

    @Nullable
    String getParentName();

    // 设置该bean的类名，一般在解析Resource后得到类名时调用设置
    void setBeanClassName(@Nullable String beanClassName);

    String getBeanClassName();

    // 设置该bean的scope信息，xml在读取xml属性后同时调用设置scope，注解则在全部读取后的外层doScan进行处理
    void setScope(@Nullable String scope);

    String getScope();

    // 是否懒加载，即不在容器初始化时加载，直到调用getBean时再去加载
    void setLazyInit(boolean lazyInit);

    boolean isLazyInit();

    // 指定bean的依赖关系
    void setDependsOn(@Nullable String... dependsOn);

    @Nullable
	String[] getDependsOn();

    // 设置该bean是否在被其他对象作为自动注入对象时，作为候选bean，默认值是true
    // 与Primary互斥，用于解决通过类型查找bean时的情况
    void setAutowireCandidate(boolean autowireCandidate);

    boolean isAutowireCandidate();

    // 按类型自动注入时，如果存在多个bean，则选取primary的bean
    void setPrimary(boolean primary);

    boolean isPrimary();

    // 指定要使用的工厂bean（如果有）
	void setFactoryBeanName(@Nullable String factoryBeanName);

    @Nullable
	String getFactoryBeanName();

    // 指定工厂方法，此方法将使用调用构造函数参数，如果未指定参数则默认不带参数，将在上面指定的工厂bean（如果有）上调用该方法
    void setFactoryMethodName(@Nullable String factoryMethodName);

    String getFactoryMethodName();
}
```

# **2. bean的装配**

以xml为例，介绍type、id、name、alias的含义

- type：指bean的类型，以`class`属性的值，或注解所在类

- id/name：都可作为bean的`标识符`，以id优先
    - `<bean id = "id" />`："id"作为标识符
    - `<bean name = "name1">`："name1"作为标识符
    - `<bean name = "name1, name2, name3">`：”name1“作为标识符，name2，name3作为别名
    - `<bean id = "id" name = "name1">`："id"作为标识符，”name1“作为别名
    - `<bean id = "id" name = "name1, name2, name3">`：“id”作为标识符，“name1”、“name2”、“name3”作为别名

- alias：bean的别名

    ```xml
    <bean id = "id" name = "name1, name2, name3" class = "xxxx" />
    <alias alias = "alias1" name = "id"/>
    ```

    "id"作为标识符，“name1”、“name2”、“name3”、“alias1”作为bean的别名

    > 注意，<alias>标签中的name对于的值应该为<bean>的`标识符`

## **2.1 装配相关注解**

JSR-250规范定义的注解：`Resource`、`PostConstruct`、`PreDestroy`

Spring自定义：`Autowired`

资源：`Bean`、`Component`(`Configuration`、`Service`、`Controller`)

重点在于自动注入，即@Autowired和@Resource的装配方式：

- @Autowired：默认按bean的`type`注入，且默认要求依赖对象必须**存在且唯一**

    - 如果允许不存在，则标注required属性为null

        ```java
        @Autowired(required = false)
        ```
    - 如果想通过`bean标识符`进行装配，或者**同个类型存在多个bean**，可以用以下方式解决：

        > 这种情况在web开发中很常见，比如以上userService可能有多种用户impl类，若未指定标识符Spring将无法确定注入的对象，抛出BeanCreationException

        - 可以搭配`@Qualifier("xxxx")`注解指定唯一标识符：
        
            ```java
            @Qualifier("id1")
            @Autowired(required = false)
            private UserService userService;
            ```

        > 注意：这种方式下，直接按名字查找，不会再根据类型查找

        - 可以搭配`@Primary`，为确定的实现子类做唯一标识

            ```java
            @Primary
            @Service
            public class UserServiceImplOne {}
            ```
        - 可以搭配`autowire-candidate`属性，目前已经被废弃，没有为其做配套的注解

        - 根据加上Autowired注解的bean引用名

            因为基于注解的初始化方式下，会使用该类的类型名作为`标识符`，并且在Autowired的postBeanProcessor下会读取引用的名字作为`标识符`

            > 当然Autowired还是默认以type为第一加载选取

            ```java
            // 先按照类型查找，如果发现有多个实现类，则使用userServiceImplOne作为标识符在多个实现类中查找
            @Autowired
            private UserService userServiceImplOne;
            ```

- @Resource

    - 如果没有指定name属性，也没有指定type属性

        默认按照bean的`标识符`注入，如果没有匹配，则再按bean的`type`装配

    > 容错性高，但效率较低（因为如果在注册组件和填写规则时没有遵循良好的规范，会导致查找两次，低效率）
    
    - 如果同时指定name和type

        从上下文中找到唯一匹配的bean进行装配，找不到则抛出错误

    - 只指定name

        只按`标识符`注入，不会再按类型注入，找不到则抛出错误

    - 只指定type

        只按`类型`注入，不会再按标识符注入，如果找到多个或找不到则抛出错误

### **自动装配的Demo**

```java
public interface IUserService {
    // ...
}

@Service
// 标识符：userServiceImplOne
public class UserServiceImplOne implements IUserService {

}

@Service
// 标识符；userService
public class UserService implements IUserService {

}

@Service("iUserService1")
// 标识符：iUserService1
public class UserServiceImplTwo implements IUserService {

}

@Controller
public class UserController {
    @Autowired
    // 按照type查找，发现有多个，则按照"iUserService"标识符进行name查找，发现查找不到
    private IUserService iUserService; // 出错，不知道要注入哪个

    @Autowired
    // 先按type查找，发现有多个，则按照"userService"标识符进行name查找
    private IUserService userService; // 注入UserService类的bean实例

    @Autowired
    @Qualifier("userServiceImplOne")
    // 直接按照"userServiceImplOne"进行name查找
    private IUserService iUserService; // 注入UserServiceImplOne类的bean实例

    @Resource
    // 按照iUserService进行name查找，找不到，再按照类型查找，发现有个多
    private IUserService iUserService;  // 出错

    @Resource
    // 按照"iUserService1"进行name查找，直接找到
    private IUserService iUserService1; // 注入UserServiceImplTwo类的bean实例

    @Resource(name = "userService121231")
    // 只按照"userService121231"进行name查找，找不到则直接报错
    private IUserService iUserService; // 出错

    @Resource(name = "userService")
    // 只按照"userService"进行name查找，找到则注入
    private IUserService iUserService; // 注入UserService的bean实例

    @Resource(type = "UserServiceImplThree")
    // 只按照类型为UserServiceImplThree进行查找，找不到直接报错
    private IUserServide iUserService; // 报错

    @Resource(type = "UserServiceImplTwo")
    // 只按照类型为UserServiceImplThree进行查找，找到直接注入
    private IUserService iUserService; // 注入UserServiceImplTwo的bean实例

    @Resource(name = "iUserService1", type = "UserServiceImplTwo")
    // 直接查找iUserService1为name，UserServiceImplTwo的类的bean，这种意味着唯一标识bean，找不到则直接报错
    private IUserService iUserService; // 注入UserServiceImplTwo的bean实例

    // ...
}

```

## **2.2 12种装配方式**

有空再看，吐了吐了。。。

# 参考
- [bean中的autowire-candidate又是干什么的](https://cloud.tencent.com/developer/article/1591125)
- [@Autowired 与@Resource的区别](https://blog.csdn.net/weixin_40423597/article/details/80643990)
- [Bean的命名id和name区别](https://www.cnblogs.com/zheting/p/7712426.html)
- [beans中的bean对象取别名的两种方式，alias标签和bean的name属性](https://blog.csdn.net/qq_45895576/article/details/115740700)

- [Autowired如何解决注入多个实现类时的问题](https://blog.csdn.net/bird_tp/article/details/106338111)