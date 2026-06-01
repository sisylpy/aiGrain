package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.ai.advisor.capability.dto.AdvisorSuggestedQuestionRowDTO;
import com.nongxinle.entity.GbAiWorkflowSuggestedQuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GbAiWorkflowSuggestedQuestionMapper extends BaseMapper<GbAiWorkflowSuggestedQuestionEntity> {

    /**
     * 顾问下可见推荐问句：必须 join advisor_workflow。
     * ACTIVE 要求 workflow.enabled=1；COMING_SOON 允许 workflow.enabled=0。
     *
     * @param questionCodePrefix 非空时仅返回 {@code gb_ai_wsq_question_code} 以该前缀开头的行（顾问级隔离）
     */
    List<AdvisorSuggestedQuestionRowDTO> selectVisibleByAdvisorId(
            @Param("advisorId") Long advisorId,
            @Param("sceneFilter") String sceneFilter,
            @Param("questionCodePrefix") String questionCodePrefix);
}
