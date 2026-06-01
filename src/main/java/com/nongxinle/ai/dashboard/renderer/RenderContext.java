package com.nongxinle.ai.dashboard.renderer;

import com.nongxinle.entity.GbAiDashboardEntity;
import lombok.Builder;
import lombok.Data;

/**
 * 渲染上下文：封装一次看板渲染所需的所有公共参数
 */
@Data
@Builder
public class RenderContext {
    private Long userId;
    private Long distributerId;
    private String scopeMode;
    private Long departmentId;
    private String startDate;
    private String endDate;
    private String storeName;
    private GbAiDashboardEntity dashboard;
}
