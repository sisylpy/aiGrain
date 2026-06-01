package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.tool.business.PurchaseOverviewTool;
import com.nongxinle.service.GbDepartmentReorderReminderService;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.Builder;
import lombok.Value;

/** Card 投影层可选依赖（订货 / 配料核对 / 营业额 Agent / 采购卡查库）。 */
@Value
@Builder
public class BusinessStatusCardBuildDeps {

    GbDepartmentReorderReminderService reorderReminderService;
    GbDishCostAnalysisService dishCostAnalysisService;
    GbDepFoodBusinessInsightService depFoodBusinessInsightService;
    ToolDepartmentResolutionSupport toolDepartmentResolutionSupport;
    BusinessOverviewDishSalesReasonAgent dishSalesReasonAgent;
    GbDistributerPurchaseGoodsService purchaseGoodsService;
    PurchaseOverviewTool purchaseOverviewTool;
}
