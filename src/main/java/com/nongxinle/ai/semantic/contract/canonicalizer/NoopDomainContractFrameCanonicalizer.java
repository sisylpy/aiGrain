package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;

/** 无 Matrix contract frame 补全的 domain：原样返回 parse。 */
public final class NoopDomainContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    private final String domain;

    public NoopDomainContractFrameCanonicalizer(String domain) {
        this.domain = domain;
    }

    @Override
    public String domain() {
        return domain;
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        return context == null ? null : context.getParse();
    }
}
