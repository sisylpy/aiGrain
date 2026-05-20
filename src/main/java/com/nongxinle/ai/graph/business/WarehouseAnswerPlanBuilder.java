package com.nongxinle.ai.graph.business;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrix;
import com.nongxinle.ai.harness.followup.WarehouseDrilldownMatrixRow;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
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
        boolean planned = state.getDataPlanTools() != null
                && state.getDataPlanTools().contains(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        boolean hasToolEnvelope = state.getToolResults() != null
                && state.getToolResults().containsKey(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        if (!planned && !hasToolEnvelope) {
            return;
        }

        LinkedHashMap<String, Object> baseDiag = new LinkedHashMap<>();
        baseDiag.put("attachAttempted", true);
        baseDiag.put("expectedToolKey", AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        baseDiag.put("source", "WarehouseStockOverviewTool");
        baseDiag.put("sourceToolKey", AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);

        Object env = state.getToolResults() == null ? null
                : state.getToolResults().get(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
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
        Map<String, Object> wo = extractWarehouseOverview(dataObj, baseDiag);
        if (wo.isEmpty()) {
            attachFailure(state, baseDiag, "empty_warehouse_overview", "warehouseOverview missing");
            return;
        }

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        try {
            WarehouseAnswerPlan plan = build(state, wo, rq, baseDiag);
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
        WarehouseAnswerPlan plan = WarehouseAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(resolveScopeLabel(Map.of(), rq))
                .timeLabel(resolveTimeLabel(state, rq))
                .summary(new LinkedHashMap<>())
                .focusRows(new ArrayList<>())
                .secondaryRows(new ArrayList<>())
                .debug(new LinkedHashMap<>(diag))
                .build();
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

        WarehouseDrilldownMatrixRow matrixRow =
                WarehouseDrilldownMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK, wire, semantic(rq), rq);
        String knownGap = WarehouseDrilldownMatrix.knownGapForResolvedRow(matrixRow);
        if (knownGap != null) {
            dbg.put("warehouseKnownGap", knownGap);
        }

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        Object summaryText = wo.get("summary");
        if (summaryText != null) {
            summary.put("narrative", summaryText.toString());
        }
        summary.put("totalStockAmount", wo.get("totalStockAmount"));
        summary.put("stockItemCount", wo.get("stockItemCount"));

        List<Map<String, Object>> focus = new ArrayList<>();
        List<Map<String, Object>> secondary = new ArrayList<>();

        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING.equals(planType)) {
            copyRankingRows(focus, secondary, wo.get("storeStockAmountRanking"), 5);
        } else if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH.equals(planType)) {
            copyRankingRows(focus, secondary, wo.get("goodsStockAmountRanking"), 5);
        } else if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW.equals(planType)) {
            copyRankingRows(focus, secondary, wo.get("goodsStockAmountRankingAsc"), 5);
        } else if (WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK.equals(planType)) {
            copyListRows(focus, wo.get("lowStockItems"), 5);
            summary.put("riskNote", "账面偏低启发式清单，非严格缺货口径");
        } else {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("totalStockAmount", wo.get("totalStockAmount"));
            row.put("stockItemCount", wo.get("stockItemCount"));
            focus.add(row);
            copyListRows(secondary, wo.get("recommendations"), 3);
        }

        WarehouseAnswerPlan plan = WarehouseAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(resolveScopeLabel(wo, rq))
                .timeLabel(resolveTimeLabel(state, rq))
                .summary(summary)
                .focusRows(focus)
                .secondaryRows(secondary)
                .debug(dbg)
                .build();
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
        WarehouseDrilldownMatrixRow row =
                WarehouseDrilldownMatrix.resolveMatrixRow(path, canonWire, semantic(rq), rq);
        if (row != null) {
            dbg.put("warehouseMatrixRowId", row.getRowId());
            dbg.put("warehouseStructuredIntentDetailWire", row.getStructuredIntentDetailWire());
            dbg.put("warehouseStockFacet", row.getStockFacet());
            String gap = WarehouseDrilldownMatrix.knownGapForResolvedRow(row);
            if (gap != null) {
                dbg.put("warehouseKnownGap", gap);
            }
        } else if (StringUtils.hasText(canonWire)) {
            dbg.put("warehouseStructuredIntentDetailWire", canonWire);
        }
        if (WarehouseDrilldownMatrix.detectMatrixWireMissing(semantic(rq), path, canonWire)) {
            dbg.put("warehouseMatrixWireMissing", WarehouseDrilldownMatrix.MATRIX_WIRE_MISSING);
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

    private static String resolveTimeLabel(AiRunState state, AiResolvedQueryContext rq) {
        if (rq != null && rq.getTimeWindowLabel() != null && !rq.getTimeWindowLabel().isBlank()) {
            return rq.getTimeWindowLabel().trim();
        }
        if (state != null && state.getStatStartDate() != null && state.getStatEndDate() != null) {
            return state.getStatStartDate() + " 至 " + state.getStatEndDate();
        }
        return "";
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
}
