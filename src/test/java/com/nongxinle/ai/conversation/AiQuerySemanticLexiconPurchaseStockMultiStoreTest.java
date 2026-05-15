package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.harness.AiMultiStoreHarnessTrace;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 双排店名归一在 {@link AiQuerySemanticParseResult#effectiveMentionedStoreNames()}，Harness 据此观测多店并排。 */
class AiQuerySemanticLexiconPurchaseStockMultiStoreTest {

    private static AiResolvedOrgScope groupTwoStoresOrg() {
        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .visibleStores(
                        List.of(
                                AiStoreScopeDTO.builder().storeName("AAA").storeDepartmentId(1L).build(),
                                AiStoreScopeDTO.builder().storeName("汀兰餐厅").storeDepartmentId(2L).build()))
                .build();
    }

    private static AiQuerySemanticParseResult twoStoreMentions() {
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .confidence(1d)
                .requestedScope(
                        AiQuerySemanticParseResult.RequestedScopePart.builder()
                                .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                .build())
                .build();
    }

    @Test
    void harness_detects_parallel_mentions_when_group_has_two_visible_stores() {
        AiMultiStoreHarnessTrace trace = AiMultiStoreHarnessTrace.create();
        trace.ingestDetectionCandidate("", twoStoreMentions(), groupTwoStoresOrg());
        assertThat(trace.isDetected()).isTrue();
    }

    @Test
    void harness_does_not_flag_parallel_when_parse_missing_second_store() {
        AiMultiStoreHarnessTrace trace = AiMultiStoreHarnessTrace.create();
        AiQuerySemanticParseResult one =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(1d)
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of("AAA"))
                                        .build())
                        .build();
        trace.ingestDetectionCandidate("", one, groupTwoStoresOrg());
        assertThat(trace.isDetected()).isFalse();
    }

    @Test
    void resolveSingleStoreNarrowingBlocked_requires_group_and_two_llm_mentions() {
        AiMultiStoreHarnessTrace trace = AiMultiStoreHarnessTrace.create();
        AiResolvedOrgScope merged = groupTwoStoresOrg();
        assertThat(trace.resolveSingleStoreNarrowingBlocked("", merged, twoStoreMentions()))
                .isTrue();
        assertThat(trace.resolveSingleStoreNarrowingBlocked("", merged, null)).isFalse();
    }
}
