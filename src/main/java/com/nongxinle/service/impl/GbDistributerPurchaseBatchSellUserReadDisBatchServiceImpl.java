package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchSellUserReadDisBatchService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchSellUserReadDisBatchServiceImpl implements GbDistributerPurchaseBatchSellUserReadDisBatchService {

    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;

    @Override
    public GbDistributerPurchaseBatchEntity sellUserReadDisBatchGb(GbDistributerPurchaseBatchEntity batch) {
        log.debug("sellUserReadDisBatchGbsellUserReadDisBatchGbsellUserReadDisBatchGb");
        batch.setGbDpbStatus(GbConstants.DistributorPurchaseBatchStatus.SELLER_READ);
        gbDPBService.updateById(batch);
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
                List<GbDepartmentOrdersEntity> ordersEntities =
                        purchaseGoodsEntity.getGbDistributerGoodsEntity().getGbDepartmentOrdersEntities();
                if (ordersEntities.size() > 0) {
                    for (GbDepartmentOrdersEntity ordersEntity : ordersEntities) {
                        //give price
                        ordersEntity.setGbDoPrice(lastItem.getGbDpgBuyPrice());
                        if (gbDistributerGoodsEntity.getGbDgGoodsStandardname().equals(ordersEntity.getGbDoStandard())) {
                            BigDecimal orderSubtotal = new BigDecimal(lastItem.getGbDpgBuyPrice())
                                    .multiply(new BigDecimal(ordersEntity.getGbDoQuantity()));
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

            gbDPGService.updateById(purchaseGoodsEntity);
        }
        return gbDPBService.queryBatchWithOrders(batchId);
    }
}
