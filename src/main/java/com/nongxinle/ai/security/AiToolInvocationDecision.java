package com.nongxinle.ai.security;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/** Tool / Agent 是否可执行的判定结果（不依赖异常）。 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiToolInvocationDecision {

    boolean allowed;
    AiPermissionDenied denial;

    public static AiToolInvocationDecision allow() {
        return new AiToolInvocationDecision(true, null);
    }

    public static AiToolInvocationDecision deny(AiPermissionDenied denial) {
        return new AiToolInvocationDecision(false, denial);
    }
}
