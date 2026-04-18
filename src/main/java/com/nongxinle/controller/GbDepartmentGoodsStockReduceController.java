package com.nongxinle.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.entity.*;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepartmentGoodsStockLedgerService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerFoodGoodsService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.nongxinle.utils.DateUtils.afterWhatDay;
import static com.nongxinle.utils.DateUtils.getHowManyDaysInPeriod;
import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;

/**
 * 部门库存损耗相关接口。
 * <p>历史完整实现参考 {@code reference/GbDepartmentGoodsStockReduceController.legacy.txt}。</p>
 */
@RestController
@RequestMapping("gbdepartmentgoodsstockreduce")
public class GbDepartmentGoodsStockReduceController {

    @Autowired
    private GbDepartmentGoodsStockLedgerService gbDepartmentGoodsStockLedgerService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    @Autowired
    private GbDepFoodSalesService gbDepFoodSalesService;
    @Autowired
    private GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    @Autowired
    private GbDistributerFoodService gbDistributerFoodService;
    @Autowired
    private GbDepartmentService gbDepartmentService;




    @RequestMapping(value = "/getGoodsReduceWithDayData", method = RequestMethod.POST)
    @ResponseBody
    public R getGoodsReduceWithDayData(String startDate, String stopDate, Integer disGoodsId,
                                       String searchDepId) {
        GbDistributerGoodsEntity disGoods = gbDistributerGoodsService.queryObject(disGoodsId);
        if (disGoods == null) {
            return R.error(-1, "商品不存在");
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

        Map<String, Object> map0 = buildReduceParamsForGoodsDay(disId, disGoodsId, startDate, stopDate,
                howManyDaysInPeriod, searchDepId);
        map0.put("types", Arrays.asList(
                GbConstants.StockReduceType.PRODUCTION,
                GbConstants.StockReduceType.WASTE,
                GbConstants.StockReduceType.LOSS));
        Integer integer1 = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(map0);
        if (integer1 == null || integer1 == 0) {
            return R.error(-1, "没有数据");
        }
        map0.remove("types");

        Double weightTotalTL = nzD(gbDepartmentGoodsStockReduceService.queryReduceCostSubtotal(
                withReduceType(map0, GbConstants.StockReduceType.LOSS)));
        Double weightTotalTLW = nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                withReduceType(map0, GbConstants.StockReduceType.LOSS)));
        Double weightTotalTW = nzD(gbDepartmentGoodsStockReduceService.queryReduceCostSubtotal(
                withReduceType(map0, GbConstants.StockReduceType.WASTE)));
        Double weightTotalTWW = nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                withReduceType(map0, GbConstants.StockReduceType.WASTE)));
        Double weightTotalS = nzD(gbDepartmentGoodsStockReduceService.queryReduceCostSubtotal(
                withReduceType(map0, GbConstants.StockReduceType.PRODUCTION)));
        Double weightTotalSW = nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                withReduceType(map0, GbConstants.StockReduceType.PRODUCTION)));

        System.out.println("susbbddmap" + map0);
        double subTotal = 0.0;
        double weightTotal = 0.0;
        Map<String, Object> purMap = new HashMap<>(map0);
        purMap.remove("type");
        purMap.put("useStockFinishDate", Boolean.TRUE);
        Integer integer11 = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(purMap);
        if (integer11 != null && integer11 > 0) {
            subTotal = nzD(gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(purMap));
            weightTotal = nzD(gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(purMap));
        }

        double weightTotalP = weightTotalS + weightTotalTL + weightTotalTW;
        double weightTotalPW = weightTotalSW + weightTotalTLW + weightTotalTWW;

        double foodSalesIngredientPeriod = 0.0;
        if (howManyDaysInPeriod > 0) {
            for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                String whichDay = i == 0 ? startDate : afterWhatDay(startDate, i);
                String substring = whichDay.substring(8, 10);
                dateList.add(substring);

                Map<String, Object> map = buildReduceParamsForGoodsDay(disId, disGoodsId, whichDay, whichDay,
                        0, searchDepId);
                Map<String, Object> countMap = new HashMap<>(map);
                countMap.put("types", Arrays.asList(
                        GbConstants.StockReduceType.PRODUCTION,
                        GbConstants.StockReduceType.WASTE,
                        GbConstants.StockReduceType.LOSS));
                Integer integer = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(countMap);
                // 按日曲线：生产 / 损耗 / 废弃均为「数量（重量汇总）」，与 queryReduceWeightSum 一致，非金额
                double weightDayTotal = 0.0;
                double produceSubtotal = 0.0;
                double lossSubtotal = 0.0;
                double wasteSubtotal = 0.0;
                if (integer != null && integer > 0) {
                    System.out.println("kaankanank25hao" + map);
                    lossSubtotal = nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                            withReduceType(map, GbConstants.StockReduceType.LOSS)));
                    wasteSubtotal = nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                            withReduceType(map, GbConstants.StockReduceType.WASTE)));
                    produceSubtotal = nzD(gbDepartmentGoodsStockReduceService.queryReduceWeightSum(
                            withReduceType(map, GbConstants.StockReduceType.PRODUCTION)));
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
                    System.out.println("kanakndninteterere" + mapForReduceList);
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
                BigDecimal.valueOf(foodSalesIngredientPeriod).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("foodSalesStats", buildFoodSalesStatsEmbedded(disGoodsId, disId, startDate, stopDate, searchDepId,
                foodSalesIngredientList, foodSalesIngredientPeriod));

        mapResult.put("subTotal", BigDecimal.valueOf(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("weightTotal", BigDecimal.valueOf(weightTotal).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("lossList", losslist);
        mapResult.put("wasteList", wastelist);
        mapResult.put("oneTotal", BigDecimal.valueOf(weightTotalP).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("oneTotalWeight", BigDecimal.valueOf(weightTotalPW).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("salesTotal", BigDecimal.valueOf(weightTotalS).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("salesTotalWeight", BigDecimal.valueOf(weightTotalSW).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("lossTotal", BigDecimal.valueOf(weightTotalTL).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("lossTotalWeight", BigDecimal.valueOf(weightTotalTLW).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("wasteTotal", BigDecimal.valueOf(weightTotalTW).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("wasteTotalWeight", BigDecimal.valueOf(weightTotalTWW).setScale(1, BigDecimal.ROUND_HALF_UP));
        mapResult.put("date", dateList);

        int spanDays = howManyDaysInPeriod > 0 ? howManyDaysInPeriod + 1 : 1;
        Map<String, Object> overallAiAnalysis = generateOverallAiAnalysis(
                weightTotalS, weightTotalTL, weightTotalTW, disGoodsId, spanDays, weightTotalPW, weightTotalP);
        if (overallAiAnalysis != null) {
            mapResult.put("aiAnalysis", overallAiAnalysis);
        }

        return R.ok().put("data", mapResult);
    }

    private static Map<String, Object> buildReduceParamsForGoodsDay(Integer disId, Integer disGoodsId,
            String startDate, String stopDate, int howManyDaysInPeriod, String searchDepId) {
        Map<String, Object> m = new HashMap<>();
        m.put("disId", disId);
        m.put("disGoodsId", disGoodsId);
        if (howManyDaysInPeriod > 0) {
            m.put("startDate", startDate);
            m.put("stopDate", stopDate);
        } else {
            m.put("date", startDate);
        }
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            m.put("depId", Integer.valueOf(searchDepId));
        } else {
            m.put("depType", getGbDepartmentTypeMendian());
        }
        return m;
    }

    /**
     * 采购分析按日曲线：从 mapDay 抽出与 {@code gb_department_goods_stock_reduce} 统计一致的参数（见 Mapper 中 queryReduceTypeCount 条件）。
     */
    private static Map<String, Object> buildReduceParamsFromFenxiMapDay(Map<String, Object> mapDay) {
        Map<String, Object> p = new HashMap<>();
        Object disId = mapDay.get("disId");
        if (disId != null) {
            p.put("disId", disId);
        }
        Object depId = mapDay.get("depId");
        if (depId != null) {
            p.put("depId", depId);
        }
        Object depType = mapDay.get("depType");
        if (depType != null) {
            p.put("depType", depType);
        }
        Object disGoodsId = mapDay.get("disGoodsId");
        if (disGoodsId != null) {
            p.put("disGoodsId", disGoodsId);
        }
        Object date = mapDay.get("date");
        if (date != null) {
            p.put("date", date);
        }
        Object startDate = mapDay.get("startDate");
        if (startDate != null) {
            p.put("startDate", startDate);
        }
        Object stopDate = mapDay.get("stopDate");
        if (stopDate != null) {
            p.put("stopDate", stopDate);
        }
        Object disGoodsGreatId = mapDay.get("disGoodsGreatId");
        if (disGoodsGreatId != null) {
            p.put("disGoodsGreatId", disGoodsGreatId);
        }
        return p;
    }

    private static Map<String, Object> withReduceType(Map<String, Object> base, Integer type) {
        Map<String, Object> p = new HashMap<>(base);
        p.remove("types");
        p.put("type", type);
        return p;
    }

    private static double nzD(Double d) {
        return d == null ? 0.0 : d;
    }

    /**
     * 采购分析：按 disId 与配方关联；汇总名称与总份数；并按每道菜给出销售份数与本原料（disGoodsId）在 gb_dep_food_goods_sales 中的消耗合计。
     */
    private Map<String, Object> resolveFenxiLinkedDishSalesSummary(Integer disGoodsId, Integer disId,
            String startDate, String stopDate) {
        Map<String, Object> out = new HashMap<>();
        out.put("dishFoodNames", "");
        out.put("dishSalesQtyTotal", 0.0);
        out.put("linkedDishList", new ArrayList<Map<String, Object>>());
        if (disGoodsId == null || startDate == null || stopDate == null) {
            return out;
        }
        List<GbDistributerFoodGoodsEntity> lines =
                gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, disId);
        if ((lines == null || lines.isEmpty()) && disId != null) {
            lines = gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, null);
        }
        if (lines == null) {
            lines = Collections.emptyList();
        }
        LinkedHashSet<Integer> foodIds = new LinkedHashSet<>();
        for (GbDistributerFoodGoodsEntity line : lines) {
            if (!isActiveFoodGoodsLine(line) || line.getGbDfgFoodId() == null) {
                continue;
            }
            foodIds.add(line.getGbDfgFoodId());
        }
        StringBuilder names = new StringBuilder();
        for (Integer fid : foodIds) {
            GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(fid);
            if (food != null && food.getGbDfFoodName() != null && !food.getGbDfFoodName().trim().isEmpty()) {
                if (names.length() > 0) {
                    names.append('、');
                }
                names.append(food.getGbDfFoodName().trim());
            }
        }
        out.put("dishFoodNames", names.toString());

        if (foodIds.isEmpty()) {
            return out;
        }

        LambdaQueryWrapper<GbDepFoodSalesEntity> sq = new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                .in(GbDepFoodSalesEntity::getGbDfsFoodId, foodIds)
                .ge(GbDepFoodSalesEntity::getGbDfsFullDate, startDate)
                .le(GbDepFoodSalesEntity::getGbDfsFullDate, stopDate);
        if (disId != null) {
            sq.eq(GbDepFoodSalesEntity::getGbDfsDistributerId, disId);
        }
        List<GbDepFoodSalesEntity> salesRows = gbDepFoodSalesService.list(sq);

        Map<Integer, Integer> foodSaleIdToFoodId = new HashMap<>();
        Map<Integer, BigDecimal> dishQtyByFoodId = new HashMap<>();
        BigDecimal dishQtyTotal = BigDecimal.ZERO;
        for (GbDepFoodSalesEntity s : salesRows) {
            Integer fsId = s.getGbDepFoodSalesId();
            Integer fid = s.getGbDfsFoodId();
            if (fsId == null || fid == null) {
                continue;
            }
            foodSaleIdToFoodId.put(fsId, fid);
            BigDecimal amt = parseGoodsAmountString(s.getGbDfsAmount());
            dishQtyByFoodId.merge(fid, amt, BigDecimal::add);
            dishQtyTotal = dishQtyTotal.add(amt);
        }
        out.put("dishSalesQtyTotal", dishQtyTotal.setScale(1, RoundingMode.HALF_UP).doubleValue());

        Set<Integer> relevantFoodSaleIds = foodSaleIdToFoodId.keySet();
        Map<Integer, BigDecimal> ingredientByFoodId = new HashMap<>();
        if (!relevantFoodSaleIds.isEmpty()) {
            LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> iw = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                    .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                    .ge(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, startDate)
                    .le(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, stopDate)
                    .in(GbDepFoodGoodsSalesEntity::getGbDfgsFoodSalesId, relevantFoodSaleIds);
            for (GbDepFoodGoodsSalesEntity r : gbDepFoodGoodsSalesService.list(iw)) {
                Integer fsId = r.getGbDfgsFoodSalesId();
                if (fsId == null) {
                    continue;
                }
                Integer fid = foodSaleIdToFoodId.get(fsId);
                if (fid == null) {
                    continue;
                }
                ingredientByFoodId.merge(fid, parseGoodsAmountString(r.getGbDfgsGoodsAmount()), BigDecimal::add);
            }
        }

        List<Map<String, Object>> linkedDishList = new ArrayList<>();
        for (Integer fid : foodIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dishFoodId", fid);
            GbDistributerFoodEntity food = gbDistributerFoodService.queryObject(fid);
            String dishName = food != null && food.getGbDfFoodName() != null ? food.getGbDfFoodName().trim() : "";
            row.put("dishFoodName", dishName);
            BigDecimal dQty = dishQtyByFoodId.getOrDefault(fid, BigDecimal.ZERO);
            BigDecimal ingQty = ingredientByFoodId.getOrDefault(fid, BigDecimal.ZERO);
            row.put("dishSalesQty", String.format("%.1f", dQty.setScale(1, RoundingMode.HALF_UP).doubleValue()));
            row.put("foodIngredientSalesQty",
                    String.format("%.1f", ingQty.setScale(1, RoundingMode.HALF_UP).doubleValue()));
            linkedDishList.add(row);
        }
        out.put("linkedDishList", linkedDishList);
        return out;
    }

    /**
     * 指定批发商商品、自然日，汇总「菜品销售」写入的 gb_dep_food_goods_sales 消耗量（与 Excel 上传逻辑同源）。
     */
    private double sumFoodGoodsSalesIngredient(Integer disGoodsId, String fullDate, String searchDepId) {
        LambdaQueryWrapper<GbDepFoodGoodsSalesEntity> w = new LambdaQueryWrapper<GbDepFoodGoodsSalesEntity>()
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsDisGoodsId, disGoodsId)
                .eq(GbDepFoodGoodsSalesEntity::getGbDfgsFullDate, fullDate);
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            w.eq(GbDepFoodGoodsSalesEntity::getGbDfgsDepId, Integer.valueOf(searchDepId));
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GbDepFoodGoodsSalesEntity e : gbDepFoodGoodsSalesService.list(w)) {
            sum = sum.add(parseGoodsAmountString(e.getGbDfgsGoodsAmount()));
        }
        return sum.doubleValue();
    }

    private static BigDecimal parseGoodsAmountString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 本商品维度下的「菜品销售 + 菜品带动原料」统计，并入 getGoodsReduceWithDayData。
     * <p>含：菜品侧关联统计、配方与理论/实际对比；按日原料曲线请用顶层 foodSalesIngredientList / foodSalesIngredientTotal。</p>
     */
    private Map<String, Object> buildFoodSalesStatsEmbedded(Integer disGoodsId, Integer disId, String startDate,
            String stopDate, String searchDepId, List<Map<String, Object>> ingredientByDay,
            double ingredientPeriodTotal) {
        System.out.println("[DEBUG][foodSalesStats] buildFoodSalesStatsEmbedded 入参 disGoodsId=" + disGoodsId
                + " disId=" + disId + " startDate=" + startDate + " stopDate=" + stopDate
                + " searchDepId=" + searchDepId + " ingredientByDay.size=" + ingredientByDay.size()
                + " ingredientPeriodTotal=" + ingredientPeriodTotal);

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
                    parseGoodsAmountString(r.getGbDfgsGoodsAmount()), BigDecimal::add);
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
                        row.put("dishQty", parseGoodsAmountString(sale.getGbDfsAmount())
                                .setScale(1, RoundingMode.HALF_UP).doubleValue());
                    }
                    return row;
                })
                .collect(Collectors.toList());

        BigDecimal linkedDishQtyTotal = BigDecimal.ZERO;
        for (Integer fsId : ingredientByFoodSalesId.keySet()) {
            GbDepFoodSalesEntity sale = gbDepFoodSalesService.getById(fsId);
            if (sale != null) {
                linkedDishQtyTotal = linkedDishQtyTotal.add(parseGoodsAmountString(sale.getGbDfsAmount()));
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
        // 按日/合计原料来自菜品：与顶层 foodSalesIngredientList、foodSalesIngredientTotal 一致，不再嵌套重复
        root.put("depFoodGoodsSalesRowCountInPeriod", ingRows.size());
        root.put("dishSales", dishSales);
        root.put("period", period);
        root.put("filters", filters);
        root.putAll(recipeAndTheory);
        return root;
    }

    private static boolean isActiveFoodGoodsLine(GbDistributerFoodGoodsEntity line) {
        return line.getGbDfgStatus() == null || line.getGbDfgStatus() != 0;
    }

    private BigDecimal sumRecipeAmountAllIngredients(Integer foodId) {
        BigDecimal total = BigDecimal.ZERO;
        for (GbDistributerFoodGoodsEntity line : gbDistributerFoodGoodsService.queryFoodGoodsByFoodId(foodId)) {
            if (!isActiveFoodGoodsLine(line)) {
                continue;
            }
            total = total.add(parseGoodsAmountString(line.getGbDfgGoodsAmount()));
        }
        return total;
    }

    /**
     * 配方中用本料的菜品、单菜占比；期内部门+菜销量；理论原料 vs 实际差距。
     */
    private Map<String, Object> buildRecipeTheoreticalAndDepDishSales(Integer disGoodsId, Integer disId,
            String startDate, String stopDate, String searchDepId, double actualIngredientTotal) {
        System.out.println("[DEBUG][foodSalesStats] buildRecipeTheoreticalAndDepDishSales 入参 disGoodsId=" + disGoodsId
                + " disId=" + disId + " startDate=" + startDate + " stopDate=" + stopDate
                + " searchDepId=" + searchDepId + " actualIngredientTotal=" + actualIngredientTotal);

        Map<String, Object> out = new HashMap<>();
        List<GbDistributerFoodGoodsEntity> linesForGood =
                gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, disId);
        System.out.println("[DEBUG][foodSalesStats] queryFoodGoodsByDisGoodsId(disGoodsId=" + disGoodsId
                + ", disId=" + disId + ") 返回行数=" + linesForGood.size());
        if (linesForGood.isEmpty() && disId != null) {
            List<GbDistributerFoodGoodsEntity> fallback =
                    gbDistributerFoodGoodsService.queryFoodGoodsByDisGoodsId(disGoodsId, null);
            System.out.println("[DEBUG][foodSalesStats] 按 disId 无配方行，回退为仅 gb_dfg_dis_goods_id=" + disGoodsId
                    + " 查询，行数=" + fallback.size());
            if (!fallback.isEmpty()) {
                for (int i = 0; i < Math.min(3, fallback.size()); i++) {
                    GbDistributerFoodGoodsEntity x = fallback.get(i);
                    System.out.println("[DEBUG][foodSalesStats] 回退命中样例 gb_dfg_dis_id=" + x.getGbDfgDisId()
                            + " gb_dfg_food_id=" + x.getGbDfgFoodId() + " gb_dfg_dis_goods_id=" + x.getGbDfgDisGoodsId());
                }
            }
            linesForGood = fallback;
        }
        int skippedInactive = 0;
        int skippedNullFoodId = 0;
        int maxSample = Math.min(5, linesForGood.size());
        for (int i = 0; i < maxSample; i++) {
            GbDistributerFoodGoodsEntity x = linesForGood.get(i);
            System.out.println("[DEBUG][foodSalesStats] 配方样例[" + i + "] gbDfgFoodId=" + x.getGbDfgFoodId()
                    + " gbDfgDisGoodsId=" + x.getGbDfgDisGoodsId() + " gbDfgDisId=" + x.getGbDfgDisId()
                    + " gbDfgStatus=" + x.getGbDfgStatus() + " amount=" + x.getGbDfgGoodsAmount());
        }

        Map<Integer, BigDecimal> recipeAmountThisGoodByFood = new HashMap<>();
        for (GbDistributerFoodGoodsEntity line : linesForGood) {
            if (line.getGbDfgFoodId() == null) {
                skippedNullFoodId++;
                continue;
            }
            if (!isActiveFoodGoodsLine(line)) {
                skippedInactive++;
                continue;
            }
            recipeAmountThisGoodByFood.merge(line.getGbDfgFoodId(),
                    parseGoodsAmountString(line.getGbDfgGoodsAmount()), BigDecimal::add);
        }
        System.out.println("[DEBUG][foodSalesStats] 配方行过滤: skippedNullFoodId=" + skippedNullFoodId
                + " skippedInactive=" + skippedInactive
                + " recipeAmountThisGoodByFood.size=" + recipeAmountThisGoodByFood.size()
                + " foodIds=" + recipeAmountThisGoodByFood.keySet());

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
        System.out.println("[DEBUG][foodSalesStats] recipeDishesUsingThisGood 条数=" + recipeDishes.size());

        if (recipeAmountThisGoodByFood.isEmpty()) {
            System.out.println("[DEBUG][foodSalesStats] recipeAmountThisGoodByFood 为空 → recipeDishes 与 periodSales 均为空；"
                    + "请检查 gb_distributer_food_goods 是否有 gb_dfg_dis_goods_id=" + disGoodsId
                    + " 且 gb_dfg_dis_id=" + disId + " 且状态有效");
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
        System.out.println("[DEBUG][foodSalesStats] gb_dep_food_sales 查询条件 foodId in " + recipeAmountThisGoodByFood.keySet()
                + " fullDate [" + startDate + "," + stopDate + "] depFilter=" + searchDepId);
        List<GbDepFoodSalesEntity> salesRows = gbDepFoodSalesService.list(sq);
        System.out.println("[DEBUG][foodSalesStats] gb_dep_food_sales 命中行数=" + salesRows.size());
        for (int i = 0; i < Math.min(5, salesRows.size()); i++) {
            GbDepFoodSalesEntity s = salesRows.get(i);
            System.out.println("[DEBUG][foodSalesStats] 销售样例[" + i + "] id=" + s.getGbDepFoodSalesId()
                    + " depId=" + s.getGbDfsDepId() + " foodId=" + s.getGbDfsFoodId()
                    + " fullDate=" + s.getGbDfsFullDate() + " amount=" + s.getGbDfsAmount());
        }

        Map<String, BigDecimal> soldQtyByDepFood = new HashMap<>();
        for (GbDepFoodSalesEntity s : salesRows) {
            if (s.getGbDfsDepId() == null || s.getGbDfsFoodId() == null) {
                continue;
            }
            String key = s.getGbDfsDepId() + "_" + s.getGbDfsFoodId();
            soldQtyByDepFood.merge(key, parseGoodsAmountString(s.getGbDfsAmount()), BigDecimal::add);
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
        System.out.println("[DEBUG][foodSalesStats] periodSalesByDepAndDish 条数=" + depDishRows.size()
                + " soldQtyByDepFood.keys=" + soldQtyByDepFood.keySet());

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


    /**
     * 生成总体AI分析 - 基于整个统计周期的数据
     */
    private Map<String, Object> generateOverallAiAnalysis(Double totalProduce, Double totalLoss, Double totalWaste,
                                                          Integer disGoodsId, int spanDays, double weightTotalPW,
                                                          double weightTotalP) {
        try {
            Map<String, Object> aiResult = new HashMap<>();
            int days = Math.max(1, spanDays);

            // 1. 基础数据计算
            double totalUsage = totalProduce + totalLoss + totalWaste; // 实际总使用量
            double aWeight = weightTotalPW / days; //平均用量
            double aSubtotal = weightTotalP / days; //平均成本

            // 2. 效率分析
            double lossRate = totalProduce > 0 ? (totalLoss / totalUsage) * 100 : 0;
            double wasteRate = totalProduce > 0 ? (totalWaste / totalUsage) * 100 : 0;
            double totalWasteRate = totalProduce > 0 ? (totalWaste / totalUsage) * 100 : 0;

            String type = "normal";
            List<String> suggestions = new ArrayList<>();

            // 3. 获取商品信息和安全库存天数
            GbDistributerGoodsEntity goods = gbDistributerGoodsService.queryObject(disGoodsId);
            if(goods.getGbDgControlFresh() == 1){
                type = "fresh";
                // 5. 问题预警
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

            // 6. 管理建议
            if (lossRate > 5) {
                suggestions.add("💡 建议加强员工培训，减少操作损耗");
            }

            Map<String, Object> map = new HashMap<>();
            map.put("disGoodsId", disGoodsId);
            if (goods.getGbDgDistributerId() != null) {
                map.put("disId", goods.getGbDgDistributerId());
            }
            System.out.println("weiidmappa" + map);
//            Double weightTotal = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(map));
            Double weightTotal =  gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(map);
            Double aDouble = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestTotal(map));

            // 7. 构建返回结果
            aiResult.put("suggestions", suggestions);
            aiResult.put("type", type);
            aiResult.put("averageSubtotal", new BigDecimal(aSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP));
            aiResult.put("averageWeight", new BigDecimal(aWeight).setScale(2, BigDecimal.ROUND_HALF_UP));
            aiResult.put("lossRate", new BigDecimal(lossRate).setScale(1, BigDecimal.ROUND_HALF_UP));
            aiResult.put("wasteRate", new BigDecimal(wasteRate).setScale(1, BigDecimal.ROUND_HALF_UP));
            aiResult.put("totalWasteRate", new BigDecimal(totalWasteRate).setScale(1, BigDecimal.ROUND_HALF_UP));
            aiResult.put("stockWeight", new BigDecimal(weightTotal).setScale(1, BigDecimal.ROUND_HALF_UP));
            aiResult.put("stockSubtotal", new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP));
            aiResult.put("totalCostWeight", new BigDecimal(weightTotalPW).setScale(1,BigDecimal.ROUND_HALF_UP));
            aiResult.put("totalCostSubtotal", new BigDecimal(weightTotalP).setScale(1,BigDecimal.ROUND_HALF_UP));

            return aiResult;

        } catch (Exception e) {
            // 如果AI分析出错，返回null，不影响原有功能
            System.err.println("总体AI分析出错: " + e.getMessage());
            return null;
        }
    }



    /**
     * 商品成本汇总（生产 / 损耗 / 损失），数据来自 {@code gb_department_goods_stock_reduce}，不再使用日报表。
     */
    @RequestMapping(value = "/getGbGoodsCostStatistics", method = RequestMethod.POST)
    @ResponseBody
    public R getGbGoodsCostStatistics(String startDate, String stopDate,
            Integer disId, Integer greatId, String searchDepId) {
        Map<String, Object> map0 = buildReduceCostQueryMap(startDate, stopDate, disId, greatId, searchDepId);
        map0.put("types", Arrays.asList(
                GbConstants.StockReduceType.PRODUCTION,
                GbConstants.StockReduceType.WASTE,
                GbConstants.StockReduceType.LOSS));
        Integer rowCount = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(map0);
        if (rowCount == null || rowCount == 0) {
            return R.error(-1, "没有数据");
        }

        Map<String, Object> totals = gbDepartmentGoodsStockReduceService.queryReduceAllTypesTotal(map0);
        double produce = toDouble(totals.get("produceTotal"));
        double waste = toDouble(totals.get("wasteTotal"));
        double loss = toDouble(totals.get("lossTotal"));
        double all = produce + loss + waste;

        Map<String, Object> countMap = new HashMap<>(map0);
        countMap.remove("types");
        countMap.put("type", GbConstants.StockReduceType.PRODUCTION);
        Integer produceCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);
        countMap.put("type", GbConstants.StockReduceType.LOSS);
        Integer lossCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);
        countMap.put("type", GbConstants.StockReduceType.WASTE);
        Integer wasteCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);

        Map<String, Object> mapR = new HashMap<>();
        mapR.put("allTotal", BigDecimal.valueOf(all).setScale(1, RoundingMode.HALF_UP));
        mapR.put("salesTotal", BigDecimal.valueOf(produce).setScale(1, RoundingMode.HALF_UP));
        mapR.put("lossTotal", BigDecimal.valueOf(loss).setScale(1, RoundingMode.HALF_UP));
        mapR.put("wasteTotal", BigDecimal.valueOf(waste).setScale(1, RoundingMode.HALF_UP));
        mapR.put("produceCount", produceCount != null ? produceCount : 0);
        mapR.put("lossCount", lossCount != null ? lossCount : 0);
        mapR.put("wasteCount", wasteCount != null ? wasteCount : 0);

        return R.ok().put("data", mapR);
    }

    /**
     * 按日期与类型分页查询商品成本（reduce 聚合），不再使用日报表。
     */
    @RequestMapping(value = "/getGoodsCostBySearchDate", method = RequestMethod.POST)
    @ResponseBody
    public R getGoodsCostBySearchDate(String startDate, String stopDate, Integer disId,
            String type, String searchDepId, Integer page,
            Integer limit, Integer greatId) {
        int p = page == null || page < 1 ? 1 : page;
        int lim = limit == null || limit < 1 ? 10 : limit;

        Map<String, Object> map0 = buildReduceCostQueryMap(startDate, stopDate, disId, greatId, searchDepId);
        map0.put("offset", (p - 1) * lim);
        map0.put("limit", lim);

        String orderType;
        Integer reduceTypeFilter = null;
        switch (type == null ? "" : type) {
            case "cost":
                orderType = "cost";
                break;
            case "sales":
                orderType = "produce";
                reduceTypeFilter = GbConstants.StockReduceType.PRODUCTION;
                break;
            case "loss":
                orderType = "loss";
                reduceTypeFilter = GbConstants.StockReduceType.LOSS;
                break;
            case "waste":
                orderType = "waste";
                reduceTypeFilter = GbConstants.StockReduceType.WASTE;
                break;
            default:
                orderType = "cost";
                break;
        }
        map0.put("orderType", orderType);
        map0.put("reduceTypeFilter", reduceTypeFilter);

        Map<String, Object> countMap = new HashMap<>(map0);
        countMap.remove("offset");
        countMap.remove("limit");
        countMap.remove("orderType");
        countMap.remove("reduceTypeFilter");
        if (reduceTypeFilter != null) {
            countMap.put("type", reduceTypeFilter);
        } else {
            countMap.put("types", Arrays.asList(
                    GbConstants.StockReduceType.PRODUCTION,
                    GbConstants.StockReduceType.WASTE,
                    GbConstants.StockReduceType.LOSS));
        }
        Integer totalCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);
        int tc = totalCount != null ? totalCount : 0;
        int totalPages = lim > 0 ? (int) Math.ceil((double) tc / lim) : 0;

        List<GbDistributerGoodsEntity> goodsList = gbDepartmentGoodsStockReduceService.queryGoodsCostGoodsPageWithDetails(map0);
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", tc);
        result.put("totalPages", totalPages);
        result.put("currentPage", p);
        result.put("arr", goodsList);
        return R.ok().put("data", result);
    }

    @RequestMapping(value = "/deleteReduceItem/{id}")
    public R deleteReduceItem(@PathVariable Integer id) {
        GbDepGoodsStockAdjustResult result = gbDepartmentGoodsStockLedgerService.removeReduceAndRevert(id);
        if (!result.isOk()) {
            return R.error(result.getCode(), result.getMessage());
        }
        return R.ok().put("data", result.getData().get("data"));
    }

    private static Map<String, Object> buildReduceCostQueryMap(String startDate, String stopDate, Integer disId,
            Integer greatId, String searchDepId) {
        Map<String, Object> map0 = new HashMap<>();
        map0.put("disId", disId);
        map0.put("startDate", startDate);
        map0.put("stopDate", stopDate);
        if (greatId != null && greatId != -1) {
            map0.put("disGoodsGreatId", greatId);
        }
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            map0.put("depId", Integer.valueOf(searchDepId));
        } else {
            map0.put("depType", getGbDepartmentTypeMendian());
        }
        return map0;
    }

    private static double toDouble(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).doubleValue();
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }






    @RequestMapping(value = "/getGbPurGoodsFenxi", method = RequestMethod.POST)
    @ResponseBody
    public R getGbPurGoodsFenxi(Integer disGoodsId, String startDate, String stopDate, Integer supplierId, Integer purUserId) {

        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(disGoodsId);

        Integer howManyDaysInPeriod = 0;
        if (!startDate.equals(stopDate)) {
            howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);
        }

        Map<String, Object> map1 = new HashMap<>();
        map1.put("dayuStatus", 1);
        map1.put("disId", goodsEntity.getGbDgDistributerId());
        map1.put("disGoodsId", disGoodsId);
        map1.put("startDate", startDate);
        map1.put("stopDate", stopDate);
        map1.put("typeNotEqual", 9);
        System.out.println("wsupsspspspsp" + map1);
        Integer integerPur = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(map1);
        Map<String, Object> reduceExistsMap = buildReduceParamsForGoodsDay(
                goodsEntity.getGbDgDistributerId(), disGoodsId, startDate, stopDate, howManyDaysInPeriod, null);
        Integer reduceRowCount = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(reduceExistsMap);
        int integer = reduceRowCount == null ? 0 : reduceRowCount;
        if (integer > 0 || (integerPur != null && integerPur > 0)) {

            Map<String, Object> mapDay = new HashMap<>();
            mapDay.put("disId", goodsEntity.getGbDgDistributerId());
            mapDay.put("disGoodsId", disGoodsId);
            mapDay.put("useStockFinishDate", Boolean.TRUE);
            mapDay.put("dayuStatus", 2);
            mapDay.put("typeNotEqual", 9);
            List<String> dateList = new ArrayList<>();
            List<Map<String, Object>> spplierValueList = new ArrayList<>();
            List<Map<String, Object>> purUserValueList = new ArrayList<>();
            List<Map<String, Object>> purchaseDayValue = new ArrayList<>();

            List<String> produceDayValue = new ArrayList<>();
            List<String> wasteDayValue = new ArrayList<>();
            List<String> lossDayValue = new ArrayList<>();
            List<String> returnDayValue = new ArrayList<>();
            List<String> lowestPriceList = new ArrayList<>();
            List<String> highestPriceList = new ArrayList<>();

            List<String> searchProduceDayValue = new ArrayList<>();
            List<String> searchWasteDayValue = new ArrayList<>();
            List<String> searchLossDayValue = new ArrayList<>();
            List<String> searchReturnDayValue = new ArrayList<>();
            List<String> foodIngredientSalesDayValue = new ArrayList<>();

            List<NxJrdhSupplierEntity> supplierEntities = new ArrayList<>();
            List<GbDepartmentUserEntity> purUserList = new ArrayList<>();


            double doublePurchaseWeight = 0;
            double doublePurchaseV = 0;
            double v = 0;
            String maxPrice = "0";
            String minPrice = "0";
            String perPrice = "0";
            int purCount = 0;
            double searchDoubleCostWeight = 0;
            double searchDoubleCostV = 0;
            double searchV = 0;
            String searchMaxPrice = "0";
            String searchMinPrice = "0";
            String searchPerPrice = "0";
            int searchPurCount = 0;
            String preWeight = "0";
            String preSubtotal = "0";
            Map<String, Object> searchItem = new HashMap<>();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate date = LocalDate.parse(startDate, formatter);
            LocalDate prevDay = date.minusDays(1);
            String prevDayStr = prevDay.format(formatter);
            Map<String, Object> mapPreStock = new HashMap<>();
            mapPreStock.put("disGoodsId", disGoodsId);
            mapPreStock.put("disId", goodsEntity.getGbDgDistributerId());
            mapPreStock.put("date", prevDayStr);
            Integer stockDayCount = gbDepartmentGoodsStockService.queryGoodsStockCount(mapPreStock);
            if (stockDayCount != null && stockDayCount > 0) {
                Double aDouble = gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(mapPreStock);
                Double aDoubleS = gbDepartmentGoodsStockService.queryDepStockRestSubtotal(mapPreStock);
                preWeight = new BigDecimal(aDouble != null ? aDouble : 0).setScale(1, BigDecimal.ROUND_HALF_UP).toString();
                preSubtotal = new BigDecimal(aDoubleS != null ? aDoubleS : 0).setScale(1, BigDecimal.ROUND_HALF_UP).toString();
            }


            Map<String, Object> map = new HashMap<>();
            map.put("disId", goodsEntity.getGbDgDistributerId());
            map.put("startDate", startDate);
            map.put("stopDate", stopDate);
            map.put("disGoodsId", disGoodsId);
            map.put("dayuStatus", 2);
            map.put("typeNotEqual", 9);
            map.put("useStockFinishDate", Boolean.TRUE);
            System.out.println("whhahahhaha" + map);
            purCount = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(map);
            searchItem.put("type", "none");
            if (purCount > 0) {
                System.out.println("subsososoososos" + map);
                doublePurchaseV = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(map);
                doublePurchaseWeight = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(map);
                v = doublePurchaseV / doublePurchaseWeight;
                perPrice = new BigDecimal(v).setScale(1, BigDecimal.ROUND_HALF_UP).toString();
                maxPrice = gbDistributerPurchaseGoodsService.queryPurGoodsMaxPrice(map);
                minPrice = gbDistributerPurchaseGoodsService.queryPurGoodsMinPrice(map);

                if (supplierId != -1 || purUserId != -1) {
                    if (supplierId != -1) {
                        searchItem.put("type", "supplier");
                        map.put("supplierId", supplierId);
                    } else {
                        searchItem.put("type", "purUser");
                        map.put("purUserId", purUserId);
                    }
                    System.out.println("searittmeee" + searchItem);
                    searchPurCount = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(map);
                    if (searchPurCount > 0) {
                        System.out.println("subsososoososos" + map);
                        searchDoubleCostV = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(map);
                        searchDoubleCostWeight = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(map);
                        searchV = searchDoubleCostV / searchDoubleCostWeight;
                        searchPerPrice = new BigDecimal(searchV).setScale(1, BigDecimal.ROUND_HALF_UP).toString();
                        searchMaxPrice = gbDistributerPurchaseGoodsService.queryPurGoodsMaxPrice(map);
                        searchMinPrice = gbDistributerPurchaseGoodsService.queryPurGoodsMinPrice(map);
                    }
                }

                supplierEntities = gbDistributerPurchaseGoodsService.queryDisPurGoodsSupplierList(map);
                purUserList = gbDistributerPurchaseGoodsService.queryPurUserList(map);

            }

            if (howManyDaysInPeriod > 0) {

                System.out.println("epsososososo==0");
                // top
                for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                    // dateList
                    String whichDay = "";
                    if (i == 0) {
                        whichDay = startDate;
                    } else {
                        whichDay = afterWhatDay(startDate, i);
                    }
                    //1.day
                    String substring = whichDay.substring(8, 10);
                    dateList.add(substring);

                    //4,supplier
                    mapDay.put("date", whichDay);
                    mapDay.put("disGoodsId", disGoodsId);
                    mapDay.put("supplierId", null);
                    mapDay.put("purUserId", null);
                    mapDay.put("typeNotEqual", 9);
                    processDailyBusinessData(mapDay, produceDayValue, lossDayValue, wasteDayValue, returnDayValue);
                    processDailyBusinessDataSearch(mapDay, searchProduceDayValue, searchLossDayValue, searchWasteDayValue, searchReturnDayValue);
                    processDailyPurSubtotalData(mapDay, whichDay, purchaseDayValue);
                    foodIngredientSalesDayValue.add(String.format("%.1f",
                            sumFoodGoodsSalesIngredient(disGoodsId, whichDay, null)));
                }

                // 处理供货商数据 - 修改后的逻辑
                if (supplierEntities.size() > 0) {

                    for (NxJrdhSupplierEntity supplierEntity : supplierEntities) {
                        Map<String, Object> supplierMap = new HashMap<>();
                        supplierMap.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                        supplierMap.put("supplierName", supplierEntity.getNxJrdhsSupplierName());

                        // 为每个供货商创建独立的数据列表
                        List<String> thisSupplierPriceValue = new ArrayList<>();
                        List<String> thisSupplierWeightValue = new ArrayList<>();

                        // 重新计算这个供货商每天的数据
                        for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                            String whichDay = "";
                            if (i == 0) {
                                whichDay = startDate;
                            } else {
                                whichDay = afterWhatDay(startDate, i);
                            }

                            mapDay.put("date", whichDay);
                            mapDay.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                            mapDay.put("disGoodsId", disGoodsId);
                            mapDay.put("typeNotEqual", 9);
                            System.out.println("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        supplierMap.put("supplierPriceValue", thisSupplierPriceValue);
                        supplierMap.put("supplierWeightValue", thisSupplierWeightValue);

                        System.out.println("suplisiisisiisis" + spplierValueList.size());
                        spplierValueList.add(supplierMap);
                    }
                }
                if (purUserList.size() > 0) {
                    for (GbDepartmentUserEntity departmentUserEntity : purUserList) {
                        Map<String, Object> purUserMap = new HashMap<>();
                        purUserMap.put("purUserId", departmentUserEntity.getGbDepartmentUserId());
                        purUserMap.put("puUserName", departmentUserEntity.getGbDuWxNickName());

                        // 为每个供货商创建独立的数据列表
                        List<String> thisSupplierPriceValue = new ArrayList<>();
                        List<String> thisSupplierWeightValue = new ArrayList<>();

                        // 重新计算这个供货商每天的数据
                        for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                            String whichDay = "";
                            if (i == 0) {
                                whichDay = startDate;
                            } else {
                                whichDay = afterWhatDay(startDate, i);
                            }

                            mapDay.put("date", whichDay);
                            mapDay.put("supplierId", null);
                            mapDay.put("purUserId", departmentUserEntity.getGbDepartmentUserId());
                            mapDay.put("typeNotEqual", 9);
                            System.out.println("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        purUserMap.put("supplierPriceValue", thisSupplierPriceValue);
                        purUserMap.put("supplierWeightValue", thisSupplierWeightValue);

                        System.out.println("suplisiisisiisis" + spplierValueList.size());
                        purUserValueList.add(purUserMap);
                    }
                }

            } else {

                String substring = startDate.substring(8, 10);
                dateList.add(substring);
                mapDay.put("date", startDate);
                mapDay.put("disGoodsId", disGoodsId);
                mapDay.put("typeNotEqual", 9);

                System.out.println("wokkkkkkondaayayaayayayy" + mapDay);

                processDailyBusinessData(mapDay, produceDayValue, lossDayValue, wasteDayValue, returnDayValue);
                processDailyBusinessDataSearch(mapDay, searchProduceDayValue, searchLossDayValue, searchWasteDayValue, searchReturnDayValue);
                processDailyPurSubtotalData(mapDay, startDate, purchaseDayValue);
                foodIngredientSalesDayValue.add(String.format("%.1f",
                        sumFoodGoodsSalesIngredient(disGoodsId, startDate, null)));
                // 处理单日供货商数据
                // 处理供货商数据 - 修改后的逻辑
                if (supplierEntities.size() > 0) {

                    for (NxJrdhSupplierEntity supplierEntity : supplierEntities) {
                        Map<String, Object> supplierMap = new HashMap<>();
                        supplierMap.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                        supplierMap.put("supplierName", supplierEntity.getNxJrdhsSupplierName());

                        // 为每个供货商创建独立的数据列表
                        List<String> thisSupplierPriceValue = new ArrayList<>();
                        List<String> thisSupplierWeightValue = new ArrayList<>();

                        // 重新计算这个供货商每天的数据
                        for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                            String whichDay = "";
                            if (i == 0) {
                                whichDay = startDate;
                            } else {
                                whichDay = afterWhatDay(startDate, i);
                            }

                            mapDay.put("date", whichDay);
                            mapDay.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                            mapDay.put("disGoodsId", disGoodsId);
                            mapDay.put("typeNotEqual", 9);
                            System.out.println("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        supplierMap.put("supplierPriceValue", thisSupplierPriceValue);
                        supplierMap.put("supplierWeightValue", thisSupplierWeightValue);

                        System.out.println("suplisiisisiisis" + spplierValueList.size());
                        spplierValueList.add(supplierMap);
                    }
                }
                if (purUserList.size() > 0) {
                    for (GbDepartmentUserEntity departmentUserEntity : purUserList) {
                        Map<String, Object> purUserMap = new HashMap<>();
                        purUserMap.put("purUserId", departmentUserEntity.getGbDepartmentUserId());
                        purUserMap.put("puUserName", departmentUserEntity.getGbDuWxNickName());

                        // 为每个供货商创建独立的数据列表
                        List<String> thisSupplierPriceValue = new ArrayList<>();
                        List<String> thisSupplierWeightValue = new ArrayList<>();

                        // 重新计算这个供货商每天的数据
                        for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                            String whichDay = "";
                            if (i == 0) {
                                whichDay = startDate;
                            } else {
                                whichDay = afterWhatDay(startDate, i);
                            }

                            mapDay.put("date", whichDay);
                            mapDay.put("supplierId", null);
                            mapDay.put("purUserId", departmentUserEntity.getGbDepartmentUserId());
                            mapDay.put("typeNotEqual", 9);
                            System.out.println("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        purUserMap.put("supplierPriceValue", thisSupplierPriceValue);
                        purUserMap.put("supplierWeightValue", thisSupplierWeightValue);

                        System.out.println("suplisiisisiisis" + spplierValueList.size());
                        purUserValueList.add(purUserMap);
                    }
                }
            }

            Map<String, Object> mapEveryDay = new HashMap<>();
            mapEveryDay.put("purchaseValue", purchaseDayValue);
            mapEveryDay.put("produceValue", produceDayValue);
            mapEveryDay.put("lossValue", lossDayValue);
            mapEveryDay.put("wasteValue", wasteDayValue);
            mapEveryDay.put("returnValue", returnDayValue);

            mapEveryDay.put("searchProduceValue", searchProduceDayValue);
            mapEveryDay.put("searchLossValue", searchLossDayValue);
            mapEveryDay.put("searchWasteValue", searchWasteDayValue);
            mapEveryDay.put("searchReturnValue", searchReturnDayValue);

            mapEveryDay.put("dateList", dateList);
            mapEveryDay.put("lowestList", lowestPriceList);
            mapEveryDay.put("highestList", highestPriceList);

            mapEveryDay.put("supplierListValue", spplierValueList);
            mapEveryDay.put("purUserListValue", purUserValueList);
            mapEveryDay.put("foodIngredientSalesDayValue", foodIngredientSalesDayValue);

            goodsEntity.setPurEveryDay(mapEveryDay);

            Map<String, Object> mapResult = new HashMap<>();
            mapResult.put("totalPurchaseWeight", new BigDecimal(doublePurchaseWeight).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
            mapResult.put("totalPurchaseSubtotal", new BigDecimal(doublePurchaseV).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
            mapResult.put("maxPrice", maxPrice);
            mapResult.put("minPrice", minPrice);
            mapResult.put("perPrice", perPrice);
            mapResult.put("purCount", purCount);
            mapResult.put("preWeight", preWeight);
            mapResult.put("preSubtotal", preSubtotal);

            mapResult.put("sTotalCost", new BigDecimal(searchDoubleCostWeight).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
            mapResult.put("sTotalCostSubtotal", new BigDecimal(searchDoubleCostV).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
            mapResult.put("sMaxPrice", searchMaxPrice);
            mapResult.put("sMinPrice", searchMinPrice);
            mapResult.put("sPerPrice", searchPerPrice);
            mapResult.put("sPurCount", searchPurCount);

            mapResult.put("supplierList", supplierEntities);
            mapResult.put("purUserList", purUserList);

            Integer fenxiDisId = goodsEntity.getGbDgDistributerId();
            Map<String, Object> dishIng = resolveFenxiLinkedDishSalesSummary(disGoodsId, fenxiDisId, startDate, stopDate);
            mapResult.put("dishFoodNames", dishIng.get("dishFoodNames"));
            mapResult.put("dishSalesQtyTotal",
                    String.format("%.1f", (Double) dishIng.get("dishSalesQtyTotal")));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> linkedDishList =
                    (List<Map<String, Object>>) dishIng.get("linkedDishList");
            mapResult.put("linkedDishList", linkedDishList != null ? linkedDishList : new ArrayList<>());
            double ingredientSalesPeriodTotal = 0;
            for (String s : foodIngredientSalesDayValue) {
                ingredientSalesPeriodTotal += toDouble(s);
            }

            mapResult.put("foodIngredientSalesQtyTotal", String.format("%.1f", ingredientSalesPeriodTotal));

            double doubleProduceWeightPeriod =
                    nzD(gbDepartmentGoodsStockReduceService.queryReduceProduceWeightTotal(reduceExistsMap));
            mapResult.put("totalProduceWeight",
                    new BigDecimal(doubleProduceWeightPeriod).setScale(1, BigDecimal.ROUND_HALF_UP).toString());

            double purchaseVsIngredientDiff = doubleProduceWeightPeriod - ingredientSalesPeriodTotal;
            System.out.println("fenxi produceWeight=" + doubleProduceWeightPeriod + " ingredientSales="
                    + ingredientSalesPeriodTotal + " purchaseWeight=" + doublePurchaseWeight);
            mapResult.put("purchaseVsIngredientSalesQtyDiff", String.format("%.1f", purchaseVsIngredientDiff));

            mapResult.put("code", 0);

            goodsEntity.setGoodsData(mapResult);

            map1.put("dayuStatus", null);

            double goodsDoubleTotalWeight = 0;
            double goodsDoubleTotalSubtotal = 0;
            double goodsDoubleRestV = 0;
            double goodsDoubleRest = 0;
            double goodsDoubleLoss = 0;
            double goodsDoubleLossV = 0;
            double goodsDoubleWaste = 0;
            double goodsDoubleWasteV = 0;
            double goodsDoubleProduce = 0;
            double goodsDoubleProduceV = 0;
            double goodsDoubleReturnV = 0;
            double goodsDoubleReturn = 0;


            double searchGoodsDoubleTotalWeight = 0;
            double searchGoodsDoubleTotalSubtotal = 0;
            double searchGoodsDoubleRestV = 0;
            double searchGoodsDoubleRest = 0;
            double searchGoodsDoubleLoss = 0;
            double searchGoodsDoubleLossV = 0;
            double searchGoodsDoubleWaste = 0;
            double searchGoodsDoubleWasteV = 0;
            double searchGoodsDoubleProduce = 0;
            double searchGoodsDoubleProduceV = 0;
            double searchGoodsDoubleReturnV = 0;
            double searchGoodsDoubleReturn = 0;

            Map<String, Object> mapDepStock = new HashMap<>();
            Map<String, Object> mdapDepStockSearch = new HashMap<>();

            mapDepStock.put("disId", goodsEntity.getGbDgDistributerId());
            mapDepStock.put("disGoodsId", disGoodsId);
            mdapDepStockSearch.put("disId", goodsEntity.getGbDgDistributerId());
            mdapDepStockSearch.put("disGoodsId", disGoodsId);
            Integer integer3 = gbDepartmentGoodsStockService.queryGoodsStockCount(mapDepStock);
            if (integer3 > 0) {
                goodsDoubleRest = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(mapDepStock));
                goodsDoubleRestV = nzD(gbDepartmentGoodsStockService.queryDepStockRestSubtotal(mapDepStock));
                goodsEntity.setGoodsWeightTotalString(String.format("%.1f", goodsDoubleRest));
                goodsEntity.setGoodsStockTotalString(String.format("%.1f", goodsDoubleRestV));
                System.out.println("stoskckkckckc" + goodsDoubleRest + "vvvvv" + goodsDoubleRestV);

                //serchData
                if (supplierId != -1 || purUserId != -1) {
                    if (supplierId != -1) {
                        mdapDepStockSearch.put("supplierId", supplierId);
                    } else {
                        mdapDepStockSearch.put("purUserId", purUserId);
                    }
                    System.out.println("purrususuerr" + mdapDepStockSearch);
                    Integer integer3S = gbDepartmentGoodsStockService.queryGoodsStockCount(mdapDepStockSearch);
                    if (integer3S > 0) {
                        searchGoodsDoubleRest = nzD(gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(mdapDepStockSearch));
                        searchGoodsDoubleRestV = nzD(gbDepartmentGoodsStockService.queryDepStockRestSubtotal(mdapDepStockSearch));
                    }

                }

            }


            //每日消耗图表数据
            mapDepStock.put("startDate", startDate);
            mapDepStock.put("stopDate", stopDate);
            mdapDepStockSearch.put("startDate", startDate);
            mdapDepStockSearch.put("stopDate", stopDate);
            Integer integer22 = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mapDepStock);
            mapDepStock.put("dayuStatus", 2);
            mdapDepStockSearch.put("dayuStatus", 2);
            if (integer22 > 0) {
                mapDepStock.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                mdapDepStockSearch.put("equalType", GbConstants.StockReduceType.PRODUCTION);
                Integer integerProduce = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mapDepStock);
                if (integerProduce > 0) {
                    goodsDoubleProduceV = gbDepartmentGoodsStockReduceService.queryReduceProduceTotal(mapDepStock);
                    goodsDoubleProduce = gbDepartmentGoodsStockReduceService.queryReduceProduceWeightTotal(mapDepStock);
                    goodsDoubleTotalWeight = goodsDoubleTotalWeight + goodsDoubleProduce;
                    goodsDoubleTotalSubtotal = goodsDoubleTotalSubtotal + goodsDoubleProduceV;

                    //serchData
                    if (supplierId != -1 || purUserId != -1) {
                        if (supplierId != -1) {
                            mdapDepStockSearch.put("supplierId", supplierId);
                        } else {
                            mdapDepStockSearch.put("purUserId", purUserId);
                        }
                        System.out.println("zazizzzoozo" + mdapDepStockSearch);
                        Integer integerProduceS = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mdapDepStockSearch);
                        if (integerProduceS > 0) {
                            searchGoodsDoubleProduceV = gbDepartmentGoodsStockReduceService.queryReduceProduceTotal(mdapDepStockSearch);
                            searchGoodsDoubleProduce = gbDepartmentGoodsStockReduceService.queryReduceProduceWeightTotal(mdapDepStockSearch);
                            searchGoodsDoubleTotalWeight = searchGoodsDoubleTotalWeight + searchGoodsDoubleProduce;
                            searchGoodsDoubleTotalSubtotal = searchGoodsDoubleTotalSubtotal + searchGoodsDoubleProduceV;
                        }
                    }

                } else {
                    goodsDoubleProduceV = 0;
                    goodsDoubleProduce = 0;
                    searchGoodsDoubleProduceV = 0;
                    searchGoodsDoubleProduce = 0;
                }
                mapDepStock.put("equalType", GbConstants.StockReduceType.LOSS);
                mdapDepStockSearch.put("equalType", GbConstants.StockReduceType.LOSS);
                Integer integerLoss = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mapDepStock);
                if (integerLoss > 0) {
                    goodsDoubleLossV = gbDepartmentGoodsStockReduceService.queryReduceLossTotal(mapDepStock);
                    goodsDoubleLoss = gbDepartmentGoodsStockReduceService.queryReduceLossWeightTotal(mapDepStock);
                    goodsDoubleTotalWeight = goodsDoubleTotalWeight + goodsDoubleLoss;
                    goodsDoubleTotalSubtotal = goodsDoubleTotalSubtotal + goodsDoubleLossV;
                    System.out.println("goodsDoubleCostgoodsDoubleCostllll" + goodsDoubleTotalWeight);

                    //serchData
                    if (supplierId != -1 || purUserId != -1) {
                        if (supplierId != -1) {
                            mdapDepStockSearch.put("supplierId", supplierId);
                        } else {
                            mdapDepStockSearch.put("purUserId", purUserId);
                        }
                        Integer integerLossS = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mdapDepStockSearch);
                        if (integerLossS > 0) {
                            searchGoodsDoubleLossV = gbDepartmentGoodsStockReduceService.queryReduceLossTotal(mdapDepStockSearch);
                            searchGoodsDoubleLoss = gbDepartmentGoodsStockReduceService.queryReduceLossWeightTotal(mdapDepStockSearch);
                            searchGoodsDoubleTotalWeight = searchGoodsDoubleTotalWeight + searchGoodsDoubleLoss;
                            searchGoodsDoubleTotalSubtotal = searchGoodsDoubleTotalSubtotal + searchGoodsDoubleLossV;
                        }
                    }

                } else {
                    goodsDoubleLossV = 0;
                    goodsDoubleLoss = 0;
                    searchGoodsDoubleLossV = 0;
                    searchGoodsDoubleLoss = 0;
                }
                mapDepStock.put("equalType", GbConstants.StockReduceType.WASTE);
                mdapDepStockSearch.put("equalType", GbConstants.StockReduceType.WASTE);
                System.out.println("reedoddddmap" + mapDepStock);
                Integer integerWaste = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mapDepStock);
                if (integerWaste > 0) {
                    goodsDoubleWasteV = gbDepartmentGoodsStockReduceService.queryReduceWasteTotal(mapDepStock);
                    goodsDoubleWaste = gbDepartmentGoodsStockReduceService.queryReduceWasteWeightTotal(mapDepStock);
                    goodsDoubleTotalWeight = goodsDoubleTotalWeight + goodsDoubleWaste;
                    goodsDoubleTotalSubtotal = goodsDoubleTotalSubtotal + goodsDoubleWasteV;

                    //serchData
                    if (supplierId != -1 || purUserId != -1) {
                        if (supplierId != -1) {
                            mdapDepStockSearch.put("supplierId", supplierId);
                        } else {
                            mdapDepStockSearch.put("purUserId", purUserId);
                        }
                        Integer integerLossS = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mdapDepStockSearch);
                        if (integerLossS > 0) {
                            searchGoodsDoubleWasteV = gbDepartmentGoodsStockReduceService.queryReduceWasteTotal(mdapDepStockSearch);
                            searchGoodsDoubleWaste = gbDepartmentGoodsStockReduceService.queryReduceWasteWeightTotal(mdapDepStockSearch);
                            searchGoodsDoubleTotalWeight = searchGoodsDoubleTotalWeight + searchGoodsDoubleWaste;
                            searchGoodsDoubleTotalSubtotal = searchGoodsDoubleTotalSubtotal + searchGoodsDoubleWasteV;
                        }
                    }


                } else {
                    goodsDoubleWasteV = 0;
                    goodsDoubleWaste = 0;
                    searchGoodsDoubleWasteV = 0;
                    searchGoodsDoubleWaste = 0;

                }
                mapDepStock.put("equalType", GbConstants.StockReduceType.RETURN);
                mdapDepStockSearch.put("equalType", GbConstants.StockReduceType.RETURN);
                Integer integerReturn = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mapDepStock);
                if (integerReturn > 0) {
                    goodsDoubleReturnV = gbDepartmentGoodsStockReduceService.queryReduceReturnTotal(mapDepStock);
                    goodsDoubleReturn = gbDepartmentGoodsStockReduceService.queryReduceReturnWeightTotal(mapDepStock);

                    //serchData
                    if (supplierId != -1 || purUserId != -1) {
                        if (supplierId != -1) {
                            mdapDepStockSearch.put("supplierId", supplierId);
                        } else {
                            mdapDepStockSearch.put("purUserId", purUserId);
                        }
                        Integer integerLossS = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(mdapDepStockSearch);
                        if (integerLossS > 0) {
                            searchGoodsDoubleReturnV = gbDepartmentGoodsStockReduceService.queryReduceReturnTotal(mdapDepStockSearch);
                            searchGoodsDoubleReturn = gbDepartmentGoodsStockReduceService.queryReduceReturnWeightTotal(mdapDepStockSearch);
                        }
                    }

                } else {
                    goodsDoubleReturnV = 0;
                }

                BigDecimal proPercent = new BigDecimal(0);
                BigDecimal lossPercent = new BigDecimal(0);
                BigDecimal wastePercent = new BigDecimal(0);
                BigDecimal retPercent = new BigDecimal(0);
                if (goodsDoubleProduce > 0) {
                    proPercent = new BigDecimal(goodsDoubleProduce).divide(new BigDecimal(goodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    System.out.println("propenene" + goodsDoubleProduce + " ostweiicei" + goodsDoubleTotalWeight);
                    lossPercent = new BigDecimal(goodsDoubleLoss).divide(new BigDecimal(goodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    wastePercent = new BigDecimal(goodsDoubleWaste).divide(new BigDecimal(goodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    retPercent = new BigDecimal(goodsDoubleReturn).divide(new BigDecimal(goodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);
                }

                BigDecimal searchProPercent = new BigDecimal(0);
                BigDecimal searchLossPercent = new BigDecimal(0);
                BigDecimal searchWastePercent = new BigDecimal(0);
                BigDecimal searchRetPercent = new BigDecimal(0);
                if (searchGoodsDoubleTotalWeight > 0) {
                    searchProPercent = new BigDecimal(searchGoodsDoubleProduce).divide(new BigDecimal(searchGoodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    searchLossPercent = new BigDecimal(searchGoodsDoubleLoss).divide(new BigDecimal(searchGoodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    searchWastePercent = new BigDecimal(searchGoodsDoubleWaste).divide(new BigDecimal(searchGoodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    searchRetPercent = new BigDecimal(searchGoodsDoubleReturn).divide(new BigDecimal(searchGoodsDoubleTotalWeight), 3, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(1, BigDecimal.ROUND_HALF_UP);

                }

                System.out.println("ggogoogogsaleepddid" + proPercent);
                goodsEntity.setGoodsProducePercent(proPercent.toString());
                goodsEntity.setGoodsLossPercent(lossPercent.toString());
                goodsEntity.setGoodsWastePercent(wastePercent.toString());
                goodsEntity.setGoodsReturnPercent(retPercent.toString());
                goodsEntity.setGoodsCostWeightTotalString(String.format("%.1f", goodsDoubleTotalWeight));
                goodsEntity.setGoodsCostTotalString(String.format("%.1f", goodsDoubleTotalSubtotal));
                goodsEntity.setGoodsProduceWeightTotalString(String.format("%.1f", goodsDoubleProduce));
                goodsEntity.setGoodsProduceTotalString(String.format("%.1f", goodsDoubleProduceV));
                goodsEntity.setGoodsLossWeightTotalString(String.format("%.1f", goodsDoubleLoss));
                goodsEntity.setGoodsLossTotalString(String.format("%.1f", goodsDoubleLossV));
                goodsEntity.setGoodsWasteWeightTotalString(String.format("%.1f", goodsDoubleWaste));
                goodsEntity.setGoodsWasteTotalString(String.format("%.1f", goodsDoubleWasteV));
                goodsEntity.setGoodsReturnWeightTotalString(String.format("%.1f", goodsDoubleReturn));
                goodsEntity.setGoodsReturnTotalString(String.format("%.1f", goodsDoubleReturnV));


                System.out.println("searpurororoortotaooa" + searchGoodsDoubleTotalSubtotal);
                searchItem.put("restSubtotal", String.format("%.1f", searchGoodsDoubleRestV));
                searchItem.put("restWeight", String.format("%.1f", searchGoodsDoubleRest));
                searchItem.put("produceSubtotal", String.format("%.1f", searchGoodsDoubleProduceV));
                searchItem.put("produceWeight", String.format("%.1f", searchGoodsDoubleProduce));
                searchItem.put("lossSubtotal", String.format("%.1f", searchGoodsDoubleLossV));
                searchItem.put("lossWeight", String.format("%.1f", searchGoodsDoubleLoss));
                searchItem.put("wasteSubtotal", String.format("%.1f", searchGoodsDoubleWasteV));
                searchItem.put("wasteWeight", String.format("%.1f", searchGoodsDoubleWaste));
                searchItem.put("returnSubtotal", String.format("%.1f", searchGoodsDoubleReturnV));
                searchItem.put("returnWeight", String.format("%.1f", searchGoodsDoubleReturn));

                searchItem.put("producePercent", String.format("%.1f", searchProPercent));
                searchItem.put("lossPercent", String.format("%.1f", searchLossPercent));
                searchItem.put("wastePercent", String.format("%.1f", searchWastePercent));
                searchItem.put("returnPercent", String.format("%.1f", searchRetPercent));
                searchItem.put("total", String.format("%.1f", searchGoodsDoubleTotalSubtotal));
                searchItem.put("totalWeight", String.format("%.1f", searchGoodsDoubleTotalWeight));
            }
            mapResult.put("searchItem", searchItem);
        } else {
            Map<String, Object> mapResult = new HashMap<>();

            mapResult.put("code", -1);
            goodsEntity.setGoodsData(mapResult);

            // 设置默认值，避免 null
            goodsEntity.setGoodsPurTotalWeight("0");
            goodsEntity.setGoodsPurTotalCount(0);
        }

        return R.ok().put("data", goodsEntity);
    }




    // 这个方法需要你实现，用于获取指定供货商在指定日期的采购数据
    private void processDailyPurchaseDataForSupplier(Map<String, Object> mapDay, List<String> supplierPriceValue, List<String> supplierWeightValue) {
        // 根据 mapDay 中的 date, disGoodsId, supplierId 查询该供货商在该日期的采购数据
        // 将价格和重量分别添加到 supplierPriceValue 和 supplierWeightValue 列表中
        int purCountDay = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(mapDay);
        if (purCountDay == 1) {
            String price = gbDistributerPurchaseGoodsService.queryPurchaseGoodsPrice(mapDay);
            String weight = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeight(mapDay);

            // 添加null检查，防止数据库字段值为null
            if (price == null || weight == null) {
                // 如果查询结果为空，按0处理
                supplierPriceValue.add("0.0");
                supplierWeightValue.add("0.0");
            } else {
                // 检查空字符串
                if (price.trim().isEmpty()) {
                    price = "0";
                }
                if (weight.trim().isEmpty()) {
                    weight = "0";
                }

                supplierPriceValue.add(new BigDecimal(price).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                supplierWeightValue.add(new BigDecimal(weight).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
            }
        } else if (purCountDay > 1) {
            Double subTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapDay);
            Double weightTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(mapDay);

            // 添加null检查
            if (subTotal == null || weightTotal == null || weightTotal == 0) {
                supplierPriceValue.add("0.0");
                supplierWeightValue.add("0.0");
            } else {
                double v1 = subTotal / weightTotal;
                double v2 = weightTotal / purCountDay;

                supplierPriceValue.add(new BigDecimal(v1).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                supplierWeightValue.add(new BigDecimal(v2).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
            }
        } else {
            supplierPriceValue.add("0");
            supplierWeightValue.add("0");
        }
    }



    /**
     * 处理每日业务数据（生产、损耗、废弃、退货）
     */
    private void processDailyBusinessDataSearch(Map<String, Object> mapDay, List<String> produceDayValue,
                                                List<String> lossDayValue, List<String> wasteDayValue, List<String> returnDayValue) {
        Map<String, Object> reduceParams = buildReduceParamsFromFenxiMapDay(mapDay);
        Map<String, Object> w = gbDepartmentGoodsStockReduceService.queryReduceTypeWeightTotalsByScope(reduceParams);
        System.out.println("onda0101001010 " + mapDay);
        double produceW = toDouble(w != null ? w.get("produceWeight") : null);
        double lossW = toDouble(w != null ? w.get("lossWeight") : null);
        double wasteW = toDouble(w != null ? w.get("wasteWeight") : null);
        double returnW = toDouble(w != null ? w.get("returnWeight") : null);
        if (produceW + lossW + wasteW + returnW > 1e-9) {
            produceDayValue.add(String.format("%.1f", produceW));
            lossDayValue.add(String.format("%.1f", lossW));
            wasteDayValue.add(String.format("%.1f", wasteW));
            returnDayValue.add(String.format("%.1f", returnW));
        } else {
            produceDayValue.add("0");
            lossDayValue.add("0");
            wasteDayValue.add("0");
            returnDayValue.add("0");
        }
    }


    /**
     * 处理每日业务数据（生产、损耗、废弃、退货）
     */
    private void processDailyBusinessData(Map<String, Object> mapDay, List<String> produceDayValue,
                                          List<String> lossDayValue, List<String> wasteDayValue, List<String> returnDayValue) {
        Map<String, Object> reduceParams = buildReduceParamsFromFenxiMapDay(mapDay);
        Map<String, Object> w = gbDepartmentGoodsStockReduceService.queryReduceTypeWeightTotalsByScope(reduceParams);
        System.out.println("onda0101001010 " + mapDay);
        double produceW = toDouble(w != null ? w.get("produceWeight") : null);
        double lossW = toDouble(w != null ? w.get("lossWeight") : null);
        double wasteW = toDouble(w != null ? w.get("wasteWeight") : null);
        double returnW = toDouble(w != null ? w.get("returnWeight") : null);
        if (produceW + lossW + wasteW + returnW > 1e-9) {
            produceDayValue.add(String.format("%.1f", produceW));
            lossDayValue.add(String.format("%.1f", lossW));
            wasteDayValue.add(String.format("%.1f", wasteW));
            returnDayValue.add(String.format("%.1f", returnW));
        } else {
            produceDayValue.add("0");
            lossDayValue.add("0");
            wasteDayValue.add("0");
            returnDayValue.add("0");
        }
    }

    private void processDailyPurSubtotalData(Map<String, Object> mapDay, String whichDay, List<Map<String, Object>> purchaseDayValue) {
        Integer integer1 = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(mapDay);
        System.out.println("onda0101001010 " + mapDay);
        Map<String, Object> map = new HashMap<>();
        map.put("date", whichDay);
        if (integer1 > 0) {
            Double subTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapDay);
            map.put("purSubtotal", String.format("%.1f", subTotal));
        } else {
            map.put("purSubtotal", 0);
        }
        purchaseDayValue.add(map);
    }

}
