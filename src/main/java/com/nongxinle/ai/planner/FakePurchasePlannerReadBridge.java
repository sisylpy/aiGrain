package com.nongxinle.ai.planner;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness-only 假桥：验证 {@link PurchasePlannerReadRequest} → {@link PurchasePlannerReadResponse} 闭环。
 * <strong>非</strong>真实 {@link com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor} / DB。
 */
public final class FakePurchasePlannerReadBridge implements PurchasePlannerReadBridge {

    public static final String HARNESS_HONESTY_FAKE_READ_BRIDGE_OK = "FAKE_READ_BRIDGE_OK";

    private FakePurchasePlannerReadBridge() {
    }

    public static FakePurchasePlannerReadBridge instance() {
        return Holder.INSTANCE;
    }

    @Override
    public PurchasePlannerReadResponse readPurchase(PurchasePlannerReadRequest request) {
        String pst =
                request != null && request.getPurchaseSourceType() != null
                        && !request.getPurchaseSourceType().isBlank()
                        ? request.getPurchaseSourceType().trim()
                        : AiQuerySemanticLexicon.SOURCE_ALL;
        String timeLabel =
                request != null && request.getTimeLabel() != null && !request.getTimeLabel().isBlank()
                        ? request.getTimeLabel().trim()
                        : "harness_fake_purchase_time";
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPurchaseAmount", new BigDecimal("9876.54"));
        summary.put("purchaseOrderCount", 42L);
        summary.put("timeLabel", timeLabel);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("skuLabel", "Harness Fake Goods");
        row.put("totalPurchaseAmount", new BigDecimal("5000.00"));
        return PurchasePlannerReadResponse.builder()
                .status(PurchasePlannerReadStatus.OK)
                .purchaseAmount(new BigDecimal("9876.54"))
                .purchaseCount(42L)
                .purchaseSourceType(pst)
                .summary(summary)
                .focusRows(List.of(row))
                .secondaryRows(List.of())
                .errorCode(null)
                .errorMessage(null)
                .build();
    }

    private static final class Holder {
        private static final FakePurchasePlannerReadBridge INSTANCE = new FakePurchasePlannerReadBridge();
    }
}
