package com.nongxinle.ai.context;

import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.entity.GbDepartmentUserEntity;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiUserContextResolverTest {

    @Test
    void userWithGroupManagerAdmin_mapsFromDbRow() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.groupManager(1, 10, 2);
        AiUserContextResolver resolver = AiDepartmentUserTestRows.resolverReturning(row);

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(1L);
        req.setDepartmentId(10L);
        req.setMessage("x");
        AiUserContext ctx = resolver.resolve(req);
        assertThat(ctx.getSourceAdminRole()).isEqualTo(GbConstants.DepartmentUserRole.GROUP_MANAGER_APP);
        assertThat(ctx.getRoleCode()).isEqualTo(AiRoleCodes.GROUP_MANAGER);
        assertThat(ctx.getPermissions()).containsExactlyInAnyOrderElementsOf(
                AiRoleMapper.permissionsForAiRole(AiRoleCodes.GROUP_MANAGER));
    }

    @Test
    void explicitFinanceManager_missingViewCostPermission() {
        AiUserContextResolver resolver =
                AiDepartmentUserTestRows.resolverReturning(AiDepartmentUserTestRows.financeManager(701, 1, 2));

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(701L);
        req.setDepartmentId(1L);
        req.setMessage("成本");
        AiUserContext ctx = resolver.resolve(req);
        assertThat(ctx.getRoleCode()).isEqualTo(AiRoleCodes.FINANCE_MANAGER);
        assertThat(ctx.getSourceAdminRole()).isEqualTo(GbConstants.DepartmentUserRole.FINANCE_MANAGER_AI_APP);
        assertThat(ctx.getPermissions()).contains(AiPermissions.VIEW_REVENUE).contains(AiPermissions.ACCESS_MARKETING_WORKSPACE)
                .doesNotContain(AiPermissions.VIEW_COST);
    }

    @Test
    void storeManager_rowUsesDepartmentAsAnchor() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.storeManager(
                AiDepartmentUserTestRows.STORE_MANAGER_TEST_USER_PK, 100, 2);
        AiUserContextResolver resolver = AiDepartmentUserTestRows.resolverReturning(row);

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId((long) AiDepartmentUserTestRows.STORE_MANAGER_TEST_USER_PK);
        req.setDepartmentId(999L);
        req.setMessage("x");
        AiUserContext ctx = resolver.resolve(req);
        assertThat(ctx.getDepartmentId()).isEqualTo(100L);
        assertThat(ctx.getStoreId()).isEqualTo(100L);
        assertThat(ctx.getAllowedStoreIds()).containsExactly(100L);
    }

    @Test
    void subDepartmentUser_normalizesAllowedStoreToRoot() {
        GbDepartmentUserEntity row = AiDepartmentUserTestRows.storeManager(550, 101, 2);
        row.setGbDuDepartmentFatherId(100);
        AiUserContextResolver resolver = AiDepartmentUserTestRows.resolverReturning(row);

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(550L);
        req.setMessage("x");
        AiUserContext ctx = resolver.resolve(req);
        assertThat(ctx.getDepartmentId()).isEqualTo(101L);
        assertThat(ctx.getStoreId()).isEqualTo(100L);
        assertThat(ctx.getAllowedStoreIds()).containsExactly(100L);
    }

    @Test
    void marketingManager_onlyWorkspaceCapability() {
        AiUserContextResolver resolver =
                AiDepartmentUserTestRows.resolverReturning(AiDepartmentUserTestRows.marketingManager(881, 1, 2));

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(881L);
        req.setDepartmentId(1L);
        req.setMessage("营销");
        AiUserContext ctx = resolver.resolve(req);
        assertThat(ctx.getPermissions()).containsExactly(AiPermissions.ACCESS_MARKETING_WORKSPACE);
    }
}
