package com.nongxinle.controller;

import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.utils.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 菜品成本 / 出库分析、配料分析。
 * <p>{@code /report} 中 {@code reportKind=salesDish}：按销售菜品与配方分摊；<b>按菜成本、配料均价仅基于 type=1（生产）出库</b>。</p>
 * <p>{@code reportKind=outboundQty}：按出库商品聚合下列菜品，同源 type=1 分摊。</p>
 * <p>响应 {@code data.scopeOutboundSubtotals}：同统计范围下 type 1/2/3 出库金额小计及损耗率 {@code wasteLossRatioInOutbound123}（(2+3)/(1+2+3)×100，百分数字符串两位小数），
 * 与按菜行成本口径分离，供老板看区间整体结构；说明见 {@code data.bossColumnHintsZh.scopeOutboundSubtotals}。</p>
 * <p>{@code /ingredientAnalysis}：销售汇总 + 按菜配方理论 + type1/2/3 分摊成本；{@code utilizationRate} = 仅 type1 生产分摊 {@code actualProduceUsage}÷{@code theoryUsage}（与 {@code produceAllocatedPerSoldPortion}÷{@code recipeUnitPerDish} 等价）；全量出库见 {@code actualUsage}，见 {@code data.disclaimerZh}。</p>
 * <p>{@code /outboundIngredientAnalysis}：按商汇总；商品行利用率为 Σ type1 生产分摊÷Σ 配方理论；{@code actualUsage} 仍为 type1+2+3 合计。</p>
 * <p>{@code /dishIngredientDashboard}：单菜配料独立页；含扩展配料行、{@code costStructure}（成本结构占比）、{@code costTrend}（按月单份成本趋势）及 {@code summarySuggestionZh}。</p>
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
     * 配料分析：销售汇总、配方理论用量、type1+2+3 分摊与利用率分档；与 {@code /report} 同一筛选参数；日期结束优先 {@code endDate}，未传时与 {@code stopDate} 同义。
     *
     * @param sortBy 菜品行排序：{@code sales|salesAmount|销量} 按实收销售额降序（默认）；{@code diff|diffCostPerPortion} 按每份成本差异绝对值降序
     */
    @RequestMapping(value = "/ingredientAnalysis", method = RequestMethod.POST)
    @ResponseBody
    public R ingredientAnalysis(String startDate, String stopDate, String endDate, Integer disId, String searchDepId,
            Integer depFatherId, String sortBy) {
        String end = endDate != null && !endDate.isEmpty() ? endDate : stopDate;
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildIngredientAnalysisReport(
                    startDate, end, disId, searchDepId, depFatherId, sortBy);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 按分销商商品（配料）维度的出库分析；区间与 {@code /ingredientAnalysis} 同；日期结束优先 {@code endDate}。
     *
     * @param sortBy {@code outbound|outboundAmount|出库金额} 按本商品 1+2+3 出库**金额**降序（默认）；
     *                {@code util|utilization|利用率} 按本料行**利用率**降序，无理论量的排后；
     *                {@code wasteloss|wasteAndLoss|损耗损失|损耗} 按 type2+type3 出库**重量**降序
     */
    @RequestMapping(value = "/outboundIngredientAnalysis", method = RequestMethod.POST)
    @ResponseBody
    public R outboundIngredientAnalysis(String startDate, String stopDate, String endDate, Integer disId, String searchDepId,
            Integer depFatherId, String sortBy) {
        String end = endDate != null && !endDate.isEmpty() ? endDate : stopDate;
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildOutboundIngredientAnalysisReport(
                    startDate, end, disId, searchDepId, depFatherId, sortBy);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }

    /**
     * 单菜配料看板（独立页）：主区间内的配料明细（在 {@code /ingredientAnalysis} 同行基础上扩展偏差、占比、建议等）、成本结构占比、按月成本趋势与综合建议。
     *
     * @param trendStartDate 趋势起点，可空则取 {@code endDate} 往前 5 个自然月与主区间求交
     * @param trendEndDate   趋势终点，可空则同主区间 {@code endDate}
     * @param trendGranularity 仅支持 {@code month}
     * @param primaryDisGoodsId 趋势聚焦的原料 id，可空则取主区间内单份实际成本最高的配料
     */
    @RequestMapping(value = "/dishIngredientDashboard", method = RequestMethod.POST)
    @ResponseBody
    public R dishIngredientDashboard(String startDate, String endDate, Integer disId, Integer depFatherId, Integer foodId,
            String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId) {
        try {
            Map<String, Object> data = gbDishCostAnalysisService.buildDishIngredientDashboard(
                    startDate, endDate, disId, depFatherId, foodId, trendStartDate, trendEndDate, trendGranularity, primaryDisGoodsId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(-1, e.getMessage());
        }
    }
}
