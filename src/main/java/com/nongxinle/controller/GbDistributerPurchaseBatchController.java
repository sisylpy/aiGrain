package com.nongxinle.controller;

/**
 * @author lpy
 * @date 06-25 22:52
 */

import java.util.*;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("gbdistributerpurchasebatch")
@Slf4j
@RequiredArgsConstructor
public class GbDistributerPurchaseBatchController {

    private final GbDistributerPurchaseBatchFinishPayService gbDistributerPurchaseBatchFinishPayService;
    private final GbDistributerPurchaseBatchDisPurchaseDetailTypeService gbDistributerPurchaseBatchDisPurchaseDetailTypeService;
    private final GbDistributerPurchaseBatchDisPurchaseDetaiTypeWithIdService gbDistributerPurchaseBatchDisPurchaseDetaiTypeWithIdService;
    private final GbDistributerPurchaseBatchSellerPurchaseBatchsGbService gbDistributerPurchaseBatchSellerPurchaseBatchsGbService;
    private final GbDistributerPurchaseBatchSupplierBillsWithStatusService gbDistributerPurchaseBatchSupplierBillsWithStatusService;
    private final GbDistributerPurchaseBatchGbSupplierBillsService gbDistributerPurchaseBatchGbSupplierBillsService;
    private final GbDistributerPurchaseBatchDisCheckUnPayBillsService gbDistributerPurchaseBatchDisCheckUnPayBillsService;
    private final GbDistributerPurchaseBatchFinishShareReturnService gbDistributerPurchaseBatchFinishShareReturnService;
    private final GbDistributerPurchaseBatchSellUserReadDisBatchService gbDistributerPurchaseBatchSellUserReadDisBatchService;
    private final GbDistributerPurchaseBatchSellerReceiveReturnBillService gbDistributerPurchaseBatchSellerReceiveReturnBillService;
    private final GbDistributerPurchaseBatchSellerFinishPurchaseGoodsService gbDistributerPurchaseBatchSellerFinishPurchaseGoodsService;
    private final GbDistributerPurchaseBatchJingjingBuyingGoodsService gbDistributerPurchaseBatchJingjingBuyingGoodsService;
    private final GbDistributerPurchaseBatchDisGoodsBatchQueryService gbDistributerPurchaseBatchDisGoodsBatchQueryService;
    private final GbDistributerPurchaseBatchSaveDisPurGoodsBatchService gbDistributerPurchaseBatchSaveDisPurGoodsBatchService;
    private final GbDistributerPurchaseBatchDeleteDisPurGoodsItemService gbDistributerPurchaseBatchDeleteDisPurGoodsItemService;
    private final GbDistributerPurchaseBatchReceiveGbBatchService gbDistributerPurchaseBatchReceiveGbBatchService;
    private final GbDistributerPurchaseBatchSaveDisPurGoodsBatchGbSupplierService gbDistributerPurchaseBatchSaveDisPurGoodsBatchGbSupplierService;
    private final GbDistributerPurchaseBatchSupplierEditBatchService gbDistributerPurchaseBatchSupplierEditBatchService;
    private final GbDistributerPurchaseBatchSupplierGetPrintBatchService gbDistributerPurchaseBatchSupplierGetPrintBatchService;


    @RequestMapping(value = "/finishPayPurchaseBatchGb", method = RequestMethod.POST)
    @ResponseBody
    public R finishPayPurchaseBatchGb(String ids, Integer gbDisId, String total, Integer supplierId, Integer userId) {
        gbDistributerPurchaseBatchFinishPayService.finishPayPurchaseBatchGb(ids, gbDisId, total, supplierId, userId);
        return R.ok();
    }


    @RequestMapping(value = "/disCheckUnPayBillsGb", method = RequestMethod.POST)
    @ResponseBody
    public R disCheckUnPayBillsGb(Integer disId, Integer supplierId) {
        Map<String, Object> map2 = gbDistributerPurchaseBatchDisCheckUnPayBillsService.buildUnPayBillsSummary(disId, supplierId);
        return R.ok().put("data", map2);
    }


    @RequestMapping(value = "/disGetPurchaseDetailType", method = RequestMethod.POST)
    @ResponseBody
    public R disGetPurchaseDetailType(Integer disId, String purUserIds, Integer type, Integer greatId,
                                      String startDate, String stopDate, String supplierIds) {
        Map<String, Object> mapR = gbDistributerPurchaseBatchDisPurchaseDetailTypeService.buildPurchaseDetailType(
                disId, purUserIds, type, greatId, startDate, stopDate, supplierIds);
        return R.ok().put("data", mapR);
    }





    @RequestMapping(value = "/disGetPurchaseDetaiTypeWithId", method = RequestMethod.POST)
    @ResponseBody
    public R disGetPurchaseDetaiTypeWithId(Integer disId, String purUserId, Integer type,
                                           String startDate, String stopDate, String supplierId) {
        Map<String, Object> mapUser = gbDistributerPurchaseBatchDisPurchaseDetaiTypeWithIdService
                .buildPurchaseDetaiTypeWithId(disId, purUserId, type, startDate, stopDate, supplierId);
        if (mapUser == null) {
            return R.error(-1, "没有数据");
        }
        return R.ok().put("data", mapUser);
    }




    @RequestMapping(value = "/supplierEditBatchGb/{batchId}")
    @ResponseBody
    public R supplierEditBatchGb(@PathVariable Integer batchId) {
        GbDistributerPurchaseBatchSupplierEditBatchService.SupplierEditBatchGbResult result =
                gbDistributerPurchaseBatchSupplierEditBatchService.supplierEditBatchGb(batchId);
        if (!result.isSuccess()) {
            return R.error(-1, result.getErrorMessage());
        }
        return R.ok().put("data", result.getEntity());
    }

    @RequestMapping(value = "/supplierGetPrintBatchGb/{batchId}")
    @ResponseBody
    public R supplierGetPrintBatchGb(@PathVariable Integer batchId) {
        List<GbDepartmentOrdersEntity> ordersEntities =
                gbDistributerPurchaseBatchSupplierGetPrintBatchService.listOrdersForSupplierPrint(batchId);
        return R.ok().put("data", ordersEntities);
    }




    @RequestMapping(value = "/sellerDistributerPurchaseBatchsGb", method = RequestMethod.POST)
    @ResponseBody
    public R sellerDistributerPurchaseBatchsGb(Integer disId, Integer supplierId) {
        Map<String, Object> mapR =
                gbDistributerPurchaseBatchSellerPurchaseBatchsGbService.buildSellerDistributerPurchaseBatchsGb(disId, supplierId);
        return R.ok().put("data", mapR);
    }

    @RequestMapping(value = "/disGetGbSupplierBillsWithStatus", method = RequestMethod.POST)
    @ResponseBody
    public R disGetGbSupplierBillsWithStatus(Integer supplierId, String status, Integer disId, String startDate, String stopDate) {
        List<GbDistributerPurchaseBatchEntity> batchEntities =
                gbDistributerPurchaseBatchSupplierBillsWithStatusService.queryBatches(
                        supplierId, status, disId, startDate, stopDate);
        return R.ok().put("data", batchEntities);
    }

    @RequestMapping(value = "/disGetGbSupplierBills", method = RequestMethod.POST)
    @ResponseBody
    public R disGetGbSupplierBills(Integer supplierId, Integer disId) {
        List<Map<String, Object>> result =
                gbDistributerPurchaseBatchGbSupplierBillsService.buildGbSupplierBills(supplierId, disId);
        return R.ok().put("data", result);
    }




    @RequestMapping(value = "/finishSharePurGoodsBatchReturn/{batchId}")
    @ResponseBody
    public R finishSharePurGoodsBatchReturn(@PathVariable Integer batchId) {
        gbDistributerPurchaseBatchFinishShareReturnService.finishSharePurGoodsBatchReturn(batchId);
        return R.ok();
    }



    @RequestMapping(value = "/sellUserReadDisBatchGb", method = RequestMethod.POST)
    @ResponseBody
    public R sellUserReadDisBatchGb(@RequestBody GbDistributerPurchaseBatchEntity batch) {
        GbDistributerPurchaseBatchEntity nxDistributerPurchaseBatchEntity =
                gbDistributerPurchaseBatchSellUserReadDisBatchService.sellUserReadDisBatchGb(batch);
        return R.ok().put("data", nxDistributerPurchaseBatchEntity);
    }

    @RequestMapping(value = "/sellerReceiveReturnBill")
    @ResponseBody
    public R sellerReceiveReturnBill(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {
        gbDistributerPurchaseBatchSellerReceiveReturnBillService.sellerReceiveReturnBill(batchEntity);
        return R.ok();
    }


    @RequestMapping(value = "/sellerFinishPurchaseGoodsBatchGb")
    @ResponseBody
    public R sellerFinishPurchaseGoodsBatchGb(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {
        gbDistributerPurchaseBatchSellerFinishPurchaseGoodsService.sellerFinishPurchaseGoodsBatchGb(batchEntity);
        return R.ok();
    }



    @RequestMapping(value = "/jingjingGetBuyingGoodsGb/{disId}")
    @ResponseBody
    public R jingjingGetBuyingGoodsGb(@PathVariable Integer disId) {
        Map<String, Object> map3 = gbDistributerPurchaseBatchJingjingBuyingGoodsService.buildBuyingGoodsGb(disId);
        return R.ok().put("data", map3);
    }


    /**
     * 批发商获取进货商品列表
     *
     * @param batchId
     * @return
     */
    @RequestMapping(value = "/getDisPurchaseGoodsBatchGb/{batchId}")
    @ResponseBody
    public R getDisPurchaseGoodsBatchGb(@PathVariable Integer batchId) {
        GbDistributerPurchaseBatchEntity entity = gbDistributerPurchaseBatchDisGoodsBatchQueryService.getBatchWithOrders(batchId);
        if (entity != null) {
            return R.ok().put("data", entity);
        } else {
            return R.error(-1, "没有订单");
        }
    }
    /**
     * 批发商获取进货商品列表
     *
     * @param batchId
     * @return
     */
    @RequestMapping(value = "/getDisPurchaseGoodsBatchDetail/{batchId}")
    @ResponseBody
    public R getDisPurchaseGoodsBatchDetail(@PathVariable Integer batchId) {
        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities =
                gbDistributerPurchaseBatchDisGoodsBatchQueryService.listBatchDetailGoodsTree(batchId);
        return R.ok().put("data", gbDistributerGoodsEntities);
    }



    /**
     * 采购员分享进货商品
     *
     * @param
     * @return ok  shareGbPurchaseGoodsStatus
     */
    @RequestMapping(value = "/saveDisPurGoodsBatchGb", method = RequestMethod.POST)
    @ResponseBody
    public R saveDisPurGoodsBatchGb(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {
        try {
            GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity =
                    gbDistributerPurchaseBatchSaveDisPurGoodsBatchService.saveDisPurGoodsBatchGb(batchEntity);
            return R.ok().put("data", gbDistributerPurchaseBatchEntity);
        } catch (Exception e) {
            log.warn("saveDisPurGoodsBatchGb failed", e);
            return R.error("保存失败：" + e.getMessage());
        }
    }


    /**
     * 删除订货批次->"采购商品"
     *
     * @param id 采购商品id
     * @return ok
     */
    @RequestMapping(value = "/deleteDisPurBatchGbItem/{id}")
    @ResponseBody
    public R deleteDisPurBatchGbItem(@PathVariable Integer id) {
        if (gbDistributerPurchaseBatchDeleteDisPurGoodsItemService.deleteDisPurBatchGbItem(id)) {
            return R.ok();
        }
        return R.error(-1, "请刷新数据");
    }


    @RequestMapping(value = "/receiveGbBatch/{id}")
    @ResponseBody
    public R receiveGbBatch(@PathVariable Integer id) {
        GbDistributerPurchaseBatchReceiveGbBatchService.Outcome outcome =
                gbDistributerPurchaseBatchReceiveGbBatchService.receiveGbBatch(id);
        switch (outcome) {
            case OK:
                return R.ok();
            case STATUS_CHANGED:
                return R.error(-1, "订单状态已经改变");
            case NO_PURCHASE_LINES:
                return R.error(-1, "ccc");
            case ORDER_NOT_WAIT_RECEIVE:
                return R.error(-1, "bbb");
            default:
                return R.error(-1, "ccc");
        }
    }



    @RequestMapping(value = "/saveDisPurGoodsBatchGbSupplier", method = RequestMethod.POST)
    @ResponseBody
    public R saveDisPurGoodsBatchGbSupplier(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {
        try {
            gbDistributerPurchaseBatchSaveDisPurGoodsBatchGbSupplierService.saveDisPurGoodsBatchGbSupplier(batchEntity);
            return R.ok();
        } catch (Exception e) {
            log.warn("saveDisPurGoodsBatchGbSupplier failed", e);
            return R.error("保存失败：" + e.getMessage());
        }
    }


}
