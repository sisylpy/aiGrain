package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOperationOptimizationRendererTest {

    private final MenuOperationDeterministicRenderer renderer = new MenuOperationDeterministicRenderer();

    @Test
    void render_actionRecommendation_readsOptimizationPlanNotEnglishCodes() {
        MenuOptimizationPlan optimization =
                MenuOptimizationPlan.builder()
                        .optimizationSummary("本月菜单优化重点是先复核引流菜。")
                        .priorityGroups(
                                List.of(
                                        MenuOptimizationPriorityGroup.builder()
                                                .groupCode(MenuOperationAnswerPlan.OPT_GROUP_PRIORITY_HANDLE)
                                                .groupName("优先处理")
                                                .priority(1)
                                                .reason("相对引流档需复核成本")
                                                .dishes(
                                                        List.of(
                                                                MenuOptimizationDishItem.builder()
                                                                        .dishName("酸奶碗")
                                                                        .suggestedActionLabel("复核成本")
                                                                        .blendedGrossMarginRateOnListPrice("18.00%")
                                                                        .build()))
                                                .build()))
                        .nextSteps(List.of("先复核「酸奶碗」的成本结构、标准用量与损耗"))
                        .capabilityLimits(
                                new LinkedHashMap<>(
                                        Map.of(
                                                "latestPurchasePrice",
                                                "NOT_IN_P1",
                                                "externalMarketBenchmark",
                                                "NOT_IN_P1")))
                        .build();

        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                        .scopeLabel("集团")
                        .timeLabel("2026年4月")
                        .summaryFacts(Map.of("dishCountAnalyzed", "12", "comprehensiveGrossMarginRate", "32.50%"))
                        .menuOptimizationPlan(optimization)
                        .knownGaps(List.of("MENU_PRICING_ADVICE_NOT_IN_P1"))
                        .build();

        String text = renderer.render(plan);

        assertTrue(text.contains("范围：集团"));
        assertTrue(text.contains("时间：2026年4月"));
        assertTrue(text.contains("本月菜单优化重点是先复核引流菜"));
        assertTrue(text.contains("优先处理"));
        assertTrue(text.contains("酸奶碗"));
        assertTrue(text.contains("本周可先做的事"));
        assertTrue(text.contains("最新采购价"));
        assertFalse(text.contains("NOT_IN_P1"));
        assertFalse(text.contains("MENU_PRICING_ADVICE_NOT_IN_P1"));
        assertFalse(text.contains("分析菜品数"));
        assertFalse(text.contains("关键证据"));
    }
}
