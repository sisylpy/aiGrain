package com.nongxinle.service;

import java.util.Map;

/**
 * 单菜经营详情（业务接口，非 AI）：与菜单类别菜品列表同口径的经营事实与配料成本行。
 */
public interface GbMenuFoodBusinessDetailService {

    /**
     * @param distributerId 配送商 id
     * @param scopeMode     {@code GROUP} 或 {@code STORE}
     * @param departmentId  {@code scopeMode=STORE} 时必填
     * @param foodId        批发商菜品 id（{@code gb_distributer_food_id}）
     * @param days          统计天数，默认 30；当未传 {@code startDate}/{@code stopDate} 时生效
     * @param startDate     可选，与 {@code stopDate} 同时传入时优先于 {@code days}
     * @param stopDate      可选
     * @param categoryId    可选，仅校验菜品是否属于该分类，不重算四象限/角色
     */
    Map<String, Object> buildDetail(
            Integer distributerId,
            String scopeMode,
            Integer departmentId,
            Integer foodId,
            int days,
            String startDate,
            String stopDate,
            Integer categoryId);
}
