package com.nongxinle.controller;

import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.service.GbAiChatService;
import com.nongxinle.service.GbAiMemoryService;
import com.nongxinle.utils.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI对话接口
 */
@RestController
@RequestMapping("ai/chat")
@Tag(name = "AI对话接口")
@RequiredArgsConstructor
public class GbAiChatController {

    private final GbAiChatService chatService;
    private final GbAiMemoryService memoryService;

    /**
     * 创建或恢复对话
     *
     * @Description 根据部门ID和用户ID创建新对话或获取已有对话
     * @param type 对话类型 (0=普通聊天, 1=促销活动/销售额, 2=公众号相关)
     */
    @PostMapping("/conversation")
    @Operation(summary = "创建或恢复对话", description = "根据部门ID和用户ID创建新对话或获取已有对话")
    public R createConversation(@Parameter(description = "部门ID") @RequestParam Long departmentId,
                                @Parameter(description = "部门用户ID") @RequestParam Long userId,
                                @Parameter(description = "对话类型: 0=普通聊天, 1=促销活动/销售额, 2=公众号相关") @RequestParam(required = false) Integer type) {
        GbAiConversationEntity conv = chatService.getOrCreateConversation(departmentId, userId, type);
        return R.ok().put("data", conv);
    }

    /**
     * 发送消息（非流式，用于测试）
     *
     * @Description 发送消息并获取AI回复（非流式）
     */
    @PostMapping("/send")
    @Operation(summary = "发送消息（非流式）", description = "发送消息并获取AI回复（非流式）")
    public R sendMessage(@Parameter(description = "对话ID") @RequestParam Long conversationId,
                         @RequestBody SendMessageDTO body) {
        String message = body.getMessage();
        Long userId = body.getUserId();
        GbAiMessageEntity reply = chatService.chat(conversationId, userId, message);
        return R.ok().put("data", reply);
    }

    /**
     * 发送消息（SSE 流式）
     *
     * @Description 发送消息并以SSE流式返回AI回复
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE流式）", description = "发送消息并以SSE流式返回AI回复")
    public SseEmitter streamChat(@Parameter(description = "对话ID") @RequestParam Long conversationId,
                                 @RequestBody SendMessageDTO body) {
        String message = body.getMessage();
        Long userId = body.getUserId();
        return chatService.streamChat(conversationId, userId, message);
    }

    /**
     * 获取对话历史
     *
     * @Description 获取指定对话的所有消息历史
     */
    @GetMapping("/history/{conversationId}")
    @Operation(summary = "获取对话历史", description = "获取指定对话的所有消息历史")
    public R getHistory(@Parameter(description = "对话ID") @PathVariable Long conversationId) {
        List<GbAiMessageEntity> messages = chatService.getConversationMessages(conversationId);
        return R.ok().put("data", messages);
    }

    /**
     * 结束对话（触发记忆提取）
     *
     * @Description 结束对话并触发记忆提取流程
     */
    @PostMapping("/end/{conversationId}")
    @Operation(summary = "结束对话", description = "结束对话并触发记忆提取流程")
    public R endConversation(@Parameter(description = "对话ID") @PathVariable Long conversationId) {
        chatService.endConversation(conversationId);
        return R.ok("对话已结束，记忆已提取");
    }

    /**
     * 手动触发 AutoDream
     *
     * @Description 手动触发AutoDream记忆提取任务
     */
    @PostMapping("/autodream")
    @Operation(summary = "手动触发AutoDream", description = "手动触发AutoDream记忆整理任务")
    public R autoDream() {
        memoryService.autoDream();
        return R.ok("AutoDream 执行完成");
    }

    /**
     * 请求DTO
     */
    public static class SendMessageDTO {
        private String message;
        private Long userId;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}
