package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioCategory;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioClassification;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuPortfolioDish;
import com.nongxinle.ai.dto.business.MenuOperationRecommendedAction;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.matrix.MenuOperationSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.MenuOperationSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 挂载 {@link MenuOperationAnswerPlan}：仅 {@link AiResolvedQueryIntent#PATH_MENU_OPERATION}；
 * 只消费 {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS} 快照。
 */
public final class MenuOperationAnswerPlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(MenuOperationAnswerPlanBuilder.class);

    private static final int TOP_N_FOCUS = 5;
    private static final int TOP_N_RISK = 5;
    private static final int TOP_N_OPTIMIZATION = 5;
    private static final int TOP_N_NEXT_STEPS = 3;
    /** 销量头部阈值：按售出份数降序，取前 30% 分位（含），至少 2 道。 */
    private static final double SALES_HEAD_PERCENTILE = 0.30;
    /** 毛利率尾部阈值：按综合毛利率升序，取后 30% 分位（含），至少 2 道。 */
    private static final double MARGIN_TAIL_PERCENTILE = 0.30;
    private static final int MIN_PERCENTILE_BUCKET = 2;
    private static final int MAX_HIGH_SALES_LOW_MARGIN_MATCH = 5;
    private static final int FALLBACK_LOW_MARGIN_MIN = 2;
    private static final int FALLBACK_LOW_MARGIN_MAX = 3;
    private static final String MATCH_MODE_HIGH_SALES_LOW_MARGIN = "HIGH_SALES_LOW_MARGIN";
    private static final String MATCH_MODE_LOW_MARGIN_FALLBACK = "LOW_MARGIN_FALLBACK";
    private static final String SUMMARY_HIGH_SALES_LOW_MARGIN =
            "本月有 %d 道菜销量靠前但毛利效率偏低，建议优先复核成本、份量和定价。";
    private static final String SUMMARY_LOW_MARGIN_FALLBACK =
            "本期未发现特别典型的畅销低利菜，但以下菜品毛利率相对偏低，建议优先复核。";
    private static final String SUMMARY_NO_DISHES = "本期暂无可分析的在售菜品。";
    /** 四象限分类：样本少于该数时在 knownGaps 标注仅供参考。 */
    private static final int PORTFOLIO_MIN_SAMPLE = 4;

    private static final String SALES_METRIC = "soldPortionsTotal";
    private static final String PROFIT_METRIC = "actualProfitAmount";
    private static final String THRESHOLD_METHOD_MEDIAN = "median";

    private MenuOperationAnswerPlanBuilder() {}

    public static void attachIfApplicable(AiRunState state) {
        if (state == null || !state.isMenuOperationPath()) {
            return;
        }
        state.setMenuOperationAnswerPlan(null);
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return;
        }

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("sourceTool", AiBusinessToolIds.DISH_PROFIT_ANALYSIS);

        if (!toolEnvelopeSuccess(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS)) {
            debug.put("earlyReturnReason", "tool_envelope_missing_or_unsuccessful");
            attachEarlyExit(state, debug, List.of("菜品毛利工具未成功返回，无法生成菜单经营计划。"));
            return;
        }

        WireResolution wireRes = resolveMenuOperationWire(rq);
        debug.put("rawStructuredIntentDetail", wireRes.raw());
        debug.put("resolvedMenuOperationWire", wireRes.wire());
        if (wireRes.rejectReason() != null) {
            debug.put("wireRejectedReason", wireRes.rejectReason());
        }

        String wire = wireRes.wire();
        if (!StringUtils.hasText(wire)) {
            debug.put("earlyReturnReason", wireRes.rejectReason() != null ? wireRes.rejectReason() : "no_wire");
            attachEarlyExit(state, debug, List.of("未从 contract 解析到有效的菜单经营 wire。"));
            return;
        }

        MenuOperationSemanticCapabilityMatrixRow matrixRow =
                MenuOperationSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_MENU_OPERATION, wire, semantic(rq));
        if (matrixRow == null) {
            debug.put("menuOperationMatrixWireMissing", MenuOperationSemanticCapabilityMatrix.MATRIX_WIRE_MISSING);
            attachEarlyExit(state, debug, List.of("wire 未命中 MenuOperation 能力矩阵。"));
            return;
        }
        debug.put("matrixRowId", matrixRow.getRowId());
        debug.put("menuOperationAnswerPlanType", matrixRow.getTargetMenuOperationPlanType());

        Map<String, Object> toolData = toolEnvelopeData(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        List<Map<String, Object>> dishRowsRaw = extractDishRows(toolData);
        List<Map<String, Object>> dishRows = aggregateDishRowsForPortfolio(dishRowsRaw);
        Map<String, Object> insight = businessInsightSummary(toolData);
        debug.put("dishRowsCountRaw", dishRowsRaw.size());
        debug.put("dishRowsCount", dishRows.size());
        if (dishRowsRaw.size() > dishRows.size()) {
            debug.put("dishRowsPortfolioAggregated", true);
            debug.put("dishRowsPortfolioAggregateMergedCount", dishRowsRaw.size() - dishRows.size());
        }

        List<Map<String, Object>> evidenceRows = new ArrayList<>();
        AtomicInteger evidenceSeq = new AtomicInteger(1);
        enrichPortfolioEvidence(evidenceRows, evidenceSeq, insight, toolData);

        String planType = matrixRow.getTargetMenuOperationPlanType();
        MenuOperationAnswerPlan plan;
        if (MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT.equals(planType)) {
            plan = buildHighSalesLowProfit(state, rq, dishRows, insight, evidenceRows, evidenceSeq, debug);
        } else if (MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION.equals(planType)) {
            plan = buildActionRecommendation(state, rq, dishRows, insight, evidenceRows, evidenceSeq, debug);
        } else {
            plan = buildOverview(state, rq, dishRows, insight, evidenceRows, evidenceSeq, debug);
        }
        plan.setPlanType(planType);
        plan.setDebug(debug);
        state.setMenuOperationAnswerPlan(plan);
        log.info(
                "[MenuOperationAnswerPlan] attached runId={} planType={} focus={} risk={} actions={}",
                state.getRunId(),
                planType,
                plan.getFocusDishes() == null ? 0 : plan.getFocusDishes().size(),
                plan.getRiskDishes() == null ? 0 : plan.getRiskDishes().size(),
                plan.getRecommendedActions() == null ? 0 : plan.getRecommendedActions().size());
    }

    private static MenuOperationAnswerPlan buildOverview(
            AiRunState state,
            AiResolvedQueryContext rq,
            List<Map<String, Object>> dishRows,
            Map<String, Object> insight,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq,
            LinkedHashMap<String, Object> debug) {
        LinkedHashMap<String, Object> summaryFacts = buildSummaryFacts(dishRows, insight);
        List<DishMetrics> metrics = toMetrics(dishRows);
        BigDecimal refMargin = parseDecimal(insight.get("comprehensiveGrossMarginRateOnListPrice"));
        MenuPortfolioSalesEvidenceSupport.Assessment salesEvidence =
                MenuPortfolioSalesEvidenceSupport.assess(dishRows, insight);
        MenuPortfolioSalesEvidenceSupport.writeEvidenceDebug(salesEvidence, debug, summaryFacts);
        List<Map<String, Object>> focus = new ArrayList<>();
        List<Map<String, Object>> risk = new ArrayList<>();
        List<Map<String, Object>> opportunity = new ArrayList<>();

        metrics.stream()
                .filter(DishMetrics::hasPositiveProfit)
                .sorted(Comparator.comparing(DishMetrics::actualProfit).reversed())
                .limit(TOP_N_FOCUS)
                .forEach(m -> focus.add(m.toFocusRow()));

        metrics.stream()
                .filter(m -> isRiskDish(m, refMargin))
                .sorted(Comparator.comparing(DishMetrics::actualProfit))
                .limit(TOP_N_RISK)
                .forEach(m -> risk.add(m.toRiskRow()));

        metrics.stream()
                .filter(m -> m.soldPortions().compareTo(BigDecimal.ZERO) > 0)
                .filter(m -> m.blendedMargin().compareTo(refMargin) > 0)
                .sorted(Comparator.comparing(DishMetrics::blendedMargin).reversed())
                .limit(3)
                .forEach(m -> opportunity.add(m.toOpportunityRow()));

        summaryFacts.put("riskDishCount", risk.size());
        summaryFacts.put("focusDishCount", focus.size());

        List<MenuOperationRecommendedAction> actions = new ArrayList<>();
        if (!focus.isEmpty()) {
            DishMetrics top = metrics.stream()
                    .filter(DishMetrics::hasPositiveProfit)
                    .max(Comparator.comparing(DishMetrics::actualProfit))
                    .orElse(null);
            if (top != null) {
                String eid = addDishEvidence(evidenceRows, evidenceSeq, top, "focus_head_profit");
                actions.add(actionWithEvidence(
                        MenuOperationRecommendedAction.KEEP_AND_PROMOTE,
                        1,
                        top.foodId(),
                        "HEAD_PROFIT_DISH",
                        List.of(eid)));
            }
        }
        for (DishMetrics m : metrics.stream().filter(m -> isRiskDish(m, refMargin)).limit(3).toList()) {
            String eid = addDishEvidence(evidenceRows, evidenceSeq, m, "risk_low_margin");
            String code = m.actualProfit().compareTo(BigDecimal.ZERO) < 0
                    ? MenuOperationRecommendedAction.RAISE_PRICE
                    : MenuOperationRecommendedAction.REDUCE_COST;
            actions.add(actionWithEvidence(code, 2, m.foodId(), "LOW_MARGIN_OR_LOSS", List.of(eid)));
        }

        debug.put("recommendedActionCount", actions.size());

        MenuPortfolioClassification portfolio = null;
        List<String> gaps = new ArrayList<>(overviewKnownGaps());
        if (salesEvidence.salesEvidenceAvailable()) {
            portfolio =
                    buildMenuPortfolioClassification(metrics, evidenceRows, evidenceSeq, debug, refMargin);
            if (portfolio != null && metrics.size() < PORTFOLIO_MIN_SAMPLE) {
                gaps.add("MENU_PORTFOLIO_CLASSIFICATION_SMALL_SAMPLE");
            }
        } else {
            debug.put("menuPortfolioClassificationSkipped", "no_dish_sales_for_period");
            gaps.add(MenuPortfolioSalesEvidenceSupport.KNOWN_GAP_NO_SALES);
        }

        return MenuOperationAnswerPlan.builder()
                .timeLabel(timeLabel(state))
                .scopeLabel(scopeLabel(rq))
                .statStartDate(state.getStatStartDate())
                .statEndDate(state.getStatEndDate())
                .summaryFacts(summaryFacts)
                .focusDishes(focus)
                .riskDishes(risk)
                .opportunityDishes(opportunity)
                .recommendedActions(dedupeActions(actions))
                .evidenceRows(evidenceRows)
                .knownGaps(gaps)
                .menuPortfolioClassification(portfolio)
                .displayCards(buildPortfolioDisplayCards(portfolio, salesEvidence))
                .build();
    }

    /** overview：有销量证据时挂四象限卡；无销量时挂 EMPTY 态四象限卡占位。 */
    private static List<MenuOperationAnswerPlan.MenuOperationDisplayCard> buildPortfolioDisplayCards(
            MenuPortfolioClassification portfolio,
            MenuPortfolioSalesEvidenceSupport.Assessment salesEvidence) {
        if (portfolio != null) {
            return buildPortfolioDisplayCards(portfolio);
        }
        if (salesEvidence != null && !salesEvidence.salesEvidenceAvailable()) {
            return List.of(
                    MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                            .cardType(MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT)
                            .title("菜单结构四象限")
                            .subtitle(MenuPortfolioSalesEvidenceSupport.EMPTY_PORTFOLIO_MESSAGE)
                            .chartType(MenuOperationAnswerPlan.CHART_TYPE_PIE)
                            .dataRef(MenuOperationAnswerPlan.DATA_REF_MENU_PORTFOLIO_CLASSIFICATION)
                            .build());
        }
        return List.of();
    }

    /** overview 有四象限数据时挂展示卡片描述；cardType 不参与业务判断。 */
    private static List<MenuOperationAnswerPlan.MenuOperationDisplayCard> buildPortfolioDisplayCards(
            MenuPortfolioClassification portfolio) {
        if (portfolio == null) {
            return List.of();
        }
        return List.of(
                MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                        .cardType(MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT)
                        .title("菜单结构四象限")
                        .subtitle("本轮菜单内相对分层（销量与实际利润中位数阈值，非绝对行业标准）")
                        .chartType(MenuOperationAnswerPlan.CHART_TYPE_PIE)
                        .dataRef(MenuOperationAnswerPlan.DATA_REF_MENU_PORTFOLIO_CLASSIFICATION)
                        .build());
    }

    /**
     * 菜单四象限：销量（soldPortionsTotal）× 盈利（actualProfitAmount，type123 实际利润）。
     * 阈值取本轮菜品集合中位数；Renderer / Composer 不得重算。
     */
    private static MenuPortfolioClassification buildMenuPortfolioClassification(
            List<DishMetrics> metrics,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq,
            LinkedHashMap<String, Object> debug,
            BigDecimal refMargin) {
        if (metrics == null || metrics.isEmpty()) {
            debug.put("menuPortfolioClassificationSkipped", "no_dish_rows");
            return null;
        }

        List<BigDecimal> salesValues = new ArrayList<>();
        List<BigDecimal> profitValues = new ArrayList<>();
        for (DishMetrics m : metrics) {
            salesValues.add(m.soldPortions());
            profitValues.add(m.actualProfit());
        }
        BigDecimal salesThreshold = median(salesValues);
        BigDecimal profitThreshold = median(profitValues);

        debug.put("portfolioClassificationSalesMetric", SALES_METRIC);
        debug.put("portfolioClassificationProfitMetric", PROFIT_METRIC);
        debug.put("portfolioClassificationThresholdMethod", THRESHOLD_METHOD_MEDIAN);
        debug.put(
                "portfolioClassificationInterpretation",
                "本轮分析菜品集合内的相对四象限分层（销量/实际利润中位数阈值），非绝对行业标准；"
                        + "ELIMINATE 档为相对观察档，不代表建议下架。");
        debug.put("portfolioClassificationStarRequiresNonNegativeProfit", true);
        debug.put("portfolioClassificationSalesHighThreshold", formatAmount(salesThreshold));
        debug.put("portfolioClassificationProfitHighThreshold", formatAmount(profitThreshold));
        debug.put("portfolioClassificationDishCount", metrics.size());
        if (metrics.size() < PORTFOLIO_MIN_SAMPLE) {
            debug.put("portfolioClassificationSmallSample", true);
        }

        EnumMap<PortfolioQuadrant, List<DishMetrics>> buckets = new EnumMap<>(PortfolioQuadrant.class);
        for (PortfolioQuadrant q : PortfolioQuadrant.values()) {
            buckets.put(q, new ArrayList<>());
        }
        for (DishMetrics m : metrics) {
            PortfolioQuadrant q = resolveQuadrant(m, salesThreshold, profitThreshold);
            buckets.get(q).add(m);
        }

        int total = metrics.size();
        List<MenuPortfolioCategory> categories = new ArrayList<>();
        for (PortfolioQuadrant q : PortfolioQuadrant.displayOrder()) {
            List<DishMetrics> inBucket = buckets.get(q);
            inBucket.sort(
                    Comparator.comparing(DishMetrics::soldPortions)
                            .reversed()
                            .thenComparing(DishMetrics::actualProfit, Comparator.reverseOrder()));
            List<MenuPortfolioDish> dishItems = new ArrayList<>();
            for (DishMetrics m : inBucket) {
                String eid = addDishEvidence(evidenceRows, evidenceSeq, m, "portfolio_" + q.code().toLowerCase());
                dishItems.add(
                        MenuPortfolioDish.builder()
                                .dishId(m.foodId())
                                .dishName(StringUtils.hasText(m.dishName()) ? m.dishName() : "（未命名菜品）")
                                .salesCount(formatAmount(m.soldPortions()))
                                .salesAmount(formatAmount(m.listPriceRevenue()))
                                .blendedGrossMarginRateOnListPrice(formatRate(m.blendedMargin()))
                                .actualProfitAmount(formatAmount(m.actualProfit()))
                                .actualCostTotalAmount123(formatAmount(m.actualCost123()))
                                .reason(
                                        q == PortfolioQuadrant.ELIMINATE
                                                ? buildEliminateDishReason(
                                                        m, salesThreshold, profitThreshold, refMargin)
                                                : buildPortfolioDishReason(
                                                        m, salesThreshold, profitThreshold, q))
                                .evidenceRefId(eid)
                                .build());
            }
            int count = inBucket.size();
            categories.add(
                    MenuPortfolioCategory.builder()
                            .categoryCode(q.code())
                            .categoryName(q.displayName())
                            .count(count)
                            .ratio(formatRatio(count, total))
                            .summary(q.categorySummary(count, total))
                            .recommendedAction(q.recommendedAction())
                            .dishes(dishItems)
                            .build());
        }

        return MenuPortfolioClassification.builder()
                .totalDishCount(total)
                .salesMetricName(SALES_METRIC)
                .profitMetricName(PROFIT_METRIC)
                .salesHighThreshold(formatAmount(salesThreshold))
                .profitHighThreshold(formatAmount(profitThreshold))
                .thresholdMethod(THRESHOLD_METHOD_MEDIAN)
                .categories(categories)
                .build();
    }

    /**
     * 四象限相对分层：中位数切分；明星档额外要求 actualProfitAmount &gt;= 0。
     */
    private static PortfolioQuadrant resolveQuadrant(
            DishMetrics m, BigDecimal salesThreshold, BigDecimal profitThreshold) {
        boolean highSales = m.soldPortions().compareTo(salesThreshold) >= 0;
        boolean highProfit = m.actualProfit().compareTo(profitThreshold) >= 0;
        if (highSales) {
            if (highProfit && m.actualProfit().compareTo(BigDecimal.ZERO) >= 0) {
                return PortfolioQuadrant.STAR;
            }
            return PortfolioQuadrant.TRAFFIC;
        }
        return highProfit ? PortfolioQuadrant.POTENTIAL : PortfolioQuadrant.ELIMINATE;
    }

    private static String buildPortfolioDishReason(
            DishMetrics m,
            BigDecimal salesThreshold,
            BigDecimal profitThreshold,
            PortfolioQuadrant assigned) {
        boolean highSales = m.soldPortions().compareTo(salesThreshold) >= 0;
        boolean highProfit = m.actualProfit().compareTo(profitThreshold) >= 0;
        String salesSide = highSales ? "销量高于本轮中位" : "销量低于本轮中位";
        String profitSide =
                highProfit
                        ? "实际利润不低于本轮中位（type123）"
                        : "实际利润低于本轮中位（type123）";
        if (highSales
                && highProfit
                && assigned == PortfolioQuadrant.TRAFFIC
                && m.actualProfit().compareTo(BigDecimal.ZERO) < 0) {
            return salesSide + "，" + profitSide + "；但实际利润为负，归入相对引流档而非明星档";
        }
        return salesSide + "，" + profitSide;
    }

    /**
     * ELIMINATE 档内按利润/毛利率轻重分层：正利且毛利率未明显偏低 → 观察；毛利偏低 → 重点复核；亏损 → 考虑下架或重做。
     * P1 无多期趋势，不下「立即淘汰」类强动作。
     */
    private static String buildEliminateDishReason(
            DishMetrics m,
            BigDecimal salesThreshold,
            BigDecimal profitThreshold,
            BigDecimal refMargin) {
        String base =
                buildPortfolioDishReason(m, salesThreshold, profitThreshold, PortfolioQuadrant.ELIMINATE);
        EliminateObservationTier tier = resolveEliminateObservationTier(m, refMargin);
        return base + "；" + tier.dishReasonSuffix();
    }

    private enum EliminateObservationTier {
        SOFT_OBSERVE("观察调整", "仍有正利润且毛利率未明显低于整体，建议先观察一个周期，可增加曝光或复核菜单位置"),
        KEY_REVIEW("重点复核", "毛利率明显低于整体或利润贡献偏弱，建议重点复核成本、定价与备货"),
        CONSIDER_DELIST("考虑下架或重做", "实际利润为负；P1 无多期趋势，仅建议复核是否下架或调整配方，不宜单凭一轮数据武断淘汰");

        private final String actionLabel;
        private final String dishReasonSuffix;

        EliminateObservationTier(String actionLabel, String dishReasonSuffix) {
            this.actionLabel = actionLabel;
            this.dishReasonSuffix = dishReasonSuffix;
        }

        String actionLabel() {
            return actionLabel;
        }

        String dishReasonSuffix() {
            return dishReasonSuffix;
        }
    }

    private static EliminateObservationTier resolveEliminateObservationTier(
            DishMetrics m, BigDecimal refMargin) {
        if (m.actualProfit().compareTo(BigDecimal.ZERO) <= 0) {
            return EliminateObservationTier.CONSIDER_DELIST;
        }
        if (refMargin.compareTo(BigDecimal.ZERO) > 0 && m.blendedMargin().compareTo(refMargin) < 0) {
            return EliminateObservationTier.KEY_REVIEW;
        }
        return EliminateObservationTier.SOFT_OBSERVE;
    }

    private enum PortfolioQuadrant {
        STAR(CATEGORY_STAR, "明星菜", "稳定供应、继续主推"),
        TRAFFIC(CATEGORY_TRAFFIC, "引流菜", "保留流量价值，优先降本或适度提价"),
        POTENTIAL(CATEGORY_POTENTIAL, "潜力菜", "增加曝光、放到推荐位、尝试套餐"),
        ELIMINATE(
                CATEGORY_ELIMINATE,
                "观察菜",
                "相对低销量且实际利润低于本轮中位，建议先观察并复核曝光与备货（不等同建议下架）");

        private final String code;
        private final String displayName;
        private final String recommendedAction;

        PortfolioQuadrant(String code, String displayName, String recommendedAction) {
            this.code = code;
            this.displayName = displayName;
            this.recommendedAction = recommendedAction;
        }

        String code() {
            return code;
        }

        String displayName() {
            return displayName;
        }

        String recommendedAction() {
            return recommendedAction;
        }

        static List<PortfolioQuadrant> displayOrder() {
            return List.of(STAR, TRAFFIC, POTENTIAL, ELIMINATE);
        }

        String categorySummary(int count, int total) {
            if (count <= 0) {
                return "本轮菜单内相对分类：" + displayName + "暂无菜品";
            }
            return "本轮菜单内相对分类：" + displayName + " " + count + " 道，占分析菜品 " + formatRatio(count, total);
        }
    }

    private static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        BigDecimal a = sorted.get(n / 2 - 1);
        BigDecimal b = sorted.get(n / 2);
        return a.add(b).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private static String formatRatio(int count, int total) {
        if (total <= 0) {
            return "0%";
        }
        BigDecimal pct =
                BigDecimal.valueOf(count)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return pct.toPlainString() + "%";
    }

    private static final String CATEGORY_STAR = MenuOperationAnswerPlan.CATEGORY_STAR;
    private static final String CATEGORY_TRAFFIC = MenuOperationAnswerPlan.CATEGORY_TRAFFIC;
    private static final String CATEGORY_POTENTIAL = MenuOperationAnswerPlan.CATEGORY_POTENTIAL;
    private static final String CATEGORY_ELIMINATE = MenuOperationAnswerPlan.CATEGORY_ELIMINATE;

    private static MenuOperationAnswerPlan buildHighSalesLowProfit(
            AiRunState state,
            AiResolvedQueryContext rq,
            List<Map<String, Object>> dishRows,
            Map<String, Object> insight,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq,
            LinkedHashMap<String, Object> debug) {
        LinkedHashMap<String, Object> summaryFacts = buildSummaryFacts(dishRows, insight);
        List<DishMetrics> metrics = toMetrics(dishRows);
        BigDecimal refMargin = parseDecimal(insight.get("comprehensiveGrossMarginRateOnListPrice"));
        MenuPortfolioSalesEvidenceSupport.Assessment salesEvidence =
                MenuPortfolioSalesEvidenceSupport.assess(dishRows, insight);
        MenuPortfolioSalesEvidenceSupport.writeEvidenceDebug(salesEvidence, debug, summaryFacts);

        HighSalesLowMarginSelection selection = selectHighSalesLowMarginDishes(metrics, refMargin);
        List<Map<String, Object>> risk = selection.riskRows();
        List<DishMetrics> matchedMetrics = selection.matchedMetrics();

        summaryFacts.put("highSalesLowProfitCount", risk.size());
        summaryFacts.put("highSalesLowMarginMatchMode", selection.matchMode());
        summaryFacts.put("highSalesLowMarginSummary", selection.summary());
        summaryFacts.put("salesHeadThresholdPercent", (int) (SALES_HEAD_PERCENTILE * 100));
        summaryFacts.put("marginTailThresholdPercent", (int) (MARGIN_TAIL_PERCENTILE * 100));

        List<MenuOperationRecommendedAction> actions = new ArrayList<>();
        for (DishMetrics m : matchedMetrics) {
            String eid = addDishEvidence(evidenceRows, evidenceSeq, m, "high_sales_low_profit");
            List<String> refs = new ArrayList<>();
            refs.add(eid);
            String rationale =
                    m.actualProfit().compareTo(BigDecimal.ZERO) <= 0
                            ? "NEGATIVE_ACTUAL_PROFIT"
                            : MATCH_MODE_LOW_MARGIN_FALLBACK.equals(selection.matchMode())
                                    ? "LOW_MARGIN_FALLBACK"
                                    : "HIGH_SALES_LOW_MARGIN";
            if (m.actualProfit().compareTo(BigDecimal.ZERO) <= 0) {
                actions.add(actionWithEvidence(
                        MenuOperationRecommendedAction.RAISE_PRICE, 1, m.foodId(), rationale, refs));
            } else {
                actions.add(actionWithEvidence(
                        MenuOperationRecommendedAction.REDUCE_COST, 1, m.foodId(), rationale, refs));
            }
        }

        debug.put("highSalesLowProfitMatched", matchedMetrics.size());
        debug.put("highSalesLowMarginMatchMode", selection.matchMode());
        debug.put("highSalesLowMarginThresholdDebug", selection.thresholdDebug());
        debug.put("recommendedActionCount", actions.size());

        return MenuOperationAnswerPlan.builder()
                .timeLabel(timeLabel(state))
                .scopeLabel(scopeLabel(rq))
                .statStartDate(state.getStatStartDate())
                .statEndDate(state.getStatEndDate())
                .summaryFacts(summaryFacts)
                .focusDishes(List.of())
                .riskDishes(risk)
                .opportunityDishes(List.of())
                .recommendedActions(dedupeActions(actions))
                .evidenceRows(evidenceRows)
                .knownGaps(highSalesKnownGaps())
                .displayCards(buildHighSalesLowMarginDisplayCards())
                .build();
    }

    /**
     * 畅销低利：销量前 30% ∩（毛利率后 30% 或低于菜单综合毛利率或实际利润≤0）；无典型命中则取毛利率最低 2～3 道 fallback。
     */
    private static HighSalesLowMarginSelection selectHighSalesLowMarginDishes(
            List<DishMetrics> metrics, BigDecimal refMargin) {
        List<DishMetrics> withSales =
                metrics.stream()
                        .filter(m -> m.soldPortions().compareTo(BigDecimal.ZERO) > 0)
                        .sorted(Comparator.comparing(DishMetrics::soldPortions).reversed())
                        .toList();
        int totalWithSales = withSales.size();
        LinkedHashMap<String, Object> thresholdDebug = new LinkedHashMap<>();
        thresholdDebug.put("salesHeadPercentile", (int) (SALES_HEAD_PERCENTILE * 100));
        thresholdDebug.put("marginTailPercentile", (int) (MARGIN_TAIL_PERCENTILE * 100));
        thresholdDebug.put("comprehensiveGrossMarginRateOnListPrice", formatRate(refMargin));
        thresholdDebug.put("totalDishCountWithSales", totalWithSales);

        if (totalWithSales == 0) {
            thresholdDebug.put("matchMode", MATCH_MODE_LOW_MARGIN_FALLBACK);
            thresholdDebug.put("matchedHighSalesLowMarginCount", 0);
            return new HighSalesLowMarginSelection(
                    MATCH_MODE_LOW_MARGIN_FALLBACK, SUMMARY_NO_DISHES, List.of(), List.of(), thresholdDebug);
        }

        List<DishMetrics> byMargin =
                withSales.stream()
                        .sorted(
                                Comparator.comparing(DishMetrics::blendedMargin)
                                        .thenComparing(DishMetrics::soldPortions))
                        .toList();

        int salesHeadCount = percentileBucketCount(totalWithSales, SALES_HEAD_PERCENTILE);
        int marginTailCount = percentileBucketCount(totalWithSales, MARGIN_TAIL_PERCENTILE);
        thresholdDebug.put("salesHeadCount", salesHeadCount);
        thresholdDebug.put("marginTailCount", marginTailCount);

        LinkedHashSet<String> salesHeadIds = new LinkedHashSet<>();
        for (int i = 0; i < salesHeadCount; i++) {
            salesHeadIds.add(withSales.get(i).foodId());
        }
        LinkedHashSet<String> marginTailIds = new LinkedHashSet<>();
        for (int i = 0; i < marginTailCount; i++) {
            marginTailIds.add(byMargin.get(i).foodId());
        }

        Map<String, Integer> salesRankById = new LinkedHashMap<>();
        for (int i = 0; i < withSales.size(); i++) {
            salesRankById.put(withSales.get(i).foodId(), i + 1);
        }

        List<DishMetrics> matched = new ArrayList<>();
        for (DishMetrics m : withSales) {
            if (!salesHeadIds.contains(m.foodId())) {
                continue;
            }
            boolean inMarginTail = marginTailIds.contains(m.foodId());
            boolean belowRef =
                    refMargin.compareTo(BigDecimal.ZERO) > 0
                            && m.blendedMargin().compareTo(refMargin) < 0;
            boolean loss = m.actualProfit().compareTo(BigDecimal.ZERO) <= 0;
            if (inMarginTail || belowRef || loss) {
                matched.add(m);
            }
        }
        matched.sort(
                Comparator.comparing(DishMetrics::soldPortions)
                        .reversed()
                        .thenComparing(DishMetrics::blendedMargin));

        if (!matched.isEmpty()) {
            List<DishMetrics> limited =
                    matched.size() > MAX_HIGH_SALES_LOW_MARGIN_MATCH
                            ? matched.subList(0, MAX_HIGH_SALES_LOW_MARGIN_MATCH)
                            : matched;
            List<Map<String, Object>> riskRows = new ArrayList<>();
            for (DishMetrics m : limited) {
                riskRows.add(
                        buildHighSalesLowMarginRiskRow(
                                m,
                                MATCH_MODE_HIGH_SALES_LOW_MARGIN,
                                salesRankById.getOrDefault(m.foodId(), 0),
                                totalWithSales,
                                salesHeadCount));
            }
            thresholdDebug.put("matchMode", MATCH_MODE_HIGH_SALES_LOW_MARGIN);
            thresholdDebug.put("matchedHighSalesLowMarginCount", limited.size());
            String summary = String.format(SUMMARY_HIGH_SALES_LOW_MARGIN, limited.size());
            return new HighSalesLowMarginSelection(
                    MATCH_MODE_HIGH_SALES_LOW_MARGIN, summary, riskRows, limited, thresholdDebug);
        }

        int fallbackCount =
                Math.min(
                        totalWithSales,
                        Math.max(FALLBACK_LOW_MARGIN_MIN, Math.min(FALLBACK_LOW_MARGIN_MAX, totalWithSales)));
        List<DishMetrics> fallback = byMargin.subList(0, fallbackCount);
        List<Map<String, Object>> riskRows = new ArrayList<>();
        for (DishMetrics m : fallback) {
            riskRows.add(
                    buildHighSalesLowMarginRiskRow(
                            m,
                            MATCH_MODE_LOW_MARGIN_FALLBACK,
                            salesRankById.getOrDefault(m.foodId(), 0),
                            totalWithSales,
                            salesHeadCount));
        }
        thresholdDebug.put("matchMode", MATCH_MODE_LOW_MARGIN_FALLBACK);
        thresholdDebug.put("matchedHighSalesLowMarginCount", 0);
        thresholdDebug.put("fallbackLowMarginCount", fallbackCount);
        return new HighSalesLowMarginSelection(
                MATCH_MODE_LOW_MARGIN_FALLBACK,
                SUMMARY_LOW_MARGIN_FALLBACK,
                riskRows,
                fallback,
                thresholdDebug);
    }

    private static int percentileBucketCount(int total, double percentile) {
        if (total <= 0) {
            return 0;
        }
        return Math.min(total, Math.max(MIN_PERCENTILE_BUCKET, (int) Math.ceil(total * percentile)));
    }

    private static Map<String, Object> buildHighSalesLowMarginRiskRow(
            DishMetrics m,
            String matchMode,
            int salesRank,
            int totalWithSales,
            int salesHeadCount) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>(m.dishRowMap("risk"));
        if (salesRank > 0) {
            row.put("salesRank", salesRank);
        }
        row.put("salesLevelDescription", describeSalesLevel(salesRank, totalWithSales, salesHeadCount));
        if (m.actualProfit().compareTo(BigDecimal.ZERO) <= 0) {
            row.put("profitOutcome", "LOSS");
            row.put("riskReason", "实际利润为负，需要优先处理");
            row.put("riskReasonCode", "NEGATIVE_ACTUAL_PROFIT");
        } else if (MATCH_MODE_HIGH_SALES_LOW_MARGIN.equals(matchMode)) {
            row.put("profitOutcome", "LOW_MARGIN_EFFICIENCY");
            row.put("riskReason", "销量靠前但毛利率偏低，建议复核成本、份量和定价");
            row.put("riskReasonCode", "HIGH_SALES_LOW_MARGIN");
        } else {
            row.put("profitOutcome", "LOW_MARGIN_EFFICIENCY");
            row.put("riskReason", "毛利率处于本轮菜单偏低位置，销量不一定高，建议先复核成本结构");
            row.put("riskReasonCode", "LOW_MARGIN_FALLBACK");
        }
        return row;
    }

    private static String describeSalesLevel(int salesRank, int totalWithSales, int salesHeadCount) {
        if (salesRank <= 0 || totalWithSales <= 0) {
            return "销量数据不足";
        }
        if (salesRank <= salesHeadCount) {
            return "销量第" + salesRank + "/" + totalWithSales + "，处于前30%畅销档";
        }
        return "销量第" + salesRank + "/" + totalWithSales + "，未进入前30%畅销档";
    }

    private record HighSalesLowMarginSelection(
            String matchMode,
            String summary,
            List<Map<String, Object>> riskRows,
            List<DishMetrics> matchedMetrics,
            Map<String, Object> thresholdDebug) {}

    /** action_recommendation：菜单优化方案主链；四象限分层 + 优先级分组 + nextSteps。 */
    private static MenuOperationAnswerPlan buildActionRecommendation(
            AiRunState state,
            AiResolvedQueryContext rq,
            List<Map<String, Object>> dishRows,
            Map<String, Object> insight,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq,
            LinkedHashMap<String, Object> debug) {
        LinkedHashMap<String, Object> summaryFacts = buildSummaryFacts(dishRows, insight);
        List<DishMetrics> metrics = toMetrics(dishRows);
        BigDecimal refMargin = parseDecimal(insight.get("comprehensiveGrossMarginRateOnListPrice"));
        MenuPortfolioSalesEvidenceSupport.Assessment salesEvidence =
                MenuPortfolioSalesEvidenceSupport.assess(dishRows, insight);
        MenuPortfolioSalesEvidenceSupport.writeEvidenceDebug(salesEvidence, debug, summaryFacts);

        MenuPortfolioClassification portfolio = null;
        List<String> gaps = new ArrayList<>(actionRecommendationKnownGaps());
        if (salesEvidence.salesEvidenceAvailable()) {
            portfolio =
                    buildMenuPortfolioClassification(metrics, evidenceRows, evidenceSeq, debug, refMargin);
            if (portfolio != null && metrics.size() < PORTFOLIO_MIN_SAMPLE) {
                gaps.add("MENU_PORTFOLIO_CLASSIFICATION_SMALL_SAMPLE");
            }
        } else {
            debug.put("menuPortfolioClassificationSkipped", "no_dish_sales_for_period");
            gaps.add(MenuPortfolioSalesEvidenceSupport.KNOWN_GAP_NO_SALES);
        }

        Map<String, DishMetrics> metricsById = indexMetricsByFoodId(metrics);
        List<MenuOptimizationDishItem> costReviewDishes =
                buildCostReviewDishes(portfolio, metricsById, refMargin, evidenceRows, evidenceSeq);
        List<MenuOptimizationDishItem> protectDishes =
                buildProtectDishes(portfolio, metricsById, evidenceRows, evidenceSeq);
        List<MenuOptimizationDishItem> promotionDishes =
                buildPromotionDishes(portfolio, metricsById, evidenceRows, evidenceSeq);
        List<MenuOptimizationDishItem> watchListDishes =
                buildWatchListDishes(portfolio, metricsById, refMargin, evidenceRows, evidenceSeq);

        List<MenuOptimizationPriorityGroup> priorityGroups =
                buildPriorityGroups(costReviewDishes, protectDishes, promotionDishes, watchListDishes);

        String optimizationSummary =
                buildOptimizationSummary(portfolio, costReviewDishes, protectDishes, promotionDishes, watchListDishes);
        List<String> nextSteps =
                buildOptimizationNextSteps(costReviewDishes, protectDishes, promotionDishes, watchListDishes);

        MenuOptimizationPlan optimizationPlan =
                MenuOptimizationPlan.builder()
                        .optimizationSummary(optimizationSummary)
                        .priorityGroups(priorityGroups)
                        .costReviewDishes(costReviewDishes)
                        .protectDishes(protectDishes)
                        .promotionDishes(promotionDishes)
                        .watchListDishes(watchListDishes)
                        .nextSteps(nextSteps)
                        .capabilityLimits(buildActionRecommendationCapabilityLimits())
                        .build();

        List<Map<String, Object>> focus = projectOptimizationItemsAsRows(protectDishes, "focus");
        List<Map<String, Object>> risk = projectOptimizationItemsAsRows(costReviewDishes, "risk");
        List<Map<String, Object>> opportunity = projectOptimizationItemsAsRows(promotionDishes, "opportunity");

        summaryFacts.put("riskDishCount", risk.size());
        summaryFacts.put("focusDishCount", focus.size());
        summaryFacts.put("opportunityDishCount", opportunity.size());
        summaryFacts.put("watchListDishCount", watchListDishes.size());
        summaryFacts.put("optimizationSummary", optimizationSummary);

        List<MenuOperationRecommendedAction> actions =
                buildRecommendedActionsFromOptimization(
                        costReviewDishes, protectDishes, promotionDishes, metrics, refMargin, evidenceRows, evidenceSeq);

        debug.put("recommendedActionCount", actions.size());
        debug.put("menuOptimizationCostReviewCount", costReviewDishes.size());
        debug.put("menuOptimizationProtectCount", protectDishes.size());
        debug.put("menuOptimizationPromotionCount", promotionDishes.size());
        debug.put("menuOptimizationWatchCount", watchListDishes.size());

        return MenuOperationAnswerPlan.builder()
                .timeLabel(timeLabel(state))
                .scopeLabel(scopeLabel(rq))
                .statStartDate(state.getStatStartDate())
                .statEndDate(state.getStatEndDate())
                .summaryFacts(summaryFacts)
                .focusDishes(focus)
                .riskDishes(risk)
                .opportunityDishes(opportunity)
                .recommendedActions(dedupeActions(actions))
                .evidenceRows(evidenceRows)
                .knownGaps(gaps)
                .menuPortfolioClassification(portfolio)
                .menuOptimizationPlan(optimizationPlan)
                .displayCards(buildActionRecommendationDisplayCards())
                .build();
    }

    private static Map<String, DishMetrics> indexMetricsByFoodId(List<DishMetrics> metrics) {
        Map<String, DishMetrics> index = new LinkedHashMap<>();
        if (metrics == null) {
            return index;
        }
        for (DishMetrics m : metrics) {
            if (m != null && StringUtils.hasText(m.foodId())) {
                index.putIfAbsent(m.foodId(), m);
            }
        }
        return index;
    }

    private static List<MenuOptimizationDishItem> buildCostReviewDishes(
            MenuPortfolioClassification portfolio,
            Map<String, DishMetrics> metricsById,
            BigDecimal refMargin,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq) {
        List<MenuOptimizationDishItem> out = new ArrayList<>();
        if (portfolio == null || portfolio.getCategories() == null) {
            return out;
        }
        List<MenuPortfolioDish> candidates = new ArrayList<>();
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat == null || !CATEGORY_TRAFFIC.equals(cat.getCategoryCode())) {
                continue;
            }
            if (cat.getDishes() != null) {
                candidates.addAll(cat.getDishes());
            }
        }
        candidates.sort(
                Comparator.comparing(
                                (MenuPortfolioDish d) -> parseDecimal(d == null ? null : d.getSalesCount()))
                        .reversed());
        for (MenuPortfolioDish dish : candidates) {
            if (out.size() >= TOP_N_OPTIMIZATION) {
                break;
            }
            DishMetrics m = dish == null ? null : metricsById.get(dish.getDishId());
            if (m == null) {
                continue;
            }
            String eid = ensureDishEvidence(evidenceRows, evidenceSeq, m, "opt_cost_review");
            String actionLabel =
                    m.actualProfit().compareTo(BigDecimal.ZERO) < 0 ? "考虑调价" : "复核成本";
            String reason =
                    m.actualProfit().compareTo(BigDecimal.ZERO) < 0
                            ? "相对引流档且实际利润为负，优先复核定价与成本"
                            : "相对引流档（畅销低利），优先复核成本、用量与损耗";
            out.add(toOptimizationDishItem(m, CATEGORY_TRAFFIC, "引流菜", actionLabel, reason, eid, dish));
        }
        if (out.isEmpty()) {
            metricsById.values().stream()
                    .filter(m -> isRiskDish(m, refMargin))
                    .sorted(Comparator.comparing(DishMetrics::soldPortions).reversed())
                    .limit(TOP_N_OPTIMIZATION)
                    .forEach(
                            m -> {
                                String eid = ensureDishEvidence(evidenceRows, evidenceSeq, m, "opt_cost_review_fallback");
                                out.add(
                                        toOptimizationDishItem(
                                                m,
                                                null,
                                                null,
                                                m.actualProfit().compareTo(BigDecimal.ZERO) < 0 ? "考虑调价" : "复核成本",
                                                "毛利率偏低或实际利润为负，建议复核成本与定价",
                                                eid,
                                                null));
                            });
        }
        return out;
    }

    private static List<MenuOptimizationDishItem> buildProtectDishes(
            MenuPortfolioClassification portfolio,
            Map<String, DishMetrics> metricsById,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq) {
        return dishesFromQuadrant(
                portfolio,
                metricsById,
                CATEGORY_STAR,
                "明星菜",
                "继续主推",
                "利润与销量表现较好，建议稳定供应并保持在推荐位",
                evidenceRows,
                evidenceSeq,
                "opt_protect",
                Comparator.comparing(DishMetrics::actualProfit).reversed());
    }

    private static List<MenuOptimizationDishItem> buildPromotionDishes(
            MenuPortfolioClassification portfolio,
            Map<String, DishMetrics> metricsById,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq) {
        return dishesFromQuadrant(
                portfolio,
                metricsById,
                CATEGORY_POTENTIAL,
                "潜力菜",
                "增加曝光",
                "利润效率较好但销量偏低，可加强推荐位或套餐搭配",
                evidenceRows,
                evidenceSeq,
                "opt_promotion",
                Comparator.comparing(DishMetrics::blendedMargin).reversed());
    }

    private static List<MenuOptimizationDishItem> buildWatchListDishes(
            MenuPortfolioClassification portfolio,
            Map<String, DishMetrics> metricsById,
            BigDecimal refMargin,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq) {
        List<MenuOptimizationDishItem> out = new ArrayList<>();
        if (portfolio == null || portfolio.getCategories() == null) {
            return out;
        }
        List<DishMetrics> bucket = new ArrayList<>();
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat == null || !CATEGORY_ELIMINATE.equals(cat.getCategoryCode()) || cat.getDishes() == null) {
                continue;
            }
            for (MenuPortfolioDish dish : cat.getDishes()) {
                if (dish == null || !StringUtils.hasText(dish.getDishId())) {
                    continue;
                }
                DishMetrics m = metricsById.get(dish.getDishId());
                if (m != null) {
                    bucket.add(m);
                }
            }
        }
        bucket.sort(Comparator.comparing(DishMetrics::soldPortions));
        for (DishMetrics m : bucket) {
            if (out.size() >= TOP_N_OPTIMIZATION) {
                break;
            }
            String eid = ensureDishEvidence(evidenceRows, evidenceSeq, m, "opt_watch");
            MenuPortfolioDish portfolioDish = findPortfolioDish(portfolio, CATEGORY_ELIMINATE, m.foodId());
            EliminateObservationTier tier = resolveEliminateObservationTier(m, refMargin);
            String reason =
                    portfolioDish != null && StringUtils.hasText(portfolioDish.getReason())
                            ? portfolioDish.getReason().trim()
                            : "相对观察档（低销量低利润），建议先观察一个周期";
            out.add(
                    toOptimizationDishItem(
                            m,
                            CATEGORY_ELIMINATE,
                            "观察菜",
                            tier.actionLabel(),
                            reason,
                            eid,
                            portfolioDish));
        }
        return out;
    }

    private static List<MenuOptimizationDishItem> dishesFromQuadrant(
            MenuPortfolioClassification portfolio,
            Map<String, DishMetrics> metricsById,
            String quadrantCode,
            String quadrantName,
            String actionLabel,
            String defaultReason,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq,
            String evidenceSuffix,
            Comparator<DishMetrics> sort) {
        List<MenuOptimizationDishItem> out = new ArrayList<>();
        if (portfolio == null || portfolio.getCategories() == null) {
            return out;
        }
        List<DishMetrics> bucket = new ArrayList<>();
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat == null || !quadrantCode.equals(cat.getCategoryCode()) || cat.getDishes() == null) {
                continue;
            }
            for (MenuPortfolioDish dish : cat.getDishes()) {
                if (dish == null || !StringUtils.hasText(dish.getDishId())) {
                    continue;
                }
                DishMetrics m = metricsById.get(dish.getDishId());
                if (m != null) {
                    bucket.add(m);
                }
            }
        }
        bucket.sort(sort);
        for (DishMetrics m : bucket) {
            if (out.size() >= TOP_N_OPTIMIZATION) {
                break;
            }
            String eid = ensureDishEvidence(evidenceRows, evidenceSeq, m, evidenceSuffix);
            MenuPortfolioDish portfolioDish = findPortfolioDish(portfolio, quadrantCode, m.foodId());
            String reason =
                    portfolioDish != null && StringUtils.hasText(portfolioDish.getReason())
                            ? portfolioDish.getReason().trim()
                            : defaultReason;
            out.add(toOptimizationDishItem(m, quadrantCode, quadrantName, actionLabel, reason, eid, portfolioDish));
        }
        return out;
    }

    private static MenuPortfolioDish findPortfolioDish(
            MenuPortfolioClassification portfolio, String quadrantCode, String dishId) {
        if (portfolio == null || portfolio.getCategories() == null || !StringUtils.hasText(dishId)) {
            return null;
        }
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat == null || !quadrantCode.equals(cat.getCategoryCode()) || cat.getDishes() == null) {
                continue;
            }
            for (MenuPortfolioDish dish : cat.getDishes()) {
                if (dish != null && dishId.equals(dish.getDishId())) {
                    return dish;
                }
            }
        }
        return null;
    }

    private static MenuOptimizationDishItem toOptimizationDishItem(
            DishMetrics m,
            String quadrantCode,
            String quadrantName,
            String actionLabel,
            String reason,
            String evidenceRefId,
            MenuPortfolioDish portfolioDish) {
        return MenuOptimizationDishItem.builder()
                .dishId(m.foodId())
                .dishName(StringUtils.hasText(m.dishName()) ? m.dishName() : "（未命名菜品）")
                .quadrantCode(quadrantCode)
                .quadrantName(quadrantName)
                .soldPortionsTotal(formatAmount(m.soldPortions()))
                .listPriceRevenue(formatAmount(m.listPriceRevenue()))
                .blendedGrossMarginRateOnListPrice(formatRate(m.blendedMargin()))
                .actualProfitAmount(formatAmount(m.actualProfit()))
                .suggestedActionLabel(actionLabel)
                .reason(
                        StringUtils.hasText(reason)
                                ? reason
                                : portfolioDish != null && StringUtils.hasText(portfolioDish.getReason())
                                        ? portfolioDish.getReason().trim()
                                        : null)
                .evidenceRefId(evidenceRefId)
                .build();
    }

    private static String ensureDishEvidence(
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq,
            DishMetrics m,
            String suffix) {
        return addDishEvidence(evidenceRows, evidenceSeq, m, suffix);
    }

    private static List<MenuOptimizationPriorityGroup> buildPriorityGroups(
            List<MenuOptimizationDishItem> costReview,
            List<MenuOptimizationDishItem> protect,
            List<MenuOptimizationDishItem> promotion,
            List<MenuOptimizationDishItem> watch) {
        List<MenuOptimizationPriorityGroup> groups = new ArrayList<>();
        if (!costReview.isEmpty()) {
            groups.add(
                    MenuOptimizationPriorityGroup.builder()
                            .groupCode(MenuOperationAnswerPlan.OPT_GROUP_PRIORITY_HANDLE)
                            .groupName("优先处理")
                            .priority(1)
                            .reason("相对引流档或毛利/利润偏弱的菜，优先复核成本、用量与定价")
                            .suggestedAction("复核成本与定价")
                            .dishes(new ArrayList<>(costReview))
                            .build());
        }
        if (!protect.isEmpty()) {
            groups.add(
                    MenuOptimizationPriorityGroup.builder()
                            .groupCode(MenuOperationAnswerPlan.OPT_GROUP_STABLE_PROMOTE)
                            .groupName("稳定主推")
                            .priority(2)
                            .reason("明星菜利润与销量贡献稳定，建议保供并维持推荐位")
                            .suggestedAction("继续主推")
                            .dishes(new ArrayList<>(protect))
                            .build());
        }
        if (!promotion.isEmpty()) {
            groups.add(
                    MenuOptimizationPriorityGroup.builder()
                            .groupCode(MenuOperationAnswerPlan.OPT_GROUP_INCREASE_EXPOSURE)
                            .groupName("增加曝光")
                            .priority(3)
                            .reason("潜力菜利润效率较好但点单不足，可加强曝光与搭配")
                            .suggestedAction("增加曝光")
                            .dishes(new ArrayList<>(promotion))
                            .build());
        }
        if (!watch.isEmpty()) {
            groups.add(
                    MenuOptimizationPriorityGroup.builder()
                            .groupCode(MenuOperationAnswerPlan.OPT_GROUP_WATCH_ADJUST)
                            .groupName("观察调整")
                            .priority(4)
                            .reason("相对观察档（低销量低利润）先跟踪周期表现，再决定是否调整；不代表建议下架")
                            .suggestedAction("观察调整")
                            .dishes(new ArrayList<>(watch))
                            .build());
        }
        return groups;
    }

    private static String buildOptimizationSummary(
            MenuPortfolioClassification portfolio,
            List<MenuOptimizationDishItem> costReview,
            List<MenuOptimizationDishItem> protect,
            List<MenuOptimizationDishItem> promotion,
            List<MenuOptimizationDishItem> watch) {
        if (portfolio == null || portfolio.getTotalDishCount() <= 0) {
            return "当前样本不足以形成菜单优化方案，建议补充更多菜品经营数据后再判断。";
        }
        int traffic = countForCategoryCode(portfolio, CATEGORY_TRAFFIC);
        int star = countForCategoryCode(portfolio, CATEGORY_STAR);
        StringBuilder sb = new StringBuilder();
        sb.append("本月菜单优化重点：");
        if (!costReview.isEmpty() || traffic > 0) {
            sb.append("先复核相对引流档（畅销低利）菜的成本与定价");
        } else {
            sb.append("整体毛利结构尚可，可优先稳定利润贡献菜");
        }
        if (!protect.isEmpty() || star > 0) {
            sb.append("，同时稳定明星菜供应与推荐位");
        }
        if (!promotion.isEmpty()) {
            sb.append("；另有潜力菜可加强曝光");
        }
        if (!watch.isEmpty()) {
            sb.append("；相对观察档菜品建议先观察再调整");
        }
        sb.append('。');
        return sb.toString();
    }

    private static int countForCategoryCode(MenuPortfolioClassification portfolio, String code) {
        if (portfolio == null || portfolio.getCategories() == null) {
            return 0;
        }
        for (MenuPortfolioCategory cat : portfolio.getCategories()) {
            if (cat != null && code.equals(cat.getCategoryCode())) {
                return cat.getCount();
            }
        }
        return 0;
    }

    private static List<String> buildOptimizationNextSteps(
            List<MenuOptimizationDishItem> costReview,
            List<MenuOptimizationDishItem> protect,
            List<MenuOptimizationDishItem> promotion,
            List<MenuOptimizationDishItem> watch) {
        List<String> steps = new ArrayList<>();
        if (!costReview.isEmpty()) {
            MenuOptimizationDishItem top = costReview.get(0);
            steps.add(
                    "先复核「"
                            + top.getDishName()
                            + "」的成本结构、标准用量与损耗"
                            + (StringUtils.hasText(top.getSuggestedActionLabel())
                                    ? "（" + top.getSuggestedActionLabel() + "）"
                                    : ""));
        }
        if (!protect.isEmpty() && steps.size() < TOP_N_NEXT_STEPS) {
            MenuOptimizationDishItem top = protect.get(0);
            steps.add("保持「" + top.getDishName() + "」等明星菜推荐位与供应稳定");
        }
        if (!promotion.isEmpty() && steps.size() < TOP_N_NEXT_STEPS) {
            MenuOptimizationDishItem top = promotion.get(0);
            steps.add("为「" + top.getDishName() + "」增加推荐位或套餐曝光，观察点单变化");
        } else if (!watch.isEmpty() && steps.size() < TOP_N_NEXT_STEPS) {
            MenuOptimizationDishItem top = watch.get(0);
            steps.add("对「" + top.getDishName() + "」等观察菜先跟踪一个周期，再决定是否调整");
        }
        return steps.stream().limit(TOP_N_NEXT_STEPS).toList();
    }

    private static Map<String, Object> buildActionRecommendationCapabilityLimits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("latestPurchasePrice", "NOT_IN_P1");
        limits.put("externalMarketBenchmark", "NOT_IN_P1");
        limits.put("multiPeriodTrend", "NOT_IN_P1");
        limits.put("crossStoreDishRank", "NOT_IN_P1");
        limits.put("comboOrderAnalysis", "NOT_IN_P1");
        return limits;
    }

    private static List<Map<String, Object>> projectOptimizationItemsAsRows(
            List<MenuOptimizationDishItem> items, String bucket) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MenuOptimizationDishItem item : items) {
            if (item == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", bucket);
            row.put("foodId", item.getDishId());
            row.put("dishId", item.getDishId());
            row.put("dishName", item.getDishName());
            putIfPresent(row, "soldPortionsTotal", item.getSoldPortionsTotal());
            putIfPresent(row, "listPriceRevenue", item.getListPriceRevenue());
            putIfPresent(row, "actualProfitAmount", item.getActualProfitAmount());
            putIfPresent(row, "blendedGrossMarginRateOnListPrice", item.getBlendedGrossMarginRateOnListPrice());
            putIfPresent(row, "reason", item.getReason());
            putIfPresent(row, "evidenceRefId", item.getEvidenceRefId());
            rows.add(row);
        }
        return rows;
    }

    private static List<MenuOperationRecommendedAction> buildRecommendedActionsFromOptimization(
            List<MenuOptimizationDishItem> costReview,
            List<MenuOptimizationDishItem> protect,
            List<MenuOptimizationDishItem> promotion,
            List<DishMetrics> metrics,
            BigDecimal refMargin,
            List<Map<String, Object>> evidenceRows,
            AtomicInteger evidenceSeq) {
        List<MenuOperationRecommendedAction> actions = new ArrayList<>();
        for (MenuOptimizationDishItem item : costReview) {
            if (item == null || !StringUtils.hasText(item.getDishId())) {
                continue;
            }
            DishMetrics m = findMetric(metrics, item.getDishId());
            if (m == null) {
                continue;
            }
            String code =
                    m.actualProfit().compareTo(BigDecimal.ZERO) < 0
                            ? MenuOperationRecommendedAction.RAISE_PRICE
                            : MenuOperationRecommendedAction.REDUCE_COST;
            String eid =
                    StringUtils.hasText(item.getEvidenceRefId())
                            ? item.getEvidenceRefId()
                            : addDishEvidence(evidenceRows, evidenceSeq, m, "action_cost_review");
            actions.add(
                    actionWithEvidence(
                            code,
                            1,
                            m.foodId(),
                            m.actualProfit().compareTo(BigDecimal.ZERO) < 0
                                    ? "NEGATIVE_ACTUAL_PROFIT"
                                    : "LOW_MARGIN_OR_LOSS",
                            List.of(eid)));
        }
        for (MenuOptimizationDishItem item : protect) {
            if (item == null || !StringUtils.hasText(item.getDishId())) {
                continue;
            }
            DishMetrics m = findMetric(metrics, item.getDishId());
            String eid =
                    StringUtils.hasText(item.getEvidenceRefId())
                            ? item.getEvidenceRefId()
                            : m != null
                                    ? addDishEvidence(evidenceRows, evidenceSeq, m, "action_protect")
                                    : null;
            if (!StringUtils.hasText(eid)) {
                continue;
            }
            actions.add(
                    actionWithEvidence(
                            MenuOperationRecommendedAction.KEEP_AND_PROMOTE,
                            2,
                            item.getDishId(),
                            "HEAD_PROFIT_DISH",
                            List.of(eid)));
        }
        for (MenuOptimizationDishItem item : promotion) {
            if (item == null || !StringUtils.hasText(item.getDishId())) {
                continue;
            }
            DishMetrics m = findMetric(metrics, item.getDishId());
            String eid =
                    StringUtils.hasText(item.getEvidenceRefId())
                            ? item.getEvidenceRefId()
                            : m != null
                                    ? addDishEvidence(evidenceRows, evidenceSeq, m, "action_promotion")
                                    : null;
            if (!StringUtils.hasText(eid)) {
                continue;
            }
            actions.add(
                    actionWithEvidence(
                            MenuOperationRecommendedAction.KEEP_AND_PROMOTE,
                            3,
                            item.getDishId(),
                            "HEAD_PROFIT_DISH",
                            List.of(eid)));
        }
        if (actions.isEmpty()) {
            DishMetrics topProfit =
                    metrics.stream()
                            .filter(DishMetrics::hasPositiveProfit)
                            .max(Comparator.comparing(DishMetrics::actualProfit))
                            .orElse(null);
            if (topProfit != null) {
                String eid = addDishEvidence(evidenceRows, evidenceSeq, topProfit, "action_fallback_head");
                actions.add(
                        actionWithEvidence(
                                MenuOperationRecommendedAction.KEEP_AND_PROMOTE,
                                1,
                                topProfit.foodId(),
                                "HEAD_PROFIT_DISH",
                                List.of(eid)));
            }
        }
        return actions;
    }

    private static DishMetrics findMetric(List<DishMetrics> metrics, String foodId) {
        if (metrics == null || !StringUtils.hasText(foodId)) {
            return null;
        }
        for (DishMetrics m : metrics) {
            if (m != null && foodId.equals(m.foodId())) {
                return m;
            }
        }
        return null;
    }

    private static List<MenuOperationAnswerPlan.MenuOperationDisplayCard> buildActionRecommendationDisplayCards() {
        return List.of(
                MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                        .cardType(MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION)
                        .title("菜单优化方案")
                        .subtitle("基于四象限分层与销量、毛利、实际利润生成的优先级建议")
                        .chartType(MenuOperationAnswerPlan.CHART_TYPE_PLAN)
                        .dataRef(MenuOperationAnswerPlan.DATA_REF_MENU_OPTIMIZATION_PLAN)
                        .build());
    }

    private static List<String> actionRecommendationKnownGaps() {
        return List.of(
                "MENU_PRICING_ADVICE_NOT_IN_P1",
                "MENU_SINGLE_ANALYSIS_NOT_IN_P1",
                "STOCK_REDUCE_EVIDENCE_OPTIONAL",
                "TIME_SCOPE_FOLLOW_UP_INHERITANCE_LABEL_INACCURATE");
    }

    /** high_sales_low_profit 展示卡片描述；cardType 不参与业务判断。 */
    private static List<MenuOperationAnswerPlan.MenuOperationDisplayCard> buildHighSalesLowMarginDisplayCards() {
        return List.of(
                MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                        .cardType(MenuOperationAnswerPlan.CARD_TYPE_MENU_HIGH_SALES_LOW_MARGIN)
                        .title("畅销低利菜")
                        .subtitle("按本轮菜单内销量与毛利率分位识别畅销低利或毛利率相对偏低的菜品")
                        .chartType(MenuOperationAnswerPlan.CHART_TYPE_TABLE)
                        .dataRef(MenuOperationAnswerPlan.DATA_REF_RISK_DISHES)
                        .build());
    }

    private static List<String> overviewKnownGaps() {
        return List.of(
                "MENU_PRICING_ADVICE_NOT_IN_P1",
                "MENU_SINGLE_ANALYSIS_NOT_IN_P1",
                "STOCK_REDUCE_EVIDENCE_OPTIONAL",
                "TIME_SCOPE_FOLLOW_UP_INHERITANCE_LABEL_INACCURATE");
    }

    private static List<String> highSalesKnownGaps() {
        return List.of(
                "DISH_INGREDIENT_COST_BREAKDOWN_NOT_IN_P1",
                "MENU_PRICING_ADVICE_NOT_IN_P1",
                "TIME_SCOPE_FOLLOW_UP_INHERITANCE_LABEL_INACCURATE");
    }

    private static LinkedHashMap<String, Object> buildSummaryFacts(
            List<Map<String, Object>> dishRows, Map<String, Object> insight) {
        LinkedHashMap<String, Object> facts = new LinkedHashMap<>();
        facts.put("dishCountAnalyzed", dishRows.size());
        if (insight != null) {
            putIfPresent(facts, "totalListPriceRevenue", insight.get("totalListPriceRevenue"));
            putIfPresent(
                    facts,
                    "comprehensiveGrossMarginRate",
                    formatRate(parseDecimal(insight.get("comprehensiveGrossMarginRateOnListPrice"))));
            Object waste = insight.get("wasteLossRatioInOutbound123");
            if (waste == null && insight.get("scopeOutboundSubtotals") instanceof Map<?, ?> sub) {
                waste = sub.get("wasteLossRatioInOutbound123");
            }
            putIfPresent(facts, "wasteLossRatioInOutbound123", waste);
        }
        BigDecimal totalRev = BigDecimal.ZERO;
        BigDecimal totalCost123 = BigDecimal.ZERO;
        for (DishMetrics m : toMetrics(dishRows)) {
            totalRev = totalRev.add(m.listPriceRevenue());
            totalCost123 = totalCost123.add(m.actualCost123());
        }
        BigDecimal profit = totalRev.subtract(totalCost123);
        facts.put("portfolioActualProfitAmount", formatAmount(profit));
        facts.put("totalActualCost123", formatAmount(totalCost123));
        return facts;
    }

    private static void enrichPortfolioEvidence(
            List<Map<String, Object>> evidenceRows,
            AtomicInteger seq,
            Map<String, Object> insight,
            Map<String, Object> toolData) {
        if (insight != null) {
            addEvidence(
                    evidenceRows,
                    seq,
                    "ev-portfolio-revenue",
                    AiBusinessToolIds.DISH_PROFIT_ANALYSIS,
                    "businessInsightSummary.totalListPriceRevenue",
                    "标价销售额",
                    insight.get("totalListPriceRevenue"),
                    "元");
            addEvidence(
                    evidenceRows,
                    seq,
                    "ev-portfolio-margin",
                    AiBusinessToolIds.DISH_PROFIT_ANALYSIS,
                    "businessInsightSummary.comprehensiveGrossMarginRateOnListPrice",
                    "综合毛利率",
                    formatRate(parseDecimal(insight.get("comprehensiveGrossMarginRateOnListPrice"))),
                    "");
        }
        Object banner = toolData.get("queryScopeBanner");
        if (banner != null) {
            addEvidence(
                    evidenceRows,
                    seq,
                    "ev-scope-banner",
                    AiBusinessToolIds.DISH_PROFIT_ANALYSIS,
                    "queryScopeBanner",
                    "查询范围",
                    banner,
                    "");
        }
    }

    private static String addDishEvidence(
            List<Map<String, Object>> evidenceRows,
            AtomicInteger seq,
            DishMetrics m,
            String suffix) {
        String id = "ev-dish-" + suffix + "-" + seq.get();
        addEvidence(
                evidenceRows,
                seq,
                id,
                AiBusinessToolIds.DISH_PROFIT_ANALYSIS,
                "dishRows.actualProfitAmount123",
                m.dishName() + " 实际利润(type123)",
                formatAmount(m.actualProfit()),
                "元");
        return id;
    }

    private static void addEvidence(
            List<Map<String, Object>> out,
            AtomicInteger seq,
            String id,
            String sourceTool,
            String fieldPath,
            String label,
            Object value,
            String unit) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return;
        }
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("evidenceId", id != null ? id : "ev-" + seq.getAndIncrement());
        row.put("sourceTool", sourceTool);
        row.put("fieldPath", fieldPath);
        row.put("displayLabel", label);
        row.put("value", value.toString().trim());
        if (StringUtils.hasText(unit)) {
            row.put("unit", unit);
        }
        out.add(row);
        seq.incrementAndGet();
    }

    private static MenuOperationRecommendedAction actionWithEvidence(
            String code, int priority, String foodId, String rationale, List<String> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return null;
        }
        List<String> targets = StringUtils.hasText(foodId) ? List.of(foodId) : List.of();
        return MenuOperationRecommendedAction.builder()
                .actionCode(code)
                .priority(priority)
                .targetDishIds(targets)
                .rationaleKey(rationale)
                .evidenceRefIds(new ArrayList<>(evidenceIds))
                .build();
    }

    private static List<MenuOperationRecommendedAction> dedupeActions(List<MenuOperationRecommendedAction> in) {
        LinkedHashMap<String, MenuOperationRecommendedAction> map = new LinkedHashMap<>();
        for (MenuOperationRecommendedAction a : in) {
            if (a == null || !StringUtils.hasText(a.getActionCode())) {
                continue;
            }
            if (a.getEvidenceRefIds() == null || a.getEvidenceRefIds().isEmpty()) {
                continue;
            }
            String key = a.getActionCode() + "|" + String.join(",", a.getTargetDishIds());
            map.putIfAbsent(key, a);
        }
        return new ArrayList<>(map.values());
    }

    private record DishMetrics(
            String foodId,
            String dishName,
            BigDecimal soldPortions,
            BigDecimal listPriceRevenue,
            BigDecimal actualCost123,
            BigDecimal actualProfit,
            BigDecimal blendedMargin) {

        static DishMetrics fromRow(Map<String, Object> row) {
            String foodId = nz(row.get("foodId"));
            if (!StringUtils.hasText(foodId)) {
                foodId = nz(row.get("dishId"));
            }
            String name = nz(row.get("dishName"));
            BigDecimal qty = parseDecimal(row.get("soldPortionsTotal"));
            BigDecimal rev = parseDecimal(row.get("listPriceRevenue"));
            BigDecimal cost123 = parseDecimal(row.get("actualCostTotalAmount123"));
            if (cost123.compareTo(BigDecimal.ZERO) == 0) {
                cost123 = parseDecimal(row.get("actualCostTotalAmount"));
            }
            BigDecimal profit = rev.subtract(cost123);
            BigDecimal margin = parseDecimal(row.get("blendedGrossMarginRateOnListPrice"));
            return new DishMetrics(foodId, name, qty, rev, cost123, profit, margin);
        }

        boolean hasPositiveProfit() {
            return actualProfit.compareTo(BigDecimal.ZERO) > 0;
        }

        Map<String, Object> toFocusRow() {
            return dishRowMap("focus");
        }

        Map<String, Object> toRiskRow() {
            return dishRowMap("risk");
        }

        Map<String, Object> toOpportunityRow() {
            return dishRowMap("opportunity");
        }

        private Map<String, Object> dishRowMap(String bucket) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("bucket", bucket);
            m.put("foodId", foodId);
            m.put("dishName", StringUtils.hasText(dishName) ? dishName : "（未命名菜品）");
            m.put("soldPortionsTotal", formatAmount(soldPortions));
            m.put("listPriceRevenue", formatAmount(listPriceRevenue));
            m.put("actualCostTotalAmount123", formatAmount(actualCost123));
            m.put("actualProfitAmount", formatAmount(actualProfit));
            m.put("blendedGrossMarginRateOnListPrice", formatRate(blendedMargin));
            return m;
        }
    }

    private static boolean isRiskDish(DishMetrics m, BigDecimal refMargin) {
        if (m.actualProfit().compareTo(BigDecimal.ZERO) < 0) {
            return true;
        }
        return refMargin.compareTo(BigDecimal.ZERO) > 0 && m.blendedMargin().compareTo(refMargin) < 0;
    }

    private static List<DishMetrics> toMetrics(List<Map<String, Object>> dishRows) {
        List<DishMetrics> out = new ArrayList<>();
        if (dishRows == null) {
            return out;
        }
        for (Map<String, Object> row : dishRows) {
            if (row != null) {
                out.add(DishMetrics.fromRow(row));
            }
        }
        return out;
    }

    /**
     * dish_profit_analysis 快照按门店/部门菜谱行展开，同一 {@code foodId} 可能多行。
     * 四象限前按 foodId 确定性合并为一条经营记录（销量/销售额/成本/利润求和，毛利率重算）。
     */
    private static List<Map<String, Object>> aggregateDishRowsForPortfolio(List<Map<String, Object>> dishRows) {
        if (dishRows == null || dishRows.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, LinkedHashMap<String, Object>> acc = new LinkedHashMap<>();
        int anonymousIdx = 0;
        for (Map<String, Object> row : dishRows) {
            if (row == null) {
                continue;
            }
            String foodId = resolvePortfolioFoodId(row);
            String key =
                    StringUtils.hasText(foodId)
                            ? "food:" + foodId
                            : "anon:" + anonymousIdx++;
            acc.compute(
                    key,
                    (k, existing) ->
                            existing == null
                                    ? shallowCopyPortfolioRow(row)
                                    : mergePortfolioDishRowAccum(existing, row));
        }
        return new ArrayList<>(acc.values());
    }

    private static String resolvePortfolioFoodId(Map<String, Object> row) {
        String foodId = nz(row.get("foodId"));
        if (!StringUtils.hasText(foodId)) {
            foodId = nz(row.get("dishId"));
        }
        return StringUtils.hasText(foodId) ? foodId.trim() : null;
    }

    private static LinkedHashMap<String, Object> shallowCopyPortfolioRow(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    private static LinkedHashMap<String, Object> mergePortfolioDishRowAccum(
            LinkedHashMap<String, Object> acc, Map<String, Object> row) {
        BigDecimal qty =
                parseDecimal(acc.get("soldPortionsTotal")).add(parseDecimal(row.get("soldPortionsTotal")));
        BigDecimal rev =
                parseDecimal(acc.get("listPriceRevenue")).add(parseDecimal(row.get("listPriceRevenue")));
        BigDecimal cost123 =
                parseDecimal(acc.get("actualCostTotalAmount123"))
                        .add(parseDecimal(row.get("actualCostTotalAmount123")));
        if (cost123.compareTo(BigDecimal.ZERO) == 0) {
            cost123 =
                    parseDecimal(acc.get("actualCostTotalAmount"))
                            .add(parseDecimal(row.get("actualCostTotalAmount")));
        }
        BigDecimal costType1 =
                parseDecimal(acc.get("actualCostAmount")).add(parseDecimal(row.get("actualCostAmount")));

        acc.put("soldPortionsTotal", formatAmount(qty));
        acc.put("listPriceRevenue", formatAmount(rev));
        acc.put("actualCostTotalAmount123", formatAmount(cost123));
        acc.put("totalActualCostAmount123", formatAmount(cost123));
        if (costType1.compareTo(BigDecimal.ZERO) != 0) {
            acc.put("actualCostAmount", formatAmount(costType1));
        }
        acc.put(
                "blendedGrossMarginRateOnListPrice",
                formatBlendedMarginPercentForRow(rev, cost123));
        preferNonEmptyField(acc, row, "dishName");
        preferNonEmptyField(acc, row, "foodName");
        return acc;
    }

    private static void preferNonEmptyField(
            LinkedHashMap<String, Object> acc, Map<String, Object> row, String field) {
        if (StringUtils.hasText(nz(acc.get(field)))) {
            return;
        }
        String v = nz(row.get(field));
        if (StringUtils.hasText(v)) {
            acc.put(field, v);
        }
    }

    /** 与 {@link DishMetrics#fromRow} 一致：存 0～100 百分数字符串（不含 %）。 */
    private static String formatBlendedMarginPercentForRow(BigDecimal revenue, BigDecimal cost123) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.00";
        }
        BigDecimal marginPct =
                revenue.subtract(cost123)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(revenue, 4, RoundingMode.HALF_UP);
        return marginPct.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> businessInsightSummary(Map<String, Object> toolData) {
        if (toolData == null) {
            return Map.of();
        }
        Object raw = toolData.get("businessInsightSummary");
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractDishRows(Map<String, Object> toolData) {
        if (toolData == null) {
            return List.of();
        }
        Object raw = toolData.get("dishRows");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    private static void attachEarlyExit(
            AiRunState state, LinkedHashMap<String, Object> debug, List<String> gaps) {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .summaryFacts(Map.of())
                        .focusDishes(List.of())
                        .riskDishes(List.of())
                        .opportunityDishes(List.of())
                        .recommendedActions(List.of())
                        .evidenceRows(List.of())
                        .knownGaps(new ArrayList<>(gaps))
                        .timeLabel(timeLabel(state))
                        .scopeLabel(scopeLabel(state.getResolvedQueryContext()))
                        .statStartDate(state.getStatStartDate())
                        .statEndDate(state.getStatEndDate())
                        .debug(debug)
                        .build();
        state.setMenuOperationAnswerPlan(plan);
    }

    private record WireResolution(String raw, String wire, String rejectReason) {}

    private static WireResolution resolveMenuOperationWire(AiResolvedQueryContext rq) {
        AiQuerySemanticParseResult sem = semantic(rq);
        if (!SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return new WireResolution(null, null, "non_contract_locked_parse");
        }
        String raw =
                sem.getSemanticSlots() != null
                        ? blankToNull(sem.getSemanticSlots().getStructuredIntentDetailWire())
                        : null;
        if (!StringUtils.hasText(raw)) {
            return new WireResolution(null, null, "missing_contract_completed_wire");
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
        if (!MenuOperationSemanticCapabilityMatrix.isAcceptedMenuOperationWire(wire)) {
            return new WireResolution(raw, null, "contract_wire_not_in_menu_operation_matrix");
        }
        return new WireResolution(raw, wire, null);
    }

    private static String timeLabel(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        if (tw != null && StringUtils.hasText(tw.getDisplayTimeRange())) {
            return tw.getDisplayTimeRange().trim();
        }
        if (state == null) {
            return "";
        }
        String start = blankToNull(state.getStatStartDate());
        String end = blankToNull(state.getStatEndDate());
        if (start != null && end != null) {
            return start + " 至 " + end;
        }
        return "";
    }

    private static String scopeLabel(AiResolvedQueryContext rq) {
        if (rq == null) {
            return "";
        }
        if (StringUtils.hasText(rq.getQueryScopeBanner())) {
            return rq.getQueryScopeBanner().trim();
        }
        return "";
    }

    private static AiQuerySemanticParseResult semantic(AiResolvedQueryContext rq) {
        return rq == null ? null : rq.getQuerySemanticParse();
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && StringUtils.hasText(value.toString())) {
            target.put(key, value.toString().trim());
        }
    }

    private static BigDecimal parseDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = v.toString().trim().replace(",", "");
        if (s.isEmpty() || "—".equals(s) || "-".equals(s)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String formatAmount(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 数据源已是 0～100 百分数口径（如 55.88），不再乘 100。 */
    private static String formatRate(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String nz(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolEnvelopeData(AiRunState state, String toolId) {
        Object env = state.getToolResults() == null ? null : state.getToolResults().get(toolId);
        if (!(env instanceof Map<?, ?> tm)) {
            return Map.of();
        }
        Object data = tm.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        return (Map<String, Object>) dm;
    }

    private static boolean toolEnvelopeSuccess(AiRunState state, String toolId) {
        Object env = state.getToolResults() == null ? null : state.getToolResults().get(toolId);
        if (!(env instanceof Map<?, ?> m)) {
            return false;
        }
        return Boolean.TRUE.equals(m.get("success"));
    }
}
