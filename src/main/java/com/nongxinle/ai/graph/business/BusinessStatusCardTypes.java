package com.nongxinle.ai.graph.business;

import java.util.Set;

/**
 * 经营状态业务卡（营业额 / 采购 / 销货核对 / 订货）统一 cardType 常量。
 * 供 AI 对话「经营怎么样」组合问与单域问复用。
 */
public final class BusinessStatusCardTypes {

    public static final String REVENUE_REPORT_CARD = "REVENUE_REPORT_CARD";
    public static final String PURCHASE_CHECK_CARD = "PURCHASE_CHECK_CARD";
    public static final String STOCK_RECONCILE_CARD = "STOCK_RECONCILE_CARD";
    public static final String REORDER_REMINDER_CARD = "REORDER_REMINDER_CARD";

    private static final Set<String> ALL =
            Set.of(
                    REVENUE_REPORT_CARD,
                    PURCHASE_CHECK_CARD,
                    STOCK_RECONCILE_CARD,
                    REORDER_REMINDER_CARD);

    private BusinessStatusCardTypes() {}

    public static boolean isBusinessStatusCardType(String cardType) {
        if (cardType == null || cardType.isBlank()) {
            return false;
        }
        return ALL.contains(cardType.trim());
    }
}
