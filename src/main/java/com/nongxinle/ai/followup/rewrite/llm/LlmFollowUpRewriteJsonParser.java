package com.nongxinle.ai.followup.rewrite.llm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析 Follow-up Rewrite LLM 单行 JSON。
 */
public final class LlmFollowUpRewriteJsonParser {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "queryStoreIds",
            "queryRealDepartmentIds",
            "expandedSqlDepartmentIds",
            "storeToDepartmentIds",
            "queryDistributerId",
            "distributerId",
            "departmentIds");

    private LlmFollowUpRewriteJsonParser() {}

    public static LlmFollowUpRewriteParsed parseRaw(String raw) {
        if (StrUtil.isBlank(raw)) {
            return failed("blank_response", digest(raw));
        }
        String trimmed = stripMarkdownFence(raw.trim());
        JSONObject o = extractJsonObject(trimmed);
        if (o == null || o.isEmpty()) {
            return failed("json_extract_or_syntax_failed", digest(trimmed));
        }
        stripForbiddenKeysRecursive(o);
        return fromJsonObject(o, digest(trimmed));
    }

    public static String describeParseFailureReason(String raw) {
        LlmFollowUpRewriteParsed p = parseRaw(raw);
        return p.isParseFailed() ? p.getParseError() : null;
    }

    private static LlmFollowUpRewriteParsed failed(String reason, String digest) {
        return LlmFollowUpRewriteParsed.builder()
                .parseFailed(true)
                .parseError(reason)
                .rawDigest(digest)
                .build();
    }

    private static LlmFollowUpRewriteParsed fromJsonObject(JSONObject o, String digest) {
        List<LlmFollowUpRewriteParsed.UsedAnchor> anchors = parseUsedAnchors(o.getJSONArray("usedAnchors"));
        Map<String, Object> debug = parseDebug(o.getJSONObject("debug"));
        return LlmFollowUpRewriteParsed.builder()
                .parseFailed(false)
                .rawDigest(digest)
                .isFollowUp(o.getBool("isFollowUp", false))
                .canRewrite(o.getBool("canRewrite", false))
                .completedUserQuery(trimToNull(o.getStr("completedUserQuery")))
                .needClarification(o.getBool("needClarification", false))
                .clarificationQuestion(trimToNull(o.getStr("clarificationQuestion")))
                .rewriteReason(trimToNull(o.getStr("rewriteReason")))
                .usedAnchors(anchors)
                .debug(debug)
                .build();
    }

    private static List<LlmFollowUpRewriteParsed.UsedAnchor> parseUsedAnchors(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return null;
        }
        List<LlmFollowUpRewriteParsed.UsedAnchor> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (!(item instanceof JSONObject jo)) {
                continue;
            }
            String type = trimToNull(jo.getStr("anchorType"));
            String name = trimToNull(jo.getStr("anchorName"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            out.add(
                    LlmFollowUpRewriteParsed.UsedAnchor.builder()
                            .anchorType(type)
                            .anchorName(name)
                            .build());
        }
        return out.isEmpty() ? null : out;
    }

    private static Map<String, Object> parseDebug(JSONObject debugJo) {
        if (debugJo == null || debugJo.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        String reason = trimToNull(debugJo.getStr("reason"));
        if (reason != null) {
            m.put("reason", reason);
        }
        if (debugJo.containsKey("confidence")) {
            m.put("confidence", debugJo.get("confidence"));
        }
        if (debugJo.containsKey("inheritedTime")) {
            m.put("inheritedTime", debugJo.getBool("inheritedTime"));
        }
        if (debugJo.containsKey("inheritedScope")) {
            m.put("inheritedScope", debugJo.getBool("inheritedScope"));
        }
        return m.isEmpty() ? null : m;
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
}
