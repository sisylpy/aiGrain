package com.nongxinle.ai.semantic.routing;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Set;

/** Step 1 路由 businessObject 计分：域专属词强、跨域泛词弱。 */
@UtilityClass
final class DomainRoutingBusinessObjectScorer {

    private static final double WEAK_TOKEN_FACTOR = 0.35;

    /** 跨域泛词：只能弱加分，不能压过「库存/出库/采购/营业额/销售/毛利」等明确域词。 */
    private static final Set<String> WEAK_TOKENS =
            Set.of("商品", "菜品", "异常", "问题", "金额", "最高");

    static double scoreToken(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        double base = Math.max(1.0, token.trim().length() / 2.0);
        if (isWeakToken(token)) {
            return base * WEAK_TOKEN_FACTOR;
        }
        return base;
    }

    static boolean isWeakToken(String token) {
        return token != null && WEAK_TOKENS.contains(token.trim());
    }

    static boolean matchedObjectsAllWeak(List<String> matchedObjects) {
        if (matchedObjects == null || matchedObjects.isEmpty()) {
            return false;
        }
        for (String token : matchedObjects) {
            if (!isWeakToken(token)) {
                return false;
            }
        }
        return true;
    }

    static boolean hasStrongMatchedObject(List<String> matchedObjects) {
        if (matchedObjects == null || matchedObjects.isEmpty()) {
            return false;
        }
        for (String token : matchedObjects) {
            if (!isWeakToken(token)) {
                return true;
            }
        }
        return false;
    }
}
