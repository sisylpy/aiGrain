package com.nongxinle.ai.identity;

import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class ResolvedEntityIdentity {
    EntityIdentityType entityType;
    String userMentionedName;
    String resolvedCanonicalName;
    Integer resolvedEntityId;
    EntityIdentityResolutionStatus resolutionStatus;
    EntityIdentityResolutionSource resolutionSource;
    @Builder.Default
    List<EntityIdentityCandidate> candidates = Collections.emptyList();
    String anchorPolicyApplied;
    String clarificationMessage;
    @Builder.Default
    Map<String, Object> debugTrace = Collections.emptyMap();

    public boolean hasExplicitMention() {
        return StringUtils.hasText(userMentionedName);
    }

    public boolean isExecutable() {
        return resolutionStatus == EntityIdentityResolutionStatus.OK
                && resolvedEntityId != null
                && resolvedEntityId > 0;
    }

    public static ResolvedEntityIdentity skipped(EntityIdentityType type) {
        return ResolvedEntityIdentity.builder()
                .entityType(type)
                .resolutionStatus(EntityIdentityResolutionStatus.SKIPPED)
                .resolutionSource(EntityIdentityResolutionSource.SKIPPED)
                .candidates(Collections.emptyList())
                .build();
    }

    public static ResolvedEntityIdentity unresolved(EntityIdentityType type, String anchorPolicy) {
        return ResolvedEntityIdentity.builder()
                .entityType(type)
                .resolutionStatus(EntityIdentityResolutionStatus.UNRESOLVED)
                .resolutionSource(EntityIdentityResolutionSource.UNRESOLVED)
                .anchorPolicyApplied(anchorPolicy)
                .candidates(Collections.emptyList())
                .build();
    }
}
