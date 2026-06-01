package com.nongxinle.ai.advisor.capability.dto;

import lombok.Data;

/**
 * Mapper 行：顾问下可见的推荐问句（已 join advisor_workflow）。
 */
@Data
public class AdvisorSuggestedQuestionRowDTO {

    private Long workflowId;
    private String workflowCode;
    private Integer workflowEnabled;
    private String topicId;
    private String topicTitle;
    private String topicDescription;
    private Integer topicSort;
    private String questionCode;
    private String questionText;
    private Integer enabled;
    private String status;
    private String scene;
    private Integer sort;
    private String intentHint;
    private String contractHint;
}
