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
 * 出库 / 核销 Harness：服务端生成的回答计划（Replay / Debug 同源；Composer 后续只读）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReduceAnswerPlan {

    public static final String TYPE_STOCK_REDUCE_OVERVIEW = "STOCK_REDUCE_OVERVIEW";
    public static final String TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW = "STOCK_REDUCE_PRODUCTION_OVERVIEW";
    public static final String TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW = "STOCK_REDUCE_OUTPUT_OVERVIEW";
    public static final String TYPE_STOCK_REDUCE_WASTE_OVERVIEW = "STOCK_REDUCE_WASTE_OVERVIEW";
    public static final String TYPE_STOCK_REDUCE_LOSS_OVERVIEW = "STOCK_REDUCE_LOSS_OVERVIEW";
    public static final String TYPE_STOCK_REDUCE_RETURN_OVERVIEW = "STOCK_REDUCE_RETURN_OVERVIEW";
    public static final String TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING = "STOCK_REDUCE_GOODS_AMOUNT_RANKING";
    public static final String TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING = "STOCK_REDUCE_GOODS_COUNT_RANKING";
    /** 多店内按门店汇总出库/核销四类金额合计后对比（逐店复用与单店 harness 相同的父部门汇总调用）。 */
    public static final String TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING = "STOCK_REDUCE_STORE_AMOUNT_RANKING";

    public static final String REDUCE_TYPE_ALL = "ALL";
    public static final String REDUCE_TYPE_TYPE1 = "TYPE1";
    public static final String REDUCE_TYPE_TYPE2 = "TYPE2";
    public static final String REDUCE_TYPE_TYPE3 = "TYPE3";
    public static final String REDUCE_TYPE_TYPE4 = "TYPE4";

    @JSONField(name = "type")
    private String planType;

    private String scopeLabel;
    private String timeLabel;

    /** 收窄口径：ALL / TYPE1…TYPE4，便于 Debug 与后续 Composer。 */
    private String reduceType;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
