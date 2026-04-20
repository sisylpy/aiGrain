package com.nongxinle.service;

import java.util.Map;

/**
 * 菜品成本 / 出库分析：{@code salesDish} 以销售菜品为主；{@code outboundQty} 以出库数量为主。
 */
public interface GbDishCostAnalysisService {

    /**
     * @param reportKind {@code salesDish}（默认）| {@code outboundQty}
     */
    Map<String, Object> buildReport(String startDate, String stopDate, Integer disId, String searchDepId,
            Integer depFatherId, String reportKind);
}
