package com.nongxinle.ai.semantic.intake;

import org.springframework.util.StringUtils;

import java.util.Set;

/** 一级业务域枚举常量；Java 仅校验 LLM 输出是否在允许集合内，不做关键词推断。 */
public final class SemanticIntakePrimaryDomain {

    public static final String REVENUE = "REVENUE";
    public static final String PURCHASE = "PURCHASE";
    public static final String STOCK_REDUCE = "STOCK_REDUCE";
    public static final String WAREHOUSE = "WAREHOUSE";
    public static final String DISH_SALES = "DISH_SALES";
    public static final String DISH_PROFIT = "DISH_PROFIT";
    public static final String DISH_COST = "DISH_COST";
    public static final String MENU_OPERATION = "MENU_OPERATION";
    public static final String BUSINESS_OVERVIEW = "BUSINESS_OVERVIEW";
    public static final String BUSINESS_DIAGNOSIS = "BUSINESS_DIAGNOSIS";
    public static final String MULTI_DOMAIN = "MULTI_DOMAIN";
    public static final String UNKNOWN = "UNKNOWN";

    private static final Set<String> ALL =
            Set.of(
                    REVENUE,
                    PURCHASE,
                    STOCK_REDUCE,
                    WAREHOUSE,
                    DISH_SALES,
                    DISH_PROFIT,
                    DISH_COST,
                    MENU_OPERATION,
                    BUSINESS_OVERVIEW,
                    BUSINESS_DIAGNOSIS,
                    MULTI_DOMAIN,
                    UNKNOWN);

    private static final Set<String> EXECUTABLE =
            Set.of(
                    REVENUE,
                    PURCHASE,
                    STOCK_REDUCE,
                    WAREHOUSE,
                    DISH_SALES,
                    DISH_PROFIT,
                    DISH_COST,
                    MENU_OPERATION,
                    BUSINESS_OVERVIEW,
                    BUSINESS_DIAGNOSIS);

    private SemanticIntakePrimaryDomain() {}

    public static boolean isKnown(String domain) {
        return StringUtils.hasText(domain) && ALL.contains(domain.trim().toUpperCase());
    }

    public static boolean isExecutable(String domain) {
        return StringUtils.hasText(domain) && EXECUTABLE.contains(domain.trim().toUpperCase());
    }

    public static String normalize(String domain) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        return domain.trim().toUpperCase();
    }
}
