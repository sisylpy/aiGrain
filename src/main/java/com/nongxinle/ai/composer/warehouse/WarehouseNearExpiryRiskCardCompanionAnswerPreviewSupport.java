package com.nongxinle.ai.composer.warehouse;

import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.inventory.WarehouseNearExpiryRiskFilterSupport;

import java.util.Map;

/**
 * 临期/过期风险卡：Composer 仅输出短导语，风险分层明细在 Card payload。
 */
public final class WarehouseNearExpiryRiskCardCompanionAnswerPreviewSupport {

    private WarehouseNearExpiryRiskCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(WarehouseAnswerPlan plan) {
        return plan != null
                && WarehouseAnswerPlan.TYPE_WAREHOUSE_NEAR_EXPIRY_RISK.equals(plan.getPlanType());
    }

    public static String composeCardCompanionHint(WarehouseAnswerPlan plan) {
        if (plan == null) {
            return "";
        }
        String scope = plan.getScopeLabel() == null ? "当前范围" : plan.getScopeLabel().trim();
        int count = plan.getFocusRows() == null ? 0 : plan.getFocusRows().size();
        Map<String, Object> summary = plan.getSummary();
        String filter =
                plan.getExpiryRiskFilter() != null
                        ? plan.getExpiryRiskFilter()
                        : summary != null && summary.get("expiryRiskFilter") != null
                                ? summary.get("expiryRiskFilter").toString()
                                : WarehouseNearExpiryRiskFilterSupport.FILTER_ALL_RISK;
        if (count <= 0 && summary != null && summary.get("emptyRiskList") != null) {
            return scope + emptyMessageForFilter(filter) + "；详情见下方卡片。";
        }
        String overview = overviewSuffix(summary, filter);
        return scope + focusMessageForFilter(filter, count) + overview + "；详情见下方卡片。";
    }

    private static String emptyMessageForFilter(String filter) {
        return switch (filter) {
            case WarehouseNearExpiryRiskFilterSupport.FILTER_NEAR_EXPIRY ->
                    "当前库存口径下未识别到临期批次";
            case WarehouseNearExpiryRiskFilterSupport.FILTER_EXPIRED ->
                    "当前库存口径下未识别到已过期批次";
            case WarehouseNearExpiryRiskFilterSupport.FILTER_DUE_TODAY ->
                    "当前库存口径下未识别到今天到期批次";
            default -> "当前库存口径下未识别到已过期、今日到期或临期的库存批次";
        };
    }

    private static String focusMessageForFilter(String filter, int count) {
        return switch (filter) {
            case WarehouseNearExpiryRiskFilterSupport.FILTER_NEAR_EXPIRY ->
                    "共 " + count + " 个库存批次处于临期窗口";
            case WarehouseNearExpiryRiskFilterSupport.FILTER_EXPIRED ->
                    "共 " + count + " 个库存批次已过期";
            case WarehouseNearExpiryRiskFilterSupport.FILTER_DUE_TODAY ->
                    "共 " + count + " 个库存批次今天到期";
            default -> "共 " + count + " 个库存批次存在过期或临期风险";
        };
    }

    private static String overviewSuffix(Map<String, Object> summary, String filter) {
        if (!WarehouseNearExpiryRiskFilterSupport.FILTER_NEAR_EXPIRY.equals(filter) || summary == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Object expired = summary.get("expiredOverviewCount");
        Object dueToday = summary.get("dueTodayOverviewCount");
        if (expired instanceof Number n && n.intValue() > 0) {
            sb.append("（另有 ").append(n.intValue()).append(" 个已过期");
        }
        if (dueToday instanceof Number n && n.intValue() > 0) {
            if (sb.isEmpty()) {
                sb.append("（另有 ");
            } else {
                sb.append("、");
            }
            sb.append(n.intValue()).append(" 个今天到期");
        }
        if (!sb.isEmpty()) {
            sb.append("）");
        }
        return sb.toString();
    }
}
