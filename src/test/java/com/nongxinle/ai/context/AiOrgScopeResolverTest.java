package com.nongxinle.ai.context;

import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.entity.GbDepartmentUserEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOrgScopeResolverTest {

    private final AiOrgScopeResolver orgResolver = new AiOrgScopeResolver();

    @Test
    void groupManager_orgScope_isGroupWide() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.groupManager(1, 5, 2);
        AiUserContextResolver userResolver = AiDepartmentUserTestRows.resolverReturning(row);
        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(1L);
        req.setDepartmentId(5L);
        req.setMessage("x");
        AiUserContext u = userResolver.resolve(req);
        AiOrgScope s = orgResolver.resolve(u, req);
        assertThat(s.getScopeType()).isEqualTo(AiOrgScopeResolver.SCOPE_GROUP);
    }

    @Test
    void storeManager_scopePinsDepartmentAnchor() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.storeManager(902, 100, 2);
        AiUserContextResolver userResolver = AiDepartmentUserTestRows.resolverReturning(row);
        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(902L);
        req.setDepartmentId(999L);
        req.setMessage("x");
        AiUserContext u = userResolver.resolve(req);
        AiOrgScope s = orgResolver.resolve(u, req);
        assertThat(u.getRoleCode()).isEqualTo(AiRoleCodes.STORE_MANAGER);
        assertThat(s.getDepartmentId()).isEqualTo(100L);
    }

    @Test
    void groupManager_costPhrase_staysGroupWide() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.groupManager(1, 10, 2);
        AiUserContextResolver userResolver = AiDepartmentUserTestRows.resolverReturning(row);
        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(1L);
        req.setDepartmentId(10L);
        req.setMessage("本月成本怎么样");
        AiUserContext u = userResolver.resolve(req);
        AiOrgScope s = orgResolver.resolve(u, req);
        assertThat(s.getScopeType()).isEqualTo(AiOrgScopeResolver.SCOPE_GROUP);
    }

    @Test
    void storeManager_costPhrase_scopeStillPinnedOnAnchorDepartment() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.storeManager(902, 100, 2);
        AiUserContextResolver userResolver = AiDepartmentUserTestRows.resolverReturning(row);
        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(902L);
        req.setDepartmentId(999L);
        req.setMessage("本月成本怎么样");
        AiUserContext u = userResolver.resolve(req);
        AiOrgScope s = orgResolver.resolve(u, req);
        assertThat(s.getDepartmentId()).isEqualTo(100L);
        assertThat(s.getScopeType()).isNotEqualTo(AiOrgScopeResolver.SCOPE_GROUP);
    }
}
