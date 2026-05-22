package com.nongxinle.ai.semantic.contract.canonicalizer;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrix;

/** 出库域：委托 {@link StockReduceSemanticCapabilityMatrix#canonicalizeStockReduceContractFrame}。 */
public final class StockReduceContractFrameCanonicalizer implements DomainContractFrameCanonicalizer {

    public static final StockReduceContractFrameCanonicalizer INSTANCE =
            new StockReduceContractFrameCanonicalizer();

    private StockReduceContractFrameCanonicalizer() {}

    @Override
    public String domain() {
        return "STOCK_REDUCE";
    }

    @Override
    public AiQuerySemanticParseResult canonicalize(DomainContractFrameCanonicalizeContext context) {
        if (context == null) {
            return null;
        }
        return StockReduceSemanticCapabilityMatrix.canonicalizeStockReduceContractFrame(context.getParse());
    }
}
