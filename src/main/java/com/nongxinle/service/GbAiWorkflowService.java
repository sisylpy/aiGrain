package com.nongxinle.service;

import com.nongxinle.ai.advisor.workflow.dto.AiWorkflowListItemDTO;
import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunCreateRequest;
import com.nongxinle.ai.advisor.workflow.dto.WorkflowRunCreateResponseDTO;

import java.util.List;

public interface GbAiWorkflowService {

    List<AiWorkflowListItemDTO> listWorkflows();

    WorkflowRunCreateResponseDTO startWorkflowRun(Long workflowId, WorkflowRunCreateRequest body);
}
