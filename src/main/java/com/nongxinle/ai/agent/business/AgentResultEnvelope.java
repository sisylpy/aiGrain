package com.nongxinle.ai.agent.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个子 Agent 的执行结果信封（阶段 A 骨架）。
 *
 * @see docs/ai/master-business-agent-design.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResultEnvelope {

    private String agentName;
    private AgentResultStatus status;
    /** 预留：AnswerPlan 类型或片段标识（阶段 B+ 再收窄类型）。 */
    private String resultType;
    /** 预留：通常为 AnswerPlan 或 Builder 产物（阶段 B+ 替换为强类型）。 */
    private Object answerPlan;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    private boolean degraded;
    private long durationMs;
    private String traceId;

    /**
     * {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#REVENUE_QUERY} 的 {@link com.nongxinle.ai.tool.ToolResult#isSuccess()}；
     * Master 仅在 true 时可跳过 legacy REVENUE_QUERY。
     */
    private Boolean revenueQueryToolSuccess;

    /**
     * {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#PURCHASE_OVERVIEW} 的 Tool success；
     * Master 仅在 true 时可跳过 legacy PURCHASE_OVERVIEW。
     */
    private Boolean purchaseOverviewToolSuccess;

    /**
     * {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#STOCK_REDUCE_QUERY} 的 Tool success；
     * Master 仅在 true 时可跳过 legacy STOCK_REDUCE_QUERY。
     */
    private Boolean stockReduceQueryToolSuccess;

    /**
     * {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#DISH_PROFIT_ANALYSIS} 的 Tool success；
     * Master 仅在 true 时可跳过 legacy DISH_PROFIT_ANALYSIS。
     */
    private Boolean dishProfitAnalysisToolSuccess;

    /** {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW} */
    private Boolean warehouseStockOverviewToolSuccess;
}
