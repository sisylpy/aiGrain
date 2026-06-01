package com.nongxinle.ai.dashboard.dto;

import lombok.Data;

import java.util.Map;

/**
 * 添加关注卡片请求
 */
@Data
public class AiDashboardWidgetAddRequest {
    private Long userId;
    private Long dashboardId;
    private Long moduleId;
    private String widgetType;
    private String title;
    private Integer position;
    private Map<String, String> config;
}
