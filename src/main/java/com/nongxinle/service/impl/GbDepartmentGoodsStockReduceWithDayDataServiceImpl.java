package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceWithDayDataService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import com.nongxinle.utils.GbDepFoodSalesMetricsSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
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
    private final GbDishCostAnalysisService gbDishCostAnalysisService;

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
        List<Map<String, Object>> producelist = new ArrayList<>();
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
            LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> anyIng = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                    .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                    .ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, startDate)
                    .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, stopDate);
            if (searchDepId != null && !"-1".equals(searchDepId)) {
                anyIng.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, Integer.valueOf(searchDepId));
            }
            if (gbDepFoodGoodsSalesService.count(anyIng) == 0) {
                throw new IllegalArgumentException("没有数据");
            }
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
        int spanDayCount = howManyDaysInPeriod > 0 ? howManyDaysInPeriod + 1 : 1;
        for (int i = 0; i < spanDayCount; i++) {
                String whichDay = i == 0 ? startDate : afterWhatDay(startDate, i);

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

                double dayFoodIngredient = sumFoodGoodsSalesIngredient(disGoodsId, whichDay, searchDepId);
                boolean hasOutbound123 = integer != null && integer > 0;
                boolean hasDishIngredientSales = dayFoodIngredient > 1e-9;
                if (!hasOutbound123 && !hasDishIngredientSales) {
                    continue;
                }

                Map<String, Object> mapPro = new HashMap<>();
                mapPro.put("date", whichDay);
                mapPro.put("value", String.format("%.1f", produceSubtotal));
                producelist.add(mapPro);

                foodSalesIngredientPeriod += dayFoodIngredient;

                Map<String, Object> mapLoss = new HashMap<>();
                mapLoss.put("date", whichDay);
                mapLoss.put("value", String.format("%.1f", lossSubtotal));
                losslist.add(mapLoss);

                Map<String, Object> mapWaste = new HashMap<>();
                mapWaste.put("date", whichDay);
                mapWaste.put("value", String.format("%.1f", wasteSubtotal));
                wastelist.add(mapWaste);

                Map<String, Object> mapReduce = new LinkedHashMap<>();
                mapReduce.put("date", whichDay);
                LinkedHashMap<Integer, GbDepartmentEntity> dayDepUnique = new LinkedHashMap<>();
                if (integer != null && integer > 0) {
                    Map<String, Object> mapForReduceList = new HashMap<>(map);
                    mapForReduceList.remove("depId");
                    mapForReduceList.remove("depType");
                    log.debug("kanakndninteterere{}", mapForReduceList);
                    Integer integerD = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mapForReduceList);
                    if (integerD != null && integerD > 0) {
                        List<GbDepartmentEntity> gbDepartmentEntities =
                                gbDepartmentGoodsStockReduceService.queryReduceDepartment(map);
                        for (GbDepartmentEntity d : gbDepartmentEntities) {
                            if (d.getGbDepartmentId() != null) {
                                dayDepUnique.put(d.getGbDepartmentId(), d);
                            }
                        }
                    }
                }
                if (hasDishIngredientSales) {
                    for (GbDepartmentEntity d : departmentEntitiesFromIngredientSales(disGoodsId, whichDay, searchDepId)) {
                        if (d != null && d.getGbDepartmentId() != null) {
                            dayDepUnique.putIfAbsent(d.getGbDepartmentId(), d);
                        }
                    }
                }
                List<GbDepartmentEntity> dayDepArr = dayDepUnique.isEmpty()
                        ? new ArrayList<>()
                        : getDepReduceDataAll(new ArrayList<>(dayDepUnique.values()), map);
                mapReduce.put("arr", dayDepArr);
                listItem.add(mapReduce);
        }

        mapResult.put("itemList", listItem);
        mapResult.put("produceList", producelist);
        Map<String, Object> ingredientTheoryForAi = new LinkedHashMap<>();
        mapResult.put("foodSalesStats", buildFoodSalesStatsEmbedded(disGoodsId, disId, startDate, stopDate, searchDepId,
                foodSalesIngredientPeriod, ingredientTheoryForAi));

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

        int spanDays = howManyDaysInPeriod > 0 ? howManyDaysInPeriod + 1 : 1;
        Map<String, Object> overallAiAnalysis = generateOverallAiAnalysis(
                weightTotalS, weightTotalTL, weightTotalTW, disGoodsId, spanDays, weightTotalPW, weightTotalP);
        Map<String, Object> aiMerge = new HashMap<>();
        if (overallAiAnalysis != null) {
            aiMerge.putAll(overallAiAnalysis);
        }
        if (!ingredientTheoryForAi.isEmpty()) {
            aiMerge.putAll(ingredientTheoryForAi);
        }
        if (!aiMerge.isEmpty()) {
            mapResult.put("aiAnalysis", aiMerge);
        }

        return mapResult;
    }



    private List<GbDepartmentEntity> getDepReduceDataAll(
            List<GbDepartmentEntity> gbDepartmentEntities, Map<String, Object> dayMap) {
        for (GbDepartmentEntity departmentEntity : gbDepartmentEntities) {
            Map<String, Object> depMap = new HashMap<>(dayMap);
            depMap.remove("depType");
            depMap.put("depId", departmentEntity.getGbDepartmentId());

            applyDepartmentDayOutboundAndDishStats(departmentEntity, depMap);

            List<GbDepartmentGoodsStockReduceEntity> reduceRows =
                    gbDepartmentGoodsStockReduceService.queryStockReduceListByParams(depMap);
            if (reduceRows == null || reduceRows.isEmpty()) {
                departmentEntity.setDepartmentGoodsDailyEntity(null);
                departmentEntity.setWasteReduceList(null);
                continue;
            }

            for (GbDepartmentGoodsStockReduceEntity row : reduceRows) {
                GbDepartmentGoodsStockReduceSupport.applyWxTypeAmountFields(row);
            }
            departmentEntity.setWasteReduceList(reduceRows);

            Double produceTotal = gbDepartmentGoodsStockReduceService.queryReduceProduceTotal(depMap);
            Double wasteTotal = gbDepartmentGoodsStockReduceService.queryReduceWasteTotal(depMap);
            Double lossTotal = gbDepartmentGoodsStockReduceService.queryReduceLossTotal(depMap);
            Double returnTotal = gbDepartmentGoodsStockReduceService.queryReduceReturnTotal(depMap);
            Double employeeMealTotal = gbDepartmentGoodsStockReduceService.queryReduceEmployeeMealTotal(depMap);
            double v = GbDepartmentGoodsStockReduceSupport.nzD(produceTotal) + GbDepartmentGoodsStockReduceSupport.nzD(wasteTotal)
                    + GbDepartmentGoodsStockReduceSupport.nzD(lossTotal) + GbDepartmentGoodsStockReduceSupport.nzD(returnTotal)
                    + GbDepartmentGoodsStockReduceSupport.nzD(employeeMealTotal);
            departmentEntity.setDepCostGoodsTotalString(String.format("%.1f", v));

            Double produceTotalWeight = gbDepartmentGoodsStockReduceService.queryReduceProduceWeightTotal(depMap);
            Double wasteTotalWeight = gbDepartmentGoodsStockReduceService.queryReduceWasteWeightTotal(depMap);
            Double lossTotalWeight = gbDepartmentGoodsStockReduceService.queryReduceLossWeightTotal(depMap);
            Double returnTotalWeight = gbDepartmentGoodsStockReduceService.queryReduceReturnWeightTotal(depMap);
            Double employeeMealTotalWeight = gbDepartmentGoodsStockReduceService.queryReduceEmployeeMealWeightTotal(depMap);
            double vW = GbDepartmentGoodsStockReduceSupport.nzD(produceTotalWeight) + GbDepartmentGoodsStockReduceSupport.nzD(wasteTotalWeight)
                    + GbDepartmentGoodsStockReduceSupport.nzD(lossTotalWeight) + GbDepartmentGoodsStockReduceSupport.nzD(returnTotalWeight)
                    + GbDepartmentGoodsStockReduceSupport.nzD(employeeMealTotalWeight);
            departmentEntity.setDepStockWeightTotalString(String.format("%.1f", vW));

            Map<String, Object> weightTotals = gbDepartmentGoodsStockReduceService.queryReduceTypeWeightTotalsByScope(depMap);
            GbDepartmentGoodsDailyEntity daily = new GbDepartmentGoodsDailyEntity();
            daily.setGbDgdGbDepartmentId(departmentEntity.getGbDepartmentId());
            Object disGoodsIdObj = depMap.get("disGoodsId");
            if (disGoodsIdObj instanceof Integer) {
                daily.setGbDgdGbDisGoodsId((Integer) disGoodsIdObj);
            } else if (disGoodsIdObj != null) {
                daily.setGbDgdGbDisGoodsId(Integer.valueOf(disGoodsIdObj.toString()));
            }
            Object dateVal = depMap.get("date");
            if (dateVal != null) {
                daily.setGbDgdDate(dateVal.toString());
            } else {
                Object start = depMap.get("startDate");
                Object stop = depMap.get("stopDate");
                if (start != null && stop != null && start.equals(stop)) {
                    daily.setGbDgdDate(start.toString());
                }
            }
            daily.setGbDgdProduceWeight(String.format("%.1f",
                    GbDepartmentGoodsStockReduceSupport.toDouble(weightTotals != null ? weightTotals.get("produceWeight") : null)));
            daily.setGbDgdLossWeight(String.format("%.1f",
                    GbDepartmentGoodsStockReduceSupport.toDouble(weightTotals != null ? weightTotals.get("lossWeight") : null)));
            daily.setGbDgdWasteWeight(String.format("%.1f",
                    GbDepartmentGoodsStockReduceSupport.toDouble(weightTotals != null ? weightTotals.get("wasteWeight") : null)));
            daily.setGbDgdReturnWeight(String.format("%.1f",
                    GbDepartmentGoodsStockReduceSupport.toDouble(weightTotals != null ? weightTotals.get("returnWeight") : null)));
            daily.setGbDgdEmployeeMealWeight(String.format("%.1f",
                    GbDepartmentGoodsStockReduceSupport.toDouble(weightTotals != null
                            ? weightTotals.get(GbDepartmentGoodsStockReduceSupport.KEY_EMPLOYEE_MEAL_WEIGHT) : null)));
            daily.setGbDgdProduceSubtotal(String.format("%.1f", GbDepartmentGoodsStockReduceSupport.nzD(produceTotal)));
            daily.setGbDgdWasteSubtotal(String.format("%.1f", GbDepartmentGoodsStockReduceSupport.nzD(wasteTotal)));
            daily.setGbDgdLossSubtotal(String.format("%.1f", GbDepartmentGoodsStockReduceSupport.nzD(lossTotal)));
            daily.setGbDgdReturnSubtotal(String.format("%.1f", GbDepartmentGoodsStockReduceSupport.nzD(returnTotal)));
            daily.setGbDgdEmployeeMealSubtotal(String.format("%.1f", GbDepartmentGoodsStockReduceSupport.nzD(employeeMealTotal)));
            departmentEntity.setDepartmentGoodsDailyEntity(daily);
        }
        return gbDepartmentEntities;
    }

    /** itemList.arr：部门当日出库 1+2+3 重量/金额 + 当日本菜配方理论与毛利贡献（按部门 scope）。 */
    private void applyDepartmentDayOutboundAndDishStats(GbDepartmentEntity departmentEntity, Map<String, Object> depMap) {
        Map<String, Object> p123 = new HashMap<>(depMap);
        p123.remove("type");
        p123.put("types", Arrays.asList(
                GbConstants.StockReduceType.PRODUCTION,
                GbConstants.StockReduceType.WASTE,
                GbConstants.StockReduceType.LOSS));
        Double w123 = gbDepartmentGoodsStockReduceService.queryReduceWeightSum(p123);
        departmentEntity.setDayOutbound123Weight(
                BigDecimal.valueOf(GbDepartmentGoodsStockReduceSupport.nzD(w123))
                        .setScale(2, RoundingMode.HALF_UP).toPlainString());
        Double s123 = gbDepartmentGoodsStockReduceService.queryReduceCostSubtotal(p123);
        departmentEntity.setDayOutbound123Subtotal(
                BigDecimal.valueOf(GbDepartmentGoodsStockReduceSupport.nzD(s123))
                        .setScale(2, RoundingMode.HALF_UP).toPlainString());

        Integer disId = null;
        Object disIdObj = depMap.get("disId");
        if (disIdObj instanceof Integer) {
            disId = (Integer) disIdObj;
        } else if (disIdObj != null) {
            disId = Integer.valueOf(disIdObj.toString());
        }
        Integer disGoodsId = null;
        Object disGoodsIdObj = depMap.get("disGoodsId");
        if (disGoodsIdObj instanceof Integer) {
            disGoodsId = (Integer) disGoodsIdObj;
        } else if (disGoodsIdObj != null) {
            disGoodsId = Integer.valueOf(disGoodsIdObj.toString());
        }
        String dayStr = null;
        Object dateVal = depMap.get("date");
        if (dateVal != null) {
            dayStr = dateVal.toString();
        } else {
            Object start = depMap.get("startDate");
            Object stop = depMap.get("stopDate");
            if (start != null && stop != null && start.equals(stop)) {
                dayStr = start.toString();
            }
        }
        if (dayStr != null && disId != null && disGoodsId != null && departmentEntity.getGbDepartmentId() != null) {
            Map<String, Object> dish = gbDishCostAnalysisService.summarizeDisGoodsDayForReduceCurve(
                    dayStr, disId, disGoodsId, String.valueOf(departmentEntity.getGbDepartmentId()));
            Object th = dish != null ? dish.get("theoryOutboundQty") : null;
            Object gp = dish != null ? dish.get("grossProfitContributionTotal") : null;
            departmentEntity.setDayTheoryOutboundQty(th != null ? th.toString() : "0.00");
            departmentEntity.setDayGrossProfitContributionTotal(gp != null ? gp.toString() : "0.00");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> salesRows =
                    dish != null ? (List<Map<String, Object>>) dish.get("dishIngredientDayBreakdown") : null;
            departmentEntity.setDayDishIngredientSales(
                    salesRows == null ? new ArrayList<>() : new ArrayList<>(salesRows));
        } else {
            departmentEntity.setDayTheoryOutboundQty("0.00");
            departmentEntity.setDayGrossProfitContributionTotal("0.00");
            departmentEntity.setDayDishIngredientSales(new ArrayList<>());
        }
    }

    /** 某日该原料在 gb_dep_food_goods_sales 中出现过的门店（出库为 0 时仍要能展示菜品销售拆分）。 */
    private List<GbDepartmentEntity> departmentEntitiesFromIngredientSales(Integer disGoodsId, String fullDate,
            String searchDepId) {
        LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, fullDate);
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            w.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, Integer.valueOf(searchDepId));
        }
        LinkedHashSet<Integer> depIds = new LinkedHashSet<>();
        for (GbDepFoodGoodsSalesEntity row : gbDepFoodGoodsSalesService.list(w)) {
            if (row.getGbDfgsDepId() != null) {
                depIds.add(row.getGbDfgsDepId());
            }
        }
        List<GbDepartmentEntity> out = new ArrayList<>();
        for (Integer depId : depIds) {
            GbDepartmentEntity dep = gbDepartmentService.getById(depId);
            if (dep != null) {
                out.add(dep);
            }
        }
        return out;
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
            String stopDate, String searchDepId, double ingredientPeriodTotal,
            Map<String, Object> ingredientMetricsForAi) {
        log.debug("[DEBUG][foodSalesStats] buildFoodSalesStatsEmbedded 入参 disGoodsId={} disId={} startDate={} stopDate={} searchDepId={} ingredientPeriodTotal={}",
                disGoodsId, disId, startDate, stopDate, searchDepId, ingredientPeriodTotal);

        LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                .ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, startDate)
                .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, stopDate);
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            w.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, Integer.valueOf(searchDepId));
        }
        List<GbDepFoodGoodsSalesEntity> ingRows = gbDepFoodGoodsSalesService.list(w);

        Map<String, Object> period = new HashMap<>();
        period.put("startDate", startDate);
        period.put("stopDate", stopDate);

        Map<String, Object> filters = new HashMap<>();
        filters.put("disGoodsId", disGoodsId);
        filters.put("searchDepId", searchDepId);

        Map<String, Object> recipeAndTheory = buildRecipeTheoreticalAndDepDishSales(disGoodsId, disId, startDate,
                stopDate, searchDepId, ingredientPeriodTotal, ingredientMetricsForAi);

        Map<String, Object> root = new HashMap<>();
        root.put("depFoodGoodsSalesRowCountInPeriod", ingRows.size());
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
            String startDate, String stopDate, String searchDepId, double actualIngredientTotal,
            Map<String, Object> ingredientMetricsForAi) {
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
            out.put("supportedDishes", new ArrayList<>());
            Map<String, Object> tv = new LinkedHashMap<>();
            tv.put("theoreticalIngredientTotal", 0.0);
            tv.put("actualIngredientTotal", round1(actualIngredientTotal));
            tv.put("ingredientGapActualMinusTheoretical", round1(actualIngredientTotal));
            tv.put("ingredientGapPercentOfTheoretical", null);
            if (ingredientMetricsForAi != null) {
                ingredientMetricsForAi.putAll(tv);
            }
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
        Map<String, BigDecimal> consumptionQtyByDepFood = new HashMap<>();
        for (GbDepFoodSalesEntity s : salesRows) {
            if (s.getGbDfsDepId() == null || s.getGbDfsFoodId() == null) {
                continue;
            }
            String key = s.getGbDfsDepId() + "_" + s.getGbDfsFoodId();
            if (GbDepFoodSalesMetricsSupport.countsAsOperationalSales(s)) {
                soldQtyByDepFood.merge(key, GbDepFoodSalesMetricsSupport.operationalSalesQty(s), BigDecimal::add);
            }
            if (GbDepFoodSalesMetricsSupport.countsAsIngredientConsumption(s)) {
                consumptionQtyByDepFood.merge(key, GbDepFoodSalesMetricsSupport.totalConsumptionQty(s), BigDecimal::add);
            }
        }

        BigDecimal theoreticalTotal = BigDecimal.ZERO;
        List<Map<String, Object>> depDishRows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : soldQtyByDepFood.entrySet()) {
            String[] parts = e.getKey().split("_", 2);
            int depId = Integer.parseInt(parts[0]);
            int foodId = Integer.parseInt(parts[1]);
            BigDecimal soldQty = e.getValue();
            BigDecimal per = recipeAmountThisGoodByFood.getOrDefault(foodId, BigDecimal.ZERO);
            BigDecimal consumptionQty = consumptionQtyByDepFood.getOrDefault(e.getKey(), soldQty);
            BigDecimal theo = consumptionQty.multiply(per);

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
        for (Map.Entry<String, BigDecimal> e : consumptionQtyByDepFood.entrySet()) {
            String[] parts = e.getKey().split("_", 2);
            int foodId = Integer.parseInt(parts[1]);
            BigDecimal per = recipeAmountThisGoodByFood.getOrDefault(foodId, BigDecimal.ZERO);
            theoreticalTotal = theoreticalTotal.add(e.getValue().multiply(per));
        }
        depDishRows.sort(Comparator
                .comparing((Map<String, Object> m) -> (String) m.getOrDefault("depName", ""))
                .thenComparing(m -> (Integer) m.get("gbDfsFoodId")));
        out.put("periodSalesByDepAndDish", depDishRows);
        out.put("recipeDishesUsingThisGood", recipeDishes);
        log.debug("[DEBUG][foodSalesStats] periodSalesByDepAndDish 条数={} soldQtyByDepFood.keys={}",
                depDishRows.size(), soldQtyByDepFood.keySet());

        Map<Integer, BigDecimal> operationalQtyByFoodId = new HashMap<>();
        Map<Integer, BigDecimal> consumptionQtyByFoodId = new HashMap<>();
        for (GbDepFoodSalesEntity s : salesRows) {
            if (s.getGbDfsFoodId() == null) {
                continue;
            }
            if (GbDepFoodSalesMetricsSupport.countsAsOperationalSales(s)) {
                operationalQtyByFoodId.merge(s.getGbDfsFoodId(),
                        GbDepFoodSalesMetricsSupport.operationalSalesQty(s), BigDecimal::add);
            }
            if (GbDepFoodSalesMetricsSupport.countsAsIngredientConsumption(s)) {
                consumptionQtyByFoodId.merge(s.getGbDfsFoodId(),
                        GbDepFoodSalesMetricsSupport.totalConsumptionQty(s), BigDecimal::add);
            }
        }
        Set<Integer> saleIds = salesRows.stream()
                .map(GbDepFoodSalesEntity::getGbDepFoodSalesId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, Integer> foodSaleIdToFoodId = new HashMap<>();
        for (GbDepFoodSalesEntity s : salesRows) {
            if (s.getGbDepFoodSalesId() != null && s.getGbDfsFoodId() != null) {
                foodSaleIdToFoodId.put(s.getGbDepFoodSalesId(), s.getGbDfsFoodId());
            }
        }
        Map<Integer, BigDecimal> ingredientByFoodId = new HashMap<>();
        if (!saleIds.isEmpty()) {
            LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> iw = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                    .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                    .ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, startDate)
                    .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, stopDate)
                    .in(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, saleIds);
            if (searchDepId != null && !"-1".equals(searchDepId)) {
                iw.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, Integer.valueOf(searchDepId));
            }
            for (GbDepFoodGoodsSalesEntity r : gbDepFoodGoodsSalesService.list(iw)) {
                Integer fsId = r.getGbDfgsFoodSalesId();
                Integer fid = fsId == null ? null : foodSaleIdToFoodId.get(fsId);
                if (fid == null) {
                    continue;
                }
                ingredientByFoodId.merge(fid,
                        GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(r.getGbDfgsGoodsAmount()),
                        BigDecimal::add);
            }
        }
        List<Integer> orderedFoodIds = new ArrayList<>(recipeAmountThisGoodByFood.keySet());
        orderedFoodIds.sort((a, b) -> operationalQtyByFoodId.getOrDefault(b, BigDecimal.ZERO)
                .compareTo(operationalQtyByFoodId.getOrDefault(a, BigDecimal.ZERO)));
        List<Map<String, Object>> supportedDishes = new ArrayList<>();
        for (Integer fid : orderedFoodIds) {
            BigDecimal dishU = recipeAmountThisGoodByFood.getOrDefault(fid, BigDecimal.ZERO);
            BigDecimal salesPortions = operationalQtyByFoodId.getOrDefault(fid, BigDecimal.ZERO);
            BigDecimal consumptionPortions = consumptionQtyByFoodId.getOrDefault(fid, BigDecimal.ZERO);
            BigDecimal ingQty = ingredientByFoodId.getOrDefault(fid, BigDecimal.ZERO);
            BigDecimal theoryRecipe = dishU.multiply(consumptionPortions);
            GbDistributerFoodEntity foodEntity = gbDistributerFoodService.queryObject(fid);
            String dishName = foodEntity != null && foodEntity.getGbDfFoodName() != null
                    ? foodEntity.getGbDfFoodName().trim()
                    : "";
            Map<String, Object> sRow = new LinkedHashMap<>();
            sRow.put("dishId", fid);
            sRow.put("dishName", dishName);
            sRow.put("recipeUnitPerDish", supportedIngredientQtyTwoDecimals(dishU));
            sRow.put("salesPortions", supportedSalesPortionsString(salesPortions));
            sRow.put("theoryUsage", supportedIngredientQtyTwoDecimals(theoryRecipe));
            sRow.put("salesUsageFromOrders", supportedIngredientQtyTwoDecimals(ingQty));
            sRow.put("actualUsage", supportedIngredientQtyTwoDecimals(ingQty));
            sRow.put("diffUsage", supportedIngredientQtyTwoDecimals(ingQty.subtract(theoryRecipe)));
            supportedDishes.add(sRow);
        }
        out.put("supportedDishes", supportedDishes);

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
        if (ingredientMetricsForAi != null) {
            ingredientMetricsForAi.putAll(tv);
        }
        return out;
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**与同 {@link com.nongxinle.service.impl.GbDishCostAnalysisServiceImpl#buildOutboundIngredientAnalysisReport} 中 {@code supportedDishes} 数量格式一致（两位小数）。 */
    private static String supportedIngredientQtyTwoDecimals(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 与配料报表 {@code salesPortions} 一致：整数份字符串。 */
    private static String supportedSalesPortionsString(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP).toPlainString();
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
