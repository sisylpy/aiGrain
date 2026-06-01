package com.nongxinle.ai.dashboard.renderer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.nongxinle.ai.dashboard.dto.AiDashboardModuleItem;
import com.nongxinle.ai.dashboard.dto.AiDashboardWidgetItem;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.entity.GbAiDashboardWidgetEntity;
import com.nongxinle.mapper.GbAiDashboardWidgetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 我的关注模块渲染器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFocusDashboardModuleRenderer implements AiDashboardModuleRenderer {

    private final GbAiDashboardWidgetMapper widgetMapper;

    @Override
    public String moduleType() {
        return "USER_CUSTOM_FOCUS";
    }

    @Override
    public AiDashboardModuleItem render(RenderContext ctx, GbAiDashboardModuleEntity module) {
        try {
            List<GbAiDashboardWidgetEntity> widgets = widgetMapper.selectList(
                    new LambdaQueryWrapper<GbAiDashboardWidgetEntity>()
                            .eq(GbAiDashboardWidgetEntity::getDashboardId, ctx.getDashboard().getId())
                            .eq(GbAiDashboardWidgetEntity::getModuleId, module.getId())
                            .eq(GbAiDashboardWidgetEntity::getEnabled, 1)
                            .orderByAsc(GbAiDashboardWidgetEntity::getPosition));

            List<AiDashboardWidgetItem> widgetItems = new ArrayList<>();
            if (widgets != null) {
                for (GbAiDashboardWidgetEntity w : widgets) {
                    widgetItems.add(buildWidgetItem(w));
                }
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("widgets", widgetItems);

            return buildModule(module, "SUCCESS", null, payload);
        } catch (Exception e) {
            log.error("我的关注渲染失败", e);
            return buildModule(module, "FAILED", "我的关注数据获取失败: " + e.getMessage(), new LinkedHashMap<>());
        }
    }

    private AiDashboardWidgetItem buildWidgetItem(GbAiDashboardWidgetEntity w) {
        Map<String, String> config = parseConfig(w.getConfigJson());
        Map<String, Object> data = renderWidgetData(w.getWidgetType(), config);

        return AiDashboardWidgetItem.builder()
                .widgetId(w.getId())
                .widgetType(w.getWidgetType())
                .title(w.getTitle())
                .position(w.getPosition())
                .enabled(w.getEnabled() != null && w.getEnabled() == 1)
                .status("SUCCESS")
                .errorMessage(null)
                .config(config)
                .data(data)
                .build();
    }

    private Map<String, Object> renderWidgetData(String widgetType, Map<String, String> config) {
        // P1: placeholder，后续根据 widgetType + config 渲染实际数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mainValueText", "—");
        return data;
    }

    private Map<String, String> parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.parseObject(configJson, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private AiDashboardModuleItem buildModule(GbAiDashboardModuleEntity m, String status, String error, Map<String, Object> payload) {
        return AiDashboardModuleItem.builder()
                .moduleId(m.getId())
                .moduleKey(m.getModuleKey())
                .moduleTitle(m.getModuleTitle())
                .moduleType(m.getModuleType())
                .position(m.getPosition())
                .enabled(m.getEnabled() != null && m.getEnabled() == 1)
                .removable(m.getRemovable() != null && m.getRemovable() == 1)
                .configurable(m.getConfigurable() != null && m.getConfigurable() == 1)
                .status(status)
                .errorMessage(error)
                .config(new LinkedHashMap<>())
                .payload(payload)
                .build();
    }
}
