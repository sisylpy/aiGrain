package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 合同 strict 统一决策（P3：observe + enforce 共用）。
 * <p>写入 {@link com.nongxinle.ai.context.AiResolvedQueryContext} / Harness debug；
 * strict 开启且 {@link #isModelContractViolation()} 时阻断 adoption，不进入 Tool。
 */
@Value
@Builder
public class SemanticContractStrictDecision {

    /** 配置 {@code semantic.contract.strict.enabled} 快照。 */
    boolean strictEnabled;
    /** 是否存在合同层违例（与 strict 开关无关）。 */
    boolean modelContractViolation;
    /** {@code strictEnabled && modelContractViolation}；Harness 预览 enforce 行为。 */
    boolean enforceClarification;

    SemanticContractViolationCode violationCode;
    String violationReason;
    String selectedDomain;
    String unsupportedWire;
    @Singular("missingSlot")
    List<String> missingSlots;
    @Singular("candidateDomain")
    List<String> candidateDomains;
    @Singular("allowedWire")
    List<String> allowedWires;
    int allowedContractCount;
    String matchedContractId;
    /** {@link SemanticContractClarificationQuestionFactory} 生成；observe 模式亦写入便于预览。 */
    String clarificationQuestion;
    /**
     * 仍阻塞 strict enforce 的 blocker 登记 id（Catalog {@code STATUS_ACTIVE} only）。
     * contract entry 校验通过时应为空。
     */
    @Singular("activeStrictBlocker")
    List<String> activeStrictBlockers;
    /**
     * 已降级为 debug-only / known-debt 的 blocker 登记 id；Harness replay 观测用，不 enforce。
     */
    @Singular("deprecatedStrictBlocker")
    List<String> deprecatedStrictBlockers;
}
