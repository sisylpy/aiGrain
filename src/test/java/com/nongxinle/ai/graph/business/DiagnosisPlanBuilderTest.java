package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiDishProfitDishBrief;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisPlanBuilderTest {

    @Test
    void attachIfApplicable_overallWhenBusinessDiagnosisPath() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAmount", 100);
        PurchaseAnswerPlan pap = PurchaseAnswerPlan.builder()
                .planType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW)
                .scopeLabel("测试范围")
                .timeLabel("本月")
                .summary(summary)
                .build();
        AiRunState state = AiRunState.builder()
                .rawUserInput("怎么诊断这个月经营情况？")
                .businessDiagnosisPath(true)
                .purchaseAnswerPlan(pap)
                .build();

        DiagnosisPlanBuilder.attachIfApplicable(state);

        DiagnosisPlan dp = state.getDiagnosisPlan();
        assertNotNull(dp);
        assertEquals(DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS, dp.getPlanType());
        assertNotNull(dp.getSummary());
        assertFalse(dp.getEvidenceRows().isEmpty());
        assertNotNull(dp.getDebug().get("consumedAnswerPlans"));
        assertNotNull(dp.getDebug().get("missingAnswerPlans"));
    }

    @Test
    void attachIfApplicable_whenBusinessDiagnosisPath_ignores_empty_raw_input() {
        AiRunState state = AiRunState.builder()
                .businessDiagnosisPath(true)
                .rawUserInput("")
                .build();
        DiagnosisPlanBuilder.attachIfApplicable(state);
        assertNotNull(state.getDiagnosisPlan());
        assertEquals(DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS, state.getDiagnosisPlan().getPlanType());
    }

    @Test
    void attachIfApplicable_clearsWhenNotBusinessDiagnosisPath() {
        DiagnosisPlan existing = DiagnosisPlan.builder()
                .planType(DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS)
                .summary("x")
                .build();
        AiRunState state = AiRunState.builder()
                .businessDiagnosisPath(false)
                .diagnosisPlan(existing)
                .build();

        DiagnosisPlanBuilder.attachIfApplicable(state);

        assertNull(state.getDiagnosisPlan());
    }

    @Test
    void shouldAttach_onBusinessOverviewWhenHolisticCue() {
        AiRunState state = AiRunState.builder()
                .rawUserInput("这个月经营情况怎么样？")
                .businessOverviewPath(true)
                .businessDiagnosisPath(false)
                .build();
        assertTrue(DiagnosisPlanBuilder.shouldAttachDiagnosisPlan(state));
    }

    @Test
    void attachIfApplicable_stillBuildsWhenNoSubPlans() {
        AiRunState state = AiRunState.builder()
                .rawUserInput("帮我诊断一下这个月经营情况")
                .businessDiagnosisPath(true)
                .build();
        DiagnosisPlanBuilder.attachIfApplicable(state);
        assertNotNull(state.getDiagnosisPlan());
        assertTrue(state.getDiagnosisPlan().getEvidenceRows().isEmpty());
        assertNotNull(state.getDiagnosisPlan().getDebug().get("undiagnosableReason"));
    }

    @Test
    void attachIfApplicable_consumesBusinessDiagnosisDishOverviewPlan() {
        AiDishProfitDishBrief yogurt = AiDishProfitDishBrief.builder()
                .dishName("酸奶碗")
                .salesQty("10")
                .salesAmount("100")
                .theoreticalCost("80")
                .actualCost("95.77")
                .grossProfitRate("4.23%")
                .build();
        AiDishProfitOverviewResult overview = AiDishProfitOverviewResult.builder()
                .dishCount(1)
                .lowProfitDishes(List.of(yogurt))
                .build();
        DishProfitAnswerPlan dpp = DishProfitAnswerPlan.builder()
                .planType(DishProfitAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_DISH_OVERVIEW)
                .scopeLabel("门店A")
                .timeLabel("本月")
                .focusRows(List.of(Map.of("dishName", "酸奶碗", "blendedGrossMarginRateOnListPrice", "4.23%")))
                .build();
        AiRunState state = AiRunState.builder()
                .businessDiagnosisPath(true)
                .dishProfitOverviewResult(overview)
                .dishProfitAnswerPlan(dpp)
                .build();

        DiagnosisPlanBuilder.attachIfApplicable(state);

        DiagnosisPlan dp = state.getDiagnosisPlan();
        assertNotNull(dp);
        @SuppressWarnings("unchecked")
        List<String> consumed = (List<String>) dp.getDebug().get("consumedAnswerPlans");
        assertNotNull(consumed);
        assertTrue(consumed.stream().anyMatch(s -> s.startsWith("DishProfitAnswerPlan:")));
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) dp.getDebug().get("missingAnswerPlans");
        assertFalse(missing.contains("DishProfitAnswerPlan"));
    }

    @Test
    void attachIfApplicable_businessOverview_noMissingSubPlansFocusWhenPartiallyMounted() {
        Map<String, Object> rs = new LinkedHashMap<>();
        rs.put("totalRevenueHint", "100");
        DailyRevenueAnswerPlan rap = DailyRevenueAnswerPlan.builder()
                .planType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW)
                .scopeLabel("集团")
                .timeLabel("本月")
                .summary(rs)
                .build();
        AiRunState state = AiRunState.builder()
                .rawUserInput("这个月经营得怎么样")
                .businessOverviewPath(true)
                .businessDiagnosisPath(false)
                .revenueAnswerPlan(rap)
                .build();

        DiagnosisPlanBuilder.attachIfApplicable(state);

        DiagnosisPlan dp = state.getDiagnosisPlan();
        assertNotNull(dp);
        assertEquals("business_overview_path", dp.getDebug().get("attachSurface"));
        assertTrue(dp.getSummary().contains("经营概览"));

        List<Map<String, Object>> ff = dp.getFocusFindings();
        if (ff != null) {
            for (Map<String, Object> row : ff) {
                assertFalse("MISSING_SUB_PLANS".equals(String.valueOf(row.get("code"))));
            }
        }

        @SuppressWarnings("unchecked")
        List<String> consumed = (List<String>) dp.getDebug().get("consumedAnswerPlans");
        assertNotNull(consumed);
        assertTrue(consumed.stream().anyMatch(s -> s.startsWith("DailyRevenueAnswerPlan:")));
    }

    @Test
    void shouldPreferComposer_falseOnBusinessOverviewEvenWithDiagnosisPlan() {
        DiagnosisPlan dp = DiagnosisPlan.builder()
                .planType(DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS)
                .summary("x")
                .build();
        AiRunState state = AiRunState.builder()
                .diagnosisPlan(dp)
                .businessOverviewPath(true)
                .build();
        assertFalse(DiagnosisPlanBuilder.shouldPreferDiagnosisPlanInComposer(state));
    }
}
