package com.nongxinle.ai.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 模块渲染结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDashboardModuleItem {
    private Long moduleId;
    private String moduleKey;
    private String moduleTitle;
    private String moduleType;
    private Integer position;
    private boolean enabled;
    private boolean removable;
    private boolean configurable;
    private String status;
    private String errorMessage;
    private Map<String, Object> config;
    private Map<String, Object> payload;
}
