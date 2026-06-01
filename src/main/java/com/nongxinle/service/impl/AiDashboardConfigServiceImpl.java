package com.nongxinle.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.ai.dashboard.dto.AiDashboardModuleReorderRequest;
import com.nongxinle.ai.dashboard.dto.AiDashboardModuleUpdateRequest;
import com.nongxinle.ai.dashboard.dto.AiDashboardWidgetAddRequest;
import com.nongxinle.ai.dashboard.dto.AiDashboardWidgetCatalogItem;
import com.nongxinle.ai.dashboard.dto.AiDashboardWidgetUpdateRequest;
import com.nongxinle.ai.dashboard.renderer.AiDashboardModuleRendererRegistry;
import com.nongxinle.entity.GbAiDashboardEntity;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.entity.GbAiDashboardWidgetEntity;
import com.nongxinle.mapper.GbAiDashboardMapper;
import com.nongxinle.mapper.GbAiDashboardModuleMapper;
import com.nongxinle.mapper.GbAiDashboardWidgetMapper;
import com.nongxinle.service.AiDashboardConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * 看板配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDashboardConfigServiceImpl implements AiDashboardConfigService {

    private static final String DEFAULT_DASHBOARD_CODE = "SMART_BUSINESS_DASHBOARD";
    private static final String DEFAULT_DASHBOARD_NAME = "智能经营看板";

    /** widgetType -> 允许的 metric 白名单 */
    private static final Map<String, Set<String>> WIDGET_METRIC_WHITELIST = Map.of(
            "DISH_FOCUS", Set.of("SALES_QUANTITY", "SALES_AMOUNT", "GROSS_MARGIN"),
            "INGREDIENT_FOCUS", Set.of("OUTBOUND_AMOUNT", "UTILIZATION_RATE", "PURCHASE_AMOUNT")
    );

    private final GbAiDashboardMapper dashboardMapper;
    private final GbAiDashboardModuleMapper moduleMapper;
    private final GbAiDashboardWidgetMapper widgetMapper;

    @Override
    @Transactional
    public GbAiDashboardEntity getOrInitDashboard(Long userId, Long distributerId) {
        GbAiDashboardEntity dashboard = dashboardMapper.selectOne(
                new LambdaQueryWrapper<GbAiDashboardEntity>()
                        .eq(GbAiDashboardEntity::getUserId, userId)
                        .eq(GbAiDashboardEntity::getDistributerId, distributerId)
                        .eq(GbAiDashboardEntity::getDashboardCode, DEFAULT_DASHBOARD_CODE)
                        .eq(GbAiDashboardEntity::getStatus, 1));

        if (dashboard != null) {
            return dashboard;
        }

        // 懒初始化
        dashboard = new GbAiDashboardEntity();
        dashboard.setUserId(userId);
        dashboard.setDistributerId(distributerId);
        dashboard.setDashboardCode(DEFAULT_DASHBOARD_CODE);
        dashboard.setDashboardName(DEFAULT_DASHBOARD_NAME);
        dashboard.setIsDefault(1);
        dashboard.setStatus(1);
        dashboard.setCreateTime(LocalDateTime.now());
        dashboard.setUpdateTime(LocalDateTime.now());
        dashboardMapper.insert(dashboard);

        // 插入默认模块
        List<GbAiDashboardModuleEntity> templates = AiDashboardModuleRendererRegistry.defaultTemplateModules();
        for (GbAiDashboardModuleEntity tmpl : templates) {
            tmpl.setDashboardId(dashboard.getId());
            tmpl.setCreateTime(LocalDateTime.now());
            tmpl.setUpdateTime(LocalDateTime.now());
            moduleMapper.insert(tmpl);
        }

        log.info("初始化用户看板 userId={} distributerId={} dashboardId={}", userId, distributerId, dashboard.getId());
        return dashboard;
    }

    @Override
    public List<GbAiDashboardModuleEntity> getModules(Long dashboardId) {
        return moduleMapper.selectList(
                new LambdaQueryWrapper<GbAiDashboardModuleEntity>()
                        .eq(GbAiDashboardModuleEntity::getDashboardId, dashboardId)
                        .orderByAsc(GbAiDashboardModuleEntity::getPosition));
    }

    @Override
    public GbAiDashboardModuleEntity updateModule(Long moduleId, Long userId, AiDashboardModuleUpdateRequest request) {
        GbAiDashboardModuleEntity module = moduleMapper.selectById(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("模块不存在: " + moduleId);
        }
        validateDashboardOwnership(module.getDashboardId(), userId);

        if (request.getModuleTitle() != null) {
            module.setModuleTitle(request.getModuleTitle());
        }
        if (request.getEnabled() != null) {
            module.setEnabled(request.getEnabled() ? 1 : 0);
        }
        if (request.getPosition() != null) {
            module.setPosition(request.getPosition());
        }
        if (request.getConfig() != null) {
            module.setConfigJson(request.getConfig());
        }
        module.setUpdateTime(LocalDateTime.now());
        moduleMapper.updateById(module);
        return module;
    }

    @Override
    @Transactional
    public void reorderModules(Long userId, AiDashboardModuleReorderRequest request) {
        if (request.getModules() == null || request.getModules().isEmpty()) {
            return;
        }
        for (AiDashboardModuleReorderRequest.ModulePosition mp : request.getModules()) {
            GbAiDashboardModuleEntity module = moduleMapper.selectById(mp.getModuleId());
            if (module != null) {
                validateDashboardOwnership(module.getDashboardId(), userId);
                module.setPosition(mp.getPosition());
                module.setUpdateTime(LocalDateTime.now());
                moduleMapper.updateById(module);
            }
        }
    }

    @Override
    public List<GbAiDashboardWidgetEntity> getWidgets(Long dashboardId, Long moduleId) {
        return widgetMapper.selectList(
                new LambdaQueryWrapper<GbAiDashboardWidgetEntity>()
                        .eq(GbAiDashboardWidgetEntity::getDashboardId, dashboardId)
                        .eq(GbAiDashboardWidgetEntity::getModuleId, moduleId)
                        .orderByAsc(GbAiDashboardWidgetEntity::getPosition));
    }

    @Override
    @Transactional
    public GbAiDashboardWidgetEntity addWidget(Long userId, AiDashboardWidgetAddRequest request) {
        // 校验 widgetType 白名单
        String widgetType = request.getWidgetType();
        if (widgetType == null || !WIDGET_METRIC_WHITELIST.containsKey(widgetType)) {
            throw new IllegalArgumentException("不支持的关注类型: " + widgetType
                    + "，允许的类型: " + WIDGET_METRIC_WHITELIST.keySet());
        }
        // 校验 metric 白名单
        Map<String, String> config = request.getConfig();
        if (config != null && config.containsKey("metric")) {
            String metric = config.get("metric");
            if (metric != null && !WIDGET_METRIC_WHITELIST.get(widgetType).contains(metric)) {
                throw new IllegalArgumentException(widgetType + " 不支持的指标: " + metric
                        + "，允许的指标: " + WIDGET_METRIC_WHITELIST.get(widgetType));
            }
        }
        // 校验看板归属
        validateDashboardOwnership(request.getDashboardId(), userId);

        GbAiDashboardWidgetEntity widget = new GbAiDashboardWidgetEntity();
        widget.setDashboardId(request.getDashboardId());
        widget.setModuleId(request.getModuleId());
        widget.setWidgetType(widgetType);
        widget.setTitle(request.getTitle());
        widget.setPosition(request.getPosition() != null ? request.getPosition() : 0);
        widget.setEnabled(1);
        widget.setConfigJson(JSON.toJSONString(config));
        widget.setCreateTime(LocalDateTime.now());
        widget.setUpdateTime(LocalDateTime.now());
        widgetMapper.insert(widget);
        return widget;
    }

    @Override
    @Transactional
    public GbAiDashboardWidgetEntity updateWidget(Long widgetId, Long userId, AiDashboardWidgetUpdateRequest request) {
        GbAiDashboardWidgetEntity widget = widgetMapper.selectById(widgetId);
        if (widget == null) {
            throw new IllegalArgumentException("关注卡片不存在: " + widgetId);
        }
        // 校验看板归属
        validateDashboardOwnership(widget.getDashboardId(), userId);

        // 如果更新了 config.metric，校验白名单
        Map<String, String> newConfig = request.getConfig();
        if (newConfig != null && newConfig.containsKey("metric")) {
            String metric = newConfig.get("metric");
            String widgetType = widget.getWidgetType();
            if (metric != null && !WIDGET_METRIC_WHITELIST.getOrDefault(widgetType, Set.of()).contains(metric)) {
                throw new IllegalArgumentException(widgetType + " 不支持的指标: " + metric
                        + "，允许的指标: " + WIDGET_METRIC_WHITELIST.get(widgetType));
            }
        }

        if (request.getTitle() != null) {
            widget.setTitle(request.getTitle());
        }
        if (request.getPosition() != null) {
            widget.setPosition(request.getPosition());
        }
        if (request.getEnabled() != null) {
            widget.setEnabled(request.getEnabled() ? 1 : 0);
        }
        if (newConfig != null) {
            widget.setConfigJson(JSON.toJSONString(newConfig));
        }
        widget.setUpdateTime(LocalDateTime.now());
        widgetMapper.updateById(widget);
        return widget;
    }

    @Override
    @Transactional
    public void deleteWidget(Long widgetId, Long userId) {
        GbAiDashboardWidgetEntity widget = widgetMapper.selectById(widgetId);
        if (widget == null) {
            throw new IllegalArgumentException("关注卡片不存在: " + widgetId);
        }
        validateDashboardOwnership(widget.getDashboardId(), userId);
        widget.setEnabled(0);
        widget.setUpdateTime(LocalDateTime.now());
        widgetMapper.updateById(widget);
    }

    @Override
    public List<AiDashboardWidgetCatalogItem> getWidgetCatalog() {
        return Arrays.asList(
                new AiDashboardWidgetCatalogItem("DISH_FOCUS", "菜品关注", Arrays.asList(
                        new AiDashboardWidgetCatalogItem.MetricItem("销量", "SALES_QUANTITY"),
                        new AiDashboardWidgetCatalogItem.MetricItem("销售额", "SALES_AMOUNT"),
                        new AiDashboardWidgetCatalogItem.MetricItem("毛利率", "GROSS_MARGIN")
                )),
                new AiDashboardWidgetCatalogItem("INGREDIENT_FOCUS", "配料关注", Arrays.asList(
                        new AiDashboardWidgetCatalogItem.MetricItem("出库金额", "OUTBOUND_AMOUNT"),
                        new AiDashboardWidgetCatalogItem.MetricItem("利用率", "UTILIZATION_RATE"),
                        new AiDashboardWidgetCatalogItem.MetricItem("采购金额", "PURCHASE_AMOUNT")
                ))
        );
    }

    // ========== 归属校验 ==========

    /**
     * 校验看板是否属于指定用户
     */
    private void validateDashboardOwnership(Long dashboardId, Long expectedUserId) {
        GbAiDashboardEntity dashboard = dashboardMapper.selectById(dashboardId);
        if (dashboard == null || !dashboard.getUserId().equals(expectedUserId)) {
            throw new IllegalArgumentException("看板不存在或无权操作");
        }
    }
}
