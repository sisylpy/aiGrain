package com.nongxinle.ai.semantic.contract;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Strict 合同 enforce 开关（P3：默认 observe-only）。
 * <p>{@code semantic.contract.strict.enabled=false} 时只写 debug，不阻断 adoption / Tool。
 */
@Component
public class SemanticContractStrictProperties {

    @Value("${semantic.contract.strict.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }
}
