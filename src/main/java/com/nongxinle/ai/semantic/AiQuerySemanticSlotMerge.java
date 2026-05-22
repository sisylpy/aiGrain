package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.RevenueSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.RevenueSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrixRow;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * V2 {@code semanticSlots} 多轮 reconcile（D-1X 主链路）。
 * 仅做：当前轮 slots → Matrix wire/planType 对齐、{@code sourceFacet}→{@code metric.purchaseSourceType} reconcile、
 * 会话记忆落库对齐。不继承上一轮 wire/answerPlanType；省略追问须先经 LlmFollowUpQueryRewriter。
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
        final String currentTurnWire = extractCurrentParseStructuredIntentDetailWire(sem.getSemanticSlots());
        return attachCurrentTurnStructuredIntentDetailWire(
                reconcileBusinessDiagnosisAnswerPlanTypeFromWire(
                        reconcileBusinessDiagnosisSemanticSlots(
                                reconcileBusinessOverviewAnswerPlanTypeFromWire(
                                        reconcileBusinessOverviewSemanticSlots(
                                                reconcileDishSalesAnswerPlanTypeFromWire(
                                                        reconcileDishSalesSemanticSlots(
                                                                reconcileWarehouseAnswerPlanTypeFromWire(
                                                                        reconcileWarehouseSemanticSlots(
                                                                                reconcileRevenueAnswerPlanTypeFromWire(
                                                                                        reconcileRevenueSemanticSlots(
                                                                                                reconcileDishProfitAnswerPlanTypeFromWire(
                                                                                                        reconcileDishProfitSemanticSlots(
                                                                                                                reconcileStockReduceSemanticSlots(
                                                                                                                        reconcileStockReduceAnswerPlanTypeFromWire(
                                                                                                                                reconcilePurchaseGoodsRankingSemanticSlots(
                                                                                                                                        reconcilePurchaseSourceFacetDefaults(
                                                                                                                                                reconcileAnswerPlanTypeFromWire(
                                                                                                                                                        reconcileMetricWithSourceFacet(
                                                                                                                                                                sem)))))))))))))))))),
                currentTurnWire);
    }

    /** @deprecated Phase1-J 第三批：请用 {@link #reconcileSemanticSlotsViaCapabilityMatrices}。 */
    @Deprecated
    public static AiQuerySemanticParseResult applyPreviousFrameInheritance(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        return reconcileSemanticSlotsViaCapabilityMatrices(sem);
    }

    /** @deprecated Phase1-J 第三批：请用 {@link #reconcileSemanticSlotsViaCapabilityMatrices}。 */
    @Deprecated
    public static AiQuerySemanticParseResult applyPreviousFrameInheritance(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
        return reconcileSemanticSlotsViaCapabilityMatrices(sem);
    }

    /** @deprecated Phase1-J 第三批：请用 {@link #reconcileSemanticSlotsViaCapabilityMatrices}。 */
    @Deprecated
    public static AiQuerySemanticParseResult applyPreviousFrameInheritance(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            @SuppressWarnings("unused") boolean inheritBusinessSemanticSlots) {
        return reconcileSemanticSlotsViaCapabilityMatrices(sem);
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

    public static AiQuerySemanticParseResult reconcileBusinessOverviewSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || !AiQuerySemanticLlmMergeHelper.hasExplicitBusinessOverviewRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null) {
            return sem;
        }
        String resolved =
                BusinessOverviewSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW,
                        s.getStructuredIntentDetailWire());
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        BusinessOverviewSemanticCapabilityMatrixRow row =
                BusinessOverviewSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW, resolved);
        String queryObject = pickPreferCurrent(s.getQueryObject(), row != null ? row.getQueryObject() : null);
        String operation = pickPreferCurrent(s.getOperation(), row != null ? row.getOperation() : null);
        String metric = pickPreferCurrent(s.getMetric(), row != null ? row.getMetric() : null);
        String anchor = pickPreferCurrent(s.getAnchorPolicy(), ANCHOR_IGNORE_PREVIOUS);
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(queryObject)
                        .operation(operation)
                        .metric(metric)
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(anchor)
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    public static AiQuerySemanticParseResult reconcileBusinessOverviewAnswerPlanTypeFromWire(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.hasExplicitBusinessOverviewRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        String planType = BusinessOverviewSemanticCapabilityMatrix.targetPlanTypeForWire(wire);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        return sem.toBuilder()
                .semanticSlots(copySlotsWithAnswerPlanType(s, planType))
                .build();
    }

    public static AiQuerySemanticParseResult reconcileBusinessDiagnosisSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || !AiQuerySemanticLlmMergeHelper.hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null) {
            return sem;
        }
        String resolved =
                BusinessDiagnosisSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS,
                        s.getStructuredIntentDetailWire());
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        BusinessDiagnosisSemanticCapabilityMatrixRow row =
                BusinessDiagnosisSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS, resolved, sem);
        String queryObject = pickPreferCurrent(s.getQueryObject(), row != null ? row.getQueryObject() : null);
        String operation = pickPreferCurrent(s.getOperation(), row != null ? row.getOperation() : null);
        String metric = pickPreferCurrent(s.getMetric(), null);
        String anchor = pickPreferCurrent(s.getAnchorPolicy(), ANCHOR_IGNORE_PREVIOUS);
        String detailWanted = s.getDetailWanted();
        if (isIndependentStandaloneSemanticQuery(s)) {
            detailWanted = pickPreferCurrent(detailWanted, null);
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(queryObject)
                        .operation(operation)
                        .metric(metric)
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(anchor)
                        .detailWanted(detailWanted)
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    public static AiQuerySemanticParseResult reconcileBusinessDiagnosisAnswerPlanTypeFromWire(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.hasExplicitBusinessDiagnosisRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        String planType = BusinessDiagnosisSemanticCapabilityMatrix.targetAnswerPlanTypeForWire(wire);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        return sem.toBuilder()
                .semanticSlots(copySlotsWithAnswerPlanType(s, planType))
                .build();
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

    /** 点名单菜 / 单菜明细：禁止继承上一轮排行 detailWanted / answerPlanType。 */
    private static boolean isDishProfitSingleDishStandaloneQuery(
            AiQuerySemanticParseResult.SemanticSlotsPart cur) {
        if (cur == null) {
            return false;
        }
        String op = normalizeToken(cur.getOperation());
        if (!"DETAIL".equals(op) && !"BREAKDOWN".equals(op)) {
            return false;
        }
        String wire =
                StringUtils.hasText(cur.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                cur.getStructuredIntentDetailWire().trim())
                        : null;
        return AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(wire);
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
     * 采购完整问句：LLM 未填 {@code sourceFacet} 时补 Matrix/ wire 合法默认值，
     * 避免 {@link com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidator} 误报 {@code SOURCE_FACET_UNKNOWN}。
     * 异常 wire 族 / {@code ANOMALY_DETECTION} 未指定渠道时默认 {@code ALL}；
     * STORE/SUMMARY/金额总览默认 {@code ALL}（{@link AiQuerySemanticLexicon#SOURCE_ALL}）；
     * {@code purchase_source_goods_query}（商品→供货商明细等）默认 {@code SUPPLIER_PURCHASE}
     * （{@link AiQuerySemanticLexicon#SOURCE_SUPPLIER_PURCHASE}）或矩阵行要求值。
     */
    /**
     * 采购 GOODS 锚供货商明细：按 Matrix 合同帧补全槽位（非 alias）。
     * 须在 {@link #reconcilePurchaseSourceFacetDefaults} 之前执行，避免 {@code SOURCE_FACET_UNKNOWN}。
     */
    public static AiQuerySemanticParseResult reconcilePurchaseGoodsAnchorSupplierBreakdownFrame(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)
                || !AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem)) {
            return sem;
        }
        if (!PurchaseSemanticCapabilityMatrix.matchesGoodsAnchorSupplierBreakdownFrame(sem)) {
            return sem;
        }
        return PurchaseSemanticCapabilityMatrix.completeGoodsAnchorSupplierBreakdownFrame(sem);
    }

    public static AiQuerySemanticParseResult reconcilePurchaseSourceFacetDefaults(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        sem = reconcilePurchaseGoodsAnchorSupplierBreakdownFrame(sem);
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(normalizeSourceFacet(s.getSourceFacet()))) {
            return sem;
        }
        String defaultSf = PurchaseSemanticCapabilityMatrix.inferDefaultSourceFacetWhenMissing(sem);
        if (defaultSf == null) {
            String qo = normalizeToken(s.getQueryObject());
            String op = normalizeToken(s.getOperation());
            String metric = normalizeToken(s.getMetric());
            String wireCanon =
                    StringUtils.hasText(s.getStructuredIntentDetailWire())
                            ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                    s.getStructuredIntentDetailWire().trim())
                            : null;
            boolean purchaseStoreSummaryAmount =
                    "STORE".equals(qo)
                            && "SUMMARY".equals(op)
                            && StringUtils.hasText(metric)
                            && (metric.contains("PURCHASE") || "AMOUNT".equals(metric));
            boolean purchaseOverviewWire =
                    StringUtils.hasText(wireCanon)
                            && AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(wireCanon)
                            && ("SUMMARY".equals(op) || "OVERVIEW".equals(op) || !StringUtils.hasText(op));
            if (!purchaseStoreSummaryAmount && !purchaseOverviewWire) {
                return sem;
            }
            defaultSf = AiQuerySemanticLexicon.SOURCE_ALL;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(defaultSf)
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    /**
     * 采购域：四槽完整但 LLM wire 非法/自造时，由 Matrix 从 semanticSlots 推导 canonical wire 并写回 slots。
     */
    public static AiQuerySemanticParseResult reconcilePurchaseStructuredWireFromSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)
                || !AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        String currentRaw = s.getStructuredIntentDetailWire();
        String currentCanon =
                StringUtils.hasText(currentRaw)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(currentRaw.trim())
                        : null;
        String resolved =
                PurchaseSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem, AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW, null);
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        if (resolved.equals(currentCanon)) {
            return sem;
        }
        List<String> reasons = new ArrayList<>();
        if (sem.getPurchaseMatrixCanonicalReasons() != null) {
            reasons.addAll(sem.getPurchaseMatrixCanonicalReasons());
        }
        if (AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(currentCanon)) {
            reasons.add(PurchaseSemanticCapabilityMatrix.REASON_LLM_WIRE_OVERRIDDEN_BY_SLOTS);
        } else {
            reasons.add(PurchaseSemanticCapabilityMatrix.REASON_STRUCTURED_WIRE_REWRITTEN_FROM_SLOTS);
        }
        String anchorPolicy = s.getAnchorPolicy();
        if (!StringUtils.hasText(normalizeToken(anchorPolicy)) || UNKNOWN.equals(normalizeToken(anchorPolicy))) {
            anchorPolicy = ANCHOR_IGNORE_PREVIOUS;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(anchorPolicy)
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder()
                .semanticSlots(updated)
                .purchaseMatrixCanonicalReasons(reasons)
                .build();
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

    /**
     * 商品采购金额排行：{@code queryObject=GOODS} + {@code operation=RANKING} 时对齐来源与 wire，
     * 禁止误落成 {@code supplier_amount_ranking}；{@code sourceFacet} 为采购来源主语义。
     */
    public static AiQuerySemanticParseResult reconcilePurchaseGoodsRankingSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (!"GOODS".equals(normalizeToken(s.getQueryObject()))
                || !"RANKING".equals(normalizeToken(s.getOperation()))) {
            return sem;
        }
        String wireCanon =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(wireCanon)) {
            wireCanon = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING;
        }
        if (!StringUtils.hasText(wireCanon)) {
            wireCanon = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING;
        }
        String sf = normalizeSourceFacet(s.getSourceFacet());
        if (!StringUtils.hasText(sf)) {
            sf = AiQuerySemanticLexicon.SOURCE_ALL;
        }
        String metricSlot = pickPreferCurrent(s.getMetric(), null);
        if (!StringUtils.hasText(metricSlot)) {
            metricSlot = "PURCHASE_AMOUNT";
        }
        String anchor =
                pickPreferCurrent(s.getAnchorPolicy(), null);
        if (!StringUtils.hasText(anchor)) {
            anchor = ANCHOR_IGNORE_PREVIOUS;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject("GOODS")
                        .operation("RANKING")
                        .metric(metricSlot)
                        .sourceFacet(sf)
                        .anchorPolicy(anchor)
                        .detailWanted(null)
                        .structuredIntentDetailWire(wireCanon)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    /**
     * 出库域：slots 已表达本域语义时，对齐 canonical wire 与矩阵行形状；不以 metric.rankingType 覆盖明确 slots。
     */
    public static AiQuerySemanticParseResult reconcileStockReduceSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || !AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null) {
            return sem;
        }
        String resolved =
                StockReduceSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY,
                        s.getStructuredIntentDetailWire());
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        StockReduceSemanticCapabilityMatrixRow row =
                StockReduceSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY, resolved, sem);
        String queryObject = pickPreferCurrent(s.getQueryObject(), row != null ? row.getQueryObject() : null);
        String operation = pickPreferCurrent(s.getOperation(), row != null ? row.getOperation() : null);
        String metric = pickPreferCurrent(s.getMetric(), row != null ? row.getMetric() : null);
        String sourceFacet = normalizeStockReduceSourceFacet(s.getSourceFacet());
        String anchor =
                pickPreferCurrent(s.getAnchorPolicy(), null);
        if (!StringUtils.hasText(anchor)) {
            anchor = ANCHOR_IGNORE_PREVIOUS;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(queryObject)
                        .operation(operation)
                        .metric(metric)
                        .sourceFacet(sourceFacet)
                        .anchorPolicy(anchor)
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    /** 出库域：LLM 未填 answerPlanType 时，由 Matrix canonical wire 推导（仅观测/对齐）。 */
    public static AiQuerySemanticParseResult reconcileStockReduceAnswerPlanTypeFromWire(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        if (!AiQuerySemanticLexicon.isStructuredStockReduceDetail(wire)) {
            return sem;
        }
        String planType = StockReduceSemanticCapabilityMatrix.targetPlanTypeForWire(wire);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .answerPlanType(planType)
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    /** 菜品毛利域：slots + Matrix canonical 对齐 queryObject / operation / metric / wire。 */
    public static AiQuerySemanticParseResult reconcileDishProfitSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null
                || AiQuerySemanticLlmMergeHelper.hasExplicitBusinessOverviewRouteSignal(sem)
                || AiQuerySemanticLlmMergeHelper.hasExplicitBusinessDiagnosisRouteSignal(sem)
                || !AiQuerySemanticLlmMergeHelper.v2MapsToExplicitDishProfitPath(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null) {
            return sem;
        }
        String resolved =
                DishProfitSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_DISH_PROFIT,
                        s.getStructuredIntentDetailWire());
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        DishProfitSemanticCapabilityMatrixRow row = DishProfitSemanticCapabilityMatrix.findFirstTurnRowByWire(resolved);
        String queryObject = pickPreferCurrent(s.getQueryObject(), row != null ? row.getQueryObject() : null);
        String operation = pickPreferCurrent(s.getOperation(), row != null ? row.getOperation() : null);
        String metric = pickPreferCurrent(s.getMetric(), row != null ? row.getMetric() : null);
        String anchor = pickPreferCurrent(s.getAnchorPolicy(), null);
        if (!StringUtils.hasText(anchor)) {
            anchor = ANCHOR_IGNORE_PREVIOUS;
        }
        String detailWanted = s.getDetailWanted();
        if (isIndependentStandaloneSemanticQuery(s) || isDishProfitSingleDishStandaloneQuery(s)) {
            detailWanted = pickPreferCurrent(detailWanted, null);
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(queryObject)
                        .operation(operation)
                        .metric(metric)
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(anchor)
                        .detailWanted(detailWanted)
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    public static AiQuerySemanticParseResult reconcileDishProfitAnswerPlanTypeFromWire(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.v2MapsToExplicitDishProfitPath(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        String planType = DishProfitSemanticCapabilityMatrix.targetPlanTypeForWire(wire);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        return sem.toBuilder()
                .semanticSlots(copySlotsWithAnswerPlanType(s, planType))
                .build();
    }

    public static AiQuerySemanticParseResult reconcileRevenueSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || !AiQuerySemanticLlmMergeHelper.hasExplicitRevenueRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null) {
            return sem;
        }
        String resolved =
                RevenueSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW,
                        s.getStructuredIntentDetailWire(),
                        null);
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        RevenueSemanticCapabilityMatrixRow row =
                RevenueSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW, resolved, sem, null);
        String queryObject = pickPreferCurrent(s.getQueryObject(), row != null ? row.getQueryObject() : null);
        String operation = pickPreferCurrent(s.getOperation(), row != null ? row.getOperation() : null);
        String metric = pickPreferCurrent(s.getMetric(), row != null ? row.getMetric() : null);
        String anchor = pickPreferCurrent(s.getAnchorPolicy(), null);
        if (!StringUtils.hasText(anchor)) {
            anchor = ANCHOR_IGNORE_PREVIOUS;
        }
        String detailWanted = s.getDetailWanted();
        if (isIndependentStandaloneSemanticQuery(s)) {
            detailWanted = pickPreferCurrent(detailWanted, null);
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(queryObject)
                        .operation(operation)
                        .metric(metric)
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(anchor)
                        .detailWanted(detailWanted)
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    public static AiQuerySemanticParseResult reconcileRevenueAnswerPlanTypeFromWire(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.hasExplicitRevenueRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        if (!AiQuerySemanticLexicon.isStructuredRevenueDetail(wire)) {
            return sem;
        }
        String planType = RevenueSemanticCapabilityMatrix.targetPlanTypeForWire(wire);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        return sem.toBuilder()
                .semanticSlots(copySlotsWithAnswerPlanType(s, planType))
                .build();
    }

    public static AiQuerySemanticParseResult reconcileWarehouseSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || !AiQuerySemanticLlmMergeHelper.hasExplicitWarehouseRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null) {
            return sem;
        }
        String resolved =
                WarehouseSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK,
                        s.getStructuredIntentDetailWire());
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        WarehouseSemanticCapabilityMatrixRow row =
                WarehouseSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK, resolved, sem, null);
        String queryObject = pickPreferCurrent(s.getQueryObject(), row != null ? row.getQueryObject() : null);
        String operation = pickPreferCurrent(s.getOperation(), row != null ? row.getOperation() : null);
        String metric = pickPreferCurrent(s.getMetric(), row != null ? row.getMetric() : null);
        String sourceFacet =
                pickPreferCurrent(s.getSourceFacet(), row != null ? row.getStockFacet() : null);
        String anchor = pickPreferCurrent(s.getAnchorPolicy(), null);
        if (!StringUtils.hasText(anchor)) {
            anchor = ANCHOR_IGNORE_PREVIOUS;
        }
        String detailWanted = s.getDetailWanted();
        if (isIndependentStandaloneSemanticQuery(s)) {
            detailWanted = pickPreferCurrent(detailWanted, null);
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(queryObject)
                        .operation(operation)
                        .metric(metric)
                        .sourceFacet(sourceFacet)
                        .anchorPolicy(anchor)
                        .detailWanted(detailWanted)
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    public static AiQuerySemanticParseResult reconcileWarehouseAnswerPlanTypeFromWire(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.hasExplicitWarehouseRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        if (!AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(wire)) {
            return sem;
        }
        String planType = WarehouseSemanticCapabilityMatrix.targetPlanTypeForWire(wire);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        return sem.toBuilder()
                .semanticSlots(copySlotsWithAnswerPlanType(s, planType))
                .build();
    }

    public static AiQuerySemanticParseResult reconcileDishSalesSemanticSlots(
            AiQuerySemanticParseResult sem) {
        if (sem == null || !AiQuerySemanticLlmMergeHelper.hasExplicitDishSalesRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (s == null) {
            return sem;
        }
        String resolved =
                DishSalesSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem,
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY,
                        s.getStructuredIntentDetailWire(),
                        null,
                        null);
        if (!StringUtils.hasText(resolved)) {
            return sem;
        }
        DishSalesSemanticCapabilityMatrixRow row =
                DishSalesSemanticCapabilityMatrix.findFirstTurnRowByWire(resolved);
        String queryObject = pickPreferCurrent(s.getQueryObject(), row != null ? row.getQueryObject() : null);
        String operation = pickPreferCurrent(s.getOperation(), row != null ? row.getOperation() : null);
        String metric = pickPreferCurrent(s.getMetric(), row != null ? row.getMetric() : null);
        String anchor = pickPreferCurrent(s.getAnchorPolicy(), null);
        if (!StringUtils.hasText(anchor)) {
            anchor = ANCHOR_IGNORE_PREVIOUS;
        }
        String detailWanted = s.getDetailWanted();
        if (isIndependentStandaloneSemanticQuery(s)) {
            detailWanted = pickPreferCurrent(detailWanted, null);
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(queryObject)
                        .operation(operation)
                        .metric(metric)
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(anchor)
                        .detailWanted(detailWanted)
                        .structuredIntentDetailWire(resolved)
                        .answerPlanType(s.getAnswerPlanType())
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
    }

    public static AiQuerySemanticParseResult reconcileDishSalesAnswerPlanTypeFromWire(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (!AiQuerySemanticLlmMergeHelper.hasExplicitDishSalesRouteSignal(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                StringUtils.hasText(s.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                s.getStructuredIntentDetailWire().trim())
                        : null;
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        if (!AiQuerySemanticLexicon.isStructuredDishSalesDetail(wire)) {
            return sem;
        }
        String planType = DishSalesSemanticCapabilityMatrix.targetPlanTypeForWire(wire);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        return sem.toBuilder()
                .semanticSlots(copySlotsWithAnswerPlanType(s, planType))
                .build();
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

    private static String normalizeStockReduceSourceFacet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(u)
                || AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(u)) {
            return null;
        }
        if ("UNKNOWN".equals(u)) {
            return null;
        }
        return u;
    }

    /** 采购域：LLM 未填 answerPlanType 时，由 canonical wire + sourceFacet 推导（仅观测/对齐）。 */
    public static AiQuerySemanticParseResult reconcileAnswerPlanTypeFromWire(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return sem;
        }
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)
                || !AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem.getSemanticSlots();
        if (StringUtils.hasText(s.getAnswerPlanType())) {
            return sem;
        }
        String wire =
                PurchaseSemanticCapabilityMatrix.resolveStructuredIntentDetailWire(
                        sem, AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW, null);
        if (!StringUtils.hasText(wire) && StringUtils.hasText(s.getStructuredIntentDetailWire())) {
            wire =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            s.getStructuredIntentDetailWire().trim());
        }
        if (!StringUtils.hasText(wire)) {
            return sem;
        }
        if (!AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(wire)) {
            return sem;
        }
        String pst = normalizeSourceFacet(s.getSourceFacet());
        if (pst == null && sem.getMetric() != null) {
            pst = normalizeSourceFacet(sem.getMetric().getPurchaseSourceType());
        }
        String planType = PurchaseAnswerPlanBuilder.resolvePlanType(wire, pst);
        if (!StringUtils.hasText(planType)) {
            return sem;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart updated =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(planType)
                        .build();
        return sem.toBuilder().semanticSlots(updated).build();
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
