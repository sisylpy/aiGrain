package com.nongxinle.controller;

/**
 * @author lpy
 * @date 06-25 22:52
 */

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.GbTypeUtils.*;
import static com.nongxinle.utils.PinYin4jUtils.*;


@RestController
@RequestMapping("api/gbdistributerpurchasebatch")
public class GbDistributerPurchaseBatchController {
    @Autowired
    private GbDistributerPurchaseBatchService gbDPBService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDPGService;
    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;
    @Autowired
    private GbDepartmentService gbDepartmentService;
    @Autowired
    private GbDistributerService gbDistributerService;
    @Autowired
    private NxJrdhSupplierService nxJrdhSupplierService;
    @Autowired
    private GbDistributerGoodsService gbDistributerGoodsService;
    @Autowired
    private GbDistributerFatherGoodsService gbDistributerFatherGoodsService;

    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;

    @Autowired
    private GbDepartmentGoodsDailyService gbDepGoodsDailyService;

    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private NxJrdhSupplierService jrdhSupplierService;
    @Autowired
    private NxJrdhUserService nxJrdhUserService;
    @Autowired
    private NxGoodsService nxGoodsService;

    @Autowired
    private NxAliasService nxAliasService;
    @Autowired
    private NxStandardService nxStandardService;
    @Autowired
    private GbDistributerSupplierPaymentService gbDistributerSupplierPaymentService;



    @RequestMapping(value = "/finishPayPurchaseBatchGb", method = RequestMethod.POST)
    @ResponseBody
    public R finishPayPurchaseBatchGb(String ids, Integer gbDisId, String total, Integer supplierId, Integer userId) {


        GbDistributerSupplierPaymentEntity paymentEntity = new GbDistributerSupplierPaymentEntity();
        paymentEntity.setGbDspDistributerId(gbDisId);
        paymentEntity.setGbDspDate(formatWhatDay(0));
        paymentEntity.setGbDspPayFullTime(formatFullTime());
        paymentEntity.setGbDspPayTotal(total);
        paymentEntity.setGbDspSupplierId(supplierId);
        paymentEntity.setGbDspPayUserId(userId);
        paymentEntity.setGbDspStatus(0);
        gbDistributerSupplierPaymentService.save(paymentEntity);

        String[] split = ids.split(",");


        for (String id : split) {

            Map<String, Object> map = new HashMap<>();
            map.put("batchId", id);
            List<GbDistributerPurchaseGoodsEntity> distributerPurchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(map);
            for (GbDistributerPurchaseGoodsEntity purGoods : distributerPurchaseGoodsEntities) {
                purGoods.setGbDpgStatus(getGbPurchaseGoodsStatusStockFinish());
                gbDPGService.update(purGoods);

                Map<String, Object> mapGO = new HashMap<>();
                mapGO.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
                List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(mapGO);
                if (gbDepartmentOrdersEntities.size() > 0) {
                    for (GbDepartmentOrdersEntity gbDepartmentOrdersEntity : gbDepartmentOrdersEntities) {
//                    gbDepartmentOrdersEntity.setGbDoStatus(4);
                        gbDepartmentOrdersEntity.setGbDoBuyStatus(getGbOrderBuyStatusHavePayFinish());
                        gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);
                    }
                }
            }
            GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryObject(Integer.valueOf(id));
            batchEntity.setGbDpbFinishFullTime(formatFullTime());
            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDisUserFinishPay());
            batchEntity.setGbDpbGbSupplierPaymentId(paymentEntity.getGbDistributerSupplierPaymentId());
            gbDPBService.update(batchEntity);
        }
        return R.ok();
    }


    @RequestMapping(value = "/disCheckUnPayBillsGb", method = RequestMethod.POST)
    @ResponseBody
    public R disCheckUnPayBillsGb(Integer disId, Integer supplierId) {

        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        map.put("equalStatus", getGbDisPurchaseBatchDepUserReceiveFinish());
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatch(map);
        int i = gbDPBService.queryDisPurchaseBatchCount(map);
        Double decimal = 0.0;
        if (i > 0) {
            decimal = gbDPBService.querySupplierUnSettleSubtotal(map);
        }
        Map<String, Object> map2 = new HashMap<>();
        map2.put("arr", batchEntities);
        map2.put("total", new BigDecimal(decimal).setScale(1, BigDecimal.ROUND_HALF_UP).toString());

        return R.ok().put("data", map2);

    }



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
            Integer integer1 = gbDPGService.queryGbPurchaseGoodsCount(map);
            System.out.println("subslslslsl" + map);
            if (integer1 > 0) {
                subTotal = gbDPGService.queryPurchaseGoodsSubTotal(map);
            }


            List<GbDepartmentUserEntity> purUserList = gbDPGService.queryPurUserList(map);

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
                    Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
                    List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
                    Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);
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
            Integer integer1 = gbDPGService.queryGbPurchaseGoodsCount(map);
            System.out.println("subslslslsl" + map);
            if (integer1 > 0) {
                subTotal = gbDPGService.queryPurchaseGoodsSubTotal(map);
            }
            List<NxJrdhSupplierEntity> supplierEntities = gbDPGService.queryDisPurGoodsSupplierList(map);

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
                    Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
                    List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
                    Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);
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



    @RequestMapping(value = "/getPurchaseUserGoods", method = RequestMethod.POST)
    @ResponseBody
    public R getPurchaseUserGoods(String purUserId,  String startDate, String stopDate) {

        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("typeNotEqual", 9);
        queryMap.put("dayuStatus", 2);
        queryMap.put("startDate", startDate);
        queryMap.put("stopDate", stopDate);
        queryMap.put("purUserId", purUserId);
        System.out.println("pururrus" + queryMap);
        List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);

        return R.ok().put("data", goodsList);

    }


    @RequestMapping(value = "/disGetPurchaseDetaiTypeWithId", method = RequestMethod.POST)
    @ResponseBody
    public R disGetPurchaseDetaiTypeWithId(Integer disId, String purUserId, Integer type,
                                           String startDate, String stopDate, String supplierId) {


        if (type == 0) {

            Map<String, Object> mapUser = new HashMap<>();

            // 创建新的查询参数Map，避免参数污染
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("typeNotEqual", 9);
            queryMap.put("supplierBuy", -1);
            queryMap.put("dayuStatus", 2);
            queryMap.put("startDate", startDate);
            queryMap.put("stopDate", stopDate);
            queryMap.put("disId", disId);
            queryMap.put("purUserId", purUserId);
            queryMap.put("offset", 0);
            queryMap.put("limit", 100);
            System.out.println("mapppppppp" + queryMap);
            Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
            List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
            Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);

            mapUser.put("arr", goodsList);
            mapUser.put("count", integer);
            mapUser.put("purSubtotal", String.format("%.1f", subTotal1));

            Map<String, Object> mapT = new HashMap<>();
            mapT.put("startDate", startDate);
            mapT.put("stopDate", stopDate);
            mapT.put("disId", disId);
            mapT.put("purUserId", purUserId);
            mapT.put("xiaoyuSubtotal", 0);
            int count = gbDPGService.queryGbGoodsCount(mapT);
            double tuitotal = 0.0;
            if (count > 0) {
                tuitotal = gbDPGService.queryPurchaseGoodsSubTotal(mapT);
            }
            mapUser.put("tuiSubtotal", String.format("%.1f", tuitotal));
            return R.ok().put("data", mapUser);
        } else if (type == 1) {

            Map<String, Object> mapUser = new HashMap<>();
            // 创建新的查询参数Map，避免参数污染
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("typeNotEqual", 9);
            queryMap.put("supplierBuy", 1);
            queryMap.put("dayuStatus", 2);
            queryMap.put("startDate", startDate);
            queryMap.put("stopDate", stopDate);
            queryMap.put("disId", disId);
            queryMap.put("supplierId", supplierId);
            queryMap.put("offset", 0);
            queryMap.put("limit", 100);
            Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
            List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
            Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);
            mapUser.put("arr", goodsList);
            mapUser.put("count", integer);
            mapUser.put("purSubtotal", String.format("%.1f", subTotal1));

            Map<String, Object> mapT = new HashMap<>();
            mapT.put("startDate", startDate);
            mapT.put("stopDate", stopDate);
            mapT.put("disId", disId);
            mapT.put("supplierId", supplierId);
            mapT.put("xiaoyuSubtotal", 0);
            int count = gbDPGService.queryGbGoodsCount(mapT);
            double tuitotal = 0.0;
            if (count > 0) {
                tuitotal = gbDPGService.queryPurchaseGoodsSubTotal(mapT);
            }
            mapUser.put("tuiSubtotal", String.format("%.1f", tuitotal));

            return R.ok().put("data", mapUser);

        }
        return R.error(-1, "没有数据");
    }

    @RequestMapping(value = "/userGetDinghuoByDate", method = RequestMethod.POST)
    @ResponseBody
    public R userGetDinghuoByDate(Integer userId, Integer type, String date) {

        Map<String, Object> map = new HashMap<>();
        map.put("purUserId", userId);
        map.put("date", date);

        System.out.println("gbgbbgbgb");
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatch(map);
        return R.ok().put("data", batchEntities);
    }


    @RequestMapping(value = "/getPurchaserPurBill", method = RequestMethod.POST)
    @ResponseBody
    public R getPurchaserPurBill(Integer userId, String date) {
        System.out.println(date);
        Map<String, Object> map = new HashMap<>();
        map.put("purUserId", userId);
        map.put("date", date);
        List<GbDistributerPurchaseBatchEntity> entities = gbDPBService.queryDisPurchaseBatch(map);

        return R.ok().put("data", entities);
    }



    @RequestMapping(value = "/depGetUnSettleSupplierBills/{depId}")
    @ResponseBody
    public R depGetUnSettleSupplierBills(@PathVariable Integer depId) {
        Map<String, Object> map = new HashMap<>();
        map.put("purDepId", depId);
        map.put("equalStatus", 3);

        List<GbDistributerPurchaseBatchEntity> billEntityList = gbDPBService.queryDisPurchaseBatch(map);
        return R.ok().put("data", billEntityList);
    }


//        @RequestMapping(value = "/disGetUnSettleSupplierAccountBills/{supplierId}")
//        @ResponseBody
//        public R disGetUnSettleSupplierAccountBills (@PathVariable Integer supplierId){
//            Map<String, Object> map = new HashMap<>();
//            map.put("supplierId", supplierId);
//            map.put("equalStatus", 3);
//            List<GbDistributerPurchaseBatchEntity> billEntityList = gbDPBService.queryDisPurchaseBatch(map);
//            if (billEntityList.size() > 0) {
//                return R.ok().put("data", billEntityList);
//            } else {
//                return R.error(-1, "没有订单");
//            }
//        }

    @RequestMapping(value = "/supplierEditBatchGb/{batchId}")
    @ResponseBody
    public R supplierEditBatchGb(@PathVariable Integer batchId) {

        //todo
        Map<String, Object> mapIf = new HashMap<>();
        mapIf.put("batchId", batchId);
        mapIf.put("finishAmount", 0);
        Integer integer = gbDPGService.queryGbPurchaseGoodsCount(mapIf);
        if (integer > 0) {
            return R.error(-1, "已有收货");
        }
        GbDistributerPurchaseBatchEntity gbDisPurBatchEntity = gbDPBService.queryObject(batchId);
        if (gbDisPurBatchEntity.getGbDpbStatus() == 2) {
            gbDisPurBatchEntity.setGbDpbStatus(0);
            gbDPBService.update(gbDisPurBatchEntity);

            Map<String, Object> mapG = new HashMap<>();
            mapG.put("batchId", batchId);
            List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(mapG);
            if (purchaseGoodsEntities.size() > 0) {
                for (GbDistributerPurchaseGoodsEntity purchaseGoodsEntity : purchaseGoodsEntities) {
                    purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
                    purchaseGoodsEntity.setGbDpgOrdersFinishAmount(0);
                    purchaseGoodsEntity.setGbDpgOrdersWeightAmount(0);
                    gbDPGService.update(purchaseGoodsEntity);

                    Map<String, Object> map = new HashMap<>();
                    map.put("purGoodsId", purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
                    List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);

                    if (gbDepartmentOrdersEntities.size() > 0) {
                        for (GbDepartmentOrdersEntity ordersEntity : gbDepartmentOrdersEntities) {
//                            ordersEntity.setGbDoBuyStatus(GbTypeUtils.getGbOrderBuyStatusProcurement());
//                            ordersEntity.setGbDoStatus(GbTypeUtils.getGbOrderStatusNew());
                            gbDepartmentOrdersService.update(ordersEntity);
                        }
                    }
                }
            }
        }
        GbDistributerPurchaseBatchEntity entity = gbDPBService.queryBatchWithOrders(batchId);
        return R.ok().put("data", entity);
    }

    @RequestMapping(value = "/supplierGetPrintBatchGb/{batchId}")
    @ResponseBody
    public R supplierGetPrintBatchGb(@PathVariable Integer batchId) {
        Map<String, Object> map = new HashMap<>();

        System.out.println("supplierGetPrintBatchGbsupplierGetPrintBatchGb");
        map.put("batchId", batchId);
        List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(map);

        return R.ok().put("data", ordersEntities);
    }

    @RequestMapping(value = "/supplierPrintBatchGb/{batchId}")
    @ResponseBody
    public R supplierPrintBatchGb(@PathVariable Integer batchId) {
        GbDistributerPurchaseBatchEntity gbDisPurBatchEntity = gbDPBService.queryObject(batchId);
        if (gbDisPurBatchEntity.getGbDpbStatus() == 2) {
            gbDisPurBatchEntity.setGbDpbStatus(3);
            gbDPBService.update(gbDisPurBatchEntity);
        }
        GbDistributerPurchaseBatchEntity entity = gbDPBService.queryBatchWithOrders(batchId);
        return R.ok().put("data", entity);
    }

    @RequestMapping(value = "/getGbDepartmentPurBatch/{depFatherId}")
    @ResponseBody
    public R getGbDepartmentPurBatch(@PathVariable Integer depFatherId) {
        Map<String, Object> map = new HashMap<>();
        map.put("purDepId", depFatherId);
        map.put("month", formatWhatMonth(0));
        map.put("dayuStatus", 1);
        List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDisPurchaseBatch(map);
        Map<String, Object> map1 = new HashMap<>();
        map1.put("month", formatWhatMonth(0));
        map1.put("arr", purchaseBatch);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("purDepId", depFatherId);
        map2.put("month", getLastMonth());
        map2.put("dayuStatus", 1);

        List<GbDistributerPurchaseBatchEntity> purchaseBatch2 = gbDPBService.queryDisPurchaseBatch(map2);
        Map<String, Object> map3 = new HashMap<>();
        map3.put("month", getLastMonth());
        map3.put("arr", purchaseBatch2);

        Map<String, Object> map4 = new HashMap<>();
        map4.put("purDepId", depFatherId);
        map4.put("month", getLastTwoMonth());
        map4.put("dayuStatus", 1);
        List<GbDistributerPurchaseBatchEntity> purchaseBatch3 = gbDPBService.queryDisPurchaseBatch(map4);
        Map<String, Object> map5 = new HashMap<>();
        map5.put("month", getLastTwoMonth());
        map5.put("arr", purchaseBatch3);

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(map1);
        result.add(map3);
        result.add(map5);
        return R.ok().put("data", result);
    }




    @RequestMapping(value = "/sellerDistributerPurchaseBatchsGb", method = RequestMethod.POST)
    @ResponseBody
    public R sellerDistributerPurchaseBatchsGb(Integer disId, Integer supplierId) {

        //第一个月
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        map.put("month", formatWhatMonth(0));
        map.put("year", formatWhatYear(0));
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatchInfo(map);

        map.put("equalStatus", 3);
        map.put("notEqualPurchaseType", 9);
        Double unPayOrderDouble = 0.0; // 未结账订单
        Double unPayReturn = 0.0; // 未记账退货
        Double havePayOrderDouble = 0.0; // 已结账订单
        Double havePayReturn = 0.0; // 已结账退货

        //未结账订单
        Integer unPayCount = gbDPBService.queryDisPurchaseBatchCount(map);
        if (unPayCount > 0) {
            unPayOrderDouble = gbDPBService.querySupplierUnSettleSubtotal(map);
        }
        // //未结账退货
        map.put("notEqualPurchaseType", null);
        map.put("purchaseType", 9);
        Integer unPayTuihuoCount = gbDPBService.queryDisPurchaseBatchCount(map);
        if (unPayTuihuoCount > 0) {
            unPayReturn = gbDPBService.querySupplierUnSettleSubtotal(map);
        }
        //已结账订单
        map.put("equalStatus", 4);
        map.put("notEqualPurchaseType", 9);
        map.put("purchaseType", null);
        Integer havePayCount = gbDPBService.queryDisPurchaseBatchCount(map);
        if (havePayCount > 0) {
            havePayOrderDouble = gbDPBService.querySupplierUnSettleSubtotal(map);
        }

        // 已结账退货
        map.put("notEqualPurchaseType", null);
        map.put("purchaseType", 9);
        Integer havePayTuihuoCount = gbDPBService.queryDisPurchaseBatchCount(map);
        if (havePayTuihuoCount > 0) {
            havePayReturn = gbDPBService.querySupplierUnSettleSubtotal(map);
        }

        //打印日志，查看查询条件
        System.out.println("========== sellerDistributerPurchaseBatchsGb 查询条件 ==========");
        System.out.println("disId: " + disId + ", supplierId: " + supplierId);
        System.out.println("当前月份: " + formatWhatMonth(0) + ", 当前年份: " + formatWhatYear(0));
        System.out.println("未结账订单 - equalStatus=3, notEqualPurchaseType=9, unPayCount: " + unPayCount + ", unPayOrderDouble: " + unPayOrderDouble);
        System.out.println("未结账退货 - equalStatus=3, purchaseType=9, unPayTuihuoCount: " + unPayTuihuoCount + ", unPayReturn: " + unPayReturn);
        System.out.println("已结账订单 - equalStatus=4, notEqualPurchaseType=9, havePayCount: " + havePayCount + ", havePayOrderDouble: " + havePayOrderDouble);
        System.out.println("已结账退货 - equalStatus=4, purchaseType=9, havePayTuihuoCount: " + havePayTuihuoCount + ", havePayReturn: " + havePayReturn);
        System.out.println("================================================================");

        //计算结果:
        //订单数量
        int billCount = unPayCount + havePayCount;
        //订单金额
        double billTotal = unPayOrderDouble + havePayOrderDouble;

        //已结订单
        int havePayCountTotal = havePayCount + havePayTuihuoCount;

        //已结金额
        double havePayTotl = havePayOrderDouble - havePayReturn;

        //实际未接金额
        double actPayTotal = unPayOrderDouble - unPayReturn;

        //实际订单数量
        int actPayCountTotal = unPayCount + unPayTuihuoCount;

        Map<String, Object> mapDataOne = new HashMap<>();
        mapDataOne.put("billCount", billCount);
        mapDataOne.put("billTotal", String.format("%.1f", billTotal));

        mapDataOne.put("unPayCount", unPayCount);
        mapDataOne.put("unPayTotal", String.format("%.1f", unPayOrderDouble));

        mapDataOne.put("havePayCount", havePayCountTotal);
        mapDataOne.put("havePayTotal", String.format("%.1f", havePayTotl));

        mapDataOne.put("returnBillCount", unPayTuihuoCount);
        mapDataOne.put("returnPayTotal", String.format("%.1f", unPayReturn));

        mapDataOne.put("actBillCount", actPayCountTotal);
        mapDataOne.put("actPayTotal", String.format("%.1f", actPayTotal));


        //第二个月
        Map<String, Object> map1 = new HashMap<>();
        map1.put("disId", disId);
        map1.put("supplierId", supplierId);
        map1.put("month", getLastMonth());
        map1.put("year", formatWhatYear(0));
        System.out.println("99999999yueuueeuue" + map1);
        List<GbDistributerPurchaseBatchEntity> batchEntities1 = gbDPBService.queryDisPurchaseBatchInfo(map1);

        map1.put("equalStatus", 3);
        map1.put("notEqualPurchaseType", 9);
        Double unPayOrderDoubleOne = 0.0; // 未结账订单
        Double unPayReturnOne = 0.0; // 未记账退货
        Double havePayOrderDoubleOne = 0.0; // 已结账订单
        Double havePayReturnOne = 0.0; // 已结账退货
        //未结账订单
        System.out.println("未结账订单====map1map1map1====" + map1);
        Integer unPayCountOne = gbDPBService.queryDisPurchaseBatchCount(map1);
        if (unPayCountOne > 0) {
            unPayOrderDoubleOne = gbDPBService.querySupplierUnSettleSubtotal(map1);
        }
        // //未结账退货
        map1.put("notEqualPurchaseType", null);
        map1.put("purchaseType", 9);
        System.out.println("未结账退货==========map1map1map1====" + map1);
        Integer unPayTuihuoCountOne = gbDPBService.queryDisPurchaseBatchCount(map1);
        if (unPayTuihuoCountOne > 0) {
            unPayReturnOne = gbDPBService.querySupplierUnSettleSubtotal(map1);
        }
        //已结账订单
        map1.put("equalStatus", 4);
        map1.put("notEqualPurchaseType", 9);
        map1.put("purchaseType", null);
        System.out.println("已结账订单已结账订单======" + map1);
        Integer havePayCountOne = gbDPBService.queryDisPurchaseBatchCount(map1);
        if (havePayCountOne > 0) {
            havePayOrderDoubleOne = gbDPBService.querySupplierUnSettleSubtotal(map1);
        }

        // 已结账退货
        map1.put("notEqualPurchaseType", null);
        map1.put("purchaseType", 9);
        System.out.println("已结账退货======" + map1);
        Integer havePayTuihuoCountOne = gbDPBService.queryDisPurchaseBatchCount(map1);
        if (havePayTuihuoCountOne > 0) {
            havePayReturnOne = gbDPBService.querySupplierUnSettleSubtotal(map1);
        }


        //计算结果:
        //订单数量
        int billCountOne = unPayCountOne + havePayCountOne;
        //订单金额
        double billTotalOne = unPayOrderDoubleOne + havePayOrderDoubleOne;


        //已结订单
        int havePayCountTotalOne = havePayCountOne + havePayTuihuoCountOne;
        System.out.println("havePayTuihuoCountOnehavePayCountOne=" + havePayCountOne + "havePayTuihuoCountOne=" + havePayTuihuoCountOne);

        //已结金额
        double havePayTotlOne = havePayOrderDoubleOne - havePayReturnOne;
        System.out.println("havePayTotlOnehavePayOrderDoubleOne=" + havePayOrderDoubleOne + "havePayReturnOne=" + havePayReturnOne);

        //实际未接金额
        double actPayTotalOne = unPayOrderDoubleOne - unPayReturnOne;
        //实际订单数量
        int actPayCountTotalOne = unPayCountOne + unPayTuihuoCountOne;

        Map<String, Object> mapDataTwo = new HashMap<>();
        mapDataTwo.put("billCount", billCountOne);
        mapDataTwo.put("billTotal", String.format("%.1f", billTotalOne));

        mapDataTwo.put("unPayCount", unPayCountOne);
        mapDataTwo.put("unPayTotal", String.format("%.1f", unPayOrderDoubleOne));

        mapDataTwo.put("havePayCount", havePayCountTotalOne);
        mapDataTwo.put("havePayTotal", String.format("%.1f", havePayTotlOne));

        mapDataTwo.put("returnBillCount", unPayTuihuoCountOne);
        mapDataTwo.put("returnPayTotal", String.format("%.1f", unPayReturnOne));

        mapDataTwo.put("actBillCount", actPayCountTotalOne);
        mapDataTwo.put("actPayTotal", String.format("%.1f", actPayTotalOne));


        //第三个月
        Map<String, Object> map2 = new HashMap<>();
        map2.put("disId", disId);
        map2.put("supplierId", supplierId);
        map2.put("month", getLastTwoMonth());
        map2.put("year", formatWhatYear(0));
        List<GbDistributerPurchaseBatchEntity> batchEntities2 = gbDPBService.queryDisPurchaseBatchInfo(map2);

        map2.put("equalStatus", 3);
        map2.put("notEqualPurchaseType", 9);
        Double unPayOrderDoubleTwo = 0.0; // 未结账订单
        Double unPayReturnTwo = 0.0; // 未记账退货
        Double havePayOrderDoubleTwo = 0.0; // 已结账订单
        Double havePayReturnTwo = 0.0; // 已结账退货
        //未结账订单
        System.out.println("未结账订单====map1map1map1====" + map2);
        Integer unPayCountTwo = gbDPBService.queryDisPurchaseBatchCount(map2);
        if (unPayCountTwo > 0) {
            unPayOrderDoubleTwo = gbDPBService.querySupplierUnSettleSubtotal(map2);
        }
        // //未结账退货
        map2.put("notEqualPurchaseType", null);
        map2.put("purchaseType", 9);
        System.out.println("未结账退货==========map1map1map1====" + map2);
        Integer unPayTuihuoCountTwo = gbDPBService.queryDisPurchaseBatchCount(map2);
        if (unPayTuihuoCountTwo > 0) {
            unPayReturnTwo = gbDPBService.querySupplierUnSettleSubtotal(map2);
        }
        //已结账订单
        map2.put("equalStatus", 4);
        map2.put("notEqualPurchaseType", 9);
        map2.put("purchaseType", null);
        System.out.println("已结账订单已结账订单======" + map2);
        Integer havePayCountTwo = gbDPBService.queryDisPurchaseBatchCount(map2);
        if (havePayCountTwo > 0) {
            havePayOrderDoubleTwo = gbDPBService.querySupplierUnSettleSubtotal(map2);
        }

        // 已结账退货
        map2.put("notEqualPurchaseType", null);
        map2.put("purchaseType", 9);
        System.out.println("已结账退货======" + map2);
        Integer havePayTuihuoCountTwo = gbDPBService.queryDisPurchaseBatchCount(map2);
        if (havePayTuihuoCountTwo > 0) {
            havePayReturnTwo = gbDPBService.querySupplierUnSettleSubtotal(map2);
        }


        //计算结果:
        //订单数量
        int billCountTwo = unPayCountTwo + havePayCountTwo;
        //订单金额
        double billTotalTwo = unPayOrderDoubleTwo + havePayOrderDoubleTwo;

        //已结订单
        int havePayCountTotalTwo = havePayCountTwo + havePayTuihuoCountTwo;
        System.out.println("havePayTuihuoCountOnehavePayCountOne=" + havePayCountOne + "havePayTuihuoCountOne=" + havePayTuihuoCountOne);

        //已结金额
        double havePayTotlTwo = havePayOrderDoubleTwo - havePayReturnTwo;

        //实际未接金额
        double actPayTotalTwo = unPayOrderDoubleTwo - unPayReturnTwo;
        //实际订单数量
        int actPayCountTotalTwo = unPayCountTwo + unPayTuihuoCountTwo;

        Map<String, Object> mapDataThree = new HashMap<>();
        mapDataThree.put("billCount", billCountTwo);
        mapDataThree.put("billTotal", String.format("%.1f", billTotalTwo));

        mapDataThree.put("unPayCount", unPayCountTwo);
        mapDataThree.put("unPayTotal", String.format("%.1f", unPayOrderDoubleTwo));

        mapDataThree.put("havePayCount", havePayCountTotalTwo);
        mapDataThree.put("havePayTotal", String.format("%.1f", havePayTotlTwo));

        mapDataThree.put("returnBillCount", unPayTuihuoCountTwo);
        mapDataThree.put("returnPayTotal", String.format("%.1f", unPayReturnTwo));

        mapDataThree.put("actPayTotal", String.format("%.1f", actPayTotalTwo));
        mapDataThree.put("actBillCount", actPayCountTotalTwo);


        Map<String, Object> map3 = new HashMap<>();
        map3.put("arr", batchEntities);
        map3.put("month", formatWhatMonth(0));
        map3.put("itemData", mapDataOne);
        Map<String, Object> map4 = new HashMap<>();
        map4.put("arr", batchEntities1);
        map4.put("itemData", mapDataTwo);
        map4.put("month", getLastMonth());
        Map<String, Object> map5 = new HashMap<>();
        map5.put("arr", batchEntities2);
        map5.put("itemData", mapDataThree);
        map5.put("month", getLastTwoMonth());

        List<Map<String, Object>> resultData = new ArrayList<>();
        resultData.add(map3);
        resultData.add(map4);
        resultData.add(map5);


        Map<String, Object> mapR = new HashMap<>();
        mapR.put("arr", resultData);
        mapR.put("disInfo", gbDistributerService.getById(disId));

        NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.getById(supplierId);
        mapR.put("supplierInfo", supplierEntity);

        return R.ok().put("data", mapR);

    }

    @RequestMapping(value = "/disGetGbSupplierBillsWithStatus", method = RequestMethod.POST)
    @ResponseBody
    public R disGetGbSupplierBillsWithStatus(Integer supplierId, String status, Integer disId, String startDate, String stopDate) {

        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        if (status.equals("all")) {
            map.put("dayuStatus", 2);
            map.put("notEqualPurchaseType", 9);
        } else if (status.equals("allUnPay")) {
            map.put("equalStatus", 3);
        } else if (status.equals("havePayed")) {
            map.put("equalStatus", 4);
        } else if (status.equals("unPayBills")) {
            map.put("equalStatus", 3);
            map.put("notEqualPurchaseType", 9);
        } else if (status.equals("unPayReturnBills")) {
            map.put("equalStatus", 3);
            map.put("notEqualPurchaseType", null);
            map.put("purchaseType", 9);
        }

        map.put("startDate", startDate);
        map.put("stopDate", stopDate);
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatchInfo(map);

        return R.ok().put("data", batchEntities);
    }

    @RequestMapping(value = "/disGetGbSupplierBills", method = RequestMethod.POST)
    @ResponseBody
    public R disGetGbSupplierBills(Integer supplierId, Integer disId) {

        BigDecimal listTotal = new BigDecimal("0.0");
        double unSettleSubtotal = 0.0;

        //第一个月账单
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        map.put("month", formatWhatMonth(0));
        map.put("dayuStatus", 1);
        String totalDec1 = "0";
        List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDisPurchaseBatch(map);
        BigDecimal bigDecimal = new BigDecimal(purchaseBatch.size());
        listTotal = listTotal.add(bigDecimal); //账单数量

        Map<String, Object> map41 = new HashMap<>();
        map41.put("disId", disId);
        map41.put("supplierId", supplierId);
        map41.put("month", formatWhatMonth(0));
        map41.put("dayuStatus", 1);
        map41.put("status", 4);
        System.out.println("41mapapapap" + map41);
        Integer integer = gbDPBService.queryDisPurchaseBatchCount(map41);
        if (integer > 0) {
            Double total1 = gbDPBService.querySupplierUnSettleSubtotal(map41);
            unSettleSubtotal = unSettleSubtotal + total1; //未结账款总额
            totalDec1 = String.format("%.2f", total1);
        }
        Map<String, Object> map1 = new HashMap<>();
        map1.put("month", formatWhatMonth(0));
        map1.put("arr", purchaseBatch);
        map1.put("total", totalDec1);


        //第二个月账单
        Map<String, Object> map2 = new HashMap<>();
        map2.put("disId", disId);
        map2.put("supplierId", supplierId);
        map2.put("month", getLastMonth());
        map2.put("dayuStatus", 1);
        List<GbDistributerPurchaseBatchEntity> purchaseBatch2 = gbDPBService.queryDisPurchaseBatch(map2);
        BigDecimal bigDecimal2 = new BigDecimal(purchaseBatch2.size());
        listTotal = listTotal.add(bigDecimal2); //账单数量

        String totalDec2 = "0";
        Map<String, Object> map42 = new HashMap<>();
        map42.put("disId", disId);
        map42.put("supplierId", supplierId);
        map42.put("month", getLastMonth());
        map42.put("dayuStatus", 1);
        map42.put("status", 4);
        Integer integer1 = gbDPBService.queryDisPurchaseBatchCount(map42);
        if (integer1 > 0) {
            Double total2 = gbDPBService.querySupplierUnSettleSubtotal(map42);
            unSettleSubtotal = unSettleSubtotal + total2; //未结账款总额
            totalDec2 = String.format("%.2f", total2);
        }

        Map<String, Object> map3 = new HashMap<>();
        map3.put("month", getLastMonth());
        map3.put("arr", purchaseBatch2);
        map3.put("total", totalDec2);

        //第三个月账单
        Map<String, Object> map4 = new HashMap<>();
        map4.put("disId", disId);
        map4.put("supplierId", supplierId);
        map4.put("month", getLastTwoMonth());
        map4.put("dayuStatus", 1);
        List<GbDistributerPurchaseBatchEntity> purchaseBatch3 = gbDPBService.queryDisPurchaseBatch(map4);
        BigDecimal bigDecimal3 = new BigDecimal(purchaseBatch3.size());
        listTotal = listTotal.add(bigDecimal3);

        String totalDec3 = "0";
        Map<String, Object> map43 = new HashMap<>();
        map43.put("disId", disId);
        map43.put("supplierId", supplierId);
        map43.put("month", getLastTwoMonth());
        map43.put("dayuStatus", 1);
        map43.put("status", 4);
        Integer integer2 = gbDPBService.queryDisPurchaseBatchCount(map43);

        if (integer2 > 0) {
            Double total3 = gbDPBService.querySupplierUnSettleSubtotal(map43);
            unSettleSubtotal = unSettleSubtotal + total3; //未结账款总额
            totalDec3 = String.format("%.2f", total3);
        }

        Map<String, Object> map5 = new HashMap<>();
        map5.put("month", getLastTwoMonth());
        map5.put("arr", purchaseBatch3);
        map5.put("total", totalDec3);

        Map<String, Object> map111 = new HashMap<>();
        map111.put("unSettleSubtotal", unSettleSubtotal);
        map111.put("listTotal", listTotal);

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(map1);
        result.add(map3);
        result.add(map5);
        result.add(map111);
        return R.ok().put("data", result);

    }




    @RequestMapping(value = "/finishSharePurGoodsBatchReturn/{batchId}")
    @ResponseBody
    public R finishSharePurGoodsBatchReturn(@PathVariable Integer batchId) {
        System.out.println("baucicic" + batchId);
        GbDistributerPurchaseBatchEntity batch = gbDPBService.queryObject(batchId);
        Map<String, Object> mapP = new HashMap<>();
        mapP.put("batchId", batchId);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(mapP);

        for (GbDistributerPurchaseGoodsEntity purGoods : purchaseGoodsEntities) {

            purGoods.setGbDpgStatus(getGbPurchaseGoodsStatusWaitReceive());
            gbDPGService.update(purGoods);

            Integer gbDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", gbDistributerPurchaseGoodsId);
            List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            for (GbDepartmentOrdersEntity orders : ordersEntities) {
                Integer gbDoDgsrReturnId = orders.getGbDoDgsrReturnId();
                GbDepartmentGoodsStockReduceEntity reduceEntity = gbDepartmentStockReduceService.queryObject(gbDoDgsrReturnId);
//                reduceEntity.setGbDgsrStatus(2);
                gbDepartmentStockReduceService.update(reduceEntity);
                orders.setGbDoStatus(getGbOrderStatusReceived()); //wancheng
                System.out.println("bactpey");
                if (batch.getGbDpbPayType() == 0) {
                    orders.setGbDoBuyStatus(getGbOrderBuyStatusHavePayFinish());
                } else {
//                    orders.setGbDoBuyStatus(getGbOrderBuyStatusUnPayFinish());
                }
                gbDepartmentOrdersService.update(orders);
            }
        }

        batch.setGbDpbFinishFullTime(formatFullTime());
        if (batch.getGbDpbPayType() == 0) {
            batch.setGbDpbStatus(4); //如果是现金 status == 4 完成结账状态
        } else {
            batch.setGbDpbStatus(2); //如果是记账，status == 2， 开票完成后，status == 3
            Integer gbDpbSupplierId = batch.getGbDpbSupplierId();
            NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.getById(gbDpbSupplierId);
            Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
            System.out.println("dafdafas" + supplierEntity);
            //todo
//            if (nxJrdhsUserId == null) {
//                System.out.println("dafafads" + nxJrdhsUserId);
//                supplierEntity.setNxJrdhsUserId(batch.getGbDpbSellUserId());
//                nxJrdhSupplierService.update(supplierEntity);
//            }
        }
        gbDPBService.update(batch);

        return R.ok();

    }






    @RequestMapping(value = "/sellerGetPurchaseBatch", method = RequestMethod.POST)
    @ResponseBody
    public R sellerGetPurchaseBatch(Integer userId, Integer disId) {
        System.out.println("selleieid" + userId);
        System.out.println("dididididididi" + disId);
        Map<String, Object> map = new HashMap<>();
        map.put("sellerId", userId);
        map.put("month", formatWhatMonth(0));
        System.out.println("paumondthhthth" + map);
        List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDisPurchaseBatch(map);

        Map<String, Object> map1 = new HashMap<>();
        map1.put("month", formatWhatMonth(0));
        map1.put("arr", purchaseBatch);

        //lastMonth
        Map<String, Object> map2 = new HashMap<>();
        map2.put("sellerId", userId);
        map2.put("month", getLastMonth());
        List<GbDistributerPurchaseBatchEntity> purchaseBatch1 = gbDPBService.queryDisPurchaseBatch(map2);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("month", getLastMonth());
        map3.put("arr", purchaseBatch1);

        //lastTwoMonth
        Map<String, Object> map4 = new HashMap<>();
        map4.put("sellerId", userId);
        map4.put("month", getLastTwoMonth());
        List<GbDistributerPurchaseBatchEntity> purchaseBatch2 = gbDPBService.queryDisPurchaseBatch(map4);

        Map<String, Object> map5 = new HashMap<>();
        map5.put("month", getLastTwoMonth());
        map5.put("arr", purchaseBatch2);

        Map<String, Object> map6 = new HashMap<>();
        map6.put("sellerId", userId);
        map6.put("status", 4);
        map6.put("dayuStatus", 1);
        Integer integer = gbDPBService.queryDisPurchaseBatchCount(map6);
        BigDecimal subtotal = new BigDecimal(0);
        if (integer > 0) {
            Double aDouble = gbDPBService.querySupplierUnSettleSubtotal(map6);
            subtotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(map1);
        result.add(map3);
        result.add(map5);
        Map<String, Object> map111 = new HashMap<>();
        map111.put("month", result);
        map111.put("unSettle", subtotal);
        map111.put("gbDis", gbDistributerService.getById(disId));
        return R.ok().put("data", map111);
    }



    @RequestMapping(value = "/sellUserOpenDisBatchGb/{batchId}")
    @ResponseBody
    public R sellUserOpenDisBatchGb(@PathVariable Integer batchId) {
        GbDistributerPurchaseBatchEntity batch = gbDPBService.queryObject(batchId);
        batch.setGbDpbStatus(0);
        gbDPBService.update(batch);
        Integer gbDistributerPurchaseBatchId = batch.getGbDistributerPurchaseBatchId();
        GbDistributerPurchaseBatchEntity nxDistributerPurchaseBatchEntity = gbDPBService.queryBatchWithOrders(gbDistributerPurchaseBatchId);
        return R.ok().put("data", nxDistributerPurchaseBatchEntity);
    }

    @RequestMapping(value = "/sellUserReadDisBatchGb", method = RequestMethod.POST)
    @ResponseBody
    public R sellUserReadDisBatchGb(@RequestBody GbDistributerPurchaseBatchEntity batch) {

        System.out.println("sellUserReadDisBatchGbsellUserReadDisBatchGbsellUserReadDisBatchGb");
//        batch.setGbDpbStatus(getGbDisPurchaseBatchHaveRead());
        gbDPBService.update(batch);
        Integer gbDpbSupplierId = batch.getGbDpbSupplierId();
        Integer batchId = batch.getGbDistributerPurchaseBatchId();

        for (GbDistributerPurchaseGoodsEntity purchaseGoodsEntity : batch.getGbDPGEntities()) {
            purchaseGoodsEntity.setGbDpgPurchaseNxSupplierId(batch.getGbDpbSupplierId());
            Integer gbDpgDisGoodsId = purchaseGoodsEntity.getGbDpgDisGoodsId();
            GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
            Map<String, Object> mapItem = new HashMap<>();
            mapItem.put("disGoodsId", purchaseGoodsEntity.getGbDpgDisGoodsId());
            mapItem.put("supplierId", gbDpbSupplierId);
            mapItem.put("dayuStatus", 2);
            GbDistributerPurchaseGoodsEntity lastItem = gbDPGService.queryPurchaseGoodsLastItem(mapItem);
            if (lastItem != null) {
                //give price
                purchaseGoodsEntity.setGbDpgBuyPrice(lastItem.getGbDpgBuyPrice());
                BigDecimal buySubtotal = new BigDecimal(0);
                BigDecimal buyWeight = new BigDecimal(0);
                List<GbDepartmentOrdersEntity> ordersEntities = purchaseGoodsEntity.getGbDistributerGoodsEntity().getGbDepartmentOrdersEntities();
                if (ordersEntities.size() > 0) {
                    for (GbDepartmentOrdersEntity ordersEntity : ordersEntities) {
                        //give price
                        ordersEntity.setGbDoPrice(lastItem.getGbDpgBuyPrice());
                        System.out.println("namdmmdmdmmd" + gbDistributerGoodsEntity.getGbDgGoodsName() + "orderstnad" + ordersEntity.getGbDoStandard());
                        if (gbDistributerGoodsEntity.getGbDgGoodsStandardname().equals(ordersEntity.getGbDoStandard())) {
                            BigDecimal orderSubtotal = new BigDecimal(lastItem.getGbDpgBuyPrice()).multiply(new BigDecimal(ordersEntity.getGbDoQuantity()));
                            buySubtotal = buySubtotal.add(orderSubtotal);
                            buyWeight = buyWeight.add(new BigDecimal(ordersEntity.getGbDoQuantity()));
                            ordersEntity.setGbDoWeight(ordersEntity.getGbDoQuantity());
                            ordersEntity.setGbDoSubtotal(orderSubtotal.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                        }
                        gbDepartmentOrdersService.update(ordersEntity);
                    }
                    if (purchaseGoodsEntity.getGbDpgStandard().equals(gbDistributerGoodsEntity.getGbDgGoodsStandardname())) {
                        purchaseGoodsEntity.setGbDpgBuyQuantity(buyWeight.toString());
                        purchaseGoodsEntity.setGbDpgBuySubtotal(buySubtotal.toString());
                    }

                }
            }

            gbDPGService.update(purchaseGoodsEntity);
        }
        GbDistributerPurchaseBatchEntity nxDistributerPurchaseBatchEntity = gbDPBService.queryBatchWithOrders(batchId);
        return R.ok().put("data", nxDistributerPurchaseBatchEntity);
    }

    @RequestMapping(value = "/sellerReceiveReturnBill")
    @ResponseBody
    public R sellerReceiveReturnBill(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {

        BigDecimal tuihuo = new BigDecimal(0);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = batchEntity.getGbDPGEntities();
        for (GbDistributerPurchaseGoodsEntity purGoods : purchaseGoodsEntityList) {
            String gbDpgBuySubtotal = purGoods.getGbDpgBuySubtotal();
            tuihuo = tuihuo.add(new BigDecimal(gbDpgBuySubtotal));
            GbDistributerPurchaseGoodsEntity updatePurGoods = gbDPGService.queryObject(purGoods.getGbDistributerPurchaseGoodsId());
            if(batchEntity.getGbDpbPayType() == 0){
                updatePurGoods.setGbDpgStatus(getGbPurchaseGoodsStatusPayFinish());
            }else{
                updatePurGoods.setGbDpgStatus(getGbPurchaseGoodsStatusStockFinish());
            }

            updatePurGoods.setGbDpgStockFinishDate(formatWhatDay(0));
            gbDPGService.update(updatePurGoods);

            List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = purGoods.getGbDistributerGoodsEntity().getGbDepartmentOrdersEntities();
            if (gbDepartmentOrdersEntities.size() > 0) {

                for (GbDepartmentOrdersEntity ordersEntity : gbDepartmentOrdersEntities) {
                    System.out.println("orddidid" + ordersEntity.getGbDepartmentOrdersId());
                    GbDepartmentOrdersEntity updateOrders = gbDepartmentOrdersService.queryObject(ordersEntity.getGbDepartmentOrdersId());
                    updateOrders.setGbDoStatus(4);
                    updateOrders.setGbDoBuyStatus(6);
                    gbDepartmentOrdersService.update(updateOrders);
                    Integer gbDoDgsrReturnId = updateOrders.getGbDoDgsrReturnId();
                    GbDepartmentGoodsStockReduceEntity reduceEntity = gbDepartmentStockReduceService.queryObject(gbDoDgsrReturnId);
//                    reduceEntity.setGbDgsrStatus(0);
                    gbDepartmentStockReduceService.update(reduceEntity);
                }
            }
        }

        batchEntity.setGbDpbSubtotal(tuihuo.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
        if(batchEntity.getGbDpbPayType() == 0){
            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDisUserFinishPay());
        }else{
            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDepUserReceiveFinish());
        }

        batchEntity.setGbDpbFinishFullTime(formatFullTime());
        gbDPBService.update(batchEntity);
        return R.ok();
    }


    @RequestMapping(value = "/sellerFinishPurchaseGoodsBatchGb")
    @ResponseBody
    public R sellerFinishPurchaseGoodsBatchGb(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {

        Integer gbDpbPayType = batchEntity.getGbDpbPayType();

        List<GbDistributerPurchaseGoodsEntity> nxDPBEntities = batchEntity.getGbDPGEntities();
        for (GbDistributerPurchaseGoodsEntity purGoods : nxDPBEntities) {
            purGoods.setGbDpgPayType(batchEntity.getGbDpbPayType());
            purGoods.setGbDpgSupplierFinishDate(formatWhatDay(0));
            gbDPGService.update(purGoods);
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
            List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            if (gbDepartmentOrdersEntities.size() > 0) {
                for (GbDepartmentOrdersEntity gbDepartmentOrdersEntity : gbDepartmentOrdersEntities) {
                    gbDepartmentOrdersEntity.setGbDoBuyStatus(4);
                    gbDepartmentOrdersEntity.setGbDoStatus(2);
                    gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);
                }
            }
        }
        batchEntity.setGbDpbStatus(2);
        batchEntity.setGbDpbSellerReplyFullTime(formatFullTime());
        gbDPBService.update(batchEntity);

        return R.ok();
    }


    @RequestMapping(value = "/updatePurchaseBatch")
    @ResponseBody
    public R updatePurchaseBatch(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {

        gbDPBService.update(batchEntity);
        return R.ok();
    }



    @RequestMapping(value = "/purUserGetBuyingGoods/{userId}")
    @ResponseBody
    public R purUserGetBuyingGoodsWithNx(@PathVariable Integer userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("purUserId", userId);
        map.put("status", 2);
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatch(map);
        return R.ok().put("data", batchEntities);
    }


    @RequestMapping(value = "/jingjingGetBuyingGoodsGb/{disId}")
    @ResponseBody
    public R jingjingGetBuyingGoodsGb(@PathVariable Integer disId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("status", 3);
        System.out.println("abbdbdbd" + map);
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatch(map);


        Map<String, Object> map1 = new HashMap<>();
        map1.put("disId", disId);
        map1.put("status", 3);
        map1.put("equalBuyStatus", 0);
        int purCount = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        map1.put("equalBuyStatus", null);
        map1.put("dayuBuyStatus", 0);
        map1.put("dayuStatus", -2);
        int purCountOne = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        Map<String, Object> map3 = new HashMap<>();

        map3.put("arr", batchEntities);
        map3.put("orderAmount", purCount);
        map3.put("wxAmount", purCountOne);
        map3.put("disInfo", gbDistributerService.queryDistributerInfo(disId));
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
        System.out.println("bababbtidid" + batchId);
        GbDistributerPurchaseBatchEntity entity = gbDPBService.queryBatchWithOrders(batchId);
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
//    @RequestMapping(value = "/getDisPurchaseGoodsBatchDetail/{batchId}")
//    @ResponseBody
//    public R getDisPurchaseGoodsBatchDetail(@PathVariable Integer batchId) {
//
//        Map<String, Object> queryMap = new HashMap<>();
//        queryMap.put("batchId", batchId);
//        System.out.println("mapmcansn" + queryMap);
//        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
//        return R.ok().put("data", gbDistributerGoodsEntities);
//    }



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
            batchEntity.setGbDpbDate(formatWhatDay(0));
            batchEntity.setGbDpbHour(formatWhatHour(0));
            batchEntity.setGbDpbMinute(formatWhatMinute(0));
            batchEntity.setGbDpbTime(formatWhatTime(0));
            batchEntity.setGbDpbPurchaseMonth(formatWhatMonth(0));
            batchEntity.setGbDpbPurchaseWeek(getWeek(0));
            batchEntity.setGbDpbPurchaseYear(formatWhatYear(0));
            batchEntity.setGbDpbPurchaseFullTime(formatWhatYearDayTime(0));
//            batchEntity.setGbDpbStatus(GbTypeUtils.getGbDisPurchaseBatchUnRead());
            gbDPBService.save(batchEntity);
            System.out.println("savvbabba" + batchEntity);

            for (GbDistributerPurchaseGoodsEntity gbPurGoods : batchEntity.getGbDPGEntities()) {
                Integer gbDpgDisGoodsId = gbPurGoods.getGbDpgDisGoodsId();
                GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
                List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities = gbPurGoods.getGbDistributerGoodsEntity().getGbDepartmentOrdersEntities();
                List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();
                BigDecimal buytotal = new BigDecimal(0);
                for (GbDepartmentOrdersEntity orders : nxDepartmentOrdersEntities) {
                    Boolean hasChoice = orders.getIsNotice();
                    if (hasChoice) {
//                        orders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
                        buytotal = buytotal.add(new BigDecimal(orders.getGbDoQuantity()));
                        gbDepartmentOrdersService.update(orders);
                    } else {
                        unChoiceOrderList.add(orders);
                    }
                }

                Integer newLength = nxDepartmentOrdersEntities.size() - unChoiceOrderList.size();
                GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(gbPurGoods.getGbDistributerPurchaseGoodsId());

                purchaseGoodsEntity.setGbDpgOrdersAmount(newLength);
                purchaseGoodsEntity.setGbDpgPurchaseType(2);
                purchaseGoodsEntity.setGbDpgBatchId(batchEntity.getGbDistributerPurchaseBatchId());
                purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
                purchaseGoodsEntity.setGbDpgPurchaseDate(formatWhatDay(0));
                purchaseGoodsEntity.setGbDpgPurchaseMonth(formatWhatMonth(0));
                purchaseGoodsEntity.setGbDpgPurchaseYear(formatWhatYear(0));
                purchaseGoodsEntity.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
                purchaseGoodsEntity.setGbDpgPurchaseWeek(getWeek(0));
                purchaseGoodsEntity.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
                purchaseGoodsEntity.setGbDpgTime(formatWhatTime(0));
                purchaseGoodsEntity.setGbDpgQuantity(buytotal.toString());
                purchaseGoodsEntity.setGbDpgDisGoodsGrandId(gbDistributerGoodsEntity.getGbDgDfgGoodsGrandId());
                purchaseGoodsEntity.setGbDpgDisGoodsGreatId(gbDistributerGoodsEntity.getGbDgDfgGoodsGreatId());

                gbDPGService.update(purchaseGoodsEntity);

                //查询 supplierr 是不是 nxDis

                if (unChoiceOrderList.size() > 0) {
                    GbDistributerPurchaseGoodsEntity disGoods = new GbDistributerPurchaseGoodsEntity();
                    disGoods.setGbDpgDistributerId(batchEntity.getGbDpbDistributerId());
                    disGoods.setGbDpgPayType(0);
                    disGoods.setGbDpgDisGoodsGrandId(purchaseGoodsEntity.getGbDpgDisGoodsGrandId());
                    disGoods.setGbDpgDisGoodsGreatId(purchaseGoodsEntity.getGbDpgDisGoodsGreatId());

                    disGoods.setGbDpgDisGoodsFatherId(unChoiceOrderList.get(0).getGbDoDisGoodsFatherId());
                    disGoods.setGbDpgDisGoodsId(unChoiceOrderList.get(0).getGbDoDisGoodsId());
                    disGoods.setGbDpgDistributerId(unChoiceOrderList.get(0).getGbDoDistributerId());
                    disGoods.setGbDpgApplyDate(formatWhatDay(0));
                    disGoods.setGbDpgStatus(0);
                    disGoods.setGbDpgTime(formatWhatTime(0));
                    disGoods.setGbDpgOrdersAmount(unChoiceOrderList.size());
                    disGoods.setGbDpgOrdersFinishAmount(0);
                    disGoods.setGbDpgOrdersWeightAmount(0);
                    disGoods.setGbDpgOrdersBillAmount(0);
                    disGoods.setGbDpgIsCheck(0);
                    disGoods.setGbDpgPurchaseDepartmentId(unChoiceOrderList.get(0).getGbDoToDepartmentId());
                    disGoods.setGbDpgPurchaseType(2);
                    disGoods.setGbDpgPurchaseNxSupplierId(-1);
                    disGoods.setGbDpgDisGoodsGrandId(gbDistributerGoodsEntity.getGbDgDfgGoodsGrandId());
                    disGoods.setGbDpgDisGoodsGreatId(gbDistributerGoodsEntity.getGbDgDfgGoodsGreatId());

                    gbDPGService.save(disGoods);
                    BigDecimal unPurQuantity = new BigDecimal(0);
                    for (GbDepartmentOrdersEntity unChoiceOrder : unChoiceOrderList) {
                        Integer gbDistributerPurchaseGoodsId = disGoods.getGbDistributerPurchaseGoodsId();
                        unChoiceOrder.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);
                        gbDepartmentOrdersService.update(unChoiceOrder);
                        BigDecimal orderQuantity = new BigDecimal(unChoiceOrder.getGbDoQuantity());
                        unPurQuantity = unPurQuantity.add(orderQuantity).setScale(1, BigDecimal.ROUND_HALF_UP);

                    }
                    disGoods.setGbDpgQuantity(unPurQuantity.toString());
                    disGoods.setGbDpgStandard(unChoiceOrderList.get(0).getGbDoStandard());
                    gbDPGService.update(disGoods);
                }
            }

            GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryBatchWithOrders(batchEntity.getGbDistributerPurchaseBatchId());
            return R.ok().put("data", gbDistributerPurchaseBatchEntity);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error("保存失败：" + e.getMessage());
        }
    }


    @RequestMapping(value = "/saveDisPurGoodsBatchGbSx", method = RequestMethod.POST)
    @ResponseBody
    public R saveDisPurGoodsBatchGbSx(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {
        batchEntity.setGbDpbDate(formatWhatDay(0));
        batchEntity.setGbDpbHour(formatWhatHour(0));
        batchEntity.setGbDpbMinute(formatWhatMinute(0));
        batchEntity.setGbDpbTime(formatWhatTime(0));
        batchEntity.setGbDpbPurchaseMonth(formatWhatMonth(0));
        batchEntity.setGbDpbPurchaseWeek(getWeek(0));
        batchEntity.setGbDpbPurchaseYear(formatWhatYear(0));
        batchEntity.setGbDpbPurchaseFullTime(formatWhatYearDayTime(0));
//        batchEntity.setGbDpbStatus(getGbDisPurchaseBatchUnSend());
        gbDPBService.save(batchEntity);

        for (GbDistributerPurchaseGoodsEntity gbPurGoods : batchEntity.getGbDPGEntities()) {
            Integer gbDpgDisGoodsId = gbPurGoods.getGbDpgDisGoodsId();
            GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
            List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities = gbPurGoods.getGbDistributerGoodsEntity().getGbDepartmentOrdersEntities();

            List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();
            BigDecimal buytotal = new BigDecimal(0);
            for (GbDepartmentOrdersEntity orders : nxDepartmentOrdersEntities) {
                Boolean hasChoice = orders.getIsNotice();
                if (hasChoice) {
//                    orders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
                    buytotal = buytotal.add(new BigDecimal(orders.getGbDoQuantity()));
                    gbDepartmentOrdersService.update(orders);
                } else {
                    unChoiceOrderList.add(orders);
                }
            }

            Integer newLength = nxDepartmentOrdersEntities.size() - unChoiceOrderList.size();
            gbPurGoods.setGbDpgOrdersAmount(newLength);
            gbPurGoods.setGbDpgPurchaseType(2);
            gbPurGoods.setGbDpgBatchId(batchEntity.getGbDistributerPurchaseBatchId());
            gbPurGoods.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
            gbPurGoods.setGbDpgPurchaseDate(formatWhatDay(0));
            gbPurGoods.setGbDpgPurchaseMonth(formatWhatMonth(0));
            gbPurGoods.setGbDpgPurchaseYear(formatWhatYear(0));
            gbPurGoods.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
            gbPurGoods.setGbDpgPurchaseWeek(getWeek(0));
            gbPurGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
            gbPurGoods.setGbDpgTime(formatWhatTime(0));
            gbPurGoods.setGbDpgQuantity(buytotal.toString());
            gbPurGoods.setGbDpgDisGoodsGrandId(gbDistributerGoodsEntity.getGbDgDfgGoodsGrandId());
            gbPurGoods.setGbDpgDisGoodsGreatId(gbDistributerGoodsEntity.getGbDgDfgGoodsGreatId());
            gbDPGService.update(gbPurGoods);

            if (unChoiceOrderList.size() > 0) {
                GbDistributerPurchaseGoodsEntity disGoods = new GbDistributerPurchaseGoodsEntity();
                disGoods.setGbDpgDistributerId(batchEntity.getGbDpbDistributerId());
                disGoods.setGbDpgPayType(0);
                disGoods.setGbDpgDisGoodsGrandId(gbPurGoods.getGbDpgDisGoodsGrandId());
                disGoods.setGbDpgDisGoodsGreatId(gbPurGoods.getGbDpgDisGoodsGreatId());

                disGoods.setGbDpgDisGoodsFatherId(unChoiceOrderList.get(0).getGbDoDisGoodsFatherId());
                disGoods.setGbDpgDisGoodsId(unChoiceOrderList.get(0).getGbDoDisGoodsId());
                disGoods.setGbDpgDistributerId(unChoiceOrderList.get(0).getGbDoDistributerId());
                disGoods.setGbDpgApplyDate(formatWhatDay(0));
                disGoods.setGbDpgStatus(0);
                disGoods.setGbDpgTime(formatWhatTime(0));
                disGoods.setGbDpgOrdersAmount(unChoiceOrderList.size());
                disGoods.setGbDpgOrdersFinishAmount(0);
                disGoods.setGbDpgOrdersWeightAmount(0);
                disGoods.setGbDpgOrdersBillAmount(0);
                disGoods.setGbDpgPurchaseWeek(getWeek(0));
                disGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
                disGoods.setGbDpgIsCheck(0);
                disGoods.setGbDpgPurchaseDepartmentId(unChoiceOrderList.get(0).getGbDoToDepartmentId());
                disGoods.setGbDpgPurchaseType(2);
                disGoods.setGbDpgPurchaseNxSupplierId(-1);
                disGoods.setGbDpgDisGoodsGrandId(gbDistributerGoodsEntity.getGbDgDfgGoodsGrandId());
                disGoods.setGbDpgDisGoodsGreatId(gbDistributerGoodsEntity.getGbDgDfgGoodsGreatId());

                gbDPGService.save(disGoods);
                BigDecimal unPurQuantity = new BigDecimal(0);
                for (GbDepartmentOrdersEntity unChoiceOrder : unChoiceOrderList) {
                    Integer gbDistributerPurchaseGoodsId = disGoods.getGbDistributerPurchaseGoodsId();
                    unChoiceOrder.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);
                    gbDepartmentOrdersService.update(unChoiceOrder);
                    BigDecimal orderQuantity = new BigDecimal(unChoiceOrder.getGbDoQuantity());
                    unPurQuantity = unPurQuantity.add(orderQuantity).setScale(1, BigDecimal.ROUND_HALF_UP);

                }
                disGoods.setGbDpgQuantity(unPurQuantity.toString());
                disGoods.setGbDpgStandard(unChoiceOrderList.get(0).getGbDoStandard());
                gbDPGService.update(disGoods);
            }
        }

        GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryBatchWithOrders(batchEntity.getGbDistributerPurchaseBatchId());
        return R.ok().put("data", gbDistributerPurchaseBatchEntity);
    }

}
