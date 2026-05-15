package com.nongxinle.ai.graph.business.scope;

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
 * <b>SQL 查询部门范围</b>：见 {@link com.nongxinle.ai.context.AiResolvedDataScope#getEffectiveSqlDepartmentIds()} /
 * {@code revenueSqlDepartmentIds} 等；勿与 visible-root 列表混用。
 */
public final class BusinessScopeResolutionSupport {

    private BusinessScopeResolutionSupport() {}

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
}
