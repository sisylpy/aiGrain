package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;

/** 采购域：P4-J2 有 selectedContractId 时仅轻量规范化；否则委托 Matrix（Historical）。 */
public final class PurchaseContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    public static final PurchaseContractFrameCanonicalizer INSTANCE =
            new PurchaseContractFrameCanonicalizer();

    private PurchaseContractFrameCanonicalizer() {}

    @Override
    public String domain() {
        return "PURCHASE";
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        return PurchaseSemanticCapabilityMatrix.canonicalizePurchaseContractFrame(
                context.getParse(), context.getPreviousTurn());
    }
}
