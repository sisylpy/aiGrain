package com.nongxinle.ai.platform.dto;

import com.nongxinle.ai.platform.AiRunStatus;

/**
 * POST /stop 返回值构造与联调日志用。
 */
public record StopRunOutcome(
        boolean sessionFound,
        AiRunStatus statusAtInvocation,
        boolean cancelApplied,
        String message
) {
}
