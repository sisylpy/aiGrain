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

/** 单菜配料可支撑天数 AnswerPlan（P1：复用 dish_cost_analysis 数据，不重算 SQL）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishIngredientCoverAnswerPlan {

    public static final String TYPE = "DISH_INGREDIENT_COVER_DAYS";
    public static final String CONTRACT_ID = "dish.ingredient_cover_days.v1";
    public static final String CARD_TYPE = "DISH_INGREDIENT_COVER_DAYS_CARD";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    @JSONField(name = "type")
    private String planType;

    private String contractId;
    private String status;

    private String dishName;
    private Integer dishId;
    private String scopeLabel;
    private String timeLabel;

    /** {@link com.nongxinle.ai.inventory.InventoryQueryTimeKind} 名称。 */
    private String inventoryQueryTimeKind;

    /** 配料现量快照锚定日（ISO）。 */
    private String asOfDate;

    /** 当前库存口径文案。 */
    private String stockSnapshotLabel;

    /** 日均耗用/销量统计基线区间文案。 */
    private String periodFlowLabel;

    /** 整道菜按最短板配料推算还能支撑的天数（可为 null）。 */
    private String dishCoverDays;

    /** 最短板配料名称。 */
    private String bottleneckIngredientName;

    /** 最短板配料可支撑天数。 */
    private String bottleneckCoverDays;

    @Builder.Default
    private List<Map<String, Object>> ingredientRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<String> knownGaps = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
