package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
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
public final class WarehouseDrilldownMatrix {

    public static final String ROW_KIND_FIRST_TURN = "FIRST_TURN";
    public static final String ROW_KIND_GOODS_RANKING_FOLLOWUP = "GOODS_RANKING_FOLLOWUP";
    public static final String ROW_KIND_STORE_FOLLOWUP = "STORE_FOLLOWUP";

    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    public static final String ANCHOR_STRATEGY_NONE = "NONE";
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

    private static final Set<String> OVERVIEW_PRIOR_PLAN_TYPES =
            Set.of(
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK);

    public static final WarehouseDrilldownMatrixRow OVERVIEW =
            firstTurnRow(
                    "WH-A",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    "ALL",
                    "SUMMARY",
                    "STOCK_AMOUNT",
                    STOCK_FACET_OVERVIEW,
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final WarehouseDrilldownMatrixRow GOODS_AMOUNT_RANKING_HIGH =
            firstTurnRow(
                    "WH-B",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH,
                    "GOODS",
                    "RANKING",
                    "STOCK_AMOUNT",
                    STOCK_FACET_GOODS_RANKING_HIGH,
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final WarehouseDrilldownMatrixRow GOODS_AMOUNT_RANKING_LOW =
            firstTurnRow(
                    "WH-C",
                    AiQuerySemanticLexicon.STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW,
                    "GOODS",
                    "RANKING",
                    "STOCK_AMOUNT",
                    STOCK_FACET_GOODS_RANKING_LOW,
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final WarehouseDrilldownMatrixRow STORE_AMOUNT_RANKING =
            firstTurnRow(
                    "WH-D",
                    AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_AMOUNT_RANKING,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING,
                    "STORE",
                    "RANKING",
                    "STOCK_AMOUNT",
                    STOCK_FACET_STORE_RANKING,
                    ANCHOR_STRATEGY_NONE,
                    null);

    public static final WarehouseDrilldownMatrixRow SINGLE_STORE_OVERVIEW =
            firstTurnRow(
                    "WH-E",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    "STORE",
                    "SUMMARY",
                    "STOCK_AMOUNT",
                    STOCK_FACET_OVERVIEW,
                    ANCHOR_STRATEGY_STORE,
                    null);

    public static final WarehouseDrilldownMatrixRow OUT_OF_STOCK =
            firstTurnRow(
                    "WH-F",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK,
                    "GOODS",
                    "RISK",
                    "LOW_STOCK",
                    STOCK_FACET_LOW_STOCK,
                    ANCHOR_STRATEGY_NONE,
                    KNOWN_GAP_OUT_OF_STOCK_STRICT_NOT_SUPPORTED);

    public static final WarehouseDrilldownMatrixRow NEAR_EXPIRY =
            firstTurnRow(
                    "WH-G",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_NEAR_EXPIRY,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    "GOODS",
                    "RISK",
                    "NEAR_EXPIRY",
                    STOCK_FACET_NEAR_EXPIRY,
                    ANCHOR_STRATEGY_NONE,
                    KNOWN_GAP_NEAR_EXPIRY_NOT_IN_TOOL);

    public static final WarehouseDrilldownMatrixRow GOODS_RANKING_FOLLOWUP_HIGH =
            goodsRankingFollowupRow(
                    "WH-H",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH);

    public static final WarehouseDrilldownMatrixRow STORE_FOLLOWUP_AAA =
            storeFollowupRow(
                    "WH-I",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW);

    private static final Map<String, WarehouseDrilldownMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, WarehouseDrilldownMatrixRow> buildFirstTurnIndex() {
        Map<String, WarehouseDrilldownMatrixRow> index = new LinkedHashMap<>();
        for (WarehouseDrilldownMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return index;
    }

    public static List<WarehouseDrilldownMatrixRow> firstTurnRows() {
        return List.of(
                OVERVIEW,
                GOODS_AMOUNT_RANKING_HIGH,
                GOODS_AMOUNT_RANKING_LOW,
                STORE_AMOUNT_RANKING,
                SINGLE_STORE_OVERVIEW,
                OUT_OF_STOCK,
                NEAR_EXPIRY);
    }

    public static List<WarehouseDrilldownMatrixRow> followUpRows() {
        return List.of(GOODS_RANKING_FOLLOWUP_HIGH, STORE_FOLLOWUP_AAA);
    }

    public static WarehouseDrilldownMatrixRow findFirstTurnRowByWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return canon == null ? null : FIRST_TURN_BY_WIRE.get(canon);
    }

    public static WarehouseDrilldownMatrixRow resolveMatrixRow(
            String pathCode, String resolvedWire, AiQuerySemanticParseResult sem) {
        return resolveMatrixRow(pathCode, resolvedWire, sem, null);
    }

    public static WarehouseDrilldownMatrixRow resolveMatrixRow(
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
            return isGoodsRankingFollowupShape(sem) ? GOODS_RANKING_FOLLOWUP_HIGH : GOODS_AMOUNT_RANKING_HIGH;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW.equals(canon)) {
            if (isStoreFollowupShape(sem)) {
                return STORE_FOLLOWUP_AAA;
            }
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

    public static boolean isGoodsRankingFollowupShape(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        if (Boolean.TRUE.equals(sem.getFollowUp())) {
            String wire =
                    sem.getSemanticSlots() != null
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                    sem.getSemanticSlots().getStructuredIntentDetailWire())
                            : null;
            if (AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(wire)) {
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

    public static boolean isStoreFollowupShape(AiQuerySemanticParseResult sem) {
        if (sem == null || !Boolean.TRUE.equals(sem.getFollowUp())) {
            return false;
        }
        String wire =
                sem.getSemanticSlots() != null
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                sem.getSemanticSlots().getStructuredIntentDetailWire())
                        : null;
        return AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW.equals(wire);
    }

    public static String knownGapForResolvedRow(WarehouseDrilldownMatrixRow row) {
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

    private static WarehouseDrilldownMatrixRow firstTurnRow(
            String rowId,
            String wire,
            String planType,
            String queryObject,
            String operation,
            String metric,
            String stockFacet,
            String anchorStrategy,
            String knownGap) {
        return WarehouseDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_FIRST_TURN)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .stockFacet(stockFacet)
                .structuredIntentDetailWire(wire)
                .targetWarehousePlanType(planType)
                .resultAnchorStrategy(anchorStrategy)
                .knownGapCode(knownGap)
                .allowedPriorPlanTypes(Set.of())
                .rejectPriorRankingWire(false)
                .build();
    }

    private static WarehouseDrilldownMatrixRow goodsRankingFollowupRow(
            String rowId, String wire, String planType) {
        return WarehouseDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_GOODS_RANKING_FOLLOWUP)
                .queryObject("GOODS")
                .operation("RANKING")
                .metric("STOCK_AMOUNT")
                .stockFacet(STOCK_FACET_GOODS_RANKING_HIGH)
                .structuredIntentDetailWire(wire)
                .targetWarehousePlanType(planType)
                .resultAnchorStrategy(ANCHOR_STRATEGY_NONE)
                .knownGapCode(null)
                .allowedPriorPlanTypes(OVERVIEW_PRIOR_PLAN_TYPES)
                .rejectPriorRankingWire(true)
                .build();
    }

    private static WarehouseDrilldownMatrixRow storeFollowupRow(String rowId, String wire, String planType) {
        return WarehouseDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_STORE_FOLLOWUP)
                .queryObject("STORE")
                .operation("SUMMARY")
                .metric("STOCK_AMOUNT")
                .stockFacet(STOCK_FACET_OVERVIEW)
                .structuredIntentDetailWire(wire)
                .targetWarehousePlanType(planType)
                .resultAnchorStrategy(ANCHOR_STRATEGY_STORE)
                .knownGapCode(null)
                .allowedPriorPlanTypes(Set.of(WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH))
                .rejectPriorRankingWire(true)
                .build();
    }
}
