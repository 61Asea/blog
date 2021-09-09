# **Spring：使用AOP可能会遇到的问题**

## **1. 可能使得synchronized不符合预期互斥效果**

场景：有以下代码通过启动1000个线程，为id为1的实体对象的属性进行递增操作，假设初始值为0，则希望得到结果值为1000

```java
@RestController
public class EmployController {
    @Service
    private EmployService employService;

    @RequestMapping("/add")
    public void add() {
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> employService.increaseAge()).start();
        }
    }
}

@Service
public class EmployService {
    @Autowired
    private EmployRepository employRepository;

    // @Transactional：L通过Spring AOP将数据库事务操作织入到业务中
    @Transactional
    // synchronized：预期通过互斥量使得多个线程串行执行
    public synchronized void increaseAge() {
        Employee employee = employRepository.findById(1);
        employee.setAge(employee.getAge() + 1);
        employRepository.save(employee);
    }
}
```

做法：为了防止线程并发问题而采取synchronized，以期望`每个线程的事务执行到提交是串行的`

结果：employee的age可能会小于1000

原因：以上的代码并不能符合事务串行执行并提交的预期，synchronized代码块的生效区域并没有覆盖整个事务操作，多个线程可能会读到相同的age值进行操作

> **该同步方法是增强对象执行的其中一步**，synchronized即并未包含事务的提交操作

```java
// TransactionAspectSupport.class
protected Object invokeWithTransaction(Method method, Class<?> targetClass, final InvocationCallbck invocation) throws Throwable {
    // ...

    // 开启事务
    TranscationInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
    Object retVal = null;
    try {
        // 调用target（被代理对象）的方法
        retVal = invocation.proceedWithInvocation();
    } catch (Throwable ex) {
        completeTransacationAfterThrowing(txInfo, ex);
        throw ex;
    } finally {
        cleanupTransactionInfo(txInfo);
    }
    // 提交事务
    commitTransactionAfterReturning(txInfo);
    return retVal;
}
```

解决方案：将synchronized的生效区域扩大覆盖到增强对象的整个执行周期

```java
@Service
public class EmployService {
    @Autowired
    private EmployRepository employRepository;

    @Transactional
    // 去掉target方法的互斥量，没用
    public void increaseAge() {
        Employee employee = employRepository.findById(1);
        employee.setAge(employee.getAge() + 1);
        employRepository.save(employee);
    }
}

@RestController
public class EmployController {
    @Service
    private EmployService employService;

    private static Object mutexObject;

    @RequestMapping("/add")
    public void add() {
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> {
                // 互斥量覆盖到整个代理对象方法的执行
                synchronized (mutexObject) {
                    employService.increaseAge();
                }
            }).start();
        }
    }
}
```

## **2. 自身引用获取问题**

> [《详解Spring中Bean的this调用导致AOP失效的原因》](https://my.oschina.net/guangshan/blog/1807721)



## **3. @Configuration对@bean动态代理**



# 参考
- [SpringAOP会导致的一些问题](https://www.jianshu.com/p/5fd84480c43f)
- [@Configuration 和 @Component 区别](https://blog.csdn.net/isea533/article/details/78072133)
- [详解Spring中Bean的this调用导致AOP失效的原因](https://my.oschina.net/guangshan/blog/1807721)