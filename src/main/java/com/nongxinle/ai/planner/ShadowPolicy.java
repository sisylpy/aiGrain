package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * C-63：普通 {@code SHADOW} Run 旁路 Composite 前的灰度闸；仅读 {@link AiRunState} /
 * {@link AiResolvedQueryContext} / 配置；不读用户原文。Harness {@code GRAPH_RUN} 不经此类。
 *
 * <p>前置 {@link BusinessDiagnosisCompositeProductionGate} {@code allowed=true} 由调用方保证。</p>
 */
@Slf4j
@Component
public class ShadowPolicy {

    public static final String SKIP_SHADOW_GRAY_DISABLED = "SHADOW_GRAY_DISABLED";
    public static final String SKIP_WHITELIST_NO_MATCH = "WHITELIST_NO_MATCH";
    public static final String SKIP_SCOPE_NOT_ALLOWED = "SCOPE_NOT_ALLOWED";
    public static final String SKIP_THROTTLE_GLOBAL_MINUTE = "THROTTLE_GLOBAL_MINUTE";
    public static final String SKIP_THROTTLE_GLOBAL_HOUR = "THROTTLE_GLOBAL_HOUR";
    public static final String SKIP_THROTTLE_USER_COOLDOWN = "THROTTLE_USER_COOLDOWN";
    public static final String SKIP_THROTTLE_DISTRIBUTER_COOLDOWN = "THROTTLE_DISTRIBUTER_COOLDOWN";

    private final boolean shadowCompositeEnabled;

    private final Set<Long> userWhitelist;
    private final Set<Long> distributerWhitelist;
    private final Set<Long> departmentWhitelist;

    /** 大写canonical：STORE / GROUP … */
    private final Set<String> scopeWhitelist;

    private final int maxRunsPerMinute;
    private final int maxRunsPerHour;
    private final int cooldownSeconds;

    private final ConcurrentLinkedQueue<Long> globalMinuteMarks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> globalHourMarks = new ConcurrentLinkedQueue<>();
    /** userId {@code →} millis 冷却截止时间（不含）后可再跑旁路（进程内 MVP）。 */
    private final ConcurrentHashMap<Long, Long> userCooldownDeadlineMs = new ConcurrentHashMap<>();

    /** distributerId {@code →} millis 冷却截止时间。 */
    private final ConcurrentHashMap<Long, Long> distributerCooldownDeadlineMs = new ConcurrentHashMap<>();

    private final Object throttleLock = new Object();

    public ShadowPolicy(
            @Value("${ai.composite.businessDiagnosis.shadow.enabled:false}") boolean shadowCompositeEnabled,
            @Value("${ai.composite.businessDiagnosis.shadow.userWhitelist:}") String userWhitelistRaw,
            @Value("${ai.composite.businessDiagnosis.shadow.distributerWhitelist:}")
                    String distributerWhitelistRaw,
            @Value("${ai.composite.businessDiagnosis.shadow.departmentWhitelist:}")
                    String departmentWhitelistRaw,
            @Value("${ai.composite.businessDiagnosis.shadow.scopeWhitelist:}") String scopeWhitelistRaw,
            @Value("${ai.composite.businessDiagnosis.shadow.maxRunsPerMinute:0}") int maxRunsPerMinute,
            @Value("${ai.composite.businessDiagnosis.shadow.maxRunsPerHour:0}") int maxRunsPerHour,
            @Value("${ai.composite.businessDiagnosis.shadow.cooldownSeconds:0}") int cooldownSeconds) {
        this.shadowCompositeEnabled = shadowCompositeEnabled;
        this.userWhitelist = parseLongCsv(userWhitelistRaw);
        this.distributerWhitelist = parseLongCsv(distributerWhitelistRaw);
        this.departmentWhitelist = parseLongCsv(departmentWhitelistRaw);
        this.scopeWhitelist = parseScopeCsv(scopeWhitelistRaw);
        this.maxRunsPerMinute = Math.max(0, maxRunsPerMinute);
        this.maxRunsPerHour = Math.max(0, maxRunsPerHour);
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    /** Gate {@code allowed} + SHADOW mode + productionEnabled 成立后调用；若放行则在此处占用节流配额（再调 {@code tryExecute}）。 */
    public ShadowDecision evaluate(AiRunState runState, AiResolvedQueryContext resolved) {
        if (!shadowCompositeEnabled) {
            return ShadowDecision.skip(SKIP_SHADOW_GRAY_DISABLED, Boolean.FALSE, Boolean.FALSE);
        }

        boolean idsConfigured =
                !(userWhitelist.isEmpty() && distributerWhitelist.isEmpty() && departmentWhitelist.isEmpty());
        boolean scopeConfigured = !scopeWhitelist.isEmpty();
        if (!idsConfigured && !scopeConfigured) {
            return ShadowDecision.skip(SKIP_WHITELIST_NO_MATCH, Boolean.FALSE, Boolean.FALSE);
        }

        AiResolvedOrgScope org = resolved != null ? resolved.getOrgScope() : null;
        String canonScope =
                Optional.ofNullable(org)
                        .map(AiResolvedOrgScope::getScopeType)
                        .filter(StringUtils::hasText)
                        .map(s -> s.trim().toUpperCase())
                        .orElse(null);

        boolean idMatched = !idsConfigured || idWhitelistOrHit(runState, org);
        if (!idMatched) {
            return ShadowDecision.skip(SKIP_WHITELIST_NO_MATCH, Boolean.FALSE, Boolean.FALSE);
        }

        if (scopeConfigured) {
            if (canonScope == null || canonScope.isEmpty() || !scopeWhitelist.contains(canonScope)) {
                return ShadowDecision.skip(SKIP_SCOPE_NOT_ALLOWED, Boolean.FALSE, Boolean.FALSE);
            }
        }

        Map<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("shadowEnabled", Boolean.TRUE);
        dbg.put("whitelistMatched", Boolean.TRUE);

        synchronized (throttleLock) {
            pruneThrottleQueues();
            long now = System.currentTimeMillis();

            dbg.put("throttleQueuedMinute", minuteWindowCount(now));
            dbg.put("throttleQueuedHour", hourWindowCount(now));

            if (maxRunsPerMinute > 0 && minuteWindowCount(now) >= maxRunsPerMinute) {
                dbg.put("throttleCause", SKIP_THROTTLE_GLOBAL_MINUTE);
                return ShadowDecision.skip(SKIP_THROTTLE_GLOBAL_MINUTE, Boolean.TRUE, Boolean.TRUE, dbg);
            }

            if (maxRunsPerHour > 0 && hourWindowCount(now) >= maxRunsPerHour) {
                dbg.put("throttleCause", SKIP_THROTTLE_GLOBAL_HOUR);
                return ShadowDecision.skip(SKIP_THROTTLE_GLOBAL_HOUR, Boolean.TRUE, Boolean.TRUE, dbg);
            }

            if (cooldownSeconds > 0) {
                Long deadline;
                Long uid = runState != null ? runState.getUserId() : null;
                if (uid != null) {
                    deadline = userCooldownDeadlineMs.get(uid);
                    if (deadline != null && now < deadline) {
                        dbg.put("throttleCause", SKIP_THROTTLE_USER_COOLDOWN);
                        return ShadowDecision.skip(
                                SKIP_THROTTLE_USER_COOLDOWN, Boolean.TRUE, Boolean.TRUE, dbg);
                    }
                }

                Long did = distributorKey(runState, org);
                if (did != null) {
                    deadline = distributerCooldownDeadlineMs.get(did);
                    if (deadline != null && now < deadline) {
                        dbg.put("throttleCause", SKIP_THROTTLE_DISTRIBUTER_COOLDOWN);
                        return ShadowDecision.skip(
                                SKIP_THROTTLE_DISTRIBUTER_COOLDOWN, Boolean.TRUE, Boolean.TRUE, dbg);
                    }
                }
            }

            reserveThrottle(now, runState, org);
            return ShadowDecision.allow(dbg);
        }
    }

    /**
     * 名单 OR：对已配置的维度，至少一维命中才算通过；（未配置的维度不参与）。
     */
    private boolean idWhitelistOrHit(AiRunState runState, AiResolvedOrgScope org) {
        if (!userWhitelist.isEmpty()) {
            Long u = runState != null ? runState.getUserId() : null;
            if (u != null && userWhitelist.contains(u)) {
                return true;
            }
        }
        if (!distributerWhitelist.isEmpty()) {
            Long d = distributorKey(runState, org);
            if (d != null && distributerWhitelist.contains(d)) {
                return true;
            }
        }
        if (!departmentWhitelist.isEmpty()) {
            if (hitsDepartmentWhitelist(runState, org)) {
                return true;
            }
        }
        return false;
    }

    private boolean hitsDepartmentWhitelist(AiRunState runState, AiResolvedOrgScope org) {
        if (safeContains(runState != null ? runState.getDepartmentId() : null, departmentWhitelist)) {
            return true;
        }
        if (org == null) {
            return false;
        }
        return safeContains(org.getCurrentStoreDepartmentId(), departmentWhitelist)
                || safeContains(org.getRequestDepartmentId(), departmentWhitelist)
                || safeContains(org.getCurrentDepartmentId(), departmentWhitelist)
                || visitsVisibleDept(org);
    }

    private boolean visitsVisibleDept(AiResolvedOrgScope org) {
        if (org.getVisibleStores() == null) {
            return false;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s != null && departmentWhitelist.contains(s.getStoreDepartmentId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeContains(Long id, Set<Long> set) {
        return id != null && set.contains(id);
    }

    private static Long distributorKey(AiRunState runState, AiResolvedOrgScope org) {
        if (runState != null && runState.getDistributerId() != null) {
            return runState.getDistributerId();
        }
        if (org != null && org.getDistributerId() != null) {
            return org.getDistributerId();
        }
        return null;
    }

    private void pruneThrottleQueues() {
        long now = System.currentTimeMillis();
        pruneTimestampsOlderThan(globalMinuteMarks, now - 60_000L);
        pruneTimestampsOlderThan(globalHourMarks, now - 3_600_000L);

        pruneCooldownNoise(userCooldownDeadlineMs, now);
        pruneCooldownNoise(distributerCooldownDeadlineMs, now);
    }

    private static void pruneCooldownNoise(ConcurrentHashMap<Long, Long> deadlines, long now) {
        if (deadlines.size() > 10_000) {
            deadlines.entrySet().removeIf(e -> now >= Optional.ofNullable(e.getValue()).orElse(0L));
        }
    }

    /** 移除严格早于 {@code keepIfAtOrAfter} 的时间戳记录。 */
    private static void pruneTimestampsOlderThan(ConcurrentLinkedQueue<Long> queue, long keepIfAtOrAfter) {
        while (true) {
            Long h = queue.peek();
            if (h == null || h >= keepIfAtOrAfter) {
                break;
            }
            queue.poll();
        }
    }

    private int minuteWindowCount(long now) {
        pruneTimestampsOlderThan(globalMinuteMarks, now - 60_000L);
        return globalMinuteMarks.size();
    }

    private int hourWindowCount(long now) {
        pruneTimestampsOlderThan(globalHourMarks, now - 3_600_000L);
        return globalHourMarks.size();
    }

    /** 占位：已通过限流检查后立即占用一次全局配额并刷新冷却截止时间。 */
    private void reserveThrottle(long nowMs, AiRunState runState, AiResolvedOrgScope org) {
        globalMinuteMarks.add(nowMs);
        globalHourMarks.add(nowMs);

        long coolUntilExclusive = cooldownSeconds > 0 ? nowMs + cooldownSeconds * 1000L : 0;
        if (cooldownSeconds > 0 && runState != null && runState.getUserId() != null) {
            userCooldownDeadlineMs.put(runState.getUserId(), coolUntilExclusive);
        }
        Long did = distributorKey(runState, org);
        if (cooldownSeconds > 0 && did != null) {
            distributerCooldownDeadlineMs.put(did, coolUntilExclusive);
        }
    }

    private static Set<Long> parseLongCsv(String raw) {
        Set<Long> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        for (String p : raw.split(",")) {
            String t = p != null ? p.trim() : "";
            if (!t.isEmpty()) {
                try {
                    out.add(Long.parseLong(t));
                } catch (NumberFormatException ignore) {
                    log.warn("[ShadowPolicy] ignore whitelist token (not Long): {}", t);
                }
            }
        }
        return out;
    }

    private static Set<String> parseScopeCsv(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        for (String p : raw.split(",")) {
            String t = p != null ? p.trim().toUpperCase() : "";
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
