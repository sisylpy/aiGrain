package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 当前轮 V2 Business Frame 相对上一轮 stable frame 是否发生非 time-only 的实质变更。
 * 用于 Transition 正向证明：operation / detail / sourceFacet 等已变时，禁止 SAME_CAPABILITY_TIME_OVERRIDE 抢权。
 */
public final class BusinessFrameMaterialChangeSupport {

    private BusinessFrameMaterialChangeSupport() {}

    public static boolean currentMateriallyChangesStableBusinessFrame(
            AiQuerySemanticParseResult current, AiConversationTurnMemory previous) {
        if (current == null
                || previous == null
                || !SemanticContractFamilySupport.previousTurnHasStableBusinessFrame(previous)) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart cur = current.getSemanticSlots();
        AiQuerySemanticParseResult.SemanticSlotsPart prev = previous.getLastSemanticSlots();
        if (cur == null || prev == null) {
            return false;
        }

        String curContract = normalizeToken(cur.getSelectedContractId());
        String prevContract = normalizeToken(prev.getSelectedContractId());
        if (StringUtils.hasText(curContract)
                && StringUtils.hasText(prevContract)
                && !curContract.equalsIgnoreCase(prevContract)) {
            return true;
        }

        if (fieldMateriallyChanged(cur.getQueryObject(), prev.getQueryObject())) {
            return true;
        }
        if (fieldMateriallyChanged(cur.getOperation(), prev.getOperation())) {
            return true;
        }
        if (fieldMateriallyChanged(cur.getMetric(), prev.getMetric())) {
            return true;
        }
        if (fieldMateriallyChanged(cur.getSourceFacet(), prev.getSourceFacet())) {
            return true;
        }
        if (fieldMateriallyChanged(cur.getDetailWanted(), prev.getDetailWanted())) {
            return true;
        }
        if (fieldMateriallyChanged(
                canonicalWire(cur.getStructuredIntentDetailWire()),
                canonicalWire(prev.getStructuredIntentDetailWire()))) {
            return true;
        }
        return fieldMateriallyChanged(cur.getAnswerPlanType(), prev.getAnswerPlanType());
    }

    private static boolean fieldMateriallyChanged(String currentValue, String previousValue) {
        String cur = normalizeToken(currentValue);
        if (!StringUtils.hasText(cur)) {
            return false;
        }
        String prev = normalizeToken(previousValue);
        return !StringUtils.hasText(prev) || !cur.equalsIgnoreCase(prev);
    }

    private static String canonicalWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
