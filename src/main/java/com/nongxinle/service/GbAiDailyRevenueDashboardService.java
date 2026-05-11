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
     * @param departmentFatherId 父部门/餐厅 ID（与日营收 department_id、核销 father_id 一致）
     * @param profile            已加载的餐厅画像
     * @param stats              {@link GbAiDailyRevenueService#getStatsByDepartmentId(Long, String, String)} 的原始结果
     * @param startDate          可选，与统计查询一致的 yyyy-MM-dd
     * @param endDate            可选
     */
    Map<String, Object> buildStatsDashboard(Long departmentFatherId, GbAiRestaurantProfileEntity profile,
                                          Map<String, Object> stats, String startDate, String endDate);

    /**
     * 集团多部门：与日营收单行聚合同源字段（{@code selectGroupIncomeAggregateForDepartmentIds}），扁平中文 stats。
     * 不合并核销/画像成本；利润率、盈亏等为占位说明。
     *
     * @param groupAggRow          mapper 聚合行（含 totalGrossRevenue、totalOrders、distinctRecordDates 等）
     * @param visibleDeptNodeCount AiQueryScope.resolvedDepartmentIds 大小（可见部门节点数）
     * @param parentStoreCountHint 范围内父级门店数，可为 null
     */
    Map<String, Object> buildGroupWideIncomeFlattened(Map<String, Object> groupAggRow,
                                                      int visibleDeptNodeCount,
                                                      Integer parentStoreCountHint,
                                                      String startDate,
                                                      String endDate);
}
