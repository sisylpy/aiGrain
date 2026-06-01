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

/** 原料 → 受影响菜品可支撑分析（{@code warehouse.goods_supported_dish_cover.v1}）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsSupportedDishCoverAnswerPlan {

    public static final String TYPE = "GOODS_SUPPORTED_DISH_COVER";
    public static final String CONTRACT_ID = "warehouse.goods_supported_dish_cover.v1";
    public static final String CARD_TYPE = "GOODS_SUPPORTED_DISH_COVER_CARD";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    @JSONField(name = "type")
    private String planType;

    private String contractId;
    private String status;

    private String goodsName;
    private Integer disGoodsId;
    private String scopeLabel;

    /** 原料现量快照文案。 */
    private String stockSnapshotLabel;

    /** 销量基线区间文案（默认近 7 天）。 */
    private String salesBaselineLabel;

    private String currentStockQty;
    private String stockUnit;

    /** 按 coverDays 升序，最先受影响的菜名。 */
    private String firstImpactedDishName;
    private String firstImpactedCoverDays;

    @Builder.Default
    private List<Map<String, Object>> dishRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<String> knownGaps = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
