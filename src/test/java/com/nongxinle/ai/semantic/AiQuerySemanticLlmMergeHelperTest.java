package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuerySemanticLlmMergeHelperTest {

    private static AiQuerySemanticParseResult.SemanticSlotsPart slots(
            String structuredIntentDetailWire,
            String queryObject,
            String operation,
            String metric) {
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .structuredIntentDetailWire(structuredIntentDetailWire)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .sourceFacet("ALL")
                .anchorPolicy("IGNORE_PREVIOUS_ANCHOR")
                .build();
    }

    /** D-1X-D1：merge 测试须同时写 slots 与 {@code currentTurnStructuredIntentDetailWire}。 */
    private static AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder withV2Slots(
            AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder b,
            String wire,
            String queryObject,
            String operation,
            String metric) {
        return b.semanticSlots(slots(wire, queryObject, operation, metric))
                .currentTurnStructuredIntentDetailWire(wire);
    }

    @Test
    void mergeOverviewAcceptanceShapes() {
        AiQuerySemanticParseResult sem =
                withV2Slots(
                                AiQuerySemanticParseResult.builder()
                                        .intent("BUSINESS_OVERVIEW")
                                        .confidence(0.9)
                                        .time(
                                                AiQuerySemanticParseResult.TimePart.builder()
                                                        .timeType("CURRENT_MONTH")
                                                        .startDate("2026-05-01")
                                                        .endDate("2026-05-13")
                                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                                        .needInheritFromPrevious(false)
                                                        .build())
                                        .requestedScope(
                                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                                        .requestedScopeType("GROUP")
                                                        .build())
                                        .metric(
                                                AiQuerySemanticParseResult.MetricPart.builder()
                                                        .primaryMetric("BUSINESS_STATUS")
                                                        .build())
                                        .parseMissing(false),
                                "business_overview_status",
                                "STORE",
                                "SUMMARY",
                                "BUSINESS_STATUS")
                        .build();

        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55);

        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_OVERVIEW_STATUS);

        LocalDate anchor = LocalDate.of(2026, 5, 13);

        AiResolvedTimeWindow tentative = null;
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        tentative,
                        sem,
                        anchor,
                        0.55,
                        "本月生意怎么样",
                        merged);
        assertThat(tw.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.THIS_MONTH);
        assertThat(tw.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(tw.getEndDate()).isEqualTo(anchor);
    }

    @Test
    void mergeTentativeTime_appliesLlmTodayFromV2TimeOnly_withoutUtteranceLexicon() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("REVENUE_OVERVIEW")
                .confidence(0.9)
                .timeAction("NEW")
                .time(AiQuerySemanticParseResult.TimePart.builder()
                        .timeType("TODAY")
                        .startDate("2026-05-13")
                        .endDate("2026-05-13")
                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                        .needInheritFromPrevious(false)
                        .build())
                .parseMissing(false)
                .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "哪个门店营业额最高");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw = AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                null, sem, anchor, 0.55, "哪个门店营业额最高", merged);
        assertThat(tw).isNotNull();
        assertThat(tw.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.TODAY);
        assertThat(tw.getStartDate()).isEqualTo(anchor);
    }

    @Test
    void mergeTentativeTime_acceptsLlmTodayWhenUserSaysToday() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("REVENUE_OVERVIEW")
                .confidence(0.9)
                .time(AiQuerySemanticParseResult.TimePart.builder()
                        .timeType("TODAY")
                        .startDate("2026-05-13")
                        .endDate("2026-05-13")
                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                        .needInheritFromPrevious(false)
                        .build())
                .parseMissing(false)
                .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "今天哪个店营业额高");
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw = AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                null, sem, anchor, 0.55, "今天哪个店营业额高", merged);
        assertThat(tw).isNotNull();
        assertThat(tw.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.TODAY);
        assertThat(tw.getStartDate()).isEqualTo(anchor);
        assertThat(tw.getEndDate()).isEqualTo(anchor);
    }

    @Test
    void mergeTentativeTime_lastYearOverride_respectsUserSayingLastYear() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .followUp(true)
                        .timeAction("OVERRIDE")
                        .confidence(0.92)
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("LAST_YEAR")
                                        .startDate("2025-01-01")
                                        .endDate("2025-12-31")
                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                        .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "去年呢", merged, null);
        assertThat(tw).isNotNull();
        assertThat(tw.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.LAST_YEAR);
        assertThat(tw.getStartDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(tw.getEndDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void mergeTentativeTime_lastYearSamePeriod_usesLlmProvidedDates() {
        AiConversationTurnMemory prev = AiConversationTurnMemory.builder()
                .lastStartDate("2026-05-01")
                .lastEndDate("2026-05-13")
                .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .followUp(true)
                        .intentAction("INHERIT_PREVIOUS")
                        .timeAction("OVERRIDE")
                        .confidence(0.92)
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("LAST_YEAR_SAME_PERIOD")
                                        .startDate("2025-05-01")
                                        .endDate("2025-05-13")
                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                        .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "去年呢", merged, prev);
        assertThat(tw).isNotNull();
        assertThat(tw.getTimeLabel()).isEqualTo("LAST_YEAR_SAME_PERIOD");
        assertThat(tw.getStartDate()).isEqualTo(LocalDate.of(2025, 5, 1));
        assertThat(tw.getEndDate()).isEqualTo(LocalDate.of(2025, 5, 13));
        assertThat(tw.isExplicitTimeMentioned()).isTrue();
    }

    @Test
    void mergeIntent_mapsStockReducePath_fromSemanticSlotsWire() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("STOCK_REDUCE_QUERY")
                        .confidence(0.9)
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .rankingType("goods_outbound_ranking")
                                        .build())
                        .semanticSlots(
                                slots(
                                        "goods_outbound_ranking",
                                        "GOODS",
                                        "RANKING",
                                        "OUTBOUND_AMOUNT"))
                        .currentTurnStructuredIntentDetailWire("goods_outbound_ranking")
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING);
    }

    @Test
    void mergeIntent_purchaseMultiStore_fromSemanticSlotsWire() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("PURCHASE_OVERVIEW")
                        .confidence(0.9)
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .requestedScopeType("GROUP")
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .rankingType("supplier_amount_ranking")
                                        .build())
                        .semanticSlots(
                                slots(
                                        "purchase_store_amount_ranking",
                                        "STORE",
                                        "COMPARE",
                                        "PURCHASE_AMOUNT"))
                        .currentTurnStructuredIntentDetailWire("purchase_store_amount_ranking")
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING);
    }

    @Test
    void mergeIntent_stockReduceFromLlmIntentAndSlots_notLegacyKeywordBaseline() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("STOCK_REDUCE_QUERY")
                        .confidence(0.9)
                        .semanticSlots(
                                slots(
                                        "store_outbound_amount_ranking",
                                        "STORE",
                                        "COMPARE",
                                        "OUTBOUND_AMOUNT"))
                        .parseMissing(false)
                        .build();
        String q = "AAA和汀兰餐厅哪个出库金额高";
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(
                        AiResolvedQueryIntent.builder().build(), sem, 0.55, q);
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
    }

    @Test
    void mergeIntent_purchaseFromLlmIntentAndSlots_notLegacyKeywordBaseline() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("PURCHASE_OVERVIEW")
                        .confidence(0.9)
                        .semanticSlots(
                                slots(
                                        "purchase_store_amount_ranking",
                                        "STORE",
                                        "COMPARE",
                                        "PURCHASE_AMOUNT"))
                        .parseMissing(false)
                        .build();
        String q = "AAA和汀兰餐厅哪个采购金额高";
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(
                        AiResolvedQueryIntent.builder().build(), sem, 0.55, q);
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
    }

    @Test
    void mergeIntent_rankingTypeOnly_doesNotWriteStructuredWireWithoutSlots() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("STOCK_REDUCE_QUERY")
                        .confidence(0.9)
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .rankingType("goods_outbound_ranking")
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        assertThat(merged.getStructuredIntentDetail()).isNull();
    }

    @Test
    void mergeIntent_dishProfitFromSemanticSlotsWire_notRankingTypeCompat() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("DISH_PROFIT")
                        .confidence(0.9)
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .rankingType("dish_actual_cost_ranking")
                                        .build())
                        .semanticSlots(
                                slots(
                                        "dish_actual_cost_ranking_high",
                                        "DISH",
                                        "RANKING",
                                        "PROFIT_MARGIN"))
                        .currentTurnStructuredIntentDetailWire("dish_actual_cost_ranking_high")
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.DISH_PROFIT);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH);
    }

    @Test
    void mergeIntent_businessDiagnosisFromSemanticSlotsWire() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("BUSINESS_DIAGNOSIS")
                        .confidence(0.9)
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("revenue")
                                        .build())
                        .semanticSlots(
                                slots(
                                        "business_cost_pressure_diagnosis",
                                        "STORE",
                                        "DIAGNOSIS",
                                        "BUSINESS_STATUS"))
                        .currentTurnStructuredIntentDetailWire("business_cost_pressure_diagnosis")
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS);
    }

    @Test
    void mergeIntent_businessDiagnosisCompare_fromSemanticSlotsWire() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("BUSINESS_DIAGNOSIS")
                        .confidence(0.9)
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .requestedScopeType("GROUP")
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("business_status_compare_diagnosis")
                                        .build())
                        .semanticSlots(
                                slots(
                                        "business_store_status_compare_diagnosis",
                                        "STORE",
                                        "COMPARE",
                                        "BUSINESS_STATUS"))
                        .currentTurnStructuredIntentDetailWire("business_store_status_compare_diagnosis")
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);
    }

    @Test
    void mergeIntent_businessDiagnosisMultiStore_fromSemanticSlotsWire() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("BUSINESS_DIAGNOSIS")
                        .confidence(0.9)
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .semanticSlots(
                                slots(
                                        "business_store_status_compare_diagnosis",
                                        "STORE",
                                        "COMPARE",
                                        "BUSINESS_STATUS"))
                        .currentTurnStructuredIntentDetailWire("business_store_status_compare_diagnosis")
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);
    }

    @Test
    void mergeTentativeTime_samePathDishMentionFollowUp_defersWhenTimeInheritsPrevious() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .followUp(true)
                        .mentionedDishName("核桃芽菜西芹")
                        .timeAction("INHERIT_PREVIOUS")
                        .confidence(0.9)
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("CURRENT_MONTH")
                                        .needInheritFromPrevious(true)
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "", merged, prev);
        assertThat(tw).isNull();
    }

    @Test
    void mergeTentativeTime_dualStoreMetricScopeOverride_afterDishProfit_timeInherit_deferredForFinalize() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .lastTimeLabel(AiResolvedTimeWindow.LAST_MONTH)
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .intent("REVENUE_OVERVIEW")
                        .intentAction("OVERRIDE")
                        .timeAction("INHERIT_PREVIOUS")
                        .scopeAction("OVERRIDE")
                        .metricAction("OVERRIDE")
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .metric(AiQuerySemanticParseResult.MetricPart.builder().primaryMetric("revenue").build())
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                        .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "", merged, prev);
        assertThat(tw).isNull();
    }

    @Test
    void mergeTentativeTime_afterDishProfit_appliesV2OverrideMonthFromStructuredTime() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .lastTimeLabel(AiResolvedTimeWindow.LAST_MONTH)
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .intent("REVENUE_OVERVIEW")
                        .intentAction("OVERRIDE")
                        .timeAction("OVERRIDE")
                        .scopeAction("OVERRIDE")
                        .metricAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .startDate("2026-05-01")
                                        .endDate("2026-05-13")
                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .metric(AiQuerySemanticParseResult.MetricPart.builder().primaryMetric("revenue").build())
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                        .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "", merged, prev);
        assertThat(tw).isNotNull();
        assertThat(tw.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.THIS_MONTH);
        assertThat(tw.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(tw.getEndDate()).isEqualTo(anchor);
    }

    @Test
    void mergeTentativeTime_samePathScopeOverride_thisMonthExplicitPhrase_mirrorsLlmDates() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .intentAction("OVERRIDE")
                        .timeAction("OVERRIDE")
                        .scopeAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .startDate("2026-05-01")
                                        .endDate("2026-05-14")
                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                        .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 14);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "AAA 这个月营业额多少？", merged, prev);
        assertThat(tw).isNotNull();
        assertThat(tw.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(tw.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 14));
        assertThat(tw.isExplicitTimeMentioned()).isTrue();
    }

    @Test
    void mergeTentativeTime_followUpPurchase_inheritTime_returnsNullWithoutLlmDates() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                        .lastStartDate("2026-05-01")
                        .lastEndDate("2026-05-13")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .intent("PURCHASE_OVERVIEW")
                        .intentAction("OVERRIDE")
                        .timeAction("INHERIT_PREVIOUS")
                        .scopeAction("INHERIT_PREVIOUS")
                        .metricAction("INHERIT_PREVIOUS")
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                        .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "那采购呢", merged, prev);
        assertThat(tw).isNull();
    }

    @Test
    void mergeIntent_namedDishFollowUp_usesSemanticSlotsWireOverPreviousRanking() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .lastIntentCode(AiResolvedQueryIntent.DISH_PROFIT)
                        .lastStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN)
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .followUp(true)
                        .intentAction("OVERRIDE")
                        .timeAction("INHERIT_PREVIOUS")
                        .scopeAction("INHERIT_PREVIOUS")
                        .metricAction("OVERRIDE")
                        .intent("DISH_PROFIT")
                        .mentionedDishName("核桃芽菜西芹")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("profit_margin")
                                        .rankingType(null)
                                        .build())
                        .semanticSlots(
                                slots(
                                        "dish_gross_margin_query",
                                        "DISH",
                                        "DETAIL",
                                        "PROFIT_MARGIN"))
                        .currentTurnStructuredIntentDetailWire("dish_gross_margin_query")
                        .build();
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "", prev);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
    }

    @Test
    void mergeTentativeTime_samePathDishMentionFollowUp_stillAppliesTimeWhenOverridden() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .followUp(true)
                        .mentionedDishName("核桃芽菜西芹")
                        .timeAction("OVERRIDE")
                        .confidence(0.9)
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("CURRENT_MONTH")
                                        .startDate("2026-05-01")
                                        .endDate("2026-05-13")
                                        .timeSource("CURRENT_MESSAGE")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "", merged, prev);
        assertThat(tw).isNotNull();
        assertThat(tw.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.THIS_MONTH);
    }

    @Test
    void mergeTentativeTime_inheritWithMatchingPreviousDates_mirrorsLlmTimeSource() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_COST_DIAGNOSIS)
                        .lastStartDate("2026-05-01")
                        .lastEndDate("2026-05-20")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .intent("WAREHOUSE_STOCK_OVERVIEW")
                        .intentAction("OVERRIDE")
                        .timeAction("INHERIT_PREVIOUS")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .startDate("2026-05-01")
                                        .endDate("2026-05-20")
                                        .timeSource("INHERITED_PREVIOUS")
                                        .needInheritFromPrevious(true)
                                        .build())
                        .build();
        AiResolvedQueryIntent merged =
                AiResolvedQueryIntent.builder()
                        .pathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                        .intentCode(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW)
                        .build();
        LocalDate anchor = LocalDate.of(2026, 5, 20);
        AiResolvedTimeWindow tw =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        null, sem, anchor, 0.55, "那库房呢", merged, prev);
        assertThat(tw).isNotNull();
        assertThat(tw.isInheritedFromPreviousTurn()).isTrue();
        assertThat(tw.isExplicitTimeMentioned()).isFalse();
        assertThat(tw.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(tw.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 20));
    }

    @Test
    void semanticTimeContractCheck_passesExplicitThisMonth() {
        AiQuerySemanticParseResult sem =
                revenueSemWithTime(
                        "THIS_MONTH",
                        "2026-05-01",
                        "2026-05-20",
                        "CURRENT_MESSAGE_EXPLICIT",
                        false);
        SemanticTimeContractCheck.Result r =
                SemanticTimeContractCheck.check(sem, null, LocalDate.of(2026, 5, 20));
        assertThat(r.valid()).isTrue();
        assertThat(r.normalizedTimeSource())
                .isEqualTo(SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT);
    }

    @Test
    void semanticTimeContractCheck_passesInheritedPrevious() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-05-20")
                        .build();
        AiQuerySemanticParseResult sem =
                revenueSemWithTime(
                        "CUSTOM",
                        "2026-04-01",
                        "2026-05-20",
                        "INHERITED_PREVIOUS",
                        true);
        SemanticTimeContractCheck.Result r =
                SemanticTimeContractCheck.check(sem, prev, LocalDate.of(2026, 5, 20));
        assertThat(r.valid()).isTrue();
        assertThat(r.normalizedTimeSource())
                .isEqualTo(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS);
    }

    @Test
    void semanticTimeContractCheck_failsWhenThisQuarterDatesMismatch() {
        AiQuerySemanticParseResult sem =
                revenueSemWithTime(
                        "THIS_QUARTER",
                        "2026-05-01",
                        "2026-05-20",
                        "CURRENT_MESSAGE_EXPLICIT",
                        false);
        SemanticTimeContractCheck.Result r =
                SemanticTimeContractCheck.check(sem, null, LocalDate.of(2026, 5, 20));
        assertThat(r.valid()).isFalse();
        assertThat(r.failureReason())
                .isEqualTo(SemanticTimeContractCheck.FAIL_TIME_TYPE_DATE_MISMATCH);
        assertThat(r.clarificationQuestion()).contains("不一致");
    }

    @Test
    void semanticTimeContractCheck_failsInheritWithoutPrevious() {
        AiQuerySemanticParseResult sem =
                revenueSemWithTime(
                        "THIS_MONTH",
                        "2026-05-01",
                        "2026-05-20",
                        "INHERITED_PREVIOUS",
                        true);
        SemanticTimeContractCheck.Result r =
                SemanticTimeContractCheck.check(sem, null, LocalDate.of(2026, 5, 20));
        assertThat(r.valid()).isFalse();
        assertThat(r.failureReason())
                .isEqualTo(SemanticTimeContractCheck.FAIL_INHERIT_WITHOUT_PREVIOUS);
    }

    @Test
    void semanticTimeContractCheck_failsWhenThisMonthEndDateAfterToday() {
        AiQuerySemanticParseResult sem =
                revenueSemWithTime(
                        "THIS_MONTH",
                        "2026-05-01",
                        "2026-05-21",
                        "CURRENT_MESSAGE_EXPLICIT",
                        false);
        SemanticTimeContractCheck.Result r =
                SemanticTimeContractCheck.check(sem, null, LocalDate.of(2026, 5, 20));
        assertThat(r.valid()).isFalse();
        assertThat(r.failureReason())
                .isEqualTo(SemanticTimeContractCheck.FAIL_TIME_TYPE_DATE_MISMATCH);
    }

    @Test
    void reconcileTimePartForContract_contextFollowUpDefaultMonthToDate_inheritsPreviousTurn() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-05-01")
                        .lastEndDate("2026-05-31")
                        .lastTimeLabel("LAST_MONTH")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .followUp(true)
                        .timeAction("INHERIT_PREVIOUS")
                        .intentAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .startDate("2026-06-01")
                                        .endDate("2026-06-01")
                                        .timeSource("DEFAULT_MONTH_TO_DATE")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .build();
        LocalDate anchor = LocalDate.of(2026, 6, 1);
        AiQuerySemanticParseResult reconciled =
                SemanticTimeContractCheck.reconcileTimePartForContract(sem, prev, anchor);
        SemanticTimeContractCheck.Result contract =
                SemanticTimeContractCheck.check(reconciled, prev, anchor);
        assertThat(contract.valid()).isTrue();
        assertThat(contract.normalizedTimeSource())
                .isEqualTo(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS);
        assertThat(contract.normalizedStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(contract.normalizedEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(reconciled.getTime().getTimeType()).isEqualTo(AiResolvedTimeWindow.LAST_MONTH);
    }

    @Test
    void reconcileTimePartForContract_intakeContextSignalsWithoutV2FollowUp_inheritsPreviousTurn() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-05-01")
                        .lastEndDate("2026-05-31")
                        .lastTimeLabel("LAST_MONTH")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .followUp(false)
                        .timeAction("OVERRIDE")
                        .intentAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .startDate("2026-06-01")
                                        .endDate("2026-06-01")
                                        .timeSource("DEFAULT_MONTH_TO_DATE")
                                        .build())
                        .build();
        TimeLayerContextSignals signals = new TimeLayerContextSignals(true, true, false);
        LocalDate anchor = LocalDate.of(2026, 6, 1);
        AiQuerySemanticParseResult reconciled =
                SemanticTimeContractCheck.reconcileTimePartForContract(sem, prev, anchor, signals);
        SemanticTimeContractCheck.Result contract =
                SemanticTimeContractCheck.check(reconciled, prev, anchor);
        assertThat(contract.valid()).isTrue();
        assertThat(contract.normalizedTimeSource())
                .isEqualTo(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS);
        assertThat(contract.normalizedStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(contract.normalizedEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void reconcileTimePartForContract_firstTurnDefaultMonthToDate_unchanged() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .followUp(false)
                        .timeAction("NEW")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .startDate("2026-06-01")
                                        .endDate("2026-06-01")
                                        .timeSource("DEFAULT_MONTH_TO_DATE")
                                        .build())
                        .build();
        LocalDate anchor = LocalDate.of(2026, 6, 1);
        AiQuerySemanticParseResult reconciled =
                SemanticTimeContractCheck.reconcileTimePartForContract(sem, null, anchor);
        assertThat(reconciled).isSameAs(sem);
        SemanticTimeContractCheck.Result contract =
                SemanticTimeContractCheck.check(reconciled, null, anchor);
        assertThat(contract.valid()).isTrue();
        assertThat(contract.normalizedTimeSource())
                .isEqualTo(SemanticTimeContractCheck.SOURCE_DEFAULT_MONTH_TO_DATE);
    }

    @Test
    void reconcileTimePartForContract_explicitCurrentMessage_notOverriddenByPrevious() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-05-01")
                        .lastEndDate("2026-05-31")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .followUp(true)
                        .timeAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .startDate("2026-06-01")
                                        .endDate("2026-06-01")
                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                        .build())
                        .build();
        LocalDate anchor = LocalDate.of(2026, 6, 1);
        AiQuerySemanticParseResult reconciled =
                SemanticTimeContractCheck.reconcileTimePartForContract(sem, prev, anchor);
        assertThat(reconciled).isSameAs(sem);
    }

    /**
     * V2 销售分析类问句的时间合同：锚定相对型 + {@link AiResolvedTimeWindow#CUSTOM} 自由区间，
     * 须通过 {@link SemanticTimeContractCheck#check}（{@code today=2026-06-02}）。
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("salesAnalysisTimeContractCases")
    void semanticTimeContractCheck_salesAnalysisTimeOutputs(
            String scenario, String timeType, String startDate, String endDate) {
        AiQuerySemanticParseResult sem =
                revenueSemWithTime(
                        timeType,
                        startDate,
                        endDate,
                        SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT,
                        false);
        SemanticTimeContractCheck.Result r =
                SemanticTimeContractCheck.check(sem, null, LocalDate.of(2026, 6, 2));
        assertThat(r.valid())
                .as("scenario=%s timeType=%s %s~%s", scenario, timeType, startDate, endDate)
                .isTrue();
        assertThat(r.normalizedTimeSource())
                .isEqualTo(SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT);
        assertThat(r.normalizedStartDate()).isEqualTo(LocalDate.parse(startDate));
        assertThat(r.normalizedEndDate()).isEqualTo(LocalDate.parse(endDate));
    }

    static Stream<Arguments> salesAnalysisTimeContractCases() {
        return Stream.of(
                Arguments.of("这个月销售分析", "THIS_MONTH", "2026-06-01", "2026-06-02"),
                Arguments.of("上个月销售分析", "LAST_MONTH", "2026-05-01", "2026-05-31"),
                Arguments.of("5月销售分析", "CUSTOM", "2026-05-01", "2026-05-31"),
                Arguments.of("4月销售分析", "CUSTOM", "2026-04-01", "2026-04-30"),
                Arguments.of("上个月最后一周销售分析", "CUSTOM", "2026-05-25", "2026-05-31"),
                Arguments.of("近7天销售分析", "ROLLING_7", "2026-05-27", "2026-06-02"));
    }

    private static AiQuerySemanticParseResult revenueSemWithTime(
            String timeType,
            String start,
            String end,
            String timeSource,
            boolean needInherit) {
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .confidence(0.9)
                .intent("REVENUE_OVERVIEW")
                .timeAction(needInherit ? "INHERIT_PREVIOUS" : "NEW")
                .time(
                        AiQuerySemanticParseResult.TimePart.builder()
                                .timeType(timeType)
                                .startDate(start)
                                .endDate(end)
                                .timeSource(timeSource)
                                .needInheritFromPrevious(needInherit)
                                .build())
                .build();
    }
}
