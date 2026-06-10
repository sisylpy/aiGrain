package com.nongxinle.controller;

import com.nongxinle.dto.GbDishIngredientConsumptionAuditRequest;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.utils.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 菜品成本 / 出库分析、配料分析。
 * <p>{@code /report} 中 {@code reportKind=salesDish}：按销售菜品与配方分摊；<b>按菜成本、配料均价仅基于 type=1（生产）出库</b>。</p>
 * <p>{@code reportKind=outboundQty}：按出库商品聚合下列菜品，同源 type=1 分摊。</p>
 * <p>响应 {@code data.scopeOutboundSubtotals}：同统计范围下 type 1/2/3/6 出库金额小计及损耗率 {@code wasteLossRatioInOutbound123}（(2+3)/(1+2+3)×100，百分数字符串两位小数），
 * type=6 原料型员工餐见 {@code subtotalEmployeeMealType6}；
 * 与按菜行成本口径分离，供老板看区间整体结构；说明见 {@code data.bossColumnHintsZh.scopeOutboundSubtotals}。</p>
 * <p>{@code /ingredientAnalysis}：销售汇总 + 按菜配方理论 + type1/2/3 分摊成本；{@code utilizationRate} = 仅 type1 生产分摊 {@code actualProduceUsage}÷{@code theoryUsage}（与 {@code produceAllocatedPerSoldPortion}÷{@code recipeUnitPerDish} 等价）；全量出库见 {@code actualUsage}，见 {@code data.disclaimerZh}。</p>
 * <p>{@code /outboundIngredientAnalysis}：按商汇总；支持 {@code goodsNameSearch} 商品名筛选与 {@code page}/{@code pageSize} 分页；不分页时不传 {@code pageSize}；{@code summary}、{@code utilizationDistribution} 仍为全量口径。</p>
 * <p>{@code /dishIngredientDashboard}：单菜配料独立页；含扩展配料行、{@code costStructure}（成本结构占比）、{@code costTrend}（按月单份成本趋势）及 {@code summarySuggestionZh}。</p>
 * <p>{@code /dishIngredientConsumptionAudit}：单菜配料消耗排查；按 {@code startDate}～{@code endDate}/{@code stopDate} 汇总销量、理论/实际消耗与 type1 分摊，不含日趋势。</p>
 */
@Slf4j
@RestController
@RequestMapping("gbDishCostAnalysis")
@RequiredArgsConstructor
public class GbDishCostAnalysisController {

    private final GbDishCostAnalysisService gbDishCostAnalysisService;

    /**
     * @param reportKind {@code salesDish}（默认）| {@code outboundQty}；大小写不敏感
     * @return {@code data} 含 {@code salesDishRows} / {@code outboundGoodsRows}、{@code scopeOutboundSubtotals}、{@code bossColumnHintsZh}
     */
    @RequestMapping(value = "/report", method = RequestMethod.POST)
    @ResponseBody
    public R report(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String reportKind) {
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildReport(
                    startDate, stopDate, disId, searchDepId, depFatherId, reportKind);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 配料分析：销售汇总、配方理论用量、type1+2+3 分摊与利用率分档；与 {@code /report} 同一筛选参数（含 {@code stopDate} 为区间结束日）。
     * <p>可选 {@code subDepId}：子部门 ID；不传则在 {@code depFatherId}（可空表示不限制父部门）下汇总下属门店口径，见服务层 {@code resolveScopeDepIds}。</p>
     * <p>{@code data.scopeSalesSubtotals} 为顶部成本汇总：{@code actualCostTotal}、{@code theoreticalCostTotal}、{@code costDeviationTotal}、{@code costDeviationRate}（百分数字符串，如 {@code "82.49"}）。</p>
     *
     * @param sortBy 菜品行排序：{@code sales|salesAmount|销量} 实收销售额；
     *               {@code diff|diffCostPerPortion|成本差异} 每份成本差异绝对值；
     *               {@code diffRate|diffRatePerPortion|成本偏差率} 每份成本偏差率 {@code (实际−理论)÷理论}；
     *               {@code actualCost|actualCostPerPortion|单份实际成本} 每道菜<strong>单份</strong>实际成本（type1+2+3，见 {@code actualCostPerPortion}），非区间内销售额合计成本；
     *               {@code ingredientCount|配料数量} 有效配方配料行数
     * @param sortOrder {@code desc|降序}（默认）、{@code asc|升序}
     */
    @RequestMapping(value = "/ingredientAnalysis", method = RequestMethod.POST)
    @ResponseBody
    public R ingredientAnalysis(String startDate, String stopDate, Integer disId,
            @RequestParam(value = "subDepId", required = false) Integer subDepId,
            Integer depFatherId, String sortBy, String sortOrder) {
        String scopeDepStr = subDepId != null ? String.valueOf(subDepId) : null;
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildIngredientAnalysisReport(
                    startDate, stopDate, disId, scopeDepStr, depFatherId, sortBy, sortOrder);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 按分销商商品（配料）维度的出库分析；区间与 {@code /ingredientAnalysis} 同（{@code stopDate} 为区间结束日）。
     * <p>可选 {@code subDepId}：子部门 ID；不传则在 {@code depFatherId} 下汇总下属门店口径。</p>
     * <p>{@code data.summary} 顶部三张卡片：{@code totalOutboundAmount}（含员工餐）、{@code verifiedTotalAmount}（核销，附 {@code verifiedGrossMarginRate}、{@code verifiedTheoryDeviationAmount}、{@code verifiedTheoryDeviationRate}）、{@code nonVerifiedTotalAmount}（非核销，附 {@code nonVerifiedDishCount}）。</p>
     * <p>配料行 {@code ingredientsAnalysis[]} 主展示：{@code gbDgGoodsName}、{@code gbDgGoodsFileImg}；
     * {@code outboundTotalAmount}（type1+2+3 出库金额，= verified + nonVerified）、
     * {@code verifiedAmount}、{@code nonVerifiedAmount}、{@code employeeMealAmount}（type6 单独）。</p>
     *
     * @param sortBy {@code outbound|outboundAmount|出库金额} 按本商品 1+2+3 出库**金额**（默认键）；
     *                {@code util|utilization|利用率} 按本料行**利用率**，无理论量的排后；
     *                {@code wasteloss|wasteAndLoss|损耗损失|损耗} 按 type2+type3 出库**重量**
     * @param sortOrder {@code desc|降序}（默认）、{@code asc|升序}，与 {@code sortBy} 联用
     * @param goodsNameSearch 商品名称关键字（模糊匹配 {@code gbDgGoodsName}、{@code gbDgGoodsStandardname}）
     * @param verificationStatus 核销状态筛选：{@code all}（默认全量）、{@code verified}（已核销）、{@code unverified}（未核销）
     * @param page 页码从 1 起；与 {@code pageSize} 同时使用
     * @param pageSize 每页条数；不传或 ≤0 则不分页，返回全部配料行（兼容旧客户端）
     */
    @RequestMapping(value = "/outboundIngredientAnalysis", method = RequestMethod.POST)
    @ResponseBody
    public R outboundIngredientAnalysis(String startDate, String stopDate, Integer disId,
            @RequestParam(value = "subDepId", required = false) Integer subDepId,
            Integer depFatherId, String sortBy, String sortOrder, String goodsNameSearch, String verificationStatus,
            Integer page, Integer pageSize) {
        String scopeDepStr = subDepId != null ? String.valueOf(subDepId) : null;
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildOutboundIngredientAnalysisReport(
                    startDate, stopDate, disId, scopeDepStr, depFatherId, sortBy, sortOrder, goodsNameSearch,
                    verificationStatus, page, pageSize);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 单菜配料看板（独立页）：主区间内的配料明细（在 {@code /ingredientAnalysis} 同行基础上扩展偏差、占比、建议等）、成本结构占比、按月成本趋势与综合建议。
     *
     * @param trendStartDate 趋势起点，可空则取 {@code stopDate} 往前 5 个自然月与主区间求交
     * @param trendEndDate   趋势终点，可空则同主区间 {@code stopDate}
     * @param trendGranularity 仅支持 {@code month}
     * @param primaryDisGoodsId 趋势聚焦的原料 id，可空则取主区间内单份实际成本最高的配料
     */
    @RequestMapping(value = "/dishIngredientDashboard", method = RequestMethod.POST)
    @ResponseBody
    public R dishIngredientDashboard(String startDate, String stopDate, Integer disId, Integer depFatherId, Integer foodId,
            String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId) {
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildDishIngredientDashboard(
                    startDate, stopDate, disId, depFatherId, foodId, trendStartDate, trendEndDate, trendGranularity, primaryDisGoodsId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 菜品配料消耗排查：区间内累计销量、配料理论/实际消耗、type1 生产分摊及关联菜品累计分摊。
     * <p>制作(type1)参与菜品分摊；损耗(type2)/废弃(type3)仅计入实际消耗与明细，不分摊到菜品成本。</p>
     * <p>{@code dishSummary}：整菜汇总（销量、出库 qty/amount 差异、整菜毛利率）。</p>
     * <p>{@code ingredients[]} 每条配料含页面 12 核心字段：
     * {@code theoryOutboundQty}、{@code theoryOutboundAmount}、{@code outboundCostDeviation}、
     * {@code actualOutboundQty}、{@code actualOutboundAmount}、{@code outboundQtyDeviation}、
     * {@code theoryGrossMarginRate}、{@code actualGrossMarginRate}、{@code grossMarginRateDeviation}、
     * {@code dishAllocationShare}、{@code dishAllocatedQty}、{@code dishAllocatedAmount}（均为本菜对该料口径，两位小数；占比为百分数如 {@code "45.00"}）。</p>
     * <p>{@code ingredients[].relatedDishAllocations[]}：共用该料的其它菜品对比，字段与上同构；毛利率为整菜口径。</p>
     * <p>{@code ingredients[].stockReduceRecords[]} 每条出库含 {@code purchaseBatch}：采购日、采购员/供货商、采购总量、该次出库前库存批次剩余。</p>
     */
    @RequestMapping(value = "/dishIngredientConsumptionAudit", method = RequestMethod.POST)
    @ResponseBody
    public R dishIngredientConsumptionAudit(@RequestBody GbDishIngredientConsumptionAuditRequest body) {
        if (body == null) {
            return R.error(-1, "请求体不能为空");
        }
        String startDate = body.getStartDate() != null ? body.getStartDate().trim() : null;
        String rangeEnd = body.resolvedEndDate();
        Integer disId = body.resolvedDisId();
        Integer resolvedFoodId = body.resolvedFoodId();
        String scopeDepStr = body.getSubDepId() != null ? String.valueOf(body.getSubDepId()) : null;
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildDishIngredientConsumptionAudit(
                    startDate, rangeEnd, disId, body.getDepFatherId(), scopeDepStr, resolvedFoodId, null);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }
}
