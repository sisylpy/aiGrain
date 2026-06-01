package com.nongxinle.ai.advisor.capability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvisorQuestionTopicDTO {

    private String topicId;
    private String title;
    private String description;
    private int sort;
    @Builder.Default
    private List<AdvisorSuggestedQuestionItemDTO> questions = new ArrayList<>();
}
