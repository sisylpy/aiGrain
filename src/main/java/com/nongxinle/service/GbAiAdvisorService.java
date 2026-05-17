package com.nongxinle.service;

import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorDetailDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorListItemDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowItemDTO;
import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowRunListItemDTO;

import java.util.List;

public interface GbAiAdvisorService {

    List<AiAdvisorListItemDTO> listAdvisors();

    AiAdvisorDetailDTO getAdvisor(Long advisorId);

    List<AiAdvisorWorkflowItemDTO> listAdvisorWorkflows(Long advisorId);

    /**
     * 某顾问下该用户最近的工作流运行（只读，按 run 创建时间倒序）。
     */
    List<AiAdvisorWorkflowRunListItemDTO> listRecentWorkflowRuns(Long advisorId, Long userId, int limit);
}
