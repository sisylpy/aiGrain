package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.RevenueSemanticCapabilityMatrix;

/** 营业额域：委托 {@link RevenueSemanticCapabilityMatrix#canonicalizeRevenueContractFrame}。 */
public final class RevenueContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    public static final RevenueContractFrameCanonicalizer INSTANCE =
            new RevenueContractFrameCanonicalizer();

    private RevenueContractFrameCanonicalizer() {}

    @Override
    public String domain() {
        return "REVENUE";
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        return RevenueSemanticCapabilityMatrix.canonicalizeRevenueContractFrame(context.getParse());
    }
}
