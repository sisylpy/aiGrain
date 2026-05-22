package com.nongxinle.ai.semantic;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 解析 {@link AiQuerySemanticLlmParser} 产出的 JSON；忽略任何禁止字段（含嵌套路径上的键名）。
 */
public final class AiQuerySemanticParseResultJsonParser {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "queryStoreIds",
            "queryRealDepartmentIds",
            "expandedSqlDepartmentIds",
            "storeToDepartmentIds",
            "queryDistributerId");

    private AiQuerySemanticParseResultJsonParser() {
    }

    /**
     * 在 {@link #parseRaw(String)} 已得到 {@code parseMissing=true} 时，给出可归因的失败原因（不含 ID）。
     */
    public static String describeParseFailureReason(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "blank_response";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        if (!trimmed.contains("{")) {
            return "no_json_object_markers_likely_prose";
        }
        JSONObject o = extractJsonObject(trimmed);
        if (o == null) {
            return "json_extract_or_syntax_failed";
        }
        if (o.isEmpty()) {
            return "empty_json_object_after_extract";
        }
        return "parse_missing_unclassified";
    }

    public static AiQuerySemanticParseResult parseRaw(String raw) {
        if (StrUtil.isBlank(raw)) {
            return empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        JSONObject o = extractJsonObject(trimmed);
        if (o == null || o.isEmpty()) {
            return AiQuerySemanticParseResult.builder()
                    .parseMissing(true)
                    .rawJsonDigest(digest(trimmed))
                    .build();
        }
        stripForbiddenKeysRecursive(o);
        return fromJsonObject(o, digest(trimmed));
    }

    private static String digest(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replace("\n", " ").trim();
        int max = 2000;
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static AiQuerySemanticParseResult empty() {
        return AiQuerySemanticParseResult.builder()
                .parseMissing(true)
                .build();
    }

    private static void stripForbiddenKeysRecursive(JSONObject o) {
        if (o == null) {
            return;
        }
        for (String k : FORBIDDEN_KEYS) {
            o.remove(k);
        }
        for (String key : Set.copyOf(o.keySet())) {
            Object v = o.get(key);
            if (v instanceof JSONObject jo) {
                stripForbiddenKeysRecursive(jo);
            }
        }
    }

    static AiQuerySemanticParseResult fromJsonObject(JSONObject o, String digest) {
        AiQuerySemanticParseResult.TimePart time = null;
        JSONObject tjo = o.getJSONObject("time");
        if (tjo != null && !tjo.isEmpty()) {
            stripForbiddenKeysRecursive(tjo);
            time = AiQuerySemanticParseResult.TimePart.builder()
                    .timeType(trimToNull(tjo.getStr("timeType")))
                    .startDate(trimToNull(tjo.getStr("startDate")))
                    .endDate(trimToNull(tjo.getStr("endDate")))
                    .timeSource(trimToNull(tjo.getStr("timeSource")))
                    .needInheritFromPrevious(parseNullableBool(tjo.get("needInheritFromPrevious")))
                    .reason(trimToNull(tjo.getStr("reason")))
                    .build();
        }

        AiQuerySemanticParseResult.RequestedScopePart scope = null;
        JSONObject sjo = o.getJSONObject("requestedScope");
        if (sjo != null && !sjo.isEmpty()) {
            stripForbiddenKeysRecursive(sjo);
            scope = AiQuerySemanticParseResult.RequestedScopePart.builder()
                    .requestedScopeType(trimToNull(sjo.getStr("requestedScopeType")))
                    .mentionedStoreName(trimToNull(sjo.getStr("mentionedStoreName")))
                    .mentionedStoreNames(parseNullableStringList(sjo.get("mentionedStoreNames")))
                    .mentionedDepartmentName(trimToNull(sjo.getStr("mentionedDepartmentName")))
                    .mentionedWarehouseName(trimToNull(sjo.getStr("mentionedWarehouseName")))
                    .scopeSource(trimToNull(sjo.getStr("scopeSource")))
                    .needInheritFromPrevious(parseNullableBool(sjo.get("needInheritFromPrevious")))
                    .build();
        }

        AiQuerySemanticParseResult.MetricPart metric = null;
        JSONObject mjo = o.getJSONObject("metric");
        if (mjo != null && !mjo.isEmpty()) {
            stripForbiddenKeysRecursive(mjo);
            metric = AiQuerySemanticParseResult.MetricPart.builder()
                    .primaryMetric(trimToNull(mjo.getStr("primaryMetric")))
                    .rankingType(trimToNull(mjo.getStr("rankingType")))
                    .purchaseSourceType(trimToNull(mjo.getStr("purchaseSourceType")))
                    .stockReduceType(trimToNull(mjo.getStr("stockReduceType")))
                    .build();
        }

        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orchestration = null;
        JSONObject orchJo = o.getJSONObject("orchestrationDecisionCandidate");
        if (orchJo != null && !orchJo.isEmpty()) {
            stripForbiddenKeysRecursive(orchJo);
            orchestration = AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart.builder()
                    .taskMode(trimToNull(orchJo.getStr("taskMode")))
                    .selectedAgents(parseNullableStringList(orchJo.get("selectedAgents")))
                    .selectedTools(parseNullableStringList(orchJo.get("selectedTools")))
                    .plannerRequired(parseNullableBool(orchJo.get("plannerRequired")))
                    .multiAgentRequired(parseNullableBool(orchJo.get("multiAgentRequired")))
                    .approvalRequired(parseNullableBool(orchJo.get("approvalRequired")))
                    .clarificationRequired(parseNullableBool(orchJo.get("clarificationRequired")))
                    .clarificationQuestion(trimToNull(orchJo.getStr("clarificationQuestion")))
                    .confidence(parseDouble(orchJo.get("confidence")))
                    .reason(trimToNull(orchJo.getStr("reason")))
                    .build();
        }

        AiQuerySemanticParseResult.SemanticSlotsPart semanticSlots = parseSemanticSlots(o);

        return AiQuerySemanticParseResult.builder()
                .intent(trimToNull(o.getStr("intent")))
                .semanticDomain(trimToNull(o.getStr("domain")))
                .mentionedDishName(trimToNull(o.getStr("mentionedDishName")))
                .confidence(parseDouble(o.get("confidence")))
                .followUp(parseNullableBool(o.get("isFollowUp")))
                .intentAction(trimToNull(o.getStr("intentAction")))
                .timeAction(trimToNull(o.getStr("timeAction")))
                .scopeAction(trimToNull(o.getStr("scopeAction")))
                .metricAction(trimToNull(o.getStr("metricAction")))
                .time(time)
                .requestedScope(scope)
                .metric(metric)
                .semanticSlots(semanticSlots)
                .orchestrationDecisionCandidate(orchestration)
                .needClarification(parseNullableBool(o.get("needClarification")))
                .clarificationQuestion(trimToNull(o.getStr("clarificationQuestion")))
                .reason(trimToNull(o.getStr("reason")))
                .rawJsonDigest(digest)
                .parseMissing(false)
                .build();
    }

    private static Double parseDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = StrUtil.trimToEmpty(String.valueOf(v));
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> parseNullableStringList(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof JSONArray ja) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < ja.size(); i++) {
                String t = trimToNull(ja.getStr(i));
                if (t != null) {
                    out.add(t);
                }
            }
            return out.isEmpty() ? null : out;
        }
        if (v instanceof List<?> lst) {
            List<String> out = new ArrayList<>();
            for (Object o : lst) {
                if (o == null) {
                    continue;
                }
                String t = trimToNull(String.valueOf(o));
                if (t != null) {
                    out.add(t);
                }
            }
            return out.isEmpty() ? null : out;
        }
        String single = trimToNull(String.valueOf(v));
        if (single != null) {
            return Collections.singletonList(single);
        }
        return null;
    }

    private static Boolean parseNullableBool(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = StrUtil.trimToEmpty(String.valueOf(v)).toLowerCase(Locale.ROOT);
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private static String trimToNull(String s) {
        String t = s == null ? "" : s.trim();
        return StringUtils.hasText(t) ? t : null;
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart parseSemanticSlots(JSONObject o) {
        if (o == null) {
            return null;
        }
        JSONObject sjo = o.getJSONObject("semanticSlots");
        if (sjo == null || sjo.isEmpty()) {
            return null;
        }
        stripForbiddenKeysRecursive(sjo);
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .selectedContractId(trimToNull(sjo.getStr("selectedContractId")))
                .queryObject(trimToNull(sjo.getStr("queryObject")))
                .operation(trimToNull(sjo.getStr("operation")))
                .metric(trimToNull(sjo.getStr("metric")))
                .sourceFacet(trimToNull(sjo.getStr("sourceFacet")))
                .anchorPolicy(trimToNull(sjo.getStr("anchorPolicy")))
                .detailWanted(trimToNull(sjo.getStr("detailWanted")))
                .structuredIntentDetailWire(trimToNull(sjo.getStr("structuredIntentDetailWire")))
                .answerPlanType(trimToNull(sjo.getStr("answerPlanType")))
                .build();
    }

    private static JSONObject extractJsonObject(String trimmed) {
        int l = trimmed.indexOf('{');
        int r = trimmed.lastIndexOf('}');
        if (l < 0 || r <= l) {
            return tryParseObject(trimmed);
        }
        try {
            return JSONUtil.parseObj(trimmed.substring(l, r + 1));
        } catch (Exception ignored) {
            return tryParseObject(trimmed);
        }
    }

    private static JSONObject tryParseObject(String s) {
        if (StrUtil.isBlank(s) || !s.trim().startsWith("{")) {
            return null;
        }
        try {
            return JSONUtil.parseObj(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
