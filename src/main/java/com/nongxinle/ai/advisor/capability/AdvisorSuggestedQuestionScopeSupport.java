package com.nongxinle.ai.advisor.capability;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 推荐问句的顾问可见范围：表 {@code gb_ai_workflow_suggested_question} 无 advisor 列，
 * 多顾问可绑定同一 workflow，故不能仅靠 advisor_workflow JOIN 隔离问句。
 *
 * <p>当前用种子约定的 {@code question_code} 域前缀（{@code bo_} / {@code mo_}）做顾问级过滤；
 * 未配置前缀的顾问不返回 questionTopics（避免泄露其它顾问问句）。
 */
public final class AdvisorSuggestedQuestionScopeSupport {

    private static final Map<String, String> ADVISOR_CODE_TO_QUESTION_PREFIX =
            Map.of(
                    "ADV_BOSS", "bo_",
                    "MENU_OPERATION", "mo_");

    private AdvisorSuggestedQuestionScopeSupport() {}

    /**
     * @param advisorCode {@code gb_ai_advisor.gb_ai_advisor_code}
     * @return 非空时表示仅返回 {@code question_code} 以该前缀开头的问句；空表示该顾问暂无推荐问句范围配置
     */
    public static Optional<String> questionCodePrefixForAdvisor(String advisorCode) {
        if (!StringUtils.hasText(advisorCode)) {
            return Optional.empty();
        }
        String prefix = ADVISOR_CODE_TO_QUESTION_PREFIX.get(advisorCode.trim().toUpperCase(Locale.ROOT));
        return StringUtils.hasText(prefix) ? Optional.of(prefix) : Optional.empty();
    }
}
