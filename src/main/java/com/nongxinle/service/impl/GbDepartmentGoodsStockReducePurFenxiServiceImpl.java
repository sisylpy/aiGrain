package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbDepFoodGoodsSalesEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.service.GbDepFoodGoodsSalesService;
import com.nongxinle.service.GbDepFoodSalesService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentGoodsStockReducePurFenxiService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceWithDayDataService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nongxinle.utils.DateUtils.afterWhatDay;
import static com.nongxinle.utils.DateUtils.getHowManyDaysInPeriod;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbDepartmentGoodsStockReducePurFenxiServiceImpl implements GbDepartmentGoodsStockReducePurFenxiService {

    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentGoodsStockReduceService;
    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDepFoodGoodsSalesService gbDepFoodGoodsSalesService;
    private final GbDepFoodSalesService gbDepFoodSalesService;
    private final GbDistributerFoodGoodsService gbDistributerFoodGoodsService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbDepartmentGoodsStockReduceWithDayDataService gbDepartmentGoodsStockReduceWithDayDataService;


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
            if (!GbDepartmentGoodsStockReduceSupport.isActiveFoodGoodsLine(line) || line.getGbDfgFoodId() == null) {
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
            BigDecimal amt = GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(s.getGbDfsAmount());
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
                ingredientByFoodId.merge(fid, GbDepartmentGoodsStockReduceSupport.parseGoodsAmountString(r.getGbDfgsGoodsAmount()), BigDecimal::add);
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

    @Override
    public GbDistributerGoodsEntity buildPurGoodsFenxi(Integer disGoodsId, String startDate, String stopDate, Integer supplierId, Integer purUserId) {
        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(disGoodsId);
        if (goodsEntity == null) {
            throw new IllegalArgumentException("商品不存在");
        }


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
        log.debug("wsupsspspspsp" + map1);
        Integer integerPur = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(map1);
        Map<String, Object> reduceExistsMap = GbDepartmentGoodsStockReduceSupport.buildReduceParamsForGoodsDay(
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

            String prevDayStr = com.nongxinle.utils.GbDateTimeUtils.formatDay(
                    com.nongxinle.utils.GbDateTimeUtils.parseLocalDay(startDate).minusDays(1));
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
            log.debug("whhahahhaha" + map);
            purCount = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(map);
            searchItem.put("type", "none");
            if (purCount > 0) {
                log.debug("subsososoososos" + map);
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
                    log.debug("searittmeee" + searchItem);
                    searchPurCount = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(map);
                    if (searchPurCount > 0) {
                        log.debug("subsososoososos" + map);
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

                log.debug("epsososososo==0");
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
                    processDailyBusinessData(mapDay, searchProduceDayValue, searchLossDayValue, searchWasteDayValue, searchReturnDayValue);
                    processDailyPurSubtotalData(mapDay, whichDay, purchaseDayValue);
                    foodIngredientSalesDayValue.add(String.format("%.1f",
                            gbDepartmentGoodsStockReduceWithDayDataService.sumFoodGoodsSalesIngredient(disGoodsId, whichDay, null)));
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
                            log.debug("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        supplierMap.put("supplierPriceValue", thisSupplierPriceValue);
                        supplierMap.put("supplierWeightValue", thisSupplierWeightValue);

                        log.debug("suplisiisisiisis" + spplierValueList.size());
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
                            log.debug("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        purUserMap.put("supplierPriceValue", thisSupplierPriceValue);
                        purUserMap.put("supplierWeightValue", thisSupplierWeightValue);

                        log.debug("suplisiisisiisis" + spplierValueList.size());
                        purUserValueList.add(purUserMap);
                    }
                }

            } else {

                String substring = startDate.substring(8, 10);
                dateList.add(substring);
                mapDay.put("date", startDate);
                mapDay.put("disGoodsId", disGoodsId);
                mapDay.put("typeNotEqual", 9);

                log.debug("wokkkkkkondaayayaayayayy" + mapDay);

                processDailyBusinessData(mapDay, produceDayValue, lossDayValue, wasteDayValue, returnDayValue);
                processDailyBusinessData(mapDay, searchProduceDayValue, searchLossDayValue, searchWasteDayValue, searchReturnDayValue);
                processDailyPurSubtotalData(mapDay, startDate, purchaseDayValue);
                foodIngredientSalesDayValue.add(String.format("%.1f",
                        gbDepartmentGoodsStockReduceWithDayDataService.sumFoodGoodsSalesIngredient(disGoodsId, startDate, null)));
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
                            log.debug("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        supplierMap.put("supplierPriceValue", thisSupplierPriceValue);
                        supplierMap.put("supplierWeightValue", thisSupplierWeightValue);

                        log.debug("suplisiisisiisis" + spplierValueList.size());
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
                            log.debug("eveyeedayyaaysupspspsliie" + mapDay);

                            // 调用方法获取这个供货商这一天的数据
                            processDailyPurchaseDataForSupplier(mapDay, thisSupplierPriceValue, thisSupplierWeightValue);
                        }

                        purUserMap.put("supplierPriceValue", thisSupplierPriceValue);
                        purUserMap.put("supplierWeightValue", thisSupplierWeightValue);

                        log.debug("suplisiisisiisis" + spplierValueList.size());
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
                ingredientSalesPeriodTotal += GbDepartmentGoodsStockReduceSupport.toDouble(s);
            }

            mapResult.put("foodIngredientSalesQtyTotal", String.format("%.1f", ingredientSalesPeriodTotal));

            double doubleProduceWeightPeriod =
                    GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockReduceService.queryReduceProduceWeightTotal(reduceExistsMap));
            mapResult.put("totalProduceWeight",
                    new BigDecimal(doubleProduceWeightPeriod).setScale(1, BigDecimal.ROUND_HALF_UP).toString());

            double purchaseVsIngredientDiff = doubleProduceWeightPeriod - ingredientSalesPeriodTotal;
            log.debug("fenxi produceWeight=" + doubleProduceWeightPeriod + " ingredientSales="
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
                goodsDoubleRest = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(mapDepStock));
                goodsDoubleRestV = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockService.queryDepStockRestSubtotal(mapDepStock));
                goodsEntity.setGoodsWeightTotalString(String.format("%.1f", goodsDoubleRest));
                goodsEntity.setGoodsStockTotalString(String.format("%.1f", goodsDoubleRestV));
                log.debug("stoskckkckckc" + goodsDoubleRest + "vvvvv" + goodsDoubleRestV);

                //serchData
                if (supplierId != -1 || purUserId != -1) {
                    if (supplierId != -1) {
                        mdapDepStockSearch.put("supplierId", supplierId);
                    } else {
                        mdapDepStockSearch.put("purUserId", purUserId);
                    }
                    log.debug("purrususuerr" + mdapDepStockSearch);
                    Integer integer3S = gbDepartmentGoodsStockService.queryGoodsStockCount(mdapDepStockSearch);
                    if (integer3S > 0) {
                        searchGoodsDoubleRest = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockService.queryDepGoodsRestWeightTotal(mdapDepStockSearch));
                        searchGoodsDoubleRestV = GbDepartmentGoodsStockReduceSupport.nzD(gbDepartmentGoodsStockService.queryDepStockRestSubtotal(mdapDepStockSearch));
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
                        log.debug("zazizzzoozo" + mdapDepStockSearch);
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
                    log.debug("goodsDoubleCostgoodsDoubleCostllll" + goodsDoubleTotalWeight);

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
                log.debug("reedoddddmap" + mapDepStock);
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
                    log.debug("propenene" + goodsDoubleProduce + " ostweiicei" + goodsDoubleTotalWeight);
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

                log.debug("ggogoogogsaleepddid" + proPercent);
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


                log.debug("searpurororoortotaooa" + searchGoodsDoubleTotalSubtotal);
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

        return goodsEntity;
    }

    private void processDailyPurchaseDataForSupplier(Map<String, Object> mapDay, List<String> supplierPriceValue, List<String> supplierWeightValue) {
        int purCountDay = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(mapDay);
        if (purCountDay == 1) {
            String price = gbDistributerPurchaseGoodsService.queryPurchaseGoodsPrice(mapDay);
            String weight = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeight(mapDay);

            if (price == null || weight == null) {
                supplierPriceValue.add("0.0");
                supplierWeightValue.add("0.0");
            } else {
                if (price.trim().isEmpty()) {
                    price = "0";
                }
                if (weight.trim().isEmpty()) {
                    weight = "0";
                }

                supplierPriceValue.add(new BigDecimal(price).setScale(1, RoundingMode.HALF_UP).toString());
                supplierWeightValue.add(new BigDecimal(weight).setScale(1, RoundingMode.HALF_UP).toString());
            }
        } else if (purCountDay > 1) {
            Double subTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapDay);
            Double weightTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsWeightTotal(mapDay);

            if (subTotal == null || weightTotal == null || weightTotal == 0) {
                supplierPriceValue.add("0.0");
                supplierWeightValue.add("0.0");
            } else {
                double v1 = subTotal / weightTotal;
                double v2 = weightTotal / purCountDay;

                supplierPriceValue.add(new BigDecimal(v1).setScale(1, RoundingMode.HALF_UP).toString());
                supplierWeightValue.add(new BigDecimal(v2).setScale(1, RoundingMode.HALF_UP).toString());
            }
        } else {
            supplierPriceValue.add("0");
            supplierWeightValue.add("0");
        }
    }

    private void processDailyBusinessData(Map<String, Object> mapDay, List<String> produceDayValue,
            List<String> lossDayValue, List<String> wasteDayValue, List<String> returnDayValue) {
        Map<String, Object> reduceParams = GbDepartmentGoodsStockReduceSupport.buildReduceParamsFromFenxiMapDay(mapDay);
        Map<String, Object> w = gbDepartmentGoodsStockReduceService.queryReduceTypeWeightTotalsByScope(reduceParams);
        log.debug("onda0101001010 {}", mapDay);
        double produceW = GbDepartmentGoodsStockReduceSupport.toDouble(w != null ? w.get("produceWeight") : null);
        double lossW = GbDepartmentGoodsStockReduceSupport.toDouble(w != null ? w.get("lossWeight") : null);
        double wasteW = GbDepartmentGoodsStockReduceSupport.toDouble(w != null ? w.get("wasteWeight") : null);
        double returnW = GbDepartmentGoodsStockReduceSupport.toDouble(w != null ? w.get("returnWeight") : null);
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
        log.debug("onda0101001010 {}", mapDay);
        Map<String, Object> map = new HashMap<>();
        map.put("date", whichDay);
        if (integer1 != null && integer1 > 0) {
            Double subTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapDay);
            map.put("purSubtotal", String.format("%.1f", subTotal));
        } else {
            map.put("purSubtotal", 0);
        }
        purchaseDayValue.add(map);
    }

}
