package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 采购异常 AnswerPlan → 专属异常事实卡（非 PURCHASE_CHECK_CARD 泛化投影）。 */
final class PurchaseAnomalyAnswerPlanCardSupport {

    private PurchaseAnomalyAnswerPlanCardSupport() {}

    static Map<String, Object> buildCard(PurchaseAnswerPlan plan) {
        if (plan == null) {
            return null;
        }
        Map<String, Object> debug = plan.getDebug();
        String wire = debug != null && debug.get("structuredIntentDetailWire") != null
                ? debug.get("structuredIntentDetailWire").toString()
                : null;
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        String kind = anomalyKindLabel(canon);
        boolean factsAvailable =
                debug == null
                        || !Boolean.FALSE.equals(debug.get("anomalyFactsAvailable"));

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("anomalyKind", kind);
        payload.put("anomalyWire", canon);
        if (debug != null && debug.get("anomalySubtype") != null) {
            payload.put("anomalySubtype", debug.get("anomalySubtype"));
        }
        payload.put("anomalyFactsAvailable", factsAvailable);
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(canon)) {
            payload.put(
                    "priceCompareMode",
                    PurchasePriceAnomalyBatchDetailSupport.PRICE_COMPARE_CURRENT_VS_PREVIOUS_BATCH);
            payload.put(
                    "focusRows",
                    PurchasePriceAnomalyBatchDetailSupport.projectPriceAnomalyFocusRows(plan.getFocusRows()));
        } else {
            payload.put("focusRows", plan.getFocusRows() != null ? plan.getFocusRows() : List.of());
        }
        payload.put("secondaryRows", plan.getSecondaryRows() != null ? plan.getSecondaryRows() : List.of());
        payload.put("timeLabel", plan.getTimeLabel());
        payload.put("scopeLabel", plan.getScopeLabel());
        boolean hasRows = plan.getFocusRows() != null && !plan.getFocusRows().isEmpty();
        if (!factsAvailable) {
            payload.put("status", BusinessStatusCardShellSupport.STATUS_EMPTY);
            payload.put("emptyReason", "当前异常专链缺少结构化异常判定结果，未用普通排行数据替代。");
            payload.put("projectionGap", Boolean.TRUE);
        } else {
            payload.put("status", hasRows
                    ? BusinessStatusCardShellSupport.STATUS_OK
                    : BusinessStatusCardShellSupport.STATUS_EMPTY);
            if (!hasRows) {
                payload.put("emptyReason", "当前统计区间内未检出符合「" + kind + "」口径的异常项。");
            }
        }

        return BusinessStatusCardShellSupport.buildCard(
                PurchaseAnswerPlan.CARD_TYPE_PURCHASE_ANOMALY,
                "采购异常·" + kind,
                plan.getScopeLabel(),
                BusinessStatusCardShellSupport.CHART_TABLE,
                payload,
                "purchaseAnomalyAnswerPlanCard");
    }

    private static String anomalyKindLabel(String wire) {
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(wire)) {
            return "单价波动";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY.equals(wire)) {
            return "采购次数";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_QUANTITY_ANOMALY.equals(wire)) {
            return "采购数量";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(wire)) {
            return "金额突增";
        }
        return "综合";
    }
}
