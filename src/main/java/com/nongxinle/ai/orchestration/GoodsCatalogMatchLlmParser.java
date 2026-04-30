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
 * 解析「添加商品」**第二阶段** SKU 选择的模型 JSON 输出（候选表由服务端在一级/二级分支下生成）。
 * <p>{@code allowedNxGoodsIds == null}：不做 id 白名单过滤。
 * <p>{@code allowedNxGoodsIds} 非 null：仅保留集合内 id（正常路径：与 SKU Markdown 表一致）。
 */
public final class GoodsCatalogMatchLlmParser {

    public enum Decision {
        SINGLE,
        AMBIGUOUS,
        NONE
    }

    public record ParsedMatch(
            Decision decision,
            Integer pickedNxGoodsId,
            List<Integer> ambiguousNxGoodsIds,
            double confidence,
            String reason,
            String userFacingSummary,
            boolean structuredOk
    ) {
        public static ParsedMatch invalid() {
            return new ParsedMatch(Decision.NONE, null, List.of(), 0, "", "", false);
        }
    }

    private GoodsCatalogMatchLlmParser() {
    }

    /**
     * @param allowedNxGoodsIds 为 {@code null} 时不校验 id 是否在集合内；非 null 时按白名单过滤
     */
    public static ParsedMatch parse(String raw, Set<Integer> allowedNxGoodsIds) {
        if (StrUtil.isBlank(raw)) {
            return ParsedMatch.invalid();
        }
        boolean enforceWhitelist = allowedNxGoodsIds != null;
        if (enforceWhitelist && allowedNxGoodsIds.isEmpty()) {
            return ParsedMatch.invalid();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("抱歉") || trimmed.contains("AI 服务")) {
            return ParsedMatch.invalid();
        }
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        if (!trimmed.startsWith("{")) {
            return ParsedMatch.invalid();
        }
        try {
            JSONObject o = JSONUtil.parseObj(trimmed);
            String decisionStr = o.getStr("decision", "").trim().toUpperCase(Locale.ROOT);
            Decision decision = switch (decisionStr) {
                case "SINGLE" -> Decision.SINGLE;
                case "AMBIGUOUS" -> Decision.AMBIGUOUS;
                case "NONE" -> Decision.NONE;
                default -> Decision.NONE;
            };
            double confidence = o.getDouble("confidence", 0.0);
            String reason = o.getStr("reason", "");
            String userFacingSummary = o.getStr("userFacingSummary", "");

            Integer picked = o.getInt("pickedNxGoodsId");
            if (picked != null) {
                if (picked <= 0) {
                    picked = null;
                } else if (enforceWhitelist && !allowedNxGoodsIds.contains(picked)) {
                    picked = null;
                }
            }

            List<Integer> ambiguous = new ArrayList<>();
            JSONArray arr = o.getJSONArray("ambiguousNxGoodsIds");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    Integer id = arr.getInt(i);
                    if (id == null || id <= 0) {
                        continue;
                    }
                    if (enforceWhitelist && !allowedNxGoodsIds.contains(id)) {
                        continue;
                    }
                    ambiguous.add(id);
                }
            }
            LinkedHashSet<Integer> dedupe = new LinkedHashSet<>(ambiguous);
            ambiguous = new ArrayList<>(dedupe);

            if (decision == Decision.SINGLE) {
                if (picked == null) {
                    return ParsedMatch.invalid();
                }
                return new ParsedMatch(Decision.SINGLE, picked, List.of(), confidence, reason, userFacingSummary, true);
            }
            if (decision == Decision.AMBIGUOUS) {
                return new ParsedMatch(Decision.AMBIGUOUS, null, ambiguous, confidence, reason, userFacingSummary, true);
            }
            return new ParsedMatch(Decision.NONE, null, List.of(), confidence, reason, userFacingSummary, true);
        } catch (Exception e) {
            return ParsedMatch.invalid();
        }
    }
}
