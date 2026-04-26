package com.nongxinle.utils;

import com.nongxinle.entity.GbDistributerFoodGoodsEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import static com.nongxinle.utils.GbTypeUtils.getGbDepartmentTypeMendian;

/**
 * 部门库存损耗报表 Mapper 查询参数与金额/数量解析（与 Controller / 报表 Service 共用）。
 */
public final class GbDepartmentGoodsStockReduceSupport {

    private GbDepartmentGoodsStockReduceSupport() {
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
