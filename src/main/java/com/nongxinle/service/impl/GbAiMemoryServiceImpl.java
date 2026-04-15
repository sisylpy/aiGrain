package com.nongxinle.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.entity.GbAiMemoryEntity;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.mapper.GbAiMemoryMapper;
import com.nongxinle.service.GbAiMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆系统实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiMemoryServiceImpl implements GbAiMemoryService {

    private final GbAiMemoryMapper memoryMapper;
    private final GbAiMessageMapper messageMapper;

    @Override
    public List<GbAiMemoryEntity> retrieveRelevantMemories(Long departmentId, String query, int limit) {
        LambdaQueryWrapper<GbAiMemoryEntity> wrapper = new LambdaQueryWrapper<GbAiMemoryEntity>()
                .eq(GbAiMemoryEntity::getGbAiMemoryDepartmentId, departmentId)
                .eq(GbAiMemoryEntity::getGbAiMemoryStatus, 0) // 活跃状态
                .orderByDesc(GbAiMemoryEntity::getGbAiMemoryImportance)
                .orderByDesc(GbAiMemoryEntity::getGbAiMemoryUpdateTime)
                .last("LIMIT " + limit);

        // 如果有查询关键词，尝试模糊匹配
        if (StrUtil.isNotEmpty(query)) {
            String[] keywords = query.split("[\\s,，。、]+");
            List<String> validKeywords = Arrays.stream(keywords)
                    .filter(k -> k.length() >= 2)
                    .limit(3)
                    .collect(Collectors.toList());

            if (!validKeywords.isEmpty()) {
                wrapper.and(w -> {
                    for (String kw : validKeywords) {
                        w.or().like(GbAiMemoryEntity::getGbAiMemoryTags, kw)
                         .or().like(GbAiMemoryEntity::getGbAiMemoryContent, kw);
                    }
                });
            }
        }

        List<GbAiMemoryEntity> memories = memoryMapper.selectList(wrapper);

        // 更新使用时间和次数
        for (GbAiMemoryEntity m : memories) {
            m.setGbAiMemoryLastUsedTime(new Date());
            m.setGbAiMemoryUseCount(m.getGbAiMemoryUseCount() + 1);
            memoryMapper.updateById(m);
        }

        return memories;
    }

    @Override
    public void extractMemories(Long conversationId, Long departmentId, Long userId, Integer type) {
        // 注意：记忆提取已移至 endConversation 中的 saveConversationSummary 方法
        // 此方法仅标记消息已处理，不再插入记忆
        List<GbAiMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<GbAiMessageEntity>()
                        .eq(GbAiMessageEntity::getGbAiMessageConversationId, conversationId)
                        .orderByAsc(GbAiMessageEntity::getGbAiMessageCreateTime)
        );

        if (messages.isEmpty()) {
            return;
        }

        // 标记消息已处理
        for (GbAiMessageEntity msg : messages) {
            msg.setGbAiMessageMemoryExtracted(1);
            messageMapper.updateById(msg);
        }

        log.info("对话 {} 消息已标记处理", conversationId);
    }

    @Override
    public void autoDream() {
        log.info("AutoDream 开始整理记忆...");
        // 标记 3 个月前的经营数据类记忆为归档
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -3);
        Date threeMonthsAgo = cal.getTime();

        memoryMapper.update(null,
                new LambdaUpdateWrapper<GbAiMemoryEntity>()
                        .like(GbAiMemoryEntity::getGbAiMemoryTags, "经营数据")
                        .lt(GbAiMemoryEntity::getGbAiMemoryCreateTime, threeMonthsAgo)
                        .eq(GbAiMemoryEntity::getGbAiMemoryStatus, 0)
                        .set(GbAiMemoryEntity::getGbAiMemoryStatus, 1)
        );
        log.info("AutoDream 记忆整理完成");
    }

    private GbAiMemoryEntity createMemory(Long departmentId, Long userId, Integer type, String title,
                                         String content, int importance,
                                         Long sourceConversationId, String tags) {
        GbAiMemoryEntity memory = new GbAiMemoryEntity();
        memory.setGbAiMemoryDepartmentId(departmentId);
        memory.setGbAiMemoryUserId(userId);
        memory.setGbAiMemoryType(type);
        memory.setGbAiMemorySummary(title);
        memory.setGbAiMemoryContent(content);
        memory.setGbAiMemoryImportance(importance);
        memory.setGbAiMemoryConversationId(sourceConversationId);
        memory.setGbAiMemoryTags(tags);
        memory.setGbAiMemoryUseCount(0);
        memory.setGbAiMemoryStatus(0); // 活跃
        memory.setGbAiMemoryCreateTime(new Date());
        memory.setGbAiMemoryUpdateTime(new Date());
        // 如果没有标题，用摘要或内容前50字符
        String finalTitle = StrUtil.isNotBlank(title) ? title :
                (StrUtil.isNotBlank(content) ? StrUtil.sub(content, 0, Math.min(50, content.length())) : "无标题");
        memory.setGbAiMemoryTitle(finalTitle);
        return memory;
    }

    private boolean memoryExists(Long departmentId, String keyword) {
        return memoryMapper.selectCount(
                new LambdaQueryWrapper<GbAiMemoryEntity>()
                        .eq(GbAiMemoryEntity::getGbAiMemoryDepartmentId, departmentId)
                        .like(GbAiMemoryEntity::getGbAiMemoryContent, keyword)
                        .eq(GbAiMemoryEntity::getGbAiMemoryStatus, 0)
        ) > 0;
    }

    @Override
    public void saveConversationSummary(Long conversationId, Long departmentId, Long userId, String summaryResult) {
        log.info("========== 保存DeepSeek对话总结 ==========");
        log.info("conversationId={}, departmentId={}", conversationId, departmentId);

        try {
            // 1. 解析JSON
            JSONObject json = JSONUtil.parseObj(summaryResult);

            // 2. 保存对话主题摘要（作为一条总记忆）
            String conversationTopic = json.getStr("conversationTopic", "未分类对话");
            String summary = json.getStr("summary", "");

            if (StrUtil.isNotEmpty(summary)) {
                GbAiMemoryEntity summaryMemory = new GbAiMemoryEntity();
                summaryMemory.setGbAiMemoryDepartmentId(departmentId);
                summaryMemory.setGbAiMemoryUserId(userId);
                summaryMemory.setGbAiMemoryType(0); // 普通记忆
                summaryMemory.setGbAiMemoryTitle("对话总结");
                summaryMemory.setGbAiMemorySummary(conversationTopic);
                summaryMemory.setGbAiMemoryContent(summary);
                summaryMemory.setGbAiMemoryConversationId(conversationId);
                summaryMemory.setGbAiMemoryImportance(8);
                summaryMemory.setGbAiMemoryTags("对话总结,AI总结");
                summaryMemory.setGbAiMemoryUseCount(0);
                summaryMemory.setGbAiMemoryStatus(0);
                summaryMemory.setGbAiMemoryCreateTime(new Date());
                summaryMemory.setGbAiMemoryUpdateTime(new Date());
                memoryMapper.insert(summaryMemory);
                log.info("保存对话主题记忆: {}", conversationTopic);
            }

            // 3. 保存提取的记忆点
            JSONArray memories = json.getJSONArray("memories");
            if (memories != null && !memories.isEmpty()) {
                for (JSONObject memory : memories.jsonIter()) {
                    String title = memory.getStr("title", "");
                    String content = memory.getStr("content", "");
                    String type = memory.getStr("type", "普通记忆");
                    Integer importance = memory.getInt("importance", 5);
                    String tags = memory.getStr("tags", type);

                    // 检查是否已存在相同的记忆
                    if (!memoryExists(departmentId, content)) {
                        GbAiMemoryEntity m = new GbAiMemoryEntity();
                        m.setGbAiMemoryDepartmentId(departmentId);
                        m.setGbAiMemoryUserId(userId);
                        m.setGbAiMemoryType(0);
                        m.setGbAiMemoryTitle(title);
                        m.setGbAiMemorySummary(title);
                        m.setGbAiMemoryContent(content);
                        m.setGbAiMemoryConversationId(conversationId);
                        m.setGbAiMemoryImportance(importance);
                        m.setGbAiMemoryTags(tags + ",AI提取");
                        m.setGbAiMemoryUseCount(0);
                        m.setGbAiMemoryStatus(0);
                        m.setGbAiMemoryCreateTime(new Date());
                        m.setGbAiMemoryUpdateTime(new Date());
                        memoryMapper.insert(m);
                        log.info("保存记忆点: [{}] {}", title, content);
                    } else {
                        log.info("记忆已存在，跳过: {}", content);
                    }
                }
            }

            // 4. 保存承诺
            JSONArray commitments = json.getJSONArray("commitments");
            if (commitments != null && !commitments.isEmpty()) {
                for (JSONObject commitment : commitments.jsonIter()) {
                    String content = commitment.getStr("content", "");
                    String deadline = commitment.getStr("deadline", "");

                    if (StrUtil.isNotEmpty(content)) {
                        String fullContent = deadline != null && !deadline.isEmpty()
                                ? content + " (截止: " + deadline + ")"
                                : content;

                        GbAiMemoryEntity c = new GbAiMemoryEntity();
                        c.setGbAiMemoryDepartmentId(departmentId);
                        c.setGbAiMemoryUserId(userId);
                        c.setGbAiMemoryType(0);
                        c.setGbAiMemoryTitle("老板承诺");
                        c.setGbAiMemorySummary("待办");
                        c.setGbAiMemoryContent(fullContent);
                        c.setGbAiMemoryConversationId(conversationId);
                        c.setGbAiMemoryImportance(7);
                        c.setGbAiMemoryTags("承诺,待办");
                        c.setGbAiMemoryUseCount(0);
                        c.setGbAiMemoryStatus(0);
                        c.setGbAiMemoryCreateTime(new Date());
                        c.setGbAiMemoryUpdateTime(new Date());
                        memoryMapper.insert(c);
                        log.info("保存承诺: {}", fullContent);
                    }
                }
            }

            log.info("对话总结保存完成");

        } catch (Exception e) {
            log.error("保存对话总结失败: {}", e.getMessage(), e);
        }
    }
}
