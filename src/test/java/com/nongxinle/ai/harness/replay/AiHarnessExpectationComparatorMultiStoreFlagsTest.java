package com.nongxinle.ai.harness.replay;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHarnessExpectationComparatorMultiStoreFlagsTest {

    @Test
    void matchesMultiStoreMatchedStoresIgnoringOrder() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.getMultiStoreMatchedStoresExpected().add("AAA");
        exp.getMultiStoreMatchedStoresExpected().add("汀兰餐厅");

        LinkedHashMap<String, Object> ok = new LinkedHashMap<>();
        ok.put("multiStoreMatchedStores", List.of("汀兰餐厅", "AAA"));

        assertTrue(AiHarnessExpectationComparator.compare(ok, exp, false).isEmpty());
    }

    @Test
    void optionalBooleanMismatchFails() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.setSingleStoreNarrowingBlockedExpected(true);

        LinkedHashMap<String, Object> bad = new LinkedHashMap<>();
        bad.put("singleStoreNarrowingBlocked", false);

        assertFalse(AiHarnessExpectationComparator.compare(bad, exp, false).isEmpty());
    }

    @Test
    void mentionedDishNameMustBeAbsent() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.setMentionedDishNameMustBeAbsent(true);

        LinkedHashMap<String, Object> good = new LinkedHashMap<>();
        good.put("mentionedDishName", null);

        LinkedHashMap<String, Object> ugly = new LinkedHashMap<>();
        ugly.put("mentionedDishName", "和汀兰餐厅哪个");

        assertTrue(AiHarnessExpectationComparator.compare(good, exp, false).isEmpty());
        assertFalse(AiHarnessExpectationComparator.compare(ugly, exp, false).isEmpty());
    }

    @Test
    void querySemanticV2RawTimeActionMustNotBeOverrideWhenRoundExpectsInherit() {
        AiHarnessReplayExpectedRound exp = new AiHarnessReplayExpectedRound();
        exp.getQuerySemanticV2TimeActionNoneOf().add("OVERRIDE");
        exp.getQuerySemanticV2TimeTypeNoneOf().add("CURRENT_MONTH");

        LinkedHashMap<String, Object> timeBad = new LinkedHashMap<>();
        timeBad.put("timeType", "CURRENT_MONTH");
        LinkedHashMap<String, Object> v2Bad = new LinkedHashMap<>();
        v2Bad.put("time", timeBad);

        LinkedHashMap<String, Object> summaryBad = new LinkedHashMap<>();
        summaryBad.put("querySemanticV2TimeAction", "OVERRIDE");
        summaryBad.put("querySemanticV2", v2Bad);

        assertFalse(AiHarnessExpectationComparator.compare(summaryBad, exp, false).isEmpty());

        LinkedHashMap<String, Object> timeOk = new LinkedHashMap<>();
        timeOk.put("timeType", "LAST_MONTH");
        LinkedHashMap<String, Object> v2Ok = new LinkedHashMap<>();
        v2Ok.put("time", timeOk);

        LinkedHashMap<String, Object> summaryOk = new LinkedHashMap<>();
        summaryOk.put("querySemanticV2TimeAction", "INHERIT_PREVIOUS");
        summaryOk.put("querySemanticV2", v2Ok);

        assertTrue(AiHarnessExpectationComparator.compare(summaryOk, exp, false).isEmpty());
    }
}
