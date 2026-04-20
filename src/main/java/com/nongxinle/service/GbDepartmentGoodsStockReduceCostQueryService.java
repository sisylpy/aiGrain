package com.nongxinle.service;

import java.util.Map;

/**
 * 商品成本统计与分页（原 {@code GbDepartmentGoodsStockReduceController} 中 reduce 聚合查询）。
 */
public interface GbDepartmentGoodsStockReduceCostQueryService {

    /**
     * @throws IllegalArgumentException 没有数据
     */
    Map<String, Object> buildGoodsCostStatistics(String startDate, String stopDate, Integer disId, Integer greatId,
            String searchDepId);

    Map<String, Object> buildGoodsCostPage(String startDate, String stopDate, Integer disId, String type,
            String searchDepId, Integer page, Integer limit, Integer greatId);
}
