package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowRunListItemDTO;
import com.nongxinle.entity.GbAiWorkflowRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GbAiWorkflowRunMapper extends BaseMapper<GbAiWorkflowRunEntity> {

    List<AiAdvisorWorkflowRunListItemDTO> selectRecentByAdvisorAndUser(
            @Param("advisorId") Long advisorId,
            @Param("userId") Long userId,
            @Param("limit") int limit);
}
