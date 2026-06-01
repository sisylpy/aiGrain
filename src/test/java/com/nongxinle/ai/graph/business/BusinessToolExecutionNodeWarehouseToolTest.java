package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessToolExecutionNodeWarehouseToolTest {

    @Test
    void isWarehouseToolOwnedByMasterAgent_includesGoodsSupportedDishCover() {
        assertTrue(BusinessToolExecutionNode.isWarehouseToolOwnedByMasterAgent(
                AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER));
    }

    @Test
    void isWarehouseToolOwnedByMasterAgent_includesExistingWarehouseTools() {
        assertTrue(BusinessToolExecutionNode.isWarehouseToolOwnedByMasterAgent(
                AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW));
        assertTrue(BusinessToolExecutionNode.isWarehouseToolOwnedByMasterAgent(
                AiBusinessToolIds.WAREHOUSE_INVENTORY_RISK_LIST));
    }

    @Test
    void isWarehouseToolOwnedByMasterAgent_rejectsUnrelatedTools() {
        assertFalse(BusinessToolExecutionNode.isWarehouseToolOwnedByMasterAgent(
                AiBusinessToolIds.DISH_PROFIT_ANALYSIS));
    }

    @Test
    void shouldPreserveExistingSuccessfulToolEnvelope_keepsMasterSuccessOnLaterFailure() {
        var state = new com.nongxinle.ai.core.AiRunState();
        state.getToolResults().put(
                AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER,
                java.util.Map.of("success", true, "goodsSupportedDishCover", java.util.Map.of("dishRows", java.util.List.of())));
        var failed = com.nongxinle.ai.tool.ToolResult.builder()
                .success(false)
                .message("missing_dis_id")
                .data(java.util.Map.of())
                .build();
        assertTrue(BusinessToolExecutionNode.shouldPreserveExistingSuccessfulToolEnvelope(
                state, AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER, failed));
    }
}
