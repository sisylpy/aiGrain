package com.nongxinle.ai.semantic.frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 采购域 CurrentSemanticFrame 校验结果（Phase 1）。 */
public record SemanticFrameValidationResult(
        boolean ok,
        boolean needSemanticClarification,
        String semanticClarificationQuestion,
        List<String> violationCodes) {

    public SemanticFrameValidationResult {
        violationCodes =
                violationCodes == null
                        ? List.of()
                        : Collections.unmodifiableList(new ArrayList<>(violationCodes));
    }

    /** 校验通过；勿命名为 ok()，会与记录组件 {@code ok} 的存取方法冲突。 */
    public static SemanticFrameValidationResult success() {
        return new SemanticFrameValidationResult(true, false, null, List.of());
    }

    /** 校验通过；{@code violationCodes} 仅作 debug/warning 观测，不阻断 semantic adoption。 */
    public static SemanticFrameValidationResult successWithWarnings(List<String> warningCodes) {
        return new SemanticFrameValidationResult(true, false, null, warningCodes == null ? List.of() : warningCodes);
    }

    public static SemanticFrameValidationResult clarify(String question, List<String> codes) {
        return new SemanticFrameValidationResult(false, true, question, codes == null ? List.of() : codes);
    }
}
