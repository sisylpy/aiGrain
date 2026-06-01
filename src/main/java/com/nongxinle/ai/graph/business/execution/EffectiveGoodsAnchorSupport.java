package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

/**
 * GOODS 锚：当前轮 {@code semanticSlots.mentionedGoodsName} / 结构化 GOODS anchor / 继承上一轮。
 */
public final class EffectiveGoodsAnchorSupport {

    private EffectiveGoodsAnchorSupport() {}

    public static EffectiveGoodsAnchor resolve(AiResolvedQueryContext ctx) {
        if (ctx == null
                || !SemanticContractCompletionEngine.isContractLockedParse(ctx.getQuerySemanticParse())) {
            return EffectiveGoodsAnchor.empty();
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        String anchorPolicy = anchorPolicyFromSlots(sem);
        boolean usePrevious = AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchorPolicy);
        boolean ignorePrevious = AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy);

        String currentName = resolveCurrentTurnGoodsName(sem);
        Integer currentId = resolveCurrentTurnDisGoodsId(ctx);
        if (StringUtils.hasText(currentName) || currentId != null) {
            return EffectiveGoodsAnchor.builder()
                    .goodsName(currentName)
                    .disGoodsId(currentId)
                    .source("currentTurn.mentionedGoodsName")
                    .build();
        }

        if (ignorePrevious || !usePrevious) {
            return EffectiveGoodsAnchor.empty();
        }

        if (StringUtils.hasText(ctx.getRewriteInheritedAnchorName())
                && isGoodsRewriteType(ctx.getRewriteInheritedAnchorType())) {
            return EffectiveGoodsAnchor.builder()
                    .goodsName(ctx.getRewriteInheritedAnchorName().trim())
                    .source("rewriteInheritedAnchor")
                    .build();
        }

        AiResultAnchor previous = firstStructuredGoodsResultAnchor(ctx.getPreviousTurn());
        if (previous != null) {
            return EffectiveGoodsAnchor.builder()
                    .goodsName(
                            StringUtils.hasText(previous.getEntityName())
                                    ? previous.getEntityName().trim()
                                    : null)
                    .disGoodsId(parseDisGoodsId(previous.getEntityId()))
                    .source("previousTurn.resultAnchor")
                    .build();
        }
        return EffectiveGoodsAnchor.empty();
    }

    private static String resolveCurrentTurnGoodsName(AiQuerySemanticParseResult sem) {
        if (sem == null) {
            return null;
        }
        if (sem.getSemanticSlots() != null
                && StringUtils.hasText(sem.getSemanticSlots().getMentionedGoodsName())) {
            return sem.getSemanticSlots().getMentionedGoodsName().trim();
        }
        if (StringUtils.hasText(sem.getMentionedGoodsName())) {
            return sem.getMentionedGoodsName().trim();
        }
        return null;
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

    private static Integer parseDisGoodsId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            int id = Integer.parseInt(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isGoodsRewriteType(String rewriteType) {
        return StringUtils.hasText(rewriteType)
                && AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(rewriteType.trim());
    }

    private static String anchorPolicyFromSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        String raw = sem.getSemanticSlots().getAnchorPolicy();
        return StringUtils.hasText(raw) ? raw.trim() : null;
    }
}
