package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.contract.SemanticContractPlanOutputSupport;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public final class GoodsSupportedDishCoverAnswerPlanBuilder {

    private GoodsSupportedDishCoverAnswerPlanBuilder() {}

    public static void attachIfApplicable(AiRunState state) {
        if (state == null || !state.isWarehouseStockOverviewPath()) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (!SemanticContractPlanOutputSupport.requestsPlanOutput(
                rq, GoodsSupportedDishCoverAnswerPlan.TYPE)) {
            return;
        }
        state.setGoodsSupportedDishCoverAnswerPlan(null);

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("contractId", GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID);
        debug.put("planType", GoodsSupportedDishCoverAnswerPlan.TYPE);

        Map<String, Object> toolData = toolData(state);
        String toolStatus = resolveToolStatus(state, toolData);
        if ("NEED_CLARIFICATION".equalsIgnoreCase(toolStatus)) {
            attachFailed(state, debug, GoodsSupportedDishCoverAnswerPlan.STATUS_FAILED, "需要澄清具体原料");
            return;
        }
        if (toolData == null || toolData.isEmpty()) {
            attachFailed(state, debug, GoodsSupportedDishCoverAnswerPlan.STATUS_FAILED, "缺少原料可支撑菜品数据");
            return;
        }

        try {
            GoodsSupportedDishCoverAnswerPlan plan = buildPlan(state, rq, toolData, debug);
            state.setGoodsSupportedDishCoverAnswerPlan(plan);
            log.info(
                    "[GoodsSupportedDishCover] runId={} goods={} status={} dishRows={}",
                    state.getRunId(),
                    plan.getGoodsName(),
                    plan.getStatus(),
                    plan.getDishRows() == null ? 0 : plan.getDishRows().size());
        } catch (Exception ex) {
            log.warn("[GoodsSupportedDishCover] attach failed runId={}", state.getRunId(), ex);
            debug.put("failureReason", ex.getMessage());
            attachFailed(state, debug, GoodsSupportedDishCoverAnswerPlan.STATUS_FAILED, ex.getMessage());
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
        Object core = dm.get(GoodsSupportedDishCoverDomainService.PAYLOAD_KEY);
        if (core instanceof Map<?, ?> cm) {
            return (Map<String, Object>) cm;
        }
        return (Map<String, Object>) data;
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

    private static GoodsSupportedDishCoverAnswerPlan buildPlan(
            AiRunState state,
            AiResolvedQueryContext rq,
            Map<String, Object> core,
            LinkedHashMap<String, Object> debug) {
        String goodsName = GoodsEntityDisplayNameSupport.resolveDisplayGoodsName(rq, core);
        Integer disGoodsId = GoodsEntityDisplayNameSupport.resolveDisplayDisGoodsId(rq, core);

        var timeFields =
                InventoryPresentationTimeSupport.buildForGoodsSupportedDishCover(state, rq);
        DishIngredientCoverSalesBaseline baseline =
                DishIngredientCoverSalesBaseline.fromWireMap(core.get("salesBaseline"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dishRows =
                core.get("dishRows") instanceof List<?> list
                        ? (List<Map<String, Object>>) list
                        : new ArrayList<>();

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        if (baseline != null && StringUtils.hasText(baseline.getDisplayLabel())) {
            summary.put("salesBaselineLabel", baseline.getDisplayLabel());
            summary.put("salesBaselineStartDate", baseline.getStartDateIso());
            summary.put("salesBaselineStopDate", baseline.getStopDateIso());
            summary.put("salesBaselineDays", baseline.getBaselineDays());
            summary.put("salesBaselineSource", baseline.getBaselineSource());
            summary.put(
                    "salesBaselinePeriodPhrase",
                    com.nongxinle.ai.inventory.CoverDaysSalesBaselinePresentationSupport.formatPeriodPhrase(
                            rq, baseline));
        }
        summary.put("linkedDishCount", dishRows.size());
        summary.put("currentStockQty", core.get("currentStockQty"));

        List<String> gaps = new ArrayList<>();
        if ("no_linked_dish_for_goods".equals(str(core.get("knownGap")))) {
            gaps.add("no_linked_dish_for_goods");
        }

        String status =
                gaps.isEmpty() && !dishRows.isEmpty()
                        ? GoodsSupportedDishCoverAnswerPlan.STATUS_SUCCESS
                        : (dishRows.isEmpty()
                                ? GoodsSupportedDishCoverAnswerPlan.STATUS_PARTIAL
                                : GoodsSupportedDishCoverAnswerPlan.STATUS_SUCCESS);

        return GoodsSupportedDishCoverAnswerPlan.builder()
                .planType(GoodsSupportedDishCoverAnswerPlan.TYPE)
                .contractId(GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID)
                .status(status)
                .goodsName(goodsName)
                .disGoodsId(disGoodsId)
                .scopeLabel(str(core.get("scopeBanner")))
                .stockSnapshotLabel(timeFields.getStockSnapshotLabel())
                .salesBaselineLabel(baseline != null ? baseline.getDisplayLabel() : null)
                .currentStockQty(str(core.get("currentStockQty")))
                .stockUnit(str(core.get("stockUnit")))
                .firstImpactedDishName(str(core.get("firstImpactedDishName")))
                .firstImpactedCoverDays(str(core.get("firstImpactedCoverDays")))
                .dishRows(dishRows)
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
        state.setGoodsSupportedDishCoverAnswerPlan(
                GoodsSupportedDishCoverAnswerPlan.builder()
                        .planType(GoodsSupportedDishCoverAnswerPlan.TYPE)
                        .contractId(GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID)
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
