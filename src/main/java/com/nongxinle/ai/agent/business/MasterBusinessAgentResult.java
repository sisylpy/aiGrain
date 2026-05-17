package com.nongxinle.ai.agent.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MasterBusinessAgent 单次编排结果（含 Trace；不落最终自然语言）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterBusinessAgentResult {

    private AgentTraceEnvelope trace;
    private List<AgentResultEnvelope> agentResults;

    /** 本轮是否具备尝试 Master 编排的前置条件（REVENUE_OVERVIEW 专线窄 gate）。 */
    private boolean masterAgentEnabled;
    /** 是否实际走了 Master → RevenueAgent 并成功跳过 legacy 循环中的 REVENUE_QUERY。 */
    private boolean masterAgentUsed;
    private boolean fallbackUsed;
    private String fallbackReason;

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();

    /** revenue_query 已由 Master 路径执行，{@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} 应跳过重复调用。 */
    private boolean revenueToolExecutedByMasterPath;

    /** purchase_overview 已由 Master 路径执行，{@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} 应跳过重复调用。 */
    @Builder.Default
    private boolean purchaseToolExecutedByMasterPath = false;

    /** stock_reduce_query 已由 Master 路径执行，{@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} 应跳过重复调用。 */
    @Builder.Default
    private boolean stockReduceToolExecutedByMasterPath = false;

    /** dish_profit_analysis 已由 Master 路径执行，{@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} 应跳过重复调用。 */
    @Builder.Default
    private boolean dishProfitToolExecutedByMasterPath = false;

    /** warehouse_stock_overview 由 Master → {@link com.nongxinle.ai.agent.business.WarehouseStockAgent} 独占。 */
    @Builder.Default
    private boolean warehouseStockToolExecutedByMasterPath = false;

    /**
     * 四域 Harness：循环正常跑完且无未捕获异常（与「是否有执行成功的域」无关）。应与 debug
     * {@code businessOverviewMultiAgentBatchCompleted} 同义。
     */
    @Builder.Default
    private boolean businessOverviewMultiAgentBatchCompleted = false;

    /**
     * 四域 Harness：至少有一条子链路调用了 {@code execute}（registry/supports 直接跳过不计入）。
     * {@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} 用此决定是否跳过 legacy 四工具信封。
     */
    @Builder.Default
    private boolean businessOverviewMultiAgentBatchAttempted = false;

    /** 四域中至少一域工具实际 success。 */
    @Builder.Default
    private boolean businessOverviewMultiAgentAnyDomainSuccess = false;

    /** 经典六工具经营概况由 Master → {@link com.nongxinle.ai.agent.business.BusinessOverviewAgent} 执行后，BTEN 应跳过重复工具循环。 */
    @Builder.Default
    private boolean classicBusinessOverviewMasterPath = false;

    public static MasterBusinessAgentResult skipped(String reason) {
        return MasterBusinessAgentResult.builder()
                .masterAgentEnabled(false)
                .masterAgentUsed(false)
                .fallbackUsed(false)
                .fallbackReason(reason)
                .agentResults(new ArrayList<>())
                .build();
    }
}
