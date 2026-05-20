package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * V2 {@code semanticSlots} 多轮空槽继承与 compat 对齐（D-1X 主链路）。
 * 仅做：当前轮显式槽优先、上一轮 {@code lastSemanticSlots} 填空（不含 wire 继承）、
 * {@code sourceFacet}→{@code metric.purchaseSourceType} 单向 reconcile、会话记忆落库对齐。
 * 不猜 wire、不读用户话术补语义、不用 {@code rankingType} 覆盖 structured 口径。
 */
public final class AiQuerySemanticSlotMerge {

    public static final String UNKNOWN = "UNKNOWN";

    public static final String ANCHOR_USE_PREVIOUS = "USE_PREVIOUS_ANCHOR";
    public static final String ANCHOR_IGNORE_PREVIOUS = "IGNORE_PREVIOUS_ANCHOR";
    public static final String ANCHOR_REQUIRE_CLARIFICATION = "REQUIRE_CLARIFICATION";

    private AiQuerySemanticSlotMerge() {
    }

    /**
     * 首轮继承：当前句槽位字段显式（非空且非 UNKNOWN）优先；否则用上一轮 {@code lastSemanticSlots}。
     * {@code structuredIntentDetailWire} 仅来自当前轮 LLM JSON，不从 {@code lastSemanticSlots} 继承。
     */
    public static AiQuerySemanticParseResult applyPreviousFrameInheritance(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        return applyPreviousFrameInheritance(sem, previousTurn, null, true);
    }

    /**
     * @param normalizedUserMessage 归一用户句；预留签名（与 temporal 采购路径一致），当前空槽继承逻辑不读原文。
     */
    public static AiQuerySemanticParseResult applyPreviousFrameInheritance(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
        return applyPreviousFrameInheritance(sem, previousTurn, normalizedUserMessage, true);
    }

    public static AiQuerySemanticParseResult applyPreviousFrameInheritance(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean inheritBusinessSemanticSlots) {
        if (sem == null || sem.isParseMissing()) {
            return sem;
        }
        final String currentTurnWire = extractCurrentParseStructuredIntentDetailWire(sem.getSemanticSlots());
        if (!inheritBusinessSemanticSlots) {
            return attachCurrentTurnStructuredIntentDetailWire(
                    reconcileMetricWithSourceFacet(sem), currentTurnWire);
        }
        AiQuerySemanticParseResult.SemanticSlotsPart prevFrame =
                previousTurn != null ? previousTurn.getLastSemanticSlots() : null;
        AiQuerySemanticParseResult.SemanticSlotsPart merged =
                mergeSlotPartsPreferCurrent(sem.getSemanticSlots(), prevFrame);
        AiQuerySemanticParseResult out = sem.toBuilder().semanticSlots(merged).build();
        return attachCurrentTurnStructuredIntentDetailWire(
                reconcileMetricWithSourceFacet(out), currentTurnWire);
    }

    /**
     * 采购 frame 校验路径、{@link CurrentSemanticFrameValidator} 前：纯时间换窗追问且 core 槽稀疏时，
     * 提前从 {@code lastSemanticSlots} 继承 queryObject/operation/metric/sourceFacet 等（不含 wire）。
     * 不满足条件时原样返回 {@code sem}。
     */
    public static AiQuerySemanticParseResult applyPreviousFrameInheritanceIfTemporalPurchaseFollowUp(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
        if (sem == null || previousTurn == null) {
            return sem;
        }
        String lastPath = normalizeToken(previousTurn.getLastPathCode());
        if (!AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(lastPath)) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem)) {
            return sem;
        }
        if (!previousTurnHasRecoverablePurchaseSemanticFrame(previousTurn)) {
            return sem;
        }
        if (!temporalPurchaseFollowUpTimeActionIndicatesWindowRewrite(sem.getTimeAction())
                || !temporalPurchaseFollowUpScopeInheritedOrNeutral(sem.getScopeAction())) {
            return sem;
        }
        String norm = normalizedUserMessage != null ? normalizedUserMessage : "";
        return applyPreviousFrameInheritance(sem, previousTurn, norm, true);
    }

    private static boolean previousTurnHasRecoverablePurchaseSemanticFrame(AiConversationTurnMemory p) {
        if (p == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = p.getLastSemanticSlots();
        if (slots == null) {
            return false;
        }
        return StringUtils.hasText(slots.getQueryObject())
                || StringUtils.hasText(slots.getOperation())
                || StringUtils.hasText(slots.getMetric())
                || StringUtils.hasText(slots.getSourceFacet());
    }

    private static boolean temporalPurchaseFollowUpTimeActionIndicatesWindowRewrite(String timeAction) {
        String u = normalizeToken(timeAction);
        if (!StringUtils.hasText(u)) {
            return false;
        }
        return switch (u) {
            case "OVERRIDE",
                    "NEW_TIME",
                    "SEMANTIC_EXPLICIT",
                    "EXPLICIT",
                    "EXPLICIT_TIME",
                    "TIME_OVERRIDE",
                    "RESET",
                    "SHIFT" -> true;
            default -> false;
        };
    }

    private static boolean temporalPurchaseFollowUpScopeInheritedOrNeutral(String scopeAction) {
        if (!StringUtils.hasText(scopeAction)) {
            return true;
        }
        String u = normalizeToken(scopeAction);
        return "INHERIT_PREVIOUS".equals(u) || "INHERIT".equals(u);
    }


    /**
     * 本轮 LLM JSON 已显式给出 {@code semanticSlots.structuredIntentDetailWire} 且可 canonical；
     * D-1X-D1：此时 {@code metric.rankingType} 不得再写 {@code queryIntent.structuredIntentDetail} 或覆盖 slots wire。
     */
    public static boolean hasCanonicalStructuredIntentWireFromSlots(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        String raw = sem.getCurrentTurnStructuredIntentDetailWire();
        if (!StringUtils.hasText(raw) && sem.getSemanticSlots() != null) {
            raw = sem.getSemanticSlots().getStructuredIntentDetailWire();
        }
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        return StringUtils.hasText(AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw.trim()));
    }

    /** 槽位已带 canonical 业务 wire 时，禁止 merge / resolver 再用 {@code metric.rankingType} 或已删除的后处理补 wire 覆盖子口径。 */
    public static boolean hasPurchaseStructuredIntentWireFromSlots(AiQuerySemanticParseResult sem) {
        return hasCanonicalStructuredIntentWireFromSlots(sem);
    }

    /**
     * 会话记忆落库：{@code queryIntent.structuredIntentDetail} 为 merge 后最终口径，
     * {@code querySemanticParse.semanticSlots} 可能仍残留 LLM/继承的排行或对比槽位。
     * 与 final 对齐后再写入 {@code lastSemanticSlots}，避免下一轮 V2 被误导。
     */
    public static AiQuerySemanticParseResult.SemanticSlotsPart alignSemanticSlotsForTurnMemoryPersistence(
            AiQuerySemanticParseResult.SemanticSlotsPart slots, String finalStructuredIntentDetail) {
        if (!StringUtils.hasText(finalStructuredIntentDetail)) {
            return slots;
        }
        String canonFinal =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(finalStructuredIntentDetail.trim());
        if (!StringUtils.hasText(canonFinal)) {
            return slots;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart base =
                slots != null ? slots : AiQuerySemanticParseResult.SemanticSlotsPart.builder().build();
        AiQuerySemanticParseResult.SemanticSlotsPart merged =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(base.getQueryObject())
                        .operation(base.getOperation())
                        .metric(base.getMetric())
                        .sourceFacet(base.getSourceFacet())
                        .anchorPolicy(base.getAnchorPolicy())
                        .detailWanted(base.getDetailWanted())
                        .structuredIntentDetailWire(canonFinal)
                        .build();
        String canonSlot =
                StringUtils.hasText(base.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                base.getStructuredIntentDetailWire().trim())
                        : null;
        if (canonFinal.equals(canonSlot)) {
            return slots;
        }
        return merged;
    }

    private static AiQuerySemanticParseResult attachCurrentTurnStructuredIntentDetailWire(
            AiQuerySemanticParseResult sem, String currentTurnWire) {
        if (sem == null) {
            return null;
        }
        if (!StringUtils.hasText(currentTurnWire)) {
            if (sem.getCurrentTurnStructuredIntentDetailWire() == null) {
                return sem;
            }
            return sem.toBuilder().currentTurnStructuredIntentDetailWire(null).build();
        }
        if (currentTurnWire.equals(sem.getCurrentTurnStructuredIntentDetailWire())) {
            return sem;
        }
        return sem.toBuilder().currentTurnStructuredIntentDetailWire(currentTurnWire).build();
    }

    private static String extractCurrentParseStructuredIntentDetailWire(
            AiQuerySemanticParseResult.SemanticSlotsPart slots) {
        if (slots == null) {
            return null;
        }
        String raw = slots.getStructuredIntentDetailWire();
        if (!StringUtils.hasText(raw) || UNKNOWN.equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return raw.trim();
    }

    /**
     * 当前 {@code semanticSlots} 是否表达「商品 + RANKING + PURCHASE_AMOUNT」排行形状（供采购追问信号读取，不写 wire）。
     */
    public static boolean slotsIndicateGoodsPurchaseAmountRanking(AiQuerySemanticParseResult sem) {
        return slotsIndicateGoodsPurchaseAmountRanking(sem != null ? sem.getSemanticSlots() : null);
    }

    public static boolean slotsIndicateGoodsPurchaseAmountRanking(
            AiQuerySemanticParseResult.SemanticSlotsPart s) {
        if (s == null) {
            return false;
        }
        return "GOODS".equals(normalizeToken(s.getQueryObject()))
                && "RANKING".equals(normalizeToken(s.getOperation()))
                && "PURCHASE_AMOUNT".equals(normalizeToken(s.getMetric()));
    }

    /** 采购金额汇总问法（与 {@code purchase_source_amount_query} 对齐）。 */
    public static boolean slotsIndicatePurchaseAmountSummary(AiQuerySemanticParseResult sem) {
        return slotsIndicatePurchaseAmountSummary(sem != null ? sem.getSemanticSlots() : null);
    }

    public static boolean slotsIndicatePurchaseAmountSummary(AiQuerySemanticParseResult.SemanticSlotsPart s) {
        if (s == null) {
            return false;
        }
        return "SUMMARY".equals(normalizeToken(s.getOperation()))
                && "PURCHASE_AMOUNT".equals(normalizeToken(s.getMetric()));
    }

    /**
     * 采购单价 / 商品价格类 metric（与 {@link #slotsIndicatePurchaseAmountSummary} 互斥；仅用槽位与 metric Part，不读用户原文）。
     */
    public static boolean slotsIndicatePurchaseUnitPriceFocus(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s != null) {
            String slotMet = normalizeToken(s.getMetric());
            if (StringUtils.hasText(slotMet)) {
                if (slotMet.contains("UNIT_PRICE")
                        || slotMet.contains("SKU_PRICE")
                        || "PRICE".equals(slotMet)
                        || slotMet.endsWith("_PRICE")) {
                    return true;
                }
            }
        }
        AiQuerySemanticParseResult.MetricPart m = sem.getMetric();
        if (m != null && StringUtils.hasText(m.getPrimaryMetric())) {
            String u = m.getPrimaryMetric().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (u.contains("UNIT_PRICE") || u.contains("GOODS_PRICE") || u.contains("SKU_PRICE")) {
                return true;
            }
        }
        return false;
    }

    private static AiQuerySemanticParseResult.SemanticSlotsPart mergeSlotPartsPreferCurrent(
            AiQuerySemanticParseResult.SemanticSlotsPart current,
            AiQuerySemanticParseResult.SemanticSlotsPart prev) {
        if (current == null && prev == null) {
            return AiQuerySemanticParseResult.SemanticSlotsPart.builder().build();
        }
        AiQuerySemanticParseResult.SemanticSlotsPart cur =
                current != null
                        ? current
                        : AiQuerySemanticParseResult.SemanticSlotsPart.builder().build();
        if (prev == null) {
            return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                    .queryObject(pickPreferCurrent(cur.getQueryObject(), null))
                    .operation(pickPreferCurrent(cur.getOperation(), null))
                    .metric(pickPreferCurrent(cur.getMetric(), null))
                    .sourceFacet(pickPreferCurrent(cur.getSourceFacet(), null))
                    .anchorPolicy(pickPreferCurrent(cur.getAnchorPolicy(), null))
                    .detailWanted(pickPreferCurrent(cur.getDetailWanted(), null))
                    .structuredIntentDetailWire(extractCurrentParseStructuredIntentDetailWire(cur))
                    .build();
        }
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .queryObject(pickPreferCurrent(cur.getQueryObject(), prev.getQueryObject()))
                .operation(pickPreferCurrent(cur.getOperation(), prev.getOperation()))
                .metric(pickPreferCurrent(cur.getMetric(), prev.getMetric()))
                .sourceFacet(pickPreferCurrent(cur.getSourceFacet(), prev.getSourceFacet()))
                .anchorPolicy(pickPreferCurrent(cur.getAnchorPolicy(), prev.getAnchorPolicy()))
                .detailWanted(pickPreferCurrent(cur.getDetailWanted(), prev.getDetailWanted()))
                .structuredIntentDetailWire(extractCurrentParseStructuredIntentDetailWire(cur))
                .build();
    }

    private static String pickPreferCurrent(String current, String inherited) {
        if (!isBlankOrUnknown(current)) {
            return normalizeToken(current);
        }
        return isBlankOrUnknown(inherited) ? null : normalizeToken(inherited);
    }

    private static boolean isBlankOrUnknown(String v) {
        if (!StringUtils.hasText(v)) {
            return true;
        }
        return UNKNOWN.equalsIgnoreCase(v.trim());
    }

    private static String normalizeSourceFacet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_ALL.equals(u) || "ALL".equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        return u;
    }

    /**
     * 单向同步：{@code semanticSlots.sourceFacet} → {@code metric.purchaseSourceType}（主语义覆盖 compat）。
     * 不得反向写 slots。
     */
    public static AiQuerySemanticParseResult reconcileMetricWithSourceFacet(AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null || !StringUtils.hasText(s.getSourceFacet())) {
            return sem;
        }
        String pst = normalizeSourceFacet(s.getSourceFacet());
        AiQuerySemanticParseResult.MetricPart m = sem.getMetric();
        if (m == null) {
            return sem.toBuilder()
                    .metric(
                            AiQuerySemanticParseResult.MetricPart.builder()
                                    .purchaseSourceType(pst)
                                    .build())
                    .build();
        }
        return sem.toBuilder()
                .metric(
                        m.toBuilder()
                                .purchaseSourceType(pst)
                                .build())
                .build();
    }

    private static String normalizeToken(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
