package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多轮会话组织范围公共策略：本句无显式点名门店且无「全集团/所有门店」等重置用语时，
 * 用上一轮 {@link AiConversationTurnMemory#getLastVisibleStoreIds()} 与当前权限可见门店求交，收窄 effective org。
 */
public final class AiMultiTurnOrgScopePolicy {

    private AiMultiTurnOrgScopePolicy() {
    }

    public record OrgScopeApplyOutcome(AiResolvedOrgScope org, boolean inheritedFromPreviousTurn) {
    }

    /**
     * @param baselineOrg 通常为集团 {@link AiResolvedOrgScope#SCOPE_GROUP} 全量可见门店；已与 {@link com.nongxinle.ai.conversation.AiFollowUpResolver} 输出合并
     */
    public static OrgScopeApplyOutcome applyInheritedEffectiveOrgScope(
            AiResolvedOrgScope baselineOrg,
            AiConversationTurnMemory previousTurn,
            String rawMessage) {
        return applyInheritedEffectiveOrgScope(baselineOrg, previousTurn, rawMessage, null, null, null);
    }

    /**
     * @param dishProfitReasonDishHint 可为 null；非空且为「点名 + 毛利原因」追问时，若菜名不在上轮 harness 菜榜 {@link AiConversationTurnMemory#getLastFocusName()}，
     *                             则不打断为多轮门店收窄，回退为 baseline 集团可见口径重新查。
     */
    public static OrgScopeApplyOutcome applyInheritedEffectiveOrgScope(
            AiResolvedOrgScope baselineOrg,
            AiConversationTurnMemory previousTurn,
            String rawMessage,
            String dishProfitReasonDishHint) {
        return applyInheritedEffectiveOrgScope(baselineOrg, previousTurn, rawMessage, dishProfitReasonDishHint, null, null);
    }

    /**
     * @param structuredIntentDetailWire 当轮解析下发的 structuredIntentDetail（wire/canonical）；用于门店优先级排行等场景的收窄豁免。
     */
    public static OrgScopeApplyOutcome applyInheritedEffectiveOrgScope(
            AiResolvedOrgScope baselineOrg,
            AiConversationTurnMemory previousTurn,
            String rawMessage,
            String dishProfitReasonDishHint,
            String structuredIntentDetailWire) {
        return applyInheritedEffectiveOrgScope(
                baselineOrg, previousTurn, rawMessage, dishProfitReasonDishHint, structuredIntentDetailWire, null);
    }

    /**
     * @param semanticLlm 非空且声明点名门店时，本策略不收窄（交由语义门店映射处理）。
     */
    public static OrgScopeApplyOutcome applyInheritedEffectiveOrgScope(
            AiResolvedOrgScope baselineOrg,
            AiConversationTurnMemory previousTurn,
            String rawMessage,
            String dishProfitReasonDishHint,
            String structuredIntentDetailWire,
            AiQuerySemanticParseResult semanticLlm) {
        if (baselineOrg == null || previousTurn == null) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        boolean contractLocked =
                semanticLlm != null && SemanticContractCompletionEngine.isContractLockedParse(semanticLlm);
        if (AiQuerySemanticLexicon.isStorePriorityRankingStructuredDetail(structuredIntentDetailWire)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        if (!contractLocked
                && shouldSkipStoreNarrowingForUnlistedNamedDishProfitReason(
                        baselineOrg, previousTurn, rawMessage, dishProfitReasonDishHint, structuredIntentDetailWire)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        List<Integer> prevIds = previousTurn.getLastVisibleStoreIds();
        if (prevIds == null || prevIds.isEmpty()
                || baselineOrg.getVisibleStores() == null || baselineOrg.getVisibleStores().isEmpty()) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        if (!contractLocked && messageDeclaresBroadGroupReset(rawMessage)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }

        if (semanticDeclaresStoreFocus(semanticLlm, baselineOrg)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        // 【关键修复】当 LLM 返回 OVERRIDE/NEW 但无具体门店名时（如"全部店铺平均毛利"），
        // 不继承上一轮 STORE scope，保持 baselineOrg（GROUP 全量可见）。
        if (semanticDeclaresGroupOverride(semanticLlm)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        if (shouldReleaseHarnessDualStoreContextFromPreviousTurn(previousTurn, semanticLlm)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }

        Set<Integer> want = new HashSet<>(prevIds);
        List<AiStoreScopeDTO> allVisible = baselineOrg.getVisibleStores();
        Set<Long> baseIds = allVisible.stream()
                .filter(s -> s != null && s.getStoreDepartmentId() != null)
                .map(AiStoreScopeDTO::getStoreDepartmentId)
                .collect(Collectors.toSet());
        List<AiStoreScopeDTO> filtered = new ArrayList<>();
        for (AiStoreScopeDTO s : allVisible) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            if (want.contains(s.getStoreDepartmentId().intValue())) {
                filtered.add(s);
            }
        }
        if (filtered.isEmpty()) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        Set<Long> filteredIds = filtered.stream()
                .map(AiStoreScopeDTO::getStoreDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (filteredIds.equals(baseIds)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }

        if (filtered.size() == 1) {
            AiResolvedOrgScope narrowed = AiFollowUpResolver.copyOrgNarrowedToSingleStore(baselineOrg, filtered.get(0));
            return new OrgScopeApplyOutcome(narrowed, true);
        }
        return new OrgScopeApplyOutcome(
                AiResolvedOrgScope.builder()
                        .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                        .distributerId(baselineOrg.getDistributerId())
                        .requestDepartmentId(baselineOrg.getRequestDepartmentId())
                        .currentStoreDepartmentId(baselineOrg.getCurrentStoreDepartmentId())
                        .currentDepartmentId(baselineOrg.getCurrentDepartmentId())
                        .visibleStores(new ArrayList<>(filtered))
                        .visibleWarehouses(baselineOrg.getVisibleWarehouses() == null
                                ? new ArrayList<>() : new ArrayList<>(baselineOrg.getVisibleWarehouses()))
                        .visibleDepartments(baselineOrg.getVisibleDepartments() == null
                                ? new ArrayList<>() : new ArrayList<>(baselineOrg.getVisibleDepartments()))
                        .scopeName(baselineOrg.getScopeName())
                        .queryScopeBanner(buildMultiStoreBanner(filtered, baselineOrg.getQueryScopeBanner()))
                        .coverageDetail(baselineOrg.getCoverageDetail())
                        .build(),
                true);
    }

    private static boolean shouldReleaseHarnessDualStoreContextFromPreviousTurn(
            AiConversationTurnMemory previousTurn, AiQuerySemanticParseResult sem) {
        if (previousTurn == null || sem == null || sem.isParseMissing()) {
            return false;
        }
        if (!sem.effectiveMentionedStoreNames().isEmpty()) {
            return false;
        }
        if ("INHERIT_PREVIOUS".equals(normalizeSemanticAction(sem.getScopeAction()))) {
            return false;
        }
        List<String> harnessMs = previousTurn.getLastHarnessMultiStoreMatchedStores();
        if (harnessMs == null || harnessMs.isEmpty()) {
            harnessMs = AiConversationTurnMemory.readHarnessMultiStoreFromToolSummary(previousTurn.getLastToolSummary());
        }
        if (harnessMs == null || harnessMs.size() < 2) {
            return false;
        }
        List<Integer> prevIds = previousTurn.getLastVisibleStoreIds();
        return prevIds != null && prevIds.size() >= 2;
    }

    private static String normalizeSemanticAction(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean semanticDeclaresStoreFocus(
            AiQuerySemanticParseResult sem,
            AiResolvedOrgScope groupLikeOrg) {
        if (sem == null || sem.isParseMissing() || groupLikeOrg == null
                || !AiResolvedOrgScope.SCOPE_GROUP.equals(groupLikeOrg.getScopeType())) {
            return false;
        }
        if (!sem.effectiveMentionedStoreNames().isEmpty()) {
            return true;
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = sem.getRequestedScope();
        return rs != null && StringUtils.hasText(rs.getMentionedStoreName());
    }

    /**
     * 检测 LLM 语义层是否显式声明了「集团/全店范围覆盖」。
     * 当 scopeAction=OVERRIDE 或 NEW，但 mentionedStoreNames 为空时，
     * 说明用户在说「全部店铺/全集团范围」，此时不允许继承上一轮的 STORE。
     */
    private static boolean semanticDeclaresGroupOverride(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.isParseMissing()) {
            return false;
        }
        // 必须有 OVERRIDE 或 NEW action
        String action = normalizeSemanticAction(sem.getScopeAction());
        if (!("OVERRIDE".equals(action) || "NEW".equals(action))) {
            return false;
        }
        // 并且没有点名具体门店
        if (!sem.effectiveMentionedStoreNames().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * 用户显式要求回到集团/全量门店视角（如「全部门店呢」「集团呢」），
     * 需先于其它范围策略处理。
     * <p>
     * <b>LEGACY_ONLY</b>：仅非 contract-locked 多轮继承链使用；contract-locked 主链由 scope contract / semanticSlots 决定，
     * 不得再读用户原文 {@code contains} 推断范围动作。
     *
     * @deprecated 禁止在此新增中文关键词；contract-locked 主链已摘链。
     */
    @Deprecated
    public static boolean messageDeclaresBroadGroupReset(String norm) {
        if (!StringUtils.hasText(norm)) {
            return false;
        }
        String s = norm.replace(" ", "");
        return s.contains("全部门店") || s.contains("整个集团") || s.contains("全集团")
                || s.contains("所有门店") || s.contains("各家门店") || s.contains("集团全部")
                || s.contains("全部餐厅") || s.contains("集团整体")
                || s.contains("集团呢");
    }

    private static String buildMultiStoreBanner(List<AiStoreScopeDTO> stores, String fallback) {
        if (stores == null || stores.isEmpty()) {
            return fallback;
        }
        int n = stores.size();
        StringBuilder sb = new StringBuilder("已收窄门店范围：共 ").append(n).append(" 家（");
        for (int i = 0; i < Math.min(n, 5); i++) {
            if (i > 0) {
                sb.append("、");
            }
            AiStoreScopeDTO st = stores.get(i);
            String nm = st != null && StringUtils.hasText(st.getStoreName())
                    ? st.getStoreName() : ("门店" + (st != null ? st.getStoreDepartmentId() : ""));
            sb.append(nm);
        }
        if (n > 5) {
            sb.append("…");
        }
        sb.append("）");
        return sb.toString();
    }

    public static Optional<String> singleVisibleStoreName(AiResolvedOrgScope org) {
        if (org == null || org.getVisibleStores() == null || org.getVisibleStores().size() != 1) {
            return Optional.empty();
        }
        AiStoreScopeDTO s = org.getVisibleStores().get(0);
        if (s == null) {
            return Optional.empty();
        }
        if (StringUtils.hasText(s.getStoreName())) {
            return Optional.of(s.getStoreName());
        }
        if (s.getStoreDepartmentId() != null) {
            return Optional.of("门店" + s.getStoreDepartmentId());
        }
        return Optional.empty();
    }

    /**
     * 点名菜的毛利原因追问：若上轮门店范围真子集于集团可见、且菜名不在上轮 AnswerPlan/诊断 harness 写入的 {@link AiConversationTurnMemory#getLastFocusName()} 菜名录，
     * 则不应继续套用上轮单店/少店范围（避免「AAA 上下文 + 另一道菜」误查）。
     */
    private static boolean shouldSkipStoreNarrowingForUnlistedNamedDishProfitReason(
            AiResolvedOrgScope baselineOrg,
            AiConversationTurnMemory previousTurn,
            String rawMessage,
            String dishHint,
            String structuredIntentDetailWire) {
        if (!StringUtils.hasText(rawMessage) || !StringUtils.hasText(dishHint) || previousTurn == null) {
            return false;
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structuredIntentDetailWire);
        if (!AiQuerySemanticLexicon.isDishLowProfitReasonStructuredWire(wire)) {
            return false;
        }
        if (!AiResolvedOrgScope.SCOPE_GROUP.equals(baselineOrg.getScopeType())) {
            return false;
        }
        List<Integer> prevIds = previousTurn.getLastVisibleStoreIds();
        if (prevIds == null || prevIds.isEmpty()) {
            return false;
        }
        List<AiStoreScopeDTO> allVisible = baselineOrg.getVisibleStores();
        if (allVisible == null || allVisible.isEmpty()) {
            return false;
        }
        long baseCount = allVisible.stream()
                .filter(s -> s != null && s.getStoreDepartmentId() != null)
                .count();
        if (baseCount <= 1L || prevIds.size() >= baseCount) {
            return false;
        }
        String roster = previousTurn.getLastFocusName();
        if (!StringUtils.hasText(roster)) {
            return true;
        }
        return !dishNameMatchesHarnessRoster(roster, dishHint);
    }

    /**
     * LEGACY_ONLY：仅非 contract-locked 的「未上榜菜名 + 毛利原因」多轮收窄豁免；contract-locked 主链已摘链。
     * TODO(LEGACY_ONLY-CLEANUP): 后续应改为 resultAnchors / entityId / 标准化实体匹配。
     */
    private static boolean dishNameMatchesHarnessRoster(String rosterCsv, String dishHint) {
        String hint = dishHint.replace(" ", "").trim();
        if (hint.isEmpty()) {
            return false;
        }
        for (String part : rosterCsv.split(",")) {
            if (part == null) {
                continue;
            }
            String p = part.replace(" ", "").trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.equals(hint) || hint.contains(p) || p.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
