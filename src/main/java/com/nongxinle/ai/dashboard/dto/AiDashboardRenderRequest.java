package com.nongxinle.ai.dashboard.dto;

import lombok.Data;

/**
 * 看板渲染请求
 */
@Data
public class AiDashboardRenderRequest {
    private Long userId;
    private Long distributerId;
    private String scopeMode;
    private Long departmentId;
    private String startDate;
    private String endDate;
    private boolean includeAdvice;
}
