# Spring：Bean生命周期

`八股文`版本分为`18`个阶段，假设创建bean的昵称为A：

1. 加载全部bean的定义到beanFactory中

2. 初始化beanFactoryPostProcessor的bean，并注册到ApplicationContext中

3. 调用beanFactoryPostProcessor.postProcessorBeanFactory()方法

4. 初始化beanPostProcessor的bean，并注册到BeanFactory中

5. 调用InstantiationAwareBeanPostProcessor.postProcessBeforeInstantiation()方法

6. 通过反射实例化A对象

7. 调用InstantiationAwareBeanPostProcessor.postProcessAfterInstantiation()方法

8. 对A的@Autowired属性进行注入

9. 调用InstantiationAwareBeanPostProcessor.postProcessPropertyValues()方法

10. 对A的其余属性进行赋值

11. 调用BeanNameAware.setBeanName()方法，对A设置beanName

12. 调用BeanFactoryAware.setBeanFactory()方法，对A设置beanFactory

13. **调用BeanPostProcess.postProcessBeforeInitialization()方法**

    > @PostConstructor

14. 调用InitializingBean.afterPropertiesSet()方法，对A进行扩展处理

15. 调用A的自定义init-method方法

16. **调用BeanPostProcess.postProcessAfterInitialization()方法**

    > @Aspect

17. 调用DisposableBean.destroy()方法，调用栈：DisposableBeanAdapter.destroy()

18. 调用A的自定义destroy-method，调用栈：DisposableBeanAdapter.destroy()

![bean生命周期](https://asea-cch.life/upload/2021/09/bean%E7%94%9F%E5%91%BD%E5%91%A8%E6%9C%9F-102f3ef310a342039876ee530e82849c.png)

> 图中最后的destroy-method和DisposableBean.destroy()的位置有误，需要调换一下

## **bean生命周期demo**

bean的整个生命周期穿插在`AbstractApplicationContext`#`refresh()`中，具体的调用栈可以去查看源码：

### **xml文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:p="http://www.springframework.org/schema/p"
       xmlns:aop="http://www.springframework.org/schema/aop" xmlns:tx="http://www.springframework.org/schema/tx"
       xsi:schemaLocation="
            http://www.springframework.org/schema/beans
            http://www.springframework.org/schema/beans/spring-beans-3.2.xsd">

    <bean id="beanPostProcessor" class="cn.shiyujun.lifecycle.MyBeanPostProcessor">
    </bean>

    <bean id="instantiationAwareBeanPostProcessor" class="cn.shiyujun.lifecycle.MyBeanAwarePostProcessor">
    </bean>

    <bean id="beanFactoryPostProcessor" class="cn.shiyujun.lifecycle.MyBeanFactoryPostProcessor">
    </bean>

    <bean id="person" class="cn.shiyujun.lifecycle.Person" init-method="myInit"
          destroy-method="myDestroy" scope="singleton" lazy-init="false">
            <property name="name" value="张三"></property>
            <property name="address" value="广州"></property>
            <property name="phone" value="1590000000"></property>
    </bean>
</beans>
```

容器读取该xml文件后，将先注册前三个beanPostProcessor的bean，并在后续注册`person`

这里对于`person`的属性有几点注意：
- 如果配置`lazy-init = true`，则该bean直到被调用`getBean("person")`时才会初始化
- 如果配置scope为singleton，每次调用getBean都会获得新的实例，且最终容器关闭时，**不会对该bean进行销毁**

    > prototype的bean需要调用者自行销毁

### **BeanFactoryPostProcessor**

作用：通过postProcessBeanFactory，我们可以在bean实例化前对定义进行修改

```java
package cn.shiyujun.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    public MyBeanFactoryPostProcessor() {
        super();
        System.out.println("第1步，BeanFactoryPostProcessor通过构造器实例化，调用时机：容器初始化，bean加载前");
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        System.out.println("第2步，方法名：MyBeanFactoryPostProcessor.postProcessBeanFactory，作用：在bean实例化前，对其定义进行修改，调用栈：ApplicationContext.refresh()#invokeBeanFactoryPostProcessors()，调用时机：beanFactoryProcessor的bean加载后，业务bean加载前");
        // 通过postProcessBeanFactory，在bean实例化前，对定义进行修改
        BeanDefinition bd = beanFactory.getBeanDefinition("person");
        bd.getPropertyValues().addPropertyValue("phone", "911");
    }
}
```

### **InstantiationAwareBeanPostProcessor**

作用：通过InstantiationAwareBeanPostProcessor，我们可以在`实例化前`、`实例化后`、`bean注入除自动装配属性前`共三个阶段进行扩展操作

```java
package cn.shiyujun.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

import java.beans.PropertyDescriptor;

public class MyBeanAwarePostProcessor implements InstantiationAwareBeanPostProcessor {
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        System.out.println("第3步，方法名：MyBeanAwarePostProcessor.postProcessBeforeInstantiation，作用：作为bean实例化前的扩展点，调用栈：AbstractAutowireCapableBeanFactory#createBean()，调用时机：bean实例化之前调用");
        return null;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        System.out.println("第5步，方法名：MyBeanAwarePostProcessor.postProcessAfterInstantiation，作用：作为bean实例化后的扩展点，调用栈：AbstractAutowireCapableBeanFactory#populateBean()，调用时机：bean实例化之后");
        System.out.println("第6步，作用：bean进行自动装配的bean获取，调用栈：AbstractAutowireCapableBeanFactory#populateBean()，调用时机：bean实例化之后");
        return true;
    }

    @Override
    public PropertyValues postProcessPropertyValues(PropertyValues pvs, PropertyDescriptor[] pds, Object bean,
            String beanName) throws BeansException {
        System.out.println("第7步，方法名：MyBeanAwarePostProcessor.postProcessPropertyValues()，作用：在bean注入属性前的扩展点，调用栈：AbstractAutowireCapableBeanFactory#populateBean()，调用时机：bean注入@AutoWired的bean之后");
        System.out.println("第8步，作用：bean注入其他属性，调用栈：AbstractAutowireCapableBeanFactory#populateBean()#applyPropertyValues，调用时机：bean注入@AutoWired的bean之后");
        return pvs;
    }
}
```

> 注意：如果postProcessAfterInstantiation放回了false，**bean的属性将不会被注入，直接返回**！！

### **BeanPostProcessor**

作用：在调用bean的自定义init-method前后进行操作，常用于AOP替换代理对象

```java
package cn.shiyujun.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 内卷beanPostProcessor测试
 */
public class MyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("第11步，方法名：MyBeanPostProcessor.postProcessBeforeInitialization，作用：在bean调用自定义的init方法前进行扩展，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()，调用时机：在bean调用了BeanNameAware和BeanFactoryAware之后");
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("第14步，方法名：MyBeanPostProcessor.postProcessAfterInitialization，作用：在bean调用自定义的init方法之后进行扩展，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()，调用时机：在bean调用了BeanNameAware和BeanFactoryAware之后");
        return bean;
    }
}
```

> 循环依赖时，要先提前暴露出代理对象的引用，具体的操作可继承`SmartInstantiationAwareBeanPostProcessor`获得`getEarlyBeanReference`行为，并不要BeanPostProcessor的两个行为中返回新的bean引用，促使去二级缓存取对象

具体demo可见`AbstractAutoProxyCreator#getEarlyBeanReference`：

```java
public abstract class AbstractAutoProxyCreator {
    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        if (!this.earlyProxyReferences.contains(cacheKey)) {
            this.earlyProxyReferences.add(cacheKey);
        }
        // 如果出现了循环依赖，则需要优先暴露
        return wrapIfNecessary(bean, beanName, cacheKey);
    }

    /**
    * Create a proxy with the configured interceptors if the bean is
    * identified as one to proxy by the subclass.
    * @see #getAdvicesAndAdvisorsForBean
    */
    @Override
    public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) throws BeansException {
        if (bean != null) {
            Object cacheKey = getCacheKey(bean.getClass(), beanName);
            if (!this.earlyProxyReferences.contains(cacheKey)) {
                // 如果没有循环依赖，则意味着getEarlyBeanReference并没有返回代理对象，在此刻返回即可
                return wrapIfNecessary(bean, beanName, cacheKey);
            }
        }
        // 出现了循环依赖现象，需要返回提前暴露的代理对象引用，否则会导致注入不一致
        return bean;
    }
}
```

### **业务Bean**

重写了`BeanNameAware`、`BeanFactoryAware`、`InitializingBean`、`DisposableBean`接口

```java
package cn.shiyujun.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class Person implements BeanNameAware, BeanFactoryAware, InitializingBean, DisposableBean {
    private String name;
    private String address;
    private int phone;

    private BeanFactory beanFactory;
    private String beanName;

    public Person() {
        System.out.println(
                "第4步，作用：bean调用构造器进行实例化，调用栈：AbstractAutowireCapableBeanFactory#createBeanInstance()，调用时机：InstantiationBeanPostProcessor#postProcessorBeforeInstantiation()之后");
    }

    public String getName() {
        return name;
    }

    public Person setName(String name) {
        this.name = name;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public Person setAddress(String address) {
        this.address = address;
        return this;
    }

    public int getPhone() {
        return phone;
    }

    public Person setPhone(int phone) {
        this.phone = phone;
        return this;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println(
                "第9步，方法名：BeanNameAware.setBeanName()，作用：bean注入其他属性，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeAwareMethods()，调用时机：bean注入属性完毕后");
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        System.out.println(
                "第10步，方法名：BeanNameAware.setBeanFactory()，作用：bean注入其他属性，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeAwareMethods，调用时机：bean注入beanName之后");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println(
                "第12步，方法名：InitializingBean.afterPropertiesSet()，作用：在调用custom的init方法之前，做一些操作，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeInitMethods()，调用时机：bean注入beanFactory之后");
    }

    public void myInit() {
        System.out.println(
                "第13步，作用：bean调用自定义的init-method，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeInitMethods()，调用时机：afterPropertiesSet之后");
    }

    @Override
    public void destroy() throws Exception {
        System.out.println(
                "第15步，方法名：DisposableBean.destroy，作用：销毁bean，删除三级缓存中的自己，调用栈：AbstractApplicationContext#destroyBeans()，调用时机：容器关闭");
    }

    public void myDestroy() {
        System.out.println(
                "第16步，方法名：destroy-method，作用：销毁bean，自定义销毁逻辑，调用栈：AbstractApplicationContext#destroyBeans()，调用时机：容器关闭");
    }
}
```

### **sysout 结果**

> 以下输出内容不包括`八股文版本`的beanDefinition加载，以及一些beanProcessor的bean实例化过程

`第1步，BeanFactoryPostProcessor通过构造器实例化，调用时机：容器初始化，bean加载前`

*15:53:32.600 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Eagerly caching bean 'beanFactoryPostProcessor' to allow for resolving potential circular references
15:53:32.602 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Finished creating instance of bean 'beanFactoryPostProcessor'*

`第2步，方法名：MyBeanFactoryPostProcessor.postProcessBeanFactory，作用：在bean实例化前，对其定义进行修改，调用栈：ApplicationContext.refresh()#invokeBeanFactoryPostProcessors()，调用时机：beanFactoryProcessor的bean加载后，业务bean加载前`

*15:53:32.603 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'beanPostProcessor'
15:53:32.603 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating instance of bean 'beanPostProcessor'
15:53:32.604 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Eagerly caching bean 'beanPostProcessor' to allow for resolving potential circular references
15:53:32.604 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Finished creating instance of bean 'beanPostProcessor'
15:53:32.604 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'instantiationAwareBeanPostProcessor'
15:53:32.605 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating instance of bean 'instantiationAwareBeanPostProcessor'
15:53:32.605 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Eagerly caching bean 'instantiationAwareBeanPostProcessor' to allow for resolving potential circular references
15:53:32.605 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Finished creating instance of bean 'instantiationAwareBeanPostProcessor'
15:53:32.607 [main] DEBUG org.springframework.context.support.ClassPathXmlApplicationContext - Unable to locate MessageSource with name 'messageSource': using default [org.springframework.context.support.DelegatingMessageSource@6b8ca3c8]
15:53:32.609 [main] DEBUG org.springframework.context.support.ClassPathXmlApplicationContext - Unable to locate ApplicationEventMulticaster with name 'applicationEventMulticaster': using default [org.springframework.context.event.SimpleApplicationEventMulticaster@2cd2a21f]
15:53:32.611 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Pre-instantiating singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@6989da5e: defining beans [beanPostProcessor,instantiationAwareBeanPostProcessor,beanFactoryPostProcessor,person]; root of factory hierarchy
15:53:32.611 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Returning cached instance of singleton bean 'beanPostProcessor'
15:53:32.611 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Returning cached instance of singleton bean 'instantiationAwareBeanPostProcessor'
15:53:32.611 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Returning cached instance of singleton bean 'beanFactoryPostProcessor'
15:53:32.612 [main] DEBUG org.springframework.context.support.ClassPathXmlApplicationContext - Unable to locate LifecycleProcessor with name 'lifecycleProcessor': using default [org.springframework.context.support.DefaultLifecycleProcessor@72cde7cc]
15:53:32.612 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Returning cached instance of singleton bean 'lifecycleProcessor'
15:53:32.617 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Could not find key 'spring.liveBeansView.mbeanDomain' in any property source*

`容器初始化完成`

*15:53:32.627 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'person'
15:53:32.627 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating instance of bean 'person'*

`第3步，方法名：MyBeanAwarePostProcessor.postProcessBeforeInstantiation，作用：作为bean实例化前的扩展点，调用栈：AbstractAutowireCapableBeanFactory#createBean()，调用时机：bean实例化之前调用`

`第4步，作用：bean调用构造器进行实例化，调用栈：AbstractAutowireCapableBeanFactory#createBeanInstance()，调用时机：InstantiationBeanPostProcessor#postProcessorBeforeInstantiation()之后`

*15:53:32.628 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Eagerly caching bean 'person' to allow for resolving potential circular references*

`第5步，方法名：MyBeanAwarePostProcessor.postProcessAfterInstantiation，作用：作为bean实例化后的扩展点，调用栈：AbstractAutowireCapableBeanFactory#populateBean()，调用时机：bean实例化之后`

`第6步，作用：bean进行自动装配的bean获取，调用栈：AbstractAutowireCapableBeanFactory#populateBean()，调用时机：bean实例化之后`

`第7步，方法名：MyBeanAwarePostProcessor.postProcessPropertyValues()，作用：在bean注入属性前的扩展点，调用栈：AbstractAutowireCapableBeanFactory#populateBean()，调用时机：bean注入@AutoWired的bean之后`

`第8步，作用：bean注入其他属性，调用栈：AbstractAutowireCapableBeanFactory#populateBean()#applyPropertyValues，调用时机：bean注入@AutoWired的bean之后`

`第9步，方法名：BeanNameAware.setBeanName()，作用：bean注入其他属性，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeAwareMethods()，调用时机：bean注入属性完毕后`

`第10步，方法名：BeanNameAware.setBeanFactory()，作用：bean注入其他属性，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeAwareMethods，调用时机：bean注入beanName之后`

`第11步，方法名：MyBeanPostProcessor.postProcessBeforeInitialization，作用：在bean调用自定义的init方法前进行扩展，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()，调用时机：在bean调用了BeanNameAware和BeanFactoryAware之后`

*15:53:32.672 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Invoking afterPropertiesSet() on bean with name 'person'*

`第12步，方法名：InitializingBean.afterPropertiesSet()，作用：在调用custom的init方法之前，做一些操作，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeInitMethods()，调用时机：bean注入beanFactory之后`

*15:53:32.672 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Invoking init method  'myInit' on bean with name 'person'*

`第13步，作用：bean调用自定义的init-method，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()#invokeInitMethods()，调用时机：afterPropertiesSet之后`

`第14步，方法名：MyBeanPostProcessor.postProcessAfterInitialization，作用：在bean调用自定义的init方法之后进行扩展，调用栈：AbstractAutowireCapableBeanFactory#initializeBean()，调用时机：在bean调用了BeanNameAware和BeanFactoryAware之后`

*15:53:32.673 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Finished creating instance of bean 'person'*

实例详情：cn.shiyujun.lifecycle.Person@6340e5f0

`开始关闭容器`

*15:53:32.673 [main] INFO org.springframework.context.support.ClassPathXmlApplicationContext - Closing org.springframework.context.support.ClassPathXmlApplicationContext@3439f68d: startup date [Thu Sep 02 15:53:32 CST 2021]; root of context hierarchy
15:53:32.673 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Returning cached instance of singleton bean 'lifecycleProcessor'
15:53:32.674 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Destroying singletons in org.springframework.beans.factory.support.DefaultListableBeanFactory@6989da5e: defining beans [beanPostProcessor,instantiationAwareBeanPostProcessor,beanFactoryPostProcessor,person]; root of factory hierarchy
15:53:32.674 [main] DEBUG org.springframework.beans.factory.support.DisposableBeanAdapter - Invoking destroy() on bean with name 'person'*

`第15步，方法名：DisposableBean.destroy，作用：销毁bean，删除三级缓存中的自己，调用栈：AbstractApplicationContext#destroyBeans()，调用时机：容器关闭`

*15:53:32.674 [main] DEBUG org.springframework.beans.factory.support.DisposableBeanAdapter - Invoking destroy method 'myDestroy' on bean with name 'person'*

`第16步，方法名：destroy-method，作用：销毁bean，自定义销毁逻辑，调用栈：AbstractApplicationContext#destroyBeans()，调用时机：容器关闭`

# 参考
- [Spring 循环依赖时，对需要AOP中增强的类如何处理？](https://blog.csdn.net/NEW_BUGGER/article/details/106325961)：AbstractAutoProxyBeanProcessor
- [bean生命周期](https://www.cnblogs.com/zrtqsk/p/3735273.html)：内容不严谨，问题百出，但是评论区值得一看