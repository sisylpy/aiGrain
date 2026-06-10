package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1：库房库存现量本域矩阵（Harness Engineering 契约表）。
 */
public final class WarehouseSemanticCapabilityMatrix {

    private WarehouseSemanticCapabilityMatrix() {}
    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";
    public static final String ANCHOR_STRATEGY_STORE = "STORE";

    public static final String STOCK_FACET_OVERVIEW = "OVERVIEW";
    public static final String STOCK_FACET_STORE_RANKING = "STORE_RANKING";
    public static final String STOCK_FACET_GOODS_RANKING_HIGH = "GOODS_RANKING_HIGH";
    public static final String STOCK_FACET_GOODS_RANKING_LOW = "GOODS_RANKING_LOW";
    public static final String STOCK_FACET_LOW_STOCK = "LOW_STOCK";
    public static final String STOCK_FACET_NEAR_EXPIRY = "NEAR_EXPIRY";
    public static final String STOCK_FACET_GOODS_DISH_COVER = "GOODS_DISH_COVER";
    public static final String STOCK_FACET_GOODS_BATCH_DETAIL = "GOODS_BATCH_DETAIL";
    public static final String STOCK_FACET_GOODS_ANCHOR_INVENTORY_BUNDLE = "GOODS_INVENTORY_BUNDLE";
    public static final String STOCK_FACET_INVENTORY_SUPERVISION = "INVENTORY_SUPERVISION";

    public static final String CONTRACT_GOODS_SUPPORTED_DISH_COVER =
            GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID;
    public static final String CONTRACT_GOODS_STOCK_BATCH_DETAIL =
            GoodsStockBatchDetailAnswerPlan.CONTRACT_ID;
    public static final String CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE =
            "warehouse.goods_anchor_inventory_bundle.v1";

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
                    null,
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
                    null,
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
                    null,
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
                    null,
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
                    null,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow INVENTORY_RISK_LIST =
            firstTurnRow(
                    "WH-F",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK,
                    "GOODS",
                    "RISK",
                    "LOW_STOCK",
                    STOCK_FACET_LOW_STOCK,
                    null,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow NEAR_EXPIRY =
            firstTurnRow(
                    "WH-G",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_NEAR_EXPIRY,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_NEAR_EXPIRY_RISK,
                    "GOODS",
                    "RISK",
                    "NEAR_EXPIRY",
                    STOCK_FACET_NEAR_EXPIRY,
                    null,
                    null);

    public static final WarehouseSemanticCapabilityMatrixRow GOODS_SUPPORTED_DISH_COVER =
            firstTurnRow(
                    "WH-H",
                    AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER,
                    GoodsSupportedDishCoverAnswerPlan.TYPE,
                    "GOODS",
                    "DETAIL",
                    "SUPPORTED_DISH_COVER",
                    STOCK_FACET_GOODS_DISH_COVER,
                    null,
                    List.of(GoodsSupportedDishCoverAnswerPlan.TYPE));

    public static final WarehouseSemanticCapabilityMatrixRow GOODS_STOCK_BATCH_DETAIL =
            firstTurnRow(
                    "WH-J",
                    AiQuerySemanticLexicon.STRUCTURED_GOODS_STOCK_BATCH_DETAIL,
                    GoodsStockBatchDetailAnswerPlan.TYPE,
                    "GOODS",
                    "DETAIL",
                    "STOCK_BATCH_DETAIL",
                    STOCK_FACET_GOODS_BATCH_DETAIL,
                    null,
                    List.of(GoodsStockBatchDetailAnswerPlan.TYPE));

    public static final WarehouseSemanticCapabilityMatrixRow GOODS_ANCHOR_INVENTORY_BUNDLE =
            firstTurnRow(
                    "WH-K",
                    AiQuerySemanticLexicon.STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE,
                    GoodsSupportedDishCoverAnswerPlan.TYPE,
                    "GOODS",
                    "DETAIL",
                    "GOODS_INVENTORY_BUNDLE",
                    STOCK_FACET_GOODS_ANCHOR_INVENTORY_BUNDLE,
                    null,
                    List.of(
                            GoodsSupportedDishCoverAnswerPlan.TYPE,
                            GoodsStockBatchDetailAnswerPlan.TYPE));

    public static final WarehouseSemanticCapabilityMatrixRow INVENTORY_SUPERVISION =
            firstTurnRow(
                    "WH-I",
                    AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_INVENTORY_SUPERVISION,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_INVENTORY_SUPERVISION,
                    "ALL",
                    "DIAGNOSIS",
                    "INVENTORY_SUPERVISION",
                    STOCK_FACET_INVENTORY_SUPERVISION,
                    null,
                    null);



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
                INVENTORY_RISK_LIST,
                NEAR_EXPIRY,
                GOODS_SUPPORTED_DISH_COVER,
                GOODS_STOCK_BATCH_DETAIL,
                GOODS_ANCHOR_INVENTORY_BUNDLE,
                INVENTORY_SUPERVISION);
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
            String stockFacet, String knownGap, List<String> planOutputs) {
        return WarehouseSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .stockFacet(stockFacet)
                .structuredIntentDetailWire(wire)
                .targetWarehousePlanType(planType)
                .planOutputs(planOutputs)
                .knownGapCode(knownGap)
                .build();
    }



    public static String targetPlanTypeForWire(String wire) {
        WarehouseSemanticCapabilityMatrixRow row = findFirstTurnRowByWire(wire);
        return row == null ? null : row.getTargetWarehousePlanType();
    }

    /**
     * Contract frame light normalizer：contract-locked 时归一，否则原样返回。
     */
    public static AiQuerySemanticParseResult canonicalizeWarehouseContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return ContractFrameLightNormalizer.normalize(raw);
        }
        return raw;
    }

    private static String normalizeMatrixToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
