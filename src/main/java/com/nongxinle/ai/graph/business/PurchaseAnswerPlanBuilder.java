package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntent;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntentResolver;
import com.nongxinle.ai.graph.business.execution.RequiresAnchorExecutionGateSupport;
import com.nongxinle.ai.identity.BusinessEntityExistenceLookup;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在 {@link BusinessToolExecutionNode} 完成 {@link AiBusinessToolIds#PURCHASE_OVERVIEW} 后，
 * 基于 Tool 已排序/已过滤的结果生成 {@link PurchaseAnswerPlan}（不重查 SQL）。
 */
@Slf4j
public final class PurchaseAnswerPlanBuilder {

    private PurchaseAnswerPlanBuilder() {
    }

    public static void attachIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        boolean plannedPurchaseOverview = state.getDataPlanTools() != null
                && state.getDataPlanTools().contains(AiBusinessToolIds.PURCHASE_OVERVIEW);
        Map<String, Object> overview = extractPurchaseOverviewPayload(state);
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (overview.isEmpty()) {
            if (plannedPurchaseOverview && shouldAttachAnchorIdentityFailurePlan(state, rq)) {
                attachAnchorIdentityFailurePlan(state, rq);
            } else if (plannedPurchaseOverview) {
                log.warn("[PurchaseAnswerPlan] skip empty overview runId={} toolResultKeys={} hasPurchaseEnvelope={}",
                        state.getRunId(),
                        state.getToolResults() == null ? null : state.getToolResults().keySet(),
                        state.getToolResults() != null
                                && state.getToolResults().containsKey(AiBusinessToolIds.PURCHASE_OVERVIEW));
            }
            return;
        }
        // 仅在有「非空 error 文案」时跳过（避免某些序列化层写入 error=null 误杀）
        if (overviewHasBlockingError(overview)) {
            if (plannedPurchaseOverview) {
                log.warn("[PurchaseAnswerPlan] skip overview.error runId={} err={}",
                        state.getRunId(), overview.get("error"));
            }
            return;
        }
        try {
            PurchaseAnswerPlan plan = build(state, overview, rq);
            state.setPurchaseAnswerPlan(plan);
            List<?> ra = plan.getResultAnchors();
            log.info(
                    "[PurchaseAnswerPlan] attached runId={} type={} focusSize={} secondarySize={} resultAnchorsCount={}",
                    state.getRunId(),
                    plan.getPlanType(),
                    plan.getFocusRows() == null ? 0 : plan.getFocusRows().size(),
                    plan.getSecondaryRows() == null ? 0 : plan.getSecondaryRows().size(),
                    ra == null || ra.isEmpty() ? 0 : ra.size());
        } catch (Exception ex) {
            log.warn("[PurchaseAnswerPlan] attach failed runId={}", state.getRunId(), ex);
            state.setPurchaseAnswerPlan(null);
        }
    }

    static PurchaseAnswerPlan build(AiRunState state, Map<String, Object> overview, AiResolvedQueryContext rq) {
        PurchaseSemanticExecutionIntent executionIntent = PurchaseSemanticExecutionIntentResolver.project(rq);
        boolean contractLocked = rq != null && rq.getQuerySemanticParse() != null
                && SemanticContractCompletionEngine.isContractLockedParse(rq.getQuerySemanticParse());

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();

        if (!contractLocked) {
            debug.put("earlyReturnReason", "non_contract_locked_parse");
            return PurchaseAnswerPlan.builder()
                    .planType("")
                    .scopeLabel("")
                    .timeLabel("")
                    .purchaseSourceType("")
                    .summary(Map.of())
                    .focusRows(List.of())
                    .secondaryRows(List.of())
                    .debug(debug)
                    .resultAnchors(List.of())
                    .build();
        }

        boolean multiDomainOrchestrationAttach =
                BusinessOverviewSubPlanAttachSupport.isMultiDomainOrchestrationSubPlanAttach(state, rq);

        String wire = resolveStructuredWireForPlan(rq);
        if (!multiDomainOrchestrationAttach && !StringUtils.hasText(wire)) {
            debug.put("earlyReturnReason", "missing_contract_completed_wire");
            return PurchaseAnswerPlan.builder()
                    .planType("")
                    .scopeLabel("")
                    .timeLabel("")
                    .purchaseSourceType("")
                    .summary(Map.of())
                    .focusRows(List.of())
                    .secondaryRows(List.of())
                    .debug(debug)
                    .resultAnchors(List.of())
                    .build();
        }
        if (!multiDomainOrchestrationAttach && !AiQuerySemanticLexicon.isPurchaseOverviewDomainCanonicalWire(wire)) {
            debug.put("earlyReturnReason", "contract_wire_not_accepted_purchase_matrix");
            debug.put("rejectedWire", wire);
            return PurchaseAnswerPlan.builder()
                    .planType("")
                    .scopeLabel("")
                    .timeLabel("")
                    .purchaseSourceType("")
                    .summary(Map.of())
                    .focusRows(List.of())
                    .secondaryRows(List.of())
                    .debug(debug)
                    .resultAnchors(List.of())
                    .build();
        }
        if (multiDomainOrchestrationAttach) {
            debug.put("attachMode", BusinessOverviewSubPlanAttachSupport.ATTACH_MODE);
            debug.put("orchestrationSubPlanWire",
                    BusinessOverviewSubPlanAttachSupport.contractCompletedWire(rq));
        }

        RequiresAnchorExecutionGateSupport.Decision gate = RequiresAnchorExecutionGateSupport.evaluate(rq);
        if (gate.blocksToolExecution() || isOverviewAnchorIdentityFailure(overview)) {
            return buildGoodsAnchorIdentityFailurePlan(state, rq, executionIntent, gate, overview, debug);
        }

        String pst = AiQuerySemanticLexicon.SOURCE_ALL;
        if (rq != null && rq.getQueryIntent() != null) {
            AiResolvedQueryIntent qi = rq.getQueryIntent();
            if (qi.getPurchaseSourceType() != null && !qi.getPurchaseSourceType().isBlank()) {
                pst = qi.getPurchaseSourceType().trim();
            }
        }
        if (executionIntent.isActive() && StringUtils.hasText(executionIntent.getSourceFacet())) {
            pst = executionIntent.getSourceFacet().trim();
        }

        String planType = multiDomainOrchestrationAttach
                ? PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW
                : resolvePlanTypeFromContractOrWire(rq, wire, pst);
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(planType)) {
            if (pst == null
                    || pst.isBlank()
                    || AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(pst.trim())) {
                pst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
            }
        }
        planType = applyExecutionIntentPlanType(planType, executionIntent, overview, pst);
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
            pst = AiQuerySemanticLexicon.SOURCE_ALL;
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)
                && (pst == null
                        || pst.isBlank()
                        || AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(pst.trim()))) {
            pst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        String scopeLabel = resolveScopeLabel(overview, rq);
        String timeLabel = resolveTimeLabel(state, rq);

        Map<String, Object> summary = buildSummary(overview);
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
            overlaySummaryFromGoodsSourceBreakdown(summary, overview);
        }
        List<Map<String, Object>> focusRows = new ArrayList<>();
        List<Map<String, Object>> secondaryRows = new ArrayList<>();
        fillRows(planType, wire, overview, focusRows, secondaryRows);
        if (PurchaseAnswerPlan.TYPE_PURCHASE_ANOMALY.equals(planType)) {
            appendPurchaseAnomalyPlanObservation(debug, wire, overview, focusRows);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING.equals(planType)) {
            appendPurchaseGoodsQuantityRankingCaliberObservation(debug, focusRows, secondaryRows);
        }

        debug.put("structuredIntentDetailWire", wire.isEmpty() ? null : wire);
        debug.put("resolvedPlanType", planType);
        if (executionIntent.isActive()) {
            debug.put("purchaseExecutionIntentType", executionIntent.getExecutionIntentType());
            debug.put("purchaseMatchedContractId", emptyToNull(executionIntent.getMatchedContractId()));
            debug.put("purchaseExecutionDetailWanted", emptyToNull(executionIntent.getDetailWanted()));
            debug.put("purchaseExecutionAnchorResolved", executionIntent.isAnchorResolved());
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(planType)) {
            debug.put("goodsRankingFocusRowsSize", focusRows.size());
            debug.put("goodsRankingFocusRow0GoodsName", firstGoodsNameFromRow(focusRows.isEmpty() ? null : focusRows.get(0)));
        }
        debug.put("source", "PurchaseOverviewTool");
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "totalPurchaseAmount");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "purchaseSubtotalPerStore");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "totalPurchaseAmount");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(planType)) {
            debug.put("sortKey", "purchaseTimes");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING.equals(planType)) {
            debug.put("sortKey", "purchaseQuantity");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
            debug.put("sortKey", null);
            debug.put("sortDirection", null);
        } else if (isUnifiedPurchaseGoodsDetailPlan(planType)) {
            debug.put("sortKey", "supplierPurchaseAmount");
            debug.put("sortDirection", "DESC");
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL.equals(planType)) {
            debug.put("sortKey", "amount");
            debug.put("sortDirection", "DESC");
            debug.put("periodGoodsDetailFocusRowsSize", focusRows.size());
            mirrorPeriodGoodsDetailOverviewToDebug(overview, debug);
        }

        mergePurchaseAnchorExecutionObservationIfPresent(overview, debug, planType);
        mergePurchaseGoodsSourceBreakdownHarnessFields(overview, debug, planType, rq);
        finalizePurchaseSupplierGoodsDetailHarnessDebug(overview, debug, planType, focusRows, secondaryRows);
        finalizePurchaseGoodsSourceBreakdownHarnessDebug(overview, debug, planType, focusRows, rq);
        appendPurchaseSupplierGoodsDetailExecutionDebug(planType, overview, rq, debug);
        appendPurchaseGoodsSourceBreakdownExecutionDebug(planType, overview, rq, debug);
        patchGoodsSupplierUnitPriceNoDataReason(planType, executionIntent, rq, focusRows, secondaryRows, debug);
        patchGoodsSupplierBreakdownNoDataReason(planType, executionIntent, rq, focusRows, secondaryRows, debug);

        List<AiResultAnchor> anchors = buildResultAnchors(planType, focusRows, overview, rq);
        if (!anchors.isEmpty()) {
            debug.put("resultAnchorsCount", anchors.size());
            if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
                debug.put("resultAnchorSourcePlanType", PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN);
            } else if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)) {
                debug.put("resultAnchorSourcePlanType", PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
            } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)
                    || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(planType)) {
                debug.put("resultAnchorSourcePlanType", planType);
            }
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(planType)) {
            debug.put("goodsRankingAnchorSkippedReason", diagnoseGoodsRankingAnchorSkip(planType, focusRows, overview));
        }

        return PurchaseAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .purchaseSourceType(pst)
                .summary(summary)
                .focusRows(focusRows)
                .secondaryRows(secondaryRows)
                .debug(debug)
                .resultAnchors(anchors)
                .build();
    }

    /**
     * contract-locked 仅读取 {@code queryIntent.structuredIntentDetail}（由
     * {@code applyCompletedContractFieldsToIntent} 写入），
     * 禁止从 raw semanticSlots / currentTurn wire 兜底。
     */
    private static String resolveStructuredWireForPlan(AiResolvedQueryContext rq) {
        if (rq != null && rq.getQueryIntent() != null) {
            String fromQi = rq.getQueryIntent().getStructuredIntentDetail();
            if (fromQi != null && !fromQi.isBlank()) {
                return fromQi.trim();
            }
        }
        return "";
    }

    private static String firstGoodsNameFromRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        for (String key : new String[] {"goodsName", "goodsTitle", "name"}) {
            Object v = row.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }

    private static String diagnoseGoodsRankingAnchorSkip(
            String planType, List<Map<String, Object>> focusRows, Map<String, Object> overview) {
        if (focusRows != null && !focusRows.isEmpty()) {
            String name = firstGoodsNameFromRow(focusRows.get(0));
            if (name == null) {
                return "focusRow0_missing_goods_name";
            }
            return "unknown";
        }
        List<Map<String, Object>> tops =
                PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)
                        ? firstNonEmptyRowList(
                                overview,
                                "goodsPurchaseAmountTop",
                                "goodsAmountTop",
                                "purchaseGoodsAmountTop")
                        : firstNonEmptyRowList(
                                overview,
                                "goodsPurchaseFrequencyTop",
                                "goodsFrequencyTop",
                                "goodsPurchaseCountTop",
                                "purchaseGoodsFrequencyTop");
        if (tops.isEmpty()) {
            return "overview_goods_top_empty";
        }
        if (firstGoodsNameFromRow(tops.get(0)) == null) {
            return "overview_top0_missing_goods_name";
        }
        return "focusRows_empty_overview_has_top";
    }

    /**
     * 仅依据解析层下发的 structuredIntentDetail wire（及采购来源枚举），禁止读取用户原文推断排行语义。
     */
    /**
     * contract-locked 时 AnswerPlanType 仅来自 completed semanticSlots（由 selectedContractId 命中的合同写入）；
     * 非 contract 路径才回退 wire 确定性映射。
     */
    static String resolvePlanTypeFromContractOrWire(
            AiResolvedQueryContext rq, String structuredWire, String purchaseSourceType) {
        if (rq != null
                && rq.getQuerySemanticParse() != null
                && SemanticContractCompletionEngine.isContractLockedParse(rq.getQuerySemanticParse())) {
            AiQuerySemanticParseResult.SemanticSlotsPart slots = rq.getQuerySemanticParse().getSemanticSlots();
            if (slots != null && StringUtils.hasText(slots.getAnswerPlanType())) {
                return slots.getAnswerPlanType().trim();
            }
        }
        return resolvePlanType(structuredWire, purchaseSourceType);
    }

    public static String resolvePlanType(String structuredWire, String purchaseSourceType) {
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        String wire = canon != null ? canon.trim() : (structuredWire == null ? "" : structuredWire.trim());
        String pst = purchaseSourceType == null ? AiQuerySemanticLexicon.SOURCE_ALL : purchaseSourceType.trim();

        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_QUANTITY_RANKING.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING;
        }

        if (AiQuerySemanticLexicon.isPurchaseAnomalyDetectionWire(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_ANOMALY;
        }

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL;
        }

        boolean self = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(pst);
        boolean sup = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(pst);

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(wire)) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_ANOMALY;
        }

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL;
            }
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
        }
        if (self) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
        }
        if (sup) {
            return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
        }
        return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
    }

    /**
     * AnswerPlanType 仅来自 contract-completed wire + Matrix/Resolver 确定性映射；
     * 禁止从 Tool payload 键值反向推断业务意图。
     */
    private static String applyExecutionIntentPlanType(
            String defaultPlanType,
            PurchaseSemanticExecutionIntent executionIntent,
            Map<String, Object> overview,
            String pst) {
        if (executionIntent != null
                && executionIntent.isActive()
                && StringUtils.hasText(executionIntent.getAnswerPlanType())) {
            return executionIntent.getAnswerPlanType();
        }
        return defaultPlanType;
    }

    /**
     * Harness 测试入口：委托 {@link PurchaseSemanticExecutionIntentResolver}（semantic contract + anchor 驱动）。
     */
    static boolean isGoodsSourceBreakdownIntent(AiResolvedQueryContext rq, String structuredWire, String purchaseSourceType) {
        PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.resolve(rq);
        if (!PurchaseSemanticExecutionIntent.EXEC_GOODS_SOURCE_BREAKDOWN.equals(
                intent.getExecutionIntentType())) {
            return false;
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return false;
        }
        String pst =
                purchaseSourceType == null || purchaseSourceType.isBlank()
                        ? AiQuerySemanticLexicon.SOURCE_ALL
                        : purchaseSourceType.trim();
        if (!AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(pst)) {
            return false;
        }
        return intent.isAnchorResolved()
                || AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(
                        emptyToNull(intent.getAnchorType()))
                || resolveInheritedGoodsAnchor(rq) != null;
    }

    /** Historical 测试入口：委托 execution intent resolver。 */
    static boolean isGoodsSupplierBreakdownExecutionIntent(AiResolvedQueryContext rq, String structuredWire) {
        PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.resolve(rq);
        if (!PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_BREAKDOWN.equals(
                intent.getExecutionIntentType())) {
            return false;
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire);
    }

    /** Historical 测试入口：委托 execution intent resolver。 */
    static boolean isGoodsSupplierUnitPriceExecutionIntent(AiResolvedQueryContext rq, String structuredWire) {
        PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.resolve(rq);
        if (!PurchaseSemanticExecutionIntent.EXEC_GOODS_SUPPLIER_UNIT_PRICE.equals(
                intent.getExecutionIntentType())) {
            return false;
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        return AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire);
    }

    private static void patchGoodsSupplierUnitPriceNoDataReason(
            String planType,
            PurchaseSemanticExecutionIntent executionIntent,
            AiResolvedQueryContext rq,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows,
            LinkedHashMap<String, Object> debug) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || debug == null) {
            return;
        }
        String wanted =
                executionIntent != null && StringUtils.hasText(executionIntent.getDetailWanted())
                        ? executionIntent.getDetailWanted()
                        : null;
        if (wanted == null
                || !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equalsIgnoreCase(wanted.trim())) {
            return;
        }
        int fr = focusRows == null ? 0 : focusRows.size();
        int sr = secondaryRows == null ? 0 : secondaryRows.size();
        if (fr + sr > 0) {
            return;
        }
        debug.put("purchaseSupplierGoodsDetailNoDataReason", "GOODS_SUPPLIER_UNIT_PRICE_NO_DATA");
        debug.put("noDataReason", "GOODS_SUPPLIER_UNIT_PRICE_NO_DATA");
    }

    private static void patchGoodsSupplierBreakdownNoDataReason(
            String planType,
            PurchaseSemanticExecutionIntent executionIntent,
            AiResolvedQueryContext rq,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows,
            LinkedHashMap<String, Object> debug) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || debug == null) {
            return;
        }
        String wanted =
                executionIntent != null && StringUtils.hasText(executionIntent.getDetailWanted())
                        ? executionIntent.getDetailWanted()
                        : null;
        if (wanted == null
                || !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equalsIgnoreCase(wanted.trim())) {
            return;
        }
        int fr = focusRows == null ? 0 : focusRows.size();
        int sr = secondaryRows == null ? 0 : secondaryRows.size();
        if (fr + sr > 0) {
            return;
        }
        debug.put("purchaseSupplierGoodsDetailNoDataReason", "GOODS_SUPPLIER_BREAKDOWN_NO_DATA");
        debug.put("noDataReason", "GOODS_SUPPLIER_BREAKDOWN_NO_DATA");
    }

    private static AiResultAnchor resolveInheritedGoodsAnchor(AiResolvedQueryContext rq) {
        if (rq == null || GoodsEntityDisplayNameSupport.hasCurrentTurnExplicitGoodsMention(rq)) {
            return null;
        }
        if (!GoodsEntityDisplayNameSupport.allowsPreviousGoodsAnchor(rq)) {
            return null;
        }
        AiConversationTurnMemory prev = rq.getPreviousTurn();
        if (prev == null || prev.getLastResultAnchors() == null) {
            return null;
        }
        for (AiResultAnchor a : prev.getLastResultAnchors()) {
            if (a == null || a.getEntityType() == null) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if ((a.getEntityName() == null || a.getEntityName().isBlank())
                    && (a.getEntityId() == null || a.getEntityId().isBlank())) {
                continue;
            }
            return a;
        }
        return null;
    }

    private static String resolveGoodsSourceBreakdownGoodsId(
            Map<String, Object> overview, AiResolvedQueryContext rq, Map<String, Object> focusRow) {
        String goodsId = focusRow != null ? firstNonBlankId(focusRow, "disGoodsId", "goodsId", "gbDisGoodsId") : null;
        if (goodsId == null && overview != null) {
            Object fid = overview.get("purchaseGoodsSourceBreakdownFocusDisGoodsId");
            if (fid != null && !fid.toString().isBlank()) {
                goodsId = fid.toString().trim();
            }
        }
        return goodsId;
    }

    private static String resolveGoodsSourceBreakdownGoodsName(
            Map<String, Object> focusRow, AiResolvedQueryContext rq) {
        return focusRow != null ? firstNonBlankString(focusRow, "goodsName", "goodsTitle", "name") : null;
    }

    private static void fillRows(String planType, String wire, Map<String, Object> overview,
            List<Map<String, Object>> focusRows, List<Map<String, Object>> secondaryRows) {
        switch (planType) {
            case PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING ->
                    splitTopRows(castRowList(overview.get("topSuppliers")), focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING ->
                    mergeAndSortPurchaseStoreRows(overview, focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING ->
                    splitTopRows(firstNonEmptyRowList(overview,
                            "goodsPurchaseAmountTop",
                            "goodsAmountTop",
                            "purchaseGoodsAmountTop"),
                            focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING ->
                    splitTopRows(firstNonEmptyRowList(overview,
                            "goodsPurchaseFrequencyTop",
                            "goodsFrequencyTop",
                            "goodsPurchaseCountTop",
                            "purchaseGoodsFrequencyTop"),
                            focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING ->
                    splitTopRows(
                            filterValidPurchaseQuantityRankingRows(
                                    firstNonEmptyRowList(
                                            overview,
                                            "goodsPurchaseQuantityTop",
                                            "goodsQuantityTop",
                                            "purchaseGoodsQuantityTop")),
                            focusRows,
                            secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_ANOMALY ->
                    fillAnomalyRows(wire, overview, focusRows, secondaryRows);
            case PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL,
                    PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL -> {
                List<Map<String, Object>> detailRows = castRowList(overview.get("purchaseSupplierGoodsDetailRows"));
                if (!detailRows.isEmpty()) {
                    splitTopRows(detailRows, focusRows, secondaryRows);
                }
            }
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN -> {
                Object brow = overview.get("purchaseGoodsSourceBreakdownRow");
                if (brow instanceof Map<?, ?> bm && !bm.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) bm;
                    focusRows.add(copyRowShallow(typed));
                }
                List<Map<String, Object>> lineRows =
                        castRowList(overview.get("purchaseGoodsAnchorLineRows"));
                appendAllRows(lineRows, secondaryRows);
            }
            case PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL -> {
                List<Map<String, Object>> detailRows =
                        castRowList(overview.get("purchasePeriodGoodsDetailRows"));
                appendAllRows(detailRows, focusRows);
            }
            default -> {
                LinkedHashMap<String, Object> core = new LinkedHashMap<>();
                core.put("totalPurchaseAmount", overview.get("totalPurchaseAmount"));
                core.put("purchaseOrderCount", overview.get("purchaseOrderCount"));
                focusRows.add(core);
            }
        }
    }

    private static void fillAnomalyRows(
            String wire,
            Map<String, Object> overview,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        List<Map<String, Object>> rows = List.of();
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(canon)) {
            rows = firstNonEmptyRowList(overview, "unitPriceChangedItems", "priceChangeItems");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(canon)) {
            rows = firstNonEmptyRowList(overview, "purchaseAmountSpikeItems", "amountSpikeItems");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY.equals(canon)) {
            rows = firstNonEmptyRowList(overview, "purchaseFrequencyAnomalyItems", "frequencyAnomalyItems");
        } else if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_QUANTITY_ANOMALY.equals(canon)) {
            rows = firstNonEmptyRowList(overview, "purchaseQuantityAnomalyItems", "quantityAnomalyItems");
        }
        splitTopRows(rows, focusRows, secondaryRows);
    }

    private static void appendPurchaseAnomalyPlanObservation(
            LinkedHashMap<String, Object> debug,
            String wire,
            Map<String, Object> overview,
            List<Map<String, Object>> focusRows) {
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        debug.put("anomalySubtype", purchaseAnomalySubtypeToken(canon));
        boolean factsAvailable = purchaseAnomalyFactsPayloadPresent(canon, overview);
        debug.put("anomalyFactsAvailable", factsAvailable);
        debug.put("anomalyFocusRowsSize", focusRows == null ? 0 : focusRows.size());
        if (!factsAvailable) {
            debug.put("anomalyProjectionGapReason", "PURCHASE_ANOMALY_FACTS_PAYLOAD_MISSING");
        }
    }

    /** 数量排行：无统一基础单位换算时，跨商品单位不可严格可比（仅记录口径限制，不阻断执行）。 */
    private static void appendPurchaseGoodsQuantityRankingCaliberObservation(
            LinkedHashMap<String, Object> debug,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        List<Map<String, Object>> all = new ArrayList<>();
        if (focusRows != null) {
            all.addAll(focusRows);
        }
        if (secondaryRows != null) {
            all.addAll(secondaryRows);
        }
        Set<String> distinctUnits = new LinkedHashSet<>();
        int rowsWithUnit = 0;
        for (Map<String, Object> row : all) {
            if (row == null) {
                continue;
            }
            Object unit = row.get("unit");
            if (unit != null && StringUtils.hasText(unit.toString())) {
                distinctUnits.add(unit.toString().trim());
                rowsWithUnit++;
            }
        }
        boolean mixedOrUnknownUnits =
                distinctUnits.size() > 1 || (all.size() > 1 && rowsWithUnit < all.size());
        debug.put("quantityRankingUnitComparable", !mixedOrUnknownUnits);
        if (mixedOrUnknownUnits) {
            debug.put(
                    "quantityRankingCaliberGapReason",
                    "PURCHASE_QUANTITY_RANKING_MIXED_UNIT_NO_BASE_CONVERSION");
            debug.put("quantityRankingDistinctUnitCount", distinctUnits.size());
        }
    }

    static String purchaseAnomalySubtypeToken(String canonicalWire) {
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(canonicalWire)) {
            return "PRICE";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY.equals(canonicalWire)) {
            return "FREQUENCY";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_QUANTITY_ANOMALY.equals(canonicalWire)) {
            return "QUANTITY";
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(canonicalWire)) {
            return "AMOUNT_SPIKE";
        }
        return "UNSPECIFIED";
    }

    private static boolean purchaseAnomalyFactsPayloadPresent(String canonicalWire, Map<String, Object> overview) {
        if (overview == null || canonicalWire == null) {
            return false;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(canonicalWire)) {
            return overview.containsKey("unitPriceChangedItems") || overview.containsKey("priceChangeItems");
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(canonicalWire)) {
            return !firstNonEmptyRowList(overview, "purchaseAmountSpikeItems", "amountSpikeItems")
                    .isEmpty();
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY.equals(canonicalWire)) {
            return !firstNonEmptyRowList(overview, "purchaseFrequencyAnomalyItems", "frequencyAnomalyItems")
                    .isEmpty();
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_QUANTITY_ANOMALY.equals(canonicalWire)) {
            return !firstNonEmptyRowList(overview, "purchaseQuantityAnomalyItems", "quantityAnomalyItems")
                    .isEmpty();
        }
        return false;
    }

    private static boolean isUnifiedPurchaseGoodsDetailPlan(String planType) {
        return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL.equals(planType);
    }

    private static void mergePurchaseAnchorExecutionObservationIfPresent(
            Map<String, Object> overview, LinkedHashMap<String, Object> debug, String planType) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || overview == null) {
            return;
        }
        boolean goodsAnchorExecution = isGoodsAnchorSupplierExecutionActive(overview);
        if (goodsAnchorExecution) {
            debug.put("requestedEntityType", AiResultAnchor.ENTITY_TYPE_GOODS);
            debug.put("requestedPurchaseSourceType", AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
            Object goodsName =
                    overview.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME);
            if (goodsName != null) {
                debug.put("requestedGoodsName", goodsName);
            }
            Object goodsId =
                    overview.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID);
            if (goodsId != null) {
                debug.put("requestedGoodsId", goodsId);
            }
        }
        mirrorOverviewExecutionPayloadToDebug(overview, debug);
    }

    private static void mirrorPeriodGoodsDetailOverviewToDebug(
            Map<String, Object> overview, LinkedHashMap<String, Object> debug) {
        if (overview == null || debug == null) {
            return;
        }
        String[] keys = {
            "purchasePeriodGoodsDetailActive",
            "purchasePeriodGoodsDetailQueryMethod",
            "purchasePeriodGoodsDetailRowsCount",
            "purchasePeriodGoodsDetailNoDataReason"
        };
        for (String key : keys) {
            debug.put(key, overview.get(key));
        }
    }

    private static void mirrorOverviewExecutionPayloadToDebug(
            Map<String, Object> overview, LinkedHashMap<String, Object> debug) {
        putOverviewExecutionField(
                overview,
                debug,
                AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME);
        putOverviewExecutionField(
                overview,
                debug,
                AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID);
        putOverviewExecutionField(
                overview,
                debug,
                AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE);
        putOverviewExecutionField(
                overview,
                debug,
                AiBusinessToolIds.PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_TIME_WINDOW);
        putOverviewExecutionField(
                overview,
                debug,
                AiBusinessToolIds.PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_PUR_DEP_IDS);
        putOverviewExecutionField(
                overview,
                debug,
                AiBusinessToolIds.PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_SOURCE_FOCUS);
        String[] passthrough = {
            "purchaseSupplierGoodsDetailRowsCount",
            "purchaseSupplierGoodsDetailNoDataReason",
            "purchaseSupplierGoodsDetailAlternativeFacet",
            "purchaseSupplierGoodsDetailAlternativeHasData",
            "purchaseSupplierGoodsDetailAlternativeEvidence",
            "purchaseSupplierGoodsDetailQueryMethod",
            "purchaseSupplierGoodsDetailFocusSupplierId"
        };
        for (String k : passthrough) {
            if (overview.containsKey(k)) {
                debug.put(k, overview.get(k));
            }
        }
    }

    private static void putOverviewExecutionField(
            Map<String, Object> overview, LinkedHashMap<String, Object> debug, String executionKey) {
        if (overview != null && overview.containsKey(executionKey)) {
            debug.put(executionKey, overview.get(executionKey));
        }
    }

    private static boolean isGoodsAnchorSupplierExecutionActive(Map<String, Object> overview) {
        return overview != null
                && Boolean.TRUE.equals(
                        overview.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE));
    }
    /**
     * Harness / Replay：按 plan 内真实明细行数收口 {@code purchaseSupplierGoodsDetail*}，并把 Tool 原因码规范为稳定枚举，
     * 不修改 Tool 返回值本身（仅在 debug 镜像中规范化）。
     */
    private static void finalizePurchaseSupplierGoodsDetailHarnessDebug(
            Map<String, Object> overview,
            LinkedHashMap<String, Object> debug,
            String planType,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || debug == null) {
            return;
        }
        int fr = focusRows == null ? 0 : focusRows.size();
        int sr = secondaryRows == null ? 0 : secondaryRows.size();
        int totalRows = fr + sr;
        boolean payloadPresent = overview != null
                && (overview.containsKey("purchaseSupplierGoodsDetailRows")
                        || isGoodsAnchorSupplierExecutionActive(overview));
        if (totalRows > 0) {
            debug.put("purchaseSupplierGoodsDetailRowsCount", totalRows);
            debug.put("purchaseSupplierGoodsDetailNoDataReason", null);
        } else {
            debug.put("purchaseSupplierGoodsDetailRowsCount", 0);
            Object rawReason = debug.get("purchaseSupplierGoodsDetailNoDataReason");
            String normalized = normalizePurchaseSupplierGoodsDetailNoDataReason(stringLoose(rawReason));
            if (normalized == null) {
                if (!payloadPresent) {
                    normalized = "TOOL_PAYLOAD_EMPTY";
                } else {
                    normalized = "NO_PURCHASE_RECORD_FOR_FOCUSED_GOODS";
                }
            }
            debug.put("purchaseSupplierGoodsDetailNoDataReason", normalized);
        }
        syncAlternativeHasDataWithEvidenceStatus(debug);
    }

    @SuppressWarnings("unchecked")
    private static void syncAlternativeHasDataWithEvidenceStatus(LinkedHashMap<String, Object> debug) {
        Object ev = debug.get("purchaseSupplierGoodsDetailAlternativeEvidence");
        if (!(ev instanceof Map<?, ?> raw)) {
            return;
        }
        Object st = raw.get("status");
        String status = st == null ? null : st.toString().trim();
        if ("NOT_CHECKED".equals(status)) {
            debug.put("purchaseSupplierGoodsDetailAlternativeHasData", null);
        }
    }

    private static String stringLoose(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Tool 历史字符串 → Harness 稳定码（{@link com.nongxinle.ai.tool.business.PurchaseOverviewTool} 不重读）。 */
    static String normalizePurchaseSupplierGoodsDetailNoDataReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        return switch (t) {
            case "NO_SUPPLIER_PURCHASE_FOR_GOODS" -> "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS";
            case "GOODS_NOT_FOUND_FOR_PURCHASE_DETAIL" -> t;
            case "GOODS_ID_MISSING_FOR_DRILLDOWN", "GOODS_ID_MISSING_FOR_ANCHOR_EXECUTION" -> "FOCUSED_GOODS_NOT_FOUND";
            case "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS",
                 "FOCUSED_GOODS_NOT_FOUND",
                 "NO_PURCHASE_RECORD_FOR_FOCUSED_GOODS",
                 "NO_PURCHASE_LINES_FOR_FOCUSED_SUPPLIER",
                 "NO_SUPPLIER_PURCHASE_FOR_SCOPE",
                 "SUPPLIER_ID_MISSING_FOR_DRILLDOWN",
                 "SUPPLIER_ID_MISSING_FOR_ANCHOR_EXECUTION",
                 "TOOL_PAYLOAD_EMPTY",
                 "GOODS_SUPPLIER_UNIT_PRICE_NO_DATA" -> t;
            default -> t;
        };
    }

    private static void putPurchaseExecutionHarnessDebug(
            PurchaseSemanticExecutionIntent intent, LinkedHashMap<String, Object> debug) {
        if (debug == null) {
            return;
        }
        PurchaseSemanticExecutionIntent exec =
                intent == null ? PurchaseSemanticExecutionIntent.none() : intent;
        debug.put("executionIntentType", emptyToNull(exec.getExecutionIntentType()));
        debug.put("executionDetailWanted", emptyToNull(exec.getDetailWanted()));
        debug.put("matchedContractId", emptyToNull(exec.getMatchedContractId()));
        debug.put("focusEntityType", emptyToNull(exec.getAnchorType()));
        debug.put("focusEntityName", emptyToNull(exec.getFocusGoodsName()));
        debug.put("focusEntityId", emptyToNull(exec.getFocusGoodsId()));
        if (exec.getFocusSupplierId() != null) {
            debug.put("focusSupplierId", exec.getFocusSupplierId());
        }
    }

    /**
     * Harness / 复盘：锚 execution 时，把 execution intent 与 Tool 入参镜像摊平到 {@link PurchaseAnswerPlan#getDebug()}。
     */
    private static void appendPurchaseSupplierGoodsDetailExecutionDebug(
            String planType,
            Map<String, Object> overview,
            AiResolvedQueryContext rq,
            LinkedHashMap<String, Object> debug) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || rq == null || debug == null) {
            return;
        }
        putPurchaseExecutionHarnessDebug(PurchaseSemanticExecutionIntentResolver.project(rq), debug);
        com.nongxinle.ai.identity.BusinessEntityIdentityBridge.appendGoodsIdentityHarnessDebug(rq, debug);
        Object toolSid = overview == null ? null : overview.get("purchaseSupplierGoodsDetailFocusSupplierId");
        if (toolSid != null) {
            debug.put("toolFocusSupplierId", toolSid);
            PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.project(rq);
            if (AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(emptyToNull(intent.getAnchorType()))) {
                debug.put("focusEntityId", toolSid);
            }
        }
        AiConversationTurnMemory prev = rq.getPreviousTurn();
        if (prev != null && prev.getLastResultAnchors() != null) {
            for (AiResultAnchor a : prev.getLastResultAnchors()) {
                if (a == null || a.getEntityType() == null) {
                    continue;
                }
                if (!AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(a.getEntityType().trim())) {
                    continue;
                }
                debug.put("inheritedAnchorType", a.getEntityType());
                debug.put("inheritedAnchorId", emptyToNull(a.getEntityId()));
                debug.put("inheritedAnchorName", emptyToNull(a.getEntityName()));
                debug.put("inheritedSourcePlanType", emptyToNull(a.getSourcePlanType()));
                break;
            }
        }
        if (rq.getDataScope() != null
                && rq.getDataScope().getExpandedSqlDepartmentIds() != null
                && !rq.getDataScope().getExpandedSqlDepartmentIds().isEmpty()) {
            debug.put("expandedSqlDepartmentIds", new ArrayList<>(rq.getDataScope().getExpandedSqlDepartmentIds()));
        }
        if (rq.getQueryIntent() != null && rq.getQueryIntent().getPurchaseSourceType() != null) {
            String pst = rq.getQueryIntent().getPurchaseSourceType().trim();
            debug.put("purchaseSourceType", pst.isEmpty() ? null : pst);
        }
        if (debug.get("purchaseSupplierGoodsDetailQueryMethod") != null) {
            debug.put("queryMethod", debug.get("purchaseSupplierGoodsDetailQueryMethod"));
        }
        debug.put("noDataReason", debug.get("purchaseSupplierGoodsDetailNoDataReason"));
    }

    private static void mergePurchaseGoodsSourceBreakdownHarnessFields(
            Map<String, Object> overview, LinkedHashMap<String, Object> debug, String planType, AiResolvedQueryContext rq) {
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType) || debug == null) {
            return;
        }
        Object qm = overview == null ? null : overview.get("purchaseGoodsSourceBreakdownQueryMethod");
        debug.put(
                "queryMethod",
                qm != null && !qm.toString().isBlank()
                        ? qm
                        // TODO(CLEANUP): 当前 SQL method 名称含 Legacy，容易误导；实际调用链已走 semantic contract。后续应统一改名并确认前后端/debug/replay 无依赖。
                        : "queryGbPurchaseGoodsAggByLegacyPurchaseMethod");
        Object focusId = overview == null ? null : overview.get("purchaseGoodsSourceBreakdownFocusDisGoodsId");
        if (focusId == null && rq != null && rq.getResolvedGoodsIdentity() != null
                && rq.getResolvedGoodsIdentity().getResolvedEntityId() != null) {
            focusId = rq.getResolvedGoodsIdentity().getResolvedEntityId();
        }
        debug.put("focusDisGoodsId", focusId);
        if (overview != null) {
            Object buckets = overview.get("purchaseGoodsSourceBreakdownBuckets");
            if (buckets != null) {
                debug.put("sourceBreakdownBuckets", buckets);
            }
            Object exp = overview.get("purchaseGoodsSourceBreakdownExpandedSqlDepartmentIds");
            if (exp != null) {
                debug.put("expandedSqlDepartmentIds", exp);
            }
            debug.put("noDataReason", overview.get("purchaseGoodsSourceBreakdownNoDataReason"));
            debug.put("goodsAnchorIdMissing", overview.get("purchaseGoodsSourceBreakdownGoodsAnchorIdMissing"));
            debug.put("purchaseGoodsAnchorLineRowsCount", overview.get("purchaseGoodsAnchorLineRowsCount"));
            debug.put("purchaseGoodsAnchorLineQueryMethod", overview.get("purchaseGoodsAnchorLineQueryMethod"));
        }
    }

    private static void finalizePurchaseGoodsSourceBreakdownHarnessDebug(
            Map<String, Object> overview,
            LinkedHashMap<String, Object> debug,
            String planType,
            List<Map<String, Object>> focusRows,
            AiResolvedQueryContext rq) {
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType) || debug == null) {
            return;
        }
        int fr = focusRows == null ? 0 : focusRows.size();
        if (fr > 0) {
            return;
        }
        Object rawReason = debug.get("noDataReason");
        if (rawReason == null && overview != null) {
            rawReason = overview.get("purchaseGoodsSourceBreakdownNoDataReason");
        }
        String normalized = normalizePurchaseGoodsSourceBreakdownNoDataReason(stringLoose(rawReason));
        if (normalized == null) {
            Object toolFocusId =
                    overview == null ? null : overview.get("purchaseGoodsSourceBreakdownFocusDisGoodsId");
            normalized = toolFocusId == null ? "GOODS_ANCHOR_ID_MISSING" : "TOOL_PAYLOAD_EMPTY";
        }
        debug.put("noDataReason", normalized);
        if (debug.get("goodsAnchorIdMissing") == null) {
            Object toolFocusId =
                    overview == null ? null : overview.get("purchaseGoodsSourceBreakdownFocusDisGoodsId");
            if (toolFocusId == null) {
                debug.put("goodsAnchorIdMissing", Boolean.TRUE);
            }
        }
    }

    private static String normalizePurchaseGoodsSourceBreakdownNoDataReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        return switch (t) {
            case "NO_PURCHASE_RECORD_FOR_SCOPE",
                    "GOODS_ANCHOR_ID_MISSING",
                    "NO_PURCHASE_LINES_FOR_FOCUSED_GOODS",
                    "TOOL_PAYLOAD_EMPTY" -> t;
            default -> t;
        };
    }

    private static void overlaySummaryFromGoodsSourceBreakdown(Map<String, Object> summary, Map<String, Object> overview) {
        if (summary == null || overview == null) {
            return;
        }
        Object rowObj = overview.get("purchaseGoodsSourceBreakdownRow");
        if (!(rowObj instanceof Map<?, ?> row) || row.isEmpty()) {
            return;
        }
        Object ta = row.get("totalPurchaseAmount");
        if (ta != null) {
            summary.put("totalAmount", parseDoubleLoose(ta));
        }
        summary.put("selfPurchaseAmount", parseDoubleLoose(row.get("selfPurchaseAmount")));
        summary.put("supplierPurchaseAmount", parseDoubleLoose(row.get("supplierPurchaseAmount")));
        summary.put("selfPurchaseLineCount", parseIntLoose(row.get("selfPurchaseLineCount")));
        summary.put("supplierPurchaseLineCount", parseIntLoose(row.get("supplierPurchaseLineCount")));
        Object tq = row.get("totalPurchaseQuantity");
        if (tq != null) {
            summary.put("totalPurchaseQuantity", tq);
        }
        Object unit = row.get("unit");
        if (unit != null && !unit.toString().isBlank()) {
            summary.put("unit", unit.toString().trim());
        }
        int lineCount =
                parseIntLoose(row.get("selfPurchaseLineCount"))
                        + parseIntLoose(row.get("supplierPurchaseLineCount"))
                        + parseIntLoose(row.get("otherPurchaseLineCount"));
        // 优先逐笔行数（按 purchaseGoodsId 去重），与卡片 lines[] 一致。
        if (overview != null && overview.get("purchaseGoodsAnchorLineRowsCount") != null) {
            lineCount = parseIntLoose(overview.get("purchaseGoodsAnchorLineRowsCount"));
        }
        if (lineCount > 0) {
            summary.put("purchaseLineCount", lineCount);
        }
    }

    /** Phase2-A：GOODS 来源拆桶锚 execution，摊平 execution intent / 继承 GOODS 锚。 */
    private static void appendPurchaseGoodsSourceBreakdownExecutionDebug(
            String planType,
            Map<String, Object> overview,
            AiResolvedQueryContext rq,
            LinkedHashMap<String, Object> debug) {
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType) || rq == null || debug == null) {
            return;
        }
        putPurchaseExecutionHarnessDebug(PurchaseSemanticExecutionIntentResolver.project(rq), debug);
        com.nongxinle.ai.identity.BusinessEntityIdentityBridge.appendGoodsIdentityHarnessDebug(rq, debug);
        debug.put("purchaseSourceTypeSource", "contractLockedFrame.sourceFacet");
        debug.put("structuredIntentDetailSource", "contractLockedFrame.wire");
        if (overview != null) {
            Object fid = overview.get("purchaseGoodsSourceBreakdownFocusDisGoodsId");
            if (fid != null) {
                debug.put("focusEntityId", fid.toString());
            }
        }
        AiConversationTurnMemory prev = rq.getPreviousTurn();
        if (prev != null && prev.getLastResultAnchors() != null) {
            for (AiResultAnchor a : prev.getLastResultAnchors()) {
                if (a == null || a.getEntityType() == null) {
                    continue;
                }
                if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                    continue;
                }
                debug.put("inheritedAnchorType", a.getEntityType());
                debug.put("inheritedAnchorId", emptyToNull(a.getEntityId()));
                debug.put("inheritedAnchorName", emptyToNull(a.getEntityName()));
                debug.put("inheritedSourcePlanType", emptyToNull(a.getSourcePlanType()));
                break;
            }
        }
        boolean expandedMissing =
                debug.get("expandedSqlDepartmentIds") == null
                        || (!(debug.get("expandedSqlDepartmentIds") instanceof List<?> ls) || ls.isEmpty());
        if (expandedMissing
                && rq.getDataScope() != null
                && rq.getDataScope().getExpandedSqlDepartmentIds() != null
                && !rq.getDataScope().getExpandedSqlDepartmentIds().isEmpty()) {
            debug.put("expandedSqlDepartmentIds", new ArrayList<>(rq.getDataScope().getExpandedSqlDepartmentIds()));
        }
        if (rq.getQueryIntent() != null && rq.getQueryIntent().getPurchaseSourceType() != null) {
            String pst = rq.getQueryIntent().getPurchaseSourceType().trim();
            debug.put("purchaseSourceType", pst.isEmpty() ? null : pst);
        }
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    /**
     * 多店并排采购金额对比：{@link com.nongxinle.ai.tool.business.PurchaseOverviewTool} 写入的 coveredStores /
     * dataMissingStores（有额度的店在前，金额为 0 的店在后，按额度降序）。
     */
    private static void mergeAndSortPurchaseStoreRows(Map<String, Object> overview,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        List<Map<String, Object>> merged = new ArrayList<>();
        merged.addAll(castRowList(overview.get("coveredStores")));
        merged.addAll(castRowList(overview.get("dataMissingStores")));
        merged.sort(Comparator.<Map<String, Object>, Double>comparing(
                        r -> parseDoubleLoose(r.get("purchaseSubtotal")))
                .reversed());
        splitTopRows(merged, focusRows, secondaryRows);
    }

    /** 按候选 key 顺序读取 ToolResult 中已有排行列表，不重查 SQL。 */
    private static List<Map<String, Object>> firstNonEmptyRowList(Map<String, Object> overview, String... keys) {
        if (keys == null) {
            return List.of();
        }
        for (String k : keys) {
            List<Map<String, Object>> rows = castRowList(overview.get(k));
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    private static void splitTopRows(List<Map<String, Object>> ordered,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows) {
        if (ordered == null || ordered.isEmpty()) {
            return;
        }
        focusRows.add(copyRowShallow(ordered.get(0)));
        for (int i = 1; i < ordered.size(); i++) {
            secondaryRows.add(copyRowShallow(ordered.get(i)));
        }
    }

    /** 数量排行：剔除无有效 purchaseQuantity/quantity 的行（null/空/非正数不得进入排行）。 */
    private static List<Map<String, Object>> filterValidPurchaseQuantityRankingRows(
            List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row != null && hasValidPurchaseQuantityRankingValue(row)) {
                out.add(row);
            }
        }
        return out;
    }

    private static boolean hasValidPurchaseQuantityRankingValue(Map<String, Object> row) {
        Object qty = row.get("purchaseQuantity");
        if (qty == null) {
            qty = row.get("quantity");
        }
        if (qty == null) {
            qty = row.get("buyQuantity");
        }
        if (qty == null || !StringUtils.hasText(qty.toString())) {
            return false;
        }
        return parseDoubleLoose(qty) > 0;
    }

    private static void appendAllRows(List<Map<String, Object>> ordered, List<Map<String, Object>> focusRows) {
        if (ordered == null || ordered.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : ordered) {
            if (row != null && !row.isEmpty()) {
                focusRows.add(copyRowShallow(row));
            }
        }
    }

    private static Map<String, Object> buildSummary(Map<String, Object> overview) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("totalAmount", parseDoubleLoose(overview.get("totalPurchaseAmount")));
        Object cnt = overview.get("purchaseOrderCount");
        if (cnt instanceof Number n) {
            m.put("totalCount", n.intValue());
        } else if (cnt != null) {
            try {
                m.put("totalCount", Integer.parseInt(cnt.toString().trim()));
            } catch (Exception e) {
                m.put("totalCount", 0);
            }
        } else {
            m.put("totalCount", 0);
        }
        appendPurchaseMethodSummary(m, overview);
        return m;
    }

    private static void appendPurchaseMethodSummary(Map<String, Object> summary, Map<String, Object> overview) {
        Object br = overview.get("purchaseMethodBreakdown");
        if (!(br instanceof List<?> list)) {
            return;
        }
        BigDecimalHolder selfAmt = new BigDecimalHolder();
        BigDecimalHolder supAmt = new BigDecimalHolder();
        int selfLines = 0;
        int supLines = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> row)) {
                continue;
            }
            Object lab = row.get("label");
            String label = lab == null ? "" : lab.toString().trim();
            Object amtY = row.get("amountYuan");
            Object lc = row.get("lineCount");
            double amt = parseDoubleLoose(amtY);
            int lines = lc instanceof Number ? ((Number) lc).intValue() : parseIntLoose(lc);
            if ("自采".equals(label)) {
                selfAmt.add(amt);
                selfLines += lines;
            } else if ("供货商采购".equals(label)) {
                supAmt.add(amt);
                supLines += lines;
            }
        }
        summary.put("selfPurchaseAmount", selfAmt.value);
        summary.put("supplierPurchaseAmount", supAmt.value);
        summary.put("selfPurchaseLineCount", selfLines);
        summary.put("supplierPurchaseLineCount", supLines);
    }

    private static final class BigDecimalHolder {
        double value;

        void add(double v) {
            value += v;
        }
    }

    private static int parseIntLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String resolveScopeLabel(Map<String, Object> overview, AiResolvedQueryContext rq) {
        Object b = overview.get("queryScopeBanner");
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

    private static boolean overviewHasBlockingError(Map<String, Object> overview) {
        if (overview == null || overview.isEmpty()) {
            return false;
        }
        Object err = overview.get("error");
        return err != null && !err.toString().isBlank();
    }

    /** Tool 失败但 requiresAnchor identity 阻断时仍应挂载失败 AnswerPlan。 */
    public static boolean shouldAttachPlanAfterToolExecution(AiRunState state, boolean toolSuccess) {
        if (toolSuccess) {
            return true;
        }
        return shouldAttachAnchorIdentityFailurePlan(state, state != null ? state.getResolvedQueryContext() : null);
    }

    static boolean shouldAttachAnchorIdentityFailurePlan(AiRunState state, AiResolvedQueryContext rq) {
        if (rq != null && RequiresAnchorExecutionGateSupport.blocksToolExecution(rq)) {
            return true;
        }
        return isOverviewAnchorIdentityFailure(purchaseOverviewFromRunState(state));
    }

    static boolean isOverviewAnchorIdentityFailure(Map<String, Object> overview) {
        if (overview == null || overview.isEmpty()) {
            return false;
        }
        if (Boolean.TRUE.equals(overview.get("purchaseGoodsSourceBreakdownGoodsAnchorIdMissing"))) {
            return true;
        }
        if (overview.get(AiBusinessToolIds.PAYLOAD_PURCHASE_ANCHOR_IDENTITY_FAILURE) instanceof Map<?, ?>) {
            return true;
        }
        return "GOODS_ANCHOR_ID_MISSING".equalsIgnoreCase(stringLoose(overview.get("purchaseGoodsSourceBreakdownNoDataReason")));
    }

    private static void attachAnchorIdentityFailurePlan(AiRunState state, AiResolvedQueryContext rq) {
        try {
            PurchaseSemanticExecutionIntent projected = PurchaseSemanticExecutionIntentResolver.project(rq);
            RequiresAnchorExecutionGateSupport.Decision gate = RequiresAnchorExecutionGateSupport.evaluate(rq);
            PurchaseAnswerPlan plan =
                    buildGoodsAnchorIdentityFailurePlan(
                            state, rq, projected, gate, Map.of(), new LinkedHashMap<>());
            state.setPurchaseAnswerPlan(plan);
        } catch (Exception ex) {
            log.warn("[PurchaseAnswerPlan] attach anchor identity failure plan failed runId={}", state.getRunId(), ex);
            state.setPurchaseAnswerPlan(null);
        }
    }

    private static PurchaseAnswerPlan buildGoodsAnchorIdentityFailurePlan(
            AiRunState state,
            AiResolvedQueryContext rq,
            PurchaseSemanticExecutionIntent executionIntent,
            RequiresAnchorExecutionGateSupport.Decision gate,
            Map<String, Object> overview,
            LinkedHashMap<String, Object> debug) {
        String planType =
                executionIntent != null && StringUtils.hasText(executionIntent.getAnswerPlanType())
                        ? executionIntent.getAnswerPlanType().trim()
                        : PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN;
        String requestedName = gate != null ? emptyToNull(gate.getRequestedEntityName()) : null;
        if (requestedName == null && executionIntent != null) {
            requestedName = emptyToNull(executionIntent.getFocusGoodsName());
        }
        if (requestedName == null && overview != null) {
            Object failure = overview.get(AiBusinessToolIds.PAYLOAD_PURCHASE_ANCHOR_IDENTITY_FAILURE);
            if (failure instanceof Map<?, ?> fm) {
                Object rn = fm.get("requestedGoodsName");
                if (rn != null && StringUtils.hasText(rn.toString())) {
                    requestedName = rn.toString().trim();
                }
            }
        }

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        if (StringUtils.hasText(requestedName)) {
            summary.put("requestedGoodsName", requestedName);
        }

        debug.put("anchorIdentityBlocked", Boolean.TRUE);
        debug.put("noDataReason", "GOODS_ANCHOR_ID_MISSING");
        debug.put("goodsAnchorIdMissing", Boolean.TRUE);
        debug.put("focusDisGoodsId", null);
        if (gate != null) {
            debug.put("anchorIdentityBlockReason", emptyToNull(gate.getBlockReason()));
            if (gate.getIdentityStatus() != null) {
                debug.put("entityIdentityResolutionStatus", gate.getIdentityStatus().name());
            }
        }
        putPurchaseExecutionHarnessDebug(executionIntent, debug);
        debug.put("purchaseSourceTypeSource", "contractLockedFrame.sourceFacet");
        debug.put("structuredIntentDetailSource", "contractLockedFrame.wire");
        if (StringUtils.hasText(requestedName)) {
            debug.put("focusEntityName", requestedName);
            debug.put("requestedGoodsName", requestedName);
        }
        debug.put(
                "clarificationMessage",
                gate != null && StringUtils.hasText(gate.getClarificationMessage())
                        ? gate.getClarificationMessage().trim()
                        : BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND);

        return PurchaseAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(resolveScopeLabel(overview, rq))
                .timeLabel(resolveTimeLabel(state, rq))
                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                .summary(summary)
                .focusRows(List.of())
                .secondaryRows(List.of())
                .debug(debug)
                .resultAnchors(List.of())
                .build();
    }

    /**
     * 少数链路会把 Tool payload 的 {@code data} 落成 JSON 字符串；此处尽力还原为 Map 再走 purchaseOverview 路径。
     */
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
     * 浅层遍历 Map 值，查找嵌套的 {@code purchaseOverview}（兼容双重 envelope 等异常形状）。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepFindPurchaseOverview(Object node, int depthLeft) {
        if (depthLeft <= 0 || node == null) {
            return Map.of();
        }
        if (node instanceof Map<?, ?> m) {
            Object po = m.get("purchaseOverview");
            if (po instanceof Map<?, ?> pom && !pom.isEmpty()) {
                return new LinkedHashMap<>((Map<String, Object>) pom);
            }
            for (Object v : m.values()) {
                Map<String, Object> hit = deepFindPurchaseOverview(v, depthLeft - 1);
                if (!hit.isEmpty()) {
                    return hit;
                }
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    /** Card 投影层读取 purchaseOverview（与 AnswerPlan Builder 同源）。 */
    public static Map<String, Object> purchaseOverviewFromRunState(AiRunState state) {
        if (state == null || state.getToolResults() == null || state.getToolResults().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> overview = extractPurchaseOverviewPayload(state);
        return overview == null || overview.isEmpty() ? Map.of() : overview;
    }

    private static Map<String, Object> extractPurchaseOverviewPayload(AiRunState state) {
        Object env = state.getToolResults().get(AiBusinessToolIds.PURCHASE_OVERVIEW);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> envMap = (Map<String, Object>) env;
        Object data = unwrapDataMaybeJsonString(envMap.get("data"));
        if (data instanceof Map<?, ?> dm) {
            Object po = dm.get("purchaseOverview");
            if (po instanceof Map<?, ?> pom) {
                Map<String, Object> raw = (Map<String, Object>) pom;
                return raw.isEmpty() ? Map.of() : new LinkedHashMap<>(raw);
            }
            // 兼容：data 即 overview（扁平字段）
            Map<String, Object> asDataMap = (Map<String, Object>) dm;
            if (asDataMap.containsKey("totalPurchaseAmount") || asDataMap.containsKey("purchaseOrderCount")) {
                return new LinkedHashMap<>(asDataMap);
            }
        }
        Object poTop = envMap.get("purchaseOverview");
        if (poTop instanceof Map<?, ?> pom) {
            Map<String, Object> raw = (Map<String, Object>) pom;
            return raw.isEmpty() ? Map.of() : new LinkedHashMap<>(raw);
        }
        Map<String, Object> deep = deepFindPurchaseOverview(envMap, 5);
        return deep.isEmpty() ? Map.of() : deep;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRowList(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static LinkedHashMap<String, Object> copyRowShallow(Map<String, Object> row) {
        return row == null ? new LinkedHashMap<>() : new LinkedHashMap<>(row);
    }

    /**
     * 仅从已有 focusRows（或 Tool 排行列表首行）抽取 Top1 锚点，不重算排行。
     */
    private static List<AiResultAnchor> buildResultAnchors(
            String planType,
            List<Map<String, Object>> focusRows,
            Map<String, Object> overview,
            AiResolvedQueryContext rq) {
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(planType)) {
            return buildSupplierAmountTop1Anchor(focusRows, overview);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(planType)) {
            return buildGoodsRankingTop1Anchor(planType, focusRows, overview);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
            return buildGoodsSourceBreakdownAnchor(focusRows, overview, rq);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)) {
            return buildSupplierGoodsDetailGoodsAnchor(focusRows, overview, rq);
        }
        return List.of();
    }

    /**
     * 供货商商品明细：沉淀当前 GOODS 锚点供下一轮 USE_PREVIOUS_ANCHOR 继承（无明细数据时也保留继承锚）。
     */
    private static List<AiResultAnchor> buildSupplierGoodsDetailGoodsAnchor(
            List<Map<String, Object>> focusRows,
            Map<String, Object> overview,
            AiResolvedQueryContext rq) {
        if (rq == null) {
            return List.of();
        }
        PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.resolve(rq);
        String goodsId = emptyToNull(intent.getFocusGoodsId());
        String goodsName = emptyToNull(intent.getFocusGoodsName());

        Map<String, Object> row = null;
        if (focusRows != null && !focusRows.isEmpty()) {
            row = focusRows.get(0);
        }
        if (goodsId == null && row != null) {
            goodsId = firstNonBlankId(row, "disGoodsId", "goodsId", "gbDisGoodsId");
        }
        if (goodsId == null && overview != null) {
            Object fid = overview.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID);
            if (fid != null && !fid.toString().isBlank()) {
                goodsId = fid.toString().trim();
            }
        }
        if (goodsName == null && row != null) {
            goodsName = firstNonBlankString(row, "goodsName", "goodsTitle", "name");
        }
        if (goodsName == null && overview != null) {
            Object fn = overview.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME);
            if (fn != null && !fn.toString().isBlank()) {
                goodsName = fn.toString().trim();
            }
        }

        AiResultAnchor inherited = resolveInheritedGoodsAnchor(rq);
        if (goodsId == null && inherited != null) {
            goodsId = emptyToNull(inherited.getEntityId());
        }
        if (goodsName == null && inherited != null) {
            goodsName = emptyToNull(inherited.getEntityName());
        }

        if ((goodsName == null || goodsName.isEmpty())
                && (goodsId == null || goodsId.isEmpty())) {
            return List.of();
        }

        AiResultAnchor anchor =
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                        .entityId(goodsId)
                        .entityName(goodsName)
                        .sourcePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                        .metric("supplierPurchaseAmount")
                        .build();
        return List.of(anchor);
    }

    /**
     * 商品来源拆桶：沉淀当前 GOODS 锚点供下一轮 USE_PREVIOUS_ANCHOR 继承（不重算、不读用户原文）。
     */
    private static List<AiResultAnchor> buildGoodsSourceBreakdownAnchor(
            List<Map<String, Object>> focusRows,
            Map<String, Object> overview,
            AiResolvedQueryContext rq) {
        Map<String, Object> row = null;
        if (focusRows != null && !focusRows.isEmpty()) {
            row = focusRows.get(0);
        }
        String goodsName = resolveGoodsSourceBreakdownGoodsName(row, rq);
        if (goodsName == null || goodsName.isEmpty()) {
            return List.of();
        }
        String goodsId = resolveGoodsSourceBreakdownGoodsId(overview, rq, row);
        Object amtObj = row != null ? row.get("totalPurchaseAmount") : null;
        String amountStr = amtObj != null ? amtObj.toString().trim() : null;
        AiResultAnchor anchor =
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                        .entityId(goodsId)
                        .entityName(goodsName)
                        .sourcePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN)
                        .metric("totalPurchaseAmount")
                        .amount(amountStr != null && !amountStr.isEmpty() ? amountStr : null)
                        .build();
        return List.of(anchor);
    }

    private static String firstNonBlankString(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            Object v = row.get(k);
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static List<AiResultAnchor> buildSupplierAmountTop1Anchor(
            List<Map<String, Object>> focusRows, Map<String, Object> overview) {
        Map<String, Object> row = null;
        if (focusRows != null && !focusRows.isEmpty()) {
            row = focusRows.get(0);
        }
        if (row == null || row.isEmpty()) {
            List<Map<String, Object>> tops = castRowList(overview != null ? overview.get("topSuppliers") : null);
            if (!tops.isEmpty()) {
                row = tops.get(0);
            }
        }
        if (row == null || row.isEmpty()) {
            return List.of();
        }
        Object nameObj = row.get("supplierName");
        String supplierName = nameObj != null ? nameObj.toString().trim() : "";
        if (supplierName.isEmpty()) {
            return List.of();
        }
        Object idObj = row.get("supplierId");
        String supplierId = idObj != null ? idObj.toString().trim() : null;
        if (supplierId != null && supplierId.isEmpty()) {
            supplierId = null;
        }
        Object amtObj = row.get("totalPurchaseAmount");
        String amountStr =
                amtObj != null ? amtObj.toString().trim() : null;
        Integer rank = parseRankLoose(row.get("rank"), 1);
        AiResultAnchor anchor =
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_SUPPLIER)
                        .entityId(supplierId)
                        .entityName(supplierName)
                        .rank(rank)
                        .sourcePlanType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING)
                        .metric("totalPurchaseAmount")
                        .amount(amountStr != null && !amountStr.isEmpty() ? amountStr : null)
                        .build();
        return List.of(anchor);
    }

    private static List<AiResultAnchor> buildGoodsRankingTop1Anchor(
            String planType,
            List<Map<String, Object>> focusRows,
            Map<String, Object> overview) {
        Map<String, Object> row = null;
        if (focusRows != null && !focusRows.isEmpty()) {
            row = focusRows.get(0);
        }
        if (row == null || row.isEmpty()) {
            List<Map<String, Object>> tops =
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)
                            ? firstNonEmptyRowList(
                                    overview,
                                    "goodsPurchaseAmountTop",
                                    "goodsAmountTop",
                                    "purchaseGoodsAmountTop")
                            : firstNonEmptyRowList(
                                    overview,
                                    "goodsPurchaseFrequencyTop",
                                    "goodsFrequencyTop",
                                    "goodsPurchaseCountTop",
                                    "purchaseGoodsFrequencyTop");
            if (!tops.isEmpty()) {
                row = tops.get(0);
            }
        }
        if (row == null || row.isEmpty()) {
            return List.of();
        }
        Object nameObj = row.get("goodsName");
        if (nameObj == null || nameObj.toString().isBlank()) {
            nameObj = row.get("goodsTitle");
        }
        if (nameObj == null || nameObj.toString().isBlank()) {
            nameObj = row.get("name");
        }
        String goodsName = nameObj != null ? nameObj.toString().trim() : "";
        if (goodsName.isEmpty()) {
            return List.of();
        }
        String goodsId = firstNonBlankId(row, "disGoodsId", "goodsId", "gbDisGoodsId");
        boolean amountRanking =
                PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType);
        String metric = amountRanking ? "totalPurchaseAmount" : "purchaseTimes";
        Object amtObj = amountRanking ? row.get("totalPurchaseAmount") : row.get("purchaseTimes");
        if (amountRanking && (amtObj == null || amtObj.toString().isBlank())) {
            amtObj = row.get("purchaseSubtotal");
        }
        String amountStr = amtObj != null ? amtObj.toString().trim() : null;
        Integer rank = parseRankLoose(row.get("rank"), 1);
        String sourcePlanType =
                amountRanking
                        ? PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING
                        : PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING;
        AiResultAnchor anchor =
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                        .entityId(goodsId)
                        .entityName(goodsName)
                        .rank(rank)
                        .sourcePlanType(sourcePlanType)
                        .metric(metric)
                        .amount(amountStr != null && !amountStr.isEmpty() ? amountStr : null)
                        .build();
        return List.of(anchor);
    }

    private static String firstNonBlankId(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            Object v = row.get(k);
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static int parseRankLoose(Object rk, int defaultRank) {
        if (rk instanceof Number n) {
            return n.intValue();
        }
        if (rk != null) {
            try {
                return Integer.parseInt(rk.toString().trim());
            } catch (Exception ignore) {
                return defaultRank;
            }
        }
        return defaultRank;
    }
}
