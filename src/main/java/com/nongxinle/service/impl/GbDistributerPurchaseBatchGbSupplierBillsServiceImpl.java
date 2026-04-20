package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.service.GbDistributerPurchaseBatchGbSupplierBillsService;
import com.nongxinle.service.GbDistributerPurchaseBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nongxinle.utils.DateUtils.formatWhatMonth;
import static com.nongxinle.utils.DateUtils.getLastMonth;
import static com.nongxinle.utils.DateUtils.getLastTwoMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class GbDistributerPurchaseBatchGbSupplierBillsServiceImpl implements GbDistributerPurchaseBatchGbSupplierBillsService {

    private final GbDistributerPurchaseBatchService gbDPBService;

    @Override
    public List<Map<String, Object>> buildGbSupplierBills(Integer supplierId, Integer disId) {
        BigDecimal listTotal = new BigDecimal("0.0");
        double unSettleSubtotal = 0.0;

        MonthUnsettled m0 = loadMonthUnsettled(disId, supplierId, formatWhatMonth(0));
        listTotal = listTotal.add(m0.billCount);
        unSettleSubtotal += m0.unsettleAdd;

        MonthUnsettled m1 = loadMonthUnsettled(disId, supplierId, getLastMonth());
        listTotal = listTotal.add(m1.billCount);
        unSettleSubtotal += m1.unsettleAdd;

        MonthUnsettled m2 = loadMonthUnsettled(disId, supplierId, getLastTwoMonth());
        listTotal = listTotal.add(m2.billCount);
        unSettleSubtotal += m2.unsettleAdd;

        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("month", formatWhatMonth(0));
        map1.put("arr", m0.batches);
        map1.put("total", m0.totalDec);

        Map<String, Object> map3 = new LinkedHashMap<>();
        map3.put("month", getLastMonth());
        map3.put("arr", m1.batches);
        map3.put("total", m1.totalDec);

        Map<String, Object> map5 = new LinkedHashMap<>();
        map5.put("month", getLastTwoMonth());
        map5.put("arr", m2.batches);
        map5.put("total", m2.totalDec);

        Map<String, Object> map111 = new HashMap<>();
        map111.put("unSettleSubtotal", unSettleSubtotal);
        map111.put("listTotal", listTotal);

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(map1);
        result.add(map3);
        result.add(map5);
        result.add(map111);
        return result;
    }

    private MonthUnsettled loadMonthUnsettled(Integer disId, Integer supplierId, String month) {
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        map.put("supplierId", supplierId);
        map.put("month", month);
        map.put("dayuStatus", 1);
        List<GbDistributerPurchaseBatchEntity> purchaseBatch = gbDPBService.queryDisPurchaseBatchInfo(map);
        BigDecimal billCount = new BigDecimal(purchaseBatch.size());

        Map<String, Object> map41 = new HashMap<>();
        map41.put("disId", disId);
        map41.put("supplierId", supplierId);
        map41.put("month", month);
        map41.put("dayuStatus", 1);
        map41.put("status", 4);
        log.debug("41mapapapap{}", map41);
        String totalDec = "0";
        double unsettleAdd = 0.0;
        Integer integer = gbDPBService.queryDisPurchaseBatchCount(map41);
        if (integer != null && integer > 0) {
            Double total1 = gbDPBService.querySupplierUnSettleSubtotal(map41);
            double t = total1 == null ? 0.0 : total1;
            unsettleAdd = t;
            totalDec = String.format("%.2f", t);
        }
        return new MonthUnsettled(purchaseBatch, totalDec, billCount, unsettleAdd);
    }

    private static final class MonthUnsettled {
        final List<GbDistributerPurchaseBatchEntity> batches;
        final String totalDec;
        final BigDecimal billCount;
        final double unsettleAdd;

        MonthUnsettled(List<GbDistributerPurchaseBatchEntity> batches, String totalDec,
                       BigDecimal billCount, double unsettleAdd) {
            this.batches = batches;
            this.totalDec = totalDec;
            this.billCount = billCount;
            this.unsettleAdd = unsettleAdd;
        }
    }
}
