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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI对话接口
 */
@Slf4j
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
        String sourceTopicId = body.getSourceTopicId();
        int msgLen = message != null ? message.length() : 0;
        String preview = message == null ? "" : (msgLen <= 200 ? message : message.substring(0, 200) + "…");
        log.info("[AI-CHAT][send] step=entry conversationId={} userId={} sourceTopicId={} messageChars={} messagePreview={}",
                conversationId, userId, sourceTopicId, msgLen, preview);
        GbAiMessageEntity reply = chatService.chat(conversationId, userId, message);
        String replyContent = reply != null ? reply.getGbAiMessageContent() : null;
        int replyLen = replyContent != null ? replyContent.length() : 0;
        String replyPreview = replyContent == null ? "" : (replyLen <= 300 ? replyContent : replyContent.substring(0, 300) + "…");
        log.info("[AI-CHAT][send] step=exit conversationId={} sourceTopicId={} replyMessageId={} replyChars={} replyPreview={}",
                conversationId, sourceTopicId, reply != null ? reply.getGbAiMessageId() : null, replyLen, replyPreview);
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
        String sourceTopicId = body.getSourceTopicId();
        int msgLen = message != null ? message.length() : 0;
        String preview = message == null ? "" : (msgLen <= 200 ? message : message.substring(0, 200) + "…");
        log.info("[AI-CHAT][stream] step=entry conversationId={} userId={} sourceTopicId={} messageChars={} messagePreview={}",
                conversationId, userId, sourceTopicId, msgLen, preview);
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
     * 根据用户ID获取历史聊天主题
     *
     * @Description 返回该用户全部历史会话主题（按更新时间倒序）
     */
    @GetMapping("/topics/history/{userId}")
    @Operation(summary = "获取历史聊天主题", description = "根据用户ID返回历史聊天主题列表")
    public R getTopicHistoryByUser(@Parameter(description = "用户ID") @PathVariable Long userId) {
        System.out.println("userid" + userId);
        List<GbAiConversationEntity> topics = chatService.getUserConversationTopics(userId);
        return R.ok().put("data", topics);
    }

    /**
     * 根据主题ID获取聊天详细内容
     *
     * @Description 主题ID即 conversationId
     */
    @GetMapping("/topic/{topicId}/messages")
    @Operation(summary = "获取主题聊天详情", description = "根据主题ID获取历史聊天详细内容")
    public R getTopicMessages(@Parameter(description = "主题ID（conversationId）") @PathVariable Long topicId) {
        List<GbAiMessageEntity> messages = chatService.getTopicMessages(topicId);
        return R.ok().put("data", messages);
    }

    /**
     * 获取首页推荐对话主题（算账版）
     *
     * @Description 返回前台可直接展示的 AI 主题卡片（标题、说明、建议引导语）
     */
    @GetMapping("/topics")
    @Operation(summary = "获取推荐对话主题", description = "返回老板算账场景的推荐主题卡片")
    public R getRecommendedTopics() {
        List<Map<String, Object>> topics = new ArrayList<>();
        topics.add(topic("dish-profit",
                "这道菜到底赚不赚钱？",
                "对比菜品销量、配料消耗和出库分摊，快速找出成本偏差最大的菜。",
                List.of(
                        question("dish-profit-top2", "先看高销量里的利润风险",
                                "帮我看这个月哪道菜卖得多但利润最危险，先给我前2名。"),
                        question("dish-profit-diff", "先看成本差额最大的菜",
                                "按成本差额从高到低排前3道菜，告诉我每道菜最可能的问题配料。"),
                        question("dish-profit-bottleneck", "先看瓶颈原料",
                                "先找出最卡脖子的原料对应了哪些菜，并给我本周处理优先级。")
                )));
        topics.add(topic("procurement-structure",
                "这个月钱主要花在哪？",
                "按采购、自采、供应商和未结账结构拆账，找出支出大头和现金流风险。",
                List.of(
                        question("procurement-top3", "先看采购结构前3",
                                "把本月采购、自采、未结账按金额排个前3，告诉我先盯哪里。"),
                        question("procurement-supplier-risk", "先看供应商应付风险",
                                "按供应商列出未结账金额前3，给我一句话风险判断和建议动作。"),
                        question("procurement-self-purchase", "先看自采是否异常",
                                "帮我判断本月自采占比是否异常，并给我本周可执行的优化建议。")
                )));
        topics.add(topic("profit-gap",
                "我离保本还差多少？",
                "结合租金、工资、固定成本与本月营收，判断当前经营压力和保本缺口。",
                List.of(
                        question("profit-gap-month", "先看本月保本压力",
                                "按现在的数据，帮我算本月经营压力，并给我本周最优先的两件事。"),
                        question("profit-gap-fixed-cost", "先看固定成本缺口",
                                "先按固定成本和当前营收判断我离保本还差多少，并说明关键缺口。"),
                        question("profit-gap-action", "先看三步止损动作",
                                "别讲大道理，按数据给我3个本周就能执行的止损动作。")
                )));
        topics.add(topic("weekly-checkup",
                "做一次本周算账体检",
                "自动汇总营收、成本、菜品异常三张清单，给老板一页能执行的结论。",
                List.of(
                        question("weekly-checkup-brief", "一页结论版",
                                "给我做本周算账体检：营收、成本、菜品异常各说一句和一个动作。"),
                        question("weekly-checkup-risk", "风险优先版",
                                "先列本周最危险的3个经营信号，再给对应动作。"),
                        question("weekly-checkup-opportunity", "机会优先版",
                                "先看本周最值得放大的2个机会点，并给出具体执行建议。")
                )));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "老板算账助手");
        data.put("subtitle", "先选主题，再选一个具体问题开始对话。");
        data.put("topics", topics);
        return R.ok().put("data", data);
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
        /**
         * 可选：前端话题卡片 ID（来自 /ai/chat/topics），仅用于日志埋点。
         */
        private String sourceTopicId;

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

        public String getSourceTopicId() {
            return sourceTopicId;
        }

        public void setSourceTopicId(String sourceTopicId) {
            this.sourceTopicId = sourceTopicId;
        }
    }

    private static Map<String, Object> topic(String id, String title, String summary,
                                             List<Map<String, String>> questions) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", id);
        t.put("title", title);
        t.put("summary", summary);
        t.put("questions", questions);
        return t;
    }

    private static Map<String, String> question(String id, String title, String prompt) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("title", title);
        q.put("prompt", prompt);
        return q;
    }
}
