package com.nongxinle.ai.context;

import com.nongxinle.ai.followup.AiFollowUpConversationMemory;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import com.nongxinle.ai.followup.FollowUpPathKind;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 采购多轮后「利润」必须切换为菜品毛利路径，不得继续继承 purchase_overview_path。
 */
class ProfitDomainRoutingRegressionTest {

    private final FollowUpIntentResolveService followUp =
            new FollowUpIntentResolveService(new AiFollowUpConversationMemory());

    @Test
    void fromUserMessage_maps_standalone_profit_question_to_dish_profit() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.fromUserMessage("AAA 利润怎么样？");
        Assertions.assertThat(qi.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        Assertions.assertThat(qi.getIntentCode()).isEqualTo(AiResolvedQueryIntent.DISH_PROFIT);
        Assertions.assertThat(qi.getPurchaseSourceType()).isNull();
    }

    @Test
    void currentMessageDeclaresDomain_for_store_prefixed_profit_question() {
        Assertions.assertThat(
                FollowUpIntentResolveService.currentMessageDeclaresDomainPath("AAA 利润怎么样？")).isTrue();
    }

    @Test
    void topicConflict_when_previous_was_purchase_and_message_is_profit() {
        Assertions.assertThat(followUp.conflictsWithPreviousPath("AAA利润怎么样？", FollowUpPathKind.PURCHASE_OVERVIEW))
                .isTrue();
        Assertions.assertThat(followUp.conflictsWithPreviousPath("毛利怎么样？", FollowUpPathKind.PURCHASE_OVERVIEW))
                .isTrue();
    }

    @Test
    void topicConflict_false_when_still_in_purchase_wording_with_supplier_and_profit_is_substring_noise() {
        // 「供应商」子串命中 hasPurchase，利润子句也不应单独切走（保守：仍可能走采购解读）
        Assertions.assertThat(followUp.conflictsWithPreviousPath(
                "供货商订货利润占比多少", FollowUpPathKind.PURCHASE_OVERVIEW)).isFalse();
    }
}
