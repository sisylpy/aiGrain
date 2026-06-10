package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticIntakeInput {

    private String rawUserMessage;
    private String normalizedUserMessage;
    private LocalDate today;
    private boolean hasPreviousTurn;

    private String previousIntentCode;
    private String previousPathCode;
    private String previousStructuredIntentDetail;
    private String previousStartDate;
    private String previousEndDate;
    private String previousTimeLabel;
    private String previousScopeType;
    private String previousMentionedStoreName;
    private String previousMentionedDishName;
    private AiQuerySemanticParseResult.SemanticSlotsPart previousSemanticSlots;
    private List<AiResultAnchor> resultAnchors;
    private List<String> visibleStoreNames;
    private String previousEffectiveQuestion;
    private String previousAnswerSummary;
    /** 当前商户 distributerId，供 cover days 实体存在性探测。 */
    private Long distributerId;

    public static SemanticIntakeInput from(
            String rawUserMessage,
            String normalizedUserMessage,
            LocalDate today,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope orgScope) {
        List<String> visible = new ArrayList<>();
        if (orgScope != null && orgScope.getVisibleStores() != null) {
            for (AiStoreScopeDTO s : orgScope.getVisibleStores()) {
                if (s != null && StringUtils.hasText(s.getStoreName())) {
                    visible.add(s.getStoreName().trim());
                }
            }
        }
        Long distributerId =
                orgScope != null && orgScope.getDistributerId() != null
                        ? orgScope.getDistributerId()
                        : null;
        if (previousTurn == null) {
            return SemanticIntakeInput.builder()
                    .rawUserMessage(rawUserMessage)
                    .normalizedUserMessage(normalizedUserMessage)
                    .today(today)
                    .hasPreviousTurn(false)
                    .visibleStoreNames(visible.isEmpty() ? null : visible)
                    .distributerId(distributerId)
                    .build();
        }
        String mentionedStore = trim(previousTurn.getLastMentionedStore());
        if (mentionedStore == null) {
            mentionedStore = trim(previousTurn.getLastFocusedStoreName());
        }
        return SemanticIntakeInput.builder()
                .rawUserMessage(rawUserMessage)
                .normalizedUserMessage(normalizedUserMessage)
                .today(today)
                .hasPreviousTurn(true)
                .previousIntentCode(trim(previousTurn.getLastIntentCode()))
                .previousPathCode(trim(previousTurn.getLastPathCode()))
                .previousStructuredIntentDetail(trim(previousTurn.getLastStructuredIntentDetail()))
                .previousStartDate(trim(previousTurn.getLastStartDate()))
                .previousEndDate(trim(previousTurn.getLastEndDate()))
                .previousTimeLabel(trim(previousTurn.getLastTimeLabel()))
                .previousScopeType(trim(previousTurn.getLastScopeType()))
                .previousMentionedStoreName(mentionedStore)
                .previousMentionedDishName(trim(previousTurn.getLastMentionedDishName()))
                .previousEffectiveQuestion(trim(previousTurn.getLastEffectiveQuestion()))
                .previousAnswerSummary(trim(previousTurn.getLastAnswerSummary()))
                .previousSemanticSlots(previousTurn.getLastSemanticSlots())
                .resultAnchors(copyAnchors(previousTurn.getLastResultAnchors()))
                .visibleStoreNames(visible.isEmpty() ? null : visible)
                .distributerId(distributerId)
                .build();
    }

    private static List<AiResultAnchor> copyAnchors(List<AiResultAnchor> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        return new ArrayList<>(in);
    }

    private static String trim(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
