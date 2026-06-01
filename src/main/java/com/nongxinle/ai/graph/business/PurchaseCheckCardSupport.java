package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 经营采购卡：按查询周期只读查库，单日 vs 上一笔、多日 vs 对比期均价。 */
final class PurchaseCheckCardSupport {

    private static final String SOURCE = "purchaseCheckCardFactBuilder";

    private PurchaseCheckCardSupport() {}

    static Map<String, Object> build(
            AiRunState state, BusinessStatusCardBuildRequest req, BusinessStatusCardBuildDeps deps) {
        Map<String, Object> payload = new LinkedHashMap<>();
        BusinessStatusCardShellSupport.putRangeFields(payload, req);

        PurchaseCheckCardFactBuilder.FactResult fact =
                PurchaseCheckCardFactBuilder.build(
                        state,
                        req,
                        deps != null ? deps.getPurchaseGoodsService() : null,
                        deps != null ? deps.getPurchaseOverviewTool() : null,
                        deps != null ? deps.getToolDepartmentResolutionSupport() : null);

        payload.put("priceCompareMode", fact.priceCompareMode());
        payload.put(
                "priceCompareLabel",
                PurchaseCheckCardFactBuilder.priceCompareLabel(fact.priceCompareMode(), req));
        payload.put(
                "priceCompareDescription",
                PurchaseCheckCardFactBuilder.priceCompareDescription(fact.priceCompareMode(), req));
        Map<String, Object> purchaseSummary = fact.purchaseSummary();
        payload.put("totalPurchaseAmount", purchaseSummary.get("totalPurchaseAmount"));
        payload.put("selfPurchaseAmount", purchaseSummary.get("selfPurchaseAmount"));
        payload.put("supplierPurchaseAmount", purchaseSummary.get("supplierPurchaseAmount"));
        List<Map<String, Object>> unitPriceChanged = fact.unitPriceChangedItems();
        boolean hasSummary = PurchaseCheckCardFactBuilder.hasPurchaseSummaryData(purchaseSummary);
        boolean hasPriceChanges = !unitPriceChanged.isEmpty();
        payload.put("status", hasSummary || hasPriceChanges
                ? BusinessStatusCardShellSupport.STATUS_OK
                : BusinessStatusCardShellSupport.STATUS_EMPTY);
        payload.put("unitPriceChangedItems", unitPriceChanged);
        if (!hasPriceChanges) {
            payload.put("emptyReason", PurchaseCheckCardFactBuilder.emptyReasonForMode(fact.priceCompareMode()));
        }
        payload.put("warnings", List.of());

        return shell(req, fact.subtitle(), payload);
    }

    private static Map<String, Object> shell(
            BusinessStatusCardBuildRequest req, String subtitle, Map<String, Object> payload) {
        return BusinessStatusCardShellSupport.buildCard(
                BusinessStatusCardTypes.PURCHASE_CHECK_CARD,
                BusinessStatusCardShellSupport.titled(req.getReportLabel(), "·采购"),
                subtitle,
                BusinessStatusCardShellSupport.CHART_TABLE,
                payload,
                SOURCE);
    }
}
