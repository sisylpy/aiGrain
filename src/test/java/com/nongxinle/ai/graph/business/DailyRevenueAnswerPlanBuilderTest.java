package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyRevenueAnswerPlanBuilderTest {

    @Test
    void attachIfApplicable_skipsWhenNotRevenueOverviewPath() {
        AiRunState state = AiRunState.builder()
                .runId(1L)
                .costInsightPath(true)
                .revenueOverviewPath(false)
                .build();

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("totalRevenue", BigDecimal.TEN);
        inner.put("days", 1);
        inner.put("avgDailyRevenue", BigDecimal.TEN);
        inner.put("rawStats", Map.of("total_revenue", BigDecimal.TEN, "days", 1));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("success", true);
        env.put("data", inner);
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, env);

        DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
        assertNull(state.getRevenueAnswerPlan());
    }

    @Test
    void attachIfApplicable_buildsOverviewFromEnvelope() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();

        AiRunState state = AiRunState.builder()
                .runId(2L)
                .revenueOverviewPath(true)
                .resolvedQueryContext(rq)
                .rawUserInput("本月营业额怎么样")
                .build();

        Map<String, Object> rawStats = new LinkedHashMap<>();
        rawStats.put("total_revenue", new BigDecimal("900"));
        rawStats.put("days", 30);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("rawStats", rawStats);
        inner.put("days", 30);
        inner.put("totalRevenue", new BigDecimal("900"));
        inner.put("avgDailyRevenue", new BigDecimal("30"));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("success", true);
        env.put("data", inner);
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, env);

        DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
        assertNotNull(state.getRevenueAnswerPlan());
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW, state.getRevenueAnswerPlan().getPlanType());
        assertEquals(1, state.getRevenueAnswerPlan().getFocusRows().size());
    }

    @Test
    void attachIfApplicable_buildsWhenBusinessOverviewPathAndRevenueToolRan() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();

        AiRunState state = AiRunState.builder()
                .runId(210L)
                .businessOverviewPath(true)
                .revenueOverviewPath(false)
                .businessDiagnosisPath(false)
                .resolvedQueryContext(rq)
                .rawUserInput("这个月经营怎么样？")
                .build();

        Map<String, Object> rawStats = new LinkedHashMap<>();
        rawStats.put("total_revenue", new BigDecimal("900"));
        rawStats.put("days", 30);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("rawStats", rawStats);
        inner.put("days", 30);
        inner.put("totalRevenue", new BigDecimal("900"));
        inner.put("avgDailyRevenue", new BigDecimal("30"));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("success", true);
        env.put("data", inner);
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, env);

        DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
        assertNotNull(state.getRevenueAnswerPlan());
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW, state.getRevenueAnswerPlan().getPlanType());
    }

    @Test
    void attachIfApplicable_missingTool_setsDiagnosticPlan() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();

        AiRunState state = AiRunState.builder()
                .runId(3L)
                .revenueOverviewPath(true)
                .resolvedQueryContext(rq)
                .build();

        DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
        assertNotNull(state.getRevenueAnswerPlan());
        assertEquals("missing_tool_result",
                String.valueOf(state.getRevenueAnswerPlan().getDebug().get("failureReason")));
    }

    @Test
    void resolvePlanType_platformRankingWire_mapsToPlatformRankingPlanType() {
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING,
                DailyRevenueAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_PLATFORM_RANKING,
                        null));
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW,
                DailyRevenueAnswerPlanBuilder.resolvePlanType("", null));
    }

    @Test
    void resolvePlanType_storeAndDailyRanking_wiresOnly() {
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING,
                DailyRevenueAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING,
                        null));
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_DAILY_AMOUNT_RANKING,
                DailyRevenueAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING,
                        null));
    }

    @Test
    void attachIfApplicable_takeoutOverview_noExplainAggregateFlag_withoutPlatformRankingWire() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_TAKEOUT_OVERVIEW)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();

        AiRunState state = AiRunState.builder()
                .runId(11L)
                .revenueOverviewPath(true)
                .resolvedQueryContext(rq)
                .rawUserInput("哪个外卖平台金额最高？")
                .build();

        Map<String, Object> rawStats = new LinkedHashMap<>();
        rawStats.put("total_revenue", new BigDecimal("900"));
        rawStats.put("total_takeout_revenue", new BigDecimal("450"));
        rawStats.put("days", 30);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("rawStats", rawStats);
        inner.put("days", 30);
        inner.put("totalRevenue", new BigDecimal("900"));
        inner.put("avgDailyRevenue", new BigDecimal("30"));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("success", true);
        env.put("data", inner);
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, env);

        DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
        assertNotNull(state.getRevenueAnswerPlan());
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_TAKEOUT_OVERVIEW,
                state.getRevenueAnswerPlan().getPlanType());
        assertEquals(1, state.getRevenueAnswerPlan().getFocusRows().size());
        assertEquals("takeout_total",
                String.valueOf(state.getRevenueAnswerPlan().getFocusRows().get(0).get("role")));
        assertEquals(450.0,
                ((Number) state.getRevenueAnswerPlan().getFocusRows().get(0).get("revenueAmount")).doubleValue(),
                0.001);
        assertTrue(state.getRevenueAnswerPlan().getDebug().get("explainTakeoutChannelAggregateOnly") == null
                || Boolean.FALSE.equals(state.getRevenueAnswerPlan().getDebug().get("explainTakeoutChannelAggregateOnly")));
    }

    @Test
    void attachIfApplicable_platformRankingWire_mapsToPlatformRankingWithExplainFlag() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_PLATFORM_RANKING)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();

        AiRunState state = AiRunState.builder()
                .runId(12L)
                .revenueOverviewPath(true)
                .resolvedQueryContext(rq)
                .rawUserInput("外卖平台金额最高的是哪个")
                .build();

        Map<String, Object> rawStats = new LinkedHashMap<>();
        rawStats.put("total_revenue", new BigDecimal("900"));
        rawStats.put("total_takeout_revenue", new BigDecimal("450"));
        rawStats.put("days", 30);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("rawStats", rawStats);
        inner.put("days", 30);
        inner.put("totalRevenue", new BigDecimal("900"));
        inner.put("avgDailyRevenue", new BigDecimal("30"));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("success", true);
        env.put("data", inner);
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, env);

        DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
        assertNotNull(state.getRevenueAnswerPlan());
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING,
                state.getRevenueAnswerPlan().getPlanType());
        assertTrue(Boolean.TRUE.equals(state.getRevenueAnswerPlan().getDebug().get("explainTakeoutChannelAggregateOnly")));
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING,
                state.getRevenueAnswerPlan().getDebug().get("degradedFromPlanType"));
    }

    @Test
    void attachIfApplicable_storeRanking_splitsFocusAndSecondaryFromToolRanking() {
        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING)
                .build();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder().queryIntent(qi).build();

        AiRunState state = AiRunState.builder()
                .runId(20L)
                .revenueOverviewPath(true)
                .resolvedQueryContext(rq)
                .rawUserInput("哪个门店营业额最高？")
                .build();

        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("storeDepartmentId", 10L);
        r1.put("storeName", "AAA");
        r1.put("revenueAmount", 100.0);
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("storeDepartmentId", 20L);
        r2.put("storeName", "汀兰餐厅");
        r2.put("revenueAmount", 500.0);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("storeRevenueRanking", List.of(r1, r2));
        inner.put("days", 30);
        inner.put("totalRevenue", new BigDecimal("600"));
        inner.put("avgDailyRevenue", new BigDecimal("20"));
        inner.put("rawStats", Map.of("total_revenue", new BigDecimal("600"), "days", 30));

        Map<String, Object> env = new LinkedHashMap<>();
        env.put("success", true);
        env.put("data", inner);
        state.getToolResults().put(AiBusinessToolIds.REVENUE_QUERY, env);

        DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
        DailyRevenueAnswerPlan plan = state.getRevenueAnswerPlan();
        assertNotNull(plan);
        assertEquals(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING, plan.getPlanType());
        assertEquals(1, plan.getFocusRows().size());
        assertEquals("store_rank_top", plan.getFocusRows().get(0).get("role"));
        assertEquals("汀兰餐厅", plan.getFocusRows().get(0).get("storeName"));
        assertEquals(500.0,
                ((Number) plan.getFocusRows().get(0).get("revenueAmount")).doubleValue(), 0.001);
        assertEquals(1, plan.getSecondaryRows().size());
        assertEquals("store_rank_rest", plan.getSecondaryRows().get(0).get("role"));
        assertEquals("AAA", plan.getSecondaryRows().get(0).get("storeName"));
        assertNull(plan.getDebug().get("failureReason"));
    }
}
