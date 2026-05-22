package com.nongxinle.ai.semantic.capability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCapabilityMatch {

    private String capabilityId;
    private String targetPurchasePlanType;
    private String queryMode;
}
