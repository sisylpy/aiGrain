package com.nongxinle.ai.planner;

/**
 * C-16/C-17：采购只读桥接。{@link PurchasePlannerRealReadBridge} 的真实入口为
 * {@link PurchasePlannerRealReadBridge#readWithExecutionContext}；
 * <strong>禁止</strong>解析用户聊天原文、禁止直接拼接 SQL。
 */
@FunctionalInterface
public interface PurchasePlannerReadBridge {

    PurchasePlannerReadResponse readPurchase(PurchasePlannerReadRequest request);
}
