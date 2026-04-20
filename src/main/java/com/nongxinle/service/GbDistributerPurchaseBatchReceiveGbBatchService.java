package com.nongxinle.service;

/**
 * 批发商确认收货批次（receiveGbBatch）。
 */
public interface GbDistributerPurchaseBatchReceiveGbBatchService {

    enum Outcome {
        /** 处理成功 */
        OK,
        /** 批次状态已不是待收货 */
        STATUS_CHANGED,
        /** 无采购商品行 */
        NO_PURCHASE_LINES,
        /** 存在订单已被他人收货等 */
        ORDER_NOT_WAIT_RECEIVE
    }

    Outcome receiveGbBatch(Integer batchId);
}
