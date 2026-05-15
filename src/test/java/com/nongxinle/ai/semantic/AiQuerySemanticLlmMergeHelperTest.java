package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuerySemanticLlmMergeHelperTest {

    @Test
    void mergeOverviewAcceptanceShapes() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("BUSINESS_OVERVIEW")
                .confidence(0.9)
                .time(AiQuerySemanticParseResult.TimePart.builder()
                        .timeType("CURRENT_MONTH")
                        .needInheritFromPrevious(false)
                        .build())
                .requestedScope(AiQuerySemanticParseResult.RequestedScopePart.builder()
                        .requestedScopeType("GROUP")
                        .build())
                .metric(AiQuerySemanticParseResult.MetricPart.builder()
                        .primaryMetric("BUSINESS_STATUS")
                        .build())
                .parseMissing(false)
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
    void mergeTentativeTime_rejectsLlmTodayWithoutUserSayingToday() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("REVENUE_OVERVIEW")
                .confidence(0.9)
                .time(AiQuerySemanticParseResult.TimePart.builder()
                        .timeType("TODAY")
                        .needInheritFromPrevious(false)
                        .build())
                .parseMissing(false)
                .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "哪个门店营业额最高");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        LocalDate anchor = LocalDate.of(2026, 5, 13);
        AiResolvedTimeWindow tw = AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                null, sem, anchor, 0.55, "哪个门店营业额最高", merged);
        assertThat(tw).isNull();
    }

    @Test
    void mergeTentativeTime_acceptsLlmTodayWhenUserSaysToday() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("REVENUE_OVERVIEW")
                .confidence(0.9)
                .time(AiQuerySemanticParseResult.TimePart.builder()
                        .timeType("TODAY")
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
    void mergeTentativeTime_lastYearSamePeriod_shiftsPreviousTurnWindow() {
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
        assertThat(tw.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.LAST_YEAR_SAME_PERIOD);
        assertThat(tw.getStartDate()).isEqualTo(LocalDate.of(2025, 5, 1));
        assertThat(tw.getEndDate()).isEqualTo(LocalDate.of(2025, 5, 13));
        assertThat(tw.isExplicitTimeMentioned()).isTrue();
    }

    @Test
    void mergeIntent_remapsWarehousePath_whenMetricHasOutboundRankingWire() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("WAREHOUSE_STOCK_OVERVIEW")
                        .confidence(0.9)
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .rankingType("goods_outbound_ranking")
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING);
    }

    @Test
    void mergeIntent_purchaseMultiStore_overridesSupplierRankingWire() {
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
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING);
    }

    @Test
    void mergeIntent_prefersStockReduceWhenLlmMapsRevenue_forParallelOutboundAmountQuestion() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("REVENUE_OVERVIEW")
                .confidence(0.9)
                .parseMissing(false)
                .build();
        String q = "AAA和汀兰餐厅哪个出库金额高";
        AiResolvedQueryIntent keyword = AiResolvedQueryIntent.fromUserMessage(q);
        assertThat(keyword.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);

        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(keyword, sem, 0.55, q);
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
    }

    @Test
    void mergeIntent_prefersPurchaseWhenLlmMapsRevenue_forParallelPurchaseAmountQuestion() {
        AiQuerySemanticParseResult sem = AiQuerySemanticParseResult.builder()
                .intent("REVENUE_OVERVIEW")
                .confidence(0.9)
                .parseMissing(false)
                .build();
        String q = "AAA和汀兰餐厅哪个采购金额高";
        AiResolvedQueryIntent keyword = AiResolvedQueryIntent.fromUserMessage(q);
        assertThat(keyword.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);

        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(keyword, sem, 0.55, q);
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
    }

    @Test
    void mergeIntent_remapsCostDiagnosisToDishProfit_whenMetricHasDishActualCostRankingWire() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("COST_DIAGNOSIS")
                        .confidence(0.9)
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .rankingType("dish_actual_cost_ranking")
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.DISH_PROFIT);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH);
    }

    @Test
    void mergeIntent_residualCostDiagnosisPath_mapsToEvidenceBusinessDiagnosis() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("COST_DIAGNOSIS")
                        .confidence(0.9)
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("revenue")
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS);
    }

    @Test
    void mergeIntent_overviewCompareWithCompareDiagnosisMetric_primary_elevatesToBusinessDiagnosis() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("BUSINESS_OVERVIEW")
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
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getPathCode()).isEqualTo(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        assertThat(merged.getIntentCode()).isEqualTo(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);
    }

    @Test
    void mergeIntent_businessDiagnosisMultiStore_fillsStructuredCompare_whenWireBlankOrSummary() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .intent("BUSINESS_DIAGNOSIS")
                        .confidence(0.9)
                        .requestedScope(
                                AiQuerySemanticParseResult.RequestedScopePart.builder()
                                        .mentionedStoreNames(List.of("AAA", "汀兰餐厅"))
                                        .build())
                        .parseMissing(false)
                        .build();
        AiResolvedQueryIntent merged = AiQuerySemanticLlmMergeHelper.mergeIntent(null, sem, 0.55, "");
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS);
    }

    @Test
    void mergeTentativeTime_samePathDishMentionFollowUp_skipsDefaultMonthWhenTimeNotOverridden() {
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
                        .timeAction("NEW")
                        .confidence(0.9)
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("CURRENT_MONTH")
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
    void mergeTentativeTime_afterDishProfit_llmOverrideThisMonthPlaceholder_withoutUtteranceSource_defers() {
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
        assertThat(tw).isNull();
    }

    @Test
    void canonicalQuerySemanticV2TimeAction_placeholderThisMonth_mapsToInherit() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .timeAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .build();
        assertThat(
                        AiQuerySemanticLlmMergeHelper.canonicalQuerySemanticV2TimeActionForHarness(
                                sem, prev, 0.55))
                .isEqualTo("INHERIT_PREVIOUS");
    }

    @Test
    void canonicalQuerySemanticV2TimeAction_placeholderThisMonth_explicitMonthPhrase_keepsOverride() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .timeAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .build();
        assertThat(
                        AiQuerySemanticLlmMergeHelper.canonicalQuerySemanticV2TimeActionForHarness(
                                sem, prev, 0.55, "AAA 这个月营业额多少？"))
                .isEqualTo("OVERRIDE");
    }

    @Test
    void mergeTentativeTime_samePathScopeOverride_thisMonthExplicitPhrase_resolvesMonthToDate() {
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
    }

    @Test
    void canonicalQuerySemanticV2TimeAction_thisMonthWithCurrentMessage_keepsOverride() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.92)
                        .timeAction("OVERRIDE")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("THIS_MONTH")
                                        .timeSource("CURRENT_MESSAGE")
                                        .needInheritFromPrevious(false)
                                        .build())
                        .build();
        assertThat(
                        AiQuerySemanticLlmMergeHelper.canonicalQuerySemanticV2TimeActionForHarness(
                                sem, prev, 0.55))
                .isEqualTo("OVERRIDE");
    }

    @Test
    void mergeTentativeTime_followUpPurchase_inheritTime_notForcedToMonthToDate() {
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
    void sanitize_namedDishWithGrossProfitRankingType_clearsRankingAndSetsMetricActionOverride() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .intent("DISH_PROFIT")
                        .metricAction("INHERIT_PREVIOUS")
                        .mentionedDishName("核桃芽菜西芹")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("profit_margin")
                                        .rankingType("dish_gross_profit_rate_ranking_low")
                                        .build())
                        .build();
        AiQuerySemanticV2DishProfitGate.SanitizeResult r = AiQuerySemanticV2DishProfitGate.sanitize(sem);
        assertThat(r.semantic().getMetric().getRankingType()).isNull();
        assertThat(r.semantic().getMetricAction()).isEqualTo("OVERRIDE");
    }

    @Test
    void mergeIntent_afterLowMarginRanking_namedDishFollowUp_usesGrossMarginQueryWire() {
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder()
                        .lastPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .lastIntentCode(AiResolvedQueryIntent.DISH_PROFIT)
                        .lastStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN)
                        .build();
        AiQuerySemanticParseResult raw =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .confidence(0.9)
                        .followUp(true)
                        .intentAction("INHERIT_PREVIOUS")
                        .timeAction("INHERIT_PREVIOUS")
                        .scopeAction("INHERIT_PREVIOUS")
                        .metricAction("INHERIT_PREVIOUS")
                        .intent("DISH_PROFIT")
                        .mentionedDishName("核桃芽菜西芹")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("profit_margin")
                                        .rankingType("dish_gross_profit_rate_ranking_low")
                                        .build())
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeType("LAST_MONTH")
                                        .startDate("2026-04-01")
                                        .endDate("2026-04-30")
                                        .timeSource("INHERITED_PREVIOUS")
                                        .needInheritFromPrevious(true)
                                        .build())
                        .build();
        AiQuerySemanticParseResult sanitized =
                AiQuerySemanticV2DishProfitGate.sanitize(raw).semantic();
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(null, sanitized, 0.55, "", prev);
        assertThat(merged.getStructuredIntentDetail())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
    }

    @Test
    void mergeIntent_afterLowMarginRanking_namedDish_rankingTypeNull_replacesInheritedRankingWire() {
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
                        .intentAction("INHERIT_PREVIOUS")
                        .timeAction("INHERIT_PREVIOUS")
                        .scopeAction("INHERIT_PREVIOUS")
                        .metricAction("INHERIT_PREVIOUS")
                        .intent("DISH_PROFIT")
                        .mentionedDishName("核桃芽菜西芹")
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("profit_margin")
                                        .rankingType(null)
                                        .build())
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
}
