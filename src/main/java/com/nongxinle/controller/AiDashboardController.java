package com.nongxinle.controller;

import com.nongxinle.ai.dashboard.dto.*;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.entity.GbAiDashboardWidgetEntity;
import com.nongxinle.service.AiDashboardApplicationService;
import com.nongxinle.service.AiDashboardConfigService;
import com.nongxinle.utils.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能经营看板接口
 */
@Slf4j
@RestController
@RequestMapping("/ai/dashboard")
@RequiredArgsConstructor
public class AiDashboardController {

    private final AiDashboardApplicationService applicationService;
    private final AiDashboardConfigService configService;

    /**
     * 获取并渲染我的智能看板
     */
    @PostMapping("/my/render")
    public R renderMyDashboard(@RequestBody AiDashboardRenderRequest request) {
        try {
            Map<String, Object> data = applicationService.renderDashboard(request);
            return R.ok().put("data", data);
        } catch (Exception e) {
            log.error("看板渲染失败", e);
            return R.error("看板渲染失败: " + e.getMessage());
        }
    }

    /**
     * 修改模块配置
     */
    @PutMapping("/modules/{moduleId}")
    public R updateModule(@PathVariable Long moduleId, @RequestBody AiDashboardModuleUpdateRequest request) {
        try {
            GbAiDashboardModuleEntity module = configService.updateModule(moduleId, request.getUserId(), request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("moduleId", module.getId());
            data.put("moduleTitle", module.getModuleTitle());
            data.put("enabled", module.getEnabled() == 1);
            data.put("position", module.getPosition());
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新模块失败", e);
            return R.error("更新模块失败: " + e.getMessage());
        }
    }

    /**
     * 批量调整模块顺序
     */
    @PutMapping("/modules/reorder")
    public R reorderModules(@RequestBody AiDashboardModuleReorderRequest request) {
        try {
            configService.reorderModules(request.getUserId(), request);
            return R.ok();
        } catch (Exception e) {
            log.error("调整模块顺序失败", e);
            return R.error("调整模块顺序失败: " + e.getMessage());
        }
    }

    /**
     * 查询可添加关注类型
     */
    @GetMapping("/focus-widget-catalog")
    public R getWidgetCatalog() {
        try {
            return R.ok().put("data", applicationService.getWidgetCatalog());
        } catch (Exception e) {
            log.error("查询关注类型失败", e);
            return R.error("查询关注类型失败: " + e.getMessage());
        }
    }

    /**
     * 添加关注卡片
     */
    @PostMapping("/focus-widgets")
    public R addWidget(@RequestBody AiDashboardWidgetAddRequest request) {
        try {
            GbAiDashboardWidgetEntity widget = configService.addWidget(request.getUserId(), request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("widgetId", widget.getId());
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            log.error("添加关注卡片失败", e);
            return R.error("添加关注卡片失败: " + e.getMessage());
        }
    }

    /**
     * 修改关注卡片
     */
    @PutMapping("/focus-widgets/{widgetId}")
    public R updateWidget(@PathVariable Long widgetId, @RequestBody AiDashboardWidgetUpdateRequest request) {
        try {
            GbAiDashboardWidgetEntity widget = configService.updateWidget(widgetId, request.getUserId(), request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("widgetId", widget.getId());
            data.put("title", widget.getTitle());
            data.put("enabled", widget.getEnabled() == 1);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新关注卡片失败", e);
            return R.error("更新关注卡片失败: " + e.getMessage());
        }
    }

    /**
     * 删除关注卡片（软删除）
     */
    @DeleteMapping("/focus-widgets/{widgetId}")
    public R deleteWidget(@PathVariable Long widgetId, @RequestParam Long userId) {
        try {
            configService.deleteWidget(widgetId, userId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除关注卡片失败", e);
            return R.error("删除关注卡片失败: " + e.getMessage());
        }
    }
}
