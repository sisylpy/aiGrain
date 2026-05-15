package com.nongxinle.ai.planner;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C-63：普通 Run {@code SHADOW} Composite 灰度判定结果；不写用户正文；不参与 Harness {@code HARNESS_ONLY}。
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShadowDecision {

    private final boolean allowed;
    private final boolean skipped;
    /** 稳定性机读码，如 {@code SHADOW_GRAY_DISABLED}、{@code WHITELIST_NO_MATCH}。 */
    private final String skipReason;
    /** 名单 OR 语义是否通过（vacuous truth：未配置任一维度则无意义，见 Policy）。 */
    private final Boolean whitelistMatched;
    private final Boolean throttleHit;
    @Builder.Default
    private final Map<String, Object> debug = Map.of();

    public static ShadowDecision allow(Map<String, Object> debug) {
        Map<String, Object> d = debug == null || debug.isEmpty() ? Map.of() : Map.copyOf(debug);
        return ShadowDecision.builder()
                .allowed(true)
                .skipped(false)
                .skipReason(null)
                .whitelistMatched(Boolean.TRUE)
                .throttleHit(Boolean.FALSE)
                .debug(d)
                .build();
    }

    public static ShadowDecision skip(String reason, Boolean whitelistMatched, Boolean throttleHit) {
        return skip(reason, whitelistMatched, throttleHit, Map.of());
    }

    public static ShadowDecision skip(
            String reason, Boolean whitelistMatched, Boolean throttleHit, Map<String, Object> debug) {
        LinkedHashMap<String, Object> d =
                debug == null || debug.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(debug);
        return ShadowDecision.builder()
                .allowed(false)
                .skipped(true)
                .skipReason(reason)
                .whitelistMatched(whitelistMatched)
                .throttleHit(throttleHit)
                .debug(Map.copyOf(d))
                .build();
    }
}
