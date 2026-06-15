package com.nongxinle.ai.workrecord.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkRecordDeleteResponse {

    private Long recordId;
    private boolean deleted;
}
