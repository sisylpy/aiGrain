package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 营收只读桥接出参（C-8）。Bridge 返回结构化结果；{@link RevenuePlannerAgentAdapter} 将其转为 {@link PlannerStepExecutionResponse}。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RevenuePlannerReadResponse {

    @Builder.Default
    private RevenuePlannerReadStatus status = RevenuePlannerReadStatus.DEGRADED;

    /** 汇总营业额（Tool 口径）；无数据时可 null。 */
    private BigDecimal revenueAmount;

    @Builder.Default
    private List<RevenuePlannerStoreRevenueRow> storeRows = new ArrayList<>();

    private String timeLabel;
    private String scopeLabel;

    /** 业务错误码（如 {@code REVENUE_TOOL_TIMEOUT}）；降级/失败时建议非空。 */
    private String errorCode;

    private String errorMessage;
}
