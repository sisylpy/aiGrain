package com.nongxinle.ai.dashboard.renderer;

import com.nongxinle.entity.GbAiDashboardModuleEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 渲染器注册表 + 默认模板定义
 */
@Component
public class AiDashboardModuleRendererRegistry {

    private final Map<String, AiDashboardModuleRenderer> rendererMap;

    public AiDashboardModuleRendererRegistry(List<AiDashboardModuleRenderer> renderers) {
        this.rendererMap = renderers.stream()
                .collect(Collectors.toMap(AiDashboardModuleRenderer::moduleType, Function.identity()));
    }

    public AiDashboardModuleRenderer getRenderer(String moduleType) {
        return rendererMap.get(moduleType);
    }

    /**
     * 获取系统默认模板模块列表
     */
    public static List<GbAiDashboardModuleEntity> defaultTemplateModules() {
        return List.of(
                buildTemplateModule("business", "SYSTEM_BUSINESS", "经营看板", 1, false),
                buildTemplateModule("dish", "SYSTEM_DISH", "菜品看板", 2, false),
                buildTemplateModule("ingredient", "SYSTEM_INGREDIENT", "配料看板", 3, false),
                buildTemplateModule("custom_focus", "USER_CUSTOM_FOCUS", "我的关注", 4, false)
        );
    }

    private static GbAiDashboardModuleEntity buildTemplateModule(String key, String type, String title, int pos, boolean removable) {
        GbAiDashboardModuleEntity m = new GbAiDashboardModuleEntity();
        m.setModuleKey(key);
        m.setModuleType(type);
        m.setModuleTitle(title);
        m.setPosition(pos);
        m.setEnabled(1);
        m.setRemovable(removable ? 1 : 0);
        m.setConfigurable(1);
        return m;
    }
}
