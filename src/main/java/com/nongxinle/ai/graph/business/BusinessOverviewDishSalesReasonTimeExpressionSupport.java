package com.nongxinle.ai.graph.business;

import org.springframework.util.StringUtils;

/** 营业额卡菜品销量 Agent：输出时间表述与 reportLabel / 统计窗对齐（Java 不猜语义，只透传或做确定性格式化）。 */
final class BusinessOverviewDishSalesReasonTimeExpressionSupport {

    private BusinessOverviewDishSalesReasonTimeExpressionSupport() {}

    /**
     * LLM 输出必须使用的统计区间表述；优先 {@code reportLabel}，否则按起止日确定性降级。
     */
    static String resolveTimeExpression(String reportLabel, String startDate, String endDate) {
        if (StringUtils.hasText(reportLabel)) {
            return reportLabel.trim();
        }
        if (StringUtils.hasText(startDate) && StringUtils.hasText(endDate)) {
            String start = startDate.trim();
            String end = endDate.trim();
            if (start.equals(end)) {
                return start;
            }
            return "该时间段";
        }
        if (StringUtils.hasText(endDate)) {
            return endDate.trim();
        }
        return "该时间段";
    }
}
