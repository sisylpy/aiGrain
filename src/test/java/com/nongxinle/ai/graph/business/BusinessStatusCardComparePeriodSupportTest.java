package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedTimeWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BusinessStatusCardComparePeriodSupportTest {

    @Test
    void resolve_today_yesterdaySingleDay() {
        BusinessStatusCardComparePeriodSupport.ComparePeriod p =
                BusinessStatusCardComparePeriodSupport.resolve(
                        AiResolvedTimeWindow.TODAY, "2026-05-31", "2026-05-31");

        assertEquals("2026-05-30", p.compareStartDate());
        assertEquals("2026-05-30", p.compareEndDate());
        assertEquals("昨天", p.compareLabel());
        assertEquals(1L, p.compareDayCount());
    }

    @Test
    void resolve_thisWeek_shiftBackOneWeek() {
        BusinessStatusCardComparePeriodSupport.ComparePeriod p =
                BusinessStatusCardComparePeriodSupport.resolve(
                        AiResolvedTimeWindow.THIS_WEEK, "2026-05-26", "2026-06-01");

        assertEquals("2026-05-19", p.compareStartDate());
        assertEquals("2026-05-25", p.compareEndDate());
        assertEquals("上周", p.compareLabel());
    }

    @Test
    void resolve_thisMonth_sameCalendarOffset() {
        BusinessStatusCardComparePeriodSupport.ComparePeriod p =
                BusinessStatusCardComparePeriodSupport.resolve(
                        AiResolvedTimeWindow.THIS_MONTH, "2026-05-01", "2026-05-31");

        assertEquals("2026-04-01", p.compareStartDate());
        assertEquals("2026-04-30", p.compareEndDate());
        assertEquals("上月同期", p.compareLabel());
    }

    @Test
    void resolve_rolling7_priorEqualLengthWindow() {
        BusinessStatusCardComparePeriodSupport.ComparePeriod p =
                BusinessStatusCardComparePeriodSupport.resolve(
                        AiResolvedTimeWindow.ROLLING_7, "2026-05-25", "2026-05-31");

        assertEquals("2026-05-18", p.compareStartDate());
        assertEquals("2026-05-24", p.compareEndDate());
        assertEquals("前7天", p.compareLabel());
        assertEquals(7L, p.compareDayCount());
    }

    @Test
    void resolve_custom_previousEqualLengthInterval() {
        BusinessStatusCardComparePeriodSupport.ComparePeriod p =
                BusinessStatusCardComparePeriodSupport.resolve(
                        "CUSTOM", "2026-05-10", "2026-05-19");

        assertEquals("2026-04-30", p.compareStartDate());
        assertEquals("2026-05-09", p.compareEndDate());
        assertEquals("上一时间段", p.compareLabel());
        assertEquals(10L, p.compareDayCount());
    }

    @Test
    void resolve_blankDates_empty() {
        BusinessStatusCardComparePeriodSupport.ComparePeriod p =
                BusinessStatusCardComparePeriodSupport.resolve(AiResolvedTimeWindow.TODAY, "", "2026-05-31");

        assertNull(p.compareStartDate());
        assertNull(p.compareEndDate());
        assertNull(p.compareLabel());
    }
}
