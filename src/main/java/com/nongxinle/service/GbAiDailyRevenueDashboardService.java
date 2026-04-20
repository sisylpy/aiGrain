package com.nongxinle.service;

import com.nongxinle.entity.GbAiRestaurantProfileEntity;

import java.util.Map;

/**
 * 日营业额经营看板：聚合 stats、成本核销与画像，组装 dashboard + 扁平 stats。
 */
public interface GbAiDailyRevenueDashboardService {

    /**
     * 构建 /stats 接口完整 data（含 dashboard、stats、profile）。
     *
     * @param departmentId 部门/餐厅 ID
     * @param profile      已加载的餐厅画像
     * @param stats        {@link GbAiDailyRevenueService#getStatsByDepartmentId(Long)} 的原始结果
     */
    Map<String, Object> buildStatsDashboard(Long departmentId, GbAiRestaurantProfileEntity profile, Map<String, Object> stats);
}
