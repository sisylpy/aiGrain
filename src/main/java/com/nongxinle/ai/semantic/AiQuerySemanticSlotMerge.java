package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * V2 {@code semanticSlots} 多轮 reconcile（D-1X 主链路）。
 * 仅做：当前轮 slots → Matrix wire/planType 对齐、{@code sourceFacet}→{@code metric.purchaseSourceType} reconcile、
 * 会话记忆落库对齐。不继承上一轮 wire/answerPlanType；省略追问须先经 SemanticIntake。
 */
public final class AiQuerySemanticSlotMerge {

    public static final String UNKNOWN = "UNKNOWN";

    public static final String ANCHOR_USE_PREVIOUS = "USE_PREVIOUS_ANCHOR";
    public static final String ANCHOR_IGNORE_PREVIOUS = "IGNORE_PREVIOUS_ANCHOR";
    public static final String ANCHOR_REQUIRE_CLARIFICATION = "REQUIRE_CLARIFICATION";

    private AiQuerySemanticSlotMerge() {
    }

    /**
     * Merge 后 reconcile 入口：仅对<strong>当前轮</strong> semanticSlots 跑 Matrix reconcile 链
     * （wire / planType / sourceFacet 等）；不继承上一轮 wire / answerPlanType / 业务槽。
     */
    public static AiQuerySemanticParseResult reconcileSemanticSlotsViaCapabilityMatrices(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return sem;
        }
        if (SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            final String currentTurnWire = extractCurrentParseStructuredIntentDetailWire(sem.getSemanticSlots());
            return attachCurrentTurnStructuredIntentDetailWire(sem, currentTurnWire);
        }
        // LEGACY_ONLY deleted — reconcileBusinessOverviewSemanticSlots / reconcileBusinessOverviewAnswerPlanTypeFromWire
        //   reconcileBusinessDiagnosisSemanticSlots / reconcileBusinessDiagnosisAnswerPlanTypeFromWire
        //   removed in BUSINESS-DIAGNOSIS-OVERVIEW-SEMANTIC-MERGE-CLEAN-P1
        final String currentTurnWire = extractCurrentParseStructuredIntentDetailWire(sem.getSemanticSlots());
        return attachCurrentTurnStructuredIntentDetailWire(sem, currentTurnWire);
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
        if (slots != null && StringUtils.hasText(slots.getSelectedContractId())) {
            return slots;
        }
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
                        .answerPlanType(base.getAnswerPlanType())
                        .build();
        String canonSlot =
                StringUtils.hasText(base.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                base.getStructuredIntentDetailWire().trim())
                        : null;
        if (canonFinal.equals(canonSlot)) {
            return slots;
        }
        if (crossDomainStructuredWireConflict(canonSlot, canonFinal)) {
            if (!StringUtils.hasText(base.getAnswerPlanType())) {
                return slots;
            }
            return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                    .queryObject(base.getQueryObject())
                    .operation(base.getOperation())
                    .metric(base.getMetric())
                    .sourceFacet(base.getSourceFacet())
                    .anchorPolicy(base.getAnchorPolicy())
                    .detailWanted(base.getDetailWanted())
                    .structuredIntentDetailWire(base.getStructuredIntentDetailWire())
                    .answerPlanType(null)
                    .build();
        }
        return merged;
    }

    /** 会话记忆：已确定的本域 wire 不得被其它域 reconcile 结果覆盖。 */
    private static boolean crossDomainStructuredWireConflict(String slotCanon, String finalCanon) {
        if (!StringUtils.hasText(slotCanon) || !StringUtils.hasText(finalCanon)) {
            return false;
        }
        if (slotCanon.equals(finalCanon)) {
            return false;
        }
        String slotFam = structuredWireDomainFamily(slotCanon);
        String finalFam = structuredWireDomainFamily(finalCanon);
        return slotFam != null && finalFam != null && !slotFam.equals(finalFam);
    }

    private static String structuredWireDomainFamily(String canon) {
        if (!StringUtils.hasText(canon)) {
            return null;
        }
        if (AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(canon)
                || AiQuerySemanticLexicon.isStructuredBusinessDiagnosisDetail(canon)) {
            return "BUSINESS_COMPOSITE";
        }
        if (AiQuerySemanticLexicon.isStructuredRevenueDetail(canon)) {
            return "REVENUE";
        }
        if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(canon)) {
            return "PURCHASE";
        }
        if (AiQuerySemanticLexicon.isStructuredStockReduceDetail(canon)) {
            return "STOCK_REDUCE";
        }
        if (AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(canon)) {
            return "WAREHOUSE";
        }
        if (AiQuerySemanticLexicon.isStructuredDishSalesDetail(canon)) {
            return "DISH_SALES";
        }
        if (AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(canon)) {
            return "DISH_PROFIT";
        }
        return null;
    }

    // reconcileBusinessOverviewSemanticSlots DELETED — BusinessOverview slots→wire cleanup P1
    // reconcileBusinessOverviewAnswerPlanTypeFromWire DELETED — BusinessOverview wire→planType cleanup P1
    // reconcileBusinessDiagnosisSemanticSlots DELETED — BusinessDiagnosis slots→wire cleanup P1
    // reconcileBusinessDiagnosisAnswerPlanTypeFromWire DELETED — BusinessDiagnosis wire→planType cleanup P1

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

    /**
     * 本轮为独立单域问法（{@code IGNORE_PREVIOUS_ANCHOR} + {@code RANKING}/{@code SUMMARY}/{@code COMPARE}）时，
     * 禁止从上一轮继承 detailWanted / anchorPolicy / answerPlanType / wire。
     */
    private static boolean isIndependentStandaloneSemanticQuery(
            AiQuerySemanticParseResult.SemanticSlotsPart cur) {
        if (cur == null) {
            return false;
        }
        if (!ANCHOR_IGNORE_PREVIOUS.equals(normalizeToken(cur.getAnchorPolicy()))) {
            return false;
        }
        String op = normalizeToken(cur.getOperation());
        return "RANKING".equals(op) || "SUMMARY".equals(op) || "COMPARE".equals(op) || "OVERVIEW".equals(op);
    }

    /**
     * LLM FollowUp Rewrite 已产出完整问句时：默认首轮 anchorPolicy，避免旧下钻门禁误伤。
     */
    public static AiQuerySemanticParseResult reconcilePurchaseCompleteUtteranceDefaults(
            AiQuerySemanticParseResult sem, boolean followUpRewriteApplied) {
        if (!followUpRewriteApplied || sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String ap = normalizeToken(s.getAnchorPolicy());
        if (StringUtils.hasText(ap) && !UNKNOWN.equals(ap)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(ANCHOR_IGNORE_PREVIOUS)
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }



    private static AiQuerySemanticParseResult.SemanticSlotsPart copySlotsWithAnswerPlanType(
            AiQuerySemanticParseResult.SemanticSlotsPart s, String planType) {
        return AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                .queryObject(s.getQueryObject())
                .operation(s.getOperation())
                .metric(s.getMetric())
                .sourceFacet(s.getSourceFacet())
                .anchorPolicy(s.getAnchorPolicy())
                .detailWanted(s.getDetailWanted())
                .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                .answerPlanType(planType)
                .build();
    }

    private static String normalizeToken(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
