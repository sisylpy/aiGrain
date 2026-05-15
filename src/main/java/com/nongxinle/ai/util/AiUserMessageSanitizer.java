package com.nongxinle.ai.util;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户输入前缀清洗：列表编号、Harness 复现序号等，避免污染实体抽取与多轮扩写。
 */
public final class AiUserMessageSanitizer {

    private static final Pattern LEADING_ENUM =
            Pattern.compile("^\\s*(?:[（(]\\s*\\d{1,2}\\s*[）)]|\\d{1,2}\\s*[\\.．、])\\s*");

    private AiUserMessageSanitizer() {
    }

    /**
     * 去掉行首枚举前缀，可多重剥离（如「1. 2.  text」→「text」）。
     * 覆盖：{@code 1.}、{@code 2、}、{@code （1）} 等。
     */
    public static String stripLeadingEnumeration(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw == null ? "" : raw.trim();
        }
        String s = raw.trim();
        while (true) {
            Matcher m = LEADING_ENUM.matcher(s);
            if (!m.find()) {
                break;
            }
            s = s.substring(m.end()).trim();
            if (s.isEmpty()) {
                break;
            }
        }
        return s;
    }
}
