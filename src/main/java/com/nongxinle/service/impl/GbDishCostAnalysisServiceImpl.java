package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import com.nongxinle.utils.GbDepFoodSalesMetricsSupport;
import com.nongxinle.utils.GrossMarginStandardDisplay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;

/**
 * {@link #REPORT_KIND_SALES_DISH}：以销售菜品为主，含配料行与整菜出库可支撑瓶颈；按菜分摊的出库均价仅来自 type=1（生产）。<br>
 * {@link #REPORT_KIND_OUTBOUND_QTY}：以本期 type=1 出库商品为主下列关联菜品；另返回区间 type1/2/3 金额与损耗率供老板看整体结构。
 * <p>出库按菜分摊的数学约定、降级顺序、type2/3 与 type1 的关系见项目文档 {@code docs/gb-dish-cost-allocation-model.md}；核心实现为
 * {@link #allocateOutboundWeightForDishGood} 与 {@link #buildSumNeedByGoods}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbDishCostAnalysisServiceImpl implements GbDishCostAnalysisService {

    /** 以销售菜品为主（原「按菜看可支撑」口径）。 */
    private static final String REPORT_KIND_SALES_DISH = "salesdish";
    /** 以出库数量为主（按分销商商品聚合下列菜）。 */
    private static final String REPORT_KIND_OUTBOUND_QTY = "outboundqty";
    private static final int IN_BATCH = 900;
    /** {@link #buildOutboundIngredientAnalysisReport} 分页时 {@code pageSize} 上限。 */
    private static final int OUTBOUND_INGREDIENT_MAX_PAGE_SIZE = 500;

    /** 报表 data.bossColumnHintsZh：与 JSON 字段键一一对应的老板可读说明（白话）。 */
    private static final Map<String, String> BOSS_HINTS_SALES_DISH_ROW_ZH;
    private static final Map<String, String> BOSS_HINTS_BOTTLE_ZH;
    private static final Map<String, String> BOSS_HINTS_INGREDIENT_ROW_ZH;
    private static final Map<String, String> BOSS_HINTS_SCOPE_OUTBOUND_ZH;
    static {
        LinkedHashMap<String, String> dish = new LinkedHashMap<>();
        dish.put("foodId", "菜品在系统里的编号");
        dish.put("foodName", "菜名");
        dish.put("soldPortions", "这段统计时间里，这道菜一共卖了多少份");
        dish.put("theoryInboundQtyTotal", "按配方粗算：卖这么多份大概要耗多少料（不同料单位数字直接加，看个大概）");
        dish.put("actualInboundQtyTotal", "销售开单明细里，各原料用量加起来的总数");
        dish.put("outboundAllocatedQtyTotal", "仓库/成本出库里，按比例摊到这道菜上一共有多少（多道菜抢同一料时，优先按「各菜该料理论消耗 q×单份用量」占比分，短料时反推份数不会超过实销）");
        dish.put("theoryCostAmount", "按「该用多少料」算出来的成本（元）");
        dish.put("actualCostAmount", "按「摊给你的出库」算出来的成本（元）");
        dish.put("diffCostAmount", "实际比理论多还是少花了多少钱（元）");
        dish.put("absDiffCostAmountSum", "各原料成本差多少，绝对值加总（用来抓偏差大的菜）");
        dish.put("bottle", "瓶颈汇总：整菜可支撑份数、卡脖子原料编号、摊给本菜的出库斤数、该料主档规格与鲜品等，见内层字段说明（bossColumnHintsZh.bottle）");
        dish.put("sortKey", "给系统排序用的权重，老板一般不用看");
        dish.put("hint", "一句话经营提示");
        dish.put("ingredientRows", "这道菜用了哪些料、各占多少（展开看每一行）");
        BOSS_HINTS_SALES_DISH_ROW_ZH = Collections.unmodifiableMap(dish);

        LinkedHashMap<String, String> bottleHints = new LinkedHashMap<>();
        bottleHints.put("soldPortions", "本菜本期实销份数（与菜品行 soldPortions 相同，便于只读 bottle 一节）");
        bottleHints.put("supportedPortions", "按最缺的那一样原料，整道菜从出库分摊角度大约还能对上多少份");
        bottleHints.put("disGoodsId", "最卡脖子的分销商商品编号（无出库可选瓶颈时为 null）");
        bottleHints.put("goodsName", "瓶颈原料名称（来自配方合并行；无瓶颈时为 null）");
        bottleHints.put("theoryQtyFromSales", "瓶颈料在本菜销售子表 gb_dep_food_goods_sales 中的用量合计（与 ingredientRows 同 disGoodsId 的 theoryQtyFromSales 一致；无瓶颈时为 null）");
        bottleHints.put("theoryOutboundQtyByRecipe", "瓶颈料：实销份数×本菜该料合并单份配方用量（与 ingredientRows 同口径；无瓶颈时为 null）");
        bottleHints.put("outboundAllocatedQty", "瓶颈料摊给本菜的出库斤数（无瓶颈时为 null）");
        bottleHints.put("theorySalesCostAmount", "仅瓶颈料 disGoodsId：子表用量 theoryQtyFromSales×本期该料出库均价（元），与 ingredientRows 同料的 salesIngredientCostAmount 一致；无瓶颈为 null");
        bottleHints.put("recipeSalesCostAmount", "仅瓶颈料：实销×配方用量 theoryOutboundQtyByRecipe×同上均价（元），与 ingredientRows 的 recipeTheoryIngredientCostAmount 一致；无瓶颈为 null");
        bottleHints.put("outboundAllocatedCostAmount", "仅瓶颈料：摊给出库 outboundAllocatedQty×同上均价（元），与 ingredientRows 的 outboundAllocatedIngredientCostAmount 一致；无瓶颈为 null");
        bottleHints.put("soldVsSupportedPortionDiff", "本菜实销份数 − bottle.supportedPortions（可正可负；与 sortKey 中份数差同源）");
        bottleHints.put("recipeSalesVsOutboundCostDiff", "瓶颈料：recipeSalesCostAmount − outboundAllocatedCostAmount（元）；无瓶颈为 null");
        bottleHints.put("theoryQtyFromSalesVsOutboundAllocDiff", "瓶颈料：销售子表用量 theoryQtyFromSales − 摊销出库斤数 outboundAllocatedQty（与 plainQty 尺度一致）；无瓶颈为 null");
        bottleHints.put("recipeTheoryQtyVsOutboundAllocDiff", "瓶颈料：配方推算用量 theoryOutboundQtyByRecipe − 摊销出库斤数 outboundAllocatedQty；无瓶颈为 null");
        bottleHints.put("gbDgGoodsStandardname", "瓶颈料主档规格名称");
        bottleHints.put("gbDgControlFresh", "瓶颈料是否管控鲜度（0/1 等）");
        bottleHints.put("gbDgFreshWarnHour", "瓶颈料鲜品预警时长（字符串存库）");
        bottleHints.put("gbDgFreshWasteHour", "瓶颈料鲜品报废/浪费时长相关配置");
        bottleHints.put("gbDgGoodsStandardWeight", "瓶颈料标准重量说明");
        bottleHints.put("gbDgGoodsFileImg", "瓶颈料图片路径");
        BOSS_HINTS_BOTTLE_ZH = Collections.unmodifiableMap(bottleHints);

        LinkedHashMap<String, String> ing = new LinkedHashMap<>();
        ing.put("disGoodsId", "原料在系统里的商品编号");
        ing.put("goodsName", "原料名称");
        ing.put("recipeUnitPerDish", "每做一份这道菜，这条料标准该用多少");
        ing.put("theoryQtyFromSales", "销售开单明细里，这条料一共写了用多少");
        ing.put("theoryOutboundQtyByRecipe", "按实际卖了多少份乘配方，这条料按理该用多少");
        ing.put("outboundAllocatedQty",
                "仅 type1（生产）出库：摊给本菜的重量（斤）；与看板 `actualProduceUsage` 同源量级；**不等于**看板 `actualUsage`（后者为 type1+2+3 合计）。");
        ing.put("supportedPortionsThisGood",
                "本行 outboundAllocatedQty（仅 type1 生产出库分摊重量）÷ recipeUnitPerDish（合并后单份配方用量，斤/份），"
                        + "表示「这条料按当前摊到的出库，够做几份菜」；**除数必须是单份配方，不得用本期理论总用量 theoryOutboundQtyByRecipe**（误用会得到 10 这类与系统 30 不一致的数）。");
        ing.put("salesIngredientCostAmount", "按销售子表里这条料的用量 theoryQtyFromSales×本期该料出库均价（元）；均价仅来自本期该料 type=1（生产）出库：W_g>0 时 subtotal/weight；W_g=0 时金额为 0（无法从扣库汇总推单价）");
        ing.put("recipeTheoryIngredientCostAmount", "按配方推算用量 theoryOutboundQtyByRecipe×同上均价（元）；与子表用量对照用，不是「销售录入」口径");
        ing.put("outboundAllocatedIngredientCostAmount", "按本条摊得的出库斤数 outboundAllocatedQty×同上均价（元），与 supportedPortionsThisGood 同源分摊");
        ing.put("recipeSalesVsOutboundCostDiff", "本料：recipeTheoryIngredientCostAmount − outboundAllocatedIngredientCostAmount（元）");
        ing.put("soldVsSupportedPortionDiff", "本菜实销份数 − 本料 supportedPortionsThisGood（可正可负；与 bottle.soldVsSupportedPortionDiff 同源实销、本料可支撑）");
        ing.put("recipeTheoryQtyVsOutboundAllocDiff", "本料：theoryOutboundQtyByRecipe − outboundAllocatedQty 数值差（plainQty 尺度）");
        ing.put("gbDgControlFresh", "批发商商品是否管控鲜度（0/1 等，见 gb_distributer_goods）");
        ing.put("gbDgFreshWarnHour", "鲜品预警时长（小时类配置，字符串存库）");
        ing.put("gbDgFreshWasteHour", "鲜品报废/浪费时长相关配置");
        ing.put("gbDgGoodsStandardWeight", "商品标准重量说明（规格侧文案）");
        ing.put("gbDgGoodsStandardname", "商品规格名称（如「500g/袋」等，见主档）");
        BOSS_HINTS_INGREDIENT_ROW_ZH = Collections.unmodifiableMap(ing);

        LinkedHashMap<String, String> scope = new LinkedHashMap<>();
        scope.put("subtotalProduceType1", "本报表统计范围与日期内，type=1（生产成本）出库金额合计（元）；按菜分摊、配料均价只用这一类。");
        scope.put("subtotalWasteType2", "同上范围内 type=2（损耗）出库金额合计（元）。");
        scope.put("subtotalLossType3", "同上范围内 type=3（损失）出库金额合计（元）。");
        scope.put("subtotalOutbound123", "type 1+2+3 出库金额合计（元），不含退货；作损耗率分母。");
        scope.put("wasteLossAmountType23", "type 2+3 出库金额合计（元），作损耗率分子。");
        scope.put("wasteLossRatioInOutbound123", "损耗率 = (2+3 金额) ÷ (1+2+3 金额)，以百分数字符串返回、固定两位小数（如 \"5.23\" 表示 5.23%，不含 %）；分母为 0 时为 \"0.00\"。与按菜行的成本口径（仅 1）不同，是区间整体结构指标。");
        scope.put("subtotalEmployeeMealType6", "本报表统计范围与日期内，type=6（原料型员工餐）出库金额合计（元）；与 gb_dep_food_sales type=5 菜品型员工餐不同，单独列示。");
        BOSS_HINTS_SCOPE_OUTBOUND_ZH = Collections.unmodifiableMap(scope);
    }

    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbDepartmentService gbDepartmentService;
    /** 配料行上的鲜品/规格字段来自 {@code gb_distributer_goods}（与 disGoodsId 对应）。 */
    private final GbDistributerGoodsService gbDistributerGoodsService;

    @Override
    public Map<String, Object> buildReport(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String reportKind, Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (startDate == null || stopDate == null || disId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId 不能为空");
        }
        String rk = reportKind == null || reportKind.isEmpty() ? REPORT_KIND_SALES_DISH
                : reportKind.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace(" ", "");
        if (!REPORT_KIND_SALES_DISH.equals(rk) && !REPORT_KIND_OUTBOUND_QTY.equals(rk)) {
            throw new IllegalArgumentException("reportKind 仅支持 salesDish 或 outboundQty");
        }

        List<Integer> scopeDepIds = resolveScopeDepIds(disId, searchDepId, depFatherId, scopeDepartmentIdsAllowFilter);
        Map<String, Object> reduceParams = buildReduceParams(disId, searchDepId, depFatherId, startDate, stopDate);
        List<Map<String, Object>> reduceAgg =
                gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(reduceParams);

        BigDecimal scopeSub1 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceProduceTotal(reduceParams));
        BigDecimal scopeSub2 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceWasteTotal(reduceParams));
        BigDecimal scopeSub3 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceLossTotal(reduceParams));
        BigDecimal scopeSub6 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceEmployeeMealTotal(reduceParams));
        BigDecimal scopeSub123 = scopeSub1.add(scopeSub2).add(scopeSub3);
        BigDecimal scopeSub23 = scopeSub2.add(scopeSub3);
        BigDecimal wasteLossRatio = scopeSub123.compareTo(BigDecimal.ZERO) > 0
                ? scopeSub23.divide(scopeSub123, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // W_g：本期「仅 type=1 生产」出库按分销商商品 g 汇总的出库重量（与 reduceAgg.weightSum 一致，斤等实物单位）。
        // S_g、Q_g：见 buildRecipeGoodsAggregates。sumT_g：全报表各菜在销售子表 gb_dep_food_goods_sales 中该料用量 t 之和。
        Map<Integer, BigDecimal> reduceW = new HashMap<>();
        Map<Integer, BigDecimal> reduceS = new HashMap<>();
        for (Map<String, Object> row : reduceAgg) {
            Integer gid = toInt(row.get("disGoodsId"));
            if (gid == null) {
                continue;
            }
            reduceW.put(gid, toBd(row.get("weightSum")));
            reduceS.put(gid, toBd(row.get("subtotalSum")));
        }

        List<GbDepFoodSalesEntity> foodSales = loadFoodSales(startDate, stopDate, disId, scopeDepIds);
        Map<Integer, Integer> saleIdToFoodId = new HashMap<>();
        Map<Integer, BigDecimal> salesQtyByFood = new HashMap<>();
        Map<Integer, BigDecimal> consumptionQtyByFood = new HashMap<>();
        for (GbDepFoodSalesEntity s : foodSales) {
            Integer sid = s.getGbDepFoodSalesId();
            Integer fid = s.getGbDfsFoodId();
            if (sid == null || fid == null) {
                continue;
            }
            saleIdToFoodId.put(sid, fid);
            if (GbDepFoodSalesMetricsSupport.countsAsOperationalSales(s)) {
                salesQtyByFood.merge(fid, GbDepFoodSalesMetricsSupport.operationalSalesQty(s), BigDecimal::add);
            }
            if (GbDepFoodSalesMetricsSupport.countsAsIngredientConsumption(s)) {
                consumptionQtyByFood.merge(fid, GbDepFoodSalesMetricsSupport.totalConsumptionQty(s), BigDecimal::add);
            }
        }

        Set<Integer> saleIds = saleIdToFoodId.keySet();
        Map<Integer, Map<Integer, BigDecimal>> theoryWtByFoodAndGoods = new HashMap<>();
        if (!saleIds.isEmpty()) {
            List<Integer> idList = new ArrayList<>(saleIds);
            for (int i = 0; i < idList.size(); i += IN_BATCH) {
                int to = Math.min(i + IN_BATCH, idList.size());
                List<Integer> batch = idList.subList(i, to);
                LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<>();
                w.ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, startDate)
                        .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, stopDate)
                        .in(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, batch);
                applyDepFoodGoodsFilter(w, scopeDepIds);
                for (GbDepFoodGoodsSalesEntity r : gbDepFoodGoodsSalesService.list(w)) {
                    Integer fsId = r.getGbDfgsFoodSalesId();
                    Integer foodId = saleIdToFoodId.get(fsId);
                    Integer gId = r.getGbDfgsDisGoodsId();
                    if (foodId == null || gId == null) {
                        continue;
                    }
                    BigDecimal amt = GbDepartmentGoodsStockReduceSupport.coerceDecimal(r.getGbDfgsGoodsAmount());
                    theoryWtByFoodAndGoods
                            .computeIfAbsent(foodId, k -> new HashMap<>())
                            .merge(gId, amt, BigDecimal::add);
                }
            }
        }

        // sumT_g（sumTheoryByGoods）：原料 g 在报表涉及的全部销售明细里，各菜该料子表用量 t 的全局合计，用作「按子表消耗占比」分摊 W_g 的分母。
        Map<Integer, BigDecimal> sumTheoryByGoods = new HashMap<>();
        for (Map<Integer, BigDecimal> byG : theoryWtByFoodAndGoods.values()) {
            for (Map.Entry<Integer, BigDecimal> e : byG.entrySet()) {
                sumTheoryByGoods.merge(e.getKey(), e.getValue(), BigDecimal::add);
            }
        }

        Set<Integer> allFoodIds = new HashSet<>();
        allFoodIds.addAll(salesQtyByFood.keySet());
        allFoodIds.addAll(consumptionQtyByFood.keySet());
        allFoodIds.addAll(theoryWtByFoodAndGoods.keySet());
        Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId = loadRecipesByFoodIds(allFoodIds);
        RecipeGoodsAgg recipeAgg = buildRecipeGoodsAggregates(allFoodIds, consumptionQtyByFood, recipeByFoodId);
        Map<Integer, BigDecimal> sumRecipeUnitByGoods = recipeAgg.sumUByGoods;
        Map<Integer, BigDecimal> sumSalesQtyByGoods = recipeAgg.sumSalesByGoods;
        Map<Integer, BigDecimal> sumNeedByGoods = buildSumNeedByGoods(allFoodIds, consumptionQtyByFood, recipeByFoodId);
        if (log.isInfoEnabled()) {
            log.info("[dishCost] reportKind={} 区间={}~{} disId={} searchDepId={} depFatherId={} scopeDepIds={} allFoodIds={}",
                    rk, startDate, stopDate, disId, searchDepId, depFatherId, scopeDepIds, allFoodIds);
            log.info("[dishCost] 本期生产(type1)出库 W(disGoodsId->weight)={}", reduceW);
            log.info("[dishCost] S_g(Σu)={} Q_g(Σq)={}", sumRecipeUnitByGoods, sumSalesQtyByGoods);
            for (Map.Entry<Integer, BigDecimal> e : reduceW.entrySet()) {
                Integer gid = e.getKey();
                if (gid == null || nz(e.getValue()).signum() <= 0) {
                    continue;
                }
                log.info("[dishCostGlobal] disGoodsId={} W_g={} sumNeed_g={} Q_g={} S_g={} sumT_g={} (分摊:有sumNeed走W*本菜需求/sumNeed;否则Q;否则子表t;否则S)",
                        gid,
                        plainQty(e.getValue()),
                        plainQty(sumNeedByGoods.get(gid)),
                        plainQty(sumSalesQtyByGoods.get(gid)),
                        plainQty(sumRecipeUnitByGoods.get(gid)),
                        plainQty(sumTheoryByGoods.get(gid)));
            }
        }

        List<Map<String, Object>> salesDishRows = new ArrayList<>();
        List<Map<String, Object>> outboundGoodsRows = new ArrayList<>();
        // 配料行要带的鲜品/规格字段来自 gb_distributer_goods：按报表涉及配方去重后批量查，避免每行 getById 风暴
        Map<Integer, GbDistributerGoodsEntity> disGoodsById = loadDisGoodsDetailByRecipeGoods(allFoodIds, recipeByFoodId);
        if (REPORT_KIND_SALES_DISH.equals(rk)) {
            for (Integer foodId : allFoodIds) {
                salesDishRows.add(buildSalesDishRow(foodId, theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                        sumTheoryByGoods, sumRecipeUnitByGoods, sumSalesQtyByGoods, sumNeedByGoods, disGoodsById, reduceW, reduceS,
                        consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO), recipeByFoodId));
            }
            salesDishRows.sort(Comparator.comparing(o -> toBd(o.get("sortKey")), Comparator.reverseOrder()));
        } else {
            outboundGoodsRows.addAll(buildOutboundGoodsRows(allFoodIds, reduceW, reduceS, sumRecipeUnitByGoods, sumSalesQtyByGoods,
                    sumNeedByGoods, salesQtyByFood, theoryWtByFoodAndGoods, sumTheoryByGoods, disGoodsById));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportKind", REPORT_KIND_SALES_DISH.equals(rk) ? "salesDish" : "outboundQty");
        out.put("startDate", startDate);
        out.put("stopDate", stopDate);
        out.put("disId", disId);
        out.put("depFatherId", depFatherId);
        out.put("salesDishRows", REPORT_KIND_SALES_DISH.equals(rk) ? salesDishRows : null);
        out.put("outboundGoodsRows", REPORT_KIND_OUTBOUND_QTY.equals(rk) ? outboundGoodsRows : null);

        Map<String, Object> scopeOutbound = new LinkedHashMap<>();
        scopeOutbound.put("subtotalProduceType1", plainMoney(scopeSub1));
        scopeOutbound.put("subtotalWasteType2", plainMoney(scopeSub2));
        scopeOutbound.put("subtotalLossType3", plainMoney(scopeSub3));
        scopeOutbound.put("subtotalOutbound123", plainMoney(scopeSub123));
        scopeOutbound.put("wasteLossAmountType23", plainMoney(scopeSub23));
        scopeOutbound.put("wasteLossRatioInOutbound123",
                GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(wasteLossRatio));
        scopeOutbound.put("subtotalEmployeeMealType6", plainMoney(scopeSub6));
        out.put("scopeOutboundSubtotals", scopeOutbound);

        Map<String, Object> bossHints = new LinkedHashMap<>();
        bossHints.put("salesDishRow", BOSS_HINTS_SALES_DISH_ROW_ZH);
        bossHints.put("bottle", BOSS_HINTS_BOTTLE_ZH);
        bossHints.put("ingredientRow", BOSS_HINTS_INGREDIENT_ROW_ZH);
        bossHints.put("scopeOutboundSubtotals", BOSS_HINTS_SCOPE_OUTBOUND_ZH);
        out.put("bossColumnHintsZh", bossHints);

        return out;
    }

    private static final String INGREDIENT_ANALYSIS_DISCLAIMER_ZH =
            "扣减单未关菜品，type2（损耗）与 type3（损失）在「同商品、全店」的出库总量上，按本行 type1 摊得占全店 type1 的同一比例，分摊到本菜本行配料，不等同于责任到菜的扣减明细。"
                    + "配料行「偏差率 devianceRate」= 仅 type1（生产）出库摊到本菜的重量 actualProduceUsage（alloc1）÷ 配方理论 theoryUsage；不含 type2 损耗、type3 损失摊入分子（二者仍在 actualUsage、成本与分档外说明中体现）。"
                    + "「每份」对照：produceAllocatedPerSoldPortion÷recipeUnitPerDish 与上式等价；allocatedOutboundPerSoldPortion 为 type1+2+3 合计每份量。"
                    + "「销售子表用量 salesUsageFromOrders」来自 gb_dep_food_goods_sales 汇总，用于对照开单/配方，非仓库实物出库量。";

    private static final String OUTBOUND_INGREDIENT_DISCLAIMER_ZH = INGREDIENT_ANALYSIS_DISCLAIMER_ZH;

    /** 与 {@link #loadIngredientAnalysisData} 同口径，供按菜/按商两条报表复用。 */
    private static final class IngredientAnalysisData {
        final String startDate;
        final String stopDate;
        final Integer disId;
        final String searchDepId;
        final Integer depFatherId;
        final List<Integer> scopeDepIds;
        final Map<String, Object> reduceParams;
        final Map<Integer, BigDecimal> reduceW;
        final Map<Integer, BigDecimal> reduceS;
        final Map<Integer, BigDecimal> wasteW;
        final Map<Integer, BigDecimal> wasteS;
        final Map<Integer, BigDecimal> lossW;
        final Map<Integer, BigDecimal> lossS;
        final Map<Integer, BigDecimal> salesQtyByFood;
        /** 本区间各菜总消费份数（type 1～5，含菜品型员工餐/赠送），用于出库分摊与核销。 */
        final Map<Integer, BigDecimal> consumptionQtyByFood;
        final Map<Integer, BigDecimal> salesSubtotalByFood;
        final BigDecimal totalPortions;
        final BigDecimal totalSales;
        final Map<Integer, Map<Integer, BigDecimal>> theoryWtByFoodAndGoods;
        final Map<Integer, BigDecimal> sumTheoryByGoods;
        final Set<Integer> allFoodIds;
        final Map<Integer, BigDecimal> sumRecipeUnitByGoods;
        final Map<Integer, BigDecimal> sumSalesQtyByGoods;
        final Map<Integer, BigDecimal> sumNeedByGoods;
        final Map<Integer, GbDistributerGoodsEntity> disGoodsById;
        /** 本区间各菜末次消费日（yyyy-MM-dd，type 1～5），用于配料核销宽限期。 */
        final Map<Integer, String> lastConsumptionDateByFood;
        /** 配料 → 使用该料的菜品 id 集合（反向配方索引）。 */
        final Map<Integer, Set<Integer>> foodIdsByGoods;
        /** Σ(本报表内各菜实销份数×该菜分销商标价)，与 {@code businessInsightSummary} 综合毛利率分子分母同源（标价侧用 {@code gb_distributer_food.gb_df_food_price}）。 */
        final BigDecimal scopeListPriceRevenueTotal;
        /** 与 {@code scopeOutboundSubtotals.subtotalOutbound123} 一致：本区间 type1+2+3 出库金额合计。 */
        final BigDecimal scopeSubtotalOutbound123;
        /** 菜品 → 配方行列表（批量加载结果，供 dashboard/audit 复用，避免重复查询）。 */
        final Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId;
        /** 菜品 id → 菜品实体（批量加载结果，供 dashboard/audit 复用，避免重复查询）。 */
        final Map<Integer, GbDistributerFoodEntity> foodById;

        private IngredientAnalysisData(String startDate, String stopDate, Integer disId, String searchDepId,
                Integer depFatherId, List<Integer> scopeDepIds, Map<String, Object> reduceParams,
                Map<Integer, BigDecimal> reduceW, Map<Integer, BigDecimal> reduceS,
                Map<Integer, BigDecimal> wasteW, Map<Integer, BigDecimal> wasteS,
                Map<Integer, BigDecimal> lossW, Map<Integer, BigDecimal> lossS,
                Map<Integer, BigDecimal> salesQtyByFood, Map<Integer, BigDecimal> consumptionQtyByFood,
                Map<Integer, BigDecimal> salesSubtotalByFood,
                BigDecimal totalPortions, BigDecimal totalSales,
                Map<Integer, Map<Integer, BigDecimal>> theoryWtByFoodAndGoods, Map<Integer, BigDecimal> sumTheoryByGoods,
                Set<Integer> allFoodIds, Map<Integer, BigDecimal> sumRecipeUnitByGoods,
                Map<Integer, BigDecimal> sumSalesQtyByGoods, Map<Integer, BigDecimal> sumNeedByGoods,
                Map<Integer, GbDistributerGoodsEntity> disGoodsById,
                Map<Integer, String> lastConsumptionDateByFood, Map<Integer, Set<Integer>> foodIdsByGoods,
                BigDecimal scopeListPriceRevenueTotal, BigDecimal scopeSubtotalOutbound123,
                Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId,
                Map<Integer, GbDistributerFoodEntity> foodById) {
            this.startDate = startDate;
            this.stopDate = stopDate;
            this.disId = disId;
            this.searchDepId = searchDepId;
            this.depFatherId = depFatherId;
            this.scopeDepIds = scopeDepIds;
            this.reduceParams = reduceParams;
            this.reduceW = reduceW;
            this.reduceS = reduceS;
            this.wasteW = wasteW;
            this.wasteS = wasteS;
            this.lossW = lossW;
            this.lossS = lossS;
            this.salesQtyByFood = salesQtyByFood;
            this.consumptionQtyByFood = consumptionQtyByFood == null ? Collections.emptyMap() : consumptionQtyByFood;
            this.salesSubtotalByFood = salesSubtotalByFood;
            this.totalPortions = totalPortions;
            this.totalSales = totalSales;
            this.theoryWtByFoodAndGoods = theoryWtByFoodAndGoods;
            this.sumTheoryByGoods = sumTheoryByGoods;
            this.allFoodIds = allFoodIds;
            this.sumRecipeUnitByGoods = sumRecipeUnitByGoods;
            this.sumSalesQtyByGoods = sumSalesQtyByGoods;
            this.sumNeedByGoods = sumNeedByGoods;
            this.disGoodsById = disGoodsById;
            this.lastConsumptionDateByFood = lastConsumptionDateByFood == null
                    ? Collections.emptyMap() : lastConsumptionDateByFood;
            this.foodIdsByGoods = foodIdsByGoods == null ? Collections.emptyMap() : foodIdsByGoods;
            this.scopeListPriceRevenueTotal = scopeListPriceRevenueTotal == null ? BigDecimal.ZERO : scopeListPriceRevenueTotal;
            this.scopeSubtotalOutbound123 = scopeSubtotalOutbound123 == null ? BigDecimal.ZERO : scopeSubtotalOutbound123;
            this.recipeByFoodId = recipeByFoodId == null ? Collections.emptyMap() : recipeByFoodId;
            this.foodById = foodById == null ? Collections.emptyMap() : foodById;
        }
    }

    /** 单条「菜 + 商」在分摊下的一条用量/成本。 */
    private static final class PerDishAlloc {
        final int foodId;
        final int gId;
        final String foodName;
        final String goodsNameHint;
        final BigDecimal salesSubtotal;
        final BigDecimal salesQty;
        final BigDecimal dishU;
        final BigDecimal theoryRecipe;
        /** 出库 type1+2+3 摊到本菜本料的重量（与 ingredientRows.actualUsage 一致）。 */
        final BigDecimal actualW;
        /** 仅 type1 生产出库摊到本菜本料的重量（与 ingredientRows.actualProduceUsage、偏差率分子一致）。 */
        final BigDecimal produceAllocW;
        /** 销售子表 gb_dep_food_goods_sales 本菜本料用量合计。 */
        final BigDecimal salesUsageT;
        final BigDecimal thCost;
        final BigDecimal actCost;
        final BigDecimal p1;

        private PerDishAlloc(int foodId, int gId, String foodName, String goodsNameHint, BigDecimal salesSubtotal,
                BigDecimal salesQty, BigDecimal dishU, BigDecimal theoryRecipe, BigDecimal actualW, BigDecimal produceAllocW,
                BigDecimal salesUsageT, BigDecimal thCost, BigDecimal actCost, BigDecimal p1) {
            this.foodId = foodId;
            this.gId = gId;
            this.foodName = foodName;
            this.goodsNameHint = goodsNameHint;
            this.salesSubtotal = salesSubtotal;
            this.salesQty = salesQty;
            this.dishU = dishU;
            this.theoryRecipe = theoryRecipe;
            this.actualW = actualW;
            this.produceAllocW = produceAllocW;
            this.salesUsageT = salesUsageT;
            this.thCost = thCost;
            this.actCost = actCost;
            this.p1 = p1;
        }
    }

    private IngredientAnalysisData loadIngredientAnalysisData(String startDate, String stopDate, Integer disId,
            String searchDepId, Integer depFatherId, Set<Integer> unionFoodIdsIntoScope) {
        return loadIngredientAnalysisData(startDate, stopDate, disId, searchDepId, depFatherId, unionFoodIdsIntoScope, null);
    }

    private IngredientAnalysisData loadIngredientAnalysisData(String startDate, String stopDate, Integer disId,
            String searchDepId, Integer depFatherId, Set<Integer> unionFoodIdsIntoScope,
            Collection<Integer> scopeDepartmentIdsAllowFilter) {
        List<Integer> scopeDepIds = resolveScopeDepIds(disId, searchDepId, depFatherId, scopeDepartmentIdsAllowFilter);
        Map<String, Object> reduceParams = buildReduceParams(disId, searchDepId, depFatherId, startDate, stopDate);
        BigDecimal scopeSubtotalOutbound123 = computeScopeOutbound123Subtotal(reduceParams);
        BigDecimal scopeListPriceRevenueTotal = BigDecimal.ZERO;
        List<Map<String, Object>> reduceAgg1 =
                gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(reduceParams);
        List<Map<String, Object>> reduceAgg2 = gbDepartmentGoodsStockReduceService.queryReduceAggByDisGoodsByType(
                reduceParams, GbConstants.StockReduceType.WASTE);
        List<Map<String, Object>> reduceAgg3 = gbDepartmentGoodsStockReduceService.queryReduceAggByDisGoodsByType(
                reduceParams, GbConstants.StockReduceType.LOSS);

        Map<Integer, BigDecimal> reduceW = new HashMap<>();
        Map<Integer, BigDecimal> reduceS = new HashMap<>();
        fillReduceAgg(reduceAgg1, reduceW, reduceS);
        Map<Integer, BigDecimal> wasteW = new HashMap<>();
        Map<Integer, BigDecimal> wasteS = new HashMap<>();
        fillReduceAgg(reduceAgg2, wasteW, wasteS);
        Map<Integer, BigDecimal> lossW = new HashMap<>();
        Map<Integer, BigDecimal> lossS = new HashMap<>();
        fillReduceAgg(reduceAgg3, lossW, lossS);

        List<GbDepFoodSalesEntity> foodSales = loadFoodSales(startDate, stopDate, disId, scopeDepIds);
        Map<Integer, Integer> saleIdToFoodId = new HashMap<>();
        Map<Integer, BigDecimal> salesQtyByFood = new HashMap<>();
        Map<Integer, BigDecimal> consumptionQtyByFood = new HashMap<>();
        Map<Integer, BigDecimal> salesSubtotalByFood = new HashMap<>();
        Map<Integer, String> lastConsumptionDateByFood = new HashMap<>();
        BigDecimal totalPortions = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;
        for (GbDepFoodSalesEntity s : foodSales) {
            Integer sid = s.getGbDepFoodSalesId();
            Integer fid = s.getGbDfsFoodId();
            if (sid == null || fid == null) {
                continue;
            }
            saleIdToFoodId.put(sid, fid);
            // 实收金额：与 depGeFoodBusiness actualRevenueByFood 统一，type 1-5 全量 rowSubtotal
            salesSubtotalByFood.merge(fid, GbDepFoodSalesMetricsSupport.rowSubtotal(s), BigDecimal::add);
            if (GbDepFoodSalesMetricsSupport.countsAsOperationalSales(s)) {
                BigDecimal q = GbDepFoodSalesMetricsSupport.operationalSalesQty(s);
                BigDecimal st = GbDepFoodSalesMetricsSupport.operationalActualRevenue(s);
                salesQtyByFood.merge(fid, q, BigDecimal::add);
                totalPortions = totalPortions.add(q);
                totalSales = totalSales.add(st);
                scopeListPriceRevenueTotal = scopeListPriceRevenueTotal.add(
                        GbDepFoodSalesMetricsSupport.operationalListPriceRevenue(s));
            }
            if (GbDepFoodSalesMetricsSupport.countsAsIngredientConsumption(s)) {
                consumptionQtyByFood.merge(fid, GbDepFoodSalesMetricsSupport.totalConsumptionQty(s), BigDecimal::add);
                OutboundIngredientReconcileSupport.trackLastConsumptionDate(
                        lastConsumptionDateByFood, fid, s.getGbDfsFullDate());
            }
        }
        Set<Integer> saleIds = saleIdToFoodId.keySet();
        Map<Integer, Map<Integer, BigDecimal>> theoryWtByFoodAndGoods = new HashMap<>();
        if (!saleIds.isEmpty()) {
            List<Integer> idList = new ArrayList<>(saleIds);
            for (int i = 0; i < idList.size(); i += IN_BATCH) {
                int to = Math.min(i + IN_BATCH, idList.size());
                List<Integer> batch = idList.subList(i, to);
                LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<>();
                w.ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, startDate)
                        .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, stopDate)
                        .in(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, batch);
                applyDepFoodGoodsFilter(w, scopeDepIds);
                for (GbDepFoodGoodsSalesEntity r : gbDepFoodGoodsSalesService.list(w)) {
                    Integer fsId = r.getGbDfgsFoodSalesId();
                    Integer foodId = saleIdToFoodId.get(fsId);
                    Integer gId = r.getGbDfgsDisGoodsId();
                    if (foodId == null || gId == null) {
                        continue;
                    }
                    BigDecimal amt = GbDepartmentGoodsStockReduceSupport.coerceDecimal(r.getGbDfgsGoodsAmount());
                    theoryWtByFoodAndGoods
                            .computeIfAbsent(foodId, k -> new HashMap<>())
                            .merge(gId, amt, BigDecimal::add);
                }
            }
        }
        Map<Integer, BigDecimal> sumTheoryByGoods = new HashMap<>();
        for (Map<Integer, BigDecimal> byG : theoryWtByFoodAndGoods.values()) {
            for (Map.Entry<Integer, BigDecimal> e : byG.entrySet()) {
                sumTheoryByGoods.merge(e.getKey(), e.getValue(), BigDecimal::add);
            }
        }
        Set<Integer> allFoodIds = new HashSet<>();
        allFoodIds.addAll(salesQtyByFood.keySet());
        allFoodIds.addAll(consumptionQtyByFood.keySet());
        allFoodIds.addAll(theoryWtByFoodAndGoods.keySet());
        if (unionFoodIdsIntoScope != null) {
            allFoodIds.addAll(unionFoodIdsIntoScope);
        }
        Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId = loadRecipesByFoodIds(allFoodIds);
        Map<Integer, GbDistributerFoodEntity> foodById = loadFoodEntitiesByIds(allFoodIds);
        Map<Integer, Set<Integer>> foodIdsByGoods =
                OutboundIngredientReconcileSupport.buildFoodIdsByGoods(recipeByFoodId);
        RecipeGoodsAgg recipeAgg = buildRecipeGoodsAggregates(allFoodIds, consumptionQtyByFood, recipeByFoodId);
        Map<Integer, BigDecimal> sumNeedByGoods = buildSumNeedByGoods(allFoodIds, consumptionQtyByFood, recipeByFoodId);
        Map<Integer, GbDistributerGoodsEntity> disGoodsById = loadDisGoodsDetailByRecipeGoods(allFoodIds, recipeByFoodId);
        return new IngredientAnalysisData(startDate, stopDate, disId, searchDepId, depFatherId, scopeDepIds, reduceParams,
                reduceW, reduceS, wasteW, wasteS, lossW, lossS, salesQtyByFood, consumptionQtyByFood, salesSubtotalByFood,
                totalPortions, totalSales,
                theoryWtByFoodAndGoods, sumTheoryByGoods, allFoodIds, recipeAgg.sumUByGoods, recipeAgg.sumSalesByGoods,
                sumNeedByGoods, disGoodsById, lastConsumptionDateByFood, foodIdsByGoods,
                scopeListPriceRevenueTotal, scopeSubtotalOutbound123,
                recipeByFoodId, foodById);
    }

    private List<PerDishAlloc> collectPerDishAllocs(IngredientAnalysisData d) {
        List<PerDishAlloc> out = new ArrayList<>();
        for (Integer foodId : d.allFoodIds) {
            GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(foodId);
            String foodName = food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "";
            List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
            if (recipe == null) {
                recipe = Collections.emptyList();
            }
            BigDecimal q = nz(d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO));
            BigDecimal st = nz(d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO));
            Map<Integer, BigDecimal> theoryByGoods = d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap());
            LinkedHashMap<Integer, MergeRecipeU> mergedByGood = new LinkedHashMap<>();
            for (GbDistributerFoodGoodsEntity line : recipe) {
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                Integer gId = line.getGbDfgDisGoodsId();
                if (gId == null) {
                    continue;
                }
                BigDecimal uu = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
                if (uu.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                MergeRecipeU mu = mergedByGood.computeIfAbsent(gId, k -> new MergeRecipeU());
                mu.sumU = mu.sumU.add(uu);
                if (mu.goodsName.isEmpty()) {
                    String nm = line.getGbDfgGoodsName();
                    if (nm != null && !nm.trim().isEmpty()) {
                        mu.goodsName = nm.trim();
                    }
                }
            }
            for (Map.Entry<Integer, MergeRecipeU> en : mergedByGood.entrySet()) {
                int gId = en.getKey();
                MergeRecipeU mu = en.getValue();
                BigDecimal t = nz(theoryByGoods.get(gId));
                BigDecimal w1g = nz(d.reduceW.get(gId));
                BigDecimal s1g = nz(d.reduceS.get(gId));
                BigDecimal w2g = nz(d.wasteW.get(gId));
                BigDecimal s2g = nz(d.wasteS.get(gId));
                BigDecimal w3g = nz(d.lossW.get(gId));
                BigDecimal s3g = nz(d.lossS.get(gId));
                BigDecimal recipeUnitSum = nz(d.sumRecipeUnitByGoods.get(gId));
                BigDecimal salesSumForGood = nz(d.sumSalesQtyByGoods.get(gId));
                BigDecimal dishU = mu.sumU;
                BigDecimal sumT = nz(d.sumTheoryByGoods.get(gId));
                BigDecimal needThis = q.multiply(dishU);
                BigDecimal sumNeed = nz(d.sumNeedByGoods.get(gId));
                String tag = "outboundPerDishAlloc foodId=" + foodId + " disGoodsId=" + gId;
                BigDecimal alloc1 = allocateOutboundWeightForDishGood(w1g, t, sumT, q, salesSumForGood, dishU, recipeUnitSum, needThis,
                        sumNeed, tag);
                BigDecimal share = w1g.compareTo(BigDecimal.ZERO) > 0
                        ? alloc1.divide(w1g, 8, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                BigDecimal actual12 = alloc1.add(share.multiply(w2g)).add(share.multiply(w3g));
                BigDecimal theoryRecipe = q.multiply(dishU);
                BigDecimal p1 = w1g.compareTo(BigDecimal.ZERO) > 0 ? s1g.divide(w1g, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal p2 = w2g.compareTo(BigDecimal.ZERO) > 0 ? s2g.divide(w2g, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal p3 = w3g.compareTo(BigDecimal.ZERO) > 0 ? s3g.divide(w3g, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal thCost = p1.multiply(theoryRecipe);
                BigDecimal actC = p1.multiply(alloc1).add(p2.multiply(share.multiply(w2g))).add(p3.multiply(share.multiply(w3g)));
                out.add(new PerDishAlloc(foodId, gId, foodName, mu.goodsName, st, q, dishU, theoryRecipe, actual12, alloc1, t, thCost, actC, p1));
            }
        }
        return out;
    }

    @Override
    public Map<String, Object> buildOutboundIngredientAnalysisReport(String startDate, String stopDate, Integer disId,
            String searchDepId, Integer depFatherId, String sortBy, String sortOrder, String goodsNameSearch,
            String verificationStatus, Integer page, Integer pageSize) {
        if (startDate == null || stopDate == null || disId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId 不能为空");
        }
        String outboundSort = normalizeOutboundSortBy(sortBy);
        String orderMode = normalizeOutboundSortOrder(sortOrder);
        String verificationMode = OutboundIngredientReconcileSupport.normalizeVerificationStatus(verificationStatus);
        boolean sortAsc = "asc".equals(orderMode);
        IngredientAnalysisData initialData = loadIngredientAnalysisData(startDate, stopDate, disId, searchDepId, depFatherId, null);
        Set<Integer> unionFoodIdsForOutboundGoods = collectFoodIdsForOutboundGoods(disId,
                OutboundIngredientReconcileSupport.mergeOutbound123ByGoods(initialData.reduceS, initialData.wasteS, initialData.lossS).keySet());
        IngredientAnalysisData d = needsUnionFoodScopeReload(initialData.allFoodIds, unionFoodIdsForOutboundGoods)
                ? loadIngredientAnalysisData(startDate, stopDate, disId, searchDepId, depFatherId, unionFoodIdsForOutboundGoods)
                : initialData;
        List<PerDishAlloc> lines = collectPerDishAllocs(d);
        List<Map<String, Object>> employeeMealAggRows =
                gbDepartmentGoodsStockReduceService.queryEmployeeMealReduceAggByDisGoods(d.reduceParams);
        Map<Integer, BigDecimal> employeeMealByGoods =
                OutboundIngredientReconcileSupport.indexSubtotalByGoods(employeeMealAggRows);
        Map<Integer, BigDecimal> employeeMealWeightByGoods =
                OutboundIngredientReconcileSupport.indexWeightByGoods(employeeMealAggRows);
        Map<Integer, BigDecimal> outbound123ByGoods = OutboundIngredientReconcileSupport.mergeOutbound123ByGoods(
                d.reduceS, d.wasteS, d.lossS);
        Map<Integer, BigDecimal> outbound123WeightByGoods = OutboundIngredientReconcileSupport.mergeOutbound123WeightByGoods(
                d.reduceW, d.wasteW, d.lossW);
        Map<Integer, GbDistributerGoodsEntity> enrichGoodsProfiles = new HashMap<>();
        if (d.disGoodsById != null) {
            enrichGoodsProfiles.putAll(d.disGoodsById);
        }
        LinkedHashSet<Integer> enrichProfileMissing = new LinkedHashSet<>();
        for (Integer gId : outbound123WeightByGoods.keySet()) {
            if (!enrichGoodsProfiles.containsKey(gId)) {
                enrichProfileMissing.add(gId);
            }
        }
        for (Integer gId : employeeMealWeightByGoods.keySet()) {
            if (!enrichGoodsProfiles.containsKey(gId)) {
                enrichProfileMissing.add(gId);
            }
        }
        enrichGoodsProfiles.putAll(loadDisGoodsEntitiesByIds(enrichProfileMissing));
        Map<Integer, BigDecimal> catalogUnitPriceByGoods =
                OutboundIngredientReconcileSupport.buildCatalogUnitPriceByGoods(enrichGoodsProfiles);
        outbound123ByGoods = OutboundIngredientReconcileSupport.enrichOutbound123WhenSubtotalMissing(
                outbound123ByGoods, d.reduceW, d.wasteW, d.lossW, d.reduceS, d.wasteS, d.lossS, catalogUnitPriceByGoods);
        employeeMealByGoods = OutboundIngredientReconcileSupport.enrichAmountWhenWeightMissing(
                employeeMealByGoods, employeeMealWeightByGoods, catalogUnitPriceByGoods);
        Map<Integer, BigDecimal> outbound123AfterSalesGrace = OutboundIngredientReconcileSupport
                .computeOutbound123AfterSalesGraceByGoods(
                        gbDepartmentGoodsStockReduceService.queryProduceLossWasteReduceAggByDisGoodsAndDate(
                                d.reduceParams),
                        d.foodIdsByGoods,
                        d.lastConsumptionDateByFood);
        List<OutboundIngredientReconcileSupport.DishGoodCostLine> costLines = new ArrayList<>(lines.size());
        for (PerDishAlloc a : lines) {
            costLines.add(new OutboundIngredientReconcileSupport.DishGoodCostLine(
                    a.foodId, a.gId, a.salesQty, a.actCost, a.thCost));
        }
        OutboundIngredientReconcileSupport.ScopeBreakdown reconcileScope = OutboundIngredientReconcileSupport.compute(
                costLines, outbound123ByGoods, employeeMealByGoods, outbound123AfterSalesGrace);

        // 偏差率核销区分：type-1 生产出库重量按已核销/未核销金额比例拆分（员工餐模式不缩放）
        Map<Integer, BigDecimal> devianceScaleRatioByGoods = new LinkedHashMap<>();
        if (!"all".equals(verificationMode) && !"employeeMeal".equals(verificationMode)) {
            for (Map.Entry<Integer, OutboundIngredientReconcileSupport.GoodsBreakdown> en : reconcileScope.byGoods.entrySet()) {
                Integer gId = en.getKey();
                OutboundIngredientReconcileSupport.GoodsBreakdown bd = en.getValue();
                if (bd.outbound123.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal verifiedAmt = "verified".equals(verificationMode) ? bd.verified : bd.nonVerified;
                    devianceScaleRatioByGoods.put(gId, verifiedAmt.divide(bd.outbound123, 8, RoundingMode.HALF_UP));
                }
            }
        }

        BigDecimal totalOutboundWeight = toBdFromDouble(
                        gbDepartmentGoodsStockReduceService.queryReduceProduceWeightTotal(d.reduceParams))
                .add(toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceWasteWeightTotal(d.reduceParams)))
                .add(toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceLossWeightTotal(d.reduceParams)))
                .add(toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceEmployeeMealWeightTotal(d.reduceParams)));
        Map<Integer, List<PerDishAlloc>> byGood = groupPerDishAllocsByGoods(lines);
        Map<Integer, BigDecimal> deviancePctByGood = computeOutboundDeviancePercentByGoods(byGood);
        if (!"all".equals(verificationMode) && !"employeeMeal".equals(verificationMode)) {
            deviancePctByGood = scaleDevianceByScaleRatio(deviancePctByGood, devianceScaleRatioByGoods);
        }
        Map<Integer, BigDecimal> devianceAmountByGood = computeOutboundDevianceAmountByGoods(byGood);
        LinkedHashSet<Integer> listGoodsIds = OutboundIngredientReconcileSupport.mergeOutboundIngredientListGoodsIds(
                byGood.keySet(), outbound123ByGoods, employeeMealByGoods);
        List<Integer> gIdOrder = "all".equals(verificationMode)
                ? new ArrayList<>(listGoodsIds)
                : OutboundIngredientReconcileSupport.listGoodsIdsMatchingVerification(reconcileScope, verificationMode);
        if ("outbound".equals(outboundSort)) {
            Comparator<BigDecimal> cmp = sortAsc ? Comparator.naturalOrder() : Comparator.reverseOrder();
            if ("unverified".equals(verificationMode)) {
                gIdOrder.sort(Comparator.comparing(
                        (Integer gid) -> nz(OutboundIngredientReconcileSupport.goodsBreakdown(reconcileScope, gid).nonVerified),
                        cmp)
                        .thenComparing(Comparator.comparingInt(gid -> gid)));
            } else if ("verified".equals(verificationMode)) {
                gIdOrder.sort(Comparator.comparing(
                        (Integer gid) -> nz(OutboundIngredientReconcileSupport.goodsBreakdown(reconcileScope, gid).verified),
                        cmp)
                        .thenComparing(Comparator.comparingInt(gid -> gid)));
            } else if ("employeeMeal".equals(verificationMode)) {
                gIdOrder.sort(Comparator.comparing(
                        (Integer gid) -> nz(OutboundIngredientReconcileSupport.goodsBreakdown(reconcileScope, gid).employeeMeal),
                        cmp)
                        .thenComparing(Comparator.comparingInt(gid -> gid)));
            } else {
                gIdOrder.sort(Comparator.comparing(
                        (Integer gid) -> nz(d.reduceS.get(gid))
                                .add(nz(d.wasteS.get(gid)))
                                .add(nz(d.lossS.get(gid))),
                        cmp)
                        .thenComparing(Comparator.comparingInt(gid -> gid)));
            }
        } else if ("deviance".equals(outboundSort)) {
            gIdOrder.sort(buildOutboundDevianceGoodsComparator(deviancePctByGood, sortAsc));
        } else if ("devianceAmount".equals(outboundSort)) {
            Comparator<BigDecimal> cmp = sortAsc ? Comparator.naturalOrder() : Comparator.reverseOrder();
            gIdOrder.sort(Comparator.comparing(
                    (Integer gid) -> nz(devianceAmountByGood.get(gid)),
                    cmp)
                    .thenComparing(Comparator.comparingInt(gid -> gid)));
        }

        String searchNorm = normalizeOutboundGoodsNameSearch(goodsNameSearch);
        Map<Integer, GbDistributerGoodsEntity> listGoodsProfiles = new HashMap<>();
        if (d.disGoodsById != null) {
            listGoodsProfiles.putAll(d.disGoodsById);
        }
        Set<Integer> profileMissing = new LinkedHashSet<>();
        Set<Integer> profileScope = new LinkedHashSet<>(listGoodsIds);
        profileScope.addAll(gIdOrder);
        for (Integer gId : profileScope) {
            if (!listGoodsProfiles.containsKey(gId)) {
                profileMissing.add(gId);
            }
        }
        listGoodsProfiles.putAll(loadDisGoodsEntitiesByIds(profileMissing));
        List<Integer> filteredGIds = new ArrayList<>();
        for (Integer gId : gIdOrder) {
            if (!outboundIngredientGoodsMatchesNameSearch(gId, listGoodsProfiles, byGood.get(gId), searchNorm)) {
                continue;
            }
            if (!"all".equals(verificationMode)) {
                OutboundIngredientReconcileSupport.GoodsBreakdown bd =
                        OutboundIngredientReconcileSupport.goodsBreakdown(reconcileScope, gId);
                if (!OutboundIngredientReconcileSupport.matchesVerificationStatus(bd, verificationMode)) {
                    continue;
                }
            }
            filteredGIds.add(gId);
        }
        int totalFiltered = filteredGIds.size();
        int resolvedPage = 1;
        Integer resolvedPageSize = null;
        List<Integer> pageGIds = filteredGIds;
        if (pageSize != null && pageSize > 0) {
            resolvedPageSize = Math.min(pageSize, OUTBOUND_INGREDIENT_MAX_PAGE_SIZE);
            resolvedPage = page == null || page < 1 ? 1 : page;
            int from = (resolvedPage - 1) * resolvedPageSize;
            if (from >= totalFiltered) {
                pageGIds = Collections.emptyList();
            } else {
                int to = Math.min(from + resolvedPageSize, totalFiltered);
                pageGIds = new ArrayList<>(filteredGIds.subList(from, to));
            }
        }

        LocalDate curStart = parseIsoLocalDate(d.startDate);
        LocalDate curEnd = parseIsoLocalDate(d.stopDate);
        IngredientAnalysisData dPrev = loadIngredientAnalysisData(curStart.minusMonths(1).toString(),
                curEnd.minusMonths(1).toString(), disId, searchDepId, depFatherId, null);
        Map<Integer, BigDecimal> deviancePctPrev = computeOutboundDeviancePercentByGoods(groupPerDishAllocsByGoods(collectPerDishAllocs(dPrev)));
        int abnormalNow = countOutboundAbnormalIngredientsHighOrCritical(deviancePctByGood);
        int abnormalPrev = countOutboundAbnormalIngredientsHighOrCritical(deviancePctPrev);

        BigDecimal allTheoryW = BigDecimal.ZERO;
        BigDecimal allProduceAllocForUtil = BigDecimal.ZERO;
        for (Map.Entry<Integer, List<PerDishAlloc>> en : byGood.entrySet()) {
            Integer gId = en.getKey();
            BigDecimal ratio = "all".equals(verificationMode) ? BigDecimal.ONE
                    : devianceScaleRatioByGoods.getOrDefault(gId, BigDecimal.ONE);
            for (PerDishAlloc a : en.getValue()) {
                allTheoryW = allTheoryW.add(a.theoryRecipe);
                allProduceAllocForUtil = allProduceAllocForUtil.add(a.produceAllocW.multiply(ratio));
            }
        }
        BigDecimal avgDeviance = BigDecimal.ZERO;
        if (allTheoryW.compareTo(BigDecimal.ZERO) > 0) {
            avgDeviance = allProduceAllocForUtil
                    .divide(allTheoryW, 8, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        List<Map<String, Object>> ingredientsAnalysis = new ArrayList<>();
        for (Integer gId : pageGIds) {
            List<PerDishAlloc> ls = byGood.getOrDefault(gId, Collections.emptyList());
            BigDecimal sumThW = BigDecimal.ZERO;
            BigDecimal sumActW = BigDecimal.ZERO;
            BigDecimal sumProduceAllocForUtil = BigDecimal.ZERO;
            BigDecimal sumSalesUsageT = BigDecimal.ZERO;
            BigDecimal sumThC = BigDecimal.ZERO;
            BigDecimal sumActC = BigDecimal.ZERO;
            for (PerDishAlloc a : ls) {
                sumThW = sumThW.add(a.theoryRecipe);
                sumActW = sumActW.add(a.actualW);
                sumProduceAllocForUtil = sumProduceAllocForUtil.add(a.produceAllocW);
                sumSalesUsageT = sumSalesUsageT.add(a.salesUsageT);
                sumThC = sumThC.add(a.thCost);
                sumActC = sumActC.add(a.actCost);
            }
            BigDecimal p1 = !ls.isEmpty() ? ls.get(0).p1 : BigDecimal.ZERO;
            if (ls.isEmpty()) {
                BigDecimal w1g = nz(d.reduceW.get(gId));
                BigDecimal w2g = nz(d.wasteW.get(gId));
                BigDecimal w3g = nz(d.lossW.get(gId));
                BigDecimal s1g = nz(d.reduceS.get(gId));
                BigDecimal s2g = nz(d.wasteS.get(gId));
                BigDecimal s3g = nz(d.lossS.get(gId));
                sumActW = w1g.add(w2g).add(w3g);
                sumActC = s1g.add(s2g).add(s3g);
                if (w1g.compareTo(BigDecimal.ZERO) > 0) {
                    p1 = s1g.divide(w1g, 8, RoundingMode.HALF_UP);
                }
            }
            if (p1.compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal catalogP = catalogUnitPriceByGoods.get(gId);
                if (catalogP != null && catalogP.compareTo(BigDecimal.ZERO) > 0) {
                    p1 = catalogP;
                }
            }
            Map<String, Object> pRow = new LinkedHashMap<>();
            pRow.put("disGoodsId", gId);
            GbDistributerGoodsEntity goodsProfile = listGoodsProfiles.get(gId);
            putDisGoodsProfileFields(pRow, goodsProfile);
            if (pRow.get("gbDgGoodsName") == null) {
                pRow.put("gbDgGoodsName", !ls.isEmpty() ? ls.get(0).goodsNameHint : null);
            }
            pRow.put("unitPrice", ingredientTwoDecimals(p1));
            pRow.put("theoryUsage", ingredientTwoDecimals(sumThW));
            pRow.put("salesUsageFromOrders", ingredientTwoDecimals(sumSalesUsageT));
            pRow.put("actualUsage", ingredientTwoDecimals(sumActW));
            pRow.put("diffUsage", ingredientTwoDecimals(sumActW.subtract(sumThW)));
            pRow.put("diffCostAmount", ingredientTwoDecimals(sumActC.subtract(sumThC)));
            OutboundIngredientReconcileSupport.GoodsBreakdown goodsBd =
                    OutboundIngredientReconcileSupport.goodsBreakdown(reconcileScope, gId);
            pRow.putAll(OutboundIngredientReconcileSupport.rowReconcileFields(goodsBd,
                    outbound123WeightByGoods.get(gId), employeeMealWeightByGoods.get(gId)));
            if (sumThW.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal scaleRatio = "all".equals(verificationMode) ? BigDecimal.ONE
                        : devianceScaleRatioByGoods.getOrDefault(gId, BigDecimal.ONE);
                BigDecimal scaledAlloc = sumProduceAllocForUtil.multiply(scaleRatio);
                BigDecimal devG = scaledAlloc.divide(sumThW, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                pRow.put("devianceRate", ingredientTwoDecimals(devG));
                GbConstants.IngredientDevianceLevel.LevelAndLabel u =
                        GbConstants.IngredientDevianceLevel.fromRatePercent(devG);
                Map<String, Object> um = new LinkedHashMap<>();
                um.put("level", u.getLevel());
                um.put("labelZh", u.getLabelZh());
                pRow.put("deviance", um);
            } else {
                pRow.put("devianceRate", null);
                pRow.put("deviance", null);
            }
            List<PerDishAlloc> sortedD = new ArrayList<>(ls);
            sortedD.sort(Comparator.comparing((PerDishAlloc x) -> x.salesSubtotal, Comparator.nullsFirst(Comparator.reverseOrder())));
            List<Map<String, Object>> supported = new ArrayList<>();
            for (PerDishAlloc a : sortedD) {
                Map<String, Object> sRow = new LinkedHashMap<>();
                sRow.put("dishId", a.foodId);
                sRow.put("dishName", a.foodName);
                sRow.put("recipeUnitPerDish", ingredientTwoDecimals(a.dishU));
                sRow.put("salesPortions", ingredientSalesCountString(a.salesQty));
                sRow.put("theoryUsage", ingredientTwoDecimals(a.theoryRecipe));
                sRow.put("salesUsageFromOrders", ingredientTwoDecimals(a.salesUsageT));
                sRow.put("actualUsage", ingredientTwoDecimals(a.actualW));
                sRow.put("diffUsage", ingredientTwoDecimals(a.actualW.subtract(a.theoryRecipe)));
                supported.add(sRow);
            }
            pRow.put("supportedDishes", supported);
            ingredientsAnalysis.add(pRow);
        }
        Map<String, Object> summ = OutboundIngredientReconcileSupport.buildSummaryCard(reconcileScope, d.totalSales);
        summ.put("totalOutboundWeight", ingredientTwoDecimals(totalOutboundWeight));
        summ.put("averageDevianceRate", allTheoryW.compareTo(BigDecimal.ZERO) > 0
                ? ingredientTwoDecimals(avgDeviance)
                : ingredientTwoDecimals(BigDecimal.ZERO));
        summ.put("abnormalIngredientCount", abnormalNow);
        summ.put("abnormalIngredientMomDelta", abnormalNow - abnormalPrev);
        summ.put("priorMonthAbnormalIngredientCount", abnormalPrev);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", d.startDate);
        out.put("stopDate", d.stopDate);
        out.put("disId", d.disId);
        out.put("depFatherId", d.depFatherId);
        out.put("sortBy", outboundSort);
        out.put("sortOrder", orderMode);
        out.put("verificationStatus", verificationMode);
        if (searchNorm != null) {
            out.put("goodsNameSearch", searchNorm);
        }
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("page", resolvedPage);
        pagination.put("pageSize", resolvedPageSize);
        pagination.put("total", totalFiltered);
        int totalPages = resolvedPageSize == null || resolvedPageSize <= 0
                ? 1
                : totalFiltered <= 0 ? 0 : (totalFiltered + resolvedPageSize - 1) / resolvedPageSize;
        pagination.put("totalPages", totalPages);
        out.put("pagination", pagination);
        out.put("summary", summ);
        out.put("devianceDistribution", buildOutboundDevianceDistribution(deviancePctByGood));
        out.put("ingredientsAnalysis", ingredientsAnalysis);
        out.put("disclaimerZh", OUTBOUND_INGREDIENT_DISCLAIMER_ZH);
        return out;
    }

    @Override
    public Map<String, Object> summarizeDisGoodsDayForReduceCurve(String day, Integer disId, Integer disGoodsId,
            String searchDepId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> dishBreakdown = new ArrayList<>();
        if (day == null || day.trim().isEmpty() || disId == null || disGoodsId == null) {
            out.put("theoryOutboundQty", ingredientTwoDecimals(BigDecimal.ZERO));
            out.put("grossProfitContributionTotal", ingredientTwoDecimals(BigDecimal.ZERO));
            out.put("dishIngredientDayBreakdown", dishBreakdown);
            return out;
        }
        String d = day.trim();
        IngredientAnalysisData data = loadIngredientAnalysisData(d, d, disId, searchDepId, null, null);
        List<GbDistributerFoodGoodsEntity> recipeLines =
                gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, disId);
        if (recipeLines == null || recipeLines.isEmpty()) {
            recipeLines = gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, null);
        }
        Set<Integer> candidateFoodIds = new HashSet<>();
        if (recipeLines != null) {
            for (GbDistributerFoodGoodsEntity line : recipeLines) {
                if (line.getGbDfgFoodId() == null) {
                    continue;
                }
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                candidateFoodIds.add(line.getGbDfgFoodId());
            }
        }
        BigDecimal theoryOutboundQty = BigDecimal.ZERO;
        BigDecimal grossProfitContributionTotal = BigDecimal.ZERO;
        for (Integer foodId : candidateFoodIds) {
            BigDecimal q = nz(data.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO));
            if (q.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> dishRow = buildIngredientAnalysisDishRow(foodId,
                    data.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                    data.sumTheoryByGoods, data.sumRecipeUnitByGoods, data.sumSalesQtyByGoods, data.sumNeedByGoods,
                    data.disGoodsById,
                    data.reduceW, data.reduceS, data.wasteW, data.wasteS, data.lossW, data.lossS,
                    data.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    data.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    data.scopeListPriceRevenueTotal, data.scopeSubtotalOutbound123);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ingredientRows = (List<Map<String, Object>>) dishRow.get("ingredientRows");
            if (ingredientRows == null || ingredientRows.isEmpty()) {
                continue;
            }
            Map<String, Object> targetIr = null;
            for (Map<String, Object> ir : ingredientRows) {
                if (Objects.equals(disGoodsId, toInt(ir.get("disGoodsId")))) {
                    targetIr = ir;
                    break;
                }
            }
            if (targetIr == null) {
                continue;
            }
            GbDistributerFoodEntity foodEntity = gbDistributerFoodService.queryObject(foodId);
            BigDecimal listUnit = GbDepartmentGoodsStockReduceSupport.coerceDecimal(
                    foodEntity == null ? null : foodEntity.getGbDfFoodPrice());
            BigDecimal listPriceRevenueDish = q.multiply(listUnit).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalTheoryDish = BigDecimal.ZERO;
            for (Map<String, Object> ir : ingredientRows) {
                BigDecimal thPpRow = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("theoryCostPerPortion"));
                totalTheoryDish = totalTheoryDish.add(thPpRow.multiply(q));
            }
            BigDecimal thPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(targetIr.get("theoryCostPerPortion"));
            BigDecimal theoryUsage = GbDepartmentGoodsStockReduceSupport.coerceDecimal(targetIr.get("theoryUsage"));
            theoryOutboundQty = theoryOutboundQty.add(theoryUsage);
            // 与 dep 当日 dayOutbound123Subtotal 对齐：扣 type1+2+3 分摊（actualCostPerPortion×q），勿用仅 type1 的 produceCostPerPortion。
            BigDecimal actPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(targetIr.get("actualCostPerPortion"));
            BigDecimal actTotal = actPp.multiply(q);
            BigDecimal listAlloc;
            if (totalTheoryDish.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal thCostRow = thPp.multiply(q);
                listAlloc = listPriceRevenueDish.multiply(thCostRow).divide(totalTheoryDish, 8, RoundingMode.HALF_UP);
                grossProfitContributionTotal = grossProfitContributionTotal.add(listAlloc.subtract(actTotal));
            } else {
                // 全菜理论成本合计为 0（常见：原料未维护单价）时无法用成本占比分摊标价收入，但仍输出销售份数 / 配方用量等行，
                // 否则 dayTheoryOutboundQty 有值而 dayDishIngredientSales 为空。
                listAlloc = BigDecimal.ZERO;
            }
            Map<String, Object> br = new LinkedHashMap<>();
            br.put("dishId", foodId);
            br.put("dishName", foodEntity != null && foodEntity.getGbDfFoodName() != null
                    ? foodEntity.getGbDfFoodName().trim() : "");
            br.put("salesPortions", dishRow.get("salesPortions"));
            br.put("salesAmount", dishRow.get("salesAmount"));
            br.put("salesUnitPrice", dishRow.get("salesUnitPrice"));
            br.put("recipeUnitPerDish", targetIr.get("recipeUnitPerDish"));
            br.put("theoryIngredientQty", targetIr.get("theoryUsage"));
            br.put("listRevenueAllocatedToIngredient", ingredientTwoDecimals(listAlloc));
            br.put("outbound123CostAllocatedToIngredient", ingredientTwoDecimals(actTotal));
            br.put("grossProfitContribution", ingredientTwoDecimals(listAlloc.subtract(actTotal)));
            br.put("_sortSales", q);
            dishBreakdown.add(br);
        }
        dishBreakdown.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("_sortSales"))
                .reversed());
        for (Map<String, Object> m : dishBreakdown) {
            m.remove("_sortSales");
        }
        out.put("theoryOutboundQty", ingredientTwoDecimals(theoryOutboundQty));
        out.put("grossProfitContributionTotal", ingredientTwoDecimals(grossProfitContributionTotal));
        out.put("dishIngredientDayBreakdown", dishBreakdown);
        return out;
    }

    /**
     * 与接口 {@code sortBy} 约定一致，响应 {@code data.sortBy} 为 {@code sales|diff|diffrate|actualcost|ingredientcount}；
     * 升降序见 {@link #normalizeIngredientSortOrder}、{@code data.sortOrder}。
     */
    static String normalizeIngredientSortBy(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return "sales";
        }
        String s = sortBy.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        if ("sales".equals(s) || "salesamount".equals(s) || "销量".equals(s)) {
            return "sales";
        }
        if ("diff".equals(s) || "diffcost".equals(s) || "diffcostperportion".equals(s) || "成本差异".equals(s)) {
            return "diff";
        }
        if ("diffrate".equals(s) || "diffrateperportion".equals(s) || "成本偏差率".equals(s)) {
            return "diffrate";
        }
        if ("actualcost".equals(s) || "actualcostperportion".equals(s) || "actualcostpp".equals(s)
                || "单份实际成本".equals(s) || "实际成本".equals(s)) {
            return "actualcost";
        }
        if ("ingredientcount".equals(s) || "配料数量".equals(s)) {
            return "ingredientcount";
        }
        throw new IllegalArgumentException(
                "sortBy 仅支持 sales(销售额)、diff(每份成本差异绝对值)、diffRate(每份成本偏差率)、"
                        + "actualCost(单份实际成本 type1+2+3)、ingredientCount(配料数量)，当前: " + sortBy);
    }

    private static String normalizeIngredientSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.trim().isEmpty()) {
            return "desc";
        }
        String s = sortOrder.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if ("asc".equals(s) || "ascending".equals(s) || "升序".equals(s)) {
            return "asc";
        }
        if ("desc".equals(s) || "descending".equals(s) || "降序".equals(s)) {
            return "desc";
        }
        throw new IllegalArgumentException("sortOrder 仅支持 asc(升序) 或 desc(降序)，当前: " + sortOrder);
    }

    /**
     * 仅 {@code /outboundIngredientAnalysis}：响应 {@code data.sortBy} 为 {@code outbound|deviance|devianceAmount} 之一；{@code data.sortOrder} 为 {@code asc|desc}。
     * <p>1：全店分商品 1+2+3 出库**金额**；2：同料在报表内**偏差率** (Σ仅 type1 生产分摊重量 / Σ本菜配方理论×100)；3：分商品**偏差金额**（实际成本−理论成本，元）。</p>
     */
    private static String normalizeOutboundSortBy(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return "outbound";
        }
        String s = sortBy.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        s = s.replace("_", "");
        if ("outbound".equals(s) || "outboundamount".equals(s) || "subtotal".equals(s) || "totaloutbound".equals(s)
                || "出库".equals(s) || "出库金额".equals(s)) {
            return "outbound";
        }
        if ("deviance".equals(s) || "deviancerate".equals(s) || "偏差率".equals(s) || "rate".equals(s)) {
            return "deviance";
        }
        if ("devianceamount".equals(s) || "deviancecost".equals(s) || "偏差金额".equals(s) || "偏差成本".equals(s)) {
            return "devianceAmount";
        }
        throw new IllegalArgumentException(
                "outbound 排序 sortBy 仅支持 outbound(出库金额)、deviance(偏差率)、devianceAmount(偏差金额)，当前: " + sortBy);
    }

    /** {@code /outboundIngredientAnalysis} 升降序；默认 desc，与 {@link #normalizeOutboundSortBy} 独立。 */
    private static String normalizeOutboundSortOrder(String sortOrder) {
        if (sortOrder == null || sortOrder.trim().isEmpty()) {
            return "desc";
        }
        String s = sortOrder.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if ("asc".equals(s) || "ascending".equals(s) || "升序".equals(s)) {
            return "asc";
        }
        if ("desc".equals(s) || "descending".equals(s) || "降序".equals(s)) {
            return "desc";
        }
        throw new IllegalArgumentException("sortOrder 仅支持 asc(升序) 或 desc(降序)，当前: " + sortOrder);
    }

    private static String normalizeOutboundGoodsNameSearch(String goodsNameSearch) {
        if (goodsNameSearch == null) {
            return null;
        }
        String t = goodsNameSearch.trim();
        return t.isEmpty() ? null : t;
    }

    /** 主档商品名、规格名；无主档时用配方行名称提示。大小写不敏感，子串匹配。 */
    private static boolean outboundIngredientGoodsMatchesNameSearch(Integer gId,
            Map<Integer, GbDistributerGoodsEntity> goodsProfiles, List<PerDishAlloc> ls, String searchTrimmed) {
        if (searchTrimmed == null) {
            return true;
        }
        String needle = searchTrimmed.toLowerCase(Locale.ROOT);
        GbDistributerGoodsEntity ge = goodsProfiles == null ? null : goodsProfiles.get(gId);
        if (ge != null) {
            if (outboundIngredientHaystackContains(needle, ge.getGbDgGoodsName())) {
                return true;
            }
            if (outboundIngredientHaystackContains(needle, ge.getGbDgGoodsStandardname())) {
                return true;
            }
        }
        if (ls != null) {
            for (PerDishAlloc a : ls) {
                if (outboundIngredientHaystackContains(needle, a.goodsNameHint)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean outboundIngredientHaystackContains(String needleLower, String haystack) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private static Map<Integer, List<PerDishAlloc>> groupPerDishAllocsByGoods(List<PerDishAlloc> lines) {
        Map<Integer, List<PerDishAlloc>> byGood = new LinkedHashMap<>();
        if (lines == null) {
            return byGood;
        }
        for (PerDishAlloc a : lines) {
            byGood.computeIfAbsent(a.gId, k -> new ArrayList<>()).add(a);
        }
        return byGood;
    }

    /** 按商行聚合：Σ type1 生产分摊÷Σ 配方理论×100；无理论量的商品不出现于返回 Map。 */
    private static Map<Integer, BigDecimal> computeOutboundDeviancePercentByGoods(Map<Integer, List<PerDishAlloc>> byGood) {
        Map<Integer, BigDecimal> deviancePctByGood = new LinkedHashMap<>();
        if (byGood == null) {
            return deviancePctByGood;
        }
        for (Map.Entry<Integer, List<PerDishAlloc>> en : byGood.entrySet()) {
            BigDecimal sumThW = BigDecimal.ZERO;
            BigDecimal sumProduceAllocForDeviance = BigDecimal.ZERO;
            for (PerDishAlloc a : en.getValue()) {
                sumThW = sumThW.add(a.theoryRecipe);
                sumProduceAllocForDeviance = sumProduceAllocForDeviance.add(a.produceAllocW);
            }
            if (sumThW.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal devG = sumProduceAllocForDeviance.divide(sumThW, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                deviancePctByGood.put(en.getKey(), devG);
            }
        }
        return deviancePctByGood;
    }

    /** 按商行聚合：Σ (实际成本 − 理论成本)，单位元，两位小数。 */
    private static Map<Integer, BigDecimal> computeOutboundDevianceAmountByGoods(Map<Integer, List<PerDishAlloc>> byGood) {
        Map<Integer, BigDecimal> devianceAmountByGood = new LinkedHashMap<>();
        if (byGood == null) {
            return devianceAmountByGood;
        }
        for (Map.Entry<Integer, List<PerDishAlloc>> en : byGood.entrySet()) {
            BigDecimal sumActCost = BigDecimal.ZERO;
            BigDecimal sumThCost = BigDecimal.ZERO;
            for (PerDishAlloc a : en.getValue()) {
                sumActCost = sumActCost.add(a.actCost);
                sumThCost = sumThCost.add(a.thCost);
            }
            devianceAmountByGood.put(en.getKey(),
                    sumActCost.subtract(sumThCost).setScale(2, RoundingMode.HALF_UP));
        }
        return devianceAmountByGood;
    }

    /** 按核销/非核销金额比例缩放偏差率。{@code scaleRatioByGoods} 中缺少的 goods 保持原值 (×1.0)。 */
    private static Map<Integer, BigDecimal> scaleDevianceByScaleRatio(Map<Integer, BigDecimal> deviancePctByGood,
            Map<Integer, BigDecimal> scaleRatioByGoods) {
        if (scaleRatioByGoods == null || scaleRatioByGoods.isEmpty() || deviancePctByGood == null) {
            return deviancePctByGood;
        }
        Map<Integer, BigDecimal> out = new LinkedHashMap<>(deviancePctByGood);
        for (Map.Entry<Integer, BigDecimal> en : out.entrySet()) {
            BigDecimal ratio = scaleRatioByGoods.getOrDefault(en.getKey(), BigDecimal.ONE);
            if (ratio.compareTo(BigDecimal.ONE) != 0) {
                out.put(en.getKey(), en.getValue().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return out;
    }

    private static Comparator<Integer> buildOutboundDevianceGoodsComparator(Map<Integer, BigDecimal> deviancePctByGood,
            boolean ascending) {
        return (g1, g2) -> {
            BigDecimal u1 = deviancePctByGood.get(g1);
            BigDecimal u2 = deviancePctByGood.get(g2);
            boolean h1 = u1 != null;
            boolean h2 = u2 != null;
            if (!h1 && !h2) {
                return Integer.compare(g1, g2);
            }
            if (!h1) {
                return 1;
            }
            if (!h2) {
                return -1;
            }
            int c = ascending ? u1.compareTo(u2) : u2.compareTo(u1);
            if (c != 0) {
                return c;
            }
            return Integer.compare(g1, g2);
        };
    }

    /**
     * 「异常配料」计数：偏差率档位为偏高({@link GbConstants.IngredientDevianceLevel#CODE_HIGH}) 或
     * 浪费严重({@link GbConstants.IngredientDevianceLevel#CODE_CRITICAL})，与 {@link GbConstants.IngredientDevianceLevel} 区间一致。
     */
    private static int countOutboundAbnormalIngredientsHighOrCritical(Map<Integer, BigDecimal> deviancePctByGood) {
        int n = 0;
        if (deviancePctByGood == null) {
            return 0;
        }
        for (BigDecimal rate : deviancePctByGood.values()) {
            GbConstants.IngredientDevianceLevel.LevelAndLabel u =
                    GbConstants.IngredientDevianceLevel.fromRatePercent(rate);
            if (u == null) {
                continue;
            }
            String lvl = u.getLevel();
            if (GbConstants.IngredientDevianceLevel.CODE_HIGH.equals(lvl)
                    || GbConstants.IngredientDevianceLevel.CODE_CRITICAL.equals(lvl)) {
                n++;
            }
        }
        return n;
    }

    /** 环图数据：四档计数及占「有偏差率配料种类」的比例；档位区间见 {@link GbConstants.IngredientDevianceLevel}。 */
    private static Map<String, Object> buildOutboundDevianceDistribution(Map<Integer, BigDecimal> deviancePctByGood) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(GbConstants.IngredientDevianceLevel.CODE_CRITICAL, 0);
        counts.put(GbConstants.IngredientDevianceLevel.CODE_HIGH, 0);
        counts.put(GbConstants.IngredientDevianceLevel.CODE_NORMAL, 0);
        counts.put(GbConstants.IngredientDevianceLevel.CODE_LOW, 0);
        int total = deviancePctByGood != null ? deviancePctByGood.size() : 0;
        if (deviancePctByGood != null) {
            for (BigDecimal rate : deviancePctByGood.values()) {
                GbConstants.IngredientDevianceLevel.LevelAndLabel u =
                        GbConstants.IngredientDevianceLevel.fromRatePercent(rate);
                if (u == null) {
                    continue;
                }
                counts.computeIfPresent(u.getLevel(), (k, v) -> v + 1);
            }
        }
        List<Map<String, Object>> buckets = new ArrayList<>();
        appendOutboundDevianceBucket(buckets, counts, total, GbConstants.IngredientDevianceLevel.CODE_CRITICAL,
                GbConstants.IngredientDevianceLevel.LABEL_ZH_CRITICAL, ">120%");
        appendOutboundDevianceBucket(buckets, counts, total, GbConstants.IngredientDevianceLevel.CODE_HIGH,
                GbConstants.IngredientDevianceLevel.LABEL_ZH_HIGH, ">110%～120%");
        appendOutboundDevianceBucket(buckets, counts, total, GbConstants.IngredientDevianceLevel.CODE_NORMAL,
                GbConstants.IngredientDevianceLevel.LABEL_ZH_NORMAL, "90%～110%");
        appendOutboundDevianceBucket(buckets, counts, total, GbConstants.IngredientDevianceLevel.CODE_LOW,
                GbConstants.IngredientDevianceLevel.LABEL_ZH_LOW, "<90%");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalKindsWithDeviance", total);
        out.put("buckets", buckets);
        return out;
    }

    private static void appendOutboundDevianceBucket(List<Map<String, Object>> buckets, Map<String, Integer> counts,
            int total, String level, String labelZh, String rangeZh) {
        int cnt = counts.getOrDefault(level, 0);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("level", level);
        row.put("labelZh", labelZh);
        row.put("rangeZh", rangeZh);
        row.put("count", cnt);
        if (total > 0) {
            BigDecimal pct = new BigDecimal(cnt).multiply(new BigDecimal("100"))
                    .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP);
            row.put("percentOfKinds", ingredientTwoDecimals(pct));
        } else {
            row.put("percentOfKinds", ingredientTwoDecimals(BigDecimal.ZERO));
        }
        buckets.add(row);
    }

    @Override
    public Map<String, Object> buildIngredientAnalysisReport(String startDate, String stopDate, Integer disId,
            String searchDepId, Integer depFatherId, String sortBy, String sortOrder) {
        if (startDate == null || stopDate == null || disId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId 不能为空");
        }
        String sortMode = normalizeIngredientSortBy(sortBy);
        String orderMode = normalizeIngredientSortOrder(sortOrder);
        boolean asc = "asc".equals(orderMode);
        IngredientAnalysisData d = loadIngredientAnalysisData(startDate, stopDate, disId, searchDepId, depFatherId, null);
        Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId = loadRecipesByFoodIds(d.allFoodIds);
        Map<Integer, GbDistributerFoodEntity> foodById = loadFoodEntitiesByIds(d.allFoodIds);
        List<Map<String, Object>> salesDishRows = new ArrayList<>();
        for (Integer foodId : d.allFoodIds) {
            salesDishRows.add(buildIngredientAnalysisDishRow(foodId,
                    d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                    d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods, d.disGoodsById,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS,
                    d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.scopeListPriceRevenueTotal, d.scopeSubtotalOutbound123,
                    foodById.get(foodId),
                    recipeByFoodId.getOrDefault(foodId, Collections.emptyList())));
        }
        sortIngredientAnalysisDishRows(salesDishRows, sortMode, asc);

        logWawaCabbageIngredientReportSummary(d, salesDishRows);

        Map<String, Object> scope = buildIngredientAnalysisScopeSalesSubtotals(salesDishRows);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", startDate);
        out.put("stopDate", stopDate);
        out.put("disId", disId);
        out.put("depFatherId", depFatherId);
        out.put("sortBy", sortMode);
        out.put("sortOrder", orderMode);
        out.put("scopeSalesSubtotals", scope);
        out.put("salesDishRows", salesDishRows);
        out.put("disclaimerZh", INGREDIENT_ANALYSIS_DISCLAIMER_ZH);
        return out;
    }

    @Override
    public List<Map<String, Object>> buildCategoryOverviewDishRows(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (startDate == null || stopDate == null || disId == null || depFatherId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId、depFatherId 不能为空");
        }
        IngredientAnalysisData d =
                loadIngredientAnalysisData(startDate, stopDate, disId, null, depFatherId, null, scopeDepartmentIdsAllowFilter);
        Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId = loadRecipesByFoodIds(d.allFoodIds);
        Map<Integer, GbDistributerFoodEntity> foodById = loadFoodEntitiesByIds(d.allFoodIds);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Integer foodId : d.allFoodIds) {
            if (foodId == null) {
                continue;
            }
            Map<String, Object> dishRow =
                    buildIngredientAnalysisDishRow(
                            foodId,
                            d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                            d.sumTheoryByGoods,
                            d.sumRecipeUnitByGoods,
                            d.sumSalesQtyByGoods,
                            d.sumNeedByGoods,
                            d.disGoodsById,
                            d.reduceW,
                            d.reduceS,
                            d.wasteW,
                            d.wasteS,
                            d.lossW,
                            d.lossS,
                            d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                            d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                            d.scopeListPriceRevenueTotal,
                            d.scopeSubtotalOutbound123,
                            foodById.get(foodId),
                            recipeByFoodId.getOrDefault(foodId, Collections.emptyList()));
            out.add(mapCategoryOverviewDishRow(foodId, dishRow, foodById.get(foodId)));
        }
        return out;
    }

    @Override
    public Map<String, Object> buildCategoryOverviewDishRowForFoodId(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Integer foodId, Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (startDate == null || stopDate == null || disId == null || depFatherId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId、depFatherId 不能为空");
        }
        if (foodId == null) {
            throw new IllegalArgumentException("foodId 不能为空");
        }
        Set<Integer> unionFoodIds = Set.of(foodId);
        IngredientAnalysisData d =
                loadIngredientAnalysisData(
                        startDate, stopDate, disId, null, depFatherId, unionFoodIds, scopeDepartmentIdsAllowFilter);
        Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId = loadRecipesByFoodIds(d.allFoodIds);
        Map<Integer, GbDistributerFoodEntity> foodById = loadFoodEntitiesByIds(d.allFoodIds);
        Map<String, Object> dishRow =
                buildIngredientAnalysisDishRow(
                        foodId,
                        d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                        d.sumTheoryByGoods,
                        d.sumRecipeUnitByGoods,
                        d.sumSalesQtyByGoods,
                        d.sumNeedByGoods,
                        d.disGoodsById,
                d.reduceW,
                d.reduceS,
                d.wasteW,
                d.wasteS,
                d.lossW,
                d.lossS,
                d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                d.scopeListPriceRevenueTotal,
                d.scopeSubtotalOutbound123,
                foodById.get(foodId),
                recipeByFoodId.getOrDefault(foodId, Collections.emptyList()));
        return mapCategoryOverviewDishRow(foodId, dishRow, foodById.get(foodId));
    }

    @Override
    public Map<String, Object> buildIngredientAnalysisDishRowForFoodId(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Integer foodId,
            Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (startDate == null || stopDate == null || disId == null || depFatherId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId、depFatherId 不能为空");
        }
        if (foodId == null) {
            throw new IllegalArgumentException("foodId 不能为空");
        }
        Set<Integer> unionFoodIds = Set.of(foodId);
        IngredientAnalysisData d =
                loadIngredientAnalysisData(
                        startDate, stopDate, disId, searchDepId, depFatherId, unionFoodIds, scopeDepartmentIdsAllowFilter);
        Map<Integer, GbDistributerFoodEntity> foodById = loadFoodEntitiesByIds(d.allFoodIds);
        List<GbDistributerFoodGoodsEntity> recipeForDish = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
        if (recipeForDish == null) {
            recipeForDish = Collections.emptyList();
        }
        return buildIngredientAnalysisDishRow(
                foodId,
                d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                d.sumTheoryByGoods,
                d.sumRecipeUnitByGoods,
                d.sumSalesQtyByGoods,
                d.sumNeedByGoods,
                d.disGoodsById,
                d.reduceW,
                d.reduceS,
                d.wasteW,
                d.wasteS,
                d.lossW,
                d.lossS,
                d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                d.scopeListPriceRevenueTotal,
                d.scopeSubtotalOutbound123,
                foodById.get(foodId),
                recipeForDish);
    }

    private static Map<String, Object> mapCategoryOverviewDishRow(Integer foodId, Map<String, Object> dishRow,
            GbDistributerFoodEntity food) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", foodId);
        row.put("dishId", foodId);
        row.put("foodName", dishRow.get("dishName"));
        row.put("soldPortionsTotal", dishRow.get("salesPortions"));
        row.put("actualRevenue", dishRow.get("salesAmount"));
        row.put("salesUnitPrice", dishRow.get("salesUnitPrice"));
        row.put("listPricePerPortion", food != null && food.getGbDfFoodPrice() != null && !food.getGbDfFoodPrice().isBlank()
                ? food.getGbDfFoodPrice() : "0");
        row.put("actualCostTotalAmount123", dishRow.get("actualCostAmount"));
        row.put("actualCostPerPortion", dishRow.get("actualCostPerPortion"));
        row.put("theoryCostPerPortion", dishRow.get("theoryCostPerPortion"));
        row.put("diffCostPerPortion", dishRow.get("diffCostPerPortion"));
        BigDecimal rev = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dishRow.get("salesAmount"));
        BigDecimal cost = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dishRow.get("actualCostAmount"));
        BigDecimal qty = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dishRow.get("salesPortions"));
        BigDecimal thPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dishRow.get("theoryCostPerPortion"));
        row.put("theoreticalCostTotalAmount", moneyPlainForCategoryRow(thPp.multiply(qty)));
        if (rev.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal margin = rev.subtract(cost).divide(rev, 8, RoundingMode.HALF_UP);
            row.put(
                    "blendedGrossMarginRateOnListPrice",
                    GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(margin));
            BigDecimal theoryMargin =
                    rev.subtract(thPp.multiply(qty)).divide(rev, 8, RoundingMode.HALF_UP);
            row.put(
                    "theoreticalGrossMarginRateOnListPrice",
                    GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(theoryMargin));
        } else {
            row.put("blendedGrossMarginRateOnListPrice", "0.00");
            row.put("theoreticalGrossMarginRateOnListPrice", "0.00");
        }
        return row;
    }

    private static String moneyPlainForCategoryRow(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    @Override
    public Map<Integer, List<Map<String, Object>>> buildIngredientRowsForFoodIds(String startDate, String stopDate,
            Integer disId, Integer depFatherId, Set<Integer> foodIds) {
        return buildIngredientRowsForFoodIds(startDate, stopDate, disId, depFatherId, null, foodIds);
    }

    @Override
    public Map<Integer, List<Map<String, Object>>> buildIngredientRowsForFoodIds(String startDate, String stopDate,
            Integer disId, Integer depFatherId, String searchDepId, Set<Integer> foodIds) {
        if (startDate == null || stopDate == null || disId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId 不能为空");
        }
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (foodIds != null) {
            for (Integer id : foodIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        IngredientAnalysisData d = loadIngredientAnalysisData(startDate, stopDate, disId, searchDepId, depFatherId, ids);
        Map<Integer, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Integer foodId : ids) {
            Map<String, Object> dishRow = buildIngredientAnalysisDishRow(foodId,
                    d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                    d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods, d.disGoodsById,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS,
                    d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.scopeListPriceRevenueTotal, d.scopeSubtotalOutbound123);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) dishRow.get("ingredientRows");
            out.put(foodId, rows != null ? rows : Collections.emptyList());
        }
        return out;
    }

    @Override
    public Map<Integer, BigDecimal> getDishActualCostPerPortion123ByFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Set<Integer> foodIds, Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate 不能为空");
        }
        if (disId == null || depFatherId == null) {
            throw new IllegalArgumentException("disId、depFatherId 不能为空");
        }
        if (foodIds == null || foodIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sd = startDate.trim();
        String st = stopDate.trim();
        IngredientAnalysisData d = loadIngredientAnalysisData(sd, st, disId, searchDepId, depFatherId, foodIds,
                scopeDepartmentIdsAllowFilter);
        Map<Integer, BigDecimal> out = new HashMap<>();
        for (Integer foodId : foodIds) {
            if (foodId == null) {
                continue;
            }
            Map<String, Object> dishRow = buildIngredientAnalysisDishRow(foodId,
                    d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                    d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods, d.disGoodsById,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS,
                    d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.scopeListPriceRevenueTotal, d.scopeSubtotalOutbound123);
            out.put(foodId, GbDepartmentGoodsStockReduceSupport.coerceDecimal(dishRow.get("actualCostPerPortion")));
        }
        return out;
    }

    @Override
    public Map<Integer, Map<String, String>> getDishPerPortionCosts123ByFoodIds(String startDate, String stopDate, Integer disId,
            Integer depFatherId, Set<Integer> foodIds) {
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate 不能为空");
        }
        if (disId == null || depFatherId == null) {
            throw new IllegalArgumentException("disId、depFatherId 不能为空");
        }
        if (foodIds == null || foodIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sd = startDate.trim();
        String st = stopDate.trim();
        IngredientAnalysisData d = loadIngredientAnalysisData(sd, st, disId, null, depFatherId, foodIds);
        Map<Integer, Map<String, String>> out = new LinkedHashMap<>();
        for (Integer foodId : foodIds) {
            if (foodId == null) {
                continue;
            }
            Map<String, Object> dishRow = buildIngredientAnalysisDishRow(foodId,
                    d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                    d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods, d.disGoodsById,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS,
                    d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                    d.scopeListPriceRevenueTotal, d.scopeSubtotalOutbound123);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("theoryCostPerPortion", dishRowFieldString(dishRow.get("theoryCostPerPortion")));
            m.put("actualCostPerPortion", dishRowFieldString(dishRow.get("actualCostPerPortion")));
            m.put("diffCostPerPortion", dishRowFieldString(dishRow.get("diffCostPerPortion")));
            m.put("salesPortions", dishRowFieldString(dishRow.get("salesPortions")));
            out.put(foodId, m);
        }
        return out;
    }

    private static final BigDecimal DASHBOARD_USAGE_ABNORMAL_THRESHOLD = new BigDecimal("0.15");
    private static final BigDecimal DASHBOARD_COST_OTHER_MERGE_PCT = new BigDecimal("5");
    private static final int DASHBOARD_TREND_MAX_MONTHS = 18;


    @Override
    public Map<String, Object> buildDishIngredientDashboard(String startDate, String stopDate, Integer disId, Integer depFatherId,
            Integer foodId, String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId) {
        validateDashboardParams(startDate, stopDate, disId, depFatherId, foodId);
        String sd = startDate.trim();
        String st = stopDate.trim();
        String gran = resolveTrendGranularity(trendGranularity);
        IngredientAnalysisData d = loadIngredientAnalysisData(sd, st, disId, null, depFatherId,
                Collections.singleton(foodId));
        return buildDishIngredientDashboardFromData(d, disId, depFatherId, foodId, sd, st, trendStartDate, trendEndDate, gran, primaryDisGoodsId);
    }

    private static void validateDashboardParams(String startDate, String stopDate, Integer disId, Integer depFatherId, Integer foodId) {
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate 不能为空");
        }
        if (disId == null || depFatherId == null || foodId == null) {
            throw new IllegalArgumentException("disId、depFatherId、foodId 不能为空");
        }
    }

    private static String resolveTrendGranularity(String trendGranularity) {
        String gran = trendGranularity == null || trendGranularity.trim().isEmpty()
                ? "month"
                : trendGranularity.trim().toLowerCase(Locale.ROOT);
        if (!"month".equals(gran)) {
            throw new IllegalArgumentException("trendGranularity 当前仅支持 month");
        }
        return gran;
    }

    private Map<String, Object> buildDishIngredientDashboardFromData(IngredientAnalysisData d,
            Integer disId, Integer depFatherId, Integer foodId, String sd, String st,
            String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId) {
        Map<String, Object> dishRow = buildIngredientAnalysisDishRow(foodId,
                d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods, d.disGoodsById,
                d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS,
                d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO),
                d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO),
                d.scopeListPriceRevenueTotal, d.scopeSubtotalOutbound123);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ingredientRows =
                (List<Map<String, Object>>) dishRow.get("ingredientRows");
        if (ingredientRows == null) {
            ingredientRows = new ArrayList<>();
        } else {
            ingredientRows = new ArrayList<>(ingredientRows);
        }

        GbDistributerFoodEntity food = d.foodById.get(foodId);
        BigDecimal q = nz(d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO));
        BigDecimal listUnit = GbDepartmentGoodsStockReduceSupport.coerceDecimal(
                food == null ? null : food.getGbDfFoodPrice());
        BigDecimal listPriceRevenueDish = q.multiply(listUnit).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTheoryDish = BigDecimal.ZERO;
        for (Map<String, Object> ir : ingredientRows) {
            BigDecimal thPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("theoryCostPerPortion"));
            if (q.compareTo(BigDecimal.ZERO) > 0) {
                totalTheoryDish = totalTheoryDish.add(thPp.multiply(q));
            }
        }
        enrichDishIngredientDashboardRows(ingredientRows, q, listPriceRevenueDish, totalTheoryDish);

        BigDecimal totalActPortion = BigDecimal.ZERO;
        for (Map<String, Object> ir : ingredientRows) {
            totalActPortion = totalActPortion.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualCostPerPortion")));
        }
        Map<String, Object> costStructure = buildDashboardCostStructure(ingredientRows, totalActPortion);

        int resolvedPrimary = primaryDisGoodsId != null ? primaryDisGoodsId : pickPrimaryDisGoodsIdForTrend(ingredientRows);
        LocalDate mainStart = parseIsoLocalDate(sd);
        LocalDate mainEnd = parseIsoLocalDate(st);
        LocalDate trendStart = trendStartDate != null && !trendStartDate.trim().isEmpty()
                ? parseIsoLocalDate(trendStartDate.trim())
                : mainEnd.minusMonths(5);
        LocalDate trendEnd = trendEndDate != null && !trendEndDate.trim().isEmpty()
                ? parseIsoLocalDate(trendEndDate.trim())
                : mainEnd;
        if (trendStart.isAfter(trendEnd)) {
            LocalDate tmp = trendStart;
            trendStart = trendEnd;
            trendEnd = tmp;
        }
        if (trendStart.isBefore(mainStart)) {
            trendStart = mainStart;
        }
        if (trendEnd.isAfter(mainEnd)) {
            trendEnd = mainEnd;
        }

        Map<String, Object> costTrend = buildDashboardCostTrendMonthSeriesFast(d, disId, depFatherId, foodId, resolvedPrimary,
                trendStart, trendEnd);

        Map<String, Object> dish = new LinkedHashMap<>();
        dish.put("foodId", foodId);
        dish.put("foodName", dishRow.get("dishName"));
        dish.put("listPricePerPortion", food != null && food.getGbDfFoodPrice() != null ? food.getGbDfFoodPrice() : "");
        dish.put("salesPortions", dishRow.get("salesPortions"));
        dish.put("salesAmount", dishRow.get("salesAmount"));
        dish.put("salesUnitPrice", dishRow.get("salesUnitPrice"));
        dish.put("theoryCostPerPortion", dishRow.get("theoryCostPerPortion"));
        dish.put("actualCostPerPortion", dishRow.get("actualCostPerPortion"));
        dish.put("diffCostPerPortion", dishRow.get("diffCostPerPortion"));
        putDishIngredientDashboardDisplayMargins(dish, food, ingredientRows, d);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", sd);
        out.put("stopDate", st);
        out.put("trendStartDate", trendStart.toString());
        out.put("trendEndDate", trendEnd.toString());
        out.put("trendGranularity", trendGranularity);
        out.put("disId", disId);
        out.put("depFatherId", depFatherId);
        out.put("dish", dish);
        out.put("ingredientRows", ingredientRows);
        out.put("costStructure", costStructure);
        out.put("costTrend", costTrend);
        out.put("scopeOutboundSubtotals", buildScopeOutboundSubtotalsMapFromData(d));
        out.put("summarySuggestionZh", buildDashboardSummarySuggestionZh(String.valueOf(dishRow.get("dishName")), ingredientRows));
        out.put("disclaimerZh", INGREDIENT_ANALYSIS_DISCLAIMER_ZH);
        return out;
    }

    @Override
    public Map<String, Object> buildDishIngredientConsumptionAudit(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Integer foodId,
            Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate/endDate 不能为空");
        }
        if (disId == null || foodId == null) {
            throw new IllegalArgumentException("disId、dishId/foodId 不能为空");
        }
        String sd = startDate.trim();
        String st = stopDate.trim();
        IngredientAnalysisData d = loadIngredientAnalysisData(sd, st, disId, searchDepId, depFatherId,
                Collections.singleton(foodId), scopeDepartmentIdsAllowFilter);
        return buildDishIngredientConsumptionAuditFromData(d, foodId, sd, st);
    }

    private Map<String, Object> buildDishIngredientConsumptionAuditFromData(IngredientAnalysisData d,
            Integer foodId, String sd, String st) {
        List<GbDistributerFoodGoodsEntity> selectedRecipe = d.recipeByFoodId.get(foodId);
        if (selectedRecipe == null) {
            selectedRecipe = Collections.emptyList();
        }
        LinkedHashMap<Integer, MergeRecipeU> mergedByGood = mergeRecipeUnitsByDisGoods(selectedRecipe);

        Set<Integer> relatedFoodIds = new HashSet<>();
        relatedFoodIds.add(foodId);
        for (Integer gId : mergedByGood.keySet()) {
            Set<Integer> fids = d.foodIdsByGoods.get(gId);
            if (fids != null) {
                relatedFoodIds.addAll(fids);
            }
        }
        // 复用主数据已加载的 recipe/food，不重复查询
        Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId = new LinkedHashMap<>();
        Map<Integer, GbDistributerFoodEntity> foodById = new LinkedHashMap<>();
        for (Integer fid : relatedFoodIds) {
            List<GbDistributerFoodGoodsEntity> recipe = d.recipeByFoodId.get(fid);
            if (recipe != null) {
                recipeByFoodId.put(fid, recipe);
            }
            GbDistributerFoodEntity food = d.foodById.get(fid);
            if (food != null) {
                foodById.put(fid, food);
            }
        }

        GbDistributerFoodEntity food = d.foodById.get(foodId);
        String dishName = food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "";
        BigDecimal q = nz(d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO));
        BigDecimal salesSubtotal = nz(d.salesSubtotalByFood.getOrDefault(foodId, BigDecimal.ZERO));
        BigDecimal listPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(
                food == null ? null : food.getGbDfFoodPrice());

        Map<Integer, BigDecimal> theoryByGoods = d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap());
        BigDecimal listPriceRevenueDish = q.multiply(listPp).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTheoryDishCost = BigDecimal.ZERO;
        for (Map.Entry<Integer, MergeRecipeU> en : mergedByGood.entrySet()) {
            IngredientAnalysisLineCalc tmp = computeIngredientAnalysisLineCalc(foodId, en.getKey(), en.getValue(),
                    theoryByGoods, d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS, q);
            totalTheoryDishCost = totalTheoryDishCost.add(tmp.thCost);
        }
        BigDecimal sumProducePp = BigDecimal.ZERO;
        BigDecimal sumTheoryPp = BigDecimal.ZERO;
        BigDecimal sumTheoryOutboundQty = BigDecimal.ZERO;
        BigDecimal sumActualOutboundQty = BigDecimal.ZERO;
        BigDecimal sumTheoryOutboundAmount = BigDecimal.ZERO;
        BigDecimal sumActualOutboundAmount = BigDecimal.ZERO;
        List<Map<String, Object>> ingredients = new ArrayList<>();
        for (Map.Entry<Integer, MergeRecipeU> en : mergedByGood.entrySet()) {
            Integer gId = en.getKey();
            MergeRecipeU mu = en.getValue();
            IngredientAnalysisLineCalc sel = computeIngredientAnalysisLineCalc(foodId, gId, mu, theoryByGoods,
                    d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS, q);
            sumTheoryOutboundQty = sumTheoryOutboundQty.add(sel.theoryRecipe);
            sumActualOutboundQty = sumActualOutboundQty.add(sel.alloc1);
            sumTheoryOutboundAmount = sumTheoryOutboundAmount.add(sel.thCost);
            sumActualOutboundAmount = sumActualOutboundAmount.add(sel.a1c);
            if (q.compareTo(BigDecimal.ZERO) > 0) {
                sumProducePp = sumProducePp.add(sel.a1c.divide(q, 8, RoundingMode.HALF_UP));
                sumTheoryPp = sumTheoryPp.add(sel.thCost.divide(q, 8, RoundingMode.HALF_UP));
            }

            GbDistributerGoodsEntity goodsEntity = d.disGoodsById.get(gId);
            String unit = goodsEntity != null && goodsEntity.getGbDgGoodsStandardname() != null
                    ? goodsEntity.getGbDgGoodsStandardname().trim()
                    : "";
            GbDistributerFoodGoodsEntity foodGoodsLine = findActiveFoodGoodsLine(selectedRecipe, gId);

            Map<String, Object> ingRow = new LinkedHashMap<>();
            ingRow.put("goodsId", gId);
            ingRow.put("goodsName", resolveGoodsDisplayName(mu.goodsName, goodsEntity));
            ingRow.put("unit", unit);
            ingRow.put("gbDgGoodsStandardWeight",
                    goodsEntity != null ? goodsEntity.getGbDgGoodsStandardWeight() : null);
            ingRow.put("unitPrice", ingredientTwoDecimals(sel.p1));
            ingRow.put("salesPortions", ingredientSalesCountString(q));
            ingRow.put("salesAmount", ingredientTwoDecimals(salesSubtotal));
            putAuditIngredientConsumptionMetrics(ingRow, sel, q, listPriceRevenueDish, totalTheoryDishCost, mu,
                    foodGoodsLine);
            ingRow.put("stockReduceRecords", buildAuditStockReduceRecords(d.reduceParams, gId, unit));
            ingRow.put("relatedDishAllocations", buildAuditRelatedDishAllocations(d, gId, foodId, recipeByFoodId, foodById));
            ingredients.add(ingRow);
        }

        String actualGross = marginRateOnListPriceString(listPp, sumProducePp);
        String theoryGross = marginRateOnListPriceString(listPp, sumTheoryPp);
        Map<String, Object> dishSummary = new LinkedHashMap<>();
        dishSummary.put("dishId", foodId);
        dishSummary.put("dishName", dishName);
        dishSummary.put("salesPortions", ingredientSalesCountString(q));
        dishSummary.put("salesAmount", ingredientTwoDecimals(salesSubtotal));
        dishSummary.put("theoryOutboundQty", ingredientTwoDecimals(sumTheoryOutboundQty));
        dishSummary.put("actualOutboundQty", ingredientTwoDecimals(sumActualOutboundQty));
        dishSummary.put("outboundQtyDeviation",
                ingredientTwoDecimals(sumActualOutboundQty.subtract(sumTheoryOutboundQty)));
        dishSummary.put("theoryOutboundAmount", ingredientTwoDecimals(sumTheoryOutboundAmount));
        dishSummary.put("actualOutboundAmount", ingredientTwoDecimals(sumActualOutboundAmount));
        dishSummary.put("outboundCostDeviation",
                ingredientTwoDecimals(sumActualOutboundAmount.subtract(sumTheoryOutboundAmount)));
        dishSummary.put("actualGrossMarginRate", actualGross);
        dishSummary.put("theoryGrossMarginRate", theoryGross);
        dishSummary.put("grossMarginRateDeviation", marginRateDeviationString(actualGross, theoryGross));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", sd);
        out.put("endDate", st);
        out.put("stopDate", st);
        out.put("dishSummary", dishSummary);
        out.put("ingredients", ingredients);
        return out;
    }

    @Override
    public Map<String, Object> buildDishIngredientDashboardAndAudit(String startDate, String stopDate, Integer disId,
            Integer depFatherId, String searchDepId, Integer foodId,
            String trendStartDate, String trendEndDate, String trendGranularity, Integer primaryDisGoodsId) {
        if (startDate == null || startDate.trim().isEmpty() || stopDate == null || stopDate.trim().isEmpty()) {
            throw new IllegalArgumentException("startDate、stopDate 不能为空");
        }
        if (disId == null || depFatherId == null || foodId == null) {
            throw new IllegalArgumentException("disId、depFatherId、foodId 不能为空");
        }
        String sd = startDate.trim();
        String st = stopDate.trim();
        String gran = resolveTrendGranularity(trendGranularity);

        // 一次数据加载，dashboard 与 audit 共享
        IngredientAnalysisData d = loadIngredientAnalysisData(sd, st, disId, searchDepId, depFatherId,
                Collections.singleton(foodId));

        // 看板数据
        Map<String, Object> dashboardData = buildDishIngredientDashboardFromData(d,
                disId, depFatherId, foodId, sd, st, trendStartDate, trendEndDate, gran, primaryDisGoodsId);

        // 消耗排查数据
        Map<String, Object> auditData = buildDishIngredientConsumptionAuditFromData(d, foodId, sd, st);

        // 合并返回：看板字段在前，排查字段追加
        Map<String, Object> out = new LinkedHashMap<>(dashboardData);
        out.put("dishSummary", auditData.get("dishSummary"));
        out.put("consumptionAuditIngredients", auditData.get("ingredients"));
        // auditData 提供的 startDate/endDate/stopDate 覆盖看板的（可能有 stopDate/endDate 双属）
        out.put("startDate", sd);
        out.put("stopDate", st);
        return out;
    }

    private static LinkedHashMap<Integer, MergeRecipeU> mergeRecipeUnitsByDisGoods(
            List<GbDistributerFoodGoodsEntity> recipe) {
        LinkedHashMap<Integer, MergeRecipeU> mergedByGood = new LinkedHashMap<>();
        if (recipe == null) {
            return mergedByGood;
        }
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            Integer gId = line.getGbDfgDisGoodsId();
            if (gId == null) {
                continue;
            }
            BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
            if (u.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            MergeRecipeU mu = mergedByGood.computeIfAbsent(gId, k -> new MergeRecipeU());
            mu.sumU = mu.sumU.add(u);
            if (mu.goodsName.isEmpty()) {
                String nm = line.getGbDfgGoodsName();
                if (nm != null && !nm.trim().isEmpty()) {
                    mu.goodsName = nm.trim();
                }
            }
        }
        return mergedByGood;
    }

    private static String resolveGoodsDisplayName(String recipeName, GbDistributerGoodsEntity goodsEntity) {
        if (recipeName != null && !recipeName.isEmpty()) {
            return recipeName;
        }
        if (goodsEntity != null && goodsEntity.getGbDgGoodsName() != null) {
            return goodsEntity.getGbDgGoodsName().trim();
        }
        return "";
    }

    /** 配方行中按 disGoodsId 取有效配料行（用于小单位换算字段）。 */
    private static GbDistributerFoodGoodsEntity findActiveFoodGoodsLine(
            List<GbDistributerFoodGoodsEntity> recipe, Integer disGoodsId) {
        if (recipe == null || disGoodsId == null) {
            return null;
        }
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            if (disGoodsId.equals(line.getGbDfgDisGoodsId())) {
                return line;
            }
        }
        return null;
    }

    /** 关联菜品：每份用量 / 小单位 / 包装换算（与 {@link GbDistributerFoodGoodsEntity} 字段名一致）。 */
    private static void putFoodGoodsPortionConversionFields(Map<String, Object> row,
            GbDistributerFoodGoodsEntity line) {
        if (line == null) {
            row.put("gbDfgPortionAmount", null);
            row.put("gbDfgPortionUnit", null);
            row.put("gbDfgPackQtyInMin", null);
            return;
        }
        BigDecimal portionAmount = line.getGbDfgPortionAmount();
        row.put("gbDfgPortionAmount", portionAmount == null ? null : plainQty(portionAmount));
        String portionUnit = line.getGbDfgPortionUnit();
        row.put("gbDfgPortionUnit",
                portionUnit == null || portionUnit.trim().isEmpty() ? null : portionUnit.trim());
        BigDecimal packQtyInMin = line.getGbDfgPackQtyInMin();
        row.put("gbDfgPackQtyInMin", packQtyInMin == null ? null : plainQty(packQtyInMin));
    }

    /** 排查页核心 9 项：理论/实际出库 qty·amount、差异、本菜分摊（不含毛利率）。 */
    private static void putAuditOutboundCoreMetrics(Map<String, Object> row, IngredientAnalysisLineCalc c) {
        row.put("theoryOutboundQty", ingredientTwoDecimals(c.theoryRecipe));
        row.put("theoryOutboundAmount", ingredientTwoDecimals(c.thCost));
        row.put("outboundCostDeviation", ingredientTwoDecimals(c.a1c.subtract(c.thCost)));
        row.put("actualOutboundQty", ingredientTwoDecimals(c.alloc1));
        row.put("actualOutboundAmount", ingredientTwoDecimals(c.a1c));
        row.put("outboundQtyDeviation", ingredientTwoDecimals(c.alloc1.subtract(c.theoryRecipe)));
        row.put("dishAllocationShare",
                GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(c.share));
        row.put("dishAllocatedQty", ingredientTwoDecimals(c.alloc1));
        row.put("dishAllocatedAmount", ingredientTwoDecimals(c.a1c));
    }

    /**
     * 配料排查 {@code ingredients[]} 单条：理论/实际出库数量与金额、差异、毛利率、本菜分摊等（主菜视角）。
     */
    private static void putAuditIngredientConsumptionMetrics(Map<String, Object> row,
            IngredientAnalysisLineCalc c, BigDecimal dishQ, BigDecimal listPriceRevenueDish,
            BigDecimal totalTheoryDishCost, MergeRecipeU mu, GbDistributerFoodGoodsEntity foodGoodsLine) {
        putAuditOutboundCoreMetrics(row, c);
        String theoryGross = blendedGrossMarginRateOnListPricePerIngredient(
                listPriceRevenueDish, totalTheoryDishCost, c.thCost, c.thCost);
        String actualGross = blendedGrossMarginRateOnListPricePerIngredient(
                listPriceRevenueDish, totalTheoryDishCost, c.thCost, c.a1c);
        row.put("theoryGrossMarginRate", theoryGross);
        row.put("actualGrossMarginRate", actualGross);
        row.put("grossMarginRateDeviation", marginRateDeviationString(actualGross, theoryGross));
        row.put("recipeUsagePerPortion", plainQty(mu.sumU));
        putFoodGoodsPortionConversionFields(row, foodGoodsLine);
        row.put("goodsTotalOutboundQty", ingredientTwoDecimals(c.w1g));
        row.put("goodsTotalOutboundAmount", ingredientTwoDecimals(c.s1g));
        if (dishQ.compareTo(BigDecimal.ZERO) > 0) {
            row.put("theoryPerPortion",
                    ingredientTwoDecimals(c.theoryRecipe.divide(dishQ, 8, RoundingMode.HALF_UP)));
            row.put("actualPerPortion",
                    ingredientTwoDecimals(c.alloc1.divide(dishQ, 8, RoundingMode.HALF_UP)));
        } else {
            row.put("theoryPerPortion", null);
            row.put("actualPerPortion", null);
        }
        row.put("actualConsumption", ingredientTwoDecimals(c.w1g.add(c.w2g).add(c.w3g)));
        row.put("selectedDishTheoryConsumption", row.get("theoryOutboundQty"));
        row.put("selectedDishAllocatedConsumption", row.get("actualOutboundQty"));
        row.put("consumptionDeviation", row.get("outboundQtyDeviation"));
        row.put("selectedDishAllocatedCost", row.get("actualOutboundAmount"));
    }

    private static void putAuditRelatedDishPerPortionMetrics(Map<String, Object> row,
            IngredientAnalysisLineCalc c, BigDecimal dishQ) {
        if (dishQ.compareTo(BigDecimal.ZERO) > 0) {
            row.put("theoryPerPortion",
                    ingredientTwoDecimals(c.theoryRecipe.divide(dishQ, 8, RoundingMode.HALF_UP)));
            row.put("actualPerPortion",
                    ingredientTwoDecimals(c.alloc1.divide(dishQ, 8, RoundingMode.HALF_UP)));
        } else {
            row.put("theoryPerPortion", null);
            row.put("actualPerPortion", null);
        }
    }

    private List<Map<String, Object>> buildAuditStockReduceRecords(Map<String, Object> reduceParams, Integer gId,
            String unit) {
        Map<String, Object> p = new HashMap<>(reduceParams);
        p.put("disGoodsId", gId);
        List<GbDepartmentGoodsStockReduceEntity> rows =
                gbDepartmentGoodsStockReduceService.queryStockReduceListByParams(p);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        gbDepartmentGoodsStockReduceService.enrichReducesWithStockAndPurchaseBatch(rows, unit);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GbDepartmentGoodsStockReduceEntity r : rows) {
            Integer type = r.getGbDgsrType();
            if (type == null || (!GbConstants.StockReduceType.PRODUCTION.equals(type)
                    && !GbConstants.StockReduceType.WASTE.equals(type)
                    && !GbConstants.StockReduceType.LOSS.equals(type))) {
                continue;
            }
            BigDecimal weight = GbDepartmentGoodsStockReduceSupport.coerceDecimal(r.getGbDgsrWeight());
            BigDecimal subtotal = GbDepartmentGoodsStockReduceSupport.coerceDecimal(r.getGbDgsrSubtotal());
            BigDecimal unitPrice = weight.compareTo(BigDecimal.ZERO) > 0
                    ? subtotal.divide(weight, 8, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("recordId", r.getGbDepartmentGoodsStockReduceId());
            rec.put("stockBatchId", r.getGbDgsrGbGoodsStockId());
            rec.put("reduceTime", formatAuditReduceTime(r));
            rec.put("quantity", plainQty(weight));
            rec.put("unit", unit);
            rec.put("unitPrice", ingredientTwoDecimals(unitPrice));
            rec.put("amount", ingredientTwoDecimals(subtotal));
            rec.put("purchaseBatch", r.getPurchaseBatchInfo());
            putAuditReduceTypeFields(rec, type);
            out.add(rec);
        }
        return out;
    }

    private static String formatAuditReduceTime(GbDepartmentGoodsStockReduceEntity r) {
        String full = r.getGbDgsrFullTime();
        if (full != null && full.trim().length() >= 16) {
            return full.trim().substring(0, 16);
        }
        if (full != null && !full.trim().isEmpty()) {
            return full.trim();
        }
        String date = r.getGbDgsrDate();
        return date != null && !date.trim().isEmpty() ? date.trim() + " 00:00" : "";
    }

    private static void putAuditReduceTypeFields(Map<String, Object> rec, Integer type) {
        if (GbConstants.StockReduceType.PRODUCTION.equals(type)) {
            rec.put("reduceType", "PRODUCTION");
            rec.put("reduceTypeName", "制作");
        } else if (GbConstants.StockReduceType.WASTE.equals(type)) {
            rec.put("reduceType", "WASTE");
            rec.put("reduceTypeName", "废弃");
        } else if (GbConstants.StockReduceType.LOSS.equals(type)) {
            rec.put("reduceType", "LOSS");
            rec.put("reduceTypeName", "损耗");
        } else {
            rec.put("reduceType", "OTHER");
            rec.put("reduceTypeName", "其它");
        }
    }

    private List<Map<String, Object>> buildAuditRelatedDishAllocations(IngredientAnalysisData d, Integer gId,
            Integer selectedFoodId, Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId,
            Map<Integer, GbDistributerFoodEntity> foodById) {
        Set<Integer> fids = d.foodIdsByGoods.get(gId);
        if (fids == null || fids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> ordered = new ArrayList<>(fids);
        ordered.sort((a, b) -> {
            if (Objects.equals(a, selectedFoodId)) {
                return -1;
            }
            if (Objects.equals(b, selectedFoodId)) {
                return 1;
            }
            BigDecimal qa = nz(d.consumptionQtyByFood.getOrDefault(a, BigDecimal.ZERO));
            BigDecimal qb = nz(d.consumptionQtyByFood.getOrDefault(b, BigDecimal.ZERO));
            return qb.compareTo(qa);
        });
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Integer, AuditRelatedDishMarginSnapshot> marginByFoodId = new HashMap<>();
        for (Integer fid : ordered) {
            List<GbDistributerFoodGoodsEntity> recipe = recipeByFoodId.getOrDefault(fid, Collections.emptyList());
            MergeRecipeU mu = mergeRecipeUnitsByDisGoods(recipe).get(gId);
            if (mu == null) {
                continue;
            }
            BigDecimal dishQ = nz(d.consumptionQtyByFood.getOrDefault(fid, BigDecimal.ZERO));
            Map<Integer, BigDecimal> theoryByGoods =
                    d.theoryWtByFoodAndGoods.getOrDefault(fid, Collections.emptyMap());
            IngredientAnalysisLineCalc c = computeIngredientAnalysisLineCalc(fid, gId, mu, theoryByGoods,
                    d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS, dishQ);
            GbDistributerFoodEntity f = foodById.get(fid);
            String name = f != null && f.getGbDfFoodName() != null ? f.getGbDfFoodName().trim() : "";
            GbDistributerFoodGoodsEntity foodGoodsLine = findActiveFoodGoodsLine(recipe, gId);
            AuditRelatedDishMarginSnapshot margin = marginByFoodId.computeIfAbsent(fid,
                    k -> computeAuditRelatedDishMarginSnapshot(d, k, recipeByFoodId, foodById));
            BigDecimal salesSubtotal = nz(d.salesSubtotalByFood.getOrDefault(fid, BigDecimal.ZERO));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dishId", fid);
            row.put("dishName", name);
            row.put("isSelectedDish", Objects.equals(fid, selectedFoodId));
            row.put("salesPortions", ingredientSalesCountString(dishQ));
            row.put("salesAmount", ingredientTwoDecimals(salesSubtotal));
            row.put("recipeUsagePerPortion", plainQty(mu.sumU));
            putFoodGoodsPortionConversionFields(row, foodGoodsLine);
            row.put("unitPrice", ingredientTwoDecimals(c.p1));
            row.put("goodsTotalOutboundQty", ingredientTwoDecimals(c.w1g));
            row.put("goodsTotalOutboundAmount", ingredientTwoDecimals(c.s1g));
            putAuditOutboundCoreMetrics(row, c);
            putAuditRelatedDishPerPortionMetrics(row, c, dishQ);
            row.put("theoryGrossMarginRate", margin.theoryGrossMarginRate);
            row.put("actualGrossMarginRate", margin.actualGrossMarginRate);
            row.put("grossMarginRateDeviation", margin.grossMarginRateDeviation);
            row.put("theoryConsumption", row.get("theoryOutboundQty"));
            row.put("allocatedConsumption", row.get("actualOutboundQty"));
            row.put("consumptionDeviation", row.get("outboundQtyDeviation"));
            row.put("allocatedCost", row.get("actualOutboundAmount"));
            rows.add(row);
        }
        return rows;
    }

    /** 关联菜品行：整菜标价 vs 理论/生产(type1) 成本/份的毛利率（与 dishSummary 同口径）。 */
    private static final class AuditRelatedDishMarginSnapshot {
        final String theoryGrossMarginRate;
        final String actualGrossMarginRate;
        final String grossMarginRateDeviation;

        private AuditRelatedDishMarginSnapshot(String theoryGrossMarginRate, String actualGrossMarginRate,
                String grossMarginRateDeviation) {
            this.theoryGrossMarginRate = theoryGrossMarginRate;
            this.actualGrossMarginRate = actualGrossMarginRate;
            this.grossMarginRateDeviation = grossMarginRateDeviation;
        }
    }

    private AuditRelatedDishMarginSnapshot computeAuditRelatedDishMarginSnapshot(IngredientAnalysisData d,
            Integer foodId, Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId,
            Map<Integer, GbDistributerFoodEntity> foodById) {
        List<GbDistributerFoodGoodsEntity> recipe = recipeByFoodId.getOrDefault(foodId, Collections.emptyList());
        LinkedHashMap<Integer, MergeRecipeU> mergedByGood = mergeRecipeUnitsByDisGoods(recipe);
        BigDecimal q = nz(d.consumptionQtyByFood.getOrDefault(foodId, BigDecimal.ZERO));
        Map<Integer, BigDecimal> theoryByGoods =
                d.theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap());
        BigDecimal sumProducePp = BigDecimal.ZERO;
        BigDecimal sumTheoryPp = BigDecimal.ZERO;
        for (Map.Entry<Integer, MergeRecipeU> en : mergedByGood.entrySet()) {
            IngredientAnalysisLineCalc line = computeIngredientAnalysisLineCalc(foodId, en.getKey(), en.getValue(),
                    theoryByGoods, d.sumTheoryByGoods, d.sumRecipeUnitByGoods, d.sumSalesQtyByGoods, d.sumNeedByGoods,
                    d.reduceW, d.reduceS, d.wasteW, d.wasteS, d.lossW, d.lossS, q);
            if (q.compareTo(BigDecimal.ZERO) > 0) {
                sumProducePp = sumProducePp.add(line.a1c.divide(q, 8, RoundingMode.HALF_UP));
                sumTheoryPp = sumTheoryPp.add(line.thCost.divide(q, 8, RoundingMode.HALF_UP));
            }
        }
        GbDistributerFoodEntity food = foodById.get(foodId);
        BigDecimal listPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(
                food == null ? null : food.getGbDfFoodPrice());
        String actualGross = marginRateOnListPriceString(listPp, sumProducePp);
        String theoryGross = marginRateOnListPriceString(listPp, sumTheoryPp);
        return new AuditRelatedDishMarginSnapshot(theoryGross, actualGross,
                marginRateDeviationString(actualGross, theoryGross));
    }

    private static String marginRateDeviationString(String actualMargin, String theoryMargin) {
        if (actualMargin == null || theoryMargin == null) {
            return null;
        }
        try {
            BigDecimal a = new BigDecimal(actualMargin);
            BigDecimal t = new BigDecimal(theoryMargin);
            return a.subtract(t).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 看板「整菜头区」展示字段：区间综合毛利率、本菜标价 vs type1 / vs type1+2+3 / 理论毛利率、净毛利/份、父级毛利率标尺。
     * <p>{@code blendedGrossMarginRateOnListPrice} 与 {@code depGeFoodBusiness} 统一为实收口径：
     * {@code (实收金额 − type1+2+3 总成本) ÷ 实收金额}；
     * {@code grossMarginRateOnListPriceUsingActual123} 保留标价口径 {@code (listPricePerPortion − actualCostPerPortion) / listPrice}；
     * {@code grossMarginRateOnListPrice} 为仅 type1 生产口径。</p>
     */
    private void putDishIngredientDashboardDisplayMargins(Map<String, Object> dish, GbDistributerFoodEntity food,
            List<Map<String, Object>> ingredientRows, IngredientAnalysisData d) {
        BigDecimal listPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(food == null ? null : food.getGbDfFoodPrice());
        BigDecimal actPp123 = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dish.get("actualCostPerPortion"));
        BigDecimal thPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dish.get("theoryCostPerPortion"));
        BigDecimal sumProducePp = BigDecimal.ZERO;
        if (ingredientRows != null) {
            for (Map<String, Object> ir : ingredientRows) {
                sumProducePp = sumProducePp.add(
                        GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("produceCostPerPortion")));
            }
        }
        // 本菜综合毛利率 = (实收 − type1+2+3 总成本) / 实收，用本菜数据而非 scope
        BigDecimal dishSalesAmt = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dish.get("salesAmount"));
        BigDecimal dishPortions = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dish.get("salesPortions"));
        BigDecimal dishCostTotal = actPp123.multiply(dishPortions).setScale(2, RoundingMode.HALF_UP);
        String comprehensive = marginRateOnListPriceString(dishSalesAmt, dishCostTotal);
        dish.put("comprehensiveGrossMarginRateOnListPrice", comprehensive);

        // blendedGrossMarginRateOnListPrice: 与 depGeFoodBusiness 统一口径
        // 公式：(实收金额 − type1+2+3 总成本) ÷ 实收金额，百分数两位小数
        String blendedGrossOnRevenue = marginRateOnListPriceString(dishSalesAmt, dishCostTotal);
        dish.put("blendedGrossMarginRateOnListPrice", blendedGrossOnRevenue);

        // grossMarginRateOnListPriceUsingActual123: 保留标价口径 (listPricePerPortion − actualCostPerPortion) / listPrice
        String grossAct123OnList = marginRateOnListPriceString(listPp, actPp123);
        String grossType1OnList = marginRateOnListPriceString(listPp, sumProducePp);
        if (grossType1OnList == null && listPp.compareTo(BigDecimal.ZERO) > 0) {
            grossType1OnList = grossAct123OnList;
        }
        dish.put("grossMarginRateOnListPrice", grossType1OnList);
        dish.put("grossMarginRateOnListPriceUsingActual123", grossAct123OnList);

        String theoryOnList = marginRateOnListPriceString(listPp, thPp);
        dish.put("grossMarginRateTheoryOnListPrice", theoryOnList);
        dish.put("blendedGrossMarginRateTheoryOnListPrice", theoryOnList);

        if (listPp.compareTo(BigDecimal.ZERO) > 0) {
            dish.put("netGrossProfitPerPortion", ingredientTwoDecimals(listPp.subtract(actPp123)));
        } else {
            dish.put("netGrossProfitPerPortion", ingredientTwoDecimals(BigDecimal.ZERO.subtract(actPp123)));
        }

        putDishGrossMarginStandardOnDashboard(dish, food);
    }

    /**
     * 毛利率标尺 display，与 {@code blendedGrossMarginRateOnListPrice} 统一使用实收口径：
     * {@code (实收金额 − type1+2+3 总成本) ÷ 实收金额}。
     */
    private void putDishGrossMarginStandardOnDashboard(Map<String, Object> dish, GbDistributerFoodEntity food) {
        GbDistributerFoodEntity directParent = null;
        if (food != null) {
            Integer pid = food.getGbDfFoodFatherId();
            if (pid != null && pid != 0) {
                directParent = gbDistributerFoodService.queryObject(pid);
            }
        }
        BigDecimal dishSalesAmt = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dish.get("salesAmount"));
        BigDecimal actPp123 = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dish.get("actualCostPerPortion"));
        BigDecimal dishPortions = GbDepartmentGoodsStockReduceSupport.coerceDecimal(dish.get("salesPortions"));
        BigDecimal dishCostTotal = actPp123.multiply(dishPortions).setScale(2, RoundingMode.HALF_UP);
        BigDecimal blendedRatio = null;
        if (dishSalesAmt.compareTo(BigDecimal.ZERO) > 0) {
            blendedRatio = dishSalesAmt.subtract(dishCostTotal).divide(dishSalesAmt, 8, RoundingMode.HALF_UP);
        } else if (dishSalesAmt.signum() == 0 && dishCostTotal.signum() == 0) {
            blendedRatio = BigDecimal.ZERO;
        }
        GrossMarginStandardDisplay.putOnMap(dish, blendedRatio, directParent);
    }

    private static LocalDate parseIsoLocalDate(String s) {
        String t = s.length() >= 10 ? s.substring(0, 10) : s;
        return LocalDate.parse(t);
    }

    private static void enrichDishIngredientDashboardRows(List<Map<String, Object>> rows, BigDecimal q,
            BigDecimal listPriceRevenueDish, BigDecimal totalTheoryDish) {
        if (rows == null) {
            return;
        }
        BigDecimal totalTh = nz(totalTheoryDish);
        BigDecimal totalActPp = BigDecimal.ZERO;
        for (Map<String, Object> ir : rows) {
            totalActPp = totalActPp.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualCostPerPortion")));
        }
        for (Map<String, Object> ir : rows) {
            BigDecimal actPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualCostPerPortion"));
            BigDecimal sharePct = BigDecimal.ZERO;
            if (totalActPp.compareTo(BigDecimal.ZERO) > 0) {
                sharePct = actPp.multiply(new BigDecimal("100")).divide(totalActPp, 8, RoundingMode.HALF_UP);
            }
            ir.put("costShareOfDishActualPercent", ingredientTwoDecimals(sharePct));

            BigDecimal theoryW = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("theoryUsage"));
            BigDecimal actualW = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualUsage"));
            BigDecimal devRatio = BigDecimal.ZERO;
            if (theoryW.compareTo(BigDecimal.ZERO) > 0) {
                devRatio = actualW.subtract(theoryW).divide(theoryW, 8, RoundingMode.HALF_UP);
            }
            ir.put("usageDeviationRatio", devRatio.stripTrailingZeros().toPlainString());
            ir.put("usageDeviationPercent", ingredientTwoDecimals(devRatio.multiply(new BigDecimal("100"))));
            ir.put("usageStatus", devRatio.abs().compareTo(DASHBOARD_USAGE_ABNORMAL_THRESHOLD) > 0 ? "ABNORMAL" : "NORMAL");

            BigDecimal thPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("theoryCostPerPortion"));
            BigDecimal prodPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("produceCostPerPortion"));
            BigDecimal listAllocPp = BigDecimal.ZERO;
            BigDecimal contribPp = BigDecimal.ZERO;
            if (q.compareTo(BigDecimal.ZERO) > 0 && totalTh.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal thCostRow = thPp.multiply(q);
                BigDecimal listAlloc = listPriceRevenueDish.multiply(thCostRow).divide(totalTh, 8, RoundingMode.HALF_UP);
                listAllocPp = listAlloc.divide(q, 8, RoundingMode.HALF_UP);
                contribPp = listAllocPp.subtract(prodPp);
            }
            ir.put("actualRevenueAllocatedPerPortion", ingredientTwoDecimals(listAllocPp));
            ir.put("grossProfitContributionPerPortion", ingredientTwoDecimals(contribPp));

            String level;
            String sug;
            if ("ABNORMAL".equals(ir.get("usageStatus")) && sharePct.compareTo(new BigDecimal("25")) >= 0) {
                level = "FOCUS";
                sug = "用量偏离明显且成本占比高，建议核查切配标准、出库与销售录入，并关注采购价波动。";
            } else if ("ABNORMAL".equals(ir.get("usageStatus")) || sharePct.compareTo(new BigDecimal("15")) >= 0) {
                level = "OPTIMIZE";
                sug = "可关注配方执行与分摊差异，结合偏差率与单份成本优化。";
            } else {
                level = "NORMAL";
                sug = "整体可控，保持现有出品与盘点节奏即可。";
            }
            ir.put("suggestionLevel", level);
            ir.put("suggestionZh", sug);
        }
    }

    private static int pickPrimaryDisGoodsIdForTrend(List<Map<String, Object>> rows) {
        Integer best = null;
        BigDecimal bestAct = BigDecimal.ZERO;
        if (rows == null) {
            return 0;
        }
        for (Map<String, Object> ir : rows) {
            Integer gid = toInt(ir.get("disGoodsId"));
            if (gid == null) {
                continue;
            }
            BigDecimal ap = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualCostPerPortion"));
            if (ap.compareTo(bestAct) > 0) {
                bestAct = ap;
                best = gid;
            }
        }
        return best == null ? 0 : best;
    }

    /**
     * 快速趋势查询：一次全区间 food_sales + 一次全区间 theory_weights 批量加载，每月仅查 3 条 reduce agg。
     * <p>配方、菜品、商品数据复用主 {@code IngredientAnalysisData}，消除原按月完整 {@code loadIngredientAnalysisData} 的冗余。</p>
     */
    private Map<String, Object> buildDashboardCostTrendMonthSeriesFast(IngredientAnalysisData d,
            Integer disId, Integer depFatherId, Integer foodId, int primaryDisGoodsId,
            LocalDate trendStart, LocalDate trendEnd) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("granularity", "month");
        out.put("primaryDisGoodsId", primaryDisGoodsId > 0 ? primaryDisGoodsId : null);
        List<Map<String, Object>> points = new ArrayList<>();
        String primaryName = null;
        if (primaryDisGoodsId > 0 && d.disGoodsById != null) {
            GbDistributerGoodsEntity g = d.disGoodsById.get(primaryDisGoodsId);
            if (g != null && g.getGbDgGoodsName() != null) {
                primaryName = g.getGbDgGoodsName().trim();
            }
        }
        out.put("primaryGoodsName", primaryName);

        // 快速退出：趋势区间不在主数据区间内或无效
        if (trendStart.isAfter(trendEnd)) {
            out.put("points", points);
            return out;
        }

        List<Integer> scopeDepIds = resolveScopeDepIds(disId, null, depFatherId, null);
        String ts = trendStart.toString();
        String te = trendEnd.toString();

        // 1) 一次全区间 food_sales
        List<GbDepFoodSalesEntity> allSales = loadFoodSales(ts, te, disId, scopeDepIds);
        // YearMonth → (saleIds, consumptionQty, salesSubtotal)
        Map<YearMonth, TrendMonthAgg> monthMap = groupSalesByMonth(allSales, foodId, trendStart, trendEnd);
        // 收集全部 saleId → 按 month 组织为 saleMonthMap
        Map<Integer, YearMonth> saleMonthMap = new LinkedHashMap<>();
        for (Map.Entry<YearMonth, TrendMonthAgg> e : monthMap.entrySet()) {
            for (Integer sid : e.getValue().saleIds) {
                saleMonthMap.put(sid, e.getKey());
            }
        }

        // 2) 一次全区间 theory_weights（按 sale_id 分批）
        Map<Integer, Map<Integer, BigDecimal>> theoryBySaleAndGoods = new LinkedHashMap<>();
        if (!saleMonthMap.isEmpty()) {
            List<Integer> allSaleIds = new ArrayList<>(saleMonthMap.keySet());
            for (int i = 0; i < allSaleIds.size(); i += IN_BATCH) {
                int to = Math.min(i + IN_BATCH, allSaleIds.size());
                List<Integer> batch = allSaleIds.subList(i, to);
                LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<>();
                w.ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, ts)
                        .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, te)
                        .in(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, batch);
                applyDepFoodGoodsFilter(w, scopeDepIds);
                for (GbDepFoodGoodsSalesEntity r : gbDepFoodGoodsSalesService.list(w)) {
                    Integer sid = r.getGbDfgsFoodSalesId();
                    Integer gId = r.getGbDfgsDisGoodsId();
                    if (sid == null || gId == null) {
                        continue;
                    }
                    BigDecimal amt = GbDepartmentGoodsStockReduceSupport.coerceDecimal(r.getGbDfgsGoodsAmount());
                    theoryBySaleAndGoods.computeIfAbsent(sid, k -> new HashMap<>()).merge(gId, amt, BigDecimal::add);
                }
            }
        }

        // 3) 预计算配方聚合（所有月份共享，不随月份变化）
        List<GbDistributerFoodGoodsEntity> recipe = d.recipeByFoodId.get(foodId);
        Map<Integer, BigDecimal> recipeSumUByGoods = new LinkedHashMap<>();
        if (recipe != null) {
            for (GbDistributerFoodGoodsEntity line : recipe) {
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                Integer gId = line.getGbDfgDisGoodsId();
                if (gId == null) {
                    continue;
                }
                BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
                if (u.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                recipeSumUByGoods.merge(gId, u, BigDecimal::add);
            }
        }

        // 4) 按月迭代
        YearMonth ymStart = YearMonth.from(trendStart);
        YearMonth ymEnd = YearMonth.from(trendEnd);
        int monthCount = 0;
        for (YearMonth ym = ymStart; !ym.isAfter(ymEnd) && monthCount < DASHBOARD_TREND_MAX_MONTHS; ym = ym.plusMonths(1), monthCount++) {
            LocalDate ms = ym.atDay(1);
            LocalDate me = ym.atEndOfMonth();
            if (ms.isBefore(trendStart)) {
                ms = trendStart;
            }
            if (me.isAfter(trendEnd)) {
                me = trendEnd;
            }
            if (ms.isAfter(me)) {
                continue;
            }
            try {
                TrendMonthAgg agg = monthMap.getOrDefault(ym, TrendMonthAgg.EMPTY);
                BigDecimal q = agg.consumptionQty;
                BigDecimal st = agg.salesSubtotal;

                // 月度理论用量
                Map<Integer, BigDecimal> mTheoryByGoods = new HashMap<>();
                for (Integer sid : agg.saleIds) {
                    Map<Integer, BigDecimal> perSale = theoryBySaleAndGoods.get(sid);
                    if (perSale != null) {
                        for (Map.Entry<Integer, BigDecimal> e : perSale.entrySet()) {
                            mTheoryByGoods.merge(e.getKey(), e.getValue(), BigDecimal::add);
                        }
                    }
                }

                // 月度出库数据（3 条轻量 reduce agg）
                Map<String, Object> mRp = buildReduceParams(disId, null, depFatherId, ms.toString(), me.toString());
                List<Map<String, Object>> r1 = gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(mRp);
                List<Map<String, Object>> r2 = gbDepartmentGoodsStockReduceService.queryReduceAggByDisGoodsByType(
                        mRp, GbConstants.StockReduceType.WASTE);
                List<Map<String, Object>> r3 = gbDepartmentGoodsStockReduceService.queryReduceAggByDisGoodsByType(
                        mRp, GbConstants.StockReduceType.LOSS);
                Map<Integer, BigDecimal> mrw = new HashMap<>();
                Map<Integer, BigDecimal> mrs = new HashMap<>();
                fillReduceAgg(r1, mrw, mrs);
                Map<Integer, BigDecimal> mww = new HashMap<>();
                Map<Integer, BigDecimal> mws = new HashMap<>();
                fillReduceAgg(r2, mww, mws);
                Map<Integer, BigDecimal> mlw = new HashMap<>();
                Map<Integer, BigDecimal> mls = new HashMap<>();
                fillReduceAgg(r3, mlw, mls);

                // 单菜场景下 sumTheoryByGoods=mTheoryByGoods，sumRecipeUnitByGoods=recipeSumUByGoods
                Map<Integer, BigDecimal> mSumSales = new LinkedHashMap<>();
                Map<Integer, BigDecimal> mSumNeed = new LinkedHashMap<>();
                for (Map.Entry<Integer, BigDecimal> e : recipeSumUByGoods.entrySet()) {
                    mSumSales.put(e.getKey(), q);
                    mSumNeed.put(e.getKey(), e.getValue().multiply(q));
                }
                BigDecimal s123 = sumMapValues(mrs).add(sumMapValues(mws)).add(sumMapValues(mls));

                // 调用 buildIngredientAnalysisDishRow（preload recipe + food，消除 2 条冗余查询）
                Map<String, Object> rowM = buildIngredientAnalysisDishRow(foodId,
                        mTheoryByGoods, mTheoryByGoods, recipeSumUByGoods, mSumSales, mSumNeed,
                        d.disGoodsById,
                        mrw, mrs, mww, mws, mlw, mls,
                        q, st, BigDecimal.ZERO, s123,
                        d.foodById.get(foodId), recipe == null ? Collections.emptyList() : recipe);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ingM = (List<Map<String, Object>>) rowM.get("ingredientRows");
                String costPp = "0.00";
                if (primaryDisGoodsId > 0 && ingM != null) {
                    for (Map<String, Object> ir : ingM) {
                        if (Integer.valueOf(primaryDisGoodsId).equals(toInt(ir.get("disGoodsId")))) {
                            Object v = ir.get("actualCostPerPortion");
                            costPp = v != null ? String.valueOf(v) : "0.00";
                            if (primaryName == null && ir.get("gbDgGoodsName") != null) {
                                primaryName = String.valueOf(ir.get("gbDgGoodsName")).trim();
                            }
                            break;
                        }
                    }
                }
                Map<String, Object> pt = new LinkedHashMap<>();
                pt.put("periodLabel", ym.toString());
                pt.put("periodStart", ms.toString());
                pt.put("periodEnd", me.toString());
                pt.put("soldPortions", rowM.get("salesPortions"));
                pt.put("primaryIngredientCostPerPortion", costPp);
                points.add(pt);
            } catch (RuntimeException ex) {
                log.warn("[dishIngredientDashboard] trend month skip ym={}: {}", ym, ex.toString());
            }
        }
        if (primaryName != null) {
            out.put("primaryGoodsName", primaryName);
        }
        out.put("points", points);
        out.put("changeSummaryZh", buildTrendChangeSummaryZh(points));
        return out;
    }

    /** 月度趋势聚合：sale id 列表 + 消费份数 + 实收小计。 */
    private static final class TrendMonthAgg {
        static final TrendMonthAgg EMPTY = new TrendMonthAgg(Collections.emptySet(), BigDecimal.ZERO, BigDecimal.ZERO);
        final Set<Integer> saleIds;
        final BigDecimal consumptionQty;
        final BigDecimal salesSubtotal;

        TrendMonthAgg(Set<Integer> saleIds, BigDecimal consumptionQty, BigDecimal salesSubtotal) {
            this.saleIds = saleIds;
            this.consumptionQty = consumptionQty;
            this.salesSubtotal = salesSubtotal;
        }
    }

    private Map<YearMonth, TrendMonthAgg> groupSalesByMonth(List<GbDepFoodSalesEntity> sales, Integer foodId,
            LocalDate trendStart, LocalDate trendEnd) {
        Map<YearMonth, TrendMonthAgg> out = new LinkedHashMap<>();
        if (sales == null) {
            return out;
        }
        for (GbDepFoodSalesEntity s : sales) {
            if (s.getGbDfsFoodId() == null || !s.getGbDfsFoodId().equals(foodId)) {
                continue;
            }
            Integer sid = s.getGbDepFoodSalesId();
            String dateStr = s.getGbDfsFullDate();
            if (sid == null || dateStr == null) {
                continue;
            }
            YearMonth ym;
            try {
                LocalDate d = parseIsoLocalDate(dateStr);
                ym = YearMonth.from(d);
            } catch (RuntimeException e) {
                continue;
            }
            // 裁剪到趋势区间
            if (ym.getYear() < trendStart.getYear() || (ym.getYear() == trendStart.getYear() && ym.getMonthValue() < trendStart.getMonthValue())) {
                continue;
            }
            if (ym.getYear() > trendEnd.getYear() || (ym.getYear() == trendEnd.getYear() && ym.getMonthValue() > trendEnd.getMonthValue())) {
                continue;
            }
            Set<Integer> ids = out.computeIfAbsent(ym, k -> new TrendMonthAgg(new LinkedHashSet<>(), BigDecimal.ZERO, BigDecimal.ZERO)).saleIds;
            ids.add(sid);
            BigDecimal cq = BigDecimal.ZERO;
            BigDecimal st = BigDecimal.ZERO;
            if (GbDepFoodSalesMetricsSupport.countsAsOperationalSales(s)) {
                cq = GbDepFoodSalesMetricsSupport.operationalSalesQty(s);
                st = GbDepFoodSalesMetricsSupport.operationalActualRevenue(s);
            }
            TrendMonthAgg newAgg = new TrendMonthAgg(
                    ids,
                    out.get(ym).consumptionQty.add(cq),
                    out.get(ym).salesSubtotal.add(st));
            out.put(ym, newAgg);
        }
        return out;
    }

    private static String buildTrendChangeSummaryZh(List<Map<String, Object>> points) {
        if (points == null || points.size() < 2) {
            return points == null || points.isEmpty() ? "趋势区间至少需要两个自然月才有环比说明。" : "仅一个月数据，暂无环比。";
        }
        Map<String, Object> a = points.get(points.size() - 2);
        Map<String, Object> b = points.get(points.size() - 1);
        BigDecimal ca = GbDepartmentGoodsStockReduceSupport.coerceDecimal(a.get("primaryIngredientCostPerPortion"));
        BigDecimal cb = GbDepartmentGoodsStockReduceSupport.coerceDecimal(b.get("primaryIngredientCostPerPortion"));
        if (ca.compareTo(BigDecimal.ZERO) <= 0) {
            return "前期单份成本基数过低或为 0，暂不计算环比百分比。";
        }
        BigDecimal pct = cb.subtract(ca).divide(ca, 8, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP);
        String dir = pct.compareTo(BigDecimal.ZERO) >= 0 ? "上升" : "下降";
        return "聚焦原料近两月单份实际成本" + dir + "约 " + pct.abs().stripTrailingZeros().toPlainString() + "%（期末相对上月）。";
    }

    private static String buildDashboardSummarySuggestionZh(String dishName, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return (dishName == null || dishName.isEmpty() ? "本菜" : dishName) + "暂无配方配料数据，请维护配方后再看分析。";
        }
        Map<String, Object> focus = null;
        for (Map<String, Object> ir : rows) {
            if ("FOCUS".equals(ir.get("suggestionLevel"))) {
                focus = ir;
                break;
            }
        }
        if (focus == null) {
            for (Map<String, Object> ir : rows) {
                if ("OPTIMIZE".equals(ir.get("suggestionLevel"))) {
                    focus = ir;
                    break;
                }
            }
        }
        if (focus == null) {
            focus = rows.get(0);
        }
        String nm = focus.get("gbDgGoodsName") != null ? String.valueOf(focus.get("gbDgGoodsName")) : "主配料";
        String dn = dishName == null || dishName.isEmpty() ? "本菜" : dishName;
        return dn + "：建议优先关注「" + nm + "」——用量偏差 "
                + String.valueOf(focus.get("usageDeviationPercent")) + "%（"
                + String.valueOf(focus.get("usageStatus")) + "），成本占 "
                + String.valueOf(focus.get("costShareOfDishActualPercent")) + "%；可结合趋势曲线与采购价综合管控。";
    }

    private Map<String, Object> buildScopeOutboundSubtotalsMap(Map<String, Object> reduceParams) {
        BigDecimal s1 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceProduceTotal(reduceParams));
        BigDecimal s2 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceWasteTotal(reduceParams));
        BigDecimal s3 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceLossTotal(reduceParams));
        BigDecimal s6 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceEmployeeMealTotal(reduceParams));
        BigDecimal s123 = s1.add(s2).add(s3);
        BigDecimal s23 = s2.add(s3);
        BigDecimal wasteLossRatio = s123.compareTo(BigDecimal.ZERO) > 0
                ? s23.divide(s123, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subtotalProduceType1", plainMoney(s1));
        m.put("subtotalWasteType2", plainMoney(s2));
        m.put("subtotalLossType3", plainMoney(s3));
        m.put("subtotalOutbound123", plainMoney(s123));
        m.put("wasteLossAmountType23", plainMoney(s23));
        m.put("wasteLossRatioInOutbound123",
                GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(wasteLossRatio));
        m.put("subtotalEmployeeMealType6", plainMoney(s6));
        return m;
    }

    /** 从已加载的 {@code IngredientAnalysisData} 中计算出库小计，避免重复调用4条 total 查询（s1/s2/s3 从已聚合的 map 求和）。仅 employeeMeal（s6）需额外查询。 */
    private Map<String, Object> buildScopeOutboundSubtotalsMapFromData(IngredientAnalysisData d) {
        BigDecimal s1 = sumMapValues(d.reduceS);
        BigDecimal s2 = sumMapValues(d.wasteS);
        BigDecimal s3 = sumMapValues(d.lossS);
        BigDecimal s123 = s1.add(s2).add(s3);
        BigDecimal s23 = s2.add(s3);
        BigDecimal s6 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceEmployeeMealTotal(d.reduceParams));
        BigDecimal wasteLossRatio = s123.compareTo(BigDecimal.ZERO) > 0
                ? s23.divide(s123, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subtotalProduceType1", plainMoney(s1));
        m.put("subtotalWasteType2", plainMoney(s2));
        m.put("subtotalLossType3", plainMoney(s3));
        m.put("subtotalOutbound123", plainMoney(s123));
        m.put("wasteLossAmountType23", plainMoney(s23));
        m.put("wasteLossRatioInOutbound123",
                GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(wasteLossRatio));
        m.put("subtotalEmployeeMealType6", plainMoney(s6));
        return m;
    }

    private static BigDecimal sumMapValues(Map<Integer, BigDecimal> map) {
        if (map == null || map.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : map.values()) {
            if (v != null) {
                sum = sum.add(v);
            }
        }
        return sum;
    }

    private static Map<String, Object> buildDashboardCostStructure(List<Map<String, Object>> rows,
            BigDecimal totalActPortion) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("basis", "actualCostPerPortion");
        out.put("totalCostPerPortion", ingredientTwoDecimals(nz(totalActPortion)));
        out.put("otherMergeThresholdPercent", ingredientTwoDecimals(DASHBOARD_COST_OTHER_MERGE_PCT));
        if (rows == null || rows.isEmpty() || nz(totalActPortion).compareTo(BigDecimal.ZERO) <= 0) {
            out.put("segments", Collections.emptyList());
            return out;
        }
        List<Map<String, Object>> raw = new ArrayList<>();
        for (Map<String, Object> ir : rows) {
            BigDecimal ap = GbDepartmentGoodsStockReduceSupport.coerceDecimal(ir.get("actualCostPerPortion"));
            BigDecimal pct = ap.multiply(new BigDecimal("100")).divide(totalActPortion, 8, RoundingMode.HALF_UP);
            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("disGoodsId", ir.get("disGoodsId"));
            seg.put("name", ir.get("gbDgGoodsName"));
            seg.put("costPerPortion", ir.get("actualCostPerPortion"));
            seg.put("sharePercent", ingredientTwoDecimals(pct));
            raw.add(seg);
        }
        raw.sort(Comparator.comparing(o -> GbDepartmentGoodsStockReduceSupport.coerceDecimal(o.get("costPerPortion")),
                Comparator.reverseOrder()));
        List<Map<String, Object>> segments = new ArrayList<>();
        BigDecimal otherCost = BigDecimal.ZERO;
        BigDecimal otherShare = BigDecimal.ZERO;
        for (Map<String, Object> seg : raw) {
            BigDecimal sh = GbDepartmentGoodsStockReduceSupport.coerceDecimal(seg.get("sharePercent"));
            if (sh.compareTo(DASHBOARD_COST_OTHER_MERGE_PCT) < 0) {
                otherCost = otherCost.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(seg.get("costPerPortion")));
                otherShare = otherShare.add(sh);
            } else {
                segments.add(seg);
            }
        }
        if (otherCost.compareTo(BigDecimal.ZERO) > 0 || otherShare.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> other = new LinkedHashMap<>();
            other.put("key", "OTHER");
            other.put("name", "其他");
            other.put("costPerPortion", ingredientTwoDecimals(otherCost));
            other.put("sharePercent", ingredientTwoDecimals(otherShare.setScale(2, RoundingMode.HALF_UP)));
            segments.add(other);
        }
        out.put("segments", segments);
        return out;
    }

    private static void fillReduceAgg(List<Map<String, Object>> rows, Map<Integer, BigDecimal> w, Map<Integer, BigDecimal> s) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Integer gid = toInt(row.get("disGoodsId"));
            if (gid == null) {
                continue;
            }
            w.put(gid, toBd(row.get("weightSum")));
            s.put(gid, toBd(row.get("subtotalSum")));
        }
    }

    /** 配料分析里是否打「娃娃菜」专用追踪日志（配方名含「娃娃菜」即命中，如大娃娃菜）。 */
    private static boolean traceWawaCabbageName(String recipeGoodsName) {
        return recipeGoodsName != null && recipeGoodsName.contains("娃娃菜");
    }

    private static String profileGoodsNameForTrace(Map<Integer, GbDistributerGoodsEntity> disGoodsById, Integer gId) {
        if (gId == null || disGoodsById == null) {
            return "";
        }
        GbDistributerGoodsEntity e = disGoodsById.get(gId);
        if (e == null || e.getGbDgGoodsName() == null) {
            return "";
        }
        return e.getGbDgGoodsName().trim();
    }

    /**
     * 报表生成后：按「娃娃菜」相关 disGoodsId 汇总区间出库、全表 sumNeed / 销售子表合计，并校验各菜分摊 type1 之和是否等于 W1。
     */
    private void logWawaCabbageIngredientReportSummary(IngredientAnalysisData d, List<Map<String, Object>> salesDishRows) {
        if (!log.isInfoEnabled() || salesDishRows == null) {
            return;
        }
        LinkedHashSet<Integer> gIds = new LinkedHashSet<>();
        for (Map<String, Object> dishRow : salesDishRows) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ing = (List<Map<String, Object>>) dishRow.get("ingredientRows");
            if (ing == null) {
                continue;
            }
            for (Map<String, Object> ir : ing) {
                if (traceWawaCabbageName((String) ir.get("gbDgGoodsName"))) {
                    Integer gid = toInt(ir.get("disGoodsId"));
                    if (gid != null) {
                        gIds.add(gid);
                    }
                }
            }
        }
        if (gIds.isEmpty()) {
            return;
        }
        for (Integer gId : gIds) {
            BigDecimal w1 = nz(d.reduceW.get(gId));
            BigDecimal w2 = nz(d.wasteW.get(gId));
            BigDecimal w3 = nz(d.lossW.get(gId));
            BigDecimal s1 = nz(d.reduceS.get(gId));
            BigDecimal s2 = nz(d.wasteS.get(gId));
            BigDecimal s3 = nz(d.lossS.get(gId));
            BigDecimal sumNeed = nz(d.sumNeedByGoods == null ? null : d.sumNeedByGoods.get(gId));
            BigDecimal sumSalesT = nz(d.sumTheoryByGoods.get(gId));
            BigDecimal sumAlloc1FromRows = BigDecimal.ZERO;
            for (Map<String, Object> dishRow : salesDishRows) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ing = (List<Map<String, Object>>) dishRow.get("ingredientRows");
                if (ing == null) {
                    continue;
                }
                for (Map<String, Object> ir : ing) {
                    if (!gId.equals(toInt(ir.get("disGoodsId")))) {
                        continue;
                    }
                    sumAlloc1FromRows = sumAlloc1FromRows.add(toBd(ir.get("actualProduceUsage")));
                }
            }
            String profile = profileGoodsNameForTrace(d.disGoodsById, gId);
            BigDecimal w123 = w1.add(w2).add(w3);
            BigDecimal diffAlloc1VsW1 = sumAlloc1FromRows.subtract(w1);
            log.info("[ingredientTrace娃娃菜] GLOBAL_SUMMARY disGoodsId={} profileGbDgGoodsName={} "
                            + "scopeW1_produceWeight={} scopeW2_wasteWeight={} scopeW3_lossWeight={} scopeW123_totalWeight={} "
                            + "scopeS1_produceSubtotal={} scopeS2_wasteSubtotal={} scopeS3_lossSubtotal={} "
                            + "sumNeed_allDishesRecipe_qTimesU={} sumSalesT_allDishesDepFoodGoodsSales={} "
                            + "sumPerDish_alloc1_produceShare_checkShouldEqualW1={} diff_sumAlloc1_minus_W1={}",
                    gId,
                    profile,
                    plainQty(w1),
                    plainQty(w2),
                    plainQty(w3),
                    plainQty(w123),
                    plainQty(s1),
                    plainQty(s2),
                    plainQty(s3),
                    plainQty(sumNeed),
                    plainQty(sumSalesT),
                    plainQty(sumAlloc1FromRows),
                    plainQty(diffAlloc1VsW1));
        }
    }

    /**
     * 配料分析单行：分摊重量与成本（与 {@link #buildIngredientAnalysisDishRow} 内层循环一致），便于先汇总整菜理论/生产实际再写毛利率。
     */
    private static final class IngredientAnalysisLineCalc {
        final BigDecimal t;
        final BigDecimal w1g;
        final BigDecimal s1g;
        final BigDecimal w2g;
        final BigDecimal s2g;
        final BigDecimal w3g;
        final BigDecimal s3g;
        final BigDecimal recipeUnitSum;
        final BigDecimal salesSumForGood;
        final BigDecimal dishU;
        final BigDecimal sumT;
        final BigDecimal sumNeed;
        final BigDecimal alloc1;
        final BigDecimal share;
        final BigDecimal alloc2;
        final BigDecimal alloc3;
        final BigDecimal actual12;
        final BigDecimal theoryRecipe;
        final BigDecimal p1;
        final BigDecimal p2;
        final BigDecimal p3;
        final BigDecimal thCost;
        final BigDecimal a1c;
        final BigDecimal a2c;
        final BigDecimal a3c;
        final BigDecimal act;

        private IngredientAnalysisLineCalc(BigDecimal t, BigDecimal w1g, BigDecimal s1g, BigDecimal w2g, BigDecimal s2g,
                BigDecimal w3g, BigDecimal s3g, BigDecimal recipeUnitSum, BigDecimal salesSumForGood, BigDecimal dishU,
                BigDecimal sumT, BigDecimal sumNeed, BigDecimal alloc1, BigDecimal share, BigDecimal alloc2,
                BigDecimal alloc3, BigDecimal actual12, BigDecimal theoryRecipe, BigDecimal p1, BigDecimal p2,
                BigDecimal p3, BigDecimal thCost, BigDecimal a1c, BigDecimal a2c, BigDecimal a3c, BigDecimal act) {
            this.t = t;
            this.w1g = w1g;
            this.s1g = s1g;
            this.w2g = w2g;
            this.s2g = s2g;
            this.w3g = w3g;
            this.s3g = s3g;
            this.recipeUnitSum = recipeUnitSum;
            this.salesSumForGood = salesSumForGood;
            this.dishU = dishU;
            this.sumT = sumT;
            this.sumNeed = sumNeed;
            this.alloc1 = alloc1;
            this.share = share;
            this.alloc2 = alloc2;
            this.alloc3 = alloc3;
            this.actual12 = actual12;
            this.theoryRecipe = theoryRecipe;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.thCost = thCost;
            this.a1c = a1c;
            this.a2c = a2c;
            this.a3c = a3c;
            this.act = act;
        }
    }

    private IngredientAnalysisLineCalc computeIngredientAnalysisLineCalc(int foodId, Integer gId, MergeRecipeU mu,
            Map<Integer, BigDecimal> theoryByGoods,
            Map<Integer, BigDecimal> sumTheoryByGoods,
            Map<Integer, BigDecimal> sumRecipeUnitByGoods,
            Map<Integer, BigDecimal> sumSalesQtyByGoods,
            Map<Integer, BigDecimal> sumNeedByGoods,
            Map<Integer, BigDecimal> reduceW,
            Map<Integer, BigDecimal> reduceS,
            Map<Integer, BigDecimal> wasteW,
            Map<Integer, BigDecimal> wasteS,
            Map<Integer, BigDecimal> lossW,
            Map<Integer, BigDecimal> lossS,
            BigDecimal q) {
        BigDecimal t = nz(theoryByGoods.get(gId));
        BigDecimal w1g = nz(reduceW.get(gId));
        BigDecimal s1g = nz(reduceS.get(gId));
        BigDecimal w2g = nz(wasteW.get(gId));
        BigDecimal s2g = nz(wasteS.get(gId));
        BigDecimal w3g = nz(lossW.get(gId));
        BigDecimal s3g = nz(lossS.get(gId));
        BigDecimal recipeUnitSum = nz(sumRecipeUnitByGoods.get(gId));
        BigDecimal salesSumForGood = nz(sumSalesQtyByGoods.get(gId));
        BigDecimal dishU = mu.sumU;
        BigDecimal sumT = nz(sumTheoryByGoods.get(gId));
        BigDecimal needThis = q.multiply(dishU);
        BigDecimal sumNeed = nz(sumNeedByGoods == null ? null : sumNeedByGoods.get(gId));
        String tagIng = "ingredientAnalysis foodId=" + foodId + " disGoodsId=" + gId;
        BigDecimal alloc1 = allocateOutboundWeightForDishGood(w1g, t, sumT, q, salesSumForGood, dishU,
                recipeUnitSum, needThis, sumNeed, tagIng);
        BigDecimal share = w1g.compareTo(BigDecimal.ZERO) > 0
                ? alloc1.divide(w1g, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal alloc2 = share.multiply(w2g);
        BigDecimal alloc3 = share.multiply(w3g);
        BigDecimal actual12 = alloc1.add(alloc2).add(alloc3);
        BigDecimal theoryRecipe = q.multiply(dishU);
        BigDecimal p1 = w1g.compareTo(BigDecimal.ZERO) > 0 ? s1g.divide(w1g, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal p2 = w2g.compareTo(BigDecimal.ZERO) > 0 ? s2g.divide(w2g, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal p3 = w3g.compareTo(BigDecimal.ZERO) > 0 ? s3g.divide(w3g, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal thCost = p1.multiply(theoryRecipe);
        BigDecimal a1c = p1.multiply(alloc1);
        BigDecimal a2c = p2.multiply(alloc2);
        BigDecimal a3c = p3.multiply(alloc3);
        BigDecimal act = a1c.add(a2c).add(a3c);
        return new IngredientAnalysisLineCalc(t, w1g, s1g, w2g, s2g, w3g, s3g, recipeUnitSum, salesSumForGood, dishU,
                sumT, sumNeed, alloc1, share, alloc2, alloc3, actual12, theoryRecipe, p1, p2, p3, thCost, a1c, a2c, a3c, act);
    }

    private Map<String, Object> buildIngredientAnalysisDishRow(Integer foodId,
            Map<Integer, BigDecimal> theoryByGoods,
            Map<Integer, BigDecimal> sumTheoryByGoods,
            Map<Integer, BigDecimal> sumRecipeUnitByGoods,
            Map<Integer, BigDecimal> sumSalesQtyByGoods,
            Map<Integer, BigDecimal> sumNeedByGoods,
            Map<Integer, GbDistributerGoodsEntity> disGoodsById,
            Map<Integer, BigDecimal> reduceW,
            Map<Integer, BigDecimal> reduceS,
            Map<Integer, BigDecimal> wasteW,
            Map<Integer, BigDecimal> wasteS,
            Map<Integer, BigDecimal> lossW,
            Map<Integer, BigDecimal> lossS,
            BigDecimal salesQty,
            BigDecimal salesSubtotal,
            BigDecimal scopeListPriceRevenueTotal,
            BigDecimal scopeSubtotalOutbound123) {
        return buildIngredientAnalysisDishRow(foodId, theoryByGoods, sumTheoryByGoods, sumRecipeUnitByGoods,
                sumSalesQtyByGoods, sumNeedByGoods, disGoodsById, reduceW, reduceS, wasteW, wasteS, lossW, lossS,
                salesQty, salesSubtotal, scopeListPriceRevenueTotal, scopeSubtotalOutbound123, null, null);
    }

    private Map<String, Object> buildIngredientAnalysisDishRow(Integer foodId,
            Map<Integer, BigDecimal> theoryByGoods,
            Map<Integer, BigDecimal> sumTheoryByGoods,
            Map<Integer, BigDecimal> sumRecipeUnitByGoods,
            Map<Integer, BigDecimal> sumSalesQtyByGoods,
            Map<Integer, BigDecimal> sumNeedByGoods,
            Map<Integer, GbDistributerGoodsEntity> disGoodsById,
            Map<Integer, BigDecimal> reduceW,
            Map<Integer, BigDecimal> reduceS,
            Map<Integer, BigDecimal> wasteW,
            Map<Integer, BigDecimal> wasteS,
            Map<Integer, BigDecimal> lossW,
            Map<Integer, BigDecimal> lossS,
            BigDecimal salesQty,
            BigDecimal salesSubtotal,
            BigDecimal scopeListPriceRevenueTotal,
            BigDecimal scopeSubtotalOutbound123,
            GbDistributerFoodEntity foodPreload,
            List<GbDistributerFoodGoodsEntity> recipePreload) {

        GbDistributerFoodEntity food = foodPreload != null ? foodPreload : gbDistributerFoodService.queryObject(foodId);
        String foodName = food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "";
        List<GbDistributerFoodGoodsEntity> recipe =
                recipePreload != null ? recipePreload : gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
        if (recipe == null) {
            recipe = Collections.emptyList();
        }
        BigDecimal q = nz(salesQty);
        String salesPortionStr = ingredientSalesCountString(q);
        String salesAmountStr = ingredientTwoDecimals(nz(salesSubtotal));
        BigDecimal salesUnit = BigDecimal.ZERO;
        if (q.compareTo(BigDecimal.ZERO) > 0) {
            salesUnit = salesSubtotal.divide(q, 4, RoundingMode.HALF_UP);
        }
        String salesUnitStr = ingredientTwoDecimals(salesUnit);

        LinkedHashMap<Integer, MergeRecipeU> mergedByGood = new LinkedHashMap<>();
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            Integer gId = line.getGbDfgDisGoodsId();
            if (gId == null) {
                continue;
            }
            BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
            if (u.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            MergeRecipeU mu = mergedByGood.computeIfAbsent(gId, k -> new MergeRecipeU());
            mu.sumU = mu.sumU.add(u);
            if (mu.goodsName.isEmpty()) {
                String nm = line.getGbDfgGoodsName();
                if (nm != null && !nm.trim().isEmpty()) {
                    mu.goodsName = nm.trim();
                }
            }
        }
        List<Map.Entry<Integer, MergeRecipeU>> mergedEntries = new ArrayList<>(mergedByGood.entrySet());
        List<IngredientAnalysisLineCalc> lineCalcs = new ArrayList<>(mergedEntries.size());
        BigDecimal totalTheoryDish = BigDecimal.ZERO;
        BigDecimal totalActualDish = BigDecimal.ZERO;
        for (Map.Entry<Integer, MergeRecipeU> en : mergedEntries) {
            IngredientAnalysisLineCalc c = computeIngredientAnalysisLineCalc(foodId, en.getKey(), en.getValue(),
                    theoryByGoods, sumTheoryByGoods, sumRecipeUnitByGoods, sumSalesQtyByGoods, sumNeedByGoods,
                    reduceW, reduceS, wasteW, wasteS, lossW, lossS, q);
            lineCalcs.add(c);
            totalTheoryDish = totalTheoryDish.add(c.thCost);
            totalActualDish = totalActualDish.add(c.act);
        }
        BigDecimal listUnitForMargin = GbDepartmentGoodsStockReduceSupport.coerceDecimal(
                food == null ? null : food.getGbDfFoodPrice());
        BigDecimal listPriceRevenueDish = q.multiply(listUnitForMargin).setScale(2, RoundingMode.HALF_UP);
        // 本菜综合毛利率 = (实收 − type1+2+3 总成本) / 实收，用本菜数据而非 scope
        String comprehensiveGrossMarginRateOnListPriceEach = marginRateOnListPriceString(salesSubtotal, totalActualDish);
        String blendedGrossMarginRateTheoryOnListPriceEach;
        if (nz(totalTheoryDish).compareTo(BigDecimal.ZERO) <= 0) {
            blendedGrossMarginRateTheoryOnListPriceEach = listPriceRevenueDish.signum() == 0 ? "0.00" : null;
        } else {
            blendedGrossMarginRateTheoryOnListPriceEach = marginRateOnListPriceString(listPriceRevenueDish, totalTheoryDish);
        }
        List<Map<String, Object>> ingredientRows = new ArrayList<>();
        for (int idx = 0; idx < mergedEntries.size(); idx++) {
            Map.Entry<Integer, MergeRecipeU> en = mergedEntries.get(idx);
            Integer gId = en.getKey();
            MergeRecipeU mu = en.getValue();
            IngredientAnalysisLineCalc c = lineCalcs.get(idx);
            BigDecimal t = c.t;
            BigDecimal w1g = c.w1g;
            BigDecimal s1g = c.s1g;
            BigDecimal w2g = c.w2g;
            BigDecimal s2g = c.s2g;
            BigDecimal w3g = c.w3g;
            BigDecimal s3g = c.s3g;
            BigDecimal recipeUnitSum = c.recipeUnitSum;
            BigDecimal salesSumForGood = c.salesSumForGood;
            BigDecimal dishU = c.dishU;
            BigDecimal sumT = c.sumT;
            BigDecimal sumNeed = c.sumNeed;
            BigDecimal alloc1 = c.alloc1;
            BigDecimal share = c.share;
            BigDecimal alloc2 = c.alloc2;
            BigDecimal alloc3 = c.alloc3;
            BigDecimal actual12 = c.actual12;
            BigDecimal theoryRecipe = c.theoryRecipe;
            BigDecimal p1 = c.p1;
            BigDecimal p2 = c.p2;
            BigDecimal p3 = c.p3;
            BigDecimal thCost = c.thCost;
            BigDecimal a1c = c.a1c;
            BigDecimal a2c = c.a2c;
            BigDecimal a3c = c.a3c;
            BigDecimal act = c.act;
            boolean qPos = q.compareTo(BigDecimal.ZERO) > 0;
            String unitPriceStr = ingredientTwoDecimals(p1);
            Map<String, Object> ir = new LinkedHashMap<>();
            ir.put("disGoodsId", gId);
            ir.put("gbDgGoodsName", mu.goodsName);
            putDisGoodsProfileFields(ir, disGoodsById == null ? null : disGoodsById.get(gId));
            ir.put("unitPrice", unitPriceStr);
            // 同 gb_distributer_food_goods 中该料配方用量在「本菜」上按 disGoodsId 合并后的每份量（与 buildReport 中 recipeUnitPerDish 口径一致）
            ir.put("recipeUnitPerDish", ingredientTwoDecimals(dishU));
            ir.put("theoryUsage", ingredientTwoDecimals(theoryRecipe));
            // 销售子表汇总；多与 theoryUsage 一致，非实物出库。devianceRate = actualProduceUsage÷theoryUsage = produceAllocatedPerSoldPortion÷recipeUnitPerDish
            ir.put("salesUsageFromOrders", ingredientTwoDecimals(t));
            ir.put("actualUsage", ingredientTwoDecimals(actual12));
            ir.put("actualProduceUsage", ingredientTwoDecimals(alloc1));
            ir.put("actualWasteUsage", ingredientTwoDecimals(alloc2));
            ir.put("actualLossUsage", ingredientTwoDecimals(alloc3));
            ir.put("actualLossAndWasteUsage", ingredientTwoDecimals(alloc2.add(alloc3)));
            // 每实销一份：type1+2+3 合计 vs 仅生产 type1；偏差率仅看后者÷单份配方
            if (q.compareTo(BigDecimal.ZERO) > 0) {
                ir.put("allocatedOutboundPerSoldPortion",
                        ingredientTwoDecimals(actual12.divide(q, 8, RoundingMode.HALF_UP)));
                ir.put("produceAllocatedPerSoldPortion",
                        ingredientTwoDecimals(alloc1.divide(q, 8, RoundingMode.HALF_UP)));
            } else {
                ir.put("allocatedOutboundPerSoldPortion", null);
                ir.put("produceAllocatedPerSoldPortion", null);
            }
            ir.put("theoryCostPerPortion", ratioPerPortionString(thCost, q, qPos));
            ir.put("actualCostPerPortion", ratioPerPortionString(act, q, qPos));
            ir.put("produceCostPerPortion", ratioPerPortionString(a1c, q, qPos));
            ir.put("wasteCostPerPortion", ratioPerPortionString(a2c, q, qPos));
            ir.put("lossCostPerPortion", ratioPerPortionString(a3c, q, qPos));
            ir.put("lossAndWasteCostPerPortion", ratioPerPortionString(a2c.add(a3c), q, qPos));
            if (theoryRecipe.compareTo(BigDecimal.ZERO) > 0) {
                // 偏差率 = 仅 type1 生产分摊 ÷ 配方理论（做菜/制作口径，不含 type2/3 摊入分子）
                BigDecimal deviancePct = alloc1
                        .divide(theoryRecipe, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                ir.put("devianceRate", ingredientTwoDecimals(deviancePct));
                GbConstants.IngredientDevianceLevel.LevelAndLabel ul =
                        GbConstants.IngredientDevianceLevel.fromRatePercent(deviancePct);
                Map<String, Object> umap = new LinkedHashMap<>();
                umap.put("level", ul.getLevel());
                umap.put("labelZh", ul.getLabelZh());
                ir.put("deviance", umap);
                if (log.isInfoEnabled() && traceWawaCabbageName(mu.goodsName)) {
                    BigDecimal salesTableVsTheoryPct = t.divide(theoryRecipe, 8, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
                    String profileGoodsName = profileGoodsNameForTrace(disGoodsById, gId);
                    BigDecimal allocPerPortion = actual12.divide(q, 8, RoundingMode.HALF_UP);
                    BigDecimal produceAllocPerPortion = alloc1.divide(q, 8, RoundingMode.HALF_UP);
                    BigDecimal pct123DivTheory = actual12.divide(theoryRecipe, 8, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
                    log.info("[ingredientTrace娃娃菜] PER_DISH dishName={} foodId={} disGoodsId={} recipeGoodsName={} profileGbDgGoodsName={} "
                                    + "salesPortions={} recipeUnitPerDish_theoryPerPortion={} theoryRecipe_qTimesU={} salesSubtableSumT={} "
                                    + "allocatedOutboundPerSoldPortion_actual123_div_q={} produceAllocatedPerSoldPortion_alloc1_div_q={} "
                                    + "scopeW1_produceWeight={} scopeW2_wasteWeight={} scopeW3_lossWeight={} "
                                    + "scopeS1_produceSubtotal={} scopeS2_wasteSubtotal={} scopeS3_lossSubtotal={} "
                                    + "sumNeed_allDishes={} sumSalesT_allDishes_sameAsSumT_inCode={} Q_g_sumSalesPortionsOnRecipes={} S_g_sumRecipeUnitPerDish={} "
                                    + "alloc1_produceShareWeight={} alloc2_wasteShareWeight={} alloc3_lossShareWeight={} actual123_outboundAllocSum={} share_alloc1DivW1={} "
                                    + "deviancePct_produceAlloc1_div_theory={} pct_actual123_div_theory_debug_includes23={} pct_salesSubtableT_div_theory_debug={} devianceLevel={}",
                            foodName,
                            foodId,
                            gId,
                            mu.goodsName,
                            profileGoodsName,
                            plainQty(q),
                            plainQty(dishU),
                            plainQty(theoryRecipe),
                            plainQty(t),
                            plainQty(allocPerPortion),
                            plainQty(produceAllocPerPortion),
                            plainQty(w1g),
                            plainQty(w2g),
                            plainQty(w3g),
                            plainQty(s1g),
                            plainQty(s2g),
                            plainQty(s3g),
                            plainQty(sumNeed),
                            plainQty(sumT),
                            plainQty(salesSumForGood),
                            plainQty(recipeUnitSum),
                            plainQty(alloc1),
                            plainQty(alloc2),
                            plainQty(alloc3),
                            plainQty(actual12),
                            plainQty(share),
                            deviancePct.toPlainString(),
                            pct123DivTheory.toPlainString(),
                            salesTableVsTheoryPct.toPlainString(),
                            ul != null ? ul.getLevel() : null);
                }
            } else {
                ir.put("devianceRate", null);
                ir.put("deviance", null);
                if (log.isInfoEnabled() && traceWawaCabbageName(mu.goodsName)) {
                    String profileGoodsName = profileGoodsNameForTrace(disGoodsById, gId);
                    log.info("[ingredientTrace娃娃菜] PER_DISH dishName={} foodId={} disGoodsId={} recipeGoodsName={} profileGbDgGoodsName={} "
                                    + "deviance_skipped theoryRecipe_zero salesPortions={} recipeUnitPerDish={} salesSubtableSumT={} "
                                    + "scopeW1={} scopeW2={} scopeW3={} sumNeed={}",
                            foodName,
                            foodId,
                            gId,
                            mu.goodsName,
                            profileGoodsName,
                            plainQty(q),
                            plainQty(dishU),
                            plainQty(t),
                            plainQty(w1g),
                            plainQty(w2g),
                            plainQty(w3g),
                            plainQty(sumNeed));
                }
            }
            ir.put("blendedGrossMarginRateOnListPrice",
                    blendedGrossMarginRateOnListPricePerIngredient(listPriceRevenueDish, totalTheoryDish, thCost, a1c));
            ir.put("blendedGrossMarginRateTheoryOnListPrice", blendedGrossMarginRateTheoryOnListPriceEach);
            ir.put("comprehensiveGrossMarginRateOnListPrice", comprehensiveGrossMarginRateOnListPriceEach);
            ingredientRows.add(ir);
        }
        String thDishPp = ratioPerPortionString(totalTheoryDish, q, qPosOf(q));
        String acDishPp = ratioPerPortionString(totalActualDish, q, qPosOf(q));
        String diffDishPp;
        if (qPosOf(q)) {
            diffDishPp = ingredientTwoDecimals(
                    totalActualDish.subtract(totalTheoryDish)
                            .divide(q, 8, RoundingMode.HALF_UP));
        } else {
            diffDishPp = ingredientTwoDecimals(BigDecimal.ZERO);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishId", foodId);
        row.put("dishName", foodName);
        row.put("salesPortions", salesPortionStr);
        row.put("salesAmount", salesAmountStr);
        row.put("salesUnitPrice", salesUnitStr);
        row.put("theoryCostPerPortion", thDishPp);
        row.put("actualCostPerPortion", acDishPp);
        row.put("actualCostAmount", ingredientTwoDecimals(totalActualDish));
        row.put("diffCostPerPortion", diffDishPp);
        row.put("diffRatePerPortion", diffRatePerPortionString(totalTheoryDish, totalActualDish, q, qPosOf(q)));
        // 新增：理论总成本、理论和实际总成本差额
        row.put("theoryCostTotal", ingredientTwoDecimals(totalTheoryDish));
        row.put("costTotalDiff", ingredientTwoDecimals(totalActualDish.subtract(totalTheoryDish)));
        row.put("ingredientCount", ingredientRows.size());
        row.put("ingredientRows", ingredientRows);
        return row;
    }

    /** {@code /ingredientAnalysis} 顶部汇总：实际/理论成本及偏差；包级可见供单测。 */
    static Map<String, Object> buildIngredientAnalysisScopeSalesSubtotals(List<Map<String, Object>> salesDishRows) {
        BigDecimal actualCostTotal = BigDecimal.ZERO;
        BigDecimal theoreticalCostTotal = BigDecimal.ZERO;
        if (salesDishRows != null) {
            for (Map<String, Object> row : salesDishRows) {
                actualCostTotal = actualCostTotal.add(GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("actualCostAmount")));
                BigDecimal qty = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("salesPortions"));
                BigDecimal thPp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("theoryCostPerPortion"));
                theoreticalCostTotal = theoreticalCostTotal.add(thPp.multiply(qty));
            }
        }
        BigDecimal costDeviationTotal = actualCostTotal.subtract(theoreticalCostTotal);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("actualCostTotal", ingredientTwoDecimals(actualCostTotal));
        scope.put("theoreticalCostTotal", ingredientTwoDecimals(theoreticalCostTotal));
        scope.put("costDeviationTotal", ingredientTwoDecimals(costDeviationTotal));
        if (theoreticalCostTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = costDeviationTotal.divide(theoreticalCostTotal, 8, RoundingMode.HALF_UP);
            scope.put("costDeviationRate",
                    GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(ratio));
        } else {
            scope.put("costDeviationRate", actualCostTotal.signum() == 0 && theoreticalCostTotal.signum() == 0
                    ? "0.00"
                    : null);
        }
        return scope;
    }

    /** {@code /ingredientAnalysis} 菜品行排序；包级可见供单测。 */
    static void sortIngredientAnalysisDishRows(List<Map<String, Object>> salesDishRows, String sortMode, boolean asc) {
        Comparator<BigDecimal> valueCmp = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<BigDecimal> nullSafeBd = Comparator.nullsLast(valueCmp);
        Comparator<Map<String, Object>> tieDishId = Comparator.comparing(
                m -> (Integer) m.get("dishId"), Comparator.nullsLast(Comparator.naturalOrder()));
        if ("diff".equals(sortMode)) {
            salesDishRows.sort(Comparator.comparing(
                    (Map<String, Object> o) -> toBd(o.get("diffCostPerPortion")).abs(), nullSafeBd)
                    .thenComparing(tieDishId));
        } else if ("diffrate".equals(sortMode)) {
            salesDishRows.sort(Comparator.comparing(
                    (Map<String, Object> o) -> diffRatePerPortionForSort(o.get("diffRatePerPortion")), nullSafeBd)
                    .thenComparing(tieDishId));
        } else if ("actualcost".equals(sortMode)) {
            salesDishRows.sort(Comparator.comparing(
                    (Map<String, Object> o) -> toBd(o.get("actualCostPerPortion")), nullSafeBd)
                    .thenComparing(tieDishId));
        } else if ("ingredientcount".equals(sortMode)) {
            Comparator<Integer> intCmp = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
            salesDishRows.sort(Comparator.comparing(
                    GbDishCostAnalysisServiceImpl::ingredientCountForSort, intCmp)
                    .thenComparing(tieDishId));
        } else {
            salesDishRows.sort(Comparator.comparing(
                    (Map<String, Object> o) -> toBd(o.get("salesAmount")), nullSafeBd)
                    .thenComparing(tieDishId));
        }
    }

    private static BigDecimal diffRatePerPortionForSort(Object diffRatePerPortion) {
        if (diffRatePerPortion == null) {
            return null;
        }
        return toBd(diffRatePerPortion);
    }

    private static int ingredientCountForSort(Map<String, Object> row) {
        Object count = row.get("ingredientCount");
        if (count instanceof Number) {
            return ((Number) count).intValue();
        }
        Object rows = row.get("ingredientRows");
        if (rows instanceof List) {
            return ((List<?>) rows).size();
        }
        return 0;
    }

    /** (实际 − 理论) ÷ 理论，百分数字符串两位小数；理论为 0 时 {@code null}（排序置后）。 */
    private static String diffRatePerPortionString(BigDecimal totalTheoryDish, BigDecimal totalActualDish,
            BigDecimal q, boolean qPos) {
        if (!qPos || totalTheoryDish == null || totalTheoryDish.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal theoryPp = totalTheoryDish.divide(q, 8, RoundingMode.HALF_UP);
        BigDecimal actualPp = totalActualDish.divide(q, 8, RoundingMode.HALF_UP);
        BigDecimal ratio = actualPp.subtract(theoryPp).divide(theoryPp, 8, RoundingMode.HALF_UP);
        return GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(ratio);
    }

    private static boolean qPosOf(BigDecimal q) {
        return q.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String ratioPerPortionString(BigDecimal num, BigDecimal q, boolean qPos) {
        if (num == null || !qPos) {
            return ingredientTwoDecimals(BigDecimal.ZERO);
        }
        return ingredientTwoDecimals(num.divide(q, 8, RoundingMode.HALF_UP));
    }

    /**
     * 与 {@link GbDepFoodBusinessInsightServiceImpl} 中单菜 {@code grossMarginRateOnListPrice} 相同展示规则：
     * {@code (revenue − cost) ÷ revenue}，百分数字符串固定两位小数；收入≤0 时无成本为 {@code "0.00"} 否则 {@code null}。
     */
    private static String marginRateOnListPriceString(BigDecimal revenue, BigDecimal cost) {
        BigDecimal rev = revenue == null ? BigDecimal.ZERO : revenue;
        BigDecimal co = cost == null ? BigDecimal.ZERO : cost;
        if (rev.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = rev.subtract(co).divide(rev, 8, RoundingMode.HALF_UP);
            return GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(ratio);
        }
        boolean allZero = rev.signum() == 0 && co.signum() == 0;
        return allZero ? "0.00" : null;
    }

    /** 与 {@code businessInsightSummary.comprehensiveGrossMarginRateOnListPrice}：{@code (Σ标价收入 − 区间 type1+2+3 出库金额) ÷ Σ标价收入}。 */
    private static String comprehensiveGrossMarginRateOnListPriceScope(BigDecimal scopeListRevenue,
            BigDecimal scopeOutbound123) {
        return marginRateOnListPriceString(scopeListRevenue, scopeOutbound123);
    }

    /**
     * 本菜标价收入按该行「理论配方成本」占整菜理论成本的比例摊作分母，成本取该行仅 type1 生产实摊金额（与按菜经营分析 {@code blendedGrossMarginRateOnListPrice} 的「实际」同为 type1 口径）。
     */
    private static String blendedGrossMarginRateOnListPricePerIngredient(BigDecimal listPriceRevenueDish,
            BigDecimal totalTheoryDish,
            BigDecimal thCostRow,
            BigDecimal produceCostType1Row) {
        BigDecimal revD = nz(listPriceRevenueDish);
        if (revD.compareTo(BigDecimal.ZERO) <= 0) {
            boolean z = revD.signum() == 0 && nz(thCostRow).signum() == 0 && nz(produceCostType1Row).signum() == 0;
            return z ? "0.00" : null;
        }
        BigDecimal totalTh = nz(totalTheoryDish);
        if (totalTh.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal allocRev = revD.multiply(nz(thCostRow)).divide(totalTh, 8, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        return marginRateOnListPriceString(allocRev, produceCostType1Row);
    }

    /** 配料分析加载数据时：与 {@code buildReport} / {@code scopeOutboundSubtotals.subtotalOutbound123} 同源的区间出库金额合计。 */
    private BigDecimal computeScopeOutbound123Subtotal(Map<String, Object> reduceParams) {
        BigDecimal s1 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceProduceTotal(reduceParams));
        BigDecimal s2 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceWasteTotal(reduceParams));
        BigDecimal s3 = toBdFromDouble(gbDepartmentGoodsStockReduceService.queryReduceLossTotal(reduceParams));
        return s1.add(s2).add(s3);
    }

    /** 配料分析接口中的金额、数量、比例等统一保留两位小数（四舍五入，固定两位展示）。 */
    private static String ingredientTwoDecimals(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 实销份数、区间销量合计等：业务上为整数份，JSON 中无小数（四舍五入到个位）。
     * <p>用于 {@code salesPortions}、{@code totalSalesPortions}、{@code soldPortions} 等与「卖出份数」直接对应的字段。</p>
     */
    private static String ingredientSalesCountString(BigDecimal v) {
        return nz(v).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** 遍历各菜配方一次，得到 S_g=Σu 与 Q_g=Σ(该料涉及菜的实销份数，每菜每料只计一次)。 */
    private static final class RecipeGoodsAgg {
        final Map<Integer, BigDecimal> sumUByGoods;
        final Map<Integer, BigDecimal> sumSalesByGoods;

        RecipeGoodsAgg(Map<Integer, BigDecimal> sumUByGoods, Map<Integer, BigDecimal> sumSalesByGoods) {
            this.sumUByGoods = sumUByGoods;
            this.sumSalesByGoods = sumSalesByGoods;
        }
    }

    /**
     * <ul>
     *   <li>S_g：各原料 g 在参与报表的菜品上，对单份配方 u 求和（同一菜多条同料则 u 相加）。</li>
     *   <li>Q_g：凡配方含 g 且 u&gt;0 的报表内菜品，把该菜本期销售份数 q 加进 Q_g（同一菜多条同料只加一次 q）。</li>
     * </ul>
     * {@code salesDish} 可支撑：sellable = W_g·q_本菜 / (Q_g·u_本菜)；Q_g=0 时回退 W_g/S_g。
     */
    private RecipeGoodsAgg buildRecipeGoodsAggregates(Set<Integer> foodIds, Map<Integer, BigDecimal> salesQtyByFood) {
        return buildRecipeGoodsAggregates(foodIds, salesQtyByFood, loadRecipesByFoodIds(foodIds));
    }

    private RecipeGoodsAgg buildRecipeGoodsAggregates(Set<Integer> foodIds, Map<Integer, BigDecimal> salesQtyByFood,
            Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId) {
        Map<Integer, BigDecimal> sumU = new HashMap<>();
        Map<Integer, BigDecimal> sumQ = new HashMap<>();
        if (foodIds == null || foodIds.isEmpty()) {
            return new RecipeGoodsAgg(sumU, sumQ);
        }
        for (Integer fid : foodIds) {
            BigDecimal q = salesQtyByFood == null ? BigDecimal.ZERO : salesQtyByFood.getOrDefault(fid, BigDecimal.ZERO);
            List<GbDistributerFoodGoodsEntity> recipe =
                    recipeByFoodId == null
                            ? gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(fid)
                            : recipeByFoodId.getOrDefault(fid, Collections.emptyList());
            if (recipe == null || recipe.isEmpty()) {
                continue;
            }
            Set<Integer> salesCountedForGoods = new HashSet<>();
            for (GbDistributerFoodGoodsEntity line : recipe) {
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                Integer gId = line.getGbDfgDisGoodsId();
                if (gId == null) {
                    continue;
                }
                BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
                if (u.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                sumU.merge(gId, u, BigDecimal::add);
                if (salesCountedForGoods.add(gId)) {
                    sumQ.merge(gId, q, BigDecimal::add);
                }
                if (log.isDebugEnabled()) {
                    log.debug("[可支撑] S_g 累加 foodId={} disGoodsId={} u={} 累加后={}",
                            fid, gId, u.stripTrailingZeros().toPlainString(),
                            sumU.get(gId).stripTrailingZeros().toPlainString());
                }
            }
        }
        return new RecipeGoodsAgg(sumU, sumQ);
    }

    /**
     * 原料 g 在全报表内的「理论总耗」：对每个参与报表的菜品，用「头表实销份数 q × 本菜该料合并单份用量 dishU」再累加。
     * <p>与「各菜 theoryOutboundQtyByRecipe 里该料之和」同口径；当子表 t 与 q×u 一致时，也与 sumT_g 相等。
     * 共料出库按此作分母时：若 W_g &lt; sumNeed_g，每菜摊得斤数按自身需求占比收缩，单菜 alloc÷dishU 不会超过该菜实销 q。</p>
     */
    private Map<Integer, BigDecimal> buildSumNeedByGoods(Set<Integer> foodIds, Map<Integer, BigDecimal> salesQtyByFood) {
        return buildSumNeedByGoods(foodIds, salesQtyByFood, loadRecipesByFoodIds(foodIds));
    }

    private Map<Integer, BigDecimal> buildSumNeedByGoods(Set<Integer> foodIds, Map<Integer, BigDecimal> salesQtyByFood,
            Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId) {
        Map<Integer, BigDecimal> sumNeed = new HashMap<>();
        if (foodIds == null || foodIds.isEmpty()) {
            return sumNeed;
        }
        for (Integer fid : foodIds) {
            BigDecimal q = salesQtyByFood == null ? BigDecimal.ZERO : salesQtyByFood.getOrDefault(fid, BigDecimal.ZERO);
            List<GbDistributerFoodGoodsEntity> recipe =
                    recipeByFoodId == null
                            ? gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(fid)
                            : recipeByFoodId.getOrDefault(fid, Collections.emptyList());
            if (recipe == null || recipe.isEmpty()) {
                continue;
            }
            // 先按 disGoodsId 合并本菜该料单份用量（多行同料 u 相加），再乘 q，避免重复计行。
            Map<Integer, BigDecimal> dishUByGood = new HashMap<>();
            for (GbDistributerFoodGoodsEntity line : recipe) {
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                Integer gId = line.getGbDfgDisGoodsId();
                if (gId == null) {
                    continue;
                }
                BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
                if (u.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                dishUByGood.merge(gId, u, BigDecimal::add);
            }
            for (Map.Entry<Integer, BigDecimal> en : dishUByGood.entrySet()) {
                sumNeed.merge(en.getKey(), q.multiply(en.getValue()), BigDecimal::add);
            }
        }
        return sumNeed;
    }

    /**
     * 遍历报表内所有菜品配方，收集有效 {@code gbDfgDisGoodsId}，再查 {@code gb_distributer_goods}，
     * 供 {@code salesDishRows[].ingredientRows} 输出鲜品管控与标准重量等字段。
     */
    private Map<Integer, GbDistributerGoodsEntity> loadDisGoodsDetailByRecipeGoods(Set<Integer> foodIds) {
        return loadDisGoodsDetailByRecipeGoods(foodIds, loadRecipesByFoodIds(foodIds));
    }

    private Map<Integer, GbDistributerGoodsEntity> loadDisGoodsDetailByRecipeGoods(Set<Integer> foodIds,
            Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId) {
        Set<Integer> goodIds = new LinkedHashSet<>();
        if (foodIds != null) {
            for (Integer fid : foodIds) {
                List<GbDistributerFoodGoodsEntity> recipe =
                        recipeByFoodId == null
                                ? gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(fid)
                                : recipeByFoodId.getOrDefault(fid, Collections.emptyList());
                if (recipe == null) {
                    continue;
                }
                for (GbDistributerFoodGoodsEntity line : recipe) {
                    if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                        continue;
                    }
                    Integer gid = line.getGbDfgDisGoodsId();
                    if (gid == null) {
                        continue;
                    }
                    BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
                    if (u.compareTo(BigDecimal.ZERO) > 0) {
                        goodIds.add(gid);
                    }
                }
            }
        }
        if (goodIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, GbDistributerGoodsEntity> out = new HashMap<>();
        for (GbDistributerGoodsEntity e : gbDistributerGoodsService.listByIds(goodIds)) {
            if (e != null && e.getGbDistributerGoodsId() != null) {
                out.put(e.getGbDistributerGoodsId(), e);
            }
        }
        return out;
    }

    /** 本期有出库的配料 → 反查配方关联菜品，使无销量但有出库的料仍能生成分摊行与关联菜品。 */
    private Set<Integer> collectFoodIdsForOutboundGoods(Integer disId, Set<Integer> outboundGoodsIds) {
        Set<Integer> foodIds = new LinkedHashSet<>();
        if (disId == null || outboundGoodsIds == null || outboundGoodsIds.isEmpty()) {
            return foodIds;
        }
        for (Integer gId : outboundGoodsIds) {
            if (gId == null) {
                continue;
            }
            List<GbDistributerFoodGoodsEntity> recipeLines =
                    gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(gId, disId);
            if (recipeLines == null) {
                continue;
            }
            for (GbDistributerFoodGoodsEntity line : recipeLines) {
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                Integer foodId = line.getGbDfgFoodId();
                if (foodId != null) {
                    foodIds.add(foodId);
                }
            }
        }
        return foodIds;
    }

    private static boolean needsUnionFoodScopeReload(Set<Integer> currentFoodIds, Set<Integer> unionFoodIds) {
        if (unionFoodIds == null || unionFoodIds.isEmpty()) {
            return false;
        }
        if (currentFoodIds == null || currentFoodIds.isEmpty()) {
            return true;
        }
        for (Integer foodId : unionFoodIds) {
            if (!currentFoodIds.contains(foodId)) {
                return true;
            }
        }
        return false;
    }

    private Map<Integer, GbDistributerGoodsEntity> loadDisGoodsEntitiesByIds(Set<Integer> goodIds) {
        if (goodIds == null || goodIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, GbDistributerGoodsEntity> out = new HashMap<>();
        for (GbDistributerGoodsEntity e : gbDistributerGoodsService.listByIds(goodIds)) {
            if (e != null && e.getGbDistributerGoodsId() != null) {
                out.put(e.getGbDistributerGoodsId(), e);
            }
        }
        return out;
    }

    private Map<Integer, List<GbDistributerFoodGoodsEntity>> loadRecipesByFoodIds(Set<Integer> foodIds) {
        if (foodIds == null || foodIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("foodIds", new ArrayList<>(foodIds));
        List<GbDistributerFoodGoodsEntity> lines = gbDistributerFoodGoodsService.queryFoodGoodsByParams(params);
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<GbDistributerFoodGoodsEntity>> out = new HashMap<>();
        for (GbDistributerFoodGoodsEntity line : lines) {
            if (line == null || line.getGbDfgFoodId() == null) {
                continue;
            }
            out.computeIfAbsent(line.getGbDfgFoodId(), k -> new ArrayList<>()).add(line);
        }
        return out;
    }

    private Map<Integer, GbDistributerFoodEntity> loadFoodEntitiesByIds(Set<Integer> foodIds) {
        if (foodIds == null || foodIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, GbDistributerFoodEntity> out = new HashMap<>();
        for (GbDistributerFoodEntity row : gbDistributerFoodService.queryByIds(new ArrayList<>(foodIds))) {
            if (row != null && row.getGbDistributerFoodId() != null) {
                out.put(row.getGbDistributerFoodId(), row);
            }
        }
        return out;
    }

    /**
     * {@code outboundQty} 父行：与 {@code salesDishRows[].ingredientRows[]} 单条<strong>同键</strong>，数值为本料在全报表上的汇总；
     * {@code recipeUnitPerDish} 无单一语义，置 {@code null}；末尾附 {@code linkingDishRows}。
     */
    private static Map<String, Object> buildOutboundGoodsParentIngredientRow(Integer gId,
            String goodsName,
            GbDistributerGoodsEntity profile,
            BigDecimal wG,
            BigDecimal sG,
            BigDecimal sumTGlobal,
            BigDecimal theoryOutboundQtyByRecipeTotal,
            BigDecimal minChildSupported,
            BigDecimal linkingDishSoldPortionsTotal,
            List<Map<String, Object>> linkingDishRows) {
        BigDecimal pUnit = wG.compareTo(BigDecimal.ZERO) > 0 ? sG.divide(wG, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal recipeLineCost = pUnit.multiply(theoryOutboundQtyByRecipeTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal outboundLineCost = pUnit.multiply(wG).setScale(2, RoundingMode.HALF_UP);
        BigDecimal salesLineCost = pUnit.multiply(sumTGlobal).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("disGoodsId", gId);
        m.put("goodsName", goodsName);
        m.put("recipeUnitPerDish", null);
        m.put("theoryQtyFromSales", plainQty(sumTGlobal));
        m.put("theoryOutboundQtyByRecipe", plainQty(theoryOutboundQtyByRecipeTotal));
        m.put("outboundAllocatedQty", wG.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        m.put("supportedPortionsThisGood",
                minChildSupported.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        m.put("salesIngredientCostAmount", salesLineCost.stripTrailingZeros().toPlainString());
        m.put("recipeTheoryIngredientCostAmount", recipeLineCost.stripTrailingZeros().toPlainString());
        m.put("outboundAllocatedIngredientCostAmount", outboundLineCost.stripTrailingZeros().toPlainString());
        m.put("recipeSalesVsOutboundCostDiff",
                recipeLineCost.subtract(outboundLineCost).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        m.put("soldVsSupportedPortionDiff",
                linkingDishSoldPortionsTotal.subtract(minChildSupported).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        m.put("recipeTheoryQtyVsOutboundAllocDiff",
                theoryOutboundQtyByRecipeTotal.subtract(wG).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        putDisGoodsProfileFields(m, profile);
        m.put("linkingDishRows", linkingDishRows);
        return m;
    }

    /**
     * 把 {@code gb_distributer_goods} 上的规格与鲜品字段挂到 Map（用于 {@code salesDishRows[].ingredientRows} 与
     * {@code outboundGoodsRows[]} 商品行，字段名与 {@link GbDistributerGoodsEntity} 一致）。
     */
    private static void putDisGoodsProfileFields(Map<String, Object> row, GbDistributerGoodsEntity ge) {
        if (ge == null) {
            row.put("gbDgGoodsName", null);
            row.put("gbDgGoodsStandardname", null);
            row.put("gbDgGoodsIsWeight", null);
            row.put("gbDgControlFresh", null);
            row.put("gbDgFreshWarnHour", null);
            row.put("gbDgFreshWasteHour", null);
            row.put("gbDgGoodsStandardWeight", null);
            row.put("gbDgGoodsFileImg", null);
            return;
        }
        row.put("gbDgGoodsName", ge.getGbDgGoodsName());
        row.put("gbDgGoodsStandardname", ge.getGbDgGoodsStandardname());
        row.put("gbDgGoodsIsWeight", ge.getGbDgGoodsIsWeight());
        row.put("gbDgControlFresh", ge.getGbDgControlFresh());
        row.put("gbDgFreshWarnHour", ge.getGbDgFreshWarnHour());
        row.put("gbDgFreshWasteHour", ge.getGbDgFreshWasteHour());
        row.put("gbDgGoodsStandardWeight", ge.getGbDgGoodsStandardWeight());
        row.put("gbDgGoodsFileImg", ge.getGbDgNxFatherImg());
    }

    /** 收集 {@code outboundQty} 下「原料 g × 菜」中间量，便于先排序再输出与 salesDish 配料行同构的 Map。 */
    private static final class OutboundDishLinkAgg {
        final Integer foodId;
        final String foodName;
        final BigDecimal salesQty;
        final BigDecimal dishUForGood;
        final BigDecimal theoryFromSales;
        final BigDecimal theoryByRecipe;
        final BigDecimal allocW;
        final BigDecimal maxPortionsThisGood;

        OutboundDishLinkAgg(Integer foodId, String foodName, BigDecimal salesQty, BigDecimal dishUForGood,
                BigDecimal theoryFromSales, BigDecimal theoryByRecipe, BigDecimal allocW, BigDecimal maxPortionsThisGood) {
            this.foodId = foodId;
            this.foodName = foodName;
            this.salesQty = salesQty;
            this.dishUForGood = dishUForGood;
            this.theoryFromSales = theoryFromSales;
            this.theoryByRecipe = theoryByRecipe;
            this.allocW = allocW;
            this.maxPortionsThisGood = maxPortionsThisGood;
        }
    }

    /**
     * {@code outboundQty} 主表：本期有出库的 {@code disGoodsId}。
     * <p>父行：与 {@code salesDishRows[].ingredientRows} 同键（配料商品汇总）；{@code linkingDishRows}：与 {@code salesDishRows[]} 整行同结构（菜品）。</p>
     */
    private List<Map<String, Object>> buildOutboundGoodsRows(Set<Integer> allFoodIds,
            Map<Integer, BigDecimal> reduceW,
            Map<Integer, BigDecimal> reduceS,
            Map<Integer, BigDecimal> sumRecipeUnitByGoods,
            Map<Integer, BigDecimal> sumSalesQtyByGoods,
            Map<Integer, BigDecimal> sumNeedByGoods,
            Map<Integer, BigDecimal> salesQtyByFood,
            Map<Integer, Map<Integer, BigDecimal>> theoryWtByFoodAndGoods,
            Map<Integer, BigDecimal> sumTheoryByGoods,
            Map<Integer, GbDistributerGoodsEntity> disGoodsById) {
        if (allFoodIds == null || allFoodIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, GbDistributerGoodsEntity> goodsDetail = disGoodsById == null ? Collections.emptyMap() : disGoodsById;
        Map<Integer, Map<String, Object>> fullDishRowByFoodId = new LinkedHashMap<>();
        for (Integer fid : allFoodIds) {
            fullDishRowByFoodId.put(fid, buildSalesDishRow(fid, theoryWtByFoodAndGoods.getOrDefault(fid, Collections.emptyMap()),
                    sumTheoryByGoods, sumRecipeUnitByGoods, sumSalesQtyByGoods, sumNeedByGoods, goodsDetail, reduceW, reduceS,
                    salesQtyByFood == null ? BigDecimal.ZERO : salesQtyByFood.getOrDefault(fid, BigDecimal.ZERO)));
        }
        Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFood = new HashMap<>();
        for (Integer fid : allFoodIds) {
            List<GbDistributerFoodGoodsEntity> rec = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(fid);
            recipeByFood.put(fid, rec == null ? Collections.emptyList() : rec);
        }
        Map<Integer, Set<Integer>> foodIdsByGood = new HashMap<>();
        Map<Integer, String> goodsNameById = new HashMap<>();
        for (Integer fid : allFoodIds) {
            for (GbDistributerFoodGoodsEntity line : recipeByFood.get(fid)) {
                if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                    continue;
                }
                Integer gId = line.getGbDfgDisGoodsId();
                if (gId == null) {
                    continue;
                }
                BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
                if (u.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                foodIdsByGood.computeIfAbsent(gId, k -> new LinkedHashSet<>()).add(fid);
                if (!goodsNameById.containsKey(gId)) {
                    String nm = line.getGbDfgGoodsName();
                    if (nm != null && !nm.trim().isEmpty()) {
                        goodsNameById.put(gId, nm.trim());
                    }
                }
            }
        }
        List<Integer> goodsWithOutbound = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : reduceW.entrySet()) {
            if (e.getKey() != null && nz(e.getValue()).compareTo(BigDecimal.ZERO) > 0) {
                goodsWithOutbound.add(e.getKey());
            }
        }
        goodsWithOutbound.sort(Comparator.comparing((Integer g) -> nz(reduceW.get(g))).reversed());

        // 本报表「按出库商品」主表行：每个 disGoodsId 查一次主档，挂上鲜品/规格字段（与 salesDish 配料行一致）
        Map<Integer, GbDistributerGoodsEntity> outboundGoodsProfileById = new HashMap<>();
        for (Integer gid : goodsWithOutbound) {
            GbDistributerGoodsEntity e = gbDistributerGoodsService.queryObject(gid);
            if (e != null) {
                outboundGoodsProfileById.put(gid, e);
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Integer gId : goodsWithOutbound) {
            BigDecimal wG = nz(reduceW.get(gId));
            BigDecimal recipeUnitSum = nz(sumRecipeUnitByGoods.get(gId));
            BigDecimal salesSumForGood = nz(sumSalesQtyByGoods.get(gId));

            Set<Integer> fids = foodIdsByGood.get(gId);
            BigDecimal sG = nz(reduceS.get(gId));
            List<OutboundDishLinkAgg> linkAggs = new ArrayList<>();
            if (fids != null) {
                for (Integer fid : fids) {
                    List<GbDistributerFoodGoodsEntity> recipe = recipeByFood.get(fid);
                    BigDecimal dishUForGood = sumRecipeUnitForGoodOnDish(recipe, gId);
                    BigDecimal salesQty = salesQtyByFood == null ? BigDecimal.ZERO : salesQtyByFood.getOrDefault(fid, BigDecimal.ZERO);
                    BigDecimal theoryByRecipe = salesQty.multiply(dishUForGood);
                    BigDecimal theoryFromSales = BigDecimal.ZERO;
                    if (theoryWtByFoodAndGoods != null) {
                        theoryFromSales = nz(theoryWtByFoodAndGoods.getOrDefault(fid, Collections.emptyMap()).get(gId));
                    }

                    GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(fid);
                    String foodName = food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "";

                    BigDecimal sumT = nz(sumTheoryByGoods == null ? null : sumTheoryByGoods.get(gId));
                    BigDecimal needThis = salesQty.multiply(dishUForGood);
                    BigDecimal sumNeed = nz(sumNeedByGoods == null ? null : sumNeedByGoods.get(gId));
                    String allocTag = "outboundQtyLink parentDisGoodsId=" + gId + " foodId=" + fid + " foodName=" + foodName;
                    BigDecimal allocW = allocateOutboundWeightForDishGood(wG, theoryFromSales, sumT, salesQty,
                            salesSumForGood, dishUForGood, recipeUnitSum, needThis, sumNeed, allocTag);
                    BigDecimal maxPortionsThisGood = dishUForGood.compareTo(BigDecimal.ZERO) > 0
                            ? allocW.divide(dishUForGood, 8, RoundingMode.HALF_UP)
                            : minSellablePortionsForDishOnGood(recipe, gId, wG, salesQty, recipeUnitSum, salesSumForGood);

                    linkAggs.add(new OutboundDishLinkAgg(fid, foodName, salesQty, dishUForGood, theoryFromSales, theoryByRecipe,
                            allocW, maxPortionsThisGood));
                }
                linkAggs.sort(Comparator.comparing((OutboundDishLinkAgg a) -> a.salesQty).reversed());
            }

            BigDecimal theoryOutboundQtyByRecipeTotal = BigDecimal.ZERO;
            BigDecimal linkingDishSoldPortionsTotal = BigDecimal.ZERO;
            for (OutboundDishLinkAgg a : linkAggs) {
                theoryOutboundQtyByRecipeTotal = theoryOutboundQtyByRecipeTotal.add(a.theoryByRecipe);
                linkingDishSoldPortionsTotal = linkingDishSoldPortionsTotal.add(a.salesQty);
            }
            BigDecimal sumTGlobal = nz(sumTheoryByGoods == null ? null : sumTheoryByGoods.get(gId));

            BigDecimal minChildSupported = null;
            for (OutboundDishLinkAgg a : linkAggs) {
                minChildSupported = minChildSupported == null
                        ? a.maxPortionsThisGood
                        : minChildSupported.min(a.maxPortionsThisGood);
            }
            if (minChildSupported == null) {
                minChildSupported = BigDecimal.ZERO;
            }

            String goodsName = goodsNameById.getOrDefault(gId, "");
            GbDistributerGoodsEntity goodProfile = outboundGoodsProfileById.get(gId);

            List<Map<String, Object>> linkingDishRows = new ArrayList<>();
            for (OutboundDishLinkAgg a : linkAggs) {
                Map<String, Object> dishRow = fullDishRowByFoodId.get(a.foodId);
                if (dishRow != null) {
                    linkingDishRows.add(dishRow);
                }
            }

            groups.add(buildOutboundGoodsParentIngredientRow(gId, goodsName, goodProfile, wG, sG, sumTGlobal,
                    theoryOutboundQtyByRecipeTotal, minChildSupported, linkingDishSoldPortionsTotal, linkingDishRows));
        }
        return groups;
    }

    private static BigDecimal sumRecipeUnitForGoodOnDish(List<GbDistributerFoodGoodsEntity> recipe, Integer gId) {
        if (recipe == null || gId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            if (!gId.equals(line.getGbDfgDisGoodsId())) {
                continue;
            }
            BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
            if (u.compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(u);
            }
        }
        return sum;
    }

    /**
     * 把本期该料总出库重量 {@code W_g} 摊到「本菜、本料」上的出库量（斤等，与生产成本扣库一致）。
     * <p><b>主口径（老板短料逻辑）</b>：用全报表该料「理论总耗」{@code sumNeed_g = Σ_菜(q×dishU)} 作分母，
     * 本菜需求 {@code needThis = q_本菜 × dishU_本菜} 作分子：{@code alloc = W_g × needThis / sumNeed_g}。
     * 当 {@code W_g < sumNeed_g} 时，每菜摊得斤数按各自配方需求等比压缩，故 {@code alloc ÷ dishU ≤ q}，
     * 不会再出现「按销量份数比分出库却反推可卖份数大于实销」的反常现象。</p>
     * <p>降级顺序：</p>
     * <ol>
     *   <li>{@code sumNeed_g > 0}：按理论消耗占比分摊（上式）。</li>
     *   <li>否则若 {@code Q_g > 0}：{@code W_g × q / Q_g}（只有份数、没有可靠配方总耗时退化为纯销量比）。</li>
     *   <li>否则若 {@code sumT_g > 0}：{@code W_g × t / sumT_g}（销售子表用量结构）。</li>
     *   <li>否则若 {@code S_g > 0}：{@code W_g × u / S_g}（仅配方单份用量在全报表占比）。</li>
     * </ol>
     *
     * @param needThis 本菜该料理论需求 {@code q×dishU}（与 ingredient 行 theoryOutboundQtyByRecipe 同源）
     * @param sumNeed    全报表该料 {@code Σ(q×dishU)}，与 {@link #buildSumNeedByGoods} 一致
     * @param traceTag   非空且 INFO 时输出 {@code [dishCostAlloc]} 一行
     */
    private static BigDecimal allocateOutboundWeightForDishGood(BigDecimal wG,
            BigDecimal t,
            BigDecimal sumT,
            BigDecimal salesQty,
            BigDecimal salesSumForGood,
            BigDecimal dishU,
            BigDecimal recipeUnitSum,
            BigDecimal needThis,
            BigDecimal sumNeed,
            String traceTag) {
        wG = nz(wG);
        BigDecimal alloc;
        String branch;
        if (sumNeed.compareTo(BigDecimal.ZERO) > 0 && wG.compareTo(BigDecimal.ZERO) > 0) {
            alloc = wG.multiply(nz(needThis)).divide(sumNeed, 8, RoundingMode.HALF_UP);
            branch = "1_N_W*need_div_sumNeed";
        } else if (salesSumForGood.compareTo(BigDecimal.ZERO) > 0 && wG.compareTo(BigDecimal.ZERO) > 0) {
            alloc = wG.multiply(nz(salesQty)).divide(salesSumForGood, 8, RoundingMode.HALF_UP);
            branch = "2_Q_W*q_div_Qg";
        } else if (sumT.compareTo(BigDecimal.ZERO) > 0 && wG.compareTo(BigDecimal.ZERO) > 0) {
            alloc = wG.multiply(nz(t)).divide(sumT, 8, RoundingMode.HALF_UP);
            branch = "3_T_W*t_div_sumTg";
        } else if (recipeUnitSum.compareTo(BigDecimal.ZERO) > 0 && wG.compareTo(BigDecimal.ZERO) > 0) {
            alloc = wG.multiply(nz(dishU)).divide(recipeUnitSum, 8, RoundingMode.HALF_UP);
            branch = "4_S_W*u_div_Sg";
        } else {
            alloc = BigDecimal.ZERO;
            branch = "0_none";
        }
        if (traceTag != null && !traceTag.isEmpty() && log.isInfoEnabled()) {
            log.info("[dishCostAlloc] tag={} branch={} wG={} needThis={} sumNeed_g={} t={} sumT_g={} q={} Q_g={} dishU={} S_g={} => allocW={}",
                    traceTag,
                    branch,
                    plainQty(wG),
                    plainQty(needThis),
                    plainQty(sumNeed),
                    plainQty(t),
                    plainQty(sumT),
                    plainQty(salesQty),
                    plainQty(salesSumForGood),
                    plainQty(dishU),
                    plainQty(recipeUnitSum),
                    plainQty(alloc));
        }
        return alloc;
    }

    /**
     * 仅站在原料 g 上：在「无合并 dishU」等退化场景下估算可支撑份数（如出库按旧逻辑、或 dishU=0）。
     * <p>正常路径下配料行已改为「摊销斤数 allocW ÷ 合并单份用量 dishU」，与 {@link #allocateOutboundWeightForDishGood}
     * 的 sumNeed 主口径一致，不再依赖本方法。</p>
     */
    private static BigDecimal minSellablePortionsForDishOnGood(List<GbDistributerFoodGoodsEntity> recipe,
            Integer gId,
            BigDecimal wG,
            BigDecimal salesQty,
            BigDecimal recipeUnitSum,
            BigDecimal salesSumForGood) {
        if (recipe == null || gId == null || wG.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal min = null;
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            if (!gId.equals(line.getGbDfgDisGoodsId())) {
                continue;
            }
            BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
            if (u.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal sellable = BigDecimal.ZERO;
            if (salesSumForGood.compareTo(BigDecimal.ZERO) > 0) {
                sellable = wG.multiply(salesQty)
                        .divide(salesSumForGood.multiply(u), 8, RoundingMode.HALF_UP);
            } else if (recipeUnitSum.compareTo(BigDecimal.ZERO) > 0) {
                sellable = wG.divide(recipeUnitSum, 8, RoundingMode.HALF_UP);
            }
            min = min == null ? sellable : min.min(sellable);
        }
        return min == null ? BigDecimal.ZERO : min;
    }

    private static final class MergeRecipeU {
        BigDecimal sumU = BigDecimal.ZERO;
        String goodsName = "";
    }

    /**
     * {@code salesDish}：一行菜 + {@code ingredientRows} 配料（按配方首次出现顺序合并同料）+ {@code bottle} 瓶颈汇总。
     * <p>{@code outboundAllocatedQtyTotal}：对本菜在 {@code theoryByGoods} 中出现的各料，将 {@link #allocateOutboundWeightForDishGood}
     * 结果按料相加；与配料行同用「先 sumNeed 理论总耗占比，再 Q、T、S」降级链。</p>
     */
    private Map<String, Object> buildSalesDishRow(Integer foodId,
            Map<Integer, BigDecimal> theoryByGoods,
            Map<Integer, BigDecimal> sumTheoryByGoods,
            Map<Integer, BigDecimal> sumRecipeUnitByGoods,
            Map<Integer, BigDecimal> sumSalesQtyByGoods,
            Map<Integer, BigDecimal> sumNeedByGoods,
            Map<Integer, GbDistributerGoodsEntity> disGoodsById,
            Map<Integer, BigDecimal> reduceW,
            Map<Integer, BigDecimal> reduceS,
            BigDecimal salesQty) {
        return buildSalesDishRow(foodId, theoryByGoods, sumTheoryByGoods, sumRecipeUnitByGoods, sumSalesQtyByGoods,
                sumNeedByGoods, disGoodsById, reduceW, reduceS, salesQty, null);
    }

    private Map<String, Object> buildSalesDishRow(Integer foodId,
            Map<Integer, BigDecimal> theoryByGoods,
            Map<Integer, BigDecimal> sumTheoryByGoods,
            Map<Integer, BigDecimal> sumRecipeUnitByGoods,
            Map<Integer, BigDecimal> sumSalesQtyByGoods,
            Map<Integer, BigDecimal> sumNeedByGoods,
            Map<Integer, GbDistributerGoodsEntity> disGoodsById,
            Map<Integer, BigDecimal> reduceW,
            Map<Integer, BigDecimal> reduceS,
            BigDecimal salesQty,
            Map<Integer, List<GbDistributerFoodGoodsEntity>> recipeByFoodId) {

        GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(foodId);
        String foodName = food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "";

        List<GbDistributerFoodGoodsEntity> recipe =
                recipeByFoodId == null
                        ? gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId)
                        : recipeByFoodId.getOrDefault(foodId, Collections.emptyList());
        if (recipe == null) {
            recipe = Collections.emptyList();
        }

        BigDecimal actualWeightTotal = BigDecimal.ZERO;
        BigDecimal theoryCostTotal = BigDecimal.ZERO;
        BigDecimal actualCostTotal = BigDecimal.ZERO;
        BigDecimal sumAbsDiffMoney = BigDecimal.ZERO;
        // actualInboundQtyFromSales：销售子表各料用量 t 之和（与 ingredientRows.theoryQtyFromSales 逐料加总一致）；不是出库分摊。
        BigDecimal actualInboundQtyFromSales = BigDecimal.ZERO;

        for (Map.Entry<Integer, BigDecimal> e : theoryByGoods.entrySet()) {
            Integer gId = e.getKey();
            BigDecimal t = e.getValue();
            actualInboundQtyFromSales = actualInboundQtyFromSales.add(nz(t));
            BigDecimal wG = nz(reduceW.get(gId));
            BigDecimal sG = nz(reduceS.get(gId));
            BigDecimal sumT = nz(sumTheoryByGoods.get(gId));
            BigDecimal dishUForG = sumRecipeUnitForGoodOnDish(recipe, gId);
            BigDecimal salesSumForGood = nz(sumSalesQtyByGoods.get(gId));
            BigDecimal recipeUnitSum = nz(sumRecipeUnitByGoods.get(gId));
            BigDecimal needThis = salesQty.multiply(dishUForG);
            BigDecimal sumNeed = nz(sumNeedByGoods == null ? null : sumNeedByGoods.get(gId));

            BigDecimal p = BigDecimal.ZERO;
            if (wG.compareTo(BigDecimal.ZERO) > 0) {
                p = sG.divide(wG, 8, RoundingMode.HALF_UP);
            }
            String tagCost = "salesDishCostLoop foodId=" + foodId + " foodName=" + foodName + " disGoodsId=" + gId;
            BigDecimal allocW = allocateOutboundWeightForDishGood(wG, t, sumT, salesQty, salesSumForGood, dishUForG,
                    recipeUnitSum, needThis, sumNeed, tagCost);
            actualWeightTotal = actualWeightTotal.add(allocW);

            BigDecimal theoC = p.multiply(t).setScale(4, RoundingMode.HALF_UP);
            BigDecimal actC = p.multiply(allocW).setScale(4, RoundingMode.HALF_UP);
            theoryCostTotal = theoryCostTotal.add(theoC);
            actualCostTotal = actualCostTotal.add(actC);
            sumAbsDiffMoney = sumAbsDiffMoney.add(theoC.subtract(actC).abs());
        }
        // 理论用量合计（展示）：按 gb_distributer_food_goods 单份用量 × 本期实销份数，逐行相加（不同单位数值相加为经营粗算口径）
        BigDecimal theoryInboundQtyByRecipe = BigDecimal.ZERO;
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            BigDecimal uLine = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
            if (uLine.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            theoryInboundQtyByRecipe = theoryInboundQtyByRecipe.add(salesQty.multiply(uLine));
        }

        LinkedHashMap<Integer, MergeRecipeU> mergedByGood = new LinkedHashMap<>();
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            Integer gId = line.getGbDfgDisGoodsId();
            if (gId == null) {
                continue;
            }
            BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
            if (u.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            MergeRecipeU mu = mergedByGood.computeIfAbsent(gId, k -> new MergeRecipeU());
            mu.sumU = mu.sumU.add(u);
            if (mu.goodsName.isEmpty()) {
                String nm = line.getGbDfgGoodsName();
                if (nm != null && !nm.trim().isEmpty()) {
                    mu.goodsName = nm.trim();
                }
            }
        }

        List<Map<String, Object>> ingredientRows = new ArrayList<>();
        for (Map.Entry<Integer, MergeRecipeU> en : mergedByGood.entrySet()) {
            Integer gId = en.getKey();
            MergeRecipeU mu = en.getValue();
            BigDecimal t = nz(theoryByGoods.get(gId));
            BigDecimal wG = nz(reduceW.get(gId));
            BigDecimal recipeUnitSum = nz(sumRecipeUnitByGoods.get(gId));
            BigDecimal salesSumForGood = nz(sumSalesQtyByGoods.get(gId));
            BigDecimal dishU = mu.sumU;
            BigDecimal sumT = nz(sumTheoryByGoods.get(gId));
            BigDecimal needThis = salesQty.multiply(dishU);
            BigDecimal sumNeed = nz(sumNeedByGoods == null ? null : sumNeedByGoods.get(gId));
            String tagIng = "salesDishIngredient foodId=" + foodId + " foodName=" + foodName + " disGoodsId=" + gId
                    + " goodsName=" + mu.goodsName;
            BigDecimal allocW = allocateOutboundWeightForDishGood(wG, t, sumT, salesQty, salesSumForGood, dishU,
                    recipeUnitSum, needThis, sumNeed, tagIng);
            // 合并后的本菜该料单份用量 dishU：摊销斤数 ÷ dishU = 仅站在该料上、出库能「顶住」的份数（sumNeed 分摊时整体缺料则必 ≤ 实销 q）
            BigDecimal minPortions = dishU.compareTo(BigDecimal.ZERO) > 0
                    ? allocW.divide(dishU, 8, RoundingMode.HALF_UP)
                    : minSellablePortionsForDishOnGood(recipe, gId, wG, salesQty, recipeUnitSum, salesSumForGood);
            BigDecimal theoryByRecipe = salesQty.multiply(dishU);
            Map<String, Object> ir = new LinkedHashMap<>();
            ir.put("disGoodsId", gId);
            ir.put("goodsName", mu.goodsName);
            ir.put("recipeUnitPerDish", plainQty(dishU));
            ir.put("theoryQtyFromSales", plainQty(t));
            ir.put("theoryOutboundQtyByRecipe", plainQty(theoryByRecipe));
            // 配料行摊销斤数：与 salesDishRows[].bottle.outboundAllocatedQty 一致保留 2 位小数
            ir.put("outboundAllocatedQty",
                    nz(allocW).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            ir.put("supportedPortionsThisGood", minPortions.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            BigDecimal sG = nz(reduceS.get(gId));
            BigDecimal pUnit = BigDecimal.ZERO;
            if (wG.compareTo(BigDecimal.ZERO) > 0) {
                pUnit = sG.divide(wG, 8, RoundingMode.HALF_UP);
            }
            ir.put("salesIngredientCostAmount",
                    pUnit.multiply(nz(t)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            ir.put("recipeTheoryIngredientCostAmount",
                    pUnit.multiply(nz(theoryByRecipe)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            ir.put("outboundAllocatedIngredientCostAmount",
                    pUnit.multiply(nz(allocW)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            BigDecimal recipeLineCost = pUnit.multiply(nz(theoryByRecipe));
            BigDecimal outboundLineCost = pUnit.multiply(nz(allocW));
            ir.put("recipeSalesVsOutboundCostDiff",
                    recipeLineCost.subtract(outboundLineCost).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            ir.put("soldVsSupportedPortionDiff",
                    salesQty.subtract(minPortions).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            ir.put("recipeTheoryQtyVsOutboundAllocDiff", theoryByRecipe.subtract(allocW).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
//            ir.put("recipeTheoryQtyVsOutboundAllocDiff", plainQty(theoryByRecipe.subtract(allocW)));
            // 分销商商品主档：鲜度管控、预警/报废时长、标准重量（无主档记录时四键为 null）
            putDisGoodsProfileFields(ir, disGoodsById == null ? null : disGoodsById.get(gId));
            ingredientRows.add(ir);
        }

        Map<Integer, BigDecimal> dishSumUByGoods = new HashMap<>();
        for (Map.Entry<Integer, MergeRecipeU> en : mergedByGood.entrySet()) {
            dishSumUByGoods.put(en.getKey(), en.getValue().sumU);
        }

        BigDecimal maxSell = null;
        Integer bottleneckGoodsId = null;
        BigDecimal bottleneckAllocatedWeight = null;
        BigDecimal dishMarginalCost = BigDecimal.ZERO;
        if (log.isInfoEnabled()) {
            log.info("[salesDish] foodId={} name={} soldPortions={}", foodId, foodName, salesQty.stripTrailingZeros().toPlainString());
        }
        for (GbDistributerFoodGoodsEntity line : recipe) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            Integer gId = line.getGbDfgDisGoodsId();
            if (gId == null) {
                continue;
            }
            BigDecimal u = GbDepartmentGoodsStockReduceSupport.coerceDecimal(line.getGbDfgGoodsAmount());
            if (u.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal wG = nz(reduceW.get(gId));
            if (wG.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal sG = nz(reduceS.get(gId));
            BigDecimal pG = sG.divide(wG, 8, RoundingMode.HALF_UP);
            dishMarginalCost = dishMarginalCost.add(pG.multiply(u));

            BigDecimal recipeUnitSum = nz(sumRecipeUnitByGoods.get(gId));
            BigDecimal salesSumForGood = nz(sumSalesQtyByGoods.get(gId));
            BigDecimal dishUForGood = nz(dishSumUByGoods.get(gId));
            BigDecimal tForGood = nz(theoryByGoods.get(gId));
            BigDecimal sumTForGood = nz(sumTheoryByGoods.get(gId));
            BigDecimal needThisB = salesQty.multiply(dishUForGood);
            BigDecimal sumNeedB = nz(sumNeedByGoods == null ? null : sumNeedByGoods.get(gId));
            String tagBottle = "salesDishBottleneck foodId=" + foodId + " foodName=" + foodName + " disGoodsId=" + gId
                    + " recipeLineU=" + u.stripTrailingZeros().toPlainString();
            BigDecimal allocWForGood = allocateOutboundWeightForDishGood(wG, tForGood, sumTForGood, salesQty,
                    salesSumForGood, dishUForGood, recipeUnitSum, needThisB, sumNeedB, tagBottle);
            // 整菜瓶颈：同一原料多行配方时，仍以「合并 dishU」为整料可支撑份数（与同料 ingredient 行一致）
            BigDecimal sellable;
            if (dishUForGood.compareTo(BigDecimal.ZERO) > 0) {
                sellable = allocWForGood.divide(dishUForGood, 8, RoundingMode.HALF_UP);
            } else if (salesSumForGood.compareTo(BigDecimal.ZERO) > 0) {
                sellable = allocWForGood.divide(u, 8, RoundingMode.HALF_UP);
            } else if (recipeUnitSum.compareTo(BigDecimal.ZERO) > 0) {
                sellable = wG.divide(recipeUnitSum, 8, RoundingMode.HALF_UP);
            } else {
                sellable = BigDecimal.ZERO;
            }
            if (maxSell == null || sellable.compareTo(maxSell) < 0) {
                maxSell = sellable;
                bottleneckGoodsId = gId;
                bottleneckAllocatedWeight = allocWForGood;
            }
        }
        if (maxSell == null) {
            maxSell = BigDecimal.ZERO;
        }

        BigDecimal diffCost = actualCostTotal.subtract(theoryCostTotal);
        BigDecimal portionDiff = salesQty.subtract(maxSell).abs();
        BigDecimal sortKeyVal = portionDiff.multiply(dishMarginalCost).setScale(4, RoundingMode.HALF_UP);
        if (dishMarginalCost.compareTo(BigDecimal.ZERO) <= 0) {
            sortKeyVal = sumAbsDiffMoney.setScale(4, RoundingMode.HALF_UP);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", foodId);
        row.put("foodName", foodName);
        String soldPortionsStr = ingredientSalesCountString(salesQty);
        row.put("soldPortions", soldPortionsStr);
        row.put("theoryInboundQtyTotal", plainQty(theoryInboundQtyByRecipe));
        row.put("actualInboundQtyTotal", plainQty(actualInboundQtyFromSales));
        // outboundAllocatedQtyTotal：本菜在 theoryByGoods 中出现的各料，按 sumNeed 主口径摊得的出库斤数之和（短料时各料 alloc÷dishU ≤ 本菜实销）。
//        row.put("outboundAllocatedQtyTotal", plainQty(actualWeightTotal));
        row.put("outboundAllocatedQtyTotal", actualWeightTotal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        row.put("theoryCostAmount", theoryCostTotal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        row.put("actualCostAmount", actualCostTotal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        row.put("diffCostAmount", diffCost.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        row.put("absDiffCostAmountSum", sumAbsDiffMoney.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        Map<String, Object> bottle = new LinkedHashMap<>();
        bottle.put("soldPortions", soldPortionsStr);
        bottle.put("supportedPortions", maxSell.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        bottle.put("disGoodsId", bottleneckGoodsId);
        if (bottleneckGoodsId != null) {
            MergeRecipeU muB = mergedByGood.get(bottleneckGoodsId);
            String bnName = muB != null && muB.goodsName != null && !muB.goodsName.isEmpty() ? muB.goodsName : null;
            bottle.put("goodsName", bnName);
            bottle.put("theoryQtyFromSales", plainQty(nz(theoryByGoods.get(bottleneckGoodsId))));
            BigDecimal dishUB = muB != null ? muB.sumU : nz(dishSumUByGoods.get(bottleneckGoodsId));
            bottle.put("theoryOutboundQtyByRecipe", plainQty(salesQty.multiply(nz(dishUB))));
        } else {
            bottle.put("goodsName", null);
            bottle.put("theoryQtyFromSales", null);
            bottle.put("theoryOutboundQtyByRecipe", null);
        }
        if (bottleneckGoodsId != null) {
            Integer bid = bottleneckGoodsId;
            BigDecimal wB = nz(reduceW.get(bid));
            BigDecimal sB = nz(reduceS.get(bid));
            BigDecimal pB = BigDecimal.ZERO;
            if (wB.compareTo(BigDecimal.ZERO) > 0) {
                pB = sB.divide(wB, 8, RoundingMode.HALF_UP);
            }
            BigDecimal tB = nz(theoryByGoods.get(bid));
            BigDecimal dishUBottle = nz(dishSumUByGoods.get(bid));
            BigDecimal needBottle = salesQty.multiply(dishUBottle);
            BigDecimal allocBottle = nz(bottleneckAllocatedWeight);
            BigDecimal theoryBottleCost = pB.multiply(tB).setScale(2, RoundingMode.HALF_UP);
            BigDecimal recipeBottleCost = pB.multiply(needBottle).setScale(2, RoundingMode.HALF_UP);
            BigDecimal outboundBottleCost = pB.multiply(allocBottle).setScale(2, RoundingMode.HALF_UP);
            bottle.put("theorySalesCostAmount", theoryBottleCost.stripTrailingZeros().toPlainString());
            bottle.put("recipeSalesCostAmount", recipeBottleCost.stripTrailingZeros().toPlainString());
            bottle.put("outboundAllocatedCostAmount", outboundBottleCost.stripTrailingZeros().toPlainString());
            bottle.put("recipeSalesVsOutboundCostDiff",
                    recipeBottleCost.subtract(outboundBottleCost).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            bottle.put("theoryQtyFromSalesVsOutboundAllocDiff", tB.subtract(allocBottle).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            bottle.put("recipeTheoryQtyVsOutboundAllocDiff", needBottle.subtract(allocBottle).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        } else {
            bottle.put("theorySalesCostAmount", null);
            bottle.put("recipeSalesCostAmount", null);
            bottle.put("outboundAllocatedCostAmount", null);
            bottle.put("recipeSalesVsOutboundCostDiff", null);
            bottle.put("theoryQtyFromSalesVsOutboundAllocDiff", null);
            bottle.put("recipeTheoryQtyVsOutboundAllocDiff", null);
        }
        bottle.put("soldVsSupportedPortionDiff",
                salesQty.subtract(maxSell).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        if (bottleneckGoodsId != null && bottleneckAllocatedWeight != null) {
            bottle.put("outboundAllocatedQty",
                    nz(bottleneckAllocatedWeight).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            putDisGoodsProfileFields(bottle, disGoodsById == null ? null : disGoodsById.get(bottleneckGoodsId));
        } else {
            bottle.put("outboundAllocatedQty", null);
            putDisGoodsProfileFields(bottle, null);
        }
        row.put("bottle", bottle);
        row.put("sortKey", sortKeyVal.stripTrailingZeros().toPlainString());
        row.put("hint", hintProduceReduceVsSales(salesQty, maxSell));
        row.put("ingredientRows", ingredientRows);
        return row;
    }

    /** type=1 生产出库可支撑份数 vs 实销（不引用采购量、不推算库存；不含损耗/损失扣库）。 */
    private static String hintProduceReduceVsSales(BigDecimal salesQty, BigDecimal maxSell) {
        if (maxSell.compareTo(BigDecimal.ZERO) <= 0 && salesQty.compareTo(BigDecimal.ZERO) > 0) {
            return "按本期实销占比分摊共料出库后，可支撑份数仍不足实销，请核对共料菜品是否齐全、未出库库存或其它部门调拨。";
        }
        if (salesQty.compareTo(BigDecimal.ZERO) <= 0 && maxSell.compareTo(BigDecimal.ZERO) > 0) {
            return "有出库可支撑空间但销售较少，关注动销或销售录入。";
        }
        if (maxSell.compareTo(salesQty) > 0 && maxSell.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal r = salesQty.divide(maxSell, 4, RoundingMode.HALF_UP);
            if (r.compareTo(new BigDecimal("0.75")) < 0) {
                return "相对出库可支撑份数，实销偏低，出库与销售节奏可能不一致。";
            }
        }
        if (salesQty.subtract(maxSell).compareTo(new BigDecimal("0.5")) > 0) {
            return "实销高于 type=1 生产出库可支撑份数，可能配方用量偏小、销售录入偏大，或其它来源原料未记入生产扣库（损耗/损失另计）。";
        }
        return "出库可支撑份数与实销大致匹配，可结合用量模式查看理论消耗偏差。";
    }

    /**
     * {@code queryGroupDepsByDisId} 返回的是「分组父部门」，真实门店/后厨等在 {@code gbDepartmentEntityList} 子部门中，
     * 销售表 {@code gb_dep_food_sales.gb_dfs_department_id} 对应子部门 id，不能只用父行的 fatherId 过滤。
     */
    private List<Integer> resolveScopeDepIds(Integer disId, String searchDepId, Integer depFatherId,
            Collection<Integer> scopeDepartmentIdsAllowFilter) {
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            return applyScopeDepartmentAllowFilter(Collections.singletonList(Integer.valueOf(searchDepId)),
                    scopeDepartmentIdsAllowFilter);
        }
        if (!AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId) && depFatherId != null) {
            LinkedHashMap<Integer, Boolean> singleStoreUniq = new LinkedHashMap<>();
            List<GbDepartmentEntity> subs = gbDepartmentService.querySubDepartments(depFatherId);
            if (subs != null) {
                for (GbDepartmentEntity sub : subs) {
                    if (sub.getGbDepartmentId() != null) {
                        singleStoreUniq.put(sub.getGbDepartmentId(), Boolean.TRUE);
                    }
                }
            }
            if (scopeDepartmentIdsAllowFilter != null) {
                for (Integer allowed : scopeDepartmentIdsAllowFilter) {
                    if (allowed != null && allowed.equals(depFatherId)) {
                        singleStoreUniq.put(depFatherId, Boolean.TRUE);
                        break;
                    }
                }
            }
            return applyScopeDepartmentAllowFilter(new ArrayList<>(singleStoreUniq.keySet()),
                    scopeDepartmentIdsAllowFilter);
        }
        Map<String, Object> q = new HashMap<>();
        q.put("disId", disId);
        q.put("depType", getGbDepartmentTypeMendian());
        List<GbDepartmentEntity> groupDeps = gbDepartmentService.queryGroupDepsByDisId(q);
        if (groupDeps == null || groupDeps.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<Integer, Boolean> uniq = new LinkedHashMap<>();
        for (GbDepartmentEntity group : groupDeps) {
            List<GbDepartmentEntity> subs = group.getGbDepartmentEntityList();
            if (subs == null || subs.isEmpty()) {
                continue;
            }
            for (GbDepartmentEntity sub : subs) {
                if (sub.getGbDepartmentId() == null) {
                    continue;
                }
                if (!AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId)
                        && depFatherId != null
                        && !depFatherId.equals(sub.getGbDepartmentFatherId())) {
                    continue;
                }
                uniq.put(sub.getGbDepartmentId(), Boolean.TRUE);
            }
        }
        if (!AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId)
                && depFatherId != null
                && scopeDepartmentIdsAllowFilter != null) {
            for (Integer allowed : scopeDepartmentIdsAllowFilter) {
                if (allowed != null && allowed.equals(depFatherId)) {
                    uniq.put(depFatherId, Boolean.TRUE);
                    break;
                }
            }
        }
        return applyScopeDepartmentAllowFilter(new ArrayList<>(uniq.keySet()), scopeDepartmentIdsAllowFilter);
    }

    private static List<Integer> applyScopeDepartmentAllowFilter(List<Integer> scopeDeptIds,
            Collection<Integer> allowFilter) {
        if (scopeDeptIds == null || scopeDeptIds.isEmpty()) {
            return scopeDeptIds == null ? Collections.emptyList() : scopeDeptIds;
        }
        if (allowFilter == null || allowFilter.isEmpty()) {
            return scopeDeptIds;
        }
        Set<Integer> allow = new HashSet<>();
        for (Integer id : allowFilter) {
            if (id != null) {
                allow.add(id);
            }
        }
        if (allow.isEmpty()) {
            return scopeDeptIds;
        }
        List<Integer> out = new ArrayList<>();
        for (Integer id : scopeDeptIds) {
            if (id != null && allow.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private List<GbDepFoodSalesEntity> loadFoodSales(String startDate, String stopDate, Integer disId,
            List<Integer> scopeDepIds) {
        if (scopeDepIds == null || scopeDepIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<GbDepFoodSalesEntity> w = new LambdaQueryWrapper<>();
        w.eq(GbDepFoodSalesEntity::getGbDfsDistributerId, disId)
                .ge(GbDepFoodSalesEntity::getGbDfsFullDate, startDate)
                .le(GbDepFoodSalesEntity::getGbDfsFullDate, stopDate);
        if (scopeDepIds.size() == 1) {
            w.eq(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds.get(0));
        } else {
            w.in(GbDepFoodSalesEntity::getGbDfsDepId, scopeDepIds);
        }
        return gbDepFoodSalesService.list(w);
    }

    private static void applyDepFoodGoodsFilter(LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w,
            List<Integer> scopeDepIds) {
        if (scopeDepIds == null || scopeDepIds.isEmpty()) {
            w.apply("1=0");
            return;
        }
        if (scopeDepIds.size() == 1) {
            w.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, scopeDepIds.get(0));
        } else {
            w.in(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, scopeDepIds);
        }
    }

    private static Map<String, Object> buildReduceParams(Integer disId, String searchDepId, Integer depFatherId,
            String startDate, String stopDate) {
        Map<String, Object> map = GbDepartmentGoodsStockReduceSupport.buildReduceCostQueryMap(
                startDate, stopDate, disId, null, searchDepId);
        if (depFatherId != null && !AiInsightDishProfitScope.isGroupWideMendianAggregateUnderDis(depFatherId)) {
            map.put("depFatherId", depFatherId);
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    private static BigDecimal toBdFromDouble(Double d) {
        if (d == null || d.isNaN() || d.isInfinite()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(d.toString());
    }

    private static String plainMoney(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Integer) {
            return (Integer) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        String s = o.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.parseInt(s);
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        if (o instanceof Number) {
            return new BigDecimal(((Number) o).toString());
        }
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(o);
    }

    /** 数量类字段输出：先统一 4 位小数再去尾零，避免小数量（如 0.03）或累加结果被 4 位尺度吃掉。 */
    private static String plainQty(BigDecimal v) {
        return nz(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 配料分析 dish 行 Map 转 String 注入用（null→空串）。 */
    private static String dishRowFieldString(Object v) {
        if (v == null) {
            return "";
        }
        return String.valueOf(v).trim();
    }
}
