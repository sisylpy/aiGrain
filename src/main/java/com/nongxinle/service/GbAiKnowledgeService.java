package com.nongxinle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nongxinle.entity.GbAiKnowledgeEntity;
import com.nongxinle.utils.R;

import java.util.List;

/**
 * AI知识库表 gb_ai_knowledge
 *
 * @author lpy
 * @date 2026-04-11
 */
public interface GbAiKnowledgeService extends IService<GbAiKnowledgeEntity> {

    /**
     * 获取摘要列表（阶段一用，不含详细内容）
     *
     * @param category 分类
     * @param type     类型
     * @param tags     标签（多个用逗号分隔）
     * @return 摘要列表
     */
    List<GbAiKnowledgeEntity> getSummaryList(String category, Integer type, String tags);

    /**
     * 获取详细内容（阶段二用）
     *
     * @param id 知识ID
     * @return 详细内容
     */
    GbAiKnowledgeEntity getDetail(Integer id);

    /**
     * 获取所有分类
     *
     * @return 分类列表
     */
    List<String> getAllCategories();

    /**
     * 根据标签推荐知识
     *
     * @param tags 标签（多个用逗号分隔）
     * @param limit 返回数量
     * @return 推荐的知识列表
     */
    List<GbAiKnowledgeEntity> recommendByTags(String tags, Integer limit);

    /**
     * 记录知识使用
     *
     * @param id 知识ID
     */
    void recordUsage(Integer id);

    /**
     * 新增知识
     *
     * @param knowledge 知识实体
     * @return 结果
     */
    R saveKnowledge(GbAiKnowledgeEntity knowledge);

    /**
     * 更新知识
     *
     * @param knowledge 知识实体
     * @return 结果
     */
    R updateKnowledge(GbAiKnowledgeEntity knowledge);

    /**
     * 删除知识
     *
     * @param id 知识ID
     * @return 结果
     */
    R deleteKnowledge(Integer id);

    /**
     * 下架知识（软删除）
     *
     * @param id 知识ID
     * @return 结果
     */
    R softDeleteKnowledge(Integer id);

    /**
     * 彻底删除知识（物理删除）
     *
     * @param id 知识ID
     * @return 结果
     */
    R removeKnowledge(Integer id);

}
