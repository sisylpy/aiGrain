package com.nongxinle.ai.workrecord.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkRecordCategoryDTO {

    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String description;
    private Integer sortOrder;
}
