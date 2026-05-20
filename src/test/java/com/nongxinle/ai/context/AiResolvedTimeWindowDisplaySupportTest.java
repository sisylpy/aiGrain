package com.nongxinle.ai.context;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiResolvedTimeWindowDisplaySupportTest {

    @Test
    void boundaryNote_inherited_isPresent() {
        var tw =
                AiResolvedTimeWindow.builder()
                        .timeLabel(AiResolvedTimeWindow.LAST_MONTH)
                        .startDate(java.time.LocalDate.of(2026, 4, 1))
                        .endDate(java.time.LocalDate.of(2026, 4, 30))
                        .build();
        String note =
                AiResolvedTimeWindowDisplaySupport.buildAnswerBoundaryNote("INHERITED_PREVIOUS", tw, null);
        assertThat(note).contains("上个月");
        assertThat(note).contains("本句未指定新的统计时间");
    }

    @Test
    void humanReadableTimeCarryover_lastMonth() {
        var tw =
                AiResolvedTimeWindow.builder()
                        .timeLabel(AiResolvedTimeWindow.LAST_MONTH)
                        .startDate(java.time.LocalDate.of(2026, 4, 1))
                        .endDate(java.time.LocalDate.of(2026, 4, 30))
                        .build();
        assertThat(AiResolvedTimeWindowDisplaySupport.humanReadableTimeCarryover(tw)).isEqualTo("上个月");
    }

    @Test
    void combinedBoundaryNote_timeAndScopeInherited() {
        var tw =
                AiResolvedTimeWindow.builder()
                        .timeLabel(AiResolvedTimeWindow.LAST_MONTH)
                        .startDate(java.time.LocalDate.of(2026, 4, 1))
                        .endDate(java.time.LocalDate.of(2026, 4, 30))
                        .build();
        String note =
                AiResolvedTimeWindowDisplaySupport.buildCombinedBoundaryNote(
                        "INHERITED_PREVIOUS", "INHERITED_PREVIOUS", tw, null, null);
        assertThat(note).contains("上个月");
        assertThat(note).contains("本句未指定新的时间和门店");
    }
}
