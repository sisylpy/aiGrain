package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.tool.business.PurchaseOverviewTool;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营采购卡：按 {@link BusinessStatusCardBuildRequest} 周期只读查库；
 * 单日 vs 上一笔入库价，多日 vs 对比期均价。
 */
final class PurchaseCheckCardFactBuilder {

    static final String MODE_SINGLE_DAY_VS_PREVIOUS = "SINGLE_DAY_VS_PREVIOUS_PURCHASE";
    static final String MODE_PERIOD_AVG_VS_COMPARE = "PERIOD_AVG_VS_COMPARE_PERIOD_AVG";

    private static final int UNIT_PRICE_CHANGED_LIMIT = 20;

    private PurchaseCheckCardFactBuilder() {}

    record FactResult(
            List<Map<String, Object>> unitPriceChangedItems,
            String priceCompareMode,
            String subtitle,
            Map<String, Object> purchaseSummary) {}

    static FactResult build(
            AiRunState state,
            BusinessStatusCardBuildRequest req,
            GbDistributerPurchaseGoodsService purchaseGoodsService,
            PurchaseOverviewTool purchaseOverviewTool,
            ToolDepartmentResolutionSupport departmentResolutionSupport) {
        if (state == null
                || req == null
                || purchaseGoodsService == null
                || purchaseOverviewTool == null
                || !StringUtils.hasText(req.getStartDate())
                || !StringUtils.hasText(req.getEndDate())) {
            boolean singleDay =
                    req != null
                            && StringUtils.hasText(req.getStartDate())
                            && StringUtils.hasText(req.getEndDate())
                            && req.getStartDate().trim().equals(req.getEndDate().trim());
            return empty(
                    singleDay
                            ? MODE_SINGLE_DAY_VS_PREVIOUS
                            : MODE_PERIOD_AVG_VS_COMPARE,
                    subtitleForMode(singleDay),
                    Map.of());
        }

        String start = req.getStartDate().trim();
        String end = req.getEndDate().trim();
        Long dept =
                departmentResolutionSupport != null
                        ? departmentResolutionSupport.resolveToolDepartmentFatherId(
                                state, state.getDepartmentId())
                        : state.getDepartmentId();

        Map<String, Object> toolArgs =
                PurchaseOverviewToolExecutor.buildPurchaseOverviewToolArgs(
                        dept, state.getDistributerId(), start, end, state);
        Map<String, Object> base = purchaseOverviewTool.buildPurchaseGoodsSqlQueryBase(toolArgs);
        if (base == null || base.isEmpty() || base.get("disId") == null) {
            return empty(MODE_SINGLE_DAY_VS_PREVIOUS, subtitleForMode(start.equals(end)), Map.of());
        }

        Map<String, Object> purchaseSummary = buildPurchaseSummary(base, purchaseGoodsService);

        Map<String, Object> query = new HashMap<>(base);
        query.put("limit", UNIT_PRICE_CHANGED_LIMIT);

        boolean singleDay = start.equals(end);
        if (singleDay) {
            List<Map<String, Object>> rows =
                    nullToEmpty(purchaseGoodsService.queryGbPurchaseGoodsUnitPriceChangedVsPrevious(query));
            return new FactResult(
                    mapRows(rows),
                    MODE_SINGLE_DAY_VS_PREVIOUS,
                    subtitleForMode(true),
                    purchaseSummary);
        }

        String compareStart = req.getCompareStartDate();
        String compareEnd = req.getCompareEndDate();
        if (!StringUtils.hasText(compareStart) || !StringUtils.hasText(compareEnd)) {
            return empty(MODE_PERIOD_AVG_VS_COMPARE, subtitleForMode(false), purchaseSummary);
        }
        query.put("compareStartDate", compareStart.trim());
        query.put("compareStopDate", compareEnd.trim());
        List<Map<String, Object>> rows =
                nullToEmpty(
                        purchaseGoodsService.queryGbPurchaseGoodsUnitPriceChangedPeriodAvgVsCompare(query));
        return new FactResult(
                mapRows(rows),
                MODE_PERIOD_AVG_VS_COMPARE,
                subtitleForMode(false),
                purchaseSummary);
    }

    private static FactResult empty(String mode, String subtitle, Map<String, Object> purchaseSummary) {
        return new FactResult(List.of(), mode, subtitle, purchaseSummary == null ? Map.of() : purchaseSummary);
    }

    /**
     * 采购卡基础统计：与 {@link PurchaseOverviewTool} 同源 scope；
     * 自采/订货拆分与 {@code GbDistributerPurchaseGoodsController#getGbPurGoodsStatisticsSeachDate} 一致
     * （{@code supplierBuy} + 订货 {@code batchDayuStatus}）。
     */
    static Map<String, Object> buildPurchaseSummary(
            Map<String, Object> base, GbDistributerPurchaseGoodsService purchaseGoodsService) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        if (base == null || base.isEmpty() || purchaseGoodsService == null) {
            summary.put("totalPurchaseAmount", 0.0);
            summary.put("selfPurchaseAmount", 0.0);
            summary.put("supplierPurchaseAmount", 0.0);
            return summary;
        }
        Double total = purchaseGoodsService.queryGbPurchaseGoodsBuySubtotalSum(base);
        BigDecimal totalBd =
                total == null ? BigDecimal.ZERO : BigDecimal.valueOf(total).setScale(1, RoundingMode.HALF_UP);

        Integer focusedSupplierBuy = toIntegerSupplierBuy(base.get("supplierBuy"));
        if (focusedSupplierBuy != null && focusedSupplierBuy == -1) {
            summary.put("totalPurchaseAmount", totalBd.doubleValue());
            summary.put("selfPurchaseAmount", totalBd.doubleValue());
            summary.put("supplierPurchaseAmount", 0.0);
            return summary;
        }
        if (focusedSupplierBuy != null && focusedSupplierBuy == 1) {
            summary.put("totalPurchaseAmount", totalBd.doubleValue());
            summary.put("selfPurchaseAmount", 0.0);
            summary.put("supplierPurchaseAmount", totalBd.doubleValue());
            return summary;
        }

        Map<String, Object> selfQuery = copyBaseForSupplierBuySplit(base);
        selfQuery.put("supplierBuy", -1);
        BigDecimal selfAmt = queryPurchaseSubtotalScaled(purchaseGoodsService, selfQuery);

        Map<String, Object> supplierQuery = copyBaseForSupplierBuySplit(base);
        supplierQuery.put("supplierBuy", 1);
        supplierQuery.put("batchDayuStatus", 2);
        BigDecimal supAmt = queryPurchaseSubtotalScaled(purchaseGoodsService, supplierQuery);

        summary.put("totalPurchaseAmount", totalBd.doubleValue());
        summary.put("selfPurchaseAmount", selfAmt.doubleValue());
        summary.put("supplierPurchaseAmount", supAmt.doubleValue());
        return summary;
    }

    private static Integer toIntegerSupplierBuy(Object supplierBuy) {
        if (supplierBuy == null) {
            return null;
        }
        if (supplierBuy instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(supplierBuy.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> copyBaseForSupplierBuySplit(Map<String, Object> base) {
        return PurchaseOverviewTool.copyBaseWithoutLegacyPurchaseMethodFocus(base);
    }

    private static BigDecimal queryPurchaseSubtotalScaled(
            GbDistributerPurchaseGoodsService purchaseGoodsService, Map<String, Object> query) {
        Integer count = purchaseGoodsService.queryGbPurchaseGoodsCount(query);
        if (count == null || count <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        Double subtotal = purchaseGoodsService.queryPurchaseGoodsSubTotal(query);
        if (subtotal == null) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(subtotal).setScale(1, RoundingMode.HALF_UP);
    }

    static boolean hasPurchaseSummaryData(Map<String, Object> purchaseSummary) {
        if (purchaseSummary == null || purchaseSummary.isEmpty()) {
            return false;
        }
        return parseDoubleLoose(purchaseSummary.get("totalPurchaseAmount")) > 0
                || parseDoubleLoose(purchaseSummary.get("selfPurchaseAmount")) > 0
                || parseDoubleLoose(purchaseSummary.get("supplierPurchaseAmount")) > 0;
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

    static String subtitleForMode(boolean singleDay) {
        return singleDay
                ? "周期内入库价相对上一笔采购价变化"
                : "本期平均采购单价相对对比期平均单价变化";
    }

    static String emptyReasonForMode(String priceCompareMode) {
        if (MODE_PERIOD_AVG_VS_COMPARE.equals(priceCompareMode)) {
            return "本期相对对比期无平均采购单价发生变化的商品";
        }
        return "本期无相对上一笔入库价发生变化的采购商品";
    }

    /** 采购卡专用对比展示标签（勿用四卡公共 {@code compareLabel} 解释单日入库价变化）。 */
    static String priceCompareLabel(String priceCompareMode, BusinessStatusCardBuildRequest req) {
        if (MODE_SINGLE_DAY_VS_PREVIOUS.equals(priceCompareMode)) {
            return "上一笔采购价";
        }
        if (req != null && StringUtils.hasText(req.getCompareLabel())) {
            return req.getCompareLabel().trim();
        }
        return "对比期";
    }

    /** 采购卡专用对比口径说明，供前端展示单价变化含义。 */
    static String priceCompareDescription(String priceCompareMode, BusinessStatusCardBuildRequest req) {
        if (MODE_SINGLE_DAY_VS_PREVIOUS.equals(priceCompareMode)) {
            return "本次入库价相对上一笔采购价变化";
        }
        String compareLabel = priceCompareLabel(priceCompareMode, req);
        return "本期平均采购单价相对" + compareLabel + "平均单价变化";
    }

    private static List<Map<String, Object>> mapRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> src : rows) {
            if (out.size() >= UNIT_PRICE_CHANGED_LIMIT) {
                break;
            }
            if (src == null || src.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("goodsName", firstNonBlank(src.get("goodsName"), src.get("disGoodsName")));
            item.put("standardName", src.get("standardName"));
            item.put("currentUnitPrice", firstPresent(src, "currentUnitPrice", "maxPrice"));
            item.put("previousUnitPrice", firstPresent(src, "previousUnitPrice", "minPrice"));
            item.put("priceChangePercent", firstPresent(src, "priceChangePercent", "priceFluctuationPercent"));
            if (item.get("goodsName") != null) {
                out.add(item);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> nullToEmpty(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (StringUtils.hasText(s)) {
                return s;
            }
        }
        return null;
    }
}
