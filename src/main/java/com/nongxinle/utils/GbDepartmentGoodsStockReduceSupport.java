package com.nongxinle.utils;

import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import com.nongxinle.entity.GbDepartmentGoodsStockReduceEntity;
import com.nongxinle.entity.GbDistributerFoodGoodsEntity;
import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.entity.NxJrdhSupplierEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;

/**
 * 部门库存损耗报表 Mapper 查询参数与金额/数量解析（与 Controller / 报表 Service 共用）。
 */
public final class GbDepartmentGoodsStockReduceSupport {

    /** {@link com.nongxinle.service.GbDepartmentGoodsStockReduceService#queryReduceAllTypesTotal} 等汇总 Map 的员工餐金额键。 */
    public static final String KEY_EMPLOYEE_MEAL_TOTAL = "employeeMealTotal";
    /** {@link com.nongxinle.service.GbDepartmentGoodsStockReduceService#queryReduceTypeWeightTotalsByScope} 的员工餐重量键。 */
    public static final String KEY_EMPLOYEE_MEAL_WEIGHT = "employeeMealWeight";

    private GbDepartmentGoodsStockReduceSupport() {
    }

    public static Map<String, Object> enrichAllTypesTotalWithEmployeeMeal(Map<String, Object> base, Double employeeMealTotal) {
        Map<String, Object> result = base != null ? new HashMap<>(base) : new HashMap<>();
        result.put(KEY_EMPLOYEE_MEAL_TOTAL, employeeMealTotal != null ? employeeMealTotal : 0.0);
        return result;
    }

    public static Map<String, Object> enrichTypeWeightTotalsWithEmployeeMeal(Map<String, Object> base, Double employeeMealWeight) {
        Map<String, Object> result = base != null ? new HashMap<>(base) : new HashMap<>();
        result.put(KEY_EMPLOYEE_MEAL_WEIGHT, employeeMealWeight != null ? employeeMealWeight : 0.0);
        return result;
    }

    /**
     * 小程序批次明细：按 {@code gbDgsrType} 回填 gbDgsrProduceWeight / gbDgsrEmployeeMealWeight 等展示字段。
     */
    public static void applyWxTypeAmountFields(GbDepartmentGoodsStockReduceEntity row) {
        if (row == null || row.getGbDgsrType() == null) {
            return;
        }
        String weight = row.getGbDgsrWeight() != null ? row.getGbDgsrWeight() : "0";
        String subtotal = row.getGbDgsrSubtotal() != null ? row.getGbDgsrSubtotal() : "0";
        Integer type = row.getGbDgsrType();
        if (Objects.equals(type, GbConstants.StockReduceType.PRODUCTION)) {
            row.setGbDgsrProduceWeight(weight);
            row.setGbDgsrProduceSubtotal(subtotal);
        } else if (Objects.equals(type, GbConstants.StockReduceType.WASTE)) {
            row.setGbDgsrWasteWeight(weight);
            row.setGbDgsrWasteSubtotal(subtotal);
        } else if (Objects.equals(type, GbConstants.StockReduceType.LOSS)) {
            row.setGbDgsrLossWeight(weight);
            row.setGbDgsrLossSubtotal(subtotal);
        } else if (Objects.equals(type, GbConstants.StockReduceType.RETURN)) {
            row.setGbDgsrReturnWeight(weight);
            row.setGbDgsrReturnSubtotal(subtotal);
        } else if (Objects.equals(type, GbConstants.StockReduceType.EMPLOYEE_MEAL)) {
            row.setGbDgsrEmployeeMealWeight(weight);
            row.setGbDgsrEmployeeMealSubtotal(subtotal);
        }
    }

    /**
     * 批次展示：回填每条 reduce 的类型展示字段，并汇总 type=6 到 {@code gbDgsEmployeeMealWeight} / {@code gbDgsEmployeeMealSubtotal}。
     */
    public static void enrichStockBatchWxDisplay(GbDepartmentGoodsStockEntity stock,
            List<GbDepartmentGoodsStockReduceEntity> reduces) {
        if (stock == null) {
            return;
        }
        BigDecimal employeeMealWeight = BigDecimal.ZERO;
        BigDecimal employeeMealSubtotal = BigDecimal.ZERO;
        if (reduces != null) {
            for (GbDepartmentGoodsStockReduceEntity row : reduces) {
                applyWxTypeAmountFields(row);
                if (Objects.equals(row.getGbDgsrType(), GbConstants.StockReduceType.EMPLOYEE_MEAL)) {
                    employeeMealWeight = employeeMealWeight.add(coerceDecimal(row.getGbDgsrWeight()));
                    employeeMealSubtotal = employeeMealSubtotal.add(coerceDecimal(row.getGbDgsrSubtotal()));
                }
            }
        }
        stock.setGbDgsEmployeeMealWeight(formatStoredWeight(employeeMealWeight));
        stock.setGbDgsEmployeeMealSubtotal(formatStoredSubtotal(employeeMealSubtotal));
    }

    private static String formatStoredWeight(BigDecimal value) {
        return value.setScale(GbConstants.StockLedger.WEIGHT_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatStoredSubtotal(BigDecimal value) {
        return value.setScale(GbConstants.StockLedger.SUBTOTAL_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    public static Map<String, Object> buildReduceParamsForGoodsDay(Integer disId, Integer disGoodsId,
            String startDate, String stopDate, int howManyDaysInPeriod, String searchDepId) {
        Map<String, Object> m = new HashMap<>();
        m.put("disId", disId);
        m.put("disGoodsId", disGoodsId);
        if (howManyDaysInPeriod > 0) {
            m.put("startDate", startDate);
            m.put("stopDate", stopDate);
        } else {
            m.put("date", startDate);
        }
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            m.put("depId", Integer.valueOf(searchDepId));
        } else {
            m.put("depType", getGbDepartmentTypeMendian());
        }
        return m;
    }

    /**
     * 采购分析按日曲线：从 mapDay 抽出与 {@code gb_department_goods_stock_reduce} 统计一致的参数。
     */
    public static Map<String, Object> buildReduceParamsFromFenxiMapDay(Map<String, Object> mapDay) {
        Map<String, Object> p = new HashMap<>();
        Object disId = mapDay.get("disId");
        if (disId != null) {
            p.put("disId", disId);
        }
        Object depId = mapDay.get("depId");
        if (depId != null) {
            p.put("depId", depId);
        }
        Object depType = mapDay.get("depType");
        if (depType != null) {
            p.put("depType", depType);
        }
        Object disGoodsId = mapDay.get("disGoodsId");
        if (disGoodsId != null) {
            p.put("disGoodsId", disGoodsId);
        }
        Object date = mapDay.get("date");
        if (date != null) {
            p.put("date", date);
        }
        Object startDate = mapDay.get("startDate");
        if (startDate != null) {
            p.put("startDate", startDate);
        }
        Object stopDate = mapDay.get("stopDate");
        if (stopDate != null) {
            p.put("stopDate", stopDate);
        }
        Object disGoodsGreatId = mapDay.get("disGoodsGreatId");
        if (disGoodsGreatId != null) {
            p.put("disGoodsGreatId", disGoodsGreatId);
        }
        return p;
    }

    public static Map<String, Object> withReduceType(Map<String, Object> base, Integer type) {
        Map<String, Object> p = new HashMap<>(base);
        p.remove("types");
        p.put("type", type);
        return p;
    }

    public static double nzD(Double d) {
        return d == null ? 0.0 : d;
    }

    public static BigDecimal parseGoodsAmountString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 将 JDBC / 表单 / 实体中的数量统一为 {@link BigDecimal}；避免对 {@link Double} 使用 {@link BigDecimal#valueOf(double)} 造成二进制误差。
     * 字符串会先去掉首尾空白，再把全角逗号、中文逗号规范为半角点再解析（不支持带千分位逗号的英美写法）。
     */
    /**
     * 将 0～1 的比例转为百分数数值字符串，固定两位小数（不含 {@code %} 后缀，如 {@code "45.23"} 表示 45.23%）。
     */
    public static String formatRatioAsPercentTwoDecimals(BigDecimal ratio0to1) {
        if (ratio0to1 == null) {
            return "0.00";
        }
        return ratio0to1.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static BigDecimal coerceDecimal(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
        }
        if (raw instanceof Number) {
            return new BigDecimal(((Number) raw).toString());
        }
        String s = raw.toString();
        if (s == null || (s = s.trim()).isEmpty()) {
            return BigDecimal.ZERO;
        }
        s = s.replace('\uFF0C', '.').replace('，', '.');
        return parseGoodsAmountString(s);
    }

    public static double toDouble(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).doubleValue();
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isActiveFoodGoodsLine(GbDistributerFoodGoodsEntity line) {
        return line.getGbDfgStatus() == null || line.getGbDfgStatus() != 0;
    }

    /**
     * 按库存批次时间顺序计算每条出库「当时」批次剩余量（出库前剩余 = 批次入库量 − 更早出库累计）。
     */
    public static Map<Integer, BigDecimal> computeRemainingBeforeOutboundByReduceId(
            Map<Integer, List<GbDepartmentGoodsStockReduceEntity>> reducesByStockId,
            Map<Integer, GbDepartmentGoodsStockEntity> stockById) {
        Map<Integer, BigDecimal> out = new HashMap<>();
        if (reducesByStockId == null || reducesByStockId.isEmpty()) {
            return out;
        }
        for (Map.Entry<Integer, List<GbDepartmentGoodsStockReduceEntity>> en : reducesByStockId.entrySet()) {
            Integer stockId = en.getKey();
            GbDepartmentGoodsStockEntity stock = stockById != null ? stockById.get(stockId) : null;
            BigDecimal batchInitial = stock != null
                    ? coerceDecimal(stock.getGbDgsWeight())
                    : BigDecimal.ZERO;
            List<GbDepartmentGoodsStockReduceEntity> ordered = new ArrayList<>(en.getValue());
            ordered.sort(compareReduceChronological());
            BigDecimal consumed = BigDecimal.ZERO;
            for (GbDepartmentGoodsStockReduceEntity row : ordered) {
                if (row == null || row.getGbDepartmentGoodsStockReduceId() == null) {
                    continue;
                }
                out.put(row.getGbDepartmentGoodsStockReduceId(), batchInitial.subtract(consumed));
                consumed = consumed.add(coerceDecimal(row.getGbDgsrWeight()));
            }
        }
        return out;
    }

    public static Comparator<GbDepartmentGoodsStockReduceEntity> compareReduceChronological() {
        return (a, b) -> {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            String ta = reduceSortTimeKey(a);
            String tb = reduceSortTimeKey(b);
            int cmp = ta.compareTo(tb);
            if (cmp != 0) {
                return cmp;
            }
            Integer ida = a.getGbDepartmentGoodsStockReduceId();
            Integer idb = b.getGbDepartmentGoodsStockReduceId();
            if (ida == null && idb == null) {
                return 0;
            }
            if (ida == null) {
                return 1;
            }
            if (idb == null) {
                return -1;
            }
            return ida.compareTo(idb);
        };
    }

    private static String reduceSortTimeKey(GbDepartmentGoodsStockReduceEntity row) {
        if (row.getGbDgsrFullTime() != null && !row.getGbDgsrFullTime().trim().isEmpty()) {
            return row.getGbDgsrFullTime().trim();
        }
        if (row.getGbDgsrDate() != null && !row.getGbDgsrDate().trim().isEmpty()) {
            return row.getGbDgsrDate().trim() + " 00:00:00";
        }
        return "";
    }

    /**
     * 构建出库明细上挂的采购批次摘要（采购日、采购员或供货商、采购总量、出库前剩余）。
     */
    public static Map<String, Object> buildPurchaseBatchInfo(GbDepartmentGoodsStockEntity stock,
            GbDistributerPurchaseGoodsEntity pur, BigDecimal remainingBeforeOutbound, String unit) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (stock != null && stock.getGbDepartmentGoodsStockId() != null) {
            info.put("stockBatchId", stock.getGbDepartmentGoodsStockId());
        }
        Integer purGoodsId = resolvePurGoodsId(stock, pur);
        if (purGoodsId != null) {
            info.put("purchaseGoodsId", purGoodsId);
        }
        info.put("purchaseDate", resolvePurchaseDate(stock, pur));
        Integer nxSupplierId = resolveNxSupplierId(stock, pur);
        boolean selfPurchase = nxSupplierId == null || Objects.equals(nxSupplierId, -1);
        info.put("sourceType", selfPurchase ? "SELF_PURCHASE" : "SUPPLIER");
        String purchaserName = resolvePurchaserName(stock, pur);
        String supplierName = resolveSupplierName(pur);
        info.put("purchaserName", purchaserName);
        info.put("supplierName", supplierName);
        info.put("sourceDisplayName", selfPurchase
                ? (purchaserName != null && !purchaserName.isEmpty() ? purchaserName : "自采")
                : (supplierName != null && !supplierName.isEmpty() ? supplierName : "供货商"));
        info.put("totalQuantity", formatDisplayQuantity(resolvePurchaseTotalQuantity(stock, pur)));
        info.put("unit", unit != null ? unit : "");
        info.put("remainingBeforeOutbound", formatDisplayQuantity(remainingBeforeOutbound));
        return info;
    }

    private static Integer resolvePurGoodsId(GbDepartmentGoodsStockEntity stock, GbDistributerPurchaseGoodsEntity pur) {
        if (pur != null && pur.getGbDistributerPurchaseGoodsId() != null) {
            return pur.getGbDistributerPurchaseGoodsId();
        }
        if (stock != null && stock.getGbDgsGbPurGoodsId() != null && stock.getGbDgsGbPurGoodsId() != -1) {
            return stock.getGbDgsGbPurGoodsId();
        }
        return null;
    }

    private static Integer resolveNxSupplierId(GbDepartmentGoodsStockEntity stock, GbDistributerPurchaseGoodsEntity pur) {
        if (pur != null && pur.getGbDpgPurchaseNxSupplierId() != null) {
            return pur.getGbDpgPurchaseNxSupplierId();
        }
        if (stock != null) {
            return stock.getGbDgsNxSupplierId();
        }
        return null;
    }

    public static String resolvePurchaseDate(GbDepartmentGoodsStockEntity stock, GbDistributerPurchaseGoodsEntity pur) {
        if (pur != null) {
            if (pur.getGbDpgPurchaseDate() != null && !pur.getGbDpgPurchaseDate().trim().isEmpty()) {
                return pur.getGbDpgPurchaseDate().trim();
            }
            if (pur.getGbDpgApplyDate() != null && !pur.getGbDpgApplyDate().trim().isEmpty()) {
                return pur.getGbDpgApplyDate().trim();
            }
            if (pur.getGbDpgTime() != null && !pur.getGbDpgTime().trim().isEmpty()) {
                String t = pur.getGbDpgTime().trim();
                return t.length() >= 10 ? t.substring(0, 10) : t;
            }
        }
        if (stock != null && stock.getGbDgsDate() != null && !stock.getGbDgsDate().trim().isEmpty()) {
            return stock.getGbDgsDate().trim();
        }
        return "";
    }

    public static BigDecimal resolvePurchaseTotalQuantity(GbDepartmentGoodsStockEntity stock,
            GbDistributerPurchaseGoodsEntity pur) {
        if (pur != null) {
            BigDecimal buyQty = coerceDecimal(pur.getGbDpgBuyQuantity());
            if (buyQty.compareTo(BigDecimal.ZERO) > 0) {
                return buyQty;
            }
            BigDecimal qty = coerceDecimal(pur.getGbDpgQuantity());
            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                return qty;
            }
        }
        if (stock != null) {
            return coerceDecimal(stock.getGbDgsWeight());
        }
        return BigDecimal.ZERO;
    }

    public static String resolvePurchaserName(GbDepartmentGoodsStockEntity stock, GbDistributerPurchaseGoodsEntity pur) {
        if (pur != null && pur.getPurchaseDepartmentUser() != null
                && pur.getPurchaseDepartmentUser().getGbDuWxNickName() != null
                && !pur.getPurchaseDepartmentUser().getGbDuWxNickName().trim().isEmpty()) {
            return pur.getPurchaseDepartmentUser().getGbDuWxNickName().trim();
        }
        if (stock != null && stock.getStockUserEntity() != null
                && stock.getStockUserEntity().getGbDuWxNickName() != null
                && !stock.getStockUserEntity().getGbDuWxNickName().trim().isEmpty()) {
            return stock.getStockUserEntity().getGbDuWxNickName().trim();
        }
        return "";
    }

    public static String resolveSupplierName(GbDistributerPurchaseGoodsEntity pur) {
        if (pur == null) {
            return "";
        }
        NxJrdhSupplierEntity sup = pur.getNxJrdhSupplierEntity();
        if (sup != null && sup.getNxJrdhsSupplierName() != null && !sup.getNxJrdhsSupplierName().trim().isEmpty()) {
            return sup.getNxJrdhsSupplierName().trim();
        }
        return "";
    }

    public static String formatDisplayQuantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.setScale(GbConstants.StockLedger.WEIGHT_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** 商品成本统计 / 分页查询共用的 Mapper 参数（含大类、部门或门店类型）。 */
    public static Map<String, Object> buildReduceCostQueryMap(String startDate, String stopDate, Integer disId,
            Integer greatId, String searchDepId) {
        Map<String, Object> map0 = new HashMap<>();
        map0.put("disId", disId);
        map0.put("startDate", startDate);
        map0.put("stopDate", stopDate);
        if (greatId != null && greatId != -1) {
            map0.put("disGoodsGreatId", greatId);
        }
        if (searchDepId != null && !"-1".equals(searchDepId)) {
            map0.put("depId", Integer.valueOf(searchDepId));
        } else {
            map0.put("depType", getGbDepartmentTypeMendian());
        }
        return map0;
    }
}
