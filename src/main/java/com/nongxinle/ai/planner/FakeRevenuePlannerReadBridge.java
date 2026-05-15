package com.nongxinle.ai.planner;

import java.math.BigDecimal;
import java.util.List;

/**
 * Harness-only 假桥：验证 {@link RevenuePlannerReadRequest} → {@link RevenuePlannerReadBridge} →
 * {@link RevenuePlannerReadResponse} 结构化闭环。<strong>非</strong>真实 SQL / Tool / 查库；返回金额为合成示例。
 *
 * @see com.nongxinle.ai.harness.replay.AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE
 */
public final class FakeRevenuePlannerReadBridge implements RevenuePlannerReadBridge {

    /** Replay 摘要 {@code plannerRevenueAdapterHonesty}；不得解读为生产库或真实 Tool 成功。 */
    public static final String HARNESS_HONESTY_FAKE_READ_BRIDGE_OK = "FAKE_READ_BRIDGE_OK";

    private FakeRevenuePlannerReadBridge() {
    }

    public static FakeRevenuePlannerReadBridge instance() {
        return Holder.INSTANCE;
    }

    @Override
    public RevenuePlannerReadResponse readRevenue(RevenuePlannerReadRequest request) {
        String timeLabel =
                request != null && request.getTimeLabel() != null && !request.getTimeLabel().isBlank()
                        ? request.getTimeLabel().trim()
                        : "harness_fake_time_label";
        String scopeLabel =
                request != null && request.getScopeType() != null && !request.getScopeType().isBlank()
                        ? "HARNESS_FAKE_" + request.getScopeType().trim()
                        : "HARNESS_FAKE_SCOPE";

        return RevenuePlannerReadResponse.builder()
                .status(RevenuePlannerReadStatus.OK)
                .revenueAmount(new BigDecimal("12345.67"))
                .storeRows(
                        List.of(
                                RevenuePlannerStoreRevenueRow.builder()
                                        .departmentId(7001L)
                                        .storeLabel("Harness Fake Store A")
                                        .amount(new BigDecimal("8000.00"))
                                        .build(),
                                RevenuePlannerStoreRevenueRow.builder()
                                        .departmentId(7002L)
                                        .storeLabel("Harness Fake Store B")
                                        .amount(new BigDecimal("4345.67"))
                                        .build()))
                .timeLabel(timeLabel)
                .scopeLabel(scopeLabel)
                .errorCode(null)
                .errorMessage(null)
                .build();
    }

    private static final class Holder {
        private static final FakeRevenuePlannerReadBridge INSTANCE = new FakeRevenuePlannerReadBridge();
    }
}
