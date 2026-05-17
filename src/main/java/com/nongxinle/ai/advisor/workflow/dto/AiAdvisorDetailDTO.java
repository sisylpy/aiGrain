package com.nongxinle.ai.advisor.workflow.dto;

import lombok.Data;

@Data
public class AiAdvisorDetailDTO {
    private Long advisorId;
    private String code;
    private String name;
    private String subtitle;
    private String description;
    private String avatarUrl;
    private Integer sortOrder;
    private Long distributerId;
    private Long departmentId;
}
