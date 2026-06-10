package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDistributerFatherGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsDisPurchaseCataService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseGoodsDisPurchaseCataServiceImpl implements GbDistributerPurchaseGoodsDisPurchaseCataService {

    private final GbDistributerPurchaseGoodsService gbDpgService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDistributerFatherGoodsService gbDistributerFatherGoodsService;

    @Override
    public Map<String, Object> buildDisPurchaseCata(Integer disId, String startDate, String stopDate,
                                                    String purUserId, String supplierId) {
        log.debug("======== GB采购分类统计开始 ========");
        log.debug("批发商ID: {}, 日期: {} ~ {}, purUserId={}, supplierId={}",
                disId, startDate, stopDate, purUserId, supplierId);

        Map<String, Object> map123 = new HashMap<>();

        Map<String, Object> mapDep = new HashMap<>();
        mapDep.put("disId", disId);
        mapDep.put("dayuStatus", 1);
        mapDep.put("typeNotEqual", 9);
        mapDep.put("startDate", startDate);
        mapDep.put("stopDate", stopDate);
        mapDep.put("depType", getGbDepartmentTypeMendian());

        log.debug("purmapapapapa{}", mapDep);
        Integer integerT = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
        Integer integer3 = gbDepartmentStockReduceService.queryReduceTypeCount(mapDep);
        int it = integerT == null ? 0 : integerT;
        int i3 = integer3 == null ? 0 : integer3;

        BigDecimal purchaseTotal = new BigDecimal(0);
        if (it == 0 && i3 == 0) {
            return null;
        } else {
            Integer integer = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
            if (integer != null && integer > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                purchaseTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
        }

        Map<String, Object> reduceTotalsAll = gbDepartmentStockReduceService.queryReduceAllTypesTotal(mapDep);
        double totalCost = 0.0;
        double employeeMealAll = 0.0;
        if (reduceTotalsAll != null) {
            double produceAll = GbDepartmentGoodsStockReduceSupport.toDouble(reduceTotalsAll.get("produceTotal"));
            double wasteAll = GbDepartmentGoodsStockReduceSupport.toDouble(reduceTotalsAll.get("wasteTotal"));
            double lossAll = GbDepartmentGoodsStockReduceSupport.toDouble(reduceTotalsAll.get("lossTotal"));
            employeeMealAll = GbDepartmentGoodsStockReduceSupport.toDouble(
                    reduceTotalsAll.get(GbDepartmentGoodsStockReduceSupport.KEY_EMPLOYEE_MEAL_TOTAL));
            totalCost = produceAll + wasteAll + lossAll + employeeMealAll;
        }

        mapDep.put("startDate", startDate);
        mapDep.put("stopDate", stopDate);

        log.debug("查询参数 mapDepaaa: {}", mapDep);
        Integer purchaseCount = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
        int pc = purchaseCount == null ? 0 : purchaseCount;
        if (pc > 0) {
            log.debug("========== 开始查询商品大类 ==========");

            TreeSet<GbDistributerFatherGoodsEntity> fatherGoodsTreeSet =
                    gbDistributerFatherGoodsService.queryPurchaseGoodsFatherTypes(mapDep);
            log.debug("查询成功，返回记录数: {}", fatherGoodsTreeSet.size());

            for (GbDistributerFatherGoodsEntity greatEntity : fatherGoodsTreeSet) {
                log.debug("goodsName{}", greatEntity.getGbDfgFatherGoodsName());
                log.debug("goodsNamegetGbDistributerFatherGoodsId{}", greatEntity.getGbDistributerFatherGoodsId());
                log.debug("goodsNamegetGbDfgFathersFatherId{}", greatEntity.getGbDfgFathersFatherId());

                Map<String, Object> cataMap = new HashMap<>();

                mapDep.put("dayuStatus", -1);
                mapDep.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                log.debug("mazzzuissisis{}", mapDep);
                Integer integer = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
                BigDecimal batchBillTotal = new BigDecimal(0);
                if (integer != null && integer > 0) {
                    Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                    batchBillTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                BigDecimal divide = new BigDecimal(0);
                if (purchaseTotal.compareTo(new BigDecimal(0)) == 1) {
                    divide = batchBillTotal.divide(purchaseTotal, 4, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100));
                }

                cataMap.put("greatPurTotal", batchBillTotal);
                cataMap.put("greatPercent", divide);
                cataMap.put("purTotal", purchaseTotal);
                mapDep.put("dayuStatus", 2);
                mapDep.put("purchaseType", GbConstants.PurchaseOrderType.SELF_PURCHASE);
                Integer integerZicai = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
                BigDecimal batchBillTotalZicai = new BigDecimal(0);
                if (integerZicai != null && integerZicai > 0) {
                    Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                    batchBillTotalZicai = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                cataMap.put("zicai", batchBillTotalZicai);

                mapDep.put("purchaseType", GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
                mapDep.put("dayuStatus", 2);
                Integer integerDh = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
                BigDecimal batchBillTotalDh = new BigDecimal(0);
                if (integerDh != null && integerDh > 0) {
                    Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                    batchBillTotalDh = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                cataMap.put("dinghuo", batchBillTotalDh);

                double add = 0.0;
                Map<String, Object> mapDepCost = new HashMap<>();
                mapDepCost.put("startDate", startDate);
                mapDepCost.put("stopDate", stopDate);
                mapDepCost.put("disId", disId);
                mapDepCost.put("depType", getGbDepartmentTypeMendian());
                mapDepCost.put("dayuStatus", -1);
                mapDepCost.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                double produceSubtotal = 0.0;
                double wasteTotal = 0.0;
                double lossTotal = 0.0;
                double employeeMealTotal = 0.0;
                log.debug("cosssoososososointeger4integer4{}", mapDepCost);
                Map<String, Object> reduceTotalsGreat = gbDepartmentStockReduceService.queryReduceAllTypesTotal(mapDepCost);
                if (reduceTotalsGreat != null) {
                    produceSubtotal = GbDepartmentGoodsStockReduceSupport.toDouble(reduceTotalsGreat.get("produceTotal"));
                    log.debug("cossisisis{}", produceSubtotal);
                    wasteTotal = GbDepartmentGoodsStockReduceSupport.toDouble(reduceTotalsGreat.get("wasteTotal"));
                    lossTotal = GbDepartmentGoodsStockReduceSupport.toDouble(reduceTotalsGreat.get("lossTotal"));
                    employeeMealTotal = GbDepartmentGoodsStockReduceSupport.toDouble(
                            reduceTotalsGreat.get(GbDepartmentGoodsStockReduceSupport.KEY_EMPLOYEE_MEAL_TOTAL));
                    add = produceSubtotal + wasteTotal + lossTotal + employeeMealTotal;
                }

                Map<String, Object> mapDepCostThis = new HashMap<>();
                mapDepCostThis.put("disId", disId);
                mapDepCostThis.put("depType", getGbDepartmentTypeMendian());
                mapDepCostThis.put("dayuStatus", -1);
                mapDepCostThis.put("startDate", startDate);
                mapDepCostThis.put("stopDate", stopDate);
                mapDepCostThis.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                double perStockDouble = 0.0;

                log.debug("thisSttockckckckckkc{}", mapDepCostThis);
                Integer integerPer = gbDepartmentGoodsStockService.queryGoodsStockCount(mapDepCostThis);
                if (integerPer != null && integerPer > 0) {
                    perStockDouble = gbDepartmentGoodsStockService.queryDepGoodsRestTotal(mapDepCostThis);
                }

                double lastStockDouble = 0.0;
                Map<String, Object> mapDepCostLast = new HashMap<>();
                mapDepCostLast.put("disId", disId);
                mapDepCostLast.put("depType", getGbDepartmentTypeMendian());
                mapDepCostLast.put("dayuStatus", -1);
                mapDepCostLast.put("stopDate", startDate);
                mapDepCostLast.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                log.debug("lastStockckckk{}", mapDepCostLast);
                Integer integerPerLast = gbDepartmentGoodsStockService.queryGoodsStockCount(mapDepCostLast);
                if (integerPerLast != null && integerPerLast > 0) {
                    lastStockDouble = gbDepartmentGoodsStockService.queryDepGoodsRestTotal(mapDepCostLast);
                }

                cataMap.put("lastStock", String.format("%.1f", lastStockDouble));
                cataMap.put("perStock", String.format("%.1f", perStockDouble));
                cataMap.put("costTotal", String.format("%.1f", add));
                cataMap.put("costAllTotal", String.format("%.1f", totalCost));
                double v = 0.0;
                if (totalCost > 0) {
                    v = add / totalCost * 100;
                }
                cataMap.put("costPercent", String.format("%.1f", v));
                cataMap.put("produceSubtotal", String.format("%.1f", produceSubtotal));
                cataMap.put("lossTotal", String.format("%.1f", lossTotal));
                cataMap.put("wasteTotal", String.format("%.1f", wasteTotal));
                cataMap.put("employeeMealCostTotal", String.format("%.1f", employeeMealTotal));
                cataMap.put("employeeMealCostAllTotal", String.format("%.1f", employeeMealAll));

                greatEntity.setDailyData(cataMap);
            }
            map123.put("arr", fatherGoodsTreeSet);
        }

        log.debug("======== GB采购分类统计完成 ========");
        return map123;
    }
}
