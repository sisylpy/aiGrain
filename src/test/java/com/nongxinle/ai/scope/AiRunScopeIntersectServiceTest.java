package com.nongxinle.ai.scope;

import com.nongxinle.ai.context.AiOrgScope;
import com.nongxinle.ai.context.AiOrgScopeResolver;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiRoleCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRunScopeIntersectServiceTest {

    @Mock
    AiScopeResolver scopeResolver;
    @Mock
    AiOrgScopeResolver orgScopeResolver;

    @InjectMocks
    AiRunScopeIntersectService sut;

    @BeforeEach
    void stubs() {
        when(orgScopeResolver.resolve(any(), any(AiRunCreateRequest.class))).thenReturn(
                AiOrgScope.builder().scopeType(AiOrgScopeResolver.SCOPE_GROUP).build());
        when(scopeResolver.departmentTypeCountsForIds(anyList())).thenReturn(Map.of());
    }

    @Test
    void groupBoss_doesNotChangeRequestedDepartmentFather() {
        AiUserContext ctx = AiUserContext.builder().roleCode(AiRoleCodes.GROUP_MANAGER).departmentId(1L).build();
        AiRunState st = AiRunState.builder().userId(1L).departmentId(88L).distributerId(2L).aiUserContext(ctx).build();

        when(scopeResolver.listStoreDepartmentIdsUnderDistributer(anyInt())).thenReturn(List.of(10, 11));
        when(scopeResolver.collectSubtreeDepartmentIds(10, null)).thenReturn(List.of(10, 101));
        when(scopeResolver.collectSubtreeDepartmentIds(11, null)).thenReturn(List.of(11, 102));

        sut.applyIntersection(st, null);

        assertThat(st.getDepartmentId()).isEqualTo(88L);
        assertThat(st.getScope()).isNotNull();
        assertThat(st.getScope().getResolvedDepartmentIds()).containsExactly(10, 11, 101, 102);
        assertThat(st.getScope().getParentStoreCount()).isEqualTo(2);
    }

    @Test
    void storeManager_foreignRequestRoot_clampsToAnchor() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_MANAGER)
                .departmentId(100L)
                .userId(AiUserContextResolver.TEST_STORE_MANAGER_UID)
                .distributerId(2L)
                .build();
        AiRunState st = AiRunState.builder()
                .userId(902L)
                .departmentId(1L)
                .distributerId(2L)
                .aiUserContext(ctx)
                .build();

        when(scopeResolver.collectSubtreeDepartmentIds(100, null)).thenReturn(List.of(100, 101));
        when(scopeResolver.collectSubtreeDepartmentIds(1, null)).thenReturn(List.of(1, 2, 3));

        sut.applyIntersection(st, null);

        assertThat(st.getDepartmentId()).isEqualTo(100L);
        assertThat(st.getScopeConvergenceNote()).isEqualTo(AiAnswerBoundary.SCOPE_CLAMP_STORE_FRONT);
    }

    @Test
    void storeManager_matchingRoot_keepsDepartment() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_MANAGER)
                .departmentId(100L)
                .distributerId(2L)
                .build();
        AiRunState st = AiRunState.builder().userId(200L).departmentId(100L).distributerId(2L).aiUserContext(ctx).build();

        List<Integer> sub = List.of(100, 101);
        when(scopeResolver.collectSubtreeDepartmentIds(100, null)).thenReturn(sub);

        sut.applyIntersection(st, null);

        assertThat(st.getDepartmentId()).isEqualTo(100L);
        assertThat(st.getScope().getResolvedDepartmentIds()).containsExactly(100, 101);
        assertThat(st.getScopeConvergenceNote()).isNull();
    }

    @Test
    void nonBoss_nullDepartment_defaultsToAnchor() {
        AiUserContext ctx = AiUserContext.builder()
                .roleCode(AiRoleCodes.STORE_MANAGER)
                .departmentId(55L)
                .distributerId(9L)
                .build();
        AiRunState st = AiRunState.builder().userId(3L).departmentId(null).distributerId(9L).aiUserContext(ctx).build();

        List<Integer> sub = List.of(55, 56);
        when(scopeResolver.collectSubtreeDepartmentIds(55, null)).thenReturn(sub);

        sut.applyIntersection(st, null);

        assertThat(st.getDepartmentId()).isEqualTo(55L);
        assertThat(st.getScopeConvergenceNote()).isEqualTo(AiAnswerBoundary.SCOPE_CLAMP_STORE_FRONT);
    }
}
