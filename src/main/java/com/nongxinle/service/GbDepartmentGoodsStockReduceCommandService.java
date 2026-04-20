package com.nongxinle.service;

import com.nongxinle.dto.GbDepGoodsStockAdjustResult;

/**
 * 部门库存损耗写操作（撤销损耗单等），委托 {@link GbDepartmentGoodsStockLedgerService}。
 */
public interface GbDepartmentGoodsStockReduceCommandService {

    /**
     * 删除一条损耗记录并回滚台账，语义同原 {@code /deleteReduceItem/{id}}。
     */
    GbDepGoodsStockAdjustResult removeReduceItem(Integer id);
}
