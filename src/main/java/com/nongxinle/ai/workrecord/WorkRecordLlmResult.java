package com.nongxinle.ai.workrecord;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WorkRecordLlmResult {

    private String polishedContent;
    /** debug only: KEEP / LIGHT_EDIT / STRUCTURE */
    private String polishMode;
    private Long selectedCategoryId;
    private String selectedCategoryCode;
    private String selectedCategoryName;
    private String categoryDecision;
    private String suggestedCategoryName;
    private BigDecimal confidence;
    private String shortReason;
    /** transitional protocol warning from parser; debug only */
    private String protocolWarning;
}
