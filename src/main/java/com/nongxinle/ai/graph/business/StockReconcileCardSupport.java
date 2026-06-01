package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.service.GbDishCostAnalysisService;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存 / 销货核对卡：销售 / 出库 / 毛利基础统计 + 配料理论消耗 vs 实际出库/核销差异。
 */
final class StockReconcileCardSupport {

    private static final String SOURCE = "gbDishCostAnalysisService.buildOutboundIngredientAnalysisReport";

    private StockReconcileCardSupport() {}

    static Map<String, Object> build(
            AiRunState state,
            BusinessStatusCardBuildRequest req,
            GbDishCostAnalysisService dishCostAnalysisService,
            ToolDepartmentResolutionSupport departmentResolutionSupport) {
        Map<String, Object> payload = new LinkedHashMap<>();
        BusinessStatusCardShellSupport.putRangeFields(payload, req);

        StockIngredientReconcileFactBuilder.FactResult fact =
                StockIngredientReconcileFactBuilder.build(
                        state, req, dishCostAnalysisService, departmentResolutionSupport);

        Map<String, Object> outboundSummary = fact.outboundSummary();
        payload.put("actualOutboundAmount", outboundSummary.get("actualOutboundAmount"));
        payload.put("theoryOutboundAmount", outboundSummary.get("theoryOutboundAmount"));
        payload.put("actualGrossMarginRate", outboundSummary.get("actualGrossMarginRate"));
        payload.put("theoryGrossMarginRate", outboundSummary.get("theoryGrossMarginRate"));

        List<Map<String, Object>> diffItems = fact.ingredientDiffItems();
        boolean hasSummary = StockIngredientReconcileFactBuilder.hasOutboundSummaryData(outboundSummary);
        boolean hasDiffs = !diffItems.isEmpty();
        payload.put("status", hasSummary || hasDiffs
                ? BusinessStatusCardShellSupport.STATUS_OK
                : BusinessStatusCardShellSupport.STATUS_EMPTY);
        payload.put("ingredientDiffItems", diffItems);
        if (hasDiffs) {
            payload.put("ingredientDiffItemCount", diffItems.size());
        }
        if (!hasDiffs) {
            payload.put("emptyReason", "本期未发现配料理论消耗与实际出库/核销的明显差异");
        }
        payload.put("warnings", List.of());
        return shell(req, payload);
    }

    private static Map<String, Object> shell(BusinessStatusCardBuildRequest req, Map<String, Object> payload) {
        String periodHint =
                StringUtils.hasText(req.getTimeExpression())
                        ? req.getTimeExpression()
                        : req.getReportLabel();
        String subtitle =
                StringUtils.hasText(periodHint)
                        ? periodHint + "：销售毛利统计与配料核销差异"
                        : "销售毛利统计与配料核销差异";
        return BusinessStatusCardShellSupport.buildCard(
                BusinessStatusCardTypes.STOCK_RECONCILE_CARD,
                BusinessStatusCardShellSupport.titled(req.getReportLabel(), "·库存 / 销货核对"),
                subtitle,
                BusinessStatusCardShellSupport.CHART_TABLE,
                payload,
                SOURCE);
    }
}
