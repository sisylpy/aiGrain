package com.nongxinle.ai.graph.business;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.tool.business.WarehouseInventoryRiskListTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 {@link BusinessToolExecutionNode} 完成 {@link AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW} 后，
 * 基于 Tool 信封生成 {@link WarehouseAnswerPlan}（不重查 SQL）。
 */
@Slf4j
public final class WarehouseAnswerPlanBuilder {

    private WarehouseAnswerPlanBuilder() {
    }

    public static void attachIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        if (!state.isWarehouseStockOverviewPath()) {
            return;
        }
        AiResolvedQueryContext rqEarly = state.getResolvedQueryContext();
        if (ToolRequestContractExecutionParamSupport.isGoodsSupportedDishCoverContract(rqEarly)) {
            return;
        }
        if (!warehouseToolPlannedOrExecuted(state)) {
            return;
        }

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        AiQuerySemanticParseResult sem = semantic(rq);

        // contract-locked gate: non-contract-locked must early exit — no wire consumption, no plan.
        if (sem == null || !SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            log.info("[WarehouseAnswerPlan] skipped runId={} reason=non_contract_locked_parse",
                    state.getRunId());
            return;
        }

        String contractWire = resolveWire(rq);
        if (!StringUtils.hasText(contractWire)) {
            log.info("[WarehouseAnswerPlan] skipped runId={} reason=missing_contract_completed_wire",
                    state.getRunId());
            return;
        }

        WarehouseSemanticCapabilityMatrixRow matrixRow =
                WarehouseSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK, contractWire, sem, rq);
        if (matrixRow == null) {
            log.info("[WarehouseAnswerPlan] skipped runId={} reason=contract_wire_not_accepted_warehouse_matrix rejectedWire={}",
                    state.getRunId(), contractWire);
            return;
        }

        String planType = resolvePlanType(contractWire);
        String expectedTool =
                WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK.equals(planType)
                        ? AiBusinessToolIds.WAREHOUSE_INVENTORY_RISK_LIST
                        : AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW;

        LinkedHashMap<String, Object> baseDiag = new LinkedHashMap<>();
        baseDiag.put("attachAttempted", true);
        baseDiag.put("expectedToolKey", expectedTool);
        baseDiag.put("source", expectedTool);
        baseDiag.put("sourceToolKey", expectedTool);
        baseDiag.put("contractLocked", true);
        baseDiag.put("contractWire", contractWire);

        Object env = state.getToolResults() == null ? null : state.getToolResults().get(expectedTool);
        if (!(env instanceof Map<?, ?> envMapRaw)) {
            attachFailure(state, baseDiag, "missing_or_invalid_tool_envelope", "tool result missing");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> envMap = (Map<String, Object>) envMapRaw;
        if (!Boolean.TRUE.equals(envMap.get("success"))) {
            attachFailure(state, baseDiag, "tool_envelope_unsuccessful",
                    envMap.get("message") == null ? "success=false" : envMap.get("message").toString());
            return;
        }

        Object dataObj = unwrapDataMaybeJsonString(envMap.get("data"));
        Map<String, Object> payload;
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK.equals(planType)) {
            payload = extractWarehouseInventoryRisk(dataObj, baseDiag);
            if (payload.isEmpty()) {
                attachFailure(state, baseDiag, "empty_warehouse_inventory_risk", "warehouseInventoryRisk missing");
                return;
            }
        } else {
            payload = extractWarehouseOverview(dataObj, baseDiag);
            if (payload.isEmpty()) {
                attachFailure(state, baseDiag, "empty_warehouse_overview", "warehouseOverview missing");
                return;
            }
        }

        try {
            WarehouseAnswerPlan plan = build(state, payload, rq, baseDiag);
            state.setWarehouseAnswerPlan(plan);
            log.info("[WarehouseAnswerPlan] attached runId={} type={} focusSize={}",
                    state.getRunId(),
                    plan.getPlanType(),
                    plan.getFocusRows() == null ? 0 : plan.getFocusRows().size());
        } catch (Exception ex) {
            log.warn("[WarehouseAnswerPlan] attach failed runId={}", state.getRunId(), ex);
            baseDiag.put("failureReason", "build_exception");
            attachFailure(state, baseDiag, "build_exception", ex.getMessage());
        }
    }

    private static void attachFailure(
            AiRunState state, Map<String, Object> diag, String reasonCode, String detail) {
        diag.put("failureReason", reasonCode);
        diag.put("failureDetail", detail);
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        String wire = resolveWire(rq);
        String planType = resolvePlanType(wire);
        WarehouseAnswerPlan.WarehouseAnswerPlanBuilder planBuilder = WarehouseAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(resolveScopeLabel(Map.of(), rq))
                .summary(new LinkedHashMap<>())
                .focusRows(new ArrayList<>())
                .secondaryRows(new ArrayList<>())
                .debug(new LinkedHashMap<>(diag));
        InventoryPresentationTimeSupport.applyToWarehousePlanBuilder(planBuilder, planType, state, rq);
        WarehouseAnswerPlan plan = planBuilder.build();
        enrichWarehouseMatrixDebug(plan.getDebug(), rq, planType, wire);
        state.setWarehouseAnswerPlan(plan);
    }

    static WarehouseAnswerPlan build(
            AiRunState state,
            Map<String, Object> wo,
            AiResolvedQueryContext rq,
            LinkedHashMap<String, Object> debug) {
        String wire = resolveWire(rq);
        String planType = resolvePlanType(wire);
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>(debug);
        dbg.put("resolvedPlanType", planType);
        dbg.put("structuredIntentDetailWire", wire.isEmpty() ? null : wire);

        WarehouseSemanticCapabilityMatrixRow matrixRow =
                WarehouseSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK, wire, semantic(rq), rq);
        String knownGap = WarehouseSemanticCapabilityMatrix.knownGapForResolvedRow(matrixRow);
        if (knownGap != null) {
            dbg.put("warehouseKnownGap", knownGap);
        }

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> focus = new ArrayList<>();
        List<Map<String, Object>> secondary = new ArrayList<>();

        if (WarehouseSemanticCapabilityMatrix.KNOWN_GAP_OUT_OF_STOCK_STRICT_NOT_SUPPORTED.equals(knownGap)) {
            summary.put(
                    "gapMessage",
                    "系统暂不支持严格缺货清单；不能用账面偏低启发式或库存总览代替正式缺货结论。");
            return finishWarehousePlan(state, rq, planType, wo, summary, focus, secondary, dbg);
        }
        if (WarehouseSemanticCapabilityMatrix.KNOWN_GAP_NEAR_EXPIRY_NOT_IN_TOOL.equals(knownGap)) {
            summary.put(
                    "gapMessage",
                    "系统暂不支持临期/保质期专链；当前库存数据不能给出临期商品清单。");
            return finishWarehousePlan(state, rq, planType, wo, summary, focus, secondary, dbg);
        }

        Object summaryText = wo.get("summary");
        if (summaryText != null) {
            summary.put("narrative", summaryText.toString());
        }
        summary.put("totalStockAmount", wo.get("totalStockAmount"));
        summary.put("stockItemCount", wo.get("stockItemCount"));

        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING.equals(planType)) {
            copyRankingRows(focus, secondary, wo.get("storeStockAmountRanking"), 5);
        } else if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH.equals(planType)) {
            copyRankingRows(focus, secondary, wo.get("goodsStockAmountRanking"), 5);
        } else if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW.equals(planType)) {
            copyRankingRows(focus, secondary, wo.get("goodsStockAmountRankingAsc"), 5);
        } else if (WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK.equals(planType)) {
            summary.put("windowDays", wo.get("windowDays"));
            summary.put("dataSources", wo.get("dataSources"));
            summary.put("knownGaps", wo.get("knownGaps"));
            copyListRows(focus, wo.get("riskItems"), 15);
            if (focus.isEmpty()) {
                summary.put("emptyRiskList", true);
            }
        } else {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("totalStockAmount", wo.get("totalStockAmount"));
            row.put("stockItemCount", wo.get("stockItemCount"));
            focus.add(row);
            copyListRows(secondary, wo.get("recommendations"), 3);
        }

        return finishWarehousePlan(state, rq, planType, wo, summary, focus, secondary, dbg);
    }

    private static WarehouseAnswerPlan finishWarehousePlan(
            AiRunState state,
            AiResolvedQueryContext rq,
            String planType,
            Map<String, Object> wo,
            LinkedHashMap<String, Object> summary,
            List<Map<String, Object>> focus,
            List<Map<String, Object>> secondary,
            LinkedHashMap<String, Object> dbg) {
        String wire = resolveWire(rq);
        WarehouseAnswerPlan.WarehouseAnswerPlanBuilder planBuilder = WarehouseAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(resolveScopeLabel(wo, rq))
                .summary(summary)
                .focusRows(focus)
                .secondaryRows(secondary)
                .debug(dbg);
        InventoryPresentationTimeSupport.applyToWarehousePlanBuilder(planBuilder, planType, state, rq);
        WarehouseAnswerPlan plan = planBuilder.build();
        enrichWarehouseMatrixDebug(plan.getDebug(), rq, planType, wire);
        return plan;
    }

    private static void enrichWarehouseMatrixDebug(
            Map<String, Object> dbg,
            AiResolvedQueryContext rq,
            String planType,
            String wire) {
        if (dbg == null || rq == null) {
            return;
        }
        String path = rq.getEffectivePathCode();
        if (path == null || path.isBlank()) {
            path = rq.getQueryIntent() != null ? rq.getQueryIntent().getPathCode() : null;
        }
        String canonWire =
                StringUtils.hasText(wire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim())
                        : null;
        WarehouseSemanticCapabilityMatrixRow row =
                WarehouseSemanticCapabilityMatrix.resolveMatrixRow(path, canonWire, semantic(rq), rq);
        if (row != null) {
            dbg.put("warehouseMatrixRowId", row.getRowId());
            dbg.put("warehouseStructuredIntentDetailWire", row.getStructuredIntentDetailWire());
            dbg.put("warehouseStockFacet", row.getStockFacet());
            String gap = WarehouseSemanticCapabilityMatrix.knownGapForResolvedRow(row);
            if (gap != null) {
                dbg.put("warehouseKnownGap", gap);
            }
        } else if (StringUtils.hasText(canonWire)) {
            dbg.put("warehouseStructuredIntentDetailWire", canonWire);
        }
        if (WarehouseSemanticCapabilityMatrix.detectMatrixWireMissing(semantic(rq), path, canonWire)) {
            dbg.put("warehouseMatrixWireMissing", WarehouseSemanticCapabilityMatrix.MATRIX_WIRE_MISSING);
        }
        dbg.put("warehouseAnswerPlanType", planType);
    }

    private static String resolveWire(AiResolvedQueryContext rq) {
        if (rq == null) {
            return "";
        }
        if (rq.getQueryIntent() != null && StringUtils.hasText(rq.getQueryIntent().getStructuredIntentDetail())) {
            return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                    rq.getQueryIntent().getStructuredIntentDetail());
        }
        return "";
    }

    private static String resolvePlanType(String wire) {
        if (!StringUtils.hasText(wire)) {
            return WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(canon)) {
            return WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(canon)) {
            return WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW.equals(canon)) {
            return WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK.equals(canon)) {
            return WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_NEAR_EXPIRY.equals(canon)) {
            return WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW;
        }
        return WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW;
    }

    private static AiQuerySemanticParseResult semantic(AiResolvedQueryContext rq) {
        return rq == null ? null : rq.getQuerySemanticParse();
    }

    private static String resolveScopeLabel(Map<String, Object> wo, AiResolvedQueryContext rq) {
        Object sn = wo.get("scopeName");
        if (sn != null && !sn.toString().isBlank()) {
            return sn.toString().trim();
        }
        if (rq != null && StringUtils.hasText(rq.getQueryScopeBanner())) {
            return rq.getQueryScopeBanner().trim();
        }
        if (rq != null && rq.getOrgScope() != null) {
            String st = rq.getOrgScope().getScopeType();
            if (AiResolvedOrgScope.SCOPE_GROUP.equals(st)) {
                return "集团范围";
            }
        }
        return "当前范围";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractWarehouseInventoryRisk(Object dataObj, Map<String, Object> diag) {
        if (!(dataObj instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> data = (Map<String, Object>) raw;
        Object inner = data.get("data");
        if (inner instanceof Map<?, ?> nested) {
            data = (Map<String, Object>) nested;
        }
        Object risk = data.get(WarehouseInventoryRiskListTool.PAYLOAD_KEY);
        if (risk instanceof Map<?, ?> rm) {
            diag.put("foundDataPath", "envelope.data." + WarehouseInventoryRiskListTool.PAYLOAD_KEY);
            return new LinkedHashMap<>((Map<String, Object>) rm);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractWarehouseOverview(Object dataObj, Map<String, Object> diag) {
        if (!(dataObj instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> data = (Map<String, Object>) raw;
        Object inner = data.get("data");
        if (inner instanceof Map<?, ?> nested) {
            data = (Map<String, Object>) nested;
        }
        Object wo = data.get("warehouseOverview");
        if (wo instanceof Map<?, ?> wm) {
            diag.put("foundDataPath", "envelope.data.warehouseOverview");
            return new LinkedHashMap<>((Map<String, Object>) wm);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static void copyRankingRows(
            List<Map<String, Object>> focus,
            List<Map<String, Object>> secondary,
            Object rankingObj,
            int topN) {
        if (!(rankingObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        int i = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) o);
            if (i == 0) {
                focus.add(row);
            } else if (i < topN) {
                secondary.add(row);
            }
            i++;
        }
    }

    @SuppressWarnings("unchecked")
    private static void copyListRows(List<Map<String, Object>> target, Object listObj, int limit) {
        if (!(listObj instanceof List<?> list)) {
            return;
        }
        int n = 0;
        for (Object o : list) {
            if (n >= limit) {
                break;
            }
            if (o instanceof Map<?, ?> m) {
                target.add(new LinkedHashMap<>((Map<String, Object>) m));
                n++;
            } else if (o != null) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("text", o.toString());
                target.add(row);
                n++;
            }
        }
    }

    private static Object unwrapDataMaybeJsonString(Object data) {
        if (data instanceof String s && !s.isBlank()) {
            try {
                return JSON.parseObject(s);
            } catch (Exception ignore) {
                return data;
            }
        }
        return data;
    }

    /** {@code warehouse.inventory_risk_list} 仅规划/执行风险 Tool 时亦须挂载 AnswerPlan。 */
    private static boolean warehouseToolPlannedOrExecuted(AiRunState state) {
        if (state == null) {
            return false;
        }
        List<String> plan = state.getDataPlanTools();
        Map<String, Object> results = state.getToolResults();
        return containsTool(plan, AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW)
                || containsTool(results, AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW)
                || containsTool(plan, AiBusinessToolIds.WAREHOUSE_INVENTORY_RISK_LIST)
                || containsTool(results, AiBusinessToolIds.WAREHOUSE_INVENTORY_RISK_LIST);
    }

    private static boolean containsTool(List<String> plan, String toolId) {
        return plan != null && plan.contains(toolId);
    }

    private static boolean containsTool(Map<String, Object> results, String toolId) {
        return results != null && results.containsKey(toolId);
    }
}
