package com.nongxinle.ai.advisor.capability.dto;

import com.nongxinle.ai.advisor.workflow.dto.AiAdvisorWorkflowRunListItemDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 顾问能力详情：小程序与 Electron 共用。
 * workflowCode / questionCode 仅用于展示、归属、埋点、recentRuns、后续一键执行预留。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAdvisorCapabilityDTO {

    private Long advisorId;
    private String advisorCode;
    private String advisorName;
    private String subtitle;
    private String description;
    private String capabilityDescription;
    private String avatarUrl;
    private Integer sortOrder;
    /** 回显请求 scene；未传时为 null */
    private String scene;
    @Builder.Default
    private List<AdvisorCommonWorkflowDTO> commonWorkflows = new ArrayList<>();
    @Builder.Default
    private List<AdvisorQuestionTopicDTO> questionTopics = new ArrayList<>();
    @Builder.Default
    private List<AiAdvisorWorkflowRunListItemDTO> recentRuns = new ArrayList<>();
}
