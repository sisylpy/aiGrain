package com.nongxinle.ai.dashboard.renderer;

import com.nongxinle.ai.dashboard.dto.AiDashboardModuleItem;
import com.nongxinle.entity.GbAiDashboardModuleEntity;

/**
 * 看板模块渲染器接口
 */
public interface AiDashboardModuleRenderer {

    /**
     * 匹配的模块类型
     */
    String moduleType();

    /**
     * 渲染模块数据
     */
    AiDashboardModuleItem render(RenderContext ctx, GbAiDashboardModuleEntity module);
}
