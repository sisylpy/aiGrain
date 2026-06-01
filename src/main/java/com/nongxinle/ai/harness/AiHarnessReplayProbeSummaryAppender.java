package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.harness.replay.AiHarnessReplayContextProbes;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replay / Graph RunState 探针、answerPreview、consumedAnswerPlans 等观测字段。
 */
final class AiHarnessReplayProbeSummaryAppender {

    private static final int ANSWER_PREVIEW_MAX_LEN = 500;

    private AiHarnessReplayProbeSummaryAppender() {
    }

    static void appendAnswerPreviewAndDiagnosisPlanWireFields(LinkedHashMap<String, Object> out, AiRunState state) {
        String fat = state.getFinalAnswerText();
        if (!StringUtils.hasText(fat)) {
            String syn = synthesizePurchaseSupplierGoodsDetailAnswerPreview(state);
            out.put("finalAnswerText", null);
            if (StringUtils.hasText(syn)) {
                out.put("answerPreview", syn.substring(0, Math.min(ANSWER_PREVIEW_MAX_LEN, syn.length())));
            } else {
                out.put("answerPreview", null);
            }
        } else {
            out.put("finalAnswerText", fat);
            out.put("answerPreview", fat.substring(0, Math.min(ANSWER_PREVIEW_MAX_LEN, fat.length())));
        }
        DiagnosisPlan dp = state.getDiagnosisPlan();
        if (dp == null || dp.getDebug() == null || dp.getDebug().isEmpty()) {
            synthesizeConsumedAnswerPlansFromWireAnswerPlans(out, state);
            return;
        }
        List<String> consumed = AiHarnessSummaryUtils.stringListFromDebugList(dp.getDebug().get("consumedAnswerPlans"));
        List<String> missing = AiHarnessSummaryUtils.stringListFromDebugList(dp.getDebug().get("missingAnswerPlans"));
        out.put("consumedAnswerPlans", consumed == null || consumed.isEmpty() ? null : consumed);
        out.put("missingAnswerPlans", missing == null || missing.isEmpty() ? null : missing);
    }

    static void appendAnswerContextHarnessFields(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            out.put("answerContextPreambleDebug", null);
            out.put("answerContextSummary", null);
            return;
        }
        out.put(
                "answerContextPreambleDebug",
                AiHarnessSummaryUtils.blankToNull(state.getAnswerContextPreambleDebug()));
        Map<String, Object> summary = state.getAnswerContextSummary();
        if (summary == null || summary.isEmpty()) {
            out.put("answerContextSummary", null);
        } else {
            out.put("answerContextSummary", new LinkedHashMap<>(summary));
        }
    }

    private static void synthesizeConsumedAnswerPlansFromWireAnswerPlans(
            LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            out.put("consumedAnswerPlans", null);
            out.put("missingAnswerPlans", null);
            return;
        }
        List<String> acc = new ArrayList<>();
        if (state.getRevenueAnswerPlan() != null) {
            acc.add("DailyRevenueAnswerPlan");
        }
        if (state.getPurchaseAnswerPlan() != null) {
            acc.add("PurchaseAnswerPlan");
        }
        if (state.getStockReduceAnswerPlan() != null) {
            acc.add("StockReduceAnswerPlan");
        }
        boolean dishSalesPrimary = isDishSalesPrimaryHarnessPath(state);
        if (dishSalesPrimary) {
            if (state.getDishSalesAnswerPlan() != null) {
                acc.add("DishSalesAnswerPlan");
            }
        } else {
            if (state.getDishProfitAnswerPlan() != null) {
                acc.add("DishProfitAnswerPlan");
            }
            if (state.getDishSalesAnswerPlan() != null) {
                acc.add("DishSalesAnswerPlan");
            }
        }
        if (warehouseStockOverviewEligibleForConsumedProbe(state)) {
            acc.add("WarehouseStockOverview");
        }
        if (state.getDishProfitPrescriptionAnswerPlan() != null) {
            acc.add("DishProfitPrescriptionAnswerPlan");
        }
        if (state.getDishIngredientCoverAnswerPlan() != null) {
            acc.add("DishIngredientCoverAnswerPlan");
        }
        if (state.getGoodsSupportedDishCoverAnswerPlan() != null) {
            acc.add("GoodsSupportedDishCoverAnswerPlan");
        }
        if (acc.isEmpty()) {
            out.put("consumedAnswerPlans", null);
            out.put("missingAnswerPlans", null);
        } else {
            out.put("consumedAnswerPlans", acc);
            out.put("missingAnswerPlans", new ArrayList<String>());
        }
    }

    private static boolean isDishSalesPrimaryHarnessPath(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        String effIntent =
                rq.getEffectiveIntentCode() == null ? null : rq.getEffectiveIntentCode().trim();
        String effPath = rq.getEffectivePathCode() == null ? null : rq.getEffectivePathCode().trim();
        return AiResolvedQueryIntent.DISH_SALES_QUERY.equals(effIntent)
                || AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effPath);
    }

    private static boolean warehouseStockOverviewEligibleForConsumedProbe(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.getWarehouseOverview() != null && !state.getWarehouseOverview().isEmpty()) {
            return true;
        }
        if (state.getToolResults() == null || state.getToolResults().isEmpty()) {
            return false;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        return raw instanceof Map<?, ?> && !((Map<?, ?>) raw).isEmpty();
    }

    static String synthesizePurchaseSupplierGoodsDetailAnswerPreview(AiRunState state) {
        if (state == null) {
            return null;
        }
        PurchaseAnswerPlan pap = state.getPurchaseAnswerPlan();
        if (pap == null
                || (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(pap.getPlanType())
                        && !PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL.equals(pap.getPlanType()))) {
            return null;
        }
        Map<String, Object> d = pap.getDebug();
        String gn =
                d == null ? null : AiHarnessSummaryUtils.blankToNull(
                        AiHarnessSummaryUtils.stringifyHarnessDbg(
                                d.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME)));
        Object cntObj = d == null ? null : d.get("purchaseSupplierGoodsDetailRowsCount");
        int cnt = cntObj instanceof Number ? ((Number) cntObj).intValue() : -1;
        String reason =
                d == null ? null : AiHarnessSummaryUtils.blankToNull(
                        AiHarnessSummaryUtils.stringifyHarnessDbg(d.get("purchaseSupplierGoodsDetailNoDataReason")));
        Object altObj = d == null ? null : d.get("purchaseSupplierGoodsDetailAlternativeHasData");
        boolean alt = Boolean.TRUE.equals(altObj);
        StringBuilder sb = new StringBuilder("[Harness][采购供货商商品单价]");
        if (StringUtils.hasText(gn)) {
            sb.append(' ').append(gn.trim());
        }
        if (cnt >= 0) {
            sb.append(" rows=").append(cnt);
        }
        if (StringUtils.hasText(reason)) {
            sb.append(" reason=").append(reason);
        }
        if (alt) {
            sb.append(" altSelfPurchaseData=true");
        }
        String s = sb.toString();
        return s.isEmpty() ? null : s;
    }

    static void mirrorHarnessReplayProbesPresenceFromAnswerPlans(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            return;
        }
        AiResolvedQueryContext rqExe = state.getResolvedQueryContext();
        String pathEff = rqExe != null ? AiHarnessSummaryUtils.blankToNull(rqExe.getEffectivePathCode()) : null;
        String intentEff = rqExe != null ? AiHarnessSummaryUtils.blankToNull(rqExe.getEffectiveIntentCode()) : null;

        Object planSrc = out.get("planSource");
        if (planSrc != null && StringUtils.hasText(planSrc.toString())) {
            out.putIfAbsent("harnessReplayPlanSource", planSrc.toString().trim());
        }

        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathEff)
                || AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathEff)
                || AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(pathEff)) {
            boolean revenueEvidence =
                    state.getRevenueAnswerPlan() != null
                            || Boolean.TRUE.equals(out.get("revenueAnswerPlanPresent"))
                            || harnessConsumedAnswerPlansIndicatesDailyRevenue(out);
            out.putIfAbsent("harnessReplayRevenueAnswerPlanProbePresent", revenueEvidence);
            Object rtp = out.get("revenueAnswerPlanType");
            if (rtp != null && StringUtils.hasText(rtp.toString())) {
                out.putIfAbsent("harnessReplayRevenueAnswerPlanType", rtp.toString().trim());
            }
        }

        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathEff)) {
            out.putIfAbsent(
                    "harnessReplayPurchaseAnswerPlanProbePresent",
                    Boolean.TRUE.equals(out.get("purchaseAnswerPlanPresent")));
            Object pt = out.get("purchaseAnswerPlanType");
            if (pt != null && StringUtils.hasText(pt.toString())) {
                out.putIfAbsent("harnessReplayPurchaseAnswerPlanType", pt.toString().trim());
            }
        }

        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathEff)) {
            Object st = out.get("stockReduceAnswerPlanType");
            if (st != null && StringUtils.hasText(st.toString())) {
                out.putIfAbsent("harnessReplayStockReduceAnswerPlanType", st.toString().trim());
            }
            Object sd = out.get("stockReduceAnswerPlanSortDirection");
            if (sd != null && StringUtils.hasText(sd.toString())) {
                out.putIfAbsent("harnessReplayStockReduceAnswerPlanSortDirection", sd.toString().trim());
            }
            StockReduceAnswerPlan srp = state.getStockReduceAnswerPlan();
            if (srp != null && StringUtils.hasText(srp.getPlanType())) {
                String rt = AiHarnessReplayContextProbes.resolveStockReduceType(srp.getPlanType());
                if (StringUtils.hasText(rt)) {
                    out.putIfAbsent("harnessReplayStockReduceReduceType", rt.trim());
                }
            }
        }

        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathEff)) {
            DishProfitAnswerPlan dpp = state.getDishProfitAnswerPlan();
            if (dpp != null && StringUtils.hasText(dpp.getPlanType())) {
                String dpt = dpp.getPlanType().trim();
                out.putIfAbsent("harnessReplayDishProfitAnswerPlanType", dpt);
                if (DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(dpt)
                        || DishProfitAnswerPlan.TYPE_DISH_LOWEST_PROFIT_AMOUNT.equals(dpt)) {
                    out.putIfAbsent("harnessReplayDishProfitAnswerPlanSortDirection", "ASC");
                } else if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN.equals(dpt)
                        || DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT.equals(dpt)
                        || DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST.equals(dpt)) {
                    out.putIfAbsent("harnessReplayDishProfitAnswerPlanSortDirection", "DESC");
                }
                if (StringUtils.hasText(dpp.getSortKey())) {
                    out.putIfAbsent("dishProfitAnswerPlanSortKey", dpp.getSortKey().trim());
                } else if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST.equals(dpt)) {
                    out.putIfAbsent("dishProfitAnswerPlanSortKey", "totalActualCostAmount123");
                } else if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT.equals(dpt)) {
                    out.putIfAbsent("dishProfitAnswerPlanSortKey", "grossProfitAmount");
                }
            }
        }

        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathEff)
                || AiResolvedQueryIntent.DISH_SALES_QUERY.equals(intentEff)) {
            DishSalesAnswerPlan dsp = state.getDishSalesAnswerPlan();
            if (dsp != null) {
                if (StringUtils.hasText(dsp.getPlanType())) {
                    out.putIfAbsent("harnessReplayDishSalesAnswerPlanType", dsp.getPlanType().trim());
                }
                if (dsp.getRankingRows() != null) {
                    out.putIfAbsent("harnessReplayDishSalesRankingRowCount", dsp.getRankingRows().size());
                }
                if (StringUtils.hasText(dsp.getMetricType())) {
                    out.putIfAbsent("harnessReplayDishSalesMetricType", dsp.getMetricType().trim());
                }
                List<Map<String, Object>> rrows = dsp.getRankingRows();
                if (rrows != null && !rrows.isEmpty()) {
                    Map<String, Object> top = rrows.get(0);
                    if (top != null) {
                        Object nm = top.get("dishName");
                        if (nm != null && StringUtils.hasText(nm.toString())) {
                            out.putIfAbsent("harnessReplayDishSalesTopDishName", nm.toString().trim());
                        }
                    }
                }
            }
        }

        if (isHarnessStoreCompareDiagnosisWire(rqExe)) {
            DiagnosisPlan dpc = state.getDiagnosisPlan();
            List<Map<String, Object>> cev = dpc != null ? dpc.getStoreCompareEvidence() : null;
            int crLen = cev == null ? 0 : cev.size();
            out.putIfAbsent("harnessReplayStoreCompareEvidenceRowsLen", crLen);
        }
    }

    private static boolean isHarnessStoreCompareDiagnosisWire(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return false;
        }
        String canon =
                com.nongxinle.ai.conversation.AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        rq.getQueryIntent().getStructuredIntentDetail());
        return com.nongxinle.ai.conversation.AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon);
    }

    private static boolean harnessConsumedAnswerPlansIndicatesDailyRevenue(LinkedHashMap<String, Object> out) {
        Object raw = out.get("consumedAnswerPlans");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object x : list) {
            if (x == null) {
                continue;
            }
            String s = x.toString().trim();
            if (s.startsWith("DailyRevenueAnswerPlan")) {
                return true;
            }
        }
        return false;
    }

    static void putHarnessReplayGraphRunStateProbeDefaults(LinkedHashMap<String, Object> out) {
        out.put("needSemanticClarification", null);
        out.put("needClarification", null);
        out.put("clarificationQuestion", null);
        out.put("businessDiagnosisPath", null);
        out.put("businessOverviewPath", null);
        out.put("revenueOverviewPath", null);
        out.put("purchaseOverviewPath", null);
        out.put("stockReduceQueryPath", null);
        out.put("warehouseStockOverviewPath", null);
        out.put("groupWarehouseStockOverview", null);
        out.put("groupPurchaseOverview", null);
        out.put("groupStockReduceQuery", null);
        out.put("costInsightPath", null);
        out.put("dishCostAnalysisPath", null);
        out.put("permissionDenials", null);
        out.put("finalAnswerTextBlank", null);
        out.put("couponCostInsightBlocked", null);
        out.put("diagnosisPlanExists", null);
        AiHarnessAnswerPlanSummaryAppender.emitDeprecatedDiagnosisPlanHarnessCompatDefaults(out);
    }

    static void appendHarnessReplayGraphRunStateProbes(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            putHarnessReplayGraphRunStateProbeDefaults(out);
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        out.put("needSemanticClarification",
                rq != null ? Boolean.valueOf(rq.isNeedSemanticClarification()) : null);
        out.put("needClarification", Boolean.valueOf(state.isNeedClarification()));
        out.put("clarificationQuestion", AiHarnessSummaryUtils.blankToNull(state.getClarificationQuestion()));
        out.put("businessDiagnosisPath", Boolean.valueOf(state.isBusinessDiagnosisPath()));
        out.put("businessOverviewPath", Boolean.valueOf(state.isBusinessOverviewPath()));
        out.put("revenueOverviewPath", Boolean.valueOf(state.isRevenueOverviewPath()));
        out.put("purchaseOverviewPath", Boolean.valueOf(state.isPurchaseOverviewPath()));
        out.put("stockReduceQueryPath", Boolean.valueOf(state.isStockReduceQueryPath()));
        out.put("warehouseStockOverviewPath", Boolean.valueOf(state.isWarehouseStockOverviewPath()));
        out.put("groupWarehouseStockOverview", Boolean.valueOf(state.isGroupWarehouseStockOverview()));
        out.put("groupPurchaseOverview", Boolean.valueOf(state.isGroupPurchaseOverview()));
        out.put("groupStockReduceQuery", Boolean.valueOf(state.isGroupStockReduceQuery()));
        out.put("costInsightPath", Boolean.valueOf(state.isCostInsightPath()));
        out.put("dishCostAnalysisPath", Boolean.valueOf(state.isDishCostAnalysisPath()));

        List<AiPermissionDenied> denials = state.getPermissionDenials();
        if (denials == null || denials.isEmpty()) {
            out.put("permissionDenials", null);
        } else {
            List<Map<String, Object>> rows = new ArrayList<>(denials.size());
            for (AiPermissionDenied d : denials) {
                if (d != null) {
                    rows.add(d.asDataMap());
                }
            }
            out.put("permissionDenials", rows.isEmpty() ? null : rows);
        }

        String fat = state.getFinalAnswerText();
        out.put("finalAnswerTextBlank", Boolean.valueOf(!StringUtils.hasText(fat)));
        out.put("couponCostInsightBlocked", Boolean.valueOf(state.isCouponCostInsightBlocked()));
        out.put("diagnosisPlanExists", Boolean.valueOf(state.getDiagnosisPlan() != null));
        AiHarnessAnswerPlanSummaryAppender.emitDeprecatedDiagnosisPlanHarnessCompatKeys(out, state, rq, state.getDiagnosisPlan());
    }

    static void appendHarnessToolRequestOnlyFields(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null || !state.isHarnessToolRequestOnly()) {
            out.put("dryRunStage", null);
            out.put("toolExecuteSkipped", null);
            out.put("toolRequestCaptured", null);
            out.put("plannedToolArgsByToolId", null);
            return;
        }
        out.put("dryRunStage", "TOOL_REQUEST_ONLY");
        out.put("toolExecuteSkipped", Boolean.valueOf(state.isToolExecuteSkipped()));
        out.put("toolRequestCaptured", Boolean.valueOf(state.isToolRequestCaptured()));
        Map<String, Map<String, Object>> planned = state.getPlannedToolArgsByToolId();
        if (planned == null || planned.isEmpty()) {
            out.put("plannedToolArgsByToolId", null);
        } else {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : planned.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    copy.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
                }
            }
            out.put("plannedToolArgsByToolId", copy.isEmpty() ? null : copy);
        }
    }

    static void appendMenuExpertPromptPreview(LinkedHashMap<String, Object> out, AiRunState state) {
        appendMenuExpertComposerDebug(out, state);
    }

    static void appendMenuExpertComposerDebug(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            out.put("menuExpertPromptPreview", null);
            out.put("menuExpertLlmOutputPreview", null);
            out.put("menuExpertComposerDecision", null);
            return;
        }
        out.put(
                "menuExpertPromptPreview",
                state.getMenuExpertPromptPreview() == null || state.getMenuExpertPromptPreview().isEmpty()
                        ? null
                        : new LinkedHashMap<>(state.getMenuExpertPromptPreview()));
        out.put(
                "menuExpertLlmOutputPreview",
                state.getMenuExpertLlmOutputPreview() == null || state.getMenuExpertLlmOutputPreview().isEmpty()
                        ? null
                        : new LinkedHashMap<>(state.getMenuExpertLlmOutputPreview()));
        out.put(
                "menuExpertComposerDecision",
                state.getMenuExpertComposerDecision() == null || state.getMenuExpertComposerDecision().isEmpty()
                        ? null
                        : new LinkedHashMap<>(state.getMenuExpertComposerDecision()));
    }

    static void appendDishSalesReasonAgentHarnessFields(LinkedHashMap<String, Object> out, AiRunState state) {
        BusinessOverviewDishSalesReasonAgentHarnessSupport.appendFlatHarnessFields(out, state);
    }
}
