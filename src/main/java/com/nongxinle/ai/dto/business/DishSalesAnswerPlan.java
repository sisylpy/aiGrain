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
 * 菜品销量/销售额排行与单菜销量：服务端生成的回答计划（Composer 只读本对象；Harness / Debug）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishSalesAnswerPlan {

    /** 单菜销售卡片（小程序）；planType 仍为 {@link #TYPE_DISH_SALES_SINGLE_DISH}。 */
    public static final String CARD_TYPE_DISH_SALES = "DISH_SALES_CARD";

    /** 菜品销量/销售额排行卡片（多菜列表）；planType 为 {@code DISH_SALES_*_RANKING_*}。 */
    public static final String CARD_TYPE_DISH_SALES_RANKING = "DISH_SALES_RANKING_CARD";

    public static final String RANKING_TYPE_COUNT_HIGH = "COUNT_HIGH";
    public static final String RANKING_TYPE_AMOUNT_HIGH = "AMOUNT_HIGH";
    public static final String RANKING_TYPE_COUNT_LOW = "COUNT_LOW";

    public static final String TYPE_DISH_SALES_COUNT_RANKING_HIGH = "DISH_SALES_COUNT_RANKING_HIGH";
    public static final String TYPE_DISH_SALES_AMOUNT_RANKING_HIGH = "DISH_SALES_AMOUNT_RANKING_HIGH";
    public static final String TYPE_DISH_SALES_COUNT_RANKING_LOW = "DISH_SALES_COUNT_RANKING_LOW";
    /** 排行 wire 命中但区间内无销量/销售额证据；不生成 rankingRows / resultAnchor。 */
    public static final String TYPE_DISH_SALES_RANKING_NO_DATA = "DISH_SALES_RANKING_NO_DATA";
    /** 点名菜销量/份数（单菜，非排行表）。 */
    public static final String TYPE_DISH_SALES_SINGLE_DISH = "DISH_SALES_SINGLE_DISH";

    public static final String METRIC_COUNT_HIGH = "COUNT_HIGH";
    public static final String METRIC_AMOUNT_HIGH = "AMOUNT_HIGH";
    public static final String METRIC_COUNT_LOW = "COUNT_LOW";
    public static final String METRIC_SINGLE_DISH = "SINGLE_DISH";

    @JSONField(name = "type")
    private String planType;

    private String metricType;

    private String scopeLabel;
    private String timeLabel;

    @Builder.Default
    private List<Map<String, Object>> rankingRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> dataCoverage = new LinkedHashMap<>();

    @Builder.Default
    private List<String> limitations = new ArrayList<>();

    private String summary;

    @Builder.Default
    private List<AiResultAnchor> resultAnchors = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
