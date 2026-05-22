package com.nongxinle.ai.followup.rewrite.llm;

import com.nongxinle.ai.followup.rewrite.FollowUpRewriteDebug;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteRequest;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteResult;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase1-I：LLM 省略追问补全 → {@code completedUserQuery} → v2 语义主链。
 * <p>
 * 仅输出 {@code completedUserQuery} 或 {@code clarificationQuestion}；不输出 intent/path/wire/Tool/planType。
 * LLM 失败或 JSON 非法时 fallback 为原句 passthrough，不恢复 Java 规则模板。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmFollowUpQueryRewriter {

    private final LlmGateway llmGateway;
    private final AiPromptService aiPromptService;

    public FollowUpRewriteResult rewrite(FollowUpRewriteRequest request) {
        if (request == null || !StringUtils.hasText(request.getNormalizedUserMessage())) {
            return FollowUpRewriteResult.passthrough();
        }
        String pid = AiPromptIds.FOLLOWUP_QUERY_REWRITER_V1;
        String systemPrompt;
        try {
            systemPrompt = aiPromptService.require(pid);
        } catch (RuntimeException ex) {
            log.warn("[LlmFollowUpQueryRewriter] prompt load failed: {}", ex.toString());
            return fallbackPassthrough(pid, null, "rewriteError", "prompt_load_failed", null);
        }
        String userPayload = LlmFollowUpRewritePromptBuilder.toUserJson(request);
        String raw = null;
        try {
            raw = llmGateway.chatSimple(systemPrompt, userPayload);
            if (!StringUtils.hasText(raw)) {
                return fallbackPassthrough(pid, truncateRaw(raw), "rewriteError", "empty_llm_response", null);
            }
            LlmFollowUpRewriteParsed parsed = LlmFollowUpRewriteJsonParser.parseRaw(raw);
            if (parsed.isParseFailed()) {
                return fallbackPassthrough(
                        pid,
                        truncateRaw(raw),
                        "parseError",
                        parsed.getParseError(),
                        parsed.getRawDigest());
            }
            return mapParsed(request, pid, truncateRaw(raw), parsed);
        } catch (Exception e) {
            log.warn("[LlmFollowUpQueryRewriter] llm rewrite failed: {}", e.toString());
            return fallbackPassthrough(
                    pid, truncateRaw(raw), "rewriteError", e.getClass().getSimpleName(), null);
        }
    }

    private static FollowUpRewriteResult mapParsed(
            FollowUpRewriteRequest request,
            String promptId,
            String rawObs,
            LlmFollowUpRewriteParsed parsed) {
        if (parsed.isNeedClarification() && StringUtils.hasText(parsed.getClarificationQuestion())) {
            return FollowUpRewriteResult.builder()
                    .isFollowUp(true)
                    .canRewrite(false)
                    .needClarification(true)
                    .clarificationQuestion(parsed.getClarificationQuestion().trim())
                    .rewriteReason(parsed.getRewriteReason())
                    .usedAnchors(toUsedAnchorMaps(parsed.getUsedAnchors()))
                    .debug(buildLlmDebug(promptId, rawObs, parsed, null, null))
                    .build();
        }
        if (!parsed.isFollowUp()) {
            return FollowUpRewriteResult.builder()
                    .isFollowUp(false)
                    .canRewrite(false)
                    .needClarification(false)
                    .rewriteReason(parsed.getRewriteReason())
                    .debug(buildLlmDebug(promptId, rawObs, parsed, null, null))
                    .build();
        }
        if (parsed.isCanRewrite() && StringUtils.hasText(parsed.getCompletedUserQuery())) {
            String completed = parsed.getCompletedUserQuery().trim();
            String qualityReject =
                    LlmFollowUpRewriteQualityValidator.rejectReason(request, completed);
            if (qualityReject != null) {
                return qualityRejectedOutcome(
                        request, promptId, rawObs, parsed, qualityReject, completed);
            }
            boolean inheritedTime = boolDebug(parsed.getDebug(), "inheritedTime");
            boolean inheritedScope = boolDebug(parsed.getDebug(), "inheritedScope");
            List<LlmFollowUpRewriteParsed.UsedAnchor> anchors = parsed.getUsedAnchors();
            String anchorType = null;
            String anchorName = null;
            if (anchors != null && anchors.size() == 1) {
                anchorType = anchors.get(0).getAnchorType();
                anchorName = anchors.get(0).getAnchorName();
            }
            return FollowUpRewriteResult.builder()
                    .isFollowUp(true)
                    .canRewrite(true)
                    .completedUserQuery(completed)
                    .rewriteReason(parsed.getRewriteReason())
                    .inheritedTime(inheritedTime)
                    .inheritedScope(inheritedScope)
                    .inheritedAnchorType(anchorType)
                    .inheritedAnchorName(anchorName)
                    .usedAnchors(toUsedAnchorMaps(anchors))
                    .needClarification(false)
                    .debug(buildLlmDebug(promptId, rawObs, parsed, null, null))
                    .build();
        }
        return FollowUpRewriteResult.builder()
                .isFollowUp(parsed.isFollowUp())
                .canRewrite(false)
                .needClarification(false)
                .rewriteReason(parsed.getRewriteReason())
                .usedAnchors(toUsedAnchorMaps(parsed.getUsedAnchors()))
                .debug(buildLlmDebug(promptId, rawObs, parsed, null, null))
                .build();
    }

    private static FollowUpRewriteResult qualityRejectedOutcome(
            FollowUpRewriteRequest request,
            String promptId,
            String rawObs,
            LlmFollowUpRewriteParsed parsed,
            String qualityReject,
            String rejectedCompleted) {
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put("rewrite_quality_rejected", qualityReject);
        if (StringUtils.hasText(rejectedCompleted)) {
            extras.put("rejectedCompletedUserQuery", rejectedCompleted);
        }
        if (parsed.getDebug() != null) {
            extras.putAll(parsed.getDebug());
        }
        boolean askClarification =
                shouldClarifyAfterQualityReject(qualityReject, request, parsed);
        String clarification =
                askClarification
                        ? firstNonBlank(
                                parsed.getClarificationQuestion(),
                                genericClarificationForQualityReject(qualityReject))
                        : null;
        return FollowUpRewriteResult.builder()
                .isFollowUp(parsed.isFollowUp() || (request != null && request.isHasPreviousTurn()))
                .canRewrite(false)
                .needClarification(askClarification && StringUtils.hasText(clarification))
                .clarificationQuestion(
                        askClarification && StringUtils.hasText(clarification)
                                ? clarification.trim()
                                : null)
                .rewriteReason(parsed.getRewriteReason())
                .usedAnchors(toUsedAnchorMaps(parsed.getUsedAnchors()))
                .debug(
                        FollowUpRewriteDebug.builder()
                                .detector("LLM")
                                .promptId(promptId)
                                .llmRawText(rawObs)
                                .extras(extras)
                                .build())
                .build();
    }

    private static boolean shouldClarifyAfterQualityReject(
            String qualityReject, FollowUpRewriteRequest request, LlmFollowUpRewriteParsed parsed) {
        if ("unresolved_deictic".equals(qualityReject)
                || "unchanged_from_raw".equals(qualityReject)
                || "scope_pivot_leaked_stores".equals(qualityReject)) {
            return true;
        }
        return parsed.isNeedClarification();
    }

    private static String genericClarificationForQualityReject(String qualityReject) {
        return switch (qualityReject) {
            case "unresolved_deictic", "unchanged_from_raw" -> "请问您指的是哪一项？";
            case "scope_pivot_leaked_stores" -> "请问您要查哪家门店？";
            default -> "能再具体说一下您想问的内容吗？";
        };
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        return StringUtils.hasText(b) ? b.trim() : null;
    }

    private static FollowUpRewriteResult fallbackPassthrough(
            String promptId, String rawObs, String errorKey, String errorValue, String rawDigest) {
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        extras.put(errorKey, errorValue);
        if (rawDigest != null) {
            extras.put("rawDigest", rawDigest);
        }
        return FollowUpRewriteResult.builder()
                .isFollowUp(false)
                .canRewrite(false)
                .needClarification(false)
                .debug(
                        FollowUpRewriteDebug.builder()
                                .detector("LLM")
                                .promptId(promptId)
                                .llmRawText(rawObs)
                                .extras(extras)
                                .build())
                .build();
    }

    private static FollowUpRewriteDebug buildLlmDebug(
            String promptId,
            String rawObs,
            LlmFollowUpRewriteParsed parsed,
            String errorKey,
            String errorValue) {
        LinkedHashMap<String, Object> extras = new LinkedHashMap<>();
        if (parsed.getDebug() != null) {
            extras.putAll(parsed.getDebug());
        }
        if (errorKey != null) {
            extras.put(errorKey, errorValue);
        }
        Double confidence = null;
        if (parsed.getDebug() != null && parsed.getDebug().get("confidence") instanceof Number n) {
            confidence = n.doubleValue();
        }
        return FollowUpRewriteDebug.builder()
                .detector("LLM")
                .promptId(promptId)
                .llmRawText(rawObs)
                .confidence(confidence)
                .extras(extras.isEmpty() ? null : extras)
                .build();
    }

    private static boolean boolDebug(Map<String, Object> debug, String key) {
        if (debug == null) {
            return false;
        }
        Object v = debug.get(key);
        return v instanceof Boolean b && b;
    }

    private static List<Map<String, String>> toUsedAnchorMaps(
            List<LlmFollowUpRewriteParsed.UsedAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (LlmFollowUpRewriteParsed.UsedAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getAnchorName())) {
                continue;
            }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            row.put("anchorType", a.getAnchorType());
            row.put("anchorName", a.getAnchorName());
            out.add(row);
        }
        return out.isEmpty() ? null : out;
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
}
