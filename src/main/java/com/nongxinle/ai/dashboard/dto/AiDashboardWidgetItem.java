package com.nongxinle.ai.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 关注卡片渲染结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDashboardWidgetItem {
    private Long widgetId;
    private String widgetType;
    private String title;
    private Integer position;
    private boolean enabled;
    private String status;
    private String errorMessage;
    private Map<String, String> config;
    private Map<String, Object> data;
}
