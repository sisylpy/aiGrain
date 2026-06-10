package com.nongxinle.ai.identity;

public enum EntityIdentityResolutionSource {
    CURRENT_MENTION_DB,
    CURRENT_STRUCTURED_ID,
    INHERITED_PREVIOUS_ANCHOR,
    REWRITE_INHERITED_ANCHOR,
    UNRESOLVED,
    SKIPPED
}
