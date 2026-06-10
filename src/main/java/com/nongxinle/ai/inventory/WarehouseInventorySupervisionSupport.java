package com.nongxinle.ai.inventory;

/**
 * 库存监督分桶规则（确定性；不读用户原文）。
 */
public final class WarehouseInventorySupervisionSupport {

    public static final String PAYLOAD_KEY = "warehouseInventorySupervision";

    public static final String SECTION_URGENT_TODAY = "URGENT_PURCHASE_TODAY";
    public static final String SECTION_URGENT_TOMORROW = "URGENT_PURCHASE_TOMORROW";
    public static final String SECTION_SHORTAGE_2_3 = "SHORTAGE_2_3_DAYS";
    public static final String SECTION_SHORTAGE_WEEK = "SHORTAGE_WITHIN_WEEK";
    public static final String SECTION_HEALTHY = "HEALTHY_NO_ALERT";
    public static final String SECTION_OVERSTOCK = "OVERSTOCK_SLOW_MOVING";
    public static final String SECTION_EXPIRY = "EXPIRY_RISK";

    public static final String EXPIRY_SUB_EXPIRED = "expired";
    public static final String EXPIRY_SUB_DUE_TODAY = "due_today";
    public static final String EXPIRY_SUB_NEAR_EXPIRY = "near_expiry";

    /** 支撑天数超过该阈值视为积压/慢动销。 */
    public static final double OVERSTOCK_SUPPORT_DAYS_THRESHOLD = 45.0;

    public static final int SECTION_ROW_CAP = 8;
    public static final int DISH_LINK_ENRICH_CAP = 5;

    private WarehouseInventorySupervisionSupport() {}

    public static String classifySupportDaysBucket(Double supportDays) {
        if (supportDays == null || !Double.isFinite(supportDays)) {
            return null;
        }
        double d = supportDays;
        if (d < 1.0) {
            return SECTION_URGENT_TODAY;
        }
        if (d < 2.0) {
            return SECTION_URGENT_TOMORROW;
        }
        if (d <= 3.0) {
            return SECTION_SHORTAGE_2_3;
        }
        if (d <= 7.0) {
            return SECTION_SHORTAGE_WEEK;
        }
        if (d > OVERSTOCK_SUPPORT_DAYS_THRESHOLD) {
            return SECTION_OVERSTOCK;
        }
        return SECTION_HEALTHY;
    }

    public static int bucketSortKey(String sectionId) {
        if (sectionId == null) {
            return 99;
        }
        return switch (sectionId) {
            case SECTION_URGENT_TODAY -> 1;
            case SECTION_URGENT_TOMORROW -> 2;
            case SECTION_SHORTAGE_2_3 -> 3;
            case SECTION_SHORTAGE_WEEK -> 4;
            case SECTION_OVERSTOCK -> 5;
            case SECTION_HEALTHY -> 6;
            case SECTION_EXPIRY -> 7;
            default -> 99;
        };
    }

    public static String sectionTitle(String sectionId) {
        if (sectionId == null) {
            return "";
        }
        return switch (sectionId) {
            case SECTION_URGENT_TODAY -> "今天急需采购";
            case SECTION_URGENT_TOMORROW -> "明天急需采购";
            case SECTION_SHORTAGE_2_3 -> "2–3 天内可能缺货";
            case SECTION_SHORTAGE_WEEK -> "一周内需关注";
            case SECTION_HEALTHY -> "库存正常（暂无需提醒）";
            case SECTION_OVERSTOCK -> "库存积压 / 慢动销";
            case SECTION_EXPIRY -> "临期 / 过期 / 今日到期";
            default -> sectionId;
        };
    }

    public static String expirySubTitle(String tier) {
        if (tier == null) {
            return "";
        }
        return switch (tier) {
            case EXPIRY_SUB_EXPIRED -> "已过期";
            case EXPIRY_SUB_DUE_TODAY -> "今日到期";
            case EXPIRY_SUB_NEAR_EXPIRY -> "临期风险";
            default -> tier;
        };
    }
}
