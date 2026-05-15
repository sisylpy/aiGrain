package com.nongxinle.ai.semantic;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Harness 入口：仅用 LLM 解析「用户语义意图/时间偏好/口述范围」，禁止产出任何 SQL 或可执行 ID；
 * {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 负责把门店名等映射为权限内 ID。
 * <p>
 * v2：{@link SemanticParserInput} + {@link AiPromptIds#SEMANTIC_QUERY_PARSER_V2}；
 * {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 优先 {@link #parse(SemanticParserInput)}，
 * {@link #parseUserQuestion(String)}（v1）仅 fallback / 对照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuerySemanticLlmParser {

    private final LlmGateway llmGateway;
    private final AiPromptService aiPromptService;

    @Value("${ai.agent.querySemanticLlm.enabled:true}")
    private boolean enabled;

    /**
     * v2：user 消息为 {@link SemanticParserInput} 的 JSON；system 为 {@link AiPromptIds#SEMANTIC_QUERY_PARSER_V2}。
     * Resolver：v2 为主入口；v1 见 {@link #parseUserQuestion(String)}。
     */
    public AiQuerySemanticParseResult parse(SemanticParserInput input) {
        String pid = AiPromptIds.SEMANTIC_QUERY_PARSER_V2;
        if (!enabled || input == null || !StringUtils.hasText(input.getCurrentUserMessage())) {
            AiQuerySemanticParseResult out =
                    AiQuerySemanticParseResult.builder()
                            .parseMissing(true)
                            .observationJsonParseError("skipped_disabled_or_empty_message")
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
            AiQuerySemanticParseResult parsed = AiQuerySemanticParseResultJsonParser.parseRaw(raw);
            String err = parsed.isParseMissing()
                    ? AiQuerySemanticParseResultJsonParser.describeParseFailureReason(raw)
                    : null;
            AiQuerySemanticParseResult out =
                    parsed.toBuilder()
                            .promptRegistryId(pid)
                            .observationLlmRawText(rawObs)
                            .observationJsonParseError(err)
                            .build();
            logSemanticInvocation("v2", pid, raw, out, null);
            return out;
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

    public AiQuerySemanticParseResult parseUserQuestion(String sanitizedUserMessage) {
        String pid = AiPromptIds.SEMANTIC_QUERY_PARSER_V1;
        if (!enabled || !StringUtils.hasText(sanitizedUserMessage)) {
            AiQuerySemanticParseResult out = AiQuerySemanticParseResult.builder().parseMissing(true).build();
            logSemanticInvocation("v1", pid, null, out, "skipped_disabled_or_empty_message");
            return out;
        }
        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(pid);
        } catch (RuntimeException bootEx) {
            log.warn("[AiQuerySemanticLlmParser] load semantic prompt failed: {}", bootEx.toString());
            AiQuerySemanticParseResult out = AiQuerySemanticParseResult.builder().parseMissing(true).build();
            logSemanticInvocation("v1", pid, null, out, "semantic_prompt_load_failed");
            return out;
        }
        String raw = null;
        try {
            raw = llmGateway.chatSimple(systemPrompt, sanitizedUserMessage.trim());
            if (!StringUtils.hasText(raw)) {
                AiQuerySemanticParseResult out =
                        AiQuerySemanticParseResult.builder().parseMissing(true).promptRegistryId(pid).build();
                logSemanticInvocation("v1", pid, raw, out, "empty_llm_response");
                return out;
            }
            AiQuerySemanticParseResult parsed = AiQuerySemanticParseResultJsonParser.parseRaw(raw);
            AiQuerySemanticParseResult out = parsed.toBuilder().promptRegistryId(pid).build();
            logSemanticInvocation("v1", pid, raw, out, null);
            return out;
        } catch (Exception e) {
            log.warn("[AiQuerySemanticLlmParser] llm semantic parse failed: {}", e.toString());
            AiQuerySemanticParseResult out =
                    AiQuerySemanticParseResult.builder().parseMissing(true).promptRegistryId(pid).build();
            logSemanticInvocation("v1", pid, raw, out, "exception:" + e.getClass().getSimpleName());
            return out;
        }
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
