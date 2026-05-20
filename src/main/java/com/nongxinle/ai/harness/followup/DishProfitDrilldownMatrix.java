package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
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
 * 参见 {@code docs/ai/dish-profit-drilldown-matrix-contract.md}。
 */
@UtilityClass
public final class DishProfitDrilldownMatrix {

    public static final String ROW_KIND_FIRST_TURN = "FIRST_TURN";
    public static final String ROW_KIND_DISH_ANCHOR_FOLLOW_UP = "DISH_ANCHOR_FOLLOW_UP";

    public static final String ANCHOR_TYPE_NONE = "NONE";
    public static final String ANCHOR_TYPE_DISH = "DISH";

    public static final String DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN = "INGREDIENT_COST_BREAKDOWN";
    public static final String DETAIL_WANTED_PROFIT_REASON = "PROFIT_REASON";
    public static final String DETAIL_WANTED_COST_DETAIL = "COST_DETAIL";

    public static final String CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN =
            "dish.dish_anchor.ingredient_breakdown";

    /** dish_profit_path 排行首轮缺 {@code semanticSlots.structuredIntentDetailWire} 时写入 plan/context debug。 */
    public static final String MATRIX_WIRE_MISSING = "MATRIX_WIRE_MISSING";

    private static final Set<String> DISH_ANCHOR_SOURCE_PLAN_TYPES =
            Set.of(
                    DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST,
                    DishProfitAnswerPlan.TYPE_DISH_COST_GAP,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON,
                    DishProfitAnswerPlan.TYPE_DISH_THEORETICAL_COST,
                    DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE);

    public static final DishProfitDrilldownMatrixRow RANKING_LOW_MARGIN =
            firstTurnRow(
                    "DP-R0a",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN,
                    "DISH",
                    "RANKING",
                    "GROSS_MARGIN_RATE",
                    true);

    public static final DishProfitDrilldownMatrixRow RANKING_HIGH_MARGIN =
            firstTurnRow(
                    "DP-R0b",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN,
                    "DISH",
                    "RANKING",
                    "GROSS_MARGIN_RATE",
                    true);

    public static final DishProfitDrilldownMatrixRow RANKING_HIGH_ACTUAL_COST =
            firstTurnRow(
                    "DP-R0c",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH,
                    DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST,
                    "DISH",
                    "RANKING",
                    "ACTUAL_COST",
                    true);

    public static final DishProfitDrilldownMatrixRow RANKING_MAX_COST_GAP =
            firstTurnRow(
                    "DP-R0d",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_GAP_RANKING_MAX,
                    DishProfitAnswerPlan.TYPE_DISH_COST_GAP,
                    "DISH",
                    "RANKING",
                    "COST_GAP",
                    true);

    public static final DishProfitDrilldownMatrixRow LOW_PROFIT_REASON =
            firstTurnRow(
                    "DP-R0e",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_LOW_PROFIT_REASON,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON,
                    "DISH",
                    "DETAIL",
                    DETAIL_WANTED_PROFIT_REASON,
                    true);

    public static final DishProfitDrilldownMatrixRow SINGLE_THEORETICAL_COST =
            firstTurnRow(
                    "DP-R0f",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_THEORETICAL_COST,
                    DishProfitAnswerPlan.TYPE_DISH_THEORETICAL_COST,
                    "DISH",
                    "DETAIL",
                    "THEORETICAL_COST",
                    true);

    public static final DishProfitDrilldownMatrixRow SINGLE_ACTUAL_OUTBOUND_COST =
            firstTurnRow(
                    "DP-R0g",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST,
                    DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST,
                    "DISH",
                    "DETAIL",
                    "ACTUAL_COST",
                    true);

    public static final DishProfitDrilldownMatrixRow SINGLE_GROSS_MARGIN =
            firstTurnRow(
                    "DP-R0h",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY,
                    DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE,
                    "DISH",
                    "DETAIL",
                    "GROSS_MARGIN_RATE",
                    true);

    public static final DishProfitDrilldownMatrixRow SINGLE_COST_GAP =
            firstTurnRow(
                    "DP-R0i",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_COST_GAP,
                    DishProfitAnswerPlan.TYPE_DISH_COST_GAP,
                    "DISH",
                    "DETAIL",
                    "COST_GAP",
                    true);

    public static final DishProfitDrilldownMatrixRow INGREDIENT_COST_BREAKDOWN_FIRST_TURN =
            firstTurnRow(
                    "DP-R0j",
                    AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN,
                    DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN,
                    "INGREDIENT",
                    "BREAKDOWN",
                    "INGREDIENT_COST",
                    false);

    public static final DishProfitDrilldownMatrixRow DISH_ANCHOR_INGREDIENT_BREAKDOWN =
            DishProfitDrilldownMatrixRow.builder()
                    .rowId("DP-R1")
                    .rowKind(ROW_KIND_DISH_ANCHOR_FOLLOW_UP)
                    .capabilityId(CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN)
                    .anchorType(ANCHOR_TYPE_DISH)
                    .allowedPriorFramePlanTypes(DISH_ANCHOR_SOURCE_PLAN_TYPES)
                    .queryObject("INGREDIENT")
                    .operation("BREAKDOWN")
                    .metric("INGREDIENT_COST")
                    .detailWanted(DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN)
                    .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                    .structuredIntentDetailWire(
                            AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN)
                    .targetDishProfitPlanType(DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN)
                    .emitsDishResultAnchor(false)
                    .build();

    private static final Map<String, DishProfitDrilldownMatrixRow> FIRST_TURN_BY_WIRE = buildFirstTurnIndex();

    private static Map<String, DishProfitDrilldownMatrixRow> buildFirstTurnIndex() {
        Map<String, DishProfitDrilldownMatrixRow> index = new LinkedHashMap<>();
        for (DishProfitDrilldownMatrixRow row : firstTurnRows()) {
            index.put(row.getStructuredIntentDetailWire(), row);
        }
        return Map.copyOf(index);
    }

    public static List<DishProfitDrilldownMatrixRow> firstTurnRows() {
        return List.of(
                RANKING_LOW_MARGIN,
                RANKING_HIGH_MARGIN,
                RANKING_HIGH_ACTUAL_COST,
                RANKING_MAX_COST_GAP,
                LOW_PROFIT_REASON,
                SINGLE_THEORETICAL_COST,
                SINGLE_ACTUAL_OUTBOUND_COST,
                SINGLE_GROSS_MARGIN,
                SINGLE_COST_GAP,
                INGREDIENT_COST_BREAKDOWN_FIRST_TURN);
    }

    public static List<DishProfitDrilldownMatrixRow> dishAnchorFollowUpRows() {
        return List.of(DISH_ANCHOR_INGREDIENT_BREAKDOWN);
    }

    public static Set<String> dishAnchorSourcePlanTypes() {
        return DISH_ANCHOR_SOURCE_PLAN_TYPES;
    }

    public static DishProfitDrilldownMatrixRow findFirstTurnRowByWire(String wire) {
        String canonical = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        if (!StringUtils.hasText(canonical)) {
            return null;
        }
        return FIRST_TURN_BY_WIRE.get(canonical.trim());
    }

    public static DishProfitDrilldownMatrixRow findDishAnchorFollowUpRow(
            String detailWanted, String structuredIntentDetailWire, String priorFramePlanType) {
        String dw = normalizeToken(detailWanted);
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire);
        String prior = priorFramePlanType == null ? null : priorFramePlanType.trim();
        for (DishProfitDrilldownMatrixRow row : dishAnchorFollowUpRows()) {
            if (!matchesDetailWanted(dw, row.getDetailWanted())) {
                continue;
            }
            if (wire == null || !wire.equals(row.getStructuredIntentDetailWire())) {
                continue;
            }
            if (prior == null
                    || row.getAllowedPriorFramePlanTypes() == null
                    || !row.getAllowedPriorFramePlanTypes().contains(prior)) {
                continue;
            }
            return row;
        }
        return null;
    }

    public static DishProfitDrilldownMatrixRow findDishAnchorFollowUpRowByDetailWanted(String detailWanted) {
        String dw = normalizeToken(detailWanted);
        if (dw == null) {
            return null;
        }
        for (DishProfitDrilldownMatrixRow row : dishAnchorFollowUpRows()) {
            if (dw.equals(row.getDetailWanted())) {
                return row;
            }
        }
        return null;
    }

    public static boolean followUpSlotMatchesRow(BusinessFollowUpSlot slot, DishProfitDrilldownMatrixRow row) {
        if (slot == null || row == null || !ROW_KIND_DISH_ANCHOR_FOLLOW_UP.equals(row.getRowKind())) {
            return false;
        }
        if (DISH_ANCHOR_INGREDIENT_BREAKDOWN == row
                || CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN.equals(row.getCapabilityId())) {
            return matchesDishAnchorIngredientBreakdownSlot(slot);
        }
        String dw = normalizeToken(slot.getSlotDetailWanted());
        if (!matchesDetailWanted(dw, row.getDetailWanted())) {
            return false;
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        slot.getSemanticStructuredIntentDetailWire());
        if (wire == null || !wire.equals(row.getStructuredIntentDetailWire())) {
            return false;
        }
        String ap = normalizeToken(slot.getSemanticAnchorPolicy());
        return AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(ap);
    }

    /**
     * DISH 锚原料构成追问槽形状（DP-R1）：接受 canonical/别名 wire + INGREDIENT|DISH 槽组合，不读用户原话。
     */
    public static boolean matchesDishAnchorIngredientBreakdownSlot(BusinessFollowUpSlot slot) {
        if (slot == null) {
            return false;
        }
        String ap = normalizeToken(slot.getSemanticAnchorPolicy());
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(ap)) {
            return false;
        }
        if (!ingredientBreakdownWireFromSlot(slot.getSemanticStructuredIntentDetailWire())) {
            return false;
        }
        String dw = normalizeToken(slot.getSlotDetailWanted());
        if (StringUtils.hasText(dw) && !matchesDetailWanted(dw, DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN)) {
            return false;
        }
        String qo = normalizeToken(slot.getSemanticQueryObject());
        String op = normalizeToken(slot.getSemanticOperation());
        if ("INGREDIENT".equals(qo) && ("BREAKDOWN".equals(op) || "DETAIL".equals(op))) {
            return true;
        }
        if ("DISH".equals(qo) && ("DETAIL".equals(op) || "BREAKDOWN".equals(op))) {
            String metric = normalizeToken(slot.getSemanticMetric());
            return metric == null
                    || metric.contains("INGREDIENT")
                    || metric.contains("COST")
                    || "INGREDIENT_COST".equals(metric);
        }
        return false;
    }

    public static boolean semanticSlotsIndicateDishAnchorIngredientBreakdown(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String dw = normalizeToken(s.getDetailWanted());
        if (StringUtils.hasText(dw) && !matchesDetailWanted(dw, DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN)) {
            return false;
        }
        return matchesDishAnchorIngredientBreakdownSlot(
                BusinessFollowUpSlot.builder()
                        .followUp(true)
                        .slotDetailWanted(
                                StringUtils.hasText(dw) ? dw : DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN)
                        .semanticQueryObject(s.getQueryObject())
                        .semanticOperation(s.getOperation())
                        .semanticMetric(s.getMetric())
                        .semanticAnchorPolicy(s.getAnchorPolicy())
                        .semanticStructuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .build());
    }

    /** Phase1 Registry：从 frame + sem 推导 detailWanted（仅 DISH 锚原料构成）。 */
    public static String resolveFollowUpDetailWanted(
            BusinessContextFrame frame,
            AiQuerySemanticParseResult sem,
            String canonicalStructuredIntentWire) {
        if (frame == null || sem == null) {
            return null;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(nullToEmpty(frame.getLastPathCode()))) {
            return null;
        }
        if (!isDishAnchoredDrilldownFramePlanType(frame.getFramePlanType())) {
            return null;
        }
        if (!uniqueDishAnchorPresent(frame.getPreviousResultAnchors())) {
            return null;
        }
        if (semanticSlotsIndicateDishAnchorIngredientBreakdown(sem)) {
            return DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN;
        }
        if (ingredientBreakdownWireFromSlot(canonicalStructuredIntentWire)) {
            String ap =
                    sem.getSemanticSlots() != null
                            ? normalizeToken(sem.getSemanticSlots().getAnchorPolicy())
                            : null;
            if (AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(ap)) {
                return DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN;
            }
        }
        return null;
    }

    /**
     * Registry 匹配前的 slot 对齐：把 merge 后的 ingredient wire / USE_PREVIOUS anchor 写入 slot，
     * 避免 {@link #resolveFollowUpDetailWanted} 已命中但 {@link BusinessFollowUpSlot} 仍缺 wire 导致 Registry 失配。
     */
    public static BusinessFollowUpSlot alignRegistryFollowUpSlot(
            BusinessContextFrame frame,
            BusinessFollowUpSlot slot,
            String mergedWireCanon,
            AiQuerySemanticParseResult sem) {
        if (frame == null || slot == null || sem == null) {
            return slot;
        }
        String detail = resolveFollowUpDetailWanted(frame, sem, mergedWireCanon);
        if (!StringUtils.hasText(detail)) {
            return slot;
        }
        BusinessFollowUpSlot.BusinessFollowUpSlotBuilder b =
                BusinessFollowUpSlot.builder()
                        .followUp(slot.isFollowUp())
                        .normalizedUserMessage(slot.getNormalizedUserMessage())
                        .slotDetailWanted(slot.getSlotDetailWanted())
                        .semanticQueryObject(slot.getSemanticQueryObject())
                        .semanticOperation(slot.getSemanticOperation())
                        .semanticMetric(slot.getSemanticMetric())
                        .semanticSourceFacet(slot.getSemanticSourceFacet())
                        .semanticAnchorPolicy(slot.getSemanticAnchorPolicy())
                        .semanticStructuredIntentDetailWire(slot.getSemanticStructuredIntentDetailWire());
        b.followUp(true).slotDetailWanted(detail);
        if (!ingredientBreakdownWireFromSlot(slot.getSemanticStructuredIntentDetailWire())
                && ingredientBreakdownWireFromSlot(mergedWireCanon)) {
            b.semanticStructuredIntentDetailWire(mergedWireCanon);
        }
        String ap = normalizeToken(slot.getSemanticAnchorPolicy());
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(ap)) {
            String semAp =
                    sem.getSemanticSlots() != null
                            ? normalizeToken(sem.getSemanticSlots().getAnchorPolicy())
                            : null;
            if (AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(semAp)) {
                b.semanticAnchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS);
            }
        }
        return b.build();
    }

    /**
     * DP-R1 Registry 合同（wire + detail + USE_PREVIOUS），与 {@link #resolveFollowUpDetailWanted} 第二路径对齐。
     */
    public static boolean matchesDishAnchorIngredientBreakdownRegistryContract(BusinessFollowUpSlot slot) {
        if (slot == null) {
            return false;
        }
        String dw = normalizeToken(slot.getSlotDetailWanted());
        if (!matchesDetailWanted(dw, DETAIL_WANTED_INGREDIENT_COST_BREAKDOWN)) {
            return false;
        }
        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(normalizeToken(slot.getSemanticAnchorPolicy()))) {
            return false;
        }
        return ingredientBreakdownWireFromSlot(slot.getSemanticStructuredIntentDetailWire());
    }

    /**
     * Matrix/Registry 合同已满足但 {@link BusinessCapabilityRegistry#match} 未命中时的 capability 回填。
     */
    public static BusinessCapabilityMatch synthesizeDishAnchorIngredientBreakdownMatch(
            BusinessContextFrame frame, String slotDetailWanted) {
        if (frame == null || !StringUtils.hasText(slotDetailWanted)) {
            return null;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(nullToEmpty(frame.getLastPathCode()))) {
            return null;
        }
        if (!isDishAnchoredDrilldownFramePlanType(frame.getFramePlanType())) {
            return null;
        }
        if (!uniqueDishAnchorPresent(frame.getPreviousResultAnchors())) {
            return null;
        }
        DishProfitDrilldownMatrixRow row = findDishAnchorFollowUpRowByDetailWanted(slotDetailWanted);
        if (row == null || !CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN.equals(row.getCapabilityId())) {
            return null;
        }
        return BusinessCapabilityMatch.builder()
                .capabilityId(row.getCapabilityId())
                .targetPurchasePlanType(row.getTargetDishProfitPlanType())
                .queryMode("dish_anchor_ingredient_breakdown")
                .build();
    }

    /**
     * dish_profit_path 下 structured wire 的最终口径：点名单菜覆盖排行 wire；DISH 锚原料构成；槽位 canonical。
     */
    public static String resolveStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem, String pathCode, String mergedStructuredDetail) {
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathCode) || sem == null) {
            return null;
        }
        String singleDish = resolveExplicitMentionedDishWire(sem, mergedStructuredDetail);
        if (StringUtils.hasText(singleDish)) {
            return singleDish;
        }
        if (semanticSlotsIndicateDishAnchorIngredientBreakdown(sem)) {
            return AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN;
        }
        String fromSlots = structuredWireFromSemanticSlots(sem);
        if (StringUtils.hasText(fromSlots)) {
            return fromSlots;
        }
        if (StringUtils.hasText(mergedStructuredDetail)) {
            return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(mergedStructuredDetail.trim());
        }
        return null;
    }

    private static String resolveExplicitMentionedDishWire(
            AiQuerySemanticParseResult sem, String mergedStructuredDetail) {
        if (!StringUtils.hasText(sem.getMentionedDishName())) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String op = normalizeToken(s != null ? s.getOperation() : null);
        if ("RANKING".equals(op)) {
            return null;
        }
        String mergedCanon =
                StringUtils.hasText(mergedStructuredDetail)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                mergedStructuredDetail.trim())
                        : null;
        if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(mergedCanon) || isRankingFirstTurnRow(mergedCanon)) {
            return SINGLE_GROSS_MARGIN.getStructuredIntentDetailWire();
        }
        String metric = normalizeToken(s != null ? s.getMetric() : null);
        String qo = normalizeToken(s != null ? s.getQueryObject() : null);
        if ("DISH".equals(qo)
                && (metric == null
                        || metric.contains("GROSS_MARGIN")
                        || metric.contains("PROFIT_MARGIN")
                        || "PROFIT_RATE".equals(metric))) {
            DishProfitDrilldownMatrixRow matched = findFirstTurnRowFromNonRankingSlots(sem);
            if (matched != null && !isRankingFirstTurnRow(matched.getStructuredIntentDetailWire())) {
                return matched.getStructuredIntentDetailWire();
            }
            return SINGLE_GROSS_MARGIN.getStructuredIntentDetailWire();
        }
        return null;
    }

    private static DishProfitDrilldownMatrixRow findFirstTurnRowFromNonRankingSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String wire = structuredWireFromSemanticSlots(sem);
        if (StringUtils.hasText(wire)) {
            DishProfitDrilldownMatrixRow byWire = findFirstTurnRowByWire(wire);
            if (byWire != null && !isRankingFirstTurnRow(byWire.getStructuredIntentDetailWire())) {
                return byWire;
            }
        }
        String qo = normalizeToken(s.getQueryObject());
        String op = normalizeToken(s.getOperation());
        String metric = normalizeToken(s.getMetric());
        for (DishProfitDrilldownMatrixRow row : firstTurnRows()) {
            if (isRankingFirstTurnRow(row.getStructuredIntentDetailWire())) {
                continue;
            }
            if (StringUtils.hasText(qo) && !qo.equals(normalizeToken(row.getQueryObject()))) {
                continue;
            }
            if (StringUtils.hasText(op) && !op.equals(normalizeToken(row.getOperation()))) {
                continue;
            }
            if (StringUtils.hasText(metric) && !metric.equals(normalizeToken(row.getMetric()))) {
                continue;
            }
            return row;
        }
        return null;
    }

    private static String structuredWireFromSemanticSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        String raw = sem.getSemanticSlots().getStructuredIntentDetailWire();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw.trim());
    }

    private static boolean ingredientBreakdownWireFromSlot(String rawWire) {
        if (!StringUtils.hasText(rawWire)) {
            return true;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rawWire.trim());
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(canon)) {
            return true;
        }
        String supplemented = canonicalWireSupplement(rawWire.trim());
        return AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(supplemented);
    }

    private static boolean isRankingFirstTurnRow(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        DishProfitDrilldownMatrixRow row = findFirstTurnRowByWire(wire);
        return row != null && "RANKING".equals(normalizeToken(row.getOperation()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
        DishProfitDrilldownMatrixRow row = findFirstTurnRowByPlanType(planType.trim());
        return row != null && row.isEmitsDishResultAnchor();
    }

    public static DishProfitDrilldownMatrixRow findFirstTurnRowByPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return null;
        }
        String t = planType.trim();
        for (DishProfitDrilldownMatrixRow row : firstTurnRows()) {
            if (t.equals(row.getTargetDishProfitPlanType())) {
                return row;
            }
        }
        return null;
    }

    public static String targetPlanTypeForWire(String wire) {
        DishProfitDrilldownMatrixRow row = findFirstTurnRowByWire(wire);
        return row == null ? null : row.getTargetDishProfitPlanType();
    }

    /**
     * LLM / merge 常见 dish wire 别名 → canonical first-turn wire（仅枚举合同，不读用户原话）。
     */
    public static String canonicalWireSupplement(String snakeWire) {
        if (!StringUtils.hasText(snakeWire)) {
            return null;
        }
        return switch (snakeWire.trim().toLowerCase(Locale.ROOT)) {
            case "dish_cost_breakdown",
                    "dish_cost_breakdown_query",
                    "dish_cost_structure",
                    "dish_ingredient_breakdown",
                    "dish_ingredient_cost_breakdown_query",
                    "dish_cost_composition",
                    "dish_cost_components",
                    "dish_ingredient_composition",
                    "dish_ingredient_cost_composition" ->
                    AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN;
            case "dish_gross_profit_rate_ranking_high",
                    "dish_profit_rate_ranking_high",
                    "dish_margin_ranking_high",
                    "highest_dish_margin_ranking",
                    "dish_highest_margin_ranking" ->
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN;
            case "dish_gross_profit_rate_ranking_low",
                    "dish_profit_rate_ranking_low",
                    "dish_margin_ranking_low",
                    "lowest_dish_margin_ranking",
                    "dish_lowest_margin_ranking" ->
                    AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN;
            case "dish_profit_rate_query",
                    "dish_margin_query",
                    "dish_gross_profit_query",
                    "dish_gross_margin_rate_query",
                    "dish_profit_detail",
                    "dish_single_profit_rate",
                    "dish_single_gross_margin_query" ->
                    AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY;
            default -> null;
        };
    }

    /**
     * 菜品毛利排行槽形状（仅 semanticSlots；不读用户原话、不用 {@code metric.rankingType} 路由）。
     */
    public static boolean semanticSlotsIndicateDishProfitRankingShape(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (!"DISH".equals(normalizeToken(s.getQueryObject()))) {
            return false;
        }
        if (!"RANKING".equals(normalizeToken(s.getOperation()))) {
            return false;
        }
        String metric = normalizeToken(s.getMetric());
        return metric == null
                || metric.contains("GROSS_MARGIN")
                || metric.contains("PROFIT_MARGIN")
                || "PROFIT_RATE".equals(metric);
    }

    /**
     * dish_profit_path 下首轮排行预期 wire 缺失：slots 已表达 RANKING 或 compat rankingType 指向毛利排行，
     * 但 merge 后仍无 matrix first-turn wire。仅用于 debug / 阻断 portfolio fallback，不作 rankingType 路由。
     */
    public static boolean detectMatrixWireMissing(
            AiQuerySemanticParseResult sem, String pathCode, String resolvedWire) {
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathCode)) {
            return false;
        }
        if (StringUtils.hasText(resolvedWire)) {
            DishProfitDrilldownMatrixRow resolvedRow = findFirstTurnRowByWire(resolvedWire);
            if (resolvedRow != null) {
                return false;
            }
        }
        if (semanticSlotsIndicateDishProfitRankingShape(sem)) {
            DishProfitDrilldownMatrixRow ranked =
                    findFirstTurnRowByWire(
                            AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)
                                    ? structuredWireFromSemanticSlots(sem)
                                    : resolvedWire);
            return ranked == null || !"RANKING".equals(normalizeToken(ranked.getOperation()));
        }
        if (sem != null && sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getRankingType())) {
            String rtCanon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            sem.getMetric().getRankingType().trim());
            if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(rtCanon) || isRankingFirstTurnRow(rtCanon)) {
                DishProfitDrilldownMatrixRow ranked =
                        findFirstTurnRowByWire(
                                AiQuerySemanticSlotMerge.hasCanonicalStructuredIntentWireFromSlots(sem)
                                        ? structuredWireFromSemanticSlots(sem)
                                        : resolvedWire);
                return ranked == null || !"RANKING".equals(normalizeToken(ranked.getOperation()));
            }
        }
        return false;
    }

    public static String capabilityIdForDishAnchorFollowUp(String detailWanted) {
        DishProfitDrilldownMatrixRow row = findDishAnchorFollowUpRowByDetailWanted(detailWanted);
        return row == null ? null : row.getCapabilityId();
    }

    public static boolean isDishAnchoredDrilldownFramePlanType(String framePlanType) {
        return isDishAnchorSourcePlanType(framePlanType);
    }

    public static boolean uniqueDishAnchorPresent(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return false;
        }
        AiResultAnchor picked = null;
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            String spt = a.getSourcePlanType() == null ? "" : a.getSourcePlanType().trim();
            if (!isDishAnchorSourcePlanType(spt)) {
                continue;
            }
            if (picked != null) {
                return false;
            }
            picked = a;
        }
        if (picked == null) {
            return false;
        }
        return StringUtils.hasText(picked.getEntityName()) || StringUtils.hasText(picked.getEntityId());
    }

    private static DishProfitDrilldownMatrixRow firstTurnRow(
            String rowId,
            String wire,
            String targetPlanType,
            String queryObject,
            String operation,
            String metric,
            boolean emitsAnchor) {
        return DishProfitDrilldownMatrixRow.builder()
                .rowId(rowId)
                .rowKind(ROW_KIND_FIRST_TURN)
                .capabilityId(null)
                .anchorType(ANCHOR_TYPE_NONE)
                .allowedPriorFramePlanTypes(Set.of())
                .queryObject(queryObject)
                .operation(operation)
                .metric(metric)
                .detailWanted(null)
                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                .structuredIntentDetailWire(wire)
                .targetDishProfitPlanType(targetPlanType)
                .emitsDishResultAnchor(emitsAnchor)
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
}
