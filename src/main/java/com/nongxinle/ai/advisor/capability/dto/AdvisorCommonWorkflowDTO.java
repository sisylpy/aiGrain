package com.nongxinle.ai.advisor.capability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorCommonWorkflowDTO {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long workflowId;
    private String workflowCode;
    private String title;
    private String description;
    private String category;
    private boolean enabled;
    private String status;
    private int sort;
    private boolean pinned;
    private boolean isDefault;
}
