package com.nongxinle.service;

import java.util.Map;

/**
 * 菜单类别下菜品经营列表（业务接口，非 AI）：分类内菜品销量、成本、毛利与四象限角色。
 */
public interface GbMenuCategoryFoodBusinessListService {

    /**
     * @param distributerId 配送商 id
     * @param scopeMode     {@code GROUP} 或 {@code STORE}
     * @param departmentId  {@code scopeMode=STORE} 时必填
     * @param categoryId    菜品分类 id（父级 gb_distributer_food id）
     * @param days          统计天数，默认 30
     * @param keyword       菜品名模糊搜索，可空
     * @param roleFilter    {@code ALL|STAR|TRAFFIC|POTENTIAL|WATCH}，可空默认 ALL
     * @param sortBy        排序字段，默认 salesCount
     * @param sortOrder     {@code ASC|DESC}，默认 DESC
     */
    Map<String, Object> buildFoodList(
            Integer distributerId,
            String scopeMode,
            Integer departmentId,
            Integer categoryId,
            int days,
            String keyword,
            String roleFilter,
            String sortBy,
            String sortOrder);
}
