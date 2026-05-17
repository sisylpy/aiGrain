package com.nongxinle.controller;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.GbTypeUtils.*;

/**
 * 批发商采购商品Controller
 */
@RestController
@RequestMapping("gbdistributerpurchasegoods")
@Slf4j
@RequiredArgsConstructor
public class GbDistributerPurchaseGoodsController {

    private final GbDistributerPurchaseGoodsService gbDpgService;
    private final GbDepartmentService gbDepartmentService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerService gbDistributerService;
    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    private final GbDepartmentUserService gbDepartmentUserService;
    private final GbDistributerPurchaseGoodsDetailListService gbDistributerPurchaseGoodsDetailListService;
    private final GbDistributerPurchaseGoodsDisPurchaseDateService gbDistributerPurchaseGoodsDisPurchaseDateService;
    private final GbDistributerPurchaseGoodsDisPurchaseCataService gbDistributerPurchaseGoodsDisPurchaseCataService;
    private final GbDistributerPurchaseGoodsSellerUpdatePurGoodsService gbDistributerPurchaseGoodsSellerUpdatePurGoodsService;
    private final GbDistributerPurchaseGoodsFinishPurGoodsToStockService gbDistributerPurchaseGoodsFinishPurGoodsToStockService;




    /**
     * 删除订货批次->"采购商品"
     *
     * @param id 采购商品id
     * @return ok
     */
    @RequestMapping(value = "/supplierInitWeightPurItem/{id}")
    @ResponseBody
    public R supplierInitWeightPurItem(@PathVariable Integer id) {

        GbDistributerPurchaseGoodsEntity purGoods = gbDpgService.getById(id);
        purGoods.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.SHARED_TO_SUPPLIER);
        purGoods.setGbDpgOrdersWeightAmount(0);
        gbDpgService.updateById(purGoods);

        Map<String, Object> map = new HashMap<>();
        map.put("purGoodsId", id);
        List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
        for (GbDepartmentOrdersEntity orders : ordersEntities) {
            orders.setGbDoStatus(GbConstants.DepartmentOrderStatus.NEW);
            orders.setGbDoBuyStatus(GbConstants.OrderBuyStatus.SHARED_TO_SUPPLIER);
            gbDepartmentOrdersService.update(orders);
        }

        return R.ok();

    }



    @RequestMapping(value = "/getGbPurGoodsDetailList", method = RequestMethod.POST)
    @ResponseBody
    public R getGbPurGoodsDetailList(

            @RequestParam Integer disGoodsId,
            @RequestParam String startDate,
            @RequestParam String stopDate) {

        try {
            Map<String, Object> mapResult =
                    gbDistributerPurchaseGoodsDetailListService.buildPurGoodsDetailList(disGoodsId, startDate, stopDate);
            return R.ok().put("data", mapResult);

        } catch (Exception e) {
            log.warn("getGbPurGoodsDetailList failed", e);
            return R.error("获取商品列表失败：" + e.getMessage());
        }
    }


    /**
     * DISTRIBUTE
     * 批发商获取进货商品列表
     *
     * @param disId 批发商ID
     * @return 进货商品列表
     */
    @RequestMapping(value = "/getPurchaseGoodsGbWithTabCount/{disId}")
    @ResponseBody
    public R getPurchaseGoodsGbWithTabCount(@PathVariable Integer disId) {

        Map<String, Object> map4 = new HashMap<>();
        map4.put("disId", disId);
        map4.put("orderStatus", 3);
        map4.put("orderEqualBuyStatus", 0);
        map4.put("supplierBuy", -1);
        map4.put("purType", 0);
        log.debug("map4444444whyyyy111" + map4);
        List<GbDistributerPurchaseGoodsEntity> purchaseToday = gbDpgService.querySimplePurGoods(map4);
        if (purchaseToday != null && !purchaseToday.isEmpty()) {
            log.debug("kanaknGbgoods" + purchaseToday.get(0).getGbDistributerGoodsEntity());
        }
        Map<String, Object> map1 = new HashMap<>();
        map1.put("disId", disId);
        map1.put("status", 3);
        map1.put("equalBuyStatus", 0);
        map1.put("notEqualOrderType", 9);
        log.debug("mapp111aaaaaaa" + map1);

        int purCount = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        map1.put("equalBuyStatus", null);
        map1.put("dayuBuyStatus", 0);
        map1.put("dayuStatus", -2);
        log.debug("mapp111oneoeneoene11111" + map1);
        int purCountOne = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("arr", purchaseToday);
        map3.put("orderAmount", purCount);
        map3.put("wxAmount", purCountOne);
        // 查询完整批发商信息（含所有部门列表）
        map3.put("disInfo", gbDistributerService.queryDistributerWithAllDepartments(disId));

        return R.ok().put("data", map3);
    }

    /**
     * DISTRIBUTE
     * 批发商获取进货商品列表
     *
     * @param purDepId 采购部门 id
     * @return 进货商品列表
     */
    @RequestMapping(value = "/getStorePurchaseGoodsGbWithTabCount/{purDepId}")
    @ResponseBody
    public R getStorePurchaseGoodsGbWithTabCount(@PathVariable Integer purDepId) {

        Map<String, Object> map4 = new HashMap<>();
        map4.put("purDepId", purDepId);
        map4.put("orderStatus", 3);
        map4.put("orderEqualBuyStatus", 0);
        map4.put("supplierBuy", -1);
        map4.put("purType", 0);
        log.info("map4444444whyyyy111" + map4);
        List<GbDistributerPurchaseGoodsEntity> purchaseToday = gbDpgService.querySimplePurGoods(map4);
        if (purchaseToday != null && !purchaseToday.isEmpty()) {
            log.info("kanaknGbgoods" + purchaseToday.get(0).getGbDistributerGoodsEntity());
        }
        Map<String, Object> map1 = new HashMap<>();
        map1.put("toDepId", purDepId);
        map1.put("status", 3);
        map1.put("equalBuyStatus", 0);
        map1.put("notEqualOrderType", 9);
        log.debug("mapp111aaaaaaa" + map1);

        int purCount = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        map1.put("equalBuyStatus", null);
        map1.put("dayuBuyStatus", 0);
        map1.put("dayuStatus", -2);
        log.info("mapp111oneoeneoene11111" + map1);
        int purCountOne = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("arr", purchaseToday);
        map3.put("orderAmount", purCount);
        map3.put("wxAmount", purCountOne);
        // 查询完整批发商信息（含所有部门列表）
        GbDepartmentEntity byId = gbDepartmentService.getById(purDepId);
        map3.put("disInfo", gbDistributerService.queryDistributerWithAllDepartments(byId.getGbDepartmentDisId()));

        return R.ok().put("data", map3);
    }


    @RequestMapping(value = "/purUserGetPurGoodsInfo", method = RequestMethod.POST)
    @ResponseBody
    public R purUserGetPurGoodsInfo(@RequestParam Integer purUserId, @RequestParam Integer disGoodsId) {
        Map<String, Object> map = new HashMap<>();
        map.put("purUserId", purUserId);
        map.put("disGoodsId", disGoodsId);
        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDpgService.queryPurchaseGoodsLastItem(map);
        if (purchaseGoodsEntity != null) {
            return R.ok().put("data", purchaseGoodsEntity.getGbDpgBuyPrice());
        } else {
            return R.error(-1, "没有采购商品历史");
        }
    }

    @RequestMapping(value = "/finishPurGoodsToStock", method = RequestMethod.POST)
    @ResponseBody
    public R finishPurGoodsToStock(@RequestBody GbDistributerPurchaseGoodsEntity purGoods) {
        gbDistributerPurchaseGoodsFinishPurGoodsToStockService.finishPurGoodsToStock(purGoods);
        return R.ok();
    }

    /**
     * GB系统采购日期统计
     * @param disId 批发商ID
     * @param startDate 开始日期
     * @param stopDate 结束日期
     * @return 统计数据
     */
    @RequestMapping(value = "/disGetPurchaseDate", method = RequestMethod.POST)
    @ResponseBody
    public R disGetPurchaseDate(Integer disId, String startDate, String stopDate) {
        Map<String, Object> result =
                gbDistributerPurchaseGoodsDisPurchaseDateService.buildDisPurchaseDate(disId, startDate, stopDate);
        if (result == null) {
            return R.error(-1, "没有数据");
        }
        return R.ok().put("data", result);
    }

    /**
     * GB系统采购分类统计
     * @param disId 批发商ID
     * @param startDate 开始日期
     * @param stopDate 结束日期
     * @param purUserId 采购用户ID
     * @param supplierId 供应商ID
     * @return 分类统计数据
     */
    @RequestMapping(value = "/disGetPurchaseCata", method = RequestMethod.POST)
    @ResponseBody
    public R disGetPurchaseCata(Integer disId, String startDate, String stopDate,
                                String purUserId, String supplierId) {
        Map<String, Object> map123 = gbDistributerPurchaseGoodsDisPurchaseCataService.buildDisPurchaseCata(
                disId, startDate, stopDate, purUserId, supplierId);
        if (map123 == null) {
            return R.error(-1, "没有数据");
        }
        return R.ok().put("data", map123);
    }


    /**
     * 单个采购员在日期区间内每日采购汇总（自采 zicai、订货 dinghuo）。
     * 仅返回区间内<strong>有采购记录</strong>的日期，无采购日不放入列表。
     * 接口: /gbdistributerpurchasegoods/getPurUserDate
     *
     * @param purUserId 部门用户 id（gb_department_user），非部门 id
     */
    @RequestMapping(value = "/getPurUserDate", method = RequestMethod.POST)
    @ResponseBody
    public R getPurUserDate(@RequestParam String startDate, @RequestParam String stopDate,
                            @RequestParam Integer disId, @RequestParam Integer purUserId) {
        GbDepartmentUserEntity user = gbDepartmentUserService.getById(purUserId);
        if (user == null) {
            return R.error("采购员不存在");
        }
        if (user.getGbDuDistributerId() != null && !user.getGbDuDistributerId().equals(disId)) {
            return R.error("采购员不属于该批发商");
        }

        log.info("【getPurUserDate】disId={}, purUserId={}, startDate={}, stopDate={}",
                disId, purUserId, startDate, stopDate);

        int daySpan = 0;
        if (!startDate.equals(stopDate)) {
            daySpan = getHowManyDaysInPeriod(stopDate, startDate);
        }

        List<Map<String, Object>> zicaiArr = new ArrayList<>();
        for (int i = 0; i < daySpan + 1; i++) {
            String whichDay = i == 0 ? startDate : afterWhatDay(startDate, i);

            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("day", whichDay);

            Map<String, Object> dayQuery = new HashMap<>();
            dayQuery.put("date", whichDay);
            dayQuery.put("disId", disId);
            dayQuery.put("dayuStatus", 1);
            dayQuery.put("purUserId", purUserId);

            dayQuery.put("purchaseType", GbConstants.PurchaseOrderType.SELF_PURCHASE);
            BigDecimal zicaiTotal = BigDecimal.ZERO;
            Integer zicaiCount = gbDpgService.queryPurchaseGoodsCount(dayQuery);
            if (zicaiCount != null && zicaiCount > 0) {
                Double z = gbDpgService.queryPurchaseGoodsSubTotal(dayQuery);
                zicaiTotal = new BigDecimal(z != null ? z : 0).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("zicai", zicaiTotal);
            dayQuery.remove("purchaseType");

            dayQuery.put("purchaseType", GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
            BigDecimal dinghuoTotal = BigDecimal.ZERO;
            Integer dinghuoCount = gbDpgService.queryPurchaseGoodsCount(dayQuery);
            if (dinghuoCount != null && dinghuoCount > 0) {
                Double d = gbDpgService.queryPurchaseGoodsSubTotal(dayQuery);
                dinghuoTotal = new BigDecimal(d != null ? d : 0).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("dinghuo", dinghuoTotal);

            boolean hasPurchase = (zicaiCount != null && zicaiCount > 0)
                    || (dinghuoCount != null && dinghuoCount > 0);
            if (hasPurchase) {
                zicaiArr.add(dayMap);
            }
        }
        user.setZicaiArr(zicaiArr);

        return R.ok().put("data", user);
    }



    @RequestMapping(value = "/getPurchaseUserGoods", method = RequestMethod.POST)
    @ResponseBody
    public R getPurchaseUserGoods(String purUserId,  String startDate, String stopDate) {

        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("typeNotEqual", 9);
        queryMap.put("dayuStatus", 2);
        queryMap.put("startDate", startDate);
        queryMap.put("stopDate", stopDate);
        queryMap.put("purUserId", purUserId);
        queryMap.put("dateOrder", 1);
        log.debug("pururrus" + queryMap);
        List<GbDistributerGoodsEntity> goodsList = gbDpgService.queryDisTreeGoodsWithPurList(queryMap);
        gbDpgService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
        return R.ok().put("data", goodsList);

    }

    /**
     * 获取批发商采购统计信息
     */
    @RequestMapping(value = "/getGbPurGoodsStatisticsForDis", method = RequestMethod.POST)
    @ResponseBody
    public R getGbPurGoodsStatisticsForDis(@RequestParam String supplierIds,
                                           @RequestParam String purUserIds,
                                           @RequestParam Integer disId,
                                           @RequestParam String startDate,
                                           @RequestParam String stopDate) {

        try {
            // 构建查询参数
            Map<String, Object> map = new HashMap<>();

            if (!purUserIds.equals("-1")) {
                String[] arrGb = purUserIds.split(",");
                List<String> idsGb = new ArrayList<>();
                for (String idGb : arrGb) {
                    idsGb.add(idGb);
                    if (idsGb.size() > 0) {
                        map.put("purUserIds", idsGb);
                    }
                }
            }
            if (!supplierIds.equals("-1")) {
                String[] arrGb = supplierIds.split(",");
                List<String> idsGb = new ArrayList<>();
                for (String idGb : arrGb) {
                    idsGb.add(idGb);
                    if (idsGb.size() > 0) {
                        map.put("supplierIds", idsGb);
                    }
                }
            }

            map.put("disId", disId);
            map.put("startDate", startDate);
            map.put("stopDate", stopDate);
            map.put("dayuStatus", 2);
            map.put("typeNotEqual", 9);  // 排除type=9的记录

            Integer integer = gbDpgService.queryGbPurchaseGoodsCount(map);
            double purtotal = 0.0;
            if (integer > 0) {
                purtotal = gbDpgService.queryPurchaseGoodsSubTotal(map);
            }

            //采购员数据
            List<Map<String, Object>> purUserList = new ArrayList<>();
            log.debug("puruuruuruuruuruuuruur" + map);
            map.put("disGoodsGrandId", null);
            List<GbDepartmentUserEntity> departmentUserEntities = gbDpgService.queryPurUserList(map);
            if (departmentUserEntities.size() > 0) {
                for (GbDepartmentUserEntity departmentUserEntity : departmentUserEntities) {
                    Map<String, Object> mapPurUser = new HashMap<>();
                    mapPurUser.put("purUserName", departmentUserEntity.getGbDuWxNickName());
                    map.put("purUserId", departmentUserEntity.getGbDepartmentUserId());
                    map.put("supplierId", null);
                    Double subTotal = 0.0;
                    Integer integerD = gbDpgService.queryGbPurchaseGoodsCount(map);
                    if (integerD > 0) {
                        subTotal = gbDpgService.queryPurchaseGoodsSubTotal(map);
                    }

                    mapPurUser.put("totalAmount", String.format("%.1f", subTotal));
                    purUserList.add(mapPurUser);
                }
            }


            //供货商数据
            List<Map<String, Object>> supplerList = new ArrayList<>();
            map.put("disGoodsGrandId", null);
            List<NxJrdhSupplierEntity> supplierEntities = gbDpgService.queryDisPurGoodsSupplierList(map);
            if (supplierEntities.size() > 0) {
                for (NxJrdhSupplierEntity supplierEntity : supplierEntities) {
                    Map<String, Object> mapPurUser = new HashMap<>();
                    mapPurUser.put("supplierName", supplierEntity.getNxJrdhsSupplierName());
                    map.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                    map.put("purUserId", null);
                    Double subTotal = 0.0;

                    log.debug("suppsmap" + map);
                    Integer integerS = gbDpgService.queryGbPurchaseGoodsCount(map);
                    if (integerS > 0) {
                        subTotal = gbDpgService.queryPurchaseGoodsSubTotal(map);
                    }
                    mapPurUser.put("totalAmount", String.format("%.1f", subTotal));
                    supplerList.add(mapPurUser);
                }
            }

            //按采购频率数据
            map.put("supplierId", null);
            log.debug("tootititittiitqueryGbPurchaseGoodsTopTimes" + map);
            List<GbDistributerGoodsEntity> topGoods = new ArrayList<>();

            //按采购金额数据
            List<GbDistributerGoodsEntity> topGoodsWeight  = new ArrayList<>();
            double topSubtotal = 0.0;

            List<GbDistributerGoodsEntity> topGoodsPrice = new ArrayList<>();

            if(integer > 0){
                topGoods  = gbDpgService.queryGbPurchaseGoodsTopTimes(map);
                topGoodsWeight = gbDpgService.queryGbPurchaseGoodsTopSubtotal(map);
                topSubtotal = gbDpgService.queryGbPurchaseSubtotalTopSubtotal(map);
                //按价格浮动
                map.put("supplierId", null);
                log.debug("tootititittiit" + map);
                topGoodsPrice = gbDpgService.queryGbPurchaseGoodsTopPriceFluctuation(map);
            }
            // 构建返回数据
            Map<String, Object> result = new HashMap<>();

            result.put("purTotal", String.format("%.1f", purtotal));
            result.put("purUserData", purUserList);
            result.put("supplierData", supplerList);
            result.put("topTimesGoods", topGoods);
            result.put("topSubtotalGoods", topGoodsWeight);
            result.put("topGoodsPrice", topGoodsPrice);
            BigDecimal bigDecimal = new BigDecimal(0);

            if(purtotal > 0){
                double v = topSubtotal / purtotal;
                bigDecimal = new BigDecimal(v).multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
            }

            result.put("topSubtotalGoodsPercent", bigDecimal);
            result.put("topSubtotalGoodsSubtotal", new BigDecimal(topSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP));
            return R.ok().put("data", result);

        } catch (Exception e) {
            log.warn("getGbPurGoodsStatisticsForDis failed", e);
            return R.error(-1, "没有数据");
        }
    }

    /**
     * 获取批发商成本统计信息
     */
    @RequestMapping(value = "/getGbCostGoodsStatistics", method = RequestMethod.POST)
    @ResponseBody
    public R getGbCostGoodsStatistics(@RequestParam String supplierIds,
                                      @RequestParam String purUserIds,
                                      @RequestParam Integer disId,
                                      @RequestParam String startDate,
                                      @RequestParam String stopDate) {

        try {
            // 构建查询参数
            Map<String, Object> map = new HashMap<>();

            if (!purUserIds.equals("-1")) {
                String[] arrGb = purUserIds.split(",");
                List<String> idsGb = new ArrayList<>();
                for (String idGb : arrGb) {
                    idsGb.add(idGb);
                    if (idsGb.size() > 0) {
                        map.put("purUserIds", idsGb);
                    }
                }
            }
            if (!supplierIds.equals("-1")) {
                String[] arrGb = supplierIds.split(",");
                List<String> idsGb = new ArrayList<>();
                for (String idGb : arrGb) {
                    idsGb.add(idGb);
                    if (idsGb.size() > 0) {
                        map.put("supplierIds", idsGb);
                    }
                }
            }

            map.put("disId", disId);
            map.put("startDate", startDate);
            map.put("stopDate", stopDate);

            Integer integer = gbDepartmentStockReduceService.queryReduceTypeCount(map);
            String costTotal = "";
            if (integer > 0) {
                // 查询所有类型的 subtotal 总和
                Map<String, Object> totalMap = gbDepartmentStockReduceService.queryReduceAllTypesTotal(map);
                double produceTotal = totalMap.get("produceTotal") != null ? Double.parseDouble(totalMap.get("produceTotal").toString()) : 0;
                double wasteTotal = totalMap.get("wasteTotal") != null ? Double.parseDouble(totalMap.get("wasteTotal").toString()) : 0;
                double lossTotal = totalMap.get("lossTotal") != null ? Double.parseDouble(totalMap.get("lossTotal").toString()) : 0;
                double returnTotal = totalMap.get("returnTotal") != null ? Double.parseDouble(totalMap.get("returnTotal").toString()) : 0;
                double v = produceTotal + wasteTotal + lossTotal + returnTotal;
                costTotal = String.format("%.1f", v);
            }

            //按支出成本 (type=1)
            log.debug("tootititittiitProduce" + map);
            map.put("type", 1);
            List<GbDistributerGoodsEntity> topGoodsProduce = gbDepartmentStockReduceService.queryStockSubtotalTopTimes(map);

            //按损耗成本 (type=3)
            map.put("type", 3);
            List<GbDistributerGoodsEntity> topGoodsLoss = gbDepartmentStockReduceService.queryStockSubtotalTopTimes(map);

            //按废弃成本 (type=2)
            map.put("type", 2);
            List<GbDistributerGoodsEntity> topGoodsWaste = gbDepartmentStockReduceService.queryStockSubtotalTopTimes(map);

            //按日支出
            log.debug("tootititittiitDDDD" + map);
            List<Map<String, Object>> topDayCost = gbDepartmentStockReduceService.queryGbPurchaseGoodsTopDay(map);


            // 构建返回数据
            Map<String, Object> result = new HashMap<>();

            result.put("costTotal", costTotal);
            result.put("topGoodsProduce", topGoodsProduce);
            result.put("topGoodsLoss", topGoodsLoss);
            result.put("topGoodsWaste", topGoodsWaste);
            result.put("topDayCost", topDayCost);
            return R.ok().put("data", result);

        } catch (Exception e) {
            log.warn("getGbCostGoodsStatistics failed", e);
            return R.error(-1, "没有数据");
        }
    }

    /**
     * 供货商获取未称重出库采购商品
     */
    @RequestMapping(value = "/supplierGetUnWeightOutPurGoods/{batchId}")
    @ResponseBody
    public R supplierGetUnWeightOutPurGoods(@PathVariable Integer batchId) {
        Map<String, Object> map = new HashMap<>();
        map.put("batchId", batchId);
        // 状态 3 表示称重完成
        map.put("status", 1);
        log.debug("supplierGetUnWeightOutPurGoods: " + map);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = gbDpgService.queryPurchaseGoodsWithOrdersByBatch(map);
        return R.ok().put("data", purchaseGoodsEntityList);
    }

    /**
     * 供货商修改采购商品
     */
    @ResponseBody
    @RequestMapping("/sellerUpdatePurGoods")
    public R sellerUpdatePurGoods(@RequestBody GbDistributerPurchaseGoodsEntity purchaseGoodsEntity) {
        GbDistributerPurchaseGoodsEntity updated =
                gbDistributerPurchaseGoodsSellerUpdatePurGoodsService.sellerUpdatePurGoods(purchaseGoodsEntity);
        return R.ok().put("data", updated);
    }




    @RequestMapping(value = "/getGbPurGoodsStatisticsSeachDate", method = RequestMethod.POST)
    @ResponseBody
    public R getGbPurGoodsStatisticsSeachDate(@RequestParam Integer supplierId,
                                              @RequestParam Integer purUserId,
                                              @RequestParam Integer disId,
                                              @RequestParam String startDate,
                                              @RequestParam String stopDate,
                                              @RequestParam Integer greatId

    ) {

        try {
            // 构建查询参数
            Map<String, Object> map = new HashMap<>();

            if (purUserId != -1) {
                map.put("purUserId", purUserId);
            } else if (supplierId != -1) {
                map.put("supplierId", supplierId);
            }
            if (greatId != -1) {
                map.put("disGoodsGreatId", greatId);
            }
            map.put("disId", disId);
            map.put("startDate", startDate);
            map.put("stopDate", stopDate);
            map.put("dayuStatus", 2);
            map.put("typeNotEqual", 9);

            // 获取本期采购统计
            Integer purCount = gbDpgService.queryGbPurchaseGoodsCount(map);
            if (purCount == 0) {
                log.debug("rriirr0000000");
                return R.error(-1, "没有数据");
            }

            Double allDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);

            List<GbDepartmentUserEntity> departmentUserEntities = gbDpgService.queryPurUserList(map);
            List<NxJrdhSupplierEntity> supplierEntities = gbDpgService.queryDisPurGoodsSupplierList(map);

            map.put("supplierBuy", -1);
            map.put("dayuStatus", 2);
            //自采数据
            Integer integerZicai = gbDpgService.queryGbPurchaseGoodsCount(map);
            BigDecimal batchBillTotalZicai = new BigDecimal(0);
            int zicaiGoodsCount = 0;
            if (integerZicai > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);
                batchBillTotalZicai = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                log.debug("ziciagouttt" + map);
                zicaiGoodsCount = gbDpgService.queryGbGoodsCount(map);
                log.debug("自采商品数量查询结果: " + zicaiGoodsCount + " 个不同商品");
            }

            //订货数据
            map.put("supplierBuy", 1);
            map.put("dayuStatus", 2);
            map.put("batchDayuStatus", 2);
            Integer integerDh = gbDpgService.queryGbPurchaseGoodsCount(map);
            int dinghuoGoodsCount = 0;
            BigDecimal batchBillTotalDh = new BigDecimal(0);
            if (integerDh > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);
                batchBillTotalDh = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                dinghuoGoodsCount = gbDpgService.queryGbGoodsCount(map);
            }


            //kuun
            map.put("dayuStatus", null);
            Double aDouble = gbDepartmentGoodsStockService.queryDepGoodsRestTotal(map);
            map.put("restWeight", 0);
            Integer stockCount = gbDepartmentGoodsStockService.queryDisStockGoodsCount(map);
            // 构建返回数据
            Map<String, Object> result = new HashMap<>();
            result.put("allDouble", String.format("%.1f", allDouble));
            result.put("dinghuo", batchBillTotalDh);
            result.put("dinghuoCount", dinghuoGoodsCount);
            result.put("zicai", batchBillTotalZicai);
            result.put("zicaiCount", zicaiGoodsCount);
            result.put("stockTotal", String.format("%.1f", aDouble));
            result.put("stockCount", stockCount);
            result.put("supplierArr", supplierEntities);
            result.put("depUserArr", departmentUserEntities);
            return R.ok().put("data", result);

        } catch (Exception e) {
            log.warn("getGbPurGoodsStatisticsSeachDate failed", e);
            return R.error("获取统计信息失败：" + e.getMessage());
        }
    }



    @RequestMapping(value = "/getGbPurGoodsListSearchDate", method = RequestMethod.POST)
    @ResponseBody
    public R getGbPurGoodsListSearchDate(

            @RequestParam Integer disId,
            @RequestParam Integer greatId,
            @RequestParam String startDate,
            @RequestParam String stopDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit) {

        try {
            // 构建查询参数
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("disId", disId);
            queryMap.put("startDate", startDate);
            queryMap.put("stopDate", stopDate);
            queryMap.put("typeNotEqual", 9);
            queryMap.put("dayuStatus", 2);
            queryMap.put("offset", (page - 1) * limit);
            queryMap.put("limit", limit);
            if (greatId != -1) {
                queryMap.put("disGoodsGreatId", greatId);
            }

            log.debug("lisisisiisisisnnnn00000000aaa" + queryMap);
            // 获取商品总数
            Integer totalCount = gbDpgService.queryGbDisGoodsTreeCount(queryMap);
            log.debug("totalCount: " + totalCount); // 新增日志
            Integer totalPages = (int) Math.ceil((double) totalCount / limit);

            // 获取商品列表
            queryMap.put("dateOrder", 1);
            log.debug("查询商品map" + queryMap);
            log.debug("开始查询商品列表..."); // 新增日志
            List<GbDistributerGoodsEntity> goodsList = gbDpgService.queryDisTreeGoodsWithPurList(queryMap);
            gbDpgService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
            log.debug("商品列表查询完成，数量: " + (goodsList != null ? goodsList.size() : "null")); // 新增日志


            // 构建返回数据
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", totalCount);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("goodsList", goodsList);
            log.debug("reuslt" );
            log.debug("reuslt" + result);

            return R.ok().put("data", result);

        } catch (Exception e) {
            log.warn("getGbPurGoodsListSearchDate failed", e);
            return R.error("获取商品列表失败：" + e.getMessage());
        }
    }






}
