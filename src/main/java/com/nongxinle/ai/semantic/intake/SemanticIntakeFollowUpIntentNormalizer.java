package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.inheritance.SemanticContractFamilySupport;
import com.nongxinle.ai.semantic.inheritance.StructuredRankingTimeOnlyIntakeSupport;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityGroundingService;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Intake {@code followUpIntent} 唯一归一入口：LLM JSON {@code followUpIntent}、legacy reason wire token、
 * 或结构化 Intake 字段（isFollowUp / usedPreviousContext）→ {@link SemanticIntakeFollowUpIntent}。
 * Policy / Sovereignty / Transition 只读 {@link SemanticIntakeResult#getFollowUpIntent()}，禁止再 parse reason。
 */
public final class SemanticIntakeFollowUpIntentNormalizer {

    private SemanticIntakeFollowUpIntentNormalizer() {}

    /** Intake reconcile 链末调用；写入 {@code followUpIntent}，保留 {@code reason} 作 debug。 */
    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null || intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return intake;
        }
        SemanticIntakeFollowUpIntent intent = resolve(input, intake);
        if (intent == null || !intent.isActive()) {
            return intake;
        }
        return intake.toBuilder().followUpIntent(intent).build();
    }

    /** 从 LLM parsed JSON 块构造（parser 阶段）；可被 reconcile 覆盖/补全。 */
    public static SemanticIntakeFollowUpIntent fromParsedJson(LlmSemanticIntakeParsed parsed) {
        if (parsed == null || parsed.getFollowUpIntent() == null || !parsed.getFollowUpIntent().isActive()) {
            return null;
        }
        return parsed.getFollowUpIntent();
    }

    static SemanticIntakeFollowUpIntent resolve(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (!SemanticContractFamilySupport.intakeHasPreviousStableContract(input)) {
            return null;
        }
        if (intake.getFollowUpIntent() != null && intake.getFollowUpIntent().isActive()) {
            return finalizeIntent(input, intake, intake.getFollowUpIntent());
        }
        SemanticIntakeFollowUpIntent fromLegacy = fromLegacyReasonWireTokens(input, intake.getReason());
        if (fromLegacy != null) {
            return finalizeIntent(input, intake, fromLegacy);
        }
        SemanticIntakeFollowUpIntent structured = deriveStructuredFollowUpIntent(input, intake);
        if (structured != null) {
            return finalizeIntent(input, intake, structured);
        }
        return null;
    }

    private static SemanticIntakeFollowUpIntent fromLegacyReasonWireTokens(
            SemanticIntakeInput input, String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String n = reason.trim().toLowerCase(Locale.ROOT);
        if (StructuredRankingTimeOnlyIntakeSupport.isStructuredRankingTimeOnlyIntakeReason(reason)) {
            return SemanticIntakeFollowUpIntent.builder()
                    .kind(SemanticIntakeFollowUpKind.RANKING_TIME_OVERRIDE)
                    .targetContractId(resolvePreviousStableContractId(input))
                    .anchorPolicy("USE_PREVIOUS_ANCHOR")
                    .build();
        }
        if (n.contains(CoverDaysEntityGroundingService.SALES_BASELINE_REASON_MARKER)) {
            return SemanticIntakeFollowUpIntent.builder()
                    .kind(SemanticIntakeFollowUpKind.SAME_CAPABILITY_TIME_OVERRIDE)
                    .targetContractId(resolvePreviousStableContractId(input))
                    .anchorPolicy("USE_PREVIOUS_ANCHOR")
                    .build();
        }
        if (n.contains(SemanticIntakeGoodsAnchorFollowUpSupport.REASON_MARKER)
                || n.contains("inherit_previous_goods_anchor")
                || n.contains("goods_stock_follow_up")) {
            return SemanticIntakeFollowUpIntent.builder()
                    .kind(SemanticIntakeFollowUpKind.GOODS_ANCHOR_STOCK)
                    .targetContractId(resolvePreviousStableContractId(input))
                    .anchorPolicy("USE_PREVIOUS_ANCHOR")
                    .build();
        }
        return null;
    }

    /** 结构化 Intake 字段推导；不读 reason / canonicalUserQuery。 */
    private static SemanticIntakeFollowUpIntent deriveStructuredFollowUpIntent(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (intake == null) {
            return null;
        }
        if (SemanticIntakeSovereignDomainSupport.intakeDeclaresSovereignCrossFamilyCapability(
                input, intake)) {
            return null;
        }
        if (!Boolean.TRUE.equals(intake.getIsFollowUp())
                || !Boolean.TRUE.equals(intake.getUsedPreviousContext())) {
            return null;
        }
        if (intake.getNormalizationType() != SemanticIntakeNormalizationType.REWRITE) {
            return null;
        }
        return null;
    }

    private static SemanticIntakeFollowUpIntent finalizeIntent(
            SemanticIntakeInput input,
            SemanticIntakeResult intake,
            SemanticIntakeFollowUpIntent intent) {
        if (intent == null || !intent.isActive()) {
            return null;
        }
        if (intake != null
                && intent.getKind() == SemanticIntakeFollowUpKind.SAME_CAPABILITY_TIME_OVERRIDE
                && SemanticIntakeSovereignDomainSupport.intakeDeclaresSovereignCrossFamilyCapability(
                        input, intake)) {
            return null;
        }
        SemanticIntakeFollowUpIntent enriched = enrichTargetContract(input, intent);
        if (enriched == null || !enriched.isActive()) {
            return null;
        }
        if (!StringUtils.hasText(enriched.getTargetContractId())) {
            return null;
        }
        return enriched;
    }

    private static SemanticIntakeFollowUpIntent enrichTargetContract(
            SemanticIntakeInput input, SemanticIntakeFollowUpIntent intent) {
        if (StringUtils.hasText(intent.getTargetContractId())) {
            return intent;
        }
        String resolved = resolvePreviousStableContractId(input);
        if (!StringUtils.hasText(resolved)) {
            return null;
        }
        return intent.toBuilder().targetContractId(resolved).build();
    }

    static String resolvePreviousStableContractId(SemanticIntakeInput input) {
        return SemanticContractFamilySupport.resolvePreviousStableContractIdFromIntakeInput(input);
    }
}
