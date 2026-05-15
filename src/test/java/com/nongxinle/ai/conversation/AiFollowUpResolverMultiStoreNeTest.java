package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM {@code mentionedStoreNames} 经确定性映射后：多店应保持 GROUP 与并列 visibleStores。
 */
class AiFollowUpResolverMultiStoreNeTest {

    @Test
    void twoDistinctLlmMentionsKeepGroupAndBothVisibleStores() {
        AiResolvedOrgScope groupOrg =
                AiResolvedOrgScope.builder()
                        .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                        .distributerId(9L)
                        .visibleStores(
                                List.of(
                                        AiStoreScopeDTO.builder().storeDepartmentId(1L).storeName("AAA").build(),
                                        AiStoreScopeDTO.builder()
                                                .storeDepartmentId(3L)
                                                .storeName("汀兰餐厅")
                                                .build(),
                                        AiStoreScopeDTO.builder()
                                                .storeDepartmentId(9L)
                                                .storeName("其他店")
                                                .build()))
                        .build();

        List<AiStoreScopeDTO> picks =
                AiFollowUpResolver.resolvedStoresSubsetFromDistinctMentions(
                        List.of("AAA", "汀兰餐厅"), groupOrg.getVisibleStores());
        assertThat(picks).hasSize(2);
        AiResolvedOrgScope narrowed =
                AiFollowUpResolver.copyOrgNarrowedToStoreSubsetKeepingGroup(groupOrg, picks);
        assertThat(narrowed.getScopeType()).isEqualTo(AiResolvedOrgScope.SCOPE_GROUP);
        assertThat(narrowed.getVisibleStores()).hasSize(2);
        assertThat(narrowed.getVisibleStores().get(0).getStoreDepartmentId()).isEqualTo(1L);
        assertThat(narrowed.getVisibleStores().get(1).getStoreDepartmentId()).isEqualTo(3L);
    }
}
