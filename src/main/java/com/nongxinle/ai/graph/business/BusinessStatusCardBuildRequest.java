package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Card 投影层只读上下文：时间窗、展示标签与对比期均来自 RunState / Resolver，不解析用户原文。
 */
@Value
@Builder
public class BusinessStatusCardBuildRequest {

    String startDate;
    String endDate;
    String reportLabel;
    String scopeLabel;

    /** Resolver {@link AiResolvedTimeWindow#getTimeLabel()} 归一化值，用于对比期派生。 */
    String timeLabel;

    /** 与 reportLabel 对齐的 Agent / 卡片时间表述。 */
    String timeExpression;

    Long periodDayCount;

    String compareStartDate;
    String compareEndDate;
    String compareLabel;

    public static BusinessStatusCardBuildRequest fromRunState(AiRunState state) {
        if (state == null) {
            return BusinessStatusCardBuildRequest.builder().build();
        }
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String reportLabel = firstNonBlank(
                tw != null ? tw.getTimeSubjectText() : null,
                tw != null ? tw.getDisplayTimeRange() : null,
                state.getRevenueAnswerPlan() != null ? state.getRevenueAnswerPlan().getTimeLabel() : null,
                state.getPurchaseAnswerPlan() != null ? state.getPurchaseAnswerPlan().getTimeLabel() : null,
                state.getStockReduceAnswerPlan() != null ? state.getStockReduceAnswerPlan().getTimeLabel() : null,
                state.getDishProfitAnswerPlan() != null ? state.getDishProfitAnswerPlan().getTimeLabel() : null);
        String scopeLabel = firstNonBlank(
                state.getRevenueAnswerPlan() != null ? state.getRevenueAnswerPlan().getScopeLabel() : null,
                state.getPurchaseAnswerPlan() != null ? state.getPurchaseAnswerPlan().getScopeLabel() : null,
                state.getBusinessOverviewAnswerPlan() != null
                        ? state.getBusinessOverviewAnswerPlan().getScopeLabel()
                        : null);

        String startDate = blankToNull(state.getStatStartDate());
        String endDate = blankToNull(state.getStatEndDate());
        String timeLabel = resolveTimeLabel(state.getResolvedQueryContext());
        String timeExpression =
                BusinessOverviewDishSalesReasonTimeExpressionSupport.resolveTimeExpression(
                        reportLabel, startDate, endDate);
        Long periodDayCount = computePeriodDayCount(startDate, endDate);

        BusinessStatusCardComparePeriodSupport.ComparePeriod compare =
                BusinessStatusCardComparePeriodSupport.resolve(timeLabel, startDate, endDate);

        return BusinessStatusCardBuildRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .reportLabel(reportLabel)
                .scopeLabel(scopeLabel)
                .timeLabel(blankToNull(timeLabel))
                .timeExpression(timeExpression)
                .periodDayCount(periodDayCount)
                .compareStartDate(compare.compareStartDate())
                .compareEndDate(compare.compareEndDate())
                .compareLabel(compare.compareLabel())
                .build();
    }

    private static String resolveTimeLabel(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getTimeWindow() == null) {
            return null;
        }
        return blankToNull(ctx.getTimeWindow().getTimeLabel());
    }

    private static Long computePeriodDayCount(String startDate, String endDate) {
        if (!StringUtils.hasText(startDate) || !StringUtils.hasText(endDate)) {
            return null;
        }
        try {
            LocalDate start = LocalDate.parse(startDate.trim());
            LocalDate end = LocalDate.parse(endDate.trim());
            if (end.isBefore(start)) {
                return null;
            }
            return ChronoUnit.DAYS.between(start, end) + 1;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
