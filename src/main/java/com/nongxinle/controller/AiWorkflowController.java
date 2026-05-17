package com.nongxinle.controller;

import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunCreateRequest;
import com.nongxinle.service.GbAiWorkflowService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流（Workflow）列表与发起运行。
 * {@code POST .../run} 落库 {@code gb_ai_workflow_run}；{@code WF_REVENUE_MONTH_REVIEW} 额外调度 {@link com.nongxinle.ai.platform.AiRunService#startRun}。
 */
@RestController
@RequestMapping("ai/workflows")
@Tag(name = "AI 工作流")
@RequiredArgsConstructor
public class AiWorkflowController {

    private final GbAiWorkflowService gbAiWorkflowService;

    @GetMapping
    @Operation(summary = "工作流列表")
    public R list() {
        return R.ok().put("data", gbAiWorkflowService.listWorkflows());
    }

    @PostMapping("/{workflowId}/run")
    @Operation(summary = "发起一次工作流运行",
            description = "落库 workflow_run；WF_REVENUE_MONTH_REVIEW 会调度 Harness（AiRunService.startRun），返回 runId/conversationId。")
    public R run(@PathVariable Long workflowId, @RequestBody(required = false) WorkflowRunCreateRequest body) {
        try {
            return R.ok().put("data", gbAiWorkflowService.startWorkflowRun(workflowId, body));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
