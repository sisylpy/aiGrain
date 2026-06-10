package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.dto.PurchaseMethodLegacyAggRow;
import com.nongxinle.ai.identity.BusinessEntityIdentityBridge;
import com.nongxinle.ai.identity.BusinessEntityIdentityGoodsProjection;
import com.nongxinle.ai.identity.BusinessEntityIdentityHarnessDebugSupport;
import com.nongxinle.ai.identity.BusinessEntityIdentityResolver;
import com.nongxinle.ai.identity.BusinessEntityIdentityScopeSupport;
import com.nongxinle.ai.identity.EntityIdentityResolutionStatus;
import com.nongxinle.ai.identity.ResolvedEntityIdentity;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.tool.business.PurchaseOverviewTool;
import com.nongxinle.entity.GbDistributerGoodsEntity;
import com.nongxinle.service.GbDistributerGoodsService;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单原料采购经营分析事实层：复用采购 Mapper 口径与 {@link GoodsSupportedDishCoverDomainService}，
 * 不经过 {@link PurchaseOverviewTool#execute}，避免影响采购概览主链。
 */
@Service
@RequiredArgsConstructor
public class PurchaseGoodsBusinessAnalysisSupport {

    public static final String PAYLOAD_KEY = "purchaseGoodsBusinessAnalysis";

    private static final int UNIT_PRICE_LIMIT = 5;

    private final GbDistributerPurchaseGoodsService purchaseGoodsService;
    private final GbDistributerGoodsService gbDistributerGoodsService;
    private final PurchaseOverviewTool purchaseOverviewTool;
    private final GoodsSupportedDishCoverDomainService coverDomainService;
    private final BusinessEntityIdentityResolver entityIdentityResolver;

    public Map<String, Object> buildPayload(
            AiRunState state,
            AiResolvedQueryContext rq,
            Map<String, Object> toolArgs,
            LinkedHashMap<String, Object> debug) {
        return buildPayload(state, rq, toolArgs, debug, BusinessEntityIdentityScopeSupport.disIdFromToolArgs(toolArgs));
    }

    public Map<String, Object> buildPayload(
            AiRunState state,
            AiResolvedQueryContext rq,
            Map<String, Object> toolArgs,
            LinkedHashMap<String, Object> debug,
            Integer distributerIdHint) {
        BusinessEntityIdentityHarnessDebugSupport.appendPreResolveExecutionTrace(
                debug, rq, toolArgs, distributerIdHint);
        ResolvedEntityIdentity identity = entityIdentityResolver.resolveGoods(rq, distributerIdHint);
        BusinessEntityIdentityBridge.appendGoodsIdentityHarnessDebug(identity, debug);

        if (identity.getResolutionStatus() == EntityIdentityResolutionStatus.NEED_CLARIFICATION) {
            return failedPayload(
                    "goods_identity_ambiguous",
                    identity.getUserMentionedName(),
                    null,
                    debug,
                    identity.getClarificationMessage());
        }
        if (identity.getResolutionStatus() == EntityIdentityResolutionStatus.NOT_FOUND
                && identity.hasExplicitMention()) {
            return failedPayload(
                    "goods_identity_not_found",
                    identity.getUserMentionedName(),
                    null,
                    debug,
                    identity.getClarificationMessage());
        }

        Integer disGoodsId = BusinessEntityIdentityGoodsProjection.executionDisGoodsId(identity);
        String goodsName = BusinessEntityIdentityGoodsProjection.executionGoodsNameHint(identity);
        if (toolArgs != null) {
            if (disGoodsId == null || disGoodsId <= 0) {
                disGoodsId = parseIntLoose(toolArgs.get(AiBusinessToolIds.ARG_PURCHASE_FOCUS_DIS_GOODS_ID));
            }
            if (!StringUtils.hasText(goodsName)) {
                goodsName = str(toolArgs.get(AiBusinessToolIds.ARG_PURCHASE_FOCUS_GOODS_NAME));
            }
        }

        Map<String, Object> base = purchaseOverviewTool.buildPurchaseGoodsSqlQueryBase(toolArgs);
        if (base == null || base.isEmpty() || base.get("disId") == null) {
            return failedPayload("missing_purchase_scope", goodsName, disGoodsId, debug, null);
        }
        if (disGoodsId == null || disGoodsId <= 0) {
            if (identity.hasExplicitMention()) {
                return failedPayload(
                        "goods_identity_not_found",
                        identity.getUserMentionedName(),
                        null,
                        debug,
                        identity.getClarificationMessage());
            }
            disGoodsId =
                    resolveDisGoodsIdByName(
                            ((Number) base.get("disId")).intValue(), goodsName, debug);
        }
        if (disGoodsId == null || disGoodsId <= 0) {
            return failedPayload("goods_anchor_id_missing", goodsName, null, debug, null);
        }

        Map<String, Object> executionBase = new HashMap<>(base);
        executionBase.remove("legacyPurchaseMethodFocus");
        executionBase.put("disGoodsId", disGoodsId);

        LinkedHashMap<String, Object> sourceBreakdownRow = buildSourceBreakdownRow(executionBase, goodsName, disGoodsId);
        if (toolArgs != null) {
            String focus = str(toolArgs.get(AiBusinessToolIds.ARG_PURCHASE_SOURCE_FOCUS));
            if (StringUtils.hasText(focus)) {
                sourceBreakdownRow.put("requestedSourceFacet", focus.trim().toUpperCase());
            }
        }
        LinkedHashMap<String, Object> priceSection = buildPriceSection(base, executionBase, rq, state);
        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);
        String stockAsOf =
                InventoryPresentationTimeSupport.resolveCoverStockSnapshotAsOfDateIso(state, rq);
        Map<String, Object> coverCore =
                coverDomainService.buildPayload(
                        ((Number) base.get("disId")).intValue(),
                        toDepFatherId(toolArgs),
                        disGoodsId,
                        goodsName,
                        baseline,
                        stockAsOf,
                        debug);

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("status", "OK");
        out.put("disGoodsId", disGoodsId);
        out.put("goodsName", firstNonBlank(str(coverCore.get("goodsName")), goodsName));
        out.put("purchaseSourceBreakdown", sourceBreakdownRow);
        out.put("priceSection", priceSection);
        out.put("inventoryCover", coverCore);
        out.put("purchaseTimeLabel", resolvePurchaseTimeLabel(rq, state));
        out.put("inventorySnapshotLabel", InventoryPresentationTimeSupport.formatStockSnapshotLabel(stockAsOf));
        out.put("salesBaselineLabel", baseline.getDisplayLabel());
        if (debug != null) {
            debug.put("disGoodsId", disGoodsId);
            debug.put("stockAsOfDate", stockAsOf);
        }
        return out;
    }

    private LinkedHashMap<String, Object> buildSourceBreakdownRow(
            Map<String, Object> executionBase, String goodsName, int disGoodsId) {
        List<PurchaseMethodLegacyAggRow> raw =
                nullToEmptyPurchaseMethodAgg(
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

        for (PurchaseMethodLegacyAggRow r : raw) {
            if (r == null) {
                continue;
            }
            String b = r.getMethodBucket();
            int lc = r.getLineCount() == null ? 0 : r.getLineCount();
            BigDecimal a = r.getLineSubtotal() == null ? BigDecimal.ZERO : r.getLineSubtotal();
            BigDecimal q = r.getLineQuantity() == null ? BigDecimal.ZERO : r.getLineQuantity();
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

        BigDecimal totalAmt = selfAmt.add(supAmt).add(otherAmt);
        BigDecimal totalQty = selfQty.add(supQty).add(otherQty);
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("disGoodsId", disGoodsId);
        if (StringUtils.hasText(goodsName)) {
            row.put("goodsName", goodsName.trim());
        }
        row.put("totalPurchaseAmount", formatScaleOnePlain(totalAmt));
        row.put("totalPurchaseQuantity", formatQtyPlain(totalQty));
        row.put("selfPurchaseAmount", formatScaleOnePlain(selfAmt));
        row.put("selfPurchaseQuantity", formatQtyPlain(selfQty));
        row.put("supplierPurchaseAmount", formatScaleOnePlain(supAmt));
        row.put("supplierPurchaseQuantity", formatQtyPlain(supQty));
        row.put("otherPurchaseAmount", formatScaleOnePlain(otherAmt));
        row.put("otherPurchaseQuantity", formatQtyPlain(otherQty));
        row.put("selfPurchaseLineCount", selfLines);
        row.put("supplierPurchaseLineCount", supLines);
        row.put("otherPurchaseLineCount", otherLines);
        row.put("totalPurchaseLineCount", selfLines + supLines + otherLines);
        if (otherAmt.compareTo(BigDecimal.ZERO) > 0) {
            row.put(
                    "otherPurchaseBucketNote",
                    "purchase_type 非 1/5 的其余采购方式（legacy 桶 other，非自采/供货商渠道）");
        }
        return row;
    }

    private LinkedHashMap<String, Object> buildPriceSection(
            Map<String, Object> base,
            Map<String, Object> goodsScopedBase,
            AiResolvedQueryContext rq,
            AiRunState state) {
        LinkedHashMap<String, Object> section = new LinkedHashMap<>();
        String start = str(base.get("startDate"));
        String stop = str(base.get("stopDate"));
        if (!StringUtils.hasText(start) || !StringUtils.hasText(stop)) {
            section.put("priceCompareMode", "UNKNOWN");
            return section;
        }
        Map<String, Object> query = new HashMap<>(goodsScopedBase);
        query.put("limit", UNIT_PRICE_LIMIT);

        if (start.trim().equals(stop.trim())) {
            List<Map<String, Object>> rows =
                    filterFocusedGoods(
                            disGoodsIdFrom(goodsScopedBase),
                            nullToEmpty(
                                    purchaseGoodsService.queryGbPurchaseGoodsUnitPriceChangedVsPrevious(query)));
            section.put("priceCompareMode", "SINGLE_DAY_VS_PREVIOUS");
            section.put("priceChangeRows", mapPriceRows(rows));
            applyFocusedPriceSummary(section, rows);
            return section;
        }

        BusinessStatusCardComparePeriodSupport.ComparePeriod compare =
                BusinessStatusCardComparePeriodSupport.resolve(
                        rq != null ? rq.getTimeWindowLabel() : null, start.trim(), stop.trim());
        if (!StringUtils.hasText(compare.compareStartDate())
                || !StringUtils.hasText(compare.compareEndDate())) {
            section.put("priceCompareMode", "PERIOD_AVG_NO_COMPARE");
            return section;
        }
        query.put("compareStartDate", compare.compareStartDate().trim());
        query.put("compareStopDate", compare.compareEndDate().trim());
        List<Map<String, Object>> rows =
                filterFocusedGoods(
                        disGoodsIdFrom(goodsScopedBase),
                        nullToEmpty(
                                purchaseGoodsService.queryGbPurchaseGoodsUnitPriceChangedPeriodAvgVsCompare(
                                        query)));
        section.put("priceCompareMode", "PERIOD_AVG_VS_COMPARE");
        section.put("compareLabel", compare.compareLabel());
        section.put("priceChangeRows", mapPriceRows(rows));
        applyFocusedPriceSummary(section, rows);
        return section;
    }

    private static void applyFocusedPriceSummary(
            LinkedHashMap<String, Object> section, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            section.put("priceTrend", "UNKNOWN");
            return;
        }
        Map<String, Object> row = rows.get(0);
        Object pct = firstPresent(row, "priceChangePercent", "priceFluctuationPercent");
        section.put("currentAvgUnitPrice", row.get("currentAvgUnitPrice"));
        section.put("previousAvgUnitPrice", firstPresent(row, "previousAvgUnitPrice", "compareAvgUnitPrice"));
        section.put("priceChangePercent", pct);
        Double pctNum = parseDoubleLoose(pct);
        if (pctNum == null) {
            section.put("priceTrend", "UNKNOWN");
        } else if (pctNum > 5.0) {
            section.put("priceTrend", "UP");
        } else if (pctNum < -5.0) {
            section.put("priceTrend", "DOWN");
        } else {
            section.put("priceTrend", "FLAT");
        }
    }

    private static List<Map<String, Object>> mapPriceRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> src : rows) {
            if (src == null || src.isEmpty()) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("disGoodsId", src.get("disGoodsId"));
            row.put("goodsName", src.get("goodsName"));
            row.put("currentAvgUnitPrice", src.get("currentAvgUnitPrice"));
            row.put("previousAvgUnitPrice", firstPresent(src, "previousAvgUnitPrice", "compareAvgUnitPrice"));
            row.put("priceChangePercent", firstPresent(src, "priceChangePercent", "priceFluctuationPercent"));
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> filterFocusedGoods(Integer disGoodsId, List<Map<String, Object>> rows) {
        if (disGoodsId == null || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Integer id = parseIntLoose(row.get("disGoodsId"));
            if (id == null || disGoodsId.equals(id)) {
                filtered.add(row);
            }
        }
        return filtered.isEmpty() ? rows : filtered;
    }

    private static LinkedHashMap<String, Object> failedPayload(
            String reason,
            String goodsName,
            Integer disGoodsId,
            LinkedHashMap<String, Object> debug,
            String clarificationMessage) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("status", "FAILED");
        out.put("failureReason", reason);
        out.put("goodsName", goodsName);
        out.put("disGoodsId", disGoodsId);
        if (debug != null) {
            debug.put("failureReason", reason);
            if (StringUtils.hasText(clarificationMessage)) {
                debug.put("clarificationMessage", clarificationMessage.trim());
            }
        }
        return out;
    }

    private Integer resolveDisGoodsIdByName(
            int disId, String goodsNameHint, LinkedHashMap<String, Object> debug) {
        if (!StringUtils.hasText(goodsNameHint)) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("disId", disId);
        String hint = goodsNameHint.trim();
        if (containsHan(hint)) {
            map.put("searchStr", hint);
        } else {
            map.put("searchPinyin", hint);
        }
        List<GbDistributerGoodsEntity> hits = gbDistributerGoodsService.queryGbDisGoodsQuickSearchStr(map);
        if (hits == null || hits.isEmpty()) {
            if (debug != null) {
                debug.put("goodsSearchEmpty", hint);
            }
            return null;
        }
        LinkedHashMap<Integer, String> ids = new LinkedHashMap<>();
        for (GbDistributerGoodsEntity e : hits) {
            if (e != null && e.getGbDistributerGoodsId() != null) {
                ids.putIfAbsent(
                        e.getGbDistributerGoodsId(),
                        e.getGbDgGoodsName() == null ? "" : e.getGbDgGoodsName().trim());
            }
        }
        if (ids.size() == 1) {
            return ids.keySet().iterator().next();
        }
        if (debug != null) {
            debug.put("goodsSearchAmbiguous", new ArrayList<>(ids.keySet()));
        }
        return null;
    }

    private static boolean containsHan(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static String resolvePurchaseTimeLabel(AiResolvedQueryContext rq, AiRunState state) {
        if (rq != null && StringUtils.hasText(rq.getTimeWindowLabel())) {
            return rq.getTimeWindowLabel().trim();
        }
        if (state != null && StringUtils.hasText(state.getStatStartDate()) && StringUtils.hasText(state.getStatEndDate())) {
            return state.getStatStartDate().trim() + "至" + state.getStatEndDate().trim();
        }
        return null;
    }

    private static Integer toDepFatherId(Map<String, Object> toolArgs) {
        if (toolArgs == null) {
            return null;
        }
        Object o = toolArgs.get(com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID);
        if (o instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static Integer disGoodsIdFrom(Map<String, Object> base) {
        return parseIntLoose(base.get("disGoodsId"));
    }

    private static List<PurchaseMethodLegacyAggRow> nullToEmptyPurchaseMethodAgg(
            List<PurchaseMethodLegacyAggRow> list) {
        return list == null ? List.of() : list;
    }

    private static List<Map<String, Object>> nullToEmpty(List<Map<String, Object>> list) {
        return list == null ? List.of() : list;
    }

    private static String formatScaleOnePlain(BigDecimal v) {
        return v == null ? "0.0" : v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatQtyPlain(BigDecimal v) {
        return v == null
                ? "0.0"
                : v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && (!(v instanceof String s) || StringUtils.hasText(s))) {
                return v;
            }
        }
        return null;
    }

    private static Double parseDoubleLoose(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseIntLoose(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a.trim() : (StringUtils.hasText(b) ? b.trim() : null);
    }
}
