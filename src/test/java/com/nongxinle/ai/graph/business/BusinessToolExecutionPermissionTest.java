package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiDepartmentUserTestRows;
import com.nongxinle.ai.context.AiOrgScopeResolver;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRegistry;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.service.GbAiDailyRevenueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessToolExecutionPermissionTest {

    @Mock
    AiSseEventPublisher publisher;

    @Test
    void groupManager_executesEveryPlannedTool() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.groupManager(1, 1, 2));
        AiOrgScopeResolver or = new AiOrgScopeResolver();

        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(1L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(2L);
        rq.setMessage("成本");

        AiRunState state = AiRunState.builder()
                .runId(100L)
                .userId(rq.getUserId())
                .departmentId(rq.getDepartmentId())
                .distributerId(rq.getDistributerId())
                .costInsightPath(true)
                .dataPlanTools(List.copyOf(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS))
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-10")
                .aiUserContext(ur.resolve(rq))
                .aiOrgScope(or.resolve(ur.resolve(rq), rq))
                .build();

        AtomicInteger executeCount = new AtomicInteger();
        AiTool tool = countingTool(executeCount);

        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.find(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(tool));

        AiScopeResolver scopeResolver = mock(AiScopeResolver.class);

        GbAiDailyRevenueService revenueSvc = mock(GbAiDailyRevenueService.class);

        BusinessToolExecutionNode node = new BusinessToolExecutionNode(registry, new AiPermissionGuard(), scopeResolver,
                publisher, revenueSvc);
        node.run(state);

        assertThat(executeCount.get()).isEqualTo(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS.size());
        assertThat(state.getToolResults()).containsKey(AiBusinessToolIds.GROSS_MARGIN_CALCULATOR);
        assertThat(state.getPermissionDenials()).isEmpty();
    }

    @Test
    void testStore902_departmentMismatch_skipsToolExecution() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.storeManager(
                        (int) AiUserContextResolver.TEST_STORE_MANAGER_UID, 100, 2));
        AiOrgScopeResolver or = new AiOrgScopeResolver();

        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(AiUserContextResolver.TEST_STORE_MANAGER_UID);
        rq.setDepartmentId(999L);
        rq.setDistributerId(2L);
        rq.setMessage("x");

        AiRunState state = AiRunState.builder()
                .runId(101L)
                .userId(rq.getUserId())
                .departmentId(999L)
                .distributerId(2L)
                .costInsightPath(true)
                .dataPlanTools(List.of(AiBusinessToolIds.REVENUE_QUERY))
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-10")
                .aiUserContext(ur.resolve(rq))
                .aiOrgScope(or.resolve(ur.resolve(rq), rq))
                .build();

        ToolRegistry registry = mock(ToolRegistry.class);

        AiScopeResolver scopeResolver = mock(AiScopeResolver.class);
        when(scopeResolver.resolveDomainStoreDepartmentId(anyInt())).thenAnswer(inv -> inv.getArgument(0));

        GbAiDailyRevenueService revenueSvc = mock(GbAiDailyRevenueService.class);

        BusinessToolExecutionNode node = new BusinessToolExecutionNode(registry, new AiPermissionGuard(), scopeResolver,
                publisher, revenueSvc);
        node.run(state);

        assertThat(state.getPermissionDenials()).isNotEmpty();
        org.mockito.Mockito.verify(registry, org.mockito.Mockito.never()).find(org.mockito.ArgumentMatchers.anyString());
    }

    private static AiTool countingTool(AtomicInteger executeCount) {
        return new AiTool() {
            @Override
            public String name() {
                return "mock-business-tool";
            }

            @Override
            public ToolResult execute(ToolRequest request) {
                executeCount.incrementAndGet();
                return ToolResult.builder().success(true).data(Map.of("data", Map.of())).build();
            }
        };
    }
}
