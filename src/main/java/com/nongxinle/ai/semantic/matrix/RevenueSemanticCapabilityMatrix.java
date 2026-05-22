package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1：营业额本域矩阵（Harness Engineering 契约表）。
 * <p>职责：矩阵行定义 + wire → planType 白名单（完整问题；ellipsis 由 LlmFollowUpQueryRewriter 补全）。
 * 执行挂载仍在 {@link com.nongxinle.ai.graph.business.DailyRevenueAnswerPlanBuilder} / Tool 专线。
 */
@UtilityClass
public final class RevenueSemanticCapabilityMatrix {


    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";
    public static final String ANCHOR_STRATEGY_STORE = "STORE";
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


    public static final RevenueSemanticCapabilityMatrixRow OVERVIEW =
            firstTurnRow(
                    "RV-A",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW,
                    "ALL",
                    "SUMMARY",
                    "REVENUE_AMOUNT",
                    null);

    public static final RevenueSemanticCapabilityMatrixRow STORE_AMOUNT_RANKING =
            firstTurnRow(
                    "RV-B",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING,
                    "STORE",
                    "RANKING",
                    "REVENUE_AMOUNT",
                    null);

    public static final RevenueSemanticCapabilityMatrixRow SINGLE_STORE_OVERVIEW =
            firstTurnRow(
                    "RV-C",
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW,
                    DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW,
                    "STORE",
                    "SUMMARY",
                    "REVENUE_AMOUNT",
                    null);

    public static final RevenueSemanticCapabilityMatrixRow STORE_COMPARE =
            RevenueSemanticCapabilityMatrixRow.builder()
                    .rowId("RV-D")
                    .queryObject("STORE")
                    .operation("COMPARE")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_COMPARE)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING)
                    .knownGapCode(KNOWN_GAP_STORE_COMPARE_NOT_PAIRWISE)
                    .build();

    /** 契约行 RV-E：与 {@link #OVERVIEW} 同 wire，上轮显式「上月」；矩阵 id 在 Harness 仍标 RV-E。 */
    public static final RevenueSemanticCapabilityMatrixRow OVERVIEW_PREV_MONTH = OVERVIEW;

    /** RV-F：时间追问切上月；继承 scope/域，不继承上一轮时间窗（Harness 禁止 INHERITED_PREVIOUS）。 */


    public static final RevenueSemanticCapabilityMatrixRow PERIOD_COMPARE =
            RevenueSemanticCapabilityMatrixRow.builder()
                    .rowId("RV-H")
                    .queryObject("ALL")
                    .operation("COMPARE")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_PERIOD_COMPARE)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW)
                    .knownGapCode(KNOWN_GAP_PERIOD_COMPARE_NOT_IMPLEMENTED)
                    .build();

    public static final RevenueSemanticCapabilityMatrixRow DAILY_AMOUNT_RANKING =
            RevenueSemanticCapabilityMatrixRow.builder()
                    .rowId("RV-I")
                    .queryObject("DAY")
                    .operation("RANKING")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_DAILY_AMOUNT_RANKING)
                    .knownGapCode(KNOWN_GAP_DAILY_RANKING_CALENDAR_DATE_MISSING)
                    .build();

    public static final RevenueSemanticCapabilityMatrixRow TREND =
            RevenueSemanticCapabilityMatrixRow.builder()
                    .rowId("RV-J")
                    .queryObject("ALL")
                    .operation("TREND")
                    .metric("REVENUE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_REVENUE_TREND)
                    .targetRevenuePlanType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW)
                    .knownGapCode(KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED)
                    .build();

    private static final Map<String, RevenueSemanticCapabilityMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, RevenueSemanticCapabilityMatrixRow> buildFirstTurnIndex() {
        Map<String, RevenueSemanticCapabilityMatrixRow> index = new LinkedHashMap<>();
        for (RevenueSemanticCapabilityMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return index;
    }

    public static List<RevenueSemanticCapabilityMatrixRow> firstTurnRows() {
        return List.of(
                OVERVIEW,
                STORE_AMOUNT_RANKING,
                SINGLE_STORE_OVERVIEW,
                STORE_COMPARE,
                PERIOD_COMPARE,
                DAILY_AMOUNT_RANKING,
                TREND);
    }


    public static RevenueSemanticCapabilityMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return canon == null ? null : FIRST_TURN_BY_WIRE.get(canon);
    }

    public static String targetPlanTypeForWire(String wire) {
        RevenueSemanticCapabilityMatrixRow row = resolveMatrixRow(
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
        RevenueSemanticCapabilityMatrixRow row = resolveMatrixRow(pathCode, corrected, sem, normalizedUserMessage);
        return row != null ? row.getStructuredIntentDetailWire() : corrected;
    }

    /**
     * LLM / merge 常见 revenue wire 别名 → Matrix P1 canonical first-turn wire。
     * @deprecated Historical；P4-C 后 Lexicon 不再调用；strict 下须 LLM 直接输出 registered wire。
     */
    @Deprecated
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
        if (!AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            // 完整问句 wire 纠偏（趋势/环比/上月总览等）；省略追问须先经 LLM Rewrite，不在此识别。
            String fromMsg = inferMatrixWireFromNormalizedQuestion(normalizedUserMessage);
            if (StringUtils.hasText(fromMsg)) {
                return fromMsg;
            }
        }
        return null;
    }

    /**
     * Matrix P1 RV-H/I/J：在 LLM 误标 wire 时，用<strong>完整</strong>归一问句纠正（不识别省略追问如「上个月呢」）。
     * @deprecated Historical — P4-J2 主链 contract selection only from {@code selectedContractId}；P4-J3 删除。
     */
    @Deprecated
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
        if ("STORE".equals(qo) && "RANKING".equals(op)) {
            String metric = normalizeMatrixToken(s.getMetric());
            if (metric != null && metric.contains("REVENUE")) {
                return AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING;
            }
        }
        return null;
    }

    /**
     * Contract observe / slot view：按 Matrix 槽位形状补全 wire / answerPlanType 等；仅依据 semanticSlots 形状，不读用户原文。
     */
    public static AiQuerySemanticParseResult canonicalizeRevenueContractFrame(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (SemanticContractCompletionEngine.hasSelectedContractId(raw)) {
            return ContractFrameLightNormalizer.normalize(raw);
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(raw)) {
            return raw;
        }
        AiQuerySemanticParseResult adjusted = applyRevenueMatrixRowContractCompletion(raw);
        String inferred = inferMatrixWireFromSemanticSlots(adjusted);
        if (!StringUtils.hasText(inferred)) {
            return adjusted;
        }
        RevenueSemanticCapabilityMatrixRow row =
                resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW, inferred, adjusted, null);
        if (row == null || row.getKnownGapCode() != null) {
            return adjusted;
        }
        return mergeRevenueContractRowSlots(adjusted, row);
    }

    /**
     * STORE + RANKING + REVENUE 金额语义 → {@link #STORE_AMOUNT_RANKING} 合同帧（{@code revenue.store_amount_ranking}）。
     */
    private static AiQuerySemanticParseResult applyRevenueMatrixRowContractCompletion(
            AiQuerySemanticParseResult raw) {
        if (!slotsInferStoreAmountRankingShape(raw)) {
            return raw;
        }
        return mergeRevenueContractRowSlots(raw, STORE_AMOUNT_RANKING);
    }

    private static AiQuerySemanticParseResult mergeRevenueContractRowSlots(
            AiQuerySemanticParseResult raw, RevenueSemanticCapabilityMatrixRow row) {
        if (raw == null || raw.getSemanticSlots() == null || row == null) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String wire = row.getStructuredIntentDetailWire();
        String slotWireCanon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        s.getStructuredIntentDetailWire());
        String qo = normalizeMatrixToken(s.getQueryObject());
        String op = normalizeMatrixToken(s.getOperation());
        String met = normalizeMatrixToken(s.getMetric());
        String plan = normalizeMatrixToken(s.getAnswerPlanType());
        boolean needsUpdate =
                !wire.equals(slotWireCanon)
                        || !row.getQueryObject().equals(qo)
                        || !row.getOperation().equals(op)
                        || met == null
                        || !met.contains("REVENUE")
                        || !row.getTargetRevenuePlanType().equals(plan);
        if (!needsUpdate) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(row.getQueryObject())
                        .operation(row.getOperation())
                        .metric(row.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(row.getTargetRevenuePlanType())
                        .build();
        return raw.toBuilder().semanticSlots(updated).build();
    }

    /** 仅 semanticSlots 形状：STORE + RANKING + metric 含 REVENUE（不读用户原话）。 */
    private static boolean slotsInferStoreAmountRankingShape(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        if (!"STORE".equals(normalizeMatrixToken(s.getQueryObject()))) {
            return false;
        }
        if (!"RANKING".equals(normalizeMatrixToken(s.getOperation()))) {
            return false;
        }
        String metric = normalizeMatrixToken(s.getMetric());
        return metric != null && metric.contains("REVENUE");
    }

    /**
     * @deprecated Historical — P4-J2 主链不再用用户原文纠正 wire；P4-J3 删除。
     */
    @Deprecated
    private static String correctMislabeledStoreRankingCanon(
            String canon, AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (!AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(canon)) {
            return canon;
        }
        if (utteranceRequestsStoreRanking(normalizedUserMessage)) {
            return canon;
        }
        // P4-J: canonicalized slot shape already proves STORE+RANKING+REVENUE —
        // do not downgrade to overview when no user-text evidence contradicts it.
        if (!StringUtils.hasText(normalizedUserMessage) && slotsInferStoreAmountRankingShape(sem)) {
            return canon;
        }
        if (isSingleStoreOverviewFromSemantics(sem, normalizedUserMessage)) {
            return AiQuerySemanticLexicon.STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW;
        }
        return AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY;
    }

    /**
     * @deprecated Historical — P4-J2 主链不再读用户原文推断排行；P4-J3 删除。
     */
    @Deprecated
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

    /**
     * @deprecated Historical — P4-J2 主链不再读用户原文推断门店排行；P4-J3 删除。
     */
    @Deprecated
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

    /**
     * @deprecated Historical — P4-J2 主链不再读用户原文推断上月总览；P4-J3 删除。
     */
    @Deprecated
    private static boolean isPreviousMonthOverviewFromMessage(String compactMsg) {
        if (!StringUtils.hasText(compactMsg)) {
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

    public static RevenueSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        return resolveMatrixRow(pathCode, resolvedWire, sem, null);
    }

    public static RevenueSemanticCapabilityMatrixRow resolveMatrixRow(
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
            return STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY.equals(canon)) {
return OVERVIEW;
        }
        RevenueSemanticCapabilityMatrixRow first = findFirstTurnRowByWire(canon);
        if (first != null) {
            return first;
        }
        return null;
    }

    public static String knownGapForResolvedRow(RevenueSemanticCapabilityMatrixRow row) {
        return row == null ? null : row.getKnownGapCode();
    }

    public static boolean detectMatrixWireMissing(
            AiQuerySemanticParseResult sem, String pathCode, String resolvedWire) {
        if (!AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathCode)) {
            return false;
        }
        if (StringUtils.hasText(resolvedWire)) {
            if (resolveMatrixRow(pathCode, resolvedWire, sem, null) != null) {
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
            return resolveMatrixRow(pathCode, slotWire, sem, null) == null;
        }
        return false;
    }

    /**
     * 语义层标记为时间切换（slots.timeAction / metric 等）；省略追问须先经 LLM Rewrite。
     */

    public static String detectPriorCompareOrRankingWireLeak(
            String priorCanonicalWire, String currentCanonicalWire) {
        if (!StringUtils.hasText(priorCanonicalWire) || !StringUtils.hasText(currentCanonicalWire)) {
            return null;
        }
        String prior = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(priorCanonicalWire.trim());
        String cur = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(currentCanonicalWire.trim());
        return null;
    }

    private static RevenueSemanticCapabilityMatrixRow firstTurnRow(
            String rowId, String wire, String planType,
            String queryObject, String operation, String metric, String knownGap) {
        return RevenueSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .structuredIntentDetailWire(wire)
                .targetRevenuePlanType(planType)
                .knownGapCode(knownGap)
                .build();
    }


}
