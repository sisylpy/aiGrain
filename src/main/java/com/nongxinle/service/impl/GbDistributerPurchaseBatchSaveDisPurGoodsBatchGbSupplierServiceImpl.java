package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.entity.NxJrdhUserEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDepartmentService;
import com.nongxinle.service.GbDepartmentUserService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchSaveDisPurGoodsBatchGbSupplierService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.service.NxJrdhSupplierService;
import com.nongxinle.service.NxJrdhUserService;
import com.nongxinle.service.WeNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatDayTime;
import static com.nongxinle.utils.DateUtils.formatWhatHour;
import static com.nongxinle.utils.DateUtils.formatWhatMinute;
import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.formatWhatTime;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.DateUtils.formatWhatYearDayTime;
import static com.nongxinle.utils.DateUtils.getWeek;
import static com.nongxinle.utils.DateUtils.getWeekOfYear;
import static com.nongxinle.utils.GbTypeUtils.getGbDisPurchaseBatchHaveRead;
import static com.nongxinle.utils.GbTypeUtils.getGbOrderBuyStatusProcurement;
import static com.nongxinle.utils.GbTypeUtils.getGbPurchaseGoodsStatusProcurement;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchSaveDisPurGoodsBatchGbSupplierServiceImpl
        implements GbDistributerPurchaseBatchSaveDisPurGoodsBatchGbSupplierService {

    private final NxJrdhSupplierService nxJrdhSupplierService;
    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDepartmentService gbDepartmentService;
    private final GbDistributerService gbDistributerService;
    private final GbDepartmentUserService gbDepartmentUserService;
    private final NxJrdhUserService nxJrdhUserService;
    private final WeNoticeService weNoticeService;

    @Override
    public void saveDisPurGoodsBatchGbSupplier(GbDistributerPurchaseBatchEntity batchEntity) {
        Integer gbDpbSupplierId = batchEntity.getGbDpbSupplierId();

        NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.getById(batchEntity.getGbDpbSupplierId());
        Map<String, Object> map = new HashMap<>();
        map.put("disId", batchEntity.getGbDpbDistributerId());
        map.put("supplierId", batchEntity.getGbDpbSupplierId());
        map.put("status", 1);
        map.put("notEqualPurchaseType", 9);
        log.debug("mapapmaaapa" + map);
        System.out.println("aipapa" + map);
        List<GbDistributerPurchaseBatchEntity> entities = gbDPBService.queryDisPurchaseBatchInfo(map);
        log.debug("enennensisiziizizi" + entities.size());
        Integer gbDistributerPurchaseBatchId = 0;
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
            gbDPBService.save(batchEntity);

            gbDistributerPurchaseBatchId = batchEntity.getGbDistributerPurchaseBatchId();

            for (GbDistributerPurchaseGoodsEntity gbPurGoods : batchEntity.getGbDPGEntities()) {

                Integer gbDpgDisGoodsId = gbPurGoods.getGbDpgDisGoodsId();
                GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
                List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities =
                        gbPurGoods.getGbDepartmentOrdersEntities();
                List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();

                Map<String, Object> mapItem = new HashMap<>();
                mapItem.put("disGoodsId", gbPurGoods.getGbDpgDisGoodsId());
                mapItem.put("supplierId", gbDpbSupplierId);
                mapItem.put("dayuStatus", 2);
                GbDistributerPurchaseGoodsEntity lastItem = gbDPGService.queryPurchaseGoodsLastItem(mapItem);
                GbDistributerPurchaseGoodsEntity purchaseGoodsEntity =
                        gbDPGService.getById(gbPurGoods.getGbDistributerPurchaseGoodsId());

                BigDecimal buyWeight = new BigDecimal(0);
                BigDecimal buySubtotal = new BigDecimal(0);
                BigDecimal weightTotal = new BigDecimal(0);
                for (GbDepartmentOrdersEntity gbDepartmentOrders : nxDepartmentOrdersEntities) {
                    Boolean hasChoice = gbDepartmentOrders.getIsNotice();
                    if (hasChoice) {
                        GbDepartmentOrdersEntity updateOrders =
                                gbDepartmentOrdersService.queryObject(gbDepartmentOrders.getGbDepartmentOrdersId());
                        weightTotal = weightTotal.add(new BigDecimal(gbDepartmentOrders.getGbDoQuantity()));
                        updateOrders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
                        if (lastItem != null) {
                            purchaseGoodsEntity.setGbDpgBuyPrice(lastItem.getGbDpgBuyPrice());
                            updateOrders.setGbDoPrice(lastItem.getGbDpgBuyPrice());
                            if (gbDistributerGoodsEntity.getGbDgGoodsStandardname().equals(updateOrders.getGbDoStandard())) {
                                BigDecimal multiply = new BigDecimal(lastItem.getGbDpgBuyPrice())
                                        .multiply(new BigDecimal(updateOrders.getGbDoQuantity()));
                                updateOrders.setGbDoWeight(updateOrders.getGbDoQuantity());
                                updateOrders.setGbDoSubtotal(multiply.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                                buySubtotal = buySubtotal.add(multiply);
                                buyWeight = buyWeight.add(new BigDecimal(updateOrders.getGbDoQuantity()));
                            }
                        }
                        gbDepartmentOrdersService.update(updateOrders);

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
                gbDPGService.updateById(purchaseGoodsEntity);

                if (unChoiceOrderList.size() > 0) {
                    GbDistributerPurchaseGoodsEntity disGoods = new GbDistributerPurchaseGoodsEntity();
                    disGoods.setGbDpgDistributerId(batchEntity.getGbDpbDistributerId());
                    disGoods.setGbDpgPayType(0);
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
                    gbDPGService.updateById(disGoods);
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
                List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities =
                        gbPurGoods.getGbDepartmentOrdersEntities();

                List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();
                BigDecimal buyWeight = new BigDecimal(0);
                BigDecimal buySubtotal = new BigDecimal(0);
                BigDecimal weightTotal = new BigDecimal(0);
                for (GbDepartmentOrdersEntity gbDepartmentOrders : nxDepartmentOrdersEntities) {
                    Boolean hasChoice = gbDepartmentOrders.getIsNotice();
                    if (hasChoice) {
                        GbDepartmentOrdersEntity updateOrders =
                                gbDepartmentOrdersService.queryObject(gbDepartmentOrders.getGbDepartmentOrdersId());

                        if (lastItem != null) {
                            gbPurGoods.setGbDpgBuyPrice(lastItem.getGbDpgBuyPrice());
                            updateOrders.setGbDoPrice(lastItem.getGbDpgBuyPrice());
                            weightTotal = weightTotal.add(new BigDecimal(updateOrders.getGbDoQuantity()));

                            if (gbDistributerGoodsEntity.getGbDgGoodsStandardname().equals(updateOrders.getGbDoStandard())) {
                                BigDecimal multiply = new BigDecimal(lastItem.getGbDpgBuyPrice())
                                        .multiply(new BigDecimal(updateOrders.getGbDoQuantity()));
                                buyWeight = buyWeight.add(new BigDecimal(updateOrders.getGbDoQuantity()));
                                buySubtotal = buySubtotal.add(multiply);
                                updateOrders.setGbDoWeight(updateOrders.getGbDoQuantity());
                                updateOrders.setGbDoSubtotal(multiply.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
                            }
                        }

                        updateOrders.setGbDoBuyStatus(getGbOrderBuyStatusProcurement());
                        gbDepartmentOrdersService.update(updateOrders);

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

                gbDPGService.updateById(gbPurGoods);
            }
        }
        GbDepartmentEntity departmentEntity = gbDepartmentService.getById(supplierEntity.getNxJrdhsGbDepartmentId());
        GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(batchEntity.getGbDpbDistributerId());
        if (supplierEntity.getNxJrdhsUserId() != null) {

            Map<String, WeNoticeService.TemplateData> mapNotice = new HashMap<>();
            mapNotice.put("time2", new WeNoticeService.TemplateData(formatWhatDayTime(0)));
            mapNotice.put("thing13", new WeNoticeService.TemplateData(departmentEntity.getGbDepartmentName()));
            mapNotice.put("thing8", new WeNoticeService.TemplateData("采购员订货"));
            mapNotice.put("thing10", new WeNoticeService.TemplateData("订货"));
            Integer gbDoOrderUserId = supplierEntity.getNxJrdhsNxPurUserId();
            GbDepartmentUserEntity gbDepartmentUserEntity = gbDepartmentUserService.getById(gbDoOrderUserId);
            mapNotice.put("thing9", new WeNoticeService.TemplateData(gbDepartmentUserEntity.getGbDuWxNickName()));
            log.debug("nociiciiiicicautotootototoototo" + mapNotice);
            Integer nxJrdhsUserId = supplierEntity.getNxJrdhsUserId();
            NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(nxJrdhsUserId);
            StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbReceiveBatch/gbReceiveBatch");
            pathBuilder.append("?batchId=").append(gbDistributerPurchaseBatchId);
            pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
            pathBuilder.append("&from=notification");
            String path = pathBuilder.toString();
            log.debug("EncodedTautoGbSuppliertixingMessageJj: " + path);

            weNoticeService.autoGbSuppliertixingMessageJj(nxJrdhUserEntity.getNxJrdhWxOpenId(), path, mapNotice);
        }
    }
}
