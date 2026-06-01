package com.nongxinle.service;

import com.nongxinle.ai.dashboard.dto.*;
import com.nongxinle.entity.GbAiDashboardEntity;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.entity.GbAiDashboardWidgetEntity;

import java.util.List;

/**
 * 看板配置服务：负责看板/模块/Widget 的 CRUD 与默认初始化
 */
public interface AiDashboardConfigService {

    /**
     * 获取或懒初始化用户看板
     */
    GbAiDashboardEntity getOrInitDashboard(Long userId, Long distributerId);

    /**
     * 获取看板下所有模块（按 position 排序）
     */
    List<GbAiDashboardModuleEntity> getModules(Long dashboardId);

    /**
     * 更新模块配置（校验归属）
     */
    GbAiDashboardModuleEntity updateModule(Long moduleId, Long userId, AiDashboardModuleUpdateRequest request);

    /**
     * 批量调整模块顺序（校验归属）
     */
    void reorderModules(Long userId, AiDashboardModuleReorderRequest request);

    /**
     * 获取关注卡片列表
     */
    List<GbAiDashboardWidgetEntity> getWidgets(Long dashboardId, Long moduleId);

    /**
     * 添加关注卡片（校验 widgetType + metric 白名单，校验归属）
     */
    GbAiDashboardWidgetEntity addWidget(Long userId, AiDashboardWidgetAddRequest request);

    /**
     * 更新关注卡片（校验归属，校验白名单）
     */
    GbAiDashboardWidgetEntity updateWidget(Long widgetId, Long userId, AiDashboardWidgetUpdateRequest request);

    /**
     * 删除（禁用）关注卡片（校验归属）
     */
    void deleteWidget(Long widgetId, Long userId);

    /**
     * 获取可添加关注类型目录
     */
    List<AiDashboardWidgetCatalogItem> getWidgetCatalog();
}
