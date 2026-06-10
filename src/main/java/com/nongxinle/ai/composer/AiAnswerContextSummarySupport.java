package com.nongxinle.ai.composer;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaselineSupport;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.history.dto.AiConversationMessageDTO;
import com.nongxinle.ai.resolver.AiMultiTurnOrgScopePolicy;
import com.nongxinle.ai.security.AiAnswerBoundary;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.trace.AiRunSessionRegistry;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将 Composer 层上下文说明与用户可见正文分离：结构化 {@code contextSummary} 供前端 context bar，
 * 完整前言保留在 {@link AiRunState#getAnswerContextPreambleDebug()} 供 Harness 复盘。
 */
public final class AiAnswerContextSummarySupport {

    private static final DateTimeFormatter MD_FMT = DateTimeFormatter.ofPattern("MM/dd");

    private static final List<String> PERSISTENCE_FIELD_KEYS = List.of(
            "contextBar",
            "storeText",
            "timeText",
            "dateRangeText",
            "scopeText",
            "permissionScopeText",
            "noticeText");

    private AiAnswerContextSummarySupport() {
    }

    /**
     * @param boundaryNote 已 refine 的 answerBoundaryNote（可为空）
     * @param scopePrefix  {@link AiAnswerBoundary#scopeConvergencePrefix(String)} 结果
     * @param intentPrefix {@link AiAnswerBoundary#costIntentConvergencePrefix(String)} 结果
     * @param permissionPrefix {@link AiAnswerBoundary#composeHumanPrefix(java.util.List)} 结果
     */
    public static void captureComposerContext(
            AiRunState state,
            String boundaryNote,
            String scopePrefix,
            String intentPrefix,
            String permissionPrefix) {
        if (state == null) {
            return;
        }
        String preamble = joinPreamble(boundaryNote, scopePrefix, intentPrefix, permissionPrefix);
        state.setAnswerContextPreambleDebug(StringUtils.hasText(preamble) ? preamble : null);
        state.setAnswerContextSummary(buildUserSummary(state, boundaryNote, scopePrefix, permissionPrefix));
    }

    public static void appendToEnvelope(Map<String, Object> envelope, AiRunState state) {
        if (envelope == null || state == null) {
            return;
        }
        Map<String, Object> summary = state.getAnswerContextSummary();
        if (summary == null || summary.isEmpty()) {
            return;
        }
        envelope.put("contextSummary", summary);
    }

    /** 落库前仅保留用户可见字段；无有效字段时返回 null。 */
    public static Map<String, Object> sanitizeForUserPersistence(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (String key : PERSISTENCE_FIELD_KEYS) {
            Object v = raw.get(key);
            if (v instanceof String s && StringUtils.hasText(s)) {
                out.put(key, s.trim());
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** 将 {@code contextSummary} 序列化为 {@code gb_ai_message_context_summary_json}。 */
    public static String serializeForPersistence(Map<String, Object> summary) {
        Map<String, Object> sanitized = sanitizeForUserPersistence(summary);
        if (sanitized == null) {
            return null;
        }
        try {
            return JSON.toJSONString(sanitized);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 从 {@code gb_ai_message_context_summary_json} 反序列化；非法 JSON 返回 null。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseFromPersistence(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Object parsed = JSON.parse(json);
            if (!(parsed instanceof Map<?, ?> map) || map.isEmpty()) {
                return null;
            }
            return sanitizeForUserPersistence((Map<String, Object>) map);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static void hydrateMessageFromPersistence(AiConversationMessageDTO message, String json) {
        if (message == null) {
            return;
        }
        Map<String, Object> summary = parseFromPersistence(json);
        if (summary != null && !summary.isEmpty()) {
            message.setContextSummary(summary);
        }
    }

    /** Run Session 仍驻内存时，从 {@link AiRunState#getAnswerContextSummary()} 回填历史消息。 */
    public static void hydrateMessageFromRunSession(
            AiConversationMessageDTO message,
            AiRunSessionRegistry sessionRegistry,
            Long runId) {
        if (message == null || sessionRegistry == null || runId == null) {
            return;
        }
        if (message.getContextSummary() != null && !message.getContextSummary().isEmpty()) {
            return;
        }
        sessionRegistry.get(runId).ifPresent(session -> {
            AiRunState st = session.getState();
            if (st == null) {
                return;
            }
            Map<String, Object> summary = sanitizeForUserPersistence(st.getAnswerContextSummary());
            if (summary != null && !summary.isEmpty()) {
                message.setContextSummary(summary);
            }
        });
    }

    private static Map<String, Object> buildUserSummary(
            AiRunState state,
            String boundaryNote,
            String scopePrefix,
            String permissionPrefix) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        AiResolvedQueryContext ctx = state.getResolvedQueryContext();

        String storeText = resolveStoreText(ctx);
        String scopeText = resolveScopeText(ctx);
        String permissionScopeText = resolvePermissionScopeText(state.getScopeConvergenceNote(), scopePrefix, scopeText);
        resolveTimeFields(ctx, state, out);

        out.put("storeText", blankToNull(storeText));
        out.put("scopeText", blankToNull(scopeText));
        out.put("timeText", out.get("timeText"));
        out.put("dateRangeText", out.get("dateRangeText"));
        out.put("permissionScopeText", blankToNull(permissionScopeText));
        out.put("noticeText", blankToNull(resolveNoticeText(boundaryNote, permissionPrefix, state.getPermissionDenials())));

        String contextBar = composeContextBar(
                storeText,
                stringVal(out.get("timeText")),
                stringVal(out.get("dateRangeText")),
                permissionScopeText,
                scopeText);
        out.put("contextBar", blankToNull(contextBar));
        return out;
    }

    private static String resolveStoreText(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return null;
        }
        AiResolvedOrgScope org = ctx.getOrgScope();
        Optional<String> one = AiMultiTurnOrgScopePolicy.singleVisibleStoreName(org);
        if (one.isPresent()) {
            return one.get();
        }
        if (org != null && StringUtils.hasText(org.getScopeName())) {
            return org.getScopeName().trim();
        }
        if (org != null && org.getVisibleStores() != null && !org.getVisibleStores().isEmpty()) {
            AiStoreScopeDTO first = org.getVisibleStores().get(0);
            if (first != null && StringUtils.hasText(first.getStoreName())) {
                int n = org.getVisibleStores().size();
                if (n <= 1) {
                    return first.getStoreName().trim();
                }
                return first.getStoreName().trim() + "等" + n + "店";
            }
        }
        if (StringUtils.hasText(ctx.getQueryScopeBanner())) {
            return ctx.getQueryScopeBanner().trim();
        }
        return null;
    }

    private static String resolveScopeText(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getOrgScope() == null) {
            return null;
        }
        AiResolvedOrgScope org = ctx.getOrgScope();
        String type = org.getScopeType();
        if (AiResolvedOrgScope.SCOPE_GROUP.equals(type)) {
            return "集团";
        }
        if (AiResolvedOrgScope.SCOPE_STORE.equals(type)) {
            return "本门店";
        }
        if (AiResolvedOrgScope.SCOPE_REGION.equals(type)) {
            return "本区域";
        }
        if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(type)) {
            return "本库房";
        }
        if (AiResolvedOrgScope.SCOPE_PURCHASER.equals(type)) {
            return "采购视角";
        }
        if (StringUtils.hasText(org.getQueryScopeBanner())) {
            String banner = org.getQueryScopeBanner().trim();
            if (banner.length() <= 20) {
                return banner;
            }
        }
        return null;
    }

    private static String resolvePermissionScopeText(
            String scopeConvergenceNote, String scopePrefix, String scopeTextFallback) {
        if (StringUtils.hasText(scopeConvergenceNote)) {
            String note = scopeConvergenceNote.trim();
            if (note.equals(AiAnswerBoundary.SCOPE_CLAMP_STORE_FRONT)) {
                return "本门店";
            }
            if (note.equals(AiAnswerBoundary.SCOPE_CLAMP_REGION_FRONT)) {
                return "本区域";
            }
            if (note.equals(AiAnswerBoundary.SCOPE_CLAMP_PROCUREMENT_FRONT)) {
                return "采购视角";
            }
        }
        if (StringUtils.hasText(scopePrefix)) {
            String body = scopePrefix.trim();
            while (body.startsWith("【查询范围】")) {
                body = body.substring("【查询范围】".length()).trim();
            }
            if (body.equals(AiAnswerBoundary.SCOPE_CLAMP_STORE_FRONT)) {
                return "本门店";
            }
            if (body.equals(AiAnswerBoundary.SCOPE_CLAMP_REGION_FRONT)) {
                return "本区域";
            }
            if (body.equals(AiAnswerBoundary.SCOPE_CLAMP_PROCUREMENT_FRONT)) {
                return "采购视角";
            }
        }
        return scopeTextFallback;
    }

    private static void resolveTimeFields(AiResolvedQueryContext ctx, AiRunState state, Map<String, Object> out) {
        if (ToolRequestContractExecutionParamSupport.isInventoryCoverDaysCapability(ctx)) {
            DishIngredientCoverSalesBaseline baseline =
                    DishIngredientCoverSalesBaselineSupport.resolve(state, ctx);
            String baselinePhrase =
                    baseline != null && StringUtils.hasText(baseline.getDisplayLabel())
                            ? baseline.getDisplayLabel().trim()
                            : "最近7天销量基线";
            out.put("timeText", "当前库存 · " + baselinePhrase);
            out.put("dateRangeText", null);
            return;
        }
        if (ToolRequestContractExecutionParamSupport.isWarehouseNearExpiryContract(ctx)) {
            String asOf =
                    InventoryPresentationTimeSupport.resolveCoverStockSnapshotAsOfDateIso(state, ctx);
            out.put(
                    "timeText",
                    InventoryPresentationTimeSupport.formatStockSnapshotLabel(asOf));
            out.put("dateRangeText", null);
            return;
        }
        if (ToolRequestContractExecutionParamSupport.isWarehouseInventorySupervisionContract(ctx)) {
            String asOf =
                    InventoryPresentationTimeSupport.resolveCoverStockSnapshotAsOfDateIso(state, ctx);
            out.put(
                    "timeText",
                    InventoryPresentationTimeSupport.formatStockSnapshotLabel(asOf));
            out.put("dateRangeText", null);
            return;
        }
        String timeText = null;
        AiResolvedTimeWindow tw = ctx != null ? ctx.getTimeWindow() : null;
        if (ctx != null && StringUtils.hasText(ctx.getTimeWindowLabel())) {
            timeText = ctx.getTimeWindowLabel().trim();
        } else if (tw != null) {
            String cn = AiResolvedTimeWindowDisplaySupport.labelDisplayCn(tw.getTimeLabel());
            if (StringUtils.hasText(cn)) {
                timeText = cn;
            } else if (StringUtils.hasText(tw.getDisplayText())
                    && !"继承上一轮时间窗".equals(tw.getDisplayText().trim())) {
                timeText = tw.getDisplayText().trim();
            }
        }
        LocalDate start = tw != null ? tw.getStartDate() : null;
        LocalDate end = tw != null ? tw.getEndDate() : null;
        if (start == null && state != null && StringUtils.hasText(state.getStatStartDate())) {
            start = parseYmd(state.getStatStartDate());
        }
        if (end == null && state != null && StringUtils.hasText(state.getStatEndDate())) {
            end = parseYmd(state.getStatEndDate());
        }
        out.put("timeText", blankToNull(timeText));
        out.put("dateRangeText", blankToNull(formatMdRange(start, end)));
    }

    private static String resolveNoticeText(
            String boundaryNote,
            String permissionPrefix,
            List<AiPermissionDenied> denials) {
        if (StringUtils.hasText(permissionPrefix)
                || (denials != null && !denials.isEmpty())) {
            return "部分功能权限受限";
        }
        if (!StringUtils.hasText(boundaryNote)) {
            return null;
        }
        return null;
    }

    private static String composeContextBar(
            String storeText,
            String timeText,
            String dateRangeText,
            String permissionScopeText,
            String scopeText) {
        List<String> parts = new ArrayList<>(3);
        if (StringUtils.hasText(storeText)) {
            parts.add(storeText.trim());
        }
        String timeSegment = resolveContextBarTimeSegment(timeText, dateRangeText);
        if (StringUtils.hasText(timeSegment)) {
            parts.add(timeSegment);
        }
        String scopeSegment = StringUtils.hasText(permissionScopeText) ? permissionScopeText : scopeText;
        if (StringUtils.hasText(scopeSegment)) {
            parts.add(scopeSegment.trim());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" · ", parts);
    }

    /**
     * contextBar 时间中段：有 {@code dateRangeText} 时仅用它（优先于 {@code timeText}）；
     * 否则回退 {@code timeText}。完整 yyyy-MM-dd 区间只保留在 {@code timeText} 字段，不重复拼进 contextBar。
     */
    private static String resolveContextBarTimeSegment(String timeText, String dateRangeText) {
        if (StringUtils.hasText(dateRangeText)) {
            return dateRangeText.trim();
        }
        if (StringUtils.hasText(timeText)) {
            return timeText.trim();
        }
        return null;
    }

    private static String joinPreamble(
            String boundaryNote, String scopePrefix, String intentPrefix, String permissionPrefix) {
        StringBuilder head = new StringBuilder();
        appendBlock(head, boundaryNote);
        appendBlock(head, scopePrefix);
        appendBlock(head, intentPrefix);
        appendBlock(head, permissionPrefix);
        return head.toString().trim();
    }

    private static void appendBlock(StringBuilder head, String block) {
        if (!StringUtils.hasText(block)) {
            return;
        }
        if (head.length() > 0) {
            head.append('\n');
        }
        head.append(block.trim());
    }

    private static String formatMdRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        if (start.equals(end)) {
            return MD_FMT.format(start);
        }
        return MD_FMT.format(start) + "–" + MD_FMT.format(end);
    }

    private static LocalDate parseYmd(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String stringVal(Object o) {
        return o instanceof String s && StringUtils.hasText(s) ? s.trim() : null;
    }
}
