package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticContractAnchorInheritanceSupport;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 统一 Dish anchor 选择（Tool Request / AnswerPlan / resolvedContext 对齐）。
 *
 * <p>优先级：
 * <ol>
 *   <li>当前轮 {@code semanticSlots.mentionedDishName} / {@code mentionedDishName}</li>
 *   <li>当前轮结构化 DISH resultAnchor（仅当本轮 parse 未显式菜名）</li>
 *   <li>{@code rewriteInheritedAnchor}（仅 {@code USE_PREVIOUS_ANCHOR}）</li>
 *   <li>{@code previousTurn.lastMentionedDishName} / previous DISH resultAnchor（仅 {@code USE_PREVIOUS_ANCHOR}）</li>
 * </ol>
 * 当前轮显式菜名存在时，禁止带入上一轮 foodId。
 */
public final class EffectiveDishAnchorSupport {

    private EffectiveDishAnchorSupport() {}

    public static EffectiveDishAnchor resolve(AiResolvedQueryContext ctx) {
        if (ctx == null
                || !SemanticContractCompletionEngine.isContractLockedParse(ctx.getQuerySemanticParse())) {
            return EffectiveDishAnchor.empty();
        }
        AiQuerySemanticParseResult sem = ctx.getQuerySemanticParse();
        String anchorPolicy = anchorPolicyFromSlots(sem);
        boolean usePrevious = AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(anchorPolicy);
        boolean ignorePrevious = AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy);

        String currentDish = resolveCurrentTurnDishName(ctx, sem);
        if (StringUtils.hasText(currentDish)) {
            return EffectiveDishAnchor.builder()
                    .dishName(currentDish)
                    .foodId(null)
                    .source("currentTurn.mentionedDishName")
                    .build();
        }

        AiResultAnchor currentTurnAnchor = resolveCurrentTurnStructuredDishAnchor(sem);
        if (currentTurnAnchor != null) {
            String name =
                    StringUtils.hasText(currentTurnAnchor.getEntityName())
                            ? finalizeDishName(currentTurnAnchor.getEntityName().trim())
                            : null;
            Integer foodId = parseFoodId(currentTurnAnchor.getEntityId());
            if (StringUtils.hasText(name) || foodId != null) {
                return EffectiveDishAnchor.builder()
                        .dishName(name)
                        .foodId(foodId)
                        .source("currentTurn.resultAnchor")
                        .build();
            }
        }

        if (ignorePrevious || !usePrevious) {
            return EffectiveDishAnchor.empty();
        }

        if (StringUtils.hasText(ctx.getRewriteInheritedAnchorName())
                && isDishRewriteType(ctx.getRewriteInheritedAnchorType())) {
            String dish = finalizeDishName(ctx.getRewriteInheritedAnchorName().trim());
            if (StringUtils.hasText(dish)) {
                return EffectiveDishAnchor.builder()
                        .dishName(dish)
                        .foodId(null)
                        .source("rewriteInheritedAnchor")
                        .build();
            }
        }

        AiConversationTurnMemory previousTurn = ctx.getPreviousTurn();
        String inheritedDish =
                SemanticContractAnchorInheritanceSupport.resolveStructuredDishAnchor(
                        previousTurn,
                        ctx.getRewriteInheritedAnchorType(),
                        ctx.getRewriteInheritedAnchorName());
        if (StringUtils.hasText(inheritedDish)) {
            inheritedDish = finalizeDishName(inheritedDish);
        }
        AiResultAnchor previousAnchor = firstStructuredDishResultAnchor(previousTurn);
        Integer inheritedFoodId =
                previousAnchor != null ? parseFoodId(previousAnchor.getEntityId()) : null;
        if (StringUtils.hasText(inheritedDish)) {
            return EffectiveDishAnchor.builder()
                    .dishName(inheritedDish)
                    .foodId(inheritedFoodId)
                    .source("previousTurn.structuredDishAnchor")
                    .build();
        }
        if (inheritedFoodId != null) {
            return EffectiveDishAnchor.builder()
                    .foodId(inheritedFoodId)
                    .source("previousTurn.resultAnchor.foodId")
                    .build();
        }
        return EffectiveDishAnchor.empty();
    }

    private static String resolveCurrentTurnDishName(
            AiResolvedQueryContext ctx, AiQuerySemanticParseResult sem) {
        if (sem != null && StringUtils.hasText(sem.effectiveMentionedDishName())) {
            String dish = finalizeDishName(sem.effectiveMentionedDishName().trim());
            if (StringUtils.hasText(dish)) {
                return dish;
            }
        }
        if (StringUtils.hasText(ctx.getMentionedDishName())) {
            String dish = finalizeDishName(ctx.getMentionedDishName().trim());
            if (StringUtils.hasText(dish)) {
                return dish;
            }
        }
        return null;
    }

    /** 当前轮 V2/LockedFrame 结构化 DISH anchor；不读 rewriteUsedAnchors / previousTurn。 */
    private static AiResultAnchor resolveCurrentTurnStructuredDishAnchor(AiQuerySemanticParseResult sem) {
        // V2 尚未输出 dish structured ID 槽位；预留扩展点，当前恒为 null。
        return null;
    }

    private static AiResultAnchor firstStructuredDishResultAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return null;
        }
        for (AiResultAnchor anchor : previousTurn.getLastResultAnchors()) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(anchor.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(anchor.getEntityName()) || StringUtils.hasText(anchor.getEntityId())) {
                return anchor;
            }
        }
        return null;
    }

    private static String anchorPolicyFromSlots(AiQuerySemanticParseResult sem) {
        if (sem == null || sem.getSemanticSlots() == null) {
            return null;
        }
        String raw = sem.getSemanticSlots().getAnchorPolicy();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean isDishRewriteType(String rewriteType) {
        return !StringUtils.hasText(rewriteType)
                || AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(rewriteType.trim());
    }

    private static String finalizeDishName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return AiQuerySemanticLexicon.finalizeMentionedDishNameForDishProfit(raw.trim());
    }

    private static Integer parseFoodId(String entityId) {
        if (!StringUtils.hasText(entityId)) {
            return null;
        }
        try {
            return Integer.parseInt(entityId.trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
