## **Nacos**

根据以下文章入门：
- [Nacos整体概括](https://www.jianshu.com/p/3f4f6554b67c)

sxdpz项目预期使用Nacos的两个功能：配置中心、注册中心
- nacos-config: 配置中心
- nacos-console: 控制台，界面操作
- nacos-naming：注册中心

![官网基础结构图](https://upload-images.jianshu.io/upload_images/20913719-b9f22875236c0fcd.jpeg?imageMogr2/auto-orient/strip|imageView2/2/w/1100/format/webp)

服务端通过OpenAPI对外提供接口调用，内部有Config Server与Naming Service两个服务，底层通过Nacos-Core作为基础设施建设

当启动Nacos启动类时，会在服务端启动config，naming，console项目

### **一、配置中心**
在这里要区分好，Nacos配置中心集群 -> Nacos客户端(游戏服务端/Nacos-Console)的关系

#### **1. nacos-console客户端**

官方客户端例子：

    Thread.sleep(3000);
    content = configService.getConfig(dataId, group, 5000);
    System.out.println(content);

    boolean isRemoveOk = configService.removeConfig(dataId, group);
    System.out.println(isRemoveOk);
    Thread.sleep(3000);

    content = configService.getConfig(dataId, group, 5000);
    System.out.println(content);
    Thread.sleep(300000);

#### **NacosConfigService (implements ConfigService)**

    //根据dataId,group获取配置信息
    String content = configService.getConfig(dataId, group, 5000);

通过configService的getConfig()方法，通过注册的监听器个数/ **3000（默认值）** 并向上取整分批次处理

    //添加监听器
    configService.addListener(dataId, group, new Listener() {
        @Override
        public void receiveConfigInfo(String configInfo) {
            System.out.println("receive:" + configInfo);
        }

        @Override
        public Executor getExecutor() {
            return null;
        }
    });

通常情况下直接调用文件，如果本地文件不存在则通过Get方法调用服务端，/nacos/v1/configs获取配置
- FailoverFile

        // 优先使用本地配置
        String content = LocalConfigInfoProcessor.getFailover(agent.getName(), dataId, group, tenant);

- HttpGet

        String[] ct = worker.getServerConfig(dataId, group, tenant, timeoutMs);
        
        getServerConfig() -> 
        
        result = agent.httpGet(Constants.CONFIG_CONTROLLER_PATH, null, params, agent.getEncode(), readTimeout);

    httpGet工具对获取调用进行熔断处理，通过不大于超时时间，以及maxRetry最大次数递减，进行do-while循环。
    
    1. 在每一次循环都未获取到可用服务时，会重新调用权重迭代器（进行刷新服务列表）

            maxRetry--;
            if (maxRetry < 0) {
                throw new ConnectException(
                        "[NACOS HTTP-GET] The maximum number of tolerable server reconnection errors has been reached");
            }
            serverListMgr.refreshCurrentServerAddr();

    2. 若能成功获得result，则直接保存到快照中

            case HttpURLConnection.HTTP_OK(200):
            LocalConfigInfoProcessor.saveSnapshot(agent.getName(), dataId, group, tenant, result.getData());

            case HttpURLConnection.HTTP_NOT_FOUND(404):
            LocalConfigInfoProcessor.saveSnapshot(agent.getName(), dataId, group, tenant, null);

- Snapshot

    第二步有返回的时候保存快照，如果第二步没有返回则直接返回快照信息
  

#### **2. nacos-config服务端**

#### 配置获取：

    /* Config服务restFul接口: /v2/configs */ 
    @GetMapping
    public void getConfig(...) {
        // ...

        inner.doGetConfig(request, response, dataId, group, tenant, tag, clientIp);
    }

    /* ConfigServletInner.doGetConfig: */
    md5 = cacheItem.getMd5();
    lastModified = cacheItem.getLastModifiedTs();
    // 这里就是单机模式，并且没有使用mysql才会从derby中获取
    if (PropertyUtil.isDirectRead()) {
        configInfoBase = persistService.findConfigInfo(dataId, group, tenant);
    } else {
        file = DiskUtil.targetFile(dataId, group, tenant);
    }
    if (configInfoBase == null && fileNotExist(file)) {
        // FIXME CacheItem
        // No longer exists. It is impossible to simply calculate the push delayed. Here, simply record it as - 1.
        ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1,
                ConfigTraceService.PULL_EVENT_NOTFOUND, -1, requestIp);
        
        // pullLog.info("[client-get] clientIp={}, {},
        // no data",
        // new Object[]{clientIp, groupKey});
        
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().println("config data not exist");
        return HttpServletResponse.SC_NOT_FOUND + "";
    }

    根据单机策略和存储策略（emmed或extral），若为单机&&derby方式，则sql查询；否则进行本地文件读取
        - 减少mysql等调用次数
        - 减少主服务器的调用次数

#### 配置更新

    /* 发布配置 */
    boolean isPublishOk = configService.publishConfig(dataId, group, "content");
    System.out.println(isPublishOk);

    /* ConfigController的publishConfig方法 */
    if (StringUtils.isBlank(tag)) {
        persistService.insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, true);
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent(false, dataId, group, tenant, time.getTime()));
    } else {
        // 这里就是简单的 数据库持久化（derby/mysql）
        persistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser, time, true);

        // 发布订阅模式
        ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
    }

通知过程采用发布订阅模式，发布者DefaultPublish类继承了线程对start方法进行了重写

发布时间通过类名作为类型，配置为ConfigDataChangeEvent类型

通过NotifyCenter根据主题类型取出发布者，调用publish方法将事件加入到发布者对象的阻塞队列中

    // DefaultPublisher.class
    // 发布到阻塞队列
    @Override
    public boolean publish(Event event) {
        checkIsStart();
        boolean success = this.queue.offer(event);
        if (!success) {
            LOGGER.warn("Unable to plug in due to interruption, synchronize sending time, event : {}", event);
            receiveEvent(event);
            return true;
        }
        return true;
    }

    // 阻塞队列消费
    void openEventHandler() {
        // ...
        final Event event = queue.take();
        // 通知订阅者
        receiveEvent(event);
    }

    receiveEvent(Event event) {
        // Subscriber => DumpConfigHandler
        // subscriber通过AsyncNotifyService自动注入进行注册
        for (Subscriber subscriber : subscribers) {
            //... 

            notifySubscriber(subscriber, event); -> subscriber.onEvent -> 通知服务列表各个成员的/comunication/dataChange接口
        }
    }

@Service
AsyncNotifyService.class

    // Register A Subscriber to subscribe ConfigDataChangeEvent.
        NotifyCenter.registerSubscriber(new Subscriber() {
            @Override
            public void onEvent(Event event) {
                // Generate ConfigDataChangeEvent concurrently
                if (event instanceof ConfigDataChangeEvent) {
                    ConfigDataChangeEvent evt = (ConfigDataChangeEvent) event;
                    long dumpTs = evt.lastModifiedTs;
                    String dataId = evt.dataId;
                    String group = evt.group;
                    String tenant = evt.tenant;
                    String tag = evt.tag;
                    Collection<Member> ipList = memberManager.allMembers();
                    
                    // In fact, any type of queue here can be
                    Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
                    for (Member member : ipList) {
                        queue.add(new NotifySingleTask(dataId, group, tenant, tag, dumpTs, member.getAddress(),
                                evt.isBeta));
                    }
                    ConfigExecutor.executeAsyncNotify(new AsyncTask(nacosAsyncRestTemplate, queue));
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        
    tips: 注册中心：// subscribers通过ServerMemberManager的registerClusterEvent方法进行注册

DumpService.class
    
    // 往TaskManager加入新任务new DumpTask()
    dumpTaskMgr.addTask(groupKey, new DumpTask(groupKey, tag, lastModified, handleIp, isBeta));

    // TaskManager的task对象加入任务

DumpConfigHandler.class

    // 保存到本地文件中
    result = ConfigCacheService.dump(dataId, group, namespaceId, content, lastModified, type);


通过客户端推送或更新配置时，先更新当前服务的数据，再通过事件触发器进行广播，调用集群每个Member的/comunication/dataChange接口，接口为异步操作，立即返回

其他服务将通过configService.dump()方法将任务加入，并最后通过prossor对执行task的onEvent方法（使用dumpProssor进行装饰）
- 先修改数据库
- 读取库对内存进行更新


# 参考：
- [Nacos官网](https://nacos.io)
- [Nacos整体概括](https://www.jianshu.com/p/3f4f6554b67c)
- [Nacos配置中心](https://www.jianshu.com/p/1dd59c113287)
- [Nacos集群](https://www.jianshu.com/p/d24c11d16ba1)
- [Nacos服务发现](https://www.jianshu.com/p/2cd487f9560a)