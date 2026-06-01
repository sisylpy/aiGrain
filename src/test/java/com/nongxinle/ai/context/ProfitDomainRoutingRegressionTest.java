package com.nongxinle.ai.context;

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

}
