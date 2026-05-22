package com.nongxinle.ai.followup.rewrite.llm;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class LlmFollowUpRewriteParsed {

    boolean parseFailed;
    String parseError;
    String rawDigest;

    boolean isFollowUp;
    boolean canRewrite;
    String completedUserQuery;
    boolean needClarification;
    String clarificationQuestion;
    String rewriteReason;
    List<UsedAnchor> usedAnchors;
    Map<String, Object> debug;

    @Value
    @Builder
    public static class UsedAnchor {
        String anchorType;
        String anchorName;
    }
}
