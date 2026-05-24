package com.nongxinle.ai.graph.business.scope;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 经营域「解析后范围」的纯函数收口：只读 {@link AiResolvedQueryContext} 已落地的结构化字段，
 * 不解析用户原文、不含 regex/contains。
 * <p>
 * <b>门店可见根（visible store roots）</b>：来自 {@link com.nongxinle.ai.context.AiResolvedOrgScope#getVisibleStores()}
 * 的 {@code storeDepartmentId}，用于展示口径与 Tool 锚点候选；<b>不是</b> SQL {@code department_id IN (...)} 列表。
 * <p>
 * <b>SQL 查询部门范围</b>：见 {@link AiResolvedDataScope#getEffectiveSqlDepartmentIds()} /
 * {@code revenueSqlDepartmentIds} 等；勿与 visible-root 列表混用。
 */
public final class BusinessScopeResolutionSupport {

    private BusinessScopeResolutionSupport() {}

    /**
     * GROUP 广角 Tool Request 范围：只读 ResolvedQueryContext；缺失时不回退旧 AiQueryScope。
     *
     * @param scopeSource {@code resolvedQueryContext.orgScope.visibleStores} /
     *                    {@code resolvedQueryContext.dataScope.effectiveSqlDepartmentIds} /
     *                    {@code missing_resolved_scope}
     */
    public record GroupWideToolScope(List<Integer> resolvedDepartmentIds, int parentStoreCount, String scopeSource) {}

    /**
     * 与 {@link AiResolvedQueryContext#getOrgScope()} {@code visibleStores} 对齐；
     * 集团经营概览等场景下避免仅按单列 request 锚点推断门店集合。
     */
    public static List<Integer> extractVisibleStoreRootDepartmentIds(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getOrgScope() == null) {
            return List.of();
        }
        List<AiStoreScopeDTO> stores = ctx.getOrgScope().getVisibleStores();
        if (stores == null || stores.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(stores.size());
        for (AiStoreScopeDTO s : stores) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            long id = s.getStoreDepartmentId();
            if (id > Integer.MAX_VALUE || id <= 0) {
                continue;
            }
            out.add((int) id);
        }
        return out;
    }

    /** 与 Harness {@code effectiveSqlDepartmentIds} 同源；Tool Request 层不得回退 {@link com.nongxinle.ai.scope.AiQueryScope}。 */
    public static List<Integer> extractEffectiveSqlDepartmentIdsForTools(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getDataScope() == null) {
            return List.of();
        }
        List<Long> raw = ctx.getDataScope().getEffectiveSqlDepartmentIds();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(raw.size());
        for (Long id : raw) {
            if (id == null || id <= 0 || id > Integer.MAX_VALUE) {
                continue;
            }
            out.add(id.intValue());
        }
        return out;
    }

    /** GROUP 广角：orgScope.visibleStores → dataScope.effectiveSqlDepartmentIds → 空（安全，不扩大范围）。 */
    public static GroupWideToolScope resolveGroupWideToolScope(AiResolvedQueryContext ctx) {
        List<Integer> visible = extractVisibleStoreRootDepartmentIds(ctx);
        if (!visible.isEmpty()) {
            return new GroupWideToolScope(visible, visible.size(), "resolvedQueryContext.orgScope.visibleStores");
        }
        List<Integer> sql = extractEffectiveSqlDepartmentIdsForTools(ctx);
        if (!sql.isEmpty()) {
            return new GroupWideToolScope(sql, sql.size(), "resolvedQueryContext.dataScope.effectiveSqlDepartmentIds");
        }
        return new GroupWideToolScope(List.of(), 0, "missing_resolved_scope");
    }
}
