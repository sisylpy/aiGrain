package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 {@link BusinessToolExecutionNode} 完成 {@link AiBusinessToolIds#STOCK_REDUCE_QUERY} 后，
 * 基于 Tool 已返回结构生成 {@link StockReduceAnswerPlan}（不重查 SQL）。
 */
@Slf4j
public final class StockReduceAnswerPlanBuilder {

    private StockReduceAnswerPlanBuilder() {
    }

    public static void attachIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        boolean planned = state.getDataPlanTools() != null
                && state.getDataPlanTools().contains(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        boolean path = state.isStockReduceQueryPath();
        boolean hasToolEnvelope = state.getToolResults() != null
                && state.getToolResults().containsKey(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        if (!planned && !path && !hasToolEnvelope) {
            return;
        }

        LinkedHashMap<String, Object> baseDiag = new LinkedHashMap<>();
        baseDiag.put("attachAttempted", true);
        baseDiag.put("expectedToolKey", AiBusinessToolIds.STOCK_REDUCE_QUERY);
        baseDiag.put("source", "StockReduceQueryTool");
        baseDiag.put("sourceToolKey", AiBusinessToolIds.STOCK_REDUCE_QUERY);
        baseDiag.put("toolResultKeys",
                state.getToolResults() == null ? null : state.getToolResults().keySet());

        // === CONTRACT-LOCKED GATE (P1) ===
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        AiQuerySemanticParseResult sem = rq != null ? rq.getQuerySemanticParse() : null;
        boolean contractLocked = SemanticContractCompletionEngine.isContractLockedParse(sem);
        baseDiag.put("contractLockedParse", contractLocked);
        if (!contractLocked) {
            baseDiag.put("earlyReturnReason", "non_contract_locked_parse");
            baseDiag.put("failureReason", "non_contract_locked_parse");
            baseDiag.put("failureDetail",
                    "StockReduce AnswerPlan requires contract-locked parse; "
                            + "non-contract-locked must early exit / clarification / known gap");
            log.info("[StockReduceAnswerPlan] earlyExit runId={} reason=non_contract_locked_parse", state.getRunId());
            writeEmptyEarlyExitPlan(state, baseDiag);
            return;
        }
        // contract-locked but no completed wire → early exit
        String completedWire = resolveWire(rq);
        baseDiag.put("completedWire", completedWire.isEmpty() ? null : completedWire);
        boolean multiDomainOrchestrationAttach =
                BusinessOverviewSubPlanAttachSupport.isMultiDomainOrchestrationSubPlanAttach(state, rq);
        if (!multiDomainOrchestrationAttach && completedWire.isEmpty()) {
            baseDiag.put("earlyReturnReason", "missing_contract_completed_wire");
            baseDiag.put("failureReason", "missing_contract_completed_wire");
            baseDiag.put("failureDetail",
                    "contract-locked but no completed StructuredIntentDetail wire in queryIntent");
            log.info("[StockReduceAnswerPlan] earlyExit runId={} reason=missing_contract_completed_wire",
                    state.getRunId());
            writeEmptyEarlyExitPlan(state, baseDiag);
            return;
        }
        // wire not in StockReduce accepted canonical wire set → early exit
        String canon = completedWire.isEmpty() ? null
                : AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(completedWire.trim());
        if (!multiDomainOrchestrationAttach
                && (canon == null || !AiQuerySemanticLexicon.isStructuredStockReduceDetail(canon))) {
            baseDiag.put("earlyReturnReason", "contract_wire_not_accepted_stock_reduce_matrix");
            baseDiag.put("failureReason", "contract_wire_not_accepted_stock_reduce_matrix");
            baseDiag.put("failureDetail", "contract wire not accepted by StockReduce matrix: " + completedWire);
            baseDiag.put("rejectedWire", completedWire);
            log.info("[StockReduceAnswerPlan] earlyExit runId={} reason=contract_wire_not_accepted", state.getRunId());
            writeEmptyEarlyExitPlan(state, baseDiag);
            return;
        }
        if (multiDomainOrchestrationAttach) {
            baseDiag.put("attachMode", BusinessOverviewSubPlanAttachSupport.ATTACH_MODE);
            baseDiag.put("orchestrationSubPlanWire",
                    BusinessOverviewSubPlanAttachSupport.contractCompletedWire(rq));
        }

        Object env = state.getToolResults() == null ? null
                : state.getToolResults().get(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        baseDiag.put("hasStockReduceToolResult", env != null);
        baseDiag.put("dataClass", env == null ? null : env.getClass().getName());

        if (!(env instanceof Map<?, ?> envMapRaw)) {
            attachFailure(state, baseDiag, "missing_or_invalid_tool_envelope",
                    "toolResults[" + AiBusinessToolIds.STOCK_REDUCE_QUERY + "] is missing or not a Map");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> envMap = (Map<String, Object>) envMapRaw;
        boolean envSuccess = Boolean.TRUE.equals(envMap.get("success"));
        if (!envSuccess) {
            baseDiag.put("foundDataPath", "envelope.success=false");
            attachFailure(state, baseDiag, "tool_envelope_unsuccessful",
                    envMap.get("message") == null ? "success=false" : envMap.get("message").toString());
            return;
        }

        Object dataObj = unwrapDataMaybeJsonString(envMap.get("data"));
        Map<String, Object> inner = extractStockReduceInnerData(dataObj, baseDiag);
        baseDiag.put("foundStockReduceOverview", !inner.isEmpty());
        if (inner.isEmpty()) {
            attachFailure(state, baseDiag, "empty_inner_data", "data map has no stock reduce totals fields");
            return;
        }
        if (innerHasBlockingError(inner)) {
            attachFailure(state, baseDiag, "inner_error", String.valueOf(inner.get("error")));
            return;
        }

        try {
            StockReduceAnswerPlan plan = build(state, inner, rq, baseDiag);
            state.setStockReduceAnswerPlan(plan);
            log.info("[StockReduceAnswerPlan] attached runId={} type={} focusSize={} secondarySize={}",
                    state.getRunId(),
                    plan.getPlanType(),
                    plan.getFocusRows() == null ? 0 : plan.getFocusRows().size(),
                    plan.getSecondaryRows() == null ? 0 : plan.getSecondaryRows().size());
        } catch (Exception ex) {
            log.warn("[StockReduceAnswerPlan] attach failed runId={}", state.getRunId(), ex);
            baseDiag.put("failureReason", "build_exception");
            baseDiag.put("exception", ex.getClass().getName());
            attachFailure(state, baseDiag, "build_exception", ex.getMessage());
        }
    }

    private static void writeEmptyEarlyExitPlan(AiRunState state, Map<String, Object> diag) {
        StockReduceAnswerPlan plan = StockReduceAnswerPlan.builder()
                .planType("UNKNOWN")
                .reduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                .scopeLabel("")
                .timeLabel("")
                .summary(new LinkedHashMap<>())
                .focusRows(new ArrayList<>())
                .secondaryRows(new ArrayList<>())
                .debug(new LinkedHashMap<>(diag))
                .build();
        state.setStockReduceAnswerPlan(plan);
    }

    private static void attachFailure(AiRunState state, Map<String, Object> diag, String reasonCode, String detail) {
        diag.put("failureReason", reasonCode);
        diag.put("failureDetail", detail);
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        String wire = resolveWire(rq);
        String planType = resolvePlanType(wire);
        StockReduceAnswerPlan plan = StockReduceAnswerPlan.builder()
                .planType(planType)
                .reduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                .scopeLabel(resolveScopeLabel(Map.of(), rq))
                .timeLabel(resolveTimeLabel(state, rq))
                .summary(new LinkedHashMap<>())
                .focusRows(new ArrayList<>())
                .secondaryRows(new ArrayList<>())
                .debug(new LinkedHashMap<>(diag))
                .build();
        state.setStockReduceAnswerPlan(plan);
        log.info("[StockReduceAnswerPlan] attachFailure runId={} reason={}", state.getRunId(), reasonCode);
    }

    static StockReduceAnswerPlan build(AiRunState state, Map<String, Object> inner, AiResolvedQueryContext rq,
            LinkedHashMap<String, Object> debug) {
        String wire = resolveWire(rq);
        String planType = BusinessOverviewSubPlanAttachSupport.isMultiDomainOrchestrationSubPlanAttach(state, rq)
                ? StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW
                : resolvePlanType(wire);
        String reduceType = resolveReduceType(planType);
        String scopeLabel = resolveScopeLabel(inner, rq);
        String timeLabel = resolveTimeLabel(state, rq);

        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>(debug);
        dbg.put("source", "StockReduceQueryTool");
        dbg.put("resolvedPlanType", planType);
        dbg.put("reduceType", reduceType);
        dbg.put("structuredIntentDetailWire", wire.isEmpty() ? null : wire);
        dbg.put("sourceToolKey", AiBusinessToolIds.STOCK_REDUCE_QUERY);
        dbg.put("foundDataPath", dbg.getOrDefault("foundDataPath", "envelope.data"));

        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW.equals(planType)) {
            dbg.put("narrative", "OUTPUT");
        }

        BigDecimal produce = nz(inner.get("produceTotal"));
        BigDecimal waste = nz(inner.get("wasteTotal"));
        BigDecimal loss = nz(inner.get("lossTotal"));
        BigDecimal ret = nz(inner.get("returnTotal"));
        BigDecimal grand = nz(inner.get("grandTotalFourTypes"));
        if (grand.compareTo(BigDecimal.ZERO) == 0) {
            grand = produce.add(waste).add(loss).add(ret);
        }

        List<Map<String, Object>> focusRows = new ArrayList<>();
        List<Map<String, Object>> secondaryRows = new ArrayList<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("grandTotalFourTypes", grand.doubleValue());
        summary.put("produceTotal", produce.doubleValue());
        summary.put("wasteTotal", waste.doubleValue());
        summary.put("lossTotal", loss.doubleValue());
        summary.put("returnTotal", ret.doubleValue());
        if (inner.get("totalsBasis") != null) {
            summary.put("totalsBasis", inner.get("totalsBasis").toString());
        }

        fillRowsForPlanType(planType, inner, produce, waste, loss, ret, grand, focusRows, secondaryRows, dbg);

        if (!dbg.containsKey("sortKey")) {
            dbg.put("sortKey", null);
            dbg.put("sortDirection", null);
        }

        enrichStockReduceMatrixDebug(dbg, rq, planType, wire);

        return StockReduceAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .reduceType(reduceType)
                .summary(summary)
                .focusRows(focusRows)
                .secondaryRows(secondaryRows)
                .debug(dbg)
                .build();
    }

    private static void fillRowsForPlanType(String planType, Map<String, Object> inner,
            BigDecimal produce, BigDecimal waste, BigDecimal loss, BigDecimal ret, BigDecimal grand,
            List<Map<String, Object>> focusRows, List<Map<String, Object>> secondaryRows,
            LinkedHashMap<String, Object> dbg) {
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW.equals(planType)) {
            focusRows.add(amountRow("ALL", "出库/核销合计（type1+2+3+4）", grand, inner));
            secondaryRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE1, "生产耗用（type1）", produce, inner));
            secondaryRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE2, "废弃（type2）", waste, inner));
            secondaryRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE3, "损耗/报损（type3）", loss, inner));
            secondaryRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE4, "退货（type4）", ret, inner));
            return;
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW.equals(planType)
                || StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW.equals(planType)) {
            String label = StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW.equals(planType)
                    ? "出品（口径同 type1）" : "生产耗用（type1）";
            focusRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE1, label, produce, inner));
            return;
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW.equals(planType)) {
            focusRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE2, "废弃（type2）", waste, inner));
            return;
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW.equals(planType)) {
            focusRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE3, "损耗/报损（type3）", loss, inner));
            return;
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW.equals(planType)) {
            focusRows.add(amountRow(StockReduceAnswerPlan.REDUCE_TYPE_TYPE4, "退货（type4）", ret, inner));
            return;
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING.equals(planType)) {
            List<Map<String, Object>> top = castRowList(inner.get("topGoodsOutboundBySubtotal"));
            dbg.put("sortKey", "goodsSubtotalAmount");
            dbg.put("sortDirection", "DESC");
            if (top.isEmpty()) {
                dbg.put("failureReason", "missing_top_goods_subtotal_list");
                dbg.put("failureDetail", "topGoodsOutboundBySubtotal absent or empty; no SQL change in phase 1");
                return;
            }
            splitTopRows(top, focusRows, secondaryRows);
            return;
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING.equals(planType)) {
            List<Map<String, Object>> top = castRowList(inner.get("topGoodsOutboundByOutboundTimes"));
            dbg.put("sortKey", "outboundTimes");
            dbg.put("sortDirection", "DESC");
            if (top.isEmpty()) {
                dbg.put("failureReason", "missing_top_goods_outbound_times_list");
                dbg.put("failureDetail", "topGoodsOutboundByOutboundTimes absent or empty");
                return;
            }
            splitTopRows(top, focusRows, secondaryRows);
            return;
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING.equals(planType)) {
            List<Map<String, Object>> top = castRowList(inner.get("topStoresOutboundByGrandTotal"));
            dbg.put("sortKey", "grandTotalFourTypes");
            dbg.put("sortDirection", "DESC");
            if (top.isEmpty()) {
                dbg.put("failureReason", "missing_top_stores_grand_total_list");
                dbg.put("failureDetail", "topStoresOutboundByGrandTotal absent or empty");
                return;
            }
            splitTopRows(top, focusRows, secondaryRows);
            return;
        }
    }

    private static Map<String, Object> amountRow(String reduceTypeKey, String label, BigDecimal amount,
            Map<String, Object> inner) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("reduceType", reduceTypeKey);
        row.put("label", label);
        row.put("amount", amount.doubleValue());
        if (inner.get("totalsBasis") != null) {
            row.put("totalsBasis", inner.get("totalsBasis").toString());
        }
        return row;
    }

    static String resolvePlanType(String wire) {
        String w = wire == null ? "" : AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (w == null) {
            w = wire == null ? "" : wire.trim();
        }
        if (AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PRODUCE_OUTPUT.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PRODUCE_CONSUME.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_WASTE.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_LOSS.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_RETURN.equals(w)) {
            return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW;
        }
        return StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW;
    }

    static String resolveReduceType(String planType) {
        return switch (planType) {
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE1;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE2;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE3;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW -> StockReduceAnswerPlan.REDUCE_TYPE_TYPE4;
            case StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING,
                    StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING -> "RANKING";
            default -> StockReduceAnswerPlan.REDUCE_TYPE_ALL;
        };
    }

    private static String resolveWire(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return "";
        }
        AiResolvedQueryIntent qi = rq.getQueryIntent();
        if (qi.getStructuredIntentDetail() == null || qi.getStructuredIntentDetail().isBlank()) {
            return "";
        }
        return qi.getStructuredIntentDetail().trim();
    }

    private static void enrichStockReduceMatrixDebug(
            LinkedHashMap<String, Object> dbg,
            AiResolvedQueryContext rq,
            String planType,
            String wire) {
        if (dbg == null || rq == null) {
            return;
        }
        String path = rq.getEffectivePathCode();
        if (path == null || path.isBlank()) {
            path = rq.getQueryIntent() != null ? rq.getQueryIntent().getPathCode() : null;
        }
        AiQuerySemanticParseResult sem = rq.getQuerySemanticParse();
        String canonWire =
                StringUtils.hasText(wire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim())
                        : null;
        StockReduceSemanticCapabilityMatrixRow row =
                StockReduceSemanticCapabilityMatrix.resolveMatrixRow(path, canonWire, sem);
        if (row != null) {
            dbg.put("stockReduceMatrixRowId", row.getRowId());
            dbg.put("stockReduceStructuredIntentDetailWire", row.getStructuredIntentDetailWire());
            String gap = StockReduceSemanticCapabilityMatrix.knownGapForResolvedRow(row);
            if (gap != null) {
                dbg.put("stockReduceKnownGap", gap);
            }
        } else if (StringUtils.hasText(canonWire)) {
            dbg.put("stockReduceStructuredIntentDetailWire", canonWire);
        }
        if (StockReduceSemanticCapabilityMatrix.detectMatrixWireMissing(sem, path, canonWire)) {
            dbg.put("stockReduceMatrixWireMissing", StockReduceSemanticCapabilityMatrix.MATRIX_WIRE_MISSING);
        }
        dbg.put("stockReduceAnswerPlanType", planType);
        dbg.put("stockReduceReduceType", resolveReduceType(planType));
        if (sem != null && sem.getMetric() != null && sem.getMetric().getStockReduceType() != null) {
            dbg.put("stockReduceTypeFacetDebug", sem.getMetric().getStockReduceType().trim());
        }
    }

    private static String resolveScopeLabel(Map<String, Object> inner, AiResolvedQueryContext rq) {
        Object b = inner.get("queryScopeBanner");
        if (b != null && !b.toString().isBlank()) {
            return b.toString().trim();
        }
        if (rq != null && rq.getQueryScopeBanner() != null && !rq.getQueryScopeBanner().isBlank()) {
            return rq.getQueryScopeBanner().trim();
        }
        return "";
    }

    private static String resolveTimeLabel(AiRunState state, AiResolvedQueryContext rq) {
        if (rq != null && rq.getTimeWindowLabel() != null && !rq.getTimeWindowLabel().isBlank()) {
            return rq.getTimeWindowLabel().trim();
        }
        String start = state.getStatStartDate();
        String end = state.getStatEndDate();
        if (start != null && end != null && !start.isBlank() && !end.isBlank()) {
            return start + " 至 " + end;
        }
        return "";
    }

    private static boolean innerHasBlockingError(Map<String, Object> inner) {
        if (inner == null || inner.isEmpty()) {
            return false;
        }
        Object err = inner.get("error");
        return err != null && !err.toString().isBlank();
    }

    private static Object unwrapDataMaybeJsonString(Object data) {
        if (data instanceof String s && !s.isBlank()) {
            try {
                return JSON.parseObject(s);
            } catch (Exception ignore) {
                return data;
            }
        }
        return data;
    }

    /**
     * Tool 信封 {@code data}：扁平字段见 {@link StockReduceQueryTool}；或嵌套 {@code data.data}。
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> extractStockReduceInnerData(Object dataObj, Map<String, Object> diag) {
        Object node = dataObj;
        if (node instanceof Map<?, ?> m) {
            Object nested = m.get("data");
            if (nested instanceof Map<?, ?> && m.containsKey("schemaVersion")) {
                node = nested;
                diag.put("foundDataPath", "envelope.data.data");
            } else {
                diag.put("foundDataPath", "envelope.data");
            }
        }
        if (!(node instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        Map<String, Object> map = (Map<String, Object>) dm;
        if (looksLikeStockReduceDataMap(map)) {
            return new LinkedHashMap<>(map);
        }
        Map<String, Object> deep = deepFindStockReduceShape(map, 4);
        if (!deep.isEmpty()) {
            diag.put("foundDataPath", diag.get("foundDataPath") + "+deepScan");
            return deep;
        }
        Object rr = map.get("rawReduceTotals");
        if (rr instanceof Map<?, ?> rm) {
            LinkedHashMap<String, Object> lifted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) rr).entrySet()) {
                lifted.put(String.valueOf(e.getKey()), e.getValue());
            }
            lifted.putAll(map);
            if (looksLikeStockReduceDataMap(lifted)) {
                diag.put("foundDataPath", "rawReduceTotals+merge");
                return lifted;
            }
        }
        return Map.of();
    }

    private static boolean looksLikeStockReduceDataMap(Map<String, Object> map) {
        return map.containsKey("produceTotal") || map.containsKey("grandTotalFourTypes")
                || map.containsKey("topGoodsOutboundBySubtotal")
                || map.containsKey("topGoodsOutboundByOutboundTimes")
                || map.containsKey("topStoresOutboundByGrandTotal");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepFindStockReduceShape(Object node, int depth) {
        if (depth <= 0 || !(node instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, Object> asObj = (Map<String, Object>) m;
        if (looksLikeStockReduceDataMap(asObj)) {
            return new LinkedHashMap<>(asObj);
        }
        for (Object v : m.values()) {
            Map<String, Object> hit = deepFindStockReduceShape(v, depth - 1);
            if (!hit.isEmpty()) {
                return hit;
            }
        }
        return Map.of();
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRowList(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        return out;
    }

    private static void splitTopRows(List<Map<String, Object>> ordered,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        if (ordered == null || ordered.isEmpty()) {
            return;
        }
        focusRows.add(new LinkedHashMap<>(ordered.get(0)));
        for (int i = 1; i < ordered.size(); i++) {
            secondaryRows.add(new LinkedHashMap<>(ordered.get(i)));
        }
    }
}
