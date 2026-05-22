package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/**
 * 菜品毛利 Matrix 单行：wire / planType / capability 注册。
 * 契约见 {@code docs/ai/dish-profit-domain-capability-matrix.md}。
 */
@Value
@Builder
public class DishProfitSemanticCapabilityMatrixRow {

    String rowId;

    /** 追问 capability 行非空；首轮 structured wire 行为 null。 */
    String capabilityId;

    /** 上一轮锚类型前提，首轮为 {@code NONE}，追问 capability 行为 {@code DISH}。 */
    String anchorType;

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
