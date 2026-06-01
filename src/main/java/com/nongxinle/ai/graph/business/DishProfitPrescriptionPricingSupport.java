package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 单菜处方卡建议售价：唯一确定性公式计算点。 */
public final class DishProfitPrescriptionPricingSupport {

    public static final String FORMULA_ID = DishProfitPrescriptionAnswerPlan.FORMULA_ACTUAL_COST123_DIV_TARGET;

    private DishProfitPrescriptionPricingSupport() {}

    public record SuggestedPriceResult(
            String targetGrossMarginRate,
            String costBasePerPortion,
            String suggestedPricePerPortion,
            String formulaId) {}

    /**
     * {@code suggestedPrice = actualCostPerPortion123 / (1 - target/100)}。
     *
     * @param requestedTarget 来自 semanticSlots（优先）
     * @param standardTarget  来自 insight 行 grossMarginStandardTarget
     */
    public static SuggestedPriceResult computeSuggestedPrice(
            BigDecimal actualCostPerPortion123, String requestedTarget, String standardTarget) {
        if (actualCostPerPortion123 == null || actualCostPerPortion123.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal target = resolveTargetRate(requestedTarget, standardTarget);
        if (target == null) {
            return null;
        }
        if (target.compareTo(BigDecimal.ZERO) <= 0 || target.compareTo(new BigDecimal("100")) >= 0) {
            return null;
        }
        BigDecimal divisor = BigDecimal.ONE.subtract(target.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        if (divisor.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal suggested = actualCostPerPortion123.divide(divisor, 2, RoundingMode.HALF_UP);
        return new SuggestedPriceResult(
                plain2(target),
                plain2(actualCostPerPortion123),
                plain2(suggested),
                FORMULA_ID);
    }

    private static BigDecimal resolveTargetRate(String requestedTarget, String standardTarget) {
        BigDecimal fromRequested = parseRatePercent(requestedTarget);
        if (fromRequested != null) {
            return fromRequested;
        }
        return parseRatePercent(standardTarget);
    }

    static BigDecimal parseRatePercent(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    static String plain2(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
