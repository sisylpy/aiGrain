package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.dto.business.AiOverviewCoveredStoreItem;
import com.nongxinle.ai.dto.business.AiOverviewStoreIssueItem;
import com.nongxinle.ai.dto.business.AiOverviewVisibleStoreItem;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;
import com.nongxinle.service.GbAiDailyRevenueDashboardService;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbAiGroupOverviewStoreIssuesService;
import com.nongxinle.service.GbAiRestaurantProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDate;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_AI_ROLE_CODE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PARENT_STORE_COUNT;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;

/**
 * 与日营收 REST {@code GET /ai/daily-revenue/stats/{id}} 同源：画像 + 统计 + {@link GbAiDailyRevenueDashboardService#buildStatsDashboard}。
 * 集团广角：visibleStores 仍为门店根（{@code gb_department_father_id=0}）；日营收 SQL 对每店展开为
 * 「门店 + 直属子部门」（{@link GbAiDailyRevenueService#expandStoreRootsToDailyRevenueScopeIds}），聚合金额 rollup 为门店级，不把子部门当作独立门店展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessOverviewQueryTool implements AiTool {

    private final GbAiDailyRevenueService gbAiDailyRevenueService;
    private final GbAiDailyRevenueDashboardService gbAiDailyRevenueDashboardService;
    private final GbAiRestaurantProfileService gbAiRestaurantProfileService;
    private final GbAiGroupOverviewStoreIssuesService gbAiGroupOverviewStoreIssuesService;
    private final AiScopeResolver aiScopeResolver;

    @Override
    public String name() {
        return AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY;
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
            data.put("stats", Map.of());
            data.put("anomalyHints", List.of("参数不完整，无法拉取经营看板"));
            return ToolResult.builder()
                    .success(false)
                    .message("missing departmentFatherId/startDate/stopDate")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "参数不完整"))
                    .build();
        }

        if (groupWideHint) {
            return executeGroupWideOverview(request, args, dept, start, stop);
        }

        return executeSingleStoreOverview(request, dept, start, stop);
    }

    private ToolResult executeGroupWideOverview(ToolRequest request, Map<String, Object> args, Long dept,
            String start, String stop) {
        List<Integer> resolved = normalizeResolvedIds(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        boolean fallbackResolved = false;
        if (resolved.isEmpty()) {
            resolved = List.of(dept.intValue());
            fallbackResolved = true;
        }

        Integer parentStoreCountHint = parseIntegerObj(args.get(ARG_PARENT_STORE_COUNT));
        String aiRoleCode = str(args.get(ARG_AI_ROLE_CODE));

        log.info(
                "[BusinessOverviewQueryTool][group] runId={} userId={} aiRoleCode={} anchorDeptFatherId={} "
                        + "resolvedDepartmentCount={} parentStoreCount={} range={}..{} fallbackResolved={}",
                request.getRunId(), request.getUserId(), aiRoleCode, dept,
                resolved.size(), parentStoreCountHint, start, stop, fallbackResolved);

        List<Integer> retailAnchors = aiScopeResolver.listDomainStoreAnchorsInResolved(resolved);
        log.info(
                "[BusinessOverviewQueryTool][group-stores] runId={} userId={} domainStoreAnchorCount={} domainStoreAnchorIds={}",
                request.getRunId(), request.getUserId(), retailAnchors.size(), retailAnchors);

        if (retailAnchors.isEmpty()) {
            String hint = "集团经营概况要求统计范围内至少包含一个门店根部门（gb_department_father_id=0，通常为分销户下顶层门店）。"
                    + "当前展开列表未命中门店根；请核对 Scope 是否按分销户合并了各门店子树，或会话锚点是否落在门店顶级节点之下。";
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stats", Map.of());
            data.put("failureKind", "GROUP_WIDE_NO_PARENT_STORE_ANCHOR");
            data.put("anomalyHints", List.of(hint));
            putGroupRollupMeta(data, resolved.size(), 0, fallbackResolved);
            return ToolResult.builder()
                    .success(false)
                    .message("group_wide_no_parent_store_anchor")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "范围内无父级门店锚点"))
                    .build();
        }

        try {
            AiTimeWindowTextFormatter.UserPhrases period = AiTimeWindowTextFormatter.fromIsoRange(start, stop,
                    LocalDate.now());
            List<Integer> revenueQueryScopeIds =
                    gbAiDailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(retailAnchors);
            log.info(
                    "[BusinessOverviewQueryTool][group-revenue-scope] runId={} userId={} storeRootDepartmentIds={} "
                            + "revenueQueryDepartmentIds={} range={}..{}",
                    request.getRunId(), request.getUserId(), retailAnchors, revenueQueryScopeIds, start, stop);

            Map<String, Object> agg = gbAiDailyRevenueService.getGroupIncomeAggregateForDepartmentIds(
                    revenueQueryScopeIds, start, stop);
            int distinctDays = toPositiveInt(agg != null ? agg.get("distinctRecordDates") : null);
            BigDecimal totalGross = decimalFromAgg(agg != null ? agg.get("totalGrossRevenue") : null);
            int recordingDeptCount = toPositiveInt(agg != null ? agg.get("distinctRecordingDepartments") : null);

            if (distinctDays <= 0 && totalGross.signum() <= 0) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("stats", Map.of());
                data.put("failureKind", "GROUP_AGGREGATE_ZERO_UNDER_RESOLVED_IDS");
                List<String> mh = new ArrayList<>(noDailyRevenueHints(true, dept.longValue()));
                mh.add(period.getDisplayTimeRange()
                        + "在门店级可见范围对应的日营收查账部门（含各店直属子部门）内聚合后仍无有效日营业额行。");
                data.put("anomalyHints", mh);
                putGroupRollupMeta(data, retailAnchors.size(), recordingDeptCount, fallbackResolved);
                return ToolResult.builder()
                        .success(false)
                        .message("no_daily_revenue_rows_group_aggregate")
                        .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                                "集团范围内未汇总到日营收"))
                        .build();
            }

            int storeRootsWithRevenue = countStoreRootsWithRecordedRevenue(retailAnchors, start, stop);
            Map<String, Object> statsCn = gbAiDailyRevenueDashboardService.buildGroupWideIncomeFlattened(
                    agg, retailAnchors.size(), parentStoreCountHint, start, stop, storeRootsWithRevenue);

            GbAiGroupOverviewStoreIssuesService.BuiltGroupOverviewStoreIssues snap = null;
            if (AiRoleMapper.isGroupWideOrgScope(aiRoleCode)) {
                try {
                    snap = gbAiGroupOverviewStoreIssuesService.buildGroupStoreIssuesSnapshot(aiRoleCode, resolved,
                            start, stop, dept);
                    logGroupBusinessOverviewScopeDebug(request, aiRoleCode, dept, resolved,
                            parentStoreCountHint, fallbackResolved, snap, null);
                } catch (Exception snapEx) {
                    log.warn(
                            "[BusinessOverviewQueryTool][group] store-issues snapshot failed runId={} dept={} range={}..{} msg={}",
                            request.getRunId(), dept, start, stop,
                            snapEx.getMessage() != null ? snapEx.getMessage() : snapEx.getClass().getSimpleName());
                    logGroupBusinessOverviewScopeDebug(request, aiRoleCode, dept, resolved,
                            parentStoreCountHint, fallbackResolved, null, snapEx);
                }
            }

            Map<String, Object> supplement = new LinkedHashMap<>();
            putIfNumber(supplement, "订单汇总(区间)", agg.get("totalOrders"));
            putIfNumber(supplement, "总平台费(_mapper)", agg.get("totalPlatformFee"));
            putIfNumber(supplement, "堂食合计(_mapper)", agg.get("totalDineIn"));
            putIfNumber(supplement, "外卖合计(_mapper)", agg.get("totalTakeout"));

            List<String> hints = new ArrayList<>();
            Object scopeNote = statsCn.get("数据口径说明");
            if (scopeNote != null && !scopeNote.toString().isBlank()) {
                hints.add(scopeNote.toString().trim());
            }
            if (fallbackResolved) {
                hints.add("本轮未拿到 resolvedDepartmentIds，已临时仅按当前 departmentId 参与汇总；集团场景请确保 ScopeIntersect 写入部门列表。");
            }
            if (distinctDays > 0 && distinctDays < 5) {
                hints.add("统计天数较少，结论波动大，建议拉长区间或补录日营收");
            }
            if (snap != null && !snap.getDataMissingStores().isEmpty()) {
                hints.add(snap.getDataMissingStores().size() + " 家门店在" + period.getDisplayTimeRange()
                        + "暂无日营收或未纳入本条汇总，可与后台门店列表核对。");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stats", statsCn);
            data.put("dashboardBindings", Map.of());
            data.put("mapperSupplement", supplement);
            data.put("anomalyHints", hints);
            data.put("grossMarginFromDashboardPercent", statsCn.get("利润率"));
            data.put("profitStatusText", statsCn.get("盈亏状态"));

            BigDecimal totalRevBd = decimalFromStat(statsCn.get("总营业额"));
            data.put("totalRevenue", totalRevBd);
            data.put("days", statsCn.get("统计天数"));

            putGroupRollupMeta(data, retailAnchors.size(), recordingDeptCount, fallbackResolved);

            if (snap != null) {
                data.put("visibleStores", new ArrayList<>(snap.getVisibleStores()));
                data.put("dataMissingStores", new ArrayList<>(snap.getDataMissingStores()));
                data.put("attentionStores", new ArrayList<>(snap.getAttentionStores()));
                data.put("coveredStores", new ArrayList<>(snap.getCoveredStores()));
                String priorityBrief = snap.getPriorityStoresBrief();
                if (priorityBrief != null && !priorityBrief.isBlank()) {
                    data.put("priorityStoresBrief", priorityBrief.trim());
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rmSnap = (Map<String, Object>) data.get("rollupMeta");
                if (rmSnap != null) {
                    rmSnap.put("visibleStoreCount", snap.getVisibleStores().size());
                    rmSnap.put("storeWithRevenueCount", snap.getCoveredStores().size());
                    rmSnap.put("storeMissingRevenueCount", snap.getDataMissingStores().size());
                    rmSnap.put("dataMissingVisibleNodeApprox", snap.getDataMissingStores().size());
                }
            }

            return ToolResult.builder()
                    .success(true)
                    .message("ok_group_aggregate")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, null, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[BusinessOverviewQueryTool][group] runId={} dept={} range={}..{}: {}",
                    request.getRunId(), dept, start, stop, e.toString(), e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stats", Map.of());
            data.put("anomalyHints", List.of("集团经营看板汇总异常，请稍后重试或核对数据库"));
            data.put("errorCode", "query_failed");
            putGroupRollupMeta(data, retailAnchors.size(), 0, fallbackResolved);
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "查询异常"))
                    .build();
        }
    }

    /** 任一集团聚合响应都应携带，便于 {@code overviewScope} 不误判为门店子树。 */
    private static void putGroupRollupMeta(Map<String, Object> data, int visibleStoreRootCount,
            int recordingDeptCount,
            boolean fallbackSingleAnchorOnly) {
        LinkedHashMap<String, Object> rollupMeta = new LinkedHashMap<>();
        rollupMeta.put("aggregationMode", "GROUP_SQL_ROLLUP");
        rollupMeta.put("visibleDepartmentNodeCount", visibleStoreRootCount);
        rollupMeta.put("dataAvailableRecordingDepartmentCount", recordingDeptCount);
        rollupMeta.put("dataMissingVisibleNodeApprox", 0);
        rollupMeta.put("fallbackSingleAnchorOnly", fallbackSingleAnchorOnly);
        data.put("rollupMeta", rollupMeta);
    }

    private ToolResult executeSingleStoreOverview(ToolRequest request, Long dept, String start, String stop) {
        GbAiRestaurantProfileEntity profile = gbAiRestaurantProfileService.getByDepartmentId(dept);
        if (profile == null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stats", Map.of());
            data.put("failureKind", "RESTAURANT_PROFILE_MISSING");
            data.put("anomalyHints", profileMissingHints(dept.longValue()));
            return ToolResult.builder()
                    .success(false)
                    .message("restaurant_profile_missing")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "餐厅画像不存在"))
                    .build();
        }

        try {
            Map<String, Object> rawStats = gbAiDailyRevenueService.getStatsByDepartmentId(dept, start, stop);
            if (rawStats == null || rawStats.get("days") == null
                    || ((Number) rawStats.get("days")).intValue() == 0) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("stats", Map.of());
                data.put("coveredStores", List.of(coveredStoreLine(dept, profile, Map.of(), rawStats, false)));
                data.put("anomalyHints", noDailyRevenueHints(false, dept.longValue()));
                return ToolResult.builder()
                        .success(false)
                        .message("no_daily_revenue_rows")
                        .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                                "暂无营业额数据"))
                        .build();
            }

            Map<String, Object> dashboardPayload = gbAiDailyRevenueDashboardService.buildStatsDashboard(
                    dept, profile, rawStats, start, stop);

            @SuppressWarnings("unchecked")
            Map<String, Object> statsCn = dashboardPayload.get("stats") instanceof Map
                    ? (Map<String, Object>) dashboardPayload.get("stats")
                    : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> dashboard = dashboardPayload.get("dashboard") instanceof Map
                    ? (Map<String, Object>) dashboardPayload.get("dashboard")
                    : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> bindings = dashboard.get("bindings") instanceof Map
                    ? (Map<String, Object>) dashboard.get("bindings")
                    : Map.of();

            Map<String, Object> supplement = new LinkedHashMap<>();
            putIfNumber(supplement, "订单汇总(区间)", rawStats.get("total_orders"));
            putIfNumber(supplement, "日均订单数(_mapper)", rawStats.get("avg_order_count"));
            putIfNumber(supplement, "总平台费(_mapper)", rawStats.get("total_platform_fee"));
            putIfNumber(supplement, "总优惠券金额(_mapper)", rawStats.get("total_coupon_amount"));

            List<String> hints = new ArrayList<>();
            Object daysObj = statsCn.get("统计天数");
            int days = daysObj instanceof Number ? ((Number) daysObj).intValue() : 0;
            if (days > 0 && days < 5) {
                hints.add("统计天数较少，结论波动大，建议拉长区间或补录日营收");
            }
            Object safety = bindings.get("safetyText");
            if (safety != null && !safety.toString().isBlank()) {
                hints.add("健康度参考：" + safety.toString().trim());
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stats", statsCn);
            data.put("dashboardBindings", shallowCopyBindings(bindings));
            data.put("mapperSupplement", supplement);
            data.put("anomalyHints", hints);
            data.put("grossMarginFromDashboardPercent", statsCn.get("利润率"));
            data.put("profitStatusText", statsCn.get("盈亏状态"));

            BigDecimal totalRevBd = decimalFromStat(statsCn.get("总营业额"));
            data.put("totalRevenue", totalRevBd);
            data.put("days", statsCn.get("统计天数"));
            data.put("coveredStores", List.of(coveredStoreLine(dept, profile, statsCn, rawStats, true)));

            return ToolResult.builder()
                    .success(true)
                    .message("ok")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, null, data, null))
                    .build();
        } catch (Exception e) {
            log.warn("[BusinessOverviewQueryTool] runId={} dept={} range={}..{}: {}",
                    request.getRunId(), dept, start, stop, e.toString(), e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stats", Map.of());
            data.put("anomalyHints", List.of("经营看板查询异常，请稍后重试或核对数据库"));
            data.put("errorCode", "query_failed");
            return ToolResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, null, data,
                            "查询异常"))
                    .build();
        }
    }

    private static AiOverviewCoveredStoreItem coveredStoreLine(Long dept,
            GbAiRestaurantProfileEntity profile,
            Map<String, Object> statsCn,
            Map<String, Object> rawStats,
            boolean dashboardOk) {
        String name = "";
        if (profile != null && profile.getGbAiRestaurantProfileRestaurantName() != null) {
            name = profile.getGbAiRestaurantProfileRestaurantName().trim();
        }
        if (name.isBlank()) {
            name = "门店#" + dept;
        }
        BigDecimal gross = statsCn != null && !statsCn.isEmpty()
                ? decimalFromStat(statsCn.get("总营业额"))
                : BigDecimal.ZERO;
        int days = statsCn != null ? toPositiveInt(statsCn.get("统计天数")) : 0;
        BigDecimal orders = rawStats != null ? decimalFromAgg(rawStats.get("total_orders")) : BigDecimal.ZERO;
        BigDecimal avgDailyOrd = statsCn != null ? decimalFromStat(statsCn.get("日均订单数")) : BigDecimal.ZERO;
        if (avgDailyOrd.signum() <= 0 && days > 0 && orders.signum() > 0) {
            avgDailyOrd = orders.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        }
        BigDecimal avgPerCust = statsCn != null ? decimalFromStat(statsCn.get("客单价")) : BigDecimal.ZERO;
        boolean hasRev = dashboardOk && days > 0 && gross.signum() > 0 && orders.signum() > 0;
        return AiOverviewCoveredStoreItem.builder()
                .storeName(name)
                .hasRevenueData(hasRev)
                .totalRevenue(gross)
                .days(days)
                .orderCount(orders)
                .avgOrderCount(avgDailyOrd)
                .avgPerCustomer(avgPerCust)
                .build();
    }

    private static Integer parseIntegerObj(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
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

    /**
     * 与门店排行/单店 stats 同源：按<strong>门店根</strong>在区间内是否有合计营业额，仅供「数据口径说明」写「几家门店」；
     * 不得误用 {@code distinctRecordingDepartments}（展开后的记账部门数）。
     */
    private int countStoreRootsWithRecordedRevenue(List<Integer> retailAnchors, String start, String stop) {
        if (retailAnchors == null || retailAnchors.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Integer a : retailAnchors) {
            if (a == null || a <= 0) {
                continue;
            }
            Map<String, Object> st = gbAiDailyRevenueService.getStatsByDepartmentId(a.longValue(), start, stop);
            if (decimalFromAgg(st != null ? st.get("total_revenue") : null).signum() > 0) {
                n++;
            }
        }
        return n;
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

    private static List<String> profileMissingHints(long deptRequested) {
        return List.of("门店餐厅画像未配置，无法生成与经营页一致的经营看板。");
    }

    private static List<String> noDailyRevenueHints(boolean groupWideHint, long deptRequested) {
        if (groupWideHint) {
            return List.of(
                    "当前所选组织节点在统计区间内未命中任何日营业额行——常见于集团根/区域根节点名下无直属日营收台账。",
                    "集团多维汇总：已按可见部门列表做 SQL 汇总；若仍为空，表示这些组织在日营收表中无记录。",
                    "可与采购/分销商口径区分理解：后者可能仍有金额，但与「单体门店经营看板」不是同一拼装。"
            );
        }
        return List.of("所选区间暂无日营业额记录");
    }

    private static BigDecimal decimalFromStat(Object v) {
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

    private static Map<String, Object> shallowCopyBindings(Map<String, Object> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(bindings);
    }

    private static void putIfNumber(Map<String, Object> m, String key, Object v) {
        if (v == null) {
            return;
        }
        if (v instanceof Number || v instanceof BigDecimal) {
            m.put(key, v);
        } else {
            try {
                m.put(key, new BigDecimal(v.toString().trim()));
            } catch (Exception ignored) {
                m.put(key, v.toString());
            }
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

    private static void logGroupBusinessOverviewScopeDebug(
            ToolRequest request,
            String aiRoleCode,
            Long anchorDeptFatherId,
            List<Integer> resolvedDepartmentIds,
            Integer parentStoreCountArg,
            boolean fallbackResolved,
            GbAiGroupOverviewStoreIssuesService.BuiltGroupOverviewStoreIssues snap,
            Exception snapshotError) {

        long runId = request.getRunId() != null ? request.getRunId() : -1L;
        long userId = request.getUserId() != null ? request.getUserId() : -1L;
        if (snapshotError != null) {
            log.info(
                    "[BusinessOverviewQueryTool][group][scope-debug] runId={} userId={} aiRoleCode={} anchorDeptFatherId={} "
                            + "resolvedDepartmentCount={} resolvedDepartmentIds={} parentStoreCount={} fallbackResolved={} "
                            + "snapshotErrorClass={} snapshotError={}",
                    runId, userId, aiRoleCode, anchorDeptFatherId,
                    resolvedDepartmentIds.size(), resolvedDepartmentIds, parentStoreCountArg, fallbackResolved,
                    snapshotError.getClass().getSimpleName(),
                    snapshotError.getMessage() != null ? snapshotError.getMessage() : "");
            return;
        }
        if (snap == null) {
            return;
        }
        String vis = snap.getVisibleStores().stream().map(AiOverviewVisibleStoreItem::getStoreName).collect(Collectors.joining("; "));
        String cov = snap.getCoveredStores().stream().map(AiOverviewCoveredStoreItem::getStoreName).collect(Collectors.joining("; "));
        String miss = snap.getDataMissingStores().stream().map(AiOverviewStoreIssueItem::getStoreName).collect(Collectors.joining("; "));
        String att = snap.getAttentionStores().stream().map(AiOverviewStoreIssueItem::getStoreName).collect(Collectors.joining("; "));
        log.info(
                "[BusinessOverviewQueryTool][group][scope-debug] runId={} userId={} aiRoleCode={} anchorDeptFatherId={} "
                        + "resolvedDepartmentCount={} resolvedDepartmentIds={} parentStoreCount={} fallbackResolved={} "
                        + "visibleStoreCount={} storeWithRevenueCount={} dataMissingStoreCount={} attentionStoreCount={} "
                        + "visibleStoreNames={} coveredStoreNames={} dataMissingStoreNames={} attentionStoreNames={}",
                runId, userId, aiRoleCode, anchorDeptFatherId,
                resolvedDepartmentIds.size(), resolvedDepartmentIds, parentStoreCountArg, fallbackResolved,
                snap.getVisibleStores().size(), snap.getCoveredStores().size(),
                snap.getDataMissingStores().size(), snap.getAttentionStores().size(),
                vis, cov, miss, att);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }
}
