package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.service.GbDistributerPurchaseBatchDisGoodsBatchQueryService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
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
public class GbDistributerPurchaseBatchDisGoodsBatchQueryServiceImpl implements GbDistributerPurchaseBatchDisGoodsBatchQueryService {

    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerPurchaseGoodsService gbDPGService;

    @Override
    public GbDistributerPurchaseBatchEntity getBatchWithOrders(Integer batchId) {
        log.debug("bababbtidid{}", batchId);
        return gbDPBService.queryBatchWithOrders(batchId);
    }

    @Override
    public List<GbDistributerGoodsEntity> listBatchDetailGoodsTree(Integer batchId) {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("batchId", batchId);
        log.debug("mapmcansn{}", queryMap);
        System.out.println("mappapap" + queryMap);
        List<GbDistributerGoodsEntity> gbDistributerGoodsEntities = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
        gbDPGService.fillWastePurGoodsForDisTreeGoods(gbDistributerGoodsEntities, queryMap);
        return gbDistributerGoodsEntities;
    }
}
