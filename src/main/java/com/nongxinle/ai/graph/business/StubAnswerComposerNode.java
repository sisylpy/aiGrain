package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.BusinessDiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.dto.cost.AiCostDiagnosisResult;
import com.nongxinle.ai.gateway.LlmGateway;
import com.nongxinle.ai.composer.payload.AnswerComposerPayloadFactory;
import com.nongxinle.ai.composer.AnswerBoundaryNoteComposer;
import com.nongxinle.ai.composer.renderer.DiagnosisDeterministicRenderer;
import com.nongxinle.ai.composer.renderer.DeterministicAnswerRenderer;
import com.nongxinle.ai.composer.summary.BusinessOverviewDeterministicSummaryBuilder;
import com.nongxinle.ai.prompt.AiPromptIds;
import com.nongxinle.ai.prompt.AiPromptService;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiNumericPlainText;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answer Composer：汇入 Tool/诊断结果与 DeepSeek 生成最终自然语言。<br>
 * 有结构化卡片（成本诊断 / 经营概览）时，正文只输出短口语化结论，明细由前端卡片承载。<br>
 * DeepSeek 经 {@link LlmGateway} 接入；仅占位时请设 {@code ai.agent.llm.stub=true}
 *（{@link com.nongxinle.ai.gateway.PlaceholderLlmGateway}）。
 */
@Component
@RequiredArgsConstructor
public class StubAnswerComposerNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(StubAnswerComposerNode.class);

    /** 库存重量对用户展示单位（与业务库存字段常见口径一致）。 */
    private static final String W_STOCK_WEIGHT_UNIT = "斤";

    private static String fmtStockWeightCn(Object value) {
        return plainNumericHint(value) + " " + W_STOCK_WEIGHT_UNIT;
    }

    /** 写入「剩余 {n} 斤」时中间的数字部分（斤前留空格）。 */
    private static String stockWeightNumberOnly(Object value) {
        return plainNumericHint(value);
    }

    private static boolean warehouseOverviewHasVisibleWarehouses(Map<String, Object> wo) {
        if (wo == null) {
            return false;
        }
        Object v = wo.get("visibleWarehouses");
        return v instanceof List<?> l && !l.isEmpty();
    }

    /** 按岗位去掉不当的「店长」寒暄（与 {@link #warehouseSalutationDirective} 一致）。 */
    private static final Pattern WAREHOUSE_LEADING_SALUTATION =
            Pattern.compile("^((好的|嗯|您好)[，,\\s]*)?((亲爱的)?店长)([，,。．、:\\s]+|(?=本库房|以下是|以下按|当前|说明|目前|共有))");

    private static final Pattern WAREHOUSE_LEADING_MANAGER_NO_PUNCT =
            Pattern.compile("^店长(?=本库房|以下是|以下按|当前|说明|目前|共有)");

    private final LlmGateway llmGateway;
    private final AiSseEventPublisher publisher;
    private final AiPromptService aiPromptService;
    private final AnswerComposerPayloadFactory answerComposerPayloadFactory;
    private final DeterministicAnswerRenderer deterministicAnswerRenderer;

    @Override
    public String name() {
        return "AnswerComposer";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "AnswerComposerNode",
                "displayText", "正在生成自然语言回答…"
        ));

        String scopeP = AiAnswerBoundary.scopeConvergencePrefix(state.getScopeConvergenceNote());
        String intentP = AiAnswerBoundary.costIntentConvergencePrefix(state.getCostIntentConvergenceNote());
        String permPrefix = AiAnswerBoundary.composeHumanPrefix(state.getPermissionDenials());
        String boundaryNote = "";
        if (state.getResolvedQueryContext() != null) {
            String n = state.getResolvedQueryContext().getAnswerBoundaryNote();
            if (n != null && !n.isBlank()) {
                boundaryNote = AnswerBoundaryNoteComposer.refineUserFacingBoundaryNote(
                        state.getResolvedQueryContext(), n.trim());
            }
        }

        // 库存排行确定性正文已含「查询范围 / 统计时间」，rankTakeover 时再置位，避免 head 重复 scope/intent/boundary。
        boolean suppressScopeIntentBoundaryHead = false;

        String coreToolPermissionOnlyBody = AiAnswerBoundary.tryComposeCoreToolPermissionOnlyAnswer(state);
        if (StringUtils.hasText(coreToolPermissionOnlyBody)) {
            coreToolPermissionOnlyBody = coreToolPermissionOnlyBody.trim();
        } else {
            coreToolPermissionOnlyBody = null;
        }

        String answer;
        if (state.isNeedClarification() && state.getClarificationQuestion() != null
                && !state.getClarificationQuestion().isBlank()) {
            answer = state.getClarificationQuestion().trim();
        } else if (state.isCouponCostInsightBlocked()) {
            answer = "";
        } else if (state.getCostDiagnosisResult() != null) {
            AiCostDiagnosisResult d = state.getCostDiagnosisResult();
            String pid = AiPromptIds.COMPOSER_COST_DIAGNOSIS_V1;
            state.setComposerPromptRegistryId(pid);
            String llm = llmGateway.chatSimple(aiPromptService.require(pid),
                    JSON.toJSONString(answerComposerPayloadFactory.buildCostPayload(state, d)));
            answer = pickLlmSanitized(llm, deterministicAnswerRenderer.renderCostFallback(d));
        } else if (coreToolPermissionOnlyBody != null) {
            answer = coreToolPermissionOnlyBody;
        } else if (state.isBusinessDiagnosisPath() && state.getBusinessDiagnosisPlan() != null
                && AiAnswerBoundary.shouldRenderPermissionDowngradedBusinessDiagnosis(state)) {
            answer = deterministicAnswerRenderer.renderPermissionDowngradedBusinessDiagnosis(state,
                    state.getBusinessDiagnosisPlan());
        } else if (!DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)
                && state.getDiagnosisPlan() != null
                && DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS.equals(state.getDiagnosisPlan().getPlanType())
                && (state.isBusinessDiagnosisPath()
                || DiagnosisPlanBuilder.shouldPreferDiagnosisPlanInComposer(state))) {
            // 新版 DiagnosisPlan：经营诊断 path 上即使 dishProfitPath 仍为 true（历史标志位）也必须优先，避免落入旧 BusinessDiagnosisPlan+LLM
            answer = deterministicAnswerRenderer.renderHarnessDiagnosisPlan(state, state.getDiagnosisPlan());
        } else if (state.isBusinessDiagnosisPath() && state.getBusinessDiagnosisPlan() != null) {
            BusinessDiagnosisPlan bdPlan = state.getBusinessDiagnosisPlan();
            if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)) {
                if (DiagnosisDeterministicRenderer.isWarehouseOrgScope(state)) {
                    answer = deterministicAnswerRenderer.renderWarehouseBoundedBusinessDiagnosisStorePriority(state,
                            bdPlan);
                } else {
                    LinkedHashMap<String, Object> spPayload =
                            answerComposerPayloadFactory.buildBusinessDiagnosisStorePriorityPayload(state, bdPlan);
                    String pidSp = AiPromptIds.COMPOSER_DIAGNOSIS_STORE_PRIORITY_V1;
                    state.setComposerPromptRegistryId(pidSp);
                    String llmSp = llmGateway.chatSimple(aiPromptService.require(pidSp),
                            JSON.toJSONString(spPayload));
                    String draftSp =
                            pickLlmSanitized(llmSp, deterministicAnswerRenderer.renderStorePriorityRanking(state, bdPlan));
                    answer = guardBusinessDiagnosisAnswer(draftSp, state, bdPlan);
                }
            } else {
                LinkedHashMap<String, Object> bdPayload =
                        answerComposerPayloadFactory.buildBusinessDiagnosisPayload(state, bdPlan);
                String pidBd = AiPromptIds.COMPOSER_DIAGNOSIS_V1;
                state.setComposerPromptRegistryId(pidBd);
                String llm = llmGateway.chatSimple(aiPromptService.require(pidBd), JSON.toJSONString(bdPayload));
                String draft = pickLlmSanitized(llm,
                        deterministicAnswerRenderer.renderBusinessDiagnosisFallback(state, bdPlan));
                answer = guardBusinessDiagnosisAnswer(draft, state, bdPlan);
            }
        } else if (dishSalesDeterministicEligible(state)) {
            answer = deterministicAnswerRenderer.renderDishSalesAnswerPlan(state.getDishSalesAnswerPlan());
        } else if (state.isDishProfitPath()) {
            // 最终短文由 DeterministicAnswerRenderer.renderDishProfitFallback 组装；其中有 focusRows 的 AnswerPlan 优先于工具 summary。
            AiDishProfitOverviewResult dp = state.getDishProfitOverviewResult();
            if (dp != null) {
                if (dishProfitUseDeterministicSummaryOnly(state) || dishProfitNarrowRankingOrReasonPlan(state)) {
                    answer = pickLlmSanitized("",
                            deterministicAnswerRenderer.renderDishProfitFallback(dp, state));
                } else {
                    String pidDp = AiPromptIds.COMPOSER_DISH_PROFIT_V1;
                    state.setComposerPromptRegistryId(pidDp);
                    String llm = llmGateway.chatSimple(aiPromptService.require(pidDp),
                            JSON.toJSONString(answerComposerPayloadFactory.buildDishProfitPayload(state, dp)));
                    answer = pickLlmSanitized(llm, deterministicAnswerRenderer.renderDishProfitFallback(dp, state));
                }
            } else {
                answer = deterministicAnswerRenderer.renderDishProfitFallback(null, state);
            }
        } else if (state.isRevenueOverviewPath()) {
            if (AiAnswerBoundary.isRevenuePermissionDenied(state.getPermissionDenials())) {
                answer = AiAnswerBoundary.revenuePermissionDeniedComposerBody(state);
            } else {
                AiTimeWindowTextFormatter.UserPhrases twRevenue = AiTimeWindowTextFormatter.forAnswer(state);
                DailyRevenueAnswerPlan rap = state.getRevenueAnswerPlan();
                String revenueFromPlan = BusinessOverviewDeterministicSummaryBuilder.composeRevenueDeterministicFromAnswerPlan(rap, twRevenue);
                if (revenueFromPlan != null && !revenueFromPlan.isBlank()) {
                    answer = revenueFromPlan;
                } else {
                    answer = deterministicAnswerRenderer.renderRevenueEnvelopeFallback(state);
                }
            }
        } else if (businessOverviewMultiAgentFourDomainDeterministicEligible(state)) {
            answer = composeBusinessOverviewMultiAgentFourDomainMarkdown(state).trim();
        } else if (state.getBusinessOverviewResult() != null) {
            AiBusinessOverviewResult o = state.getBusinessOverviewResult();
            boolean skipOverviewLlm = BusinessOverviewDeterministicSummaryBuilder.hasAuthoritativeBusinessOverviewRevenuePlan(state);
            if (skipOverviewLlm) {
                answer = deterministicAnswerRenderer.renderBusinessOverviewFallback(state, o).trim();
            } else {
                String pidBo = AiPromptIds.COMPOSER_BUSINESS_OVERVIEW_V1;
                state.setComposerPromptRegistryId(pidBo);
                String llm = llmGateway.chatSimple(aiPromptService.require(pidBo),
                        JSON.toJSONString(answerComposerPayloadFactory.buildBusinessOverviewPayload(state, o)));
                answer = pickLlmSanitized(llm,
                        deterministicAnswerRenderer.renderBusinessOverviewFallback(state, o));
            }
        } else if (state.isWarehouseStockOverviewPath()) {
            Map<String, Object> woForSalutation = extractWarehouseOverviewPayload(state);
            LinkedHashMap<String, Object> whCtx = answerComposerPayloadFactory.buildWarehouseOverviewPayload(state);
            state.setWarehouseOverview(buildWarehouseOverviewStructured(state));
            String fb = deterministicAnswerRenderer.renderWarehouseStockFallback(state);
            boolean rankTakeover = warehouseStockRankingDeterministicTakeoverEligible(state);
            if (log.isInfoEnabled()) {
                log.info(
                        "[D6-4B-WH-RANKING] composer warehouse branch runId={} rankTakeover={} fbHasTop3Amount={} fbHasTop3Sku={}",
                        state.getRunId(),
                        rankTakeover,
                        fb != null && fb.contains("门店库存金额排行 Top3"),
                        fb != null && fb.contains("门店库存商品种类排行 Top3"));
            }
            if (rankTakeover) {
                // 与 dishSalesDeterministicEligible 一致：结构化排行由确定性渲染出 Top3，避免 COMPOSER_WAREHOUSE_V1 覆盖 fb。
                suppressScopeIntentBoundaryHead = true;
                answer = pickLlmSanitized("", fb);
            } else {
                String llmRaw = "";
                try {
                    String payload;
                    try {
                        payload = JSON.toJSONString(whCtx);
                    } catch (Exception jsonEx) {
                        payload = "{}";
                    }
                    String pidWh = AiPromptIds.COMPOSER_WAREHOUSE_V1;
                    state.setComposerPromptRegistryId(pidWh);
                    llmRaw = llmGateway.chatSimple(aiPromptService.require(pidWh), payload);
                } catch (Exception ignored) {
                    llmRaw = "";
                }
                String llmUse = llmLooksUnavailable(llmRaw) ? "" : llmRaw;
                answer = pickLlmSanitized(llmUse, fb);
            }
            answer = applyWarehouseSalutationPolicy(answer, state, woForSalutation);
        } else if (state.isStockReduceQueryPath()) {
            AiTimeWindowTextFormatter.UserPhrases twStockReduce = AiTimeWindowTextFormatter.forAnswer(state);
            String stockReduceFromPlan = composeStockReduceDeterministicFromAnswerPlan(
                    state.getStockReduceAnswerPlan(), twStockReduce, state);
            if (stockReduceFromPlan != null && !stockReduceFromPlan.isBlank()) {
                answer = stockReduceFromPlan;
            } else {
                answer = deterministicAnswerRenderer.renderStockReduceToolFallback(state);
            }
        } else if (state.isPurchaseCostInsightPath()) {
            Map<String, Object> poRaw = extractPurchaseOverviewPayload(state);
            if (!poRaw.isEmpty()) {
                state.setPurchaseOverview(new LinkedHashMap<>(poRaw));
            }
            AiTimeWindowTextFormatter.UserPhrases twPurchase = AiTimeWindowTextFormatter.forAnswer(state);
            String purchaseFromPlan = composePurchaseDeterministicFromAnswerPlan(state.getPurchaseAnswerPlan(), twPurchase);
            if (purchaseFromPlan != null && !purchaseFromPlan.isBlank()) {
                answer = purchaseFromPlan;
            } else {
                boolean deterministicPurchaseOnly = shouldForceDeterministicPurchaseAnswer(state);
                String llm = "";
                if (!deterministicPurchaseOnly) {
                    LinkedHashMap<String, Object> purchaseCtx =
                            answerComposerPayloadFactory.buildPurchaseOverviewPayload(state);
                    try {
                        String pidPo = AiPromptIds.COMPOSER_PURCHASE_OVERVIEW_V1;
                        state.setComposerPromptRegistryId(pidPo);
                        llm = llmGateway.chatSimple(aiPromptService.require(pidPo),
                                JSON.toJSONString(purchaseCtx));
                    } catch (Exception ignored) {
                        llm = "";
                    }
                }
                answer = pickLlmSanitized(llm, deterministicAnswerRenderer.renderPurchaseCostFallback(state));
            }
        } else if (genericChatBlockedForDishReasonInDiagnosisContext(state)) {
            answer = "这类问题需要对照菜品毛利与成本数据作答。当前未走通数据查询链路，请改用完整提问（例如点明菜名并说明为什么毛利偏低或成本高），"
                    + "或在经营诊断结果页再追问该菜。";
        } else if (composerEmitRevenueDeniedPermissionOnly(state)) {
            answer = AiAnswerBoundary.revenuePermissionDeniedComposerBody(state);
        } else {
            LinkedHashMap<String, Object> ctx = composeSafeFallbackContext(state);
            String pidGc = AiPromptIds.COMPOSER_GENERIC_CHAT_V1;
            state.setComposerPromptRegistryId(pidGc);
            String llmOnly = llmGateway.chatSimple(aiPromptService.require(pidGc), JSON.toJSONString(ctx));
            answer = pickLlmSanitized(llmOnly, deterministicAnswerRenderer.genericEmptyLlmFallback());
        }
        if (suppressScopeIntentBoundaryHead) {
            scopeP = "";
            intentP = "";
            boundaryNote = "";
        } else if (shouldUseStoreCompareIntentHeader(state)) {
            intentP = DiagnosisDeterministicRenderer.storeCompareIntentConvergencePrefix(state.getDiagnosisPlan());
        } else if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)) {
            intentP =
                    AiAnswerBoundary.costIntentConvergencePrefix(
                            rewriteStorePriorityRankingCostIntentNote(state.getCostIntentConvergenceNote(), state));
        }
        StringBuilder head = new StringBuilder();
        if (!boundaryNote.isEmpty()) {
            head.append(boundaryNote);
        }
        if (!scopeP.isBlank()) {
            if (head.length() > 0) {
                head.append('\n');
            }
            head.append(scopeP.trim());
        }
        if (!intentP.isBlank()) {
            if (head.length() > 0) {
                head.append('\n');
            }
            head.append(intentP.trim());
        }
        if (!permPrefix.isBlank()) {
            if (head.length() > 0) {
                head.append('\n');
            }
            head.append(permPrefix.trim());
        }
        if (head.length() > 0) {
            answer = head + (answer.isEmpty() ? "" : "\n" + answer);
        }
        if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)) {
            answer = DiagnosisDeterministicRenderer.applyStorePrioritySingleStoreScopeDisplayPatches(
                    (answer == null ? "" : answer).trim(), state);
        }
        state.setFinalAnswerText(AiAnswerBoundary.stripDeveloperFacingLeakage(
                (answer == null ? "" : answer).trim()));

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "AnswerComposerNode",
                "displayText", "回答草稿已就绪",
                "hasStructuredCostDiagnosis", state.getCostDiagnosisResult() != null,
                "hasDishProfitOverview", state.getDishProfitOverviewResult() != null,
                "hasBusinessOverview", state.getBusinessOverviewResult() != null,
                "businessDiagnosisPath", state.isBusinessDiagnosisPath(),
                "purchaseCostInsightPath", state.isPurchaseCostInsightPath(),
                "warehouseStockOverviewPath", state.isWarehouseStockOverviewPath(),
                "stockReduceQueryPath", state.isStockReduceQueryPath(),
                "revenueOverviewPath", state.isRevenueOverviewPath()
        ));
        return state;
    }

    /**
     * 仅 {@code store_priority_ranking}：重写 Planner 下发的 {@code costIntentConvergenceNote} 中短语，终稿「【意图说明】」与 Phase 2B
     * 用语一致；**不**改写 {@link DiagnosisPlan#TYPE_OVERALL_BUSINESS_DIAGNOSIS} / 采购收敛等其它诊断话术。
     */
    private static String rewriteStorePriorityRankingCostIntentNote(String note, AiRunState state) {
        if (note == null || note.isBlank()) {
            return note;
        }
        String n = note.replace(
                "按集团权限范围内门店合并做经营诊断", "按集团权限范围内各门店做综合风险优先排序");
        return DiagnosisDeterministicRenderer.applyStorePrioritySingleStoreScopeDisplayPatches(n, state);
    }

    /**
     * 仅当本轮会走 Harness {@link DiagnosisPlan} 且存在门店经营对比证据时，替换【意图说明】，
     * 避免沿用 Planner 的「集团权限范围内门店合并做经营诊断」话术。
     */
    private static boolean shouldUseStoreCompareIntentHeader(AiRunState state) {
        if (state == null || state.isNeedClarification() || state.isCouponCostInsightBlocked()) {
            return false;
        }
        if (state.getCostDiagnosisResult() != null) {
            return false;
        }
        DiagnosisPlan p = state.getDiagnosisPlan();
        if (p == null) {
            return false;
        }
        if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)) {
            return false;
        }
        if (!DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS.equals(p.getPlanType())) {
            return false;
        }
        if (!state.isBusinessDiagnosisPath() && !DiagnosisPlanBuilder.shouldPreferDiagnosisPlanInComposer(state)) {
            return false;
        }
        return DiagnosisDeterministicRenderer.isStoreCompareEvidenceAnswerTurn(state, p);
    }

    private static String pickLlmSanitized(String llm, String fallback) {
        String picked = llm == null || llm.isBlank() ? fallback : llm.trim();
        return AiAnswerBoundary.stripDeveloperFacingLeakage(picked);
    }

    /**
     * {@link com.nongxinle.ai.DeepSeekCompletionClient} 在 HTTP/解析异常时返回「抱歉…」短句；
     * 库存链路必须以工具摘要作答，不能把该句当作正式回复。
     */
    private static boolean llmLooksUnavailable(String llm) {
        if (llm == null || llm.isBlank()) {
            return true;
        }
        String t = llm.trim();
        return t.startsWith("抱歉，AI 服务")
                || t.contains("AI 服务出现异常")
                || t.contains("AI 服务暂时不可用")
                || t.startsWith("AI 未返回有效");
    }

    /**
     * LLM 偶发忽略 focusRows 时：若仍存在结构化风险依据，则打回兜底句，避免「未识别风险」类错答。
     */
    private String guardBusinessDiagnosisAnswer(String text, AiRunState state, BusinessDiagnosisPlan plan) {
        if (!businessDiagnosisHasRiskSignal(state, plan)) {
            return text;
        }
        String t = text == null ? "" : text;
        boolean badDenial = containsBusinessDiagnosisBadDenial(t);
        if (badDenial) {
            return deterministicAnswerRenderer.renderBusinessDiagnosisFallback(state, plan);
        }
        return t;
    }

    private static boolean businessDiagnosisHasRiskSignal(AiRunState state, BusinessDiagnosisPlan plan) {
        if (plan != null && plan.getStorePriorityRanking() != null
                && plan.getStorePriorityRanking().getFocusStores() != null
                && !plan.getStorePriorityRanking().getFocusStores().isEmpty()) {
            return true;
        }
        if (plan != null && plan.getRiskItems() != null && !plan.getRiskItems().isEmpty()) {
            return true;
        }
        DishProfitAnswerPlan ap = state != null ? state.getDishProfitAnswerPlan() : null;
        if (ap != null && ap.getFocusRows() != null && !ap.getFocusRows().isEmpty()) {
            return true;
        }
        return false;
    }

    /** 与「暂无风险」「未识别」等指令冲突的泛泛否认（在有 risk/focusRows 时不可用）。 */
    private static boolean containsBusinessDiagnosisBadDenial(String t) {
        if (t == null || t.isBlank()) {
            return true;
        }
        String s = t;
        if (s.contains("未识别到") && (s.contains("风险") || s.contains("具体"))) {
            return true;
        }
        if (s.contains("暂无风险") || s.contains("无具体建议") || s.contains("没有明显风险")) {
            return true;
        }
        if (s.contains("暂无具体执行") || s.contains("无具体执行事项")) {
            return true;
        }
        return false;
    }


    private static boolean dishSalesDeterministicEligible(AiRunState state) {
        if (state == null || state.getDishSalesAnswerPlan() == null) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String effIntentRaw = rq.getEffectiveIntentCode();
        String effPathRaw = rq.getEffectivePathCode();
        String effIntent = StringUtils.hasText(effIntentRaw) ? effIntentRaw.trim() : null;
        String effPath = StringUtils.hasText(effPathRaw) ? effPathRaw.trim() : null;
        boolean dishSales =
                AiResolvedQueryIntent.DISH_SALES_QUERY.equals(effIntent)
                        || AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effPath);
        if (!dishSales) {
            return false;
        }
        DishSalesAnswerPlan p = state.getDishSalesAnswerPlan();
        String pt = p.getPlanType();
        if (!StringUtils.hasText(pt)) {
            return false;
        }
        pt = pt.trim();
        return DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW.equals(pt);
    }

    /**
     * 库存门店/仓库排行 wire：{@link com.nongxinle.ai.composer.renderer.WarehouseDeterministicRenderer} 已出确定性结论，
     * Composer 侧禁止再走 LLM，否则会覆盖 Top3（payload Summary 未带排行字段时模型常写总览）。
     */
    private static boolean warehouseStockRankingDeterministicTakeoverEligible(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        String raw = null;
        AiResolvedQueryIntent qi = ctx.getQueryIntent();
        if (qi != null && StringUtils.hasText(qi.getStructuredIntentDetail())) {
            raw = qi.getStructuredIntentDetail().trim();
        }
        if (!StringUtils.hasText(raw)
                && ctx.getQuerySemanticParse() != null
                && ctx.getQuerySemanticParse().getMetric() != null) {
            String rt = ctx.getQuerySemanticParse().getMetric().getRankingType();
            if (StringUtils.hasText(rt)) {
                raw = rt.trim();
            }
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
        String rankingTypeFallback = ctx.getQuerySemanticParse() != null
                        && ctx.getQuerySemanticParse().getMetric() != null
                ? ctx.getQuerySemanticParse().getMetric().getRankingType()
                : null;
        boolean eligible = AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(wire);
        if (log.isInfoEnabled()) {
            log.info(
                    "[D6-4B-WH-RANKING] takeoverEligible={} runId={} queryIntentStructuredDetail={} metricRankingType={} canonicalWire={}",
                    eligible,
                    state.getRunId(),
                    qi != null ? qi.getStructuredIntentDetail() : null,
                    rankingTypeFallback,
                    wire);
        }
        return eligible;
    }

    private static boolean dishProfitUseDeterministicSummaryOnly(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        var qi = ctx != null ? ctx.getQueryIntent() : null;
        if (qi == null) {
            return false;
        }
        String sid = qi.getStructuredIntentDetail();
        if (sid == null || sid.isBlank()) {
            return false;
        }
        return AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(sid);
    }

    /**
     * 排行/原因类子意图：服务端已压缩 summary 与 answerPlan，Composer 走确定性短文，避免总览模板。
     */
    private static boolean dishProfitNarrowRankingOrReasonPlan(AiRunState state) {
        DishProfitAnswerPlan plan = state != null ? state.getDishProfitAnswerPlan() : null;
        if (plan == null || plan.getPlanType() == null || plan.getPlanType().isBlank()) {
            return false;
        }
        String t = plan.getPlanType().trim();
        return DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_PROFIT_REASON.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_THEORETICAL_COST.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_ACTUAL_OUTBOUND_COST.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE.equals(t)
                || DishProfitAnswerPlan.TYPE_DISH_COST_GAP.equals(t);
    }


    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String nz(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String plainNumericHint(Object v) {
        if (v == null) {
            return "暂无";
        }
        if (v instanceof BigDecimal bd) {
            return AiNumericPlainText.plainNumber(bd);
        }
        if (v instanceof Number n) {
            return AiNumericPlainText.plainNumber(n);
        }
        String s = v.toString().trim();
        return s.isEmpty() ? "暂无" : s;
    }



    private static boolean shouldForceDeterministicPurchaseAnswer(AiRunState state) {
        var ctx = state.getResolvedQueryContext();
        if (ctx == null || ctx.getQueryIntent() == null) {
            return false;
        }
        String sid = ctx.getQueryIntent().getStructuredIntentDetail();
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)) {
            return true;
        }
        if (AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sid)) {
            return true;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(sid)) {
            return true;
        }
        return false;
    }

    private static boolean businessOverviewMultiAgentFourDomainDeterministicEligible(AiRunState state) {
        if (state == null || !state.isBusinessOverviewPath()) {
            return false;
        }
        BusinessOverviewAnswerPlan bop = state.getBusinessOverviewAnswerPlan();
        if (bop == null) {
            return false;
        }
        String pt = bop.getPlanType();
        if (pt == null || !BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1.equals(pt.trim())) {
            return false;
        }
        List<String> missing = bop.getMissingSections();
        if (missing != null && !missing.isEmpty()) {
            return false;
        }
        return state.getRevenueAnswerPlan() != null
                && state.getPurchaseAnswerPlan() != null
                && state.getStockReduceAnswerPlan() != null
                && state.getDishProfitAnswerPlan() != null;
    }

    private String composeBusinessOverviewMultiAgentFourDomainMarkdown(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String bracket = tw.getBracketTimeRangeLine().trim();
        BusinessOverviewAnswerPlan bop = state.getBusinessOverviewAnswerPlan();

        String revenueBlock =
                nz(BusinessOverviewDeterministicSummaryBuilder.composeRevenueDeterministicFromAnswerPlan(
                        state.getRevenueAnswerPlan(), tw));
        String purchaseBlock = nz(composePurchaseDeterministicFromAnswerPlan(state.getPurchaseAnswerPlan(), tw));
        String stockBlock =
                nz(composeStockReduceDeterministicFromAnswerPlan(state.getStockReduceAnswerPlan(), tw, state));
        String dishBlock = nz(deterministicAnswerRenderer.renderDishProfitAnswerPlanOneLiner(
                state.getDishProfitAnswerPlan()));

        StringBuilder sb = new StringBuilder();
        sb.append("【经营概览·四域汇总】\n");
        sb.append(bracket).append('\n');
        String scope = resolveBusinessOverviewAggregateScopeLabel(bop, state);
        if (!scope.isBlank()) {
            sb.append("组织范围：").append(scope.trim()).append('\n');
        }
        sb.append("\n本轮四域子计划均已返回；以下为对各子域 AnswerPlan 的直接宣读（未重算）：\n\n");

        sb.append("【营业额】\n");
        sb.append(orNonBlankParagraph(
                stripDuplicateBracketTimeLine(revenueBlock, bracket), "营业额侧概要暂不可用。"));
        sb.append("\n\n【采购】\n");
        sb.append(orNonBlankParagraph(
                stripDuplicateBracketTimeLine(purchaseBlock, bracket), "采购侧概要暂不可用。"));
        sb.append("\n\n【出库/核销】\n");
        sb.append(orNonBlankParagraph(
                stripDuplicateBracketTimeLine(stockBlock, bracket), "出库/核销侧概要暂不可用。"));
        sb.append("\n\n【菜品毛利】\n");
        sb.append(orNonBlankParagraph(dishBlock, "菜品毛利侧概要暂不可用。"));

        sb.append("\n\n【重点问题】\n");
        appendBusinessOverviewAggregateFocusIssues(sb, state);

        sb.append("\n【下一步建议】\n");
        sb.append("1. 需要某一域更细拆分（渠道、排行、SKU 明细等），请切换到该业务专线再问。\n");
        sb.append("2. 可对照页面经营概览卡片查看结构化明细。\n");

        return sb.toString().trim();
    }

    private static String resolveBusinessOverviewAggregateScopeLabel(BusinessOverviewAnswerPlan bop, AiRunState state) {
        if (bop != null && !nz(bop.getScopeLabel()).isBlank()) {
            return nz(bop.getScopeLabel()).trim();
        }
        if (state.getRevenueAnswerPlan() != null && !nz(state.getRevenueAnswerPlan().getScopeLabel()).isBlank()) {
            return nz(state.getRevenueAnswerPlan().getScopeLabel()).trim();
        }
        if (state.getPurchaseAnswerPlan() != null && !nz(state.getPurchaseAnswerPlan().getScopeLabel()).isBlank()) {
            return nz(state.getPurchaseAnswerPlan().getScopeLabel()).trim();
        }
        if (state.getStockReduceAnswerPlan() != null
                && !nz(state.getStockReduceAnswerPlan().getScopeLabel()).isBlank()) {
            return nz(state.getStockReduceAnswerPlan().getScopeLabel()).trim();
        }
        if (state.getDishProfitAnswerPlan() != null && !nz(state.getDishProfitAnswerPlan().getScopeLabel()).isBlank()) {
            return nz(state.getDishProfitAnswerPlan().getScopeLabel()).trim();
        }
        return "";
    }

    private static void appendBusinessOverviewAggregateFocusIssues(StringBuilder sb, AiRunState state) {
        DiagnosisPlan dp = state != null ? state.getDiagnosisPlan() : null;
        if (dp == null || dp.getFocusFindings() == null || dp.getFocusFindings().isEmpty()) {
            sb.append("暂无。\n");
            return;
        }
        int printed = 0;
        int maxOut = 3;
        int seq = 1;
        for (Map<String, Object> row : dp.getFocusFindings()) {
            if (printed >= maxOut) {
                break;
            }
            if (row == null || row.isEmpty()) {
                continue;
            }
            Object d = row.get("detail");
            if (d == null || d.toString().isBlank()) {
                continue;
            }
            sb.append(seq++).append(". ").append(d.toString().trim()).append('\n');
            printed++;
        }
        if (printed == 0) {
            sb.append("暂无。\n");
        }
    }

    private static String stripDuplicateBracketTimeLine(String block, String bracketLine) {
        if (block == null || block.isBlank()) {
            return "";
        }
        String trimmed = block.trim();
        if (bracketLine == null || bracketLine.isBlank()) {
            return trimmed;
        }
        String br = bracketLine.trim();
        int nl = trimmed.indexOf('\n');
        String firstLine = nl < 0 ? trimmed : trimmed.substring(0, nl).trim();
        if (!firstLine.equals(br)) {
            return trimmed;
        }
        return nl < 0 ? "" : trimmed.substring(nl + 1).trim();
    }

    private static String orNonBlankParagraph(String s, String placeholder) {
        return s != null && !s.isBlank() ? s.trim() : placeholder;
    }

    /**
     * 采购 AnswerPlan：排序与选行已在 Builder 完成；此处仅宣读 focusRows / secondaryRows，不重算、不重排。
     *
     * @return 可展示的确定性正文；不满足条件时返回 {@code null} 交由 summary / LLM fallback。
     */
    private static String composePurchaseDeterministicFromAnswerPlan(PurchaseAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        if (plan == null || plan.getFocusRows() == null || plan.getPlanType() == null || plan.getPlanType().isBlank()) {
            return null;
        }
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, java.time.LocalDate.now());
        String type = plan.getPlanType().trim();
        if (PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW.equals(type)) {
            return composePurchaseOverviewTotalsFromPlan(plan, p, false, false);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW.equals(type)) {
            return composePurchaseOverviewTotalsFromPlan(plan, p, true, false);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW.equals(type)) {
            return composePurchaseOverviewTotalsFromPlan(plan, p, false, true);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(type)) {
            return composePurchaseGoodsAmountRankingFromPlan(plan, p);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(type)) {
            return composePurchaseGoodsCountRankingFromPlan(plan, p);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(type)) {
            return composePurchaseSupplierAmountRankingFromPlan(plan, p);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING.equals(type)) {
            return composePurchaseStoreAmountRankingFromPlan(plan, p);
        }
        return null;
    }

    /** 并排门店采购金额（AnswerPlan Builder 已定序）。 */
    private static String composePurchaseStoreAmountRankingFromPlan(PurchaseAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (focus == null || focus.isEmpty()) {
            sb.append(tw.getDisplayTimeRange()).append(
                    "，当前未能按门店对比采购入库金额范围（请在问题中并排点到具体门店并保持可见范围）。");
            return sb.toString();
        }
        Map<String, Object> top = focus.get(0);
        sb.append(tw.getDisplayTimeRange()).append("，").append(purchaseStoreLabelFromPurchasePlanRow(top))
                .append("采购金额为").append(plainNumericHint(purchaseStorePurchaseSubtotalFromRow(top))).append("元");
        sb.append(buildPurchaseStoreRankingTail(sec));
        sb.append("。");
        return sb.toString();
    }

    private static String purchaseStoreLabelFromPurchasePlanRow(Map<String, Object> row) {
        if (row == null) {
            return "该门店";
        }
        Object n = row.get("storeName");
        return n != null && !n.toString().isBlank() ? n.toString().trim() + "：" : "该门店：";
    }

    private static Object purchaseStorePurchaseSubtotalFromRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("purchaseSubtotal");
        if (v == null) {
            v = row.get("totalPurchaseAmount");
        }
        return v;
    }

    /** @param secondaryRows AnswerPlan.secondaryRows（其余门店）；用于「其后依次为」。 */
    private static String buildPurchaseStoreRankingTail(List<Map<String, Object>> secondaryRows) {
        if (secondaryRows.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> r : secondaryRows) {
            parts.add(purchaseStoreLabelFromPurchasePlanRow(r).replace("：", "").trim()
                    + "采购金额" + plainNumericHint(purchaseStorePurchaseSubtotalFromRow(r)) + "元");
        }
        return "；其后依次为：" + String.join("；", parts);
    }

    private static String composePurchaseOverviewTotalsFromPlan(PurchaseAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw, boolean self, boolean supplierChannel) {
        List<Map<String, Object>> fr = plan.getFocusRows();
        if (fr.isEmpty()) {
            return null;
        }
        Map<String, Object> row = fr.get(0);
        int cnt = intHint(row.get("purchaseOrderCount"));
        String amt = plainNumericHint(row.get("totalPurchaseAmount"));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        String range = tw.getDisplayTimeRange();
        if (self) {
            sb.append(range).append("，自采金额为").append(amt).append("元，共").append(cnt).append("笔自采入库。");
        } else if (supplierChannel) {
            sb.append(range).append("，供货商渠道采购金额为").append(amt).append("元，共").append(cnt).append("笔供货商采购入库。");
        } else {
            sb.append(range).append("，采购入库总金额为").append(amt).append("元，共").append(cnt).append("笔。");
        }
        return sb.toString();
    }

    private static String goodsNameFromPurchasePlanRow(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object g = row.get("goodsName");
        return g != null ? g.toString().trim() : "";
    }

    private static Object purchaseGoodsAmountFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("purchaseSubtotal");
        if (v == null) {
            v = row.get("totalPurchaseAmount");
        }
        return v;
    }

    private static Object purchaseGoodsCountFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("purchaseTimes");
        if (v == null) {
            v = row.get("purchaseCount");
        }
        if (v == null) {
            v = row.get("purchaseLineCount");
        }
        return v;
    }

    private static Object supplierPurchaseAmountFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("totalPurchaseAmount");
        if (v == null) {
            v = row.get("purchaseAmount");
        }
        return v;
    }

    private static String supplierNameFromPurchasePlanRow(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object n = row.get("supplierName");
        return n != null ? n.toString().trim() : "";
    }

    private static boolean purchasePlanRowCountsEqual(Map<String, Object> row, Object topCountObj) {
        String a = plainNumericHint(purchaseGoodsCountFromPlanRow(row));
        String b = plainNumericHint(topCountObj);
        return Objects.equals(a, b);
    }

    private static String composePurchaseGoodsAmountRankingFromPlan(PurchaseAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (focus.isEmpty()) {
            sb.append(tw.getDisplayTimeRange()).append("暂未查询到采购商品金额排行数据。");
            return sb.toString();
        }
        Map<String, Object> top = focus.get(0);
        sb.append(tw.getDisplayTimeRange()).append("，采购金额最高的商品为")
                .append(nz(goodsNameFromPurchasePlanRow(top)))
                .append("，约")
                .append(plainNumericHint(purchaseGoodsAmountFromPlanRow(top)))
                .append("元。");
        List<String> restParts = new ArrayList<>();
        for (int i = 1; i < focus.size(); i++) {
            Map<String, Object> r = focus.get(i);
            restParts.add(nz(goodsNameFromPurchasePlanRow(r)) + "约" + plainNumericHint(purchaseGoodsAmountFromPlanRow(r))
                    + "元");
        }
        for (Map<String, Object> r : sec) {
            restParts.add(nz(goodsNameFromPurchasePlanRow(r)) + "约" + plainNumericHint(purchaseGoodsAmountFromPlanRow(r))
                    + "元");
        }
        if (!restParts.isEmpty()) {
            sb.append("其后依次为：").append(String.join("；", restParts)).append("。");
        }
        return sb.toString();
    }

    private static String composePurchaseGoodsCountRankingFromPlan(PurchaseAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (focus.isEmpty()) {
            sb.append(tw.getDisplayTimeRange()).append("暂未查询到采购商品次数排行数据。");
            return sb.toString();
        }
        List<Map<String, Object>> ordered = new ArrayList<>(focus.size() + sec.size());
        ordered.addAll(focus);
        ordered.addAll(sec);
        Object topCountObj = purchaseGoodsCountFromPlanRow(ordered.get(0));
        String topCountDisp = plainNumericHint(topCountObj);
        int i = 0;
        while (i < ordered.size() && purchasePlanRowCountsEqual(ordered.get(i), topCountObj)) {
            i++;
        }
        List<String> tieNames = new ArrayList<>();
        for (int k = 0; k < i; k++) {
            String nm = nz(goodsNameFromPurchasePlanRow(ordered.get(k)));
            if (!nm.isBlank()) {
                tieNames.add(nm);
            }
        }
        if (tieNames.isEmpty()) {
            return null;
        }
        String range = tw.getDisplayTimeRange();
        if (tieNames.size() > 1) {
            sb.append(range).append("，采购次数最多的商品包括").append(String.join("、", tieNames)).append("，均为")
                    .append(topCountDisp).append("次。");
        } else {
            sb.append(range).append("，采购次数最多的商品为").append(tieNames.get(0)).append("，共").append(topCountDisp)
                    .append("次。");
        }
        List<String> restParts = new ArrayList<>();
        while (i < ordered.size()) {
            Map<String, Object> r = ordered.get(i);
            restParts.add(nz(goodsNameFromPurchasePlanRow(r)) + "共" + plainNumericHint(purchaseGoodsCountFromPlanRow(r))
                    + "次");
            i++;
        }
        if (!restParts.isEmpty()) {
            sb.append("其后依次为：").append(String.join("；", restParts)).append("。");
        }
        return sb.toString();
    }

    private static int supplierRankingLineCountHint(Map<?, ?> row) {
        int a = intHint(row.get("purchaseLineCount"));
        if (a > 0) {
            return a;
        }
        return intHint(row.get("purchaseOrderCount"));
    }

    private static String composePurchaseSupplierAmountRankingFromPlan(PurchaseAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        if (focus.isEmpty()) {
            sb.append("当前口径下暂未查询到真实供货商采购记录；本期采购主要为自采或未挂靠供货商采购。");
            return sb.toString();
        }
        Map<String, Object> top = focus.get(0);
        int lines = supplierRankingLineCountHint(top);
        sb.append(tw.getDisplayTimeRange()).append("，供货商采购金额第一名为").append(nz(supplierNameFromPurchasePlanRow(top)))
                .append("，采购金额").append(plainNumericHint(supplierPurchaseAmountFromPlanRow(top))).append("元，共")
                .append(lines > 0 ? lines : Math.max(intHint(top.get("purchaseCount")), intHint(top.get("orderCount"))))
                .append("笔。");
        if (!sec.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map<String, Object> r : sec) {
                int ln = supplierRankingLineCountHint(r);
                parts.add(nz(supplierNameFromPurchasePlanRow(r)) + "采购金额"
                        + plainNumericHint(supplierPurchaseAmountFromPlanRow(r)) + "元，共"
                        + (ln > 0 ? ln : Math.max(intHint(r.get("purchaseCount")), intHint(r.get("orderCount")))) + "笔");
            }
            sb.append("其后依次为：").append(String.join("；", parts)).append("。");
        }
        return sb.toString();
    }


    private static boolean isBusinessToWarehouseStockConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "WAREHOUSE_STOCK_OVERVIEW".equals(ic.get("to"));
    }

    private static String resolveAiRoleCode(AiRunState state) {
        AiUserContext ctx = state.getAiUserContext();
        if (ctx != null && ctx.getRoleCode() != null && !ctx.getRoleCode().isBlank()) {
            return ctx.getRoleCode().trim();
        }
        if (ctx != null && ctx.getSourceAdminRole() != null) {
            return AiRoleMapper.resolveAdmin(ctx.getSourceAdminRole())
                    .map(AiRoleMapper.AiRoleDefinition::roleCode)
                    .orElse("");
        }
        return "";
    }

    private static boolean isPurchasingRoleForWarehouse(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return AiRoleCodes.STORE_PURCHASER.equals(roleCode)
                || AiRoleCodes.GROUP_PURCHASER.equals(roleCode)
                || AiRoleCodes.WAREHOUSE_PURCHASER.equals(roleCode)
                || AiRoleCodes.CENTRAL_KITCHEN_PURCHASER.equals(roleCode)
                || AiRoleCodes.REGION_PURCHASER.equals(roleCode);
    }

    private static boolean isWarehouseStaffRole(String roleCode) {
        return AiRoleCodes.WAREHOUSE_MANAGER.equals(roleCode)
                || AiRoleCodes.REGION_WAREHOUSE.equals(roleCode);
    }

    /**
     * 是否禁止在库存答复中使用「店长」等称呼（与产品约定一致：仅非集团视角下的店长岗可称店长）。
     */
    private static boolean forbidsStoreManagerSalutation(String roleCode, boolean groupScope) {
        if (groupScope) {
            return true;
        }
        if (roleCode == null || roleCode.isBlank()) {
            return true;
        }
        if (AiRoleCodes.GROUP_MANAGER.equals(roleCode)) {
            return true;
        }
        if (isWarehouseStaffRole(roleCode)) {
            return true;
        }
        if (isPurchasingRoleForWarehouse(roleCode)) {
            return true;
        }
        return !AiRoleCodes.STORE_MANAGER.equals(roleCode);
    }

    private static boolean warehouseOverviewIndicatesGroupScope(Map<String, Object> wo) {
        return wo != null && !wo.isEmpty()
                && "GROUP".equalsIgnoreCase(String.valueOf(wo.get("scopeType")).trim());
    }

    private static String warehouseSalutationDirective(AiRunState state, Map<String, Object> wo) {
        String rc = resolveAiRoleCode(state);
        boolean groupScope = state.isGroupWarehouseStockOverview() || warehouseOverviewIndicatesGroupScope(wo);
        if (AiRoleCodes.GROUP_MANAGER.equals(rc) || groupScope) {
            return "【开篇】用「以下是集团范围库存汇总」或等价客观句起首（可接门店名枚举）；禁止「店长」「老板」及「好的，店长」类寒暄；不要反问指定门店。";
        }
        if (AiRoleCodes.STORE_MANAGER.equals(rc)) {
            return "【开篇】可称呼「店长」，也可无称呼直接写库存数据。";
        }
        if (isWarehouseStaffRole(rc)) {
            return "【开篇】用「以下是你当前可查看库房/所属门店」类客观句起首（可与 queryScopeBanner 一致带出门店名）；禁止「店长」「老板」；勿写「店长，本库房…」。";
        }
        if (isPurchasingRoleForWarehouse(rc)) {
            return "【开篇】可用「以下按采购视角分析」起首（再写库存数字）；禁止「店长」「老板」。";
        }
        return "【开篇】若不确定对方具体岗位，不要使用老板/店长/库管等称呼；直接写库存客观表述。";
    }

    private static String applyWarehouseSalutationPolicy(String answer, AiRunState state, Map<String, Object> wo) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String rc = resolveAiRoleCode(state);
        boolean groupScope = state.isGroupWarehouseStockOverview() || warehouseOverviewIndicatesGroupScope(wo);
        if (!forbidsStoreManagerSalutation(rc, groupScope)) {
            return answer;
        }
        String t = answer.trim();
        for (int i = 0; i < 10; i++) {
            Matcher m1 = WAREHOUSE_LEADING_SALUTATION.matcher(t);
            if (m1.find() && m1.start() == 0) {
                t = t.substring(m1.end()).trim();
                continue;
            }
            Matcher m2 = WAREHOUSE_LEADING_MANAGER_NO_PUNCT.matcher(t);
            if (m2.find() && m2.start() == 0) {
                t = t.substring(m2.end()).trim();
                continue;
            }
            break;
        }
        return t.isBlank() ? answer : t;
    }

    private static Map<String, Object> buildWarehouseOverviewStructured(AiRunState state) {
        Map<String, Object> wo = extractWarehouseOverviewPayload(state);
        if (!wo.isEmpty()) {
            return new LinkedHashMap<>(wo);
        }
        Map<String, Object> sq = toolDataInnerMap(state, AiBusinessToolIds.STOCK_QUERY);
        Map<String, Object> stk = toolDataInnerMap(state, AiBusinessToolIds.STOCK_REDUCE_QUERY);
        LinkedHashMap<String, Object> legacy = new LinkedHashMap<>();
        List<String> metrics = new ArrayList<>();
        if (!sq.isEmpty()) {
            metrics.add("库存批次行数 " + plainNumericHint(sq.get("stockBatchRowCount")));
            metrics.add("库存剩余金额约 " + plainNumericHint(sq.get("stockRestSubtotal")) + " 元");
            metrics.add("库存剩余重量 " + fmtStockWeightCn(sq.get("stockRestWeightTotal")));
            metrics.add("区间内入库金额约 " + plainNumericHint(sq.get("periodInboundSubtotal")) + " 元");
            metrics.add("区间内入库重量 " + fmtStockWeightCn(sq.get("periodInboundWeightTotal")));
        }
        if (!stk.isEmpty()) {
            metrics.add("核销生产耗用合计 " + plainNumericHint(stk.get("productionTotal")));
        }
        legacy.put("keyMetrics", metrics);
        legacy.put("stockWarnings", new ArrayList<String>());
        List<String> rec = new ArrayList<>();
        rec.add("重点核对盘点剩余与核销明细是否闭合；异常批次建议在库存模块复查。");
        legacy.put("recommendations", rec);
        String summary = (sq.isEmpty() && stk.isEmpty())
                ? "暂无可用库房库存汇总数据。"
                : "已按库房权限汇总库存剩余与区间内入库，并结合核销/出库结构给出摘要（旧版降级字段）。";
        legacy.put("summary", summary);
        return legacy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPurchaseOverviewPayload(AiRunState state) {
        Map<String, Object> inner = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        Object po = inner.get("purchaseOverview");
        if (!(po instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> raw = (Map<String, Object>) po;
        if (raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractWarehouseOverviewPayload(AiRunState state) {
        Map<String, Object> inner = toolDataInnerMap(state, AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        Object wo = inner.get("warehouseOverview");
        if (!(wo instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> raw = (Map<String, Object>) wo;
        if (raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }


    private static int intHint(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 出库 AnswerPlan：仅宣读 {@link StockReduceAnswerPlan} 的 focusRows / secondaryRows，不重算、不重排、不改口径。
     *
     * @return 可展示的确定性正文；{@code null} 表示交由出库/核销工具结果确定性朗读。
     */
    private static String composeStockReduceDeterministicFromAnswerPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw, AiRunState state) {
        if (plan == null || plan.getPlanType() == null || plan.getPlanType().isBlank()) {
            return null;
        }
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, java.time.LocalDate.now());
        String type = plan.getPlanType().trim();
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW.equals(type)) {
            return composeStockReduceOverviewFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_PRODUCTION_OVERVIEW.equals(type)) {
            return composeStockReduceProductionOverviewFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW.equals(type)) {
            return composeStockReduceOutputOverviewFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_WASTE_OVERVIEW.equals(type)) {
            return composeStockReduceWasteOverviewFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_LOSS_OVERVIEW.equals(type)) {
            return composeStockReduceLossOverviewFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_RETURN_OVERVIEW.equals(type)) {
            return composeStockReduceReturnOverviewFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_AMOUNT_RANKING.equals(type)) {
            return composeStockReduceGoodsAmountRankingFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING.equals(type)) {
            return composeStockReduceGoodsCountRankingFromPlan(plan, p);
        }
        if (StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING.equals(type)) {
            return composeStockReduceStoreAmountRankingFromPlan(plan, p, state);
        }
        return null;
    }

    /** 并排门店出库/核销四类金额合计对比（与非商品排行口径一致的自然说明）。 */
    private static String composeStockReduceStoreAmountRankingFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw, AiRunState state) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        if (focus == null || focus.isEmpty()) {
            if (composerSingleVisibleStoreRankingDegradeEligible(state)) {
                String storeLabel = nz(firstVisibleScopedStoreHumanName(state)).trim();
                if (storeLabel.isEmpty()) {
                    storeLabel = "该门店";
                }
                sb.append(stockReducePlanLead(plan, tw)).append(
                        "，你权限范围内仅能查看一家门店「" + storeLabel
                                + "」，因此出库/核销四类金额合计最高的就是这一家；无法进行多门店并排排行对比。");
            } else {
                sb.append(stockReducePlanLead(plan, tw)).append(
                        "，当前未能生成门店维度的出库/核销金额并排对比结果，请稍后重试或并排点到具体门店。");
            }
            return sb.toString();
        }
        Map<String, Object> top = focus.get(0);
        sb.append(stockReducePlanLead(plan, tw)).append("，出库/核销四类金额合计较高的是 ")
                .append(nz(stockReduceStoreCaptionFromPlanRow(top))).append("，约 ")
                .append(plainNumericHint(stockReduceAmountFromPlanRow(top))).append(" 元。");
        if (!sec.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map<String, Object> r : sec) {
                parts.add(nz(stockReduceStoreCaptionFromPlanRow(r)) + "约 "
                        + plainNumericHint(stockReduceAmountFromPlanRow(r)) + " 元");
            }
            sb.append("其后依次为：").append(String.join("；", parts)).append("。");
        }
        return sb.toString();
    }

    /**
     * 店长/门店采购单锚点 Scope：visibleStores.size==1 时，门店额排行 AnswerPlan 可能无 focusRows，但语义上仍可解读为「该店即为最高」。
     */
    private static boolean composerSingleVisibleStoreRankingDegradeEligible(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
        if (org == null) {
            return false;
        }
        String scopeType = org.getScopeType();
        if (!AiResolvedOrgScope.SCOPE_STORE.equals(scopeType)
                && !AiResolvedOrgScope.SCOPE_PURCHASER.equals(scopeType)) {
            return false;
        }
        List<AiStoreScopeDTO> vis = org.getVisibleStores();
        return vis != null && vis.size() == 1;
    }

    private static String firstVisibleScopedStoreHumanName(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        if (ctx == null) {
            return "";
        }
        AiResolvedOrgScope org = ctx.getOrgScope();
        if (org == null || org.getVisibleStores() == null) {
            return "";
        }
        for (AiStoreScopeDTO row : org.getVisibleStores()) {
            if (row != null && StringUtils.hasText(row.getStoreName())) {
                return row.getStoreName().trim();
            }
        }
        return "";
    }

    private static String stockReduceStoreCaptionFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return "该门店";
        }
        Object n = row.get("storeName");
        if (n != null && !n.toString().isBlank()) {
            return n.toString().trim();
        }
        Object id = row.get("storeDepartmentId");
        return id != null ? "门店 " + id : "该门店";
    }

    private static String stockReducePlanLead(StockReduceAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        String lead = tw.getDisplayTimeRange();
        String scope = plan.getScopeLabel();
        if (scope != null && !scope.isBlank()) {
            lead = scope.trim() + "；" + lead;
        }
        return lead;
    }

    private static String stockReduceGoodsNameFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object g = row.get("goodsName");
        if (g == null) {
            g = row.get("name");
        }
        return g != null ? g.toString().trim() : "";
    }

    private static Object stockReduceAmountFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("amount");
        if (v == null) {
            v = row.get("totalAmount");
        }
        if (v == null) {
            v = row.get("subtotal");
        }
        if (v == null) {
            v = row.get("reduceAmount");
        }
        if (v == null) {
            v = row.get("totalReduceAmount");
        }
        if (v == null) {
            v = row.get("outboundAmount");
        }
        if (v == null) {
            v = row.get("grandTotalFourTypes");
        }
        return v;
    }

    private static Object stockReduceCountFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object v = row.get("outboundTimes");
        if (v == null) {
            v = row.get("reduceTimes");
        }
        if (v == null) {
            v = row.get("count");
        }
        if (v == null) {
            v = row.get("times");
        }
        return v;
    }

    private static String stockReduceLabelFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object l = row.get("label");
        return l != null ? l.toString().trim() : "";
    }

    private static boolean stockReducePlanRowCountsEqual(Map<String, Object> row, Object topCountObj) {
        String a = plainNumericHint(stockReduceCountFromPlanRow(row));
        String b = plainNumericHint(topCountObj);
        return Objects.equals(a, b);
    }

    private static boolean stockReduceSingleTypeRowMissingOrZero(List<Map<String, Object>> focus) {
        if (focus == null || focus.isEmpty()) {
            return true;
        }
        return parseDoubleLoose(stockReduceAmountFromPlanRow(focus.get(0))) == 0.0;
    }

    private static String composeStockReduceOverviewFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        Map<String, Object> row0 = focus.get(0);
        String totalAmt = plainNumericHint(stockReduceAmountFromPlanRow(row0));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(stockReducePlanLead(plan, tw)).append("，本期出库/核销合计金额约 ").append(totalAmt).append(" 元。");
        List<Map<String, Object>> sec = plan.getSecondaryRows();
        if (sec != null && !sec.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map<String, Object> r : sec) {
                String lbl = stockReduceLabelFromPlanRow(r);
                if (lbl.isBlank()) {
                    lbl = nz(r.get("reduceType"));
                }
                parts.add(lbl + " " + plainNumericHint(stockReduceAmountFromPlanRow(r)) + " 元");
            }
            if (!parts.isEmpty()) {
                sb.append("其中").append(String.join("、", parts)).append("。");
            }
        }
        return sb.toString();
    }

    private static String composeStockReduceProductionOverviewFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        String amt = plainNumericHint(stockReduceAmountFromPlanRow(focus.get(0)));
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(stockReducePlanLead(plan, tw)).append("，生产耗用金额约 ").append(amt).append(" 元。");
        return sb.toString();
    }

    private static String composeStockReduceOutputOverviewFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        String amt = plainNumericHint(stockReduceAmountFromPlanRow(focus.get(0)));
        boolean outputNarr =
                plan.getDebug() != null && "OUTPUT".equals(String.valueOf(plan.getDebug().get("narrative")));
        String phrase = outputNarr ? "出品耗用" : "出品用量";
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        sb.append(stockReducePlanLead(plan, tw)).append("，").append(phrase).append("金额约 ").append(amt).append(" 元。");
        return sb.toString();
    }

    private static String composeStockReduceWasteOverviewFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        if (stockReduceSingleTypeRowMissingOrZero(focus)) {
            sb.append(stockReducePlanLead(plan, tw)).append("，当前口径下暂未查询到对应类型的出库/核销金额，或本期金额为 0。");
            return sb.toString();
        }
        String amt = plainNumericHint(stockReduceAmountFromPlanRow(focus.get(0)));
        sb.append(stockReducePlanLead(plan, tw)).append("，废弃（type2）金额约 ").append(amt).append(" 元。");
        return sb.toString();
    }

    private static String composeStockReduceLossOverviewFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        if (stockReduceSingleTypeRowMissingOrZero(focus)) {
            sb.append(stockReducePlanLead(plan, tw)).append("，当前口径下暂未查询到对应类型的出库/核销金额，或本期金额为 0。");
            return sb.toString();
        }
        String amt = plainNumericHint(stockReduceAmountFromPlanRow(focus.get(0)));
        sb.append(stockReducePlanLead(plan, tw)).append("，损耗/报损（type3）金额约 ").append(amt).append(" 元。");
        return sb.toString();
    }

    private static String composeStockReduceReturnOverviewFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        if (stockReduceSingleTypeRowMissingOrZero(focus)) {
            sb.append(stockReducePlanLead(plan, tw)).append("，当前口径下暂未查询到对应类型的出库/核销金额，或本期金额为 0。");
            return sb.toString();
        }
        String amt = plainNumericHint(stockReduceAmountFromPlanRow(focus.get(0)));
        sb.append(stockReducePlanLead(plan, tw)).append("，退货（type4）金额约 ").append(amt).append(" 元。");
        return sb.toString();
    }

    private static String composeStockReduceGoodsAmountRankingFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        if (focus == null || focus.isEmpty()) {
            sb.append(stockReducePlanLead(plan, tw)).append("，当前口径下暂未查询到商品出库排行数据。");
            return sb.toString();
        }
        Map<String, Object> top = focus.get(0);
        sb.append(stockReducePlanLead(plan, tw)).append("，出库金额最高的商品为")
                .append(nz(stockReduceGoodsNameFromPlanRow(top))).append("，约")
                .append(plainNumericHint(stockReduceAmountFromPlanRow(top))).append(" 元。");
        List<String> restParts = new ArrayList<>();
        for (Map<String, Object> r : sec) {
            restParts.add(nz(stockReduceGoodsNameFromPlanRow(r)) + "约"
                    + plainNumericHint(stockReduceAmountFromPlanRow(r)) + " 元");
        }
        if (!restParts.isEmpty()) {
            sb.append("其后依次为：").append(String.join("；", restParts)).append("。");
        }
        return sb.toString();
    }

    private static String composeStockReduceGoodsCountRankingFromPlan(StockReduceAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append('\n');
        if (focus == null || focus.isEmpty()) {
            sb.append(stockReducePlanLead(plan, tw)).append("，当前口径下暂未查询到商品出库排行数据。");
            return sb.toString();
        }
        List<Map<String, Object>> ordered = new ArrayList<>(focus.size() + sec.size());
        ordered.addAll(focus);
        ordered.addAll(sec);
        Object topCountObj = stockReduceCountFromPlanRow(ordered.get(0));
        String topCountDisp = plainNumericHint(topCountObj);
        int i = 0;
        while (i < ordered.size() && stockReducePlanRowCountsEqual(ordered.get(i), topCountObj)) {
            i++;
        }
        List<String> tieNames = new ArrayList<>();
        for (int k = 0; k < i; k++) {
            String nm = nz(stockReduceGoodsNameFromPlanRow(ordered.get(k)));
            if (!nm.isBlank()) {
                tieNames.add(nm);
            }
        }
        if (tieNames.isEmpty()) {
            sb.append(stockReducePlanLead(plan, tw)).append("，当前口径下暂未查询到商品出库排行数据。");
            return sb.toString();
        }
        String lead = stockReducePlanLead(plan, tw);
        if (tieNames.size() > 1) {
            sb.append(lead).append("，出库次数最多的商品包括").append(String.join("、", tieNames)).append("，均为")
                    .append(topCountDisp).append(" 次。");
        } else {
            sb.append(lead).append("，出库次数最多的商品为").append(tieNames.get(0)).append("，共").append(topCountDisp)
                    .append(" 次。");
        }
        List<String> restParts = new ArrayList<>();
        while (i < ordered.size()) {
            Map<String, Object> r = ordered.get(i);
            restParts.add(nz(stockReduceGoodsNameFromPlanRow(r)) + "共"
                    + plainNumericHint(stockReduceCountFromPlanRow(r)) + " 次");
            i++;
        }
        if (!restParts.isEmpty()) {
            sb.append("其后依次为：").append(String.join("；", restParts)).append("。");
        }
        return sb.toString();
    }

    /** 出库/核销专线 stub：可读数字 + 分项，避免走错采购/成本话术。 */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolDataInnerMap(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object data = ((Map<String, Object>) env).get("data");
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
    }

    /** 经营诊断/菜品毛利上下文中「某菜为什么这么低」：禁止落入泛泛经营建议，引导走 harness。 */
    private static boolean genericChatBlockedForDishReasonInDiagnosisContext(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryContext rqc = state.getResolvedQueryContext();
        String structured =
                rqc.getQueryIntent() != null ? rqc.getQueryIntent().getStructuredIntentDetail() : null;
        if (!AiQuerySemanticLexicon.isDishLowProfitReasonStructuredWire(structured)) {
            return false;
        }
        String dish = AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(rqc.getMentionedDishName());
        if (dish == null || dish.isBlank()) {
            if (rqc.getQuerySemanticParse() != null) {
                dish = AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(
                        rqc.getQuerySemanticParse().getMentionedDishName());
            }
        }
        if (dish == null || dish.isBlank()) {
            return false;
        }
        AiConversationTurnMemory prev = state.getResolvedQueryContext().getPreviousTurn();
        if (prev == null) {
            return false;
        }
        String p = prev.getLastPathCode();
        return AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(p)
                || AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(p);
    }

    /**
     * D-11：营业额意图且营收权限/工具被拒、未命中其它专属 Composer 分支时，仅用权限说明正文，禁止 generic LLM 捏造「零元 / 数据不足」。
     */
    private static boolean composerEmitRevenueDeniedPermissionOnly(AiRunState state) {
        if (state == null || state.getPermissionDenials() == null || state.getPermissionDenials().isEmpty()) {
            return false;
        }
        if (!AiAnswerBoundary.isRevenuePermissionDenied(state.getPermissionDenials())) {
            return false;
        }
        if (!resolvedIntentIsRevenueOverview(state)) {
            return false;
        }
        if (state.isRevenueOverviewPath()) {
            return false;
        }
        if (state.isWarehouseStockOverviewPath()
                || state.isStockReduceQueryPath()
                || state.isPurchaseCostInsightPath()
                || state.isBusinessDiagnosisPath()
                || state.isDishProfitPath()
                || dishSalesDeterministicEligible(state)) {
            return false;
        }
        if (state.getCostDiagnosisResult() != null) {
            return false;
        }
        if (!DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)
                && state.getDiagnosisPlan() != null
                && DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS.equals(state.getDiagnosisPlan().getPlanType())
                && (state.isBusinessDiagnosisPath()
                        || DiagnosisPlanBuilder.shouldPreferDiagnosisPlanInComposer(state))) {
            return false;
        }
        if (state.isBusinessDiagnosisPath() && state.getBusinessDiagnosisPlan() != null) {
            return false;
        }
        return true;
    }

    private static boolean resolvedIntentIsRevenueOverview(AiRunState state) {
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        if (StringUtils.hasText(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.REVENUE_OVERVIEW.equals(rq.getEffectiveIntentCode().trim())) {
            return true;
        }
        if (StringUtils.hasText(rq.getEffectivePathCode())
                && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(rq.getEffectivePathCode().trim())) {
            return true;
        }
        AiResolvedQueryIntent qi = rq.getQueryIntent();
        if (qi == null) {
            return false;
        }
        if (StringUtils.hasText(qi.getIntentCode())
                && AiResolvedQueryIntent.REVENUE_OVERVIEW.equals(qi.getIntentCode().trim())) {
            return true;
        }
        return StringUtils.hasText(qi.getPathCode())
                && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(qi.getPathCode().trim());
    }

    /** 不向模型暴露 Workspace、Tool 英文名与原始 trace。 */
    private static LinkedHashMap<String, Object> composeSafeFallbackContext(AiRunState state) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("本轮用户输入", nz(state.getNormalizedUserInput()));
        String start = state.getStatStartDate();
        String end = state.getStatEndDate();
        if (start != null && end != null && !start.isBlank() && !end.isBlank()) {
            m.put("统计口径起止日期", start + " 至 " + end);
        }
        return m;
    }
}
