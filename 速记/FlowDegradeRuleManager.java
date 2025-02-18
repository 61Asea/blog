package com.xiaoying.investbase.degrade;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.*;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.google.protobuf.AbstractMessage;
import com.xiaoying.investbase.constants.ServerConsts;
import com.xiaoying.investbase.http.RequestContextManager;
import com.xiaoying.investutils.util.PbUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Configuration
public class FlowDegradeRuleManager {
    // 接口加参数级别的限流（热点参数限流）
    @Value("#{${ams.degrade.field:{}}}")
    private Map<String, String> degradeFieldMap = new HashMap<>();

    // 慢响应触发熔断时间（默认10秒，对应DegradeFlow的RT）
    @Value("#{${ams.degrade.maxResponseMs:{}}}")
    private Map<String, Integer> maxResponseMsMap = new HashMap<>();

    // 限流调整幅度（默认10%）
    @Value("${ams.degrade.changeRate:10}")
    private int changeRate;

    @Resource
    private RequestContextManager requestContextManager;

    // 最少统计数量（1 / 0.2 + 1 = 6个）
    private int minStatsNum = (int) ((1 / ServerConsts.SLOW_RATIO) + 1);

    private List<FlowRule> flowRuleList = new ArrayList<>(20);
    private List<DegradeRule> degradeRuleList = new ArrayList<>(20);
    private Map<String, FlowDegradeRuleManager.FlowDegradeInfo> infoMap = new HashMap<>(10);
    private Map<String, InterceptorIdentifier> identifierHashMap = new HashMap<>(10);

    private FlowDegradeService flowDegradeService = null;

    @PostConstruct
    private void printProperties() {
        log.info("ams.degrade.maxResponseMs : {}", maxResponseMsMap.toString());
        log.info("ams.degrade.field : {}", degradeFieldMap.toString());
        log.info("ams.degrade.changeRate : {}", changeRate);
    }

    public void setFlowDegradeService(FlowDegradeService flowDegradeService){
        this.flowDegradeService = flowDegradeService;
    }

    public void registerInterceptorIdentifier(String messageName, InterceptorIdentifier identifier) {
        log.info("registerInterceptorIdentifier : {}", messageName);
        identifierHashMap.put(messageName, identifier);
    }

    public String getResource(String path, AbstractMessage message) {
        String messageName = message.getClass().getSimpleName();
        InterceptorIdentifier identifier = identifierHashMap.get(messageName);
        if (null != identifier) {
            return path + "/"+ identifier.getIdentifier(message);
        }

        String fieldName = degradeFieldMap.get(messageName);
        if (null != fieldName) {
            Object obj = PbUtils.getField(message, degradeFieldMap.get(messageName));
            if (null != obj) {
                return path + "/" + String.valueOf(obj);
            }
        }

        return path;
    }

    public void addFlowRuleByPath(String path, String msgName){
        if (degradeFieldMap.containsKey(msgName)) {
            return;
        }

        addFlowRule(path, msgName);
    }

    public boolean existFlowRule(String resource) {
        return infoMap.containsKey(resource);
    }

    public FlowDegradeInfo getFlowDegradeInfo(String resource) {
        return infoMap.get(resource);
    }

    private int getMaxResponseTime(String msgName) {
        Integer maxResponseMs = maxResponseMsMap.get(msgName);
        if (null == maxResponseMs){
            return ServerConsts.MAX_RESPONSE_MS;
        }

        return maxResponseMs;
    }

    public void addFlowRule(String resource, String msgName) {
        // 接口最大返回时间
        int maxResponseMs = getMaxResponseTime(msgName);
        // 接口的统计间隔（10 * 2 = 20秒）
        int statInterval = maxResponseMs * 2; // 10000 * 2 = 20000ms

        FlowRule flowRule = new FlowRule(resource + ServerConsts.FLOW_SUFFIX);
        // 默认QPS(默认10)
        flowRule.setCount(ServerConsts.DEFAULT_QPS);
        // 按照QPS进行限流
        flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // 默认模式，通过滑动窗口进行计算
        flowRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);

        DegradeRule degradeRule = new DegradeRule(resource);
        // 慢响应熔断模式
        degradeRule.setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType());
        // 判断慢响应触发的最大RT时长（默认10秒）
        degradeRule.setCount(maxResponseMs);
        // 熔断时间（默认，20000 * 10 / 100 / 1000）
        degradeRule.setTimeWindow(((statInterval * changeRate) / 100000) + 1);
        // 超过RT的比例（默认20%）
        degradeRule.setSlowRatioThreshold(ServerConsts.SLOW_RATIO);
        // 统计数量（默认6个）
        degradeRule.setMinRequestAmount(minStatsNum);
        // 统计间隔 （默认20秒）
        degradeRule.setStatIntervalMs(statInterval);

        FlowDegradeRuleManager.FlowDegradeInfo info;
        synchronized (infoMap) {
            if (infoMap.containsKey(resource)) {
                return;
            }

            info = new FlowDegradeRuleManager.FlowDegradeInfo(
                    ServerConsts.FLOW_DEGRADE_CLOSED, System.currentTimeMillis(), flowRule, degradeRule, null, new AtomicLong(0));
            infoMap.put(resource, info);
        }
        log.info("add flowRule: {}", flowRule);
        log.info("add degradeRule: {}", degradeRule);
        flowRuleList.add(flowRule);
        degradeRuleList.add(degradeRule);
        FlowRuleManager.loadRules(getFlowList());
        DegradeRuleManager.loadRules(getDegradeList());
        info.autoResumeCircuitBreaker = getCircuitBreaker(resource);
    }

    public void registerStateChangeObserver() {
        EventObserverRegistry.getInstance().addStateChangeObserver("listen",
            (prevState, newState, rule, snapshotValue) -> {
                // 会直接关闭
                if (newState == CircuitBreaker.State.CLOSED) {
                    log.info("{} degrade close", rule.getResource());
                }
                if (newState == CircuitBreaker.State.OPEN) {
                    log.error("{} degrade open", rule.getResource());
                    decreaseQps(rule.getResource());
                }

                // 屏蔽掉sentinel的半恢复状态，到达熔断时间后，直接进入关闭状态
                if (newState == CircuitBreaker.State.HALF_OPEN) {
                    fromHalfOpenToClose(infoMap.get(rule.getResource()).autoResumeCircuitBreaker);
                }
            });
    }

    public void increaseQps(String resource){
        FlowDegradeInfo info = infoMap.get(resource);
        FlowRule flowRule = info.getFlowRule();
        DegradeRule degradeRule = info.getDegradeRule();
        int statInterval = degradeRule.getStatIntervalMs();
        // QPS = QPS * 1.1
        double qps = flowRule.getCount() * (1 + (changeRate / 100.0));

        // 20秒的QPS小于最小统计个数（6个）
        if (qps * (statInterval / 1000.0) < minStatsNum) {
            // 将QPS提升足够满足最小统计
            qps = minStatsNum * 1000.0 / statInterval;
        }
        log.info("increase qps: {}", qps);
        flowRule.setCount(qps);
        FlowRuleManager.loadRules(getFlowList());

        if (1 == degradeRule.getMinRequestAmount()) {
            degradeRule.setMinRequestAmount(minStatsNum);
            log.info("new MinRequestAmount: {}", minStatsNum);
            DegradeRuleManager.loadRules(getDegradeList());
            info.autoResumeCircuitBreaker = getCircuitBreaker(resource);
        }
    }

    public void decreaseQps(String resource) {
        FlowDegradeInfo info = infoMap.get(resource);
        FlowRule flowRule = info.getFlowRule();
        DegradeRule degradeRule = info.getDegradeRule();
        int statInterval = degradeRule.getStatIntervalMs();
        double tps = 0;
        // qps 降低为原先的百分之90
        double qps = flowRule.getCount() * (1 - (changeRate / 100.0));
        double minQps = 1000.1 / statInterval;

        if (ServerConsts.FLOW_DEGRADE_CLOSED == info.getStatus()) {
            info.setStatus(ServerConsts.FLOW_DEGRADE_OPEN);
            log.info(" {} flow degrade open", resource);
        }
        if (ServerConsts.FLOW_DEGRADE_RESUME == info.getStatus()) {
            info.setStatus(ServerConsts.FLOW_DEGRADE_HALF_RESUME);
            log.info(" {} flow degrade half resume", resource);
        }
        info.setLastDegradeTime(System.currentTimeMillis());

        if (qps < minQps) {
            qps = minQps;
        }
        log.info("decrease qps: {}", qps);
        flowRule.setCount(qps);
        FlowRuleManager.loadRules(getFlowList());

        tps = qps * statInterval;
        if (tps < degradeRule.getMinRequestAmount() ) {
            degradeRule.setMinRequestAmount(1);
            log.info("new MinRequestAmount: 1");
            DegradeRuleManager.loadRules(getDegradeList());
            info.autoResumeCircuitBreaker = getCircuitBreaker(resource);
        }
    }

    private List<FlowRule> getFlowList() {
        List<FlowRule> list = new ArrayList<>(flowRuleList.size());
        flowRuleList.forEach(rule -> {
            FlowRule tmp = new FlowRule(rule.getResource());
            tmp.setCount(rule.getCount());
            tmp.setGrade(rule.getGrade());
            tmp.setControlBehavior(rule.getControlBehavior());
            list.add(tmp);
        });

        return list;
    }

    private List<DegradeRule> getDegradeList() {
        List<DegradeRule> list = new ArrayList<>(degradeRuleList.size());
        degradeRuleList.forEach(rule -> {
            DegradeRule tmp = new DegradeRule(rule.getResource());
            tmp.setGrade(rule.getGrade());
            tmp.setCount(rule.getCount());
            tmp.setTimeWindow(rule.getTimeWindow());
            tmp.setSlowRatioThreshold(rule.getSlowRatioThreshold());
            tmp.setMinRequestAmount(rule.getMinRequestAmount());
            tmp.setStatIntervalMs(rule.getStatIntervalMs());
            list.add(tmp);
        });

        return list;
    }

    public void fromHalfOpenToClose(ResponseTimeCircuitBreaker circuitBreaker) {
        Method fromHalfOpenToClose = null;
        try {
            fromHalfOpenToClose = AbstractCircuitBreaker.class.getDeclaredMethod("fromHalfOpenToClose");
            fromHalfOpenToClose.setAccessible(true);
            fromHalfOpenToClose.invoke(circuitBreaker);
            fromHalfOpenToClose.setAccessible(false);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AutoResumeCircuitBreaker getCircuitBreaker(String resource) {
        AutoResumeCircuitBreaker circuitBreaker = null;
        List<CircuitBreaker> cbList = null;
        Map<String, List<CircuitBreaker>> cbMap = null;
        try {
            Field circuitBreakers = DegradeRuleManager.class.getDeclaredField("circuitBreakers");
            circuitBreakers.setAccessible(true);
            cbMap = (Map<String, List<CircuitBreaker>>) circuitBreakers.get(null);
            cbList = cbMap.get(resource);
            circuitBreakers.setAccessible(false);
            circuitBreaker = new AutoResumeCircuitBreaker(cbList.get(0).getRule(), requestContextManager, flowDegradeService);
            cbList.set(0, circuitBreaker);
        }catch (Exception e) {
            e.printStackTrace();
        }

        return circuitBreaker;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowDegradeInfo {
        private int status;
        private long lastDegradeTime;
        private FlowRule flowRule;
        private DegradeRule degradeRule;
        private AutoResumeCircuitBreaker autoResumeCircuitBreaker;
        private AtomicLong lastFlowTime;
    }
}