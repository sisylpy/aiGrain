package com.nongxinle.controller;

import com.nongxinle.ai.history.dto.AiMessageNoteResponseDTO;
import com.nongxinle.ai.history.dto.AiMessagePinResponseDTO;
import com.nongxinle.service.GbAiWorkNoteService;
import com.nongxinle.service.GbAiWorkPinService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天区答案级图钉（{@code gb_ai_work_pin}）与「本条回答保存为笔记」（{@code gb_ai_work_note}）。
 *
 * <p>与会话置顶 {@link AiConversationController 会话 pin API}、{@code ai/work-pins} 工作区接口语义区分：</p>
 * <ul>
 *   <li>左侧列表 {@code conversation.pinned} → {@code gb_ai_conversation_pin}</li>
 *   <li>本条消息的 {@code message.pinned} / {@code message.pinId} → 本控制器 + messages DTO</li>
 *   <li>本条消息的 {@code message.noted} / {@code message.noteId} → {@code POST .../note} + messages DTO</li>
 * </ul>
 *
 * <p>完整路径前缀含 {@code server.servlet.context-path}（默认 {@code /api}）。</p>
 */
@RestController
@RequestMapping("ai/messages")
@Tag(name = "AI 消息级图钉与笔记")
@RequiredArgsConstructor
public class AiMessagePinController {

    private final GbAiWorkPinService gbAiWorkPinService;
    private final GbAiWorkNoteService gbAiWorkNoteService;

    @PostMapping("/{messageId}/pin")
    @Operation(summary = "钉住一条 assistant 消息（幂等）")
    public R pin(
            @PathVariable Long messageId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            AiMessagePinResponseDTO data = gbAiWorkPinService.pinAssistantMessage(userId, messageId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/{messageId}/pin")
    @Operation(summary = "取消消息图钉（幂等）")
    public R unpin(
            @PathVariable Long messageId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            AiMessagePinResponseDTO data = gbAiWorkPinService.unpinAssistantMessage(userId, messageId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{messageId}/note")
    @Operation(summary = "将本条 assistant 回答保存为工作笔记（幂等）")
    public R saveMessageNote(
            @PathVariable Long messageId,
            @Parameter(description = "部门用户 ID") @RequestParam Long userId) {
        try {
            AiMessageNoteResponseDTO data = gbAiWorkNoteService.saveNoteFromAssistantMessage(userId, messageId);
            return R.ok().put("data", data);
        } catch (IllegalArgumentException ex) {
            return R.error(400, ex.getMessage());
        }
    }
}
