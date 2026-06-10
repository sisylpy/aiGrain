package com.nongxinle.controller;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.GbTypeUtils.*;

/**
 * 部门订单Controller
 */
@RestController
@RequestMapping("gbdepartmentorders")
public class GbDepartmentOrdersController {

    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDistributerPurchaseGoodsService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDistributerPurchaseBatchService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;
    @Autowired
    private GbDistributerFatherGoodsService gbDistributerFatherGoodsService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private NxJrdhSupplierService jrdhSupplierService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDPBService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private NxJrdhUserService nxJrdhUserService;
    @Autowired
    private GbDepartmentGoodsStockLedgerService gbDepartmentGoodsStockLedgerService;
    @Autowired
    private GbJjOrderPurchaseLinkService gbJjOrderPurchaseLinkService;




    @ResponseBody
    @RequestMapping(value = "/updateOrderGbJj", method = RequestMethod.POST)
    public R updateOrderGbJj(Integer id, String standard, String remark, String weight) {

        System.out.println("updateeelelellee" + id);
        //检查修改规格

        GbDepartmentOrdersEntity oldOrdersEntity = gbDepartmentOrdersService.getById(id);
        String oldStandard = oldOrdersEntity.getGbDoStandard();
        System.out.println("updateeelelellee" + oldStandard);

        Integer gbDoDisGoodsId = oldOrdersEntity.getGbDoDisGoodsId();
        GbDistributerGoodsEntity gbDisGoodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
        String standardname = gbDisGoodsEntity.getGbDgGoodsStandardname();
        Integer gbDoPurchaseGoodsId = oldOrdersEntity.getGbDoPurchaseGoodsId();

        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDistributerPurchaseGoodsService.getById(gbDoPurchaseGoodsId);

        if (!oldStandard.equals(standard)) {

            // 1，修改原来的purGoods
            Integer oldOrdersAmount = purchaseGoodsEntity.getGbDpgOrdersAmount();

            if (oldOrdersAmount == 1) { //如果新规格的采购商品只有一个订单

                Map<String, Object> map = new HashMap<>();
                map.put("disGoodsId", oldOrdersEntity.getGbDoDisGoodsId());
                map.put("equalStatus", 0);
                map.put("standard", standard);
                List<GbDistributerPurchaseGoodsEntity> goodsEntities = gbDistributerPurchaseGoodsService.queryOnlyPurGoods(map);
                if (goodsEntities.size() == 1) {
                    GbDistributerPurchaseGoodsEntity sameStandardPurGoods = goodsEntities.get(0);
                    BigDecimal decimal = new BigDecimal(sameStandardPurGoods.getGbDpgBuyQuantity()).add(new BigDecimal(weight));
                    sameStandardPurGoods.setGbDpgQuantity(decimal.toString());
                    sameStandardPurGoods.setGbDpgBuyQuantity(decimal.toString());
                    if (standard.equals(standardname) && sameStandardPurGoods.getGbDpgBuyPrice() != null) {
                        BigDecimal decimal1 = new BigDecimal(sameStandardPurGoods.getGbDpgBuyPrice()).multiply(decimal).setScale(1, BigDecimal.ROUND_HALF_UP);
                        sameStandardPurGoods.setGbDpgBuySubtotal(decimal1.toString());
                    }
                    gbDistributerPurchaseGoodsService.updateById(sameStandardPurGoods);
                    //删除原来的采购商品
                    gbDistributerPurchaseGoodsService.removeById(purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());

                } else {

                    purchaseGoodsEntity.setGbDpgBuyQuantity(weight);
                    purchaseGoodsEntity.setGbDpgQuantity(weight);
                    purchaseGoodsEntity.setGbDpgStandard(standard);
                    if (standard.equals(standardname) && purchaseGoodsEntity.getGbDpgBuyPrice() != null) {
                        BigDecimal decimal = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice()).multiply(new BigDecimal(weight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                        purchaseGoodsEntity.setGbDpgBuySubtotal(decimal.toString());
                    }
                    gbDistributerPurchaseGoodsService.updateById(purchaseGoodsEntity);
                }

            } else { //如果采购商品有多个订单

                BigDecimal subtract = new BigDecimal(purchaseGoodsEntity.getGbDpgQuantity()).subtract(new BigDecimal(oldOrdersEntity.getGbDoQuantity()));
                purchaseGoodsEntity.setGbDpgQuantity(subtract.toString());
                purchaseGoodsEntity.setGbDpgOrdersAmount(purchaseGoodsEntity.getGbDpgOrdersAmount() - 1);
                if (standard.equals(standardname) && purchaseGoodsEntity.getGbDpgBuyPrice() != null) {
                    BigDecimal decimal = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice()).multiply(new BigDecimal(weight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    purchaseGoodsEntity.setGbDpgBuySubtotal(decimal.toString());
                }
                gbDistributerPurchaseGoodsService.updateById(purchaseGoodsEntity);

                // 2，查询是否有采购的同一个商品
                //有采购商品
                Map<String, Object> map = new HashMap<>();
                map.put("disGoodsId", oldOrdersEntity.getGbDoDisGoodsId());
                map.put("equalStatus", 0);
                map.put("standard", standard);
                List<GbDistributerPurchaseGoodsEntity> goodsEntities = gbDistributerPurchaseGoodsService.queryOnlyPurGoods(map);
                if (goodsEntities.size() == 0) {
                    //是个新采购商品
                    GbDistributerPurchaseGoodsEntity newPurGoods = new GbDistributerPurchaseGoodsEntity();
                    newPurGoods.setGbDpgDisGoodsFatherId(oldOrdersEntity.getGbDoDisGoodsFatherId());
                    newPurGoods.setGbDpgDisGoodsId(oldOrdersEntity.getGbDoDisGoodsId());
                    newPurGoods.setGbDpgDistributerId(oldOrdersEntity.getGbDoDistributerId());
                    newPurGoods.setGbDpgApplyDate(formatWhatDay(0));
                    newPurGoods.setGbDpgStatus(getGbPurchaseGoodsStatusNew());
                    newPurGoods.setGbDpgOrdersAmount(1);
                    newPurGoods.setGbDpgOrdersFinishAmount(0);
                    newPurGoods.setGbDpgOrdersBillAmount(0);
                    newPurGoods.setGbDpgStandard(standard);
                    newPurGoods.setGbDpgQuantity(weight);
                    newPurGoods.setGbDpgPurchaseWeek(getWeek(0));
                    newPurGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
                    if (standard.equals(standardname) && purchaseGoodsEntity.getGbDpgBuyPrice() != null) {
                        BigDecimal decimal = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice()).multiply(new BigDecimal(weight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                        newPurGoods.setGbDpgBuySubtotal(decimal.toString());
                    }
                    gbDistributerPurchaseGoodsService.save(newPurGoods);
                    Integer gbDistributerPurchaseGoodsId = newPurGoods.getGbDistributerPurchaseGoodsId();
                    oldOrdersEntity.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);


                } else {
                    // 3， 给老采购商品添加新订单
                    GbDistributerPurchaseGoodsEntity gbDisPurGoodsEntity = goodsEntities.get(0);
                    Integer gbDistributerPurchaseGoodsId = gbDisPurGoodsEntity.getGbDistributerPurchaseGoodsId();
                    oldOrdersEntity.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);
                    //采购商品订单数量更新
                    Integer gbDpgOrdersAmount = gbDisPurGoodsEntity.getGbDpgOrdersAmount();
                    gbDisPurGoodsEntity.setGbDpgOrdersAmount(gbDpgOrdersAmount + 1);
                    BigDecimal purQuantity = new BigDecimal(gbDisPurGoodsEntity.getGbDpgQuantity());
                    BigDecimal orderQuantity = new BigDecimal(weight);
                    BigDecimal add = purQuantity.add(orderQuantity).setScale(1, BigDecimal.ROUND_HALF_UP);
                    gbDisPurGoodsEntity.setGbDpgQuantity(add.toString());
                    if (standard.equals(standardname) && purchaseGoodsEntity.getGbDpgBuyPrice() != null) {
                        BigDecimal decimal = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice()).multiply(add).setScale(1, BigDecimal.ROUND_HALF_UP);
                        gbDisPurGoodsEntity.setGbDpgBuySubtotal(decimal.toString());
                    }
                    gbDistributerPurchaseGoodsService.updateById(gbDisPurGoodsEntity);
                }

                //元采购商品减去
                String gbDpgBuyQuantity = purchaseGoodsEntity.getGbDpgBuyQuantity();
                BigDecimal decimal = new BigDecimal(gbDpgBuyQuantity).subtract(new BigDecimal(weight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                purchaseGoodsEntity.setGbDpgBuyQuantity(decimal.toString());
                purchaseGoodsEntity.setGbDpgQuantity(decimal.toString());
                purchaseGoodsEntity.setGbDpgOrdersAmount(oldOrdersAmount - 1);
                if (purchaseGoodsEntity.getGbDpgStandard().equals(standardname) && purchaseGoodsEntity.getGbDpgBuyPrice() != null) {
                    BigDecimal decimal1 = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice()).multiply(decimal).setScale(1, BigDecimal.ROUND_HALF_UP);
                    purchaseGoodsEntity.setGbDpgBuySubtotal(decimal1.toString());
                    gbDistributerPurchaseGoodsService.updateById(purchaseGoodsEntity);
                }

            }


            // 修改 price 和 subtotal
            if (standard.equals(gbDisGoodsEntity.getGbDgGoodsStandardname())) {
                oldOrdersEntity.setGbDoWeight(weight);
                if (standard.equals(standardname) && oldOrdersEntity.getGbDoPrice() != null) {
                    BigDecimal decimal = new BigDecimal(oldOrdersEntity.getGbDoPrice()).multiply(new BigDecimal(weight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    oldOrdersEntity.setGbDoSubtotal(decimal.toString());
                }
            } else {
                oldOrdersEntity.setGbDoWeight("0");
                oldOrdersEntity.setGbDoSubtotal("0");
            }
        } else {
            System.out.println("updatepururrurur");
            Integer oldOrdersAmount = purchaseGoodsEntity.getGbDpgOrdersAmount();
            if (oldOrdersAmount == 1) {
                purchaseGoodsEntity.setGbDpgQuantity(weight);
                purchaseGoodsEntity.setGbDpgStandard(standard);
                if (standard.equals(standardname) && oldOrdersEntity.getGbDoPrice() != null && !oldOrdersEntity.getGbDoPrice().trim().isEmpty()) {
                    BigDecimal decimal = new BigDecimal(oldOrdersEntity.getGbDoPrice()).multiply(new BigDecimal(weight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    purchaseGoodsEntity.setGbDpgBuySubtotal(decimal.toString());
                }
                gbDistributerPurchaseGoodsService.updateById(purchaseGoodsEntity);
            } else {

                BigDecimal subtract = new BigDecimal(purchaseGoodsEntity.getGbDpgQuantity()).subtract(new BigDecimal(oldOrdersEntity.getGbDoQuantity()));
                purchaseGoodsEntity.setGbDpgQuantity(subtract.toString());
                purchaseGoodsEntity.setGbDpgOrdersAmount(purchaseGoodsEntity.getGbDpgOrdersAmount() - 1);
                if (standard.equals(standardname) && purchaseGoodsEntity.getGbDpgBuyPrice() != null) {
                    BigDecimal decimal = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice()).multiply(subtract).setScale(1, BigDecimal.ROUND_HALF_UP);
                    purchaseGoodsEntity.setGbDpgBuySubtotal(decimal.toString());
                }
                gbDistributerPurchaseGoodsService.updateById(purchaseGoodsEntity);
            }
        }


        oldOrdersEntity.setGbDoRemark(remark);
        oldOrdersEntity.setGbDoQuantity(weight);
        oldOrdersEntity.setGbDoStandard(standard);
        System.out.println("fdstandnndndd" + standard + "snen" + standardname);
        if (standard.equals(standardname) && oldOrdersEntity.getGbDoPrice() != null && !oldOrdersEntity.getGbDoPrice().trim().isEmpty()) {
            BigDecimal decimal = new BigDecimal(oldOrdersEntity.getGbDoPrice()).multiply(new BigDecimal(weight)).setScale(1, BigDecimal.ROUND_HALF_UP);
            oldOrdersEntity.setGbDoSubtotal(decimal.toString());
        }
        gbDepartmentOrdersService.update(oldOrdersEntity);

        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
        oldOrdersEntity.setGbDistributerGoodsEntity(goodsEntity);
        return R.ok().put("data", oldOrdersEntity);
    }

    /**
     * 获取部门父级AI申请订单
     * 参考老项目实现，返回结构包含子部门及其订单
     *
     * @param depFatherId 父部门ID
     * @return 订单列表
     */
    @RequestMapping(value = "/depGetApplyAiFather/{depFatherId}", method = RequestMethod.GET)
    @ResponseBody
    public R depGetApplyAiFather(@PathVariable Integer depFatherId) {
        Map<String, Object> mapR = new HashMap<>();
        List<Map<String, Object>> mapList = new ArrayList<>();

        // 查询子部门列表
        List<GbDepartmentEntity> entities = gbDepartmentService.querySubDepartments(depFatherId);
        System.out.println("depGetApplyAiFather - subDepartments size: " + entities.size());

        if (entities.size() > 0) {
            // 有子部门，遍历每个子部门查询订单
            for (GbDepartmentEntity dep : entities) {
                Map<String, Object> mapDep = new HashMap<>();
                mapDep.put("depId", dep.getGbDepartmentId());
                mapDep.put("depName", dep.getGbDepartmentName());

                // 查询该部门的父级商品订单
                Map<String, Object> map1 = new HashMap<>();
                map1.put("status", 3);
                map1.put("depId", dep.getGbDepartmentId());
                map1.put("orderTypeNotEqual", 9);

                List<GbDistributerFatherGoodsEntity> gbDistributerFatherGoodsEntities =
                        gbDepartmentOrdersService.queryGrandGoodsOrder(map1);
                mapDep.put("depOrders", gbDistributerFatherGoodsEntities);
                System.out.println("depGetApplyAiFather - depId: " + dep.getGbDepartmentId() +
                        ", orders size: " + gbDistributerFatherGoodsEntities.size());

                mapList.add(mapDep);
            }
            mapR.put("arr", mapList);
            return R.ok().put("data", mapR);
        } else {
            // 没有子部门，直接查询父部门的订单
            Map<String, Object> map = new HashMap<>();
            map.put("status", 3);
            map.put("depFatherId", depFatherId);
            map.put("orderTypeNotEqual", 9);

            List<GbDistributerFatherGoodsEntity> gbDistributerFatherGoodsEntities =
                    gbDepartmentOrdersService.queryGrandGoodsOrder(map);

            mapR.put("arr", gbDistributerFatherGoodsEntities);
            return R.ok().put("data", mapR);
        }
    }

    /**
     * 按时间获取部门AI申请订单
     * 参考老项目实现，返回结构包含子部门及其订单
     *
     * @param depFatherId 父部门ID
     * @return 订单列表
     */
    @RequestMapping(value = "/depGetApplyAiByTime/{depFatherId}", method = RequestMethod.GET)
    @ResponseBody
    public R depGetApplyAiByTime(@PathVariable Integer depFatherId) {
        Map<String, Object> mapR = new HashMap<>();
        List<Map<String, Object>> mapList = new ArrayList<>();

        // 查询子部门列表
        List<GbDepartmentEntity> entities = gbDepartmentService.querySubDepartments(depFatherId);
        System.out.println("depGetApplyAiByTime - subDepartments size: " + entities.size());
        if (entities.size() > 1) {
            // 有子部门，遍历每个子部门查询订单
            for (GbDepartmentEntity dep : entities) {
                Map<String, Object> mapDep = new HashMap<>();
                mapDep.put("depId", dep.getGbDepartmentId());
                mapDep.put("depName", dep.getGbDepartmentName());

                // 查询该部门的订单列表
                Map<String, Object> map1 = new HashMap<>();
                map1.put("status", 3);
                map1.put("depId", dep.getGbDepartmentId());
                map1.put("orderTypeNotEqual", 9);

                List<GbDepartmentOrdersEntity> depOrders = gbDepartmentOrdersService.queryDisOrdersListByParams(map1);
                mapDep.put("depOrders", depOrders);
                mapDep.put("depInfo", gbDepartmentService.getById(dep.getGbDepartmentId()));
                System.out.println("depGetApplyAiByTime - depId: " + dep.getGbDepartmentId() +
                        ", orders size: " + depOrders.size());

                mapList.add(mapDep);
            }
            mapR.put("arr", mapList);
            return R.ok().put("data", mapR);
        } else {
            // 没有子部门，直接查询父部门的订单
            Map<String, Object> map = new HashMap<>();
            map.put("status", 3);
            map.put("depFatherId", depFatherId);
            map.put("orderTypeNotEqual", 9);

            List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(map);

            mapR.put("arr", ordersEntities);
            mapR.put("depInfo", gbDepartmentService.getById(depFatherId));
            return R.ok().put("data", mapR);
        }
    }

    /**
     * 订单出库称重
     * @param orderId 订单ID
     * @param weight 重量
     * @return 操作结果
     */
    @RequestMapping(value = "/gbOrderOutWeight", method = RequestMethod.POST)
    @ResponseBody
    public R gbOrderOutWeight(@RequestParam Integer orderId, @RequestParam String weight) {

        GbDepartmentOrdersEntity gbDepartmentOrdersEntity = gbDepartmentOrdersService.queryObject(orderId);
        Integer purchaseGoodsId = gbDepartmentOrdersEntity.getGbDoPurchaseGoodsId();
        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDistributerPurchaseGoodsService.getById(purchaseGoodsId);

        System.out.println("ordstats" + gbDepartmentOrdersEntity.getGbDoStatus());
        if(gbDepartmentOrdersEntity.getGbDoStatus() == 0){

            gbDepartmentOrdersEntity.setGbDoStatus(GbConstants.DepartmentOrderStatus.WEIGHT_CAPTURED);

            Integer gbDpgOrdersWeightAmount = purchaseGoodsEntity.getGbDpgOrdersWeightAmount();
            Integer gbDpgOrdersAmount = purchaseGoodsEntity.getGbDpgOrdersAmount();
            System.out.println("gbDpgOrdersFinishAmount=" + gbDpgOrdersWeightAmount +"gbDpgOrdersAmount== " + gbDpgOrdersAmount);
            if(gbDpgOrdersAmount - gbDpgOrdersWeightAmount == 1){
                purchaseGoodsEntity.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.WEIGHT_FINISHED);
            }
            purchaseGoodsEntity.setGbDpgOrdersWeightAmount(gbDpgOrdersWeightAmount + 1);
        }

        gbDepartmentOrdersEntity.setGbDoWeight(weight);

        System.out.println("priiciie" + gbDepartmentOrdersEntity.getGbDoPrice());
        if (gbDepartmentOrdersEntity.getGbDoPrice() != null && !gbDepartmentOrdersEntity.getGbDoPrice().trim().isEmpty() && !gbDepartmentOrdersEntity.getGbDoPrice().equals("0.0")) {
            BigDecimal decimal1 = new BigDecimal(gbDepartmentOrdersEntity.getGbDoPrice());
            BigDecimal decimal2 = new BigDecimal(weight);
            BigDecimal decimal3 = decimal1.multiply(decimal2).setScale(1, BigDecimal.ROUND_HALF_UP);
            gbDepartmentOrdersEntity.setGbDoSubtotal(decimal3.toString());
            gbDepartmentOrdersEntity.setGbDoBuyStatus(GbConstants.OrderBuyStatus.HAS_WEIGHT_AND_PRICE);

        }else{
            gbDepartmentOrdersEntity.setGbDoBuyStatus(getGbOrderBuyStatusPrepareing());
        }
        gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);
        gbDistributerPurchaseGoodsService.updateById(purchaseGoodsEntity);

        System.out.println("aaaaaaaaaaaaaaaaaaaaa");
        Map<String, Object> map = new HashMap<>();
        map.put("purGoodsId", purchaseGoodsId);
        System.out.println("mapsusmssnnsns1111aaaa" + map);
        Integer integer = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map);
        if(integer > 0){
            double weightTotal = gbDepartmentOrdersService.queryOrderWeightTotalByPurGoodsId(purchaseGoodsId);
            purchaseGoodsEntity.setGbDpgBuyQuantity(String.format("%.1f", weightTotal));
             map.put("subtotal", 0);
            Integer integerHave = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map);
            if(integerHave > 0){
                System.out.println("queryGbOrdersSubtotalqueryGbOrdersSubtotal");
                double subtotalTotal = gbDepartmentOrdersService.queryGbOrdersSubtotal(map);
                purchaseGoodsEntity.setGbDpgBuySubtotal(String.format("%.1f", subtotalTotal));
            }
            gbDistributerPurchaseGoodsService.updateById(purchaseGoodsEntity);
//
//            Integer gbDpgBatchId = purchaseGoodsEntity.getGbDpgBatchId();
//            Map<String, Object> mapBatch = new HashMap<>();
//            mapBatch.put("batchId", gbDpgBatchId);
//            Integer integer1 = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(mapBatch);
//            if(integer1 > 0){
//                Double subTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapBatch);
//                GbDistributerPurchaseBatchEntity batchEntity = gbDistributerPurchaseBatchService.getById(gbDpgBatchId);
//                batchEntity.setGbDpbSubtotal(String.format("%.1f", subTotal));
//                gbDistributerPurchaseBatchService.updateById(batchEntity);
//            }
        }

        return R.ok();
    }



    @ResponseBody
    /**
     * 已有批发商商品时：新建部门商品并保存订货单与采购行关联。
     * <p>路径 {@code /saveOrdersGbJjAndSaveDepGoods} 不变。
     */
    @RequestMapping("/createDepartmentOrderWithNewDepDisGoods")
    public R createDepartmentOrderWithNewDepDisGoods(@RequestBody GbDepartmentOrdersEntity gbDepartmentOrders) {


        Integer gbDoDisGoodsId = gbDepartmentOrders.getGbDoDisGoodsId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.getById(gbDoDisGoodsId);
        GbDepartmentDisGoodsEntity mendianDisGoodsEntity =
                gbDepartmentDisGoodsService.createDepDisGoodsForJjOrder(
                        gbDepartmentOrders, gbDistributerGoodsEntity);
        gbDepartmentOrders.setGbDoDepDisGoodsId(mendianDisGoodsEntity.getGbDepartmentDisGoodsId());
        gbJjOrderPurchaseLinkService.applyDisGoodsCategoryHierarchyToOrder(
                gbDepartmentOrders, gbDistributerGoodsEntity.getGbDgDfgGoodsFatherId());
        gbJjOrderPurchaseLinkService.applyJjOrderTimestamps(gbDepartmentOrders);
        gbDepartmentOrders.setGbDoGoodsType(gbDistributerGoodsEntity.getGbDgGoodsType());
        gbDepartmentOrders.setGbDoOrderType(gbDistributerGoodsEntity.getGbDgGoodsType());
        gbDepartmentOrders.setGbDoBuyStatus(GbConstants.OrderBuyStatus.NEW);
        gbDepartmentOrders.setGbDoStatus(GbConstants.DepartmentOrderStatus.NEW);
        gbDepartmentOrdersService.save(gbDepartmentOrders);

        gbJjOrderPurchaseLinkService.resolvePurchaseGoodsLineForJjOrder(
                gbDepartmentOrders,
                gbDistributerGoodsEntity,
                GbJjOrderPurchaseLinkService.PurchaseGoodsLinkMode.MERGE_BY_PUR_DEPARTMENT);

        if (gbDistributerGoodsEntity.getGbDgGbSupplierId() != null && gbDistributerGoodsEntity.getGbDgGbSupplierId() != -1) {
            Integer gbDoDepartmentFatherId = gbDepartmentOrders.getGbDoDepartmentFatherId();
            GbDepartmentEntity departmentEntity = gbDepartmentService.getById(gbDoDepartmentFatherId);
            Map<String, Object> mapData = gbJjOrderPurchaseLinkService.ensureSupplierPurchaseBatchForJjOrder(gbDepartmentOrders, gbDistributerGoodsEntity);
            Integer batchId = (Integer) mapData.get("batchId");
            Integer gbDepartmentDisId = departmentEntity.getGbDepartmentDisId();
            GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDepartmentDisId);

            NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.getById(gbDistributerGoodsEntity.getGbDgGbSupplierId());

            if (supplierEntity.getNxJrdhsUserId() != null) {
//                Map<String, TemplateData> mapNotice = new HashMap<>();
//                mapNotice.put("time2", new TemplateData(formatWhatDayTime(0)));
//                mapNotice.put("thing13", new TemplateData(departmentEntity.getGbDepartmentName()));
//                mapNotice.put("thing8", new TemplateData(gbDistributerGoodsEntity.getGbDgGoodsName()));
//                mapNotice.put("thing10", new TemplateData("订货"));
//                Integer gbDoOrderUserId = gbDepartmentOrders.getGbDoOrderUserId();
//                GbDepartmentUserEntity gbDepartmentUserEntity = gbDepartmentUserService.getById(gbDoOrderUserId);
//                mapNotice.put("thing9", new TemplateData(gbDepartmentUserEntity.getGbDuWxNickName()));
//                System.out.println("nociiciiiicicautotootototoototo" + mapNotice);
//                Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
//                NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(nxJrdhsUserId);
//
//                StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbReceiveBatch/gbReceiveBatch");
//                pathBuilder.append("?batchId=").append(batchId);
//                pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
//                pathBuilder.append("&from=notification"); // 添加这个参数
//
//                String path = pathBuilder.toString();
//                System.out.println("Encoded URLARRRRRRRRRR00000000: " + path);
//                WeNoticeService.autoGbSuppliertixingMessageJj(nxJrdhUserEntity.getNxJrdhWxOpenId(), path, mapNotice);
            }

        }


        GbDepartmentOrdersEntity gbDepartmentOrdersEntity = gbDepartmentOrdersService.queryObject(gbDepartmentOrders.getGbDepartmentOrdersId());
        Map<String, Object> mapG = new HashMap<>();
        mapG.put("disGoodsId", gbDoDisGoodsId);
        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.getById(gbDoDisGoodsId);
        gbDepartmentOrdersEntity.setGbDistributerGoodsEntity(goodsEntity);
        return R.ok().put("data", gbDepartmentOrdersEntity);

    }



    /**
     * ORDER
     * 删除申请
     *
     * @param gbDepartmentOrdersId 订货申请id
     * @return ok
     */
    @ResponseBody
    @RequestMapping("/deleteOrderGb/{gbDepartmentOrdersId}")
    public R deleteOrderGb(@PathVariable Integer gbDepartmentOrdersId) {
        GbDepartmentOrdersEntity ordersEntity = gbDepartmentOrdersService.queryObject(gbDepartmentOrdersId);


        Integer gbDoDisGoodsId = ordersEntity.getGbDoDisGoodsId();
        if (ordersEntity.getGbDoStatus() != -2 && ordersEntity.getGbDoPurchaseGoodsId() != -1) {
            Integer gbDoPurchaseGoodsId = ordersEntity.getGbDoPurchaseGoodsId();
            GbDistributerPurchaseGoodsEntity gbDistributerPurchaseGoodsEntity = gbDistributerPurchaseGoodsService.getById(gbDoPurchaseGoodsId);
            if (gbDistributerPurchaseGoodsEntity != null) {
                Integer gbDpgOrdersAmount = gbDistributerPurchaseGoodsEntity.getGbDpgOrdersAmount();
                if (gbDpgOrdersAmount > 1) {
                    gbDistributerPurchaseGoodsEntity.setGbDpgOrdersAmount(gbDpgOrdersAmount - 1);
                    BigDecimal subtract = new BigDecimal(gbDistributerPurchaseGoodsEntity.getGbDpgQuantity()).subtract(new BigDecimal(ordersEntity.getGbDoQuantity()));
                    gbDistributerPurchaseGoodsEntity.setGbDpgQuantity(subtract.toString());
                    gbDistributerPurchaseGoodsService.updateById(gbDistributerPurchaseGoodsEntity);
                } else {
                    //订货批次是否是最后一个采购商品
                    Integer gbDpgBatchId = gbDistributerPurchaseGoodsEntity.getGbDpgBatchId();
                    Integer oldSupplierId = gbDistributerPurchaseGoodsEntity.getGbDpgPurchaseNxSupplierId();
                    Integer gbDpgDistributerId1 = gbDistributerPurchaseGoodsEntity.getGbDpgDistributerId();
                    Map<String, Object> mapBatch = new HashMap<>();
                    mapBatch.put("batchId", gbDpgBatchId);
                    List<GbDistributerPurchaseGoodsEntity> goodsEntities = gbDistributerPurchaseGoodsService.queryOnlyPurGoods(mapBatch);
                    if (goodsEntities.size() == 1) {
                        gbDPBService.removeById(gbDistributerPurchaseGoodsEntity.getGbDpgBatchId());
                    }
                    gbDistributerPurchaseGoodsService.removeById(gbDoPurchaseGoodsId);

                    System.out.println("s删除订单提醒供货商");
                    if (oldSupplierId != -1) {
                        NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.getById(oldSupplierId);
                        Integer jrdhsUserId = supplierEntity.getNxJrdhsUserId();
                        NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(jrdhsUserId);
                        GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDpgDistributerId1);
                        System.out.println("tuuihuouonoticeeiee");
//                        if (supplierEntity.getNxJrdhsUserId() != null) {
//                            Map<String, TemplateData> mapNotice = new HashMap<>();
//                            mapNotice.put("date7", new TemplateData(formatWhatDayTime(0)));
//                            mapNotice.put("thing12", new TemplateData("删除订货" + gbDistributerGoodsEntity.getGbDgGoodsName()));
//                            if (goodsEntities.size() == 1) {
//                                mapNotice.put("phrase9", new TemplateData("订单取消"));
//                            } else {
//                                mapNotice.put("phrase9", new TemplateData("订单变更"));
//                            }
//
//                            StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbReceiveBatch/gbReceiveBatch");
//                            pathBuilder.append("?batchId=").append(gbDpgBatchId);
//                            pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
//                            pathBuilder.append("&from=notification"); // 添加这个参数
//                            String path = pathBuilder.toString();
//                            WeNoticeService.changeOrderSuppliertixingMessageJj(nxJrdhUserEntity.getNxJrdhWxOpenId(), path, mapNotice);
//                        }

                    }
                }
            }
        }



        gbDepartmentOrdersService.removeById(gbDepartmentOrdersId);
        return R.ok();

    }




    /**
     * 按批发商商品发起部门订货（Jj），并解析/合并采购商品行。
     * <p>路径 {@code /saveOrdersGbJj} 不变。
     */
    @ResponseBody
    @RequestMapping("/createDepartmentOrderForJj")
    public R createDepartmentOrderForJj(@RequestBody GbDepartmentOrdersEntity gbDepartmentOrders) {

        System.out.println("autottototot" + gbDepartmentOrders);

        if (Boolean.TRUE.equals(gbDepartmentOrders.getStockIsZero())) {
            gbDepartmentGoodsStockLedgerService.clearDepGoodsStockWhenJjStockIsZero(gbDepartmentOrders);
        }

        Integer gbDoDisGoodsId = gbDepartmentOrders.getGbDoDisGoodsId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);

        gbDepartmentOrders.setGbDoNxGoodsId(gbDistributerGoodsEntity.getGbDgNxGoodsId());
        gbDepartmentOrders.setGbDoNxGoodsFatherId(gbDistributerGoodsEntity.getGbDgNxFatherId());
        gbDepartmentOrders.setGbDoDistributerId(gbDistributerGoodsEntity.getGbDgDistributerId());
        gbDepartmentOrders.setGbDoToDepartmentId(gbDistributerGoodsEntity.getGbDgGbDepartmentId());
        gbDepartmentOrders.setGbDoDisGoodsFatherId(gbDistributerGoodsEntity.getGbDgDfgGoodsFatherId());
        gbDepartmentOrders.setGbDoPurchaseGoodsId(-1);
        gbDepartmentOrders.setGbDoGoodsType(gbDistributerGoodsEntity.getGbDgGoodsType());
        gbDepartmentOrders.setGbDoOrderType(gbDistributerGoodsEntity.getGbDgGoodsType());
        gbDepartmentOrders.setGbDoBuyStatus(GbConstants.OrderBuyStatus.NEW);
        gbDepartmentOrders.setGbDoStatus(GbConstants.DepartmentOrderStatus.NEW);
        gbJjOrderPurchaseLinkService.applyJjOrderTimestamps(gbDepartmentOrders);
        Integer gbDoDisGoodsFatherId = gbDepartmentOrders.getGbDoDisGoodsFatherId();
        gbJjOrderPurchaseLinkService.applyDisGoodsCategoryHierarchyToOrder(gbDepartmentOrders, gbDoDisGoodsFatherId);
        gbDepartmentOrdersService.save(gbDepartmentOrders);

        gbJjOrderPurchaseLinkService.resolvePurchaseGoodsLineForJjOrder(
                gbDepartmentOrders,
                gbDistributerGoodsEntity,
                GbJjOrderPurchaseLinkService.PurchaseGoodsLinkMode.MERGE_BY_SUPPLIER_OR_STATUS);

        Integer gbDoDepartmentFatherId = gbDepartmentOrders.getGbDoDepartmentFatherId();
        GbDepartmentEntity departmentEntity = gbDepartmentService.getById(gbDoDepartmentFatherId);
        System.out.println("gbgst" + gbDistributerGoodsEntity.getGbDgGbSupplierId());

        if (gbDistributerGoodsEntity.getGbDgGbSupplierId() != null && gbDistributerGoodsEntity.getGbDgGbSupplierId() != -1) {
            System.out.println("gbgst" + gbDistributerGoodsEntity.getGbDgGbSupplierId());

            Map<String, Object> mapData = gbJjOrderPurchaseLinkService.ensureSupplierPurchaseBatchForJjOrder(gbDepartmentOrders, gbDistributerGoodsEntity);
            Integer batchId = (Integer) mapData.get("batchId");
            Integer gbDepartmentDisId = departmentEntity.getGbDepartmentDisId();
            GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDepartmentDisId);

            NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.getById(gbDistributerGoodsEntity.getGbDgGbSupplierId());

            if (supplierEntity.getNxJrdhsUserId() != null) {
//                Map<String, TemplateData> mapNotice = new HashMap<>();
//                mapNotice.put("time2", new TemplateData(formatWhatDayTime(0)));
//                mapNotice.put("thing13", new TemplateData(departmentEntity.getGbDepartmentName()));
//                mapNotice.put("thing8", new TemplateData(gbDistributerGoodsEntity.getGbDgGoodsName()));
//                mapNotice.put("thing10", new TemplateData("订货"));
//                Integer gbDoOrderUserId = gbDepartmentOrders.getGbDoOrderUserId();
//                GbDepartmentUserEntity gbDepartmentUserEntity = gbDepartmentUserService.queryObject(gbDoOrderUserId);
//                mapNotice.put("thing9", new TemplateData(gbDepartmentUserEntity.getGbDuWxNickName()));
//                System.out.println("nociiciiiicicautotootototoototo" + mapNotice);
//                Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
//                NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(nxJrdhsUserId);
//
//                StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbReceiveBatch/gbReceiveBatch");
//                pathBuilder.append("?batchId=").append(batchId);
//                pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
//                pathBuilder.append("&from=notification"); // 添加这个参数
//                String path = pathBuilder.toString();
//                System.out.println("Encoded URLARRRRRRRRRR00000000saveOrdersGbJj: " + path);
//                WeNoticeService.autoGbSuppliertixingMessageJj(nxJrdhUserEntity.getNxJrdhWxOpenId(), path, mapNotice);
            }
        }


        gbDepartmentOrdersService.update(gbDepartmentOrders);
        GbDepartmentOrdersEntity gbDepartmentOrdersEntity = gbDepartmentOrdersService.queryObject(gbDepartmentOrders.getGbDepartmentOrdersId());
        Map<String, Object> mapG = new HashMap<>();
        mapG.put("disGoodsId", gbDoDisGoodsId);
        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.getById(gbDoDisGoodsId);
        gbDepartmentOrdersEntity.setGbDistributerGoodsEntity(goodsEntity);

        return R.ok().put("data", gbDepartmentOrdersEntity);
    }



    @RequestMapping(value = "/getDisGoodsOrderDayJingjinig", method = RequestMethod.POST)
    @ResponseBody
    public R getDisGoodsOrderDayJingjinig(Integer disGoodsId, String startDate, String stopDate, Integer depId, Integer searchDepIds) {

        Integer howManyDaysInPeriod = 0;
        if (!startDate.equals(stopDate)) {
            howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        if (howManyDaysInPeriod > 0) {
            Map<String, Object> rangeParams = new HashMap<>();
            rangeParams.put("disGoodsId", disGoodsId);
            rangeParams.put("equalStatus", 4);
            rangeParams.put("startDate", startDate);
            rangeParams.put("stopDate", stopDate);
            if (searchDepIds != null && searchDepIds != -1) {
                rangeParams.put("depId", searchDepIds);
            }

            List<String> datesWithOrders = gbDepartmentOrdersService.queryDisGoodsDistinctArriveDates(rangeParams);
            for (String whichDay : datesWithOrders) {
                Map<String, Object> mapResult = new HashMap<>();
                mapResult.put("date", whichDay);

                Map<String, Object> mapDisGoods = new HashMap<>();
                mapDisGoods.put("disGoodsId", disGoodsId);
                mapDisGoods.put("arriveDate", whichDay);
                mapDisGoods.put("equalStatus", 4);
                if (searchDepIds != null && searchDepIds != -1) {
                    mapDisGoods.put("depId", searchDepIds);
                }

                List<GbDepartmentOrdersEntity> ordersEntities =
                        gbDepartmentOrdersService.queryDisOrdersListByParams(mapDisGoods);
                double total = 0;
                if (!ordersEntities.isEmpty()) {
                    total = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapDisGoods);
                }

                mapResult.put("arr", ordersEntities);
                mapResult.put("total", total);
                if (!ordersEntities.isEmpty()) {
                    list.add(mapResult);
                }
            }
        } else {
            Map<String, Object> mapResult = new HashMap<>();
            mapResult.put("arriveDate", startDate);
            double total = 0;
            Map<String, Object> mapDisGoods = new HashMap<>();
            mapDisGoods.put("disGoodsId", disGoodsId);
            mapDisGoods.put("equalStatus", 3);
            if (searchDepIds != null && searchDepIds != -1) {
                mapDisGoods.put("depId", searchDepIds);
            }

            List<GbDepartmentOrdersEntity> ordersEntities = new ArrayList<>();
            Integer count = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(mapDisGoods);

            if (count > 0) {
                ordersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(mapDisGoods);
                total = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapDisGoods);
            }
            mapResult.put("total", total);
            mapResult.put("arr", ordersEntities);
            if (!ordersEntities.isEmpty()) {
                list.add(mapResult);
            }
        }
        return R.ok().put("data", list);

    }

}
