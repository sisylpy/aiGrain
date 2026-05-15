package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiDepartmentUserTestRows;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.utils.GbConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集团经营概览须走广角 SQL，不得误判为门店子树；与门店话术隔离。
 */
class BusinessToolExecutionGroupWideRoutingRegressionTest {

    private static AiRunState sampleState(AiUserContextResolver ur, AiRunCreateRequest rq, String resolvedScopeType) {
        var uc = ur.resolve(rq);
        AiResolvedOrgScope org = AiResolvedOrgScope.builder()
                .scopeType(resolvedScopeType)
                .distributerId(rq.getDistributerId())
                .requestDepartmentId(rq.getDepartmentId())
                .currentDepartmentId(uc.getDepartmentId())
                .build();
        return AiRunState.builder()
                .runId(1L)
                .userId(rq.getUserId())
                .departmentId(rq.getDepartmentId())
                .distributerId(rq.getDistributerId())
                .aiUserContext(uc)
                .resolvedQueryContext(AiResolvedQueryContext.builder().orgScope(org).build())
                .statStartDate("2026-05-01")
                .statEndDate("2026-05-10")
                .scope(AiQueryScope.builder()
                        .resolvedDepartmentIds(List.of(101, 102))
                        .parentStoreCount(2)
                        .build())
                .build();
    }

    @Test
    void groupManagerApp_viaDbRow_routesWide() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.groupManager(701, 1, 99));
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(701L);
        rq.setDepartmentId(1L);
        rq.setDistributerId(99L);

        AiRunState st = sampleState(ur, rq, AiResolvedOrgScope.SCOPE_GROUP);
        assertThat(BusinessToolExecutionNode.shouldRouteGroupWideBusinessOverview(st)).isTrue();
    }

    @Test
    void groupManagerApp_roleCodeMissingBut_sourceAdmin_fallback_routesWide() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.groupManager(702, 1, 99));
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId(702L);

        AiRunState st = sampleState(ur, rq, AiResolvedOrgScope.SCOPE_GROUP);
        st.getAiUserContext().setRoleCode(null);

        assertThat(st.getAiUserContext().getSourceAdminRole()).isEqualTo(GbConstants.DepartmentUserRole.GROUP_MANAGER_APP);
        assertThat(BusinessToolExecutionNode.shouldRouteGroupWideBusinessOverview(st)).isTrue();
    }

    @Test
    void storeManagerApp_viaDbRow_doesNotRouteWide() {
        AiUserContextResolver ur = AiDepartmentUserTestRows.resolverReturning(
                AiDepartmentUserTestRows.storeManager(AiDepartmentUserTestRows.STORE_MANAGER_TEST_USER_PK, 100, 2));
        AiRunCreateRequest rq = new AiRunCreateRequest();
        rq.setUserId((long) AiDepartmentUserTestRows.STORE_MANAGER_TEST_USER_PK);
        rq.setDepartmentId(100L);

        AiRunState st = sampleState(ur, rq, AiResolvedOrgScope.SCOPE_STORE);
        assertThat(st.getAiUserContext().getRoleCode()).isEqualTo(AiRoleCodes.STORE_MANAGER);
        assertThat(BusinessToolExecutionNode.shouldRouteGroupWideBusinessOverview(st)).isFalse();
    }
}
