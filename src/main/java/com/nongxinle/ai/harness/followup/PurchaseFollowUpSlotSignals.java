package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.harness.followup.BusinessContextFrame;
import com.nongxinle.ai.harness.followup.BusinessContextFrameBuilder;
import com.nongxinle.ai.harness.followup.DishProfitDrilldownMatrix;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 采购域追问结构信号（主链路 Resolver 使用，非 harness-only）。
 * <p>职责：
 * <ul>
 *   <li>帧形状判定（{@code isComplete*}）— Validator 用</li>
 *   <li>detailWanted 推导（{@link #resolveSlotDetailWanted}）— LLM 未显式给出 detail 时的<strong>槽位/帧推断</strong>（非 canonical）</li>
 *   <li>effFollow / time-only / overview pivot 等门禁</li>
 * </ul>
 * 不做 sem 突变；不做 wire canonical。与 {@link CurrentSemanticFrame#canonicalizePurchaseFollowUp} 边界：
 * 此处可据 framePlan + slots 推断 detail，但不整包重写 wire/metric。
 */
public final class PurchaseFollowUpSlotSignals {

    private static final Set<String> PURCHASE_CANONICAL_RANKING_WIRES =
            Set.of(
                    AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING);

    private PurchaseFollowUpSlotSignals() {
    }

    /**
     * 完整采购排行帧：{@code operation=RANKING} + canonical ranking wire + 核心槽位与 wire/queryObject/metric 自洽；
     * 非 Registry 对象下钻，不要求 {@code detailWanted}。
     */
    public static boolean isCompletePurchaseRankingFrame(CurrentSemanticFrame frame) {
        if (frame == null) {
            return false;
        }
        if (!"RANKING".equals(normalizeSlotToken(frame.getOperation()))) {
            return false;
        }
        String wire = frame.getStructuredIntentDetailWire();
        if (!StringUtils.hasText(wire) || !PURCHASE_CANONICAL_RANKING_WIRES.contains(wire)) {
            return false;
        }
        if (!slotCorePresent(frame.getQueryObject())
                || !slotCorePresent(frame.getMetric())
                || !slotCorePresent(frame.getSourceFacet())
                || !slotCorePresent(frame.getAnchorPolicy())) {
            return false;
        }
        String qo = normalizeSlotToken(frame.getQueryObject());
        if (!rankingWireMatchesQueryObject(wire, qo)) {
            return false;
        }
        return rankingMetricMatchesWire(wire, normalizeSlotToken(frame.getMetric()));
    }

    /**
     * 商品锚下供货商单价排行/明细帧 — 矩阵 {@link PurchaseDrilldownMatrix#SUPPLIER_UNIT_PRICE}。
     */
    public static boolean isCompleteGoodsAnchorSupplierUnitPriceFrame(CurrentSemanticFrame frame) {
        return PurchaseDrilldownMatrix.frameMatchesRow(frame, PurchaseDrilldownMatrix.SUPPLIER_UNIT_PRICE);
    }

    /**
     * 商品锚下各供货商采购额/量拆分明细帧 — 矩阵 {@link PurchaseDrilldownMatrix#SUPPLIER_BREAKDOWN}。
     */
    public static boolean isCompleteGoodsAnchorSupplierBreakdownFrame(CurrentSemanticFrame frame) {
        return PurchaseDrilldownMatrix.frameMatchesRow(frame, PurchaseDrilldownMatrix.SUPPLIER_BREAKDOWN);
    }

    private static boolean rankingWireMatchesQueryObject(String wire, String queryObject) {
        if (!StringUtils.hasText(queryObject)) {
            return false;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(wire)) {
            return "SUPPLIER".equals(queryObject);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(wire)) {
            return "GOODS".equals(queryObject);
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(wire)) {
            return "STORE".equals(queryObject);
        }
        return false;
    }

    private static boolean rankingMetricMatchesWire(String wire, String metric) {
        if (!StringUtils.hasText(metric)) {
            return false;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(wire)) {
            return "PURCHASE_AMOUNT".equals(metric) || metric.contains("PURCHASE_AMOUNT");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(wire)) {
            return "PURCHASE_COUNT".equals(metric)
                    || "PURCHASE_QUANTITY".equals(metric)
                    || metric.contains("PURCHASE_COUNT")
                    || metric.contains("PURCHASE_QUANTITY");
        }
        return false;
    }

    /**
     * 解析本轮「明细诉求」；返回 null 表示无明确槽位。
     */
    public static String resolveSlotDetailWanted(
            boolean followUp, String normalizedUserMessage, AiQuerySemanticParseResult sem) {
        return resolveSlotDetailWanted(followUp, normalizedUserMessage, sem, null, null);
    }

    public static String resolveSlotDetailWanted(
            boolean followUp,
            String normalizedUserMessage,
            AiQuerySemanticParseResult sem,
            BusinessContextFrame frame) {
        return resolveSlotDetailWanted(followUp, normalizedUserMessage, sem, frame, null);
    }

    public static String resolveSlotDetailWanted(
            boolean followUp,
            String normalizedUserMessage,
            AiQuerySemanticParseResult sem,
            BusinessContextFrame frame,
            String canonicalStructuredIntentWire) {
        if (!followUp) {
            return null;
        }
        if (frame != null && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(nullToEmpty(frame.getLastPathCode()))) {
            String dishDetail =
                    DishProfitDrilldownMatrix.resolveFollowUpDetailWanted(
                            frame, sem, canonicalStructuredIntentWire);
            if (StringUtils.hasText(dishDetail)) {
                return dishDetail;
            }
        }
        if (sem != null && sem.getSemanticSlots() != null) {
            AiQuerySemanticParseResult.SemanticSlotsPart slots = sem.getSemanticSlots();
            String explicitDetail = slots.getDetailWanted();
            if (StringUtils.hasText(explicitDetail)
                    && !AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(explicitDetail.trim())) {
                String wireCanon =
                        StringUtils.hasText(canonicalStructuredIntentWire)
                                ? canonicalStructuredIntentWire.trim()
                                : AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                        slots.getStructuredIntentDetailWire());
                String canonical =
                        AiQuerySemanticLexicon.canonicalDetailWanted(
                                explicitDetail.trim(),
                                normalizeSlotToken(slots.getQueryObject()),
                                normalizeSlotToken(slots.getOperation()),
                                wireCanon);
                return canonical != null ? canonical : explicitDetail.trim();
            }
        }
        String msg = compactMessage(normalizedUserMessage);
        String wire =
                StringUtils.hasText(canonicalStructuredIntentWire)
                        ? canonicalStructuredIntentWire.trim()
                        : null;

        if (StringUtils.hasText(wire)
                && AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(wire)
                && !utteranceSupportsGoodsFocusedDrilldown(normalizedUserMessage, sem)
                && !supplierChannelOverviewGoodsListingException(frame, sem, msg)) {
            return null;
        }

        if (suppressSlotForAggregateMoneyQuestionAfterSupplierRanking(frame, sem, normalizedUserMessage)) {
            return null;
        }

        if (messageIndicatesAggregateMoneyFocus(msg, sem)
                && !utteranceSupportsGoodsFocusedDrilldown(normalizedUserMessage, sem)
                && !semanticGoodsSourceBreakdownRequest(sem)
                && !semanticGoodsSupplierBreakdownRequest(sem)) {
            return null;
        }

        String plan = frame != null ? frame.getFramePlanType() : null;

        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW.equals(plan)
                && AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(
                        nullToEmpty(frame != null ? frame.getLastPurchaseSourceType() : null))) {
            if (semanticRequestsGoodsListing(sem) || semanticSlotsIndicateGoodsOrderLineListing(sem)) {
                return "GOODS_DETAIL";
            }
        }

        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(plan)) {
            boolean priceHint =
                    semanticSlotsIndicatePurchasePriceDrilldown(sem)
                            || semanticPurchaseMetricRequestsGoodsPriceDetail(sem);
            boolean listingHint =
                    semanticRequestsGoodsListing(sem)
                            || semanticSlotsIndicateGoodsOrderLineListing(sem)
                            || frameQueryObjectOrDetailImpliesGoodsDrilldownFromSupplierRanking(sem);
            if (!priceHint && !listingHint) {
                return null;
            }
            if (priceHint) {
                return "GOODS_UNIT_PRICE";
            }
            return "GOODS_DETAIL";
        }

        if (PurchaseDrilldownMatrix.isGoodsAnchoredDrilldownFramePlanType(plan)) {
            if (semanticGoodsSupplierBreakdownRequest(sem)) {
                return AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN;
            }
            if (semanticGoodsSourceBreakdownRequest(sem)) {
                return AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN;
            }
            if (semanticGoodsRankingToSupplierUnitPrice(sem)) {
                return AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE;
            }
        }

        if (semanticRequestsGoodsListing(sem) || semanticSlotsIndicateGoodsOrderLineListing(sem)) {
            if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(plan)) {
                return null;
            }
            return "GOODS_DETAIL";
        }
        boolean priceDrilldown =
                semanticPurchaseMetricRequestsGoodsPriceDetail(sem)
                        || semanticPurchaseMetricRequestsSupplierUnitPriceForGoodsAnchor(sem)
                        || semanticSlotsIndicatePurchasePriceDrilldown(sem);
        if (priceDrilldown) {
            if (frame != null) {
                String fp = frame.getFramePlanType();
                if (PurchaseDrilldownMatrix.isGoodsAnchoredDrilldownFramePlanType(fp)) {
                    return AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE;
                }
                if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(fp)) {
                    return "GOODS_UNIT_PRICE";
                }
            }
            if (semanticPurchaseMetricRequestsSupplierUnitPriceForGoodsAnchor(sem)
                    || semanticSlotsIndicateSupplierUnitPriceForGoodsAnchor(sem)) {
                return "SUPPLIER_UNIT_PRICE";
            }
            return "GOODS_UNIT_PRICE";
        }
        return null;
    }

    /**
     * 结构化槽或表层（商品行/单价口语）是否指向 GOODS 追问；与 {@link #semanticIndicatesGoodsFocusedPurchaseDrilldown}
     * 一致用于 registry，不落业务 wire。
     */
    public static boolean utteranceSupportsGoodsFocusedDrilldown(
            String normalizedUserMessage, AiQuerySemanticParseResult sem) {
        return semanticIndicatesGoodsFocusedPurchaseDrilldown(sem);
    }

    /** Resolver：阻断将 goods_query 误升为供货商排行等（需与 {@link #resolveSlotDetailWanted} 一致）。 */
    public static boolean semanticIndicatesGoodsFocusedPurchaseDrilldown(AiQuerySemanticParseResult sem) {
        return semanticRequestsGoodsListing(sem)
                || semanticSlotsIndicateGoodsOrderLineListing(sem)
                || semanticPurchaseMetricRequestsGoodsPriceDetail(sem)
                || semanticPurchaseMetricRequestsSupplierUnitPriceForGoodsAnchor(sem)
                || semanticSlotsIndicatePurchasePriceDrilldown(sem)
                || semanticSlotsIndicateSupplierUnitPriceForGoodsAnchor(sem)
                || AiQuerySemanticSlotMerge.slotsIndicatePurchaseUnitPriceFocus(sem);
    }

    /**
     * 纯时间追问：仅依据 V2 {@code timeAction} / {@code time} / {@code semanticSlots} 形状，不解析用户原话。
     */
    public static boolean shouldSkipObjectDrilldownForTimeOnly(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String slotDetailWanted) {
        if (StringUtils.hasText(slotDetailWanted)) {
            return false;
        }
        if (sem == null || sem.isParseMissing() || previousTurn == null) {
            return false;
        }
        BusinessContextFrame frame = BusinessContextFrameBuilder.fromPreviousTurn(previousTurn);
        return isPurchaseSemanticTimeOnlyNonDrillFollowUp(sem, previousTurn, frame, null);
    }

    /**
     * 上一帧为供货商渠道 overview 且仍继承 {@code purchase_source_amount_query} wire 时，
     * 「定了什么/哪些商品」类追问仍应走 GOODS_DETAIL，不能因 wire 过早 return null。
     */
    private static boolean supplierChannelOverviewGoodsListingException(
            BusinessContextFrame frame, AiQuerySemanticParseResult sem, String msg) {
        if (frame == null) {
            return false;
        }
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW.equals(frame.getFramePlanType())) {
            return false;
        }
        if (!AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(
                nullToEmpty(frame.getLastPurchaseSourceType()))) {
            return false;
        }
        return semanticRequestsGoodsListing(sem) || semanticSlotsIndicateGoodsOrderLineListing(sem);
    }

    private static boolean suppressSlotForAggregateMoneyQuestionAfterSupplierRanking(
            BusinessContextFrame frame, AiQuerySemanticParseResult sem, String normalizedUserMessage) {
        if (frame == null
                || !PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(frame.getFramePlanType())) {
            return false;
        }
        if (utteranceSupportsGoodsFocusedDrilldown(normalizedUserMessage, sem)) {
            return false;
        }
        String msg = compactMessage(normalizedUserMessage);
        return messageIndicatesAggregateMoneyFocus(msg, sem);
    }

    private static boolean messageIndicatesAggregateMoneyFocus(String msg, AiQuerySemanticParseResult sem) {
        if (semanticGoodsSourceBreakdownRequest(sem) || semanticGoodsSupplierBreakdownRequest(sem)) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem != null ? sem.getSemanticSlots() : null;
        if (s != null) {
            String op = normalizeSlotToken(s.getOperation());
            String m = normalizeSlotToken(s.getMetric());
            if ("SUMMARY".equals(op)
                    && m != null
                    && (m.contains("PURCHASE_AMOUNT") || m.contains("AMOUNT") || m.contains("TOTAL"))) {
                if (!"GOODS".equals(normalizeSlotToken(s.getQueryObject()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean frameQueryObjectOrDetailImpliesGoodsDrilldownFromSupplierRanking(
            AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem != null ? sem.getSemanticSlots() : null;
        if (s == null) {
            return false;
        }
        String qo = normalizeSlotToken(s.getQueryObject());
        String op = normalizeSlotToken(s.getOperation());
        if ("GOODS".equals(qo)) {
            return true;
        }
        if ("DETAIL".equals(op)) {
            return qo == null || "GOODS".equals(qo) || "ORDER".equals(qo);
        }
        return false;
    }

    /**
     * transitional compat only — LLM 未显式 detailWanted 时，仅识别 slots 内单价合同信号；不推断 wire 误填（由 canonical 处理）。
     * 不得继续扩展。
     */
    private static boolean semanticGoodsRankingToSupplierUnitPrice(AiQuerySemanticParseResult sem) {
        return PurchaseDrilldownMatrix.hasUnitPriceContractSignal(sem);
    }

    /** Phase2-A：矩阵 {@link PurchaseDrilldownMatrix#SOURCE_BREAKDOWN} 形状推断（LLM 未写 detailWanted 时）。 */
    private static boolean semanticGoodsSourceBreakdownRequest(AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem != null ? sem.getSemanticSlots() : null;
        if (s == null) {
            return false;
        }
        String dw = normalizeSlotToken(s.getDetailWanted());
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw)) {
            return false;
        }
        if (StringUtils.hasText(dw)
                && !AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(dw)) {
            return false;
        }
        return PurchaseDrilldownMatrix.slotsInferRowShape(sem, PurchaseDrilldownMatrix.SOURCE_BREAKDOWN);
    }

    /** 矩阵 {@link PurchaseDrilldownMatrix#SUPPLIER_BREAKDOWN} 形状推断。 */
    private static boolean semanticGoodsSupplierBreakdownRequest(AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem != null ? sem.getSemanticSlots() : null;
        if (s == null) {
            return false;
        }
        String dw = normalizeSlotToken(s.getDetailWanted());
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(dw)) {
            return true;
        }
        if (StringUtils.hasText(dw)) {
            return false;
        }
        return PurchaseDrilldownMatrix.slotsInferRowShape(sem, PurchaseDrilldownMatrix.SUPPLIER_BREAKDOWN);
    }

    private static String compactMessage(String normalizedUserMessage) {
        if (normalizedUserMessage == null) {
            return "";
        }
        return normalizedUserMessage.replace(" ", "").replace("\u3000", "").trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean semanticRequestsGoodsListing(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return false;
        }
        String pm = sem.getMetric().getPrimaryMetric();
        if (!StringUtils.hasText(pm)) {
            return false;
        }
        String u = pm.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return u.contains("GOODS_LISTING")
                || u.contains("ORDER_LINES")
                || u.contains("PURCHASE_LINES")
                || u.contains("LINE_ITEMS");
    }

    private static boolean semanticSlotsIndicateGoodsOrderLineListing(AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem != null ? sem.getSemanticSlots() : null;
        if (s == null) {
            return false;
        }
        String op = normalizeSlotToken(s.getOperation());
        String qo = normalizeSlotToken(s.getQueryObject());
        if (!("DETAIL".equals(op) || "LIST".equals(op))) {
            return false;
        }
        return "GOODS".equals(qo)
                || "ORDER".equals(qo)
                || "LINE_ITEM".equals(qo)
                || "LINE_ITEMS".equals(qo)
                || "PURCHASE_LINE".equals(qo)
                || "PURCHASE_LINES".equals(qo);
    }

    private static boolean semanticSlotsIndicatePurchasePriceDrilldown(AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem != null ? sem.getSemanticSlots() : null;
        if (s == null) {
            return false;
        }
        String m = normalizeSlotToken(s.getMetric());
        if (!StringUtils.hasText(m)) {
            return false;
        }
        return m.contains("UNIT_PRICE")
                || m.contains("PRICE")
                || "UNIT_PRICE".equals(m)
                || "PURCHASE_UNIT_PRICE".equals(m);
    }

    private static boolean semanticSlotsIndicateSupplierUnitPriceForGoodsAnchor(AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.SemanticSlotsPart s = sem != null ? sem.getSemanticSlots() : null;
        if (s == null) {
            return false;
        }
        if (!"SUPPLIER".equals(normalizeSlotToken(s.getQueryObject()))) {
            return false;
        }
        return semanticSlotsIndicatePurchasePriceDrilldown(sem)
                || "DETAIL".equals(normalizeSlotToken(s.getOperation()));
    }

    private static boolean semanticPurchaseMetricRequestsGoodsPriceDetail(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return false;
        }
        String pm = sem.getMetric().getPrimaryMetric();
        if (!StringUtils.hasText(pm)) {
            return false;
        }
        String u = pm.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (u.contains("UNIT_PRICE") || u.contains("GOODS_PRICE") || u.contains("SKU_PRICE")) {
            return true;
        }
        return u.contains("GOODS") && (u.contains("PRICE") || u.contains("DETAIL") || u.contains("LINE"));
    }

    private static boolean semanticPurchaseMetricRequestsSupplierUnitPriceForGoodsAnchor(
            AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getMetric() == null) {
            return false;
        }
        String pm = sem.getMetric().getPrimaryMetric();
        if (!StringUtils.hasText(pm)) {
            return false;
        }
        String u = pm.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return u.contains("SUPPLIER_UNIT_PRICE")
                || u.contains("GOODS_SUPPLIER_UNIT")
                || (u.contains("SUPPLIER") && u.contains("UNIT_PRICE"));
    }

    private static String normalizeSlotToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    /**
     * Resolver 菜品 frame 校验前闸门：上一轮为 dish_profit 专线且有 DP-R1 槽形状 + 唯一 DISH 锚。
     */
    public static boolean isEffectiveStructuralDishFollowUp(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || sem == null || sem.isParseMissing()) {
            return false;
        }
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)
                && !AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(mapLlmIntentPathCode(sem))) {
            return false;
        }
        BusinessContextFrame frame = BusinessContextFrameBuilder.fromPreviousTurn(previousTurn);
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(nullToEmpty(frame.getLastPathCode()))) {
            return false;
        }
        if (!DishProfitDrilldownMatrix.isDishAnchoredDrilldownFramePlanType(frame.getFramePlanType())) {
            return false;
        }
        if (!DishProfitDrilldownMatrix.uniqueDishAnchorPresent(frame.getPreviousResultAnchors())) {
            return false;
        }
        return DishProfitDrilldownMatrix.semanticSlotsIndicateDishAnchorIngredientBreakdown(sem)
                || StringUtils.hasText(
                        DishProfitDrilldownMatrix.resolveFollowUpDetailWanted(frame, sem, null));
    }

    private static String mapLlmIntentPathCode(AiQuerySemanticParseResult sem) {
        if (sem == null || !StringUtils.hasText(sem.getIntent())) {
            return null;
        }
        String u = sem.getIntent().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("DISH_PROFIT".equals(u) || "DISH_MARGIN".equals(u)) {
            return AiResolvedQueryIntent.PATH_DISH_PROFIT;
        }
        return null;
    }

    /**
     * Resolver 采购 frame 校验前闸门：上一轮为采购专线，且有 Registry 侧 detailWanted 或由 V2 显式下发的
     * {@code purchase_source_goods_query} wire，但不把裸时间换窗追问当作采购结构化下钻。
     */
    public static boolean isEffectiveStructuralPurchaseFollowUp(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
        if (previousTurn == null || sem == null || sem.isParseMissing()) {
            return false;
        }
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)) {
            return false;
        }
        BusinessContextFrame frame = BusinessContextFrameBuilder.fromPreviousTurn(previousTurn);
        if (!frame.isPurchasePath()) {
            return false;
        }
        if (Boolean.TRUE.equals(sem.getFollowUp())) {
            if (isPurchaseSemanticTimeOnlyNonDrillFollowUp(
                    sem, previousTurn, frame, normalizedUserMessage)) {
                return false;
            }
            if (isPurchaseOverviewSummaryScopeTimePivotFollowUp(
                    sem, previousTurn, normalizedUserMessage)) {
                return false;
            }
            return AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem);
        }

        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        String canonWire =
                ss != null
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(ss.getStructuredIntentDetailWire())
                        : null;
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(canonWire)) {
            return true;
        }

        String prevCanon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(previousTurn.getLastStructuredIntentDetail());
        String slotDetail =
                resolveSlotDetailWanted(true, normalizedUserMessage, sem, frame, prevCanon == null ? null : prevCanon);
        if (!StringUtils.hasText(slotDetail)) {
            return false;
        }
        return !shouldSkipObjectDrilldownForTimeOnly(sem, previousTurn, slotDetail);
    }

    /**
     * 采购总览汇总 + 显式 scope/time pivot（如点名门店 + 本月采购金额）：非 Registry 对象下钻，不要求 detailWanted。
     * 结构化信号：{@code purchase_overview_summary} + SUMMARY/OVERVIEW + 总览 metric + pivot action/anchor。
     */
    public static boolean isPurchaseOverviewSummaryScopeTimePivotFollowUp(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
        if (previousTurn == null || sem == null || sem.isParseMissing()) {
            return false;
        }
        if (AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)) {
            return false;
        }
        BusinessContextFrame frame = BusinessContextFrameBuilder.fromPreviousTurn(previousTurn);
        if (!frame.isPurchasePath()) {
            return false;
        }
        if (!Boolean.TRUE.equals(sem.getFollowUp())) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss == null || !semanticSlotsCoreBusinessShapeComplete(ss)) {
            return false;
        }
        String canonWire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(ss.getStructuredIntentDetailWire());
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(canonWire)) {
            return false;
        }
        String op = normalizeSlotToken(ss.getOperation());
        if (!("SUMMARY".equals(op) || "OVERVIEW".equals(op))) {
            return false;
        }
        if (!isPurchaseOverviewSummaryMetric(normalizeSlotToken(ss.getMetric()))) {
            return false;
        }
        if (semanticSlotsExplicitDetailWanted(ss)) {
            return false;
        }
        if (semanticIndicatesGoodsFocusedPurchaseDrilldown(sem)
                || semanticGoodsSourceBreakdownRequest(sem)
                || semanticGoodsSupplierBreakdownRequest(sem)) {
            return false;
        }
        String anchorNorm = normalizeSlotToken(ss.getAnchorPolicy());
        boolean ignorePreviousAnchor = AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorNorm);
        boolean scopePivot = isExplicitScopePivot(sem);
        boolean timePivot = isExplicitTimePivot(sem);
        if (!ignorePreviousAnchor && !scopePivot && !timePivot) {
            return false;
        }
        return true;
    }

    /**
     * 基于 v2 action + semanticSlots：仅改时间窗、主查询 shape 不变且无下钻槽/表层信号时，不算「结构化下钻追问」
     * （避免 {@code followUp=true} 一律触发 Registry/detailWanted 门禁）。
     */
    private static boolean isPurchaseSemanticTimeOnlyNonDrillFollowUp(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            BusinessContextFrame frame,
            String normalizedUserMessage) {
        if (!Boolean.TRUE.equals(sem.getFollowUp())) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = sem.getSemanticSlots();
        if (ss == null || !semanticSlotsCoreBusinessShapeComplete(ss)) {
            return false;
        }
        String timeAct = normalizeMergeActionToken(sem.getTimeAction());
        if (!"OVERRIDE".equals(timeAct) && !timeWindowExplicitlySpecified(sem)) {
            return false;
        }
        if (!"INHERIT_PREVIOUS".equals(normalizeMergeActionToken(sem.getScopeAction()))
                || !"INHERIT_PREVIOUS".equals(normalizeMergeActionToken(sem.getMetricAction()))) {
            return false;
        }
        String intentAct = normalizeMergeActionToken(sem.getIntentAction());
        if ("NEW".equals(intentAct) || "OVERRIDE".equals(intentAct)) {
            return false;
        }
        String op = normalizeSlotToken(ss.getOperation());
        if (!("RANKING".equals(op) || "SUMMARY".equals(op) || "OVERVIEW".equals(op))) {
            return false;
        }
        String canonWire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(ss.getStructuredIntentDetailWire());
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(canonWire)) {
            return false;
        }
        if (semanticSlotsExplicitDetailWanted(ss)) {
            return false;
        }
        if (semanticIndicatesGoodsFocusedPurchaseDrilldown(sem)
                || semanticGoodsSourceBreakdownRequest(sem)
                || semanticGoodsSupplierBreakdownRequest(sem)) {
            return false;
        }
        String prevCanon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        previousTurn.getLastStructuredIntentDetail());
        String slotDetail =
                resolveSlotDetailWanted(
                        true, normalizedUserMessage, sem, frame, prevCanon == null ? null : prevCanon);
        return !StringUtils.hasText(slotDetail);
    }

    private static boolean semanticSlotsCoreBusinessShapeComplete(
            AiQuerySemanticParseResult.SemanticSlotsPart ss) {
        return slotCorePresent(ss.getQueryObject())
                && slotCorePresent(ss.getOperation())
                && slotCorePresent(ss.getMetric())
                && slotCorePresent(ss.getSourceFacet());
    }

    private static boolean slotCorePresent(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String t = raw.trim();
        return !AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(t);
    }

    private static boolean semanticSlotsExplicitDetailWanted(AiQuerySemanticParseResult.SemanticSlotsPart ss) {
        String dw = ss.getDetailWanted();
        if (!StringUtils.hasText(dw)) {
            return false;
        }
        String u = dw.trim();
        return !AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(u);
    }

    private static String normalizeMergeActionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("INHERITED_PREVIOUS".equals(u)) {
            return "INHERIT_PREVIOUS";
        }
        return u;
    }

    private static boolean timeWindowExplicitlySpecified(AiQuerySemanticParseResult sem) {
        AiQuerySemanticParseResult.TimePart t = sem.getTime();
        if (t == null) {
            return false;
        }
        if (Boolean.TRUE.equals(t.getNeedInheritFromPrevious())) {
            return false;
        }
        return StringUtils.hasText(t.getStartDate()) || StringUtils.hasText(t.getEndDate());
    }

    private static boolean isPurchaseOverviewSummaryMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return false;
        }
        return "PURCHASE_AMOUNT".equals(metric)
                || "PURCHASE_COUNT".equals(metric)
                || "PURCHASE_QUANTITY".equals(metric);
    }

    private static boolean isExplicitScopePivot(AiQuerySemanticParseResult sem) {
        if ("OVERRIDE".equals(normalizeMergeActionToken(sem.getScopeAction()))) {
            return true;
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = sem.getRequestedScope();
        if (rs == null) {
            return false;
        }
        if (Boolean.TRUE.equals(rs.getNeedInheritFromPrevious())) {
            return false;
        }
        if (StringUtils.hasText(rs.getMentionedStoreName())) {
            return true;
        }
        List<String> names = rs.getMentionedStoreNames();
        return names != null && !names.isEmpty();
    }

    private static boolean isExplicitTimePivot(AiQuerySemanticParseResult sem) {
        if ("OVERRIDE".equals(normalizeMergeActionToken(sem.getTimeAction()))) {
            return true;
        }
        AiQuerySemanticParseResult.TimePart t = sem.getTime();
        if (t == null) {
            return false;
        }
        if (Boolean.TRUE.equals(t.getNeedInheritFromPrevious())) {
            return false;
        }
        return StringUtils.hasText(t.getTimeType())
                || StringUtils.hasText(t.getStartDate())
                || StringUtils.hasText(t.getEndDate());
    }
}
