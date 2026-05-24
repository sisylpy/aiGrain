package com.nongxinle.ai.semantic.intake.llm;

import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import com.nongxinle.ai.semantic.intake.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SemanticIntake LLM：话术规范化 + 一级业务域选择 + 多问题识别。
 * Java 仅做 schema/enum 校验，不通过关键词修正 domain。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSemanticIntakeParser {

    private final LlmGateway llmGateway;
    private final AiPromptService aiPromptService;

    @Value("${ai.agent.semanticIntakeLlm.minConfidence:0.55}")
    private double minConfidence;

    public SemanticIntakeResult parse(SemanticIntakeInput input) {
        if (input == null || !StringUtils.hasText(input.getNormalizedUserMessage())) {
            return SemanticIntakeResult.invalid(
                    "empty_message", AiPromptIds.SEMANTIC_INTAKE_V1, null, "empty_message");
        }
        String pid = AiPromptIds.SEMANTIC_INTAKE_V1;
        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(pid);
        } catch (RuntimeException ex) {
            log.warn("[LlmSemanticIntakeParser] prompt load failed: {}", ex.toString());
            return SemanticIntakeResult.invalid(
                    "prompt_load_failed", pid, null, ex.getClass().getSimpleName());
        }
        String userPayload = LlmSemanticIntakePromptBuilder.toUserJson(input);
        String raw = null;
        try {
            raw = llmGateway.chatSimple(systemPrompt, userPayload);
            if (!StringUtils.hasText(raw)) {
                return SemanticIntakeResult.invalid(
                        "empty_llm_response", pid, truncateRaw(raw), "empty_llm_response");
            }
            LlmSemanticIntakeParsed parsed = LlmSemanticIntakeJsonParser.parseRaw(raw);
            if (parsed.isParseFailed()) {
                return SemanticIntakeResult.invalid(
                        "parse_failed", pid, truncateRaw(raw), parsed.getParseError());
            }
            List<String> enumErrors = collectEnumErrors(parsed);
            if (!enumErrors.isEmpty()) {
                return parseWithProtocolRepair(input, pid, systemPrompt, truncateRaw(raw), parsed, enumErrors);
            }
            return mapParsed(input, pid, truncateRaw(raw), parsed);
        } catch (Exception e) {
            log.warn("[LlmSemanticIntakeParser] llm intake failed: {}", e.toString());
            return SemanticIntakeResult.invalid(
                    "llm_exception", pid, truncateRaw(raw), e.getClass().getSimpleName());
        }
    }

    private SemanticIntakeResult mapParsed(
            SemanticIntakeInput input,
            String promptId,
            String rawObs,
            LlmSemanticIntakeParsed parsed) {
        if (!LlmSemanticIntakeJsonParser.isValidQuestionMode(parsed.getQuestionMode())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_question_mode");
        }
        if (!LlmSemanticIntakeJsonParser.isValidNormalizationType(parsed.getNormalizationType())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_normalization_type");
        }
        if (!StringUtils.hasText(parsed.getCanonicalUserQuery())) {
            return invalidFromParsed(parsed, promptId, rawObs, "missing_canonical_user_query");
        }
        if (!LlmSemanticIntakeJsonParser.isValidRouteType(parsed.getRouteType())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_route_type");
        }
        if (!LlmSemanticIntakeJsonParser.isValidPrimaryDomain(parsed.getPrimaryDomain())) {
            return invalidFromParsed(parsed, promptId, rawObs, "invalid_primary_domain");
        }
        if (parsed.getConfidence() == null || parsed.getConfidence() < minConfidence) {
            return needClarificationFromParsed(
                    parsed,
                    promptId,
                    rawObs,
                    "low_confidence",
                    firstNonBlank(
                            parsed.getClarificationQuestion(), "能再具体说一下您想问的内容吗？"));
        }
        if (parsed.isNeedClarification() && StringUtils.hasText(parsed.getClarificationQuestion())) {
            return needClarificationFromParsed(
                    parsed, promptId, rawObs, parsed.getReason(), parsed.getClarificationQuestion().trim());
        }

        SemanticIntakeQuestionMode questionMode =
                SemanticIntakeQuestionMode.valueOf(parsed.getQuestionMode().trim().toUpperCase());
        if (questionMode == SemanticIntakeQuestionMode.MULTI_QUESTION) {
            String question =
                    StringUtils.hasText(parsed.getClarificationQuestion())
                            ? parsed.getClarificationQuestion().trim()
                            : "您一次问了多个问题，请先告诉我您想先查哪一个方向？";
            return SemanticIntakeResult.builder()
                    .status(SemanticIntakeStatus.NEED_CLARIFICATION)
                    .questionMode(questionMode)
                    .normalizationType(parseNormalizationType(parsed.getNormalizationType()))
                    .canonicalUserQuery(parsed.getCanonicalUserQuery().trim())
                    .isFollowUp(parsed.isFollowUp())
                    .usedPreviousContext(parsed.isUsedPreviousContext())
                    .primaryDomain(SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain()))
                    .candidateDomains(parsed.getCandidateDomains())
                    .routeType(parsed.getRouteType().trim().toUpperCase())
                    .confidence(parsed.getConfidence())
                    .needClarification(true)
                    .clarificationQuestion(question)
                    .reason(firstNonBlank(parsed.getReason(), "multi_question"))
                    .subQuestions(parsed.getSubQuestions())
                    .promptId(promptId)
                    .llmRawText(rawObs)
                    .build();
        }

        String routeType = parsed.getRouteType().trim().toUpperCase();
        String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        if ("AMBIGUOUS".equals(routeType)
                || "UNKNOWN".equals(routeType)
                || "MULTI_DOMAIN".equals(routeType)
                || SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(primary)
                || SemanticIntakePrimaryDomain.UNKNOWN.equals(primary)
                || !SemanticIntakePrimaryDomain.isExecutable(primary)) {
            String question =
                    StringUtils.hasText(parsed.getClarificationQuestion())
                            ? parsed.getClarificationQuestion().trim()
                            : "请问您想查的是营业额、采购、库存还是其他哪一类数据？";
            return needClarificationFromParsed(
                    parsed, promptId, rawObs, firstNonBlank(parsed.getReason(), routeType), question);
        }

        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(questionMode)
                .normalizationType(parseNormalizationType(parsed.getNormalizationType()))
                .canonicalUserQuery(parsed.getCanonicalUserQuery().trim())
                .isFollowUp(parsed.isFollowUp())
                .usedPreviousContext(parsed.isUsedPreviousContext())
                .primaryDomain(primary)
                .candidateDomains(parsed.getCandidateDomains())
                .routeType(routeType)
                .confidence(parsed.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(parsed.getReason())
                .subQuestions(parsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .build();
    }

    private static SemanticIntakeResult invalidFromParsed(
            LlmSemanticIntakeParsed parsed, String promptId, String rawObs, String reason) {
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.INVALID)
                .questionMode(safeQuestionMode(parsed.getQuestionMode()))
                .normalizationType(safeNormalizationType(parsed.getNormalizationType()))
                .canonicalUserQuery(parsed.getCanonicalUserQuery())
                .isFollowUp(parsed.isFollowUp())
                .usedPreviousContext(parsed.isUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain()))
                .candidateDomains(parsed.getCandidateDomains())
                .routeType(parsed.getRouteType())
                .confidence(parsed.getConfidence())
                .needClarification(false)
                .reason(reason)
                .subQuestions(parsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .parseError(reason)
                .build();
    }

    private static SemanticIntakeResult needClarificationFromParsed(
            LlmSemanticIntakeParsed parsed,
            String promptId,
            String rawObs,
            String reason,
            String clarificationQuestion) {
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.NEED_CLARIFICATION)
                .questionMode(safeQuestionMode(parsed.getQuestionMode()))
                .normalizationType(safeNormalizationType(parsed.getNormalizationType()))
                .canonicalUserQuery(
                        StringUtils.hasText(parsed.getCanonicalUserQuery())
                                ? parsed.getCanonicalUserQuery().trim()
                                : null)
                .isFollowUp(parsed.isFollowUp())
                .usedPreviousContext(parsed.isUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain()))
                .candidateDomains(parsed.getCandidateDomains())
                .routeType(parsed.getRouteType())
                .confidence(parsed.getConfidence())
                .needClarification(true)
                .clarificationQuestion(clarificationQuestion)
                .reason(reason)
                .subQuestions(parsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .build();
    }

    private static SemanticIntakeQuestionMode safeQuestionMode(String mode) {
        if (!LlmSemanticIntakeJsonParser.isValidQuestionMode(mode)) {
            return null;
        }
        return SemanticIntakeQuestionMode.valueOf(mode.trim().toUpperCase());
    }

    private static SemanticIntakeNormalizationType safeNormalizationType(String type) {
        if (!LlmSemanticIntakeJsonParser.isValidNormalizationType(type)) {
            return null;
        }
        return SemanticIntakeNormalizationType.valueOf(type.trim().toUpperCase());
    }

    private static SemanticIntakeNormalizationType parseNormalizationType(String type) {
        return SemanticIntakeNormalizationType.valueOf(type.trim().toUpperCase());
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        return StringUtils.hasText(b) ? b.trim() : null;
    }

    private static String truncateRaw(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        int max = 4000;
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /**
     * 收集所有非空但枚举值非法的字段错误，用于触发协议纠错重试。
     */
    static List<String> collectEnumErrors(LlmSemanticIntakeParsed parsed) {
        List<String> errors = new ArrayList<>();
        String nt = parsed.getNormalizationType();
        if (nt != null && !LlmSemanticIntakeJsonParser.isValidNormalizationType(nt)) {
            errors.add(
                    "normalizationType: got \""
                            + nt
                            + "\", allowed: PASS_THROUGH, REWRITE");
        }
        String qm = parsed.getQuestionMode();
        if (qm != null && !LlmSemanticIntakeJsonParser.isValidQuestionMode(qm)) {
            errors.add(
                    "questionMode: got \""
                            + qm
                            + "\", allowed: SINGLE_QUESTION, MULTI_QUESTION");
        }
        String rt = parsed.getRouteType();
        if (rt != null && !LlmSemanticIntakeJsonParser.isValidRouteType(rt)) {
            errors.add(
                    "routeType: got \""
                            + rt
                            + "\", allowed: EXPLICIT, INHERITED, AMBIGUOUS, UNKNOWN, MULTI_DOMAIN");
        }
        return errors;
    }

    /**
     * 构建协议纠错 user message，仅要求修正枚举值，不做业务语义推断。
     */
    static String buildRepairUserMessage(String originalRaw, List<String> enumErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("protocol_repair_request\n");
        sb.append(
                "Your JSON output contained invalid enum values. Correct ONLY the invalid enum values while keeping all other fields unchanged. Re-output one line of corrected JSON.\n\n");
        sb.append("Invalid fields:\n");
        for (String err : enumErrors) {
            sb.append("- ").append(err).append("\n");
        }
        sb.append("\nOriginal output:\n");
        sb.append(originalRaw);
        return sb.toString();
    }

    /**
     * 协议纠错重试：JSON 可解析但枚举非法时，重试一次要求模型修正枚举值。
     * 这是输出协议层面的修复，不做业务语义推断。
     */
    private SemanticIntakeResult parseWithProtocolRepair(
            SemanticIntakeInput input,
            String promptId,
            String systemPrompt,
            String originalRaw,
            LlmSemanticIntakeParsed originalParsed,
            List<String> enumErrors) {
        String repairUserMessage = buildRepairUserMessage(originalRaw, enumErrors);
        String repairedRaw = null;
        try {
            repairedRaw = llmGateway.chatSimple(systemPrompt, repairUserMessage);
            if (!StringUtils.hasText(repairedRaw)) {
                return markRepairedInvalid(
                        originalParsed,
                        promptId,
                        originalRaw,
                        enumErrors,
                        false,
                        "empty_repair_response");
            }
            LlmSemanticIntakeParsed repaired =
                    LlmSemanticIntakeJsonParser.parseRaw(repairedRaw);
            if (repaired.isParseFailed()) {
                return markRepairedInvalid(
                        originalParsed, promptId, originalRaw, enumErrors, false, repaired.getParseError());
            }
            List<String> repairedErrors = collectEnumErrors(repaired);
            if (!repairedErrors.isEmpty()) {
                return markRepairedInvalid(
                        originalParsed,
                        promptId,
                        originalRaw,
                        enumErrors,
                        false,
                        "repair_still_invalid:" + String.join(";", repairedErrors));
            }
            SemanticIntakeResult result =
                    mapParsed(input, promptId, truncateRaw(repairedRaw), repaired);
            result.setIntakeRepairAttempted(true);
            result.setIntakeRepairSuccess(true);
            result.setIntakeRepairReason(buildRepairReasonCode(enumErrors));
            return result;
        } catch (Exception e) {
            log.warn("[LlmSemanticIntakeParser] protocol repair failed: {}", e.toString());
            return markRepairedInvalid(
                    originalParsed,
                    promptId,
                    originalRaw,
                    enumErrors,
                    false,
                    "repair_exception:" + e.getClass().getSimpleName());
        }
    }

    private static SemanticIntakeResult markRepairedInvalid(
            LlmSemanticIntakeParsed originalParsed,
            String promptId,
            String rawObs,
            List<String> enumErrors,
            boolean repairSuccess,
            String detailError) {
        String reasonCode = buildRepairReasonCode(enumErrors);
        if (StringUtils.hasText(detailError)) {
            reasonCode = reasonCode + ";" + detailError;
        }
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.INVALID)
                .questionMode(safeQuestionMode(originalParsed.getQuestionMode()))
                .normalizationType(safeNormalizationType(originalParsed.getNormalizationType()))
                .canonicalUserQuery(originalParsed.getCanonicalUserQuery())
                .isFollowUp(originalParsed.isFollowUp())
                .usedPreviousContext(originalParsed.isUsedPreviousContext())
                .primaryDomain(
                        SemanticIntakePrimaryDomain.normalize(originalParsed.getPrimaryDomain()))
                .candidateDomains(originalParsed.getCandidateDomains())
                .routeType(originalParsed.getRouteType())
                .confidence(originalParsed.getConfidence())
                .needClarification(false)
                .reason(reasonCode)
                .subQuestions(originalParsed.getSubQuestions())
                .promptId(promptId)
                .llmRawText(rawObs)
                .parseError(reasonCode)
                .intakeRepairAttempted(true)
                .intakeRepairSuccess(repairSuccess)
                .intakeRepairReason(buildRepairReasonCode(enumErrors))
                .build();
    }

    private static String buildRepairReasonCode(List<String> enumErrors) {
        if (enumErrors == null || enumErrors.isEmpty()) {
            return "unknown_repair";
        }
        if (enumErrors.stream().anyMatch(e -> e.startsWith("normalizationType:"))) {
            if (enumErrors.stream().anyMatch(e -> e.startsWith("routeType:"))) {
                return "invalid_normalization_type;invalid_route_type";
            }
            return "invalid_normalization_type";
        }
        if (enumErrors.stream().anyMatch(e -> e.startsWith("routeType:"))) {
            return "invalid_route_type";
        }
        if (enumErrors.stream().anyMatch(e -> e.startsWith("questionMode:"))) {
            return "invalid_question_mode";
        }
        return "invalid_enum";
    }
}
