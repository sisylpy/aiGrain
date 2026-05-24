package com.nongxinle.ai.semantic;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 仅做 currentTurnStructuredIntentDetailWire 镜像、会话记忆落库对齐、follow-up anchorPolicy 默认值；
 * 不做 Matrix wire 推断，不写 answerPlanType，不从 slots 推业务语义。
 */
public final class AiQuerySemanticSlotMerge {

    public static final String UNKNOWN = "UNKNOWN";

    public static final String ANCHOR_USE_PREVIOUS = "USE_PREVIOUS_ANCHOR";
    public static final String ANCHOR_IGNORE_PREVIOUS = "IGNORE_PREVIOUS_ANCHOR";

    private AiQuerySemanticSlotMerge() {
    }

    /**
     * 仅提取当前轮 canonical structuredIntentDetailWire 并镜像到 parse 对象；
     * 不做 Matrix wire/planType 推断，不区分 contract-locked / non-contract-locked。
     */
    public static AiQuerySemanticParseResult reconcileSemanticSlotsViaCapabilityMatrices(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return sem;
        }
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

    private static String normalizeToken(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
