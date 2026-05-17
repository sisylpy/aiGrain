package com.nongxinle.controller;

import com.nongxinle.service.GbAiWorkflowRunService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流运行记录查询（与后续 Harness Run 对齐）。
 */
@RestController
@RequestMapping("ai/workflow-runs")
@Tag(name = "AI 工作流运行")
@RequiredArgsConstructor
public class AiWorkflowRunController {

    private final GbAiWorkflowRunService gbAiWorkflowRunService;

    @GetMapping("/{id}")
    @Operation(summary = "工作流运行详情")
    public R detail(@PathVariable Long id) {
        try {
            return R.ok().put("data", gbAiWorkflowRunService.getRun(id));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
