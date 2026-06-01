package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiDepartmentUserTestRows;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.context.AiUserContextResolver;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiResolvedOrgScopeAssemblerStoreModeTest {

    @Mock
    GbDepartmentMapper gbDepartmentMapper;
    @Mock
    AiScopeResolver scopeResolver;

    AiResolvedOrgScopeAssembler sut;

    @BeforeEach
    void setUp() {
        sut = new AiResolvedOrgScopeAssembler(gbDepartmentMapper, scopeResolver);
    }

    @Test
    void groupManager_storeConversationMode_doesNotEnumerateDistributerStores() {
        AiUserContextResolver ur =
                AiDepartmentUserTestRows.resolverReturning(
                        AiDepartmentUserTestRows.groupManager(701, 1, 99));
        AiUserContext ctx = ur.resolve(new AiRunCreateRequest());
        ctx.setUserId(701L);

        GbDepartmentEntity store = new GbDepartmentEntity();
        store.setGbDepartmentId(100);
        store.setGbDepartmentName("测试门店");
        store.setGbDepartmentFatherId(0);
        when(gbDepartmentMapper.selectById(100)).thenReturn(store);

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(701L);
        req.setDepartmentId(100L);
        req.setDistributerId(99L);

        AiResolvedOrgScope org =
                sut.resolveOrgScope(ctx, 100L, req, AiConversationScopeMode.STORE);

        assertThat(org.getScopeType()).isEqualTo(AiResolvedOrgScope.SCOPE_STORE);
        assertThat(org.getVisibleStores()).hasSize(1);
        assertThat(org.getVisibleStores().get(0).getStoreDepartmentId()).isEqualTo(100L);
        verify(gbDepartmentMapper, never()).selectStoreDepartmentIdsUnderDistributer(anyInt());
    }

    @Test
    void groupManager_groupConversationMode_enumeratesDistributerStores() {
        AiUserContextResolver ur =
                AiDepartmentUserTestRows.resolverReturning(
                        AiDepartmentUserTestRows.groupManager(702, 1, 99));
        AiUserContext ctx = ur.resolve(new AiRunCreateRequest());
        ctx.setUserId(702L);

        when(gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(99)).thenReturn(java.util.List.of(10, 11));
        GbDepartmentEntity d10 = new GbDepartmentEntity();
        d10.setGbDepartmentId(10);
        d10.setGbDepartmentName("门店A");
        GbDepartmentEntity d11 = new GbDepartmentEntity();
        d11.setGbDepartmentId(11);
        d11.setGbDepartmentName("门店B");
        when(gbDepartmentMapper.selectById(10)).thenReturn(d10);
        when(gbDepartmentMapper.selectById(11)).thenReturn(d11);

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(702L);
        req.setDistributerId(99L);

        AiResolvedOrgScope org =
                sut.resolveOrgScope(ctx, null, req, AiConversationScopeMode.GROUP);

        assertThat(org.getScopeType()).isEqualTo(AiResolvedOrgScope.SCOPE_GROUP);
        assertThat(org.getVisibleStores()).hasSize(2);
        verify(gbDepartmentMapper).selectStoreDepartmentIdsUnderDistributer(99);
    }
}
