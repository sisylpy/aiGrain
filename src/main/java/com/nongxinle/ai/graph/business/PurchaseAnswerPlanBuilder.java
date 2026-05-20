package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (overview.isEmpty()) {
            if (plannedPurchaseOverview) {
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
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        try {
            PurchaseAnswerPlan plan = build(state, overview, rq);
            state.setPurchaseAnswerPlan(plan);
            log.info("[PurchaseAnswerPlan] attached runId={} type={} focusSize={} secondarySize={}",
                    state.getRunId(),
                    plan.getPlanType(),
                    plan.getFocusRows() == null ? 0 : plan.getFocusRows().size(),
                    plan.getSecondaryRows() == null ? 0 : plan.getSecondaryRows().size());
        } catch (Exception ex) {
            log.warn("[PurchaseAnswerPlan] attach failed runId={}", state.getRunId(), ex);
            state.setPurchaseAnswerPlan(null);
        }
    }

    static PurchaseAnswerPlan build(AiRunState state, Map<String, Object> overview, AiResolvedQueryContext rq) {
        String wire = "";
        String pst = AiQuerySemanticLexicon.SOURCE_ALL;
        if (rq != null && rq.getQueryIntent() != null) {
            AiResolvedQueryIntent qi = rq.getQueryIntent();
            if (qi.getStructuredIntentDetail() != null && !qi.getStructuredIntentDetail().isBlank()) {
                wire = qi.getStructuredIntentDetail().trim();
            }
            if (qi.getPurchaseSourceType() != null && !qi.getPurchaseSourceType().isBlank()) {
                pst = qi.getPurchaseSourceType().trim();
            }
        }

        String planType = resolvePlanType(wire, pst);
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(planType)) {
            if (pst == null
                    || pst.isBlank()
                    || AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(pst.trim())) {
                pst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
            }
        }
        if (isGoodsSupplierUnitPriceFollowUpIntent(rq, wire)) {
            planType = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL;
            pst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        } else if (isGoodsSupplierBreakdownFollowUpIntent(rq, wire)) {
            planType = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL;
            pst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        } else if (Boolean.TRUE.equals(overview.get("purchaseGoodsSupplierDrilldown"))) {
            planType = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL;
            if (pst == null
                    || pst.isBlank()
                    || AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(pst.trim())) {
                pst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
            }
        } else if (isGoodsSourceBreakdownIntent(rq, wire, pst)) {
            planType = PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN;
            pst = AiQuerySemanticLexicon.SOURCE_ALL;
        } else if (Boolean.TRUE.equals(overview.get("purchaseGoodsSourceBreakdownActive"))) {
            planType = PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN;
            pst = AiQuerySemanticLexicon.SOURCE_ALL;
        }
        String scopeLabel = resolveScopeLabel(overview, rq);
        String timeLabel = resolveTimeLabel(state, rq);

        Map<String, Object> summary = buildSummary(overview);
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
            overlaySummaryFromGoodsSourceBreakdown(summary, overview);
        }
        List<Map<String, Object>> focusRows = new ArrayList<>();
        List<Map<String, Object>> secondaryRows = new ArrayList<>();
        fillRows(planType, overview, focusRows, secondaryRows);

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("structuredIntentDetailWire", wire.isEmpty() ? null : wire);
        debug.put("resolvedPlanType", planType);
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
        } else if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
            debug.put("sortKey", null);
            debug.put("sortDirection", null);
        } else if (isUnifiedPurchaseGoodsDetailPlan(planType)) {
            debug.put("sortKey", "supplierPurchaseAmount");
            debug.put("sortDirection", "DESC");
        }

        mergePurchaseGoodsDrilldownObservationIfPresent(overview, debug, planType);
        mergePurchaseGoodsSourceBreakdownHarnessFields(overview, debug, planType, rq);
        finalizePurchaseSupplierGoodsDetailHarnessDebug(overview, debug, planType, focusRows, secondaryRows);
        finalizePurchaseGoodsSourceBreakdownHarnessDebug(overview, debug, planType, focusRows, rq);
        appendPurchaseSupplierGoodsDetailFollowUpDebug(planType, overview, rq, debug);
        appendPurchaseGoodsSourceBreakdownFollowUpDebug(planType, overview, rq, debug);
        patchGoodsSupplierUnitPriceNoDataReason(planType, rq, focusRows, secondaryRows, debug);
        patchGoodsSupplierBreakdownNoDataReason(planType, rq, focusRows, secondaryRows, debug);

        List<AiResultAnchor> anchors = buildResultAnchors(planType, focusRows, overview, rq);
        if (!anchors.isEmpty()) {
            debug.put("resultAnchorsCount", anchors.size());
            if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
                debug.put("resultAnchorSourcePlanType", PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN);
            } else if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)) {
                debug.put("resultAnchorSourcePlanType", PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL);
            }
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
     * 仅依据解析层下发的 structuredIntentDetail wire（及采购来源枚举），禁止读取用户原文推断排行语义。
     */
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

        boolean self = AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(pst);
        boolean sup = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(pst);

        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(wire)) {
            if (self) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW;
            }
            if (sup) {
                return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW;
            }
            return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW;
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
     * Phase2-A：{@code purchase_source_goods_query} + GOODS 锚 + {@code SOURCE_BREAKDOWN} 追问，
     * 须落成 {@link PurchaseAnswerPlan#TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN}，不得静默落回 OVERVIEW。
     */
    static boolean isGoodsSourceBreakdownIntent(AiResolvedQueryContext rq, String structuredWire, String purchaseSourceType) {
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
        if (rq == null || !rq.isFollowUp()) {
            return false;
        }
        String wanted = rq.getFollowUpDetailWanted();
        if (wanted == null || !"SOURCE_BREAKDOWN".equalsIgnoreCase(wanted.trim())) {
            return false;
        }
        if (AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(emptyToNull(rq.getFollowUpTargetEntityType()))) {
            return true;
        }
        return resolveInheritedGoodsAnchor(rq) != null;
    }

    /**
     * GOODS 锚 + {@code SUPPLIER_BREAKDOWN} 追问：落成 {@link PurchaseAnswerPlan#TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL}，
     * 不得落回 SOURCE_BREAKDOWN / OVERVIEW。
     */
    static boolean isGoodsSupplierBreakdownFollowUpIntent(AiResolvedQueryContext rq, String structuredWire) {
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return false;
        }
        if (rq == null || !rq.isFollowUp()) {
            return false;
        }
        String wanted = rq.getFollowUpDetailWanted();
        if (wanted == null
                || !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equalsIgnoreCase(wanted.trim())) {
            return false;
        }
        if (AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(emptyToNull(rq.getFollowUpTargetEntityType()))) {
            return true;
        }
        return resolveInheritedGoodsAnchor(rq) != null;
    }

    /**
     * GOODS 锚 + {@code SUPPLIER_UNIT_PRICE} 追问：落成 {@link PurchaseAnswerPlan#TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL}，
     * 不回退 OVERVIEW。
     */
    static boolean isGoodsSupplierUnitPriceFollowUpIntent(AiResolvedQueryContext rq, String structuredWire) {
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredWire);
        if (!AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(wire)) {
            return false;
        }
        if (rq == null || !rq.isFollowUp()) {
            return false;
        }
        String wanted = rq.getFollowUpDetailWanted();
        if (wanted == null
                || !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equalsIgnoreCase(wanted.trim())) {
            return false;
        }
        if (AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(emptyToNull(rq.getFollowUpTargetEntityType()))) {
            return true;
        }
        return resolveInheritedGoodsAnchor(rq) != null;
    }

    private static void patchGoodsSupplierUnitPriceNoDataReason(
            String planType,
            AiResolvedQueryContext rq,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows,
            LinkedHashMap<String, Object> debug) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || rq == null || debug == null) {
            return;
        }
        String wanted = rq.getFollowUpDetailWanted();
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
            AiResolvedQueryContext rq,
            List<Map<String, Object>> focusRows,
            List<Map<String, Object>> secondaryRows,
            LinkedHashMap<String, Object> debug) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || rq == null || debug == null) {
            return;
        }
        String wanted = rq.getFollowUpDetailWanted();
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
        if (rq == null) {
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
        if (goodsId == null && rq != null) {
            goodsId = emptyToNull(rq.getFollowUpTargetEntityId());
        }
        if (goodsId == null && rq != null) {
            AiResultAnchor inherited = resolveInheritedGoodsAnchor(rq);
            if (inherited != null) {
                goodsId = emptyToNull(inherited.getEntityId());
            }
        }
        return goodsId;
    }

    private static String resolveGoodsSourceBreakdownGoodsName(
            Map<String, Object> focusRow, AiResolvedQueryContext rq) {
        String goodsName = focusRow != null ? firstNonBlankString(focusRow, "goodsName", "goodsTitle", "name") : null;
        if (goodsName == null && rq != null) {
            goodsName = emptyToNull(rq.getFollowUpTargetEntityName());
        }
        if (goodsName == null && rq != null) {
            AiResultAnchor inherited = resolveInheritedGoodsAnchor(rq);
            if (inherited != null) {
                goodsName = emptyToNull(inherited.getEntityName());
            }
        }
        return goodsName;
    }

    private static void fillRows(String planType, Map<String, Object> overview,
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
            }
            default -> {
                LinkedHashMap<String, Object> core = new LinkedHashMap<>();
                core.put("totalPurchaseAmount", overview.get("totalPurchaseAmount"));
                core.put("purchaseOrderCount", overview.get("purchaseOrderCount"));
                focusRows.add(core);
            }
        }
    }

    private static boolean isUnifiedPurchaseGoodsDetailPlan(String planType) {
        return PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL.equals(planType);
    }

    private static void mergePurchaseGoodsDrilldownObservationIfPresent(
            Map<String, Object> overview, LinkedHashMap<String, Object> debug, String planType) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || overview == null) {
            return;
        }
        boolean drill = Boolean.TRUE.equals(overview.get("purchaseGoodsSupplierDrilldown"));
        if (drill) {
            debug.put("requestedEntityType", AiResultAnchor.ENTITY_TYPE_GOODS);
            debug.put("requestedPurchaseSourceType", AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
            if (overview.get("purchaseGoodsDrilldownTargetGoodsName") != null) {
                debug.put("requestedGoodsName", overview.get("purchaseGoodsDrilldownTargetGoodsName"));
            }
            if (overview.get("purchaseGoodsDrilldownTargetGoodsId") != null) {
                debug.put("requestedGoodsId", overview.get("purchaseGoodsDrilldownTargetGoodsId"));
            }
        }
        String[] keys = {
                "purchaseGoodsDrilldownTargetGoodsName",
                "purchaseGoodsDrilldownTargetGoodsId",
                "purchaseSupplierGoodsDetailRowsCount",
                "purchaseSupplierGoodsDetailNoDataReason",
                "purchaseSupplierGoodsDetailAlternativeFacet",
                "purchaseSupplierGoodsDetailAlternativeHasData",
                "purchaseGoodsSupplierDrilldown",
                "purchaseSupplierGoodsDetailAlternativeEvidence",
                "purchaseSupplierGoodsDetailQueryMethod",
                "purchaseSupplierGoodsDetailFocusSupplierId",
                "purchaseSupplierDrilldownTimeWindow",
                "purchaseSupplierDrilldownPurDepIds",
                "purchaseSupplierDrilldownSourceFocus"
        };
        for (String k : keys) {
            if (overview.containsKey(k)) {
                debug.put(k, overview.get(k));
            }
        }
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
                        || Boolean.TRUE.equals(overview.get("purchaseGoodsSupplierDrilldown")));
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
            case "GOODS_ID_MISSING_FOR_DRILLDOWN" -> "FOCUSED_GOODS_NOT_FOUND";
            case "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS",
                 "FOCUSED_GOODS_NOT_FOUND",
                 "NO_PURCHASE_RECORD_FOR_FOCUSED_GOODS",
                 "NO_PURCHASE_LINES_FOR_FOCUSED_SUPPLIER",
                 "NO_SUPPLIER_PURCHASE_FOR_SCOPE",
                 "SUPPLIER_ID_MISSING_FOR_DRILLDOWN",
                 "TOOL_PAYLOAD_EMPTY",
                 "GOODS_SUPPLIER_UNIT_PRICE_NO_DATA" -> t;
            default -> t;
        };
    }

    /**
     * Harness / 复盘：第三轮供货商商品明细时，把 Resolver 锚点协议与 Tool 入参镜像摊平到 {@link PurchaseAnswerPlan#getDebug()}。
     */
    private static void appendPurchaseSupplierGoodsDetailFollowUpDebug(
            String planType,
            Map<String, Object> overview,
            AiResolvedQueryContext rq,
            LinkedHashMap<String, Object> debug) {
        if (!isUnifiedPurchaseGoodsDetailPlan(planType) || rq == null || debug == null) {
            return;
        }
        debug.put("followUpAction", emptyToNull(rq.getFollowUpAction()));
        debug.put("followUpTargetEntityType", emptyToNull(rq.getFollowUpTargetEntityType()));
        debug.put("followUpTargetEntityName", emptyToNull(rq.getFollowUpTargetEntityName()));
        debug.put("followUpDetailWanted", emptyToNull(rq.getFollowUpDetailWanted()));
        Object toolSid = overview == null ? null : overview.get("purchaseSupplierGoodsDetailFocusSupplierId");
        if (toolSid != null) {
            debug.put("toolFocusSupplierId", toolSid);
            String ft = rq.getFollowUpTargetEntityType();
            if (ft != null && AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(ft.trim())) {
                debug.put("followUpTargetEntityId", toolSid);
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
                        : "queryGbPurchaseGoodsAggByLegacyPurchaseMethod");
        Object focusId = overview == null ? null : overview.get("purchaseGoodsSourceBreakdownFocusDisGoodsId");
        if (focusId == null && rq != null) {
            String inheritedId = resolveGoodsSourceBreakdownGoodsId(overview, rq, null);
            if (inheritedId != null) {
                focusId = inheritedId;
            }
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
            String goodsId = resolveGoodsSourceBreakdownGoodsId(overview, rq, null);
            normalized = goodsId == null ? "GOODS_ANCHOR_ID_MISSING" : "TOOL_PAYLOAD_EMPTY";
        }
        debug.put("noDataReason", normalized);
        if (debug.get("goodsAnchorIdMissing") == null) {
            String goodsId = resolveGoodsSourceBreakdownGoodsId(overview, rq, null);
            if (goodsId == null) {
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
    }

    /** Phase2-A：GOODS 来源拆桶追问，摊平 followUp / 继承 GOODS 锚（与 Supplier 明细路径对称）。 */
    private static void appendPurchaseGoodsSourceBreakdownFollowUpDebug(
            String planType,
            Map<String, Object> overview,
            AiResolvedQueryContext rq,
            LinkedHashMap<String, Object> debug) {
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType) || rq == null || debug == null) {
            return;
        }
        debug.put("followUpAction", emptyToNull(rq.getFollowUpAction()));
        debug.put("followUpTargetEntityType", emptyToNull(rq.getFollowUpTargetEntityType()));
        debug.put("followUpTargetEntityName", emptyToNull(rq.getFollowUpTargetEntityName()));
        String tid = rq.getFollowUpTargetEntityId();
        if (tid != null && !tid.isBlank()) {
            debug.put("followUpTargetEntityId", tid.trim());
        } else if (overview != null) {
            Object fid = overview.get("purchaseGoodsSourceBreakdownFocusDisGoodsId");
            if (fid != null) {
                debug.put("followUpTargetEntityId", fid.toString());
            }
        }
        debug.put("followUpDetailWanted", emptyToNull(rq.getFollowUpDetailWanted()));
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
        String goodsId = emptyToNull(rq.getFollowUpTargetEntityId());
        String goodsName = emptyToNull(rq.getFollowUpTargetEntityName());

        Map<String, Object> row = null;
        if (focusRows != null && !focusRows.isEmpty()) {
            row = focusRows.get(0);
        }
        if (goodsId == null && row != null) {
            goodsId = firstNonBlankId(row, "disGoodsId", "goodsId", "gbDisGoodsId");
        }
        if (goodsId == null && overview != null) {
            Object fid = overview.get("purchaseGoodsDrilldownTargetGoodsId");
            if (fid != null && !fid.toString().isBlank()) {
                goodsId = fid.toString().trim();
            }
        }
        if (goodsName == null && row != null) {
            goodsName = firstNonBlankString(row, "goodsName", "goodsTitle", "name");
        }
        if (goodsName == null && overview != null) {
            Object fn = overview.get("purchaseGoodsDrilldownTargetGoodsName");
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
