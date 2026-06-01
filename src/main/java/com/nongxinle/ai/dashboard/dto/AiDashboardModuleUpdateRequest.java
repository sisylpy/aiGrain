package com.nongxinle.ai.dashboard.dto;

import lombok.Data;

/**
 * 模块更新请求
 */
@Data
public class AiDashboardModuleUpdateRequest {
    private Long userId;
    private String moduleTitle;
    private Boolean enabled;
    private Integer position;
    private String config;
}
