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
 * GOODS 实体 Identity SSOT。主权顺序：
 * <ol>
 *   <li>当前轮 V2/LockedFrame 结构化 ID</li>
 *   <li>当前轮显式名称 → DB lookup</li>
 *   <li>无显式实体且 {@code USE_PREVIOUS_ANCHOR} → previous / rewrite resultAnchor</li>
 *   <li>否则 unresolved / NOT_FOUND</li>
 * </ol>
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
        String anchorPolicy = EntityAnchorSovereigntySupport.anchorPolicyFromParse(sem);
        String userMention = EntityAnchorSovereigntySupport.resolveCurrentTurnGoodsName(sem);
        boolean hasExplicitName = StringUtils.hasText(userMention);
        boolean allowHistorical =
                EntityAnchorSovereigntySupport.shouldAllowHistoricalAnchorSources(
                        anchorPolicy, hasExplicitName);
        Integer disId = BusinessEntityIdentityScopeSupport.resolveGoodsLookupDisId(ctx, distributerIdHint);

        Integer v2StructuredId = resolveCurrentV2StructuredGoodsId(sem);
        if (v2StructuredId != null) {
            return resolveByStructuredId(
                    v2StructuredId,
                    userMention,
                    anchorPolicy,
                    disId,
                    EntityIdentityResolutionSource.CURRENT_V2_STRUCTURED_ID);
        }

        if (hasExplicitName) {
            return resolveByExplicitName(userMention, anchorPolicy, disId);
        }

        if (EntityAnchorSovereigntySupport.isIgnorePreviousAnchor(anchorPolicy)) {
            return ResolvedEntityIdentity.unresolved(EntityIdentityType.GOODS, anchorPolicy);
        }

        if (!allowHistorical) {
            return ResolvedEntityIdentity.unresolved(EntityIdentityType.GOODS, anchorPolicy);
        }

        ResolvedEntityIdentity fromRewriteId = resolveFromRewriteResultAnchorId(ctx, disId, anchorPolicy);
        if (fromRewriteId != null) {
            return fromRewriteId;
        }

        if (StringUtils.hasText(ctx.getRewriteInheritedAnchorName())
                && isGoodsRewriteType(ctx.getRewriteInheritedAnchorType())) {
            return resolveFromRewriteInheritedName(
                    ctx.getRewriteInheritedAnchorName().trim(), disId, anchorPolicy);
        }

        return resolveFromPreviousResultAnchor(ctx, disId, anchorPolicy);
    }

    private ResolvedEntityIdentity resolveByStructuredId(
            Integer structuredId,
            String userMention,
            String anchorPolicy,
            Integer disId,
            EntityIdentityResolutionSource source) {
        BusinessEntityExistenceLookup.GoodsIdLookupResult byId = existenceLookup.lookupGoodsById(structuredId);
        if (byId.status() != EntityIdentityResolutionStatus.OK) {
            if (StringUtils.hasText(userMention)) {
                return resolveByExplicitName(userMention, anchorPolicy, disId);
            }
            return notFoundGoods(null, anchorPolicy, source, "goods_structured_id_not_found");
        }
        if (StringUtils.hasText(userMention)
                && !EntityAnchorSovereigntySupport.canonicalEntityNamesMatch(
                        userMention, byId.canonicalName())) {
            return nameIdConflict(userMention, byId.disGoodsId(), byId.canonicalName(), anchorPolicy);
        }
        return ResolvedEntityIdentity.builder()
                .entityType(EntityIdentityType.GOODS)
                .userMentionedName(trimOrNull(userMention))
                .resolvedCanonicalName(byId.canonicalName())
                .resolvedEntityId(byId.disGoodsId())
                .resolutionStatus(EntityIdentityResolutionStatus.OK)
                .resolutionSource(source)
                .anchorPolicyApplied(anchorPolicy)
                .build();
    }

    private ResolvedEntityIdentity resolveByExplicitName(
            String userMention, String anchorPolicy, Integer disId) {
        if (disId == null) {
            return ResolvedEntityIdentity.builder()
                    .entityType(EntityIdentityType.GOODS)
                    .userMentionedName(userMention.trim())
                    .resolutionStatus(EntityIdentityResolutionStatus.NOT_FOUND)
                    .resolutionSource(EntityIdentityResolutionSource.CURRENT_EXPLICIT_NAME_DB)
                    .anchorPolicyApplied(anchorPolicy)
                    .clarificationMessage(BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND)
                    .debugTrace(lookupDebug(null, userMention.trim(), null, 0, "missing_disId_for_db_lookup"))
                    .build();
        }
        return fromNameLookup(existenceLookup.lookupGoodsByName(disId, userMention), anchorPolicy);
    }

    private ResolvedEntityIdentity resolveFromRewriteResultAnchorId(
            AiResolvedQueryContext ctx, Integer disId, String anchorPolicy) {
        Integer rewriteId =
                CanonicalResultAnchorIdentitySupport.resolveRewriteResultAnchorGoodsDisId(ctx);
        if (rewriteId == null) {
            return null;
        }
        BusinessEntityExistenceLookup.GoodsIdLookupResult byId = existenceLookup.lookupGoodsById(rewriteId);
        if (byId.status() != EntityIdentityResolutionStatus.OK) {
            return null;
        }
        return ResolvedEntityIdentity.builder()
                .entityType(EntityIdentityType.GOODS)
                .resolvedCanonicalName(byId.canonicalName())
                .resolvedEntityId(byId.disGoodsId())
                .resolutionStatus(EntityIdentityResolutionStatus.OK)
                .resolutionSource(EntityIdentityResolutionSource.REWRITE_RESULT_ANCHOR_ID)
                .anchorPolicyApplied(anchorPolicy)
                .build();
    }

    private ResolvedEntityIdentity resolveFromRewriteInheritedName(
            String rewriteName, Integer disId, String anchorPolicy) {
        if (disId != null) {
            BusinessEntityExistenceLookup.GoodsNameLookupResult lookup =
                    existenceLookup.lookupGoodsByName(disId, rewriteName);
            if (lookup.status() == EntityIdentityResolutionStatus.OK) {
                return ResolvedEntityIdentity.builder()
                        .entityType(EntityIdentityType.GOODS)
                        .userMentionedName(rewriteName)
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
                .userMentionedName(rewriteName)
                .resolvedCanonicalName(rewriteName)
                .resolutionStatus(EntityIdentityResolutionStatus.OK)
                .resolutionSource(EntityIdentityResolutionSource.REWRITE_INHERITED_ANCHOR)
                .anchorPolicyApplied(anchorPolicy)
                .build();
    }

    private ResolvedEntityIdentity resolveFromPreviousResultAnchor(
            AiResolvedQueryContext ctx, Integer disId, String anchorPolicy) {
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
                        .resolvedCanonicalName(firstNonBlank(byId.canonicalName(), inheritedName))
                        .resolvedEntityId(byId.disGoodsId())
                        .resolutionStatus(EntityIdentityResolutionStatus.OK)
                        .resolutionSource(EntityIdentityResolutionSource.PREVIOUS_RESULT_ANCHOR_ID)
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
                        .resolutionSource(EntityIdentityResolutionSource.PREVIOUS_RESULT_ANCHOR_ID)
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
                        .resolutionSource(EntityIdentityResolutionSource.PREVIOUS_RESULT_ANCHOR_ID)
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
                    .resolutionSource(EntityIdentityResolutionSource.PREVIOUS_RESULT_ANCHOR_ID)
                    .anchorPolicyApplied(anchorPolicy)
                    .build();
        }
        return ResolvedEntityIdentity.unresolved(EntityIdentityType.GOODS, anchorPolicy);
    }

    /** 当前轮 V2/LockedFrame 结构化 ID；不读 rewriteUsedAnchors / previousTurn。 */
    private static Integer resolveCurrentV2StructuredGoodsId(AiQuerySemanticParseResult sem) {
        // V2 尚未输出 goods structured ID 槽位；预留扩展点，当前恒为 null。
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
                .resolutionSource(EntityIdentityResolutionSource.CURRENT_EXPLICIT_NAME_DB)
                .anchorPolicyApplied(anchorPolicy)
                .candidates(
                        lookup.candidates() == null
                                ? List.of()
                                : new ArrayList<>(lookup.candidates()))
                .clarificationMessage(lookup.clarificationMessage())
                .debugTrace(debug)
                .build();
    }

    private static ResolvedEntityIdentity nameIdConflict(
            String userMention, Integer candidateId, String canonicalName, String anchorPolicy) {
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("reason", "current_name_id_conflict");
        debug.put("candidateDisGoodsId", candidateId);
        debug.put("candidateCanonicalName", canonicalName);
        return ResolvedEntityIdentity.builder()
                .entityType(EntityIdentityType.GOODS)
                .userMentionedName(trimOrNull(userMention))
                .resolvedCanonicalName(canonicalName)
                .resolvedEntityId(null)
                .resolutionStatus(EntityIdentityResolutionStatus.NOT_FOUND)
                .resolutionSource(EntityIdentityResolutionSource.CURRENT_NAME_ID_CONFLICT)
                .anchorPolicyApplied(anchorPolicy)
                .clarificationMessage(BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND)
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
            String userMention,
            String anchorPolicy,
            EntityIdentityResolutionSource source,
            String reason) {
        return ResolvedEntityIdentity.builder()
                .entityType(EntityIdentityType.GOODS)
                .userMentionedName(trimOrNull(userMention))
                .resolutionStatus(EntityIdentityResolutionStatus.NOT_FOUND)
                .resolutionSource(source)
                .anchorPolicyApplied(anchorPolicy)
                .debugTrace(Map.of("reason", reason))
                .build();
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
