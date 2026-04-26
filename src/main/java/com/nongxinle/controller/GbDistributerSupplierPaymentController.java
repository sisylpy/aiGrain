package com.nongxinle.controller;

/**
 * @author lpy
 * @date 10-28 13:40
 */

import java.math.BigDecimal;
import java.util.*;

import com.nongxinle.entity.*;
import com.nongxinle.service.*;
import com.nongxinle.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import static com.nongxinle.utils.DateUtils.formatFullTime;
import static com.nongxinle.utils.DateUtils.formatWhatDay;
import static com.nongxinle.utils.GbTypeUtils.getGbOrderBuyStatusHavePayFinish;


@RestController
@RequestMapping("gbdistributersupplierpayment")
public class GbDistributerSupplierPaymentController {
    @Autowired
    private GbDistributerSupplierPaymentService gbDisSupplierPaymentService;
    @Autowired
    private GbDistributerPurchaseBatchService gbDPBService;
    @Autowired
    private GbDistributerPurchaseGoodsService gbDPGoodsService;
    @Autowired
    private GbDepartmentOrdersService gbDepartmentOrdersService;


    @RequestMapping(value = "/disSettleSupplierBills", method = RequestMethod.POST)
    @ResponseBody
    public R disSettleSupplierBills(@RequestBody GbDistributerSupplierPaymentEntity paymentEntity) {

        paymentEntity.setGbDspStatus(0);
        paymentEntity.setGbDspDate(formatWhatDay(0));
        paymentEntity.setGbDspPayFullTime(formatFullTime());
        gbDisSupplierPaymentService.save(paymentEntity);
        Integer gbDisSupplierPaymentId = paymentEntity.getGbDistributerSupplierPaymentId();

        BigDecimal total = new BigDecimal("0.0");

        if (paymentEntity.getGbDspSupplierId() != -1) {
            for (GbDistributerPurchaseBatchEntity batchEntity : paymentEntity.getGbDisPurchaseBatchEntities()) {
                total = total.add(new BigDecimal(batchEntity.getGbDpbSubtotal()));
                batchEntity.setGbDpbStatus(4);
                batchEntity.setGbDpbGbSupplierPaymentId(gbDisSupplierPaymentId);
                gbDPBService.updateById(batchEntity);

                Map<String, Object> map = new HashMap<>();
                map.put("batchId", batchEntity.getGbDistributerPurchaseBatchId());
                System.out.println("whwhwhwhhwhw" + map);
                List<GbDistributerPurchaseGoodsEntity> purchaseGoodsEntities = gbDPGoodsService.queryOnlyPurGoods(map);
                if(purchaseGoodsEntities.size() > 0){
                    for(GbDistributerPurchaseGoodsEntity purchaseGoodsEntity: purchaseGoodsEntities){
                        Map<String, Object> mapO = new HashMap<>();
                        mapO.put("purGoodsId", purchaseGoodsEntity.getGbDistributerPurchaseGoodsId());
                        System.out.println("Fdafjfaksf;laksjf;dasljf;lksad" + mapO);
                        List<GbDepartmentOrdersEntity> ordersEntities = gbDepartmentOrdersService.queryDisOrdersListByParams(mapO);
                        if(ordersEntities.size() > 0){
                            for(GbDepartmentOrdersEntity ordersEntity: ordersEntities){
                                ordersEntity.setGbDoBuyStatus(getGbOrderBuyStatusHavePayFinish());
                                gbDepartmentOrdersService.update(ordersEntity);
                            }
                        }
                    }
                }
            }
        }

        paymentEntity.setGbDspPayTotal(total.toString());
        gbDisSupplierPaymentService.updateById(paymentEntity);

        return R.ok();
    }


    @RequestMapping(value = "/getDistributerPayment/{disId}")
    @ResponseBody
    public R getDistributerPayment(@PathVariable Integer disId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        List<GbDistributerSupplierPaymentEntity> paymentEntities = gbDisSupplierPaymentService.queryPaymentListByParams(map);
        return R.ok().put("data", paymentEntities);
    }


}
