package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.ScopeResolutionTrace;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.RevenueSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.RevenueSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间窗 / 组织范围 / dataScope / queryIntent 路径探针 / mentionedStore。
 */
final class AiHarnessTimeScopeSummaryAppender {

    private AiHarnessTimeScopeSummaryAppender() {
    }

    static void appendTimeAndScopeFields(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        // ── Time Merge Debug：LLM 原始时间 + 合并过程追踪 ──
        appendTimeMergeDebugFields(out, ctx);

        AiResolvedTimeWindow tw = ctx.getTimeWindow();
        if (tw != null) {
            out.put("startDate", tw.getStartDate() != null ? tw.getStartDate().toString() : null);
            out.put("endDate", tw.getEndDate() != null ? tw.getEndDate().toString() : null);
            out.put("timeLabel", AiHarnessSummaryUtils.blankToNull(tw.getTimeLabel()));
            out.put("timeDisplayText", AiHarnessSummaryUtils.blankToNull(tw.getDisplayText()));
            out.put("timeInheritedFromPrevious", tw.isInheritedFromPreviousTurn());
            out.put("timeExplicitInMessage", AiHarnessSummaryUtils.harnessTimeExplicitForSummary(ctx, tw));
        } else {
            out.put("startDate", null);
            out.put("endDate", null);
            out.put("timeLabel", null);
        }

        AiResolvedOrgScope org = ctx.getOrgScope();
        out.put("scopeType", org != null ? AiHarnessSummaryUtils.blankToNull(org.getScopeType()) : null);
        out.put("scopeLabel", org != null ? AiHarnessSummaryUtils.blankToNull(org.getQueryScopeBanner()) : null);
        out.put("visibleStores", summarizeStores(org));

        AiSemanticStoreNarrowingDiagnostics narrowDiag = ctx.getSemanticStoreNarrowingDebug();
        if (narrowDiag != null) {
            out.put(
                    "semanticMentionedStoreNames",
                    narrowDiag.getSemanticMentionedStoreNames() == null
                            ? new ArrayList<String>()
                            : new ArrayList<>(narrowDiag.getSemanticMentionedStoreNames()));
            out.put(
                    "storeRootCandidates",
                    narrowDiag.getStoreRootCandidates() == null
                            ? new ArrayList<String>()
                            : new ArrayList<>(narrowDiag.getStoreRootCandidates()));
            out.put(
                    "visibleStoreCandidates",
                    narrowDiag.getVisibleStoreCandidates() == null
                            ? new ArrayList<String>()
                            : new ArrayList<>(narrowDiag.getVisibleStoreCandidates()));
            out.put("matchedStoreCandidate", AiHarnessSummaryUtils.blankToNull(narrowDiag.getMatchedStoreCandidate()));
            out.put("narrowingFailureReason", AiHarnessSummaryUtils.blankToNull(narrowDiag.getNarrowingFailureReason()));
        } else {
            out.put("semanticMentionedStoreNames", null);
            out.put("storeRootCandidates", null);
            out.put("visibleStoreCandidates", null);
            out.put("matchedStoreCandidate", null);
            out.put("narrowingFailureReason", null);
        }

        AiResolvedDataScope ds = ctx.getDataScope();
        if (ds != null) {
            List<Long> roots = AiHarnessSummaryUtils.longList(ds.getVisibleStoreRootIds());
            List<Long> childOnly = AiHarnessSummaryUtils.longList(ds.getChildDepartmentIds());
            List<Long> sqlExpanded = AiHarnessSummaryUtils.longList(ds.getEffectiveSqlDepartmentIds());
            String qsm = AiHarnessSummaryUtils.blankToNull(ds.getQueryScopeMode());

            out.put("queryScopeKind", AiHarnessSummaryUtils.blankToNull(ds.getQueryScopeKind()));
            out.put("queryStoreIds", AiHarnessSummaryUtils.intList(ds.getQueryStoreIds()));
            out.put("queryRealDepartmentIds", AiHarnessSummaryUtils.intList(ds.getQueryRealDepartmentIds()));
            out.put("queryDistributerId", ds.getQueryDistributerId());
            out.put("storeToDepartmentIds", stringifyStoreToDeptMap(ds.getStoreToDepartmentIds()));

            out.put("visibleStoreRootIds", new ArrayList<>(roots));
            out.put("childDepartmentIds", new ArrayList<>(childOnly));
            out.put("expandedSqlDepartmentIds", new ArrayList<>(sqlExpanded));
            out.put("revenueSqlDepartmentIds", AiHarnessSummaryUtils.longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_REVENUE)));
            out.put("purchaseSqlDepartmentIds", AiHarnessSummaryUtils.longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_PURCHASE)));
            out.put("stockSqlDepartmentIds", AiHarnessSummaryUtils.longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_STOCK)));
            out.put("dishProfitSqlDepartmentIds", AiHarnessSummaryUtils.longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_DISH_PROFIT)));
            out.put("stockReduceSqlDepartmentIds", AiHarnessSummaryUtils.longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_STOCK_REDUCE)));

            out.put("visibleStoreIds", AiHarnessSummaryUtils.longList(ds.getVisibleStoreIds()));
            out.put("visibleWarehouseIds", AiHarnessSummaryUtils.longList(ds.getVisibleWarehouseIds()));
            out.put("explicitChildDepartmentIds", AiHarnessSummaryUtils.longList(ds.getExplicitChildDepartmentIds()));
            out.put("queryScopeMode", qsm);
            out.put("storeToChildDepartmentIds", stringifyStoreChildMap(ds.getStoreToChildDepartmentIds()));
            out.put("departmentScopeModelNote",
                    "主查询维度：queryScopeKind=STORE 用 queryStoreIds（门店根）；DEPARTMENT 用 queryRealDepartmentIds（仅真实部门）；"
                            + "DISTRIBUTER 用 queryDistributerId。业务表 department_id IN 用 expandedSqlDepartmentIds（根∪子），"
                            + "勿与门店列表混淆。storeToDepartmentIds 仅结构说明。");
        } else {
            out.put("visibleStoreIds", null);
            out.put("visibleStoreRootIds", null);
            out.put("childDepartmentIds", null);
            out.put("queryScopeKind", null);
            out.put("queryStoreIds", null);
            out.put("queryRealDepartmentIds", null);
            out.put("queryDistributerId", null);
            out.put("storeToDepartmentIds", null);
            out.put("expandedSqlDepartmentIds", null);
            out.put("revenueSqlDepartmentIds", null);
            out.put("purchaseSqlDepartmentIds", null);
            out.put("stockSqlDepartmentIds", null);
            out.put("dishProfitSqlDepartmentIds", null);
            out.put("stockReduceSqlDepartmentIds", null);
            out.put("visibleWarehouseIds", null);
            out.put("explicitChildDepartmentIds", null);
            out.put("queryScopeMode", null);
            out.put("storeToChildDepartmentIds", null);
            out.put("departmentScopeModelNote", null);
        }

        if (ctx.getConversationScopeMode() != null) {
            out.put("conversationScopeMode", ctx.getConversationScopeMode().name());
        } else {
            out.put("conversationScopeMode", null);
        }
        appendScopeResolutionTrace(out, ctx.getScopeResolutionTrace());

        AiResolvedQueryIntent qi = ctx.getQueryIntent();
        String pst = qi != null ? AiHarnessSummaryUtils.blankToNull(qi.getPurchaseSourceType()) : null;
        String sidWireRaw = qi != null ? AiHarnessSummaryUtils.blankToNull(qi.getStructuredIntentDetail()) : null;
        String sidWire = sidWireRaw;
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        if (!StringUtils.hasText(sidWire) && sem != null) {
            AiQuerySemanticParseResult.SemanticSlotsPart slots = sem.getSemanticSlots();
            if (slots != null && StringUtils.hasText(slots.getStructuredIntentDetailWire())) {
                sidWire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        slots.getStructuredIntentDetailWire().trim());
            }
        }
        String sidCode = AiQuerySemanticLexicon.toStructuredIntentDetailDebugCode(sidWire);
        // 调试/UI：structuredIntentDetail 为人类可读枚举名（如 SUPPLIER_AMOUNT_RANKING）；wire 放 structuredIntentDetailWire 供 Harness 比对。
        String sidDisplay = sidCode != null ? sidCode : sidWire;
        // 供货商金额排行：queryIntent 可能未带 purchaseSourceType（或仍为 ALL），Debug 与采购 Tool 语义对齐为 SUPPLIER_PURCHASE。
        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sidWire)
                || "SUPPLIER_AMOUNT_RANKING".equals(sidCode)) {
            if (!StringUtils.hasText(pst) || AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(pst)) {
                pst = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
            }
        }
        out.put("purchaseSourceType", pst);
        out.put("structuredIntentDetailWire", sidWire);
        out.put("structuredIntentDetail", sidDisplay);
        out.put("structuredIntentDetailCode", sidCode);
        out.put("structuredIntentDetailPresent", sidWire != null && !sidWire.isBlank());

        AiQuerySemanticParseResult.SemanticSlotsPart slotPart = sem != null ? sem.getSemanticSlots() : null;
        if (slotPart != null) {
            out.put("queryObject", AiHarnessSummaryUtils.blankToNull(slotPart.getQueryObject()));
            out.put("operation", AiHarnessSummaryUtils.blankToNull(slotPart.getOperation()));
            out.put("metric", AiHarnessSummaryUtils.blankToNull(slotPart.getMetric()));
            out.put("sourceFacet", AiHarnessSummaryUtils.blankToNull(slotPart.getSourceFacet()));
            out.put("anchorPolicy", AiHarnessSummaryUtils.blankToNull(slotPart.getAnchorPolicy()));
            out.put(
                    "structuredIntentDetailWire",
                    AiHarnessSummaryUtils.blankToNull(
                            StringUtils.hasText(slotPart.getStructuredIntentDetailWire())
                                    ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                            slotPart.getStructuredIntentDetailWire().trim())
                                    : null));
            out.put("answerPlanType", AiHarnessSummaryUtils.blankToNull(slotPart.getAnswerPlanType()));
        } else {
            out.put("queryObject", null);
            out.put("operation", null);
            out.put("metric", null);
            out.put("sourceFacet", null);
            out.put("anchorPolicy", null);
            out.put("structuredIntentDetailWire", null);
            out.put("answerPlanType", null);
        }
        String canonStructuredWire =
                qi != null && StringUtils.hasText(qi.getStructuredIntentDetail())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                qi.getStructuredIntentDetail().trim())
                        : null;
        if (!StringUtils.hasText(canonStructuredWire) && slotPart != null) {
            Object slotWire = out.get("structuredIntentDetailWire");
            if (slotWire instanceof String sw && StringUtils.hasText(sw)) {
                canonStructuredWire = sw;
            }
        }
        out.put("canonicalStructuredIntentDetailWire", AiHarnessSummaryUtils.blankToNull(canonStructuredWire));

        String effectivePath = AiHarnessSummaryUtils.blankToNull(ctx.getEffectivePathCode());
        boolean stockReduceStructured = AiQuerySemanticLexicon.isStructuredStockReduceDetail(sidWire);
        // Run Debug：与 structuredIntentDetail / structuredIntentDetailCode 对齐；出库 path 下用枚举名便于比对 GOODS_OUTBOUND_RANKING、PRODUCE_CONSUME 等
        String stockReduceTypeVal = null;
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(effectivePath) && sidCode != null) {
            stockReduceTypeVal = sidCode;
        } else if (stockReduceStructured && sidDisplay != null) {
            stockReduceTypeVal = sidDisplay;
        }
        out.put("stockReduceType", stockReduceTypeVal);

        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(effectivePath)) {
            out.put("stockReduceStructuredIntentDetailWire", AiHarnessSummaryUtils.blankToNull(canonStructuredWire));
            StockReduceSemanticCapabilityMatrixRow matrixRow =
                    StockReduceSemanticCapabilityMatrix.resolveMatrixRow(effectivePath, canonStructuredWire, sem);
            out.put("stockReduceMatrixRowId", matrixRow == null ? null : matrixRow.getRowId());
            out.put(
                    "stockReduceMatrixWireMissing",
                    StockReduceSemanticCapabilityMatrix.detectMatrixWireMissing(sem, effectivePath, canonStructuredWire)
                            ? StockReduceSemanticCapabilityMatrix.MATRIX_WIRE_MISSING
                            : null);
            String matrixGap = StockReduceSemanticCapabilityMatrix.knownGapForResolvedRow(matrixRow);
            out.put("stockReduceKnownGap", AiHarnessSummaryUtils.blankToNull(matrixGap));
            if (matrixRow != null) {
                out.put("stockReduceReduceType", AiHarnessSummaryUtils.blankToNull(matrixRow.getReduceTypeLabel()));
            } else {
                out.put("stockReduceReduceType", stockReduceTypeVal);
            }
        } else {
            out.put("stockReduceStructuredIntentDetailWire", null);
            out.put("stockReduceMatrixRowId", null);
            out.put("stockReduceMatrixWireMissing", null);
            out.put("stockReduceKnownGap", null);
        }

        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(effectivePath)) {
            out.put("revenueStructuredIntentDetailWire", AiHarnessSummaryUtils.blankToNull(canonStructuredWire));
            RevenueSemanticCapabilityMatrixRow revenueRow =
                    RevenueSemanticCapabilityMatrix.resolveMatrixRow(effectivePath, canonStructuredWire, sem);
            out.put("revenueMatrixRowId", revenueRow == null ? null : revenueRow.getRowId());
            out.put(
                    "revenueMatrixWireMissing",
                    RevenueSemanticCapabilityMatrix.detectMatrixWireMissing(sem, effectivePath, canonStructuredWire)
                            ? RevenueSemanticCapabilityMatrix.MATRIX_WIRE_MISSING
                            : null);
            out.put("revenueKnownGap", AiHarnessSummaryUtils.blankToNull(
                    RevenueSemanticCapabilityMatrix.knownGapForResolvedRow(revenueRow)));
        } else {
            out.put("revenueStructuredIntentDetailWire", null);
            out.put("revenueMatrixRowId", null);
            out.put("revenueMatrixWireMissing", null);
            out.put("revenueKnownGap", null);
        }

        if (AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(effectivePath)) {
            out.put("warehouseStructuredIntentDetailWire", AiHarnessSummaryUtils.blankToNull(canonStructuredWire));
            com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrixRow warehouseRow =
                    com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix.resolveMatrixRow(
                            effectivePath, canonStructuredWire, sem, ctx);
            out.put("warehouseMatrixRowId", warehouseRow == null ? null : warehouseRow.getRowId());
            out.put(
                    "warehouseMatrixWireMissing",
                    com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix.detectMatrixWireMissing(
                                    sem, effectivePath, canonStructuredWire)
                            ? com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix.MATRIX_WIRE_MISSING
                            : null);
            out.put(
                    "warehouseKnownGap",
                    AiHarnessSummaryUtils.blankToNull(
                            com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix.knownGapForResolvedRow(
                                    warehouseRow)));
        } else {
            out.put("warehouseStructuredIntentDetailWire", null);
            out.put("warehouseMatrixRowId", null);
            out.put("warehouseMatrixWireMissing", null);
            out.put("warehouseKnownGap", null);
        }

        boolean dishStructuredProbe = AiQuerySemanticLexicon.isNonOverviewDishProfitStructuredDetail(sidWire);
        String dishProfitStructuredDetailVal = null;
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(effectivePath) && sidCode != null) {
            dishProfitStructuredDetailVal = sidCode;
        } else if (dishStructuredProbe && sidDisplay != null) {
            dishProfitStructuredDetailVal = sidDisplay;
        }
        out.put("dishProfitStructuredDetail", dishProfitStructuredDetailVal);

        out.put("mentionedDishName", AiHarnessSummaryUtils.blankToNull(ctx.getMentionedDishName()));
        String dishProfitMetricType = AiHarnessSummaryUtils.blankToNull(ctx.getDishProfitMetricType());
        if (dishProfitMetricType == null
                && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(effectivePath)
                && StringUtils.hasText(canonStructuredWire)) {
            dishProfitMetricType =
                    AiHarnessSummaryUtils.blankToNull(
                            AiQuerySemanticLexicon.dishProfitMetricTypeFromStructuredWire(canonStructuredWire));
        }
        out.put("dishProfitMetricType", dishProfitMetricType);
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(effectivePath)) {
            boolean wireMissing;
            if (!SemanticContractCompletionEngine.isContractLockedParse(sem)) {
                wireMissing = true;
            } else if (!StringUtils.hasText(canonStructuredWire)) {
                wireMissing = true;
            } else {
                wireMissing = DishProfitSemanticCapabilityMatrix.findFirstTurnRowByWire(canonStructuredWire) == null;
            }
            out.put("dishProfitMatrixWireMissing",
                    wireMissing ? DishProfitSemanticCapabilityMatrix.MATRIX_WIRE_MISSING : null);
        } else {
            out.put("dishProfitMatrixWireMissing", null);
        }

        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effectivePath)) {
            DishSalesSemanticCapabilityMatrixRow dishSalesRow =
                    DishSalesSemanticCapabilityMatrix.resolveMatrixRow(effectivePath, canonStructuredWire, sem);
            out.put("dishSalesMatrixObservedDebugOnly", Boolean.TRUE);
            out.put(
                    "dishSalesMatrixObservedRowId",
                    dishSalesRow == null ? null : dishSalesRow.getRowId());
            out.put(
                    "dishSalesMatrixObservedWire",
                    dishSalesRow == null ? null : dishSalesRow.getStructuredIntentDetailWire());
            out.put(
                    "dishSalesMatrixWireMissing",
                    DishSalesSemanticCapabilityMatrix.detectMatrixWireMissing(sem, effectivePath, canonStructuredWire)
                            ? DishSalesSemanticCapabilityMatrix.MATRIX_WIRE_MISSING
                            : null);
            out.put(
                    "dishSalesKnownGap",
                    AiHarnessSummaryUtils.blankToNull(DishSalesSemanticCapabilityMatrix.knownGapForResolvedRow(dishSalesRow)));
        } else {
            out.put("dishSalesMatrixObservedDebugOnly", null);
            out.put("dishSalesMatrixObservedRowId", null);
            out.put("dishSalesMatrixObservedWire", null);
            out.put("dishSalesMatrixWireMissing", null);
            out.put("dishSalesKnownGap", null);
        }

        if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(effectivePath)) {
            BusinessOverviewSemanticCapabilityMatrixRow overviewRow =
                    BusinessOverviewSemanticCapabilityMatrix.resolveMatrixRow(effectivePath, canonStructuredWire);
            out.put("businessOverviewMatrixRowId", overviewRow == null ? null : overviewRow.getRowId());
            out.put(
                    "businessOverviewMatrixMatched",
                    overviewRow != null && !BusinessOverviewSemanticCapabilityMatrix.isMatrixWireMissing(canonStructuredWire));
            out.put(
                    "businessOverviewMatrixWireMissing",
                    BusinessOverviewSemanticCapabilityMatrix.isMatrixWireMissing(canonStructuredWire)
                            ? BusinessOverviewSemanticCapabilityMatrix.MATRIX_WIRE_MISSING
                            : null);
            out.put("plannerToolsSource", "business_overview_matrix");
            out.put(
                    "matrixPlannerTools",
                    new ArrayList<>(BusinessOverviewSemanticCapabilityMatrix.defaultFourDomainPlannerTools()));
        } else {
            out.put("businessOverviewMatrixRowId", null);
            out.put("businessOverviewMatrixMatched", null);
            out.put("businessOverviewMatrixWireMissing", null);
        }

        if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(effectivePath)) {
            BusinessDiagnosisSemanticCapabilityMatrixRow diagnosisRow =
                    BusinessDiagnosisSemanticCapabilityMatrix.resolveMatrixRow(effectivePath, canonStructuredWire, sem);
            out.put("businessDiagnosisMatrixRowId", diagnosisRow == null ? null : diagnosisRow.getRowId());
            out.put(
                    "businessDiagnosisMatrixMatched",
                    diagnosisRow != null
                            && !BusinessDiagnosisSemanticCapabilityMatrix.isMatrixWireMissing(canonStructuredWire));
            out.put(
                    "businessDiagnosisMatrixWireMissing",
                    BusinessDiagnosisSemanticCapabilityMatrix.isMatrixWireMissing(canonStructuredWire)
                            ? BusinessDiagnosisSemanticCapabilityMatrix.MATRIX_WIRE_MISSING
                            : null);
            out.put(
                    "plannerToolsSource",
                    BusinessDiagnosisSemanticCapabilityMatrix.isDualDomainPurchaseStockWire(canonStructuredWire)
                            ? "business_diagnosis_matrix_dual_domain"
                            : "business_diagnosis_matrix_four_domain");
            out.put(
                    "matrixPlannerTools",
                    new ArrayList<>(
                            BusinessDiagnosisSemanticCapabilityMatrix.plannerToolsForWire(canonStructuredWire)));
        } else {
            out.put("businessDiagnosisMatrixRowId", null);
            out.put("businessDiagnosisMatrixMatched", null);
            out.put("businessDiagnosisMatrixWireMissing", null);
        }

        out.put("mentionedStore", resolveMentionedStore(ctx));
    }

    private static Map<String, List<Integer>> stringifyStoreToDeptMap(Map<Integer, List<Integer>> raw) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (Map.Entry<Integer, List<Integer>> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = String.valueOf(e.getKey());
            List<Integer> v = e.getValue();
            out.put(k, v != null ? new ArrayList<>(v) : new ArrayList<>());
        }
        return out;
    }

    /**
     * JSON 友好的 {@code {"1":[2,5],"3":[4]}} 形式（字符串键更易读）。
     */
    private static Map<String, List<Long>> stringifyStoreChildMap(Map<Long, List<Long>> raw) {
        Map<String, List<Long>> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (Map.Entry<Long, List<Long>> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = String.valueOf(e.getKey());
            List<Long> v = e.getValue();
            out.put(k, v != null ? new ArrayList<>(v) : new ArrayList<>());
        }
        return out;
    }

    private static List<Map<String, Object>> summarizeStores(AiResolvedOrgScope org) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (org == null || org.getVisibleStores() == null) {
            return list;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("storeDepartmentId", s.getStoreDepartmentId());
            row.put("storeName", s.getStoreName());
            list.add(row);
        }
        return list;
    }

    private static String resolveMentionedStore(AiResolvedQueryContext ctx) {
        if (StringUtils.hasText(ctx.getResolvedMatchedSemanticStoreMention())) {
            return ctx.getResolvedMatchedSemanticStoreMention().trim();
        }
        AiFollowUpResolution fur = ctx.getFollowUpResolution();
        if (fur != null && StringUtils.hasText(fur.getStoreScopeFollowUpMentionedName())) {
            return fur.getStoreScopeFollowUpMentionedName().trim();
        }
        AiResolvedOrgScope org = ctx.getOrgScope();
        if (org != null && org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
            AiStoreScopeDTO s = org.getVisibleStores().get(0);
            if (s != null && StringUtils.hasText(s.getStoreName())) {
                return s.getStoreName().trim();
            }
        }
        return null;
    }
    static void overlayReplayResolvedExecutionMirrorsFromDataScope(
            LinkedHashMap<String, Object> out, AiResolvedDataScope ds) {
        if (ds == null) {
            return;
        }
        List<Long> roots = AiHarnessSummaryUtils.longList(ds.getVisibleStoreRootIds());
        if (!roots.isEmpty()) {
            out.put("resolvedVisibleStoreRootIds", new ArrayList<>(roots));
        }
        List<Long> sqlExpanded = AiHarnessSummaryUtils.longList(ds.getEffectiveSqlDepartmentIds());
        if (!sqlExpanded.isEmpty()) {
            out.put("resolvedEffectiveSqlDepartmentIds", new ArrayList<>(sqlExpanded));
        }
        List<Long> dishSql =
                AiHarnessSummaryUtils.longList(ds.getSqlDepartmentIdsForDomain(AiResolvedDataScope.SQL_DOMAIN_DISH_PROFIT));
        out.put("resolvedDishProfitSqlDepartmentIds", new ArrayList<>(dishSql));
        Object hintObj = out.get("departmentIdSemanticsHint");
        if (!(hintObj instanceof String h && StringUtils.hasText(h.trim()))) {
            out.put(
                    "departmentIdSemanticsHint",
                    "门店展示=visibleStores/queryStoreIds；department_id IN=expandedSqlDepartmentIds；语义部门=queryRealDepartmentIds（仅 DEPARTMENT 口径）");
        }
    }
    private static void appendTimeMergeDebugFields(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return;
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        AiQuerySemanticParseResult.TimePart tp = sem != null ? sem.getTime() : null;
        AiConversationTurnMemory prev = ctx.getPreviousTurn();

        out.put("llmTimeAction", sem != null ? AiHarnessSummaryUtils.blankToNull(sem.getTimeAction()) : null);
        out.put("llmTimeType", tp != null ? AiHarnessSummaryUtils.blankToNull(tp.getTimeType()) : null);
        out.put("llmTimeSource", tp != null ? AiHarnessSummaryUtils.blankToNull(tp.getTimeSource()) : null);
        out.put("llmStartDate", tp != null ? AiHarnessSummaryUtils.blankToNull(tp.getStartDate()) : null);
        out.put("llmEndDate", tp != null ? AiHarnessSummaryUtils.blankToNull(tp.getEndDate()) : null);
        out.put(
                "llmNeedInheritFromPrevious",
                tp != null && tp.getNeedInheritFromPrevious() != null
                        ? tp.getNeedInheritFromPrevious()
                        : null);
        out.put("llmTimeReason", tp != null ? AiHarnessSummaryUtils.blankToNull(tp.getReason()) : null);
        out.put("previousStartDate", prev != null ? AiHarnessSummaryUtils.blankToNull(prev.getLastStartDate()) : null);
        out.put("previousEndDate", prev != null ? AiHarnessSummaryUtils.blankToNull(prev.getLastEndDate()) : null);
        out.put("timeContractValid", ctx.getTimeContractValid());
        out.put("timeContractFailureReason", AiHarnessSummaryUtils.blankToNull(ctx.getTimeContractFailureReason()));
    }

    private static void appendScopeResolutionTrace(LinkedHashMap<String, Object> out, ScopeResolutionTrace trace) {
        if (trace == null) {
            out.put("requestScopeMode", null);
            out.put("rawScopeAction", null);
            out.put("effectiveScopeModeBeforeResolveOrg", null);
            out.put("baselineOrgScopeType", null);
            out.put("baselineVisibleStores", null);
            out.put("mergedOrgScopeTypeBeforePreparation", null);
            out.put("mergedOrgScopeTypeAfterPreparation", null);
            out.put("multiTurnInherited", null);
            out.put("semanticNarrowingApplied", null);
            out.put("dataScopeInputScopeType", null);
            out.put("expandedSqlDepartmentIds", null);
            out.put("explicitGroupRequest", null);
            out.put("groupToStoreNarrowingAllowed", null);
            out.put("baselineOrgSource", null);
            out.put("postIntersectOrgScopeType", null);
            out.put("scopeIntersectPath", null);
            return;
        }
        out.put("requestScopeMode", AiHarnessSummaryUtils.blankToNull(trace.getRequestScopeMode()));
        out.put("rawScopeAction", AiHarnessSummaryUtils.blankToNull(trace.getRawScopeAction()));
        out.put("effectiveScopeModeBeforeResolveOrg", AiHarnessSummaryUtils.blankToNull(trace.getEffectiveScopeModeBeforeResolveOrg()));
        out.put("baselineOrgScopeType", AiHarnessSummaryUtils.blankToNull(trace.getBaselineOrgScopeType()));
        out.put(
                "baselineVisibleStores",
                trace.getBaselineVisibleStores() == null || trace.getBaselineVisibleStores().isEmpty()
                        ? null
                        : new ArrayList<>(trace.getBaselineVisibleStores()));
        out.put("mergedOrgScopeTypeBeforePreparation", AiHarnessSummaryUtils.blankToNull(trace.getMergedOrgScopeTypeBeforePreparation()));
        out.put("mergedOrgScopeTypeAfterPreparation", AiHarnessSummaryUtils.blankToNull(trace.getMergedOrgScopeTypeAfterPreparation()));
        out.put("multiTurnInherited", trace.getMultiTurnInherited());
        out.put("semanticNarrowingApplied", trace.getSemanticNarrowingApplied());
        out.put("dataScopeInputScopeType", AiHarnessSummaryUtils.blankToNull(trace.getDataScopeInputScopeType()));
        out.put(
                "queryStoreIds",
                trace.getQueryStoreIds() == null || trace.getQueryStoreIds().isEmpty()
                        ? out.get("queryStoreIds")
                        : new ArrayList<>(trace.getQueryStoreIds()));
        out.put(
                "queryDistributerId",
                trace.getQueryDistributerId() != null ? trace.getQueryDistributerId() : out.get("queryDistributerId"));
        out.put(
                "expandedSqlDepartmentIds",
                trace.getExpandedSqlDepartmentIds() == null || trace.getExpandedSqlDepartmentIds().isEmpty()
                        ? out.get("expandedSqlDepartmentIds")
                        : new ArrayList<>(trace.getExpandedSqlDepartmentIds()));
        out.put("explicitGroupRequest", AiHarnessSummaryUtils.blankToNull(trace.getExplicitGroupRequest()));
        out.put("groupToStoreNarrowingAllowed", trace.getGroupToStoreNarrowingAllowed());
        out.put("baselineOrgSource", AiHarnessSummaryUtils.blankToNull(trace.getBaselineOrgSource()));
        out.put("postIntersectOrgScopeType", AiHarnessSummaryUtils.blankToNull(trace.getPostIntersectOrgScopeType()));
        out.put("scopeIntersectPath", AiHarnessSummaryUtils.blankToNull(trace.getScopeIntersectPath()));
    }
}
