package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;

/** 菜品毛利域：委托 {@link DishProfitSemanticCapabilityMatrix#canonicalizeDishProfitContractFrame}。 */
public final class DishProfitContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    public static final DishProfitContractFrameCanonicalizer INSTANCE =
            new DishProfitContractFrameCanonicalizer();

    private DishProfitContractFrameCanonicalizer() {}

    @Override
    public String domain() {
        return "DISH_PROFIT";
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        return DishProfitSemanticCapabilityMatrix.canonicalizeDishProfitContractFrame(context.getParse());
    }
}
