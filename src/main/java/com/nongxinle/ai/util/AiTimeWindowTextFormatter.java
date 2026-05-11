package com.nongxinle.ai.util;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

/**
 * 从 {@link AiRunState#getResolvedQueryContext()} 的 {@code timeWindow}（或等价的 ISO 起止日）生成对用户展示的稳定时间口径文案，
 * 避免回答模板硬编码「本月 / 本期」等与实际统计窗不一致。
 */
public final class AiTimeWindowTextFormatter {

    private AiTimeWindowTextFormatter() {
    }

    @Value
    @Builder
    public static class UserPhrases {
        /** 上个月 / 本月至今 / 今天 / 最近7天 / 该统计区间 */
        String timeSubjectText;
        /** 上个月（2026-04-01～2026-04-30）；单日为 今天（2026-05-11） */
        String displayTimeRange;
        /** 【时间范围】+ displayTimeRange */
        String bracketTimeRangeLine;

        /** 与 {@link #displayTimeRange} 相同，便于调用方按「主题+括号日期」接写业务句。 */
        public String getSubjectWithRange() {
            return displayTimeRange;
        }
    }

    /**
     * 优先 {@code state.resolvedQueryContext.timeWindow}；否则用 {@code statStartDate/statEndDate}；再否则默认本月至今。
     */
    public static UserPhrases forAnswer(AiRunState state) {
        LocalDate today = LocalDate.now();
        if (state == null) {
            return fromWindow(AiResolvedTimeWindow.defaultMonthToDate(today), today);
        }
        AiResolvedTimeWindow tw = null;
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        if (ctx != null) {
            tw = ctx.getTimeWindow();
        }
        if (tw == null && state.getStatStartDate() != null && state.getStatEndDate() != null) {
            try {
                LocalDate s = LocalDate.parse(state.getStatStartDate().trim());
                LocalDate e = LocalDate.parse(state.getStatEndDate().trim());
                tw = AiResolvedTimeWindow.builder()
                        .timeLabel(inferLabel(s, e, today))
                        .startDate(s)
                        .endDate(e)
                        .build();
            } catch (Exception ignore) {
                // fall through
            }
        }
        if (tw == null) {
            tw = AiResolvedTimeWindow.defaultMonthToDate(today);
        }
        return fromWindow(ensureDates(tw, today), today);
    }

    /** Tool 等仅有 yyyy-MM-dd 边界、无完整 {@link AiResolvedTimeWindow} 时使用。 */
    public static UserPhrases fromIsoRange(String startIso, String endIso, LocalDate today) {
        LocalDate anchor = today != null ? today : LocalDate.now();
        try {
            if (startIso != null && endIso != null && !startIso.isBlank() && !endIso.isBlank()) {
                LocalDate s = LocalDate.parse(startIso.trim());
                LocalDate e = LocalDate.parse(endIso.trim());
                String label = inferLabel(s, e, anchor);
                AiResolvedTimeWindow tw = AiResolvedTimeWindow.builder()
                        .timeLabel(label)
                        .startDate(s)
                        .endDate(e)
                        .build();
                return fromWindow(tw, anchor);
            }
        } catch (Exception ignore) {
            // fall through
        }
        return fromWindow(AiResolvedTimeWindow.defaultMonthToDate(anchor), anchor);
    }

    public static UserPhrases fromWindow(AiResolvedTimeWindow tw, LocalDate today) {
        if (today == null) {
            today = LocalDate.now();
        }
        if (tw == null) {
            tw = AiResolvedTimeWindow.defaultMonthToDate(today);
        }
        AiResolvedTimeWindow fixed = ensureDates(tw, today);
        LocalDate s = fixed.getStartDate();
        LocalDate e = fixed.getEndDate();
        String label = fixed.getTimeLabel();
        if (label == null || label.isBlank() || AiResolvedTimeWindow.CUSTOM.equals(label)) {
            label = inferLabel(s, e, today);
        }
        String subject = subjectForLabel(label, s, e, today);
        String display = formatDisplayRange(subject, s, e);
        return UserPhrases.builder()
                .timeSubjectText(subject)
                .displayTimeRange(display)
                .bracketTimeRangeLine("【时间范围】" + display)
                .build();
    }

    private static AiResolvedTimeWindow ensureDates(AiResolvedTimeWindow tw, LocalDate today) {
        if (tw.getStartDate() != null && tw.getEndDate() != null) {
            return tw;
        }
        AiResolvedTimeWindow fallback = AiResolvedTimeWindow.defaultMonthToDate(today);
        return AiResolvedTimeWindow.builder()
                .timeLabel(tw.getTimeLabel() != null && !tw.getTimeLabel().isBlank()
                        ? tw.getTimeLabel() : fallback.getTimeLabel())
                .startDate(tw.getStartDate() != null ? tw.getStartDate() : fallback.getStartDate())
                .endDate(tw.getEndDate() != null ? tw.getEndDate() : fallback.getEndDate())
                .displayText(tw.getDisplayText())
                .explicitTimeMentioned(tw.isExplicitTimeMentioned())
                .inheritedFromPreviousTurn(tw.isInheritedFromPreviousTurn())
                .build();
    }

    private static String inferLabel(LocalDate s, LocalDate e, LocalDate today) {
        if (s == null || e == null) {
            return AiResolvedTimeWindow.THIS_MONTH;
        }
        if (s.equals(e)) {
            if (s.equals(today)) {
                return AiResolvedTimeWindow.TODAY;
            }
            if (s.equals(today.minusDays(1))) {
                return AiResolvedTimeWindow.YESTERDAY;
            }
            return AiResolvedTimeWindow.CUSTOM;
        }
        if (e.equals(today) && s.equals(today.minusDays(6))) {
            return AiResolvedTimeWindow.ROLLING_7;
        }
        LocalDate monthStart = today.withDayOfMonth(1);
        if (s.equals(monthStart) && e.equals(today) && !today.isBefore(s)) {
            return AiResolvedTimeWindow.THIS_MONTH;
        }
        LocalDate firstThis = today.withDayOfMonth(1);
        LocalDate endLast = firstThis.minusDays(1);
        LocalDate startLast = endLast.withDayOfMonth(1);
        if (s.equals(startLast) && e.equals(endLast)) {
            return AiResolvedTimeWindow.LAST_MONTH;
        }
        return AiResolvedTimeWindow.CUSTOM;
    }

    private static String subjectForLabel(String label, LocalDate s, LocalDate e, LocalDate today) {
        if (AiResolvedTimeWindow.LAST_MONTH.equals(label)) {
            return "上个月";
        }
        if (AiResolvedTimeWindow.TODAY.equals(label)) {
            return "今天";
        }
        if (AiResolvedTimeWindow.YESTERDAY.equals(label)) {
            return "昨天";
        }
        if (AiResolvedTimeWindow.ROLLING_7.equals(label)) {
            return "最近7天";
        }
        if (AiResolvedTimeWindow.THIS_WEEK.equals(label)) {
            return "本周";
        }
        if (AiResolvedTimeWindow.THIS_MONTH.equals(label)) {
            if (s != null && e != null && today != null
                    && s.equals(today.withDayOfMonth(1))
                    && e.equals(today)
                    && s.getMonth().equals(today.getMonth())
                    && s.getYear() == today.getYear()) {
                return "本月至今";
            }
            return "本月";
        }
        return "该统计区间";
    }

    private static String formatDisplayRange(String subject, LocalDate s, LocalDate e) {
        if (s == null || e == null) {
            return subject;
        }
        if (s.equals(e)) {
            return subject + "（" + s + "）";
        }
        return subject + "（" + s + "～" + e + "）";
    }
}
