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
 * 菜单经营顾问 AnswerPlan：老板视角经营建议层（与 {@link DishProfitAnswerPlan} 独立）。
 * Composer / Renderer 只读本对象，不直读 toolResults。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuOperationAnswerPlan {

    public static final String TYPE_MENU_OPERATION_OVERVIEW = "MENU_OPERATION_OVERVIEW";
    public static final String TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT = "MENU_DISH_HIGH_SALES_LOW_PROFIT";
    public static final String TYPE_MENU_ACTION_RECOMMENDATION = "MENU_ACTION_RECOMMENDATION";

    /** 前端固定业务卡片：菜单四象限饼图 + 分类列表（仅展示，不参与业务判断）。 */
    public static final String CARD_TYPE_MENU_PORTFOLIO_QUADRANT = "MENU_PORTFOLIO_QUADRANT_CARD";
    /** 前端固定业务卡片：畅销低利菜表格（high_sales_low_profit）。 */
    public static final String CARD_TYPE_MENU_HIGH_SALES_LOW_MARGIN = "MENU_HIGH_SALES_LOW_MARGIN_CARD";
    /** 前端固定业务卡片：菜单调整行动清单。 */
    public static final String CARD_TYPE_MENU_ACTION_RECOMMENDATION = "MENU_ACTION_RECOMMENDATION_CARD";
    public static final String CHART_TYPE_PIE = "PIE";
    public static final String CHART_TYPE_TABLE = "TABLE";
    public static final String CHART_TYPE_LIST = "LIST";
    /** {@link #menuPortfolioClassification} 在 AnswerPlan 内的 dataRef 键名。 */
    public static final String DATA_REF_MENU_PORTFOLIO_CLASSIFICATION = "menuPortfolioClassification";
    /** {@link #riskDishes} 在 AnswerPlan 内的 dataRef 键名（high_sales_low_profit 卡）。 */
    public static final String DATA_REF_RISK_DISHES = "riskDishes";
    /** {@link #recommendedActions} 在 AnswerPlan 内的 dataRef 键名（行动建议卡 · 兼容）。 */
    public static final String DATA_REF_RECOMMENDED_ACTIONS = "recommendedActions";
    /** {@link #menuOptimizationPlan} 在 AnswerPlan 内的 dataRef 键名（菜单优化方案卡）。 */
    public static final String DATA_REF_MENU_OPTIMIZATION_PLAN = "menuOptimizationPlan";

    public static final String CHART_TYPE_PLAN = "PLAN";

    public static final String OPT_GROUP_PRIORITY_HANDLE = "PRIORITY_HANDLE";
    public static final String OPT_GROUP_STABLE_PROMOTE = "STABLE_PROMOTE";
    public static final String OPT_GROUP_INCREASE_EXPOSURE = "INCREASE_EXPOSURE";
    public static final String OPT_GROUP_WATCH_ADJUST = "WATCH_ADJUST";

    @JSONField(name = "type")
    private String planType;

    private String timeLabel;
    private String scopeLabel;
    private String statStartDate;
    private String statEndDate;

    @Builder.Default
    private Map<String, Object> summaryFacts = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusDishes = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> riskDishes = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> opportunityDishes = new ArrayList<>();

    @Builder.Default
    private List<MenuOperationRecommendedAction> recommendedActions = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> evidenceRows = new ArrayList<>();

    @Builder.Default
    private List<String> knownGaps = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();

    @Builder.Default
    private List<AiResultAnchor> resultAnchors = new ArrayList<>();

    /**
     * 菜单结构四象限分类（overview / action_recommendation）：明星/引流/潜力/淘汰。
     * 由 Builder 基于 dish_profit_analysis 快照动态阈值计算；Renderer 只读。
     */
    private MenuPortfolioClassification menuPortfolioClassification;

    /**
     * 菜单优化方案（action_recommendation 主链）：优先级分组、分桶菜品与可执行 nextSteps。
     * Builder 确定性生成；CardSupport / Renderer 只读。
     */
    private MenuOptimizationPlan menuOptimizationPlan;

    /**
     * 展示层卡片描述（cardType 仅前端渲染用）；业务事实以 {@link #menuPortfolioClassification} 等字段为准。
     */
    @Builder.Default
    private List<MenuOperationDisplayCard> displayCards = new ArrayList<>();

    public static final String CATEGORY_STAR = "STAR";
    public static final String CATEGORY_TRAFFIC = "TRAFFIC";
    public static final String CATEGORY_POTENTIAL = "POTENTIAL";
    public static final String CATEGORY_ELIMINATE = "ELIMINATE";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuPortfolioClassification {

        private int totalDishCount;
        /** 销量维度指标字段名，如 soldPortionsTotal */
        private String salesMetricName;
        /** 盈利维度指标字段名，如 actualProfitAmount */
        private String profitMetricName;
        private String salesHighThreshold;
        private String profitHighThreshold;
        /** 阈值算法说明，如 median */
        private String thresholdMethod;
        @Builder.Default
        private List<MenuPortfolioCategory> categories = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuPortfolioCategory {

        private String categoryCode;
        private String categoryName;
        private int count;
        /** 占分析菜品数比例，如 20.83% */
        private String ratio;
        private String summary;
        private String recommendedAction;
        @Builder.Default
        private List<MenuPortfolioDish> dishes = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuPortfolioDish {

        private String dishId;
        private String dishName;
        private String salesCount;
        private String salesAmount;
        private String blendedGrossMarginRateOnListPrice;
        private String actualProfitAmount;
        private String actualCostTotalAmount123;
        private String reason;
        private String evidenceRefId;
    }

    /**
     * 菜单优化方案定稿：{@code menu.action.recommendation.v1} 主卡 payload 来源。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuOptimizationPlan {

        private String optimizationSummary;
        @Builder.Default
        private List<MenuOptimizationPriorityGroup> priorityGroups = new ArrayList<>();
        @Builder.Default
        private List<MenuOptimizationDishItem> costReviewDishes = new ArrayList<>();
        @Builder.Default
        private List<MenuOptimizationDishItem> protectDishes = new ArrayList<>();
        @Builder.Default
        private List<MenuOptimizationDishItem> promotionDishes = new ArrayList<>();
        @Builder.Default
        private List<MenuOptimizationDishItem> watchListDishes = new ArrayList<>();
        @Builder.Default
        private List<String> nextSteps = new ArrayList<>();
        @Builder.Default
        private Map<String, Object> capabilityLimits = new LinkedHashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuOptimizationPriorityGroup {

        private String groupCode;
        private String groupName;
        private int priority;
        private String reason;
        private String suggestedAction;
        @Builder.Default
        private List<MenuOptimizationDishItem> dishes = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuOptimizationDishItem {

        private String dishId;
        private String dishName;
        private String quadrantCode;
        private String quadrantName;
        private String soldPortionsTotal;
        private String listPriceRevenue;
        private String blendedGrossMarginRateOnListPrice;
        private String actualProfitAmount;
        private String suggestedActionLabel;
        private String reason;
        private String evidenceRefId;
    }

    /**
     * AnswerPlan 内轻量卡片描述：前端按 cardType 选组件，按 dataRef 解析同 Plan 内业务数据。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuOperationDisplayCard {

        /** 前端组件键，如 {@link #CARD_TYPE_MENU_PORTFOLIO_QUADRANT}。 */
        private String cardType;
        private String title;
        private String subtitle;
        /** 如图表形态：{@link #CHART_TYPE_PIE}。 */
        private String chartType;
        /** AnswerPlan 内字段名，如 {@link #DATA_REF_MENU_PORTFOLIO_CLASSIFICATION}。 */
        private String dataRef;
    }
}
