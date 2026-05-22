package com.nongxinle.ai.semantic.capability;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
/**
 * Phase 1：采购能力注册表（有序；首个匹配胜出）。
 * <p>职责：上一轮 {@link SemanticContextFrame} + 本轮 {@link SemanticCapabilitySlot} → capabilityId / planType。
 * Registry 供路由匹配；帧形状校验由 {@link com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidator}
 * + {@link PurchaseSemanticCapabilityMatrix#frameMatchesRow} 承担。勿在此做 canonical 或读用户原话。
 */
public final class SemanticCapabilityRegistry {

    private final List<SemanticCapability> capabilities;

    public SemanticCapabilityRegistry(List<SemanticCapability> capabilities) {
        this.capabilities = capabilities == null ? List.of() : capabilities;
    }

    public static SemanticCapabilityRegistry phase1PurchaseOnly() {
        return new SemanticCapabilityRegistry(registerPhase1PurchaseCapabilities());
    }

    /** 采购 Phase 1 + 菜品毛利 DISH 锚追问（只读匹配；不改变 {@link #phase1PurchaseOnly()} 默认消费者）。 */
    public static SemanticCapabilityRegistry phase1PurchaseWithDish() {
        List<SemanticCapability> list = new ArrayList<>(registerPhase1PurchaseCapabilities());
        list.addAll(registerPhase1DishCapabilities());
        return new SemanticCapabilityRegistry(list);
    }

    public static SemanticCapabilityRegistry phase1DishOnly() {
        return new SemanticCapabilityRegistry(registerPhase1DishCapabilities());
    }

    public SemanticCapabilityMatch match(SemanticContextFrame frame, SemanticCapabilitySlot slot) {
        for (SemanticCapability c : capabilities) {
            if (c.getMatcher() != null && c.getMatcher().test(frame, slot)) {
                return SemanticCapabilityMatch.builder()
                        .capabilityId(c.getCapabilityId())
                        .targetPurchasePlanType(c.getTargetPurchasePlanType())
                        .queryMode(c.getQueryMode())
                        .build();
            }
        }
        return null;
    }

    private static List<SemanticCapability> registerPhase1PurchaseCapabilities() {
        List<SemanticCapability> list = new ArrayList<>();
        list.add(
                SemanticCapability.builder()
                        .capabilityId("purchase.supplier_anchor.goods_detail")
                        .description(
                                "PURCHASE_SUPPLIER_AMOUNT_RANKING + SUPPLIER anchor + GOODS_DETAIL or GOODS_UNIT_PRICE")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("supplier_anchor_goods_detail")
                        .matcher(SemanticCapabilityRegistry::matchSupplierAnchorGoodsDetail)
                        .build());
        list.add(
                SemanticCapability.builder()
                        .capabilityId("purchase.goods_anchor.supplier_unit_price")
                        .description("PURCHASE_GOODS_AMOUNT_RANKING or PURCHASE_GOODS_SOURCE_BREAKDOWN + GOODS anchor + SUPPLIER_UNIT_PRICE")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("goods_anchor_supplier_unit_price")
                        .matcher(SemanticCapabilityRegistry::matchGoodsAnchorSupplierUnitPrice)
                        .build());
        list.add(
                SemanticCapability.builder()
                        .capabilityId("purchase.goods_anchor.supplier_breakdown")
                        .description(
                                "Prior GOODS anchor + semanticSlots: BREAKDOWN|DETAIL / GOODS / SUPPLIER_PURCHASE / USE_PREVIOUS_ANCHOR / SUPPLIER_BREAKDOWN / purchase_source_goods_query")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("goods_anchor_supplier_breakdown")
                        .matcher(SemanticCapabilityRegistry::matchGoodsAnchorSupplierBreakdown)
                        .build());
        list.add(
                SemanticCapability.builder()
                        .capabilityId("purchase.goods_anchor.source_breakdown")
                        .description(
                                "Prior GOODS anchor (amount ranking or source breakdown) + semanticSlots: BREAKDOWN / GOODS / ALL / USE_PREVIOUS_ANCHOR / SOURCE_BREAKDOWN / purchase_source_goods_query")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN)
                        .queryMode("goods_source_breakdown")
                        .matcher(SemanticCapabilityRegistry::matchGoodsAnchorSourceBreakdown)
                        .build());
        list.add(
                SemanticCapability.builder()
                        .capabilityId("purchase.supplier_channel.goods_detail")
                        .description("PURCHASE_SUPPLIER_OVERVIEW + SUPPLIER_PURCHASE + GOODS_DETAIL")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .queryMode("supplier_channel_goods_detail")
                        .matcher(SemanticCapabilityRegistry::matchSupplierChannelGoodsDetail)
                        .build());
        list.add(
                SemanticCapability.builder()
                        .capabilityId("purchase.self_channel.goods_detail")
                        .description("PURCHASE_SELF_OVERVIEW + SELF_PURCHASE + GOODS_DETAIL")
                        .targetPurchasePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL)
                        .queryMode("self_channel_goods_detail")
                        .matcher(SemanticCapabilityRegistry::matchSelfChannelGoodsDetail)
                        .build());
        return list;
    }

    private static List<SemanticCapability> registerPhase1DishCapabilities() {
        List<SemanticCapability> list = new ArrayList<>();
        list.add(
                SemanticCapability.builder()
                        .capabilityId(DishProfitSemanticCapabilityMatrix.CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN)
                        .description(
                                "Prior DISH anchor + INGREDIENT_COST_BREAKDOWN + dish_ingredient_cost_breakdown wire")
                        .targetPurchasePlanType(DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN)
                        .queryMode("dish_anchor_ingredient_breakdown")
                        .matcher(SemanticCapabilityRegistry::matchDishAnchorIngredientBreakdown)
                        .build());
        return list;
    }

    private static boolean matchDishAnchorIngredientBreakdown(SemanticContextFrame f, SemanticCapabilitySlot s) {
        if (f == null || s == null || !s.isFollowUp() || f.isPurchasePath()) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(nullToEmpty(f.getLastPathCode()))) {
            return false;
        }
        if (!DishProfitSemanticCapabilityMatrix.isDishAnchoredExecutionFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!DishProfitSemanticCapabilityMatrix.uniqueDishAnchorPresent(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors())) {
            return false;
        }
        if (DishProfitSemanticCapabilityMatrix.followUpSlotMatchesRow(
                s, DishProfitSemanticCapabilityMatrix.DISH_ANCHOR_INGREDIENT_BREAKDOWN)) {
            return true;
        }
        return DishProfitSemanticCapabilityMatrix.matchesDishAnchorIngredientBreakdownRegistryContract(s);
    }

    private static boolean matchSupplierAnchorGoodsDetail(SemanticContextFrame f, SemanticCapabilitySlot s) {
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

    private static boolean matchGoodsAnchorSupplierUnitPrice(SemanticContextFrame f, SemanticCapabilitySlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseSemanticCapabilityMatrix.isGoodsAnchoredExecutionFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!PurchaseSemanticCapabilityMatrix.followUpSlotMatchesRow(s, PurchaseSemanticCapabilityMatrix.SUPPLIER_UNIT_PRICE)) {
            return false;
        }
        return top1GoodsAnchoredExecutionAnchor(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors());
    }

    private static boolean matchGoodsAnchorSupplierBreakdown(SemanticContextFrame f, SemanticCapabilitySlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseSemanticCapabilityMatrix.isGoodsAnchoredExecutionFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!PurchaseSemanticCapabilityMatrix.followUpSlotMatchesRow(s, PurchaseSemanticCapabilityMatrix.SUPPLIER_BREAKDOWN)) {
            return false;
        }
        return top1GoodsAnchoredExecutionAnchor(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors());
    }

    private static boolean matchGoodsAnchorSourceBreakdown(SemanticContextFrame f, SemanticCapabilitySlot s) {
        if (!f.isPurchasePath() || s == null || !s.isFollowUp()) {
            return false;
        }
        if (!PurchaseSemanticCapabilityMatrix.isGoodsAnchoredExecutionFramePlanType(f.getFramePlanType())) {
            return false;
        }
        if (!PurchaseSemanticCapabilityMatrix.followUpSlotMatchesRow(s, PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN)) {
            return false;
        }
        return top1GoodsAnchoredExecutionAnchor(
                f.getPreviousResultAnchors() == null ? List.of() : f.getPreviousResultAnchors());
    }

    private static boolean matchSupplierChannelGoodsDetail(SemanticContextFrame f, SemanticCapabilitySlot s) {
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

    private static boolean matchSelfChannelGoodsDetail(SemanticContextFrame f, SemanticCapabilitySlot s) {
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

    private static boolean top1GoodsAnchoredExecutionAnchor(List<AiResultAnchor> anchors) {
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
