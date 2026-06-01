package com.nongxinle.ai.advisor.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvisorSuggestedQuestionScopeSupportTest {

    @Test
    void advBossUsesBoPrefix() {
        assertEquals("bo_", AdvisorSuggestedQuestionScopeSupport.questionCodePrefixForAdvisor("ADV_BOSS").orElseThrow());
    }

    @Test
    void menuOperationUsesMoPrefix() {
        assertEquals("mo_", AdvisorSuggestedQuestionScopeSupport.questionCodePrefixForAdvisor("MENU_OPERATION").orElseThrow());
    }

    @Test
    void unconfiguredAdvisorHasNoPrefixScope() {
        assertTrue(AdvisorSuggestedQuestionScopeSupport.questionCodePrefixForAdvisor("ADV_PURCHASE").isEmpty());
    }
}
