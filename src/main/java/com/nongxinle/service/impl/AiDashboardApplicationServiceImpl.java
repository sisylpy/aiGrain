package com.nongxinle.service.impl;

import com.nongxinle.ai.dashboard.dto.AiDashboardModuleItem;
import com.nongxinle.ai.dashboard.dto.AiDashboardRenderRequest;
import com.nongxinle.ai.dashboard.dto.AiDashboardWidgetCatalogItem;
import com.nongxinle.ai.dashboard.renderer.RenderContext;
import com.nongxinle.entity.GbAiDashboardEntity;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.service.AiDashboardAdviceService;
import com.nongxinle.service.AiDashboardApplicationService;
import com.nongxinle.service.AiDashboardConfigService;
import com.nongxinle.service.AiDashboardModuleRenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDashboardApplicationServiceImpl implements AiDashboardApplicationService {

    private final AiDashboardConfigService configService;
    private final AiDashboardModuleRenderService renderService;
    private final AiDashboardAdviceService adviceService;

    @Override
    public Map<String, Object> renderDashboard(AiDashboardRenderRequest request) {
        Long userId = request.getUserId();
        Long distributerId = request.getDistributerId();

        // 1. 获取或初始化看板
        GbAiDashboardEntity dashboard = configService.getOrInitDashboard(userId, distributerId);

        // 2. 构建渲染上下文
        RenderContext ctx = RenderContext.builder()
                .userId(userId)
                .distributerId(distributerId)
                .scopeMode(request.getScopeMode())
                .departmentId(request.getDepartmentId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .dashboard(dashboard)
                .build();

        // 3. 读取模块配置（按 position 排序）
        List<GbAiDashboardModuleEntity> modules = configService.getModules(dashboard.getId());

        // 4. 渲染各模块
        List<AiDashboardModuleItem> renderedModules = renderService.renderModules(ctx, modules);

        // 5. AI 建议（P1 固定 null）
        Map<String, Object> advice = null;
        if (request.isIncludeAdvice()) {
            advice = adviceService.generateAdvice(ctx, new LinkedHashMap<>());
        }

        // 6. 组装返回
        Map<String, Object> dashboardInfo = new LinkedHashMap<>();
        dashboardInfo.put("dashboardId", dashboard.getId());
        dashboardInfo.put("dashboardName", dashboard.getDashboardName());
        dashboardInfo.put("dashboardCode", dashboard.getDashboardCode());
        dashboardInfo.put("userId", dashboard.getUserId());
        dashboardInfo.put("distributerId", dashboard.getDistributerId());

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("scopeMode", request.getScopeMode());
        scope.put("departmentId", request.getDepartmentId());

        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("startDate", request.getStartDate());
        timeWindow.put("endDate", request.getEndDate());
        timeWindow.put("label", computeTimeLabel(request.getStartDate(), request.getEndDate()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dashboard", dashboardInfo);
        data.put("scope", scope);
        data.put("timeWindow", timeWindow);
        data.put("modules", renderedModules);
        data.put("advice", advice);

        return data;
    }

    @Override
    public List<AiDashboardWidgetCatalogItem> getWidgetCatalog() {
        return configService.getWidgetCatalog();
    }

    private String computeTimeLabel(String startDate, String endDate) {
        if (startDate == null || endDate == null) {
            return "全部";
        }
        // 简单判断：同月且月初到月底 -> 本月
        if (startDate.length() >= 7 && endDate.length() >= 7
                && startDate.substring(0, 7).equals(endDate.substring(0, 7))) {
            return startDate.substring(0, 7).replace("-", "年") + "月";
        }
        return startDate + " 至 " + endDate;
    }
}
