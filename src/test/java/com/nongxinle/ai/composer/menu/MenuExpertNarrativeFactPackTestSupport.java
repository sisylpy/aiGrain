package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 菜单专家 LLM 测试：构造 dish_profit_analysis 快照。 */
final class MenuExpertNarrativeFactPackTestSupport {

    private MenuExpertNarrativeFactPackTestSupport() {}

    static AiRunState stateWithYogurtBowlRow() {
        Map<String, Object> dishRow = new LinkedHashMap<>();
        dishRow.put("dishName", "酸奶碗");
        dishRow.put("soldPortionsTotal", "100");
        dishRow.put("listPriceRevenue", "6200");
        dishRow.put("actualCostTotalAmount123", "5000");
        dishRow.put("blendedGrossMarginRateOnListPrice", "18");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dishRows", List.of(dishRow));
        data.put("businessInsightSummary", Map.of("comprehensiveGrossMarginRateOnListPrice", "18"));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", true);
        envelope.put("data", data);

        return AiRunState.builder()
                .toolResults(Map.of(AiBusinessToolIds.DISH_PROFIT_ANALYSIS, envelope))
                .build();
    }
}
