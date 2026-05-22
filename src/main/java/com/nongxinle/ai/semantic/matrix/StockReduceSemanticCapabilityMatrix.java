package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
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

    /**
     * stock_reduce_query_path 下：slots/merge 已表达排行或子口径，但 canonical wire 无法映射到矩阵行。
     */
    /**
     * stock_reduce_query_path 下 structured wire 最终口径：semanticSlots + Matrix 形状优先；
     * Lexicon 仅做别名归一；无矩阵行时保留 canonical wire，不静默回落为 overview。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem, String pathCode, String mergedStructuredDetail) {
        if (!AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathCode)) {
            return null;
        }
        if (AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String slotCanon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            sem.getSemanticSlots().getStructuredIntentDetailWire().trim());
            return adoptWireViaMatrix(pathCode, slotCanon, sem);
        }
        String fromShape = inferMatrixWireFromSemanticSlots(sem);
        if (StringUtils.hasText(fromShape)) {
            return adoptWireViaMatrix(pathCode, fromShape, sem);
        }
        String fromFacet = inferWireFromStockReduceTypeFacet(sem);
        if (StringUtils.hasText(fromFacet)) {
            return adoptWireViaMatrix(pathCode, fromFacet, sem);
        }
        String mergedCanon =
                StringUtils.hasText(mergedStructuredDetail)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                mergedStructuredDetail.trim())
                        : null;
        if (StringUtils.hasText(mergedCanon)
                && AiQuerySemanticLexicon.isStructuredStockReduceDetail(mergedCanon)) {
            return adoptWireViaMatrix(pathCode, mergedCanon, sem);
        }
        return null;
    }

    private static String adoptWireViaMatrix(
            String pathCode, String canonWire, AiQuerySemanticParseResult sem) {
        if (!StringUtils.hasText(canonWire)) {
            return null;
        }
        StockReduceSemanticCapabilityMatrixRow row = resolveMatrixRow(pathCode, canonWire, sem);
        return row != null ? row.getStructuredIntentDetailWire() : canonWire;
    }

    /**
     * 仅依据 semanticSlots 形状推断 wire（不读用户原话、不用 metric.rankingType）。
     */
    public static String inferMatrixWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String op = normalizeMatrixToken(s.getOperation());
        String qo = normalizeMatrixToken(s.getQueryObject());
        String metric = normalizeMatrixToken(s.getMetric());
        if ("RANKING".equals(op)) {
            if ("GOODS".equals(qo)) {
                if (hasWasteTypeFacet(sem)) {
                    return AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING;
                }
                if (metric != null && metric.contains("COUNT")) {
                    return AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING;
                }
                return AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING;
            }
            if ("STORE".equals(qo) || "BUSINESS".equals(qo)) {
                return AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING;
            }
        }
        if ("COMPARE".equals(op) && ("STORE".equals(qo) || "BUSINESS".equals(qo))) {
            return AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING;
        }
        if ("SUMMARY".equals(op) || "OVERVIEW".equals(op)) {
            String facetWire = inferWireFromStockReduceTypeFacet(sem);
            if (StringUtils.hasText(facetWire)) {
                return facetWire;
            }
            return AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY;
        }
        return null;
    }

    private static String inferWireFromStockReduceTypeFacet(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        String raw = null;
        if (sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getStockReduceType())) {
            raw = sem.getMetric().getStockReduceType().trim();
        }
        if (!StringUtils.hasText(raw) && sem.getSemanticSlots() != null) {
            raw = sem.getSemanticSlots().getMetric();
        }
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
        if (StringUtils.hasText(canon) && AiQuerySemanticLexicon.isStructuredStockReduceDetail(canon)) {
            return canon;
        }
        return null;
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

    public static boolean semanticSlotsIndicateGoodsWasteRanking(
            AiQuerySemanticParseResult sem, String resolvedWire) {
        String wire = resolvedWire;
        if (!StringUtils.hasText(wire) && sem != null && sem.getSemanticSlots() != null) {
            wire =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            sem.getSemanticSlots().getStructuredIntentDetailWire());
        }
        if (!AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire))) {
            return false;
        }
        return hasWasteTypeFacet(sem);
    }

    public static boolean hasWasteTypeFacet(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getStockReduceType())) {
            String raw = sem.getMetric().getStockReduceType().trim().toUpperCase(Locale.ROOT);
            if ("TYPE2".equals(raw) || "WASTE".equals(raw)) {
                return true;
            }
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
            if (AiQuerySemanticLexicon.STRUCTURED_WASTE.equals(canon)) {
                return true;
            }
        }
        if (sem.getSemanticSlots() != null && StringUtils.hasText(sem.getSemanticSlots().getMetric())) {
            String m = sem.getSemanticSlots().getMetric().trim().toUpperCase(Locale.ROOT);
            if (m.contains("WASTE") || "TYPE2".equals(m)) {
                return true;
            }
        }
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
     * Contract observe：按 Matrix 槽位形状补全 wire / 四槽（不读用户原文、不做合同外 wire 别名）。
     */
    public static AiQuerySemanticParseResult canonicalizeStockReduceContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (slotsInferStockReduceOverviewShape(raw)) {
            return mergeStockReduceContractRow(raw, OVERVIEW);
        }
        if (slotsInferGoodsOutboundAmountRankingShape(raw)) {
            return mergeStockReduceContractRow(raw, GOODS_AMOUNT_RANKING);
        }
        if (slotsInferStoreOutboundAmountRankingShape(raw)) {
            return mergeStockReduceContractRow(raw, STORE_AMOUNT_RANKING);
        }
        String inferred = inferMatrixWireFromSemanticSlots(raw);
        if (!StringUtils.hasText(inferred)) {
            return raw;
        }
        StockReduceSemanticCapabilityMatrixRow row = findFirstTurnRowByWire(inferred);
        if (row == null || row.getKnownGapCode() != null) {
            return raw;
        }
        return mergeStockReduceContractRow(raw, row);
    }

    private static AiQuerySemanticParseResult mergeStockReduceContractRow(
            AiQuerySemanticParseResult raw, StockReduceSemanticCapabilityMatrixRow row) {
        if (raw == null || raw.getSemanticSlots() == null || row == null) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String wire = row.getStructuredIntentDetailWire();
        String slotWire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        s.getStructuredIntentDetailWire());
        String qo = row.getQueryObject();
        String op = row.getOperation();
        String metric = row.getMetric();
        String plan = row.getTargetStockReducePlanType();
        boolean needsUpdate =
                !wire.equals(slotWire)
                        || !qo.equals(normalizeMatrixToken(s.getQueryObject()))
                        || !op.equals(normalizeMatrixToken(s.getOperation()))
                        || !metric.equals(normalizeMatrixToken(s.getMetric()))
                        || !plan.equals(normalizeMatrixToken(s.getAnswerPlanType()));
        if (!needsUpdate) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(qo)
                        .operation(op)
                        .metric(metric)
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(plan)
                        .build();
        return raw.toBuilder().semanticSlots(updated).build();
    }

    /** ALL/STORE + SUMMARY/OVERVIEW + 出库金额语义 → {@link #OVERVIEW}（非门店排行 wire）。 */
    private static boolean slotsInferStockReduceOverviewShape(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String op = normalizeMatrixToken(s.getOperation());
        if (!"SUMMARY".equals(op) && !"OVERVIEW".equals(op)) {
            return false;
        }
        String qo = normalizeMatrixToken(s.getQueryObject());
        if ("GOODS".equals(qo)) {
            return false;
        }
        String metric = normalizeMatrixToken(s.getMetric());
        return metric == null || metric.contains("OUTBOUND") || metric.contains("REDUCE");
    }

    private static boolean slotsInferGoodsOutboundAmountRankingShape(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        return "GOODS".equals(normalizeMatrixToken(s.getQueryObject()))
                && "RANKING".equals(normalizeMatrixToken(s.getOperation()))
                && outboundAmountMetric(s.getMetric());
    }

    private static boolean slotsInferStoreOutboundAmountRankingShape(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String qo = normalizeMatrixToken(s.getQueryObject());
        return ("STORE".equals(qo) || "BUSINESS".equals(qo))
                && "RANKING".equals(normalizeMatrixToken(s.getOperation()))
                && outboundAmountMetric(s.getMetric());
    }

    private static boolean outboundAmountMetric(String raw) {
        String metric = normalizeMatrixToken(raw);
        return metric == null || metric.contains("OUTBOUND") || metric.contains("AMOUNT");
    }
}
