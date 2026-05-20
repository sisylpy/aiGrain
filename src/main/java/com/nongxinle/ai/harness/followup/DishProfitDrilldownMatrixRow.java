package com.nongxinle.ai.harness.followup;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 菜品毛利下钻矩阵单行（契约见 {@code docs/ai/dish-profit-drilldown-matrix-contract.md}）。
 * 不做 NL、不读用户原文、不调用 LLM。
 */
@Value
@Builder
public class DishProfitDrilldownMatrixRow {

    /** 文档行号，如 {@code DP-R0a}；Harness 对照用。 */
    String rowId;

    /**
     * {@code FIRST_TURN}：首轮 structuredIntentDetailWire → planType；
     * {@code DISH_ANCHOR_FOLLOW_UP}：上一轮 DISH 锚 + detailWanted 追问。
     */
    String rowKind;

    /** 追问行 capabilityId；首轮行为 null。 */
    String capabilityId;

    /** 上一轮锚类型前提；首轮为 {@code NONE}，追问为 {@code DISH}。 */
    String anchorType;

    /** 追问行：允许的上一轮 planType（{@link DishProfitDrilldownMatrix#dishAnchorSourcePlanTypes()} 子集）。 */
    Set<String> allowedPriorFramePlanTypes;

    String queryObject;
    String operation;
    String metric;
    String detailWanted;
    String anchorPolicy;
    String structuredIntentDetailWire;
    String targetDishProfitPlanType;

    /** 本轮 AnswerPlan 是否在服务端沉淀 DISH {@code resultAnchors}。 */
    boolean emitsDishResultAnchor;
}
