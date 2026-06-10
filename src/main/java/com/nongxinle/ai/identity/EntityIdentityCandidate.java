package com.nongxinle.ai.identity;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EntityIdentityCandidate {
    Integer entityId;
    String canonicalName;
}
