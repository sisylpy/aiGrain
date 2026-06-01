package com.nongxinle.ai.semantic;

import com.nongxinle.ai.gateway.LlmGatewayFailureMarker;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Semantic Intake / V2 解析层基础设施失败分类（非业务澄清）。
 * 仅依据传输原因码、网关标记与 JSON 结构信号，不读用户业务关键词。
 */
public final class SemanticLlmFailureClassification {

    public static final String CODE_LLM_SERVICE_UNAVAILABLE = "LLM_SERVICE_UNAVAILABLE";
    public static final String CODE_SEMANTIC_INTAKE_PARSE_FAILED = "SEMANTIC_INTAKE_PARSE_FAILED";
    public static final String CODE_SEMANTIC_V2_PARSE_FAILED = "SEMANTIC_V2_PARSE_FAILED";

    public static final String STAGE_SEMANTIC_INTAKE = "SEMANTIC_INTAKE";
    public static final String STAGE_SEMANTIC_V2 = "SEMANTIC_V2";

    public static final String USER_MESSAGE_SERVICE_UNAVAILABLE =
            "AI 语义解析服务暂时不可用，请稍后重试";
    public static final String USER_MESSAGE_PARSE_FAILED =
            "AI 语义解析暂时失败，请稍后重试";

    private static final Set<String> TRANSPORT_REASON_CODES =
            Set.of(
                    "empty_message",
                    "empty_llm_response",
                    "blank_response",
                    "llm_exception",
                    "prompt_load_failed",
                    "empty_repair_response",
                    "semantic_prompt_load_failed",
                    "skipped_empty_or_null_input");

    private SemanticLlmFailureClassification() {}

    public static boolean isInfrastructureFailure(String failureCode) {
        return CODE_LLM_SERVICE_UNAVAILABLE.equals(failureCode)
                || CODE_SEMANTIC_INTAKE_PARSE_FAILED.equals(failureCode)
                || CODE_SEMANTIC_V2_PARSE_FAILED.equals(failureCode);
    }

    public static String userMessageForFailureCode(String failureCode) {
        if (CODE_LLM_SERVICE_UNAVAILABLE.equals(failureCode)) {
            return USER_MESSAGE_SERVICE_UNAVAILABLE;
        }
        if (CODE_SEMANTIC_INTAKE_PARSE_FAILED.equals(failureCode)
                || CODE_SEMANTIC_V2_PARSE_FAILED.equals(failureCode)) {
            return USER_MESSAGE_PARSE_FAILED;
        }
        return null;
    }

    /** Intake {@link SemanticIntakeStatus#INVALID} 时的基础设施失败码；业务 NEED_CLARIFICATION 返回 null。 */
    public static String classifyIntakeFailure(SemanticIntakeResult intake) {
        if (intake == null) {
            return CODE_LLM_SERVICE_UNAVAILABLE;
        }
        if (intake.getStatus() != SemanticIntakeStatus.INVALID) {
            return null;
        }
        if (StringUtils.hasText(intake.getFailureCode())) {
            return intake.getFailureCode().trim();
        }
        if (LlmGatewayFailureMarker.isMarked(intake.getLlmRawText())) {
            return CODE_LLM_SERVICE_UNAVAILABLE;
        }
        String reason = trim(intake.getReason());
        String parseError = trim(intake.getParseError());
        if (isTransportReason(reason) || isTransportReason(parseError)) {
            return CODE_LLM_SERVICE_UNAVAILABLE;
        }
        if (startsWithTransportPrefix(reason) || startsWithTransportPrefix(parseError)) {
            return CODE_LLM_SERVICE_UNAVAILABLE;
        }
        if ("parse_failed".equals(reason) || "json_extract_or_syntax_failed".equals(parseError)) {
            if (isProseNotJson(intake.getLlmRawText())) {
                return CODE_LLM_SERVICE_UNAVAILABLE;
            }
            return CODE_SEMANTIC_INTAKE_PARSE_FAILED;
        }
        return CODE_SEMANTIC_INTAKE_PARSE_FAILED;
    }

    public static String classifyV2ParseFailure(AiQuerySemanticParseResult v2) {
        if (v2 == null || !v2.isParseMissing()) {
            return null;
        }
        String raw = v2.getObservationLlmRawText();
        if (LlmGatewayFailureMarker.isMarked(raw)) {
            return CODE_LLM_SERVICE_UNAVAILABLE;
        }
        String err = trim(v2.getObservationJsonParseError());
        if (isTransportReason(err) || startsWithTransportPrefix(err)) {
            return CODE_LLM_SERVICE_UNAVAILABLE;
        }
        if (isProseNotJson(raw)) {
            return CODE_LLM_SERVICE_UNAVAILABLE;
        }
        return CODE_SEMANTIC_V2_PARSE_FAILED;
    }

    public static void enrichIntakeFailureMeta(SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() != SemanticIntakeStatus.INVALID) {
            return;
        }
        String code = classifyIntakeFailure(intake);
        if (code == null) {
            return;
        }
        intake.setFailureCode(code);
        intake.setFailureStage(STAGE_SEMANTIC_INTAKE);
    }

    private static boolean isTransportReason(String code) {
        return StringUtils.hasText(code) && TRANSPORT_REASON_CODES.contains(code.trim());
    }

    private static boolean startsWithTransportPrefix(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String c = code.trim();
        return c.startsWith("exception:")
                || c.startsWith("resolver_v2_exception:")
                || c.startsWith("repair_exception:")
                || c.startsWith("repair_still_invalid:");
    }

    private static boolean isProseNotJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return true;
        }
        if (LlmGatewayFailureMarker.isMarked(raw)) {
            return true;
        }
        String reason = AiQuerySemanticParseResultJsonParser.describeParseFailureReason(raw);
        return "no_json_object_markers_likely_prose".equals(reason)
                || "blank_response".equals(reason);
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
