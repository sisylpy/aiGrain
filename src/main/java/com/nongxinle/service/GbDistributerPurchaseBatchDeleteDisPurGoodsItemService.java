package com.nongxinle.service;

/**
 * 删除订货批次下的采购商品项（deleteDisPurBatchGbItem）。
 */
public interface GbDistributerPurchaseBatchDeleteDisPurGoodsItemService {

    /**
     * @return true 处理成功；false 表示前后端状态不一致需刷新（原 R.error -1）
     */
    boolean deleteDisPurBatchGbItem(Integer purGoodsId);
}
