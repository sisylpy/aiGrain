package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GOODS/DISH 实体 Identity SSOT（PR1：GOODS 落地）。当前轮显式 mention 优先 DB grounding；
 * 仅无显式实体且 {@code anchorPolicy=USE_PREVIOUS} 时继承 {@code previousTurn.resultAnchor}。
 */
@Service
@RequiredArgsConstructor
public class BusinessEntityIdentityResolver {

    private final BusinessEntityExistenceLookup existenceLookup;

    public ResolvedEntityIdentity resolveGoods(AiResolvedQueryContext ctx) {
        return resolveGoods(ctx, null);
    }

    public ResolvedEntityIdentity resolveGoods(AiResolvedQueryContext ctx, Integer distributerIdHint) {
        if (ctx == null
                || !SemanticContractCompletionEngine.isContractLockedParse(ctx.getQuerySemanticParse())) {
            return ResolvedEntityIdentity.skipped(EntityIdentityType.GOODS);
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        String anchorPolicy = anchorPolicyFromSlots(sem);
        String userMention = resolveCurrentTurnGoodsName(sem);
        Integer structuredId = resolveCurrentTurnDisGoodsId(ctx);
        boolean hasExplicit = StringUtils.hasText(userMention) || structuredId != null;
        Integer disId = BusinessEntityIdentityScopeSupport.resolveGoodsLookupDisId(ctx, distributerIdHint);

        if (structuredId != null) {
            BusinessEntityExistenceLookup.GoodsIdLookupResult byId = existenceLookup.lookupGoodsById(structuredId);
            if (byId.status() == EntityIdentityResolutionStatus.OK) {
                return ResolvedEntityIdentity.builder()
                        .entityType(EntityIdentityType.GOODS)
                        .userMentionedName(trimOrNull(userMention))
                        .resolvedCanonicalName(byId.canonicalName())
                        .resolvedEntityId(byId.disGoodsId())
                        .resolutionStatus(EntityIdentityResolutionStatus.OK)
                        .resolutionSource(EntityIdentityResolutionSource.CURRENT_STRUCTURED_ID)
                        .anchorPolicyApplied(anchorPolicy)
                        .build();
            }
            if (hasExplicit && !StringUtils.hasText(userMention)) {
                return notFoundGoods(null, anchorPolicy, "goods_structured_id_not_found");
            }
        }

        ResolvedEntityIdentity inheritedCanonical =
                resolveInheritedCanonicalGoodsById(ctx, disId, anchorPolicy);
        if (inheritedCanonical != null) {
            return inheritedCanonical;
        }

        if (StringUtils.hasText(userMention)) {
            if (disId == null) {
                return ResolvedEntityIdentity.builder()
                        .entityType(EntityIdentityType.GOODS)
                        .userMentionedName(userMention.trim())
                        .resolutionStatus(EntityIdentityResolutionStatus.NOT_FOUND)
                        .resolutionSource(EntityIdentityResolutionSource.CURRENT_MENTION_DB)
                        .anchorPolicyApplied(anchorPolicy)
                        .clarificationMessage(BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND)
                        .debugTrace(lookupDebug(null, userMention.trim(), null, 0, "missing_disId_for_db_lookup"))
                        .build();
            }
            return fromNameLookup(existenceLookup.lookupGoodsByName(disId, userMention), anchorPolicy);
        }

        if (AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy)) {
            return ResolvedEntityIdentity.unresolved(EntityIdentityType.GOODS, anchorPolicy);
        }

        if (StringUtils.hasText(ctx.getRewriteInheritedAnchorName())
                && isGoodsRewriteType(ctx.getRewriteInheritedAnchorType())) {
            String rewrite = ctx.getRewriteInheritedAnchorName().trim();
            if (disId != null) {
                BusinessEntityExistenceLookup.GoodsNameLookupResult lookup =
                        existenceLookup.lookupGoodsByName(disId, rewrite);
                if (lookup.status() == EntityIdentityResolutionStatus.OK) {
                    return ResolvedEntityIdentity.builder()
                            .entityType(EntityIdentityType.GOODS)
                            .userMentionedName(rewrite)
                            .resolvedCanonicalName(lookup.canonicalName())
                            .resolvedEntityId(lookup.disGoodsId())
                            .resolutionStatus(EntityIdentityResolutionStatus.OK)
                            .resolutionSource(EntityIdentityResolutionSource.REWRITE_INHERITED_ANCHOR)
                            .anchorPolicyApplied(anchorPolicy)
                            .build();
                }
            }
            return ResolvedEntityIdentity.builder()
                    .entityType(EntityIdentityType.GOODS)
                    .userMentionedName(rewrite)
                    .resolvedCanonicalName(rewrite)
                    .resolutionStatus(EntityIdentityResolutionStatus.OK)
                    .resolutionSource(EntityIdentityResolutionSource.REWRITE_INHERITED_ANCHOR)
                    .anchorPolicyApplied(anchorPolicy)
                    .build();
        }

        if (!AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchorPolicy)) {
            return ResolvedEntityIdentity.unresolved(EntityIdentityType.GOODS, anchorPolicy);
        }

        AiResultAnchor previous = firstStructuredGoodsResultAnchor(ctx.getPreviousTurn());
        if (previous == null) {
            return ResolvedEntityIdentity.unresolved(EntityIdentityType.GOODS, anchorPolicy);
        }
        Integer inheritedId = parseDisGoodsId(previous.getEntityId());
        String inheritedName =
                StringUtils.hasText(previous.getEntityName()) ? previous.getEntityName().trim() : null;
        if (inheritedId != null) {
            BusinessEntityExistenceLookup.GoodsIdLookupResult byId = existenceLookup.lookupGoodsById(inheritedId);
            if (byId.status() == EntityIdentityResolutionStatus.OK) {
                return ResolvedEntityIdentity.builder()
                        .entityType(EntityIdentityType.GOODS)
                        .resolvedCanonicalName(
                                firstNonBlank(byId.canonicalName(), inheritedName))
                        .resolvedEntityId(byId.disGoodsId())
                        .resolutionStatus(EntityIdentityResolutionStatus.OK)
                        .resolutionSource(EntityIdentityResolutionSource.INHERITED_PREVIOUS_ANCHOR)
                        .anchorPolicyApplied(anchorPolicy)
                        .build();
            }
        }
        if (StringUtils.hasText(inheritedName) && disId != null) {
            BusinessEntityExistenceLookup.GoodsNameLookupResult lookup =
                    existenceLookup.lookupGoodsByName(disId, inheritedName);
            if (lookup.status() == EntityIdentityResolutionStatus.OK) {
                return ResolvedEntityIdentity.builder()
                        .entityType(EntityIdentityType.GOODS)
                        .resolvedCanonicalName(lookup.canonicalName())
                        .resolvedEntityId(lookup.disGoodsId())
                        .resolutionStatus(EntityIdentityResolutionStatus.OK)
                        .resolutionSource(EntityIdentityResolutionSource.INHERITED_PREVIOUS_ANCHOR)
                        .anchorPolicyApplied(anchorPolicy)
                        .build();
            }
            if (lookup.status() == EntityIdentityResolutionStatus.NEED_CLARIFICATION
                    || lookup.status() == EntityIdentityResolutionStatus.NOT_FOUND) {
                ResolvedEntityIdentity base = fromNameLookup(lookup, anchorPolicy);
                return ResolvedEntityIdentity.builder()
                        .entityType(base.getEntityType())
                        .userMentionedName(base.getUserMentionedName())
                        .resolvedCanonicalName(base.getResolvedCanonicalName())
                        .resolvedEntityId(base.getResolvedEntityId())
                        .resolutionStatus(base.getResolutionStatus())
                        .resolutionSource(EntityIdentityResolutionSource.INHERITED_PREVIOUS_ANCHOR)
                        .candidates(base.getCandidates())
                        .anchorPolicyApplied(anchorPolicy)
                        .clarificationMessage(base.getClarificationMessage())
                        .debugTrace(base.getDebugTrace())
                        .build();
            }
        }
        if (inheritedId != null || StringUtils.hasText(inheritedName)) {
            return ResolvedEntityIdentity.builder()
                    .entityType(EntityIdentityType.GOODS)
                    .resolvedCanonicalName(inheritedName)
                    .resolvedEntityId(inheritedId)
                    .resolutionStatus(EntityIdentityResolutionStatus.OK)
                    .resolutionSource(EntityIdentityResolutionSource.INHERITED_PREVIOUS_ANCHOR)
                    .anchorPolicyApplied(anchorPolicy)
                    .build();
        }
        return ResolvedEntityIdentity.unresolved(EntityIdentityType.GOODS, anchorPolicy);
    }

    private ResolvedEntityIdentity resolveInheritedCanonicalGoodsById(
            AiResolvedQueryContext ctx, Integer disId, String anchorPolicy) {
        if (ctx == null) {
            return null;
        }
        Integer explicitRewriteId =
                CanonicalResultAnchorIdentitySupport.resolveExplicitRewriteAdoptedGoodsDisId(ctx);
        if (explicitRewriteId != null) {
            return buildInheritedGoodsFromId(ctx, disId, anchorPolicy, explicitRewriteId);
        }
        if (ctx.getPreviousTurn() == null) {
            return null;
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy)) {
            return null;
        }
        Integer inheritedId =
                CanonicalResultAnchorIdentitySupport.resolveTrustworthyGoodsDisId(ctx.getPreviousTurn());
        if (inheritedId == null) {
            return null;
        }
        return buildInheritedGoodsFromId(ctx, disId, anchorPolicy, inheritedId);
    }

    private ResolvedEntityIdentity buildInheritedGoodsFromId(
            AiResolvedQueryContext ctx, Integer disId, String anchorPolicy, Integer goodsId) {
        BusinessEntityExistenceLookup.GoodsIdLookupResult byId = existenceLookup.lookupGoodsById(goodsId);
        if (byId.status() == EntityIdentityResolutionStatus.OK) {
            AiResultAnchor previous =
                    ctx.getPreviousTurn() != null
                            ? CanonicalResultAnchorIdentitySupport.firstTrustworthyGoodsAnchor(
                                    ctx.getPreviousTurn())
                            : null;
            String inheritedName =
                    firstNonBlank(byId.canonicalName(), previous != null ? previous.getEntityName() : null);
            return ResolvedEntityIdentity.builder()
                    .entityType(EntityIdentityType.GOODS)
                    .userMentionedName(trimOrNull(resolveCurrentTurnGoodsName(ctx.getQuerySemanticParse())))
                    .resolvedCanonicalName(inheritedName)
                    .resolvedEntityId(byId.disGoodsId())
                    .resolutionStatus(EntityIdentityResolutionStatus.OK)
                    .resolutionSource(EntityIdentityResolutionSource.INHERITED_PREVIOUS_ANCHOR)
                    .anchorPolicyApplied(anchorPolicy)
                    .build();
        }
        if (disId != null) {
            return null;
        }
        return null;
    }

    private static ResolvedEntityIdentity fromNameLookup(
            BusinessEntityExistenceLookup.GoodsNameLookupResult lookup, String anchorPolicy) {
        Map<String, Object> debug =
                lookupDebug(
                        lookup.lookupDisId() > 0 ? lookup.lookupDisId() : null,
                        lookup.userMention(),
                        lookup.lookupSearchParam(),
                        lookup.lookupHitCount(),
                        lookup.status() == EntityIdentityResolutionStatus.NOT_FOUND
                                ? "db_quick_search_no_match"
                                : null);
        return ResolvedEntityIdentity.builder()
                .entityType(EntityIdentityType.GOODS)
                .userMentionedName(trimOrNull(lookup.userMention()))
                .resolvedCanonicalName(lookup.canonicalName())
                .resolvedEntityId(lookup.disGoodsId())
                .resolutionStatus(lookup.status())
                .resolutionSource(EntityIdentityResolutionSource.CURRENT_MENTION_DB)
                .anchorPolicyApplied(anchorPolicy)
                .candidates(
                        lookup.candidates() == null
                                ? List.of()
                                : new ArrayList<>(lookup.candidates()))
                .clarificationMessage(lookup.clarificationMessage())
                .debugTrace(debug)
                .build();
    }

    private static Map<String, Object> lookupDebug(
            Integer disId, String hint, String searchParam, int hitCount, String failureReason) {
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        if (disId != null && disId > 0) {
            debug.put("identityLookupDisId", disId);
        }
        if (StringUtils.hasText(hint)) {
            debug.put("identityLookupHint", hint.trim());
        }
        if (StringUtils.hasText(searchParam)) {
            debug.put("identityLookupSearchParam", searchParam.trim());
        }
        debug.put("identityLookupHitCount", hitCount);
        if (StringUtils.hasText(failureReason)) {
            debug.put("identityLookupFailureReason", failureReason.trim());
        }
        return debug;
    }

    private static ResolvedEntityIdentity notFoundGoods(
            String userMention, String anchorPolicy, String reason) {
        return ResolvedEntityIdentity.builder()
                .entityType(EntityIdentityType.GOODS)
                .userMentionedName(trimOrNull(userMention))
                .resolutionStatus(EntityIdentityResolutionStatus.NOT_FOUND)
                .resolutionSource(EntityIdentityResolutionSource.CURRENT_STRUCTURED_ID)
                .anchorPolicyApplied(anchorPolicy)
                .debugTrace(Map.of("reason", reason))
                .build();
    }

    private static String resolveCurrentTurnGoodsName(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        return sem.effectiveMentionedGoodsName();
    }

    private static Integer resolveCurrentTurnDisGoodsId(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getRewriteUsedAnchors() == null) {
            return null;
        }
        for (var raw : ctx.getRewriteUsedAnchors()) {
            if (raw == null) {
                continue;
            }
            String type = raw.get("entityType");
            if (!StringUtils.hasText(type)
                    || !AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(type.trim())) {
                continue;
            }
            Integer id = parseDisGoodsId(raw.get("entityId"));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static AiResultAnchor firstStructuredGoodsResultAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return null;
        }
        for (AiResultAnchor anchor : previousTurn.getLastResultAnchors()) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(anchor.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(anchor.getEntityName()) || StringUtils.hasText(anchor.getEntityId())) {
                return anchor;
            }
        }
        return null;
    }

    private static Integer parseDisGoodsId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            int id = n.intValue();
            return id > 0 ? id : null;
        }
        if (raw instanceof String s && StringUtils.hasText(s)) {
            try {
                int id = Integer.parseInt(s.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String anchorPolicyFromSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        String raw = sem.getSemanticSlots().getAnchorPolicy();
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }

    private static boolean isGoodsRewriteType(String rewriteType) {
        return StringUtils.hasText(rewriteType)
                && AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(rewriteType.trim());
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return null;
    }
}
