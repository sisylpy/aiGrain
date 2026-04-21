package com.nongxinle.ai.orchestration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 从主模型原文中解析末尾 {@code ```json} 中的 {@code type=skill_handoff} 块；并可从正文中剥离，避免干扰画像抽取。
 */
public final class SkillHandoffParser {

    private static final Set<String> ALLOWED_TO_SKILLS = Set.of(
            "cost", "revenue", "data_extractor", "dish_sales",
            "dish_cost", "procurement", "profit_pilot"
    );

    private SkillHandoffParser() {
    }

    /**
     * 从后往前查找第一个合法的 skill_handoff（通常模型放在全文最后）。
     */
    public static Optional<SkillHandoffPayload> parseLastSkillHandoff(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Optional.empty();
        }
        int from = raw.length();
        while (true) {
            int fence = raw.lastIndexOf("```json", from);
            if (fence < 0) {
                break;
            }
            int innerStart = fence + 7;
            int close = raw.indexOf("```", innerStart);
            if (close <= innerStart) {
                from = fence - 1;
                continue;
            }
            String inner = raw.substring(innerStart, close).trim();
            Optional<SkillHandoffPayload> parsed = tryParseHandoffObject(inner);
            if (parsed.isPresent()) {
                return parsed;
            }
            from = fence - 1;
            if (from < 0) {
                break;
            }
        }
        return Optional.empty();
    }

    private static Optional<SkillHandoffPayload> tryParseHandoffObject(String inner) {
        if (!inner.startsWith("{")) {
            return Optional.empty();
        }
        try {
            JSONObject o = JSONUtil.parseObj(inner);
            if (!"skill_handoff".equalsIgnoreCase(o.getStr("type"))) {
                return Optional.empty();
            }
            String toSkill = o.getStr("toSkill");
            if (StrUtil.isBlank(toSkill)) {
                return Optional.empty();
            }
            toSkill = toSkill.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_TO_SKILLS.contains(toSkill)) {
                return Optional.empty();
            }
            String reason = o.getStr("reason", "");
            Map<String, Object> carry = new java.util.LinkedHashMap<>();
            JSONObject co = o.getJSONObject("carryOver");
            if (co != null) {
                for (String key : co.keySet()) {
                    carry.put(key, co.get(key));
                }
            }
            return Optional.of(new SkillHandoffPayload(toSkill, reason, carry));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * 移除正文中所有 skill_handoff 代码块，便于 {@code extractUserDataFromReply} 只命中画像 JSON。
     */
    public static String stripAllSkillHandoffFences(String raw) {
        if (StrUtil.isBlank(raw)) {
            return raw;
        }
        String s = raw;
        boolean changed = true;
        while (changed) {
            changed = false;
            int fence = s.lastIndexOf("```json");
            if (fence < 0) {
                break;
            }
            int innerStart = fence + 7;
            int close = s.indexOf("```", innerStart);
            if (close <= innerStart) {
                break;
            }
            String inner = s.substring(innerStart, close).trim();
            if (tryParseHandoffObject(inner).isPresent()) {
                s = (fence > 0 ? s.substring(0, fence) : "").trim()
                        + (close + 3 < s.length() ? s.substring(close + 3) : "");
                s = s.trim();
                changed = true;
            } else {
                break;
            }
        }
        return s;
    }
}
