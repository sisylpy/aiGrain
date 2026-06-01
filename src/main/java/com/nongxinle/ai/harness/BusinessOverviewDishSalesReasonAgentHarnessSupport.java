package com.nongxinle.ai.harness;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.prompt.AiPromptIds;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Harness：营业额卡底部菜品销量原因 Agent 可观测字段（不写业务语义）。 */
public final class BusinessOverviewDishSalesReasonAgentHarnessSupport {

    private static final int INPUT_PREVIEW_MAX = 4000;
    private static final int LLM_OUTPUT_PREVIEW_MAX = 2000;
    private static final int FINAL_SUMMARY_MAX = 500;

    public static final String KEY_ENABLED = "dishSalesReasonAgentEnabled";
    public static final String KEY_PROMPT_ID = "dishSalesReasonPromptId";
    public static final String KEY_INPUT_PREVIEW = "dishSalesReasonInputPreview";
    public static final String KEY_LLM_OUTPUT_PREVIEW = "dishSalesReasonLlmOutputPreview";
    public static final String KEY_FINAL_SUMMARY = "dishSalesReasonFinalSummary";
    public static final String KEY_FAILURE_REASON = "dishSalesReasonFailureReason";
    public static final String KEY_REVENUE_CARD_REASON_SUMMARY = "revenueCardReasonSummary";
    public static final String KEY_FACT_PACK_DIAGNOSTICS = "dishSalesReasonFactPackDiagnostics";

    static final List<String> HARNESS_FIELD_KEYS =
            List.of(
                    KEY_ENABLED,
                    KEY_PROMPT_ID,
                    KEY_INPUT_PREVIEW,
                    KEY_LLM_OUTPUT_PREVIEW,
                    KEY_FINAL_SUMMARY,
                    KEY_FAILURE_REASON,
                    KEY_REVENUE_CARD_REASON_SUMMARY,
                    KEY_FACT_PACK_DIAGNOSTICS);

    private BusinessOverviewDishSalesReasonAgentHarnessSupport() {}

    public static LinkedHashMap<String, Object> newHarnessMap(boolean agentEnabled) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put(KEY_ENABLED, agentEnabled);
        out.put(KEY_PROMPT_ID, AiPromptIds.BUSINESS_OVERVIEW_DISH_SALES_REASON_AGENT_V2);
        out.put(KEY_INPUT_PREVIEW, null);
        out.put(KEY_LLM_OUTPUT_PREVIEW, null);
        out.put(KEY_FINAL_SUMMARY, null);
        out.put(KEY_FAILURE_REASON, null);
        out.put(KEY_REVENUE_CARD_REASON_SUMMARY, null);
        out.put(KEY_FACT_PACK_DIAGNOSTICS, null);
        return out;
    }

    public static void publish(AiRunState state, Map<String, Object> harness) {
        if (state == null || harness == null || harness.isEmpty()) {
            return;
        }
        state.setDishSalesReasonAgentHarnessDebug(new LinkedHashMap<>(harness));
    }

    public static void recordFailure(Map<String, Object> harness, String reason) {
        if (harness == null) {
            return;
        }
        harness.put(KEY_FAILURE_REASON, reason);
    }

    @SuppressWarnings("unchecked")
    public static void recordFactPackDiagnostics(Map<String, Object> harness, Map<String, Object> factPack) {
        if (harness == null || factPack == null) {
            return;
        }
        Object diag = factPack.get("factPackDiagnostics");
        if (diag instanceof Map<?, ?> m) {
            harness.put(KEY_FACT_PACK_DIAGNOSTICS, new LinkedHashMap<>((Map<String, Object>) m));
        } else {
            harness.put(KEY_FACT_PACK_DIAGNOSTICS, null);
        }
    }

    public static void recordInputPreview(
            Map<String, Object> harness, String systemPrompt, String userMessage) {
        if (harness == null) {
            return;
        }
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("promptId", harness.get(KEY_PROMPT_ID));
        preview.put("systemPromptPreview", truncate(systemPrompt, INPUT_PREVIEW_MAX));
        preview.put("userMessagePreview", truncate(userMessage, INPUT_PREVIEW_MAX));
        harness.put(KEY_INPUT_PREVIEW, preview);
    }

    public static void recordLlmOutputPreview(Map<String, Object> harness, String raw) {
        if (harness == null) {
            return;
        }
        LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
        preview.put("llmRawResponsePreview", truncate(raw, LLM_OUTPUT_PREVIEW_MAX));
        harness.put(KEY_LLM_OUTPUT_PREVIEW, preview);
    }

    public static void recordFinalSummary(Map<String, Object> harness, String summary) {
        if (harness == null) {
            return;
        }
        if (!StringUtils.hasText(summary)) {
            harness.put(KEY_FINAL_SUMMARY, null);
            return;
        }
        harness.put(KEY_FINAL_SUMMARY, truncate(summary.trim(), FINAL_SUMMARY_MAX));
    }

    public static void recordRevenueCardWrittenSummary(AiRunState state, String writtenSummary) {
        if (state == null) {
            return;
        }
        Map<String, Object> harness = state.getDishSalesReasonAgentHarnessDebug();
        if (harness == null) {
            harness = newHarnessMap(true);
        } else {
            harness = new LinkedHashMap<>(harness);
        }
        harness.put(
                KEY_REVENUE_CARD_REASON_SUMMARY,
                StringUtils.hasText(writtenSummary) ? truncate(writtenSummary.trim(), FINAL_SUMMARY_MAX) : null);
        publish(state, harness);
    }

    public static void putHarnessDefaults(LinkedHashMap<String, Object> out) {
        if (out == null) {
            return;
        }
        for (String k : HARNESS_FIELD_KEYS) {
            out.put(k, null);
        }
    }

    public static void appendFlatHarnessFields(LinkedHashMap<String, Object> out, AiRunState state) {
        putHarnessDefaults(out);
        if (state == null || state.getDishSalesReasonAgentHarnessDebug() == null) {
            return;
        }
        Map<String, Object> harness = state.getDishSalesReasonAgentHarnessDebug();
        for (String k : HARNESS_FIELD_KEYS) {
            Object v = harness.get(k);
            out.put(k, v instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : v);
        }
        mirrorIntoMasterBusinessAgentDebug(out, harness);
    }

    /** GET {@code harnessDebug} 顶层：与 menuExpert* 同级下发扁平字段。 */
    public static void copyFlatHarnessFieldsToMap(Map<String, Object> target, AiRunState state) {
        if (target == null || state == null || state.getDishSalesReasonAgentHarnessDebug() == null) {
            return;
        }
        Map<String, Object> harness = state.getDishSalesReasonAgentHarnessDebug();
        for (String k : HARNESS_FIELD_KEYS) {
            Object v = harness.get(k);
            target.put(k, v instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : v);
        }
    }

    @SuppressWarnings("unchecked")
    static void mirrorIntoMasterBusinessAgentDebug(
            LinkedHashMap<String, Object> out, Map<String, Object> harness) {
        if (out == null || harness == null || harness.isEmpty()) {
            return;
        }
        Object mdObj = out.get("masterBusinessAgentDebug");
        LinkedHashMap<String, Object> md;
        if (mdObj instanceof Map<?, ?> existing && !existing.isEmpty()) {
            md = new LinkedHashMap<>((Map<String, Object>) existing);
        } else {
            md = new LinkedHashMap<>();
        }
        for (String k : HARNESS_FIELD_KEYS) {
            Object v = harness.get(k);
            md.put(k, v instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : v);
        }
        out.put("masterBusinessAgentDebug", md);
    }

    private static String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
