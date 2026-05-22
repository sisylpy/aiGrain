package com.nongxinle.ai.semantic.capability;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class SemanticContextFrameBuilder {

    private SemanticContextFrameBuilder() {
    }

    public static SemanticContextFrame fromPreviousTurn(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return SemanticContextFrame.builder()
                    .purchasePath(false)
                    .previousResultAnchors(List.of())
                    .build();
        }
        String path = previousTurn.getLastPathCode();
        String wireRaw = previousTurn.getLastStructuredIntentDetail();
        String pst = previousTurn.getLastPurchaseSourceType();
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wireRaw);
        String wire = StringUtils.hasText(canon) ? canon : (wireRaw != null ? wireRaw.trim() : "");
        boolean dishProfitPath = AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(path);
        String planType;
        if (dishProfitPath) {
            planType = DishProfitSemanticCapabilityMatrix.targetPlanTypeForWire(wire);
        } else {
            planType =
                    PurchaseAnswerPlanBuilder.resolvePlanType(
                            wire, pst == null ? AiQuerySemanticLexicon.SOURCE_ALL : pst);
        }
        boolean purchasePath = AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(path);
        List<AiResultAnchor> anchors =
                previousTurn.getLastResultAnchors() == null
                        ? List.of()
                        : new ArrayList<>(previousTurn.getLastResultAnchors());
        planType = resolveFramePlanTypeFromPreviousAnchors(anchors, planType);
        return SemanticContextFrame.builder()
                .lastPathCode(path)
                .lastStructuredIntentDetailWire(StringUtils.hasText(wire) ? wire : null)
                .lastPurchaseSourceType(StringUtils.hasText(pst) ? pst.trim() : null)
                .framePlanType(planType)
                .purchasePath(purchasePath)
                .previousResultAnchors(anchors)
                .build();
    }

    /**
     * 追问帧 planType：优先沿用上一轮 GOODS/SUPPLIER 结果锚的 {@code sourcePlanType}，
     * 避免 wire 仍为 {@code purchase_source_goods_query} 时落回 OVERVIEW 导致 Registry 失配。
     */
    private static String resolveFramePlanTypeFromPreviousAnchors(
            List<AiResultAnchor> anchors, String wireDerivedPlanType) {
        if (anchors == null || anchors.isEmpty()) {
            return wireDerivedPlanType;
        }
        String dishPlan = resolveDishAnchorExecutionFramePlanFromAnchors(anchors);
        if (StringUtils.hasText(dishPlan)) {
            return dishPlan;
        }
        String goodsPlan = resolveGoodsAnchorExecutionFramePlanFromAnchors(anchors);
        if (StringUtils.hasText(goodsPlan)) {
            return goodsPlan;
        }
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(
                    nullToEmpty(a.getSourcePlanType()))) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING;
            }
        }
        return wireDerivedPlanType;
    }

    private static String resolveDishAnchorExecutionFramePlanFromAnchors(List<AiResultAnchor> anchors) {
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            String spt = nullToEmpty(a.getSourcePlanType());
            if (!DishProfitSemanticCapabilityMatrix.isDishAnchorSourcePlanType(spt)) {
                continue;
            }
            Integer rk = a.getRank();
            boolean rankOne = rk != null && rk == 1;
            boolean singleUnranked = rk == null && anchors.size() == 1;
            if (rankOne || singleUnranked) {
                return spt;
            }
        }
        return null;
    }

    private static String resolveGoodsAnchorExecutionFramePlanFromAnchors(List<AiResultAnchor> anchors) {
        for (AiResultAnchor a : anchors) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            String spt = nullToEmpty(a.getSourcePlanType());
            if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(spt)
                    || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(spt)
                    || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(spt)
                    || PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(spt)) {
                Integer rk = a.getRank();
                boolean rankOne = rk != null && rk == 1;
                boolean singleUnranked = rk == null && anchors.size() == 1;
                boolean sourceBreakdownUnranked =
                        PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(spt);
                boolean supplierGoodsDetailUnranked =
                        PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(spt);
                if (rankOne || singleUnranked || sourceBreakdownUnranked || supplierGoodsDetailUnranked) {
                    return spt;
                }
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
