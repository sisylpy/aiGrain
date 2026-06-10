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
 * 可信 canonical resultAnchor（正整数 entityId）在语义→实体执行链上的优先消费；不读 NL 词表。
 * <p>
 * Rewrite 结构化 provenance（{@code rewriteUsedAnchors} / rewriteInherited*）优先于 V2 raw
 * {@code IGNORE_PREVIOUS_ANCHOR}；不得仅凭 {@code usedPreviousContext} 全局放宽。
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

    public static Integer resolveExplicitRewriteAdoptedGoodsDisId(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return null;
        }
        return resolveExplicitRewriteAdoptedGoodsDisId(
                ctx.getRewriteInheritedAnchorType(),
                ctx.getRewriteInheritedAnchorEntityId(),
                ctx.getRewriteUsedAnchors());
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
        return resolveExplicitRewriteAdoptedGoodsDisId(ctx) != null;
    }

    public static boolean hasExplicitRewriteAdoptedGoodsProvenance(
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorEntityId,
            List<Map<String, String>> rewriteUsedAnchors) {
        return resolveExplicitRewriteAdoptedGoodsDisId(
                        rewriteInheritedAnchorType, rewriteInheritedAnchorEntityId, rewriteUsedAnchors)
                != null;
    }

    public static boolean hasConfirmedCanonicalGoodsProvenance(AiResolvedQueryContext ctx) {
        if (ctx == null) {
            return false;
        }
        return hasExplicitRewriteAdoptedGoodsProvenance(ctx);
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
        if (hasExplicitRewriteAdoptedGoodsProvenance(
                rewriteInheritedAnchorType, rewriteInheritedAnchorEntityId, rewriteUsedAnchors)) {
            return true;
        }
        if (sem != null && AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy(sem))) {
            return false;
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchorPolicy(sem))
                && resolveTrustworthyGoodsDisId(previousTurn) != null) {
            return true;
        }
        return isGoodsRewriteAnchor(rewriteInheritedAnchorType, rewriteInheritedAnchorName)
                && parsePositiveEntityId(rewriteInheritedAnchorEntityId) != null;
    }

    static AiResultAnchor firstTrustworthyGoodsAnchor(AiConversationTurnMemory previousTurn) {
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

    private static String anchorPolicy(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        String raw = sem.getSemanticSlots().getAnchorPolicy();
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }
}
