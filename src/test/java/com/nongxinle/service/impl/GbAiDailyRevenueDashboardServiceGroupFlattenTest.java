package com.nongxinle.service.impl;

import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GbAiDailyRevenueDashboardServiceGroupFlattenTest {

    @Test
    void buildGroupWideIncomeFlattened_derivesMeansFromAggRow() {
        GbDepartmentGoodsStockReduceService stock = mock(GbDepartmentGoodsStockReduceService.class);
        GbAiDailyRevenueDashboardServiceImpl svc = new GbAiDailyRevenueDashboardServiceImpl(stock);

        Map<String, Object> agg = new HashMap<>();
        agg.put("distinctRecordDates", 3);
        agg.put("distinctRecordingDepartments", 1);
        agg.put("totalGrossRevenue", new BigDecimal("1188"));
        agg.put("totalOrders", new BigDecimal("21"));
        agg.put("totalPlatformFee", new BigDecimal("30"));
        agg.put("totalDineIn", new BigDecimal("800"));
        agg.put("totalTakeout", new BigDecimal("388"));
        agg.put("totalTakeoutNetApprox", new BigDecimal("358"));
        agg.put("maxDailyGross", new BigDecimal("500"));
        agg.put("minDailyGrossPositive", new BigDecimal("100"));

        Map<String, Object> out = svc.buildGroupWideIncomeFlattened(agg, 5, 2, "2026-05-01", "2026-05-10", null);

        assertEquals(3, ((Number) out.get("统计天数")).intValue());
        assertEquals("不适用", out.get("盈亏状态").toString());
        String note = out.get("数据口径说明").toString();
        assertTrue(note.contains("集团汇总"));
        assertTrue(note.contains("记账部门"), "null 入账提示应走「记账部门」兜底句，避免 distinctRecordingDepartments 当「家」");
        assertEquals(new BigDecimal("396"), new BigDecimal(out.get("日均营业额").toString()));
        assertEquals(new BigDecimal("7"), new BigDecimal(out.get("日均订单数").toString()));
    }
}
