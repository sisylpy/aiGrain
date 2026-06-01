package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuOperationPortfolioExpressionSupportTest {

    @Test
    void rewritePortfolioDishReason_mapsMedianWordingToBossLanguage() {
        String rewritten =
                MenuOperationPortfolioExpressionSupport.rewritePortfolioDishReason(
                        "销量低于本轮中位，实际利润低于本轮中位（type123）",
                        MenuOperationAnswerPlan.CATEGORY_ELIMINATE);

        assertThat(rewritten).contains("销量低于本期大多数菜");
        assertThat(rewritten).contains("利润贡献低于本期大多数菜");
        assertThat(rewritten).doesNotContain("中位");
        assertThat(rewritten).doesNotContain("type123");
        assertThat(rewritten).doesNotContain("阈值");
    }

    @Test
    void rewritePortfolioDishReason_eliminateUsesSoftObservationTone() {
        String rewritten =
                MenuOperationPortfolioExpressionSupport.rewritePortfolioDishReason(
                        "相对观察档（低销量低利润），建议先观察一个周期",
                        MenuOperationAnswerPlan.CATEGORY_ELIMINATE);

        assertThat(rewritten).contains("观察");
        assertThat(rewritten).doesNotContain("淘汰");
        assertThat(rewritten).doesNotContain("下架");
    }

    @Test
    void categoryRecommendedAction_eliminateAvoidsDelistTone() {
        assertThat(
                        MenuOperationPortfolioExpressionSupport.categoryRecommendedAction(
                                MenuOperationAnswerPlan.CATEGORY_ELIMINATE))
                .isEqualTo(MenuOperationPortfolioExpressionSupport.ELIMINATE_RECOMMENDED_ACTION);
    }

    @Test
    void portfolioRuleExplanation_avoidsTechnicalTerms() {
        String rule = MenuOperationPortfolioExpressionSupport.portfolioRuleExplanation();

        assertThat(rule).contains("本期参与销售的菜品");
        assertThat(rule).doesNotContain("中位数");
        assertThat(rule).doesNotContain("阈值");
        assertThat(rule).doesNotContain("median");
    }
}
