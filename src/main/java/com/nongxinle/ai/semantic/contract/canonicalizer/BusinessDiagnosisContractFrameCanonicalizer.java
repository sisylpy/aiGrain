package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrix;

/** 经营诊断域：委托 {@link BusinessDiagnosisSemanticCapabilityMatrix#canonicalizeBusinessDiagnosisContractFrame}。 */
public final class BusinessDiagnosisContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    public static final BusinessDiagnosisContractFrameCanonicalizer INSTANCE =
            new BusinessDiagnosisContractFrameCanonicalizer();

    private BusinessDiagnosisContractFrameCanonicalizer() {}

    @Override
    public String domain() {
        return "BUSINESS_DIAGNOSIS";
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        return BusinessDiagnosisSemanticCapabilityMatrix.canonicalizeBusinessDiagnosisContractFrame(
                context.getParse());
    }
}
