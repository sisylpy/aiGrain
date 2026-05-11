package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiOrgScopeResolver;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.security.AiRoleCodes;
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
        AiUserContextResolver ur = new AiUserContextResolver(
                org.mockito.Mockito.mock(com.nongxinle.service.GbDepartmentUserService.class),
                org.mockito.Mockito.mock(com.nongxinle.mapper.GbDepartmentMapper.class));
        AiOrgScopeResolver or = new AiOrgScopeResolver();

        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(771L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(2L);
        rq.setRoleCode(AiRoleCodes.FINANCE_MANAGER);
        rq.setMessage("毛利");

        var uc = ur.resolve(rq);
        var os = or.resolve(uc, rq);

        AiRunState state = AiRunState.builder()
                .runId(505L)
                .userId(rq.getUserId())
                .departmentId(1L)
                .distributerId(2L)
                .costInsightPath(true)
                .dataPlanTools(List.copyOf(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS))
                .aiUserContext(uc)
                .aiOrgScope(os)
                .build();
        preloadTools(state);

        CostDiagnosisAgentNode node = new CostDiagnosisAgentNode(publisher, permissionGuard);
        node.run(state);

        assertThat(state.getCostDiagnosisResult()).isNull();
        assertThat(state.getPermissionDenials()).isNotEmpty();
        assertThat(state.getPermissionDenials().get(0).getRequiredPermission()).isEqualTo(AiPermissions.VIEW_COST);
    }

    private static void preloadTools(AiRunState state) {
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, envData(Map.of(
                "totalRevenue", new BigDecimal("100"),
                "days", 1)));
        state.getToolResults().put(AiBusinessToolIds.PURCHASE_QUERY, envData(Map.of("purchaseSubTotal", BigDecimal.ONE)));
        state.getToolResults().put(AiBusinessToolIds.STOCK_REDUCE_QUERY, envData(Map.of(
                "productionTotal", BigDecimal.ZERO,
                "produceTotal", BigDecimal.ZERO,
                "wasteTotal", BigDecimal.ZERO,
                "lossTotal", BigDecimal.ZERO)));
        state.getToolResults().put(AiBusinessToolIds.DISH_SALES_QUERY, envData(Map.of("listPriceRevenueTotal", BigDecimal.ZERO)));
        state.getToolResults().put(AiBusinessToolIds.GROSS_MARGIN_CALCULATOR, envData(Map.of(
                "grossMarginReliable", false,
                "estimatedGrossMarginPercent", "0",
                "basisRevenue", "100")));
    }

    private static Map<String, Object> envData(Map<String, Object> payload) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("data", payload);
        return env;
    }
}
