package com.nongxinle.service.impl;

import com.nongxinle.ai.dashboard.dto.AiDashboardModuleItem;
import com.nongxinle.ai.dashboard.renderer.AiDashboardModuleRenderer;
import com.nongxinle.ai.dashboard.renderer.AiDashboardModuleRendererRegistry;
import com.nongxinle.ai.dashboard.renderer.RenderContext;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.service.AiDashboardModuleRenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 模块渲染服务实现：独立渲染每个模块，失败不影响其他模块
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDashboardModuleRenderServiceImpl implements AiDashboardModuleRenderService {

    private final AiDashboardModuleRendererRegistry registry;

    @Override
    public List<AiDashboardModuleItem> renderModules(RenderContext ctx, List<GbAiDashboardModuleEntity> modules) {
        List<AiDashboardModuleItem> results = new ArrayList<>();
        for (GbAiDashboardModuleEntity module : modules) {
            results.add(renderOne(ctx, module));
        }
        return results;
    }

    private AiDashboardModuleItem renderOne(RenderContext ctx, GbAiDashboardModuleEntity module) {
        if (module.getEnabled() == null || module.getEnabled() == 0) {
            return AiDashboardModuleItem.builder()
                    .moduleId(module.getId())
                    .moduleKey(module.getModuleKey())
                    .moduleTitle(module.getModuleTitle())
                    .moduleType(module.getModuleType())
                    .position(module.getPosition())
                    .enabled(false)
                    .removable(module.getRemovable() != null && module.getRemovable() == 1)
                    .configurable(module.getConfigurable() != null && module.getConfigurable() == 1)
                    .status("DISABLED")
                    .errorMessage(null)
                    .config(new LinkedHashMap<>())
                    .payload(new LinkedHashMap<>())
                    .build();
        }

        AiDashboardModuleRenderer renderer = registry.getRenderer(module.getModuleType());
        if (renderer == null) {
            log.warn("未找到模块渲染器: moduleType={}", module.getModuleType());
            return AiDashboardModuleItem.builder()
                    .moduleId(module.getId())
                    .moduleKey(module.getModuleKey())
                    .moduleTitle(module.getModuleTitle())
                    .moduleType(module.getModuleType())
                    .position(module.getPosition())
                    .enabled(true)
                    .removable(module.getRemovable() != null && module.getRemovable() == 1)
                    .configurable(module.getConfigurable() != null && module.getConfigurable() == 1)
                    .status("FAILED")
                    .errorMessage("渲染器未注册: " + module.getModuleType())
                    .config(new LinkedHashMap<>())
                    .payload(new LinkedHashMap<>())
                    .build();
        }

        return renderer.render(ctx, module);
    }
}
