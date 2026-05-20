package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营概览聚合：MultiAgent 四域产物，供 Composer / Debug 同源只读消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessOverviewAnswerPlan {

    public static final String PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1 = "BUSINESS_OVERVIEW_MULTI_AGENT_V1";

    /** 经营诊断 path：复用四域专线 MultiAgent（与 OVERVIEW v1 结构相同，仅 planType/debug.source 区分表面）。 */
    public static final String PLAN_TYPE_BUSINESS_DIAGNOSIS_MULTI_AGENT_V1 = "BUSINESS_DIAGNOSIS_MULTI_AGENT_V1";

    private String planType;

    private String timeLabel;
    private String scopeLabel;

    /** 子域 AnswerPlan 或降级摘要对象 */
    private DailyRevenueAnswerPlan revenueSummary;
    private PurchaseAnswerPlan purchaseSummary;
    private StockReduceAnswerPlan stockReduceSummary;
    private DishProfitAnswerPlan dishProfitSummary;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> missingSections = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
