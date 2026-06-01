package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptRegistry;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单专家 LLM Harness 调试快照：输入 / 输出 / 决策分块（只观测，不参与业务判断）。
 */
public final class MenuOperationExpertNarrativePromptPreviewSupport {

    static final int MAX_TEXT_CHARS = 20_000;

    public static final String FINAL_ANSWER_SOURCE_LLM = "llm_expert_narrative";
    public static final String FINAL_ANSWER_SOURCE_LLM_PRESENTATION = "llm_expert_presentation";
    public static final String FINAL_ANSWER_SOURCE_DETERMINISTIC = "deterministic_fallback";
    public static final String FINAL_ANSWER_SOURCE_SKIPPED = "skipped";

    private MenuOperationExpertNarrativePromptPreviewSupport() {}

    public static Map<String, Object> buildInputPreview(
            String promptId,
            String promptPath,
            String systemPrompt,
            String userMessage,
            Map<String, Object> inputPayload) {
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("promptId", promptId);
        preview.put("promptPath", promptPath);

        TruncatedText system = truncate(systemPrompt);
        TruncatedText user = truncate(userMessage);
        preview.put("systemPrompt", system.text());
        preview.put("promptText", system.text());
        preview.put("userMessage", user.text());
        preview.put("inputPayload", inputPayload == null ? Map.of() : inputPayload);
        if (inputPayload != null && !inputPayload.isEmpty()) {
            preview.put("inputPayloadKeys", new ArrayList<>(inputPayload.keySet()));
        }

        List<Map<String, String>> finalMessages = new ArrayList<>(2);
        finalMessages.add(message("system", system.text()));
        finalMessages.add(message("user", user.text()));
        preview.put("finalMessages", finalMessages);
        preview.put("finalPrompt", composeFinalPrompt(system.text(), user.text()));

        boolean truncated = system.truncated() || user.truncated();
        preview.put("truncated", truncated);

        preview.put("auditContainsRawToolResults", containsRawToolResultsInPayloadData(inputPayload));
        preview.put("auditContainsNotInP1", containsNotInP1InPayloadData(inputPayload));
        preview.put("auditContainsKnownGapCode", containsKnownGapInPayloadData(inputPayload));
        return preview;
    }

    public static Map<String, Object> buildOutputPreview(String rawResponse, String normalizedResponse) {
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        if (StringUtils.hasText(rawResponse)) {
            TruncatedText raw = truncate(rawResponse);
            preview.put("llmRawResponsePreview", raw.text());
            preview.put("outputLength", rawResponse.length());
            if (raw.truncated()) {
                preview.put("truncated", true);
            }
        } else {
            preview.put("llmRawResponsePreview", null);
            preview.put("outputLength", 0);
        }
        if (StringUtils.hasText(normalizedResponse)) {
            TruncatedText normalized = truncate(normalizedResponse);
            preview.put("llmNormalizedResponsePreview", normalized.text());
            preview.put("normalizedOutputLength", normalizedResponse.length());
            if (Boolean.TRUE.equals(preview.get("truncated")) || normalized.truncated()) {
                preview.put("truncated", true);
            }
        } else {
            preview.put("llmNormalizedResponsePreview", null);
            preview.put("normalizedOutputLength", 0);
        }
        if (!preview.containsKey("truncated")) {
            preview.put("truncated", false);
        }
        return preview;
    }

    public static Map<String, Object> buildDecisionPreview(
            boolean enabled,
            boolean llmUsed,
            boolean fallbackUsed,
            String outputGuardResult,
            String rejectedReason,
            String finalAnswerSource) {
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("enabled", enabled);
        preview.put("llmUsed", llmUsed);
        preview.put("fallbackUsed", fallbackUsed);
        preview.put("outputGuardResult", outputGuardResult);
        preview.put("rejectedReason", StringUtils.hasText(rejectedReason) ? rejectedReason.trim() : null);
        preview.put("finalAnswerSource", finalAnswerSource);
        return preview;
    }

    public static Map<String, Object> parseInputPayload(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return Map.of();
        }
        try {
            JSONObject parsed = JSON.parseObject(userMessage.trim());
            if (parsed == null || parsed.isEmpty()) {
                return Map.of();
            }
            return new LinkedHashMap<>(parsed);
        } catch (Exception ignore) {
            return Map.of("parseError", "invalid_json");
        }
    }

    public static String defaultPromptPath(AiPromptRegistry registry) {
        if (registry == null) {
            return "ai-prompts/semantic/menu-expert-runtime-prompt.md";
        }
        String path = registry.resolveClasspathRelativePath(AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1);
        return StringUtils.hasText(path) ? path.trim() : "ai-prompts/semantic/menu-expert-runtime-prompt.md";
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private static String composeFinalPrompt(String systemPrompt, String userMessage) {
        return "--- system ---\n"
                + (systemPrompt == null ? "" : systemPrompt)
                + "\n\n--- user ---\n"
                + (userMessage == null ? "" : userMessage);
    }

    private static TruncatedText truncate(String text) {
        if (text == null) {
            return new TruncatedText("", false);
        }
        if (text.length() <= MAX_TEXT_CHARS) {
            return new TruncatedText(text, false);
        }
        return new TruncatedText(text.substring(0, MAX_TEXT_CHARS) + "\n…[truncated]", true);
    }

    private static boolean containsKnownGapInPayloadData(Map<String, Object> inputPayload) {
        if (inputPayload == null || inputPayload.isEmpty()) {
            return false;
        }
        return jsonContainsAny(inputPayload.get("menuOptimizationPlan"), "_NOT_IN_P1", "knownGap", "NOT_IN_P1")
                || jsonContainsAny(inputPayload.get("cardPayload"), "_NOT_IN_P1", "knownGap", "NOT_IN_P1")
                || jsonContainsAny(inputPayload.get("hardRules"), "_NOT_IN_P1", "knownGap", "NOT_IN_P1");
    }

    private static boolean containsRawToolResultsInPayloadData(Map<String, Object> inputPayload) {
        if (inputPayload == null || inputPayload.isEmpty()) {
            return false;
        }
        if (inputPayload.containsKey("toolResults") || inputPayload.containsKey("tool_results")) {
            return true;
        }
        return containsRawToolStructure(inputPayload.get("menuOptimizationPlan"))
                || containsRawToolStructure(inputPayload.get("cardPayload"));
    }

    private static boolean containsRawToolStructure(Object node) {
        if (node == null) {
            return false;
        }
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString();
                if (isRawToolResultKey(key)) {
                    return true;
                }
                if (looksLikeToolEnvelope(map)) {
                    return true;
                }
                if (containsRawToolStructure(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                if (containsRawToolStructure(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRawToolResultKey(String key) {
        if (key == null) {
            return false;
        }
        return switch (key) {
            case "toolResults",
                    "tool_results",
                    "sql",
                    "sqlQuery",
                    "querySql",
                    "jdbcParams",
                    "sqlParams",
                    "preparedStatement",
                    "rawRows",
                    "fullResultSet",
                    "resultSetRows",
                    "dbRawRows" -> true;
            default -> false;
        };
    }

    private static boolean looksLikeToolEnvelope(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        Object schemaVersion = map.get("schemaVersion");
        if (!"v1".equals(schemaVersion == null ? null : schemaVersion.toString())) {
            return false;
        }
        return map.containsKey("tool") && (map.containsKey("success") || map.containsKey("data"));
    }

    private static boolean containsNotInP1InPayloadData(Map<String, Object> inputPayload) {
        if (inputPayload == null || inputPayload.isEmpty()) {
            return false;
        }
        return jsonContainsLiteral(inputPayload.get("menuOptimizationPlan"), "NOT_IN_P1")
                || jsonContainsLiteral(inputPayload.get("cardPayload"), "NOT_IN_P1")
                || jsonContainsLiteral(inputPayload.get("hardRules"), "NOT_IN_P1");
    }

    private static boolean jsonContainsLiteral(Object node, String literal) {
        if (node == null || literal == null) {
            return false;
        }
        return JSON.toJSONString(node).contains(literal);
    }

    private static boolean jsonContainsAny(Object node, String... needles) {
        if (node == null || needles == null) {
            return false;
        }
        String json = JSON.toJSONString(node);
        for (String needle : needles) {
            if (needle != null && json.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private record TruncatedText(String text, boolean truncated) {}
}
