package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 库房端「经营怎么样」→ 库存视角：仅编排库存类 Tool，不走营收/菜品/毛利主链。 */
public final class WarehouseStockIntentConvergence {

    private WarehouseStockIntentConvergence() {
    }

    /** 库房库存概览工具链（须具备库存查看权限）。 */
    public static List<String> buildWarehouseStockOverviewTools(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        if (!permissions.contains(AiPermissions.VIEW_STOCK)) {
            return List.of();
        }
        List<String> plan = new ArrayList<>(1);
        plan.add(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        return List.copyOf(plan);
    }
}
