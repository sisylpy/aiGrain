package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.harness.followup.PurchaseDrilldownMatrix;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 本轮 LLM 输出的语义帧（采购域 Phase 1）。
 * <p>职责：只读视图 + 矩阵 canonical 入口委托；不做 capability 匹配、不做澄清决策。
 * <ul>
 *   <li>{@link #canonicalizePurchaseFollowUp} — 委托 {@link PurchaseDrilldownMatrix}</li>
 *   <li>{@link #buildFrame} — 枚举/wire 归一后构建帧</li>
 * </ul>
 */
@Value
@Builder
public class CurrentSemanticFrame {

    String queryObject;
    String operation;
    String metric;
    String sourceFacet;
    String timeAction;
    String scopeAction;
    String anchorPolicy;
    String detailWanted;
    String structuredIntentDetailWire;
    boolean needClarification;
    String clarificationQuestion;

    public static CurrentSemanticFrame fromParseResult(AiQuerySemanticParseResult raw) {
        return fromParseResult(raw, null);
    }

    /**
     * 构建 canonical frame；若需突变 sem 供 Merge，请先 {@link #canonicalizePurchaseFollowUp} 再 {@link #buildFrame}。
     */
    public static CurrentSemanticFrame fromParseResult(
            AiQuerySemanticParseResult raw, AiConversationTurnMemory previousTurn) {
        return buildFrame(canonicalizePurchaseFollowUp(raw, previousTurn));
    }

    /** 采购追问 canonical：委托矩阵契约表；返回新 parse 副本或原引用。 */
    public static AiQuerySemanticParseResult canonicalizePurchaseFollowUp(
            AiQuerySemanticParseResult raw, AiConversationTurnMemory previousTurn) {
        return PurchaseDrilldownMatrix.canonicalizePurchaseFollowUp(raw, previousTurn);
    }

    /** 对已 canonical 的 parse 构建帧；operation 归一见 {@link PurchaseDrilldownMatrix#canonicalOperation}。 */
    public static CurrentSemanticFrame buildFrame(AiQuerySemanticParseResult raw) {
        if (raw == null || raw.isParseMissing()) {
            return CurrentSemanticFrame.builder().build();
        }
        AiQuerySemanticParseResult.SemanticSlotsPart ss = raw.getSemanticSlots();
        String wireRaw = ss != null ? ss.getStructuredIntentDetailWire() : null;
        String wireCanon =
                StringUtils.hasText(wireRaw)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wireRaw.trim())
                        : null;
        String queryObject = normalizeEnumToken(ss != null ? ss.getQueryObject() : null);
        String anchorPolicy = normalizeEnumToken(ss != null ? ss.getAnchorPolicy() : null);
        String detailWanted =
                AiQuerySemanticLexicon.canonicalDetailWanted(
                        ss != null ? ss.getDetailWanted() : null,
                        queryObject,
                        normalizeEnumToken(ss != null ? ss.getOperation() : null),
                        wireCanon);
        String operation =
                PurchaseDrilldownMatrix.canonicalOperation(
                        ss != null ? ss.getOperation() : null,
                        detailWanted,
                        queryObject,
                        anchorPolicy,
                        wireCanon);
        return CurrentSemanticFrame.builder()
                .queryObject(queryObject)
                .operation(operation)
                .metric(normalizeEnumToken(ss != null ? ss.getMetric() : null))
                .sourceFacet(normalizeSourceFacet(ss != null ? ss.getSourceFacet() : null))
                .timeAction(trimUpper(raw.getTimeAction()))
                .scopeAction(trimUpper(raw.getScopeAction()))
                .anchorPolicy(anchorPolicy)
                .detailWanted(detailWanted)
                .structuredIntentDetailWire(wireCanon)
                .needClarification(Boolean.TRUE.equals(raw.getNeedClarification()))
                .clarificationQuestion(trimQuestion(raw.getClarificationQuestion()))
                .build();
    }

    private static String trimQuestion(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimUpper(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return t.isEmpty() ? null : t;
    }

    private static String normalizeEnumToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticSlotMerge.UNKNOWN.equalsIgnoreCase(t)) {
            return AiQuerySemanticSlotMerge.UNKNOWN;
        }
        return t.isEmpty() ? null : t;
    }

    private static String normalizeSourceFacet(String raw) {
        String t = normalizeEnumToken(raw);
        if (!StringUtils.hasText(t)) {
            return null;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(t)) {
            return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(t)) {
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_ALL.equals(t) || "ALL".equals(t)) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        return t;
    }
}
