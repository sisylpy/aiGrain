package com.nongxinle.ai.scope;

import com.nongxinle.ai.context.AiOrgScope;
import com.nongxinle.ai.context.AiOrgScopeResolver;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiAnswerBoundary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Run 链路：请求的部门／分销锚点与用户身份允许的子树求交，写回 {@link AiRunState}，并回填 {@link AiQueryScope}
 * （与会话侧的 {@link AiQueryScopeAccess} 互补：后者按 {@code gb_department_user}，本条按 Resolver 下发的角色锚点）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRunScopeIntersectService {

    private final AiScopeResolver scopeResolver;
    private final AiOrgScopeResolver orgScopeResolver;

    public void applyIntersection(AiRunState state, Map<Integer, List<Integer>> subtreeCache) {
        if (state == null || state.getAiUserContext() == null) {
            return;
        }
        AiUserContext ctx = state.getAiUserContext();
        if (AiRoleMapper.isGroupWideOrgScope(ctx.getRoleCode())) {
            fillGroupWideScopeSnapshot(state, subtreeCache);
            return;
        }

        Long anchor = ctx.getDepartmentId();
        if (anchor == null) {
            return;
        }

        clampDistributorIfNeeded(state, ctx);

        Set<Integer> allowedSet = new LinkedHashSet<>(
                scopeResolver.collectSubtreeDepartmentIds(anchor.intValue(), subtreeCache));

        Long requestedRoot = state.getDepartmentId();
        if (requestedRoot == null) {
            state.setDepartmentId(anchor);
            writeQueryScope(state, allowedSet);

            state.setScopeConvergenceNote(AiAnswerBoundary.scopeClampIntroductionForRole(ctx.getRoleCode()));
            log.info("[AI-RUN-SCOPE] userId={} role={} null request dept clamped to anchor={}",
                    ctx.getUserId(), ctx.getRoleCode(), anchor);
            refreshOrgSnapshot(state);
            return;
        }

        List<Integer> requestTree =
                scopeResolver.collectSubtreeDepartmentIds(requestedRoot.intValue(), subtreeCache);
        Set<Integer> effective = intersect(requestTree, allowedSet);

        if (effective.isEmpty()) {
            state.setDepartmentId(anchor);
            writeQueryScope(state, allowedSet);
            state.setScopeConvergenceNote(AiAnswerBoundary.scopeClampIntroductionForRole(ctx.getRoleCode()));
            log.info("[AI-RUN-SCOPE] userId={} role={} empty intersection requested={} anchor={}",
                    ctx.getUserId(), ctx.getRoleCode(), requestedRoot, anchor);
            refreshOrgSnapshot(state);
            return;
        }

        boolean rootInside = effective.contains(requestedRoot.intValue());
        if (!rootInside) {
            state.setDepartmentId(anchor);
            writeQueryScope(state, allowedSet);
            state.setScopeConvergenceNote(AiAnswerBoundary.scopeClampIntroductionForRole(ctx.getRoleCode()));
            log.info("[AI-RUN-SCOPE] userId={} role={} broad request root={} clamped to anchor={} overlapNodes={}",
                    ctx.getUserId(), ctx.getRoleCode(), requestedRoot, anchor, effective.size());
        } else {
            writeQueryScope(state, effective);
        }

        refreshOrgSnapshot(state);
    }

    /** 集团管理端：仅按 {@code distributerId} 枚举门店根（father_id=0），合并各门店子树写入 scope；不依赖请求 departmentId。 */
    private void fillGroupWideScopeSnapshot(AiRunState state, Map<Integer, List<Integer>> subtreeCache) {
        AiUserContext ctx = state.getAiUserContext();
        if (ctx != null) {
            clampDistributorIfNeeded(state, ctx);
        }
        Long dis = state.getDistributerId();
        if (dis == null || dis <= 0) {
            dis = ctx != null ? ctx.getDistributerId() : null;
        }
        if (dis == null || dis <= 0) {
            log.warn("[AI-RUN-SCOPE] GROUP_WIDE missing distributerId userId={}",
                    ctx != null ? ctx.getUserId() : null);
            writeQueryScope(state, Set.of(), null);
            refreshOrgSnapshot(state);
            return;
        }
        List<Integer> storeRoots = scopeResolver.listStoreDepartmentIdsUnderDistributer(dis.intValue());
        if (storeRoots.isEmpty()) {
            log.warn("[AI-RUN-SCOPE] GROUP_WIDE disId={} no store roots (father_id=0)", dis);
            writeQueryScope(state, Set.of(), 0);
            refreshOrgSnapshot(state);
            return;
        }
        LinkedHashSet<Integer> union = new LinkedHashSet<>();
        for (Integer root : storeRoots) {
            union.addAll(scopeResolver.collectSubtreeDepartmentIds(root, subtreeCache));
        }
        log.info(
                "[AI-RUN-SCOPE] GROUP_WIDE disId={} storeRootCount={} storeRootIds={} resolvedDeptNodeCount={}",
                dis, storeRoots.size(), storeRoots, union.size());
        writeQueryScope(state, union, storeRoots.size());
        refreshOrgSnapshot(state);
    }

    private static void clampDistributorIfNeeded(AiRunState state, AiUserContext ctx) {
        Long reqDis = state.getDistributerId();
        Long uDis = ctx.getDistributerId();
        if (reqDis != null && uDis != null && !Objects.equals(reqDis, uDis)) {
            state.setDistributerId(uDis);
            log.info("[AI-RUN-SCOPE] distributer mismatch req={} ctx={} userId={}; clamp to ctx distributer",
                    reqDis, uDis, ctx.getUserId());
        }
    }

    private static Set<Integer> intersect(List<Integer> requestSubtree, Set<Integer> allowedSet) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (Integer id : requestSubtree) {
            if (allowedSet.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private void writeQueryScope(AiRunState state, Set<Integer> deptIds) {
        writeQueryScope(state, deptIds, null);
    }

    /**
     * @param overrideParentStoreCount 集团广角下为分销户下「门店根」数量（{@code father_id=0}），与
     *                                  {@link AiScopeResolver#listStoreDepartmentIdsUnderDistributer(int)} 一致；
     *                                  避免仅依赖 {@code is_group_dep=1} 的零售锚点统计。
     */
    private void writeQueryScope(AiRunState state, Set<Integer> deptIds, Integer overrideParentStoreCount) {
        List<Integer> sorted = deptIds.stream().sorted().distinct().collect(Collectors.toList());
        AiUserContext ctx = state.getAiUserContext();
        Long anchorMem = ctx != null ? ctx.getDepartmentId() : null;
        int disInt = state.getDistributerId() != null ? state.getDistributerId().intValue() : 0;
        Map<Integer, Integer> counts = sorted.isEmpty()
                ? Map.of()
                : scopeResolver.departmentTypeCountsForIds(sorted);
        boolean groupWideCtx = ctx != null && AiRoleMapper.isGroupWideOrgScope(ctx.getRoleCode());
        int parentStoreCount;
        if (sorted.isEmpty()) {
            parentStoreCount = 0;
        } else if (groupWideCtx) {
            parentStoreCount = overrideParentStoreCount != null
                    ? Math.max(0, overrideParentStoreCount)
                    : scopeResolver.listRetailStoreAnchorDepartmentIds(sorted).size();
        } else {
            int parentStoresPrecise = scopeResolver.countRetailParentStoresAmongIds(sorted);
            parentStoreCount = parentStoresPrecise > 0 ? parentStoresPrecise : 1;
        }
        AiQueryScope qs = AiQueryScope.builder()
                .mode(AiConversationScopeMode.STORE)
                .departmentFatherId(state.getDepartmentId())
                .distributerId(state.getDistributerId())
                .disIdForPurchaseQueries(disInt)
                .resolvedDepartmentIds(List.copyOf(sorted))
                .departmentTypeCounts(counts)
                .parentStoreCount(parentStoreCount)
                .userMemoryAnchorDepartmentId(anchorMem)
                .groupRevenueUseDistributerWideQuery(false)
                .build();
        state.setScope(qs);
    }

    /** 使用收窄后的 dept/dis 重建 {@link AiOrgScope}，供后续 Guard／Trace 对齐。 */
    private void refreshOrgSnapshot(AiRunState state) {
        if (state.getAiUserContext() == null) {
            return;
        }
        AiRunCreateRequest synthetic = new AiRunCreateRequest();
        synthetic.setUserId(state.getUserId());
        synthetic.setConversationId(state.getConversationId());
        synthetic.setDepartmentId(state.getDepartmentId());
        synthetic.setDistributerId(state.getDistributerId());
        synthetic.setRoleCode(state.getAiUserContext().getRoleCode());
        synthetic.setMessage(state.getNormalizedUserInput() != null ? state.getNormalizedUserInput() : "");
        AiOrgScope refreshed = orgScopeResolver.resolve(state.getAiUserContext(), synthetic);
        state.setAiOrgScope(refreshed);
    }
}
