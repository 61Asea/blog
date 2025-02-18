package com.xiaoying.investbase.degrade;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.google.protobuf.AbstractMessage;
import com.googlecode.protobuf.format.JsonFormat;
import com.xiaoying.investbase.constants.ServerConsts;
import com.xiaoying.investbase.http.AbstractRequestAspect;
import com.xiaoying.investbase.http.RequestContextManager;
import com.xiaoying.investutils.proto.Basic;
import com.xiaoying.investutils.proto.Common;
import com.xiaoying.investutils.util.PbUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Configuration
public class FlowDegradeService extends AbstractRequestAspect {
    // 熔断后进入恢复时间（默认1分钟）
    @Value("${ams.degrade.resumeWindowMs:60000}")
    private int resumeWindowMs;

    // 熔断后关闭时间（默认5分钟）
    @Value("${ams.degrade.halfResumeWindowMs:300000}")
    private int halfResumeWindowMs;

    // 熔断忽略接口
    @Value("#{'${ams.degrade.ignoreRequest:}'.empty ? null : '${ams.degrade.ignoreRequest:}'.split(',')}")
    private Set<String> ignoreRequest;

    @Resource
    private RequestContextManager requestContextManager;

    @Resource
    private FlowDegradeRuleManager flowDegradeRuleManager;

    private Map<Long, FlowDegradeContext> contextMap = new HashMap<>();
    private Map<Long, List<Long>> timeoutMap = new LinkedHashMap<>();

    @PostConstruct
    private void initFlowDegradeRuleManager() {
        flowDegradeRuleManager.setFlowDegradeService(this);
        if (null == ignoreRequest) {
            ignoreRequest = new HashSet<>();
        }

        log.info("ams.degrade.ignoreRequest : {}", ignoreRequest.toString());
    }

    public Map<Long, FlowDegradeContext> getContextMap() {
        return contextMap;
    }

    public FlowDegradeContext getFlowDegradeContext(String resource) {
        FlowDegradeContext context = new FlowDegradeContext(null, false, null, false, new AtomicBoolean(false));

        try {
            context.degradeEntry = SphU.asyncEntry(resource);
        } catch (BlockException e) {
            context.isDegrade = true;
            return context;
        }

        try {
            context.flowEntry = SphU.asyncEntry(resource + ServerConsts.FLOW_SUFFIX);
        } catch (BlockException e) {
            context.isFlow = true;
        }finally {
            return context;
        }
    }

    private AbstractMessage getErrorMessage(RequestContextManager.RequestContext context) {
        Common.Response response= Common.Response.newBuilder().setBasic(
                Basic.ResponseBasic.newBuilder()
                        .setCode(ServerConsts.ERROR_CODE_FALL_BACK)
                        .setMsg("request has intercepted.")).build();

        try {
            return (AbstractMessage) PbUtils.byteToPb(context.getContentType(),
                    requestContextManager.getResponseClass(context.getPath()), response.toByteArray());
        } catch (Exception e) {
           return null;
        }
    }

    @Override
    public AbstractMessage interceptRequest() throws JsonFormat.ParseException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        RequestContextManager.RequestContext context = requestContextManager.getRequestContext();
        AbstractMessage request = context.getRequest();
        String msgName = request.getClass().getSimpleName();
        if (ignoreRequest.contains(msgName)) {
            return null;
        }

        // 1. 根据请求 + 请求参数，生成DefaultNode，实现热点参数熔断
        String resource = flowDegradeRuleManager.getResource(context.getPath(), request);
        Long currentTime = System.currentTimeMillis();
        Long requestId = requestContextManager.getRequestId();

        // 2. 添加限流/熔断规则
        if (!flowDegradeRuleManager.existFlowRule(resource)) {
            flowDegradeRuleManager.addFlowRule(resource, msgName);
        }
        FlowDegradeRuleManager.FlowDegradeInfo flowDegradeInfo = flowDegradeRuleManager.getFlowDegradeInfo(resource);
        if (null == flowDegradeInfo) {
            return null;
        }

        // 3. 通过SphU接口进行限流/熔断等判断
        FlowDegradeContext flowContext = getFlowDegradeContext(resource);

        // 4. 触发熔断，直接返回降级内容
        if (flowContext.isDegrade) {
            flowContext = null;
            return getErrorMessage(context);
        }

        // 5. 熔断恢复策略
        if (flowDegradeInfo.getStatus() == ServerConsts.FLOW_DEGRADE_OPEN
                && flowDegradeInfo.getLastDegradeTime() + resumeWindowMs < currentTime) {
            flowDegradeInfo.setStatus(ServerConsts.FLOW_DEGRADE_RESUME);
            flowDegradeInfo.setLastDegradeTime(currentTime);
            log.info(" {} flow degrade resume", resource);
        }
        if ((flowDegradeInfo.getStatus() == ServerConsts.FLOW_DEGRADE_RESUME)
                && flowDegradeInfo.getLastDegradeTime() + halfResumeWindowMs < currentTime) {
            flowDegradeInfo.setStatus(ServerConsts.FLOW_DEGRADE_CLOSED);
            flowDegradeInfo.setLastDegradeTime(currentTime);
            log.info(" {} flow degrade close", resource);
        }
        if ((flowDegradeInfo.getStatus() == ServerConsts.FLOW_DEGRADE_HALF_RESUME)
                && flowDegradeInfo.getLastDegradeTime() + halfResumeWindowMs < currentTime) {
            flowDegradeInfo.setStatus(ServerConsts.FLOW_DEGRADE_CLOSED);
            flowDegradeInfo.setLastDegradeTime(currentTime);
            log.info(" {} flow degrade close", resource);
        }

        synchronized(contextMap) {
            contextMap.put(requestId, flowContext);
        }
        synchronized(timeoutMap) {
            Long timeoutMs = currentTime + (int)flowDegradeInfo.getDegradeRule().getCount() + 1000;
            List<Long> list = timeoutMap.get(timeoutMs);
            if (null == list){
                list = new ArrayList<>();
                timeoutMap.put(timeoutMs, list);
            }
            list.add(requestId);
        }

        // 触发限流，默认触发才会进行提升QPS
        if (flowContext.isFlow) {
            // 熔断开启/熔断半恢复状态，返回降级内容
            if (flowDegradeInfo.getStatus() == ServerConsts.FLOW_DEGRADE_OPEN
                || flowDegradeInfo.getStatus() == ServerConsts.FLOW_DEGRADE_HALF_RESUME){
                return getErrorMessage(context);
            }

            // 提高QPS
            long last = flowDegradeInfo.getLastFlowTime().get();
            long current = System.currentTimeMillis() / 1000;
            flowContext.isFlow = false;
            if (last < current && flowDegradeInfo.getLastFlowTime().compareAndSet(last, current)) {
                flowDegradeRuleManager.increaseQps(resource);
            }
        }

        return null;
    }

    @Override
    public void beforeRequest() {
        List<Long> timeOutList = null;
        Long currentTime = System.currentTimeMillis();
        synchronized (timeoutMap) {
            Iterator<Map.Entry<Long, List<Long>>> it = timeoutMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, List<Long>> entry = it.next();
                if (entry.getKey() > currentTime){
                    break;
                }
                if (null == timeOutList) {
                    timeOutList = entry.getValue();
                }else {
                    timeOutList.addAll(entry.getValue());
                }
                it.remove();
            }
        }

        if (null == timeOutList) {
            return;
        }

        for (Long requestId : timeOutList){
            exit(requestId);
        }
    }

    public void exit(Long requestId) {
        FlowDegradeContext flowContext = contextMap.get(requestId);
        if (null == flowContext) {
            return;
        }

        if (!flowContext.getFinished().compareAndSet(false, true)){
            return;
        }

        if (null != flowContext.getFlowEntry()) {
            flowContext.getFlowEntry().exit();
        }
        if (null != flowContext.getDegradeEntry()) {
            flowContext.getDegradeEntry().exit();
        }
        synchronized(contextMap) {
            contextMap.remove(requestId);
        }
        flowContext = null;
    }

    @Override
    public void afterRequest() {
        exit(requestContextManager.getRequestId());
    }

    @Data
    @AllArgsConstructor
    public static class FlowDegradeContext {
        private Entry flowEntry;
        private boolean isFlow;
        private Entry degradeEntry;
        private boolean isDegrade;
        private AtomicBoolean finished;
    }
}