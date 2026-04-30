package com.nongxinle.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜品成本 / 出库分析：{@code salesDish} 以销售菜品为主；{@code outboundQty} 以出库数量为主。
 */
public interface GbDishCostAnalysisService {

    /**
     * @param reportKind {@code salesDish}（默认）| {@code outboundQty}
     */
    Map<String, Object> buildReport(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String reportKind);

    /**
     * 配料分析：销售汇总、按菜按配方( q×u )实收口径，type2/3 在扣减无菜品关联时按「本行 type1 占全店 type1」同比分摊到本菜本料。
     *
     * @param endDate 同 {@link #buildReport} 的 {@code stopDate}
     * @param sortBy  {@code sales|salesAmount|销量} 按实收销售额降序；{@code diff|diffCostPerPortion|成本差异} 按每份成本差异绝对值降序；空则同 sales
     */
    Map<String, Object> buildIngredientAnalysisReport(String startDate, String endDate, Integer disId, String searchDepId,
            Integer depFatherId, String sortBy);

    /**
     * 按分销商商品（配料）聚合的出库分析：同区间与配料分摊口径，汇总出库金额/重量及按料行的理论、实际、差异与涉及菜品行。
     *
     * @param sortBy {@code outbound|outboundAmount} 本商品 1+2+3 出库**金额**合计降序；{@code util|utilization|利用率} 同料**利用率**降序，无理论为末；
     *             {@code wasteloss|waste2loss3|损耗损失} 本商品 type2+type3 出库**重量**合计降序。空为 outbound。
     */
    Map<String, Object> buildOutboundIngredientAnalysisReport(String startDate, String endDate, Integer disId, String searchDepId,
            Integer depFatherId, String sortBy);

    /**
     * 按 {@code /ingredientAnalysis} 同一口径生成各菜的 {@code ingredientRows}（Map 列表），供部门菜品列表等复用。
     * <p>会将 {@code foodIds} 并入分摊用的全表菜品集合，使本期内无销量但需展示的菜仍参与 {@code sumNeed} 等汇总。</p>
     *
     * @param depFatherId 与 {@link #buildIngredientAnalysisReport} 一致；{@code searchDepId} 在此固定为 null，由父部门解析门店范围
     */
    Map<Integer, List<Map<String, Object>>> buildIngredientRowsForFoodIds(String startDate, String endDate, Integer disId,
            Integer depFatherId, Set<Integer> foodIds);

    /**
     * 单菜配料看板：配料明细（在 {@code /ingredientAnalysis} 同行口径上扩展用量偏差、成本占比、建议等）、成本结构占比、按月的成本趋势点与综合建议文案。
     * <p>{@code trendGranularity} 当前仅支持 {@code month}；趋势区间缺省时为 {@code endDate} 往前至多 6 个自然月（与主区间求交）。</p>
     *
     * @param trendStartDate 趋势起点（含），可空
     * @param trendEndDate   趋势终点（含），可空则取主区间 {@code endDate}
     * @param primaryDisGoodsId 趋势曲线聚焦的原料；可空则取主区间内「单份实际成本」最高的配料
     */
    Map<String, Object> buildDishIngredientDashboard(String startDate, String endDate, Integer disId, Integer depFatherId,
            Integer foodId, String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId);

    /**
     * 各菜在区间内、部门 scope 下 type1+2+3 摊销后的**单份实际成本**（元/份），与 {@code buildIngredientAnalysisDishRow} 中 {@code actualCostPerPortion} 同口径。
     * <p>供部门菜品 {@code gbDfBusinessInsight} 等列表展示，与 {@code grossMarginRateOnListPrice}（仅 type1）区分。</p>
     */
    Map<Integer, BigDecimal> getDishActualCostPerPortion123ByFoodIds(String startDate, String endDate, Integer disId,
            Integer depFatherId, Set<Integer> foodIds);

    /**
     * 与 {@code /gbDishCostAnalysis/dishIngredientDashboard} 的 {@code dish}、{@code /ingredientAnalysis} 按菜行一致：
     * 各菜 {@code theoryCostPerPortion}、{@code actualCostPerPortion}（type1+2+3 摊销）、{@code diffCostPerPortion}、{@code salesPortions}（字符串）。
     * <p>注意：与 {@link #buildReport} {@code salesDishRows} 的 {@code theoryCostAmount}/{@code actualCostAmount} 不同——后者整菜「实际」侧仅按生产出库 type1 重量分摊计价，未并入 type2/3 分摊金额。</p>
     */
    Map<Integer, Map<String, String>> getDishPerPortionCosts123ByFoodIds(String startDate, String endDate, Integer disId,
            Integer depFatherId, Set<Integer> foodIds);
}
