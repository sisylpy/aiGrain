package com.nongxinle.controller;

import com.nongxinle.ai.advisor.AiAdvisorConversationService;
import com.nongxinle.service.GbAiAdvisorService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 业务顾问（Advisor）列表、详情、绑定工作流查询。数据来自 {@code gb_ai_advisor}，不写死。
 */
@RestController
@RequestMapping("ai/advisors")
@Tag(name = "AI 业务顾问")
@RequiredArgsConstructor
public class AiAdvisorController {

    private final GbAiAdvisorService gbAiAdvisorService;
    private final AiAdvisorConversationService aiAdvisorConversationService;

    @GetMapping
    @Operation(summary = "业务顾问列表")
    public R list() {
        return R.ok().put("data", gbAiAdvisorService.listAdvisors());
    }

    @GetMapping("/{advisorId}")
    @Operation(summary = "业务顾问详情")
    public R detail(@PathVariable Long advisorId) {
        try {
            return R.ok().put("data", gbAiAdvisorService.getAdvisor(advisorId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{advisorId}/workflows")
    @Operation(summary = "某顾问绑定的工作流列表")
    public R workflows(@PathVariable Long advisorId) {
        try {
            return R.ok().put("data", gbAiAdvisorService.listAdvisorWorkflows(advisorId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    /**
     * 顾问页长期会话锚点：按 userId + advisorId（及门店/集团范围）幂等获取或插入 {@code gb_ai_conversation}
     *（{@code gb_ai_conversation_thread_kind=ADVISOR}），并返回 {@link com.nongxinle.ai.history.dto.AiConversationMessageDTO} 历史。
     *
     * <p>前台随后 {@code POST /api/ai/runs} 传同一 {@code conversationId} 与 {@code advisorId} 即可复用既有落库。
     */
    @GetMapping("/{advisorId}/conversation")
    @Operation(summary = "获取或创建顾问会话（含 gb_ai_message 历史）")
    public R advisorConversation(
            @PathVariable Long advisorId,
            @Parameter(description = "gb_department_user 主键，与会话归属一致") @RequestParam Long userId,
            @Parameter(description = "单店(scope=STORE)：门店父部门 id") @RequestParam(required = false) Long departmentId,
            @Parameter(description = "集团(scope=GROUP)：配送商 distributer(dis) id") @RequestParam(required = false) Long distributerId,
            @Parameter(description = "STORE | GROUP；不传则根据 departmentId / distributerId 推断") @RequestParam(required = false) String scopeMode) {
        try {
            return R.ok().put(
                    "data",
                    aiAdvisorConversationService.getOrBootstrap(
                            advisorId, userId, departmentId, distributerId, scopeMode));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{advisorId}/workflow-runs")
    @Operation(summary = "某顾问下该用户最近的工作流运行列表（只读）")
    public R recentWorkflowRuns(
            @PathVariable Long advisorId,
            @Parameter(description = "gb_department_user") @RequestParam Long userId,
            @Parameter(description = "条数，默认 10，最大 50") @RequestParam(defaultValue = "10") int limit) {
        try {
            return R.ok().put("data", gbAiAdvisorService.listRecentWorkflowRuns(advisorId, userId, limit));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
