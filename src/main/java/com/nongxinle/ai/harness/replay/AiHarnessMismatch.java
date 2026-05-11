package com.nongxinle.ai.harness.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiHarnessMismatch {

    private AiHarnessFailureType type;
    /** 人类可读的语义字段名，如 effectiveIntentCode、startDate */
    private String field;
    private Object expected;
    private Object actual;
}
