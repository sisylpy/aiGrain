package com.nongxinle.ai.graph.business.execution;

import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

/**
 * contract-locked 单菜执行统一 DISH anchor（Tool Request / AnswerPlan 共用）。
 */
@Value
@Builder
public class EffectiveDishAnchor {

    String dishName;
    Integer foodId;
    String source;

    public static EffectiveDishAnchor empty() {
        return EffectiveDishAnchor.builder().build();
    }

    public boolean hasSelector() {
        return StringUtils.hasText(dishName) || foodId != null;
    }
}
