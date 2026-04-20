package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchFinishShareReturnService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.NxJrdhSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatFullTime;
import static com.nongxinle.utils.GbTypeUtils.getGbOrderBuyStatusHavePayFinish;
import static com.nongxinle.utils.GbTypeUtils.getGbOrderStatusReceived;
import static com.nongxinle.utils.GbTypeUtils.getGbPurchaseGoodsStatusWaitReceive;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchFinishShareReturnServiceImpl implements GbDistributerPurchaseBatchFinishShareReturnService {

    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    private final NxJrdhSupplierService nxJrdhSupplierService;

    @Override
    public void finishSharePurGoodsBatchReturn(Integer batchId) {
        log.debug("finishSharePurGoodsBatchReturn batchId={}", batchId);
        GbDistributerPurchaseBatchEntity batch = gbDPBService.getById(batchId);
        Map<String, Object> mapP = new HashMap<>();
        mapP.put("batchId", batchId);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGService.queryOnlyPurGoods(mapP);

        for (GbDistributerPurchaseGoodsEntity purGoods : purchaseGoodsEntities) {
            purGoods.setGbDpgStatus(getGbPurchaseGoodsStatusWaitReceive());
            gbDPGService.updateById(purGoods);

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
                log.debug("bactpey");
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
            log.debug("dafdafas" + supplierEntity);
            //todo
//            if (nxJrdhsUserId == null) {
//                log.debug("dafafads" + nxJrdhsUserId);
//                supplierEntity.setNxJrdhsUserId(batch.getGbDpbSellUserId());
//                nxJrdhSupplierService.update(supplierEntity);
//            }
        }
        gbDPBService.updateById(batch);
    }
}
