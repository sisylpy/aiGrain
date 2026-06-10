package com.nongxinle.ai.semantic.intake.grounding;

import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import com.nongxinle.ai.inventory.CoverDaysSalesBaselinePresentationSupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.InventoryCoverDaysContractSupport;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.frame.ContractLockedSemanticFrame;
import com.nongxinle.ai.semantic.frame.SchemaValidatedSemanticDraft;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cover-days 双时间主权：{@code stockSnapshot.asOfDate} + {@code salesBaselineWindow} 结构化协议。
 * <p>
 * 禁止读 {@code time.reason}、Intake reason marker、rawMessage；禁止用全局 {@code time.timeSource}
 * 或 {@code effectiveTimeWindowSource} 推断销量基线显式。
 */
public final class CoverDaysSalesBaselineTimeSupport {

    static final String TRACE_KEY = "coverDaysSalesBaselineTimeReconcile";

    public static final String SBW_ACTION_DEFAULT = "DEFAULT";
    public static final String SBW_ACTION_EXPLICIT = "EXPLICIT";

    private CoverDaysSalesBaselineTimeSupport() {}

    public record DualTimePlan(
            String stockAsOfDate,
            DishIngredientCoverSalesBaseline baseline,
            String salesBaselineTimeType) {}

    /**
     * Time Contract 之前：补齐/校验结构化双时间字段，并将全局 {@code time}  decouple（防快照 explicit 泄漏）。
     */
    public static AiQuerySemanticParseResult reconcileBeforeTimeContract(
            AiQuerySemanticParseResult sem,
            SemanticIntakeResult intake,
            AiConversationTurnMemory previousTurn,
            LocalDate anchorDate) {
        if (sem == null || sem.isParseMissing()) {
            return sem;
        }
        if (sem.getContractLockedFrame() != null) {
            return sem;
        }
        if (!InventoryCoverDaysContractSupport.parseSelectsInventoryCoverDaysContract(sem)) {
            return sem;
        }
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        SchemaValidatedSemanticDraft normalizedDraft = normalizeDraft(sem.getSemanticDraft(), anchor);
        AiQuerySemanticParseResult.SalesBaselineWindowPart sbw =
                normalizedDraft != null ? normalizedDraft.salesBaselineWindow() : null;
        AiQuerySemanticParseResult rebuilt =
                sem.toBuilder()
                        .semanticDraft(normalizedDraft)
                        .time(buildDecoupledGlobalTimePlaceholder(anchor))
                        .timeAction("NEW")
                        .build();
        return attachTrace(rebuilt, traceMode(sbw));
    }

    /** Completion 前唯一 cover-days 默认入口：只写 Draft，不写 parse flat 字段。 */
    public static AiQuerySemanticParseResult normalizeDraftBeforeCompletion(
            AiQuerySemanticParseResult sem, LocalDate anchorDate) {
        if (sem == null || sem.isParseMissing() || !InventoryCoverDaysContractSupport.parseSelectsInventoryCoverDaysContract(sem)) {
            return sem;
        }
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        SchemaValidatedSemanticDraft normalizedDraft = normalizeDraft(sem.getSemanticDraft(), anchor);
        AiQuerySemanticParseResult.SalesBaselineWindowPart sbw =
                normalizedDraft != null ? normalizedDraft.salesBaselineWindow() : null;
        AiQuerySemanticParseResult rebuilt =
                sem.toBuilder()
                        .semanticDraft(normalizedDraft)
                        .time(buildDecoupledGlobalTimePlaceholder(anchor))
                        .timeAction("NEW")
                        .build();
        if (hasProtocolError(normalizedDraft, "domainExtensions.salesBaselineWindow")
                || hasProtocolError(normalizedDraft, "domainExtensions.stockSnapshot")) {
            rebuilt =
                    rebuilt.toBuilder()
                            .needClarification(true)
                            .reason("cover_days_dual_time_protocol_error")
                            .clarificationQuestion("这个问题的库存快照或销量基线时间结构不完整，请重新说明。")
                            .build();
        }
        return attachTrace(rebuilt, traceMode(sbw));
    }

    /** 从已采纳 parse 投影执行层双时间计划（Resolver → Tool / AnswerPlan）。 */
    public static DualTimePlan resolveDualTimePlan(
            AiQuerySemanticParseResult sem, LocalDate anchorDate) {
        if (sem == null
                || sem.isParseMissing()
                || !InventoryCoverDaysContractSupport.parseSelectsInventoryCoverDaysContract(sem)) {
            return null;
        }
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        AiQuerySemanticParseResult.SalesBaselineWindowPart sbw =
                requireNormalizedSalesBaseline(sem.effectiveSalesBaselineWindow(), anchor);
        AiQuerySemanticParseResult.StockSnapshotPart ss =
                requireNormalizedStockSnapshot(sem.effectiveStockSnapshot(), anchor);
        return toDualTimePlan(sbw, ss, anchor);
    }

    public static DualTimePlan resolveDualTimePlan(ContractLockedSemanticFrame frame, LocalDate anchorDate) {
        if (frame == null || frame.salesBaselineWindow() == null || frame.stockSnapshot() == null) {
            return null;
        }
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        return toDualTimePlan(frame.salesBaselineWindow(), frame.stockSnapshot(), anchor);
    }

    private static DualTimePlan toDualTimePlan(
            AiQuerySemanticParseResult.SalesBaselineWindowPart sbw,
            AiQuerySemanticParseResult.StockSnapshotPart ss,
            LocalDate anchor) {
        DishIngredientCoverSalesBaseline baseline = toExecutionBaseline(sbw, anchor);
        String displayLabel =
                CoverDaysSalesBaselinePresentationSupport.formatSalesBaselineDisplayLabel(null, baseline);
        if (StringUtils.hasText(displayLabel) && displayLabel.startsWith("销量基线：")) {
            displayLabel = displayLabel.substring("销量基线：".length()).trim();
        }
        baseline = baseline.toBuilder().displayLabel(displayLabel).build();
        return new DualTimePlan(ss.getAsOfDate(), baseline, trimToNull(sbw.getTimeType()));
    }

    public static SchemaValidatedSemanticDraft normalizeDraft(
            SchemaValidatedSemanticDraft draft, LocalDate anchorDate) {
        if (draft == null) {
            return null;
        }
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        Map<String, SchemaValidatedSemanticDraft.FieldPresence> presence =
                draft.getPresence() != null ? new LinkedHashMap<>(draft.getPresence()) : new LinkedHashMap<>();
        java.util.List<String> errors =
                draft.getProtocolErrors() != null ? new java.util.ArrayList<>(draft.getProtocolErrors()) : new java.util.ArrayList<>();
        AiQuerySemanticParseResult.SalesBaselineWindowPart sbw =
                normalizeSalesBaselineWindowFromDraft(draft.salesBaselineWindow(), presence, errors, anchor);
        AiQuerySemanticParseResult.StockSnapshotPart ss =
                normalizeStockSnapshotFromDraft(draft.stockSnapshot(), presence, errors, anchor);
        return draft.toBuilder()
                .domainExtensions(
                        SchemaValidatedSemanticDraft.DomainExtensions.builder()
                                .salesBaselineWindow(sbw)
                                .stockSnapshot(ss)
                                .build())
                .presence(presence)
                .protocolErrors(errors)
                .build();
    }

    static boolean isExplicitSalesBaselineAction(AiQuerySemanticParseResult.SalesBaselineWindowPart sbw) {
        if (sbw == null || !StringUtils.hasText(sbw.getAction())) {
            return false;
        }
        return SBW_ACTION_EXPLICIT.equals(normalizeToken(sbw.getAction()));
    }

    private static AiQuerySemanticParseResult.SalesBaselineWindowPart normalizeSalesBaselineWindowFromDraft(
            AiQuerySemanticParseResult.SalesBaselineWindowPart raw,
            Map<String, SchemaValidatedSemanticDraft.FieldPresence> presence,
            java.util.List<String> errors,
            LocalDate anchor) {
        SchemaValidatedSemanticDraft.FieldPresence p = presence.get("domainExtensions.salesBaselineWindow");
        SchemaValidatedSemanticDraft.PresenceState state = p != null ? p.getState() : SchemaValidatedSemanticDraft.PresenceState.MISSING;
        if (SchemaValidatedSemanticDraft.PresenceState.PROTOCOL_ERROR.equals(state)) {
            return raw;
        }
        if (SchemaValidatedSemanticDraft.PresenceState.MISSING.equals(state)) {
            presence.put("domainExtensions.salesBaselineWindow", fieldPresence(SchemaValidatedSemanticDraft.PresenceState.DEFAULTED, Set.of()));
            return defaultSalesBaselineWindow(anchor);
        }
        AiQuerySemanticParseResult.SalesBaselineWindowPart normalized =
                normalizeExplicitSalesBaselineWindow(raw);
        if (normalized == null) {
            String code = "protocol_invalid:salesBaselineWindow:invalid_explicit_window";
            errors.add(code);
            presence.put("domainExtensions.salesBaselineWindow", fieldPresence(SchemaValidatedSemanticDraft.PresenceState.PROTOCOL_ERROR, p != null ? p.getRawLocations() : Set.of(), code));
            return raw;
        }
        return normalized;
    }

    private static AiQuerySemanticParseResult.SalesBaselineWindowPart requireNormalizedSalesBaseline(
            AiQuerySemanticParseResult.SalesBaselineWindowPart raw, LocalDate anchor) {
        AiQuerySemanticParseResult.SalesBaselineWindowPart explicit = normalizeExplicitSalesBaselineWindow(raw);
        return explicit != null ? explicit : defaultSalesBaselineWindow(anchor);
    }

    private static AiQuerySemanticParseResult.SalesBaselineWindowPart normalizeExplicitSalesBaselineWindow(
            AiQuerySemanticParseResult.SalesBaselineWindowPart raw) {
        if (isExplicitSalesBaselineAction(raw)
                && StringUtils.hasText(raw.getStartDate())
                && StringUtils.hasText(raw.getEndDate())) {
            LocalDate start = parseIso(raw.getStartDate());
            LocalDate end = parseIso(raw.getEndDate());
            if (start != null && end != null) {
                if (end.isBefore(start)) {
                    LocalDate tmp = start;
                    start = end;
                    end = tmp;
                }
                int days = (int) Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
                String source =
                        StringUtils.hasText(raw.getSource())
                                ? raw.getSource().trim()
                                : DishIngredientCoverSalesBaseline.SOURCE_USER_EXPLICIT_TIME_WINDOW;
                return AiQuerySemanticParseResult.SalesBaselineWindowPart.builder()
                        .action(SBW_ACTION_EXPLICIT)
                        .source(source)
                        .startDate(start.toString())
                        .endDate(end.toString())
                        .timeType(trimToNull(raw.getTimeType()))
                        .reason(raw.getReason())
                        .build();
            }
        }
        if (raw != null && SBW_ACTION_DEFAULT.equals(normalizeToken(raw.getAction()))
                && StringUtils.hasText(raw.getStartDate())
                && StringUtils.hasText(raw.getEndDate())) {
            return raw;
        }
        return null;
    }

    private static AiQuerySemanticParseResult.SalesBaselineWindowPart defaultSalesBaselineWindow(
            LocalDate anchor) {
        LocalDate end = anchor == null ? LocalDate.now() : anchor;
        LocalDate start = end.minusDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS - 1L);
        return AiQuerySemanticParseResult.SalesBaselineWindowPart.builder()
                .action(SBW_ACTION_DEFAULT)
                .source(DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS)
                .startDate(start.toString())
                .endDate(end.toString())
                .timeType(AiResolvedTimeWindow.ROLLING_7)
                .build();
    }

    private static AiQuerySemanticParseResult.StockSnapshotPart normalizeStockSnapshotFromDraft(
            AiQuerySemanticParseResult.StockSnapshotPart raw,
            Map<String, SchemaValidatedSemanticDraft.FieldPresence> presence,
            java.util.List<String> errors,
            LocalDate anchor) {
        SchemaValidatedSemanticDraft.FieldPresence p = presence.get("domainExtensions.stockSnapshot");
        SchemaValidatedSemanticDraft.PresenceState state = p != null ? p.getState() : SchemaValidatedSemanticDraft.PresenceState.MISSING;
        if (SchemaValidatedSemanticDraft.PresenceState.PROTOCOL_ERROR.equals(state)) {
            return raw;
        }
        if (SchemaValidatedSemanticDraft.PresenceState.MISSING.equals(state)) {
            presence.put("domainExtensions.stockSnapshot", fieldPresence(SchemaValidatedSemanticDraft.PresenceState.DEFAULTED, Set.of()));
            return defaultStockSnapshot(anchor);
        }
        AiQuerySemanticParseResult.StockSnapshotPart normalized = normalizeExplicitStockSnapshot(raw);
        if (normalized == null) {
            String code = "protocol_invalid:stockSnapshot:invalid_asOfDate";
            errors.add(code);
            presence.put("domainExtensions.stockSnapshot", fieldPresence(SchemaValidatedSemanticDraft.PresenceState.PROTOCOL_ERROR, p != null ? p.getRawLocations() : Set.of(), code));
            return raw;
        }
        return normalized;
    }

    private static AiQuerySemanticParseResult.StockSnapshotPart requireNormalizedStockSnapshot(
            AiQuerySemanticParseResult.StockSnapshotPart raw, LocalDate anchor) {
        AiQuerySemanticParseResult.StockSnapshotPart normalized = normalizeExplicitStockSnapshot(raw);
        return normalized != null ? normalized : defaultStockSnapshot(anchor);
    }

    private static AiQuerySemanticParseResult.StockSnapshotPart normalizeExplicitStockSnapshot(
            AiQuerySemanticParseResult.StockSnapshotPart raw) {
        LocalDate asOf = raw != null ? parseIso(raw.getAsOfDate()) : null;
        if (asOf == null) {
            return null;
        }
        return AiQuerySemanticParseResult.StockSnapshotPart.builder()
                .asOfDate(asOf.toString())
                .reason(raw != null ? raw.getReason() : null)
                .build();
    }

    private static AiQuerySemanticParseResult.StockSnapshotPart defaultStockSnapshot(LocalDate anchor) {
        LocalDate asOf = anchor != null ? anchor : LocalDate.now();
        return AiQuerySemanticParseResult.StockSnapshotPart.builder()
                .asOfDate(asOf.toString())
                .build();
    }

    private static DishIngredientCoverSalesBaseline toExecutionBaseline(
            AiQuerySemanticParseResult.SalesBaselineWindowPart sbw, LocalDate anchor) {
        AiQuerySemanticParseResult.SalesBaselineWindowPart normalized =
                requireNormalizedSalesBaseline(sbw, anchor);
        LocalDate start = parseIso(normalized.getStartDate());
        LocalDate end = parseIso(normalized.getEndDate());
        int days =
                (int)
                        Math.max(
                                1,
                                start != null && end != null
                                        ? ChronoUnit.DAYS.between(start, end) + 1
                                        : DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS);
        return DishIngredientCoverSalesBaseline.builder()
                .startDateIso(normalized.getStartDate())
                .stopDateIso(normalized.getEndDate())
                .baselineDays(days)
                .baselineSource(normalized.getSource())
                .build();
    }

    /** Cover-days 全局 time 占位：非 explicit，避免 effectiveTimeWindowSource 被快照误标劫持。 */
    private static AiQuerySemanticParseResult.TimePart buildDecoupledGlobalTimePlaceholder(
            LocalDate anchor) {
        LocalDate end = anchor != null ? anchor : LocalDate.now();
        LocalDate start = end.minusDays(DishIngredientCoverSalesBaseline.DEFAULT_BASELINE_DAYS - 1L);
        return AiQuerySemanticParseResult.TimePart.builder()
                .timeType(AiResolvedTimeWindow.ROLLING_7)
                .startDate(start.toString())
                .endDate(end.toString())
                .timeSource(SemanticTimeContractCheck.SOURCE_DEFAULT_MONTH_TO_DATE)
                .needInheritFromPrevious(false)
                .build();
    }

    private static String traceMode(AiQuerySemanticParseResult.SalesBaselineWindowPart sbw) {
        return isExplicitSalesBaselineAction(sbw) ? "explicit_sales_baseline" : "default_sales_baseline";
    }

    private static LocalDate parseIso(String iso) {
        if (!StringUtils.hasText(iso)) {
            return null;
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeToken(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String trimToNull(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private static AiQuerySemanticParseResult attachTrace(
            AiQuerySemanticParseResult sem, String mode) {
        if (sem == null) {
            return null;
        }
        Map<String, Object> trace =
                sem.getContractCompletionTrace() != null
                        ? new LinkedHashMap<>(sem.getContractCompletionTrace())
                        : new LinkedHashMap<>();
        trace.put(TRACE_KEY, true);
        trace.put(TRACE_KEY + "Mode", mode);
        if (sem.effectiveSalesBaselineWindow() != null) {
            trace.put("salesBaselineAction", sem.effectiveSalesBaselineWindow().getAction());
            trace.put("salesBaselineSource", sem.effectiveSalesBaselineWindow().getSource());
        }
        if (sem.effectiveStockSnapshot() != null) {
            trace.put("stockAsOfDate", sem.effectiveStockSnapshot().getAsOfDate());
        }
        return sem.toBuilder().contractCompletionTrace(trace).build();
    }

    private static boolean hasProtocolError(SchemaValidatedSemanticDraft draft, String fieldPath) {
        if (draft == null || draft.getPresence() == null || fieldPath == null) {
            return false;
        }
        SchemaValidatedSemanticDraft.FieldPresence p = draft.getPresence().get(fieldPath);
        return p != null && SchemaValidatedSemanticDraft.PresenceState.PROTOCOL_ERROR.equals(p.getState());
    }

    private static SchemaValidatedSemanticDraft.FieldPresence fieldPresence(
            SchemaValidatedSemanticDraft.PresenceState state, Set<String> rawLocations) {
        return fieldPresence(state, rawLocations, null);
    }

    private static SchemaValidatedSemanticDraft.FieldPresence fieldPresence(
            SchemaValidatedSemanticDraft.PresenceState state, Set<String> rawLocations, String error) {
        return SchemaValidatedSemanticDraft.FieldPresence.builder()
                .state(state)
                .rawLocations(rawLocations)
                .protocolError(error)
                .build();
    }
}
