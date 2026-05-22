package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrix;

/** 菜品销量域：委托 {@link DishSalesSemanticCapabilityMatrix#canonicalizeDishSalesContractFrame}。 */
public final class DishSalesContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    public static final DishSalesContractFrameCanonicalizer INSTANCE =
            new DishSalesContractFrameCanonicalizer();

    private DishSalesContractFrameCanonicalizer() {}

    @Override
    public String domain() {
        return "DISH_SALES";
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        return DishSalesSemanticCapabilityMatrix.canonicalizeDishSalesContractFrame(context.getParse());
    }
}
