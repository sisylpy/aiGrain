package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.harness.AiMultiStoreHarnessTrace;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiQuerySemanticLexiconMultiStoreDishMarginTest {

    private static AiResolvedOrgScope groupTwoStoresOrg() {
        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .visibleStores(
                        List.of(
                                AiStoreScopeDTO.builder().storeName("AAA").storeDepartmentId(1L).build(),
                                AiStoreScopeDTO.builder().storeName("汀兰餐厅").storeDepartmentId(2L).build()))
                .build();
    }

    @Test
    void two_explicit_store_mentions_triggers_parallel_detection() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(1d)
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .build();
        AiMultiStoreHarnessTrace trace = AiMultiStoreHarnessTrace.create();
        trace.ingestDetectionCandidate("", sem, groupTwoStoresOrg());
        assertTrue(trace.isDetected());
    }

    @Test
    void revenue_style_two_stores_same_parse_shape_still_dual_mention_signal() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(1d)
                        .intent("REVENUE")
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .build();
        AiMultiStoreHarnessTrace trace = AiMultiStoreHarnessTrace.create();
        trace.ingestDetectionCandidate("", sem, groupTwoStoresOrg());
        assertTrue(trace.isDetected());
    }

    @Test
    void ranking_only_one_store_mention_not_parallel_pair() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(1d)
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of())
                                        .build())
                        .build();
        AiMultiStoreHarnessTrace trace = AiMultiStoreHarnessTrace.create();
        trace.ingestDetectionCandidate("", sem, groupTwoStoresOrg());
        assertFalse(trace.isDetected());
    }
}
