package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

/**
 * 某顾问下绑定的可执行工作流（含绑定表展示字段）。
 */
@Data
public class AiAdvisorWorkflowItemDTO {
    private Long workflowId;
    private String code;
    private String name;
    private String description;
    private String category;
    private Integer bindSortOrder;
    private Integer bindPinned;
    private Integer bindIsDefault;
    private String relationType;
}
