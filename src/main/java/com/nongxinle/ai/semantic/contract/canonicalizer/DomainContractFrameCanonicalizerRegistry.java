package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 按 selectedDomain 选择 {@link DomainContractFrameCanonicalizer}；未知 domain 原样返回 parse。
 */
public final class DomainContractFrameCanonicalizerRegistry {

    private static final Map<String, DomainContractFrameCanonicalizer> BY_DOMAIN = buildRegistry();

    private DomainContractFrameCanonicalizerRegistry() {}

    public static AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        AiQuerySemanticParseResult parse = context.getParse();
        if (parse == null || parse.isParseMissing()) {
            return parse;
        }
        String domain = normalizeDomain(context.getSelectedDomain());
        if (!StringUtils.hasText(domain)) {
            return parse;
        }
        DomainContractFrameCanonicalizer canonicalizer = BY_DOMAIN.get(domain);
        if (canonicalizer == null) {
            return parse;
        }
        return canonicalizer.canonicalize(context);
    }

    /** 注册表只读视图（Harness / debug）。 */
    public static Map<String, String> registeredDomainCanonicalizerIds() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, DomainContractFrameCanonicalizer> e : BY_DOMAIN.entrySet()) {
            out.put(e.getKey(), e.getValue().getClass().getSimpleName());
        }
        return Map.copyOf(out);
    }

    private static Map<String, DomainContractFrameCanonicalizer> buildRegistry() {
        Map<String, DomainContractFrameCanonicalizer> map = new LinkedHashMap<>();
        map.put("PURCHASE", PurchaseContractFrameCanonicalizer.INSTANCE);
        map.put("WAREHOUSE", WarehouseContractFrameCanonicalizer.INSTANCE);
        map.put("REVENUE", RevenueContractFrameCanonicalizer.INSTANCE);
        map.put("STOCK_REDUCE", StockReduceContractFrameCanonicalizer.INSTANCE);
        map.put("DISH_SALES", DishSalesContractFrameCanonicalizer.INSTANCE);
        map.put("DISH_PROFIT", DishProfitContractFrameCanonicalizer.INSTANCE);
        map.put("BUSINESS_DIAGNOSIS", BusinessDiagnosisContractFrameCanonicalizer.INSTANCE);
        return Map.copyOf(map);
    }

    private static String normalizeDomain(String domain) {
        return domain == null ? null : domain.trim().toUpperCase(Locale.ROOT);
    }
}
