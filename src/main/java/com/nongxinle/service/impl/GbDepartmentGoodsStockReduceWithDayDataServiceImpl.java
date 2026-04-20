package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceWithDayDataService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nongxinle.utils.DateUtils.afterWhatDay;
import static com.nongxinle.utils.DateUtils.getHowManyDaysInPeriod;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbDepartmentGoodsStockReduceWithDayDataServiceImpl implements GbDepartmentGoodsStockReduceWithDayDataService {

    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbDepartmentService gbDepartmentService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;

    @Override
    public Map<String, Object> buildReduceWithDayData(String startDate, String stopDate, Integer disGoodsId,
            String searchDepId) {
        GbDistributerGoodsEntity disGoods = gbDistributerGoodsService.queryObject(disGoodsId);
        if (disGoods == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        Integer disId = disGoods.getGbDgDistributerId();

        int howManyDaysInPeriod = 0;
        if (!startDate.equals(stopDate)) {
            howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);
        }
        Map<String, Object> mapResult = new HashMap<>();
        List<String> dateList = new ArrayList<>();
        List<Map<String, Object>> producelist = new ArrayList<>();
        List<Map<String, Object>> foodSalesIngredientList = new ArrayList<>();
        List<Map<String, Object>> losslist = new ArrayList<>();
        List<Map<String, Object>> wastelist = new ArrayList<>();
        List<Map<String, Object>> listItem = new ArrayList<>();

        Map<String, Object> map0 = GbDepartmentGoodsStockReduceSupport.buildReduceParamsForGoodsDay(disId, disGoodsId,
                startDate, stopDate, howManyDaysInPeriod, searchDepId);
        map0.put("types", Arrays.asList(
                GbConstants.StockReduceType.PRODUCTION,
                GbConstants.StockReduceType.WASTE,
                GbConstants.StockReduceType.LOSS));
        Integer integer1 = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(map0);
        if (integer1 == null || integer1 == 0) {
            throw new IllegalArgumentException("没有数据");
        }
        map0.remove("types");

        Double weightTotalTL = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceCostSubtotal(
                GbDepartmentGoodsStockReduceSupport.withReduceType(map0, GbConstants.StockReduceType.LOSS)));
        Double weightTotalTLW = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                GbDepartmentGoodsStockReduceSupport.withReduceType(map0, GbConstants.StockReduceType.LOSS)));
        Double weightTotalTW = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceCostSubtotal(
                GbDepartmentGoodsStockReduceSupport.withReduceType(map0, GbConstants.StockReduceType.WASTE)));
        Double weightTotalTWW = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                GbDepartmentGoodsStockReduceSupport.withReduceType(map0, GbConstants.StockReduceType.WASTE)));
        Double weightTotalS = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceCostSubtotal(
                GbDepartmentGoodsStockReduceSupport.withReduceType(map0, GbConstants.StockReduceType.PRODUCTION)));
        Double weightTotalSW = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                GbDepartmentGoodsStockReduceSupport.withReduceType(map0, GbConstants.StockReduceType.PRODUCTION)));

        log.debug("susbbddmap{}", map0);
        double subTotal = 0.0;
        double weightTotal = 0.0;
        Map<String, Object> purMap = new HashMap<>(map0);
        purMap.remove("type");
        purMap.put("useStockFinishDate", Boolean.TRUE);
        Integer integer11 = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(purMap);
        if (integer11 != null && integer11 > 0) {
            subTotal = GbDepartmentGoodsStockReduceSupport.nzD(gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(purMap));
            weightTotal = GbDepartmentGoodsStockReduceSupport.nzD(gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(purMap));
        }

        double weightTotalP = weightTotalS + weightTotalTL + weightTotalTW;
        double weightTotalPW = weightTotalSW + weightTotalTLW + weightTotalTWW;

        double foodSalesIngredientPeriod = 0.0;
        if (howManyDaysInPeriod > 0) {
            for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                String whichDay = i == 0 ? startDate : afterWhatDay(startDate, i);
                String substring = whichDay.substring(8, 10);
                dateList.add(substring);

                Map<String, Object> map = GbDepartmentGoodsStockReduceSupport.buildReduceParamsForGoodsDay(disId, disGoodsId, whichDay, whichDay,
                        0, searchDepId);
                Map<String, Object> countMap = new HashMap<>(map);
                countMap.put("types", Arrays.asList(
                        GbConstants.StockReduceType.PRODUCTION,
                        GbConstants.StockReduceType.WASTE,
                        GbConstants.StockReduceType.LOSS));
                Integer integer = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(countMap);
                double weightDayTotal = 0.0;
                double produceSubtotal = 0.0;
                double lossSubtotal = 0.0;
                double wasteSubtotal = 0.0;
                if (integer != null && integer > 0) {
                    log.debug("kaankanank25hao{}", map);
                    lossSubtotal = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                            GbDepartmentGoodsStockReduceSupport.withReduceType(map, GbConstants.StockReduceType.LOSS)));
                    wasteSubtotal = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                            GbDepartmentGoodsStockReduceSupport.withReduceType(map, GbConstants.StockReduceType.WASTE)));
                    produceSubtotal = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                            GbDepartmentGoodsStockReduceSupport.withReduceType(map, GbConstants.StockReduceType.PRODUCTION)));
                    weightDayTotal = produceSubtotal + lossSubtotal + wasteSubtotal;
                }
                Map<String, Object> mapPro = new HashMap<>();
                mapPro.put("date", whichDay);
                mapPro.put("value", String.format("%.1f", produceSubtotal));
                producelist.add(mapPro);

                double dayFoodIngredient = sumFoodGoodsSalesIngredient(disGoodsId, whichDay, searchDepId);
                foodSalesIngredientPeriod += dayFoodIngredient;
                Map<String, Object> mapFoodIng = new HashMap<>();
                mapFoodIng.put("date", whichDay);
                mapFoodIng.put("value", String.format("%.1f", dayFoodIngredient));
                foodSalesIngredientList.add(mapFoodIng);

                Map<String, Object> mapLoss = new HashMap<>();
                mapLoss.put("date", whichDay);
                mapLoss.put("value", String.format("%.1f", lossSubtotal));
                losslist.add(mapLoss);

                Map<String, Object> mapWaste = new HashMap<>();
                mapWaste.put("date", whichDay);
                mapWaste.put("value", String.format("%.1f", wasteSubtotal));
                wastelist.add(mapWaste);

                if (weightDayTotal > 0) {
                    Map<String, Object> mapForReduceList = new HashMap<>(map);
                    mapForReduceList.remove("depId");
                    mapForReduceList.remove("depType");
                    log.debug("kanakndninteterere{}", mapForReduceList);
                    Integer integerD = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mapForReduceList);
                    Map<String, Object> mapReduce = new HashMap<>();
                    mapReduce.put("date", whichDay);
                    if (integerD != null && integerD > 0) {
                        listItem.add(mapReduce);
                    }
                }
            }
        }

        mapResult.put("itemList", listItem);
        mapResult.put("produceList", producelist);
        mapResult.put("foodSalesIngredientList", foodSalesIngredientList);
        mapResult.put("foodSalesIngredientTotal",
                BigDecimal.valueOf(foodSalesIngredientPeriod).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("foodSalesStats", buildFoodSalesStatsEmbedded(disGoodsId, disId, startDate, stopDate, searchDepId,
                foodSalesIngredientList, foodSalesIngredientPeriod));

        mapResult.put("subTotal", BigDecimal.valueOf(subTotal).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("weightTotal", BigDecimal.valueOf(weightTotal).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("lossList", losslist);
        mapResult.put("wasteList", wastelist);
        mapResult.put("oneTotal", BigDecimal.valueOf(weightTotalP).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("oneTotalWeight", BigDecimal.valueOf(weightTotalPW).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("salesTotal", BigDecimal.valueOf(weightTotalS).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("salesTotalWeight", BigDecimal.valueOf(weightTotalSW).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("lossTotal", BigDecimal.valueOf(weightTotalTL).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("lossTotalWeight", BigDecimal.valueOf(weightTotalTLW).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("wasteTotal", BigDecimal.valueOf(weightTotalTW).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("wasteTotalWeight", BigDecimal.valueOf(weightTotalTWW).setScale(1, RoundingMode.HALF_UP));
        mapResult.put("date", dateList);

        int spanDays = howManyDaysInPeriod > 0 ? howManyDaysInPeriod + 1 : 1;
        Map<String, Object> overallAiAnalysis = generateOverallAiAnalysis(
                weightTotalS, weightTotalTL, weightTotalTW, disGoodsId, spanDays, weightTotalPW, weightTotalP);
        if (overallAiAnalysis != null) {
            mapResult.put("aiAnalysis", overallAiAnalysis);
        }

        return mapResult;
    }

    @Override
    public double sumFoodGoodsSalesIngredient(Integer disGoodsId, String fullDate, String searchDepId) {
        LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, fullDate);
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            w.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, Integer.valueOf(searchDepId));
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GbDepFoodGoodsSalesEntity e : gbDepFoodGoodsSalesService.list(w)) {
            sum = sum.add(GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(e.getGbDfgsGoodsAmount()));
        }
        return sum.doubleValue();
    }

    private Map<String, Object> buildFoodSalesStatsEmbedded(Integer disGoodsId, Integer disId, String startDate,
            String stopDate, String searchDepId, List<Map<String, Object>> ingredientByDay,
            double ingredientPeriodTotal) {
        log.debug("[DEBUG][foodSalesStats] buildFoodSalesStatsEmbedded 入参 disGoodsId={} disId={} startDate={} stopDate={} searchDepId={} ingredientByDay.size={} ingredientPeriodTotal={}",
                disGoodsId, disId, startDate, stopDate, searchDepId, ingredientByDay.size(), ingredientPeriodTotal);

        LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                .ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, startDate)
                .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, stopDate);
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            w.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, Integer.valueOf(searchDepId));
        }
        List<GbDepFoodGoodsSalesEntity> ingRows = gbDepFoodGoodsSalesService.list(w);

        Map<Integer, BigDecimal> ingredientByFoodSalesId = new HashMap<>();
        for (GbDepFoodGoodsSalesEntity r : ingRows) {
            if (r.getGbDfgsFoodSalesId() == null) {
                continue;
            }
            ingredientByFoodSalesId.merge(r.getGbDfgsFoodSalesId(),
                    GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(r.getGbDfgsGoodsAmount()), BigDecimal::add);
        }

        List<Map<String, Object>> topLinked = ingredientByFoodSalesId.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(25)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("gbDepFoodSalesId", e.getKey());
                    row.put("ingredientAmount", e.getValue().setScale(1, RoundingMode.HALF_UP).doubleValue());
                    GbDepFoodSalesEntity sale = gbDepFoodSalesService.getById(e.getKey());
                    if (sale != null) {
                        row.put("gbDfsFoodId", sale.getGbDfsFoodId());
                        row.put("dishQty", GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(sale.getGbDfsAmount())
                                .setScale(1, RoundingMode.HALF_UP).doubleValue());
                    }
                    return row;
                })
                .collect(Collectors.toList());

        BigDecimal linkedDishQtyTotal = BigDecimal.ZERO;
        for (Integer fsId : ingredientByFoodSalesId.keySet()) {
            GbDepFoodSalesEntity sale = gbDepFoodSalesService.getById(fsId);
            if (sale != null) {
                linkedDishQtyTotal = linkedDishQtyTotal.add(GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(sale.getGbDfsAmount()));
            }
        }

        Map<String, Object> dishSales = new HashMap<>();
        dishSales.put("linkedSalesRowCount", ingredientByFoodSalesId.size());
        dishSales.put("linkedDishQtyTotal", linkedDishQtyTotal.setScale(1, RoundingMode.HALF_UP).doubleValue());
        dishSales.put("topDishesDrivingThisIngredient", topLinked);

        Map<String, Object> period = new HashMap<>();
        period.put("startDate", startDate);
        period.put("stopDate", stopDate);

        Map<String, Object> filters = new HashMap<>();
        filters.put("disGoodsId", disGoodsId);
        filters.put("searchDepId", searchDepId);

        Map<String, Object> recipeAndTheory = buildRecipeTheoreticalAndDepDishSales(disGoodsId, disId, startDate,
                stopDate, searchDepId, ingredientPeriodTotal);

        Map<String, Object> root = new HashMap<>();
        root.put("depFoodGoodsSalesRowCountInPeriod", ingRows.size());
        root.put("dishSales", dishSales);
        root.put("period", period);
        root.put("filters", filters);
        root.putAll(recipeAndTheory);
        return root;
    }

    private BigDecimal sumRecipeAmountAllIngredients(Integer foodId) {
        BigDecimal total = BigDecimal.ZERO;
        for (GbDistributerFoodGoodsEntity line : gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId)) {
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                continue;
            }
            total = total.add(GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(line.getGbDfgGoodsAmount()));
        }
        return total;
    }

    private Map<String, Object> buildRecipeTheoreticalAndDepDishSales(Integer disGoodsId, Integer disId,
            String startDate, String stopDate, String searchDepId, double actualIngredientTotal) {
        log.debug("[DEBUG][foodSalesStats] buildRecipeTheoreticalAndDepDishSales 入参 disGoodsId={} disId={} startDate={} stopDate={} searchDepId={} actualIngredientTotal={}",
                disGoodsId, disId, startDate, stopDate, searchDepId, actualIngredientTotal);

        Map<String, Object> out = new HashMap<>();
        List<GbDistributerFoodGoodsEntity> linesForGood =
                gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, disId);
        log.debug("[DEBUG][foodSalesStats] queryFoodGoodsByDisGoodsId(disGoodsId={}, disId={}) 返回行数={}",
                disGoodsId, disId, linesForGood.size());
        if (linesForGood.isEmpty() && disId != null) {
            List<GbDistributerFoodGoodsEntity> fallback =
                    gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, null);
            log.debug("[DEBUG][foodSalesStats] 按 disId 无配方行，回退为仅 gb_dfg_dis_goods_id={} 查询，行数={}",
                    disGoodsId, fallback.size());
            if (!fallback.isEmpty()) {
                for (int i = 0; i < Math.min(3, fallback.size()); i++) {
                    GbDistributerFoodGoodsEntity x = fallback.get(i);
                    log.debug("[DEBUG][foodSalesStats] 回退命中样例 gb_dfg_dis_id={} gb_dfg_food_id={} gb_dfg_dis_goods_id={}",
                            x.getGbDfgDisId(), x.getGbDfgFoodId(), x.getGbDfgDisGoodsId());
                }
            }
            linesForGood = fallback;
        }
        int skippedInactive = 0;
        int skippedNullFoodId = 0;
        int maxSample = Math.min(5, linesForGood.size());
        for (int i = 0; i < maxSample; i++) {
            GbDistributerFoodGoodsEntity x = linesForGood.get(i);
            log.debug("[DEBUG][foodSalesStats] 配方样例[{}] gbDfgFoodId={} gbDfgDisGoodsId={} gbDfgDisId={} gbDfgStatus={} amount={}",
                    i, x.getGbDfgFoodId(), x.getGbDfgDisGoodsId(), x.getGbDfgDisId(), x.getGbDfgStatus(), x.getGbDfgGoodsAmount());
        }

        Map<Integer, BigDecimal> recipeAmountThisGoodByFood = new HashMap<>();
        for (GbDistributerFoodGoodsEntity line : linesForGood) {
            if (line.getGbDfgFoodId() == null) {
                skippedNullFoodId++;
                continue;
            }
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line)) {
                skippedInactive++;
                continue;
            }
            recipeAmountThisGoodByFood.merge(line.getGbDfgFoodId(),
                    GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(line.getGbDfgGoodsAmount()), BigDecimal::add);
        }
        log.debug("[DEBUG][foodSalesStats] 配方行过滤: skippedNullFoodId={} skippedInactive={} recipeAmountThisGoodByFood.size={} foodIds={}",
                skippedNullFoodId, skippedInactive, recipeAmountThisGoodByFood.size(), recipeAmountThisGoodByFood.keySet());

        Map<Integer, BigDecimal> totalRecipeCache = new HashMap<>();
        List<Map<String, Object>> recipeDishes = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : recipeAmountThisGoodByFood.entrySet()) {
            Integer foodId = e.getKey();
            BigDecimal amtThis = e.getValue();
            BigDecimal totalDish = totalRecipeCache.computeIfAbsent(foodId, this::sumRecipeAmountAllIngredients);
            double share = BigDecimal.ZERO.compareTo(totalDish) == 0 ? 0.0
                    : amtThis.divide(totalDish, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gbDfsFoodId", foodId);
            row.put("recipeAmountThisGoodPerDish", amtThis.setScale(4, RoundingMode.HALF_UP).doubleValue());
            row.put("totalRecipeAmountAllIngredients",
                    totalDish.setScale(4, RoundingMode.HALF_UP).doubleValue());
            row.put("shareOfRecipePercent", Math.round(share * 10) / 10.0);
            GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(foodId);
            row.put("foodName", food != null ? food.getGbDfFoodName() : "");
            recipeDishes.add(row);
        }
        recipeDishes.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("foodName", ""))));
        log.debug("[DEBUG][foodSalesStats] recipeDishesUsingThisGood 条数={}", recipeDishes.size());

        if (recipeAmountThisGoodByFood.isEmpty()) {
            log.debug("[DEBUG][foodSalesStats] recipeAmountThisGoodByFood 为空 → recipeDishes 与 periodSales 均为空；请检查 gb_distributer_food_goods 是否有 gb_dfg_dis_goods_id={} 且 gb_dfg_dis_id={} 且状态有效",
                    disGoodsId, disId);
            out.put("recipeDishesUsingThisGood", recipeDishes);
            Map<String, Object> tv = new LinkedHashMap<>();
            tv.put("theoreticalIngredientTotal", 0.0);
            tv.put("actualIngredientTotal", round1(actualIngredientTotal));
            tv.put("ingredientGapActualMinusTheoretical", round1(actualIngredientTotal));
            tv.put("ingredientGapPercentOfTheoretical", null);
            out.put("theoreticalVsActual", tv);
            out.put("periodSalesByDepAndDish", new ArrayList<>());
            return out;
        }

        LambdaQueryWrapper<GbDepFoodSalesEntity> sq = new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                .in(GbDepFoodSalesEntity::getGbDfsFoodId, recipeAmountThisGoodByFood.keySet())
                .ge(GbDepFoodSalesEntity::getGbDfsFullDate, startDate)
                .le(GbDepFoodSalesEntity::getGbDfsFullDate, stopDate);
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            sq.eq(GbDepFoodSalesEntity::getGbDfsDepId, Integer.valueOf(searchDepId));
        }
        log.debug("[DEBUG][foodSalesStats] gb_dep_food_sales 查询条件 foodId in {} fullDate [{},{}] depFilter={}",
                recipeAmountThisGoodByFood.keySet(), startDate, stopDate, searchDepId);
        List<GbDepFoodSalesEntity> salesRows = gbDepFoodSalesService.list(sq);
        log.debug("[DEBUG][foodSalesStats] gb_dep_food_sales 命中行数={}", salesRows.size());
        for (int i = 0; i < Math.min(5, salesRows.size()); i++) {
            GbDepFoodSalesEntity s = salesRows.get(i);
            log.debug("[DEBUG][foodSalesStats] 销售样例[{}] id={} depId={} foodId={} fullDate={} amount={}",
                    i, s.getGbDepFoodSalesId(), s.getGbDfsDepId(), s.getGbDfsFoodId(), s.getGbDfsFullDate(), s.getGbDfsAmount());
        }

        Map<String, BigDecimal> soldQtyByDepFood = new HashMap<>();
        for (GbDepFoodSalesEntity s : salesRows) {
            if (s.getGbDfsDepId() == null || s.getGbDfsFoodId() == null) {
                continue;
            }
            String key = s.getGbDfsDepId() + "_" + s.getGbDfsFoodId();
            soldQtyByDepFood.merge(key, GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(s.getGbDfsAmount()), BigDecimal::add);
        }

        BigDecimal theoreticalTotal = BigDecimal.ZERO;
        List<Map<String, Object>> depDishRows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : soldQtyByDepFood.entrySet()) {
            String[] parts = e.getKey().split("_", 2);
            int depId = Integer.parseInt(parts[0]);
            int foodId = Integer.parseInt(parts[1]);
            BigDecimal soldQty = e.getValue();
            BigDecimal per = recipeAmountThisGoodByFood.getOrDefault(foodId, BigDecimal.ZERO);
            BigDecimal theo = soldQty.multiply(per);
            theoreticalTotal = theoreticalTotal.add(theo);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gbDfsDepId", depId);
            GbDepartmentEntity dep = gbDepartmentService.getById(depId);
            row.put("depName", dep != null ? dep.getGbDepartmentName() : "");
            row.put("gbDfsFoodId", foodId);
            GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(foodId);
            row.put("foodName", food != null ? food.getGbDfFoodName() : "");
            row.put("soldQty", soldQty.setScale(1, RoundingMode.HALF_UP).doubleValue());
            row.put("recipeAmountThisGoodPerDish", per.setScale(4, RoundingMode.HALF_UP).doubleValue());
            row.put("theoreticalIngredientForThisRow", theo.setScale(1, RoundingMode.HALF_UP).doubleValue());
            depDishRows.add(row);
        }
        depDishRows.sort(Comparator
                .comparing((Map<String, Object> m) -> (String) m.getOrDefault("depName", ""))
                .thenComparing(m -> (Integer) m.get("gbDfsFoodId")));
        out.put("periodSalesByDepAndDish", depDishRows);
        out.put("recipeDishesUsingThisGood", recipeDishes);
        log.debug("[DEBUG][foodSalesStats] periodSalesByDepAndDish 条数={} soldQtyByDepFood.keys={}",
                depDishRows.size(), soldQtyByDepFood.keySet());

        BigDecimal actualBd = BigDecimal.valueOf(actualIngredientTotal);
        BigDecimal gap = actualBd.subtract(theoreticalTotal);
        Double gapPct = BigDecimal.ZERO.compareTo(theoreticalTotal) == 0 ? null
                : gap.divide(theoreticalTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP).doubleValue();

        Map<String, Object> tv = new LinkedHashMap<>();
        tv.put("theoreticalIngredientTotal", theoreticalTotal.setScale(1, RoundingMode.HALF_UP).doubleValue());
        tv.put("actualIngredientTotal", actualBd.setScale(1, RoundingMode.HALF_UP).doubleValue());
        tv.put("ingredientGapActualMinusTheoretical", gap.setScale(1, RoundingMode.HALF_UP).doubleValue());
        tv.put("ingredientGapPercentOfTheoretical", gapPct);
        out.put("theoreticalVsActual", tv);
        return out;
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private Map<String, Object> generateOverallAiAnalysis(Double totalProduce, Double totalLoss, Double totalWaste,
            Integer disGoodsId, int spanDays, double weightTotalPW, double weightTotalP) {
        try {
            Map<String, Object> aiResult = new HashMap<>();
            int days = Math.max(1, spanDays);

            double totalUsage = totalProduce + totalLoss + totalWaste;
            double aWeight = weightTotalPW / days;
            double aSubtotal = weightTotalP / days;

            double lossRate = totalProduce > 0 ? (totalLoss / totalUsage) * 100 : 0;
            double wasteRate = totalProduce > 0 ? (totalWaste / totalUsage) * 100 : 0;
            double totalWasteRate = totalProduce > 0 ? (totalWaste / totalUsage) * 100 : 0;

            String type = "normal";
            List<String> suggestions = new ArrayList<>();

            GbDistributerGoodsEntity goods = gbDistributerGoodsService.queryObject(disGoodsId);
            if (goods == null) {
                return null;
            }
            if (Integer.valueOf(1).equals(goods.getGbDgControlFresh())) {
                type = "fresh";
                List<String> warnings = new ArrayList<>();
                if (wasteRate > 0) {
                    warnings.add("⚠️ 统计周期内存在废弃情况，需要关注商品质量或存储条件");
                }
                if (lossRate > 10) {
                    warnings.add("⚠️ 平均损耗率较高(" + String.format("%.1f", lossRate) + "%)，建议检查操作流程");
                }
                if (totalWasteRate > 15) {
                    warnings.add("⚠️ 总浪费率过高(" + String.format("%.1f", totalWasteRate) + "%)，需要优化管理");
                }
                if (wasteRate > 0) {
                    suggestions.add("💡 建议检查存储条件，优化商品管理流程");
                }
                if (totalWasteRate < 5) {
                    suggestions.add("✅ 商品管理良好，浪费率控制在合理范围内");
                }
                if (lossRate < 5 && wasteRate == 0) {
                    suggestions.add("🎉 商品管理优秀，损耗和废弃都控制得很好");
                }
                aiResult.put("warnings", warnings);
            }

            if (lossRate > 5) {
                suggestions.add("💡 建议加强员工培训，减少操作损耗");
            }

            Map<String, Object> map = new HashMap<>();
            map.put("disGoodsId", disGoodsId);
            if (goods.getGbDgDistributerId() != null) {
                map.put("disId", goods.getGbDgDistributerId());
            }
            log.debug("weiidmappa{}", map);
            Double weightTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(map);
            Double aDouble = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockService.queryDepGoodsRestTotal(map));

            aiResult.put("suggestions", suggestions);
            aiResult.put("type", type);
            aiResult.put("averageSubtotal", new BigDecimal(aSubtotal).setScale(1, RoundingMode.HALF_UP));
            aiResult.put("averageWeight", new BigDecimal(aWeight).setScale(2, RoundingMode.HALF_UP));
            aiResult.put("lossRate", new BigDecimal(lossRate).setScale(1, RoundingMode.HALF_UP));
            aiResult.put("wasteRate", new BigDecimal(wasteRate).setScale(1, RoundingMode.HALF_UP));
            aiResult.put("totalWasteRate", new BigDecimal(totalWasteRate).setScale(1, RoundingMode.HALF_UP));
            aiResult.put("stockWeight", new BigDecimal(GbDepartmentGoodsStockReduceSupport.nzD(weightTotal)).setScale(1, RoundingMode.HALF_UP));
            aiResult.put("stockSubtotal", new BigDecimal(aDouble).setScale(1, RoundingMode.HALF_UP));
            aiResult.put("totalCostWeight", new BigDecimal(weightTotalPW).setScale(1, RoundingMode.HALF_UP));
            aiResult.put("totalCostSubtotal", new BigDecimal(weightTotalP).setScale(1, RoundingMode.HALF_UP));

            return aiResult;

        } catch (Exception e) {
            log.warn("总体AI分析出错", e);
            return null;
        }
    }
}
