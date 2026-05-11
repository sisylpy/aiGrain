package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AiMultiTurnTimeWindowPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);

    @Test
    void explicitInCurrentMessage_overridesPreviousTurn() {
        AiResolvedTimeWindow explicit = AiResolvedTimeWindow.tryParseExplicitFromUserMessage("这个月采购多少", TODAY);
        assertThat(explicit).isNotNull();
        var prev = AiConversationTurnMemory.builder()
                .lastStartDate("2026-04-01")
                .lastEndDate("2026-04-30")
                .lastTimeLabel(AiResolvedTimeWindow.LAST_MONTH)
                .build();
        AiResolvedTimeWindow out = AiMultiTurnTimeWindowPolicy.finalizeTimeWindow(null, explicit, prev, TODAY);
        assertThat(out.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.THIS_MONTH);
        assertThat(out.isExplicitTimeMentioned()).isTrue();
        assertThat(out.isInheritedFromPreviousTurn()).isFalse();
        assertThat(AiMultiTurnTimeWindowPolicy.resolveEffectiveTimeWindowSource(explicit, out))
                .isEqualTo("CURRENT_MESSAGE_EXPLICIT");
    }

    @Test
    void noExplicit_whenPreviousTurnHasDates_inheritsRegardlessOfPathChange() {
        var prev = AiConversationTurnMemory.builder()
                .lastStartDate("2026-04-01")
                .lastEndDate("2026-04-30")
                .lastTimeLabel(AiResolvedTimeWindow.LAST_MONTH)
                .build();
        AiResolvedTimeWindow out = AiMultiTurnTimeWindowPolicy.finalizeTimeWindow(null, null, prev, TODAY);
        assertThat(out.isInheritedFromPreviousTurn()).isTrue();
        assertThat(out.getStartDate().toString()).isEqualTo("2026-04-01");
        assertThat(out.getEndDate().toString()).isEqualTo("2026-04-30");
        assertThat(AiMultiTurnTimeWindowPolicy.resolveEffectiveTimeWindowSource(null, out))
                .isEqualTo("INHERITED_PREVIOUS");
    }

    @Test
    void noExplicit_noPreviousDates_fallsBackToMonthToDate() {
        AiResolvedTimeWindow out = AiMultiTurnTimeWindowPolicy.finalizeTimeWindow(null, null, null, TODAY);
        assertThat(out.getTimeLabel()).isEqualTo(AiResolvedTimeWindow.THIS_MONTH);
        assertThat(out.isInheritedFromPreviousTurn()).isFalse();
        assertThat(AiMultiTurnTimeWindowPolicy.resolveEffectiveTimeWindowSource(null, out))
                .isEqualTo("DEFAULT_MONTH_TO_DATE");
    }

    @Test
    void boundaryNote_inherited_isPresent() {
        var tw = AiMultiTurnTimeWindowPolicy.timeWindowFromPreviousTurn(
                AiConversationTurnMemory.builder()
                        .lastStartDate("2026-04-01")
                        .lastEndDate("2026-04-30")
                        .lastTimeLabel(AiResolvedTimeWindow.LAST_MONTH)
                        .build());
        String note = AiMultiTurnTimeWindowPolicy.buildAnswerBoundaryNote("INHERITED_PREVIOUS", tw, null);
        assertThat(note).contains("上个月");
        assertThat(note).contains("本句未指定新的统计时间");
    }
}
