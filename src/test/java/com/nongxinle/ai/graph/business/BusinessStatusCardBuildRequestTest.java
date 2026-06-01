package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BusinessStatusCardBuildRequestTest {

    @Test
    void fromRunState_populatesPeriodAndCompareFromResolvedContext() {
        AiRunState state = new AiRunState();
        state.setStatStartDate("2026-05-01");
        state.setStatEndDate("2026-05-31");
        state.setResolvedQueryContext(
                AiResolvedQueryContext.builder()
                        .timeWindow(
                                AiResolvedTimeWindow.builder()
                                        .timeLabel(AiResolvedTimeWindow.THIS_MONTH)
                                        .build())
                        .build());
        state.setRevenueAnswerPlan(
                DailyRevenueAnswerPlan.builder()
                        .planType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW)
                        .timeLabel("本月至今")
                        .build());

        BusinessStatusCardBuildRequest req = BusinessStatusCardBuildRequest.fromRunState(state);

        assertEquals("2026-05-01", req.getStartDate());
        assertEquals("2026-05-31", req.getEndDate());
        assertEquals("本月至今", req.getReportLabel());
        assertEquals(AiResolvedTimeWindow.THIS_MONTH, req.getTimeLabel());
        assertNotNull(req.getTimeExpression());
        assertEquals(31L, req.getPeriodDayCount());
        assertEquals("2026-04-01", req.getCompareStartDate());
        assertEquals("2026-04-30", req.getCompareEndDate());
        assertEquals("上月同期", req.getCompareLabel());
    }
}
