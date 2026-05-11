package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 经营概览路径不返回 {@link AiBusinessToolIds#REVENUE_QUERY} 快照时，毛利计算器须从 overview 取营业额与 meta。
 */
class GrossMarginCalculatorToolOverviewRevenueFallbackTest {

    @Test
    void whenOnlyBusinessOverviewHasRevenue_usesOverviewTotalAndMetaAndTagsDashboardSource() {
        GrossMarginCalculatorTool tool = new GrossMarginCalculatorTool();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(AiBusinessToolIds.REVENUE_QUERY, env("2026-05-01", "2026-05-10", null,
                Map.of("totalRevenue", BigDecimal.ZERO, "days", 0)));
        inputs.put(AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY, env("2026-05-01", "2026-05-10", 42L,
                Map.of(
                        "totalRevenue", new BigDecimal("5000"),
                        "days", 5)));
        inputs.put(AiBusinessToolIds.DISH_SALES_QUERY, env("2026-05-01", "2026-05-10", null,
                Map.of("listPriceRevenueTotal", BigDecimal.ZERO)));
        inputs.put(AiBusinessToolIds.STOCK_REDUCE_QUERY, env("2026-05-01", "2026-05-10", 42L,
                Map.of(
                        "productionTotal", BigDecimal.ZERO,
                        "produceTotal", new BigDecimal("1000"),
                        "wasteTotal", BigDecimal.ZERO,
                        "lossTotal", BigDecimal.ZERO)));

        ToolRequest req = ToolRequest.builder().args(Map.of(AiBusinessToolIds.ARG_INPUT_SNAPSHOT, inputs)).build();
        ToolResult r = tool.execute(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) r.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) envelope.get("data");
        assertEquals("daily_revenue_overview_dashboard", data.get("revenueSource"));
        assertEquals("5000", data.get("basisRevenue"));
        assertEquals(Long.valueOf(42L), envelope.get(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID));
        assertEquals("2026-05-01", envelope.get(AiBusinessToolIds.ARG_START_DATE));
    }

    private static Map<String, Object> env(String start, String end, Long dept, Map<String, Object> innerData) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(AiBusinessToolIds.ARG_START_DATE, start);
        m.put(AiBusinessToolIds.ARG_STOP_DATE, end);
        if (dept != null) {
            m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
        }
        m.put("data", innerData);
        return m;
    }
}
