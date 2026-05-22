package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionArgs;
import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.dto.DepartmentPurchaseAggRow;
import com.nongxinle.dto.PurchaseMethodLegacyAggRow;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import com.nongxinle.utils.GbConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nongxinle.ai.util.AiTimeWindowTextFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;

import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_AI_ROLE_CODE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DIS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_GROUP_PURCHASE_AGGREGATION;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_DEPARTMENT_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_PUR_DEP_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_SOURCE_FOCUS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_TIME_WINDOW;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_ENTITY_TYPE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_FOCUS_SUPPLIER_ID;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_NARRATIVE_MODE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_PURCHASE_SOURCE_FOCUS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_QUERY_SCOPE_BANNER;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_START_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOP_DATE;
import static com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_VISIBLE_STORES;

/**
 * 采购概览（{@code purchase_overview_path}）：按 {@link com.nongxinle.ai.context.AiResolvedQueryContext}
 * 解析的组织范围与时间窗聚合采购入库金额、行数、Top 列表与采购方式拆分。
 * <p>采购方式与历史「供货属性摘要」口径一致：type=5 与 type=1 且
 * {@code gb_DPG_purchase_nx_supplier_id} 为正合并为「供货商采购」；type=1 且 nx 为 null 或 -1 为「自采」；其余类型为「其它方式」。
 * 退货以 {@code typeNotEqual}={@link GbConstants.PurchaseOrderType#RETURN} 排除；
 * 统计条件与 {@link com.nongxinle.mapper.GbDistributerPurchaseGoodsMapper#queryGbPurchaseGoodsCount} 同 join。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOverviewTool implements AiTool {

    private static final BigDecimal PURCHASE_METHOD_RECON_TOLERANCE = new BigDecimal("0.08");

    private final GbDistributerPurchaseGoodsService purchaseGoodsService;
    private final GbAiDailyRevenueService gbAiDailyRevenueService;

    @Override
    public String name() {
        return AiBusinessToolIds.PURCHASE_OVERVIEW;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        Long dept = toLong(args.get(ARG_DEPARTMENT_FATHER_ID));
        Long purDep = toLong(args.get(ARG_PURCHASE_DEPARTMENT_ID));
        if (purDep == null) {
            purDep = dept;
        }
        Long disId = toLong(args.get(ARG_DIS_ID));
        String start = str(args.get(ARG_START_DATE));
        String stop = str(args.get(ARG_STOP_DATE));
        boolean groupAgg = Boolean.TRUE.equals(args.get(ARG_GROUP_PURCHASE_AGGREGATION));
        List<Integer> purDepIds = extractIntList(args.get(ARG_RESOLVED_DEPARTMENT_IDS));
        String aiRoleCode = str(args.get(ARG_AI_ROLE_CODE));
        String bannerArg = str(args.get(ARG_QUERY_SCOPE_BANNER));
        String purchaseSourceFocus = str(args.get(ARG_PURCHASE_SOURCE_FOCUS));
        String purchaseNarrativeMode = str(args.get(ARG_PURCHASE_NARRATIVE_MODE));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> visibleStoresArg =
                args.get(ARG_VISIBLE_STORES) instanceof List<?> raw
                        ? (List<Map<String, Object>>) raw
                        : List.of();

        if (disId == null || start.isEmpty() || stop.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("purchaseOverview", Map.of("error", "missing disId or date range"));
            return ToolResult.builder()
                    .success(false)
                    .message("missing disId/date range")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disId, data,
                            "参数不完整"))
                    .build();
        }

        try {
            AiTimeWindowTextFormatter.UserPhrases periodPhrases =
                    AiTimeWindowTextFormatter.fromIsoRange(start, stop, LocalDate.now());
            List<Integer> storeRootsForScope = Collections.emptyList();
            List<Integer> actualQueryDepartmentIds = Collections.emptyList();
            Map<Integer, Integer> purDepRowToStoreRoot = Collections.emptyMap();

            if (groupAgg) {
                storeRootsForScope = resolveGroupPurchaseStoreRoots(purDepIds, visibleStoresArg);
                if (storeRootsForScope.isEmpty()) {
                    log.warn(
                            "[PurchaseOverviewTool] runId={} groupPurchaseAggregation=true but empty store roots (resolvedIds={}, visibleStoresSize={})",
                            request.getRunId(), purDepIds == null ? 0 : purDepIds.size(), visibleStoresArg.size());
                }
            } else if (!visibleStoresArg.isEmpty()) {
                storeRootsForScope = resolveGroupPurchaseStoreRoots(List.of(), visibleStoresArg);
            }

            if (!storeRootsForScope.isEmpty()) {
                actualQueryDepartmentIds =
                        gbAiDailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(storeRootsForScope);
                if (actualQueryDepartmentIds == null || actualQueryDepartmentIds.isEmpty()) {
                    actualQueryDepartmentIds = new ArrayList<>(storeRootsForScope);
                }
                purDepRowToStoreRoot = buildPurDepartmentToStoreRootMap(storeRootsForScope);
            }

            Map<String, Object> base = new HashMap<>(16);
            base.put("disId", disId.intValue());
            base.put("startDate", start);
            base.put("stopDate", stop);
            base.put("useStockFinishDate", Boolean.TRUE);
            base.put("dayuStatus", 2);
            base.put("typeNotEqual", GbConstants.PurchaseOrderType.RETURN);
            if (groupAgg && storeRootsForScope.isEmpty()) {
                base.put("purDepIds", List.of(-1));
            } else if (!actualQueryDepartmentIds.isEmpty()) {
                base.put("purDepIds", new ArrayList<>(actualQueryDepartmentIds));
            } else if (purDep != null) {
                base.put("purDepId", purDep.intValue());
            }

            applyLegacySourceFocusToQueryParams(base, purchaseSourceFocus);

            String narrativeMode = purchaseNarrativeMode;
            if (narrativeMode.isEmpty() && !purchaseSourceFocus.isEmpty()) {
                narrativeMode = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY;
            }
            if (narrativeMode.isEmpty()) {
                narrativeMode = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
            }

            boolean rollupToStoreRoots = !purDepRowToStoreRoot.isEmpty();

            Integer rowCount = purchaseGoodsService.queryGbPurchaseGoodsCount(base);
            boolean hasRows = rowCount != null && rowCount > 0;
            double subtotal = 0.0;
            BigDecimal totalAmountBd = BigDecimal.ZERO;
            if (hasRows) {
                Double st = purchaseGoodsService.queryGbPurchaseGoodsBuySubtotalSum(base);
                totalAmountBd = st == null
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(st).setScale(6, RoundingMode.HALF_UP);
                subtotal = totalAmountBd.doubleValue();
            }

            PurchaseMethodSection methodSection = hasRows
                    ? resolvePurchaseMethodSection(
                            purchaseGoodsService.queryGbPurchaseGoodsAggByLegacyPurchaseMethod(base),
                            rowCount == null ? 0 : rowCount,
                            totalAmountBd)
                    : PurchaseMethodSection.empty();
            methodSection = applyPurchaseSourceFocus(methodSection, purchaseSourceFocus, periodPhrases);

            List<DepartmentPurchaseAggRow> byDep =
                    purchaseGoodsService.sumPurchaseSubtotalGroupedByPurDepartmentId(base);
            byDep = byDep == null ? List.of() : byDep;
            Map<Integer, BigDecimal> amtByPurDep = new HashMap<>();
            for (DepartmentPurchaseAggRow r : byDep) {
                if (r.getDepartmentId() != null && r.getPurchaseSubtotal() != null) {
                    amtByPurDep.put(r.getDepartmentId(), r.getPurchaseSubtotal());
                }
            }

            Map<Integer, BigDecimal> amtByStoreRoot = rollupToStoreRoots
                    ? rollupPurchaseSubtotalByStoreRoot(byDep, purDepRowToStoreRoot, storeRootsForScope)
                    : Collections.emptyMap();

            List<Map<String, Object>> coveredStores = new ArrayList<>();
            List<Map<String, Object>> dataMissingStores = new ArrayList<>();
            if (!visibleStoresArg.isEmpty()) {
                for (Map<String, Object> vs : visibleStoresArg) {
                    if (vs == null) {
                        continue;
                    }
                    Integer id = toInt(vs.get("storeDepartmentId"));
                    if (id == null || id <= 0) {
                        continue;
                    }
                    BigDecimal sub = resolvePurchaseSubtotalForVisibleStore(id, rollupToStoreRoots, amtByStoreRoot, amtByPurDep);
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("storeDepartmentId", id);
                    row.put("storeName", vs.get("storeName"));
                    row.put("purchaseSubtotal", sub.setScale(1, RoundingMode.HALF_UP).toPlainString());
                    if (sub.signum() > 0) {
                        coveredStores.add(row);
                    } else {
                        dataMissingStores.add(row);
                    }
                }
            } else if (purDep != null && !groupAgg && visibleStoresArg.isEmpty()) {
                BigDecimal sub = amtByPurDep.getOrDefault(purDep.intValue(), BigDecimal.ZERO);
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("storeDepartmentId", purDep.intValue());
                row.put("storeName", "本门店");
                row.put("purchaseSubtotal", sub.setScale(1, RoundingMode.HALF_UP).toPlainString());
                if (sub.signum() > 0) {
                    coveredStores.add(row);
                } else {
                    dataMissingStores.add(row);
                }
            } else if (purDepIds != null && !purDepIds.isEmpty()) {
                for (Integer id : purDepIds) {
                    if (id == null || id <= 0) {
                        continue;
                    }
                    BigDecimal sub = resolvePurchaseSubtotalForVisibleStore(id, rollupToStoreRoots, amtByStoreRoot, amtByPurDep);
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("storeDepartmentId", id);
                    row.put("storeName", String.format(Locale.CHINA, "门店 %d", id));
                    row.put("purchaseSubtotal", sub.setScale(1, RoundingMode.HALF_UP).toPlainString());
                    if (sub.signum() > 0) {
                        coveredStores.add(row);
                    } else {
                        dataMissingStores.add(row);
                    }
                }
            }

            if (groupAgg || rollupToStoreRoots) {
                boolean scopeFromVisibleStores =
                        !storeRootIdsFromVisibleStoresOnly(visibleStoresArg).isEmpty();
                log.info(
                        "[PurchaseOverviewTool] runId={} purchaseScopeRootSource={} groupPurchaseAggregation={} storeTreeRollup={} aiRoleCode={} requestDepartmentFatherId={} requestPurDepId={} visibleStoresCount={} visibleStoreIds={} storeRootIds={} expandedQueryIdCount={} statStartDate={} statEndDate={} purchaseOrderCount={} totalPurchaseAmount={} purchaseMethodSupported={} coveredStores={} dataMissingStores={}",
                        request.getRunId(),
                        scopeFromVisibleStores ? "visibleStores" : "resolvedDepartmentIdsFallback",
                        groupAgg,
                        rollupToStoreRoots,
                        aiRoleCode.isEmpty() ? "-" : aiRoleCode,
                        dept,
                        purDep,
                        visibleStoresArg.size(),
                        visibleStoresArg.stream()
                                .filter(vs -> vs != null)
                                .map(vs -> toInt(vs.get("storeDepartmentId")))
                                .filter(id -> id != null && id > 0)
                                .collect(Collectors.toList()),
                        storeRootsForScope,
                        actualQueryDepartmentIds == null ? 0 : actualQueryDepartmentIds.size(),
                        start,
                        stop,
                        rowCount == null ? 0 : rowCount,
                        formatDecimal(subtotal),
                        methodSection.supported,
                        summarizeStorePurchaseRowsForLog(coveredStores),
                        summarizeStorePurchaseRowsForLog(dataMissingStores));
            }

            Map<String, Object> topBase = new HashMap<>(base);
            List<GbDistributerGoodsEntity> topByTimes = hasRows
                    ? nullToEmpty(purchaseGoodsService.queryGbPurchaseGoodsTopTimesMerged(topBase))
                    : List.of();
            List<GbDistributerGoodsEntity> topBySub = hasRows
                    ? nullToEmpty(purchaseGoodsService.queryGbPurchaseGoodsTopSubtotalMerged(topBase))
                    : List.of();
            List<GbDistributerGoodsEntity> topPrice = hasRows
                    ? nullToEmpty(purchaseGoodsService.queryGbPurchaseGoodsTopPriceFluctuation(topBase))
                    : List.of();
            List<Map<String, Object>> topSuppliers = hasRows
                    ? applyTopSuppliersFocus(
                            filterRealSupplierSpendTopRows(
                                    normalizeSupplierDisplayRows(
                                            nullToEmptyMap(purchaseGoodsService.queryGbPurchaseSupplierSpendTop(
                                                    base)))),
                            purchaseSourceFocus)
                    : List.of();

            String storeCoverageSummary =
                    buildStoreCoverageSummaryCn(groupAgg, visibleStoresArg, coveredStores, dataMissingStores,
                            purchaseSourceFocus, periodPhrases);

            if (log.isInfoEnabled()) {
                log.info(
                        "[PurchaseOverviewTool] runId={} purchaseSourceFocus={} purchaseNarrativeMode={} sqlLegacySourceFilter={} purchaseOrderCount={} totalPurchaseAmount={} startDate={} endDate={}",
                        request.getRunId(),
                        purchaseSourceFocus.isEmpty() ? "-" : purchaseSourceFocus,
                        narrativeMode,
                        base.get("legacyPurchaseMethodFocus") == null ? "none" : base.get("legacyPurchaseMethodFocus"),
                        rowCount == null ? 0 : rowCount,
                        formatDecimal(subtotal),
                        start,
                        stop);
            }

            List<Map<String, Object>> goodsFrequencyTop = mapGoodsFrequencyTop(topByTimes);
            List<Map<String, Object>> goodsAmountTop = mapGoodsAmountTop(topBySub);

            PurchaseAnchorExecutionExtras anchorExecution =
                    resolvePurchaseAnchorExecution(
                            args, base, hasRows, purchaseSourceFocus, goodsAmountTop);

            Map<String, Object> purchaseOverview = new LinkedHashMap<>();
            purchaseOverview.put("queryScopeBanner", bannerArg.isEmpty() ? null : bannerArg);
            purchaseOverview.put("purchaseSourceFocus",
                    purchaseSourceFocus.isEmpty() ? null : purchaseSourceFocus);
            purchaseOverview.put("purchaseNarrativeMode", narrativeMode);
            purchaseOverview.put("visibleStores", new ArrayList<>(visibleStoresArg));
            purchaseOverview.put("coveredStores", coveredStores);
            purchaseOverview.put("dataMissingStores", dataMissingStores);
            purchaseOverview.put("storeCoverageSummary", storeCoverageSummary);
            purchaseOverview.put("purchaseOrderCount", rowCount == null ? 0 : rowCount);
            purchaseOverview.put("totalPurchaseAmount", formatDecimal(subtotal));
            purchaseOverview.put("purchaseMethodBreakdownSupported", methodSection.supported);
            purchaseOverview.put("purchaseMethodBreakdown", new ArrayList<>(methodSection.breakdown));
            purchaseOverview.put("purchaseMethodNote", methodSection.note);
            purchaseOverview.put("purchaseMethodSummaryFragment", methodSection.narrativeFragment);
            purchaseOverview.put("goodsPurchaseFrequencyTop", goodsFrequencyTop);
            purchaseOverview.put("goodsPurchaseAmountTop", goodsAmountTop);
            if (anchorExecution.active) {
                if (anchorExecution.goodsSourceBreakdown) {
                    purchaseOverview.put("purchaseGoodsSourceBreakdownActive", Boolean.TRUE);
                    purchaseOverview.put(
                            "purchaseGoodsSourceBreakdownQueryMethod",
                            anchorExecution.queryMethod == null || anchorExecution.queryMethod.isBlank()
                                    ? "queryGbPurchaseGoodsAggByLegacyPurchaseMethod"
                                    : anchorExecution.queryMethod);
                    purchaseOverview.put("purchaseGoodsSourceBreakdownRow", anchorExecution.sourceBreakdownRow);
                    purchaseOverview.put("purchaseGoodsSourceBreakdownNoDataReason", anchorExecution.noDataReason);
                    purchaseOverview.put(
                            "purchaseGoodsSourceBreakdownFocusDisGoodsId",
                            anchorExecution.targetGoodsId != null && anchorExecution.targetGoodsId > 0
                                    ? anchorExecution.targetGoodsId
                                    : null);
                    purchaseOverview.put(
                            "purchaseGoodsSourceBreakdownGoodsAnchorIdMissing",
                            anchorExecution.goodsAnchorIdMissing);
                    purchaseOverview.put(
                            "purchaseGoodsSourceBreakdownBuckets",
                            anchorExecution.sourceBreakdownBuckets == null || anchorExecution.sourceBreakdownBuckets.isEmpty()
                                    ? List.of()
                                    : new ArrayList<>(anchorExecution.sourceBreakdownBuckets));
                    Object scopePids = base.get("purDepIds");
                    if (scopePids instanceof List<?> l && !l.isEmpty()) {
                        purchaseOverview.put(
                                "purchaseGoodsSourceBreakdownExpandedSqlDepartmentIds", new ArrayList<>(l));
                    } else if (base.get("purDepId") != null) {
                        purchaseOverview.put(
                                "purchaseGoodsSourceBreakdownExpandedSqlDepartmentIds",
                                List.of(base.get("purDepId")));
                    } else {
                        purchaseOverview.put("purchaseGoodsSourceBreakdownExpandedSqlDepartmentIds", null);
                    }
                    purchaseOverview.put("purchaseSupplierGoodsDetailRows", List.of());
                    purchaseOverview.put("purchaseSupplierGoodsDetailRowsCount", 0);
                    purchaseOverview.put("purchaseSupplierGoodsDetailNoDataReason", null);
                    purchaseOverview.put("purchaseSupplierGoodsDetailAlternativeFacet", null);
                    purchaseOverview.put("purchaseSupplierGoodsDetailAlternativeHasData", null);
                    purchaseOverview.put("purchaseSupplierGoodsDetailQueryMethod", null);
                } else {
                    purchaseOverview.put("purchaseSupplierGoodsDetailRows",
                            new ArrayList<>(anchorExecution.detailRows));
                    if (anchorExecution.goodsAnchorSupplierBreakdown) {
                        putGoodsAnchorSupplierExecutionPayload(
                                purchaseOverview,
                                anchorExecution.targetGoodsName.isEmpty()
                                        ? null
                                        : anchorExecution.targetGoodsName,
                                anchorExecution.targetGoodsId);
                    }
                    purchaseOverview.put("purchaseSupplierGoodsDetailRowsCount", anchorExecution.detailRows.size());
                    purchaseOverview.put("purchaseSupplierGoodsDetailNoDataReason", anchorExecution.noDataReason);
                    if (anchorExecution.goodsAnchorSupplierBreakdown) {
                        purchaseOverview.put("purchaseSupplierGoodsDetailAlternativeFacet", "SELF_PURCHASE");
                        purchaseOverview.put(
                                "purchaseSupplierGoodsDetailAlternativeHasData", anchorExecution.alternativeHasData);
                        if (anchorExecution.alternativeEvidence != null && !anchorExecution.alternativeEvidence.isEmpty()) {
                            purchaseOverview.put(
                                    "purchaseSupplierGoodsDetailAlternativeEvidence", anchorExecution.alternativeEvidence);
                        }
                    } else {
                        purchaseOverview.put("purchaseSupplierGoodsDetailAlternativeFacet", null);
                        purchaseOverview.put("purchaseSupplierGoodsDetailAlternativeHasData", null);
                    }
                    if (anchorExecution.queryMethod != null && !anchorExecution.queryMethod.isBlank()) {
                        purchaseOverview.put("purchaseSupplierGoodsDetailQueryMethod", anchorExecution.queryMethod);
                    }
                    if (anchorExecution.focusSupplierId != null && anchorExecution.focusSupplierId > 0) {
                        purchaseOverview.put("purchaseSupplierGoodsDetailFocusSupplierId", anchorExecution.focusSupplierId);
                    }
                }
                String timeWindow =
                        str(args.get(ARG_START_DATE)).isEmpty() || str(args.get(ARG_STOP_DATE)).isEmpty()
                                ? null
                                : str(args.get(ARG_START_DATE)) + "~" + str(args.get(ARG_STOP_DATE));
                Object pids = base.get("purDepIds");
                Object purDepPayload =
                        pids instanceof List<?> l && !l.isEmpty()
                                ? new ArrayList<>(l)
                                : (base.get("purDepId") != null ? List.of(base.get("purDepId")) : null);
                String sourceFocusPayload =
                        purchaseSourceFocus.isEmpty() ? null : purchaseSourceFocus;
                putSupplierAnchorExecutionScopePayload(
                        purchaseOverview, timeWindow, purDepPayload, sourceFocusPayload);
            }
            purchaseOverview.put("topGoods", mapTopGoods(topByTimes, topBySub));
            purchaseOverview.put("topSuppliers", topSuppliers);
            purchaseOverview.put("priceChangeItems", mapPriceChange(topPrice));
            purchaseOverview.put("highAmountItems", mapHighAmount(topBySub));
            purchaseOverview.put("purchaseWithoutSalesItems", List.of());
            purchaseOverview.put("recommendations", buildRecommendations(hasRows, periodPhrases));

            Map<String, Object> inner = new LinkedHashMap<>(2);
            inner.put("purchaseOverview", purchaseOverview);

            return ToolResult.builder()
                    .success(true)
                    .message(hasRows ? "ok" : "empty")
                    .data(AiBusinessToolResponses.envelope(name(), true, false, start, stop, dept, disId, inner, null))
                    .build();
        } catch (Exception e) {
            log.warn("[PurchaseOverviewTool] runId={} dis={}", request.getRunId(), disId, e);
            AiTimeWindowTextFormatter.UserPhrases periodPhrases =
                    AiTimeWindowTextFormatter.fromIsoRange(start, stop, LocalDate.now());
            Map<String, Object> failOverview = new LinkedHashMap<>();
            failOverview.put("queryScopeBanner", bannerArg.isEmpty() ? null : bannerArg);
            failOverview.put("visibleStores", new ArrayList<>(visibleStoresArg));
            failOverview.put("purchaseOrderCount", 0);
            failOverview.put("totalPurchaseAmount", "0");
            failOverview.put("purchaseMethodBreakdownSupported", false);
            failOverview.put("purchaseMethodBreakdown", List.of());
            failOverview.put("purchaseMethodNote", null);
            failOverview.put("purchaseMethodSummaryFragment", null);
            failOverview.put("goodsPurchaseFrequencyTop", List.of());
            failOverview.put("goodsPurchaseAmountTop", List.of());
            failOverview.put("topGoods", List.of());
            failOverview.put("topSuppliers", List.of());
            failOverview.put("priceChangeItems", List.of());
            failOverview.put("highAmountItems", List.of());
            failOverview.put("purchaseWithoutSalesItems", List.of());
            failOverview.put("recommendations", List.of(periodPhrases.getDisplayTimeRange()
                    + "暂未查询到有效采购记录（查询异常降级），请确认采购入库数据是否已录入。"));
            failOverview.put("coveredStores", List.of());
            failOverview.put("dataMissingStores", List.of());
            failOverview.put("storeCoverageSummary", null);
            Map<String, Object> inner = Map.of("purchaseOverview", failOverview);
            return ToolResult.builder()
                    .success(false)
                    .message("query_error")
                    .data(AiBusinessToolResponses.envelope(name(), false, false, start, stop, dept, disId, inner,
                            "采购概览查询异常，已降级为无数据口径"))
                    .build();
        }
    }

    private PurchaseAnchorExecutionExtras resolvePurchaseAnchorExecution(
            Map<String, Object> args,
            Map<String, Object> base,
            boolean hasRows,
            String purchaseSourceFocus,
            List<Map<String, Object>> goodsAmountTop) {
        PurchaseAnchorExecutionExtras out = new PurchaseAnchorExecutionExtras();
        if (isGoodsAnchorSourceBreakdownExecutionArgs(args)) {
            return resolveGoodsAnchorSourceBreakdownExecution(args, base, hasRows, out);
        }
        if (isChannelOverviewGoodsDetailExecutionArgs(args)) {
            return resolveChannelOverviewGoodsDetailFromTopGoods(
                    goodsAmountTop, hasRows, purchaseSourceFocus, out);
        }
        if (isSupplierAnchorGoodsLinesExecutionArgs(args)) {
            return resolveSupplierAnchorGoodsLinesExecution(args, base, hasRows, purchaseSourceFocus, out);
        }
        if (!isGoodsAnchorSupplierBreakdownExecutionArgs(args)) {
            return out;
        }
        out.active = true;
        out.goodsAnchorSupplierBreakdown = true;
        out.targetGoodsName = str(args.get(ARG_PURCHASE_FOCUS_GOODS_NAME));
        out.targetGoodsId = toInt(args.get(ARG_PURCHASE_FOCUS_DIS_GOODS_ID));
        out.queryMethod = "queryGbPurchaseSupplierAggRowsForFocusedDisGoods";
        boolean idOk = out.targetGoodsId != null && out.targetGoodsId > 0;
        boolean nameOk = !out.targetGoodsName.isEmpty();

        if (!hasRows) {
            if (!idOk && !nameOk) {
                out.noDataReason = "GOODS_NOT_FOUND_FOR_PURCHASE_DETAIL";
            } else {
                out.noDataReason = "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS";
            }
            applySelfPurchaseAlternativeProbeForGoodsAnchorExecution(out, base, idOk ? out.targetGoodsId : null);
            return out;
        }
        if (!idOk) {
            if (!nameOk) {
                out.noDataReason = "GOODS_NOT_FOUND_FOR_PURCHASE_DETAIL";
            } else {
                out.noDataReason = "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS";
            }
            applySelfPurchaseAlternativeProbeForGoodsAnchorExecution(out, base, null);
            return out;
        }
        Map<String, Object> executionBase = new HashMap<>(base);
        executionBase.put("disGoodsId", out.targetGoodsId);
        out.detailRows = new ArrayList<>(nullToEmptyMap(
                purchaseGoodsService.queryGbPurchaseSupplierAggRowsForFocusedDisGoods(executionBase)));
        if (!out.detailRows.isEmpty()) {
            return out;
        }
        out.noDataReason = "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS";
        applySelfPurchaseAlternativeProbeForGoodsAnchorExecution(out, base, out.targetGoodsId);
        return out;
    }

    /**
     * Phase2-A：GOODS 锚 + {@code SOURCE_BREAKDOWN}，按采购记录行 legacy 桶拆自采/供货商/其它（时间窗 + 权限 + ALL，不带 {@code legacyPurchaseMethodFocus}）。
     */
    private PurchaseAnchorExecutionExtras resolveGoodsAnchorSourceBreakdownExecution(
            Map<String, Object> args, Map<String, Object> base, boolean hasRows, PurchaseAnchorExecutionExtras out) {
        out.active = true;
        out.goodsSourceBreakdown = true;
        out.goodsAnchorSupplierBreakdown = false;
        out.queryMethod = "queryGbPurchaseGoodsAggByLegacyPurchaseMethod";
        out.targetGoodsName = str(args.get(ARG_PURCHASE_FOCUS_GOODS_NAME));
        out.targetGoodsId = toInt(args.get(ARG_PURCHASE_FOCUS_DIS_GOODS_ID));
        boolean idOk = out.targetGoodsId != null && out.targetGoodsId > 0;

        if (!hasRows) {
            out.noDataReason = "NO_PURCHASE_RECORD_FOR_SCOPE";
            if (!idOk) {
                out.goodsAnchorIdMissing = Boolean.TRUE;
            }
            return out;
        }
        if (!idOk) {
            out.noDataReason = "GOODS_ANCHOR_ID_MISSING";
            out.goodsAnchorIdMissing = Boolean.TRUE;
            return out;
        }

        Map<String, Object> executionBase = new HashMap<>(base);
        executionBase.remove("legacyPurchaseMethodFocus");
        executionBase.put("disGoodsId", out.targetGoodsId);

        List<PurchaseMethodLegacyAggRow> raw = nullToEmptyPurchaseMethodAgg(
                purchaseGoodsService.queryGbPurchaseGoodsAggByLegacyPurchaseMethod(executionBase));

        int selfLines = 0;
        int supLines = 0;
        int otherLines = 0;
        BigDecimal selfAmt = BigDecimal.ZERO;
        BigDecimal supAmt = BigDecimal.ZERO;
        BigDecimal otherAmt = BigDecimal.ZERO;
        BigDecimal selfQty = BigDecimal.ZERO;
        BigDecimal supQty = BigDecimal.ZERO;
        BigDecimal otherQty = BigDecimal.ZERO;
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (PurchaseMethodLegacyAggRow r : raw) {
            if (r == null) {
                continue;
            }
            String b = r.getMethodBucket();
            int lc = r.getLineCount() == null ? 0 : r.getLineCount();
            BigDecimal a = r.getLineSubtotal() == null ? BigDecimal.ZERO : r.getLineSubtotal();
            BigDecimal q = r.getLineQuantity() == null ? BigDecimal.ZERO : r.getLineQuantity();
            LinkedHashMap<String, Object> br = new LinkedHashMap<>();
            br.put("methodBucket", b);
            br.put("lineCount", lc);
            br.put("lineSubtotal", formatScaleOnePlain(a));
            br.put("lineQuantity", q.setScale(1, RoundingMode.HALF_UP).toPlainString());
            buckets.add(br);
            if ("supplier_channel".equals(b)) {
                supLines += lc;
                supAmt = supAmt.add(a);
                supQty = supQty.add(q);
            } else if ("self_strict".equals(b)) {
                selfLines += lc;
                selfAmt = selfAmt.add(a);
                selfQty = selfQty.add(q);
            } else {
                otherLines += lc;
                otherAmt = otherAmt.add(a);
                otherQty = otherQty.add(q);
            }
        }
        out.sourceBreakdownBuckets = buckets;

        int totalLines = selfLines + supLines + otherLines;
        if (totalLines == 0) {
            out.noDataReason = "NO_PURCHASE_LINES_FOR_FOCUSED_GOODS";
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("disGoodsId", out.targetGoodsId);
            if (!out.targetGoodsName.isEmpty()) {
                row.put("goodsName", out.targetGoodsName);
            }
            row.put("totalPurchaseAmount", formatScaleOnePlain(BigDecimal.ZERO));
            row.put("totalPurchaseQuantity", BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP).toPlainString());
            row.put("selfPurchaseAmount", formatScaleOnePlain(BigDecimal.ZERO));
            row.put("selfPurchaseQuantity", BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP).toPlainString());
            row.put("supplierPurchaseAmount", formatScaleOnePlain(BigDecimal.ZERO));
            row.put("supplierPurchaseQuantity", BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP).toPlainString());
            row.put("otherPurchaseAmount", formatScaleOnePlain(BigDecimal.ZERO));
            row.put("otherPurchaseQuantity", BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP).toPlainString());
            row.put("selfPurchaseLineCount", 0);
            row.put("supplierPurchaseLineCount", 0);
            row.put("otherPurchaseLineCount", 0);
            out.sourceBreakdownRow = row;
            return out;
        }

        BigDecimal totalAmt = selfAmt.add(supAmt).add(otherAmt);
        BigDecimal totalQty = selfQty.add(supQty).add(otherQty);
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("disGoodsId", out.targetGoodsId);
        if (!out.targetGoodsName.isEmpty()) {
            row.put("goodsName", out.targetGoodsName);
        }
        row.put("totalPurchaseAmount", formatScaleOnePlain(totalAmt));
        row.put("totalPurchaseQuantity", totalQty.setScale(1, RoundingMode.HALF_UP).toPlainString());
        row.put("selfPurchaseAmount", formatScaleOnePlain(selfAmt));
        row.put("selfPurchaseQuantity", selfQty.setScale(1, RoundingMode.HALF_UP).toPlainString());
        row.put("supplierPurchaseAmount", formatScaleOnePlain(supAmt));
        row.put("supplierPurchaseQuantity", supQty.setScale(1, RoundingMode.HALF_UP).toPlainString());
        row.put("otherPurchaseAmount", formatScaleOnePlain(otherAmt));
        row.put("otherPurchaseQuantity", otherQty.setScale(1, RoundingMode.HALF_UP).toPlainString());
        row.put("selfPurchaseLineCount", selfLines);
        row.put("supplierPurchaseLineCount", supLines);
        row.put("otherPurchaseLineCount", otherLines);
        out.sourceBreakdownRow = row;
        return out;
    }

    private static List<PurchaseMethodLegacyAggRow> nullToEmptyPurchaseMethodAgg(
            List<PurchaseMethodLegacyAggRow> list) {
        return list == null ? List.of() : list;
    }

    private static boolean isGoodsAnchorSourceBreakdownExecutionArgs(Map<String, Object> args) {
        if (args == null) {
            return false;
        }
        String et = str(args.get(ARG_PURCHASE_FOCUS_ENTITY_TYPE));
        if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(et)) {
            return false;
        }
        return "SOURCE_BREAKDOWN".equalsIgnoreCase(PurchaseSemanticExecutionArgs.readExecutionDetailWanted(args));
    }

    /** 渠道 overview 追问 GOODS_DETAIL，仅 executionDetailWanted（无 focus 实体）。 */
    private static boolean isChannelOverviewGoodsDetailExecutionArgs(Map<String, Object> args) {
        if (args == null) {
            return false;
        }
        if (!"GOODS_DETAIL".equalsIgnoreCase(PurchaseSemanticExecutionArgs.readExecutionDetailWanted(args))) {
            return false;
        }
        return !StringUtils.hasText(str(args.get(ARG_PURCHASE_FOCUS_ENTITY_TYPE)));
    }

    private PurchaseAnchorExecutionExtras resolveChannelOverviewGoodsDetailFromTopGoods(
            List<Map<String, Object>> goodsAmountTop,
            boolean hasRows,
            String purchaseSourceFocus,
            PurchaseAnchorExecutionExtras out) {
        out.active = true;
        out.goodsAnchorSupplierBreakdown = false;
        String pst = purchaseSourceFocus == null ? "" : purchaseSourceFocus.trim();
        boolean sup = AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(pst);
        out.queryMethod = sup ? "supplier_channel_goods_detail" : "self_channel_goods_detail";
        if (!hasRows || goodsAmountTop == null || goodsAmountTop.isEmpty()) {
            out.noDataReason = "NO_PURCHASE_RECORD_FOR_SCOPE";
            out.detailRows = List.of();
            return out;
        }
        List<Map<String, Object>> copy = new ArrayList<>(goodsAmountTop.size());
        for (Map<String, Object> r : goodsAmountTop) {
            copy.add(r != null ? new LinkedHashMap<>(r) : new LinkedHashMap<>());
        }
        out.detailRows = copy;
        return out;
    }

    private PurchaseAnchorExecutionExtras resolveSupplierAnchorGoodsLinesExecution(
            Map<String, Object> args,
            Map<String, Object> base,
            boolean hasRows,
            String purchaseSourceFocus,
            PurchaseAnchorExecutionExtras out) {
        out.active = true;
        out.goodsAnchorSupplierBreakdown = false;
        out.queryMethod = "queryGbPurchaseGoodsAggRowsForFocusedSupplier";
        Integer sid = toInt(args.get(ARG_PURCHASE_FOCUS_SUPPLIER_ID));
        out.focusSupplierId = sid;
        if (!hasRows) {
            out.noDataReason = "NO_SUPPLIER_PURCHASE_FOR_SCOPE";
            return out;
        }
        if (sid == null || sid <= 0) {
            out.noDataReason = "SUPPLIER_ID_MISSING_FOR_ANCHOR_EXECUTION";
            return out;
        }
        Map<String, Object> executionBase = new HashMap<>(base);
        executionBase.put("supplierId", sid);
        out.detailRows = new ArrayList<>(nullToEmptyMap(
                purchaseGoodsService.queryGbPurchaseGoodsAggRowsForFocusedSupplier(executionBase)));
        if (!out.detailRows.isEmpty()) {
            return out;
        }
        out.noDataReason = "NO_PURCHASE_LINES_FOR_FOCUSED_SUPPLIER";
        return out;
    }

    /**
     * 同一商品在自采口径下的轻量笔数探针（已有 SQL/Service，不切换采购来源）。
     * {@code disGoodsId} 缺失时不查库：{@code alternativeHasData=null}、{@code status=NOT_CHECKED}。
     */
    private void applySelfPurchaseAlternativeProbeForGoodsAnchorExecution(
            PurchaseAnchorExecutionExtras out, Map<String, Object> base, Integer disGoodsId) {
        if (disGoodsId == null || disGoodsId <= 0) {
            out.alternativeHasData = null;
            LinkedHashMap<String, Object> alt = new LinkedHashMap<>();
            alt.put("originalFacet", "SUPPLIER_PURCHASE");
            alt.put("alternativeFacet", "SELF_PURCHASE");
            alt.put("entityType", "GOODS");
            alt.put("entityName", out.targetGoodsName.isEmpty() ? null : out.targetGoodsName);
            alt.put("status", "NOT_CHECKED");
            out.alternativeEvidence = alt;
            return;
        }
        Map<String, Object> executionBase = new HashMap<>(base);
        executionBase.put("disGoodsId", disGoodsId);
        Map<String, Object> selfBase = new HashMap<>(executionBase);
        selfBase.put("legacyPurchaseMethodFocus", "self_strict");
        Integer selfCnt = purchaseGoodsService.queryGbPurchaseGoodsCount(selfBase);
        out.alternativeHasData = Boolean.valueOf(selfCnt != null && selfCnt > 0);
        LinkedHashMap<String, Object> alt = new LinkedHashMap<>();
        alt.put("originalFacet", "SUPPLIER_PURCHASE");
        alt.put("alternativeFacet", "SELF_PURCHASE");
        alt.put("entityType", "GOODS");
        alt.put("entityName", out.targetGoodsName.isEmpty() ? null : out.targetGoodsName);
        alt.put("status", Boolean.TRUE.equals(out.alternativeHasData) ? "HAS_DATA" : "NO_DATA");
        alt.put("rowCount", selfCnt == null ? 0 : selfCnt);
        out.alternativeEvidence = alt;
    }

    private static boolean isGoodsAnchorSupplierBreakdownExecutionArgs(Map<String, Object> args) {
        if (args == null) {
            return false;
        }
        String et = str(args.get(ARG_PURCHASE_FOCUS_ENTITY_TYPE));
        if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(et)) {
            return false;
        }
        String dw = PurchaseSemanticExecutionArgs.readExecutionDetailWanted(args);
        if (!"SUPPLIER_UNIT_PRICE".equalsIgnoreCase(dw)) {
            return false;
        }
        Integer id = toInt(args.get(ARG_PURCHASE_FOCUS_DIS_GOODS_ID));
        String nm = str(args.get(ARG_PURCHASE_FOCUS_GOODS_NAME));
        return (id != null && id > 0) || !nm.isEmpty();
    }

    /** 供货商排行锚 → 按商品聚合行（D-13.1）。 */
    private static boolean isSupplierAnchorGoodsLinesExecutionArgs(Map<String, Object> args) {
        if (args == null) {
            return false;
        }
        String et = str(args.get(ARG_PURCHASE_FOCUS_ENTITY_TYPE));
        if (!AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(et)) {
            return false;
        }
        String dw = PurchaseSemanticExecutionArgs.readExecutionDetailWanted(args);
        if (!"GOODS_UNIT_PRICE".equalsIgnoreCase(dw)) {
            return false;
        }
        Integer sid = toInt(args.get(ARG_PURCHASE_FOCUS_SUPPLIER_ID));
        return sid != null && sid > 0;
    }

    /** 写入商品锚供货商 execution payload（P4-E：仅 anchor execution key）。 */
    private static void putGoodsAnchorSupplierExecutionPayload(
            Map<String, Object> purchaseOverview, String goodsName, Integer goodsId) {
        purchaseOverview.put(PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE, Boolean.TRUE);
        purchaseOverview.put(PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME, goodsName);
        purchaseOverview.put(PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID, goodsId);
    }

    private static void putSupplierAnchorExecutionScopePayload(
            Map<String, Object> purchaseOverview,
            String timeWindow,
            Object purDepIds,
            String sourceFocus) {
        purchaseOverview.put(PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_TIME_WINDOW, timeWindow);
        purchaseOverview.put(PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_PUR_DEP_IDS, purDepIds);
        purchaseOverview.put(PAYLOAD_PURCHASE_SUPPLIER_ANCHOR_EXECUTION_SOURCE_FOCUS, sourceFocus);
    }

    private static final class PurchaseAnchorExecutionExtras {
        boolean active;
        /** Phase2-A：单商品 legacy 来源拆桶（与 {@link #resolveGoodsAnchorSourceBreakdownExecution} 对应）。 */
        boolean goodsSourceBreakdown;
        /** true：商品锚下各供应商行；false：供应商锚下各商品行。 */
        boolean goodsAnchorSupplierBreakdown = true;
        String queryMethod;
        Integer focusSupplierId;
        String targetGoodsName = "";
        Integer targetGoodsId;
        List<Map<String, Object>> detailRows = List.of();
        Map<String, Object> sourceBreakdownRow;
        List<Map<String, Object>> sourceBreakdownBuckets = List.of();
        String noDataReason;
        Boolean goodsAnchorIdMissing;
        /** null = 未做自采探针（{@code alternativeEvidence.status=NOT_CHECKED}） */
        Boolean alternativeHasData;
        Map<String, Object> alternativeEvidence;
    }

    private static final class PurchaseMethodSection {
        final boolean supported;
        final List<Map<String, Object>> breakdown;
        final String note;
        final String narrativeFragment;

        private PurchaseMethodSection(boolean supported, List<Map<String, Object>> breakdown, String note,
                String narrativeFragment) {
            this.supported = supported;
            this.breakdown = breakdown == null ? List.of() : breakdown;
            this.note = note;
            this.narrativeFragment = narrativeFragment;
        }

        static PurchaseMethodSection empty() {
            return new PurchaseMethodSection(false, List.of(), null, null);
        }
    }

    /**
     * 将旧版供货属性桶（{@link com.nongxinle.dto.PurchaseMethodLegacyAggRow}）汇总为「供货商采购 / 自采 / 其它方式」，
     * 并与总笔数、总金额 reconcile。
     */
    private static PurchaseMethodSection resolvePurchaseMethodSection(List<PurchaseMethodLegacyAggRow> rawRows, int rowCount,
            BigDecimal totalAmountBd) {
        if (rawRows == null || rawRows.isEmpty()) {
            if (rowCount > 0) {
                return new PurchaseMethodSection(false, List.of(), "未能读取采购方式分组数据，暂不拆分采购方式。", null);
            }
            return PurchaseMethodSection.empty();
        }
        int supplierLines = 0;
        int selfLines = 0;
        int otherLines = 0;
        BigDecimal supplierAmt = BigDecimal.ZERO;
        BigDecimal selfAmt = BigDecimal.ZERO;
        BigDecimal otherAmt = BigDecimal.ZERO;
        for (PurchaseMethodLegacyAggRow r : rawRows) {
            String b = r.getMethodBucket();
            int c = r.getLineCount() == null ? 0 : r.getLineCount();
            BigDecimal a = r.getLineSubtotal() == null ? BigDecimal.ZERO : r.getLineSubtotal();
            if ("supplier_channel".equals(b)) {
                supplierLines += c;
                supplierAmt = supplierAmt.add(a);
            } else if ("self_strict".equals(b)) {
                selfLines += c;
                selfAmt = selfAmt.add(a);
            } else {
                otherLines += c;
                otherAmt = otherAmt.add(a);
            }
        }
        int sumLines = supplierLines + selfLines + otherLines;
        BigDecimal sumAmt = supplierAmt.add(selfAmt).add(otherAmt);
        BigDecimal total = totalAmountBd == null ? BigDecimal.ZERO : totalAmountBd;
        boolean linesOk = sumLines == rowCount;
        boolean amtOk = sumAmt.subtract(total).abs().compareTo(PURCHASE_METHOD_RECON_TOLERANCE) <= 0;
        if (!linesOk || !amtOk) {
            return new PurchaseMethodSection(false, List.of(),
                    "当前按采购方式拆分后与总笔数或总金额未能完全对齐，暂不在答复中展示采购方式分项。", null);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> fragParts = new ArrayList<>();
        if (supplierLines > 0) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("label", "供货商采购");
            m.put("lineCount", supplierLines);
            m.put("amountYuan", formatScaleOnePlain(supplierAmt));
            rows.add(m);
            fragParts.add(String.format(Locale.CHINA, "供货商采购%d笔、金额%s元", supplierLines, formatScaleOnePlain(supplierAmt)));
        }
        if (selfLines > 0) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("label", "自采");
            m.put("lineCount", selfLines);
            m.put("amountYuan", formatScaleOnePlain(selfAmt));
            rows.add(m);
            fragParts.add(String.format(Locale.CHINA, "自采%d笔、金额%s元", selfLines, formatScaleOnePlain(selfAmt)));
        }
        if (otherLines > 0) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("label", "其它方式");
            m.put("lineCount", otherLines);
            m.put("amountYuan", formatScaleOnePlain(otherAmt));
            rows.add(m);
            fragParts.add(String.format(Locale.CHINA, "其它方式%d笔、金额%s元", otherLines, formatScaleOnePlain(otherAmt)));
        }
        String frag = fragParts.isEmpty() ? null : String.join("；", fragParts);
        return new PurchaseMethodSection(true, rows, null, frag);
    }

    private static String formatScaleOnePlain(BigDecimal v) {
        if (v == null) {
            return "0.0";
        }
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 集团采购：门店根须与 {@code orgScope.visibleStores} 一致。若工具入参已带 visibleStores（由 resolvedQueryContext
     * 下发），则<strong>只以其门店 id 为集团统计锚点</strong>，避免仅靠 {@code resolvedDepartmentIds}/请求锚点单列 id 时与子部门
     * 展开范围不一致，导致「总笔数/金额 Top」与「频次 Top」落入不同部门范围。
     * <p>无可见门店 id 时回退到 {@code resolvedStoreRoots}（与历史行为兼容）。
     */
    private static List<Integer> resolveGroupPurchaseStoreRoots(List<Integer> resolvedStoreRoots,
            List<Map<String, Object>> visibleStoresArg) {
        List<Integer> fromVisible = storeRootIdsFromVisibleStoresOnly(visibleStoresArg);
        if (!fromVisible.isEmpty()) {
            return dedupeIntPreserveOrder(fromVisible);
        }
        List<Integer> roots = new ArrayList<>();
        if (resolvedStoreRoots != null) {
            for (Integer id : resolvedStoreRoots) {
                if (id != null && id > 0) {
                    roots.add(id);
                }
            }
        }
        return dedupeIntPreserveOrder(roots);
    }

    private static List<Integer> storeRootIdsFromVisibleStoresOnly(List<Map<String, Object>> visibleStoresArg) {
        if (visibleStoresArg == null || visibleStoresArg.isEmpty()) {
            return List.of();
        }
        List<Integer> roots = new ArrayList<>();
        for (Map<String, Object> vs : visibleStoresArg) {
            if (vs == null) {
                continue;
            }
            Integer id = toInt(vs.get("storeDepartmentId"));
            if (id != null && id > 0) {
                roots.add(id);
            }
        }
        return roots;
    }

    private Map<Integer, Integer> buildPurDepartmentToStoreRootMap(List<Integer> storeRootsForPurchaseGroup) {
        Map<Long, List<Integer>> scopeByRoot =
                gbAiDailyRevenueService.buildStoreRevenueQueryScopeByStoreRoot(storeRootsForPurchaseGroup);
        Map<Integer, Integer> out = new HashMap<>(scopeByRoot.size() * 4);
        for (Map.Entry<Long, List<Integer>> e : scopeByRoot.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            int root = e.getKey().intValue();
            List<Integer> ids = e.getValue();
            if (ids == null) {
                continue;
            }
            for (Integer depId : ids) {
                if (depId != null && depId > 0) {
                    out.put(depId, root);
                }
            }
        }
        return out;
    }

    private static Map<Integer, BigDecimal> rollupPurchaseSubtotalByStoreRoot(
            List<DepartmentPurchaseAggRow> byDep,
            Map<Integer, Integer> purDepRowToStoreRoot,
            List<Integer> storeRootsForPurchaseGroup) {
        Map<Integer, BigDecimal> byRoot = new HashMap<>();
        Set<Integer> rootSet = new LinkedHashSet<>(storeRootsForPurchaseGroup);
        for (DepartmentPurchaseAggRow r : byDep) {
            if (r.getDepartmentId() == null || r.getPurchaseSubtotal() == null) {
                continue;
            }
            Integer depId = r.getDepartmentId();
            Integer root = purDepRowToStoreRoot.get(depId);
            if (root == null && rootSet.contains(depId)) {
                root = depId;
            }
            if (root == null) {
                continue;
            }
            byRoot.merge(root, r.getPurchaseSubtotal(), BigDecimal::add);
        }
        return byRoot;
    }

    private static BigDecimal resolvePurchaseSubtotalForVisibleStore(int storeRootId, boolean rollupToStoreRoots,
            Map<Integer, BigDecimal> amtByStoreRoot, Map<Integer, BigDecimal> amtByPurDep) {
        if (rollupToStoreRoots) {
            return amtByStoreRoot == null
                    ? BigDecimal.ZERO
                    : amtByStoreRoot.getOrDefault(storeRootId, BigDecimal.ZERO);
        }
        return amtByPurDep.getOrDefault(storeRootId, BigDecimal.ZERO);
    }

    private static String summarizeStorePurchaseRowsForLog(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "[]";
        }
        return rows.stream()
                .map(r -> {
                    Object name = r.get("storeName");
                    Object sub = r.get("purchaseSubtotal");
                    return (name == null ? "?" : name.toString().trim()) + "=" + sub;
                })
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static List<Integer> dedupeIntPreserveOrder(List<Integer> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        List<Integer> out = new ArrayList<>();
        for (Integer id : in) {
            if (id == null || id <= 0) {
                continue;
            }
            if (seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static List<String> buildRecommendations(boolean hasRows,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, LocalDate.now());
        if (!hasRows) {
            return List.of(p.getDisplayTimeRange() + "暂未查询到有效采购记录，请确认采购入库数据是否已录入。");
        }
        return List.of(
                "建议结合采购金额 Top 与价格波动项，核对验收单单价与供货商报价。",
                "对「暂无采购数据」的门店，可检查是否未完成入库或采购部门归集是否一致。");
    }

    private static List<Map<String, Object>> mapGoodsFrequencyTop(List<GbDistributerGoodsEntity> byTimes) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(byTimes.size(), 5);
        for (int i = 0; i < n; i++) {
            GbDistributerGoodsEntity g = byTimes.get(i);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("goodsName", g.getGbDgGoodsName());
            row.put("purchaseTimes", g.getGbDgQuantityDays());
            if (g.getGbDistributerGoodsId() != null) {
                row.put("disGoodsId", g.getGbDistributerGoodsId());
            }
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> mapGoodsAmountTop(List<GbDistributerGoodsEntity> bySubtotal) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(bySubtotal.size(), 5);
        for (int i = 0; i < n; i++) {
            GbDistributerGoodsEntity g = bySubtotal.get(i);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("goodsName", g.getGbDgGoodsName());
            row.put("purchaseSubtotal", g.getGoodsPurTotalSubtotal());
            if (g.getGbDistributerGoodsId() != null) {
                row.put("disGoodsId", g.getGbDistributerGoodsId());
            }
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> mapTopGoods(List<GbDistributerGoodsEntity> byTimes,
            List<GbDistributerGoodsEntity> bySubtotal) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(byTimes.size(), 5);
        for (int i = 0; i < n; i++) {
            GbDistributerGoodsEntity g = byTimes.get(i);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "by_times");
            row.put("goodsName", g.getGbDgGoodsName());
            row.put("purchaseTimes", g.getGbDgQuantityDays());
            if (g.getGbDistributerGoodsId() != null) {
                row.put("disGoodsId", g.getGbDistributerGoodsId());
            }
            out.add(row);
        }
        n = Math.min(bySubtotal.size(), 5);
        for (int i = 0; i < n; i++) {
            GbDistributerGoodsEntity g = bySubtotal.get(i);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "by_amount");
            row.put("goodsName", g.getGbDgGoodsName());
            row.put("purchaseSubtotal", g.getGoodsPurTotalSubtotal());
            if (g.getGbDistributerGoodsId() != null) {
                row.put("disGoodsId", g.getGbDistributerGoodsId());
            }
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> mapHighAmount(List<GbDistributerGoodsEntity> bySubtotal) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(bySubtotal.size(), 5);
        for (int i = 0; i < n; i++) {
            GbDistributerGoodsEntity g = bySubtotal.get(i);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("goodsName", g.getGbDgGoodsName());
            row.put("purchaseSubtotal", g.getGoodsPurTotalSubtotal());
            out.add(row);
        }
        return out;
    }

    private static PurchaseMethodSection applyPurchaseSourceFocus(PurchaseMethodSection sec, String focus,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, LocalDate.now());
        if (sec == null || !sec.supported || focus == null || focus.isBlank()
                || AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(focus)) {
            return sec;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(focus)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> m : sec.breakdown) {
                if (m != null && "自采".equals(String.valueOf(m.get("label")))) {
                    rows.add(m);
                }
            }
            String frag = buildMethodFragmentFromRows(rows);
            return new PurchaseMethodSection(!rows.isEmpty(), rows,
                    rows.isEmpty() ? p.getDisplayTimeRange() + "未识别到自采入库拆分" : null, frag);
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(focus)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> m : sec.breakdown) {
                if (m != null && "供货商采购".equals(String.valueOf(m.get("label")))) {
                    rows.add(m);
                }
            }
            String frag = buildMethodFragmentFromRows(rows);
            return new PurchaseMethodSection(!rows.isEmpty(), rows,
                    rows.isEmpty() ? p.getDisplayTimeRange() + "未识别到供货商采购拆分" : null, frag);
        }
        return sec;
    }

    private static String buildMethodFragmentFromRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            if (m == null) {
                continue;
            }
            Object lab = m.get("label");
            Object cnt = m.get("lineCount");
            Object amt = m.get("amountYuan");
            parts.add(String.format(Locale.CHINA, "%s%s笔、金额%s元",
                    lab == null ? "" : lab, cnt == null ? 0 : cnt, amt == null ? "0.0" : amt));
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private static List<Map<String, Object>> applyTopSuppliersFocus(List<Map<String, Object>> top, String focus) {
        if (top == null) {
            return List.of();
        }
        if (!AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(focus)) {
            return top;
        }
        return List.of();
    }

    /**
     * 排行与「供货商 Top」均只保留真实供货商：排除占位/自采 nx_supplier_id（null、{@code <=0}）及展示为自采或未维护占位的一行。
     */
    private static List<Map<String, Object>> filterRealSupplierSpendTopRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            if (m == null) {
                continue;
            }
            if (isRealSupplierSpendTopRow(m)) {
                out.add(m);
            }
        }
        return out;
    }

    private static boolean isRealSupplierSpendTopRow(Map<String, Object> row) {
        Integer id = toInt(row.get("supplierId"));
        if (id == null || id <= 0) {
            return false;
        }
        Object sn = row.get("supplierName");
        String name = sn == null ? "" : sn.toString().trim();
        if ("自采".equals(name)) {
            return false;
        }
        if (name.contains("供货商ID -1") || name.contains("供货商ID-1")) {
            return false;
        }
        return true;
    }

    private static List<Map<String, Object>> mapPriceChange(List<GbDistributerGoodsEntity> xs) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(xs.size(), 5);
        for (int i = 0; i < n; i++) {
            GbDistributerGoodsEntity g = xs.get(i);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("goodsName", g.getGbDgGoodsName());
            row.put("minPrice", g.getGbDgGoodsLowestPrice());
            row.put("maxPrice", g.getGbDgGoodsHighestPrice());
            row.put("priceFluctuationPercent", g.getGoodsPriceFluctuation());
            out.add(row);
        }
        return out;
    }

    private static List<GbDistributerGoodsEntity> nullToEmpty(List<GbDistributerGoodsEntity> list) {
        return list == null ? List.of() : list;
    }

    private static List<Map<String, Object>> nullToEmptyMap(List<Map<String, Object>> list) {
        return list == null ? List.of() : list;
    }

    /**
     * 与 Mapper {@code purGoodsWhereLegacyPurchaseMethodFocus} 一致：count/金额/Top/按店汇总与拆分同一过滤口径。
     */
    private static void applyLegacySourceFocusToQueryParams(Map<String, Object> base, String purchaseSourceFocus) {
        if (base == null || purchaseSourceFocus == null || purchaseSourceFocus.isBlank()
                || AiQuerySemanticLexicon.SOURCE_ALL.equalsIgnoreCase(purchaseSourceFocus)) {
            return;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseSourceFocus)) {
            base.put("legacyPurchaseMethodFocus", "self_strict");
        } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseSourceFocus)) {
            base.put("legacyPurchaseMethodFocus", "supplier_channel");
        }
    }

    /**
     * 集团采购：人话版门店覆盖（与经营概览 coveredStores / dataMissingStores 语义对齐）。
     */
    private static String buildStoreCoverageSummaryCn(boolean groupAgg,
            List<Map<String, Object>> visibleStoresArg,
            List<Map<String, Object>> coveredStores,
            List<Map<String, Object>> dataMissingStores,
            String purchaseSourceFocus,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, LocalDate.now());
        if (!groupAgg || visibleStoresArg == null || visibleStoresArg.isEmpty()) {
            return null;
        }
        String allHavePhrase;
        String nonePhrase;
        String haveSuffix;
        String missSuffix;
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(purchaseSourceFocus)) {
            allHavePhrase = "均有自采入库数据。";
            nonePhrase = p.getDisplayTimeRange() + "均无自采入库记录。";
            haveSuffix = " 有自采数据";
            missSuffix = "暂无自采记录";
        } else if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(purchaseSourceFocus)) {
            allHavePhrase = "均有供货商采购入库数据。";
            nonePhrase = p.getDisplayTimeRange() + "均无供货商采购入库记录。";
            haveSuffix = " 有供货商采购数据";
            missSuffix = "暂无供货商采购记录";
        } else {
            allHavePhrase = "均有采购入库数据。";
            nonePhrase = p.getDisplayTimeRange() + "均无采购入库记录。";
            haveSuffix = " 有采购数据";
            missSuffix = "暂无采购记录";
        }
        List<String> allNames = new ArrayList<>();
        for (Map<String, Object> vs : visibleStoresArg) {
            if (vs == null) {
                continue;
            }
            String nm = formatVisibleStoreName(vs);
            if (!nm.isBlank()) {
                allNames.add(nm);
            }
        }
        if (allNames.isEmpty()) {
            return null;
        }
        int n = allNames.size();
        String enumAll = String.join("、", allNames);
        int missingCount = dataMissingStores == null ? 0 : dataMissingStores.size();
        int coveredCount = coveredStores == null ? 0 : coveredStores.size();
        if (missingCount == 0) {
            return String.format(Locale.CHINA, "本次识别到 %d 家门店：%s，%s", n, enumAll, allHavePhrase);
        }
        if (coveredCount == 0) {
            return String.format(Locale.CHINA, "本次识别到 %d 家门店：%s。%s", n, enumAll, nonePhrase);
        }
        List<String> have = new ArrayList<>();
        if (coveredStores != null) {
            for (Map<String, Object> row : coveredStores) {
                if (row == null) {
                    continue;
                }
                String nm = storeRowDisplayName(row);
                if (!nm.isBlank()) {
                    have.add(nm);
                }
            }
        }
        List<String> miss = new ArrayList<>();
        if (dataMissingStores != null) {
            for (Map<String, Object> row : dataMissingStores) {
                if (row == null) {
                    continue;
                }
                String nm = storeRowDisplayName(row);
                if (!nm.isBlank()) {
                    miss.add(nm);
                }
            }
        }
        String havePhrase = joinHaveStoresCn(have, haveSuffix);
        String missPhrase = joinMissStoresCn(miss, missSuffix);
        if (havePhrase.isEmpty() || missPhrase.isEmpty()) {
            return String.format(Locale.CHINA, "本次识别到 %d 家门店：%s。", n, enumAll);
        }
        return String.format(Locale.CHINA, "本次识别到 %d 家门店：%s。其中 %s，%s。", n, enumAll, havePhrase, missPhrase);
    }

    private static String formatVisibleStoreName(Map<String, Object> vs) {
        Object sn = vs.get("storeName");
        if (sn != null && !sn.toString().isBlank()) {
            return sn.toString().trim();
        }
        Integer id = toInt(vs.get("storeDepartmentId"));
        return id != null && id > 0 ? "门店" + id : "";
    }

    private static String storeRowDisplayName(Map<String, Object> row) {
        Object sn = row.get("storeName");
        if (sn != null && !sn.toString().isBlank()) {
            return sn.toString().trim();
        }
        Integer id = toInt(row.get("storeDepartmentId"));
        return id != null && id > 0 ? "门店" + id : "";
    }

    private static String joinHaveStoresCn(List<String> names, String activitySuffix) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        return String.join("、", names) + activitySuffix;
    }

    private static String joinMissStoresCn(List<String> names, String missSuffix) {
        if (names == null || names.isEmpty()) {
            return "";
        }
        return String.join("、", names) + missSuffix;
    }

    private static List<Map<String, Object>> normalizeSupplierDisplayRows(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            if (m == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(m);
            Object sn = row.get("supplierName");
            String nameStr = sn == null ? "" : sn.toString().trim();
            if (nameStr.isEmpty() || looksLikeLegacyFakeSupplierLabel(nameStr)) {
                row.put("supplierName", formatSupplierFallbackName(row.get("supplierId")));
            }
            out.add(row);
        }
        return out;
    }

    private static boolean looksLikeLegacyFakeSupplierLabel(String s) {
        if (s == null || s.isBlank()) {
            return true;
        }
        return s.matches("^供货商-?\\d+$") || s.matches("^供货商\\d+$");
    }

    private static String formatSupplierFallbackName(Object supplierId) {
        if (supplierId == null) {
            return "未维护供货商名称";
        }
        String id = supplierId.toString().trim();
        if (id.isEmpty()) {
            return "未维护供货商名称";
        }
        if ("-1".equals(id)) {
            return "自采";
        }
        try {
            if (Integer.parseInt(id) == -1) {
                return "自采";
            }
        } catch (Exception ignore) {
            // fall through
        }
        return "供货商ID " + id + "（名称未维护）";
    }

    private static String formatDecimal(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static Long toLong(Object v) {
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

    private static Integer toInt(Object v) {
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

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static List<Integer> extractIntList(Object v) {
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> raw)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : raw) {
            Integer n = toInt(o);
            if (n != null && n > 0) {
                out.add(n);
            }
        }
        return out;
    }
}
