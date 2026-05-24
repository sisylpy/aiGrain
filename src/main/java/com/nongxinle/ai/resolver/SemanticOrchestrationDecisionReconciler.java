package com.nongxinle.ai.resolver;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParseClarificationPolicy;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrix;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 编排决策业务修正：经营诊断/概览 MULTI_AGENT 门闸、四域换时 follow-up 候选补齐、Matrix 工具表对齐。
 * <p>从 {@link AiResolvedQueryContextResolver} 抽出的 orchestration policy，不改变 debug 字段名与写入顺序。
 */
public final class SemanticOrchestrationDecisionReconciler {

    private SemanticOrchestrationDecisionReconciler() {}

    /**
     * LLM {@link AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart} 经业务 Matrix/路径规则修正后的编排快照。
     */
    public record ReconcileRequest(
            boolean clarificationRequired,
            AiFollowUpResolution followUp,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent queryIntent,
            AiQuerySemanticParseResult orchSource,
            List<String> selectedAgents,
            List<String> selectedTools,
            String taskMode,
            Boolean plannerRequired,
            Boolean multiAgentRequired,
            String reason) {}

    public record ReconcileResult(
            List<String> selectedAgents,
            List<String> selectedTools,
            String taskMode,
            Boolean plannerRequired,
            Boolean multiAgentRequired,
            String reason) {}

    /** LLM orchestration 候选 + reconcile 后的扁平字段（供 Context builder 写入）。 */
    public record OrchestrationAssemblyFields(
            List<String> selectedAgents,
            List<String> selectedTools,
            String taskMode,
            Boolean plannerRequired,
            Boolean multiAgentRequired,
            Boolean approvalRequired,
            Boolean clarificationRequiredFlag,
            String clarificationQuestionField,
            Double confidence,
            String reason) {}

    public record OrchestrationPipelineResult(
            OrchestrationAssemblyFields fields, ClarificationState clarificationState) {}

    public record ClarificationState(boolean clarificationRequired, String semanticClarificationQuestion) {}

    /**
     * 从 v2 orchestration 候选提取 → {@link #reconcile} → 经营概览 orchestration 澄清门闸。
     */
    public static OrchestrationPipelineResult extractReconcileAndApplyClarificationGate(
            boolean clarificationRequired,
            String semanticClarificationQuestion,
            AiQuerySemanticParseResult querySemanticV2Raw,
            AiQuerySemanticParseResult semanticLlm,
            AiFollowUpResolution followUp,
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent queryIntent) {
        AiQuerySemanticParseResult orchSource =
                querySemanticV2Raw != null && !querySemanticV2Raw.isParseMissing()
                        ? querySemanticV2Raw
                        : semanticLlm;
        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart odPart =
                orchSource != null ? orchSource.getOrchestrationDecisionCandidate() : null;
        boolean businessOverviewEffectiveRouting =
                followUp != null
                        && AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(followUp.getEffectiveIntentCode())
                        && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(
                                followUp.getEffectivePathCode());
        List<String> agents =
                odPart != null && odPart.getSelectedAgents() != null && !odPart.getSelectedAgents().isEmpty()
                        ? new ArrayList<>(odPart.getSelectedAgents())
                        : null;
        List<String> tools =
                odPart != null && odPart.getSelectedTools() != null && !odPart.getSelectedTools().isEmpty()
                        ? new ArrayList<>(odPart.getSelectedTools())
                        : null;
        String taskMode =
                odPart != null
                        ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(odPart.getTaskMode())
                        : null;
        Boolean plannerRequired = odPart != null ? odPart.getPlannerRequired() : null;
        Boolean multiAgentRequired = odPart != null ? odPart.getMultiAgentRequired() : null;
        Boolean approvalRequired = odPart != null ? odPart.getApprovalRequired() : null;
        Boolean orchClarificationRequiredFlag = odPart != null ? odPart.getClarificationRequired() : null;
        String orchClarificationQuestionField =
                odPart != null
                        ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(
                                odPart.getClarificationQuestion())
                        : null;
        Double confidence = odPart != null ? odPart.getConfidence() : null;
        String reason =
                odPart != null
                        ? AiResolvedQueryContextDebugFactory.blankToNullSemantic(odPart.getReason())
                        : null;

        ReconcileResult reconciled =
                reconcile(
                        new ReconcileRequest(
                                clarificationRequired,
                                followUp,
                                previousTurn,
                                queryIntent,
                                orchSource,
                                agents,
                                tools,
                                taskMode,
                                plannerRequired,
                                multiAgentRequired,
                                reason));

        ClarificationState clarify =
                applyBusinessOverviewOrchestrationClarificationGate(
                        clarificationRequired,
                        semanticClarificationQuestion,
                        businessOverviewEffectiveRouting,
                        odPart != null,
                        orchClarificationRequiredFlag,
                        orchClarificationQuestionField,
                        approvalRequired,
                        reconciled.reason());

        OrchestrationAssemblyFields fields =
                new OrchestrationAssemblyFields(
                        reconciled.selectedAgents(),
                        reconciled.selectedTools(),
                        reconciled.taskMode(),
                        reconciled.plannerRequired(),
                        reconciled.multiAgentRequired(),
                        approvalRequired,
                        orchClarificationRequiredFlag,
                        orchClarificationQuestionField,
                        confidence,
                        reconciled.reason());
        return new OrchestrationPipelineResult(fields, clarify);
    }

    static ClarificationState applyBusinessOverviewOrchestrationClarificationGate(
            boolean clarificationRequired,
            String semanticClarificationQuestion,
            boolean businessOverviewEffectiveRouting,
            boolean odPartPresent,
            Boolean orchestrationClarificationRequiredFlag,
            String orchestrationClarificationQuestionField,
            Boolean orchestrationApprovalRequired,
            String orchestrationReasonField) {
        if (clarificationRequired || !businessOverviewEffectiveRouting || !odPartPresent) {
            return new ClarificationState(clarificationRequired, semanticClarificationQuestion);
        }
        if (Boolean.TRUE.equals(orchestrationClarificationRequiredFlag)) {
            String q =
                    StringUtils.hasText(orchestrationClarificationQuestionField)
                            ? orchestrationClarificationQuestionField
                            : (!StringUtils.hasText(semanticClarificationQuestion)
                                    ? SemanticParseClarificationPolicy.clarificationQuestion()
                                    : semanticClarificationQuestion);
            return new ClarificationState(true, q);
        }
        if (Boolean.TRUE.equals(orchestrationApprovalRequired)) {
            String q;
            if (StringUtils.hasText(orchestrationReasonField)) {
                q = "该操作需要确认：" + orchestrationReasonField.trim();
            } else if (!StringUtils.hasText(semanticClarificationQuestion)) {
                q = "该操作需要确认后才能继续。";
            } else {
                q = semanticClarificationQuestion;
            }
            return new ClarificationState(true, q);
        }
        return new ClarificationState(clarificationRequired, semanticClarificationQuestion);
    }

    public static ReconcileResult reconcile(ReconcileRequest request) {
        if (request == null) {
            return emptyResult();
        }
        List<String> agents = copyList(request.selectedAgents());
        List<String> tools = copyList(request.selectedTools());
        String taskMode = request.taskMode();
        Boolean plannerRequired = request.plannerRequired();
        Boolean multiAgentRequired = request.multiAgentRequired();
        String reason = request.reason();

        if (!request.clarificationRequired() && request.followUp() != null) {
            AiFollowUpResolution followUp = request.followUp();
            if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(followUp.getEffectivePathCode())) {
                String tmEff = taskMode != null ? taskMode.trim() : "";
                boolean needHarnessMultiGate =
                        !StringUtils.hasText(tmEff) || !"MULTI_AGENT".equalsIgnoreCase(tmEff);
                if (needHarnessMultiGate || Boolean.FALSE.equals(multiAgentRequired)) {
                    taskMode = "MULTI_AGENT";
                    multiAgentRequired = true;
                    plannerRequired = false;
                }
            }
            if (AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(followUp.getEffectiveIntentCode())
                    && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(followUp.getEffectivePathCode())
                    && request.queryIntent() != null
                    && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                            request.queryIntent().getStructuredIntentDetail())) {
                String tmOv = taskMode != null ? taskMode.trim() : "";
                boolean needOverviewMultiOrchestration =
                        !StringUtils.hasText(tmOv) || !"MULTI_AGENT".equalsIgnoreCase(tmOv);
                if (needOverviewMultiOrchestration || Boolean.FALSE.equals(multiAgentRequired)) {
                    taskMode = "MULTI_AGENT";
                    multiAgentRequired = true;
                    plannerRequired = false;
                }
            }
            if (followUp.isFollowUp()
                    && request.previousTurn() != null
                    && request.queryIntent() != null
                    && request.orchSource() != null
                    && !request.orchSource().isParseMissing()
                    && AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(followUp.getEffectiveIntentCode())
                    && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(followUp.getEffectivePathCode())
                    && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                            request.queryIntent().getStructuredIntentDetail())
                    && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                            request.previousTurn().getLastStructuredIntentDetail())) {
                String tmRest = taskMode != null ? taskMode.trim() : "";
                boolean multiRest = StringUtils.hasText(tmRest) && "MULTI_AGENT".equalsIgnoreCase(tmRest);
                if (multiRest
                        && shouldCanonicalizeOrchestrationForSemanticTimeFollowUpBizOverview(
                                request.orchSource(), request.previousTurn())) {
                    agents = new ArrayList<>(canonicalBusinessOverviewMultiAgentAgents());
                    tools = new ArrayList<>(AiBusinessToolIds.BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS);
                }
            }
        }

        OrchestrationToolsMatrixReconcile matrixReconcile =
                reconcileSelectedToolsFromBusinessMatrix(request.followUp(), request.queryIntent(), tools);
        tools = matrixReconcile.tools();
        if (StringUtils.hasText(matrixReconcile.reasonNote())) {
            reason =
                    StringUtils.hasText(reason)
                            ? reason.trim() + "; " + matrixReconcile.reasonNote()
                            : matrixReconcile.reasonNote();
        }

        return new ReconcileResult(agents, tools, taskMode, plannerRequired, multiAgentRequired, reason);
    }

    private static ReconcileResult emptyResult() {
        return new ReconcileResult(null, null, null, null, null, null);
    }

    /** V2 四域经营概览：仅换时间窗且 intent 继承时，补齐 orchestration 候选里可能被 LLM 截断的子 Agent/tool 列表。 */
    static boolean shouldCanonicalizeOrchestrationForSemanticTimeFollowUpBizOverview(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        if (sem == null) {
            return false;
        }
        String ia = normalizeSemanticV2ActionToken(sem.getIntentAction());
        boolean intentInherited =
                "INHERIT_PREVIOUS".equals(ia)
                        || (!StringUtils.hasText(ia)
                                && previousTurn != null
                                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(
                                        previousTurn.getLastPathCode()));
        if (!intentInherited) {
            return false;
        }
        String ta = normalizeSemanticV2ActionToken(sem.getTimeAction());
        if (!"NEW".equals(ta) && !"OVERRIDE".equals(ta)) {
            return false;
        }
        if ("OVERRIDE".equals(normalizeSemanticV2ActionToken(sem.getScopeAction()))) {
            return false;
        }
        if ("OVERRIDE".equals(normalizeSemanticV2ActionToken(sem.getMetricAction()))) {
            return false;
        }
        return true;
    }

    private static List<String> canonicalBusinessOverviewMultiAgentAgents() {
        return List.of(
                BusinessAgentNames.REVENUE_OVERVIEW,
                BusinessAgentNames.PURCHASE_OVERVIEW,
                BusinessAgentNames.STOCK_REDUCE_QUERY,
                BusinessAgentNames.DISH_PROFIT_ANALYSIS);
    }

    private record OrchestrationToolsMatrixReconcile(List<String> tools, String reasonNote) {}

    /**
     * 经营概览/诊断：Planner 工具表以 Matrix 为准；LLM {@code selectedTools} 冲突时写 reason 供 Debug。
     */
    private static OrchestrationToolsMatrixReconcile reconcileSelectedToolsFromBusinessMatrix(
            AiFollowUpResolution followUpRes,
            AiResolvedQueryIntent queryIntent,
            List<String> llmSelectedTools) {
        if (followUpRes == null || queryIntent == null) {
            return new OrchestrationToolsMatrixReconcile(llmSelectedTools, null);
        }
        String path = followUpRes.getEffectivePathCode();
        String wireRaw = queryIntent.getStructuredIntentDetail();
        String canon =
                StringUtils.hasText(wireRaw)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wireRaw.trim())
                        : null;
        List<String> matrixTools = null;
        String source = null;
        if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(path)
                && StringUtils.hasText(canon)
                && AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(canon)) {
            matrixTools = BusinessOverviewSemanticCapabilityMatrix.defaultFourDomainPlannerTools();
            source = "business_overview_matrix";
        } else if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(path) && StringUtils.hasText(canon)) {
            matrixTools = BusinessDiagnosisSemanticCapabilityMatrix.plannerToolsForWire(canon);
            source =
                    BusinessDiagnosisSemanticCapabilityMatrix.isDualDomainPurchaseStockWire(canon)
                            ? "business_diagnosis_matrix_dual_domain"
                            : "business_diagnosis_matrix_four_domain";
        }
        if (matrixTools == null || matrixTools.isEmpty()) {
            return new OrchestrationToolsMatrixReconcile(llmSelectedTools, null);
        }
        String reason = null;
        if (llmSelectedTools != null
                && !llmSelectedTools.isEmpty()
                && !plannerToolListsEqual(llmSelectedTools, matrixTools)) {
            reason = "planner_tools_matrix_override_llm_selectedTools:" + source;
        }
        return new OrchestrationToolsMatrixReconcile(new ArrayList<>(matrixTools), reason);
    }

    private static boolean plannerToolListsEqual(List<String> a, List<String> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            String ta = a.get(i) != null ? a.get(i).trim() : "";
            String tb = b.get(i) != null ? b.get(i).trim() : "";
            if (!ta.equals(tb)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeSemanticV2ActionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static List<String> copyList(List<String> in) {
        return in == null || in.isEmpty() ? null : new ArrayList<>(in);
    }
}
