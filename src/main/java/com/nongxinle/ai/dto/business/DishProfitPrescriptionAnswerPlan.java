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
 * 单菜利润处方 AnswerPlan：定价 + 成本 + 配料 + 行动建议（独立于 {@link DishCostAnalysisCapabilityResult} 旧成本卡）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishProfitPrescriptionAnswerPlan {

    public static final String TYPE = "DISH_PROFIT_PRESCRIPTION";
    public static final String CONTRACT_ID = "dish.profit.prescription.v1";
    public static final String CARD_TYPE = "DISH_PROFIT_PRESCRIPTION_CARD";
    public static final String FORMULA_ACTUAL_COST123_DIV_TARGET =
            "ACTUAL_COST123_DIV_ONE_MINUS_TARGET";
    public static final String UNIT_PRICE_SOURCE_OUTBOUND_TYPE1_AVG = "OUTBOUND_TYPE1_AVG";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    @JSONField(name = "type")
    private String planType;

    private String contractId;
    private String status;
    private String failureReasonCode;

    private Integer dishId;
    private String dishName;
    private String timeLabel;
    private String scopeLabel;
    private String statStartDate;
    private String statEndDate;

    @Builder.Default
    private Map<String, Object> pricing = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> margin = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> suggestedPrice = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> menuContext = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> diagnosis = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> ingredientRows = new ArrayList<>();

    @Builder.Default
    private List<DishProfitPrescriptionRecommendedAction> recommendedActions = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> capabilityLimits = new LinkedHashMap<>();

    @Builder.Default
    private List<String> knownGaps = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> evidenceRows = new ArrayList<>();

    @Builder.Default
    private List<AiResultAnchor> resultAnchors = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DishProfitPrescriptionRecommendedAction {
        private String actionCode;
        private int priority;
        private Integer disGoodsId;
        private String reasonZh;
    }
}
