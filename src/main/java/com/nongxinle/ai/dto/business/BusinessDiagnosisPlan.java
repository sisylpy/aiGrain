package com.nongxinle.ai.dto.business;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营诊断 Harness：服务端组装的中间计划（Replay / Debug / Composer 同源）。
 * 契约见 {@code docs/ai/business-diagnosis-harness-plan.md}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisPlan {

    public static final String TYPE_BUSINESS_DIAGNOSIS = "BUSINESS_DIAGNOSIS";

    @JSONField(name = "type")
    private String planType;

    private String scopeLabel;
    private String timeLabel;

    /** 汇总级风险：INFO / WARN / HIGH */
    private String riskLevel;

    private OverallSummary overallSummary;

    @Builder.Default
    private List<String> mainFindings = new ArrayList<>();

    @Builder.Default
    private List<DiagnosisRiskItem> riskItems = new ArrayList<>();

    private FocusTargets focusTargets;

    @Builder.Default
    private List<String> actionItems = new ArrayList<>();

    @Builder.Default
    private List<String> sourceTools = new ArrayList<>();

    @Builder.Default
    private List<String> usedTools = new ArrayList<>();

    private DataCompletenessBlock dataCompleteness;

    private DebugRef debugRef;

    private SourceResultSummary sourceResultSummary;

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();

    /**
     * 集团可见门店：老板「先处理哪家店」类问法的门店级排序（规则打分，同源于 Tool 门店覆盖字段）。
     */
    private StorePriorityRankingPlan storePriorityRanking;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorePriorityRankingPlan {
        /** 固定为 {@code STORE_PRIORITY_RANKING}，与 Debug structured 人类码对齐。 */
        private String rankingType;

        @Builder.Default
        private List<StorePriorityFocus> focusStores = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorePriorityFocus {
        private Long storeDepartmentId;
        private String storeName;
        /** 1 = 最先处理 */
        private Integer priorityRank;
        /** HIGH / MEDIUM / LOW */
        private String riskLevel;
        private String reason;

        @Builder.Default
        private Map<String, Object> signals = new LinkedHashMap<>();

        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallSummary {
        private Boolean normalized;
        private Boolean dataSufficient;
        private String headline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosisRiskItem {
        private String level;
        private String domain;
        private String title;
        private String evidence;
        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FocusTargets {
        @Builder.Default
        private List<String> stores = new ArrayList<>();
        @Builder.Default
        private List<String> dishes = new ArrayList<>();
        @Builder.Default
        private List<String> costCategories = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataCompletenessBlock {
        private String purchase;
        private String stockReduce;
        private String dishProfit;
        private String revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DebugRef {
        private String purchaseSnapshotId;
        private String stockReduceSnapshotId;
        private String dishProfitAnswerPlanType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceResultSummary {
        private PurchaseSketch purchase;
        private StockReduceSketch stockReduce;
        private DishProfitSketch dishProfit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseSketch {
        /** 总采购额（元），摘要可读 */
        private Double totalAmount;
        private Double selfPurchaseAmount;
        private Double supplierPurchaseAmount;
        @Builder.Default
        private List<String> riskSignals = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockReduceSketch {
        private Double totalAmount;
        private Double produceAmount;
        private Double wasteAmount;
        private Double lossAmount;
        private Double returnAmount;
        @Builder.Default
        private List<String> riskSignals = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DishProfitSketch {
        private Double salesAmount;
        private Double actualCostAmount;
        /** 综合毛利率（百分点），来自透视 summary 可读串解析或服务端数值 */
        private Double grossMarginRate;
        private String lowestMarginDish;
        @Builder.Default
        private List<String> riskSignals = new ArrayList<>();
    }
}
