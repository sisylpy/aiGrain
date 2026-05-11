package com.nongxinle.ai.security;

import com.nongxinle.ai.context.AiDepartmentUserTestRows;
import com.nongxinle.ai.context.AiOrgScope;
import com.nongxinle.ai.context.AiOrgScopeResolver;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.entity.GbDepartmentUserEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiPermissionGuardTest {

    private final AiPermissionGuard guard = new AiPermissionGuard();
    private final AiOrgScopeResolver orgs = new AiOrgScopeResolver();

    @Test
    void financeManager_deniesGrossMarginTool() {
        AiUserContextResolver users = new AiUserContextResolver(
                org.mockito.Mockito.mock(com.nongxinle.service.GbDepartmentUserService.class),
                org.mockito.Mockito.mock(com.nongxinle.mapper.GbDepartmentMapper.class));
        AiRunCreateRequest rq = financeManagerRequest();
        AiUserContext uc = users.resolve(rq);
        AiOrgScope os = orgs.resolve(uc, rq);

        AiRunState st = baseState(uc, os, rq.getDepartmentId(), rq.getDistributerId());
        ToolRequest tr = ToolRequest.builder()
                .runId(9L)
                .userId(rq.getUserId())
                .toolName(AiBusinessToolIds.GROSS_MARGIN_CALCULATOR)
                .args(java.util.Map.of())
                .build();

        AiToolInvocationDecision d = guard.evaluateToolInvocation(st, tr);
        assertThat(d.isAllowed()).isFalse();
        assertThat(d.getDenial()).isNotNull();
        assertThat(d.getDenial().getRequiredPermission()).isEqualTo(AiPermissions.VIEW_COST);
    }

    @Test
    void scopedStoreManager_deniesWhenDepartmentMismatch() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.storeManager(
                (int) AiUserContextResolver.TEST_STORE_MANAGER_UID, 100, 2);
        AiUserContextResolver users = AiDepartmentUserTestRows.resolverReturning(row);

        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(AiUserContextResolver.TEST_STORE_MANAGER_UID);
        rq.setDepartmentId(777L);
        rq.setDistributerId(2L);
        rq.setMessage("x");
        AiUserContext uc = users.resolve(rq);
        AiOrgScope os = orgs.resolve(uc, rq);

        AiRunState st = baseState(uc, os, rq.getDepartmentId(), rq.getDistributerId());
        ToolRequest tr = ToolRequest.builder()
                .runId(11L)
                .userId(rq.getUserId())
                .toolName(AiBusinessToolIds.REVENUE_QUERY)
                .args(java.util.Map.of())
                .build();

        assertThat(guard.evaluateToolInvocation(st, tr).isAllowed()).isFalse();
    }

    @Test
    void groupManager_allowsMappedTools() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.groupManager(1, 1, 2);
        AiUserContextResolver users = AiDepartmentUserTestRows.resolverReturning(row);

        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(1L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(2L);
        rq.setMessage("x");
        AiUserContext uc = users.resolve(rq);
        AiOrgScope os = orgs.resolve(uc, rq);
        AiRunState st = baseState(uc, os, rq.getDepartmentId(), rq.getDistributerId());
        ToolRequest tr = ToolRequest.builder()
                .runId(3L)
                .userId(1L)
                .toolName(AiBusinessToolIds.REVENUE_QUERY)
                .args(java.util.Map.of())
                .build();
        assertThat(guard.evaluateToolInvocation(st, tr).isAllowed()).isTrue();
    }

    @Test
    void dishProfitTool_storePurchaser_deniesWithGuidance() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.storePurchaser(9201, 100, 2);
        AiUserContextResolver users = AiDepartmentUserTestRows.resolverReturning(row);
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(9201L);
        rq.setDepartmentId(100L);
        rq.setDistributerId(2L);
        rq.setMessage("菜品毛利怎么样");
        AiUserContext uc = users.resolve(rq);
        AiOrgScope os = orgs.resolve(uc, rq);
        AiRunState st = baseState(uc, os, rq.getDepartmentId(), rq.getDistributerId());
        ToolRequest tr = ToolRequest.builder()
                .runId(9301L)
                .userId(9201L)
                .toolName(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                .args(Map.of())
                .build();
        AiToolInvocationDecision d = guard.evaluateToolInvocation(st, tr);
        assertThat(d.isAllowed()).isFalse();
        assertThat(d.getDenial()).isNotNull();
        assertThat(d.getDenial().getReason()).contains("不能查看菜品毛利");
    }

    @Test
    void dishProfitTool_warehouseManager_deniesWithStockGuidance() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.warehouseManager(9203, 100, 2);
        AiUserContextResolver users = AiDepartmentUserTestRows.resolverReturning(row);
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(9203L);
        rq.setDepartmentId(100L);
        rq.setDistributerId(2L);
        rq.setMessage("菜品毛利怎么样");
        AiUserContext uc = users.resolve(rq);
        AiOrgScope os = orgs.resolve(uc, rq);
        AiRunState st = baseState(uc, os, rq.getDepartmentId(), rq.getDistributerId());
        ToolRequest tr = ToolRequest.builder()
                .runId(9303L)
                .userId(9203L)
                .toolName(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                .args(Map.of())
                .build();
        AiToolInvocationDecision d = guard.evaluateToolInvocation(st, tr);
        assertThat(d.isAllowed()).isFalse();
        assertThat(d.getDenial().getReason()).contains("库存");
    }

    @Test
    void dishProfitTool_groupManager_allows() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.groupManager(1, 1, 2);
        AiUserContextResolver users = AiDepartmentUserTestRows.resolverReturning(row);
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(1L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(2L);
        rq.setMessage("菜品毛利怎么样");
        AiUserContext uc = users.resolve(rq);
        AiOrgScope os = orgs.resolve(uc, rq);
        AiRunState st = baseState(uc, os, rq.getDepartmentId(), rq.getDistributerId());
        ToolRequest tr = ToolRequest.builder()
                .runId(9401L)
                .userId(1L)
                .toolName(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                .args(Map.of())
                .build();
        assertThat(guard.evaluateToolInvocation(st, tr).isAllowed()).isTrue();
    }

    @Test
    void null_runContext_fallsThroughAllow_forUnitTestsCompatibility() {
        AiRunState st = new AiRunState();
        st.setAiUserContext(null);
        ToolRequest tr = ToolRequest.builder()
                .runId(1L)
                .userId(1L)
                .toolName(AiBusinessToolIds.REVENUE_QUERY)
                .build();
        assertThat(guard.evaluateToolInvocation(st, tr).isAllowed()).isTrue();
    }

    private static AiRunCreateRequest financeManagerRequest() {
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(881L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(2L);
        rq.setRoleCode(AiRoleCodes.FINANCE_MANAGER);
        rq.setMessage("毛利");
        return rq;
    }

    private static AiRunState baseState(AiUserContext uc, AiOrgScope os, Long dept, Long dis) {
        return AiRunState.builder()
                .runId(42L)
                .userId(uc.getUserId())
                .departmentId(dept)
                .distributerId(dis)
                .aiUserContext(uc)
                .aiOrgScope(os)
                .build();
    }
}
