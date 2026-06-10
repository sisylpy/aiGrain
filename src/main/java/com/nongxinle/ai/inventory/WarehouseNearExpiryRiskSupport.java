package com.nongxinle.ai.inventory;

import com.nongxinle.entity.GbDepartmentGoodsStockEntity;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * 库房临期/过期风险：批次到期日推算与风险分层（集中配置，不读用户原文）。
 */
public final class WarehouseNearExpiryRiskSupport {

    /** 临期窗口：到期日在锚定日之后 N 天内（含）视为 near_expiry。 */
    public static final int DEFAULT_NEAR_EXPIRY_WINDOW_DAYS = 3;

    public static final String RISK_TIER_EXPIRED = "expired";
    public static final String RISK_TIER_DUE_TODAY = "due_today";
    public static final String RISK_TIER_NEAR_EXPIRY = "near_expiry";
    public static final String RISK_TIER_NORMAL = "normal";
    public static final String RISK_TIER_UNJUDGABLE = "unjudgable";

    public static final String EXPIRY_SOURCE_WASTE_FULL_TIME = "WASTE_FULL_TIME";
    public static final String EXPIRY_SOURCE_QUANTITY_DAYS = "QUANTITY_DAYS";

    private WarehouseNearExpiryRiskSupport() {}

    public record ExpiryResolution(
            LocalDate expiryDate,
            String expirySource,
            Integer quantityDaysUsed) {}

    /**
     * 推算批次有效到期日；无法判断时返回 null（如无 wasteFullTime 且 quantityDays 为空）。
     */
    public static ExpiryResolution resolveExpiry(GbDepartmentGoodsStockEntity batch) {
        if (batch == null) {
            return null;
        }
        LocalDate fromWaste = parseDateLoose(batch.getGbDgsWasteFullTime());
        if (fromWaste != null) {
            return new ExpiryResolution(fromWaste, EXPIRY_SOURCE_WASTE_FULL_TIME, null);
        }
        Integer qtyDays = batch.getGbDgQuantityDays();
        if (qtyDays == null || qtyDays <= 0) {
            return null;
        }
        LocalDate stockIn = parseDateLoose(batch.getGbDgsDate());
        if (stockIn == null) {
            return null;
        }
        return new ExpiryResolution(stockIn.plusDays(qtyDays), EXPIRY_SOURCE_QUANTITY_DAYS, qtyDays);
    }

    public static String classifyRiskTier(LocalDate expiryDate, LocalDate asOfDate, int nearExpiryWindowDays) {
        if (expiryDate == null || asOfDate == null) {
            return RISK_TIER_UNJUDGABLE;
        }
        if (expiryDate.isBefore(asOfDate)) {
            return RISK_TIER_EXPIRED;
        }
        if (expiryDate.isEqual(asOfDate)) {
            return RISK_TIER_DUE_TODAY;
        }
        long daysUntil = ChronoUnit.DAYS.between(asOfDate, expiryDate);
        if (daysUntil > 0 && daysUntil <= Math.max(0, nearExpiryWindowDays)) {
            return RISK_TIER_NEAR_EXPIRY;
        }
        return RISK_TIER_NORMAL;
    }

    public static boolean isActionableRiskTier(String tier) {
        return RISK_TIER_EXPIRED.equals(tier)
                || RISK_TIER_DUE_TODAY.equals(tier)
                || RISK_TIER_NEAR_EXPIRY.equals(tier);
    }

    public static long daysUntilExpiry(LocalDate expiryDate, LocalDate asOfDate) {
        if (expiryDate == null || asOfDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(asOfDate, expiryDate);
    }

    public static LocalDate parseAnchorDate(String raw) {
        LocalDate d = parseDateLoose(raw);
        return d == null ? LocalDate.now() : d;
    }

    public static int resolveNearExpiryWindowDays(Object arg) {
        if (arg instanceof Number n) {
            int v = n.intValue();
            return v >= 0 ? v : DEFAULT_NEAR_EXPIRY_WINDOW_DAYS;
        }
        if (arg != null && StringUtils.hasText(arg.toString())) {
            try {
                int v = Integer.parseInt(arg.toString().trim());
                return v >= 0 ? v : DEFAULT_NEAR_EXPIRY_WINDOW_DAYS;
            } catch (NumberFormatException ignore) {
                return DEFAULT_NEAR_EXPIRY_WINDOW_DAYS;
            }
        }
        return DEFAULT_NEAR_EXPIRY_WINDOW_DAYS;
    }

    static LocalDate parseDateLoose(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.length() >= 10) {
            try {
                return LocalDate.parse(s.substring(0, 10));
            } catch (DateTimeParseException ignore) {
                // fall through
            }
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static int tierSortKey(String tier) {
        if (tier == null) {
            return 99;
        }
        return switch (tier.toLowerCase(Locale.ROOT)) {
            case RISK_TIER_EXPIRED -> 0;
            case RISK_TIER_DUE_TODAY -> 1;
            case RISK_TIER_NEAR_EXPIRY -> 2;
            default -> 99;
        };
    }
}
