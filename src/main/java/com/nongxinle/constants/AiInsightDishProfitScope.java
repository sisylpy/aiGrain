package com.nongxinle.constants;

/**
 * AI 菜品毛利 / buildInsight「父部门锚点」特殊值：不可用真实 gb_department 主键，避免与用户部门 ID 冲突。
 *
 * <p>{@link #DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID}：
 * 在 {@link com.nongxinle.service.impl.GbDepFoodBusinessInsightServiceImpl} / 成本报表
 * {@link com.nongxinle.service.impl.GbDishCostAnalysisServiceImpl} 中表示按 {@code distributerId (disId)} 聚合
 * 该集团 {@code queryGroupDepsByDisId} 下<strong>全部</strong>子公司部门销量与出库，
 * <strong>不得</strong>把集团管理员当前 {@code departmentId} 当成门店锚点。</p>
 */
public final class AiInsightDishProfitScope {

    private AiInsightDishProfitScope() {
    }

    /**
     * 负值占位：业务库部门 ID 通常为自增正值。
     */
    public static final int DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID = -988_001_003;

    public static boolean isGroupWideMendianAggregateUnderDis(Integer depFatherId) {
        return depFatherId != null && depFatherId.intValue() == DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID;
    }
}
