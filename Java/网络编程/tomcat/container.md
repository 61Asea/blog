# Tomcat：Container

![container](https://asea-cch.life/upload/2021/11/container-e2ffcb08119b460a9c99ba625bb6bc53.png)

# **初始化与启动**

engine（container）的初始化与启动，分别通过容器顶层service的`init()`和`start()`驱动，各个容器都秉承`LifecycleBase`管理生命周期

tomcat的结构都通过`逐级调用`的方式进行初始化，在container（StandardEngine）之前都通过代码`逐级指定`，之后则通过`LifecycleSupport#fireLifecycleEvent`进行事件驱动

> 除了最顶层容器（Engine）的init是被Service调用的，子容器（Host,Context,Wrapper）都是通过事件驱动对应的XXXConfig类进行动态加载

![container层级关系](https://asea-cch.life/upload/2021/11/container%E5%B1%82%E7%BA%A7%E5%85%B3%E7%B3%BB-2ed9bfd924d640d8bef77007a70c31f5.png)

```java
public abstract class ContainerBase extends LifecycleBeanBase implements Container {
    @Override
    protected void initInternal() throws LifecyleException {
        BlockingQueue<Runnable> startStopQueue = new LinkedBlockingQueue<>();
        // 每个容器都会有专职负责容器组件启动/停止的线程池
        startStopExecutor = new ThreadPoolExecutor(getStartStopThreadsInternal(), getStartStopThreadsInternal(), 10, TimeUnit.SECONDS, startStopQueue, new StartStopThreadFactory(getName() + "-startstop-"));
        // 该参数影响了每个worker的自循环，详情可重新温习线程池
        startStopExecutor.allowCoreThreadTimeout(true);
        super.initInternal();
    }

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

        // 事件驱动，engine、host、context容器都有对应的EngineConfig、HostConfig、ContextConfig类
        setState(LifecycleState.STARTING);

        // 启动热部署异步线程
        threadStart();
    }
}
```

- 构造StandardContext，则使用HostConfig进行动态加载，会对webapps文件夹下的各个子文件夹生成对应context

    ```java
    public class HostConfig implements LifecycleListener {
        @Override
        public void lifecycleEvent(lifecycleEvent event) {
            try {
                host = (Host) event.getLifecycle();
                if (host instanceof StandardHost) {
                    // ...
                }
            } catch (...) {
                // ...
            }

            if (event.getType().equals(Lifecycle.PERIODIC_EVENT)) {
                check();
            } else if (event.getType().equals(Lifecycle.BEFORE_START_EVENT)) {
                beforeStart();
            } else if (event.getType().equals(Lifecycle.START_EVENT)) {
                // 加载工作路径下的webapps文件夹，每一个子文件夹代表一个context
                start();
            } else if (event.getType().equals(Lifecycle.STOP_EVENT)) {
                stop();
            }
        }

        public void start() {
            // ...
            if (host.getDeployOnStartup())
                deployApps();
        }

        protected void deployApps() {
            File appBase = host.getAppBaseFile();
            File configBase = host.getConfigBaseFile();
            String[] filteredAppPaths = filterAppPaths(appBase.list());
            deployDescriptors(configBase, configBase.list());
            deployWARs(appBase, filteredAppPaths);
            // 根据目录部署web应用
            deployDirectories(appBase, filteredAppPaths);
        }

        protected void deployDirectories(File appBase, String[] files) {
            if (files == null)
                return;

            // 使用启动线程池来异步执行部署任务
            ExecutorService es = host.getStartStopExecutor();
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < file.length; i++) {
                if (files[i].equalsIgnoreCase("META-INF"))
                    continue;
                if (files.equalsIgnoreCase("WEB-INF"))
                    continue;
                File dir = new File(appBase, files[i]);
                if (dir.isDirectory()) {
                    // ...
                    results.add(es.submit(new DeployDirectory(this, cn, dir)));
                }
            }

            for (Future<?> result : results) {
                try {
                    result.get();
                } catch(Exception e) {
                    // ...
                }
            }
        }

        protected void deployDirectory(ContextName cn, File dir) {
            Context context = null;
            // META-INF/context.xml
            File xml = new File(dir, Constants.ApplicationContextXml);
            
            // ...

            try {
                if (deployThisXML && xml.exists()) {
                    synchronized (digesterLock) {
                        try {
                            context = (Context) digester.parse(xml);
                        } catch (...) {
                            // ...
                        } finally {
                            digester.reset();
                            if (context == null) {
                                context = new FailedContext();
                            }
                        }
                    }
                }

                // 获取ContextConfig类
                Class<?> clazz = Class.forName(host.getConfigClass());
                LifecycleListener listener = (LifecycleListener) clazz.getConstructor().newInstance();
                // 为context设置ContextConfig监听器，在后续context的startInternal()方法中，也可以触发setState(LifecycleConstans.START)对Wrapper的初始化进行事件驱动
                context.addLifecycleListener(listener);

                context.setName(cn.getName());
                context.setPath(cn.getPath());
                context.setWebappVersion(cn.getVersion());
                context.setDocBase(cn.getBaseName());
                host.addChild(context);
            } catch (...) {
                // ...
            } finally {
                // ...
            }
        }

        private static class DeployDirectory implements Runnable {
            // parent = host
            private HostConfig config;
            // context的名字
            private ContextName cn;
            private File dir;

            public DeployDirectory(HostConfig config, ContextName cn, File dir) {
                this.config = config;
                this.cn = cn;
                this.dir = dir;
            }

            @Override
            public void run() {
                // 返回去调用HostConfig的方法
                config.deployDirectory(cn, dir);
            }
        }
    }
    ```

- 构造StandardWrapper，则使用ContextConfig进行辅助，对context路径下的web.xml文件进行解析获得servlet的对应wrapper

    > Context对Wrapper的启动不是通过Lifecycle.START进行的，即没有通过父类ContainerBase的startInternal()方法

    ```java
    public class ContextConfig implements LifecycleListener {
        // 由StandardContext#startInternal()调用fireLifecycleEvent(Lifecycle.CONFIGURE_START_EVENT)
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            // ...
            if (event.getType().eqauls(Lifecycle.CONFIGURE_START_EVENT)) {
                configureStart();

            // ...
        }

        protected synchronized void configureStart() {
            // ...
            webConfig();
            // ...
        }

        protected void webConfig() {
            WebXml webXml = createWebXml();

            InputSource contextWebXml = getContextWebXmlSource();
            if (!webXmlParser.parseWebXml(contextWebXml, webXml, false)) {
                ok = false;
            }

            // ...

            if (!webXml.isMetadataComplete()) {
                // ...
            } else {
                webXml.merge(defaults);
                // 将jsp解析为servlet
                converJsps(webXml);
                // 解析webXml中的servlet
                configureContext(webXml);
            }

            // ...
        }

        private void configureContext(WebXml webxml) {
            // ...

            // 遍历配置
            for (ServletDef servlet : webXml.getServlets().values()) {
                Wrapper wrapper = context.createWrapper();

                // 设置wrapper属性的代码

                context.addChild(wrapper);
            }

            // 设置Context的Session属性等等
        }
    }
    ```

![container全部子容器的初始化与启动](https://asea-cch.life/upload/2021/11/container%E5%85%A8%E9%83%A8%E5%AD%90%E5%AE%B9%E5%99%A8%E7%9A%84%E5%88%9D%E5%A7%8B%E5%8C%96%E4%B8%8E%E5%90%AF%E5%8A%A8-73a2da3898e94419b1803cc7a78936bf.png)

# **请求数据解析**

请求处理链：

1. CoyoteAdapter：

- 解析从socket读进来的数据，将其从org.apache.coyote.Request转换为符合servlet规范的对象，最终一切的映射都在Mapper类中进行转换

    注意查看StandardEngine的启动方法，里面启动了mapperListener，这个Listener用于setState(START)的触发，将会把tomcat所拥有的Host以MappedHost对象包装注册到Mapper中

    ```java
    public class CoyoteAdapter implements Adapter {
        // 以上各个容器间的调用，都由request指定，所以request对象在构造的时候应有路由计算规则，具体方法见CoyoteAdapter#postParseRequest(req, request,res,response)
        protected boolean postParseRequest(org.apache.coyote.Request req, Request request, org.apache.coyote.Response res, Response response) {
            // ...
            while (mapRequired) {
                // mappingData将在map方法中被填充，最终可获得host、context、wrapper的一对一对一关系
                // host：根据请求URL的ip地址/域名
                // context：根据URI的第一个/后内容，如果只有一个context则默认返回第一个context
                // wrapper：根据之前解析的”/“，继续往后解析得到servlet名称
                connector.getService().getMapper().map(serverName, decodedURI, version, request.getMappingData());
                
                // ...
            }
            // ...
        }
    }
    ```

    map方法通过各个容器的name与请求URI做对比，遍历容器查找，容器包含以下内容：

    - MappedHost对象：包含了旗下的context，以ContextList（MappedContext数组）存在

    - MappedContext对象：具体是ContextVersion数组，每个ContextVersion包含了旗下的wrapper

    > 查找wrapper的规则，先exact match，再prefix match，再extension match

- 将请求下发到容器处理，这个过程同样是同步阻塞的

    ```java
    public class CoyoteAdapter implements Adapter {
        public void service(org.apache.coyote.Request req, org.apache.coyote.Response res) throws Exception {
            Request request = (Request) req.getNote(ADAPTER_NOTES);
            Response response = (Response) res.getNote(ADAPTER_NOTES);

            // ...

            try {
                // 解析请求数据，做适配转换
                postParseSuccess = postParseRequest(req, request, res, response);
                if (postParseSuccess) {
                    // ...

                    // Calling the container
                    connector.getService().getContainer().getPipeline().getFirst().invoke(request, response);
                }

                // ...
            } catch (...) {
                // ...
            }

        }
    }
    ```

2. StandardEngineValue：从request中取出host对象，host.getPipeline().getFirst().invoke() --> StandardHostValue.invoke()

    ```java
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 从request对象中取出host对象
        Host host = request.getHost();
        if (host == null) {
            response.sendError
                (HttpServletResponse.SC_BAD_REQUEST,
                sm.getString("standardEngine.noHost",
                            request.getServerName()));
            return;
        }
        if (request.isAsyncSupported()) {
            request.setAsyncSupported(host.getPipeline().isAsyncSupported());
        }

        // 往下传递
        host.getPipeline().getFirst().invoke(request, response);
    }
    ```

3. StandardHostValue：StandardHostValue从传入的request取出Context，context.getPipeline().getFirst().invoke() --> StandardContextValue.invoke()

    AccessLogValue、ErrorReportValue相继处理，最后再调用StandardHostValue的invoke方法

    ```java
    // StandardHost的pipeline：AccessLogValue(first/basic) -> ErrorReportValue -> StandardHostValue
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 从传入的request取出Context，context.getPipeline().getFirst().invoke() --> StandardContextValue.invoke()
        Context context = request.getContext();
        if (context == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                 sm.getString("standardHost.noContext"));
            return;
        }

        if (request.isAsyncSupported()) {
            request.setAsyncSupported(context.getPipeline().isAsyncSupported());
        }

        boolean asyncAtStart = request.isAsync();
        boolean asyncDispatching = request.isAsyncDispatching();

        try {
            context.bind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);

            if (!asyncAtStart && !context.fireRequestInitEvent(request.getRequest())) {
                return;
            }

            try {
                if (!asyncAtStart || asyncDispatching) {
                    // 调用context的pipeline
                    context.getPipeline().getFirst().invoke(request, response);
                } else {
                    if (!response.isErrorReportRequired()) {
                        throw new IllegalStateException(sm.getString("standardHost.asyncStateError"));
                    }
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                container.getLogger().error("Exception Processing " + request.getRequestURI(), t);
                if (!response.isErrorReportRequired()) {
                    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
                    throwable(request, response, t);
                }
            }

            response.setSuspended(false);

            Throwable t = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

            if (!context.getState().isAvailable()) {
                return;
            }

            if (response.isErrorReportRequired()) {
                if (t != null) {
                    throwable(request, response, t);
                } else {
                    status(request, response);
                }
            }

            if (!request.isAsync() && !asyncAtStart) {
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } finally {
            if (ACCESS_SESSION) {
                request.getSession(false);
            }

            context.unbind(Globals.IS_SECURITY_ENABLED, MY_CLASSLOADER);
        }
    }
    ```

4. StandardContextValue：从request中取出Wrapper，wrapper.getPipeline().getFirst().invoke() --> StandardWrapperValue.invoke()

    ```java
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        MessageBytes requestPathMB = request.getRequestPathMB();
        if ((requestPathMB.startsWithIgnoreCase("/META-INF/", 0))
                || (requestPathMB.equalsIgnoreCase("/META-INF"))
                || (requestPathMB.startsWithIgnoreCase("/WEB-INF/", 0))
                || (requestPathMB.equalsIgnoreCase("/WEB-INF"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 取出对应的servlet
        Wrapper wrapper = request.getWrapper();
        if (wrapper == null || wrapper.isUnavailable()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            response.sendAcknowledgement();
        } catch (IOException ioe) {
            container.getLogger().error(sm.getString(
                    "standardContextValve.acknowledgeException"), ioe);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        if (request.isAsyncSupported()) {
            request.setAsyncSupported(wrapper.getPipeline().isAsyncSupported());
        }
        // 调用wrapper进行处理
        wrapper.getPipeline().getFirst().invoke(request, response);
    }
    ```

5. StandardWrapperValue：调用filterChain.doFilter(request, response)

    ```java
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {
        // ...

        // 获取父类
        Context context = (Context) wrapper.getParent();

        // ...

        try {
            if (!unavailable) {
                // 若为被初始化过，则进行初始化，所以servlet是单例
                servlet = wrapper.allocate();
            }
        } catch (....) {
            // ...
        }

        // ...

        // 通过ApplicationFilterFactory.createFilterChain(request, wrapper, servlet)生成servlet拦截链
        ApplicationFilterChain filterChain =
                ApplicationFilterFactory.createFilterChain(request, wrapper, servlet);
        try {
            if ((servlet != null) && (filterChain != null)) {
                if (context.getSwallowOutput()) {
                    try {
                        SystemLogHandler.startCapture();
                        if (request.isAsyncDispatching()) {
                            request.getAsyncContextInternal().doInternalDispatch();
                        } else if (comet) {
                            filterChain.doFilterEvent(request.getEvent());
                        } else {
                            // 传递到servlet的filter链，进行真正的业务处理
                            filterChain.doFilter(request.getRequest(),
                                    response.getResponse());
                        }
                    } finally {
                       // ...
                    }
                }
                // ...
            }
        } catch (... ) {
            // ...
        }

        // 释放掉这次请求的拦截链
        if (filterChain != null) {
            if (request.isComet()) {
                filterChain.reuse();
            } else {
                filterChain.release();
            }
        }

        // 回收servlet
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            // ...
        }

        // ...
    }
    ```

# **servlet**

## **单例/多例**

> [最佳实践: 勿在 Servlet 中实现 SingleThreadModel](https://www.cnblogs.com/soundcode/p/6296519.html)

单例：不实现SingleThreadModel接口，每个请求共用同一个servlet实例

多例：Servlet实现SingleThreadModel接口的话，容器会为每一个请求都创建一个新的Servlet实例，这样每个servlet都有自己独立的变量

> 虽然可以使用SingleThreadModel，即多例模式来解决多线程并发操作servlet导致线程安全的问题，但这种方式会导致大量的servlet对象被创建销毁，产生极大开销

## **生命周期**

![servlet生命周期](https://asea-cch.life/upload/2021/11/servlet%E7%94%9F%E5%91%BD%E5%91%A8%E6%9C%9F-d248dc5c017b49829bff5c272bcca0a7.jpg)

`生命周期`：初始化、service()调用、销毁，共三个阶段

正常逻辑：在请求对应servlet时进行初始化，然后常驻在内存中，在最终服务器关闭时被销毁

## **初始化**

`层级关系（初始化时机）`：tomcat对于servlet的层级关系通过\<load-on-startup>来进行指定，这个层级属性决定servlet是否应该在**容器启动时初始化**以及**初始化的顺序**：

```java
public class StandardWrapper extends ContainerBase {
    // 对应xml配置文件中的<load-on-startup>属性，这是一个层级属性
    protected int loadOnStartUp = -1;

    public int getLoadOnStartUp() {
        return loadOnStartUp;
    }
}
```

具体的Wrapper加载(class load)、初始化(newInstance)、构造(init)逻辑在StandardContext中，所以servlet的初始化过程具备有**两个时机**：

- StandardContext#startInternal()：容器启动

    ```java
    public class StandardContext extends ContainerBase {
        // 前一个调用栈是StandardContext#startInternal()
        public boolean loadOnStartUp(Container children[]) {
            TreeMap<Integer, ArrayList<Wrapper>> map = new TreeMap<>();
            for (int i = 0; i < children.length; i++) {
                Wrapper wrapper = (Wrapper) children[i];
                int loadOnStartup = wrapper.getLoadOnStartup();
                // load-on-startup配置的值如果为负数，则不会立即被加载
                if (loadOnStartup < 0)
                    continue;
                Integer key = Integer.valueOf(loadOnStartup);
                ArrayList<Wrapper> list = map.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(key, list);
                }
                list.add(wrapper);
            }

            // 按照TreeMap顺序进行load，则servlet会根据int值从小到大遍历
            for (ArrayList<Wrapper> list : map.values()) {
                for (Wrapper wrapper : list) {
                    try {
                        // 对load-on-startup大于0的servlet进行启动加载
                        wrapper.load();
                    } catch (ServletException e) {
                    // ...
                    }
                }
            }
            return true;
        }
    }
    ```

    ```java
    public class StandardWrapper extends ContainerBase {
        @Override
        public synchronized void load() throws ServletException {
            // 调用loadServlet()获取到servlet的实例
            instance = loadServlet();

            if (!instanceInitialized) {
                // 调用实例的init()方法
                initServlet(instance);
            }

            // ...
        }
    }
    ```

- StandardWrapperValue#invoke(Request, Response)：客户端请求

    没有配置load-on-startup属性的servlet，默认值都为-1，所以在容器启动时都不会被实例化

    ```java
    public class StandardWrapper extends ContainerBase {
        @Override
        // 具体见上代码调用wrapper.allocate()
        public Servlet allocate() throws ServletException {
            if (unloading) {
                throw new ServletException(sm.getString("standardWrapper.unloading", getName()));
            }
            boolean newInstance = false;
            if (!singleThreadModel) {
            if (instance == null || !instanceInitialized) {
                synchronized (this) {
                    if (instance == null) {
                        try {
                            // 获得servlet的实例对象
                            instance = loadServlet();
                            newInstance = true;
                        } catch (...) {
                            // ...
                        }
                    }
                    if (!instanceInitialized) {
                        // 调用实例的init()方法
                        initServlet(instance);
                    }
                }
            }

            // ...
        }
    }
    ```

无论是哪个时机初始化了servlet，最终都会进入相似的逻辑：获取servlet instance（StandardWrapper.loadServlet()），再调用实例的init()方法（initServlet(instance)）

> 如果servlet是容器启动时已初始化完毕，当在请求到达StandardWrapperValue时，会直接从Wrapper中返回instance

## **service()调用**

详情见上请求解析过程

## **销毁**

一般是容器关闭的时候，由容器顶层的关闭事件往下传递，最终调用到servlet的destroy()方法

# **总结**

合理运用门面模式，将字节流数据读取完毕后，传入到tomcat的Request对象中，再通过Adapter类（门面）将该对象与Servlet的Request对象进行转换解析，Servlet用户将无需关注底层网络实现原理

# 参考
- [tomcat-container源码分析](https://blog.csdn.net/it_freshman/article/details/81609998)