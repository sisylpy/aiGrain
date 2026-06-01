package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOperationExpertNarrativeOutputGuardTest {

    private final MenuOperationAnswerPlan plan =
            MenuOperationAnswerPlan.builder()
                    .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                    .menuOptimizationPlan(
                            MenuOptimizationPlan.builder()
                                    .capabilityLimits(
                                            new LinkedHashMap<>(Map.of("latestPurchasePrice", "NOT_IN_P1")))
                                    .watchListDishes(
                                            List.of(
                                                    MenuOptimizationDishItem.builder()
                                                            .dishName("观察菜")
                                                            .suggestedActionLabel("继续观察")
                                                            .build()))
                                    .build())
                    .build();

    @Test
    void rejectsInternalCodesAndLossWithoutEvidence() {
        assertFalse(
                MenuOperationExpertNarrativeOutputGuard.accepts(
                        "总体判断：NOT_IN_P1 不应出现。", plan));
        assertFalse(
                MenuOperationExpertNarrativeOutputGuard.accepts(
                        "总体判断：酸奶碗虽然畅销但已经亏损，建议立即处理。", plan));
    }

    @Test
    void acceptsConsultantStyleNarrative() {
        String narrative =
                """
                ## 总体判断
                这次菜单优化的核心矛盾，不是急着下架菜，而是先处理畅销低利菜，同时保护已有利润菜。

                ## 优先动作
                酸奶碗、核桃芽菜西芹要先复核成本、用量和定价；椒麻鸡适合稳定主推；香煎青鱼可以增加曝光。

                ## 菜单经营建议
                引流菜不要直接砍，先看成本和套餐搭配；明星菜要保供应和出品稳定；潜力菜可以尝试推荐位优化。

                ## 本周先做的事
                先复核「酸奶碗」的成本结构、标准用量与损耗。

                ## 能力边界
                当前版本暂不提供最新采购价与连续多周期趋势，因此不做强下架建议。
                """;
        assertTrue(MenuOperationExpertNarrativeOutputGuard.accepts(narrative, plan));
    }
}
