package com.nongxinle.ai.harness.followup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessCapabilityMatch {

    private String capabilityId;
    private String targetPurchasePlanType;
    private String queryMode;
}
