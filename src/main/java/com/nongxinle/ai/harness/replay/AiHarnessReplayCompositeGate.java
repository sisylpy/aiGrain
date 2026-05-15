package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateReasonCode;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeGateResult;
import com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C-54：Harness-only Composite Production Gate replay — 固定 {@link AiResolvedQueryContext} / {@link AiRunState}，
 * <b>仅</b>调用 {@link BusinessDiagnosisCompositeProductionGate#evaluate}；不跑 Resolver、{@code PlannerExecutor}、Tool、Composer、LLM。
 */
public final class AiHarnessReplayCompositeGate {

    private static final long SYNTH_CONVERSATION_ID = 0L;
    private static final long SYNTH_RUN_ID = 9_100_000L;

    private AiHarnessReplayCompositeGate() {
    }

    public static AiHarnessReplayResponse replay(AiHarnessReplayRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }
        String caseId = req.getCaseId() != null ? req.getCaseId().trim() : "";
        if (!AiHarnessBuiltinCases.isCompositeGateHarnessCase(caseId)) {
            throw new IllegalArgumentException("not a composite gate harness caseId: " + caseId);
        }

        LocalDate today = resolveToday(req.getFrozenClockDate());
        String shellMsg = req.getMessages().get(0);
        String message = StringUtils.hasText(shellMsg) ? shellMsg.trim() : "";

        GateScenario scenario = GateScenario.forCaseId(caseId);
        AiResolvedQueryContext ctx = scenario.buildContext(today);
        boolean flag = scenario.compositeProductionEnabled();

        BusinessDiagnosisCompositeGateResult gateResult =
                BusinessDiagnosisCompositeProductionGate.evaluate(ctx, scenario.runState(), flag);

        Map<String, Object> root = buildRootSummary(caseId, gateResult);

        boolean pass = scenario.matchesExpectation(gateResult);

        Map<String, Object> roundSummary = new LinkedHashMap<>(root);

        AiHarnessReplayRoundResult round = AiHarnessReplayRoundResult.builder()
                .roundIndex(1)
                .message(message)
                .runId(SYNTH_RUN_ID)
                .conversationId(SYNTH_CONVERSATION_ID)
                .resolvedQueryContextSummary(roundSummary)
                .pass(pass)
                .failedFields(List.of())
                .build();

        return AiHarnessReplayResponse.builder()
                .conversationId(SYNTH_CONVERSATION_ID)
                .overallPass(pass)
                .frozenClockDate(today.toString())
                .caseId(caseId)
                .harnessRootSummary(root)
                .rounds(List.of(round))
                .build();
    }

    private static Map<String, Object> buildRootSummary(
            String caseId, BusinessDiagnosisCompositeGateResult gateResult) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("harnessReplayMode", AiHarnessReplayMode.BUSINESS_DIAGNOSIS_COMPOSITE_GATE.name());
        m.put("gateCaseId", caseId);
        m.put("gateAllowed", gateResult.isAllowed());
        m.put("gateReasonCode", gateResult.getReasonCode() != null ? gateResult.getReasonCode().name() : null);
        m.put("gateReason", gateResult.getReason());
        m.put("gateScopeType", gateResult.getScopeType());
        m.put(
                "gateRecommendedCaseKind",
                gateResult.getRecommendedCaseKind() != null
                        ? gateResult.getRecommendedCaseKind().name()
                        : null);
        m.put("gateFinalAnswerPlanType", gateResult.getFinalAnswerPlanType());
        m.put("gateDebug", gateResult.getDebug() != null ? new LinkedHashMap<>(gateResult.getDebug()) : Map.of());
        return m;
    }

    private static LocalDate resolveToday(String frozenClockDate) {
        if (!StringUtils.hasText(frozenClockDate)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(frozenClockDate.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid frozenClockDate (yyyy-MM-dd): " + frozenClockDate);
        }
    }

    private enum GateScenario {
        STORE_ALLOWED(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_STORE_ALLOWED,
                true,
                BusinessDiagnosisCompositeGateReasonCode.ALLOWED_STORE,
                true),
        GROUP_ALLOWED(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_ALLOWED,
                true,
                BusinessDiagnosisCompositeGateReasonCode.ALLOWED_GROUP,
                true),
        FEATURE_DISABLED(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_FEATURE_DISABLED,
                false,
                BusinessDiagnosisCompositeGateReasonCode.FEATURE_FLAG_DISABLED,
                false),
        DOMAIN_REVENUE(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_DOMAIN_REVENUE_BLOCKED,
                true,
                BusinessDiagnosisCompositeGateReasonCode.DOMAIN_SINGLE_INTENT_NOT_COMPOSITE,
                false),
        NAMED_DISH(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_NAMED_DISH_BLOCKED,
                true,
                BusinessDiagnosisCompositeGateReasonCode.NAMED_DISH_DEEP_DIVE_NOT_COMPOSITE,
                false),
        RANKING(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_RANKING_BLOCKED,
                true,
                BusinessDiagnosisCompositeGateReasonCode.RANKING_OR_DEEP_DIVE_NOT_COMPOSITE,
                false),
        MISSING_TIME(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_MISSING_TIME,
                true,
                BusinessDiagnosisCompositeGateReasonCode.MISSING_TIME_WINDOW,
                false),
        GROUP_INSUFFICIENT(
                AiHarnessBuiltinCases.BUSINESS_DIAGNOSIS_COMPOSITE_GATE_GROUP_INSUFFICIENT_STORES,
                true,
                BusinessDiagnosisCompositeGateReasonCode.GROUP_SCOPE_INSUFFICIENT_VISIBLE_STORES,
                false);

        private final String caseId;
        private final boolean productionFlag;
        private final BusinessDiagnosisCompositeGateReasonCode expectedReason;
        private final boolean expectedAllowed;

        GateScenario(
                String caseId,
                boolean productionFlag,
                BusinessDiagnosisCompositeGateReasonCode expectedReason,
                boolean expectedAllowed) {
            this.caseId = caseId;
            this.productionFlag = productionFlag;
            this.expectedReason = expectedReason;
            this.expectedAllowed = expectedAllowed;
        }

        static GateScenario forCaseId(String caseId) {
            for (GateScenario s : values()) {
                if (s.caseId.equals(caseId)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("unknown composite gate caseId: " + caseId);
        }

        boolean compositeProductionEnabled() {
            return productionFlag;
        }

        AiRunState runState() {
            return AiRunState.builder().needClarification(false).build();
        }

        boolean matchesExpectation(BusinessDiagnosisCompositeGateResult r) {
            if (expectedAllowed != r.isAllowed() || expectedReason != r.getReasonCode()) {
                return false;
            }
            if (expectedAllowed) {
                if (!BusinessDiagnosisCompositeAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_COMPOSITE.equals(
                        r.getFinalAnswerPlanType())) {
                    return false;
                }
                if (this == STORE_ALLOWED
                        && r.getRecommendedCaseKind()
                                != BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.STORE) {
                    return false;
                }
                if (this == GROUP_ALLOWED
                        && r.getRecommendedCaseKind()
                                != BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.GROUP) {
                    return false;
                }
            }
            return true;
        }

        AiResolvedQueryContext buildContext(LocalDate today) {
            return switch (this) {
                case STORE_ALLOWED -> storeDiagnosisAllowed(today, AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
                case GROUP_ALLOWED -> groupDiagnosisAllowed(today);
                case FEATURE_DISABLED -> storeDiagnosisAllowed(today, AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
                case DOMAIN_REVENUE -> revenueSingleDomain(today);
                case NAMED_DISH -> namedDishStore(today);
                case RANKING -> rankingStructured(today);
                case MISSING_TIME -> missingTimeStore();
                case GROUP_INSUFFICIENT -> groupOneStore(today);
            };
        }
    }

    private static AiResolvedTimeWindow monthToDate(LocalDate today) {
        LocalDate start = today.withDayOfMonth(1);
        return AiResolvedTimeWindow.builder()
                .timeLabel(AiResolvedTimeWindow.THIS_MONTH)
                .startDate(start)
                .endDate(today)
                .build();
    }

    private static AiResolvedQueryIntent diagnosisIntent(String structuredWire) {
        return AiResolvedQueryIntent.builder()
                .intentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .pathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .structuredIntentDetail(structuredWire)
                .build();
    }

    private static AiResolvedQueryContext storeDiagnosisAllowed(LocalDate today, String structuredWire) {
        long storeId = 1L;
        AiResolvedOrgScope org = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .currentStoreDepartmentId(storeId)
                .requestDepartmentId(storeId)
                .visibleStores(
                        List.of(AiStoreScopeDTO.builder().storeDepartmentId(storeId).storeName("AAA").build()))
                .build();

        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .queryIntent(diagnosisIntent(structuredWire))
                .orgScope(org)
                .timeWindow(monthToDate(today))
                .needSemanticClarification(false)
                .orchestrationClarificationRequired(false)
                .mentionedDishName(null)
                .build();
    }

    private static AiResolvedQueryContext groupDiagnosisAllowed(LocalDate today) {
        AiResolvedOrgScope org = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(100L)
                .visibleStores(
                        List.of(
                                AiStoreScopeDTO.builder().storeDepartmentId(1L).storeName("AAA").build(),
                                AiStoreScopeDTO.builder().storeDepartmentId(3L).storeName("BBB").build()))
                .build();

        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .queryIntent(diagnosisIntent(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY))
                .orgScope(org)
                .timeWindow(monthToDate(today))
                .needSemanticClarification(false)
                .orchestrationClarificationRequired(false)
                .mentionedDishName(null)
                .build();
    }

    private static AiResolvedQueryContext revenueSingleDomain(LocalDate today) {
        long storeId = 1L;
        AiResolvedOrgScope org = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .currentStoreDepartmentId(storeId)
                .visibleStores(
                        List.of(AiStoreScopeDTO.builder().storeDepartmentId(storeId).storeName("AAA").build()))
                .build();

        AiResolvedQueryIntent qi = AiResolvedQueryIntent.builder()
                .intentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                .pathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .structuredIntentDetail(null)
                .build();

        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.REVENUE_OVERVIEW)
                .effectivePathCode(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW)
                .queryIntent(qi)
                .orgScope(org)
                .timeWindow(monthToDate(today))
                .needSemanticClarification(false)
                .orchestrationClarificationRequired(false)
                .build();
    }

    private static AiResolvedQueryContext namedDishStore(LocalDate today) {
        AiResolvedQueryContext ctx =
                storeDiagnosisAllowed(today, AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
        ctx.setMentionedDishName("HARNESS_SHELL_DISH");
        return ctx;
    }

    private static AiResolvedQueryContext rankingStructured(LocalDate today) {
        AiResolvedQueryIntent qi = diagnosisIntent(AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING);
        long storeId = 1L;
        AiResolvedOrgScope org = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .currentStoreDepartmentId(storeId)
                .visibleStores(
                        List.of(AiStoreScopeDTO.builder().storeDepartmentId(storeId).storeName("AAA").build()))
                .build();

        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .queryIntent(qi)
                .orgScope(org)
                .timeWindow(monthToDate(today))
                .needSemanticClarification(false)
                .orchestrationClarificationRequired(false)
                .mentionedDishName(null)
                .build();
    }

    private static AiResolvedQueryContext missingTimeStore() {
        long storeId = 1L;
        AiResolvedOrgScope org = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .currentStoreDepartmentId(storeId)
                .visibleStores(
                        List.of(AiStoreScopeDTO.builder().storeDepartmentId(storeId).storeName("AAA").build()))
                .build();

        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .queryIntent(diagnosisIntent(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY))
                .orgScope(org)
                .timeWindow(null)
                .needSemanticClarification(false)
                .orchestrationClarificationRequired(false)
                .build();
    }

    private static AiResolvedQueryContext groupOneStore(LocalDate today) {
        AiResolvedOrgScope org = AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(100L)
                .visibleStores(
                        List.of(AiStoreScopeDTO.builder().storeDepartmentId(1L).storeName("AAA").build()))
                .build();

        return AiResolvedQueryContext.builder()
                .effectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS)
                .effectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS)
                .queryIntent(diagnosisIntent(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY))
                .orgScope(org)
                .timeWindow(monthToDate(today))
                .needSemanticClarification(false)
                .orchestrationClarificationRequired(false)
                .build();
    }
}
