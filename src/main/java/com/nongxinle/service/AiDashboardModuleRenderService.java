package com.nongxinle.service;

import com.nongxinle.ai.dashboard.dto.AiDashboardModuleItem;
import com.nongxinle.ai.dashboard.renderer.RenderContext;
import com.nongxinle.entity.GbAiDashboardModuleEntity;

import java.util.List;

/**
 * 模块渲染服务：按模块配置调用对应 Renderer 生成数据
 */
public interface AiDashboardModuleRenderService {

    /**
     * 渲染看板所有模块
     */
    List<AiDashboardModuleItem> renderModules(RenderContext ctx, List<GbAiDashboardModuleEntity> modules);
}
