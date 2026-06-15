package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan.DishProfitPrescriptionRecommendedAction;
import com.nongxinle.ai.dto.business.MenuOperationRecommendedAction;
import com.nongxinle.ai.graph.business.DishProfitPrescriptionPricingSupport.SuggestedPriceResult;
import com.nongxinle.ai.graph.business.DishProfitPrescriptionRankSupport.RankResult;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 挂载 {@link DishProfitPrescriptionAnswerPlan}：contract {@code dish.profit.prescription.v1}；
 * 合并 {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS} + {@link AiBusinessToolIds#DISH_COST_ANALYSIS} 快照。
 */
public final class DishProfitPrescriptionAnswerPlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(DishProfitPrescriptionAnswerPlanBuilder.class);

    private static final double USAGE_ABNORMAL_RATIO = 0.15;
    private static final double FOCUS_INGREDIENT_COST_SHARE = 0.25;

    private DishProfitPrescriptionAnswerPlanBuilder() {}

    public static void attachIfApplicable(AiRunState state) {
        if (state == null || !state.isDishCostAnalysisPath()) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (!ToolRequestContractExecutionParamSupport.isDishProfitPrescriptionContract(rq)) {
            return;
        }
        state.setDishProfitPrescriptionAnswerPlan(null);

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("contractId", DishProfitPrescriptionAnswerPlan.CONTRACT_ID);
        debug.put("planType", DishProfitPrescriptionAnswerPlan.TYPE);

        Map<String, Object> costData = toolData(state, AiBusinessToolIds.DISH_COST_ANALYSIS);
        Map<String, Object> profitData = toolData(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        String costStatus = resolveToolStatus(state, AiBusinessToolIds.DISH_COST_ANALYSIS, costData);
        String profitStatus = resolveToolStatus(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS, profitData);

        if ("NEED_CLARIFICATION".equalsIgnoreCase(costStatus)) {
            debug.put("earlyReturnReason", "cost_tool_need_clarification");
            attachFailed(state, debug, "NEED_CLARIFICATION", null);
            return;
        }

        Integer costFoodId = parseFoodId(costData == null ? null : costData.get("dishId"));
        Integer profitFoodId = resolveProfitFoodId(profitData, costData);

        if (costFoodId != null && profitFoodId != null && !costFoodId.equals(profitFoodId)) {
            debug.put("costFoodId", costFoodId);
            debug.put("profitFoodId", profitFoodId);
            debug.put("earlyReturnReason", "DISH_ID_MISMATCH");
            attachFailed(state, debug, "DISH_ID_MISMATCH", List.of("DISH_ID_MISMATCH"));
            return;
        }

        LinkedHashSet<String> gaps = baselineP1Gaps();
        List<String> userMessages = new ArrayList<>();

        if (!"SUCCESS".equalsIgnoreCase(costStatus) && !"SUCCESS".equalsIgnoreCase(profitStatus)) {
            debug.put("earlyReturnReason", "both_tools_unsuccessful");
            attachFailed(state, debug, "TOOL_FAILURE", List.copyOf(gaps));
            return;
        }

        Integer dishId = costFoodId != null ? costFoodId : profitFoodId;
        String dishName = firstNonBlank(
                str(costData, "dishName"),
                str(profitData, "dishNameFocusHint"),
                effectiveDishName(rq));

        Map<String, Object> profitRow = findProfitRow(profitData, dishId);
        Map<String, Object> insightRow = targetInsightRow(profitData, dishId);

        if (!"SUCCESS".equalsIgnoreCase(profitStatus)) {
            gaps.add("MENU_RANK_CONTEXT_UNAVAILABLE");
        }
        if (!"SUCCESS".equalsIgnoreCase(costStatus)) {
            gaps.add("INGREDIENT_DETAIL_UNAVAILABLE");
        }

        DishProfitPrescriptionAnswerPlan plan = DishProfitPrescriptionAnswerPlan.builder()
                .planType(DishProfitPrescriptionAnswerPlan.TYPE)
                .contractId(DishProfitPrescriptionAnswerPlan.CONTRACT_ID)
                .dishId(dishId)
                .dishName(dishName)
                .timeLabel(timeLabel(state))
                .scopeLabel(scopeLabel(rq))
                .statStartDate(state.getStatStartDate())
                .statEndDate(state.getStatEndDate())
                .pricing(buildPricing(costData, insightRow, profitRow, gaps))
                .margin(buildMargin(costData, profitRow, insightRow))
                .suggestedPrice(buildSuggestedPrice(rq, profitRow, insightRow, gaps))
                .menuContext(buildMenuContext(profitData, dishId, gaps))
                .ingredientRows(projectIngredientRows(costData, gaps))
                .diagnosis(new LinkedHashMap<>())
                .recommendedActions(new ArrayList<>())
                .capabilityLimits(buildCapabilityLimits())
                .knownGaps(new ArrayList<>(gaps))
                .resultAnchors(buildAnchors(dishId, dishName))
                .debug(debug)
                .build();

        fillDiagnosisAndActions(plan, gaps);
        plan.setStatus(resolveStatus(gaps, costStatus, profitStatus));
        if (userMessages.isEmpty() && DishProfitPrescriptionAnswerPlan.STATUS_FAILED.equals(plan.getStatus())) {
            plan.setFailureReasonCode("PARTIAL_DATA");
        }

        state.setDishProfitPrescriptionAnswerPlan(plan);
        log.info(
                "[DishProfitPrescription] runId={} dishId={} status={} gaps={}",
                state.getRunId(),
                dishId,
                plan.getStatus(),
                gaps);
    }

    private static void attachFailed(
            AiRunState state,
            LinkedHashMap<String, Object> debug,
            String reasonCode,
            List<String> extraGaps) {
        LinkedHashSet<String> gaps = baselineP1Gaps();
        if (extraGaps != null) {
            gaps.addAll(extraGaps);
        }
        DishProfitPrescriptionAnswerPlan plan =
                DishProfitPrescriptionAnswerPlan.builder()
                        .planType(DishProfitPrescriptionAnswerPlan.TYPE)
                        .contractId(DishProfitPrescriptionAnswerPlan.CONTRACT_ID)
                        .status(DishProfitPrescriptionAnswerPlan.STATUS_FAILED)
                        .failureReasonCode(reasonCode)
                        .knownGaps(new ArrayList<>(gaps))
                        .capabilityLimits(buildCapabilityLimits())
                        .debug(debug)
                        .build();
        state.setDishProfitPrescriptionAnswerPlan(plan);
    }

    private static LinkedHashSet<String> baselineP1Gaps() {
        LinkedHashSet<String> gaps = new LinkedHashSet<>();
        gaps.add("LATEST_PURCHASE_PRICE_NOT_IN_P1");
        gaps.add("EXTERNAL_MARKET_BENCHMARK_NOT_IN_P1");
        gaps.add("CROSS_STORE_DISH_RANK_NOT_IN_P1");
        return gaps;
    }

    private static Map<String, Object> buildCapabilityLimits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("latestPurchasePrice", "NOT_IN_P1");
        limits.put("externalMarketBenchmark", "NOT_IN_P1");
        limits.put("crossStoreDishRank", "NOT_IN_P1");
        return limits;
    }

    private static String resolveStatus(LinkedHashSet<String> gaps, String costStatus, String profitStatus) {
        if (gaps.contains("DISH_ID_MISMATCH")) {
            return DishProfitPrescriptionAnswerPlan.STATUS_FAILED;
        }
        boolean costOk = "SUCCESS".equalsIgnoreCase(costStatus);
        boolean profitOk = "SUCCESS".equalsIgnoreCase(profitStatus);
        if (costOk && profitOk) {
            return DishProfitPrescriptionAnswerPlan.STATUS_SUCCESS;
        }
        if (costOk || profitOk) {
            return DishProfitPrescriptionAnswerPlan.STATUS_PARTIAL;
        }
        return DishProfitPrescriptionAnswerPlan.STATUS_FAILED;
    }

    private static Map<String, Object> buildPricing(
            Map<String, Object> costData,
            Map<String, Object> insightRow,
            Map<String, Object> profitRow,
            LinkedHashSet<String> gaps) {
        Map<String, Object> pricing = new LinkedHashMap<>();
        String listPrice = str(insightRow, "listPrice");
        if (StringUtils.hasText(listPrice)) {
            pricing.put("listPricePerPortion", listPrice);
        } else {
            gaps.add("LIST_PRICE_UNAVAILABLE");
        }
        putIfPresent(pricing, "salesUnitPrice", str(costData, "salesUnitPrice"));
        putIfPresent(pricing, "salesPortions", firstNonBlank(str(costData, "salesPortions"), str(profitRow, "soldPortionsTotal")));
        putIfPresent(pricing, "salesAmount", firstNonBlank(str(costData, "salesAmount"), str(profitRow, "actualRevenue")));
        return pricing;
    }

    private static Map<String, Object> buildMargin(
            Map<String, Object> costData, Map<String, Object> profitRow, Map<String, Object> insightRow) {
        Map<String, Object> margin = new LinkedHashMap<>();
        putIfPresent(margin, "actualCostPerPortion123", str(profitRow, "actualCostPerPortion123"));
        putIfPresent(margin, "theoryCostPerPortion", str(costData, "theoryCostPerPortion"));
        putIfPresent(margin, "actualCostPerPortion", str(costData, "actualCostPerPortion"));
        putIfPresent(margin, "diffCostPerPortion", str(costData, "diffCostPerPortion"));
        putIfPresent(margin, "blendedGrossMarginRateOnListPrice", str(profitRow, "blendedGrossMarginRateOnListPrice"));
        putIfPresent(margin, "grossMarginStandardTarget", str(insightRow, "grossMarginStandardTarget"));
        putIfPresent(margin, "grossMarginStandardBandLower", str(insightRow, "grossMarginStandardBandLower"));
        putIfPresent(margin, "grossMarginStandardBandUpper", str(insightRow, "grossMarginStandardBandUpper"));
        putIfPresent(margin, "grossMarginLevel", str(profitRow, "grossMarginLevel"));
        margin.put("pricingVerdict", pricingVerdict(margin));
        return margin;
    }

    private static String pricingVerdict(Map<String, Object> margin) {
        Object level = margin.get("grossMarginLevel");
        if (level == null) {
            return "UNKNOWN";
        }
        return switch (level.toString().trim().toUpperCase()) {
            case "WITHIN", "IN_BAND", "NORMAL" -> "IN_BAND";
            case "BELOW", "LOW" -> "BELOW";
            case "ABOVE", "HIGH" -> "ABOVE";
            default -> "UNKNOWN";
        };
    }

    private static Map<String, Object> buildSuggestedPrice(
            AiResolvedQueryContext rq,
            Map<String, Object> profitRow,
            Map<String, Object> insightRow,
            LinkedHashSet<String> gaps) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal costBase = coerceDecimal(profitRow == null ? null : profitRow.get("actualCostPerPortion123"));
        String requested = requestedTarget(rq);
        String standard = str(insightRow, "grossMarginStandardTarget");
        SuggestedPriceResult computed =
                DishProfitPrescriptionPricingSupport.computeSuggestedPrice(costBase, requested, standard);
        if (computed == null) {
            gaps.add("TARGET_MARGIN_UNSPECIFIED");
            return out;
        }
        out.put("targetGrossMarginRate", computed.targetGrossMarginRate());
        out.put("costBasePerPortion", computed.costBasePerPortion());
        out.put("suggestedPricePerPortion", computed.suggestedPricePerPortion());
        out.put("formulaId", computed.formulaId());
        return out;
    }

    private static Map<String, Object> buildMenuContext(
            Map<String, Object> profitData, Integer dishId, LinkedHashSet<String> gaps) {
        if (profitData == null || dishId == null) {
            return Map.of("rankBasis", "dish_profit_analysis.dishRows");
        }
        List<Map<String, Object>> dishRows = dishRows(profitData);
        int fullCount = parseInt(profitData.get("dishLineCountFull"), dishRows.size());
        int returned = parseInt(profitData.get("dishLineReturned"), dishRows.size());
        boolean truncated = fullCount > returned;
        if (truncated) {
            gaps.add("DISH_ROWS_TRUNCATED_FOR_RANK");
        }
        RankResult sales = DishProfitPrescriptionRankSupport.salesRank(dishRows, dishId, fullCount);
        RankResult margin = DishProfitPrescriptionRankSupport.marginRank(dishRows, dishId, fullCount);
        return DishProfitPrescriptionRankSupport.buildMenuContext(sales, margin, truncated);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> projectIngredientRows(
            Map<String, Object> costData, LinkedHashSet<String> gaps) {
        if (costData == null) {
            return List.of();
        }
        Object raw = costData.get("ingredientRows");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal totalActual = coerceDecimal(costData.get("actualCostPerPortion"));
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rowRaw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) rowRaw);
            row.put("unitPriceSource", DishProfitPrescriptionAnswerPlan.UNIT_PRICE_SOURCE_OUTBOUND_TYPE1_AVG);
            flagIngredientReview(row, totalActual);
            out.add(projectIngredientRow(row));
        }
        return out;
    }

    private static Map<String, Object> projectIngredientRow(Map<String, Object> row) {
        Map<String, Object> p = new LinkedHashMap<>();
        putIfPresent(p, "disGoodsId", str(row, "disGoodsId"));
        putIfPresent(p, "gbDgGoodsName", firstNonBlank(str(row, "gbDgGoodsName"), str(row, "goodsName")));
        putIfPresent(p, "recipeUnitPerDish", str(row, "recipeUnitPerDish"));
        putIfPresent(p, "theoryUsage", str(row, "theoryUsage"));
        putIfPresent(p, "actualProduceUsage", str(row, "actualProduceUsage"));
        putIfPresent(p, "actualWasteUsage", str(row, "actualWasteUsage"));
        putIfPresent(p, "actualLossUsage", str(row, "actualLossUsage"));
        putIfPresent(p, "unitPrice", str(row, "unitPrice"));
        p.put("unitPriceSource", DishProfitPrescriptionAnswerPlan.UNIT_PRICE_SOURCE_OUTBOUND_TYPE1_AVG);
        putIfPresent(p, "produceCostPerPortion", str(row, "produceCostPerPortion"));
        putIfPresent(p, "wasteCostPerPortion", str(row, "wasteCostPerPortion"));
        putIfPresent(p, "lossCostPerPortion", str(row, "lossCostPerPortion"));
        if (row.get("reviewFlags") instanceof List<?> flags && !flags.isEmpty()) {
            p.put("reviewFlags", new ArrayList<>(flags));
        }
        return p;
    }

    private static void flagIngredientReview(Map<String, Object> row, BigDecimal totalActualPerPortion) {
        List<String> flags = new ArrayList<>();
        BigDecimal theory = coerceDecimal(row.get("theoryUsage"));
        BigDecimal actual = coerceDecimal(row.get("actualProduceUsage"));
        if (theory != null && theory.compareTo(BigDecimal.ZERO) > 0 && actual != null) {
            BigDecimal diff = actual.subtract(theory).abs();
            if (diff.divide(theory, 4, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(USAGE_ABNORMAL_RATIO)) > 0) {
                flags.add("USAGE_ABNORMAL");
            }
        }
        BigDecimal produceCost = coerceDecimal(row.get("produceCostPerPortion"));
        if (totalActualPerPortion != null
                && totalActualPerPortion.compareTo(BigDecimal.ZERO) > 0
                && produceCost != null) {
            BigDecimal share = produceCost.divide(totalActualPerPortion, 4, RoundingMode.HALF_UP);
            if (share.compareTo(BigDecimal.valueOf(FOCUS_INGREDIENT_COST_SHARE)) >= 0 && flags.contains("USAGE_ABNORMAL")) {
                flags.add("FOCUS_INGREDIENT");
            }
        }
        BigDecimal waste = coerceDecimal(row.get("wasteCostPerPortion"));
        BigDecimal loss = coerceDecimal(row.get("lossCostPerPortion"));
        if ((waste != null && waste.compareTo(BigDecimal.ZERO) > 0)
                || (loss != null && loss.compareTo(BigDecimal.ZERO) > 0)) {
            flags.add("WASTE_OR_LOSS");
        }
        if (!flags.isEmpty()) {
            row.put("reviewFlags", flags);
        }
    }

    private static void fillDiagnosisAndActions(DishProfitPrescriptionAnswerPlan plan, LinkedHashSet<String> gaps) {
        Map<String, Object> margin = plan.getMargin() == null ? Map.of() : plan.getMargin();
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        String verdict = str(margin, "pricingVerdict");
        String primary = "MIXED";
        if ("BELOW".equalsIgnoreCase(verdict)) {
            primary = "BELOW_TARGET_MARGIN";
        } else if (hasHighDiffCost(margin)) {
            primary = "HIGH_COST_DRIFT";
        } else if ("ABOVE".equalsIgnoreCase(verdict)) {
            primary = "ABOVE_TARGET_MARGIN";
        }
        diagnosis.put("primaryIssue", primary);
        diagnosis.put("headlineZh", headlineFor(primary, plan.getDishName()));
        plan.setDiagnosis(diagnosis);

        List<DishProfitPrescriptionRecommendedAction> actions = new ArrayList<>();
        int priority = 1;
        boolean belowMargin = "BELOW".equalsIgnoreCase(verdict) || "BELOW_TARGET_MARGIN".equals(primary);
        boolean highDiff = hasHighDiffCost(margin);

        if (belowMargin && highDiff) {
            actions.add(action(MenuOperationRecommendedAction.RAISE_PRICE, priority++, null, "先复核成本，再判断是否调价"));
        } else if (belowMargin) {
            actions.add(
                    action(
                            MenuOperationRecommendedAction.RAISE_PRICE,
                            priority++,
                            null,
                            "实际毛利率低于目标带，可考虑调价"));
        }

        String focusIngredientName = null;
        if (highDiff) {
            focusIngredientName = findFocusIngredientName(plan.getIngredientRows());
            if (StringUtils.hasText(focusIngredientName)) {
                actions.add(
                        action(
                                MenuOperationRecommendedAction.RECIPE_REVIEW,
                                priority++,
                                null,
                                "重点核对" + focusIngredientName.trim() + "用量和出库记录"));
            } else {
                actions.add(
                        action(
                                MenuOperationRecommendedAction.RECIPE_REVIEW,
                                priority++,
                                null,
                                "实际成本高于理论成本，建议复核配方与出库"));
            }
        }

        List<String> abnormalIngredientNames =
                collectAbnormalIngredientNames(plan.getIngredientRows(), focusIngredientName);
        if (!abnormalIngredientNames.isEmpty()) {
            actions.add(
                    action(
                            MenuOperationRecommendedAction.CHECK_STOCK_REDUCE,
                            priority++,
                            null,
                            buildMergedIngredientActionReason(abnormalIngredientNames)));
        }

        if (actions.isEmpty() && DishProfitPrescriptionAnswerPlan.STATUS_SUCCESS.equals(plan.getStatus())) {
            actions.add(
                    action(MenuOperationRecommendedAction.KEEP_AND_PROMOTE, 1, null, "当前指标在可控范围内，可持续观察"));
        }
        plan.setRecommendedActions(trimActions(actions, 4));
    }

    private static List<DishProfitPrescriptionRecommendedAction> trimActions(
            List<DishProfitPrescriptionRecommendedAction> actions, int maxCount) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, maxCount);
        List<DishProfitPrescriptionRecommendedAction> trimmed =
                actions.size() <= limit ? new ArrayList<>(actions) : new ArrayList<>(actions.subList(0, limit));
        int nextPriority = 1;
        for (DishProfitPrescriptionRecommendedAction item : trimmed) {
            item.setPriority(nextPriority++);
        }
        return trimmed;
    }

    private static String findFocusIngredientName(List<Map<String, Object>> ingredientRows) {
        if (ingredientRows == null || ingredientRows.isEmpty()) {
            return null;
        }
        Map<String, Object> best = null;
        BigDecimal bestCost = null;
        for (Map<String, Object> row : ingredientRows) {
            if (row == null || !hasReviewFlag(row, "USAGE_ABNORMAL", "FOCUS_INGREDIENT")) {
                continue;
            }
            BigDecimal cost = coerceDecimal(row.get("produceCostPerPortion"));
            if (best == null || (cost != null && (bestCost == null || cost.compareTo(bestCost) > 0))) {
                best = row;
                bestCost = cost;
            }
        }
        if (best == null) {
            for (Map<String, Object> row : ingredientRows) {
                if (row == null) {
                    continue;
                }
                BigDecimal cost = coerceDecimal(row.get("produceCostPerPortion"));
                if (best == null || (cost != null && (bestCost == null || cost.compareTo(bestCost) > 0))) {
                    best = row;
                    bestCost = cost;
                }
            }
        }
        return best == null ? null : ingredientDisplayName(best);
    }

    private static List<String> collectAbnormalIngredientNames(
            List<Map<String, Object>> ingredientRows, String excludeName) {
        if (ingredientRows == null || ingredientRows.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Map<String, Object> row : ingredientRows) {
            if (row == null || !hasReviewFlag(row, "USAGE_ABNORMAL", "WASTE_OR_LOSS")) {
                continue;
            }
            String name = ingredientDisplayName(row);
            if (!StringUtils.hasText(name)) {
                continue;
            }
            if (StringUtils.hasText(excludeName) && excludeName.trim().equals(name.trim())) {
                continue;
            }
            names.add(name.trim());
        }
        return new ArrayList<>(names);
    }

    private static boolean hasReviewFlag(Map<String, Object> row, String... flags) {
        Object raw = row.get("reviewFlags");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (String flag : flags) {
            if (list.contains(flag)) {
                return true;
            }
        }
        return false;
    }

    private static String ingredientDisplayName(Map<String, Object> row) {
        return firstNonBlank(str(row, "gbDgGoodsName"), str(row, "goodsName"));
    }

    private static String buildMergedIngredientActionReason(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "建议复核配料用量与损耗";
        }
        if (names.size() == 1) {
            return "建议复核" + names.get(0) + "的用量偏差";
        }
        int showCount = Math.min(3, names.size());
        String joined = String.join("、", names.subList(0, showCount));
        if (names.size() > showCount) {
            return "统一复核" + joined + "等配料用量偏差";
        }
        return "统一复核" + joined + "等配料用量偏差";
    }

    private static boolean hasHighDiffCost(Map<String, Object> margin) {
        BigDecimal diff = coerceDecimal(margin.get("diffCostPerPortion"));
        return diff != null && diff.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String headlineFor(String primary, String dishName) {
        String name = StringUtils.hasText(dishName) ? dishName.trim() : "该菜品";
        return switch (primary) {
            case "BELOW_TARGET_MARGIN" -> name + "当前毛利率低于目标带，建议优先关注定价与成本";
            case "HIGH_COST_DRIFT" -> name + "实际成本高于理论成本，建议复核配方与出库损耗";
            case "ABOVE_TARGET_MARGIN" -> name + "毛利率高于目标带，可评估是否仍有优化空间";
            default -> name + "利润处方分析已完成，详见下方卡片";
        };
    }

    private static DishProfitPrescriptionRecommendedAction action(
            String code, int priority, Integer disGoodsId, String reason) {
        return DishProfitPrescriptionRecommendedAction.builder()
                .actionCode(code)
                .priority(priority)
                .disGoodsId(disGoodsId)
                .reasonZh(reason)
                .build();
    }

    private static List<AiResultAnchor> buildAnchors(Integer dishId, String dishName) {
        if (dishId == null) {
            return List.of();
        }
        return List.of(
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_DISH)
                        .entityId(dishId.toString())
                        .entityName(dishName)
                        .sourcePlanType(DishProfitPrescriptionAnswerPlan.TYPE)
                        .build());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolData(AiRunState state, String toolId) {
        if (state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(toolId);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object data = ((Map<String, Object>) envelope).get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static String statusOf(Map<String, Object> data) {
        return data == null ? null : str(data, "status");
    }

    private static String resolveToolStatus(AiRunState state, String toolId, Map<String, Object> data) {
        String fromData = statusOf(data);
        if (StringUtils.hasText(fromData)) {
            return fromData.trim();
        }
        Map<String, Object> envelope = toolEnvelope(state, toolId);
        if (envelope == null) {
            return null;
        }
        Object success = envelope.get("success");
        if (Boolean.TRUE.equals(success)) {
            return "SUCCESS";
        }
        if (Boolean.FALSE.equals(success)) {
            return "ERROR";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolEnvelope(AiRunState state, String toolId) {
        if (state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(toolId);
        if (raw instanceof Map<?, ?> envelope) {
            return (Map<String, Object>) envelope;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dishRows(Map<String, Object> profitData) {
        if (profitData == null) {
            return List.of();
        }
        Object raw = profitData.get("dishRows");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static Map<String, Object> findProfitRow(Map<String, Object> profitData, Integer dishId) {
        if (profitData == null || dishId == null) {
            return Map.of();
        }
        for (Map<String, Object> row : dishRows(profitData)) {
            if (dishId.equals(parseFoodId(row.get("foodId")))) {
                return row;
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> targetInsightRow(Map<String, Object> profitData, Integer dishId) {
        if (profitData == null) {
            return Map.of();
        }
        Object raw = profitData.get("targetDishInsightRow");
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            return (Map<String, Object>) map;
        }
        return findProfitRow(profitData, dishId);
    }

    private static Integer resolveProfitFoodId(Map<String, Object> profitData, Map<String, Object> costData) {
        Integer fromInsight = parseFoodId(targetInsightRow(profitData, null).get("foodId"));
        if (fromInsight != null) {
            return fromInsight;
        }
        Integer costId = parseFoodId(costData == null ? null : costData.get("dishId"));
        if (costId != null) {
            Map<String, Object> row = findProfitRow(profitData, costId);
            if (!row.isEmpty()) {
                return costId;
            }
        }
        List<Map<String, Object>> rows = dishRows(profitData);
        if (rows.size() == 1) {
            return parseFoodId(rows.get(0).get("foodId"));
        }
        return null;
    }

    private static String requestedTarget(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return null;
        }
        return rq.getQuerySemanticParse().effectiveRequestedTargetGrossMarginRate();
    }

    private static String effectiveDishName(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return null;
        }
        return rq.getQuerySemanticParse().effectiveMentionedDishName();
    }

    private static String scopeLabel(AiResolvedQueryContext rq) {
        if (rq == null || !StringUtils.hasText(rq.getQueryScopeBanner())) {
            return null;
        }
        return rq.getQueryScopeBanner().trim();
    }

    private static String timeLabel(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        if (tw != null && StringUtils.hasText(tw.getDisplayTimeRange())) {
            return tw.getDisplayTimeRange().trim();
        }
        if (state == null) {
            return null;
        }
        String start = state.getStatStartDate();
        String end = state.getStatEndDate();
        if (StringUtils.hasText(start) && StringUtils.hasText(end)) {
            return start.trim() + " 至 " + end.trim();
        }
        return null;
    }

    private static Integer parseFoodId(Object raw) {
        return DishProfitPrescriptionRankSupport.parseFoodId(raw);
    }

    private static int parseInt(Object raw, int defaultVal) {
        if (raw == null) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static BigDecimal coerceDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(raw);
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
