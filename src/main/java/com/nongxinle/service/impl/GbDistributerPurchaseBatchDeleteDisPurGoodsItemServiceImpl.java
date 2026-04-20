package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.entity.NxJrdhUserEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchDeleteDisPurGoodsItemService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.service.NxJrdhSupplierService;
import com.nongxinle.service.NxJrdhUserService;
import com.nongxinle.service.WeNoticeService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatDayTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchDeleteDisPurGoodsItemServiceImpl
        implements GbDistributerPurchaseBatchDeleteDisPurGoodsItemService {

    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final NxJrdhSupplierService nxJrdhSupplierService;
    private final NxJrdhUserService nxJrdhUserService;
    private final GbDistributerService gbDistributerService;

    @Override
    public boolean deleteDisPurBatchGbItem(Integer id) {
        GbDistributerPurchaseGoodsEntity purGoods = gbDPGService.getById(id);
        Integer gbDpgDisGoodsId = purGoods.getGbDpgDisGoodsId();
        Integer oldSupplierId = purGoods.getGbDpgPurchaseNxSupplierId();
        GbDistributerGoodsEntity gbDistributerGoodsEntity = gbDistributerGoodsService.queryObject(gbDpgDisGoodsId);
        int count = -1;

        log.debug("eqklqlqlq" + purGoods.getGbDpgOrdersAmount() + "fiins" + purGoods.getGbDpgOrdersFinishAmount());
        if (purGoods.getGbDpgOrdersAmount() != purGoods.getGbDpgOrdersFinishAmount()) {
            Integer gbDpgBatchId = purGoods.getGbDpgBatchId();
            Map<String, Object> map1 = new HashMap<>();
            map1.put("batchId", gbDpgBatchId);
            log.debug("bahccmcmamappap" + map1);
            Integer countObj = gbDPGService.queryGbGoodsCount(map1);
            count = countObj == null ? 0 : countObj;
            if (count == 1) {
                gbDPBService.removeById(gbDpgBatchId);
            } else {
                log.debug("mapsusb" + map1);
                map1.put("dayuStatus", 1);
                Integer integer = gbDPGService.queryGbPurchaseGoodsCount(map1);
                Double subTotal = 0.0;
                BigDecimal lasttotal = new BigDecimal(0);
                if (integer != null && integer > 0) {
                    subTotal = gbDPGService.queryPurchaseGoodsSubTotal(map1);
                    lasttotal = new BigDecimal(subTotal).subtract(new BigDecimal(purGoods.getGbDpgBuySubtotal())).setScale(1, BigDecimal.ROUND_HALF_UP);
                }
                GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.getById(gbDpgBatchId);
                batchEntity.setGbDpbSubtotal(lasttotal.toString());
                gbDPBService.updateById(batchEntity);
            }
            // updateById 默认不更新 null 字段，清空批次/金额等必须用 Wrapper 显式 set(..., null)
            Integer purGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
            LambdaUpdateWrapper<GbDistributerPurchaseGoodsEntity> purUw = new LambdaUpdateWrapper<>();
            purUw.eq(GbDistributerPurchaseGoodsEntity::getGbDistributerPurchaseGoodsId, purGoodsId)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.UN_DETERMINED)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgDisGoodsPriceId, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgBatchId, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurUserId, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 0)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgTime, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgBuySubtotal, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPrice, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyQuantity, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyScalePrice, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgBuyScaleQuantity, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseFullTime, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseMonth, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseYear, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseWeek, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseWeekYear, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseNxSupplierId, -1)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgWasteFullTime, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgWarnFullTime, null)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgOrdersFinishAmount, 0)
                    .set(GbDistributerPurchaseGoodsEntity::getGbDpgOrdersWeightAmount, 0);
            log.debug("purururruururur" + purGoodsId);
            gbDPGService.update(null, purUw);

            gbDistributerGoodsEntity.setGbDgGbSupplierId(-1);
            gbDistributerGoodsEntity.setGbDgGoodsType(GbConstants.DistributorGoodsType.SELF_PURCHASE);
            gbDistributerGoodsService.update(gbDistributerGoodsEntity);

            Integer nxDistributerPurchaseGoodsId = purGoods.getGbDistributerPurchaseGoodsId();
            Map<String, Object> map = new HashMap<>();
            map.put("purGoodsId", nxDistributerPurchaseGoodsId);
            List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersByParams(map);
            for (GbDepartmentOrdersEntity orders : ordersEntities) {
                orders.setGbDoBuyStatus(GbConstants.OrderBuyStatus.NEW);
                orders.setGbDoWeight("0.0");
                orders.setGbDoPrice("0.0");
                orders.setGbDoSubtotal("0.0");
                orders.setGbDoScalePrice("0.0");
                orders.setGbDoScaleWeight("0.0");
                orders.setGbDoStatus(GbConstants.DepartmentOrderStatus.NEW);
                orders.setGbDoPurchaseUserId(null);
                orders.setGbDoOrderType(2);
                orders.setGbDoGoodsType(2);
                orders.setGbDoNxDistributerGoodsId(-1);
                orders.setGbDoNxDistributerId(-1);
                orders.setGbDoNxDepartmentOrderId(null);
                gbDepartmentOrdersService.update(orders);
            }

            if (oldSupplierId != -1) {
                NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.getById(oldSupplierId);
                Integer jrdhsUserId = supplierEntity.getNxJrdhsUserId();
                NxJrdhUserEntity nxJrdhUserEntity = nxJrdhUserService.queryObject(jrdhsUserId);
                Integer gbDpgDistributerId = purGoods.getGbDpgDistributerId();
                GbDistributerEntity gbDistributerEntity = gbDistributerService.getById(gbDpgDistributerId);
                log.debug("tuuihuouonoticeeiee");
                if (supplierEntity.getNxJrdhsUserId() != null && nxJrdhUserEntity != null) {
                    String openId = nxJrdhUserEntity.getNxJrdhWxOpenId();
                    if (openId != null && !openId.trim().isEmpty()) {
                        Map<String, WeNoticeService.TemplateData> mapNotice = new HashMap<>();
                        mapNotice.put("date7", new WeNoticeService.TemplateData(formatWhatDayTime(0)));
                        mapNotice.put("thing12", new WeNoticeService.TemplateData("删除订货" + gbDistributerGoodsEntity.getGbDgGoodsName()));
                        if (count == 1) {
                            mapNotice.put("phrase9", new WeNoticeService.TemplateData("订单取消"));
                        } else {
                            mapNotice.put("phrase9", new WeNoticeService.TemplateData("订单变更"));
                        }

                        StringBuilder pathBuilder = new StringBuilder("subPackage/pages/gbMarket/gbReceiveBatch/gbReceiveBatch");
                        pathBuilder.append("?batchId=").append(gbDpgBatchId);
                        pathBuilder.append("&retName=").append(gbDistributerEntity.getGbDistributerName());
                        pathBuilder.append("&from=notification"); // 添加这个参数
                        String path = pathBuilder.toString();
                        log.debug("deleteDisPurBatchGbItem notice path: {}", path);
//                        WeNoticeService.changeOrderSuppliertixingMessageJj(openId, path, mapNotice);
                    } else {
                        log.debug("微信通知发送失败: openId为空");
                    }
                }
            }
            return true;
        }
        return false;
    }
}
