# Tomcat：Container

engine（container）的启动通过容器顶层service的`init()`和`start()`驱动，各个容器都秉承LifecycleBase对容器生命周期的管理方式

tomcat的结构都通过`逐级调用`的方式进行初始化，在container之前都通过代码`逐级指定`，container之后则使用配置文件来逐级寻找子节点

> 除了最顶层容器（Engine）的init是被Service调用的，子容器（Host,Context,Wrapper）的init方法并不是此时在容器中循环调用的，而是在执行start方法时通过状态判断调用的

```java
public abstract class ContainerBase extends LifecycleBeanBase implements Container {
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        // ...

        // 启动Cluster
        Cluster cluster = getClusterInternal();
        if (cluster instanceof Lifecycle) {
            ((Lifecycle) cluster).start();
        }

        // 启动Realm
        Realm realm = getRealmInternal();
        if (realm instanceof Lifecyle) {
            ((Lifecycle) realm).start();
        }

        // 往下搜索下一级的子容器，在此处会搜索到Host子容器
        Container children[] = findChidren();
        List<Future<Void>> results = new ArrayList<>();
        for (int i = 0; i < children.length; i++) {
            // StartChild的run()方法实现，就是执行容器的start()方法
            results.add(startStopExecutor.submit(new StartChild(children[i])));
        }

        MultiThrowable multiThrowable = null;

        for (Future<Void> result : results) {
            try {
                // 阻塞调用
                result.get();
            } catch (Throwable e) {
                log.error(sm.getString("containerBase.threadedStartFailed"), e);
                if (multiThrowable == null) {
                    multiThrowable = new MultiThrowable();
                }
                multiThrowable.add(e);
            }
        }

        if (multiThrowable != null) {
            throw new LifecycleException(sm.getString("containerBase.threadedStartFailed"),
                    multiThrowable.getThrowable());
        }

        // 启动pipeline，会联合各个容器进行组装
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).start();
        }

        setState(LifecycleState.STARTING);

        // 启动热部署异步线程
        threadStart();
    }
}
```



# 参考
- [tomcat-container源码分析](https://blog.csdn.net/it_freshman/article/details/81609998)