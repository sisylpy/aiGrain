package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.service.GbDistributerPurchaseBatchDisCheckUnPayBillsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GbDistributerPurchaseBatchDisCheckUnPayBillsServiceImpl implements GbDistributerPurchaseBatchDisCheckUnPayBillsService {

    private final GbDistributerPurchaseBatchService gbDPBService;

    @Override
    public Map<String, Object> buildUnPayBillsSummary(Integer disId, Integer supplierId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        map.put("equalStatus", GbConstants.DistributorPurchaseBatchStatus.RECEIPT_FINISHED);
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatchInfo(map);
        Integer countObj = gbDPBService.queryDisPurchaseBatchCount(map);
        int i = countObj == null ? 0 : countObj;
        Double decimal = 0.0;
        if (i > 0) {
            decimal = gbDPBService.querySupplierUnSettleSubtotal(map);
        }
        double d = decimal == null ? 0.0 : decimal;
        Map<String, Object> map2 = new HashMap<>();
        map2.put("arr", batchEntities);
        map2.put("total", new BigDecimal(d).setScale(1, BigDecimal.ROUND_HALF_UP).toString());
        return map2;
    }
}
