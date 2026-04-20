package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.entity.NxJrdhUserEntity;
import com.nongxinle.service.GbDistributerPurchaseBatchDisPurchaseDetailTypeService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.service.NxJrdhUserService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchDisPurchaseDetailTypeServiceImpl
        implements GbDistributerPurchaseBatchDisPurchaseDetailTypeService {

    private final GbDistributerPurchaseGoodsService gbDPGService;
    private final NxJrdhUserService nxJrdhUserService;

    @Override
    public Map<String, Object> buildPurchaseDetailType(Integer disId, String purUserIds, Integer type, Integer greatId,
                                                       String startDate, String stopDate, String supplierIds) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("purchase", type);
        log.debug("初始map: {}", map);
        if (!purUserIds.equals("-1")) {
            String[] arrGb = purUserIds.split(",");
            List<String> idsGb = new ArrayList<>();
            for (String idGb : arrGb) {
                idsGb.add(idGb);
                if (!idsGb.isEmpty()) {
                    map.put("purUserIds", idsGb);
                }
            }
        }

        if (!supplierIds.equals("-1")) {
            String[] arrGb = supplierIds.split(",");
            List<String> idsGb = new ArrayList<>();
            for (String idGb : arrGb) {
                idsGb.add(idGb);
                if (!idsGb.isEmpty()) {
                    map.put("supplierIds", idsGb);
                }
            }
        }

        Map<String, Object> mapR = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        if (type == GbConstants.PurchaseOrderType.SELF_PURCHASE) {
            map.put("typeNotEqual", 9);
            map.put("dayuStatus", 2);
            map.put("startDate", startDate);
            map.put("stopDate", stopDate);
            if (greatId != -1) {
                map.put("disGoodsGreatId", greatId);
            }
            double subTotal = 0.0;
            Integer integer1 = gbDPGService.queryGbPurchaseGoodsCount(map);
            log.debug("subslslslsl{}", map);
            if (integer1 != null && integer1 > 0) {
                subTotal = gbDPGService.queryPurchaseGoodsSubTotal(map);
            }

            List<GbDepartmentUserEntity> purUserList = gbDPGService.queryPurUserList(map);

            if (!purUserList.isEmpty()) {
                for (GbDepartmentUserEntity userEntity : purUserList) {
                    Map<String, Object> mapUser = new HashMap<>();
                    mapUser.put("user", userEntity);
                    mapUser.put("expanded", Boolean.FALSE);

                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("typeNotEqual", 9);
                    queryMap.put("dayuStatus", 2);
                    queryMap.put("startDate", startDate);
                    queryMap.put("stopDate", stopDate);
                    queryMap.put("disId", map.get("disId"));
                    queryMap.put("purUserId", userEntity.getGbDepartmentUserId());
                    queryMap.put("offset", 0);
                    queryMap.put("limit", 100);
                    if (greatId != -1) {
                        queryMap.put("disGoodsGreatId", greatId);
                    }

                    log.debug("mapppppppp{}", queryMap);
                    queryMap.put("dateOrder", 1);
                    Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
                    List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
                    gbDPGService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
                    Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);
                    mapUser.put("arr", goodsList);
                    mapUser.put("count", integer);
                    mapUser.put("purSubtotal", String.format("%.1f", subTotal1));
                    mapUser.put("expanded", Boolean.FALSE);
                    result.add(mapUser);
                }
                log.debug("subslslsls{}", map);
            }

            BigDecimal total = new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP);
            mapR.put("total", total);
            mapR.put("purUserArr", result);

        } else if (type == GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER) {
            log.debug("=== 执行type=1分支 ===");

            map.put("startDate", startDate);
            map.put("stopDate", stopDate);
            if (greatId != -1) {
                map.put("disGoodsGreatId", greatId);
            }
            double subTotal = 0.0;
            Integer integer1 = gbDPGService.queryGbPurchaseGoodsCount(map);
            log.debug("subslslslsl{}", map);
            if (integer1 != null && integer1 > 0) {
                subTotal = gbDPGService.queryPurchaseGoodsSubTotal(map);
            }
            List<NxJrdhSupplierEntity> supplierEntities = gbDPGService.queryDisPurGoodsSupplierList(map);

            if (!supplierEntities.isEmpty()) {
                for (NxJrdhSupplierEntity supplierEntity : supplierEntities) {
                    Map<String, Object> mapUser = new HashMap<>();
                    mapUser.put("nxJrdhSupplierId", supplierEntity.getNxJrdhSupplierId());
                    mapUser.put("expanded", Boolean.FALSE);
                    Map<String, Object> userView = new HashMap<>();
                    userView.put("nxJrdhsSupplierName", supplierEntity.getNxJrdhsSupplierName());
                    userView.put("nxJrdhSupplierId", supplierEntity.getNxJrdhSupplierId());
                    NxJrdhUserEntity jrdhUserEntity = new NxJrdhUserEntity();
                    if (supplierEntity.getNxJrdhsUserId() != null) {
                        NxJrdhUserEntity loaded = nxJrdhUserService.queryObject(supplierEntity.getNxJrdhsUserId());
                        if (loaded != null) {
                            jrdhUserEntity = loaded;
                        }
                    }
                    userView.put("jrdhUserEntity", jrdhUserEntity);
                    mapUser.put("user", userView);

                    Map<String, Object> queryMap = new HashMap<>();
                    queryMap.put("typeNotEqual", 9);
                    queryMap.put("supplierBuy", 1);
                    queryMap.put("dayuStatus", 2);
                    queryMap.put("startDate", startDate);
                    queryMap.put("stopDate", stopDate);
                    queryMap.put("disId", map.get("disId"));
                    queryMap.put("supplierId", supplierEntity.getNxJrdhSupplierId());
                    queryMap.put("offset", 0);
                    queryMap.put("limit", 100);
                    queryMap.put("dateOrder", 1);
                    if (greatId != -1) {
                        queryMap.put("disGoodsGreatId", greatId);
                    }

                    log.debug("mappppppppSuppp{}", queryMap);
                    Integer integer = gbDPGService.queryGbDisGoodsTreeCount(queryMap);
                    List<GbDistributerGoodsEntity> goodsList = gbDPGService.queryDisTreeGoodsWithPurList(queryMap);
                    gbDPGService.fillWastePurGoodsForDisTreeGoods(goodsList, queryMap);
                    Double subTotal1 = gbDPGService.queryPurchaseGoodsSubTotal(queryMap);
                    mapUser.put("arr", goodsList);
                    mapUser.put("count", integer);
                    mapUser.put("purSubtotal", String.format("%.1f", subTotal1));
                    result.add(mapUser);
                }
            }

            BigDecimal total = new BigDecimal(subTotal).setScale(1, BigDecimal.ROUND_HALF_UP);
            mapR.put("supplierArr", result);
            mapR.put("total", total);
        }

        return mapR;
    }
}
