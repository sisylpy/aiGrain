package com.nongxinle.ai.composer;

import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import org.springframework.util.StringUtils;

/**
 * @deprecated 请用 {@link DishProfitRankingCardCompanionAnswerPreviewSupport}。
 */
@Deprecated
public final class DishProfitCostRankingCardCompanionAnswerPreviewSupport {

    private DishProfitCostRankingCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(DishProfitAnswerPlan plan) {
        return DishProfitRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan);
    }

    public static String composeCardCompanionHint(DishProfitAnswerPlan plan) {
        return DishProfitRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);
    }

    private static String blankToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
