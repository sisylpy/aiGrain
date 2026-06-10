package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import com.nongxinle.ai.semantic.contract.SemanticContractPlanOutputSupport;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class GoodsStockBatchDetailAnswerPlanBuilder {

    private GoodsStockBatchDetailAnswerPlanBuilder() {}

    public static void attachIfApplicable(AiRunState state) {
        if (state == null || !state.isWarehouseStockOverviewPath()) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (!SemanticContractPlanOutputSupport.requestsPlanOutput(
                rq, GoodsStockBatchDetailAnswerPlan.TYPE)) {
            return;
        }
        state.setGoodsStockBatchDetailAnswerPlan(null);

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("contractId", GoodsStockBatchDetailAnswerPlan.CONTRACT_ID);
        debug.put("planType", GoodsStockBatchDetailAnswerPlan.TYPE);

        Map<String, Object> toolData = toolData(state);
        String toolStatus = resolveToolStatus(state, toolData);
        if ("NEED_CLARIFICATION".equalsIgnoreCase(toolStatus)) {
            attachFailed(state, debug, GoodsStockBatchDetailAnswerPlan.STATUS_FAILED, "需要澄清具体原料");
            return;
        }
        if (toolData == null || toolData.isEmpty()) {
            attachFailed(state, debug, GoodsStockBatchDetailAnswerPlan.STATUS_FAILED, "缺少库存批次明细数据");
            return;
        }

        try {
            GoodsStockBatchDetailAnswerPlan plan = buildPlan(state, rq, toolData, debug);
            state.setGoodsStockBatchDetailAnswerPlan(plan);
            log.info(
                    "[GoodsStockBatchDetail] runId={} goods={} status={} batchRows={}",
                    state.getRunId(),
                    plan.getGoodsName(),
                    plan.getStatus(),
                    plan.getBatchRows() == null ? 0 : plan.getBatchRows().size());
        } catch (Exception ex) {
            log.warn("[GoodsStockBatchDetail] attach failed runId={}", state.getRunId(), ex);
            debug.put("failureReason", ex.getMessage());
            attachFailed(state, debug, GoodsStockBatchDetailAnswerPlan.STATUS_FAILED, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolData(AiRunState state) {
        Object raw =
                state.getToolResults() == null
                        ? null
                        : state.getToolResults().get(AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER);
        if (!(raw instanceof Map<?, ?> env) || !Boolean.TRUE.equals(env.get("success"))) {
            return null;
        }
        Object data = env.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return null;
        }
        Object core = dm.get(GoodsStockBatchDetailDomainSupport.PAYLOAD_KEY);
        if (core instanceof Map<?, ?> cm) {
            return (Map<String, Object>) cm;
        }
        return null;
    }

    private static String resolveToolStatus(AiRunState state, Map<String, Object> core) {
        if (core != null && StringUtils.hasText(str(core.get("status")))) {
            return str(core.get("status"));
        }
        Object raw =
                state.getToolResults() == null
                        ? null
                        : state.getToolResults().get(AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER);
        if (raw instanceof Map<?, ?> env) {
            Object data = env.get("data");
            if (data instanceof Map<?, ?> dm && Boolean.TRUE.equals(dm.get("needClarification"))) {
                return "NEED_CLARIFICATION";
            }
        }
        return core == null ? "FAILED" : "OK";
    }

    private static GoodsStockBatchDetailAnswerPlan buildPlan(
            AiRunState state,
            AiResolvedQueryContext rq,
            Map<String, Object> core,
            LinkedHashMap<String, Object> debug) {
        String goodsName = GoodsEntityDisplayNameSupport.resolveDisplayGoodsName(rq, core);
        Integer disGoodsId = GoodsEntityDisplayNameSupport.resolveDisplayDisGoodsId(rq, core);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batchRows =
                core.get("batchRows") instanceof List<?> list
                        ? (List<Map<String, Object>>) list
                        : new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batchesByUnit =
                core.get("batchesByUnit") instanceof List<?> unitList
                        ? (List<Map<String, Object>>) unitList
                        : new ArrayList<>();

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeBatchCount", core.get("activeBatchCount"));
        Object mismatch = core.get("balanceMismatchCount");
        if (mismatch != null) {
            summary.put("balanceMismatchCount", mismatch);
        }

        List<String> gaps = new ArrayList<>();
        if ("no_active_stock_batch".equals(str(core.get("knownGap")))) {
            gaps.add("no_active_stock_batch");
        }
        if (mismatch instanceof Number n && n.intValue() > 0) {
            gaps.add("batch_balance_mismatch");
        }

        String status =
                "NOT_FOUND".equalsIgnoreCase(str(core.get("status")))
                        ? GoodsStockBatchDetailAnswerPlan.STATUS_FAILED
                        : (batchRows.isEmpty()
                                ? GoodsStockBatchDetailAnswerPlan.STATUS_PARTIAL
                                : GoodsStockBatchDetailAnswerPlan.STATUS_SUCCESS);

        return GoodsStockBatchDetailAnswerPlan.builder()
                .planType(GoodsStockBatchDetailAnswerPlan.TYPE)
                .contractId(GoodsStockBatchDetailAnswerPlan.CONTRACT_ID)
                .status(status)
                .goodsName(goodsName)
                .disGoodsId(disGoodsId)
                .scopeLabel(str(core.get("scopeBanner")))
                .stockSnapshotLabel("当前仍有剩余的库存批次")
                .batchRows(batchRows)
                .batchesByUnit(batchesByUnit)
                .summary(summary)
                .knownGaps(gaps)
                .debug(debug)
                .build();
    }

    private static void attachFailed(
            AiRunState state,
            LinkedHashMap<String, Object> debug,
            String status,
            String message) {
        if (message != null) {
            debug.put("failureReason", message);
        }
        state.setGoodsStockBatchDetailAnswerPlan(
                GoodsStockBatchDetailAnswerPlan.builder()
                        .planType(GoodsStockBatchDetailAnswerPlan.TYPE)
                        .contractId(GoodsStockBatchDetailAnswerPlan.CONTRACT_ID)
                        .status(status)
                        .goodsName(
                                GoodsEntityDisplayNameSupport.resolveDisplayGoodsName(
                                        state.getResolvedQueryContext(), Map.of()))
                        .debug(debug)
                        .build());
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String t = o.toString().trim();
        return t.isEmpty() ? null : t;
    }
}
