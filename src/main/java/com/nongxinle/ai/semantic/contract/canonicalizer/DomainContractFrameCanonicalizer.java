package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;

/**
 * 合同校验前按 domain 补齐 semanticSlots / wire（委托各域 Matrix；不读用户原文）。
 */
public interface DomainContractFrameCanonicalizer {

    /** 本 canonicalizer 负责的 domain 代码（与 {@link DomainContractFrameCanonicalizerRegistry} 键一致）。 */
    String domain();

    AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context);
}
