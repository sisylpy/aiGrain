package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.semantic.matrix.BusinessDiagnosisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.BusinessOverviewSemanticCapabilityMatrix;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BusinessDataPlannerNode implements AgentNode {

    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "DataPlanner";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "DataPlannerNode",
                "displayText", "正在规划经营数据来源…"
        ));

        String q = state.getNormalizedUserInput() != null ? state.getNormalizedUserInput().trim() : "";

        var rCtx = state.getResolvedQueryContext();
        var rqi = rCtx != null ? rCtx.getQueryIntent() : null;
        boolean semanticClarifies = rCtx != null && rCtx.isNeedSemanticClarification();
        String effPath =
                !semanticClarifies && rCtx != null && StringUtils.hasText(rCtx.getEffectivePathCode())
                        ? rCtx.getEffectivePathCode().trim()
                        : (!semanticClarifies && rqi != null && StringUtils.hasText(rqi.getPathCode())
                                ? rqi.getPathCode().trim()
                                : null);

        // 主路由以 LLM 解析为准：若已有有效 path，不因历史非 BUSINESS_CHAT workspace 截断经营 Tool 规划。
        boolean inBusinessChat = state.getWorkspaceMode() == AiWorkspaceMode.BUSINESS_CHAT
                || (!semanticClarifies && StringUtils.hasText(effPath));

        boolean resolvedPurchaseOverview =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(effPath);
        boolean resolvedDishProfit =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(effPath);
        /** D-8 Phase 1：菜品销量/销售额专线；底层工具仍复用 {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS}。 */
        boolean resolvedDishSalesQuery =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effPath);
        boolean resolvedBusinessOverview =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(effPath);
        boolean resolvedWarehouse =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(effPath);
        boolean resolvedStockReduce =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(effPath);
        boolean resolvedRevenueOverview =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(effPath);
        boolean resolvedBusinessDiagnosis =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(effPath);

        resetCostIntentFlags(state);

        if (state.isNeedClarification()) {
            state.setCouponCostInsightBlocked(false);
            state.setCostInsightPath(false);
            state.setPurchaseCostInsightPath(false);
            state.setWarehouseStockOverviewPath(false);
            state.setGroupWarehouseStockOverview(false);
            state.setPurchaseOverviewPath(false);
            state.setGroupPurchaseOverview(false);
            state.setBusinessOverviewPath(false);
            state.setDishProfitPath(false);
            state.setStockReduceQueryPath(false);
            state.setGroupStockReduceQuery(false);
            state.setBusinessDiagnosisPath(false);
            state.setRevenueOverviewPath(false);
            state.setRevenueAnswerPlan(null);
            state.setDataPlanTools(new ArrayList<>());
            Map<String, Object> payloadCl = new LinkedHashMap<>();
            payloadCl.put("needClarification", true);
            payloadCl.put("tools", List.of());
            publisher.publish(rid, "agent_finished", Map.of(
                    "agent", "DataPlannerNode",
                    "displayText", "需要补充上一句业务问题后才能查数",
                    "dataPlan", payloadCl
            ));
            return state;
        }

        boolean resolvedCostDiagnosis =
                inBusinessChat && !semanticClarifies && AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(effPath);

        boolean dishProfitIntent = resolvedDishProfit || resolvedDishSalesQuery;
        boolean businessDiagnosisIntent = resolvedBusinessDiagnosis;
        boolean stockReduceStandaloneIntent =
                !dishProfitIntent && !businessDiagnosisIntent && inBusinessChat && resolvedStockReduce;
        boolean revenueStandaloneIntent =
                !dishProfitIntent
                        && !businessDiagnosisIntent
                        && !stockReduceStandaloneIntent
                        && inBusinessChat
                        && resolvedRevenueOverview;
        boolean purchaseOverviewOnlyIntent =
                !dishProfitIntent
                        && !businessDiagnosisIntent
                        && !stockReduceStandaloneIntent
                        && !revenueStandaloneIntent
                        && inBusinessChat
                        && resolvedPurchaseOverview;
        boolean rawCostIntent =
                !dishProfitIntent
                        && !businessDiagnosisIntent
                        && !stockReduceStandaloneIntent
                        && !revenueStandaloneIntent
                        && !purchaseOverviewOnlyIntent
                        && inBusinessChat
                        && resolvedCostDiagnosis;
        boolean stockOverviewIntent =
                !dishProfitIntent
                        && !businessDiagnosisIntent
                        && !stockReduceStandaloneIntent
                        && !revenueStandaloneIntent
                        && !purchaseOverviewOnlyIntent
                        && !rawCostIntent
                        && inBusinessChat
                        && resolvedWarehouse;
        boolean overviewIntent =
                !dishProfitIntent
                        && !businessDiagnosisIntent
                        && !stockReduceStandaloneIntent
                        && !rawCostIntent
                        && !stockOverviewIntent
                        && !purchaseOverviewOnlyIntent
                        && !revenueStandaloneIntent
                        && inBusinessChat
                        && resolvedBusinessOverview;

        List<String> plan;

        if (!inBusinessChat) {
            state.setCostInsightPath(false);
            state.setPurchaseCostInsightPath(false);
            state.setWarehouseStockOverviewPath(false);
            state.setGroupWarehouseStockOverview(false);
            state.setPurchaseOverviewPath(false);
            state.setGroupPurchaseOverview(false);
            state.setBusinessOverviewPath(false);
            state.setDishProfitPath(false);
            state.setStockReduceQueryPath(false);
            state.setGroupStockReduceQuery(false);
            state.setBusinessDiagnosisPath(false);
            state.setRevenueOverviewPath(false);
            state.setRevenueAnswerPlan(null);
            state.setDataPlanTools(new ArrayList<>());
            plan = List.of();
        } else if (businessDiagnosisIntent) {
            applyBusinessDiagnosisBranch(state);
            if (state.isBusinessDiagnosisPath()) {
                syncResolvedQueryContextToBusinessDiagnosis(state);
            }
            plan = state.getDataPlanTools();
        } else if (dishProfitIntent) {
            state.setCostInsightPath(false);
            state.setPurchaseCostInsightPath(false);
            state.setWarehouseStockOverviewPath(false);
            state.setGroupWarehouseStockOverview(false);
            state.setPurchaseOverviewPath(false);
            state.setGroupPurchaseOverview(false);
            state.setStockReduceQueryPath(false);
            state.setGroupStockReduceQuery(false);
            state.setBusinessDiagnosisPath(false);
            state.setCouponCostInsightBlocked(false);
            state.setBusinessOverviewPath(false);
            state.setRevenueOverviewPath(false);
            state.setRevenueAnswerPlan(null);
            state.setDishProfitPath(true);
            plan = new ArrayList<>(AiBusinessToolIds.DEFAULT_DISH_PROFIT_TOOLS);
            if (rCtx != null && rqi != null && StringUtils.hasText(rqi.getStructuredIntentDetail())) {
                String ingWire =
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rqi.getStructuredIntentDetail());
                if (AiQuerySemanticLexicon.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(ingWire)
                        && !plan.contains(AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN)) {
                    plan.add(AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN);
                }
            }
            state.setDataPlanTools(plan);
        } else if (stockReduceStandaloneIntent) {
            applyStockReduceQuestionBranch(state);
            plan = state.getDataPlanTools();
        } else if (revenueStandaloneIntent) {
            applyRevenueOverviewQuestionBranch(state);
            plan = state.getDataPlanTools();
        } else if (purchaseOverviewOnlyIntent) {
            applyPurchaseOverviewQuestionBranch(state);
            plan = state.getDataPlanTools();
        } else if (stockOverviewIntent) {
            applyInventoryOverviewQuestionBranch(state);
            plan = state.getDataPlanTools();
        } else if (!rawCostIntent) {
            state.setCostInsightPath(false);
            state.setPurchaseCostInsightPath(false);
            state.setWarehouseStockOverviewPath(false);
            state.setGroupWarehouseStockOverview(false);
            state.setPurchaseOverviewPath(false);
            state.setGroupPurchaseOverview(false);
            state.setBusinessOverviewPath(false);
            state.setDataPlanTools(new ArrayList<>());

            AiUserContext ctx = state.getAiUserContext();
            if (overviewIntent && ctx != null && AiRoleCodes.STORE_PURCHASER.equals(ctx.getRoleCode())) {
                applyStorePurchaserBusinessOverviewToPurchaseOverview(state, ctx);
                plan = state.getDataPlanTools();
            } else if (overviewIntent && ctx != null && AiRoleCodes.WAREHOUSE_MANAGER.equals(ctx.getRoleCode())) {
                applyWarehouseManagerBusinessOverviewToStockOverview(state, ctx);
                plan = state.getDataPlanTools();
            } else if (overviewIntent) {
                state.setBusinessOverviewPath(true);
                if (isBusinessOverviewMultiAgentMainline(rCtx)
                        || resolvedContextOrchestrationMultiAgentOverview(rCtx)) {
                    plan = new ArrayList<>(buildBusinessOverviewMultiAgentToolsPermissionFiltered(ctx));
                    applyGroupWideEmbeddedPurchaseStockFlags(state, plan);
                } else {
                    plan = new ArrayList<>();
                }
                state.setDataPlanTools(plan);
            } else {
                plan = List.of();
            }
        } else {
            state.setBusinessOverviewPath(false);
            state.setDishProfitPath(false);
            applyCostIntentBranch(state, q);
            plan = state.getDataPlanTools();
        }

        boolean overview = state.isBusinessOverviewPath();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("costInsightPath", state.isCostInsightPath());
        payload.put("purchaseCostInsightPath", state.isPurchaseCostInsightPath());
        payload.put("warehouseStockOverviewPath", state.isWarehouseStockOverviewPath());
        payload.put("groupWarehouseStockOverview", state.isGroupWarehouseStockOverview());
        payload.put("purchaseOverviewPath", state.isPurchaseOverviewPath());
        payload.put("groupPurchaseOverview", state.isGroupPurchaseOverview());
        payload.put("couponCostInsightBlocked", state.isCouponCostInsightBlocked());
        payload.put("businessOverviewPath", overview);
        payload.put("dishProfitPath", state.isDishProfitPath());
        payload.put("businessDiagnosisPath", state.isBusinessDiagnosisPath());
        payload.put("stockReduceQueryPath", state.isStockReduceQueryPath());
        payload.put("groupStockReduceQuery", state.isGroupStockReduceQuery());
        payload.put("revenueOverviewPath", state.isRevenueOverviewPath());
        payload.put("tools", plan == null ? List.of() : plan);
        payload.put("finalPlannerTools", plan == null ? List.of() : new ArrayList<>(plan));
        if (state.isBusinessDiagnosisPath()) {
            payload.put("plannerToolsSource", resolveBusinessDiagnosisPlannerToolsSource(rCtx));
        } else if (overview
                && (isBusinessOverviewMultiAgentMainline(rCtx)
                        || resolvedContextOrchestrationMultiAgentOverview(rCtx))) {
            payload.put("plannerToolsSource", "business_overview_matrix");
        }
        if (overview && (plan == null || plan.isEmpty())) {
            if (isBusinessOverviewMultiAgentMainline(rCtx)
                    || resolvedContextOrchestrationMultiAgentOverview(rCtx)) {
                payload.put("businessOverviewMultiAgentNoEligibleDomainTools", true);
            } else {
                payload.put("businessOverviewClassicPlanSuppressed", true);
            }
        }
        if (state.getIntentConvergence() != null && !state.getIntentConvergence().isEmpty()) {
            payload.put("intentConvergence", state.getIntentConvergence());
        }

        String displayText;
        if (state.isCouponCostInsightBlocked()) {
            displayText = "成本分析对该账号不可用，已跳过数据拉取";
        } else if (state.isBusinessDiagnosisPath()) {
            displayText = "已编排「经营诊断」链路 " + plan.size() + " 个数据来源（采购·出库/核销·菜品毛利）";
        } else if (state.isDishProfitPath()) {
            displayText = "已编排「菜品毛利透视」链路 " + plan.size() + " 个数据来源";
        } else if (state.isWarehouseStockOverviewPath()) {
            if (state.isGroupWarehouseStockOverview()) {
                boolean mixWh = resolvedOrgHasVisibleWarehouses(state);
                displayText = mixWh
                        ? "集团账号：库存问句已按集团下属门店/库房范围汇总，已编排 " + plan.size() + " 个数据来源"
                        : "集团账号：库存问句已按集团下属门店范围汇总，已编排 " + plan.size() + " 个数据来源";
            } else if (isBusinessToWarehouseStockConvergence(state)) {
                displayText = "库房端账号：经营问句已收敛为库存视角，已编排 " + plan.size() + " 个数据来源（不含营业额/菜品/毛利主链）";
            } else {
                displayText = "已编排「库存概览」链路 " + plan.size() + " 个数据来源（不含营业额/菜品销售主链）";
            }
        } else if (state.isStockReduceQueryPath()) {
            if (state.isGroupStockReduceQuery()) {
                displayText = "集团账号：出库/核销问句已按下属门店汇总，已编排 " + plan.size() + " 个数据来源";
            } else {
                displayText = "已编排「出库/核销」基础查询链路 " + plan.size() + " 个数据来源";
            }
        } else if (state.isRevenueOverviewPath()) {
            displayText = "已编排「日营业额/营收」链路 " + plan.size() + " 个数据来源";
        } else if (state.isPurchaseCostInsightPath() && state.isGroupPurchaseOverview()) {
            displayText = "集团账号：采购问句已按集团下属门店采购范围汇总，已编排 " + plan.size() + " 个数据来源";
        } else if (state.isPurchaseCostInsightPath()) {
            if (isBusinessToPurchaseConvergence(state)) {
                displayText = "门店采购账号：经营问句已收敛为采购视角，已编排 " + plan.size() + " 个数据来源（不含营业额/毛利主链）";
            } else {
                displayText = "已按采购视角编排数据来源 " + plan.size() + " 个工具（不含毛利/营业额主链）";
            }
        } else if (state.isCostInsightPath()) {
            displayText = "已编排「成本诊断」链路 " + plan.size() + " 个数据来源";
        } else if (rawCostIntent) {
            displayText = "成本问句已识别，但未编排数据源（请核对权限或联系管理员）";
        } else if (overview) {
            if (plan == null || plan.isEmpty()) {
                if (isBusinessOverviewMultiAgentMainline(rCtx)
                        || resolvedContextOrchestrationMultiAgentOverview(rCtx)) {
                    displayText = "经营概览四域编排：当前账号无可用数据来源权限，已跳过 Tool 链";
                } else {
                    displayText = "经营概览：已不再编排经典六工具链，已跳过 Tool 链";
                }
            } else {
                displayText = "已编排「经营概览」链路 " + plan.size() + " 个数据来源";
            }
        } else if (inBusinessChat) {
            displayText = "未匹配成本/经营概览关键词，将跳过经营 Tool 链";
        } else {
            displayText = "非 BUSINESS_CHAT，跳过经营工具链";
        }

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "DataPlannerNode",
                "displayText", displayText,
                "dataPlan", payload
        ));
        return state;
    }

    private static boolean isBusinessToPurchaseConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "PURCHASE_OVERVIEW".equals(ic.get("to"));
    }

    /**
     * Planner 已切到经营诊断编排后，将 Harness / Replay 可见的 path、intent 与解析意图对齐（避免仍显示 business_overview）。
     */
    private static void syncResolvedQueryContextToBusinessDiagnosis(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        if (ctx == null) {
            return;
        }
        AiResolvedQueryIntent qi = ctx.getQueryIntent();
        if (qi == null) {
            qi = AiResolvedQueryIntent.builder().build();
            ctx.setQueryIntent(qi);
        }
        qi.setPathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        qi.setIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
        if (qi.getStructuredIntentDetail() == null || qi.getStructuredIntentDetail().isBlank()) {
            qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY);
        }
        ctx.setEffectivePathCode(AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS);
        ctx.setEffectiveIntentCode(AiResolvedQueryIntent.BUSINESS_DIAGNOSIS);
    }


    /** 与库存抬头一致：仅当解析结果中确有独立库房节点时，文案才写「门店/库房」。 */
    private static boolean resolvedOrgHasVisibleWarehouses(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        var org = state.getResolvedQueryContext().getOrgScope();
        if (org == null || org.getVisibleWarehouses() == null) {
            return false;
        }
        return !org.getVisibleWarehouses().isEmpty();
    }

    private static void resetCostIntentFlags(AiRunState state) {
        state.setCouponCostInsightBlocked(false);
        state.setPurchaseCostInsightPath(false);
        state.setWarehouseStockOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setCostIntentConvergenceNote(null);
        state.setIntentConvergence(null);
        state.setWarehouseOverview(null);
        state.setPurchaseOverview(null);
        state.setPurchaseAnswerPlan(null);
        state.setStockReduceAnswerPlan(null);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setDishProfitPath(false);
        state.setBusinessDiagnosisPath(false);
        state.setRevenueOverviewPath(false);
        state.setRevenueAnswerPlan(null);
    }

    /**
     * 经营诊断：采购 + 出库/核销 + 菜品毛利 + 营业额（{@link AiPermissions#VIEW_REVENUE} 时追加
     * {@link AiBusinessToolIds#REVENUE_QUERY}， 挂载 {@link com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan}）；
     * 同一 {@link AiResolvedQueryContext}；权限可裁剪子集。
     */
    private static void applyBusinessDiagnosisBranch(AiRunState state) {
        state.setCostInsightPath(false);
        state.setBusinessOverviewPath(false);
        state.setDishProfitPath(false);
        state.setCouponCostInsightBlocked(false);
        state.setWarehouseStockOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setBusinessDiagnosisPath(true);

        state.setPurchaseCostInsightPath(false);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setRevenueOverviewPath(false);
        state.setRevenueAnswerPlan(null);
        state.setDataPlanTools(new ArrayList<>());

        AiUserContext ctx = state.getAiUserContext();
        if (ctx == null) {
            state.setBusinessDiagnosisPath(false);
            return;
        }
        String diagWire = resolveCanonicalStructuredWireFromContext(state);
        List<String> matrixTools = BusinessDiagnosisSemanticCapabilityMatrix.plannerToolsForWire(diagWire);
        List<String> tools = permissionFilterPlannerToolsFromMatrix(ctx, matrixTools);
        if (tools.isEmpty()) {
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    AiBusinessToolIds.PURCHASE_OVERVIEW, AiPermissions.VIEW_PURCHASE));
            state.setBusinessDiagnosisPath(false);
            return;
        }
        boolean mayPurchase = tools.contains(AiBusinessToolIds.PURCHASE_OVERVIEW);
        boolean mayStock = tools.contains(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        boolean mayDish = tools.contains(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        boolean mayRevenue = tools.contains(AiBusinessToolIds.REVENUE_QUERY);

        String roleCode = ctx.getRoleCode();
        String ts = AiTimeWindowTextFormatter.forAnswer(state).getTimeSubjectText();
        if (AiRoleMapper.isGroupWideOrgScope(roleCode)) {
            if (resolvedOrgIsSingleEffectiveStore(state)) {
                Map<String, String> ic = new LinkedHashMap<>();
                ic.put("from", "BUSINESS_DIAGNOSIS");
                ic.put("to", "STORE_BUSINESS_DIAGNOSIS");
                ic.put("reason", "集团账号但本句收窄为单一门店：诊断仅汇总该门店");
                state.setIntentConvergence(ic);
                state.setCostIntentConvergenceNote("本句已点名或收窄为单一门店，经营诊断仅按该门店权限范围汇总（采购、出库/核销"
                        + (mayDish ? "、菜品毛利" : "")
                        + (mayRevenue ? "、营业额" : "") + "）。");
                state.setGroupPurchaseOverview(false);
                state.setGroupStockReduceQuery(false);
                state.setPurchaseOverviewPath(mayPurchase);
                state.setPurchaseCostInsightPath(mayPurchase);
                state.setStockReduceQueryPath(mayStock);
                state.setDataPlanTools(new ArrayList<>(tools));
                applyWarehouseBusinessDiagnosisScopeNote(state);
                return;
            }
            Map<String, String> ic = new LinkedHashMap<>();
            ic.put("from", "BUSINESS_DIAGNOSIS");
            ic.put("to", "GROUP_BUSINESS_DIAGNOSIS");
            ic.put("reason", "集团管理账号：按 visibleStores 门店合并汇总经营诊断");
            state.setIntentConvergence(ic);
            state.setCostIntentConvergenceNote("下面按集团权限范围内门店合并做经营诊断（"
                    + ts + "；含采购、出库/核销" + (mayDish ? "、菜品毛利" : "")
                    + (mayRevenue ? "、营业额" : "") + "）。");
            state.setGroupPurchaseOverview(mayPurchase);
            state.setGroupStockReduceQuery(mayStock);
            state.setPurchaseOverviewPath(mayPurchase);
            state.setPurchaseCostInsightPath(mayPurchase);
            state.setStockReduceQueryPath(mayStock);
            state.setDataPlanTools(new ArrayList<>(tools));
            applyWarehouseBusinessDiagnosisScopeNote(state);
            return;
        }

        state.setGroupPurchaseOverview(false);
        state.setGroupStockReduceQuery(false);
        state.setPurchaseOverviewPath(mayPurchase);
        state.setPurchaseCostInsightPath(mayPurchase);
        state.setStockReduceQueryPath(mayStock);
        state.setCostIntentConvergenceNote("按当前门店/部门权限汇总经营诊断（" + ts + "）。");
        state.setDataPlanTools(new ArrayList<>(tools));
        applyWarehouseBusinessDiagnosisScopeNote(state);
    }

    /** D-11：库房 Scope 下的经营诊断意图说明边界（不做集团多门店经营排名话术）。 */
    private static void applyWarehouseBusinessDiagnosisScopeNote(AiRunState state) {
        if (state == null || !state.isBusinessDiagnosisPath()) {
            return;
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext() != null
                ? state.getResolvedQueryContext().getOrgScope()
                : null;
        if (org == null || !AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(org.getScopeType())) {
            return;
        }
        state.setCostIntentConvergenceNote(
                "当前账号为库房端，只能查看本库房及所属门店权限范围内的库存、出库/核销与采购入库信号；未授权查看营业额与菜品毛利，亦不做集团或多门店综合经营排名。");
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
     * {@code business_overview_summary/status/compare} + MULTI_AGENT 唯一主线：只计划四域专线工具，
     * 权限裁剪为空时保持空 plan，禁止 silent 回退 classic 六工具链（已删除，见 docs/AI_MAINLINE_INDEX.md）。
     */
    private static boolean isBusinessOverviewMultiAgentMainline(AiResolvedQueryContext rq) {
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

    /**
     * 固定四域能力与顺序（对齐 {@link AiBusinessToolIds#BUSINESS_OVERVIEW_MULTI_AGENT_DOMAIN_TOOLS}）；仅权限裁剪，
     * 不根据用户原文删减域。
     */
    private static List<String> buildBusinessOverviewMultiAgentToolsPermissionFiltered(AiUserContext ctx) {
        return permissionFilterPlannerToolsFromMatrix(
                ctx, BusinessOverviewSemanticCapabilityMatrix.defaultFourDomainPlannerTools());
    }

    private static String resolveCanonicalStructuredWireFromContext(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return null;
        }
        AiResolvedQueryIntent qi = state.getResolvedQueryContext().getQueryIntent();
        if (qi == null || !StringUtils.hasText(qi.getStructuredIntentDetail())) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                qi.getStructuredIntentDetail().trim());
    }

    private static String resolveBusinessDiagnosisPlannerToolsSource(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return "business_diagnosis_matrix";
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        rq.getQueryIntent().getStructuredIntentDetail());
        return BusinessDiagnosisSemanticCapabilityMatrix.isDualDomainPurchaseStockWire(wire)
                ? "business_diagnosis_matrix_dual_domain"
                : "business_diagnosis_matrix_four_domain";
    }

    /**
     * Matrix 工具表为准：仅保留 matrix 列出的 tool，并按权限裁剪（LLM selectedTools 冲突时由 Resolver 先对齐）。
     */
    private static List<String> permissionFilterPlannerToolsFromMatrix(
            AiUserContext ctx, List<String> matrixTools) {
        if (ctx == null || matrixTools == null || matrixTools.isEmpty()) {
            return List.of();
        }
        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
        boolean mayPurchase = perms.contains(AiPermissions.VIEW_PURCHASE)
                || ((AiRoleCodes.WAREHOUSE_MANAGER.equals(ctx.getRoleCode())
                        || AiRoleCodes.REGION_WAREHOUSE.equals(ctx.getRoleCode()))
                && perms.contains(AiPermissions.VIEW_STOCK));
        boolean mayStock = perms.contains(AiPermissions.VIEW_STOCK);
        boolean mayDish = mayDishProfitToolForDiagnosis(ctx, perms);
        boolean mayRevenue = perms.contains(AiPermissions.VIEW_REVENUE);
        List<String> tools = new ArrayList<>();
        for (String toolId : matrixTools) {
            if (!StringUtils.hasText(toolId)) {
                continue;
            }
            String t = toolId.trim();
            if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(t) && mayPurchase) {
                tools.add(t);
            } else if (AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(t) && mayStock) {
                tools.add(t);
            } else if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(t) && mayDish) {
                tools.add(t);
            } else if (AiBusinessToolIds.REVENUE_QUERY.equals(t) && mayRevenue) {
                tools.add(t);
            }
        }
        return tools;
    }

    private static boolean mayDishProfitToolForDiagnosis(AiUserContext ctx, Set<String> perms) {
        if (ctx == null) {
            return false;
        }
        String rc = ctx.getRoleCode();
        if (CostInsightIntentConvergence.isProcurementCostConvergenceRole(rc)) {
            return false;
        }
        if (AiRoleCodes.WAREHOUSE_MANAGER.equals(rc) || AiRoleCodes.REGION_WAREHOUSE.equals(rc)) {
            return false;
        }
        return perms.contains(AiPermissions.VIEW_DISH_SALES) && perms.contains(AiPermissions.VIEW_COST);
    }

    /** 出库/核销基础查询专线（单日历自然口；与成本主链内嵌 {@link AiBusinessToolIds#STOCK_REDUCE_QUERY} 区分）。 */
    private static void applyStockReduceQuestionBranch(AiRunState state) {
        state.setCostInsightPath(false);
        state.setPurchaseCostInsightPath(false);
        state.setBusinessOverviewPath(false);
        state.setDishProfitPath(false);
        state.setCouponCostInsightBlocked(false);
        state.setWarehouseStockOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setRevenueOverviewPath(false);
        state.setRevenueAnswerPlan(null);
        state.setDataPlanTools(new ArrayList<>());

        AiUserContext ctx = state.getAiUserContext();
        if (ctx == null) {
            return;
        }
        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
        if (!perms.contains(AiPermissions.VIEW_STOCK)) {
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    AiBusinessToolIds.STOCK_REDUCE_QUERY, AiPermissions.VIEW_STOCK));
            return;
        }

        List<String> tools = List.of(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        String roleCode = ctx.getRoleCode();
        String ts = AiTimeWindowTextFormatter.forAnswer(state).getTimeSubjectText();

        if (AiRoleMapper.isGroupWideOrgScope(roleCode)) {
            if (resolvedOrgIsSingleEffectiveStore(state)) {
                Map<String, String> ic = new LinkedHashMap<>();
                ic.put("from", "STOCK_REDUCE_INQUIRY");
                ic.put("to", "STORE_STOCK_REDUCE_QUERY");
                ic.put("reason", "集团账号但本句收窄为单一门店：仅汇总该门店出库/核销");
                state.setIntentConvergence(ic);
                state.setCostIntentConvergenceNote(
                        "本句已点名或收窄为单一门店，下面仅按该门店权限范围汇总出库/核销金额（自然日，类型1–4 金额合计）。");
                state.setGroupStockReduceQuery(false);
                state.setStockReduceQueryPath(true);
                state.setDataPlanTools(new ArrayList<>(tools));
                return;
            }
            Map<String, String> ic = new LinkedHashMap<>();
            ic.put("from", "STOCK_REDUCE_INQUIRY");
            ic.put("to", "GROUP_STOCK_REDUCE_QUERY");
            ic.put("reason", "集团账号：按下属 visibleStores 门店根 in 聚合出库/核销");
            state.setIntentConvergence(ic);
            state.setCostIntentConvergenceNote(
                    "下面按集团权限范围内下属门店合并汇总出库/核销金额（自然日历日统计，生产耗用+废弃+损耗+退货四类金额之和）。");
            state.setGroupStockReduceQuery(true);
            state.setStockReduceQueryPath(true);
            state.setDataPlanTools(new ArrayList<>(tools));
            return;
        }

        Map<String, String> ic = new LinkedHashMap<>();
        ic.put("from", "STOCK_REDUCE_INQUIRY");
        ic.put("to", "STORE_STOCK_REDUCE_QUERY");
        ic.put("reason", "门店/库房类账号：按归属门店口径汇总出库/核销");
        state.setIntentConvergence(ic);
        state.setCostIntentConvergenceNote("下面按你可查看的范围汇总" + ts + "出库/核销金额（自然日，类型1–4 之和）。");
        state.setGroupStockReduceQuery(false);
        state.setStockReduceQueryPath(true);
        state.setDataPlanTools(new ArrayList<>(tools));
    }

    /**
     * 「库存怎么样」类问句：优先于经营泛化话术；集团 {@link AiRoleMapper#isGroupWideOrgScope(String)} 走多门店汇总，禁止落入通用闲聊反问门店。
     */
    private static void applyInventoryOverviewQuestionBranch(AiRunState state) {
        state.setCostInsightPath(false);
        state.setPurchaseCostInsightPath(false);
        state.setBusinessOverviewPath(false);
        state.setDishProfitPath(false);
        state.setCouponCostInsightBlocked(false);
        state.setWarehouseStockOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setRevenueOverviewPath(false);
        state.setRevenueAnswerPlan(null);
        state.setDataPlanTools(new ArrayList<>());

        AiUserContext ctx = state.getAiUserContext();
        if (ctx == null) {
            return;
        }
        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
        if (!perms.contains(AiPermissions.VIEW_STOCK)) {
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    "warehouse_stock_overview", AiPermissions.VIEW_STOCK));
            return;
        }

        List<String> tools = WarehouseStockIntentConvergence.buildWarehouseStockOverviewTools(perms);
        if (tools.isEmpty()) {
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    "warehouse_stock_overview", AiPermissions.VIEW_STOCK));
            return;
        }

        String roleCode = ctx.getRoleCode();
        if (AiRoleMapper.isGroupWideOrgScope(roleCode)) {
            if (resolvedOrgIsSingleEffectiveStore(state)) {
                Map<String, String> ic = new LinkedHashMap<>();
                ic.put("from", "STOCK_INQUIRY");
                ic.put("to", "STORE_WAREHOUSE_STOCK_OVERVIEW");
                ic.put("reason", "集团账号但本句点名单一门店：仅汇总该门店库存，不作集团下属全部门店合并");
                state.setIntentConvergence(ic);
                mergeScopeConvergenceNote(state, "本句已点名具体门店，下面仅按该门店库存汇总（非全集团合并）。");
                state.setCostIntentConvergenceNote(
                        "下面仅按该门店权限范围汇总库存、入库与核销结构。");
                state.setGroupWarehouseStockOverview(false);
                state.setWarehouseStockOverviewPath(true);
                state.setDataPlanTools(new ArrayList<>(tools));
                return;
            }
            Map<String, String> ic = new LinkedHashMap<>();
            ic.put("from", "STOCK_INQUIRY");
            ic.put("to", "GROUP_WAREHOUSE_STOCK_OVERVIEW");
            ic.put("reason", "集团管理账号查询库存：按 distributerId 下属门店根汇总，不使用登录 departmentId 当作单一门店");
            state.setIntentConvergence(ic);
            boolean mixWh = resolvedOrgHasVisibleWarehouses(state);
            mergeScopeConvergenceNote(state, mixWh
                    ? "你当前账号可查看集团下属门店/库房库存汇总，下面按集团范围为你分析。"
                    : "你当前账号可查看集团下属门店库存汇总，下面按集团范围为你分析。");
            state.setCostIntentConvergenceNote(mixWh
                    ? "下面按集团权限范围内门店/库房合并汇总库存、入库与核销结构（不含营业额、订单、客单价）。"
                    : "下面按集团权限范围内门店合并汇总库存、入库与核销结构（不含营业额、订单、客单价）。");
            state.setGroupWarehouseStockOverview(true);
            state.setWarehouseStockOverviewPath(true);
            state.setDataPlanTools(new ArrayList<>(tools));
            return;
        }

        if (AiRoleCodes.WAREHOUSE_MANAGER.equals(roleCode)) {
            Map<String, String> ic = new LinkedHashMap<>();
            ic.put("from", "STOCK_INQUIRY");
            ic.put("to", "WAREHOUSE_STOCK_OVERVIEW");
            ic.put("reason", "库房端账号查询库存：按所在库房（归一到所属门店根）汇总");
            state.setIntentConvergence(ic);
            mergeScopeConvergenceNote(state,
                    "你当前账号为库房端，下面按你可查看的库房/所属门店库存汇总（不按集团全部门店）。");
            state.setCostIntentConvergenceNote(
                    "下面按库房权限汇总当前库存、区间内入库与核销（生产耗用/废弃/损耗/退货），不含营业额与菜品销售。");
            state.setWarehouseStockOverviewPath(true);
            state.setDataPlanTools(new ArrayList<>(tools));
            return;
        }

        Map<String, String> ic = new LinkedHashMap<>();
        ic.put("from", "STOCK_INQUIRY");
        ic.put("to", "STORE_WAREHOUSE_STOCK_OVERVIEW");
        ic.put("reason", "门店侧账号查询库存：按本人归属门店（部门归一）汇总");
        state.setIntentConvergence(ic);
        mergeScopeConvergenceNote(state,
                "下面按你当前账号可归集的门店/部门库存汇总（非集团全部门店）。");
        state.setCostIntentConvergenceNote(
                "下面按门店权限汇总库存与核销结构，不含营业额、订单、客单价。");
        state.setWarehouseStockOverviewPath(true);
        state.setDataPlanTools(new ArrayList<>(tools));
    }

    /** 日营业额 / 营收专线：仅 {@link AiBusinessToolIds#REVENUE_QUERY}，与成本主链内嵌营收 Tool 区分；
     * {@link DailyRevenueAnswerPlanBuilder} 在 {@link AiRunState#isRevenueOverviewPath()} 或经营诊断 path 上挂载。 */
    private static void applyRevenueOverviewQuestionBranch(AiRunState state) {
        state.setCostInsightPath(false);
        state.setPurchaseCostInsightPath(false);
        state.setBusinessOverviewPath(false);
        state.setDishProfitPath(false);
        state.setCouponCostInsightBlocked(false);
        state.setWarehouseStockOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setBusinessDiagnosisPath(false);
        state.setDataPlanTools(new ArrayList<>());

        AiUserContext ctx = state.getAiUserContext();
        if (ctx == null) {
            return;
        }
        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
        if (!perms.contains(AiPermissions.VIEW_REVENUE)) {
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    AiBusinessToolIds.REVENUE_QUERY, AiPermissions.VIEW_REVENUE));
            return;
        }

        Map<String, String> ic = new LinkedHashMap<>();
        ic.put("from", "REVENUE_INQUIRY");
        ic.put("to", "REVENUE_OVERVIEW");
        ic.put("reason", "独占日营业额/营收查询链路");
        state.setIntentConvergence(ic);
        state.setCostIntentConvergenceNote(null);
        state.setRevenueOverviewPath(true);
        state.setDataPlanTools(new ArrayList<>(List.of(AiBusinessToolIds.REVENUE_QUERY)));
    }

    private static void applyPurchaseOverviewQuestionBranch(AiRunState state) {
        state.setCostInsightPath(false);
        state.setBusinessOverviewPath(false);
        state.setDishProfitPath(false);
        state.setCouponCostInsightBlocked(false);
        state.setWarehouseStockOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setPurchaseCostInsightPath(false);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setRevenueOverviewPath(false);
        state.setRevenueAnswerPlan(null);
        state.setDataPlanTools(new ArrayList<>());

        AiUserContext ctx = state.getAiUserContext();
        if (ctx == null) {
            return;
        }
        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
        boolean mayPurchaseData = perms.contains(AiPermissions.VIEW_PURCHASE)
                || ((AiRoleCodes.WAREHOUSE_MANAGER.equals(ctx.getRoleCode())
                || AiRoleCodes.REGION_WAREHOUSE.equals(ctx.getRoleCode()))
                && perms.contains(AiPermissions.VIEW_STOCK));
        if (!mayPurchaseData) {
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    "purchase_overview", AiPermissions.VIEW_PURCHASE));
            return;
        }

        String roleCode = ctx.getRoleCode();
        // purchase_overview_path：专线仅编排 PURCHASE_OVERVIEW（Master gate 要求计划唯一工具；
        // 出库/核销另走 stock_reduce_query_path / 采购成本洞察分支）。
        List<String> tools = new ArrayList<>(List.of(AiBusinessToolIds.PURCHASE_OVERVIEW));

        if (AiRoleMapper.isGroupWideOrgScope(roleCode)) {
            if (resolvedOrgIsSingleEffectiveStore(state)) {
                Map<String, String> ic = new LinkedHashMap<>();
                ic.put("from", "PURCHASE_INQUIRY");
                ic.put("to", "STORE_PURCHASE_OVERVIEW");
                ic.put("reason", "集团账号但本句点名单一门店：仅汇总该门店采购，不作集团合并");
                state.setIntentConvergence(ic);
                if (inheritedEffectiveOrgScopeThisPurchaseTurn(state)) {
                    mergeScopeConvergenceNote(state, groupSingleStoreInheritedPurchaseScopeUserLine(state));
                    state.setCostIntentConvergenceNote(null);
                } else {
                    state.setCostIntentConvergenceNote(
                            "本句已点名具体门店，下面仅按该门店权限范围汇总采购入库（非全集团合并）。");
                }
                state.setGroupPurchaseOverview(false);
                state.setPurchaseOverviewPath(true);
                state.setPurchaseCostInsightPath(true);
                state.setDataPlanTools(new ArrayList<>(tools));
                return;
            }
            Map<String, String> ic = new LinkedHashMap<>();
            ic.put("from", "PURCHASE_INQUIRY");
            ic.put("to", "GROUP_PURCHASE_OVERVIEW");
            ic.put("reason", "集团管理账号查询采购：按 visibleStores 对应门店采购部门汇总");
            state.setIntentConvergence(ic);
            state.setCostIntentConvergenceNote(
                    "下面按集团权限范围内门店合并汇总采购入库（不含营业额、订单、客单价、毛利）。");
            state.setGroupPurchaseOverview(true);
            state.setPurchaseOverviewPath(true);
            state.setPurchaseCostInsightPath(true);
            state.setDataPlanTools(new ArrayList<>(tools));
            return;
        }

        if (AiRoleCodes.WAREHOUSE_MANAGER.equals(roleCode) || AiRoleCodes.REGION_WAREHOUSE.equals(roleCode)) {
            Map<String, String> ic = new LinkedHashMap<>();
            ic.put("from", "PURCHASE_INQUIRY");
            ic.put("to", "WAREHOUSE_PURCHASE_RECEIPT_VIEW");
            ic.put("reason", "库房端：按本人可见库房/所属门店采购入库视角汇总");
            state.setIntentConvergence(ic);
            state.setCostIntentConvergenceNote("下面按库房权限汇总采购入库情况，不作集团全部门店采购经营分析。");
            state.setGroupPurchaseOverview(false);
            state.setPurchaseOverviewPath(true);
            state.setPurchaseCostInsightPath(true);
            state.setDataPlanTools(List.of(AiBusinessToolIds.PURCHASE_OVERVIEW));
            return;
        }

        Map<String, String> ic = new LinkedHashMap<>();
        ic.put("from", "PURCHASE_INQUIRY");
        ic.put("to", "STORE_PURCHASE_OVERVIEW");
        ic.put("reason", "门店侧账号查询采购：按本人归属门店（部门归一）汇总");
        state.setIntentConvergence(ic);
        state.setCostIntentConvergenceNote("下面按门店权限汇总采购入库。");
        state.setGroupPurchaseOverview(false);
        state.setPurchaseOverviewPath(true);
        state.setPurchaseCostInsightPath(true);
        state.setDataPlanTools(new ArrayList<>(tools));
    }

    /**
     * 四域内嵌采购/出库 Tool 的集团聚合旗标（经营概览 MULTI、成本诊断全链、与 {@link #applyBusinessDiagnosisBranch} 对齐）。
     * 单店收窄时不置集团合并；不恢复 classic overview。
     */
    private static void applyGroupWideEmbeddedPurchaseStockFlags(AiRunState state, List<String> plannedTools) {
        if (state == null || plannedTools == null || plannedTools.isEmpty()) {
            return;
        }
        boolean mayPurchase = plannedTools.contains(AiBusinessToolIds.PURCHASE_OVERVIEW);
        boolean mayStock = plannedTools.contains(AiBusinessToolIds.STOCK_REDUCE_QUERY);
        if (!mayPurchase && !mayStock) {
            return;
        }
        if (!isGroupWidePurchaseStockScope(state)) {
            state.setGroupPurchaseOverview(false);
            state.setGroupStockReduceQuery(false);
            return;
        }
        if (resolvedOrgIsSingleEffectiveStore(state)) {
            state.setGroupPurchaseOverview(false);
            state.setGroupStockReduceQuery(false);
            return;
        }
        state.setGroupPurchaseOverview(mayPurchase);
        state.setGroupStockReduceQuery(mayStock);
    }

    /** 集团管理员或 orgScope=GROUP：内嵌采购/出库 Tool 可走集团 visibleStores 聚合。 */
    private static boolean isGroupWidePurchaseStockScope(AiRunState state) {
        AiUserContext ctx = state != null ? state.getAiUserContext() : null;
        if (ctx != null && AiRoleMapper.isGroupWideOrgScope(ctx.getRoleCode())) {
            return true;
        }
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
        return org != null && AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType());
    }

    /**
     * 解析结果已收窄到单一门店根（点名门店 / 单店 scope）时，即使账号为集团视角也不可再走集团采购合并。
     */
    private static boolean resolvedOrgIsSingleEffectiveStore(AiRunState state) {
        if (state == null || state.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedOrgScope org = state.getResolvedQueryContext().getOrgScope();
        if (org == null) {
            return false;
        }
        if (AiResolvedOrgScope.SCOPE_STORE.equals(org.getScopeType())
                || AiResolvedOrgScope.SCOPE_PURCHASER.equals(org.getScopeType())) {
            return true;
        }
        List<AiStoreScopeDTO> vs = org.getVisibleStores();
        return vs != null && vs.size() == 1
                && vs.get(0) != null
                && vs.get(0).getStoreDepartmentId() != null;
    }

    private static boolean isBusinessToWarehouseStockConvergence(AiRunState state) {
        Map<String, String> ic = state.getIntentConvergence();
        return ic != null
                && "BUSINESS_OVERVIEW".equals(ic.get("from"))
                && "WAREHOUSE_STOCK_OVERVIEW".equals(ic.get("to"));
    }

    /**
     * 库房端问「经营怎么样」：BUSINESS_OVERVIEW → WAREHOUSE_STOCK_OVERVIEW，仅编排库存类 Tool。
     */
    private static void applyWarehouseManagerBusinessOverviewToStockOverview(AiRunState state, AiUserContext ctx) {
        Map<String, String> ic = new LinkedHashMap<>();
        ic.put("from", "BUSINESS_OVERVIEW");
        ic.put("to", "WAREHOUSE_STOCK_OVERVIEW");
        ic.put("reason", "当前账号是库房端角色，不能查看完整经营数据，已切换为库房库存视角分析");
        state.setIntentConvergence(ic);

        state.setBusinessOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        mergeScopeConvergenceNote(state,
                "你当前账号只能查看所在库房的库存相关数据，下面按库房库存视角为你分析。");
        state.setCostIntentConvergenceNote(
                "你当前账号是库房端角色，不能查看完整经营数据。下面按你的权限，为你分析当前库房库存情况。");

        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
        List<String> tools = WarehouseStockIntentConvergence.buildWarehouseStockOverviewTools(perms);
        if (tools.isEmpty()) {
            state.setWarehouseStockOverviewPath(false);
            state.setDataPlanTools(new ArrayList<>());
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    "warehouse_stock_overview", AiPermissions.VIEW_STOCK));
            state.setCostIntentConvergenceNote(
                    "你当前账号是库房端角色，不能查看完整经营数据。下面本应为你汇总库房库存情况，但当前账号缺少库存查看权限，请联系管理员开通。");
            return;
        }
        state.setWarehouseStockOverviewPath(true);
        state.setDataPlanTools(new ArrayList<>(tools));
    }

    /**
     * 门店采购端问「经营怎么样」：原始 BUSINESS_OVERVIEW → PURCHASE_OVERVIEW，仅编排采购/库存工具。
     */
    private static void applyStorePurchaserBusinessOverviewToPurchaseOverview(AiRunState state, AiUserContext ctx) {
        String ts = AiTimeWindowTextFormatter.forAnswer(state).getTimeSubjectText();
        Map<String, String> ic = new LinkedHashMap<>();
        ic.put("from", "BUSINESS_OVERVIEW");
        ic.put("to", "PURCHASE_OVERVIEW");
        ic.put("reason", "当前账号是门店采购角色，不能查看完整经营数据，已切换为采购视角分析");
        state.setIntentConvergence(ic);

        state.setBusinessOverviewPath(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
        List<String> tools = CostInsightIntentConvergence.buildPurchaseCostInsightTools(perms);
        if (tools.isEmpty()) {
            state.setPurchaseCostInsightPath(false);
            state.setPurchaseOverviewPath(false);
            state.setGroupPurchaseOverview(false);
            state.setDataPlanTools(new ArrayList<>());
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    "purchase_overview", AiPermissions.VIEW_PURCHASE));
            state.setCostIntentConvergenceNote(
                    "你当前账号是门店采购角色，不能查看完整经营数据。下面本应为你汇总" + ts + "的采购情况，但当前账号缺少采购或库存数据查看权限，请联系管理员开通。");
            return;
        }
        state.setPurchaseCostInsightPath(true);
        state.setPurchaseOverviewPath(true);
        state.setGroupPurchaseOverview(false);
        state.setDataPlanTools(new ArrayList<>(tools));
        state.setCostIntentConvergenceNote(
                "你当前账号是门店采购角色，不能查看完整经营数据。下面按你的权限，为你分析" + ts + "的采购情况。");
    }

    private static void mergeScopeConvergenceNote(AiRunState state, String noteLine) {
        if (noteLine == null || noteLine.isBlank()) {
            return;
        }
        String cur = state.getScopeConvergenceNote();
        state.setScopeConvergenceNote(cur == null || cur.isBlank() ? noteLine : cur + "\n" + noteLine);
    }

    /**
     * 全量成本诊断链（非采购角色收敛）：与 {@link AiBusinessToolIds#DEFAULT_COST_INSIGHT_TOOLS} 对齐。
     * 有登录上下文时按权限裁剪；无上下文时沿用完整序（Replay/Harness）。
     */
    private static void applyFullCostInsightPath(AiRunState state) {
        state.setCouponCostInsightBlocked(false);
        state.setCostInsightPath(true);
        state.setPurchaseCostInsightPath(false);
        state.setBusinessOverviewPath(false);
        state.setDishProfitPath(false);
        state.setWarehouseStockOverviewPath(false);
        state.setGroupWarehouseStockOverview(false);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setRevenueOverviewPath(false);
        state.setRevenueAnswerPlan(null);
        state.setBusinessDiagnosisPath(false);

        AiUserContext ctx = state.getAiUserContext();
        if (ctx != null) {
            Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());
            List<String> tools = new ArrayList<>();
            for (String toolId : AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS) {
                String req = AiPermissionGuard.requiredPermissionForTool(toolId);
                if (req == null || perms.contains(req)) {
                    tools.add(toolId);
                }
            }
            if (tools.isEmpty()) {
                state.setCostInsightPath(false);
                state.setDataPlanTools(new ArrayList<>());
                state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                        AiBusinessToolIds.REVENUE_QUERY, AiPermissions.VIEW_REVENUE));
                return;
            }
            state.setDataPlanTools(new ArrayList<>(tools));
            applyGroupWideEmbeddedPurchaseStockFlags(state, tools);
            return;
        }
        List<String> defaultTools = new ArrayList<>(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS);
        state.setDataPlanTools(defaultTools);
        applyGroupWideEmbeddedPurchaseStockFlags(state, defaultTools);
    }

    private void applyCostIntentBranch(AiRunState state, String q) {
        AiUserContext ctx = state.getAiUserContext();
        if (ctx == null) {
            applyFullCostInsightPath(state);
            return;
        }

        String roleCode = ctx.getRoleCode();

        if (AiRoleCodes.COUPON_OPERATOR.equals(roleCode)) {
            state.setCostInsightPath(false);
            state.setPurchaseCostInsightPath(false);
            state.setPurchaseOverviewPath(false);
            state.setGroupPurchaseOverview(false);
            state.setWarehouseStockOverviewPath(false);
            state.setStockReduceQueryPath(false);
            state.setGroupStockReduceQuery(false);
            state.setRevenueOverviewPath(false);
            state.setRevenueAnswerPlan(null);
            state.setCouponCostInsightBlocked(true);
            state.setDataPlanTools(new ArrayList<>());
            state.getPermissionDenials().add(AiAnswerBoundary.forCouponOperatorCostInsight());
            return;
        }

        Set<String> perms = ctx.getPermissions() == null ? Set.of() : Set.copyOf(ctx.getPermissions());

        String costTimeSubject = AiTimeWindowTextFormatter.forAnswer(state).getTimeSubjectText();
        if (CostInsightIntentConvergence.isProcurementCostConvergenceRole(roleCode)) {
            List<String> tools = CostInsightIntentConvergence.buildPurchaseCostInsightTools(perms);
            if (tools.isEmpty()) {
                state.setCostInsightPath(false);
                state.setPurchaseCostInsightPath(false);
                state.setPurchaseOverviewPath(false);
                state.setGroupPurchaseOverview(false);
                state.setWarehouseStockOverviewPath(false);
                state.setStockReduceQueryPath(false);
                state.setGroupStockReduceQuery(false);
                state.setRevenueOverviewPath(false);
                state.setRevenueAnswerPlan(null);
                state.setDataPlanTools(new ArrayList<>());
                state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                        "purchase_cost_analysis", AiPermissions.VIEW_PURCHASE));
                return;
            }
            state.setCostInsightPath(false);
            state.setPurchaseCostInsightPath(true);
            state.setPurchaseOverviewPath(true);
            state.setGroupPurchaseOverview(false);
            state.setWarehouseStockOverviewPath(false);
            state.setStockReduceQueryPath(false);
            state.setGroupStockReduceQuery(false);
            state.setRevenueOverviewPath(false);
            state.setRevenueAnswerPlan(null);
            state.setDataPlanTools(new ArrayList<>(tools));
            state.setCostIntentConvergenceNote(
                    "你当前账号是采购角色，不能查看完整经营成本和毛利分析。我可以从采购视角帮你分析" + costTimeSubject
                            + "的采购成本、供应商价格和入库情况。");
            return;
        }

        applyFullCostInsightPath(state);

        if (CostInsightIntentConvergence.shouldAddStoreScopedGroupCostDisclaimer(ctx, q)) {
            mergeScopeConvergenceNote(state,
                    "你当前账号只能查看本门店数据。下面是本门店" + costTimeSubject + "的成本情况。");
        }
    }

    /** 组织口径由上一轮继承（本句未点名新店/新范围）时，不向用户写「本句已点名具体门店」。 */
    private static boolean inheritedEffectiveOrgScopeThisPurchaseTurn(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        return ctx != null && "INHERITED_PREVIOUS".equals(ctx.getEffectiveScopeSource());
    }

    private static boolean purchaseQuestionUsesSupplierSourceThisTurn(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        if (ctx == null || ctx.getQueryIntent() == null) {
            return false;
        }
        return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(ctx.getQueryIntent().getPurchaseSourceType());
    }

    /**
     * 【查询范围】用：集团账号 + 已收窄到单一门店时，若为继承口径则说明沿用上文门店/时间，供货商问法补「本轮只看供货商采购」。
     */
    private static String groupSingleStoreInheritedPurchaseScopeUserLine(AiRunState state) {
        AiResolvedQueryContext ctx = state != null ? state.getResolvedQueryContext() : null;
        String storeLabel = singleVisibleStoreUserLabel(ctx != null ? ctx.getOrgScope() : null);
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        String timeLabel = tw.getTimeSubjectText() != null && !tw.getTimeSubjectText().isBlank()
                ? tw.getTimeSubjectText()
                : "该统计区间";
        String storePart = storeLabel != null && !storeLabel.isBlank() ? storeLabel : "上文门店";
        StringBuilder sb = new StringBuilder();
        sb.append("沿用上文「").append(storePart).append(" + ").append(timeLabel).append("」口径");
        if (purchaseQuestionUsesSupplierSourceThisTurn(state)) {
            sb.append("，本轮只看供货商采购。");
        } else {
            sb.append("，下面仅按该门店权限范围汇总采购入库（非全集团合并）。");
        }
        return sb.toString();
    }

    private static String singleVisibleStoreUserLabel(AiResolvedOrgScope orgScope) {
        if (orgScope == null || orgScope.getVisibleStores() == null || orgScope.getVisibleStores().isEmpty()) {
            return null;
        }
        List<AiStoreScopeDTO> vs = orgScope.getVisibleStores();
        if (vs.size() == 1 && vs.get(0) != null && vs.get(0).getStoreName() != null) {
            return vs.get(0).getStoreName().trim();
        }
        return null;
    }
}
