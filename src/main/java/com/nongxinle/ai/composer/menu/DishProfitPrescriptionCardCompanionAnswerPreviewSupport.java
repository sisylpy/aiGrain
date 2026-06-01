package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import org.springframework.util.StringUtils;

/** 单菜利润处方卡已生成时，answerPreview 只保留一句短引导，避免与 card 重复。 */
public final class DishProfitPrescriptionCardCompanionAnswerPreviewSupport {

    private static final String DEFAULT_HINT =
            "已生成价格与配方诊断，下方卡片展示建议售价、成本差异和需要优先复核的配料。";

    private DishProfitPrescriptionCardCompanionAnswerPreviewSupport() {}

    public static boolean shouldUseShortPreview(DishProfitPrescriptionAnswerPlan plan) {
        return plan != null && DishProfitPrescriptionAnswerPlan.TYPE.equals(plan.getPlanType());
    }

    public static String composeCardCompanionHint(DishProfitPrescriptionAnswerPlan plan) {
        if (plan == null) {
            return DEFAULT_HINT;
        }
        if (StringUtils.hasText(plan.getDishName())) {
            return "已生成「"
                    + plan.getDishName().trim()
                    + "」价格与配方诊断，下方卡片展示建议售价、成本差异和需要优先复核的配料。";
        }
        return DEFAULT_HINT;
    }
}
