package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExplicitStoreMentionNarrowingTest {

    @Test
    void compoundPurchaseQuestion_narrowsGroupOrgToNamedStore() {
        AiResolvedOrgScope group = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(1L)
                .visibleStores(List.of(
                        AiStoreScopeDTO.builder().storeDepartmentId(101L).storeName("AAA").build(),
                        AiStoreScopeDTO.builder().storeDepartmentId(102L).storeName("汀兰餐厅").build()))
                .build();
        AiResolvedOrgScope out = AiFollowUpResolver.maybeNarrowGroupScopeToExplicitStoreMention(
                "汀兰餐厅采购总额是多少？", group, null);
        assertThat(out.getScopeType()).isEqualTo(AiResolvedOrgScope.SCOPE_STORE);
        assertThat(out.getVisibleStores()).hasSize(1);
        assertThat(out.getVisibleStores().get(0).getStoreDepartmentId()).isEqualTo(102L);
    }

    @Test
    void noStoreNamedInMessage_keepsGroupScope() {
        AiResolvedOrgScope group = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(1L)
                .visibleStores(List.of(
                        AiStoreScopeDTO.builder().storeDepartmentId(101L).storeName("AAA").build(),
                        AiStoreScopeDTO.builder().storeDepartmentId(102L).storeName("汀兰餐厅").build()))
                .build();
        AiResolvedOrgScope out = AiFollowUpResolver.maybeNarrowGroupScopeToExplicitStoreMention(
                "这个月采购总额是多少？", group, null);
        assertThat(out).isSameAs(group);
    }
}
