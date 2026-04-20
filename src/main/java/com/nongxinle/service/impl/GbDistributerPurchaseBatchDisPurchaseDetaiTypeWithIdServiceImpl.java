package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDistributerPurchaseBatchDisPurchaseDetaiTypeWithIdService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchDisPurchaseDetaiTypeWithIdServiceImpl
        implements GbDistributerPurchaseBatchDisPurchaseDetaiTypeWithIdService {

    private final GbDistributerPurchaseGoodsService gbDPGService;

    @Override
    public Map<String, Object> buildPurchaseDetaiTypeWithId(Integer disId, String purUserId, Integer type,
                                                              String startDate, String stopDate, String supplierId) {
        if (type == 0) {
            Map<String, Object> mapUser = new HashMap<>();
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("typeNotEqual", 9);
            queryMap.put("supplierBuy", -1);
            queryMap.put("dayuStatus", 2);
            queryMap.put("startDate", startDate);
            queryMap.put("stopDate", stopDate);
            queryMap.put("disId", disId);
            queryMap.put("purUserId", purUserId);
            queryMap.put("offset", 0);
            queryMap.put("limit", 100);
            queryMap.put("dateOrder", 1);
            log.debug("mapppppppp{}", queryMap);
            Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
            List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
            gbDPGService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
            Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);

            mapUser.put("arr", goodsList);
            mapUser.put("count", integer);
            mapUser.put("purSubtotal", String.format("%.1f", subTotal1));

            Map<String, Object> mapT = new HashMap<>();
            mapT.put("startDate", startDate);
            mapT.put("stopDate", stopDate);
            mapT.put("disId", disId);
            mapT.put("purUserId", purUserId);
            mapT.put("xiaoyuSubtotal", 0);
            Integer countObj = gbDPGService.queryGbGoodsCount(mapT);
            int count = countObj == null ? 0 : countObj;
            double tuitotal = 0.0;
            if (count > 0) {
                tuitotal = gbDPGService.queryPurchaseGoodsSubTotal(mapT);
            }
            mapUser.put("tuiSubtotal", String.format("%.1f", tuitotal));
            return mapUser;
        } else if (type == 1) {
            Map<String, Object> mapUser = new HashMap<>();
            Map<String, Object> queryMap = new HashMap<>();
            queryMap.put("typeNotEqual", 9);
            queryMap.put("supplierBuy", 1);
            queryMap.put("dayuStatus", 2);
            queryMap.put("startDate", startDate);
            queryMap.put("stopDate", stopDate);
            queryMap.put("disId", disId);
            queryMap.put("supplierId", supplierId);
            queryMap.put("offset", 0);
            queryMap.put("limit", 100);
            queryMap.put("dateOrder", 1);
            Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
            List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
            gbDPGService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
            Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);
            mapUser.put("arr", goodsList);
            mapUser.put("count", integer);
            mapUser.put("purSubtotal", String.format("%.1f", subTotal1));

            Map<String, Object> mapT = new HashMap<>();
            mapT.put("startDate", startDate);
            mapT.put("stopDate", stopDate);
            mapT.put("disId", disId);
            mapT.put("supplierId", supplierId);
            mapT.put("xiaoyuSubtotal", 0);
            Integer countObj = gbDPGService.queryGbGoodsCount(mapT);
            int count = countObj == null ? 0 : countObj;
            double tuitotal = 0.0;
            if (count > 0) {
                tuitotal = gbDPGService.queryPurchaseGoodsSubTotal(mapT);
            }
            mapUser.put("tuiSubtotal", String.format("%.1f", tuitotal));

            return mapUser;
        }
        return null;
    }
}
