package com.nongxinle.ai.planner;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness-only 假桥：验证 {@link StockReducePlannerReadRequest} → {@link StockReducePlannerReadResponse} 闭环。
 * <strong>非</strong>真实 {@link com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor} / DB。
 */
public final class FakeStockReducePlannerReadBridge implements StockReducePlannerReadBridge {

    public static final String HARNESS_HONESTY_FAKE_READ_BRIDGE_OK = "FAKE_READ_BRIDGE_OK";

    private FakeStockReducePlannerReadBridge() {
    }

    public static FakeStockReducePlannerReadBridge instance() {
        return Holder.INSTANCE;
    }

    @Override
    public StockReducePlannerReadResponse readStockReduce(StockReducePlannerReadRequest request) {
        String reduceType =
                request != null && request.getReduceType() != null && !request.getReduceType().isBlank()
                        ? request.getReduceType().trim()
                        : StockReduceAnswerPlan.REDUCE_TYPE_ALL;
        String timeLabel =
                request != null && request.getTimeLabel() != null && !request.getTimeLabel().isBlank()
                        ? request.getTimeLabel().trim()
                        : "harness_fake_stock_reduce_time";
        String totalsBasis =
                request != null && request.getTotalsBasis() != null && !request.getTotalsBasis().isBlank()
                        ? request.getTotalsBasis().trim()
                        : "CALENDAR_NATURAL_DAY";
        Map<String, Object> summary = new LinkedHashMap<>();
        BigDecimal produce = new BigDecimal("1111.11");
        BigDecimal waste = new BigDecimal("2222.22");
        BigDecimal loss = new BigDecimal("3333.33");
        BigDecimal ret = new BigDecimal("4444.44");
        BigDecimal grand = produce.add(waste).add(loss).add(ret);
        summary.put("grandTotalFourTypes", grand);
        summary.put("produceTotal", produce);
        summary.put("wasteTotal", waste);
        summary.put("lossTotal", loss);
        summary.put("returnTotal", ret);
        summary.put("timeLabel", timeLabel);
        summary.put("reduceType", reduceType);
        summary.put(
                "structuredIntentDetail",
                request != null && request.getStructuredIntentDetail() != null
                                && !request.getStructuredIntentDetail().isBlank()
                        ? request.getStructuredIntentDetail().trim()
                        : AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("skuLabel", "Harness Fake Stock Reduce Row");
        row.put("subtotal", new BigDecimal("500.00"));
        return StockReducePlannerReadResponse.builder()
                .status(StockReducePlannerReadStatus.OK)
                .grandTotalAmount(grand)
                .produceTotal(produce)
                .wasteTotal(waste)
                .lossTotal(loss)
                .returnTotal(ret)
                .totalsBasis(totalsBasis)
                .summary(summary)
                .focusRows(List.of(row))
                .secondaryRows(List.of())
                .errorCode(null)
                .errorMessage(null)
                .build();
    }

    private static final class Holder {
        private static final FakeStockReducePlannerReadBridge INSTANCE = new FakeStockReducePlannerReadBridge();
    }
}
