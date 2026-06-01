package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.composer.menu.MenuOperationPortfolioExpressionSupport;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioCategory;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioClassification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOperationDeterministicRendererTest {

    private final MenuOperationDeterministicRenderer renderer = new MenuOperationDeterministicRenderer();

    @Test
    void render_highSalesLowProfit_knownGaps_useNaturalLanguageNotDebugCodes() {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT)
                        .knownGaps(
                                List.of(
                                        "DISH_INGREDIENT_COST_BREAKDOWN_NOT_IN_P1",
                                        "MENU_PRICING_ADVICE_NOT_IN_P1",
                                        "TIME_SCOPE_FOLLOW_UP_INHERITANCE_LABEL_INACCURATE"))
                        .build();

        String answer = renderer.render(plan);

        assertTrue(answer.contains("当前暂不支持食材级成本拆解和自动定价建议。"));
        assertFalse(answer.contains("DISH_INGREDIENT_COST_BREAKDOWN_NOT_IN_P1"));
        assertFalse(answer.contains("MENU_PRICING_ADVICE_NOT_IN_P1"));
        assertFalse(answer.contains("TIME_SCOPE_FOLLOW_UP_INHERITANCE_LABEL_INACCURATE"));
    }

    @Test
    void render_overviewPortfolio_usesTimeLabelInLeadLine() {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                        .timeLabel("上个月")
                        .menuPortfolioClassification(
                                MenuPortfolioClassification.builder()
                                        .totalDishCount(4)
                                        .salesMetricName("销量")
                                        .profitMetricName("利润")
                                        .thresholdMethod("median")
                                        .categories(
                                                List.of(
                                                        MenuPortfolioCategory.builder()
                                                                .categoryCode(MenuOperationAnswerPlan.CATEGORY_STAR)
                                                                .categoryName("明星菜")
                                                                .count(1)
                                                                .build()))
                                        .build())
                        .build();

        String answer = renderer.render(plan);

        assertTrue(answer.contains("上个月共分析 4 道菜"));
        assertFalse(answer.contains("本月共分析"));
    }

    @Test
    void render_overviewPortfolio_usesBossFriendlyWordingNotTechnicalThresholds() {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                        .timeLabel("上个月")
                        .menuPortfolioClassification(
                                MenuPortfolioClassification.builder()
                                        .totalDishCount(4)
                                        .salesMetricName("soldPortionsTotal")
                                        .profitMetricName("actualProfitAmount")
                                        .thresholdMethod("median")
                                        .salesHighThreshold("243")
                                        .profitHighThreshold("9440.76")
                                        .categories(
                                                List.of(
                                                        MenuPortfolioCategory.builder()
                                                                .categoryCode(
                                                                        MenuOperationAnswerPlan.CATEGORY_ELIMINATE)
                                                                .categoryName("观察菜")
                                                                .count(1)
                                                                .ratio("25.00%")
                                                                .summary("本轮菜单内相对分类：观察菜 1 道，占分析菜品 25.00%")
                                                                .recommendedAction(
                                                                        "相对低销量且实际利润低于本轮中位，建议先观察并复核曝光与备货（不等同建议下架）")
                                                                .build()))
                                        .build())
                        .build();

        String answer = renderer.render(plan);

        assertTrue(answer.contains("系统会把本期参与销售的菜品放在一起比较"));
        assertTrue(answer.contains("观察菜 1 道"));
        assertTrue(
                answer.contains(
                        MenuOperationPortfolioExpressionSupport.ELIMINATE_RECOMMENDED_ACTION));
        assertFalse(answer.contains("中位数"));
        assertFalse(answer.contains("median"));
        assertFalse(answer.contains("salesHighThreshold"));
        assertFalse(answer.contains("相对明星档"));
        assertFalse(answer.contains("本轮中位"));
    }
}
