package com.nongxinle.ai.harness;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MasterBusinessAgentDebug 及经营概览 / 采购 / 出库 / 库房 / 菜品毛利专线摊平字段。
 */
final class AiHarnessMasterAgentDebugSummaryAppender {

    /** 与 {@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode} Promote Keys 的子集对齐。 */
    static final String[] BUSINESS_OVERVIEW_MULTI_ORCHESTRATION_FLAT_FALLBACK_KEYS = {
            "businessOverviewMultiAgentBatchCompleted",
            "businessOverviewAllExpectedDomainsAttempted",
            "businessOverviewMultiAgentAnyDomainSuccess",
            "businessOverviewMultiAgentBatchAttempted",
            "businessOverviewMultiAgentAllDomainsSkipped",
            "businessOverviewMultiAgentBatchUsableForDiagnosis",
            "businessOverviewSuccessfulDomains",
    };

    static final String[] BUSINESS_OVERVIEW_EXECUTION_CONTRACT_KEYS = {
            "businessOverviewExecutionMode",
            "multiBusinessOverviewEligible",
    };

    private AiHarnessMasterAgentDebugSummaryAppender() {
    }

    static void putMasterBusinessAgentDebugDefaults(LinkedHashMap<String, Object> out) {
        out.put("businessOverviewMultiMaster", null);
        out.put("masterAgentEnabled", null);
        out.put("masterAgentUsed", null);
        out.put("selectedAgents", null);
        out.put("dispatchPlan", null);
        out.put("agentResults", null);
        out.put("agentResultStatus", null);
        out.put("degraded", null);
        out.put("failurePolicy", null);
        out.put("fallbackUsed", null);
        out.put("fallbackReason", null);
        out.put("legacyRevenueSkipped", null);
        out.put("masterRevenueToolResultKey", null);
        out.put("masterRevenueToolResultSuccess", null);
        out.put("revenueToolExecutedByMasterPath", null);
        out.put("purchaseMasterAgentEnabled", null);
        out.put("purchaseMasterAgentUsed", null);
        out.put("supplierAnalysisAgentUsed", null);
        out.put("supplierAnalysisAgentStatus", null);
        out.put("supplierAnalysisPlanType", null);
        out.put("purchaseSelectedAgents", null);
        out.put("purchaseDispatchPlan", null);
        out.put("purchaseAgentResults", null);
        out.put("purchaseAgentResultStatus", null);
        out.put("purchaseDegraded", null);
        out.put("purchaseFailurePolicy", null);
        out.put("purchaseFallbackUsed", null);
        out.put("purchaseFallbackReason", null);
        out.put("legacyPurchaseSkipped", null);
        out.put("masterPurchaseToolResultKey", null);
        out.put("masterPurchaseToolResultSuccess", null);
        out.put("purchaseToolExecutedByMasterPath", null);
        out.put("purchaseRequestResolutionDebug", null);
        out.put("stockReduceMasterAgentEnabled", null);
        out.put("stockReduceMasterAgentUsed", null);
        out.put("stockReduceSelectedAgents", null);
        out.put("stockReduceDispatchPlan", null);
        out.put("stockReduceAgentResults", null);
        out.put("stockReduceAgentResultStatus", null);
        out.put("stockReduceDegraded", null);
        out.put("stockReduceFailurePolicy", null);
        out.put("stockReduceFallbackUsed", null);
        out.put("stockReduceFallbackReason", null);
        out.put("legacyStockReduceSkipped", null);
        out.put("masterStockReduceToolResultKey", null);
        out.put("masterStockReduceToolResultSuccess", null);
        out.put("stockReduceToolExecutedByMasterPath", null);
        out.put("stockReduceRequestResolutionDebug", null);
        out.put("warehouseMasterAgentEnabled", null);
        out.put("warehouseDispatchPlan", null);
        out.put("warehouseSelectedAgents", null);
        out.put("warehouseAgentResults", null);
        out.put("warehouseAgentResultStatus", null);
        out.put("warehouseMasterAgentUsed", null);
        out.put("warehouseToolExecutedByMasterPath", null);
        out.put("masterWarehouseToolResultSuccess", null);
        out.put("warehouseFallbackReason", null);
        out.put("warehouseStockAgentUsed", null);
        out.put("warehouseStockAgentStatus", null);
        out.put("warehouseStockOverviewToolSuccess", null);
        out.put("warehouseStockPlanType", null);
        out.put("warehouseStockResultCount", null);
        out.put("dishProfitMasterAgentEnabled", null);
        out.put("dishProfitMasterAgentUsed", null);
        out.put("dishProfitSelectedAgents", null);
        out.put("dishProfitDispatchPlan", null);
        out.put("dishProfitAgentResults", null);
        out.put("dishProfitAgentResultStatus", null);
        out.put("dishProfitFallbackUsed", null);
        out.put("dishProfitFallbackReason", null);
        out.put("legacyDishProfitSkipped", null);
        out.put("masterDishProfitToolResultKey", null);
        out.put("masterDishProfitToolResultSuccess", null);
        out.put("dishIngredientCostBreakdownToolSuccess", null);
        out.put("dishProfitToolExecutedByMasterPath", null);
        out.put("dishProfitRequestResolutionDebug", null);
        out.put("compositeGateAllowed", null);
        out.put("compositeGateReasonCode", null);
        out.put("compositeGateReason", null);
        out.put("compositeGateScopeType", null);
        out.put("compositeGateRecommendedCaseKind", null);
        out.put("compositeGateFinalAnswerPlanType", null);
        out.put("compositeGateDebug", null);
        out.put("compositeGateProductionEnabledSource", null);
        out.put("compositeGateProductionEnabledEffective", null);
        out.put("masterBusinessAgentDebug", null);
        AiHarnessCompositeSummaryAppender.putCompositeHarnessExecutionFieldDefaults(out);
    }

    static void mergeMasterBusinessAgentDebug(LinkedHashMap<String, Object> out, AiRunState state) {
        Map<String, Object> md = state != null ? state.getMasterBusinessAgentDebug() : null;
        String[] revenueKeys = {
                "masterAgentEnabled",
                "masterAgentUsed",
                "selectedAgents",
                "dispatchPlan",
                "agentResults",
                "agentResultStatus",
                "degraded",
                "failurePolicy",
                "fallbackUsed",
                "fallbackReason",
                "legacyRevenueSkipped",
                "masterRevenueToolResultKey",
                "masterRevenueToolResultSuccess",
                "revenueToolExecutedByMasterPath",
        };
        String[] purchaseKeys = {
                "purchaseMasterAgentEnabled",
                "purchaseMasterAgentUsed",
                "supplierAnalysisAgentUsed",
                "supplierAnalysisAgentStatus",
                "supplierAnalysisPlanType",
                "purchaseSelectedAgents",
                "purchaseDispatchPlan",
                "purchaseAgentResults",
                "purchaseAgentResultStatus",
                "purchaseDegraded",
                "purchaseFailurePolicy",
                "purchaseFallbackUsed",
                "purchaseFallbackReason",
                "legacyPurchaseSkipped",
                "masterPurchaseToolResultKey",
                "masterPurchaseToolResultSuccess",
                "purchaseToolExecutedByMasterPath",
                "purchaseRequestResolutionDebug",
        };
        String[] stockReduceKeys = {
                "stockReduceMasterAgentEnabled",
                "stockReduceMasterAgentUsed",
                "stockReduceSelectedAgents",
                "stockReduceDispatchPlan",
                "stockReduceAgentResults",
                "stockReduceAgentResultStatus",
                "stockReduceDegraded",
                "stockReduceFailurePolicy",
                "stockReduceFallbackUsed",
                "stockReduceFallbackReason",
                "legacyStockReduceSkipped",
                "masterStockReduceToolResultKey",
                "masterStockReduceToolResultSuccess",
                "stockReduceToolExecutedByMasterPath",
                "stockReduceRequestResolutionDebug",
        };
        String[] warehouseKeys = {
                "warehouseMasterAgentEnabled",
                "warehouseDispatchPlan",
                "warehouseSelectedAgents",
                "warehouseAgentResults",
                "warehouseAgentResultStatus",
                "warehouseMasterAgentUsed",
                "warehouseToolExecutedByMasterPath",
                "masterWarehouseToolResultSuccess",
                "warehouseFallbackReason",
                "warehouseStockAgentUsed",
                "warehouseStockAgentStatus",
                "warehouseStockOverviewToolSuccess",
                "warehouseStockPlanType",
                "warehouseStockResultCount",
        };
        String[] dishProfitKeys = {
                "dishProfitMasterAgentEnabled",
                "dishProfitMasterAgentUsed",
                "dishProfitSelectedAgents",
                "dishProfitDispatchPlan",
                "dishProfitAgentResults",
                "dishProfitAgentResultStatus",
                "dishProfitFallbackUsed",
                "dishProfitFallbackReason",
                "legacyDishProfitSkipped",
                "masterDishProfitToolResultKey",
                "masterDishProfitToolResultSuccess",
                "dishIngredientCostBreakdownToolSuccess",
                "dishProfitToolExecutedByMasterPath",
                "dishProfitRequestResolutionDebug",
        };
        if (md == null || md.isEmpty()) {
            out.put("businessOverviewMultiMaster", null);
            for (String k : revenueKeys) {
                out.put(k, null);
            }
            for (String k : purchaseKeys) {
                out.put(k, null);
            }
            for (String k : stockReduceKeys) {
                out.put(k, null);
            }
            for (String k : warehouseKeys) {
                out.put(k, null);
            }
            for (String k : dishProfitKeys) {
                out.put(k, null);
            }
            for (String k : BUSINESS_OVERVIEW_MULTI_ORCHESTRATION_FLAT_FALLBACK_KEYS) {
                out.put(k, null);
            }
            mirrorBusinessOverviewExecutionContractKeysDefaults(out);
            out.put("masterBusinessAgentDebug", null);
            fillSupplierAnalysisHarnessFieldsFromMaster(out, state);
            fillWarehouseStockHarnessFieldsFromMaster(out, state);
            return;
        }
        Object boNest = md.get("businessOverviewMultiMaster");
        if (boNest instanceof Map<?, ?> && !((Map<?, ?>) boNest).isEmpty()) {
            out.put("businessOverviewMultiMaster",
                    JSON.parseObject(JSON.toJSONString(boNest), Map.class));
        } else {
            out.put("businessOverviewMultiMaster", null);
        }
        for (String k : revenueKeys) {
            out.put(k, md.get(k));
        }
        for (String k : purchaseKeys) {
            out.put(k, md.get(k));
        }
        for (String k : stockReduceKeys) {
            out.put(k, md.get(k));
        }
        for (String k : warehouseKeys) {
            out.put(k, md.get(k));
        }
        for (String k : dishProfitKeys) {
            out.put(k, md.get(k));
        }
        fillFlatBusinessOverviewOrchestrationFieldsFromNestedMultiMaster(out);
        mirrorBusinessOverviewExecutionContractKeys(out, md);
        try {
            out.put(
                    "masterBusinessAgentDebug",
                    md.isEmpty() ? null : JSON.parseObject(JSON.toJSONString(md), Map.class));
        } catch (Exception ex) {
            out.put("masterBusinessAgentDebug", null);
            out.put("masterBusinessAgentDebugWarning", "serialize_failed");
        }
        fillSupplierAnalysisHarnessFieldsFromMaster(out, state);
        fillWarehouseStockHarnessFieldsFromMaster(out, state);
    }

    static void fillFlatBusinessOverviewOrchestrationFieldsFromNestedMultiMaster(LinkedHashMap<String, Object> out) {
        Object nest = out.get("businessOverviewMultiMaster");
        if (!(nest instanceof Map<?, ?> nm) || nm.isEmpty()) {
            return;
        }
        for (String k : BUSINESS_OVERVIEW_MULTI_ORCHESTRATION_FLAT_FALLBACK_KEYS) {
            if (out.get(k) != null) {
                continue;
            }
            if (nm.containsKey(k)) {
                out.put(k, nm.get(k));
            }
        }
    }

    static void mirrorDishProfitGraphToolEnvelopeSuccessProbes(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            return;
        }
        if (out.get("masterDishProfitToolResultSuccess") == null
                && state.getToolResults() != null
                && state.getToolResults().containsKey(AiBusinessToolIds.DISH_PROFIT_ANALYSIS)) {
            out.put(
                    "masterDishProfitToolResultSuccess",
                    AiHarnessSummaryUtils.harnessToolEnvelopeSuccess(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS));
        }
        reconcileDishIngredientCostBreakdownToolSuccessForHarness(out, state);
    }

    private static void mirrorBusinessOverviewExecutionContractKeysDefaults(LinkedHashMap<String, Object> out) {
        for (String k : BUSINESS_OVERVIEW_EXECUTION_CONTRACT_KEYS) {
            out.put(k, null);
        }
    }

    private static void mirrorBusinessOverviewExecutionContractKeys(
            LinkedHashMap<String, Object> out, Map<String, Object> md) {
        if (md == null) {
            mirrorBusinessOverviewExecutionContractKeysDefaults(out);
            return;
        }
        for (String k : BUSINESS_OVERVIEW_EXECUTION_CONTRACT_KEYS) {
            out.put(k, md.get(k));
        }
    }

    private static void fillSupplierAnalysisHarnessFieldsFromMaster(LinkedHashMap<String, Object> out, AiRunState state) {
        if (out == null) {
            return;
        }
        List<String> sel = AiHarnessSummaryUtils.stringListFromDebugList(out.get("purchaseSelectedAgents"));
        boolean supplierSelected =
                sel != null && sel.contains(BusinessAgentNames.SUPPLIER_ANALYSIS);
        PurchaseAnswerPlan p = state != null ? state.getPurchaseAnswerPlan() : null;
        boolean supplierPlan = PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(out.get("purchaseAnswerPlanType"))
                || PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(out.get("purchaseAnswerPlanType"))
                || (p != null && PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(p.getPlanType()))
                || (p != null && PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(p.getPlanType()));
        if (!supplierSelected && supplierPlan) {
            supplierSelected = true;
        }
        if (!supplierSelected) {
            return;
        }
        if (!Boolean.TRUE.equals(out.get("supplierAnalysisAgentUsed"))) {
            out.put("supplierAnalysisAgentUsed", Boolean.TRUE);
        }
        if (out.get("supplierAnalysisAgentStatus") == null
                || "SKIPPED".equals(String.valueOf(out.get("supplierAnalysisAgentStatus")))) {
            Object st = out.get("purchaseAgentResultStatus");
            out.put("supplierAnalysisAgentStatus", st != null ? st : null);
        }
        if (out.get("supplierAnalysisPlanType") == null) {
            String pt = p != null ? AiHarnessSummaryUtils.blankToNull(p.getPlanType()) : null;
            out.put(
                    "supplierAnalysisPlanType",
                    StringUtils.hasText(pt) ? pt : PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING);
        }
    }

    private static void fillWarehouseStockHarnessFieldsFromMaster(LinkedHashMap<String, Object> out, AiRunState state) {
        if (out == null || state == null) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return;
        }
        boolean whPath =
                state.isWarehouseStockOverviewPath()
                        && AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW.equals(rq.getEffectiveIntentCode())
                        && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(rq.getEffectivePathCode());
        if (!whPath) {
            return;
        }
        if (Boolean.FALSE.equals(out.get("warehouseStockAgentUsed"))) {
            if (out.get("planSource") == null || !StringUtils.hasText(String.valueOf(out.get("planSource")))) {
                out.put("planSource", "WarehouseStockAgent");
            }
            return;
        }
        if (out.get("warehouseStockAgentUsed") == null) {
            out.put("warehouseStockAgentUsed", Boolean.TRUE);
        }
        if (out.get("warehouseStockAgentStatus") == null || "SKIPPED".equals(String.valueOf(out.get("warehouseStockAgentStatus")))) {
            Object st = out.get("warehouseAgentResultStatus");
            if (st != null) {
                out.put("warehouseStockAgentStatus", st);
            } else if (!Boolean.FALSE.equals(out.get("warehouseStockOverviewToolSuccess"))) {
                out.put("warehouseStockAgentStatus", "SUCCESS");
            }
        }
        if (out.get("warehouseStockOverviewToolSuccess") == null) {
            Object t = out.get("masterWarehouseToolResultSuccess");
            if (t instanceof Boolean b) {
                out.put("warehouseStockOverviewToolSuccess", b);
            } else {
                Boolean probed = probeWarehouseStockToolSuccessFromState(state);
                if (probed != null) {
                    out.put("warehouseStockOverviewToolSuccess", probed);
                }
            }
        }
        if (out.get("warehouseStockPlanType") == null) {
            out.put("warehouseStockPlanType", AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW);
        }
        if (out.get("warehouseStockResultCount") == null) {
            out.put("warehouseStockResultCount", resolveWarehouseStockResultCountFromRunState(state));
        }
        if (out.get("planSource") == null || !StringUtils.hasText(String.valueOf(out.get("planSource")))) {
            out.put("planSource", "WarehouseStockAgent");
        }
    }

    private static Boolean probeWarehouseStockToolSuccessFromState(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object s = envelope.get("success");
        if (s instanceof Boolean b) {
            return b;
        }
        Object data = envelope.get("data");
        if (data instanceof Map<?, ?> dm && dm.get("warehouseOverview") != null) {
            return Boolean.TRUE;
        }
        return null;
    }

    private static Integer resolveWarehouseStockResultCountFromRunState(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object data = envelope.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return null;
        }
        Object wo = dm.get("warehouseOverview");
        if (!(wo instanceof Map<?, ?> wom)) {
            return null;
        }
        Object sic = wom.get("stockItemCount");
        if (sic instanceof Number n) {
            return n.intValue();
        }
        int sum = 0;
        String[] listKeys = {
                "lowStockItems",
                "overStockItems",
                "inactiveStockItems",
                "priorityStocktakeItems",
                "storeStockAmountRanking",
                "warehouseStockAmountRanking",
                "storeInventoryAmountRanking"
        };
        for (String k : listKeys) {
            Object v = wom.get(k);
            if (v instanceof List<?> list) {
                sum += list.size();
            }
        }
        return sum;
    }

    private static void reconcileDishIngredientCostBreakdownToolSuccessForHarness(
            LinkedHashMap<String, Object> out, AiRunState state) {
        DishProfitAnswerPlan dpp = state.getDishProfitAnswerPlan();
        if (dpp != null && DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN.equals(dpp.getPlanType())) {
            boolean avail = Boolean.TRUE.equals(
                    AiHarnessAnswerPlanSummaryAppender.resolveIngredientBreakdownAvailableForHarnessPlan(dpp));
            List<Map<String, Object>> rows = dpp.getIngredientRows();
            int n = rows == null ? 0 : rows.size();
            out.put("dishIngredientCostBreakdownToolSuccess", avail && n > 0);
            return;
        }
        if (state.getToolResults() != null
                && state.getToolResults().containsKey(AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN)) {
            out.put(
                    "dishIngredientCostBreakdownToolSuccess",
                    AiHarnessSummaryUtils.harnessToolEnvelopeSuccess(state, AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN));
        } else {
            out.put("dishIngredientCostBreakdownToolSuccess", null);
        }
    }
}
