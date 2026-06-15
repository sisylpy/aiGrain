package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.security.AiToolInvocationDecision;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 成本主线收口：采购有发生、核销侧全 0 + 内部毛利推导标示不可靠 → data_incomplete 与克制摘要。
 *
 * @see docs/TODO_MULTI_AGENT.md 「成本分析主线收口」
 */
@ExtendWith(MockitoExtension.class)
class CostDiagnosisAgentNodeDataIncompleteScenarioTest {

    @Mock
    AiSseEventPublisher publisher;

    @Mock
    AiPermissionGuard permissionGuard;

    @InjectMocks
    CostDiagnosisAgentNode node;

    @BeforeEach
    void allowDiagnosisByDefault() {
        when(permissionGuard.evaluateCostDiagnosisAgent(any())).thenReturn(AiToolInvocationDecision.allow());
    }

    @Test
    void purchaseWithoutOutboundMarksDataIncompleteSummaryAndRisk() {
        AiRunState state = new AiRunState();
        state.setRunId(1L);
        state.setDepartmentId(1L);
        state.setDistributerId(2L);
        state.setCostInsightPath(true);
        state.setDataPlanTools(List.copyOf(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS));

        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, envData(Map.of(
                "totalRevenue", new BigDecimal("10000"),
                "days", 10)));
        state.getToolResults().put(AiBusinessToolIds.PURCHASE_OVERVIEW, envData(Map.of(
                "purchaseOverview", Map.of(
                        "totalPurchaseAmount", "3303",
                        "purchaseOrderCount", 1))));
        Map<String, Object> dishProfitData = new LinkedHashMap<>();
        dishProfitData.put("businessInsightSummary", Map.of("totalActualRevenue", BigDecimal.ZERO));
        state.getToolResults().put(AiBusinessToolIds.DISH_PROFIT_ANALYSIS, envData(dishProfitData));
        state.getToolResults().put(AiBusinessToolIds.STOCK_REDUCE_QUERY, envData(Map.of(
                "productionTotal", BigDecimal.ZERO,
                "produceTotal", BigDecimal.ZERO,
                "wasteTotal", BigDecimal.ZERO,
                "lossTotal", BigDecimal.ZERO)));

        node.applyIfApplicable(state);

        AiCostDiagnosisResult r = state.getCostDiagnosisResult();
        assertThat(r).isNotNull();
        assertThat(r.getRiskLevel()).isEqualTo("data_incomplete");
        assertThat(r.getSummary()).contains("成本判断还不完整").contains("入库、核销、出库链路");

        boolean marginMetricText = false;
        for (Map<String, Object> m : r.getKeyMetrics()) {
            if ("估算毛利率%".equals(m.get("name"))) {
                marginMetricText = true;
                assertThat(String.valueOf(m.get("value"))).doesNotContain("100");
                break;
            }
        }
        assertThat(marginMetricText).isTrue();

        assertThat(r.getFindings()).anyMatch(s -> s.contains("链路断点") || s.contains("核销侧"));
    }

    private static Map<String, Object> envData(Map<String, Object> payload) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("data", payload);
        return env;
    }
}
