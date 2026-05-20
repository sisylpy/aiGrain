package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DiagnosisPlan;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** AnswerPlan/经营诊断 harness 出现过的菜名（逗号分隔），多轮点名菜与门店收窄对齐用；与 {@link #lastFocusType} 独立。 */
    private String lastFocusName;

    /** 上一轮 Run 结束时 {@link AiResolvedQueryContext#getEffectiveScopeSource()}，便于多轮收窄诊断 */
    private String lastEffectiveScopeSource;

    /** 与 {@link com.nongxinle.ai.followup.AiFollowUpIntentSnapshot#getEffectiveQuestion()} 对齐，供时间追问 splice */
    private String lastEffectiveQuestion;
    /** 可选：极短摘要供日志 */
    private String lastAnswerSummary;
    /** 本轮工具链执行情况简述（如 dish_profit_analysis:ok），供回放与 Harness 日志 */
    private String lastToolSummary;
    /** 上轮用户焦点/语境中的「点名门店」（如单店可见时的店名）；集团多店常为 null */
    private String lastMentionedStore;
    /** 菜品毛利追问：点名的菜名，用于多轮「实际成本呢」继承。 */
    private String lastMentionedDishName;

    /**
     * Harness / 解析多店对比对齐出的店名（≥2）；持久化时嵌入 {@link #lastToolSummary} 前缀
     * {@code harness_ms_json=[...]|}。
     */
    private List<String> lastHarnessMultiStoreMatchedStores;

    /**
     * 上一轮 AnswerPlan 产生的可追问锚点；跨 HTTP 请求依赖 {@link #lastToolSummary} 前缀 {@code nx_ctm_ra_json=} 写入 DB，
     * 见 {@link AiConversationTurnMemoryEntities#toEntity} / {@link #readResultAnchorsFromToolSummary}。
     */
    private List<AiResultAnchor> lastResultAnchors;

    /**
     * 上一轮语义槽位（D-13）；跨请求经 {@link #lastToolSummary} 前缀 {@code nx_ctm_ss_json=} 持久化。
     */
    private AiQuerySemanticParseResult.SemanticSlotsPart lastSemanticSlots;

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

        String dishForMemory = trimSummary(ctx != null ? ctx.getMentionedDishName() : null);
        if (!StringUtils.hasText(dishForMemory) && ctx != null) {
            var qiRank = ctx.getQueryIntent();
            String sw = qiRank != null
                    ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qiRank.getStructuredIntentDetail())
                    : null;
            if (AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(sw)) {
                AiConversationTurnMemory p = ctx.getPreviousTurn();
                if (p != null && StringUtils.hasText(p.getLastMentionedDishName())) {
                    dishForMemory = trimSummary(p.getLastMentionedDishName());
                }
            }
        }
        if (!StringUtils.hasText(dishForMemory) && state.getDishProfitAnswerPlan() != null) {
            List<AiResultAnchor> das = state.getDishProfitAnswerPlan().getResultAnchors();
            if (das != null) {
                for (AiResultAnchor a : das) {
                    if (a != null
                            && AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(
                                    StringUtils.hasText(a.getEntityType()) ? a.getEntityType().trim() : "")
                            && StringUtils.hasText(a.getEntityName())) {
                        dishForMemory = trimSummary(a.getEntityName());
                        break;
                    }
                }
            }
        }

        AiQuerySemanticParseResult.SemanticSlotsPart lastSlots = null;
        if (ctx != null && ctx.getQuerySemanticParse() != null) {
            lastSlots = ctx.getQuerySemanticParse().getSemanticSlots();
        }
        lastSlots = AiQuerySemanticSlotMerge.alignSemanticSlotsForTurnMemoryPersistence(lastSlots, structured);

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
                .lastFocusName(trimSummary(buildHarnessDishRosterSnapshot(state)))
                .lastEffectiveScopeSource(ctx != null ? ctx.getEffectiveScopeSource() : null)
                .lastEffectiveQuestion(effectiveQ != null ? effectiveQ.trim() : null)
                .lastAnswerSummary(trimSummary(state.getFinalAnswerText()))
                .lastToolSummary(trimToolSummary(
                        maybePrefixPurchaseAllSourceCarryStats(state, summarizeToolChain(state))))
                .lastMentionedStore(trimSummary(resolveMentionedStoreDisplay(ctx)))
                .lastMentionedDishName(dishForMemory)
                .lastHarnessMultiStoreMatchedStores(copyHarnessStoreList(ctx.getHarnessMultiStoreMatchedStores()))
                .lastResultAnchors(copyAnchorsFromCompletedPlans(state))
                .lastSemanticSlots(lastSlots)
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

        AiQuerySemanticParseResult.SemanticSlotsPart replaySlots =
                ctx.getQuerySemanticParse() != null ? ctx.getQuerySemanticParse().getSemanticSlots() : null;
        replaySlots =
                AiQuerySemanticSlotMerge.alignSemanticSlotsForTurnMemoryPersistence(replaySlots, structured);

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
                .lastHarnessMultiStoreMatchedStores(copyHarnessStoreList(ctx.getHarnessMultiStoreMatchedStores()))
                .lastResultAnchors(null)
                .lastSemanticSlots(replaySlots)
                .build();
    }

    private static List<AiResultAnchor> copyAnchorsFromCompletedPlans(AiRunState state) {
        if (state == null) {
            return null;
        }
        List<AiResultAnchor> merged = new ArrayList<>();
        DiagnosisPlan dp = state.getDiagnosisPlan();
        if (dp != null && dp.getResultAnchors() != null) {
            for (AiResultAnchor a : dp.getResultAnchors()) {
                if (a != null) {
                    merged.add(a);
                }
            }
        }
        if (state.getPurchaseAnswerPlan() != null && state.getPurchaseAnswerPlan().getResultAnchors() != null) {
            for (AiResultAnchor a : state.getPurchaseAnswerPlan().getResultAnchors()) {
                if (a != null) {
                    merged.add(a);
                }
            }
        }
        if (state.getDishProfitAnswerPlan() != null && state.getDishProfitAnswerPlan().getResultAnchors() != null) {
            for (AiResultAnchor a : state.getDishProfitAnswerPlan().getResultAnchors()) {
                if (a != null) {
                    merged.add(a);
                }
            }
        }
        return merged.isEmpty() ? null : merged;
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
        int cnt = intHintFlexible(po.get("purchaseOrderCount"));
        String amtToken = normalizedAmountCarryToken(po.get("totalPurchaseAmount"));
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

    public static String embedHarnessMultiStoreInToolSummary(String existing, List<String> names) {
        if (names == null || names.isEmpty()) {
            return existing;
        }
        String json = JSON.toJSONString(names);
        return "harness_ms_json=" + json + "|" + (existing != null ? existing : "");
    }

    /** 会话记忆落库：前缀序列化 {@link #lastResultAnchors}，与 harness 前缀可共存。 */
    public static String embedResultAnchorsInToolSummary(String existing, List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return existing;
        }
        String json = JSON.toJSONString(anchors);
        return "nx_ctm_ra_json=" + json + "|" + (existing != null ? existing : "");
    }

    /** 会话记忆落库：前缀序列化上一轮 {@link #lastSemanticSlots}。 */
    public static String embedSemanticSlotsInToolSummary(
            String existing, AiQuerySemanticParseResult.SemanticSlotsPart slots) {
        if (slots == null) {
            return existing;
        }
        String json = JSON.toJSONString(slots);
        return "nx_ctm_ss_json=" + json + "|" + (existing != null ? existing : "");
    }

    public static AiQuerySemanticParseResult.SemanticSlotsPart readSemanticSlotsFromToolSummary(String toolSummary) {
        if (!StringUtils.hasText(toolSummary)) {
            return null;
        }
        String rest = toolSummary;
        while (StringUtils.hasText(rest)) {
            int pipe = rest.indexOf('|');
            String seg = pipe >= 0 ? rest.substring(0, pipe) : rest;
            if (seg.startsWith("nx_ctm_ss_json=")) {
                String json = seg.substring("nx_ctm_ss_json=".length());
                try {
                    return JSON.parseObject(json, AiQuerySemanticParseResult.SemanticSlotsPart.class);
                } catch (Exception ignore) {
                    return null;
                }
            }
            if (pipe < 0) {
                break;
            }
            rest = rest.substring(pipe + 1);
        }
        return null;
    }

    public static List<AiResultAnchor> readResultAnchorsFromToolSummary(String toolSummary) {
        if (!StringUtils.hasText(toolSummary)) {
            return null;
        }
        String rest = toolSummary;
        while (StringUtils.hasText(rest)) {
            int pipe = rest.indexOf('|');
            String seg = pipe >= 0 ? rest.substring(0, pipe) : rest;
            if (seg.startsWith("nx_ctm_ra_json=")) {
                String json = seg.substring("nx_ctm_ra_json=".length());
                try {
                    List<AiResultAnchor> list = JSON.parseArray(json, AiResultAnchor.class);
                    return list == null || list.isEmpty() ? null : list;
                } catch (Exception ignore) {
                    return null;
                }
            }
            if (pipe < 0) {
                break;
            }
            rest = rest.substring(pipe + 1);
        }
        return null;
    }

    public static List<String> readHarnessMultiStoreFromToolSummary(String toolSummary) {
        if (!StringUtils.hasText(toolSummary)) {
            return null;
        }
        String s = toolSummary;
        while (StringUtils.hasText(s)) {
            if (s.startsWith("harness_ms_json=")) {
                int pipe = s.indexOf('|');
                int prefixLen = "harness_ms_json=".length();
                if (pipe <= prefixLen) {
                    return null;
                }
                try {
                    String json = s.substring(prefixLen, pipe);
                    List<String> list = JSON.parseArray(json, String.class);
                    return list == null || list.isEmpty() ? null : list;
                } catch (Exception ignore) {
                    return null;
                }
            }
            int pipe = s.indexOf('|');
            if (pipe < 0) {
                return null;
            }
            s = s.substring(pipe + 1);
        }
        return null;
    }

    private static List<String> copyHarnessStoreList(List<String> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        return new ArrayList<>(in);
    }

    private static String trimSummary(String t) {
        if (t == null) {
            return null;
        }
        String s = t.replace('\n', ' ').trim();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static String fallbackPathFromFlags(AiRunState state) {
        if (state.isBusinessDiagnosisPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS;
        }
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
        if (state.isRevenueOverviewPath()) {
            return com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW;
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
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.BUSINESS_DIAGNOSIS;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_DISH_PROFIT ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.DISH_PROFIT;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.BUSINESS_OVERVIEW;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.STOCK_REDUCE_QUERY;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.REVENUE_OVERVIEW;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.PURCHASE_OVERVIEW;
            case com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_COST_DIAGNOSIS ->
                    com.nongxinle.ai.context.AiResolvedQueryIntent.COST_DIAGNOSIS;
            default -> null;
        };
    }

    private static final int MAX_HARNESS_DISH_ROSTER_CHARS = 512;

    private static String buildHarnessDishRosterSnapshot(AiRunState state) {
        if (state == null) {
            return null;
        }
        Set<String> names = new LinkedHashSet<>();
        collectDishNamesFromAnswerPlan(state.getDishProfitAnswerPlan(), names);
        if (names.isEmpty()) {
            return null;
        }
        String joined = String.join(",", names);
        if (joined.length() > MAX_HARNESS_DISH_ROSTER_CHARS) {
            return joined.substring(0, MAX_HARNESS_DISH_ROSTER_CHARS);
        }
        return joined;
    }

    private static void collectDishNamesFromAnswerPlan(DishProfitAnswerPlan ap, Set<String> out) {
        if (ap == null || out == null) {
            return;
        }
        appendAnswerPlanRowDishNames(ap.getFocusRows(), out);
        appendAnswerPlanRowDishNames(ap.getSecondaryRows(), out);
    }

    @SuppressWarnings("unchecked")
    private static void appendAnswerPlanRowDishNames(List<?> rows, Set<String> out) {
        if (rows == null) {
            return;
        }
        for (Object row : rows) {
            if (!(row instanceof Map)) {
                continue;
            }
            Object dn = ((Map<String, Object>) row).get("dishName");
            if (dn != null && StringUtils.hasText(dn.toString())) {
                out.add(dn.toString().trim());
            }
        }
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
