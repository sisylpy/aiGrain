package com.nongxinle.service;

import java.util.Map;

/**
 * 菜单类别经营概览（业务接口，非 AI）：按分类汇总销量、成本、毛利与四象限数量。
 */
public interface GbMenuCategoryBusinessOverviewService {

    /**
     * @param distributerId 配送商 id
     * @param scopeMode     {@code GROUP} 或 {@code STORE}
     * @param departmentId  {@code scopeMode=STORE} 时必填，门店根部门 id
     * @param days          统计天数，默认 30
     */
    Map<String, Object> buildOverview(Integer distributerId, String scopeMode, Integer departmentId, int days);
}
