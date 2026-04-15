package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbAiKnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI知识库表 gb_ai_knowledge
 *
 * @author lpy
 * @date 2026-04-11
 */
@Mapper
public interface GbAiKnowledgeMapper extends BaseMapper<GbAiKnowledgeEntity> {

    /**
     * 根据分类和标签查询知识列表（摘要，不含详细内容）
     */
    List<GbAiKnowledgeEntity> selectSummaryList(@Param("category") String category,
                                                 @Param("type") Integer type,
                                                 @Param("tags") String tags);

    /**
     * 获取所有分类
     */
    List<String> selectAllCategories();

    /**
     * 增加使用次数
     */
    int incrementUseCount(@Param("id") Integer id);

    /**
     * 增加查看次数
     */
    int incrementViewCount(@Param("id") Integer id);

}
