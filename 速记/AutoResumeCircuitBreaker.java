package com.xiaoying.investbase.degrade;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.ResponseTimeCircuitBreaker;
import com.xiaoying.investbase.http.RequestContextManager;

public class AutoResumeCircuitBreaker extends ResponseTimeCircuitBreaker {
    private RequestContextManager requestContextManager;
    private FlowDegradeService flowDegradeService;

    public AutoResumeCircuitBreaker(DegradeRule rule, RequestContextManager requestContextManager,
                                    FlowDegradeService flowDegradeService ) {

        super(rule);
        this.requestContextManager = requestContextManager;
        this.flowDegradeService = flowDegradeService;
    }

    @Override
    public void onRequestComplete(Context context){
        Long requestId = requestContextManager.getRequestId();
        FlowDegradeService.FlowDegradeContext degradeContext = flowDegradeService.getContextMap().get(requestId);
        // 本身不触发限流的情况下，才能去恢复sentinel的熔断
        if (!degradeContext.isFlow()){
            super.onRequestComplete(context);
        }
    }
}