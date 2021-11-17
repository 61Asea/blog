# tomcat：filter

web.xml元素执行的顺序为：filters -> servlet

> 误区：servlet有拦截器

答：servlet整个体系本身只有filter和servlet两个概念，这两者通过组成了`ApplicationFilterChain`，而拦截器是由mvc框架spring mvc、structs2等实现提供

补充：structs是基于ApplicationFilterChain的filter实现，spring mvc是基于ApplicationFilterChain的servlet实现，即我们称这些mvc框架都是基于servlet体系实现的

```java
public final class ApplicationFilterFactory {
    private ApplicationFilterFactory() {}

    // Wrapper容器处理业务逻辑的入口
    public static ApplicationFilterChain createFilterChain(ServletRequest request, Wrapper wrapper, Servlet servlet) {
        DispatcherType dispatcher = null;
        if (request.getAttribute(Globals.DISPATCHER_TYPE_ATTR) != null) {
            // 获取处理的类型，值一般为REQUEST，DISPATCHER_TYPE_ATTR = "org.apache.catalina.core.DISPATCHER_TYPE"
            dispatcher = (DispatcherType) request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);
        }
        String requestPath = null;
        // 获取请求的路径值，DISPATCHER_REQUEST_PATH_ATTR = "org.apache.catalina.core.DISPATCHER_REQUEST_PATH"
        Object attribute = request.getAttribute(Globals.DISPATHCER_REQUEST_PATH_ATTR);
        if (attribute != null) {
            requestPath = attribute.toString();
        }

        if (servlet == null)
            return (null);

        boolean comet = false;
        ApplicationFilterChain filterChain;
        if (request instanceof Request) {
            // 转了个寂寞
            Request req = (Request) request;
            comet = req.isComet();
            if (Globals.IS_SECURITY_ENABLED) {
                filterChain = new ApplicationFilterChain();
                if (comet) {
                    req.setFilterChain(filterChain);
                }
            } else {
                // http下，一般在request构造时就有了一条半成型的过滤器链
                filterChain = (ApplicationFilterChain) req.getFilterChain();
                if (filterChain == null) {
                    filterChain = new ApplicationFilterChain();
                    req.setFilterChain(filterChain);
                }
            }
        } else {
            filterChain = new ApplicationFilterChain();
        }

        // 过滤链设置servlet属性
        filterChain.setServlet(servlet);

        // 过滤链设置servlet的实例支持，该支持可以辅助进行事件处理，类似于netty.pipeline的fire机制
        filterChain.setSupport(((StandardWrapper) wrapper).getInstanceSupport());

        StandardContecx context = (StandardContext) wrapper.getParent();
        FilterMap filterMaps[] = context.findFilterMaps();

        if ((filterMaps == null) || (filterMaps.length == 0))
            // context中没有配置其他过滤器的话，则大概率会在这直接返回
            return (filterChain);

        String servletName = wrapper.getName();

        // Add the relevant path-mapped filters to this filter chain
        // 1. 先将相关请求路径映射的filter添加到该链中
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i], dispatcher)) {
                continue;
            }

            // 判断当前请求路径是否与过滤器的URL相匹配
            if (!matchFiltersURL(filterMaps[i], requestPath))
                continue;

            // 匹配则获取filterConfig类，这里的filterConfig的init()方法都被调用了
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig) context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                continue;
            }

            boolean isCometFilter = false;
            if (comet) {
                try {
                    isCometFilter = filterConfig.getFilter() instanceof CometFilter;
                } catch (Exception e) {
                    // ...
                }
                if (isCometFilter) {
                    // 添加到链中
                    filterChain.addFilter(filterConfig);
                }
            } else {
                filterChain.addFilter(filterConfig);
            }
        }

        // Add filters that match on servlet name second
        // 2. 添加与servlet名称相匹配的filter
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i], dispatcher)) {
                continue;
            }

            // 与servlet的名称匹配筛选
            if (!matchFiltersServlet(filterMaps[i], servletName))
                continue;

            // 与上面添加filter到链中一样的逻辑
        }

        return (filterChain);
}
```

filterChain.doFilter()：

```java
public class ApplicationFilterChain implements FilterChain, CometFilterChain {
    // 配置的过滤器链
    private ApplicationFilterConfig[] filters = new ApplicationFIlterConfig[0];

    // 过滤器链当前正在执行的索引
    private int pos = 0;

    // 过滤器链的filter个数
    private int n = 0;

    // 真正的业务逻辑
    private Servlet servlet = null;

    private InstanceSupport support = null;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
        if (Globals.IS_SECURITY_ENABLED) {
            // ...
        } else {
            internalDoFilter(request, response);
        }
    }

    // filter生命周期：doFilter() -> destory()
    private void internalDoFilter(ServletRequest request, ServletResponse response) {
        // Call the next filter if there is one
        if (pos < n) {
            // 先处理filter数组，将pos往后挪动
            ApplicationFilterConfig filterConfig = filters[pos++];
            Filter filter = null;
            try {
                filter = filterConfig.getFilter();
                // 1. 调用listener方法，通过传递BEFORE_FILTER_EVENT来触发
                support.fireInstanceEvent(InstanceEvent.BEFORE_FILTER_EVENT, filter, request, response);

                // ...
                if (Globals.IS_SECURITY_ENABLED) {
                    final ServletRequest req = request;
                    final ServletResponse res = response;
                    Principal principal = ((HttpServletRequest) req).getUserPrincipal();
                    Object[] args = new Object[]{req, res, this};
                    SecurityUtil.doAsPrivilege("doFilter", filter, classType, args, principal);
                } else {
                    // 2. 调用filter的doFilter方法
                    filter.doFilter(request, response, this);
                }
                // 3. 调用listener方法，通过传递AFTER_FILTER_EVENT来触发
                support.fireInstanceEvent(InstanceEvent.AFTER_FILTER_EVENT, filter, request, response);
            } catch (...) {
                // ...
            }
            return;
        }

        // We fell off the end of the chain -- call the servlet instance
        try {
            if (ApplicationDispatcher.WRAP_SAME_OBJECT) {
                lastServicedRequest.set(request);
                lastServicedResponse.set(response);
            }

            support.fireInstanceEvent(InstanceEvent.BEFORE_SERVICE_EVENT,
                                      servlet, request, response);
            if (request.isAsyncSupported()
                    && !support.getWrapper().isAsyncSupported()) {
                request.setAttribute(Globals.ASYNC_SUPPORTED_ATTR,
                        Boolean.FALSE);
            }
            // Use potentially wrapped request from this point
            if ((request instanceof HttpServletRequest) &&
                (response instanceof HttpServletResponse)) {

                if( Globals.IS_SECURITY_ENABLED ) {
                    final ServletRequest req = request;
                    final ServletResponse res = response;
                    Principal principal =
                        ((HttpServletRequest) req).getUserPrincipal();
                    Object[] args = new Object[]{req, res};
                    SecurityUtil.doAsPrivilege("service",
                                               servlet,
                                               classTypeUsedInService,
                                               args,
                                               principal);
                } else {
                    servlet.service(request, response);
                }
            } else {
                servlet.service(request, response);
            }
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                                      servlet, request, response);
        } catch (...) {
            // ...
        } finally {
            // ...
        }
    }
}
```

filter生命周期：

- init：在context构造该filter的filterConfig类时，就已经被调用
- doFilter：请求处理过程调用
- destroy：请求处理结束，删除请求链时调用

# 参考
- [Servlet中的过滤器Filter详解](https://www.oschina.net/question/565065_86538)

# 重点参考
- [servlet有拦截器吗？](https://www.zhihu.com/question/65303725)：servlet体系、servlet模块、filter模块概念理清，ApplicationFilterChain模块集成servlet、filter模块，而我们常说的servlet指的是servlet体系，即指的ApplicationFilterChain