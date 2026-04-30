package com.nongxinle.ai.orchestration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 解析「添加商品」第一步：在库内一级 / 二级目录表中选定分支（仅 id，不含 SKU）。
 */
public final class GoodsCatalogL1L2PickParser {

    public enum BranchDecision {
        SINGLE,
        AMBIGUOUS,
        NONE
    }

    public record ParsedBranch(
            BranchDecision decision,
            Integer greatGrandNxGoodsId,
            Integer grandNxGoodsId,
            List<Integer> ambiguousGrandNxGoodsIds,
            double confidence,
            String reason,
            String userFacingSummary,
            boolean structuredOk
    ) {
        public static ParsedBranch invalid() {
            return new ParsedBranch(BranchDecision.NONE, null, null, List.of(), 0, "", "", false);
        }
    }

    private GoodsCatalogL1L2PickParser() {
    }

    public static ParsedBranch parse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return ParsedBranch.invalid();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("抱歉") || trimmed.contains("AI 服务")) {
            return ParsedBranch.invalid();
        }
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && fence > firstNl) {
                trimmed = trimmed.substring(firstNl + 1, fence).trim();
            }
        }
        if (!trimmed.startsWith("{")) {
            return ParsedBranch.invalid();
        }
        try {
            JSONObject o = JSONUtil.parseObj(trimmed);
            String decisionStr = o.getStr("decision", "").trim().toUpperCase(Locale.ROOT);
            BranchDecision decision = switch (decisionStr) {
                case "SINGLE" -> BranchDecision.SINGLE;
                case "AMBIGUOUS" -> BranchDecision.AMBIGUOUS;
                case "NONE" -> BranchDecision.NONE;
                default -> BranchDecision.NONE;
            };
            double confidence = o.getDouble("confidence", 0.0);
            String reason = o.getStr("reason", "");
            String userFacingSummary = o.getStr("userFacingSummary", "");

            Integer gg = o.getInt("greatGrandNxGoodsId");
            if (gg != null && gg <= 0) {
                gg = null;
            }
            Integer gr = o.getInt("grandNxGoodsId");
            if (gr != null && gr <= 0) {
                gr = null;
            }

            List<Integer> ambGrand = new ArrayList<>();
            JSONArray arr = o.getJSONArray("ambiguousGrandNxGoodsIds");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    Integer id = arr.getInt(i);
                    if (id != null && id > 0) {
                        ambGrand.add(id);
                    }
                }
            }
            ambGrand = new ArrayList<>(new LinkedHashSet<>(ambGrand));

            if (decision == BranchDecision.NONE) {
                return new ParsedBranch(BranchDecision.NONE, gg, gr, List.of(), confidence, reason, userFacingSummary, true);
            }
            if (decision == BranchDecision.SINGLE) {
                if (gg == null || gr == null) {
                    return ParsedBranch.invalid();
                }
                return new ParsedBranch(BranchDecision.SINGLE, gg, gr, List.of(), confidence, reason, userFacingSummary, true);
            }
            if (decision == BranchDecision.AMBIGUOUS) {
                if (gg == null || ambGrand.size() < 2) {
                    return ParsedBranch.invalid();
                }
                return new ParsedBranch(BranchDecision.AMBIGUOUS, gg, null, ambGrand, confidence, reason, userFacingSummary, true);
            }
            return ParsedBranch.invalid();
        } catch (Exception e) {
            return ParsedBranch.invalid();
        }
    }
}
