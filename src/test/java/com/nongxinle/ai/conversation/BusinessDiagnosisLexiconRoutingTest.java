package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedQueryIntent;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 经营诊断路径与结构化协议：wire/canonical；菜名归一不靠 Java 正则从整句反问里抽。
 */
class BusinessDiagnosisLexiconRoutingTest {

    @Test
    void diagnosis_phrases_noLongerRoutedByJavaKeyword_fromUserMessageIsStub() {
        for (String q : List.of(
                "怎么诊断这个月经营情况？",
                "帮我诊断一下这个月经营情况",
                "帮我分析一下这个月经营状况")) {
            AiResolvedQueryIntent qi = AiResolvedQueryIntent.fromUserMessage(q);
            Assertions.assertThat(qi.getPathCode()).as(q).isNull();
        }
    }

    @Test
    void finalizeMentionedDish_collapses_spacing() {
        Assertions.assertThat(AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit("　酸奶碗　"))
                .isEqualTo("酸奶碗");
        Assertions.assertThat(AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit("核桃芽\t菜西芹"))
                .isEqualTo("核桃芽菜西芹");
    }

    @Test
    void store_priority_wire_isCanonicalInLexicon_notJavaKeywordRouting() {
        Assertions.assertThat(
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire("store_priority_ranking"))
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING);
        Assertions.assertThat(AiResolvedQueryIntent.fromUserMessage("今天老板先处理哪个门店？").getPathCode())
                .isNull();
    }

    @Test
    void canonical_wire_unifies_store_risk_to_priority() {
        Assertions.assertThat(AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire("store_risk_ranking"))
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING);
        Assertions.assertThat(AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail("STORE_RISK_RANKING"))
                .isTrue();
    }

    @Test
    void low_profit_reason_structured_wire_predicate() {
        Assertions.assertThat(
                        AiQuerySemanticLexicon.isDishLowProfitReasonStructuredWire(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON))
                .isTrue();
        Assertions.assertThat(
                        AiQuerySemanticLexicon.isDishLowProfitReasonStructuredWire(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN))
                .isFalse();
    }
}
