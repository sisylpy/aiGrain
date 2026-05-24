package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1：出库/核销本域矩阵（Harness Engineering 契约表）。
 * <p>职责：矩阵行定义 + wire → planType 白名单 + 追问形状（无 NL）。
 * 执行挂载仍在 {@link com.nongxinle.ai.graph.business.StockReduceAnswerPlanBuilder} / Tool 专线。
 */
@UtilityClass
public final class StockReduceSemanticCapabilityMatrix {
    public static final String ROW_KIND_FACET_SWITCH = "FACET_SWITCH";
    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    /**
     * 商品废弃排行：语义要求 TYPE2/WASTE，但 {@code stock_reduce_query} harness 路径 SQL 未按 type 过滤排行。
     */
    public static final String KNOWN_GAP_GOODS_WASTE_TYPE2_SQL_NOT_FILTERED =
            "GOODS_WASTE_RANKING_TYPE2_SQL_NOT_FILTERED";

    private static final Set<String> OVERVIEW_PRIOR_PLAN_TYPES =
            Set.of(
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW);

    public static final StockReduceSemanticCapabilityMatrixRow OVERVIEW =
            firstTurnRow(
                    "SR-A",
                    AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW,
                    "ALL",
                    "SUMMARY",
                    "OUTBOUND_AMOUNT",
                    null);

    public static final StockReduceSemanticCapabilityMatrixRow STORE_AMOUNT_RANKING =
            firstTurnRow(
                    "SR-B",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING,
                    "RANKING",
                    "RANKING",
                    "OUTBOUND_AMOUNT",
                    null);

    public static final StockReduceSemanticCapabilityMatrixRow PRODUCTION_OVERVIEW =
            firstTurnRow(
                    "SR-C",
                    AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW,
                    StockReduceAnswerPlan.REDUCE_TYPE_TYPE1,
                    "SUMMARY",
                    "PRODUCTION_CONSUME",
                    null);

    public static final StockReduceSemanticCapabilityMatrixRow WASTE_OVERVIEW =
            firstTurnRow(
                    "SR-D",
                    AiQuerySemanticLexicon.STRUCTURED_WASTE,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW,
                    StockReduceAnswerPlan.REDUCE_TYPE_TYPE2,
                    "SUMMARY",
                    "WASTE_AMOUNT",
                    null);

    public static final StockReduceSemanticCapabilityMatrixRow LOSS_OVERVIEW =
            firstTurnRow(
                    "SR-E",
                    AiQuerySemanticLexicon.STRUCTURED_LOSS,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW,
                    StockReduceAnswerPlan.REDUCE_TYPE_TYPE3,
                    "SUMMARY",
                    "LOSS_AMOUNT",
                    null);

    public static final StockReduceSemanticCapabilityMatrixRow RETURN_OVERVIEW =
            firstTurnRow(
                    "SR-F",
                    AiQuerySemanticLexicon.STRUCTURED_RETURN,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW,
                    StockReduceAnswerPlan.REDUCE_TYPE_TYPE4,
                    "SUMMARY",
                    "RETURN_AMOUNT",
                    null);

    public static final StockReduceSemanticCapabilityMatrixRow GOODS_AMOUNT_RANKING =
            firstTurnRow(
                    "SR-G",
                    AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING,
                    "RANKING",
                    "RANKING",
                    "OUTBOUND_AMOUNT",
                    null);

    public static final StockReduceSemanticCapabilityMatrixRow GOODS_WASTE_AMOUNT_RANKING =
            StockReduceSemanticCapabilityMatrixRow.builder()
                    .rowId("SR-GW")
                    .queryObject("GOODS")
                    .operation("RANKING")
                    .metric("WASTE_AMOUNT")
                    .structuredIntentDetailWire(AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING)
                    .targetStockReducePlanType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING)
                    .reduceTypeLabel(StockReduceAnswerPlan.REDUCE_TYPE_TYPE2)
                    .knownGapCode(KNOWN_GAP_GOODS_WASTE_TYPE2_SQL_NOT_FILTERED)
                    .rejectPriorRankingWire(true)
                    .build();

    public static final StockReduceSemanticCapabilityMatrixRow FACET_SWITCH_WASTE =
            facetSwitchRow(
                    "SR-I",
                    AiQuerySemanticLexicon.STRUCTURED_WASTE,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW,
                    StockReduceAnswerPlan.REDUCE_TYPE_TYPE2);

    public static final StockReduceSemanticCapabilityMatrixRow FACET_SWITCH_LOSS =
            facetSwitchRow(
                    "SR-J",
                    AiQuerySemanticLexicon.STRUCTURED_LOSS,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW,
                    StockReduceAnswerPlan.REDUCE_TYPE_TYPE3);

    private static final Map<String, StockReduceSemanticCapabilityMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, StockReduceSemanticCapabilityMatrixRow> buildFirstTurnIndex() {
        Map<String, StockReduceSemanticCapabilityMatrixRow> index = new LinkedHashMap<>();
        for (StockReduceSemanticCapabilityMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return index;
    }

    public static List<StockReduceSemanticCapabilityMatrixRow> firstTurnRows() {
        return List.of(
                OVERVIEW,
                STORE_AMOUNT_RANKING,
                PRODUCTION_OVERVIEW,
                WASTE_OVERVIEW,
                LOSS_OVERVIEW,
                RETURN_OVERVIEW,
                GOODS_AMOUNT_RANKING);
    }

    public static List<StockReduceSemanticCapabilityMatrixRow> facetSwitchRows() {
        return List.of(FACET_SWITCH_WASTE, FACET_SWITCH_LOSS);
    }

    public static StockReduceSemanticCapabilityMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return canon == null ? null : FIRST_TURN_BY_WIRE.get(canon);
    }

    public static StockReduceSemanticCapabilityMatrixRow findFirstTurnRowByPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return null;
        }
        String t = planType.trim();
        for (StockReduceSemanticCapabilityMatrixRow row : firstTurnRows()) {
            if (t.equals(row.getTargetStockReducePlanType())) {
                return row;
            }
        }
        return null;
    }

    public static StockReduceSemanticCapabilityMatrixRow findFacetSwitchRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_WASTE.equals(canon)) {
            return FACET_SWITCH_WASTE;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_LOSS.equals(canon)) {
            return FACET_SWITCH_LOSS;
        }
        return null;
    }

    public static String targetPlanTypeForWire(String wire) {
        StockReduceSemanticCapabilityMatrixRow row = findFirstTurnRowByWire(wire);
        if (row != null) {
            return row.getTargetStockReducePlanType();
        }
        StockReduceSemanticCapabilityMatrixRow facet = findFacetSwitchRowByWire(wire);
        return facet == null ? null : facet.getTargetStockReducePlanType();
    }

    /**
     * 按 resolved wire + 语义 facet 解析矩阵行（Builder / Harness summary 共用）。
     */
    public static StockReduceSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        if (!AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathCode)) {
            return null;
        }
        String canon = StringUtils.hasText(resolvedWire)
                ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(resolvedWire.trim())
                : null;
        if (semanticSlotsIndicateGoodsWasteRanking(sem, canon)) {
            return GOODS_WASTE_AMOUNT_RANKING;
        }
        if (canon != null) {
            StockReduceSemanticCapabilityMatrixRow facet = findFacetSwitchRowByWire(canon);
            if (facet != null) {
                return facet;
            }
            StockReduceSemanticCapabilityMatrixRow first = findFirstTurnRowByWire(canon);
            if (first != null) {
                return first;
            }
        }
        return null;
    }

    public static String knownGapForResolvedRow(StockReduceSemanticCapabilityMatrixRow row) {
        return row == null ? null : row.getKnownGapCode();
    }

    private static String normalizeMatrixToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public static boolean detectMatrixWireMissing(
            AiQuerySemanticParseResult sem, String pathCode, String resolvedWire) {
        if (!AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathCode)) {
            return false;
        }
        if (StringUtils.hasText(resolvedWire)) {
            StockReduceSemanticCapabilityMatrixRow row = resolveMatrixRow(pathCode, resolvedWire, sem);
            if (row != null) {
                return false;
            }
        }
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        String slotWire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        sem.getSemanticSlots().getStructuredIntentDetailWire());
        if (StringUtils.hasText(slotWire) && AiQuerySemanticLexicon.isStructuredStockReduceDetail(slotWire)) {
            return resolveMatrixRow(pathCode, slotWire, sem) == null;
        }
        return false;
    }

    public static boolean isStockReduceRankingWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return AiQuerySemanticLexicon.isStockReduceOutboundRankingWire(canon);
    }

    /**
     * 追问切换子口径时，上一轮不得仍为排行 wire（矩阵契约；仅 debug / harness）。
     */
    public static String detectPriorRankingWireLeak(String priorCanonicalWire, String currentCanonicalWire) {
        if (!StringUtils.hasText(priorCanonicalWire) || !StringUtils.hasText(currentCanonicalWire)) {
            return null;
        }
        String prior = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(priorCanonicalWire.trim());
        String cur = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(currentCanonicalWire.trim());
        if (!isStockReduceRankingWire(prior)) {
            return null;
        }
        StockReduceSemanticCapabilityMatrixRow facet = findFacetSwitchRowByWire(cur);
        if (facet != null && facet.isRejectPriorRankingWire()) {
            return "PRIOR_RANKING_WIRE_NOT_CLEARED";
        }
        if (semanticSlotsIndicateGoodsWasteRanking(null, cur) && facet == null) {
            return "PRIOR_RANKING_WIRE_NOT_CLEARED";
        }
        return null;
    }

    /**
     * 商品废弃排行检测：不依赖 metric.stockReduceType / metric.contains，仅校验 canonical wire 身份。
     * 废弃 facet 判断由 contract-locked entry 语义兜底。
     */
    public static boolean semanticSlotsIndicateGoodsWasteRanking(
            AiQuerySemanticParseResult sem, String resolvedWire) {
        if (!StringUtils.hasText(resolvedWire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(resolvedWire.trim());
        if (!AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(canon)) {
            return false;
        }
        // Without metric.stockReduceType / metric.contains, waste-intent cannot be independently 
        // determined from slots. Contract-locked entry semantic is the authoritative source.
        return false;
    }

    private static StockReduceSemanticCapabilityMatrixRow firstTurnRow(
            String rowId,
            String wire,
            String planType,
            String reduceTypeLabel,
            String operation,
            String metric,
            String knownGap) {
        return StockReduceSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(inferQueryObject(operation, wire))
                .operation(operation)
                .metric(metric)
                .structuredIntentDetailWire(wire)
                .targetStockReducePlanType(planType)
                .reduceTypeLabel(reduceTypeLabel)
                .knownGapCode(knownGap)
                .build();
    }

    private static StockReduceSemanticCapabilityMatrixRow facetSwitchRow(
            String rowId, String wire, String planType, String reduceTypeLabel) {
        return StockReduceSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject("ALL")
                .operation("SUMMARY")
                .metric(wire.equals(AiQuerySemanticLexicon.STRUCTURED_WASTE) ? "WASTE_AMOUNT" : "LOSS_AMOUNT")
                .structuredIntentDetailWire(wire)
                .targetStockReducePlanType(planType)
                .reduceTypeLabel(reduceTypeLabel)
                .knownGapCode(null)
                .rejectPriorRankingWire(true)
                .build();
    }

    private static String inferQueryObject(String operation, String wire) {
        if ("RANKING".equals(operation)) {
            if (AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(wire)) {
                return "STORE";
            }
            if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(wire)) {
                return "GOODS";
            }
        }
        return "ALL";
    }

    /**
     * Contract frame canonicalizer：
     * contract-locked → {@link com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer#normalize}；
     * non-contract-locked → 原样返回 raw（不通过 slots/inferMatrixWire/slotsInferXxxShape 补全）。
     */
    public static AiQuerySemanticParseResult canonicalizeStockReduceContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer.normalize(raw);
        }
        return raw;
    }
}
