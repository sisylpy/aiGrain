package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 统计时间窗口：承载结构化 {@link #timeLabel}、起止日与展示文案。
 * 口语时间语义由 LLM {@code QuerySemanticParser} 产出 {@code timeType} / {@code timeAction}，
 * Java 侧通过 {@link #fromSemanticTimeType} / {@link #fromSemanticCustomRange} 落地日期，本类不做用户话术关键词解析。
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
    /** 自然年上一完整历年（日历 1 月 1 日～12 月 31 日） */
    public static final String LAST_YEAR = "LAST_YEAR";
    /**
     * 将上一轮统计窗口整体平移一年前（去年同期），由语义 LLM timeType 触发、{@link com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper} 结合上轮记忆落地。
     */
    public static final String LAST_YEAR_SAME_PERIOD = "LAST_YEAR_SAME_PERIOD";
    /** 含今日共 7 天的滚动窗口，与业务上「最近 7 天」一致 */
    public static final String ROLLING_7 = "ROLLING_7";
    /**
     * 本年累计（1月1日～当前日期），由语义 LLM timeType=YEAR_TO_DATE 触发。
     * 与「今年到现在/今年至今/今年以来」等用户话术对齐。
     */
    public static final String YEAR_TO_DATE = "YEAR_TO_DATE";
    public static final String CUSTOM = "CUSTOM";

    private String timeLabel;

    private LocalDate startDate;
    private LocalDate endDate;

    private String displayText;
    private boolean inheritedFromPreviousTurn;

    /**
     * 是否为「本轮明确给出的时间窗」（语义 LLM 或结构化继承去年同期等）；非继承上一轮默认窗、非独立问默认本月至今。
     */
    @Builder.Default
    private boolean explicitTimeMentioned = false;

    /**
     * 归一化 LLM / 合并层传入的 timeType（大小写、别名）。
     */
    public static String normalizeSemanticTimeTypeLabel(String timeTypeRaw) {
        if (timeTypeRaw == null || timeTypeRaw.isBlank()) {
            return "";
        }
        String u = timeTypeRaw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("CURRENT_MONTH".equals(u)) {
            return THIS_MONTH;
        }
        if ("LAST_YEAR_SAME_PERIOD".equals(u)) {
            return LAST_YEAR_SAME_PERIOD;
        }
        return u;
    }

    /**
     * 由语义层 timeType 锚定 {@code today} 落地起止日；{@link #LAST_YEAR_SAME_PERIOD} 依赖 {@code previousWindow} 的起止日整体减一年。
     *
     * @param timeTypeRaw    原始 timeType，会先 {@link #normalizeSemanticTimeTypeLabel}
     * @param today          「今天」锚点（Harness 可传入固定日）
     * @param previousWindow 去年同期所用的上一轮窗口；其它 timeType 可为 {@code null}
     */
    public static AiResolvedTimeWindow fromSemanticTimeType(
            String timeTypeRaw, LocalDate today, AiResolvedTimeWindow previousWindow) {
        LocalDate anchor = today != null ? today : LocalDate.now();
        String label = normalizeSemanticTimeTypeLabel(timeTypeRaw);
        if (label.isEmpty()) {
            return null;
        }
        if (LAST_YEAR_SAME_PERIOD.equals(label)) {
            if (previousWindow == null
                    || previousWindow.getStartDate() == null
                    || previousWindow.getEndDate() == null) {
                return null;
            }
            LocalDate s = previousWindow.getStartDate().minusYears(1);
            LocalDate e = previousWindow.getEndDate().minusYears(1);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(LAST_YEAR_SAME_PERIOD)
                    .startDate(s)
                    .endDate(e)
                    .displayText("去年同期（" + s + "～" + e + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (TODAY.equals(label)) {
            return AiResolvedTimeWindow.builder()
                    .timeLabel(TODAY)
                    .startDate(anchor)
                    .endDate(anchor)
                    .displayText("今天（" + anchor + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (YESTERDAY.equals(label)) {
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
        if (THIS_WEEK.equals(label)) {
            LocalDate start = anchor.minusDays((anchor.getDayOfWeek().getValue() + 6) % 7);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(THIS_WEEK)
                    .startDate(start)
                    .endDate(anchor)
                    .displayText("本周至今（" + start + "～" + anchor + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (THIS_MONTH.equals(label)) {
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
        if (LAST_MONTH.equals(label)) {
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
        if (LAST_YEAR.equals(label)) {
            LocalDate start = anchor.minusYears(1).withDayOfYear(1);
            LocalDate end = anchor.minusYears(1).withMonth(12).withDayOfMonth(31);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(LAST_YEAR)
                    .startDate(start)
                    .endDate(end)
                    .displayText("去年（" + start + "～" + end + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        if (ROLLING_7.equals(label)) {
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
        if (YEAR_TO_DATE.equals(label)) {
            LocalDate start = anchor.withDayOfYear(1);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(YEAR_TO_DATE)
                    .startDate(start)
                    .endDate(anchor)
                    .displayText("本年累计（" + start + "～" + anchor + "）")
                    .inheritedFromPreviousTurn(false)
                    .explicitTimeMentioned(true)
                    .build();
        }
        return null;
    }

    /** 语义层 {@code CUSTOM}：ISO 起止日已由解析器抽出时使用。 */
    public static AiResolvedTimeWindow fromSemanticCustomRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        return AiResolvedTimeWindow.builder()
                .timeLabel(CUSTOM)
                .startDate(start)
                .endDate(end)
                .displayText("自定义（" + start + "～" + end + "）")
                .inheritedFromPreviousTurn(false)
                .explicitTimeMentioned(true)
                .build();
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

    /** 从 ISO-8601 日期字符串解析，供语义合并层使用。 */
    public static LocalDate parseIsoDateOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
