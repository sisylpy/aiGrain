package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
/**
 * Phase 1：采购能力注册表（有序；首个匹配胜出）。
 * <p>职责：上一轮 {@link BusinessContextFrame} + 本轮 {@link BusinessFollowUpSlot} → capabilityId / planType。
 * {@code semanticMirrorsMatch*} 只做槽位形状镜像，与 {@link PurchaseFollowUpSlotSignals} 帧完整性校验分工：
 * Signals 供 Validator 形状门禁，Registry 供路由匹配；二者 intentionally 相似，勿在此做 canonical 或读用户原话。
 */
public final class BusinessCapabilityRegistry {

    private final List<BusinessCapability> capabilities;

    public BusinessCapabilityRegistry(List<BusinessCapability> capabilities) {
        this.capabilities = capabilities == null ? List.of() : capabilities;
    }

    public static BusinessCapabilityRegistry phase1PurchaseOnly() {
        return new BusinessCapabilityRegistry(registerPhase1PurchaseCapabilities());
    }

    /** 采购 Phase 1 + 菜品毛利 DISH 锚追问（只读匹配；不改变 {@link #phase1PurchaseOnly()} 默认消费者）。 */
    public static BusinessCapabilityRegistry phase1PurchaseWithDish() {
        List<BusinessCapability> list = new ArrayList<>(registerPhase1PurchaseCapabilities());
        list.addAll(registerPhase1DishCapabilities());
        return new BusinessCapabilityRegistry(list);
    }

    public static BusinessCapabilityRegistry phase1DishOnly() {
        return new BusinessCapabilityRegistry(registerPhase1DishCapabilities());
    }

    public BusinessCapabilityMatch match(BusinessContextFrame frame, BusinessFollowUpSlot slot) {
        for (BusinessCapability c : capabilities) {
            if (c.getMatcher() != null && c.getMatcher().test(frame, slot)) {
                return BusinessCapabilityMatch.builder()
                        .capabilityId(c.getCapabilityId())
                        .targetPurchasePlanType(c.getTargetPurchasePlanType())
                        .queryMode(c.getQueryMode())
                        .build();
            }
        }
        return null;
    }

    private static List<BusinessCapability> registerPhase1PurchaseCapabilities() {
        List<BusinessCapability> list = new ArrayList<>();
        list.add(
                BusinessCapability.builder()
                        .capabilityId("purchase.supplier_anchor.goods_detail")
                        .description(
                                "PURCHASE_SUPPLIER_AMOUNT_RANKING + SUPPLIER anchor + GOODS_DETAIL or GOODS_UNIT_PRICE")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("supplier_anchor_goods_detail")
                        .matcher(BusinessCapabilityRegistry::matchSupplierAnchorGoodsDetail)
                        .build());
        list.add(
                BusinessCapability.builder()
                        .capabilityId("purchase.goods_anchor.supplier_unit_price")
                        .description("PURCHASE_GOODS_AMOUNT_RANKING or PURCHASE_GOODS_SOURCE_BREAKDOWN + GOODS anchor + SUPPLIER_UNIT_PRICE")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("goods_anchor_supplier_unit_price")
                        .matcher(BusinessCapabilityRegistry::matchGoodsAnchorSupplierUnitPrice)
                        .build());
        list.add(
                BusinessCapability.builder()
                        .capabilityId("purchase.goods_anchor.supplier_breakdown")
                        .description(
                                "Prior GOODS anchor + semanticSlots: BREAKDOWN|DETAIL / GOODS / SUPPLIER_PURCHASE / USE_PREVIOUS_ANCHOR / SUPPLIER_BREAKDOWN / purchase_source_goods_query")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("goods_anchor_supplier_breakdown")
                        .matcher(BusinessCapabilityRegistry::matchGoodsAnchorSupplierBreakdown)
                        .build());
        list.add(
                BusinessCapability.builder()
                        .capabilityId("purchase.goods_anchor.source_breakdown")
                        .description(
                                "Prior GOODS anchor (amount ranking or source breakdown) + semanticSlots: BREAKDOWN / GOODS / ALL / USE_PREVIOUS_ANCHOR / SOURCE_BREAKDOWN / purchase_source_goods_query")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN)
                        .queryMode("goods_source_breakdown")
                        .matcher(BusinessCapabilityRegistry::matchGoodsAnchorSourceBreakdown)
                        .build());
        list.add(
                BusinessCapability.builder()
                        .capabilityId("purchase.supplier_channel.goods_detail")
                        .description("PURCHASE_SUPPLIER_OVERVIEW + SUPPLIER_PURCHASE + GOODS_DETAIL")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("supplier_channel_goods_detail")
                        .matcher(BusinessCapabilityRegistry::matchSupplierChannelGoodsDetail)
                        .build());
        list.add(
                BusinessCapability.builder()
                        .capabilityId("purchase.self_channel.goods_detail")
                        .description("PURCHASE_SELF_OVERVIEW + SELF_PURCHASE + GOODS_DETAIL")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL)
                        .queryMode("self_channel_goods_detail")
                        .matcher(BusinessCapabilityRegistry::matchSelfChannelGoodsDetail)
                        .build());
        return list;
    }

    private static List<BusinessCapability> registerPhase1DishCapabilities() {
        List<BusinessCapability> list = new ArrayList<>();
        list.add(
                BusinessCapability.builder()
                        .capabilityId(DishProfitDrilldownMatrix.CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN)
                        .description(
                                "Prior DISH anchor + INGREDIENT_COST_BREAKDOWN + dish_ingredient_cost_breakdown wire")
                        .targetPurchasePlanType(DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN)
                        .queryMode("dish_anchor_ingredient_breakdown")
                        .matcher(BusinessCapabilityRegistry::matchDishAnchorIngredientBreakdown)
                        .build());
        return list;
    }

    private static boolean matchDishAnchorIngredientBreakdown(BusinessContextFrame f, BusinessFollowUpSlot s) {
        if (f == null || s == null || !s.isFollowUp() || f.isPurchasePath()) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(nullToEmpty(f.getLastPathCode()))) {
            return false;
        }
        if (!DishProfitDrilldownMatrix.isDishAnchoredDrilldownFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!DishProfitDrilldownMatrix.uniqueDishAnchorPresent(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors())) {
            return false;
        }
        if (DishProfitDrilldownMatrix.followUpSlotMatchesRow(
                s, DishProfitDrilldownMatrix.DISH_ANCHOR_INGREDIENT_BREAKDOWN)) {
            return true;
        }
        return DishProfitDrilldownMatrix.matchesDishAnchorIngredientBreakdownRegistryContract(s);
    }

    private static boolean matchSupplierAnchorGoodsDetail(BusinessContextFrame f, BusinessFollowUpSlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(f.getFramePlanType())) {
            return false;
        }
        String dw = s.getSlotDetailWanted();
        if (!"GOODS_UNIT_PRICE".equals(dw) && !"GOODS_DETAIL".equals(dw)) {
            return false;
        }
        return top1SupplierRankingAnchor(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors());
    }

    private static boolean matchGoodsAnchorSupplierUnitPrice(BusinessContextFrame f, BusinessFollowUpSlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseDrilldownMatrix.isGoodsAnchoredDrilldownFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!PurchaseDrilldownMatrix.followUpSlotMatchesRow(s, PurchaseDrilldownMatrix.SUPPLIER_UNIT_PRICE)) {
            return false;
        }
        return top1GoodsAnchoredDrilldownAnchor(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors());
    }

    private static boolean matchGoodsAnchorSupplierBreakdown(BusinessContextFrame f, BusinessFollowUpSlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseDrilldownMatrix.isGoodsAnchoredDrilldownFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!PurchaseDrilldownMatrix.followUpSlotMatchesRow(s, PurchaseDrilldownMatrix.SUPPLIER_BREAKDOWN)) {
            return false;
        }
        return top1GoodsAnchoredDrilldownAnchor(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors());
    }

    private static boolean matchGoodsAnchorSourceBreakdown(BusinessContextFrame f, BusinessFollowUpSlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseDrilldownMatrix.isGoodsAnchoredDrilldownFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!PurchaseDrilldownMatrix.followUpSlotMatchesRow(s, PurchaseDrilldownMatrix.SOURCE_BREAKDOWN)) {
            return false;
        }
        return top1GoodsAnchoredDrilldownAnchor(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors());
    }

    private static boolean matchSupplierChannelGoodsDetail(BusinessContextFrame f, BusinessFollowUpSlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW.equals(f.getFramePlanType())) {
            return false;
        }
        if (!AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(
                nullToEmpty(f.getLastPurchaseSourceType()))) {
            return false;
        }
        return "GOODS_DETAIL".equals(s.getSlotDetailWanted());
    }

    private static boolean matchSelfChannelGoodsDetail(BusinessContextFrame f, BusinessFollowUpSlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW.equals(f.getFramePlanType())) {
            return false;
        }
        if (!AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equalsIgnoreCase(
                nullToEmpty(f.getLastPurchaseSourceType()))) {
            return false;
        }
        return "GOODS_DETAIL".equals(s.getSlotDetailWanted());
    }

    private static boolean top1SupplierRankingAnchor(List<AiResultAnchor> anchors) {
        if (anchors.isEmpty()) {
            return false;
        }
        long supplierRankingAnchors =
                anchors.stream()
                        .filter(
                                a ->
                                        a != null
                                                && AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(
                                                        nullToEmpty(a.getEntityType()).trim())
                                                && PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(
                                                        nullToEmpty(a.getSourcePlanType())))
                        .count();
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(
                    nullToEmpty(a.getSourcePlanType()))) {
                continue;
            }
            Integer rk = a.getRank();
            boolean rankOne = rk != null && rk == 1;
            boolean singleUnranked = rk == null && anchors.size() == 1;
            boolean loneSupplierRankingUnranked = supplierRankingAnchors == 1 && rk == null;
            if (!(rankOne || singleUnranked || loneSupplierRankingUnranked)) {
                continue;
            }
            return StringUtils.hasText(a.getEntityName());
        }
        return false;
    }

    private static boolean top1GoodsAnchoredDrilldownAnchor(List<AiResultAnchor> anchors) {
        if (anchors.isEmpty()) {
            return false;
        }
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            String spt = nullToEmpty(a.getSourcePlanType());
            boolean goodsRanking =
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(spt)
                            || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(spt);
            boolean goodsBreakdown =
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(spt);
            boolean goodsSupplierDetail =
                    PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(spt);
            if (!goodsRanking && !goodsBreakdown && !goodsSupplierDetail) {
                continue;
            }
            if (goodsBreakdown || goodsSupplierDetail) {
                return StringUtils.hasText(a.getEntityName()) || StringUtils.hasText(a.getEntityId());
            }
            Integer rk = a.getRank();
            boolean rankOne = rk != null && rk == 1;
            boolean singleUnranked = rk == null && anchors.size() == 1;
            if (!(rankOne || singleUnranked)) {
                continue;
            }
            return StringUtils.hasText(a.getEntityName()) || StringUtils.hasText(a.getEntityId());
        }
        return false;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /*
     * Phase2-B（未实现）：结果集内排序 enhancement —
     * purchase.focused_supplier.goods_amount_ranking、purchase.result_frame.goods_amount_ranking。
     * 仅留设计占位；本文件不放业务分支。
     */
}
