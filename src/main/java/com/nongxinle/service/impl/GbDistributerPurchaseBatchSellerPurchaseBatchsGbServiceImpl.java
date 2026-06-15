package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.service.GbDistributerPurchaseBatchSellerPurchaseBatchsGbService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import com.nongxinle.service.GbDistributerService;
import com.nongxinle.service.NxJrdhSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.formatWhatYear;
import static com.nongxinle.utils.DateUtils.getLastMonth;
import static com.nongxinle.utils.DateUtils.getLastTwoMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchSellerPurchaseBatchsGbServiceImpl
        implements GbDistributerPurchaseBatchSellerPurchaseBatchsGbService {

    private final GbDistributerPurchaseBatchService gbDPBService;
    private final GbDistributerService gbDistributerService;
    private final NxJrdhSupplierService nxJrdhSupplierService;

    @Override
    public Map<String, Object> buildSellerDistributerPurchaseBatchsGb(Integer disId, Integer supplierId) {
        List<Map<String, Object>> resultData = new ArrayList<>();
        resultData.add(buildSellerMonthPanel(disId, supplierId, formatWhatMonth(0), "current", true));
        resultData.add(buildSellerMonthPanel(disId, supplierId, getLastMonth(), "last", false));
        resultData.add(buildSellerMonthPanel(disId, supplierId, getLastTwoMonth(), "lastTwo", false));

        Map<String, Object> mapR = new HashMap<>();
        mapR.put("arr", resultData);
        mapR.put("disInfo", gbDistributerService.queryDistributerWithAllDepartments(disId));
        NxJrdhSupplierEntity supplierEntity = nxJrdhSupplierService.getById(supplierId);
        mapR.put("supplierInfo", supplierEntity);
        return mapR;
    }

    /**
     * @param monthBeforeItemData true 时与首月原逻辑一致：arr → month → itemData；false 时与后两月一致：arr → itemData → month
     * @param logTag              仅用于区分日志
     */
    private Map<String, Object> buildSellerMonthPanel(Integer disId, Integer supplierId, String month, String logTag,
                                                      boolean monthBeforeItemData) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        map.put("month", month);
        map.put("year", formatWhatYear(0));
        List<GbDistributerPurchaseBatchEntity> batchEntities = gbDPBService.queryDisPurchaseBatchInfo(map);

        map.put("equalStatus", 3);
        map.put("notEqualPurchaseType", 9);
        double unPayOrderDouble = 0.0;
        double unPayReturn = 0.0;
        double havePayOrderDouble = 0.0;
        double havePayReturn = 0.0;

        Integer unPayCount = gbDPBService.queryDisPurchaseBatchCount(map);
        int uc = unPayCount == null ? 0 : unPayCount;
        if (uc > 0) {
            unPayOrderDouble = gbDPBService.querySupplierUnSettleSubtotal(map);
        }
        map.put("notEqualPurchaseType", null);
        map.put("purchaseType", 9);
        Integer unPayTuihuoCount = gbDPBService.queryDisPurchaseBatchCount(map);
        int utc = unPayTuihuoCount == null ? 0 : unPayTuihuoCount;
        if (utc > 0) {
            unPayReturn = gbDPBService.querySupplierUnSettleSubtotal(map);
        }
        map.put("equalStatus", 4);
        map.put("notEqualPurchaseType", 9);
        map.put("purchaseType", null);
        Integer havePayCount = gbDPBService.queryDisPurchaseBatchCount(map);
        int hpc = havePayCount == null ? 0 : havePayCount;
        if (hpc > 0) {
            havePayOrderDouble = gbDPBService.querySupplierUnSettleSubtotal(map);
        }
        map.put("notEqualPurchaseType", null);
        map.put("purchaseType", 9);
        Integer havePayTuihuoCount = gbDPBService.queryDisPurchaseBatchCount(map);
        int hptc = havePayTuihuoCount == null ? 0 : havePayTuihuoCount;
        if (hptc > 0) {
            havePayReturn = gbDPBService.querySupplierUnSettleSubtotal(map);
        }

        log.debug("sellerMonthPanel[{}] disId={}, supplierId={}, month={}, unPayCount={}, havePayCount={}",
                logTag, disId, supplierId, month, uc, hpc);

        int billCount = uc + hpc;
        double billTotal = unPayOrderDouble + havePayOrderDouble;
        int havePayCountTotal = hpc + hptc;
        double havePayTotl = havePayOrderDouble - havePayReturn;
        double actPayTotal = unPayOrderDouble - unPayReturn;
        int actPayCountTotal = uc + utc;

        Map<String, Object> mapData = new HashMap<>();
        mapData.put("billCount", billCount);
        mapData.put("billTotal", String.format("%.1f", billTotal));
        mapData.put("unPayCount", uc);
        mapData.put("unPayTotal", String.format("%.1f", unPayOrderDouble));
        mapData.put("havePayCount", havePayCountTotal);
        mapData.put("havePayTotal", String.format("%.1f", havePayTotl));
        mapData.put("returnBillCount", utc);
        mapData.put("returnPayTotal", String.format("%.1f", unPayReturn));
        mapData.put("actBillCount", actPayCountTotal);
        mapData.put("actPayTotal", String.format("%.1f", actPayTotal));

        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("arr", batchEntities);
        if (monthBeforeItemData) {
            panel.put("month", month);
            panel.put("itemData", mapData);
        } else {
            panel.put("itemData", mapData);
            panel.put("month", month);
        }
        return panel;
    }
}
