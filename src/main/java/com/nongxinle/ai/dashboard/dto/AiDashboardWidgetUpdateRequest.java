package com.nongxinle.ai.dashboard.dto;

import lombok.Data;

import java.util.Map;

/**
 * 修改关注卡片请求
 */
@Data
public class AiDashboardWidgetUpdateRequest {
    private Long userId;
    private String title;
    private Integer position;
    private Boolean enabled;
    private Map<String, String> config;
}
