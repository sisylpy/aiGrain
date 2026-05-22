package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 1：库房库存现量本域矩阵（Harness Engineering 契约表）。
 */
@UtilityClass
public final class WarehouseSemanticCapabilityMatrix {


    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";
    public static final String ANCHOR_STRATEGY_STORE = "STORE";

    public static final String STOCK_FACET_OVERVIEW = "OVERVIEW";
    public static final String STOCK_FACET_STORE_RANKING = "STORE_RANKING";
    public static final String STOCK_FACET_GOODS_RANKING_HIGH = "GOODS_RANKING_HIGH";
    public static final String STOCK_FACET_GOODS_RANKING_LOW = "GOODS_RANKING_LOW";
    public static final String STOCK_FACET_LOW_STOCK = "LOW_STOCK";
    public static final String STOCK_FACET_NEAR_EXPIRY = "NEAR_EXPIRY";

    /** 缺货：仅有启发式 lowStockItems，无严格缺货口径 SQL。 */
    public static final String KNOWN_GAP_OUT_OF_STOCK_STRICT_NOT_SUPPORTED =
            "WAREHOUSE_OUT_OF_STOCK_STRICT_NOT_SUPPORTED";

    /** 临期：Tool 无保质期/临期字段专链。 */
    public static final String KNOWN_GAP_NEAR_EXPIRY_NOT_IN_TOOL = "WAREHOUSE_NEAR_EXPIRY_NOT_IN_TOOL";


    public static final WarehouseSemanticCapabilityMatrixRow OVERVIEW =
            firstTurnRow(
                    "WH-A",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    "ALL",
                    "SUMMARY",
                    "STOCK_AMOUNT",
                    STOCK_FACET_OVERVIEW,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow GOODS_AMOUNT_RANKING_HIGH =
            firstTurnRow(
                    "WH-B",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH,
                    "GOODS",
                    "RANKING",
                    "STOCK_AMOUNT",
                    STOCK_FACET_GOODS_RANKING_HIGH,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow GOODS_AMOUNT_RANKING_LOW =
            firstTurnRow(
                    "WH-C",
                    AiQuerySemanticLexicon.STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW,
                    "GOODS",
                    "RANKING",
                    "STOCK_AMOUNT",
                    STOCK_FACET_GOODS_RANKING_LOW,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow STORE_AMOUNT_RANKING =
            firstTurnRow(
                    "WH-D",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_AMOUNT_RANKING,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING,
                    "STORE",
                    "RANKING",
                    "STOCK_AMOUNT",
                    STOCK_FACET_STORE_RANKING,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow SINGLE_STORE_OVERVIEW =
            firstTurnRow(
                    "WH-E",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    "STORE",
                    "SUMMARY",
                    "STOCK_AMOUNT",
                    STOCK_FACET_OVERVIEW,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow OUT_OF_STOCK =
            firstTurnRow(
                    "WH-F",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK,
                    "GOODS",
                    "RISK",
                    "LOW_STOCK",
                    STOCK_FACET_LOW_STOCK,
                    KNOWN_GAP_OUT_OF_STOCK_STRICT_NOT_SUPPORTED);

    public static final WarehouseSemanticCapabilityMatrixRow NEAR_EXPIRY =
            firstTurnRow(
                    "WH-G",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_NEAR_EXPIRY,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    "GOODS",
                    "RISK",
                    "NEAR_EXPIRY",
                    STOCK_FACET_NEAR_EXPIRY,
                    KNOWN_GAP_NEAR_EXPIRY_NOT_IN_TOOL);



    private static final Map<String, WarehouseSemanticCapabilityMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, WarehouseSemanticCapabilityMatrixRow> buildFirstTurnIndex() {
        Map<String, WarehouseSemanticCapabilityMatrixRow> index = new LinkedHashMap<>();
        for (WarehouseSemanticCapabilityMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return index;
    }

    public static List<WarehouseSemanticCapabilityMatrixRow> firstTurnRows() {
        return List.of(
                OVERVIEW,
                GOODS_AMOUNT_RANKING_HIGH,
                GOODS_AMOUNT_RANKING_LOW,
                STORE_AMOUNT_RANKING,
                SINGLE_STORE_OVERVIEW,
                OUT_OF_STOCK,
                NEAR_EXPIRY);
    }


    public static WarehouseSemanticCapabilityMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return canon == null ? null : FIRST_TURN_BY_WIRE.get(canon);
    }

    public static WarehouseSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        return resolveMatrixRow(pathCode, resolvedWire, sem, null);
    }

    public static WarehouseSemanticCapabilityMatrixRow resolveMatrixRow(
            String pathCode,
            String resolvedWire,
            AiQuerySemanticParseResult sem,
            AiResolvedQueryContext rq) {
        if (!AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(pathCode)) {
            return null;
        }
        String canon =
                StringUtils.hasText(resolvedWire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(resolvedWire.trim())
                        : null;
        if (canon == null) {
            return null;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(canon)) {
            return GOODS_AMOUNT_RANKING_HIGH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW.equals(canon)) {
if (isSingleStoreFirstTurnScope(rq)) {
                return SINGLE_STORE_OVERVIEW;
            }
            return OVERVIEW;
        }
        return findFirstTurnRowByWire(canon);
    }

    private static boolean isSingleStoreFirstTurnScope(AiResolvedQueryContext rq) {
        if (rq == null || rq.getOrgScope() == null) {
            return false;
        }
        String st = rq.getOrgScope().getScopeType();
        return AiResolvedOrgScope.SCOPE_STORE.equals(st) || AiResolvedOrgScope.SCOPE_PURCHASER.equals(st);
    }


    public static String knownGapForResolvedRow(WarehouseSemanticCapabilityMatrixRow row) {
        return row == null ? null : row.getKnownGapCode();
    }

    public static boolean detectMatrixWireMissing(
            AiQuerySemanticParseResult sem, String pathCode, String resolvedWire) {
        if (!AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(pathCode)) {
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
        if (StringUtils.hasText(slotWire) && AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(slotWire)) {
            return resolveMatrixRow(pathCode, slotWire, sem, null) == null;
        }
        return false;
    }

    public static boolean isWarehouseRankingWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return AiQuerySemanticLexicon.isStructuredWarehouseStockRankingDetail(canon);
    }

    private static WarehouseSemanticCapabilityMatrixRow firstTurnRow(
            String rowId, String wire, String planType,
            String queryObject, String operation, String metric,
            String stockFacet, String knownGap) {
        return WarehouseSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .stockFacet(stockFacet)
                .structuredIntentDetailWire(wire)
                .targetWarehousePlanType(planType)
                .knownGapCode(knownGap)
                .build();
    }



    public static String targetPlanTypeForWire(String wire) {
        WarehouseSemanticCapabilityMatrixRow row = findFirstTurnRowByWire(wire);
        return row == null ? null : row.getTargetWarehousePlanType();
    }

    /**
     * warehouse_stock_overview_path 下 structured wire 最终口径：semanticSlots + Matrix 优先；
     * {@code stock_reduce_*} 仅 Lexicon compat 映射为现量 overview，不静默回落 overview。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem, String pathCode, String mergedStructuredDetail) {
        if (!AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(pathCode) || sem == null) {
            return null;
        }
        if (AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return null;
        }
        if (AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)) {
            String slotCanon =
                    canonicalWarehouseWireFromRaw(
                            sem.getSemanticSlots().getStructuredIntentDetailWire().trim());
            if (StringUtils.hasText(slotCanon)) {
                return adoptWireViaMatrix(pathCode, slotCanon, sem);
            }
        }
        String fromShape = inferMatrixWireFromSemanticSlots(sem);
        if (StringUtils.hasText(fromShape)) {
            return adoptWireViaMatrix(pathCode, fromShape, sem);
        }
        String mergedCanon =
                StringUtils.hasText(mergedStructuredDetail)
                        ? canonicalWarehouseWireFromRaw(mergedStructuredDetail.trim())
                        : null;
        if (StringUtils.hasText(mergedCanon)) {
            return adoptWireViaMatrix(pathCode, mergedCanon, sem);
        }
        return null;
    }

    private static String canonicalWarehouseWireFromRaw(String raw) {
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
        if (!StringUtils.hasText(canon)) {
            return null;
        }
        if (AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(canon)) {
            return canon;
        }
        return null;
    }

    private static String adoptWireViaMatrix(
            String pathCode, String canonWire, AiQuerySemanticParseResult sem) {
        if (!StringUtils.hasText(canonWire)) {
            return null;
        }
        WarehouseSemanticCapabilityMatrixRow row = resolveMatrixRow(pathCode, canonWire, sem, null);
        return row != null ? row.getStructuredIntentDetailWire() : canonWire;
    }

    /** 仅依据 semanticSlots 形状推断 wire（不读用户原话、不用 metric.rankingType）。 */
    public static String inferMatrixWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String op = normalizeMatrixToken(s.getOperation());
        String qo = normalizeMatrixToken(s.getQueryObject());
        if ("RANKING".equals(op)) {
            if ("GOODS".equals(qo)) {
                String facet = normalizeMatrixToken(s.getSourceFacet());
                if (STOCK_FACET_GOODS_RANKING_LOW.equals(facet)) {
                    return AiQuerySemanticLexicon.STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW;
                }
                if (STOCK_FACET_GOODS_RANKING_HIGH.equals(facet)) {
                    return AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING;
                }
                String metric = normalizeMatrixToken(s.getMetric());
                if (metric != null && (metric.contains("LOW") || metric.contains("MIN"))) {
                    return AiQuerySemanticLexicon.STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW;
                }
                return AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING;
            }
            if ("STORE".equals(qo)) {
                return AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_AMOUNT_RANKING;
            }
        }
        if ("RISK".equals(op) || "LOW_STOCK".equals(normalizeMatrixToken(s.getMetric()))) {
            return AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK;
        }
        if ("SUMMARY".equals(op) || "OVERVIEW".equals(op)) {
            return AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW;
        }
        return null;
    }

    /**
     * Contract observe / slot view：按 Matrix 槽位形状补全 {@code structuredIntentDetailWire} 与 {@code sourceFacet}；
     * 非 alias 归一，不读用户原话。
     */
    public static AiQuerySemanticParseResult canonicalizeWarehouseContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (!AiQuerySemanticLlmMergeHelper.hasExplicitWarehouseRouteSignal(raw)) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        String resolved =
                resolveStructuredIntentDetailWire(
                        raw,
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK,
                        s.getStructuredIntentDetailWire());
        if (!StringUtils.hasText(resolved)) {
            return raw;
        }
        WarehouseSemanticCapabilityMatrixRow row =
                resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK, resolved, raw, null);
        if (row == null) {
            return raw;
        }
        String wire = row.getStructuredIntentDetailWire();
        String facet = row.getStockFacet();
        String slotWireCanon = canonicalWarehouseWireFromRaw(s.getStructuredIntentDetailWire());
        boolean wireNeedsFix = !wire.equals(slotWireCanon);
        boolean facetNeedsFix =
                StringUtils.hasText(facet)
                        && !facet.equals(normalizeMatrixToken(s.getSourceFacet()));
        if (!wireNeedsFix && !facetNeedsFix) {
            return raw;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(facetNeedsFix ? facet : s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return raw.toBuilder().semanticSlots(updated).build();
    }

    private static String normalizeMatrixToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
