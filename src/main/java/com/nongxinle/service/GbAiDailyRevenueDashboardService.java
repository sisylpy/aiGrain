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
     * @param groupAggRow                    mapper 聚合行（含 totalGrossRevenue、totalOrders、distinctRecordDates 等）
     * @param visibleStoreRootCount           本轮参与汇总的<strong>门店根</strong>数量（与广角 retailAnchors 一致）
     * @param parentStoreCountHint           可选：Scope 侧的父级门店数提示，可为 null（仅兜底展示用）
     * @param startDate                       yyyy-MM-dd
     * @param endDate                         yyyy-MM-dd
     * @param storeRootsWithRecordedRevenue  可选：按<strong>门店根</strong>{@code getStatsByDepartmentId} 统计的、区间内有营业额
     *                                         台账的门店数；非 null 时「数据口径说明」入账句不再使用
     *                                         {@code distinctRecordingDepartments}（展开后的记账部门数），避免写成「几家」错乱
     */
    Map<String, Object> buildGroupWideIncomeFlattened(Map<String, Object> groupAggRow,
                                                      int visibleStoreRootCount,
                                                      Integer parentStoreCountHint,
                                                      String startDate,
                                                      String endDate,
                                                      Integer storeRootsWithRecordedRevenue);
}
