package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.ToolDepartmentResolutionSupport;
import com.nongxinle.ai.graph.business.WarehouseStockOverviewToolExecutor;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 库房库存概览专线：仅 {@link AiResolvedQueryIntent#WAREHOUSE_STOCK_OVERVIEW} +
 * {@link AiResolvedQueryIntent#PATH_WAREHOUSE_STOCK}；内部独占调用
 * {@link AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseStockAgent implements BusinessSubAgent {

    private final WarehouseStockOverviewToolExecutor warehouseStockOverviewToolExecutor;
    private final ToolDepartmentResolutionSupport toolDepartmentResolutionSupport;

    @Override
    public String agentName() {
        return BusinessAgentNames.WAREHOUSE_STOCK;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.builder()
                .agentName(agentName())
                .supportedIntentCodes(List.of(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW))
                .supportedPathCodes(List.of(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK))
                .supportsGroupScope(true)
                .supportsStoreCompare(false)
                .supportsMultiTurn(true)
                .build();
    }

    @Override
    public boolean supports(BusinessAgentRequest request) {
        if (request == null || request.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryContext rq = request.getResolvedQueryContext();
        return AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(rq.getEffectivePathCode());
    }

    @Override
    public AgentResultEnvelope execute(BusinessAgentRequest request) {
        long t0 = System.nanoTime();
        AiRunState state = request == null ? null : request.getExecutionContext();
        if (state == null) {
            return failureEnvelope("missing_execution_context", AgentResultStatus.FAILED, t0);
        }
        long rid = state.getRunId();
        AiResolvedQueryContext rqCtx = request.getResolvedQueryContext();
        Long dept = state.getDepartmentId();
        Long deptScoped = toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, dept);
        Long dis = state.getDistributerId();
        String start = state.getStatStartDate();
        String stop = state.getStatEndDate();
        if (rqCtx != null && rqCtx.getTimeWindow() != null) {
            if (rqCtx.getTimeWindow().getStartDate() != null) {
                start = rqCtx.getTimeWindow().getStartDate().toString();
            }
            if (rqCtx.getTimeWindow().getEndDate() != null) {
                stop = rqCtx.getTimeWindow().getEndDate().toString();
            }
            state.setStatStartDate(start);
            state.setStatEndDate(stop);
        }

        ToolResult executed = warehouseStockOverviewToolExecutor.executeWarehouseStockOverview(
                rid,
                state,
                deptScoped,
                dis,
                start,
                stop,
                new LinkedHashMap<>());
        if (executed == null) {
            return AgentResultEnvelope.builder()
                    .agentName(agentName())
                    .status(AgentResultStatus.PERMISSION_DENIED)
                    .errors(List.of("permission_denied_warehouse_stock_overview"))
                    .warnings(new ArrayList<>())
                    .durationMs(elapsedMs(t0))
                    .traceId(traceId(rid))
                    .warehouseStockOverviewToolSuccess(false)
                    .build();
        }

        AgentResultStatus st = executed.isSuccess() ? AgentResultStatus.SUCCESS : AgentResultStatus.FAILED;
        return AgentResultEnvelope.builder()
                .agentName(agentName())
                .status(st)
                .warnings(new ArrayList<>())
                .errors(executed.isSuccess() ? new ArrayList<>() : List.of(
                        executed.getMessage() == null ? "warehouse_stock_tool_failed" : executed.getMessage()))
                .durationMs(elapsedMs(t0))
                .traceId(traceId(rid))
                .warehouseStockOverviewToolSuccess(executed.isSuccess())
                .build();
    }

    private static AgentResultEnvelope failureEnvelope(String err, AgentResultStatus status, long t0) {
        return AgentResultEnvelope.builder()
                .agentName(BusinessAgentNames.WAREHOUSE_STOCK)
                .status(status)
                .errors(List.of(err))
                .warnings(new ArrayList<>())
                .durationMs(elapsedMs(t0))
                .traceId("no-run")
                .warehouseStockOverviewToolSuccess(false)
                .build();
    }

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000L;
    }

    private static String traceId(long runId) {
        return runId + "-" + BusinessAgentNames.WAREHOUSE_STOCK + "-" + UUID.randomUUID();
    }
}
