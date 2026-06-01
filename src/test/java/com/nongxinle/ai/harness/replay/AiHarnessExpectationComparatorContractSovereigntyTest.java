package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHarnessExpectationComparatorContractSovereigntyTest {

    @Test
    void forbiddenSubstring_ignoresAllowedWiresCatalog_butCatchesFinalWire() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.getForbiddenSubstringsInSummaryJson().add("purchase_overview_summary");

        LinkedHashMap<String, Object> validation = new LinkedHashMap<>();
        validation.put("allowedWires", List.of("purchase_overview_summary", "purchase_period_goods_list"));

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("selectedContractId", "purchase.period_goods_list");
        summary.put("structuredIntentDetailWire", "purchase_period_goods_list");
        summary.put("harnessReplayPurchaseAnswerPlanType", PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL);
        summary.put("semanticContractValidation", validation);

        assertEquals(0, AiHarnessExpectationComparator.compare(summary, exp, false).size());

        summary.put("structuredIntentDetailWire", "purchase_overview_summary");
        assertFalse(AiHarnessExpectationComparator.compare(summary, exp, false).isEmpty());
    }

    @Test
    void matchedCapabilityIdExpected_readsSelectedContractIdWhenLegacyFieldAbsent() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.setMatchedCapabilityIdExpected("purchase.period_goods_list");

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("selectedContractId", "purchase.period_goods_list");
        summary.put("executionIntentType", PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST);

        assertEquals(0, AiHarnessExpectationComparator.compare(summary, exp, false).size());
    }

    @Test
    void periodGoodsListCase_passesWithoutExecutionDetailWanted() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.setSelectedContractIdExpected("purchase.period_goods_list");
        exp.setHarnessReplayPurchaseAnswerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL);
        exp.setExecutionIntentTypeExpected(PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST);
        exp.setScopeType("STORE");
        exp.getQueryStoreIdsMustContain().add(3);

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("selectedContractId", "purchase.period_goods_list");
        summary.put("structuredIntentDetailWire", "purchase_period_goods_list");
        summary.put("harnessReplayPurchaseAnswerPlanType", PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL);
        summary.put("executionIntentType", PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST);
        summary.put("executionDetailWanted", null);
        summary.put("scopeType", "STORE");
        summary.put("queryStoreIds", new ArrayList<>(List.of(3)));

        assertTrue(AiHarnessExpectationComparator.compare(summary, exp, false).isEmpty());
    }
}
