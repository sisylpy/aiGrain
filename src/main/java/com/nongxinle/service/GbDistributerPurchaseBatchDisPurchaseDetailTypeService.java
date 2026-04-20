package com.nongxinle.service;

import java.util.Map;

/**
 * 批发商按采购类型（自采 / 订货）查看采购明细结构（disGetPurchaseDetailType）。
 */
public interface GbDistributerPurchaseBatchDisPurchaseDetailTypeService {

    Map<String, Object> buildPurchaseDetailType(Integer disId, String purUserIds, Integer type, Integer greatId,
                                               String startDate, String stopDate, String supplierIds);
}
