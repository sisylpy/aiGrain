package com.nongxinle.ai.harness;

import com.nongxinle.ai.core.AiRunState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BusinessOverviewDishSalesReasonAgentHarnessSupportTest {

    @Test
    void appendFlatHarnessFields_mirrorsIntoMasterBusinessAgentDebug() {
        AiRunState state = new AiRunState();
        LinkedHashMap<String, Object> harness = BusinessOverviewDishSalesReasonAgentHarnessSupport.newHarnessMap(true);
        harness.put(BusinessOverviewDishSalesReasonAgentHarnessSupport.KEY_FINAL_SUMMARY, "测试摘要");
        harness.put(
                BusinessOverviewDishSalesReasonAgentHarnessSupport.KEY_INPUT_PREVIEW,
                Map.of("userMessagePreview", "{\"periodDishSales\":[]}"));
        state.setDishSalesReasonAgentHarnessDebug(harness);
        state.setMasterBusinessAgentDebug(new LinkedHashMap<>(Map.of("masterAgentUsed", true)));

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        BusinessOverviewDishSalesReasonAgentHarnessSupport.appendFlatHarnessFields(out, state);

        assertEquals(true, out.get(BusinessOverviewDishSalesReasonAgentHarnessSupport.KEY_ENABLED));
        assertEquals("测试摘要", out.get(BusinessOverviewDishSalesReasonAgentHarnessSupport.KEY_FINAL_SUMMARY));
        @SuppressWarnings("unchecked")
        Map<String, Object> md = (Map<String, Object>) out.get("masterBusinessAgentDebug");
        assertNotNull(md);
        assertEquals(true, md.get("masterAgentUsed"));
        assertEquals("测试摘要", md.get(BusinessOverviewDishSalesReasonAgentHarnessSupport.KEY_FINAL_SUMMARY));
    }
}
