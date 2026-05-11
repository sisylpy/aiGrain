package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class BusinessDataPlannerNode implements AgentNode {

    private final AiSseEventPublisher publisher;

    /** 「鱼香肉丝毛利」类单菜问法（排除同时含「菜品」类泛词时的重复匹配） */
    private static final Pattern SINGLE_DISH_MARGIN = Pattern.compile("[\\u4e00-\\u9fa5]{2,18}(毛利率|毛利)");

    /** 「本月毛利」等泛时间前缀走成本主线，不走菜品毛利透视（参见 {@link #singleDishMarginHasNamedDishCue}）。 */
    private static final Set<String> GENERIC_MARGIN_TIME_PREFIXES = Set.of(
            "本月", "上月", "这个月", "上个月", "本周", "这周", "近期", "最近", "本年", "今年");

    /** 经营概览：显式话术（与子串匹配）；成本优先于本条。 */
    private static final String[] BUSINESS_OVERVIEW_PHRASES = {
            "这个月经营怎么样",
            "本月经营怎么样",
            "这个月生意怎么样",
            "本月生意怎么样",
            "最近生意怎么样",
            "本月营业情况怎么样",
            "本月经营情况怎么样",
            "经营情况怎么样",
            // 以下为历史触达话术保留
            "最近经营情况怎么样",
            "这个月营业额怎么样",
            "帮我看一下本月经营",
            "帮我看下本月经营",
            "看下本月经营",
            "看一下本月经营",
    };

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

        boolean inBusinessChat = state.getWorkspaceMode() == AiWorkspaceMode.BUSINESS_CHAT;
        String q = state.getNormalizedUserInput() != null ? state.getNormalizedUserInput().trim() : "";

        var rCtx = state.getResolvedQueryContext();
        var rqi = rCtx != null ? rCtx.getQueryIntent() : null;
        var prevTurn = rCtx != null ? rCtx.getPreviousTurn() : null;
        var followUpRes = rCtx != null ? rCtx.getFollowUpResolution() : null;

        boolean resolvedPurchaseOverview =
                rqi != null && AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(rqi.getPathCode());
        if (!resolvedPurchaseOverview && prevTurn != null
                && AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(prevTurn.getLastPathCode())
                && !FollowUpIntentResolveService.currentMessageDeclaresDomainPath(q)
                && (FollowUpIntentResolveService.isShortTemporalFollowUp(q)
                || (followUpRes != null && followUpRes.isFollowUp()
                && "TIME_SHIFT".equals(followUpRes.getFollowUpType())))) {
            patchPurchaseIntentFromPreviousTurn(rqi, prevTurn);
            resolvedPurchaseOverview = true;
        }

        boolean resolvedDishProfit = rqi != null && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(rqi.getPathCode());
        if (!resolvedDishProfit && rqi != null && prevTurn != null
                && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(prevTurn.getLastPathCode())
                && !FollowUpIntentResolveService.currentMessageDeclaresDomainPath(q)
                && AiQuerySemanticLexicon.dishProfitStructuredIntentFromUtterance(q)) {
            patchDishProfitIntentFromPreviousTurn(rqi, prevTurn, q);
            resolvedDishProfit = true;
        }
        boolean resolvedBusinessOverview =
                rqi != null && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(rqi.getPathCode());
        if (!resolvedBusinessOverview && prevTurn != null
                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(prevTurn.getLastPathCode())
                && !FollowUpIntentResolveService.currentMessageDeclaresDomainPath(q)
                && (FollowUpIntentResolveService.isShortTemporalFollowUp(q)
                || (followUpRes != null && followUpRes.isFollowUp()
                && "TIME_SHIFT".equals(followUpRes.getFollowUpType())))) {
            patchBusinessOverviewFromPreviousTurn(rqi, prevTurn);
            resolvedBusinessOverview = true;
        }
        boolean resolvedWarehouse =
                rqi != null && AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(rqi.getPathCode());

        resetCostIntentFlags(state);

        boolean resolvedStockReduce =
                rqi != null && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(rqi.getPathCode());
        if (!resolvedStockReduce && prevTurn != null
                && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(prevTurn.getLastPathCode())
                && !FollowUpIntentResolveService.currentMessageDeclaresDomainPath(q)
                && (FollowUpIntentResolveService.isShortTemporalFollowUp(q)
                || (followUpRes != null && followUpRes.isFollowUp()
                && "TIME_SHIFT".equals(followUpRes.getFollowUpType())))) {
            patchStockReduceIntentFromPreviousTurn(rqi, prevTurn);
            resolvedStockReduce = true;
        }

        if (inBusinessChat && FollowUpIntentResolveService.isShortTemporalFollowUp(q)) {
            String curPath = rqi != null ? rqi.getPathCode() : null;
            // isShortTemporalFollowUp 对「这个月自采金额是多少」也为 true（句中含时间词），
            // 不能以「没有上一轮」为由误判为追问缺上下文；仅当当前轮根本解不出 path 时才澄清。
            if (!StringUtils.hasText(curPath)) {
                boolean standaloneDomainCue = looksLikePurchaseOverviewOnly(q)
                        || AiQuerySemanticLexicon.looksPurchaseDomainShortQuestion(q)
                        || looksLikeDishProfitInsight(q)
                        || looksLikeBusinessOverview(q)
                        || looksLikeWarehouseStockOverviewQuestion(q)
                        || AiResolvedQueryIntent.messageDeclaresStandaloneStockReduce(q)
                        || looksLikeCostInsight(q);
                if (!standaloneDomainCue) {
                    boolean prevHasPath = prevTurn != null
                            && StringUtils.hasText(prevTurn.getLastPathCode());
                    state.setNeedClarification(true);
                    state.setClarificationQuestion(prevHasPath
                            ? "暂时没能接续上一轮的查询类型，请把要查的内容用一句话说全（需带业务口径，例如自采金额、采购或经营）。"
                            : "上次聊的是哪一类数据？请用完整一句话说明，例如「上个月自采金额是多少」或「上月经营情况」。");
                }
            }
        }

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

        boolean dishProfitIntent = inBusinessChat && (resolvedDishProfit || looksLikeDishProfitInsight(q));
        boolean stockReduceStandaloneIntent = !dishProfitIntent && inBusinessChat
                && (resolvedStockReduce || AiResolvedQueryIntent.messageDeclaresStandaloneStockReduce(q));
        boolean purchaseOverviewOnlyIntent = !dishProfitIntent && !stockReduceStandaloneIntent && inBusinessChat
                && (resolvedPurchaseOverview || looksLikePurchaseOverviewOnly(q)
                || AiQuerySemanticLexicon.looksPurchaseDomainShortQuestion(q));
        boolean rawCostIntent = !dishProfitIntent && !stockReduceStandaloneIntent && !purchaseOverviewOnlyIntent
                && inBusinessChat && looksLikeCostInsight(q);
        boolean stockOverviewIntent = !dishProfitIntent && !stockReduceStandaloneIntent && !rawCostIntent
                && !purchaseOverviewOnlyIntent
                && inBusinessChat && (resolvedWarehouse || looksLikeWarehouseStockOverviewQuestion(q));
        boolean overviewIntent = !dishProfitIntent && !stockReduceStandaloneIntent && !rawCostIntent
                && !stockOverviewIntent
                && !purchaseOverviewOnlyIntent && inBusinessChat
                && (resolvedBusinessOverview || looksLikeBusinessOverview(q));

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
            state.setDataPlanTools(new ArrayList<>());
            plan = List.of();
        } else if (dishProfitIntent) {
            state.setCostInsightPath(false);
            state.setPurchaseCostInsightPath(false);
            state.setWarehouseStockOverviewPath(false);
            state.setGroupWarehouseStockOverview(false);
            state.setPurchaseOverviewPath(false);
            state.setGroupPurchaseOverview(false);
            state.setStockReduceQueryPath(false);
            state.setGroupStockReduceQuery(false);
            state.setCouponCostInsightBlocked(false);
            state.setBusinessOverviewPath(false);
            state.setDishProfitPath(true);
            plan = new ArrayList<>(AiBusinessToolIds.DEFAULT_DISH_PROFIT_TOOLS);
            state.setDataPlanTools(plan);
        } else if (stockReduceStandaloneIntent) {
            applyStockReduceQuestionBranch(state);
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
                plan = new ArrayList<>(AiBusinessToolIds.DEFAULT_BUSINESS_OVERVIEW_TOOLS);
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
        payload.put("stockReduceQueryPath", state.isStockReduceQueryPath());
        payload.put("groupStockReduceQuery", state.isGroupStockReduceQuery());
        payload.put("tools", plan == null ? List.of() : plan);
        if (state.getIntentConvergence() != null && !state.getIntentConvergence().isEmpty()) {
            payload.put("intentConvergence", state.getIntentConvergence());
        }

        String displayText;
        if (state.isCouponCostInsightBlocked()) {
            displayText = "成本分析对该账号不可用，已跳过数据拉取";
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
            displayText = "已编排「经营概览」链路 " + plan.size() + " 个数据来源";
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

    /** 时间追问兜底：解析阶段若未合并成功，从 TurnMemory 恢复采购 path / 子意图 / 来源，避免只更新时间不落工具链。 */
    private static void patchPurchaseIntentFromPreviousTurn(AiResolvedQueryIntent rqi, AiConversationTurnMemory prev) {
        if (rqi == null || prev == null) {
            return;
        }
        rqi.setPathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW);
        rqi.setIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW);
        if (org.springframework.util.StringUtils.hasText(prev.getLastStructuredIntentDetail())) {
            rqi.setStructuredIntentDetail(prev.getLastStructuredIntentDetail());
        }
        if (org.springframework.util.StringUtils.hasText(prev.getLastPurchaseSourceType())) {
            rqi.setPurchaseSourceType(prev.getLastPurchaseSourceType());
        }
        rqi.setInheritedFromPreviousTurn(true);
    }

    /** 时间追问兜底：与采购 patch 对称，避免「上个月呢？」仅命中时间短句但 path 未并入时落入 DataPlanner 澄清。 */
    private static void patchBusinessOverviewFromPreviousTurn(AiResolvedQueryIntent rqi, AiConversationTurnMemory prev) {
        if (rqi == null || prev == null) {
            return;
        }
        rqi.setPathCode(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);
        rqi.setIntentCode(AiResolvedQueryIntent.BUSINESS_OVERVIEW);
        rqi.setInheritedFromPreviousTurn(true);
    }

    private static void patchStockReduceIntentFromPreviousTurn(AiResolvedQueryIntent rqi, AiConversationTurnMemory prev) {
        if (rqi == null || prev == null) {
            return;
        }
        rqi.setPathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        rqi.setIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY);
        if (StringUtils.hasText(prev.getLastStructuredIntentDetail())) {
            rqi.setStructuredIntentDetail(prev.getLastStructuredIntentDetail());
        }
        rqi.setInheritedFromPreviousTurn(true);
    }

    /** 与采购 patch 对称：「出库成本多少」等子口径若解析阶段未并入 path，从 TurnMemory 续上菜品毛利链。 */
    private static void patchDishProfitIntentFromPreviousTurn(
            AiResolvedQueryIntent rqi, AiConversationTurnMemory prev, String normalizedQuestion) {
        if (rqi == null || prev == null) {
            return;
        }
        rqi.setPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT);
        rqi.setIntentCode(AiResolvedQueryIntent.DISH_PROFIT);
        AiQuerySemanticLexicon.mergeDishProfitCuesInto(rqi, normalizedQuestion != null ? normalizedQuestion : "");
        rqi.setInheritedFromPreviousTurn(true);
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
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setDishProfitPath(false);
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

    /**
     * 「这个月采购怎么样」类问句：优先于成本主链；排除含成本/毛利/核销/出库等更广诊断话术。
     */
    private static boolean looksLikePurchaseOverviewOnly(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        String s = q.replace(" ", "");
        boolean purchaseCue = s.contains("采购") || s.contains("进货") || s.contains("订货");
        if (!purchaseCue) {
            return false;
        }
        if (s.contains("成本") || s.contains("毛利") || s.contains("损耗")
                || s.contains("核销") || s.contains("出库")) {
            return false;
        }
        return s.contains("怎么样") || s.contains("多少") || s.contains("如何")
                || s.contains("情况") || s.contains("分析") || s.contains("概况");
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
        List<String> tools;
        if (CostInsightIntentConvergence.isProcurementCostConvergenceRole(roleCode)) {
            tools = new ArrayList<>(CostInsightIntentConvergence.buildPurchaseCostInsightTools(perms));
        } else {
            tools = new ArrayList<>();
            tools.add(AiBusinessToolIds.PURCHASE_OVERVIEW);
            if (perms.contains(AiPermissions.VIEW_STOCK)) {
                tools.add(AiBusinessToolIds.STOCK_REDUCE_QUERY);
            }
        }

        if (tools.isEmpty()) {
            state.getPermissionDenials().add(AiAnswerBoundary.forMissingToolPermission(
                    "purchase_overview", AiPermissions.VIEW_PURCHASE));
            return;
        }

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

    private static boolean looksLikeWarehouseStockOverviewQuestion(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        String s = q.replace(" ", "");
        if (!s.contains("库存")) {
            return false;
        }
        return s.contains("怎么样") || s.contains("如何") || s.contains("好不好") || s.contains("还行")
                || s.contains("多少") || s.contains("情况") || s.contains("概况") || s.contains("状态")
                || s.contains("还剩") || s.contains("还多") || s.contains("怎样") || s.contains("咋样")
                || s.contains("正常吗") || s.contains("行吗");
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

    private static void applyFullCostInsightPath(AiRunState state) {
        state.setCostInsightPath(true);
        state.setPurchaseCostInsightPath(false);
        state.setPurchaseOverviewPath(false);
        state.setGroupPurchaseOverview(false);
        state.setWarehouseStockOverviewPath(false);
        state.setStockReduceQueryPath(false);
        state.setGroupStockReduceQuery(false);
        state.setCouponCostInsightBlocked(false);
        state.setDataPlanTools(new ArrayList<>(AiBusinessToolIds.DEFAULT_COST_INSIGHT_TOOLS));
    }

    /**
     * 菜品毛利/经营透视问法；优先于 {@link #looksLikeCostInsight}，避免泛泛「毛利」吃进成本主线。
     */
    private static boolean looksLikeDishProfitInsight(String q) {
        if (q == null || q.isEmpty()) {
            return false;
        }
        String s = q.replace(" ", "");
        // 水煮鱼毛利 / ××毛利率怎么样（排除「本月毛利」等整体经营毛利泛问）
        if (SINGLE_DISH_MARGIN.matcher(s).find()) {
            return singleDishMarginHasNamedDishCue(s);
        }
        if (s.contains("菜品")) {
            if (s.contains("毛利") || s.contains("毛利率") || s.contains("利润")) {
                return true;
            }
            if (s.contains("赚钱") || s.contains("不赚钱") || s.contains("亏本") || s.contains("亏钱")) {
                return true;
            }
            if (s.contains("分析")) {
                return true;
            }
        }
        boolean dishQuestion = s.contains("哪些菜") || s.contains("什么菜") || s.contains("哪道菜") || s.contains("哪个菜");
        if (dishQuestion && (s.contains("赚钱") || s.contains("毛利") || s.contains("利润") || s.contains("不赚钱")
                || s.contains("亏钱") || s.contains("亏本"))) {
            return true;
        }
        boolean profitCue = s.contains("利润") || s.contains("盈利") || s.contains("盈亏");
        if (profitCue) {
            if (s.contains("采购") || s.contains("进货") || s.contains("订货")
                    || s.contains("供货商") || s.contains("供应商") || s.contains("入库")) {
                return false;
            }
            return s.contains("怎么样") || s.contains("如何") || s.contains("多少") || s.contains("呢") || s.contains("情况");
        }
        if (s.contains("毛利") && (s.contains("怎么样") || s.contains("如何") || s.contains("多少") || s.contains("呢"))) {
            if (s.contains("采购") || s.contains("进货") || s.contains("订货")
                    || s.contains("供货商") || s.contains("供应商") || s.contains("入库")) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * {@link #SINGLE_DISH_MARGIN} 也会命中「本月毛利」：前缀仅为时间泛词时视为整体毛利问法，交给成本链。
     */
    private static boolean singleDishMarginHasNamedDishCue(String s) {
        Matcher sm = SINGLE_DISH_MARGIN.matcher(s);
        while (sm.find()) {
            String full = sm.group(0);
            String before = full.replaceFirst("(毛利率|毛利)$", "");
            if (!GENERIC_MARGIN_TIME_PREFIXES.contains(before)) {
                return true;
            }
        }
        return false;
    }

    /** 命中则走成本主链；纯出库/结构化核销分型、出库专线已先分流（勿把「损耗多少」等误判为成本洞察）。 */
    private static boolean looksLikeCostInsight(String q) {
        if (q == null || q.isEmpty()) {
            return false;
        }
        String c = q.replace(" ", "");
        if (AiQuerySemanticLexicon.mapsToStructuredStockReduceDetailCue(q)) {
            return false;
        }
        // 生产成本/制作成本类：出库 type1 专线，不是泛成本诊断
        if (c.contains("生产成本") || c.contains("成本耗用") || c.contains("生产耗用")
                || c.contains("制作成本") || c.contains("制作消耗") || c.contains("做菜成本")
                || c.contains("菜品制作消耗") || c.contains("正常制作消耗")) {
            return false;
        }
        return q.contains("成本")
                || q.contains("毛利")
                || q.contains("采购")
                || q.contains("食材");
    }

    private static boolean looksLikeBusinessOverview(String q) {
        if (q.isEmpty()) {
            return false;
        }
        String compact = q.replace(" ", "").toLowerCase(Locale.ROOT);
        for (String p : BUSINESS_OVERVIEW_PHRASES) {
            if (compact.contains(p.replace(" ", ""))) {
                return true;
            }
        }
        // 宽泛：营业额/流水/经营状况/概况 + 「怎么样」「如何」（成本关键词已排除）
        if ((compact.contains("营业额") || compact.contains("流水") || compact.contains("经营状况")
                || compact.contains("经营概况")) && (compact.contains("怎么样") || compact.contains("如何"))) {
            return !looksLikeCostInsight(q);
        }
        // 「经营怎么样 / 经营状况怎么样 / …」（成本关键词已排除）
        if ((compact.contains("经营怎么样") || compact.contains("经营状况怎么样")
                || compact.contains("经营情况怎么样") || compact.contains("营业情况怎么样"))
                && (compact.contains("怎么样") || compact.contains("如何"))) {
            return !looksLikeCostInsight(q);
        }
        if ((compact.contains("生意怎么样") || compact.contains("生意如何")) && !looksLikeCostInsight(q)) {
            return true;
        }
        return false;
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
