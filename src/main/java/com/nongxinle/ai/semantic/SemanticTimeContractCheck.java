package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/**
 * V2 时间输出合同校验：仅做结构自洽检查，不解析用户自然语言时间词。
 * 合同通过时采用 LLM 的 {@code startDate}/{@code endDate}/{@code timeSource}；失败时进入 Resolver 澄清。
 */
public final class SemanticTimeContractCheck {

    public static final String SOURCE_CURRENT_MESSAGE_EXPLICIT = "CURRENT_MESSAGE_EXPLICIT";
    public static final String SOURCE_INHERITED_PREVIOUS = "INHERITED_PREVIOUS";
    public static final String SOURCE_DEFAULT_MONTH_TO_DATE = "DEFAULT_MONTH_TO_DATE";

    public static final String FAIL_MISSING_TIME_FIELDS = "MISSING_TIME_FIELDS";
    public static final String FAIL_INHERIT_WITHOUT_PREVIOUS = "INHERIT_WITHOUT_PREVIOUS";
    public static final String FAIL_TIME_TYPE_DATE_MISMATCH = "TIME_TYPE_DATE_MISMATCH";
    public static final String FAIL_TIME_SOURCE_CONFLICT = "TIME_SOURCE_CONFLICT";
    public static final String FAIL_INVALID_DATE_RANGE = "INVALID_DATE_RANGE";
    public static final String FAIL_UNSUPPORTED_TIME_SOURCE = "UNSUPPORTED_TIME_SOURCE";

    private SemanticTimeContractCheck() {
    }

    public record Result(
            boolean valid,
            String failureReason,
            LocalDate normalizedStartDate,
            LocalDate normalizedEndDate,
            String normalizedTimeSource,
            String clarificationQuestion) {

        public AiResolvedTimeWindow toTimeWindow(String timeTypeLabel) {
            if (!valid || normalizedStartDate == null || normalizedEndDate == null) {
                return null;
            }
            String label =
                    StringUtils.hasText(timeTypeLabel)
                            ? AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(timeTypeLabel)
                            : AiResolvedTimeWindow.CUSTOM;
            if (!StringUtils.hasText(label)) {
                label = AiResolvedTimeWindow.CUSTOM;
            }
            boolean inherited = SOURCE_INHERITED_PREVIOUS.equals(normalizedTimeSource);
            boolean explicit = SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(normalizedTimeSource);
            return AiResolvedTimeWindow.builder()
                    .timeLabel(label)
                    .startDate(normalizedStartDate)
                    .endDate(normalizedEndDate)
                    .displayText(
                            normalizedStartDate.toString() + "～" + normalizedEndDate.toString())
                    .inheritedFromPreviousTurn(inherited)
                    .explicitTimeMentioned(explicit)
                    .build();
        }
    }

    /**
     * Matrix / 结构化短路：V2 未产出 time 块时，从上一轮 turn memory 继承区间并标记 {@link #SOURCE_INHERITED_PREVIOUS}。
     */
    /**
     * Matrix / V2 缺失时：锚定日所在自然月 1 号至锚定日（含），标记 {@link #SOURCE_DEFAULT_MONTH_TO_DATE}。
     */
    public static Result defaultMonthToDateOnAnchor(LocalDate today) {
        LocalDate anchor = today != null ? today : LocalDate.now();
        LocalDate start = anchor.withDayOfMonth(1);
        if (anchor.isBefore(start)) {
            return fail(FAIL_INVALID_DATE_RANGE);
        }
        return new Result(true, null, start, anchor, SOURCE_DEFAULT_MONTH_TO_DATE, null);
    }

    public static Result inheritFromPreviousTurn(AiConversationTurnMemory previousTurn) {
        if (!TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn)) {
            return fail(FAIL_INHERIT_WITHOUT_PREVIOUS);
        }
        try {
            LocalDate sd = LocalDate.parse(previousTurn.getLastStartDate().trim());
            LocalDate ed = LocalDate.parse(previousTurn.getLastEndDate().trim());
            if (ed.isBefore(sd)) {
                return fail(FAIL_INVALID_DATE_RANGE);
            }
            return new Result(true, null, sd, ed, SOURCE_INHERITED_PREVIOUS, null);
        } catch (Exception ex) {
            return fail(FAIL_INHERIT_WITHOUT_PREVIOUS);
        }
    }

    /**
     * V2 已表达时间语义但缺 ISO 起止日时，在 {@link #check} 前补齐（不解析用户自然语言）。
     * <p>典型：LLM 输出 {@code timeType=THIS_MONTH} / {@code timeSource=DEFAULT_MONTH_TO_DATE} 但未填 {@code startDate}/{@code endDate}。
     */
    public static AiQuerySemanticParseResult reconcileTimePartForContract(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            LocalDate today) {
        if (sem == null || sem.isParseMissing()) {
            return sem;
        }
        LocalDate anchor = today != null ? today : LocalDate.now();
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp == null) {
            AiQuerySemanticParseResult.TimePart inferred = inferTimePartWhenMissing(sem, previousTurn, anchor);
            return inferred == null ? sem : sem.toBuilder().time(inferred).build();
        }
        LocalDate sd = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getStartDate());
        LocalDate ed = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getEndDate());
        if (sd != null && ed != null) {
            return sem;
        }
        AiQuerySemanticParseResult.TimePart filled = fillMissingTimeDates(tp, sem, previousTurn, anchor);
        return filled == null ? sem : sem.toBuilder().time(filled).build();
    }

    public static Result check(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            LocalDate today) {
        if (sem == null || sem.isParseMissing()) {
            return fail(FAIL_MISSING_TIME_FIELDS);
        }
        AiQuerySemanticParseResult.TimePart tp = sem.getTime();
        if (tp == null) {
            return fail(FAIL_MISSING_TIME_FIELDS);
        }
        LocalDate sd = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getStartDate());
        LocalDate ed = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getEndDate());
        if (sd == null || ed == null) {
            return fail(FAIL_MISSING_TIME_FIELDS);
        }
        if (ed.isBefore(sd)) {
            return fail(FAIL_INVALID_DATE_RANGE);
        }
        String timeSource = normalizeProductionTimeSource(tp.getTimeSource());
        if (timeSource == null) {
            return fail(FAIL_UNSUPPORTED_TIME_SOURCE);
        }
        String timeAction = normalizeAction(sem.getTimeAction());
        boolean needInherit = Boolean.TRUE.equals(tp.getNeedInheritFromPrevious());
        boolean hasPrevious = TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn);

        if (SOURCE_INHERITED_PREVIOUS.equals(timeSource) && !hasPrevious) {
            return fail(FAIL_INHERIT_WITHOUT_PREVIOUS);
        }
        if (needInherit && !hasPrevious) {
            return fail(FAIL_INHERIT_WITHOUT_PREVIOUS);
        }
        if (SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(timeSource) && needInherit) {
            return fail(FAIL_TIME_SOURCE_CONFLICT);
        }
        if (SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(timeSource)
                && ("INHERIT_PREVIOUS".equals(timeAction) || "INHERIT".equals(timeAction))) {
            return fail(FAIL_TIME_SOURCE_CONFLICT);
        }
        if (SOURCE_INHERITED_PREVIOUS.equals(timeSource)
                && ("NEW".equals(timeAction) || "OVERRIDE".equals(timeAction))
                && !needInherit) {
            return fail(FAIL_TIME_SOURCE_CONFLICT);
        }
        if (hasPrevious && SOURCE_INHERITED_PREVIOUS.equals(timeSource)) {
            try {
                LocalDate ps = LocalDate.parse(previousTurn.getLastStartDate());
                LocalDate pe = LocalDate.parse(previousTurn.getLastEndDate());
                if (!sd.equals(ps) || !ed.equals(pe)) {
                    return fail(FAIL_TIME_SOURCE_CONFLICT);
                }
            } catch (Exception ex) {
                return fail(FAIL_INHERIT_WITHOUT_PREVIOUS);
            }
        }
        LocalDate anchor = today != null ? today : LocalDate.now();
        if (!timeTypeConsistentWithDates(tp.getTimeType(), sd, ed, anchor)) {
            return fail(FAIL_TIME_TYPE_DATE_MISMATCH);
        }
        return new Result(true, null, sd, ed, timeSource, null);
    }

    public static String clarificationQuestion(String failureReason) {
        if (failureReason == null) {
            return null;
        }
        return switch (failureReason) {
            case FAIL_MISSING_TIME_FIELDS, FAIL_INVALID_DATE_RANGE, FAIL_UNSUPPORTED_TIME_SOURCE ->
                    "未能确定统计时间，请说明要查哪段时间（如本月、上季度或起止日期）。";
            case FAIL_INHERIT_WITHOUT_PREVIOUS ->
                    "本句似乎沿用上一轮时间，但对话里没有可继承的统计区间，请重新说明时间。";
            case FAIL_TIME_TYPE_DATE_MISMATCH ->
                    "时间类型与起止日期不一致，请确认要查的时间段。";
            case FAIL_TIME_SOURCE_CONFLICT ->
                    "时间来源理解存在冲突，请重新说明要查的时间范围。";
            default -> "未能确定统计时间，请说明要查哪段时间（如本月、上季度或起止日期）。";
        };
    }

    private static Result fail(String reason) {
        return new Result(false, reason, null, null, null, clarificationQuestion(reason));
    }

    private static AiQuerySemanticParseResult.TimePart inferTimePartWhenMissing(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            LocalDate anchor) {
        String timeAction = normalizeAction(sem.getTimeAction());
        if (("INHERIT_PREVIOUS".equals(timeAction) || "INHERIT".equals(timeAction))
                && TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn)) {
            return AiQuerySemanticParseResult.TimePart.builder()
                    .timeType(AiResolvedTimeWindow.THIS_MONTH)
                    .startDate(previousTurn.getLastStartDate().trim())
                    .endDate(previousTurn.getLastEndDate().trim())
                    .timeSource(SOURCE_INHERITED_PREVIOUS)
                    .needInheritFromPrevious(true)
                    .build();
        }
        return monthToDateTimePart(SOURCE_DEFAULT_MONTH_TO_DATE, anchor);
    }

    private static AiQuerySemanticParseResult.TimePart fillMissingTimeDates(
            AiQuerySemanticParseResult.TimePart tp,
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            LocalDate anchor) {
        String src = normalizeProductionTimeSource(tp.getTimeSource());
        String timeType = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType());
        String timeAction = normalizeAction(sem.getTimeAction());
        boolean needInherit = Boolean.TRUE.equals(tp.getNeedInheritFromPrevious());

        if (SOURCE_INHERITED_PREVIOUS.equals(src)
                || needInherit
                || "INHERIT_PREVIOUS".equals(timeAction)
                || "INHERIT".equals(timeAction)) {
            if (TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn)) {
                return copyTimePart(
                        tp,
                        previousTurn.getLastStartDate().trim(),
                        previousTurn.getLastEndDate().trim(),
                        SOURCE_INHERITED_PREVIOUS,
                        true);
            }
        }
        if (SOURCE_DEFAULT_MONTH_TO_DATE.equals(src)
                || AiResolvedTimeWindow.THIS_MONTH.equals(timeType)
                || "THIS_MONTH_TO_DATE".equals(timeType)
                || "MONTH_TO_DATE".equals(timeType)) {
            return monthToDateTimePart(
                    StringUtils.hasText(src) ? src : SOURCE_DEFAULT_MONTH_TO_DATE, anchor, tp);
        }
        if (SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(src)
                && AiResolvedTimeWindow.THIS_MONTH.equals(timeType)) {
            return monthToDateTimePart(SOURCE_CURRENT_MESSAGE_EXPLICIT, anchor, tp);
        }
        return null;
    }

    private static AiQuerySemanticParseResult.TimePart monthToDateTimePart(
            String timeSource, LocalDate anchor) {
        return monthToDateTimePart(timeSource, anchor, null);
    }

    private static AiQuerySemanticParseResult.TimePart monthToDateTimePart(
            String timeSource, LocalDate anchor, AiQuerySemanticParseResult.TimePart base) {
        LocalDate start = anchor.withDayOfMonth(1);
        String timeType =
                base != null && StringUtils.hasText(base.getTimeType())
                        ? AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(base.getTimeType())
                        : AiResolvedTimeWindow.THIS_MONTH;
        return copyTimePart(
                base, start.toString(), anchor.toString(), timeSource, false, timeType);
    }

    private static AiQuerySemanticParseResult.TimePart copyTimePart(
            AiQuerySemanticParseResult.TimePart base,
            String startDate,
            String endDate,
            String timeSource,
            Boolean needInherit) {
        return copyTimePart(base, startDate, endDate, timeSource, needInherit, null);
    }

    private static AiQuerySemanticParseResult.TimePart copyTimePart(
            AiQuerySemanticParseResult.TimePart base,
            String startDate,
            String endDate,
            String timeSource,
            Boolean needInherit,
            String timeTypeOverride) {
        String timeType =
                StringUtils.hasText(timeTypeOverride)
                        ? timeTypeOverride
                        : base != null ? base.getTimeType() : null;
        if (!StringUtils.hasText(timeType)) {
            timeType = AiResolvedTimeWindow.THIS_MONTH;
        }
        return AiQuerySemanticParseResult.TimePart.builder()
                .timeType(timeType)
                .startDate(startDate)
                .endDate(endDate)
                .timeSource(timeSource)
                .needInheritFromPrevious(needInherit)
                .reason(base != null ? base.getReason() : null)
                .build();
    }

    /** LLM {@code CURRENT_MESSAGE} → 生产 {@code CURRENT_MESSAGE_EXPLICIT}。 */
    public static String normalizeProductionTimeSource(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "CURRENT_MESSAGE", "CURRENT_MESSAGE_EXPLICIT" -> SOURCE_CURRENT_MESSAGE_EXPLICIT;
            case "INHERITED_PREVIOUS" -> SOURCE_INHERITED_PREVIOUS;
            case "DEFAULT_MONTH_TO_DATE" -> SOURCE_DEFAULT_MONTH_TO_DATE;
            default -> null;
        };
    }

    private static String normalizeAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * 校验 LLM 自报的 {@code timeType} 与起止日是否结构一致（日历边界，非用户话术解析）。
     */
    static boolean timeTypeConsistentWithDates(
            String timeTypeRaw, LocalDate start, LocalDate end, LocalDate anchor) {
        if (start == null || end == null || anchor == null) {
            return false;
        }
        String label = AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(timeTypeRaw);
        if (!StringUtils.hasText(label) || AiResolvedTimeWindow.CUSTOM.equals(label)) {
            return true;
        }
        if (AiResolvedTimeWindow.TODAY.equals(label)) {
            return start.equals(anchor) && end.equals(anchor);
        }
        if (AiResolvedTimeWindow.YESTERDAY.equals(label)) {
            LocalDate y = anchor.minusDays(1);
            return start.equals(y) && end.equals(y);
        }
        if (AiResolvedTimeWindow.THIS_MONTH.equals(label)) {
            return start.getDayOfMonth() == 1
                    && YearMonth.from(start).equals(YearMonth.from(end))
                    && YearMonth.from(start).equals(YearMonth.from(anchor))
                    && !end.isAfter(anchor);
        }
        if (AiResolvedTimeWindow.LAST_MONTH.equals(label)) {
            YearMonth lm = YearMonth.from(anchor).minusMonths(1);
            return start.equals(lm.atDay(1)) && end.equals(lm.atEndOfMonth());
        }
        if ("THIS_QUARTER".equals(label)) {
            LocalDate qStart = quarterStart(anchor);
            return start.equals(qStart)
                    && !start.isAfter(end)
                    && quarterOf(start) == quarterOf(anchor)
                    && !end.isAfter(anchor);
        }
        if ("LAST_QUARTER".equals(label)) {
            LocalDate lastQStart = quarterStart(anchor).minusMonths(3);
            LocalDate lastQEnd = quarterStart(anchor).minusDays(1);
            return start.equals(lastQStart) && end.equals(lastQEnd);
        }
        if (AiResolvedTimeWindow.ROLLING_7.equals(label)) {
            return end.equals(anchor) && start.equals(anchor.minusDays(6));
        }
        if (AiResolvedTimeWindow.LAST_YEAR.equals(label)) {
            int y = anchor.getYear() - 1;
            return start.equals(LocalDate.of(y, 1, 1)) && end.equals(LocalDate.of(y, 12, 31));
        }
        return true;
    }

    private static LocalDate quarterStart(LocalDate d) {
        int month = ((d.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(d.getYear(), month, 1);
    }

    private static int quarterOf(LocalDate d) {
        return (d.getMonthValue() - 1) / 3;
    }
}
