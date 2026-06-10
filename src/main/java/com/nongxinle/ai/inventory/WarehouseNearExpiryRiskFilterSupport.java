package com.nongxinle.ai.inventory;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * warehouse.near_expiry 风险子意图：由 Intake/V2 {@code expiryRiskFilter} 驱动，AnswerPlan 筛选 focusRows。
 */
public final class WarehouseNearExpiryRiskFilterSupport {

    /** 快临期/快到期：主列表 near_expiry。 */
    public static final String FILTER_NEAR_EXPIRY = "NEAR_EXPIRY";
    /** 已经过期：仅 expired。 */
    public static final String FILTER_EXPIRED = "EXPIRED";
    /** 今天到期：仅 due_today。 */
    public static final String FILTER_DUE_TODAY = "DUE_TODAY";
    /** 临期或过期风险：expired + due_today + near_expiry。 */
    public static final String FILTER_ALL_RISK = "ALL_RISK";

    private WarehouseNearExpiryRiskFilterSupport() {}

    public record FilterOutcome(
            List<Map<String, Object>> focusRows, Map<String, Object> summaryExtras) {}

    public static String normalizeFilter(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "NEAR_EXPIRY", "NEAR_EXPIRY_ONLY", "NEAR" -> FILTER_NEAR_EXPIRY;
            case "EXPIRED", "EXPIRED_ONLY", "ALREADY_EXPIRED" -> FILTER_EXPIRED;
            case "DUE_TODAY", "DUE_TODAY_ONLY", "TODAY" -> FILTER_DUE_TODAY;
            case "ALL_RISK", "ALL", "COMBINED", "EXPIRY_RISK", "NEAR_OR_EXPIRED" -> FILTER_ALL_RISK;
            default -> null;
        };
    }

    public static boolean isKnownFilter(String raw) {
        return normalizeFilter(raw) != null;
    }

    public static String resolveFilter(AiResolvedQueryContext rq) {
        if (rq == null) {
            return FILTER_ALL_RISK;
        }
        AiQuerySemanticParseResult sem = rq.getQuerySemanticParse();
        if (sem != null && sem.getSemanticSlots() != null) {
            String fromSlots = normalizeFilter(sem.getSemanticSlots().getExpiryRiskFilter());
            if (fromSlots != null) {
                return fromSlots;
            }
        }
        SemanticIntakeResult intake = rq.getSemanticIntake();
        if (intake != null) {
            String fromIntake = normalizeFilter(intake.getExpiryRiskFilter());
            if (fromIntake != null) {
                return fromIntake;
            }
            if (WarehouseInventoryShortageSemanticsSupport.SEMANTICS_NEAR_EXPIRY.equals(
                    WarehouseInventoryShortageSemanticsSupport.normalizeSemantics(
                            intake.getWarehouseInventorySemantics()))) {
                return FILTER_ALL_RISK;
            }
        }
        return FILTER_ALL_RISK;
    }

    public static FilterOutcome applyFilter(List<Map<String, Object>> allItems, String filterRaw) {
        String filter = normalizeFilter(filterRaw);
        if (filter == null) {
            filter = FILTER_ALL_RISK;
        }
        List<Map<String, Object>> source = allItems == null ? List.of() : allItems;
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("expiryRiskFilter", filter);

        List<Map<String, Object>> focus =
                switch (filter) {
                    case FILTER_NEAR_EXPIRY -> {
                        int expired = countTier(source, WarehouseNearExpiryRiskSupport.RISK_TIER_EXPIRED);
                        int dueToday = countTier(source, WarehouseNearExpiryRiskSupport.RISK_TIER_DUE_TODAY);
                        if (expired > 0) {
                            extras.put("expiredOverviewCount", expired);
                        }
                        if (dueToday > 0) {
                            extras.put("dueTodayOverviewCount", dueToday);
                        }
                        yield selectTier(source, WarehouseNearExpiryRiskSupport.RISK_TIER_NEAR_EXPIRY);
                    }
                    case FILTER_EXPIRED ->
                            selectTier(source, WarehouseNearExpiryRiskSupport.RISK_TIER_EXPIRED);
                    case FILTER_DUE_TODAY ->
                            selectTier(source, WarehouseNearExpiryRiskSupport.RISK_TIER_DUE_TODAY);
                    default -> selectActionable(source);
                };
        return new FilterOutcome(focus, extras);
    }

    private static List<Map<String, Object>> selectActionable(List<Map<String, Object>> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : items) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            String tier = str(row.get("riskTier"));
            if (WarehouseNearExpiryRiskSupport.isActionableRiskTier(tier)) {
                out.add(new LinkedHashMap<>(row));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> selectTier(List<Map<String, Object>> items, String tier) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : items) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            if (tier.equals(str(row.get("riskTier")))) {
                out.add(new LinkedHashMap<>(row));
            }
        }
        return out;
    }

    private static int countTier(List<Map<String, Object>> items, String tier) {
        int n = 0;
        for (Map<String, Object> row : items) {
            if (row != null && tier.equals(str(row.get("riskTier")))) {
                n++;
            }
        }
        return n;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
