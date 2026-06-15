package com.nongxinle.ai.graph.business;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.composer.menu.MenuOperationPortfolioExpressionSupport;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOperationDisplayCard;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioCategory;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioClassification;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioDish;
import com.nongxinle.ai.dto.business.MenuOperationRecommendedAction;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 从已定稿 {@link MenuOperationAnswerPlan} 投影统一 Run {@code cards[]} 条目；
 * 不查库、不重算业务指标。
 */
public final class MenuOperationAnswerPlanCardSupport {

    private static final String SOURCE_ANSWER_PLAN = "menuOperationAnswerPlan";
    private static final String PAYLOAD_STATUS_EMPTY = "EMPTY";
    private static final String PAYLOAD_STATUS_ACTIVE = "ACTIVE";
    /** 预留 planType：专门「调整建议」类问题主卡为行动清单（Semantic 矩阵 MO-C）。 */
    private static final String PLAN_TYPE_MENU_ACTION_RECOMMENDATION =
            MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION;

    private MenuOperationAnswerPlanCardSupport() {}

    /**
     * 按 AnswerPlan {@code planType} 投影 cards[]：一问一主卡；{@code recommendedActions} 为事实字段，
     * 不得因非空而追加 {@code MENU_ACTION_RECOMMENDATION_CARD}。
     */
    public static List<Map<String, Object>> buildRunCards(MenuOperationAnswerPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getPlanType())) {
            return List.of();
        }
        String planType = plan.getPlanType().trim();

        if (PLAN_TYPE_MENU_ACTION_RECOMMENDATION.equals(planType)) {
            Map<String, Object> actionCard = buildActionRecommendationCard(plan);
            if (actionCard != null) {
                return List.of(actionCard);
            }
            return List.of();
        }

        if (MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT.equals(planType)) {
            Map<String, Object> marginCard = buildHighSalesLowMarginCard(plan);
            if (marginCard != null) {
                return List.of(marginCard);
            }
            return List.of();
        }

        if (MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW.equals(planType)) {
            if (plan.getMenuPortfolioClassification() != null) {
                Map<String, Object> quadrantCard = buildPortfolioQuadrantCard(plan);
                if (quadrantCard != null) {
                    return List.of(quadrantCard);
                }
            }
            if (MenuPortfolioSalesEvidenceSupport.hasPortfolioQuadrantEmptyCard(plan)) {
                Map<String, Object> emptyCard = buildPortfolioQuadrantEmptyCard(plan);
                if (emptyCard != null) {
                    return List.of(emptyCard);
                }
            }
            return List.of();
        }

        return List.of();
    }

    /** @deprecated 单卡入口；请使用 {@link #buildRunCards(MenuOperationAnswerPlan)}。 */
    @Deprecated
    public static Map<String, Object> buildRunCard(MenuOperationAnswerPlan plan) {
        List<Map<String, Object>> cards = buildRunCards(plan);
        return cards.isEmpty() ? null : cards.get(0);
    }

    /** 行动清单卡：只读 {@link MenuOperationAnswerPlan#getRecommendedActions()} 等字段（含空态）。 */
    private static Map<String, Object> buildActionRecommendationCard(MenuOperationAnswerPlan plan) {
        MenuOperationDisplayCard display =
                findDisplayCard(plan.getDisplayCards(), MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION);
        card.put(
                "title",
                display != null && StringUtils.hasText(display.getTitle())
                        ? display.getTitle().trim()
                        : "菜单优化方案");
        card.put(
                "subtitle",
                display != null && StringUtils.hasText(display.getSubtitle())
                        ? display.getSubtitle().trim()
                        : "基于四象限分层与销量、毛利、实际利润生成的优先级建议");
        card.put(
                "chartType",
                display != null && StringUtils.hasText(display.getChartType())
                        ? display.getChartType().trim()
                        : MenuOperationAnswerPlan.CHART_TYPE_PLAN);
        card.put("payload", buildActionRecommendationPayload(plan));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE_ANSWER_PLAN);
        source.put(
                "dataRef",
                display != null && StringUtils.hasText(display.getDataRef())
                        ? display.getDataRef().trim()
                        : MenuOperationAnswerPlan.DATA_REF_MENU_OPTIMIZATION_PLAN);
        card.put("source", source);
        return card;
    }

    private static Map<String, Object> buildActionRecommendationPayload(MenuOperationAnswerPlan plan) {
        MenuOptimizationPlan optimization = plan.getMenuOptimizationPlan();
        if (optimization != null) {
            return buildOptimizationPlanPayload(plan, optimization);
        }
        Map<String, Map<String, Object>> dishIndex = indexDishFacts(plan);
        List<Map<String, Object>> actions = projectActionRows(plan.getRecommendedActions(), dishIndex);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalActionCount", actions.size());
        payload.put("summary", buildActionRecommendationSummary(actions));
        payload.put("actions", actions);
        if (actions.isEmpty()) {
            payload.put("status", PAYLOAD_STATUS_EMPTY);
        } else {
            payload.put("status", PAYLOAD_STATUS_ACTIVE);
        }
        return payload;
    }

    private static Map<String, Object> buildOptimizationPlanPayload(
            MenuOperationAnswerPlan plan, MenuOptimizationPlan optimization) {
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean hasContent =
                StringUtils.hasText(optimization.getOptimizationSummary())
                        || hasOptimizationDishes(optimization);
        payload.put("status", hasContent ? PAYLOAD_STATUS_ACTIVE : PAYLOAD_STATUS_EMPTY);
        putIfPresent(payload, "optimizationSummary", optimization.getOptimizationSummary());
        payload.put("priorityGroups", serializeList(optimization.getPriorityGroups()));
        payload.put("costReviewDishes", serializeList(optimization.getCostReviewDishes()));
        payload.put("protectDishes", serializeList(optimization.getProtectDishes()));
        payload.put("promotionDishes", serializeList(optimization.getPromotionDishes()));
        payload.put("watchListDishes", serializeList(normalizeWatchListDisplay(optimization.getWatchListDishes())));
        payload.put("nextSteps", optimization.getNextSteps() == null ? List.of() : optimization.getNextSteps());
        payload.put(
                "evidenceRows",
                plan.getEvidenceRows() == null ? List.of() : serializeList(plan.getEvidenceRows()));
        payload.put(
                "capabilityLimits",
                optimization.getCapabilityLimits() == null
                        ? Map.of()
                        : new LinkedHashMap<>(optimization.getCapabilityLimits()));
        payload.put("summary", optimization.getOptimizationSummary());
        return payload;
    }

    private static boolean hasOptimizationDishes(MenuOptimizationPlan optimization) {
        if (optimization == null) {
            return false;
        }
        return !isEmpty(optimization.getCostReviewDishes())
                || !isEmpty(optimization.getProtectDishes())
                || !isEmpty(optimization.getPromotionDishes())
                || !isEmpty(optimization.getWatchListDishes())
                || !isEmpty(optimization.getPriorityGroups());
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> serializeList(List<?> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        try {
            Object parsed = JSON.parse(JSON.toJSONString(items));
            if (!(parsed instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return out;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private static List<Map<String, Object>> projectActionRows(
            List<MenuOperationRecommendedAction> recommendedActions,
            Map<String, Map<String, Object>> dishIndex) {
        if (recommendedActions == null || recommendedActions.isEmpty()) {
            return List.of();
        }
        List<MenuOperationRecommendedAction> sorted = new ArrayList<>(recommendedActions);
        sorted.sort(Comparator.comparingInt(MenuOperationRecommendedAction::getPriority));

        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MenuOperationRecommendedAction action : sorted) {
            if (action == null || !StringUtils.hasText(action.getActionCode())) {
                continue;
            }
            List<String> dishIds = action.getTargetDishIds();
            if (dishIds == null || dishIds.isEmpty()) {
                continue;
            }
            for (String dishId : dishIds) {
                if (!StringUtils.hasText(dishId)) {
                    continue;
                }
                String normalizedDishId = dishId.trim();
                String dedupeKey = action.getActionCode().trim() + "|" + normalizedDishId;
                if (!seen.add(dedupeKey)) {
                    continue;
                }
                Map<String, Object> dishFacts = dishIndex.get(normalizedDishId);
                rows.add(projectActionRow(action, normalizedDishId, dishFacts));
            }
        }
        return rows;
    }

    private static Map<String, Object> projectActionRow(
            MenuOperationRecommendedAction action, String dishId, Map<String, Object> dishFacts) {
        String actionCode = action.getActionCode().trim();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actionType", actionTypeForCode(actionCode));
        row.put("actionName", actionLabel(actionCode));
        row.put("dishId", dishId);
        putIfPresent(row, "dishName", resolveDishName(dishFacts));
        row.put("priority", priorityLabel(action.getPriority()));
        putIfPresent(row, "reason", resolveActionReason(action, dishFacts));
        putIfPresent(row, "salesCount", resolveSalesCount(dishFacts));
        putIfPresent(row, "salesAmount", resolveSalesAmount(dishFacts));
        putIfPresent(row, "blendedGrossMarginRateOnListPrice", fieldFromDish(dishFacts, "blendedGrossMarginRateOnListPrice"));
        putIfPresent(row, "actualProfitAmount", fieldFromDish(dishFacts, "actualProfitAmount"));
        putIfPresent(row, "evidenceRefId", resolveEvidenceRefId(action));
        return row;
    }

    private static Map<String, Map<String, Object>> indexDishFacts(MenuOperationAnswerPlan plan) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        indexDishList(index, plan.getRiskDishes());
        indexDishList(index, plan.getOpportunityDishes());
        indexDishList(index, plan.getFocusDishes());
        indexPortfolioDishes(index, plan.getMenuPortfolioClassification());
        return index;
    }

    private static void indexDishList(Map<String, Map<String, Object>> index, List<Map<String, Object>> dishes) {
        if (dishes == null) {
            return;
        }
        for (Map<String, Object> row : dishes) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String dishId = firstNonBlank(row.get("foodId"), row.get("dishId"));
            if (StringUtils.hasText(dishId)) {
                index.putIfAbsent(dishId.trim(), row);
            }
        }
    }

    private static void indexPortfolioDishes(
            Map<String, Map<String, Object>> index, MenuPortfolioClassification portfolio) {
        if (portfolio == null || portfolio.getCategories() == null) {
            return;
        }
        for (MenuPortfolioCategory category : portfolio.getCategories()) {
            if (category == null || category.getDishes() == null) {
                continue;
            }
            for (MenuPortfolioDish dish : category.getDishes()) {
                if (dish == null || !StringUtils.hasText(dish.getDishId())) {
                    continue;
                }
                index.putIfAbsent(dish.getDishId().trim(), portfolioDishAsMap(dish));
            }
        }
    }

    private static Map<String, Object> portfolioDishAsMap(MenuPortfolioDish dish) {
        Map<String, Object> row = new LinkedHashMap<>();
        putIfPresent(row, "dishId", dish.getDishId());
        putIfPresent(row, "foodId", dish.getDishId());
        putIfPresent(row, "dishName", dish.getDishName());
        putIfPresent(row, "soldPortionsTotal", dish.getSalesCount());
        putIfPresent(row, "actualRevenue", dish.getSalesAmount());
        putIfPresent(row, "blendedGrossMarginRateOnListPrice", dish.getBlendedGrossMarginRateOnListPrice());
        putIfPresent(row, "actualProfitAmount", dish.getActualProfitAmount());
        putIfPresent(row, "actualCostTotalAmount123", dish.getActualCostTotalAmount123());
        putIfPresent(row, "riskReason", dish.getReason());
        putIfPresent(row, "evidenceRefId", dish.getEvidenceRefId());
        return row;
    }

    private static String resolveDishName(Map<String, Object> dishFacts) {
        String name = fieldFromDish(dishFacts, "dishName");
        return StringUtils.hasText(name) ? name : null;
    }

    private static String resolveSalesCount(Map<String, Object> dishFacts) {
        return firstNonBlank(
                fieldFromDish(dishFacts, "soldPortionsTotal"), fieldFromDish(dishFacts, "salesCount"));
    }

    private static String resolveSalesAmount(Map<String, Object> dishFacts) {
        return firstNonBlank(
                fieldFromDish(dishFacts, "actualRevenue"), fieldFromDish(dishFacts, "salesAmount"));
    }

    private static String fieldFromDish(Map<String, Object> dishFacts, String key) {
        if (dishFacts == null || key == null) {
            return null;
        }
        Object v = dishFacts.get(key);
        return v == null ? null : v.toString().trim();
    }

    private static String resolveActionReason(
            MenuOperationRecommendedAction action, Map<String, Object> dishFacts) {
        Object riskReason = dishFacts != null ? dishFacts.get("riskReason") : null;
        if (riskReason != null && StringUtils.hasText(riskReason.toString())) {
            return riskReason.toString().trim();
        }
        Object reason = dishFacts != null ? dishFacts.get("reason") : null;
        if (reason != null && StringUtils.hasText(reason.toString())) {
            return reason.toString().trim();
        }
        return rationaleLabel(action.getRationaleKey());
    }

    private static String buildActionRecommendationSummary(List<Map<String, Object>> actions) {
        if (actions == null || actions.isEmpty()) {
            return "本期暂无需要优先调整的菜品。";
        }
        int total = actions.size();
        LinkedHashMap<String, Integer> byActionName = new LinkedHashMap<>();
        for (Map<String, Object> action : actions) {
            if (action == null) {
                continue;
            }
            Object nameObj = action.get("actionName");
            if (nameObj == null || !StringUtils.hasText(nameObj.toString())) {
                continue;
            }
            String name = nameObj.toString().trim();
            byActionName.merge(name, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本月建议优先关注 ").append(total).append(" 道菜");
        if (!byActionName.isEmpty()) {
            sb.append("，其中 ");
            int i = 0;
            for (Map.Entry<String, Integer> entry : byActionName.entrySet()) {
                if (i++ > 0) {
                    sb.append('，');
                }
                sb.append(entry.getValue()).append(" 道建议").append(entry.getKey());
            }
        }
        sb.append('。');
        return sb.toString();
    }

    private static String actionTypeForCode(String code) {
        return switch (code) {
            case MenuOperationRecommendedAction.KEEP_AND_PROMOTE -> "PROMOTE";
            case MenuOperationRecommendedAction.RAISE_PRICE -> "PRICE_ADJUST";
            case MenuOperationRecommendedAction.REDUCE_COST -> "COST_REVIEW";
            case MenuOperationRecommendedAction.CONSIDER_DROP -> "CONSIDER_DROP";
            case MenuOperationRecommendedAction.RECIPE_REVIEW -> "RECIPE_REVIEW";
            case MenuOperationRecommendedAction.CHECK_STOCK_REDUCE -> "STOCK_WATCH";
            default -> code;
        };
    }

    private static String priorityLabel(int priority) {
        if (priority <= 1) {
            return "HIGH";
        }
        if (priority == 2) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static String rationaleLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return switch (key.trim()) {
            case "NEGATIVE_ACTUAL_PROFIT" -> "实际利润为负，存在亏损";
            case "HIGH_SALES_LOW_MARGIN" -> "卖得多但毛利偏低，建议降本复核";
            case "LOW_MARGIN_FALLBACK" -> "毛利率相对偏低，建议复核成本结构";
            case "LOW_MARGIN_OR_LOSS" -> "毛利率偏低或实际利润为负";
            case "HEAD_PROFIT_DISH" -> "利润贡献领先，可继续主推";
            default -> null;
        };
    }

    private static Map<String, Object> buildPortfolioQuadrantCard(MenuOperationAnswerPlan plan) {
        MenuPortfolioClassification portfolio = plan.getMenuPortfolioClassification();
        if (portfolio == null) {
            return null;
        }
        MenuOperationDisplayCard display =
                findDisplayCard(plan.getDisplayCards(), MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);
        card.put(
                "title",
                display != null && StringUtils.hasText(display.getTitle())
                        ? display.getTitle().trim()
                        : "菜单结构四象限");
        card.put(
                "subtitle",
                MenuOperationPortfolioExpressionSupport.portfolioCardSubtitle());
        card.put(
                "chartType",
                display != null && StringUtils.hasText(display.getChartType())
                        ? display.getChartType().trim()
                        : MenuOperationAnswerPlan.CHART_TYPE_PIE);
        Map<String, Object> payload = serializeObject(normalizePortfolioDisplay(portfolio));
        stripTechnicalPortfolioPayloadFields(payload);
        card.put("payload", payload);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE_ANSWER_PLAN);
        source.put(
                "dataRef",
                display != null && StringUtils.hasText(display.getDataRef())
                        ? display.getDataRef().trim()
                        : MenuOperationAnswerPlan.DATA_REF_MENU_PORTFOLIO_CLASSIFICATION);
        card.put("source", source);
        return card;
    }

    private static Map<String, Object> buildPortfolioQuadrantEmptyCard(MenuOperationAnswerPlan plan) {
        MenuOperationDisplayCard display =
                findDisplayCard(plan.getDisplayCards(), MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);
        card.put(
                "title",
                display != null && StringUtils.hasText(display.getTitle())
                        ? display.getTitle().trim()
                        : "菜单结构四象限");
        card.put(
                "subtitle",
                display != null && StringUtils.hasText(display.getSubtitle())
                        ? display.getSubtitle().trim()
                        : MenuPortfolioSalesEvidenceSupport.EMPTY_PORTFOLIO_MESSAGE);
        card.put(
                "chartType",
                display != null && StringUtils.hasText(display.getChartType())
                        ? display.getChartType().trim()
                        : MenuOperationAnswerPlan.CHART_TYPE_PIE);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", PAYLOAD_STATUS_EMPTY);
        payload.put(
                "noDataReason",
                MenuPortfolioSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
        payload.put("summary", MenuPortfolioSalesEvidenceSupport.EMPTY_PORTFOLIO_MESSAGE);
        payload.put("categories", List.of());
        card.put("payload", payload);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE_ANSWER_PLAN);
        source.put(
                "dataRef",
                display != null && StringUtils.hasText(display.getDataRef())
                        ? display.getDataRef().trim()
                        : MenuOperationAnswerPlan.DATA_REF_MENU_PORTFOLIO_CLASSIFICATION);
        card.put("source", source);
        return card;
    }

    /** high_sales_low_profit：始终返回 card（含空态 summary），数据只读 {@link MenuOperationAnswerPlan#getRiskDishes()} 等。 */
    private static Map<String, Object> buildHighSalesLowMarginCard(MenuOperationAnswerPlan plan) {
        MenuOperationDisplayCard display =
                findDisplayCard(plan.getDisplayCards(), MenuOperationAnswerPlan.CARD_TYPE_MENU_HIGH_SALES_LOW_MARGIN);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", MenuOperationAnswerPlan.CARD_TYPE_MENU_HIGH_SALES_LOW_MARGIN);
        card.put(
                "title",
                display != null && StringUtils.hasText(display.getTitle())
                        ? display.getTitle().trim()
                        : "畅销低利菜");
        card.put(
                "subtitle",
                display != null && StringUtils.hasText(display.getSubtitle())
                        ? display.getSubtitle().trim()
                        : "销量较高但毛利率或实际利润偏低的菜品");
        card.put(
                "chartType",
                display != null && StringUtils.hasText(display.getChartType())
                        ? display.getChartType().trim()
                        : MenuOperationAnswerPlan.CHART_TYPE_TABLE);
        card.put("payload", buildHighSalesLowMarginPayload(plan));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE_ANSWER_PLAN);
        source.put(
                "dataRef",
                display != null && StringUtils.hasText(display.getDataRef())
                        ? display.getDataRef().trim()
                        : MenuOperationAnswerPlan.DATA_REF_RISK_DISHES);
        card.put("source", source);
        return card;
    }

    private static Map<String, Object> buildHighSalesLowMarginPayload(MenuOperationAnswerPlan plan) {
        List<Map<String, Object>> riskRows =
                plan.getRiskDishes() == null ? List.of() : plan.getRiskDishes();
        Map<String, MenuOperationRecommendedAction> actionByDishId =
                indexRecommendedActionsByDishId(plan.getRecommendedActions());

        List<Map<String, Object>> dishes = new ArrayList<>(riskRows.size());
        for (Map<String, Object> row : riskRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            dishes.add(projectHighSalesLowMarginDishRow(row, actionByDishId));
        }

        int total = dishes.size();
        Map<String, Object> facts = plan.getSummaryFacts();
        String matchMode = resolveHighSalesLowMarginMatchMode(facts);
        String summary = resolveHighSalesLowMarginSummary(facts, matchMode, total);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matchMode", matchMode);
        payload.put("totalRiskDishCount", total);
        payload.put("summary", summary);
        payload.put("dishes", dishes);
        if (total == 0) {
            payload.put("status", PAYLOAD_STATUS_EMPTY);
        }
        return payload;
    }

    private static String resolveHighSalesLowMarginMatchMode(Map<String, Object> facts) {
        if (facts != null) {
            Object mode = facts.get("highSalesLowMarginMatchMode");
            if (mode != null && StringUtils.hasText(mode.toString())) {
                return mode.toString().trim();
            }
        }
        return "HIGH_SALES_LOW_MARGIN";
    }

    private static String resolveHighSalesLowMarginSummary(
            Map<String, Object> facts, String matchMode, int total) {
        if (facts != null) {
            Object summary = facts.get("highSalesLowMarginSummary");
            if (summary != null && StringUtils.hasText(summary.toString())) {
                return summary.toString().trim();
            }
        }
        return buildHighSalesLowMarginSummary(matchMode, total);
    }

    private static String buildHighSalesLowMarginSummary(String matchMode, int total) {
        if (total <= 0) {
            return "本期未发现明显畅销低利菜。";
        }
        if ("LOW_MARGIN_FALLBACK".equals(matchMode)) {
            return "本期未发现特别典型的畅销低利菜，但以下菜品毛利率相对偏低，建议优先复核。";
        }
        return "本月有 " + total + " 道菜销量靠前但利润效率偏低，建议优先复核成本和定价。";
    }

    private static Map<String, Object> projectHighSalesLowMarginDishRow(
            Map<String, Object> row, Map<String, MenuOperationRecommendedAction> actionByDishId) {
        String dishId = firstNonBlank(row.get("foodId"), row.get("dishId"));
        MenuOperationRecommendedAction action =
                StringUtils.hasText(dishId) ? actionByDishId.get(dishId.trim()) : null;

        Map<String, Object> dish = new LinkedHashMap<>();
        putIfPresent(dish, "dishId", dishId);
        putIfPresent(dish, "dishName", row.get("dishName"));
        putIfPresent(dish, "salesCount", firstNonBlank(row.get("soldPortionsTotal"), row.get("salesCount")));
        putIfPresent(dish, "salesAmount", firstNonBlank(row.get("actualRevenue"), row.get("salesAmount")));
        putIfPresent(dish, "blendedGrossMarginRateOnListPrice", row.get("blendedGrossMarginRateOnListPrice"));
        putIfPresent(dish, "actualProfitAmount", row.get("actualProfitAmount"));
        putIfPresent(dish, "actualCostTotalAmount123", row.get("actualCostTotalAmount123"));
        putIfPresent(dish, "riskReason", row.get("riskReason"));
        putIfPresent(dish, "salesRank", row.get("salesRank"));
        putIfPresent(dish, "salesLevelDescription", row.get("salesLevelDescription"));
        putIfPresent(dish, "recommendedAction", resolveRecommendedActionLabel(action, row));
        putIfPresent(dish, "evidenceRefId", resolveEvidenceRefId(action));
        return dish;
    }

    private static Map<String, MenuOperationRecommendedAction> indexRecommendedActionsByDishId(
            List<MenuOperationRecommendedAction> actions) {
        Map<String, MenuOperationRecommendedAction> out = new LinkedHashMap<>();
        if (actions == null) {
            return out;
        }
        for (MenuOperationRecommendedAction action : actions) {
            if (action == null || action.getTargetDishIds() == null) {
                continue;
            }
            for (String dishId : action.getTargetDishIds()) {
                if (StringUtils.hasText(dishId)) {
                    out.putIfAbsent(dishId.trim(), action);
                }
            }
        }
        return out;
    }

    private static String resolveRecommendedActionLabel(
            MenuOperationRecommendedAction action, Map<String, Object> row) {
        if (action != null && StringUtils.hasText(action.getActionCode())) {
            return actionLabel(action.getActionCode().trim());
        }
        Object outcome = row.get("profitOutcome");
        if ("LOSS".equals(outcome)) {
            return actionLabel(MenuOperationRecommendedAction.RAISE_PRICE);
        }
        return actionLabel(MenuOperationRecommendedAction.REDUCE_COST);
    }

    private static String resolveEvidenceRefId(MenuOperationRecommendedAction action) {
        if (action == null || action.getEvidenceRefIds() == null || action.getEvidenceRefIds().isEmpty()) {
            return null;
        }
        for (String ref : action.getEvidenceRefIds()) {
            if (StringUtils.hasText(ref)) {
                return ref.trim();
            }
        }
        return null;
    }

    private static String actionLabel(String code) {
        return switch (code) {
            case MenuOperationRecommendedAction.KEEP_AND_PROMOTE -> "继续主推";
            case MenuOperationRecommendedAction.RAISE_PRICE -> "考虑调价";
            case MenuOperationRecommendedAction.REDUCE_COST -> "压降成本";
            case MenuOperationRecommendedAction.CONSIDER_DROP -> "评估下架";
            case MenuOperationRecommendedAction.RECIPE_REVIEW -> "复核配方";
            case MenuOperationRecommendedAction.CHECK_STOCK_REDUCE -> "关注损耗";
            default -> code;
        };
    }

    private static MenuOperationDisplayCard findDisplayCard(
            List<MenuOperationDisplayCard> cards, String cardType) {
        if (cards == null || cards.isEmpty() || !StringUtils.hasText(cardType)) {
            return null;
        }
        for (MenuOperationDisplayCard card : cards) {
            if (card != null && cardType.equals(card.getCardType())) {
                return card;
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = value.toString().trim();
        if (StringUtils.hasText(s)) {
            target.put(key, s);
        }
    }

    private static String firstNonBlank(Object a, Object b) {
        if (a != null && StringUtils.hasText(a.toString())) {
            return a.toString().trim();
        }
        if (b != null && StringUtils.hasText(b.toString())) {
            return b.toString().trim();
        }
        return null;
    }

    private static final String ELIMINATE_LEGACY_DISPLAY = "淘汰菜";
    private static final String ELIMINATE_DISPLAY = "观察菜";

    /**
     * 卡片投影层：统一老板可读文案；ELIMINATE 展示名固定为「观察菜」；不向卡片暴露算法阈值字段。
     */
    private static MenuPortfolioClassification normalizePortfolioDisplay(
            MenuPortfolioClassification portfolio) {
        if (portfolio == null || portfolio.getCategories() == null) {
            return portfolio;
        }
        List<MenuPortfolioCategory> categories = new ArrayList<>();
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat == null) {
                continue;
            }
            categories.add(normalizePortfolioCategory(cat));
        }
        return MenuPortfolioClassification.builder()
                .totalDishCount(portfolio.getTotalDishCount())
                .categories(categories)
                .build();
    }

    private static MenuPortfolioCategory normalizePortfolioCategory(MenuPortfolioCategory cat) {
        String categoryCode = cat.getCategoryCode();
        String categoryName = cat.getCategoryName();
        if (MenuOperationAnswerPlan.CATEGORY_ELIMINATE.equals(categoryCode)) {
            if (!StringUtils.hasText(categoryName) || ELIMINATE_LEGACY_DISPLAY.equals(categoryName.trim())) {
                categoryName = ELIMINATE_DISPLAY;
            }
        }
        String recommendedAction = MenuOperationPortfolioExpressionSupport.categoryRecommendedAction(categoryCode);
        if (!StringUtils.hasText(recommendedAction)) {
            recommendedAction = cat.getRecommendedAction();
        }
        String summary =
                MenuOperationPortfolioExpressionSupport.rewriteCategorySummary(
                        cat.getSummary(), categoryName, cat.getCount(), cat.getRatio());
        List<MenuPortfolioDish> dishes = normalizePortfolioDishes(cat.getDishes(), categoryCode);
        return MenuPortfolioCategory.builder()
                .categoryCode(categoryCode)
                .categoryName(categoryName)
                .count(cat.getCount())
                .ratio(cat.getRatio())
                .summary(summary)
                .recommendedAction(recommendedAction)
                .dishes(dishes)
                .build();
    }

    private static List<MenuPortfolioDish> normalizePortfolioDishes(
            List<MenuPortfolioDish> dishes, String categoryCode) {
        if (dishes == null || dishes.isEmpty()) {
            return dishes;
        }
        List<MenuPortfolioDish> out = new ArrayList<>();
        for (MenuPortfolioDish dish : dishes) {
            if (dish == null) {
                continue;
            }
            out.add(
                    MenuPortfolioDish.builder()
                            .dishId(dish.getDishId())
                            .dishName(dish.getDishName())
                            .salesCount(dish.getSalesCount())
                            .salesAmount(dish.getSalesAmount())
                            .blendedGrossMarginRateOnListPrice(dish.getBlendedGrossMarginRateOnListPrice())
                            .actualProfitAmount(dish.getActualProfitAmount())
                            .actualCostTotalAmount123(dish.getActualCostTotalAmount123())
                            .reason(
                                    MenuOperationPortfolioExpressionSupport.rewritePortfolioDishReason(
                                            dish.getReason(), categoryCode))
                            .evidenceRefId(dish.getEvidenceRefId())
                            .build());
        }
        return out;
    }

    private static void stripTechnicalPortfolioPayloadFields(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        payload.remove("salesHighThreshold");
        payload.remove("profitHighThreshold");
        payload.remove("thresholdMethod");
        payload.remove("salesMetricName");
        payload.remove("profitMetricName");
    }

    private static List<MenuOptimizationDishItem> normalizeWatchListDisplay(
            List<MenuOptimizationDishItem> items) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        List<MenuOptimizationDishItem> out = new ArrayList<>();
        for (MenuOptimizationDishItem item : items) {
            if (item == null) {
                continue;
            }
            if (!MenuOperationAnswerPlan.CATEGORY_ELIMINATE.equals(item.getQuadrantCode())) {
                out.add(item);
                continue;
            }
            String quadrantName = item.getQuadrantName();
            String action = item.getSuggestedActionLabel();
            boolean changed = false;
            if (!StringUtils.hasText(quadrantName) || ELIMINATE_LEGACY_DISPLAY.equals(quadrantName.trim())) {
                quadrantName = ELIMINATE_DISPLAY;
                changed = true;
            }
            if (StringUtils.hasText(action)
                    && ("考虑下架".equals(action.trim()) || action.contains("淘汰"))) {
                action = "观察调整";
                changed = true;
            }
            if (!changed) {
                out.add(item);
                continue;
            }
            out.add(
                    MenuOptimizationDishItem.builder()
                            .dishId(item.getDishId())
                            .dishName(item.getDishName())
                            .quadrantCode(item.getQuadrantCode())
                            .quadrantName(quadrantName)
                            .soldPortionsTotal(item.getSoldPortionsTotal())
                            .listPriceRevenue(item.getListPriceRevenue())
                            .blendedGrossMarginRateOnListPrice(item.getBlendedGrossMarginRateOnListPrice())
                            .actualProfitAmount(item.getActualProfitAmount())
                            .suggestedActionLabel(action)
                            .reason(item.getReason())
                            .evidenceRefId(item.getEvidenceRefId())
                            .build());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeObject(Object value) {
        try {
            return JSON.parseObject(JSON.toJSONString(value));
        } catch (Exception ignore) {
            return new LinkedHashMap<>();
        }
    }
}
