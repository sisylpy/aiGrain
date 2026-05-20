package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Harness 用 {@link AiResolvedDataScope} 简化构造（不做 DB 子部门展开）；生产链路请用 Resolver {@code buildDataScope}。
 */
public final class AiHarnessDataScopeFixtures {

    private AiHarnessDataScopeFixtures() {
    }

    public static AiResolvedDataScope fromOrgScope(AiResolvedOrgScope org) {
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
        List<Long> storeIds =
                org.getVisibleStores() == null
                        ? new ArrayList<>()
                        : org.getVisibleStores().stream()
                                .map(AiStoreScopeDTO::getStoreDepartmentId)
                                .filter(id -> id != null)
                                .collect(Collectors.toCollection(ArrayList::new));

        List<Long> whIds =
                org.getVisibleWarehouses() == null
                        ? new ArrayList<>()
                        : org.getVisibleWarehouses().stream()
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
                    .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_DEPARTMENT)
                    .queryRealDepartmentIds(new ArrayList<>(whInt))
                    .queryStoreIds(new ArrayList<>())
                    .queryDistributerId(null)
                    .storeToDepartmentIds(new LinkedHashMap<>())
                    .expandedSqlDepartmentIds(new ArrayList<>(whInt))
                    .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_WAREHOUSE_DEPARTMENT)
                    .visibleStoreIds(new ArrayList<>())
                    .storeRootDepartmentIds(new ArrayList<>())
                    .visibleWarehouseIds(new ArrayList<>(whIds))
                    .allVisibleStores(false)
                    .allVisibleWarehouses(true)
                    .build();
        }

        return AiResolvedDataScope.builder()
                .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                .queryStoreIds(new ArrayList<>(qStores))
                .queryRealDepartmentIds(new ArrayList<>())
                .queryDistributerId(null)
                .storeToDepartmentIds(new LinkedHashMap<>())
                .expandedSqlDepartmentIds(new ArrayList<>(expandedInt))
                .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE)
                .visibleStoreIds(new ArrayList<>(storeIds))
                .storeRootDepartmentIds(new ArrayList<>(storeIds))
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
