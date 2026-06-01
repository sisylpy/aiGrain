package com.nongxinle.ai.composer.menu;

/** @deprecated 请使用 {@link MenuOperationCardCompanionAnswerPreviewSupport}。 */
@Deprecated
public final class MenuExpertPresentationPlanAnswerPreviewSupport {

    private MenuExpertPresentationPlanAnswerPreviewSupport() {}

    public static String composeCardCompanionHint() {
        return MenuOperationCardCompanionAnswerPreviewSupport.composeActionRecommendationHint();
    }
}
