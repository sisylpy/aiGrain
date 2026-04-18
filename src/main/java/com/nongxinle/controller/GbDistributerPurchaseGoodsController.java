package com.nongxinle.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nongxinle.entity.*;
import com.nongxinle.mapper.GbDistributerPurchaseGoodsMapper;
import com.nongxinle.service.*;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class GbDistributerPurchaseGoodsController {

    @Autowired
    private GbDistributerPurchaseGoodsService gbDpgService;
    @Autowired
    private GbDistributerPurchaseGoodsMapper gbDpgMapper;
    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    @Autowired
    private GbDistributerFatherGoodsService gbDistributerFatherGoodsService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDPBService;
    @Autowired
    private GbJjOrderPurchaseLinkService gbJjOrderPurchaseLinkService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;





    @RequestMapping(value = "/getGbPurGoodsDetailList", method = RequestMethod.POST)
    @ResponseBody
    public R getGbPurGoodsDetailList(

            @RequestParam Integer disGoodsId,
            @RequestParam String startDate,
            @RequestParam String stopDate) {

        Integer howManyDaysInPeriod = 0;
        if (!startDate.equals(stopDate)) {
            howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);
        }
        Map<String, Object> mapResult = new HashMap<>();

        try {
            // 构建查询参数
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("disGoodsId", disGoodsId);
            queryMap.put("startDate", startDate);
            queryMap.put("stopDate", stopDate);
            queryMap.put("dayuStatus", 2);
            queryMap.put("typeNotEqual", 9);
            GbDistributerGoodsEntity disGoodsForQuery = gbDistributerGoodsService.queryObject(disGoodsId);
            if (disGoodsForQuery != null && disGoodsForQuery.getGbDgDistributerId() != null) {
                queryMap.put("disId", disGoodsForQuery.getGbDgDistributerId());
            }

            // 获取商品列表
            System.out.println("查询商品map" + queryMap);
            System.out.println("开始查询商品列表..."); // 新增日志
            List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = gbDpgService.queryPurchaseGoodsWithDetailByParams(queryMap);

            List<Map<String, Object>> purchaseDayValue = new ArrayList<>();

            if (howManyDaysInPeriod > 0) {
                // top
                for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                    Map<String, Object> mapEvery = new HashMap<>();

                    // dateList
                    String whichDay = "";
                    if (i == 0) {
                        whichDay = startDate;
                    } else {
                        whichDay = afterWhatDay(startDate, i);
                    }
                    Map<String, Object> mapDay = new HashMap<>();
                    mapDay.put("date", whichDay);
                    mapDay.put("disGoodsId", disGoodsId);
                    mapDay.put("typeNotEqual", 9);
                    Integer integer1 = gbDpgService.queryGbPurchaseGoodsCount(mapDay);
                    mapEvery.put("date", whichDay);
                    if (integer1 > 0) {
                        Double subTotal = gbDpgService.queryPurchaseGoodsSubTotal(mapDay);
                        mapEvery.put("purSubtotal", String.format("%.1f", subTotal));
                    } else {
                        mapEvery.put("purSubtotal", 0);
                    }
                    purchaseDayValue.add(mapEvery);
                }
            }

            mapResult.put("arr", purchaseGoodsEntityList);
            mapResult.put("itemList", purchaseDayValue);
            return R.ok().put("data", mapResult);

        } catch (Exception e) {
            System.out.println("查询商品列表异常: " + e.getMessage());
            e.printStackTrace();
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
        System.out.println("map4444444whyyyy111" + map4);
        List<GbDistributerPurchaseGoodsEntity> purchaseToday = gbDpgService.querySimplePurGoods(map4);
        if (purchaseToday != null && !purchaseToday.isEmpty()) {
            System.out.println("kanaknGbgoods" + purchaseToday.get(0).getGbDistributerGoodsEntity());
        }
        Map<String, Object> map1 = new HashMap<>();
        map1.put("disId", disId);
        map1.put("status", 3);
        map1.put("equalBuyStatus", 0);
        map1.put("notEqualOrderType", 9);
        System.out.println("mapp111aaaaaaa" + map1);

        int purCount = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        map1.put("equalBuyStatus", null);
        map1.put("dayuBuyStatus", 0);
        map1.put("dayuStatus", -2);
        System.out.println("mapp111oneoeneoene11111" + map1);
        int purCountOne = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("arr", purchaseToday);
        map3.put("orderAmount", purCount);
        map3.put("wxAmount", purCountOne);
        // 查询完整批发商信息（含所有部门列表）
        map3.put("disInfo", gbDistributerService.queryDistributerWithAllDepartments(disId));

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
        List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities = purGoods.getGbDepartmentOrdersEntities();
        List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();
        int finishOrder = 0;
        for (GbDepartmentOrdersEntity orders : nxDepartmentOrdersEntities) {
            Boolean hasChoice = orders.getIsNotice();
            System.out.println("isisiis" + hasChoice);
            if (hasChoice) {
                finishOrder = finishOrder + 1;
                GbDepartmentOrdersEntity gbDepartmentOrdersEntity = gbDepartmentOrdersService.getById(orders.getGbDepartmentOrdersId());
                gbDepartmentOrdersEntity.setGbDoPrice(orders.getGbDoPrice());
                gbDepartmentOrdersEntity.setGbDoWeight(orders.getGbDoWeight());
                gbDepartmentOrdersEntity.setGbDoSubtotal(orders.getGbDoSubtotal());
                gbDepartmentOrdersEntity.setGbDoStatus(getGbOrderStatusReceived());
                gbDepartmentOrdersEntity.setGbDoBuyStatus(GbConstants.OrderBuyStatus.PAID_FINISHED);
                gbDepartmentOrdersEntity.setGbDoPurchaseUserId(purGoods.getGbDpgPurUserId());
                gbDepartmentOrdersEntity.setGbDoArriveDate(formatWhatDay(0));
                gbDepartmentOrdersEntity.setGbDoArriveOnlyDate(formatWhatDate(0));
                gbDepartmentOrdersEntity.setGbDoArriveWhatDay(getWeek(0));
                gbDepartmentOrdersEntity.setGbDoArriveWeeksYear(getWeekOfYear(0));
                gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);

            } else {
                unChoiceOrderList.add(orders);
            }
        }

        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDpgMapper.selectById(purGoods.getGbDistributerPurchaseGoodsId());
        purchaseGoodsEntity.setGbDpgBuyPrice(purGoods.getGbDpgBuyPrice());
        purchaseGoodsEntity.setGbDpgBuyQuantity(purGoods.getGbDpgBuyQuantity());
        purchaseGoodsEntity.setGbDpgBuySubtotal(purGoods.getGbDpgBuySubtotal());
        purchaseGoodsEntity.setGbDpgPurUserId(purGoods.getGbDpgPurUserId());
        purchaseGoodsEntity.setGbDpgPurchaseDepartmentId(purGoods.getGbDpgPurchaseDepartmentId());
        purchaseGoodsEntity.setGbDpgBatchId(-1);
        purchaseGoodsEntity.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.STOCK_FINISHED);
        purchaseGoodsEntity.setGbDpgPayType(GbConstants.PurchaseGoodsStatus.PAY_FINISHED);
        purchaseGoodsEntity.setGbDpgPurchaseType(GbConstants.PurchaseOrderType.SELF_PURCHASE);
        purchaseGoodsEntity.setGbDpgTime(formatWhatTime(0));
        purchaseGoodsEntity.setGbDpgPurchaseDate(formatWhatDay(0));
        purchaseGoodsEntity.setGbDpgStockFinishDate(formatWhatDay(0));
        purchaseGoodsEntity.setGbDpgPurchaseMonth(formatWhatMonth(0));
        purchaseGoodsEntity.setGbDpgPurchaseYear(formatWhatYear(0));
        purchaseGoodsEntity.setGbDpgPurchaseWeek(getWeek(0));
        purchaseGoodsEntity.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
        purchaseGoodsEntity.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
        Integer gbDpgDisGoodsId = purGoods.getGbDpgDisGoodsId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.getById(gbDpgDisGoodsId);

        System.out.println("apbccccc" + gbDistributerGoodsEntity.getGbDgControlPrice());
        if (gbDistributerGoodsEntity.getGbDgControlPrice() != null && gbDistributerGoodsEntity.getGbDgControlPrice() == 1) {
            gbDpgService.annotatePurchaseGoodsPriceReason(purchaseGoodsEntity);
        }

        // 判断是否有保鲜时间参数
        GbDistributerGoodsEntity gbDisGoodsEntity = gbDistributerGoodsService.getById(gbDpgDisGoodsId);
        if (gbDisGoodsEntity.getGbDgControlFresh() != null && gbDisGoodsEntity.getGbDgControlFresh() == 1) {
            System.out.println("wasteeieiee" + gbDisGoodsEntity.getGbDgControlFresh());
            int wasteHour = Integer.parseInt(gbDisGoodsEntity.getGbDgFreshWasteHour());
            purchaseGoodsEntity.setGbDpgWasteFullTime(formatWhatFullTime(wasteHour));
        }
        // 使用 LambdaUpdateWrapper 更新非空字段
        LambdaUpdateWrapper<GbDistributerPurchaseGoodsEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(GbDistributerPurchaseGoodsEntity::getGbDistributerPurchaseGoodsId, purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
        
        if (purchaseGoodsEntity.getGbDpgBuyPrice() != null) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPrice, purchaseGoodsEntity.getGbDpgBuyPrice());
        }
        if (purchaseGoodsEntity.getGbDpgBuyQuantity() != null) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyQuantity, purchaseGoodsEntity.getGbDpgBuyQuantity());
        }
        if (purchaseGoodsEntity.getGbDpgBuySubtotal() != null) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgBuySubtotal, purchaseGoodsEntity.getGbDpgBuySubtotal());
        }
        if (purchaseGoodsEntity.getGbDpgPurUserId() != null) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurUserId, purchaseGoodsEntity.getGbDpgPurUserId());
        }
        if (purchaseGoodsEntity.getGbDpgPurchaseDepartmentId() != null) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDepartmentId, purchaseGoodsEntity.getGbDpgPurchaseDepartmentId());
        }
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgBatchId, purchaseGoodsEntity.getGbDpgBatchId());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, purchaseGoodsEntity.getGbDpgStatus());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPayType, purchaseGoodsEntity.getGbDpgPayType());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, purchaseGoodsEntity.getGbDpgPurchaseType());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgTime, purchaseGoodsEntity.getGbDpgTime());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDate, purchaseGoodsEntity.getGbDpgPurchaseDate());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, purchaseGoodsEntity.getGbDpgStockFinishDate());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseMonth, purchaseGoodsEntity.getGbDpgPurchaseMonth());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseYear, purchaseGoodsEntity.getGbDpgPurchaseYear());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseWeek, purchaseGoodsEntity.getGbDpgPurchaseWeek());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseFullTime, purchaseGoodsEntity.getGbDpgPurchaseFullTime());
        updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseWeekYear, purchaseGoodsEntity.getGbDpgPurchaseWeekYear());
        if (purchaseGoodsEntity.getGbDpgWasteFullTime() != null) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgWasteFullTime, purchaseGoodsEntity.getGbDpgWasteFullTime());
        }
        if (purchaseGoodsEntity.getGbDpgBuyPriceReason() != null && !purchaseGoodsEntity.getGbDpgBuyPriceReason().trim().isEmpty()) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPriceReason, purchaseGoodsEntity.getGbDpgBuyPriceReason());
        }
        gbDpgMapper.update(null, updateWrapper);

        System.out.println("unspsosos" + unChoiceOrderList);
        if (!unChoiceOrderList.isEmpty()) {
            gbJjOrderPurchaseLinkService.moveUnconfirmedOrdersToNewPurchaseGoods(
                    purchaseGoodsEntity, unChoiceOrderList, gbDistributerGoodsEntity);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
        List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
        gbDpgService.saveDepartmentStockEntriesByPurchase(
                gbDepartmentOrdersEntities, purGoods.getGbDistributerPurchaseGoodsId());

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
        System.out.println("======== GB采购日期统计开始 ========");
        System.out.println("批发商ID: " + disId + ", 日期: " + startDate + " ~ " + stopDate);

        // 数据验证
        Map<String, Object> mapCheck = new HashMap<>();
        mapCheck.put("disId", disId);
        mapCheck.put("startDate", startDate);
        mapCheck.put("stopDate", stopDate);
        mapCheck.put("dayuStatus", 1); // 状态>1，已完成的采购

        Integer purchaseCount = gbDpgService.queryPurchaseGoodsCount(mapCheck);

        Map<String, Object> mapReduceCheck = new HashMap<>();
        mapReduceCheck.put("disId", disId);
        mapReduceCheck.put("startDate", startDate);
        mapReduceCheck.put("stopDate", stopDate);
        Integer reduceCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapReduceCheck);

        System.out.println("采购商品数量: " + purchaseCount + ", 出货记录数量: " + reduceCount);

        if (purchaseCount == 0 && reduceCount == 0) {
            System.out.println("没有数据，返回错误");
            return R.error(-1, "没有数据");
        }

        // 计算日期跨度
        List<Map<String, Object>> dayList = new ArrayList<>();
        Integer howManyDaysInPeriod = 0;

        if (!startDate.equals(stopDate)) {
            howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);
        }
        System.out.println("日期跨度: " + (howManyDaysInPeriod + 1) + " 天");

        // 按天统计
        for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
            String whichDay = "";
            if (i == 0) {
                whichDay = startDate;
            } else {
                whichDay = afterWhatDay(startDate, i);
            }

            System.out.println("--- 统计日期: " + whichDay + " ---");

            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("day", whichDay);

            Map<String, Object> map = new HashMap<>();
            map.put("date", whichDay);
            map.put("disId", disId);
            map.put("dayuStatus", 1); // 已完成的采购

            // 1. 自采数据
            map.put("purchaseType", GbConstants.PurchaseOrderType.SELF_PURCHASE);
            Integer zicaiCount = gbDpgService.queryPurchaseGoodsCount(map);
            BigDecimal zicaiTotal = new BigDecimal(0);
            if (zicaiCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);
                zicaiTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("zicai", zicaiTotal);
            // 移除 purchaseType，以便后续查询使用
            map.remove("purchaseType");

            // 2. 订货数据
            map.put("purchaseType", GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
            Integer dinghuoCount = gbDpgService.queryPurchaseGoodsCount(map);
            BigDecimal dinghuoTotal = new BigDecimal(0);
            if (dinghuoCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);
                dinghuoTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("dinghuo", dinghuoTotal);
            System.out.println("订货金额: " + dinghuoTotal);
            // 3. 所有入库的总和（包括自采、订货）
            Map<String, Object> mapAllStock = new HashMap<>();
            mapAllStock.put("date", whichDay);
            mapAllStock.put("disId", disId);
            mapAllStock.put("dayuStatus", 1);
            Integer allStockCount = gbDpgService.queryPurchaseGoodsCount(mapAllStock);
            BigDecimal allStockTotal = new BigDecimal(0);
            if (allStockCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapAllStock);
                allStockTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("allStock", allStockTotal);
            // 4. 当天采购（采购日=whichDay，与上面对齐）对应部门库存行上「剩余成本」合计 ∑gb_dgs_rest_subtotal（非采购金额）
            Map<String, Object> restMap = new HashMap<>();
            restMap.put("disId", disId);
            restMap.put("purchaseLinkDate", whichDay);
            restMap.put("purDayuStatus", 1);
            Double restDouble = gbDepartmentGoodsStockService.queryDepGoodsRestTotal(restMap);
            BigDecimal restStockTotal = new BigDecimal(restDouble != null ? restDouble : 0)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);
            dayMap.put("restStock", restStockTotal);
            System.out.println("所有入库采购金额: " + allStockTotal + ", 当日采购关联库存剩余金额: " + restStockTotal);

            // 5. 当日支出统计（3种类型）
            // 5.1 销售支出 (type = 0, 1)
            Map<String, Object> mapSale = new HashMap<>();
            mapSale.put("date", whichDay);
            mapSale.put("disId", disId);
            List<Integer> saleTypes = new ArrayList<>();
            saleTypes.add(0);
            saleTypes.add(1);
            mapSale.put("types", saleTypes);
            BigDecimal saleCostTotal = new BigDecimal(0);
            Integer saleCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapSale);
            if (saleCount > 0) {
                Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapSale);
                saleCostTotal = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("saleCostTotal", saleCostTotal);

            // 5.2 损耗支出 (type = 2, 3)
            Map<String, Object> mapLoss = new HashMap<>();
            mapLoss.put("date", whichDay);
            mapLoss.put("disId", disId);
            List<Integer> lossTypes = new ArrayList<>();
            lossTypes.add(2);
            lossTypes.add(3);
            mapLoss.put("types", lossTypes);
            BigDecimal lossCostTotal = new BigDecimal(0);
            Integer lossCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapLoss);
            if (lossCount > 0) {
                Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapLoss);
                lossCostTotal = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("lossCostTotal", lossCostTotal);

            // 5.3 退货支出 (type = 4)
            Map<String, Object> mapReturn = new HashMap<>();
            mapReturn.put("date", whichDay);
            mapReturn.put("disId", disId);
            mapReturn.put("type", 4);
            BigDecimal returnCostTotal = new BigDecimal(0);
            Integer returnCount = gbDepartmentStockReduceService.queryReduceTypeCount(mapReturn);
            if (returnCount > 0) {
                Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapReturn);
                returnCostTotal = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("returnCostTotal", returnCostTotal);

            // 5.4 总支出
            BigDecimal costTotal = saleCostTotal.add(lossCostTotal).add(returnCostTotal);
            dayMap.put("costTotal", costTotal);

            dayList.add(dayMap);
        }

        // 汇总统计
        System.out.println("--- 开始汇总统计 ---");
        Map<String, Object> mapTotal = new HashMap<>();
        mapTotal.put("disId", disId);
        mapTotal.put("startDate", startDate);
        mapTotal.put("stopDate", stopDate);
        mapTotal.put("dayuStatus", 1);

        // 总采购金额
        BigDecimal purchaseTotal = new BigDecimal(0);
        Integer totalCount = gbDpgService.queryPurchaseGoodsCount(mapTotal);
        if (totalCount > 0) {
            Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapTotal);
            purchaseTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        System.out.println("总采购金额: " + purchaseTotal);

        // 自采总额
        mapTotal.put("purchaseType", GbConstants.PurchaseOrderType.SELF_PURCHASE);
        BigDecimal zicaiTotalSum = new BigDecimal(0);
        Integer zicaiCountSum = gbDpgService.queryPurchaseGoodsCount(mapTotal);
        if (zicaiCountSum > 0) {
            Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapTotal);
            zicaiTotalSum = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        mapTotal.remove("purchaseType");

        // 订货总额
        mapTotal.put("purchaseType", GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
        System.out.println("dingdhuomapappapap" + mapTotal);
        BigDecimal dinghuoTotalSum = new BigDecimal(0);
        Integer dinghuoCountSum = gbDpgService.queryPurchaseGoodsCount(mapTotal);
        if (dinghuoCountSum > 0) {
            System.out.println("mappaaptttt" + mapTotal);
            Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapTotal);
            dinghuoTotalSum = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        System.out.println("订货总额1: " + dinghuoTotalSum);
        mapTotal.remove("purchaseType");

        // 支出总额统计（3种类型）
        // 销售支出总额 (type = 0, 1)
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
        if (saleCountSum > 0) {
            Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapSaleTotal);
            saleCostTotalSum = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        System.out.println("销售支出总额: " + saleCostTotalSum);

        // 损耗支出总额 (type = 2, 3)
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
        if (lossCountSum > 0) {
            Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapLossTotal);
            lossCostTotalSum = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        System.out.println("损耗支出总额: " + lossCostTotalSum);

        // 退货支出总额 (type = 4)
        Map<String, Object> mapReturnTotal = new HashMap<>();
        mapReturnTotal.put("disId", disId);
        mapReturnTotal.put("startDate", startDate);
        mapReturnTotal.put("stopDate", stopDate);
        mapReturnTotal.put("type", 4);
        BigDecimal returnCostTotalSum = new BigDecimal(0);
        Integer returnCountSum = gbDepartmentStockReduceService.queryReduceTypeCount(mapReturnTotal);
        if (returnCountSum > 0) {
            Double aDouble = gbDepartmentStockReduceService.queryReduceCostSubtotal(mapReturnTotal);
            returnCostTotalSum = new BigDecimal(aDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
        }
        System.out.println("退货支出总额: " + returnCostTotalSum);

        // 总支出
        BigDecimal costTotalSum = saleCostTotalSum.add(lossCostTotalSum).add(returnCostTotalSum);
        System.out.println("总支出: " + costTotalSum);

        // 计算日均值
        BigDecimal purchasePerDay = purchaseTotal;
        BigDecimal costPerDay = costTotalSum;
        if (howManyDaysInPeriod > 0) {
            purchasePerDay = purchaseTotal.divide(new BigDecimal(howManyDaysInPeriod + 1), 1, BigDecimal.ROUND_HALF_UP);
            costPerDay = costTotalSum.divide(new BigDecimal(howManyDaysInPeriod + 1), 1, BigDecimal.ROUND_HALF_UP);
        }
        System.out.println("日均采购: " + purchasePerDay + ", 日均支出: " + costPerDay);

        // 组装返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("allTotal", purchaseTotal);
        result.put("purchasePerDay", purchasePerDay);
        result.put("zicaiTotal", zicaiTotalSum);
        result.put("dinghuoTotal", dinghuoTotalSum);
        result.put("costTotal", costTotalSum);
        result.put("costPerDay", costPerDay);
        // 3种支出总额
        result.put("saleCostTotal", saleCostTotalSum);
        result.put("lossCostTotal", lossCostTotalSum);
        result.put("returnCostTotal", returnCostTotalSum);
        result.put("arr", dayList);

        System.out.println("======== GB采购日期统计完成 ========");
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
        System.out.println("======== GB采购分类统计开始 ========");
        System.out.println("批发商ID: " + disId + ", 日期: " + startDate + " ~ " + stopDate);

        Map<String, Object> map123 = new HashMap<>();

        Map<String, Object> mapDep = new HashMap<>();
        mapDep.put("disId", disId);
        mapDep.put("dayuStatus", 1);
        mapDep.put("typeNotEqual", 9);
        mapDep.put("startDate", startDate);
        mapDep.put("stopDate", stopDate);
        // 只查询门店类型的大类
        mapDep.put("depType", getGbDepartmentTypeMendian());

        //采购总额
        System.out.println("purmapapapapa" + mapDep);
        Integer integerT = gbDpgService.queryGbPurchaseGoodsCount(mapDep);

        Integer integer3 = gbDepartmentStockReduceService.queryReduceTypeCount(mapDep);
        BigDecimal purchaseTotal = new BigDecimal(0);
        if (integerT == 0 && integer3 == 0) {
            return R.error(-1, "没有数据");
        } else {
            Integer integer = gbDpgService.queryGbPurchaseGoodsCount(mapDep);

            if (integer > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                purchaseTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
        }

        // 支出总额（gb_department_goods_stock_reduce：生产/废弃/损耗）
        Map<String, Object> reduceTotalsAll = gbDepartmentStockReduceService.queryReduceAllTypesTotal(mapDep);
        double totalCost = 0.0;
        if (reduceTotalsAll != null) {
            double produceAll = reduceTotalsAll.get("produceTotal") != null
                    ? Double.parseDouble(reduceTotalsAll.get("produceTotal").toString()) : 0;
            double wasteAll = reduceTotalsAll.get("wasteTotal") != null
                    ? Double.parseDouble(reduceTotalsAll.get("wasteTotal").toString()) : 0;
            double lossAll = reduceTotalsAll.get("lossTotal") != null
                    ? Double.parseDouble(reduceTotalsAll.get("lossTotal").toString()) : 0;
            totalCost = produceAll + wasteAll + lossAll;
        }

        mapDep.put("startDate", startDate);
        mapDep.put("stopDate", stopDate);

        // 改为检查采购商品数量，只要采购商品有数据就返回
        System.out.println("查询参数 mapDepaaa: " + mapDep);
        Integer purchaseCount = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
        if (purchaseCount > 0) {
            System.out.println("========== 开始查询商品大类 ==========");

            // 直接查询采购商品涉及的大类，用TreeSet接收
            TreeSet<GbDistributerFatherGoodsEntity> fatherGoodsTreeSet = gbDistributerFatherGoodsService.queryPurchaseGoodsFatherTypes(mapDep);
            System.out.println("查询成功，返回记录数: " + fatherGoodsTreeSet.size());

            for (GbDistributerFatherGoodsEntity greatEntity : fatherGoodsTreeSet) {

                System.out.println("goodsName" + greatEntity.getGbDfgFatherGoodsName());
                System.out.println("goodsNamegetGbDistributerFatherGoodsId" + greatEntity.getGbDistributerFatherGoodsId());
                System.out.println("goodsNamegetGbDfgFathersFatherId" + greatEntity.getGbDfgFathersFatherId());
                // dayMap
                Map<String, Object> cataMap = new HashMap<>();

                mapDep.put("dayuStatus", -1);
                mapDep.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                System.out.println("mazzzuissisis" + mapDep);
                Integer integer = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
                BigDecimal batchBillTotal = new BigDecimal(0);
                if (integer > 0) {
                    Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                    batchBillTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                BigDecimal divide = new BigDecimal(0);
                if ((purchaseTotal.compareTo(new BigDecimal(0)) == 1)) {
                    divide = batchBillTotal.divide(purchaseTotal, 4, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100));
                }

                cataMap.put("greatPurTotal", batchBillTotal);
                cataMap.put("greatPercent", divide);
                cataMap.put("purTotal", purchaseTotal);
                mapDep.put("dayuStatus", 2);
                mapDep.put("purchaseType", GbConstants.PurchaseOrderType.SELF_PURCHASE);
                // 自采数据
                Integer integerZicai = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
                BigDecimal batchBillTotalZicai = new BigDecimal(0);
                if (integerZicai > 0) {
                    Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                    batchBillTotalZicai = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                cataMap.put("zicai", batchBillTotalZicai);

                // 订货数据
                mapDep.put("purchaseType", GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
                mapDep.put("dayuStatus", 2);
                Integer integerDh = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
                BigDecimal batchBillTotalDh = new BigDecimal(0);
                if (integerDh > 0) {
                    Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                    batchBillTotalDh = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                cataMap.put("dinghuo", batchBillTotalDh);

                // 支出数据
                double add = 0.0;
                Map<String, Object> mapDepCost = new HashMap<>();
                mapDepCost.put("startDate", startDate);
                mapDepCost.put("stopDate", stopDate);
                mapDepCost.put("disId", disId);
                mapDepCost.put("depType", getGbDepartmentTypeMendian());
                mapDepCost.put("dayuStatus", -1);
                mapDepCost.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                Double produceSubtotal = 0.0;
                Double wasteTotal = 0.0;
                Double lossTotal = 0.0;
                System.out.println("cosssoososososointeger4integer4" + mapDepCost);
                Map<String, Object> reduceTotalsGreat = gbDepartmentStockReduceService.queryReduceAllTypesTotal(mapDepCost);
                if (reduceTotalsGreat != null) {
                    produceSubtotal = reduceTotalsGreat.get("produceTotal") != null
                            ? Double.parseDouble(reduceTotalsGreat.get("produceTotal").toString()) : 0.0;
                    System.out.println("cossisisis" + produceSubtotal);
                    wasteTotal = reduceTotalsGreat.get("wasteTotal") != null
                            ? Double.parseDouble(reduceTotalsGreat.get("wasteTotal").toString()) : 0.0;
                    lossTotal = reduceTotalsGreat.get("lossTotal") != null
                            ? Double.parseDouble(reduceTotalsGreat.get("lossTotal").toString()) : 0.0;
                    add = produceSubtotal + wasteTotal + lossTotal;
                }

                // 本期库存
                Map<String, Object> mapDepCostThis = new HashMap<>();
                mapDepCostThis.put("disId", disId);
                mapDepCostThis.put("depType", getGbDepartmentTypeMendian());
                mapDepCostThis.put("dayuStatus", -1);
                mapDepCostThis.put("startDate", startDate);
                mapDepCostThis.put("stopDate", stopDate);
                mapDepCostThis.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                Double perStockDouble = 0.0;

                System.out.println("thisSttockckckckckkc" + mapDepCostThis);
                Integer integerPer = gbDepartmentGoodsStockService.queryGoodsStockCount(mapDepCostThis);
                if (integerPer > 0) {
                    perStockDouble = gbDepartmentGoodsStockService.queryDepGoodsRestTotal(mapDepCostThis);
                }

                // 上期库存
                Double lastStockDouble = 0.0;
                Map<String, Object> mapDepCostLast = new HashMap<>();
                mapDepCostLast.put("disId", disId);
                mapDepCostLast.put("depType", getGbDepartmentTypeMendian());
                mapDepCostLast.put("dayuStatus", -1);
                mapDepCostLast.put("stopDate", startDate);
                mapDepCostLast.put("disGoodsGreatId", greatEntity.getGbDistributerFatherGoodsId());
                System.out.println("lastStockckckk" + mapDepCostLast);
                Integer integerPerLast = gbDepartmentGoodsStockService.queryGoodsStockCount(mapDepCostLast);
                if (integerPerLast > 0) {
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

                greatEntity.setDailyData(cataMap);
            }
            map123.put("arr", fatherGoodsTreeSet);
        }

        System.out.println("======== GB采购分类统计完成 ========");
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
        System.out.println("pururrus" + queryMap);
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
            System.out.println("puruuruuruuruuruuuruur" + map);
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

                    System.out.println("suppsmap" + map);
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
            System.out.println("tootititittiitqueryGbPurchaseGoodsTopTimes" + map);
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
                System.out.println("tootititittiit" + map);
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
            e.printStackTrace();
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
            System.out.println("tootititittiitProduce" + map);
            map.put("type", 1);
            List<GbDistributerGoodsEntity> topGoodsProduce = gbDepartmentStockReduceService.queryStockSubtotalTopTimes(map);

            //按损耗成本 (type=3)
            map.put("type", 3);
            List<GbDistributerGoodsEntity> topGoodsLoss = gbDepartmentStockReduceService.queryStockSubtotalTopTimes(map);

            //按废弃成本 (type=2)
            map.put("type", 2);
            List<GbDistributerGoodsEntity> topGoodsWaste = gbDepartmentStockReduceService.queryStockSubtotalTopTimes(map);

            //按日支出
            System.out.println("tootititittiitDDDD" + map);
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
            e.printStackTrace();
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
        System.out.println("supplierGetUnWeightOutPurGoods: " + map);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = gbDpgService.queryPurchaseGoodsWithOrdersByBatch(map);
        return R.ok().put("data", purchaseGoodsEntityList);
    }

    /**
     * 供货商修改采购商品
     */
    @ResponseBody
    @RequestMapping("/sellerUpdatePurGoods")
    public R sellerUpdatePurGoods(@RequestBody GbDistributerPurchaseGoodsEntity purchaseGoodsEntity) {
        //如果供货商填写了重量，则判断商品是否有保鲜时间参数
        if (purchaseGoodsEntity.getGbDpgBuyQuantity() != null && !purchaseGoodsEntity.getGbDpgBuyQuantity().trim().isEmpty()) {
            Integer gbDoDisGoodsId = purchaseGoodsEntity.getGbDpgDisGoodsId();
            GbDistributerGoodsEntity gbDisGoodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
            if (gbDisGoodsEntity.getGbDgControlFresh() != null && gbDisGoodsEntity.getGbDgControlFresh() == 1) {
                int wasteHour = Integer.parseInt(gbDisGoodsEntity.getGbDgFreshWasteHour());
                purchaseGoodsEntity.setGbDpgWasteFullTime(formatWhatFullTime(wasteHour));
            }
        }

        gbDpgService.updateById(purchaseGoodsEntity);
        List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = purchaseGoodsEntity.getGbDepartmentOrdersEntities();
        for (GbDepartmentOrdersEntity orders : gbDepartmentOrdersEntities) {
            System.out.println("wieieieieiieieiieie" + orders.getGbDoWeight());
            if(orders.getGbDoWeight() != null && !orders.getGbDoWeight().trim().isEmpty() && !orders.getGbDoWeight().equals("0.0")){
                BigDecimal decimal1 = new BigDecimal(orders.getGbDoPrice());
                BigDecimal decimal2 = new BigDecimal(orders.getGbDoWeight());
                BigDecimal decimal3 = decimal1.multiply(decimal2).setScale(1, BigDecimal.ROUND_HALF_UP);
                orders.setGbDoSubtotal(decimal3.toString());
                orders.setGbDoBuyStatus(getGbOrderBuyStatusHasWeightAndPrice());
            }else{
                orders.setGbDoBuyStatus(getGbOrderBuyStatusPrepareing());
            }
            gbDepartmentOrdersService.update(orders);
        }

        Map<String, Object> map = new HashMap<>();
        Integer gbDpgBatchId = purchaseGoodsEntity.getGbDpgBatchId();
        map.put("batchId", gbDpgBatchId);
        Double subTotal = gbDpgService.queryPurchaseGoodsSubTotal(map);
        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.getById(gbDpgBatchId);
        batchEntity.setGbDpbSubtotal(String.format("%.1f", subTotal));
        gbDPBService.updateById(batchEntity);

        return R.ok().put("data", purchaseGoodsEntity);
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
                System.out.println("rriirr0000000");
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
                System.out.println("ziciagouttt" + map);
                zicaiGoodsCount = gbDpgService.queryGbGoodsCount(map);
                System.out.println("自采商品数量查询结果: " + zicaiGoodsCount + " 个不同商品");
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
            e.printStackTrace();
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

            System.out.println("lisisisiisisisnnnn00000000aaa" + queryMap);
            // 获取商品总数
            Integer totalCount = gbDpgService.queryGbDisGoodsTreeCount(queryMap);
            System.out.println("totalCount: " + totalCount); // 新增日志
            Integer totalPages = (int) Math.ceil((double) totalCount / limit);

            // 获取商品列表
            queryMap.put("dateOrder", 1);
            System.out.println("查询商品map" + queryMap);
            System.out.println("开始查询商品列表..."); // 新增日志
            List<GbDistributerGoodsEntity> goodsList = gbDpgService.queryDisTreeGoodsWithPurList(queryMap);
            gbDpgService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
            System.out.println("商品列表查询完成，数量: " + (goodsList != null ? goodsList.size() : "null")); // 新增日志


            // 构建返回数据
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", totalCount);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("goodsList", goodsList);
            System.out.println("reuslt" );
            System.out.println("reuslt" + result);

            return R.ok().put("data", result);

        } catch (Exception e) {
            System.out.println("查询商品列表异常: " + e.getMessage());
            e.printStackTrace();
            return R.error("获取商品列表失败：" + e.getMessage());
        }
    }






}
