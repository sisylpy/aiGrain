package com.nongxinle.ai.harness;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 采购概览链路下「短追问包络命中但未收窄 purchaseSourceType」的未知语义采样，供扩充 {@code AiQuerySemanticLexicon}。
 */
@Slf4j
@Component
public class AiHarnessUnknownPurchaseSemanticLogger {

    @Value("${ai.harness.unknown-purchase-semantic-log-enabled:false}")
    private boolean logEnabled;

    public void recordPurchaseOverviewAugmentUnresolved(
            String rawText,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent mergedIntent,
            Long conversationId,
            Long runId) {
        if (!logEnabled || !StringUtils.hasText(rawText) || mergedIntent == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rawText", rawText.trim());
        payload.put("conversationId", conversationId);
        payload.put("runId", runId);
        if (previousTurn != null) {
            payload.put("previousIntent", blankToNull(previousTurn.getLastIntentCode()));
            payload.put("previousPath", blankToNull(previousTurn.getLastPathCode()));
            payload.put("previousTimeWindow",
                    summarizePrevTime(previousTurn.getLastStartDate(), previousTurn.getLastEndDate(),
                            previousTurn.getLastTimeLabel()));
            payload.put("previousScope", summarizePrevScope(previousTurn));
            payload.put("previousPurchaseSourceType", blankToNull(previousTurn.getLastPurchaseSourceType()));
        } else {
            payload.put("previousIntent", null);
            payload.put("previousPath", null);
            payload.put("previousTimeWindow", null);
            payload.put("previousScope", null);
        }
        payload.put("resolvedEffectiveIntentAfterMerge",
                mergedIntent != null ? blankToNull(mergedIntent.getIntentCode()) : null);
        payload.put("resolvedEffectivePathAfterMerge",
                mergedIntent != null ? blankToNull(mergedIntent.getPathCode()) : null);
        payload.put("resolvedPurchaseSourceType", blankToNull(mergedIntent != null ? mergedIntent.getPurchaseSourceType() : null));
        payload.put("event", "PURCHASE_OVERVIEW_SHORT_SOURCE_UNRESOLVED");
        log.warn("[AIHarnessUnknownPurchaseSemantic] {}", JSON.toJSONString(payload));
    }

    private static Map<String, Object> summarizePrevTime(String start, String end, String label) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("startDate", blankToNull(start));
        m.put("endDate", blankToNull(end));
        m.put("timeLabel", blankToNull(label));
        return m;
    }

    private static Map<String, Object> summarizePrevScope(AiConversationTurnMemory t) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (t == null) {
            return m;
        }
        m.put("scopeType", blankToNull(t.getLastScopeType()));
        m.put("visibleStoreIds", t.getLastVisibleStoreIds());
        m.put("focusedStoreName", blankToNull(t.getLastFocusedStoreName()));
        return m;
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }
}
