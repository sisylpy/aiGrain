package com.nongxinle.ai.graph.business.toolrequest;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.ToolDepartmentResolutionSupport;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 经营域 Tool 请求构造的<b>公共收口</b>：时间窗、部门锚点、ResolvedDataScope 快照与统一 debug 形状。
 * <ul>
 *     <li>只读 {@link AiResolvedQueryContext} / {@link AiRunState} 已有结构化字段；不解析用户原文、不加 regex/contains 规则。</li>
 *     <li>不生成 SQL；不落 Composer；权限仍以 {@link com.nongxinle.ai.security.AiPermissionGuard} + resolvedContext 为准。</li>
 *     <li><b>Tool 结果写入策略</b>：由调用方（如 {@link com.nongxinle.ai.graph.business.RevenueQueryToolExecutor}、编排节点）
 *     决定；失败信封是否保留、fallback 前是否 {@code toolResults.remove(toolId)} 由编排层统一处理（参见 Master/BTEN）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BusinessToolExecutionRequestResolver {

    private final ToolDepartmentResolutionSupport toolDepartmentResolutionSupport;

    /**
     * 解析 REVENUE_QUERY 所需的日期与部门锚点，并附带可见门店根 / SQL 展开 ID 快照（观测与后续扩展）。
     */
    public RevenueToolRequestResolution resolveRevenueToolRequest(AiRunState state, AiResolvedQueryContext rq) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();

        String start = resolveStartDateIso(rq, state);
        String stop = resolveEndDateIso(rq, state);
        dbg.put("timeWindowSource", classifyTimeWindowSource(rq, state));
        dbg.put("startDateIso", start);
        dbg.put("stopDateIso", stop);
        dbg.put("endDateIso", stop);

        Long deptRaw = state != null ? state.getDepartmentId() : null;
        Long deptAnchor = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptRaw);
        String deptAnchorSource = "resolveBuildInsightDepartmentFatherId(loginDepartmentId)";
        if (deptAnchor == null && rq != null) {
            deptAnchor = firstVisibleStoreDepartmentId(rq);
            deptAnchorSource = "resolvedQueryContext.orgScope.visibleStores[0].storeDepartmentId";
        }
        if (deptAnchor == null && rq != null) {
            Long fromDs = firstDataScopeDepartmentRootAnchor(rq.getDataScope());
            if (fromDs != null) {
                deptAnchor = fromDs;
                deptAnchorSource =
                        "resolvedQueryContext.dataScope.visibleStoreRootIds|storeRootDepartmentIds|effectiveSql";
            }
        }
        dbg.put("departmentAnchorSource", deptAnchorSource);

        Long deptScoped =
                toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, deptAnchor);
        Long deptBuild =
                toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptScoped);
        dbg.put("departmentFatherIdForScopedTools", deptScoped);
        dbg.put("departmentFatherIdForBuildInsight", deptBuild);

        List<Integer> visibleRoots =
                rq != null ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(rq) : List.of();
        dbg.put("visibleStoreRootDepartmentIds", new ArrayList<>(visibleRoots));

        List<Long> expandedSql = new ArrayList<>();
        List<Long> storeRootIds = new ArrayList<>();
        if (rq != null && rq.getDataScope() != null) {
            AiResolvedDataScope ds = rq.getDataScope();
            expandedSql.addAll(ds.getEffectiveSqlDepartmentIds());
            storeRootIds.addAll(ds.getVisibleStoreRootIds());
        }
        dbg.put("expandedSqlDepartmentIds", expandedSql);
        dbg.put("visibleStoreRootIds", storeRootIds);

        return RevenueToolRequestResolution.builder()
                .startDateIso(start)
                .stopDateIso(stop)
                .departmentFatherIdForScopedTools(deptScoped)
                .departmentFatherIdForBuildInsight(deptBuild)
                .visibleStoreRootDepartmentIds(new ArrayList<>(visibleRoots))
                .expandedSqlDepartmentIds(expandedSql)
                .visibleStoreRootIds(storeRootIds)
                .resolutionDebug(dbg)
                .build();
    }

    /** 采购专线 {@link AiBusinessToolIds#PURCHASE_OVERVIEW}：时间窗、部门锚点、SQL/门店根快照与观测 debug（不重读用户原文）。 */
    public PurchaseToolRequestContext buildPurchaseRequestContext(AiRunState state, AiResolvedQueryContext rq) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();

        String start = resolveStartDateIso(rq, state);
        String end = resolveEndDateIso(rq, state);
        dbg.put("startDateIso", start);
        dbg.put("endDateIso", end);
        dbg.put("stopDateIso", end);
        dbg.put("timeWindowSource", classifyTimeWindowSource(rq, state));

        List<Long> purchaseSql = new ArrayList<>();
        List<Long> effectiveSql = new ArrayList<>();
        List<Long> visibleRootIds = new ArrayList<>();
        String queryScopeKind = null;
        if (rq != null && rq.getDataScope() != null) {
            AiResolvedDataScope ds = rq.getDataScope();
            purchaseSql.addAll(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_PURCHASE));
            effectiveSql.addAll(ds.getEffectiveSqlDepartmentIds());
            visibleRootIds.addAll(ds.getVisibleStoreRootIds());
            queryScopeKind = ds.getQueryScopeKind();
            dbg.put("purchaseSqlDepartmentIdsSource", "resolvedQueryContext.dataScope.sqlDepartmentIdsForDomain(purchase)");
            dbg.put("effectiveSqlDepartmentIdsSource", "resolvedQueryContext.dataScope.effectiveSqlDepartmentIds");
            dbg.put("visibleStoreRootIdsSource", "resolvedQueryContext.dataScope.visibleStoreRootIds");
        } else {
            dbg.put("purchaseSqlDepartmentIdsSource", "none_missing_dataScope");
        }

        String orgScopeType = null;
        if (rq != null && rq.getOrgScope() != null) {
            orgScopeType = rq.getOrgScope().getScopeType();
        }

        List<Integer> visibleDeptRoots =
                rq != null ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(rq) : List.of();
        dbg.put("visibleStoreRootDepartmentIdsSource", "resolvedQueryContext.orgScope.visibleStores.storeDepartmentId");

        String purchaseSourceType = null;
        String structuredIntentDetail = null;
        if (rq != null && rq.getQueryIntent() != null) {
            purchaseSourceType = rq.getQueryIntent().getPurchaseSourceType();
            structuredIntentDetail = rq.getQueryIntent().getStructuredIntentDetail();
            dbg.put(
                    "purchaseSourceTypeSource",
                    purchaseSourceType != null && !purchaseSourceType.isBlank()
                            ? "resolvedQueryContext.queryIntent.purchaseSourceType"
                            : "none");
            dbg.put(
                    "structuredIntentDetailSource",
                    structuredIntentDetail != null && !structuredIntentDetail.isBlank()
                            ? "resolvedQueryContext.queryIntent.structuredIntentDetail"
                            : "none");
        }

        dbg.put("orgScopeType", orgScopeType);
        dbg.put("queryScopeKind", queryScopeKind);

        Long deptRaw = state != null ? state.getDepartmentId() : null;
        Long deptAnchor = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptRaw);
        String deptAnchorSource = "resolveBuildInsightDepartmentFatherId(loginDepartmentId)";
        if (deptAnchor == null && rq != null) {
            deptAnchor = firstVisibleStoreDepartmentId(rq);
            deptAnchorSource = "resolvedQueryContext.orgScope.visibleStores[0].storeDepartmentId";
        }
        if (deptAnchor == null && rq != null) {
            Long fromDs = firstDataScopeDepartmentRootAnchor(rq.getDataScope());
            if (fromDs != null) {
                deptAnchor = fromDs;
                deptAnchorSource =
                        "resolvedQueryContext.dataScope.visibleStoreRootIds|storeRootDepartmentIds|effectiveSql";
            }
        }
        dbg.put("departmentAnchorSource", deptAnchorSource);

        Long deptScoped = toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, deptAnchor);
        Long deptBuild = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptScoped);
        dbg.put("departmentFatherIdForScopedTools", deptScoped);
        dbg.put("departmentFatherIdForBuildInsight", deptBuild);

        return PurchaseToolRequestContext.builder()
                .startDateIso(start)
                .endDateIso(end)
                .departmentFatherIdForScopedTools(deptScoped)
                .departmentFatherIdForBuildInsight(deptBuild)
                .purchaseSqlDepartmentIds(purchaseSql)
                .effectiveSqlDepartmentIds(effectiveSql)
                .visibleStoreRootIds(visibleRootIds)
                .visibleStoreRootDepartmentIds(new ArrayList<>(visibleDeptRoots))
                .purchaseSourceType(purchaseSourceType)
                .structuredIntentDetail(structuredIntentDetail)
                .orgScopeType(orgScopeType)
                .queryScopeKind(queryScopeKind)
                .resolutionDebug(dbg)
                .build();
    }

    /** 出库/核销专线 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#STOCK_REDUCE_QUERY}：时间窗、部门锚点、SQL/门店根快照与观测 debug（不重读用户原文）。 */
    public StockReduceToolRequestContext buildStockReduceRequestContext(AiRunState state, AiResolvedQueryContext rq) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();

        String start = resolveStartDateIso(rq, state);
        String end = resolveEndDateIso(rq, state);
        dbg.put("startDateIso", start);
        dbg.put("endDateIso", end);
        dbg.put("stopDateIso", end);
        dbg.put("timeWindowSource", classifyTimeWindowSource(rq, state));

        String structuredIntentDetail = null;
        if (rq != null && rq.getQueryIntent() != null) {
            structuredIntentDetail = rq.getQueryIntent().getStructuredIntentDetail();
            dbg.put(
                    "structuredIntentDetailSource",
                    structuredIntentDetail != null && !structuredIntentDetail.isBlank()
                            ? "resolvedQueryContext.queryIntent.structuredIntentDetail"
                            : "none");
        } else {
            dbg.put("structuredIntentDetailSource", "none_missing_queryIntent");
        }

        String stockReduceType = null;
        if (rq != null && rq.getQuerySemanticParse() != null) {
            AiQuerySemanticParseResult.MetricPart metric = rq.getQuerySemanticParse().getMetric();
            if (metric != null && metric.getStockReduceType() != null && !metric.getStockReduceType().isBlank()) {
                stockReduceType = metric.getStockReduceType().trim();
                dbg.put("stockReduceTypeSource",
                        "resolvedQueryContext.querySemanticParse.metric.stockReduceType");
            }
        }
        if (stockReduceType == null) {
            dbg.put("stockReduceTypeSource", "none_missing_metric_or_stockReduceType");
        }

        List<Long> stockReduceSql = new ArrayList<>();
        List<Long> effectiveSql = new ArrayList<>();
        List<Long> visibleRootIds = new ArrayList<>();
        String queryScopeKind = null;
        if (rq != null && rq.getDataScope() != null) {
            AiResolvedDataScope ds = rq.getDataScope();
            stockReduceSql.addAll(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_STOCK_REDUCE));
            effectiveSql.addAll(ds.getEffectiveSqlDepartmentIds());
            visibleRootIds.addAll(ds.getVisibleStoreRootIds());
            queryScopeKind = ds.getQueryScopeKind();
            dbg.put("stockReduceSqlDepartmentIdsSource",
                    "resolvedQueryContext.dataScope.sqlDepartmentIdsForDomain(stock_reduce)");
            dbg.put("effectiveSqlDepartmentIdsSource",
                    "resolvedQueryContext.dataScope.effectiveSqlDepartmentIds");
            dbg.put("visibleStoreRootIdsSource", "resolvedQueryContext.dataScope.visibleStoreRootIds");
        } else {
            dbg.put("stockReduceSqlDepartmentIdsSource", "none_missing_dataScope");
        }

        String orgScopeType = null;
        if (rq != null && rq.getOrgScope() != null) {
            orgScopeType = rq.getOrgScope().getScopeType();
            dbg.put("scopeTypeSource", "resolvedQueryContext.orgScope.scopeType");
        } else {
            dbg.put("scopeTypeSource", "none_missing_orgScope");
        }
        dbg.put("scopeType", orgScopeType);
        dbg.put("queryScopeKind", queryScopeKind);

        List<Integer> visibleDeptRoots =
                rq != null ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(rq) : List.of();
        dbg.put("visibleStoreRootDepartmentIdsSource",
                "resolvedQueryContext.orgScope.visibleStores.storeDepartmentId");

        Long deptRaw = state != null ? state.getDepartmentId() : null;
        Long deptAnchor = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptRaw);
        String deptAnchorSource = "resolveBuildInsightDepartmentFatherId(loginDepartmentId)";
        if (deptAnchor == null && rq != null) {
            deptAnchor = firstVisibleStoreDepartmentId(rq);
            deptAnchorSource = "resolvedQueryContext.orgScope.visibleStores[0].storeDepartmentId";
        }
        if (deptAnchor == null && rq != null) {
            Long fromDs = firstDataScopeDepartmentRootAnchor(rq.getDataScope());
            if (fromDs != null) {
                deptAnchor = fromDs;
                deptAnchorSource =
                        "resolvedQueryContext.dataScope.visibleStoreRootIds|storeRootDepartmentIds|effectiveSql";
            }
        }
        dbg.put("departmentAnchorSource", deptAnchorSource);

        Long deptScoped = toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, deptAnchor);
        Long deptBuild = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptScoped);
        dbg.put("departmentFatherIdForScopedTools", deptScoped);
        dbg.put("departmentFatherIdForBuildInsight", deptBuild);

        return StockReduceToolRequestContext.builder()
                .startDateIso(start)
                .endDateIso(end)
                .departmentFatherIdForScopedTools(deptScoped)
                .departmentFatherIdForBuildInsight(deptBuild)
                .stockReduceSqlDepartmentIds(stockReduceSql)
                .effectiveSqlDepartmentIds(effectiveSql)
                .visibleStoreRootIds(visibleRootIds)
                .visibleStoreRootDepartmentIds(new ArrayList<>(visibleDeptRoots))
                .structuredIntentDetail(structuredIntentDetail)
                .stockReduceType(stockReduceType)
                .orgScopeType(orgScopeType)
                .queryScopeKind(queryScopeKind)
                .resolutionDebug(dbg)
                .build();
    }

    /** 菜品毛利专线 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#DISH_PROFIT_ANALYSIS}：时间窗、部门锚点、SQL/门店根与结构化字段（不重读用户原文）。 */
    public DishProfitToolRequestContext buildDishProfitRequestContext(AiRunState state, AiResolvedQueryContext rq) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();

        String start = resolveStartDateIso(rq, state);
        String end = resolveEndDateIso(rq, state);
        dbg.put("startDateIso", start);
        dbg.put("endDateIso", end);
        dbg.put("stopDateIso", end);
        dbg.put("timeWindowSource", classifyTimeWindowSource(rq, state));

        List<Long> dishProfitSql = new ArrayList<>();
        List<Long> effectiveSql = new ArrayList<>();
        List<Long> visibleRootIds = new ArrayList<>();
        String queryScopeKind = null;
        if (rq != null && rq.getDataScope() != null) {
            AiResolvedDataScope ds = rq.getDataScope();
            dishProfitSql.addAll(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_DISH_PROFIT));
            effectiveSql.addAll(ds.getEffectiveSqlDepartmentIds());
            visibleRootIds.addAll(ds.getVisibleStoreRootIds());
            queryScopeKind = ds.getQueryScopeKind();
            dbg.put(
                    "dishProfitSqlDepartmentIdsSource",
                    "resolvedQueryContext.dataScope.sqlDepartmentIdsForDomain(dish_profit)");
            dbg.put("effectiveSqlDepartmentIdsSource", "resolvedQueryContext.dataScope.effectiveSqlDepartmentIds");
            dbg.put("visibleStoreRootIdsSource", "resolvedQueryContext.dataScope.visibleStoreRootIds");
        } else {
            dbg.put("dishProfitSqlDepartmentIdsSource", "none_missing_dataScope");
        }

        String orgScopeType = null;
        if (rq != null && rq.getOrgScope() != null) {
            orgScopeType = rq.getOrgScope().getScopeType();
            dbg.put("scopeTypeSource", "resolvedQueryContext.orgScope.scopeType");
        } else {
            dbg.put("scopeTypeSource", "none_missing_orgScope");
        }
        dbg.put("scopeType", orgScopeType);
        dbg.put("queryScopeKind", queryScopeKind);

        List<Integer> visibleDeptRoots =
                rq != null ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(rq) : List.of();
        dbg.put(
                "visibleStoreRootDepartmentIdsSource",
                "resolvedQueryContext.orgScope.visibleStores.storeDepartmentId");

        String structuredIntentDetail = null;
        String mentionedDishName = null;
        String dishProfitMetricType = null;
        if (rq != null) {
            if (rq.getQueryIntent() != null) {
                structuredIntentDetail = rq.getQueryIntent().getStructuredIntentDetail();
                dbg.put(
                        "structuredIntentDetailSource",
                        structuredIntentDetail != null && !structuredIntentDetail.isBlank()
                                ? "resolvedQueryContext.queryIntent.structuredIntentDetail"
                                : "none");
            } else {
                dbg.put("structuredIntentDetailSource", "none_missing_queryIntent");
            }
            mentionedDishName = rq.getMentionedDishName();
            dbg.put(
                    "mentionedDishNameSource",
                    mentionedDishName != null && !mentionedDishName.isBlank()
                            ? "resolvedQueryContext.mentionedDishName"
                            : "none");
            dishProfitMetricType = rq.getDishProfitMetricType();
            dbg.put(
                    "dishProfitMetricTypeSource",
                    dishProfitMetricType != null && !dishProfitMetricType.isBlank()
                            ? "resolvedQueryContext.dishProfitMetricType"
                            : "none");
        }

        Long deptRaw = state != null ? state.getDepartmentId() : null;
        Long deptAnchor = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptRaw);
        String deptAnchorSource = "resolveBuildInsightDepartmentFatherId(loginDepartmentId)";
        if (deptAnchor == null && rq != null) {
            deptAnchor = firstVisibleStoreDepartmentId(rq);
            deptAnchorSource = "resolvedQueryContext.orgScope.visibleStores[0].storeDepartmentId";
        }
        if (deptAnchor == null && rq != null) {
            Long fromDs = firstDataScopeDepartmentRootAnchor(rq.getDataScope());
            if (fromDs != null) {
                deptAnchor = fromDs;
                deptAnchorSource =
                        "resolvedQueryContext.dataScope.visibleStoreRootIds|storeRootDepartmentIds|effectiveSql";
            }
        }
        dbg.put("departmentAnchorSource", deptAnchorSource);

        Long deptScoped = toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, deptAnchor);
        Long deptBuild = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptScoped);
        dbg.put("departmentFatherIdForScopedTools", deptScoped);
        dbg.put("departmentFatherIdForBuildInsight", deptBuild);

        return DishProfitToolRequestContext.builder()
                .startDateIso(start)
                .endDateIso(end)
                .stopDateIso(end)
                .departmentFatherIdForScopedTools(deptScoped)
                .departmentFatherIdForBuildInsight(deptBuild)
                .dishProfitSqlDepartmentIds(dishProfitSql)
                .effectiveSqlDepartmentIds(effectiveSql)
                .visibleStoreRootIds(visibleRootIds)
                .visibleStoreRootDepartmentIds(new ArrayList<>(visibleDeptRoots))
                .mentionedDishName(mentionedDishName)
                .structuredIntentDetail(structuredIntentDetail)
                .dishProfitMetricType(dishProfitMetricType)
                .orgScopeType(orgScopeType)
                .queryScopeKind(queryScopeKind)
                .resolutionDebug(dbg)
                .build();
    }

    private static String resolveStartDateIso(AiResolvedQueryContext rqCtx, AiRunState state) {
        AiResolvedTimeWindow eff = effectiveTimeWindowForResolution(rqCtx);
        if (eff != null && eff.getStartDate() != null) {
            return eff.getStartDate().toString();
        }
        return blankToNull(state != null ? state.getStatStartDate() : null);
    }

    private static String resolveEndDateIso(AiResolvedQueryContext rqCtx, AiRunState state) {
        AiResolvedTimeWindow eff = effectiveTimeWindowForResolution(rqCtx);
        if (eff != null && eff.getEndDate() != null) {
            return eff.getEndDate().toString();
        }
        return blankToNull(state != null ? state.getStatEndDate() : null);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private static Long firstVisibleStoreDepartmentId(AiResolvedQueryContext rqCtx) {
        List<Integer> roots = BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(rqCtx);
        if (roots.isEmpty()) {
            return null;
        }
        return roots.get(0).longValue();
    }

    /**
     * 当 orgScope.visibleStores 未带 {@code storeDepartmentId}（常见于点名门店后仅以 dataScope / SQL 展开落地）时，
     * 用 {@link AiResolvedDataScope} 的门店根 / 展开列表恢复 Tool 锚点；不重读用户原文。
     */
    private static Long firstDataScopeDepartmentRootAnchor(AiResolvedDataScope ds) {
        if (ds == null) {
            return null;
        }
        for (Long id : ds.getVisibleStoreRootIds()) {
            if (id != null && id > 0L) {
                return id;
            }
        }
        for (Long id : ds.resolveStoreRootDepartmentIds()) {
            if (id != null && id > 0L) {
                return id;
            }
        }
        for (Long id : ds.getEffectiveSqlDepartmentIds()) {
            if (id != null && id > 0L) {
                return id;
            }
        }
        return null;
    }

    /** Resolver 已解析时间窗；不在 Tool 请求层根据 timeLabel 推算起止日。 */
    private static AiResolvedTimeWindow effectiveTimeWindowForResolution(AiResolvedQueryContext rqCtx) {
        return rqCtx != null ? rqCtx.getTimeWindow() : null;
    }

    private static String classifyTimeWindowSource(AiResolvedQueryContext rqCtx, AiRunState state) {
        if (rqCtx != null && rqCtx.getTimeWindow() != null) {
            AiResolvedTimeWindow tw = rqCtx.getTimeWindow();
            if (tw.getStartDate() != null && tw.getEndDate() != null) {
                return "resolvedQueryContext.timeWindow.explicitDates";
            }
            return "resolvedQueryContext.timeWindow.incomplete_dates";
        }
        return "aiRunState.statStartDate.statEndDate";
    }
}
