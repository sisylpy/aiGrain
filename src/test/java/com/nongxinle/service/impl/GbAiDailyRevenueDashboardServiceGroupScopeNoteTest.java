package com.nongxinle.service.impl;

import com.nongxinle.service.GbDepartmentGoodsStockReduceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 集团「数据口径说明」入账句须按门店根计，不可用 distinctRecordingDepartments 当「家」。 */
class GbAiDailyRevenueDashboardServiceGroupScopeNoteTest {

    @Test
    void scopeNote_whenAllAnchorsHaveRevenue_omits_redundant_sentence() {
        GbDepartmentGoodsStockReduceService stock = mock(GbDepartmentGoodsStockReduceService.class);
        GbAiDailyRevenueDashboardServiceImpl svc = new GbAiDailyRevenueDashboardServiceImpl(stock);

        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("distinctRecordDates", 2);
        agg.put("distinctRecordingDepartments", 99);
        agg.put("totalGrossRevenue", new BigDecimal("100"));
        agg.put("totalOrders", BigDecimal.ZERO);
        agg.put("totalPlatformFee", BigDecimal.ZERO);
        agg.put("totalDineIn", BigDecimal.ZERO);
        agg.put("totalTakeout", BigDecimal.ZERO);
        agg.put("totalTakeoutNetApprox", BigDecimal.ZERO);
        agg.put("maxDailyGross", BigDecimal.ZERO);
        agg.put("minDailyGrossPositive", BigDecimal.ZERO);

        Map<String, Object> out = svc.buildGroupWideIncomeFlattened(agg, 2, null, "2026-05-01", "2026-05-07", 2);
        String note = out.get("数据口径说明").toString();

        assertThat(note).contains("可见范围内 2 家门店");
        assertThat(note).doesNotContain("本期");
        assertThat(note).doesNotContain("有家日营收入账");
        assertThat(note).doesNotContain("记账部门在行内约 99");
    }

    @Test
    void scopeNote_whenPartialCoverage_sentenceUsesStoreAnchors_not_deptDistinct() {
        GbDepartmentGoodsStockReduceService stock = mock(GbDepartmentGoodsStockReduceService.class);
        GbAiDailyRevenueDashboardServiceImpl svc = new GbAiDailyRevenueDashboardServiceImpl(stock);

        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("distinctRecordDates", 2);
        agg.put("distinctRecordingDepartments", 7);
        agg.put("totalGrossRevenue", new BigDecimal("100"));
        agg.put("totalOrders", BigDecimal.ZERO);
        agg.put("totalPlatformFee", BigDecimal.ZERO);
        agg.put("totalDineIn", BigDecimal.ZERO);
        agg.put("totalTakeout", BigDecimal.ZERO);
        agg.put("totalTakeoutNetApprox", BigDecimal.ZERO);
        agg.put("maxDailyGross", BigDecimal.ZERO);
        agg.put("minDailyGrossPositive", BigDecimal.ZERO);

        Map<String, Object> out = svc.buildGroupWideIncomeFlattened(agg, 3, null, "2026-05-01", "2026-05-07", 1);
        String note = out.get("数据口径说明").toString();

        assertThat(note).contains("本期 1 家门店根部有日营收入账");
        assertThat(note).contains("2 家暂无日营收或未纳入本条汇总（按门店根口径）");
        assertThat(note).doesNotContain("本期约 7");
    }
}
