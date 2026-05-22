package com.nongxinle.ai.harness;

import com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1;
import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.harness.replay.AiHarnessReplayContextProbes;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * AnswerPlan 及执行后镜像字段（purchase / stockReduce / revenue / dishProfit / dishSales / diagnosisPlan）。
 */
final class AiHarnessAnswerPlanSummaryAppender {

    /** 原料行摘要：任一行该列非空即记入 {@code ingredientRowFieldsPresent}。 */
    private static final String[] HARNESS_INGREDIENT_ROW_COLUMN_PROBE_KEYS = {
            "ingredientName",
            "recipeQuantityPerDish",
            "recipeUnit",
            "unitCost",
            "costPerDish",
            "totalCost",
            "costRatio",
    };

    private AiHarnessAnswerPlanSummaryAppender() {
    }

    static void putNullStateAnswerPlanExecutionDefaults(LinkedHashMap<String, Object> out) {
        if (!out.containsKey("answerPreview")) {
            out.put("answerPreview", null);
        }
        if (!out.containsKey("consumedAnswerPlans")) {
            out.put("consumedAnswerPlans", null);
        }
        if (!out.containsKey("missingAnswerPlans")) {
            out.put("missingAnswerPlans", null);
        }
        out.put("usedToolId", null);
        out.put("buildInsightUsed", false);
        out.put("usedBuildInsight", false);
        out.put("buildInsightRequest", null);
        out.put("buildInsightInputStoreRootIds", null);
        out.put("buildInsightInputDepartmentIdsAllowFilter", null);
        out.put("dishesCount", null);
        out.put("dishLineReturned", null);
        out.put("salesDishCount", null);
        out.put("riskLevel", null);
        out.put("resolvedVisibleStoreRootIds", null);
        out.put("resolvedEffectiveSqlDepartmentIds", null);
        out.put("resolvedDishProfitSqlDepartmentIds", null);
        out.put("departmentIdSemanticsHint", null);
        out.put("dishProfitAnswerPlan", null);
        out.put("dishProfitAnswerPlanPresent", false);
        out.put("dishSalesAnswerPlan", null);
        out.put("dishSalesAnswerPlanPresent", false);
        out.put("dishSalesAnswerPlanType", null);
        out.put("dishSalesMatrixRowId", null);
        out.put("dishSalesMatrixWireMissing", null);
        out.put("dishSalesStructuredIntentDetailWire", null);
        out.put("dishSalesKnownGap", null);
        out.put("purchaseAnswerPlan", null);
        out.put("purchaseAnswerPlanPresent", false);
        out.put("purchaseAnswerPlanType", null);
        out.put("purchaseAnswerPlanSortKey", null);
        out.put("purchaseAnswerPlanSortDirection", null);
        out.put("purchaseAnswerPlanFocusRows", null);
        out.put("purchaseAnswerPlanSecondaryRows", null);
        out.put("purchaseAnswerPlanDebug", null);
        out.put("purchaseAnswerPlanResultAnchorsCount", null);
        out.put("purchaseAnswerPlanResultAnchorTypes", null);
        out.put("turnMemoryPersistResultAnchorsCount", null);
        putPurchaseSupplierGoodsDetailHarnessTopDefaults(out);
        out.put("stockReduceAnswerPlan", null);
        out.put("stockReduceAnswerPlanPresent", false);
        out.put("stockReduceAnswerPlanType", null);
        out.put("stockReduceAnswerPlanSortKey", null);
        out.put("stockReduceAnswerPlanSortDirection", null);
        out.put("stockReduceAnswerPlanFocusRows", null);
        out.put("stockReduceAnswerPlanSecondaryRows", null);
        out.put("stockReduceAnswerPlanDebug", null);
        out.put("stockReduceMatrixRowId", null);
        out.put("stockReduceMatrixWireMissing", null);
        out.put("stockReduceStructuredIntentDetailWire", null);
        out.put("stockReduceReduceType", null);
        out.put("stockReduceKnownGap", null);
        out.put("revenueAnswerPlan", null);
        out.put("revenueAnswerPlanPresent", false);
        out.put("revenueAnswerPlanType", null);
        out.put("revenueAnswerPlanSortKey", null);
        out.put("revenueAnswerPlanSortDirection", null);
        out.put("revenueAnswerPlanFocusRows", null);
        out.put("revenueAnswerPlanSecondaryRows", null);
        out.put("revenueAnswerPlanDebug", null);
        out.put("revenueMatrixRowId", null);
        out.put("revenueMatrixWireMissing", null);
        out.put("revenueStructuredIntentDetailWire", null);
        out.put("revenueKnownGap", null);
        out.put("warehouseAnswerPlan", null);
        out.put("warehouseAnswerPlanPresent", false);
        out.put("warehouseAnswerPlanType", null);
        out.put("warehouseAnswerPlanDebug", null);
        out.put("warehouseMatrixRowId", null);
        out.put("warehouseMatrixWireMissing", null);
        out.put("warehouseStructuredIntentDetailWire", null);
        out.put("warehouseKnownGap", null);
        out.put("planSource", null);
        out.put("dataPlanTools", null);
        out.put("usedTools", null);
        out.put("diagnosisPlan", null);
        out.put("diagnosisPlanPresent", false);
        out.put("diagnosisPlanType", null);
        out.put("diagnosisRiskLevel", null);
        out.put("diagnosisDataCompleteness", null);
        out.put("businessStoreCompareEvidenceRowsLen", null);
        out.put("businessStoreCompareTop1StoreName", null);
        out.put("businessStoreCompareTop2StoreName", null);
    }

    static void appendAnswerPlanExecutionFields(LinkedHashMap<String, Object> out, AiRunState state) {
        String used = null;
        List<String> tools = state.getDataPlanTools();
        if (tools != null) {
            for (String t : tools) {
                if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(t)) {
                    used = t;
                    break;
                }
            }
            if (used == null && !tools.isEmpty()) {
                used = tools.get(0);
            }
        }
        out.put("dataPlanTools", tools == null || tools.isEmpty() ? null : new ArrayList<>(tools));
        out.put("usedToolId", used);
        boolean bi = false;
        Object bir = null;
        Object dishesCount = null;
        Object dishLineRet = null;
        Object salesDishCount = null;
        Object riskLevel = null;
        Object pay = state.getToolResults() == null ? null : state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (pay instanceof Map<?, ?> tm) {
            Object data = tm.get("data");
            if (data instanceof Map<?, ?> dm) {
                bi = Boolean.TRUE.equals(dm.get("buildInsightUsed")) || Boolean.TRUE.equals(dm.get("usedBuildInsight"))
                        || dm.containsKey("businessInsightSummary");
                bir = dm.get("buildInsightRequest");
                dishesCount = dm.get("dishLineCountFull");
                dishLineRet = dm.get("dishLineReturned");
                salesDishCount = dm.get("salesDishCount");
                riskLevel = dm.get("riskLevel");
            }
        }
        out.put("buildInsightUsed", bi);
        out.put("usedBuildInsight", bi);
        out.put("buildInsightRequest", bir);
        applyFlattenedBuildInsightDebugFields(out, bir);
        out.put("dishesCount", dishesCount);
        out.put("dishLineReturned", dishLineRet);
        out.put("salesDishCount", salesDishCount);
        out.put("riskLevel", riskLevel);
        AiResolvedQueryContext rqExe = state.getResolvedQueryContext();
        if (rqExe != null && rqExe.getDataScope() != null) {
            AiResolvedDataScope dsx = rqExe.getDataScope();
            out.put("resolvedVisibleStoreRootIds", new ArrayList<>(AiHarnessSummaryUtils.longList(dsx.getVisibleStoreRootIds())));
            out.put("resolvedEffectiveSqlDepartmentIds", new ArrayList<>(AiHarnessSummaryUtils.longList(dsx.getEffectiveSqlDepartmentIds())));
            out.put("resolvedDishProfitSqlDepartmentIds",
                    new ArrayList<>(AiHarnessSummaryUtils.longList(dsx.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_DISH_PROFIT))));
            out.put("departmentIdSemanticsHint",
                    "门店展示=visibleStores/queryStoreIds；department_id IN=expandedSqlDepartmentIds；语义部门=queryRealDepartmentIds（仅 DEPARTMENT 口径）");
        } else {
            out.put("resolvedVisibleStoreRootIds", null);
            out.put("resolvedEffectiveSqlDepartmentIds", null);
            out.put("resolvedDishProfitSqlDepartmentIds", null);
            out.put("departmentIdSemanticsHint", null);
        }
        appendDishProfitAnswerPlan(out, state);
        appendDishSalesAnswerPlan(out, state);
        appendPurchaseAnswerPlan(out, state);
        appendStockReduceAnswerPlan(out, state);
        appendRevenueAnswerPlan(out, state);
        appendWarehouseAnswerPlan(out, state);
        List<String> allPlanned = state.getDataPlanTools();
        out.put("usedTools", AiHarnessSummaryUtils.resolveHarnessUsedTools(state, rqExe, allPlanned));
        appendDiagnosisPlan(out, state);
        applyBusinessStoreCompareEvidenceHarnessSummaryFields(out, state);
        DiagnosisPlan dp = state.getDiagnosisPlan();
        out.put("diagnosisRiskLevel", dp != null ? AiHarnessSummaryUtils.blankToNull(dp.getRiskLevel()) : null);
        out.put("diagnosisDataCompleteness", null);
        mirrorDiagnosisStorePriorityHarnessFields(out, dp);
        appendDiagnosisPlanResultAnchorHarnessFields(out, dp);
        emitDeprecatedDiagnosisPlanHarnessCompatKeys(out, state, rqExe, dp);
        out.put("planSource", resolvePlanSource(state, rqExe));
        appendTurnMemoryPersistResultAnchorsCount(out, state);
    }

    private static void appendTurnMemoryPersistResultAnchorsCount(
            LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            out.put("turnMemoryPersistResultAnchorsCount", null);
            return;
        }
        AiConversationTurnMemory preview = AiConversationTurnMemory.fromCompletedState(state);
        if (preview == null
                || preview.getLastResultAnchors() == null
                || preview.getLastResultAnchors().isEmpty()) {
            out.put("turnMemoryPersistResultAnchorsCount", null);
            return;
        }
        out.put("turnMemoryPersistResultAnchorsCount", preview.getLastResultAnchors().size());
    }

    private static void appendDishProfitAnswerPlan(LinkedHashMap<String, Object> out, AiRunState state) {
        DishProfitAnswerPlan dppFlat = state.getDishProfitAnswerPlan();
        if (dppFlat != null) {
            try {
                out.put("dishProfitAnswerPlan", JSON.parseObject(JSON.toJSONString(dppFlat)));
                out.put("dishProfitAnswerPlanPresent", true);
                out.put("dishProfitAnswerPlanType", summarizeDishProfitAnswerPlanTypeLabel(dppFlat.getPlanType()));
                List<?> dra = dppFlat.getResultAnchors();
                out.put(
                        "dishProfitAnswerPlanResultAnchorsCount",
                        dra == null || dra.isEmpty() ? null : dra.size());
                if (dra != null && !dra.isEmpty()) {
                    LinkedHashSet<String> dTy = new LinkedHashSet<>();
                    for (Object o : dra) {
                        if (o instanceof AiResultAnchor ax
                                && StringUtils.hasText(ax.getEntityType())) {
                            dTy.add(ax.getEntityType().trim());
                        }
                    }
                    out.put("dishProfitAnswerPlanResultAnchorTypes", dTy.isEmpty() ? null : new ArrayList<>(dTy));
                } else {
                    out.put("dishProfitAnswerPlanResultAnchorTypes", null);
                }
                Map<String, Object> dDbg = dppFlat.getDebug();
                Boolean ingAvail = resolveIngredientBreakdownAvailableForHarnessPlan(dppFlat);
                out.put("ingredientBreakdownAvailable", ingAvail);

                String ingReason = dppFlat.getIngredientBreakdownUnavailableReason();
                if (!StringUtils.hasText(ingReason) && dDbg != null) {
                    ingReason = AiHarnessSummaryUtils.blankToNull(
                            AiHarnessSummaryUtils.stringifyHarnessDbg(dDbg.get("ingredientBreakdownUnavailableReason")));
                }
                out.put("ingredientBreakdownUnavailableReason", ingReason);
                putIngredientBreakdownHarnessSummaryFields(out, dppFlat);
            } catch (Exception ex) {
                out.put("dishProfitAnswerPlan", null);
                out.put("dishProfitAnswerPlanWarning", "serialize_failed");
                out.put("dishProfitAnswerPlanPresent", false);
                out.put("dishProfitAnswerPlanType", null);
                out.put("dishProfitAnswerPlanResultAnchorsCount", null);
                out.put("dishProfitAnswerPlanResultAnchorTypes", null);
                out.put("ingredientBreakdownAvailable", null);
                out.put("ingredientBreakdownUnavailableReason", null);
                out.put("ingredientRowsCount", null);
                out.put("ingredientRowCoreMetricPresent", null);
                out.put("ingredientRowFieldsPresent", null);
            }
        } else {
            out.put("dishProfitAnswerPlan", null);
            out.put("dishProfitAnswerPlanPresent", false);
            out.put("dishProfitAnswerPlanType", null);
            out.put("dishProfitAnswerPlanResultAnchorsCount", null);
            out.put("dishProfitAnswerPlanResultAnchorTypes", null);
            out.put("ingredientBreakdownAvailable", null);
            out.put("ingredientBreakdownUnavailableReason", null);
            out.put("ingredientRowsCount", null);
            out.put("ingredientRowCoreMetricPresent", null);
            out.put("ingredientRowFieldsPresent", null);
        }
    }

    private static void appendDishSalesAnswerPlan(LinkedHashMap<String, Object> out, AiRunState state) {
        com.nongxinle.ai.dto.business.DishSalesAnswerPlan dsp = state.getDishSalesAnswerPlan();
        if (dsp != null) {
            try {
                out.put("dishSalesAnswerPlan", JSON.parseObject(JSON.toJSONString(dsp)));
                out.put("dishSalesAnswerPlanPresent", true);
                out.put("dishSalesAnswerPlanType", dsp.getPlanType());
                mirrorDishSalesMatrixHarnessFieldsToSummaryTop(out, dsp);
            } catch (Exception ex) {
                out.put("dishSalesAnswerPlan", null);
                out.put("dishSalesAnswerPlanWarning", "serialize_failed");
                out.put("dishSalesAnswerPlanPresent", false);
                out.put("dishSalesAnswerPlanType", null);
                putDishSalesMatrixHarnessTopDefaults(out);
            }
        } else {
            out.put("dishSalesAnswerPlan", null);
            out.put("dishSalesAnswerPlanPresent", false);
            out.put("dishSalesAnswerPlanType", null);
            putDishSalesMatrixHarnessTopDefaults(out);
        }
    }

    private static void putDishSalesMatrixHarnessTopDefaults(LinkedHashMap<String, Object> out) {
        if (out == null) {
            return;
        }
        out.put("dishSalesMatrixRowId", null);
        out.put("dishSalesMatrixWireMissing", null);
        out.put("dishSalesStructuredIntentDetailWire", null);
        out.put("dishSalesKnownGap", null);
    }

    private static void mirrorDishSalesMatrixHarnessFieldsToSummaryTop(
            LinkedHashMap<String, Object> out, com.nongxinle.ai.dto.business.DishSalesAnswerPlan dsp) {
        if (out == null || dsp == null) {
            putDishSalesMatrixHarnessTopDefaults(out);
            return;
        }
        Map<String, Object> dbg = dsp.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            putDishSalesMatrixHarnessTopDefaults(out);
            return;
        }
        copyDebugString(out, dbg, "dishSalesMatrixRowId");
        copyDebugString(out, dbg, "dishSalesMatrixWireMissing");
        copyDebugString(out, dbg, "dishSalesStructuredIntentDetailWire");
        copyDebugString(out, dbg, "dishSalesKnownGap");
        if (dbg.get("dishSalesAnswerPlanType") != null) {
            out.put("dishSalesAnswerPlanType", dbg.get("dishSalesAnswerPlanType").toString().trim());
        }
    }

    private static void appendPurchaseAnswerPlan(LinkedHashMap<String, Object> out, AiRunState state) {
        PurchaseAnswerPlan pap = state.getPurchaseAnswerPlan();
        if (pap != null) {
            try {
                out.put("purchaseAnswerPlan", JSON.parseObject(JSON.toJSONString(pap)));
                out.put("purchaseAnswerPlanPresent", true);
                out.put("purchaseAnswerPlanType", pap.getPlanType());
                Map<String, Object> dbg = pap.getDebug();
                if (dbg != null && !dbg.isEmpty()) {
                    out.put("purchaseAnswerPlanSortKey", dbg.get("sortKey"));
                    out.put("purchaseAnswerPlanSortDirection", dbg.get("sortDirection"));
                    out.put("purchaseAnswerPlanDebug", new LinkedHashMap<>(dbg));
                } else {
                    out.put("purchaseAnswerPlanSortKey", null);
                    out.put("purchaseAnswerPlanSortDirection", null);
                    out.put("purchaseAnswerPlanDebug", null);
                }
                out.put("purchaseAnswerPlanFocusRows",
                        pap.getFocusRows() == null ? null : new ArrayList<>(pap.getFocusRows()));
                out.put("purchaseAnswerPlanSecondaryRows",
                        pap.getSecondaryRows() == null ? null : new ArrayList<>(pap.getSecondaryRows()));
                List<?> pra = pap.getResultAnchors();
                out.put(
                        "purchaseAnswerPlanResultAnchorsCount",
                        pra == null || pra.isEmpty() ? null : pra.size());
                if (pra != null && !pra.isEmpty()) {
                    LinkedHashSet<String> pTy = new LinkedHashSet<>();
                    for (Object o : pra) {
                        if (o instanceof AiResultAnchor ax
                                && StringUtils.hasText(ax.getEntityType())) {
                            pTy.add(ax.getEntityType().trim());
                        }
                    }
                    out.put("purchaseAnswerPlanResultAnchorTypes", pTy.isEmpty() ? null : new ArrayList<>(pTy));
                } else {
                    out.put("purchaseAnswerPlanResultAnchorTypes", null);
                }
            } catch (Exception ex) {
                out.put("purchaseAnswerPlan", null);
                out.put("purchaseAnswerPlanWarning", "serialize_failed");
                out.put("purchaseAnswerPlanPresent", false);
                out.put("purchaseAnswerPlanType", null);
                out.put("purchaseAnswerPlanSortKey", null);
                out.put("purchaseAnswerPlanSortDirection", null);
                out.put("purchaseAnswerPlanFocusRows", null);
                out.put("purchaseAnswerPlanSecondaryRows", null);
                out.put("purchaseAnswerPlanDebug", null);
                out.put("purchaseAnswerPlanResultAnchorsCount", null);
                out.put("purchaseAnswerPlanResultAnchorTypes", null);
            }
        } else {
            out.put("purchaseAnswerPlan", null);
            out.put("purchaseAnswerPlanPresent", false);
            out.put("purchaseAnswerPlanType", null);
            out.put("purchaseAnswerPlanSortKey", null);
            out.put("purchaseAnswerPlanSortDirection", null);
            out.put("purchaseAnswerPlanFocusRows", null);
            out.put("purchaseAnswerPlanSecondaryRows", null);
            out.put("purchaseAnswerPlanDebug", null);
            out.put("purchaseAnswerPlanResultAnchorsCount", null);
            out.put("purchaseAnswerPlanResultAnchorTypes", null);
        }
        mirrorPurchaseSupplierGoodsDetailHarnessFieldsToSummaryTop(out, state.getPurchaseAnswerPlan());
        mirrorPurchaseGoodsSourceBreakdownHarnessFieldsToSummaryTop(out, state.getPurchaseAnswerPlan());
        reconcileHarnessPurchaseSourceType(out, state);
    }

    private static void appendStockReduceAnswerPlan(LinkedHashMap<String, Object> out, AiRunState state) {
        StockReduceAnswerPlan srap = state.getStockReduceAnswerPlan();
        if (srap != null) {
            try {
                out.put("stockReduceAnswerPlan", JSON.parseObject(JSON.toJSONString(srap)));
                out.put("stockReduceAnswerPlanPresent", true);
                out.put("stockReduceAnswerPlanType", srap.getPlanType());
                Map<String, Object> sdbg = srap.getDebug();
                if (sdbg != null && !sdbg.isEmpty()) {
                    out.put("stockReduceAnswerPlanSortKey", sdbg.get("sortKey"));
                    out.put("stockReduceAnswerPlanSortDirection", sdbg.get("sortDirection"));
                    out.put("stockReduceAnswerPlanDebug", new LinkedHashMap<>(sdbg));
                } else {
                    out.put("stockReduceAnswerPlanSortKey", null);
                    out.put("stockReduceAnswerPlanSortDirection", null);
                    out.put("stockReduceAnswerPlanDebug", null);
                }
                out.put("stockReduceAnswerPlanFocusRows",
                        srap.getFocusRows() == null ? null : new ArrayList<>(srap.getFocusRows()));
                out.put("stockReduceAnswerPlanSecondaryRows",
                        srap.getSecondaryRows() == null ? null : new ArrayList<>(srap.getSecondaryRows()));
                mirrorStockReduceMatrixHarnessFieldsToSummaryTop(out, srap);
            } catch (Exception ex) {
                out.put("stockReduceAnswerPlan", null);
                out.put("stockReduceAnswerPlanWarning", "serialize_failed");
                out.put("stockReduceAnswerPlanPresent", false);
                out.put("stockReduceAnswerPlanType", null);
                out.put("stockReduceAnswerPlanSortKey", null);
                out.put("stockReduceAnswerPlanSortDirection", null);
                out.put("stockReduceAnswerPlanFocusRows", null);
                out.put("stockReduceAnswerPlanSecondaryRows", null);
                out.put("stockReduceAnswerPlanDebug", null);
            }
        } else {
            out.put("stockReduceAnswerPlan", null);
            out.put("stockReduceAnswerPlanPresent", false);
            out.put("stockReduceAnswerPlanType", null);
            out.put("stockReduceAnswerPlanSortKey", null);
            out.put("stockReduceAnswerPlanSortDirection", null);
            out.put("stockReduceAnswerPlanFocusRows", null);
            out.put("stockReduceAnswerPlanSecondaryRows", null);
            out.put("stockReduceAnswerPlanDebug", null);
            putStockReduceMatrixHarnessTopDefaults(out);
        }
    }

    private static void putStockReduceMatrixHarnessTopDefaults(LinkedHashMap<String, Object> out) {
        if (out == null) {
            return;
        }
        out.put("stockReduceMatrixRowId", null);
        out.put("stockReduceMatrixWireMissing", null);
        out.put("stockReduceStructuredIntentDetailWire", null);
        out.put("stockReduceReduceType", null);
        out.put("stockReduceKnownGap", null);
    }

    private static void mirrorStockReduceMatrixHarnessFieldsToSummaryTop(
            LinkedHashMap<String, Object> out, StockReduceAnswerPlan srap) {
        if (out == null) {
            return;
        }
        if (srap == null) {
            putStockReduceMatrixHarnessTopDefaults(out);
            return;
        }
        Map<String, Object> dbg = srap.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            putStockReduceMatrixHarnessTopDefaults(out);
            return;
        }
        copyDebugString(out, dbg, "stockReduceMatrixRowId");
        copyDebugString(out, dbg, "stockReduceMatrixWireMissing");
        copyDebugString(out, dbg, "stockReduceStructuredIntentDetailWire");
        copyDebugString(out, dbg, "stockReduceReduceType");
        copyDebugString(out, dbg, "stockReduceKnownGap");
        if (!out.containsKey("stockReduceAnswerPlanType") && dbg.get("stockReduceAnswerPlanType") != null) {
            out.put("stockReduceAnswerPlanType", dbg.get("stockReduceAnswerPlanType").toString());
        }
    }

    private static void copyDebugString(
            LinkedHashMap<String, Object> out, Map<String, Object> dbg, String key) {
        Object v = dbg.get(key);
        out.put(key, v == null || v.toString().isBlank() ? null : v.toString().trim());
    }

    private static void appendWarehouseAnswerPlan(LinkedHashMap<String, Object> out, AiRunState state) {
        com.nongxinle.ai.dto.business.WarehouseAnswerPlan wap = state.getWarehouseAnswerPlan();
        if (wap != null) {
            try {
                out.put("warehouseAnswerPlan", JSON.parseObject(JSON.toJSONString(wap)));
                out.put("warehouseAnswerPlanPresent", true);
                out.put("warehouseAnswerPlanType", wap.getPlanType());
                Map<String, Object> wdbg = wap.getDebug();
                if (wdbg != null && !wdbg.isEmpty()) {
                    out.put("warehouseAnswerPlanDebug", new LinkedHashMap<>(wdbg));
                } else {
                    out.put("warehouseAnswerPlanDebug", null);
                }
                mirrorWarehouseMatrixHarnessFieldsToSummaryTop(out, wap);
            } catch (Exception ex) {
                out.put("warehouseAnswerPlan", null);
                out.put("warehouseAnswerPlanWarning", "serialize_failed");
                out.put("warehouseAnswerPlanPresent", false);
                out.put("warehouseAnswerPlanType", null);
                out.put("warehouseAnswerPlanDebug", null);
                putWarehouseMatrixHarnessTopDefaults(out);
            }
        } else {
            out.put("warehouseAnswerPlan", null);
            out.put("warehouseAnswerPlanPresent", false);
            out.put("warehouseAnswerPlanType", null);
            out.put("warehouseAnswerPlanDebug", null);
            putWarehouseMatrixHarnessTopDefaults(out);
        }
    }

    private static void putWarehouseMatrixHarnessTopDefaults(LinkedHashMap<String, Object> out) {
        if (out == null) {
            return;
        }
        out.put("warehouseMatrixRowId", null);
        out.put("warehouseMatrixWireMissing", null);
        out.put("warehouseStructuredIntentDetailWire", null);
        out.put("warehouseKnownGap", null);
    }

    private static void mirrorWarehouseMatrixHarnessFieldsToSummaryTop(
            LinkedHashMap<String, Object> out, com.nongxinle.ai.dto.business.WarehouseAnswerPlan wap) {
        if (out == null || wap == null) {
            putWarehouseMatrixHarnessTopDefaults(out);
            return;
        }
        Map<String, Object> dbg = wap.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            putWarehouseMatrixHarnessTopDefaults(out);
            return;
        }
        copyDebugString(out, dbg, "warehouseMatrixRowId");
        copyDebugString(out, dbg, "warehouseMatrixWireMissing");
        copyDebugString(out, dbg, "warehouseStructuredIntentDetailWire");
        copyDebugString(out, dbg, "warehouseKnownGap");
        if (!out.containsKey("warehouseAnswerPlanType") && dbg.get("warehouseAnswerPlanType") != null) {
            out.put("warehouseAnswerPlanType", dbg.get("warehouseAnswerPlanType").toString());
        }
    }

    private static void appendRevenueAnswerPlan(LinkedHashMap<String, Object> out, AiRunState state) {
        DailyRevenueAnswerPlan rap = state.getRevenueAnswerPlan();
        if (rap != null) {
            try {
                out.put("revenueAnswerPlan", JSON.parseObject(JSON.toJSONString(rap)));
                out.put("revenueAnswerPlanPresent", true);
                out.put("revenueAnswerPlanType", rap.getPlanType());
                Map<String, Object> rdbg = rap.getDebug();
                if (rdbg != null && !rdbg.isEmpty()) {
                    out.put("revenueAnswerPlanSortKey", rdbg.get("sortKey"));
                    out.put("revenueAnswerPlanSortDirection", rdbg.get("sortDirection"));
                    out.put("revenueAnswerPlanDebug", new LinkedHashMap<>(rdbg));
                } else {
                    out.put("revenueAnswerPlanSortKey", null);
                    out.put("revenueAnswerPlanSortDirection", null);
                    out.put("revenueAnswerPlanDebug", null);
                }
                out.put("revenueAnswerPlanFocusRows",
                        rap.getFocusRows() == null ? null : new ArrayList<>(rap.getFocusRows()));
                out.put("revenueAnswerPlanSecondaryRows",
                        rap.getSecondaryRows() == null ? null : new ArrayList<>(rap.getSecondaryRows()));
                mirrorRevenueMatrixHarnessFieldsToSummaryTop(out, rap);
            } catch (Exception ex) {
                out.put("revenueAnswerPlan", null);
                out.put("revenueAnswerPlanWarning", "serialize_failed");
                out.put("revenueAnswerPlanPresent", false);
                out.put("revenueAnswerPlanType", null);
                out.put("revenueAnswerPlanSortKey", null);
                out.put("revenueAnswerPlanSortDirection", null);
                out.put("revenueAnswerPlanFocusRows", null);
                out.put("revenueAnswerPlanSecondaryRows", null);
                out.put("revenueAnswerPlanDebug", null);
                putRevenueMatrixHarnessTopDefaults(out);
            }
        } else {
            out.put("revenueAnswerPlan", null);
            out.put("revenueAnswerPlanPresent", false);
            out.put("revenueAnswerPlanType", null);
            out.put("revenueAnswerPlanSortKey", null);
            out.put("revenueAnswerPlanSortDirection", null);
            out.put("revenueAnswerPlanFocusRows", null);
            out.put("revenueAnswerPlanSecondaryRows", null);
            out.put("revenueAnswerPlanDebug", null);
            putRevenueMatrixHarnessTopDefaults(out);
        }
    }

    private static void putRevenueMatrixHarnessTopDefaults(LinkedHashMap<String, Object> out) {
        if (out == null) {
            return;
        }
        out.put("revenueMatrixRowId", null);
        out.put("revenueMatrixWireMissing", null);
        out.put("revenueStructuredIntentDetailWire", null);
        out.put("revenueKnownGap", null);
    }

    private static void mirrorRevenueMatrixHarnessFieldsToSummaryTop(
            LinkedHashMap<String, Object> out, com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan rap) {
        if (out == null) {
            return;
        }
        if (rap == null) {
            putRevenueMatrixHarnessTopDefaults(out);
            return;
        }
        Map<String, Object> dbg = rap.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            putRevenueMatrixHarnessTopDefaults(out);
            return;
        }
        copyDebugString(out, dbg, "revenueMatrixRowId");
        copyDebugString(out, dbg, "revenueMatrixWireMissing");
        copyDebugString(out, dbg, "revenueStructuredIntentDetailWire");
        copyDebugString(out, dbg, "revenueKnownGap");
        if (!out.containsKey("revenueAnswerPlanType") && dbg.get("revenueAnswerPlanType") != null) {
            out.put("revenueAnswerPlanType", dbg.get("revenueAnswerPlanType").toString());
        }
    }

    private static void appendDiagnosisPlan(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state.getDiagnosisPlan() != null) {
            try {
                out.put("diagnosisPlan", JSON.parseObject(JSON.toJSONString(state.getDiagnosisPlan())));
                out.put("diagnosisPlanPresent", true);
                out.put("diagnosisPlanType", state.getDiagnosisPlan().getPlanType());
            } catch (Exception ex) {
                out.put("diagnosisPlan", null);
                out.put("diagnosisPlanWarning", "serialize_failed");
                out.put("diagnosisPlanPresent", false);
                out.put("diagnosisPlanType", null);
            }
        } else {
            out.put("diagnosisPlan", null);
            out.put("diagnosisPlanPresent", false);
            out.put("diagnosisPlanType", null);
        }
    }

    private static String resolvePlanSource(AiRunState state, AiResolvedQueryContext rqExe) {
        String pathEff = rqExe != null ? AiHarnessSummaryUtils.blankToNull(rqExe.getEffectivePathCode()) : null;
        if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(pathEff) || state.isBusinessDiagnosisPath()) {
            return state.getDiagnosisPlan() != null ? "diagnosisPlan" : "N/A";
        }
        if (state.getDiagnosisPlan() != null) {
            return "diagnosisPlan";
        }
        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathEff)) {
            return "revenueAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathEff)) {
            return "stockReduceAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathEff)) {
            return "purchaseAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(pathEff)
                || state.isWarehouseStockOverviewPath()) {
            return state.getWarehouseAnswerPlan() != null ? "warehouseAnswerPlan" : "WarehouseStockAgent";
        }
        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathEff)) {
            return "dishSalesAnswerPlan";
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathEff)) {
            return "dishProfitAnswerPlan";
        }
        return null;
    }

    static void emitDeprecatedDiagnosisPlanHarnessCompatDefaults(LinkedHashMap<String, Object> out) {
        if (out == null) {
            return;
        }
        out.put("businessDiagnosisPlanExists", null);
        out.put("businessDiagnosisPlanType", null);
        out.put("harnessReplayBusinessDiagnosisPlanType", null);
        out.put("storePriorityRankingPlanType", null);
        out.put("harnessReplayStorePriorityRankingPlanType", null);
        out.put("storePriorityRankingRowsLen", null);
        out.put("harnessReplayStorePriorityRankingRowsLen", null);
        out.put("storePriorityRankingTop1StoreName", null);
        out.put("harnessReplayStorePriorityRankingTop1StoreName", null);
        out.put("storePriorityRankingTop1PriorityRank", null);
        out.put("harnessReplayStorePriorityRankingTop1PriorityRank", null);
    }

    static void emitDeprecatedDiagnosisPlanHarnessCompatKeys(
            LinkedHashMap<String, Object> out,
            AiRunState state,
            AiResolvedQueryContext rqExe,
            DiagnosisPlan dp) {
        if (out == null) {
            return;
        }
        boolean exists = dp != null;
        out.put("businessDiagnosisPlanExists", Boolean.valueOf(exists));
        if (!exists) {
            out.put("businessDiagnosisPlanType", null);
            out.put("harnessReplayBusinessDiagnosisPlanType", null);
            return;
        }
        String planType = AiHarnessSummaryUtils.blankToNull(dp.getPlanType());
        out.putIfAbsent("diagnosisPlanType", planType);
        if (rqExe != null
                && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(
                        AiHarnessSummaryUtils.blankToNull(rqExe.getEffectivePathCode()))) {
            out.put("businessDiagnosisPlanType", AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        } else {
            out.put("businessDiagnosisPlanType", planType);
        }
        if (planType != null) {
            out.putIfAbsent("harnessReplayBusinessDiagnosisPlanType", planType);
        }
        if (state == null || dp.getDebug() == null) {
            return;
        }
        Object qtype = dp.getDebug().get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_QUESTION_TYPE);
        if (!BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING.equals(qtype)) {
            return;
        }
        String priorityPlanType = BusinessDiagnosisAgentV1.DIAGNOSIS_QUESTION_STORE_PRIORITY_RANKING;
        out.put("storePriorityRankingPlanType", priorityPlanType);
        out.put("harnessReplayStorePriorityRankingPlanType", priorityPlanType);
        Object rows = dp.getDebug().get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_RANKING_ROWS_COUNT);
        if (rows instanceof Number n) {
            out.put("storePriorityRankingRowsLen", n.intValue());
            out.put("harnessReplayStorePriorityRankingRowsLen", n.intValue());
        }
        Object topName = dp.getDebug().get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_NAME);
        if (topName != null && StringUtils.hasText(topName.toString())) {
            String nm = topName.toString().trim();
            out.put("storePriorityRankingTop1StoreName", nm);
            out.put("harnessReplayStorePriorityRankingTop1StoreName", nm);
        }
        out.put("storePriorityRankingTop1PriorityRank", 1);
        out.put("harnessReplayStorePriorityRankingTop1PriorityRank", 1);
    }

    static void putPurchaseSupplierGoodsDetailHarnessTopDefaults(LinkedHashMap<String, Object> out) {
        out.put("purchaseSupplierGoodsDetailRowsCount", null);
        out.put("purchaseSupplierGoodsDetailNoDataReason", null);
        out.put("purchaseSupplierGoodsDetailAlternativeHasData", null);
        out.put("purchaseGoodsAnchorExecutionTargetGoodsName", null);
        out.put("purchaseGoodsAnchorExecutionTargetGoodsId", null);
        out.put("purchaseSupplierGoodsDetailQueryMethod", null);
        out.put("toolFocusSupplierId", null);
        out.put("focusEntityId", null);
        out.put("inheritedAnchorType", null);
        out.put("inheritedAnchorId", null);
        out.put("inheritedAnchorName", null);
        out.put("purchaseSupplierGoodsDetailNoDataReasonFlat", null);
    }

    static void mirrorPurchaseSupplierGoodsDetailHarnessFieldsToSummaryTop(
            LinkedHashMap<String, Object> out, PurchaseAnswerPlan pap) {
        if (pap == null
                || (!PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(pap.getPlanType())
                        && !PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL.equals(pap.getPlanType()))) {
            putPurchaseSupplierGoodsDetailHarnessTopDefaults(out);
            return;
        }
        Map<String, Object> dbg = pap.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            putPurchaseSupplierGoodsDetailHarnessTopDefaults(out);
            return;
        }
        Object cnt = dbg.get("purchaseSupplierGoodsDetailRowsCount");
        if (cnt instanceof Number n) {
            out.put("purchaseSupplierGoodsDetailRowsCount", n.intValue());
        } else if (cnt == null) {
            out.put("purchaseSupplierGoodsDetailRowsCount", null);
        } else {
            try {
                out.put("purchaseSupplierGoodsDetailRowsCount", Integer.parseInt(cnt.toString().trim(), 10));
            } catch (NumberFormatException e) {
                out.put("purchaseSupplierGoodsDetailRowsCount", null);
            }
        }
        out.put(
                "purchaseSupplierGoodsDetailNoDataReason",
                AiHarnessSummaryUtils.blankToNull(
                        AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("purchaseSupplierGoodsDetailNoDataReason"))));
        Object alt = dbg.get("purchaseSupplierGoodsDetailAlternativeHasData");
        if (alt instanceof Boolean b) {
            out.put("purchaseSupplierGoodsDetailAlternativeHasData", b);
        } else if (alt == null) {
            out.put("purchaseSupplierGoodsDetailAlternativeHasData", null);
        } else {
            out.put("purchaseSupplierGoodsDetailAlternativeHasData", Boolean.valueOf(Boolean.parseBoolean(alt.toString())));
        }
        out.put(
                "purchaseGoodsAnchorExecutionTargetGoodsName",
                AiHarnessSummaryUtils.blankToNull(
                        AiHarnessSummaryUtils.stringifyHarnessDbg(
                                dbg.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME))));
        Object gid = dbg.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID);
        if (gid instanceof Number n) {
            out.put("purchaseGoodsAnchorExecutionTargetGoodsId", n.intValue());
        } else {
            out.put("purchaseGoodsAnchorExecutionTargetGoodsId", gid);
        }
        out.put("purchaseSupplierGoodsDetailQueryMethod", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("queryMethod"))));
        if (out.get("purchaseSupplierGoodsDetailQueryMethod") == null) {
            out.put(
                    "purchaseSupplierGoodsDetailQueryMethod",
                    AiHarnessSummaryUtils.blankToNull(
                            AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("purchaseSupplierGoodsDetailQueryMethod"))));
        }
        out.put("toolFocusSupplierId", dbg.get("toolFocusSupplierId"));
        Object focusEntityId = dbg.get("focusEntityId");
        if (focusEntityId != null) {
            out.put("focusEntityId", focusEntityId);
        }
        out.put("inheritedAnchorType", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("inheritedAnchorType"))));
        out.put("inheritedAnchorId", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("inheritedAnchorId"))));
        out.put("inheritedAnchorName", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("inheritedAnchorName"))));
        out.put("purchaseSupplierGoodsDetailNoDataReasonFlat",
                AiHarnessSummaryUtils.blankToNull(AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("noDataReason"))));
    }

    static void mirrorPurchaseGoodsSourceBreakdownHarnessFieldsToSummaryTop(
            LinkedHashMap<String, Object> out, PurchaseAnswerPlan pap) {
        if (pap == null || !PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(pap.getPlanType())) {
            out.put("purchaseGoodsSourceBreakdownQueryMethod", null);
            out.put("purchaseGoodsSourceBreakdownFocusDisGoodsId", null);
            out.put("purchaseGoodsSourceBreakdownNoDataReasonFlat", null);
            out.put("goodsSourceBreakdownGoodsAnchorIdMissing", null);
            return;
        }
        Map<String, Object> dbg = pap.getDebug();
        if (dbg == null || dbg.isEmpty()) {
            out.put("purchaseGoodsSourceBreakdownQueryMethod", null);
            out.put("purchaseGoodsSourceBreakdownFocusDisGoodsId", null);
            out.put("purchaseGoodsSourceBreakdownNoDataReasonFlat", null);
            out.put("goodsSourceBreakdownGoodsAnchorIdMissing", null);
            return;
        }
        out.put("purchaseGoodsSourceBreakdownQueryMethod", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("queryMethod"))));
        out.put("purchaseGoodsSourceBreakdownFocusDisGoodsId", dbg.get("focusDisGoodsId"));
        out.put("purchaseGoodsSourceBreakdownNoDataReasonFlat", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("noDataReason"))));
        out.put("goodsSourceBreakdownGoodsAnchorIdMissing", dbg.get("goodsAnchorIdMissing"));
        Object fid = dbg.get("focusEntityId");
        if (fid != null) {
            out.put("focusEntityId", fid);
        }
        out.put("inheritedAnchorType", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("inheritedAnchorType"))));
        out.put("inheritedAnchorId", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("inheritedAnchorId"))));
        out.put("inheritedAnchorName", AiHarnessSummaryUtils.blankToNull(
                AiHarnessSummaryUtils.stringifyHarnessDbg(dbg.get("inheritedAnchorName"))));
    }

    static void reconcileHarnessPurchaseSourceType(LinkedHashMap<String, Object> out, AiRunState state) {
        if (out == null || state == null) {
            return;
        }
        PurchaseAnswerPlan pap = state.getPurchaseAnswerPlan();
        if (pap == null) {
            return;
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(pap.getPlanType())) {
            out.put("purchaseSourceType", AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
            return;
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(pap.getPlanType())) {
            out.put("purchaseSourceType", AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
            return;
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL.equals(pap.getPlanType())) {
            out.put("purchaseSourceType", AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE);
            return;
        }
        String pst = pap.getPurchaseSourceType();
        if (pst != null
                && !pst.isBlank()
                && AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(pst.trim())) {
            out.put("purchaseSourceType", AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
        }
    }

    static void mirrorDiagnosisStorePriorityHarnessFields(LinkedHashMap<String, Object> out, DiagnosisPlan dp) {
        if (out == null || dp == null || dp.getDebug() == null) {
            return;
        }
        Map<String, Object> d = dp.getDebug();
        Object rowId = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_REASON_EXPLANATION_MATRIX_ROW_ID);
        if (rowId != null && StringUtils.hasText(String.valueOf(rowId))) {
            out.put("diagnosisReasonExplanationMatrixRowId", String.valueOf(rowId).trim());
        }
        Object facet = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_FACET);
        if (facet != null && StringUtils.hasText(String.valueOf(facet))) {
            out.put("diagnosisFacet", String.valueOf(facet).trim());
        }
        Object child = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_CHILD_DOMAIN);
        if (child != null && StringUtils.hasText(String.valueOf(child))) {
            out.put("diagnosisChildDomain", String.valueOf(child).trim());
        }
        Object gap = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_KNOWN_GAP);
        if (gap != null && StringUtils.hasText(String.valueOf(gap))) {
            out.put("diagnosisKnownGap", String.valueOf(gap).trim());
        }
        Object target = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TARGET_STORE_NAME);
        if (target == null) {
            target = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_NAME);
        }
        out.put(
                "diagnosisTargetStoreName",
                target == null || !StringUtils.hasText(String.valueOf(target))
                        ? null
                        : String.valueOf(target).trim());

        Object qtype = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_QUESTION_TYPE);
        if (qtype != null && StringUtils.hasText(String.valueOf(qtype))) {
            out.put("diagnosisQuestionType", qtype);
        }
        if (qtype == null || !StringUtils.hasText(String.valueOf(qtype))) {
            return;
        }
        Object top = d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_NAME);
        out.put("diagnosisTopStoreName", top == null || !StringUtils.hasText(String.valueOf(top))
                ? null
                : String.valueOf(top).trim());
        out.put("diagnosisTopStoreReasons", d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_TOP_STORE_REASONS));
        out.put("diagnosisRankingRowsCount", d.get(BusinessDiagnosisAgentV1.DEBUG_DIAGNOSIS_RANKING_ROWS_COUNT));
        out.put("storeAnchorCandidateStores", d.get(BusinessDiagnosisAgentV1.DEBUG_STORE_ANCHOR_CANDIDATE_STORES));
        out.put("storeAnchorRejectedReason", d.get(BusinessDiagnosisAgentV1.DEBUG_STORE_ANCHOR_REJECTED_REASON));
        out.put("storeAnchorRejectedSources", d.get(BusinessDiagnosisAgentV1.DEBUG_STORE_ANCHOR_REJECTED_SOURCES));
    }

    static void appendDiagnosisPlanResultAnchorHarnessFields(LinkedHashMap<String, Object> out, DiagnosisPlan dp) {
        if (out == null) {
            return;
        }
        if (dp == null || dp.getResultAnchors() == null || dp.getResultAnchors().isEmpty()) {
            out.put("diagnosisPlanResultAnchorsCount", null);
            out.put("diagnosisPlanResultAnchorTypes", null);
            return;
        }
        List<AiResultAnchor> ra = dp.getResultAnchors();
        out.put("diagnosisPlanResultAnchorsCount", ra.size());
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (AiResultAnchor a : ra) {
            if (a != null && StringUtils.hasText(a.getEntityType())) {
                types.add(a.getEntityType().trim());
            }
        }
        out.put("diagnosisPlanResultAnchorTypes", types.isEmpty() ? null : new ArrayList<>(types));
    }

    static void applyBusinessStoreCompareEvidenceHarnessSummaryFields(
            LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null || !isHarnessStoreCompareDiagnosisWire(state.getResolvedQueryContext())) {
            return;
        }
        DiagnosisPlan dp = state.getDiagnosisPlan();
        List<Map<String, Object>> ev = dp != null ? dp.getStoreCompareEvidence() : null;
        int len = ev == null ? 0 : ev.size();
        out.put("businessStoreCompareEvidenceRowsLen", Integer.valueOf(len));
        List<Map<String, Object>> sorted = sortedStoreCompareEvidenceRowsForHarness(ev);
        out.put(
                "businessStoreCompareTop1StoreName",
                sorted.isEmpty() ? null : AiHarnessSummaryUtils.blankToNull(storeCompareRowPrimaryLabel(sorted.get(0))));
        out.put(
                "businessStoreCompareTop2StoreName",
                sorted.size() < 2 ? null : AiHarnessSummaryUtils.blankToNull(storeCompareRowPrimaryLabel(sorted.get(1))));
    }

    private static boolean isHarnessStoreCompareDiagnosisWire(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return false;
        }
        String canon =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        rq.getQueryIntent().getStructuredIntentDetail());
        return AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(canon);
    }

    private static List<Map<String, Object>> sortedStoreCompareEvidenceRowsForHarness(
            List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> x : raw) {
            if (x != null) {
                rows.add(x);
            }
        }
        rows.sort(
                Comparator.<Map<String, Object>, Double>comparing(
                                AiHarnessAnswerPlanSummaryAppender::storeCompareEvidenceRevenueOrNull,
                                Comparator.nullsFirst(Double::compareTo))
                        .reversed()
                        .thenComparing(r -> AiHarnessSummaryUtils.plainOrEmpty(storeCompareRowPrimaryLabel(r))));
        return rows;
    }

    private static Double storeCompareEvidenceRevenueOrNull(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("revenueAmount");
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = v.toString().trim().replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String storeCompareRowPrimaryLabel(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object n = row.get("storeName");
        if (n != null && StringUtils.hasText(n.toString())) {
            return n.toString().trim();
        }
        Object id = row.get("storeDepartmentId");
        return id != null ? ("门店 " + id) : null;
    }

    private static void applyFlattenedBuildInsightDebugFields(LinkedHashMap<String, Object> out, Object buildInsightRequest) {
        if (!(buildInsightRequest instanceof Map<?, ?> m)) {
            out.put("buildInsightInputStoreRootIds", null);
            out.put("buildInsightInputDepartmentIdsAllowFilter", null);
            return;
        }
        out.put("buildInsightInputStoreRootIds", m.get("buildInsightInputStoreRootIds"));
        out.put("buildInsightInputDepartmentIdsAllowFilter", m.get("buildInsightInputDepartmentIdsAllowFilter"));
    }

    private static String summarizeDishProfitAnswerPlanTypeLabel(String planType) {
        if (!StringUtils.hasText(planType)) {
            return null;
        }
        String t = planType.trim();
        if (DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(t)) {
            return "低毛利排行";
        }
        if (DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN.equals(t)) {
            return "高毛利排行";
        }
        if (DishProfitAnswerPlan.TYPE_DISH_INGREDIENT_COST_BREAKDOWN.equals(t)) {
            return "原料成本构成";
        }
        return t;
    }

    static Boolean resolveIngredientBreakdownAvailableForHarnessPlan(DishProfitAnswerPlan dpp) {
        if (dpp == null) {
            return null;
        }
        Boolean ingAvail = dpp.getIngredientBreakdownAvailable();
        Map<String, Object> dDbg = dpp.getDebug();
        if (ingAvail == null && dDbg != null) {
            Object rawAv = dDbg.get("ingredientBreakdownAvailable");
            if (rawAv instanceof Boolean b) {
                ingAvail = b;
            }
        }
        return ingAvail;
    }

    private static List<String> computeHarnessIngredientRowFieldsPresent(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> present = new LinkedHashSet<>();
        for (Map<String, Object> r : rows) {
            if (r == null || r.isEmpty()) {
                continue;
            }
            for (String k : HARNESS_INGREDIENT_ROW_COLUMN_PROBE_KEYS) {
                if (AiHarnessSummaryUtils.harnessNonBlankish(r.get(k))) {
                    present.add(k);
                }
            }
        }
        return present.isEmpty() ? null : new ArrayList<>(present);
    }

    private static void putIngredientBreakdownHarnessSummaryFields(LinkedHashMap<String, Object> out, DishProfitAnswerPlan dpp) {
        if (dpp == null) {
            out.put("ingredientRowsCount", null);
            out.put("ingredientRowCoreMetricPresent", null);
            out.put("ingredientRowFieldsPresent", null);
            return;
        }
        List<Map<String, Object>> rows = dpp.getIngredientRows();
        int n = rows == null ? 0 : rows.size();
        out.put("ingredientRowsCount", n > 0 ? n : null);
        out.put("ingredientRowFieldsPresent", computeHarnessIngredientRowFieldsPresent(rows));
        boolean metric = false;
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                if (r == null || r.isEmpty()) {
                    continue;
                }
                if (ingredientRowHasCoreMetricForHarness(r)) {
                    metric = true;
                    break;
                }
            }
        }
        out.put("ingredientRowCoreMetricPresent", n > 0 ? metric : null);
    }

    private static boolean ingredientRowHasCoreMetricForHarness(Map<String, Object> r) {
        return AiHarnessSummaryUtils.harnessNonBlankish(r.get("recipeQuantityPerDish"))
                || AiHarnessSummaryUtils.harnessNonBlankish(r.get("costPerDish"))
                || AiHarnessSummaryUtils.harnessNonBlankish(r.get("totalCost"))
                || AiHarnessSummaryUtils.harnessNonBlankish(r.get("costRatio"));
    }
}
