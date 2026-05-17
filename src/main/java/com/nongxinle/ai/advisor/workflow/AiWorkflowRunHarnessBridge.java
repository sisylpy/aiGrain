package com.nongxinle.ai.advisor.workflow;

import com.nongxinle.ai.advisor.workflow.dto.WorkflowHarnessDispatchResult;
import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunCreateRequest;
import com.nongxinle.ai.platform.AiRunService;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.platform.dto.AiRunStartResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Workflow 启动壳 → 复用 {@link AiRunService#startRun}，不查业务表、不绕开 Resolver。
 *
 * <p>workflowCode 须与 {@code gb_ai_workflow.gb_ai_workflow_code}（种子 {@code gb_ai_advisor_workflow_seed.sql}）一致。
 */
@Component
@RequiredArgsConstructor
public class AiWorkflowRunHarnessBridge {

    public static final String WORKFLOW_CODE_REVENUE_MONTH_REVIEW = "WF_REVENUE_MONTH_REVIEW";
    public static final String WORKFLOW_CODE_REVENUE_STORE_RANK = "WF_REVENUE_STORE_RANK";
    /** 种子库名为「采购金额分析」，code 为 {@code WF_PURCHASE_AMOUNT}（非 WF_PURCHASE_AMOUNT_ANALYSIS）。 */
    public static final String WORKFLOW_CODE_PURCHASE_AMOUNT = "WF_PURCHASE_AMOUNT";
    /** 种子库名为「出库耗用分析」，code 为 {@code WF_STOCK_CONSUMPTION}（非 WF_STOCK_REDUCE_ANALYSIS）。 */
    public static final String WORKFLOW_CODE_STOCK_CONSUMPTION = "WF_STOCK_CONSUMPTION";
    public static final String WORKFLOW_CODE_PURCHASE_STORE_RANK = "WF_PURCHASE_STORE_RANK";
    public static final String WORKFLOW_CODE_STOCK_ON_HAND = "WF_STOCK_ON_HAND";
    public static final String WORKFLOW_CODE_DISH_MARGIN_RANK = "WF_DISH_MARGIN_RANK";
    public static final String WORKFLOW_CODE_DISH_SALES_RANK = "WF_DISH_SALES_RANK";

    private static final String MESSAGE_REVENUE_MONTH_REVIEW = "帮我做本月营业额复盘";
    private static final String MESSAGE_REVENUE_STORE_RANK = "帮我看本月门店营业额排行";
    private static final String MESSAGE_PURCHASE_AMOUNT = "帮我分析本月采购金额和采购结构";
    private static final String MESSAGE_STOCK_CONSUMPTION = "帮我分析本月出库耗用情况";
    private static final String MESSAGE_PURCHASE_STORE_RANK = "帮我看本月门店采购金额排行";
    private static final String MESSAGE_STOCK_ON_HAND = "帮我检查当前库存现量";
    private static final String MESSAGE_DISH_MARGIN_RANK = "帮我看本月菜品毛利排行";
    private static final String MESSAGE_DISH_SALES_RANK = "帮我看本月菜品销量排行";

    private final AiRunService aiRunService;

    public boolean supportsHarnessDispatch(String workflowCode) {
        return seedMessageForWorkflow(workflowCode) != null;
    }

    /**
     * @param workflowCode {@code gb_ai_workflow.gb_ai_workflow_code}
     * @throws IllegalArgumentException 与 {@link AiRunService#startRun} 一致（参数/会话校验失败等）
     * @throws IllegalStateException    {@code workflowCode} 未注册种子问句（调用方应先 {@link #supportsHarnessDispatch}）
     */
    public WorkflowHarnessDispatchResult dispatch(WorkflowRunCreateRequest workflowReq, String workflowCode) {
        String message = seedMessageForWorkflow(workflowCode);
        if (message == null) {
            throw new IllegalStateException("workflowCode has no harness seed message: " + workflowCode);
        }

        AiRunCreateRequest req = new AiRunCreateRequest();
        req.setUserId(workflowReq.getUserId());
        req.setDepartmentId(workflowReq.getDepartmentId());
        req.setDistributerId(workflowReq.getDistributerId());
        req.setConversationId(workflowReq.getConversationId());
        req.setScopeMode(workflowReq.getScopeMode());
        req.setMessage(message);

        AiRunStartResult started = aiRunService.startRun(req);
        return new WorkflowHarnessDispatchResult(
                started.runId(), started.conversationId(), WorkflowRunStatus.RUNNING.code());
    }

    static String seedMessageForWorkflow(String workflowCode) {
        if (workflowCode == null) {
            return null;
        }
        return switch (workflowCode) {
            case WORKFLOW_CODE_REVENUE_MONTH_REVIEW -> MESSAGE_REVENUE_MONTH_REVIEW;
            case WORKFLOW_CODE_REVENUE_STORE_RANK -> MESSAGE_REVENUE_STORE_RANK;
            case WORKFLOW_CODE_PURCHASE_AMOUNT -> MESSAGE_PURCHASE_AMOUNT;
            case WORKFLOW_CODE_STOCK_CONSUMPTION -> MESSAGE_STOCK_CONSUMPTION;
            case WORKFLOW_CODE_PURCHASE_STORE_RANK -> MESSAGE_PURCHASE_STORE_RANK;
            case WORKFLOW_CODE_STOCK_ON_HAND -> MESSAGE_STOCK_ON_HAND;
            case WORKFLOW_CODE_DISH_MARGIN_RANK -> MESSAGE_DISH_MARGIN_RANK;
            case WORKFLOW_CODE_DISH_SALES_RANK -> MESSAGE_DISH_SALES_RANK;
            default -> null;
        };
    }
}
