package com.nongxinle.ai.composer.menu;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MenuExpertPresentationComposeResult {

    boolean accepted;
    MenuExpertPresentationPlan presentationPlan;
    String answerPreview;
}
