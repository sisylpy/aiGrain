package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜品毛利分析结构化输出（SSE {@code answer_delta.data.dishProfitOverview}）。
 * 数据来源：旧版 {@code GbDepFoodBusinessInsightService#buildInsight} 衍生。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDishProfitOverviewResult {

    @Builder.Default
    private String agentName = "DishProfitAgent";

    private String summary;

    private String statStartDate;

    private String statEndDate;

    /** 透视范围：`GROUP` / `REGION` / `STORE`（与会话组织视角对齐）。 */
    private String scopeType;

    /** 可读范围文案，例如「集团范围」。 */
    private String scopeName;

    /**
     * 集团/区域广角时开篇话术（与经营概览 queryScopeBanner 对齐），含可见门店家数与店名枚举。
     */
    private String queryScopeBanner;

    /**
     * 解析口径下可见门店根（来自 {@code AiResolvedQueryContext.orgScope.visibleStores}，与 covered/missing 分列）。
     */
    @Builder.Default
    private List<AiOverviewVisibleStoreItem> visibleStores = new ArrayList<>();

    /** true 时 {@link #grossProfitRate} 为粗算参考值，非最终可审计毛利结论。 */
    private boolean grossProfitRateUncertain;

    private int visibleStoreCount = 0;

    private int dataAvailableStoreCount = 0;

    private int dataMissingStoreCount = 0;

    @Builder.Default
    private List<AiOverviewVisibleStoreItem> coveredStores = new ArrayList<>();

    @Builder.Default
    private List<AiOverviewStoreIssueItem> dataMissingStores = new ArrayList<>();

    private int dishCount;

    /** 标价收入汇总（元，plain string）。 */
    private String totalDishSalesAmount;

    /** 理论成本汇总（元）。 */
    private String totalTheoreticalCost;

    /** 实际成本汇总 type1（元）。 */
    private String totalActualCost;

    /** 综合毛利额（元）：收入 − type1 实际。 */
    private String grossProfitAmount;

    /** 综合毛利率可读串或「暂不适用」。 */
    private String grossProfitRate;

    /**
     * 与 {@link #reliableProfitDishes} 同源（兼容旧字段）：成本数据相对完整、毛利率可信、表现较好的菜。
     */
    @Builder.Default
    private List<AiDishProfitDishBrief> topProfitDishes = new ArrayList<>();

    /** 低毛利或实际成本相对理论明显偏高（仅成本口径可信的菜品）。 */
    @Builder.Default
    private List<AiDishProfitDishBrief> lowProfitDishes = new ArrayList<>();

    /** BOM/出库不全等导致毛利率不可信（如表面 100%）的菜品，勿与「毛利较好」混用。 */
    @Builder.Default
    private List<AiDishProfitDishBrief> costDataIncompleteDishes = new ArrayList<>();

    @Builder.Default
    private List<AiDishProfitDishBrief> reliableProfitDishes = new ArrayList<>();

    @Builder.Default
    private List<AiDishProfitDishBrief> abnormalDishes = new ArrayList<>();

    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    /** ok | warning | data_incomplete */
    private String riskLevel;
}
