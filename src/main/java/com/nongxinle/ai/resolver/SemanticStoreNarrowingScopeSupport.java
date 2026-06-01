package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.ScopeResolutionTrace;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 语义门店收窄已落地后的 scope 判定与门店根解析（只读结构化字段，不解析用户原文）。
 */
public final class SemanticStoreNarrowingScopeSupport {

    private SemanticStoreNarrowingScopeSupport() {}

    public static boolean isSemanticStoreNarrowingActive(AiResolvedQueryContext rq) {
        if (rq == null) {
            return false;
        }
        ScopeResolutionTrace trace = rq.getScopeResolutionTrace();
        if (trace != null && Boolean.TRUE.equals(trace.getSemanticNarrowingApplied())) {
            return true;
        }
        if ("CURRENT_MESSAGE_EXPLICIT_STORE".equals(rq.getEffectiveScopeSource())) {
            return true;
        }
        AiSemanticStoreNarrowingDiagnostics diag = rq.getSemanticStoreNarrowingDebug();
        return diag != null && diag.isNarrowedSuccessfully();
    }

    /** 已收窄查询目标对应的门店根 department id（去重、保序）。 */
    public static List<Integer> resolveNarrowedStoreRootDepartmentIds(AiResolvedQueryContext rq) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        if (rq == null) {
            return List.of();
        }
        AiResolvedOrgScope org = rq.getOrgScope();
        if (org != null && org.getVisibleStores() != null) {
            for (AiStoreScopeDTO s : org.getVisibleStores()) {
                if (s == null || s.getStoreDepartmentId() == null) {
                    continue;
                }
                long sid = s.getStoreDepartmentId();
                if (sid > 0L && sid <= Integer.MAX_VALUE) {
                    out.add((int) sid);
                }
            }
        }
        if (out.isEmpty()) {
            Integer fromDiag = parseStoreRootIdFromDiagCandidate(
                    rq.getSemanticStoreNarrowingDebug() != null
                            ? rq.getSemanticStoreNarrowingDebug().getMatchedStoreCandidate()
                            : null);
            if (fromDiag != null) {
                out.add(fromDiag);
            }
        }
        return new ArrayList<>(out);
    }

    static Integer parseStoreRootIdFromDiagCandidate(String matchedStoreCandidate) {
        if (!StringUtils.hasText(matchedStoreCandidate)) {
            return null;
        }
        String raw = matchedStoreCandidate.trim();
        int colon = raw.indexOf(':');
        String idPart = colon > 0 ? raw.substring(0, colon).trim() : raw;
        try {
            int id = Integer.parseInt(idPart);
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
