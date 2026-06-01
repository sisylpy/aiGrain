package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan.DishProfitPrescriptionRecommendedAction;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@link DishProfitPrescriptionAnswerPlan} 投影统一 Run {@code cards[]}；不重算指标。
 * 正式 payload 仅保留老板可读字段，工程字段留在 AnswerPlan / harnessDebug。
 */
public final class DishProfitPrescriptionAnswerPlanCardSupport {

    private static final String SOURCE_ANSWER_PLAN = "dishProfitPrescriptionAnswerPlan";
    private static final String CHART_TYPE_PRESCRIPTION = "PRESCRIPTION";
    private static final String DATA_REF_ROOT = "dishProfitPrescriptionAnswerPlan";

    private DishProfitPrescriptionAnswerPlanCardSupport() {}

    public static List<Map<String, Object>> buildRunCards(DishProfitPrescriptionAnswerPlan plan) {
        if (plan == null || !DishProfitPrescriptionAnswerPlan.TYPE.equals(plan.getPlanType())) {
            return List.of();
        }
        Map<String, Object> card = buildPrescriptionCard(plan);
        return card == null ? List.of() : List.of(card);
    }

    private static Map<String, Object> buildPrescriptionCard(DishProfitPrescriptionAnswerPlan plan) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishProfitPrescriptionAnswerPlan.CARD_TYPE);
        card.put("title", buildTitle(plan));
        card.put("subtitle", buildSubtitle(plan));
        card.put("chartType", CHART_TYPE_PRESCRIPTION);
        card.put("payload", buildPayload(plan));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE_ANSWER_PLAN);
        source.put("dataRef", DATA_REF_ROOT);
        card.put("source", source);
        return card;
    }

    private static String buildTitle(DishProfitPrescriptionAnswerPlan plan) {
        if (StringUtils.hasText(plan.getDishName())) {
            return plan.getDishName().trim() + " · 利润处方";
        }
        return "单菜利润处方";
    }

    private static String buildSubtitle(DishProfitPrescriptionAnswerPlan plan) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(plan.getTimeLabel())) {
            sb.append(plan.getTimeLabel().trim());
        }
        if (StringUtils.hasText(plan.getScopeLabel())) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(plan.getScopeLabel().trim());
        }
        if (sb.length() == 0) {
            return "售价、成本、目标毛利与行动建议";
        }
        return sb.toString();
    }

    private static Map<String, Object> buildPayload(DishProfitPrescriptionAnswerPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", plan.getStatus());
        putIfPresent(payload, "dishName", plan.getDishName());
        putIfPresent(payload, "timeLabel", plan.getTimeLabel());
        putIfPresent(payload, "scopeLabel", plan.getScopeLabel());
        payload.put("summary", buildSummary(plan));
        payload.put("pricing", projectPricing(plan.getPricing()));
        payload.put("margin", projectMargin(plan.getMargin()));
        payload.put("suggestedPrice", projectSuggestedPrice(plan.getSuggestedPrice()));
        payload.put("menuContext", projectMenuContext(plan.getMenuContext()));
        payload.put("diagnosis", projectDiagnosis(plan.getDiagnosis()));
        payload.put(
                "ingredientRows",
                projectIngredientRows(plan.getIngredientRows() == null ? List.of() : plan.getIngredientRows()));
        payload.put("recommendedActions", projectActions(plan.getRecommendedActions()));
        return payload;
    }

    private static String buildSummary(DishProfitPrescriptionAnswerPlan plan) {
        Map<String, Object> diagnosis = plan.getDiagnosis();
        if (diagnosis != null && StringUtils.hasText(str(diagnosis, "headlineZh"))) {
            return str(diagnosis, "headlineZh");
        }
        if (StringUtils.hasText(plan.getDishName())) {
            return plan.getDishName().trim() + " 的利润处方分析已完成。";
        }
        return "单菜利润处方分析已完成。";
    }

    private static Map<String, Object> projectPricing(Map<String, Object> pricing) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (pricing == null || pricing.isEmpty()) {
            return out;
        }
        copyIfPresent(out, pricing, "listPricePerPortion");
        copyIfPresent(out, pricing, "salesPortions");
        copyIfPresent(out, pricing, "salesAmount");
        return out;
    }

    private static Map<String, Object> projectMargin(Map<String, Object> margin) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (margin == null || margin.isEmpty()) {
            return out;
        }
        copyIfPresent(out, margin, "theoryCostPerPortion");
        String actualCost = str(margin, "actualCostPerPortion");
        if (!StringUtils.hasText(actualCost)) {
            actualCost = str(margin, "actualCostPerPortion123");
        }
        putIfPresent(out, "actualCostPerPortion", actualCost);
        copyIfPresent(out, margin, "diffCostPerPortion");
        copyIfPresent(out, margin, "blendedGrossMarginRateOnListPrice");
        copyIfPresent(out, margin, "grossMarginStandardTarget");
        return out;
    }

    private static Map<String, Object> projectSuggestedPrice(Map<String, Object> suggested) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (suggested == null || suggested.isEmpty()) {
            return out;
        }
        copyIfPresent(out, suggested, "targetGrossMarginRate");
        copyIfPresent(out, suggested, "suggestedPricePerPortion");
        return out;
    }

    private static Map<String, Object> projectMenuContext(Map<String, Object> menuContext) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (menuContext == null || menuContext.isEmpty()) {
            return out;
        }
        copyIfPresent(out, menuContext, "salesRank");
        copyIfPresent(out, menuContext, "salesRankOf");
        copyIfPresent(out, menuContext, "marginRank");
        copyIfPresent(out, menuContext, "marginRankOf");
        copyIfPresent(out, menuContext, "rankTruncated");
        return out;
    }

    private static Map<String, Object> projectDiagnosis(Map<String, Object> diagnosis) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (diagnosis == null || diagnosis.isEmpty()) {
            return out;
        }
        copyIfPresent(out, diagnosis, "headlineZh");
        return out;
    }

    private static List<Map<String, Object>> projectIngredientRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            copyIfPresent(projected, row, "gbDgGoodsName");
            copyIfPresent(projected, row, "recipeUnitPerDish");
            copyIfPresent(projected, row, "theoryUsage");
            copyIfPresent(projected, row, "actualProduceUsage");
            copyIfPresent(projected, row, "unitPrice");
            copyIfPresent(projected, row, "produceCostPerPortion");
            if (!projected.isEmpty()) {
                out.add(projected);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> projectActions(
            List<DishProfitPrescriptionRecommendedAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        List<DishProfitPrescriptionRecommendedAction> sorted = new ArrayList<>(actions);
        sorted.sort(Comparator.comparingInt(DishProfitPrescriptionRecommendedAction::getPriority));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DishProfitPrescriptionRecommendedAction action : sorted) {
            if (action == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("priority", action.getPriority());
            if (StringUtils.hasText(action.getReasonZh())) {
                row.put("reasonZh", action.getReasonZh().trim());
            } else if (StringUtils.hasText(action.getActionCode())) {
                row.put("reasonZh", actionLabel(action.getActionCode().trim()));
            }
            if (StringUtils.hasText(action.getActionCode())) {
                row.put("actionName", actionLabel(action.getActionCode().trim()));
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static String actionLabel(String code) {
        return switch (code) {
            case "KEEP_AND_PROMOTE" -> "继续主推";
            case "RAISE_PRICE" -> "考虑调价";
            case "REDUCE_COST" -> "压降成本";
            case "RECIPE_REVIEW" -> "复核配方";
            case "CHECK_STOCK_REDUCE" -> "复核用量与损耗";
            default -> "优化建议";
        };
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        putIfPresent(target, key, source.get(key));
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s) {
            if (StringUtils.hasText(s)) {
                target.put(key, s.trim());
            }
            return;
        }
        target.put(key, value);
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }
}
