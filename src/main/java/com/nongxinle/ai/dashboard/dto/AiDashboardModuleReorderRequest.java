package com.nongxinle.ai.dashboard.dto;

import lombok.Data;

import java.util.List;

/**
 * 模块批量排序请求
 */
@Data
public class AiDashboardModuleReorderRequest {
    private Long userId;
    private Long dashboardId;
    private List<ModulePosition> modules;

    @Data
    public static class ModulePosition {
        private Long moduleId;
        private Integer position;
    }
}
