package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        if (baselineOrg == null || previousTurn == null) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        List<Integer> prevIds = previousTurn.getLastVisibleStoreIds();
        if (prevIds == null || prevIds.isEmpty()
                || baselineOrg.getVisibleStores() == null || baselineOrg.getVisibleStores().isEmpty()) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        if (messageDeclaresBroadGroupReset(rawMessage)) {
            return new OrgScopeApplyOutcome(baselineOrg, false);
        }
        if (hasExplicitUniqueStoreMention(rawMessage, baselineOrg)) {
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

    /** 当前句是否在可见门店列表中唯一命中一家店（本策略应让位给显式收窄）。 */
    public static boolean hasExplicitUniqueStoreMention(String rawMessage, AiResolvedOrgScope groupLikeOrg) {
        if (groupLikeOrg == null || !AiResolvedOrgScope.SCOPE_GROUP.equals(groupLikeOrg.getScopeType())) {
            return false;
        }
        List<AiStoreScopeDTO> stores = groupLikeOrg.getVisibleStores();
        if (stores == null || stores.size() <= 1) {
            return false;
        }
        if (!StringUtils.hasText(rawMessage)) {
            return false;
        }
        String work = FollowUpIntentResolveService.stripKnownTemporalPhrases(rawMessage.trim());
        if (!StringUtils.hasText(work)) {
            work = rawMessage.trim();
        }
        work = work.replace(" ", "");
        return AiFollowUpResolver.uniquelyMentionedStoreFromVisibleList(work, stores).isPresent();
    }

    /**
     * 用户显式要求回到集团/全量门店视角（如「全部门店呢」「集团呢」），
     * 与 {@link AiFollowUpResolver} 中「店名+呢」收窄区分；需先于假店名片语匹配处理。
     */
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
}
