package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装 {@link SemanticParserInput}（v2 LLM 用户消息 JSON），仅含脱敏字段，不包含任何数据库 ID。
 * <p>
 * 生产主链路仍走 {@link AiQuerySemanticLlmParser#parseUserQuestion(String)}；本类供后续 Resolver / Harness 在切换 v2 时调用。
 * <p>
 * <b>缺口</b>：`AiConversationTurnMemory` 未持久化多店点名列表，{@link SemanticParserPreviousTurn#getMentionedStoreNames()}
 * 恒为 {@code null}，仅 {@code mentionedStoreName}（来自 {@code lastMentionedStore}，必要时辅以 {@code lastFocusedStoreName}）。
 */
public final class SemanticParserInputBuilder {

    private SemanticParserInputBuilder() {
    }

    /**
     * @param normalizedUserMessage 与 Resolver 侧一致的清洗后问句（可为空串，由 {@link AiQuerySemanticLlmParser#parse} 再门禁）
     * @param today                 须与 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver#resolve(Long, com.nongxinle.ai.platform.dto.AiRunCreateRequest, com.nongxinle.ai.context.AiUserContext, LocalDate)}
     *                              的 {@code today} 或 Harness Replay 的 {@code frozenClockDate} 解析结果一致；不得为 null
     * @param previousTurn          可为 null（首轮）
     * @param orgScope              可为 null；非 null 时从 {@link AiResolvedOrgScope#getVisibleStores()} 仅取店名
     */
    public static SemanticParserInput build(
            String normalizedUserMessage,
            LocalDate today,
            AiConversationTurnMemory previousTurn,
            AiResolvedOrgScope orgScope) {
        if (today == null) {
            throw new IllegalArgumentException("today must not be null");
        }
        String msg = normalizedUserMessage == null ? "" : normalizedUserMessage.trim();
        return SemanticParserInput.builder()
                .currentUserMessage(msg)
                .today(today.toString())
                .previousTurn(mapPreviousTurn(previousTurn))
                .visibleStores(mapVisibleStores(orgScope))
                .build();
    }

    /**
     * 双跑 / Harness 观测用：仅含 currentUserMessage、today、previousTurn 文本摘要、visibleStores（每项仅 storeName），无 ID。
     */
    public static Map<String, Object> toDebugPreview(SemanticParserInput input) {
        if (input == null) {
            return null;
        }
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("currentUserMessage", input.getCurrentUserMessage());
        root.put("today", input.getToday());
        SemanticParserPreviousTurn pt = input.getPreviousTurn();
        if (pt == null) {
            root.put("previousTurn", null);
        } else {
            LinkedHashMap<String, Object> p = new LinkedHashMap<>();
            p.put("intentCode", pt.getIntentCode());
            p.put("pathCode", pt.getPathCode());
            p.put("structuredIntentDetail", pt.getStructuredIntentDetail());
            p.put("purchaseSourceType", pt.getPurchaseSourceType());
            p.put("timeLabel", pt.getTimeLabel());
            p.put("startDate", pt.getStartDate());
            p.put("endDate", pt.getEndDate());
            p.put("scopeType", pt.getScopeType());
            p.put("mentionedStoreName", pt.getMentionedStoreName());
            p.put("mentionedStoreNames", pt.getMentionedStoreNames());
            p.put("mentionedDishName", pt.getMentionedDishName());
            root.put("previousTurn", p);
        }
        List<Map<String, String>> vis = new ArrayList<>();
        if (input.getVisibleStores() != null) {
            for (SemanticParserVisibleStore vs : input.getVisibleStores()) {
                if (vs == null || !StringUtils.hasText(vs.getStoreName())) {
                    continue;
                }
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                row.put("storeName", vs.getStoreName().trim());
                vis.add(row);
            }
        }
        root.put("visibleStores", vis);
        return root;
    }

    private static SemanticParserPreviousTurn mapPreviousTurn(AiConversationTurnMemory mem) {
        if (mem == null) {
            return null;
        }
        String mentionedStore = trimToNull(mem.getLastMentionedStore());
        if (mentionedStore == null) {
            mentionedStore = trimToNull(mem.getLastFocusedStoreName());
        }
        return SemanticParserPreviousTurn.builder()
                .intentCode(trimToNull(mem.getLastIntentCode()))
                .pathCode(trimToNull(mem.getLastPathCode()))
                .structuredIntentDetail(trimToNull(mem.getLastStructuredIntentDetail()))
                .purchaseSourceType(trimToNull(mem.getLastPurchaseSourceType()))
                .timeLabel(trimToNull(mem.getLastTimeLabel()))
                .startDate(trimToNull(mem.getLastStartDate()))
                .endDate(trimToNull(mem.getLastEndDate()))
                .scopeType(trimToNull(mem.getLastScopeType()))
                .mentionedStoreName(mentionedStore)
                .mentionedStoreNames(null)
                .mentionedDishName(trimToNull(mem.getLastMentionedDishName()))
                .build();
    }

    private static List<SemanticParserVisibleStore> mapVisibleStores(AiResolvedOrgScope orgScope) {
        List<SemanticParserVisibleStore> out = new ArrayList<>();
        if (orgScope == null || orgScope.getVisibleStores() == null) {
            return out;
        }
        for (AiStoreScopeDTO s : orgScope.getVisibleStores()) {
            if (s == null) {
                continue;
            }
            String name = s.getStoreName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            out.add(SemanticParserVisibleStore.builder().storeName(name.trim()).build());
        }
        return out;
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
