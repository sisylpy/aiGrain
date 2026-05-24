package com.nongxinle.ai.semantic;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Harness 入口：仅用 LLM 解析「用户语义意图/时间偏好/口述范围」，禁止产出任何 SQL 或可执行 ID；
 * {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 负责把门店名等映射为权限内 ID。
 * <p>
 * 生产语义仅 v2：{@link SemanticParserInput} + {@link AiPromptIds#SEMANTIC_QUERY_PARSER_V2}，由 Resolver 调用 {@link #parse(SemanticParserInput)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuerySemanticLlmParser {

    private final LlmGateway llmGateway;
    private final AiPromptService aiPromptService;

    /**
     * v2：user 消息为 {@link SemanticParserInput} 的 JSON；system 为 {@link AiPromptIds#SEMANTIC_QUERY_PARSER_V2}。
     */
    public AiQuerySemanticParseResult parse(SemanticParserInput input) {
        String pid = AiPromptIds.SEMANTIC_QUERY_PARSER_V2;
        if (input == null || !StringUtils.hasText(input.getCurrentUserMessage())) {
            AiQuerySemanticParseResult out =
                    AiQuerySemanticParseResult.builder()
                            .parseMissing(true)
                            .observationJsonParseError("skipped_empty_or_null_input")
                            .build();
            logSemanticInvocation("v2", pid, null, out, null);
            return out;
        }
        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(pid);
        } catch (RuntimeException bootEx) {
            log.warn("[AiQuerySemanticLlmParser] v2 load semantic prompt failed: {}", bootEx.toString());
            AiQuerySemanticParseResult out =
                    AiQuerySemanticParseResult.builder()
                            .parseMissing(true)
                            .observationJsonParseError("semantic_prompt_load_failed")
                            .build();
            logSemanticInvocation("v2", pid, null, out, null);
            return out;
        }
        String userPayload = JSON.toJSONString(input);
        String raw = null;
        try {
            raw = llmGateway.chatSimple(systemPrompt, userPayload);
            String rawObs = truncateSemanticObservationRaw(raw);
            if (!StringUtils.hasText(raw)) {
                AiQuerySemanticParseResult out =
                        AiQuerySemanticParseResult.builder()
                                .parseMissing(true)
                                .promptRegistryId(pid)
                                .observationLlmRawText(rawObs)
                                .observationJsonParseError("empty_llm_response")
                                .build();
                logSemanticInvocation("v2", pid, raw, out, null);
                return out;
            }
            return finalizeParsed(
                    pid,
                    rawObs,
                    parseWithOptionalProtocolRepair(systemPrompt, raw));
        } catch (Exception e) {
            log.warn("[AiQuerySemanticLlmParser] v2 llm semantic parse failed: {}", e.toString());
            AiQuerySemanticParseResult out =
                    AiQuerySemanticParseResult.builder()
                            .parseMissing(true)
                            .promptRegistryId(pid)
                            .observationLlmRawText(truncateSemanticObservationRaw(raw))
                            .observationJsonParseError("exception:" + e.getClass().getSimpleName())
                            .build();
            logSemanticInvocation("v2", pid, raw, out, null);
            return out;
        }
    }

    private record ParseAttempt(AiQuerySemanticParseResult parsed, String observationRaw) {}

    private ParseAttempt parseWithOptionalProtocolRepair(String systemPrompt, String raw) {
        AiQuerySemanticParseResultJsonParser.ProtocolNormalizeResult norm =
                AiQuerySemanticParseResultJsonParser.parseAndNormalizeProtocol(raw);
        AiQuerySemanticParseResult parsed = norm.parsed();
        String normalizedRaw = norm.normalizedJson();
        if (parsed.isParseMissing()) {
            return new ParseAttempt(parsed, raw);
        }
        List<String> protocolErrors =
                AiQuerySemanticParseResultJsonParser.collectProtocolErrors(normalizedRaw, parsed);
        if (protocolErrors.isEmpty()) {
            if (norm.relocate().changed()) {
                return new ParseAttempt(markJavaProtocolRelocate(parsed, norm.relocate()), normalizedRaw);
            }
            return new ParseAttempt(parsed, normalizedRaw);
        }
        return parseWithProtocolRepair(systemPrompt, raw, normalizedRaw, parsed, protocolErrors);
    }

    /**
     * JSON 可解析但协议不合格时，重试一次要求模型修正 schema/枚举/字段位置；不做业务语义推断。
     * LLM repair 失败时仍保留 Java 协议搬移后的 parse（含已恢复的顶层 confidence 等）。
     */
    private ParseAttempt parseWithProtocolRepair(
            String systemPrompt,
            String originalRaw,
            String normalizedRaw,
            AiQuerySemanticParseResult normalizedParsed,
            List<String> protocolErrors) {
        String repairReason =
                AiQuerySemanticParseResultJsonParser.buildProtocolRepairReasonCode(protocolErrors);
        String repairUserMessage =
                AiQuerySemanticParseResultJsonParser.buildProtocolRepairUserMessage(
                        normalizedRaw, protocolErrors);
        String repairedRaw = null;
        try {
            repairedRaw = llmGateway.chatSimple(systemPrompt, repairUserMessage);
            if (!StringUtils.hasText(repairedRaw)) {
                return new ParseAttempt(
                        markRepaired(normalizedParsed, repairReason, false, "empty_repair_response"),
                        normalizedRaw);
            }
            AiQuerySemanticParseResultJsonParser.ProtocolNormalizeResult repairedNorm =
                    AiQuerySemanticParseResultJsonParser.parseAndNormalizeProtocol(repairedRaw);
            AiQuerySemanticParseResult repaired = repairedNorm.parsed();
            String repairedNormalizedRaw = repairedNorm.normalizedJson();
            if (repaired.isParseMissing()) {
                return new ParseAttempt(
                        markRepaired(
                                normalizedParsed,
                                repairReason,
                                false,
                                AiQuerySemanticParseResultJsonParser.describeParseFailureReason(repairedRaw)),
                        normalizedRaw);
            }
            List<String> repairedErrors =
                    AiQuerySemanticParseResultJsonParser.collectProtocolErrors(
                            repairedNormalizedRaw, repaired);
            if (!repairedErrors.isEmpty()) {
                return new ParseAttempt(
                        markRepaired(
                                normalizedParsed,
                                repairReason,
                                false,
                                "repair_still_invalid:" + String.join(";", repairedErrors)),
                        normalizedRaw);
            }
            AiQuerySemanticParseResult success =
                    markRepaired(repaired, repairReason, true, null);
            if (repairedNorm.relocate().changed()) {
                String relocateSuffix =
                        "java_protocol_relocate:" + String.join(";", repairedNorm.relocate().moves());
                success =
                        success.toBuilder()
                                .querySemanticV2RepairReason(
                                        repairReason + ";" + relocateSuffix)
                                .build();
            }
            return new ParseAttempt(success, repairedNormalizedRaw);
        } catch (Exception e) {
            log.warn("[AiQuerySemanticLlmParser] v2 protocol repair failed: {}", e.toString());
            return new ParseAttempt(
                    markRepaired(
                            normalizedParsed,
                            repairReason,
                            false,
                            "repair_exception:" + e.getClass().getSimpleName()),
                    normalizedRaw);
        }
    }

    private static AiQuerySemanticParseResult markJavaProtocolRelocate(
            AiQuerySemanticParseResult parsed,
            AiQuerySemanticParseResultJsonParser.ProtocolRelocateResult relocate) {
        String reason = "java_protocol_relocate";
        if (relocate.moves() != null && !relocate.moves().isEmpty()) {
            reason = reason + ":" + String.join(";", relocate.moves());
        }
        return parsed.toBuilder()
                .querySemanticV2RepairAttempted(true)
                .querySemanticV2RepairSuccess(true)
                .querySemanticV2RepairReason(reason)
                .build();
    }

    private static AiQuerySemanticParseResult markRepaired(
            AiQuerySemanticParseResult baseParsed,
            String repairReason,
            boolean repairSuccess,
            String detailError) {
        String reason = repairReason;
        if (StringUtils.hasText(detailError)) {
            reason = repairReason + ";" + detailError;
        }
        return baseParsed.toBuilder()
                .querySemanticV2RepairAttempted(true)
                .querySemanticV2RepairSuccess(repairSuccess)
                .querySemanticV2RepairReason(reason)
                .build();
    }

    private AiQuerySemanticParseResult finalizeParsed(
            String pid, String rawObsFallback, ParseAttempt attempt) {
        AiQuerySemanticParseResult parsed = attempt.parsed();
        String observationRaw = truncateSemanticObservationRaw(attempt.observationRaw());
        if (observationRaw == null) {
            observationRaw = rawObsFallback;
        }
        String err =
                parsed.isParseMissing()
                        ? AiQuerySemanticParseResultJsonParser.describeParseFailureReason(
                                attempt.observationRaw())
                        : null;
        AiQuerySemanticParseResult out =
                parsed.toBuilder()
                        .promptRegistryId(pid)
                        .observationLlmRawText(observationRaw)
                        .observationJsonParseError(err)
                        .build();
        logSemanticInvocation("v2", pid, attempt.observationRaw(), out, null);
        return out;
    }

    private static String truncateSemanticObservationRaw(String r) {
        if (r == null) {
            return null;
        }
        String t = r.trim();
        if (t.isEmpty()) {
            return null;
        }
        int max = 8000;
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /**
     * 不落用户原文与 system prompt；仅长度与解析摘要，便于与 {@link LlmGateway} 实现对齐排障。
     */
    private void logSemanticInvocation(
            String parserVersion,
            String promptId,
            String raw,
            AiQuerySemanticParseResult result,
            String parseErrorOverride) {
        if (!log.isInfoEnabled()) {
            return;
        }
        int rawLen = raw == null ? 0 : raw.length();
        boolean rawEmpty = !StringUtils.hasText(raw);
        String parseError =
                parseErrorOverride != null
                        ? parseErrorOverride
                        : (result != null ? result.getObservationJsonParseError() : null);
        boolean parseMissing = result == null || result.isParseMissing();
        log.info(
                "[QuerySemanticLlm] parserVersion={} promptId={} rawEmpty={} rawLen={} parseMissing={} parseError={}",
                parserVersion,
                promptId,
                rawEmpty,
                rawLen,
                parseMissing,
                parseError);
    }

}
