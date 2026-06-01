package com.nongxinle.ai.graph.business;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessOverviewDishSalesReasonTimeExpressionSupportTest {

    @Test
    void resolveTimeExpression_prefersReportLabel() {
        assertEquals(
                "本月至今",
                BusinessOverviewDishSalesReasonTimeExpressionSupport.resolveTimeExpression(
                        "本月至今", "2026-05-01", "2026-05-31"));
    }

    @Test
    void resolveTimeExpression_singleDayWithoutLabel_usesIsoDate() {
        assertEquals(
                "2026-05-31",
                BusinessOverviewDishSalesReasonTimeExpressionSupport.resolveTimeExpression(
                        null, "2026-05-31", "2026-05-31"));
    }

    @Test
    void resolveTimeExpression_rangeWithoutLabel_usesGenericPeriod() {
        assertEquals(
                "该时间段",
                BusinessOverviewDishSalesReasonTimeExpressionSupport.resolveTimeExpression(
                        null, "2026-05-01", "2026-05-31"));
    }

    @Test
    void resolveTimeExpression_yesterdayLabel() {
        assertEquals(
                "昨天",
                BusinessOverviewDishSalesReasonTimeExpressionSupport.resolveTimeExpression(
                        "昨天", "2026-05-30", "2026-05-30"));
    }

    @Test
    void resolveTimeExpression_last7DaysLabel() {
        assertEquals(
                "最近 7 天",
                BusinessOverviewDishSalesReasonTimeExpressionSupport.resolveTimeExpression(
                        "最近 7 天", "2026-05-25", "2026-05-31"));
    }
}
