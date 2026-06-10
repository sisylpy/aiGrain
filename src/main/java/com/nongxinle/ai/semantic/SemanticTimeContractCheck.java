package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.inheritance.StructuredTimeFollowUpSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * V2 时间输出合同校验：仅做结构自洽检查与有限缺字段补齐，不解析用户自然语言、不据 {@code timeType} 重算日期。
 * <p>合同通过时采用 LLM 的 {@code startDate}/{@code endDate}/{@code timeSource}；失败时进入 Resolver 澄清。
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
     * Matrix / V2 缺失 time 块时的兜底：锚定日所在自然月 1 号至锚定日（含），标记 {@link #SOURCE_DEFAULT_MONTH_TO_DATE}。
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
     * 在 {@link #check} 前仅补齐缺失的起止日（不据 {@code timeType} 重算、不读 {@code time.reason}）：
     * <ul>
     *   <li>追问无显式新时间且缺日期 → {@link #SOURCE_INHERITED_PREVIOUS}</li>
     *   <li>首轮/无 inherit 信号且缺日期 → {@link #SOURCE_DEFAULT_MONTH_TO_DATE}</li>
     * </ul>
     */
    public static AiQuerySemanticParseResult reconcileTimePartForContract(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            LocalDate today) {
        return reconcileTimePartForContract(sem, previousTurn, today, TimeLayerContextSignals.empty());
    }

    public static AiQuerySemanticParseResult reconcileTimePartForContract(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            LocalDate today,
            TimeLayerContextSignals contextSignals) {
        if (sem == null || sem.isParseMissing()) {
            return sem;
        }
        TimeLayerContextSignals signals =
                contextSignals != null ? contextSignals : TimeLayerContextSignals.empty();
        LocalDate anchor = today != null ? today : LocalDate.now();
        AiQuerySemanticParseResult.TimePart rawTp = sem.getTime();
        AiQuerySemanticParseResult.TimePart reconciledTp;
        if (rawTp == null) {
            reconciledTp = inferTimePartWhenMissing(sem, previousTurn, anchor, signals);
            if (reconciledTp == null) {
                return sem;
            }
        } else {
            reconciledTp = fillMissingTimeDates(rawTp, sem, previousTurn, anchor, signals);
            if (reconciledTp == null) {
                reconciledTp =
                        overrideDefaultMonthToDateForInheritedContextFollowUp(
                                rawTp, sem, previousTurn, signals);
            }
            if (reconciledTp == null) {
                return sem;
            }
        }
        Map<String, Object> trace =
                sem.getContractCompletionTrace() != null
                        ? new LinkedHashMap<>(sem.getContractCompletionTrace())
                        : new LinkedHashMap<>();
        trace.put("timeContractReconcile", buildTimeContractReconcileTrace(rawTp, reconciledTp));
        return sem.toBuilder().time(reconciledTp).contractCompletionTrace(trace).build();
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
            LocalDate anchor,
            TimeLayerContextSignals contextSignals) {
        if (shouldPreferInheritedPreviousTime(sem, null, contextSignals)
                && TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn)) {
            String inheritedLabel = inheritedTimeLabelFromPreviousTurn(previousTurn);
            return AiQuerySemanticParseResult.TimePart.builder()
                    .timeType(inheritedLabel)
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
            LocalDate anchor,
            TimeLayerContextSignals contextSignals) {
        LocalDate sdExisting = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getStartDate());
        LocalDate edExisting = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getEndDate());
        if (sdExisting != null && edExisting != null) {
            return null;
        }
        String src = normalizeProductionTimeSource(tp.getTimeSource());
        String timeAction = normalizeAction(sem.getTimeAction());
        boolean needInherit = Boolean.TRUE.equals(tp.getNeedInheritFromPrevious());
        boolean inheritAction =
                "INHERIT_PREVIOUS".equals(timeAction) || "INHERIT".equals(timeAction);
        boolean hasPrevious = TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn);

        if (SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(src)) {
            return null;
        }
        if ((SOURCE_INHERITED_PREVIOUS.equals(src)
                        || needInherit
                        || inheritAction
                        || shouldPreferInheritedPreviousTime(sem, tp, contextSignals))
                && hasPrevious) {
            return copyTimePart(
                    tp,
                    previousTurn.getLastStartDate().trim(),
                    previousTurn.getLastEndDate().trim(),
                    SOURCE_INHERITED_PREVIOUS,
                    true,
                    inheritedTimeLabelFromPreviousTurn(previousTurn));
        }
        if (inheritAction || needInherit || SOURCE_INHERITED_PREVIOUS.equals(src)) {
            return null;
        }
        if ((SOURCE_DEFAULT_MONTH_TO_DATE.equals(src) || src == null)
                && shouldPreferInheritedPreviousTime(sem, tp, contextSignals)
                && hasPrevious) {
            return copyTimePart(
                    tp,
                    previousTurn.getLastStartDate().trim(),
                    previousTurn.getLastEndDate().trim(),
                    SOURCE_INHERITED_PREVIOUS,
                    true,
                    inheritedTimeLabelFromPreviousTurn(previousTurn));
        }
        if (SOURCE_DEFAULT_MONTH_TO_DATE.equals(src) || src == null) {
            String label =
                    tp != null
                            ? AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(tp.getTimeType())
                            : "";
            if (isExplicitCalendarTimeTypeRequiringLlmDates(label)) {
                return null;
            }
            return monthToDateTimePart(SOURCE_DEFAULT_MONTH_TO_DATE, anchor, tp);
        }
        return null;
    }

    /**
     * 上下文追问且当前句无显式时间时，V2 带齐的 {@link #SOURCE_DEFAULT_MONTH_TO_DATE} 不得覆盖上一轮区间。
     */
    private static AiQuerySemanticParseResult.TimePart overrideDefaultMonthToDateForInheritedContextFollowUp(
            AiQuerySemanticParseResult.TimePart tp,
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            TimeLayerContextSignals contextSignals) {
        if (tp == null || sem == null || !TimeContractPreviousTurnSupport.hasTurnMemoryDates(previousTurn)) {
            return null;
        }
        if (StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(sem)) {
            return null;
        }
        String timeSource = normalizeProductionTimeSource(tp.getTimeSource());
        if (SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(timeSource)) {
            return null;
        }
        if (!shouldPreferInheritedPreviousTime(sem, tp, contextSignals)) {
            return null;
        }
        if (SOURCE_INHERITED_PREVIOUS.equals(timeSource)) {
            return null;
        }
        if (timeSource != null && !SOURCE_DEFAULT_MONTH_TO_DATE.equals(timeSource)) {
            return null;
        }
        LocalDate sdExisting = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getStartDate());
        LocalDate edExisting = AiResolvedTimeWindow.parseIsoDateOrNull(tp.getEndDate());
        if (sdExisting == null || edExisting == null) {
            return null;
        }
        try {
            LocalDate ps = LocalDate.parse(previousTurn.getLastStartDate().trim());
            LocalDate pe = LocalDate.parse(previousTurn.getLastEndDate().trim());
            if (pe.isBefore(ps)) {
                return null;
            }
            String inheritedLabel = inheritedTimeLabelFromPreviousTurn(previousTurn);
            return copyTimePart(
                    tp,
                    ps.toString(),
                    pe.toString(),
                    SOURCE_INHERITED_PREVIOUS,
                    true,
                    inheritedLabel);
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean shouldPreferInheritedPreviousTime(
            AiQuerySemanticParseResult sem,
            AiQuerySemanticParseResult.TimePart tp,
            TimeLayerContextSignals contextSignals) {
        if (sem == null) {
            return false;
        }
        TimeLayerContextSignals signals =
                contextSignals != null ? contextSignals : TimeLayerContextSignals.empty();
        if (signals.suppressPreviousTurnTimeInheritance()) {
            return false;
        }
        if (StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(sem)) {
            return false;
        }
        String timeSource = normalizeProductionTimeSource(tp != null ? tp.getTimeSource() : null);
        if (SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(timeSource)) {
            return false;
        }
        String timeAction = normalizeAction(sem.getTimeAction());
        if ("INHERIT_PREVIOUS".equals(timeAction) || "INHERIT".equals(timeAction)) {
            return true;
        }
        if (tp != null && Boolean.TRUE.equals(tp.getNeedInheritFromPrevious())) {
            return true;
        }
        if (tp != null && SOURCE_INHERITED_PREVIOUS.equals(timeSource)) {
            return true;
        }
        if (Boolean.TRUE.equals(sem.getFollowUp())) {
            return true;
        }
        return signals.contextContinuesFromPreviousTurn();
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
            if (SOURCE_INHERITED_PREVIOUS.equals(timeSource)) {
                timeType = AiResolvedTimeWindow.CUSTOM;
            } else if (SOURCE_DEFAULT_MONTH_TO_DATE.equals(timeSource)) {
                timeType = AiResolvedTimeWindow.THIS_MONTH;
            } else {
                timeType = AiResolvedTimeWindow.CUSTOM;
            }
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

    /**
     * 显式日历 timeType（非 CUSTOM/空/THIS_MONTH 默认语义）：缺起止日时不由 Java 补本月至今，留给 {@link #check} 判
     * {@link #FAIL_MISSING_TIME_FIELDS}。
     */
    static boolean isExplicitCalendarTimeTypeRequiringLlmDates(String normalizedLabel) {
        if (!StringUtils.hasText(normalizedLabel) || AiResolvedTimeWindow.CUSTOM.equals(normalizedLabel)) {
            return false;
        }
        if (AiResolvedTimeWindow.THIS_MONTH.equals(normalizedLabel)) {
            return false;
        }
        return switch (normalizedLabel) {
            case "TODAY",
                    "YESTERDAY",
                    "THIS_WEEK",
                    "LAST_MONTH",
                    "THIS_QUARTER",
                    "LAST_QUARTER",
                    "ROLLING_7",
                    "LAST_YEAR",
                    "LAST_YEAR_SAME_PERIOD",
                    "YEAR_TO_DATE" -> true;
            default -> false;
        };
    }

    private static String inheritedTimeLabelFromPreviousTurn(AiConversationTurnMemory previousTurn) {
        if (previousTurn != null && StringUtils.hasText(previousTurn.getLastTimeLabel())) {
            String label =
                    AiResolvedTimeWindow.normalizeSemanticTimeTypeLabel(previousTurn.getLastTimeLabel());
            if (StringUtils.hasText(label)) {
                return label;
            }
        }
        return AiResolvedTimeWindow.CUSTOM;
    }

    private static Map<String, Object> buildTimeContractReconcileTrace(
            AiQuerySemanticParseResult.TimePart raw, AiQuerySemanticParseResult.TimePart reconciled) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (raw != null) {
            trace.put("rawTimeType", raw.getTimeType());
            trace.put("rawStartDate", raw.getStartDate());
            trace.put("rawEndDate", raw.getEndDate());
            trace.put("rawTimeSource", raw.getTimeSource());
        } else {
            trace.put("rawTimeMissing", true);
        }
        if (reconciled != null) {
            trace.put("reconciledTimeType", reconciled.getTimeType());
            trace.put("reconciledStartDate", reconciled.getStartDate());
            trace.put("reconciledEndDate", reconciled.getEndDate());
            trace.put("reconciledTimeSource", reconciled.getTimeSource());
        }
        trace.put(
                "reconciled",
                raw == null
                        || reconciled == null
                        || !timePartEquivalent(raw, reconciled));
        return trace;
    }

    private static boolean timePartEquivalent(
            AiQuerySemanticParseResult.TimePart a, AiQuerySemanticParseResult.TimePart b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return objectsEqual(a.getTimeType(), b.getTimeType())
                && objectsEqual(a.getStartDate(), b.getStartDate())
                && objectsEqual(a.getEndDate(), b.getEndDate())
                && objectsEqual(a.getTimeSource(), b.getTimeSource())
                && objectsEqual(a.getNeedInheritFromPrevious(), b.getNeedInheritFromPrevious());
    }

    private static boolean objectsEqual(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
