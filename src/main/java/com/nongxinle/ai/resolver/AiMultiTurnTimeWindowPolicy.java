package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * 多轮会话时间窗公共策略：显式时间优先；本句无时间词且上轮有统计起止日时继承；否则回落到 FollowUp 合并窗或本月至今。
 * 供经营 / 采购 / 库存 / 菜品毛利等路径共用，避免各 Agent 各自实现。
 */
public final class AiMultiTurnTimeWindowPolicy {

    private AiMultiTurnTimeWindowPolicy() {
    }

    /**
     * @param mergedFromFollowUp {@link com.nongxinle.ai.conversation.AiFollowUpResolver} 合并后的时间窗（可为 null）
     * @param tentativeExplicit  本句规则解析出的显式时间（无时间词则为 null）
     */
    public static AiResolvedTimeWindow finalizeTimeWindow(
            AiResolvedTimeWindow mergedFromFollowUp,
            AiResolvedTimeWindow tentativeExplicit,
            AiConversationTurnMemory previousTurn,
            LocalDate today) {
        LocalDate anchor = today != null ? today : LocalDate.now();
        if (tentativeExplicit != null) {
            tentativeExplicit.setExplicitTimeMentioned(true);
            tentativeExplicit.setInheritedFromPreviousTurn(false);
            return tentativeExplicit;
        }
        if (hasTurnMemoryDates(previousTurn)) {
            AiResolvedTimeWindow inherited = timeWindowFromPreviousTurn(previousTurn);
            if (inherited != null) {
                return inherited;
            }
        }
        if (mergedFromFollowUp != null) {
            mergedFromFollowUp.setExplicitTimeMentioned(false);
            return mergedFromFollowUp;
        }
        return AiResolvedTimeWindow.defaultMonthToDate(anchor);
    }

    public static boolean hasTurnMemoryDates(AiConversationTurnMemory p) {
        return p != null && StringUtils.hasText(p.getLastStartDate()) && StringUtils.hasText(p.getLastEndDate());
    }

    public static AiResolvedTimeWindow timeWindowFromPreviousTurn(AiConversationTurnMemory p) {
        if (!hasTurnMemoryDates(p)) {
            return null;
        }
        try {
            LocalDate s = LocalDate.parse(p.getLastStartDate());
            LocalDate e = LocalDate.parse(p.getLastEndDate());
            String label = p.getLastTimeLabel() != null ? p.getLastTimeLabel() : AiResolvedTimeWindow.CUSTOM;
            return AiResolvedTimeWindow.builder()
                    .timeLabel(label)
                    .startDate(s)
                    .endDate(e)
                    .displayText("继承上一轮时间窗")
                    .inheritedFromPreviousTurn(true)
                    .explicitTimeMentioned(false)
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }

    public static String resolveEffectiveTimeWindowSource(
            AiResolvedTimeWindow tentativeExplicit,
            AiResolvedTimeWindow finalTw) {
        if (finalTw == null) {
            return "UNRESOLVED";
        }
        if (tentativeExplicit != null) {
            return "CURRENT_MESSAGE_EXPLICIT";
        }
        if (finalTw.isInheritedFromPreviousTurn()) {
            return "INHERITED_PREVIOUS";
        }
        return "DEFAULT_MONTH_TO_DATE";
    }

    /**
     * @param previousTurn 用于判断是否在多轮会话中「本可继承却落到默认本月」等需提示场景；首轮单句为 null 时不输出 DEFAULT 提示以减少干扰。
     */
    public static String buildAnswerBoundaryNote(
            String effectiveTimeWindowSource,
            AiResolvedTimeWindow tw,
            AiConversationTurnMemory previousTurn) {
        if (tw == null || effectiveTimeWindowSource == null) {
            return null;
        }
        if ("INHERITED_PREVIOUS".equals(effectiveTimeWindowSource)) {
            String hint = humanReadableTimeCarryover(tw);
            return "按上文「" + hint + "」口径查询；本句未指定新的统计时间。若需其他时间段（例如本月），可直接说明。";
        }
        if ("DEFAULT_MONTH_TO_DATE".equals(effectiveTimeWindowSource) && previousTurn != null) {
            return "本句未指定统计时间，已按本月至今口径查询；若需其他时间段可说明。";
        }
        return null;
    }

    public static String humanReadableTimeCarryover(AiResolvedTimeWindow tw) {
        if (tw == null) {
            return "时间范围";
        }
        String lab = tw.getTimeLabel();
        if (AiResolvedTimeWindow.LAST_MONTH.equals(lab)) {
            return "上个月";
        }
        if (AiResolvedTimeWindow.THIS_MONTH.equals(lab)) {
            return "本月至今";
        }
        if (AiResolvedTimeWindow.TODAY.equals(lab)) {
            return "今天";
        }
        if (AiResolvedTimeWindow.YESTERDAY.equals(lab)) {
            return "昨天";
        }
        if (AiResolvedTimeWindow.ROLLING_7.equals(lab)) {
            return "最近7天";
        }
        if (tw.getStartDate() != null && tw.getEndDate() != null) {
            return tw.getStartDate() + "～" + tw.getEndDate();
        }
        String d = tw.getDisplayText();
        return d != null && !d.isBlank() ? d : "上文时间范围";
    }
}
