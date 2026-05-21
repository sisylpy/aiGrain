package com.nongxinle.ai.harness.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 探索型 Replay（{@link AiHarnessReplayRequest#isIgnoreExpectations()} 或 {@link AiHarnessReplayRequest#isBuiltinProbeCaseId(String)}
 * 且无自定义 expectations）在每轮输出的扁平探针视图。
 */
final class AiHarnessReplayProbeView {

    private AiHarnessReplayProbeView() {
    }

    static LinkedHashMap<String, Object> fromSummary(Map<String, Object> summary, String roundMessage) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (roundMessage != null) {
            m.put("message", roundMessage.trim());
        }
        copy(m, summary, "effectiveIntentCode");
        copy(m, summary, "effectivePathCode");
        copy(m, summary, "structuredIntentDetailWire");
        copy(m, summary, "canonicalStructuredIntentDetailWire");
        copy(m, summary, "queryObject");
        copy(m, summary, "operation");
        copy(m, summary, "metric");
        copy(m, summary, "sourceFacet");
        copy(m, summary, "anchorPolicy");
        copy(m, summary, "answerPlanType");
        copy(m, summary, "timeSource");
        copy(m, summary, "startDate");
        copy(m, summary, "endDate");
        copy(m, summary, "scopeType");
        copy(m, summary, "visibleStores");
        copy(m, summary, "orchestrationSelectedAgents");
        copy(m, summary, "orchestrationSelectedTools");
        copy(m, summary, "needSemanticClarification");
        copy(m, summary, "needClarification");
        copy(m, summary, "clarificationQuestion");
        copy(m, summary, "businessDiagnosisPath");
        copy(m, summary, "businessOverviewPath");
        copy(m, summary, "revenueOverviewPath");
        copy(m, summary, "purchaseOverviewPath");
        copy(m, summary, "stockReduceQueryPath");
        copy(m, summary, "warehouseStockOverviewPath");
        copy(m, summary, "groupWarehouseStockOverview");
        copy(m, summary, "permissionDenials");
        copy(m, summary, "finalAnswerTextBlank");
        copy(m, summary, "couponCostInsightBlocked");
        copy(m, summary, "diagnosisPlanExists");
        copy(m, summary, "diagnosisPlanPresent");
        copy(m, summary, "diagnosisPlanType");
        copy(m, summary, "businessDiagnosisPlanExists"); // deprecated compat — mirrors diagnosisPlanExists
        copy(m, summary, "planSource");
        copy(m, summary, "harnessReplayPlanSource");
        copy(m, summary, "harnessReplayPurchaseAnswerPlanType");
        copy(m, summary, "harnessReplayPurchaseAnswerPlanTypeMeaningful");
        copy(m, summary, "harnessReplayPurchaseAnswerPlanProbePresent");
        copy(m, summary, "harnessReplayStockReduceAnswerPlanType");
        copy(m, summary, "harnessReplayStockReduceReduceType");
        copy(m, summary, "harnessReplayDishProfitAnswerPlanType");
        copy(m, summary, "harnessReplayDishSalesAnswerPlanType");
        copy(m, summary, "harnessReplayDishSalesRankingRowCount");
        copy(m, summary, "harnessReplayDishSalesTopDishName");
        copy(m, summary, "harnessReplayDishSalesMetricType");
        copy(m, summary, "harnessReplayBusinessDiagnosisPlanType"); // deprecated compat — mirrors diagnosisPlanType
        copy(m, summary, "harnessReplayStorePriorityRankingPlanType");
        copy(m, summary, "harnessReplayStorePriorityRankingRowsLen");
        copy(m, summary, "harnessReplayStorePriorityRankingTop1StoreName");
        copy(m, summary, "harnessReplayStorePriorityRankingTop1PriorityRank");
        copy(m, summary, "businessStoreCompareEvidenceRowsLen");
        copy(m, summary, "harnessReplayStoreCompareEvidenceRowsLen");
        copy(m, summary, "businessStoreCompareTop1StoreName");
        copy(m, summary, "businessStoreCompareTop2StoreName");
        copy(m, summary, "compositeGateAllowed");
        copy(m, summary, "compositeGateReasonCode");

        Object planTools = summary != null ? summary.get("dataPlanTools") : null;
        if (planTools == null && summary != null) {
            planTools = summary.get("usedTools");
        }
        m.put("dataPlanTools", shallowCopyList(planTools));
        copy(m, summary, "dryRunStage");
        copy(m, summary, "toolExecuteSkipped");
        copy(m, summary, "toolRequestCaptured");
        copyPlannedToolArgs(m, summary);
        copy(m, summary, "resolvedVisibleStoreRootIds");
        copy(m, summary, "resolvedEffectiveSqlDepartmentIds");
        copy(m, summary, "groupPurchaseOverview");
        copy(m, summary, "groupStockReduceQuery");
        copy(m, summary, "groupWarehouseStockOverview");
        copy(m, summary, "costInsightPath");

        Object fat = summary != null ? summary.get("finalAnswerText") : null;
        if (fat == null && summary != null) {
            fat = summary.get("answerPreview");
        }
        m.put("finalAnswerText", fat);
        copy(m, summary, "finalAnswerTextBlank");
        return m;
    }

    private static void copy(LinkedHashMap<String, Object> dest, Map<String, Object> src, String key) {
        if (src == null) {
            dest.put(key, null);
            return;
        }
        Object v = src.get(key);
        dest.put(key, shallowCopyList(v));
    }

    private static Object shallowCopyList(Object v) {
        if (v instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static void copyPlannedToolArgs(LinkedHashMap<String, Object> dest, Map<String, Object> src) {
        if (src == null) {
            dest.put("plannedToolArgsByToolId", null);
            return;
        }
        Object planned = src.get("plannedToolArgsByToolId");
        if (!(planned instanceof Map<?, ?> raw) || raw.isEmpty()) {
            dest.put("plannedToolArgsByToolId", null);
            return;
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getValue() instanceof Map<?, ?> inner) {
                copy.put(e.getKey().toString(), new LinkedHashMap<>((Map<String, Object>) inner));
            }
        }
        dest.put("plannedToolArgsByToolId", copy.isEmpty() ? null : copy);
    }
}
