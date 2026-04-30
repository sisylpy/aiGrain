package com.nongxinle.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.entity.GbAiDailyRevenueEntity;
import com.nongxinle.ai.orchestration.SkillHandoffParser;
import com.nongxinle.ai.orchestration.SkillHandoffPayload;
import com.nongxinle.ai.orchestration.SkillRouteFallback;
import com.nongxinle.ai.orchestration.SkillSelectionLlmParser;
import com.nongxinle.ai.orchestration.SkillSelectionResult;
import com.nongxinle.entity.GbDistributerPurchaseBatchEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;
import com.nongxinle.entity.GbDepFoodSalesEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.entity.GbDepFoodEntity;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.mapper.GbAiRestaurantProfileMapper;
import com.nongxinle.mapper.GbAiDailyRevenueMapper;
import com.nongxinle.mapper.GbDistributerPurchaseBatchMapper;
import com.nongxinle.mapper.GbDistributerPurchaseGoodsMapper;
import com.nongxinle.mapper.NxJrdhSupplierMapper;
import com.nongxinle.mapper.GbDepFoodSalesMapper;
import com.nongxinle.mapper.GbDepartmentGoodsStockMapper;
import com.nongxinle.mapper.GbDepartmentGoodsStockReduceMapper;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.mapper.GbDistributerFoodMapper;
import com.nongxinle.mapper.GbDistributerGoodsMapper;
import com.nongxinle.service.GbAiChatService;
import com.nongxinle.service.GbAiMemoryService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import com.nongxinle.service.GbDepFoodService;
import com.nongxinle.service.GbDepartmentReorderReminderService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import com.nongxinle.utils.GrossMarginStandardDisplay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI对话引擎实现
 * 完整版：包含Skill选择、数据查询、DeepSeek调用、记忆提取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiChatServiceImpl implements GbAiChatService {

    private final GbAiConversationMapper conversationMapper;
    private final GbAiMessageMapper messageMapper;
    private final GbAiMemoryService memoryService;
    private final GbDepartmentMapper departmentMapper;
    private final GbAiRestaurantProfileMapper restaurantProfileMapper;
    private final GbAiRestaurantProfileService gbAiRestaurantProfileService;
    private final GbAiDailyRevenueMapper dailyRevenueMapper;
    private final GbDistributerPurchaseBatchMapper distributerPurchaseBatchMapper;
    private final GbDistributerPurchaseGoodsMapper distributerPurchaseGoodsMapper;
    private final NxJrdhSupplierMapper nxJrdhSupplierMapper;
    private final GbDepartmentGoodsStockMapper departmentGoodsStockMapper;
    private final GbDepartmentGoodsStockReduceMapper stockReduceMapper;
    private final GbDistributerGoodsMapper distributerGoodsMapper;
    private final GbDepFoodSalesMapper depFoodSalesMapper;
    private final GbDistributerFoodMapper distributerFoodMapper;
    private final GbDepFoodService gbDepFoodService;
    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDepartmentReorderReminderService gbDepartmentReorderReminderService;

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${ai.deepseek.model}")
    private String model;

    @Value("${ai.deepseek.max-tokens}")
    private int maxTokens;

    @Value("${ai.deepseek.temperature}")
    private double temperature;

    @Value("${ai.deepseek.timeout-seconds}")
    private int timeoutSeconds;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // ========== 常量 ==========

    /** 历史消息保留数量 */
    private static final int MAX_HISTORY_MESSAGES = 20;

    /** Skill文件列表 */
    private static final List<String> SKILL_FILES = List.of(
            "ai-skill-cost.md",
            "ai-skill-revenue-boost.md",
            "ai-skill-data-extractor.md",
            "ai-skill-dish-cost-diagnosis.md",
            "ai-skill-procurement-structure.md",
            "ai-skill-profit-pilot.md"
    );

    /** DeepSeek 监控：system 角色 content 预览字符上限（INFO 级别，避免单条日志过大） */
    private static final int LOG_PREVIEW_SYSTEM_CHARS = 1500;
    /** DeepSeek 监控：user/assistant 单条预览上限 */
    private static final int LOG_PREVIEW_MESSAGE_CHARS = 800;
    /** DeepSeek 返回正文预览上限 */
    private static final int LOG_PREVIEW_RESPONSE_CHARS = 1600;

    /**
     * 组完主对话请求后的结果：正常调用主模型，或因成本三项固定成本未齐而短路（不调用第二次 DeepSeek）。
     */
    private record BuiltChatPayload(List<Map<String, String>> messages, boolean skipMainDeepSeek, String gateAssistantReply) {}

    // ========== 对话核心方法 ==========

    @Override
    public GbAiConversationEntity getOrCreateConversation(Long departmentId, Long userId, Integer type) {
        log.info("获取或创建对话 - departmentId={}, userId={}, type={}", departmentId, userId, type);

        LambdaQueryWrapper<GbAiConversationEntity> wrapper = new LambdaQueryWrapper<GbAiConversationEntity>()
                .eq(GbAiConversationEntity::getGbAiConversationDepartmentId, departmentId)
                .eq(GbAiConversationEntity::getGbAiConversationUserId, userId)
                .eq(GbAiConversationEntity::getGbAiConversationStatus, 0)
                .orderByDesc(GbAiConversationEntity::getGbAiConversationUpdateTime)
                .last("LIMIT 1");

        if (type != null) {
            wrapper.eq(GbAiConversationEntity::getGbAiConversationType, type);
        }

        GbAiConversationEntity existing = conversationMapper.selectOne(wrapper);
        if (existing != null) {
            log.info("找到现有对话 - conversationId={}", existing.getGbAiConversationId());
            return existing;
        }

        // 创建新对话
        GbAiConversationEntity conv = new GbAiConversationEntity();
        conv.setGbAiConversationDepartmentId(departmentId);
        conv.setGbAiConversationUserId(userId);
        conv.setGbAiConversationStatus(0);
        conv.setGbAiConversationTitle("新对话");
        conv.setGbAiConversationCreateTime(new Date());
        conv.setGbAiConversationUpdateTime(new Date());
        conv.setGbAiConversationType(type != null ? type : 0);

        conversationMapper.insert(conv);
        log.info("创建新对话成功 - conversationId={}", conv.getGbAiConversationId());
        return conv;
    }

    @Override
    public GbAiMessageEntity chat(Long conversationId, Long userId, String userMessage) {
        log.info("[AI-CHAT][service] trace=non-stream step=start conversationId={} userId={} userChars={}",
                conversationId, userId, userMessage != null ? userMessage.length() : 0);

        try {
            // 1. 获取对话信息
            GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
            if (conv == null) {
                log.error("对话不存在: conversationId={}", conversationId);
                throw new RuntimeException("对话不存在");
            }

            Long departmentId = conv.getGbAiConversationDepartmentId();
            Integer conversationType = conv.getGbAiConversationType();
            log.info("[AI-CHAT][service] step=load_conversation conversationId={} departmentId={} conversationType={}",
                    conversationId, departmentId, conversationType);

            // 2. 保存用户消息
            log.info("[AI-CHAT][service] step=persist_user_message conversationId={}", conversationId);
            saveMessage(conversationId, userId, conversationType, "user", userMessage);

            // 3. 构建消息列表（内含第一次 DeepSeek：Skill 选择）
            log.info("[AI-CHAT][service] step=build_messages_begin conversationId={}", conversationId);
            BuiltChatPayload payload = buildChatPayload(conv, userMessage);
            log.info("[AI-CHAT][service] step=build_messages_done conversationId={} outboundMessageCount={} skipMainDeepSeek={}",
                    conversationId, payload.messages().size(), payload.skipMainDeepSeek());

            // 4. 主回复：完整走 DeepSeek，或固定成本门禁短路（不调主模型）
            String assistantReplyRaw;
            if (payload.skipMainDeepSeek()) {
                assistantReplyRaw = payload.gateAssistantReply();
                log.info("[AI-CHAT][service] step=main_reply_fixed_cost_gate conversationId={} replyChars={} (no main DeepSeek)",
                        conversationId, assistantReplyRaw != null ? assistantReplyRaw.length() : 0);
            } else {
                assistantReplyRaw = callDeepSeekApi(payload.messages(), "main-reply");
                log.info("[AI-CHAT][service] step=main_reply_received conversationId={} replyChars={}",
                        conversationId, assistantReplyRaw != null ? assistantReplyRaw.length() : 0);
            }

            // 画像抽取：先去掉 skill_handoff 块，避免误解析；若有移交则再调一轮修订
            String forProfileExtract = SkillHandoffParser.stripAllSkillHandoffFences(assistantReplyRaw);
            extractUserDataFromReply(forProfileExtract, departmentId);

            String assistantReply = assistantUserVisibleAfterOptionalHandoff(assistantReplyRaw, userMessage, departmentId);

            // 5. 保存 AI 回复
            log.info("[AI-CHAT][service] step=persist_assistant_message conversationId={}", conversationId);
            GbAiMessageEntity assistantMsg = saveMessage(conversationId, userId, conversationType, "assistant", assistantReply);

            // 6. 更新对话时间
            conv.setGbAiConversationUpdateTime(new Date());
            if ("新对话".equals(conv.getGbAiConversationTitle()) && assistantReply.length() > 5) {
                String title = assistantReply.substring(0, Math.min(assistantReply.length(), 30));
                if (title.length() < assistantReply.length()) title += "...";
                conv.setGbAiConversationTitle(title);
            }
            conversationMapper.updateById(conv);

            // 7. 异步提取记忆
            log.info("[AI-CHAT][service] step=async_memory_extract conversationId={}", conversationId);
            memoryService.extractMemories(conversationId, departmentId, userId, conversationType);

            // 8. 异步提取用户数据（移交修订轮已在 assistantUserVisibleAfterOptionalHandoff 内处理）
            log.info("[AI-CHAT][service] step=async_profile_extract conversationId={}", conversationId);

            log.info("[AI-CHAT][service] trace=non-stream step=end_ok conversationId={}", conversationId);
            return assistantMsg;

        } catch (Exception e) {
            log.error("对话处理异常: {}", e.getMessage(), e);
            throw new RuntimeException("AI 对话处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public SseEmitter streamChat(Long conversationId, Long userId, String userMessage) {
        log.info("[AI-CHAT][service] trace=sse step=start conversationId={} userId={} userChars={}",
                conversationId, userId, userMessage != null ? userMessage.length() : 0);

        SseEmitter emitter = new SseEmitter(120000L);

        try {
            GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
            if (conv == null) {
                emitter.send(SseEmitter.event().name("error").data("对话不存在"));
                emitter.complete();
                return emitter;
            }

            Long departmentId = conv.getGbAiConversationDepartmentId();
            Integer conversationType = conv.getGbAiConversationType();
            log.info("[AI-CHAT][service] trace=sse step=load_conversation conversationId={} departmentId={}",
                    conversationId, departmentId);

            // 保存用户消息
            saveMessage(conversationId, userId, conversationType, "user", userMessage);

            // 构建消息（内含 Skill 选择 DeepSeek 调用）
            log.info("[AI-CHAT][service] trace=sse step=build_messages_begin conversationId={}", conversationId);
            BuiltChatPayload payload = buildChatPayload(conv, userMessage);
            log.info("[AI-CHAT][service] trace=sse step=build_messages_done conversationId={} outboundMessageCount={} skipMainDeepSeek={}",
                    conversationId, payload.messages().size(), payload.skipMainDeepSeek());

            if (payload.skipMainDeepSeek()) {
                log.info("[AI-CHAT][service] trace=sse step=main_reply_fixed_cost_gate conversationId={}", conversationId);
                completeSseWithDirectAssistantReply(emitter, payload.gateAssistantReply(),
                        conversationId, userId, conv, departmentId);
            } else {
                callDeepSeekSSE(payload.messages(), emitter, conversationId, userId, conv, departmentId, userMessage);
            }

            // 触发记忆提取
            memoryService.extractMemories(conversationId, departmentId, userId, conversationType);

            return emitter;

        } catch (Exception e) {
            log.error("流式对话异常: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("处理异常"));
            } catch (Exception ignored) {}
            emitter.completeWithError(e);
            return emitter;
        }
    }

    @Override
    public List<GbAiMessageEntity> getConversationMessages(Long conversationId) {
        log.debug("获取对话消息 - conversationId={}", conversationId);
        return messageMapper.selectList(
                new LambdaQueryWrapper<GbAiMessageEntity>()
                        .eq(GbAiMessageEntity::getGbAiMessageConversationId, conversationId)
                        .orderByAsc(GbAiMessageEntity::getGbAiMessageCreateTime)
        );
    }

    @Override
    public List<GbAiConversationEntity> getUserConversationTopics(Long userId) {
        log.info("获取用户历史聊天主题 - userId={}", userId);
        return conversationMapper.selectList(
                new LambdaQueryWrapper<GbAiConversationEntity>()
                        .eq(GbAiConversationEntity::getGbAiConversationUserId, userId)
                        .orderByDesc(GbAiConversationEntity::getGbAiConversationUpdateTime)
        );
    }

    @Override
    public List<GbAiMessageEntity> getTopicMessages(Long topicId) {
        return getConversationMessages(topicId);
    }

    @Override
    public int endConversation(Long conversationId) {
        log.info("结束对话 - conversationId={}", conversationId);

        GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            log.warn("结束对话：会话不存在 - conversationId={}", conversationId);
            return 0;
        }
        conv.setGbAiConversationStatus(1);
        conv.setGbAiConversationUpdateTime(new Date());
        conversationMapper.updateById(conv);

        List<GbAiMessageEntity> messages = getConversationMessages(conversationId);
        if (!hasConversationSubstance(messages)) {
            log.info("对话无实质内容，跳过记忆与总结 - conversationId={}", conversationId);
            return 1;
        }

        // 1. 规则记忆：标记消息已处理
        memoryService.extractMemories(conversationId,
                conv.getGbAiConversationDepartmentId(),
                conv.getGbAiConversationUserId(),
                conv.getGbAiConversationType());

        // 2. DeepSeek 总结并保存
        try {
            String summaryResult = summarizeConversation(conversationId);
            memoryService.saveConversationSummary(conversationId,
                    conv.getGbAiConversationDepartmentId(),
                    conv.getGbAiConversationUserId(),
                    summaryResult);
        } catch (Exception e) {
            log.error("DeepSeek总结对话失败: {}", e.getMessage(), e);
        }

        log.info("对话已结束 - conversationId={}", conversationId);
        return 2;
    }

    /**
     * 是否至少有一条非空白消息（有内容才值得总结、落库）。
     */
    private static boolean hasConversationSubstance(List<GbAiMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (GbAiMessageEntity m : messages) {
            if (m != null && StrUtil.isNotBlank(m.getGbAiMessageContent())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String summarizeConversation(Long conversationId) {
        log.info("========== 使用DeepSeek总结对话 ==========");
        log.info("conversationId={}", conversationId);

        // 1. 获取对话信息
        GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            log.error("对话不存在: conversationId={}", conversationId);
            return "{\"error\": \"对话不存在\"}";
        }

        // 2. 获取所有对话消息
        List<GbAiMessageEntity> messages = getConversationMessages(conversationId);
        if (messages.isEmpty()) {
            log.info("对话无消息，直接返回空总结");
            return "{\"conversationTopic\": \"空对话\", \"summary\": \"\", \"memories\": [], \"commitments\": []}";
        }

        // 3. 构建对话文本
        StringBuilder dialogText = new StringBuilder();
        for (GbAiMessageEntity msg : messages) {
            String role = "user".equals(msg.getGbAiMessageRole()) ? "老板" : "钱多多老师";
            dialogText.append("【").append(role).append("】").append(msg.getGbAiMessageContent()).append("\n\n");
        }

        // 4. 加载对话总结Skill
        String skillContent = loadSkillFile("ai-skill-conversation-summary.md");
        log.info("对话总结Skill加载完成，长度: {} 字", skillContent.length());

        // 5. 构建总结Prompt
        String summaryPrompt = buildConversationSummaryPrompt(skillContent, dialogText.toString(), conv);
        log.info("[AI-CHAT][summary] conversationId={} summaryPromptChars={} preview={}",
                conversationId, summaryPrompt.length(), abbreviateForLog(summaryPrompt, LOG_PREVIEW_SYSTEM_CHARS));
        log.debug("【对话总结Prompt 全文】\n{}", summaryPrompt);

        // 6. 调用DeepSeek API
        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", summaryPrompt));

        String summaryResult = callDeepSeekApi(requestMessages, "conversation-summary");
        log.info("[AI-CHAT][summary] conversationId={} resultChars={}", conversationId, summaryResult.length());
        log.debug("【总结结果 全文】\n{}", summaryResult);

        return summaryResult;
    }

    /**
     * 构建对话总结Prompt
     */
    private String buildConversationSummaryPrompt(String skillContent, String dialogText, GbAiConversationEntity conv) {
        StringBuilder sb = new StringBuilder();

        // 身份设定
        sb.append("【身份设定】你是钱多多老师的\"记忆管家\"，负责对餐饮老板的对话进行深度总结和记忆提取。\n");
        sb.append("你的任务是从对话中提取有价值的信息，生成结构化的记忆摘要。\n\n");

        // 参考技能
        sb.append("【参考技能】\n");
        sb.append("ai-skill-conversation-summary\n\n");

        // 对话内容
        sb.append("【对话内容】\n");
        sb.append(dialogText).append("\n\n");

        // 任务说明
        sb.append("【任务】\n");
        sb.append("请按照技能的指导，对以上对话进行总结和记忆提取。\n");
        sb.append("输出格式必须为JSON，包含conversationTopic、summary、memories和commitments四个字段。\n");
        sb.append("如果对话中没有有价值的信息，memories和commitments可以为空数组。\n\n");

        // 输出要求
        sb.append("【输出要求】\n");
        sb.append("只输出JSON格式的结果，不要添加其他解释文字。\n");
        sb.append("JSON示例：\n");
        sb.append("{\n");
        sb.append("  \"conversationTopic\": \"讨论如何提升午市营业额\",\n");
        sb.append("  \"summary\": \"老板月租12000元，当前午市客流约50人...\",\n");
        sb.append("  \"memories\": [...],\n");
        sb.append("  \"commitments\": [...]\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ========== 消息构建 ==========

    /**
     * 是否已具备成本分析所需的月租金、月工资、月固定成本（其它）三项。
     */
    private boolean hasAllBasicFixedCosts(GbAiRestaurantProfileEntity profile) {
        if (profile == null) {
            return false;
        }
        return profile.getGbAiRestaurantProfileRentMonthly() != null
                && profile.getGbAiRestaurantProfileMonthlyWage() != null
                && profile.getGbAiRestaurantProfileMonthlyFixedCost() != null;
    }

    /**
     * 选中成本 skill 但三项固定成本不全时，直接给老板的说明（不调主模型）。
     */
    private String buildFixedCostMissingGateReply(GbAiRestaurantProfileEntity profile) {
        boolean hasRent = profile != null && profile.getGbAiRestaurantProfileRentMonthly() != null;
        boolean hasWage = profile != null && profile.getGbAiRestaurantProfileMonthlyWage() != null;
        boolean hasFixed = profile != null && profile.getGbAiRestaurantProfileMonthlyFixedCost() != null;

        List<String> missing = new ArrayList<>();
        if (!hasRent) {
            missing.add("月租金");
        }
        if (!hasWage) {
            missing.add("每月工资总支出");
        }
        if (!hasFixed) {
            missing.add("其它月固定成本（水电、物业等合计，不含人工）");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("钱多多老师要帮你把「本月成本」算清楚，必须先在你的门店档案里凑齐三项**月固定成本**，否则后面的食材、损耗、营业额对比会缺基准，容易算偏。\n\n");
        sb.append("系统里目前还缺：").append(String.join("、", missing)).append("。\n\n");
        sb.append("你直接回我三个数字就行，例如：「房租 12000，工资 35000，其它固定 2000」。\n");
        sb.append("我会帮你记进档案，再接着拆本月成本结构。");
        if (hasRent || hasWage || hasFixed) {
            sb.append("\n\n（你已填过的项我会保留，只补上面缺的。）");
        }
        return sb.toString();
    }

    /**
     * 流式：不调用 DeepSeek，把固定成本门禁说明一次发给前端并落库。
     */
    private void completeSseWithDirectAssistantReply(SseEmitter emitter, String reply,
            Long conversationId, Long userId, GbAiConversationEntity conv, Long departmentId) {
        try {
            if (StrUtil.isNotEmpty(reply)) {
                String cleaned = SkillHandoffParser.stripAllSkillHandoffFences(reply);
                String visible = stripAssistantUserVisibleTail(cleaned);
                extractUserDataFromReply(cleaned, departmentId);
                emitter.send(SseEmitter.event().name("message").data(visible));
                saveMessage(conversationId, userId, conv.getGbAiConversationType(), "assistant", visible);
                conv.setGbAiConversationUpdateTime(new Date());
                if ("新对话".equals(conv.getGbAiConversationTitle()) && visible.length() > 5) {
                    conv.setGbAiConversationTitle(visible.substring(0, Math.min(visible.length(), 30)) + "...");
                }
                conversationMapper.updateById(conv);
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            log.error("[AI-CHAT][sse] fixed_cost_gate emit failed: {}", e.getMessage(), e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 构建主对话请求
     * 流程：Skill选择（DeepSeek）-> 若命中成本且三项固定成本不全则短路 -> 否则查库组最终 Prompt
     */
    private BuiltChatPayload buildChatPayload(GbAiConversationEntity conv, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        Long departmentId = conv.getGbAiConversationDepartmentId();
        Integer conversationType = conv.getGbAiConversationType();

        // 1. 加载所有 Skills 摘要（用于Skill选择）
        String skillsBrief = loadAllSkillsBrief();
        log.debug("Skills 摘要长度: {} 字", skillsBrief.length());

        // 2. 第一次调用 DeepSeek：让AI分析问题，选择合适的 Skill
        Long convId = conv.getGbAiConversationId();
        log.info("[AI-CHAT][build] step=skill_selection_begin conversationId={}", convId);
        String skillSelectionPrompt = buildSkillSelectionPrompt(userMessage, skillsBrief);
        List<Map<String, String>> skillMessages = new ArrayList<>();
        skillMessages.add(Map.of("role", "system", "content", skillSelectionPrompt));
        skillMessages.add(Map.of("role", "user", "content", "用户问题：" + userMessage));

        // INFO 只打长度：正文预览由 callDeepSeekApi → [DeepSeek-REQ] 统一打印，避免与 build 重复
        log.info("[AI-CHAT][build] step=skill_selection_prompt conversationId={} systemPromptChars={}",
                convId, skillSelectionPrompt.length());
        log.debug("[AI-CHAT][build] step=skill_selection_prompt preview={}",
                abbreviateForLog(skillSelectionPrompt, LOG_PREVIEW_SYSTEM_CHARS));
        log.debug("【Skill选择 - System 全文】\n{}", skillSelectionPrompt);

        String rawSkillSelection = callDeepSeekApi(skillMessages, "skill-selection");
        SkillSelectionResult llmParsed = SkillSelectionLlmParser.parseRaw(rawSkillSelection, userMessage);
        SkillSelectionResult selection = SkillRouteFallback.apply(userMessage, llmParsed);
        String selectedSkills = selection.skillsCsv();
        log.info("[AI-CHAT][build] step=skill_selection_parsed conversationId={} skillsCsv={} costFacet={} broadQuestion={} routeSource={} confidence={} rawChars={}",
                convId, selectedSkills, selection.costFacet(), selection.broadQuestion(), selection.routeSource(),
                selection.confidence(),
                rawSkillSelection != null ? rawSkillSelection.length() : 0);
        log.debug("Skill选择原始响应全文: {}", rawSkillSelection);

        // 2b. 成本 skill + 三项固定成本不全：不调主 DeepSeek，不查库存/营收明细，直接门禁回复
        String skillsLower = selectedSkills != null ? selectedSkills.toLowerCase(Locale.ROOT) : "";
        if (skillsLower.contains("cost")) {
            GbAiRestaurantProfileEntity profile = restaurantProfileMapper.selectOne(
                    new LambdaQueryWrapper<GbAiRestaurantProfileEntity>()
                            .eq(GbAiRestaurantProfileEntity::getGbAiRestaurantProfileDepartmentId, departmentId));
            if (!hasAllBasicFixedCosts(profile)) {
                log.info("[AI-CHAT][build] step=fixed_cost_gate_short_circuit conversationId={} departmentId={}",
                        convId, departmentId);
                return new BuiltChatPayload(Collections.emptyList(), true, buildFixedCostMissingGateReply(profile));
            }
        }

        // 3. 根据选择的 Skill 类型，决定查询哪些相关数据
        log.info("[AI-CHAT][build] step=query_real_data_begin conversationId={}", convId);
        String realDataSection = queryRealDataBySkills(departmentId, selectedSkills, userMessage, selection.costFacet());
        log.info("[AI-CHAT][build] step=query_real_data_done conversationId={} sectionChars={}",
                convId, realDataSection.length());
        log.debug("[AI-CHAT][build] step=query_real_data_done preview={}",
                abbreviateForLog(realDataSection, 700));

        // 4. 构建最终 Prompt（包含身份 + 选中的 Skill名字 + 相关数据）
        String finalSystemPrompt = buildFinalSystemPrompt(selectedSkills, realDataSection, conversationType,
                selection.costFacet(), selection.broadQuestion());
        log.info("[AI-CHAT][build] step=final_system_prompt conversationId={} totalChars={}",
                convId, finalSystemPrompt.length());
        log.debug("[AI-CHAT][build] step=final_system_prompt preview={}",
                abbreviateForLog(finalSystemPrompt, LOG_PREVIEW_SYSTEM_CHARS));
        log.debug("【最终SystemPrompt 全文】\n{}", finalSystemPrompt);

        // 6. 添加 System Prompt 到消息列表
        messages.add(Map.of("role", "system", "content", finalSystemPrompt));
        log.info("已添加 System Prompt 到消息列表");

        // 7. 添加历史消息（当前轮用户句已在 chat() 里持久化，避免在末尾再叠一条重复 user）
        List<GbAiMessageEntity> history = getConversationMessages(conv.getGbAiConversationId());
        int historyEnd = history.size();
        if (!history.isEmpty()) {
            GbAiMessageEntity last = history.get(history.size() - 1);
            if ("user".equals(last.getGbAiMessageRole())
                    && userMessage != null
                    && userMessage.equals(last.getGbAiMessageContent())) {
                historyEnd = history.size() - 1;
            }
        }
        log.info("历史消息数量: {} 条（注入模型 {} 条，已去重当前 user）", history.size(), historyEnd);

        int startIdx = Math.max(0, historyEnd - MAX_HISTORY_MESSAGES);
        for (int i = startIdx; i < historyEnd; i++) {
            GbAiMessageEntity msg = history.get(i);
            String role = msg.getGbAiMessageRole();
            String content = msg.getGbAiMessageContent();
            messages.add(Map.of("role", role, "content", content));
            log.debug("添加历史消息 - role={}, content长度={}", role, content.length());
        }

        // 8. 添加当前用户消息
        messages.add(Map.of("role", "user", "content", userMessage));
        log.info("消息列表构建完成，共 {} 条", messages.size());

        return new BuiltChatPayload(messages, false, null);
    }

    /**
     * 构建用于 Skill 选择的 Prompt
     */
    private String buildSkillSelectionPrompt(String userMessage, String skillsContent) {
        return "你是AI技能选择助手。根据用户的问题，从以下技能文件中选择最合适的1-2个技能。\n\n" +
                "【技能列表】\n" + skillsContent + "\n\n" +
                "【选择规则】\n" +
                "1. 问题涉及成本、费用、支出、利润、损耗、食材费用等（含很宽泛的「成本高」「帮我看成本」），至少包含 ai-skill-cost.md 或 ai-skill-profit-pilot.md\n" +
                "1a. 问“哪道菜赚钱/亏钱、配料成本、瓶颈原料、出库分摊”，优先 ai-skill-dish-cost-diagnosis.md（可同时含 ai-skill-cost.md）\n" +
                "1b. 问采购、进货、自采、供货商订货、订货/采购频率或节奏、供应商占比、应付未结、采购单价波动/进价涨跌，优先 ai-skill-procurement-structure.md（可同时含 ai-skill-cost.md）\n" +
                "1c. 问“本月算账、保本、赚不赚钱、经营盘子”，优先 ai-skill-profit-pilot.md\n" +
                "2. 问题涉及营收提升、客流、促销活动，选择 ai-skill-revenue-boost.md\n" +
                "3. 用户明确给出要记录的数字（租金、工资、营业额等），可同时选 ai-skill-data-extractor.md\n" +
                "4. 可同时选 1-2 个技能，用 skills 数组列出\n\n" +
                "【costFacet】仅当 skills 中含 ai-skill-cost.md 时填写；**不含 cost skill 时必须为 null**（即便问题完全是采购/订货频率话题）。表示老板成本关切子类（单选其一）：\n" +
                "overview | dish_structure | procurement | waste | margin | supplier | time_series | pricing | mixed\n" +
                "无法判断或极宽泛时用 mixed；若明显谈供应商用 supplier，谈报废损耗用 waste，依此类推。\n" +
                "需要 procurement 子类且要走完整成本探索规则时，skills 请同时包含 ai-skill-cost.md 与 ai-skill-procurement-structure.md，此时再填 costFacet=procurement。\n\n" +
                "【broadQuestion】是否「泛指、未限定场景」的问法（与是否选 cost 无关，营收类泛问也必须标）：\n" +
                "- true：如「怎么提高营业额」「有什么建议」「帮我看看经营」等一两句、未说渠道/客群/时段/痛点\n" +
                "- false：已具体到「午市外卖」「老客复购」「五一活动」等\n\n" +
                "【输出格式】\n" +
                "只输出一行合法 JSON，不要 markdown 代码块，不要其它文字。\n" +
                "可选字段：\n" +
                "- confidence：0~1，表示你对 skills 选择的把握；把握低时服务端会用关键词规则兜底。\n" +
                "- primarySkill：与 skills[0] 一致即可，便于日志（可省略）。\n" +
                "示例：\n" +
                "{\"skills\":[\"ai-skill-dish-cost-diagnosis.md\"],\"costFacet\":\"dish_structure\",\"broadQuestion\":false,\"confidence\":0.9}\n" +
                "{\"skills\":[\"ai-skill-procurement-structure.md\"],\"costFacet\":null,\"broadQuestion\":false,\"confidence\":0.85}\n" +
                "{\"skills\":[\"ai-skill-procurement-structure.md\",\"ai-skill-cost.md\"],\"costFacet\":\"procurement\",\"broadQuestion\":false,\"confidence\":0.88}\n" +
                "{\"skills\":[\"ai-skill-revenue-boost.md\"],\"costFacet\":null,\"broadQuestion\":true,\"confidence\":0.55}\n" +
                "若无合适技能：{\"skills\":[],\"costFacet\":null,\"broadQuestion\":false}\n" +
                "若 JSON 合法但 skills 为空数组，服务端会用关键词规则再推断一次（仍可能得到 none）。\n\n" +
                "【用户问题】\n" + userMessage;
    }

    /**
     * 构建最终 System Prompt
     */
    private String buildFinalSystemPrompt(String skillNames, String realDataSection, Integer conversationType,
                                          String costFacet, boolean broadQuestion) {
        StringBuilder sb = new StringBuilder();
        String skillsLower = skillNames != null ? skillNames.toLowerCase(Locale.ROOT) : "";
        boolean costSkill = skillsLower.contains("cost");
        boolean procurementSkill = skillsLower.contains("procurement");
        boolean revenueSkill = skillsLower.contains("revenue") || skillsLower.contains("boost");

        // 身份设定（强化）
        sb.append("【身份设定】你是钱多多老师，资深餐饮营销顾问，拥有10年餐饮行业经验。\n");
        sb.append("你必须以\"钱多多老师\"的身份回复！\n");
        sb.append("说话风格：直接、短句、少废话；老板很忙，**宁可短而准，不要长而全**。\n");
        sb.append("咨询方式（苏格拉底）：**好问题胜过快答案**。老板问得泛、或关键事实未对齐时，你要像良师一样——先**少量、精准的追问**帮他把目标、场景、数据边界说清楚，再下判断或给方案；**禁止**用长篇结论代替追问。追问与后文「苏格拉底前置」「成本/营收探索模式」一致，是同一套人格，不是额外任务。\n");
        sb.append("回复格式：开头必须用\"钱多多老师\"！例如：\"钱多多老师直接给你算笔账\" 或 \"钱多多老师直接看数据\" 或 \"钱多多老师直接告诉你\"。\n");
        sb.append("你的目标是帮助餐饮老板优化经营、提升利润。\n\n");

        // 参考技能
        if (StrUtil.isNotEmpty(skillNames)) {
            sb.append("【参考技能】\n");
            sb.append("当前使用的技能：").append(skillNames).append("\n");
            if (costSkill && StrUtil.isNotEmpty(costFacet)) {
                sb.append("系统判定的成本关切子意图(costFacet)：").append(costFacet)
                        .append("（请优先围绕该角度组织分析，并与技能中的「关切菜单」一致）\n");
            }
            sb.append("\n");
        }

        // 技能全文（此前未注入会导致 md 规则不生效）
        String skillBodies = loadSelectedSkills(skillNames);
        if (StrUtil.isNotEmpty(skillBodies)) {
            sb.append("【技能细则】\n");
            sb.append(skillBodies);
        }

        // 真实数据
        sb.append("【餐厅真实数据】\n");
        sb.append(realDataSection).append("\n\n");

        // 仅 procurement-structure、未带 cost skill 时：【成本探索模式】不会注入，此处补上供货/频率硬约束（避免模型只按 md 里「自采占比」骨架胡写）
        if (procurementSkill && !costSkill) {
            sb.append("【采购模式硬约束（无 cost skill 时也必须遵守）】\n");
            sb.append("- **严禁措辞**：「全部为自采」「100%自采」「没有供货商配送」「全无供货商」「完全没有供货商」——除非上文 **供货属性摘要**、**全批发商入库供货维度**及【本月采购数据】摘录各行 **nx_supplier_id** 一致表明无任何供货商维度（type=5 与 type=1 且 nx 为正均为 0）。否则只能说「本统计口径下…」，并引用摘要数字。\n");
            sb.append("- **采购/订货频率**：若上文有【订货/到货频率与习惯】（gb_department_orders 到货），优先用它谈节奏；**若该块列表为空**（窗口内无「到货≥minTimes」候选），通常表示**部门订货订单未录入或未走该链路**，不等于「你没采购」或「每次都是临时采购」——此时改用【本月采购数据】按 **入库完成日** 描述到货密度即可，**禁止**顺带下供货商定性结论。\n");
            sb.append("- 【本月自采金额】块仅含 type=1，**不得**因它与采购合计接近就概括为「全部自采」。供货商维度以摘要与 nx 为准。\n");
            sb.append("- 正文宜短：**宁可短而准**；与上文【回复规则】4g、4h 冲突时以本块与真实数据为准。\n\n");
        }

        // 泛问句：营收/成本均可能命中；由第一步 broadQuestion 显式标记，强制先苏格拉底再方案
        if (broadQuestion && (costSkill || revenueSkill)) {
            sb.append("【苏格拉底前置（强约束）】\n");
            sb.append("本回合 broadQuestion=true：用户问题**较泛**，未限定场景/对象/渠道/时段。\n");
            sb.append("**禁止**首段直接列「建议1、2、3」或下结论式方案；**禁止**用「核心问题是…」代替追问。\n");
            sb.append("正文顺序必须是：① **1句**复述你对老板目标的理解；② **恰好2个**极短追问（单句、问号结尾，分别收窄不同维度）；③ **再**用不超过 **4句** 给方向提示，且至少 **1句** 引用上文【餐厅真实数据】中的具体数字。\n");
            if (revenueSkill) {
                sb.append("追问须结合技能书中的「营收关切维度」（引流/客单/复购/外卖/时段/活动）里**尚未明确**的点，勿问空泛「想要什么建议」。\n");
            }
            if (costSkill) {
                sb.append("若同时命中成本 skill，追问须对齐 costFacet 与技能书中「关切菜单」；未明确时先问清再分析。\n");
            }
            sb.append("仍须遵守全文篇幅上限。\n\n");
        }

        // 成本探索模式（苏格拉底 + 简版多维）
        if (costSkill) {
            sb.append("【成本探索模式】\n");
            sb.append("1. 若用户问题过宽（如只说成本高、帮我看成本），或尚未明确分析角度：最多提 **2 个** 简短的苏格拉底式澄清问题，帮助收窄到「关切菜单」中的某一类。\n");
            sb.append("2. 若【餐厅真实数据】中三项固定成本（月租金、月工资、月固定成本）任一缺失：只做门禁说明与补数引导，不要展开变动成本定量分析。\n");
            sb.append("3. 当三项固定成本已齐，但「本月营业额」记录天数明显少于当月已过天数，或「本月库存减少数据」中成本/合计为 0 且与老板预期不符时：**不得以「经营遇到大问题」「强烈提示」等话术下结论**；必须先做苏格拉底式澄清（**最多 2 个问题**），例如：营业额是否在别处登记、本系统是否未录全；库存流水是否挂在别的部门 ID。可简述保本测算思路，须标注「在流水不齐时仅供参考」。\n");
            sb.append("4. 当用户已明确分析角度且【餐厅真实数据】对应该角度已足够（营业额天数充足、库存段有非零金额等）：少问多答，优先逐字引用注入块中的数字与天数；不要重复追问已出现的数字。\n");
            sb.append("5. 多维思考：固定/变动、时间、损耗等**各点一句带过**即可，禁止展开成长篇论述。\n");
            sb.append("6. 禁止编造数据库未提供的维度（如具体菜名对应 ID、供应商名称、准时率）；仅有 ID 聚合时只能谈金额结构。\n");
            sb.append("7. **必须先阅读再回答**：凡上文【本月营业额数据】【本月库存减少数据】【本月采购数据】【本月供货商订货采购】【订货/到货频率与习惯】【本月采购单价波动（采购商品行）】【本月自采金额（采购商品行）】【供货商未结账款（采购批次）】中出现的金额、天数、行数，后文分析必须与之一致；**若【本月采购数据】标明「金额摘录已省略」**，不得以臆造明细回答采购金额排名。**采购单价波动**仅允许引用【本月采购单价波动】中的 **gb_DPG_buy_price（入库单价）**：最高/最低价必须同为该字段，**禁止**把 gb_DPG_buy_subtotal、摘录里的「金额」或其它总额当作单价；「给供货商订了哪些货」须用【本月供货商订货采购】（type=5），**勿与自采 type=1 混淆**；全量采购金额（若有注入）以【本月采购数据】为准；**自采金额**以【本月自采金额】为准；供货商欠款以【供货商未结账款】为准。**采购/进货/订货频率**若上文已注入【订货/到货频率与习惯】，回答频率优劣须以该块（gb_department_orders 到货）为主，**禁止**仅用【本月采购数据】入库笔数充当「订货频率」结论。**判断是否「全是自采、有无供货商配送」时**，必须同步阅读【本月采购数据】中的 **供货属性摘要**、**全批发商入库供货维度**及各摘录行的 **gb_DPG_purchase_nx_supplier_id**，**禁止**仅凭【本月自采金额】合计≈采购合计或「未见 type=5」断言「100%自采」「完全没有供货商配送」，也**禁止**在讨论频率话题时顺带做该类断言。\n");
            sb.append("8. **成本场景篇幅**：面向老板的正文（不含 JSON、不含「数据完整性」块）**严格控制在 380 字以内**。\n\n");
        }

        // 回复规则
        sb.append("【回复规则】\n");
        if (costSkill) {
            sb.append("1. 在成本探索模式下：澄清与基于数据并行——数据稀疏或可能未录全时以苏格拉底追问为主；数据已充分时直接分析，不重复追问已注入字段。\n");
        } else if (broadQuestion && revenueSkill) {
            sb.append("1. 本回合为营收类泛问：须遵守上文「苏格拉底前置」；不得以「不要询问已有数据」为由跳过追问。\n");
        } else {
            sb.append("1. 直接基于上面的真实数据进行分析和建议，不要询问已有数据\n");
        }
        sb.append("2. 如果发现数据不完整或有问题，一句话点明缺口即可，勿反复渲染\n");
        sb.append("3. 结构：结论 1～2 句 → 依据（尽量带数字）→ 建议 1～2 条；**禁止**「首先/其次/再次/最后」式长框架。\n");
        sb.append("4. 数据要准确引用，不要编造数字\n");
        if (realDataSection != null && realDataSection.contains("【当前库存快照】")) {
            sb.append("4a. 「库存/存货」金额：**只能**逐字引用【当前库存快照】中的「剩余成本合计」与「在库批次数」。若该块显示合计为 0 或行数为 0，如实说明即可；**禁止**用【本月库存减少数据】、营业额或其它段落拼凑「约 XXX 元」的库存结论。\n");
        }
        if (realDataSection != null && realDataSection.contains("【本月采购数据】")) {
            sb.append("4b. 「采购/进货金额、谁买得最多」：**只能**引用【本月采购数据】中的合计、Top 汇总与摘录行（gb_DPG_buy_subtotal）；若该块写明「金额摘录已省略」则不得编造采购金额明细。**禁止**把【本月库存减少数据】里 type=1 的金额说成采购额。\n");
            sb.append("4g. 「是否全是自采、有没有供货商配送」：**禁止**使用「100%自采」「完全没有供货商配送」等**绝对化**表述，除非上文 **供货属性摘要** 与 **全批发商入库供货维度** 中 type=5 笔数为 0 **且** type=1 且 nx_supplier_id 为正整数笔数为 0 **且** 摘录各行 nx 均为 -1 或未填。否则必须按摘要区分：**type=5** 或 **type=1 且 nx_supplier_id 为正整数** 均表示存在供货商维度入库。若仅部门收窄块内未见供货商维度，须说明「当前部门采购口径下…」，并引用全批发商维度行。\n");
        }
        if (realDataSection != null && realDataSection.contains("【供货商未结账款（采购批次）】")) {
            sb.append("4c. 「供货商/供应商未结、应付、欠款」：**只能**引用【供货商未结账款（采购批次）】中的净额与按供货商 Top；口径为 gb_distributer_purchase_batch 未结账(status=3)批次小计，**禁止**说系统无此数据若上文已给出数字。\n");
        }
        if (realDataSection != null && realDataSection.contains("【本月自采金额（采购商品行）】")) {
            sb.append("4d. 「自采金额」：**只能**引用【本月自采金额（采购商品行）】中的合计与 Top；口径为 gb_distributer_purchase_goods 且 gb_DPG_purchase_type=1（GbConstants.PurchaseOrderType.SELF_PURCHASE），**禁止**用全量采购块或库存流水代替。**禁止**将「本块合计≈【本月采购数据】合计」解释为「全部为自采、无供货商」：同一表中可有 type=1 且 gb_DPG_purchase_nx_supplier_id 为正整数的供货商供货入库，以【本月采购数据】供货摘要与摘录为准。\n");
        }
        if (realDataSection != null && realDataSection.contains("【本月采购单价波动（采购商品行）】")) {
            sb.append("4e. 「采购单价波动、最高价/最低价、前三名」：**最高与最低必须同为字段 gb_DPG_buy_price（入库单价）**；**严禁**使用【本月采购数据】或任意「金额¥」「小计」「gb_DPG_buy_subtotal」充当单价；表中「入库单价价差」= 最高入库单价−最低入库单价。**只能**引用【本月采购单价波动】核验表与摘录中的 buy_price；禁止编造与表矛盾的数字。\n");
        }
        if (realDataSection != null && realDataSection.contains("【本月供货商订货采购】")) {
            sb.append("4f. 「给供货商/供应商订货了哪些、订货原料清单」：**只能**引用【本月供货商订货采购】（gb_DPG_purchase_type=5）；**禁止**说成「全部为自采」、禁止用【本月自采】(type=1) 或未按 type 区分的数据块代替。**type=5 为供货商订货**；**type=1 仍需对照 gb_DPG_purchase_nx_supplier_id**，正整数表示供货商维度入库，勿仅凭 type=1 统称自采。\n");
        }
        if (realDataSection != null && realDataSection.contains("【订货/到货频率与习惯】")) {
            sb.append("4h. 「采购/进货/订货频率怎么样、节奏好不好」：若上文有【订货/到货频率与习惯】，**优先引用该块**（gb_department_orders 到货日）；**禁止**用【本月采购数据】入库笔数、金额 Top 代替「订货频率」结论。**禁止**在该话题下顺带断言「没有供货商配送、全部自采」——供货商维度只能依据【本月采购数据】供货摘要、全批发商供货维度及 nx_supplier_id 摘录。\n");
        }
        sb.append("5. 如果用户提供了新的数据/数字，明确告知已记录\n");
        if (costSkill) {
            sb.append("6. 篇幅上限见上文「成本场景篇幅」；少用 `**` 标题，列表**最多 3 条**，每条宜一行。\n");
        } else {
            sb.append("6. **篇幅硬性上限**：面向老板的正文 **320 字以内**（不含 JSON）；两句能说清不写第三句。\n");
        }
        sb.append("7. 不要用长破折号、排比、重复总结；行动建议合并为一条主行动即可。\n\n");

        // 如果选择了数据提取相关的Skill（data-extractor 或 cost），强化JSON输出要求
        if (skillNames != null && (skillNames.toLowerCase().contains("data-extractor") || skillNames.toLowerCase().contains("cost"))) {
            sb.append("【重要：数据提取规则】\n");
            sb.append("当用户提到任何经营数据（如房租、租金、工资、营业额、成本等）时：\n");
            sb.append("1. 必须在回复末尾添加JSON格式的数据提取结果\n");
            sb.append("2. 可用的字段名（必须严格使用这些名称）：\n");
            sb.append("   - gb_ai_restaurant_profile_daily_revenue (日均营收)\n");
            sb.append("   - gb_ai_restaurant_profile_rent_monthly (月租金)\n");
            sb.append("   - gb_ai_restaurant_profile_monthly_wage (月工资预算)\n");
            sb.append("   - gb_ai_restaurant_profile_monthly_fixed_cost (月固定成本)\n");
            sb.append("3. JSON格式示例：\n");
            sb.append("```json\n");
            sb.append("{\"hasData\": true, \"needsConfirm\": false, \"updates\": [{\"field\": \"gb_ai_restaurant_profile_rent_monthly\", \"value\": 12000.00, \"displayName\": \"月租金\"}], \"summary\": \"提取到月租金12000元，直接更新\"}\n");
            sb.append("```\n");
            sb.append("4. 如果数据有冲突需要确认，needsConfirm设为true\n");
            sb.append("5. 只有当数据真正被保存后才能说\"已记录/已更新\"，否则只能说\"明白了\"\n");
            sb.append("6. 固定成本（如用户说\"固定成本\"或\"其他成本\"）必须使用字段名 gb_ai_restaurant_profile_monthly_fixed_cost\n\n");
        }

        sb.append("【技能移交（结构化 JSON，仅服务端消费）】\n");
        sb.append("若【餐厅真实数据】仍不足以准确回答（例如用户问「哪道菜卖得最多」但上文缺按菜聚合销量），可在面向老板的正文之后，**另起一段**追加一个 ```json 代码块。\n");
        sb.append("块内须为单行 JSON：type=\"skill_handoff\"、version=1、toSkill、reason；toSkill 只能是：cost | revenue | data_extractor | dish_sales | dish_cost | procurement | profit_pilot。\n");
        sb.append("carryOver 可选，例如 {\"userIntentSummary\":\"用户想对比热销菜\"}；**禁止**在该块里写画像 hasData/updates。\n");
        sb.append("若不需要移交，不要输出该块。用户可见正文仍须符合篇幅与身份要求。\n\n");

        // 数据完整性检查说明
        sb.append("【数据完整性检查】\n");
        sb.append("在回复结尾添加以下格式的数据完整性声明：\n");
        sb.append("【数据完整性】\n");
        sb.append("- 日均营收数据: 有/无 (覆盖X天)\n");
        sb.append("- 固定成本数据: 有/无\n");
        sb.append("- 本月营业额数据: 有/无 (记录X天)\n");
        if (costSkill) {
            sb.append("- 食材/出库成本: 请按【本月库存减少数据】中 type=1 金额与上文行数填写；仅当该段合计为 ¥0 且行数为 0 时方可标「无」\n");
        } else {
            sb.append("- 食材成本数据: 有/无 (记录X条)\n");
        }
        sb.append("如果某项数据缺失，在分析部分要特别说明这个问题。\n\n");

        return sb.toString();
    }

    /**
     * 查询并格式化真实数据
     */
    private String queryAndFormatRealData(Long departmentId) {
        StringBuilder sb = new StringBuilder();
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        LocalDate monthEnd = now;

        log.info("========== 查询真实数据 ==========");
        log.info("部门ID: {}, 日期范围: {} 至 {}", departmentId, monthStart, monthEnd);

        // 1. 餐厅画像数据
        sb.append("【餐厅基本信息】\n");
        GbAiRestaurantProfileEntity profile = restaurantProfileMapper.selectOne(
                new LambdaQueryWrapper<GbAiRestaurantProfileEntity>()
                        .eq(GbAiRestaurantProfileEntity::getGbAiRestaurantProfileDepartmentId, departmentId)
        );

        if (profile != null) {
            BigDecimal dailyRevenue = profile.getGbAiRestaurantProfileDailyRevenue();
            BigDecimal rentMonthly = profile.getGbAiRestaurantProfileRentMonthly();
            BigDecimal wageMonthly = profile.getGbAiRestaurantProfileMonthlyWage();
            BigDecimal fixedCostMonthly = profile.getGbAiRestaurantProfileMonthlyFixedCost();

            sb.append("- 日均营收目标: ").append(dailyRevenue != null ? "¥" + dailyRevenue : "未设置").append("\n");
            sb.append("- 月租金: ").append(rentMonthly != null ? "¥" + rentMonthly : "未设置").append("\n");
            sb.append("- 月工资预算: ").append(wageMonthly != null ? "¥" + wageMonthly : "未设置").append("\n");
            sb.append("- 月固定成本（其他）: ").append(fixedCostMonthly != null ? "¥" + fixedCostMonthly : "未设置").append("\n");

            // 计算月固定成本总计
            BigDecimal totalFixedCost = BigDecimal.ZERO;
            if (rentMonthly != null) totalFixedCost = totalFixedCost.add(rentMonthly);
            if (wageMonthly != null) totalFixedCost = totalFixedCost.add(wageMonthly);
            if (fixedCostMonthly != null) totalFixedCost = totalFixedCost.add(fixedCostMonthly);
            sb.append("- 月固定成本总计: ¥").append(totalFixedCost).append("\n");

            log.info("餐厅画像: 日均={}, 租金={}, 工资={}, 固定={}",
                    dailyRevenue, rentMonthly, wageMonthly, fixedCostMonthly);
        } else {
            sb.append("- 暂无餐厅画像数据，请告知您的餐厅基本信息（日均营收、租金、工资等）\n");
            log.warn("部门 {} 没有餐厅画像数据", departmentId);
        }
        sb.append("\n");

        // 2. 本月营业额数据
        sb.append("【本月营业额数据】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        List<GbAiDailyRevenueEntity> revenues = dailyRevenueMapper.selectList(
                new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, departmentId)
                        .between(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, monthStart, monthEnd)
                        .orderByAsc(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate)
        );

        log.info("查到 {} 条营业额记录", revenues.size());

        if (!revenues.isEmpty()) {
            BigDecimal totalDineIn = BigDecimal.ZERO;
            BigDecimal totalTakeout = BigDecimal.ZERO;
            BigDecimal totalPlatformFee = BigDecimal.ZERO;

            for (GbAiDailyRevenueEntity r : revenues) {
                if (r.getGbAiDailyRevenueDineInRevenue() != null) {
                    totalDineIn = totalDineIn.add(r.getGbAiDailyRevenueDineInRevenue());
                }
                if (r.getGbAiDailyRevenueTakeoutRevenue() != null) {
                    totalTakeout = totalTakeout.add(r.getGbAiDailyRevenueTakeoutRevenue());
                }
                if (r.getGbAiDailyRevenuePlatformFee() != null) {
                    totalPlatformFee = totalPlatformFee.add(r.getGbAiDailyRevenuePlatformFee());
                }
            }

            BigDecimal totalRevenue = totalDineIn.add(totalTakeout).subtract(totalPlatformFee);
            int daysInMonth = now.getDayOfMonth();
            double avgDaily = revenues.isEmpty() ? 0 : totalRevenue.doubleValue() / revenues.size();

            sb.append("- 记录天数: ").append(revenues.size()).append(" 天（共 ").append(daysInMonth).append(" 天）\n");
            sb.append("- 堂食营收: ¥").append(totalDineIn).append("\n");
            sb.append("- 外卖营收: ¥").append(totalTakeout).append("\n");
            sb.append("- 平台抽成: ¥").append(totalPlatformFee).append("\n");
            sb.append("- 本月总营收（扣除平台费）: ¥").append(totalRevenue).append("\n");
            sb.append("- 日均营收: ¥").append(String.format("%.2f", avgDaily)).append("\n");

            // 显示每日明细
            sb.append("- 每日明细:\n");
            for (GbAiDailyRevenueEntity r : revenues) {
                String date = r.getGbAiDailyRevenueRecordDate().toString();
                BigDecimal dineIn = r.getGbAiDailyRevenueDineInRevenue() != null ? r.getGbAiDailyRevenueDineInRevenue() : BigDecimal.ZERO;
                BigDecimal takeout = r.getGbAiDailyRevenueTakeoutRevenue() != null ? r.getGbAiDailyRevenueTakeoutRevenue() : BigDecimal.ZERO;
                BigDecimal fee = r.getGbAiDailyRevenuePlatformFee() != null ? r.getGbAiDailyRevenuePlatformFee() : BigDecimal.ZERO;
                BigDecimal dayTotal = dineIn.add(takeout).subtract(fee);
                sb.append("  ").append(date).append(": 堂食¥").append(dineIn).append(" + 外卖¥").append(takeout).append(" - 平台费¥").append(fee).append(" = ¥").append(dayTotal).append("\n");
            }
        } else {
            sb.append("- 暂无本月营业额数据\n");
        }
        sb.append("\n");

        // 3. 本月库存减少数据（成本、损耗、废弃、退货）
        sb.append("【本月库存减少数据】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        sb.append("- 查询范围：gb_dgsr_department_father_id = ").append(departmentId)
                .append(" 或 gb_dgsr_gb_department_id = ").append(departmentId).append("（与门店档案部门ID对齐）\n");

        LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity> queryWrapper = stockReduceMonthScope(departmentId, monthStart, monthEnd);
        log.info("【SQL查询】库存减少记录: father_id或department_id={}, 日期 {}..{}",
                departmentId, monthStart, monthEnd);

        List<GbDepartmentGoodsStockReduceEntity> reduces = stockReduceMapper.selectList(queryWrapper);
        log.info("【SQL结果】查到 {} 条库存减少记录", reduces.size());

        // type: 1=成本, 2=损耗, 3=废弃, 4=退货
        BigDecimal totalCost = BigDecimal.ZERO;     // 成本
        BigDecimal totalLoss = BigDecimal.ZERO;      // 损耗
        BigDecimal totalWaste = BigDecimal.ZERO;     // 废弃
        BigDecimal totalReturn = BigDecimal.ZERO;    // 退货
        int costCount = 0, lossCount = 0, wasteCount = 0, returnCount = 0;

        for (GbDepartmentGoodsStockReduceEntity r : reduces) {
            if (r.getGbDgsrSubtotal() != null && !r.getGbDgsrSubtotal().isEmpty()) {
                try {
                    BigDecimal subtotal = new BigDecimal(r.getGbDgsrSubtotal());
                    Integer type = r.getGbDgsrType();
                    if (type != null) {
                        switch (type) {
                            case 1:
                                totalCost = totalCost.add(subtotal);
                                costCount++;
                                break;
                            case 2:
                                totalLoss = totalLoss.add(subtotal);
                                lossCount++;
                                break;
                            case 3:
                                totalWaste = totalWaste.add(subtotal);
                                wasteCount++;
                                break;
                            case 4:
                                totalReturn = totalReturn.add(subtotal);
                                returnCount++;
                                break;
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("解析金额失败: {}", r.getGbDgsrSubtotal());
                }
            }
        }

        sb.append("- 成本（type=1）: ¥").append(totalCost).append(" (").append(costCount).append(" 条记录)\n");
        sb.append("- 损耗（type=2）: ¥").append(totalLoss).append(" (").append(lossCount).append(" 条记录)\n");
        sb.append("- 废弃（type=3）: ¥").append(totalWaste).append(" (").append(wasteCount).append(" 条记录)\n");
        sb.append("- 退货（type=4）: ¥").append(totalReturn).append(" (").append(returnCount).append(" 条记录)\n");

        BigDecimal totalReduce = totalCost.add(totalLoss).add(totalWaste).add(totalReturn);
        sb.append("- 库存减少总计: ¥").append(totalReduce).append("\n");

        if (!reduces.isEmpty()) {
            sb.append("- 每日明细:\n");
            Map<String, List<GbDepartmentGoodsStockReduceEntity>> byDate = new LinkedHashMap<>();
            for (GbDepartmentGoodsStockReduceEntity r : reduces) {
                String date = r.getGbDgsrDate();
                byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(r);
            }
            for (Map.Entry<String, List<GbDepartmentGoodsStockReduceEntity>> entry : byDate.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ");
                BigDecimal dayTotal = BigDecimal.ZERO;
                for (GbDepartmentGoodsStockReduceEntity r : entry.getValue()) {
                    if (r.getGbDgsrSubtotal() != null) {
                        try {
                            BigDecimal subtotal = new BigDecimal(r.getGbDgsrSubtotal());
                            dayTotal = dayTotal.add(subtotal);
                            String typeName = r.getGbDgsrType() != null ?
                                    (r.getGbDgsrType() == 1 ? "成本" : r.getGbDgsrType() == 2 ? "损耗" : r.getGbDgsrType() == 3 ? "废弃" : "退货") : "未知";
                            sb.append(typeName).append("¥").append(subtotal).append(" ");
                        } catch (NumberFormatException ignored) {}
                    }
                }
                sb.append("= ¥").append(dayTotal).append("\n");
            }
        }

        log.info("========== 数据查询完成 ==========");
        return sb.toString();
    }

    private static int departmentIdAsIntOrSentinel(Long departmentId) {
        if (departmentId == null || departmentId > Integer.MAX_VALUE || departmentId < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return departmentId.intValue();
    }

    /**
     * 本月库存减少：日期落在区间内，且（父部门ID 或 本部门ID）与对话门店 departmentId 一致，避免业务只填子部门导致查空。
     */
    private static LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity> stockReduceMonthScope(
            Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        int dep = departmentIdAsIntOrSentinel(departmentId);
        return new LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity>()
                .and(w -> w.eq(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbDepartmentFatherId, dep)
                        .or()
                        .eq(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbDepartmentId, dep))
                .between(GbDepartmentGoodsStockReduceEntity::getGbDgsrDate, monthStart.toString(), monthEnd.toString());
    }

    /**
     * 根据选择的 Skill 类型智能查询相关数据
     * @param departmentId 部门ID
     * @param selectedSkills 选中的Skill（文件名）
     * @param userMessage 用户消息（用于更精准判断需要哪些数据）
     * @param costFacet 成本子意图（来自第一步 JSON，可为 null）
     * @return 格式化后的数据字符串
     */
    private String queryRealDataBySkills(Long departmentId, String selectedSkills, String userMessage, String costFacet) {
        // 转换为小写便于匹配
        String skillsLower = selectedSkills != null ? selectedSkills.toLowerCase() : "";
        StringBuilder sb = new StringBuilder();

        log.info("根据Skill类型智能查询数据: {}", selectedSkills);

        // 餐厅画像数据 - 所有Skill都需要这个基础数据
        sb.append("【餐厅基本信息】\n");
        GbAiRestaurantProfileEntity profile = restaurantProfileMapper.selectOne(
                new LambdaQueryWrapper<GbAiRestaurantProfileEntity>()
                        .eq(GbAiRestaurantProfileEntity::getGbAiRestaurantProfileDepartmentId, departmentId)
        );

        // 提前提取3个基本固定成本数据（用于后续检查）
        BigDecimal dailyRevenue = null;
        BigDecimal rentMonthly = null;
        BigDecimal wageMonthly = null;
        BigDecimal fixedCostMonthly = null;

        if (profile != null) {
            dailyRevenue = profile.getGbAiRestaurantProfileDailyRevenue();
            rentMonthly = profile.getGbAiRestaurantProfileRentMonthly();
            wageMonthly = profile.getGbAiRestaurantProfileMonthlyWage();
            fixedCostMonthly = profile.getGbAiRestaurantProfileMonthlyFixedCost();

            sb.append("- 日均营收目标: ").append(dailyRevenue != null ? "¥" + dailyRevenue : "未设置").append("\n");
            sb.append("- 月租金: ").append(rentMonthly != null ? "¥" + rentMonthly : "未设置").append("\n");
            sb.append("- 月工资预算: ").append(wageMonthly != null ? "¥" + wageMonthly : "未设置").append("\n");
            sb.append("- 月固定成本（其他）: ").append(fixedCostMonthly != null ? "¥" + fixedCostMonthly : "未设置").append("\n");

            BigDecimal totalFixedCost = BigDecimal.ZERO;
            if (rentMonthly != null) totalFixedCost = totalFixedCost.add(rentMonthly);
            if (wageMonthly != null) totalFixedCost = totalFixedCost.add(wageMonthly);
            if (fixedCostMonthly != null) totalFixedCost = totalFixedCost.add(fixedCostMonthly);
            sb.append("- 月固定成本总计: ¥").append(totalFixedCost).append("\n");
        } else {
            sb.append("- 暂无餐厅画像数据\n");
        }
        sb.append("\n");

        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        LocalDate monthEnd = now;

        // 检查成本分析所需的3个基本固定成本数据是否完整
        boolean hasRent = rentMonthly != null;
        boolean hasWage = wageMonthly != null;
        boolean hasFixedCost = fixedCostMonthly != null;
        boolean hasAllBasicCostData = hasRent && hasWage && hasFixedCost;

        boolean costSkill = skillsLower.contains("ai-skill-cost.md") || skillsLower.contains("成本");
        boolean dishCostSkill = skillsLower.contains("ai-skill-dish-cost-diagnosis.md");
        boolean procurementSkill = skillsLower.contains("ai-skill-procurement-structure.md");
        boolean profitPilotSkill = skillsLower.contains("ai-skill-profit-pilot.md");

        // 根据Skill类型决定查询哪些数据
        if (costSkill || dishCostSkill || procurementSkill || profitPilotSkill) {
            // 采购结构分析可在缺固定成本时继续；其余算账技能必须先有三项固定成本
            boolean needsFixedCostGate = costSkill || dishCostSkill || profitPilotSkill;
            if (needsFixedCostGate && !hasAllBasicCostData) {
                sb.append("【数据完整性警告】\n");
                sb.append("⚠️ 固定成本数据不完整，无法进行完整算账分析！\n");
                sb.append("缺少的数据：\n");
                if (!hasRent) sb.append("  - 月租金\n");
                if (!hasWage) sb.append("  - 月工资\n");
                if (!hasFixedCost) sb.append("  - 月固定成本（其他）\n");
                sb.append("\n请先补充以上3项固定成本数据，才能进行利润与保本分析。\n");
                sb.append("提示用户：\"要帮你算账，我还缺固定成本三项。请告诉我月租金、月工资和其它固定成本。\"\n\n");
                if (procurementSkill) {
                    boolean procurementOnly = !costSkill && !dishCostSkill && !profitPilotSkill;
                    boolean omitReduceForReorderFocus = procurementOnly
                            && SkillRouteFallback.shouldAttachReorderHabitFacts(userMessage);
                    sb.append(queryCostData(departmentId, monthStart, monthEnd, "procurement", userMessage,
                            omitReduceForReorderFocus));
                    if (!SkillRouteFallback.shouldAttachSelfPurchaseFacts(userMessage)) {
                        sb.append(querySelfPurchaseGoodsFactsForAi(departmentId, monthStart, monthEnd));
                    }
                    if (!SkillRouteFallback.shouldAttachSupplierUnsettledFacts(userMessage, "procurement")) {
                        sb.append(querySupplierUnsettledFactsForAi(departmentId));
                    }
                }
            } else {
                if (costSkill || dishCostSkill || profitPilotSkill) {
                    sb.append(queryRevenueData(departmentId, monthStart, monthEnd));
                } else {
                    sb.append(queryRevenueDataBrief(departmentId, monthStart, monthEnd));
                }

                String facetToUse = costFacet;
                if (StrUtil.isBlank(facetToUse)) {
                    if (dishCostSkill) {
                        facetToUse = "dish_structure";
                    } else if (procurementSkill) {
                        facetToUse = "procurement";
                    } else if (profitPilotSkill) {
                        facetToUse = "overview";
                    }
                }
                boolean procurementOnly = procurementSkill && !costSkill && !dishCostSkill && !profitPilotSkill;
                boolean omitReduceForReorderFocus = procurementOnly
                        && SkillRouteFallback.shouldAttachReorderHabitFacts(userMessage);
                sb.append(queryCostData(departmentId, monthStart, monthEnd, facetToUse, userMessage,
                        omitReduceForReorderFocus));

                if (dishCostSkill) {
                    sb.append(queryDishSalesFacts(departmentId, monthStart, monthEnd));
                    sb.append(queryDishCostAnalysisFactsForAi(departmentId, monthStart, monthEnd));
                }
                if (procurementSkill) {
                    if (!SkillRouteFallback.shouldAttachSelfPurchaseFacts(userMessage)) {
                        sb.append(querySelfPurchaseGoodsFactsForAi(departmentId, monthStart, monthEnd));
                    }
                    if (!SkillRouteFallback.shouldAttachSupplierUnsettledFacts(userMessage, facetToUse)) {
                        sb.append(querySupplierUnsettledFactsForAi(departmentId));
                    }
                }
                if (profitPilotSkill) {
                    sb.append(queryDishSalesFacts(departmentId, monthStart, monthEnd));
                }
            }
        } else if (skillsLower.contains("revenue") || skillsLower.contains("营收") || skillsLower.contains("boost")) {
            // 营收提升Skill - 查询营业额数据
            sb.append(queryRevenueData(departmentId, monthStart, monthEnd));
            if (SkillRouteFallback.shouldAttachDishSalesFacts(userMessage)) {
                sb.append(queryDishSalesFacts(departmentId, monthStart, monthEnd));
            }
        } else {
            // data-extractor 或其他 - 查询简要的营业额数据作为参考
            sb.append(queryRevenueDataBrief(departmentId, monthStart, monthEnd));
        }

        if (SkillRouteFallback.shouldAttachInventoryFacts(userMessage)) {
            sb.append(queryInventorySnapshotForAi(departmentId));
        }

        return sb.toString();
    }

    /**
     * 当前部门在库批次剩余成本汇总（仅作事实注入；模型不得用其它段落数字「推算」库存金额）。
     */
    private String queryInventorySnapshotForAi(Long departmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前库存快照】\n");
        sb.append("- 口径：gb_department_goods_stock.gb_dgs_rest_subtotal（批次剩余成本）按部门汇总；与「本月入库/出库条数」无直接换算关系。\n");
        sb.append("- **批次供货属性**：字段 **gb_dgs_nx_supplier_id**：**-1**=自采入库批次；**正整数**=nx 供货商 ID（供货商配送）。回答「在库这批是自采还是供货商送的」**必须**看该字段。\n");
        int dep = departmentIdAsIntOrSentinel(departmentId);
        if (dep == Integer.MIN_VALUE) {
            sb.append("- 部门 ID 无效，未查询。\n\n");
            return sb.toString();
        }
        List<GbDepartmentGoodsStockEntity> rows = departmentGoodsStockMapper.selectList(
                new LambdaQueryWrapper<GbDepartmentGoodsStockEntity>()
                        .and(w -> w.eq(GbDepartmentGoodsStockEntity::getGbDgsGbDepartmentFatherId, dep)
                                .or()
                                .eq(GbDepartmentGoodsStockEntity::getGbDgsGbDepartmentId, dep)));
        sb.append("- 查询范围：gb_dgs_gb_department_father_id = ").append(dep)
                .append(" 或 gb_dgs_gb_department_id = ").append(dep).append("\n");
        sb.append("- 在库批次数（行数）: ").append(rows.size()).append("\n");
        BigDecimal totalRest = BigDecimal.ZERO;
        for (GbDepartmentGoodsStockEntity row : rows) {
            String rest = row.getGbDgsRestSubtotal();
            if (StrUtil.isEmpty(rest)) {
                continue;
            }
            try {
                totalRest = totalRest.add(new BigDecimal(rest.trim()));
            } catch (Exception ignored) {
                // 跳过无法解析的金额字段
            }
        }
        sb.append("- 剩余成本合计: ¥").append(totalRest.setScale(2, RoundingMode.HALF_UP)).append("\n");
        List<GbDepartmentGoodsStockEntity> sorted = new ArrayList<>(rows);
        sorted.removeIf(row -> {
            String rest = row.getGbDgsRestSubtotal();
            if (StrUtil.isEmpty(rest)) {
                return true;
            }
            try {
                return new BigDecimal(rest.trim()).compareTo(BigDecimal.ZERO) <= 0;
            } catch (Exception e) {
                return true;
            }
        });
        sorted.sort((a, b) -> {
            try {
                return new BigDecimal(b.getGbDgsRestSubtotal().trim())
                        .compareTo(new BigDecimal(a.getGbDgsRestSubtotal().trim()));
            } catch (Exception e) {
                return 0;
            }
        });
        int cap = Math.min(20, sorted.size());
        if (cap > 0) {
            sb.append("- 【在库批次摘录】（按剩余成本降序最多 20 条；单价 gb_dgs_price）：\n");
            Map<Integer, String> nameCache = new HashMap<>();
            for (int i = 0; i < cap; i++) {
                GbDepartmentGoodsStockEntity row = sorted.get(i);
                String nm = goodsNameFromCache(row.getGbDgsGbDisGoodsId(), nameCache);
                String day = StrUtil.blankToDefault(row.getGbDgsDate(), "?");
                String price = StrUtil.blankToDefault(row.getGbDgsPrice(), "?");
                String rest = row.getGbDgsRestSubtotal();
                sb.append("  - ").append(nm).append("，批次日 ").append(day)
                        .append("，单价 ").append(price)
                        .append("，剩余成本 ¥").append(rest)
                        .append("，").append(nxSupplierChannelShort(row.getGbDgsNxSupplierId()))
                        .append("\n");
            }
        }
        sb.append("- 回答「库存还有多少钱/剩多少」时，**只能**引用本块「剩余成本合计」与「在库批次数」；不得用营业额、库存减少流水条数或其它段落臆造库存总额。\n\n");
        return sb.toString();
    }

    /**
     * 查询成本数据（库存减少）：含商品名/部门名/日期的流水摘录，以及按供应商/商品 ID 的 Top 聚合（下钻用）。
     * <p>订货/采购频率类问题且仅命中 procurement-structure、未带通用成本 skill 时，可跳过 reduce 表查询（出库≠进货节奏）。</p>
     */
    private String queryCostData(Long departmentId, LocalDate monthStart, LocalDate monthEnd, String costFacet,
                                 String userMessage) {
        return queryCostData(departmentId, monthStart, monthEnd, costFacet, userMessage, false);
    }

    private String queryCostData(Long departmentId, LocalDate monthStart, LocalDate monthEnd, String costFacet,
                                 String userMessage, boolean omitStockReduceSection) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月库存减少数据】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        if (StrUtil.isNotEmpty(costFacet)) {
            sb.append("- 成本子意图(costFacet): ").append(costFacet).append("\n");
        }
        if (omitStockReduceSection) {
            sb.append("- 本回合用户关切为**订货/采购频率节奏**：未查询 gb_department_goods_stock_reduce。"
                    + "出库流水为「扣库存成本」，与「进货/订货节奏」口径不同；频率请以【订货/到货频率与习惯】与【本月采购数据】入库完成日为准。\n\n");
            appendPurchaseReorderSupplierFacts(sb, departmentId, monthStart, monthEnd, costFacet, userMessage);
            log.info("成本分析查询: departmentId={}, omitStockReduce=reorder_focus procurement-only", departmentId);
            return sb.toString();
        }

        sb.append("- gb_dgsr_type 含义: 1=成本类金额, 2=损耗, 3=废弃, 4=退货\n");
        sb.append("- **出库所扣批次的供货属性**：gb_department_goods_stock_reduce.**gb_dgsr_stock_nx_supplier_id**（源自入库批次 gb_department_goods_stock.**gb_dgs_nx_supplier_id**）：**-1**=自采；**正整数**=供货商 ID。回答「这笔出库/成本是自采还是供货商送的」须引用流水摘录中的该项；与 gb_DPG_purchase_type 并列参考，勿互相臆替。\n");

        LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity> queryWrapper = stockReduceMonthScope(departmentId, monthStart, monthEnd);
        sb.append("- 查询范围：gb_dgsr_department_father_id = ").append(departmentId)
                .append(" 或 gb_dgsr_gb_department_id = ").append(departmentId).append("\n");

        List<GbDepartmentGoodsStockReduceEntity> reduces = stockReduceMapper.selectList(queryWrapper);
        log.info("成本分析查询: departmentId={}, 查到 {} 条库存减少记录", departmentId, reduces.size());
        sb.append("- 匹配行数: ").append(reduces.size()).append("（为 0 才表示本条件未查到流水）\n");

        BigDecimal totalCost = BigDecimal.ZERO, totalLoss = BigDecimal.ZERO, totalWaste = BigDecimal.ZERO, totalReturn = BigDecimal.ZERO;
        Map<String, BigDecimal> supplierType1 = new HashMap<>();
        Map<String, BigDecimal> goodsWasteLoss = new HashMap<>();

        for (GbDepartmentGoodsStockReduceEntity r : reduces) {
            if (r.getGbDgsrSubtotal() == null || r.getGbDgsrSubtotal().isEmpty()) {
                continue;
            }
            try {
                BigDecimal subtotal = new BigDecimal(r.getGbDgsrSubtotal());
                Integer type = r.getGbDgsrType();
                if (type == null) {
                    continue;
                }
                String supplierKey = r.getGbDgsrStockNxSupplierId() != null
                        ? "nx_supplier_id=" + r.getGbDgsrStockNxSupplierId()
                        : "supplier=未关联";
                String goodsKey = r.getGbDgsrGbDisGoodsId() != null
                        ? "gb_dgsr_gb_dis_goods_id=" + r.getGbDgsrGbDisGoodsId()
                        : "goods=未关联";
                switch (type) {
                    case 1:
                        totalCost = totalCost.add(subtotal);
                        supplierType1.merge(supplierKey, subtotal, BigDecimal::add);
                        break;
                    case 2:
                        totalLoss = totalLoss.add(subtotal);
                        goodsWasteLoss.merge(goodsKey, subtotal, BigDecimal::add);
                        break;
                    case 3:
                        totalWaste = totalWaste.add(subtotal);
                        goodsWasteLoss.merge(goodsKey, subtotal, BigDecimal::add);
                        break;
                    case 4:
                        totalReturn = totalReturn.add(subtotal);
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }

        BigDecimal totalReduce = totalCost.add(totalLoss).add(totalWaste).add(totalReturn);
        sb.append("- 成本(type=1): ¥").append(totalCost).append(", 损耗(type=2): ¥").append(totalLoss)
                .append(", 废弃(type=3): ¥").append(totalWaste).append(", 退货(type=4): ¥").append(totalReturn).append("\n");
        sb.append("- 库存减少总计: ¥").append(totalReduce).append("\n");

        appendStockReduceReadableLines(sb, reduces, 20);
        appendTopMoneyLines(sb, "【下钻】本月 type=1 成本金额按 nx_supplier_id Top10（**-1**=自采；其它正整数=供货商 ID）", supplierType1, 10);
        appendTopMoneyLines(sb, "【下钻】本月损耗+废弃(type=2+3)按分销商品 ID Top10（可与上文流水摘录对照）", goodsWasteLoss, 10);
        sb.append("\n");

        appendPurchaseReorderSupplierFacts(sb, departmentId, monthStart, monthEnd, costFacet, userMessage);

        return sb.toString();
    }

    /** 订货习惯 + 采购表 + 单价波动 + 自采 + 供货未结（紧跟在库存减少块之后或替代该块）。 */
    private void appendPurchaseReorderSupplierFacts(StringBuilder sb, Long departmentId, LocalDate monthStart,
                                                    LocalDate monthEnd, String costFacet, String userMessage) {
        if (SkillRouteFallback.shouldAttachReorderHabitFacts(userMessage)) {
            int dep = departmentIdAsIntOrSentinel(departmentId);
            if (dep != Integer.MIN_VALUE) {
                sb.append(gbDepartmentReorderReminderService.buildAiReorderHabitFactsMarkdown(dep, null, null, 25));
            }
        }

        boolean purchasePriceVolatilityIntent = SkillRouteFallback.shouldAttachPurchasePriceVolatilityFacts(userMessage);
        boolean supplierOrderCue = SkillRouteFallback.shouldAttachSupplierOrderPurchaseFacts(userMessage);
        boolean broadPurchaseCue = StrUtil.isNotBlank(userMessage)
                && (userMessage.contains("采购") || userMessage.contains("进货"));
        boolean wantPurchaseFacts = SkillRouteFallback.shouldAttachPurchaseFacts(userMessage, costFacet);

        if (supplierOrderCue) {
            sb.append(querySupplierOrderPurchaseGoodsFactsForAi(departmentId, monthStart, monthEnd));
        }
        if (wantPurchaseFacts) {
            if (purchasePriceVolatilityIntent) {
                sb.append(queryPurchaseGoodsFactsForAiVolatilityOmitSubtotalExcerpt(departmentId, monthStart, monthEnd));
            } else if (!(supplierOrderCue && !broadPurchaseCue)) {
                sb.append(queryPurchaseGoodsFactsForAi(departmentId, monthStart, monthEnd));
            }
        }
        if (purchasePriceVolatilityIntent) {
            sb.append(queryPurchasePriceVolatilityFactsForAi(departmentId, monthStart, monthEnd, userMessage));
        }
        if (SkillRouteFallback.shouldAttachSelfPurchaseFacts(userMessage)) {
            sb.append(querySelfPurchaseGoodsFactsForAi(departmentId, monthStart, monthEnd));
        }
        if (SkillRouteFallback.shouldAttachSupplierUnsettledFacts(userMessage, costFacet)) {
            sb.append(querySupplierUnsettledFactsForAi(departmentId));
        }
    }

    /**
     * 本月采购明细事实：来自 gb_distributer_purchase_goods.gb_DPG_buy_subtotal（与库存减少 type=1 不是同一口径）。
     */
    private String queryPurchaseGoodsFactsForAi(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月采购数据】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        sb.append("- 口径：gb_distributer_purchase_goods.gb_DPG_buy_subtotal；日期筛选用 gb_DPG_stock_finish_date（入库完成日），与 gb_department_goods_stock_reduce 的出库成本不同。\n");
        sb.append("- 状态：gb_DPG_status &gt; 2；排除 gb_DPG_purchase_type = 9（与现有采购统计接口一致）。\n");
        sb.append("- **区分自采 vs 供货商配送**：**gb_DPG_purchase_nx_supplier_id** 与入库批次 **gb_department_goods_stock.gb_dgs_nx_supplier_id** 同语义：**-1**=自采；正整数=供货商 ID（可与 gb_DPG_purchase_type 对照，勿只凭类型臆断）。\n");

        Integer disId = resolveDistributerIdForDepartment(departmentId);
        int rootDep = departmentIdAsIntOrSentinel(departmentId);
        if (disId == null || rootDep == Integer.MIN_VALUE) {
            sb.append("- 无法解析批发商 ID 或部门 ID，未查询采购表。\n\n");
            return sb.toString();
        }
        List<Integer> depIds = resolvePurchaseDepartmentIdsForAi(rootDep);
        if (depIds.isEmpty()) {
            sb.append("- 无采购部门范围，未查询。\n\n");
            return sb.toString();
        }

        String d0 = monthStart.toString();
        String d1 = monthEnd.toString();
        List<GbDistributerPurchaseGoodsEntity> rows = distributerPurchaseGoodsMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                        .in(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDepartmentId, depIds)
                        .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                        .ne(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, 9)
                        .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate)
                        .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                        .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1));
        log.info("采购事实查询: departmentId={}, disId={}, depIds={}, 行数={}", departmentId, disId, depIds, rows.size());
        sb.append("- 批发商 ID: ").append(disId).append("；采购部门 ID in ").append(depIds).append("\n");
        sb.append("- 匹配采购行数: ").append(rows.size()).append("\n");
        appendPurchaseSupplyMixSummary(sb, rows);
        appendDisWideSupplierDimensionPurchaseSummary(sb, disId, d0, d1);

        Map<Integer, BigDecimal> byGoods = new HashMap<>();
        BigDecimal monthTotal = BigDecimal.ZERO;
        for (GbDistributerPurchaseGoodsEntity r : rows) {
            String sub = r.getGbDpgBuySubtotal();
            if (StrUtil.isBlank(sub)) {
                continue;
            }
            try {
                BigDecimal amt = new BigDecimal(sub.trim());
                monthTotal = monthTotal.add(amt);
                Integer gid = r.getGbDpgDisGoodsId();
                if (gid != null) {
                    byGoods.merge(gid, amt, BigDecimal::add);
                }
            } catch (Exception ignored) {
                // skip bad number
            }
        }
        sb.append("- 本月采购金额合计(gb_DPG_buy_subtotal): ¥").append(monthTotal.setScale(2, RoundingMode.HALF_UP)).append("\n");

        List<Map.Entry<Integer, BigDecimal>> top = byGoods.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .toList();
        if (top.isEmpty()) {
            sb.append("- 按商品汇总：无有效金额行（可能入库未完成或未写入采购表）。\n");
        } else {
            sb.append("- 按分销商品 ID 汇总采购金额 Top（回答「采购金额最高」须引用本表，勿用库存减少 type=1）：\n");
            Map<Integer, String> nameCache = new HashMap<>();
            int rank = 1;
            for (Map.Entry<Integer, BigDecimal> e : top) {
                String nm = goodsNameFromCache(e.getKey(), nameCache);
                sb.append("  ").append(rank++).append(". ").append(nm)
                        .append(" (gb_DPG_dis_goods_id=").append(e.getKey()).append("): ¥")
                        .append(e.getValue().setScale(2, RoundingMode.HALF_UP)).append("\n");
            }
        }
        sb.append("- 摘录（按采购金额降序，最多 15 行，便于核对日期）：\n");
        List<GbDistributerPurchaseGoodsEntity> excerpt = new ArrayList<>(rows);
        excerpt.removeIf(r -> StrUtil.isBlank(r.getGbDpgBuySubtotal()));
        excerpt.sort((a, b) -> {
            try {
                return new BigDecimal(b.getGbDpgBuySubtotal().trim())
                        .compareTo(new BigDecimal(a.getGbDpgBuySubtotal().trim()));
            } catch (Exception ex) {
                return 0;
            }
        });
        Map<Integer, String> excerptNameCache = new HashMap<>();
        for (int i = 0; i < excerpt.size() && i < 15; i++) {
            GbDistributerPurchaseGoodsEntity r = excerpt.get(i);
            String nm = goodsNameFromCache(r.getGbDpgDisGoodsId(), excerptNameCache);
            String day = StrUtil.isBlank(r.getGbDpgStockFinishDate()) ? "?" : r.getGbDpgStockFinishDate();
            sb.append("  - ").append(nm).append("，入库完成日 ").append(day)
                    .append("，金额 ¥").append(r.getGbDpgBuySubtotal())
                    .append("，purchase_type=").append(r.getGbDpgPurchaseType() == null ? "?" : r.getGbDpgPurchaseType())
                    .append("，").append(nxSupplierChannelShort(r.getGbDpgPurchaseNxSupplierId()))
                    .append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 本月向供货商订货（配送商）入库：{@code gb_DPG_purchase_type} = {@link GbConstants.PurchaseOrderType#DELIVERY_SUPPLIER}（5），非自采 type=1。
     * <p>与同批发商采购统计一致：按 {@code gb_DPG_distributer_id} 全量入库行统计，**不按** {@code gb_DPG_purchase_department_id} 收窄。</p>
     */
    private String querySupplierOrderPurchaseGoodsFactsForAi(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月供货商订货采购】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        sb.append("- **gb_DPG_purchase_type**：**1**=自采；**5**=向供货商/配送商订货（DELIVERY_SUPPLIER）。**本块仅统计 type=5。**\n");
        sb.append("- **供货字段**：入库行 **gb_DPG_purchase_nx_supplier_id** 与同批次 **gb_dgs_nx_supplier_id** 对齐：正整数=供货商 ID；-1 表示自采（本块一般为供货商）。\n");
        sb.append("- **统计范围**：全批发商 `gb_DPG_distributer_id`（不按采购部门收窄），与前台采购统计同口径，避免配送入库挂在子部门时漏数。\n");
        sb.append("- 金额：gb_DPG_buy_subtotal；日期：gb_DPG_stock_finish_date；gb_DPG_status&gt;2。\n");
        sb.append("- **回答「供货商配送了哪些原料」须引用本块**；勿用【本月自采】或未区分 type 的采购汇总；勿把 type=5 说成自采。\n");

        Integer disId = resolveDistributerIdForDepartment(departmentId);
        if (disId == null) {
            sb.append("- 无法解析批发商 ID，未查询。\n\n");
            return sb.toString();
        }

        String d0 = monthStart.toString();
        String d1 = monthEnd.toString();
        List<GbDistributerPurchaseGoodsEntity> rows = distributerPurchaseGoodsMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER)
                        .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                        .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate)
                        .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                        .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1));

        log.info("[AI-SUPPLIER-ORDER] departmentId={} disId={} scope=distributer_wide type=5 rows={}", departmentId, disId, rows.size());
        sb.append("- 批发商 ID: ").append(disId).append("\n");
        sb.append("- 匹配订货入库行数(type=5): ").append(rows.size()).append("\n");

        Map<Integer, BigDecimal> byGoods = new HashMap<>();
        BigDecimal monthTotal = BigDecimal.ZERO;
        for (GbDistributerPurchaseGoodsEntity r : rows) {
            String sub = r.getGbDpgBuySubtotal();
            if (StrUtil.isBlank(sub)) {
                continue;
            }
            try {
                BigDecimal amt = new BigDecimal(sub.trim());
                monthTotal = monthTotal.add(amt);
                Integer gid = r.getGbDpgDisGoodsId();
                if (gid != null) {
                    byGoods.merge(gid, amt, BigDecimal::add);
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        sb.append("- 本月订货入库金额合计(仅 type=5, gb_DPG_buy_subtotal): ¥").append(monthTotal.setScale(2, RoundingMode.HALF_UP)).append("\n");

        List<Map.Entry<Integer, BigDecimal>> top = byGoods.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(40)
                .toList();
        if (top.isEmpty()) {
            sb.append("- 按商品汇总：无有效金额行。若业务实际有配送但此处为空，请核对入库行是否仍记为 type=1，或是否未写入 gb_DPG_stock_finish_date。**勿**臆造配送清单。\n");
        } else {
            sb.append("- **供货商配送涉及原料/商品**（按金额降序列出）：\n");
            Map<Integer, String> nameCache = new HashMap<>();
            int rank = 1;
            for (Map.Entry<Integer, BigDecimal> e : top) {
                String nm = goodsNameFromCache(e.getKey(), nameCache);
                sb.append("  ").append(rank++).append(". ").append(nm)
                        .append(" (gb_DPG_dis_goods_id=").append(e.getKey()).append("): ¥")
                        .append(e.getValue().setScale(2, RoundingMode.HALF_UP)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 用户问「单价波动」时注入：省略采购金额摘录，避免模型把 gb_DPG_buy_subtotal 误认为 gb_DPG_buy_price。
     */
    private String queryPurchaseGoodsFactsForAiVolatilityOmitSubtotalExcerpt(Long departmentId, LocalDate monthStart,
                                                                             LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月采购数据】（单价波动场景 · **金额摘录已省略**）(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        sb.append("- **字段区分**：**gb_DPG_buy_subtotal** = 本条入库「采购金额/小计」，常为几十～几百元；**gb_DPG_buy_price** = **入库单价**。回答单价波动时 **只能**用后者。\n");
        sb.append("- **严禁**把本段合计或历史上下文里的「金额¥」当成某一商品的单价。\n");
        sb.append("- **供货属性**：每笔入库行可查 **gb_DPG_purchase_nx_supplier_id**（-1 自采；正整数供货商 ID），与批次 **gb_dgs_nx_supplier_id** 对应。\n");

        Integer disId = resolveDistributerIdForDepartment(departmentId);
        int rootDep = departmentIdAsIntOrSentinel(departmentId);
        if (disId == null || rootDep == Integer.MIN_VALUE) {
            sb.append("- 无法解析批发商 ID 或部门 ID。\n\n");
            return sb.toString();
        }
        List<Integer> depIds = resolvePurchaseDepartmentIdsForAi(rootDep);
        if (depIds.isEmpty()) {
            sb.append("- 无采购部门范围。\n\n");
            return sb.toString();
        }

        String d0 = monthStart.toString();
        String d1 = monthEnd.toString();
        List<GbDistributerPurchaseGoodsEntity> rows = distributerPurchaseGoodsMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                        .in(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDepartmentId, depIds)
                        .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                        .ne(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                        .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate)
                        .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                        .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1));
        BigDecimal monthTotal = BigDecimal.ZERO;
        for (GbDistributerPurchaseGoodsEntity r : rows) {
            String sub = r.getGbDpgBuySubtotal();
            if (StrUtil.isBlank(sub)) {
                continue;
            }
            try {
                monthTotal = monthTotal.add(new BigDecimal(sub.trim()));
            } catch (Exception ignored) {
                // skip
            }
        }
        log.info("[AI-VOL] purchase_subtotal_block_omitted_excerpt departmentId={} disId={} depIds={} rows={} monthTotal={}",
                departmentId, disId, depIds, rows.size(), monthTotal);
        sb.append("- 批发商 ID: ").append(disId).append("；采购部门 ID in ").append(depIds).append("\n");
        sb.append("- 匹配采购行数: ").append(rows.size()).append("\n");
        appendPurchaseSupplyMixSummary(sb, rows);
        appendDisWideSupplierDimensionPurchaseSummary(sb, disId, d0, d1);
        sb.append("- 本月采购金额合计(gb_DPG_buy_subtotal): ¥").append(monthTotal.setScale(2, RoundingMode.HALF_UP))
                .append("（全月盘子参考；**勿当作任一 SKU 的入库单价**）\n\n");
        return sb.toString();
    }

    /**
     * 本月采购单价波动：默认与前台 {@link com.nongxinle.controller.GbDistributerPurchaseGoodsController#getGbPurGoodsStatisticsForDis}
     * 共用 {@link GbDistributerPurchaseGoodsMapper#queryGbPurchaseGoodsTopPriceFluctuation}（全批发商 disId 口径，不按采购部门收窄）。
     * 仅当用户只问「自采」且未出现采购/进货时，仍按部门范围自采行聚合。
     */
    private String queryPurchasePriceVolatilityFactsForAi(Long departmentId, LocalDate monthStart, LocalDate monthEnd,
                                                          String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月采购单价波动（采购商品行）】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        sb.append("- **仅字段 gb_DPG_buy_price（入库单价）**：表内「最高/最低/价差」**全部**来自该列；**绝非** gb_DPG_buy_subtotal（金额小计）。\n");
        sb.append("- **统计范围（默认）**：与接口 `getGbPurGoodsStatisticsForDis` → `queryGbPurchaseGoodsTopPriceFluctuation` 完全一致——仅按 `gb_DPG_distributer_id` + 入库完成日，**不按** `gb_DPG_purchase_department_id` 收窄。\n");
        sb.append("- 日期条件：gb_DPG_stock_finish_date；gb_DPG_status&gt;2；排除 gb_DPG_purchase_type=9。\n");
        sb.append("- 排名：Mapper 初筛后按**入库行逐项核验**重排，最多展示 10 条（与前台接口同源 SQL，额外剔除「单价实质相同」的假波动）。\n");
        sb.append("- **自采/供货商判定**：摘录行均带 **gb_DPG_purchase_nx_supplier_id**（**-1**=自采；正整数=供货商 ID），与同批次 **gb_dgs_nx_supplier_id** 对齐；勿仅用 gb_DPG_purchase_type 与用户口述「配送」强行对应。\n");

        Integer disId = resolveDistributerIdForDepartment(departmentId);
        int rootDep = departmentIdAsIntOrSentinel(departmentId);
        if (disId == null || rootDep == Integer.MIN_VALUE) {
            sb.append("- 无法解析批发商 ID 或部门 ID，未查询。\n\n");
            return sb.toString();
        }

        boolean selfOnly = SkillRouteFallback.volatilityFactsSelfPurchaseOnly(userMessage);
        String d0 = monthStart.toString();
        String d1 = monthEnd.toString();

        log.info("[AI-VOL] step=entry departmentId={} disId={} dateRange={}..{} selfOnly={}",
                departmentId, disId, d0, d1, selfOnly);

        if (!selfOnly) {
            Map<String, Object> fluctMap = new HashMap<>();
            fluctMap.put("disId", disId);
            fluctMap.put("startDate", d0);
            fluctMap.put("stopDate", d1);
            fluctMap.put("typeNotEqual", GbConstants.PurchaseOrderType.RETURN);

            List<GbDistributerGoodsEntity> rawTop = distributerPurchaseGoodsMapper.queryGbPurchaseGoodsTopPriceFluctuation(fluctMap);
            int rawN = rawTop == null ? 0 : rawTop.size();
            log.info("[AI-VOL] step=mapper_queryGbPurchaseGoodsTopPriceFluctuation disId={} startDate={} stopDate={} typeNotEqual={} rawRowCount={}",
                    disId, d0, d1, GbConstants.PurchaseOrderType.RETURN, rawN);

            List<VerifiedPurchaseVolatilityRow> verified = new ArrayList<>();
            if (rawTop != null) {
                int rankLog = 1;
                for (GbDistributerGoodsEntity g : rawTop) {
                    log.info("[AI-VOL][MAPPER_RAW] rank={} gb_DPG_dis_goods_id={} name={} low={} high={} diff={} fluctPct={}",
                            rankLog++,
                            g.getGbDistributerGoodsId(),
                            StrUtil.blankToDefault(g.getGbDgGoodsName(), ""),
                            g.getGbDgGoodsLowestPrice(),
                            g.getGbDgGoodsHighestPrice(),
                            g.getGoodsPriceDiff(),
                            g.getGoodsPriceFluctuation());
                    Integer gid = g.getGbDistributerGoodsId();
                    Optional<ReconciledPurchaseVolatility> rec = reconcilePurchaseVolatilityFromRawLines(disId, d0, d1, gid);
                    if (rec.isEmpty()) {
                        log.warn("[AI-VOL][RECONCILE_DROP] goodsId={} name={} mapperLow={} mapperHigh={} mapperPct={} reason=no_distinct_buy_price_after_raw_scan",
                                gid, StrUtil.blankToDefault(g.getGbDgGoodsName(), ""),
                                g.getGbDgGoodsLowestPrice(), g.getGbDgGoodsHighestPrice(), g.getGoodsPriceFluctuation());
                        continue;
                    }
                    verified.add(new VerifiedPurchaseVolatilityRow(g, rec.get()));
                }
            }
            verified.sort((a, b) -> {
                int c = b.vol().fluctuationPercent().compareTo(a.vol().fluctuationPercent());
                if (c != 0) {
                    return c;
                }
                return b.vol().spread().compareTo(a.vol().spread());
            });

            logDeptScopedPurchaseVolatilityDiag(departmentId, disId, rootDep, d0, d1);

            sb.append("- 批发商 ID: ").append(disId).append("\n");
            sb.append("- **核验规则**：逐行读取 **gb_DPG_buy_price**；至少两种不同入库单价才上榜。排名按核验后的相对波动降序。\n");
            if (verified.isEmpty()) {
                sb.append("- **本月无可核验的价格波动品**（可能单价均未录入、仅一单，或各笔单价实质相同）。\n\n");
                return sb.toString();
            }

            int showLimit = Math.min(10, verified.size());
            sb.append("- **入库单价波动 Top（gb_DPG_buy_price；已核验；前三名须逐字照抄）**：\n");
            Map<Integer, String> nameCache = new HashMap<>();
            List<Integer> topThreeIds = new ArrayList<>();
            for (int i = 0; i < showLimit; i++) {
                VerifiedPurchaseVolatilityRow row = verified.get(i);
                GbDistributerGoodsEntity g = row.meta();
                ReconciledPurchaseVolatility v = row.vol();
                Integer gid = g.getGbDistributerGoodsId();
                String nm = StrUtil.isNotBlank(g.getGbDgGoodsName())
                        ? g.getGbDgGoodsName()
                        : goodsNameFromCache(gid, nameCache);
                sb.append("  ").append(i + 1).append(". ").append(nm).append(" (gb_DPG_dis_goods_id=").append(gid).append(")")
                        .append(" **最低入库单价**¥").append(v.minPrice().setScale(1, RoundingMode.HALF_UP))
                        .append(" **最高入库单价**¥").append(v.maxPrice().setScale(1, RoundingMode.HALF_UP))
                        .append(" **入库单价价差**¥").append(v.spread().setScale(1, RoundingMode.HALF_UP))
                        .append(" 波动幅度").append(v.fluctuationPercent().setScale(1, RoundingMode.HALF_UP)).append("%")
                        .append(" 入库行数").append(v.lineCount()).append("\n");
                if (i < 3 && gid != null) {
                    topThreeIds.add(gid);
                }
            }
            appendPurchaseVolatilityDetailLines(sb, disId, d0, d1, topThreeIds, nameCache);
            sb.append("- 回答「波动最大的前三名」：**严格按上表 1～3 行**；最高/最低必须是 **同一字段 gb_DPG_buy_price**，不得混入金额小计。\n\n");
            return sb.toString();
        }

        // --- 仅「自采」：仍按采购部门 + 子部门收窄 ---
        sb.append("- 范围：**仅自采**（gb_DPG_purchase_type=").append(GbConstants.PurchaseOrderType.SELF_PURCHASE).append("），按本部门+子部门采购范围。\n");

        List<Integer> depIds = resolvePurchaseDepartmentIdsForAi(rootDep);
        if (depIds.isEmpty()) {
            sb.append("- 无采购部门范围，未查询。\n\n");
            log.warn("[AI-VOL] step=self_only_abort departmentId={} disId={} reason=no_dep_ids", departmentId, disId);
            return sb.toString();
        }

        LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity> qw = new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                .in(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDepartmentId, depIds)
                .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                .ne(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                .eq(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.SELF_PURCHASE)
                .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPrice)
                .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate)
                .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1);

        List<GbDistributerPurchaseGoodsEntity> rows = distributerPurchaseGoodsMapper.selectList(qw);
        log.info("[AI-VOL] step=self_only_query departmentId={} disId={} depIds={} matchingRows={}",
                departmentId, disId, depIds, rows.size());

        List<VolatilityGoodsAgg> ranked = rankPurchaseVolatilityFromPurchaseRows(rows);
        if (!ranked.isEmpty()) {
            VolatilityGoodsAgg r0 = ranked.get(0);
            Map<Integer, String> nc = new HashMap<>();
            log.info("[AI-VOL][SELF_ONLY_TOP1] goodsId={} name={} min={} max={} pct={}%",
                    r0.goodsId(), goodsNameFromCache(r0.goodsId(), nc),
                    r0.minPrice(), r0.maxPrice(), r0.fluctuationPercent().setScale(1, RoundingMode.HALF_UP));
        }

        sb.append("- 批发商 ID: ").append(disId).append("；采购部门 ID in ").append(depIds).append("\n");
        sb.append("- 匹配行数: ").append(rows.size()).append("\n");

        Map<Integer, List<VolatilityPricePoint>> byGoods = groupPurchaseRowsToVolatilityPoints(rows);
        int singles = (int) byGoods.values().stream().filter(pts -> pts.size() == 1).count();
        sb.append("- 本月仅 1 笔有效入库价的品: ").append(singles).append(" 个\n");

        if (ranked.isEmpty()) {
            sb.append("- **无可比对的多笔自采单价**。\n\n");
            return sb.toString();
        }

        sb.append("- **价格波动 Top（自采·按部门收窄；均为 gb_DPG_buy_price）**：\n");
        Map<Integer, String> nameCache = new HashMap<>();
        int limit = Math.min(15, ranked.size());
        List<Integer> topThreeIds = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            VolatilityGoodsAgg ag = ranked.get(i);
            String nm = goodsNameFromCache(ag.goodsId(), nameCache);
            sb.append("  ").append(i + 1).append(". ").append(nm).append(" (gb_DPG_dis_goods_id=").append(ag.goodsId()).append(")")
                    .append(" **最低入库单价**¥").append(ag.minPrice().setScale(4, RoundingMode.HALF_UP))
                    .append(" **最高入库单价**¥").append(ag.maxPrice().setScale(4, RoundingMode.HALF_UP))
                    .append(" **入库单价价差**¥").append(ag.spread().setScale(4, RoundingMode.HALF_UP))
                    .append(" 波动幅度").append(ag.fluctuationPercent().setScale(1, RoundingMode.HALF_UP)).append("%")
                    .append(" 有效笔数").append(ag.pointCount()).append("\n");
            if (i < 3) {
                topThreeIds.add(ag.goodsId());
            }
        }

        appendPurchaseVolatilityDetailLinesFromPoints(sb, topThreeIds, ranked, nameCache);
        sb.append("- 回答「波动最大是哪个」：**默认指上表第 1 名**（本块为自采+部门收窄，与全口径前台可能不一致）。\n\n");
        return sb.toString();
    }

    /**
     * 对照日志：用「部门收窄」重算一遍 TOP1，便于与历史逻辑对比；正式结果已走 mapper 全批发商口径。
     */
    private void logDeptScopedPurchaseVolatilityDiag(Long departmentId, Integer disId, int rootDep, String d0, String d1) {
        if (!log.isInfoEnabled()) {
            return;
        }
        List<Integer> depIds = resolvePurchaseDepartmentIdsForAi(rootDep);
        log.info("[AI-VOL][DIAG] deptScoped_depIds={} note=若 TOP1 与 MAPPER_TOP 不一致，通常因采购行挂在子部门/其他档口，前台接口不按部门过滤",
                depIds);
        if (depIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity> qw = new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                .in(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDepartmentId, depIds)
                .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                .ne(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPrice)
                .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate)
                .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1);
        List<GbDistributerPurchaseGoodsEntity> depRows = distributerPurchaseGoodsMapper.selectList(qw);
        List<VolatilityGoodsAgg> depRanked = rankPurchaseVolatilityFromPurchaseRows(depRows);
        log.info("[AI-VOL][DIAG] departmentId={} deptScoped_rowCount={}", departmentId, depRows.size());
        if (!depRanked.isEmpty()) {
            VolatilityGoodsAgg a = depRanked.get(0);
            Map<Integer, String> nc = new HashMap<>();
            log.info("[AI-VOL][DIAG_DEPT_TOP1] goodsId={} name={} min={} max={} spread={} pct={}%",
                    a.goodsId(), goodsNameFromCache(a.goodsId(), nc),
                    a.minPrice(), a.maxPrice(), a.spread(), a.fluctuationPercent().setScale(1, RoundingMode.HALF_UP));
        }
    }

    /**
     * 按原始入库行重算 MIN/MAX：至少两行有效单价，且 stripTrailingZeros 后至少两种不同数值，才视为真有波动。
     */
    private Optional<ReconciledPurchaseVolatility> reconcilePurchaseVolatilityFromRawLines(Integer disId, String d0, String d1,
                                                                                           Integer gid) {
        if (gid == null) {
            return Optional.empty();
        }
        List<GbDistributerPurchaseGoodsEntity> lines = distributerPurchaseGoodsMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDisGoodsId, gid)
                        .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                        .ne(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                        .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPrice)
                        .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                        .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1)
                        .orderByAsc(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate));
        List<BigDecimal> prices = new ArrayList<>();
        for (GbDistributerPurchaseGoodsEntity r : lines) {
            parseGbDpgBuyPrice(r).ifPresent(prices::add);
        }
        if (prices.size() < 2) {
            return Optional.empty();
        }
        Set<String> distinctNorm = new HashSet<>();
        for (BigDecimal p : prices) {
            distinctNorm.add(p.stripTrailingZeros().toPlainString());
        }
        if (distinctNorm.size() < 2) {
            return Optional.empty();
        }
        BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (min.compareTo(BigDecimal.ZERO) <= 0 || max.compareTo(min) <= 0) {
            return Optional.empty();
        }
        BigDecimal spread = max.subtract(min);
        BigDecimal pct = spread.divide(min, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal spreadR = spread.setScale(1, RoundingMode.HALF_UP);
        return Optional.of(new ReconciledPurchaseVolatility(min, max, spreadR, pct, prices.size()));
    }

    private record ReconciledPurchaseVolatility(BigDecimal minPrice, BigDecimal maxPrice, BigDecimal spread,
                                                BigDecimal fluctuationPercent, int lineCount) {}

    private record VerifiedPurchaseVolatilityRow(GbDistributerGoodsEntity meta, ReconciledPurchaseVolatility vol) {}

    private void appendPurchaseVolatilityDetailLines(StringBuilder sb, Integer disId, String d0, String d1,
                                                     List<Integer> goodsIds, Map<Integer, String> nameCache) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return;
        }
        sb.append("- 摘录（前 3 个品：每笔 **入库单价** `gb_DPG_buy_price`，**不是**小计）：\n");
        for (Integer gid : goodsIds) {
            if (gid == null) {
                continue;
            }
            List<GbDistributerPurchaseGoodsEntity> lines = distributerPurchaseGoodsMapper.selectList(
                    new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                            .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                            .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDisGoodsId, gid)
                            .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                            .ne(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                            .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgBuyPrice)
                            .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                            .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1)
                            .orderByAsc(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate));
            String nm = goodsNameFromCache(gid, nameCache);
            sb.append("  · ").append(nm).append(":\n");
            int cap = Math.min(12, lines.size());
            for (int j = 0; j < cap; j++) {
                GbDistributerPurchaseGoodsEntity r = lines.get(j);
                Optional<BigDecimal> p = parseGbDpgBuyPrice(r);
                if (p.isEmpty()) {
                    continue;
                }
                String day = StrUtil.blankToDefault(r.getGbDpgStockFinishDate(), "?");
                sb.append("    - ").append(day).append(" ¥").append(p.get().setScale(4, RoundingMode.HALF_UP))
                        .append(" (gb_DPG_buy_price)，purchase_type=").append(r.getGbDpgPurchaseType() == null ? "?" : r.getGbDpgPurchaseType())
                        .append("，").append(nxSupplierChannelShort(r.getGbDpgPurchaseNxSupplierId()))
                        .append("\n");
            }
        }
    }

    private void appendPurchaseVolatilityDetailLinesFromPoints(StringBuilder sb, List<Integer> goodsIds,
                                                             List<VolatilityGoodsAgg> ranked,
                                                             Map<Integer, String> nameCache) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return;
        }
        sb.append("- 摘录（波动 Top 前 3 个品，按入库日）：\n");
        Map<Integer, VolatilityGoodsAgg> byId = new HashMap<>();
        for (VolatilityGoodsAgg a : ranked) {
            byId.put(a.goodsId(), a);
        }
        for (Integer gid : goodsIds) {
            if (gid == null) {
                continue;
            }
            VolatilityGoodsAgg ag = byId.get(gid);
            if (ag == null) {
                continue;
            }
            String nm = goodsNameFromCache(gid, nameCache);
            sb.append("  · ").append(nm).append(":\n");
            List<VolatilityPricePoint> pts = new ArrayList<>(ag.points());
            pts.sort(Comparator.comparing(VolatilityPricePoint::finishDate, Comparator.nullsLast(String::compareTo)));
            int cap = Math.min(12, pts.size());
            for (int j = 0; j < cap; j++) {
                VolatilityPricePoint p = pts.get(j);
                sb.append("    - ").append(p.finishDate()).append(" ¥").append(p.price().setScale(4, RoundingMode.HALF_UP))
                        .append(" (gb_DPG_buy_price)，purchase_type=").append(p.purchaseType() == null ? "?" : p.purchaseType())
                        .append("，").append(nxSupplierChannelShort(p.nxSupplierId()))
                        .append("\n");
            }
        }
    }

    private Map<Integer, List<VolatilityPricePoint>> groupPurchaseRowsToVolatilityPoints(List<GbDistributerPurchaseGoodsEntity> rows) {
        Map<Integer, List<VolatilityPricePoint>> byGoods = new HashMap<>();
        for (GbDistributerPurchaseGoodsEntity r : rows) {
            Integer gid = r.getGbDpgDisGoodsId();
            if (gid == null) {
                continue;
            }
            Optional<BigDecimal> pu = parseGbDpgBuyPrice(r);
            if (pu.isEmpty()) {
                continue;
            }
            String day = StrUtil.blankToDefault(r.getGbDpgStockFinishDate(), "?");
            byGoods.computeIfAbsent(gid, k -> new ArrayList<>()).add(new VolatilityPricePoint(pu.get(), day,
                    r.getGbDpgPurchaseType(), r.getGbDpgPurchaseNxSupplierId()));
        }
        return byGoods;
    }

    private List<VolatilityGoodsAgg> rankPurchaseVolatilityFromPurchaseRows(List<GbDistributerPurchaseGoodsEntity> rows) {
        Map<Integer, List<VolatilityPricePoint>> byGoods = groupPurchaseRowsToVolatilityPoints(rows);
        List<VolatilityGoodsAgg> ranked = new ArrayList<>();
        for (Map.Entry<Integer, List<VolatilityPricePoint>> e : byGoods.entrySet()) {
            List<VolatilityPricePoint> pts = e.getValue();
            if (pts.size() < 2) {
                continue;
            }
            Set<String> distinctNorm = new HashSet<>();
            for (VolatilityPricePoint x : pts) {
                distinctNorm.add(x.price().stripTrailingZeros().toPlainString());
            }
            if (distinctNorm.size() < 2) {
                continue;
            }
            BigDecimal min = pts.stream().map(VolatilityPricePoint::price).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = pts.stream().map(VolatilityPricePoint::price).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal spread = max.subtract(min);
            BigDecimal fluctPct = min.compareTo(BigDecimal.ZERO) > 0
                    ? spread.divide(min, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;
            ranked.add(new VolatilityGoodsAgg(e.getKey(), min, max, spread, fluctPct, pts.size(), pts));
        }
        ranked.sort((a, b) -> {
            int c = b.fluctuationPercent().compareTo(a.fluctuationPercent());
            if (c != 0) {
                return c;
            }
            return b.spread().compareTo(a.spread());
        });
        return ranked;
    }

    /** 仅解析 GB 入库单价 gb_DPG_buy_price。 */
    private Optional<BigDecimal> parseGbDpgBuyPrice(GbDistributerPurchaseGoodsEntity r) {
        return Optional.ofNullable(parseStrictlyPositiveDecimal(r.getGbDpgBuyPrice()));
    }

    private static BigDecimal parseStrictlyPositiveDecimal(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            if (v.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private record VolatilityPricePoint(BigDecimal price, String finishDate, Integer purchaseType, Integer nxSupplierId) {}

    private record VolatilityGoodsAgg(int goodsId, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal spread,
                                      BigDecimal fluctuationPercent, int pointCount, List<VolatilityPricePoint> points) {}

    /**
     * 本月自采：采购商品行 {@code gb_DPG_purchase_type} = {@link GbConstants.PurchaseOrderType#SELF_PURCHASE}，
     * 金额汇总 {@code gb_DPG_buy_subtotal}（与「本月采购数据」全量口径区分）。
     */
    private String querySelfPurchaseGoodsFactsForAi(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月自采金额（采购商品行）】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        sb.append("- 表：gb_distributer_purchase_goods；自采条件：gb_DPG_purchase_type=")
                .append(GbConstants.PurchaseOrderType.SELF_PURCHASE)
                .append("（GbConstants.PurchaseOrderType.SELF_PURCHASE）。\n");
        sb.append("- **勿误解**：本块仅筛 **purchase_type=1**；其中仍可有 **gb_DPG_purchase_nx_supplier_id 为正整数** 的供货商供货入库。**禁止**因「本块合计≈【本月采购数据】合计」对用户断言「100%自采、无供货商配送」——须看【本月采购数据】中的供货摘要与摘录。\n");
        sb.append("- 金额：gb_DPG_buy_subtotal；日期：gb_DPG_stock_finish_date（入库完成日）；状态：gb_DPG_status&gt;2。\n");

        Integer disId = resolveDistributerIdForDepartment(departmentId);
        int rootDep = departmentIdAsIntOrSentinel(departmentId);
        if (disId == null || rootDep == Integer.MIN_VALUE) {
            sb.append("- 无法解析批发商 ID 或部门 ID，未查询。\n\n");
            return sb.toString();
        }
        List<Integer> depIds = resolvePurchaseDepartmentIdsForAi(rootDep);
        if (depIds.isEmpty()) {
            sb.append("- 无采购部门范围，未查询。\n\n");
            return sb.toString();
        }
        String d0 = monthStart.toString();
        String d1 = monthEnd.toString();
        List<GbDistributerPurchaseGoodsEntity> rows = distributerPurchaseGoodsMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                        .in(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseDepartmentId, depIds)
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.SELF_PURCHASE)
                        .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                        .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate)
                        .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                        .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1));
        log.info("自采事实查询: departmentId={}, disId={}, depIds={}, 行数={}", departmentId, disId, depIds, rows.size());
        sb.append("- 批发商 ID: ").append(disId).append("；采购部门 ID in ").append(depIds).append("\n");
        sb.append("- 匹配行数: ").append(rows.size()).append("\n");

        BigDecimal total = BigDecimal.ZERO;
        Map<Integer, BigDecimal> byGoods = new HashMap<>();
        for (GbDistributerPurchaseGoodsEntity r : rows) {
            if (StrUtil.isBlank(r.getGbDpgBuySubtotal())) {
                continue;
            }
            try {
                BigDecimal amt = new BigDecimal(r.getGbDpgBuySubtotal().trim());
                total = total.add(amt);
                if (r.getGbDpgDisGoodsId() != null) {
                    byGoods.merge(r.getGbDpgDisGoodsId(), amt, BigDecimal::add);
                }
            } catch (Exception ignored) {
                // skip
            }
        }
        sb.append("- 本月自采金额合计(gb_DPG_buy_subtotal): ¥").append(total.setScale(2, RoundingMode.HALF_UP)).append("\n");
        List<Map.Entry<Integer, BigDecimal>> top = byGoods.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .toList();
        if (!top.isEmpty()) {
            sb.append("- 自采金额按商品 Top5：\n");
            Map<Integer, String> nameCache = new HashMap<>();
            int rank = 1;
            for (Map.Entry<Integer, BigDecimal> e : top) {
                sb.append("  ").append(rank++).append(". ")
                        .append(goodsNameFromCache(e.getKey(), nameCache))
                        .append(" (gb_DPG_dis_goods_id=").append(e.getKey()).append("): ¥")
                        .append(e.getValue().setScale(2, RoundingMode.HALF_UP)).append("\n");
            }
        }
        sb.append("- 回答「自采金额」须引用本块合计；勿用全量【本月采购数据】或库存减少代替。\n\n");
        return sb.toString();
    }

    /**
     * 供货商未结账款：与 {@code GbDistributerPurchaseBatchController#sellerDistributerPurchaseBatchsGb} 一致，
     * 未结进货 = status {@link GbConstants.DistributorPurchaseBatchStatus#RECEIPT_FINISHED} 且非退货批次；未结退货单独扣减。
     */
    private String querySupplierUnsettledFactsForAi(Long departmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("【供货商未结账款（采购批次）】\n");
        sb.append("- 表：gb_distributer_purchase_batch；金额：gb_dpb_subtotal。\n");
        sb.append("- 状态常量 GbConstants.DistributorPurchaseBatchStatus：RECEIPT_FINISHED(3)=收货完成/未结账；PAYMENT_FINISHED(4)=结账完成。\n");
        sb.append("- 未结进货：gb_dpb_status=3 且 gb_dpb_purchase_type≠")
                .append(GbConstants.PurchaseOrderType.RETURN).append("；未结退货：status=3 且 type=")
                .append(GbConstants.PurchaseOrderType.RETURN).append("；净未结=进货小计−退货小计。\n");

        Integer disId = resolveDistributerIdForDepartment(departmentId);
        int rootDep = departmentIdAsIntOrSentinel(departmentId);
        if (disId == null || rootDep == Integer.MIN_VALUE) {
            sb.append("- 无法解析批发商或部门，未查询。\n\n");
            return sb.toString();
        }
        List<Integer> depIds = resolvePurchaseDepartmentIdsForAi(rootDep);
        sb.append("- 批发商 ID: ").append(disId).append("；采购部门（gb_dpb_pur_department_id）in ").append(depIds).append("\n");

        List<GbDistributerPurchaseBatchEntity> normalRows = distributerPurchaseBatchMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseBatchEntity>()
                        .eq(GbDistributerPurchaseBatchEntity::getGbDpbDistributerId, disId)
                        .eq(GbDistributerPurchaseBatchEntity::getGbDpbStatus, GbConstants.DistributorPurchaseBatchStatus.RECEIPT_FINISHED)
                        .ne(GbDistributerPurchaseBatchEntity::getGbDpbPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                        .in(GbDistributerPurchaseBatchEntity::getGbDpbPurDepartmentId, depIds));
        List<GbDistributerPurchaseBatchEntity> returnRows = distributerPurchaseBatchMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseBatchEntity>()
                        .eq(GbDistributerPurchaseBatchEntity::getGbDpbDistributerId, disId)
                        .eq(GbDistributerPurchaseBatchEntity::getGbDpbStatus, GbConstants.DistributorPurchaseBatchStatus.RECEIPT_FINISHED)
                        .eq(GbDistributerPurchaseBatchEntity::getGbDpbPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                        .in(GbDistributerPurchaseBatchEntity::getGbDpbPurDepartmentId, depIds));

        BigDecimal unPayOrder = sumPurchaseBatchSubtotals(normalRows);
        BigDecimal unPayReturn = sumPurchaseBatchSubtotals(returnRows);
        BigDecimal net = unPayOrder.subtract(unPayReturn).setScale(2, RoundingMode.HALF_UP);
        log.info("未结账款事实: departmentId={}, disId={}, 未结进货批次数={}, 未结退货批次数={}, net={}",
                departmentId, disId, normalRows.size(), returnRows.size(), net);

        sb.append("- 未结进货批次数: ").append(normalRows.size()).append("，小计: ¥")
                .append(unPayOrder.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("- 未结退货批次数: ").append(returnRows.size()).append("，小计: ¥")
                .append(unPayReturn.setScale(2, RoundingMode.HALF_UP)).append("\n");
        sb.append("- **净未结账款（应付供货商）**: ¥").append(net).append("\n");

        Map<Integer, BigDecimal> netBySupplier = new HashMap<>();
        for (GbDistributerPurchaseBatchEntity b : normalRows) {
            Integer sid = b.getGbDpbSupplierId();
            if (sid == null) {
                continue;
            }
            netBySupplier.merge(sid, parseMoneyField(b.getGbDpbSubtotal()), BigDecimal::add);
        }
        for (GbDistributerPurchaseBatchEntity b : returnRows) {
            Integer sid = b.getGbDpbSupplierId();
            if (sid == null) {
                continue;
            }
            BigDecimal one = parseMoneyField(b.getGbDpbSubtotal());
            netBySupplier.merge(sid, one, (old, v) -> old.subtract(v));
        }
        List<Map.Entry<Integer, BigDecimal>> supTop = netBySupplier.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .toList();
        if (!supTop.isEmpty()) {
            sb.append("- 按供货商净未结 Top（nx_jrdh_supplier）：\n");
            int rank = 1;
            for (Map.Entry<Integer, BigDecimal> e : supTop) {
                sb.append("  ").append(rank++).append(". ")
                        .append(supplierDisplayName(e.getKey()))
                        .append(" (gb_dpb_supplier_id=").append(e.getKey()).append("): ¥")
                        .append(e.getValue().setScale(2, RoundingMode.HALF_UP)).append("\n");
            }
        }
        sb.append("- 回答「供货商未结账款」时须引用本块净额与上表；**禁止**用库存减少或日营业额代替本口径。\n\n");
        return sb.toString();
    }

    private static BigDecimal sumPurchaseBatchSubtotals(List<GbDistributerPurchaseBatchEntity> rows) {
        BigDecimal s = BigDecimal.ZERO;
        for (GbDistributerPurchaseBatchEntity r : rows) {
            s = s.add(parseMoneyField(r.getGbDpbSubtotal()));
        }
        return s;
    }

    private static BigDecimal parseMoneyField(String raw) {
        if (StrUtil.isBlank(raw)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String supplierDisplayName(Integer supplierId) {
        if (supplierId == null) {
            return "未关联供货商";
        }
        NxJrdhSupplierEntity s = nxJrdhSupplierMapper.selectById(supplierId);
        if (s != null && StrUtil.isNotBlank(s.getNxJrdhsSupplierName())) {
            return s.getNxJrdhsSupplierName();
        }
        return "供货商(ID=" + supplierId + ")";
    }

    private Integer resolveDistributerIdForDepartment(Long departmentId) {
        GbAiRestaurantProfileEntity profile = restaurantProfileMapper.selectOne(
                new LambdaQueryWrapper<GbAiRestaurantProfileEntity>()
                        .eq(GbAiRestaurantProfileEntity::getGbAiRestaurantProfileDepartmentId, departmentId));
        if (profile != null && profile.getGbAiRestaurantProfileDistributerId() != null) {
            long d = profile.getGbAiRestaurantProfileDistributerId();
            if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return (int) d;
            }
        }
        int dep = departmentIdAsIntOrSentinel(departmentId);
        if (dep == Integer.MIN_VALUE) {
            return null;
        }
        GbDepartmentEntity depRow = departmentMapper.selectById(dep);
        return depRow != null ? depRow.getGbDepartmentDisId() : null;
    }

    /**
     * 采购部门范围：本部门 + 直接子部门（采购行上的 gb_DPG_purchase_department_id 多为子厨房/档口）。
     */
    private List<Integer> resolvePurchaseDepartmentIdsForAi(int rootDepId) {
        List<Integer> ids = new ArrayList<>();
        ids.add(rootDepId);
        List<GbDepartmentEntity> children = departmentMapper.selectList(
                new LambdaQueryWrapper<GbDepartmentEntity>()
                        .eq(GbDepartmentEntity::getGbDepartmentFatherId, rootDepId));
        for (GbDepartmentEntity c : children) {
            if (c.getGbDepartmentId() != null) {
                ids.add(c.getGbDepartmentId());
            }
        }
        return ids;
    }

    private void appendTopMoneyLines(StringBuilder sb, String title, Map<String, BigDecimal> totals, int limit) {
        if (totals.isEmpty()) {
            return;
        }
        sb.append(title).append("\n");
        totals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .forEach(e -> sb.append("  - ").append(e.getKey()).append(": ¥").append(e.getValue()).append("\n"));
    }

    /**
     * 将库存流水按金额降序列出，并解析分销商品名、部门名、业务日期，供模型直接对用户表述。
     */
    private void appendStockReduceReadableLines(StringBuilder sb, List<GbDepartmentGoodsStockReduceEntity> reduces, int limit) {
        List<GbDepartmentGoodsStockReduceEntity> withAmount = new ArrayList<>();
        for (GbDepartmentGoodsStockReduceEntity r : reduces) {
            if (r.getGbDgsrSubtotal() == null || r.getGbDgsrSubtotal().isEmpty() || r.getGbDgsrType() == null) {
                continue;
            }
            try {
                new BigDecimal(r.getGbDgsrSubtotal());
                withAmount.add(r);
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (withAmount.isEmpty()) {
            return;
        }
        withAmount.sort((a, b) -> {
            BigDecimal as = new BigDecimal(a.getGbDgsrSubtotal());
            BigDecimal bs = new BigDecimal(b.getGbDgsrSubtotal());
            return bs.compareTo(as);
        });
        sb.append("【流水摘录】（回答时请用商品名、部门名与日期；勿仅用分销商品 ID；按金额降序最多 ")
                .append(limit).append(" 条）\n");
        Map<Integer, String> goodsCache = new HashMap<>();
        Map<Integer, String> deptCache = new HashMap<>();
        int n = 0;
        for (GbDepartmentGoodsStockReduceEntity r : withAmount) {
            if (n >= limit) {
                break;
            }
            String goods = goodsNameFromCache(r.getGbDgsrGbDisGoodsId(), goodsCache);
            String dept = deptNameFromCache(r.getGbDgsrGbDepartmentId(), deptCache);
            String day = StrUtil.isNotBlank(r.getGbDgsrDate()) ? r.getGbDgsrDate() : "日期未填";
            String typeLabel = stockReduceTypeLabel(r.getGbDgsrType());
            sb.append("  - ").append(goods).append("，").append(dept).append("，").append(day).append("，")
                    .append(typeLabel).append("：¥").append(r.getGbDgsrSubtotal())
                    .append("，").append(nxSupplierChannelShort(r.getGbDgsrStockNxSupplierId()))
                    .append("\n");
            n++;
        }
    }

    private static BigDecimal parseGbDpgBuySubtotalTrim(GbDistributerPurchaseGoodsEntity r) {
        String sub = r.getGbDpgBuySubtotal();
        if (StrUtil.isBlank(sub)) {
            return null;
        }
        try {
            return new BigDecimal(sub.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 按采购部门收窄的本月行：拆分 type=5、type=1+nx 正、type=1+自采口径，防止模型把「全是 type=1」说成 100% 无供货商。
     */
    private static void appendPurchaseSupplyMixSummary(StringBuilder sb, List<GbDistributerPurchaseGoodsEntity> rows) {
        int n5 = 0;
        int n1SupplierNx = 0;
        int n1SelfNx = 0;
        int nOther = 0;
        BigDecimal s5 = BigDecimal.ZERO;
        BigDecimal s1SupplierNx = BigDecimal.ZERO;
        BigDecimal s1SelfNx = BigDecimal.ZERO;
        BigDecimal sOther = BigDecimal.ZERO;
        for (GbDistributerPurchaseGoodsEntity r : rows) {
            BigDecimal amt = parseGbDpgBuySubtotalTrim(r);
            Integer t = r.getGbDpgPurchaseType();
            Integer nx = r.getGbDpgPurchaseNxSupplierId();
            if (Objects.equals(t, GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER)) {
                n5++;
                if (amt != null) {
                    s5 = s5.add(amt);
                }
            } else if (Objects.equals(t, GbConstants.PurchaseOrderType.SELF_PURCHASE)) {
                if (nx != null && nx != -1) {
                    n1SupplierNx++;
                    if (amt != null) {
                        s1SupplierNx = s1SupplierNx.add(amt);
                    }
                } else {
                    n1SelfNx++;
                    if (amt != null) {
                        s1SelfNx = s1SelfNx.add(amt);
                    }
                }
            } else {
                nOther++;
                if (amt != null) {
                    sOther = sOther.add(amt);
                }
            }
        }
        sb.append("- **供货属性摘要（本块采购部门范围内）**：type=5 **").append(n5).append("** 笔 ¥")
                .append(s5.setScale(2, RoundingMode.HALF_UP));
        sb.append("；type=1 且 **nx_supplier_id 为正整数** **").append(n1SupplierNx).append("** 笔 ¥")
                .append(s1SupplierNx.setScale(2, RoundingMode.HALF_UP)).append("（供货商维度入库，**勿**统称「纯自采」）");
        sb.append("；type=1 且 **nx=-1 或未填** **").append(n1SelfNx).append("** 笔 ¥")
                .append(s1SelfNx.setScale(2, RoundingMode.HALF_UP));
        if (nOther > 0) {
            sb.append("；其它 purchase_type **").append(nOther).append("** 笔 ¥")
                    .append(sOther.setScale(2, RoundingMode.HALF_UP));
        }
        sb.append("。**禁止**在 **type=5＞0** 或 **type=1 且 nx 为正＞0** 时向用户说「100%自采」「完全没有供货商配送」。\n");
    }

    /** 全 disId 不按采购部门收窄，避免部门口径漏掉挂在其它档口的供货商入库。 */
    private void appendDisWideSupplierDimensionPurchaseSummary(StringBuilder sb, Integer disId, String d0, String d1) {
        List<GbDistributerPurchaseGoodsEntity> wide = distributerPurchaseGoodsMapper.selectList(
                new LambdaQueryWrapper<GbDistributerPurchaseGoodsEntity>()
                        .eq(GbDistributerPurchaseGoodsEntity::getGbDpgDistributerId, disId)
                        .gt(GbDistributerPurchaseGoodsEntity::getGbDpgStatus, 2)
                        .ne(GbDistributerPurchaseGoodsEntity::getGbDpgPurchaseType, GbConstants.PurchaseOrderType.RETURN)
                        .isNotNull(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate)
                        .ge(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d0)
                        .le(GbDistributerPurchaseGoodsEntity::getGbDpgStockFinishDate, d1));
        int n5 = 0;
        int n1Nx = 0;
        BigDecimal s5 = BigDecimal.ZERO;
        BigDecimal s1Nx = BigDecimal.ZERO;
        for (GbDistributerPurchaseGoodsEntity r : wide) {
            BigDecimal amt = parseGbDpgBuySubtotalTrim(r);
            Integer t = r.getGbDpgPurchaseType();
            Integer nx = r.getGbDpgPurchaseNxSupplierId();
            if (Objects.equals(t, GbConstants.PurchaseOrderType.DELIVERY_SUPPLIER)) {
                n5++;
                if (amt != null) {
                    s5 = s5.add(amt);
                }
            } else if (Objects.equals(t, GbConstants.PurchaseOrderType.SELF_PURCHASE)
                    && nx != null && nx != -1) {
                n1Nx++;
                if (amt != null) {
                    s1Nx = s1Nx.add(amt);
                }
            }
        }
        sb.append("- **全批发商入库（不按 gb_DPG_purchase_department_id 收窄）供货维度**：type=5 **").append(n5)
                .append("** 笔 ¥").append(s5.setScale(2, RoundingMode.HALF_UP));
        sb.append("；type=1 且 nx_supplier_id 为正整数 **").append(n1Nx).append("** 笔 ¥")
                .append(s1Nx.setScale(2, RoundingMode.HALF_UP));
        sb.append("。**若任一侧笔数＞0**，即存在供货商维度入库，**禁止**对用户下「100%自采」「全无供货商配送」结论")
                .append("；若均为 0，也请避免绝对化，可写「本统计口径下未见供货商维度记录」。\n");
    }

    private static String nxSupplierChannelShort(Integer nxSupplierId) {
        if (nxSupplierId == null) {
            return "nx_supplier_id 未填";
        }
        if (nxSupplierId == -1) {
            return "自采(nx_supplier_id=-1)";
        }
        return "供货商配送(nx_supplier_id=" + nxSupplierId + ")";
    }

    private static String stockReduceTypeLabel(Integer type) {
        if (type == null) {
            return "类型未知";
        }
        switch (type) {
            case 1:
                return "成本";
            case 2:
                return "损耗";
            case 3:
                return "废弃";
            case 4:
                return "退货";
            default:
                return "类型" + type;
        }
    }

    private String goodsNameFromCache(Integer id, Map<Integer, String> cache) {
        if (id == null) {
            return "未关联商品";
        }
        return cache.computeIfAbsent(id, this::loadDisGoodsName);
    }

    private String loadDisGoodsName(Integer id) {
        GbDistributerGoodsEntity g = distributerGoodsMapper.selectById(id);
        if (g == null) {
            return "分销商品(ID=" + id + ")";
        }
        if (StrUtil.isNotBlank(g.getGbDgGoodsName())) {
            return g.getGbDgGoodsName();
        }
        return "分销商品(ID=" + id + ")";
    }

    private String deptNameFromCache(Integer id, Map<Integer, String> cache) {
        if (id == null) {
            return "未关联部门";
        }
        return cache.computeIfAbsent(id, this::loadDepartmentDisplayName);
    }

    private String loadDepartmentDisplayName(Integer id) {
        GbDepartmentEntity d = departmentMapper.selectById(id);
        if (d == null) {
            return "部门(ID=" + id + ")";
        }
        if (StrUtil.isNotBlank(d.getGbDepartmentName())) {
            return d.getGbDepartmentName();
        }
        return "部门(ID=" + id + ")";
    }

    /**
     * 查询营收数据
     */
    private String queryRevenueData(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月营业额数据】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");

        List<GbAiDailyRevenueEntity> revenues = dailyRevenueMapper.selectList(
                new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, departmentId)
                        .between(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, monthStart, monthEnd)
                        .orderByAsc(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate)
        );

        log.info("营收查询: 查到 {} 条营业额记录", revenues.size());

        if (!revenues.isEmpty()) {
            BigDecimal totalDineIn = BigDecimal.ZERO, totalTakeout = BigDecimal.ZERO, totalPlatformFee = BigDecimal.ZERO;

            for (GbAiDailyRevenueEntity r : revenues) {
                if (r.getGbAiDailyRevenueDineInRevenue() != null) totalDineIn = totalDineIn.add(r.getGbAiDailyRevenueDineInRevenue());
                if (r.getGbAiDailyRevenueTakeoutRevenue() != null) totalTakeout = totalTakeout.add(r.getGbAiDailyRevenueTakeoutRevenue());
                if (r.getGbAiDailyRevenuePlatformFee() != null) totalPlatformFee = totalPlatformFee.add(r.getGbAiDailyRevenuePlatformFee());
            }

            BigDecimal totalRevenue = totalDineIn.add(totalTakeout).subtract(totalPlatformFee);
            int daysInMonth = LocalDate.now().getDayOfMonth();
            double avgDaily = totalRevenue.doubleValue() / revenues.size();

            sb.append("- 记录天数: ").append(revenues.size()).append(" 天（共 ").append(daysInMonth).append(" 天）\n");
            sb.append("- 堂食营收: ¥").append(totalDineIn).append(", 外卖营收: ¥").append(totalTakeout).append("\n");
            sb.append("- 平台抽成: ¥").append(totalPlatformFee).append(", 本月总营收: ¥").append(totalRevenue).append("\n");
            sb.append("- 日均营收: ¥").append(String.format("%.2f", avgDaily)).append("\n\n");
        } else {
            sb.append("- 暂无本月营业额数据\n\n");
        }

        return sb.toString();
    }

    /**
     * 查询简要营收数据（只返回汇总，不含每日明细）
     */
    private String queryRevenueDataBrief(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月营收概况】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");

        List<GbAiDailyRevenueEntity> revenues = dailyRevenueMapper.selectList(
                new LambdaQueryWrapper<GbAiDailyRevenueEntity>()
                        .eq(GbAiDailyRevenueEntity::getGbAiDailyRevenueDepartmentId, departmentId)
                        .between(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate, monthStart, monthEnd)
                        .orderByAsc(GbAiDailyRevenueEntity::getGbAiDailyRevenueRecordDate)
        );

        if (!revenues.isEmpty()) {
            BigDecimal totalDineIn = BigDecimal.ZERO, totalTakeout = BigDecimal.ZERO, totalPlatformFee = BigDecimal.ZERO;

            for (GbAiDailyRevenueEntity r : revenues) {
                if (r.getGbAiDailyRevenueDineInRevenue() != null) totalDineIn = totalDineIn.add(r.getGbAiDailyRevenueDineInRevenue());
                if (r.getGbAiDailyRevenueTakeoutRevenue() != null) totalTakeout = totalTakeout.add(r.getGbAiDailyRevenueTakeoutRevenue());
                if (r.getGbAiDailyRevenuePlatformFee() != null) totalPlatformFee = totalPlatformFee.add(r.getGbAiDailyRevenuePlatformFee());
            }

            BigDecimal totalRevenue = totalDineIn.add(totalTakeout).subtract(totalPlatformFee);
            int daysInMonth = LocalDate.now().getDayOfMonth();
            double avgDaily = totalRevenue.doubleValue() / revenues.size();

            sb.append("- 已记录 ").append(revenues.size()).append(" 天（共 ").append(daysInMonth).append(" 天）\n");
            sb.append("- 本月总营收: ¥").append(totalRevenue).append(", 日均: ¥").append(String.format("%.2f", avgDaily)).append("\n\n");
        } else {
            sb.append("- 暂无本月营业额数据\n\n");
        }

        return sb.toString();
    }

    /**
     * 菜品销量聚合：markdown 供 prompt；{@link DishSalesRecital} 供 handoff 强制复述与兜底开场。
     */
    private record DishSalesRecital(int detailRowCount, String topName, String topQtyPlain, String secondName,
                                    String secondQtyPlain) {
        boolean hasSecond() {
            return StrUtil.isNotBlank(secondName) && StrUtil.isNotBlank(secondQtyPlain);
        }

        String mandatoryHandoffBlock() {
            StringBuilder b = new StringBuilder();
            b.append("【你必须写进「给用户正文」前 5 句内的核对结论】（菜名、份数、明细行数与下表一致，可口语衔接但数字不得改）\n");
            b.append("- gb_dep_food_sales 本区间**明细行数**（原始导入行）: ").append(detailRowCount).append("\n");
            b.append("- 按菜汇总后**销量第一**: 「").append(topName).append("」共 ").append(topQtyPlain).append(" 份\n");
            if (hasSecond()) {
                b.append("- **销量第二**: 「").append(secondName).append("」共 ").append(secondQtyPlain).append(" 份\n");
            }
            b.append("- 用户问「哪个最好/销量最高/卖得最多」时：必须明确点名销量第一的**完整菜名**与**份数**；不得写「无法判断」「没有数据」等，除非上文聚合块写明匹配行数为 0。\n");
            b.append("- 若你上一版正文中的「条数/天数」与本次「明细行数」冲突，**以本次为准**，并承认此前口径有误。\n");
            return b.toString();
        }

        String deterministicUserLead(LocalDate monthEnd) {
            StringBuilder b = new StringBuilder();
            b.append("钱多多老师直接看数据：在系统已录入的菜品销量里，截至").append(monthEnd).append("，**「")
                    .append(topName).append("」卖得最多，累计 ").append(topQtyPlain).append(" 份**。");
            if (hasSecond()) {
                b.append("第二是「").append(secondName).append("」").append(secondQtyPlain).append(" 份。");
            }
            b.append("（以上按 gb_dep_food_sales 共 ").append(detailRowCount).append(" 条明细汇总。）");
            return b.toString();
        }
    }

    private record DishSalesBuilt(String markdown, Optional<DishSalesRecital> recital) {}

    /**
     * 按 gb_dep_food_sales 聚合同月菜品销量（份），供热销类问题核对。
     */
    private String queryDishSalesFacts(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        return buildDishSalesFactsWithRecital(departmentId, monthStart, monthEnd).markdown();
    }

    /**
     * 与 {@link com.nongxinle.service.impl.GbDepFoodBusinessInsightServiceImpl#buildInsight} 中每行
     * {@code gb_dep_food.gb_df_food_price} 对齐：本父部门下该菜门店标价（有则用于综合毛利率分母），否则用批发商主档
     * {@code gb_distributer_food.gb_df_food_price}。
     */
    private BigDecimal resolveListPricePerPortionForDepInsight(Integer disId, Integer depFatherId, Integer foodId,
            GbDistributerFoodEntity masterFood) {
        BigDecimal master = GbDepartmentGoodsStockReduceSupport.coerceDecimal(
                masterFood == null ? null : masterFood.getGbDfFoodPrice());
        if (depFatherId == null || foodId == null) {
            return master;
        }
        Map<String, Object> p = new HashMap<>();
        p.put("depFatherId", depFatherId);
        p.put("foodId", foodId);
        List<GbDepFoodEntity> depRows = gbDepFoodService.queryDepFoodByParams(p);
        if (depRows == null || depRows.isEmpty()) {
            return master;
        }
        BigDecimal firstDep = null;
        Set<String> distinct = new HashSet<>();
        for (GbDepFoodEntity r : depRows) {
            BigDecimal lp = GbDepartmentGoodsStockReduceSupport.coerceDecimal(r.getGbDfFoodPrice());
            if (lp != null && lp.compareTo(BigDecimal.ZERO) > 0) {
                distinct.add(lp.setScale(2, RoundingMode.HALF_UP).toPlainString());
                if (firstDep == null) {
                    firstDep = lp;
                }
            }
        }
        if (distinct.size() > 1) {
            log.warn("[AI-DISH-COST-FACTS] listPrice multiple_dep_rows foodId={} depFatherId={} distinctListPrices={} useFirst={}",
                    foodId, depFatherId, distinct,
                    firstDep != null ? firstDep.toPlainString() : "-");
        }
        if (firstDep != null && firstDep.compareTo(BigDecimal.ZERO) > 0
                && master != null && master.compareTo(BigDecimal.ZERO) > 0
                && firstDep.compareTo(master) != 0) {
            log.info("[AI-DISH-COST-FACTS] listPrice dep_vs_master foodId={} depFatherId={} depListPp={} masterListPp={}",
                    foodId, depFatherId, firstDep.toPlainString(), master.toPlainString());
        }
        return firstDep != null && firstDep.compareTo(BigDecimal.ZERO) > 0 ? firstDep : master;
    }

    /**
     * 菜品成本分析事实摘要：调用 {@code GbDishCostAnalysisService} 的月区间结果，提炼前台可用的 Top 异常菜与关键配料。
     * <p>会话 {@code departmentId} 视为<strong>父部门</strong>，与 {@code /gbdepfood/depGetAllFood}、{@code /gbDishCostAnalysis/*}
     * 的 {@code depFatherId} 一致传入 {@link GbDishCostAnalysisService#buildReport}，避免按分销商下全部门汇总导致与页面不一致。</p>
     */
    private String queryDishCostAnalysisFactsForAi(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【菜品成本分析摘要】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        Integer disId = resolveDistributerIdForDepartment(departmentId);
        if (disId == null) {
            sb.append("- 无法解析批发商 ID，未执行菜品成本分析。\n\n");
            return sb.toString();
        }
        int depFatherIdInt = departmentIdAsIntOrSentinel(departmentId);
        if (depFatherIdInt == Integer.MIN_VALUE) {
            sb.append("- 父部门 ID 无效，未执行菜品成本分析。\n\n");
            return sb.toString();
        }
        Integer depFatherId = depFatherIdInt;
        sb.append("- 统计范围：父部门 ID=").append(depFatherId)
                .append("（子门店 gb_department_father_id 指向该父部门；与 depGetAllFood / ingredientAnalysis 的 depFatherId 一致）。\n");
        try {
            log.info("[AI-DISH-COST-FACTS] step=buildReport_request departmentId={} disId={} depFatherId={} startDate={} stopDate={} reportKind=salesDish searchDepId=-1",
                    departmentId, disId, depFatherId, monthStart, monthEnd);
            Map<String, Object> report = gbDishCostAnalysisService.buildReport(
                    monthStart.toString(), monthEnd.toString(), disId, "-1", depFatherId, "salesDish");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("salesDishRows");
            if (rows == null || rows.isEmpty()) {
                log.info("[AI-DISH-COST-FACTS] step=buildReport_done salesDishRows=0_or_null departmentId={} disId={} depFatherId={}",
                        departmentId, disId, depFatherId);
                sb.append("- 本期无可用菜品成本行。\n\n");
                return sb.toString();
            }
            log.info("[AI-DISH-COST-FACTS] step=buildReport_done salesDishRows={} departmentId={} disId={} depFatherId={}",
                    rows.size(), departmentId, disId, depFatherId);
            int logDish = Math.min(rows.size(), 5);
            for (int i = 0; i < logDish; i++) {
                Map<String, Object> r = rows.get(i);
                log.info("[AI-DISH-COST-FACTS] salesDishRow idx={} foodId={} foodName={} soldPortions={} theoryCost={} actualCost={} diffCost={} sortKey={}",
                        i, toStr(r.get("foodId")), toStr(r.get("foodName")), toStr(r.get("soldPortions")),
                        toStr(r.get("theoryCostAmount")), toStr(r.get("actualCostAmount")),
                        toStr(r.get("diffCostAmount")), toStr(r.get("sortKey")));
            }
            sb.append("- 口径：gb_dep_food_sales + gb_dep_food_goods_sales + gb_department_goods_stock_reduce 分摊。\n");
            sb.append("- 异常菜 Top（按 sortKey，最多 5 行）：\n");
            int top = Math.min(rows.size(), 5);
            for (int i = 0; i < top; i++) {
                Map<String, Object> row = rows.get(i);
                sb.append("  ").append(i + 1).append(". ")
                        .append(toStr(row.get("foodName")))
                        .append("，销量 ").append(toStr(row.get("soldPortions"))).append(" 份")
                        .append("，理论成本 ¥").append(toStr(row.get("theoryCostAmount")))
                        .append("，实际成本 ¥").append(toStr(row.get("actualCostAmount")))
                        .append("，差额 ¥").append(toStr(row.get("diffCostAmount")))
                        .append("，偏差权重 ").append(toStr(row.get("sortKey")))
                        .append("（整行金额为 buildReport 内部口径；「每份」与页面一致见下段）\n");
            }
            LinkedHashSet<Integer> topFoodIds = new LinkedHashSet<>();
            for (int i = 0; i < top; i++) {
                Integer fid = parseFoodIdForCost(rows.get(i).get("foodId"));
                if (fid != null) {
                    topFoodIds.add(fid);
                }
            }
            if (!topFoodIds.isEmpty()) {
                try {
                    Map<Integer, Map<String, String>> pp123 = gbDishCostAnalysisService.getDishPerPortionCosts123ByFoodIds(
                            monthStart.toString(), monthEnd.toString(), disId, depFatherId, topFoodIds);
                    sb.append("- 【与看板 dishIngredientDashboard / ingredientAnalysis 一致·单份成本】")
                            .append("（实际/份=含生产+报损+退货等**整单**摊销后每份；谈「每份多花」**必须**用本段。上行整菜「实际成本」若按仅生产单加总，÷份数会与看板每份实际不一致。）\n");
                    for (Integer fid : topFoodIds) {
                        Map<String, String> m = pp123.get(fid);
                        String fn = "";
                        for (int j = 0; j < top; j++) {
                            if (fid.equals(parseFoodIdForCost(rows.get(j).get("foodId")))) {
                                fn = toStr(rows.get(j).get("foodName"));
                                break;
                            }
                        }
                        if (m != null) {
                            log.info("[AI-DISH-COST-FACTS] perPortion123_aligned foodId={} name={} salesPortions={} theoryPp={} actualPp123={} diffPp={}",
                                    fid, fn, m.get("salesPortions"), m.get("theoryCostPerPortion"),
                                    m.get("actualCostPerPortion"), m.get("diffCostPerPortion"));
                            sb.append("  · foodId=").append(fid).append(" ").append(fn)
                                    .append(" 实销").append(m.getOrDefault("salesPortions", "")).append("份")
                                    .append(" 理论/份¥").append(m.getOrDefault("theoryCostPerPortion", "-"))
                                    .append(" 实际/份¥").append(m.getOrDefault("actualCostPerPortion", "-"))
                                    .append("（整单出库全摊 差异/份¥").append(m.getOrDefault("diffCostPerPortion", "-"))
                                    .append("）\n");
                        }
                    }
                    sb.append("- 【父级毛利率标尺】与 depGetAllFood·gbDfBusinessInsight、dishIngredientDashboard·dish 同源；")
                            .append("blendedGrossMarginRateOnListPrice=综合实际%（已算好），**叙述单菜综合实际毛利率时必须原样引用本字段，禁止用心算另编一个%**；")
                            .append("标价分母：优先本父部门下 gb_dep_food 行价（与经营分析 listPrice 列一致），无则主档 gb_distributer_food 价；与 (标价−每份整单实际)÷标价 一致；")
                            .append("T=目标%、F=浮动百分点、带=[T−F,T+F]、level=IN_BAND/ABOVE/BELOW/UNKNOWN。\n");
                    for (Integer fid : topFoodIds) {
                        if (fid == null) {
                            continue;
                        }
                        GbDistributerFoodEntity dishRow = distributerFoodMapper.selectById(fid);
                        if (dishRow == null) {
                            continue;
                        }
                        String fn2 = dishRow.getGbDfFoodName() != null ? dishRow.getGbDfFoodName().trim() : "";
                        Map<String, String> pm = pp123.get(fid);
                        BigDecimal actPp123 = null;
                        if (pm != null && pm.get("actualCostPerPortion") != null) {
                            actPp123 = GbDepartmentGoodsStockReduceSupport.coerceDecimal(pm.get("actualCostPerPortion"));
                        }
                        BigDecimal listPp = resolveListPricePerPortionForDepInsight(disId, depFatherId, fid, dishRow);
                        if (listPp == null) {
                            listPp = BigDecimal.ZERO;
                        }
                        BigDecimal blendedRatio = null;
                        if (listPp.compareTo(BigDecimal.ZERO) > 0 && actPp123 != null) {
                            blendedRatio = listPp.subtract(actPp123).divide(listPp, 8, RoundingMode.HALF_UP);
                        } else if (listPp.signum() == 0 && (actPp123 == null || actPp123.signum() == 0)) {
                            blendedRatio = BigDecimal.ZERO;
                        }
                        GbDistributerFoodEntity directParent = null;
                        Integer pFather = dishRow.getGbDfFoodFatherId();
                        if (pFather != null && pFather != 0) {
                            directParent = distributerFoodMapper.selectById(pFather);
                        }
                        Map<String, Object> gLine = new LinkedHashMap<>();
                        GrossMarginStandardDisplay.putOnMap(gLine, blendedRatio, directParent);
                        String blendedShow = blendedRatio == null ? "-"
                                : GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(blendedRatio) + "%";
                        sb.append("  · foodId=").append(fid).append(" ").append(fn2.isEmpty() ? "-" : fn2)
                                .append(" blendedGrossMarginRateOnListPrice=").append(blendedShow)
                                .append(" grossMarginStandardTarget=").append(toStr(gLine.get("grossMarginStandardTarget")))
                                .append(" grossMarginStandardFloatAbs=").append(toStr(gLine.get("grossMarginStandardFloatAbs")))
                                .append(" grossMarginStandardBandLower=").append(toStr(gLine.get("grossMarginStandardBandLower")))
                                .append(" grossMarginStandardBandUpper=").append(toStr(gLine.get("grossMarginStandardBandUpper")))
                                .append(" grossMarginLevel=").append(toStr(gLine.get("grossMarginLevel")))
                                .append("\n");
                    }
                    sb.append("- 【综合实际毛利率·输出规则】文中写「综合毛利率」「综合实际毛利率」时，**百分数必须与上行每道菜 blendedGrossMarginRateOnListPrice= 原文一致**。")
                            .append("解释原因请用「理论/份、实际/份、差额/份（元）」或配料斤数。")
                            .append("**禁止**用「(任意标价−实际/份)÷标价」现场重算：部门门店标价(gb_dep_food)可能与主档不同，心算易与系统行矛盾。")
                            .append("公式与事实块一致：((上表标价/份)−(每份整单实际))÷(上表标价/份)。例：部门标价¥40、实际/份¥24 → **40%**；若误用主档¥30 则会错成 20%。")
                            .append("**对终端用户/老板**：禁止在可见回复中出现 type1、1+2+3、outbound 等代码词；用「生产领用量」「每份整单实际」等口语，键名与英文仅供你内部对齐字段。\n");
                } catch (Exception ex) {
                    log.warn("[AI-DISH-COST-FACTS] perPortion123_aligned_failed departmentId={} err={}",
                            departmentId, ex.getMessage());
                }
            }
            int detailDishCount = Math.min(3, rows.size());
            if (detailDishCount > 0) {
                sb.append("- 以下为异常榜**前 3 名**（不足 3 道则全列）的**逐料明细**；每道单独一节。下列配料行均为**重量**（与系统主档一致，常见为斤），**不是份数**；不得把「理论总用量」说成「每份配方」。\n");
                sb.append("  列含义：单份配方=每 1 份菜的配方用量；理论总用量=实销份数×单份配方；列「**生产领用量**」= 仅**正常生产单**按规则摊到本菜本料的重量（内部字段 outboundAllocatedQty / 看板 actualProduceUsage 同级）；**不要**在回复里写 type1。")
                        .append(" 看板若列「**整单实际用量**」会含报损/退货等，一般**大于**上列生产领用；两句不要混成一个数。内部字段名仅供你对齐，**对用户用中文口语**。\n");
                sb.append("  可支撑份数=上列**生产领用**重量÷**单份配方**（例：上海青 3÷0.1=30）；**禁止**用÷理论总用量（误算 3÷0.3=10）。\n");
                sb.append("  重量差(理−领)=理论总用量−**生产领用量**；成本差(元)按生产出库均价口径。对用户说明「和配方比多领/少领了多少斤、差多少钱」。\n");
                sb.append("- 仅允许引用以下各道「关键配料」表中的**商品名**；表外原料（未出现）一律不得编造。\n");
                for (int r = 0; r < detailDishCount; r++) {
                    appendDishCostIngredientDetailSection(sb, rows.get(r), r + 1);
                }
            }
            for (int r = 0; r < detailDishCount; r++) {
                String h = toStr(rows.get(r).get("hint"));
                if (StrUtil.isNotBlank(h) && !"-".equals(h)) {
                    sb.append("- 第 ").append(r + 1).append(" 名系统提示(hint)：").append(h).append("\n");
                }
            }
            if (rows.size() > 3) {
                sb.append("- **第 4 名及之后**：本对话事实块**仅**包含异常榜**前 3 道**的逐料行；上表「异常菜 Top（最多 5 行）」中第 4、5 名仅保留整菜汇总。若需看更多菜品的**单菜配料/出库/看板**（与 `dishIngredientDashboard` / 前台「**菜品分析**」页数据同源），请用户到**菜品分析**按菜名或 foodId 打开单菜看板继续查询，勿编造未注入的配料。\n");
            }
            sb.append("- 回答“哪道菜成本异常”时，优先引用本块**前 3 名**菜名、差额与各自关键配料；第 4 名及之后未带配料明细的，**必须**据上节引导用户到「菜品分析」查。\n\n");
        } catch (Exception e) {
            log.warn("菜品成本摘要注入失败: departmentId={}, disId={}, depFatherId={}, err={}",
                    departmentId, disId, depFatherId, e.getMessage(), e);
            sb.append("- 菜品成本分析暂不可用（").append(e.getMessage()).append("）。\n\n");
        }
        return sb.toString();
    }

    /**
     * 将一道菜的 ingredientRows 按 |成本差| 降序（最多 8 行）写入 AI 事实块；与仅 Top1 时字段口径一致。
     */
    private void appendDishCostIngredientDetailSection(StringBuilder sb, Map<String, Object> dishRow, int oneBasedRank) {
        if (dishRow == null) {
            return;
        }
        String dName = toStr(dishRow.get("foodName"));
        String dSold = toStr(dishRow.get("soldPortions"));
        String dFid = toStr(dishRow.get("foodId"));
        sb.append("- 第 ").append(oneBasedRank).append(" 名「").append(dName).append("」 foodId=").append(dFid)
                .append(" 本期实销 ").append(dSold).append(" 份。\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ingredientRows = (List<Map<String, Object>>) dishRow.get("ingredientRows");
        if (ingredientRows == null || ingredientRows.isEmpty()) {
            sb.append("  - （本道本期无可用 ingredientRows/配料行，不列举逐料。）\n");
            return;
        }
        log.info("[AI-DISH-COST-FACTS] top{}_dish foodId={} foodName={} soldPortions={} ingredientRows_count={}",
                oneBasedRank, dFid, dName, dSold, ingredientRows.size());
        for (int j = 0; j < ingredientRows.size(); j++) {
            log.info("[AI-DISH-COST-FACTS] top{}_ingredient_recipeOrder idx={} {}",
                    oneBasedRank, j, formatIngredientRowForLog(ingredientRows.get(j)));
        }
        List<Map<String, Object>> ingSorted = new ArrayList<>(ingredientRows);
        ingSorted.sort(Comparator.comparing(
                (Map<String, Object> ir) -> parseBigDecimalLoose(ir.get("recipeSalesVsOutboundCostDiff")).abs(),
                Comparator.reverseOrder()));
        for (int j = 0; j < ingSorted.size(); j++) {
            log.info("[AI-DISH-COST-FACTS] top{}_ingredient_byAbsCostDiff idx={} {}",
                    oneBasedRank, j, formatIngredientRowForLog(ingSorted.get(j)));
        }
        sb.append("  关键配料（按 |成本差(元)| 降序，最多 8 条）：\n");
        int limit = Math.min(ingSorted.size(), 8);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> ir = ingSorted.get(i);
            sb.append("  - ").append(toStr(ir.get("goodsName")))
                    .append("｜单份配方 ").append(toStr(ir.get("recipeUnitPerDish")))
                    .append("｜理论总用量 ").append(toStr(ir.get("theoryOutboundQtyByRecipe")))
                    .append("｜生产领用量 ").append(toStr(ir.get("outboundAllocatedQty")))
                    .append("｜重量差(理−摊) ").append(toStr(ir.get("recipeTheoryQtyVsOutboundAllocDiff")))
                    .append("｜成本差(元) ").append(toStr(ir.get("recipeSalesVsOutboundCostDiff")))
                    .append("｜销售子表用量 ").append(toStr(ir.get("theoryQtyFromSales")))
                    .append("｜可支撑份数 ").append(toStr(ir.get("supportedPortionsThisGood"))).append("\n");
        }
    }

    /**
     * 单行日志用：配料关键数字（与注入模型、页面 ingredientRows 同源字段名）。
     */
    private static String formatIngredientRowForLog(Map<String, Object> ir) {
        if (ir == null) {
            return "(null)";
        }
        return "disGoodsId=" + toStr(ir.get("disGoodsId"))
                + " goodsName=" + toStr(ir.get("goodsName"))
                + " recipeUnitPerDish=" + toStr(ir.get("recipeUnitPerDish"))
                + " theoryOutboundQtyByRecipe=" + toStr(ir.get("theoryOutboundQtyByRecipe"))
                + " theoryQtyFromSales=" + toStr(ir.get("theoryQtyFromSales"))
                + " outboundAllocatedQty=" + toStr(ir.get("outboundAllocatedQty"))
                + " recipeTheoryQtyVsOutboundAllocDiff=" + toStr(ir.get("recipeTheoryQtyVsOutboundAllocDiff"))
                + " recipeSalesVsOutboundCostDiff=" + toStr(ir.get("recipeSalesVsOutboundCostDiff"))
                + " supportedPortionsThisGood=" + toStr(ir.get("supportedPortionsThisGood"));
    }

    private static String toStr(Object v) {
        if (v == null) {
            return "-";
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? "-" : s;
    }

    /** 解析配料行中的数字字符串，失败按 0（仅用于排序）。 */
    private static BigDecimal parseBigDecimalLoose(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "-".equals(s)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static Integer parseFoodIdForCost(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Integer) {
            return (Integer) v;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                return null;
            }
            return Integer.valueOf(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private DishSalesBuilt buildDishSalesFactsWithRecital(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月菜品销量聚合】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");
        sb.append("- 数据来源: gb_dep_food_sales.gb_dfs_amount 按份累加；部门匹配 gb_dfs_dep_id 或 gb_dfs_dep_father_id\n");
        int dep = departmentIdAsIntOrSentinel(departmentId);
        if (dep == Integer.MIN_VALUE) {
            sb.append("- 部门 ID 无效，未查询。\n");
            return new DishSalesBuilt(sb.toString(), Optional.empty());
        }
        List<GbDepFoodSalesEntity> rows = depFoodSalesMapper.selectList(
                new LambdaQueryWrapper<GbDepFoodSalesEntity>()
                        .and(w -> w.eq(GbDepFoodSalesEntity::getGbDfsDepId, dep)
                                .or()
                                .eq(GbDepFoodSalesEntity::getGbDfsDepFatherId, dep))
                        .between(GbDepFoodSalesEntity::getGbDfsFullDate, monthStart.toString(), monthEnd.toString())
        );
        sb.append("- 匹配行数: ").append(rows.size()).append("\n");
        if (rows.isEmpty()) {
            sb.append("- 暂无菜品销量明细（可能未导入或未写入 gb_dep_food_sales）。\n");
            return new DishSalesBuilt(sb.toString(), Optional.empty());
        }
        Map<Integer, BigDecimal> qtyByFood = new HashMap<>();
        for (GbDepFoodSalesEntity r : rows) {
            if (r.getGbDfsFoodId() == null || StrUtil.isBlank(r.getGbDfsAmount())) {
                continue;
            }
            try {
                BigDecimal q = new BigDecimal(r.getGbDfsAmount().trim());
                if (q.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                qtyByFood.merge(r.getGbDfsFoodId(), q, BigDecimal::add);
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (qtyByFood.isEmpty()) {
            sb.append("- 有记录但可解析的有效销量为 0。\n");
            return new DishSalesBuilt(sb.toString(), Optional.empty());
        }
        List<Map.Entry<Integer, BigDecimal>> sorted = new ArrayList<>(qtyByFood.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        sb.append("- Top 菜品（按份，最多 15）：\n");
        int n = 0;
        for (Map.Entry<Integer, BigDecimal> e : sorted) {
            if (n >= 15) {
                break;
            }
            String name = resolveFoodNameForAi(e.getKey());
            sb.append("  - ").append(name).append("（菜品 ID ").append(e.getKey()).append("）：共 ")
                    .append(e.getValue().stripTrailingZeros().toPlainString()).append(" 份\n");
            n++;
        }
        Map.Entry<Integer, BigDecimal> top = sorted.get(0);
        String topName = resolveFoodNameForAi(top.getKey());
        String topQty = top.getValue().stripTrailingZeros().toPlainString();
        String secondName = "";
        String secondQty = "";
        if (sorted.size() > 1) {
            Map.Entry<Integer, BigDecimal> s = sorted.get(1);
            secondName = resolveFoodNameForAi(s.getKey());
            secondQty = s.getValue().stripTrailingZeros().toPlainString();
        }
        DishSalesRecital recital = new DishSalesRecital(rows.size(), topName, topQty, secondName, secondQty);
        return new DishSalesBuilt(sb.toString(), Optional.of(recital));
    }

    private String resolveFoodNameForAi(Integer foodId) {
        GbDistributerFoodEntity f = distributerFoodMapper.selectById(foodId);
        if (f != null && StrUtil.isNotBlank(f.getGbDfFoodName())) {
            return f.getGbDfFoodName();
        }
        return "未命名菜品";
    }

    // ========== 工具方法 ==========

    /**
     * 加载所有 Skill 文件的摘要（用于Skill选择）
     */
    private String loadAllSkillsBrief() {
        StringBuilder sb = new StringBuilder();
        for (String filename : SKILL_FILES) {
            String content = loadSkillFile(filename);
            if (StrUtil.isNotEmpty(content)) {
                // 提取skill名字，去掉.md后缀作为标题
                String skillName = filename.replace(".md", "");
                // 取前200字作为摘要
                String summary = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                sb.append("【").append(skillName).append("】\n");
                sb.append(summary).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 加载所有 Skill 文件内容
     */
    private String loadAllSkills() {
        StringBuilder sb = new StringBuilder();
        for (String filename : SKILL_FILES) {
            String content = loadSkillFile(filename);
            if (StrUtil.isNotEmpty(content)) {
                sb.append("【").append(filename).append("】\n");
                sb.append(content).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 加载选中的 Skill 文件内容
     */
    private String loadSelectedSkills(String selectedSkillsStr) {
        if (StrUtil.isEmpty(selectedSkillsStr) || "none".equalsIgnoreCase(selectedSkillsStr.trim())) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String[] skillNames = selectedSkillsStr.split("[,，]");

        for (String name : skillNames) {
            String trimmed = name.trim();
            if (StrUtil.isNotEmpty(trimmed)) {
                // 确保有 .md 后缀
                if (!trimmed.endsWith(".md")) {
                    trimmed = trimmed + ".md";
                }
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (!lower.matches("ai-skill-[\\w-]+\\.md")) {
                    log.warn("忽略非法 skill 文件名: {}", trimmed);
                    continue;
                }
                String content = loadSkillFile(trimmed);
                if (StrUtil.isNotEmpty(content)) {
                    sb.append("【").append(trimmed).append("】\n");
                    sb.append(content).append("\n\n");
                    log.info("加载 Skill 文件: {}", trimmed);
                } else {
                    log.warn("Skill 文件不存在或为空: {}", trimmed);
                }
            }
        }

        return sb.toString();
    }

    /**
     * 加载单个 Skill 文件
     */
    private String loadSkillFile(String filename) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                log.warn("Skill 文件未找到: {}", filename);
            }
        } catch (IOException e) {
            log.error("加载 Skill 文件失败: {} - {}", filename, e.getMessage());
        }
        return "";
    }

    /**
     * 保存消息
     */
    private GbAiMessageEntity saveMessage(Long conversationId, Long userId, Integer messageType, String role, String content) {
        GbAiMessageEntity msg = new GbAiMessageEntity();
        msg.setGbAiMessageConversationId(conversationId);
        msg.setGbAiMessageUserId(userId);
        msg.setGbAiMessageType(messageType != null ? messageType : 0);
        msg.setGbAiMessageRole(role);
        msg.setGbAiMessageContent(content);
        msg.setGbAiMessageTokenCount(0);
        msg.setGbAiMessageMemoryExtracted(0);
        msg.setGbAiMessageCreateTime(new Date());

        messageMapper.insert(msg);
        log.debug("消息保存成功 - id={}, role={}, content长度={}", msg.getGbAiMessageId(), role, content.length());

        return msg;
    }

    /**
     * 从 AI 回复中提取用户数据并保存到数据库
     */
    private void extractUserDataFromReply(String reply, Long departmentId) {
        if (StrUtil.isBlank(reply)) return;

        try {
            // 尝试从 JSON 格式中提取数据
            String jsonStr = null;
            if (reply.contains("```json")) {
                int jsonStart = reply.indexOf("```json") + 7;
                int jsonEnd = reply.indexOf("```", jsonStart);
                if (jsonEnd > jsonStart) {
                    jsonStr = reply.substring(jsonStart, jsonEnd).trim();
                }
            } else if (reply.contains("{")) {
                // 尝试直接解析JSON（如果没有标准markdown格式）
                int jsonStart = reply.indexOf("{");
                int jsonEnd = reply.lastIndexOf("}");
                if (jsonEnd > jsonStart) {
                    jsonStr = reply.substring(jsonStart, jsonEnd + 1).trim();
                }
            }

            if (jsonStr != null) {
                log.info("提取到 JSON 数据: {}", jsonStr);

                JSONObject json = JSONUtil.parseObj(jsonStr);
                boolean hasData = json.getBool("hasData", false);
                boolean needsConfirm = json.getBool("needsConfirm", false);

                // 如果hasData=false或needsConfirm=true，不自动保存
                if (!hasData) {
                    log.debug("没有检测到有效数据，跳过保存");
                    return;
                }

                if (needsConfirm) {
                    // 需要确认的数据，不自动保存
                    JSONArray updates = json.getJSONArray("updates");
                    if (updates != null && updates.size() > 0) {
                        log.info("检测到 {} 条数据需要确认，暂不保存", updates.size());
                    }
                    return;
                }

                // needsConfirm=false，且hasData=true，执行数据保存
                log.info("数据验证通过，开始保存...");
                saveExtractedData(json, departmentId);
            } else {
                log.debug("AI回复中未找到JSON数据格式，跳过数据提取");
            }
        } catch (Exception e) {
            log.warn("数据提取失败: {}", e.getMessage());
        }
    }

    /**
     * 保存提取的数据到数据库
     */
    private void saveExtractedData(JSONObject json, Long departmentId) {
        try {
            JSONArray updates = json.getJSONArray("updates");
            if (updates == null || updates.isEmpty()) {
                log.debug("没有需要更新的数据");
                return;
            }

            log.info("开始保存 {} 条数据到数据库", updates.size());

            // 查询或创建餐厅画像
            GbAiRestaurantProfileEntity profile = restaurantProfileMapper.selectOne(
                    new LambdaQueryWrapper<GbAiRestaurantProfileEntity>()
                            .eq(GbAiRestaurantProfileEntity::getGbAiRestaurantProfileDepartmentId, departmentId)
            );

            if (profile == null) {
                profile = new GbAiRestaurantProfileEntity();
                profile.setGbAiRestaurantProfileDepartmentId(departmentId);
                GbDepartmentEntity dep = departmentMapper.selectById(departmentId);
                if (dep != null && dep.getGbDepartmentDisId() != null) {
                    profile.setGbAiRestaurantProfileDistributerId(dep.getGbDepartmentDisId().longValue());
                }
                log.info("创建新的餐厅画像记录");
            }

            // 根据字段名更新对应属性
            for (int i = 0; i < updates.size(); i++) {
                JSONObject update = updates.getJSONObject(i);
                String field = update.getStr("field");
                Object value = update.get("value");

                if (field == null || value == null) continue;

                BigDecimal numValue = null;
                if (value instanceof Number) {
                    numValue = new BigDecimal(value.toString());
                } else {
                    try {
                        numValue = new BigDecimal(value.toString());
                    } catch (NumberFormatException e) {
                        log.warn("无法转换字段 {} 的值: {}", field, value);
                        continue;
                    }
                }

                switch (field) {
                    case "gb_ai_restaurant_profile_daily_revenue":
                        profile.setGbAiRestaurantProfileDailyRevenue(numValue);
                        log.info("更新 日均营收 = {}", numValue);
                        break;
                    case "gb_ai_restaurant_profile_rent_monthly":
                        profile.setGbAiRestaurantProfileRentMonthly(numValue);
                        log.info("更新 月租金 = {}", numValue);
                        break;
                    case "gb_ai_restaurant_profile_monthly_wage":
                        profile.setGbAiRestaurantProfileMonthlyWage(numValue);
                        log.info("更新 月工资 = {}", numValue);
                        break;
                    case "gb_ai_restaurant_profile_monthly_fixed_cost":
                        profile.setGbAiRestaurantProfileMonthlyFixedCost(numValue);
                        log.info("更新 月固定成本 = {}", numValue);
                        break;
                    default:
                        log.warn("未知字段: {}", field);
                }
            }

            gbAiRestaurantProfileService.saveOrUpdateProfile(profile);
            log.info("餐厅画像保存成功");

        } catch (Exception e) {
            log.error("数据保存失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 主回复：剥离 skill_handoff 后算用户可见正文；若模型声明移交则再拉补充事实并调用第二轮修订。
     */
    private String assistantUserVisibleAfterOptionalHandoff(String rawMain, String userMessage, Long departmentId) {
        String cleanedMain = SkillHandoffParser.stripAllSkillHandoffFences(rawMain);
        Optional<SkillHandoffPayload> ho = SkillHandoffParser.parseLastSkillHandoff(rawMain);
        if (ho.isEmpty()) {
            return stripAssistantUserVisibleTail(cleanedMain);
        }
        log.info("[AI-CHAT][handoff] toSkill={} reasonPreview={}", ho.get().toSkill(),
                abbreviateForLog(ho.get().reason(), 200));
        String firstVisible = stripAssistantUserVisibleTail(cleanedMain);
        HandoffFactPayload factPayload = buildHandoffFactPayload(ho.get(), departmentId);
        String facts = factPayload.markdown();
        List<Map<String, String>> revisionMessages = buildHandoffRevisionMessages(
                userMessage, firstVisible, facts, ho.get(), factPayload.dishRecital());
        String raw2 = callDeepSeekApi(revisionMessages, "handoff-revision");
        if (StrUtil.isBlank(raw2) || raw2.contains("AI 服务暂时不可用") || raw2.contains("AI 服务出现异常")) {
            return firstVisible;
        }
        String cleaned2 = SkillHandoffParser.stripAllSkillHandoffFences(raw2);
        extractUserDataFromReply(cleaned2, departmentId);
        String visible2 = stripAssistantUserVisibleTail(cleaned2);
        return enforceDishSalesHandoffVisible(visible2, ho.get().toSkill(), factPayload.dishRecital());
    }

    private record HandoffFactPayload(String markdown, Optional<DishSalesRecital> dishRecital) {}

    private HandoffFactPayload buildHandoffFactPayload(SkillHandoffPayload ho, Long departmentId) {
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        LocalDate monthEnd = now;
        return switch (ho.toSkill()) {
            case "cost" -> new HandoffFactPayload(buildHandoffCostFacts(departmentId, monthStart, monthEnd), Optional.empty());
            case "revenue" -> new HandoffFactPayload(queryRevenueData(departmentId, monthStart, monthEnd), Optional.empty());
            case "data_extractor" -> new HandoffFactPayload(queryRevenueDataBrief(departmentId, monthStart, monthEnd), Optional.empty());
            case "dish_sales" -> {
                DishSalesBuilt b = buildDishSalesFactsWithRecital(departmentId, monthStart, monthEnd);
                String md = b.markdown() + "\n" + queryRevenueDataBrief(departmentId, monthStart, monthEnd);
                yield new HandoffFactPayload(md, b.recital());
            }
            case "dish_cost" -> new HandoffFactPayload(
                    queryDishSalesFacts(departmentId, monthStart, monthEnd)
                            + "\n" + queryDishCostAnalysisFactsForAi(departmentId, monthStart, monthEnd),
                    Optional.empty());
            case "procurement" -> new HandoffFactPayload(
                    queryPurchaseGoodsFactsForAi(departmentId, monthStart, monthEnd)
                            + querySelfPurchaseGoodsFactsForAi(departmentId, monthStart, monthEnd)
                            + querySupplierUnsettledFactsForAi(departmentId),
                    Optional.empty());
            case "profit_pilot" -> new HandoffFactPayload(
                    queryRevenueData(departmentId, monthStart, monthEnd)
                            + queryCostData(departmentId, monthStart, monthEnd, "overview", "")
                            + queryDishSalesFacts(departmentId, monthStart, monthEnd),
                    Optional.empty());
            default -> new HandoffFactPayload(queryRevenueDataBrief(departmentId, monthStart, monthEnd), Optional.empty());
        };
    }

    /**
     * handoff 修订后：若仍不写 Top 菜名与份数，则前置服务端根据库算好的开场句，保证用户界面可见。
     */
    private String enforceDishSalesHandoffVisible(String visible, String hoToSkill, Optional<DishSalesRecital> recital) {
        if (!"dish_sales".equals(hoToSkill) || recital.isEmpty() || StrUtil.isBlank(visible)) {
            return visible;
        }
        DishSalesRecital r = recital.get();
        if (visible.contains(r.topName()) && visible.contains(r.topQtyPlain())) {
            return visible;
        }
        log.info("[AI-CHAT][handoff] dish_sales post_enforcement=prepend_deterministic_lead topName={}", r.topName());
        return r.deterministicUserLead(LocalDate.now()) + "\n\n" + visible.trim();
    }

    private List<Map<String, String>> buildHandoffRevisionMessages(String userMessage, String firstAssistantVisible,
                                                                   String supplementFacts, SkillHandoffPayload ho,
                                                                   Optional<DishSalesRecital> dishRecital) {
        StringBuilder sys = new StringBuilder();
        sys.append("【身份】你是钱多多老师，与主对话人设一致。\n");
        sys.append("【技能移交】以下为服务端根据 toSkill 注入的补充事实，用于修正你上一版可能的信息缺口。\n");
        sys.append("- toSkill=").append(ho.toSkill()).append("\n");
        sys.append("- reason=").append(ho.reason()).append("\n\n");
        sys.append(supplementFacts);
        dishRecital.ifPresent(r -> sys.append("\n").append(r.mandatoryHandoffBlock()));
        if ("dish_cost".equals(ho.toSkill())) {
            sys.append("\n\n【单菜综合实际毛利率·硬性规则】与父级 T±F、`grossMarginLevel` 对拍时，**综合实际毛利率百分数**必须与原样引用上文中 **blendedGrossMarginRateOnListPrice**（或【父级毛利率标尺】行内 `blendedGrossMarginRateOnListPrice=`）一致。")
                    .append("**禁止**用任意标价自行心算；部门 gb_dep_food 标价可能与主档不同。解释成本用元/斤，不另造毛利。易错：(主档¥30、实际¥24) 心算 20%，但部门标价¥40 时系统为 40%。\n");
        }
        sys.append("\n\n【要求】结合补充事实，对用户原话给出**修订后的完整可见回复**；若补充与先前冲突，以补充为准。\n");
        sys.append("本轮是「事实修订」：禁止复述第一版中的错误条数/错误结论；**必须先落实上文核对结论（若有）中的菜名与份数**，再酌情一句说明数据覆盖范围。\n");
        sys.append("禁止再次输出 type 为 skill_handoff 的 ```json 代码块。开头仍须以「钱多多老师」起语。\n");
        List<Map<String, String>> m = new ArrayList<>();
        m.add(Map.of("role", "system", "content", sys.toString()));
        m.add(Map.of("role", "user", "content", "用户原话：\n" + userMessage + "\n\n你刚才已发给用户的版本（可能有缺口）：\n" + firstAssistantVisible));
        return m;
    }

    private String buildHandoffCostFacts(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        GbAiRestaurantProfileEntity profile = restaurantProfileMapper.selectOne(
                new LambdaQueryWrapper<GbAiRestaurantProfileEntity>()
                        .eq(GbAiRestaurantProfileEntity::getGbAiRestaurantProfileDepartmentId, departmentId));
        if (!hasAllBasicFixedCosts(profile)) {
            return "【移交补充】门店档案中月租金/月工资/月其它固定成本未齐备，无法拉取完整成本与库存对照流水；请提示用户补齐三项后再做细拆。\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(queryRevenueData(departmentId, monthStart, monthEnd));
        sb.append(queryCostData(departmentId, monthStart, monthEnd, null, ""));
        return sb.toString();
    }

    // ========== DeepSeek API 调用 ==========

    /**
     * 去掉仅用于服务端解析、不宜展示给终端用户的内容：「数据完整性」声明块、末尾 ```json 数据提取块。
     * 注意：{@link #extractUserDataFromReply} 必须在截断前使用原始全文调用。
     */
    private String stripAssistantUserVisibleTail(String raw) {
        if (StrUtil.isBlank(raw)) {
            return raw;
        }
        String s = SkillHandoffParser.stripAllSkillHandoffFences(raw.trim());
        int cut = -1;
        int i1 = s.indexOf("【数据完整性】");
        int i2 = s.indexOf("【数据完整性检查】");
        if (i1 >= 0) {
            cut = i1;
        }
        if (i2 >= 0 && (cut < 0 || i2 < cut)) {
            cut = i2;
        }
        if (cut >= 0) {
            s = s.substring(0, cut).trim();
        }
        while (true) {
            int j = s.lastIndexOf("```json");
            if (j < 0) {
                break;
            }
            int k = s.indexOf("```", j + 7);
            if (k > j) {
                s = s.substring(0, j).trim();
            } else {
                break;
            }
        }
        return s.trim();
    }

    private static String abbreviateForLog(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...[截断,总长度=" + text.length() + "]";
    }

    /**
     * 打印发往 DeepSeek 的 messages（按条预览，便于对照业务流程）。
     */
    private void logDeepSeekRequestPayload(String phase, List<Map<String, String>> messages) {
        int n = messages == null ? 0 : messages.size();
        log.info("[AI-CHAT][DeepSeek-REQ] phase={} model={} messageCount={}", phase, model, n);
        if (messages == null) {
            return;
        }
        int idx = 0;
        for (Map<String, String> m : messages) {
            idx++;
            String role = m.get("role");
            String content = m.get("content");
            int len = content != null ? content.length() : 0;
            int cap = "system".equals(role) ? LOG_PREVIEW_SYSTEM_CHARS : LOG_PREVIEW_MESSAGE_CHARS;
            log.info("[AI-CHAT][DeepSeek-REQ] phase={} part={}/{} role={} chars={} preview={}",
                    phase, idx, n, role, len, abbreviateForLog(content, cap));
        }
    }

    private void logDeepSeekResponsePayload(String phase, String content) {
        int len = content != null ? content.length() : 0;
        log.info("[AI-CHAT][DeepSeek-RES] phase={} chars={} preview={}",
                phase, len, abbreviateForLog(content, LOG_PREVIEW_RESPONSE_CHARS));
    }

    /**
     * 调用 DeepSeek API（非流式）
     *
     * @param phase 监控用阶段标识：skill-selection | main-reply | conversation-summary 等
     */
    private String callDeepSeekApi(List<Map<String, String>> messages, String phase) {
        log.info("[AI-CHAT][DeepSeek] step=http_begin phase={}", phase);
        logDeepSeekRequestPayload(phase, messages);

        try {
            JSONObject body = new JSONObject();
            body.set("model", model);
            body.set("messages", messages);
            body.set("max_tokens", maxTokens);
            body.set("temperature", temperature);
            body.set("stream", false);

            log.debug("[AI-CHAT][DeepSeek] phase={} requestBodyJsonChars={}", phase, body.toString().length());

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                    .build();

            log.info("[AI-CHAT][DeepSeek] phase={} postUrl={}", phase, baseUrl + "/chat/completions");

            try (Response response = httpClient.newCall(request).execute()) {
                log.info("[AI-CHAT][DeepSeek] phase={} httpStatus={}", phase, response.code());

                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    log.error("[AI-CHAT][DeepSeek] phase={} error status={} bodyPreview={}",
                            phase, response.code(), abbreviateForLog(errBody, 800));
                    return "抱歉，AI 服务暂时不可用。请稍后重试。";
                }

                String responseBody = response.body().string();
                log.debug("[AI-CHAT][DeepSeek] phase={} rawResponseBodyChars={}", phase, responseBody.length());

                JSONObject json = JSONUtil.parseObj(responseBody);
                JSONArray choices = json.getJSONArray("choices");

                if (choices != null && !choices.isEmpty()) {
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    String content = message.getStr("content");
                    logDeepSeekResponsePayload(phase, content);
                    log.info("[AI-CHAT][DeepSeek] step=http_end_ok phase={}", phase);
                    return content != null ? content : "AI 未返回有效内容。";
                }

                log.warn("[AI-CHAT][DeepSeek] phase={} choices empty or null", phase);
                return "AI 未返回有效回复。";
            }

        } catch (Exception e) {
            log.error("[AI-CHAT][DeepSeek] phase={} exception: {}", phase, e.getMessage(), e);
            return "抱歉，AI 服务出现异常。请稍后重试。";
        }
    }

    /**
     * 调用 DeepSeek API（流式 SSE）
     */
    private void callDeepSeekSSE(List<Map<String, String>> messages, SseEmitter emitter,
                                 Long conversationId, Long userId, GbAiConversationEntity conv, Long departmentId,
                                 String userMessage) {
        new Thread(() -> {
            StringBuilder fullReply = new StringBuilder();

            try {
                log.info("[AI-CHAT][DeepSeek] trace=sse step=http_begin phase=sse-main-reply conversationId={}", conversationId);
                logDeepSeekRequestPayload("sse-main-reply", messages);

                JSONObject body = new JSONObject();
                body.set("model", model);
                body.set("messages", messages);
                body.set("max_tokens", maxTokens);
                body.set("temperature", temperature);
                body.set("stream", true);

                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "text/event-stream")
                        .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    log.info("[AI-CHAT][DeepSeek] trace=sse phase=sse-main-reply conversationId={} httpStatus={}",
                            conversationId, response.code());
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "";
                        log.error("[AI-CHAT][DeepSeek] trace=sse phase=sse-main-reply error status={} bodyPreview={}",
                                response.code(), abbreviateForLog(errBody, 800));
                        emitter.send(SseEmitter.event().name("error").data("AI 服务暂时不可用"));
                        emitter.complete();
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();

                            if ("[DONE]".equals(data)) {
                                log.info("[AI-CHAT][DeepSeek] trace=sse phase=sse-main-reply conversationId={} stream_token=[DONE]",
                                        conversationId);
                                break;
                            }

                            try {
                                JSONObject chunk = JSONUtil.parseObj(data);
                                JSONArray choices = chunk.getJSONArray("choices");

                                if (choices != null && !choices.isEmpty()) {
                                    JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                    if (delta != null) {
                                        String content = delta.getStr("content", "");
                                        if (StrUtil.isNotEmpty(content)) {
                                            fullReply.append(content);
                                            // 不在流式过程中下发正文，避免把末尾 JSON/数据完整性块发给客户端；结束后统一发剥离版
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("解析 SSE 数据块异常: {}", e.getMessage());
                            }
                        }
                    }

                    // 完整回复：先解析 JSON（原始），再剥离后一次下发 SSE 并落库
                    String replyRaw = fullReply.toString();
                    log.info("[AI-CHAT][DeepSeek] trace=sse phase=sse-main-reply conversationId={} aggregatedChars={}",
                            conversationId, replyRaw.length());
                    logDeepSeekResponsePayload("sse-main-reply", replyRaw);

                    String forProfile = SkillHandoffParser.stripAllSkillHandoffFences(replyRaw);
                    extractUserDataFromReply(forProfile, departmentId);
                    String replyVisible = assistantUserVisibleAfterOptionalHandoff(replyRaw, userMessage, departmentId);
                    if (StrUtil.isNotEmpty(replyVisible)) {
                        emitter.send(SseEmitter.event().name("message").data(replyVisible));
                    }

                    if (StrUtil.isNotEmpty(replyVisible)) {
                        saveMessage(conversationId, userId, conv.getGbAiConversationType(), "assistant", replyVisible);
                        conv.setGbAiConversationUpdateTime(new Date());

                        if ("新对话".equals(conv.getGbAiConversationTitle()) && replyVisible.length() > 5) {
                            conv.setGbAiConversationTitle(replyVisible.substring(0, Math.min(replyVisible.length(), 30)) + "...");
                        }
                        conversationMapper.updateById(conv);
                    }

                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();

                } catch (Exception e) {
                    log.error("SSE 处理异常: {}", e.getMessage(), e);
                    try {
                        emitter.send(SseEmitter.event().name("error").data("流式响应异常"));
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {}
                }

            } catch (Exception e) {
                log.error("SSE 线程异常: {}", e.getMessage(), e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        }, "deepseek-sse-" + conversationId).start();
    }
}