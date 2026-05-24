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

import static java.util.Set.of;

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

    private static final Set<String> VALID_ACTION_TOKENS =
            of("NEW", "INHERIT_PREVIOUS", "OVERRIDE");

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
        return parseAndNormalizeProtocol(raw).parsed();
    }

    /**
     * 解析 LLM JSON 并做协议层字段位置修正（仅 schema：误放 nested 的 confidence / *Action 搬到顶层；
     * 不做业务语义推断）。
     */
    public static ProtocolNormalizeResult parseAndNormalizeProtocol(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new ProtocolNormalizeResult(
                    empty(), raw, new ProtocolRelocateResult(false, List.of()));
        }
        String trimmed = prepareTrimmedJson(raw);
        JSONObject o = extractJsonObject(trimmed);
        if (o == null || o.isEmpty()) {
            return new ProtocolNormalizeResult(
                    AiQuerySemanticParseResult.builder()
                            .parseMissing(true)
                            .rawJsonDigest(digest(trimmed))
                            .build(),
                    trimmed,
                    new ProtocolRelocateResult(false, List.of()));
        }
        stripForbiddenKeysRecursive(o);
        ProtocolRelocateResult relocate = normalizeProtocolFieldPlacement(o);
        String normalizedJson = JSONUtil.toJsonStr(o);
        AiQuerySemanticParseResult parsed = fromJsonObject(o, digest(trimmed));
        if (relocate.changed() && parsed != null && !parsed.isParseMissing()) {
            parsed =
                    parsed.toBuilder()
                            .querySemanticV2RepairAttempted(true)
                            .querySemanticV2RepairSuccess(true)
                            .querySemanticV2RepairReason(buildJavaProtocolRelocateReason(relocate))
                            .build();
        }
        return new ProtocolNormalizeResult(parsed, normalizedJson, relocate);
    }

    private static String buildJavaProtocolRelocateReason(
            ProtocolRelocateResult relocate) {
        if (relocate == null || relocate.moves() == null || relocate.moves().isEmpty()) {
            return "java_protocol_relocate";
        }
        return "java_protocol_relocate:" + String.join(";", relocate.moves());
    }

    public record ProtocolNormalizeResult(
            AiQuerySemanticParseResult parsed, String normalizedJson, ProtocolRelocateResult relocate) {}

    public record ProtocolRelocateResult(boolean changed, List<String> moves) {}

    /**
     * 协议层：将误置于 nested 的 confidence / *Action 搬到顶层；将非协议 enum 的 *Action 收窄为
     * NEW/INHERIT_PREVIOUS/OVERRIDE 或 INVALID（不做业务语义推断）。
     */
    static ProtocolRelocateResult normalizeProtocolFieldPlacement(JSONObject o) {
        List<String> moves = new ArrayList<>();
        if (o == null) {
            return new ProtocolRelocateResult(false, moves);
        }
        normalizeOrchestrationDecisionCandidateShape(o, moves);
        JSONObject slots = safeGetJSONObject(o, "semanticSlots");
        JSONObject metric = safeGetJSONObject(o, "metric");
        JSONObject orch = safeGetJSONObject(o, "orchestrationDecisionCandidate");
        normalizeSemanticSlotsMetricObject(o, moves);
        relocateNumericTopLevel(o, "confidence", moves, slots, metric, orch);

        for (String actionField : List.of("intentAction", "timeAction", "scopeAction", "metricAction")) {
            relocateStringTopLevel(o, actionField, moves, slots, metric);
        }

        JSONObject time = safeGetJSONObject(o, "time");
        if (time != null && time.containsKey("timeAction")) {
            String nestedTimeAction = trimToNull(time.getStr("timeAction"));
            if (nestedTimeAction != null) {
                if (!StringUtils.hasText(o.getStr("timeAction"))) {
                    o.set("timeAction", nestedTimeAction);
                    moves.add("timeAction: relocated from time");
                }
                time.remove("timeAction");
            }
        }

        for (String actionField : List.of("intentAction", "timeAction", "scopeAction", "metricAction")) {
            coerceTopLevelActionField(o, actionField, moves);
        }

        return new ProtocolRelocateResult(!moves.isEmpty(), List.copyOf(moves));
    }

    /**
     * 协议 enum 收窄：仅允许 NEW / INHERIT_PREVIOUS / OVERRIDE；有限旧写法同义映射；无法映射则 INVALID。
     */
    static String coerceProtocolActionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = normalizeActionToken(raw);
        if (VALID_ACTION_TOKENS.contains(u)) {
            return u;
        }
        return switch (u) {
            case "OVERWRITE", "OVER_WRITE", "REPLACE", "UPDATE" -> "OVERRIDE";
            case "INHERIT",
                    "INHERITED",
                    "INHERITED_PREVIOUS",
                    "USE_PREVIOUS",
                    "KEEP",
                    "KEEP_PREVIOUS",
                    "PREVIOUS" -> "INHERIT_PREVIOUS";
            case "RESET", "FRESH", "CURRENT", "DEFAULT" -> "NEW";
            default -> "INVALID";
        };
    }

    /**
     * orchestrationDecisionCandidate 仅观测：非 JSONObject（字符串/数字/布尔/数组等）时降级为空对象，不做业务推断。
     */
    static void normalizeOrchestrationDecisionCandidateShape(JSONObject top, List<String> moves) {
        if (top == null || moves == null || !top.containsKey("orchestrationDecisionCandidate")) {
            return;
        }
        Object val = top.get("orchestrationDecisionCandidate");
        if (val == null || val instanceof JSONObject) {
            return;
        }
        top.set("orchestrationDecisionCandidate", new JSONObject());
        moves.add("orchestrationDecisionCandidate_scalar_ignored");
    }

    /** 仅当值为 JSONObject 时返回；标量/数组等返回 null，避免 getJSONObject 抛 JSONException。 */
    static JSONObject safeGetJSONObject(JSONObject parent, String key) {
        if (parent == null || !parent.containsKey(key)) {
            return null;
        }
        Object val = parent.get(key);
        return val instanceof JSONObject jo ? jo : null;
    }

    /**
     * semanticSlots.metric 若为对象且含 metricKey，归一为 simple token（仅读 metricKey，不读 metricName / rankingType）。
     */
    static void normalizeSemanticSlotsMetricObject(JSONObject top, List<String> moves) {
        if (top == null || moves == null) {
            return;
        }
        JSONObject slots = safeGetJSONObject(top, "semanticSlots");
        if (slots == null || !slots.containsKey("metric")) {
            return;
        }
        Object metricVal = slots.get("metric");
        String token = extractMetricKeyToken(metricVal);
        if (token == null) {
            return;
        }
        String existing = trimToNull(slots.getStr("metric"));
        if (token.equals(existing)) {
            return;
        }
        slots.set("metric", token);
        moves.add("semanticSlots.metric: metric_object_to_key:" + token);
    }

    /** 仅从 metric 对象或 JSON 字符串读取 metricKey；不读 metricName / rankingType。 */
    static String extractMetricKeyToken(Object metricVal) {
        if (metricVal == null) {
            return null;
        }
        if (metricVal instanceof JSONObject jo) {
            return trimToNull(jo.getStr("metricKey"));
        }
        String raw = trimToNull(String.valueOf(metricVal));
        if (raw == null) {
            return null;
        }
        if (raw.startsWith("{") && raw.contains("metricKey")) {
            JSONObject jo = tryParseObject(raw);
            if (jo != null) {
                return trimToNull(jo.getStr("metricKey"));
            }
        }
        return null;
    }

    private static void relocateNumericTopLevel(
            JSONObject top, String key, List<String> moves, JSONObject... sources) {
        Double existing = parseDouble(top.get(key));
        if (existing != null && existing >= 0.0 && existing <= 1.0) {
            return;
        }
        for (JSONObject src : sources) {
            if (src == null || !src.containsKey(key)) {
                continue;
            }
            Double v = parseDouble(src.get(key));
            if (v != null && v >= 0.0 && v <= 1.0) {
                top.set(key, v);
                src.remove(key);
                moves.add(key + ": relocated from nested object");
                return;
            }
        }
    }

    private static void relocateStringTopLevel(
            JSONObject top, String key, List<String> moves, JSONObject... sources) {
        if (StringUtils.hasText(top.getStr(key))) {
            return;
        }
        for (JSONObject src : sources) {
            if (src == null || !src.containsKey(key)) {
                continue;
            }
            String v = trimToNull(src.getStr(key));
            if (v != null) {
                top.set(key, v);
                src.remove(key);
                moves.add(key + ": relocated from nested object");
                return;
            }
        }
    }

    private static void coerceTopLevelActionField(JSONObject o, String field, List<String> moves) {
        if (!o.containsKey(field)) {
            return;
        }
        String raw = o.getStr(field);
        if (!StringUtils.hasText(raw)) {
            return;
        }
        String coerced = coerceProtocolActionToken(raw);
        if (coerced == null) {
            return;
        }
        String normalizedRaw = normalizeActionToken(raw);
        if (!coerced.equals(normalizedRaw)) {
            o.set(field, coerced);
            moves.add(field + ": coerced from " + raw.trim() + " to " + coerced);
        }
    }

    private static boolean hasMisplacedAction(JSONObject o, String field) {
        JSONObject slots = safeGetJSONObject(o, "semanticSlots");
        if (slots != null && slots.containsKey(field)) {
            return true;
        }
        JSONObject time = safeGetJSONObject(o, "time");
        if (time != null && time.containsKey(field)) {
            return true;
        }
        JSONObject metric = safeGetJSONObject(o, "metric");
        return metric != null && metric.containsKey(field);
    }

    /**
     * v2 协议层校验：JSON 已可解析为 {@link AiQuerySemanticParseResult} 时，检查顶层 schema/枚举/必填位置；
     * 不做业务语义或合同推断。
     */
    public static List<String> collectProtocolErrors(String raw, AiQuerySemanticParseResult parsed) {
        List<String> errors = new ArrayList<>();
        if (parsed == null || parsed.isParseMissing()) {
            return errors;
        }
        String trimmed = prepareTrimmedJson(raw);
        JSONObject o = extractJsonObject(trimmed);
        if (o == null || o.isEmpty()) {
            return errors;
        }
        collectProtocolErrorsOnObject(o, errors);
        return errors;
    }

    static void collectProtocolErrorsOnObject(JSONObject o, List<String> errors) {
        if (o == null || errors == null) {
            return;
        }
        normalizeOrchestrationDecisionCandidateShape(o, new ArrayList<>());
        if (!o.containsKey("confidence")) {
            if (hasMisplacedConfidence(o)) {
                errors.add(
                        "confidence: must be top-level (same level as semanticSlots); "
                                + "do not put confidence inside semanticSlots, metric, or orchestrationDecisionCandidate");
            } else {
                errors.add("confidence: missing top-level field (required number 0.0-1.0)");
            }
        } else {
            Double c = parseDouble(o.get("confidence"));
            if (c == null) {
                errors.add("confidence: must be number 0.0-1.0");
            } else if (c < 0.0 || c > 1.0) {
                errors.add("confidence: out of range, must be 0.0-1.0");
            }
        }
        collectActionProtocolError(errors, "intentAction", o.getStr("intentAction"), hasMisplacedAction(o, "intentAction"));
        collectActionProtocolError(errors, "timeAction", o.getStr("timeAction"), hasMisplacedAction(o, "timeAction"));
        collectActionProtocolError(errors, "scopeAction", o.getStr("scopeAction"), hasMisplacedAction(o, "scopeAction"));
        collectActionProtocolError(errors, "metricAction", o.getStr("metricAction"), hasMisplacedAction(o, "metricAction"));
    }

    /**
     * 协议纠错 user message：仅修正 schema/枚举/字段位置；不改变业务语义。
     */
    public static String buildProtocolRepairUserMessage(String originalRaw, List<String> protocolErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("protocol_repair_request\n");
        sb.append(
                "Your JSON output had protocol/schema errors. Fix ONLY the listed protocol errors. "
                        + "Return one line of corrected JSON with the SAME business semantics.\n\n");
        sb.append("Protocol errors:\n");
        for (String err : protocolErrors) {
            sb.append("- ").append(err).append("\n");
        }
        sb.append("\nRules:\n");
        sb.append("- confidence MUST be a top-level field (same level as semanticSlots), number 0.0-1.0\n");
        sb.append(
                "- Do NOT put confidence inside semanticSlots, metric, or orchestrationDecisionCandidate\n");
        sb.append(
                "- semanticSlots.metric MUST be a simple uppercase token string (e.g. REVENUE_AMOUNT); "
                        + "do NOT output metric as JSON object; do NOT use metricName instead of metric token\n");
        sb.append(
                "- intentAction / timeAction / scopeAction / metricAction must be top-level NEW | INHERIT_PREVIOUS | OVERRIDE "
                        + "(not inside semanticSlots, time, or metric)\n");
        sb.append(
                "- Do NOT change selectedContractId, semanticSlots business fields, domain, or intent "
                        + "unless those fields are listed above as invalid enum values\n");
        sb.append("\nOriginal output:\n");
        sb.append(originalRaw);
        return sb.toString();
    }

    public static String buildProtocolRepairReasonCode(List<String> protocolErrors) {
        if (protocolErrors == null || protocolErrors.isEmpty()) {
            return "unknown_protocol_repair";
        }
        List<String> codes = new ArrayList<>();
        for (String err : protocolErrors) {
            if (err.startsWith("confidence:")) {
                if (err.contains("top-level")) {
                    codes.add("misplaced_confidence");
                } else if (err.contains("missing")) {
                    codes.add("missing_confidence");
                } else if (err.contains("out of range")) {
                    codes.add("confidence_out_of_range");
                } else {
                    codes.add("invalid_confidence");
                }
            } else if (err.startsWith("intentAction:")) {
                codes.add("invalid_intent_action");
            } else if (err.startsWith("timeAction:")) {
                codes.add("invalid_time_action");
            } else if (err.startsWith("scopeAction:")) {
                codes.add("invalid_scope_action");
            } else if (err.startsWith("metricAction:")) {
                codes.add("invalid_metric_action");
            }
        }
        if (codes.isEmpty()) {
            return "invalid_protocol";
        }
        return String.join(";", codes);
    }

    private static void collectActionProtocolError(
            List<String> errors, String field, String value, boolean misplaced) {
        if (!StringUtils.hasText(value)) {
            if (misplaced) {
                errors.add(
                        field
                                + ": must be top-level (same level as semanticSlots); "
                                + "do not put "
                                + field
                                + " inside semanticSlots or time");
            } else {
                errors.add(field + ": missing (required NEW | INHERIT_PREVIOUS | OVERRIDE)");
            }
            return;
        }
        if (!isValidActionToken(value)) {
            errors.add(
                    field
                            + ": got \""
                            + value.trim()
                            + "\", allowed: NEW, INHERIT_PREVIOUS, OVERRIDE");
        }
    }

    static boolean isValidActionToken(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return VALID_ACTION_TOKENS.contains(normalizeActionToken(value));
    }

    private static String normalizeActionToken(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean hasMisplacedConfidence(JSONObject o) {
        JSONObject slots = safeGetJSONObject(o, "semanticSlots");
        if (slots != null && slots.containsKey("confidence")) {
            return true;
        }
        JSONObject metric = safeGetJSONObject(o, "metric");
        if (metric != null && metric.containsKey("confidence")) {
            return true;
        }
        JSONObject orch = safeGetJSONObject(o, "orchestrationDecisionCandidate");
        return orch != null && orch.containsKey("confidence");
    }

    private static String prepareTrimmedJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        return trimmed;
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
        JSONObject tjo = safeGetJSONObject(o, "time");
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
        JSONObject sjo = safeGetJSONObject(o, "requestedScope");
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
        JSONObject mjo = safeGetJSONObject(o, "metric");
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
        JSONObject orchJo = safeGetJSONObject(o, "orchestrationDecisionCandidate");
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
        JSONObject sjo = safeGetJSONObject(o, "semanticSlots");
        if (sjo == null || sjo.isEmpty()) {
            return null;
        }
        stripForbiddenKeysRecursive(sjo);
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .selectedContractId(trimToNull(sjo.getStr("selectedContractId")))
                .queryObject(trimToNull(sjo.getStr("queryObject")))
                .operation(trimToNull(sjo.getStr("operation")))
                .metric(parseSemanticSlotsMetricToken(sjo.get("metric")))
                .sourceFacet(trimToNull(sjo.getStr("sourceFacet")))
                .anchorPolicy(trimToNull(sjo.getStr("anchorPolicy")))
                .detailWanted(trimToNull(sjo.getStr("detailWanted")))
                .structuredIntentDetailWire(trimToNull(sjo.getStr("structuredIntentDetailWire")))
                .answerPlanType(trimToNull(sjo.getStr("answerPlanType")))
                .build();
    }

    private static String parseSemanticSlotsMetricToken(Object metricVal) {
        String fromObject = extractMetricKeyToken(metricVal);
        if (fromObject != null) {
            return fromObject;
        }
        if (metricVal == null) {
            return null;
        }
        if (metricVal instanceof JSONObject) {
            return null;
        }
        return trimToNull(String.valueOf(metricVal));
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
