package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsFinishPurGoodsToStockService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbJjOrderPurchaseLinkService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatDate;
import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.DateUtils.formatWhatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.formatWhatTime;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.DateUtils.formatWhatYearDayTime;
import static com.nongxinle.utils.DateUtils.getWeek;
import static com.nongxinle.utils.DateUtils.getWeekOfYear;
import static com.nongxinle.utils.GbTypeUtils.getGbOrderStatusReceived;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseGoodsFinishPurGoodsToStockServiceImpl
        implements GbDistributerPurchaseGoodsFinishPurGoodsToStockService {

    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDistributerPurchaseGoodsService gbDpgService;
    private final GbJjOrderPurchaseLinkService gbJjOrderPurchaseLinkService;

    @Override
    public void finishPurGoodsToStock(GbDistributerPurchaseGoodsEntity purGoods) {
        List<GbDepartmentOrdersEntity> nxDepartmentOrdersEntities = purGoods.getGbDepartmentOrdersEntities();
        List<GbDepartmentOrdersEntity> unChoiceOrderList = new ArrayList<>();
        for (GbDepartmentOrdersEntity orders : nxDepartmentOrdersEntities) {
            Boolean hasChoice = orders.getIsNotice();
            log.debug("isisiis" + hasChoice);
            if (Boolean.TRUE.equals(hasChoice)) {
                GbDepartmentOrdersEntity gbDepartmentOrdersEntity =
                        gbDepartmentOrdersService.getById(orders.getGbDepartmentOrdersId());
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

        GbDistributerPurchaseGoodsEntity purchaseGoodsEntity =
                gbDpgService.getById(purGoods.getGbDistributerPurchaseGoodsId());
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

        log.debug("apbccccc" + gbDistributerGoodsEntity.getGbDgControlPrice());
        if (gbDistributerGoodsEntity.getGbDgControlPrice() != null && gbDistributerGoodsEntity.getGbDgControlPrice() == 1) {
            gbDpgService.annotatePurchaseGoodsPriceReason(purchaseGoodsEntity);
        }

        GbDistributerGoodsEntity gbDisGoodsEntity = gbDistributerGoodsService.getById(gbDpgDisGoodsId);
        if (gbDisGoodsEntity.getGbDgControlFresh() != null && gbDisGoodsEntity.getGbDgControlFresh() == 1) {
            log.debug("wasteeieiee" + gbDisGoodsEntity.getGbDgControlFresh());
            int wasteHour = Integer.parseInt(gbDisGoodsEntity.getGbDgFreshWasteHour());
            purchaseGoodsEntity.setGbDpgWasteFullTime(formatWhatFullTime(wasteHour));
        }

        LambdaUpdateWrapper<GbDistributerPurchaseGoodsEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(GbDistributerPurchaseGoodsEntity::getGbDistributerPurchaseGoodsId,
                purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());

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
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDepartmentId,
                    purchaseGoodsEntity.getGbDpgPurchaseDepartmentId());
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
        if (purchaseGoodsEntity.getGbDpgBuyPriceReason() != null
                && !purchaseGoodsEntity.getGbDpgBuyPriceReason().trim().isEmpty()) {
            updateWrapper.set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPriceReason, purchaseGoodsEntity.getGbDpgBuyPriceReason());
        }
        gbDpgService.update(updateWrapper);

        log.debug("unspsosos" + unChoiceOrderList);
        if (!unChoiceOrderList.isEmpty()) {
            gbJjOrderPurchaseLinkService.moveUnconfirmedOrdersToNewPurchaseGoods(
                    purchaseGoodsEntity, unChoiceOrderList, gbDistributerGoodsEntity);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
        List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
        gbDpgService.saveDepartmentStockEntriesByPurchase(
                gbDepartmentOrdersEntities, purGoods.getGbDistributerPurchaseGoodsId());
    }
}
