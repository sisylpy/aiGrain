package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedTimeWindow;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 经营四卡对比期：仅基于 Resolver 已写入的 {@code timeLabel} + ISO 起止日确定性派生；
 * 不解析用户原文、不做中文关键词猜测。
 */
public final class BusinessStatusCardComparePeriodSupport {

    private BusinessStatusCardComparePeriodSupport() {}

    public record ComparePeriod(
            String compareStartDate,
            String compareEndDate,
            String compareLabel,
            long compareDayCount) {}

    public static ComparePeriod resolve(String timeLabel, String startDateIso, String endDateIso) {
        if (!StringUtils.hasText(startDateIso) || !StringUtils.hasText(endDateIso)) {
            return empty();
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDateIso.trim());
            end = LocalDate.parse(endDateIso.trim());
        } catch (Exception e) {
            return empty();
        }
        if (end.isBefore(start)) {
            return empty();
        }
        long periodDayCount = ChronoUnit.DAYS.between(start, end) + 1;
        String key =
                StringUtils.hasText(timeLabel)
                        ? AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(timeLabel)
                        : "";

        LocalDate compareStart;
        LocalDate compareEnd;
        String compareLabel;

        switch (key) {
            case AiResolvedTimeWindow.TODAY -> {
                compareStart = start.minusDays(1);
                compareEnd = compareStart;
                compareLabel = "昨天";
            }
            case AiResolvedTimeWindow.YESTERDAY -> {
                compareStart = start.minusDays(1);
                compareEnd = compareStart;
                compareLabel = "前天";
            }
            case AiResolvedTimeWindow.THIS_WEEK -> {
                compareStart = start.minusWeeks(1);
                compareEnd = end.minusWeeks(1);
                compareLabel = "上周";
            }
            case AiResolvedTimeWindow.THIS_MONTH -> {
                compareStart = start.minusMonths(1);
                compareEnd = end.minusMonths(1);
                compareLabel = "上月同期";
            }
            case AiResolvedTimeWindow.LAST_MONTH -> {
                compareStart = start.minusMonths(1);
                compareEnd = end.minusMonths(1);
                compareLabel = "上上个月";
            }
            case AiResolvedTimeWindow.ROLLING_7 -> {
                compareEnd = start.minusDays(1);
                compareStart = compareEnd.minusDays(periodDayCount - 1);
                compareLabel = "前7天";
            }
            case AiResolvedTimeWindow.LAST_YEAR_SAME_PERIOD -> {
                compareStart = start.minusYears(1);
                compareEnd = end.minusYears(1);
                compareLabel = "去年同期";
            }
            case AiResolvedTimeWindow.YEAR_TO_DATE -> {
                compareStart = start.minusYears(1);
                compareEnd = end.minusYears(1);
                compareLabel = "去年同期";
            }
            default -> {
                compareEnd = start.minusDays(1);
                compareStart = compareEnd.minusDays(periodDayCount - 1);
                compareLabel = periodDayCount == 1 ? "前一日" : "上一时间段";
            }
        }

        long compareDayCount = ChronoUnit.DAYS.between(compareStart, compareEnd) + 1;
        return new ComparePeriod(
                compareStart.toString(),
                compareEnd.toString(),
                compareLabel,
                compareDayCount);
    }

    private static ComparePeriod empty() {
        return new ComparePeriod(null, null, null, 0L);
    }
}
