package com.nongxinle.ai.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可添加关注类型条目
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDashboardWidgetCatalogItem {
    private String widgetType;
    private String name;
    private List<MetricItem> metrics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricItem {
        private String label;
        private String value;
    }
}
