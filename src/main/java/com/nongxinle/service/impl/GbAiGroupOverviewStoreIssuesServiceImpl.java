package com.nongxinle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongxinle.ai.dto.business.AiGroupOverviewStoreBrief;
import com.nongxinle.ai.dto.business.AiOverviewCoveredStoreItem;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.business.AiOverviewVisibleStoreItem;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.dto.AiRecordingDeptRevenueAggRow;
import com.nongxinle.dto.DepartmentPurchaseAggRow;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbAiDailyRevenueMapper;
import com.nongxinle.mapper.GbDepartmentMapper;
import com.nongxinle.mapper.GbDistributerPurchaseGoodsMapper;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbAiGroupOverviewStoreIssuesService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @see GbAiGroupOverviewStoreIssuesService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbAiGroupOverviewStoreIssuesServiceImpl implements GbAiGroupOverviewStoreIssuesService {

    private final GbAiDailyRevenueMapper revenueMapper;
    private final GbDistributerPurchaseGoodsMapper purchaseGoodsMapper;
    private final GbDepartmentMapper departmentMapper;
    private final GbAiRestaurantProfileService profileService;
    private final AiScopeResolver scopeResolver;
    private final GbAiDailyRevenueService dailyRevenueService;

    @Override
    public BuiltGroupOverviewStoreIssues buildGroupStoreIssuesSnapshot(String aiRoleCode,
            List<Integer> resolvedDepartmentIds, String startDate, String stopDate,
            Long anchorDepartmentIdForDisLookup) {

        if (!AiRoleMapper.isGroupWideOrgScope(aiRoleCode)) {
            return BuiltGroupOverviewStoreIssues.empty();
        }
        List<Integer> resolved = sanitizeIds(resolvedDepartmentIds);
        if (resolved.isEmpty()) {
            return BuiltGroupOverviewStoreIssues.empty();
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate.trim());
            end = LocalDate.parse(stopDate.trim());
        } catch (Exception e) {
            log.warn("[GroupStoreIssues] bad date range start={} stop={}: {}", startDate, stopDate, e.toString());
            return BuiltGroupOverviewStoreIssues.empty();
        }

        Integer distributorId = resolveDistributorId(anchorDepartmentIdForDisLookup, resolved);

        List<Integer> retailAnchors = scopeResolver.listDomainStoreAnchorsInResolved(resolved);
        if (retailAnchors.isEmpty()) {
            log.debug("[GroupStoreIssues] no retail anchors after union/ancestor-prune resolvedSize={}", resolved.size());
            return BuiltGroupOverviewStoreIssues.empty();
        }

        Set<Integer> resolvedSet = new HashSet<>(resolved);
        Map<Integer, List<Integer>> subtreeCache = new HashMap<>();
        Map<Integer, String> deptNameCache = preloadNames(new HashSet<>(retailAnchors));

        Map<Integer, Set<Integer>> storeSubtreeInResolved = new LinkedHashMap<>();
        for (Integer retailId : retailAnchors) {
            List<Integer> sub = scopeResolver.collectSubtreeDepartmentIds(retailId, subtreeCache);
            Set<Integer> inScope = sub.stream().filter(resolvedSet::contains).collect(Collectors.toCollection(HashSet::new));
            if (!inScope.isEmpty()) {
                storeSubtreeInResolved.put(retailId, inScope);
            }
        }
        if (storeSubtreeInResolved.isEmpty()) {
            return BuiltGroupOverviewStoreIssues.empty();
        }

        Map<Long, List<Integer>> revenueScopeLong = dailyRevenueService.buildStoreRevenueQueryScopeByStoreRoot(retailAnchors);
        List<Integer> revenueQueryDepartmentIds = dailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(retailAnchors);
        Map<Integer, Set<Integer>> revenueScopeByRetail = new LinkedHashMap<>();
        if (revenueScopeLong != null) {
            for (Map.Entry<Long, List<Integer>> e : revenueScopeLong.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                revenueScopeByRetail.put(e.getKey().intValue(), new HashSet<>(e.getValue()));
            }
        }

        List<Integer> recWithRowsRaw =
                revenueMapper.selectDistinctRecordingDepartmentIdsInRange(revenueQueryDepartmentIds, start, end);
        Set<Integer> recordingWithRows =
                recWithRowsRaw == null ? Set.of() : new HashSet<>(recWithRowsRaw);

        List<AiRecordingDeptRevenueAggRow> revRows =
                revenueMapper.listRecordingDeptRevenueAggInRange(revenueQueryDepartmentIds, start, end);
        revRows = revRows == null ? List.of() : revRows;

        Set<Integer> retailSet = storeSubtreeInResolved.keySet();
        RevenueRollTotals revenueByRetail = rollupRevenueToRetailAnchors(revRows, retailSet);
        PurchaseRollTotals purchaseByRetail =
                rollupPurchaseByRetailAnchors(resolved, start, end, distributorId, retailSet);

        List<AiOverviewStoreIssueItem> dataMissingStores = new ArrayList<>();
        Set<Integer> dataMissingRetailIds = new HashSet<>();

        for (Integer retailId : sortedRetailIds(storeSubtreeInResolved.keySet(), deptNameCache)) {
            Set<Integer> subRes = storeSubtreeInResolved.get(retailId);
            if (subRes == null || subRes.isEmpty()) {
                continue;
            }
            Set<Integer> revScope = revenueScopeByRetail.getOrDefault(retailId, Set.of());
            boolean hasRecordingInSubtree = !disjoint(revScope, recordingWithRows);
            boolean profileOk = profileService.getByDepartmentId(retailId.longValue()) != null;
            BigDecimal purRetail = nz(purchaseByRetail.purchaseByRetail.get(retailId));

            List<String> reasons = new ArrayList<>(3);
            if (!profileOk) {
                reasons.add("餐厅画像未配置");
            }
            if (!hasRecordingInSubtree) {
                reasons.add("暂无日营收记录");
            }
            if (purRetail.signum() > 0 && !hasRecordingInSubtree) {
                reasons.add("有采购入库记录但本子树未见日营收台账，请核对销售与日营收录入");
            }
            GrossOrdersDays revRoll = revenueByRetail.grossOrdersDaysByRetail.get(retailId);
            boolean salesLooksEmpty =
                    revRoll == null || revRoll.gross.signum() <= 0 || nz(revRoll.orders).signum() <= 0;
            if (profileOk && hasRecordingInSubtree && salesLooksEmpty && purRetail.signum() > 0) {
                reasons.add("有采购入库但销售/订单汇总为空，请核对菜品销售与日营收是否录入一致");
            }
            if (reasons.isEmpty()) {
                continue;
            }

            dataMissingRetailIds.add(retailId);
            dataMissingStores.add(AiOverviewStoreIssueItem.builder()
                    .storeName(nick(deptNameCache, retailId))
                    .reason(String.join("；", reasons))
                    .riskLevel(null)
                    .build());
        }

        List<AiOverviewStoreIssueItem> attentionStores = buildAttentionStores(
                retailSet,
                dataMissingRetailIds,
                deptNameCache,
                revenueByRetail.grossOrdersDaysByRetail,
                purchaseByRetail.purchaseByRetail);

        List<AiOverviewVisibleStoreItem> visibleStores = buildVisibleStoreItems(
                sortedRetailIds(storeSubtreeInResolved.keySet(), deptNameCache), deptNameCache);

        List<AiOverviewCoveredStoreItem> coveredStores = buildCoveredStoresRollup(
                sortedRetailIds(storeSubtreeInResolved.keySet(), deptNameCache),
                revenueScopeByRetail,
                recordingWithRows,
                revenueByRetail.grossOrdersDaysByRetail,
                deptNameCache);

        String brief = AiGroupOverviewStoreBrief.formatPriorityBrief(dataMissingStores, attentionStores);
        if (brief == null) {
            brief = AiGroupOverviewStoreBrief.noIssuesLine();
        }
        return new BuiltGroupOverviewStoreIssues(dataMissingStores, attentionStores, brief, visibleStores, coveredStores);
    }

    private RevenueRollTotals rollupRevenueToRetailAnchors(List<AiRecordingDeptRevenueAggRow> revRows,
            Set<Integer> retailSet) {

        Map<Integer, BigDecimal> gross = new HashMap<>();
        Map<Integer, BigDecimal> orders = new HashMap<>();
        Map<Integer, BigDecimal> takeout = new HashMap<>();
        Map<Integer, BigDecimal> platform = new HashMap<>();
        Map<Integer, Integer> maxDistinctDays = new HashMap<>();
        Map<Integer, Integer> climbHitCache = new HashMap<>();
        Map<Integer, Boolean> climbMissCache = new HashMap<>();

        for (AiRecordingDeptRevenueAggRow row : revRows) {
            Integer recDeptId = row.getDepartmentId();
            if (recDeptId == null) {
                continue;
            }
            Integer retailParent =
                    climbToRetailAncestor(recDeptId, retailSet, climbHitCache, climbMissCache);
            if (retailParent == null) {
                continue;
            }

            gross.merge(retailParent, nz(row.getGrossRevenue()), BigDecimal::add);
            orders.merge(retailParent, nz(row.getTotalOrders()), BigDecimal::add);
            takeout.merge(retailParent, nz(row.getTakeoutRevenue()), BigDecimal::add);
            platform.merge(retailParent, nz(row.getPlatformFee()), BigDecimal::add);

            int dDays = Math.max(0, row.getDistinctRecordDates() == null ? 0 : row.getDistinctRecordDates());
            maxDistinctDays.merge(retailParent, dDays, Math::max);
        }

        Map<Integer, GrossOrdersDays> merged = new HashMap<>();
        for (Integer rid : gross.keySet()) {
            merged.put(rid,
                    new GrossOrdersDays(gross.get(rid), nz(orders.get(rid)),
                            maxDistinctDays.getOrDefault(rid, 0),
                            nz(takeout.get(rid)),
                            nz(platform.get(rid))));
        }
        return new RevenueRollTotals(merged);
    }

    private PurchaseRollTotals rollupPurchaseByRetailAnchors(List<Integer> resolved, LocalDate start,
            LocalDate end, Integer distributorId, Set<Integer> retailAnchorsForRollup) {

        Map<Integer, BigDecimal> rolled = new HashMap<>();
        if (distributorId == null || distributorId <= 0) {
            return new PurchaseRollTotals(rolled);
        }

        HashMap<String, Object> pmap = new HashMap<>(8);
        pmap.put("disId", distributorId.intValue());
        pmap.put("startDate", start.toString());
        pmap.put("stopDate", end.toString());
        pmap.put("purDepIds", resolved);

        List<DepartmentPurchaseAggRow> rows =
                purchaseGoodsMapper.sumPurchaseSubtotalGroupedByPurDepartmentId(pmap);
        rows = rows == null ? List.of() : rows;

        Map<Integer, Integer> climbHitCache = new HashMap<>();
        Map<Integer, Boolean> climbMissCache = new HashMap<>();
        for (DepartmentPurchaseAggRow row : rows) {
            Integer purDeptId = row.getDepartmentId();
            if (purDeptId == null) {
                continue;
            }
            Integer retailParent =
                    climbToRetailAncestor(purDeptId, retailAnchorsForRollup, climbHitCache, climbMissCache);
            if (retailParent == null) {
                continue;
            }
            BigDecimal sub = nz(row.getPurchaseSubtotal());
            rolled.merge(retailParent, sub, BigDecimal::add);
        }
        return new PurchaseRollTotals(rolled);
    }

    private static List<AiOverviewStoreIssueItem> buildAttentionStores(Set<Integer> allRetailAnchors,
            Set<Integer> dataMissingRetailIds,
            Map<Integer, String> deptNameCache,
            Map<Integer, GrossOrdersDays> agg,
            Map<Integer, BigDecimal> purchaseByRetail) {

        List<AiOverviewStoreIssueItem> attention = new ArrayList<>();
        for (Integer retailId : sortedRetailIds(allRetailAnchors, deptNameCache)) {
            if (dataMissingRetailIds.contains(retailId)) {
                continue;
            }
            GrossOrdersDays g = agg.get(retailId);
            if (g == null || g.gross.signum() <= 0) {
                continue;
            }

            BigDecimal pur = nz(purchaseByRetail.get(retailId));
            List<String> reasonParts = new ArrayList<>(4);
            String riskLevel = "";

            if (pur.signum() > 0 && pur.compareTo(g.gross) > 0) {
                reasonParts.add("采购入库额高于营业额，需要结合库存出库核对");
                riskLevel =
                        pur.compareTo(g.gross.multiply(BigDecimal.valueOf(2))) > 0 ? "high" : bumpRisk(riskLevel, "warning");
            }

            int maxDays = Math.max(1, g.maxDistinctDays);
            BigDecimal avgDailyGross =
                    g.gross.divide(BigDecimal.valueOf(maxDays), 2, RoundingMode.HALF_UP);
            if (g.maxDistinctDays >= 2 && avgDailyGross.compareTo(BigDecimal.valueOf(150)) < 0
                    && g.gross.compareTo(BigDecimal.valueOf(1200)) < 0) {
                reasonParts.add("营业额偏低（按有账日折算日均营业额）");
                riskLevel = bumpRisk(riskLevel, "warning");
            }

            if (g.maxDistinctDays >= 2 && g.gross.compareTo(BigDecimal.valueOf(200)) >= 0) {
                BigDecimal avgOrders =
                        g.orders.divide(BigDecimal.valueOf(maxDays), 2, RoundingMode.HALF_UP);
                if (avgOrders.compareTo(BigDecimal.valueOf(12)) < 0) {
                    reasonParts.add("日均订单数偏低，建议排查客流与复购");
                    riskLevel = bumpRisk(riskLevel, "warning");
                }
            }

            if (nz(g.orders).signum() > 0) {
                BigDecimal revenuePerOrder =
                        g.gross.divide(g.orders.max(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
                if (nz(g.orders).compareTo(BigDecimal.valueOf(5)) >= 0
                        && revenuePerOrder.compareTo(BigDecimal.valueOf(22)) < 0) {
                    reasonParts.add("客单价偏低，留意低价引流或录入口径");
                    riskLevel = bumpRisk(riskLevel, "warning");
                }
                if (revenuePerOrder.compareTo(BigDecimal.valueOf(600)) > 0) {
                    reasonParts.add("客单价偏高，请核对是否含大单或录入误差");
                    riskLevel = bumpRisk(riskLevel, "warning");
                }
            }

            BigDecimal takeout = nz(g.takeoutRevenue);
            BigDecimal plat = nz(g.platformFee);
            if (takeout.signum() > 0 && plat.compareTo(takeout) > 0) {
                reasonParts.add("外卖营业额低于平台/营销相关费用合计，需核对费用是否混入了堂食或全店分摊");
                riskLevel = bumpRisk(riskLevel, "warning");
            }

            if (reasonParts.isEmpty()) {
                continue;
            }
            attention.add(AiOverviewStoreIssueItem.builder()
                    .storeName(nick(deptNameCache, retailId))
                    .reason(String.join("；", reasonParts))
                    .riskLevel(riskLevel.isBlank() ? "warning" : riskLevel)
                    .build());
        }
        return attention;
    }

    private List<AiOverviewVisibleStoreItem> buildVisibleStoreItems(List<Integer> sortedRetailIds,
            Map<Integer, String> deptNameCache) {
        List<AiOverviewVisibleStoreItem> out = new ArrayList<>(sortedRetailIds.size());
        for (Integer retailId : sortedRetailIds) {
            out.add(AiOverviewVisibleStoreItem.builder()
                    .storeDepartmentId(retailId == null ? null : retailId.longValue())
                    .storeName(nick(deptNameCache, retailId))
                    .build());
        }
        return out;
    }

    private List<AiOverviewCoveredStoreItem> buildCoveredStoresRollup(List<Integer> sortedRetailIds,
            Map<Integer, Set<Integer>> revenueScopeByRetail,
            Set<Integer> recordingWithRows,
            Map<Integer, GrossOrdersDays> aggByRetail,
            Map<Integer, String> deptNameCache) {

        List<AiOverviewCoveredStoreItem> out = new ArrayList<>(sortedRetailIds.size());
        for (Integer retailId : sortedRetailIds) {
            Set<Integer> revScope = revenueScopeByRetail.getOrDefault(retailId, Set.of());
            boolean hasRecordingInSubtree = !disjoint(revScope, recordingWithRows);
            GrossOrdersDays g = aggByRetail.get(retailId);
            BigDecimal gross = g != null ? nz(g.gross) : BigDecimal.ZERO;
            BigDecimal orders = g != null ? nz(g.orders) : BigDecimal.ZERO;
            int dDays = g != null ? Math.max(0, g.maxDistinctDays) : 0;
            BigDecimal avgDailyOrd =
                    dDays > 0 ? orders.divide(BigDecimal.valueOf(dDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal avgPerCust =
                    orders.signum() > 0 ? gross.divide(orders, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            boolean hasRevData =
                    hasRecordingInSubtree && gross.signum() > 0 && orders.signum() > 0;
            if (!hasRevData) {
                continue;
            }
            out.add(AiOverviewCoveredStoreItem.builder()
                    .storeName(nick(deptNameCache, retailId))
                    .hasRevenueData(hasRevData)
                    .totalRevenue(gross)
                    .days(dDays)
                    .orderCount(orders)
                    .avgOrderCount(avgDailyOrd)
                    .avgPerCustomer(avgPerCust)
                    .build());
        }
        return out;
    }

    private static String bumpRisk(String current, String add) {
        if ("high".equals(current) || "high".equals(add)) {
            return "high";
        }
        return !current.isBlank() ? current : add;
    }

    private Integer resolveDistributorId(Long anchorDept, List<Integer> resolved) {
        if (anchorDept != null && anchorDept > 0) {
            Integer dis = distributorFromEntity(departmentMapper.selectById(anchorDept.intValue()));
            if (dis != null && dis > 0) {
                return dis;
            }
        }
        Integer fromRes = distributorIdFromResolvedFallback(resolved);
        return fromRes != null && fromRes > 0 ? fromRes : null;
    }

    private static Integer distributorFromEntity(GbDepartmentEntity d) {
        return d != null && d.getGbDepartmentDisId() != null ? d.getGbDepartmentDisId() : null;
    }

    private Integer distributorIdFromResolvedFallback(List<Integer> resolved) {
        if (resolved == null || resolved.isEmpty()) {
            return null;
        }
        Integer firstId = resolved.get(0);
        return distributorFromEntity(departmentMapper.selectById(firstId));
    }

    private Integer climbToRetailAncestor(int startDeptId, Set<Integer> retailAnchorsInScope,
            Map<Integer, Integer> hitCache,
            Map<Integer, Boolean> missCache) {

        Integer cached = hitCache.get(startDeptId);
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(missCache.get(startDeptId))) {
            return null;
        }

        Integer resolvedHit = climbToRetailAncestorUncached(startDeptId, retailAnchorsInScope, new HashSet<>());

        if (resolvedHit == null) {
            missCache.put(startDeptId, Boolean.TRUE);
            return null;
        }
        hitCache.put(startDeptId, resolvedHit);
        return resolvedHit;
    }

    /**
     * 沿父链向上查找「落在当前零售锚点集合中的最近祖先」。（记账/采购部门可能仅为子节点，其父级门店 id 仍可命中。）
     */
    private Integer climbToRetailAncestorUncached(int deptId, Set<Integer> retailAnchorsInScope,
            Set<Integer> visitGuard) {

        if (!visitGuard.add(deptId)) {
            return null;
        }

        if (retailAnchorsInScope.contains(deptId)) {
            return deptId;
        }

        GbDepartmentEntity row = departmentMapper.selectById(deptId);
        if (row == null || row.getGbDepartmentFatherId() == null || row.getGbDepartmentFatherId() <= 0) {
            return null;
        }
        return climbToRetailAncestorUncached(row.getGbDepartmentFatherId(), retailAnchorsInScope, visitGuard);
    }

    private static boolean disjoint(Set<Integer> a, Set<Integer> b) {
        if (a == null || b == null || a.isEmpty()) {
            return true;
        }
        for (Integer x : a) {
            if (b.contains(x)) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static List<Integer> sanitizeIds(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Integer, Boolean> uniq = new LinkedHashMap<>();
        for (Integer i : raw) {
            if (i != null && i > 0) {
                uniq.put(i, Boolean.TRUE);
            }
        }
        return new ArrayList<>(uniq.keySet());
    }

    private Map<Integer, String> preloadNames(Set<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<GbDepartmentEntity> rows = departmentMapper.selectList(
                new LambdaQueryWrapper<GbDepartmentEntity>().in(GbDepartmentEntity::getGbDepartmentId, ids));
        Map<Integer, String> out = new HashMap<>(ids.size());
        for (GbDepartmentEntity e : rows) {
            if (e.getGbDepartmentId() != null) {
                out.put(e.getGbDepartmentId(),
                        e.getGbDepartmentName() == null ? "" : e.getGbDepartmentName().trim());
            }
        }
        return out;
    }

    private static String nick(Map<Integer, String> names, int id) {
        String n = names.get(id);
        return n == null || n.isBlank() ? ("门店#" + id) : n.trim();
    }

    private static List<Integer> sortedRetailIds(Set<Integer> ids, Map<Integer, String> names) {
        return ids.stream()
                .sorted(java.util.Comparator.comparing(id -> nick(names, id), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static final class RevenueRollTotals {
        /** 每个零售锚点：毛利额营业额、订单、各支流 max 有账天数 */
        final Map<Integer, GrossOrdersDays> grossOrdersDaysByRetail;

        RevenueRollTotals(Map<Integer, GrossOrdersDays> merged) {
            this.grossOrdersDaysByRetail = merged == null ? Map.of() : merged;
        }
    }

    private record GrossOrdersDays(
            BigDecimal gross,
            BigDecimal orders,
            int maxDistinctDays,
            BigDecimal takeoutRevenue,
            BigDecimal platformFee) {
    }

    private static final class PurchaseRollTotals {

        final Map<Integer, BigDecimal> purchaseByRetail;

        PurchaseRollTotals(Map<Integer, BigDecimal> purchaseByRetail) {
            this.purchaseByRetail = purchaseByRetail == null ? Map.of() : purchaseByRetail;
        }
    }
}
