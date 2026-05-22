package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.harness.AiMultiStoreHarnessTrace;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Harness / Replay 多门店排行范围：subset 检测与「继承上一轮多店 + 金额排行 wire」组合判断。
 */
public final class SemanticHarnessScopePolicy {

    private SemanticHarnessScopePolicy() {}

    public record HarnessMultiStoreSnapshot(
            boolean singleStoreNarrowingBlocked,
            boolean inheritedMultiStoreRanking,
            boolean harnessMultiStoreScopeDetected,
            boolean harnessMultiStoreScopeApplied,
            String harnessMultiStoreScopeSource,
            List<String> harnessMatchedNames) {}

    public static HarnessMultiStoreSnapshot buildHarnessMultiStoreSnapshot(
            boolean clarificationRequired,
            String normalized,
            AiFollowUpResolution followUp,
            AiQuerySemanticParseResult semanticLlm,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope mergedOrg,
            AiResolvedQueryIntent queryIntent,
            AiMultiStoreHarnessTrace multiStoreHarness) {
        boolean singleStoreNarrowingBlocked =
                multiStoreHarness != null
                        && multiStoreHarness.resolveSingleStoreNarrowingBlocked(
                                normalized, mergedOrg, semanticLlm);
        boolean subsetApplied = multiStoreHarness != null && multiStoreHarness.isSubsetApplied();
        boolean inheritedMultiStoreRanking =
                !clarificationRequired
                        && !subsetApplied
                        && followUp != null
                        && "INHERITED_PREVIOUS".equals(followUp.getEffectiveScopeSource())
                        && semanticLlm != null
                        && !semanticLlm.isParseMissing()
                        && "INHERIT_PREVIOUS".equals(normalizeSemanticV2ActionToken(semanticLlm.getScopeAction()))
                        && previousTurn != null
                        && previousTurn.getLastVisibleStoreIds() != null
                        && previousTurn.getLastVisibleStoreIds().size() >= 2
                        && mergedOrg != null
                        && mergedOrg.getVisibleStores() != null
                        && queryIntent != null
                        && isHarnessMultiStoreAmountRankingWire(queryIntent.getStructuredIntentDetail())
                        && mergedOrg.getVisibleStores().stream()
                                        .filter(s -> s != null && s.getStoreDepartmentId() != null)
                                        .count()
                                >= 2;
        boolean harnessMultiStoreScopeDetected =
                (multiStoreHarness != null && multiStoreHarness.isDetected()) || inheritedMultiStoreRanking;
        boolean harnessMultiStoreScopeApplied = subsetApplied || inheritedMultiStoreRanking;
        String harnessMultiStoreScopeSource =
                subsetApplied ? "SEMANTIC_SUBSET" : (inheritedMultiStoreRanking ? "INHERITED_PREVIOUS" : null);
        List<String> harnessMatchedNames =
                multiStoreHarness != null ? multiStoreHarness.copyMatchedStores() : new ArrayList<>();
        if (inheritedMultiStoreRanking && harnessMatchedNames.isEmpty() && mergedOrg != null) {
            List<String> fromOrg = visibleStoreNamesForHarness(mergedOrg);
            if (!fromOrg.isEmpty()) {
                harnessMatchedNames = new ArrayList<>(fromOrg);
            }
        }
        return new HarnessMultiStoreSnapshot(
                singleStoreNarrowingBlocked,
                inheritedMultiStoreRanking,
                harnessMultiStoreScopeDetected,
                harnessMultiStoreScopeApplied,
                harnessMultiStoreScopeSource,
                harnessMatchedNames);
    }

    static boolean isHarnessMultiStoreAmountRankingWire(String structuredDetailRaw) {
        if (!StringUtils.hasText(structuredDetailRaw)) {
            return false;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredDetailRaw.trim());
        String wire = StringUtils.hasText(canon) ? canon : structuredDetailRaw.trim();
        return AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(wire);
    }

    private static List<String> visibleStoreNamesForHarness(AiResolvedOrgScope org) {
        if (org == null || org.getVisibleStores() == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            String n = AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(s.getStoreName());
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    private static String normalizeSemanticV2ActionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
