package com.nongxinle.ai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织/部门查询范围：主口径与 SQL 展开口径分离；<b>禁止</b>用单一混合数组同时表示「门店 + 子部门」的语义。
 * <p>
 * 主查询维度（互斥，由 {@link #queryScopeKind} 指定）：
 * <ul>
 *   <li>{@link #QUERY_SCOPE_KIND_STORE}：只用 {@link #queryStoreIds}（门店 rootId），子部门仅通过 {@link #storeToDepartmentIds} 辅助说明</li>
 *   <li>{@link #QUERY_SCOPE_KIND_DEPARTMENT}：只用 {@link #queryRealDepartmentIds}（真实部门 id，不含门店 root 混入）</li>
 *   <li>{@link #QUERY_SCOPE_KIND_DISTRIBUTER}：只用 {@link #queryDistributerId}（单值主体）</li>
 * </ul>
 * 业务表 {@code department_id IN (...)} 所需展开列表见 {@link #expandedSqlDepartmentIds}（解析层预计算），与「本轮主查询是按店/按部门/按主体」语义解耦。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResolvedDataScope {

    public static final String QUERY_SCOPE_KIND_STORE = "STORE";
    public static final String QUERY_SCOPE_KIND_DEPARTMENT = "DEPARTMENT";
    public static final String QUERY_SCOPE_KIND_DISTRIBUTER = "DISTRIBUTER";

    /** 仅 IN 门店根 ID，无任何子部门展开（预留） */
    public static final String QUERY_SCOPE_STORE_ROOT_ONLY = "STORE_ROOT_ONLY";
    /** 解析层对门店口径做了「根 ∪ 直属子」SQL 展开，见 {@link #expandedSqlDepartmentIds} */
    public static final String QUERY_SCOPE_STORE_ROOTS_AND_DIRECT_CHILDREN = "STORE_ROOTS_AND_DIRECT_CHILDREN";
    /**
     * 单店/门店根已落地的简化观测值（与非 EMPTY distributor 相对的 SUCCESS 语义）；仍为根∪直属子展开。
     */
    public static final String QUERY_SCOPE_MODE_STORE = "STORE";
    /** 库房：按部门 id 记账 */
    public static final String QUERY_SCOPE_WAREHOUSE_DEPARTMENT = "WAREHOUSE_DEPARTMENT";
    public static final String QUERY_SCOPE_DEPARTMENT_EXPLICIT = "DEPARTMENT_EXPLICIT";
    public static final String QUERY_SCOPE_EMPTY = "EMPTY";

    public static final String SQL_DOMAIN_REVENUE = "revenue";
    public static final String SQL_DOMAIN_PURCHASE = "purchase";
    public static final String SQL_DOMAIN_STOCK = "stock";
    public static final String SQL_DOMAIN_DISH_PROFIT = "dish_profit";
    public static final String SQL_DOMAIN_STOCK_REDUCE = "stock_reduce";

    /**
     * 主查询：{@link #QUERY_SCOPE_KIND_STORE} / {@link #QUERY_SCOPE_KIND_DEPARTMENT} / {@link #QUERY_SCOPE_KIND_DISTRIBUTER}
     */
    private String queryScopeKind;

    /**
     * 仅在 {@link #queryScopeKind}{@code =}{@link #QUERY_SCOPE_KIND_STORE} 时有效：门店 rootId 列表，<b>不含</b>子部门。
     */
    @Builder.Default
    private List<Integer> queryStoreIds = new ArrayList<>();

    /**
     * 仅在 {@link #queryScopeKind}{@code =}{@link #QUERY_SCOPE_KIND_DEPARTMENT} 时有效：具体部门 id（可多选），<b>不得</b>混入门店 rootId。
     */
    @Builder.Default
    private List<Integer> queryRealDepartmentIds = new ArrayList<>();

    /**
     * 仅在 {@link #queryScopeKind}{@code =}{@link #QUERY_SCOPE_KIND_DISTRIBUTER} 时有效：单一组织/经销主体 id。
     */
    private Integer queryDistributerId;

    /**
     * 辅助映射：门店 root → 其直属子部门；不作主查询数组，仅说明结构。
     */
    @Builder.Default
    private Map<Integer, List<Integer>> storeToDepartmentIds = new LinkedHashMap<>();

    /**
     * 供业务 SQL 按 {@code department_id} 类字段 IN 的展开列表（如 门店根∪子部门）；与「主查询维度」拆分，避免与门店列表混淆。
     */
    @Builder.Default
    private List<Integer> expandedSqlDepartmentIds = new ArrayList<>();

    /** 技术明细：门店根 + 子部门展开策略等 */
    private String queryScopeMode;

    /**
     * 本轮对用户展示、权限说明、门店覆盖率统计用的门店<b>根</b> ID（与 {@link AiResolvedOrgScope#getVisibleStores()} 一致）。
     */
    @Builder.Default
    private List<Long> visibleStoreIds = new ArrayList<>();

    @Builder.Default
    private List<Long> storeRootDepartmentIds = new ArrayList<>();

    @Deprecated
    @Builder.Default
    private List<Long> targetStoreIds = new ArrayList<>();

    @Builder.Default
    private List<Long> explicitChildDepartmentIds = new ArrayList<>();

    /** 与 {@link #storeToDepartmentIds} 同步的扁平子部门 id（不含根） */
    @Builder.Default
    private List<Long> expandedChildDepartmentIds = new ArrayList<>();

    @Deprecated
    @Builder.Default
    private Map<Long, List<Long>> storeToChildDepartmentIds = new LinkedHashMap<>();

    @Builder.Default
    private List<Long> visibleWarehouseIds = new ArrayList<>();

    @Deprecated
    @Builder.Default
    private List<Long> targetWarehouseIds = new ArrayList<>();

    @Builder.Default
    private List<Long> targetDepartmentIds = new ArrayList<>();

    @Builder.Default
    private List<Long> targetDishIds = new ArrayList<>();
    @Builder.Default
    private List<String> targetDishNames = new ArrayList<>();
    @Builder.Default
    private List<Long> targetGoodsIds = new ArrayList<>();
    @Builder.Default
    private List<String> targetGoodsNames = new ArrayList<>();
    @Builder.Default
    private List<Long> targetSupplierIds = new ArrayList<>();

    private boolean allVisibleStores;
    private boolean allVisibleWarehouses;

    /**
     * 业务表 {@code department_id} IN 展开（整型）；Tool 入参可再转 Long。
     */
    public List<Integer> getExpandedSqlDepartmentIds() {
        if (expandedSqlDepartmentIds == null || expandedSqlDepartmentIds.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(expandedSqlDepartmentIds);
    }

    /**
     * 与历史代码兼容：展开列表的 Long 视图（同 {@link #expandedSqlDepartmentIds}）。
     */
    public List<Long> resolveSqlQueryDepartmentIds() {
        return intListToLongList(expandedSqlDepartmentIds);
    }

    public List<Long> getEffectiveSqlDepartmentIds() {
        return new ArrayList<>(resolveSqlQueryDepartmentIds());
    }

    public List<Long> getVisibleStoreRootIds() {
        if (QUERY_SCOPE_KIND_STORE.equals(queryScopeKind) && queryStoreIds != null && !queryStoreIds.isEmpty()) {
            List<Long> out = new ArrayList<>(queryStoreIds.size());
            for (Integer id : queryStoreIds) {
                if (id != null) {
                    out.add(id.longValue());
                }
            }
            return out;
        }
        return new ArrayList<>(resolveStoreRootDepartmentIds());
    }

    public List<Long> getChildDepartmentIds() {
        if (expandedChildDepartmentIds != null && !expandedChildDepartmentIds.isEmpty()) {
            return new ArrayList<>(expandedChildDepartmentIds);
        }
        List<Long> flat = new ArrayList<>();
        if (storeToDepartmentIds != null) {
            for (List<Integer> cs : storeToDepartmentIds.values()) {
                if (cs == null) {
                    continue;
                }
                for (Integer c : cs) {
                    if (c != null) {
                        flat.add(c.longValue());
                    }
                }
            }
        }
        return flat;
    }

    public Map<Long, List<Long>> getStoreToChildDepartmentIds() {
        Map<Integer, List<Integer>> src = getStoreToDepartmentIds();
        LinkedHashMap<Long, List<Long>> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : src.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            List<Long> row = new ArrayList<>();
            if (e.getValue() != null) {
                for (Integer i : e.getValue()) {
                    if (i != null) {
                        row.add(i.longValue());
                    }
                }
            }
            out.put(e.getKey().longValue(), row);
        }
        return out;
    }

    public Map<Integer, List<Integer>> getStoreToDepartmentIds() {
        if (storeToDepartmentIds == null || storeToDepartmentIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<Integer, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : storeToDepartmentIds.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            copy.put(e.getKey(), e.getValue() != null ? new ArrayList<>(e.getValue()) : new ArrayList<>());
        }
        return copy;
    }

    public List<Long> getSqlDepartmentIdsForDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return new ArrayList<>();
        }
        if (QUERY_SCOPE_WAREHOUSE_DEPARTMENT.equals(queryScopeMode)) {
            return visibleWarehouseIds == null ? new ArrayList<>() : new ArrayList<>(visibleWarehouseIds);
        }
        return new ArrayList<>(resolveSqlQueryDepartmentIds());
    }

    public List<Long> resolveStoreRootDepartmentIds() {
        if (storeRootDepartmentIds != null && !storeRootDepartmentIds.isEmpty()) {
            return storeRootDepartmentIds;
        }
        if (visibleStoreIds != null && !visibleStoreIds.isEmpty()) {
            return visibleStoreIds;
        }
        return targetStoreIds != null ? targetStoreIds : new ArrayList<>();
    }

    private static List<Long> intListToLongList(List<Integer> in) {
        if (in == null || in.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> out = new ArrayList<>(in.size());
        for (Integer i : in) {
            if (i != null) {
                out.add(i.longValue());
            }
        }
        return out;
    }

    /** @deprecated 使用 {@link com.nongxinle.ai.context.AiResolvedDataScope} 的统一构建路径 */
    @Deprecated
    public static AiResolvedDataScope fromOrgScope(AiResolvedOrgScope org) {
        if (org == null) {
            return AiResolvedDataScope.builder()
                    .queryScopeKind(QUERY_SCOPE_KIND_STORE)
                    .queryScopeMode(QUERY_SCOPE_EMPTY)
                    .queryStoreIds(new ArrayList<>())
                    .queryRealDepartmentIds(new ArrayList<>())
                    .expandedSqlDepartmentIds(new ArrayList<>())
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .build();
        }
        List<Long> storeIds = org.getVisibleStores() == null ? new ArrayList<>() : org.getVisibleStores().stream()
                .map(AiStoreScopeDTO::getStoreDepartmentId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(ArrayList::new));

        List<Long> whIds = org.getVisibleWarehouses() == null ? new ArrayList<>() : org.getVisibleWarehouses().stream()
                .map(AiDepartmentScopeDTO::getDepartmentId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(ArrayList::new));

        boolean allStores = AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType());
        boolean allWh = AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType()) && !whIds.isEmpty();

        List<Integer> qStores = longListToIntList(storeIds);
        List<Integer> expandedInt = new ArrayList<>();
        for (Long id : storeIds) {
            if (id != null && id <= Integer.MAX_VALUE) {
                expandedInt.add(id.intValue());
            }
        }
        for (Long id : whIds) {
            if (id != null && id <= Integer.MAX_VALUE) {
                expandedInt.add(id.intValue());
            }
        }

        if (allWh) {
            List<Integer> whInt = longListToIntList(whIds);
            return AiResolvedDataScope.builder()
                    .queryScopeKind(QUERY_SCOPE_KIND_DEPARTMENT)
                    .queryRealDepartmentIds(new ArrayList<>(whInt))
                    .queryStoreIds(new ArrayList<>())
                    .queryDistributerId(null)
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .expandedSqlDepartmentIds(new ArrayList<>(whInt))
                    .queryScopeMode(QUERY_SCOPE_WAREHOUSE_DEPARTMENT)
                    .visibleStoreIds(new ArrayList<>())
                    .storeRootDepartmentIds(new ArrayList<>())
                    .visibleWarehouseIds(new ArrayList<>(whIds))
                    .allVisibleStores(false)
                    .allVisibleWarehouses(true)
                    .build();
        }

        return AiResolvedDataScope.builder()
                .queryScopeKind(QUERY_SCOPE_KIND_STORE)
                .queryStoreIds(new ArrayList<>(qStores))
                .queryRealDepartmentIds(new ArrayList<>())
                .queryDistributerId(null)
                .storeToDepartmentIds(new LinkedHashMap<>())
                .expandedSqlDepartmentIds(new ArrayList<>(expandedInt))
                .queryScopeMode(QUERY_SCOPE_MODE_STORE)
                .visibleStoreIds(new ArrayList<>(storeIds))
                .storeRootDepartmentIds(new ArrayList<>(storeIds))
                .targetStoreIds(new ArrayList<>(storeIds))
                .visibleWarehouseIds(new ArrayList<>())
                .allVisibleStores(allStores)
                .allVisibleWarehouses(false)
                .build();
    }

    private static List<Integer> longListToIntList(List<Long> in) {
        List<Integer> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        for (Long id : in) {
            if (id != null && id > 0 && id <= Integer.MAX_VALUE) {
                out.add(id.intValue());
            }
        }
        return out;
    }
}
