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
 * 日营业额 / 营收 Harness：服务端生成的回答计划（Replay / Debug 同源；Composer 后续只读）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueAnswerPlan {

    public static final String TYPE_REVENUE_OVERVIEW = "REVENUE_OVERVIEW";
    public static final String TYPE_REVENUE_DINE_IN_OVERVIEW = "REVENUE_DINE_IN_OVERVIEW";
    public static final String TYPE_REVENUE_TAKEOUT_OVERVIEW = "REVENUE_TAKEOUT_OVERVIEW";
    public static final String TYPE_REVENUE_PLATFORM_RANKING = "REVENUE_PLATFORM_RANKING";
    public static final String TYPE_REVENUE_ORDER_COUNT_OVERVIEW = "REVENUE_ORDER_COUNT_OVERVIEW";
    public static final String TYPE_REVENUE_CUSTOMER_COUNT_OVERVIEW = "REVENUE_CUSTOMER_COUNT_OVERVIEW";
    public static final String TYPE_REVENUE_AVERAGE_ORDER_VALUE = "REVENUE_AVERAGE_ORDER_VALUE";
    public static final String TYPE_REVENUE_DAILY_AMOUNT_RANKING = "REVENUE_DAILY_AMOUNT_RANKING";
    public static final String TYPE_REVENUE_STORE_AMOUNT_RANKING = "REVENUE_STORE_AMOUNT_RANKING";
    public static final String TYPE_REVENUE_CHANNEL_BREAKDOWN = "REVENUE_CHANNEL_BREAKDOWN";

    public static final String CHANNEL_ALL = "ALL";
    public static final String CHANNEL_DINE_IN = "DINE_IN";
    public static final String CHANNEL_TAKEOUT = "TAKEOUT";
    public static final String CHANNEL_PLATFORM = "PLATFORM";
    public static final String CHANNEL_MIXED_BREAKDOWN = "MIXED_BREAKDOWN";

    @JSONField(name = "type")
    private String planType;

    private String scopeLabel;
    private String timeLabel;

    /** ALL / DINE_IN / TAKEOUT / PLATFORM / MIXED_BREAKDOWN — 与本轮收窄口径对齐（Tool 原始字段镜像）。 */
    private String revenueChannel;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
