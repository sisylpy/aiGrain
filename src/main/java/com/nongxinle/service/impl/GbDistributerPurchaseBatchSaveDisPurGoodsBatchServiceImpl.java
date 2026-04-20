package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchSaveDisPurGoodsBatchService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatHour;
import static com.nongxinle.utils.DateUtils.formatWhatMinute;
import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.formatWhatTime;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.DateUtils.formatWhatYearDayTime;
import static com.nongxinle.utils.DateUtils.getWeek;
import static com.nongxinle.utils.GbTypeUtils.getGbPurchaseGoodsStatusProcurement;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchSaveDisPurGoodsBatchServiceImpl
        implements GbDistributerPurchaseBatchSaveDisPurGoodsBatchService {

    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerPurchaseGoodsService gbDPGService;

    @Override
    public GbDistributerPurchaseBatchEntity saveDisPurGoodsBatchGb(GbDistributerPurchaseBatchEntity batchEntity) {
        batchEntity.setGbDpbDate(formatWhatDay(0));
        batchEntity.setGbDpbHour(formatWhatHour(0));
        batchEntity.setGbDpbMinute(formatWhatMinute(0));
        batchEntity.setGbDpbTime(formatWhatTime(0));
        batchEntity.setGbDpbPurchaseMonth(formatWhatMonth(0));
        batchEntity.setGbDpbPurchaseWeek(getWeek(0));
        batchEntity.setGbDpbPurchaseYear(formatWhatYear(0));
        batchEntity.setGbDpbPurchaseFullTime(formatWhatYearDayTime(0));
        batchEntity.setGbDpbPurchaseType(GbConstants.PurchaseBatchOrderMode.MANUAL);
        batchEntity.setGbDpbStatus(GbConstants.DistributorPurchaseBatchStatus.SELLER_UNREAD);
        gbDPBService.save(batchEntity);
        log.debug("savvbabba{}", batchEntity);

        for (GbDistributerPurchaseGoodsEntity gbPurGoods : batchEntity.getGbDPGEntities()) {
            Integer gbDpgDisGoodsId = gbPurGoods.getGbDpgDisGoodsId();
            GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
            List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities = gbPurGoods.getGbDepartmentOrdersEntities();
            List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();
            BigDecimal buytotal = new BigDecimal(0);
            for (GbDepartmentOrdersEntity orders : nxDepartmentOrdersEntities) {
                Boolean hasChoice = orders.getIsNotice();
                if (Boolean.TRUE.equals(hasChoice)) {
                    orders.setGbDoBuyStatus(getGbPurchaseGoodsStatusProcurement());
                    buytotal = buytotal.add(new BigDecimal(orders.getGbDoQuantity()));
                    gbDepartmentOrdersService.update(orders);
                } else {
                    unChoiceOrderList.add(orders);
                }
            }

            Integer newLength = nxDepartmentOrdersEntities.size() - unChoiceOrderList.size();
            GbDistributerPurchaseGoodsEntity purchaseGoodsEntity = gbDPGService.getById(gbPurGoods.getGbDistributerPurchaseGoodsId());
            purchaseGoodsEntity.setGbDpgOrdersAmount(newLength);
            purchaseGoodsEntity.setGbDpgPurchaseType(GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER);
            purchaseGoodsEntity.setGbDpgBatchId(batchEntity.getGbDistributerPurchaseBatchId());
            purchaseGoodsEntity.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.SHARED_TO_SUPPLIER);
            purchaseGoodsEntity.setGbDpgQuantity(buytotal.toString());
            gbDPGService.updateById(purchaseGoodsEntity);

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
                disGoods.setGbDpgPurchaseType(GbConstants.PurchaseOrderType.UN_DETERMINED);
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

        return gbDPBService.queryBatchWithOrders(batchEntity.getGbDistributerPurchaseBatchId());
    }
}
