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
 * <p>{@code /ingredientAnalysis}：销售汇总 + 按菜配方理论 + type1/2/3 分摊成本；{@code devianceRate} = 仅 type1 生产分摊 {@code actualProduceUsage}÷{@code theoryUsage}（与 {@code produceAllocatedPerSoldPortion}÷{@code recipeUnitPerDish} 等价）；全量出库见 {@code actualUsage}，见 {@code data.disclaimerZh}。</p>
 * <p>{@code /outboundIngredientAnalysis}：按商汇总；支持 {@code goodsNameSearch} 商品名筛选与 {@code page}/{@code pageSize} 分页；不分页时不传 {@code pageSize}；{@code summary}、{@code devianceDistribution} 仍为全量口径。</p>
 * <p>{@code /dishIngredientDashboard}：单菜配料看板+消耗排查（已合并原 dashboard 与 audit 接口）；一次请求返回成本结构/趋势/建议 + 出库偏差/关联菜品/出库批次。</p>
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
     *                {@code deviance|devianceRate|偏差率} 按本料行**偏差率**，无理论量的排后；
     *                {@code devianceAmount|devianceCost|偏差金额} 按本料行**偏差金额**（实际成本−理论成本）
     * @param sortOrder {@code desc|降序}（默认）、{@code asc|升序}，与 {@code sortBy} 联用
     * @param goodsNameSearch 商品名称关键字（模糊匹配 {@code gbDgGoodsName}、{@code gbDgGoodsStandardname}）
     * @param verificationStatus 核销状态筛选：{@code all}（默认全量）、{@code verified}（已核销）、{@code unverified}（未核销）、{@code employeeMeal}（type6 员工餐单独）
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
     * 单菜配料看板+消耗排查（合并接口）：一次请求返回看板的成本结构/趋势/建议 + 消耗排查的出库偏差/关联菜品/出库批次。
     * <p>继承原 {@code /dishIngredientDashboard} 与 {@code /dishIngredientConsumptionAudit} 的全部字段，消除重复数据加载。</p>
     * <p>必填：{@code startDate}、{@code stopDate}、{@code disId}、{@code depFatherId}、{@code foodId}。</p>
     * <p>可选：{@code subDepId} 限定子部门口径；{@code trendStartDate}/{@code trendEndDate} 趋势区间；
     * {@code trendGranularity} 仅支持 {@code month}；{@code primaryDisGoodsId} 趋势聚焦原料。</p>
     *
     * <p>返回结构：</p>
     * <ul>
     *   <li><b>看板字段</b>：{@code dish}（菜品基本信息+毛利率）、{@code ingredientRows}（配料行含偏差/占比/建议）、{@code costStructure}（成本结构占比）、{@code costTrend}（按月趋势）、{@code summarySuggestionZh}、{@code disclaimerZh}</li>
     *   <li><b>排查字段</b>：{@code dishSummary}（整菜出库量额差异+毛利率差异）、{@code consumptionAuditIngredients[]}（每料12字段含出库偏差/分摊/关联菜品对比/出库批次记录）</li>
     * </ul>
     *
     * @param subDepId 子部门 ID，非空时限定该子部门的口径（与原 audit 接口的 subDepId 一致）
     * @param trendStartDate 趋势起点，可空则取 {@code stopDate} 往前 5 个自然月与主区间求交
     * @param trendEndDate   趋势终点，可空则同主区间 {@code stopDate}
     * @param trendGranularity 仅支持 {@code month}
     * @param primaryDisGoodsId 趋势聚焦的原料 id，可空则取主区间内单份实际成本最高的配料
     */
    @RequestMapping(value = "/dishIngredientDashboard", method = RequestMethod.POST)
    @ResponseBody
    public R dishIngredientDashboard(String startDate, String stopDate, Integer disId, Integer depFatherId,
            @RequestParam(value = "subDepId", required = false) Integer subDepId,
            Integer foodId,
            String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId) {
        String scopeDepStr = subDepId != null ? String.valueOf(subDepId) : null;
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildDishIngredientDashboardAndAudit(
                    startDate, stopDate, disId, depFatherId, scopeDepStr, foodId,
                    trendStartDate, trendEndDate, trendGranularity, primaryDisGoodsId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }


}
