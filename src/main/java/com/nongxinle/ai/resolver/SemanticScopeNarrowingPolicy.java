package com.nongxinle.ai.resolver;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiSemanticStoreNarrowingDiagnostics;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import com.nongxinle.ai.conversation.AiFollowUpResolver;
import com.nongxinle.ai.followup.rewrite.FollowUpRewriteResult;
import com.nongxinle.ai.harness.AiMultiStoreHarnessTrace;
import com.nongxinle.ai.platform.dto.AiRunCreateRequest;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.entity.GbDepartmentEntity;
import com.nongxinle.mapper.GbDepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 语义显式门店收窄：GROUP 权限下按 v2 mentionedStore / rewrite anchor / lexical 匹配收窄到单店或子集。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticScopeNarrowingPolicy {

    private final GbDepartmentMapper gbDepartmentMapper;

    public AiResolvedOrgScope narrowGroupOrgBySemanticLlmStoreIfNeeded(
            AiResolvedOrgScope mergedOrg,
            AiQuerySemanticParseResult semantic,
            AiRunCreateRequest request,
            boolean structuralLlmApplied,
            double minConfidence,
            String normalizedUserMessage,
            List<String> supplementalStoreMentions,
            AiMultiStoreHarnessTrace harnessTrace,
            AiSemanticStoreNarrowingDiagnostics diag) {
        List<String> mentionListRaw = semantic != null ? semantic.effectiveMentionedStoreNames() : List.of();
        List<String> mentionList = mentionListRaw == null ? new ArrayList<>() : new ArrayList<>(mentionListRaw);
        if (mentionList.isEmpty() && supplementalStoreMentions != null) {
            for (String supplemental : supplementalStoreMentions) {
                if (StringUtils.hasText(supplemental)) {
                    mentionList.add(supplemental.trim());
                }
            }
        }
        if (diag != null) {
            diag.setSemanticMentionedStoreNames(new ArrayList<>(mentionList));
        }
        AiQuerySemanticParseResult.RequestedScopePart rs = semantic != null ? semantic.getRequestedScope() : null;
        boolean explicitSemanticStoreIntent =
                !mentionList.isEmpty()
                        || (rs != null
                                && AiResolvedOrgScope.SCOPE_STORE.equals(rs.getRequestedScopeType())
                                && StringUtils.hasText(rs.getMentionedStoreName()));

        if (!structuralLlmApplied || mergedOrg == null) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_SKIPPED_STRUCTUREAL_GATE);
            }
            return mergedOrg;
        }
        if (semantic == null || semantic.isParseMissing() || !semantic.isUsableForMerge(minConfidence)) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_SKIPPED_SEMANTIC_UNUSABLE);
            }
            return mergedOrg;
        }
        if (!AiResolvedOrgScope.SCOPE_GROUP.equals(mergedOrg.getScopeType())) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_SKIPPED_NOT_GROUP_SCOPE);
            }
            return mergedOrg;
        }

        if (!explicitSemanticStoreIntent) {
            if (diag != null) {
                diag.setNarrowingAttemptedSemanticExplicitStore(false);
                diag.setNarrowingFailureReason(null);
            }
            return mergedOrg;
        }
        if (diag != null) {
            diag.setNarrowingAttemptedSemanticExplicitStore(true);
        }

        List<AiStoreScopeDTO> candidates =
                buildIntersectedStoreRootCandidates(mergedOrg, request, diag);

        if (mentionList.size() >= 2) {
            if (candidates.isEmpty()) {
                if (diag != null) {
                    diag.setNarrowingFailureReason(
                            AiSemanticStoreNarrowingDiagnostics.REASON_SEMANTIC_MENTION_BUT_EMPTY_CANDIDATES);
                }
                return mergedOrg;
            }
            for (String raw : mentionList) {
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                AiFollowUpResolver.SemanticLexicalSingleStoreHit h =
                        AiFollowUpResolver.matchSemanticSingleStoreLexically(raw.trim(), candidates);
                if (h.kind() == AiFollowUpResolver.SemanticLexicalSingleStoreKind.AMBIGUOUS) {
                    if (diag != null) {
                        diag.setAmbiguousLexicalMatch(true);
                        diag.setLastSingleSemanticStoreMention(raw.trim());
                        diag.setLexicalAmbiguityStoreSummaries(
                                h.ambiguousStores() == null
                                        ? new ArrayList<>()
                                        : h.ambiguousStores().stream()
                                                .map(SemanticScopeNarrowingPolicy::summarizeStoreCandidateForDiag)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList()));
                        diag.setNarrowingFailureReason(
                                AiSemanticStoreNarrowingDiagnostics.REASON_AMBIGUOUS_LEXICAL_MATCH);
                    }
                    return mergedOrg;
                }
            }
            List<AiStoreScopeDTO> picks =
                    AiFollowUpResolver.resolvedStoresSubsetFromDistinctMentions(mentionList, candidates);
            if (picks.size() >= 2) {
                log.info(
                        "[SemanticScopeNarrowing] semanticLlmMultiStoreSubset hitCount={} storeRootIds={}",
                        picks.size(),
                        picks.stream().map(AiStoreScopeDTO::getStoreDepartmentId).toList());
                if (harnessTrace != null) {
                    harnessTrace.noteSubsetKeepingGroupApplied(picks);
                }
                if (diag != null) {
                    diag.setNarrowedSuccessfully(true);
                    diag.setNarrowingFailureReason(null);
                    diag.setMatchedStoreCandidate(null);
                    diag.setMatchedSemanticStoreMention(null);
                }
                return AiFollowUpResolver.copyOrgNarrowedToStoreSubsetKeepingGroup(mergedOrg, picks);
            }
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_MULTI_STORE_SUBSET_PARTIAL);
            }
            return mergedOrg;
        }

        String singleStoreMention = null;
        if (mentionList.size() == 1) {
            singleStoreMention = mentionList.get(0);
        } else if (rs != null
                && AiResolvedOrgScope.SCOPE_STORE.equals(rs.getRequestedScopeType())
                && StringUtils.hasText(rs.getMentionedStoreName())) {
            singleStoreMention = rs.getMentionedStoreName().trim();
        }
        if (!StringUtils.hasText(singleStoreMention)) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_NO_SINGLE_STORE_MENTION);
            }
            return mergedOrg;
        }
        if (diag != null) {
            diag.setLastSingleSemanticStoreMention(singleStoreMention.trim());
        }
        if (candidates.isEmpty()) {
            if (diag != null) {
                diag.setNarrowingFailureReason(
                        AiSemanticStoreNarrowingDiagnostics.REASON_SEMANTIC_MENTION_BUT_EMPTY_CANDIDATES);
            }
            return mergedOrg;
        }
        AiFollowUpResolver.SemanticLexicalSingleStoreHit hit =
                AiFollowUpResolver.matchSemanticSingleStoreLexically(singleStoreMention.trim(), candidates);
        if (hit.kind() == AiFollowUpResolver.SemanticLexicalSingleStoreKind.AMBIGUOUS) {
            if (diag != null) {
                diag.setAmbiguousLexicalMatch(true);
                diag.setLexicalAmbiguityStoreSummaries(
                        hit.ambiguousStores() == null
                                ? new ArrayList<>()
                                : hit.ambiguousStores().stream()
                                        .map(SemanticScopeNarrowingPolicy::summarizeStoreCandidateForDiag)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList()));
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_AMBIGUOUS_LEXICAL_MATCH);
            }
            return mergedOrg;
        }
        if (hit.kind() != AiFollowUpResolver.SemanticLexicalSingleStoreKind.UNIQUE || hit.unique() == null) {
            if (diag != null) {
                diag.setNarrowingFailureReason(AiSemanticStoreNarrowingDiagnostics.REASON_NO_LEXICAL_MATCH);
            }
            return mergedOrg;
        }
        AiStoreScopeDTO narrowed = hit.unique();
        log.info(
                "[SemanticScopeNarrowing] semanticLlmStoreMentionHit storeRootId={} storeName={}",
                narrowed.getStoreDepartmentId(),
                narrowed.getStoreName());
        if (diag != null) {
            diag.setNarrowedSuccessfully(true);
            diag.setNarrowingFailureReason(null);
            diag.setMatchedStoreCandidate(summarizeStoreCandidateForDiag(narrowed));
            diag.setMatchedSemanticStoreMention(singleStoreMention.trim());
        }
        return AiFollowUpResolver.copyOrgNarrowedToSingleStore(mergedOrg, narrowed);
    }

    /**
     * Scope pivot：Rewrite / 短句中点名单店，但 v2 未写入 requestedScope 时的收窄候选（基于 visibleStores / rewrite anchors，无店名硬编码）。
     */
    public static List<String> resolveSupplementalStoreMentionsForScopePivot(
            FollowUpRewriteResult rewriteResult,
            String scopeResolutionMessage,
            String rawNormalized,
            AiResolvedOrgScope org) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (rewriteResult != null
                && rewriteResult.isCanRewrite()
                && rewriteResult.getUsedAnchors() != null) {
            for (Map<String, String> anchor : rewriteResult.getUsedAnchors()) {
                if (anchor == null) {
                    continue;
                }
                String type = anchor.get("anchorType");
                String name = anchor.get("anchorName");
                if ("STORE".equalsIgnoreCase(StringUtils.hasText(type) ? type.trim() : null)
                        && StringUtils.hasText(name)) {
                    out.add(name.trim());
                }
            }
        }
        collectVisibleStoresMentionedLexically(scopeResolutionMessage, org, out);
        if (out.isEmpty()) {
            collectVisibleStoresMentionedLexically(rawNormalized, org, out);
        }
        if (out.size() != 1 && StringUtils.hasText(rawNormalized) && org != null) {
            List<String> fromRaw = listUniqueVisibleStoresInMessage(rawNormalized, org);
            if (fromRaw.size() == 1) {
                out.clear();
                out.add(fromRaw.get(0));
            }
        }
        return out.isEmpty() ? List.of() : new ArrayList<>(out);
    }

    private static void collectVisibleStoresMentionedLexically(
            String message, AiResolvedOrgScope org, LinkedHashSet<String> out) {
        if (!StringUtils.hasText(message) || org == null || out == null) {
            return;
        }
        List<AiStoreScopeDTO> visible = org.getVisibleStores();
        if (visible == null) {
            return;
        }
        for (AiStoreScopeDTO store : visible) {
            if (store == null || !StringUtils.hasText(store.getStoreName())) {
                continue;
            }
            String name = store.getStoreName().trim();
            if (messageLexicallyMentionsStoreName(message, name)) {
                out.add(name);
            }
        }
    }

    private static List<String> listUniqueVisibleStoresInMessage(String message, AiResolvedOrgScope org) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectVisibleStoresMentionedLexically(message, org, out);
        return out.isEmpty() ? List.of() : new ArrayList<>(out);
    }

    private static boolean messageLexicallyMentionsStoreName(String message, String storeName) {
        if (!StringUtils.hasText(message) || !StringUtils.hasText(storeName)) {
            return false;
        }
        if (AiFollowUpResolver.visibleStoreRowLabelMatchesDepartmentName(message, storeName)) {
            return true;
        }
        String compactMsg = message.replace(" ", "").replace("\u3000", "").trim();
        String compactName = storeName.replace(" ", "").replace("\u3000", "").trim();
        return StringUtils.hasText(compactName) && compactMsg.contains(compactName);
    }

    /**
     * 经销权限内 gb_department 门店根：先按 visibleStores 中非空 storeDepartmentId 与根 id 相交；
     * 若无 id（仅预览店名），则按店名 ↔ 根部名称 lexical 相容匹配；再无可见行则用权限内全体根。
     */
    private List<AiStoreScopeDTO> buildIntersectedStoreRootCandidates(
            AiResolvedOrgScope mergedOrg,
            AiRunCreateRequest request,
            AiSemanticStoreNarrowingDiagnostics diag) {
        List<AiStoreScopeDTO> vis = mergedOrg != null ? mergedOrg.getVisibleStores() : null;
        boolean visEmptyCollection = vis == null || vis.isEmpty();
        if (diag != null) {
            diag.setVisibleStoreCandidates(formatVisibleStorePreviewLabels(vis));
        }
        if (mergedOrg == null) {
            return List.of();
        }

        Long dis = mergedOrg.getDistributerId() != null
                ? mergedOrg.getDistributerId()
                : (request != null ? request.getDistributerId() : null);
        if (dis == null) {
            if (diag != null) {
                diag.setStoreRootCandidates(new ArrayList<>());
            }
            return List.of();
        }
        int disPk;
        try {
            disPk = Math.toIntExact(dis);
        } catch (ArithmeticException ex) {
            if (diag != null) {
                diag.setStoreRootCandidates(new ArrayList<>());
            }
            return List.of();
        }

        List<AiStoreScopeDTO> allRoots = loadDistributerStoreRootDtos(disPk);
        if (diag != null && allRoots.isEmpty()) {
            diag.setStoreRootCandidates(new ArrayList<>());
        }

        boolean hasNamedVisibleRows =
                vis != null
                        && vis.stream()
                                .anyMatch(s -> s != null && StringUtils.hasText(s.getStoreName()));
        Set<Long> allowedIds =
                vis == null
                        ? Set.of()
                        : vis.stream()
                                .filter(s -> s != null && s.getStoreDepartmentId() != null)
                                .map(AiStoreScopeDTO::getStoreDepartmentId)
                                .collect(Collectors.toSet());

        List<AiStoreScopeDTO> candidates;
        if (!allowedIds.isEmpty()) {
            candidates =
                    allRoots.stream()
                            .filter(r -> r.getStoreDepartmentId() != null && allowedIds.contains(r.getStoreDepartmentId()))
                            .collect(Collectors.toCollection(ArrayList::new));
        } else if (hasNamedVisibleRows && vis != null) {
            candidates =
                    allRoots.stream()
                            .filter(
                                    root -> vis.stream()
                                            .anyMatch(
                                                    row ->
                                                            row != null
                                                                    && AiFollowUpResolver
                                                                            .visibleStoreRowLabelMatchesDepartmentName(
                                                                                    row.getStoreName(),
                                                                                    root.getStoreName())))
                            .collect(Collectors.toCollection(ArrayList::new));
        } else {
            candidates = new ArrayList<>(allRoots);
        }

        if (diag != null) {
            diag.setStoreRootCandidates(
                    candidates.stream()
                            .map(SemanticScopeNarrowingPolicy::summarizeStoreCandidateForDiag)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(ArrayList::new)));
        }
        return candidates;
    }

    private List<AiStoreScopeDTO> loadDistributerStoreRootDtos(int distributerPk) {
        List<Integer> ids = gbDepartmentMapper.selectStoreDepartmentIdsUnderDistributer(distributerPk);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<AiStoreScopeDTO> roots = new ArrayList<>();
        for (Integer id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            GbDepartmentEntity e = gbDepartmentMapper.selectById(id);
            if (e == null || e.getGbDepartmentFatherId() == null || e.getGbDepartmentFatherId() != 0) {
                continue;
            }
            roots.add(
                    AiStoreScopeDTO.builder()
                            .storeDepartmentId(
                                    e.getGbDepartmentId() != null ? e.getGbDepartmentId().longValue() : id.longValue())
                            .storeName(e.getGbDepartmentName())
                            .build());
        }
        return roots;
    }

    static String summarizeStoreCandidateForDiag(AiStoreScopeDTO s) {
        if (s == null) {
            return null;
        }
        Long id = s.getStoreDepartmentId();
        String name = s.getStoreName();
        boolean hasId = id != null;
        boolean hasName = StringUtils.hasText(name);
        if (hasId && hasName) {
            return id + ":" + name.trim();
        }
        if (hasId) {
            return String.valueOf(id);
        }
        return hasName ? name.trim() : null;
    }

    private static List<String> formatVisibleStorePreviewLabels(List<AiStoreScopeDTO> vis) {
        List<String> out = new ArrayList<>();
        if (vis == null) {
            return out;
        }
        for (AiStoreScopeDTO s : vis) {
            String lbl = summarizeStoreCandidateForDiag(s);
            if (lbl != null) {
                out.add(lbl);
            }
        }
        return out;
    }

    public record StoreNarrowingSideEffects(String resolvedMatchedSemanticStoreMention) {}

    /**
     * 语义门店收窄诊断的 followUp 副作用与成功命中店名（不含 builder）。
     */
    public static StoreNarrowingSideEffects resolveStoreNarrowingSideEffects(
            AiSemanticStoreNarrowingDiagnostics diag, AiFollowUpResolution followUp) {
        boolean narrowingFailedExplicitWithoutAmbiguity =
                diag != null
                        && diag.isNarrowingAttemptedSemanticExplicitStore()
                        && !diag.isNarrowedSuccessfully()
                        && !diag.isAmbiguousLexicalMatch()
                        && StringUtils.hasText(diag.getNarrowingFailureReason());
        if (narrowingFailedExplicitWithoutAmbiguity && followUp != null) {
            followUp.setEffectiveScopeSource("STORE_SCOPE_SEMANTIC_UNRESOLVED");
        }
        String resolved =
                diag != null
                                && diag.isNarrowedSuccessfully()
                                && StringUtils.hasText(diag.getMatchedSemanticStoreMention())
                        ? diag.getMatchedSemanticStoreMention().trim()
                        : null;
        return new StoreNarrowingSideEffects(resolved);
    }

    public record ClarificationState(boolean clarificationRequired, String semanticClarificationQuestion) {}

    /** 门店 lexical 歧义时强制澄清（触发条件与澄清文案不变）。 */
    public static ClarificationState applyStoreLexicalAmbiguityClarification(
            boolean clarificationRequired,
            String semanticClarificationQuestion,
            AiSemanticStoreNarrowingDiagnostics diag) {
        if (diag == null || !diag.isAmbiguousLexicalMatch()) {
            return new ClarificationState(clarificationRequired, semanticClarificationQuestion);
        }
        return new ClarificationState(
                true, buildStoreLexicalAmbiguityQuestion(diag));
    }

    public static String buildStoreLexicalAmbiguityQuestion(AiSemanticStoreNarrowingDiagnostics diag) {
        String mention = diag != null ? diag.getLastSingleSemanticStoreMention() : null;
        List<String> opts = diag != null ? diag.getLexicalAmbiguityStoreSummaries() : null;
        if (!StringUtils.hasText(mention)) {
            return "请说明要查询哪家门店（系统识别出多个同名或相近门店）。";
        }
        String m = mention.trim();
        if (opts != null && !opts.isEmpty()) {
            return String.format(
                    "您提到的「%s」可能对应多家门店：%s。请明确具体是哪一家。", m, String.join("、", opts));
        }
        return String.format("您提到的「%s」可能对应多家门店，请明确具体是哪一家。", m);
    }
}
