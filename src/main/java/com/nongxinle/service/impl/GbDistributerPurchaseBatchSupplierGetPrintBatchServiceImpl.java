package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentOrdersEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchSupplierGetPrintBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchSupplierGetPrintBatchServiceImpl
        implements GbDistributerPurchaseBatchSupplierGetPrintBatchService {

    private final GbDepartmentOrdersService gbDepartmentOrdersService;

    @Override
    public List<GbDepartmentOrdersEntity> listOrdersForSupplierPrint(Integer batchId) {
        log.debug("supplierGetPrintBatchGbsupplierGetPrintBatchGb");
        Map<String, Object> map = new HashMap<>();
        map.put("batchId", batchId);
        return gbDepartmentOrdersService.queryDisOrdersListByParams(map);
    }
}
