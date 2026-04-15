package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nongxinle.entity.GbAiKnowledgeEntity;
import com.nongxinle.mapper.GbAiKnowledgeMapper;
import com.nongxinle.service.GbAiKnowledgeService;
import com.nongxinle.utils.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI知识库 Service 实现
 *
 * @author lpy
 * @date 2026-04-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiKnowledgeServiceImpl extends ServiceImpl<GbAiKnowledgeMapper, GbAiKnowledgeEntity>
        implements GbAiKnowledgeService {

    private final GbAiKnowledgeMapper knowledgeMapper;

    @Override
    public List<GbAiKnowledgeEntity> getSummaryList(String category, Integer type, String tags) {
        return knowledgeMapper.selectSummaryList(category, type, tags);
    }

    @Override
    public GbAiKnowledgeEntity getDetail(Integer id) {
        // 增加查看次数
        knowledgeMapper.incrementViewCount(id);
        return this.getById(id);
    }

    @Override
    public List<String> getAllCategories() {
        return knowledgeMapper.selectAllCategories();
    }

    @Override
    public List<GbAiKnowledgeEntity> recommendByTags(String tags, Integer limit) {
        if (!StringUtils.hasText(tags)) {
            // 没有标签，返回热门知识
            LambdaQueryWrapper<GbAiKnowledgeEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(GbAiKnowledgeEntity::getGbAiKnowledgeStatus, 1)
                    .orderByDesc(GbAiKnowledgeEntity::getGbAiKnowledgeUseCount)
                    .orderByDesc(GbAiKnowledgeEntity::getGbAiKnowledgeEffectRating)
                    .last("LIMIT " + (limit != null ? limit : 5));
            return this.list(wrapper);
        }

        // 按标签匹配
        List<String> tagList = Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        LambdaQueryWrapper<GbAiKnowledgeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GbAiKnowledgeEntity::getGbAiKnowledgeStatus, 1);

        // 拼接标签查询条件
        StringBuilder tagCondition = new StringBuilder("(");
        for (int i = 0; i < tagList.size(); i++) {
            if (i > 0) {
                tagCondition.append(" OR ");
            }
            tagCondition.append("FIND_IN_SET('").append(tagList.get(i)).append("', gb_ai_knowledge_tags) > 0");
        }
        tagCondition.append(")");

        wrapper.and(StringUtils.hasText(tagCondition.toString()) ? w -> w.apply(tagCondition.toString()) : w -> w.isNull(GbAiKnowledgeEntity::getGbAiKnowledgeId));
        wrapper.orderByDesc(GbAiKnowledgeEntity::getGbAiKnowledgeUseCount)
                .orderByDesc(GbAiKnowledgeEntity::getGbAiKnowledgeEffectRating)
                .last("LIMIT " + (limit != null ? limit : 5));

        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordUsage(Integer id) {
        knowledgeMapper.incrementUseCount(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R saveKnowledge(GbAiKnowledgeEntity knowledge) {
        try {
            // 生成 UUID
            if (!StringUtils.hasText(knowledge.getGbAiKnowledgeUuid())) {
                knowledge.setGbAiKnowledgeUuid(UUID.randomUUID().toString());
            }
            // 设置默认值
            if (knowledge.getGbAiKnowledgeStatus() == null) {
                knowledge.setGbAiKnowledgeStatus(1);
            }
            if (knowledge.getGbAiKnowledgeViewCount() == null) {
                knowledge.setGbAiKnowledgeViewCount(0);
            }
            if (knowledge.getGbAiKnowledgeUseCount() == null) {
                knowledge.setGbAiKnowledgeUseCount(0);
            }
            if (knowledge.getGbAiKnowledgeEffectRating() == null) {
                knowledge.setGbAiKnowledgeEffectRating(0);
            }
            if (knowledge.getGbAiKnowledgeEffectCases() == null) {
                knowledge.setGbAiKnowledgeEffectCases(0);
            }

            this.save(knowledge);
            log.info("新增知识成功 - id={}, title={}", knowledge.getGbAiKnowledgeId(), knowledge.getGbAiKnowledgeTitle());
            return R.ok().put("data", knowledge);
        } catch (Exception e) {
            log.error("新增知识失败", e);
            return R.error("新增知识失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R updateKnowledge(GbAiKnowledgeEntity knowledge) {
        try {
            if (knowledge.getGbAiKnowledgeId() == null) {
                return R.error("知识ID不能为空");
            }
            this.updateById(knowledge);
            log.info("更新知识成功 - id={}, title={}", knowledge.getGbAiKnowledgeId(), knowledge.getGbAiKnowledgeTitle());
            return R.ok().put("data", knowledge);
        } catch (Exception e) {
            log.error("更新知识失败", e);
            return R.error("更新知识失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R deleteKnowledge(Integer id) {
        try {
            // 软删除，直接修改状态
            GbAiKnowledgeEntity knowledge = this.getById(id);
            if (knowledge != null) {
                knowledge.setGbAiKnowledgeStatus(2); // 下架
                this.updateById(knowledge);
                log.info("删除知识成功（软删除）- id={}", id);
                return R.ok();
            }
            return R.error("知识不存在");
        } catch (Exception e) {
            log.error("删除知识失败", e);
            return R.error("删除知识失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R softDeleteKnowledge(Integer id) {
        try {
            GbAiKnowledgeEntity knowledge = this.getById(id);
            if (knowledge != null) {
                knowledge.setGbAiKnowledgeStatus(2); // 下架
                this.updateById(knowledge);
                log.info("下架知识成功 - id={}", id);
                return R.ok();
            }
            return R.error("知识不存在");
        } catch (Exception e) {
            log.error("下架知识失败", e);
            return R.error("下架知识失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R removeKnowledge(Integer id) {
        try {
            boolean removed = this.removeById(id);
            if (removed) {
                log.info("彻底删除知识 - id={}", id);
                return R.ok();
            }
            return R.error("知识不存在");
        } catch (Exception e) {
            log.error("彻底删除知识失败", e);
            return R.error("彻底删除失败: " + e.getMessage());
        }
    }

}
