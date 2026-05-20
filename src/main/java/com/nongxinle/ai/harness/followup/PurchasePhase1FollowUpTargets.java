package com.nongxinle.ai.harness.followup;

import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.springframework.util.StringUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Phase1 采购下钻：从上一帧 {@link AiResultAnchor} 解析追问对象类型/名称（登记命中后写入 Context，供 Harness / 工具注入）。
 */
public final class PurchasePhase1FollowUpTargets {

    public record Probe(String entityType, String entityName, String entityId) {
        public static final Probe EMPTY = new Probe(null, null, null);
    }

    private PurchasePhase1FollowUpTargets() {
    }

    public static Probe resolve(BusinessCapabilityMatch match, BusinessContextFrame frame) {
        if (match == null || frame == null) {
            return Probe.EMPTY;
        }
        List<AiResultAnchor> anchors = frame.getPreviousResultAnchors();
        if (anchors == null || anchors.isEmpty()) {
            return Probe.EMPTY;
        }
        return switch (match.getCapabilityId()) {
            case "purchase.supplier_anchor.goods_detail" -> top1SupplierRankingProbe(anchors);
            case "purchase.goods_anchor.supplier_unit_price",
                    "purchase.goods_anchor.supplier_breakdown",
                    "purchase.goods_anchor.source_breakdown" -> top1GoodsAmountRankingProbe(anchors);
            case DishProfitDrilldownMatrix.CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN ->
                    uniqueDishAnchorProbe(anchors);
            default -> Probe.EMPTY;
        };
    }

    private static Probe top1SupplierRankingProbe(List<AiResultAnchor> anchors) {
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
            if (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(nullToEmpty(a.getSourcePlanType()))) {
                continue;
            }
            Integer rk = a.getRank();
            boolean rankOne = rk != null && rk == 1;
            boolean singleUnranked = rk == null && anchors.size() == 1;
            boolean loneSupplierRankingUnranked = supplierRankingAnchors == 1 && rk == null;
            if (!(rankOne || singleUnranked || loneSupplierRankingUnranked)) {
                continue;
            }
            if (!StringUtils.hasText(a.getEntityName())) {
                continue;
            }
            String sid = StringUtils.hasText(a.getEntityId()) ? a.getEntityId().trim() : null;
            return new Probe(AiResultAnchor.ENTITY_TYPE_SUPPLIER, a.getEntityName().trim(), sid);
        }
        return Probe.EMPTY;
    }

    private static Probe top1GoodsAmountRankingProbe(List<AiResultAnchor> anchors) {
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            String sourcePlan = nullToEmpty(a.getSourcePlanType());
            boolean goodsRanking =
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(sourcePlan)
                            || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(sourcePlan);
            boolean goodsBreakdown =
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(sourcePlan);
            boolean goodsSupplierDetail =
                    PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(sourcePlan);
            if (!goodsRanking && !goodsBreakdown && !goodsSupplierDetail) {
                continue;
            }
            if (goodsBreakdown || goodsSupplierDetail) {
                if (!StringUtils.hasText(a.getEntityName()) && !StringUtils.hasText(a.getEntityId())) {
                    continue;
                }
                String nm = a.getEntityName() != null ? a.getEntityName().trim() : "";
                String gid = StringUtils.hasText(a.getEntityId()) ? a.getEntityId().trim() : null;
                return new Probe(AiResultAnchor.ENTITY_TYPE_GOODS, nm, gid);
            }
            Integer rk = a.getRank();
            boolean rankOne = rk != null && rk == 1;
            boolean singleUnranked = rk == null && anchors.size() == 1;
            if (!(rankOne || singleUnranked)) {
                continue;
            }
            if (!StringUtils.hasText(a.getEntityName()) && !StringUtils.hasText(a.getEntityId())) {
                continue;
            }
            String nm = a.getEntityName() != null ? a.getEntityName().trim() : "";
            String gid = StringUtils.hasText(a.getEntityId()) ? a.getEntityId().trim() : null;
            return new Probe(AiResultAnchor.ENTITY_TYPE_GOODS, nm, gid);
        }
        return Probe.EMPTY;
    }

    private static Probe uniqueDishAnchorProbe(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return Probe.EMPTY;
        }
        AiResultAnchor picked = null;
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            String spt = nullToEmpty(a.getSourcePlanType());
            if (!DishProfitDrilldownMatrix.isDishAnchorSourcePlanType(spt)) {
                continue;
            }
            if (picked != null) {
                return Probe.EMPTY;
            }
            picked = a;
        }
        if (picked == null) {
            return Probe.EMPTY;
        }
        if (!StringUtils.hasText(picked.getEntityName())
                && !StringUtils.hasText(picked.getEntityId())) {
            return Probe.EMPTY;
        }
        String nm = picked.getEntityName() != null ? picked.getEntityName().trim() : "";
        String did = StringUtils.hasText(picked.getEntityId()) ? picked.getEntityId().trim() : null;
        return new Probe(AiResultAnchor.ENTITY_TYPE_DISH, nm, did);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
