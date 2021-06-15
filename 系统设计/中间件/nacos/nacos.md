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

#### **1. nacos-console客户端，获取配置**

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
  

#### **2. nacos-config服务端：返回配置、修改配置通知**

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
AsyncNotifyService.class, 自动注入的时候注册subscriber

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

AsyncTask.class

    // task.url为/comunicate/dataChange接口，用Get方式调用
    restTemplate.get(task.url, header, Query.EMPTY, String.class, new AsyncNotifyCallBack(task));

CommunicationController.class和DumpService.class

    CommunicationController.class
    @GetMapping("/dataChange")
    public Boolean notifyConfigInfo() {
        // ...
        dumpService.dump(dataId, group, tenant, tag, lastModifiedTs, handleIp);
    }

    =>

    DumpService.class
    public DumpService() {
        **this.processor = new DumpProcessor(this);**
        this.dumpTaskMgr = new TaskManager("com.alibaba.nacos.server.DumpTaskManager");
        this.dumpTaskMgr.setDefaultTaskProcessor(processor);
    }

    // 往TaskManager加入新任务new DumpTask()
    dumpTaskMgr.addTask(groupKey, new DumpTask(groupKey, tag, lastModified, handleIp, isBeta));

    TaskManager, 继承了AbstractNacosTaskExecuteEngine，内置一个ScheduledExecutorService线程池，会轮询调用TaskManager的processTasks方法，消化掉task, processor为构造函数传入的默认DumpProcessor方法

DumpProcessor.class

    if (StringUtils.isBlank(tag)) {
        // 在此处获得最新的数据库数据
        ConfigInfo cf = persistService.findConfigInfo(dataId, group, tenant);
        
        build.remove(Objects.isNull(cf));
        build.content(Objects.isNull(cf) ? null : cf.getContent());
        build.type(Objects.isNull(cf) ? null : cf.getType());
        
        // 在此处去修改配置
        return DumpConfigHandler.configDump(build.build());
    }

DumpConfigHandler.class

    if (!event.isRemove()) {
        // 保存到本地文件中
        result = ConfigCacheService.dump(dataId, group, namespaceId, content, lastModified, type);
        
        if (result) {
            ConfigTraceService.logDumpEvent(dataId, group, namespaceId, null, lastModified, event.getHandleIp(),
                    ConfigTraceService.DUMP_EVENT_OK, System.currentTimeMillis() - lastModified,
                    content.length());
        }
    }

ConfigCacheService.class

    // 构建CacheItem
    CacheItem ci = makeSure(groupKey);
    ci.setType(type);

    if (md5.equals(ConfigCacheService.getContentMd5(groupKey))) {
        DUMP_LOG.warn("[dump-ignore] ignore to save cache file. groupKey={}, md5={}, lastModifiedOld={}, "
                        + "lastModifiedNew={}", groupKey, md5, ConfigCacheService.getLastModifiedTs(groupKey),
                lastModifiedTs);
    } else if (!PropertyUtil.isDirectRead()) {
        // dump到文件中
        DiskUtil.saveToDisk(dataId, group, tenant, content);
    }

通过客户端推送或更新配置时，先更新当前服务的数据（集群下更新mysql），再通过事件触发器进行广播，调用集群每个Member的/comunication/dataChange接口，接口为异步操作，立即返回

其他服务将通过configService.dump()方法将任务加入，并最后通过prossor对执行task的onEvent方法（使用dumpProssor进行装饰）
- 读取库对内存进行更新
- dump配置到文件

集群Member会通过mysql的最新数据，异步的构建CacheItem缓存在内存中，再发布一个LocalDataChangeEvent事件

    //更新md5
    public static void updateMd5(String groupKey, String md5, long lastModifiedTs) {
        CacheItem cache = makeSure(groupKey);
        if (cache.md5 == null || !cache.md5.equals(md5)) {
            cache.md5 = md5;
            cache.lastModifiedTs = lastModifiedTs;
            //这里触发LocalDataChangeEvent事件，涉及到我们之前所有的长轮训监听器，哈哈，是不是有点通了；
            EventDispatcher.fireEvent(new LocalDataChangeEvent(groupKey));
        }
    }

    =》

#### **基本上这里可以再次总结下服务端配置更新后流程：**

    1.某一个服务器收到更新请求后；先自己更新本地数据库的数据；然后发布一个ConfigDataChangeEvent事件
    2.该事件会让每一个服务收到某一个配置更改了，然后每一个服务开始执行流程
    3.每一个服务会新建一个task任务放入自己的任务队列中
    4.每一个服务后台的线程会从队列中执行该任务
    任务执行包含，如果非单机或者mysql会刷新本地缓存文件；这个也是我们前面分析的获取配置的文件
    会更新内存中CacheItem缓存内容信息

#### 注意一点配置中心数据一致性的问题：

    nacos的配置中心在集群条件下配置数据依赖于第三方mysql做数据库存储，因为默认的derby是服务内置的存储，难以满足集群条件共享
    数据变更后，变更节点更新mysql后会广播的消息给其他节点，失败后也只会重试几次，多次失败了就没有做其他处理了不过是由日志记录的；节点收到广播消息会添加到自己的队列里，不断的处理，失败了在添加会队列中即可。而且没有服务器没有其他定时任务去比较服务器配置内容
    所以配置中心应该属于采用去中心化的思想设计的。

#### **3. LongPollingService 长轮询：发布订阅，客户端长轮询获取**


LongPollingService.class：
处理LocalDataChangeEvent事件

    // Register A Subscriber to subscribe LocalDataChangeEvent.
    NotifyCenter.registerSubscriber(new Subscriber() {
        
        @Override
        public void onEvent(Event event) {
            if (isFixedPolling()) {
                // Ignore.
            } else {
                if (event instanceof LocalDataChangeEvent) {
                    LocalDataChangeEvent evt = (LocalDataChangeEvent) event;
                    ConfigExecutor.executeLongPolling(new DataChangeTask(evt.groupKey, evt.isBeta, evt.betaIps));
                }
            }
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return LocalDataChangeEvent.class;
        }
    });

内部类DataChangeTask.class

    class DataChangeTask implements Runnable {
        
        @Override
        public void run() {
            try {
                ConfigCacheService.getContentBetaMd5(groupKey);
                for (Iterator<ClientLongPolling> iter = allSubs.iterator(); iter.hasNext(); ) {
                    ClientLongPolling clientSub = iter.next();
                    if (clientSub.clientMd5Map.containsKey(groupKey)) {
                        // If published tag is not in the beta list, then it skipped.
                        if (isBeta && !CollectionUtils.contains(betaIps, clientSub.ip)) {
                            continue;
                        }
                        
                        // If published tag is not in the tag list, then it skipped.
                        if (StringUtils.isNotBlank(tag) && !tag.equals(clientSub.tag)) {
                            continue;
                        }
                        
                        getRetainIps().put(clientSub.ip, System.currentTimeMillis());
                        iter.remove(); // Delete subscribers' relationships.
                        LogUtil.CLIENT_LOG
                                .info("{}|{}|{}|{}|{}|{}|{}", (System.currentTimeMillis() - changeTime), "in-advance",
                                        RequestUtil
                                                .getRemoteIp((HttpServletRequest) clientSub.asyncContext.getRequest()),
                                        "polling", clientSub.clientMd5Map.size(), clientSub.probeRequestSize, groupKey);
                        // 返回客户端的数据
                        clientSub.sendResponse(Arrays.asList(groupKey));
                    }
                }
            } catch (Throwable t) {
                LogUtil.DEFAULT_LOG.error("data change error: {}", ExceptionUtil.getStackTrace(t));
            }
        }

此处数据变动后，通过DataChangeTask对客户端进行返回，此处需要联动客户端上报的md5（md5若有变动）

客户端会启动后台线程检查配置更新：

ClientWorker.class

    public void checkConfigInfo() {
        // 分任务，这里就是监听器的个数
        int listenerSize = cacheMap.get().size();
        // 向上取整为批数      (perTaskCofigSize 默认是3000)
        int longingTaskCount = (int) Math.ceil(listenerSize / ParamUtil.getPerTaskConfigSize());
        if (longingTaskCount > currentLongingTaskCount) {
            for (int i = (int) currentLongingTaskCount; i < longingTaskCount; i++) {
                // 要判断任务是否在执行 这块需要好好想想。 任务列表现在是无序的。变化过程可能有问题
                executorService.execute(new LongPollingRunnable(i));
            }
            currentLongingTaskCount = longingTaskCount;
        }
    }

ClientWorker.LongPollingRunnable.class

    @Override
        public void run() {
            
            List<CacheData> cacheDatas = new ArrayList<CacheData>();
            List<String> inInitializingCacheList = new ArrayList<String>();
            try {
                // check failover config
                for (CacheData cacheData : cacheMap.get().values()) {
                    if (cacheData.getTaskId() == taskId) {
                        cacheDatas.add(cacheData);
                        try {
                            checkLocalConfig(cacheData);
                            if (cacheData.isUseLocalConfigInfo()) {
                                cacheData.checkListenerMd5();
                            }
                        } catch (Exception e) {
                            LOGGER.error("get local config info error", e);
                        }
                    }
                }
                
                // 这行话非常重要，在这里将关注的数据和md5拼装后，传输到服务/listener接口
                List<String> changedGroupKeys = checkUpdateDataIds(cacheDatas, inInitializingCacheList);

ConfigController.class

    @PostMapping("/listener")
    public void listener() {
        // do long-polling
        inner.doPollingConfig(request, response, clientMd5Map, probeModify.length());
    }

=》 ConfigSerletInner.class

    publish String doPollingConfig() {
        // Long polling.
        if (LongPollingService.isSupportLongPolling(request)) {
            // 记录提交请求数据的ClientWorker客户端， 用于DataChangeTask的遍历
            longPollingService.addLongPollingClient(request, response, clientMd5Map, probeRequestSize);
            return HttpServletResponse.SC_OK + "";
        }
    }

=》 LongPollingService.java

    public void addLongPollingClient() {
        /**
         * 提前500ms返回响应，为避免客户端超时 @qiaoyi.dingqy 2013.10.22改动  add delay time for LoadBalance
         */
        //这里就是服务端hang住的时间，这里是30s-500ms=29.5s
        int delayTime = SwitchService.getSwitchInteger(SwitchService.FIXED_DELAY_TIME, 500);

        long timeout = Math.max(10000, Long.parseLong(str) - delayTime);
        if (isFixedPolling()) {
            // 目前是否即可返回结果,还是保持服务端hang任务
            timeout = Math.max(10000, getFixedPollingInterval());
            // Do nothing but set fix polling timeout.
        } else {
            long start = System.currentTimeMillis();
            List<String> changedGroups = MD5Util.compareMd5(req, rsp, clientMd5Map);
            if (changedGroups.size() > 0) {
                // 如果有变化，则立即返回
                generateResponse(req, rsp, changedGroups);
                LogUtil.CLIENT_LOG.info("{}|{}|{}|{}|{}|{}|{}", System.currentTimeMillis() - start, "instant",
                        RequestUtil.getRemoteIp(req), "polling", clientMd5Map.size(), probeRequestSize,
                        changedGroups.size());
                return;
            } else if (noHangUpFlag != null && noHangUpFlag.equalsIgnoreCase(TRUE_STR)) {
                LogUtil.CLIENT_LOG.info("{}|{}|{}|{}|{}|{}|{}", System.currentTimeMillis() - start, "nohangup",
                        RequestUtil.getRemoteIp(req), "polling", clientMd5Map.size(), probeRequestSize,
                        changedGroups.size());
                return;
            }
        }

        // 提交一个定时任务，29.5s后执行返回
        ConfigExecutor.executeLongPolling(
        new ClientLongPolling(asyncContext, clientMd5Map, ip, probeRequestSize, timeout, appName, tag));

这里触发的时机有以下三种：
- 客户端ClientWorker调用listener后，数据一直没变化，将在29.5s后返回结果
- 客户端调用后，数据已经产生变化，直接返回结果
- 客户端调用时，数据暂未发送变化，但是在29.5s中发送变化，直接触发LocalDataChangeEvent，进行返回

#### **4. 服务数据启动加载**

DumpService.class

     int timeStep = 6;
        Boolean isAllDump = true;
        // initial dump all
        FileInputStream fis = null;
        Timestamp heartheatLastStamp = null;
        try {
            if (isQuickStart()) {
                File heartbeatFile = DiskUtil.heartBeatFile();
                if (heartbeatFile.exists()) {
                    fis = new FileInputStream(heartbeatFile);
                    String heartheatTempLast = IoUtils.toString(fis, Constants.ENCODE);
                    heartheatLastStamp = Timestamp.valueOf(heartheatTempLast);
                    if (TimeUtils.getCurrentTime().getTime() - heartheatLastStamp.getTime()
                            < timeStep * 60 * 60 * 1000) {
                        isAllDump = false;
                    }
                }
            }
            if (isAllDump) {
                LogUtil.DEFAULT_LOG.info("start clear all config-info.");
                DiskUtil.clearAll();
                dumpAllProcessor.process(new DumpAllTask());
            } else {
                Timestamp beforeTimeStamp = getBeforeStamp(heartheatLastStamp, timeStep);
                DumpChangeProcessor dumpChangeProcessor = new DumpChangeProcessor(this, beforeTimeStamp,
                        TimeUtils.getCurrentTime());
                dumpChangeProcessor.process(new DumpChangeTask());
                Runnable checkMd5Task = () -> {
                    LogUtil.DEFAULT_LOG.error("start checkMd5Task");
                    List<String> diffList = ConfigCacheService.checkMd5();
                    for (String groupKey : diffList) {
                        String[] dg = GroupKey.parseKey(groupKey);
                        String dataId = dg[0];
                        String group = dg[1];
                        String tenant = dg[2];
                        ConfigInfoWrapper configInfo = persistService.queryConfigInfo(dataId, group, tenant);
                        ConfigCacheService.dumpChange(dataId, group, tenant, configInfo.getContent(),
                                configInfo.getLastModified());
                    }
                    LogUtil.DEFAULT_LOG.error("end checkMd5Task");
                };
                ConfigExecutor.scheduleConfigTask(checkMd5Task, 0, 12, TimeUnit.HOURS);
            }

默认会把数据库的配置提取出来缓存到服务器磁盘文件中

- 若配置了快速启动，则会去读取是否有心跳文件（心跳文件只在集群模式下存在）
    - 不超过6h，isAllDump = false，走md5校验对比更新
    - 超过6h，isAllDump = true 
- 没配置快速启动，则直接走全量更新

#### **5. 服务器之间的健康检查**

AddressServerMemberLookup.class

    GlobalExecutor.scheduleByCommon(this, 5_000L);

    =》 
    
    执行syncFromAddressUrl()

    =》
    RestResult<String> result = restTemplate.get(addressServerUrl, Header.EMPTY, Query.EMPTY, genericType.getType());

5秒一次

#### **6.配置中心的集群总结**

- 配置中心依赖第三方数据源做存储
- 数据更新数据库后，再通过事件广播到其他成员中
- 所有服务端获取数据都是通过本地文件获取的，减少了db请求
- 配置中心一致性通过6h一次的全量更新同步，也通过ClientWorker的长轮询获取服务器配置变化
    - 若没变化则hang住29.5s
    - 若有变化则立即增量更新

# 参考：
- [Nacos官网](https://nacos.io)
- [Nacos整体概括](https://www.jianshu.com/p/3f4f6554b67c)
- [Nacos配置中心](https://www.jianshu.com/p/1dd59c113287)
- [Nacos集群](https://www.jianshu.com/p/d24c11d16ba1)
- [Nacos服务发现](https://www.jianshu.com/p/2cd487f9560a)