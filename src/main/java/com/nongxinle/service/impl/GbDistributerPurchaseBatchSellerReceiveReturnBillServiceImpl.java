package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchSellerReceiveReturnBillService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static com.nongxinle.utils.DateUtils.formatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.GbTypeUtils.getGbDisPurchaseBatchDepUserReceiveFinish;
import static com.nongxinle.utils.GbTypeUtils.getGbDisPurchaseBatchDisUserFinishPay;
import static com.nongxinle.utils.GbTypeUtils.getGbPurchaseGoodsStatusPayFinish;
import static com.nongxinle.utils.GbTypeUtils.getGbPurchaseGoodsStatusStockFinish;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchSellerReceiveReturnBillServiceImpl
        implements GbDistributerPurchaseBatchSellerReceiveReturnBillService {

    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDepartmentGoodsStockReduceService gbDepartmentStockReduceService;
    private final GbDistributerPurchaseBatchService gbDPBService;

    @Override
    public void sellerReceiveReturnBill(GbDistributerPurchaseBatchEntity batchEntity) {
        BigDecimal tuihuo = new BigDecimal(0);
        List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntityList = batchEntity.getGbDPGEntities();
        for (GbDistributerPurchaseGoodsEntity purGoods : purchaseGoodsEntityList) {
            String gbDpgBuySubtotal = purGoods.getGbDpgBuySubtotal();
            tuihuo = tuihuo.add(new BigDecimal(gbDpgBuySubtotal));
            GbDistributerPurchaseGoodsEntity updatePurGoods = gbDPGService.getById(purGoods.getGbDistributerPurchaseGoodsId());
            if (batchEntity.getGbDpbPayType() == 0) {
                updatePurGoods.setGbDpgStatus(getGbPurchaseGoodsStatusPayFinish());
            } else {
                updatePurGoods.setGbDpgStatus(getGbPurchaseGoodsStatusStockFinish());
            }

            updatePurGoods.setGbDpgStockFinishDate(formatWhatDay(0));
            gbDPGService.updateById(updatePurGoods);

            List<GbDepartmentOrdersEntity> gbDepartmentOrdersEntities =
                    purGoods.getGbDepartmentOrdersEntities();
            if (gbDepartmentOrdersEntities.size() > 0) {
                for (GbDepartmentOrdersEntity ordersEntity : gbDepartmentOrdersEntities) {
                    log.debug("orddidid" + ordersEntity.getGbDepartmentOrdersId());
                    GbDepartmentOrdersEntity updateOrders = gbDepartmentOrdersService.queryObject(ordersEntity.getGbDepartmentOrdersId());
                    updateOrders.setGbDoStatus(4);
                    updateOrders.setGbDoBuyStatus(6);
                    gbDepartmentOrdersService.update(updateOrders);
                    Integer gbDoDgsrReturnId = updateOrders.getGbDoDgsrReturnId();
                    GbDepartmentGoodsStockReduceEntity reduceEntity = gbDepartmentStockReduceService.queryObject(gbDoDgsrReturnId);
//                    reduceEntity.setGbDgsrStatus(0);
                    gbDepartmentStockReduceService.update(reduceEntity);
                }
            }
        }

        batchEntity.setGbDpbSubtotal(tuihuo.setScale(1, BigDecimal.ROUND_HALF_UP).toString());
        if (batchEntity.getGbDpbPayType() == 0) {
            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDisUserFinishPay());
        } else {
            batchEntity.setGbDpbStatus(getGbDisPurchaseBatchDepUserReceiveFinish());
        }

        batchEntity.setGbDpbFinishFullTime(formatFullTime());
        gbDPBService.updateById(batchEntity);
    }
}
