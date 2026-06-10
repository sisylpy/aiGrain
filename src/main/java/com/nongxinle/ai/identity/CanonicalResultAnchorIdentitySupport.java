package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 可信 canonical resultAnchor 在语义→实体链上的消费边界。
 * <p>Rewrite / previous provenance 仅为历史候选，不得覆盖当前轮显式实体或 {@code IGNORE_PREVIOUS_ANCHOR}。
 */
public final class CanonicalResultAnchorIdentitySupport {

    private CanonicalResultAnchorIdentitySupport() {}

    public static Integer resolveTrustworthyGoodsDisId(AiConversationTurnMemory previousTurn) {
        return resolveTrustworthyGoodsDisId(
                previousTurn == null ? null : previousTurn.getLastResultAnchors());
    }

    public static Integer resolveTrustworthyGoodsDisId(List<AiResultAnchor> anchors) {
        AiResultAnchor anchor = firstTrustworthyGoodsAnchor(anchors);
        if (anchor == null) {
            return null;
        }
        return parsePositiveEntityId(anchor.getEntityId());
    }

    /**
     * 历史 rewrite provenance 中的 GOODS ID（非当前轮 V2 structured ID）。
     * 调用方须先通过 {@link EntityAnchorSovereigntySupport#shouldAllowHistoricalAnchorSources} 门禁。
     */
    public static Integer resolveRewriteResultAnchorGoodsDisId(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return null;
        }
        return firstGoodsDisIdFromRewriteUsedAnchors(ctx.getRewriteUsedAnchors());
    }

    /** @deprecated 使用 {@link #resolveRewriteResultAnchorGoodsDisId}；不得在无 anchor 主权门禁时调用。 */
    @Deprecated
    public static Integer resolveExplicitRewriteAdoptedGoodsDisId(AiResolvedQueryContext ctx) {
        return resolveRewriteResultAnchorGoodsDisId(ctx);
    }

    public static Integer resolveExplicitRewriteAdoptedGoodsDisId(
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorEntityId,
            List<Map<String, String>> rewriteUsedAnchors) {
        Integer fromWire = firstGoodsDisIdFromRewriteUsedAnchors(rewriteUsedAnchors);
        if (fromWire != null) {
            return fromWire;
        }
        if (isGoodsRewriteAnchor(rewriteInheritedAnchorType, rewriteInheritedAnchorEntityId)) {
            return parsePositiveEntityId(rewriteInheritedAnchorEntityId);
        }
        return null;
    }

    public static boolean hasExplicitRewriteAdoptedGoodsProvenance(AiResolvedQueryContext ctx) {
        return resolveRewriteResultAnchorGoodsDisId(ctx) != null;
    }

    public static boolean hasConfirmedCanonicalGoodsProvenance(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getQuerySemanticParse() == null) {
            return false;
        }
        String anchorPolicy = EntityAnchorSovereigntySupport.anchorPolicyFromParse(ctx.getQuerySemanticParse());
        if (EntityAnchorSovereigntySupport.hasCurrentTurnExplicitGoodsName(ctx.getQuerySemanticParse())) {
            return false;
        }
        if (EntityAnchorSovereigntySupport.isIgnorePreviousAnchor(anchorPolicy)) {
            return false;
        }
        return EntityAnchorSovereigntySupport.isUsePreviousAnchor(anchorPolicy)
                && resolveRewriteResultAnchorGoodsDisId(ctx) != null;
    }

    public static boolean shouldPreferCanonicalGoodsIdBeforeNameLookup(
            AiQuerySemanticParseResult sem, AiConversationTurnMemory previousTurn) {
        return shouldPreferCanonicalGoodsIdBeforeNameLookup(
                sem, previousTurn, null, null, null, null);
    }

    public static boolean shouldPreferCanonicalGoodsIdBeforeNameLookup(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorEntityId,
            List<Map<String, String>> rewriteUsedAnchors,
            String rewriteInheritedAnchorName) {
        if (sem != null
                && EntityAnchorSovereigntySupport.hasCurrentTurnExplicitGoodsName(sem)) {
            return false;
        }
        String anchorPolicy = EntityAnchorSovereigntySupport.anchorPolicyFromParse(sem);
        if (EntityAnchorSovereigntySupport.isIgnorePreviousAnchor(anchorPolicy)) {
            return false;
        }
        if (!EntityAnchorSovereigntySupport.isUsePreviousAnchor(anchorPolicy)) {
            return false;
        }
        if (firstGoodsDisIdFromRewriteUsedAnchors(rewriteUsedAnchors) != null) {
            return true;
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchorPolicy)
                && resolveTrustworthyGoodsDisId(previousTurn) != null) {
            return true;
        }
        return isGoodsRewriteAnchor(rewriteInheritedAnchorType, rewriteInheritedAnchorName)
                && parsePositiveEntityId(rewriteInheritedAnchorEntityId) != null;
    }

    public static AiResultAnchor firstTrustworthyGoodsAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return null;
        }
        return firstTrustworthyGoodsAnchor(previousTurn.getLastResultAnchors());
    }

    static AiResultAnchor firstTrustworthyGoodsAnchor(List<AiResultAnchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return null;
        }
        for (AiResultAnchor anchor : anchors) {
            if (isTrustworthyGoodsAnchor(anchor)) {
                return anchor;
            }
        }
        return null;
    }

    public static boolean isTrustworthyGoodsAnchor(AiResultAnchor anchor) {
        if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
            return false;
        }
        if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(anchor.getEntityType().trim())) {
            return false;
        }
        if (!StringUtils.hasText(anchor.getEntityName())) {
            return false;
        }
        return parsePositiveEntityId(anchor.getEntityId()) != null;
    }

    private static Integer firstGoodsDisIdFromRewriteUsedAnchors(List<Map<String, String>> rewriteUsedAnchors) {
        if (rewriteUsedAnchors == null || rewriteUsedAnchors.isEmpty()) {
            return null;
        }
        for (Map<String, String> raw : rewriteUsedAnchors) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String type = raw.get("entityType");
            if (!StringUtils.hasText(type)
                    || !AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(type.trim())) {
                continue;
            }
            Integer id = parsePositiveEntityId(raw.get("entityId"));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static boolean isGoodsRewriteAnchor(String rewriteType, String rewriteToken) {
        return StringUtils.hasText(rewriteType)
                && StringUtils.hasText(rewriteToken)
                && AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(rewriteType.trim());
    }

    static Integer parsePositiveEntityId(String entityId) {
        if (!StringUtils.hasText(entityId)) {
            return null;
        }
        try {
            long id = Long.parseLong(entityId.trim());
            if (id <= 0 || id > Integer.MAX_VALUE) {
                return null;
            }
            return (int) id;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
