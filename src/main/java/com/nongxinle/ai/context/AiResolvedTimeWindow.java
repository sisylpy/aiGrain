package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 时间窗口解析结果；第一版仅规则解析口语（不调 LLM）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResolvedTimeWindow {

    public static final String TODAY = "TODAY";
    public static final String YESTERDAY = "YESTERDAY";
    public static final String THIS_WEEK = "THIS_WEEK";
    public static final String THIS_MONTH = "THIS_MONTH";
    public static final String LAST_MONTH = "LAST_MONTH";
    /** 含今日共 7 天的滚动窗口，与业务上「最近 7 天」一致 */
    public static final String ROLLING_7 = "ROLLING_7";
    public static final String CUSTOM = "CUSTOM";

    private String timeLabel;

    private LocalDate startDate;
    private LocalDate endDate;

    private String displayText;
    private boolean inheritedFromPreviousTurn;

    /**
     * 当前句是否包含可解析的时间用语；由 {@link AiResolvedTimeWindow#tryParseExplicitFromUserMessage} 命中时为 {@code true}，
     * 由解析器默认本月或继承上一轮写入时为 {@code false}。
     */
    @Builder.Default
    private boolean explicitTimeMentioned = false;

    /**
     * 仅当用户消息中出现时间相关词时返回窗口；否则返回 {@code null}，供
     * {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 在「独立问默认本月 / 追问继承上一轮」中决策。
     */
    public static AiResolvedTimeWindow tryParseExplicitFromUserMessage(String rawMessage, LocalDate today) {
        LocalDate anchor = today != null ? today : LocalDate.now();
        String msg = rawMessage == null ? "" : rawMessage.trim().toLowerCase(Locale.ROOT);

        if (containsAny(msg, "昨天", "昨日")) {
            LocalDate d = anchor.minusDays(1);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(YESTERDAY)
                    .startDate(d)
                    .endDate(d)
                    .displayText("昨天（" + d + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (containsAny(msg, "今天", "今日")) {
            return AiResolvedTimeWindow.builder()
                    .timeLabel(TODAY)
                    .startDate(anchor)
                    .endDate(anchor)
                    .displayText("今天（" + anchor + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (containsAny(msg, "上个月", "上月")) {
            LocalDate firstThisMonth = anchor.withDayOfMonth(1);
            LocalDate endLast = firstThisMonth.minusDays(1);
            LocalDate startLast = endLast.withDayOfMonth(1);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(LAST_MONTH)
                    .startDate(startLast)
                    .endDate(endLast)
                    .displayText("上个月（" + startLast + "～" + endLast + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (containsAny(msg, "最近7天", "最近七天", "近7天", "近七天")) {
            LocalDate start = anchor.minusDays(6);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(ROLLING_7)
                    .startDate(start)
                    .endDate(anchor)
                    .displayText("最近 7 天（" + start + "～" + anchor + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (containsAny(msg, "本月", "这个月", "当月")) {
            LocalDate start = anchor.withDayOfMonth(1);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(THIS_MONTH)
                    .startDate(start)
                    .endDate(anchor)
                    .displayText("本月至今（" + start + "～" + anchor + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        return null;
    }

    /** 独立问句未给出任何时间词时的默认：本月至今（非「继承」）。 */
    public static AiResolvedTimeWindow defaultMonthToDate(LocalDate today) {
        LocalDate anchor = today != null ? today : LocalDate.now();
        LocalDate start = anchor.withDayOfMonth(1);
        return AiResolvedTimeWindow.builder()
                .timeLabel(THIS_MONTH)
                .startDate(start)
                .endDate(anchor)
                .displayText("本月至今（" + start + "～" + anchor + "）")
                .inheritedFromPreviousTurn(false)
                .explicitTimeMentioned(false)
                .build();
    }

    /**
     * @deprecated 请使用 {@link #tryParseExplicitFromUserMessage} + {@link #defaultMonthToDate} ，或由
     * {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 统一收尾，以便追问继承上一轮时间。
     */
    @Deprecated
    public static AiResolvedTimeWindow fromUserMessage(String rawMessage, LocalDate today) {
        AiResolvedTimeWindow explicit = tryParseExplicitFromUserMessage(rawMessage, today);
        if (explicit != null) {
            return explicit;
        }
        return defaultMonthToDate(today);
    }

    private static boolean containsAny(String normalizedLower, String... needles) {
        for (String n : needles) {
            if (normalizedLower.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
