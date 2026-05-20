package com.nongxinle.ai.util;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.core.AiRunState;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

/**
 * 从 {@link AiRunState#getResolvedQueryContext()} 的 {@code timeWindow}（或等价的 ISO 起止日）生成对用户展示的稳定时间口径文案。
 * Label → 中文映射集中在 {@link AiResolvedTimeWindowDisplaySupport}；本类不推断 label、不补默认日期。
 */
public final class AiTimeWindowTextFormatter {

    private static final UserPhrases UNKNOWN_TIME =
            UserPhrases.builder()
                    .timeSubjectText("该统计区间")
                    .displayTimeRange("该统计区间")
                    .bracketTimeRangeLine("【时间范围】该统计区间")
                    .build();

    private AiTimeWindowTextFormatter() {
    }

    @Value
    @Builder
    public static class UserPhrases {
        String timeSubjectText;
        String displayTimeRange;
        String bracketTimeRangeLine;

        public String getSubjectWithRange() {
            return displayTimeRange;
        }
    }

    public static UserPhrases forAnswer(AiRunState state) {
        if (state == null) {
            return UNKNOWN_TIME;
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
                tw = AiResolvedTimeWindow.builder().startDate(s).endDate(e).build();
            } catch (Exception ignore) {
                // fall through
            }
        }
        if (tw == null) {
            return UNKNOWN_TIME;
        }
        return fromWindow(tw);
    }

    public static UserPhrases fromIsoRange(String startIso, String endIso, LocalDate today) {
        try {
            if (startIso != null && endIso != null && !startIso.isBlank() && !endIso.isBlank()) {
                LocalDate s = LocalDate.parse(startIso.trim());
                LocalDate e = LocalDate.parse(endIso.trim());
                return fromWindow(
                        AiResolvedTimeWindow.builder().startDate(s).endDate(e).build());
            }
        } catch (Exception ignore) {
            // fall through
        }
        return UNKNOWN_TIME;
    }

    public static UserPhrases fromWindow(AiResolvedTimeWindow tw) {
        if (tw == null || tw.getStartDate() == null || tw.getEndDate() == null) {
            return UNKNOWN_TIME;
        }
        String subject = AiResolvedTimeWindowDisplaySupport.answerTimeSubject(tw);
        String display =
                AiResolvedTimeWindowDisplaySupport.formatDisplayRange(
                        subject, tw.getStartDate(), tw.getEndDate());
        return UserPhrases.builder()
                .timeSubjectText(subject)
                .displayTimeRange(display)
                .bracketTimeRangeLine("【时间范围】" + display)
                .build();
    }
}
