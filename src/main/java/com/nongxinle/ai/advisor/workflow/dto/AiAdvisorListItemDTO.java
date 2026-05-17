package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

@Data
public class AiAdvisorListItemDTO {
    private Long advisorId;
    private String code;
    private String name;
    private String subtitle;
    private Integer sortOrder;
}
