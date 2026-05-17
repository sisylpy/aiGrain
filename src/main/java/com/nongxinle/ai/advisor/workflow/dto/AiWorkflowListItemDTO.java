package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

@Data
public class AiWorkflowListItemDTO {
    private Long workflowId;
    private String code;
    private String name;
    private String description;
    private String category;
    private Integer sortOrder;
    private String intentCode;
    private String pathCode;
    private String harnessEntryType;
    private String harnessPathKey;
}
