package com.nongxinle.ai.harness.followup;

/**
 * Registry 明确「不支持」时的原因码（仅观测；不替代语义澄清/权限拒绝）。
 */
public enum BusinessFollowUpUnsupportedReason {
    NOT_FOLLOW_UP,
    NOT_PURCHASE_PATH_FRAME,
    NO_DETAIL_SLOT,
    NO_MATCHING_CAPABILITY
}
