package com.nongxinle.ai.context;

import com.nongxinle.ai.followup.AiFollowUpHintSupport;
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

}
