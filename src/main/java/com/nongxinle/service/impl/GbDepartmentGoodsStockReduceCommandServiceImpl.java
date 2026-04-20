package com.nongxinle.service.impl;

import com.nongxinle.dto.GbDepGoodsStockAdjustResult;
import com.nongxinle.service.GbDepartmentGoodsStockLedgerService;
import com.nongxinle.service.GbDepartmentGoodsStockReduceCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GbDepartmentGoodsStockReduceCommandServiceImpl implements GbDepartmentGoodsStockReduceCommandService {

    private final GbDepartmentGoodsStockLedgerService gbDepartmentGoodsStockLedgerService;

    @Override
    public GbDepGoodsStockAdjustResult removeReduceItem(Integer id) {
        GbDepGoodsStockAdjustResult result = gbDepartmentGoodsStockLedgerService.removeReduceAndRevert(id);
        if (!result.isOk()) {
            log.warn("removeReduceItem failed id={} code={} message={}", id, result.getCode(), result.getMessage());
        }
        return result;
    }
}
