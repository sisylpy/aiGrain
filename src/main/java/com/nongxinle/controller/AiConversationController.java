package com.nongxinle.controller;

import com.nongxinle.ai.history.AiConversationHistoryService;
import com.nongxinle.ai.history.dto.*;
import com.nongxinle.service.GbAiWorkNoteService;
import com.nongxinle.service.GbAiWorkPinService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 会话历史列表 / 消息详情 / 置顶·标签·笔记本（左侧栏）。{@code GET .../pins}、{@code GET .../notes} 与同参数的 {@code ai/work-pins}、{@code ai/work-notes} 列表等价。
 * 完整路径前缀见 {@code server.servlet.context-path}（默认 /api）。
 *
 * <p><b>交付说明：</b>{@code GET .../messages} 仅返回 {@code gb_ai_message} 已有数据；{@code /api/ai/runs}
 * 多智能体默认不写消息表，Run 内容落库需后续单独任务（本轮不改 Harness/SSE）。</p>
 */
@RestController
@RequestMapping("ai/conversations")
@Tag(name = "AI 会话历史")
@RequiredArgsConstructor
public class AiConversationController {

    private final AiConversationHistoryService aiConversationHistoryService;
    private final GbAiWorkPinService gbAiWorkPinService;
    private final GbAiWorkNoteService gbAiWorkNoteService;

    @GetMapping
    @Operation(summary = "分页会话列表（批量组装，避免 N+1）")
    public R list(
            @Parameter(description = "部门用户 ID（gb_department_user）") @RequestParam Long userId,
            @Parameter(description = "单店筛选：门店父部门 ID") @RequestParam(required = false) Long departmentId,
            @Parameter(description = "集团筛选：批发商 disId") @RequestParam(required = false) Long distributerId,
            @Parameter(description = "标题或消息正文关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "按最近助理消息的 status 过滤：PENDING/RUNNING/COMPLETED/FAILED，ALL 或不传表示不限")
            @RequestParam(required = false) String status,
            @Parameter(description = "是否包含归档会话") @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long notebookId,
            @Parameter(description = "仅返回置顶会话") @RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            AiConversationListResponseDTO data = aiConversationHistoryService.listConversations(
                    userId, departmentId, distributerId, keyword, status, includeArchived,
                    tagId, notebookId, pinned, page, pageSize);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "会话消息列表；含每条消息的 assistant 图钉状态（gb_ai_work_pin，与会话置顶无关）")
    public R messages(
            @PathVariable Long conversationId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            AiConversationMessagesResponseDTO data = aiConversationHistoryService.listMessages(conversationId, userId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    /**
     * 与 {@code GET ai/work-pins?conversationId=&userId=} 等价；供前台按会话 REST 路径拉取图钉列表。
     */
    @GetMapping("/{conversationId}/pins")
    @Operation(summary = "本会话下图钉列表（同 ai/work-pins list）")
    public R listConversationPins(
            @PathVariable Long conversationId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", gbAiWorkPinService.listPins(conversationId, userId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    /**
     * 与 {@code GET ai/work-notes?conversationId=&userId=} 等价；供前台按会话 REST 路径拉取笔记列表。
     */
    @GetMapping("/{conversationId}/notes")
    @Operation(summary = "本会话下工作笔记列表（同 ai/work-notes list）")
    public R listConversationNotes(
            @PathVariable Long conversationId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            return R.ok().put("data", gbAiWorkNoteService.listNotes(conversationId, userId));
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{conversationId}/pin")
    @Operation(summary = "置顶会话（幂等）")
    public R pin(
            @PathVariable Long conversationId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            AiConversationPinMutationDTO data = aiConversationHistoryService.pinConversation(conversationId, userId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/{conversationId}/pin")
    @Operation(summary = "取消置顶（幂等）")
    public R unpin(
            @PathVariable Long conversationId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            aiConversationHistoryService.unpinConversation(conversationId, userId);
            return R.ok();
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{conversationId}/tags")
    @Operation(summary = "会话关联标签（幂等）；可按 tagId 或 tagName")
    public R attachTag(@PathVariable Long conversationId, @RequestBody AiConversationTagAttachRequest body) {
        try {
            AiConversationTagMutationDTO data = aiConversationHistoryService.attachTag(conversationId, body);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/{conversationId}/tags")
    @Operation(summary = "移除会话标签（幂等）")
    public R detachTag(
            @PathVariable Long conversationId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId,
            @Parameter(description = "标签 ID") @RequestParam Long tagId) {
        try {
            aiConversationHistoryService.detachTag(conversationId, userId, tagId);
            return R.ok();
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{conversationId}/notebooks")
    @Operation(summary = "会话归入笔记本（幂等）；可按 notebookId 或 notebookName")
    public R attachNotebook(@PathVariable Long conversationId, @RequestBody AiConversationNotebookAttachRequest body) {
        try {
            AiConversationNotebookMutationDTO data = aiConversationHistoryService.attachNotebook(conversationId, body);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/{conversationId}/notebooks")
    @Operation(summary = "会话移出笔记本（幂等）")
    public R detachNotebook(
            @PathVariable Long conversationId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId,
            @Parameter(description = "笔记本 ID") @RequestParam Long notebookId) {
        try {
            aiConversationHistoryService.detachNotebook(conversationId, userId, notebookId);
            return R.ok();
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
