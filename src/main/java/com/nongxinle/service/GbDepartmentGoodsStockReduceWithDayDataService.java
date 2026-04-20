package com.nongxinle.service;

import java.util.Map;

/**
 * 「按日损耗 + 采购 + 菜品原料」看板数据，原 {@code GbDepartmentGoodsStockReduceController#getGoodsReduceWithDayData}。
 */
public interface GbDepartmentGoodsStockReduceWithDayDataService {

    /**
     * @throws IllegalArgumentException 商品不存在、没有数据
     */
    Map<String, Object> buildReduceWithDayData(String startDate, String stopDate, Integer disGoodsId, String searchDepId);

    /**
     * 指定批发商商品、自然日，汇总「菜品销售」写入的 gb_dep_food_goods_sales 消耗量（与 Excel 上传逻辑同源）。
     */
    double sumFoodGoodsSalesIngredient(Integer disGoodsId, String fullDate, String searchDepId);
}
