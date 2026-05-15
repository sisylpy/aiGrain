package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.service.GbAiDailyRevenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevenueQueryTool implements AiTool {

    private final GbAiDailyRevenueService gbAiDailyRevenueService;
    private final AiScopeResolver aiScopeResolver;

    @Override
    public String name() {
        return AiBusinessToolIds.REVENUE_QUERY;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLongObj(args.get(ARG_DEPARTMENT_FATHER_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));
        boolean groupWideHint = Boolean.TRUE.equals(args.get(ARG_GROUP_WIDE_OVERVIEW_HINT));

        if (dept == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalRevenue", null);
            return ToolResult.builder()
                    .success(false)
                    .message("missing departmentFatherId/startDate/stopDate")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "参数不完整"))
                    .build();
        }

        try {
            if (groupWideHint) {
                ToolResult grouped = executeGroupWideRevenueAggregate(request, args, dept, start, stop);
                if (grouped != null) {
                    return grouped;
                }
            }

            Map<String, Object> stats = gbAiDailyRevenueService.getStatsByDepartmentId(dept, start, stop);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rawStats", stats);
            Number daysNum = stats.get("days") instanceof Number ? (Number) stats.get("days") : null;
            int days = daysNum == null ? 0 : Math.max(daysNum.intValue(), 0);
            BigDecimal totalRevenue = nz(stats.get("total_revenue"));
            data.put("days", days);
            data.put("totalRevenue", totalRevenue);
            data.put("avgDailyRevenue",
                    days > 0 ? totalRevenue.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

            List<Map<String, Object>> storeRanking =
                    buildStoreRevenueRanking(dept, start, stop, args, groupWideHint, request.getResolvedQueryContext());
            if (!storeRanking.isEmpty()) {
                data.put("storeRevenueRanking", storeRanking);
            }

            boolean mock = stats == null || stats.isEmpty();
            return ToolResult.builder()
                    .success(true)
                    .message(mock ? "no_rows" : "ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, dept, null, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[RevenueQueryTool] runId={} dept={} range={}..{}: {}",
                    request.getRunId(), dept, start, stop, e.toString(), e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("errorCode", "query_failed");
            data.putAll(AiBusinessToolResponses.mockPayload(e.getMessage()));
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, true, start, stop, dept, null, data,
                            "查询异常：半真实 mock"))
                    .build();
        }
    }

    /**
     * 与 {@link BusinessOverviewQueryTool#executeGroupWideOverview} 收入侧 rollup 对齐：门店根 →
     * {@link GbAiDailyRevenueService#expandStoreRootsToDailyRevenueScopeIds} →
     * {@link GbAiDailyRevenueService#getGroupIncomeAggregateForDepartmentIds}，
     * 保证 {@code totalRevenue/days} 与 {@link #buildStoreRevenueRanking} 各店合计一致。
     */
    private ToolResult executeGroupWideRevenueAggregate(ToolRequest request, Map<String, Object> args,
            Long dept, String start, String stop) {
        List<Integer> resolved = normalizeResolvedIds(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        if (resolved.isEmpty()) {
            resolved = List.of(dept.intValue());
        }
        List<Integer> retailAnchors = aiScopeResolver.listDomainStoreAnchorsInResolved(resolved);
        if (retailAnchors.isEmpty()) {
            return null;
        }
        List<Integer> revenueQueryScopeIds = gbAiDailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(retailAnchors);
        if (revenueQueryScopeIds == null || revenueQueryScopeIds.isEmpty()) {
            return null;
        }
        Map<String, Object> agg =
                gbAiDailyRevenueService.getGroupIncomeAggregateForDepartmentIds(revenueQueryScopeIds, start, stop);
        if (agg == null) {
            agg = Map.of();
        }
        int distinctDays = toPositiveInt(agg.get("distinctRecordDates"));
        BigDecimal totalGross = decimalFromAgg(agg.get("totalGrossRevenue"));

        LinkedHashMap<String, Object> rawStats = syntheticRawStatsFromGroupAggregate(agg, totalGross, distinctDays);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rawStats", rawStats);
        data.put("days", distinctDays);
        data.put("totalRevenue", totalGross);
        data.put("avgDailyRevenue", distinctDays > 0
                ? totalGross.divide(BigDecimal.valueOf(distinctDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        List<Map<String, Object>> storeRanking =
                buildStoreRevenueRanking(dept, start, stop, args, true, request.getResolvedQueryContext());
        if (!storeRanking.isEmpty()) {
            data.put("storeRevenueRanking", storeRanking);
        }

        boolean mock = distinctDays <= 0 && totalGross.compareTo(BigDecimal.ZERO) <= 0;
        log.info(
                "[RevenueQueryTool][group] runId={} dept={} resolvedCount={} storeAnchors={} expandedScopeIds={} "
                        + "totalGross={} distinctDays={} avgDaily={}",
                request.getRunId(), dept, resolved.size(), retailAnchors, revenueQueryScopeIds.size(),
                totalGross.toPlainString(), distinctDays,
                mock ? "na" : data.get("avgDailyRevenue"));

        Map<String, Object> rollupMeta = new LinkedHashMap<>();
        rollupMeta.put("aggregationMode", "GROUP_SQL_ROLLUP");
        rollupMeta.put("storeAnchorCount", retailAnchors.size());
        rollupMeta.put("revenueRecordingDepartmentIdsCount", revenueQueryScopeIds.size());
        data.put("rollupMeta", rollupMeta);

        return ToolResult.builder()
                .success(true)
                .message(mock ? "no_rows_group_aggregate" : "ok_group_aggregate")
                .data(AiBusinessToolResponses.envelope(name(), true, mock, start, stop, dept, null, data, null))
                .build();
    }

    /** 集团与 {@link GbAiDailyRevenueDashboardServiceImpl#buildGroupWideIncomeFlattened} 同源字段，键名与 {@code selectStatsByDepartmentId} 对齐供 AnswerPlan secondary 读取。 */
    private static LinkedHashMap<String, Object> syntheticRawStatsFromGroupAggregate(
            Map<String, Object> agg, BigDecimal totalGross, int distinctDays) {
        LinkedHashMap<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_revenue", totalGross);
        stats.put("days", distinctDays);
        BigDecimal avgDaily = distinctDays > 0
                ? totalGross.divide(BigDecimal.valueOf(distinctDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        stats.put("avg_daily_revenue", avgDaily);
        stats.put("total_orders", nz(agg.get("totalOrders")));
        stats.put("total_platform_fee", decimalFromAgg(agg.get("totalPlatformFee")));
        stats.put("total_dine_in_revenue", decimalFromAgg(agg.get("totalDineIn")));
        stats.put("total_takeout_revenue", decimalFromAgg(agg.get("totalTakeout")));
        stats.put("total_takeout_net", decimalFromAgg(agg.get("totalTakeoutNetApprox")));
        stats.put("max_daily_revenue", decimalFromAgg(agg.get("maxDailyGross")));
        stats.put("min_daily_revenue", decimalFromAgg(agg.get("minDailyGrossPositive")));
        return stats;
    }

    private static int toPositiveInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        try {
            return Math.max(0, new BigDecimal(v.toString().trim()).intValue());
        } catch (Exception e) {
            return 0;
        }
    }

    private static BigDecimal decimalFromAgg(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 按<strong>门店根</strong>复用 {@link GbAiDailyRevenueService#getStatsByDepartmentId}（与总营业额同源 SQL），
     * 一行一门店；展示名取自 {@link AiResolvedQueryContext#getOrgScope()} 中可见门店，不把 SQL 展开 id 当作门店列表。
     */
    private List<Map<String, Object>> buildStoreRevenueRanking(Long anchorDeptFatherId, String start, String stop,
            Map<String, Object> args, boolean groupWideHint, AiResolvedQueryContext ctx) {
        List<Integer> resolvedNorm = normalizeResolvedIds(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        List<Integer> seeds = new ArrayList<>();
        if (!resolvedNorm.isEmpty()) {
            seeds.addAll(resolvedNorm);
        } else if (ctx != null && ctx.getOrgScope() != null && ctx.getOrgScope().getVisibleStores() != null) {
            for (AiStoreScopeDTO s : ctx.getOrgScope().getVisibleStores()) {
                if (s == null || s.getStoreDepartmentId() == null || s.getStoreDepartmentId() <= 0L) {
                    continue;
                }
                long id = s.getStoreDepartmentId();
                if (id <= Integer.MAX_VALUE) {
                    seeds.add((int) id);
                }
            }
        }
        if (seeds.isEmpty() && anchorDeptFatherId != null) {
            seeds.add(anchorDeptFatherId.intValue());
        }
        List<Integer> retailAnchors = aiScopeResolver.listDomainStoreAnchorsInResolved(seeds);
        if (retailAnchors.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Integer> uniq = new LinkedHashSet<>();
        for (Integer a : retailAnchors) {
            if (a != null && a > 0) {
                uniq.add(a);
            }
        }
        List<Integer> anchors = new ArrayList<>(uniq);
        if (anchors.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(anchors.size());
        for (Integer storeRootId : anchors) {
            Map<String, Object> perStoreStats =
                    gbAiDailyRevenueService.getStatsByDepartmentId(storeRootId.longValue(), start, stop);
            BigDecimal rev = nz(perStoreStats != null ? perStoreStats.get("total_revenue") : null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("storeDepartmentId", storeRootId.longValue());
            row.put("storeName", resolveVisibleStoreName(storeRootId.longValue(), ctx));
            row.put("revenueAmount", rev.doubleValue());
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble(r -> -doubleVal(r.get("revenueAmount"))));
        log.info("[RevenueQueryTool] storeRevenueRanking size={} groupWideHint={} anchorDept={}",
                rows.size(), groupWideHint, anchorDeptFatherId);
        return rows;
    }

    private static double doubleVal(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0d;
        }
    }

    private static String resolveVisibleStoreName(long storeDepartmentId, AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getOrgScope() == null) {
            return "";
        }
        List<AiStoreScopeDTO> stores = ctx.getOrgScope().getVisibleStores();
        if (stores == null) {
            return "";
        }
        for (AiStoreScopeDTO s : stores) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            if (s.getStoreDepartmentId() == storeDepartmentId) {
                return s.getStoreName() != null ? s.getStoreName().trim() : "";
            }
        }
        return "";
    }

    private static List<Integer> normalizeResolvedIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        ArrayList<Integer> out = new ArrayList<>(list.size());
        for (Object x : list) {
            if (x == null) {
                continue;
            }
            if (x instanceof Number n) {
                out.add(n.intValue());
            } else {
                try {
                    out.add(Integer.parseInt(x.toString().trim()));
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
        return out;
    }

    private static BigDecimal nz(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static Long toLongObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }
}
