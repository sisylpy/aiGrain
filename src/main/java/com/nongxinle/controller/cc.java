package com.nongxinle.controller;

/**
 * @author lpy
 * @date 06-25 22:52
 */

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.github.wxpay.sdk.WXPay;
import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.nongxinle.utils.CommonUtils.generateBillTradeNo;
import static com.nongxinle.utils.DateUtils.*;
import static com.nongxinle.utils.GbTypeUtils.*;
import static com.nongxinle.utils.NxDistributerTypeUtils.*;
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
    private GbDepartmentBillService gbDepartmentBillService;
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
    private GbDistributerGoodsPriceService gbDistributerGoodsPriceService;
    @Autowired
    private GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    @Autowired
    private GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    @Autowired
    private GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    @Autowired
    private GbDistributerGoodsPriceService goodsPriceService;
    @Autowired
    private GbDepartmentGoodsDailyService gbDepGoodsDailyService;
    @Autowired
    private NxDepartmentBillService nxDepartmentBillService;
    @Autowired
    private NxDepartmentOrdersService nxDepartmentOrdersService;
    @Autowired
    private NxDistributerPurchaseGoodsService nxDistributerPurchaseGoodsService;
    @Autowired
    private NxDistributerPurchaseBatchService nxDPBService;
    @Autowired
    private GbDistributerPayListService payListService;
    @Autowired
    private NxDistributerGbDistributerService nxDisGbDisService;
    @Autowired
    private NxDistributerService nxDistributerService;
    @Autowired
    private NxDistributerPayListService nxPayListService;
    @Autowired
    private NxDistributerGoodsService dgService;
    @Autowired
    private GbDepartmentUserService gbDepartmentUserService;
    @Autowired
    private NxGbDistibuterUserCouponService nxGbDistibuterUserCouponService;
    @Autowired
    private NxDistributerCouponService nxDistributerCouponService;
    @Autowired
    private NxDepartmentOrdersService depOrdersService;
    @Autowired
    private NxJrdhSupplierService jrdhSupplierService;
    @Autowired
    private NxJrdhUserService nxJrdhUserService;
    @Autowired
    private NxDistributerPurchaseGoodsService nxDisPurchaseGoodsService;
    @Autowired
    private NxDistributerFatherGoodsService dgfService;
    @Autowired
    private NxGoodsService nxGoodsService;
    @Autowired
    private NxDistributerStandardService dsService;
    @Autowired
    private NxDistributerAliasService disAliasService;
    @Autowired
    private NxAliasService nxAliasService;
    @Autowired
    private NxStandardService nxStandardService;
    @Autowired
    private GbDistributerSupplierPaymentService gbDistributerSupplierPaymentService;


    @RequestMapping(value = "/nxDisFinishPurchaseGoodsBatchGb")
    @ResponseBody
    public R nxDisFinishPurchaseGoodsBatchGb(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {

        List<GbDistributerPurchaseGoodsEntity> nxDPBEntities = batchEntity.getGbDPGEntities();
        int orderTotal = 0;
        for (GbDistributerPurchaseGoodsEntity purGoods : nxDPBEntities) {
            purGoods.setGbDpgStatus(getGbPurchaseGoodsStatusStockFinish());
            purGoods.setGbDpgPurchaseNxDistributerId(batchEntity.getGbDpbDistributerId());
            gbDPGService.update(purGoods);

            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
            List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            if (gbDepartmentOrdersEntities.size() > 0) {
                for (GbDepartmentOrdersEntity gbDepartmentOrdersEntity : gbDepartmentOrdersEntities) {
                    orderTotal = orderTotal + 1;

                    Integer gbDoNxDepartmentOrderId = gbDepartmentOrdersEntity.getGbDoNxDepartmentOrderId();

                    NxDepartmentOrdersEntity orders = nxDepartmentOrdersService.queryObject(gbDoNxDepartmentOrderId);
                    orders.setNxDoStatus(2);
                    orders.setNxDoPurchaseStatus(getNxDisPurchaseGoodsFinishPay());
                    nxDepartmentOrdersService.update(orders);

//                    gbDepartmentOrdersEntity.setGbDoStatus(getGbOrderStatusReceived());
//                    gbDepartmentOrdersEntity.setGbDoBuyStatus(getGbOrderBuyStatusUnPayFinish());
                    gbDepartmentOrdersEntity.setGbDoArriveWeeksYear(getWeekOfYear(0));
                    gbDepartmentOrdersEntity.setGbDoArriveWhatDay(getWeek(0));
                    gbDepartmentOrdersEntity.setGbDoArriveOnlyDate(formatWhatDate(0));
                    gbDepartmentOrdersEntity.setGbDoArriveDate(formatWhatDay(0));
                    gbDepartmentOrdersEntity.setGbDoWeight(orders.getNxDoWeight());
                    gbDepartmentOrdersEntity.setGbDoSubtotal(orders.getNxDoSubtotal());
                    gbDepartmentOrdersEntity.setGbDoPrice(orders.getNxDoPrice());
                    gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);


                    Integer doDisGoodsId = orders.getNxDoDisGoodsId();
                    NxDistributerGoodsEntity distributerGoodsEntity = dgService.queryObject(doDisGoodsId);
                    BigDecimal weight = new BigDecimal(distributerGoodsEntity.getNxDgOutTotalWeight());
                    BigDecimal orderWeight = new BigDecimal(orders.getNxDoWeight());
                    BigDecimal add = weight.add(orderWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
                    distributerGoodsEntity.setNxDgOutTotalWeight(add.toString());
                    dgService.update(distributerGoodsEntity);

                    Integer nxDoPurchaseGoodsId = orders.getNxDoPurchaseGoodsId();
                    if (nxDoPurchaseGoodsId != -1) {
                        NxDistributerPurchaseGoodsEntity purchaseGoodsEntity = nxDistributerPurchaseGoodsService.queryObject(nxDoPurchaseGoodsId);
                        Integer nxDpgOrdersAmount = purchaseGoodsEntity.getNxDpgOrdersAmount();
                        Integer nxDpgFinishAmount = purchaseGoodsEntity.getNxDpgFinishAmount();
                        if (nxDpgOrdersAmount - nxDpgFinishAmount == 1) {
                            purchaseGoodsEntity.setNxDpgFinishAmount(nxDpgOrdersAmount);
                            purchaseGoodsEntity.setNxDpgStatus(getNxDisPurchaseGoodsFinishBuy());
                        } else {
                            purchaseGoodsEntity.setNxDpgFinishAmount(nxDpgFinishAmount + 1);
                        }

                        nxDistributerPurchaseGoodsService.update(purchaseGoodsEntity);
                    }
                }

            }
        }

        batchEntity.setGbDpbStatus(getGbDisPurchaseBatchSellerReply());
        batchEntity.setGbDpbSellerReplyFullTime(formatFullTime());
        gbDPBService.update(batchEntity);
        return R.ok();
    }


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

    /**
     * 设置商品业务数据
     */
    private void setGoodsBusinessData(GbDistributerGoodsEntity goods, Map<String, Object> statsMap) {
        // 制作统计
        statsMap.put("equalType", getGbDepartGoodsStockReduceTypeProduce());
        Integer produceCount = gbDepartmentStockReduceService.queryReduceTypeCount(statsMap);
        if (produceCount > 0) {
            double produceWeight = gbDepartmentStockReduceService.queryReduceProduceWeightTotal(statsMap);
            double produceValue = gbDepartmentStockReduceService.queryReduceProduceTotal(statsMap);
            goods.setGoodsProduceWeightTotalString(String.format("%.1f", produceWeight));
            goods.setGoodsProduceTotalString(String.format("%.1f", produceValue));
        } else {
            goods.setGoodsProduceWeightTotalString("0.0");
            goods.setGoodsProduceTotalString("0.0");
        }

        // 损耗统计
        statsMap.put("equalType", getGbDepartGoodsStockReduceTypeLoss());
        Integer lossCount = gbDepartmentStockReduceService.queryReduceTypeCount(statsMap);
        if (lossCount > 0) {
            double lossWeight = gbDepartmentStockReduceService.queryReduceLossWeightTotal(statsMap);
            double lossValue = gbDepartmentStockReduceService.queryReduceLossTotal(statsMap);
            goods.setGoodsLossWeightTotalString(String.format("%.1f", lossWeight));
            goods.setGoodsLossTotalString(String.format("%.1f", lossValue));
        } else {
            goods.setGoodsLossWeightTotalString("0.0");
            goods.setGoodsLossTotalString("0.0");
        }

        // 废弃统计
        statsMap.put("equalType", getGbDepartGoodsStockReduceTypeWaste());
        Integer wasteCount = gbDepartmentStockReduceService.queryReduceTypeCount(statsMap);
        if (wasteCount > 0) {
            double wasteWeight = gbDepartmentStockReduceService.queryReduceWasteWeightTotal(statsMap);
            double wasteValue = gbDepartmentStockReduceService.queryReduceWasteTotal(statsMap);
            goods.setGoodsWasteWeightTotalString(String.format("%.1f", wasteWeight));
            goods.setGoodsWasteTotalString(String.format("%.1f", wasteValue));
        } else {
            goods.setGoodsWasteWeightTotalString("0.0");
            goods.setGoodsWasteTotalString("0.0");
        }

        // 退货统计
        statsMap.put("equalType", getGbDepartGoodsStockReduceTypeReturn());
        Integer returnCount = gbDepartmentStockReduceService.queryReduceTypeCount(statsMap);
        if (returnCount > 0) {
            double returnWeight = gbDepartmentStockReduceService.queryReduceReturnWeightTotal(statsMap);
            double returnValue = gbDepartmentStockReduceService.queryReduceReturnTotal(statsMap);
            goods.setGoodsReturnWeightTotalString(String.format("%.1f", returnWeight));
            goods.setGoodsReturnTotalString(String.format("%.1f", returnValue));
        } else {
            goods.setGoodsReturnWeightTotalString("0.0");
            goods.setGoodsReturnTotalString("0.0");
        }

        // 设置平均单价
        double costWeight = Double.parseDouble(goods.getGoodsCostWeightTotalString());
        double costValue = Double.parseDouble(goods.getGoodsCostTotalString());
        if (costWeight > 0) {
            double perPrice = costValue / costWeight;
            Map<String, Object> goodsData = new HashMap<>();
            goodsData.put("perPrice", String.format("%.1f", perPrice));
            goodsData.put("purCount", goods.getGoodsPurTotalCount());
            goods.setGoodsData(goodsData);
        }
    }

    private Map<String, Object> getGoodsChartData(Integer goodsId, String startDate, String stopDate, Integer
            supplierId, Integer purUserId, Integer greatId) {
        Map<String, Object> chartData = new HashMap<>();
        List<String> dateList = new ArrayList<>();
        List<Map<String, Object>> dateDataList = new ArrayList<>();

        // 计算日期范围
        int howManyDaysInPeriod = getHowManyDaysInPeriod(stopDate, startDate);

        if (howManyDaysInPeriod > 0) {
            // 遍历每一天
            for (int i = 0; i < howManyDaysInPeriod + 1; i++) {
                System.out.println("homamnanamaama " + howManyDaysInPeriod);
                String whichDay = "";
                if (i == 0) {
                    whichDay = startDate;
                } else {
                    whichDay = afterWhatDay(startDate, i);
                }

                // 1. 添加日期
                String substring = whichDay.substring(8, 10);
                System.out.println("homamnanamaama " + substring);

                dateList.add(substring);


                // 3. 每日采购数据
                Map<String, Object> mapDay = new HashMap<>();
                mapDay.put("date", whichDay);
                mapDay.put("disGoodsId", goodsId);
                if (purUserId != -1) {
                    mapDay.put("purUserId", purUserId);
                } else if (supplierId != -1) {
                    mapDay.put("supplierId", supplierId);
                } else if (greatId != -1) {
                    mapDay.put("disGoodsGreatId", greatId);
                }
                mapDay.put("typeNotEqual", 9);
                System.out.println("kakakanaknakaknakkanka" + mapDay);

                int purCountDay = gbDPGService.queryGbPurchaseGoodsCount(mapDay);
                Double subTotal = gbDPGService.queryPurchaseGoodsSubTotal(mapDay);

                if (purCountDay == 1) {
                    // 重新验证实际数据查询，确保条件一致
                    String price = gbDPGService.queryPurchaseGoodsPrice(mapDay);
                    String weight = gbDPGService.queryPurchaseGoodsWeight(mapDay);

                    // 如果查询结果为空，说明条件不一致，按0处理
                    if (price == null || weight == null) {
                        Map<String, Object> mapDayValue = new HashMap<>();
                        mapDayValue.put("dayPrice", "0.0");
                        mapDayValue.put("dayWeight", "0.0");
                        dateDataList.add(mapDayValue);
                    } else {
                        // 添加null检查，防止数据库字段值为null
                        if (price.trim().isEmpty()) {
                            price = "0";
                        }
                        if (weight.trim().isEmpty()) {
                            weight = "0";
                        }

                        Map<String, Object> mapDayValue = new HashMap<>();
                        mapDayValue.put("daySubtotal", new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                        mapDayValue.put("dayPrice", new BigDecimal(price).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                        mapDayValue.put("dayWeight", new BigDecimal(weight).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                        dateDataList.add(mapDayValue);
                    }
                } else if (purCountDay > 1) {
                    Double weightTotal = gbDPGService.queryPurchaseGoodsWeightTotal(mapDay);

                    double v1 = subTotal / weightTotal;
                    double v2 = weightTotal / purCountDay;

                    Map<String, Object> mapDayValue = new HashMap<>();
                    mapDayValue.put("daySubtotal", new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                    mapDayValue.put("dayPrice", new BigDecimal(v1).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                    mapDayValue.put("dayWeight", new BigDecimal(v2).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                    dateDataList.add(mapDayValue);
                } else {
                    Map<String, Object> mapDayValue = new HashMap<>();
                    mapDayValue.put("dayPrice", 0);
                    mapDayValue.put("dayWeight", 0);
                    mapDayValue.put("daySubtotal", 0);
                    dateDataList.add(mapDayValue);
                }
            }
        } else {
            // 单日情况
            String substring = startDate.substring(8, 10);
            dateList.add(substring);

            Map<String, Object> mapDay = new HashMap<>();
            mapDay.put("date", startDate);
            mapDay.put("disGoodsId", goodsId);
            if (purUserId != -1) {
                mapDay.put("purUserId", purUserId);
            } else if (supplierId != -1) {
                mapDay.put("supplierId", supplierId);
            } else if (greatId != -1) {
                mapDay.put("disGoodsGreatId", greatId);
            }
            mapDay.put("typeNotEqual", 9);

            int purCountDay = gbDPGService.queryGbPurchaseGoodsCount(mapDay);
            Double subTotal = gbDPGService.queryPurchaseGoodsSubTotal(mapDay);

            if (purCountDay == 1) {
                // 重新验证实际数据查询，确保条件一致
                String price = gbDPGService.queryPurchaseGoodsPrice(mapDay);
                String weight = gbDPGService.queryPurchaseGoodsWeight(mapDay);

                // 如果查询结果为空，说明条件不一致，按0处理
                if (price == null || weight == null) {
                    Map<String, Object> mapDayValue = new HashMap<>();
                    mapDayValue.put("dayPrice", "0.0");
                    mapDayValue.put("dayWeight", "0.0");
                    mapDayValue.put("daySubtotal", "0.0");
                    dateDataList.add(mapDayValue);
                } else {
                    // 添加null检查，防止数据库字段值为null
                    if (price.trim().isEmpty()) {
                        price = "0";
                    }
                    if (weight.trim().isEmpty()) {
                        weight = "0";
                    }

                    Map<String, Object> mapDayValue = new HashMap<>();
                    mapDayValue.put("daySubtotal", new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                    mapDayValue.put("dayPrice", new BigDecimal(price).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                    mapDayValue.put("dayWeight", new BigDecimal(weight).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                    dateDataList.add(mapDayValue);
                }
            } else if (purCountDay > 1) {
                Double weightTotal = gbDPGService.queryPurchaseGoodsWeightTotal(mapDay);

                double v1 = subTotal / weightTotal;
                double v2 = weightTotal / purCountDay;

                Map<String, Object> mapDayValue = new HashMap<>();
                mapDayValue.put("daySubtotal", new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                mapDayValue.put("dayPrice", new BigDecimal(v1).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                mapDayValue.put("dayWeight", new BigDecimal(v2).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                dateDataList.add(mapDayValue);
            } else {
                Map<String, Object> mapDayValue = new HashMap<>();
                mapDayValue.put("daySubtotal", 0);
                mapDayValue.put("dayPrice", 0);
                mapDayValue.put("dayWeight", 0);
                dateDataList.add(mapDayValue);
            }


        }

        // 构建完整的图表数据，保持与原接口一致的结构
        chartData.put("dayValue", dateDataList);
        chartData.put("dateList", dateList);

        return chartData;
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


    @RequestMapping(value = "/getPaymentDetail", method = RequestMethod.POST)
    @ResponseBody
    public R getPaymentDetail(Integer payId, Integer nxDisId) {
        Map<String, Object> mapR = new HashMap<>();
        List<GbDepartmentBillEntity> billEntities = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("payId", payId);
        if (nxDisId == -1) {
            List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDisPurchaseBatch(map);
            mapR.put("arr", purchaseBatch);
        } else {
            billEntities = gbDepartmentBillService.queryBillsByParamsGb(map);
            mapR.put("arr", billEntities);
        }

        return R.ok().put("data", mapR);

    }


    @RequestMapping(value = "/getDistributerAccounting/{disId}")
    @ResponseBody
    public R getDistributerAccounting(@PathVariable Integer disId) {

        BigDecimal mapResult = new BigDecimal(0);

        Map<String, Object> map2 = new HashMap<>();
        map2.put("disId", disId);
        map2.put("equalStatus", 3);
        List<GbDepartmentEntity> departmentEntities = gbDPBService.queryDistributerAccountingData(map2);
        if (departmentEntities.size() > 0) {
            for (GbDepartmentEntity dep : departmentEntities) {
                Integer gbDepartmentId = dep.getGbDepartmentId();
                Map<String, Object> map3 = new HashMap<>();
                map3.put("purDepId", gbDepartmentId);
                map3.put("equalStatus", 3);
                map3.put("subDayu", 1);
                Double aDouble = gbDPBService.querySupplierUnSettleSubtotal(map3);
                BigDecimal result = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);

                //tuihuo
                Map<String, Object> map333 = new HashMap<>();
                map333.put("purDepId", gbDepartmentId);
                map333.put("equalStatus", 3);
                map333.put("subDayu", -1);
                int returnlist = gbDPBService.queryReturnList(map333);
                System.out.println(returnlist + "reuturlist");
                if (returnlist > 0) {
                    Map<String, Object> map33 = new HashMap<>();
                    map33.put("purDepId", gbDepartmentId);
                    map33.put("equalStatus", 3);
                    map33.put("subDayu", -1);
                    Double aDouble3 = gbDPBService.querySupplierUnSettleSubtotal(map33);
                    BigDecimal returnResult = new BigDecimal(aDouble3);
                    BigDecimal subtract = result.subtract(returnResult.abs()).setScale(2, BigDecimal.ROUND_HALF_UP);
                    dep.setUnPayTotal(subtract.toString());
                    mapResult = mapResult.add(subtract);
                    System.out.println(mapResult + "mapresitutuutu");
                } else {
                    dep.setUnPayTotal(result.toString());
                    mapResult = mapResult.add(result);
                    System.out.println(mapResult + "mapresitutuutu5555555");
                }
            }

//            Map<String, Object> map1 = new HashMap<>();
//            map1.put("disId", disId);
//            map1.put("equalStatus", 3);
//            Double aDouble = gbDPBService.querySupplierUnSettleSubtotal(map1);
//            BigDecimal mapResult = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);

            Map<String, Object> map = new HashMap<>();
            map.put("deps", departmentEntities);
            map.put("subtotal", mapResult);
            return R.ok().put("data", map);
        } else {
            return R.error(-1, "没有数据");
        }
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

    @RequestMapping(value = "/purchaserEditBatch/{batchId}")
    @ResponseBody
    public R purchaserEditBatch(@PathVariable Integer batchId) {
        GbDistributerPurchaseBatchEntity gbDisPurBatchEntity = gbDPBService.queryObject(batchId);
        if (gbDisPurBatchEntity.getGbDpbStatus() == 2) {

            Map<String, Object> mapG = new HashMap<>();
            mapG.put("batchId", batchId);
            List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(mapG);

            if (purchaseGoodsEntities.size() > 0) {
                for (GbDistributerPurchaseGoodsEntity purGoods : purchaseGoodsEntities) {
                    purGoods.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
                    gbDPGService.update(purGoods);

                    //
                    Integer purchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
                    Map<String, Object> map = new HashMap<>();
                    map.put("purGoodsId", purchaseGoodsId);
                    List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(map);
                    if (ordersEntities.size() > 0) {
                        for (GbDepartmentOrdersEntity order : ordersEntities) {
                            order.setGbDoStatus(getGbOrderBuyStatusProcurement());
                            gbDepartmentOrdersService.update(order);
                        }
                    }
                }


                //
            }

            gbDisPurBatchEntity.setGbDpbStatus(1);
            gbDPBService.update(gbDisPurBatchEntity);
        }
        return R.ok();
    }

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
                            ordersEntity.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
                            ordersEntity.setGbDoStatus(getGbOrderStatusNew());
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


    @RequestMapping(value = "/getPurchaserPurBatch/{userId}")
    @ResponseBody
    public R getPurchaserPurBatch(@PathVariable Integer userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("purUserId", userId);
        map.put("dayuStatus", 1);
        map.put("status", 5);

        List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDisPurchaseBatch(map);
//        Map<String, Object> map1 = new HashMap<>();
//        map1.put("month", formatWhatMonth(0));
//        map1.put("arr", purchaseBatch);
//
//        Map<String, Object> map2 = new HashMap<>();
//        map2.put("purUserId", userId);
//        map2.put("month", getLastMonth());
//        map2.put("dayuStatus", 1);
//
//        List<GbDistributerPurchaseBatchEntity> purchaseBatch2 = gbDPBService.queryDisPurchaseBatch(map2);
//        Map<String, Object> map3 = new HashMap<>();
//        map3.put("month", getLastMonth());
//        map3.put("arr", purchaseBatch2);
//
//        Map<String, Object> map4 = new HashMap<>();
//        map4.put("purUserId", userId);
//        map4.put("month", getLastTwoMonth());
//        map4.put("dayuStatus", 1);
//        List<GbDistributerPurchaseBatchEntity> purchaseBatch3 = gbDPBService.queryDisPurchaseBatch(map4);
//        Map<String, Object> map5 = new HashMap<>();
//        map5.put("month", getLastTwoMonth());
//        map5.put("arr", purchaseBatch3);

//        List<Map<String, Object>> result = new ArrayList<>();
//        result.add(map1);
//        result.add(map3);
//        result.add(map5);

        return R.ok().put("data", purchaseBatch);
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
        mapR.put("disInfo", gbDistributerService.queryObject(disId));

        NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.queryObject(supplierId);
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


    @RequestMapping(value = "/finishReturnToSuppler", method = RequestMethod.POST)
    @ResponseBody
    public R finishReturnToSuppler(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {

        //1，保存供应商退货单
        if (batchEntity.getGbDpbPayType() == 0) {
            batchEntity.setGbDpbStatus(4); //如果是现金 status == 4 完成结账状态
        } else {
            batchEntity.setGbDpbStatus(3); //如果是记账，status == 2， 开票完成后，status == 3
        }
        batchEntity.setGbDpbDate(formatWhatDay(0));
        batchEntity.setGbDpbHour(formatWhatHour(0));
        batchEntity.setGbDpbMinute(formatWhatMinute(0));
        batchEntity.setGbDpbTime(formatWhatTime(0));
        batchEntity.setGbDpbPurchaseMonth(formatWhatMonth(0));
        batchEntity.setGbDpbPurchaseWeek(getWeek(0));
        batchEntity.setGbDpbPurchaseYear(formatWhatYear(0));
        batchEntity.setGbDpbPurchaseFullTime(formatWhatYearDayTime(0));
        batchEntity.setGbDpbFinishFullTime(formatWhatYearDayTime(0));
        batchEntity.setGbDpbPaySubtotal(batchEntity.getGbDpbSubtotal());

        gbDPBService.save(batchEntity);


        //3，保存部门退货单
        GbDepartmentBillEntity billEntity = new GbDepartmentBillEntity();
        billEntity.setGbDbOrderAmount(1);
        billEntity.setGbDbIssueUserId(batchEntity.getGbDpbPurUserId());
        billEntity.setGbDbStatus(0);
        billEntity.setGbDbDate(formatWhatDay(0));
        billEntity.setGbDbMonth(formatWhatMonth(0));
        billEntity.setGbDbWeek(getWeek(0));
        billEntity.setGbDbTime(formatFullTime());
        billEntity.setGbDbTotal(batchEntity.getGbDpbSubtotal());
        billEntity.setGbDbDisId(batchEntity.getGbDpbDistributerId());
        billEntity.setGbDbIssueDepId(batchEntity.getGbDpbPurDepartmentId());
        billEntity.setGbDbIssueOrderType(getGbOrderTypeTuihuo());
        String areaCode = "1" + batchEntity.getGbDpbDistributerId();
        billEntity.setGbDbTradeNo(generateBillTradeNo(areaCode));
        gbDepartmentBillService.save(billEntity);


        //2， 修改订单和采购商品状态
        for (GbDistributerPurchaseGoodsEntity purGoods : batchEntity.getGbDPGEntities()) {
            purGoods.setGbDpgBatchId(batchEntity.getGbDistributerPurchaseBatchId());
            purGoods.setGbDpgStatus(3);
            purGoods.setGbDpgPayType(batchEntity.getGbDpbPayType());
            purGoods.setGbDpgPurchaseDate(formatWhatDay(0));
            purGoods.setGbDpgPurchaseYear(formatWhatYear(0));
            purGoods.setGbDpgPurchaseDate(formatWhatDay(0));
            purGoods.setGbDpgPurchaseMonth(formatWhatMonth(0));
            purGoods.setGbDpgPurchaseYear(formatWhatYear(0));
            purGoods.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
            purGoods.setGbDpgPurchaseWeek(getWeek(0));
            purGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
            purGoods.setGbDpgTime(formatWhatTime(0));
            gbDPGService.update(purGoods);

            Integer gbDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", gbDistributerPurchaseGoodsId);
            List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);

            for (GbDepartmentOrdersEntity orders : ordersEntities) {

                Integer gbDoDgsrReturnId = orders.getGbDoDgsrReturnId();
                GbDepartmentGoodsStockReduceEntity reduceEntity = gbDepartmentStockReduceService.queryObject(gbDoDgsrReturnId);
                reduceEntity.setGbDgsrStatus(0);
                gbDepartmentStockReduceService.update(reduceEntity);
                orders.setGbDoArriveWeeksYear(getWeekOfYear(0));
                orders.setGbDoArriveWhatDay(getWeek(0));
                orders.setGbDoArriveOnlyDate(formatWhatDate(0));
                orders.setGbDoArriveDate(formatWhatDay(0));
                orders.setGbDoStatus(4);
                orders.setGbDoBuyStatus(3);
                orders.setGbDoBillId(billEntity.getGbDepartmentBillId());
                gbDepartmentOrdersService.update(orders);
            }

            billEntity.setGbDbDepId(ordersEntities.get(0).getGbDoDepartmentFatherId());
            gbDepartmentBillService.update(billEntity);

        }

        return R.ok();
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
                reduceEntity.setGbDgsrStatus(2);
                gbDepartmentStockReduceService.update(reduceEntity);
                orders.setGbDoStatus(getGbOrderStatusReceived()); //wancheng
                System.out.println("bactpey");
                if (batch.getGbDpbPayType() == 0) {
                    orders.setGbDoBuyStatus(getGbOrderBuyStatusHavePayFinish());
                } else {
                    orders.setGbDoBuyStatus(getGbOrderBuyStatusUnPayFinish());
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
            NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.queryObject(gbDpbSupplierId);
            Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
            System.out.println("dafdafas" + supplierEntity);
            if (nxJrdhsUserId == null) {
                System.out.println("dafafads" + nxJrdhsUserId);
                supplierEntity.setNxJrdhsUserId(batch.getGbDpbSellUserId());
                nxJrdhSupplierService.update(supplierEntity);
            }
        }
        gbDPBService.update(batch);

        return R.ok();

    }


    @RequestMapping(value = "/nxDisPrintGbPurBatch/{id}", method = RequestMethod.GET)
    @ResponseBody
    public R nxDisPrintGbPurBatch(@PathVariable Integer id) {
        System.out.println("nxDisPrintGbPurBatchnxDisPrintGbPurBatch");
        GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryObject(id);
        Integer gbDpbStatus = gbDistributerPurchaseBatchEntity.getGbDpbStatus();
        int orderCount = 0;
        if (gbDpbStatus == 1) {
            Map<String, Object> mapG = new HashMap<>();
            mapG.put("batchId", id);
            List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(mapG);
            for (GbDistributerPurchaseGoodsEntity purGoods : purchaseGoodsEntities) {
//                purGoods.setGbDpgPayType(1);
//                purGoods.setGbDpgStatus();
//                purGoods.setGbDpgPurchaseNxSupplierId(gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
//                gbDPGService.update(purGoods);
                orderCount += purGoods.getGbDpgOrdersAmount();
                Integer gbDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
                Map<String, Object> map = new HashMap<>();
                map.put("purGoodsId", gbDistributerPurchaseGoodsId);
                List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);

                for (GbDepartmentOrdersEntity orders : ordersEntities) {
                    if (orders.getGbDoOrderType().equals(getGbOrderTypeTuihuo())) {
                        Integer gbDoDgsrReturnId = orders.getGbDoDgsrReturnId();
                        GbDepartmentGoodsStockReduceEntity reduceEntity = gbDepartmentStockReduceService.queryObject(gbDoDgsrReturnId);
                        reduceEntity.setGbDgsrStatus(2);
                        gbDepartmentStockReduceService.update(reduceEntity);
                    } else {
//                        saveDepStockDataByPurchase(orders, gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
                    }

                    orders.setGbDoStatus(getGbOrderStatusHasFinished()); //wancheng
                    orders.setGbDoBuyStatus(getGbOrderBuyStatusHasWeightAndPrice());
                    gbDepartmentOrdersService.update(orders);

                    Integer gbDoNxDepartmentOrderId = orders.getGbDoNxDepartmentOrderId();
                    NxDepartmentOrdersEntity ordersEntity = nxDepartmentOrdersService.queryObject(gbDoNxDepartmentOrderId);
                    ordersEntity.setNxDoStatus(3);
                    nxDepartmentOrdersService.update(ordersEntity);

                }
            }

            gbDistributerPurchaseBatchEntity.setGbDpbFinishFullTime(formatFullTime());
            gbDistributerPurchaseBatchEntity.setGbDpbStatus(getGbDisPurchaseBatchDisUserWaitReceive());
//            gbDistributerPurchaseBatchEntity.setGbDpbStatus(());
            gbDPBService.update(gbDistributerPurchaseBatchEntity);

//            Integer gbDpbDistributerId = gbDistributerPurchaseBatchEntity.getGbDpbDistributerId();
//            GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(gbDpbDistributerId);

//            GbDistributerPayListEntity payListEntity = new GbDistributerPayListEntity();
//            payListEntity.setGbNdplPaySubtotal(Integer.valueOf(orderCount).toString());
//            payListEntity.setGbNdplPayTime(formatFullTime());
//            payListEntity.setGbNdplPayDate(formatWhatDay(0));
//            payListEntity.setGbNdplPayMonth(formatWhatMonth(0));
//            payListEntity.setGbNdplPayYear(formatWhatYear(0));
//            payListEntity.setGbNdplStatus(0);
//            payListEntity.setGbNdplType(0);
//            payListEntity.setGbNdplRestPoints(Integer.valueOf(gbDistributerEntity.getGbDistributerBuyQuantity()));
//            payListEntity.setGbNdplGbDisId(gbDistributerEntity.getGbDistributerId());
//            payListEntity.setGbNdplNxSupplierId(gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
//            payListEntity.setGbNdplGbPbId(gbDistributerPurchaseBatchEntity.getGbDistributerPurchaseBatchId());
//            payListService.save(payListEntity);

//            BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
            BigDecimal decimal1 = new BigDecimal(orderCount);
//            BigDecimal add = decimal.subtract(decimal1).setScale(0, BigDecimal.ROUND_HALF_UP);
//            gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
//            gbDistributerService.update(gbDistributerEntity);

            Map<String, Object> mapR = new HashMap<>();
            mapR.put("supplierId", gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
            mapR.put("gbDisId", gbDistributerPurchaseBatchEntity.getGbDpbDistributerId());
            mapR.put("nxDisId", gbDistributerPurchaseBatchEntity.getGbDpbNxDistributerId());
            NxDistributerGbDistributerEntity nxDistributerGbDistributerEntity = nxDisGbDisService.queryObjectByParams(mapR);
            if (nxDistributerGbDistributerEntity.getNxDgdFromNxDisId() != null && !nxDistributerGbDistributerEntity.getNxDgdFromNxDisId().equals(gbDistributerPurchaseBatchEntity.getGbDpbNxDistributerId())) {
                NxDistributerEntity nxDistributerEntity = nxDistributerService.queryObject(gbDistributerPurchaseBatchEntity.getGbDpbNxDistributerId());

                NxDistributerPayListEntity nxPayListEntity = new NxDistributerPayListEntity();
                nxPayListEntity.setNxNdplNxDisId(gbDistributerPurchaseBatchEntity.getGbDpbNxDistributerId());
                nxPayListEntity.setNxNdplNxDepartmentId(-1);
                nxPayListEntity.setNxNdplNxDepartmentFatherId(-1);
                nxPayListEntity.setNxNdplPaySubtotal(Integer.valueOf(orderCount).toString());
                nxPayListEntity.setNxNdplPayTime(formatFullTime());
                nxPayListEntity.setNxNdplPayDate(formatWhatDay(0));
                nxPayListEntity.setNxNdplPayMonth(formatWhatMonth(0));
                nxPayListEntity.setNxNdplPayYear(formatWhatYear(0));
                nxPayListEntity.setNxNdplStatus(0);
                nxPayListEntity.setNxNdplType(getNxDisPayListPrinter());
                nxPayListEntity.setNxNdplRestPoints(nxDistributerEntity.getNxDistributerBuyQuantity());
                nxPayListService.save(nxPayListEntity);

                BigDecimal decimal0 = new BigDecimal(nxDistributerEntity.getNxDistributerBuyQuantity());
                BigDecimal restPoints = decimal0.subtract(decimal1).setScale(0, BigDecimal.ROUND_HALF_UP);
                nxDistributerEntity.setNxDistributerBuyQuantity(restPoints.toString());
                nxDistributerService.update(nxDistributerEntity);
            }

            return R.ok();

        } else {
            return R.error(-1, "请刷新数据。");
        }
    }

    @RequestMapping(value = "/depReceiveStock/{id}")
    @ResponseBody
    public R depReceiveStock(@PathVariable Integer id) {

        GbDepartmentOrdersEntity order = gbDepartmentOrdersService.queryObject(id);

        Integer gbDoStatus = order.getGbDoStatus();
        //判断没有被别人收货
        if (gbDoStatus == 2) {

            //0,修改订单上次价格涨幅
            Integer gbDoDepDisGoodsId = order.getGbDoDepDisGoodsId();
            GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.queryObject(gbDoDepDisGoodsId);

            if (departmentDisGoodsEntity.getGbDdgOrderDate() != null && !departmentDisGoodsEntity.getGbDdgOrderDate().trim().isEmpty()) {
                if (departmentDisGoodsEntity.getGbDdgOrderPrice() != null && !departmentDisGoodsEntity.getGbDdgOrderPrice().trim().isEmpty() &&
                        order.getGbDoPrice() != null && !order.getGbDoPrice().trim().isEmpty()) {
                    BigDecimal decimal = new BigDecimal(departmentDisGoodsEntity.getGbDdgOrderPrice());
                    BigDecimal decimal1 = new BigDecimal(order.getGbDoPrice());
                    BigDecimal subtract1 = decimal1.subtract(decimal);
                    order.setGbDoPriceDifferent(subtract1.toString());
                } else {
                    order.setGbDoPriceDifferent("0");
                }
            }


            //3，修改送货单收货单子数量
            Integer supplierId = -1;
            Integer purUserId = -1;


            if (order.getGbDoPurchaseGoodsId() != -1) {
                GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(order.getGbDoPurchaseGoodsId());
                supplierId = purchaseGoodsEntity.getGbDpgPurchaseNxSupplierId();
                purUserId = purchaseGoodsEntity.getGbDpgPurUserId();
                BigDecimal gbPgOrderAmount = new BigDecimal(purchaseGoodsEntity.getGbDpgOrdersAmount());
                System.out.println("amoauant" + purchaseGoodsEntity.getGbDpgOrdersAmount() + "finsaount" +
                        purchaseGoodsEntity.getGbDpgOrdersFinishAmount());
                BigDecimal gbDbFinishAmount = new BigDecimal(purchaseGoodsEntity.getGbDpgOrdersFinishAmount());
                if (gbDbFinishAmount.add(new BigDecimal(1)).compareTo(gbPgOrderAmount) == 0) {
                    purchaseGoodsEntity.setGbDpgOrdersFinishAmount(purchaseGoodsEntity.getGbDpgOrdersAmount());
                    if(purchaseGoodsEntity.getGbDpgPayType() == 0){
                        purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusPayFinish());
                    }else{
                        purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusStockFinish());
                    }

                    purchaseGoodsEntity.setGbDpgStockFinishDate(formatWhatDay(0));
                    //修改 batch 和 bill
                    Map<String, Object> map = new HashMap<>();
                    map.put("batchId", purchaseGoodsEntity.getGbDpgBatchId());
                    System.out.println("mappapaa111111" + map);
                    Integer batchCount = gbDPGService.queryGbPurchaseGoodsCount(map);
                    map.put("equalStatus", getGbPurchaseGoodsStatusStockFinish());
                    System.out.println("mappapaa22222222" + map);
                    Integer finishInteger = gbDPGService.queryGbPurchaseGoodsCount(map);
                    if (batchCount - finishInteger == 1) {
                        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryObject(purchaseGoodsEntity.getGbDpgBatchId());
                        Integer payType =  batchEntity.getGbDpbPayType();
                        if(payType == 0){
                            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDisUserFinishPay());
                        }else{
                            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDepUserReceiveFinish());
                        }
                        batchEntity.setGbDpbFinishFullTime(formatFullTime());
                        if (batchEntity.getGbDpbPurchaseType() == 5) {
                            Map<String, Object> maps = new HashMap<>();
                            maps.put("nxDisId", purchaseGoodsEntity.getGbDpgPurchaseNxDistributerId());
                            maps.put("gbDisId", purchaseGoodsEntity.getGbDpgDistributerId());
                            NxDistributerGbDistributerEntity nxDistributerGbDistributerEntity = nxDisGbDisService.queryObjectByParams(maps);
                            if (nxDistributerGbDistributerEntity.getNxDgdGbPayMethod() == 1) {
                                batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDepUserReceiveFinish());
                                batchEntity.setGbDpbFinishFullTime(formatFullTime());
                            }

                            // 1，修改送货单收货单子数量
                            Integer gbDoBillId = order.getGbDoBillId();
                            GbDepartmentBillEntity billEntity = gbDepartmentBillService.queryObject(gbDoBillId);
                            System.out.println("billlle" + billEntity.getGbDbOrderAmount() + "==billid==" + billEntity.getGbDepartmentBillId());
                            BigDecimal gbDbOrderAmount = new BigDecimal(billEntity.getGbDbOrderAmount());
                            if (gbDbOrderAmount.compareTo(new BigDecimal("1")) == 1) {
                                BigDecimal subtract = gbDbOrderAmount.subtract(new BigDecimal("1"));
                                billEntity.setGbDbOrderAmount(subtract.intValue());
                                gbDepartmentBillService.update(billEntity);
                            } else {
                                billEntity.setGbDbOrderAmount(0);
                                billEntity.setGbDbStatus(getGbDepBillWaitingPay());
                                batchEntity.setGbDpbDepBillId(billEntity.getGbDepartmentBillId());
                                gbDepartmentBillService.update(billEntity);
                            }
                        }

                        gbDPBService.update(batchEntity);

                        map.put("status", null);
                        System.out.println("mapppapap" + map);
                        int orderCount = gbDPGService.queryPurchaseGoodsOrderCount(map);
                        Integer gbDoDistributerId = order.getGbDoDistributerId();
                        GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(gbDoDistributerId);
                        GbDistributerPayListEntity payListEntity = new GbDistributerPayListEntity();
                        payListEntity.setGbNdplPaySubtotal(Integer.valueOf(orderCount).toString());
                        payListEntity.setGbNdplPayTime(formatFullTime());
                        payListEntity.setGbNdplPayDate(formatWhatDay(0));
                        payListEntity.setGbNdplPayMonth(formatWhatMonth(0));
                        payListEntity.setGbNdplPayYear(formatWhatYear(0));
                        payListEntity.setGbNdplStatus(0);
                        payListEntity.setGbNdplType(getGbDisPayBatchFinish());
                        payListEntity.setGbNdplRestPoints(gbDistributerEntity.getGbDistributerBuyQuantity());
                        payListEntity.setGbNdplGbDisId(gbDistributerEntity.getGbDistributerId());
                        payListEntity.setGbNdplNxSupplierId(supplierId);
                        payListEntity.setGbNdplGbPbId(batchEntity.getGbDistributerPurchaseBatchId());
                        payListEntity.setGbNdplGbDisGoodsId(-1);
                        payListService.save(payListEntity);

                        BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
                        BigDecimal decimal1 = new BigDecimal(orderCount);
                        BigDecimal add = decimal.subtract(decimal1);
                        gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
                        gbDistributerService.update(gbDistributerEntity);

                    }

                } else {
                    BigDecimal add = gbDbFinishAmount.add(new BigDecimal(1));
                    purchaseGoodsEntity.setGbDpgOrdersFinishAmount(add.intValue());
                }
                System.out.println("updatpuurururrurr");
                gbDPGService.update(purchaseGoodsEntity);
            }


            GbDepartmentGoodsStockEntity stockEntity = new GbDepartmentGoodsStockEntity();
            stockEntity.setGbDgsGbDepartmentId(order.getGbDoDepartmentId());
            stockEntity.setGbDgsGbDepartmentFatherId(order.getGbDoDepartmentFatherId());
            stockEntity.setGbDgsGbPurGoodsId(order.getGbDoPurchaseGoodsId());
            stockEntity.setGbDgsGbDistributerId(order.getGbDoDistributerId());
            stockEntity.setGbDgsWeight(order.getGbDoWeight());
            System.out.println("stooosrooriri" + order.getGbDoPrice());
            stockEntity.setGbDgsPrice(order.getGbDoPrice());
            stockEntity.setGbDgsSubtotal(order.getGbDoSubtotal());
            stockEntity.setGbDgsRestWeight(order.getGbDoWeight());
            stockEntity.setGbDgsRestSubtotal(order.getGbDoSubtotal());
            stockEntity.setGbDgsGbDisGoodsId(order.getGbDoDisGoodsId());
            stockEntity.setGbDgsNxSupplierId(supplierId);
            stockEntity.setGbDgsPurUserId(purUserId);

            Integer gbDoDisGoodsId = order.getGbDoDisGoodsId();
            GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
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
            stockEntity.setGbDgsLossWeight("0");
            stockEntity.setGbDgsLossSubtotal("0");
            stockEntity.setGbDgsReturnWeight("0");
            stockEntity.setGbDgsReturnSubtotal("0");
            stockEntity.setGbDgsProduceWeight("0");
            stockEntity.setGbDgsProduceSubtotal("0");
            stockEntity.setGbDgsWasteWeight("0");
            stockEntity.setGbDgsWasteSubtotal("0");
            String gbDdgSellingPrice = departmentDisGoodsEntity.getGbDdgSellingPrice();
            if (gbDdgSellingPrice != null && !gbDdgSellingPrice.trim().isEmpty() && new BigDecimal(gbDdgSellingPrice).compareTo(new BigDecimal(0)) == 1) {
                stockEntity.setGbDgsAfterProfitSubtotal("0");
                stockEntity.setGbDgsBetweenPrice("0");
                stockEntity.setGbDgsCostRate("0");
                stockEntity.setGbDgsSellingSubtotal("0");
                stockEntity.setGbDgsProduceSellingSubtotal("0");
                stockEntity.setGbDgsProfitSubtotal("0");
                stockEntity.setGbDgsProfitWeight("0");
                stockEntity.setGbDgsSellingPrice(gbDdgSellingPrice);
            } else {
                stockEntity.setGbDgsSellingPrice("-1");
            }

            // showStandard
            if (departmentDisGoodsEntity.getGbDdgShowStandardId() != -1) {
                String gbDdgShowStandardScale = departmentDisGoodsEntity.getGbDdgShowStandardScale();
                BigDecimal divide = new BigDecimal(order.getGbDoWeight()).divide(new BigDecimal(gbDdgShowStandardScale), 1, BigDecimal.ROUND_HALF_UP);
                stockEntity.setGbDgsRestWeightShowStandard(divide.toString());
                stockEntity.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
            }

            //判断是否有保鲜时间参数
            if (order.getGbDoPurchaseGoodsId() != -1) {
                GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(order.getGbDoPurchaseGoodsId());
                String gbDpgWasteFullTime1 = purchaseGoodsEntity.getGbDpgWasteFullTime();
                if (gbDpgWasteFullTime1 != null && !gbDpgWasteFullTime1.trim().isEmpty()) {
                    stockEntity.setGbDgsWasteFullTime(purchaseGoodsEntity.getGbDpgWasteFullTime());
                    String gbDpgWasteFullTime = purchaseGoodsEntity.getGbDpgWasteFullTime();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    // 设置日期字符串
                    // 解析日期字符串为Date对象
                    Date dateWaste = null;
                    Date dateWarn = null;

                    try {
                        if (gbDpgWasteFullTime != null && !gbDpgWasteFullTime.trim().isEmpty()) {
                            dateWaste = dateFormat.parse(gbDpgWasteFullTime);
                        }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    // 获取时间戳
                    long timestampWaste = 0;
                    long timestampWarn = 0;
                    if (dateWaste != null) {
                        timestampWaste = dateWaste.getTime();
                    }
                    if (dateWarn != null) {
                        timestampWarn = dateWarn.getTime();
                    }
                    // 输出时间戳

                    System.out.println("gbDpgWarnFullTimegbDpgWarnFullTime22222" + purchaseGoodsEntity.getGbDpgWasteFullTime());

                    stockEntity.setGbDgsWasteTimeQuantumName(String.valueOf(timestampWaste));
                }
                //判断是否价格异常商品
                if (purchaseGoodsEntity.getGbDpgDisGoodsPriceId() != null && !purchaseGoodsEntity.getGbDpgDisGoodsPriceId().toString().trim().isEmpty()) {
                    GbDistributerGoodsPriceEntity goodsPriceEntity = goodsPriceService.queryObject(purchaseGoodsEntity.getGbDpgDisGoodsPriceId());
                    String doWeight = order.getGbDoWeight();
                    Integer gbDgpPurWhat = goodsPriceEntity.getGbDgpPurWhat();
                    String whatSubtotal = "";
                    if (gbDgpPurWhat == 1) {
                        String gbDgpGoodsHighestPrice = goodsPriceEntity.getGbDgpGoodsHighestPrice();
                        String purPrice = goodsPriceEntity.getGbDgpPurPrice();
                        BigDecimal diffPrice = new BigDecimal(purPrice).subtract(new BigDecimal(gbDgpGoodsHighestPrice));
                        BigDecimal subtotal = diffPrice.multiply(new BigDecimal(doWeight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                        whatSubtotal = subtotal.toString();
                    }
                    if (gbDgpPurWhat == -1) {
                        String lowestPrice = goodsPriceEntity.getGbDgpGoodsLowestPrice();
                        String purPrice = goodsPriceEntity.getGbDgpPurPrice();
                        BigDecimal diffPrice = new BigDecimal(purPrice).subtract(new BigDecimal(lowestPrice));
                        BigDecimal subtotal = diffPrice.multiply(new BigDecimal(doWeight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                        whatSubtotal = subtotal.toString();
                    }

                    //价控最低价的成本
                    //实际成本与最低成本的差价
                    stockEntity.setGbDgsGbPriceSubtotal(whatSubtotal); // 相差了多少成本
                    stockEntity.setGbDgsGbPriceGoodsId(purchaseGoodsEntity.getGbDpgDisGoodsPriceId());
                    stockEntity.setGbDgsGbPriceSubtotalScale(goodsPriceEntity.getGbDgpPurScale());
                }

            }

            stockEntity.setGbDgsStatus(0);
            stockEntity.setGbDgsGbDepartmentOrderId(order.getGbDepartmentOrdersId());
            stockEntity.setGbDgsGbGoodsStockId(-1);
            stockEntity.setGbDgsGbFromDepartmentId(order.getGbDoToDepartmentId());
            stockEntity.setGbDgsNxDistributerId(order.getGbDoNxDistributerId());
            stockEntity.setGbDgsReceiveUserId(order.getGbDoReceiveUserId());
            stockEntity.setGbDgsInventoryDate(formatWhatDay(0));
            stockEntity.setGbDgsInventoryWeek(getWeekOfYear(0).toString());
            stockEntity.setGbDgsInventoryMonth(formatWhatMonth(0));
            stockEntity.setGbDgsInventoryYear(formatWhatYear(0));
            stockEntity.setGbDgsStars(5);
            System.out.println("rusosossltocc" + stockEntity.getGbDgsPrice());
            gbDepartmentGoodsStockService.save(stockEntity);


            orderAddDepDisGoods(order, stockEntity, gbDoDepDisGoodsId);
            updateDepGoodsDailyBusiness(stockEntity);

            //2，修改订单状态
            order.setGbDoStatus(getGbOrderStatusReceived());
            order.setGbDoBuyStatus(getGbOrderBuyStatusUnPayFinish());
            order.setGbDoArriveDate(formatWhatDay(0));
            order.setGbDoArriveWeeksYear(getWeekOfYear(0));
            order.setGbDoArriveWhatDay(getWeek(0));
            order.setGbDoArriveOnlyDate(formatWhatDate(0));
            order.setGbDoArriveDate(formatWhatDay(0));
            System.out.println("getGbOrderBuyStatusUnPayFinishgetGbOrderBuyStatusUnPayFinish");
            gbDepartmentOrdersService.update(order);


            return R.ok();


        }
        return R.error(-1, "订单已收货");


    }


    @RequestMapping(value = "/receiveGbBatchItem/{id}")
    @ResponseBody
    public R receiveGbBatchItem(@PathVariable Integer id) {


        Integer purUserId = -1;
        int orderCount = 0;
        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(id);
        Integer supplierId = purchaseGoodsEntity.getGbDpgPurchaseNxSupplierId();
        if (purchaseGoodsEntity.getGbDpgStatus() == 2) {
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", id);
            List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            if (ordersEntities.size() > 0) {
                for (GbDepartmentOrdersEntity order : ordersEntities) {
                    Integer gbDoStatus = order.getGbDoStatus();
                    orderCount += orderCount;
                    //判断没有被别人收货
                    if (gbDoStatus == 2) {
                        //0,修改订单上次价格涨幅
                        Integer gbDoDepDisGoodsId = order.getGbDoDepDisGoodsId();
                        GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.queryObject(gbDoDepDisGoodsId);

                        if (departmentDisGoodsEntity.getGbDdgOrderDate() != null && !departmentDisGoodsEntity.getGbDdgOrderDate().trim().isEmpty()) {
                            if (departmentDisGoodsEntity.getGbDdgOrderPrice() != null && !departmentDisGoodsEntity.getGbDdgOrderPrice().trim().isEmpty() &&
                                    order.getGbDoPrice() != null && !order.getGbDoPrice().trim().isEmpty()) {
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
                        System.out.println("stooosrooriri" + order.getGbDoPrice());
                        stockEntity.setGbDgsPrice(order.getGbDoPrice());
                        stockEntity.setGbDgsSubtotal(order.getGbDoSubtotal());
                        stockEntity.setGbDgsRestWeight(order.getGbDoWeight());
                        stockEntity.setGbDgsRestSubtotal(order.getGbDoSubtotal());
                        stockEntity.setGbDgsGbDisGoodsId(order.getGbDoDisGoodsId());
                        stockEntity.setGbDgsNxSupplierId(supplierId);
                        stockEntity.setGbDgsPurUserId(purUserId);

                        Integer gbDoDisGoodsId = order.getGbDoDisGoodsId();
                        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
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
                        stockEntity.setGbDgsLossWeight("0");
                        stockEntity.setGbDgsLossSubtotal("0");
                        stockEntity.setGbDgsReturnWeight("0");
                        stockEntity.setGbDgsReturnSubtotal("0");
                        stockEntity.setGbDgsProduceWeight("0");
                        stockEntity.setGbDgsProduceSubtotal("0");
                        stockEntity.setGbDgsWasteWeight("0");
                        stockEntity.setGbDgsWasteSubtotal("0");
                        stockEntity.setGbDgsSellingPrice("-1");
                        // showStandard
                        if (departmentDisGoodsEntity.getGbDdgShowStandardId() != -1) {
                            String gbDdgShowStandardScale = departmentDisGoodsEntity.getGbDdgShowStandardScale();
                            BigDecimal divide = new BigDecimal(order.getGbDoWeight()).divide(new BigDecimal(gbDdgShowStandardScale), 1, BigDecimal.ROUND_HALF_UP);
                            stockEntity.setGbDgsRestWeightShowStandard(divide.toString());
                            stockEntity.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
                        }

                        //判断是否有保鲜时间参数
                        String gbDpgWasteFullTime1 = purchaseGoodsEntity.getGbDpgWasteFullTime();
                        if (gbDpgWasteFullTime1 != null && !gbDpgWasteFullTime1.trim().isEmpty()) {
                            stockEntity.setGbDgsWasteFullTime(purchaseGoodsEntity.getGbDpgWasteFullTime());
                            String gbDpgWasteFullTime = purchaseGoodsEntity.getGbDpgWasteFullTime();
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                            // 设置日期字符串
                            // 解析日期字符串为Date对象
                            Date dateWaste = null;
                            try {
                                if (gbDpgWasteFullTime != null && !gbDpgWasteFullTime.trim().isEmpty()) {
                                    dateWaste = dateFormat.parse(gbDpgWasteFullTime);
                                }
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            // 获取时间戳
                            long timestampWaste = 0;
                            if (dateWaste != null) {
                                timestampWaste = dateWaste.getTime();
                            }
                            stockEntity.setGbDgsWasteTimeQuantumName(String.valueOf(timestampWaste));
                        }

                        stockEntity.setGbDgsStatus(0);
                        stockEntity.setGbDgsGbDepartmentOrderId(order.getGbDepartmentOrdersId());
                        stockEntity.setGbDgsGbGoodsStockId(-1);
                        stockEntity.setGbDgsGbFromDepartmentId(order.getGbDoToDepartmentId());
                        stockEntity.setGbDgsNxDistributerId(order.getGbDoNxDistributerId());
                        stockEntity.setGbDgsReceiveUserId(order.getGbDoReceiveUserId());
                        stockEntity.setGbDgsInventoryDate(formatWhatDay(0));
                        stockEntity.setGbDgsInventoryWeek(getWeekOfYear(0).toString());
                        stockEntity.setGbDgsInventoryMonth(formatWhatMonth(0));
                        stockEntity.setGbDgsInventoryYear(formatWhatYear(0));
                        stockEntity.setGbDgsStars(5);
                        System.out.println("rusosossltocc" + stockEntity.getGbDgsPrice());
                        gbDepartmentGoodsStockService.save(stockEntity);


                        orderAddDepDisGoods(order, stockEntity, gbDoDepDisGoodsId);
                        updateDepGoodsDailyBusiness(stockEntity);

                        //2，修改订单状态
                        order.setGbDoStatus(getGbOrderStatusReceived());
                        order.setGbDoBuyStatus(getGbOrderBuyStatusUnPayFinish());
                        order.setGbDoArriveDate(formatWhatDay(0));
                        order.setGbDoArriveWeeksYear(getWeekOfYear(0));
                        order.setGbDoArriveWhatDay(getWeek(0));
                        order.setGbDoArriveOnlyDate(formatWhatDate(0));
                        order.setGbDoArriveDate(formatWhatDay(0));
                        System.out.println("getGbOrderBuyStatusUnPayFinishgetGbOrderBuyStatusUnPayFinish===" + order.getGbDepartmentOrdersId());
                        gbDepartmentOrdersService.update(order);

                    }
                }
            }
            purchaseGoodsEntity.setGbDpgOrdersFinishAmount(purchaseGoodsEntity.getGbDpgOrdersAmount());
            purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusStockFinish());
            purchaseGoodsEntity.setGbDpgStockFinishDate(formatWhatDay(0));

            System.out.println("updatpuurururrurr");
            gbDPGService.update(purchaseGoodsEntity);

            //修改 batch 和 bill
            Map<String, Object> mapBatch = new HashMap<>();
            mapBatch.put("batchId", purchaseGoodsEntity.getGbDpgBatchId());
            System.out.println("mappapaa111111" + mapBatch);
            Integer batchCount = gbDPGService.queryGbPurchaseGoodsCount(mapBatch);
            mapBatch.put("equalStatus", getGbPurchaseGoodsStatusStockFinish());
            System.out.println("mappapaa22222222" + mapBatch);
            Integer finishInteger = gbDPGService.queryGbPurchaseGoodsCount(mapBatch);
            if (batchCount - finishInteger == 0) {
                GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryObject(purchaseGoodsEntity.getGbDpgBatchId());
                batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDepUserReceiveFinish());
                batchEntity.setGbDpbFinishFullTime(formatFullTime());
                gbDPBService.update(batchEntity);

                Integer gbDoDistributerId = purchaseGoodsEntity.getGbDpgDistributerId();
                GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(gbDoDistributerId);
                GbDistributerPayListEntity payListEntity = new GbDistributerPayListEntity();
                payListEntity.setGbNdplPaySubtotal(Integer.valueOf(orderCount).toString());
                payListEntity.setGbNdplPayTime(formatFullTime());
                payListEntity.setGbNdplPayDate(formatWhatDay(0));
                payListEntity.setGbNdplPayMonth(formatWhatMonth(0));
                payListEntity.setGbNdplPayYear(formatWhatYear(0));
                payListEntity.setGbNdplStatus(0);
                payListEntity.setGbNdplType(getGbDisPayBatchFinish());
                payListEntity.setGbNdplRestPoints(gbDistributerEntity.getGbDistributerBuyQuantity());
                payListEntity.setGbNdplGbDisId(gbDistributerEntity.getGbDistributerId());
                payListEntity.setGbNdplNxSupplierId(supplierId);
                payListEntity.setGbNdplGbPbId(batchEntity.getGbDistributerPurchaseBatchId());
                payListEntity.setGbNdplGbDisGoodsId(-1);
                payListService.save(payListEntity);

                BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
                BigDecimal decimal1 = new BigDecimal(orderCount);
                BigDecimal add = decimal.subtract(decimal1);
                gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
                gbDistributerService.update(gbDistributerEntity);
            }
            return R.ok();

        } else {
            return R.error(-1, "已经收货");
        }

    }


    @RequestMapping(value = "/receiveGbBatch/{id}")
    @ResponseBody
    public R receiveGbBatch(@PathVariable Integer id) {
        int orderCount = 0;
        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryObject(id);
        Integer supplierId = batchEntity.getGbDpbSupplierId();
        if (batchEntity.getGbDpbStatus() != 2) {
            return R.error(-1, "订单状态已经改变");
        } else {
            Map<String, Object> map1 = new HashMap<>();
            map1.put("batchId", id);
            List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = gbDPGService.queryOnlyPurGoods(map1);
            if (purchaseGoodsEntityList.size() > 0) {
                for (GbDistributerPurchaseGoodsEntity purchaseGoodsEntity : purchaseGoodsEntityList) {
                    if (purchaseGoodsEntity.getGbDpgStatus() == 2) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("purGoodsId", purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
                        System.out.println("bpuuururuurrrur" + map);
                        List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
                        if (ordersEntities.size() > 0) {
                            for (GbDepartmentOrdersEntity order : ordersEntities) {
                                Integer gbDoStatus = order.getGbDoStatus();
                                orderCount++;
                                //判断没有被别人收货
                                if (gbDoStatus == 2) {
                                    //0,修改订单上次价格涨幅
                                    Integer gbDoDepDisGoodsId = order.getGbDoDepDisGoodsId();
                                    GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.queryObject(gbDoDepDisGoodsId);

                                    if (departmentDisGoodsEntity.getGbDdgOrderDate() != null && !departmentDisGoodsEntity.getGbDdgOrderDate().trim().isEmpty()) {
                                        if (departmentDisGoodsEntity.getGbDdgOrderPrice() != null && !departmentDisGoodsEntity.getGbDdgOrderPrice().trim().isEmpty() &&
                                                order.getGbDoPrice() != null && !order.getGbDoPrice().trim().isEmpty()) {
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
                                    System.out.println("stooosrooriri" + order.getGbDoPrice());
                                    stockEntity.setGbDgsPrice(order.getGbDoPrice());
                                    stockEntity.setGbDgsSubtotal(order.getGbDoSubtotal());
                                    stockEntity.setGbDgsRestWeight(order.getGbDoWeight());
                                    stockEntity.setGbDgsRestSubtotal(order.getGbDoSubtotal());
                                    stockEntity.setGbDgsGbDisGoodsId(order.getGbDoDisGoodsId());
                                    stockEntity.setGbDgsNxSupplierId(supplierId);
                                    stockEntity.setGbDgsPurUserId(-1);
                                    Integer gbDoDisGoodsId = order.getGbDoDisGoodsId();
                                    GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
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
                                    stockEntity.setGbDgsLossWeight("0");
                                    stockEntity.setGbDgsLossSubtotal("0");
                                    stockEntity.setGbDgsReturnWeight("0");
                                    stockEntity.setGbDgsReturnSubtotal("0");
                                    stockEntity.setGbDgsProduceWeight("0");
                                    stockEntity.setGbDgsProduceSubtotal("0");
                                    stockEntity.setGbDgsWasteWeight("0");
                                    stockEntity.setGbDgsWasteSubtotal("0");
                                    stockEntity.setGbDgsSellingPrice("-1");
                                    // showStandard
                                    if (departmentDisGoodsEntity.getGbDdgShowStandardId() != -1) {
                                        String gbDdgShowStandardScale = departmentDisGoodsEntity.getGbDdgShowStandardScale();
                                        BigDecimal divide = new BigDecimal(order.getGbDoWeight()).divide(new BigDecimal(gbDdgShowStandardScale), 1, BigDecimal.ROUND_HALF_UP);
                                        stockEntity.setGbDgsRestWeightShowStandard(divide.toString());
                                        stockEntity.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
                                    }

                                    //判断是否有保鲜时间参数
                                    String gbDpgWasteFullTime1 = purchaseGoodsEntity.getGbDpgWasteFullTime();
                                    if (gbDpgWasteFullTime1 != null && !gbDpgWasteFullTime1.trim().isEmpty()) {
                                        stockEntity.setGbDgsWasteFullTime(purchaseGoodsEntity.getGbDpgWasteFullTime());
                                        String gbDpgWasteFullTime = purchaseGoodsEntity.getGbDpgWasteFullTime();
                                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                                        // 设置日期字符串
                                        // 解析日期字符串为Date对象
                                        Date dateWaste = null;
                                        try {
                                            if (gbDpgWasteFullTime != null && !gbDpgWasteFullTime.trim().isEmpty()) {
                                                dateWaste = dateFormat.parse(gbDpgWasteFullTime);
                                            }
                                        } catch (ParseException e) {
                                            e.printStackTrace();
                                        }
                                        // 获取时间戳
                                        long timestampWaste = 0;
                                        if (dateWaste != null) {
                                            timestampWaste = dateWaste.getTime();
                                        }
                                        stockEntity.setGbDgsWasteTimeQuantumName(String.valueOf(timestampWaste));
                                    }

                                    stockEntity.setGbDgsStatus(0);
                                    stockEntity.setGbDgsGbDepartmentOrderId(order.getGbDepartmentOrdersId());
                                    stockEntity.setGbDgsGbGoodsStockId(-1);
                                    stockEntity.setGbDgsGbFromDepartmentId(order.getGbDoToDepartmentId());
                                    stockEntity.setGbDgsNxDistributerId(order.getGbDoNxDistributerId());
                                    stockEntity.setGbDgsReceiveUserId(order.getGbDoReceiveUserId());
                                    stockEntity.setGbDgsInventoryDate(formatWhatDay(0));
                                    stockEntity.setGbDgsInventoryWeek(getWeekOfYear(0).toString());
                                    stockEntity.setGbDgsInventoryMonth(formatWhatMonth(0));
                                    stockEntity.setGbDgsInventoryYear(formatWhatYear(0));
                                    stockEntity.setGbDgsStars(5);
                                    System.out.println("rusosossltoccvbbbbb" + stockEntity.getGbDgsPrice());
                                    gbDepartmentGoodsStockService.save(stockEntity);

                                    orderAddDepDisGoods(order, stockEntity, gbDoDepDisGoodsId);
                                    updateDepGoodsDailyBusiness(stockEntity);

                                    //2，修改订单状态
                                    order.setGbDoStatus(getGbOrderStatusReceived());
                                    order.setGbDoBuyStatus(getGbOrderBuyStatusUnPayFinish());
                                    order.setGbDoArriveDate(formatWhatDay(0));
                                    order.setGbDoArriveWeeksYear(getWeekOfYear(0));
                                    order.setGbDoArriveWhatDay(getWeek(0));
                                    order.setGbDoArriveOnlyDate(formatWhatDate(0));
                                    order.setGbDoArriveDate(formatWhatDay(0));
                                    System.out.println("getGbOrderBuyStatusUnPayFinishgetGbOrderBuyStatusUnPayFinish===" + order.getGbDepartmentOrdersId());
                                    gbDepartmentOrdersService.update(order);

                                } else {
                                    return R.error(-1, "bbb");
                                }
                            }
                        }
                        purchaseGoodsEntity.setGbDpgOrdersFinishAmount(purchaseGoodsEntity.getGbDpgOrdersAmount());
                        if(purchaseGoodsEntity.getGbDpgPayType() == 0){
                            purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusPayFinish());

                        }else{
                            purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusStockFinish());

                        }
                        purchaseGoodsEntity.setGbDpgStockFinishDate(formatWhatDay(0));

                        System.out.println("updatpuurururrurr");
                        gbDPGService.update(purchaseGoodsEntity);
                    }
//                    else {
//                        return R.error(-1, "aaa");
//                    }
                }

                if(batchEntity.getGbDpbPayType() == 0){
                    batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDisUserFinishPay());
                }else{
                    batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDepUserReceiveFinish());
                }
                batchEntity.setGbDpbFinishFullTime(formatFullTime());
                gbDPBService.update(batchEntity);

                Integer gbDoDistributerId = batchEntity.getGbDpbDistributerId();
                GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(gbDoDistributerId);
                GbDistributerPayListEntity payListEntity = new GbDistributerPayListEntity();
                payListEntity.setGbNdplPaySubtotal(Integer.valueOf(orderCount).toString());
                payListEntity.setGbNdplPayTime(formatFullTime());
                payListEntity.setGbNdplPayDate(formatWhatDay(0));
                payListEntity.setGbNdplPayMonth(formatWhatMonth(0));
                payListEntity.setGbNdplPayYear(formatWhatYear(0));
                payListEntity.setGbNdplStatus(0);
                payListEntity.setGbNdplType(getGbDisPayBatchFinish());
                payListEntity.setGbNdplRestPoints(gbDistributerEntity.getGbDistributerBuyQuantity());
                payListEntity.setGbNdplGbDisId(gbDistributerEntity.getGbDistributerId());
                payListEntity.setGbNdplNxSupplierId(supplierId);
                payListEntity.setGbNdplGbPbId(batchEntity.getGbDistributerPurchaseBatchId());
                payListEntity.setGbNdplGbDisGoodsId(-1);
                payListService.save(payListEntity);

                BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
                BigDecimal decimal1 = new BigDecimal(orderCount);
                BigDecimal add = decimal.subtract(decimal1);
                gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
                gbDistributerService.update(gbDistributerEntity);

                return R.ok();
            } else {
                return R.error(-1, "ccc");

            }
        }


    }

    @RequestMapping(value = "/finishSharePurGoodsBatch")
    @ResponseBody
    public R finishSharePurGoodsBatch(@RequestBody GbDistributerPurchaseBatchEntity batch) {
        Integer gbDistributerPurchaseBatchId = batch.getGbDistributerPurchaseBatchId();
        GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryObject(gbDistributerPurchaseBatchId);
        Integer gbDpbStatus = gbDistributerPurchaseBatchEntity.getGbDpbStatus();
        int orderCount = 0;
        if (gbDpbStatus == 2) {
            for (GbDistributerPurchaseGoodsEntity purGoods : batch.getGbDPGEntities()) {
                purGoods.setGbDpgPayType(batch.getGbDpbPayType());
                purGoods.setGbDpgStatus(3);
                purGoods.setGbDpgPurchaseType(21);
                purGoods.setGbDpgPurchaseNxSupplierId(gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
                gbDPGService.update(purGoods);
                orderCount += purGoods.getGbDpgOrdersAmount();
                Integer gbDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
                Map<String, Object> map = new HashMap<>();
                map.put("purGoodsId", gbDistributerPurchaseGoodsId);
                List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);

                for (GbDepartmentOrdersEntity orders : ordersEntities) {
                    BigDecimal gbDoWeight = new BigDecimal(orders.getGbDoWeight());
                    BigDecimal gbDoPrice = new BigDecimal(orders.getGbDoPrice());
                    BigDecimal gbDoSubtotal = new BigDecimal(orders.getGbDoSubtotal());

                    if (gbDoWeight.compareTo(BigDecimal.ZERO) == 1 && gbDoPrice.compareTo(BigDecimal.ZERO) == 1 &&
                            gbDoSubtotal.compareTo(BigDecimal.ZERO) == 1) {

                        if (orders.getGbDoOrderType().equals(getGbOrderTypeTuihuo())) {
                            Integer gbDoDgsrReturnId = orders.getGbDoDgsrReturnId();
                            GbDepartmentGoodsStockReduceEntity reduceEntity = gbDepartmentStockReduceService.queryObject(gbDoDgsrReturnId);
                            reduceEntity.setGbDgsrStatus(2);
                            gbDepartmentStockReduceService.update(reduceEntity);
                        } else {
                            saveDepStockDataByPurchase(orders, gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
                        }

                        orders.setGbDoStatus(getGbOrderStatusReceived()); //wancheng
                        if (batch.getGbDpbPayType() == 0) {
                            orders.setGbDoBuyStatus(getGbOrderBuyStatusHavePayFinish());
                        } else {
                            orders.setGbDoBuyStatus(getGbOrderBuyStatusUnPayFinish());
                        }

                        gbDepartmentOrdersService.update(orders);

                    }


                }
            }

            batch.setGbDpbFinishFullTime(formatFullTime());
            if (batch.getGbDpbPayType() == 0) {
                batch.setGbDpbStatus(4); //如果是现金 status == 4 完成结账状态
            } else {
                batch.setGbDpbStatus(3); //如果是记账，status == 2， 开票完成后，status == 3
//                Integer gbDpbSupplierId = batch.getGbDpbSupplierId();
//                NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.queryObject(gbDpbSupplierId);
//                Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
//                System.out.println("dafdafas" + supplierEntity);
//                if (nxJrdhsUserId == null) {
//                    System.out.println("dafafads" + nxJrdhsUserId);
//                    supplierEntity.setNxJrdhsUserId(batch.getGbDpbSellUserId());
//                    nxJrdhSupplierService.update(supplierEntity);
//                }
            }
            gbDPBService.update(batch);

            Integer gbDpbDistributerId = batch.getGbDpbDistributerId();
            GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(gbDpbDistributerId);

            GbDistributerPayListEntity payListEntity = new GbDistributerPayListEntity();
            payListEntity.setGbNdplPaySubtotal(Integer.valueOf(orderCount).toString());
            payListEntity.setGbNdplPayTime(formatFullTime());
            payListEntity.setGbNdplPayDate(formatWhatDay(0));
            payListEntity.setGbNdplPayMonth(formatWhatMonth(0));
            payListEntity.setGbNdplPayYear(formatWhatYear(0));
            payListEntity.setGbNdplStatus(0);
            payListEntity.setGbNdplType(getGbDisPayBatchFinish());
            payListEntity.setGbNdplRestPoints(gbDistributerEntity.getGbDistributerBuyQuantity());
            payListEntity.setGbNdplGbDisId(gbDistributerEntity.getGbDistributerId());
            payListEntity.setGbNdplNxSupplierId(gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
            payListEntity.setGbNdplGbPbId(gbDistributerPurchaseBatchEntity.getGbDistributerPurchaseBatchId());
            payListEntity.setGbNdplGbDisGoodsId(-1);
            payListService.save(payListEntity);

            BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
            System.out.println("decimdall========" + decimal);
            BigDecimal decimal1 = new BigDecimal(orderCount);
            System.out.println("nxdkkddkdk00" + gbDistributerEntity.getGbDistributerBuyQuantity());
            BigDecimal add = decimal.subtract(decimal1);
            gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
            System.out.println("nxdkkddkdk11" + gbDistributerEntity.getGbDistributerBuyQuantity());
            gbDistributerService.update(gbDistributerEntity);

            return R.ok();

        } else {
            return R.error(-1, "请刷新数据。");
        }
    }


    @ResponseBody
    @RequestMapping(value = "/finishShixianBill", method = RequestMethod.POST)
    public R finishShixianBill(@RequestBody GbDepartmentBillEntity billEntity) {
        System.out.println("billl" + billEntity);
        //转换总金额
        String nxRbTotal = billEntity.getGbDbPayTotal();
        Double aDouble = Double.parseDouble(nxRbTotal) * 100;
        int i = aDouble.intValue();
        String s1 = String.valueOf(i);

        //订单号
        String tradeNo = CommonUtils.generateOutTradeNo();

        //根据 nxDisId 找到支付商家支付参数
//        Integer gbDbIssueNxDisId = billEntity.getGbDbIssueNxDisId();
        Integer gbDbIssueNxDisId = 56;

        //动态获取支付配置
        MyWxLaoduShixianPayConfig config = new MyWxLaoduShixianPayConfig(gbDbIssueNxDisId);
        SortedMap<String, String> params = new TreeMap<>();
        params.put("appid", config.getAppID());
        params.put("mch_id", config.getMchID());
        params.put("nonce_str", CommonUtils.generateUUID());
        params.put("body", "订单支付");
        params.put("out_trade_no", tradeNo);
        params.put("fee_type", "CNY");
        params.put("total_fee", s1);
        params.put("spbill_create_ip", "101.42.222.149");
        params.put("notify_url", "https://grainservice.club:8443/nongxinle/api/gbdistributerpurchasebatch/notifyShixian");
        params.put("trade_type", "JSAPI");
        params.put("openid", billEntity.getGbUserOpenId());

        //map转xml
        try {

            WXPay wxpay = new WXPay(config);
            long time = System.currentTimeMillis();
            String tString = String.valueOf(time / 1000);
            Map<String, String> resp = wxpay.unifiedOrder(params);
            System.out.println(resp);
            SortedMap<String, String> reMap = new TreeMap<>();
            reMap.put("appId", config.getAppID());
            reMap.put("nonceStr", resp.get("nonce_str"));
            reMap.put("package", "prepay_id=" + resp.get("prepay_id"));
            reMap.put("signType", "MD5");
            reMap.put("timeStamp", tString);
            String s = WxPayUtils.creatSign(reMap, config.getKey());
            reMap.put("paySign", s);


            GbDepartmentUserEntity gbDepartmentUserEntity = gbDepartmentUserService.queryDepUserByOpenId(billEntity.getGbUserOpenId());
            billEntity.setGbDbIssueUserId(gbDepartmentUserEntity.getGbDepartmentUserId());
            billEntity.setGbDbWxOutTradeNo(tradeNo);
            gbDepartmentBillService.update(billEntity);


            //setAutoGoods

            return R.ok().put("map", reMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return R.ok();
    }

    @RequestMapping("/notifyShixian")
    public String callBackShixian(HttpServletRequest request, HttpServletResponse response) {
        // System.out.println("微信支付成功,微信发送的callback信息,请注意修改订单信息");
        InputStream is = null;
        try {

            is = request.getInputStream();// 获取请求的流信息(这里是微信发的xml格式所有只能使用流来读)
            String xml = WxPayUtils.InputStream2String(is);
            Map<String, String> notifyMap = WxPayUtils.xmlToMap(xml);// 将微信发的xml转map
            System.out.println("微信返回给回调函数的信息为：" + xml);
            if (notifyMap.get("result_code").equals("SUCCESS")) {
                /*
                 * 以下是自己的业务处理------仅做参考 更新order对应字段/已支付金额/状态码
                 * 更新bill支付状态
                 */
                System.out.println("===notify===回调方法已经被调！！！");
                String ordersSn = notifyMap.get("out_trade_no");// 商户订单号
                GbDepartmentBillEntity gbDepartmentBillEntity = gbDepartmentBillService.queryDepartBillByTradeNo(ordersSn);
                Integer gbDbSetAutoGoods = gbDepartmentBillEntity.getGbDbSetAutoGoods();

                Map<String, Object> map = new HashMap<>();
                map.put("billId", gbDepartmentBillEntity.getGbDepartmentBillId());
                List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(map);
                for (GbDepartmentOrdersEntity gbDepartmentOrdersEntity : ordersEntities) {
                    gbDepartmentOrdersEntity.setGbDoStatus(getGbOrderStatusReceived()); //wancheng
                    gbDepartmentOrdersEntity.setGbDoBuyStatus(getGbOrderBuyStatusHavePayFinish());
                    gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);


                    if (gbDbSetAutoGoods == 1) {
                        Integer gbDoDisGoodsId = gbDepartmentOrdersEntity.getGbDoDisGoodsId();
                        GbDistributerGoodsEntity distributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
                        distributerGoodsEntity.setGbDgGoodsType(5);
                        distributerGoodsEntity.setGbDgNxDistributerId(gbDepartmentOrdersEntity.getGbDoNxDistributerId());
                        distributerGoodsEntity.setGbDgNxDistributerGoodsId(gbDepartmentOrdersEntity.getGbDoNxDistributerGoodsId());
                        gbDistributerGoodsService.update(distributerGoodsEntity);
                    }
                }

                Map<String, Object> mapB = new HashMap<>();
                mapB.put("billId", gbDepartmentBillEntity.getGbDepartmentBillId());
                List<GbDistributerPurchaseBatchEntity> gbDistributerPurchaseBatchEntities = gbDPBService.queryDisPurchaseBatch(mapB);
                if (gbDistributerPurchaseBatchEntities.size() > 0) {
                    for (GbDistributerPurchaseBatchEntity batchEntity : gbDistributerPurchaseBatchEntities) {
                        batchEntity.setGbDpbStatus(3);
                        batchEntity.setGbDpbFinishFullTime(formatFullTime());
                        gbDPBService.update(batchEntity);
                    }
                }


                gbDepartmentBillEntity.setGbDbStatus(getGbDepBillReceiveFinish());
                gbDepartmentBillService.update(gbDepartmentBillEntity);

                NxDepartmentBillEntity billEntity = nxDepartmentBillService.queryItemByGbDepBillId(gbDepartmentBillEntity.getGbDepartmentBillId());
                billEntity.setNxDbStatus(getGbDepBillReceiveFinish());
                nxDepartmentBillService.update(billEntity);

            }

            // 告诉微信服务器收到信息了，不要在调用回调action了========这里很重要回复微信服务器信息用流发送一个xml即可
            response.getWriter().write("<xml><return_code><![CDATA[SUCCESS]]></return_code></xml>");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }


    @RequestMapping(value = "/finishSharePurGoodsBatchIsAuto", method = RequestMethod.POST)
    @ResponseBody
    public R finishSharePurGoodsBatchIsAuto(Integer batchId, Boolean isAuto, Integer payType) {
        GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryObject(batchId);
        Integer gbDpbStatus = gbDistributerPurchaseBatchEntity.getGbDpbStatus();
        if (gbDpbStatus == 2) {
            Map<String, Object> mapB = new HashMap<>();
            mapB.put("batchId", batchId);
            List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(mapB);
            for (GbDistributerPurchaseGoodsEntity purGoods : purchaseGoodsEntities) {

                Integer gbDpgDisGoodsId = purGoods.getGbDpgDisGoodsId();
                if (isAuto) {
                    GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
                    goodsEntity.setGbDgGbSupplierId(gbDistributerPurchaseBatchEntity.getGbDpbSupplierId());
                    goodsEntity.setGbDgGoodsType(21);
                    gbDistributerGoodsService.update(goodsEntity);
                }
            }
            gbDistributerPurchaseBatchEntity.setGbDpbStatus(getGbDisPurchaseBatchDepUserReceiveFinish());
            gbDistributerPurchaseBatchEntity.setGbDpbFinishFullTime(formatFullTime());
            gbDPBService.update(gbDistributerPurchaseBatchEntity);

            return R.ok();

        } else {
            return R.error(-1, "请刷新数据。");
        }

    }


    public R saveDepStockDataByPurchase(@RequestBody GbDepartmentOrdersEntity order, Integer supplierId) {
        System.out.println("upddodididufidfuaisf");
        Integer gbDepartmentOrdersId = order.getGbDepartmentOrdersId();
        Integer gbDoDepDisGoodsId = order.getGbDoDepDisGoodsId();
        GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.queryObject(gbDoDepDisGoodsId);

        GbDepartmentOrdersEntity ordersEntity = gbDepartmentOrdersService.queryObject(gbDepartmentOrdersId);
        Integer gbDoStatus = ordersEntity.getGbDoStatus();
        //判断没有被别人收货
        System.out.println("eeeeeeststttttttt" + gbDoStatus);
//        if (gbDoStatus.equals(getGbOrderStatusHasBill())) {
        //0,修改订单上次价格涨幅
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
        stockEntity.setGbDgsNxSupplierId(supplierId);

        Integer gbDoDisGoodsId = order.getGbDoDisGoodsId();
        GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
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
        stockEntity.setGbDgsLossWeight("0");
        stockEntity.setGbDgsLossSubtotal("0");
        stockEntity.setGbDgsReturnWeight("0");
        stockEntity.setGbDgsReturnSubtotal("0");
        stockEntity.setGbDgsProduceWeight("0");
        stockEntity.setGbDgsProduceSubtotal("0");
        stockEntity.setGbDgsWasteWeight("0");
        stockEntity.setGbDgsWasteSubtotal("0");
        String gbDdgSellingPrice = departmentDisGoodsEntity.getGbDdgSellingPrice();
        if (gbDdgSellingPrice != null && !gbDdgSellingPrice.trim().isEmpty() && new BigDecimal(gbDdgSellingPrice).compareTo(new BigDecimal(0)) == 1) {
            stockEntity.setGbDgsAfterProfitSubtotal("0");
            stockEntity.setGbDgsBetweenPrice("0");
            stockEntity.setGbDgsCostRate("0");
            stockEntity.setGbDgsSellingSubtotal("0");
            stockEntity.setGbDgsProduceSellingSubtotal("0");
            stockEntity.setGbDgsProfitSubtotal("0");
            stockEntity.setGbDgsProfitWeight("0");
            stockEntity.setGbDgsSellingPrice(gbDdgSellingPrice);
        } else {
            stockEntity.setGbDgsSellingPrice("-1");
        }

        // showStandard
        if (departmentDisGoodsEntity.getGbDdgShowStandardId() != -1) {
            String gbDdgShowStandardScale = departmentDisGoodsEntity.getGbDdgShowStandardScale();
            BigDecimal divide = new BigDecimal(order.getGbDoWeight()).divide(new BigDecimal(gbDdgShowStandardScale), 1, BigDecimal.ROUND_HALF_UP);
            stockEntity.setGbDgsRestWeightShowStandard(divide.toString());
            stockEntity.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
        }

        //判断是否有保鲜时间参数
        if (order.getGbDoPurchaseGoodsId() != -1) {
            GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(order.getGbDoPurchaseGoodsId());
            System.out.println("gbDpgWarnFullTimegbDpgWarnFullTime1111" + purchaseGoodsEntity.getGbDpgWasteFullTime());
            String gbDpgWasteFullTime1 = purchaseGoodsEntity.getGbDpgWasteFullTime();
            if (gbDpgWasteFullTime1 != null && !gbDpgWasteFullTime1.trim().isEmpty()) {
                stockEntity.setGbDgsWasteFullTime(purchaseGoodsEntity.getGbDpgWasteFullTime());
                String gbDpgWasteFullTime = purchaseGoodsEntity.getGbDpgWasteFullTime();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                // 设置日期字符串
                // 解析日期字符串为Date对象
                Date dateWaste = null;
                Date dateWarn = null;

                try {
                    if (gbDpgWasteFullTime != null && !gbDpgWasteFullTime.trim().isEmpty()) {
                        dateWaste = dateFormat.parse(gbDpgWasteFullTime);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                // 获取时间戳
                long timestampWaste = 0;
                if (dateWaste != null) {
                    timestampWaste = dateWaste.getTime();
                }
                // 输出时间戳

                System.out.println("gbDpgWarnFullTimegbDpgWarnFullTime22222" + purchaseGoodsEntity.getGbDpgWasteFullTime());

                stockEntity.setGbDgsWasteTimeQuantumName(String.valueOf(timestampWaste));
            }
            //判断是否价格异常商品
            if (purchaseGoodsEntity.getGbDpgDisGoodsPriceId() != null && !purchaseGoodsEntity.getGbDpgDisGoodsPriceId().toString().trim().isEmpty()) {
                GbDistributerGoodsPriceEntity goodsPriceEntity = goodsPriceService.queryObject(purchaseGoodsEntity.getGbDpgDisGoodsPriceId());
                String doWeight = order.getGbDoWeight();
                Integer gbDgpPurWhat = goodsPriceEntity.getGbDgpPurWhat();
                String whatSubtotal = "";
                if (gbDgpPurWhat == 1) {
                    String gbDgpGoodsHighestPrice = goodsPriceEntity.getGbDgpGoodsHighestPrice();
                    String purPrice = goodsPriceEntity.getGbDgpPurPrice();
                    BigDecimal diffPrice = new BigDecimal(purPrice).subtract(new BigDecimal(gbDgpGoodsHighestPrice));
                    BigDecimal subtotal = diffPrice.multiply(new BigDecimal(doWeight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    whatSubtotal = subtotal.toString();
                }
                if (gbDgpPurWhat == -1) {
                    String lowestPrice = goodsPriceEntity.getGbDgpGoodsLowestPrice();
                    String purPrice = goodsPriceEntity.getGbDgpPurPrice();
                    BigDecimal diffPrice = new BigDecimal(purPrice).subtract(new BigDecimal(lowestPrice));
                    BigDecimal subtotal = diffPrice.multiply(new BigDecimal(doWeight)).setScale(1, BigDecimal.ROUND_HALF_UP);
                    whatSubtotal = subtotal.toString();
                }

                //价控最低价的成本
                //实际成本与最低成本的差价
                stockEntity.setGbDgsGbPriceSubtotal(whatSubtotal); // 相差了多少成本
                stockEntity.setGbDgsGbPriceGoodsId(purchaseGoodsEntity.getGbDpgDisGoodsPriceId());
                stockEntity.setGbDgsGbPriceSubtotalScale(goodsPriceEntity.getGbDgpPurScale());
            }

        }


        stockEntity.setGbDgsStatus(0);
        stockEntity.setGbDgsGbDepartmentOrderId(order.getGbDepartmentOrdersId());
        stockEntity.setGbDgsGbGoodsStockId(-1);
        stockEntity.setGbDgsGbFromDepartmentId(order.getGbDoToDepartmentId());
        stockEntity.setGbDgsNxDistributerId(order.getGbDoNxDistributerId());
        stockEntity.setGbDgsReceiveUserId(order.getGbDoReceiveUserId());
        stockEntity.setGbDgsInventoryDate(formatWhatDay(0));
        stockEntity.setGbDgsInventoryWeek(getWeekOfYear(0).toString());
        stockEntity.setGbDgsInventoryMonth(formatWhatMonth(0));
        stockEntity.setGbDgsInventoryYear(formatWhatYear(0));
        stockEntity.setGbDgsStars(5);
        gbDepartmentGoodsStockService.save(stockEntity);


        orderAddDepDisGoods(order, stockEntity, gbDoDepDisGoodsId);
        updateDepGoodsDailyBusiness(stockEntity);

        //2，修改订单状态
        order.setGbDoStatus(getGbOrderStatusReceived());
        gbDepartmentOrdersService.update(order);

        //3，修改送货单收货单子数量
        if (order.getGbDoPurchaseGoodsId() != -1) {
            GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(order.getGbDoPurchaseGoodsId());
            BigDecimal gbPgOrderAmount = new BigDecimal(purchaseGoodsEntity.getGbDpgOrdersAmount());
            BigDecimal gbDbFinishAmount = new BigDecimal(purchaseGoodsEntity.getGbDpgOrdersFinishAmount());
            if (gbDbFinishAmount.add(new BigDecimal(1)).compareTo(gbPgOrderAmount) == 0) {
                purchaseGoodsEntity.setGbDpgOrdersFinishAmount(purchaseGoodsEntity.getGbDpgOrdersAmount());
                purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusWaitReceive());
            } else {
                BigDecimal add = gbDbFinishAmount.add(new BigDecimal(1));
                purchaseGoodsEntity.setGbDpgOrdersFinishAmount(add.intValue());
            }
            gbDPGService.update(purchaseGoodsEntity);
        }

        //3，修改送货单收货单子数量
//            Integer gbDoBillId = order.getGbDoBillId();
//            GbDepartmentBillEntity billEntity = gbDepartmentBillService.queryObject(gbDoBillId);
//            BigDecimal gbDbOrderAmount = new BigDecimal(billEntity.getGbDbOrderAmount());
//            if (gbDbOrderAmount.compareTo(new BigDecimal("1")) == 1) {
//                BigDecimal subtract = gbDbOrderAmount.subtract(new BigDecimal("1"));
//                billEntity.setGbDbOrderAmount(subtract.intValue());
//                gbDepartmentBillService.update(billEntity);
//            } else {
//                billEntity.setGbDbOrderAmount(0);
//                billEntity.setGbDbStatus(0);
//                gbDepartmentBillService.update(billEntity);
//            }
        return R.ok();
//        } else {
//            return R.error(-1, "已经完成收货");
//        }

    }

    private void orderAddDepDisGoods(GbDepartmentOrdersEntity ordersEntity, GbDepartmentGoodsStockEntity
            stockEntity, Integer depDisGoodsId) {

        BigDecimal stockSubtotal = new BigDecimal(stockEntity.getGbDgsSubtotal());
        BigDecimal stockWeight = new BigDecimal(stockEntity.getGbDgsWeight());
        BigDecimal subTotal = new BigDecimal(0);
        BigDecimal weight = new BigDecimal(0);
        GbDepartmentDisGoodsEntity depDisGoodsEntity = gbDepartmentDisGoodsService.queryObject(depDisGoodsId);
        subTotal = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalSubtotal()).add(stockSubtotal);
        weight = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalWeight()).add(stockWeight);
        //updateOrder
        depDisGoodsEntity.setGbDdgOrderDate(formatWhatDay(0));
        depDisGoodsEntity.setGbDdgOrderPrice(ordersEntity.getGbDoPrice());
        depDisGoodsEntity.setGbDdgOrderQuantity(ordersEntity.getGbDoQuantity());
        depDisGoodsEntity.setGbDdgOrderRemark(ordersEntity.getGbDoRemark());
        depDisGoodsEntity.setGbDdgOrderStandard(ordersEntity.getGbDoStandard());
        depDisGoodsEntity.setGbDdgOrderWeight(ordersEntity.getGbDoWeight());
        depDisGoodsEntity.setGbDdgPrintStandard(ordersEntity.getGbDoPrintStandard());

        if (new BigDecimal(depDisGoodsEntity.getGbDdgShowStandardScale()).compareTo(new BigDecimal(0)) == 1) {
            BigDecimal showScale = new BigDecimal(depDisGoodsEntity.getGbDdgShowStandardScale());
            BigDecimal standardWeight = weight.divide(showScale, 1, BigDecimal.ROUND_HALF_UP);
            depDisGoodsEntity.setGbDdgShowStandardWeight(standardWeight.toString());
        }

        depDisGoodsEntity.setGbDdgStockTotalSubtotal(subTotal.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
        depDisGoodsEntity.setGbDdgStockTotalWeight(weight.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
        depDisGoodsEntity.setGbDdgInventoryDate(formatWhatDay(0));
        depDisGoodsEntity.setGbDdgInventoryFullTime(formatWhatFullTime(0));
        gbDepartmentDisGoodsService.update(depDisGoodsEntity);

    }

    private void updateDepGoodsDailyBusiness(GbDepartmentGoodsStockEntity stock) {

        Map<String, Object> map = new HashMap<>();
        map.put("depGoodsId", stock.getGbDgsGbDepDisGoodsId());
        map.put("date", formatWhatDay(0));
        GbDepartmentGoodsDailyEntity depGoodsDailyItem = gbDepGoodsDailyService.queryDepGoodsDailyItem(map);
        if (depGoodsDailyItem != null) {
            BigDecimal weight = new BigDecimal(depGoodsDailyItem.getGbDgdWeight());
            BigDecimal total = new BigDecimal(depGoodsDailyItem.getGbDgdSubtotal());
            BigDecimal restWeight = new BigDecimal(depGoodsDailyItem.getGbDgdRestWeight());
            BigDecimal restSubtotal = new BigDecimal(depGoodsDailyItem.getGbDgdRestSubtotal());
            BigDecimal totalWeight = new BigDecimal(stock.getGbDgsWeight()).add(weight).setScale(1, BigDecimal.ROUND_HALF_UP);
            BigDecimal totalSubtotal = new BigDecimal(stock.getGbDgsSubtotal()).add(total).setScale(1, BigDecimal.ROUND_HALF_UP);
            BigDecimal totalRestWeight = new BigDecimal(stock.getGbDgsWeight()).add(restWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
            BigDecimal totalRestSubtotal = new BigDecimal(stock.getGbDgsSubtotal()).add(restSubtotal).setScale(1, BigDecimal.ROUND_HALF_UP);
            depGoodsDailyItem.setGbDgdWeight(totalWeight.toString());
            depGoodsDailyItem.setGbDgdSubtotal(totalSubtotal.toString());
            depGoodsDailyItem.setGbDgdRestWeight(totalRestWeight.toString());
            depGoodsDailyItem.setGbDgdRestSubtotal(totalRestSubtotal.toString());
            depGoodsDailyItem.setGbDgdSellClearHour("-1");
            depGoodsDailyItem.setGbDgdSellClearMinute("-1");
            depGoodsDailyItem.setGbDgdStatus(0);
            gbDepGoodsDailyService.update(depGoodsDailyItem);

        } else {
            GbDepartmentGoodsDailyEntity dailyEntity = new GbDepartmentGoodsDailyEntity();
            dailyEntity.setGbDgdGbDistributerId(stock.getGbDgsGbDistributerId());
            dailyEntity.setGbDgdGbDepartmentId(stock.getGbDgsGbDepartmentId());
            dailyEntity.setGbDgdGbDepartmentFatherId(stock.getGbDgsGbDepartmentFatherId());
            dailyEntity.setGbDgdGbDisGoodsId(stock.getGbDgsGbDisGoodsId());
            dailyEntity.setGbDgdGbDisGoodsFatherId(stock.getGbDgsGbDisGoodsFatherId());
            dailyEntity.setGbDgdGbDisGoodsGrandId(stock.getGbDgsGbDisGoodsGrandId());
            GbDistributerFatherGoodsEntity fatherGoodsEntity = gbDistributerFatherGoodsService.queryObject(stock.getGbDgsGbDisGoodsGrandId());
            dailyEntity.setGbDgdGbDisGoodsGreatGrandId(fatherGoodsEntity.getGbDfgFathersFatherId());
            dailyEntity.setGbDgdGbDepDisGoodsId(stock.getGbDgsGbDepDisGoodsId());
            dailyEntity.setGbDgdDate(formatWhatDay(0));
            dailyEntity.setGbDgdWeek(getWeekOfYear(0).toString());
            dailyEntity.setGbDgdMonth(formatWhatMonth(0));
            dailyEntity.setGbDgdYear(formatWhatYear(0));
            dailyEntity.setGbDgdDay(getWeek(0));
            dailyEntity.setGbDgdWeight(stock.getGbDgsWeight());
            dailyEntity.setGbDgdRestWeight(stock.getGbDgsWeight());
            dailyEntity.setGbDgdRestSubtotal(stock.getGbDgsSubtotal());
            dailyEntity.setGbDgdSubtotal(stock.getGbDgsSubtotal());
            dailyEntity.setGbDgdProduceWeight("0");
            dailyEntity.setGbDgdProduceSubtotal("0");
            dailyEntity.setGbDgdLossWeight("0");
            dailyEntity.setGbDgdLossSubtotal("0");
            dailyEntity.setGbDgdReturnWeight("0");
            dailyEntity.setGbDgdReturnSubtotal("0");
            dailyEntity.setGbDgdWasteWeight("0");
            dailyEntity.setGbDgdWasteSubtotal("0");
            dailyEntity.setGbDgdSalesSubtotal("0");
            dailyEntity.setGbDgdProfitSubtotal("0");
            dailyEntity.setGbDgdAfterProfitSubtotal("0");
            dailyEntity.setGbDgdSellClearHour("-1");
            dailyEntity.setGbDgdSellClearMinute("-1");
            dailyEntity.setGbDgdLastWeight("0");
            dailyEntity.setGbDgdLastSubtotal("0");
            dailyEntity.setGbDgdLastProduceWeight("0");
            Integer gbDgdGbDisGoodsId = dailyEntity.getGbDgdGbDisGoodsId();
            GbDistributerGoodsEntity distributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDgdGbDisGoodsId);
            if (distributerGoodsEntity.getGbDgControlFresh() == 1) {
                dailyEntity.setGbDgdFreshRate("100");
            } else {
                dailyEntity.setGbDgdFreshRate("0");
            }
            dailyEntity.setGbDgdFullTime(formatFullTime());
            dailyEntity.setGbDgdStatus(0);
            gbDepGoodsDailyService.save(dailyEntity);
        }
    }


    private GbDistributerPurchaseGoodsEntity checkPurGoodsPrice(GbDistributerPurchaseGoodsEntity
                                                                        purchaseGoodsEntity) {
        System.out.println("checkkckGoododopriiddcheckPurGoodsPrice" + purchaseGoodsEntity.getGbDpgDisGoodsId());
        Integer gbDpgDisGoodsId = purchaseGoodsEntity.getGbDpgDisGoodsId();
        BigDecimal buyPrice = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyPrice());
        Integer gbDpgDisGoodsPriceId = purchaseGoodsEntity.getGbDpgDisGoodsPriceId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);

        BigDecimal weight = new BigDecimal(purchaseGoodsEntity.getGbDpgBuyQuantity());
        BigDecimal goodsHighest = new BigDecimal(gbDistributerGoodsEntity.getGbDgGoodsHighestPrice());
        BigDecimal goodsLowest = new BigDecimal(gbDistributerGoodsEntity.getGbDgGoodsLowestPrice());
        String priceTotal = buyPrice.multiply(weight).setScale(1, BigDecimal.ROUND_HALF_UP).toString();

        if (buyPrice.compareTo(goodsHighest) == 1 && purchaseGoodsEntity.getGbDpgBuyQuantity() != null && !purchaseGoodsEntity.getGbDpgBuyQuantity().trim().isEmpty()) { //高于最高价

            BigDecimal higherWhatPrice = buyPrice.subtract(goodsHighest);

            BigDecimal highertotal = higherWhatPrice.multiply(weight).setScale(2, BigDecimal.ROUND_HALF_UP); //高出的单价部分 * 重量
            BigDecimal multiply = higherWhatPrice.divide(goodsHighest, 4, BigDecimal.ROUND_HALF_DOWN); // 高出的单价部门 除以 正常价格
            BigDecimal highestTotal = goodsHighest.multiply(weight).setScale(1, BigDecimal.ROUND_HALF_UP);

            if (gbDpgDisGoodsPriceId != null) {
                GbDistributerGoodsPriceEntity goodsPriceEntity = gbDistributerGoodsPriceService.queryObject(gbDpgDisGoodsPriceId);
                goodsPriceEntity.setGbDgpPurWhat(1);
                goodsPriceEntity.setGbDgpPurPrice(purchaseGoodsEntity.getGbDpgBuyPrice());
                goodsPriceEntity.setGbDgpPurWeight(purchaseGoodsEntity.getGbDpgBuyQuantity());
                goodsPriceEntity.setGbDgpPurScale(multiply.toString());
                goodsPriceEntity.setGbDgpPurWhatTotal(highertotal.toString());
                goodsPriceEntity.setGbDgpGoodsHighestTotal(highestTotal.toString());
                goodsPriceEntity.setGbDgpPurTotal(priceTotal);
                goodsPriceEntity.setGbDgpPurWeight(purchaseGoodsEntity.getGbDpgBuyQuantity());
                goodsPriceEntity.setGbDgpGoodsLowestTotal("0");
                goodsPriceEntity.setGbDgpPurDepartmentId(purchaseGoodsEntity.getGbDpgPurchaseDepartmentId());
                goodsPriceEntity.setGbDgpPurNxDistributerId(purchaseGoodsEntity.getGbDpgPurchaseNxDistributerId());
                goodsPriceEntity.setGbDgpGoodsPrice(gbDistributerGoodsEntity.getGbDgGoodsPrice());
                goodsPriceEntity.setGbDgpGoodsLowestPrice(gbDistributerGoodsEntity.getGbDgGoodsLowestPrice());
                goodsPriceEntity.setGbDgpGoodsHighestPrice(gbDistributerGoodsEntity.getGbDgGoodsHighestPrice());
                gbDistributerGoodsPriceService.update(goodsPriceEntity);
            } else {
                GbDistributerGoodsPriceEntity goodsPriceEntity = new GbDistributerGoodsPriceEntity();
                goodsPriceEntity.setGbDgpGoodsPrice(gbDistributerGoodsEntity.getGbDgGoodsPrice());
                goodsPriceEntity.setGbDgpGoodsLowestPrice(gbDistributerGoodsEntity.getGbDgGoodsLowestPrice());
                goodsPriceEntity.setGbDgpGoodsHighestPrice(gbDistributerGoodsEntity.getGbDgGoodsHighestPrice());
                goodsPriceEntity.setGbDgpDistributerGoodsId(gbDistributerGoodsEntity.getGbDistributerGoodsId());
                goodsPriceEntity.setGbDgpDistributerId(gbDistributerGoodsEntity.getGbDgDistributerId());
                goodsPriceEntity.setGbDgpDfgGoodsFatherId(gbDistributerGoodsEntity.getGbDgDfgGoodsFatherId());
                goodsPriceEntity.setGbDgpPurDate(formatWhatDay(0));
                goodsPriceEntity.setGbDgpPurGoodsId(purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
                goodsPriceEntity.setGbDgpPurUserId(purchaseGoodsEntity.getGbDpgPurUserId());
                goodsPriceEntity.setGbDgpPurWhat(1);
                goodsPriceEntity.setGbDgpPurPrice(purchaseGoodsEntity.getGbDpgBuyPrice());
                goodsPriceEntity.setGbDgpPurWeight(purchaseGoodsEntity.getGbDpgBuyQuantity());
                goodsPriceEntity.setGbDgpPurScale(multiply.toString());
                goodsPriceEntity.setGbDgpPurWhatTotal(highertotal.toString());
                goodsPriceEntity.setGbDgpGoodsHighestTotal(highestTotal.toString());
                goodsPriceEntity.setGbDgpGoodsLowestTotal("0");
                goodsPriceEntity.setGbDgpPurTotal(priceTotal);
                goodsPriceEntity.setGbDgpPurWeight(purchaseGoodsEntity.getGbDpgBuyQuantity());
                goodsPriceEntity.setGbDgpPurDepartmentId(purchaseGoodsEntity.getGbDpgPurchaseDepartmentId());
                goodsPriceEntity.setGbDgpStatus(0);
                goodsPriceEntity.setGbDgpWeek(getWeekOfYear(0).toString());
                goodsPriceEntity.setGbDgpMonth(formatWhatMonth(0));
                goodsPriceEntity.setGbDgpYear(formatWhatYear(0));
                goodsPriceEntity.setGbDgpPurNxDistributerId(gbDistributerGoodsEntity.getGbDgNxDistributerId());
                System.out.println("fdhfehfrekfe" + gbDistributerGoodsEntity.getGbDgNxDistributerId());
                gbDistributerGoodsPriceService.save(goodsPriceEntity);
                purchaseGoodsEntity.setGbDpgDisGoodsPriceId(goodsPriceEntity.getGbDistributerGoodsPriceId());
            }
            gbDPGService.update(purchaseGoodsEntity);
        }

        if (buyPrice.compareTo(goodsLowest) == -1 && purchaseGoodsEntity.getGbDpgBuyQuantity() != null && !purchaseGoodsEntity.getGbDpgBuyQuantity().trim().isEmpty()) { //低于最低价
            BigDecimal lowerWhatPrice = goodsLowest.subtract(buyPrice);
            BigDecimal lowerTotal = lowerWhatPrice.multiply(weight).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal multiply = lowerWhatPrice.divide(goodsLowest, 4, BigDecimal.ROUND_HALF_DOWN);
            BigDecimal lowestTotal = goodsLowest.multiply(weight).setScale(1, BigDecimal.ROUND_HALF_UP);

            if (gbDpgDisGoodsPriceId != null) {
                GbDistributerGoodsPriceEntity goodsPriceEntity = gbDistributerGoodsPriceService.queryObject(gbDpgDisGoodsPriceId);
                goodsPriceEntity.setGbDgpPurWhat(-1);
                goodsPriceEntity.setGbDgpPurPrice(purchaseGoodsEntity.getGbDpgBuyPrice());
                goodsPriceEntity.setGbDgpPurWeight(purchaseGoodsEntity.getGbDpgBuyQuantity());
                goodsPriceEntity.setGbDgpPurScale(multiply.toString());
                goodsPriceEntity.setGbDgpPurWhatTotal(lowerTotal.toString());
                goodsPriceEntity.setGbDgpGoodsLowestTotal(lowestTotal.toString());
                goodsPriceEntity.setGbDgpGoodsHighestTotal("0");
                goodsPriceEntity.setGbDgpPurTotal(priceTotal);
                goodsPriceEntity.setGbDgpPurWeight(purchaseGoodsEntity.getGbDpgBuyQuantity());
                goodsPriceEntity.setGbDgpPurDepartmentId(purchaseGoodsEntity.getGbDpgPurchaseDepartmentId());
                goodsPriceEntity.setGbDgpPurNxDistributerId(purchaseGoodsEntity.getGbDpgPurchaseNxDistributerId());
                goodsPriceEntity.setGbDgpGoodsPrice(gbDistributerGoodsEntity.getGbDgGoodsPrice());
                goodsPriceEntity.setGbDgpGoodsLowestPrice(gbDistributerGoodsEntity.getGbDgGoodsLowestPrice());
                goodsPriceEntity.setGbDgpGoodsHighestPrice(gbDistributerGoodsEntity.getGbDgGoodsHighestPrice());

                gbDistributerGoodsPriceService.update(goodsPriceEntity);
            } else {
                GbDistributerGoodsPriceEntity goodsPriceEntity = new GbDistributerGoodsPriceEntity();
                goodsPriceEntity.setGbDgpGoodsPrice(gbDistributerGoodsEntity.getGbDgGoodsPrice());
                goodsPriceEntity.setGbDgpGoodsLowestPrice(gbDistributerGoodsEntity.getGbDgGoodsLowestPrice());
                goodsPriceEntity.setGbDgpGoodsHighestPrice(gbDistributerGoodsEntity.getGbDgGoodsHighestPrice());
                goodsPriceEntity.setGbDgpDistributerGoodsId(gbDistributerGoodsEntity.getGbDistributerGoodsId());
                goodsPriceEntity.setGbDgpDistributerId(gbDistributerGoodsEntity.getGbDgDistributerId());
                goodsPriceEntity.setGbDgpDfgGoodsFatherId(gbDistributerGoodsEntity.getGbDgDfgGoodsFatherId());
                goodsPriceEntity.setGbDgpPurDate(formatWhatDay(0));
                goodsPriceEntity.setGbDgpPurGoodsId(purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
                goodsPriceEntity.setGbDgpPurUserId(purchaseGoodsEntity.getGbDpgPurUserId());
                goodsPriceEntity.setGbDgpPurWhat(-1);
                goodsPriceEntity.setGbDgpPurPrice(purchaseGoodsEntity.getGbDpgBuyPrice());
                goodsPriceEntity.setGbDgpPurScale(multiply.toString());
                goodsPriceEntity.setGbDgpPurWhatTotal(lowerTotal.toString());
                goodsPriceEntity.setGbDgpGoodsLowestTotal(lowestTotal.toString());
                goodsPriceEntity.setGbDgpPurTotal(priceTotal);
                goodsPriceEntity.setGbDgpPurWeight(purchaseGoodsEntity.getGbDpgBuyQuantity());
                goodsPriceEntity.setGbDgpGoodsHighestTotal("0");
                goodsPriceEntity.setGbDgpPurDepartmentId(purchaseGoodsEntity.getGbDpgPurchaseDepartmentId());
                goodsPriceEntity.setGbDgpStatus(0);
                goodsPriceEntity.setGbDgpWeek(getWeekOfYear(0).toString());
                goodsPriceEntity.setGbDgpMonth(formatWhatMonth(0));
                goodsPriceEntity.setGbDgpYear(formatWhatYear(0));
                goodsPriceEntity.setGbDgpPurNxDistributerId(gbDistributerGoodsEntity.getGbDgNxDistributerId());
                gbDistributerGoodsPriceService.save(goodsPriceEntity);
                purchaseGoodsEntity.setGbDpgDisGoodsPriceId(goodsPriceEntity.getGbDistributerGoodsPriceId());
            }

            gbDPGService.update(purchaseGoodsEntity);
        }

        if (buyPrice.compareTo(goodsHighest) == -1 && buyPrice.compareTo(goodsLowest) == 1) {

            if (gbDpgDisGoodsPriceId != null) {

                gbDistributerGoodsPriceService.delete(gbDpgDisGoodsPriceId);

                purchaseGoodsEntity.setGbDpgBuyPriceReason(null);
                purchaseGoodsEntity.setGbDpgDisGoodsPriceId(null);
                gbDPGService.update(purchaseGoodsEntity);

            }
        }
        if (buyPrice.compareTo(goodsHighest) == 0 || buyPrice.compareTo(goodsLowest) == 0) {
            if (gbDpgDisGoodsPriceId != null) {
                gbDistributerGoodsPriceService.delete(gbDpgDisGoodsPriceId);
                purchaseGoodsEntity.setGbDpgBuyPriceReason(null);
                purchaseGoodsEntity.setGbDpgDisGoodsPriceId(null);
                gbDPGService.update(purchaseGoodsEntity);
            }
        }

        return purchaseGoodsEntity;

    }


//    @RequestMapping(value = "/getGbDisPurchaseGoodsBatch/{batchId}")
//    @ResponseBody
//    public R getGbDisPurchaseGoodsBatch(@PathVariable Integer batchId) {
//
//        GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryBatchWithOrders(batchId);
//
//        if (gbDistributerPurchaseBatchEntity != null) {
//            return R.ok().put("data", gbDistributerPurchaseBatchEntity);
//        } else {
//            return R.error(-1, "没有订单");
//        }
//    }


//    @RequestMapping(value = "/saveGbDisPurBatchBuyUserId", method = RequestMethod.POST)
//    @ResponseBody
//    public R saveGbDisPurBatchBuyUserId(Integer batchId, Integer buyUserId) {
//        GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryObject(batchId);
//        gbDistributerPurchaseBatchEntity.setGbDpbBuyUserId(buyUserId);
//        gbDPBService.update(gbDistributerPurchaseBatchEntity);
//        return R.ok();
//    }


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
        map111.put("gbDis", gbDistributerService.queryObject(disId));
        return R.ok().put("data", map111);
    }

//        @RequestMapping(value = "/supplierGetPurchaseBatch/{supplierId}")
//        @ResponseBody
//        public R supplierGetPurchaseBatch (@PathVariable Integer supplierId){
//            Map<String, Object> map = new HashMap<>();
//            map.put("supplierId", supplierId);
//            map.put("month", formatWhatMonth(0));
//            List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDisPurchaseBatch(map);
//
//            Map<String, Object> map1 = new HashMap<>();
//            map1.put("month", formatWhatMonth(0));
//            map1.put("arr", purchaseBatch);
//
//            //lastMonth
//            Map<String, Object> map2 = new HashMap<>();
//            map2.put("supplierId", supplierId);
//            map2.put("month", getLastMonth());
//            List<GbDistributerPurchaseBatchEntity> purchaseBatch1 = gbDPBService.queryDisPurchaseBatch(map2);
//
//            Map<String, Object> map3 = new HashMap<>();
//            map3.put("month", getLastMonth());
//            map3.put("arr", purchaseBatch1);
//
//            //lastTwoMonth
//            Map<String, Object> map4 = new HashMap<>();
//            map4.put("supplierId", supplierId);
//            map4.put("month", getLastTwoMonth());
//            List<GbDistributerPurchaseBatchEntity> purchaseBatch2 = gbDPBService.queryDisPurchaseBatch(map4);
//
//            Map<String, Object> map5 = new HashMap<>();
//            map5.put("month", getLastTwoMonth());
//            map5.put("arr", purchaseBatch2);
//
//            Map<String, Object> map6 = new HashMap<>();
//            map6.put("supplierId", supplierId);
//            map6.put("status", 4);
//            map6.put("dayuStatus", 1);
//            Integer integer = gbDPBService.queryDisPurchaseBatchCount(map6);
//            BigDecimal subtotal = new BigDecimal(0);
//            if (integer > 0) {
//                Double aDouble = gbDPBService.querySupplierUnSettleSubtotal(map6);
//                subtotal = new BigDecimal(aDouble).setScale(2, BigDecimal.ROUND_HALF_UP);
//            }
//
//            List<Map<String, Object>> result = new ArrayList<>();
//            result.add(map1);
//            result.add(map3);
//            result.add(map5);
//            Map<String, Object> map111 = new HashMap<>();
//            map111.put("month", result);
//            map111.put("unSettle", subtotal);
//            return R.ok().put("data", map111);
//        }

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
        batch.setGbDpbStatus(getGbDisPurchaseBatchHaveRead());
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
                    reduceEntity.setGbDgsrStatus(0);
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


    @RequestMapping(value = "/updateBatchBuyerIdGb", method = RequestMethod.POST)
    @ResponseBody
    public R updateBatchBuyerIdGb(Integer buyerId, Integer batchId, String openId) {
        GbDistributerPurchaseBatchEntity gbDistributerPurchaseBatchEntity = gbDPBService.queryObject(batchId);
        gbDistributerPurchaseBatchEntity.setGbDpbBuyUserId(buyerId);
        gbDistributerPurchaseBatchEntity.setGbDpbStatus(getGbDisPurchaseBatchUnRead());
        gbDistributerPurchaseBatchEntity.setGbDpbBuyUserOpenId(openId);
        gbDPBService.update(gbDistributerPurchaseBatchEntity);
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

    @RequestMapping(value = "/jingjingGetBuyingGoodsGbWithNx/{disId}")
    @ResponseBody
    public R jingjingGetBuyingGoodsGbWithNx(@PathVariable Integer disId) {
        Map<String, Object> mapNx = new HashMap<>();
        mapNx.put("disId", disId);
        mapNx.put("status", 3);
        System.out.println("abbdbdbd" + mapNx);
        List<NxDistributerEntity> batchEntities = gbDPBService.queryDisPurchaseBatchWithNx(mapNx);
        System.out.println("siisisisisisi" + batchEntities.size());

        if (batchEntities.size() > 0) {
            for (NxDistributerEntity distributerEntity : batchEntities) {
                if (distributerEntity.getNxDistributerId() != null) {
                    //
                    Integer nxDisId = distributerEntity.getNxDistributerId();
                    Map<String, Object> map4 = new HashMap<>();
                    map4.put("disId", disId);
                    map4.put("orderDayuBuyStatus", -2);
                    map4.put("orderStatus", 3);
                    map4.put("orderEqualBuyStatus", 1);
                    System.out.println("map44444000000" + map4);
                    List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = gbDPGService.queryPurchaseGoodsWithDetailByParams(map4);

                    Map<String, Object> aaa = aaa(disId, nxDisId);
                    distributerEntity.setGbDistributerPurchaseGoodsEntities(purchaseGoodsEntityList);
                    distributerEntity.setAaa(aaa);

                    Map<String, Object> map = new HashMap<>();
                    map.put("disId", disId);
                    map.put("nxDisId", nxDisId);
                    map.put("equalStatus", getGbDepBillWaitingPay());
                    System.out.println("mappppd" + map);
                    List<GbDepartmentBillEntity> billEntities = gbDepartmentBillService.queryDepartmentBillList(map);
                    List<GbDepartmentBillEntity> resultBillList = new ArrayList<>();
                    if (billEntities.size() > 0) {
                        for (GbDepartmentBillEntity billEntity : billEntities) {
                            Map<String, Object> mapGb = new HashMap<>();
                            mapGb.put("billId", billEntity.getGbDepartmentBillId());
                            List<GbDepartmentEntity> gbDepartmentEntityList = gbDepartmentOrdersService.queryDistributerTodayDepartments(mapGb);
                            billEntity.setOrderDepartments(gbDepartmentEntityList);
                            if (billEntity.getGbDbUserCouponId() == null) {
                                Map<String, Object> mapCou = new HashMap<>();
                                mapCou.put("gbDisId", disId);
                                mapCou.put("equalStatus", 0);
                                List<NxGbDistibuterUserCouponEntity> userCouponEntities = nxGbDistibuterUserCouponService.queryGbCouponListByParams(mapCou);
                                if (userCouponEntities.size() > 0) {
                                    BigDecimal maxYouhuiTotal = BigDecimal.ZERO;
                                    NxGbDistibuterUserCouponEntity bestCoupon = null;
                                    BigDecimal bestShifuTotal = BigDecimal.ZERO;  // 保存应付总额（选中的优惠券对应的金额）
                                    for (NxGbDistibuterUserCouponEntity userCouponEntity : userCouponEntities) {
                                        System.out.println("aaaa" + userCouponEntity.getNxGbDistributerUserCouponId() + "bbbb" + formatWhatDay(0));
                                        Integer startDate = getTwoDaysEarly(userCouponEntity.getNxGducStartDate(), formatWhatDay(0));
                                        Integer stopDate = getTwoDaysEarly(userCouponEntity.getNxGducStopDate(), formatWhatDay(0));
                                        System.out.println("stardete" + startDate + "stopDate" + stopDate);
                                        if (startDate > -1 && stopDate == -1) {

                                            Integer nxGducCouponId = userCouponEntity.getNxGducCouponId();
                                            NxDistributerCouponEntity couponEntity = nxDistributerCouponService.queryObject(nxGducCouponId);
                                            String nxDcPriceGreatGrandId = couponEntity.getNxDcPriceGreatGrandId();
                                            System.out.println("aaaa" + userCouponEntity.getNxGbDistributerUserCouponId() + "bbbb" + formatWhatDay(0));

                                            Map<String, Object> mapR = new HashMap<>();
                                            mapR.put("billId", billEntity.getGbDepartmentBillId());
                                            Double billDouble = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapR);
                                            BigDecimal billTotal = new BigDecimal(billDouble);
                                            BigDecimal greatTotal = new BigDecimal(0);
                                            if (!nxDcPriceGreatGrandId.equals("-1")) {
                                                mapR.put("nxGreatId", nxDcPriceGreatGrandId);
                                                Integer integer = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(mapR);
                                                if (integer > 0) {
                                                    Double greatDouble = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapR);
                                                    greatTotal = new BigDecimal(greatDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
                                                }
                                            } else {
                                                Double greatDouble = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapR);
                                                greatTotal = new BigDecimal(greatDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
                                            }

                                            BigDecimal couponSubtotalPrice = new BigDecimal(couponEntity.getNxDcSubtotalPrice());
                                            BigDecimal youhuiTotal = new BigDecimal("0");
                                            BigDecimal shifuTotal = new BigDecimal(0);
                                            if (greatTotal.compareTo(couponSubtotalPrice) >= 0) {

                                                if (couponEntity.getNxDcType() == 0) {
                                                    youhuiTotal = new BigDecimal(couponEntity.getNxDcPrice());
                                                    shifuTotal = billTotal.subtract(youhuiTotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                                                } else if (couponEntity.getNxDcType() == 1) {
                                                    BigDecimal yihuiScale = new BigDecimal(10).subtract(new BigDecimal(couponEntity.getNxDcPricePercent()));
                                                    BigDecimal mutifiyYouhuiScale = yihuiScale.divide(new BigDecimal(10), 1, BigDecimal.ROUND_HALF_UP);
                                                    youhuiTotal = couponSubtotalPrice.multiply(mutifiyYouhuiScale).setScale(1, BigDecimal.ROUND_HALF_UP);
                                                    shifuTotal = billTotal.subtract(youhuiTotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                                                }

                                                if (youhuiTotal.compareTo(maxYouhuiTotal) > 0) {
                                                    maxYouhuiTotal = youhuiTotal;
                                                    bestCoupon = userCouponEntity;
                                                    bestShifuTotal = shifuTotal;

                                                }
                                            }
                                        }
                                    }

                                    // 遍历完成后，判断是否找到最佳优惠券
                                    if (bestCoupon != null) {
                                        // 通过bestCoupon获取优惠券实体对象（假设之前已经取到couponEntity）
                                        Integer bestCouponId = bestCoupon.getNxGducCouponId();
                                        NxDistributerCouponEntity bestCouponEntity = nxDistributerCouponService.queryObject(bestCouponId);

                                        // 更新账单信息
                                        System.out.println("maxYouhuiTotal" + maxYouhuiTotal);
                                        System.out.println("bestShifuTotal" + bestShifuTotal);
                                        billEntity.setGbDbUserCouponTotal(maxYouhuiTotal.toString());
                                        billEntity.setGbDbPayTotal(bestShifuTotal.toString());
                                        billEntity.setGbDbUserCouponId(bestCoupon.getNxGbDistributerUserCouponId());
                                        billEntity.setNxDistributerCouponEntity(bestCouponEntity);

                                        // 更新优惠券状态
                                        bestCoupon.setNxGducStatus(1);
                                        System.out.println("bestcounpton" + bestCoupon.getNxGbDistributerUserCouponId());
                                        nxGbDistibuterUserCouponService.update(bestCoupon);

                                        System.out.println("选择的最佳优惠券ID：" + bestCoupon.getNxGbDistributerUserCouponId());
                                    } else {
                                        System.out.println("未找到符合条件的优惠券");
                                    }
                                }
                                gbDepartmentBillService.update(billEntity);

                            }

                            resultBillList.add(billEntity);
                        }

                        distributerEntity.setGbDepartmentBillEntities(resultBillList);

                    }

                }
            }
        }

        Map<String, Object> map1 = new HashMap<>();
        map1.put("disId", disId);
        map1.put("status", 3);
        map1.put("equalBuyStatus", 0);
        System.out.println("mapp111" + map1);
        int purCount = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        map1.put("equalBuyStatus", null);
        map1.put("dayuBuyStatus", 0);
        System.out.println("mapp111oneoeneoene" + map1);
        int purCountOne = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);

        Map<String, Object> map3 = new HashMap<>();
        map3.put("arr", batchEntities);
        map3.put("orderAmount", purCount);
        map3.put("wxAmount", purCountOne);
        map3.put("disInfo", gbDistributerService.queryDistributerInfo(disId));
        return R.ok().put("data", map3);
    }


    private Map<String, Object> aaa(Integer gbDisId, Integer nxDisId) {

        Map<String, Object> returnMap = new HashMap<>();
        Map<String, Object> mapCou = new HashMap<>();
        mapCou.put("gbDisId", gbDisId);
        mapCou.put("equalStatus", 0);
        mapCou.put("nxDisId", nxDisId);
        System.out.println("ueccoco" + mapCou);
        BigDecimal maxYouhuiTotal = BigDecimal.ZERO;
        NxGbDistibuterUserCouponEntity bestCoupon = null;
        BigDecimal bestShifuTotal = BigDecimal.ZERO;
        BigDecimal greatTotal = new BigDecimal(0);
        BigDecimal billTotal = new BigDecimal(0);
        List<NxGbDistibuterUserCouponEntity> userCouponEntities = nxGbDistibuterUserCouponService.queryGbCouponListByParams(mapCou);
        if (userCouponEntities.size() > 0) {
            // 保存应付总额（选中的优惠券对应的金额）

            for (NxGbDistibuterUserCouponEntity userCouponEntity : userCouponEntities) {
                System.out.println("aaaa" + userCouponEntity.getNxGbDistributerUserCouponId() + "bbbb" + formatWhatDay(0));
                Integer startDate = getTwoDaysEarly(userCouponEntity.getNxGducStartDate(), formatWhatDay(0));
                Integer stopDate = getTwoDaysEarly(userCouponEntity.getNxGducStopDate(), formatWhatDay(0));
                System.out.println("stardete" + startDate + "stopDate" + stopDate);

                Map<String, Object> mapR = new HashMap<>();
                mapR.put("disId", gbDisId);
                mapR.put("nxDisId", nxDisId);
                mapR.put("status", 3);
                mapR.put("orderType", 5);
                Integer integerT = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(mapR);
                Double billDouble = 0.0;
                if (integerT > 0) {
                    billDouble = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapR);
                }
                System.out.println("doudbdidddddddayuStatus" + billDouble);
                billTotal = new BigDecimal(billDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
                System.out.println("doudbdiddBIKkkkggkg" + billDouble);

                if (startDate > -1 && stopDate == -1) {

                    Integer nxGducCouponId = userCouponEntity.getNxGducCouponId();
                    NxDistributerCouponEntity couponEntity = nxDistributerCouponService.queryObject(nxGducCouponId);
                    String nxDcPriceGreatGrandId = couponEntity.getNxDcPriceGreatGrandId();

                    if (!nxDcPriceGreatGrandId.equals("-1")) {
                        mapR.put("nxGreatId", nxDcPriceGreatGrandId);
                        System.out.println("inddiitd" + mapR);
                        Integer integer = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(mapR);
                        if (integer > 0) {
                            Double greatDouble = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapR);
                            System.out.println("greardouebe" + greatDouble);
                            greatTotal = new BigDecimal(greatDouble).setScale(1, BigDecimal.ROUND_HALF_UP);
                            System.out.println("grearreerrrr" + greatTotal);
                        }

                    }
                    BigDecimal couponSubtotalPrice = new BigDecimal(couponEntity.getNxDcSubtotalPrice());
                    BigDecimal youhuiTotal = new BigDecimal("0");
                    BigDecimal shifuTotal = new BigDecimal(0);
                    if (greatTotal.compareTo(couponSubtotalPrice) >= 0) {

                        if (couponEntity.getNxDcType() == 0) {
                            youhuiTotal = new BigDecimal(couponEntity.getNxDcPrice());
                            System.out.println("billTotal======" + billTotal);
                            System.out.println("youhuiTotal=====" + youhuiTotal);
                            shifuTotal = billTotal.subtract(youhuiTotal).setScale(1, BigDecimal.ROUND_HALF_DOWN);

                            System.out.println("shifuTotal=====" + shifuTotal);
                        } else if (couponEntity.getNxDcType() == 1) {
                            BigDecimal yihuiScale = new BigDecimal(10).subtract(new BigDecimal(couponEntity.getNxDcPricePercent()));
                            BigDecimal mutifiyYouhuiScale = yihuiScale.divide(new BigDecimal(10), 1, BigDecimal.ROUND_HALF_UP);
                            youhuiTotal = couponSubtotalPrice.multiply(mutifiyYouhuiScale).setScale(1, BigDecimal.ROUND_HALF_UP);
                            shifuTotal = billTotal.subtract(youhuiTotal).setScale(1, BigDecimal.ROUND_HALF_UP);

                        }

                        if (youhuiTotal.compareTo(maxYouhuiTotal) > 0) {
                            maxYouhuiTotal = youhuiTotal;
                            bestCoupon = userCouponEntity;
                            bestShifuTotal = shifuTotal;
                        }
                    }
                }
            }

            // 遍历完成后，判断是否找到最佳优惠券
            if (bestCoupon != null) {
                // 更新账单信息
                returnMap.put("maxYouhui", maxYouhuiTotal.toString());
                returnMap.put("payTotal", bestShifuTotal.toString());
                returnMap.put("billTotal", billTotal.toString());
            } else {
                returnMap.put("maxYouhui", -1);
                returnMap.put("payTotal", billTotal.toString());
                returnMap.put("billTotal", billTotal.toString());
            }
        } else {
            Map<String, Object> mapR = new HashMap<>();
            mapR.put("disId", gbDisId);
            mapR.put("nxDisId", nxDisId);
            mapR.put("status", 3);
            mapR.put("orderType", 5);
            mapR.put("subtotal", 0);
            Integer integerT = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(mapR);
            Double billDouble = 0.0;
            if (integerT > 0) {
                billDouble = gbDepartmentOrdersService.queryGbOrdersSubtotal(mapR);
            }
            returnMap.put("maxYouhui", -1);
            returnMap.put("payTotal", 0);
            returnMap.put("billTotal", new BigDecimal(billDouble).setScale(1, BigDecimal.ROUND_HALF_UP));
        }
        return returnMap;
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

    @RequestMapping(value = "/purchaserGetBuyingGoodsGb")
    @ResponseBody
    public R purchaserGetBuyingGoodsGb(Integer userId, Integer disId, Integer userAdmin, Integer depId, Integer
            orderType) {
        Map<String, Object> map = new HashMap<>();
        map.put("purUserId", userId);
        map.put("status", 3);
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatch(map);

        Map<String, Object> map11 = new HashMap<>();
        map11.put("purDepId", depId);
        map11.put("status", 1);
        int count0 = gbDPGService.queryPurchaseGoodsAmount(map11);

        Map<String, Object> map12 = new HashMap<>();
        map12.put("purUserId", userId);
        map12.put("status", 2);
        Integer count1 = gbDPBService.queryDisPurchaseBatchCount(map12);

        Map<String, Object> map13 = new HashMap<>();
        map13.put("purUserId", userId);
        map13.put("equalStatus", 1);
        map13.put("batchId", -1);
        map13.put("weightId", -1);
        Integer count2 = gbDPGService.queryGbPurchaseGoodsCount(map13);

        //
        Map<String, Object> map14 = new HashMap<>();
        map14.put("toDepId", depId);
        map14.put("equalStatus", 2);
        map14.put("orderType", orderType);
        List<GbDepartmentEntity> departmentEntities = gbDepartmentOrdersService.queryDistributerTodayDepartments(map14);
        Map<String, Object> todayData = packageDisOrderByDep(departmentEntities, 0);
        List<GbDepartmentEntity> arr = (List<GbDepartmentEntity>) todayData.get("arr");

        Map<String, Object> map1 = new HashMap<>();
        map1.put("batchArr", batchEntities);
        map1.put("purGoodsAmount", count0);
        map1.put("haoyouAmount", count1);
        map1.put("finishAmount", count2);
        map1.put("deliveryAmount", arr.size());
        return R.ok().put("data", map1);

    }


    private Map<String, Object> packageDisOrderByDep(List<GbDepartmentEntity> departmentEntities, Integer which) {
        Map<String, Object> map = new HashMap<>();
        map.put("week", getWeek(which));
        map.put("hao", getJustHao(which));
        //根据部门是否有子部门组装部门订单
        //1,返回list
        List<Map<String, Object>> dataMap = new ArrayList<>();
        //2,有子部门的父部门
        TreeSet<GbDepartmentEntity> fatherDep = new TreeSet<>();
        //3，type是1的部门
        List<GbDepartmentEntity> subDepList = new ArrayList<>();


        for (GbDepartmentEntity dep : departmentEntities) {

            Integer fatherId = dep.getGbDepartmentFatherId();
            if (fatherId.equals(0)) {
                Map<String, Object> depMap = new HashMap<>();
                depMap.put("depHasSubs", 0);
                depMap.put("depId", dep.getGbDepartmentId());
                depMap.put("depName", dep.getGbDepartmentName());
                depMap.put("arrName", dep.getGbDepartmentAttrName());
                depMap.put("depOrders", dep.getGbDepartmentOrdersEntities());
                depMap.put("orderTotal", dep.getGbDepartmentOrdersEntities().size());
                depMap.put("choiceTotal", dep.getGbDepartmentOrdersEntities().size());
                dataMap.add(depMap);
            } else {
                Integer gbDepartmentFatherId = dep.getGbDepartmentFatherId();
                GbDepartmentEntity departmentEntity1 = gbDepartmentService.queryObject(gbDepartmentFatherId);
                fatherDep.add(departmentEntity1);
                subDepList.add(dep);
            }
        }

        for (GbDepartmentEntity father : fatherDep) {
            Map<String, Object> fatherMap = new HashMap<>();
            fatherMap.put("depHasSubs", 1);
            fatherMap.put("depId", father.getGbDepartmentId());
            fatherMap.put("depName", father.getGbDepartmentName());
            fatherMap.put("arrName", father.getGbDepartmentAttrName());

            List<GbDepartmentEntity> subDeps = new ArrayList<>();
            for (GbDepartmentEntity sub : subDepList) {
                if (father.getGbDepartmentId().equals(sub.getGbDepartmentFatherId())) {
                    subDeps.add(sub);
                }
            }
            fatherMap.put("subDeps", subDeps);
            dataMap.add(fatherMap);
        }
        map.put("arr", dataMap);
        return map;
    }

    /**
     * @param depId 门店和库房和中央厨房都可以
     * @return 采购批次
     */
    @RequestMapping(value = "/getDepartmentBuyingGoodsGb/{depId}")
    @ResponseBody
    public R getDepartmentBuyingGoodsGb(@PathVariable Integer depId) {
        Map<String, Object> map = new HashMap<>();
        map.put("purDepId", depId);
        map.put("status", 2);
        List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDepartmentPurchaseBatch(map);
        return R.ok().put("data", purchaseBatch);
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
    @RequestMapping(value = "/getDisPurchaseGoodsBatchDetail/{batchId}")
    @ResponseBody
    public R getDisPurchaseGoodsBatchDetail(@PathVariable Integer batchId) {

        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("batchId", batchId);
        System.out.println("mapmcansn" + queryMap);
        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
        return R.ok().put("data", gbDistributerGoodsEntities);
    }

    /**
     * 采购员删除订货批次->采购商品->"订单"
     *
     * @param id 订单id
     * @return ok
     */
    @RequestMapping(value = "/deleteDisPurBatchGbOrder/{id}")
    @ResponseBody
    public R deleteDisPurBatchGbOrder(@PathVariable Integer id) {

        GbDepartmentOrdersEntity orders = gbDepartmentOrdersService.queryObject(id);
        System.out.println("stastst" + orders.getGbDoStatus());
        if (orders.getGbDoBuyStatus() < 3) {
            Integer gbDoPurchaseGoodsId = orders.getGbDoPurchaseGoodsId();
            GbDistributerPurchaseGoodsEntity purGoods = gbDPGService.queryObject(gbDoPurchaseGoodsId);

            //如果只有一个采购商品，删除采购批次
            Integer gbDpgBatchId = purGoods.getGbDpgBatchId();
            Map<String, Object> map1 = new HashMap<>();
            map1.put("batchId", gbDpgBatchId);
            List<GbDistributerPurchaseGoodsEntity> goodsEntities = gbDPGService.queryOnlyPurGoods(map1);
            if (goodsEntities.size() == 1) {
                Integer purchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
                Map<String, Object> map = new HashMap<>();
                map.put("purGoodsId", purchaseGoodsId);
                List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
                if (ordersEntities.size() == 1) {
                    gbDPBService.delete(gbDpgBatchId);
                }
            }


            Integer gbDpgOrdersAmount = purGoods.getGbDpgOrdersAmount();
            if (gbDpgOrdersAmount == 1) {
                Integer gbDpgDisGoodsPriceId = purGoods.getGbDpgDisGoodsPriceId();
                if (gbDpgDisGoodsPriceId != null) {
                    gbDistributerGoodsPriceService.delete(gbDpgDisGoodsPriceId);
                }
                purGoods.setGbDpgPurUserId(null);
                purGoods.setGbDpgStatus(0);
                purGoods.setGbDpgTime(null);
                purGoods.setGbDpgBuySubtotal(null);
                purGoods.setGbDpgBuyPrice(null);
                purGoods.setGbDpgBuyQuantity(null);
                purGoods.setGbDpgBuyScalePrice(null);
                purGoods.setGbDpgBuyScaleQuantity(null);
                purGoods.setGbDpgPurchaseFullTime(null);
                purGoods.setGbDpgPurchaseMonth(null);
                purGoods.setGbDpgPurchaseYear(null);
                purGoods.setGbDpgPurchaseWeek(null);
                purGoods.setGbDpgPurchaseWeekYear(null);
                purGoods.setGbDpgBatchId(null);
                gbDPGService.update(purGoods);
            } else {

                BigDecimal purQuantity = new BigDecimal(purGoods.getGbDpgQuantity());
                BigDecimal orderQuantity = new BigDecimal(orders.getGbDoQuantity());
                BigDecimal result = purQuantity.subtract(orderQuantity).setScale(2, BigDecimal.ROUND_HALF_UP);
                purGoods.setGbDpgQuantity(result.toString());
                purGoods.setGbDpgOrdersAmount(purGoods.getGbDpgOrdersAmount() - 1);
                gbDPGService.update(purGoods);

                //新添加一个采购商品
                GbDistributerPurchaseGoodsEntity disGoods = new GbDistributerPurchaseGoodsEntity();
                disGoods.setGbDpgDisGoodsFatherId(orders.getGbDoDisGoodsFatherId());
                disGoods.setGbDpgDisGoodsId(orders.getGbDoDisGoodsId());
                disGoods.setGbDpgDistributerId(orders.getGbDoDistributerId());
                disGoods.setGbDpgApplyDate(formatWhatDay(0));
                disGoods.setGbDpgStatus(0);
                disGoods.setGbDpgOrdersAmount(1);
                disGoods.setGbDpgOrdersFinishAmount(0);
                disGoods.setGbDpgOrdersWeightAmount(0);
                disGoods.setGbDpgOrdersBillAmount(0);
                disGoods.setGbDpgStandard(orders.getGbDoStandard());
                disGoods.setGbDpgQuantity(orders.getGbDoQuantity());
                disGoods.setGbDpgBuyScale(orders.getGbDoDsStandardScale());
                disGoods.setGbDpgPurchaseDepartmentId(orders.getGbDoDepartmentFatherId());
                disGoods.setGbDpgPurchaseNxDistributerId(orders.getGbDoNxDistributerId());
                disGoods.setGbDpgPurchaseType(1);
                disGoods.setGbDpgPurchaseWeek(getWeek(0));
                disGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
                disGoods.setGbDpgIsCheck(0);
                gbDPGService.save(disGoods);
                Integer gbDistributerPurchaseGoodsId = disGoods.getGbDistributerPurchaseGoodsId();
                orders.setGbDoPurchaseGoodsId(gbDistributerPurchaseGoodsId);

            }
            orders.setGbDoBuyStatus(getGbOrderBuyStatusNew());
            orders.setGbDoWeight("0.0");
            orders.setGbDoPrice("0.0");
            orders.setGbDoSubtotal("0.0");
            orders.setGbDoScalePrice("0.0");
            orders.setGbDoScaleWeight("0.0");
            orders.setGbDoStatus(getGbOrderStatusNew());
            orders.setGbDoPurchaseUserId(null);
            gbDepartmentOrdersService.update(orders);

            return R.ok();

        } else {
            System.out.println("wieeieieb请刷新数据请刷新数据请刷新数据");
            return R.error(-1, "请刷新数据");
        }


    }


    /**
     * 删除订货批次->"采购商品"
     *
     * @param id 采购商品id
     * @return ok
     */
    @RequestMapping(value = "/supplierInitWeightPurItem/{id}")
    @ResponseBody
    public R supplierInitWeightPurItem(@PathVariable Integer id) {

        GbDistributerPurchaseGoodsEntity purGoods = gbDPGService.queryObject(id);
        purGoods.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
        purGoods.setGbDpgOrdersWeightAmount(0);
        gbDPGService.update(purGoods);

        Map<String, Object> map = new HashMap<>();
        map.put("purGoodsId", id);
        List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
        for (GbDepartmentOrdersEntity orders : ordersEntities) {
            orders.setGbDoStatus(getGbOrderStatusNew());
            orders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
            gbDepartmentOrdersService.update(orders);
        }

        return R.ok();

    }


    /**
     * 删除订货批次->"采购商品"
     *
     * @param id 采购商品id
     * @return ok
     */
    @RequestMapping(value = "/supplierDeleteDisPurBatchGbItem/{id}")
    @ResponseBody
    public R supplierDeleteDisPurBatchGbItem(@PathVariable Integer id) {

        GbDistributerPurchaseGoodsEntity purGoods = gbDPGService.queryObject(id);
        Integer gbDpgBatchId = purGoods.getGbDpgBatchId();
        Integer gbDpgPurchaseNxSupplierId = purGoods.getGbDpgPurchaseNxSupplierId();
        NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.queryObject(gbDpgPurchaseNxSupplierId);
        Integer gbDpgDistributerId = purGoods.getGbDpgDistributerId();
        GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(gbDpgDistributerId);
        Integer gbDpgDisGoodsId = purGoods.getGbDpgDisGoodsId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);

        purGoods.setGbDpgPurchaseType(2);
        purGoods.setGbDpgBatchId(null);
        purGoods.setGbDpgStatus(0);
        purGoods.setGbDpgTime(null);
        purGoods.setGbDpgBuySubtotal("0.0");
        purGoods.setGbDpgBuyPrice(null);
        purGoods.setGbDpgBuyScalePrice(null);
        purGoods.setGbDpgBuyScaleQuantity(null);
        purGoods.setGbDpgPurchaseFullTime(null);
        purGoods.setGbDpgPurchaseMonth(null);
        purGoods.setGbDpgPurchaseYear(null);
        purGoods.setGbDpgPurchaseWeek(null);
        purGoods.setGbDpgPurchaseWeekYear(null);
        purGoods.setGbDpgPurchaseNxDistributerId(-1);
        purGoods.setGbDpgPurchaseNxSupplierId(-1);
        purGoods.setGbDpgWasteFullTime(null);
        purGoods.setGbDpgWarnFullTime(null);
        purGoods.setGbDpgOrdersFinishAmount(0);
        purGoods.setGbDpgOrdersWeightAmount(0);
        gbDPGService.update(purGoods);

        Map<String, Object> map = new HashMap<>();
        map.put("purGoodsId", id);
        List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
        for (GbDepartmentOrdersEntity orders : ordersEntities) {
            orders.setGbDoStatus(getGbOrderStatusNew());
            orders.setGbDoBuyStatus(getGbOrderBuyStatusNew());
            orders.setGbDoWeight("0.0");
            orders.setGbDoPrice("0.0");
            orders.setGbDoSubtotal("0.0");
            orders.setGbDoScalePrice("0.0");
            orders.setGbDoScaleWeight("0.0");
            orders.setGbDoPurchaseUserId(null);
            orders.setGbDoOrderType(2);
            orders.setGbDoGoodsType(2);
            orders.setGbDoNxDistributerGoodsId(-1);
            orders.setGbDoNxDistributerId(-1);
            orders.setGbDoNxDepartmentOrderId(null);
            gbDepartmentOrdersService.update(orders);
        }


        Map<String, Object> map1 = new HashMap<>();
        map1.put("batchId", gbDpgBatchId);
        List<GbDistributerPurchaseGoodsEntity> goodsEntities = gbDPGService.queryOnlyPurGoods(map1);
        if (goodsEntities.size() == 0) {
            gbDPBService.delete(gbDpgBatchId);
        } else {
            map1.put("dayuStatus", 1);
            Integer integer = gbDPGService.queryGbPurchaseGoodsCount(map1);
            Double subTotal = 0.0;
            BigDecimal lasttotal = new BigDecimal(0);
            if (integer > 0) {
                subTotal = gbDPGService.queryPurchaseGoodsSubTotal(map1);
                lasttotal = new BigDecimal(subTotal).subtract(new BigDecimal(purGoods.getGbDpgBuySubtotal())).setScale(1, BigDecimal.ROUND_HALF_UP);
            }
            GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryObject(gbDpgBatchId);
            batchEntity.setGbDpbSubtotal(lasttotal.toString());
            gbDPBService.update(batchEntity);
        }

        System.out.println("weisisiissisisiissiisissiiiiis");

        Integer nxJrdhsNxJrdhBuyUserId = supplierEntity.getNxJrdhsNxJrdhBuyUserId();
        GbDepartmentUserEntity departmentUserEntity = gbDepartmentUserService.queryObject(nxJrdhsNxJrdhBuyUserId);

        Map<String, TemplateData> mapNotice = new HashMap<>();
        mapNotice.put("date7", new TemplateData(formatWhatDayTime(0)));
        mapNotice.put("thing12", new TemplateData(supplierEntity.getNxJrdhsSupplierName() + "删除" + gbDistributerGoodsEntity.getGbDgGoodsName()));
        if (goodsEntities.size() == 1) {
            mapNotice.put("phrase9", new TemplateData("订单取消"));
        } else {
            mapNotice.put("phrase9", new TemplateData("订单变更"));
        }

        StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbOrderBatch/gbOrderBatch");
        pathBuilder.append("?batchId=").append(gbDpgBatchId);
        pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
        pathBuilder.append("&disId=").append(gbDistributerEntity.getGbDistributerId());
        pathBuilder.append("&buyUserId=").append(nxJrdhsNxJrdhBuyUserId);
        pathBuilder.append("&fromBuyer=1");
        pathBuilder.append("&depId=").append(supplierEntity.getNxJrdhsGbDepartmentId());

        String path = pathBuilder.toString();
        System.out.println("Encoded URLARRRRRRSSS: " + path);
        WeNoticeService.changeOrderSuppliertixingMessageJj(departmentUserEntity.getGbDuWxOpenId(), path, mapNotice);


        return R.ok();

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
        GbDistributerPurchaseGoodsEntity purGoods = gbDPGService.queryObject(id);
        Integer gbDpgDisGoodsId = purGoods.getGbDpgDisGoodsId();
        Integer oldSupplierId = purGoods.getGbDpgPurchaseNxSupplierId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
        int count = -1;

        System.out.println("eqklqlqlq" + purGoods.getGbDpgOrdersAmount() + "fiins" + purGoods.getGbDpgOrdersFinishAmount());
        if (purGoods.getGbDpgOrdersAmount() != purGoods.getGbDpgOrdersFinishAmount()) {
            Integer gbDpgBatchId = purGoods.getGbDpgBatchId();
            Map<String, Object> map1 = new HashMap<>();
            map1.put("batchId", gbDpgBatchId);
            System.out.println("bahccmcmamappap" + map1);
            count = gbDPGService.queryGbGoodsCount(map1);
            if (count == 1) {
                gbDPBService.delete(gbDpgBatchId);
            } else {
                System.out.println("mapsusb" + map1);
                map1.put("dayuStatus", 1);
                Integer integer = gbDPGService.queryGbPurchaseGoodsCount(map1);
                Double subTotal = 0.0;
                BigDecimal lasttotal = new BigDecimal(0);
                if (integer > 0) {
                    subTotal = gbDPGService.queryPurchaseGoodsSubTotal(map1);
                    lasttotal = new BigDecimal(subTotal).subtract(new BigDecimal(purGoods.getGbDpgBuySubtotal())).setScale(1, BigDecimal.ROUND_HALF_UP);
                }
                GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryObject(gbDpgBatchId);
                batchEntity.setGbDpbSubtotal(lasttotal.toString());
                gbDPBService.update(batchEntity);
            }
            Integer gbDpgDisGoodsPriceId = purGoods.getGbDpgDisGoodsPriceId();
            if (gbDpgDisGoodsPriceId != null) {
                gbDistributerGoodsPriceService.delete(gbDpgDisGoodsPriceId);
            }
            purGoods.setGbDpgPurchaseType(2);
            purGoods.setGbDpgDisGoodsPriceId(null);
            purGoods.setGbDpgBatchId(null);
            purGoods.setGbDpgPurUserId(null);
            purGoods.setGbDpgStatus(0);
            purGoods.setGbDpgTime(null);
            purGoods.setGbDpgBuySubtotal(null);
            purGoods.setGbDpgBuyPrice(null);
            purGoods.setGbDpgBuyQuantity(null);
            purGoods.setGbDpgBuyScalePrice(null);
            purGoods.setGbDpgBuyScaleQuantity(null);
            purGoods.setGbDpgPurchaseFullTime(null);
            purGoods.setGbDpgPurchaseMonth(null);
            purGoods.setGbDpgPurchaseYear(null);
            purGoods.setGbDpgPurchaseWeek(null);
            purGoods.setGbDpgPurchaseWeekYear(null);
            purGoods.setGbDpgPurchaseNxDistributerId(-1);
            purGoods.setGbDpgPurchaseNxSupplierId(-1);
            purGoods.setGbDpgWasteFullTime(null);
            purGoods.setGbDpgWarnFullTime(null);
            purGoods.setGbDpgOrdersFinishAmount(0);
            purGoods.setGbDpgOrdersWeightAmount(0);
            System.out.println("purururruururur" + purGoods.getGbDistributerPurchaseGoodsId());
            gbDPGService.update(purGoods);

            gbDistributerGoodsEntity.setGbDgGbSupplierId(-1);
            gbDistributerGoodsEntity.setGbDgNxDistributerId(-1);
            gbDistributerGoodsEntity.setGbDgNxDistributerGoodsId(-1);
            gbDistributerGoodsEntity.setGbDgGoodsType(2);
            gbDistributerGoodsService.update(gbDistributerGoodsEntity);

            Integer nxDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", nxDistributerPurchaseGoodsId);
            List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            for (GbDepartmentOrdersEntity orders : ordersEntities) {
                if (orders.getGbDoNxDepartmentOrderId() != null && orders.getGbDoNxDepartmentOrderId() != -1) {
                    Integer gbDoNxDepartmentOrderId = orders.getGbDoNxDepartmentOrderId();
                    NxDepartmentOrdersEntity ordersEntity1 = nxDepartmentOrdersService.queryObject(gbDoNxDepartmentOrderId);
                    if (ordersEntity1 != null) {
                        System.out.println("nxnxnxnxxxoxoxxoxoxoxo" + ordersEntity1.getNxDepartmentOrdersId());
                        delNxPurGoods(ordersEntity1);
                        nxDepartmentOrdersService.delete(ordersEntity1.getNxDepartmentOrdersId());
                    }
                }

                orders.setGbDoBuyStatus(getGbOrderBuyStatusNew());
                orders.setGbDoWeight("0.0");
                orders.setGbDoPrice("0.0");
                orders.setGbDoSubtotal("0.0");
                orders.setGbDoScalePrice("0.0");
                orders.setGbDoScaleWeight("0.0");
                orders.setGbDoStatus(getGbOrderStatusNew());
                orders.setGbDoPurchaseUserId(null);
                orders.setGbDoOrderType(2);
                orders.setGbDoGoodsType(2);
                orders.setGbDoNxDistributerGoodsId(-1);
                orders.setGbDoNxDistributerId(-1);
                orders.setGbDoNxDepartmentOrderId(null);
                gbDepartmentOrdersService.update(orders);
            }

            if (oldSupplierId != -1) {
                NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.queryObject(oldSupplierId);
                Integer jrdhsUserId = supplierEntity.getNxJrdhsUserId();
                NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(jrdhsUserId);
                Integer gbDpgDistributerId = purGoods.getGbDpgDistributerId();
                GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(gbDpgDistributerId);
                System.out.println("tuuihuouonoticeeiee");
                if (supplierEntity.getNxJrdhsUserId() != null) {
                    Map<String, TemplateData> mapNotice = new HashMap<>();
                    mapNotice.put("date7", new TemplateData(formatWhatDayTime(0)));
                    mapNotice.put("thing12", new TemplateData("删除订货" + gbDistributerGoodsEntity.getGbDgGoodsName()));
                    if (count == 1) {
                        mapNotice.put("phrase9", new TemplateData("订单取消"));
                    } else {
                        mapNotice.put("phrase9", new TemplateData("订单变更"));
                    }

                    StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbReceiveBatch/gbReceiveBatch");
                    pathBuilder.append("?batchId=").append(gbDpgBatchId);
                    pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
                    pathBuilder.append("&from=notification"); // 添加这个参数
                    String path = pathBuilder.toString();
                    WeNoticeService.changeOrderSuppliertixingMessageJj(nxJrdhUserEntity.getNxJrdhWxOpenId(), path, mapNotice);
                }

            }
            return R.ok();

        } else {
            return R.error(-1, "请刷新数据");
        }
    }


    private void delNxPurGoods(NxDepartmentOrdersEntity ordersEntity) {
//
//                && ordersEntity.getNxDoPurchaseStatus() < getNxDepOrderBuyStatusFinishPurchase()

        if (ordersEntity.getNxDoPurchaseGoodsId() != null && ordersEntity.getNxDoPurchaseGoodsId() != -1) {
            NxDistributerPurchaseGoodsEntity nxDistributerPurchaseGoodsEntity = nxDistributerPurchaseGoodsService.queryObject(ordersEntity.getNxDoPurchaseGoodsId());
            if (nxDistributerPurchaseGoodsEntity != null) {
                Integer nxDpgOrdersAmount = nxDistributerPurchaseGoodsEntity.getNxDpgOrdersAmount();
                System.out.println("purgoodoamaomm" + nxDpgOrdersAmount);
                if (nxDpgOrdersAmount > 1) {
                    nxDistributerPurchaseGoodsEntity.setNxDpgOrdersAmount(nxDpgOrdersAmount - 1);
                    BigDecimal newWeight = new BigDecimal(nxDistributerPurchaseGoodsEntity.getNxDpgQuantity()).subtract(new BigDecimal(ordersEntity.getNxDoQuantity()));
                    nxDistributerPurchaseGoodsEntity.setNxDpgBuyQuantity(newWeight.toString());
                    nxDistributerPurchaseGoodsEntity.setNxDpgQuantity(newWeight.toString());
                    System.out.println("purgoodoamaomm111" + nxDistributerPurchaseGoodsEntity.getNxDpgBuyQuantity());

                    if (ordersEntity.getNxDoStandard().equals(nxDistributerPurchaseGoodsEntity.getNxDpgStandard())) {
                        System.out.println("purgoodoamaomm" + nxDpgOrdersAmount);
                        BigDecimal price = new BigDecimal(nxDistributerPurchaseGoodsEntity.getNxDpgBuyPrice());
                        BigDecimal decimal1 = newWeight.multiply(price).setScale(1, BigDecimal.ROUND_HALF_UP);
                        nxDistributerPurchaseGoodsEntity.setNxDpgBuySubtotal(decimal1.toString());
                    }
                    nxDistributerPurchaseGoodsService.update(nxDistributerPurchaseGoodsEntity);
                } else {
                    if (nxDistributerPurchaseGoodsEntity.getNxDpgBatchId() != null) {
                        Integer nxDpgBatchId = nxDistributerPurchaseGoodsEntity.getNxDpgBatchId();
                        List<NxDistributerPurchaseGoodsEntity> purchaseGoodsEntities = nxDistributerPurchaseGoodsService.queryPurchaseGoodsByBatchId(nxDpgBatchId);

                        if (purchaseGoodsEntities.size() == 1) {
                            nxDPBService.delete(nxDpgBatchId);
                        } else {
                            String nxDpgBuySubtotal = nxDistributerPurchaseGoodsEntity.getNxDpgBuySubtotal();
                            NxDistributerPurchaseBatchEntity nxDistributerPurchaseBatchEntity = nxDPBService.queryObject(nxDpgBatchId);
                            if (nxDistributerPurchaseBatchEntity.getNxDpbSellSubtotal() != null && !nxDistributerPurchaseBatchEntity.getNxDpbSellSubtotal().trim().isEmpty()) {
                                BigDecimal decimal = new BigDecimal(nxDistributerPurchaseBatchEntity.getNxDpbSellSubtotal());
                                BigDecimal decimal2 = new BigDecimal(nxDpgBuySubtotal);
                                BigDecimal decimal1 = decimal.subtract(decimal2).setScale(1, BigDecimal.ROUND_HALF_UP);
                                nxDistributerPurchaseBatchEntity.setNxDpbSellSubtotal(decimal1.toString());
                                nxDPBService.update(nxDistributerPurchaseBatchEntity);
                            }
                        }

                    }

                    nxDistributerPurchaseGoodsService.delete(nxDistributerPurchaseGoodsEntity.getNxDistributerPurchaseGoodsId());
                }
            }
        }
    }

    /**
     * 采购员删除采购批次
     *
     * @param batchId
     * @return
     */
    @RequestMapping(value = "/deleteDisBatchGb/{batchId}")
    @ResponseBody
    public R deleteDisBatchGb(@PathVariable Integer batchId) {

        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryBatchWithOrders(batchId);
        for (GbDistributerPurchaseGoodsEntity purGoods : batchEntity.getGbDPGEntities()) {
            Integer gbDpgDisGoodsPriceId = purGoods.getGbDpgDisGoodsPriceId();
            if (gbDpgDisGoodsPriceId != null) {
                gbDistributerGoodsPriceService.delete(gbDpgDisGoodsPriceId);
            }
            purGoods.setGbDpgPurchaseDate(null);
            purGoods.setGbDpgPurchaseMonth(null);
            purGoods.setGbDpgPurchaseYear(null);
            purGoods.setGbDpgPurchaseFullTime(null);
            purGoods.setGbDpgPurchaseWeek(null);
            purGoods.setGbDpgPurchaseWeekYear(null);
            purGoods.setGbDpgPurchaseDepartmentId(null);
            purGoods.setGbDpgTime(null);
            purGoods.setGbDpgBatchId(null);
            purGoods.setGbDpgBuyPrice(null);
            purGoods.setGbDpgStatus(0);
            purGoods.setGbDpgPurchaseType(2);
            purGoods.setGbDpgDisGoodsPriceId(null);
            purGoods.setGbDpgBuySubtotal(null);
            purGoods.setGbDpgBuyPrice(null);
            purGoods.setGbDpgBuyQuantity(null);
            purGoods.setGbDpgBuyScalePrice(null);
            purGoods.setGbDpgBuyScaleQuantity(null);
            purGoods.setGbDpgPurUserId(null);
            gbDPGService.update(purGoods);

            Integer gbDoDisGoodsId = purGoods.getGbDpgDisGoodsId();
            GbDistributerGoodsEntity goodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
            goodsEntity.setGbDgGbSupplierId(-1);
            goodsEntity.setGbDgNxDistributerId(-1);
            goodsEntity.setGbDgNxDistributerGoodsId(-1);
            gbDistributerGoodsService.update(goodsEntity);

            Integer gbDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", gbDistributerPurchaseGoodsId);
            List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            for (GbDepartmentOrdersEntity orders : ordersEntities) {
                orders.setGbDoBuyStatus(0);
                orders.setGbDoStatus(getGbOrderStatusNew());
                orders.setGbDoWeight(null);
                orders.setGbDoSubtotal("0");
                orders.setGbDoPrice(null);
                orders.setGbDoScalePrice(null);
                orders.setGbDoScaleWeight(null);
                orders.setGbDoPurchaseUserId(null);
                gbDepartmentOrdersService.update(orders);
            }
        }

        gbDPBService.delete(batchId);
        return R.ok();
    }


    @RequestMapping(value = "/saveDisPurGoodsBatchGbSupplier", method = RequestMethod.POST)
    @ResponseBody
    public R saveDisPurGoodsBatchGbSupplier(@RequestBody GbDistributerPurchaseBatchEntity batchEntity) {
        Integer nxDisId = batchEntity.getGbDpbNxDistributerId();
        Integer gbDpbSupplierId = batchEntity.getGbDpbSupplierId();

        NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.queryObject(batchEntity.getGbDpbSupplierId());
        Map<String, Object> map = new HashMap<>();
        map.put("disId", batchEntity.getGbDpbDistributerId());
        map.put("supplierId", batchEntity.getGbDpbSupplierId());
        map.put("status", 1);
        map.put("notEqualPurchaseType", 9);
        System.out.println("mapapmaaapa" + map);
        List<GbDistributerPurchaseBatchEntity> entities = gbDPBService.queryDisPurchaseBatch(map);
        System.out.println("enennensisiziizizi" + entities.size());
        Integer gbDistributerPurchaseBatchId = 0;
        try {
            if (entities.size() == 0) {
                batchEntity.setGbDpbDate(formatWhatDay(0));
                batchEntity.setGbDpbHour(formatWhatHour(0));
                batchEntity.setGbDpbMinute(formatWhatMinute(0));
                batchEntity.setGbDpbTime(formatWhatTime(0));
                batchEntity.setGbDpbPurchaseMonth(formatWhatMonth(0));
                batchEntity.setGbDpbPurchaseWeek(getWeek(0));
                batchEntity.setGbDpbPurchaseYear(formatWhatYear(0));
                batchEntity.setGbDpbPurchaseFullTime(formatWhatYearDayTime(0));
                batchEntity.setGbDpbStatus(getGbDisPurchaseBatchHaveRead());
                batchEntity.setGbDpbSellUserId(supplierEntity.getNxJrdhsUserId());
                if (nxDisId != -1) {
                    batchEntity.setGbDpbPurchaseType(5);
                }
                gbDPBService.save(batchEntity);

                gbDistributerPurchaseBatchId = batchEntity.getGbDistributerPurchaseBatchId();

                for (GbDistributerPurchaseGoodsEntity gbPurGoods : batchEntity.getGbDPGEntities()) {

                    Integer gbDpgDisGoodsId = gbPurGoods.getGbDpgDisGoodsId();
                    GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
                    List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities = gbPurGoods.getGbDistributerGoodsEntity().getGbDepartmentOrdersEntities();
                    List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();

                    Map<String, Object> mapItem = new HashMap<>();
                    mapItem.put("disGoodsId", gbPurGoods.getGbDpgDisGoodsId());
                    mapItem.put("supplierId", gbDpbSupplierId);
                    mapItem.put("dayuStatus", 2);
                    GbDistributerPurchaseGoodsEntity lastItem = gbDPGService.queryPurchaseGoodsLastItem(mapItem);
                    GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(gbPurGoods.getGbDistributerPurchaseGoodsId());

                    //purGoods
                    BigDecimal buyWeight = new BigDecimal(0);
                    BigDecimal buySubtotal = new BigDecimal(0);
                    BigDecimal weightTotal = new BigDecimal(0);
                    for (GbDepartmentOrdersEntity gbDepartmentOrders : nxDepartmentOrdersEntities) {
                        Boolean hasChoice = gbDepartmentOrders.getIsNotice();
                        if (hasChoice) {
                            GbDepartmentOrdersEntity updateOrders = gbDepartmentOrdersService.queryObject(gbDepartmentOrders.getGbDepartmentOrdersId());
                            weightTotal = weightTotal.add(new BigDecimal(gbDepartmentOrders.getGbDoQuantity()));
                            updateOrders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
                            //
                            if (lastItem != null) {
                                purchaseGoodsEntity.setGbDpgBuyPrice(lastItem.getGbDpgBuyPrice());
                                updateOrders.setGbDoPrice(lastItem.getGbDpgBuyPrice());
                                if (gbDistributerGoodsEntity.getGbDgGoodsStandardname().equals(updateOrders.getGbDoStandard())) {
                                    BigDecimal multiply = new BigDecimal(lastItem.getGbDpgBuyPrice()).multiply(new BigDecimal(updateOrders.getGbDoQuantity()));
                                    updateOrders.setGbDoWeight(updateOrders.getGbDoQuantity());
                                    updateOrders.setGbDoSubtotal(multiply.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                                    buySubtotal = buySubtotal.add(multiply);
                                    buyWeight = buyWeight.add(new BigDecimal(updateOrders.getGbDoQuantity()));
                                }
                            }
                            gbDepartmentOrdersService.update(updateOrders);
                            if (nxDisId != -1) {
                                transToNxData(gbDistributerGoodsEntity, updateOrders, nxDisId);
                            }

                        } else {
                            unChoiceOrderList.add(gbDepartmentOrders);
                        }
                    }

                    Integer newLength = nxDepartmentOrdersEntities.size() - unChoiceOrderList.size();

                    if (purchaseGoodsEntity.getGbDpgStandard().equals(gbDistributerGoodsEntity.getGbDgGoodsStandardname())) {
                        purchaseGoodsEntity.setGbDpgBuyQuantity(buyWeight.toString());
                        purchaseGoodsEntity.setGbDpgBuySubtotal(buySubtotal.toString());
                    }

                    purchaseGoodsEntity.setGbDpgOrdersAmount(newLength);
                    purchaseGoodsEntity.setGbDpgPurchaseNxSupplierId(batchEntity.getGbDpbSupplierId());
                    purchaseGoodsEntity.setGbDpgBatchId(batchEntity.getGbDistributerPurchaseBatchId());
                    purchaseGoodsEntity.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
                    purchaseGoodsEntity.setGbDpgPurchaseDate(formatWhatDay(0));
                    purchaseGoodsEntity.setGbDpgPurchaseMonth(formatWhatMonth(0));
                    purchaseGoodsEntity.setGbDpgPurchaseYear(formatWhatYear(0));
                    purchaseGoodsEntity.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
                    purchaseGoodsEntity.setGbDpgPurchaseWeek(getWeek(0));
                    purchaseGoodsEntity.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
                    purchaseGoodsEntity.setGbDpgTime(formatWhatTime(0));
                    purchaseGoodsEntity.setGbDpgQuantity(weightTotal.toString());
                    purchaseGoodsEntity.setGbDpgDisGoodsGrandId(gbDistributerGoodsEntity.getGbDgDfgGoodsGrandId());
                    purchaseGoodsEntity.setGbDpgDisGoodsGreatId(gbDistributerGoodsEntity.getGbDgDfgGoodsGreatId());
                    if (nxDisId != -1) {
                        purchaseGoodsEntity.setGbDpgPurchaseNxDistributerId(nxDisId);
                        purchaseGoodsEntity.setGbDpgPurchaseType(5);
                    }
                    gbDPGService.update(purchaseGoodsEntity);

                    if (unChoiceOrderList.size() > 0) {
                        GbDistributerPurchaseGoodsEntity disGoods = new GbDistributerPurchaseGoodsEntity();
                        disGoods.setGbDpgDistributerId(batchEntity.getGbDpbDistributerId());
                        disGoods.setGbDpgPayType(0);
                        disGoods.setGbDpgPurchaseNxDistributerId(-1);
                        disGoods.setGbDpgDisGoodsGrandId(purchaseGoodsEntity.getGbDpgDisGoodsGrandId());
                        disGoods.setGbDpgDisGoodsGreatId(purchaseGoodsEntity.getGbDpgDisGoodsGreatId());
                        disGoods.setGbDpgDisGoodsFatherId(unChoiceOrderList.get(0).getGbDoDisGoodsFatherId());
                        disGoods.setGbDpgDisGoodsId(unChoiceOrderList.get(0).getGbDoDisGoodsId());
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
                        disGoods.setGbDpgPurchaseNxDistributerId(-1);
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


            } else {
                GbDistributerPurchaseBatchEntity batchEntityItem = entities.get(0);
                gbDistributerPurchaseBatchId = batchEntityItem.getGbDistributerPurchaseBatchId();
                for (GbDistributerPurchaseGoodsEntity gbPurGoods : batchEntity.getGbDPGEntities()) {

                    Map<String, Object> mapItem = new HashMap<>();
                    mapItem.put("disGoodsId", gbPurGoods.getGbDpgDisGoodsId());
                    mapItem.put("supplierId", gbDpbSupplierId);
                    mapItem.put("dayuStatus", 2);
                    GbDistributerPurchaseGoodsEntity lastItem = gbDPGService.queryPurchaseGoodsLastItem(mapItem);

                    Integer gbDpgDisGoodsId = gbPurGoods.getGbDpgDisGoodsId();
                    GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
                    List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities = gbPurGoods.getGbDistributerGoodsEntity().getGbDepartmentOrdersEntities();

                    List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();
                    //purGoodsa
                    BigDecimal buyWeight = new BigDecimal(0);
                    BigDecimal buySubtotal = new BigDecimal(0);
                    BigDecimal weightTotal = new BigDecimal(0);
                    for (GbDepartmentOrdersEntity gbDepartmentOrders : nxDepartmentOrdersEntities) {
                        Boolean hasChoice = gbDepartmentOrders.getIsNotice();
                        if (hasChoice) {
                            GbDepartmentOrdersEntity updateOrders = gbDepartmentOrdersService.queryObject(gbDepartmentOrders.getGbDepartmentOrdersId());

                            if (lastItem != null) {
                                //give price
                                gbPurGoods.setGbDpgBuyPrice(lastItem.getGbDpgBuyPrice());
                                updateOrders.setGbDoPrice(lastItem.getGbDpgBuyPrice());
                                weightTotal = weightTotal.add(new BigDecimal(updateOrders.getGbDoQuantity()));

                                if (gbDistributerGoodsEntity.getGbDgGoodsStandardname().equals(updateOrders.getGbDoStandard())) {
                                    BigDecimal multiply = new BigDecimal(lastItem.getGbDpgBuyPrice()).multiply(new BigDecimal(updateOrders.getGbDoQuantity()));
                                    buyWeight = buyWeight.add(new BigDecimal(updateOrders.getGbDoQuantity()));
                                    buySubtotal = buySubtotal.add(multiply);
                                    updateOrders.setGbDoWeight(updateOrders.getGbDoQuantity());
                                    updateOrders.setGbDoSubtotal(multiply.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                                }
                            }

                            updateOrders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
                            gbDepartmentOrdersService.update(updateOrders);

                            if (nxDisId != -1) {
                                transToNxData(gbDistributerGoodsEntity, gbDepartmentOrders, nxDisId);
                            }

                        } else {
                            unChoiceOrderList.add(gbDepartmentOrders);
                        }
                    }

                    if (gbPurGoods.getGbDpgStandard().equals(gbDistributerGoodsEntity.getGbDgGoodsStandardname())) {
                        gbPurGoods.setGbDpgBuyQuantity(buyWeight.toString());
                        gbPurGoods.setGbDpgBuySubtotal(buySubtotal.toString());
                    }

                    Integer newLength = nxDepartmentOrdersEntities.size() - unChoiceOrderList.size();
                    gbPurGoods.setGbDpgOrdersAmount(newLength);
                    gbPurGoods.setGbDpgPurchaseNxSupplierId(batchEntityItem.getGbDpbSupplierId());
                    gbPurGoods.setGbDpgBatchId(batchEntityItem.getGbDistributerPurchaseBatchId());
                    gbPurGoods.setGbDpgStatus(getGbPurchaseGoodsStatusProcurement());
                    gbPurGoods.setGbDpgPurchaseDate(formatWhatDay(0));
                    gbPurGoods.setGbDpgPurchaseMonth(formatWhatMonth(0));
                    gbPurGoods.setGbDpgPurchaseYear(formatWhatYear(0));
                    gbPurGoods.setGbDpgPurchaseFullTime(formatWhatYearDayTime(0));
                    gbPurGoods.setGbDpgPurchaseWeek(getWeek(0));
                    gbPurGoods.setGbDpgPurchaseWeekYear(getWeekOfYear(0).toString());
                    gbPurGoods.setGbDpgTime(formatWhatTime(0));
                    gbPurGoods.setGbDpgQuantity(weightTotal.toString());
                    gbPurGoods.setGbDpgDisGoodsGrandId(gbDistributerGoodsEntity.getGbDgDfgGoodsGrandId());
                    gbPurGoods.setGbDpgDisGoodsGreatId(gbDistributerGoodsEntity.getGbDgDfgGoodsGreatId());
                    if (nxDisId != -1) {
                        gbPurGoods.setGbDpgPurchaseNxDistributerId(nxDisId);
                        gbPurGoods.setGbDpgPurchaseType(5);
                    }
                    gbDPGService.update(gbPurGoods);
                }
            }
            GbDepartmentEntity departmentEntity = gbDepartmentService.queryObject(supplierEntity.getNxJrdhsGbDepartmentId());
            GbDistributerEntity gbDistributerEntity = gbDistributerService.queryObject(batchEntity.getGbDpbDistributerId());
            if (supplierEntity.getNxJrdhsUserId() != null) {

                Map<String, TemplateData> mapNotice = new HashMap<>();
                mapNotice.put("time2", new TemplateData(formatWhatDayTime(0)));
                mapNotice.put("thing13", new TemplateData(departmentEntity.getGbDepartmentName()));
                mapNotice.put("thing8", new TemplateData("采购员订货"));
                mapNotice.put("thing10", new TemplateData("订货"));
                Integer gbDoOrderUserId = supplierEntity.getNxJrdhsNxJrdhBuyUserId();
                GbDepartmentUserEntity gbDepartmentUserEntity = gbDepartmentUserService.queryObject(gbDoOrderUserId);
                mapNotice.put("thing9", new TemplateData(gbDepartmentUserEntity.getGbDuWxNickName()));
                System.out.println("nociiciiiicicautotootototoototo" + mapNotice);
                Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
                NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(nxJrdhsUserId);
                StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbReceiveBatch/gbReceiveBatch");
                pathBuilder.append("?batchId=").append(gbDistributerPurchaseBatchId);
                pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
                pathBuilder.append("&from=notification"); // 添加这个参数
                String path = pathBuilder.toString();
                System.out.println("EncodedTautoGbSuppliertixingMessageJj: " + path);

                WeNoticeService.autoGbSuppliertixingMessageJj(nxJrdhUserEntity.getNxJrdhWxOpenId(), path, mapNotice);
            }

            return R.ok();

        } catch (Exception e) {
            e.printStackTrace();
            return R.error("保存失败：" + e.getMessage());
        }


    }

    private void transToNxData(GbDistributerGoodsEntity gbDistributerGoodsEntity, GbDepartmentOrdersEntity
            orders, Integer nxDisId) {
        if (gbDistributerGoodsEntity.getGbDgNxGoodsId() != null) {
            //nxDisyou
            Map<String, Object> map = new HashMap<>();
            map.put("nxGoodsId", gbDistributerGoodsEntity.getGbDgNxGoodsId());
            map.put("disId", nxDisId);
            System.out.println("nxDigodamap" + map);

            NxGoodsEntity nxGoodsEntity = nxGoodsService.queryObject(gbDistributerGoodsEntity.getGbDgNxGoodsId());

            NxDistributerGoodsEntity distributerGoodsEntity = dgService.queryOneGoodsAboutNxGoods(map);
            System.out.println("11111111");
            if (distributerGoodsEntity != null) {
                //设置 gbDisGoods给 nxDis
                changeGbOrderToNxOrder(orders, distributerGoodsEntity.getNxDistributerGoodsId(), nxDisId);

            } else {
                //下载 nxGoods
                NxDistributerGoodsEntity distributerGoodsEntity1 = downLoadNxGoodsForGbDisGoods(orders.getGbDoDisGoodsId(), nxGoodsEntity, nxDisId);

                ////设置 gbDisGoods给 nxDis
                changeGbOrderToNxOrder(orders, distributerGoodsEntity1.getNxDistributerGoodsId(), nxDisId);


            }

        } else {
            //linshitianjai
            //给 nxDis 添加临时 goods
            NxDistributerGoodsEntity goods = new NxDistributerGoodsEntity();
            Map<String, Object> map = new HashMap<>();
            map.put("disId", nxDisId);
            map.put("nxGoodsId", -1);
            map.put("goodsLevel", 2);
            System.out.println("linshsisssisisiisiissi" + nxDisId);
            List<NxDistributerFatherGoodsEntity> fatherGoodsEntities = dgfService.queryDisFathersGoodsByParams(map);

            NxDistributerFatherGoodsEntity fatherGoodsEntity = fatherGoodsEntities.get(0);
            goods.setNxDgDfgGoodsFatherId(fatherGoodsEntity.getNxDistributerFatherGoodsId());
            goods.setNxDgDfgGoodsGrandId(fatherGoodsEntity.getNxDfgFathersFatherId());
            goods.setNxDgPurchaseAuto(-1);
            goods.setNxDgGoodsName(gbDistributerGoodsEntity.getGbDgGoodsName());
            String pinyin = hanziToPinyin(gbDistributerGoodsEntity.getGbDgGoodsName());
            String headPinyin = getHeadStringByString(gbDistributerGoodsEntity.getGbDgGoodsName(), false, null);
            goods.setNxDgGoodsPinyin(pinyin);
            goods.setNxDgGoodsPy(headPinyin);
            goods.setNxDgDistributerId(nxDisId);
            goods.setNxDgBuyingPriceIsGrade(0);
            goods.setNxDgBuyingPrice("0.1");
            goods.setNxDgWillPrice("0.1");
            goods.setNxDgGoodsIsHidden(0);
            goods.setNxDgGoodsStandardname(gbDistributerGoodsEntity.getGbDgGoodsStandardname());
            goods.setNxDgGoodsDetail(gbDistributerGoodsEntity.getGbDgGoodsDetail());
            goods.setNxDgBuyingPriceUpdate(formatWhatDay(0));
            goods.setNxDgBuyingPriceOne("0.1");
            goods.setNxDgBuyingPriceOneUpdate(formatWhatDate(0));
            goods.setNxDgWillPriceOneStandard(goods.getNxDgGoodsStandardname());
            goods.setNxDgWillPriceOne("0.1");
            goods.setNxDgWillPriceOneAboutPrice("0.1");
            System.out.println("savegoogog" + goods);
            goods.setNxDgOutTotalWeight("0");
            dgService.save(goods);

            Integer gbDfgGoodsAmount = fatherGoodsEntity.getNxDfgGoodsAmount();
            fatherGoodsEntity.setNxDfgGoodsAmount(gbDfgGoodsAmount + 1);
            dgfService.update(fatherGoodsEntity);

            ////设置 gbDisGoods给 nxDis
            changeGbOrderToNxOrder(orders, goods.getNxDistributerGoodsId(), nxDisId);

        }
    }

    private void changeGbOrderToNxOrder(GbDepartmentOrdersEntity gbDepartmentOrders, Integer nxDisGoodsId, Integer
            nxDisId) {

        gbDepartmentOrders.setGbDoNxDistributerId(nxDisId);
        gbDepartmentOrders.setGbDoNxDistributerGoodsId(nxDisGoodsId);
        gbDepartmentOrders.setGbDoGoodsType(5);
        gbDepartmentOrders.setGbDoOrderType(5);
        gbDepartmentOrders.setGbDoApplyDate(formatWhatDay(0));
        gbDepartmentOrders.setGbDoApplyFullTime(formatWhatYearDayTime(0));
        gbDepartmentOrders.setGbDoApplyOnlyTime(formatWhatTime(0));
        gbDepartmentOrders.setGbDoArriveOnlyDate(formatWhatDate(0));
        gbDepartmentOrders.setGbDoArriveWeeksYear(getWeekOfYear(0));


        NxDepartmentOrdersEntity ordersEntity = new NxDepartmentOrdersEntity();
        ordersEntity.setNxDoDistributerId(nxDisId);
        ordersEntity.setNxDoDisGoodsId(nxDisGoodsId);
        ordersEntity.setNxDoQuantity(gbDepartmentOrders.getGbDoQuantity());
        ordersEntity.setNxDoStandard(gbDepartmentOrders.getGbDoStandard());
        ordersEntity.setNxDoRemark(gbDepartmentOrders.getGbDoRemark());
        ordersEntity.setNxDoCostPriceLevel("1");

        NxDistributerGoodsEntity nxDistributerGoodsEntity = dgService.queryObject(nxDisGoodsId);

        if (ordersEntity.getNxDoStandard().equals(nxDistributerGoodsEntity.getNxDgWillPriceOneStandard())) {
            BigDecimal willPrice = new BigDecimal(nxDistributerGoodsEntity.getNxDgWillPriceOne());
            BigDecimal orderWeight = new BigDecimal(gbDepartmentOrders.getGbDoQuantity());
            BigDecimal subtotal = orderWeight.multiply(willPrice).setScale(1, BigDecimal.ROUND_HALF_UP);

            gbDepartmentOrders.setGbDoPrice(nxDistributerGoodsEntity.getNxDgWillPrice());
            gbDepartmentOrders.setGbDoWeight(gbDepartmentOrders.getGbDoQuantity());
            gbDepartmentOrders.setGbDoSubtotal(subtotal.toString());
            gbDepartmentOrders.setGbDoCostPriceLevel(1);

            Integer gbDoPurchaseGoodsId = gbDepartmentOrders.getGbDoPurchaseGoodsId();
            GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(gbDoPurchaseGoodsId);
            purchaseGoodsEntity.setGbDpgBuyPrice(willPrice.toString());
            gbDPGService.update(purchaseGoodsEntity);


            ordersEntity.setNxDoPrice(nxDistributerGoodsEntity.getNxDgWillPriceOne());
            ordersEntity.setNxDoWeight(gbDepartmentOrders.getGbDoQuantity());
            ordersEntity.setNxDoSubtotal(subtotal.toString());
            ordersEntity.setNxDoCostPrice(nxDistributerGoodsEntity.getNxDgBuyingPriceOne());
            ordersEntity.setNxDoCostPriceUpdate(nxDistributerGoodsEntity.getNxDgBuyingPriceOneUpdate());
            BigDecimal buyPrice = new BigDecimal(nxDistributerGoodsEntity.getNxDgBuyingPriceOne());
            BigDecimal buySutotal = buyPrice.multiply(orderWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
            ordersEntity.setNxDoCostSubtotal(buySutotal.toString());
            BigDecimal profit = subtotal.subtract(buySutotal).setScale(1, BigDecimal.ROUND_HALF_UP);
            ordersEntity.setNxDoProfitSubtotal(profit.toString());
            BigDecimal decimal = profit.divide(subtotal, 2, BigDecimal.ROUND_HALF_UP);
            ordersEntity.setNxDoProfitScale(decimal.toString());

        } else {
            ordersEntity.setNxDoCostSubtotal("0");
            ordersEntity.setNxDoProfitSubtotal("0");
            ordersEntity.setNxDoCostPrice("0");
            ordersEntity.setNxDoCostPrice(nxDistributerGoodsEntity.getNxDgBuyingPriceOne());
            ordersEntity.setNxDoCostPriceUpdate(nxDistributerGoodsEntity.getNxDgBuyingPriceOneUpdate());
            if (nxDistributerGoodsEntity.getNxDgWillPriceTwoStandard() != null && gbDepartmentOrders.getGbDoStandard().equals(nxDistributerGoodsEntity.getNxDgWillPriceTwoStandard())) {
                BigDecimal willPrice = new BigDecimal(nxDistributerGoodsEntity.getNxDgWillPriceTwo());
                BigDecimal orderWeight = new BigDecimal(gbDepartmentOrders.getGbDoQuantity());
                BigDecimal subtotal = orderWeight.multiply(willPrice).setScale(1, BigDecimal.ROUND_HALF_UP);

                ordersEntity.setNxDoPrice(nxDistributerGoodsEntity.getNxDgWillPriceTwo());
                ordersEntity.setNxDoCostPriceLevel("2");
                ordersEntity.setNxDoWeight(gbDepartmentOrders.getGbDoQuantity());
                ordersEntity.setNxDoSubtotal(subtotal.toString());
                gbDepartmentOrders.setGbDoPrice(nxDistributerGoodsEntity.getNxDgWillPriceTwo());

                System.out.println("heeehere" + nxDistributerGoodsEntity.getNxDgWillPriceTwo());
                System.out.println("heeehere" + gbDepartmentOrders.getGbDoPrice());
                gbDepartmentOrders.setGbDoWeight(gbDepartmentOrders.getGbDoQuantity());
                gbDepartmentOrders.setGbDoSubtotal(subtotal.toString());
                gbDepartmentOrders.setGbDoCostPriceLevel(2);

                Integer gbDoPurchaseGoodsId = gbDepartmentOrders.getGbDoPurchaseGoodsId();
                GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.queryObject(gbDoPurchaseGoodsId);
                purchaseGoodsEntity.setGbDpgBuyPrice(willPrice.toString());
                gbDPGService.update(purchaseGoodsEntity);

                ordersEntity.setNxDoCostPrice(nxDistributerGoodsEntity.getNxDgBuyingPriceTwo());
                ordersEntity.setNxDoCostPriceUpdate(nxDistributerGoodsEntity.getNxDgBuyingPriceTwoUpdate());
                BigDecimal buyPrice = new BigDecimal(nxDistributerGoodsEntity.getNxDgBuyingPriceTwo());
                BigDecimal buySutotal = buyPrice.multiply(orderWeight).setScale(1, BigDecimal.ROUND_HALF_UP);
                ordersEntity.setNxDoCostSubtotal(buySutotal.toString());
                BigDecimal profit = subtotal.subtract(buySutotal).setScale(1, BigDecimal.ROUND_HALF_UP);
                ordersEntity.setNxDoProfitSubtotal(profit.toString());
                BigDecimal decimal = profit.divide(subtotal, 2, BigDecimal.ROUND_HALF_UP);
                ordersEntity.setNxDoProfitScale(decimal.toString());
            }
        }


        ordersEntity.setNxDoApplyDate(formatWhatDay(0));
        ordersEntity.setNxDoArriveOnlyDate(formatWhatDay(0));
        ordersEntity.setNxDoArriveWeeksYear(getWeekOfYear(0));
        ordersEntity.setNxDoApplyFullTime(formatWhatYearDayTime(0));
        ordersEntity.setNxDoApplyOnlyTime(formatWhatTime(0));
        ordersEntity.setNxDoArriveDate(gbDepartmentOrders.getGbDoApplyArriveDate());
        ordersEntity.setNxDoGbDistributerId(gbDepartmentOrders.getGbDoDistributerId());
        ordersEntity.setNxDoGbDepartmentId(gbDepartmentOrders.getGbDoDepartmentId());
        ordersEntity.setNxDoGbDepartmentFatherId(gbDepartmentOrders.getGbDoDepartmentFatherId());
        ordersEntity.setNxDoDepartmentId(-1);
        ordersEntity.setNxDoDepartmentFatherId(-1);
        ordersEntity.setNxDoNxCommunityId(-1);
        ordersEntity.setNxDoNxCommRestrauntFatherId(-1);
        ordersEntity.setNxDoNxCommRestrauntId(-1);
        ordersEntity.setNxDoNxGoodsId(nxDistributerGoodsEntity.getNxDgNxGoodsId());
        ordersEntity.setNxDoNxGoodsFatherId(nxDistributerGoodsEntity.getNxDgNxFatherId());
        ordersEntity.setNxDoDisGoodsFatherId(nxDistributerGoodsEntity.getNxDgDfgGoodsFatherId());
        ordersEntity.setNxDoDisGoodsGrandId(nxDistributerGoodsEntity.getNxDgDfgGoodsGrandId());
        ordersEntity.setNxDoDepDisGoodsId(-1);
        ordersEntity.setNxDoArriveWhatDay(getWeek(0));
        ordersEntity.setNxDoPurchaseStatus(getNxDepOrderBuyStatusUnPurchase());
        ordersEntity.setNxDoGoodsType(nxDistributerGoodsEntity.getNxDgPurchaseAuto());
        ordersEntity.setNxDoIsAgent(-1);
        ordersEntity.setNxDoPrintStandard(nxDistributerGoodsEntity.getNxDgGoodsStandardname());
        ordersEntity.setNxDoPurchaseUserId(-1);
        ordersEntity.setNxDoGbDepartmentOrderId(gbDepartmentOrders.getGbDepartmentOrdersId());
        if (nxDistributerGoodsEntity.getNxDgPurchaseAuto() == -1) {
            ordersEntity.setNxDoPurchaseGoodsId(nxDistributerGoodsEntity.getNxDgPurchaseAuto());
        } else {
            savePurGoodsAuto(ordersEntity);
        }

        ordersEntity.setNxDoStatus(getNxOrderStatusNew());
        depOrdersService.saveForGb(ordersEntity);
        Integer nxDepartmentOrdersId = ordersEntity.getNxDepartmentOrdersId();

        gbDepartmentOrders.setGbDoNxDepartmentOrderId(nxDepartmentOrdersId);
        gbDepartmentOrdersService.update(gbDepartmentOrders);


    }


    private NxDistributerGoodsEntity downLoadNxGoodsForGbDisGoods(Integer gbDisGoodsId, NxGoodsEntity
            nxGoodsEntity, Integer nxdisId) {
        System.out.println("downLoadNxGoodsForGbDisGoods-----");
        NxDistributerGoodsEntity cgnGoods = new NxDistributerGoodsEntity();
        cgnGoods.setNxDgDistributerId(nxdisId);
        cgnGoods.setNxDgNxGoodsId(nxGoodsEntity.getNxGoodsId());
        cgnGoods.setNxDgGoodsName(nxGoodsEntity.getNxGoodsName());
        cgnGoods.setNxDgNxFatherId(nxGoodsEntity.getNxGoodsFatherId());

        Integer nxGoodsFatherId = nxGoodsEntity.getNxGoodsFatherId();
        NxGoodsEntity fatherNxGoods = nxGoodsService.queryObject(nxGoodsFatherId);

        cgnGoods.setNxDgNxFatherImg(fatherNxGoods.getNxGoodsFile());
        cgnGoods.setNxDgNxFatherName(fatherNxGoods.getNxGoodsName());
        cgnGoods.setNxDgGoodsDetail(nxGoodsEntity.getNxGoodsDetail());
        cgnGoods.setNxDgGoodsPlace(nxGoodsEntity.getNxGoodsPlace());
        cgnGoods.setNxDgGoodsBrand(nxGoodsEntity.getNxGoodsBrand());
        cgnGoods.setNxDgGoodsStandardname(nxGoodsEntity.getNxGoodsStandardname());
        cgnGoods.setNxDgGoodsStandardWeight(nxGoodsEntity.getNxGoodsStandardWeight());
        cgnGoods.setNxDgGoodsPy(nxGoodsEntity.getNxGoodsPy());
        cgnGoods.setNxDgGoodsPinyin(nxGoodsEntity.getNxGoodsPinyin());
        cgnGoods.setNxDgCartonUnit(nxGoodsEntity.getNxGoodsCartonUnit());
        cgnGoods.setNxDgItemsPerCarton(nxGoodsEntity.getNxGoodsItemsPerCarton());
        cgnGoods.setNxDgPullOff(0);
        cgnGoods.setNxDgGoodsStatus(0);
        cgnGoods.setNxDgPurchaseAuto(1);

        NxDistributerGoodsEntity distributerGoodsEntity = saveDisGoods(cgnGoods);

        //2，保存dis规格bieming
        Integer nxDistributerGoodsId = cgnGoods.getNxDistributerGoodsId();
        //2.1
        Integer nxDgNxGoodsId = cgnGoods.getNxDgNxGoodsId();
        List<NxStandardEntity> nxStandardEntities = nxStandardService.queryGoodsStandardListByGoodId(nxDgNxGoodsId);
        if (nxStandardEntities.size() > 0) {
            for (NxStandardEntity standard : nxStandardEntities) {
                NxDistributerStandardEntity disStandards = new NxDistributerStandardEntity();
                disStandards.setNxDsDisGoodsId(nxDistributerGoodsId);
                disStandards.setNxDsStandardName(standard.getNxStandardName());
                disStandards.setNxDsStandardError(standard.getNxStandardError());
                disStandards.setNxDsStandardScale(standard.getNxStandardScale());
                disStandards.setNxDsStandardFilePath(standard.getNxStandardFilePath());
                disStandards.setNxDsStandardSort(standard.getNxStandardSort());
                dsService.save(disStandards);
            }
        }

//        2.2

        Map<String, Object> map = new HashMap<>();
        map.put("goodsId", nxDgNxGoodsId);
        List<NxAliasEntity> aliasEntities = nxAliasService.queryNxAliasList(map);
        if (aliasEntities.size() > 0) {
            for (NxAliasEntity aliasEntity : aliasEntities) {
                NxDistributerAliasEntity disAlias = new NxDistributerAliasEntity();
                disAlias.setNxDaDisGoodsId(nxDistributerGoodsId);
                disAlias.setNxDaAliasName(aliasEntity.getNxAliasName());
                disAliasService.save(disAlias);
            }
        }


        return distributerGoodsEntity;
    }


    private NxDistributerGoodsEntity saveDisGoods(NxDistributerGoodsEntity cgnGoods) {
        Integer nxDgNxGoodsId = cgnGoods.getNxDgNxGoodsId();
        NxGoodsEntity nxGoodsEntity = nxGoodsService.queryObject(nxDgNxGoodsId);
        cgnGoods.setNxDgGoodsName(nxGoodsEntity.getNxGoodsName());
        cgnGoods.setNxDgNxFatherImg(nxGoodsEntity.getNxGoodsFile());
        cgnGoods.setNxDgGoodsStandardname(nxGoodsEntity.getNxGoodsStandardname());
        cgnGoods.setNxDgGoodsDetail(nxGoodsEntity.getNxGoodsDetail());
        cgnGoods.setNxDgGoodsPlace(nxGoodsEntity.getNxGoodsPlace());
        cgnGoods.setNxDgGoodsBrand(nxGoodsEntity.getNxGoodsBrand());
        cgnGoods.setNxDgGoodsStandardWeight(nxGoodsEntity.getNxGoodsStandardWeight());
        cgnGoods.setNxDgGoodsPinyin(nxGoodsEntity.getNxGoodsPinyin());
        cgnGoods.setNxDgGoodsPy(nxGoodsEntity.getNxGoodsPy());
        cgnGoods.setNxDgPullOff(0);
        cgnGoods.setNxDgGoodsStatus(1);
        cgnGoods.setNxStandardEntities(nxGoodsEntity.getNxGoodsStandardEntities());
        cgnGoods.setNxDistributerAliasEntities(nxGoodsEntity.getNxDistributerAliasEntities());
        cgnGoods.setNxDgNxFatherId(nxGoodsEntity.getNxGoodsFatherId());
        cgnGoods.setNxDgNxFatherName(nxGoodsEntity.getFatherGoods().getNxGoodsName());
        cgnGoods.setNxDgNxFatherImg(nxGoodsEntity.getFatherGoods().getNxGoodsFile());
        cgnGoods.setNxDgNxGrandName(nxGoodsEntity.getGrandGoods().getNxGoodsName());
        cgnGoods.setNxDgNxGrandId(nxGoodsEntity.getGrandGoods().getNxGoodsId());
        cgnGoods.setNxDgGoodsFile(nxGoodsEntity.getNxGoodsFile());
        cgnGoods.setNxDgGoodsFileLarge(nxGoodsEntity.getNxGoodsFileBig());
        cgnGoods.setNxDgIsOldestSon(nxGoodsEntity.getNxGoodsIsOldestSon());
        cgnGoods.setNxDgGoodsSort(nxGoodsEntity.getNxGoodsSort());
        cgnGoods.setNxDgGoodsSonsSort(nxGoodsEntity.getNxGoodsSonsSort());
        cgnGoods.setNxDgGoodsIsHidden(0);
        cgnGoods.setNxDgBuyingPriceIsGrade(0);
        cgnGoods.setNxDgPurchaseAuto(-1);
        cgnGoods.setNxDgQuantityDays(nxGoodsEntity.getNxGoodsQuantityDays());

        cgnGoods.setNxDgBuyingPriceUpdate(formatWhatDay(0));
        cgnGoods.setNxDgWillPrice("0.1");
        cgnGoods.setNxDgBuyingPrice("0.1");
        cgnGoods.setNxDgBuyingPriceOne("0.1");
        cgnGoods.setNxDgBuyingPriceOneUpdate(formatWhatDate(0));
        cgnGoods.setNxDgWillPriceOneStandard(cgnGoods.getNxDgGoodsStandardname());
        cgnGoods.setNxDgWillPriceOne("0.1");
        cgnGoods.setNxDgWillPriceOneAboutPrice("0.1");


        //queryGrandFatherId
        NxGoodsEntity fatherEntity = nxGoodsService.queryObject(cgnGoods.getNxDgNxFatherId());
        Integer grandFatherId = fatherEntity.getNxGoodsFatherId();
        cgnGoods.setNxDgNxGrandId(grandFatherId);
        NxGoodsEntity grandEntity = nxGoodsService.queryObject(grandFatherId);
        cgnGoods.setNxDgNxGrandName(grandEntity.getNxGoodsName());


        //queryGreatGrandFatherId
        Integer greatGrandFatherId = grandEntity.getNxGoodsFatherId();
        if (greatGrandFatherId.equals(1)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#20afb8");
        }
        if (greatGrandFatherId.equals(2)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#f5c832");
        }
        if (greatGrandFatherId.equals(3)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#3cc36e");
        }
        if (greatGrandFatherId.equals(4)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#f09628");
        }
        if (greatGrandFatherId.equals(5)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#1ebaee");
        }
        if (greatGrandFatherId.equals(6)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#f05a32");
        }
        if (greatGrandFatherId.equals(7)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#c0a6dd");
        }
        if (greatGrandFatherId.equals(8)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#969696");
        }
        if (greatGrandFatherId.equals(9)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#318666");
        }
        if (greatGrandFatherId.equals(10)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#026bc2");
        }
        if (greatGrandFatherId.equals(11)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#06eb6d");
        }
        if (greatGrandFatherId.equals(12)) {
            cgnGoods.setNxDgNxGoodsFatherColor("#0690eb");
        }

        cgnGoods.setNxDgNxGreatGrandId(greatGrandFatherId);
        cgnGoods.setNxDgNxGreatGrandName(nxGoodsService.queryObject(greatGrandFatherId).getNxGoodsName());

        Integer nxDgDistributerId = cgnGoods.getNxDgDistributerId();

        // 3， 查询父类
        Integer nxDgNxFatherId = cgnGoods.getNxDgNxFatherId();
        Map<String, Object> map = new HashMap<>();
        map.put("disId", nxDgDistributerId);
        map.put("nxFatherId", nxDgNxFatherId);
        List<NxDistributerGoodsEntity> nxDistributerGoodsEntities = dgService.queryDisGoodsByParams(map);

        if (nxDistributerGoodsEntities.size() > 0) {
            //直接加disGoods和disStandard,不需要加disFatherGoods
            //1，给父类商品的字段商品数量加1
            NxDistributerGoodsEntity disGoodsEntity = nxDistributerGoodsEntities.get(0);
            Integer nxDgDfgGoodsFatherId1 = disGoodsEntity.getNxDgDfgGoodsFatherId();
            Integer nxDgDfgGoodsGrandId = disGoodsEntity.getNxDgDfgGoodsGrandId();

            NxDistributerFatherGoodsEntity nxDistributerFatherGoodsEntity = dgfService.queryObject(nxDgDfgGoodsFatherId1);
            Integer nxDfgGoodsAmount = nxDistributerFatherGoodsEntity.getNxDfgGoodsAmount();
            nxDistributerFatherGoodsEntity.setNxDfgGoodsAmount(nxDfgGoodsAmount + 1);
            dgfService.update(nxDistributerFatherGoodsEntity);

            //2，保存disId商品
            Integer nxDgDfgGoodsFatherId = disGoodsEntity.getNxDgDfgGoodsFatherId();
            cgnGoods.setNxDgDfgGoodsFatherId(nxDgDfgGoodsFatherId);
            cgnGoods.setNxDgDfgGoodsGrandId(nxDgDfgGoodsGrandId);

            cgnGoods.setNxDgOutTotalWeight("0");
            //1 ，先保存disGoods
            dgService.save(cgnGoods);
            //
        } else {
            //添加fatherGoods的第一个级别
            NxDistributerFatherGoodsEntity dgf = new NxDistributerFatherGoodsEntity();
            dgf.setNxDfgDistributerId(cgnGoods.getNxDgDistributerId());
            dgf.setNxDfgFatherGoodsName(cgnGoods.getNxDgNxFatherName());
            dgf.setNxDfgFatherGoodsLevel(2);
            dgf.setNxDfgGoodsAmount(1);
            dgf.setNxDfgFatherGoodsImg(cgnGoods.getNxDgNxFatherImg());
            dgf.setNxDfgFatherGoodsColor(cgnGoods.getNxDgNxGoodsFatherColor());
            dgf.setNxDfgFatherGoodsSort(nxGoodsEntity.getFatherGoods().getNxGoodsSort());
            dgf.setNxDfgNxGoodsId(cgnGoods.getNxDgNxFatherId());
            dgfService.save(dgf);

            //继续查询是否有GrandFather
            String grandName = cgnGoods.getNxDgNxGrandName();
            Map<String, Object> map2 = new HashMap<>();
            map2.put("disId", nxDgDistributerId);
            map2.put("fathersFatherName", grandName);
            List<NxDistributerFatherGoodsEntity> grandGoodsFather = dgfService.queryHasDisFathersFather(map2);
            if (grandGoodsFather.size() > 0) {
                NxDistributerFatherGoodsEntity nxDistributerFatherGoodsEntity = grandGoodsFather.get(0);
                dgf.setNxDfgFathersFatherId(nxDistributerFatherGoodsEntity.getNxDistributerFatherGoodsId());
                dgfService.update(dgf);
                //更新disGoods的fatherGoodsId
                Integer distributerFatherGoodsId = dgf.getNxDistributerFatherGoodsId();
                Integer nxDfgFathersFatherId = dgf.getNxDfgFathersFatherId();
                cgnGoods.setNxDgDfgGoodsFatherId(distributerFatherGoodsId);
                cgnGoods.setNxDgDfgGoodsGrandId(nxDfgFathersFatherId);
                cgnGoods.setNxDgOutTotalWeight("0");
                dgService.save(cgnGoods);

            } else {
                //tianjiaGrand
                NxDistributerFatherGoodsEntity grand = new NxDistributerFatherGoodsEntity();
                String nxCgGrandFatherName = cgnGoods.getNxDgNxGrandName();
                grand.setNxDfgFatherGoodsName(nxCgGrandFatherName);
                grand.setNxDfgDistributerId(cgnGoods.getNxDgDistributerId());
                grand.setNxDfgFatherGoodsLevel(1);
                grand.setNxDfgGoodsAmount(1);
                grand.setNxDfgFatherGoodsColor(cgnGoods.getNxDgNxGoodsFatherColor());
                grand.setNxDfgNxGoodsId(cgnGoods.getNxDgNxGrandId());
                grand.setNxDfgFatherGoodsSort(nxGoodsEntity.getGrandGoods().getNxGoodsSort());
                dgfService.save(grand);

                dgf.setNxDfgFathersFatherId(grand.getNxDistributerFatherGoodsId());
                dgfService.update(dgf);
                //更新disGoods的fatherGoodsId
                Integer distributerFatherGoodsId = dgf.getNxDistributerFatherGoodsId();
                Integer nxDfgFathersFatherId = dgf.getNxDfgFathersFatherId();
                cgnGoods.setNxDgDfgGoodsFatherId(distributerFatherGoodsId);
                cgnGoods.setNxDgDfgGoodsGrandId(nxDfgFathersFatherId);
                cgnGoods.setNxDgOutTotalWeight("0");
                dgService.save(cgnGoods);
                //查询是否有greatGrand
                String greatGrandName = cgnGoods.getNxDgNxGreatGrandName();
                Map<String, Object> map3 = new HashMap<>();
                map3.put("disId", nxDgDistributerId);
                map3.put("fathersFatherName", greatGrandName);
                List<NxDistributerFatherGoodsEntity> greatGrandGoodsFather = dgfService.queryHasDisFathersFather(map3);
                if (greatGrandGoodsFather.size() > 0) {
                    NxDistributerFatherGoodsEntity nxDistributerFatherGoodsEntity = greatGrandGoodsFather.get(0);
                    Integer disFatherId = nxDistributerFatherGoodsEntity.getNxDistributerFatherGoodsId();
                    grand.setNxDfgFathersFatherId(disFatherId);
                    dgfService.update(grand);
                } else {
                    NxDistributerFatherGoodsEntity greatGrand = new NxDistributerFatherGoodsEntity();
                    String greatGrandName1 = cgnGoods.getNxDgNxGreatGrandName();
                    greatGrand.setNxDfgFatherGoodsName(greatGrandName1);
                    greatGrand.setNxDfgDistributerId(cgnGoods.getNxDgDistributerId());
                    greatGrand.setNxDfgFatherGoodsLevel(0);
                    greatGrand.setNxDfgFatherGoodsColor(cgnGoods.getNxDgNxGoodsFatherColor());
                    greatGrand.setNxDfgNxGoodsId(cgnGoods.getNxDgNxGreatGrandId());
                    greatGrand.setNxDfgFatherGoodsImg(grandEntity.getFatherGoods().getNxGoodsFile());
                    greatGrand.setNxDfgFatherGoodsSort(grandEntity.getFatherGoods().getNxGoodsSort());
                    greatGrand.setNxDfgGoodsAmount(1);
                    dgfService.save(greatGrand);
                    grand.setNxDfgFathersFatherId(greatGrand.getNxDistributerFatherGoodsId());
                    dgfService.update(grand);
                }
            }
        }
        return cgnGoods;
    }


    private void savePurGoodsAuto(NxDepartmentOrdersEntity ordersEntity) {

        NxDistributerPurchaseGoodsEntity resultPurGoods = new NxDistributerPurchaseGoodsEntity();

        //判断是否有已经分的
        Integer doDisGoodsId = ordersEntity.getNxDoDisGoodsId();
        NxDistributerGoodsEntity disGoods = dgService.queryObject(doDisGoodsId);
        Map<String, Object> map = new HashMap<>();
        map.put("disGoodsId", doDisGoodsId);
        map.put("status", 1);
        map.put("standard", ordersEntity.getNxDoStandard());
        System.out.println("purgogogo" + map);
        NxDistributerPurchaseGoodsEntity havePurGoods = nxDisPurchaseGoodsService.queryIfHavePurGoods(map);
        if (havePurGoods != null) {
            resultPurGoods = havePurGoods;
            havePurGoods.setNxDpgOrdersAmount(resultPurGoods.getNxDpgOrdersAmount() + 1);
            NxDistributerGoodsEntity distributerGoodsEntity = dgService.queryObject(doDisGoodsId);
            if (ordersEntity.getNxDoStandard().equals(distributerGoodsEntity.getNxDgGoodsStandardname())) {
                if (havePurGoods.getNxDpgBuyQuantity() != null) {
                    BigDecimal decimal = new BigDecimal(havePurGoods.getNxDpgQuantity());
                    BigDecimal decimal1 = new BigDecimal(ordersEntity.getNxDoQuantity());
                    BigDecimal totaoWeight = decimal.add(decimal1).setScale(1, BigDecimal.ROUND_HALF_UP);
                    BigDecimal decimal2 = totaoWeight.multiply(new BigDecimal(havePurGoods.getNxDpgBuyPrice())).setScale(1, BigDecimal.ROUND_HALF_UP);
                    resultPurGoods.setNxDpgQuantity(totaoWeight.toString());
                    resultPurGoods.setNxDpgBuyQuantity(totaoWeight.toString());
                    resultPurGoods.setNxDpgBuySubtotal(decimal2.toString());
                }

            }
//            else {
//                BigDecimal decimal = new BigDecimal(resultPurGoods.getNxDpgQuantity());
//                BigDecimal purQuantity = new BigDecimal(resultPurGoods.getNxDpgQuantity());
//                BigDecimal add = decimal.add(purQuantity);
//                resultPurGoods.setNxDpgQuantity(add.toString());
//                resultPurGoods.setNxDpgBuyQuantity(add.toString());
//            }
            nxDisPurchaseGoodsService.update(resultPurGoods);

        } else {
            resultPurGoods.setNxDpgDisGoodsFatherId(disGoods.getNxDgDfgGoodsFatherId());
            resultPurGoods.setNxDpgDisGoodsGrandId(disGoods.getNxDgDfgGoodsGrandId());
            resultPurGoods.setNxDpgDistributerId(disGoods.getNxDgDistributerId());
            resultPurGoods.setNxDpgStatus(getNxDisPurchaseGoodsUnBuy());
            resultPurGoods.setNxDpgApplyDate(formatWhatYearDayTime(0));
            resultPurGoods.setNxDpgOrdersAmount(1);
            resultPurGoods.setNxDpgFinishAmount(0);
            resultPurGoods.setNxDpgPurchaseType(getGbPurchaseGoodsTypeForOrder());
            resultPurGoods.setNxDpgExpectPrice(disGoods.getNxDgBuyingPrice());
            resultPurGoods.setNxDpgBuyPrice(disGoods.getNxDgBuyingPrice());
            resultPurGoods.setNxDpgDisGoodsId(doDisGoodsId);
            resultPurGoods.setNxDpgInputType(disGoods.getNxDgPurchaseAuto());
            resultPurGoods.setNxDpgStandard(ordersEntity.getNxDoStandard());
            NxDistributerGoodsEntity distributerGoodsEntity = dgService.queryObject(doDisGoodsId);
            if (ordersEntity.getNxDoStandard().equals(distributerGoodsEntity.getNxDgGoodsStandardname())) {
                BigDecimal totaoWeight = new BigDecimal(ordersEntity.getNxDoQuantity());
                BigDecimal decimal2 = totaoWeight.multiply(new BigDecimal(resultPurGoods.getNxDpgBuyPrice())).setScale(1, BigDecimal.ROUND_HALF_UP);
                resultPurGoods.setNxDpgBuyQuantity(totaoWeight.toString());
                resultPurGoods.setNxDpgQuantity(totaoWeight.toString());
                resultPurGoods.setNxDpgBuySubtotal(decimal2.toString());
            }
            nxDisPurchaseGoodsService.save(resultPurGoods);

        }

        NxDistributerPurchaseBatchEntity batchEntity = new NxDistributerPurchaseBatchEntity();

        //给autoBatch更新gbDepartmentOrderid
        System.out.println("suplieridid" + disGoods.getNxDgGoodsName() + disGoods.getNxDgSupplierId());
        if (disGoods.getNxDgSupplierId() != null) {
            //
            Map<String, Object> mapBatch = new HashMap<>();
            Integer gbDgGbSupplierId = disGoods.getNxDgSupplierId();
            mapBatch.put("supplierId", gbDgGbSupplierId);
            mapBatch.put("status", 1);
            mapBatch.put("purchaseType", 2);

            List<NxDistributerPurchaseBatchEntity> entities = nxDPBService.queryDisPurchaseBatch(mapBatch);
            if (entities.size() == 0) {
                //
                batchEntity.setNxDpbDate(formatWhatDay(0));
                batchEntity.setNxDpbTime(formatWhatTime(0));
                batchEntity.setNxDpbMonth(formatWhatMonth(0));
                batchEntity.setNxDpbPruchaseWeek(getWeek(0));
                batchEntity.setNxDpbYear(formatWhatYear(0));
                batchEntity.setNxDpbPayFullTime(formatWhatYearDayTime(0));
                batchEntity.setNxDpbStatus(-1);
                batchEntity.setNxDpbPurchaseType(2);
                batchEntity.setNxDpbSupplierId(gbDgGbSupplierId);
                NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.queryObject(gbDgGbSupplierId);
                batchEntity.setNxDpbSellUserId(supplierEntity.getNxJrdhsUserId());
                batchEntity.setNxDpbDistributerId(ordersEntity.getNxDoDistributerId());
                batchEntity.setNxDpbPurUserId(supplierEntity.getNxJrdhsNxPurUserId());
                batchEntity.setNxDpbBuyUserId(supplierEntity.getNxJrdhsNxJrdhBuyUserId());
                NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(supplierEntity.getNxJrdhsNxJrdhBuyUserId());
                batchEntity.setNxDpbBuyUserOpenId(nxJrdhUserEntity.getNxJrdhWxOpenId());
                nxDPBService.save(batchEntity);

                resultPurGoods.setNxDpgBatchId(batchEntity.getNxDistributerPurchaseBatchId());
                resultPurGoods.setNxDpgPurchaseDate(formatWhatDay(0));
                resultPurGoods.setNxDpgTime(formatWhatYearDayTime(0));
                resultPurGoods.setNxDpgDistributerId(batchEntity.getNxDpbDistributerId());
                resultPurGoods.setNxDpgStatus(getNxDisPurchaseGoodsWithBatch());
                nxDisPurchaseGoodsService.update(resultPurGoods);
            } else {
                batchEntity = entities.get(0);
                resultPurGoods.setNxDpgBatchId(batchEntity.getNxDistributerPurchaseBatchId());
                nxDisPurchaseGoodsService.update(resultPurGoods);
            }

            Map<String, TemplateData> mapNotice = new HashMap<>();
            mapNotice.put("thing1", new TemplateData(disGoods.getNxDgGoodsName()));
            mapNotice.put("time6", new TemplateData(formatWhatDay(0)));
            Integer nxDpbDistributerId = batchEntity.getNxDpbDistributerId();
            NxDistributerEntity distributerEntity = nxDistributerService.queryObject(nxDpbDistributerId);
            mapNotice.put("thing7", new TemplateData(distributerEntity.getNxDistributerName()));
            mapNotice.put("thing8", new TemplateData(distributerEntity.getNxDistributerPhone()));
//
            StringBuilder pathBuilder = new StringBuilder("pages/txs/disOrderBatch/disOrderBatch");
            pathBuilder.append("?batchId=").append(batchEntity.getNxDistributerPurchaseBatchId());
            pathBuilder.append("&retName=").append(nxDistributerService.queryObject(batchEntity.getNxDpbDistributerId()).getNxDistributerName());
            pathBuilder.append("&disId=").append(batchEntity.getNxDpbDistributerId());
            pathBuilder.append("&buyUserId=").append(batchEntity.getNxDpbBuyUserId());
            pathBuilder.append("&purUserId=").append(batchEntity.getNxDpbPurUserId());
            pathBuilder.append("&fromBuyer=1");
            String path = pathBuilder.toString();
            System.out.println("paththhththt" + path);

            NxJrdhSupplierEntity supplierEntity = jrdhSupplierService.queryObject(disGoods.getNxDgSupplierId());
            Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
            NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(nxJrdhsUserId);
            System.out.println("suppsleir" + path);
            WeNoticeService.nxSupplierOrderSave(nxJrdhUserEntity.getNxJrdhWxOpenId(), path, mapNotice);
        }

        ordersEntity.setNxDoPurchaseGoodsId(resultPurGoods.getNxDistributerPurchaseGoodsId());
        nxDepartmentOrdersService.update(ordersEntity);

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
            batchEntity.setGbDpbDate(formatWhatDay(0));
            batchEntity.setGbDpbHour(formatWhatHour(0));
            batchEntity.setGbDpbMinute(formatWhatMinute(0));
            batchEntity.setGbDpbTime(formatWhatTime(0));
            batchEntity.setGbDpbPurchaseMonth(formatWhatMonth(0));
            batchEntity.setGbDpbPurchaseWeek(getWeek(0));
            batchEntity.setGbDpbPurchaseYear(formatWhatYear(0));
            batchEntity.setGbDpbPurchaseFullTime(formatWhatYearDayTime(0));
            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchUnRead());
            batchEntity.setGbDpbNxDistributerId(-1);
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
                        orders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
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
                    disGoods.setGbDpgPurchaseNxDistributerId(-1);
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
                    disGoods.setGbDpgPurchaseNxDistributerId(-1);
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
        batchEntity.setGbDpbStatus(getGbDisPurchaseBatchUnSend());
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
                    orders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
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
                disGoods.setGbDpgPurchaseNxDistributerId(-1);
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
                disGoods.setGbDpgPurchaseNxDistributerId(-1);
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

    /**
     * 删除订货批次
     * @param batchId
     * @return
     */
//    @RequestMapping(value = "/delteDisPurBatchGb/{batchId}")
//    @ResponseBody
//    public R delteDisPurBatchGb(@PathVariable Integer batchId) {
//        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.queryBatchWithOrders(batchId);
//        if(batchEntity.getGbDpbStatus() == 1){
//            for (GbDistributerPurchaseGoodsEntity purGoods : batchEntity.getGbDPGEntities()) {
//                purGoods.setGbDpgBatchId(null);
//                purGoods.setGbDpgStatus(0);
//                purGoods.setGbDpgBuySubtotal("0.0");
//                purGoods.setGbDpgBuyPrice("0.0");
//                purGoods.setGbDpgBuyQuantity("0.0");
//                purGoods.setGbDpgBuyScalePrice("0.0");
//                purGoods.setGbDpgBuyScaleQuantity("0.0");
//                gbDPGService.update(purGoods);
//
//                Integer nxDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
//                Map<String, Object> map = new HashMap<>();
//                map.put("purGoodsId", nxDistributerPurchaseGoodsId);
//                List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
//                for (GbDepartmentOrdersEntity orders : ordersEntities) {
//                    orders.setGbDoBuyStatus(getGbOrderBuyStatusNew());
//                    orders.setGbDoWeight("0.0");
//                    orders.setGbDoPrice("0.0");
//                    orders.setGbDoSubtotal("0.0");
//                    orders.setGbDoScalePrice("0.0");
//                    orders.setGbDoScaleWeight("0.0");
//                    orders.setGbDoStatus(getGbOrderStatusNew());
//                    orders.setGbDoPurchaseUserId(null);
//                    gbDepartmentOrdersService.update(orders);
//                }
//            }
//            gbDPBService.delete(batchId);
//            return R.ok();
//        }else{
//            return R.error(-1,"请刷新数据");
//        }
//    }
}
