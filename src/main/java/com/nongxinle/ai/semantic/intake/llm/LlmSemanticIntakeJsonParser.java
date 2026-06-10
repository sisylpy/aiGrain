package com.nongxinle.ai.semantic.intake.llm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nongxinle.ai.semantic.intake.SemanticIntakeDishIngredientCoverDaysSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeFollowUpIntent;
import com.nongxinle.ai.semantic.intake.SemanticIntakeFollowUpKind;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsSupportedDishCoverSupport;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskFilterSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeNormalizationType;
import com.nongxinle.ai.semantic.intake.SemanticIntakePrimaryDomain;
import com.nongxinle.ai.semantic.intake.SemanticIntakeQuestionMode;
import com.nongxinle.ai.semantic.intake.SemanticIntakeSubQuestion;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 解析 SemanticIntake LLM 单行 JSON；Java 不做 domain 修正。 */
public final class LlmSemanticIntakeJsonParser {

    private static final Set<String> FORBIDDEN_KEYS =
            Set.of(
                    "queryStoreIds",
                    "queryRealDepartmentIds",
                    "expandedSqlDepartmentIds",
                    "storeToDepartmentIds",
                    "queryDistributerId",
                    "distributerId",
                    "departmentIds",
                    "contractId",
                    "selectedContractId",
                    "semanticSlots",
                    "wire",
                    "answerPlanType",
                    "selectedTools");

    private LlmSemanticIntakeJsonParser() {}

    public static LlmSemanticIntakeParsed parseRaw(String raw) {
        if (StrUtil.isBlank(raw)) {
            return failed("blank_response", digest(raw));
        }
        String trimmed = stripMarkdownFence(raw.trim());
        JSONObject o = extractJsonObject(trimmed);
        if (o == null || o.isEmpty()) {
            return failed("json_extract_or_syntax_failed", digest(trimmed));
        }
        stripForbiddenKeysRecursive(o);
        normalizeSchemaAliases(o);
        return fromJsonObject(o, digest(trimmed));
    }

    private static LlmSemanticIntakeParsed failed(String reason, String digest) {
        return LlmSemanticIntakeParsed.builder()
                .parseFailed(true)
                .parseError(reason)
                .rawDigest(digest)
                .build();
    }

    private static LlmSemanticIntakeParsed fromJsonObject(JSONObject o, String digest) {
        String questionMode = trimToNull(o.getStr("questionMode"));
        String normalizationType = trimToNull(o.getStr("normalizationType"));
        List<SemanticIntakeSubQuestion> subQuestions = parseSubQuestions(o.getJSONArray("subQuestions"));
        List<String> candidateDomains = parseStringList(o.getJSONArray("candidateDomains"));
        String warehouseSemanticsRaw = trimToNull(o.getStr("warehouseInventorySemantics"));
        String reason = trimToNull(o.getStr("reason"));
        String warehouseSemanticsNormalized = null;
        if (SemanticIntakeDishIngredientCoverDaysSupport.rawWarehouseSemanticsDeclaresDishCoverMislabel(
                        warehouseSemanticsRaw)
                && !SemanticIntakeGoodsSupportedDishCoverSupport.reasonDeclaresGoodsSupportedDishCover(
                        reason)
                && !hasCoverDaysEntityFields(o)
                && !SemanticIntakePrimaryDomain.WAREHOUSE.equals(
                        SemanticIntakePrimaryDomain.normalize(trimToNull(o.getStr("primaryDomain"))))) {
            reason = SemanticIntakeDishIngredientCoverDaysSupport.appendDishCoverReasonMarker(reason);
            warehouseSemanticsNormalized = warehouseSemanticsRaw;
        } else if (StringUtils.hasText(warehouseSemanticsRaw)) {
            warehouseSemanticsNormalized =
                    WarehouseInventoryShortageSemanticsSupport.normalizeSemantics(warehouseSemanticsRaw);
        }
        return LlmSemanticIntakeParsed.builder()
                .parseFailed(false)
                .rawDigest(digest)
                .questionMode(questionMode)
                .normalizationType(normalizationType)
                .canonicalUserQuery(trimToNull(o.getStr("canonicalUserQuery")))
                .isFollowUp(o.getBool("isFollowUp", false))
                .usedPreviousContext(o.getBool("usedPreviousContext", false))
                .primaryDomain(trimToNull(o.getStr("primaryDomain")))
                .candidateDomains(candidateDomains)
                .routeType(trimToNull(o.getStr("routeType")))
                .confidence(parseConfidence(o.get("confidence")))
                .needClarification(o.getBool("needClarification", false))
                .clarificationQuestion(trimToNull(o.getStr("clarificationQuestion")))
                .reason(reason)
                .warehouseInventorySemantics(warehouseSemanticsNormalized)
                .expiryRiskFilter(
                        WarehouseNearExpiryRiskFilterSupport.normalizeFilter(
                                trimToNull(o.getStr("expiryRiskFilter"))))
                .coverDaysEntityType(trimToNull(o.getStr("coverDaysEntityType")))
                .coverDaysEntityName(trimToNull(o.getStr("coverDaysEntityName")))
                .followUpIntent(parseFollowUpIntent(o.getJSONObject("followUpIntent")))
                .contextRelation(trimToNull(o.getStr("contextRelation")))
                .subQuestions(subQuestions)
                .build();
    }

    private static SemanticIntakeFollowUpIntent parseFollowUpIntent(JSONObject fi) {
        if (fi == null || fi.isEmpty()) {
            return null;
        }
        SemanticIntakeFollowUpKind kind =
                SemanticIntakeFollowUpKind.normalize(trimToNull(fi.getStr("kind")));
        if (kind == SemanticIntakeFollowUpKind.NONE) {
            return null;
        }
        return SemanticIntakeFollowUpIntent.builder()
                .kind(kind)
                .targetContractId(trimToNull(fi.getStr("targetContractId")))
                .targetStructuredIntentDetailWire(trimToNull(fi.getStr("targetStructuredIntentDetailWire")))
                .anchorPolicy(trimToNull(fi.getStr("anchorPolicy")))
                .build();
    }

    private static boolean hasCoverDaysEntityFields(JSONObject o) {
        return o != null
                && (StringUtils.hasText(trimToNull(o.getStr("coverDaysEntityType")))
                        || StringUtils.hasText(trimToNull(o.getStr("coverDaysEntityName"))));
    }

    /**
     * 工程级 schema 别名归一：仅把 LLM 别名字段映射到标准键，不做业务域关键词推断。
     */
    private static void normalizeSchemaAliases(JSONObject o) {
        normalizePrimaryDomainAlias(o);
        normalizeQuestionModeAlias(o);
        normalizeNeedClarificationAlias(o);

        JSONArray subs = o.getJSONArray("subQuestions");
        if (subs == null || subs.isEmpty()) {
            return;
        }
        for (int i = 0; i < subs.size(); i++) {
            Object item = subs.get(i);
            if (item instanceof JSONObject jo) {
                normalizePrimaryDomainAlias(jo);
                normalizeNeedClarificationAlias(jo);
            }
        }
    }

    private static void normalizePrimaryDomainAlias(JSONObject o) {
        if (StringUtils.hasText(trimToNull(o.getStr("primaryDomain")))) {
            return;
        }
        for (String alias : List.of("businessDomain", "domain")) {
            String value = trimToNull(o.getStr(alias));
            if (value == null) {
                continue;
            }
            String normalized = SemanticIntakePrimaryDomain.normalize(value);
            if (SemanticIntakePrimaryDomain.isKnown(normalized)) {
                o.set("primaryDomain", normalized);
                return;
            }
        }
    }

    private static void normalizeQuestionModeAlias(JSONObject o) {
        if (StringUtils.hasText(trimToNull(o.getStr("questionMode")))) {
            return;
        }
        String inferred = inferQuestionModeFromAliases(o);
        if (inferred != null) {
            o.set("questionMode", inferred);
        }
    }

    private static String inferQuestionModeFromAliases(JSONObject o) {
        for (String key : List.of("isMultiQuestion", "isMultiQuery", "multiQuestion", "multiQuery")) {
            if (!o.containsKey(key)) {
                continue;
            }
            Object value = o.get(key);
            if (value instanceof Boolean boolValue) {
                return boolValue
                        ? SemanticIntakeQuestionMode.MULTI_QUESTION.name()
                        : SemanticIntakeQuestionMode.SINGLE_QUESTION.name();
            }
            if (value instanceof JSONArray arrayValue) {
                if (arrayValue.size() >= 2) {
                    return SemanticIntakeQuestionMode.MULTI_QUESTION.name();
                }
                if (arrayValue.size() == 1) {
                    return SemanticIntakeQuestionMode.SINGLE_QUESTION.name();
                }
                continue;
            }
            String text = trimToNull(String.valueOf(value));
            if (text == null) {
                continue;
            }
            if (isValidQuestionMode(text)) {
                return text.trim().toUpperCase();
            }
            if ("true".equalsIgnoreCase(text)) {
                return SemanticIntakeQuestionMode.MULTI_QUESTION.name();
            }
            if ("false".equalsIgnoreCase(text)) {
                return SemanticIntakeQuestionMode.SINGLE_QUESTION.name();
            }
        }
        return null;
    }

    private static void normalizeNeedClarificationAlias(JSONObject o) {
        if (o.containsKey("needClarification")) {
            return;
        }
        if (o.containsKey("clarificationNeeded")) {
            o.set("needClarification", o.getBool("clarificationNeeded", false));
        }
    }

    private static List<SemanticIntakeSubQuestion> parseSubQuestions(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return null;
        }
        List<SemanticIntakeSubQuestion> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (!(item instanceof JSONObject jo)) {
                continue;
            }
            List<String> candidates = parseStringList(jo.getJSONArray("candidateDomains"));
            out.add(
                    SemanticIntakeSubQuestion.builder()
                            .index(jo.getInt("index", i + 1))
                            .canonicalQuestion(
                                    firstNonBlank(
                                            trimToNull(jo.getStr("canonicalQuestion")),
                                            trimToNull(jo.getStr("canonicalUserQuery"))))
                            .primaryDomain(
                                    firstNonBlank(
                                            trimToNull(jo.getStr("primaryDomain")),
                                            knownPrimaryDomainAlias(jo, "businessDomain"),
                                            knownPrimaryDomainAlias(jo, "domain")))
                            .candidateDomains(candidates)
                            .routeType(trimToNull(jo.getStr("routeType")))
                            .confidence(parseConfidence(jo.get("confidence")))
                            .needClarification(jo.getBool("needClarification", false))
                            .clarificationQuestion(trimToNull(jo.getStr("clarificationQuestion")))
                            .reason(trimToNull(jo.getStr("reason")))
                            .build());
        }
        return out.isEmpty() ? null : out;
    }

    private static List<String> parseStringList(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            String s = trimToNull(String.valueOf(arr.get(i)));
            if (s != null) {
                out.add(s);
            }
        }
        return out.isEmpty() ? null : new ArrayList<>(out);
    }

    private static Double parseConfidence(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    static boolean isValidQuestionMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return false;
        }
        try {
            SemanticIntakeQuestionMode.valueOf(mode.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    static boolean isValidNormalizationType(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        try {
            SemanticIntakeNormalizationType.valueOf(type.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    static boolean isValidRouteType(String routeType) {
        if (!StringUtils.hasText(routeType)) {
            return false;
        }
        return Set.of("EXPLICIT", "INHERITED", "AMBIGUOUS", "UNKNOWN", "MULTI_DOMAIN")
                .contains(routeType.trim().toUpperCase());
    }

    static boolean isValidPrimaryDomain(String domain) {
        return SemanticIntakePrimaryDomain.isKnown(domain);
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
            } else if (v instanceof JSONArray ja) {
                for (int i = 0; i < ja.size(); i++) {
                    Object el = ja.get(i);
                    if (el instanceof JSONObject jo) {
                        stripForbiddenKeysRecursive(jo);
                    }
                }
            }
        }
    }

    private static JSONObject extractJsonObject(String trimmed) {
        if (!trimmed.contains("{")) {
            return null;
        }
        try {
            if (trimmed.startsWith("{")) {
                return JSONUtil.parseObj(trimmed);
            }
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return JSONUtil.parseObj(trimmed.substring(start, end + 1));
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String stripMarkdownFence(String trimmed) {
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNl = trimmed.indexOf('\n');
        int fence = trimmed.lastIndexOf("```");
        if (firstNl > 0 && fence > firstNl) {
            return trimmed.substring(firstNl + 1, fence).trim();
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

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String knownPrimaryDomainAlias(JSONObject o, String aliasKey) {
        String value = trimToNull(o.getStr(aliasKey));
        if (value == null) {
            return null;
        }
        String normalized = SemanticIntakePrimaryDomain.normalize(value);
        return SemanticIntakePrimaryDomain.isKnown(normalized) ? normalized : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
