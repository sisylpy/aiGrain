package com.nongxinle.service;

import com.nongxinle.ai.dashboard.dto.AiDashboardRenderRequest;
import com.nongxinle.ai.dashboard.dto.AiDashboardWidgetCatalogItem;

import java.util.Map;

/**
 * 看板应用服务：协调配置、渲染、建议
 */
public interface AiDashboardApplicationService {

    /**
     * 获取并渲染看板
     */
    Map<String, Object> renderDashboard(AiDashboardRenderRequest request);

    /**
     * 获取关注卡片类型目录
     */
    java.util.List<AiDashboardWidgetCatalogItem> getWidgetCatalog();
}
