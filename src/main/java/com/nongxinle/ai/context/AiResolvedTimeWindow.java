package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * V2 主链中日期由 LLM {@code time.startDate}/{@code time.endDate} 提供，经
 * {@link com.nongxinle.ai.semantic.SemanticTimeContractCheck} 校验后写入本对象。
 * <p>
 * {@code timeLabel} 仅为 LLM {@code timeType} 的归一化标签，用于 debug / display / contract，
 * <b>不作为</b>日期计算或业务分支依据。展示文案见 {@link AiResolvedTimeWindowDisplaySupport}。
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
    /** 去年同期（展示/合同校验用标签；起止日由 LLM 给出） */
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
     * 归一化 LLM / 合并层传入的 timeType（大小写、别名）；仅作 label 承载与合同校验，不用于 Java 侧推算起止日。
     */
    public static String normalizeSemanticTimeTypeLabel(String timeTypeRaw) {
        if (timeTypeRaw == null || timeTypeRaw.isBlank()) {
            return "";
        }
        String u = timeTypeRaw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("CURRENT_MONTH".equals(u)) {
            return THIS_MONTH;
        }
        if ("PREVIOUS_MONTH".equals(u)) {
            return LAST_MONTH;
        }
        if ("LAST_YEAR_SAME_PERIOD".equals(u)) {
            return LAST_YEAR_SAME_PERIOD;
        }
        return u;
    }

    /** 从 ISO-8601 日期字符串解析，供语义合并层与合同校验使用。 */
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
