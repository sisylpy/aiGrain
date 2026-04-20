package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseBatchSupplierEditBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchSupplierEditBatchServiceImpl
        implements GbDistributerPurchaseBatchSupplierEditBatchService {

    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;

    @Override
    public SupplierEditBatchGbResult supplierEditBatchGb(Integer batchId) {
        Map<String, Object> mapIf = new HashMap<>();
        mapIf.put("batchId", batchId);
        mapIf.put("finishAmount", 0);
        log.debug("mapIfCouunt" + mapIf);
        Integer integer = gbDPGService.queryGbPurchaseGoodsCount(mapIf);
        if (integer > 0) {
            return SupplierEditBatchGbResult.alreadyReceived();
        }
        GbDistributerPurchaseBatchEntity gbDisPurBatchEntity = gbDPBService.getById(batchId);
        if (gbDisPurBatchEntity.getGbDpbStatus() == 2) {
            gbDisPurBatchEntity.setGbDpbStatus(0);
            gbDPBService.updateById(gbDisPurBatchEntity);

            Map<String, Object> mapG = new HashMap<>();
            mapG.put("batchId", batchId);
            List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(mapG);
            if (purchaseGoodsEntities.size() > 0) {
                for (GbDistributerPurchaseGoodsEntity purchaseGoodsEntity : purchaseGoodsEntities) {
                    purchaseGoodsEntity.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.SHARED_TO_SUPPLIER);
                    purchaseGoodsEntity.setGbDpgOrdersFinishAmount(0);
                    purchaseGoodsEntity.setGbDpgOrdersWeightAmount(0);
                    gbDPGService.updateById(purchaseGoodsEntity);

                    Map<String, Object> map = new HashMap<>();
                    map.put("purGoodsId", purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
                    List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities =
                            gbDepartmentOrdersService.queryDisOrdersByParams(map);

                    if (gbDepartmentOrdersEntities.size() > 0) {
                        for (GbDepartmentOrdersEntity ordersEntity : gbDepartmentOrdersEntities) {
                            ordersEntity.setGbDoBuyStatus(GbConstants.OrderBuyStatus.SHARED_TO_SUPPLIER);
                            ordersEntity.setGbDoStatus(GbConstants.DepartmentOrderStatus.NEW);
                            gbDepartmentOrdersService.update(ordersEntity);
                        }
                    }
                }
            }
        }
        GbDistributerPurchaseBatchEntity entity = gbDPBService.queryBatchWithOrders(batchId);
        return SupplierEditBatchGbResult.ok(entity);
    }
}
