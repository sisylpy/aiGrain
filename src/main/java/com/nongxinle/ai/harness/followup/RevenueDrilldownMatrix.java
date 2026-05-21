package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1：营业额本域矩阵（Harness Engineering 契约表）。
 * <p>职责：矩阵行定义 + wire → planType 白名单 + 追问形状（无 NL）。
 * 执行挂载仍在 {@link com.nongxinle.ai.graph.business.DailyRevenueAnswerPlanBuilder} / Tool 专线。
 */
@UtilityClass
public final class RevenueDrilldownMatrix {

    public static final String ROW_KIND_FIRST_TURN = "FIRST_TURN";
    public static final String ROW_KIND_TIME_FOLLOWUP = "TIME_FOLLOWUP";
    public static final String ROW_KIND_RANKING_FOLLOWUP = "RANKING_FOLLOWUP";

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    public static final String ANCHOR_STRATEGY_NONE = "NONE";
    public static final String ANCHOR_STRATEGY_STORE = "STORE";
    public static final String ANCHOR_STRATEGY_STORE_PAIR = "STORE_PAIR";

    /** 两店点名对比：Tool/Plan 仅门店排行，无 pairwise compare 专链。 */
    public static final String KNOWN_GAP_STORE_COMPARE_NOT_PAIRWISE =
            "REVENUE_STORE_COMPARE_NOT_PAIRWISE_ONLY_RANKING";

    /** 本月 vs 上月：无独立 period_compare SQL / planType。 */
    public static final String KNOWN_GAP_PERIOD_COMPARE_NOT_IMPLEMENTED =
            "REVENUE_PERIOD_COMPARE_MO_M_NOT_IMPLEMENTED";

    /**
     * 按日峰值：SQL 仅返回区间 max/min，无 argmax 日历日。
     */
    public static final String KNOWN_GAP_DAILY_RANKING_CALENDAR_DATE_MISSING =
            "REVENUE_DAILY_RANKING_ARGMAX_DATE_MISSING";

    /** 趋势：无日序列 / trend planType。 */
    public static final String KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED =
            "REVENUE_TREND_SERIES_NOT_IMPLEMENTED";

    private static final Set<String> OVERVIEW_PRIOR_PLAN_TYPES =
            Set.of(
                    DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_DINE_IN_OVERVIEW,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_TAKEOUT_OVERVIEW);

    public static final RevenueDrilldownMatrixRow OVERVIEW =
            firstTurnRow(
                    "RV-A",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW,
                    "ALL",
                    "SUMMARY",
                    "REVENUE_AMOUNT",
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final RevenueDrilldownMatrixRow STORE_AMOUNT_RANKING =
            firstTurnRow(
                    "RV-B",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING,
                    "STORE",
                    "RANKING",
                    "REVENUE_AMOUNT",
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final RevenueDrilldownMatrixRow SINGLE_STORE_OVERVIEW =
            firstTurnRow(
                    "RV-C",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW,
                    "STORE",
                    "SUMMARY",
                    "REVENUE_AMOUNT",
                    ANCHOR_STRATEGY_STORE,
                    null);

    public static final RevenueDrilldownMatrixRow STORE_COMPARE =
            RevenueDrilldownMatrixRow.builder()
                    .rowId("RV-D")
                    .rowKind(ROW_KIND_FIRST_TURN)
                    .queryObject("STORE")
                    .operation("COMPARE")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_COMPARE)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING)
                    .resultAnchorStrategy(ANCHOR_STRATEGY_STORE_PAIR)
                    .knownGapCode(KNOWN_GAP_STORE_COMPARE_NOT_PAIRWISE)
                    .allowedPriorPlanTypes(Set.of())
                    .rejectPriorCompareOrRankingWire(false)
                    .build();

    /** 契约行 RV-E：与 {@link #OVERVIEW} 同 wire，上轮显式「上月」；矩阵 id 在 Harness 仍标 RV-E。 */
    public static final RevenueDrilldownMatrixRow OVERVIEW_PREV_MONTH = OVERVIEW;

    /** RV-F：时间追问切上月；继承 scope/域，不继承上一轮时间窗（Harness 禁止 INHERITED_PREVIOUS）。 */
    public static final RevenueDrilldownMatrixRow TIME_FOLLOWUP_PREV_MONTH =
            timeFollowupRow(
                    "RV-F",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW);

    public static final RevenueDrilldownMatrixRow RANKING_FOLLOWUP_STORE_TOP =
            rankingFollowupRow(
                    "RV-G",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING);

    public static final RevenueDrilldownMatrixRow PERIOD_COMPARE =
            RevenueDrilldownMatrixRow.builder()
                    .rowId("RV-H")
                    .rowKind(ROW_KIND_FIRST_TURN)
                    .queryObject("ALL")
                    .operation("COMPARE")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_PERIOD_COMPARE)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW)
                    .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                    .knownGapCode(KNOWN_GAP_PERIOD_COMPARE_NOT_IMPLEMENTED)
                    .allowedPriorPlanTypes(Set.of())
                    .rejectPriorCompareOrRankingWire(false)
                    .build();

    public static final RevenueDrilldownMatrixRow DAILY_AMOUNT_RANKING =
            RevenueDrilldownMatrixRow.builder()
                    .rowId("RV-I")
                    .rowKind(ROW_KIND_FIRST_TURN)
                    .queryObject("DAY")
                    .operation("RANKING")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_DAILY_AMOUNT_RANKING)
                    .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                    .knownGapCode(KNOWN_GAP_DAILY_RANKING_CALENDAR_DATE_MISSING)
                    .allowedPriorPlanTypes(Set.of())
                    .rejectPriorCompareOrRankingWire(false)
                    .build();

    public static final RevenueDrilldownMatrixRow TREND =
            RevenueDrilldownMatrixRow.builder()
                    .rowId("RV-J")
                    .rowKind(ROW_KIND_FIRST_TURN)
                    .queryObject("ALL")
                    .operation("TREND")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_TREND)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW)
                    .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                    .knownGapCode(KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED)
                    .allowedPriorPlanTypes(Set.of())
                    .rejectPriorCompareOrRankingWire(false)
                    .build();

    private static final Map<String, RevenueDrilldownMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, RevenueDrilldownMatrixRow> buildFirstTurnIndex() {
        Map<String, RevenueDrilldownMatrixRow> index = new LinkedHashMap<>();
        for (RevenueDrilldownMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return index;
    }

    public static List<RevenueDrilldownMatrixRow> firstTurnRows() {
        return List.of(
                OVERVIEW,
                STORE_AMOUNT_RANKING,
                SINGLE_STORE_OVERVIEW,
                STORE_COMPARE,
                PERIOD_COMPARE,
                DAILY_AMOUNT_RANKING,
                TREND);
    }

    public static List<RevenueDrilldownMatrixRow> followUpRows() {
        return List.of(TIME_FOLLOWUP_PREV_MONTH, RANKING_FOLLOWUP_STORE_TOP);
    }

    public static RevenueDrilldownMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return canon == null ? null : FIRST_TURN_BY_WIRE.get(canon);
    }

    public static RevenueDrilldownMatrixRow findTimeFollowupRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY.equals(canon)) {
            return TIME_FOLLOWUP_PREV_MONTH;
        }
        return null;
    }

    public static RevenueDrilldownMatrixRow findRankingFollowupRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(canon)) {
            return RANKING_FOLLOWUP_STORE_TOP;
        }
        return null;
    }

    public static String targetPlanTypeForWire(String wire) {
        RevenueDrilldownMatrixRow row = resolveMatrixRow(
                AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW, wire, null);
        return row == null ? null : row.getTargetRevenuePlanType();
    }

    /**
     * revenue_overview_path 下 structured wire 最终口径：Matrix 问句/槽位形状优先于 LLM 误标的门店排行 wire。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem,
            String pathCode,
            String mergedStructuredDetail,
            String normalizedUserMessage) {
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathCode)) {
            return null;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return null;
        }
        if (AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String slotCanon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            sem.getSemanticSlots().getStructuredIntentDetailWire().trim());
            return adoptWireViaMatrix(pathCode, slotCanon, sem, normalizedUserMessage);
        }
        String fromShape = inferMatrixWireFromSemantics(sem, normalizedUserMessage);
        if (StringUtils.hasText(fromShape)) {
            return adoptWireViaMatrix(pathCode, fromShape, sem, normalizedUserMessage);
        }
        String mergedCanon =
                StringUtils.hasText(mergedStructuredDetail)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                mergedStructuredDetail.trim())
                        : null;
        if (StringUtils.hasText(mergedCanon)
                && AiQuerySemanticLexicon.isStructuredRevenueDetail(mergedCanon)) {
            return adoptWireViaMatrix(pathCode, mergedCanon, sem, normalizedUserMessage);
        }
        return null;
    }

    private static String adoptWireViaMatrix(
            String pathCode,
            String canonWire,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (!StringUtils.hasText(canonWire)) {
            return null;
        }
        String msgForCorrection =
                AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)
                        ? ""
                        : normalizedUserMessage;
        String corrected = correctMislabeledStoreRankingCanon(canonWire, sem, msgForCorrection);
        RevenueDrilldownMatrixRow row = resolveMatrixRow(pathCode, corrected, sem, normalizedUserMessage);
        return row != null ? row.getStructuredIntentDetailWire() : corrected;
    }

    /**
     * LLM / merge 常见 revenue wire 别名 → Matrix P1 canonical first-turn wire。
     */
    public static String canonicalWireSupplement(String snakeWire) {
        if (!StringUtils.hasText(snakeWire)) {
            return null;
        }
        return switch (snakeWire.trim().toLowerCase(Locale.ROOT)) {
            case "revenue_period_comparison",
                    "revenue_mom_compare",
                    "revenue_month_compare",
                    "revenue_month_over_month",
                    "revenue_period_over_period" -> AiQuerySemanticLexicon.STRUCTURED_REVENUE_PERIOD_COMPARE;
            case "revenue_daily_peak",
                    "revenue_day_amount_ranking",
                    "revenue_daily_peak_ranking",
                    "revenue_highest_day" -> AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING;
            case "revenue_trend_series",
                    "revenue_amount_trend",
                    "revenue_time_series" -> AiQuerySemanticLexicon.STRUCTURED_REVENUE_TREND;
            default -> null;
        };
    }

    private static String inferMatrixWireFromSemantics(
            AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        String fromSlots = inferMatrixWireFromSemanticSlots(sem);
        if (StringUtils.hasText(fromSlots)) {
            return fromSlots;
        }
        if (isTimeFollowupShape(sem, null)
                && !AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY;
        }
        if (!AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String fromRanking = inferWireFromMetricRankingTypeCompat(sem);
            if (StringUtils.hasText(fromRanking)) {
                return fromRanking;
            }
            String fromMsg = inferMatrixWireFromNormalizedQuestion(normalizedUserMessage);
            if (StringUtils.hasText(fromMsg)) {
                return fromMsg;
            }
            if (isTimeFollowupFromMessage(normalizedUserMessage)) {
                return AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY;
            }
        }
        return null;
    }

    /** compat/debug：slots 无 canonical wire 时，才用 {@code metric.rankingType} 推断。 */
    private static String inferWireFromMetricRankingTypeCompat(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return null;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return null;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitBusinessOverviewRouteSignal(sem)
                || AiQuerySemanticLlmMergeHelper.hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return null;
        }
        String rt = sem.getMetric().getRankingType();
        if (!StringUtils.hasText(rt)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rt.trim());
        return AiQuerySemanticLexicon.isStructuredRevenueDetail(canon) ? canon : null;
    }

    /**
     * Matrix P1 RV-H/I/J：在 LLM 误标 {@code revenue_store_amount_ranking} 时，用已归一问句纠正 wire（不扩门店排行）。
     */
    private static String inferMatrixWireFromNormalizedQuestion(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return null;
        }
        String msg = normalizedUserMessage.replace(" ", "").replace("\u3000", "").trim();
        if (msg.contains("趋势")) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_TREND;
        }
        if ((msg.contains("哪天") || msg.contains("哪一天") || msg.contains("哪一日"))
                && !msg.contains("门店")) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING;
        }
        if (msg.contains("环比")
                || msg.contains("比上月")
                || msg.contains("较上月")
                || msg.contains("和上月比")
                || msg.contains("与上月比")
                || (msg.contains("本月") && msg.contains("上月") && (msg.contains("比") || msg.contains("对比")))) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_PERIOD_COMPARE;
        }
        if (isPreviousMonthOverviewFromMessage(msg)) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY;
        }
        return null;
    }

    private static String inferMatrixWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String op = normalizeMatrixToken(s.getOperation());
        String qo = normalizeMatrixToken(s.getQueryObject());
        if ("TREND".equals(op)) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_TREND;
        }
        if ("COMPARE".equals(op)) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_PERIOD_COMPARE;
        }
        if ("DAY".equals(qo) && "RANKING".equals(op)) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING;
        }
        if ("STORE".equals(qo) && ("SUMMARY".equals(op) || "DETAIL".equals(op))) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW;
        }
        return null;
    }

    private static String correctMislabeledStoreRankingCanon(
            String canon, AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (!AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(canon)) {
            return canon;
        }
        if (isRankingFollowupShape(sem, normalizedUserMessage)) {
            return canon;
        }
        if (utteranceRequestsStoreRanking(normalizedUserMessage)) {
            return canon;
        }
        if (isSingleStoreOverviewFromSemantics(sem, normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW;
        }
        return AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY;
    }

    private static boolean isSingleStoreOverviewFromSemantics(
            AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (utteranceRequestsStoreRanking(normalizedUserMessage)) {
            return false;
        }
        if (sem != null && sem.effectiveMentionedStoreNames().size() == 1) {
            return true;
        }
        if (sem != null && sem.getSemanticSlots() != null) {
            String qo = normalizeMatrixToken(sem.getSemanticSlots().getQueryObject());
            String op = normalizeMatrixToken(sem.getSemanticSlots().getOperation());
            if ("STORE".equals(qo) && ("SUMMARY".equals(op) || "DETAIL".equals(op))) {
                return true;
            }
        }
        String msg = compactMessage(normalizedUserMessage);
        if (!msg.contains("门店")) {
            return false;
        }
        if (msg.contains("哪个") || msg.contains("哪一家") || msg.contains("最高")) {
            return false;
        }
        return msg.contains("多少") || msg.contains("营业额") || msg.contains("营收");
    }

    private static boolean utteranceRequestsStoreRanking(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        if (msg.contains("那哪个门店") || msg.contains("那哪个店") || msg.contains("那哪门店")) {
            return true;
        }
        if ((msg.contains("哪个门店") || msg.contains("哪门店") || msg.contains("哪一家门店"))
                && (msg.contains("最高") || msg.contains("排行") || msg.contains("营业额最高"))) {
            return true;
        }
        return msg.contains("门店营业额最高") || msg.contains("门店最高");
    }

    private static boolean isPreviousMonthOverviewFromMessage(String compactMsg) {
        if (!StringUtils.hasText(compactMsg)) {
            return false;
        }
        if (isTimeFollowupFromMessage(compactMsg)) {
            return false;
        }
        if (!compactMsg.contains("上个月") && !compactMsg.contains("上月")) {
            return false;
        }
        if (compactMsg.contains("比")
                && (compactMsg.contains("本月") || compactMsg.contains("对比") || compactMsg.contains("环比"))) {
            return false;
        }
        if (utteranceRequestsStoreRanking(compactMsg)) {
            return false;
        }
        return compactMsg.contains("营业额")
                || compactMsg.contains("营收")
                || compactMsg.contains("多少");
    }

    private static boolean isTimeFollowupFromMessage(String normalizedUserMessage) {
        String msg = compactMessage(normalizedUserMessage);
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        return msg.contains("那上个月")
                || msg.contains("那上月")
                || msg.contains("上个月呢")
                || msg.contains("上月呢");
    }

    private static String compactMessage(String normalizedUserMessage) {
        if (!StringUtils.hasText(normalizedUserMessage)) {
            return "";
        }
        return normalizedUserMessage.replace(" ", "").replace("\u3000", "").trim();
    }

    private static String normalizeMatrixToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public static RevenueDrilldownMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        return resolveMatrixRow(pathCode, resolvedWire, sem, null);
    }

    public static RevenueDrilldownMatrixRow resolveMatrixRow(
            String pathCode,
            String resolvedWire,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathCode)) {
            return null;
        }
        String canon =
                StringUtils.hasText(resolvedWire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(resolvedWire.trim())
                        : null;
        if (canon == null) {
            return null;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(canon)) {
            return isRankingFollowupShape(sem, normalizedUserMessage)
                    ? RANKING_FOLLOWUP_STORE_TOP
                    : STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY.equals(canon)) {
            if (isTimeFollowupShape(sem, normalizedUserMessage)) {
                return TIME_FOLLOWUP_PREV_MONTH;
            }
            return OVERVIEW;
        }
        RevenueDrilldownMatrixRow first = findFirstTurnRowByWire(canon);
        if (first != null) {
            return first;
        }
        return null;
    }

    /** 「那哪个门店最高？」：继承时间、切门店排行 wire。 */
    public static boolean isRankingFollowupShape(AiQuerySemanticParseResult sem) {
        return isRankingFollowupShape(sem, null);
    }

    private static boolean isRankingFollowupShape(
            AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (sem == null) {
            return false;
        }
        if (Boolean.TRUE.equals(sem.getFollowUp())) {
            if (isTimeFollowupFromMessage(normalizedUserMessage)) {
                return false;
            }
            String wire =
                    sem.getSemanticSlots() != null
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                    sem.getSemanticSlots().getStructuredIntentDetailWire())
                            : null;
            if (AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(wire)) {
                if (StringUtils.hasText(normalizedUserMessage)) {
                    return utteranceRequestsStoreRanking(normalizedUserMessage);
                }
                return true;
            }
        }
        if (sem.getSemanticSlots() != null && StringUtils.hasText(sem.getSemanticSlots().getOperation())) {
            String op = sem.getSemanticSlots().getOperation().trim().toUpperCase();
            if (op.contains("RANKING") && op.contains("FOLLOW")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 语义层标记为时间追问（如「那上个月呢」）：slots.operation=TIME_SHIFT 或 metric 含 PREV_MONTH。
     */
    public static boolean isTimeFollowupShape(AiQuerySemanticParseResult sem) {
        return isTimeFollowupShape(sem, null);
    }

    private static boolean isTimeFollowupShape(
            AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (sem == null) {
            return false;
        }
        if (isTimeFollowupFromMessage(normalizedUserMessage)) {
            return true;
        }
        if (Boolean.TRUE.equals(sem.getFollowUp())) {
            String wire =
                    sem.getSemanticSlots() != null
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                    sem.getSemanticSlots().getStructuredIntentDetailWire())
                            : null;
            if (AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY.equals(wire)) {
                return true;
            }
            if (sem.getSemanticSlots() != null) {
                String op = normalizeMatrixToken(sem.getSemanticSlots().getOperation());
                if ("TIME".equals(op) || "TIME_SHIFT".equals(op)) {
                    return true;
                }
            }
        }
        if (sem.getSemanticSlots() == null) {
            return false;
        }
        String op = sem.getSemanticSlots().getOperation();
        if (StringUtils.hasText(op) && op.trim().toUpperCase().contains("TIME")) {
            return true;
        }
        String metric = sem.getSemanticSlots().getMetric();
        if (StringUtils.hasText(metric)) {
            String m = metric.trim().toUpperCase();
            if (m.contains("PREV") || m.contains("LAST_MONTH") || m.contains("上月")) {
                return true;
            }
        }
        return false;
    }

    public static String knownGapForResolvedRow(RevenueDrilldownMatrixRow row) {
        return row == null ? null : row.getKnownGapCode();
    }

    public static boolean detectMatrixWireMissing(
            AiQuerySemanticParseResult sem, String pathCode, String resolvedWire) {
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathCode)) {
            return false;
        }
        if (StringUtils.hasText(resolvedWire)) {
            if (resolveMatrixRow(pathCode, resolvedWire, sem) != null) {
                return false;
            }
        }
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        String slotWire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        sem.getSemanticSlots().getStructuredIntentDetailWire());
        if (StringUtils.hasText(slotWire) && AiQuerySemanticLexicon.isStructuredRevenueDetail(slotWire)) {
            return resolveMatrixRow(pathCode, slotWire, sem) == null;
        }
        return false;
    }

    public static boolean isRevenueRankingWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING.equals(canon);
    }

    public static boolean isRevenueCompareWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_COMPARE.equals(canon)
                || AiQuerySemanticLexicon.STRUCTURED_REVENUE_PERIOD_COMPARE.equals(canon);
    }

    public static String detectPriorCompareOrRankingWireLeak(
            String priorCanonicalWire, String currentCanonicalWire) {
        if (!StringUtils.hasText(priorCanonicalWire) || !StringUtils.hasText(currentCanonicalWire)) {
            return null;
        }
        String prior = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(priorCanonicalWire.trim());
        String cur = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(currentCanonicalWire.trim());
        if (!isRevenueCompareWire(prior) && !isRevenueRankingWire(prior)) {
            return null;
        }
        RevenueDrilldownMatrixRow fu = findRankingFollowupRowByWire(cur);
        if (fu != null && fu.isRejectPriorCompareOrRankingWire()) {
            return "PRIOR_COMPARE_OR_RANKING_WIRE_NOT_CLEARED";
        }
        RevenueDrilldownMatrixRow timeFu = findTimeFollowupRowByWire(cur);
        if (timeFu != null && timeFu.isRejectPriorCompareOrRankingWire()) {
            return "PRIOR_COMPARE_OR_RANKING_WIRE_NOT_CLEARED";
        }
        return null;
    }

    private static RevenueDrilldownMatrixRow firstTurnRow(
            String rowId,
            String wire,
            String planType,
            String queryObject,
            String operation,
            String metric,
            String anchorStrategy,
            String knownGap) {
        return RevenueDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_FIRST_TURN)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .structuredIntentDetailWire(wire)
                .targetRevenuePlanType(planType)
                .resultAnchorStrategy(anchorStrategy)
                .knownGapCode(knownGap)
                .allowedPriorPlanTypes(Set.of())
                .rejectPriorCompareOrRankingWire(false)
                .build();
    }

    private static RevenueDrilldownMatrixRow timeFollowupRow(String rowId, String wire, String planType) {
        return RevenueDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_TIME_FOLLOWUP)
                .queryObject("ALL")
                .operation("TIME_SHIFT")
                .metric("REVENUE_AMOUNT")
                .structuredIntentDetailWire(wire)
                .targetRevenuePlanType(planType)
                .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                .knownGapCode(null)
                .allowedPriorPlanTypes(OVERVIEW_PRIOR_PLAN_TYPES)
                .rejectPriorCompareOrRankingWire(true)
                .build();
    }

    private static RevenueDrilldownMatrixRow rankingFollowupRow(String rowId, String wire, String planType) {
        return RevenueDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_RANKING_FOLLOWUP)
                .queryObject("STORE")
                .operation("RANKING")
                .metric("REVENUE_AMOUNT")
                .structuredIntentDetailWire(wire)
                .targetRevenuePlanType(planType)
                .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                .knownGapCode(null)
                .allowedPriorPlanTypes(OVERVIEW_PRIOR_PLAN_TYPES)
                .rejectPriorCompareOrRankingWire(true)
                .build();
    }
}
