package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerPurchaseBatchSupplierBillsWithStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GbDistributerPurchaseBatchSupplierBillsWithStatusServiceImpl
        implements GbDistributerPurchaseBatchSupplierBillsWithStatusService {

    private final GbDistributerPurchaseBatchService gbDPBService;

    @Override
    public List<GbDistributerPurchaseBatchEntity> queryBatches(Integer supplierId, String status, Integer disId,
                                                               String startDate, String stopDate) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        if ("all".equals(status)) {
            map.put("dayuStatus", 2);
            map.put("notEqualPurchaseType", 9);
        } else if ("allUnPay".equals(status)) {
            map.put("equalStatus", 3);
        } else if ("havePayed".equals(status)) {
            map.put("equalStatus", 4);
        } else if ("unPayBills".equals(status)) {
            map.put("equalStatus", 3);
            map.put("notEqualPurchaseType", 9);
        } else if ("unPayReturnBills".equals(status)) {
            map.put("equalStatus", 3);
            map.put("notEqualPurchaseType", null);
            map.put("purchaseType", 9);
        }

        map.put("startDate", startDate);
        map.put("stopDate", stopDate);
        return gbDPBService.queryDisPurchaseBatchInfo(map);
    }
}
