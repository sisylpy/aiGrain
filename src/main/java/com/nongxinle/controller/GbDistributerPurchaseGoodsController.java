package com.nongxinle.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nongxinle.entity.*;
import com.nongxinle.mapper.GbDistributerPurchaseGoodsMapper;
import com.nongxinle.service.*;
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
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    @Autowired
    private GbDepartmentGoodsDailyService gbDepGoodsDailyService;
    @Autowired
    private GbDistributerFatherGoodsService gbDistributerFatherGoodsService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDPBService;

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
                gbDepartmentOrdersEntity.setGbDoBuyStatus(getGbOrderBuyStatusHavePayFinish());
                gbDepartmentOrdersEntity.setGbDoPurchaseUserId(purGoods.getGbDpgPurUserId());
                gbDepartmentOrdersEntity.setGbDoArriveDate(formatWhatDay(0));
                gbDepartmentOrdersEntity.setGbDoArriveOnlyDate(formatWhatDate(0));
                gbDepartmentOrdersEntity.setGbDoApplyFullTime(formatFullTime());
                gbDepartmentOrdersEntity.setGbDoArriveWhatDay(getWeek(0));
                gbDepartmentOrdersEntity.setGbDoApplyOnlyTime(formatWhatTime(0));
                gbDepartmentOrdersEntity.setGbDoArriveWeeksYear(getWeekOfYear(0));

                gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);

            } else {
                System.out.println("unchoiciciic" + orders.getGbDepartmentOrdersId());
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
        purchaseGoodsEntity.setGbDpgStatus(4);
        purchaseGoodsEntity.setGbDpgPayType(0);
        purchaseGoodsEntity.setGbDpgPurchaseType(1);
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
            checkPurGoodsPrice(purGoods);
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
        gbDpgMapper.update(null, updateWrapper);

        System.out.println("unspsosos" + unChoiceOrderList);
        if (unChoiceOrderList.size() > 0) {
            Integer gbDepartmentOrdersId = unChoiceOrderList.get(0).getGbDepartmentOrdersId();
            GbDepartmentOrdersEntity ordersEntity = gbDepartmentOrdersService.getById(gbDepartmentOrdersId);
            GbDistributerPurchaseGoodsEntity disGoods = new GbDistributerPurchaseGoodsEntity();
            disGoods.setGbDpgDistributerId(purchaseGoodsEntity.getGbDpgDistributerId());
            disGoods.setGbDpgPayType(0);
            disGoods.setGbDpgDisGoodsGrandId(purchaseGoodsEntity.getGbDpgDisGoodsGrandId());
            disGoods.setGbDpgDisGoodsGreatId(purchaseGoodsEntity.getGbDpgDisGoodsGreatId());
            disGoods.setGbDpgDisGoodsFatherId(ordersEntity.getGbDoDisGoodsFatherId());
            disGoods.setGbDpgDisGoodsId(ordersEntity.getGbDoDisGoodsId());
            disGoods.setGbDpgApplyDate(formatWhatDay(0));
            disGoods.setGbDpgStatus(0);
            disGoods.setGbDpgBuyScale("-1");
            disGoods.setGbDpgStandard(ordersEntity.getGbDoStandard());
            disGoods.setGbDpgOrdersAmount(unChoiceOrderList.size());
            disGoods.setGbDpgOrdersFinishAmount(0);
            disGoods.setGbDpgOrdersWeightAmount(0);
            disGoods.setGbDpgOrdersBillAmount(0);
            disGoods.setGbDpgPurchaseWeek(getWeek(0));
            disGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
            disGoods.setGbDpgIsCheck(0);
            disGoods.setGbDpgPurchaseType(1);
            disGoods.setGbDpgPurchaseNxSupplierId(-1);
            gbDpgService.save(disGoods);
            for (GbDepartmentOrdersEntity unChoiceOrder : unChoiceOrderList) {
                Integer gbDistributerPurchaseGoodsId = disGoods.getGbDistributerPurchaseGoodsId();
                Integer gbDepartmentOrdersId1 = unChoiceOrder.getGbDepartmentOrdersId();
                GbDepartmentOrdersEntity ordersEntity1 = gbDepartmentOrdersService.getById(gbDepartmentOrdersId1);
                ordersEntity1.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);
                gbDepartmentOrdersService.update(ordersEntity1);

                BigDecimal purQuantity = new BigDecimal(disGoods.getGbDpgQuantity());
                BigDecimal orderQuantity = new BigDecimal(ordersEntity1.getGbDoQuantity());
                BigDecimal add = purQuantity.add(orderQuantity).setScale(2, BigDecimal.ROUND_HALF_UP);
                disGoods.setGbDpgQuantity(add.toString());
                if (gbDistributerGoodsEntity.getGbDgGoodsStandardname().equals(ordersEntity.getGbDoStandard())) {
                    disGoods.setGbDpgBuyQuantity(add.toString());
                }
                gbDpgService.updateById(disGoods);
            }
        }

        Map<String, Object> map = new HashMap<>();
        map.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
        List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
        saveDepStockDataByPurchase(gbDepartmentOrdersEntities, purGoods.getGbDistributerPurchaseGoodsId());

        return R.ok();
    }


    private void saveDepStockDataByPurchase(List<GbDepartmentOrdersEntity> ordersEntityList, Integer purGoodsId) {
        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDpgService.getById(purGoodsId);

        for (GbDepartmentOrdersEntity order : ordersEntityList) {
            System.out.println("upddodididufidfuaisf");
            Integer gbDepartmentOrdersId = order.getGbDepartmentOrdersId();
            Integer gbDoDepDisGoodsId = order.getGbDoDepDisGoodsId();
            GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.getById(gbDoDepDisGoodsId);
            GbDepartmentOrdersEntity ordersEntity = gbDepartmentOrdersService.getById(gbDepartmentOrdersId);
            Integer gbDoStatus = ordersEntity.getGbDoStatus();
            // 判断没有被别人收货
            // 0,修改订单上次价格涨幅
            if (departmentDisGoodsEntity.getGbDdgOrderDate() != null && !departmentDisGoodsEntity.getGbDdgOrderDate().trim().isEmpty()) {
                if (order.getGbDoPrice() != null && !order.getGbDoPrice().trim().isEmpty()) {
                    BigDecimal decimal = new BigDecimal(departmentDisGoodsEntity.getGbDdgOrderPrice());
                    BigDecimal decimal1 = new BigDecimal(order.getGbDoPrice());
                    BigDecimal subtract1 = decimal1.subtract(decimal);
                    order.setGbDoPriceDifferent(subtract1.toString());
                } else {
                    order.setGbDoPriceDifferent("0");
                }
            }
            GbDepartmentGoodsStockEntity stockEntity = new GbDepartmentGoodsStockEntity();
            stockEntity.setGbDgsGbDepartmentId(order.getGbDoDepartmentId());
            stockEntity.setGbDgsGbDepartmentFatherId(order.getGbDoDepartmentFatherId());
            stockEntity.setGbDgsGbPurGoodsId(order.getGbDoPurchaseGoodsId());
            stockEntity.setGbDgsGbDistributerId(order.getGbDoDistributerId());
            stockEntity.setGbDgsWeight(order.getGbDoWeight());
            stockEntity.setGbDgsPrice(order.getGbDoPrice());
            stockEntity.setGbDgsSubtotal(order.getGbDoSubtotal());
            stockEntity.setGbDgsRestWeight(order.getGbDoWeight());
            stockEntity.setGbDgsRestSubtotal(order.getGbDoSubtotal());
            stockEntity.setGbDgsGbDisGoodsId(order.getGbDoDisGoodsId());
            stockEntity.setGbDgsNxSupplierId(-1);
            stockEntity.setGbDgsStatus(0);
            stockEntity.setGbDgsPurUserId(purchaseGoodsEntity.getGbDpgPurUserId());
            stockEntity.setGbDgsNxDistributerId(-1);

            Integer gbDoDisGoodsId = order.getGbDoDisGoodsId();
            GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.getById(gbDoDisGoodsId);
            stockEntity.setGbDgsGbDisGoodsFatherId(goodsEntity.getGbDgDfgGoodsFatherId());
            stockEntity.setGbDgsGbDisGoodsGrandId(goodsEntity.getGbDgDfgGoodsGrandId());
            stockEntity.setGbDgsGbDisGoodsGreatId(goodsEntity.getGbDgDfgGoodsGreatId());
            stockEntity.setGbDgsGbDepDisGoodsId(order.getGbDoDepDisGoodsId());
            stockEntity.setGbDgsDate(formatWhatDay(0));
            stockEntity.setGbDgsTimeStamp(getTimeStamp());
            stockEntity.setGbDgsWeek(getWeek(0));
            stockEntity.setGbDgsMonth(formatWhatMonth(0));
            stockEntity.setGbDgsYear(formatWhatYear(0));
            stockEntity.setGbDgsFullTime(formatFullTime());
            gbDepartmentGoodsStockService.save(stockEntity);
        }
    }


    private GbDistributerPurchaseGoodsEntity checkPurGoodsPrice(GbDistributerPurchaseGoodsEntity purchaseGoodsEntity) {
        System.out.println("checkkckGoododopriidd" + purchaseGoodsEntity.getGbDpgDisGoodsId());
        Integer gbDpgDisGoodsId = purchaseGoodsEntity.getGbDpgDisGoodsId();
        BigDecimal buyPrice = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice());
        Integer gbDpgDisGoodsPriceId = purchaseGoodsEntity.getGbDpgDisGoodsPriceId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.getById(gbDpgDisGoodsId);

        BigDecimal weight = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyQuantity());
        BigDecimal goodsHighest = new BigDecimal(gbDistributerGoodsEntity.getGbDgGoodsHighestPrice());
        BigDecimal goodsLowest = new BigDecimal(gbDistributerGoodsEntity.getGbDgGoodsLowestPrice());
        String priceTotal = buyPrice.multiply(weight).setScale(1, BigDecimal.ROUND_HALF_UP).toString();

        if (buyPrice.compareTo(goodsHighest) == 1 && purchaseGoodsEntity.getGbDpgBuyQuantity() != null && !purchaseGoodsEntity.getGbDpgBuyQuantity().trim().isEmpty()) {
            // 高于最高价
            BigDecimal higherWhatPrice = buyPrice.subtract(goodsHighest);
            BigDecimal highertotal = higherWhatPrice.multiply(weight).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal multiply = higherWhatPrice.divide(goodsHighest, 4, BigDecimal.ROUND_HALF_DOWN);
            BigDecimal highestTotal = goodsHighest.multiply(weight).setScale(1, BigDecimal.ROUND_HALF_UP);
            purchaseGoodsEntity.setGbDpgBuyPriceReason("价格偏高");
        } else if (buyPrice.compareTo(goodsLowest) == -1) {
            // 低于最低价
            BigDecimal lowerWhatPrice = goodsLowest.subtract(buyPrice);
            BigDecimal lowerTotal = lowerWhatPrice.multiply(weight).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal multiply = lowerWhatPrice.divide(goodsLowest, 4, BigDecimal.ROUND_HALF_DOWN);
            BigDecimal lowestTotal = goodsLowest.multiply(weight).setScale(1, BigDecimal.ROUND_HALF_UP);
            purchaseGoodsEntity.setGbDpgBuyPriceReason("价格偏低");
        } else {
            purchaseGoodsEntity.setGbDpgBuyPriceReason("价格正常");
        }
return purchaseGoodsEntity;
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

            // 1. 自采数据 (purchaseType = 0, inputType = 2)
            map.put("purchaseType", 0);
            // map.put("equalInputType", 2);
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
            map.put("purchaseType", 1);
            Integer dinghuoCount = gbDpgService.queryPurchaseGoodsCount(map);
            BigDecimal dinghuoTotal = new BigDecimal(0);
            if (dinghuoCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(map);
                dinghuoTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("dinghuo", dinghuoTotal);
            System.out.println("订货金额: " + dinghuoTotal);
            map.remove("purchaseType");

            // 3. 直接入库的采购统计 (purchaseType = 10)
            Map<String, Object> mapDirectStock = new HashMap<>();
            mapDirectStock.put("date", whichDay);
            mapDirectStock.put("disId", disId);
            mapDirectStock.put("dayuStatus", 1);
            mapDirectStock.put("purchaseType", 10);
            Integer directStockCount = gbDpgService.queryPurchaseGoodsCount(mapDirectStock);
            BigDecimal directStockTotal = new BigDecimal(0);
            if (directStockCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDirectStock);
                directStockTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("directStock", directStockTotal);
            System.out.println("直接入库采购金额: " + directStockTotal);

            // 4. 所有入库的总和（包括自采、订货、直接入库）
            Map<String, Object> mapAllStock = new HashMap<>();
            mapAllStock.put("date", whichDay);
            mapAllStock.put("disId", disId);
            // mapAllStock.put("equalInputType", 2);
            mapAllStock.put("dayuStatus", 1);
            Integer allStockCount = gbDpgService.queryPurchaseGoodsCount(mapAllStock);
            BigDecimal allStockTotal = new BigDecimal(0);
            if (allStockCount > 0) {
                Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapAllStock);
                allStockTotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
            }
            dayMap.put("allStock", allStockTotal);
            System.out.println("所有入库采购金额: " + allStockTotal);

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
        mapTotal.put("purchaseType", 0);
        BigDecimal zicaiTotalSum = new BigDecimal(0);
        Integer zicaiCountSum = gbDpgService.queryPurchaseGoodsCount(mapTotal);
        if (zicaiCountSum > 0) {
            Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapTotal);
            zicaiTotalSum = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        mapTotal.remove("purchaseType");

        // 订货总额
        mapTotal.put("purchaseType", 1);
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

        // 支出总额
        Integer integer2 = gbDepGoodsDailyService.queryDepGoodsDailyCount(mapDep);
        double totalCost = 0.0;
        if (integer2 > 0) {
            double produceSubtotal = gbDepGoodsDailyService.queryDepGoodsDailyProduceSubtotal(mapDep);
            double wasteSubtotal = gbDepGoodsDailyService.queryDepGoodsDailyWasteSubtotal(mapDep);
            double lossSubtotal = gbDepGoodsDailyService.queryDepGoodsDailyLossSubtotal(mapDep);
            totalCost = produceSubtotal + wasteSubtotal + lossSubtotal;
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

                mapDep.put("supplierBuy", -1);
                mapDep.put("dayuStatus", 2);
                // 自采数据
                Integer integerZicai = gbDpgService.queryGbPurchaseGoodsCount(mapDep);
                BigDecimal batchBillTotalZicai = new BigDecimal(0);
                if (integerZicai > 0) {
                    Double aDouble = gbDpgService.queryPurchaseGoodsSubTotal(mapDep);
                    batchBillTotalZicai = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                cataMap.put("zicai", batchBillTotalZicai);

                // 订货数据
                mapDep.put("supplierBuy", 1);
                mapDep.put("dayuStatus", 2);
                mapDep.put("batchDayuStatus", 2);

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
                Integer integer4 = gbDepGoodsDailyService.queryDepGoodsDailyCount(mapDepCost);
                if (integer4 > 0) {
                    produceSubtotal = gbDepGoodsDailyService.queryDepGoodsDailyProduceSubtotal(mapDepCost);

                    System.out.println("cossisisis" + produceSubtotal);
                    wasteTotal = gbDepGoodsDailyService.queryDepGoodsDailyWasteSubtotal(mapDepCost);
                    lossTotal = gbDepGoodsDailyService.queryDepGoodsDailyLossSubtotal(mapDepCost);
                    add = produceSubtotal + wasteTotal + lossTotal;
                }

                // 所有库存
                Double allDouble = 0.0;
                Integer integer1 = gbDepartmentGoodsStockService.queryGoodsStockCount(mapDepCost);
                if (integer1 > 0) {
                    allDouble = gbDepartmentGoodsStockService.queryDepGoodsRestTotal(mapDepCost);
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
     * GB系统采购明细分类统计（按采购用户或供应商）
     * @param disId 批发商ID
     * @param purUserIds 采购用户ID列表（逗号分隔）
     * @param type 类型：0-按采购用户统计，1-按供应商统计
     * @param greatId 商品大类ID
     * @param startDate 开始日期
     * @param stopDate 结束日期
     * @param supplierIds 供应商ID列表（逗号分隔）
     * @return 明细统计数据
     */
    @RequestMapping(value = "/disGetPurchaseDetailType", method = RequestMethod.POST)
    @ResponseBody
    public R disGetPurchaseDetailType(Integer disId, String purUserIds, Integer type, Integer greatId,
                                      String startDate, String stopDate, String supplierIds) {

        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        System.out.println("初始map: " + map);
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

        Map<String, Object> mapR = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        if (type == 0) {
            map.put("typeNotEqual", 9);
            map.put("supplierBuy", -1);
            map.put("dayuStatus", 2);
            map.put("startDate", startDate);
            map.put("stopDate", stopDate);
            if (greatId != -1) {
                map.put("disGoodsGreatId", greatId);
            }
            Double subTotal = 0.0;
            Integer integer1 = gbDpgService.queryGbPurchaseGoodsCount(map);
            System.out.println("subslslslsl" + map);
            if (integer1 > 0) {
                subTotal = gbDpgService.queryPurchaseGoodsSubTotal(map);
            }

            List<GbDepartmentUserEntity> purUserList = gbDpgService.queryPurUserList(map);

            if (purUserList.size() > 0) {
                for (GbDepartmentUserEntity userEntity : purUserList) {
                    Map<String, Object> mapUser = new HashMap<>();
                    mapUser.put("user", userEntity);

                    // 创建新的查询参数Map，避免参数污染
                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("typeNotEqual", 9);
                    queryMap.put("supplierBuy", -1);
                    queryMap.put("dayuStatus", 2);
                    queryMap.put("startDate", startDate);
                    queryMap.put("stopDate", stopDate);
                    queryMap.put("disId", map.get("disId"));
                    queryMap.put("purUserId", userEntity.getGbDepartmentUserId());
                    queryMap.put("offset", 0);
                    queryMap.put("limit", 100);
                    if (greatId != -1) {
                        queryMap.put("disGoodsGreatId", greatId);
                    }

                    System.out.println("mapppppppp" + queryMap);
                    Integer integer = gbDpgService.queryGbDisGoodsTreeCount(queryMap);
                    List<GbDistributerGoodsEntity> goodsList = gbDpgService.queryDisTreeGoodsWithPurList(queryMap);
                    Double subTotal1 = gbDpgService.queryPurchaseGoodsSubTotal(queryMap);
                    mapUser.put("arr", goodsList);
                    mapUser.put("count", integer);
                    mapUser.put("purSubtotal", String.format("%.1f", subTotal1));
                    result.add(mapUser);

                }
                System.out.println("subslslsls" + map);
            }

            BigDecimal total = new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP);
            mapR.put("total", total);
            mapR.put("purUserArr", result);

        } else if (type == 1) {
            System.out.println("=== 执行type=1分支 ===");

            map.put("startDate", startDate);
            map.put("stopDate", stopDate);
            map.put("supplierBuy", 1);
            if (greatId != -1) {
                map.put("disGoodsGreatId", greatId);
            }
            Double subTotal = 0.0;
            Integer integer1 = gbDpgService.queryGbPurchaseGoodsCount(map);
            System.out.println("subslslslsl" + map);
            if (integer1 > 0) {
                subTotal = gbDpgService.queryPurchaseGoodsSubTotal(map);
            }
            List<NxJrdhSupplierEntity> supplierEntities = gbDpgService.queryDisPurGoodsSupplierList(map);

            if (supplierEntities.size() > 0) {

                for (NxJrdhSupplierEntity supplierEntity : supplierEntities) {
                    Map<String, Object> mapUser = new HashMap<>();
                    mapUser.put("user", supplierEntity);

                    // 创建新的查询参数Map，避免参数污染
                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("typeNotEqual", 9);
                    queryMap.put("supplierBuy", 1);
                    queryMap.put("dayuStatus", 2);
                    queryMap.put("startDate", startDate);
                    queryMap.put("stopDate", stopDate);
                    queryMap.put("disId", map.get("disId"));
                    queryMap.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                    queryMap.put("offset", 0);
                    queryMap.put("limit", 100);
                    if (greatId != -1) {
                        queryMap.put("disGoodsGreatId", greatId);
                    }

                    System.out.println("mappppppppSuppp" + queryMap);
                    Integer integer = gbDpgService.queryGbDisGoodsTreeCount(queryMap);
                    List<GbDistributerGoodsEntity> goodsList = gbDpgService.queryDisTreeGoodsWithPurList(queryMap);
                    Double subTotal1 = gbDpgService.queryPurchaseGoodsSubTotal(queryMap);
                    mapUser.put("arr", goodsList);
                    mapUser.put("count", integer);
                    mapUser.put("purSubtotal", String.format("%.1f", subTotal1));
                    result.add(mapUser);
                }
            }

            BigDecimal total = new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP);
            mapR.put("supplierArr", result);
            mapR.put("total", total);
        }

        return R.ok().put("data", mapR);
    }

    /**
     * 获取采购用户日期列表
     * 接口: /gbdistributerpurchasegoods/getPurUserDate
     */
    @RequestMapping(value = "/getPurUserDate", method = RequestMethod.POST)
    @ResponseBody
    public R getPurUserDate(@RequestParam String startDate, @RequestParam String stopDate,
                            @RequestParam Integer disId, @RequestParam(required = false) Integer purDepId) {
        Map<String, Object> map = new HashMap<>();
        map.put("startDate", startDate);
        map.put("stopDate", stopDate);
        map.put("disId", disId);
        if (purDepId != null) {
            map.put("purDepId", purDepId);
        }
        map.put("dayuStatus", 4); // 已完成的采购 status > 4

        log.info("【getPurUserDate】查询参数: disId={}, purDepId={}, startDate={}, stopDate={}",
                disId, purDepId, startDate, stopDate);

        List<GbDepartmentUserEntity> userEntities = gbDpgService.queryPurUserList(map);
        log.info("【getPurUserDate】采购用户数量: {}", userEntities != null ? userEntities.size() : 0);

        return R.ok().put("data", userEntities);
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
        map.put("status", 0);
        System.out.println("supplierGetUnWeightOutPurGoods: " + map);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = gbDpgService.queryPurchaseGoodsWithDetailByParams(map);
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

        gbDpgService.update(purchaseGoodsEntity);
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
        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryObject(gbDpgBatchId);
        batchEntity.setGbDpbSubtotal(String.format("%.1f", subTotal));
        gbDPBService.update(batchEntity);

        return R.ok().put("data", purchaseGoodsEntity);
    }

}
