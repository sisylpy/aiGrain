package com.nongxinle.service;

import com.nongxinle.entity.GbDistributerGoodsEntity;

public interface GbDepartmentGoodsStockReducePurFenxiService {
    GbDistributerGoodsEntity buildPurGoodsFenxi(Integer disGoodsId, String startDate, String stopDate,
            Integer supplierId, Integer purUserId);
}
