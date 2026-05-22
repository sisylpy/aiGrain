package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.SemanticParseFallbackPolicy;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.SemanticContractClarificationQuestionFactory;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidator;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 语义采纳：ContractCompletion、SlotMerge、采购 frame、MergeHelper、{@link SemanticTimeContractCheck}。
 * 逻辑与 {@link AiResolvedQueryContextResolver} 原 {@code trySemanticAdoption} 等价，仅搬移。
 */
@Component
public class SemanticAdoptionPipeline {

    @Value("${ai.agent.querySemanticLlm.minConfidence:0.55}")
    private double querySemanticMinConfidence;

    public record Request(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {}

    public SemanticAdoptionAttempt tryAdopt(Request request) {
        if (request == null) {
            return null;
        }
        return tryAdopt(
                request.sem(),
                request.previousTurn(),
                request.normalized(),
                request.today(),
                request.explicitTentative(),
                request.followUpRewriteApplied(),
                request.contractSelection(),
                request.rewriteInheritedAnchorType(),
                request.rewriteInheritedAnchorName());
    }

    SemanticAdoptionAttempt tryAdopt(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (sem == null || SemanticParseFallbackPolicy.needSemanticParseClarification(sem, querySemanticMinConfidence)) {
            return null;
        }

        AiQuerySemanticParseResult rawForDebug = sem;
        SemanticContractCompletionEngine.Result completion =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(sem)
                                .selectedDomain(
                                        contractSelection != null
                                                ? contractSelection.getSelectedDomain()
                                                : null)
                                .contractSelection(contractSelection)
                                .previousTurn(previousTurn)
                                .rewriteInheritedAnchorType(rewriteInheritedAnchorType)
                                .rewriteInheritedAnchorName(rewriteInheritedAnchorName)
                                .build());
        if (completion.isViolation()) {
            sem = completion.getCompletedParse();
            Map<String, Object> trace =
                    sem.getContractCompletionTrace() != null
                            ? new LinkedHashMap<>(sem.getContractCompletionTrace())
                            : new LinkedHashMap<>();
            trace.put("rawParseRetained", true);
            sem = sem.toBuilder().contractCompletionTrace(trace).build();
            String question =
                    SemanticContractClarificationQuestionFactory.forContractViolation(
                            completion.getViolationCode(), completion.getViolationReason());
            if (!StringUtils.hasText(question)) {
                question = "当前问题无法匹配已支持的能力，请换一种说法或补充信息。";
            }
            sem = sem.toBuilder().needClarification(true).clarificationQuestion(question).build();
            return new SemanticAdoptionAttempt(
                    sem,
                    null,
                    null,
                    null,
                    "contract_completion:" + completion.getViolationReason(),
                    question,
                    completion.getViolationCode());
        }
        sem = completion.getCompletedParse();
        if (rawForDebug != sem) {
            Map<String, Object> trace =
                    sem.getContractCompletionTrace() != null
                            ? new LinkedHashMap<>(sem.getContractCompletionTrace())
                            : new LinkedHashMap<>();
            trace.put("contractCompletionApplied", true);
            sem = sem.toBuilder().contractCompletionTrace(trace).build();
        }

        boolean contractLocked = SemanticContractCompletionEngine.hasSelectedContractId(sem);
        boolean purchaseFrameAdoption =
                !contractLocked
                        && !AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)
                        && AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem)
                        && !AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem);
        if (purchaseFrameAdoption) {
            sem = AiQuerySemanticSlotMerge.reconcilePurchaseSourceFacetDefaults(sem);
            sem = AiQuerySemanticSlotMerge.reconcileMetricWithSourceFacet(sem);
            sem = AiQuerySemanticSlotMerge.reconcilePurchaseGoodsRankingSemanticSlots(sem);
            sem = AiQuerySemanticSlotMerge.reconcilePurchaseStructuredWireFromSemanticSlots(sem);
            sem =
                    AiQuerySemanticSlotMerge.reconcilePurchaseCompleteUtteranceDefaults(
                            sem, followUpRewriteApplied);
            sem = AiQuerySemanticSlotMerge.reconcileAnswerPlanTypeFromWire(sem);
            sem =
                    CurrentSemanticFrame.canonicalizePurchaseFollowUp(
                            sem, followUpRewriteApplied ? null : previousTurn);
            CurrentSemanticFrame frame = CurrentSemanticFrame.buildFrame(sem);
            SemanticFrameValidationResult frameVal =
                    CurrentSemanticFrameValidator.validate(
                            frame, sem, previousTurn, normalized, followUpRewriteApplied);
            if (frameVal.needSemanticClarification()) {
                sem.setNeedClarification(true);
                String frameQuestion = frameVal.semanticClarificationQuestion();
                if (StringUtils.hasText(frameQuestion)) {
                    sem.setClarificationQuestion(frameQuestion);
                }
                List<String> frameCodes = frameVal.violationCodes();
                String frameRejectReason =
                        frameCodes != null && !frameCodes.isEmpty()
                                ? String.join(",", frameCodes)
                                : "frame_validation";
                return new SemanticAdoptionAttempt(
                        sem, null, null, null, frameRejectReason, frameQuestion);
            }
            sem = AiQuerySemanticSlotMerge.reconcileSemanticSlotsViaCapabilityMatrices(sem);
            sem.setPurchaseSemanticFramePrimaryMerge(true);
        } else if (!contractLocked) {
            sem = AiQuerySemanticSlotMerge.reconcileSemanticSlotsViaCapabilityMatrices(sem);
        }
        AiResolvedQueryIntent baseline = AiResolvedQueryIntent.builder().build();
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(
                        baseline, sem, querySemanticMinConfidence, normalized, previousTurn);
        if (!StringUtils.hasText(merged.getPathCode())) {
            return null;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart alignedSlots =
                AiQuerySemanticSlotMerge.alignSemanticSlotsForTurnMemoryPersistence(
                        sem.getSemanticSlots(), merged.getStructuredIntentDetail());
        if (alignedSlots != sem.getSemanticSlots()) {
            sem = sem.toBuilder().semanticSlots(alignedSlots).build();
        }
        AiResolvedTimeWindow tentative =
                AiQuerySemanticLlmMergeHelper.mergeTentativeTime(
                        explicitTentative,
                        sem,
                        today,
                        querySemanticMinConfidence,
                        normalized,
                        merged,
                        previousTurn);
        sem =
                SemanticTimeContractCheck.reconcileTimePartForContract(
                        sem, previousTurn, today);
        SemanticTimeContractCheck.Result timeContract =
                SemanticTimeContractCheck.check(sem, previousTurn, today);
        return new SemanticAdoptionAttempt(sem, merged, tentative, timeContract, null, null);
    }
}
