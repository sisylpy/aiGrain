package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan.DishProfitPrescriptionRecommendedAction;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 单菜利润处方确定性宣读：只读 {@link DishProfitPrescriptionAnswerPlan}，不算术、不暴露 knownGaps 英文 code。
 */
@Component
public final class DishProfitPrescriptionDeterministicRenderer {

    public String render(DishProfitPrescriptionAnswerPlan plan) {
        if (plan == null) {
            return "当前未能读取单菜利润处方计划。";
        }
        StringBuilder sb = new StringBuilder();

        appendSectionTitle(sb, "结论");
        appendConclusion(sb, plan);

        appendSectionTitle(sb, "定价与毛利");
        appendPricingAndMargin(sb, plan);

        appendSectionTitle(sb, "成本结构");
        appendIngredientHighlights(sb, plan);

        appendSectionTitle(sb, "建议动作");
        appendActions(sb, plan);

        appendCapabilityLimitsNotice(sb, plan);
        return sb.toString().trim();
    }

    private static void appendSectionTitle(StringBuilder sb, String title) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(title).append('\n');
    }

    private static void appendConclusion(StringBuilder sb, DishProfitPrescriptionAnswerPlan plan) {
        if (StringUtils.hasText(plan.getScopeLabel())) {
            sb.append("范围：").append(plan.getScopeLabel().trim()).append('\n');
        }
        if (StringUtils.hasText(plan.getTimeLabel())) {
            sb.append("时间：").append(plan.getTimeLabel().trim()).append('\n');
        }
        Map<String, Object> diagnosis = plan.getDiagnosis();
        if (diagnosis != null && StringUtils.hasText(str(diagnosis, "headlineZh"))) {
            sb.append(str(diagnosis, "headlineZh")).append('\n');
        } else if (StringUtils.hasText(plan.getDishName())) {
            sb.append(plan.getDishName().trim()).append(" 的利润处方分析已完成。").append('\n');
        }
    }

    private static void appendPricingAndMargin(StringBuilder sb, DishProfitPrescriptionAnswerPlan plan) {
        Map<String, Object> pricing = plan.getPricing();
        Map<String, Object> margin = plan.getMargin();
        Map<String, Object> suggested = plan.getSuggestedPrice();
        Map<String, Object> menu = plan.getMenuContext();

        appendMetricLine(sb, "当前售价", pricing, "listPricePerPortion", "元/份");
        appendMetricLine(sb, "实际成本（123口径）", margin, "actualCostPerPortion123", "元/份");
        appendMetricLine(sb, "理论成本", margin, "theoryCostPerPortion", "元/份");
        appendMetricLine(sb, "实际成本", margin, "actualCostPerPortion", "元/份");
        appendMetricLine(sb, "实际与理论差异", margin, "diffCostPerPortion", "元/份");
        appendMetricLine(sb, "综合毛利率", margin, "blendedGrossMarginRateOnListPrice", "%");
        appendMetricLine(sb, "目标毛利率", margin, "grossMarginStandardTarget", "%");
        if (suggested != null && StringUtils.hasText(str(suggested, "suggestedPricePerPortion"))) {
            sb.append("按目标毛利率 ")
                    .append(blankOr(str(suggested, "targetGrossMarginRate"), "标准"))
                    .append("% 测算，建议售价约 ")
                    .append(str(suggested, "suggestedPricePerPortion"))
                    .append(" 元/份。")
                    .append('\n');
        }
        if (menu != null) {
            Integer salesRank = intOrNull(menu.get("salesRank"));
            Integer salesRankOf = intOrNull(menu.get("salesRankOf"));
            if (salesRank != null && salesRankOf != null) {
                sb.append("菜单内销量排名：第 ").append(salesRank).append(" / ").append(salesRankOf).append('\n');
            }
            Integer marginRank = intOrNull(menu.get("marginRank"));
            Integer marginRankOf = intOrNull(menu.get("marginRankOf"));
            if (marginRank != null && marginRankOf != null) {
                sb.append("菜单内毛利排名：第 ").append(marginRank).append(" / ").append(marginRankOf).append('\n');
            }
            if (Boolean.TRUE.equals(menu.get("rankTruncated"))) {
                sb.append("（排名基于当前返回的菜品列表，完整菜单较大时仅供参考）").append('\n');
            }
        }
    }

    private static void appendIngredientHighlights(StringBuilder sb, DishProfitPrescriptionAnswerPlan plan) {
        List<Map<String, Object>> rows = plan.getIngredientRows();
        if (rows == null || rows.isEmpty()) {
            sb.append("本轮未返回配料明细。").append('\n');
            return;
        }
        int reviewCount = 0;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object flagsObj = row.get("reviewFlags");
            if (!(flagsObj instanceof List<?> flags) || flags.isEmpty()) {
                continue;
            }
            reviewCount++;
            String name = firstNonBlank(str(row, "gbDgGoodsName"), "（未命名配料）");
            sb.append("· ").append(name);
            List<String> zhFlags = new ArrayList<>();
            for (Object f : flags) {
                if (f == null) {
                    continue;
                }
                zhFlags.add(reviewFlagZh(f.toString()));
            }
            if (!zhFlags.isEmpty()) {
                sb.append("：").append(String.join("、", zhFlags));
            }
            if (StringUtils.hasText(str(row, "unitPrice"))) {
                sb.append("；出库均价 ").append(str(row, "unitPrice")).append(" 元（非最新采购价）");
            }
            sb.append('\n');
        }
        if (reviewCount == 0) {
            sb.append("主要配料成本结构已列入卡片，未发现明显用量异常标记。").append('\n');
        }
    }

    private static void appendActions(StringBuilder sb, DishProfitPrescriptionAnswerPlan plan) {
        List<DishProfitPrescriptionRecommendedAction> actions = plan.getRecommendedActions();
        if (actions == null || actions.isEmpty()) {
            sb.append("暂无额外行动建议。").append('\n');
            return;
        }
        List<DishProfitPrescriptionRecommendedAction> sorted = new ArrayList<>(actions);
        sorted.sort(Comparator.comparingInt(DishProfitPrescriptionRecommendedAction::getPriority));
        int i = 1;
        for (DishProfitPrescriptionRecommendedAction action : sorted) {
            if (action == null || !StringUtils.hasText(action.getReasonZh())) {
                continue;
            }
            sb.append(i++).append(". ").append(action.getReasonZh().trim()).append('\n');
        }
    }

    /** knownGaps 仅 debug；正文只输出 P1 能力边界说明。 */
    private static void appendCapabilityLimitsNotice(StringBuilder sb, DishProfitPrescriptionAnswerPlan plan) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        List<String> gaps = plan.getKnownGaps();
        if (gaps != null) {
            for (String gap : gaps) {
                if (!StringUtils.hasText(gap)) {
                    continue;
                }
                switch (gap.trim()) {
                    case "LATEST_PURCHASE_PRICE_NOT_IN_P1" -> phrases.add("最新采购价");
                    case "EXTERNAL_MARKET_BENCHMARK_NOT_IN_P1" -> phrases.add("外部市场比价");
                    case "CROSS_STORE_DISH_RANK_NOT_IN_P1" -> phrases.add("跨门店菜品排名");
                    default -> { /* harness/debug only */ }
                }
            }
        }
        if (phrases.isEmpty()) {
            return;
        }
        appendSectionTitle(sb, "说明");
        sb.append("当前版本暂不提供")
                .append(joinWithAnd(new ArrayList<>(phrases)))
                .append("，配料单价为出库均价口径。")
                .append('\n');
    }

    private static String reviewFlagZh(String code) {
        return switch (code.trim()) {
            case "USAGE_ABNORMAL" -> "用量与配方偏差较大";
            case "WASTE_OR_LOSS" -> "存在损耗或报废";
            case "FOCUS_INGREDIENT" -> "为主要成本来源";
            default -> "需复核";
        };
    }

    private static void appendMetricLine(
            StringBuilder sb, String label, Map<String, Object> map, String key, String unit) {
        String v = str(map, key);
        if (!StringUtils.hasText(v)) {
            return;
        }
        sb.append(label).append("：").append(v);
        if (StringUtils.hasText(unit) && !v.endsWith(unit)) {
            sb.append(' ').append(unit);
        }
        sb.append('\n');
    }

    private static String joinWithAnd(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        if (parts.size() == 2) {
            return parts.get(0) + "和" + parts.get(1);
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < parts.size() - 1; i++) {
            if (i > 0) {
                joined.append('、');
            }
            joined.append(parts.get(i));
        }
        return joined.append('和').append(parts.get(parts.size() - 1)).toString();
    }

    private static Integer intOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }

    private static String blankOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        return StringUtils.hasText(b) ? b.trim() : b;
    }
}
