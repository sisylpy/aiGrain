package com.nongxinle.service;

import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;

import java.util.List;
import java.util.Map;

/**
 * 部门菜品经营分析：周销量、标价收入、与菜品成本报表对齐的毛利及区间损耗率。
 */
public interface GbDepFoodBusinessInsightService {

    /**
     * @param disId        分销商 id
     * @param depFatherId  部门父级 id（与 {@code gb_dep_food} / 成本报表一致）
     * @param startDate    yyyy-MM-dd
     * @param stopDate     yyyy-MM-dd
     */
    Map<String, Object> buildInsight(Integer disId, Integer depFatherId, String startDate, String stopDate);

    /**
     * 在 {@code gb_dep_food} 列表上写入 {@code gbDfBusinessInsight}，并将 {@code gbDfSalesAmount} 与按子部门口径的总销量对齐；
     * 返回需放在 {@code R} 中与 {@code data} 并列的区间级字段（含 {@code businessInsightSummary} 顶部汇总、{@code scopeOutboundSubtotals} 出库损耗率、列说明等）。
     */
    Map<String, Object> attachToFoodRows(List<GbDepFoodEntity> foods,
            Integer disId, Integer depFatherId, String startDate, String stopDate);

    /**
     * 在 {@code /depGetAllFood} 的配方行上挂本区间出库统计：type1 与 1+2+3 的汇总、出库单价、2+3 的差分数量/成本。
     */
    void enrichFoodGoodsOutboundStats(List<GbDistributerFoodGoodsEntity> recipeLines, Integer disId, Integer depFatherId,
            String startDate, String stopDate);
}
