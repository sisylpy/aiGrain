package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;

/**
 * {@link #REPORT_KIND_SALES_DISH}：以销售菜品为主，含配料行与整菜出库可支撑瓶颈；{@link #REPORT_KIND_OUTBOUND_QTY}：以本期生产成本出库商品为主，下列关联菜品。
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

    /** 报表 data.bossColumnHintsZh：与 JSON 字段键一一对应的老板可读说明（白话）。 */
    private static final Map<String, String> BOSS_HINTS_SALES_DISH_ROW_ZH;
    private static final Map<String, String> BOSS_HINTS_BOTTLE_ZH;
    private static final Map<String, String> BOSS_HINTS_INGREDIENT_ROW_ZH;
    private static final Map<String, String> BOSS_HINTS_OUTBOUND_GOODS_GROUP_ZH;
    private static final Map<String, String> BOSS_HINTS_LINKING_DISH_ROW_ZH;

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
        ing.put("outboundAllocatedQty", "总出库里摊给本菜多少斤：优先按各菜「实销份数×本菜该料配方用量」占全局该料理论总耗的比例分（整体缺料时，蒜蓉这类不会分到超过自己实销所需的斤数）");
        ing.put("supportedPortionsThisGood", "这条料摊给你的斤数÷本菜该料每份用量：出库少于理论总耗时，这个数会小于本菜实销份数（老板看是否「用料偏紧」）");
        ing.put("salesIngredientCostAmount", "按销售子表里这条料的用量 theoryQtyFromSales×本期该料生产成本出库均价（元）。均价来自本期该料出库总重 W_g>0 时的 subtotal/weight；W_g=0 时金额为 0（无法从扣库汇总推单价）");
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

        LinkedHashMap<String, String> og = new LinkedHashMap<>();
        og.put("disGoodsId", "原料商品编号");
        og.put("goodsName", "原料名称");
        og.put("gbDgGoodsStandardname", "规格名称（与 ingredientRows 同源，来自 gb_distributer_goods）");
        og.put("gbDgControlFresh", "是否管控鲜度（与 ingredientRows 同源）");
        og.put("gbDgFreshWarnHour", "鲜品预警时长");
        og.put("gbDgFreshWasteHour", "鲜品报废/浪费时长相关");
        og.put("gbDgGoodsStandardWeight", "标准重量说明");
        og.put("outboundQtyTotal", "这种原料本期一共出了多少库");
        og.put("theoryOutboundQtyByRecipeTotal", "按各菜销量乘配方，这种料按理一共该用多少");
        og.put("theoryQtyFromSalesRecordsTotal", "销售明细里，这种料一共记了多少用量");
        og.put("linkingDishSoldPortionsTotal", "下面关联的各道菜，头表实销份数加总（与每行 soldPortions 之和一致）");
        og.put("linkingDishRows", "哪些菜配方里用到这种料、各占多少");
        BOSS_HINTS_OUTBOUND_GOODS_GROUP_ZH = Collections.unmodifiableMap(og);

        LinkedHashMap<String, String> link = new LinkedHashMap<>();
        link.put("foodId", "菜品编号");
        link.put("foodName", "菜名");
        link.put("soldPortions", "这道菜卖了多少份");
        link.put("outboundQtyAllocatedToDish", "这种料总出库按各菜「实销×配方该料用量」占比摊给本菜多少斤（缺料时摊得少于理论需求）");
        link.put("supportedPortionsOnThisGoodOnly", "光看这一种料，大约还能做几份");
        link.put("recipeUnitOnDish", "这道菜里，这一种料每份用多少");
        link.put("theoryOutboundQtyByRecipe", "按卖的份数乘配方，这种料在这道菜上按理该用多少");
        link.put("theoryQtyFromSalesRecords", "销售明细里，这种料在这道菜上记了多少");
        BOSS_HINTS_LINKING_DISH_ROW_ZH = Collections.unmodifiableMap(link);
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
            Integer depFatherId, String reportKind) {
        if (startDate == null || stopDate == null || disId == null) {
            throw new IllegalArgumentException("startDate、stopDate、disId 不能为空");
        }
        String rk = reportKind == null || reportKind.isEmpty() ? REPORT_KIND_SALES_DISH
                : reportKind.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace(" ", "");
        if (!REPORT_KIND_SALES_DISH.equals(rk) && !REPORT_KIND_OUTBOUND_QTY.equals(rk)) {
            throw new IllegalArgumentException("reportKind 仅支持 salesDish 或 outboundQty");
        }

        List<Integer> scopeDepIds = resolveScopeDepIds(disId, searchDepId, depFatherId);
        Map<String, Object> reduceParams = buildReduceParams(disId, searchDepId, depFatherId, startDate, stopDate);
        List<Map<String, Object>> reduceAgg = gbDepartmentGoodsStockReduceService.queryProductionReduceAggByDisGoods(reduceParams);

        // W_g：本期「生产成本出库」按分销商商品 g 汇总的出库重量（与 reduceAgg.weightSum 一致，斤等实物单位）。
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
        for (GbDepFoodSalesEntity s : foodSales) {
            Integer sid = s.getGbDepFoodSalesId();
            Integer fid = s.getGbDfsFoodId();
            if (sid == null || fid == null) {
                continue;
            }
            saleIdToFoodId.put(sid, fid);
            BigDecimal q = GbDepartmentGoodsStockReduceSupport.coerceDecimal(s.getGbDfsAmount());
            salesQtyByFood.merge(fid, q, BigDecimal::add);
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
        allFoodIds.addAll(theoryWtByFoodAndGoods.keySet());
        RecipeGoodsAgg recipeAgg = buildRecipeGoodsAggregates(allFoodIds, salesQtyByFood);
        Map<Integer, BigDecimal> sumRecipeUnitByGoods = recipeAgg.sumUByGoods;
        Map<Integer, BigDecimal> sumSalesQtyByGoods = recipeAgg.sumSalesByGoods;
        // sumNeed_g：全报表原料 g 的「理论总耗量」= Σ_菜 (q_菜 × 本菜该料合并单份用量)，与 Σ(q×u) 一致；作共料出库分摊主分母，使 W_g<Σ 时单菜反推份数 < 实销。
        Map<Integer, BigDecimal> sumNeedByGoods = buildSumNeedByGoods(allFoodIds, salesQtyByFood);
        if (log.isInfoEnabled()) {
            log.info("[dishCost] reportKind={} 区间={}~{} disId={} searchDepId={} depFatherId={} scopeDepIds={} allFoodIds={}",
                    rk, startDate, stopDate, disId, searchDepId, depFatherId, scopeDepIds, allFoodIds);
            log.info("[dishCost] 本期生产成本出库 W(disGoodsId->weight)={}", reduceW);
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
        Map<Integer, GbDistributerGoodsEntity> disGoodsById = REPORT_KIND_SALES_DISH.equals(rk)
                ? loadDisGoodsDetailByRecipeGoods(allFoodIds)
                : Collections.emptyMap();
        if (REPORT_KIND_SALES_DISH.equals(rk)) {
            for (Integer foodId : allFoodIds) {
                salesDishRows.add(buildSalesDishRow(foodId, theoryWtByFoodAndGoods.getOrDefault(foodId, Collections.emptyMap()),
                        sumTheoryByGoods, sumRecipeUnitByGoods, sumSalesQtyByGoods, sumNeedByGoods, disGoodsById, reduceW, reduceS,
                        salesQtyByFood.getOrDefault(foodId, BigDecimal.ZERO)));
            }
            salesDishRows.sort(Comparator.comparing(o -> toBd(o.get("sortKey")), Comparator.reverseOrder()));
        } else {
            outboundGoodsRows.addAll(buildOutboundGoodsRows(allFoodIds, reduceW, sumRecipeUnitByGoods, sumSalesQtyByGoods,
                    sumNeedByGoods, salesQtyByFood, theoryWtByFoodAndGoods, sumTheoryByGoods));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportKind", REPORT_KIND_SALES_DISH.equals(rk) ? "salesDish" : "outboundQty");
        out.put("startDate", startDate);
        out.put("stopDate", stopDate);
        out.put("disId", disId);
        out.put("searchDepId", searchDepId);
        out.put("depFatherId", depFatherId);
        out.put("salesDishRows", REPORT_KIND_SALES_DISH.equals(rk) ? salesDishRows : null);
        out.put("outboundGoodsRows", REPORT_KIND_OUTBOUND_QTY.equals(rk) ? outboundGoodsRows : null);

        Map<String, Object> bossHints = new LinkedHashMap<>();
        bossHints.put("salesDishRow", BOSS_HINTS_SALES_DISH_ROW_ZH);
        bossHints.put("bottle", BOSS_HINTS_BOTTLE_ZH);
        bossHints.put("ingredientRow", BOSS_HINTS_INGREDIENT_ROW_ZH);
        if (REPORT_KIND_OUTBOUND_QTY.equals(rk)) {
            bossHints.put("outboundGoodsGroup", BOSS_HINTS_OUTBOUND_GOODS_GROUP_ZH);
            bossHints.put("linkingDishRow", BOSS_HINTS_LINKING_DISH_ROW_ZH);
        }
        out.put("bossColumnHintsZh", bossHints);

        return out;
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
        Map<Integer, BigDecimal> sumU = new HashMap<>();
        Map<Integer, BigDecimal> sumQ = new HashMap<>();
        if (foodIds == null || foodIds.isEmpty()) {
            return new RecipeGoodsAgg(sumU, sumQ);
        }
        for (Integer fid : foodIds) {
            BigDecimal q = salesQtyByFood == null ? BigDecimal.ZERO : salesQtyByFood.getOrDefault(fid, BigDecimal.ZERO);
            List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(fid);
            if (recipe == null) {
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
        Map<Integer, BigDecimal> sumNeed = new HashMap<>();
        if (foodIds == null || foodIds.isEmpty()) {
            return sumNeed;
        }
        for (Integer fid : foodIds) {
            BigDecimal q = salesQtyByFood == null ? BigDecimal.ZERO : salesQtyByFood.getOrDefault(fid, BigDecimal.ZERO);
            List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(fid);
            if (recipe == null) {
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
        Set<Integer> goodIds = new LinkedHashSet<>();
        if (foodIds != null) {
            for (Integer fid : foodIds) {
                List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(fid);
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
        Map<Integer, GbDistributerGoodsEntity> out = new HashMap<>();
        for (Integer gid : goodIds) {
            GbDistributerGoodsEntity e = gbDistributerGoodsService.queryObject(gid);
            if (e != null) {
                out.put(gid, e);
            }
        }
        return out;
    }

    /**
     * 把 {@code gb_distributer_goods} 上的规格与鲜品字段挂到 Map（用于 {@code salesDishRows[].ingredientRows} 与
     * {@code outboundGoodsRows[]} 商品行，字段名与 {@link GbDistributerGoodsEntity} 一致）。
     */
    private static void putDisGoodsProfileFields(Map<String, Object> row, GbDistributerGoodsEntity ge) {
        if (ge == null) {
            row.put("gbDgGoodsName", null);
            row.put("gbDgGoodsStandardname", null);
            row.put("gbDgControlFresh", null);
            row.put("gbDgFreshWarnHour", null);
            row.put("gbDgFreshWasteHour", null);
            row.put("gbDgGoodsStandardWeight", null);
            row.put("gbDgGoodsFileImg", null);
            return;
        }
        row.put("gbDgGoodsName", ge.getGbDgGoodsName());
        row.put("gbDgGoodsStandardname", ge.getGbDgGoodsStandardname());
        row.put("gbDgControlFresh", ge.getGbDgControlFresh());
        row.put("gbDgFreshWarnHour", ge.getGbDgFreshWarnHour());
        row.put("gbDgFreshWasteHour", ge.getGbDgFreshWasteHour());
        row.put("gbDgGoodsStandardWeight", ge.getGbDgGoodsStandardWeight());
        row.put("gbDgGoodsFileImg", ge.getGbDgNxFatherImg());
    }

    /**
     * {@code outboundQty} 主表：本期有出库的 {@code disGoodsId}，下列关联菜品。
     */
    private List<Map<String, Object>> buildOutboundGoodsRows(Set<Integer> allFoodIds,
            Map<Integer, BigDecimal> reduceW,
            Map<Integer, BigDecimal> sumRecipeUnitByGoods,
            Map<Integer, BigDecimal> sumSalesQtyByGoods,
            Map<Integer, BigDecimal> sumNeedByGoods,
            Map<Integer, BigDecimal> salesQtyByFood,
            Map<Integer, Map<Integer, BigDecimal>> theoryWtByFoodAndGoods,
            Map<Integer, BigDecimal> sumTheoryByGoods) {
        if (allFoodIds == null || allFoodIds.isEmpty()) {
            return new ArrayList<>();
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
            List<Map<String, Object>> dishRows = new ArrayList<>();
            BigDecimal theoryOutboundQtyByRecipeTotal = BigDecimal.ZERO;
            // 关联菜品头表实销份数之和（每道关联菜在 gb_dep_food_sales 汇总的 q，与 linkingDishRows[].soldPortions 加总一致）
            BigDecimal linkingDishSoldPortionsTotal = BigDecimal.ZERO;
            if (fids != null) {
                for (Integer fid : fids) {
                    List<GbDistributerFoodGoodsEntity> recipe = recipeByFood.get(fid);
                    BigDecimal dishUForGood = sumRecipeUnitForGoodOnDish(recipe, gId);
                    BigDecimal salesQty = salesQtyByFood == null ? BigDecimal.ZERO : salesQtyByFood.getOrDefault(fid, BigDecimal.ZERO);
                    linkingDishSoldPortionsTotal = linkingDishSoldPortionsTotal.add(salesQty);
                    BigDecimal theoryByRecipe = salesQty.multiply(dishUForGood);
                    theoryOutboundQtyByRecipeTotal = theoryOutboundQtyByRecipeTotal.add(theoryByRecipe);
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
                    // 与 salesDish 配料行一致：摊得斤数 ÷ 本菜该料单份合并用量 = 该料可支撑份数（sumNeed 分摊下整体缺料时 ≤ 实销）
                    BigDecimal maxPortionsThisGood = dishUForGood.compareTo(BigDecimal.ZERO) > 0
                            ? allocW.divide(dishUForGood, 8, RoundingMode.HALF_UP)
                            : minSellablePortionsForDishOnGood(recipe, gId, wG, salesQty, recipeUnitSum, salesSumForGood);

                    Map<String, Object> drow = new LinkedHashMap<>();
                    drow.put("foodId", fid);
                    drow.put("foodName", foodName);
                    drow.put("soldPortions", salesQty.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                    drow.put("outboundQtyAllocatedToDish",
                            allocW.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                    drow.put("supportedPortionsOnThisGoodOnly",
                            maxPortionsThisGood.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                    drow.put("recipeUnitOnDish",
                            dishUForGood.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                    drow.put("theoryOutboundQtyByRecipe",
                            theoryByRecipe.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                    drow.put("theoryQtyFromSalesRecords",
                            theoryFromSales.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                    dishRows.add(drow);
                }
                dishRows.sort(Comparator.comparing((Map<String, Object> m) -> toBd(m.get("soldPortions"))).reversed());
            }

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("disGoodsId", gId);
            group.put("goodsName", goodsNameById.getOrDefault(gId, ""));
            putDisGoodsProfileFields(group, outboundGoodsProfileById.get(gId));
            group.put("outboundQtyTotal",
                    wG.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            group.put("theoryOutboundQtyByRecipeTotal",
                    theoryOutboundQtyByRecipeTotal.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            group.put("theoryQtyFromSalesRecordsTotal",
                    nz(sumTheoryByGoods == null ? null : sumTheoryByGoods.get(gId))
                            .setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            group.put("linkingDishSoldPortionsTotal",
                    linkingDishSoldPortionsTotal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            group.put("linkingDishRows", dishRows);
            groups.add(group);
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

        GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(foodId);
        String foodName = food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "";

        List<GbDistributerFoodGoodsEntity> recipe = gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId);
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
        String soldPortionsStr = salesQty.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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

    /** 生产成本出库可支撑份数 vs 实销（不引用采购量、不推算库存）。 */
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
            return "实销高于出库可支撑份数，可能配方用量偏小、销售录入偏大，或其它来源原料未记入生产成本出库。";
        }
        return "出库可支撑份数与实销大致匹配，可结合用量模式查看理论消耗偏差。";
    }

    /**
     * {@code queryGroupDepsByDisId} 返回的是「分组父部门」，真实门店/后厨等在 {@code gbDepartmentEntityList} 子部门中，
     * 销售表 {@code gb_dep_food_sales.gb_dfs_department_id} 对应子部门 id，不能只用父行的 fatherId 过滤。
     */
    private List<Integer> resolveScopeDepIds(Integer disId, String searchDepId, Integer depFatherId) {
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            return Collections.singletonList(Integer.valueOf(searchDepId));
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
                if (depFatherId != null && !depFatherId.equals(sub.getGbDepartmentFatherId())) {
                    continue;
                }
                uniq.put(sub.getGbDepartmentId(), Boolean.TRUE);
            }
        }
        return new ArrayList<>(uniq.keySet());
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
        if (depFatherId != null) {
            map.put("depFatherId", depFatherId);
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
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

    /** 数量类字段输出：先统一 8 位小数再去尾零，避免小数量（如 0.03）或累加结果被 4 位尺度吃掉。 */
    private static String plainQty(BigDecimal v) {
        return nz(v).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
