package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiHarnessExpectationComparatorMultiScopeTest {

    @Test
    void revenueAnswerPlan_probePassesWhenReplaySummaryHasPathAndRankingWireOnly() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.setRevenueAnswerPlanPlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("revenueAnswerPlanType", null);
        summary.put("effectivePathCode", AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        summary.put("structuredIntentDetailWire", AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);

        assertEquals(0, AiHarnessExpectationComparator.compare(summary, exp, false).size());
    }

    @Test
    void queryStoreIdsComparedWhenStrict() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.getQueryStoreIds().addAll(List.of(1, 3));

        LinkedHashMap<String, Object> summaryOk = new LinkedHashMap<>();
        summaryOk.put("queryStoreIds", List.of(3, 1));

        LinkedHashMap<String, Object> summaryBad = new LinkedHashMap<>();
        summaryBad.put("queryStoreIds", List.of(1));

        assertEquals(0, AiHarnessExpectationComparator.compare(summaryOk, exp, true).size());
        assertFalse(AiHarnessExpectationComparator.compare(summaryBad, exp, true).isEmpty());
    }

    @Test
    void querySemanticEffectiveMentionedStoreNamesOrderInsensitiveSetMatch() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.getQuerySemanticEffectiveMentionedStoreNames().add("AAA");
        exp.getQuerySemanticEffectiveMentionedStoreNames().add("汀兰餐厅");

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("querySemanticEffectiveMentionedStoreNames", List.of("汀兰餐厅", " AAA "));

        assertEquals(0, AiHarnessExpectationComparator.compare(summary, exp, false).size());
    }
}
