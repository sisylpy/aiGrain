package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessOverviewSubPlanAttachSupportTest {

    @Test
    void isFourDomainSubPlanAttach_trueForBusinessOverviewSummaryWire() {
        AiResolvedQueryContext rq = businessOverviewContext(
                AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY);
        AiRunState state = AiRunState.builder()
                .businessOverviewPath(true)
                .resolvedQueryContext(rq)
                .build();
        assertTrue(BusinessOverviewSubPlanAttachSupport.isFourDomainSubPlanAttach(state, rq));
    }

    @Test
    void isFourDomainSubPlanAttach_falseForRevenueOverviewPath() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();
        AiRunState state = AiRunState.builder()
                .revenueOverviewPath(true)
                .resolvedQueryContext(rq)
                .build();
        assertFalse(BusinessOverviewSubPlanAttachSupport.isFourDomainSubPlanAttach(state, rq));
    }

    static AiResolvedQueryContext businessOverviewContext(String structuredWire) {
        LinkedHashMap<String, Object> trace = new LinkedHashMap<>();
        trace.put(SemanticContractCompletionEngine.TRACE_CONTRACT_ENTRY_VALIDATED, true);
        AiQuerySemanticParseResult.SemanticSlotsPart slots =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .selectedContractId("business_overview.summary")
                        .structuredIntentDetailWire(structuredWire)
                        .build();
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .semanticSlots(slots)
                .contractCompletionTrace(trace)
                .build();
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW)
                .intentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                .structuredIntentDetail(structuredWire)
                .build();
        return AiResolvedQueryContext.builder()
                .queryIntent(qi)
                .querySemanticParse(sem)
                .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW)
                .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW)
                .build();
    }
}
