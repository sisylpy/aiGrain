package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.ScopeResolutionTrace;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 组织权限与 SQL 数据范围装配：role / distributerId / department 树 → {@link AiResolvedOrgScope} / {@link AiResolvedDataScope}。
 * 不含语义 wire、门店 narrowing（见 {@link SemanticScopeNarrowingPolicy}）、
 * 语义点名越权（见 {@link SemanticPermissionMentionPolicy}，勿在本类重复实现）。
 */
@Component
@RequiredArgsConstructor
public class AiResolvedOrgScopeAssembler {

    private final GbDepartmentMapper gbDepartmentMapper;
    private final AiScopeResolver scopeResolver;

    private record NormalizedDept(Long storeDepartmentId, String storeName) {}

    public AiResolvedDataScope buildDataScope(AiResolvedOrgScope org) {
        if (org == null) {
            return AiResolvedDataScope.builder()
                    .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                    .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_EMPTY)
                    .queryStoreIds(new ArrayList<>())
                    .queryRealDepartmentIds(new ArrayList<>())
                    .expandedSqlDepartmentIds(new ArrayList<>())
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .build();
        }
        if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType())) {
            List<Long> whIds = org.getVisibleWarehouses() == null ? new ArrayList<>() : org.getVisibleWarehouses().stream()
                    .map(AiDepartmentScopeDTO::getDepartmentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
            boolean allWh = !whIds.isEmpty();
            List<Integer> whInt = new ArrayList<>();
            for (Long id : whIds) {
                if (id != null && id > 0 && id <= Integer.MAX_VALUE) {
                    whInt.add(id.intValue());
                }
            }
            return AiResolvedDataScope.builder()
                    .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_DEPARTMENT)
                    .queryRealDepartmentIds(new ArrayList<>(whInt))
                    .queryStoreIds(new ArrayList<>())
                    .queryDistributerId(null)
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .expandedSqlDepartmentIds(new ArrayList<>(whInt))
                    .visibleStoreIds(new ArrayList<>())
                    .storeRootDepartmentIds(new ArrayList<>())
                    .explicitChildDepartmentIds(new ArrayList<>())
                    .expandedChildDepartmentIds(new ArrayList<>())
                    .visibleWarehouseIds(new ArrayList<>(whIds))
                    .targetDepartmentIds(new ArrayList<>(whIds))
                    .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_WAREHOUSE_DEPARTMENT)
                    .allVisibleStores(false)
                    .allVisibleWarehouses(allWh)
                    .build();
        }

        List<Long> storeRoots = org.getVisibleStores() == null ? new ArrayList<>() : org.getVisibleStores().stream()
                .map(AiStoreScopeDTO::getStoreDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        LinkedHashMap<Integer, List<Integer>> rootToChildrenInt = new LinkedHashMap<>();
        List<Long> expandedChildren = new ArrayList<>();
        List<Integer> expandedSqlInt = new ArrayList<>();
        List<Integer> storeRootInts = new ArrayList<>();

        for (Long root : storeRoots) {
            if (root == null || root <= 0 || root > Integer.MAX_VALUE) {
                continue;
            }
            int ri = root.intValue();
            storeRootInts.add(ri);
            expandedSqlInt.add(ri);
            List<Integer> childIdsInt = new ArrayList<>();
            List<GbDepartmentEntity> subs = gbDepartmentMapper.querySubDepartments(ri);
            if (subs != null) {
                for (GbDepartmentEntity sub : subs) {
                    if (sub != null && sub.getGbDepartmentId() != null) {
                        long sid = sub.getGbDepartmentId().longValue();
                        if (sid > 0 && sid <= Integer.MAX_VALUE) {
                            int si = (int) sid;
                            expandedSqlInt.add(si);
                            childIdsInt.add(si);
                            expandedChildren.add(sid);
                        }
                    }
                }
            }
            rootToChildrenInt.put(ri, childIdsInt);
        }

        if (storeRoots.isEmpty() && org.getDistributerId() != null) {
            long dis = org.getDistributerId();
            if (dis > 0 && dis <= Integer.MAX_VALUE) {
                return AiResolvedDataScope.builder()
                        .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_DISTRIBUTER)
                        .queryStoreIds(new ArrayList<>())
                        .queryRealDepartmentIds(new ArrayList<>())
                        .queryDistributerId((int) dis)
                        .storeToDepartmentIds(new LinkedHashMap<>())
                        .expandedSqlDepartmentIds(new ArrayList<>())
                        .visibleStoreIds(new ArrayList<>())
                        .storeRootDepartmentIds(new ArrayList<>())
                        .explicitChildDepartmentIds(new ArrayList<>())
                        .expandedChildDepartmentIds(new ArrayList<>())
                        .visibleWarehouseIds(new ArrayList<>())
                        .targetDepartmentIds(new ArrayList<>())
                        .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_EMPTY)
                        .allVisibleStores(false)
                        .allVisibleWarehouses(false)
                        .build();
            }
        }

        boolean allStores = AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType());
        Integer queryDis =
                allStores && org.getDistributerId() != null
                        && org.getDistributerId() > 0
                        && org.getDistributerId() <= Integer.MAX_VALUE
                        ? org.getDistributerId().intValue()
                        : null;
        List<Long> rootsCopy = new ArrayList<>(storeRoots);
        return AiResolvedDataScope.builder()
                .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                .queryStoreIds(new ArrayList<>(storeRootInts))
                .queryRealDepartmentIds(new ArrayList<>())
                .queryDistributerId(queryDis)
                .storeToDepartmentIds(rootToChildrenInt)
                .expandedSqlDepartmentIds(new ArrayList<>(expandedSqlInt))
                .visibleStoreIds(new ArrayList<>(rootsCopy))
                .storeRootDepartmentIds(new ArrayList<>(rootsCopy))
                .explicitChildDepartmentIds(new ArrayList<>())
                .expandedChildDepartmentIds(expandedChildren)
                .visibleWarehouseIds(new ArrayList<>())
                .targetDepartmentIds(new ArrayList<>())
                .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE)
                .allVisibleStores(allStores)
                .allVisibleWarehouses(false)
                .build();
    }

    public AiResolvedOrgScope resolveOrgScope(AiUserContext ctx, Long requestDepartmentId, AiRunCreateRequest request) {
        return resolveOrgScope(ctx, requestDepartmentId, request, null);
    }

    public AiResolvedOrgScope resolveOrgScope(
            AiUserContext ctx,
            Long requestDepartmentId,
            AiRunCreateRequest request,
            AiConversationScopeMode conversationScopeMode) {
        AiResolvedOrgScope explicitGroup = buildBaselineGroupOrgScopeForRequest(ctx, requestDepartmentId, request);
        if (explicitGroup != null) {
            return explicitGroup;
        }
        Integer admin = ctx.getSourceAdminRole();
        if (admin == null) {
            return buildDepartmentLikeScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_DEPARTMENT, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.GROUP_MANAGER_APP)) {
            if (conversationScopeMode == AiConversationScopeMode.STORE) {
                return buildStoreScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_STORE, request);
            }
            return buildGroupScope(ctx, requestDepartmentId, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.STORE_MANAGER_APP)) {
            return buildStoreScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_STORE, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.STORE_PURCHASER_APP)) {
            return buildStoreScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_PURCHASER, request);
        }
        if (Objects.equals(admin, GbConstants.DepartmentUserRole.WAREHOUSE_APP)) {
            return buildWarehouseScope(ctx, requestDepartmentId, request);
        }
        return buildDepartmentLikeScope(ctx, requestDepartmentId, AiResolvedOrgScope.SCOPE_DEPARTMENT, request);
    }

    /**
     * 请求显式 {@code scopeMode=GROUP} 且角色具备集团广角权限时，直接展开 distributer 下全部门店。
     */
    public AiResolvedOrgScope buildBaselineGroupOrgScopeForRequest(
            AiUserContext ctx, Long requestDepartmentId, AiRunCreateRequest request) {
        if (!RequestExplicitGroupScopeSupport.isExplicitGroupScopeRequest(request)) {
            return null;
        }
        if (ctx == null || !com.nongxinle.ai.mapping.AiRoleMapper.isGroupWideOrgScope(ctx.getRoleCode())) {
            return null;
        }
        return buildGroupScope(ctx, requestDepartmentId, request);
    }

    /**
     * Run 请求体中的 {@code distributerId} 优先于用户表快照，避免集团账号挂靠部门与主体 ID 不一致时只展开一家门店。
     */
    public static Long mergedDistributerId(AiRunCreateRequest request, AiUserContext ctx) {
        if (request != null && request.getDistributerId() != null) {
            return request.getDistributerId();
        }
        return ctx != null ? ctx.getDistributerId() : null;
    }

    /**
     * Run 态 distributerId：请求体 → 用户快照 → 已解析 orgScope → 会话持久化值。
     */
    public static Long mergedRunDistributerId(
            AiRunCreateRequest request,
            AiUserContext ctx,
            AiResolvedQueryContext resolved,
            Long conversationDistributerId) {
        Long dis = mergedDistributerId(request, ctx);
        if (dis != null) {
            return dis;
        }
        if (resolved != null && resolved.getOrgScope() != null && resolved.getOrgScope().getDistributerId() != null) {
            return resolved.getOrgScope().getDistributerId();
        }
        return conversationDistributerId;
    }

    /**
     * {@link com.nongxinle.ai.graph.business.BusinessScopeIntersectNode} 收窄 Run 锚点后，同步
     * {@link AiResolvedQueryContext#getOrgScope()} / {@link AiResolvedQueryContext#getDataScope()}。
     */
    public void patchResolvedQueryContextAfterRunIntersect(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null || state.getAiUserContext() == null) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        AiResolvedOrgScope org = rq.getOrgScope();
        if (org == null) {
            return;
        }
        AiUserContext ctx = state.getAiUserContext();
        AiRunCreateRequest syn = new AiRunCreateRequest();
        syn.setDepartmentId(state.getDepartmentId());
        syn.setDistributerId(state.getDistributerId());
        if (StringUtils.hasText(state.getScopeMode())) {
            syn.setScopeMode(state.getScopeMode());
        } else if (rq.getConversationScopeMode() == com.nongxinle.ai.scope.AiConversationScopeMode.GROUP) {
            syn.setScopeMode("GROUP");
        }

        if ((rq.getConversationScopeMode() == com.nongxinle.ai.scope.AiConversationScopeMode.GROUP
                        || RequestExplicitGroupScopeSupport.isExplicitGroupScopeRequest(syn))
                && !AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType())
                && !SemanticStoreNarrowingScopeSupport.isSemanticStoreNarrowingActive(rq)) {
            AiResolvedOrgScope rebuilt =
                    buildBaselineGroupOrgScopeForRequest(ctx, state.getDepartmentId(), syn);
            if (rebuilt != null && AiResolvedOrgScope.SCOPE_GROUP.equals(rebuilt.getScopeType())) {
                rq.setOrgScope(rebuilt);
                org = rebuilt;
                ScopeResolutionTrace trace = rq.getScopeResolutionTrace();
                if (trace != null) {
                    trace.setPostIntersectOrgScopeType(rebuilt.getScopeType());
                    trace.setScopeIntersectPath("PATCH_RECOVERED_REQUEST_GROUP");
                }
            }
        }

        Long mergedDis = mergedDistributerId(syn, ctx);
        Long prevDis = org.getDistributerId();

        org.setDistributerId(mergedDis);
        org.setRequestDepartmentId(state.getDepartmentId());

        String scopeType = org.getScopeType();

        if (AiResolvedOrgScope.SCOPE_GROUP.equals(scopeType)) {
            org.setCurrentDepartmentId(ctx.getDepartmentId());
            boolean semanticStoreNarrowing =
                    SemanticStoreNarrowingScopeSupport.isSemanticStoreNarrowingActive(rq);
            if (mergedDis != null && prevDis != null && !Objects.equals(prevDis, mergedDis) && !semanticStoreNarrowing) {
                try {
                    int disPk = Math.toIntExact(mergedDis);
                    List<AiStoreScopeDTO> stores = loadStoreScopeDtosUnderDistributer(disPk);
                    org.setVisibleStores(stores);
                    org.setQueryScopeBanner(
                            "集团范围：共识别 " + stores.size() + " 家门店（gb_department_father_id=0）");
                } catch (ArithmeticException ex) {
                    org.setVisibleStores(new ArrayList<>());
                    org.setQueryScopeBanner("集团范围：distributerId 无法用于部门表查询");
                }
            }
        } else if (AiResolvedOrgScope.SCOPE_STORE.equals(scopeType)
                || AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType)) {
            AiStoreScopeDTO anchored = anchorStoreRowForIntersectPatch(org, state);
            if (anchored != null && anchored.getStoreDepartmentId() != null && anchored.getStoreDepartmentId() > 0) {
                AiStoreScopeDTO row = enrichStoreDepartmentNameFromDbIfNeeded(anchored);
                List<AiStoreScopeDTO> concrete = new ArrayList<>();
                concrete.add(row);
                org.setVisibleStores(concrete);
                long sid = row.getStoreDepartmentId();
                org.setCurrentStoreDepartmentId(sid);
                org.setCurrentDepartmentId(sid);
                org.setRequestDepartmentId(sid);
                state.setDepartmentId(sid);
                String labelPrefix =
                        AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType) ? "门店采购" : "门店";
                if (storeBannerLooksBrokenForIntersectPatch(org.getQueryScopeBanner())) {
                    if (StringUtils.hasText(row.getStoreName())) {
                        org.setQueryScopeBanner(labelPrefix + "：" + row.getStoreName().trim());
                    } else {
                        org.setQueryScopeBanner(labelPrefix + "：部门 " + sid);
                    }
                }
                tightenRunQueryScopeAroundStoreSubtree(state, sid);
            } else {
                NormalizedDept n = normalizeStoreAnchor(state.getDepartmentId());
                org.setCurrentStoreDepartmentId(n.storeDepartmentId());
                org.setCurrentDepartmentId(state.getDepartmentId());
                List<AiStoreScopeDTO> stores = new ArrayList<>();
                if (n.storeDepartmentId() != null) {
                    stores.add(AiStoreScopeDTO.builder()
                            .storeDepartmentId(n.storeDepartmentId())
                            .storeName(n.storeName())
                            .build());
                }
                org.setVisibleStores(stores);
                String label = AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType) ? "门店采购" : "门店";
                org.setQueryScopeBanner(n.storeName() != null
                        ? label + "：" + n.storeName()
                        : label + "：部门 " + state.getDepartmentId());
            }
        } else if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(scopeType)) {
            AiResolvedOrgScope rebuilt = buildWarehouseScope(ctx, state.getDepartmentId(), syn);
            rq.setOrgScope(rebuilt);
            rq.setDataScope(buildDataScope(rebuilt));
            rq.setQueryScopeBanner(rebuilt.getQueryScopeBanner());
            return;
        } else {
            AiResolvedOrgScope rebuilt = buildDepartmentLikeScope(ctx, state.getDepartmentId(), scopeType, syn);
            rq.setOrgScope(rebuilt);
            rq.setDataScope(buildDataScope(rebuilt));
            rq.setQueryScopeBanner(rebuilt.getQueryScopeBanner());
            return;
        }

        rq.setDataScope(buildDataScope(org));
        rq.setQueryScopeBanner(org.getQueryScopeBanner());
        ScopeResolutionTrace trace = rq.getScopeResolutionTrace();
        if (trace != null) {
            trace.setPostIntersectOrgScopeType(org.getScopeType());
            if (trace.getScopeIntersectPath() == null) {
                trace.setScopeIntersectPath("PATCH_STORE_SUBTREE");
            }
            trace.snapshotDataScope(org, rq.getDataScope());
        }
    }

    /**
     * 集团广角会话下语义已收窄 {@link AiResolvedOrgScope#SCOPE_STORE} 时，
     * {@link AiRunState#getDepartmentId()} 可能仍为 null；此处优先保留 org.visibleStores 中已落地的门店根行。
     */
    private AiStoreScopeDTO anchorStoreRowForIntersectPatch(AiResolvedOrgScope org, AiRunState state) {
        AiStoreScopeDTO row = extractFirstConcreteStoreDepartmentRow(org != null ? org.getVisibleStores() : null);
        if (row != null) {
            return row;
        }
        Long deptId = state != null ? state.getDepartmentId() : null;
        NormalizedDept n = normalizeStoreAnchor(deptId);
        if (n.storeDepartmentId() == null) {
            return null;
        }
        return AiStoreScopeDTO.builder()
                .storeDepartmentId(n.storeDepartmentId())
                .storeName(n.storeName())
                .build();
    }

    private static AiStoreScopeDTO extractFirstConcreteStoreDepartmentRow(List<AiStoreScopeDTO> visibleStores) {
        if (visibleStores == null) {
            return null;
        }
        for (AiStoreScopeDTO s : visibleStores) {
            if (s != null && s.getStoreDepartmentId() != null && s.getStoreDepartmentId() > 0) {
                return s;
            }
        }
        return null;
    }

    private AiStoreScopeDTO enrichStoreDepartmentNameFromDbIfNeeded(AiStoreScopeDTO row) {
        if (row == null || row.getStoreDepartmentId() == null) {
            return row;
        }
        if (StringUtils.hasText(row.getStoreName())) {
            return row;
        }
        long sid = row.getStoreDepartmentId();
        if (sid <= 0 || sid > Integer.MAX_VALUE) {
            return row;
        }
        GbDepartmentEntity dep = gbDepartmentMapper.selectById((int) sid);
        String nm = dep != null ? dep.getGbDepartmentName() : null;
        return AiStoreScopeDTO.builder()
                .storeDepartmentId(sid)
                .storeName(nm)
                .build();
    }

    private static boolean storeBannerLooksBrokenForIntersectPatch(String banner) {
        if (!StringUtils.hasText(banner)) {
            return true;
        }
        return banner.contains("部门 null");
    }

    /**
     * 单店语义落地后重写 Run scope：对齐 {@link AiRunScopeIntersectService} 的 STORE subtree 快照，
     * 避免 Revenue 等 Tool 拿不到 {@link AiQueryScope#getDepartmentFatherId()}。
     */
    private void tightenRunQueryScopeAroundStoreSubtree(AiRunState state, long storeRootDepartmentId) {
        if (state == null) {
            return;
        }
        int sidInt;
        try {
            sidInt = Math.toIntExact(storeRootDepartmentId);
        } catch (ArithmeticException ex) {
            return;
        }
        List<Integer> subtree = scopeResolver.collectSubtreeDepartmentIds(sidInt, null);
        if (subtree == null || subtree.isEmpty()) {
            subtree = List.of(sidInt);
        }
        List<Integer> sorted =
                subtree.stream().filter(Objects::nonNull).sorted().distinct().collect(Collectors.toList());
        AiUserContext ctx = state.getAiUserContext();
        Long anchorMem = ctx != null ? ctx.getDepartmentId() : null;
        int disInt = state.getDistributerId() != null ? state.getDistributerId().intValue() : 0;
        Map<Integer, Integer> counts =
                sorted.isEmpty() ? Map.of() : scopeResolver.departmentTypeCountsForIds(sorted);
        AiQueryScope qs = AiQueryScope.builder()
                .mode(AiConversationScopeMode.STORE)
                .departmentFatherId(storeRootDepartmentId)
                .distributerId(state.getDistributerId())
                .disIdForPurchaseQueries(disInt)
                .resolvedDepartmentIds(List.copyOf(sorted))
                .departmentTypeCounts(counts != null ? counts : Map.of())
                .parentStoreCount(sorted.isEmpty() ? 0 : 1)
                .userMemoryAnchorDepartmentId(anchorMem)
                .groupRevenueUseDistributerWideQuery(false)
                .build();
        state.setScope(qs);
    }

    private List<AiStoreScopeDTO> loadStoreScopeDtosUnderDistributer(int disPk) {
        List<Integer> storeIds = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(disPk);
        List<AiStoreScopeDTO> stores = new ArrayList<>(storeIds.size());
        for (Integer sid : storeIds) {
            GbDepartmentEntity row = sid != null ? gbDepartmentMapper.selectById(sid) : null;
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(sid != null ? sid.longValue() : null)
                    .storeName(row != null ? row.getGbDepartmentName() : null)
                    .build());
        }
        return stores;
    }

    private AiResolvedOrgScope buildGroupScope(AiUserContext ctx, Long requestDepartmentId, AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        var b = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentDepartmentId(ctx.getDepartmentId())
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>());

        if (dis == null) {
            b.scopeName("集团（未解析 distributerId）")
                    .visibleStores(new ArrayList<>())
                    .queryScopeBanner("集团范围：缺少 distributerId，暂无法展开下属门店")
                    .coverageDetail("请确保 gb_department_user 挂靠 distributerId、或请求体传入 distributerId。");
            return b.build();
        }
        int disPk;
        try {
            disPk = Math.toIntExact(dis);
        } catch (ArithmeticException ex) {
            b.scopeName("集团（distributerId 超出 int 范围）")
                    .visibleStores(new ArrayList<>())
                    .queryScopeBanner("集团范围：distributerId 无法用于部门表查询")
                    .coverageDetail("distributerId=" + dis + " 超出 MyBatis 映射 int。");
            return b.build();
        }

        List<AiStoreScopeDTO> stores = loadStoreScopeDtosUnderDistributer(disPk);

        String banner = "集团范围：共识别 " + stores.size() + " 家门店（gb_department_father_id=0）";
        b.visibleStores(stores)
                .currentStoreDepartmentId(null)
                .scopeName("集团")
                .queryScopeBanner(banner)
                .coverageDetail("visibleStores 为权限内应可见门店根，非当日有营收门店。");
        return b.build();
    }

    private AiResolvedOrgScope buildStoreScope(AiUserContext ctx, Long requestDepartmentId, String scopeType,
                                               AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        NormalizedDept n = normalizeStoreAnchor(requestDepartmentId);
        List<AiStoreScopeDTO> stores = new ArrayList<>();
        if (n.storeDepartmentId() != null) {
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(n.storeDepartmentId())
                    .storeName(n.storeName())
                    .build());
        }
        String label = AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType) ? "门店采购" : "门店";
        String banner = n.storeName() != null
                ? label + "：" + n.storeName()
                : label + "：部门 " + requestDepartmentId;
        return AiResolvedOrgScope.builder()
                .scopeType(scopeType)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(n.storeDepartmentId())
                .currentDepartmentId(requestDepartmentId)
                .visibleStores(stores)
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>())
                .scopeName(label)
                .queryScopeBanner(banner)
                .coverageDetail("单门店可见范围。")
                .build();
    }

    private AiResolvedOrgScope buildWarehouseScope(AiUserContext ctx, Long requestDepartmentId,
                                                   AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        Long deptId = requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId();
        GbDepartmentEntity dep = departmentRow(deptId);
        List<AiDepartmentScopeDTO> wh = new ArrayList<>();
        Long father = null;
        if (dep != null) {
            Integer f = dep.getGbDepartmentFatherId();
            father = f != null ? f.longValue() : null;
            wh.add(AiDepartmentScopeDTO.builder()
                    .departmentId(dep.getGbDepartmentId() != null ? dep.getGbDepartmentId().longValue() : deptId)
                    .departmentName(dep.getGbDepartmentName())
                    .fatherId(father)
                    .build());
        } else if (deptId != null) {
            wh.add(AiDepartmentScopeDTO.builder()
                    .departmentId(deptId)
                    .departmentName(null)
                    .fatherId(null)
                    .build());
        }

        Long storeAnchor = (father != null && father > 0L) ? father : null;

        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_WAREHOUSE)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(storeAnchor)
                .currentDepartmentId(deptId)
                .visibleStores(new ArrayList<>())
                .visibleWarehouses(wh)
                .visibleDepartments(new ArrayList<>(wh))
                .scopeName("库房")
                .queryScopeBanner(dep != null && dep.getGbDepartmentName() != null
                        ? "本库房：" + dep.getGbDepartmentName()
                        : "本库房/部门：" + deptId)
                .coverageDetail("库房视角：仅本人所在库房/部门，不展开集团全部门店库存。")
                .build();
    }

    private AiResolvedOrgScope buildDepartmentLikeScope(AiUserContext ctx, Long requestDepartmentId, String scopeType,
                                                        AiRunCreateRequest request) {
        Long dis = mergedDistributerId(request, ctx);
        NormalizedDept n = normalizeStoreAnchor(requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId());
        List<AiStoreScopeDTO> stores = new ArrayList<>();
        if (n.storeDepartmentId() != null) {
            stores.add(AiStoreScopeDTO.builder()
                    .storeDepartmentId(n.storeDepartmentId())
                    .storeName(n.storeName())
                    .build());
        }
        return AiResolvedOrgScope.builder()
                .scopeType(scopeType)
                .distributerId(dis)
                .requestDepartmentId(requestDepartmentId)
                .currentStoreDepartmentId(n.storeDepartmentId())
                .currentDepartmentId(requestDepartmentId != null ? requestDepartmentId : ctx.getDepartmentId())
                .visibleStores(stores)
                .visibleWarehouses(new ArrayList<>())
                .visibleDepartments(new ArrayList<>())
                .scopeName("部门")
                .queryScopeBanner(n.storeName() != null ? "可见门店：" + n.storeName() : "组织范围：待解析")
                .coverageDetail("非 0/1/3/11 角色的兜底：按挂靠部门归一化门店锚点。")
                .build();
    }

    private NormalizedDept normalizeStoreAnchor(Long departmentId) {
        GbDepartmentEntity dep = departmentRow(departmentId);
        if (dep == null) {
            return new NormalizedDept(departmentId, null);
        }
        Integer father = dep.getGbDepartmentFatherId();
        if (father == null || father == 0) {
            long sid = dep.getGbDepartmentId() != null ? dep.getGbDepartmentId().longValue() : departmentId;
            return new NormalizedDept(sid, dep.getGbDepartmentName());
        }
        GbDepartmentEntity store = gbDepartmentMapper.selectById(father);
        long sid = father.longValue();
        return new NormalizedDept(sid, store != null ? store.getGbDepartmentName() : null);
    }

    private GbDepartmentEntity departmentRow(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        if (departmentId > Integer.MAX_VALUE || departmentId < Integer.MIN_VALUE) {
            return null;
        }
        return gbDepartmentMapper.selectById(departmentId.intValue());
    }
}
