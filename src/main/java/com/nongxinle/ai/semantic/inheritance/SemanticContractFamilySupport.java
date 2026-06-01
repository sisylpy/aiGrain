package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import org.springframework.util.StringUtils;

/**
 * 从 contractId / Catalog 解析 contract family（namespace 前缀），不做业务 case 分支。
 */
public final class SemanticContractFamilySupport {

    private SemanticContractFamilySupport() {}

    public static String resolveFamily(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        String trimmed = contractId.trim();
        int dot = trimmed.indexOf('.');
        if (dot <= 0) {
            return trimmed.toLowerCase();
        }
        return trimmed.substring(0, dot).toLowerCase();
    }

    public static String resolveSelectedDomain(
            DomainContractSelectionResult selection, AiQuerySemanticParseResult parse) {
        if (selection != null && StringUtils.hasText(selection.getSelectedDomain())) {
            return selection.getSelectedDomain().trim();
        }
        if (parse != null && StringUtils.hasText(parse.getSemanticDomain())) {
            return parse.getSemanticDomain().trim();
        }
        return null;
    }

    public static boolean sameFamily(String familyA, String familyB) {
        return StringUtils.hasText(familyA)
                && StringUtils.hasText(familyB)
                && familyA.equalsIgnoreCase(familyB);
    }

    public static boolean crossFamily(String currentFamily, String previousFamily) {
        if (!StringUtils.hasText(currentFamily) || !StringUtils.hasText(previousFamily)) {
            return false;
        }
        return !currentFamily.equalsIgnoreCase(previousFamily);
    }

    public static String contractIdFromParse(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getSemanticSlots() == null) {
            return null;
        }
        return trim(parse.getSemanticSlots().getSelectedContractId());
    }

    public static String contractIdFromPreviousTurn(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastSemanticSlots() == null) {
            return null;
        }
        return trim(previousTurn.getLastSemanticSlots().getSelectedContractId());
    }

    public static SemanticCapabilityContract lookupActiveContract(
            String contractId, String domainHint) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        return SemanticContractCatalog.findActiveCapabilityContractById(
                contractId.trim(), domainHint);
    }

    /** 上一轮是否存在可继承的稳定业务 frame（contractId + wire）。 */
    public static boolean previousTurnHasStableBusinessFrame(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastSemanticSlots() == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = previousTurn.getLastSemanticSlots();
        if (!StringUtils.hasText(slots.getSelectedContractId())) {
            return false;
        }
        String wire = slots.getStructuredIntentDetailWire();
        if (StringUtils.hasText(wire)) {
            return StringUtils.hasText(
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim()));
        }
        return StringUtils.hasText(previousTurn.getLastStructuredIntentDetail());
    }

    /**
     * V2 parser 边界提示：上一轮是否为 purchase 域 period goods list frame（读 structured wire，非 NL）。
     */
    public static boolean wasPreviousPurchasePeriodGoodsList(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = previousTurn.getLastSemanticSlots();
        if (slots != null) {
            String wire =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            slots.getStructuredIntentDetailWire());
            if (AiQuerySemanticLexicon.isPurchasePeriodGoodsListStructuredDetail(wire)) {
                return true;
            }
            String family = resolveFamily(slots.getSelectedContractId());
            if ("purchase".equals(family)
                    && StringUtils.hasText(slots.getSelectedContractId())
                    && slots.getSelectedContractId().contains("period_goods_list")) {
                return true;
            }
        }
        return AiQuerySemanticLexicon.isPurchasePeriodGoodsListStructuredDetail(
                previousTurn.getLastStructuredIntentDetail());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
