package com.nongxinle.ai.gateway;

/**
 * 网关/传输层失败时的机器可读标记；供 Semantic Intake / V2 parser 识别，不做业务关键词推断。
 */
public final class LlmGatewayFailureMarker {

    public static final String MARKER = "@@LLM_GATEWAY_UNAVAILABLE@@";

    private LlmGatewayFailureMarker() {}

    public static boolean isMarked(String raw) {
        return raw != null && raw.trim().startsWith(MARKER);
    }

    public static String wrapUnavailable(String humanReadable) {
        if (humanReadable == null || humanReadable.isBlank()) {
            return MARKER;
        }
        return MARKER + "\n" + humanReadable.trim();
    }
}
