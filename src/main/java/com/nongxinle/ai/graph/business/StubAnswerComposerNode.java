package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.dto.business.DishIngredientCoverAnswerPlan;
import com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.composer.AiAnswerContextSummarySupport;
import com.nongxinle.ai.composer.AnswerBoundaryNoteComposer;
import com.nongxinle.ai.composer.menu.MenuExpertPresentationComposeResult;
import com.nongxinle.ai.composer.menu.MenuOperationExpertNarrativeComposer;
import com.nongxinle.ai.composer.DishProfitRankingCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.composer.DishSalesRankingCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.composer.warehouse.GoodsSupportedDishCoverCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.composer.menu.DishIngredientCoverCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.composer.menu.DishProfitPrescriptionCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.composer.menu.MenuOperationCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.composer.warehouse.WarehouseInventoryRiskCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.composer.warehouse.WarehouseStockRankingCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.composer.renderer.DiagnosisDeterministicRenderer;
import com.nongxinle.ai.composer.renderer.DeterministicAnswerRenderer;
import com.nongxinle.ai.composer.summary.BusinessOverviewDeterministicSummaryBuilder;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.platform.AiCardPayloadWireSupport;
import com.nongxinle.ai.platform.BusinessStatusCardWireService;
import com.nongxinle.ai.platform.PurchaseGoodsDetailCardWireService;
import com.nongxinle.ai.graph.business.BusinessStatusCardProjection;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.util.AiNumericPlainText;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Answer Composer：Plan-first 宣读层。各业务域只读 AnswerPlan / DiagnosisPlan / 结构化卡片 DTO；
 * 无 Plan 时输出固定 no-plan 话术，不再从 toolResults 拼事实，也不走 LLM + Tool fallback。
 */
@Component
@RequiredArgsConstructor
public class StubAnswerComposerNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(StubAnswerComposerNode.class);

    private final AiSseEventPublisher publisher;
    private final DeterministicAnswerRenderer deterministicAnswerRenderer;

    @Autowired(required = false)
    private MenuOperationExpertNarrativeComposer menuOperationExpertNarrativeComposer;

    @Autowired(required = false)
    private BusinessStatusCardWireService businessStatusCardWireService;

    @Autowired(required = false)
    private PurchaseGoodsDetailCardWireService purchaseGoodsDetailCardWireService;

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

        String coreToolPermissionOnlyBody = AiAnswerBoundary.tryComposeCoreToolPermissionOnlyAnswer(state);
        if (StringUtils.hasText(coreToolPermissionOnlyBody)) {
            coreToolPermissionOnlyBody = coreToolPermissionOnlyBody.trim();
        } else {
            coreToolPermissionOnlyBody = null;
        }

        if (purchaseGoodsDetailCardWireService != null) {
            purchaseGoodsDetailCardWireService.attachPurchaseGoodsDetailCardIfApplicable(state);
        }
        if (businessStatusCardWireService != null
                && !BusinessStatusCardWireSupport.isPurchasePeriodGoodsDetailMainline(state)) {
            businessStatusCardWireService.attachBusinessStatusCardsIfApplicable(state);
        }

        String answer;
        if (state.isNeedClarification() && state.getClarificationQuestion() != null
                && !state.getClarificationQuestion().isBlank()) {
            answer = state.getClarificationQuestion().trim();
        } else if (state.isCouponCostInsightBlocked()) {
            answer = "";
        } else if (state.getCostDiagnosisResult() != null) {
            answer = AiAnswerBoundary.stripDeveloperFacingLeakage(
                    deterministicAnswerRenderer.renderCostFallback(state.getCostDiagnosisResult()).trim());
        } else if (coreToolPermissionOnlyBody != null) {
            answer = coreToolPermissionOnlyBody;
        } else if (isBusinessDiagnosisComposerMainline(state)) {
            DiagnosisPlan dp = state.getDiagnosisPlan();
            if (dp != null && DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS.equals(dp.getPlanType())) {
                answer = deterministicAnswerRenderer.renderHarnessDiagnosisPlan(state, dp);
            } else {
                answer = composeBusinessDiagnosisNoPlanFallback(state);
            }
        } else if (state.getDiagnosisPlan() != null
                && DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS.equals(state.getDiagnosisPlan().getPlanType())
                && DiagnosisPlanBuilder.shouldPreferDiagnosisPlanInComposer(state)) {
            answer = deterministicAnswerRenderer.renderHarnessDiagnosisPlan(state, state.getDiagnosisPlan());
        } else if (isMenuOperationComposerMainline(state)) {
            MenuOperationAnswerPlan menuPlan = state.getMenuOperationAnswerPlan();
            String fromPlan = menuPlan != null
                    ? deterministicAnswerRenderer.renderMenuOperationAnswerPlan(menuPlan)
                    : null;
            if (menuOperationExpertNarrativeComposer != null
                    && menuPlan != null
                    && MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION.equals(menuPlan.getPlanType())
                    && menuPlan.getMenuOptimizationPlan() != null) {
                MenuExpertPresentationComposeResult presentationResult =
                        menuOperationExpertNarrativeComposer.tryComposePresentation(state, menuPlan);
                if (presentationResult != null
                        && presentationResult.isAccepted()
                        && StringUtils.hasText(presentationResult.getAnswerPreview())) {
                    answer = presentationResult.getAnswerPreview().trim();
                } else if (MenuOperationCardCompanionAnswerPreviewSupport.shouldUseShortPreview(menuPlan)) {
                    answer = MenuOperationCardCompanionAnswerPreviewSupport.composeCardCompanionHint(menuPlan);
                } else if (fromPlan != null && !fromPlan.isBlank()) {
                    answer = fromPlan.trim();
                } else {
                    answer = composeMenuOperationNoPlanFallback(state);
                }
            } else if (menuPlan != null
                    && MenuOperationCardCompanionAnswerPreviewSupport.shouldUseShortPreview(menuPlan)) {
                answer = MenuOperationCardCompanionAnswerPreviewSupport.composeCardCompanionHint(menuPlan);
            } else if (fromPlan != null && !fromPlan.isBlank()) {
                answer = fromPlan.trim();
            } else {
                answer = composeMenuOperationNoPlanFallback(state);
            }
        } else if (isDishCostAnalysisComposerMainline(state)) {
            if (isDishProfitPrescriptionComposerMainline(state)) {
                DishProfitPrescriptionAnswerPlan prescriptionPlan = state.getDishProfitPrescriptionAnswerPlan();
                String fromPlan = prescriptionPlan != null
                        ? deterministicAnswerRenderer.renderDishProfitPrescriptionAnswerPlan(prescriptionPlan)
                        : null;
                if (prescriptionPlan != null
                        && DishProfitPrescriptionCardCompanionAnswerPreviewSupport.shouldUseShortPreview(
                                prescriptionPlan)) {
                    answer = DishProfitPrescriptionCardCompanionAnswerPreviewSupport.composeCardCompanionHint(
                            prescriptionPlan);
                } else if (fromPlan != null && !fromPlan.isBlank()) {
                    answer = fromPlan.trim();
                } else {
                    answer = composeDishProfitPrescriptionNoPlanFallback(state);
                }
            } else if (isDishIngredientCoverComposerMainline(state)) {
                DishIngredientCoverAnswerPlan coverPlan = state.getDishIngredientCoverAnswerPlan();
                if (coverPlan != null
                        && DishIngredientCoverCardCompanionAnswerPreviewSupport.shouldUseShortPreview(coverPlan)) {
                    answer = DishIngredientCoverCardCompanionAnswerPreviewSupport.composeCardCompanionHint(coverPlan);
                } else {
                    answer = composeDishIngredientCoverNoPlanFallback(state);
                }
            } else {
                answer = composeDishCostAnalysisFromTool(state);
            }
        } else if (isDishSalesComposerMainline(state)) {
            String fromSalesTool = composeDishSalesSingleDishFromTool(state);
            if (StringUtils.hasText(fromSalesTool)) {
                answer = fromSalesTool.trim();
            } else if (dishSalesDeterministicEligible(state)) {
                DishSalesAnswerPlan dishSalesPlan = state.getDishSalesAnswerPlan();
                if (DishSalesRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(dishSalesPlan)) {
                    answer =
                            DishSalesRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(
                                    dishSalesPlan);
                } else {
                    String dishSalesFromPlan =
                            deterministicAnswerRenderer.renderDishSalesAnswerPlan(dishSalesPlan);
                    if (dishSalesFromPlan != null && !dishSalesFromPlan.isBlank()) {
                        answer = dishSalesFromPlan.trim();
                    } else {
                        answer = composeDishSalesNoPlanFallback(state);
                    }
                }
            } else {
                answer = composeDishSalesNoPlanFallback(state);
            }
        } else if (isDishProfitComposerMainline(state)) {
            answer =
                    composeBusinessStatusAnswerWithLegacyFallback(state, "销货核对", () -> {
                        DishProfitAnswerPlan dishProfitPlan = state.getDishProfitAnswerPlan();
                        if (DishProfitRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(
                                dishProfitPlan)) {
                            return DishProfitRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(
                                    dishProfitPlan);
                        }
                        String dishProfitFromPlan = null;
                        if (dishProfitPlan != null) {
                            String rendered =
                                    deterministicAnswerRenderer.renderDishProfitAnswerPlanOneLiner(dishProfitPlan);
                            if (rendered != null && !rendered.isBlank()) {
                                dishProfitFromPlan = rendered.trim();
                            }
                        }
                        if (dishProfitFromPlan != null && !dishProfitFromPlan.isBlank()) {
                            return dishProfitFromPlan;
                        }
                        return composeDishProfitNoPlanFallback(state);
                    });
        } else if (isRevenueOverviewComposerMainline(state)) {
            if (AiAnswerBoundary.isRevenuePermissionDenied(state.getPermissionDenials())) {
                answer = AiAnswerBoundary.revenuePermissionDeniedComposerBody(state);
            } else {
                answer =
                        composeBusinessStatusAnswerWithLegacyFallback(state, "营业额", () -> {
                            AiTimeWindowTextFormatter.UserPhrases twRevenue =
                                    AiTimeWindowTextFormatter.forAnswer(state);
                            DailyRevenueAnswerPlan rap = state.getRevenueAnswerPlan();
                            String revenueFromPlan =
                                    BusinessOverviewDeterministicSummaryBuilder.composeRevenueDeterministicFromAnswerPlan(
                                            rap, twRevenue);
                            if (revenueFromPlan != null && !revenueFromPlan.isBlank()) {
                                return revenueFromPlan;
                            }
                            return composeRevenueNoPlanFallback(state);
                        });
            }
        } else if (isBusinessOverviewMultiAgentMainline(state)) {
            answer =
                    composeBusinessStatusAnswerWithLegacyFallback(state, "经营情况", () -> {
                        if (businessOverviewMultiAgentFourDomainDeterministicEligible(state)) {
                            return composeBusinessOverviewMultiAgentFourDomainMarkdown(state).trim();
                        }
                        return composeBusinessOverviewMultiAgentNoPlanFallback(state);
                    });
        } else if (isWarehouseStockComposerMainline(state)) {
            GoodsSupportedDishCoverAnswerPlan goodsCoverPlan = state.getGoodsSupportedDishCoverAnswerPlan();
            if (goodsCoverPlan != null
                    && GoodsSupportedDishCoverCardCompanionAnswerPreviewSupport.shouldUseShortPreview(
                            goodsCoverPlan)) {
                answer =
                        GoodsSupportedDishCoverCardCompanionAnswerPreviewSupport.composeCardCompanionHint(
                                goodsCoverPlan);
            } else {
                AiTimeWindowTextFormatter.UserPhrases twWarehouse = AiTimeWindowTextFormatter.forAnswer(state);
                WarehouseAnswerPlan warehousePlan = state.getWarehouseAnswerPlan();
                if (warehousePlan != null
                        && WarehouseInventoryRiskCardCompanionAnswerPreviewSupport.shouldUseShortPreview(
                                warehousePlan)) {
                    answer =
                            WarehouseInventoryRiskCardCompanionAnswerPreviewSupport.composeCardCompanionHint(
                                    warehousePlan);
                } else if (warehousePlan != null
                        && WarehouseStockRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(
                                warehousePlan)) {
                    answer =
                            WarehouseStockRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(
                                    warehousePlan);
                } else {
                    String warehouseFromPlan =
                            composeWarehouseDeterministicFromAnswerPlan(warehousePlan, twWarehouse);
                    if (warehouseFromPlan != null && !warehouseFromPlan.isBlank()) {
                        answer = warehouseFromPlan;
                    } else {
                        answer = composeWarehouseStockNoPlanFallback(state);
                    }
                }
            }
        } else if (isStockReduceComposerMainline(state)) {
            answer =
                    composeBusinessStatusAnswerWithLegacyFallback(state, "销货核对", () -> {
                        AiTimeWindowTextFormatter.UserPhrases twStockReduceMainline =
                                AiTimeWindowTextFormatter.forAnswer(state);
                        StockReduceAnswerPlan stockReducePlan = state.getStockReduceAnswerPlan();
                        String stockReduceFromPlan =
                                composeStockReduceDeterministicFromAnswerPlan(
                                        stockReducePlan, twStockReduceMainline, state);
                        if (stockReduceFromPlan != null && !stockReduceFromPlan.isBlank()) {
                            return stockReduceFromPlan;
                        }
                        return composeStockReduceNoPlanFallback(state);
                    });
        } else if (isPurchaseOverviewComposerMainline(state)) {
            if (hasPurchaseGoodsDetailCardAttached(state)) {
                answer = composePurchaseGoodsDetailCardShortIntro(state);
            } else {
                answer =
                        composeBusinessStatusAnswerWithLegacyFallback(state, "采购情况", () -> {
                            AiTimeWindowTextFormatter.UserPhrases twPurchaseMainline =
                                    AiTimeWindowTextFormatter.forAnswer(state);
                            PurchaseAnswerPlan purchasePlan = state.getPurchaseAnswerPlan();
                            if (purchasePlan != null) {
                                String purchaseFromPlan =
                                        composePurchaseDeterministicFromAnswerPlan(
                                                purchasePlan, twPurchaseMainline, state.getResolvedQueryContext());
                                if (purchaseFromPlan != null && !purchaseFromPlan.isBlank()) {
                                    return purchaseFromPlan;
                                }
                                return composePurchaseOverviewNoPlanFallback(state);
                            }
                            return composePurchaseOverviewNoPlanFallback(state);
                        });
            }
        } else if (genericChatBlockedForDishReasonInDiagnosisContext(state)) {
            answer = "这类问题需要对照菜品毛利与成本数据作答。当前未走通数据查询链路，请改用完整提问（例如点明菜名并说明为什么毛利偏低或成本高），"
                    + "或在经营诊断结果页再追问该菜。";
        } else if (composerEmitRevenueDeniedPermissionOnly(state)) {
            answer = AiAnswerBoundary.revenuePermissionDeniedComposerBody(state);
        } else if (shouldBlockGenericComposerForBusinessSurface(state)) {
            answer = composeBlockedBusinessSurfaceNoPlanFallback(state);
        } else {
            answer = composeGenericComposerFallback(state);
        }
        if (shouldUseStoreCompareIntentHeader(state)) {
            intentP = DiagnosisDeterministicRenderer.storeCompareIntentConvergencePrefix(state.getDiagnosisPlan());
        } else if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStoreRiskReasonExplanationTurn(state)) {
            intentP =
                    AiAnswerBoundary.costIntentConvergencePrefix(
                            rewriteStorePriorityRankingCostIntentNote(state.getCostIntentConvergenceNote(), state));
        } else if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)) {
            intentP =
                    AiAnswerBoundary.costIntentConvergencePrefix(
                            rewriteStorePriorityRankingCostIntentNote(state.getCostIntentConvergenceNote(), state));
        }
        AiAnswerContextSummarySupport.captureComposerContext(
                state, boundaryNote, scopeP, intentP, permPrefix);
        if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)
                || DiagnosisDeterministicRenderer.isBusinessDiagnosisStoreRiskReasonExplanationTurn(state)) {
            answer = DiagnosisDeterministicRenderer.applyStorePrioritySingleStoreScopeDisplayPatches(
                    (answer == null ? "" : answer).trim(), state);
        }
        state.setFinalAnswerText(AiAnswerBoundary.stripDeveloperFacingLeakage(
                (answer == null ? "" : answer).trim()));

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "AnswerComposerNode",
                "displayText", "回答草稿已就绪",
                "hasStructuredCostDiagnosis", state.getCostDiagnosisResult() != null,
                "hasDishProfitOverview", state.getDishProfitOverviewResult() != null,
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
        if (DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)
                || DiagnosisDeterministicRenderer.isBusinessDiagnosisStoreRiskReasonExplanationTurn(state)) {
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

    /**
     * D-8 菜品销量专线 Composer 主线：须 intent/path 双对齐，避免仅 path 漂移时误入毛利 legacy。
     */
    private static boolean isDishSalesComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String effIntentRaw = rq.getEffectiveIntentCode();
        String effPathRaw = rq.getEffectivePathCode();
        if (!StringUtils.hasText(effIntentRaw) || !StringUtils.hasText(effPathRaw)) {
            return false;
        }
        return AiResolvedQueryIntent.DISH_SALES_QUERY.equals(effIntentRaw.trim())
                && AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effPathRaw.trim());
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
                || DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_RANKING_NO_DATA.equals(pt);
    }

    /**
     * 与 {@link BusinessDataPlannerNode} / D-2M 审计对齐：
     * {@code WAREHOUSE_STOCK_OVERVIEW} + {@code warehouse_stock_overview_path} 唯一 Composer 主线（待 {@code WarehouseAnswerPlan}）。
     */
    private static boolean isWarehouseStockComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isWarehouseStockOverviewPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(rq.getEffectivePathCode());
    }

    private String composeWarehouseStockNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "warehouse_stock_overview_no_plan");
        dbg.put("reason", "WarehouseAnswerPlan not mounted");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            dbg.put("warehouseStockOverviewPath", state.isWarehouseStockOverviewPath());
            dbg.put("groupWarehouseStockOverview", state.isGroupWarehouseStockOverview());
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerWarehouseStockNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] warehouse stock overview mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "库房库存分析计划暂未生成，请稍后重试或缩小范围。";
    }

    private static String composeWarehouseDeterministicFromAnswerPlan(
            WarehouseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (plan == null || plan.getPlanType() == null || plan.getPlanType().isBlank()) {
            return null;
        }
        String knownGapAnswer = composeWarehouseKnownGapFromPlan(plan, tw);
        if (knownGapAnswer != null && !knownGapAnswer.isBlank()) {
            return knownGapAnswer;
        }
        AiTimeWindowTextFormatter.UserPhrases p =
                tw != null ? tw : AiTimeWindowTextFormatter.fromIsoRange(null, null, java.time.LocalDate.now());
        String type = plan.getPlanType().trim();
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING.equals(type)) {
            return composeWarehouseStoreAmountRankingFromPlan(plan, p);
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH.equals(type)) {
            return composeWarehouseGoodsRankingFromPlan(plan, p, true);
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW.equals(type)) {
            return composeWarehouseGoodsRankingFromPlan(plan, p, false);
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK.equals(type)) {
            return composeWarehouseLowStockFromPlan(plan, p);
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW.equals(type)) {
            return composeWarehouseOverviewFromPlan(plan, p);
        }
        return null;
    }

    private static String warehousePlanLead(WarehouseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        String scope = plan.getScopeLabel() == null ? "" : plan.getScopeLabel().trim();
        if (scope.isEmpty()) {
            scope = "当前范围";
        }
        String snapshot = plan.getStockSnapshotLabel();
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = plan.getTimeLabel();
        }
        if (snapshot != null && !snapshot.isBlank()) {
            return scope + "，" + snapshot.trim();
        }
        return scope + "在" + tw.getTimeSubjectText();
    }

    private static String warehouseSnapshotBracketLine(WarehouseAnswerPlan plan) {
        String label = plan.getStockSnapshotLabel();
        if (label == null || label.isBlank()) {
            label = plan.getTimeLabel();
        }
        return InventoryPresentationTimeSupport.bracketStockSnapshotLine(label);
    }

    private static void appendWarehousePeriodBaselineIfPresent(StringBuilder sb, WarehouseAnswerPlan plan) {
        String baseline = InventoryPresentationTimeSupport.bracketPeriodBaselineLine(plan.getPeriodFlowLabel());
        if (baseline != null && !baseline.isBlank()) {
            sb.append(baseline).append('\n');
        }
    }

    private static String composeWarehouseKnownGapFromPlan(
            WarehouseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        Map<String, Object> summary = plan.getSummary();
        if (summary == null) {
            return null;
        }
        Object gapMessage = summary.get("gapMessage");
        if (gapMessage == null || gapMessage.toString().isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(warehouseSnapshotBracketLine(plan)).append('\n');
        appendWarehousePeriodBaselineIfPresent(sb, plan);
        sb.append(warehousePlanLead(plan, tw)).append("。").append(gapMessage.toString().trim());
        Object gap = plan.getDebug() != null ? plan.getDebug().get("warehouseKnownGap") : null;
        if (gap != null && !gap.toString().isBlank()) {
            sb.append("（").append(gap).append("）");
        }
        return sb.toString();
    }

    private static String composeWarehouseOverviewFromPlan(
            WarehouseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        Map<String, Object> summary = plan.getSummary();
        String narrative = summary != null && summary.get("narrative") != null
                ? summary.get("narrative").toString().trim()
                : "";
        StringBuilder sb = new StringBuilder();
        sb.append(warehouseSnapshotBracketLine(plan)).append('\n');
        appendWarehousePeriodBaselineIfPresent(sb, plan);
        if (!narrative.isEmpty()) {
            sb.append(narrative);
        } else {
            sb.append(warehousePlanLead(plan, tw)).append("，库存总览数据已汇总。");
        }
        return sb.toString();
    }

    private static String composeWarehouseStoreAmountRankingFromPlan(
            WarehouseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (WarehouseStockRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan)) {
            return WarehouseStockRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);
        }
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        Map<String, Object> top = focus.get(0);
        String storeName = nz(top.get("storeName"));
        if (storeName.isEmpty()) {
            storeName = nz(top.get("scopeName"));
        }
        String amt = plainNumericHint(top.get("totalStockAmount"));
        StringBuilder sb = new StringBuilder();
        sb.append(warehouseSnapshotBracketLine(plan)).append('\n');
        sb.append(warehousePlanLead(plan, tw)).append("，库存剩余金额较高的门店是 ")
                .append(storeName.isEmpty() ? "（未命名门店）" : storeName)
                .append("，约 ").append(amt).append(" 元。");
        return sb.toString();
    }

    private static String composeWarehouseGoodsRankingFromPlan(
            WarehouseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw, boolean high) {
        if (WarehouseStockRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan)) {
            return WarehouseStockRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);
        }
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        Map<String, Object> top = focus.get(0);
        String goods = nz(top.get("goodsName"));
        String amt = plainNumericHint(top.get("restAmountTotal"));
        StringBuilder sb = new StringBuilder();
        sb.append(warehouseSnapshotBracketLine(plan)).append('\n');
        sb.append(warehousePlanLead(plan, tw)).append("，按当前账面剩余库存金额排序，")
                .append(high ? "最高" : "最低")
                .append("的商品是 ")
                .append(goods.isEmpty() ? "（未命名商品）" : goods)
                .append("，约 ").append(amt).append(" 元。");
        return sb.toString();
    }

    private static String composeWarehouseLowStockFromPlan(
            WarehouseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (plan != null
                && WarehouseInventoryRiskCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan)) {
            return WarehouseInventoryRiskCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);
        }
        String knownGapAnswer = composeWarehouseKnownGapFromPlan(plan, tw);
        if (knownGapAnswer != null && !knownGapAnswer.isBlank()) {
            return knownGapAnswer;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(warehouseSnapshotBracketLine(plan)).append('\n');
        appendWarehousePeriodBaselineIfPresent(sb, plan);
        sb.append(warehousePlanLead(plan, tw)).append("，库存风险列表已返回，详见下方卡片。");
        return sb.toString();
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

    private boolean shouldUseBusinessStatusCardShortIntro(AiRunState state) {
        if (businessStatusCardWireService == null || state == null) {
            return false;
        }
        return businessStatusCardWireService.resolveProjection(state) != BusinessStatusCardProjection.NONE;
    }

    /**
     * cards[] 已写入时 Composer 只留短导语；无 cards 时回退旧确定性长文。
     */
    private String composeBusinessStatusAnswerWithLegacyFallback(
            AiRunState state, String topic, java.util.function.Supplier<String> legacyBodySupplier) {
        if (!shouldUseBusinessStatusCardShortIntro(state)) {
            return legacyBodySupplier.get();
        }
        if (hasBusinessStatusCardsAttached(state)) {
            return composeBusinessStatusCardShortIntro(state, topic);
        }
        String legacy = legacyBodySupplier.get();
        return legacy == null ? "" : legacy.trim();
    }

    private static boolean hasPurchaseGoodsDetailCardAttached(AiRunState state) {
        if (state == null || !state.isCardsPresent()) {
            return false;
        }
        return PurchaseGoodsDetailCardSupport.hasPurchaseGoodsDetailCard(state.getCards());
    }

    private static String composePurchaseGoodsDetailCardShortIntro(AiRunState state) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String label = tw != null && StringUtils.hasText(tw.getTimeSubjectText())
                ? tw.getTimeSubjectText().trim()
                : "本期";
        return "已整理 " + label + " 原料采购清单，详见下方卡片。";
    }

    private static boolean hasBusinessStatusCardsAttached(AiRunState state) {
        if (state == null || !state.isCardsPresent()) {
            return false;
        }
        return BusinessStatusCardWireSupport.hasBusinessStatusCards(state.getCards());
    }

    private static String composeBusinessStatusCardShortIntro(AiRunState state, String topic) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String label = tw != null && StringUtils.hasText(tw.getTimeSubjectText())
                ? tw.getTimeSubjectText().trim()
                : "本期";
        String suffix = topic == null || topic.isBlank() ? "数据" : topic.trim();
        return "已整理 " + label + " " + suffix + "，详见下方卡片。";
    }

    /**
     * 与 {@link BusinessDataPlannerNode} / {@link StubOutcomeReviewNode} 对齐：
     * {@code business_overview_summary/status/compare} + MULTI_AGENT 四域 overview 唯一 Composer 主线。
     */
    private static boolean isBusinessOverviewMultiAgentMainline(AiRunState state) {
        if (state == null || !state.isBusinessOverviewPath()) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        if (!AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(rq.getEffectiveIntentCode())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(rq.getEffectivePathCode())) {
            return false;
        }
        if (!resolvedContextOrchestrationMultiAgentOverview(rq)) {
            return false;
        }
        AiResolvedQueryIntent qi = rq.getQueryIntent();
        if (qi == null || !StringUtils.hasText(qi.getStructuredIntentDetail())) {
            return false;
        }
        return AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                qi.getStructuredIntentDetail());
    }

    private static boolean resolvedContextOrchestrationMultiAgentOverview(AiResolvedQueryContext rq) {
        if (rq == null) {
            return false;
        }
        String tm = rq.getOrchestrationTaskMode();
        if (tm != null && "MULTI_AGENT".equalsIgnoreCase(tm.trim())) {
            return true;
        }
        return Boolean.TRUE.equals(rq.getOrchestrationMultiAgentRequired());
    }

    /**
     * 与 {@link BusinessDataPlannerNode} / {@link StubOutcomeReviewNode} 对齐：
     * {@code BUSINESS_DIAGNOSIS} + {@code business_diagnosis_path} 唯一 Composer 主线（只读 {@link DiagnosisPlan}）。
     */
    private static boolean isBusinessDiagnosisComposerMainline(AiRunState state) {
        if (state == null || !state.isBusinessDiagnosisPath()) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.BUSINESS_DIAGNOSIS.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(rq.getEffectivePathCode());
    }

    private String composeBusinessDiagnosisNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "business_diagnosis_no_plan");
        dbg.put("reason", "DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS not mounted");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            dbg.put("orchestrationTaskMode", rq.getOrchestrationTaskMode());
            dbg.put("orchestrationMultiAgentRequired", rq.getOrchestrationMultiAgentRequired());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerBusinessDiagnosisNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] business diagnosis mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "经营诊断计划暂未生成，请稍后重试或缩小范围。";
    }

    /**
     * 与 {@link BusinessDataPlannerNode} / D-2M 审计对齐：
     * {@code REVENUE_OVERVIEW} + {@code revenue_overview_path} 唯一 Composer 主线（只读 {@link DailyRevenueAnswerPlan}）。
     */
    private static boolean isRevenueOverviewComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isRevenueOverviewPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.REVENUE_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(rq.getEffectivePathCode());
    }

    /**
     * 与 {@link BusinessDataPlannerNode} / D-2F 审计对齐：
     * 采购 Composer 唯一主线（{@code purchase_overview_path} / {@code purchaseCostInsightPath}），只读 {@link PurchaseAnswerPlan}。
     */
    private static boolean isPurchaseOverviewComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isPurchaseOverviewPath() || state.isPurchaseCostInsightPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.PURCHASE_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(rq.getEffectivePathCode());
    }

    private String composePurchaseOverviewNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "purchase_overview_no_plan");
        dbg.put("reason", "PurchaseAnswerPlan not mounted or compose yielded blank");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            dbg.put("purchaseOverviewPath", state.isPurchaseOverviewPath());
            dbg.put("purchaseCostInsightPath", state.isPurchaseCostInsightPath());
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerPurchaseOverviewNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] purchase overview mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "采购分析计划暂未生成，请稍后重试或缩小范围。";
    }

    private String composeRevenueNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "revenue_overview_no_plan");
        dbg.put("reason", "DailyRevenueAnswerPlan not mounted or compose yielded blank");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            dbg.put("revenueOverviewPath", state.isRevenueOverviewPath());
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerRevenueOverviewNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] revenue overview mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "营业额分析计划暂未生成，请稍后重试或缩小范围。";
    }

    /**
     * 与 {@link BusinessDataPlannerNode} / D-2H 审计对齐：
     * {@code STOCK_REDUCE_QUERY} + {@code stock_reduce_query_path} 唯一 Composer 主线（只读 {@link StockReduceAnswerPlan}）。
     */
    private static boolean isStockReduceComposerMainline(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isStockReduceQueryPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        return AiResolvedQueryIntent.STOCK_REDUCE_QUERY.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(rq.getEffectivePathCode());
    }

    private static boolean isMenuOperationComposerMainline(AiRunState state) {
        return state != null && state.isMenuOperationPath();
    }

    private static String composeMenuOperationNoPlanFallback(AiRunState state) {
        if (state != null && state.isNeedClarification() && StringUtils.hasText(state.getClarificationQuestion())) {
            return state.getClarificationQuestion().trim();
        }
        return "当前未能生成菜单经营顾问计划，请确认语义合同已锁定且菜品毛利数据可用。";
    }

    /**
     * 与 {@link BusinessDataPlannerNode} / D-2K 审计对齐：
     * 菜品毛利 Composer 唯一主线（{@code dish_profit_path}，不含 {@link #isDishSalesComposerMainline} 销量线），只读 {@link DishProfitAnswerPlan}。
     */
    private static boolean isDishProfitComposerMainline(AiRunState state) {
        if (state == null || !state.isDishProfitPath()) {
            return false;
        }
        if (isDishSalesComposerMainline(state)) {
            return false;
        }
        return true;
    }

    private static boolean isDishCostAnalysisComposerMainline(AiRunState state) {
        return state != null && state.isDishCostAnalysisPath();
    }

    private static boolean isDishProfitPrescriptionComposerMainline(AiRunState state) {
        if (state == null || !state.isDishCostAnalysisPath()) {
            return false;
        }
        return ToolRequestContractExecutionParamSupport.isDishProfitPrescriptionContract(
                state.getResolvedQueryContext());
    }

    private static boolean isDishIngredientCoverComposerMainline(AiRunState state) {
        if (state == null || !state.isDishCostAnalysisPath()) {
            return false;
        }
        return ToolRequestContractExecutionParamSupport.isDishIngredientCoverDaysContract(
                state.getResolvedQueryContext());
    }

    private static String composeDishIngredientCoverNoPlanFallback(AiRunState state) {
        if (state != null && state.isNeedClarification() && StringUtils.hasText(state.getClarificationQuestion())) {
            return state.getClarificationQuestion().trim();
        }
        return "当前未能生成单菜配料可支撑天数，请确认已点名具体菜品且成本分析数据可用。";
    }

    private static String composeDishProfitPrescriptionNoPlanFallback(AiRunState state) {
        if (state != null && state.isNeedClarification() && StringUtils.hasText(state.getClarificationQuestion())) {
            return state.getClarificationQuestion().trim();
        }
        return "当前未能生成单菜利润处方计划，请确认语义合同已锁定且菜品成本与毛利数据可用。";
    }

    /**
     * 单菜销售 Tool（{@link AiBusinessToolIds#DISH_SALES_ANALYSIS_CARD}）直出正文；
     * 与 cardPayload 并行，供 SSE answer_delta 使用。
     */
    private String composeDishSalesSingleDishFromTool(AiRunState state) {
        if (state == null
                || state.getResolvedQueryContext() == null
                || !ToolRequestContractExecutionParamSupport.isDishSalesSingleDishContract(
                        state.getResolvedQueryContext())) {
            return null;
        }
        Map<String, Object> data = extractDishSalesToolData(state);
        if (data == null || data.isEmpty()) {
            return null;
        }
        String status = blankToNull(data.get("status"));
        if ("NEED_CLARIFICATION".equalsIgnoreCase(status)) {
            String msg = blankToNull(data.get("message"));
            if (StringUtils.hasText(msg)) {
                return msg.trim();
            }
            return composeDishSalesEntityDisambiguationFallback(data);
        }
        if ("NO_DATA".equalsIgnoreCase(status)) {
            String msg = blankToNull(data.get("message"));
            return StringUtils.hasText(msg) ? msg.trim() : "所选时间范围内未找到该菜品的销售数据。";
        }
        if ("ERROR".equalsIgnoreCase(status)) {
            String msg = blankToNull(data.get("message"));
            return StringUtils.hasText(msg) ? msg.trim() : "菜品销售查询失败，请稍后重试。";
        }
        if (!"SUCCESS".equalsIgnoreCase(status)) {
            return null;
        }
        String dishName = blankToNull(data.get("dishName"));
        if (!StringUtils.hasText(dishName)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(dishName.trim()).append("：");
        appendMetricPart(sb, "销量", data.get("salesPortions"), "份");
        appendMetricPart(sb, "销售额", data.get("salesAmount"), "元");
        appendMetricPart(sb, "单价", data.get("salesUnitPrice"), "元");
        String text = sb.toString().trim();
        if (text.endsWith("：")) {
            return dishName.trim() + " 的销售数据已返回，详见下方卡片。";
        }
        return text.endsWith("，") ? text.substring(0, text.length() - 1) + "。" : text + "。";
    }

    private static String composeDishSalesEntityDisambiguationFallback(Map<String, Object> data) {
        StringBuilder sb =
                new StringBuilder("当前查询范围内匹配到多道同名菜品，需要您指定具体是哪一个。");
        sb.append("这是菜品实体消歧，查询口径正常，不是因为 wire、卡片或查询失败。");
        Object rawCandidates = data.get("candidates");
        if (rawCandidates instanceof List<?> list && !list.isEmpty()) {
            sb.append(" 候选：");
            int limit = Math.min(list.size(), 5);
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append("；");
                }
                Object item = list.get(i);
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                String name = blankToNull(m.get("dishName"));
                if (!StringUtils.hasText(name)) {
                    name = "（未命名）";
                }
                sb.append(i + 1).append('.').append(name);
                Object foodId = m.get("foodId");
                if (foodId != null && StringUtils.hasText(foodId.toString())) {
                    sb.append("（foodId=").append(foodId.toString().trim()).append(')');
                }
            }
        }
        return sb.toString();
    }

    private String composeDishCostAnalysisFromTool(AiRunState state) {
        Map<String, Object> data = extractDishCostToolData(state);
        if (data == null || data.isEmpty()) {
            return "暂未获取到菜品成本分析结果，请稍后重试。";
        }
        String status = blankToNull(data.get("status"));
        if ("NEED_CLARIFICATION".equalsIgnoreCase(status)) {
            String msg = blankToNull(data.get("message"));
            return StringUtils.hasText(msg) ? msg.trim() : "请说明要分析的具体菜品名称。";
        }
        if ("NO_DATA".equalsIgnoreCase(status)) {
            String msg = blankToNull(data.get("message"));
            return StringUtils.hasText(msg) ? msg.trim() : "所选时间范围内未找到该菜品的销售与成本数据。";
        }
        if ("ERROR".equalsIgnoreCase(status)) {
            String msg = blankToNull(data.get("message"));
            return StringUtils.hasText(msg) ? msg.trim() : "菜品成本分析查询失败，请稍后重试。";
        }
        String dishName = blankToNull(data.get("dishName"));
        if (!StringUtils.hasText(dishName)) {
            dishName = "该菜品";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(dishName.trim()).append("：");
        appendMetricPart(sb, "销量", data.get("salesPortions"), "份");
        appendMetricPart(sb, "销售额", data.get("salesAmount"), "元");
        appendMetricPart(sb, "单价", data.get("salesUnitPrice"), "元");
        appendMetricPart(sb, "理论成本", data.get("theoryCostPerPortion"), "元/份");
        appendMetricPart(sb, "实际成本", data.get("actualCostPerPortion"), "元/份");
        appendMetricPart(sb, "成本偏差", data.get("diffCostPerPortion"), "元/份");
        String text = sb.toString().trim();
        if (text.endsWith("：")) {
            return dishName.trim() + " 的成本与销售数据已返回，详见下方卡片。";
        }
        return text.endsWith("，") ? text.substring(0, text.length() - 1) + "。" : text + "。";
    }

    private static void appendMetricPart(StringBuilder sb, String label, Object value, String unit) {
        if (value == null) {
            return;
        }
        String s = value.toString().trim();
        if (!StringUtils.hasText(s)) {
            return;
        }
        if (sb.length() > 0 && !sb.toString().endsWith("：")) {
            sb.append("，");
        }
        sb.append(label).append(' ').append(s);
        if (StringUtils.hasText(unit)) {
            sb.append(unit);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractDishSalesToolData(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object dataObj = ((Map<String, Object>) envelope).get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractDishCostToolData(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return null;
        }
        Object raw = state.getToolResults().get(AiBusinessToolIds.DISH_COST_ANALYSIS);
        if (!(raw instanceof Map<?, ?> envelope)) {
            return null;
        }
        Object dataObj = ((Map<String, Object>) envelope).get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return null;
    }

    private static String blankToNull(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String composeDishProfitNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "dish_profit_no_plan");
        dbg.put("reason", "DishProfitAnswerPlan not mounted or compose yielded blank");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            dbg.put("dishProfitPath", state.isDishProfitPath());
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerDishProfitNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] dish profit mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "菜品毛利分析计划暂未生成，请稍后重试或缩小范围。";
    }

    private String composeDishSalesNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "dish_sales_no_plan");
        dbg.put("reason", "DishSalesAnswerPlan not mounted or deterministic render yielded blank");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            dbg.put("hasDishSalesAnswerPlan", state.getDishSalesAnswerPlan() != null);
            dbg.put("dishProfitPath", state.isDishProfitPath());
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerDishSalesNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] dish sales mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "菜品销量分析计划暂未生成，请稍后重试或缩小范围。";
    }

    private String composeStockReduceNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "stock_reduce_no_plan");
        dbg.put("reason", "StockReduceAnswerPlan not mounted or compose yielded blank");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            dbg.put("stockReduceQueryPath", state.isStockReduceQueryPath());
            dbg.put("groupStockReduceQuery", state.isGroupStockReduceQuery());
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerStockReduceNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] stock reduce mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "出库分析计划暂未生成，请稍后重试或缩小范围。";
    }

    private String composeBusinessOverviewMultiAgentNoPlanFallback(AiRunState state) {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "business_overview_multi_agent_no_plan");
        dbg.put("reason", "PLAN_TYPE_BUSINESS_OVERVIEW_MULTI_AGENT_V1 not mounted");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
            dbg.put("orchestrationTaskMode", rq.getOrchestrationTaskMode());
            dbg.put("orchestrationMultiAgentRequired", rq.getOrchestrationMultiAgentRequired());
            if (rq.getQueryIntent() != null) {
                dbg.put(
                        "structuredIntentDetailWire",
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                rq.getQueryIntent().getStructuredIntentDetail()));
            }
        }
        if (state != null) {
            List<String> plan = state.getDataPlanTools();
            dbg.put("dataPlanToolCount", plan != null ? plan.size() : 0);
            dbg.put("needClarification", state.isNeedClarification());
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            if (existingMaster != null && !existingMaster.isEmpty()) {
                dbg.put("masterBusinessAgentDebugSnapshot", new LinkedHashMap<>(existingMaster));
            }
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerBusinessOverviewMultiNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info(
                        "[StubAnswerComposer] business overview multi mainline no plan runId={} debug={}",
                        state.getRunId(),
                        dbg);
            }
        }
        return "经营概览四域计划暂未生成，请稍后重试或缩小范围。";
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
        String purchaseBlock =
                nz(composePurchaseDeterministicFromAnswerPlan(
                        state.getPurchaseAnswerPlan(), tw, state.getResolvedQueryContext()));
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
     * @return 可展示的确定性正文；plan 缺失或 compose 无法宣读时返回 {@code null}，由 no-plan fallback 兜底。
     */
    private static String composePurchaseDeterministicFromAnswerPlan(PurchaseAnswerPlan plan,
            AiTimeWindowTextFormatter.UserPhrases tw, AiResolvedQueryContext rq) {
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
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(type)) {
            return composePurchaseSupplierGoodsDetailFromPlan(plan, p, rq);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SELF_GOODS_DETAIL.equals(type)) {
            return composePurchaseSelfGoodsDetailFromPlan(plan, p);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING.equals(type)) {
            return composePurchaseStoreAmountRankingFromPlan(plan, p);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(type)) {
            return composePurchaseGoodsSourceBreakdownFromPlan(plan, p, rq);
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL.equals(type)) {
            return composePurchasePeriodGoodsDetailFromPlan(plan, p);
        }
        return null;
    }

    private static String composePurchasePeriodGoodsDetailFromPlan(
            PurchaseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        if (plan == null) {
            return null;
        }
        String time = plan.getTimeLabel() != null && !plan.getTimeLabel().isBlank()
                ? plan.getTimeLabel().trim()
                : (tw != null ? tw.getDisplayTimeRange() : "本期");
        String scope = plan.getScopeLabel() != null && !plan.getScopeLabel().isBlank()
                ? plan.getScopeLabel().trim()
                : "当前范围";
        Map<String, Object> summary = plan.getSummary() == null ? Map.of() : plan.getSummary();
        String total = plainNumericHint(summary.get("totalAmount"));
        if (total == null || total.isBlank()) {
            total = plainNumericHint(summary.get("totalPurchaseAmount"));
        }
        if (total == null || total.isBlank()) {
            total = "—";
        }
        Object cnt = summary.get("purchaseOrderCount");
        StringBuilder sb = new StringBuilder();
        sb.append(time).append(" ").append(scope).append(" 原料采购：");
        sb.append("总金额 ").append(total);
        if (cnt != null) {
            sb.append("，采购笔数 ").append(cnt);
        }
        sb.append("。\n");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (plan.getFocusRows() != null) {
            rows.addAll(plan.getFocusRows());
        }
        if (plan.getSecondaryRows() != null) {
            rows.addAll(plan.getSecondaryRows());
        }
        if (rows.isEmpty()) {
            sb.append("该时间范围内暂无采购明细。");
            return sb.toString();
        }
        int n = Math.min(rows.size(), 20);
        for (int i = 0; i < n; i++) {
            Map<String, Object> row = rows.get(i);
            if (row == null) {
                continue;
            }
            Object name = row.get("goodsName");
            Object qty = row.get("quantity");
            Object unit = row.get("unit");
            Object price = row.get("unitPrice");
            Object amt = row.get("amount");
            if (amt == null) {
                amt = row.get("purchaseSubtotal");
            }
            sb.append(i + 1).append(". ");
            sb.append(name != null ? name : "—");
            if (qty != null) {
                sb.append(" ").append(qty);
                if (unit != null && !unit.toString().isBlank()) {
                    sb.append(unit);
                }
            }
            if (price != null) {
                sb.append(" 单价").append(price);
            }
            if (amt != null) {
                sb.append(" 金额").append(amt);
            }
            sb.append("\n");
        }
        if (rows.size() > n) {
            sb.append("…共 ").append(rows.size()).append(" 项，详见卡片。");
        }
        return sb.toString().trim();
    }

    /**
     * 商品来源拆桶（Phase2-A）：只读 AnswerPlan.focusRows，不重算、不重排。
     */
    private static String composePurchaseGoodsSourceBreakdownFromPlan(
            PurchaseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw, AiResolvedQueryContext rq) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        Map<String, Object> row = focus.get(0);
        if (row == null || row.isEmpty()) {
            return null;
        }
        String goodsName = goodsNameFromPurchasePlanRow(row);
        if (goodsName.isBlank()) {
            goodsName = debugString(plan.getDebug(), "focusEntityName");
        }
        if (goodsName.isBlank()) {
            goodsName = debugString(plan.getDebug(), "inheritedAnchorName");
        }
        if (goodsName.isBlank() && rq != null && StringUtils.hasText(rq.getRewriteInheritedAnchorName())) {
            goodsName = rq.getRewriteInheritedAnchorName().trim();
        }
        if (goodsName.isBlank()) {
            goodsName = "该商品";
        }
        String total = plainNumericHint(row.get("totalPurchaseAmount"));
        String selfAmt = plainNumericHint(row.get("selfPurchaseAmount"));
        String supAmt = plainNumericHint(row.get("supplierPurchaseAmount"));
        int selfLines = intHint(row.get("selfPurchaseLineCount"));
        int supLines = intHint(row.get("supplierPurchaseLineCount"));

        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append(tw.getDisplayTimeRange()).append("，").append(goodsName).append("采购总额").append(total).append("元");
        sb.append("：自采").append(selfAmt).append("元");
        if (selfLines > 0) {
            sb.append("（").append(selfLines).append("笔）");
        }
        sb.append("，供货商订货").append(supAmt).append("元");
        if (supLines > 0) {
            sb.append("（").append(supLines).append("笔）");
        } else {
            Object supQty = row.get("supplierPurchaseQuantity");
            if (supQty != null && !supQty.toString().isBlank()
                    && parseDoubleLoose(supQty) > 1e-9) {
                sb.append("，数量").append(plainNumericHint(supQty));
            }
        }
        sb.append("。");
        return sb.toString();
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
        Object v = row.get("orderCount");
        if (v == null) {
            v = row.get("recordCount");
        }
        if (v == null) {
            v = row.get("purchaseTimes");
        }
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

    /**
     * 供货商渠道商品明细：宣读 AnswerPlan 已有行，不重算；供货商名取自 Resolver 下钻字段或 Plan 内锚点。
     */
    private static String composePurchaseSupplierGoodsDetailFromPlan(
            PurchaseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw, AiResolvedQueryContext rq) {
        Map<String, Object> dbg = plan.getDebug();
        if (Boolean.TRUE.equals(
                dbg != null
                        ? dbg.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_SUPPLIER_EXECUTION_ACTIVE)
                        : null)) {
            return composePurchaseGoodsAnchoredSupplierBreakdownFromPlan(plan, tw, rq);
        }
        String supplier = resolveSupplierDisplayNameForGoodsDetail(plan, rq);
        String subject =
                tw.getTimeSubjectText() != null && !tw.getTimeSubjectText().isBlank()
                        ? tw.getTimeSubjectText().trim()
                        : "该统计区间";
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();

        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("按上文「").append(subject).append("」和「").append(supplier).append("」供货商口径查询。\n\n");
        if (focus == null || focus.isEmpty()) {
            sb.append(subject)
                    .append("向")
                    .append(supplier)
                    .append("采购的商品：当前明细行为空（可能与权限范围或入库数据有关）。");
            return sb.toString().trim();
        }
        sb.append(subject).append("向").append(supplier).append("采购的商品如下：\n");

        appendPurchaseGoodsDetailLinesFromPlanRows(sb, focus, sec);
        return sb.toString().trim();
    }

    /**
     * 自采渠道商品明细：只读 AnswerPlan focusRows / secondaryRows，不重算、不重排。
     */
    private static String composePurchaseSelfGoodsDetailFromPlan(
            PurchaseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw) {
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        String subject =
                tw.getTimeSubjectText() != null && !tw.getTimeSubjectText().isBlank()
                        ? tw.getTimeSubjectText().trim()
                        : "该统计区间";
        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");
        sb.append("按上文「").append(subject).append("」自采口径查询。\n\n");
        sb.append(subject).append("自采商品明细如下：\n");
        appendPurchaseGoodsDetailLinesFromPlanRows(sb, focus, sec);
        return sb.toString().trim();
    }

    /** 宣读 AnswerPlan 商品明细行（goodsName / purchaseSubtotal / quantity / unitPrice），供供货商/自采明细复用。 */
    private static void appendPurchaseGoodsDetailLinesFromPlanRows(
            StringBuilder sb,
            List<Map<String, Object>> focus,
            List<Map<String, Object>> secondary) {
        List<Map<String, Object>> ordered = new ArrayList<>();
        if (focus != null) {
            ordered.addAll(focus);
        }
        if (secondary != null) {
            ordered.addAll(secondary);
        }

        boolean anyPrice = false;
        for (Map<String, Object> r : ordered) {
            if (r != null && StringUtils.hasText(unitPriceHintFromPurchaseGoodsRow(r))) {
                anyPrice = true;
                break;
            }
        }

        int idx = 1;
        for (Map<String, Object> r : ordered) {
            if (r == null) {
                continue;
            }
            String name = nz(goodsNameFromPurchasePlanRow(r));
            if (name.isBlank()) {
                name = "（未命名商品）";
            }
            String amt = plainNumericHint(purchaseGoodsAmountFromPlanRow(r));
            Object qObj = purchaseGoodsBuyQuantityFromPlanRow(r);
            boolean hasQty = qObj != null && !"暂无".equals(plainNumericHint(qObj));
            String price = unitPriceHintFromPurchaseGoodsRow(r);

            sb.append(idx++).append(". ").append(name);
            if (hasQty) {
                sb.append("，数量").append(plainNumericHint(qObj));
            }
            sb.append("，金额").append(amt).append("元");
            if (anyPrice && StringUtils.hasText(price)) {
                sb.append("，单价").append(price).append("元");
            }
            sb.append("\n");
        }

        if (!anyPrice) {
            sb.append("\n当前明细中暂缺单价字段，仅展示采购金额/数量。");
        }
    }

    /** D-13.4 Phase2：上一 GOODS 锚下，按供应商拆行的供货商采购明细（非供货商商品 Top）。 */
    private static String composePurchaseGoodsAnchoredSupplierBreakdownFromPlan(
            PurchaseAnswerPlan plan, AiTimeWindowTextFormatter.UserPhrases tw, AiResolvedQueryContext rq) {
        Map<String, Object> dbg = plan.getDebug();
        String goodsName =
                debugString(dbg, AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME);
        if (goodsName.isBlank()) {
            goodsName = debugString(dbg, "requestedGoodsName");
        }
        String subject =
                tw.getTimeSubjectText() != null && !tw.getTimeSubjectText().isBlank()
                        ? tw.getTimeSubjectText().trim()
                        : "该统计区间";
        List<Map<String, Object>> focus = plan.getFocusRows();
        List<Map<String, Object>> sec =
                plan.getSecondaryRows() != null ? plan.getSecondaryRows() : Collections.emptyList();

        StringBuilder sb = new StringBuilder();
        sb.append(tw.getBracketTimeRangeLine()).append("\n");

        if (focus == null || focus.isEmpty()) {
            String reason = dbg == null ? "" : debugString(dbg, "purchaseSupplierGoodsDetailNoDataReason");
            if ("NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS".equals(reason)
                    || "NO_SUPPLIER_PURCHASE_FOR_GOODS".equals(reason)) {
                String bracketName = goodsName.isBlank() ? "该商品" : goodsName.trim();
                sb.append("上文锚定的商品【").append(bracketName).append("】在供货商采购口径下暂未查到采购记录。");
                if (Boolean.TRUE.equals(dbg != null ? dbg.get("purchaseSupplierGoodsDetailAlternativeHasData") : null)) {
                    sb.append("但该商品在自采口径下有采购记录，如需查看自采单价，可以继续问‘看自采单价’。");
                }
            } else {
                String gLabel = goodsName.isBlank() ? "该商品" : goodsName;
                sb.append("上文锚定的商品是 ").append(gLabel).append("。");
                sb.append(tw.getDisplayTimeRange()).append("在供货商采购口径下没有查询到该商品的挂靠供应商采购明细。");
                if (Boolean.TRUE.equals(dbg != null ? dbg.get("purchaseSupplierGoodsDetailAlternativeHasData") : null)) {
                    sb.append(
                            "\n\n但同一时间范围内存在自采记录，说明该商品本期可能主要来自自采。你可以继续问：这个商品自采单价是多少？");
                }
            }
            return sb.toString().trim();
        }

        String gHead = goodsName.isBlank() ? nz(goodsNameFromPurchasePlanRow(focus.get(0))) : goodsName;
        if (gHead.isBlank()) {
            gHead = "该商品";
        }
        sb.append(subject).append("，").append(gHead).append("的供货商采购明细如下：\n\n");

        List<Map<String, Object>> ordered = new ArrayList<>(focus.size() + sec.size());
        ordered.addAll(focus);
        ordered.addAll(sec);

        int idx = 1;
        for (Map<String, Object> r : ordered) {
            if (r == null) {
                continue;
            }
            String sup = supplierNameFromPurchasePlanRow(r);
            if (sup.isBlank()) {
                sup = "（未命名供应商）";
            }
            String amt = plainNumericHint(supplierPurchaseAmountFromPlanRow(r));
            Object qObj = purchaseGoodsBuyQuantityFromPlanRow(r);
            boolean hasQty = qObj != null && !"暂无".equals(plainNumericHint(qObj));
            String price = unitPriceHintFromPurchaseGoodsRow(r);
            String oc = plainNumericHint(purchaseGoodsCountFromPlanRow(r));

            sb.append(idx++).append(". ").append("供应商：").append(sup);
            sb.append("；采购金额").append(amt).append("元");
            if (hasQty) {
                sb.append("；采购数量").append(plainNumericHint(qObj));
            }
            if (StringUtils.hasText(price)) {
                sb.append("；平均单价").append(price).append("元");
            }
            if (StringUtils.hasText(oc) && !"0".equals(oc)) {
                sb.append("；").append(oc).append("笔");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String debugString(Map<String, Object> dbg, String key) {
        if (dbg == null || key == null) {
            return "";
        }
        Object v = dbg.get(key);
        return v == null ? "" : v.toString().trim();
    }

    private static String resolveSupplierDisplayNameForGoodsDetail(PurchaseAnswerPlan plan, AiResolvedQueryContext rq) {
        if (plan != null && plan.getDebug() != null) {
            String fromDebug = debugString(plan.getDebug(), "focusEntityName");
            if (StringUtils.hasText(fromDebug)) {
                return fromDebug.trim();
            }
        }
        if (plan != null && plan.getResultAnchors() != null) {
            for (AiResultAnchor a : plan.getResultAnchors()) {
                if (a == null || !StringUtils.hasText(a.getEntityName())) {
                    continue;
                }
                if (AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(nz(a.getEntityType()))) {
                    return a.getEntityName().trim();
                }
            }
        }
        return "上文锚定的供货商";
    }

    /** 采购商品行上的数量/重量类字段（Tool→Plan 映射未带时可能为空）。 */
    private static Object purchaseGoodsBuyQuantityFromPlanRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        String[] keys = {"buyQuantity", "purchaseQuantity", "goodsQuantity", "quantity", "purchaseWeight"};
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null && StringUtils.hasText(v.toString())) {
                return v;
            }
        }
        return purchaseGoodsCountFromPlanRow(row);
    }

    private static String unitPriceHintFromPurchaseGoodsRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        String[] keys = {
            "unitPrice",
            "avgUnitPrice",
            "buyPrice",
            "avgBuyPrice",
            "averageBuyPrice",
            "weightedAvgBuyPrice",
            "purchaseAvgPrice",
            "goodsAveragePrice",
            "gbDgGoodsAveragePrice",
            "averagePrice"
        };
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                    return s;
                }
            }
        }
        return null;
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
     * @return 可展示的确定性正文；{@code null} 表示 plan 缺失或 compose 无法宣读，由 {@link #composeStockReduceNoPlanFallback} 兜底。
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
        if (isRevenueOverviewComposerMainline(state)) {
            return false;
        }
        if (isWarehouseStockComposerMainline(state)
                || state.isStockReduceQueryPath()
                || state.isPurchaseCostInsightPath()
                || state.isBusinessDiagnosisPath()
                || state.isDishProfitPath()
                || isDishSalesComposerMainline(state)) {
            return false;
        }
        if (state.getCostDiagnosisResult() != null) {
            return false;
        }
        if (!DiagnosisDeterministicRenderer.isBusinessDiagnosisStorePriorityTurn(state)
                && !DiagnosisDeterministicRenderer.isBusinessDiagnosisStoreRiskReasonExplanationTurn(state)
                && state.getDiagnosisPlan() != null
                && DiagnosisPlan.TYPE_OVERALL_BUSINESS_DIAGNOSIS.equals(state.getDiagnosisPlan().getPlanType())
                && (state.isBusinessDiagnosisPath()
                        || DiagnosisPlanBuilder.shouldPreferDiagnosisPlanInComposer(state))) {
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

    /** 经营概览/诊断 path 上禁止 generic LLM 拼事实抢主链。 */
    private static boolean shouldBlockGenericComposerForBusinessSurface(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isBusinessOverviewPath() || state.isBusinessDiagnosisPath()) {
            return true;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        String path = rq.getEffectivePathCode();
        return AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(path)
                || AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(path);
    }

    private String composeBlockedBusinessSurfaceNoPlanFallback(AiRunState state) {
        if (state != null && state.isBusinessDiagnosisPath()) {
            return composeBusinessDiagnosisNoPlanFallback(state);
        }
        if (state != null && state.isBusinessOverviewPath()) {
            return composeBusinessOverviewMultiAgentNoPlanFallback(state);
        }
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "business_surface_no_plan");
        if (state != null) {
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerBusinessSurfaceNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
        }
        return "当前经营问句未走通编排主链，暂无结构化答复。请改用更完整的经营概览或经营诊断问法后重试。";
    }

    private String composeGenericComposerFallback(AiRunState state) {
        if (shouldBlockGenericComposerForBusinessSurface(state)) {
            return composeBlockedBusinessSurfaceNoPlanFallback(state);
        }
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("composerFallback", "generic_no_business_plan");
        dbg.put("reason", "no AnswerPlan mainline matched");
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq != null) {
            dbg.put("effectiveIntentCode", rq.getEffectiveIntentCode());
            dbg.put("effectivePathCode", rq.getEffectivePathCode());
        }
        if (state != null) {
            Map<String, Object> existingMaster = state.getMasterBusinessAgentDebug();
            LinkedHashMap<String, Object> merged = existingMaster != null
                    ? new LinkedHashMap<>(existingMaster)
                    : new LinkedHashMap<>();
            merged.put("composerGenericNoPlan", dbg);
            state.setMasterBusinessAgentDebug(merged);
            if (log.isInfoEnabled()) {
                log.info("[StubAnswerComposer] generic no-plan runId={} debug={}", state.getRunId(), dbg);
            }
        }
        return deterministicAnswerRenderer.genericNonBusinessPlanFallback();
    }
}
