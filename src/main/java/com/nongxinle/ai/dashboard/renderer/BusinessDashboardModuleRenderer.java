package com.nongxinle.ai.dashboard.renderer;

import com.nongxinle.ai.dashboard.dto.AiDashboardModuleItem;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import com.nongxinle.service.impl.GbAiDailyRevenueDashboardServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 经营模块渲染器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessDashboardModuleRenderer implements AiDashboardModuleRenderer {

    private final GbAiDailyRevenueService dailyRevenueService;
    private final GbAiDailyRevenueDashboardServiceImpl revenueDashboardService;
    private final GbAiRestaurantProfileService profileService;

    @Override
    public String moduleType() {
        return "SYSTEM_BUSINESS";
    }

    @Override
    public AiDashboardModuleItem render(RenderContext ctx, GbAiDashboardModuleEntity module) {
        try {
            Long deptId = ctx.getDepartmentId();
            String startDate = ctx.getStartDate();
            String endDate = ctx.getEndDate();

            Map<String, Object> stats = dailyRevenueService.getStatsByDepartmentId(deptId, startDate, endDate);
            if (stats == null || stats.get("days") == null || ((Number) stats.get("days")).intValue() == 0) {
                return buildModule(module, "NO_DATA", null, new LinkedHashMap<>());
            }

            GbAiRestaurantProfileEntity profile = profileService.getByDepartmentId(deptId);
            if (profile == null) {
                profile = new GbAiRestaurantProfileEntity();
            }

            Map<String, Object> scaleDashboard = revenueDashboardService.buildScaleDashboard(
                    deptId, profile, stats, startDate, endDate);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scaleDashboard", scaleDashboard);

            return buildModule(module, "SUCCESS", null, payload);
        } catch (Exception e) {
            log.error("经营看板渲染失败", e);
            return buildModule(module, "FAILED", "经营看板数据获取失败: " + e.getMessage(), new LinkedHashMap<>());
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
