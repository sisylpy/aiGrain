package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.graph.business.execution.EffectiveDishAnchor;
import com.nongxinle.ai.graph.business.execution.EffectiveDishAnchorSupport;
import com.nongxinle.ai.semantic.contract.SemanticContractAnchorInheritanceSupport;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * DISH 卡片 / AnswerPlan 展示名 SSOT：当前轮显式菜名优先；Tool payload 次之；
 * 上一轮 anchor 仅在本轮无显式菜品且 {@code USE_PREVIOUS_ANCHOR} 时采用。
 */
public final class DishEntityDisplayNameSupport {

    private DishEntityDisplayNameSupport() {}

    public static boolean hasCurrentTurnExplicitDishMention(AiResolvedQueryContext rq) {
        return StringUtils.hasText(currentTurnExplicitDishName(rq));
    }

    public static String currentTurnExplicitDishName(AiResolvedQueryContext rq) {
        if (rq == null) {
            return null;
        }
        if (rq.getQuerySemanticParse() != null
                && StringUtils.hasText(rq.getQuerySemanticParse().effectiveMentionedDishName())) {
            return rq.getQuerySemanticParse().effectiveMentionedDishName().trim();
        }
        if (StringUtils.hasText(rq.getMentionedDishName())) {
            return rq.getMentionedDishName().trim();
        }
        return null;
    }

    public static boolean allowsPreviousDishAnchor(AiResolvedQueryContext rq) {
        if (hasCurrentTurnExplicitDishMention(rq)) {
            return false;
        }
        if (rq == null || rq.getQuerySemanticParse() == null) {
            return false;
        }
        return SemanticContractAnchorInheritanceSupport.isUsePreviousAnchorPolicy(
                rq.getQuerySemanticParse());
    }

    public static String resolveDisplayDishName(AiResolvedQueryContext rq, Map<String, Object> toolData) {
        String fromSemantic = currentTurnExplicitDishName(rq);
        if (StringUtils.hasText(fromSemantic)) {
            return fromSemantic;
        }
        String fromTool = str(toolData == null ? null : toolData.get("dishName"));
        if (StringUtils.hasText(fromTool)) {
            return fromTool;
        }
        return resolveDishNameFromAnchorIfAllowed(rq);
    }

    public static Integer resolveDisplayFoodId(AiResolvedQueryContext rq, Map<String, Object> toolData) {
        Integer fromTool = toInt(toolData == null ? null : toolData.get("dishId"));
        if (fromTool != null) {
            return fromTool;
        }
        return resolveFoodIdFromAnchorIfAllowed(rq);
    }

    public static String resolveDisplayDishNameForPlan(AiResolvedQueryContext rq, String planDishName) {
        String fromSemantic = currentTurnExplicitDishName(rq);
        if (StringUtils.hasText(fromSemantic)) {
            return fromSemantic;
        }
        if (StringUtils.hasText(planDishName)) {
            return planDishName.trim();
        }
        return resolveDishNameFromAnchorIfAllowed(rq);
    }

    public static Integer resolveDisplayFoodIdForPlan(AiResolvedQueryContext rq, Integer planFoodId) {
        if (planFoodId != null && planFoodId > 0) {
            return planFoodId;
        }
        return resolveFoodIdFromAnchorIfAllowed(rq);
    }

    private static String resolveDishNameFromAnchorIfAllowed(AiResolvedQueryContext rq) {
        EffectiveDishAnchor anchor = EffectiveDishAnchorSupport.resolve(rq);
        if (!StringUtils.hasText(anchor.getDishName())) {
            return null;
        }
        if (isCurrentTurnDishAnchorSource(anchor.getSource())) {
            return anchor.getDishName().trim();
        }
        if (isPreviousTurnDishAnchorSource(anchor.getSource()) && allowsPreviousDishAnchor(rq)) {
            return anchor.getDishName().trim();
        }
        return null;
    }

    private static Integer resolveFoodIdFromAnchorIfAllowed(AiResolvedQueryContext rq) {
        EffectiveDishAnchor anchor = EffectiveDishAnchorSupport.resolve(rq);
        if (anchor.getFoodId() == null || anchor.getFoodId() <= 0) {
            return null;
        }
        if (isCurrentTurnDishAnchorSource(anchor.getSource())) {
            return anchor.getFoodId();
        }
        if (isPreviousTurnDishAnchorSource(anchor.getSource()) && allowsPreviousDishAnchor(rq)) {
            return anchor.getFoodId();
        }
        return null;
    }

    private static boolean isCurrentTurnDishAnchorSource(String source) {
        return StringUtils.hasText(source)
                && (source.startsWith("currentTurn") || "rewriteInheritedAnchor".equals(source));
    }

    private static boolean isPreviousTurnDishAnchorSource(String source) {
        return StringUtils.hasText(source) && source.startsWith("previousTurn");
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        try {
            int v = Integer.parseInt(o.toString().trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
