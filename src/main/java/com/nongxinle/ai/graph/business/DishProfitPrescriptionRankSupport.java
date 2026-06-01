package com.nongxinle.ai.graph.business;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 菜单内销量/毛利 rank：只读 dish_profit_analysis.dishRows 确定性排序。 */
public final class DishProfitPrescriptionRankSupport {

    public record RankResult(Integer rank, Integer rankOf, boolean truncated) {}

    private DishProfitPrescriptionRankSupport() {}

    public static RankResult salesRank(List<Map<String, Object>> dishRows, Integer targetFoodId, int fullCount) {
        return rankBy(
                dishRows,
                targetFoodId,
                fullCount,
                Comparator.comparing(
                                (Map<String, Object> r) ->
                                        coerceDecimal(r.get("soldPortionsTotal")),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(r -> foodIdKey(r), Comparator.nullsLast(String::compareTo)));
    }

    public static RankResult marginRank(List<Map<String, Object>> dishRows, Integer targetFoodId, int fullCount) {
        return rankBy(
                dishRows,
                targetFoodId,
                fullCount,
                Comparator.comparing(
                                (Map<String, Object> r) ->
                                        coerceDecimal(r.get("blendedGrossMarginRateOnListPrice")),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(r -> foodIdKey(r), Comparator.nullsLast(String::compareTo)));
    }

    private static RankResult rankBy(
            List<Map<String, Object>> dishRows,
            Integer targetFoodId,
            int fullCount,
            Comparator<Map<String, Object>> cmp) {
        if (targetFoodId == null || dishRows == null || dishRows.isEmpty()) {
            return new RankResult(null, fullCount > 0 ? fullCount : null, fullCount > dishRows.size());
        }
        List<Map<String, Object>> sorted = new ArrayList<>(dishRows);
        sorted.sort(cmp);
        int rankOf = fullCount > 0 ? fullCount : sorted.size();
        boolean truncated = fullCount > sorted.size();
        for (int i = 0; i < sorted.size(); i++) {
            Integer fid = parseFoodId(sorted.get(i).get("foodId"));
            if (targetFoodId.equals(fid)) {
                return new RankResult(i + 1, rankOf, truncated);
            }
        }
        return new RankResult(null, rankOf, truncated);
    }

    static Integer parseFoodId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String foodIdKey(Map<String, Object> row) {
        Integer id = parseFoodId(row.get("foodId"));
        return id == null ? null : id.toString();
    }

    private static BigDecimal coerceDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, Object> buildMenuContext(
            RankResult sales, RankResult margin, boolean truncatedGap) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (sales != null && sales.rank() != null) {
            ctx.put("salesRank", sales.rank());
        }
        if (sales != null && sales.rankOf() != null) {
            ctx.put("salesRankOf", sales.rankOf());
        }
        if (margin != null && margin.rank() != null) {
            ctx.put("marginRank", margin.rank());
        }
        if (margin != null && margin.rankOf() != null) {
            ctx.put("marginRankOf", margin.rankOf());
        }
        ctx.put("rankBasis", "dish_profit_analysis.dishRows");
        ctx.put("rankScopeNote", "本店本周期菜单内排名");
        if (truncatedGap || (sales != null && sales.truncated()) || (margin != null && margin.truncated())) {
            ctx.put("rankTruncated", true);
        }
        return ctx;
    }
}
