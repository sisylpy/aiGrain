package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsSellerUpdatePurGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatFullTime;
import static com.nongxinle.utils.GbTypeUtils.getGbOrderBuyStatusHasWeightAndPrice;
import static com.nongxinle.utils.GbTypeUtils.getGbOrderBuyStatusPrepareing;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseGoodsSellerUpdatePurGoodsServiceImpl
        implements GbDistributerPurchaseGoodsSellerUpdatePurGoodsService {

    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final GbDistributerPurchaseGoodsService gbDpgService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerPurchaseBatchService gbDPBService;

    @Override
    public GbDistributerPurchaseGoodsEntity sellerUpdatePurGoods(GbDistributerPurchaseGoodsEntity purchaseGoodsEntity) {
        if (purchaseGoodsEntity.getGbDpgBuyQuantity() != null
                && !purchaseGoodsEntity.getGbDpgBuyQuantity().trim().isEmpty()) {
            Integer gbDoDisGoodsId = purchaseGoodsEntity.getGbDpgDisGoodsId();
            GbDistributerGoodsEntity gbDisGoodsEntity = gbDistributerGoodsService.queryObject(gbDoDisGoodsId);
            if (gbDisGoodsEntity.getGbDgControlFresh() != null && gbDisGoodsEntity.getGbDgControlFresh() == 1) {
                int wasteHour = Integer.parseInt(gbDisGoodsEntity.getGbDgFreshWasteHour());
                purchaseGoodsEntity.setGbDpgWasteFullTime(formatWhatFullTime(wasteHour));
            }
        }

        gbDpgService.updateById(purchaseGoodsEntity);
        List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities = purchaseGoodsEntity.getGbDepartmentOrdersEntities();
        for (GbDepartmentOrdersEntity orders : gbDepartmentOrdersEntities) {
            log.debug("wieieieieiieieiieie" + orders.getGbDoWeight());
            if (orders.getGbDoWeight() != null && !orders.getGbDoWeight().trim().isEmpty()
                    && !orders.getGbDoWeight().equals("0.0")) {
                BigDecimal decimal1 = new BigDecimal(orders.getGbDoPrice());
                BigDecimal decimal2 = new BigDecimal(orders.getGbDoWeight());
                BigDecimal decimal3 = decimal1.multiply(decimal2).setScale(1, BigDecimal.ROUND_HALF_UP);
                orders.setGbDoSubtotal(decimal3.toString());
                orders.setGbDoBuyStatus(getGbOrderBuyStatusHasWeightAndPrice());
            } else {
                orders.setGbDoBuyStatus(getGbOrderBuyStatusPrepareing());
            }
            gbDepartmentOrdersService.update(orders);
        }

        Map<String, Object> map = new HashMap<>();
        Integer gbDpgBatchId = purchaseGoodsEntity.getGbDpgBatchId();
        map.put("batchId", gbDpgBatchId);
        Double subTotal = gbDpgService.queryPurchaseGoodsSubTotal(map);
        GbDistributerPurchaseBatchEntity batchEntity = gbDPBService.getById(gbDpgBatchId);
        batchEntity.setGbDpbSubtotal(String.format("%.1f", subTotal));
        gbDPBService.updateById(batchEntity);

        return purchaseGoodsEntity;
    }
}
