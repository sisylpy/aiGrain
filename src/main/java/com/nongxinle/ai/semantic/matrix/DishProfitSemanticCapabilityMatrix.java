package com.nongxinle.ai.semantic.matrix;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.semantic.capability.SemanticCapabilitySlot;
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
 * Phase 1：菜品毛利下钻矩阵（Harness Engineering 契约表）。
 * <p>职责：矩阵行定义 + wire/planType/anchor emit 白名单 + DISH 锚追问形状匹配（无 NL）。
 * 执行挂载仍在 {@link com.nongxinle.ai.graph.business.DishProfitAgentNode}（P2 再迁）。
 * 参见 {@code docs/ai/dish-profit-domain-capability-matrix.md}。
 */
@UtilityClass
public final class DishProfitSemanticCapabilityMatrix {


    public static final String ANCHOR_TYPE_NONE = "NONE";
    public static final String ANCHOR_TYPE_DISH = "DISH";

    public static final String DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN = "INGREDIENT_COST_BREAKDOWN";
    public static final String DETAIL_WANTED_PROFIT_REASON = "PROFIT_REASON";
    public static final String DETAIL_WANTED_COST_DETAIL = "COST_DETAIL";

    public static final String CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN =
            "dish.dish_anchor.ingredient_breakdown";

    /** dish_profit_path 排行首轮缺 {@code semanticSlots.structuredIntentDetailWire} 时写入 plan/context debug。 */
    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    /** P2G：扩展排行 / 成本类首轮不在 contract-entry 主链。 */
    public static final String KNOWN_GAP_EXTENDED_RANKINGS_NOT_IN_P2G =
            "DISH_PROFIT_EXTENDED_RANKINGS_NOT_IN_P2G";

    /** P2G：诊断 / 原因类首轮不在 contract-entry 主链。 */
    public static final String KNOWN_GAP_DIAGNOSIS_DETAIL_NOT_IN_P2G =
            "DISH_PROFIT_DIAGNOSIS_DETAIL_NOT_IN_P2G";

    /** P2G：扩展单菜指标首轮不在 contract-entry 主链。 */
    public static final String KNOWN_GAP_EXTENDED_SINGLE_METRICS_NOT_IN_P2G =
            "DISH_PROFIT_EXTENDED_SINGLE_METRICS_NOT_IN_P2G";

    /** P2G：原料构成首轮不在 contract-entry 主链。 */
    public static final String KNOWN_GAP_INGREDIENT_BREAKDOWN_FIRST_TURN_NOT_IN_P2G =
            "DISH_PROFIT_INGREDIENT_BREAKDOWN_FIRST_TURN_NOT_IN_P2G";

    /** P2G：DISH 锚原料构成追问不在 contract-entry 主链。 */
    public static final String KNOWN_GAP_DISH_ANCHOR_INGREDIENT_NOT_IN_P2G =
            "DISH_PROFIT_DISH_ANCHOR_INGREDIENT_NOT_IN_P2G";

    private static final Set<String> DISH_ANCHOR_SOURCE_PLAN_TYPES =
            Set.of(
                    DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_LOWEST_PROFIT_AMOUNT,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST,
                    DishProfitAnswerPlan.TYPE_DISH_COST_GAP,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON,
                    DishProfitAnswerPlan.TYPE_DISH_THEORETICAL_COST,
                    DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE);

    public static final DishProfitSemanticCapabilityMatrixRow OVERVIEW =
            firstTurnRow(
                    "DP-R0k",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW,
                    DishProfitAnswerPlan.TYPE_AGGREGATED_DISH_PORTFOLIO_FALLBACK,
                    "DISH",
                    "OVERVIEW",
                    "GROSS_MARGIN",
                    false);

    public static final DishProfitSemanticCapabilityMatrixRow RANKING_LOW_MARGIN =
            firstTurnRow(
                    "DP-R0a",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN,
                    "DISH",
                    "RANKING",
                    "GROSS_MARGIN_RATE",
                    true);

    public static final DishProfitSemanticCapabilityMatrixRow RANKING_HIGH_MARGIN =
            firstTurnRow(
                    "DP-R0b",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN,
                    "DISH",
                    "RANKING",
                    "GROSS_MARGIN_RATE",
                    true);

    public static final DishProfitSemanticCapabilityMatrixRow RANKING_LOW_PROFIT_AMOUNT =
            firstTurnRow(
                    "DP-R0n",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT,
                    DishProfitAnswerPlan.TYPE_DISH_LOWEST_PROFIT_AMOUNT,
                    "DISH",
                    "RANKING",
                    "GROSS_PROFIT_AMOUNT",
                    true);

    public static final DishProfitSemanticCapabilityMatrixRow RANKING_HIGH_PROFIT_AMOUNT =
            firstTurnRow(
                    "DP-R0p",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT,
                    "DISH",
                    "RANKING",
                    "GROSS_PROFIT_AMOUNT",
                    true);

    public static final DishProfitSemanticCapabilityMatrixRow RANKING_HIGH_ACTUAL_COST =
            firstTurnRow(
                    "DP-R0c",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST,
                    "DISH",
                    "RANKING",
                    "ACTUAL_COST",
                    true);

    public static final DishProfitSemanticCapabilityMatrixRow RANKING_MAX_COST_GAP =
            firstTurnRow(
                    "DP-R0d",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_GAP_RANKING_MAX,
                    DishProfitAnswerPlan.TYPE_DISH_COST_GAP,
                    "DISH",
                    "RANKING",
                    "COST_GAP",
                    true,
                    KNOWN_GAP_EXTENDED_RANKINGS_NOT_IN_P2G);

    public static final DishProfitSemanticCapabilityMatrixRow LOW_PROFIT_REASON =
            firstTurnRow(
                    "DP-R0e",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON,
                    "DISH",
                    "DETAIL",
                    DETAIL_WANTED_PROFIT_REASON,
                    true,
                    KNOWN_GAP_DIAGNOSIS_DETAIL_NOT_IN_P2G);

    public static final DishProfitSemanticCapabilityMatrixRow SINGLE_THEORETICAL_COST =
            firstTurnRow(
                    "DP-R0f",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_THEORETICAL_COST,
                    DishProfitAnswerPlan.TYPE_DISH_THEORETICAL_COST,
                    "DISH",
                    "DETAIL",
                    "THEORETICAL_COST",
                    true,
                    KNOWN_GAP_EXTENDED_SINGLE_METRICS_NOT_IN_P2G);

    public static final DishProfitSemanticCapabilityMatrixRow SINGLE_ACTUAL_OUTBOUND_COST =
            firstTurnRow(
                    "DP-R0g",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST,
                    DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST,
                    "DISH",
                    "DETAIL",
                    "ACTUAL_COST",
                    true,
                    KNOWN_GAP_EXTENDED_SINGLE_METRICS_NOT_IN_P2G);

    public static final DishProfitSemanticCapabilityMatrixRow SINGLE_GROSS_MARGIN =
            firstTurnRow(
                    "DP-R0h",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE,
                    "DISH",
                    "DETAIL",
                    "GROSS_MARGIN_RATE",
                    true);

    public static final DishProfitSemanticCapabilityMatrixRow SINGLE_COST_GAP =
            firstTurnRow(
                    "DP-R0i",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_COST_GAP,
                    DishProfitAnswerPlan.TYPE_DISH_COST_GAP,
                    "DISH",
                    "DETAIL",
                    "COST_GAP",
                    true,
                    KNOWN_GAP_EXTENDED_SINGLE_METRICS_NOT_IN_P2G);

    public static final DishProfitSemanticCapabilityMatrixRow INGREDIENT_COST_BREAKDOWN_FIRST_TURN =
            firstTurnRow(
                    "DP-R0j",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN,
                    DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN,
                    "INGREDIENT",
                    "BREAKDOWN",
                    "INGREDIENT_COST",
                    false,
                    KNOWN_GAP_INGREDIENT_BREAKDOWN_FIRST_TURN_NOT_IN_P2G);

    public static final DishProfitSemanticCapabilityMatrixRow DISH_ANCHOR_INGREDIENT_BREAKDOWN =
            DishProfitSemanticCapabilityMatrixRow.builder()
                    .rowId("DP-R1")
                    .capabilityId(CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN)
                    .anchorType(ANCHOR_TYPE_DISH)
                    .queryObject("INGREDIENT")
                    .operation("BREAKDOWN")
                    .metric("INGREDIENT_COST")
                    .detailWanted(DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN)
                    .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                    .structuredIntentDetailWire(
                            AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN)
                    .targetDishProfitPlanType(DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN)
                    .emitsDishResultAnchor(false)
                    .knownGapCode(KNOWN_GAP_DISH_ANCHOR_INGREDIENT_NOT_IN_P2G)
                    .build();

    private static final Map<String, DishProfitSemanticCapabilityMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, DishProfitSemanticCapabilityMatrixRow> buildFirstTurnIndex() {
        Map<String, DishProfitSemanticCapabilityMatrixRow> index = new LinkedHashMap<>();
        for (DishProfitSemanticCapabilityMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return Map.copyOf(index);
    }

    public static List<DishProfitSemanticCapabilityMatrixRow> firstTurnRows() {
        return List.of(
                OVERVIEW,
                RANKING_LOW_MARGIN,
                RANKING_HIGH_MARGIN,
                RANKING_LOW_PROFIT_AMOUNT,
                RANKING_HIGH_PROFIT_AMOUNT,
                RANKING_HIGH_ACTUAL_COST,
                RANKING_MAX_COST_GAP,
                LOW_PROFIT_REASON,
                SINGLE_THEORETICAL_COST,
                SINGLE_ACTUAL_OUTBOUND_COST,
                SINGLE_GROSS_MARGIN,
                SINGLE_COST_GAP,
                INGREDIENT_COST_BREAKDOWN_FIRST_TURN);
    }

    public static List<DishProfitSemanticCapabilityMatrixRow> dishAnchorFollowUpRows() {
        return List.of(DISH_ANCHOR_INGREDIENT_BREAKDOWN);
    }

    public static Set<String> dishAnchorSourcePlanTypes() {
        return DISH_ANCHOR_SOURCE_PLAN_TYPES;
    }

    public static DishProfitSemanticCapabilityMatrixRow findFirstTurnRowByWire(String wire) {
        String canonical = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        if (!StringUtils.hasText(canonical)) {
            return null;
        }
        return FIRST_TURN_BY_WIRE.get(canonical.trim());
    }

    public static DishProfitSemanticCapabilityMatrixRow findDishAnchorFollowUpRow(
            String detailWanted, String structuredIntentDetailWire, String priorFramePlanType) {
        String dw = normalizeToken(detailWanted);
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire);
        String prior = priorFramePlanType == null ? null : priorFramePlanType.trim();
        for (DishProfitSemanticCapabilityMatrixRow row : dishAnchorFollowUpRows()) {
            if (!matchesDetailWanted(dw, row.getDetailWanted())) {
                continue;
            }
            if (wire == null || !wire.equals(row.getStructuredIntentDetailWire())) {
                continue;
            }
            if (prior == null
                    || !DISH_ANCHOR_SOURCE_PLAN_TYPES.contains(prior)) {
                continue;
            }
            return row;
        }
        return null;
    }

    public static DishProfitSemanticCapabilityMatrixRow findDishAnchorFollowUpRowByDetailWanted(String detailWanted) {
        String dw = normalizeToken(detailWanted);
        if (dw == null) {
            return null;
        }
        for (DishProfitSemanticCapabilityMatrixRow row : dishAnchorFollowUpRows()) {
            if (dw.equals(row.getDetailWanted())) {
                return row;
            }
        }
        return null;
    }

    /**
     * DISH 锚原料构成追问槽形状（DP-R1）：仅比较 canonical wire + anchorPolicy，不读用户原话或 contains 业务推断。
     */
    public static boolean matchesDishAnchorIngredientBreakdownSlot(SemanticCapabilitySlot slot) {
        if (slot == null) {
            return false;
        }
        String ap = normalizeToken(slot.getSemanticAnchorPolicy());
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(ap)) {
            return false;
        }
        String rawWire = slot.getSemanticStructuredIntentDetailWire();
        if (StringUtils.hasText(rawWire)) {
            String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rawWire.trim());
            if (!AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(canon)) {
                return false;
            }
        }
        String dw = normalizeToken(slot.getSlotDetailWanted());
        return !StringUtils.hasText(dw) || matchesDetailWanted(dw, DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN);
    }

    public static boolean isDishAnchorSourcePlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        return DISH_ANCHOR_SOURCE_PLAN_TYPES.contains(planType.trim());
    }

    public static boolean emitsDishResultAnchor(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        DishProfitSemanticCapabilityMatrixRow row = findFirstTurnRowByPlanType(planType.trim());
        return row != null && row.isEmitsDishResultAnchor();
    }

    public static DishProfitSemanticCapabilityMatrixRow findFirstTurnRowByPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return null;
        }
        String t = planType.trim();
        for (DishProfitSemanticCapabilityMatrixRow row : firstTurnRows()) {
            if (t.equals(row.getTargetDishProfitPlanType())) {
                return row;
            }
        }
        return null;
    }

    public static String targetPlanTypeForWire(String wire) {
        DishProfitSemanticCapabilityMatrixRow row = findFirstTurnRowByWire(wire);
        return row == null ? null : row.getTargetDishProfitPlanType();
    }

    public static boolean isRankingTargetPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        return switch (planType.trim()) {
            case DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_LOWEST_PROFIT_AMOUNT,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST,
                    DishProfitAnswerPlan.TYPE_DISH_COST_GAP -> true;
            default -> false;
        };
    }

    public static boolean isRankingStructuredWire(String wire) {
        return isRankingTargetPlanType(targetPlanTypeForWire(wire));
    }

    public static String capabilityIdForDishAnchorFollowUp(String detailWanted) {
        DishProfitSemanticCapabilityMatrixRow row = findDishAnchorFollowUpRowByDetailWanted(detailWanted);
        return row == null ? null : row.getCapabilityId();
    }

    private static DishProfitSemanticCapabilityMatrixRow firstTurnRow(
            String rowId, String wire, String targetPlanType,
            String queryObject, String operation, String metric,
            boolean emitsAnchor) {
        return firstTurnRow(rowId, wire, targetPlanType, queryObject, operation, metric, emitsAnchor, null);
    }

    private static DishProfitSemanticCapabilityMatrixRow firstTurnRow(
            String rowId, String wire, String targetPlanType,
            String queryObject, String operation, String metric,
            boolean emitsAnchor, String knownGapCode) {
        return DishProfitSemanticCapabilityMatrixRow.builder()
                .rowId(rowId)
                .capabilityId(null)
                .anchorType(ANCHOR_TYPE_NONE)
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .detailWanted(null)
                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                .structuredIntentDetailWire(wire)
                .targetDishProfitPlanType(targetPlanType)
                .emitsDishResultAnchor(emitsAnchor)
                .knownGapCode(knownGapCode)
                .build();
    }

    private static boolean matchesDetailWanted(String detailWanted, String required) {
        if (detailWanted == null || required == null) {
            return false;
        }
        if (detailWanted.equals(required)) {
            return true;
        }
        return DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN.equals(required)
                && "DISH_COST_COMPONENTS".equals(detailWanted);
    }

    private static String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return token.trim().toUpperCase(Locale.ROOT);
    }

    /** Contract observe：contract-locked 时 light normalize；non-contract-locked 原样返回。 */
    public static AiQuerySemanticParseResult canonicalizeDishProfitContractFrame(
            AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing() || raw.getSemanticSlots() == null) {
            return raw;
        }
        if (com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine.isContractLockedParse(raw)) {
            return com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer.normalize(raw);
        }
        return raw;
    }
}
