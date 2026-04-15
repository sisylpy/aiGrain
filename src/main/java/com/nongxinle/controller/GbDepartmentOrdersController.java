package com.nongxinle.controller;

import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerFatherGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                mapDep.put("depInfo", gbDepartmentService.queryDepInfoGb(dep.getGbDepartmentId()));
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
            mapR.put("depInfo", gbDepartmentService.queryDepInfoGb(depFatherId));
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
        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDistributerPurchaseGoodsService.queryObject(purchaseGoodsId);

        System.out.println("ordstats" + gbDepartmentOrdersEntity.getGbDoStatus());
        if(gbDepartmentOrdersEntity.getGbDoStatus() == 0){

            gbDepartmentOrdersEntity.setGbDoStatus(getGbOrderStatusProcurement());

            Integer gbDpgOrdersWeightAmount = purchaseGoodsEntity.getGbDpgOrdersWeightAmount();
            Integer gbDpgOrdersAmount = purchaseGoodsEntity.getGbDpgOrdersAmount();
            System.out.println("gbDpgOrdersFinishAmount=" + gbDpgOrdersWeightAmount +"gbDpgOrdersAmount== " + gbDpgOrdersAmount);
            if(gbDpgOrdersAmount - gbDpgOrdersWeightAmount == 1){
                purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusWeightFinished());
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
            gbDepartmentOrdersEntity.setGbDoBuyStatus(getGbOrderBuyStatusHasWeightAndPrice());

        }else{
            gbDepartmentOrdersEntity.setGbDoBuyStatus(getGbOrderBuyStatusPrepareing());
        }
        gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);
        gbDistributerPurchaseGoodsService.update(purchaseGoodsEntity);

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
            gbDistributerPurchaseGoodsService.update(purchaseGoodsEntity);

            Integer gbDpgBatchId = purchaseGoodsEntity.getGbDpgBatchId();
            Map<String, Object> mapBatch = new HashMap<>();
            mapBatch.put("batchId", gbDpgBatchId);
            Integer integer1 = gbDistributerPurchaseGoodsService.queryGbPurchaseGoodsCount(mapBatch);
            if(integer1 > 0){
                Double subTotal = gbDistributerPurchaseGoodsService.queryPurchaseGoodsSubTotal(mapBatch);
                GbDistributerPurchaseBatchEntity batchEntity = gbDistributerPurchaseBatchService.queryObject(gbDpgBatchId);
                batchEntity.setGbDpbSubtotal(String.format("%.1f", subTotal));
                gbDistributerPurchaseBatchService.update(batchEntity);
            }
        }

        return R.ok();
    }

}
