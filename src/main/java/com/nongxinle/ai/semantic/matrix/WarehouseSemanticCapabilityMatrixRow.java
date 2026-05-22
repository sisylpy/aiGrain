package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/**
 * 库房库存 Matrix 单行：wire / planType 注册（完整问题）。
 * 契约见 {@code docs/ai/inventory-domain-capability-matrix.md}。
 */
@Value
@Builder
public class WarehouseSemanticCapabilityMatrixRow {

    String rowId;

    String queryObject;
    String operation;
    String metric;

    /** 库存侧切面：OVERVIEW / STORE_RANKING / GOODS_RANKING / LOW_STOCK / NEAR_EXPIRY 等。 */
    String stockFacet;

    String structuredIntentDetailWire;
    String targetWarehousePlanType;

    String knownGapCode;
}
