package com.nongxinle.ai.semantic;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticContractAnchorInheritanceSupport;
import com.nongxinle.ai.semantic.inheritance.SemanticContractFamilySupport;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceApplier;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 仅做 currentTurnStructuredIntentDetailWire 镜像、会话记忆落库对齐、结构化 dish anchor reconcile、
 * 单菜 contract 主权补齐；<b>不是</b>多轮 Business Frame 继承主链。
 *
 * <p>多轮继承主链：{@link com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritancePolicy} →
 * {@link com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceApplier} →
 * {@link com.nongxinle.ai.semantic.inheritance.CanonicalContractFrameSupport}。
 * 架构见 {@code docs/ai/semantic-inheritance-architecture.md}。
 *
 * <p><b>硬边界：</b>
 * <ul>
 *   <li>本类 <b>不得</b>成为业务 if/else 补丁中心；<b>禁止恢复</b>已 {@code @Deprecated} 的
 *       {@code reconcile*FollowUpSlots} 按域继承逻辑。</li>
 *   <li><b>不得</b>用 previousTurn 覆盖当前轮 sovereign ACTIVE contract 的业务 slots。</li>
 *   <li><b>不得</b>字段级拼装 Business Frame（copy / coalesce 业务槽位）；time-only 整包继承不在此类。</li>
 *   <li>不做 Matrix wire 推断，不从 slots 推业务语义；禁止 {@code contains} / alias。</li>
 * </ul>
 */
public final class AiQuerySemanticSlotMerge {

    public static final String UNKNOWN = "UNKNOWN";

    public static final String ANCHOR_USE_PREVIOUS = "USE_PREVIOUS_ANCHOR";
    public static final String ANCHOR_IGNORE_PREVIOUS = "IGNORE_PREVIOUS_ANCHOR";

    /** Catalog ACTIVE：单菜销量合同；不得被「仅改时间」排行追问恢复逻辑覆盖。 */
    public static final String CONTRACT_DISH_SALES_SINGLE_DISH = "dish_sales.single_dish";
    public static final String CONTRACT_DISH_SALES_STORE_SINGLE_DISH = "dish_sales.store_single_dish";

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
                        .selectedContractId(base.getSelectedContractId())
                        .queryObject(base.getQueryObject())
                        .operation(base.getOperation())
                        .metric(base.getMetric())
                        .sourceFacet(base.getSourceFacet())
                        .anchorPolicy(base.getAnchorPolicy())
                        .detailWanted(base.getDetailWanted())
                        .structuredIntentDetailWire(canonFinal)
                        .answerPlanType(base.getAnswerPlanType())
                        .mentionedDishName(base.getMentionedDishName())
                        .mentionedGoodsName(base.getMentionedGoodsName())
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
                    .selectedContractId(base.getSelectedContractId())
                    .queryObject(base.getQueryObject())
                    .operation(base.getOperation())
                    .metric(base.getMetric())
                    .sourceFacet(base.getSourceFacet())
                    .anchorPolicy(base.getAnchorPolicy())
                    .detailWanted(base.getDetailWanted())
                    .structuredIntentDetailWire(base.getStructuredIntentDetailWire())
                    .answerPlanType(null)
                    .mentionedDishName(base.getMentionedDishName())
                    .mentionedGoodsName(base.getMentionedGoodsName())
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
     * 本轮已选单菜合同：以 catalog entry 为准覆盖 wire/execution 槽位，禁止残留上一轮排行 wire。
     * 不读 rawMessage；不猜菜名。
     */
    public static AiQuerySemanticParseResult reconcileDishSalesSingleDishContractSovereignty(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing() || !isDishSalesSingleDishContractSelection(sem)) {
            return sem;
        }
        String contractId = trimToken(sem.getSemanticSlots().getSelectedContractId());
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(contractId, "DISH_SALES");
        if (contract == null) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart prev = sem.getSemanticSlots();
        String dish = trimToken(firstNonBlank(sem.getMentionedDishName(), prev.getMentionedDishName()));
        String wire = authoritativeDishSalesContractWire(contract);
        String answerPlanType =
                StringUtils.hasText(contract.getAnswerPlanType())
                        ? normalizeToken(contract.getAnswerPlanType())
                        : prev.getAnswerPlanType();
        AiQuerySemanticParseResult.SemanticSlotsPart slots =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .selectedContractId(contract.getContractId())
                        .queryObject(coalesceContractSlot(prev.getQueryObject(), contract.getQueryObjects()))
                        .operation(coalesceContractSlot(prev.getOperation(), contract.getOperations()))
                        .metric(coalesceContractSlot(prev.getMetric(), contract.getMetrics()))
                        .sourceFacet(coalesceContractScalarSlot(prev.getSourceFacet(), contract.getSourceFacet()))
                        .anchorPolicy(prev.getAnchorPolicy())
                        .detailWanted(coalesceContractScalarSlot(prev.getDetailWanted(), contract.getDetailWanted()))
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(normalizeToken(answerPlanType))
                        .mentionedDishName(dish)
                        .requestedTargetGrossMarginRate(prev.getRequestedTargetGrossMarginRate())
                        .build();
        AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder b =
                sem.toBuilder().semanticSlots(slots).currentTurnStructuredIntentDetailWire(wire);
        if (StringUtils.hasText(dish)) {
            b.mentionedDishName(dish);
        }
        return b.build();
    }

    private static String authoritativeDishSalesContractWire(SemanticCapabilityContract contract) {
        if (contract == null || !StringUtils.hasText(contract.getWire())) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(contract.getWire().trim());
    }

    private static String coalesceContractSlot(String llmValue, java.util.Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return llmValue;
        }
        String token = normalizeToken(llmValue);
        if (StringUtils.hasText(token)) {
            for (String a : allowed) {
                if (token.equals(normalizeToken(a))) {
                    return a;
                }
            }
        }
        return allowed.iterator().next();
    }

    private static String coalesceContractScalarSlot(String llmValue, String contractValue) {
        if (StringUtils.hasText(llmValue)) {
            return normalizeToken(llmValue);
        }
        return StringUtils.hasText(contractValue) ? contractValue.trim() : null;
    }

    /**
     * 当前轮显式菜名优先于 slots / 上一轮 anchor：顶层 {@code mentionedDishName} 覆盖 slots 内陈旧继承；
     * 本轮 structured 菜名与 {@code previousTurn.lastMentionedDishName} 不一致时视为换菜，同步 slots 并
     * 置 {@link #ANCHOR_IGNORE_PREVIOUS}。不读 rawMessage。
     */
    public static AiQuerySemanticParseResult reconcileExplicitCurrentTurnDishAnchor(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        if (sem == null || sem.isParseMissing() || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (SemanticSlotInheritanceApplier.suppressPreviousDishAnchor(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String topExplicit = trimToken(sem.getMentionedDishName());
        String slotDish = trimToken(s.getMentionedDishName());
        String prevDish =
                previousTurn != null ? trimToken(previousTurn.getLastMentionedDishName()) : null;
        String anchorPolicy = normalizeToken(s.getAnchorPolicy());

        String resolvedDish;
        if (StringUtils.hasText(topExplicit)) {
            resolvedDish = topExplicit;
        } else if (StringUtils.hasText(slotDish)) {
            resolvedDish = slotDish;
        } else if (ANCHOR_USE_PREVIOUS.equals(anchorPolicy)) {
            if (SemanticSlotInheritanceApplier.suppressPreviousDishAnchor(sem)) {
                return sem;
            }
            resolvedDish =
                    SemanticContractAnchorInheritanceSupport.resolveStructuredDishAnchor(
                            previousTurn, null, null);
            if (!StringUtils.hasText(resolvedDish)) {
                return sem;
            }
        } else {
            return sem;
        }

        anchorPolicy = normalizeToken(s.getAnchorPolicy());
        boolean overridingPrevious =
                StringUtils.hasText(prevDish) && !resolvedDish.equals(prevDish);
        boolean topOverridesSlots =
                StringUtils.hasText(topExplicit)
                        && StringUtils.hasText(slotDish)
                        && !topExplicit.equals(slotDish);
        if (overridingPrevious || topOverridesSlots) {
            anchorPolicy = ANCHOR_IGNORE_PREVIOUS;
        }

        boolean slotsDishMismatch = !resolvedDish.equals(slotDish);
        boolean anchorPolicyChange =
                anchorPolicy != null && !anchorPolicy.equals(normalizeToken(s.getAnchorPolicy()));
        if (!slotsDishMismatch && !anchorPolicyChange) {
            return sem;
        }

        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .selectedContractId(s.getSelectedContractId())
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(anchorPolicy != null ? anchorPolicy : s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .answerPlanType(s.getAnswerPlanType())
                        .mentionedDishName(resolvedDish)
                        .requestedTargetGrossMarginRate(s.getRequestedTargetGrossMarginRate())
                        .build();

        AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder b =
                sem.toBuilder().semanticSlots(updated);
        if (StringUtils.hasText(topExplicit)) {
            b.mentionedDishName(topExplicit);
        } else if (!resolvedDish.equals(topExplicit)) {
            b.mentionedDishName(resolvedDish);
        }
        return b.build();
    }

    /**
     * @deprecated 主链已迁移至 {@link com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritancePolicy}。
     */
    @Deprecated
    public static AiQuerySemanticParseResult reconcileDishSalesStructuredTimeFollowUpSlots(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        return sem;
    }

    /**
     * @deprecated 主链已迁移至 {@link com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritancePolicy}。
     */
    @Deprecated
    public static AiQuerySemanticParseResult reconcilePurchasePeriodGoodsStructuredTimeFollowUpSlots(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        return sem;
    }

    /**
     * @deprecated 请使用 {@link com.nongxinle.ai.semantic.inheritance.SemanticContractFamilySupport#wasPreviousPurchasePeriodGoodsList}。
     */
    @Deprecated
    public static boolean wasPreviousPurchasePeriodGoodsList(AiConversationTurnMemory previousTurn) {
        return SemanticContractFamilySupport.wasPreviousPurchasePeriodGoodsList(previousTurn);
    }

    private static boolean isDishSalesSingleDishContractSlots(
            AiQuerySemanticParseResult.SemanticSlotsPart slots) {
        if (slots == null) {
            return false;
        }
        String contractId = trimToken(slots.getSelectedContractId());
        return CONTRACT_DISH_SALES_SINGLE_DISH.equals(contractId)
                || CONTRACT_DISH_SALES_STORE_SINGLE_DISH.equals(contractId);
    }

    private static boolean isDishSalesSingleDishContractSelection(AiQuerySemanticParseResult sem) {
        return sem != null && isDishSalesSingleDishContractSlots(sem.getSemanticSlots());
    }

    /**
     * @deprecated 主链已迁移至 {@link com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritancePolicy}。
     */
    @Deprecated
    public static AiQuerySemanticParseResult reconcileDishSalesExplicitDishFollowUpSlots(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        return sem;
    }

    /**
     * @deprecated 主链已迁移至 {@link com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritancePolicy}。
     */
    @Deprecated
    public static AiQuerySemanticParseResult reconcileDishCostStructuredFollowUpSlots(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        return sem;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return null;
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
                        .selectedContractId(s.getSelectedContractId())
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(ANCHOR_IGNORE_PREVIOUS)
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .answerPlanType(s.getAnswerPlanType())
                        .mentionedDishName(s.getMentionedDishName())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    private static String normalizeToken(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String trimToken(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
