package com.nongxinle.service;

import java.util.Map;

/**
 * 按采购员或供货商 ID 拉取采购明细树（disGetPurchaseDetaiTypeWithId）。
 */
public interface GbDistributerPurchaseBatchDisPurchaseDetaiTypeWithIdService {

    /**
     * @param type 0 自采（purUserId），1 订货（supplierId）
     * @return 放入 R.data 的 map；type 非 0/1 时返回 null
     */
    Map<String, Object> buildPurchaseDetaiTypeWithId(Integer disId, String purUserId, Integer type,
                                                       String startDate, String stopDate, String supplierId);
}
