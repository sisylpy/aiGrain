package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1：营业额本域矩阵（Harness Engineering 契约表）。
 * <p>职责：矩阵行定义 + wire → planType 白名单（完整问题；ellipsis 由 SemanticIntake 补全）。
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
     * Contract frame light normalize：contract-locked 时委托 ContractFrameLightNormalizer；
     * non-contract-locked 时原样返回 raw，不做任何 slots→wire 推断或 Matrix row 补全。
     */
    public static AiQuerySemanticParseResult canonicalizeRevenueContractFrame(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return ContractFrameLightNormalizer.normalize(raw);
        }
        return raw;
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
