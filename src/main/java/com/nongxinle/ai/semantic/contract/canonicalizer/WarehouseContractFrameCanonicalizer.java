package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;

/** 仓储域：委托 {@link WarehouseSemanticCapabilityMatrix#canonicalizeWarehouseContractFrame}。 */
public final class WarehouseContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    public static final WarehouseContractFrameCanonicalizer INSTANCE =
            new WarehouseContractFrameCanonicalizer();

    private WarehouseContractFrameCanonicalizer() {}

    @Override
    public String domain() {
        return "WAREHOUSE";
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        return WarehouseSemanticCapabilityMatrix.canonicalizeWarehouseContractFrame(context.getParse());
    }
}
