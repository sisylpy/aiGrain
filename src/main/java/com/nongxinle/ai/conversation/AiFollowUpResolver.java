package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.util.AiUserMessageSanitizer;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 结构化短路 {@link AiFollowUpResolution} 组装与<strong>确定性</strong>门店映射（LLM 口述店名 → visible 候选）。
 * 用户自然语言语义（追问句式、正文点名门店等）由 LLM 解析与 {@link com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper} 完成；本类不对 {@code rawMessage} 做语义判断。
 */
public final class AiFollowUpResolver {

    private AiFollowUpResolver() {
    }

    /**
     * 结构化语义 LLM 达阈短路：不跑 Java keyword follow-up 分支；合并后的 intent/时间/草案组织范围已在外层算好。
     */
    public static AiFollowUpResolution semanticStructuralBypassResolution(
            AiConversationTurnMemory previousTurn,
            AiResolvedQueryIntent mergedQueryIntentPostLlm,
            AiResolvedTimeWindow mergedTwPostLlm,
            AiResolvedOrgScope orgScopeDraft,
            String rawMessage,
            AiQuerySemanticParseResult sem) {
        String norm = rawMessage == null ? "" : AiUserMessageSanitizer.stripLeadingEnumeration(rawMessage).trim();
        AiResolvedQueryIntent merged =
                copyIntent(
                        mergedQueryIntentPostLlm != null
                                ? mergedQueryIntentPostLlm
                                : AiResolvedQueryIntent.builder().build());
        AiResolvedTimeWindow mergedTw = mergedTwPostLlm;
        AiResolvedOrgScope mergedOrg = orgScopeDraft;

        boolean fu =
                previousTurn != null
                        && StringUtils.hasText(previousTurn.getLastPathCode())
                        && sem != null
                        && Boolean.TRUE.equals(sem.getFollowUp());

        AiFollowUpResolution.AiFollowUpResolutionBuilder b =
                AiFollowUpResolution.builder()
                        .followUp(fu)
                        .followUpType("SEMANTIC_STRUCTURAL_MERGE")
                        .inheritIntent(false)
                        .inheritTimeWindow(false)
                        .inheritOrgScope(true)
                        .inheritFocus(false)
                        .normalizedInputExpandedAtResolvePhase(false)
                        .mergedQueryIntent(merged)
                        .mergedTimeWindow(mergedTw)
                        .mergedOrgScope(mergedOrg);

        b.purchaseStructuredIntent(merged.getStructuredIntentDetail());
        b.purchaseSourceType(merged.getPurchaseSourceType());
        return fillSources(b, merged, mergedTw, mergedOrg, previousTurn, norm);
    }

    /**
     * 语义解析不可信或无法映射到合法业务 path：不跑任何 Java keyword follow-up，
     * 仅保留显式日期（若有）与组织草案；澄清话术由 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 写入上下文。
     */
    public static AiFollowUpResolution clarificationFailureResolution(
            AiResolvedOrgScope orgScopeDraft, AiResolvedTimeWindow explicitTimeOnlyOrNull, String normalized) {

        AiResolvedQueryIntent empty = AiResolvedQueryIntent.builder().build();
        String norm = normalized == null ? "" : AiUserMessageSanitizer.stripLeadingEnumeration(normalized).trim();
        AiFollowUpResolution.AiFollowUpResolutionBuilder b =
                AiFollowUpResolution.builder()
                        .followUp(false)
                        .followUpType("NEED_SEMANTIC_CLARIFICATION")
                        .inheritIntent(false)
                        .inheritTimeWindow(false)
                        .inheritOrgScope(false)
                        .inheritFocus(false)
                        .normalizedInputExpandedAtResolvePhase(false)
                        .mergedQueryIntent(empty)
                        .mergedTimeWindow(explicitTimeOnlyOrNull)
                        .mergedOrgScope(orgScopeDraft);

        return fillSources(b, empty, explicitTimeOnlyOrNull, orgScopeDraft, null, norm);
    }

    private static AiFollowUpResolution fillSources(
            AiFollowUpResolution.AiFollowUpResolutionBuilder b,
            AiResolvedQueryIntent merged,
            AiResolvedTimeWindow mergedTw,
            AiResolvedOrgScope mergedOrg,
            AiConversationTurnMemory previousTurn,
            String norm) {
        AiFollowUpResolution r = b.build();
        if (merged != null) {
            if (mergedTw == null) {
                r.setEffectiveTimeWindowSource("UNRESOLVED");
            } else if (mergedTw.isInheritedFromPreviousTurn()) {
                r.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
            } else if (mergedTw.isExplicitTimeMentioned()) {
                r.setEffectiveTimeWindowSource("CURRENT_MESSAGE_EXPLICIT");
            } else {
                r.setEffectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE");
            }
        }
        String fut = r.getFollowUpType();
        if ("STORE_SCOPE_FOLLOW_UP".equals(fut)) {
            r.setEffectiveScopeSource("CURRENT_MESSAGE_STORE_OVERRIDE");
        } else if ("GROUP_SCOPE_EXPAND_FOLLOW_UP".equals(fut)) {
            r.setEffectiveScopeSource("CURRENT_MESSAGE_GROUP_EXPAND");
        } else if ("STORE_PRIORITY_RANKING_FOLLOW_UP".equals(fut)) {
            r.setEffectiveScopeSource("CURRENT_MESSAGE_STORE_PRIORITY_GROUP");
        } else if ("SCOPE_SHIFT".equals(fut)) {
            r.setEffectiveScopeSource("FOLLOWUP_NARROW_VISIBLE_STORE");
        } else {
            r.setEffectiveScopeSource(r.isInheritOrgScope() ? "INHERITED_PREVIOUS" : "CURRENT_MESSAGE");
        }
        if (merged != null) {
            r.setEffectiveIntentCode(merged.getIntentCode());
            r.setEffectivePathCode(merged.getPathCode());
        }
        if (r.isFollowUp() && r.isInheritIntent()) {
            if ("PURCHASE_DETAIL_FOLLOW_UP".equals(fut)) {
                r.setEffectiveIntentSource("CURRENT_MESSAGE_PURCHASE_DETAIL");
            } else {
                r.setEffectiveIntentSource("INHERITED_PREVIOUS");
            }
        } else {
            r.setEffectiveIntentSource("CURRENT_MESSAGE_EXPLICIT");
        }
        return r;
    }

    private static AiResolvedQueryIntent copyIntent(AiResolvedQueryIntent src) {
        if (src == null) {
            return AiResolvedQueryIntent.builder().build();
        }
        return AiResolvedQueryIntent.builder()
                .intentCode(src.getIntentCode())
                .pathCode(src.getPathCode())
                .topic(src.getTopic())
                .inheritedFromPreviousTurn(src.isInheritedFromPreviousTurn())
                .inheritedFromIntentCode(src.getInheritedFromIntentCode())
                .structuredIntentDetail(src.getStructuredIntentDetail())
                .purchaseSourceType(src.getPurchaseSourceType())
                .build();
    }

    /**
     * 继承上一轮会话意图（path / structured / purchaseSource）；{@code norm} 预留参数，当前未使用。
     */
    public static AiResolvedQueryIntent inheritIntentFromMemory(
            AiConversationTurnMemory prev, @SuppressWarnings("unused") String norm) {
        if (prev == null || !StringUtils.hasText(prev.getLastPathCode())) {
            return AiResolvedQueryIntent.builder().build();
        }
        String path = prev.getLastPathCode();
        String structured = prev.getLastStructuredIntentDetail();
        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(path) && !StringUtils.hasText(structured)) {
            structured = AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY;
        }
        if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(path) && !StringUtils.hasText(structured)) {
            structured = AiQuerySemanticLexicon.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY;
        }
        String purchase = resolveInheritedPurchaseSourceType(prev, null);
        String intent = StringUtils.hasText(prev.getLastIntentCode())
                ? prev.getLastIntentCode()
                : inferIntentCodeFromPath(path);
        return AiResolvedQueryIntent.builder()
                .intentCode(intent)
                .pathCode(path)
                .structuredIntentDetail(structured)
                .purchaseSourceType(purchase)
                .inheritedFromPreviousTurn(true)
                .inheritedFromIntentCode(
                        StringUtils.hasText(prev.getLastIntentCode())
                                ? prev.getLastIntentCode()
                                : intent)
                .topic(null)
                .build();
    }

    private static String resolveInheritedPurchaseSourceType(AiConversationTurnMemory prev, AiResolvedQueryIntent lex) {
        if (lex != null && StringUtils.hasText(lex.getPurchaseSourceType())) {
            return lex.getPurchaseSourceType();
        }
        if (prev == null || !StringUtils.hasText(prev.getLastPathCode())) {
            return null;
        }
        String pp = prev.getLastPathCode();
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pp)
                || AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(pp)) {
            return prev.getLastPurchaseSourceType();
        }
        return null;
    }

    private static String inferIntentCodeFromPath(String pathCode) {
        if (!StringUtils.hasText(pathCode)) {
            return null;
        }
        if (AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(pathCode)) {
            return AiResolvedQueryIntent.PURCHASE_OVERVIEW;
        }
        if (AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(pathCode)) {
            return AiResolvedQueryIntent.BUSINESS_OVERVIEW;
        }
        if (AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(pathCode)) {
            return AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW;
        }
        if (AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(pathCode)) {
            return AiResolvedQueryIntent.DISH_PROFIT;
        }
        if (AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(pathCode)) {
            return AiResolvedQueryIntent.DISH_SALES_QUERY;
        }
        if (AiResolvedQueryIntent.PATH_COST_DIAGNOSIS.equals(pathCode)) {
            return AiResolvedQueryIntent.COST_DIAGNOSIS;
        }
        if (AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(pathCode)) {
            return AiResolvedQueryIntent.STOCK_REDUCE_QUERY;
        }
        if (AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(pathCode)) {
            return AiResolvedQueryIntent.BUSINESS_DIAGNOSIS;
        }
        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(pathCode)) {
            return AiResolvedQueryIntent.REVENUE_OVERVIEW;
        }
        return null;
    }

    /**
     * 语义 LLM 给出的口述店名：在<b>给定候选</b>（通常为 visibleStores∩DB roots）中求唯一命中，用于收窄集团范围。
     */
    public static Optional<AiStoreScopeDTO> uniquelyResolvedStoreFromLlmMention(
            String rawMention, List<AiStoreScopeDTO> candidates) {
        SemanticLexicalSingleStoreHit r = matchSemanticSingleStoreLexically(rawMention, candidates);
        return r.kind() == SemanticLexicalSingleStoreKind.UNIQUE && r.unique() != null
                ? Optional.of(r.unique())
                : Optional.empty();
    }

    public enum SemanticLexicalSingleStoreKind {
        UNIQUE,
        NONE,
        AMBIGUOUS
    }

    public record SemanticLexicalSingleStoreHit(
            SemanticLexicalSingleStoreKind kind,
            AiStoreScopeDTO unique,
            List<AiStoreScopeDTO> ambiguousStores) {}

    /**
     * 语义点名店名 VS 候选根：唯一 / 无命中 / 多最长名并列（tie-breaker 后与 {@link #uniquelyResolvedStoreFromLlmMention} 一致）。
     */
    public static SemanticLexicalSingleStoreHit matchSemanticSingleStoreLexically(
            String rawMention, List<AiStoreScopeDTO> candidates) {
        List<AiStoreScopeDTO> noneList = List.of();
        if (!StringUtils.hasText(rawMention) || candidates == null || candidates.isEmpty()) {
            return new SemanticLexicalSingleStoreHit(SemanticLexicalSingleStoreKind.NONE, null, noneList);
        }
        String normHint = normalizeForStoreMatch(rawMention);
        if (!StringUtils.hasText(normHint) || normHint.length() < 1) {
            return new SemanticLexicalSingleStoreHit(SemanticLexicalSingleStoreKind.NONE, null, noneList);
        }
        List<AiStoreScopeDTO> hits = new ArrayList<>();
        for (AiStoreScopeDTO st : candidates) {
            if (st == null || !StringUtils.hasText(st.getStoreName())) {
                continue;
            }
            if (departmentNameMatches(normHint, st.getStoreName())) {
                hits.add(st);
            }
        }
        if (hits.isEmpty()) {
            return new SemanticLexicalSingleStoreHit(SemanticLexicalSingleStoreKind.NONE, null, noneList);
        }
        int maxLen = hits.stream()
                .mapToInt(s -> s.getStoreName() != null ? s.getStoreName().trim().replace(" ", "").length() : 0)
                .max()
                .orElse(0);
        if (maxLen < 1) {
            return new SemanticLexicalSingleStoreHit(SemanticLexicalSingleStoreKind.NONE, null, noneList);
        }
        List<AiStoreScopeDTO> longest = new ArrayList<>();
        for (AiStoreScopeDTO h : hits) {
            if (h.getStoreName() == null) {
                continue;
            }
            int len = h.getStoreName().trim().replace(" ", "").length();
            if (len == maxLen) {
                longest.add(h);
            }
        }
        if (longest.size() == 1) {
            return new SemanticLexicalSingleStoreHit(SemanticLexicalSingleStoreKind.UNIQUE, longest.get(0), noneList);
        }
        return new SemanticLexicalSingleStoreHit(
                SemanticLexicalSingleStoreKind.AMBIGUOUS, null, List.copyOf(longest));
    }

    /** 权限预览里仅有店名时的行 VS 经销门店根部名称（与口述店名收窄同一套规范化/包含匹配）。 */
    public static boolean visibleStoreRowLabelMatchesDepartmentName(String visibleStoreLabel, String rootDepartmentName) {
        if (!StringUtils.hasText(visibleStoreLabel) || !StringUtils.hasText(rootDepartmentName)) {
            return false;
        }
        String a = normalizeForStoreMatch(visibleStoreLabel);
        String b = normalizeForStoreMatch(rootDepartmentName);
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return a.contains(b) || b.contains(a);
    }

    private static boolean departmentNameMatches(String normHint, String dbName) {
        String n = normalizeForStoreMatch(dbName);
        if (!StringUtils.hasText(n)) {
            return false;
        }
        if (n.equals(normHint)) {
            return true;
        }
        return n.contains(normHint) || normHint.contains(n);
    }

    private static String normalizeForStoreMatch(String s) {
        if (s == null) {
            return "";
        }
        String x = s.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        x = x.replace("门店", "").replace("餐厅", "");
        if (x.endsWith("店") && x.length() > 1) {
            x = x.substring(0, x.length() - 1);
        }
        return x.trim();
    }

    /**
     * 集团范围 + 多条 visibleStores：收窄为<strong>仍为 GROUP</strong> 但仅保留 LLM 点名且仍属于 visible 的子集。
     */
    public static AiResolvedOrgScope copyOrgNarrowedToStoreSubsetKeepingGroup(
            AiResolvedOrgScope org, List<AiStoreScopeDTO> subsetStoresInOrder) {
        if (org == null || subsetStoresInOrder == null || subsetStoresInOrder.isEmpty()) {
            return org;
        }
        LinkedHashMap<Long, AiStoreScopeDTO> dedup = new LinkedHashMap<>();
        for (AiStoreScopeDTO s : subsetStoresInOrder) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            dedup.putIfAbsent(s.getStoreDepartmentId(), s);
        }
        if (dedup.isEmpty()) {
            return org;
        }
        List<AiStoreScopeDTO> picks = new ArrayList<>(dedup.values());
        String bannerNames = picks.stream()
                .map(AiStoreScopeDTO::getStoreName)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + "、" + b)
                .orElse("选定门店");
        String banner = "多店对比：" + bannerNames + "（" + picks.size() + "）";
        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .distributerId(org.getDistributerId())
                .requestDepartmentId(org.getRequestDepartmentId())
                .currentStoreDepartmentId(org.getCurrentStoreDepartmentId())
                .currentDepartmentId(org.getCurrentDepartmentId())
                .visibleStores(picks)
                .visibleWarehouses(org.getVisibleWarehouses() == null
                        ? new ArrayList<>() : new ArrayList<>(org.getVisibleWarehouses()))
                .visibleDepartments(org.getVisibleDepartments() == null
                        ? new ArrayList<>() : new ArrayList<>(org.getVisibleDepartments()))
                .scopeName(org.getScopeName())
                .queryScopeBanner(banner)
                .coverageDetail(org.getCoverageDetail())
                .build();
    }

    /** 口述店名短语列表逐项在候选中求唯一映射；按提及顺序返回去重门店 DTO（用于 LLM mentionedStoreNames）。 */
    public static List<AiStoreScopeDTO> resolvedStoresSubsetFromDistinctMentions(
            List<String> rawMentions, List<AiStoreScopeDTO> candidates) {
        List<AiStoreScopeDTO> out = new ArrayList<>();
        if (rawMentions == null || candidates == null || candidates.isEmpty()) {
            return out;
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (String raw : rawMentions) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            uniquelyResolvedStoreFromLlmMention(raw.trim(), candidates).ifPresent(st -> {
                if (st.getStoreDepartmentId() != null && seen.add(st.getStoreDepartmentId())) {
                    out.add(st);
                }
            });
        }
        return out;
    }

    public static AiResolvedOrgScope copyOrgNarrowedToSingleStore(AiResolvedOrgScope org, AiStoreScopeDTO store) {
        return rewriteOrgToSingleStore(org, store);
    }

    private static AiResolvedOrgScope rewriteOrgToSingleStore(AiResolvedOrgScope org, AiStoreScopeDTO store) {
        if (org == null || store == null) {
            return org;
        }
        List<AiStoreScopeDTO> one = new ArrayList<>();
        one.add(store);
        String labelRoot = StringUtils.hasText(store.getStoreName())
                ? store.getStoreName().trim()
                : ("门店" + store.getStoreDepartmentId());
        String banner = labelRoot + "单店口径";
        return AiResolvedOrgScope.builder()
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .distributerId(org.getDistributerId())
                .requestDepartmentId(store.getStoreDepartmentId())
                .currentStoreDepartmentId(store.getStoreDepartmentId())
                .currentDepartmentId(store.getStoreDepartmentId())
                .visibleStores(one)
                .visibleWarehouses(org.getVisibleWarehouses() == null
                        ? new ArrayList<>() : new ArrayList<>(org.getVisibleWarehouses()))
                .visibleDepartments(org.getVisibleDepartments() == null
                        ? new ArrayList<>() : new ArrayList<>(org.getVisibleDepartments()))
                .scopeName(org.getScopeName())
                .queryScopeBanner(banner)
                .coverageDetail(org.getCoverageDetail())
                .build();
    }
}
