package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceCostQueryService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GbDepartmentGoodsStockReduceCostQueryServiceImpl implements GbDepartmentGoodsStockReduceCostQueryService {

    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;

    @Override
    public Map<String, Object> buildGoodsCostStatistics(String startDate, String stopDate, Integer disId,
            Integer greatId, String searchDepId) {
        Map<String, Object> map0 = GbDepartmentGoodsStockReduceSupport.buildReduceCostQueryMap(startDate, stopDate, disId,
                greatId, searchDepId);
        map0.put("types", Arrays.asList(
                GbConstants.StockReduceType.PRODUCTION,
                GbConstants.StockReduceType.WASTE,
                GbConstants.StockReduceType.LOSS));
        Integer rowCount = gbDepartmentGoodsStockReduceService.queryReduceTypeCount(map0);
        if (rowCount == null || rowCount == 0) {
            throw new IllegalArgumentException("没有数据");
        }

        Map<String, Object> totals = gbDepartmentGoodsStockReduceService.queryReduceAllTypesTotal(map0);
        double produce = GbDepartmentGoodsStockReduceSupport.toDouble(totals.get("produceTotal"));
        double waste = GbDepartmentGoodsStockReduceSupport.toDouble(totals.get("wasteTotal"));
        double loss = GbDepartmentGoodsStockReduceSupport.toDouble(totals.get("lossTotal"));
        double employeeMeal = GbDepartmentGoodsStockReduceSupport.toDouble(
                totals.get(GbDepartmentGoodsStockReduceSupport.KEY_EMPLOYEE_MEAL_TOTAL));
        double all = produce + loss + waste + employeeMeal;

        Map<String, Object> countMap = new HashMap<>(map0);
        countMap.remove("types");
        countMap.put("type", GbConstants.StockReduceType.PRODUCTION);
        Integer produceCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);
        countMap.put("type", GbConstants.StockReduceType.LOSS);
        Integer lossCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);
        countMap.put("type", GbConstants.StockReduceType.WASTE);
        Integer wasteCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);
        countMap.put("type", GbConstants.StockReduceType.EMPLOYEE_MEAL);
        Integer employeeMealCount = gbDepartmentGoodsStockReduceService.queryReduceDistinctGoodsCount(countMap);

        Map<String, Object> mapR = new HashMap<>();
        mapR.put("allTotal", BigDecimal.valueOf(all).setScale(2, RoundingMode.HALF_UP));
        System.out.println("saleTotalproduce" + produce);
        mapR.put("salesTotal", BigDecimal.valueOf(produce).setScale(2, RoundingMode.HALF_UP));
        mapR.put("lossTotal", BigDecimal.valueOf(loss).setScale(2, RoundingMode.HALF_UP));
        mapR.put("wasteTotal", BigDecimal.valueOf(waste).setScale(2, RoundingMode.HALF_UP));
        mapR.put("employeeMealTotal", BigDecimal.valueOf(employeeMeal).setScale(2, RoundingMode.HALF_UP));
        mapR.put("produceCount", produceCount != null ? produceCount : 0);
        mapR.put("lossCount", lossCount != null ? lossCount : 0);
        mapR.put("wasteCount", wasteCount != null ? wasteCount : 0);
        mapR.put("employeeMealCount", employeeMealCount != null ? employeeMealCount : 0);
        return mapR;
    }

    @Override
    public Map<String, Object> buildGoodsCostPage(String startDate, String stopDate, Integer disId, String type,
            String searchDepId, Integer page, Integer limit, Integer greatId) {
        int p = page == null || page < 1 ? 1 : page;
        int lim = limit == null || limit < 1 ? 10 : limit;

        Map<String, Object> map0 = GbDepartmentGoodsStockReduceSupport.buildReduceCostQueryMap(startDate, stopDate, disId,
                greatId, searchDepId);
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
            case "employeeMeal":
                orderType = "employeeMeal";
                reduceTypeFilter = GbConstants.StockReduceType.EMPLOYEE_MEAL;
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
        return result;
    }
}
