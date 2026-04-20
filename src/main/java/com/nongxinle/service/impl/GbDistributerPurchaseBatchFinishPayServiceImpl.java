package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.GbDistributerSupplierPaymentEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchFinishPayService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.GbDistributerSupplierPaymentService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatDay;

@Service
@RequiredArgsConstructor
public class GbDistributerPurchaseBatchFinishPayServiceImpl implements GbDistributerPurchaseBatchFinishPayService {

    private final GbDistributerSupplierPaymentService gbDistributerSupplierPaymentService;
    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerPurchaseBatchService gbDPBService;

    @Override
    public void finishPayPurchaseBatchGb(String ids, Integer gbDisId, String total, Integer supplierId, Integer userId) {
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
                purGoods.setGbDpgStatus(GbConstants.PurchaseGoodsStatus.STOCK_FINISHED);
                gbDPGService.updateById(purGoods);
                Map<String, Object> mapGO = new HashMap<>();
                mapGO.put("purGoodsId", purGoods.getGbDistributerPurchaseGoodsId());
                List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities =
                        gbDepartmentOrdersService.queryDisOrdersListByParams(mapGO);
                if (!gbDepartmentOrdersEntities.isEmpty()) {
                    for (GbDepartmentOrdersEntity gbDepartmentOrdersEntity : gbDepartmentOrdersEntities) {
                        gbDepartmentOrdersEntity.setGbDoStatus(GbConstants.DepartmentOrderStatus.RECEIVED);
                        gbDepartmentOrdersEntity.setGbDoBuyStatus(GbConstants.OrderBuyStatus.PAID_FINISHED);
                        gbDepartmentOrdersService.update(gbDepartmentOrdersEntity);
                    }
                }
            }
            GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.getById(Integer.valueOf(id));
            batchEntity.setGbDpbFinishFullTime(formatFullTime());
            batchEntity.setGbDpbStatus(GbConstants.DistributorPurchaseBatchStatus.PAYMENT_FINISHED);
            batchEntity.setGbDpbGbSupplierPaymentId(paymentEntity.getGbDistributerSupplierPaymentId());
            gbDPBService.updateById(batchEntity);
        }
    }
}
