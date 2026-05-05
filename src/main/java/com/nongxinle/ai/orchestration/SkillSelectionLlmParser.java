package com.nongxinle.ai.orchestration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nongxinle.ai.time.AiUserQueryTimeWindowLlmParser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析第一步 DeepSeek 返回的技能选择 JSON（与规则兜底解耦）。
 */
public final class SkillSelectionLlmParser {

    private static final Pattern SKILL_FILENAME_PATTERN = Pattern.compile("(ai-skill-[\\w-]+\\.md)", Pattern.CASE_INSENSITIVE);

    private SkillSelectionLlmParser() {
    }

    public static SkillSelectionResult parseRaw(String raw, String userMessage) {
        if (raw == null || raw.trim().isEmpty()) {
            return emptyParse(userMessage, false);
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        if (trimmed.toLowerCase(Locale.ROOT).equals("none")) {
            return emptyParse(userMessage, true);
        }
        if (trimmed.startsWith("{")) {
            try {
                JSONObject o = JSONUtil.parseObj(trimmed);
                boolean broadQuestion;
                if (!o.containsKey("broadQuestion") || o.get("broadQuestion") == null) {
                    broadQuestion = SkillRouteFallback.inferBroadQuestionFallback(userMessage);
                } else {
                    broadQuestion = Boolean.TRUE.equals(o.getBool("broadQuestion"));
                }
                String facet = o.getStr("costFacet");
                if (facet != null) {
                    facet = facet.trim();
                    if (facet.isEmpty() || "null".equalsIgnoreCase(facet)) {
                        facet = null;
                    }
                }
                Double confidence = null;
                if (o.containsKey("confidence") && o.get("confidence") != null) {
                    try {
                        confidence = o.getDouble("confidence");
                    } catch (Exception ignored) {
                        // keep null
                    }
                }
                AiUserQueryTimeWindowLlmParser.LlmTimeOutcome statTime =
                        AiUserQueryTimeWindowLlmParser.fromJsonObject(o.getJSONObject("statTime"));
                LinkedHashSet<String> names = new LinkedHashSet<>();
                JSONArray arr = o.getJSONArray("skills");
                if (arr != null && arr.size() > 0) {
                    for (int i = 0; i < arr.size(); i++) {
                        String s = arr.getStr(i);
                        if (StrUtil.isNotEmpty(s)) {
                            addSkillIfValid(names, s);
                        }
                    }
                } else {
                    String s = o.getStr("skills");
                    if (StrUtil.isNotEmpty(s) && !"[]".equals(s.trim())) {
                        for (String part : s.split("[,，]")) {
                            if (StrUtil.isNotEmpty(part.trim())) {
                                addSkillIfValid(names, part.trim());
                            }
                        }
                    }
                }
                String skillsCsv = names.isEmpty() ? "none" : String.join(",", names);
                return new SkillSelectionResult(skillsCsv, facet, broadQuestion, confidence, true, ChatRouteSource.LLM,
                        List.of(), statTime);
            } catch (Exception e) {
                return fallbackFromText(trimmed, userMessage);
            }
        }
        return fallbackFromText(trimmed, userMessage);
    }

    private static SkillSelectionResult emptyParse(String userMessage, boolean structuredOk) {
        boolean broad = SkillRouteFallback.inferBroadQuestionFallback(userMessage);
        return new SkillSelectionResult("none", null, broad, null, structuredOk, ChatRouteSource.LLM, List.of(), null);
    }

    private static SkillSelectionResult fallbackFromText(String trimmed, String userMessage) {
        Matcher m = SKILL_FILENAME_PATTERN.matcher(trimmed);
        LinkedHashSet<String> found = new LinkedHashSet<>();
        while (m.find()) {
            String fn = m.group(1).toLowerCase(Locale.ROOT);
            if (isValidSkillFilename(fn)) {
                found.add(fn);
            }
        }
        if (!found.isEmpty()) {
            boolean broad = SkillRouteFallback.inferBroadQuestionFallback(userMessage);
            return new SkillSelectionResult(String.join(",", found), null, broad, null, false, ChatRouteSource.LLM,
                    List.of(), null);
        }
        boolean broad = SkillRouteFallback.inferBroadQuestionFallback(userMessage);
        return new SkillSelectionResult(trimmed, null, broad, null, false, ChatRouteSource.LLM, List.of(), null);
    }

    private static void addSkillIfValid(LinkedHashSet<String> names, String raw) {
        String n = normalizeSkillFilename(raw);
        if (isValidSkillFilename(n)) {
            names.add(n);
        }
    }

    private static String normalizeSkillFilename(String name) {
        String n = name.trim();
        if (!n.toLowerCase(Locale.ROOT).endsWith(".md")) {
            n = n + ".md";
        }
        return n.toLowerCase(Locale.ROOT);
    }

    /**
     * 拒绝把 "[]"、乱码等当成技能文件名（避免出现 {@code [].md}）。
     */
    private static boolean isValidSkillFilename(String normalized) {
        return normalized != null && SKILL_FILENAME_PATTERN.matcher(normalized).matches();
    }
}
