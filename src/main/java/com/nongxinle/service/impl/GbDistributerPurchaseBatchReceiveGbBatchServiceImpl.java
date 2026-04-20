package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentDisGoodsEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPayListEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentDisGoodsService;
import com.nongxinle.service.GbDepartmentGoodsStockService;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPayListService;
import com.nongxinle.service.GbDistributerPurchaseBatchReceiveGbBatchService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbTypeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatDate;
import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.DateUtils.getTimeStamp;
import static com.nongxinle.utils.DateUtils.getWeek;
import static com.nongxinle.utils.DateUtils.getWeekOfYear;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchReceiveGbBatchServiceImpl implements GbDistributerPurchaseBatchReceiveGbBatchService {

    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDepartmentDisGoodsService gbDepartmentDisGoodsService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDepartmentGoodsStockService gbDepartmentGoodsStockService;
    private final GbDistributerService gbDistributerService;
    private final GbDistributerPayListService gbDistributerPayListService;

    @Override
    public Outcome receiveGbBatch(Integer id) {
        int orderCount = 0;
        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.getById(id);
        Integer supplierId = batchEntity.getGbDpbSupplierId();
        if (batchEntity.getGbDpbStatus() != 2) {
            return Outcome.STATUS_CHANGED;
        }
        Map<String, Object> map1 = new HashMap<>();
        map1.put("batchId", id);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = gbDPGService.queryOnlyPurGoods(map1);
        if (purchaseGoodsEntityList.size() > 0) {
            for (GbDistributerPurchaseGoodsEntity purchaseGoodsEntity : purchaseGoodsEntityList) {
                if (purchaseGoodsEntity.getGbDpgStatus() == 2) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("purGoodsId", purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
                    log.debug("bpuuururuurrrur{}", map);
                    List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
                    if (ordersEntities.size() > 0) {
                        for (GbDepartmentOrdersEntity order : ordersEntities) {
                            Integer gbDoStatus = order.getGbDoStatus();
                            orderCount++;
                            if (gbDoStatus == 2) {
                                Integer gbDoDepDisGoodsId = order.getGbDoDepDisGoodsId();
                                GbDepartmentDisGoodsEntity departmentDisGoodsEntity = gbDepartmentDisGoodsService.getById(gbDoDepDisGoodsId);

                                if (departmentDisGoodsEntity.getGbDdgOrderDate() != null
                                        && !departmentDisGoodsEntity.getGbDdgOrderDate().trim().isEmpty()) {
                                    if (departmentDisGoodsEntity.getGbDdgOrderPrice() != null
                                            && !departmentDisGoodsEntity.getGbDdgOrderPrice().trim().isEmpty()
                                            && order.getGbDoPrice() != null && !order.getGbDoPrice().trim().isEmpty()) {
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
                                log.debug("stooosrooriri{}", order.getGbDoPrice());
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
                                if (departmentDisGoodsEntity.getGbDdgShowStandardId() != -1) {
                                    String gbDdgShowStandardScale = departmentDisGoodsEntity.getGbDdgShowStandardScale();
                                    BigDecimal divide = new BigDecimal(order.getGbDoWeight()).divide(
                                            new BigDecimal(gbDdgShowStandardScale), 1, BigDecimal.ROUND_HALF_UP);
                                    stockEntity.setGbDgsRestWeightShowStandard(divide.toString());
                                    stockEntity.setGbDgsRestWeightShowStandardName(departmentDisGoodsEntity.getGbDdgShowStandardName());
                                }

                                String gbDpgWasteFullTime1 = purchaseGoodsEntity.getGbDpgWasteFullTime();
                                if (gbDpgWasteFullTime1 != null && !gbDpgWasteFullTime1.trim().isEmpty()) {
                                    stockEntity.setGbDgsWasteFullTime(purchaseGoodsEntity.getGbDpgWasteFullTime());
                                    String gbDpgWasteFullTime = purchaseGoodsEntity.getGbDpgWasteFullTime();
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                                    Date dateWaste = null;
                                    try {
                                        if (gbDpgWasteFullTime != null && !gbDpgWasteFullTime.trim().isEmpty()) {
                                            dateWaste = dateFormat.parse(gbDpgWasteFullTime);
                                        }
                                    } catch (ParseException e) {
                                        log.warn("parse gbDpgWasteFullTime failed: {}", gbDpgWasteFullTime, e);
                                    }
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
                                log.debug("rusosossltoccvbbbbb{}", stockEntity.getGbDgsPrice());
                                gbDepartmentGoodsStockService.save(stockEntity);

                                orderAddDepDisGoods(order, stockEntity, gbDoDepDisGoodsId);

                                order.setGbDoStatus(GbConstants.DepartmentOrderStatus.RECEIVED);
                                order.setGbDoBuyStatus(GbConstants.OrderBuyStatus.PURCHASE_LINE_FINISHED);
                                order.setGbDoArriveDate(formatWhatDay(0));
                                order.setGbDoArriveWeeksYear(getWeekOfYear(0));
                                order.setGbDoArriveWhatDay(getWeek(0));
                                order.setGbDoArriveOnlyDate(formatWhatDate(0));
                                order.setGbDoArriveDate(formatWhatDay(0));
                                log.debug("getGbOrderBuyStatusUnPayFinishgetGbOrderBuyStatusUnPayFinish==={}", order.getGbDepartmentOrdersId());
                                gbDepartmentOrdersService.update(order);

                            } else {
                                return Outcome.ORDER_NOT_WAIT_RECEIVE;
                            }
                        }
                    }
                    purchaseGoodsEntity.setGbDpgOrdersFinishAmount(purchaseGoodsEntity.getGbDpgOrdersAmount());
                    if (purchaseGoodsEntity.getGbDpgPayType() == 0) {
                        purchaseGoodsEntity.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.PAY_FINISHED);
                    } else {
                        purchaseGoodsEntity.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.STOCK_FINISHED);
                    }
                    purchaseGoodsEntity.setGbDpgStockFinishDate(formatWhatDay(0));

                    log.debug("updatpuurururrurr");
                    gbDPGService.updateById(purchaseGoodsEntity);
                }
            }

            if (batchEntity.getGbDpbPayType() == 0) {
                batchEntity.setGbDpbStatus(GbConstants.DistributorPurchaseBatchStatus.PAYMENT_FINISHED);
            } else {
                batchEntity.setGbDpbStatus(GbConstants.DistributorPurchaseBatchStatus.RECEIPT_FINISHED);
            }
            batchEntity.setGbDpbFinishFullTime(formatFullTime());
            gbDPBService.updateById(batchEntity);

            Integer gbDoDistributerId = batchEntity.getGbDpbDistributerId();
            GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDoDistributerId);
            GbDistributerPayListEntity payListEntity = new GbDistributerPayListEntity();
            payListEntity.setGbNdplPaySubtotal(Integer.valueOf(orderCount).toString());
            payListEntity.setGbNdplPayTime(formatFullTime());
            payListEntity.setGbNdplPayDate(formatWhatDay(0));
            payListEntity.setGbNdplPayMonth(formatWhatMonth(0));
            payListEntity.setGbNdplPayYear(formatWhatYear(0));
            payListEntity.setGbNdplStatus(0);
            payListEntity.setGbNdplType(GbTypeUtils.getGbDisPayBatchFinish());
            payListEntity.setGbNdplRestPoints(gbDistributerEntity.getGbDistributerBuyQuantity());
            payListEntity.setGbNdplGbDisId(gbDistributerEntity.getGbDistributerId());
            payListEntity.setGbNdplNxSupplierId(supplierId);
            payListEntity.setGbNdplGbPbId(batchEntity.getGbDistributerPurchaseBatchId());
            payListEntity.setGbNdplGbDisGoodsId(-1);
            gbDistributerPayListService.save(payListEntity);

            BigDecimal decimal = new BigDecimal(gbDistributerEntity.getGbDistributerBuyQuantity());
            BigDecimal decimal1 = new BigDecimal(orderCount);
            BigDecimal add = decimal.subtract(decimal1);
            gbDistributerEntity.setGbDistributerBuyQuantity(add.toString());
            gbDistributerService.updateById(gbDistributerEntity);

            return Outcome.OK;
        }
        return Outcome.NO_PURCHASE_LINES;
    }

    private void orderAddDepDisGoods(GbDepartmentOrdersEntity ordersEntity, GbDepartmentGoodsStockEntity stockEntity,
                                     Integer depDisGoodsId) {

        BigDecimal stockSubtotal = new BigDecimal(stockEntity.getGbDgsSubtotal());
        BigDecimal stockWeight = new BigDecimal(stockEntity.getGbDgsWeight());
        BigDecimal subTotal = new BigDecimal(0);
        BigDecimal weight = new BigDecimal(0);
        GbDepartmentDisGoodsEntity depDisGoodsEntity = gbDepartmentDisGoodsService.getById(depDisGoodsId);
        subTotal = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalSubtotal()).add(stockSubtotal);
        weight = new BigDecimal(depDisGoodsEntity.getGbDdgStockTotalWeight()).add(stockWeight);
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
        gbDepartmentDisGoodsService.updateById(depDisGoodsEntity);
    }
}
