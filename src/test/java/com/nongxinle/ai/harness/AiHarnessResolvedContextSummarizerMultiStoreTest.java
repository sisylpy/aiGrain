package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiHarnessResolvedContextSummarizerMultiStoreTest {

    @Test
    void summarizesMultiStoreHarnessAndQuerySemantic_mentionedStoreNames() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("REVENUE_OVERVIEW")
                .confidence(1.0)
                .requestedScope(AiQuerySemanticParseResult.RequestedScopePart.builder()
                        .requestedScopeType("GROUP")
                        .mentionedStoreNames(List.of(" AAA ", "汀兰餐厅"))
                        .build())
                .parseMissing(false)
                .build();

        AiResolvedQueryContext ctx = AiResolvedQueryContext.builder()
                .effectiveIntentCode("REVENUE_OVERVIEW")
                .harnessMultiStoreScopeDetected(true)
                .harnessMultiStoreScopeApplied(true)
                .harnessMultiStoreMatchedStores(List.of("AAA", "汀兰餐厅"))
                .harnessSingleStoreNarrowingBlocked(true)
                .querySemanticParse(sem)
                .build();

        Map<String, Object> summary = AiHarnessResolvedContextSummarizer.summarize(ctx, null);
        assertEquals(true, summary.get("multiStoreScopeDetected"));
        assertEquals(true, summary.get("multiStoreScopeApplied"));
        assertEquals(true, summary.get("singleStoreNarrowingBlocked"));
        @SuppressWarnings("unchecked")
        List<String> matched = (List<String>) summary.get("multiStoreMatchedStores");
        assertNotNull(matched);
        assertEquals(List.of("AAA", "汀兰餐厅"), matched);

        @SuppressWarnings("unchecked")
        Map<String, Object> qsp = (Map<String, Object>) summary.get("querySemanticLlm");
        assertNotNull(qsp);
        @SuppressWarnings("unchecked")
        List<String> mentions = (List<String>) qsp.get("mentionedStoreNames");
        assertEquals(List.of("AAA", "汀兰餐厅"), mentions);
    }
}
