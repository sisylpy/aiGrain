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
 * 库房库存现量 Harness：服务端生成的回答计划（Replay / Debug 同源；Composer 只读）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseAnswerPlan {

    public static final String TYPE_WAREHOUSE_STOCK_OVERVIEW = "WAREHOUSE_STOCK_OVERVIEW";
    public static final String TYPE_WAREHOUSE_STORE_AMOUNT_RANKING = "WAREHOUSE_STORE_AMOUNT_RANKING";
    public static final String TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH = "WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH";
    public static final String TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW = "WAREHOUSE_GOODS_AMOUNT_RANKING_LOW";
    public static final String TYPE_WAREHOUSE_LOW_STOCK_RISK = "WAREHOUSE_LOW_STOCK_RISK";

    @JSONField(name = "type")
    private String planType;

    private String scopeLabel;
    private String timeLabel;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
