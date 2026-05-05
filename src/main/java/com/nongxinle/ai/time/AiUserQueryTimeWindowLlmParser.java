package com.nongxinle.ai.time;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.Locale;

/**
 * 解析 DeepSeek 返回的时间口径 JSON。
 */
public final class AiUserQueryTimeWindowLlmParser {

    private AiUserQueryTimeWindowLlmParser() {
    }

    public record LlmTimeOutcome(AiUserQueryTimeBucket bucket, String reason, Integer calendarYear,
                                Integer calendarMonth, String statStartDateRaw, String statEndDateRaw) {

        public static LlmTimeOutcome unknown(String reason) {
            return new LlmTimeOutcome(AiUserQueryTimeBucket.UNKNOWN, reason, null, null, null, null);
        }
    }

    public static LlmTimeOutcome parseRaw(String raw) {
        if (StrUtil.isBlank(raw)) {
            return LlmTimeOutcome.unknown("empty");
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
        if (o == null) {
            return LlmTimeOutcome.unknown("no_json");
        }
        LlmTimeOutcome parsed = fromJsonObject(o);
        return parsed != null ? parsed : LlmTimeOutcome.unknown("no_json");
    }

    /**
     * 解析 skill-selection 顶层字段 {@code statTime} 等与独立时间接口相同的 JSON 对象；缺字段或空对象返回 null。
     */
    public static LlmTimeOutcome fromJsonObject(JSONObject o) {
        if (o == null || o.isEmpty()) {
            return null;
        }
        AiUserQueryTimeBucket bucket = parseBucket(o.getStr("bucket"));
        String reason = StrUtil.trimToEmpty(o.getStr("reason"));
        if (reason.length() > 200) {
            reason = reason.substring(0, 200) + "…";
        }
        Integer cy = parseNullableInt(o.get("calendar_year"));
        Integer cm = parseNullableInt(o.get("calendar_month"));
        String rawStart = pickStatDateRaw(o, "stat_start_date", "query_start_date", "start_date");
        String rawEnd = pickStatDateRaw(o, "stat_end_date", "query_end_date", "end_date");
        return new LlmTimeOutcome(bucket, reason, cy, cm, rawStart, rawEnd);
    }

    private static String pickStatDateRaw(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.getStr(k);
            if (StrUtil.isNotBlank(v) && !"null".equalsIgnoreCase(v.trim())) {
                return v.trim();
            }
        }
        return null;
    }

    /** stat_start_date 为 OPENING_ANCHOR 等且画像无建档锚点时应反问。 */
    public static boolean isExplicitOpeningAnchorStart(LlmTimeOutcome o) {
        if (o == null || StrUtil.isBlank(o.statStartDateRaw())) {
            return false;
        }
        String u = o.statStartDateRaw().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "OPENING_ANCHOR".equals(u) || "PROFILE_ANCHOR".equals(u) || "OPENING".equals(u);
    }

    private static Integer parseNullableInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = StrUtil.trimToEmpty(String.valueOf(v));
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
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

    private static AiUserQueryTimeBucket parseBucket(String raw) {
        if (StrUtil.isBlank(raw)) {
            return AiUserQueryTimeBucket.UNKNOWN;
        }
        String k = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return AiUserQueryTimeBucket.valueOf(k);
        } catch (IllegalArgumentException ignored) {
            return AiUserQueryTimeBucket.UNKNOWN;
        }
    }
}
