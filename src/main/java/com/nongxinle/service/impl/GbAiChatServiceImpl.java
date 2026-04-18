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
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.mapper.GbAiRestaurantProfileMapper;
import com.nongxinle.mapper.GbAiDailyRevenueMapper;
import com.nongxinle.mapper.GbDepartmentGoodsStockReduceMapper;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.service.GbAiChatService;
import com.nongxinle.service.GbAiMemoryService;
import com.nongxinle.service.GbAiRestaurantProfileService;
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
    private final GbDepartmentGoodsStockReduceMapper stockReduceMapper;

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
            "ai-skill-data-extractor.md"
    );

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
        log.info("========== 对话开始 ==========");
        log.info("conversationId={}, userId={}, message={}", conversationId, userId, userMessage);

        try {
            // 1. 获取对话信息
            GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
            if (conv == null) {
                log.error("对话不存在: conversationId={}", conversationId);
                throw new RuntimeException("对话不存在");
            }

            Long departmentId = conv.getGbAiConversationDepartmentId();
            Integer conversationType = conv.getGbAiConversationType();
            log.info("部门ID={}, 对话类型={}", departmentId, conversationType);

            // 2. 保存用户消息
            log.info("保存用户消息...");
            saveMessage(conversationId, userId, conversationType, "user", userMessage);

            // 3. 构建消息列表
            log.info("构建消息列表...");
            List<Map<String, String>> messages = buildMessages(conv, userMessage);
            log.info("消息列表大小: {} 条", messages.size());

            // 4. 调用 DeepSeek API 获取回复
            log.info("调用 DeepSeek API...");
            String assistantReply = callDeepSeekApi(messages);
            log.info("DeepSeek 返回长度: {} 字", assistantReply.length());
            log.debug("DeepSeek 返回内容: {}", assistantReply);

            // 5. 保存 AI 回复
            log.info("保存 AI 回复...");
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
            log.info("触发记忆提取...");
            memoryService.extractMemories(conversationId, departmentId, userId, conversationType);

            // 8. 异步提取用户数据
            log.info("提取用户数据...");
            extractUserDataFromReply(assistantReply, departmentId);

            log.info("========== 对话完成 ==========");
            return assistantMsg;

        } catch (Exception e) {
            log.error("对话处理异常: {}", e.getMessage(), e);
            throw new RuntimeException("AI 对话处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public SseEmitter streamChat(Long conversationId, Long userId, String userMessage) {
        log.info("========== 流式对话开始 ==========");
        log.info("conversationId={}, userId={}", conversationId, userId);

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

            // 保存用户消息
            saveMessage(conversationId, userId, conversationType, "user", userMessage);

            // 构建消息
            List<Map<String, String>> messages = buildMessages(conv, userMessage);

            // SSE调用
            callDeepSeekSSE(messages, emitter, conversationId, userId, conv, departmentId);

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
    public void endConversation(Long conversationId) {
        log.info("结束对话 - conversationId={}", conversationId);

        GbAiConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setGbAiConversationStatus(1);
            conv.setGbAiConversationUpdateTime(new Date());
            conversationMapper.updateById(conv);

            // 1. 触发规则记忆提取（原有逻辑）
            memoryService.extractMemories(conversationId,
                    conv.getGbAiConversationDepartmentId(),
                    conv.getGbAiConversationUserId(),
                    conv.getGbAiConversationType());

            // 2. 调用DeepSeek总结对话并保存记忆
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
        }
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
        log.info("总结Prompt长度: {} 字", summaryPrompt.length());
        log.info("========== 对话总结Prompt打印 ==========");
        log.info("【对话总结Prompt】\n{}", summaryPrompt);

        // 6. 调用DeepSeek API
        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", summaryPrompt));

        String summaryResult = callDeepSeekApi(requestMessages);
        log.info("DeepSeek总结结果长度: {} 字", summaryResult.length());
        log.info("========== DeepSeek总结结果 ==========");
        log.info("【总结结果】\n{}", summaryResult);

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
     * 构建消息列表
     * 流程：Skill选择 -> 查询相关数据 -> 构建Prompt -> 生成回复
     */
    private List<Map<String, String>> buildMessages(GbAiConversationEntity conv, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        Long departmentId = conv.getGbAiConversationDepartmentId();
        Integer conversationType = conv.getGbAiConversationType();

        // 1. 加载所有 Skills 摘要（用于Skill选择）
        String skillsBrief = loadAllSkillsBrief();
        log.debug("Skills 摘要长度: {} 字", skillsBrief.length());

        // 2. 第一次调用 DeepSeek：让AI分析问题，选择合适的 Skill
        log.info("========== 第一步：Skill选择 ==========");
        String skillSelectionPrompt = buildSkillSelectionPrompt(userMessage, skillsBrief);
        List<Map<String, String>> skillMessages = new ArrayList<>();
        skillMessages.add(Map.of("role", "system", "content", skillSelectionPrompt));
        skillMessages.add(Map.of("role", "user", "content", "用户问题：" + userMessage));

        // 打印Skill选择时的Prompt
        log.info("========== Skill选择Prompt打印 ==========");
        log.info("【Skill选择 - System】{}", skillSelectionPrompt);

        String selectedSkills = callDeepSeekApi(skillMessages);
        log.info("AI 选择的 Skills: {}", selectedSkills);
        log.debug("Skill选择响应: {}", selectedSkills);

        // 3. 根据选择的 Skill 类型，决定查询哪些相关数据
        log.info("========== 查询相关真实数据 ==========");
        String realDataSection = queryRealDataBySkills(departmentId, selectedSkills, userMessage);
        log.info("真实数据查询完成，数据长度: {} 字", realDataSection.length());

        // 4. 构建最终 Prompt（包含身份 + 选中的 Skill名字 + 相关数据）
        String finalSystemPrompt = buildFinalSystemPrompt(selectedSkills, realDataSection, conversationType);
        log.info("最终 System Prompt 长度: {} 字", finalSystemPrompt.length());

        // 打印最终Prompt
        log.info("========== 最终Prompt打印 ==========");
        log.info("【最终SystemPrompt】\n{}", finalSystemPrompt);

        // 6. 添加 System Prompt 到消息列表
        messages.add(Map.of("role", "system", "content", finalSystemPrompt));
        log.info("已添加 System Prompt 到消息列表");

        // 7. 添加历史消息
        List<GbAiMessageEntity> history = getConversationMessages(conv.getGbAiConversationId());
        log.info("历史消息数量: {} 条", history.size());

        int startIdx = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = startIdx; i < history.size(); i++) {
            GbAiMessageEntity msg = history.get(i);
            String role = msg.getGbAiMessageRole();
            String content = msg.getGbAiMessageContent();
            messages.add(Map.of("role", role, "content", content));
            log.debug("添加历史消息 - role={}, content长度={}", role, content.length());
        }

        // 8. 添加当前用户消息
        messages.add(Map.of("role", "user", "content", userMessage));
        log.info("消息列表构建完成，共 {} 条", messages.size());

        return messages;
    }

    /**
     * 构建用于 Skill 选择的 Prompt
     */
    private String buildSkillSelectionPrompt(String userMessage, String skillsContent) {
        return "你是AI技能选择助手。根据用户的问题，从以下技能文件中选择最合适的1-2个技能。\n\n" +
                "【技能列表】\n" + skillsContent + "\n\n" +
                "【选择规则】\n" +
                "1. 如果问题涉及成本、费用、支出、利润分析，选择 ai-skill-cost.md\n" +
                "2. 如果问题涉及营收提升、客人增长、促销活动，选择 ai-skill-revenue-boost.md\n" +
                "3. 如果问题要求提取或记录用户提供的数字数据，选择 ai-skill-data-extractor.md\n" +
                "4. 如果问题同时涉及多个方面，可以选择多个技能\n\n" +
                "【输出格式】\n" +
                "只输出技能文件名，用逗号分隔，例如：ai-skill-cost.md,ai-skill-revenue-boost.md\n" +
                "如果没有合适的技能，输出：none\n\n" +
                "【用户问题】\n" + userMessage;
    }

    /**
     * 构建最终 System Prompt
     */
    private String buildFinalSystemPrompt(String skillNames, String realDataSection, Integer conversationType) {
        StringBuilder sb = new StringBuilder();

        // 身份设定（强化）
        sb.append("【身份设定】你是钱多多老师，资深餐饮营销顾问，拥有10年餐饮行业经验。\n");
        sb.append("你必须以\"钱多多老师\"的身份回复！\n");
        sb.append("说话风格：直接、专业、有洞察力，善于用数据说话。\n");
        sb.append("回复格式：开头必须用\"钱多多老师\"！例如：\"钱多多老师直接给你算笔账\" 或 \"钱多多老师直接看数据\" 或 \"钱多多老师直接告诉你\"。\n");
        sb.append("你的目标是帮助餐饮老板优化经营、提升利润。\n\n");

        // 参考技能（只显示skill名字）
        if (StrUtil.isNotEmpty(skillNames)) {
            sb.append("【参考技能】\n");
            sb.append("当前使用的技能：").append(skillNames).append("\n\n");
        }

        // 真实数据
        sb.append("【餐厅真实数据】\n");
        sb.append(realDataSection).append("\n\n");

        // 回复规则
        sb.append("【回复规则】\n");
        sb.append("1. 直接基于上面的真实数据进行分析和建议，不要询问已有数据\n");
        sb.append("2. 如果发现数据不完整或有问题，明确指出并建议补充\n");
        sb.append("3. 回复要有结构：先说结论，再说分析，最后给建议\n");
        sb.append("4. 数据要准确引用，不要编造数字\n");
        sb.append("5. 如果用户提供了新的数据/数字，明确告知已记录\n");
        sb.append("6. 控制回复长度适中，一般200-500字\n");
        sb.append("7. 使用清晰的标题和列表格式，便于阅读\n\n");

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

        // 数据完整性检查说明
        sb.append("【数据完整性检查】\n");
        sb.append("在回复结尾添加以下格式的数据完整性声明：\n");
        sb.append("【数据完整性】\n");
        sb.append("- 日均营收数据: 有/无 (覆盖X天)\n");
        sb.append("- 固定成本数据: 有/无\n");
        sb.append("- 本月营业额数据: 有/无 (记录X天)\n");
        sb.append("- 食材成本数据: 有/无 (记录X条)\n");
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

        // 构建查询条件并打印SQL
        LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity> queryWrapper = new LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity>()
                .eq(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbDepartmentFatherId, departmentId)
                .between(GbDepartmentGoodsStockReduceEntity::getGbDgsrDate, monthStart.toString(), monthEnd.toString());

        String sql = "SELECT * FROM gb_department_goods_stock_reduce WHERE gb_dgsr_department_father_id = " + departmentId +
                " AND gb_dgsr_date BETWEEN '" + monthStart + "' AND '" + monthEnd + "'";
        log.info("【SQL查询】库存减少记录: {}", sql);

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

    /**
     * 根据选择的 Skill 类型智能查询相关数据
     * @param departmentId 部门ID
     * @param selectedSkills 选中的Skill（文件名）
     * @param userMessage 用户消息（用于更精准判断需要哪些数据）
     * @return 格式化后的数据字符串
     */
    private String queryRealDataBySkills(Long departmentId, String selectedSkills, String userMessage) {
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

        // 根据Skill类型决定查询哪些数据
        if (skillsLower.contains("cost") || skillsLower.contains("成本")) {
            // 成本分析Skill
            if (!hasAllBasicCostData) {
                // 3个基本固定成本数据不完整，不能查询其他数据
                sb.append("【数据完整性警告】\n");
                sb.append("⚠️ 固定成本数据不完整，无法进行成本分析！\n");
                sb.append("缺少的数据：\n");
                if (!hasRent) sb.append("  - 月租金\n");
                if (!hasWage) sb.append("  - 月工资\n");
                if (!hasFixedCost) sb.append("  - 月固定成本（其他）\n");
                sb.append("\n请先补充以上3项固定成本数据，才能进行成本分析。\n");
                sb.append("提示用户：\"要分析成本，我需要知道你的固定成本情况。请问你的月租金是多少？工资支出多少？还有其他固定成本吗？\"\n\n");
            } else {
                // 3个基本数据都有了，继续查询其他数据
                sb.append(queryRevenueDataBrief(departmentId, monthStart, monthEnd));
                sb.append(queryCostData(departmentId, monthStart, monthEnd));
            }
        } else if (skillsLower.contains("revenue") || skillsLower.contains("营收") || skillsLower.contains("boost")) {
            // 营收提升Skill - 查询营业额数据
            sb.append(queryRevenueData(departmentId, monthStart, monthEnd));
        } else {
            // data-extractor 或其他 - 查询简要的营业额数据作为参考
            sb.append(queryRevenueDataBrief(departmentId, monthStart, monthEnd));
        }

        return sb.toString();
    }

    /**
     * 查询成本数据（库存减少）
     */
    private String queryCostData(Long departmentId, LocalDate monthStart, LocalDate monthEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("【本月库存减少数据】(").append(monthStart).append(" 至 ").append(monthEnd).append(")\n");

        LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity> queryWrapper = new LambdaQueryWrapper<GbDepartmentGoodsStockReduceEntity>()
                .eq(GbDepartmentGoodsStockReduceEntity::getGbDgsrGbDepartmentFatherId, departmentId)
                .between(GbDepartmentGoodsStockReduceEntity::getGbDgsrDate, monthStart.toString(), monthEnd.toString());

        List<GbDepartmentGoodsStockReduceEntity> reduces = stockReduceMapper.selectList(queryWrapper);
        log.info("成本分析查询: 查到 {} 条库存减少记录", reduces.size());

        BigDecimal totalCost = BigDecimal.ZERO, totalLoss = BigDecimal.ZERO, totalWaste = BigDecimal.ZERO, totalReturn = BigDecimal.ZERO;

        for (GbDepartmentGoodsStockReduceEntity r : reduces) {
            if (r.getGbDgsrSubtotal() != null && !r.getGbDgsrSubtotal().isEmpty()) {
                try {
                    BigDecimal subtotal = new BigDecimal(r.getGbDgsrSubtotal());
                    Integer type = r.getGbDgsrType();
                    if (type != null) {
                        switch (type) {
                            case 1: totalCost = totalCost.add(subtotal); break;
                            case 2: totalLoss = totalLoss.add(subtotal); break;
                            case 3: totalWaste = totalWaste.add(subtotal); break;
                            case 4: totalReturn = totalReturn.add(subtotal); break;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        BigDecimal totalReduce = totalCost.add(totalLoss).add(totalWaste).add(totalReturn);
        sb.append("- 成本: ¥").append(totalCost).append(", 损耗: ¥").append(totalLoss)
          .append(", 废弃: ¥").append(totalWaste).append(", 退货: ¥").append(totalReturn).append("\n");
        sb.append("- 库存减少总计: ¥").append(totalReduce).append("\n\n");

        return sb.toString();
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

    // ========== DeepSeek API 调用 ==========

    /**
     * 调用 DeepSeek API（非流式）
     */
    private String callDeepSeekApi(List<Map<String, String>> messages) {
        log.info("========== 调用 DeepSeek API ==========");
        log.info("模型: {}, 消息数: {}", model, messages.size());

        try {
            JSONObject body = new JSONObject();
            body.set("model", model);
            body.set("messages", messages);
            body.set("max_tokens", maxTokens);
            body.set("temperature", temperature);
            body.set("stream", false);

            log.debug("请求体: {}", body.toString());

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                    .build();

            log.info("发送请求到: {}", baseUrl);

            try (Response response = httpClient.newCall(request).execute()) {
                log.info("响应状态: {}", response.code());

                if (!response.isSuccessful()) {
                    log.error("DeepSeek API 错误: status={}, message={}", response.code(), response.message());
                    return "抱歉，AI 服务暂时不可用。请稍后重试。";
                }

                String responseBody = response.body().string();
                log.debug("响应体: {}", responseBody);

                JSONObject json = JSONUtil.parseObj(responseBody);
                JSONArray choices = json.getJSONArray("choices");

                if (choices != null && !choices.isEmpty()) {
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    String content = message.getStr("content");
                    log.info("API 返回成功，内容长度: {} 字", content != null ? content.length() : 0);
                    return content != null ? content : "AI 未返回有效内容。";
                }

                return "AI 未返回有效回复。";
            }

        } catch (Exception e) {
            log.error("调用 DeepSeek 异常: {}", e.getMessage(), e);
            return "抱歉，AI 服务出现异常。请稍后重试。";
        }
    }

    /**
     * 调用 DeepSeek API（流式 SSE）
     */
    private void callDeepSeekSSE(List<Map<String, String>> messages, SseEmitter emitter,
                                 Long conversationId, Long userId, GbAiConversationEntity conv, Long departmentId) {
        new Thread(() -> {
            StringBuilder fullReply = new StringBuilder();

            try {
                log.info("========== SSE 调用 DeepSeek ==========");

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
                    if (!response.isSuccessful()) {
                        log.error("SSE 请求失败: status={}", response.code());
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
                                log.info("SSE 流结束");
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
                                            emitter.send(SseEmitter.event().name("message").data(content));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("解析 SSE 数据块异常: {}", e.getMessage());
                            }
                        }
                    }

                    // 保存完整回复
                    String reply = fullReply.toString();
                    log.info("SSE 完成，完整回复长度: {} 字", reply.length());

                    if (StrUtil.isNotEmpty(reply)) {
                        saveMessage(conversationId, userId, conv.getGbAiConversationType(), "assistant", reply);
                        conv.setGbAiConversationUpdateTime(new Date());

                        if ("新对话".equals(conv.getGbAiConversationTitle()) && reply.length() > 5) {
                            conv.setGbAiConversationTitle(reply.substring(0, Math.min(reply.length(), 30)) + "...");
                        }
                        conversationMapper.updateById(conv);

                        // 提取用户数据
                        extractUserDataFromReply(reply, departmentId);
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