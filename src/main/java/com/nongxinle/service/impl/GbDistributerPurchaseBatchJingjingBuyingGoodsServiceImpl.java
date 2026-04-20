package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.service.GbDepartmentOrdersService;
import com.nongxinle.service.GbDistributerPurchaseBatchJingjingBuyingGoodsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchJingjingBuyingGoodsServiceImpl implements GbDistributerPurchaseBatchJingjingBuyingGoodsService {

    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDepartmentOrdersService gbDepartmentOrdersService;
    private final GbDistributerService gbDistributerService;

    @Override
    public Map<String, Object> buildBuyingGoodsGb(Integer disId) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("status", 3);
        log.debug("abbdbdbd{}", map);
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatchListWithOrders(map);

        Map<String, Object> map1 = new HashMap<>();
        map1.put("disId", disId);
        map1.put("status", 3);
        map1.put("equalBuyStatus", 0);
        Integer purCountObj = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);
        int purCount = purCountObj == null ? 0 : purCountObj;

        map1.put("equalBuyStatus", null);
        map1.put("dayuBuyStatus", 0);
        map1.put("dayuStatus", -2);
        Integer purCountOneObj = gbDepartmentOrdersService.queryGbDepartmentOrderAmount(map1);
        int purCountOne = purCountOneObj == null ? 0 : purCountOneObj;

        Map<String, Object> map3 = new HashMap<>();
        map3.put("arr", batchEntities);
        map3.put("orderAmount", purCount);
        map3.put("wxAmount", purCountOne);
        map3.put("disInfo", gbDistributerService.queryDistributerInfo(disId));
        return map3;
    }
}
