package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrossMarginCalculatorToolMissingOutboundTest {

    @Test
    void whenRevenuePresentButAllOutboundZero_noHundredPercentMargin() {
        GrossMarginCalculatorTool tool = new GrossMarginCalculatorTool();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(AiBusinessToolIds.REVENUE_QUERY, env("2026-05-01", "2026-05-10", 1L,
                Map.of("totalRevenue", new BigDecimal("10000"), "days", 10)));
        inputs.put(AiBusinessToolIds.DISH_SALES_QUERY, env("2026-05-01", "2026-05-10", null,
                Map.of("listPriceRevenueTotal", BigDecimal.ZERO)));
        inputs.put(AiBusinessToolIds.STOCK_REDUCE_QUERY, env("2026-05-01", "2026-05-10", 1L,
                Map.of(
                        "productionTotal", BigDecimal.ZERO,
                        "produceTotal", BigDecimal.ZERO,
                        "wasteTotal", BigDecimal.ZERO,
                        "lossTotal", BigDecimal.ZERO)));

        ToolRequest req = ToolRequest.builder().args(Map.of(AiBusinessToolIds.ARG_INPUT_SNAPSHOT, inputs)).build();
        ToolResult r = tool.execute(req);
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) r.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) envelope.get("data");
        assertEquals("毛利率暂不可准确计算", data.get("estimatedGrossMarginPercentDisplay"));
        assertEquals("", data.get("estimatedGrossMarginPercent"));
        assertFalse(Boolean.TRUE.equals(data.get("grossMarginReliable")));
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
