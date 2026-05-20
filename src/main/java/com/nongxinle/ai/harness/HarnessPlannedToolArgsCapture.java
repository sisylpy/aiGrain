package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.toolrequest.PurchaseToolRequestContext;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness 阶段 2：在 {@code Tool.execute} 之前快照 Tool Request（args + RequestContext）。
 */
public final class HarnessPlannedToolArgsCapture {

    private HarnessPlannedToolArgsCapture() {
    }

    public static Map<String, Object> snapshotPurchase(
            AiRunState state,
            AiResolvedQueryContext rq,
            String toolId,
            Map<String, Object> args,
            PurchaseToolRequestContext ctx) {
        LinkedHashMap<String, Object> snap = baseSnapshot(state, rq, toolId, args);
        snap.put("requestContextType", "PurchaseToolRequestContext");
        if (ctx != null) {
            snap.put("startDate", blankToNull(ctx.getStartDateIso()));
            snap.put("endDate", blankToNull(ctx.getEndDateIso()));
            snap.put("scopeType", blankToNull(ctx.getOrgScopeType()));
            snap.put("expandedSqlDepartmentIds", copyLongList(ctx.getEffectiveSqlDepartmentIds()));
            snap.put("purchaseSqlDepartmentIds", copyLongList(ctx.getPurchaseSqlDepartmentIds()));
            snap.put("visibleStoreRootIds", copyLongList(ctx.getVisibleStoreRootIds()));
            snap.put("resolutionDebug", ctx.getResolutionDebug() == null || ctx.getResolutionDebug().isEmpty()
                    ? null
                    : new LinkedHashMap<>(ctx.getResolutionDebug()));
        }
        putGroupAggregationFlags(snap, state, args);
        return snap;
    }

    /** 通用 Tool Request 快照（revenue / stock_reduce / dish_profit / warehouse 等）。 */
    public static Map<String, Object> snapshotToolRequest(
            AiRunState state,
            AiResolvedQueryContext rq,
            String toolId,
            Map<String, Object> args) {
        LinkedHashMap<String, Object> snap = baseSnapshot(state, rq, toolId, args);
        snap.put("requestContextType", "ToolRequestArgs");
        putDatesFromArgs(snap, args);
        putScopeFromResolvedContext(snap, rq);
        putGroupAggregationFlags(snap, state, args);
        return snap;
    }

    private static LinkedHashMap<String, Object> baseSnapshot(
            AiRunState state,
            AiResolvedQueryContext rq,
            String toolId,
            Map<String, Object> args) {
        LinkedHashMap<String, Object> snap = new LinkedHashMap<>();
        snap.put("toolId", toolId);
        if (rq != null) {
            snap.put("effectiveIntentCode", blankToNull(rq.getEffectiveIntentCode()));
            snap.put("effectivePathCode", blankToNull(rq.getEffectivePathCode()));
            String wire = null;
            if (rq.getQueryIntent() != null) {
                wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        rq.getQueryIntent().getStructuredIntentDetail());
            }
            snap.put("canonicalStructuredIntentDetailWire", blankToNull(wire));
        } else {
            snap.put("effectiveIntentCode", null);
            snap.put("effectivePathCode", null);
            snap.put("canonicalStructuredIntentDetailWire", null);
        }
        snap.put("visibleStores", visibleStoresSnapshot(rq));
        snap.put("args", args == null || args.isEmpty() ? null : new LinkedHashMap<>(args));
        if (args != null && args.get(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS) != null) {
            snap.put("resolvedDepartmentIds", copyObjectList(args.get(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS)));
        }
        return snap;
    }

    private static void putDatesFromArgs(LinkedHashMap<String, Object> snap, Map<String, Object> args) {
        if (args == null) {
            snap.put("startDate", null);
            snap.put("endDate", null);
            return;
        }
        Object start = args.get(AiBusinessToolIds.ARG_START_DATE);
        Object stop = args.get(AiBusinessToolIds.ARG_STOP_DATE);
        snap.put("startDate", start == null ? null : start.toString());
        snap.put("endDate", stop == null ? null : stop.toString());
    }

    private static void putScopeFromResolvedContext(LinkedHashMap<String, Object> snap, AiResolvedQueryContext rq) {
        if (rq == null) {
            snap.put("scopeType", null);
            snap.put("expandedSqlDepartmentIds", null);
            snap.put("visibleStoreRootIds", null);
            return;
        }
        if (rq.getOrgScope() != null) {
            snap.put("scopeType", blankToNull(rq.getOrgScope().getScopeType()));
        } else {
            snap.put("scopeType", null);
        }
        if (rq.getDataScope() != null) {
            AiResolvedDataScope ds = rq.getDataScope();
            snap.put("expandedSqlDepartmentIds", copyLongList(ds.getEffectiveSqlDepartmentIds()));
            snap.put("visibleStoreRootIds", copyLongList(ds.getVisibleStoreRootIds()));
        } else {
            snap.put("expandedSqlDepartmentIds", null);
            snap.put("visibleStoreRootIds", null);
        }
    }

    private static void putGroupAggregationFlags(
            LinkedHashMap<String, Object> snap, AiRunState state, Map<String, Object> args) {
        if (state != null && state.isGroupPurchaseOverview()) {
            snap.put("groupPurchaseOverview", Boolean.TRUE);
        }
        if (state != null && state.isGroupStockReduceQuery()) {
            snap.put("groupStockReduceQuery", Boolean.TRUE);
        }
        if (state != null && state.isGroupWarehouseStockOverview()) {
            snap.put("groupWarehouseStockOverview", Boolean.TRUE);
        }
        if (args == null) {
            return;
        }
        if (Boolean.TRUE.equals(args.get(AiBusinessToolIds.ARG_GROUP_PURCHASE_AGGREGATION))) {
            snap.put("groupPurchaseAggregation", Boolean.TRUE);
        }
        if (Boolean.TRUE.equals(args.get(AiBusinessToolIds.ARG_GROUP_STOCK_REDUCE_AGGREGATION))) {
            snap.put("groupStockReduceAggregation", Boolean.TRUE);
        }
        if (Boolean.TRUE.equals(args.get(AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT))) {
            snap.put("groupWideOverviewHint", Boolean.TRUE);
        }
        if (Boolean.TRUE.equals(args.get(AiBusinessToolIds.ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION))) {
            snap.put("groupWarehouseStockAggregation", Boolean.TRUE);
        }
    }

    private static List<Map<String, Object>> visibleStoresSnapshot(AiResolvedQueryContext rq) {
        if (rq == null || rq.getOrgScope() == null) {
            return null;
        }
        AiResolvedOrgScope org = rq.getOrgScope();
        List<AiStoreScopeDTO> stores = org.getVisibleStores();
        if (stores == null || stores.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>(stores.size());
        for (AiStoreScopeDTO s : stores) {
            if (s == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("storeName", blankToNull(s.getStoreName()));
            row.put("storeDepartmentId", s.getStoreDepartmentId());
            rows.add(row);
        }
        return rows.isEmpty() ? null : rows;
    }

    private static List<Long> copyLongList(List<Long> src) {
        if (src == null || src.isEmpty()) {
            return null;
        }
        return new ArrayList<>(src);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> copyObjectList(Object src) {
        if (!(src instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        return new ArrayList<>((List<Object>) list);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
