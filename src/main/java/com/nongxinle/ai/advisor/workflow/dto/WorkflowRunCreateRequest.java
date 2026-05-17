package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowRunCreateRequest {
    /** 必填：gb_department_user */
    private Long userId;
    private Long advisorId;
    private Long conversationId;
    private Long messageId;
    private Map<String, Object> inputParams;
    private Long distributerId;
    private Long departmentId;

    /** 可选；对齐 {@link com.nongxinle.ai.platform.dto.AiRunCreateRequest#getScopeMode()} */
    private String scopeMode;
}
