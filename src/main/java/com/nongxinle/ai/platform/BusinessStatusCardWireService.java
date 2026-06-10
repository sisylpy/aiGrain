package com.nongxinle.ai.platform;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.BusinessStatusCardBuildDeps;
import com.nongxinle.ai.graph.business.BusinessStatusCardProjection;
import com.nongxinle.ai.graph.business.BusinessStatusCardWireSupport;
import com.nongxinle.ai.graph.business.BusinessOverviewDishSalesReasonAgent;
import com.nongxinle.ai.graph.business.ToolDepartmentResolutionSupport;
import com.nongxinle.ai.tool.business.PurchaseOverviewTool;
import com.nongxinle.service.GbDepartmentReorderReminderService;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 经营状态业务卡：Composer 内由 {@link BusinessStatusCardWireSupport#resolveProjection} 决定投影。 */
@Service
public class BusinessStatusCardWireService {

    @Autowired(required = false)
    private GbDepartmentReorderReminderService reorderReminderService;

    @Autowired(required = false)
    private GbDishCostAnalysisService dishCostAnalysisService;

    @Autowired(required = false)
    private GbDepFoodBusinessInsightService depFoodBusinessInsightService;

    @Autowired(required = false)
    private ToolDepartmentResolutionSupport toolDepartmentResolutionSupport;

    @Autowired(required = false)
    private BusinessOverviewDishSalesReasonAgent dishSalesReasonAgent;

    @Autowired(required = false)
    private GbDistributerPurchaseGoodsService purchaseGoodsService;

    @Autowired(required = false)
    private PurchaseOverviewTool purchaseOverviewTool;

    public BusinessStatusCardProjection resolveProjection(AiRunState state) {
        return BusinessStatusCardWireSupport.resolveProjection(state);
    }

    public void attachBusinessStatusCardsIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        BusinessStatusCardProjection projection = BusinessStatusCardWireSupport.resolveProjection(state);
        if (projection == BusinessStatusCardProjection.NONE) {
            return;
        }
        BusinessStatusCardBuildDeps deps =
                BusinessStatusCardBuildDeps.builder()
                        .reorderReminderService(reorderReminderService)
                        .dishCostAnalysisService(dishCostAnalysisService)
                        .depFoodBusinessInsightService(depFoodBusinessInsightService)
                        .toolDepartmentResolutionSupport(toolDepartmentResolutionSupport)
                        .dishSalesReasonAgent(dishSalesReasonAgent)
                        .purchaseGoodsService(purchaseGoodsService)
                        .purchaseOverviewTool(purchaseOverviewTool)
                        .build();
        List<Map<String, Object>> cards =
                BusinessStatusCardWireSupport.buildCards(state, projection, deps);
        if (!BusinessStatusCardWireSupport.hasBusinessStatusCards(cards)) {
            return;
        }
        state.setCards(cards);
        state.setCardPayload(AiCardPayloadWireSupport.buildDeprecatedCardPayloadCompatFromCards(cards));
    }
}
