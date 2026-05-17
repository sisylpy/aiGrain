package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowItemDTO;
import com.nongxinle.entity.GbAiWorkflowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GbAiWorkflowMapper extends BaseMapper<GbAiWorkflowEntity> {

    List<AiAdvisorWorkflowItemDTO> selectAdvisorWorkflowItems(@Param("advisorId") Long advisorId);
}
