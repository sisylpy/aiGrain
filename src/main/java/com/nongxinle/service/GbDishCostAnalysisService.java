package com.nongxinle.service;

import java.math.BigDecimal;
import java.util.Collection;
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
    default Map<String, Object> buildReport(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String reportKind) {
        return buildReport(startDate, stopDate, disId, searchDepId, depFatherId, reportKind, null);
    }

    /**
     * @param scopeDepartmentIdsAllowFilter 非空时将与解析出的菜品销售子部门 scope 求交（如区域权限 {@code AiQueryScope#resolvedDepartmentIds}）。
     */
    Map<String, Object> buildReport(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String reportKind, Collection<Integer> scopeDepartmentIdsAllowFilter);

    /**
     * 配料分析：销售汇总、按菜按配方( q×u )实收口径，type2/3 在扣减无菜品关联时按「本行 type1 占全店 type1」同比分摊到本菜本料。
     * <p>{@code scopeSalesSubtotals}：{@code actualCostTotal}、{@code theoreticalCostTotal}、{@code costDeviationTotal}、{@code costDeviationRate}。</p>
     *
     * @param stopDate 区间结束日，同 {@link #buildReport} 的 {@code stopDate}
     * @param sortBy  {@code sales|salesAmount|销量} 实收销售额；{@code diff|diffCostPerPortion|成本差异} 每份成本差异绝对值；
     *                {@code diffRate|diffRatePerPortion|成本偏差率} 每份成本偏差率；{@code actualCost|actualCostPerPortion} 每道菜<strong>单份</strong>实际成本（type1+2+3）；
     *                {@code ingredientCount|配料数量} 配料行数；空则同 sales
     * @param sortOrder {@code desc|asc|降序|升序}，默认 {@code desc}
     */
    Map<String, Object> buildIngredientAnalysisReport(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String sortBy, String sortOrder);

    /**
     * 菜单分类经营概览专用：配料分析同口径的单次数据加载，返回按 {@code foodId} 聚合的轻量菜品行（无 ingredientRows）。
     */
    List<Map<String, Object>> buildCategoryOverviewDishRows(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Collection<Integer> scopeDepartmentIdsAllowFilter);

    /**
     * 单菜经营行（与 {@link #buildCategoryOverviewDishRows} 单行同结构）；{@code foodId} 并入 scope，供菜单详情页使用。
     */
    default Map<String, Object> buildCategoryOverviewDishRowForFoodId(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Integer foodId) {
        return buildCategoryOverviewDishRowForFoodId(startDate, stopDate, disId, depFatherId, foodId, null);
    }

    Map<String, Object> buildCategoryOverviewDishRowForFoodId(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Integer foodId, Collection<Integer> scopeDepartmentIdsAllowFilter);

    /**
     * 单菜配料分析行（与 {@link #buildIngredientAnalysisReport} 的 {@code salesDishRows[]} 元素同结构，含
     * {@code ingredientRows} / {@code bottle}）；{@code foodId} 并入 scope，本期内零销量仍可返回配方与出库分摊行。
     */
    default Map<String, Object> buildIngredientAnalysisDishRowForFoodId(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Integer foodId) {
        return buildIngredientAnalysisDishRowForFoodId(startDate, stopDate, disId, depFatherId, null, foodId, null);
    }

    Map<String, Object> buildIngredientAnalysisDishRowForFoodId(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Integer foodId,
            Collection<Integer> scopeDepartmentIdsAllowFilter);

    /**
     * 按分销商商品（配料）聚合的出库分析：同区间与配料分摊口径，汇总出库金额/重量及按料行的理论、实际、差异与涉及菜品行。
     *
     * @param sortBy {@code outbound|outboundAmount} 本商品 1+2+3 出库**金额**合计；{@code util|utilization|利用率} 同料**利用率**，无理论为末；
     *             {@code wasteloss|waste2loss3|损耗损失} 本商品 type2+type3 出库**重量**合计。空为 outbound。
     * @param sortOrder {@code desc|asc|降序|升序}，默认 {@code desc}，与 {@code sortBy} 组合控制升降序
     * @param goodsNameSearch 按分销商商品名称模糊匹配（含规格名、配方侧名称提示）；空则不过滤
     * @param verificationStatus 核销状态筛选：{@code all}（默认全量）、{@code verified}（已核销）、{@code unverified}（未核销）
     * @param page 页码，从 1 起；仅当 {@code pageSize} 有效时参与分页
     * @param pageSize 每页条数，{@code null} 或 ≤0 表示不分页（返回全部匹配行，兼容旧调用）
     */
    Map<String, Object> buildOutboundIngredientAnalysisReport(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String sortBy, String sortOrder, String goodsNameSearch, String verificationStatus,
            Integer page, Integer pageSize);

    /**
     * 按 {@code /ingredientAnalysis} 同一口径生成各菜的 {@code ingredientRows}（Map 列表），供部门菜品列表等复用。
     * <p>会将 {@code foodIds} 并入分摊用的全表菜品集合，使本期内无销量但需展示的菜仍参与 {@code sumNeed} 等汇总。</p>
     * <p>门店范围由 {@code depFatherId} 解析下属门店（与配料分析不传 {@code searchDepId} 时一致）。若需单子部门请用 {@link #buildIngredientRowsForFoodIds(String, String, Integer, Integer, String, Set)}。</p>
     */
    Map<Integer, List<Map<String, Object>>> buildIngredientRowsForFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Set<Integer> foodIds);

    /**
     * 同上，{@code searchDepId} 非空时限定单个子部门的销量与出库分摊口径。
     */
    Map<Integer, List<Map<String, Object>>> buildIngredientRowsForFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Set<Integer> foodIds);

    /**
     * 单菜配料看板：配料明细（在 {@code /ingredientAnalysis} 同行口径上扩展用量偏差、成本占比、建议等）、成本结构占比、按月的成本趋势点与综合建议文案。
     * <p>{@code trendGranularity} 当前仅支持 {@code month}；趋势区间缺省时为 {@code stopDate} 往前至多 6 个自然月（与主区间求交）。</p>
     *
     * @param trendStartDate 趋势起点（含），可空
     * @param trendEndDate   趋势终点（含），可空则取主区间 {@code stopDate}
     * @param primaryDisGoodsId 趋势曲线聚焦的原料；可空则取主区间内「单份实际成本」最高的配料
     */
    Map<String, Object> buildDishIngredientDashboard(String startDate, String stopDate, Integer disId, Integer depFatherId,
            Integer foodId, String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId);

    /**
     * 各菜在区间内、部门 scope 下 type1+2+3 摊销后的**单份实际成本**（元/份），与 {@code buildIngredientAnalysisDishRow} 中 {@code actualCostPerPortion} 同口径。
     * <p>供部门菜品 {@code gbDfBusinessInsight} 等列表展示，与 {@code grossMarginRateOnListPrice}（仅 type1）区分。</p>
     */
    default Map<Integer, BigDecimal> getDishActualCostPerPortion123ByFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Set<Integer> foodIds) {
        return getDishActualCostPerPortion123ByFoodIds(startDate, stopDate, disId, depFatherId, null, foodIds, null);
    }

    /**
     * 同 {@link #getDishActualCostPerPortion123ByFoodIds}，可限定 {@code searchDepId} 单个子部门。
     */
    default Map<Integer, BigDecimal> getDishActualCostPerPortion123ByFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Set<Integer> foodIds) {
        return getDishActualCostPerPortion123ByFoodIds(startDate, stopDate, disId, depFatherId, searchDepId, foodIds, null);
    }

    Map<Integer, BigDecimal> getDishActualCostPerPortion123ByFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Set<Integer> foodIds, Collection<Integer> scopeDepartmentIdsAllowFilter);

    /**
     * 与 {@code /gbDishCostAnalysis/dishIngredientDashboard} 的 {@code dish}、{@code /ingredientAnalysis} 按菜行一致：
     * 各菜 {@code theoryCostPerPortion}、{@code actualCostPerPortion}（type1+2+3 摊销）、{@code diffCostPerPortion}、{@code salesPortions}（字符串）。
     * <p>注意：与 {@link #buildReport} {@code salesDishRows} 的 {@code theoryCostAmount}/{@code actualCostAmount} 不同——后者整菜「实际」侧仅按生产出库 type1 重量分摊计价，未并入 type2/3 分摊金额。</p>
     */
    Map<Integer, Map<String, String>> getDishPerPortionCosts123ByFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Set<Integer> foodIds);

    /**
     * 单日、单分销商商品：配方理论出库量（配料行 {@code theoryUsage} 之和）。
     * <p><b>毛利贡献金额</b>：各涉及菜品上，按理论成本占比将「标价×销量」分摊到该料行后，减去该料行
     * {@code actualCostPerPortion}×销量（type1+2+3 分摊成本，与 {@code dayOutbound123Subtotal} 同属 1+2+3 口径），
     * 再汇总；<b>不是</b> {@link #buildDishIngredientDashboard} 里 {@code grossProfitContributionPerPortion}（后者仅扣 type1 {@code produceCostPerPortion}）。</p>
     *
     * @return {@code theoryOutboundQty}、{@code grossProfitContributionTotal} 均为两位小数字符串；
     *         {@code dishIngredientDayBreakdown} 为按菜行列表（实销份数、实收、本料配方用量、理论总用量、标价分摊收入、1+2+3 分摊成本、毛利贡献；金额类两位小数）
     */
    Map<String, Object> summarizeDisGoodsDayForReduceCurve(String day, Integer disId, Integer disGoodsId, String searchDepId);

    /**
     * 单菜配料消耗排查：区间内累计销量、各配料理论/实际消耗、type1 分摊及关联菜品累计分摊；不含日趋势。
     * <p>{@code dishId} 与 {@code foodId} 同义；区间结束日参数 {@code stopDate} 与 {@code endDate} 二选一。</p>
     */
    default Map<String, Object> buildDishIngredientConsumptionAudit(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Integer foodId) {
        return buildDishIngredientConsumptionAudit(startDate, stopDate, disId, depFatherId, null, foodId, null);
    }

    Map<String, Object> buildDishIngredientConsumptionAudit(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Integer foodId,
            Collection<Integer> scopeDepartmentIdsAllowFilter);

    /**
     * 合并的单菜配料看板+消耗排查：一次数据加载，同时返回 {@code dishIngredientDashboard} 与 {@code dishIngredientConsumptionAudit} 的完整数据。
     * <p>消除原先两个接口分别调用 {@code loadIngredientAnalysisData} 的重复开销。{@code searchDepId} 非空时限定子部门口径（与 subDepId 对应）。</p>
     * <p>趋势参数 {@code trendStartDate}/{@code trendEndDate}/{@code trendGranularity}/{@code primaryDisGoodsId} 可空，逻辑与 {@link #buildDishIngredientDashboard} 一致。</p>
     *
     * @return 合并响应：{@code dish}（看板）、{@code ingredientRows}（看板配料行）、{@code costStructure}、{@code costTrend}、{@code summarySuggestionZh}、{@code disclaimerZh}；
     *         {@code dishSummary}（消耗排查整菜汇总）、{@code consumptionAuditIngredients}（消耗排查配料行，含 relatedDishAllocations、stockReduceRecords）、{@code scopeOutboundSubtotals}
     */
    Map<String, Object> buildDishIngredientDashboardAndAudit(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Integer foodId,
            String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId);
}
