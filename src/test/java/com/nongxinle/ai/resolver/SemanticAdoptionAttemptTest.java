package com.nongxinle.ai.resolver;

import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticAdoptionAttemptTest {

    private static final String FRAME_ANCHOR_QUESTION =
            "需要沿用上轮锚点，但上一轮结果中缺少唯一明确的商品或供货商锚点，请指明要追问的对象。";

    private static final String GENERIC_FALLBACK =
            "我没有完全理解你的问题。你是想查询经营、营业额、采购、出库，还是菜品毛利？请补充时间和门店范围。";

    @Test
    void frameClarificationAttempt_isNotAdopted_andCarriesSpecificQuestion() {
        SemanticAdoptionAttempt attempt =
                new SemanticAdoptionAttempt(
                        null, null, null, null, "USE_PREVIOUS_ANCHOR_NOT_UNIQUE", FRAME_ANCHOR_QUESTION);

        assertThat(attempt.adopted()).isFalse();
        assertThat(attempt.frameClarificationRequired()).isTrue();
        assertThat(attempt.semanticClarificationQuestion()).isEqualTo(FRAME_ANCHOR_QUESTION);
    }

    @Test
    void resolveClarificationPriority_prefersFrameQuestionOverGenericFallback() {
        boolean timeContractFailed = false;
        boolean frameClarificationRequired = true;
        boolean clarificationRequired = true;

        SemanticAdoptionAttempt adoption =
                new SemanticAdoptionAttempt(
                        null, null, null, null, "USE_PREVIOUS_ANCHOR_NOT_UNIQUE", FRAME_ANCHOR_QUESTION);

        String question =
                timeContractFailed
                        ? adoption.timeContract().clarificationQuestion()
                        : frameClarificationRequired
                                ? adoption.semanticClarificationQuestion().trim()
                                : clarificationRequired ? GENERIC_FALLBACK : null;

        assertThat(question).isEqualTo(FRAME_ANCHOR_QUESTION);
        assertThat(question).doesNotContain("经营、营业额");
    }

    @Test
    void timeContractFailure_stillTakesPrecedenceOverFrameQuestion() {
        SemanticAdoptionAttempt adoption =
                new SemanticAdoptionAttempt(
                        null,
                        null,
                        null,
                        new SemanticTimeContractCheck.Result(
                                false,
                                "MISSING_TIME_FIELDS",
                                null,
                                null,
                                null,
                                "时间窗口不完整，请补充起止日期。"),
                        null,
                        FRAME_ANCHOR_QUESTION);

        boolean timeContractFailed =
                adoption.timeContract() != null && !adoption.timeContract().valid();
        assertThat(timeContractFailed).isTrue();

        String question =
                timeContractFailed
                        ? adoption.timeContract().clarificationQuestion()
                        : adoption.semanticClarificationQuestion();

        assertThat(question).contains("时间窗口");
    }
}
