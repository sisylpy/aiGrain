package com.nongxinle.service.impl;

import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerPurchaseGoodsDisPurchaseDateService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.afterWhatDay;
import static com.nongxinle.utils.DateUtils.getHowManyDaysInPeriod;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseGoodsDisPurchaseDateServiceImpl implements GbDistributerPurchaseGoodsDisPurchaseDateService {

    private final GbDistributerPurchaseGoodsService gbDpgService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;

    @Override
    public Map<String, Object> buildDisPurchaseDate(Integer disId, String startDate, String stopDate) {
        log.debug("======== GB采购日期统计开始 ========");
        log.debug("批发商ID: {}, 日期: {} ~ {}", disId, startDate, stopDate);

        Map<String, Object> mapCheck = new HashMap<>();
        mapCheck.put("disId", disId);
        mapCheck.put("startDate", startDate);
        mapCheck.put("stopDate", stopDate);
        mapCheck.put("dayuStatus", 1);

        Integer purchaseCount = gbDpgService.queryPurchaseGoodsCount(mapCheck);

        Map<String, Object> mapReduceCheck = new HashMap<>();
        mapReduceCheck.put("disId", disId);
        mapReduceCheck.put("startDate", startDate);
        mapReduceCheck.put("stopDate", stopDate);
        Integer reduceCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapReduceCheck);

        int pc = purchaseCount == null ? 0 : purchaseCount;
        int rc = reduceCount == null ? 0 : reduceCount;
        log.debug("采购商品数量: {}, 出货记录数量: {}", pc, rc);

        if (pc == 0 && rc == 0) {
            log.debug("没有数据，返回错误");
            return null;
        }

        List<Map<String, Object>> dayList = new ArrayList<>();
        Integer howManyDaysInPeriod = 0;
        if (!startDate.equals(stopDate)) {
            howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);
        }
        int periodDays = howManyDaysInPeriod == null ? 0 : howManyDaysInPeriod;
        log.debug("日期跨度: {} 天", periodDays + 1);

        for (int i = 0; i < periodDays + 1; i++) {
            String whichDay = i == 0 ? startDate : afterWhatDay(startDate, i);
            log.debug("--- 统计日期: {} ---", whichDay);

            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("day", whichDay);

            Map<String, Object> map = new HashMap<>();
            map.put("date", whichDay);
            map.put("disId", disId);
            map.put("dayuStatus", 1);

            map.put("purchaseType", GbConstants.PurchaseOrderType.SELF_PURCHASE);
            Integer zicaiCount = gbDpgService.queryPurchaseGoodsCount(map);
            BigDecimal zicaiTotal = new BigDecimal(0);
            if (zicaiCount != null && zicaiCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);
                zicaiTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("zicai", zicaiTotal);
            map.remove("purchaseType");

            map.put("purchaseType", GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
            Integer dinghuoCount = gbDpgService.queryPurchaseGoodsCount(map);
            BigDecimal dinghuoTotal = new BigDecimal(0);
            if (dinghuoCount != null && dinghuoCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);
                dinghuoTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("dinghuo", dinghuoTotal);
            log.debug("订货金额: {}", dinghuoTotal);

            Map<String, Object> mapAllStock = new HashMap<>();
            mapAllStock.put("date", whichDay);
            mapAllStock.put("disId", disId);
            mapAllStock.put("dayuStatus", 1);
            Integer allStockCount = gbDpgService.queryPurchaseGoodsCount(mapAllStock);
            BigDecimal allStockTotal = new BigDecimal(0);
            if (allStockCount != null && allStockCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapAllStock);
                allStockTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("allStock", allStockTotal);

            Map<String, Object> restMap = new HashMap<>();
            restMap.put("disId", disId);
            restMap.put("purchaseLinkDate", whichDay);
            restMap.put("purDayuStatus", 1);
            Double restDouble = gbDepartmentGoodsStockService.queryDepGoodsRestTotal(restMap);
            BigDecimal restStockTotal = new BigDecimal(restDouble != null ? restDouble : 0)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);
            dayMap.put("restStock", restStockTotal);
            log.debug("所有入库采购金额: {}, 当日采购关联库存剩余金额: {}", allStockTotal, restStockTotal);

            Map<String, Object> mapSale = new HashMap<>();
            mapSale.put("date", whichDay);
            mapSale.put("disId", disId);
            List<Integer> saleTypes = new ArrayList<>();
            saleTypes.add(0);
            saleTypes.add(1);
            mapSale.put("types", saleTypes);
            BigDecimal saleCostTotal = new BigDecimal(0);
            Integer saleCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapSale);
            if (saleCount != null && saleCount > 0) {
                Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapSale);
                saleCostTotal = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("saleCostTotal", saleCostTotal);

            Map<String, Object> mapLoss = new HashMap<>();
            mapLoss.put("date", whichDay);
            mapLoss.put("disId", disId);
            List<Integer> lossTypes = new ArrayList<>();
            lossTypes.add(2);
            lossTypes.add(3);
            mapLoss.put("types", lossTypes);
            BigDecimal lossCostTotal = new BigDecimal(0);
            Integer lossCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapLoss);
            if (lossCount != null && lossCount > 0) {
                Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapLoss);
                lossCostTotal = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("lossCostTotal", lossCostTotal);

            Map<String, Object> mapReturn = new HashMap<>();
            mapReturn.put("date", whichDay);
            mapReturn.put("disId", disId);
            mapReturn.put("type", 4);
            BigDecimal returnCostTotal = new BigDecimal(0);
            Integer returnCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapReturn);
            if (returnCount != null && returnCount > 0) {
                Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapReturn);
                returnCostTotal = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("returnCostTotal", returnCostTotal);

            BigDecimal costTotal = saleCostTotal.add(lossCostTotal).add(returnCostTotal);
            dayMap.put("costTotal", costTotal);

            dayList.add(dayMap);
        }

        log.debug("--- 开始汇总统计 ---");
        Map<String, Object> mapTotal = new HashMap<>();
        mapTotal.put("disId", disId);
        mapTotal.put("startDate", startDate);
        mapTotal.put("stopDate", stopDate);
        mapTotal.put("dayuStatus", 1);

        BigDecimal purchaseTotal = new BigDecimal(0);
        Integer totalCount = gbDpgService.queryPurchaseGoodsCount(mapTotal);
        if (totalCount != null && totalCount > 0) {
            Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapTotal);
            purchaseTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        log.debug("总采购金额: {}", purchaseTotal);

        mapTotal.put("purchaseType", GbConstants.PurchaseOrderType.SELF_PURCHASE);
        BigDecimal zicaiTotalSum = new BigDecimal(0);
        Integer zicaiCountSum = gbDpgService.queryPurchaseGoodsCount(mapTotal);
        if (zicaiCountSum != null && zicaiCountSum > 0) {
            Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapTotal);
            zicaiTotalSum = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        mapTotal.remove("purchaseType");

        mapTotal.put("purchaseType", GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
        log.debug("dingdhuomapappapap{}", mapTotal);
        BigDecimal dinghuoTotalSum = new BigDecimal(0);
        Integer dinghuoCountSum = gbDpgService.queryPurchaseGoodsCount(mapTotal);
        if (dinghuoCountSum != null && dinghuoCountSum > 0) {
            log.debug("mappaaptttt{}", mapTotal);
            Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapTotal);
            dinghuoTotalSum = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        log.debug("订货总额1: {}", dinghuoTotalSum);
        mapTotal.remove("purchaseType");

        Map<String, Object> mapSaleTotal = new HashMap<>();
        mapSaleTotal.put("disId", disId);
        mapSaleTotal.put("startDate", startDate);
        mapSaleTotal.put("stopDate", stopDate);
        List<Integer> saleTypesTotal = new ArrayList<>();
        saleTypesTotal.add(0);
        saleTypesTotal.add(1);
        mapSaleTotal.put("types", saleTypesTotal);
        BigDecimal saleCostTotalSum = new BigDecimal(0);
        Integer saleCountSum = gbDepartmentStockReduceService.queryReduceTypeCount(mapSaleTotal);
        if (saleCountSum != null && saleCountSum > 0) {
            Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapSaleTotal);
            saleCostTotalSum = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        log.debug("销售支出总额: {}", saleCostTotalSum);

        Map<String, Object> mapLossTotal = new HashMap<>();
        mapLossTotal.put("disId", disId);
        mapLossTotal.put("startDate", startDate);
        mapLossTotal.put("stopDate", stopDate);
        List<Integer> lossTypesTotal = new ArrayList<>();
        lossTypesTotal.add(2);
        lossTypesTotal.add(3);
        mapLossTotal.put("types", lossTypesTotal);
        BigDecimal lossCostTotalSum = new BigDecimal(0);
        Integer lossCountSum = gbDepartmentStockReduceService.queryReduceTypeCount(mapLossTotal);
        if (lossCountSum != null && lossCountSum > 0) {
            Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapLossTotal);
            lossCostTotalSum = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        log.debug("损耗支出总额: {}", lossCostTotalSum);

        Map<String, Object> mapReturnTotal = new HashMap<>();
        mapReturnTotal.put("disId", disId);
        mapReturnTotal.put("startDate", startDate);
        mapReturnTotal.put("stopDate", stopDate);
        mapReturnTotal.put("type", 4);
        BigDecimal returnCostTotalSum = new BigDecimal(0);
        Integer returnCountSum = gbDepartmentStockReduceService.queryReduceTypeCount(mapReturnTotal);
        if (returnCountSum != null && returnCountSum > 0) {
            Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapReturnTotal);
            returnCostTotalSum = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        log.debug("退货支出总额: {}", returnCostTotalSum);

        BigDecimal costTotalSum = saleCostTotalSum.add(lossCostTotalSum).add(returnCostTotalSum);
        log.debug("总支出: {}", costTotalSum);

        BigDecimal purchasePerDay = purchaseTotal;
        BigDecimal costPerDay = costTotalSum;
        if (periodDays > 0) {
            purchasePerDay = purchaseTotal.divide(new BigDecimal(periodDays + 1), 1, BigDecimal.ROUND_HALF_UP);
            costPerDay = costTotalSum.divide(new BigDecimal(periodDays + 1), 1, BigDecimal.ROUND_HALF_UP);
        }
        log.debug("日均采购: {}, 日均支出: {}", purchasePerDay, costPerDay);

        Map<String, Object> result = new HashMap<>();
        result.put("allTotal", purchaseTotal);
        result.put("purchasePerDay", purchasePerDay);
        result.put("zicaiTotal", zicaiTotalSum);
        result.put("dinghuoTotal", dinghuoTotalSum);
        result.put("costTotal", costTotalSum);
        result.put("costPerDay", costPerDay);
        result.put("saleCostTotal", saleCostTotalSum);
        result.put("lossCostTotal", lossCostTotalSum);
        result.put("returnCostTotal", returnCostTotalSum);
        result.put("arr", dayList);

        log.debug("======== GB采购日期统计完成 ========");
        return result;
    }
}
