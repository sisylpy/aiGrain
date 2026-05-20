package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiDepartmentUserTestRows;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VIEW_COST 缺失时结构化成本诊断跳过，并附带 permissionDenied。
 */
@ExtendWith(MockitoExtension.class)
class CostDiagnosisPermissionDeniedTest {

    @Mock
    AiSseEventPublisher publisher;

    private final AiPermissionGuard permissionGuard = new AiPermissionGuard();

    @Test
    void financeManager_withoutViewCost_skipsDiagnosisStructure() {
        AiUserContextResolver ur =
                AiDepartmentUserTestRows.resolverReturning(AiDepartmentUserTestRows.financeManager(771, 1, 2));

        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(771L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(2L);
        rq.setMessage("毛利");

        var uc = ur.resolve(rq);

        AiRunState state = AiRunState.builder()
                .runId(505L)
                .userId(rq.getUserId())
                .departmentId(1L)
                .distributerId(2L)
                .costInsightPath(true)
                .dataPlanTools(List.copyOf(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS))
                .aiUserContext(uc)
                .resolvedQueryContext(AiResolvedQueryContext.builder()
                        .orgScope(AiResolvedOrgScope.builder()
                                .scopeType(AiResolvedOrgScope.SCOPE_DEPARTMENT)
                                .distributerId(2L)
                                .requestDepartmentId(1L)
                                .currentDepartmentId(1L)
                                .build())
                        .build())
                .build();
        preloadTools(state);

        CostDiagnosisAgentNode node = new CostDiagnosisAgentNode(publisher, permissionGuard);
        node.applyIfApplicable(state);

        assertThat(state.getCostDiagnosisResult()).isNull();
        assertThat(state.getPermissionDenials()).isNotEmpty();
        assertThat(state.getPermissionDenials().get(0).getRequiredPermission()).isEqualTo(AiPermissions.VIEW_COST);
    }

    private static void preloadTools(AiRunState state) {
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, envData(Map.of(
                "totalRevenue", new BigDecimal("100"),
                "days", 1)));
        state.getToolResults().put(AiBusinessToolIds.PURCHASE_OVERVIEW, envData(Map.of(
                "purchaseOverview", Map.of(
                        "totalPurchaseAmount", "1",
                        "purchaseOrderCount", 1))));
        state.getToolResults().put(AiBusinessToolIds.STOCK_REDUCE_QUERY, envData(Map.of(
                "productionTotal", BigDecimal.ZERO,
                "produceTotal", BigDecimal.ZERO,
                "wasteTotal", BigDecimal.ZERO,
                "lossTotal", BigDecimal.ZERO)));
        state.getToolResults().put(AiBusinessToolIds.DISH_PROFIT_ANALYSIS, envData(Map.of(
                "businessInsightSummary", Map.of("totalListPriceRevenue", BigDecimal.ZERO))));
    }

    private static Map<String, Object> envData(Map<String, Object> payload) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("data", payload);
        return env;
    }
}
