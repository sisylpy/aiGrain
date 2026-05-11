package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 会话级上一轮 Run 的查询语义快照，供 {@link AiFollowUpResolver} 与 {@link AiResolvedQueryContextResolver} 做规则继承。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationTurnMemory {

    private Long conversationId;
    private Long previousRunId;

    private String lastIntentCode;
    private String lastPathCode;
    private String lastStructuredIntentDetail;
    private String lastPurchaseSourceType;

    private String lastStartDate;
    private String lastEndDate;
    private String lastTimeLabel;

    private String lastScopeType;
    private List<Integer> lastVisibleStoreIds;
    private Long lastFocusedStoreId;
    private String lastFocusedStoreName;
    private String lastFocusType;
    private String lastFocusName;

    /** 上一轮 Run 结束时 {@link AiResolvedQueryContext#getEffectiveScopeSource()}，便于多轮收窄诊断 */
    private String lastEffectiveScopeSource;

    /** 与 {@link com.nongxinle.ai.followup.AiFollowUpIntentSnapshot#getEffectiveQuestion()} 对齐，供时间追问 splice */
    private String lastEffectiveQuestion;
    /** 可选：极短摘要供日志 */
    private String lastAnswerSummary;
    /** 本轮工具链执行情况简述（如 dish_sales_query:ok），供回放与 Harness 日志 */
    private String lastToolSummary;
    /** 上轮用户焦点/语境中的「点名门店」（如单店可见时的店名）；集团多店常为 null */
    private String lastMentionedStore;
    /** 菜品毛利追问：点名的菜名，用于多轮「实际成本呢」继承。 */
    private String lastMentionedDishName;

    public static AiConversationTurnMemory fromCompletedState(AiRunState state) {
        if (state == null) {
            return null;
        }
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        String intent = null;
        String path = null;
        String structured = null;
        String purchaseSource = null;
        if (ctx != null && ctx.getQueryIntent() != null) {
            var qi = ctx.getQueryIntent();
            intent = qi.getIntentCode();
            path = qi.getPathCode();
            structured = qi.getStructuredIntentDetail();
            purchaseSource = qi.getPurchaseSourceType();
        }
        if (path == null) {
            path = fallbackPathFromFlags(state);
            intent = fallbackIntentFromPath(path);
        }
        if (path == null) {
            return null;
        }

        List<Integer> storeIds = new ArrayList<>();
        if (ctx != null && ctx.getOrgScope() != null && ctx.getOrgScope().getVisibleStores() != null) {
            for (AiStoreScopeDTO s : ctx.getOrgScope().getVisibleStores()) {
                if (s != null && s.getStoreDepartmentId() != null) {
                    storeIds.add(s.getStoreDepartmentId().intValue());
                }
            }
        }

        String scopeType = ctx != null && ctx.getOrgScope() != null ? ctx.getOrgScope().getScopeType() : null;
        String tlabel = ctx != null && ctx.getTimeWindow() != null ? ctx.getTimeWindow().getTimeLabel() : null;
        var tw = ctx != null ? ctx.getTimeWindow() : null;

        String effectiveQ = state.getNormalizedUserInput();
        if (effectiveQ == null || effectiveQ.isBlank()) {
            effectiveQ = state.getRawUserInput();
        }
        if ((effectiveQ == null || effectiveQ.isBlank()) && ctx != null
                && ctx.getNormalizedQuestion() != null && !ctx.getNormalizedQuestion().isBlank()) {
            effectiveQ = ctx.getNormalizedQuestion();
        }

        return AiConversationTurnMemory.builder()
                .conversationId(state.getConversationId())
                .previousRunId(state.getRunId())
                .lastIntentCode(intent)
                .lastPathCode(path)
                .lastStructuredIntentDetail(structured)
                .lastPurchaseSourceType(purchaseSource)
                .lastStartDate(state.getStatStartDate())
                .lastEndDate(state.getStatEndDate())
                .lastTimeLabel(tlabel)
                .lastScopeType(scopeType)
                .lastVisibleStoreIds(storeIds)
                .lastFocusedStoreId(ctx != null && ctx.getOrgScope() != null
                        ? ctx.getOrgScope().getCurrentStoreDepartmentId() : null)
                .lastFocusedStoreName(resolveFocusedStoreName(ctx != null ? ctx.getOrgScope() : null))
                .lastFocusType(null)
                .lastFocusName(null)
                .lastEffectiveScopeSource(ctx != null ? ctx.getEffectiveScopeSource() : null)
                .lastEffectiveQuestion(effectiveQ != null ? effectiveQ.trim() : null)
                .lastAnswerSummary(trimSummary(state.getFinalAnswerText()))
                .lastToolSummary(trimToolSummary(
                        maybePrefixPurchaseAllSourceCarryStats(state, summarizeToolChain(state))))
                .lastMentionedStore(trimSummary(resolveMentionedStoreDisplay(ctx)))
                .lastMentionedDishName(trimSummary(ctx != null ? ctx.getMentionedDishName() : null))
                .build();
    }

    /**
     * Harness Replay：无完整 Graph 结束时，仅用解析结果写入会话记忆，以便下一轮流式继承。
     */
    public static AiConversationTurnMemory fromHarnessReplayStep(
            AiResolvedQueryContext ctx, Long conversationId, long syntheticRunId) {
        if (ctx == null) {
            return null;
        }
        var qi = ctx.getQueryIntent();
        String intent = StringUtils.hasText(ctx.getEffectiveIntentCode())
                ? ctx.getEffectiveIntentCode()
                : (qi != null ? qi.getIntentCode() : null);
        String path = StringUtils.hasText(ctx.getEffectivePathCode())
                ? ctx.getEffectivePathCode()
                : (qi != null ? qi.getPathCode() : null);
        String structured = qi != null ? qi.getStructuredIntentDetail() : null;
        String purchaseSource = qi != null ? qi.getPurchaseSourceType() : null;

        List<Integer> storeIds = new ArrayList<>();
        if (ctx.getOrgScope() != null && ctx.getOrgScope().getVisibleStores() != null) {
            for (AiStoreScopeDTO s : ctx.getOrgScope().getVisibleStores()) {
                if (s != null && s.getStoreDepartmentId() != null) {
                    storeIds.add(s.getStoreDepartmentId().intValue());
                }
            }
        }

        AiResolvedOrgScope org = ctx.getOrgScope();
        var tw = ctx.getTimeWindow();
        String start = tw != null && tw.getStartDate() != null ? tw.getStartDate().toString() : null;
        String end = tw != null && tw.getEndDate() != null ? tw.getEndDate().toString() : null;
        String tlabel = tw != null ? tw.getTimeLabel() : null;
        String scopeType = org != null ? org.getScopeType() : null;
        String effectiveQ = ctx.getNormalizedQuestion();
        if (!StringUtils.hasText(effectiveQ)) {
            effectiveQ = ctx.getOriginalQuestion();
        }

        return AiConversationTurnMemory.builder()
                .conversationId(conversationId)
                .previousRunId(syntheticRunId)
                .lastIntentCode(intent)
                .lastPathCode(path)
                .lastStructuredIntentDetail(structured)
                .lastPurchaseSourceType(purchaseSource)
                .lastStartDate(start)
                .lastEndDate(end)
                .lastTimeLabel(tlabel)
                .lastScopeType(scopeType)
                .lastVisibleStoreIds(storeIds)
                .lastFocusedStoreId(org != null ? org.getCurrentStoreDepartmentId() : null)
                .lastFocusedStoreName(resolveFocusedStoreName(org))
                .lastEffectiveScopeSource(ctx.getEffectiveScopeSource())
                .lastEffectiveQuestion(effectiveQ != null ? effectiveQ.trim() : null)
                .lastAnswerSummary(null)
                .lastToolSummary("harness_replay:resolver_only")
                .lastMentionedStore(trimSummary(resolveHarnessMentioned(ctx)))
                .lastMentionedDishName(trimSummary(ctx.getMentionedDishName()))
                .build();
    }

    private static String resolveHarnessMentioned(AiResolvedQueryContext ctx) {
        AiFollowUpResolution fur = ctx.getFollowUpResolution();
        if (fur != null && StringUtils.hasText(fur.getStoreScopeFollowUpMentionedName())) {
            return fur.getStoreScopeFollowUpMentionedName().trim();
        }
        return resolveMentionedStoreDisplay(ctx);
    }

    private static String resolveMentionedStoreDisplay(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getOrgScope() == null) {
            return null;
        }
        var org = ctx.getOrgScope();
        var vs = org.getVisibleStores();
        if (vs != null && vs.size() == 1 && vs.get(0) != null) {
            return vs.get(0).getStoreName();
        }
        return null;
    }

    /**
     * 供下一轮流式答复引用「上一轮全来源采购」笔数/金额（仅写入未按自采/供货商过滤的汇总结果），前缀置于 tool 摘要首部以免截断丢失。
     */
    private static String maybePrefixPurchaseAllSourceCarryStats(AiRunState state, String toolChainTail) {
        if (!state.isPurchaseOverviewPath()) {
            return toolChainTail;
        }
        Map<String, Object> po = state.getPurchaseOverview();
        if (!purchaseOverviewIsAllSource(po)) {
            return toolChainTail;
        }
        int cnt = intHintFlexible(po != null ? po.get("purchaseOrderCount") : null);
        String amtToken = normalizedAmountCarryToken(po != null ? po.get("totalPurchaseAmount") : null);
        double amtVal = amtTokenCarryNumeric(amtToken);
        if (cnt <= 0 && amtVal <= 1e-9) {
            return toolChainTail;
        }
        return "carry_po=" + cnt + ",carry_amt=" + amtToken + "|"
                + (toolChainTail != null ? toolChainTail : "");
    }

    private static boolean purchaseOverviewIsAllSource(Map<String, Object> overview) {
        if (overview == null || overview.isEmpty()) {
            return false;
        }
        Object f = overview.get("purchaseSourceFocus");
        if (f == null || f.toString().isBlank()) {
            return true;
        }
        return AiQuerySemanticLexicon.SOURCE_ALL.equals(f.toString().trim());
    }

    private static int intHintFlexible(Object raw) {
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            String s = raw.toString().trim();
            if (s.isEmpty()) {
                return 0;
            }
            int dot = s.indexOf('.');
            if (dot > 0) {
                s = s.substring(0, dot);
            }
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return 0;
        }
    }

    private static String normalizedAmountCarryToken(Object raw) {
        if (raw == null) {
            return "0";
        }
        try {
            if (raw instanceof BigDecimal bd) {
                return bd.stripTrailingZeros().toPlainString();
            }
            if (raw instanceof Number n) {
                return BigDecimal.valueOf(n.doubleValue()).stripTrailingZeros().toPlainString();
            }
            return new BigDecimal(raw.toString().trim().replace(",", "")).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return raw.toString().trim();
        }
    }

    private static double amtTokenCarryNumeric(String token) {
        if (token == null || token.isBlank()) {
            return 0d;
        }
        try {
            return Double.parseDouble(token);
        } catch (Exception e) {
            return 0d;
        }
    }

    /** 形如 toolId:ok;toolId:fail，截断在安全长度 */
    private static String summarizeToolChain(AiRunState state) {
        if (state == null || state.getDataPlanTools() == null || state.getDataPlanTools().isEmpty()) {
            return null;
        }
        Map<String, Object> results = state.getToolResults();
        StringBuilder sb = new StringBuilder();
        for (String id : state.getDataPlanTools()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(id.trim()).append(':').append(toolResultVerb(id.trim(), results));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String toolResultVerb(String toolId, Map<String, Object> results) {
        if (results == null || !results.containsKey(toolId)) {
            return "?";
        }
        Object tr = results.get(toolId);
        if (!(tr instanceof Map<?, ?> map)) {
            return "ok";
        }
        Object s = map.get("success");
        if (Boolean.TRUE.equals(s)) {
            return "ok";
        }
        if (Boolean.FALSE.equals(s)) {
            return "fail";
        }
        return "ok";
    }

    private static String trimToolSummary(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() > 380 ? raw.substring(0, 380) + "…" : raw;
    }

    private static String resolveFocusedStoreName(AiResolvedOrgScope org) {
        if (org == null || org.getVisibleStores() == null || org.getVisibleStores().size() != 1) {
            return null;
        }
        AiStoreScopeDTO s = org.getVisibleStores().get(0);
        return s != null ? s.getStoreName() : null;
    }

    private static String trimSummary(String t) {
        if (t == null) {
            return null;
        }
        String s = t.replace('\n', ' ').trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static String fallbackPathFromFlags(AiRunState state) {
        if (state.isDishProfitPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_DISH_PROFIT;
        }
        if (state.isBusinessOverviewPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW;
        }
        if (state.isWarehouseStockOverviewPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK;
        }
        if (state.isStockReduceQueryPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY;
        }
        if (state.isPurchaseOverviewPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW;
        }
        if (state.isCostInsightPath() || state.isPurchaseCostInsightPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_COST_DIAGNOSIS;
        }
        return null;
    }

    private static String fallbackIntentFromPath(String path) {
        if (path == null) {
            return null;
        }
        return switch (path) {
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_DISH_PROFIT ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.DISH_PROFIT;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.BUSINESS_OVERVIEW;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.STOCK_REDUCE_QUERY;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.PURCHASE_OVERVIEW;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_COST_DIAGNOSIS ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.COST_DIAGNOSIS;
            default -> null;
        };
    }

    /** 用于范围追问：当前可见门店「名称」列表（去空） */
    public List<String> visibleStoreNamesExcludingBlank() {
        if (lastVisibleStoreIds == null || lastVisibleStoreIds.isEmpty()) {
            return List.of();
        }
        // 名称在记忆中未存全量时返回空；追问依赖 orgScope 重建后的 visibleStores
        return List.of();
    }

    @Override
    public String toString() {
        return "TurnMemory{path=" + lastPathCode + ",intent=" + lastIntentCode + ",stores="
                + (lastVisibleStoreIds == null ? 0 : lastVisibleStoreIds.size()) + "}";
    }
}
