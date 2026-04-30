package com.nongxinle.ai.orchestration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 解析 DeepSeek「近义检索词」短 JSON：<code>{ "aliases": ["词1","词2"], "userFacingSummary": "..." }</code>
 */
public final class GoodsNameNearAliasParser {

    public record ParsedAliases(List<String> aliases, String userFacingSummary, boolean structuredOk) {
        private static ParsedAliases empty() {
            return new ParsedAliases(List.of(), "", false);
        }
    }

    private GoodsNameNearAliasParser() {
    }

    /** 至多保留 max 条去重、非空的检索词（用于库 LIKE）。 */
    public static ParsedAliases parse(String raw, int max) {
        if (StrUtil.isBlank(raw) || max <= 0) {
            return ParsedAliases.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("抱歉") || trimmed.contains("AI 服务")) {
            return ParsedAliases.empty();
        }
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        if (!trimmed.startsWith("{")) {
            return ParsedAliases.empty();
        }
        try {
            JSONObject o = JSONUtil.parseObj(trimmed);
            JSONArray arr = o.getJSONArray("aliases");
            String summary = o.getStr("userFacingSummary", "");
            List<String> out = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            if (arr != null) {
                for (int i = 0; i < arr.size() && out.size() < max; i++) {
                    String s = StrUtil.trim(arr.getStr(i));
                    if (StrUtil.isBlank(s)) {
                        continue;
                    }
                    String key = s.toLowerCase(Locale.ROOT);
                    if (seen.add(key)) {
                        out.add(s);
                    }
                }
            }
            return new ParsedAliases(out, summary, !out.isEmpty());
        } catch (Exception ignored) {
            return ParsedAliases.empty();
        }
    }
}
