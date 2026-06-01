package com.nongxinle.ai.dashboard.renderer;

import com.nongxinle.ai.dashboard.dto.AiDashboardModuleItem;
import com.nongxinle.entity.GbAiDashboardModuleEntity;
import com.nongxinle.service.GbDishCostAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配料模块渲染器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngredientDashboardModuleRenderer implements AiDashboardModuleRenderer {

    private final GbDishCostAnalysisService dishCostAnalysisService;

    @Override
    public String moduleType() {
        return "SYSTEM_INGREDIENT";
    }

    @Override
    public AiDashboardModuleItem render(RenderContext ctx, GbAiDashboardModuleEntity module) {
        try {
            Integer disId = ctx.getDistributerId().intValue();
            Integer depFatherId = ctx.getDepartmentId().intValue();
            String startDate = ctx.getStartDate();
            String endDate = ctx.getEndDate();

            Map<String, Object> report = dishCostAnalysisService.buildOutboundIngredientAnalysisReport(
                    startDate, endDate, disId, null, depFatherId,
                    "outbound", "desc", null, null, null);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", report.get("summary"));
            payload.put("utilizationDistribution", report.get("utilizationDistribution"));

            return buildModule(module, "SUCCESS", null, payload);
        } catch (Exception e) {
            log.error("配料看板渲染失败", e);
            return buildModule(module, "FAILED", "配料看板数据获取失败: " + e.getMessage(), new LinkedHashMap<>());
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
