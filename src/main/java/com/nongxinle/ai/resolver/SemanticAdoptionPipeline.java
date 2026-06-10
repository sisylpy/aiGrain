package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.SemanticParseClarificationPolicy;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.TimeLayerContextSignals;
import com.nongxinle.ai.semantic.contract.SemanticContractViolationCode;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceApplier;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceDecision;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritancePolicy;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceRequest;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.SemanticContractClarificationQuestionFactory;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchPlan;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchSupport;
import com.nongxinle.ai.semantic.intake.*;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.frame.PurchaseCurrentSemanticFrameValidator;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 语义采纳编排：{@link SemanticSlotInheritancePolicy} → {@link SemanticSlotInheritanceApplier} →
 * ContractCompletion、SlotMerge（非继承）、采购 frame、MergeHelper、{@link SemanticTimeContractCheck}。
 * 逻辑与 {@link AiResolvedQueryContextResolver} 原 {@code trySemanticAdoption} 等价，仅搬移。
 *
 * <p>多轮 Business Frame 继承架构见 {@code docs/ai/semantic-inheritance-architecture.md}。
 *
 * <p><b>硬边界：</b>
 * <ul>
 *   <li>本 pipeline <b>不得</b>新增 previousTurn 业务 slots merge 或 per-domain if/else 补丁。</li>
 *   <li>继承决策与 Catalog 派生必须在 Policy/Applier 完成；<b>不得</b>在此用 previousTurn 覆盖
 *       当前轮 sovereign ACTIVE contract。</li>
 *   <li><b>不得</b>字段级拼装 Business Frame；禁止 {@code contains} / alias 猜业务语义。</li>
 *   <li>{@link AiQuerySemanticSlotMerge} 仅用于 wire 镜像、anchor、memory 对齐，不是 inherit 主链。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
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
            SemanticDomainRouteResult intakeSemanticDomainRoute,
            SemanticIntakeResult semanticIntake,
            BareRankingDimensionSwitchPlan bareRankingDimensionSwitchPlan,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName,
            String rewriteInheritedAnchorEntityId,
            List<Map<String, String>> rewriteUsedAnchors,
            TimeLayerContextSignals timeLayerContextSignals,
            Long distributerId,
            AiResolvedOrgScope orgScope) {}

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
                request.intakeSemanticDomainRoute(),
                request.semanticIntake(),
                request.bareRankingDimensionSwitchPlan(),
                request.rewriteInheritedAnchorType(),
                request.rewriteInheritedAnchorName(),
                request.rewriteInheritedAnchorEntityId(),
                request.rewriteUsedAnchors(),
                request.timeLayerContextSignals(),
                request.distributerId(),
                request.orgScope());
    }

    SemanticAdoptionAttempt tryAdopt(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalized,
            LocalDate today,
            AiResolvedTimeWindow explicitTentative,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection,
            SemanticDomainRouteResult intakeSemanticDomainRoute,
            SemanticIntakeResult semanticIntake,
            BareRankingDimensionSwitchPlan bareRankingDimensionSwitchPlan,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName,
            String rewriteInheritedAnchorEntityId,
            List<Map<String, String>> rewriteUsedAnchors,
            TimeLayerContextSignals timeLayerContextSignals,
            Long distributerId,
            AiResolvedOrgScope orgScope) {
        if (sem == null || SemanticParseClarificationPolicy.needSemanticParseClarification(sem, querySemanticMinConfidence)) {
            return null;
        }

        DomainContractSelectionResult effectiveContractSelection = contractSelection;

        SemanticSlotInheritanceDecision inheritanceDecision =
                SemanticSlotInheritancePolicy.decide(
                        SemanticSlotInheritanceRequest.builder()
                                .currentParse(sem)
                                .previousTurn(previousTurn)
                                .contractSelection(contractSelection)
                                .followUpRewriteApplied(followUpRewriteApplied)
                                .bareRankingDimensionSwitchPlan(bareRankingDimensionSwitchPlan)
                                .semanticIntake(semanticIntake)
                                .build());
        sem = SemanticSlotInheritanceApplier.apply(sem, previousTurn, inheritanceDecision);
        TimeLayerContextSignals effectiveTimeLayerSignals =
                TimeLayerContextSignals.from(semanticIntake, inheritanceDecision);

        SemanticIntakeResult canonicalIntake = semanticIntake;

        DomainContractSelectionResult completionSelection =
                BareRankingDimensionSwitchSupport.contractSelectionForPlan(
                        bareRankingDimensionSwitchPlan, contractSelection);
        effectiveContractSelection = completionSelection;

        sem = AiQuerySemanticSlotMerge.reconcileExplicitCurrentTurnDishAnchor(sem, previousTurn);

        AiQuerySemanticParseResult rawForDebug = sem;
        DomainContractSelectionResult completionSelectionResolved = completionSelection;
        SemanticContractCompletionEngine.Result completion =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(sem)
                                .selectedDomain(
                                        completionSelectionResolved != null
                                                ? completionSelectionResolved.getSelectedDomain()
                                                : null)
                                .contractSelection(completionSelectionResolved)
                                .previousTurn(previousTurn)
                                .anchorDate(today)
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
            return outcome(
                    intakeSemanticDomainRoute,
                    effectiveContractSelection,
                    canonicalIntake,
                    preserveV2RepairTrace(rawForDebug, sem),
                    null,
                    null,
                    null,
                    "contract_completion:" + completion.getViolationReason(),
                    question,
                    completion.getViolationCode());
        }
        sem = completion.getCompletedParse();
        sem = WarehouseInventoryShortageSemanticsSupport.applyRiskClarificationToParse(sem, canonicalIntake);
        if (Boolean.TRUE.equals(sem.getNeedClarification())
                && !com.nongxinle.ai.semantic.intake.SemanticIntakeDishIngredientCoverDaysSupport
                        .mustNotApplyWarehouseInventoryShortagePipeline(canonicalIntake, sem)
                && WarehouseInventoryShortageSemanticsSupport.intakeSignalsInventoryShortageSemantics(
                        canonicalIntake)
                && !WarehouseInventoryShortageSemanticsSupport.CONTRACT_INVENTORY_RISK_LIST.equals(
                        SemanticContractCompletionEngine.extractSelectedContractId(sem))) {
            String riskQuestion =
                    WarehouseInventoryShortageSemanticsSupport.resolveClarificationQuestion(
                            canonicalIntake);
            if (StringUtils.hasText(riskQuestion)) {
                return outcome(
                        intakeSemanticDomainRoute,
                        effectiveContractSelection,
                        canonicalIntake,
                        preserveV2RepairTrace(rawForDebug, sem),
                        null,
                        null,
                        null,
                        "warehouse_inventory_risk_clarification",
                        riskQuestion,
                        null);
            }
        }
        if (rawForDebug != sem) {
            Map<String, Object> trace =
                    sem.getContractCompletionTrace() != null
                            ? new LinkedHashMap<>(sem.getContractCompletionTrace())
                            : new LinkedHashMap<>();
            trace.put("contractCompletionApplied", true);
            sem = preserveV2RepairTrace(rawForDebug, sem.toBuilder().contractCompletionTrace(trace).build());
        } else {
            sem = preserveV2RepairTrace(rawForDebug, sem);
        }
        if (SemanticContractCompletionEngine.isLegacyNoCatalogParse(sem)) {
            Map<String, Object> trace =
                    sem.getContractCompletionTrace() != null
                            ? new LinkedHashMap<>(sem.getContractCompletionTrace())
                            : new LinkedHashMap<>();
            trace.put("adoptionPath", "legacy_no_catalog");
            sem = sem.toBuilder().contractCompletionTrace(trace).build();
        }

        boolean planActive =
                bareRankingDimensionSwitchPlan != null && bareRankingDimensionSwitchPlan.isActive();
        boolean contractLocked = SemanticContractCompletionEngine.isContractLockedParse(sem);
        if (contractLocked && isContractLockedPurchaseDomain(sem, effectiveContractSelection)) {
            CurrentSemanticFrame frame = CurrentSemanticFrame.fromParseResult(sem);
            SemanticFrameValidationResult frameVal =
                    PurchaseCurrentSemanticFrameValidator.validate(
                            frame,
                            sem,
                            previousTurn,
                            normalized,
                            followUpRewriteApplied,
                            effectiveContractSelection,
                            orgScope);
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
                                : "purchase_capability_boundary";
                return outcome(
                        intakeSemanticDomainRoute,
                        effectiveContractSelection,
                        canonicalIntake,
                        preserveV2RepairTrace(rawForDebug, sem),
                        null,
                        null,
                        null,
                        frameRejectReason,
                        frameQuestion,
                        null);
            }
        }
        boolean purchaseFrameAdoption =
                !planActive
                        && !contractLocked
                        && !AiQuerySemanticLlmMergeHelper.currentTurnMapsToExplicitNonPurchasePath(sem)
                        && AiQuerySemanticLlmMergeHelper.shouldUsePurchaseSemanticFrameAdoption(sem)
                        && !AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem);
        boolean basicDomainContractEntryAdoption =
                !planActive
                        && BasicDomainContractEntryAdoptionSupport.shouldRunBasicDomainContractEntryAdoption(
                                sem, contractSelection, contractLocked, purchaseFrameAdoption);
        if (purchaseFrameAdoption) {
            sem =
                    AiQuerySemanticSlotMerge.reconcilePurchaseCompleteUtteranceDefaults(
                            sem, followUpRewriteApplied);
            sem =
                    CurrentSemanticFrame.canonicalizePurchaseFollowUp(
                            sem, followUpRewriteApplied ? null : previousTurn);
            CurrentSemanticFrame frame = CurrentSemanticFrame.buildFrame(sem);
            SemanticFrameValidationResult frameVal =
                    PurchaseCurrentSemanticFrameValidator.validate(
                            frame, sem, previousTurn, normalized, followUpRewriteApplied, contractSelection, orgScope);
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
                return outcome(
                        intakeSemanticDomainRoute,
                        effectiveContractSelection,
                        canonicalIntake,
                        preserveV2RepairTrace(rawForDebug, sem),
                        null,
                        null,
                        null,
                        frameRejectReason,
                        frameQuestion,
                        null);
            }
            sem = AiQuerySemanticSlotMerge.reconcileSemanticSlotsViaCapabilityMatrices(sem);
            sem.setPurchaseSemanticFramePrimaryMerge(true);
        } else if (basicDomainContractEntryAdoption) {
            SemanticFrameValidationResult frameVal =
                    BasicDomainContractEntryAdoptionSupport.validateBasicDomainContractEntry(
                            sem,
                            previousTurn,
                            normalized,
                            followUpRewriteApplied,
                            contractSelection,
                            canonicalIntake);
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
                                : "basic_domain_contract_entry_validation";
                return outcome(
                        intakeSemanticDomainRoute,
                        effectiveContractSelection,
                        canonicalIntake,
                        preserveV2RepairTrace(rawForDebug, sem),
                        null,
                        null,
                        null,
                        frameRejectReason,
                        frameQuestion,
                        null);
            }
        } else if (!contractLocked && !planActive) {
            sem = AiQuerySemanticSlotMerge.reconcileSemanticSlotsViaCapabilityMatrices(sem);
        }
        AiResolvedQueryIntent baseline = AiResolvedQueryIntent.builder().build();
        AiResolvedQueryIntent merged =
                AiQuerySemanticLlmMergeHelper.mergeIntent(
                        baseline, sem, querySemanticMinConfidence, normalized, previousTurn);
        if (planActive) {
            sem =
                    BareRankingDimensionSwitchSupport.enforcePlanSovereignFrame(
                            sem, bareRankingDimensionSwitchPlan);
            merged =
                    AiQuerySemanticLlmMergeHelper.mergeIntent(
                            baseline, sem, querySemanticMinConfidence, normalized, previousTurn);
        }
        if (!StringUtils.hasText(merged.getPathCode())) {
            return outcome(
                    intakeSemanticDomainRoute,
                    effectiveContractSelection,
                    canonicalIntake,
                    preserveV2RepairTrace(rawForDebug, sem),
                    null,
                    null,
                    null,
                    "v2_no_routable_path",
                    null,
                    null);
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
                        sem, previousTurn, today, effectiveTimeLayerSignals);
        SemanticTimeContractCheck.Result timeContract =
                SemanticTimeContractCheck.check(sem, previousTurn, today);
        return outcome(
                intakeSemanticDomainRoute,
                effectiveContractSelection,
                canonicalIntake,
                preserveV2RepairTrace(rawForDebug, sem),
                merged,
                tentative,
                timeContract,
                null,
                null,
                null);
    }

    private static SemanticAdoptionAttempt outcome(
            SemanticDomainRouteResult intakeSemanticDomainRoute,
            DomainContractSelectionResult effectiveContractSelection,
            SemanticIntakeResult intakeForRoute,
            AiQuerySemanticParseResult semantic,
            AiResolvedQueryIntent mergedIntent,
            AiResolvedTimeWindow tentativeTime,
            SemanticTimeContractCheck.Result timeContract,
            String rejectionReason,
            String semanticClarificationQuestion,
            SemanticContractViolationCode contractViolationCode) {
        SemanticDomainRouteResult effectiveRoute =
                SemanticAdoptionEffectiveSupport.routeForDomainSwitch(
                        intakeSemanticDomainRoute, effectiveContractSelection, intakeForRoute);
        return new SemanticAdoptionAttempt(
                semantic,
                mergedIntent,
                tentativeTime,
                timeContract,
                rejectionReason,
                semanticClarificationQuestion,
                contractViolationCode,
                effectiveRoute,
                effectiveContractSelection);
    }

    private static AiQuerySemanticParseResult preserveV2RepairTrace(
            AiQuerySemanticParseResult anchor, AiQuerySemanticParseResult sem) {
        if (anchor == null || sem == null || !Boolean.TRUE.equals(anchor.getQuerySemanticV2RepairAttempted())) {
            return sem;
        }
        if (Boolean.TRUE.equals(sem.getQuerySemanticV2RepairAttempted())) {
            return sem;
        }
        return sem.toBuilder()
                .querySemanticV2RepairAttempted(anchor.getQuerySemanticV2RepairAttempted())
                .querySemanticV2RepairSuccess(anchor.getQuerySemanticV2RepairSuccess())
                .querySemanticV2RepairReason(anchor.getQuerySemanticV2RepairReason())
                .build();
    }

    private static Integer resolveDisId(Long distributerId) {
        if (distributerId == null || distributerId <= 0) {
            return null;
        }
        return distributerId.intValue();
    }

    private static boolean isContractLockedPurchaseDomain(
            AiQuerySemanticParseResult sem, DomainContractSelectionResult contractSelection) {
        if (contractSelection != null && StringUtils.hasText(contractSelection.getSelectedDomain())) {
            if ("PURCHASE".equalsIgnoreCase(contractSelection.getSelectedDomain().trim())) {
                return true;
            }
        }
        String contractId = SemanticContractCompletionEngine.extractSelectedContractId(sem);
        return StringUtils.hasText(contractId) && contractId.trim().startsWith("purchase.");
    }
}
