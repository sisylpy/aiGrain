package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** LLM 口述店名 → 候选 visible 门店的唯一映射与单店收窄。 */
class ExplicitStoreMentionNarrowingTest {

    @Test
    void llmMentionNarrowsGroupOrgToNamedStore() {
        AiResolvedOrgScope group = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(1L)
                .visibleStores(List.of(
                        AiStoreScopeDTO.builder().storeDepartmentId(101L).storeName("AAA").build(),
                        AiStoreScopeDTO.builder().storeDepartmentId(102L).storeName("汀兰餐厅").build()))
                .build();
        var hit = AiFollowUpResolver.uniquelyResolvedStoreFromLlmMention("汀兰餐厅", group.getVisibleStores());
        assertThat(hit).isPresent();
        AiResolvedOrgScope out = AiFollowUpResolver.copyOrgNarrowedToSingleStore(group, hit.get());
        assertThat(out.getScopeType()).isEqualTo(AiResolvedOrgScope.SCOPE_STORE);
        assertThat(out.getVisibleStores()).hasSize(1);
        assertThat(out.getVisibleStores().get(0).getStoreDepartmentId()).isEqualTo(102L);
    }

    @Test
    void noResolvableLlmMentionDoesNotMapToStore() {
        AiResolvedOrgScope group = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(1L)
                .visibleStores(List.of(
                        AiStoreScopeDTO.builder().storeDepartmentId(101L).storeName("AAA").build(),
                        AiStoreScopeDTO.builder().storeDepartmentId(102L).storeName("汀兰餐厅").build()))
                .build();
        assertThat(AiFollowUpResolver.uniquelyResolvedStoreFromLlmMention("虚构门店", group.getVisibleStores()))
                .isEmpty();
    }
}
