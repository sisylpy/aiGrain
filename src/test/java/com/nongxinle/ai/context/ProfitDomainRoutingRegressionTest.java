package com.nongxinle.ai.context;

import com.nongxinle.ai.followup.AiFollowUpHintSupport;
import com.nongxinle.ai.followup.FollowUpPathKind;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 采购多轮后「利润」必须切换为菜品毛利路径，不得继续继承 purchase_overview_path。
 */
class ProfitDomainRoutingRegressionTest {

    @Test
    void fromUserMessage_isDeprecatedStub_noKeywordRouting() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.fromUserMessage("AAA 利润怎么样？");
        Assertions.assertThat(qi.getPathCode()).isNull();
        Assertions.assertThat(qi.getIntentCode()).isNull();
        Assertions.assertThat(qi.getPurchaseSourceType()).isNull();
    }

    @Test
    void currentMessageDeclaresDomainPath_false_for_plain_profit_question_without_switch_hint() {
        Assertions.assertThat(
                AiFollowUpHintSupport.currentMessageDeclaresDomainPath("AAA 利润怎么样？")).isFalse();
    }

    @Test
    void currentMessageDeclaresDomainPath_true_when_explicit_switch_topic_hint() {
        Assertions.assertThat(
                AiFollowUpHintSupport.currentMessageDeclaresDomainPath("换成菜品毛利")).isTrue();
    }

    @Test
    void topicConflict_always_false_domain_switch_owned_by_semantic_parser() {
        Assertions.assertThat(AiFollowUpHintSupport.pathTopicConflict("AAA利润怎么样？", FollowUpPathKind.PURCHASE_OVERVIEW))
                .isFalse();
        Assertions.assertThat(AiFollowUpHintSupport.pathTopicConflict("毛利怎么样？", FollowUpPathKind.PURCHASE_OVERVIEW))
                .isFalse();
    }

    @Test
    void topicConflict_false_when_still_in_purchase_wording_with_supplier_and_profit_is_substring_noise() {
        Assertions.assertThat(AiFollowUpHintSupport.pathTopicConflict(
                "供货商订货利润占比多少", FollowUpPathKind.PURCHASE_OVERVIEW)).isFalse();
    }
}
