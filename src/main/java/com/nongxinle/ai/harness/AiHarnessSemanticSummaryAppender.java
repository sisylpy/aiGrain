package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.SemanticContractStrictBlockerCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractStrictDecision;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.resolver.AiResolvedQueryContextDebugFactory;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeSubQuestion;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidatorRegistry;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidatorRegistry.HarnessSemanticFrameValidation;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 语义解析 / V2 / scope merge debug / querySemanticLlm 观测字段。
 */
final class AiHarnessSemanticSummaryAppender {

    private AiHarnessSemanticSummaryAppender() {
    }

    static void appendSemanticFields(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        AiQuerySemanticParseResult qsp = ctx.getQuerySemanticParse();
        out.put("querySemanticLlm", summarizeQuerySemanticParse(qsp));
        appendSemanticFrameHarnessDebug(out, ctx);
        out.put("needSemanticClarification", ctx.isNeedSemanticClarification());
        out.put("semanticClarificationQuestion", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticClarificationQuestion()));
        if (qsp != null && !qsp.isParseMissing()) {
            List<String> eff = qsp.effectiveMentionedStoreNames();
            out.put("querySemanticEffectiveMentionedStoreNames",
                    eff == null || eff.isEmpty() ? null : new ArrayList<>(eff));
        } else {
            out.put("querySemanticEffectiveMentionedStoreNames", null);
        }
        out.put("semanticPrimaryVersion", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticPrimaryVersion()));
        out.put("semanticFallbackUsed", ctx.getSemanticFallbackUsed());
        out.put("semanticFallbackReason", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticFallbackReason()));
        out.put("semanticFailureCode", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticFailureCode()));
        out.put("semanticFailureStage", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticFailureStage()));
        out.put("semanticAdoptedFrom", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticAdoptedFrom()));
        out.put("purchaseSemanticFramePrimaryMerge", ctx.getPurchaseSemanticFramePrimaryMerge());
        List<String> adoptedFields = ctx.getSemanticAdoptedFields();
        out.put(
                "semanticAdoptedFields",
                adoptedFields == null || adoptedFields.isEmpty() ? null : new ArrayList<>(adoptedFields));
        List<String> deprecatedDebugFields =
                AiResolvedQueryContextDebugFactory.describeDeprecatedDebugSemanticFields(qsp);
        out.put(
                "semanticDeprecatedDebugFields",
                deprecatedDebugFields == null || deprecatedDebugFields.isEmpty()
                        ? null
                        : new ArrayList<>(deprecatedDebugFields));
        List<String> rejectedFields = ctx.getSemanticAdoptionRejectedFields();
        out.put(
                "semanticAdoptionRejectedFields",
                rejectedFields == null || rejectedFields.isEmpty() ? null : new ArrayList<>(rejectedFields));
        out.put("semanticAdoptionRejectedReason", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticAdoptionRejectedReason()));
        out.put("semanticMetricNormalizedFrom", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticMetricNormalizedFrom()));
        out.put("semanticMetricNormalizedTo", AiHarnessSummaryUtils.blankToNull(ctx.getSemanticMetricNormalizedTo()));
        out.put(
                "semanticV2AbstractIntentNormalizationNotes",
                AiHarnessSummaryUtils.jsonDeepCopyMap(ctx.getSemanticV2AbstractIntentNormalizationNotes()));
        Map<String, Object> v2NormNotes = ctx.getSemanticV2AbstractIntentNormalizationNotes();
        if (v2NormNotes != null && v2NormNotes.containsKey("degradedBusinessCompareByRevenue")) {
            out.put("degradedBusinessCompareByRevenue", v2NormNotes.get("degradedBusinessCompareByRevenue"));
        }
        out.put("querySemanticV2InputPreview", AiHarnessSummaryUtils.compactQuerySemanticV2InputPreview(ctx.getQuerySemanticV2InputPreview()));
        out.put("querySemanticV2", AiHarnessSummaryUtils.jsonDeepCopyMap(ctx.getQuerySemanticV2()));
        out.put("querySemanticV2ParseMissing", ctx.getQuerySemanticV2ParseMissing());
        out.put("querySemanticV2Confidence", ctx.getQuerySemanticV2Confidence());
        out.put("querySemanticV2TimeAction", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2TimeAction()));
        out.put("rawUserMessage", AiHarnessSummaryUtils.blankToNull(ctx.getRawUserMessage()));
        out.put("followUpRewriteApplied", ctx.getFollowUpRewriteApplied());
        out.put("completedUserQuery", AiHarnessSummaryUtils.blankToNull(ctx.getCompletedUserQuery()));
        out.put("followUpRewriteReason", AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpRewriteReason()));
        out.put(
                "followUpRewriteDebug",
                AiHarnessSummaryUtils.jsonDeepCopyMap(ctx.getFollowUpRewriteDebug()));
        out.put(
                "bareRankingDimensionSwitchDebug",
                AiHarnessSummaryUtils.jsonDeepCopyMap(ctx.getBareRankingDimensionSwitchDebug()));
        out.put("rewriteInheritedTime", ctx.getRewriteInheritedTime());
        out.put("rewriteInheritedScope", ctx.getRewriteInheritedScope());
        out.put("rewriteInheritedAnchorType", AiHarnessSummaryUtils.blankToNull(ctx.getRewriteInheritedAnchorType()));
        out.put("rewriteInheritedAnchorName", AiHarnessSummaryUtils.blankToNull(ctx.getRewriteInheritedAnchorName()));
        out.put(
                "rewriteInheritedAnchorEntityId",
                AiHarnessSummaryUtils.blankToNull(ctx.getRewriteInheritedAnchorEntityId()));
        out.put(
                "followUpRewriteClarificationQuestion",
                AiHarnessSummaryUtils.blankToNull(ctx.getFollowUpRewriteClarificationQuestion()));
        out.put("rewriteUsedAnchors", copyRewriteUsedAnchors(ctx.getRewriteUsedAnchors()));
        appendEntityExistenceGroundingDebug(out, ctx);
        out.put("previousTurnResultAnchorsCount", ctx.getPreviousTurnResultAnchorsCount());
        out.put("rewritePromptResultAnchorsCount", ctx.getRewritePromptResultAnchorsCount());
        out.put("querySemanticV2ScopeAction", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2ScopeAction()));
        out.put("querySemanticV2IntentAction", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2IntentAction()));
        out.put("querySemanticV2MetricAction", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2MetricAction()));
        List<String> v2stores = ctx.getQuerySemanticV2MentionedStoreNames();
        out.put(
                "querySemanticV2MentionedStoreNames",
                v2stores == null || v2stores.isEmpty() ? null : new ArrayList<>(v2stores));
        out.put("querySemanticV2MentionedDishName", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2MentionedDishName()));
        out.put("querySemanticV2RawText", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2RawText()));
        out.put("querySemanticV2ParseError", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2ParseError()));
        if (Boolean.TRUE.equals(ctx.getQuerySemanticV2RepairAttempted())) {
            out.put("querySemanticV2RepairAttempted", true);
            out.put("querySemanticV2RepairSuccess", Boolean.TRUE.equals(ctx.getQuerySemanticV2RepairSuccess()));
            out.put(
                    "querySemanticV2RepairReason",
                    AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2RepairReason()));
        }
        appendMultiTurnInheritanceDebug(out, ctx);
        appendScopeMergeDebugFields(out, ctx);
        appendSemanticIntakeDebug(out, ctx);
        appendSemanticDomainRoutingDebug(out, ctx);
        appendSemanticContractCatalogSummary(out);
    }

    private static void appendSemanticIntakeDebug(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        SemanticIntakeResult intake = ctx != null ? ctx.getSemanticIntake() : null;
        if (intake == null) {
            out.put("intakeStatus", null);
            out.put("questionMode", null);
            out.put("normalizationType", null);
            out.put("canonicalUserQuery", null);
            out.put("intakeIsFollowUp", null);
            out.put("intakeUsedPreviousContext", null);
            out.put("intakePrimaryDomain", null);
            out.put("intakeCandidateDomains", null);
            out.put("intakeRouteType", null);
            out.put("intakeConfidence", null);
            out.put("intakeNeedClarification", null);
            out.put("intakeClarificationQuestion", null);
            out.put("intakeReason", null);
            out.put("intakeParseError", null);
            out.put("intakeLlmRawText", null);
            out.put("intakeFailureCode", null);
            out.put("intakeFailureStage", null);
            out.put("intakeSubQuestionsCount", null);
            return;
        }
        out.put("intakeStatus", intake.getStatus() != null ? intake.getStatus().name() : null);
        out.put(
                "questionMode",
                intake.getQuestionMode() != null ? intake.getQuestionMode().name() : null);
        out.put(
                "normalizationType",
                intake.getNormalizationType() != null ? intake.getNormalizationType().name() : null);
        out.put("canonicalUserQuery", AiHarnessSummaryUtils.blankToNull(intake.getCanonicalUserQuery()));
        out.put("intakeIsFollowUp", intake.getIsFollowUp());
        out.put("intakeUsedPreviousContext", intake.getUsedPreviousContext());
        out.put("intakePrimaryDomain", AiHarnessSummaryUtils.blankToNull(intake.getPrimaryDomain()));
        List<String> intakeCandidates = intake.getCandidateDomains();
        out.put(
                "intakeCandidateDomains",
                intakeCandidates == null || intakeCandidates.isEmpty()
                        ? null
                        : new ArrayList<>(intakeCandidates));
        out.put("intakeRouteType", AiHarnessSummaryUtils.blankToNull(intake.getRouteType()));
        out.put("intakeConfidence", intake.getConfidence());
        out.put("intakeNeedClarification", intake.getNeedClarification());
        out.put(
                "intakeClarificationQuestion",
                AiHarnessSummaryUtils.blankToNull(intake.getClarificationQuestion()));
        out.put("intakeReason", AiHarnessSummaryUtils.blankToNull(intake.getReason()));
        out.put("intakeParseError", AiHarnessSummaryUtils.blankToNull(intake.getParseError()));
        out.put("intakeLlmRawText", AiHarnessSummaryUtils.blankToNull(intake.getLlmRawText()));
        out.put("intakeFailureCode", AiHarnessSummaryUtils.blankToNull(intake.getFailureCode()));
        out.put("intakeFailureStage", AiHarnessSummaryUtils.blankToNull(intake.getFailureStage()));
        List<SemanticIntakeSubQuestion> subs = intake.getSubQuestions();
        out.put("intakeSubQuestionsCount", subs == null ? null : subs.size());
    }

    /** P2：Router + ContractSelector + 合同观测（主链已接入；Validator 暂不强拦截）。 */
    private static void appendSemanticDomainRoutingDebug(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        if (ctx == null) {
            out.put("semanticDomainRoute", null);
            out.put("domainContractSelection", null);
            out.put("effectiveSemanticDomainRoute", null);
            out.put("effectiveDomainContractSelection", null);
            out.put("semanticContractValidation", null);
            out.put("semanticContractStrictDecision", null);
            out.put("querySemanticV2Domain", null);
            out.put("routeParserDomainMismatch", null);
            out.put("routeParserDomainMismatchReason", null);
            return;
        }
        out.put("semanticDomainRoute", mapDomainRoute(ctx.getSemanticDomainRoute()));
        out.put("domainContractSelection", mapDomainContractSelection(ctx.getDomainContractSelection()));
        out.put("effectiveSemanticDomainRoute", mapDomainRoute(ctx.getEffectiveSemanticDomainRoute()));
        out.put(
                "effectiveDomainContractSelection",
                mapDomainContractSelection(ctx.getEffectiveDomainContractSelection()));
        out.put("semanticContractValidation", mapContractValidation(ctx.getSemanticContractValidation()));
        out.put("semanticContractStrictDecision", mapStrictDecision(ctx.getSemanticContractStrictDecision()));
        out.put("querySemanticV2Domain", AiHarnessSummaryUtils.blankToNull(ctx.getQuerySemanticV2Domain()));
        out.put("routeParserDomainMismatch", ctx.getRouteParserDomainMismatch());
        out.put(
                "routeParserDomainMismatchReason",
                AiHarnessSummaryUtils.blankToNull(ctx.getRouteParserDomainMismatchReason()));
    }

    private static Map<String, Object> mapDomainRoute(SemanticDomainRouteResult route) {
        if (route == null) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("routeType", route.getRouteType() != null ? route.getRouteType().name() : null);
        m.put("primaryDomain", AiHarnessSummaryUtils.blankToNull(route.getPrimaryDomain()));
        List<String> candidates = route.getCandidateDomains();
        m.put(
                "candidateDomains",
                candidates == null || candidates.isEmpty() ? null : new ArrayList<>(candidates));
        m.put("confidence", route.getConfidence());
        List<String> reasons = route.getReasonCodes();
        m.put("reasonCodes", reasons == null || reasons.isEmpty() ? null : new ArrayList<>(reasons));
        List<String> matched = route.getMatchedBusinessObjects();
        m.put(
                "matchedBusinessObjects",
                matched == null || matched.isEmpty() ? null : new ArrayList<>(matched));
        m.put("usedPreviousContext", route.isUsedPreviousContext() ? Boolean.TRUE : null);
        m.put("needsClarification", route.isNeedsClarification() ? Boolean.TRUE : null);
        return m;
    }

    private static Map<String, Object> mapDomainContractSelection(DomainContractSelectionResult selection) {
        if (selection == null) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("selectedDomain", AiHarnessSummaryUtils.blankToNull(selection.getSelectedDomain()));
        m.put("selectedCapabilityContractCount", selection.getSelectedCapabilityContractCount());
        m.put("selectedActiveContractCount", selection.getSelectedActiveContractCount());
        m.put("selectedKnownGapCount", selection.getSelectedKnownGapCount());
        m.put("capabilityContractMissing", selection.isCapabilityContractMissing() ? Boolean.TRUE : null);
        m.put(
                "contractSelectionSkippedReason",
                AiHarnessSummaryUtils.blankToNull(selection.getContractSelectionSkippedReason()));
        return m;
    }

    private static Map<String, Object> mapContractValidation(SemanticContractValidationDebug validation) {
        if (validation == null) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("modelContractViolation", validation.isModelContractViolation() ? Boolean.TRUE : null);
        m.put("unsupportedWire", AiHarnessSummaryUtils.blankToNull(validation.getUnsupportedWire()));
        m.put(
                "violationCode",
                validation.getViolationCode() != null ? validation.getViolationCode().name() : null);
        m.put("selectedDomain", AiHarnessSummaryUtils.blankToNull(validation.getSelectedDomain()));
        m.put("matchedContractId", AiHarnessSummaryUtils.blankToNull(validation.getMatchedContractId()));
        m.put(
                "violationReason",
                AiHarnessSummaryUtils.blankToNull(validation.getViolationReason()));
        m.put("allowedContractCount", validation.getAllowedContractCount());
        List<String> wires = validation.getAllowedWires();
        m.put("allowedWires", wires == null || wires.isEmpty() ? null : new ArrayList<>(wires));
        List<String> missing = validation.getMissingSlots();
        m.put("missingSlots", missing == null || missing.isEmpty() ? null : new ArrayList<>(missing));
        return m;
    }

    private static Map<String, Object> mapStrictDecision(SemanticContractStrictDecision decision) {
        if (decision == null) {
            return null;
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("strictEnabled", decision.isStrictEnabled() ? Boolean.TRUE : null);
        m.put("modelContractViolation", decision.isModelContractViolation() ? Boolean.TRUE : null);
        m.put("enforceClarification", decision.isEnforceClarification() ? Boolean.TRUE : null);
        m.put(
                "violationCode",
                decision.getViolationCode() != null ? decision.getViolationCode().name() : null);
        m.put("violationReason", AiHarnessSummaryUtils.blankToNull(decision.getViolationReason()));
        m.put("selectedDomain", AiHarnessSummaryUtils.blankToNull(decision.getSelectedDomain()));
        m.put("unsupportedWire", AiHarnessSummaryUtils.blankToNull(decision.getUnsupportedWire()));
        m.put("matchedContractId", AiHarnessSummaryUtils.blankToNull(decision.getMatchedContractId()));
        m.put("allowedContractCount", decision.getAllowedContractCount());
        m.put(
                "clarificationQuestion",
                AiHarnessSummaryUtils.blankToNull(decision.getClarificationQuestion()));
        List<String> missing = decision.getMissingSlots();
        m.put("missingSlots", missing == null || missing.isEmpty() ? null : new ArrayList<>(missing));
        List<String> candidates = decision.getCandidateDomains();
        m.put(
                "candidateDomains",
                candidates == null || candidates.isEmpty() ? null : new ArrayList<>(candidates));
        List<String> wires = decision.getAllowedWires();
        m.put("allowedWires", wires == null || wires.isEmpty() ? null : new ArrayList<>(wires));
        List<String> blockers = decision.getActiveStrictBlockers();
        m.put(
                "activeStrictBlockers",
                blockers == null || blockers.isEmpty() ? null : new ArrayList<>(blockers));
        List<String> deprecatedBlockers = decision.getDeprecatedStrictBlockers();
        m.put(
                "deprecatedStrictBlockers",
                deprecatedBlockers == null || deprecatedBlockers.isEmpty()
                        ? null
                        : new ArrayList<>(deprecatedBlockers));
        return m;
    }

    /** 静态合同目录计数（不含全量合同列表；域路由见 {@code semanticDomainRoute} / {@code domainContractSelection}）。 */
    private static void appendSemanticContractCatalogSummary(LinkedHashMap<String, Object> out) {
        Map<String, Object> catalog = SemanticContractCatalog.dump();
        Map<String, Object> blockers = SemanticContractStrictBlockerCatalog.dump();
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("domainRoutingContractCount", catalog.get("domainRoutingContractCount"));
        summary.put("totalCapabilityContractCount", catalog.get("totalCapabilityContractCount"));
        summary.put("activeCapabilityCountByDomain", catalog.get("activeCapabilityCountByDomain"));
        summary.put("knownGapCapabilityCountByDomain", catalog.get("knownGapCapabilityCountByDomain"));
        summary.put("strictBlockerCount", blockers.get("blockerCount"));
        summary.put("activeStrictBlockerCount", blockers.get("activeBlockerCount"));
        out.put("semanticContractCatalogSummary", summary);
    }

    private static void appendMultiTurnInheritanceDebug(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return;
        }
        AiQuerySemanticParseResult qsp = ctx.getQuerySemanticParse();
        if (qsp != null && qsp.getMultiTurnInheritanceTrace() != null) {
            out.put("multiTurnInheritanceTrace", AiHarnessSummaryUtils.jsonDeepCopyMap(qsp.getMultiTurnInheritanceTrace()));
        } else {
            out.put("multiTurnInheritanceTrace", null);
        }
        if (qsp != null && qsp.getTime() != null) {
            String ts = qsp.getTime().getTimeSource();
            out.put(
                    "inheritedTime",
                    SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS.equals(
                            ts != null ? ts.trim() : null));
        } else {
            out.put("inheritedTime", null);
        }
        if (qsp != null && qsp.getRequestedScope() != null) {
            String ss = qsp.getRequestedScope().getScopeSource();
            boolean inheritedScope =
                    "INHERITED_PREVIOUS".equals(ss != null ? ss.trim() : null)
                            || Boolean.TRUE.equals(qsp.getRequestedScope().getNeedInheritFromPrevious());
            out.put("inheritedScope", inheritedScope);
        } else {
            out.put("inheritedScope", null);
        }
        boolean inheritedEntityAnchor = false;
        if (qsp != null && qsp.getMultiTurnInheritanceTrace() != null) {
            Object v = qsp.getMultiTurnInheritanceTrace().get("inheritedEntityAnchor");
            inheritedEntityAnchor = Boolean.TRUE.equals(v);
        }
        out.put("inheritedEntityAnchor", inheritedEntityAnchor ? true : null);
        if (ctx.getQueryIntent() != null) {
            out.put("inheritedFromPreviousIntent", ctx.getQueryIntent().isInheritedFromPreviousTurn());
        } else {
            out.put("inheritedFromPreviousIntent", null);
        }
    }

    private static void appendSemanticFrameHarnessDebug(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        HarnessSemanticFrameValidation harness = CurrentSemanticFrameValidatorRegistry.validateForHarness(ctx);
        if (harness == null) {
            out.put("currentSemanticFrame", null);
            out.put("semanticFrameValidation", null);
            return;
        }
        var frame = harness.frame();
        SemanticFrameValidationResult val = harness.result();
        AiQuerySemanticParseResult qsp = ctx.getQuerySemanticParse();
        LinkedHashMap<String, Object> frameMap = new LinkedHashMap<>();
        frameMap.put("queryObject", AiHarnessSummaryUtils.blankToNull(frame.getQueryObject()));
        frameMap.put("operation", AiHarnessSummaryUtils.blankToNull(frame.getOperation()));
        frameMap.put("metric", AiHarnessSummaryUtils.blankToNull(frame.getMetric()));
        frameMap.put("sourceFacet", AiHarnessSummaryUtils.blankToNull(frame.getSourceFacet()));
        frameMap.put("anchorPolicy", AiHarnessSummaryUtils.blankToNull(frame.getAnchorPolicy()));
        frameMap.put("detailWanted", AiHarnessSummaryUtils.blankToNull(frame.getDetailWanted()));
        frameMap.put("structuredIntentDetailWire", AiHarnessSummaryUtils.blankToNull(frame.getStructuredIntentDetailWire()));
        if (qsp != null && qsp.getSemanticSlots() != null) {
            frameMap.put("answerPlanType", AiHarnessSummaryUtils.blankToNull(qsp.getSemanticSlots().getAnswerPlanType()));
            frameMap.put(
                    "expiryRiskFilter",
                    AiHarnessSummaryUtils.blankToNull(qsp.getSemanticSlots().getExpiryRiskFilter()));
        } else {
            frameMap.put("answerPlanType", null);
            frameMap.put("expiryRiskFilter", null);
        }
        LinkedHashMap<String, Object> valMap = new LinkedHashMap<>();
        valMap.put("validationDomain", AiHarnessSummaryUtils.blankToNull(harness.validationDomain()));
        valMap.put("ok", val.ok());
        List<String> vc = val.violationCodes();
        valMap.put("violationCodes", vc == null || vc.isEmpty() ? null : new ArrayList<>(vc));
        valMap.put(
                "warningViolationCodes",
                val.ok() && vc != null && !vc.isEmpty() ? new ArrayList<>(vc) : null);
        valMap.put("needSemanticClarification", val.needSemanticClarification());
        valMap.put("semanticClarificationQuestion", AiHarnessSummaryUtils.blankToNull(val.semanticClarificationQuestion()));
        out.put("currentSemanticFrame", frameMap);
        out.put("semanticFrameValidation", valMap);
        if ("PURCHASE".equals(harness.validationDomain())) {
            appendPurchaseContractExportDebug(out);
        }
    }

    /** P1-B：Purchase 小合同计数（兼容字段）；详见 {@code semanticContractCatalogSummary}。 */
    private static void appendPurchaseContractExportDebug(LinkedHashMap<String, Object> out) {
        Map<String, Object> catalog = SemanticContractCatalog.dump();
        out.put("exportedPurchaseContractCount", catalog.get("purchaseCapabilityContractCount"));
        out.put("activePurchaseContractCount", catalog.get("activePurchaseCapabilityContractCount"));
        out.put("plannedPurchaseContractCount", catalog.get("plannedPurchaseCapabilityContractCount"));
        out.put("knownGapPurchaseContractCount", catalog.get("knownGapPurchaseCapabilityContractCount"));
        Object markers = catalog.get("knownGapPurchaseContractMarkers");
        out.put(
                "knownGapPurchaseContractMarkers",
                markers instanceof List && !((List<?>) markers).isEmpty()
                        ? new ArrayList<>((List<?>) markers)
                        : null);
    }
    private static Map<String, Object> summarizeQuerySemanticParse(AiQuerySemanticParseResult r) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        if (r == null) {
            m.put("parseMissing", true);
            return m;
        }
        m.put("parseMissing", r.isParseMissing());
        m.put("intent", AiHarnessSummaryUtils.blankToNull(r.getIntent()));
        m.put("domain", AiHarnessSummaryUtils.blankToNull(r.getSemanticDomain()));
        m.put("intentAction", AiHarnessSummaryUtils.blankToNull(r.getIntentAction()));
        m.put("timeAction", AiHarnessSummaryUtils.blankToNull(r.getTimeAction()));
        m.put("scopeAction", AiHarnessSummaryUtils.blankToNull(r.getScopeAction()));
        m.put("metricAction", AiHarnessSummaryUtils.blankToNull(r.getMetricAction()));
        m.put("isFollowUp", r.getFollowUp());
        m.put("purchaseSemanticFramePrimaryMerge", r.getPurchaseSemanticFramePrimaryMerge());
        m.put("confidence", r.getConfidence());
        m.put("mentionedDishName", AiHarnessSummaryUtils.blankToNull(r.effectiveMentionedDishName()));
        m.put("mentionedGoodsName", AiHarnessSummaryUtils.blankToNull(r.effectiveMentionedGoodsName()));
        if (Boolean.TRUE.equals(r.getQuerySemanticV2RepairAttempted())) {
            m.put("querySemanticV2RepairAttempted", true);
            m.put("querySemanticV2RepairSuccess", Boolean.TRUE.equals(r.getQuerySemanticV2RepairSuccess()));
            m.put(
                    "querySemanticV2RepairReason",
                    AiHarnessSummaryUtils.blankToNull(r.getQuerySemanticV2RepairReason()));
            m.put(
                    "querySemanticV2ProtocolRelocateApplied",
                    r.getQuerySemanticV2RepairReason() != null
                            && r.getQuerySemanticV2RepairReason().contains("java_protocol_relocate"));
        }
        if (r.getContractCompletionTrace() != null && !r.getContractCompletionTrace().isEmpty()) {
            Map<String, Object> trace = new LinkedHashMap<>(r.getContractCompletionTrace());
            m.put("contractCompletionTrace", trace);
            m.put("contractEntryValidated", trace.get(SemanticContractCompletionEngine.TRACE_CONTRACT_ENTRY_VALIDATED));
            Object failureReason = trace.get("contractCompletionFailureReason");
            if (failureReason == null) {
                failureReason = trace.get("violationReason");
            }
            m.put("contractCompletionFailureReason", failureReason);
            Object slotMismatch = trace.get("slotMismatchFields");
            if (slotMismatch == null) {
                slotMismatch = trace.get("missingSlots");
            }
            m.put("slotMismatchFields", slotMismatch);
            m.put("mappedIntentCode", trace.get("intentCode"));
            m.put("mappedPathCode", trace.get("pathCode"));
            m.put("mappedPlanType", trace.get("answerPlanType"));
        }
        com.nongxinle.ai.semantic.contract.ContractExecutionMappingSupport.Mapping executionMapping =
                com.nongxinle.ai.semantic.contract.ContractExecutionMappingSupport.resolve(r);
        if (executionMapping != null && executionMapping.hasRoutableExecution()) {
            m.put("mappedIntentCode", executionMapping.getIntentCode());
            m.put("mappedPathCode", executionMapping.getPathCode());
            m.put("mappedPlanType", executionMapping.getAnswerPlanType());
        }
        if (r.getTime() != null) {
            LinkedHashMap<String, Object> t = new LinkedHashMap<>();
            t.put("timeType", AiHarnessSummaryUtils.blankToNull(r.getTime().getTimeType()));
            t.put("startDate", AiHarnessSummaryUtils.blankToNull(r.getTime().getStartDate()));
            t.put("endDate", AiHarnessSummaryUtils.blankToNull(r.getTime().getEndDate()));
            t.put("timeSource", AiHarnessSummaryUtils.blankToNull(r.getTime().getTimeSource()));
            t.put("needInheritFromPrevious", r.getTime().getNeedInheritFromPrevious());
            m.put("time", t);
        } else {
            m.put("time", null);
        }
        if (r.getRequestedScope() != null) {
            LinkedHashMap<String, Object> rs = new LinkedHashMap<>();
            rs.put("requestedScopeType", AiHarnessSummaryUtils.blankToNull(r.getRequestedScope().getRequestedScopeType()));
            rs.put(
                    "mentionedStoreName",
                    AiQuerySemanticParseResult.sanitizeMentionedStoreNameToken(
                            r.getRequestedScope().getMentionedStoreName()));
            rs.put("mentionedStoreNames", AiHarnessSummaryUtils.emptyToNullCopy(r.getRequestedScope().getMentionedStoreNames()));
            rs.put("mentionedDepartmentName", AiHarnessSummaryUtils.blankToNull(r.getRequestedScope().getMentionedDepartmentName()));
            rs.put("mentionedWarehouseName", AiHarnessSummaryUtils.blankToNull(r.getRequestedScope().getMentionedWarehouseName()));
            rs.put("scopeSource", AiHarnessSummaryUtils.blankToNull(r.getRequestedScope().getScopeSource()));
            rs.put("needInheritFromPrevious", r.getRequestedScope().getNeedInheritFromPrevious());
            m.put("requestedScope", rs);
        } else {
            m.put("requestedScope", null);
        }
        if (r.getMetric() != null) {
            LinkedHashMap<String, Object> met = new LinkedHashMap<>();
            met.put("primaryMetric", AiHarnessSummaryUtils.blankToNull(r.getMetric().getPrimaryMetric()));
            met.put("rankingType", AiHarnessSummaryUtils.blankToNull(r.getMetric().getRankingType()));
            met.put("rankingTypeDebugOnly", Boolean.TRUE);
            met.put("purchaseSourceType", AiHarnessSummaryUtils.blankToNull(r.getMetric().getPurchaseSourceType()));
            met.put("stockReduceType", AiHarnessSummaryUtils.blankToNull(r.getMetric().getStockReduceType()));
            m.put("metric", met);
        } else {
            m.put("metric", null);
        }
        m.put("needClarification", r.getNeedClarification());
        m.put("clarificationQuestion", AiHarnessSummaryUtils.blankToNull(r.getClarificationQuestion()));
        m.put("reason", AiHarnessSummaryUtils.blankToNull(r.getReason()));
        m.put("mentionedStoreNames", AiHarnessSummaryUtils.emptyToNullCopy(r.effectiveMentionedStoreNames()));
        if (r.getSemanticSlots() != null) {
            AiQuerySemanticParseResult.SemanticSlotsPart s = r.getSemanticSlots();
            LinkedHashMap<String, Object> slots = new LinkedHashMap<>();
            slots.put("selectedContractId", AiHarnessSummaryUtils.blankToNull(s.getSelectedContractId()));
            slots.put("queryObject", AiHarnessSummaryUtils.blankToNull(s.getQueryObject()));
            slots.put("operation", AiHarnessSummaryUtils.blankToNull(s.getOperation()));
            slots.put("metric", AiHarnessSummaryUtils.blankToNull(s.getMetric()));
            slots.put("sourceFacet", AiHarnessSummaryUtils.blankToNull(s.getSourceFacet()));
            slots.put("anchorPolicy", AiHarnessSummaryUtils.blankToNull(s.getAnchorPolicy()));
            slots.put("detailWanted", AiHarnessSummaryUtils.blankToNull(s.getDetailWanted()));
            slots.put("structuredIntentDetailWire", AiHarnessSummaryUtils.blankToNull(s.getStructuredIntentDetailWire()));
            slots.put("answerPlanType", AiHarnessSummaryUtils.blankToNull(s.getAnswerPlanType()));
            slots.put("mentionedDishName", AiHarnessSummaryUtils.blankToNull(s.getMentionedDishName()));
            slots.put("mentionedGoodsName", AiHarnessSummaryUtils.blankToNull(s.getMentionedGoodsName()));
            slots.put(
                    "requestedTargetGrossMarginRate",
                    AiHarnessSummaryUtils.blankToNull(s.getRequestedTargetGrossMarginRate()));
            slots.put(
                    "expiryRiskFilter",
                    AiHarnessSummaryUtils.blankToNull(s.getExpiryRiskFilter()));
            m.put("semanticSlots", slots);
            m.put("selectedContractId", AiHarnessSummaryUtils.blankToNull(s.getSelectedContractId()));
        } else {
            m.put("semanticSlots", null);
            m.put("selectedContractId", null);
        }
        return m;
    }
    private static void appendScopeMergeDebugFields(LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return;
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        AiConversationTurnMemory prev = ctx.getPreviousTurn();
        AiResolvedOrgScope org = ctx.getOrgScope();

        // ── LLM 原始语义解析结果 ──
        // rawIntentCode：LLM 原始 intent 字符串
        String rawIntent = sem != null ? AiHarnessSummaryUtils.blankToNull(sem.getIntent()) : null;
        out.put("rawIntentCode", rawIntent);

        // rawPathCode：从 LLM intent 映射的 pathCode（不在 LLM 输出中，通过 intent 推断）
        String rawPathCode = mapLlmIntentToPathCode(rawIntent);
        out.put("rawPathCode", rawPathCode);

        // rawStructuredIntentDetail：V2 本轮 structured wire（slots / currentTurn）；不用 metric.rankingType 作主观测
        String rawStructuredIntentDetail = null;
        if (sem != null) {
            if (StringUtils.hasText(sem.getCurrentTurnStructuredIntentDetailWire())) {
                rawStructuredIntentDetail = sem.getCurrentTurnStructuredIntentDetailWire().trim();
            } else if (sem.getSemanticSlots() != null
                    && StringUtils.hasText(sem.getSemanticSlots().getStructuredIntentDetailWire())) {
                rawStructuredIntentDetail = sem.getSemanticSlots().getStructuredIntentDetailWire().trim();
            }
        }
        out.put("rawStructuredIntentDetail", AiHarnessSummaryUtils.blankToNull(rawStructuredIntentDetail));

        // rawScopeAction：LLM 原始 scopeAction
        out.put("rawScopeAction", sem != null ? AiHarnessSummaryUtils.blankToNull(sem.getScopeAction()) : null);

        // rawMentionedStore：LLM 原始提到的门店名（effectiveMentionedStoreNames 第一个）
        List<String> mentionedStores = sem != null ? sem.effectiveMentionedStoreNames() : null;
        String rawMentionedStore = (mentionedStores != null && !mentionedStores.isEmpty())
                ? mentionedStores.get(0) : null;
        out.put("rawMentionedStore", AiHarnessSummaryUtils.blankToNull(rawMentionedStore));

        // rawSelectedTools：来自 orchestrationDecisionCandidate.selectedTools
        List<String> rawSelectedTools = null;
        if (sem != null && sem.getOrchestrationDecisionCandidate() != null) {
            rawSelectedTools = sem.getOrchestrationDecisionCandidate().getSelectedTools();
        }
        out.put("rawSelectedTools", rawSelectedTools == null || rawSelectedTools.isEmpty()
                ? null : new ArrayList<>(rawSelectedTools));

        // ── 合并过程字段 ──
        // previousScopeType：上一轮的 scopeType
        String previousScopeType = prev != null ? AiHarnessSummaryUtils.blankToNull(prev.getLastScopeType()) : null;
        out.put("previousScopeType", previousScopeType);

        // previousMentionedStore：上一轮最终确定的门店名
        String previousMentionedStore = resolvePreviousMentionedStore(prev, ctx);
        out.put("previousMentionedStore", previousMentionedStore);

        // currentScopeExplicit：当前是否显式声明了 scope（LLM 有 mentionedStoreNames 或 scopeAction）
        boolean currentScopeExplicit = (mentionedStores != null && !mentionedStores.isEmpty())
                || (sem != null && StringUtils.hasText(sem.getScopeAction()));
        out.put("currentScopeExplicit", currentScopeExplicit);

        // currentScopeSignal：scope 信号的来源
        String currentScopeSignal = deriveScopeSignal(ctx, sem, prev, currentScopeExplicit);
        out.put("currentScopeSignal", currentScopeSignal);

        // scopeOverrideReason：为什么覆盖/继承/未覆盖上一轮的 scope
        String scopeOverrideReason = deriveScopeOverrideReason(ctx, sem, prev, mentionedStores, currentScopeSignal, previousScopeType);
        out.put("scopeOverrideReason", scopeOverrideReason);

        // finalScopeType：最终的 scopeType
        String finalScopeType = org != null ? AiHarnessSummaryUtils.blankToNull(org.getScopeType()) : null;
        out.put("finalScopeType", finalScopeType);

        // finalMentionedStore：最终确定的 mentionedStore（用于对比）
        String finalMentionedStore = resolveFinalMentionedStore(ctx, org);
        out.put("finalMentionedStore", finalMentionedStore);
    }
    private static String mapLlmIntentToPathCode(String rawIntent) {
        if (!StringUtils.hasText(rawIntent)) {
            return null;
        }
        String u = rawIntent.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "BUSINESS_OVERVIEW", "OPERATIONS_OVERVIEW" -> "PATH_BUSINESS_OVERVIEW";
            case "REVENUE_OVERVIEW", "REVENUE" -> "PATH_REVENUE_OVERVIEW";
            case "PURCHASE_OVERVIEW", "PROCUREMENT_OVERVIEW", "PURCHASE" -> "PATH_PURCHASE_OVERVIEW";
            case "WAREHOUSE_STOCK_OVERVIEW", "STOCK_OVERVIEW", "WAREHOUSE_OVERVIEW", "STOCK_QUERY" -> "PATH_WAREHOUSE_STOCK";
            case "STOCK_REDUCE_QUERY", "STOCK_OUT", "WRITE_OFF" -> "PATH_STOCK_REDUCE_QUERY";
            case "DISH_PROFIT", "DISH_MARGIN" -> "PATH_DISH_PROFIT";
            case "DISH_SALES_QUERY" -> "PATH_DISH_SALES_QUERY";
            case "MENU_OPERATION" -> "PATH_MENU_OPERATION";
            case "COST_DIAGNOSIS", "COST_DIAG" -> "PATH_COST_DIAGNOSIS";
            case "BUSINESS_DIAGNOSIS" -> "PATH_BUSINESS_DIAGNOSIS";
            default -> null;
        };
    }
    private static String resolvePreviousMentionedStore(AiConversationTurnMemory prev, AiResolvedQueryContext ctx) {
        if (prev == null) {
            return null;
        }
        // 优先从 lastHarnessMultiStoreMatchedStores 获取上一轮门店名
        List<String> matchedStores = prev.getLastHarnessMultiStoreMatchedStores();
        if (matchedStores != null && !matchedStores.isEmpty()) {
            return String.join(",", matchedStores);
        }
        // 回退 1：从当前 visibleStores 中匹配上一轮 lastVisibleStoreIds
        List<Integer> prevStoreIds = prev.getLastVisibleStoreIds();
        AiResolvedOrgScope org = ctx.getOrgScope();
        if (prevStoreIds != null && !prevStoreIds.isEmpty() && org != null && org.getVisibleStores() != null) {
            List<String> foundNames = new ArrayList<>();
            for (AiStoreScopeDTO s : org.getVisibleStores()) {
                if (s != null && s.getStoreDepartmentId() != null
                        && prevStoreIds.contains(s.getStoreDepartmentId().intValue())) {
                    String name = s.getStoreName();
                    if (StringUtils.hasText(name)) {
                        foundNames.add(name.trim());
                    }
                }
            }
            if (!foundNames.isEmpty()) {
                return String.join(",", foundNames);
            }
        }
        // 回退 2：从 visibleStores 获取（当 previousScopeType=STORE 时返回唯一门店名）
        if (org != null && org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
            AiStoreScopeDTO s = org.getVisibleStores().get(0);
            if (s != null && StringUtils.hasText(s.getStoreName())) {
                return s.getStoreName().trim();
            }
        }
        return null;
    }
    private static String deriveScopeSignal(AiResolvedQueryContext ctx, AiQuerySemanticParseResult sem,
                                           AiConversationTurnMemory prev, boolean currentScopeExplicit) {
        if (ctx == null) {
            return null;
        }
        String effectiveScopeSource = ctx.getEffectiveScopeSource();
        // 当 currentScopeExplicit=true（LLM 显式声明 scope 覆盖）时，
        // 即使 effectiveScopeSource 为 INHERITED_PREVIOUS 也应覆盖为正确的信号。
        if (currentScopeExplicit && "INHERITED_PREVIOUS".equals(effectiveScopeSource)) {
            // LLM 显式声明了 OVERRIDE/NEW，但 ctx 中的 effectiveScopeSource 仍是 INHERITED_PREVIOUS
            // 说明 scope merge 层有 bug，此处显示真实信号供调试。
            return "SEMANTIC_OVERRIDE";
        }
        if (StringUtils.hasText(effectiveScopeSource)) {
            return effectiveScopeSource;
        }
        // 兜底逻辑
        if (currentScopeExplicit) {
            return "SEMANTIC_EXPLICIT";
        }
        if (sem != null && !sem.isParseMissing()) {
            String scopeAction = sem.getScopeAction();
            if (StringUtils.hasText(scopeAction)) {
                String norm = scopeAction.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                if ("INHERIT_PREVIOUS".equals(norm)) {
                    return "SEMANTIC_INHERIT";
                }
                if ("OVERRIDE".equals(norm) || "NEW".equals(norm)) {
                    return "SEMANTIC_OVERRIDE";
                }
            }
        }
        if (prev != null && StringUtils.hasText(prev.getLastScopeType())) {
            return "INHERITED_PREVIOUS";
        }
        return "DEFAULT_GROUP";
    }
    private static String deriveScopeOverrideReason(AiResolvedQueryContext ctx, AiQuerySemanticParseResult sem,
                                                    AiConversationTurnMemory prev, List<String> mentionedStores,
                                                    String currentScopeSignal, String previousScopeType) {
        if (ctx == null) {
            return "ctx_null";
        }
        // ── 最高优先级：当 LLM rawScopeAction=OVERRIDE/NEW 且无具体门店名时，强制显示覆盖原因 ──
        if (sem != null && !sem.isParseMissing()) {
            String scopeAction = sem.getScopeAction();
            if (StringUtils.hasText(scopeAction)) {
                String norm = scopeAction.trim().toUpperCase(Locale.ROOT).replace('-', '_');
                if ("OVERRIDE".equals(norm) || "NEW".equals(norm)) {
                    // 有具体门店名 → 当前消息点名门店覆盖上一轮
                    if (mentionedStores != null && !mentionedStores.isEmpty()) {
                        return "CURRENT_EXPLICIT_STORE_OVERRIDES_PREVIOUS_STORE";
                    }
                    // 无具体门店名 → 检查上一轮 scopeType 是否为 STORE
                    // previousScopeType 非 STORE（首轮或上一轮为 GROUP）时，不写 OVERRIDES_PREVIOUS_STORE
                    if (!"STORE".equals(previousScopeType)) {
                        return "CURRENT_EXPLICIT_GROUP_SCOPE";
                    }
                    return "CURRENT_EXPLICIT_GROUP_OVERRIDES_PREVIOUS_STORE";
                }
            }
        }
        // ── effectiveScopeSource 已设置时的原因说明 ──
        String effectiveScopeSource = ctx.getEffectiveScopeSource();
        if (StringUtils.hasText(effectiveScopeSource)) {
            switch (effectiveScopeSource) {
                case "INHERITED_PREVIOUS":
                    return "继承上一轮 scope（用户未明确指定）";
                case "CURRENT_MESSAGE":
                    return "当前消息显式声明 scope（可能包含 keyword 匹配）";
                case "CURRENT_MESSAGE_EXPLICIT_STORE":
                    return "当前消息显式点名门店（语义 LLM 匹配）";
                case "SEMANTIC_SUBSET":
                    return "语义 LLM 收窄至多店子集";
                default:
                    return effectiveScopeSource;
            }
        }
        // ── 兜底：消息文本包含「全部店铺」──
        String normQ = ctx.getNormalizedQuestion();
        if (StringUtils.hasText(normQ)) {
            String s = normQ.replace(" ", "");
            if (s.contains("全部店铺") || s.contains("所有门店") || s.contains("全集团") || s.contains("全部门店")) {
                return "用户消息包含「全部店铺/全集团」但可能未被正确处理";
            }
        }
        return "unknown";
    }
    private static String resolveFinalMentionedStore(AiResolvedQueryContext ctx, AiResolvedOrgScope org) {
        // 优先从 resolvedMatchedSemanticStoreMention 获取
        if (StringUtils.hasText(ctx.getResolvedMatchedSemanticStoreMention())) {
            return ctx.getResolvedMatchedSemanticStoreMention().trim();
        }
        // 从 visibleStores 获取
        if (org != null && org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
            AiStoreScopeDTO s = org.getVisibleStores().get(0);
            if (s != null && StringUtils.hasText(s.getStoreName())) {
                return s.getStoreName().trim();
            }
        }
        return null;
    }

    private static List<Map<String, String>> copyRewriteUsedAnchors(List<Map<String, String>> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> row : in) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            out.add(new LinkedHashMap<>(row));
        }
        return out.isEmpty() ? null : out;
    }

    private static void appendEntityExistenceGroundingDebug(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx) {
        Map<String, Object> trace = null;
        if (ctx != null
                && ctx.getQuerySemanticParse() != null
                && ctx.getQuerySemanticParse().getMultiTurnInheritanceTrace() != null) {
            trace = ctx.getQuerySemanticParse().getMultiTurnInheritanceTrace();
        }
        putTraceField(out, "entityExistenceGroundingAtExecution", trace, "entityExistenceGrounding");
        putTraceField(out, "rewriteAnchorEntityIdInput", trace, "rewriteAnchorEntityIdInput");
        putTraceField(out, "rewriteUsedAnchorsInput", trace, "rewriteUsedAnchorsInput");
        putTraceField(out, "canonicalIdPreferenceMatched", trace, "canonicalIdPreferenceMatched");
        putTraceField(out, "canonicalIdProbeAttempted", trace, "canonicalIdProbeAttempted");
        putTraceField(out, "canonicalIdProbeResult", trace, "canonicalIdProbeResult");
        putTraceField(out, "fallbackToNameProbeReason", trace, "fallbackToNameProbeReason");
        putTraceField(out, "entityGroundingLlmEntityTypeAtProbe", trace, "entityGroundingLlmEntityTypeAtProbe");
        putTraceField(out, "entityGroundingLlmEntityType", trace, "entityGroundingLlmEntityType");
        putTraceField(out, "entityGroundingDecision", trace, "entityGroundingDecision");
        putTraceField(out, "goodsExistence", trace, "goodsExistence");
    }

    private static void putTraceField(
            LinkedHashMap<String, Object> out,
            String harnessKey,
            Map<String, Object> trace,
            String traceKey) {
        if (trace == null || !trace.containsKey(traceKey)) {
            out.put(harnessKey, null);
            return;
        }
        Object value = trace.get(traceKey);
        if (value instanceof Map<?, ?> map) {
            out.put(harnessKey, copyStringKeyMap(map));
            return;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    copy.add(copyStringKeyMap(map));
                } else {
                    copy.add(item);
                }
            }
            out.put(harnessKey, copy.isEmpty() ? null : copy);
            return;
        }
        out.put(harnessKey, value);
    }

    private static Map<String, Object> copyStringKeyMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy.isEmpty() ? null : copy;
    }
}
