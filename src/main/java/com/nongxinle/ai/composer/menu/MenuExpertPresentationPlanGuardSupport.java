package com.nongxinle.ai.composer.menu;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Guard 共享的确定性校验辅助。 */
final class MenuExpertPresentationPlanGuardSupport {

    private MenuExpertPresentationPlanGuardSupport() {}

    static boolean isNegativeAmount(String amount) {
        if (!StringUtils.hasText(amount)) {
            return false;
        }
        String normalized = amount.trim().replace(",", "").replace("元", "");
        if (normalized.startsWith("-")) {
            return true;
        }
        try {
            return new BigDecimal(normalized).compareTo(BigDecimal.ZERO) < 0;
        } catch (NumberFormatException ignore) {
            return false;
        }
    }

    static boolean containsDelistHint(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String t = value.trim();
        return t.contains("下架") || t.contains("淘汰") || t.contains("观察") || t.contains("调整");
    }

    static boolean mentionsForcedDelist(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("建议下架")
                || text.contains("应该下架")
                || text.contains("直接下架")
                || text.contains("立即下架");
    }

    /** 提取 nextSteps / 正文里「」包裹的菜品名，用于语义守卫（非逐字匹配）。 */
    static List<String> extractQuotedDishNames(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("「([^」]+)」").matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (StringUtils.hasText(name)) {
                names.add(name.trim());
            }
        }
        return names;
    }
}
