# Spring：bean定义

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

# 参考
- [bean中的autowire-candidate又是干什么的](https://cloud.tencent.com/developer/article/1591125)