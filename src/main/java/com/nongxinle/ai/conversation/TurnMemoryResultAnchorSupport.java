package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.graph.business.MultiDomainOrchestrationSubPlanEvidenceSupport;
import com.nongxinle.ai.graph.business.OrchestrationSubPlanEvidenceStatus;
import com.nongxinle.ai.identity.BusinessEntityIdentityBridge;
import com.nongxinle.ai.identity.EntityIdentityResolutionSource;
import com.nongxinle.ai.identity.EntityIdentityResolutionStatus;
import com.nongxinle.ai.identity.ResolvedEntityIdentity;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.inheritance.ExplicitEntityFollowUpSupport;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话记忆 {@code lastResultAnchors} 写入策略：围绕可信 canonical resultAnchor、结构化实体主权与执行态，
 * 不由 mentionedName 非空 / Tool success / AnswerPlan 非 null 单独决定。
 * 可信 anchor 须具备正整数 {@code entityId}（或经 EntityIdentity 桥接写入的同等身份）。
 */
final class TurnMemoryResultAnchorSupport {

    private TurnMemoryResultAnchorSupport() {}

    static List<AiResultAnchor> resolveForTurnMemory(
            AiRunState state,
            AiConversationTurnMemory previousTurn,
            List<AiResultAnchor> rawPlanAnchors) {
        List<AiResultAnchor> current = collectTrustworthyCurrentAnchors(state, rawPlanAnchors);
        AiResolvedQueryContext ctx = state == null ? null : state.getResolvedQueryContext();

        if (current != null && !current.isEmpty()) {
            return current;
        }
        if (isClarificationOrNonExecutionTurn(state)) {
            return preferCurrentOrPrevious(current, previousTurn);
        }
        if (shouldPreserveStableAnchorDespiteExecution(state, ctx)) {
            return preferCurrentOrPrevious(null, previousTurn);
        }
        if (hasConfirmedNewEntitySovereignty(state, ctx, previousTurn)) {
            return emptyToNull(current);
        }
        if (hasFormalDomainExecution(state)) {
            return null;
        }
        return emptyToNull(current);
    }

    private static List<AiResultAnchor> collectTrustworthyCurrentAnchors(
            AiRunState state, List<AiResultAnchor> rawPlanAnchors) {
        if (state == null) {
            return null;
        }
        List<AiResultAnchor> merged = new ArrayList<>();
        if (rawPlanAnchors != null) {
            for (AiResultAnchor anchor : rawPlanAnchors) {
                if (isTrustworthyResultAnchor(anchor)) {
                    merged.add(anchor);
                }
            }
        }
        appendConfirmedGoodsIdentityAnchor(state.getResolvedQueryContext(), merged);
        return merged.isEmpty() ? null : merged;
    }

    private static boolean isClarificationOrNonExecutionTurn(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isNeedClarification()) {
            return true;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        return ctx != null && ctx.isNeedSemanticClarification();
    }

    private static boolean hasConfirmedNewEntitySovereignty(
            AiRunState state, AiResolvedQueryContext ctx, AiConversationTurnMemory previousTurn) {
        if (ctx == null || ctx.getQuerySemanticParse() == null) {
            return false;
        }
        ResolvedEntityIdentity goods = BusinessEntityIdentityBridge.resolveGoods(ctx);
        if (hasConfirmedNewGoodsIdentity(goods)) {
            return true;
        }
        return ExplicitEntityFollowUpSupport.isExplicitEntityFollowUp(
                ctx.getQuerySemanticParse(), previousTurn, null);
    }

    private static boolean hasConfirmedNewGoodsIdentity(ResolvedEntityIdentity goods) {
        if (goods == null || !goods.isExecutable()) {
            return false;
        }
        EntityIdentityResolutionSource source = goods.getResolutionSource();
        return source == EntityIdentityResolutionSource.CURRENT_STRUCTURED_ID
                || source == EntityIdentityResolutionSource.CURRENT_MENTION_DB;
    }

    private static boolean shouldPreserveStableAnchorDespiteExecution(
            AiRunState state, AiResolvedQueryContext ctx) {
        if (AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchorPolicy(ctx))) {
            return true;
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy(ctx))) {
            ResolvedEntityIdentity goods = BusinessEntityIdentityBridge.resolveGoods(ctx);
            if (goods == null || goods.getResolutionStatus() == EntityIdentityResolutionStatus.SKIPPED) {
                return false;
            }
            if (goods.getResolutionStatus() != EntityIdentityResolutionStatus.OK) {
                return true;
            }
            return !hasConfirmedNewGoodsIdentity(goods);
        }
        ResolvedEntityIdentity goods = BusinessEntityIdentityBridge.resolveGoods(ctx);
        if (goods == null) {
            return false;
        }
        EntityIdentityResolutionStatus status = goods.getResolutionStatus();
        if (status == EntityIdentityResolutionStatus.NOT_FOUND
                || status == EntityIdentityResolutionStatus.NEED_CLARIFICATION) {
            return true;
        }
        return status == EntityIdentityResolutionStatus.UNRESOLVED && goods.hasExplicitMention();
    }

    private static boolean hasFormalDomainExecution(AiRunState state) {
        if (state == null || isClarificationOrNonExecutionTurn(state)) {
            return false;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        if (ctx == null) {
            return false;
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        if (sem == null || !SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return false;
        }
        if (hasNonInvalidDomainPlanEvidence(state)) {
            return true;
        }
        ResolvedEntityIdentity goods = BusinessEntityIdentityBridge.resolveGoods(ctx);
        return goods != null && goods.getResolutionStatus() != EntityIdentityResolutionStatus.SKIPPED;
    }

    private static boolean hasNonInvalidDomainPlanEvidence(AiRunState state) {
        return domainPlanEvidenceStatus(state.getPurchaseAnswerPlan()) != OrchestrationSubPlanEvidenceStatus.INVALID
                || domainPlanEvidenceStatus(state.getStockReduceAnswerPlan())
                        != OrchestrationSubPlanEvidenceStatus.INVALID
                || domainPlanEvidenceStatus(state.getRevenueAnswerPlan())
                        != OrchestrationSubPlanEvidenceStatus.INVALID
                || domainPlanEvidenceStatus(state.getDishProfitAnswerPlan())
                        != OrchestrationSubPlanEvidenceStatus.INVALID
                || state.getDiagnosisPlan() != null;
    }

    private static OrchestrationSubPlanEvidenceStatus domainPlanEvidenceStatus(Object plan) {
        if (plan == null) {
            return OrchestrationSubPlanEvidenceStatus.INVALID;
        }
        if (plan instanceof com.nongxinle.ai.dto.business.PurchaseAnswerPlan p) {
            return MultiDomainOrchestrationSubPlanEvidenceSupport.evaluate(p);
        }
        if (plan instanceof com.nongxinle.ai.dto.business.StockReduceAnswerPlan p) {
            return MultiDomainOrchestrationSubPlanEvidenceSupport.evaluate(p);
        }
        if (plan instanceof com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan p) {
            return MultiDomainOrchestrationSubPlanEvidenceSupport.evaluate(p);
        }
        if (plan instanceof com.nongxinle.ai.dto.business.DishProfitAnswerPlan p) {
            return MultiDomainOrchestrationSubPlanEvidenceSupport.evaluate(p);
        }
        return OrchestrationSubPlanEvidenceStatus.INVALID;
    }

    private static void appendConfirmedGoodsIdentityAnchor(
            AiResolvedQueryContext ctx, List<AiResultAnchor> merged) {
        if (ctx == null || merged == null) {
            return;
        }
        ResolvedEntityIdentity goods = BusinessEntityIdentityBridge.resolveGoods(ctx);
        if (!hasConfirmedNewGoodsIdentity(goods)) {
            return;
        }
        AiResultAnchor anchor =
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                        .entityId(String.valueOf(goods.getResolvedEntityId()))
                        .entityName(goods.getResolvedCanonicalName())
                        .sourcePlanType("entity_identity.goods")
                        .build();
        if (!containsSameGoodsAnchor(merged, anchor)) {
            merged.add(anchor);
        }
    }

    private static boolean containsSameGoodsAnchor(List<AiResultAnchor> anchors, AiResultAnchor candidate) {
        if (anchors == null || candidate == null) {
            return false;
        }
        for (AiResultAnchor existing : anchors) {
            if (existing == null) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(
                    stringLoose(existing.getEntityType()))) {
                continue;
            }
            if (candidate.getEntityId() != null
                    && candidate.getEntityId().equals(existing.getEntityId())) {
                return true;
            }
            if (StringUtils.hasText(candidate.getEntityName())
                    && candidate.getEntityName().equals(existing.getEntityName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrustworthyResultAnchor(AiResultAnchor anchor) {
        if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
            return false;
        }
        if (!StringUtils.hasText(anchor.getEntityName())) {
            return false;
        }
        return hasCanonicalEntityId(anchor.getEntityId());
    }

    /**
     * 结构化身份：正整数 canonical entityId（Tool / AnswerPlan 正式事实行 / EntityIdentity 投影）。
     * 仅 type+name 非空（含口述指代、rewrite 短语）不算可信。
     */
    private static boolean hasCanonicalEntityId(String entityId) {
        return parsePositiveEntityId(entityId) != null;
    }

    private static Integer parsePositiveEntityId(String entityId) {
        if (!StringUtils.hasText(entityId)) {
            return null;
        }
        String trimmed = entityId.trim();
        try {
            long id = Long.parseLong(trimmed);
            if (id <= 0 || id > Integer.MAX_VALUE) {
                return null;
            }
            return (int) id;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String anchorPolicy(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getQuerySemanticParse() == null) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = ctx.getQuerySemanticParse().getSemanticSlots();
        if (slots == null || !StringUtils.hasText(slots.getAnchorPolicy())) {
            return null;
        }
        return slots.getAnchorPolicy().trim();
    }

    private static List<AiResultAnchor> preferCurrentOrPrevious(
            List<AiResultAnchor> current, AiConversationTurnMemory previousTurn) {
        if (current != null && !current.isEmpty()) {
            return current;
        }
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null
                || previousTurn.getLastResultAnchors().isEmpty()) {
            return null;
        }
        List<AiResultAnchor> trustedPrevious = new ArrayList<>();
        for (AiResultAnchor anchor : previousTurn.getLastResultAnchors()) {
            if (isTrustworthyResultAnchor(anchor)) {
                trustedPrevious.add(anchor);
            }
        }
        return trustedPrevious.isEmpty() ? null : trustedPrevious;
    }

    private static List<AiResultAnchor> emptyToNull(List<AiResultAnchor> anchors) {
        return anchors == null || anchors.isEmpty() ? null : anchors;
    }

    private static String stringLoose(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
