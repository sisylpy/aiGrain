package com.nongxinle.ai.workrecord.business;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkRecordBusinessCardFactResult {

    private String sourceEntityType;
    private String sourceEntityId;
    private String sourceEntityName;
    private String sourceFactSnapshot;
    private String sourceFactText;
    private String resolvedAnswerPlanType;
}
