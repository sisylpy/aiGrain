package com.nongxinle.ai.semantic;

import com.nongxinle.ai.gateway.LlmGatewayFailureMarker;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticLlmFailureClassificationTest {

    @Test
    void classifiesGatewayProseIntakeFailureAsServiceUnavailable() {
        String raw =
                LlmGatewayFailureMarker.wrapUnavailable("抱歉，AI 服务暂时不可用。请稍后重试。");
        SemanticIntakeResult intake =
                SemanticIntakeResult.invalid("parse_failed", "semantic_intake.v1", raw, "json_extract_or_syntax_failed");

        assertThat(SemanticLlmFailureClassification.classifyIntakeFailure(intake))
                .isEqualTo(SemanticLlmFailureClassification.CODE_LLM_SERVICE_UNAVAILABLE);
        assertThat(intake.getFailureCode())
                .isEqualTo(SemanticLlmFailureClassification.CODE_LLM_SERVICE_UNAVAILABLE);
        assertThat(intake.getFailureStage())
                .isEqualTo(SemanticLlmFailureClassification.STAGE_SEMANTIC_INTAKE);
    }

    @Test
    void classifiesMalformedJsonIntakeFailureAsParseFailed() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.INVALID)
                        .reason("parse_failed")
                        .parseError("json_extract_or_syntax_failed")
                        .llmRawText("{\"questionMode\": SINGLE_QUESTION")
                        .build();
        SemanticLlmFailureClassification.enrichIntakeFailureMeta(intake);

        assertThat(intake.getFailureCode())
                .isEqualTo(SemanticLlmFailureClassification.CODE_SEMANTIC_INTAKE_PARSE_FAILED);
    }

    @Test
    void classifiesV2GatewayFailureAsServiceUnavailable() {
        AiQuerySemanticParseResult v2 =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(true)
                        .observationLlmRawText(
                                LlmGatewayFailureMarker.wrapUnavailable(
                                        "抱歉，AI 服务暂时不可用。请稍后重试。"))
                        .observationJsonParseError("json_extract_or_syntax_failed")
                        .build();

        assertThat(SemanticLlmFailureClassification.classifyV2ParseFailure(v2))
                .isEqualTo(SemanticLlmFailureClassification.CODE_LLM_SERVICE_UNAVAILABLE);
    }

    @Test
    void userMessageForServiceUnavailableIsNotBusinessClarification() {
        assertThat(
                        SemanticLlmFailureClassification.userMessageForFailureCode(
                                SemanticLlmFailureClassification.CODE_LLM_SERVICE_UNAVAILABLE))
                .isEqualTo(SemanticLlmFailureClassification.USER_MESSAGE_SERVICE_UNAVAILABLE);
    }
}
